// Daniel's page on dev spaces: banner, avatar, name, a line about him and his links

package com.bedivine.app.ui.screens.settings.devspace

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.transformations
import com.bedivine.app.R
import com.bedivine.app.constants.ProfilePicUriKey
import com.bedivine.app.ui.component.LocalZoomOutOverlayState
import com.bedivine.app.ui.component.pressBounce
import com.bedivine.app.ui.screens.donate.InstagramUrl
import com.bedivine.app.ui.screens.donate.showDonateSheet
import com.bedivine.app.ui.utils.SoftBlurTransformation
import com.bedivine.app.ui.component.RemoteFocus
import com.bedivine.app.ui.component.rememberResourcePainter
import com.bedivine.app.utils.rememberPreference

/** the upstream repo BeDivine is inspired by — the one intentional Mochy mention on this screen */
internal const val MochyRepoUrl = "https://github.com/realidkroo/Mochy-Music"

internal data class DevSocial(val label: String, val url: String)

internal data class DevPerson(
    val name: String,
    @StringRes val bio: Int,
    /** fallback picture when the person has no profile photo (or doesn't use one) */
    @DrawableRes val avatar: Int,
    /** when true, the avatar is the user's own profile photo, falling back to [avatar] */
    val useProfilePhoto: Boolean = false,
    /** the main dev takes donations, the donate section only shows for them */
    val takesDonations: Boolean = false,
    val socials: List<DevSocial> = emptyList(),
)

internal val DevPeople = listOf(
    DevPerson(
        name = "Daniel",
        bio = R.string.devspace_bio,
        avatar = R.drawable.ic_bedivine_mark,
        useProfilePhoto = true,
        socials = listOf(DevSocial("Instagram", InstagramUrl)),
    ),
)

/** the picture to show for a person: their own profile photo when they use one, else their drawable avatar */
@Composable
private fun personPhotoModel(person: DevPerson): Any {
    val profilePicUri by rememberPreference(ProfilePicUriKey, "")
    if (!person.useProfilePhoto) return person.avatar
    // the stored value is a plain file path, coil wants a file:// uri (same conversion as HomeScreen)
    return profilePicUri.takeIf { it.isNotBlank() }
        ?.let { if (it.contains("://")) it else "file://$it" }
        ?: person.avatar
}

/** someone's picture, round, on the mock's light grey */
@Composable
internal fun DevAvatar(person: DevPerson, modifier: Modifier = Modifier) {
    val model = personPhotoModel(person)
    val shaped = modifier.clip(CircleShape).background(Color(0xFFD9D9D9))
    if (model is String) {
        AsyncImage(
            model = model,
            contentDescription = person.name,
            contentScale = ContentScale.Crop,
            modifier = shaped,
        )
    } else {
        Image(
            painter = rememberResourcePainter(model as Int),
            contentDescription = person.name,
            contentScale = ContentScale.Crop,
            modifier = shaped,
        )
    }
}

private val AvatarSize = 84.dp
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** one person's whole page, its sections rise in one after another the first time it shows */
@Composable
internal fun DevProfilePage(
    person: DevPerson,
    scrollState: ScrollState,
    bannerHeight: Dp,
    modifier: Modifier = Modifier,
    below: @Composable ColumnScope.() -> Unit,
) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(900, easing = Emphasized)) }
    // each section starts a little after the one above it
    fun Modifier.rise(step: Int): Modifier = graphicsLayer {
        val p = ((reveal.value - step * 0.08f) / 0.6f).coerceIn(0f, 1f)
        alpha = p
        translationY = (1f - p) * 26.dp.toPx()
    }

    Column(modifier.fillMaxSize().verticalScroll(scrollState)) {
        ProfileHeader(person, scrollState, bannerHeight)

        Column(Modifier.padding(horizontal = DevSideInset)) {
            Text(
                person.name,
                style = devText(40f, tracking = -1.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp).rise(0),
            )
            Column(Modifier.rise(1)) {
                Rule(Modifier.padding(top = 16.dp))
                Text(
                    stringResource(person.bio),
                    style = devText(15f, FontWeight.SemiBold, DevLinkText, -0.4f),
                    modifier = Modifier.padding(vertical = 22.dp),
                )
                Rule()
            }

            if (person.takesDonations || person.socials.isNotEmpty()) {
                Column(Modifier.rise(2).padding(top = 22.dp)) {
                    if (person.takesDonations) {
                        val popup = LocalZoomOutOverlayState.current
                        SectionTitle(stringResource(R.string.devspace_donate))
                        DevLinkRow(stringResource(R.string.devspace_donate_me)) { popup.showDonateSheet() }
                        Spacer(Modifier.height(14.dp))
                    }
                    if (person.socials.isNotEmpty()) {
                        val context = LocalContext.current
                        SectionTitle(stringResource(R.string.devspace_socials))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            person.socials.forEach { social ->
                                DevLinkRow(social.label) { context.openDevUrl(social.url) }
                            }
                        }
                    }
                    Rule(Modifier.padding(top = 22.dp))
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.rise(3).padding(top = 22.dp),
                content = below,
            )
        }
    }
}

/** the banner is the person's own picture, blurred and dimmed, so every page gets its own colour */
@Composable
private fun ProfileHeader(person: DevPerson, scrollState: ScrollState, bannerHeight: Dp) {
    val context = LocalContext.current
    val bannerModel = personPhotoModel(person)
    // blurred once, small and off the main thread, so it costs the same on every phone
    val banner = remember(bannerModel) {
        ImageRequest.Builder(context)
            .data(bannerModel)
            .size(192)
            .transformations(SoftBlurTransformation())
            .build()
    }
    Box(Modifier.fillMaxWidth().height(bannerHeight + AvatarSize / 2)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(bannerHeight)
                .graphicsLayer {
                    translationY = scrollState.value * 0.45f
                    alpha = 1f - (scrollState.value / bannerHeight.toPx()).coerceIn(0f, 1f) * 0.6f
                }
                .background(Color(0xFF4A4A4A)),
        ) {
            AsyncImage(
                model = banner,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        drawRect(Color.Black.copy(alpha = 0.32f))
                        // melts into the black page so the avatar's lower half sits on one colour
                        drawRect(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black))
                    },
            )
        }
        DevAvatar(
            person,
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = DevSideInset - 2.dp)
                .size(AvatarSize)
                .graphicsLayer {
                    val p = (scrollState.value / bannerHeight.toPx()).coerceIn(0f, 1f)
                    val s = 1f - 0.18f * p
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0f, 1f)
                },
        )
    }
}

@Composable
private fun Rule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(DevRule))
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = devText(17f, tracking = -0.6f), modifier = Modifier.padding(bottom = 10.dp))
}

/** a dark row with a grey label as in the mock, it squishes a little under the finger */
@Composable
private fun DevLinkRow(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .pressBounce(interaction, pressedScale = 0.96f)
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DevCard)
            .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick)
            .padding(horizontal = 18.dp),
    ) {
        Text(label, style = devText(15f, FontWeight.SemiBold, DevLinkText, -0.4f), maxLines = 1)
    }
}

/** the one intentional Mochy mention on this screen: a link to the upstream repo BeDivine is inspired by */
@Composable
internal fun MochyAttribution(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    Text(
        text = stringResource(R.string.devspace_attribution),
        style = devText(13f, FontWeight.Medium, DevLinkText, -0.2f).copy(textDecoration = TextDecoration.Underline),
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp)
            .clickable(interactionSource = interaction, indication = RemoteFocus) { context.openDevUrl(MochyRepoUrl) },
    )
}
