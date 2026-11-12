package com.ivor.ivormusic.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class VividColorsTest {

    private fun contrast(a: Color, b: Color): Float {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }

    private fun saturation(c: Color): Float {
        val max = maxOf(c.red, c.green, c.blue)
        val min = minOf(c.red, c.green, c.blue)
        return if (max == 0f) 0f else (max - min) / max
    }

    @Test
    fun everyHueGivesABlockWithReadableInk() {
        for (hue in 0 until 360 step 10) {
            val block = vividBlock(hue.toDouble())
            val ratio = contrast(block.container, block.onContainer)
            assertTrue("hue $hue: ink contrast $ratio below 4.5", ratio >= 4.5f)
        }
    }

    @Test
    fun everyBlockIsStronglyColored() {
        for (hue in 0 until 360 step 10) {
            val sat = saturation(vividBlock(hue.toDouble()).container)
            assertTrue("hue $hue: saturation $sat is not vivid", sat >= 0.5f)
        }
    }

    @Test
    fun blueTakesCreamAndOrangeTakesInk() {
        val iris = vividBlock(288.3)
        val orange = vividBlock(38.7)
        assertTrue(iris.onContainer.luminance() > iris.container.luminance())
        assertTrue(orange.onContainer.luminance() < orange.container.luminance())
    }

    @Test
    fun aGreyCoverLeavesTheSchemeAlone() {
        val scheme = rondoColorScheme(dark = true)
        assertSame(scheme, scheme.withVividRoles(Color(0xFF808080), null, dark = true))
        assertNull(vividBlockOf(Color(0xFF7F7F80)))
    }

    @Test
    fun aColoredCoverMakesAllThreeContainersVivid() {
        val base = rondoColorScheme(dark = true)
        val red = Color(0xFFD32F2F)
        val scheme = base.withVividRoles(red, null, dark = true)
        assertEquals(vividBlockOf(red)!!.container, scheme.primaryContainer)
        for (c in listOf(scheme.primaryContainer, scheme.secondaryContainer, scheme.tertiaryContainer)) {
            assertTrue(saturation(c) >= 0.5f)
        }
        // Surfaces are the app's own.
        assertEquals(base.background, scheme.background)
        assertEquals(base.surfaceContainer, scheme.surfaceContainer)
    }

    @Test
    fun theTextRolesReadOnThePageInBothThemes() {
        for (dark in listOf(false, true)) {
            val base = rondoColorScheme(dark)
            val scheme = base.withVividRoles(Color(0xFF1E88E5), Color(0xFFFFB300), dark)
            for (role in listOf(scheme.primary, scheme.secondary, scheme.tertiary)) {
                val ratio = contrast(role, base.background)
                assertTrue("dark=$dark text role contrast $ratio below 4.5", ratio >= 4.5f)
            }
        }
    }

    @Test
    fun aDistinctSecondColorBecomesTheTertiary() {
        val base = rondoColorScheme(dark = true)
        val blue = Color(0xFF1E88E5)
        val amber = Color(0xFFFFB300)
        val withAmber = base.withVividRoles(blue, amber, dark = true)
        assertEquals(vividBlockOf(amber)!!.container, withAmber.tertiaryContainer)
        // A second color too close to the first is ignored.
        val withBlue = base.withVividRoles(blue, Color(0xFF1976D2), dark = true)
        assertTrue(withBlue.tertiaryContainer != vividBlockOf(Color(0xFF1976D2))!!.container)
    }
}
