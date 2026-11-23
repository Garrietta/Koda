package com.ivor.ivormusic.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockStepsTest {

    private val slot = 300f
    private val gap = 10f

    @Test
    fun theTwoBlocksInViewAreWhole() {
        assertEquals(StepWindow(0f, 300f), stepWindow(0f, slot, gap))
        assertEquals(StepWindow(310f, 610f), stepWindow(1f, slot, gap))
        assertEquals(null, stepWindow(-1f, slot, gap))
        assertEquals(null, stepWindow(2f, slot, gap))
    }

    @Test
    fun theTopBlockClosesIntoTheTopEdge() {
        val half = stepWindow(-0.5f, slot, gap)!!
        assertEquals(0f, half.top, 0f)
        // One gap above the block rising into the top slot.
        assertEquals(145f, half.bottom, 1e-3f)
        assertTrue(stepWindow(-0.999f, slot, gap) == null)
    }

    @Test
    fun theNextBlockOpensUpFromTheFoot() {
        val half = stepWindow(1.5f, slot, gap)!!
        assertEquals(610f, half.bottom, 1e-3f)
        assertEquals(465f, half.top, 1e-3f)
    }

    @Test
    fun theWindowsTileTheBoxWithOneGapBetweenThem() {
        var shown = 0f
        while (shown <= 1f) {
            // Blocks 0, 1 and 2 as the steps go from 0 to 1.
            val windows = (0..2).mapNotNull { stepWindow(it - shown, slot, gap) }
            for (k in 0 until windows.size - 1) {
                assertEquals("at $shown", gap, windows[k + 1].top - windows[k].bottom, 1e-3f)
            }
            assertEquals(0f, windows.first().top, 0.5f)
            assertEquals(2 * slot + gap, windows.last().bottom, 1e-3f)
            shown += 0.05f
        }
    }

    @Test
    fun aClosingBlockFadesBackAndAnOpeningOneComesUpFaint() {
        assertEquals(1f, stepFade(0f), 0f)
        assertEquals(1f, stepFade(1f), 0f)
        assertTrue(stepFade(-0.5f) < 1f)
        assertTrue(stepFade(1.9f) < stepFade(1.2f))
    }

    @Test
    fun theFingerNudgesTheBlocksOnlyALittle() {
        assertEquals(0.04f, stepNudge(0.1f), 1e-6f)
        assertEquals(0.06f, stepNudge(2f), 1e-6f)
        assertEquals(-0.06f, stepNudge(-0.5f), 1e-6f)
    }

    @Test
    fun aSwipeTurnsOneStepHoweverLong() {
        assertEquals(0, swipeTurn(0.05f))
        assertEquals(0, swipeTurn(-0.11f))
        assertEquals(1, swipeTurn(0.12f))
        assertEquals(1, swipeTurn(1.8f))
        assertEquals(-1, swipeTurn(-0.3f))
        assertEquals(-1, swipeTurn(-2.5f))
    }

    @Test
    fun aShortSwipeTurnsOnlyIfFlicked() {
        assertEquals(0, flickTurn(1f))
        assertEquals(1, flickTurn(4f))
        assertEquals(-1, flickTurn(-2f))
    }

    @Test
    fun aHeldFingerTurnsTheNextStepAMomentAfterEachLanding() {
        // Not before the swipe has turned its own step.
        assertEquals(false, holdTurns(turned = false, direction = 1, landedMillis = 2_000, heldSteps = 0))
        // Not while the finger is back where it went down.
        assertEquals(false, holdTurns(turned = true, direction = 0, landedMillis = 2_000, heldSteps = 0))
        // A swipe lifted at its natural end is gone before the first held step.
        assertEquals(false, holdTurns(turned = true, direction = 1, landedMillis = 200, heldSteps = 0))
        assertEquals(true, holdTurns(turned = true, direction = 1, landedMillis = 300, heldSteps = 0))
        // Once holding, the steps follow sooner.
        assertEquals(true, holdTurns(turned = true, direction = -1, landedMillis = 130, heldSteps = 2))
        assertEquals(false, holdTurns(turned = true, direction = -1, landedMillis = 60, heldSteps = 2))
    }

    @Test
    fun stepsPlayOneWholeStepAtATime() {
        assertEquals(3f, nextStepToward(2f, 5), 0f)
        assertEquals(3f, nextStepToward(2.4f, 5), 0f)
        assertEquals(2f, nextStepToward(2.4f, 0), 0f)
        assertEquals(1f, nextStepToward(2f, 0), 0f)
        assertEquals(2f, nextStepToward(1.6f, 2), 0f)
        assertEquals(2f, nextStepToward(2f, 2), 0f)
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
