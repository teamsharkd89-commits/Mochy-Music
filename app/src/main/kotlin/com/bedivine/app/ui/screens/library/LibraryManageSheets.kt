// LibraryManageSheets.kt
//
// "Manage imports", "Manage local" and "Manage cache": big tiles for the main actions and a row per
// other action, as in the mocks. Anything that takes music out of the library asks once more on a
// sheet pushed over this one.

package com.bedivine.app.ui.screens.library

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bedivine.app.R
import com.bedivine.app.db.entities.Song
import com.bedivine.app.importer.ImportSource
import com.bedivine.app.ui.component.PopupSheetHandle
import com.bedivine.app.ui.component.PopupSheetState
import com.bedivine.app.ui.component.SheetIcon
import com.bedivine.app.ui.component.SheetPillButton
import com.bedivine.app.ui.player.menu.MenuRowSurface
import com.bedivine.app.ui.screens.search.SearchColors
import com.bedivine.app.viewmodels.LibraryImportViewModel

/**
 * [sources] are the services that have brought music in, one "Delete ... imports" row each.
 * [onResyncAll] checks every imported playlist for new songs: a long tile under the two others.
 */
fun PopupSheetState.showManageImportsSheet(
    sources: List<ImportSource>,
    viewModel: LibraryImportViewModel,
    onResyncAll: () -> Unit,
) {
    var sheet: PopupSheetHandle? = null
    fun confirmThen(title: String, message: String, action: String, run: () -> Unit) =
        showConfirm(title, message, action) {
            run()
            sheet?.dismiss()
        }
    sheet = showWithHandle {
        val deleteAllTitle = stringResource(R.string.library_delete_all_imports_title)
        val deleteAllMessage = stringResource(R.string.library_delete_all_imports_message)
        val deleteAllAction = stringResource(R.string.library_delete_all_imports)
        val undoTitle = stringResource(R.string.library_undo_last_import_title)
        val undoMessage = stringResource(R.string.library_undo_last_import_message)
        val undoAction = stringResource(R.string.library_undo_import)
        val deleteAction = stringResource(R.string.eq_delete_selected)
        ManageLayout(
            ManageAction(R.drawable.delete, deleteAllAction) {
                confirmThen(
                    title = deleteAllTitle,
                    message = deleteAllMessage,
                    action = deleteAllAction,
                    run = viewModel::deleteAllImports,
                )
            },
            ManageAction(R.drawable.undo, stringResource(R.string.library_undo_import_session)) {
                confirmThen(
                    title = undoTitle,
                    message = undoMessage,
                    action = undoAction,
                    run = viewModel::undoLatestImport,
                )
            },
            wide = ManageAction(R.drawable.sync, stringResource(R.string.library_resync_all)) {
                sheet?.dismiss()
                onResyncAll()
            },
            rows = sources.map { source ->
                val rowLabel = stringResource(R.string.library_delete_source_imports, stringResource(source.labelRes))
                val rowTitle = stringResource(R.string.library_delete_source_imports_title, stringResource(source.labelRes))
                val rowMessage = stringResource(R.string.library_delete_source_imports_message, stringResource(source.labelRes)) +
                    if (source == ImportSource.YOUTUBE_MUSIC) stringResource(R.string.library_delete_source_imports_message_yt) else ""
                ManageAction(R.drawable.delete, rowLabel) {
                    confirmThen(
                        title = rowTitle,
                        message = rowMessage,
                        action = deleteAction,
                        run = { viewModel.deleteImportsFrom(source) },
                    )
                }
            },
        )
    }
}

/**
 * [onRescan], [onAddFolder] and [onRemoveFromList] open their own sheets or pickers, so this one
 * closes first. Re-scan also brings back missing covers and reads the picked folders again.
 */
fun PopupSheetState.showManageLocalSheet(
    viewModel: LibraryImportViewModel,
    onRescan: () -> Unit,
    onAddFolder: () -> Unit,
    onRemoveFromList: () -> Unit,
) {
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle {
        val undoTitle = stringResource(R.string.library_undo_last_local_import_title)
        val undoMessage = stringResource(R.string.library_undo_last_local_import_message)
        val undoAction = stringResource(R.string.library_undo_import)
        ManageLayout(
            ManageAction(R.drawable.refresh, stringResource(R.string.library_rescan_local)) {
                sheet?.dismiss()
                onRescan()
            },
            ManageAction(R.drawable.delete, stringResource(R.string.library_delete_from_list)) {
                sheet?.dismiss()
                onRemoveFromList()
            },
            wide = ManageAction(R.drawable.library_add, stringResource(R.string.library_add_music_folder)) {
                sheet?.dismiss()
                onAddFolder()
            },
            rows = listOf(
                ManageAction(R.drawable.undo, stringResource(R.string.library_undo_import_session)) {
                    showConfirm(
                        title = undoTitle,
                        message = undoMessage,
                        action = undoAction,
                    ) {
                        viewModel.undoLatestLocalImport()
                        sheet?.dismiss()
                    }
                },
            ),
        )
    }
}

/**
 * [cachedSongs] are the songs "Remove songs" lists. Clearing goes further and also drops songs that
 * only got partly cached; downloads are kept apart and never touched.
 */
fun PopupSheetState.showManageCachedSheet(
    cachedSongs: List<Song>,
    onClearCache: () -> Unit,
    onRemove: (Set<String>) -> Unit,
) {
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle {
        val clearTitle = stringResource(R.string.library_clear_cache_title)
        val clearMessage = stringResource(R.string.library_clear_cache_message)
        val clearAction = stringResource(R.string.storage_clear_cache_action)
        ManageLayout(
            ManageAction(R.drawable.clear_all, clearAction) {
                showConfirm(
                    title = clearTitle,
                    message = clearMessage,
                    action = clearAction,
                ) {
                    onClearCache()
                    sheet?.dismiss()
                }
            },
            ManageAction(R.drawable.delete, stringResource(R.string.library_remove_songs)) {
                sheet?.dismiss()
                showRemoveCachedSheet(cachedSongs, onRemove)
            },
            rows = emptyList(),
        )
    }
}

/** One more look before music leaves the phone or the library, pushed over whatever asked. */
internal fun PopupSheetState.showConfirm(title: String, message: String, action: String, onConfirm: () -> Unit) {
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle(
        buttonBar = {
            SheetPillButton(
                text = action,
                onClick = {
                    sheet?.dismiss()
                    onConfirm()
                },
            )
        },
    ) {
        Text(text = title, color = SearchColors.Primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(text = message, color = SearchColors.Secondary, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(12.dp))
    }
}

private class ManageAction(@DrawableRes val icon: Int, val label: String, val onClick: () -> Unit)

@Composable
private fun ManageLayout(first: ManageAction, second: ManageAction, wide: ManageAction? = null, rows: List<ManageAction>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(bottom = 16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ManageTile(first, Modifier.weight(1f))
            ManageTile(second, Modifier.weight(1f))
        }
        // a third big action spans the width under the two, rather than squeezing three into a row
        wide?.let { ManageTile(it, Modifier.fillMaxWidth()) }
        rows.forEach { ManageRow(it) }
    }
}

/** A tall tile: its icon in the top corner, the label at the bottom. */
@Composable
private fun ManageTile(action: ManageAction, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .heightIn(min = 92.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MenuRowSurface)
            .clickable(role = Role.Button, onClick = action.onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        ManageIcon(action.icon)
        Spacer(Modifier.height(14.dp))
        ManageLabel(action.label)
    }
}

@Composable
private fun ManageRow(action: ManageAction) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MenuRowSurface)
            .clickable(role = Role.Button, onClick = action.onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        ManageIcon(action.icon)
        Spacer(Modifier.width(14.dp))
        ManageLabel(action.label)
    }
}

/** The action's icon in plain white. It used to sit in a light circle; the sheets dropped those. */
@Composable
private fun ManageIcon(@DrawableRes icon: Int) {
    SheetIcon(icon, size = 22.dp)
}

@Composable
private fun ManageLabel(text: String) {
    Text(
        text = text,
        color = SearchColors.Primary,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
