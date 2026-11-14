package com.bedivine.app.ui.screens.settings.importsync

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bedivine.app.R
import com.bedivine.app.constants.AccountEmailKey
import com.bedivine.app.constants.AccountNameKey
import com.bedivine.app.constants.YtExcludeImportsKey
import com.bedivine.app.constants.YtPullHistoryKey
import com.bedivine.app.constants.YtReportPlaysKey
import com.bedivine.app.constants.YtSyncNewPlaylistsKey
import com.bedivine.app.constants.YtmSyncKey
import com.bedivine.app.ui.component.AppSwitch
import com.bedivine.app.ui.component.DefaultDialog
import com.bedivine.app.ui.component.SettingsGroup
import com.bedivine.app.ui.component.SettingsGroupStyle
import com.bedivine.app.ui.component.SettingsItem
import com.bedivine.app.ui.component.SubSettingsScaffold
import com.bedivine.app.utils.rememberPreference
import com.bedivine.app.viewmodels.ImportSyncViewModel

@Composable
fun YouTubeSyncScreen(
    navController: NavController,
    viewModel: ImportSyncViewModel = hiltViewModel(),
) {
    val signedIn = rememberYouTubeSignedIn()
    val accountName by rememberPreference(AccountNameKey, "")
    val accountEmail by rememberPreference(AccountEmailKey, "")
    val (autoSync, onAutoSyncChange) = rememberPreference(YtmSyncKey, true)
    val (syncNewPlaylists, onSyncNewPlaylistsChange) = rememberPreference(YtSyncNewPlaylistsKey, false)
    val (reportPlays, onReportPlaysChange) = rememberPreference(YtReportPlaysKey, true)
    val (pullHistory, onPullHistoryChange) = rememberPreference(YtPullHistoryKey, false)
    val (excludeImports, onExcludeImportsChange) = rememberPreference(YtExcludeImportsKey, false)
    val state by viewModel.youTubeState.collectAsState()

    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmUpload by remember { mutableStateOf(false) }
    var confirmUndo by remember { mutableStateOf(false) }

    SubSettingsScaffold(
        title = stringResource(R.string.action_sync),
        onBack = { navController.navigateUp() },
    ) {
        SettingsGroup(
            title = stringResource(R.string.ytsync_account_group),
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                if (signedIn) {
                    SettingsItem(
                        title = { Text(accountName.ifBlank { stringResource(R.string.ytsync_signed_in) }) },
                        descriptionText = accountEmail.ifBlank { "YouTube Music" },
                        icon = painterResource(R.drawable.account),
                        iconShape = CircleShape,
                        trailingContent = {
                            TextButton(onClick = { confirmSignOut = true }) { Text(stringResource(R.string.module_sign_out)) }
                        },
                    )
                } else {
                    SettingsItem(
                        title = { Text(stringResource(R.string.module_sign_in_to, "YouTube")) },
                        descriptionText = stringResource(R.string.ytsync_signin_desc),
                        icon = painterResource(R.drawable.login),
                        iconShape = CircleShape,
                        onClick = { navController.navigate(YouTubeLoginRoute) },
                    )
                }
            ),
        )

        Spacer(Modifier.height(16.dp))

        SettingsGroup(
            title = stringResource(R.string.ytsync_keep_in_sync),
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                toggleItem(
                    title = stringResource(R.string.ytsync_sync_library_title),
                    description = stringResource(R.string.ytsync_sync_library_desc),
                    icon = R.drawable.sync,
                    checked = autoSync,
                    enabled = signedIn,
                    onChange = onAutoSyncChange,
                ),
                toggleItem(
                    title = stringResource(R.string.ytsync_new_playlists_title),
                    description = stringResource(R.string.ytsync_new_playlists_desc),
                    icon = R.drawable.playlist_add,
                    checked = syncNewPlaylists,
                    enabled = signedIn,
                    onChange = onSyncNewPlaylistsChange,
                ),
                toggleItem(
                    title = stringResource(R.string.ytsync_report_plays_title),
                    description = stringResource(R.string.ytsync_report_plays_desc),
                    icon = R.drawable.history,
                    checked = reportPlays,
                    enabled = signedIn,
                    onChange = onReportPlaysChange,
                ),
                toggleItem(
                    title = stringResource(R.string.ytsync_pull_history_title),
                    description = stringResource(R.string.ytsync_pull_history_desc),
                    icon = R.drawable.history,
                    checked = pullHistory,
                    enabled = signedIn,
                    onChange = onPullHistoryChange,
                ),
            ),
        )

        Spacer(Modifier.height(16.dp))

        SettingsGroup(
            title = stringResource(R.string.ytsync_other_apps),
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                toggleItem(
                    title = stringResource(R.string.yt_sync_exclude_title),
                    description = stringResource(R.string.yt_sync_exclude_desc),
                    icon = R.drawable.cloud,
                    checked = excludeImports,
                    enabled = true,
                    onChange = onExcludeImportsChange,
                ),
            ),
        )

        Spacer(Modifier.height(16.dp))

        SettingsGroup(
            title = stringResource(R.string.ytsync_now_group),
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                SettingsItem(
                    title = { Text(stringResource(R.string.devices_sync_now)) },
                    descriptionText = if (pullHistory) stringResource(R.string.ytsync_sync_now_desc_full) else stringResource(R.string.ytsync_sync_now_desc_basic),
                    icon = painterResource(R.drawable.refresh),
                    iconShape = CircleShape,
                    enabled = signedIn && !state.running,
                    onClick = viewModel::syncNow,
                ),
                SettingsItem(
                    title = { Text(stringResource(R.string.ytsync_pull_history_now_title)) },
                    descriptionText = stringResource(R.string.ytsync_pull_history_now_desc),
                    icon = painterResource(R.drawable.history),
                    iconShape = CircleShape,
                    enabled = signedIn && !state.running,
                    onClick = viewModel::pullHistoryNow,
                ),
                SettingsItem(
                    title = { Text(stringResource(R.string.yt_sync_undo_title)) },
                    descriptionText = stringResource(R.string.yt_sync_undo_desc),
                    icon = painterResource(R.drawable.undo),
                    iconShape = CircleShape,
                    enabled = signedIn && state.canUndo && !state.running,
                    onClick = { confirmUndo = true },
                ),
                SettingsItem(
                    title = { Text(stringResource(R.string.ytsync_upload_title)) },
                    descriptionText = stringResource(R.string.ytsync_upload_desc),
                    detail = stringResource(R.string.ytsync_upload_detail),
                    icon = painterResource(R.drawable.cloud),
                    iconShape = CircleShape,
                    enabled = signedIn && !state.running,
                    onClick = { confirmUpload = true },
                ),
            ),
        )

        if (state.running || state.message != null) {
            Spacer(Modifier.height(16.dp))
            ImportInfoCard {
                if (state.running) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(state.label, style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    Text(state.message.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = viewModel::clearYouTubeMessage) { Text(stringResource(R.string.devices_ok)) }
                }
            }
        }

        if (!signedIn) {
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.ytsync_import_no_account_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmSignOut) {
        DefaultDialog(
            onDismiss = { confirmSignOut = false },
            horizontalAlignment = Alignment.Start,
            title = { Text(stringResource(R.string.ytsync_sign_out_title)) },
            buttons = {
                TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = {
                    confirmSignOut = false
                    viewModel.signOut()
                }) { Text(stringResource(R.string.module_sign_out)) }
            },
        ) {
            Text(
                stringResource(R.string.ytsync_sign_out_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    if (confirmUndo) {
        DefaultDialog(
            onDismiss = { confirmUndo = false },
            horizontalAlignment = Alignment.Start,
            title = { Text(stringResource(R.string.yt_sync_undo_dialog_title)) },
            buttons = {
                TextButton(onClick = { confirmUndo = false }) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = {
                    confirmUndo = false
                    viewModel.undoLastSync(withYouTube = false)
                }) { Text(stringResource(R.string.yt_sync_undo_phone)) }
                TextButton(
                    enabled = signedIn,
                    onClick = {
                        confirmUndo = false
                        viewModel.undoLastSync(withYouTube = true)
                    },
                ) { Text(stringResource(R.string.yt_sync_undo_both)) }
            },
        ) {
            Text(
                stringResource(R.string.yt_sync_undo_dialog_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.yt_sync_undo_both_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmUpload) {
        DefaultDialog(
            onDismiss = { confirmUpload = false },
            horizontalAlignment = Alignment.Start,
            title = { Text(stringResource(R.string.ytsync_upload_dialog_title)) },
            buttons = {
                TextButton(onClick = { confirmUpload = false }) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = {
                    confirmUpload = false
                    viewModel.uploadLocalPlaylists()
                }) { Text(stringResource(R.string.create)) }
            },
        ) {
            Text(
                stringResource(R.string.ytsync_upload_dialog_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun toggleItem(
    title: String,
    description: String,
    icon: Int,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) = SettingsItem(
    title = { Text(title) },
    highlightKey = title,
    descriptionText = description,
    icon = painterResource(icon),
    iconShape = CircleShape,
    enabled = enabled,
    onClick = { onChange(!checked) },
    trailingContent = { AppSwitch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
)
