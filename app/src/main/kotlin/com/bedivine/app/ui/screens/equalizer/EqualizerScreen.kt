// audio lab's equalizer: one curve shaped as a graph or as sliders, with an optional headphone correction

package com.bedivine.app.ui.screens.equalizer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bedivine.app.R
import com.bedivine.app.eq.EqPreset
import com.bedivine.app.eq.EqPresets
import com.bedivine.app.eq.EqSettings
import com.bedivine.app.ui.component.ActionPromptDialog
import com.bedivine.app.ui.component.SubSettingsScaffold
import com.bedivine.app.ui.component.TextFieldDialog
import com.bedivine.app.ui.screens.audiolab.LabCard
import com.bedivine.app.ui.screens.audiolab.LabChip
import com.bedivine.app.ui.screens.audiolab.LabChips
import com.bedivine.app.ui.screens.audiolab.LabSectionLabel
import com.bedivine.app.ui.screens.audiolab.LabSegmented
import com.bedivine.app.ui.screens.audiolab.LabSlider
import com.bedivine.app.ui.screens.audiolab.LabTextAction
import com.bedivine.app.ui.screens.audiolab.LabToggleRow
import com.bedivine.app.ui.screens.audiolab.ListenToAudio
import com.bedivine.app.ui.screens.audiolab.animatedDim
import com.bedivine.app.viewmodels.AudioLabViewModel
import com.bedivine.app.viewmodels.isFlat
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource

private val Tabs = listOf("Graph", "Bands", "AutoEQ")

@Composable
fun EqualizerScreen(
    navController: NavController,
    viewModel: AudioLabViewModel = hiltViewModel(),
) {
    val eq by viewModel.eq.collectAsState()
    val presets by viewModel.presets.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectedNode by rememberSaveable { mutableIntStateOf(0) }
    var savingPreset by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<EqPreset?>(null) }

    // the graph's spectrum listens while this page is open
    ListenToAudio()

    SubSettingsScaffold(
        title = stringResource(R.string.equalizer),
        backLabel = stringResource(R.string.settings_audio_lab_category),
        onBack = { navController.navigateUp() },
    ) {
        LabCard(padding = 10.dp) {
            LabToggleRow(
                title = stringResource(R.string.equalizer),
                subtitle = statusLine(eq, presets),
                checked = eq.enabled,
                onCheckedChange = viewModel::setEnabled,
                leadingDot = true,
            )
        }
        Spacer(Modifier.height(14.dp))
        LabSegmented(options = Tabs, selectedIndex = tab, onSelect = { tab = it })
        Spacer(Modifier.height(18.dp))

        val dim = animatedDim(!eq.enabled)
        Column(Modifier.fillMaxWidth().graphicsLayer { alpha = dim }) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (fadeIn(tween(220, 60)) + slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 420f)) { it / 8 * dir }) togetherWith fadeOut(tween(120))
                },
                label = "eqTab",
            ) { page ->
                Column(Modifier.fillMaxWidth()) {
                    when (page) {
                        0, 1 -> {
                            Presets(
                                eq = eq,
                                presets = presets,
                                onPick = viewModel::applyPreset,
                                onReset = viewModel::reset,
                                onSave = { savingPreset = true },
                                onLongPress = { id -> deleting = presets.firstOrNull { it.id == id } },
                            )
                            Spacer(Modifier.height(18.dp))
                            if (page == 0) {
                                EqGraphSection(
                                    nodes = eq.nodes,
                                    selected = selectedNode,
                                    onSelect = { selectedNode = it },
                                    onMove = { i, f, g -> viewModel.updateNode(i) { it.copy(frequency = f, gain = g) } },
                                    onAdd = { f, g -> viewModel.addNode(f, g).takeIf { it >= 0 }?.let { selectedNode = it } },
                                    onUpdate = viewModel::updateNode,
                                    onRemove = { i ->
                                        viewModel.removeNode(i)
                                        selectedNode = (i - 1).coerceAtLeast(0)
                                    },
                                    canAdd = eq.nodes.size < AudioLabViewModel.MaxNodes,
                                )
                            } else {
                                EqBandsSection(
                                    nodes = eq.nodes,
                                    onGain = { i, g -> viewModel.updateNode(i) { it.copy(gain = g) } },
                                )
                            }
                        }
                        else -> AutoEqTab(viewModel = viewModel, eq = eq)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Output(eq = eq, headroom = viewModel.autoHeadroom(eq), viewModel = viewModel)
        }
        Spacer(Modifier.height(120.dp))
    }

    if (savingPreset) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.misc_eq_save_preset)) },
            placeholder = { Text(stringResource(R.string.misc_eq_my_earbuds)) },
            onDone = viewModel::savePreset,
            onDismiss = { savingPreset = false },
        )
    }
    deleting?.let { preset ->
        ActionPromptDialog(
            title = stringResource(R.string.misc_eq_delete_preset, preset.name),
            onDismiss = { deleting = null },
            onCancel = { deleting = null },
            onConfirm = {
                viewModel.deletePreset(preset.id)
                deleting = null
            },
        )
    }
}

@Composable
private fun statusLine(eq: EqSettings, presets: List<EqPreset>): String {
    if (!eq.enabled) return stringResource(R.string.customise_off)
    val curve = eq.presetId?.let { id ->
        EqPresets.BuiltIn.firstOrNull { it.id == id }?.name ?: presets.firstOrNull { it.id == id }?.name
    } ?: stringResource(R.string.eq_label_custom)
    val correction = eq.correction?.takeIf { eq.correctionEnabled }?.model
    return when {
        eq.isFlat() -> stringResource(R.string.misc_eq_on_flat)
        correction != null && eq.presetId == EqPresets.FlatId -> stringResource(R.string.misc_eq_on_x, correction)
        correction != null -> stringResource(R.string.misc_eq_on_x_y, curve, correction)
        else -> stringResource(R.string.misc_eq_on_x, curve)
    }
}

@Composable
private fun Presets(
    eq: EqSettings,
    presets: List<EqPreset>,
    onPick: (String) -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    onLongPress: (String) -> Unit,
) {
    LabSectionLabel(stringResource(R.string.misc_eq_presets)) {
        LabTextAction(stringResource(R.string.eq_reset), R.drawable.refresh, onClick = onReset)
        LabTextAction(stringResource(R.string.eq_save), R.drawable.add, onClick = onSave)
    }
    val chips = buildList {
        if (eq.presetId == null) add(LabChip("custom", "Custom"))
        presets.forEach { add(LabChip(it.id, it.name, dot = androidx.compose.ui.graphics.Color.White)) }
        EqPresets.BuiltIn.forEach { add(LabChip(it.id, it.name)) }
    }
    LabChips(
        chips = chips,
        selectedKey = eq.presetId ?: "custom",
        onSelect = { if (it != "custom") onPick(it) },
        onLongPress = { id -> if (presets.any { it.id == id }) onLongPress(id) },
    )
}

@Composable
private fun Output(eq: EqSettings, headroom: Double, viewModel: AudioLabViewModel) {
    LabSectionLabel(stringResource(R.string.misc_eq_output))
    LabCard(padding = 16.dp) {
        LabSlider(
            label = stringResource(R.string.misc_eq_preamp),
            valueText = (if (eq.preamp > 0) "+" else "") + String.format("%.1f dB", eq.preamp),
            value = eq.preamp.toFloat(),
            onValueChange = { viewModel.setPreamp((it * 2).roundToInt() / 2.0) },
            valueRange = -12f..6f,
        )
        Spacer(Modifier.height(6.dp))
        LabToggleRow(
            title = stringResource(R.string.misc_eq_auto_preamp),
            subtitle = if (eq.autoPreamp) {
                if (headroom < -0.05) stringResource(R.string.misc_eq_lowering, String.format("%.1f", -headroom))
                else stringResource(R.string.misc_eq_nothing_lower)
            } else stringResource(R.string.misc_eq_off_louder),
            checked = eq.autoPreamp,
            onCheckedChange = viewModel::setAutoPreamp,
        )
    }
}
