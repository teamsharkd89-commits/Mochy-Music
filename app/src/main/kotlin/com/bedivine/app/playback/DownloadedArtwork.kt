// DownloadedArtwork.kt

package com.bedivine.app.playback

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import com.bedivine.app.applecanvas.AppleMusicCanvasProvider
import com.bedivine.app.canvas.MonochromeApiCanvas
import com.bedivine.app.canvas.SpotifyCanvas
import com.bedivine.app.constants.CanvasThumbnailAnimationKey
import com.bedivine.app.constants.CanvasWifiOnlyKey
import com.bedivine.app.db.MusicDatabase
import com.bedivine.app.db.entities.Song
import com.bedivine.app.ui.player.normalizeCanvasArtistName
import com.bedivine.app.ui.player.normalizeCanvasSongTitle
import com.bedivine.app.utils.DataSaver
import com.bedivine.app.utils.OfflineMode
import com.bedivine.app.utils.dataStore
import com.bedivine.app.utils.followAppProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Artwork saved with a download, so it's there offline.
 *
 * A download used to warm Coil's disk cache with the cover and never save a file of its own,
 * so the cover was gone as soon as the cache let it go. The HD cover and, when the song has
 * one, its animated cover (canvas) are now downloaded as files of their own, next to the song,
 * and the player prefers them over the remote URL. Files are named after the song id, so no
 * database change was needed to find them again.
 */
object DownloadedArtwork {
    private const val ARTWORK_DIR = "artwork"
    private const val CANVAS_DIR = "canvas"

    fun artworkFile(context: Context, mediaId: String): File =
        File(File(context.filesDir, ARTWORK_DIR), "$mediaId.jpg")

    /** The downloaded HD cover when it's there, else null. */
    fun downloadedArtworkFile(context: Context, mediaId: String): File? =
        artworkFile(context, mediaId).takeIf { it.exists() && it.length() > 0 }

    /** The saved HD cover file when it's there, else the remote URL — so a downloaded song's cover never needs the network. */
    fun artworkModel(context: Context, mediaId: String?, url: String?): Any? =
        mediaId?.let { downloadedArtworkFile(context, it) } ?: url

    /**
     * The song id hiding in a YouTube thumbnail URL (i.ytimg.com/vi/<id>/...): a song's
     * id is its video id, so a playlist collage tile can find the song's saved cover
     * file from the URL alone. Null for any other host.
     */
    fun songIdFromThumbnail(url: String?): String? {
        if (url == null) return null
        val marker = "i.ytimg.com/vi/"
        val start = url.indexOf(marker)
        if (start < 0) return null
        val id = url.substring(start + marker.length).substringBefore('/').substringBefore('?')
        return id.takeIf { it.isNotBlank() }
    }

    /** The downloaded animated cover when it's there, else null (any extension). */
    fun downloadedCanvasFile(context: Context, mediaId: String): File? {
        val dir = File(context.filesDir, CANVAS_DIR)
        if (!dir.isDirectory) return null
        return dir.listFiles()?.firstOrNull { it.name.startsWith("$mediaId.") && it.length() > 0 }
    }

    /** Drop a song's saved artwork, e.g. when its download is removed. */
    fun deleteFor(context: Context, mediaId: String) {
        runCatching { artworkFile(context, mediaId).delete() }
        runCatching { downloadedCanvasFile(context, mediaId)?.delete() }
    }

    private val httpClient by lazy {
        OkHttpClient.Builder().followAppProxy().build()
    }

    private fun downloadToFile(url: String, target: File): Boolean {
        val request = Request.Builder().url(url).build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use false
            val body = response.body ?: return@use false
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, "${target.name}.tmp")
            body.byteStream().use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            if (tmp.length() <= 0) {
                tmp.delete()
                return@use false
            }
            if (!tmp.renameTo(target)) {
                tmp.delete()
                return@use false
            }
            true
        }
    }

    /** Best cover first: maxres, then hq720, then hqdefault. Other hosts keep their one URL. */
    private fun artworkCandidates(thumbnailUrl: String): List<String> {
        if (!thumbnailUrl.contains("i.ytimg.com")) return listOf(thumbnailUrl)
        return listOf(
            thumbnailUrl,
            thumbnailUrl.replace("maxresdefault.jpg", "hq720.jpg"),
            thumbnailUrl.replace("maxresdefault.jpg", "hqdefault.jpg"),
        ).distinct()
    }

    /**
     * The HD cover and the animated cover of a just-downloaded song, saved as files.
     * Runs next to saveLyrics on download completion; a failure here never fails the download.
     */
    suspend fun saveForDownload(context: Context, database: MusicDatabase, mediaId: String) {
        if (OfflineMode.isOn) return
        val song = database.song(mediaId).first() ?: return
        song.song.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { url ->
            runCatching { downloadBestArtwork(context, mediaId, url) }
        }
        runCatching { downloadCanvas(context, mediaId, song) }
    }

    private fun downloadBestArtwork(context: Context, mediaId: String, thumbnailUrl: String) {
        val target = artworkFile(context, mediaId)
        if (target.exists() && target.length() > 0) return
        for (candidate in artworkCandidates(thumbnailUrl)) {
            if (runCatching { downloadToFile(candidate, target) }.getOrDefault(false)) return
        }
    }

    private suspend fun downloadCanvas(context: Context, mediaId: String, song: Song) {
        val prefs = context.dataStore.data.first()
        val canvasEnabled = prefs[CanvasThumbnailAnimationKey] ?: true
        if (!canvasEnabled) return
        val canvasWifiOnly = prefs[CanvasWifiOnlyKey] ?: true
        val connectivityManager = context.getSystemService<ConnectivityManager>()
        if (canvasWifiOnly && connectivityManager?.isActiveNetworkMetered == true) return
        // data saver keeps the cover still, a canvas is a video loop
        if (DataSaver.isActive(context)) return

        val title = normalizeCanvasSongTitle(song.song.title)
        val artist = normalizeCanvasArtistName(song.artists.firstOrNull()?.name.orEmpty())
        if (title.length < 2 || artist.length < 2) return
        val durationSec = song.song.duration.takeIf { it > 0 }

        // the same lookup the player does, Spotify first when its session is set, then Monochrome, then Apple Music
        val artwork = withContext(Dispatchers.IO) {
            SpotifyCanvas.search(title, artist, null)
                ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
            ?: MonochromeApiCanvas.getBySongArtist(title, artist, null, durationSec)
                ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                ?: AppleMusicCanvasProvider.getBySongArtist(title, artist, null, "us", durationSec)
                    ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
        } ?: return
        val url = artwork.preferredAnimationUrl ?: return
        val extension = url.substringAfterLast('.', "")
            .substringBefore('?')
            .lowercase()
            .takeIf { it in setOf("mp4", "mov", "webm", "mkv", "m4v") } ?: "mp4"
        val target = File(File(context.filesDir, CANVAS_DIR), "$mediaId.$extension")
        if (target.exists() && target.length() > 0) return
        downloadToFile(url, target)
    }
}
