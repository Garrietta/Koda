package com.ivor.ivormusic.widget

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decisions behind widget revival after the app process dies
 * (see [WidgetRevivePolicy]): the store round-trip, the fresh-process seed
 * resolution, and the artwork disk-cache key.
 *
 * The Android-bound parts are not reachable from a JVM test: a blob carrying
 * a real artwork URI decodes through android.net.Uri (a stub here), and the
 * disk cache itself needs filesDir and Bitmap. Those are covered by CI
 * compile and by the device checks, not here.
 */
class WidgetReviveTest {

    private fun fullSnapshot() = PlayerWidgetSnapshot(
        hasMedia = true,
        isPlaying = true,
        title = "Vote Him Out",
        artist = "CumTown",
        artworkUri = null,
        positionMs = 52_000L,
        durationMs = 4_468_000L,
        hasNext = true,
        hasPrevious = false,
        shuffleEnabled = false,
        repeatMode = Player.REPEAT_MODE_OFF,
        upNext = listOf(
            UpNextEntry(index = 1, title = "Next One", artist = "CumTown"),
            UpNextEntry(index = 2, title = "After That", artist = null),
        ),
        mediaId = "episode-227",
        album = "CumTown 2020",
        artworkColor = 0xFF123456.toInt(),
        artworkLabelColor = 0xFFABCDEF.toInt(),
    )

    @Test
    fun `store round-trips a full snapshot`() {
        val original = fullSnapshot()
        val decoded = snapshotFromJson(original.toJson().toString())
        assertEquals(original, decoded)
    }

    @Test
    fun `store round-trips an empty snapshot`() {
        val original = PlayerWidgetSnapshot.empty()
        val decoded = snapshotFromJson(original.toJson().toString())
        assertEquals(original, decoded)
    }

    @Test
    fun `store rejects malformed blobs`() {
        assertNull(snapshotFromJson("not json"))
        assertNull(snapshotFromJson(""))
    }

    @Test
    fun `stored playing with an unreachable session corrects to paused`() {
        val stored = fullSnapshot()
        val resolved = WidgetRevivePolicy.resolveSeedSnapshot(stored, null)
        assertEquals(stored.copy(isPlaying = false), resolved)
    }

    @Test
    fun `a live session with media wins over the stored frame`() {
        val stored = fullSnapshot()
        val live = stored.copy(title = "Newer Track", positionMs = 1_000L)
        val resolved = WidgetRevivePolicy.resolveSeedSnapshot(stored, live)
        assertEquals(live, resolved)
    }

    @Test
    fun `stored playing with a live but idle session corrects to paused`() {
        val stored = fullSnapshot()
        val resolved =
            WidgetRevivePolicy.resolveSeedSnapshot(stored, PlayerWidgetSnapshot.empty())
        assertEquals(stored.copy(isPlaying = false), resolved)
    }

    @Test
    fun `stored paused with an unreachable session stays unchanged`() {
        val stored = fullSnapshot().copy(isPlaying = false)
        val resolved = WidgetRevivePolicy.resolveSeedSnapshot(stored, null)
        assertEquals(stored, resolved)
    }

    @Test
    fun `artwork cache key is stable hex and differs per URI`() {
        val a = "https://example.test/cover-a.jpg"
        val b = "https://example.test/cover-b.jpg"
        val keyA = WidgetRevivePolicy.artworkCacheKey(a)
        assertEquals(keyA, WidgetRevivePolicy.artworkCacheKey(a))
        assertTrue(keyA.matches(Regex("[0-9a-f]{64}")))
        assertTrue(keyA != WidgetRevivePolicy.artworkCacheKey(b))
    }
}
