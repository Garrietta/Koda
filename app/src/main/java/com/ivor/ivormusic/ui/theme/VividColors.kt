@file:Suppress("RestrictedApi")

package com.ivor.ivormusic.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.utilities.Contrast
import com.google.android.material.color.utilities.Hct
import kotlin.math.abs

/*
 * Colors made vivid the way Rondo's own are.
 *
 * Rondo's scheme puts its brand colors in the container roles at full
 * strength: iris, orchid and orange blocks with ink or cream on them.
 * A scheme grown from a seed the usual Material way puts pastel tones
 * there instead, so a player or page colored from an album cover looked
 * washed out next to the tiles. These functions take any seed and give
 * back blocks at the same strength, related to each other the way
 * Rondo's three are.
 */

/** A block of color and the ink that reads on it. */
data class VividBlock(val container: Color, val onContainer: Color)

/** How far orchid and orange sit round the hue circle from iris, in HCT degrees. */
private const val SECOND_OFFSET = 28.5
private const val THIRD_OFFSET = 110.4

/** A block's tone is where its hue is most colorful, kept between these. */
private const val LOWEST_TONE = 42.0
private const val HIGHEST_TONE = 72.0

/** Below this chroma a seed is grey, and has no hue worth building on. */
private const val GREY_CHROMA = 8.0

/** Below this a second seed is too grey, or too close to the first, to stand for anything. */
private const val DISTINCT_CHROMA = 12.0
private const val DISTINCT_HUE = 30.0

/** Asks for as much chroma as the hue can carry; Hct clamps to the gamut. */
private const val ALL_CHROMA = 200.0

private fun Hct.color(): Color = Color(toInt())

private fun hct(color: Color): Hct = Hct.fromInt(color.toArgb())

private fun wrap(hue: Double): Double = ((hue % 360.0) + 360.0) % 360.0

private fun hueDistance(a: Double, b: Double): Double = abs(wrap(a - b)).let { if (it > 180.0) 360.0 - it else it }

/**
 * The block for [hue]: the hue at its most colorful tone, with ink or
 * cream on it, whichever reads better. Blue and violet peak dark, so
 * they take cream; orange and yellow peak light, so they take ink.
 */
fun vividBlock(hue: Double): VividBlock {
    var step = LOWEST_TONE
    var bestTone = LOWEST_TONE
    var bestChroma = -1.0
    while (step <= HIGHEST_TONE) {
        val chroma = Hct.from(hue, ALL_CHROMA, step).chroma
        // Half a unit of slack, so a plateau resolves to its darkest tone.
        if (chroma > bestChroma + 0.5) {
            bestChroma = chroma
            bestTone = step
        }
        step += 2.0
    }
    val ink = Hct.from(hue, 16.0, 10.0)
    val cream = Hct.from(hue, 8.0, 97.0)
    val inkReads = Contrast.ratioOfTones(ink.tone, bestTone) >= Contrast.ratioOfTones(cream.tone, bestTone)
    // A few hues peak at a middle tone where neither ink nor cream reads
    // well enough. Such a block steps away from the middle, toward the
    // side its better ink already favours, until that ink reads.
    var tone = bestTone
    val text = if (inkReads) ink else cream
    while (Contrast.ratioOfTones(text.tone, tone) < READABLE && tone in 30.0..85.0) {
        tone += if (inkReads) 1.0 else -1.0
    }
    return VividBlock(Hct.from(hue, ALL_CHROMA, tone).color(), text.color())
}

/** Ink on a block reads at this contrast or better. */
private const val READABLE = 4.6

/** The block for [seed]'s hue, or null when [seed] is grey. */
fun vividBlockOf(seed: Color): VividBlock? = hct(seed).takeIf { it.chroma >= GREY_CHROMA }?.let { vividBlock(it.hue) }

/**
 * A scheme whose accent roles are blocks built from [primarySeed]: its
 * hue as primary, the hue orchid stands at from iris as secondary, and
 * as tertiary [secondarySeed]'s hue when it is a color of its own, or
 * the hue orange stands at from iris when it is not. The text roles
 * (primary, secondary, tertiary) are the same hues at a tone that reads
 * on the page. Surfaces are left as they are.
 *
 * Returns the scheme unchanged when [primarySeed] is grey: a black and
 * white cover has no color to give, so the app keeps its own.
 */
fun ColorScheme.withVividRoles(
    primarySeed: Color,
    secondarySeed: Color?,
    dark: Boolean,
    /**
     * The palette style chosen in Customization, applied to how the three
     * blocks' hues spread: close neighbours by default, wider apart for
     * Expressive, a turn the other way for Fruit Salad, one hue for Monochrome.
     */
    style: PaletteStyle = PaletteStyle.TONAL_SPOT,
): ColorScheme {
    val first = hct(primarySeed)
    if (first.chroma < GREY_CHROMA) return this
    val hue1 = first.hue
    val (secondOffset, thirdOffset) = vividOffsets(style)
    val hue2 = wrap(hue1 + secondOffset)
    val hue3 = if (style == PaletteStyle.MONOCHROME) hue1 else secondarySeed?.let(::hct)
        ?.takeIf { it.chroma >= DISTINCT_CHROMA && hueDistance(it.hue, hue1) >= DISTINCT_HUE }
        ?.hue
        ?: wrap(hue1 + thirdOffset)
    val one = vividBlock(hue1)
    val two = vividBlock(hue2)
    val three = vividBlock(hue3)
    fun text(hue: Double) = Hct.from(hue, ALL_CHROMA, if (dark) 80.0 else 40.0).color()
    fun onText(hue: Double) = Hct.from(hue, 12.0, if (dark) 20.0 else 100.0).color()
    return copy(
        primary = text(hue1),
        onPrimary = onText(hue1),
        primaryContainer = one.container,
        onPrimaryContainer = one.onContainer,
        inversePrimary = Hct.from(hue1, ALL_CHROMA, if (dark) 40.0 else 80.0).color(),
        secondary = text(hue2),
        onSecondary = onText(hue2),
        secondaryContainer = two.container,
        onSecondaryContainer = two.onContainer,
        tertiary = text(hue3),
        onTertiary = onText(hue3),
        tertiaryContainer = three.container,
        onTertiaryContainer = three.onContainer,
        surfaceTint = text(hue1),
    )
}

/** How far the second and third blocks' hues sit from the first, for each palette style. */
internal fun vividOffsets(style: PaletteStyle): Pair<Double, Double> = when (style) {
    PaletteStyle.TONAL_SPOT, PaletteStyle.VIBRANT -> SECOND_OFFSET to THIRD_OFFSET
    PaletteStyle.EXPRESSIVE -> 60.0 to 180.0
    PaletteStyle.FRUIT_SALAD -> -50.0 to 140.0
    PaletteStyle.MONOCHROME -> 0.0 to 0.0
}

/** [withVividRoles] for callers outside this package, the way [TintedPages] is reached. */
object VividRoles {
    fun ColorScheme.vivid(
        primarySeed: Color,
        secondarySeed: Color?,
        dark: Boolean,
        style: PaletteStyle = PaletteStyle.TONAL_SPOT,
    ): ColorScheme = withVividRoles(primarySeed, secondarySeed, dark, style)
}

/** One color found in a cover, and how many of its pixels are that color. */
data class CoverColor(val color: Color, val population: Int)

/**
 * The colors a cover's player is built from: first the most colorful one
 * that also covers a real share of the art, then the most colorful one of
 * a clearly different hue. A speck of red does not outvote a sky, and a
 * sky outvotes a field of tan: muddy yellows and browns (wood, hay, skin,
 * old paper) count for half, so a muted cover with some blue in it gives
 * a blue player rather than a beige one. Null when the cover is grey.
 */
fun pickVividSeeds(colors: List<CoverColor>): Pair<Color, Color?>? {
    val total = colors.sumOf { it.population }.coerceAtLeast(1).toDouble()
    val scored = colors.mapNotNull { c ->
        val h = hct(c.color)
        if (h.chroma < GREY_CHROMA || c.population <= 0) return@mapNotNull null
        var score = h.chroma * Math.pow(c.population / total, 0.35)
        if (h.hue in MUDDY_HUES && h.chroma < MUDDY_CHROMA) score *= 0.5
        Triple(c.color, h.hue, score)
    }.sortedByDescending { it.third }
    val first = scored.firstOrNull() ?: return null
    val second = scored.drop(1).firstOrNull { hueDistance(it.second, first.second) >= SEED_HUE_APART }
    return first.first to second?.first
}

/** Yellow-brown hues that read as mud rather than color unless strongly saturated. */
private val MUDDY_HUES = 45.0..110.0
private const val MUDDY_CHROMA = 40.0

/** How far apart the two seeds' hues must be to count as a second color. */
private const val SEED_HUE_APART = 40.0
