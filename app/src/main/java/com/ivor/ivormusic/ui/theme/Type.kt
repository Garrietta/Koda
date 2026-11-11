@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.ivor.ivormusic.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ivor.ivormusic.R

/*
 * Rondo's type: one variable font, Google Sans Flex, bundled with the app
 * so the first screen is set in it with or without Play Services and with
 * or without a connection.
 *
 * Three cuts of the one file do all the work:
 *
 *   Text      width 100, square terminals   everything that is read
 *   Headline  width 110, round terminals    section and page headings
 *   Display   width 125, round terminals    the few very large lines
 *
 * The rounding is the name: a rondo comes round again, and the headings
 * are the part of the page that returns on every screen. Body text stays
 * square because round terminals cost legibility at 14sp.
 */

private const val SQUARE = 0f
private const val ROUND = 100f

/** Every weight the font has, so no call site is ever given a faked bold. */
private val WEIGHTS = intArrayOf(300, 400, 500, 600, 700, 800, 900)

private fun flex(width: Float, round: Float): FontFamily = FontFamily(
    WEIGHTS.map { weight ->
        Font(
            resId = R.font.google_sans_flex,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight),
                FontVariation.width(width),
                FontVariation.Setting("ROND", round),
            ),
        )
    }
)

val RondoText: FontFamily = flex(width = 100f, round = SQUARE)
val RondoHeadline: FontFamily = flex(width = 110f, round = ROUND)
val RondoDisplay: FontFamily = flex(width = 125f, round = ROUND)

// The names the screens were written against. Each now points at the cut
// that does the same job, so a screen that asks for one by name is set in
// the same face as the rest of the app.
val RobotoFlex: FontFamily = RondoText
val RobotoFlexMediumWidth: FontFamily = RondoHeadline
val MontserratFamily: FontFamily = RondoDisplay

private val Tight = PlatformTextStyle(includeFontPadding = false)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = RondoDisplay,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 68.sp,
        lineHeight = 0.9.em,
        letterSpacing = (-0.03).em,
        platformStyle = Tight,
    ),
    displayMedium = TextStyle(
        fontFamily = RondoHeadline,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.02).em,
    ),
    displaySmall = TextStyle(
        fontFamily = RondoHeadline,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.01).em,
    ),
    headlineLarge = TextStyle(
        fontFamily = RondoHeadline,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = RondoHeadline,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = RondoHeadline,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = RondoText,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = RondoText,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = RondoText,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = RondoText,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.2.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = RondoText,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = RondoText,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = RondoText,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = RondoText,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = RondoText,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
)
