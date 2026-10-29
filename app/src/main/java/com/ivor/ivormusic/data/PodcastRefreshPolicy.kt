package com.ivor.ivormusic.data

/**
 * The shared budget for automatic podcast feed refreshes. The
 * app-foreground, tab-visible, and background triggers all funnel through
 * [PodcastRepository.refreshAllFeeds], which enforces this policy, so the
 * New episodes rail updates without refetching on every navigation.
 *
 * Pure Kotlin with no Android dependency, so the decisions here are covered
 * by JVM unit tests ([PodcastRefreshPolicyTest]).
 */
object PodcastRefreshPolicy {

    /**
     * Foreground and tab-visible refreshes run at most once per hour.
     * Podcast feeds publish a few episodes a day at most, and the rail shows
     * a 7-day window, so sub-hour freshness is invisible in the UI — while
     * refetching on every tab switch would hammer small, often self-hosted
     * feed hosts and burn radio and battery for nothing.
     */
    const val FOREGROUND_DEBOUNCE_MS = 60L * 60 * 1000

    /**
     * Background refresh cadence: 6 hours, matching the upload-check
     * worker's cadence for the same cost class (a handful of small RSS
     * fetches). It bounds rail staleness to about 6 hours when the app is
     * never opened, without waking the device for feeds that rarely change.
     */
    const val BACKGROUND_INTERVAL_HOURS = 6L

    /**
     * Whether a refresh round should run. [force] bypasses the debounce
     * (reserved for an explicit user action); a clock moved backwards
     * simply skips until the debounce elapses, which costs nothing.
     */
    fun shouldRefresh(lastRefreshMs: Long, nowMs: Long, force: Boolean = false): Boolean {
        if (force) return true
        return nowMs - lastRefreshMs >= FOREGROUND_DEBOUNCE_MS
    }
}
