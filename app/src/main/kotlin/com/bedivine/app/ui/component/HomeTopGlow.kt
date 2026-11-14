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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// how far each corner's glow reaches sideways, so the two meet and blend across the middle
private const val GlowReachFraction = 0.95f
private const val GlowStopCount = 12
private val GlowStopPositions = FloatArray(GlowStopCount + 1) { it / GlowStopCount.toFloat() }

/** settings > customisation > home gradient: where the top glow's colours come from */
enum class HomeGlowMode(val key: String, @StringRes val labelRes: Int) {
    /** the feed covers: the first two covers shown on home, the original behaviour */
    COVERS("covers", R.string.home_glow_mode_covers),
    /** the special one: both corners take their colours from the song playing; with no cover the feed covers fill in */
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

/** everything the customisation page sets for the home glow; the defaults are what a fresh install shows */
data class HomeGlowSettings(
    val mode: HomeGlowMode = HomeGlowMode.COVERS,
    val fixedColor: Color = HomeGlowFixedColor.AMBER.color,
    /** 0..1, the glow's peak alpha (was the GlowPeakAlpha const, 0.9) */
    val strength: Float = 0.9f,
    /** how far down the gradient reaches, in dp past the top inset (was fixed at 150) */
    val length: Float = 150f,
    /** 0..1, how vivid the colours look: 0 is the old dimming (saturation ≤ 0.6, lightness ≤ 0.32), 1 lets them run full */
    val vividness: Float = 0.5f,
    /** on: the colours drift through the cover's tones and the glow breathes gently; off: colours switch instantly, no movement */
    val animated: Boolean = true,
)

// the soft wash at the top of the listen now and hop in homes: the song playing tints both corners, the feed covers fill in when nothing plays
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
    // seeded from the cache so coming back to home shows the glow straight away instead of fading it in again;
    // the seed follows the vividness too, so the slider repaints at once instead of waiting for the reload
    val colors = remember(source, settings.vividness) { mutableStateOf(cachedGlowColors(source, settings.vividness)) }
    LaunchedEffect(source, settings.vividness) { colors.value = loadGlowColors(context, source, settings.vividness) }
    // the cover's tones in walking order, so the animated glow can move through them instead of sitting still
    val palette = remember(source, settings.vividness) { mutableStateOf(cachedGlowPalette(source, settings.vividness)) }
    LaunchedEffect(source, settings.vividness) { palette.value = loadGlowPalette(context, source, settings.vividness) }
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(source, settings.animated, palette.value) {
        step = 0
        if (!settings.animated) return@LaunchedEffect
        val tones = palette.value
        if ((tones?.size ?: 0) < 2) return@LaunchedEffect
        // one step every four seconds; the crossfade below makes the change drift instead of jump
        while (true) {
            delay(4000)
            step++
        }
    }
    // animated: the glow slowly walks through the cover's colours, left and right one step apart,
    // so the gradient itself moves instead of only breathing; off, or with fewer than two tones, it stays put
    val shownPair: Pair<Color, Color>? = when {
        settings.animated && (palette.value?.size ?: 0) >= 2 -> {
            val tones = palette.value!!
            tones[step % tones.size] to tones[(step + 1) % tones.size]
        }
        else -> colors.value
    }

    // starts from clear, so the wash fades in once the colours are sampled and blends over when they change;
    // with animation off the fade is instant, so a new song repaints the glow at once
    val fadeMs = if (settings.animated) 700 else 0
    val left = animateColorAsState(shownPair?.first ?: Color.Transparent, tween(fadeMs), label = "homeGlowLeft")
    val right = animateColorAsState(shownPair?.second ?: Color.Transparent, tween(fadeMs), label = "homeGlowRight")
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

private fun cachedGlowColors(source: GlowSource, vividness: Float): Pair<Color, Color>? = when (source) {
    is GlowSource.Covers -> cachedGlowPair(source.urls, vividness)
    is GlowSource.Vivo -> HomeGlowColor.cachedPair(source.url, vividness)
    is GlowSource.Fixed -> HomeGlowColor.dimmed(source.color, vividness).let { it to it }
}

private suspend fun loadGlowColors(context: Context, source: GlowSource, vividness: Float): Pair<Color, Color>? = when (source) {
    is GlowSource.Covers -> loadGlowPair(context, source.urls, vividness)
    is GlowSource.Vivo -> HomeGlowColor.loadPair(context, source.url, vividness)
    is GlowSource.Fixed -> HomeGlowColor.dimmed(source.color, vividness).let { it to it }
}

private fun cachedGlowPair(urls: List<String>, vividness: Float): Pair<Color, Color>? =
    glowPair(urls.getOrNull(0)?.let { HomeGlowColor.cached(it, vividness) }, urls.getOrNull(1)?.let { HomeGlowColor.cached(it, vividness) })

private suspend fun loadGlowPair(context: Context, urls: List<String>, vividness: Float): Pair<Color, Color>? =
    glowPair(urls.getOrNull(0)?.let { HomeGlowColor.load(context, it, vividness) }, urls.getOrNull(1)?.let { HomeGlowColor.load(context, it, vividness) })

// the tones the animated glow walks through: the playing cover's palette, or the feed covers' palettes merged
private fun cachedGlowPalette(source: GlowSource, vividness: Float): List<Color>? = when (source) {
    is GlowSource.Covers -> mergePalettes(source.urls.mapNotNull { HomeGlowColor.cachedPalette(it, vividness) })
    is GlowSource.Vivo -> HomeGlowColor.cachedPalette(source.url, vividness)
    is GlowSource.Fixed -> null
}

private suspend fun loadGlowPalette(context: Context, source: GlowSource, vividness: Float): List<Color>? = when (source) {
    is GlowSource.Covers -> mergePalettes(source.urls.mapNotNull { HomeGlowColor.loadPalette(context, it, vividness) })
    is GlowSource.Vivo -> HomeGlowColor.loadPalette(context, source.url, vividness)
    is GlowSource.Fixed -> null
}

// merged in order, each tone a clearly different hue from the ones before it, up to five;
// a cover with only one colour gives no walk, so the glow stays put instead of cycling on itself
private fun mergePalettes(palettes: List<List<Color>>): List<Color>? {
    val tones = mutableListOf<Color>()
    for (palette in palettes) {
        for (tone in palette) {
            if (tones.size >= 5) break
            val hsl = FloatArray(3).also { ColorUtils.colorToHSL(tone.toArgb(), it) }
            if (tones.any { existing ->
                val existingHsl = FloatArray(3).also { ColorUtils.colorToHSL(existing.toArgb(), it) }
                hueDistance(existingHsl[0], hsl[0]) < 30f
            }) continue
            tones += tone
        }
    }
    return tones.takeIf { it.size >= 2 }
}

// how far apart two hues sit on the wheel, 0..180
private fun hueDistance(a: Float, b: Float): Float {
    val d = kotlin.math.abs(a - b) % 360f
    return minOf(d, 360f - d)
}

// one raw colour per cover, sampled off the main thread from a tiny copy and kept so a cover is never sampled twice;
// the dimming — and the vividness the user picks — is applied when the colour is read, so the slider repaints at once
private object HomeGlowColor {
    private val cache = LruCache<String, Int>(48)
    private val pairCache = LruCache<String, Pair<Int, Int>>(48)

    fun cached(url: String, vividness: Float): Color? = cache.get(url)?.let { Color(dimArgb(it, vividness)) }

    suspend fun load(context: Context, url: String, vividness: Float): Color? {
        cached(url, vividness)?.let { return it }
        val bitmap = loadBitmap(context, url) ?: return null
        val argb = withContext(Dispatchers.Default) { rawTone(bitmap) } ?: return null
        cache.put(url, argb)
        return Color(dimArgb(argb, vividness))
    }

    fun cachedPair(url: String, vividness: Float): Pair<Color, Color>? =
        pairCache.get(url)?.let { Color(dimArgb(it.first, vividness)) to Color(dimArgb(it.second, vividness)) }

    // both corners from one cover: its main tone and a second, different tone from the same cover
    suspend fun loadPair(context: Context, url: String, vividness: Float): Pair<Color, Color>? {
        cachedPair(url, vividness)?.let { return it }
        val bitmap = loadBitmap(context, url) ?: return null
        val tones = withContext(Dispatchers.Default) { rawPairTones(bitmap) } ?: return null
        pairCache.put(url, tones)
        return Color(dimArgb(tones.first, vividness)) to Color(dimArgb(tones.second, vividness))
    }

    // the cover's tones for the animated walk: up to four, biggest patches first, each a clearly different hue
    private val paletteCache = LruCache<String, List<Int>>(48)

    fun cachedPalette(url: String, vividness: Float): List<Color>? =
        paletteCache.get(url)?.map { Color(dimArgb(it, vividness)) }?.takeIf { it.size >= 2 }

    suspend fun loadPalette(context: Context, url: String, vividness: Float): List<Color>? {
        cachedPalette(url, vividness)?.let { return it }
        val bitmap = loadBitmap(context, url) ?: return null
        val tones = withContext(Dispatchers.Default) { rawPaletteTones(bitmap) } ?: return null
        paletteCache.put(url, tones)
        return tones.map { Color(dimArgb(it, vividness)) }.takeIf { it.size >= 2 }
    }

    private fun rawPaletteTones(bitmap: Bitmap): List<Int>? {
        val swatches = Palette.from(bitmap).maximumColorCount(12).generate().swatches
        val dominant = swatches.maxByOrNull { it.population } ?: return null
        val ranked = swatches
            .filter { it.hsl[1] >= 0.2f && it.hsl[2] in 0.08f..0.9f && it.population >= dominant.population / 8 }
            .sortedByDescending { it.population }
        val picked = mutableListOf<Palette.Swatch>()
        for (swatch in ranked) {
            if (picked.size >= 4) break
            if (picked.any { hueDistance(it.hsl[0], swatch.hsl[0]) < 30f }) continue
            picked += swatch
        }
        return picked.map { it.rgb }.takeIf { it.size >= 2 }
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

    // the biggest patch of real colour on the cover (its main grey if it has none), undimmed
    private fun rawTone(bitmap: Bitmap): Int? {
        val swatches = Palette.from(bitmap).maximumColorCount(12).generate().swatches
        return pickGlowSwatch(swatches)?.rgb
    }

    // two raw tones from one cover: its main tone, plus the biggest patch of a clearly different hue;
    // a cover with only one colour paints both corners with it, like a single cover does today
    private fun rawPairTones(bitmap: Bitmap): Pair<Int, Int>? {
        val swatches = Palette.from(bitmap).maximumColorCount(12).generate().swatches
        val first = pickGlowSwatch(swatches) ?: return null
        val second = swatches
            .filter { it !== first && hueDistance(it.hsl[0], first.hsl[0]) >= 30f }
            .filter { it.hsl[1] >= 0.2f && it.hsl[2] in 0.08f..0.9f && it.population >= first.population / 8 }
            .maxByOrNull { it.population }
            ?: swatches.filter { it !== first }.maxByOrNull { it.population }
            ?: first
        return first.rgb to second.rgb
    }

    // the main tone: the biggest patch of real colour, or the cover's dominant swatch when it has none
    private fun pickGlowSwatch(swatches: List<Palette.Swatch>): Palette.Swatch? {
        val dominant = swatches.maxByOrNull { it.population } ?: return null
        return swatches
            .filter { it.hsl[1] >= 0.2f && it.hsl[2] in 0.08f..0.9f && it.population >= dominant.population / 6 }
            .maxByOrNull { it.population }
            ?: dominant
    }

    // dimmed for the glow: some colour kept, but dark enough for white text.
    // vividness 0 is exactly the old dimming (saturation ≤ 0.6, lightness 0.2–0.32);
    // vividness 1 lets saturation run full and lightness reach 0.60
    private fun dimArgb(argb: Int, vividness: Float): Int {
        val v = vividness.coerceIn(0f, 1f)
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(argb, hsl)
        hsl[1] = hsl[1].coerceAtMost(0.6f + 0.4f * v)
        hsl[2] = hsl[2].coerceIn(0.2f, 0.32f + 0.28f * v)
        return ColorUtils.HSLToColor(hsl)
    }

    // a picked colour dimmed the same way the sampled tones are, so a fixed colour glows as softly as a cover's
    fun dimmed(color: Color, vividness: Float): Color = Color(dimArgb(color.toArgb(), vividness))
}
