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

    @Test
    fun aSwipeTurnsAStepOnceItIsLongEnough() {
        assertEquals(0, swipeTurn(0.05f))
        assertEquals(0, swipeTurn(-0.11f))
        assertEquals(1, swipeTurn(0.12f))
        assertEquals(-1, swipeTurn(-0.3f))
    }

    @Test
    fun aShortSwipeTurnsOnlyIfFlicked() {
        assertEquals(0, flickTurn(1f))
        assertEquals(1, flickTurn(4f))
        assertEquals(-1, flickTurn(-2f))
    }

    @Test
    fun aTurnIsOneStepWithinTheSteps() {
        assertEquals(3, stepTarget(from = 2, direction = 1, last = 4))
        assertEquals(1, stepTarget(from = 2, direction = -1, last = 4))
        assertEquals(2, stepTarget(from = 2, direction = 0, last = 4))
        assertEquals(3, stepTarget(from = 2, direction = 5, last = 4))
        assertEquals(4, stepTarget(from = 4, direction = 1, last = 4))
        assertEquals(0, stepTarget(from = 0, direction = -1, last = 4))
    }

    @Test
    fun stepToStaysWithinTheSteps() {
        val steps = HomeSteps().apply { count = 6 }
        steps.stepTo(9)
        assertEquals(4, steps.target)
        steps.stepTo(-2)
        assertEquals(0, steps.target)
    }

    @Test
    fun theBarTurnsIntoTheLastBlockOnTheLastStep() {
        val steps = HomeSteps().apply { count = 6; endsWithBar = true }
        assertEquals(4, steps.lastStep)
        kotlinx.coroutines.runBlocking { steps.position.snapTo(3f) }
        assertEquals(0f, steps.morph, 1e-6f)
        kotlinx.coroutines.runBlocking { steps.position.snapTo(3.5f) }
        assertEquals(0.5f, steps.morph, 1e-6f)
        kotlinx.coroutines.runBlocking { steps.position.snapTo(4f) }
        assertEquals(1f, steps.morph, 1e-6f)
        steps.endsWithBar = false
        assertEquals(0f, steps.morph, 1e-6f)
    }
}
