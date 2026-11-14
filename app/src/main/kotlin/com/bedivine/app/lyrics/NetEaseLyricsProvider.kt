// NetEaseLyricsProvider.kt

package com.bedivine.app.lyrics

import android.content.Context
import com.music.directlyrics.NetEase
import com.bedivine.app.constants.EnableNetEaseKey
import com.bedivine.app.utils.dataStore
import com.bedivine.app.utils.get

object NetEaseLyricsProvider : LyricsProvider {
    override val name = "NetEase"
    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableNetEaseKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = NetEase.getLyrics(title, artist, duration)
}
