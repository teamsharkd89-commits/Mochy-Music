// PlayerCoverStyles.kt

package com.bedivine.app.ui.player.customize

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.Preferences
import com.bedivine.app.R
import com.bedivine.app.constants.CanvasThumbnailAnimationKey
import com.bedivine.app.constants.DiscNameKey
import com.bedivine.app.constants.DiscRealisticModeKey
import com.bedivine.app.constants.DiscRotatingAnimationKey
import com.bedivine.app.constants.PlayVideoBackgroundKey
import com.bedivine.app.constants.PlayerCoverStyle
import com.bedivine.app.constants.YtVideoBackgroundLyricsSyncKey

val PlayerCoverStyle.isDisc: Boolean
    get() = when (this) {
        PlayerCoverStyle.EDGE_TO_EDGE, PlayerCoverStyle.SQUARED -> false
        else -> true
    }

val PlayerCoverStyle.usesBoxedStage: Boolean
    get() = when (this) {
        PlayerCoverStyle.EDGE_TO_EDGE,
        PlayerCoverStyle.DISC_BIG_FULL,
        PlayerCoverStyle.DISC_BIG_LABEL,
        PlayerCoverStyle.TURNTABLE,
        -> false
        else -> true
    }

val PlayerCoverStyle.isBigDisc: Boolean
    get() = this == PlayerCoverStyle.DISC_BIG_FULL || this == PlayerCoverStyle.DISC_BIG_LABEL

val PlayerCoverStyle.hasTonearm: Boolean
    get() = this == PlayerCoverStyle.DISC_BIG_LABEL

@get:StringRes
val PlayerCoverStyle.displayNameRes: Int
    get() = when (this) {
        PlayerCoverStyle.EDGE_TO_EDGE -> R.string.misc_cover_edge_to_edge
        PlayerCoverStyle.SQUARED -> R.string.misc_cover_squared
        PlayerCoverStyle.DISC_SMALL_FULL -> R.string.misc_cover_disc_small_full
        PlayerCoverStyle.DISC_SMALL_LABEL -> R.string.misc_cover_disc_small_label
        PlayerCoverStyle.DISC_BIG_FULL -> R.string.misc_cover_disc_big_full
        PlayerCoverStyle.DISC_BIG_LABEL -> R.string.misc_cover_disc_big_label
        PlayerCoverStyle.DISC_ALBUM -> R.string.misc_cover_disc_album
        PlayerCoverStyle.TURNTABLE -> R.string.misc_cover_turntable
    }

sealed interface CoverOption {
    @get:StringRes val titleRes: Int
    @get:StringRes val descriptionRes: Int
    @get:DrawableRes val icon: Int

    data class Switch(
        @get:StringRes override val titleRes: Int,
        @get:StringRes override val descriptionRes: Int,
        @param:DrawableRes override val icon: Int,
        val key: Preferences.Key<Boolean>,
        val default: Boolean,
        val inverted: Boolean = false,
        val parent: Switch? = null,
    ) : CoverOption

    data class Text(
        @get:StringRes override val titleRes: Int,
        @get:StringRes override val descriptionRes: Int,
        @param:DrawableRes override val icon: Int,
        val key: Preferences.Key<String>,
        @get:StringRes val placeholderRes: Int,
    ) : CoverOption
}

private val AnimatedCanvasOption = CoverOption.Switch(
    titleRes = R.string.misc_cover_animated,
    descriptionRes = R.string.misc_cover_animated_desc,
    icon = R.drawable.sparks,
    key = CanvasThumbnailAnimationKey,
    default = true,
)

private val YtVideoOption = CoverOption.Switch(
    titleRes = R.string.misc_cover_yt,
    descriptionRes = R.string.misc_cover_yt_desc,
    icon = R.drawable.slow_motion_video,
    key = PlayVideoBackgroundKey,
    default = false,
)

private val TimestampMatchingOption = CoverOption.Switch(
    titleRes = R.string.misc_cover_ts,
    descriptionRes = R.string.misc_cover_ts_desc,
    icon = R.drawable.lyrics,
    key = YtVideoBackgroundLyricsSyncKey,
    default = true,
    parent = YtVideoOption,
)

private val RotatingAnimationOption = CoverOption.Switch(
    titleRes = R.string.misc_cover_rotating,
    descriptionRes = R.string.misc_cover_for_disc,
    icon = R.drawable.refresh,
    key = DiscRotatingAnimationKey,
    default = true,
)

private val RealisticModeOption = CoverOption.Switch(
    titleRes = R.string.misc_cover_realistic,
    descriptionRes = R.string.misc_cover_for_disc,
    icon = R.drawable.palette,
    key = DiscRealisticModeKey,
    default = true,
)

private val DiscNameOption = CoverOption.Text(
    titleRes = R.string.misc_cover_discname,
    descriptionRes = R.string.misc_cover_discname_desc,
    icon = R.drawable.edit,
    key = DiscNameKey,
    placeholderRes = R.string.misc_cover_ph_hide,
)

private val DiscOptions = listOf(RotatingAnimationOption, RealisticModeOption, DiscNameOption)

val PlayerCoverStyle.options: List<CoverOption>
    get() = when (this) {
        PlayerCoverStyle.SQUARED -> listOf(AnimatedCanvasOption)
        PlayerCoverStyle.EDGE_TO_EDGE -> listOf(
            YtVideoOption,
            TimestampMatchingOption,
            AnimatedCanvasOption,
        )
        else -> DiscOptions
    }
