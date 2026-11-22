package com.ivor.ivormusic.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockStepsTest {

    @Test
    fun theTwoBlocksInViewAreDrawnWhole() {
        for (position in listOf(0f, 0.5f, 1f)) {
            assertEquals(StepLook(1f, 1f, 0f), stepLook(position))
        }
    }

    @Test
    fun aBlockFallsBackWhereItStandsAndFades() {
        val half = stepLook(-0.5f)
        assertTrue(half.scale < 1f && half.alpha < 1f)
        // Held back by exactly the travel it is given, so it does not move up.
        assertEquals(0.5f, half.hold, 1e-6f)
        assertEquals(0f, stepLook(-1f).alpha, 1e-6f)
    }

    @Test
    fun theNextBlockOpensIntoTheBottomSlot() {
        val half = stepLook(1.5f)
        assertTrue(half.scale < 1f && half.alpha < 1f && half.hold < 0f)
        assertEquals(0f, stepLook(2f).alpha, 1e-6f)
    }

    @Test
    fun theRisingBlockIsDrawnOverTheOthers() {
        assertTrue(stepLayer(0.4f) > stepLayer(1.6f))
        assertTrue(stepLayer(1.6f) > stepLayer(-0.4f))
    }

    @Test
    fun positionIsCountedFromTheTopSlot() {
        assertEquals(1f, stepPosition(page = 3, currentPage = 2, offsetFraction = 0f), 0f)
        assertEquals(-0.25f, stepPosition(page = 2, currentPage = 2, offsetFraction = 0.25f), 1e-6f)
    }
}
