package com.ivor.ivormusic.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.glance.color.ColorProviders
import androidx.glance.material3.ColorProviders
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.ui.theme.ThemeMode
import com.ivor.ivormusic.ui.theme.buildSeedColorScheme
import com.ivor.ivormusic.ui.theme.kodaColorScheme

/**
 * The widget family draws in Koda's palette, not in raw system dynamic color.
 *
 * Glance defaults to the wallpaper scheme, which is right for a widget that
 * belongs to no particular app - and wrong here, because someone who picked a
 * palette in Settings sees their app in one set of colors and its widgets in
 * another. [kodaColorScheme] is the same resolver `IvorMusicTheme` uses, so
 * palette, AMOLED and the dynamic default all land identically on both sides.
 *
 * Light and dark are both built and handed to Glance together: the widget host
 * picks between them per its own uiMode, which is what lets one widget be right
 * in a light launcher and a dark notification shade at the same time. When the
 * app is pinned to light or dark rather than following the system, both slots
 * get the same scheme so the widget stays pinned too.
 */
internal object KodaWidgetTheme {

    fun colors(context: Context): ColorProviders {
        val prefs = ThemePreferences(context)
        val palette = prefs.colorPalette.value
        val amoled = prefs.amoledTheme.value
        val style = prefs.paletteStyle.value
        return when (prefs.themeMode.value) {
            ThemeMode.LIGHT -> {
                val scheme = kodaColorScheme(context, false, palette, amoled, style)
                ColorProviders(light = scheme, dark = scheme)
            }
            ThemeMode.DARK -> {
                val scheme = kodaColorScheme(context, true, palette, amoled, style)
                ColorProviders(light = scheme, dark = scheme)
            }
            ThemeMode.SYSTEM -> ColorProviders(
                light = kodaColorScheme(context, false, palette, amoled, style),
                dark = kodaColorScheme(context, true, palette, amoled, style),
            )
        }
    }

    /**
     * The primary the app UI is actually wearing right now, as an ARGB int
     * - for surfaces that need a raw color, like the vinyl widget's baked
     * button bitmaps. Glance's own [GlanceTheme.colors] roles are
     * ColorProviders, not raw colors, so they cannot feed a bitmap.
     *
     * That is NOT always the chosen palette: with album colours set to
     * whole-app, MainActivity builds the app scheme from the playing
     * cover (buildSeedColorScheme) instead of the palette, so resolving
     * the palette here painted the buttons navy while the app wore the
     * cover's olive. Mirror MainActivity's choice exactly: the cover seed
     * when whole-app album colours are on and the snapshot carries a cover
     * colour, the chosen palette otherwise. AMOLED and tinted pages never
     * touch primary, so they need no handling here.
     */
    fun vinylButtonArgb(context: Context, artworkColor: Int?): Int {
        val prefs = ThemePreferences(context)
        val dark = isDark(context)
        val style = prefs.paletteStyle.value
        if (prefs.playerArtworkColors.value && prefs.artworkColorsWholeApp.value && artworkColor != null) {
            return buildSeedColorScheme(Color(artworkColor), dark, style).primary.toArgb()
        }
        return kodaColorScheme(
            context,
            dark,
            prefs.colorPalette.value,
            prefs.amoledTheme.value,
            style,
        ).primary.toArgb()
    }

    /** Whether the host is currently drawing dark, for artwork scrim choices. */
    fun isDark(context: Context): Boolean {
        val prefs = ThemePreferences(context)
        return when (prefs.themeMode.value) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM ->
                (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
        }
    }
}
