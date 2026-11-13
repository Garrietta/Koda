package com.ivor.ivormusic.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Text reads on its ground at this contrast or better (WCAG AA for body text). */
const val READABLE_CONTRAST = 4.5f

/** The WCAG contrast ratio between two colors, 1 to 21. */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/**
 * The ink for [ground]: [preferred] when it reads there, else whichever
 * of it and [fallback] reads better. For a ground that was moved away
 * from the color its ink was chosen for, such as a container darkened
 * for a dark theme, where the container's own ink no longer shows.
 */
fun readableOn(ground: Color, preferred: Color, fallback: Color): Color = when {
    contrastRatio(ground, preferred) >= READABLE_CONTRAST -> preferred
    contrastRatio(ground, fallback) > contrastRatio(ground, preferred) -> fallback
    else -> preferred
}
