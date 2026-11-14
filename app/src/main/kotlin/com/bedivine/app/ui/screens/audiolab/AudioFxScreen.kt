// audio lab's effects as cards, each with a live picture, a switch and options that fold away while it is off

package com.bedivine.app.ui.screens.audiolab

import android.content.Context
import android.media.AudioManager
import android.media.MediaCodecList
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bedivine.app.LocalPlayerConnection
import com.bedivine.app.R
import com.bedivine.app.constants.AudioNormalizationKey
import com.bedivine.app.eq.MaxSpeed
import com.bedivine.app.eq.MinSpeed
import com.bedivine.app.eq.ReverbPresets
import com.bedivine.app.eq.audio.SpatialMode
import com.bedivine.app.ui.theme.InterFontFamily
import com.bedivine.app.utils.rememberPreference
import com.bedivine.app.viewmodels.AudioLabViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun AudioFxScreen(
    navController: NavController,
    viewModel: AudioLabViewModel = hiltViewModel(),
) {
    ListenToAudio()
    val reverb by viewModel.reverb.collectAsState()
    val loudness by viewModel.loudness.collectAsState()
    val spatial by viewModel.spatial.collectAsState()
    val playback by viewModel.playback.collectAsState()
    val volume by viewModel.volume.collectAsState()
    val effects = activeEffects(reverb, loudness, spatial, playback, volume)

    AudioLabPage(
        title = LabTitle(stringResource(R.string.audiofx_title_audio), stringResource(R.string.audiofx_title_fx), firstSize = 40.sp, secondSize = 84.sp, firstDim = true),
        onBack = { navController.navigateUp() },
        art = { hidden -> FxCardStack(effects, hidden) },
    ) {
        ReverbCard(viewModel)
        Spacer(Modifier.height(14.dp))
        LoudnessCard(viewModel)
        Spacer(Modifier.height(14.dp))
        SpatialCard(viewModel)
        Spacer(Modifier.height(14.dp))
        MonoCard(viewModel)
        Spacer(Modifier.height(14.dp))
        AtmosCard(viewModel = viewModel)
        Spacer(Modifier.height(14.dp))
        SpeedCard(viewModel)
        Spacer(Modifier.height(14.dp))
        VolumeCard(viewModel)
    }
}

/** the shape every effect takes: picture, switch, then its options */
@Composable
internal fun EffectCard(
    picture: @Composable () -> Unit,
    header: @Composable () -> Unit,
    expanded: Boolean,
    options: @Composable ColumnScope.() -> Unit,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    LabCard(padding = 14.dp) {
        LabStage(height = 132.dp) { picture() }
        Spacer(Modifier.height(10.dp))
        header()
        LabCollapsible(expanded) {
            Spacer(Modifier.height(10.dp))
            options()
        }
        footer?.invoke(this)
    }
}

internal fun pct(v: Float) = "${(v * 100).roundToInt()}%"

// ---- loudness ------------------------------------------------------------------------------

@Composable
private fun LoudnessCard(viewModel: AudioLabViewModel) {
    val loudness by viewModel.loudness.collectAsState()
    val (normalize, onNormalize) = rememberPreference(AudioNormalizationKey, defaultValue = true)
    EffectCard(
        picture = { LoudnessPicture(loudness, viewModel.fx) },
        header = {
            LabToggleRow(stringResource(R.string.loudness), stringResource(R.string.audiofx_loudness_desc), loudness.enabled,
                { on -> viewModel.updateLoudness { it.copy(enabled = on) } })
        },
        expanded = loudness.enabled,
        options = {
            LabChips(
                chips = listOf(LabChip("gentle", stringResource(R.string.crossmix_tempo_gentle)), LabChip("punchy", stringResource(R.string.audiofx_loudness_punchy)), LabChip("night", stringResource(R.string.audiofx_loudness_night)), LabChip("max", stringResource(R.string.audiofx_loudness_max))),
                selectedKey = when {
                    loudness.boostDb == 2f && loudness.compression == 0.15f -> "gentle"
                    loudness.boostDb == 4f && loudness.compression == 0.3f -> "punchy"
                    loudness.boostDb == 3f && loudness.compression == 0.85f -> "night"
                    loudness.boostDb == 9f && loudness.compression == 0.5f -> "max"
                    else -> null
                },
                onSelect = { key ->
                    val (boost, comp) = when (key) {
                        "gentle" -> 2f to 0.15f
                        "night" -> 3f to 0.85f
                        "max" -> 9f to 0.5f
                        else -> 4f to 0.3f
                    }
                    viewModel.updateLoudness { it.copy(boostDb = boost, compression = comp) }
                },
            )
            Spacer(Modifier.height(10.dp))
            LabPanel {
                LabSlider(stringResource(R.string.audiofx_boost), "+${String.format("%.1f", loudness.boostDb)} dB", loudness.boostDb,
                    { v -> viewModel.updateLoudness { it.copy(boostDb = (v * 2).roundToInt() / 2f) } }, 0f..12f)
                LabSlider(stringResource(R.string.audiofx_compression), pct(loudness.compression), loudness.compression,
                    { v -> viewModel.updateLoudness { it.copy(compression = (v * 100).roundToInt() / 100f) } }, 0f..1f)
                Text(
                    stringResource(R.string.audiofx_compression_desc),
                    fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 16.sp, color = LabColors.Secondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        footer = {
            Spacer(Modifier.height(6.dp))
            LabToggleRow(stringResource(R.string.audiofx_even_out_songs), stringResource(R.string.audiofx_even_out_songs_desc), normalize, onNormalize)
            val rg by viewModel.normalize.collectAsState()
            LabCollapsible(normalize) {
                Spacer(Modifier.height(4.dp))
                LabPanel {
                    Text(
                        "REPLAYGAIN",
                        fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.3.sp,
                        color = LabColors.Secondary,
                    )
                    LabChips(
                        chips = listOf(LabChip("track", stringResource(R.string.audiofx_replaygain_track)), LabChip("album", stringResource(R.string.tab_album))),
                        selectedKey = if (rg.replayGainMode == com.bedivine.app.eq.audio.ReplayGainMode.Album) "album" else "track",
                        onSelect = { key ->
                            viewModel.updateNormalize {
                                it.copy(replayGainMode = if (key == "album") com.bedivine.app.eq.audio.ReplayGainMode.Album else com.bedivine.app.eq.audio.ReplayGainMode.Track)
                            }
                        },
                    )
                    LabSlider(stringResource(R.string.audiofx_preamp), (if (rg.preampDb > 0) "+" else "") + String.format("%.1f dB", rg.preampDb), rg.preampDb,
                        { v -> viewModel.updateNormalize { it.copy(preampDb = (v * 2).roundToInt() / 2f) } }, -6f..6f)
                    Text(
                        stringResource(R.string.audiofx_replaygain_desc),
                        fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 16.sp, color = LabColors.Secondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
    )
}

// ---- spatial -------------------------------------------------------------------------------

private class CrossfeedLevel(val key: String, @StringRes val labelRes: Int, val cutoff: Float, val feed: Float)

private val CrossfeedLevels = listOf(
    CrossfeedLevel("light", R.string.audiofx_crossfeed_light, 700f, 4.5f),
    CrossfeedLevel("natural", R.string.audiofx_crossfeed_natural, 700f, 6f),
    CrossfeedLevel("strong", R.string.audiofx_crossfeed_strong, 650f, 9.5f),
)

@Composable
private fun SpatialCard(viewModel: AudioLabViewModel) {
    val spatial by viewModel.spatial.collectAsState()
    EffectCard(
        picture = { SpatialPicture(spatial) },
        header = {
            LabToggleRow(stringResource(R.string.link_setting_spatial), stringResource(R.string.audiofx_spatial_desc), spatial.enabled,
                { on -> viewModel.updateSpatial { it.copy(enabled = on) } })
        },
        expanded = spatial.enabled,
        options = {
            LabSegmented(
                options = listOf(stringResource(R.string.audiofx_spatial_crossfeed), stringResource(R.string.audiofx_spatial_speakers)),
                selectedIndex = if (spatial.mode == SpatialMode.Crossfeed) 0 else 1,
                onSelect = { i -> viewModel.updateSpatial { it.copy(mode = if (i == 0) SpatialMode.Crossfeed else SpatialMode.Speakers) } },
                height = 42.dp,
            )
            Spacer(Modifier.height(10.dp))
            if (spatial.mode == SpatialMode.Crossfeed) {
                LabChips(
                    chips = CrossfeedLevels.map { LabChip(it.key, stringResource(it.labelRes)) },
                    selectedKey = CrossfeedLevels.firstOrNull { it.cutoff == spatial.cutoffHz && it.feed == spatial.feedDb }?.key,
                    onSelect = { key ->
                        CrossfeedLevels.firstOrNull { it.key == key }?.let { l ->
                            viewModel.updateSpatial { it.copy(cutoffHz = l.cutoff, feedDb = l.feed) }
                        }
                    },
                )
                Spacer(Modifier.height(10.dp))
                LabPanel {
                    LabSlider(stringResource(R.string.audiofx_head_shadow), "${spatial.cutoffHz.roundToInt()} Hz", spatial.cutoffHz,
                        { v -> viewModel.updateSpatial { it.copy(cutoffHz = (v / 10).roundToInt() * 10f) } }, 300f..1500f)
                    LabSlider(stringResource(R.string.audiofx_spatial_crossfeed), "−${String.format("%.1f", spatial.feedDb)} dB", 16f - spatial.feedDb,
                        { v -> viewModel.updateSpatial { it.copy(feedDb = ((16f - v) * 2).roundToInt() / 2f) } }, 1f..15f)
                    LabSlider(stringResource(R.string.audiofx_amount), pct(spatial.amount), spatial.amount,
                        { v -> viewModel.updateSpatial { it.copy(amount = v) } }, 0f..1f)
                }
            } else {
                LabPanel {
                    LabSlider(stringResource(R.string.audiofx_speaker_angle), "±${spatial.speakerAngle.roundToInt()}°", spatial.speakerAngle,
                        { v -> viewModel.updateSpatial { it.copy(speakerAngle = v.roundToInt().toFloat()) } }, 10f..90f)
                    LabSlider(stringResource(R.string.audiofx_amount), pct(spatial.amount), spatial.amount,
                        { v -> viewModel.updateSpatial { it.copy(amount = v) } }, 0f..1f)
                    Text(
                        stringResource(R.string.audiofx_speakers_desc),
                        fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 16.sp, color = LabColors.Secondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
    )
}

// ---- mono --------------------------------------------------------------------------------

@Composable
private fun MonoCard(viewModel: AudioLabViewModel) {
    val spatial by viewModel.spatial.collectAsState()
    LabCard(padding = 10.dp) {
        LabToggleRow(
            title = stringResource(R.string.audiofx_mono),
            subtitle = stringResource(R.string.audiofx_mono_desc),
            checked = spatial.mono,
            onCheckedChange = { on -> viewModel.updateSpatial { it.copy(mono = on) } },
            leadingDot = true,
        )
    }
}

// ---- dolby atmos ---------------------------------------------------------------------------

@Composable
private fun AtmosCard(viewModel: AudioLabViewModel) {
    val spatial by viewModel.spatial.collectAsState()
    val context = LocalContext.current
    var device by remember { mutableStateOf<AtmosSupport?>(null) }
    LaunchedEffect(Unit) { device = withContext(Dispatchers.Default) { atmosSupport(context) } }

    EffectCard(
        picture = { AtmosPicture(enabled = spatial.surround, renderOnHeadphones = spatial.surround) },
        header = {
            LabToggleRow(
                title = stringResource(R.string.audiofx_atmos),
                subtitle = stringResource(R.string.audiofx_atmos_desc),
                checked = spatial.surround,
                onCheckedChange = { on -> viewModel.updateSpatial { it.copy(surround = on) } },
            )
        },
        expanded = false,
        options = {},
        footer = {
            Text(
                stringResource(R.string.atmos_streaming_note),
                fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 16.sp, color = LabColors.Secondary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            )
            device?.let { support ->
                Spacer(Modifier.height(10.dp))
                Row(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SupportPill(stringResource(R.string.audiofx_atmos_decoder), support.decoder)
                    SupportPill(stringResource(R.string.audiofx_android_spatializer), support.spatializer)
                }
            }
        },
    )
}

private class AtmosSupport(val decoder: String, val spatializer: String)

/** what this phone can do with an atmos stream: decode it, and spatialise it itself */
private fun atmosSupport(context: Context): AtmosSupport {
    val decoder = runCatching {
        MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.any { info ->
            !info.isEncoder && info.supportedTypes.any { it.equals("audio/eac3-joc", ignoreCase = true) }
        }
    }.getOrDefault(false)
    val spatializer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) spatializerState(context) else "Android 12L+"
    return AtmosSupport(if (decoder) context.getString(R.string.audiopass_value_yes) else context.getString(R.string.audiofx_not_on_phone), spatializer)
}

@RequiresApi(Build.VERSION_CODES.S_V2)
private fun spatializerState(context: Context): String = runCatching {
    val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val spatializer = manager.spatializer
    when {
        !spatializer.isAvailable -> context.getString(R.string.audiofx_not_available)
        spatializer.isEnabled -> context.getString(R.string.tv_on)
        else -> context.getString(R.string.crossmix_tempo_off)
    }
}.getOrDefault(context.getString(R.string.unknown))

@Composable
private fun SupportPill(label: String, value: String) {
    Column {
        Text(label.uppercase(), fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 10.sp,
            letterSpacing = 1.sp, color = LabColors.Faint)
        Text(value, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = LabColors.Secondary)
    }
}

// ---- speed & pitch -------------------------------------------------------------------------

private class SpeedPreset(val key: String, @StringRes val labelRes: Int, val speed: Float, val preservePitch: Boolean, val withReverb: Boolean = false)

private val SpeedPresets = listOf(
    SpeedPreset("normal", R.string.audiofx_speed_normal, 1f, true),
    SpeedPreset("slowed", R.string.audiofx_speed_slowed, 0.85f, false),
    SpeedPreset("slowed_reverb", R.string.audiofx_speed_slowed_reverb, 0.85f, false, withReverb = true),
    SpeedPreset("nightcore", R.string.audiofx_speed_nightcore, 1.25f, false),
    SpeedPreset("0.75", R.string.audiofx_speed_075, 0.75f, true),
    SpeedPreset("1.25", R.string.audiofx_speed_125, 1.25f, true),
    SpeedPreset("1.5", R.string.audiofx_speed_15, 1.5f, true),
    SpeedPreset("2", R.string.audiofx_speed_2, 2f, true),
)

@Composable
private fun SpeedCard(viewModel: AudioLabViewModel) {
    val playback by viewModel.playback.collectAsState()
    val reverb by viewModel.reverb.collectAsState()
    EffectCard(
        picture = { SpeedPicture(playback) },
        header = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.audiofx_playback_speed), fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
                    Text(
                        if (playback.preservePitch) stringResource(R.string.audiofx_pitch_stays) else stringResource(R.string.audiofx_pitch_follows),
                        fontFamily = InterFontFamily, fontSize = 12.sp, color = LabColors.Secondary,
                    )
                }
                StepButton("−") { viewModel.updatePlayback { it.copy(speed = ((it.speed - 0.05f) * 100).roundToInt() / 100f) } }
                Text(
                    String.format("%.2f×", playback.speed),
                    fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
                StepButton("+") { viewModel.updatePlayback { it.copy(speed = ((it.speed + 0.05f) * 100).roundToInt() / 100f) } }
            }
        },
        expanded = true,
        options = {
            LabChips(
                chips = SpeedPresets.map { LabChip(it.key, stringResource(it.labelRes)) },
                selectedKey = if (playback.speed == 0.85f && !playback.preservePitch) {
                    if (reverb.enabled) "slowed_reverb" else "slowed"
                } else {
                    SpeedPresets.firstOrNull { p ->
                        !p.withReverb && p.speed == playback.speed && p.preservePitch == playback.preservePitch &&
                            (!p.preservePitch || playback.pitchSemitones == 0f)
                    }?.key
                },
                onSelect = { key ->
                    val p = SpeedPresets.first { it.key == key }
                    viewModel.updatePlayback { it.copy(speed = p.speed, preservePitch = p.preservePitch, pitchSemitones = 0f) }
                    if (p.withReverb) {
                        ReverbPresets.All.firstOrNull { it.id == "hall" }?.let { hall ->
                            viewModel.updateReverb { it.withPreset(hall).copy(enabled = true) }
                        }
                    } else if (key == "slowed" && reverb.enabled) {
                        // otherwise the tap does nothing: the chip stays on slowed + reverb while the reverb plays
                        viewModel.updateReverb { it.copy(enabled = false) }
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
            LabPanel {
                LabSlider(
                    stringResource(R.string.link_setting_speed), String.format("%.2f×", playback.speed), log10(playback.speed),
                    { v -> viewModel.updatePlayback { it.copy(speed = (10f.pow(v) * 100).roundToInt() / 100f) } },
                    log10(MinSpeed)..log10(MaxSpeed),
                )
                LabToggleRow(stringResource(R.string.audiofx_preserve_pitch), stringResource(R.string.audiofx_preserve_pitch_desc), playback.preservePitch,
                    { on -> viewModel.updatePlayback { it.copy(preservePitch = on) } })
                LabCollapsible(playback.preservePitch) {
                    LabSlider(
                        stringResource(R.string.audiofx_pitch),
                        (if (playback.pitchSemitones > 0) "+" else "") + stringResource(R.string.audiofx_pitch_semitones, playback.pitchSemitones.roundToInt()),
                        playback.pitchSemitones,
                        { v -> viewModel.updatePlayback { it.copy(pitchSemitones = v.roundToInt().toFloat()) } },
                        -12f..12f,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                LabTextAction(stringResource(R.string.reset), R.drawable.refresh, enabled = !playback.isNormal) {
                    viewModel.updatePlayback { com.bedivine.app.eq.PlaybackSettings() }
                }
            }
        },
    )
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(32.dp).clip(CircleShape).background(LabColors.Row).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
    }
}

// ---- volume curve --------------------------------------------------------------------------

private class CurveShape(val key: String, @StringRes val labelRes: Int, val exponent: Float)

private val CurveShapes = listOf(
    CurveShape("gentle", R.string.crossmix_tempo_gentle, 1.6f),
    CurveShape("exponential", R.string.audiofx_curve_exponential, 2.2f),
    CurveShape("steep", R.string.audiofx_curve_steep, 3.2f),
)

@Composable
private fun VolumeCard(viewModel: AudioLabViewModel) {
    val volume by viewModel.volume.collectAsState()
    val playerVolume = LocalPlayerConnection.current?.service?.playerVolume?.collectAsState()
    EffectCard(
        picture = { VolumeCurvePicture(volume, playerVolume?.value ?: 0.6f) },
        header = {
            LabToggleRow(stringResource(R.string.audiofx_exponential_volume), stringResource(R.string.audiofx_exponential_volume_desc), volume.enabled,
                { on -> viewModel.updateVolume { it.copy(enabled = on) } })
        },
        expanded = volume.enabled,
        options = {
            LabChips(
                chips = CurveShapes.map { LabChip(it.key, stringResource(it.labelRes)) },
                selectedKey = CurveShapes.firstOrNull { it.exponent == volume.exponent }?.key,
                onSelect = { key -> CurveShapes.firstOrNull { it.key == key }?.let { s -> viewModel.updateVolume { it.copy(exponent = s.exponent) } } },
            )
            Spacer(Modifier.height(10.dp))
            LabPanel {
                LabSlider(stringResource(R.string.audiofx_curve), String.format("x^%.1f", volume.exponent), volume.exponent,
                    { v -> viewModel.updateVolume { it.copy(exponent = (v * 10).roundToInt() / 10f) } }, 1f..4f)
                LabSlider(stringResource(R.string.audiofx_volume_limit), pct(volume.limit), volume.limit,
                    { v -> viewModel.updateVolume { it.copy(limit = (v * 100).roundToInt() / 100f) } }, 0.2f..1f)
                Text(
                    stringResource(R.string.audiofx_volume_desc),
                    fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 16.sp, color = LabColors.Secondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
    )
}
