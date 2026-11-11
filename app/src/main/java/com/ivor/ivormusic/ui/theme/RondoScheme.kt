package com.ivor.ivormusic.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Rondo's own colors, written out role by role rather than grown from a
 * seed.
 *
 * The idea is blocks: the app is laid out in large flat tiles, and color
 * is what tells one from the next, so the container roles carry the
 * brand colors at full strength instead of the muted tones a seed gives
 * them. Iris is primaryContainer, orchid secondaryContainer, the orange
 * of the play triangle tertiaryContainer, and the cream of the record is
 * the inverse surface. The plain primary, secondary and tertiary roles
 * are lighter (or, by day, deeper) cuts of the same three, so text and
 * icons drawn in them stay readable on the ground.
 *
 * The surface roles are written for the re-seating every scheme gets at
 * the root (withTintedPages): surfaceContainer is the page and
 * surfaceBright is the tile that sits on it.
 */

const val RONDO_PALETTE_ID = "rondo"

private val Iris = Color(0xFF5B4BFF)
private val Orchid = Color(0xFFC77DF2)
private val Orange = Color(0xFFFF7A45)
private val Cream = Color(0xFFFFF1E0)
private val Ink = Color(0xFF17130F)

private val RondoDark = darkColorScheme(
    primary = Color(0xFFC0B8FF),
    onPrimary = Color(0xFF1B1160),
    primaryContainer = Iris,
    onPrimaryContainer = Cream,
    inversePrimary = Color(0xFF4B3BEB),
    secondary = Color(0xFFE3BBF9),
    onSecondary = Color(0xFF3A1450),
    secondaryContainer = Orchid,
    onSecondaryContainer = Color(0xFF1D0A2B),
    tertiary = Color(0xFFFFB59A),
    onTertiary = Color(0xFF5A1B00),
    tertiaryContainer = Orange,
    onTertiaryContainer = Color(0xFF1A0F0A),
    background = Color(0xFF0E0D12),
    onBackground = Color(0xFFF2EFF7),
    surface = Color(0xFF0E0D12),
    onSurface = Color(0xFFF2EFF7),
    surfaceVariant = Color(0xFF2A2733),
    onSurfaceVariant = Color(0xFFC9C4D4),
    surfaceTint = Color(0xFFC0B8FF),
    inverseSurface = Cream,
    inverseOnSurface = Ink,
    outline = Color(0xFF948FA3),
    outlineVariant = Color(0xFF474352),
    surfaceDim = Color(0xFF0E0D12),
    surfaceBright = Color(0xFF1F1D28),
    surfaceContainerLowest = Color(0xFF09080C),
    surfaceContainerLow = Color(0xFF15141B),
    surfaceContainer = Color(0xFF0E0D12),
    surfaceContainerHigh = Color(0xFF24222D),
    surfaceContainerHighest = Color(0xFF2E2C38),
)

private val RondoLight = lightColorScheme(
    primary = Color(0xFF4B3BEB),
    onPrimary = Color.White,
    primaryContainer = Iris,
    onPrimaryContainer = Cream,
    inversePrimary = Color(0xFFC0B8FF),
    secondary = Color(0xFF7E35AD),
    onSecondary = Color.White,
    secondaryContainer = Orchid,
    onSecondaryContainer = Color(0xFF1D0A2B),
    tertiary = Color(0xFFB23C0A),
    onTertiary = Color.White,
    tertiaryContainer = Orange,
    onTertiaryContainer = Color(0xFF1A0F0A),
    background = Color(0xFFFBF3E8),
    onBackground = Ink,
    surface = Color(0xFFFBF3E8),
    onSurface = Ink,
    surfaceVariant = Color(0xFFEBE1D4),
    onSurfaceVariant = Color(0xFF51493F),
    surfaceTint = Color(0xFF4B3BEB),
    inverseSurface = Ink,
    inverseOnSurface = Cream,
    outline = Color(0xFF82786C),
    outlineVariant = Color(0xFFD5CABB),
    surfaceDim = Color(0xFFE2D9CC),
    surfaceBright = Color(0xFFFFFDF9),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFDF7EE),
    surfaceContainer = Color(0xFFFBF3E8),
    surfaceContainerHigh = Color(0xFFF2E9DC),
    surfaceContainerHighest = Color(0xFFEBE1D4),
)

fun rondoColorScheme(dark: Boolean): ColorScheme = if (dark) RondoDark else RondoLight
