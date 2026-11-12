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
fun ColorScheme.withVividRoles(primarySeed: Color, secondarySeed: Color?, dark: Boolean): ColorScheme {
    val first = hct(primarySeed)
    if (first.chroma < GREY_CHROMA) return this
    val hue1 = first.hue
    val hue2 = wrap(hue1 + SECOND_OFFSET)
    val hue3 = secondarySeed?.let(::hct)
        ?.takeIf { it.chroma >= DISTINCT_CHROMA && hueDistance(it.hue, hue1) >= DISTINCT_HUE }
        ?.hue
        ?: wrap(hue1 + THIRD_OFFSET)
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

/** [withVividRoles] for callers outside this package, the way [TintedPages] is reached. */
object VividRoles {
    fun ColorScheme.vivid(primarySeed: Color, secondarySeed: Color?, dark: Boolean): ColorScheme =
        withVividRoles(primarySeed, secondarySeed, dark)
}
