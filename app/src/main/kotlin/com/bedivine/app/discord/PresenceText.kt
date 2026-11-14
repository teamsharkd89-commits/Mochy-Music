// PresenceText.kt

package com.bedivine.app.discord

import com.bedivine.app.R

/** The words the user can drop into any status line or button, and Discord's length rules. */
object PresenceText {
    const val AppName = "BeDivine"

    /** the placeholder, and what it means, in the order the Discord page lists them */
    val Placeholders = listOf(
        "{name}" to R.string.misc_discord_ph_name,
        "{artist}" to R.string.misc_discord_ph_artist,
        "{album}" to R.string.misc_discord_ph_album,
        "{app}" to R.string.misc_discord_ph_app,
        "{quality}" to R.string.misc_discord_ph_quality,
        "{apple}" to R.string.misc_discord_ph_apple,
    )

    fun fill(template: String, track: NowPlaying): String = template
        .replace("{name}", track.title)
        .replace("{artist}", track.artist)
        .replace("{album}", track.album.orEmpty())
        .replace("{app}", AppName)
        .replace("{quality}", track.quality.orEmpty())
        .replace("{apple}", "")

    /**
     * A filled-in line as Discord takes it: null when there's nothing left to show (so the line is
     * left off), padded when it's one letter (Discord turns those down), cut at Discord's limit.
     */
    fun line(template: String, track: NowPlaying, maxLength: Int = 128): String? {
        val text = fill(template, track).trim().replace(Whitespace, " ")
        return when {
            text.isEmpty() -> null
            text.length == 1 -> text + Blank
            text.length > maxLength -> text.take(maxLength - 1).trimEnd() + "…"
            else -> text
        }
    }

    private val Whitespace = Regex("\\s+")

    /** an empty-looking character that still counts as one, for Discord's two-letter minimum */
    private const val Blank = "⠀"
}
