package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DjMoodTest {

    @Test
    fun aSunnyMixIsYellowOrange() {
        assertEquals(65.0, djMoodHue("A Sunny Day with Kendrick Lamar")!!, 0.0)
    }

    @Test
    fun theFirstMoodWordWins() {
        // "Midnight" comes before "rain", so the mix is night, not water.
        assertEquals(285.0, djMoodHue("Midnight Rain")!!, 0.0)
        assertEquals(250.0, djMoodHue("Rain at Midnight")!!, 0.0)
    }

    @Test
    fun spellingsOfTheSameWordReadAlike() {
        assertEquals(190.0, djMoodHue("Lo-Fi Study Session")!!, 0.0)
        assertEquals(190.0, djMoodHue("lo fi beats")!!, 0.0)
        assertEquals(305.0, djMoodHue("R&B Slow Jams")!!, 0.0)
        assertEquals(50.0, djMoodHue("Golden Hour")!!, 0.0)
    }

    @Test
    fun wordsInsideOtherWordsDoNotCount() {
        // "Sunday" is not "sun", "Season" is not "sea".
        assertNull(djMoodHue("Sunday Season Picks"))
    }

    @Test
    fun aTitleWithNoMoodHasNoHue() {
        assertNull(djMoodHue("Your Weekly Mix"))
        assertNull(djMoodHue(""))
        assertNull(djMoodHue(null))
    }
}
