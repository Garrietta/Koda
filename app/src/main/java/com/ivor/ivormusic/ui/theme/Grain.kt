package com.ivor.ivormusic.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max

/*
 * Rondo's two textures, kept for its moments - the DJ tile on Home and
 * the record behind every page's header - so the chrome around them can
 * stay flat.
 *
 * Grain: a soft glow between two colors with film grain over it, the
 * noise laid on in Overlay so it lightens and darkens without shifting
 * the hue.
 *
 * Riso: a shape printed in dots, as a risograph prints a flat color -
 * an ordered (Bayer) dither of a tone, here a record's: densest at the
 * label, thinning toward the rim, with the grooves as rings. The dots
 * are a mask made once per size and drawn in whatever ink the page has,
 * at nearest-neighbour scale so each dot stays square and crisp.
 */

private const val GRAIN_SIZE = 128

/** How strongly the grain shows on a moment. */
const val GRAIN_AMOUNT = 0.18f

/** A tile of film grain: gaussian noise around mid grey, opaque. Texture data, not a color. */
internal val GrainImage: ImageBitmap by lazy {
    val random = java.util.Random(11)
    val pixels = IntArray(GRAIN_SIZE * GRAIN_SIZE) {
        val v = (128 + random.nextGaussian() * 46).toInt().coerceIn(0, 255)
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    Bitmap.createBitmap(pixels, GRAIN_SIZE, GRAIN_SIZE, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * A glow behind the content: [hot] at [from] (fractions of the size)
 * falling off to [ground], with grain over it. Clip to the shape first.
 */
fun Modifier.grainGlow(
    hot: Color,
    ground: Color,
    from: Offset = Offset(0.85f, 0.85f),
    reach: Float = 1.1f,
    amount: Float = GRAIN_AMOUNT,
): Modifier = drawWithCache {
    val glow = Brush.radialGradient(
        0f to hot,
        0.5f to lerp(hot, ground, 0.55f),
        1f to ground,
        center = Offset(size.width * from.x, size.height * from.y),
        radius = max(size.width, size.height) * reach,
    )
    val grain = ShaderBrush(ImageShader(GrainImage, TileMode.Repeated, TileMode.Repeated))
    onDrawBehind {
        drawRect(ground)
        drawRect(glow)
        drawRect(grain, alpha = amount, blendMode = BlendMode.Overlay)
    }
}

/** The 8x8 Bayer matrix, thresholds 0..63: the order a riso screen fills its dots in. */
private val BAYER8 = intArrayOf(
    0, 32, 8, 40, 2, 34, 10, 42,
    48, 16, 56, 24, 50, 18, 58, 26,
    12, 44, 4, 36, 14, 46, 6, 38,
    60, 28, 52, 20, 62, 30, 54, 22,
    3, 35, 11, 43, 1, 33, 9, 41,
    51, 19, 59, 27, 49, 17, 57, 25,
    15, 47, 7, 39, 13, 45, 5, 37,
    63, 31, 55, 23, 61, 29, 53, 21,
)

/**
 * How much ink a record takes at [r] (0 at the centre, 1 at the rim): a
 * full label, then grooves as rings that thin toward the edge. Pure, so
 * the print can be checked without drawing it.
 */
internal fun recordInk(r: Float): Float {
    if (r > 1f) return 0f
    if (r < 0.2f) return 0.85f
    if (r < 0.26f) return 0.1f
    val grooves = 0.5f + 0.5f * cos(r * 92f)
    val fade = 1f - (r - 0.26f) / 0.74f
    return (0.18f + 0.5f * grooves) * (0.35f + 0.65f * fade)
}

/** A record's dots as a mask [cells] across: opaque where a dot is printed. */
internal fun recordDitherMask(cells: Int): ImageBitmap {
    val n = cells.coerceAtLeast(8)
    val pixels = IntArray(n * n)
    val c = (n - 1) / 2f
    for (y in 0 until n) for (x in 0 until n) {
        val r = hypot(x - c, y - c) / c
        val threshold = (BAYER8[(y % 8) * 8 + (x % 8)] + 0.5f) / 64f
        if (recordInk(r) > threshold) pixels[y * n + x] = 0xFF shl 24 or 0xFFFFFF
    }
    return Bitmap.createBitmap(pixels, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * The record printed in riso dots of [ink] over [ground]. A dot is
 * [dotPx] across, so the print reads as a print at any size. Clip to the
 * record's outline first.
 */
fun Modifier.risoRecord(ground: Color, ink: Color, dotPx: Float = 9f): Modifier = drawWithCache {
    val cells = (size.minDimension / dotPx).toInt().coerceIn(8, 256)
    val mask = recordDitherMask(cells)
    val side = size.minDimension.toInt()
    val left = ((size.width - side) / 2).toInt()
    val top = ((size.height - side) / 2).toInt()
    onDrawBehind {
        drawRect(ground)
        drawImage(
            mask,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(mask.width, mask.height),
            dstOffset = IntOffset(left, top),
            dstSize = IntSize(side, side),
            colorFilter = ColorFilter.tint(ink),
            filterQuality = FilterQuality.None,
        )
    }
}
