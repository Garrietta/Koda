package com.ivor.ivormusic.ui.home

import com.ivor.ivormusic.data.MusicShelf
import com.ivor.ivormusic.data.MusicShelfItem
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockRevealTest {

    @Test
    fun aPullShrinksThePageTowardsItsTile() {
        assertEquals(1f, pulledProgress(0f, 1000f), 0.001f)
        assertEquals(0.5f, pulledProgress(550f, 1000f), 0.001f)
        assertEquals(0f, pulledProgress(5000f, 1000f), 0.001f)
        // Before the page has a size, a pull does nothing.
        assertEquals(1f, pulledProgress(300f, 0f), 0.001f)
    }

    @Test
    fun aLongPullOrAFlickCloses() {
        assertFalse(pullCloses(100f, 1000f, 0f))
        assertTrue(pullCloses(300f, 1000f, 0f))
        assertTrue(pullCloses(40f, 1000f, 2400f))
        // A flick with nothing pulled is a scroll, not a close.
        assertFalse(pullCloses(0f, 1000f, 5000f))
    }

    private fun album(id: String) = MusicShelfItem.Collection(
        PlaylistDisplayItem(name = id, url = id, uploaderName = "", thumbnailUrl = "https://art/$id"), isAlbum = true,
    )

    private fun playlist(id: String) = MusicShelfItem.Collection(
        PlaylistDisplayItem(name = id, url = id, uploaderName = "", thumbnailUrl = "https://art/$id"), isAlbum = false,
    )

    @Test
    fun aShelfPageHeroLeadsWithItsRecords() {
        val track = MusicShelfItem.Track(Song.fromYouTube("t", "Track", "Artist", "Album", 1000L, "https://art/t"))
        val shelves = listOf(
            MusicShelf("Music videos", listOf(track, playlist("p"))),
            MusicShelf("Albums & singles", listOf(album("a1"), album("a2"), album("a1"))),
        )
        val covers = heroCoversFromShelves(shelves, 10).map { it.art }
        assertEquals("https://art/a1", covers[0])
        assertEquals("https://art/a2", covers[1])
        // Each picture once, and no more than asked for.
        assertEquals(covers.distinct(), covers)
        assertEquals(2, heroCoversFromShelves(shelves, 2).size)
    }
}
