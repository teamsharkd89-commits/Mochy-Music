package com.bedivine.app.ui.screens.settings.importsync

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bedivine.app.R
import com.bedivine.app.importer.LinkSource
import com.bedivine.app.importer.account.AccountService
import com.bedivine.app.ui.component.SettingsGroup
import com.bedivine.app.ui.component.SettingsGroupStyle
import com.bedivine.app.ui.component.SettingsItem
import com.bedivine.app.ui.component.SubSettingsScaffold
import com.bedivine.app.viewmodels.ImportSyncViewModel
import androidx.compose.ui.res.stringResource

@Composable
fun ImportProvidersScreen(
    navController: NavController,
    viewModel: ImportSyncViewModel = hiltViewModel(),
) {
    var linkSource by rememberSaveable { mutableStateOf<LinkSource?>(null) }
    val bedivineBackupLabel = stringResource(R.string.misc_import_bedivine_backup)

    // some file managers label .csv as text/plain or octet-stream, so don't filter too hard
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.loadFile(uri, sourceLabel = bedivineBackupLabel)
    }

    SubSettingsScaffold(
        title = stringResource(R.string.misc_import_import),
        onBack = { navController.navigateUp() },
    ) {
        SettingsGroup(
            title = stringResource(R.string.misc_import_signin_choose),
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                AccountService.SPOTIFY to stringResource(R.string.misc_import_spotify_desc),
                AccountService.APPLE_MUSIC to stringResource(R.string.misc_import_apple_desc),
                AccountService.YOUTUBE_MUSIC to stringResource(R.string.misc_import_ytm_desc),
                AccountService.TIDAL to stringResource(R.string.misc_import_tidal_desc),
            ).map { (service, description) ->
                SettingsItem(
                    title = { Text(service.label) },
                    highlightKey = "${service.label} account",
                    descriptionText = description,
                    icon = painterResource(R.drawable.login),
                    iconShape = CircleShape,
                    onClick = { navController.navigate(accountImportRoute(service)) },
                )
            },
        )

        Spacer(Modifier.height(16.dp))

        SettingsGroup(
            title = stringResource(R.string.misc_import_paste_link_title),
            style = SettingsGroupStyle.Grouped,
            items = LinkSource.entries.map { source ->
                SettingsItem(
                    title = { Text(source.label) },
                    highlightKey = source.label,
                    descriptionText = when (source) {
                        LinkSource.SPOTIFY -> stringResource(R.string.misc_import_link_spotify)
                        LinkSource.APPLE_MUSIC -> stringResource(R.string.misc_import_link_apple)
                        LinkSource.DEEZER -> stringResource(R.string.misc_import_link_deezer)
                        LinkSource.YOUTUBE -> stringResource(R.string.misc_import_link_yt)
                    },
                    icon = painterResource(R.drawable.link),
                    iconShape = CircleShape,
                    onClick = { linkSource = source },
                )
            },
        )


        Spacer(Modifier.height(16.dp))

        SettingsGroup(
            title = stringResource(R.string.misc_import_from_file),
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                SettingsItem(
                    title = { Text(bedivineBackupLabel) },
                    highlightKey = "BeDivine backup",
                    descriptionText = stringResource(R.string.misc_import_backup_desc),
                    icon = painterResource(R.drawable.backup),
                    iconShape = CircleShape,
                    onClick = { backupPicker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) },
                ),
                SettingsItem(
                    title = { Text("TuneMyMusic CSV") },
                    highlightKey = "TuneMyMusic CSV",
                    descriptionText = stringResource(R.string.misc_import_tmm_desc),
                    icon = painterResource(R.drawable.web_link),
                    iconShape = CircleShape,
                    onClick = { navController.navigate(ImportTuneMyMusicRoute) },
                ),
            ),
        )

        Spacer(Modifier.height(16.dp))

        SettingsGroup(
            title = stringResource(R.string.misc_import_other_services_title),
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                SettingsItem(
                    title = { Text("Amazon Music, SoundCloud, Pandora…") },
                    descriptionText = stringResource(R.string.misc_import_goes_tmm),
                    detail = stringResource(R.string.misc_import_goes_tmm_detail),
                    icon = painterResource(R.drawable.library_music),
                    iconShape = CircleShape,
                    onClick = { navController.navigate(ImportTuneMyMusicRoute) },
                ),
            ),
        )
    }

    linkSource?.let { source ->
        LinkInputDialog(
            source = source,
            onDismiss = { linkSource = null },
            onSubmit = { link ->
                linkSource = null
                viewModel.loadLink(link)
            },
        )
    }

    PendingImportHost(viewModel = viewModel, navController = navController)
}
