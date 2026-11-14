// StreamStats.kt

package com.bedivine.app.ui.player.info

import android.content.res.Resources
import androidx.media3.common.C
import com.bedivine.app.R
import com.bedivine.app.db.entities.FormatEntity
import com.bedivine.app.playback.PlayingFrom
import com.bedivine.app.playback.StreamDetails
import com.bedivine.app.playback.StreamFacts
import com.bedivine.app.playback.StreamInspector
import com.bedivine.app.playback.audio.AudioOutputs
import com.bedivine.app.playback.audio.SincResampler
import java.util.Locale
import kotlin.math.log2
import kotlin.math.roundToInt

/** one card of stats for nerds: a title and only the lines that have a real value */
data class StatCard(val title: String, val lines: List<Pair<String, String>>)

/** what the effects are doing, read once per sheet frame from audio lab and the settings */
data class EffectsSnapshot(
    val equalizerBands: Int? = null,
    val spatial: Boolean = false,
    val mono: Boolean = false,
    val reverb: Boolean = false,
    val loudness: Boolean = false,
    val volumeCurve: Boolean = false,
    val normalize: Boolean = false,
    val skipSilence: Boolean = false,
    val haptics: Boolean = false,
    val transitions: String? = null,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val hiResWanted: Boolean = false,
    val floatAllowed: Boolean = false,
)

data class StatsInput(
    val mediaId: String,
    val details: StreamDetails,
    val facts: StreamFacts?,
    /** youtube's record of its stream; null when something else plays the song */
    val youtubeFormat: FormatEntity?,
    val from: PlayingFrom?,
    val durationMs: Long,
    val playerVolume: Float,
    val decoderHardware: Boolean?,
    val device: AudioOutputs.Device?,
    val mixer: AudioOutputs.Mixer?,
    /** the phone's own mixer rate, which the speaker and the headphone jack run at */
    val systemMixerRate: Int?,
    val castingTo: String?,
    val effects: EffectsSnapshot,
    /** passthrough's own line, already worded: straight to the dac, or why not */
    val passthrough: Pair<String, String>? = null,
)

/** format, decoder, resampler, dsp, output: the song's road to the speaker, nothing guessed */
fun buildStatCards(input: StatsInput, resources: Resources): List<StatCard> = listOf(
    formatCard(input, resources),
    decoderCard(input, resources),
    resamplerCard(input, resources),
    dspCard(input, resources),
    outputCard(input, resources),
).filter { it.lines.isNotEmpty() }

private fun formatCard(input: StatsInput, resources: Resources): StatCard {
    val d = input.details
    val codec = input.facts?.codec ?: StreamFacts.codecOf(d.sampleMime, d.codecs)
    val lossless = codec in LosslessCodecs
    val sampleRate = d.sampleRate ?: input.facts?.sampleRate ?: input.youtubeFormat?.sampleRate
    val fileSize = d.fileSizeBytes ?: input.youtubeFormat?.contentLength?.takeIf { it > 0 }
    val statedBitrate = d.bitrate ?: input.youtubeFormat?.bitrate?.takeIf { it > 0 }
    // a flac file says nothing of its bitrate: its size over its length does
    val averageBitrate = if (statedBitrate == null && fileSize != null && input.durationMs > 0) {
        (fileSize * 8_000 / input.durationMs).toInt()
    } else null
    return StatCard(
        title = codec?.let(::formatTitle) ?: resources.getString(R.string.stream_format),
        lines = listOfNotNull(
            d.sourceBitDepth?.takeIf { lossless }?.let { resources.getString(R.string.stream_bit_depth) to "$it-bit" },
            sampleRate?.let { resources.getString(R.string.stream_sample_rate) to kHz(it) },
            statedBitrate?.let { resources.getString(R.string.stream_bitrate) to kbps(it) } ?: averageBitrate?.let { resources.getString(R.string.stream_bitrate) to resources.getString(R.string.stream_bitrate_average, kbps(it)) },
            d.channels?.let { resources.getString(R.string.stream_channel_type) to channelName(it, resources) },
            containerName(d.containerMime)?.let { resources.getString(R.string.stream_container) to it },
            resources.getString(R.string.stream_media_id) to input.mediaId,
            input.from?.matchedId?.let { resources.getString(R.string.stream_matched_as) to "${input.from.name} #$it" },
            resources.getString(R.string.stream_volume) to "${(input.playerVolume * 100).roundToInt()}%",
            input.youtubeFormat?.loudnessDb?.let { resources.getString(R.string.stream_loudness) to String.format(Locale.US, "%+.1f dB", it) },
            fileSize?.let { resources.getString(R.string.stream_file_size) to megabytes(it) },
        ),
    )
}

private fun decoderCard(input: StatsInput, resources: Resources): StatCard {
    val d = input.details
    return StatCard(
        title = resources.getString(R.string.stream_title_decoder),
        lines = listOfNotNull(
            d.decoderName?.let { resources.getString(R.string.stream_decoder_name) to it },
            input.decoderHardware?.let { resources.getString(R.string.stream_runs_on) to if (it) resources.getString(R.string.stream_runs_on_hardware) else resources.getString(R.string.stream_runs_on_software) },
            d.sampleMime?.let { resources.getString(R.string.stream_format) to it },
            d.codecs?.takeIf { it.isNotBlank() }?.let { resources.getString(R.string.stream_codec) to it },
            d.decodedEncoding?.let { resources.getString(R.string.stream_output) to pcmName(it, resources) },
            d.decodedSampleRate?.let { resources.getString(R.string.stream_output_rate) to kHz(it) },
            d.decodedChannels?.let { resources.getString(R.string.stream_channels) to channelName(it, resources) },
            d.decoderInitMs?.takeIf { it > 0 }?.let { resources.getString(R.string.stream_started_in) to "$it ms" },
        ),
    )
}

private fun resamplerCard(input: StatsInput, resources: Resources): StatCard {
    val d = input.details
    val effects = input.effects
    if (d.resampledFrom != null && d.resampledTo != null) return bedivineResamplerCard(d, effects, resources)
    val inRate = d.decodedSampleRate ?: d.sampleRate ?: return StatCard(resources.getString(R.string.stream_title_resampler), emptyList())
    val outRate = input.mixer?.sampleRate ?: input.systemMixerRate?.takeIf { input.device?.kind in FixedRateKinds && input.castingTo == null }
    val timeStretch = effects.speed != 1f || effects.pitch != 1f
    val resampling = outRate != null && outRate != inRate
    val type = when {
        input.castingTo != null -> resources.getString(R.string.stream_resample_cast_device)
        timeStretch -> resources.getString(R.string.stream_resample_timestretch)
        outRate == null -> resources.getString(R.string.stream_resample_mixer_as_needed)
        resampling -> resources.getString(R.string.stream_resample_mixer)
        else -> resources.getString(R.string.stream_resample_none)
    }
    return StatCard(
        title = resources.getString(R.string.stream_title_resampler),
        lines = listOfNotNull(
            resources.getString(R.string.stream_type) to type,
            outRate?.let { resources.getString(R.string.stream_io_rate) to if (resampling) "${kHz(inRate)} → ${kHz(it)}" else resources.getString(R.string.stream_io_rate_same, kHz(inRate)) },
            // the filter has to stop at the lower rate's nyquist, half of it
            outRate?.takeIf { resampling }?.let { resources.getString(R.string.stream_cutoff) to "~${kHz(minOf(inRate, it) / 2)}" },
            line(resources.getString(R.string.stream_quality), resources.getString(R.string.stream_quality_untouched).takeIf { !resampling && outRate != null && !timeStretch }),
            effects.speed.takeIf { it != 1f }?.let { resources.getString(R.string.stream_speed) to String.format(Locale.US, "%.2f×", it) },
            effects.pitch.takeIf { it != 1f }?.let { resources.getString(R.string.stream_pitch) to String.format(Locale.US, resources.getString(R.string.stream_pitch_value), 12 * log2(it)) },
        ),
    )
}

// mochy's own filter did the work, so android's mixer is handed the rate it already runs at
private fun bedivineResamplerCard(d: StreamDetails, effects: EffectsSnapshot, resources: Resources): StatCard = StatCard(
    title = resources.getString(R.string.stream_title_resampler),
    lines = listOfNotNull(
        resources.getString(R.string.stream_type) to resources.getString(R.string.stream_resample_bedivine_type),
        resources.getString(R.string.stream_io_rate) to "${kHz(d.resampledFrom!!)} → ${kHz(d.resampledTo!!)}",
        d.resamplerPassbandHz?.let { resources.getString(R.string.stream_passband) to resources.getString(R.string.stream_passband_value, kHz(it.toInt())) },
        resources.getString(R.string.stream_cutoff) to kHz(minOf(d.resampledFrom, d.resampledTo) / 2),
        resources.getString(R.string.stream_quality) to resources.getString(R.string.stream_quality_stopband, SincResampler.StopbandDb.toInt()),
        d.resamplerTaps?.let { resources.getString(R.string.stream_taps) to resources.getString(R.string.stream_taps_value, it) },
        resources.getString(R.string.stream_android_mixer) to resources.getString(R.string.stream_android_mixer_match),
        effects.speed.takeIf { it != 1f }?.let { resources.getString(R.string.stream_speed) to String.format(Locale.US, "%.2f×", it) },
    ),
)

private fun dspCard(input: StatsInput, resources: Resources): StatCard {
    val d = input.details
    val e = input.effects
    val chain = listOfNotNull(
        e.equalizerBands?.let { resources.getString(R.string.stream_effect_equalizer, it) },
        resources.getString(R.string.stream_effect_spatial).takeIf { e.spatial },
        resources.getString(R.string.stream_effect_mono).takeIf { e.mono },
        resources.getString(R.string.stream_effect_reverb).takeIf { e.reverb },
        resources.getString(R.string.stream_effect_loudness).takeIf { e.loudness },
        resources.getString(R.string.stream_effect_volume_curve).takeIf { e.volumeCurve },
        e.transitions,
        resources.getString(R.string.stream_effect_skip_silence).takeIf { e.skipSilence },
        resources.getString(R.string.stream_effect_haptics).takeIf { e.haptics },
    )
    val floatPath = d.outputEncoding == C.ENCODING_PCM_FLOAT
    return StatCard(
        title = resources.getString(R.string.stream_title_dsp),
        lines = listOfNotNull(
            d.outputEncoding?.let { resources.getString(R.string.stream_pcm_format) to pcmName(it, resources) },
            d.outputSampleRate?.let { resources.getString(R.string.stream_sample_rate) to kHz(it) },
            resources.getString(R.string.stream_effects) to chain.joinToString(", ").ifEmpty { resources.getString(R.string.stream_effects_none) },
            line(resources.getString(R.string.stream_dither), resources.getString(R.string.stream_dither_value).takeIf { d.dithered }),
            line(resources.getString(R.string.stream_normalization), resources.getString(R.string.pref_audio_quality_status_on).takeIf { e.normalize }),
            when {
                floatPath -> resources.getString(R.string.stream_hires_path) to resources.getString(R.string.stream_hires_path_float)
                e.hiResWanted && !e.floatAllowed -> resources.getString(R.string.stream_hires_path) to resources.getString(R.string.stream_hires_path_paused)
                else -> null
            },
        ),
    )
}

private fun outputCard(input: StatsInput, resources: Resources): StatCard {
    val d = input.details
    val device = input.device
    val frameBytes = (channelCountOf(d.outputChannelConfig) ?: 2) * (bytesPerSample(d.outputEncoding) ?: 2)
    val bufferMs = d.outputBufferBytes?.let { bytes -> d.outputSampleRate?.takeIf { it > 0 }?.let { rate -> bytes * 1000L / (rate.toLong() * frameBytes) } }
    val mixer = input.mixer
    return StatCard(
        title = resources.getString(R.string.stream_output),
        lines = listOfNotNull(
            input.castingTo?.let { resources.getString(R.string.stream_casting_to) to it },
            device?.let { resources.getString(R.string.stream_device) to deviceName(it, resources) },
            device?.let { resources.getString(R.string.stream_route) to routeName(it.kind, resources) },
            device?.takeIf { it.kind == AudioOutputs.Kind.USB || it.kind == AudioOutputs.Kind.HDMI }?.let { dev ->
                listOfNotNull(dev.maxSampleRate?.let(::kHz), dev.bitDepths.maxOrNull()?.let { "$it-bit" })
                    .takeIf { it.isNotEmpty() }?.let { resources.getString(R.string.stream_takes_up_to) to it.joinToString(" · ") }
            },
            mixer?.let {
                resources.getString(R.string.stream_dac_mixer) to resources.getString(
                    if (it.bitPerfect) R.string.stream_dac_mixer_bitperfect else R.string.stream_dac_mixer_locked,
                    listOfNotNull(kHz(it.sampleRate), StreamInspector.bitDepthOf(it.encoding)?.let { b -> "$b-bit" }).joinToString(" "),
                )
            },
            input.passthrough,
            d.outputEncoding?.let { enc ->
                resources.getString(R.string.stream_audio_track) to listOfNotNull(pcmShort(enc, resources), d.outputSampleRate?.let(::kHz), channelCountOf(d.outputChannelConfig)?.let { channelName(it, resources) }).joinToString(" · ")
            },
            bufferMs?.takeIf { it > 0 }?.let { resources.getString(R.string.stream_buffer) to "~$it ms" },
            line(resources.getString(R.string.stream_offload), resources.getString(R.string.pref_audio_quality_status_on).takeIf { d.offload }),
            line(resources.getString(R.string.stream_tunneling), resources.getString(R.string.pref_audio_quality_status_on).takeIf { d.tunneling }),
            d.underruns.takeIf { it > 0 }?.let { resources.getString(R.string.stream_underruns) to "$it" },
        ),
    )
}

// a line with nothing to say isn't drawn at all, rather than drawn as "-"
private fun line(label: String, value: String?): Pair<String, String>? = value?.let { label to it }

private val LosslessCodecs = setOf("FLAC", "ALAC", "PCM")
private val FixedRateKinds = setOf(AudioOutputs.Kind.PHONE, AudioOutputs.Kind.WIRED)

// a dot and the codec like the mock (".flac" shown as .Flac), but acronyms said letter by letter stay in capitals
private fun formatTitle(codec: String): String =
    if (codec in setOf("FLAC", "OPUS", "VORBIS", "ALAC")) ".${codec.lowercase().replaceFirstChar { it.uppercase() }}" else ".$codec"

private fun kHz(rate: Int): String {
    val khz = rate / 1000.0
    return if (khz % 1.0 == 0.0) "${khz.toInt()} kHz" else String.format(Locale.US, "%.${if ((rate % 100) == 0) 1 else 2}f kHz", khz)
}

private fun kbps(bitsPerSecond: Int): String = String.format(Locale.getDefault(), "%,d kbps", (bitsPerSecond + 500) / 1000)

private fun megabytes(bytes: Long): String = String.format(Locale.getDefault(), "%.1f MB", bytes / 1_048_576.0)

private fun channelName(count: Int, resources: Resources): String = when (count) {
    1 -> resources.getString(R.string.stream_channel_mono)
    2 -> resources.getString(R.string.stream_channel_stereo)
    6 -> resources.getString(R.string.stream_channel_surround_51)
    8 -> resources.getString(R.string.stream_channel_surround_71)
    else -> resources.getString(R.string.stream_channel_count, count)
}

private fun containerName(mime: String?): String? = when (mime?.lowercase()) {
    null -> null
    "audio/webm", "video/webm" -> "WebM"
    "audio/mp4", "video/mp4" -> "MP4"
    "audio/flac" -> "FLAC"
    "audio/ogg" -> "Ogg"
    "audio/mpeg" -> "MP3"
    "audio/wav", "audio/x-wav" -> "WAV"
    "application/x-mpegurl", "application/vnd.apple.mpegurl" -> "HLS"
    "application/dash+xml" -> "DASH"
    else -> mime
}

private fun pcmName(encoding: Int, resources: Resources): String = when (encoding) {
    C.ENCODING_PCM_16BIT -> resources.getString(R.string.stream_pcm_16bit)
    C.ENCODING_PCM_24BIT -> resources.getString(R.string.stream_pcm_24bit)
    C.ENCODING_PCM_32BIT -> resources.getString(R.string.stream_pcm_32bit)
    C.ENCODING_PCM_FLOAT -> resources.getString(R.string.stream_pcm_float)
    C.ENCODING_PCM_8BIT -> resources.getString(R.string.stream_pcm_8bit)
    else -> resources.getString(R.string.stream_pcm_passthrough)
}

private fun pcmShort(encoding: Int, resources: Resources): String = when (encoding) {
    C.ENCODING_PCM_FLOAT -> resources.getString(R.string.stream_pcm_float_short)
    else -> StreamInspector.bitDepthOf(encoding)?.let { "$it-bit" } ?: resources.getString(R.string.stream_pcm_encoded)
}

private fun bytesPerSample(encoding: Int?): Int? = when (encoding) {
    C.ENCODING_PCM_16BIT -> 2
    C.ENCODING_PCM_24BIT -> 3
    C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
    else -> null
}

private fun channelCountOf(channelConfig: Int?): Int? = channelConfig?.let { Integer.bitCount(it).takeIf { n -> n > 0 } }

private fun routeName(kind: AudioOutputs.Kind, resources: Resources): String = when (kind) {
    AudioOutputs.Kind.PHONE -> resources.getString(R.string.stream_route_speaker)
    AudioOutputs.Kind.WIRED -> resources.getString(R.string.stream_route_wired)
    AudioOutputs.Kind.USB -> resources.getString(R.string.stream_route_usb)
    AudioOutputs.Kind.BLUETOOTH -> resources.getString(R.string.stream_route_bluetooth)
    AudioOutputs.Kind.HDMI -> resources.getString(R.string.stream_route_hdmi)
}

internal fun deviceName(device: AudioOutputs.Device, resources: Resources): String = device.name.ifBlank {
    when (device.kind) {
        AudioOutputs.Kind.PHONE -> resources.getString(R.string.stream_device_this_phone)
        AudioOutputs.Kind.WIRED -> resources.getString(R.string.stream_device_wired_headphones)
        AudioOutputs.Kind.USB -> resources.getString(R.string.stream_device_usb_dac)
        AudioOutputs.Kind.BLUETOOTH -> resources.getString(R.string.stream_device_bluetooth)
        AudioOutputs.Kind.HDMI -> resources.getString(R.string.stream_device_hdmi)
    }
}
