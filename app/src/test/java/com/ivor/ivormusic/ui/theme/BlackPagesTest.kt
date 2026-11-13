package com.ivor.ivormusic.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlackPagesTest {

    private val orange = Color(0xFFFF7A45)
    private val iris = Color(0xFF5B4BFF)
    private val nearBlackInk = Color(0xFF1A0F0A)
    private val cream = Color(0xFFFFF1E0)

    @Test
    fun aBrightTileKeepsItsColorAsTheInk() {
        // For you: black page, orange words and buttons, exactly the tile's orange.
        assertEquals(orange, blackPageInk(orange, nearBlackInk))
    }

    @Test
    fun aDarkTileIsLiftedOnlyUntilItReads() {
        val ink = blackPageInk(iris, cream)
        assertTrue(ink.luminance() >= BLACK_PAGE_INK_LUMINANCE - 0.005f)
        assertTrue("lifted no further than it must", ink.luminance() < BLACK_PAGE_INK_LUMINANCE + 0.02f)
        // Still blue-violet, not the cream ink.
        assertTrue(ink.blue > ink.red && ink.blue > ink.green)
    }

    @Test
    fun aNearBlackTileWritesInItsInk() {
        val ground = Color(0xFF0E0D12)
        val light = Color(0xFFEDE6F0)
        assertEquals(light, blackPageInk(ground, light))
    }

    @Test
    fun theInkReadsOnBlack() {
        for (tile in listOf(orange, iris, Color(0xFFC77DF2), Color(0xFFB00020), Color(0xFF00579E))) {
            assertTrue(contrastRatio(blackPageInk(tile, nearBlackInk), PageBlack) >= 7f - 0.1f)
        }
    }

    @Test
    fun readableOnKeepsAnInkThatReads() {
        val lightBlue = Color(0xFFA8C8F8)
        val darkInk = Color(0xFF101828)
        assertEquals(darkInk, readableOn(lightBlue, darkInk, Color(0xFFE6E1E5)))
    }

    @Test
    fun readableOnSwapsAnInkLostOnADarkenedGround() {
        // The Hero player in dark mode: a sky-blue container taken halfway
        // to the surface, where the container's dark ink no longer shows.
        val darkenedBlue = Color(0xFF2A4C7C)
        val darkInk = Color(0xFF101828)
        val lightText = Color(0xFFE6E1E5)
        assertEquals(lightText, readableOn(darkenedBlue, darkInk, lightText))
    }
}
