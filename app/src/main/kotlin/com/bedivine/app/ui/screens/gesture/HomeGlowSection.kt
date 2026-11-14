// settings > customisation > home gradient: where the top glow's colours come from, how strong, how far, whether it moves

package com.bedivine.app.ui.screens.gesture

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bedivine.app.R
import com.bedivine.app.constants.HomeGlowAnimatedKey
import com.bedivine.app.constants.HomeGlowFixedColorKey
import com.bedivine.app.constants.HomeGlowLengthKey
import com.bedivine.app.constants.HomeGlowModeKey
import com.bedivine.app.constants.HomeGlowStrengthKey
import com.bedivine.app.ui.component.HomeGlowFixedColor
import com.bedivine.app.ui.component.HomeGlowLengthMax
import com.bedivine.app.ui.component.HomeGlowLengthMin
import com.bedivine.app.ui.component.HomeGlowMode
import com.bedivine.app.ui.screens.audiolab.LabCard
import com.bedivine.app.ui.screens.audiolab.LabColors
import com.bedivine.app.ui.screens.audiolab.LabToggleRow
import com.bedivine.app.ui.theme.InterFontFamily
import com.bedivine.app.utils.rememberPreference
import kotlin.math.roundToInt

@Composable
internal fun HomeGlowSection() {
    val (modePref, setModePref) = rememberPreference(HomeGlowModeKey, HomeGlowMode.COVERS.key)
    val (fixedPref, setFixedPref) = rememberPreference(HomeGlowFixedColorKey, HomeGlowFixedColor.AMBER.key)
    val (strengthPref, setStrengthPref) = rememberPreference(HomeGlowStrengthKey, 0.9f)
    val (lengthPref, setLengthPref) = rememberPreference(HomeGlowLengthKey, 150f)
    val (animatedPref, setAnimatedPref) = rememberPreference(HomeGlowAnimatedKey, true)
    val mode = HomeGlowMode.fromKey(modePref)
    val fixed = HomeGlowFixedColor.fromKey(fixedPref)
    val haptic = LocalHapticFeedback.current
    var lastStrengthTick by remember { mutableIntStateOf((strengthPref * 10f).roundToInt()) }
    var lastLengthTick by remember { mutableIntStateOf((lengthPref / 20f).roundToInt()) }

    Text(
        text = stringResource(R.string.home_glow_title),
        style = TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            letterSpacing = (-0.05).em,
        ),
        color = Color.White,
    )
    Spacer(Modifier.height(14.dp))
    LabCard(padding = 20.dp) {
        Text(
            stringResource(R.string.home_glow_title),
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            letterSpacing = (-0.02).em,
            color = LabColors.Primary,
        )
        Text(
            stringResource(R.string.home_glow_desc),
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = LabColors.Secondary,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(14.dp))
        // the three ways the glow gets its colours
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            HomeGlowMode.entries.forEach { option ->
                val selected = option == mode
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) Color.White else LabColors.Row)
                        .clickable { setModePref(option.key) }
                        .padding(horizontal = 6.dp),
                ) {
                    Text(
                        text = stringResource(option.labelRes),
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = if (selected) Color.Black else Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (mode == HomeGlowMode.VIVO) {
            Text(
                text = stringResource(R.string.home_glow_vivo_desc),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = LabColors.Secondary,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        if (mode == HomeGlowMode.FIXED) {
            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                HomeGlowFixedColor.entries.forEach { option ->
                    val selected = option == fixed
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { setFixedPref(option.key) },
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(option.color)
                                .then(if (selected) Modifier.border(2.dp, Color.White, CircleShape) else Modifier),
                        ) {
                            if (selected) {
                                Icon(
                                    painter = painterResource(R.drawable.check),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(option.labelRes),
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            color = if (selected) Color.White else LabColors.Secondary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        CustomiseSliderCard(
            title = stringResource(R.string.home_glow_strength),
            valueText = stringResource(R.string.home_glow_percent, (strengthPref * 100f).roundToInt()),
            startLabel = stringResource(R.string.home_glow_percent, 0),
            endLabel = stringResource(R.string.home_glow_percent, 100),
            fraction = strengthPref.coerceIn(0f, 1f),
            onFractionChange = { f ->
                val next = f.coerceIn(0f, 1f)
                // a tick every 10%, so the drag feels like it has notches
                val tick = (next * 10f).roundToInt()
                if (tick != lastStrengthTick) {
                    lastStrengthTick = tick
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
                setStrengthPref(next)
            },
        )
        Spacer(Modifier.height(11.dp))
        CustomiseSliderCard(
            title = stringResource(R.string.home_glow_length),
            valueText = stringResource(R.string.home_glow_length_value, lengthPref.roundToInt()),
            startLabel = stringResource(R.string.home_glow_length_value, HomeGlowLengthMin.roundToInt()),
            endLabel = stringResource(R.string.home_glow_length_value, HomeGlowLengthMax.roundToInt()),
            fraction = ((lengthPref - HomeGlowLengthMin) / (HomeGlowLengthMax - HomeGlowLengthMin)).coerceIn(0f, 1f),
            onFractionChange = { f ->
                val next = HomeGlowLengthMin + f.coerceIn(0f, 1f) * (HomeGlowLengthMax - HomeGlowLengthMin)
                // a tick every 20dp, so the drag feels like it has notches
                val tick = (next / 20f).roundToInt()
                if (tick != lastLengthTick) {
                    lastLengthTick = tick
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
                setLengthPref(next)
            },
        )
        Spacer(Modifier.height(14.dp))
        LabToggleRow(
            title = stringResource(R.string.home_glow_animated),
            subtitle = stringResource(R.string.home_glow_animated_desc),
            checked = animatedPref,
            onCheckedChange = setAnimatedPref,
        )
    }
}
