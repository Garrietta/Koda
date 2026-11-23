package com.ivor.ivormusic.ui.home

import com.ivor.ivormusic.data.MusicQueueItem
import com.ivor.ivormusic.data.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingPanelTest {

    private fun item(n: Int) = MusicQueueItem(id = "q$n", song = Song.fromYouTube("s$n", "Song $n", "Artist", "Album", 1L, null))

    private val queue = List(5, ::item)

    @Test
    fun upNextIsWhatComesAfterTheOnePlaying() {
        assertEquals(listOf("q3", "q4"), upNextOf(queue, "q2").map { it.id })
    }

    @Test
    fun theLastSongHasNothingUpNext() {
        assertEquals(emptyList<MusicQueueItem>(), upNextOf(queue, "q4"))
    }

    @Test
    fun anUnknownCurrentShowsTheWholeQueue() {
        assertEquals(5, upNextOf(queue, null).size)
        assertEquals(5, upNextOf(queue, "gone").size)
    }
}
