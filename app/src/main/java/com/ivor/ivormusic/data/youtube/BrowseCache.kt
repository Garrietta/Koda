package com.ivor.ivormusic.data.youtube

import java.io.File
import java.security.MessageDigest

/**
 * A disk cache of YouTube Music browse responses, so an artist, an album
 * or a playlist opened before opens at once, and opens at all offline.
 *
 * It sits where the HTTP cache would: [MusicApi.browseMusic] answers from
 * here while an entry is fresh, stores what the network returns, and falls
 * back to any entry, however old, when the network gives nothing. How long
 * an entry is fresh depends on what it is ([ttlFor]): an album never
 * changes, an artist's page slowly, a playlist can be edited; account
 * feeds and Your Likes are never kept. Any account write forgets the
 * playlists, so an edit is never shown its own old copy.
 *
 * Nothing is kept until [init] has been given a directory, so code run
 * without the application (tests) is untouched.
 */
internal object BrowseCache {
    private const val HOUR = 60 * 60 * 1000L
    private const val MEMORY_ENTRIES = 48

    @Volatile
    private var dir: File? = null
    private val memory = object : LinkedHashMap<String, Entry>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean = size > MEMORY_ENTRIES
    }

    private class Entry(val body: String, val storedAt: Long)

    fun init(cacheDir: File) {
        dir = File(cacheDir, "browse").apply { mkdirs() }
    }

    /** How long a response for [browseId] stays fresh, or null when it is never kept. */
    fun ttlFor(browseId: String): Long? = when {
        browseId == "LM" || browseId == "VLLM" -> null
        browseId.startsWith("MPRE") -> 7 * 24 * HOUR
        browseId.startsWith("UC") -> 24 * HOUR
        browseId.startsWith("VL") || browseId.startsWith("PL") || browseId.startsWith("OLAK") || browseId.startsWith("RD") -> HOUR / 4
        else -> null
    }

    /** The file name a response is kept under: its kind, so playlists can be found again, then a hash. */
    fun key(browseId: String, params: String?): String {
        val digest = MessageDigest.getInstance("SHA-1").digest("$browseId|${params.orEmpty()}".toByteArray())
        val kind = when {
            browseId.startsWith("MPRE") -> "album"
            browseId.startsWith("UC") -> "artist"
            else -> PLAYLIST
        }
        return kind + "_" + digest.joinToString("") { "%02x".format(it) }
    }

    private const val PLAYLIST = "playlist"

    /** The stored response under [key] no older than [maxAgeMs], or of any age when it is null. */
    fun get(key: String, maxAgeMs: Long?, now: Long = System.currentTimeMillis()): String? {
        val entry = synchronized(memory) { memory[key] } ?: readDisk(key)?.also { synchronized(memory) { memory[key] = it } }
        entry ?: return null
        return entry.body.takeIf { maxAgeMs == null || now - entry.storedAt <= maxAgeMs }
    }

    fun put(key: String, body: String, now: Long = System.currentTimeMillis()) {
        val entry = Entry(body, now)
        synchronized(memory) { memory[key] = entry }
        val folder = dir ?: return
        runCatching {
            val tmp = File(folder, "$key.tmp")
            tmp.writeText("${entry.storedAt}\n$body")
            tmp.renameTo(File(folder, "$key.json"))
        }
    }

    /** Forgets every playlist response, after the account has changed one; albums and artists stay. */
    fun forgetPlaylists() {
        synchronized(memory) { memory.keys.removeAll { it.startsWith(PLAYLIST) } }
        dir?.listFiles { file -> file.name.startsWith(PLAYLIST) }?.forEach { it.delete() }
    }

    private fun readDisk(key: String): Entry? {
        val file = File(dir ?: return null, "$key.json")
        if (!file.exists()) return null
        return runCatching {
            val text = file.readText()
            val split = text.indexOf('\n')
            Entry(text.substring(split + 1), text.substring(0, split).toLong())
        }.getOrNull()
    }
}
