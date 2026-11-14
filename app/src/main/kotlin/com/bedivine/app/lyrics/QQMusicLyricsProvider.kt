// QQMusicLyricsProvider.kt

package com.bedivine.app.lyrics

import android.content.Context
import com.music.directlyrics.QQMusic
import com.bedivine.app.constants.EnableQQMusicKey
import com.bedivine.app.utils.dataStore
import com.bedivine.app.utils.get

object QQMusicLyricsProvider : LyricsProvider {
    override val name = "QQMusic"
    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableQQMusicKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = QQMusic.getLyrics(title, artist, duration)
}
