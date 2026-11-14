// HomeTopGlow.kt

package com.bedivine.app.ui.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.LruCache
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.bedivine.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// how far each corner's glow reaches sideways, so the two meet and blend across the middle
private const val GlowReachFraction = 0.95f
private const val GlowStopCount = 12
private val GlowStopPositions = FloatArray(GlowStopCount + 1) { it / GlowStopCount.toFloat() }

/** settings > customisation > home gradient: where the top glow's colours come from */
enum class HomeGlowMode(val key: String, @StringRes val labelRes: Int) {
    /** today's behaviour: the first two feed covers tint the corners */
    COVERS("covers", R.string.home_glow_mode_covers),
    /** the special one: both corners take their colours from the song playing */
    VIVO("vivo", R.string.home_glow_mode_vivo),
    /** one fixed colour for both corners, no cover sampled */
    FIXED("fixed", R.string.home_glow_mode_fixed);

    companion object {
        fun fromKey(key: String): HomeGlowMode = entries.find { it.key == key } ?: COVERS
    }
}

/** the fixed colours the glow can be painted with; the glow itself dims them like a sampled tone */
enum class HomeGlowFixedColor(val key: String, @StringRes val labelRes: Int, val color: Color) {
    AMBER("amber", R.string.home_glow_color_amber, Color(0xFFE09112)),
    BLUE("blue", R.string.home_glow_color_blue, Color(0xFF2E7DE1)),
    VIOLET("violet", R.string.home_glow_color_violet, Color(0xFF7C4DFF)),
    GREEN("green", R.string.home_glow_color_green, Color(0xFF2E9E5B)),
    RED("red", R.string.home_glow_color_red, Color(0xFFE14B4B)),
    PINK("pink", R.string.home_glow_color_pink, Color(0xFFE14B9B));

    companion object {
        fun fromKey(key: String): HomeGlowFixedColor = entries.find { it.key == key } ?: AMBER
    }
}

/** how far down the gradient may reach, in dp past the top inset */
const val HomeGlowLengthMin = 80f
const val HomeGlowLengthMax = 400f

/** everything the customisation page sets for the home glow; the defaults are today's behaviour */
data class HomeGlowSettings(
    val mode: HomeGlowMode = HomeGlowMode.COVERS,
    val fixedColor: Color = HomeGlowFixedColor.AMBER.color,
    /** 0..1, the glow's peak alpha (was the GlowPeakAlpha const, 0.9) */
    val strength: Float = 0.9f,
    /** how far down the gradient reaches, in dp past the top inset (was fixed at 150) */
    val length: Float = 150f,
    /** on: colours crossfade and the glow breathes gently; off: colours switch instantly, no movement */
    val animated: Boolean = true,
)

// the soft wash at the top of the listen now and hop in homes: the first cover tints the left corner, the second the right
@Composable
fun HomeTopGlow(
    coverUrls: List<String>,
    depth: Dp,
    visibility: () -> Float,
    modifier: Modifier = Modifier,
    settings: HomeGlowSettings = HomeGlowSettings(),
    /** the song playing's cover, for Color Vivo; null falls back to the feed covers */
    vivoCoverUrl: String? = null,
) {
    val context = LocalContext.current
    // what paints the glow this time: the feed covers, the song playing, or one fixed colour
    val source = remember(settings.mode, settings.fixedColor, vivoCoverUrl, coverUrls) {
        when {
            settings.mode == HomeGlowMode.FIXED -> GlowSource.Fixed(settings.fixedColor)
            settings.mode == HomeGlowMode.VIVO && !vivoCoverUrl.isNullOrBlank() -> GlowSource.Vivo(vivoCoverUrl)
            else -> GlowSource.Covers(coverUrls)
        }
    }
    // seeded from the cache so coming back to home shows the glow straight away instead of fading it in again
    val colors = remember { mutableStateOf(cachedGlowColors(source)) }
    LaunchedEffect(source) { colors.value = loadGlowColors(context, source) }

    // starts from clear, so the wash fades in once the colours are sampled and blends over when they change;
    // with animation off the fade is instant, so a new song repaints the glow at once
    val fadeMs = if (settings.animated) 700 else 0
    val left = animateColorAsState(colors.value?.first ?: Color.Transparent, tween(fadeMs), label = "homeGlowLeft")
    val right = animateColorAsState(colors.value?.second ?: Color.Transparent, tween(fadeMs), label = "homeGlowRight")
    val leftPaint = remember { Paint().apply { isDither = true } }
    val rightPaint = remember { Paint().apply { isDither = true } }
    // a gentle breathing while animated: the glow swells and settles about ±8% over ~4s
    val breathe = remember { Animatable(1f) }
    LaunchedEffect(settings.animated) {
        if (!settings.animated) {
            breathe.snapTo(1f)
            return@LaunchedEffect
        }
        while (true) {
            breathe.animateTo(1.08f, tween(2000, easing = LinearEasing))
            breathe.animateTo(0.92f, tween(2000, easing = LinearEasing))
        }
    }

    Spacer(
        modifier = modifier
            // the scroll fade only moves this layer's alpha, so scrolling never redraws the wash
            .graphicsLayer {
                alpha = (visibility() * breathe.value).coerceIn(0f, 1f)
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .drawWithCache {
                val leftColor = left.value
                val rightColor = right.value
                val width = size.width
                val height = depth.toPx()
                if (width <= 0f || height <= 0f || (leftColor.alpha == 0f && rightColor.alpha == 0f)) {
                    return@drawWithCache onDrawBehind {}
                }
                val peakAlpha = settings.strength.coerceIn(0f, 1f)
                leftPaint.shader = cornerGlow(leftColor, width * 0.08f, width, height, peakAlpha)
                rightPaint.shader = cornerGlow(rightColor, width * 0.92f, width, height, peakAlpha)
                // drawn past this spacer's own bottom when the section under it is short, so the fade never gets cut off
                onDrawBehind {
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawRect(0f, 0f, width, height, leftPaint)
                        canvas.nativeCanvas.drawRect(0f, 0f, width, height, rightPaint)
                    }
                }
            }
    )
}

// an oval glow hanging from the top edge, eased out to nothing by the bottom so there's no line where it ends
private fun cornerGlow(color: Color, centerX: Float, width: Float, height: Float, peakAlpha: Float): Shader {
    val rgb = color.copy(alpha = 1f).toArgb()
    val stops = IntArray(GlowStopCount + 1) { i ->
        val remaining = 1f - GlowStopPositions[i]
        val falloff = remaining * remaining * (3f - 2f * remaining)
        ColorUtils.setAlphaComponent(rgb, (color.alpha * peakAlpha * falloff * 255f).roundToInt())
    }
    return RadialGradient(centerX, 0f, height, stops, GlowStopPositions, Shader.TileMode.CLAMP).apply {
        setLocalMatrix(Matrix().apply { setScale(width * GlowReachFraction / height, 1f, centerX, 0f) })
    }
}

private fun glowPair(left: Color?, right: Color?): Pair<Color, Color>? = when {
    left != null -> left to (right ?: left)
    right != null -> right to right
    else -> null
}

/** where the glow's colours come from, so a change of mode or song reloads them and nothing else */
private sealed interface GlowSource {
    data class Covers(val urls: List<String>) : GlowSource
    data class Vivo(val url: String) : GlowSource
    data class Fixed(val color: Color) : GlowSource
}

private fun cachedGlowColors(source: GlowSource): Pair<Color, Color>? = when (source) {
    is GlowSource.Covers -> cachedGlowPair(source.urls)
    is GlowSource.Vivo -> HomeGlowColor.cachedPair(source.url)
    is GlowSource.Fixed -> HomeGlowColor.dimmed(source.color).let { it to it }
}

private suspend fun loadGlowColors(context: Context, source: GlowSource): Pair<Color, Color>? = when (source) {
    is GlowSource.Covers -> loadGlowPair(context, source.urls)
    is GlowSource.Vivo -> HomeGlowColor.loadPair(context, source.url)
    is GlowSource.Fixed -> HomeGlowColor.dimmed(source.color).let { it to it }
}

private fun cachedGlowPair(urls: List<String>): Pair<Color, Color>? =
    glowPair(urls.getOrNull(0)?.let(HomeGlowColor::cached), urls.getOrNull(1)?.let(HomeGlowColor::cached))

private suspend fun loadGlowPair(context: Context, urls: List<String>): Pair<Color, Color>? =
    glowPair(urls.getOrNull(0)?.let { HomeGlowColor.load(context, it) }, urls.getOrNull(1)?.let { HomeGlowColor.load(context, it) })

// one dim colour per cover, sampled off the main thread from a tiny copy and kept so a cover is never sampled twice
private object HomeGlowColor {
    private val cache = LruCache<String, Int>(48)
    private val pairCache = LruCache<String, Pair<Int, Int>>(48)

    fun cached(url: String): Color? = cache.get(url)?.let { Color(it) }

    suspend fun load(context: Context, url: String): Color? {
        cached(url)?.let { return it }
        val bitmap = loadBitmap(context, url) ?: return null
        val argb = withContext(Dispatchers.Default) { glowTone(bitmap) } ?: return null
        cache.put(url, argb)
        return Color(argb)
    }

    fun cachedPair(url: String): Pair<Color, Color>? =
        pairCache.get(url)?.let { Color(it.first) to Color(it.second) }

    // both corners from one cover: its main tone and a second, different tone from the same cover
    suspend fun loadPair(context: Context, url: String): Pair<Color, Color>? {
        cachedPair(url)?.let { return it }
        val bitmap = loadBitmap(context, url) ?: return null
        val tones = withContext(Dispatchers.Default) { pairTones(bitmap) } ?: return null
        pairCache.put(url, tones)
        return Color(tones.first) to Color(tones.second)
    }

    private suspend fun loadBitmap(context: Context, url: String): Bitmap? {
        return try {
            withContext(Dispatchers.IO) {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .size(64)
                    .allowHardware(false)
                    .build()
                context.imageLoader.execute(request).image?.toBitmap()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    // the biggest patch of real colour on the cover (its main grey if it has none), kept dim so white text stays easy to read on it
    private fun glowTone(bitmap: Bitmap): Int? {
        val swatches = Palette.from(bitmap).maximumColorCount(12).generate().swatches
        val pick = pickGlowSwatch(swatches) ?: return null
        return dimSwatch(pick)
    }

    // two tones from one cover: its main tone, plus the biggest patch of a clearly different hue;
    // a cover with only one colour paints both corners with it, like a single cover does today
    private fun pairTones(bitmap: Bitmap): Pair<Int, Int>? {
        val swatches = Palette.from(bitmap).maximumColorCount(12).generate().swatches
        val first = pickGlowSwatch(swatches) ?: return null
        val second = swatches
            .filter { it !== first && hueDistance(it.hsl[0], first.hsl[0]) >= 30f }
            .filter { it.hsl[1] >= 0.2f && it.hsl[2] in 0.08f..0.9f && it.population >= first.population / 8 }
            .maxByOrNull { it.population }
            ?: swatches.filter { it !== first }.maxByOrNull { it.population }
            ?: first
        return dimSwatch(first) to dimSwatch(second)
    }

    // the main tone: the biggest patch of real colour, or the cover's dominant swatch when it has none
    private fun pickGlowSwatch(swatches: List<Palette.Swatch>): Palette.Swatch? {
        val dominant = swatches.maxByOrNull { it.population } ?: return null
        return swatches
            .filter { it.hsl[1] >= 0.2f && it.hsl[2] in 0.08f..0.9f && it.population >= dominant.population / 6 }
            .maxByOrNull { it.population }
            ?: dominant
    }

    // dimmed the same way every glow tone is: some colour kept, but dark enough for white text
    private fun dimSwatch(swatch: Palette.Swatch): Int {
        val hsl = swatch.hsl.copyOf()
        hsl[1] = hsl[1].coerceAtMost(0.6f)
        hsl[2] = hsl[2].coerceIn(0.2f, 0.32f)
        return ColorUtils.HSLToColor(hsl)
    }

    // a picked colour dimmed the same way the sampled tones are, so a fixed colour glows as softly as a cover's
    fun dimmed(color: Color): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color.toArgb(), hsl)
        hsl[1] = hsl[1].coerceAtMost(0.6f)
        hsl[2] = hsl[2].coerceIn(0.2f, 0.32f)
        return Color(ColorUtils.HSLToColor(hsl))
    }

    // how far apart two hues sit on the wheel, 0..180
    private fun hueDistance(a: Float, b: Float): Float {
        val d = kotlin.math.abs(a - b) % 360f
        return minOf(d, 360f - d)
    }
}
