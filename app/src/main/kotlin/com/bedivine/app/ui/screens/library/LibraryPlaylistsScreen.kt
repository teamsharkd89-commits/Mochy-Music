// LibraryPlaylistsScreen.kt

package com.bedivine.app.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.bedivine.app.importer.ImportSource
import com.bedivine.app.ui.component.LocalMenuState
import com.bedivine.app.ui.menu.PlaylistMenu
import com.bedivine.app.viewmodels.LibraryHomeViewModel
import com.bedivine.app.viewmodels.LibraryShelfKind
import androidx.compose.ui.res.stringResource
import com.bedivine.app.R

/**
 * The library's playlists, or with a [shelf] the Local or Cached tab's: only the playlists that
 * have one of that tab's songs in them. With [source], only the playlists imported from that service.
 */
@Composable
fun LibraryPlaylistsScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    shelf: LibraryShelfKind? = null,
    source: ImportSource? = null,
) {
    val viewModel: LibraryHomeViewModel = hiltViewModel()
    val all by (shelf?.let(viewModel::shelf)?.playlists ?: viewModel.playlists).collectAsStateWithLifecycle()
    val playlistSources by viewModel.playlistSources.collectAsState()
    val playlists = remember(all, playlistSources, source) {
        if (source == null) all else all.filter { playlistSources[it.id] == source }
    }
    val menuState = LocalMenuState.current
    val coroutineScope = rememberCoroutineScope()
    // subtitleOf is a plain (T) -> String? (LibraryEntityListScreen also calls it inside
    // remember{} for search), so the count string comes from Resources here
    val resources = LocalContext.current.resources

    LibraryEntityListScreen(
        title = stringResource(R.string.misc_libpl_title),
        subtitle = when {
            source != null -> stringResource(R.string.misc_libpl_from, stringResource(source.labelRes))
            shelf == LibraryShelfKind.Local -> stringResource(R.string.misc_libpl_device)
            shelf == LibraryShelfKind.Cached -> stringResource(R.string.misc_libpl_cache)
            else -> stringResource(R.string.misc_libpl_all)
        },
        searchPlaceholder = stringResource(R.string.misc_libpl_find),
        items = playlists,
        idOf = { it.id },
        nameOf = { it.playlist.name },
        subtitleOf = { resources.getString(R.string.misc_lib_n_songs, it.songCount) },
        thumbnailOf = { it.thumbnails.firstOrNull() },
        // the same key Home's cards use, so the playlist opens growing out of its cover
        sharedElementKeyOf = { "playlist-${it.id}" },
        onClick = { playlist -> navController.navigate("local_playlist/${playlist.id}") },
        onLongClick = { playlist ->
            menuState.show {
                PlaylistMenu(
                    playlist = playlist,
                    coroutineScope = coroutineScope,
                    onDismiss = menuState::dismiss,
                    navController = navController,
                )
            }
        },
        modifier = modifier,
        pureBlack = pureBlack,
    )
}
