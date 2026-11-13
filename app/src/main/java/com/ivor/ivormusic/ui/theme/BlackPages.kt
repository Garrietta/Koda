package com.ivor.ivormusic.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/*
 * In dark mode a page opened from Home is the tile turned inside out: the
 * tile's dark ink becomes a true black ground, so an OLED screen is off
 * behind it, and the tile's color becomes the ink - For you is a black
 * page with orange buttons and orange words.
 */

/** The ground of a page opened from Home in dark mode. */
val PageBlack: Color = Color.Black

/**
 * How bright the ink of a black page must be, as relative luminance: 7:1
 * against black, so the dimmer steps the page's scheme mixes from it
 * (secondary text, outlines) still read.
 */
const val BLACK_PAGE_INK_LUMINANCE = 0.30f

/** A tile this dark has no color to lend; its ink is used instead. */
private const val NEAR_BLACK = 0.03f

/**
 * The ink of a tile's page in dark mode: the tile's own color [ground],
 * or its [ink] when the tile is itself near black, lifted toward white
 * only as far as it must go to read on black, so it keeps as much of
 * its color as it can.
 */
fun blackPageInk(ground: Color, ink: Color): Color {
    val color = if (ground.luminance() < NEAR_BLACK && ink.luminance() > ground.luminance()) ink else ground
    if (color.luminance() >= BLACK_PAGE_INK_LUMINANCE) return color
    var low = 0f
    var high = 1f
    repeat(14) {
        val mid = (low + high) / 2f
        if (lerp(color, Color.White, mid).luminance() >= BLACK_PAGE_INK_LUMINANCE) high = mid else low = mid
    }
    return lerp(color, Color.White, high)
}
