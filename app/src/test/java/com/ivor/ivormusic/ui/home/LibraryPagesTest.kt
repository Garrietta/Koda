package com.ivor.ivormusic.ui.home

import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SongSource
import com.ivor.ivormusic.data.TasteArtist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryPagesTest {

    private fun song(id: String, artist: String, album: String, albumId: String? = null, source: SongSource = SongSource.YOUTUBE) =
        Song(id, "Title $id", artist, album, 1000, source = source, albumId = albumId)

    @Test
    fun followedArtistsComeFirstThenTheMostPlayed() {
        val songs = listOf(
            song("1", "Quiet", "A"),
            song("2", "Loud", "B"),
            song("3", "Loud", "B"),
            song("4", "Followed", "C"),
        )
        val artists = libraryArtists(
            songs,
            followed = listOf(TasteArtist("UCabc", "Followed")),
            photos = emptyMap(),
            playCounts = mapOf("2" to 5, "1" to 1),
        )
        assertEquals(listOf("Followed", "Loud", "Quiet"), artists.map { it.name })
        assertEquals("UCabc", artists.first().id)
        assertEquals(2, artists[1].songCount)
    }

    @Test
    fun albumsAreMostPlayedFirst() {
        val songs = listOf(song("1", "X", "Rare"), song("2", "Y", "Loved"), song("3", "Y", "Loved"))
        val albums = libraryAlbums(songs, playCounts = mapOf("2" to 3))
        assertEquals(listOf("Loved", "Rare"), albums.map { it.name })
        assertEquals(2, albums.first().songs.size)
    }

    @Test
    fun anAlbumWithAReleaseOpensTheWholeRelease() {
        val album = libraryAlbums(listOf(song("1", "X", "Deep Thoughts", albumId = "MPREb_123")), emptyMap()).single()
        val page = album.page()
        assertTrue(page is HomePage.Playlist)
        assertEquals("MPREb_123", (page as HomePage.Playlist).item.id)
    }

    @Test
    fun anAlbumOfFilesOpensTheFiles() {
        val album = libraryAlbums(
            listOf(song("1", "X", "Home Tapes", albumId = "MPREb_9", source = SongSource.LOCAL)),
            emptyMap(),
        ).single()
        assertEquals(HomePage.DeviceAlbum("Home Tapes"), album.page())
    }

    @Test
    fun releasesReadNewestFirstWithUndatedLast() {
        val releases = listOf(
            PlaylistDisplayItem("Old", "MPREb_1", "A", releaseYear = 2015),
            PlaylistDisplayItem("Undated", "MPREb_2", "A"),
            PlaylistDisplayItem("New", "MPREb_3", "A", releaseYear = 2025),
            PlaylistDisplayItem("Also new", "MPREb_4", "A", releaseYear = 2025),
        )
        assertEquals(
            listOf("New", "Also new", "Old", "Undated"),
            com.ivor.ivormusic.ui.artist.newestFirst(releases).map { it.name },
        )
    }
}
