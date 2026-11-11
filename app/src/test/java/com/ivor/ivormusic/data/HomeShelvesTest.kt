package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeShelvesTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    private fun play(id: String, daysAgo: Int, source: SongSource = SongSource.YOUTUBE) = PlayHistoryEntry(
        songId = id,
        title = id,
        artist = "artist",
        album = "album",
        timestamp = now - daysAgo * day,
        duration = 200_000,
        source = source,
    )

    private fun episode(id: String, feed: String, pubDate: Long) =
        PodcastEpisode(id = id, feedId = feed, title = id, audioUrl = "https://example.invalid/$id.mp3", pubDate = pubDate)

    @Test
    fun aSongNeedsThreePlaysInsideTheWindow() {
        val history = listOf(
            play("thrice", 1), play("thrice", 2), play("thrice", 3),
            play("twice", 1), play("twice", 2),
            // Three plays, but two of them before the window opened.
            play("old", 1), play("old", 40), play("old", 41),
        )
        assertEquals(listOf("thrice"), onRepeat(history, now).map { it.songId })
    }

    @Test
    fun mostPlayedComesFirstAndTiesGoToTheMoreRecent() {
        val history = listOf(
            play("three-old", 10), play("three-old", 11), play("three-old", 12),
            play("three-new", 1), play("three-new", 5), play("three-new", 6),
            play("five", 20), play("five", 21), play("five", 22), play("five", 23), play("five", 24),
        )
        assertEquals(listOf("five", "three-new", "three-old"), onRepeat(history, now).map { it.songId })
    }

    @Test
    fun eachSongIsItsNewestRowWhateverOrderTheHistoryIsIn() {
        val history = listOf(play("a", 9), play("a", 2), play("a", 5))
        assertEquals(now - 2 * day, onRepeat(history, now).single().timestamp)
    }

    @Test
    fun podcastsAreNeverOnRepeat() {
        val history = List(4) { play("podcast:ep", it + 1, SongSource.PODCAST) }
        assertTrue(onRepeat(history, now).isEmpty())
    }

    @Test
    fun theShelfStopsAtItsLimit() {
        val history = (1..20).flatMap { song -> List(3) { play("s$song", it + 1) } }
        assertEquals(12, onRepeat(history, now).size)
    }

    @Test
    fun episodesComeNewestFirstAcrossShows() {
        val byFeed = mapOf(
            "weekly" to listOf(episode("w2", "weekly", 20), episode("w1", "weekly", 10)),
            "monthly" to listOf(episode("m1", "monthly", 15)),
        )
        assertEquals(listOf("w2", "m1", "w1"), newestEpisodes(byFeed).map { it.id })
    }

    @Test
    fun oneShowCannotFillTheShelf() {
        val byFeed = mapOf(
            "daily" to (1..10).map { episode("d$it", "daily", 100L + it) },
            "weekly" to listOf(episode("w1", "weekly", 50)),
        )
        assertEquals(listOf("d10", "d9", "d8", "w1"), newestEpisodes(byFeed).map { it.id })
    }

    @Test
    fun anUndatedEpisodeSortsAfterEveryDatedOne() {
        val byFeed = mapOf(
            "dated" to listOf(episode("d1", "dated", 5)),
            "undated" to listOf(episode("u1", "undated", 0)),
        )
        assertEquals(listOf("d1", "u1"), newestEpisodes(byFeed).map { it.id })
    }

    @Test
    fun noShowsIsAnEmptyShelf() {
        assertTrue(newestEpisodes(emptyMap()).isEmpty())
    }
}
