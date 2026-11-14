// ImmersiveLyrics.kt
//
// The lyrics shown on the main player while the immersive mode hides everything else:
// the song's lines, the sung one lit, following playback. Unsynced lyrics show as a
// static list, the way the lyrics page shows them.

package com.bedivine.app.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bedivine.app.LocalPlayerConnection
import com.bedivine.app.R
import com.bedivine.app.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.bedivine.app.lyrics.LyricsEntry
import com.bedivine.app.lyrics.LyricsUtils
import com.bedivine.app.viewmodels.LyricsScreenViewModel

/**
 * The song's lyrics in the space the hidden controls leave behind. The sung line sits
 * about a third down the list and the list follows it; a manual scroll is overridden on
 * the next line change, since this list only exists while nothing else is shown — free
 * reading belongs to the full lyrics page.
 */
@Composable
internal fun ImmersiveLyricsList(
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)
    val progress by playerConnection.progressState.collectAsState()
    val lyricsOffsetMs by playerConnection.lyricsOffsetMs.collectAsState()
    // the bottom card does this too, but it isn't composed with the volume bar instead
    val loader: LyricsScreenViewModel = hiltViewModel()
    LaunchedEffect(mediaMetadata?.id) { mediaMetadata?.let(loader::ensureLyricsLoaded) }

    val parsed = remember(lyricsEntity?.lyrics) {
        val raw = lyricsEntity?.lyrics
        if (raw.isNullOrBlank() || raw == LYRICS_NOT_FOUND) {
            emptyList<LyricsEntry>() to false
        } else {
            val timed = LyricsUtils.parseLyricsCached(raw)
            when {
                timed.isEmpty() -> LyricsUtils.parsePlainLyrics(raw) to true
                LyricsUtils.isUnsynced(timed) -> timed to true
                else -> timed to false
            }
        }
    }
    val lines = parsed.first
    val unsynced = parsed.second

    val activeIndex by remember(lines, unsynced) {
        derivedStateOf {
            if (unsynced) -1 else LyricsUtils.findCurrentLineIndex(lines, progress.position + lyricsOffsetMs)
        }
    }

    // opens already on the playing line, so the list doesn't scroll past every earlier
    // line on its way in while it fades up
    val initialRow = Snapshot.withoutReadObservation {
        activeIndex.coerceIn(0, (lines.size - 1).coerceAtLeast(0))
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialRow)
    LaunchedEffect(activeIndex) {
        val target = activeIndex
        if (target !in lines.indices) return@LaunchedEffect
        val viewport = listState.layoutInfo.viewportSize.height
        val offset = (viewport * 0.35f).toInt()
        listState.animateScrollToItem(target, scrollOffset = offset)
    }

    Box(modifier = modifier) {
        if (lines.isEmpty()) {
            if (lyricsEntity == null) {
                LyricsLoadingDots(modifier = Modifier.align(Alignment.Center))
            } else {
                Text(
                    text = stringResource(R.string.video_mode_no_lyrics),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithCache {
                        val fade = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.12f to Color.Black,
                            0.85f to Color.Black,
                            1f to Color.Transparent,
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(brush = fade, blendMode = BlendMode.DstIn)
                        }
                    },
            ) {
                itemsIndexed(lines, key = { index, _ -> index }) { index, entry ->
                    val active = index == activeIndex
                    Text(
                        text = entry.text,
                        fontSize = if (active) 22.sp else 18.sp,
                        lineHeight = 27.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = Color.White.copy(alpha = if (active) 0.95f else 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    )
                }
            }
        }
    }
}
