package com.bedivine.app.ui.screens.settings.importsync

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bedivine.app.R
import com.bedivine.app.ui.component.DefaultDialog
import com.bedivine.app.ui.component.LocalZoomOutOverlayState
import com.bedivine.app.ui.component.SettingsGroup
import com.bedivine.app.ui.component.SettingsGroupStyle
import com.bedivine.app.ui.component.SettingsItem
import com.bedivine.app.ui.component.SubSettingsScaffold
import com.bedivine.app.ui.screens.devices.rememberDevicesLink
import com.bedivine.app.ui.screens.devices.showDeviceIntegration
import com.bedivine.app.viewmodels.ImportSyncViewModel
import java.time.LocalDate

/** experimental hub: import from other services, sync a youtube account, tunemymusic csv, and backup */
@Composable
fun ImportSyncScreen(
    navController: NavController,
    viewModel: ImportSyncViewModel = hiltViewModel(),
) {
    val progress by viewModel.importProgress.collectAsState()
    val exportMessage by viewModel.exportMessage.collectAsState()
    val signedIn = rememberYouTubeSignedIn()
    val sheets = LocalZoomOutOverlayState.current
    val devices = rememberDevicesLink()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) viewModel.export(uri)
    }

    SubSettingsScaffold(
        title = stringResource(R.string.misc_import_title),
        onBack = { navController.navigateUp() },
    ) {
        if (progress.isRunning || progress.isDone) {
            val resync = progress.resync
            ImportInfoCard {
                Text(
                    text = when {
                        resync != null && progress.isRunning -> stringResource(R.string.misc_import_resyncing, resync.playlistName)
                        progress.isRunning -> stringResource(R.string.misc_import_importing, progress.processedTracks, progress.totalTracks)
                        progress.cancelled -> stringResource(R.string.misc_import_stopped)
                        resync != null && resync.failed.size < resync.playlists ->
                            (if (resync.playlists == 1) stringResource(R.string.misc_import_resynced_one, resync.playlistName) else stringResource(R.string.misc_import_resynced_many, resync.playlists)) +
                                if (resync.added == 1) stringResource(R.string.misc_import_one_new_song) else stringResource(R.string.misc_import_n_new_songs, resync.added)
                        progress.error != null -> stringResource(R.string.misc_import_failed)
                        else -> stringResource(R.string.misc_import_finished, progress.matchedTracks, progress.totalTracks)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (progress.isRunning && progress.totalTracks > 0) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { progress.processedTracks.toFloat() / progress.totalTracks },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            SettingsGroup(
                style = SettingsGroupStyle.Grouped,
                items = listOf(
                    SettingsItem(
                        title = { Text(if (progress.isRunning) stringResource(R.string.misc_import_see_progress) else stringResource(R.string.misc_import_see_results)) },
                        icon = painterResource(R.drawable.playlist_play),
                        iconShape = CircleShape,
                        onClick = { navController.navigate(ImportProgressRoute) { launchSingleTop = true } },
                    )
                ),
            )
            Spacer(Modifier.height(16.dp))
        }

        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                SettingsItem(
                    title = { Text(stringResource(R.string.misc_import_import)) },
                    highlightKey = "Import",
                    descriptionText = stringResource(R.string.misc_import_signin_pick),
                    detail = stringResource(R.string.misc_import_signin_pick_detail),
                    icon = painterResource(R.drawable.playlist_add),
                    iconShape = CircleShape,
                    onClick = { navController.navigate(ImportProvidersRoute) },
                ),
                SettingsItem(
                    title = { Text(stringResource(R.string.action_sync)) },
                    highlightKey = "Sync",
                    descriptionText = if (signedIn) stringResource(R.string.misc_import_yt_connected)
                    else stringResource(R.string.misc_import_yt_connect),
                    icon = painterResource(R.drawable.sync),
                    iconShape = CircleShape,
                    onClick = { navController.navigate(YouTubeSyncRoute) },
                ),
                SettingsItem(
                    title = { Text(stringResource(R.string.devices_title)) },
                    highlightKey = "Device integration",
                    descriptionText = stringResource(R.string.devices_row_detail),
                    icon = painterResource(R.drawable.smartphone),
                    iconShape = CircleShape,
                    onClick = { sheets.showDeviceIntegration(devices) },
                ),
                SettingsItem(
                    title = { Text("TuneMyMusic CSV") },
                    highlightKey = "TuneMyMusic CSV",
                    descriptionText = stringResource(R.string.misc_import_other_services),
                    detail = stringResource(R.string.misc_import_other_services_detail),
                    icon = painterResource(R.drawable.web_link),
                    iconShape = CircleShape,
                    onClick = { navController.navigate(ImportTuneMyMusicRoute) },
                ),
            ),
        )

        Spacer(Modifier.height(16.dp))

        SettingsGroup(
            title = stringResource(R.string.action_backup),
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                SettingsItem(
                    title = { Text(stringResource(R.string.misc_import_export_csv)) },
                    highlightKey = "Export to BeDivine CSV",
                    descriptionText = stringResource(R.string.misc_import_liked_songs_playlists),
                    detail = stringResource(R.string.misc_import_export_detail),
                    icon = painterResource(R.drawable.backup),
                    iconShape = CircleShape,
                    onClick = { exportLauncher.launch("bedivine-library-${LocalDate.now()}.csv") },
                ),
            ),
        )
    }

    exportMessage?.let { message ->
        val working = message == stringResource(R.string.misc_import_exporting)
        DefaultDialog(
            onDismiss = { if (!working) viewModel.clearExportMessage() },
            buttons = {
                if (!working) TextButton(onClick = viewModel::clearExportMessage) { Text(stringResource(R.string.devices_ok)) }
            },
        ) {
            Text(message, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
