package com.ivor.ivormusic.data

/**
 * The two Home shelves that are worked out from what is already on the
 * device: the songs the listener keeps returning to, and the newest
 * episodes of the shows they follow. Both are pure, so the rules live in
 * one place and are tested without a ViewModel.
 */

/** How far back "On repeat" looks: four weeks. */
const val ON_REPEAT_WINDOW_MS: Long = 28L * 24 * 60 * 60 * 1000

/** Plays inside the window before a song counts as on repeat. */
const val ON_REPEAT_MIN_PLAYS: Int = 3

/**
 * The songs played at least [minPlays] times in the [windowMs] before
 * [now], most played first, each as its newest history row.
 *
 * Ties go to the song played most recently, so the shelf follows this
 * week over last. Podcast episodes are left out: an episode heard in
 * three sittings is not a song on repeat. [history] may be in any order.
 */
fun onRepeat(
    history: List<PlayHistoryEntry>,
    now: Long,
    windowMs: Long = ON_REPEAT_WINDOW_MS,
    minPlays: Int = ON_REPEAT_MIN_PLAYS,
    limit: Int = 12,
): List<PlayHistoryEntry> {
    val since = now - windowMs
    return history
        .asSequence()
        .filter { it.timestamp in since..now && it.source != SongSource.PODCAST }
        .groupBy { it.songId }
        .values
        .filter { it.size >= minPlays }
        .map { plays -> plays.maxBy { it.timestamp } to plays.size }
        .sortedWith(
            compareByDescending<Pair<PlayHistoryEntry, Int>> { it.second }
                .thenByDescending { it.first.timestamp }
        )
        .take(limit)
        .map { it.first }
}

/**
 * The newest episodes across every followed show, newest first.
 *
 * No show contributes more than [perShow], so one daily programme cannot
 * push every weekly one off the shelf. An episode whose feed gave no date
 * sorts after every dated one, in the order its feed listed it.
 */
fun newestEpisodes(
    episodesByFeed: Map<String, List<PodcastEpisode>>,
    limit: Int = 12,
    perShow: Int = 3,
): List<PodcastEpisode> =
    episodesByFeed.values
        .flatMap { episodes -> episodes.sortedByDescending { it.pubDate }.take(perShow) }
        .distinctBy { it.id }
        .sortedByDescending { it.pubDate }
        .take(limit)
