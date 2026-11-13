package com.ivor.ivormusic.data.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BrowseCacheTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Before
    fun setUp() {
        BrowseCache.init(folder.newFolder("cache"))
    }

    @Test
    fun albumsKeepLongerThanArtistsAndLikesAreNeverKept() {
        val album = BrowseCache.ttlFor("MPREb_abc")!!
        val artist = BrowseCache.ttlFor("UCabc")!!
        val playlist = BrowseCache.ttlFor("VLPLabc")!!
        assertTrue(album > artist && artist > playlist)
        assertNull(BrowseCache.ttlFor("VLLM"))
        assertNull(BrowseCache.ttlFor("FEmusic_home"))
    }

    @Test
    fun aFreshCopyIsServedAndAnOldOneOnlyWhenAskedForAnyAge() {
        val key = BrowseCache.key("UCfresh", null)
        BrowseCache.put(key, "{\"a\":1}", now = 1_000)
        assertEquals("{\"a\":1}", BrowseCache.get(key, maxAgeMs = 500, now = 1_400))
        assertNull(BrowseCache.get(key, maxAgeMs = 500, now = 2_000))
        assertEquals("{\"a\":1}", BrowseCache.get(key, maxAgeMs = null, now = 99_000))
    }

    @Test
    fun anAccountWriteForgetsPlaylistsButNotAlbums() {
        val playlist = BrowseCache.key("VLPLxyz", null)
        val album = BrowseCache.key("MPREb_xyz", null)
        BrowseCache.put(playlist, "p")
        BrowseCache.put(album, "a")
        BrowseCache.forgetPlaylists()
        assertNull(BrowseCache.get(playlist, maxAgeMs = null))
        assertEquals("a", BrowseCache.get(album, maxAgeMs = null))
    }
}
