// DeezerLyricsProvider.kt

package com.bedivine.app.lyrics

import android.content.Context
import com.music.directlyrics.Deezer
import com.bedivine.app.constants.EnableDeezerKey
import com.bedivine.app.utils.dataStore
import com.bedivine.app.utils.get

object DeezerLyricsProvider : LyricsProvider {
    override val name = "Deezer"
    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableDeezerKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = Deezer.getLyrics(title, artist, duration)
}
