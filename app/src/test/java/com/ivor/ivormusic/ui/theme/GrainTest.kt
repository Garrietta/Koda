package com.ivor.ivormusic.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrainTest {

    @Test
    fun theRecordsLabelIsSolidAndItsRimFadesOut() {
        assertTrue(recordInk(0.1f) > 0.8f)
        assertEquals(0f, recordInk(1.01f), 0f)
        // The grooves thin toward the rim: the mean ink over the outer ring is less than over the inner.
        fun mean(from: Float, to: Float) = (0 until 100).map { recordInk(from + (to - from) * it / 100f) }.average()
        assertTrue(mean(0.3f, 0.5f) > mean(0.8f, 1f))
    }

    @Test
    fun aVividColorHasANeighbourAndGreyHasNone() {
        assertNotNull(Color(0xFF5B4BFF).vividNeighbor())
        assertNull(Color(0xFF808080).vividNeighbor())
    }

    @Test
    fun boldIsCutLighterThanItsName() {
        assertEquals(400, cut(400))
        assertEquals(610, cut(700))
        assertTrue(cut(900) <= 650)
    }
}
