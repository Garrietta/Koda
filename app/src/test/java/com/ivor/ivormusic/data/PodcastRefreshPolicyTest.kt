package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The refresh budget behind [PodcastRefreshPolicy]: the app-foreground,
 * tab-visible, and background triggers share one debounce, so the New
 * episodes rail updates without refetching on every navigation. The
 * repository and worker are Android-bound and not reachable from a JVM
 * test; these are the decisions they depend on.
 */
class PodcastRefreshPolicyTest {

    private val now = 1_700_000_000_000L
    private val debounce = PodcastRefreshPolicy.FOREGROUND_DEBOUNCE_MS

    @Test
    fun `a never-refreshed library refreshes`() {
        assertTrue(PodcastRefreshPolicy.shouldRefresh(0L, now))
    }

    @Test
    fun `a just-refreshed library skips`() {
        assertFalse(PodcastRefreshPolicy.shouldRefresh(now, now))
    }

    @Test
    fun `exactly one debounce old refreshes`() {
        assertTrue(PodcastRefreshPolicy.shouldRefresh(now - debounce, now))
    }

    @Test
    fun `one millisecond under the debounce skips`() {
        assertFalse(PodcastRefreshPolicy.shouldRefresh(now - debounce + 1L, now))
    }

    @Test
    fun `force refreshes even when just refreshed`() {
        assertTrue(PodcastRefreshPolicy.shouldRefresh(now, now, force = true))
    }

    @Test
    fun `a clock moved backwards skips until the debounce elapses`() {
        assertFalse(PodcastRefreshPolicy.shouldRefresh(now + debounce, now))
    }

    @Test
    fun `the foreground debounce is one hour`() {
        assertEquals(3_600_000L, debounce)
    }

    @Test
    fun `the background interval stays inside the four to six hour range`() {
        val interval = PodcastRefreshPolicy.BACKGROUND_INTERVAL_HOURS
        assertTrue(interval in 4L..6L)
    }
}
