// the "follow me on Instagram" sheet: the auto prompt, help us! on the version sheet, and support me on about app all open this

package com.bedivine.app.ui.screens.donate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bedivine.app.R
import com.bedivine.app.ui.component.PopupSheetHandleHeight
import com.bedivine.app.ui.component.PopupSheetState
import com.bedivine.app.ui.screens.update.openUrl

// the band ends around the middle of the round photo, so the rainbow sits behind its top half
private const val BandEndsOnPhoto = 0.55f

/** shows the support sheet, donate us! pushes the donate sheet on top of it */
fun PopupSheetState.showSupportSheet() {
    val popup = this
    show(fullBleed = true, buttonBar = { SupportButtons(popup) }) {
        SupportSheetContent()
    }
}

@Composable
private fun SupportSheetContent() {
    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val photoSize = characterWidth(maxWidth, maxHeight)
        val bandHeight = PopupSheetHandleHeight + CharacterTop + photoSize * BandEndsOnPhoto

        NameMarquee(
            modifier = Modifier
                // starts up behind the handle so the rainbow runs right to the sheet's top edge, and takes no room of its own
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, bandHeight.roundToPx()))
                    layout(constraints.maxWidth, 0) { placeable.place(0, -PopupSheetHandleHeight.roundToPx()) }
                }
                // rides along when a short screen has to scroll, read at draw time so scrolling never recomposes
                .graphicsLayer { translationY = -scroll.value.toFloat() },
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = 26.dp),
        ) {
            Spacer(Modifier.height(CharacterTop))
            SupportProfilePhoto(size = photoSize)
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.support_sheet_enjoying),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = DonateSoftText,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.support_sheet_title),
                fontSize = 32.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp,
                textAlign = TextAlign.Center,
                color = Color.White,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.support_sheet_body),
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = DonateSoftText,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SupportButtons(popup: PopupSheetState) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DonatePillButton(
            text = stringResource(R.string.support_sheet_follow),
            onClick = { context.openUrl(InstagramUrl) },
            modifier = Modifier.fillMaxWidth(),
        )
        DonatePillButton(
            text = stringResource(R.string.support_sheet_dismiss),
            onClick = popup::dismiss,
            quiet = true,
        )
    }
}
