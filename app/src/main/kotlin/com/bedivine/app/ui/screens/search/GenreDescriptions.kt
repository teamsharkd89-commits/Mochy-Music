// GenreDescriptions.kt

package com.bedivine.app.ui.screens.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bedivine.app.R

private val Descriptions: Map<String, Int> = mapOf(

    "chill" to R.string.genre_chill,
    "commute" to R.string.genre_commute,
    "energize" to R.string.genre_energize,
    "feel good" to R.string.genre_feel_good,
    "focus" to R.string.genre_focus,
    "gaming" to R.string.genre_gaming,
    "party" to R.string.genre_party,
    "romance" to R.string.genre_romance,
    "sad" to R.string.genre_sad,
    "sleep" to R.string.genre_sleep,
    "workout" to R.string.genre_workout,
    "african" to R.string.genre_african,
    "arabic" to R.string.genre_arabic,
    "blues" to R.string.genre_blues,
    "bollywood & indian" to R.string.genre_bollywood_indian,
    "country" to R.string.genre_country,
    "dance & electronic" to R.string.genre_dance_electronic,
    "decades" to R.string.genre_decades,
    "family" to R.string.genre_family,
    "folk & acoustic" to R.string.genre_folk_acoustic,
    "hip hop" to R.string.genre_hip_hop,
    "indie & alternative" to R.string.genre_indie_alternative,
    "j-pop" to R.string.genre_j_pop,
    "jazz" to R.string.genre_jazz,
    "k-pop" to R.string.genre_k_pop,
    "latin" to R.string.genre_latin,
    "metal" to R.string.genre_metal,
    "pop" to R.string.genre_pop,
    "r&b & soul" to R.string.genre_rnb_soul,
    "reggae" to R.string.genre_reggae,
    "rock" to R.string.genre_rock,
    "soundtracks & musicals" to R.string.genre_soundtracks_musicals
)

@Composable
fun genreDescription(title: String): String {
    if (title.isBlank()) return ""
    val key = title.lowercase()
        .replace("&amp;", "&")
        .replace("-", " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    Descriptions[key]?.let { return stringResource(it) }

    Descriptions.entries
        .firstOrNull { (candidate, _) -> key.startsWith(candidate) || candidate.startsWith(key) }
        ?.let { return stringResource(it.value) }

    return stringResource(R.string.genre_fallback, title)
}
