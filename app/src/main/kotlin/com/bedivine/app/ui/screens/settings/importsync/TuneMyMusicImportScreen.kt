package com.bedivine.app.ui.screens.settings.importsync

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bedivine.app.R
import com.bedivine.app.ui.component.SettingsGroup
import com.bedivine.app.ui.component.SettingsGroupStyle
import com.bedivine.app.ui.component.SettingsItem
import com.bedivine.app.ui.component.SubSettingsScaffold
import com.bedivine.app.viewmodels.ImportSyncViewModel
import androidx.compose.ui.res.stringResource

private const val TUNEMYMUSIC_URL = "https://www.tunemymusic.com/transfer"

@Composable
fun TuneMyMusicImportScreen(
    navController: NavController,
    viewModel: ImportSyncViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.loadFile(uri, sourceLabel = "TuneMyMusic")
    }

    SubSettingsScaffold(
        title = "TuneMyMusic CSV",
        onBack = { navController.navigateUp() },
    ) {
        ImportInfoCard {
            Text(
                stringResource(R.string.misc_import_tmm_works),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Step(1, stringResource(R.string.misc_import_tmm_step1))
                Step(2, stringResource(R.string.misc_import_tmm_step2))
                Step(3, stringResource(R.string.misc_import_tmm_step3))
                Step(4, stringResource(R.string.misc_import_tmm_step4))
            }
        }

        Spacer(Modifier.height(16.dp))

        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                SettingsItem(
                    title = { Text(stringResource(R.string.misc_import_open_tmm)) },
                    descriptionText = stringResource(R.string.misc_import_tmm_browser),
                    icon = painterResource(R.drawable.web_link),
                    iconShape = CircleShape,
                    onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TUNEMYMUSIC_URL)))
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, context.getString(R.string.misc_import_no_browser), Toast.LENGTH_SHORT).show()
                        }
                    },
                ),
                SettingsItem(
                    title = { Text(stringResource(R.string.misc_import_choose_csv)) },
                    descriptionText = stringResource(R.string.misc_import_exportify),
                    icon = painterResource(R.drawable.playlist_add),
                    iconShape = CircleShape,
                    onClick = { csvPicker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) },
                ),
            ),
        )
    }

    PendingImportHost(viewModel = viewModel, navController = navController)
}

@Composable
private fun Step(number: Int, text: String) {
    Row {
        Text("$number.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
