package com.ivor.ivormusic.data

import android.content.Context
import android.content.SharedPreferences
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * One track of an AI DJ mix: the library song plus the DJ's intro line.
 */
@kotlinx.serialization.Serializable
data class AiDjTrack(
    val song: Song,
    val djLine: String?,
)

/**
 * A new-song discovery: not in the listener's library, to be resolved via
 * YouTube search into a playable [Song].
 */
data class AiDjDiscovery(
    val title: String,
    val artist: String,
    val djLine: String?,
)

/**
 * A generated mix: a title and its ordered tracks.
 */
data class AiDjMix(
    val title: String,
    val tracks: List<AiDjTrack>,
)

/** Token consumption, accumulated across generations. */
data class AiDjTokenUsage(
    val promptTokens: Long,
    val outputTokens: Long,
)

/** A subgenre the listener seems to love, with similar artists to explore. */
data class SubgenreSuggestion(
    val subgenre: String,
    /** Artists the listener already likes, grounding this subgenre. */
    val basedOn: List<String>,
    /** Similar artists to discover, most relevant first. */
    val artists: List<String>,
)

/**
 * The user never sees or hears a session number: Gemini used to echo the
 * "Session #NNNN" seed from the generation prompts into mix titles
 * ("Session 21761: ...") and spoken lines ("Kicking off session 21761 ...").
 * These strip that phrasing wherever a title or line surfaces — at parse
 * time, at cache load (so old caches stop showing it), and at playback.
 */
internal fun stripSessionNumberFromTitle(title: String): String {
    val stripped = title
        .replace(Regex("""^\s*session\s*#?\d+\s*[:\-–—]\s*""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""^\s*session\s*#?\d+\s+""", RegexOption.IGNORE_CASE), "")
        .trim()
    return stripped.ifBlank { "AI DJ Mix" }
}

private val SESSION_NUMBER_REGEX = Regex("""\bsession\s*#?\d+\b""", RegexOption.IGNORE_CASE)

/** Pure detection for the phrasing the strippers remove — no whitespace normalization, so a clean line never matches. */
internal fun containsSessionNumber(text: String): Boolean = SESSION_NUMBER_REGEX.containsMatchIn(text)

/**
 * A pre-fix DJ intro item: its fields carry a session number, and its cached
 * audio was synthesized from the same raw line, so it speaks the number too.
 * Relabeling alone is not enough — such items must be dropped, not kept.
 */
internal fun isStaleDjIntro(song: Song): Boolean =
    song.id.startsWith(StatsRepository.DJ_INTRO_ID_PREFIX) &&
        (containsSessionNumber(song.artist) || containsSessionNumber(song.title) || containsSessionNumber(song.album))

internal fun stripSessionNumberFromLine(line: String): String? {
    val cleaned = line
        .replace(SESSION_NUMBER_REGEX, "")
        .replace(Regex("""\s+([,.!?;:])"""), "$1")
        .replace(Regex("""\s+"""), " ")
        .replace(Regex("""^[,;:]\s*"""), "")
        .trim()
    return cleaned.takeIf { it.isNotBlank() }
}

internal fun sanitizeAiDjMix(mix: AiDjMix): AiDjMix = AiDjMix(
    title = stripSessionNumberFromTitle(mix.title),
    tracks = mix.tracks.map { it.copy(djLine = it.djLine?.let(::stripSessionNumberFromLine)) },
)

/**
 * Drop lines that name an artist from elsewhere in the mix. A line is
 * spoken before — and shown under — its own track, so a line naming a
 * different mix artist is misattributed by construction (the pre-fix page
 * generator paired lines with the wrong songs outright; the model can also
 * still name-drop). The previous track's artist is allowed: a transition
 * may name where the set just came from. Artists outside the mix are left
 * alone — the guard only fires on names provably from this mix — and
 * matching is whole-word, so "Mac" never fires on "Machine".
 */
internal fun dropMisattributedLines(tracks: List<AiDjTrack>): List<AiDjTrack> {
    if (tracks.size < 2) return tracks
    fun artistParts(artist: String): List<String> =
        artist.split(',', '&', ';')
            .flatMap { it.split(" feat. ", " ft. ", " Feat. ", " Ft. ") }
            .map { it.trim() }
            .filter { it.length >= 4 }
    fun mentions(line: String, part: String): Boolean =
        Regex("""\b${Regex.escape(part)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(line)
    val mixParts = tracks.map { artistParts(it.song.artist) }
    return tracks.mapIndexed { i, track ->
        val line = track.djLine ?: return@mapIndexed track
        val own = artistParts(track.song.artist)
        val prev = if (i > 0) artistParts(tracks[i - 1].song.artist) else emptyList()
        val misattributed = mixParts.withIndex().any { (j, parts) ->
            j != i && parts.any { part ->
                mentions(line, part) &&
                    own.none { mentions(it, part) || it.contains(part, ignoreCase = true) } &&
                    prev.none { mentions(it, part) || it.contains(part, ignoreCase = true) }
            }
        }
        if (misattributed) track.copy(djLine = null) else track
    }
}

/**
 * The AI DJ: crafts a playlist from the user's library via the Google AI
 * (Gemini) API, guided by a custom prompt the user writes in Settings.
 *
 * The model picks tracks by their 1-based index into the numbered library
 * list it is given, so every pick maps back to a real, playable [Song] —
 * no fuzzy title matching, no hallucinations.
 *
 * The API key lives in SharedPreferences (same as the Last.fm credentials);
 * it is never logged and never leaves the device except in the HTTPS call
 * to Google's API.
 */
class AiDjRepository(context: Context) {

    val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    private val youTubeRepository by lazy { YouTubeRepository(appContext) }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val _hasApiKey = MutableStateFlow(prefs.getString(KEY_API_KEY, null)?.isNotBlank() == true)
    val hasApiKey: StateFlow<Boolean> = _hasApiKey.asStateFlow()

    private val _prompt = MutableStateFlow(prefs.getString(KEY_PROMPT, null) ?: DEFAULT_PROMPT)
    val prompt: StateFlow<String> = _prompt.asStateFlow()

    private val _trackCount = MutableStateFlow(prefs.getInt(KEY_TRACK_COUNT, DEFAULT_TRACK_COUNT))
    val trackCount: StateFlow<Int> = _trackCount.asStateFlow()

    private val _model = MutableStateFlow(prefs.getString(KEY_MODEL, null) ?: DEFAULT_MODEL)
    val model: StateFlow<String> = _model.asStateFlow()

    private val _safeTokenMode = MutableStateFlow(prefs.getBoolean(KEY_SAFE_TOKEN_MODE, true))
    val safeTokenMode: StateFlow<Boolean> = _safeTokenMode.asStateFlow()

    private val _djVoiceEnabled = MutableStateFlow(prefs.getBoolean(KEY_DJ_VOICE, true))
    val djVoiceEnabled: StateFlow<Boolean> = _djVoiceEnabled.asStateFlow()

    private val _autoRefresh = MutableStateFlow(prefs.getBoolean(KEY_AUTO_REFRESH, true))
    val autoRefresh: StateFlow<Boolean> = _autoRefresh.asStateFlow()

    private val _tokenUsage = MutableStateFlow(loadTokenUsage())
    val tokenUsage: StateFlow<AiDjTokenUsage> = _tokenUsage.asStateFlow()

    // --- Cached mix ---
    //
    // The last generated mix is persisted so the DJ screen can reopen on it
    // without regenerating. Tapping the DJ card opens the page; only the
    // emblem (or the page's Regenerate button) builds a fresh one.
    private val _cachedMix = MutableStateFlow(loadCachedMix())
    val cachedMix: StateFlow<AiDjMix?> = _cachedMix.asStateFlow()

    /** Persist [mix] as the cached DJ playlist. */
    fun cacheMix(mix: AiDjMix) {
        // Sanitize on the way in so persisted titles/lines never carry a
        // session number, even if a caller hands us an unsanitized mix.
        val clean = sanitizeAiDjMix(mix)
        _cachedMix.value = clean
        try {
            val json = kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(AiDjTrack.serializer()),
                clean.tracks,
            )
            prefs.edit()
                .putString(KEY_CACHED_MIX_TITLE, clean.title)
                .putString(KEY_CACHED_MIX_TRACKS, json)
                .putLong(KEY_CACHED_MIX_TIME, System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            KLog.w(TAG, "Failed to cache DJ mix", e)
        }
    }

    /**
     * True when there is no cached mix or it is older than [maxAgeMs].
     * Daily refresh = 24h: one generation per day keeps token usage minimal
     * while keeping the playlist fresh.
     */
    fun isCacheStale(maxAgeMs: Long = 24 * 60 * 60 * 1000L): Boolean {
        if (_cachedMix.value == null) return true
        val cachedAt = prefs.getLong(KEY_CACHED_MIX_TIME, 0L)
        if (cachedAt <= 0L) return true
        return System.currentTimeMillis() - cachedAt > maxAgeMs
    }

    /** When the cached mix was generated, or 0 if none. */
    fun cachedMixTime(): Long = prefs.getLong(KEY_CACHED_MIX_TIME, 0L)

    private fun loadCachedMix(): AiDjMix? {
        // One-time scrub: mixes cached by the pre-fix page generator paired
        // lines with the wrong songs (numbered list shuffled, lookup pool
        // not), and the shuffle is not stored — so a cached line cannot be
        // re-paired to its intended song. Strip the lines, keep the songs
        // and title; the next generation writes correct lines. The flag is
        // set even when there is no cache, so a mix cached after the fix
        // is never scrubbed. The cached time is left untouched so the 24h
        // auto-refresh clock is not reset.
        val scrubNeeded = !prefs.getBoolean(KEY_CACHED_MIX_LINES_SCRUBBED, false)
        if (scrubNeeded) {
            prefs.edit().putBoolean(KEY_CACHED_MIX_LINES_SCRUBBED, true).apply()
        }
        return try {
            val title = prefs.getString(KEY_CACHED_MIX_TITLE, null) ?: return null
            val json = prefs.getString(KEY_CACHED_MIX_TRACKS, null) ?: return null
            val tracks = kotlinx.serialization.json.Json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(AiDjTrack.serializer()),
                json,
            )
            if (tracks.isEmpty()) {
                null
            } else {
                val mix = sanitizeAiDjMix(AiDjMix(title, tracks))
                if (scrubNeeded) {
                    val scrubbed = mix.copy(tracks = mix.tracks.map { it.copy(djLine = null) })
                    try {
                        val scrubJson = kotlinx.serialization.json.Json.encodeToString(
                            kotlinx.serialization.builtins.ListSerializer(AiDjTrack.serializer()),
                            scrubbed.tracks,
                        )
                        prefs.edit().putString(KEY_CACHED_MIX_TRACKS, scrubJson).apply()
                    } catch (_: Exception) {
                    }
                    scrubbed
                } else {
                    mix
                }
            }
        } catch (e: Exception) {
            KLog.w(TAG, "Failed to load cached DJ mix", e)
            null
        }
    }

    fun setApiKey(key: String) {
        // Use commit() (synchronous) for the API key to guarantee it hits disk
        // before we report success. apply() is async and can lose the write.
        val trimmed = key.trim()
        val ok = prefs.edit().putString(KEY_API_KEY, trimmed).commit()
        // Verify by reading back; the UI must reflect what's actually in prefs.
        val persisted = prefs.getString(KEY_API_KEY, null)?.isNotBlank() == true
        _hasApiKey.value = ok && persisted
    }

    /**
     * Returns the saved API key (or empty if none). This is the single source
     * of truth — TTS and all other Gemini features must use this, not their
     * own separate storage.
     */
    fun getApiKey(): String =
        prefs.getString(KEY_API_KEY, null)?.trim().orEmpty()

    fun clearApiKey() {
        prefs.edit().remove(KEY_API_KEY).commit()
        _hasApiKey.value = false
    }

    fun setPrompt(prompt: String) {
        prefs.edit().putString(KEY_PROMPT, prompt).apply()
        _prompt.value = prompt
    }

    fun resetPrompt() = setPrompt(DEFAULT_PROMPT)

    fun setTrackCount(count: Int) {
        val clamped = count.coerceIn(TRACK_COUNT_OPTIONS.min(), TRACK_COUNT_OPTIONS.max())
        prefs.edit().putInt(KEY_TRACK_COUNT, clamped).apply()
        _trackCount.value = clamped
    }

    fun setModel(model: String) {
        prefs.edit().putString(KEY_MODEL, model).apply()
        _model.value = model
    }

    fun setSafeTokenMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SAFE_TOKEN_MODE, enabled).apply()
        _safeTokenMode.value = enabled
    }

    fun setDjVoiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DJ_VOICE, enabled).apply()
        _djVoiceEnabled.value = enabled
    }

    fun setAutoRefresh(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_REFRESH, enabled).apply()
        _autoRefresh.value = enabled
    }

    private fun loadTokenUsage(): AiDjTokenUsage = AiDjTokenUsage(
        promptTokens = prefs.getLong(KEY_TOKENS_PROMPT, 0L),
        outputTokens = prefs.getLong(KEY_TOKENS_OUTPUT, 0L),
    )

    fun clearTokenUsage() {
        prefs.edit()
            .remove(KEY_TOKENS_PROMPT)
            .remove(KEY_TOKENS_OUTPUT)
            .apply()
        _tokenUsage.value = AiDjTokenUsage(0L, 0L)
    }

    private fun recordTokenUsage(promptTokens: Long, outputTokens: Long) {
        val updated = AiDjTokenUsage(
            promptTokens = _tokenUsage.value.promptTokens + promptTokens,
            outputTokens = _tokenUsage.value.outputTokens + outputTokens,
        )
        prefs.edit()
            .putLong(KEY_TOKENS_PROMPT, updated.promptTokens)
            .putLong(KEY_TOKENS_OUTPUT, updated.outputTokens)
            .apply()
        _tokenUsage.value = updated
    }

    /**
     * Ask Gemini to craft a mix from [library]. Returns the mix with each
     * pick resolved to its [Song], in play order.
     */
    suspend fun generateMix(library: List<Song>): Result<AiDjMix> = withContext(Dispatchers.IO) {
        val apiKey = prefs.getString(KEY_API_KEY, null)?.trim().orEmpty()
        if (apiKey.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("No Google AI API key set"))
        }
        val pool = library.distinctBy { it.id }.take(
            if (_safeTokenMode.value) MAX_LIBRARY_SONGS_SAFE else MAX_LIBRARY_SONGS
        )
        if (pool.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Library is empty"))
        }
        val count = _trackCount.value.coerceAtMost(pool.size)

        // Shuffle the library order every generation so the numbered list is
        // different each time. Position bias is real — same order = same picks.
        val shuffled = pool.shuffled()
        val numbered = shuffled.mapIndexed { i, song ->
            "${i + 1}. ${song.title} — ${song.artist}"
        }.joinToString("\n")

        // Random mix angle for the library portion (rotates every generation).
        // The shuffled library order and this angle already make every
        // generation's prompt unique — never seed it with a "session" number:
        // the model echoes that number into titles and spoken lines, and the
        // user never sees or hears one.
        val mixAngles = listOf(
            "open with an unexpected deep cut, then build",
            "front-load the energy, peak early, coast out",
            "slow burn: start mellow, build to a late peak",
            "genre-hop aggressively but make each transition land",
            "spotlight forgotten favorites from the middle of the library",
            "build around a single mood and go deep on it",
            "alternate between familiar anthems and curveballs",
            "tell a story: each song hands off to the next",
        )
        val mixAngle = mixAngles.random()

        val userText = buildString {
            appendLine(_prompt.value.trim().ifEmpty { DEFAULT_PROMPT })
            appendLine()
            appendLine("Mix angle: $mixAngle.")
            appendLine()
            appendLine("The listener's library (numbered as \"title — artist\"):")
            appendLine(numbered)
            appendLine()
            append("Pick exactly $count tracks by their 1-based numbers, in play order, ")
            append("for a flowing DJ set. You may only pick from the list above. ")
            append("CRITICAL - SPOKEN LINES: Most tracks get NO line. Add a \"line\" ONLY for a genuine ")
            append("shift worth announcing (new genre, big energy change, different era). NO MORE than ")
            append("1 in 4 tracks may have a line. When in doubt, leave it off. ")
            append("A line is spoken immediately BEFORE the track it is attached to and shown under that track, ")
            append("so it must describe THAT track — its sound, or the step into it from the previous song — ")
            append("and must never name an artist other than that track's artist or the previous track's artist. ")
            append("NEVER mention a session number, episode number, or anything like 'session 21761' ")
            append("in the title or in any line — the listener never hears one. ")
            append("Respond with ONLY this JSON object and no other text: ")
            append("{\"title\": \"mix title\", \"tracks\": [{\"n\": 3}, {\"n\": 7}, {\"n\": 12, \"line\": \"short spoken DJ intro\"}]}")
        }

        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(
                JSONObject().put("text", SYSTEM_INSTRUCTION)
            )))
            .put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", userText)
                ))
            ))
            .put("generationConfig", JSONObject()
                .put("temperature", 0.9)
                .put("response_mime_type", "application/json")
                .put("maxOutputTokens", 8192)
            )
            .toString()

        try {
            val request = Request.Builder()
                .url("$GENERATE_URL_BASE/${_model.value}:generateContent")
                .header("x-goog-api-key", apiKey)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = runCatching {
                        JSONObject(responseBody).optString("message")
                            .ifEmpty { JSONObject(responseBody).getJSONObject("error").optString("message") }
                    }.getOrNull().orEmpty()
                    KLog.w(TAG, "Gemini request failed: HTTP ${response.code} $message")
                    return@withContext Result.failure(
                        IllegalStateException(
                            if (message.isNotBlank()) "Google AI: $message"
                            else "Google AI request failed (HTTP ${response.code})"
                        )
                    )
                }
                // The numbered list was built from `shuffled`, so the lookup
                // pool must be `shuffled` too. Passing the unshuffled `pool`
                // here paired every pick — and its spoken line — with a random
                // different song from the same pool: the model wrote a line
                // for shuffled[n-1] and the code attached it to pool[n-1].
                return@withContext parseMix(responseBody, shuffled)
            }
        } catch (e: Exception) {
            KLog.w(TAG, "AI DJ generation failed", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * The one-press DJ: builds a long (60-track) set from the [recent] plays
     * with [liked] as taste context, plus fresh discoveries. Spoken transition
     * lines are attached only at notable theme or mood shifts, not between
     * every song — Spotify DJ style.
     *
     * The mix is 60% familiar (from their library) and 40% new discoveries
     * the AI thinks they'll love, resolved via YouTube search. A daily seed
     * keeps each day's mix unique.
     */
    suspend fun generateInstantMix(
        recent: List<Song>,
        liked: List<Song>,
    ): Result<AiDjMix> = withContext(Dispatchers.IO) {
        val apiKey = prefs.getString(KEY_API_KEY, null)?.trim().orEmpty()
        if (apiKey.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("No Google AI API key set"))
        }
        val recentPool = recent.distinctBy { it.id }.take(INSTANT_MIX_RECENT)
        val likedPool = liked.distinctBy { it.id }
            .filter { liked -> recentPool.none { it.id == liked.id } }
            .take(INSTANT_MIX_LIKED)
        if (recentPool.isEmpty() && likedPool.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Nothing to build a mix from yet"))
        }
        // Shuffle both pools every generation — fixed order = fixed picks.
        val shuffledRecent = recentPool.shuffled()
        val shuffledLiked = likedPool.shuffled()
        val numbered = buildString {
            appendLine("Recently played:")
            shuffledRecent.forEachIndexed { i, song ->
                appendLine("${i + 1}. ${song.title} — ${song.artist}")
            }
            if (shuffledLiked.isNotEmpty()) {
                appendLine("Liked favorites (taste context):")
                shuffledLiked.forEachIndexed { i, song ->
                    appendLine("${shuffledRecent.size + i + 1}. ${song.title} — ${song.artist}")
                }
            }
        }
        // No "session" seed here: the daily angle, the shuffled recent/liked
        // order, and temperature already make every generation unique, and a
        // session number in the prompt gets echoed into titles and spoken
        // lines — which the user never sees or hears.

        val libraryCount = (INSTANT_MIX_TRACKS * 0.6).toInt()
        val discoveryCount = INSTANT_MIX_TRACKS - libraryCount
        // Daily seed: a fresh angle every day so the mix never repeats itself.
        val today = java.time.LocalDate.now().toString()
        val dailyAngles = listOf(
            "deep cuts and B-sides they have never considered",
            "current chart climbers across their favorite genres",
            "one-hit wonders and forgotten gems from the last three decades",
            "live versions and acoustic reimaginings of familiar sounds",
            "rising underground artists about to break through",
            "cross-genre collaborations and unexpected features",
            "songs that sample or interpolate artists they love",
        )
        val dailyAngle = dailyAngles[today.hashCode().mod(dailyAngles.size).let { if (it < 0) it + dailyAngles.size else it }]

        val userText = buildString {
            appendLine(_prompt.value.trim().ifEmpty { DEFAULT_PROMPT })
            appendLine()
            appendLine(numbered)
            appendLine()
            append("Today is $today. Build a $INSTANT_MIX_TRACKS-song DJ set in play order, ")
            append("flowing from what they have been playing lately through their liked favorites. ")
            append("Today's discovery angle: $dailyAngle. ")
            append("$libraryCount tracks: pick by their 1-based numbers from the lists above. ")
            append("$discoveryCount tracks: NEW discoveries NOT in the lists above — songs they have ")
            append("never heard that match their taste, from popular charts or deep cuts. ")
            append("Give each discovery as {\"new\": {\"title\": \"...\", \"artist\": \"...\"}}. ")
            append("Interleave discoveries through the set so familiar and new alternate naturally. ")
            append("CRITICAL - SPOKEN LINES: Most tracks get NO line. Add a \"line\" ONLY when the ")
            append("track it is attached to marks a genuine shift worth announcing: a new genre, a big ")
            append("energy change, a different era, or a discovery that needs context. NO MORE than 1 in 4 tracks may ")
            append("have a line. When in doubt, leave it off. A line marks a moment, not every song. ")
            append("A line is spoken immediately BEFORE the track it is attached to and shown under that track, ")
            append("so it must describe THAT track — never the following track — and must never name an artist ")
            append("other than that track's artist or the previous track's artist. A discovery's line may introduce ")
            append("that discovery itself. ")
            append("NEVER mention a session number, episode number, or anything like 'session 21761' ")
            append("in the title or in any line — the listener never hears one. ")
            append("Respond with ONLY this JSON object and no other text: ")
            append("{\"title\": \"mix title\", \"tracks\": [{\"n\": 3}, {\"n\": 7}, {\"n\": 12, \"line\": \"spoken transition\"}, ")
            append("{\"new\": {\"title\": \"...\", \"artist\": \"...\"}}]}")
        }

        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(
                JSONObject().put("text", SYSTEM_INSTRUCTION)
            )))
            .put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", userText)
                ))
            ))
            .put("generationConfig", JSONObject()
                .put("temperature", 0.95)
                .put("response_mime_type", "application/json")
                // 60-track sets with lines run long; a clipped tail used to
                // fail the whole parse. The parser now salvages partial
                // replies, but headroom is cheaper than salvage.
                .put("maxOutputTokens", 16384)
            )
            .toString()

        try {
            val request = Request.Builder()
                .url("$GENERATE_URL_BASE/${_model.value}:generateContent")
                .header("x-goog-api-key", apiKey)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = runCatching {
                        JSONObject(responseBody).optString("message")
                            .ifEmpty { JSONObject(responseBody).getJSONObject("error").optString("message") }
                    }.getOrNull().orEmpty()
                    KLog.w(TAG, "Gemini instant mix failed: HTTP ${response.code} $message")
                    return@withContext Result.failure(
                        IllegalStateException(
                            if (message.isNotBlank()) "Google AI: $message"
                            else "Google AI request failed (HTTP ${response.code})"
                        )
                    )
                }
                return@withContext parseMixWithDiscoveries(responseBody, shuffledRecent + shuffledLiked)
            }
        } catch (e: Exception) {
            KLog.w(TAG, "AI DJ instant mix failed", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Parse a mix that interleaves library picks ({"n": ...}) with new
     * discoveries ({"new": {"title": ..., "artist": ...}}). Discoveries are
     * resolved to playable songs via YouTube search, in parallel.
     */
    /**
     * Parse a mix that interleaves library picks ({"n": ...}) with new
     * discoveries ({"new": {"title": ..., "artist": ...}}). Discoveries are
     * resolved to playable songs via YouTube search, in parallel.
     *
     * The parser is deliberately tolerant: a generation that comes back in a
     * slightly different shape (a bare track array, a bare-number entry, a
     * truncated tail) still yields a playable mix from whatever complete
     * entries it carries, instead of failing the whole regeneration with a
     * generic error. Only a response with zero usable entries fails, and
     * every failure branch logs its class under the AiDj tag (shapes and
     * short snippets only — never keys, prompts, or full responses) so the
     * next failure is diagnosable from a bug report instead of a guess.
     */
    private suspend fun parseMixWithDiscoveries(
        responseBody: String,
        pool: List<Song>,
    ): Result<AiDjMix> = withContext(Dispatchers.IO) {
        data class ParsedItem(
            val libraryIndex: Int?,
            val discovery: AiDjDiscovery?,
            val line: String?,
        )
        // One entry of the model's track list, in whatever shape it arrived.
        // Library picks are {"n": k}; discoveries are {"new": {...}}.
        // Tolerated variants: a bare number (the pick itself), "index" for
        // "n", and "discovery" for "new". Anything else is dropped, never
        // fatal — a single odd entry must not kill a 60-track mix.
        fun parseEntry(raw: Any?): ParsedItem? {
            val lineOf: (JSONObject) -> String? = { entry ->
                entry.optString("line").takeIf { it.isNotBlank() }?.let(::stripSessionNumberFromLine)
            }
            when (raw) {
                is Number -> {
                    val n = raw.toInt()
                    return if (n in 1..pool.size) ParsedItem(n, null, null) else null
                }
                is JSONObject -> {
                    val entry = raw
                    val line = lineOf(entry)
                    val n = entry.optInt("n", -1).takeIf { it >= 1 }
                        ?: entry.optInt("index", -1).takeIf { it >= 1 }
                    if (n != null && n <= pool.size) {
                        return ParsedItem(libraryIndex = n, discovery = null, line = line)
                    }
                    val newObj = entry.optJSONObject("new") ?: entry.optJSONObject("discovery")
                    if (newObj != null) {
                        val dTitle = newObj.optString("title").trim()
                        val dArtist = newObj.optString("artist").trim()
                        if (dTitle.isBlank() || dArtist.isBlank()) return null
                        // Skip discoveries already in the library.
                        if (pool.any {
                                it.title.equals(dTitle, ignoreCase = true) &&
                                    it.artist.equals(dArtist, ignoreCase = true)
                            }
                        ) return null
                        return ParsedItem(
                            libraryIndex = null,
                            discovery = AiDjDiscovery(dTitle, dArtist, line),
                            line = line,
                        )
                    }
                    return null
                }
                else -> return null
            }
        }
        // Parse one track-list JSON value (object with a "tracks" array, or
        // a bare array of entries). Returns null when the value is not a
        // track list at all.
        fun parseTrackListValue(value: Any?): Pair<String, List<ParsedItem>>? {
            val seen = HashSet<Int>()
            when (value) {
                is JSONObject -> {
                    val tracksJson = value.optJSONArray("tracks") ?: value.optJSONArray("songs")
                        ?: return null
                    val items = (0 until tracksJson.length()).mapNotNull { i ->
                        val item = parseEntry(tracksJson.opt(i))
                        if (item?.libraryIndex != null && !seen.add(item.libraryIndex)) null else item
                    }
                    val title = stripSessionNumberFromTitle(
                        value.optString("title").ifBlank { "AI DJ Mix" }
                    )
                    return title to items
                }
                is JSONArray -> {
                    val items = (0 until value.length()).mapNotNull { i ->
                        val item = parseEntry(value.opt(i))
                        if (item?.libraryIndex != null && !seen.add(item.libraryIndex)) null else item
                    }
                    return "AI DJ Mix" to items
                }
                else -> return null
            }
        }
        // Salvage pass for a truncated or otherwise unparseable reply: scan
        // for balanced {...} objects (string-aware) and keep every complete
        // track entry found, in order. A mix cut off mid-tail still yields
        // its complete head instead of nothing.
        fun salvageEntries(text: String): Pair<String, List<ParsedItem>>? {
            val seen = HashSet<Int>()
            val items = mutableListOf<ParsedItem>()
            var title: String? = null
            var i = 0
            while (i < text.length) {
                if (text[i] != '{') {
                    i++
                    continue
                }
                var depth = 0
                var inString = false
                var escaped = false
                var j = i
                var closed = false
                while (j < text.length) {
                    val c = text[j]
                    if (inString) {
                        if (escaped) escaped = false
                        else if (c == '\\') escaped = true
                        else if (c == '"') inString = false
                    } else {
                        when (c) {
                            '"' -> inString = true
                            '{' -> depth++
                            '}' -> {
                                depth--
                                if (depth == 0) {
                                    closed = true
                                    break
                                }
                            }
                        }
                    }
                    j++
                }
                if (!closed) break
                val candidate = runCatching { JSONObject(text.substring(i, j + 1)) }.getOrNull()
                if (candidate != null) {
                    val asList = parseTrackListValue(candidate)
                    if (asList != null) {
                        if (title == null && candidate.optString("title").isNotBlank()) {
                            title = stripSessionNumberFromTitle(candidate.optString("title"))
                        }
                        for (item in asList.second) {
                            if (item.libraryIndex != null && !seen.add(item.libraryIndex)) continue
                            items += item
                        }
                    } else {
                        val single = parseEntry(candidate)
                        if (single != null) {
                            if (single.libraryIndex != null && !seen.add(single.libraryIndex)) {
                                // Duplicate pick: skip, keep scanning.
                            } else {
                                items += single
                            }
                        }
                    }
                }
                i = j + 1
            }
            if (items.isEmpty()) return null
            return (title ?: "AI DJ Mix") to items
        }
        val (title, items) = try {
            val root = JSONObject(responseBody)
            root.optJSONObject("usageMetadata")?.let { usage ->
                recordTokenUsage(
                    promptTokens = usage.optLong("promptTokenCount", 0L),
                    outputTokens = usage.optLong("candidatesTokenCount", 0L),
                )
            }
            // Surface safety blocks explicitly, like the library parser does:
            // a blocked prompt used to fall through to a generic parse error.
            root.optJSONObject("promptFeedback")?.optString("blockReason")?.takeIf { it.isNotBlank() }?.let { reason ->
                KLog.w(TAG, "AI DJ instant mix prompt blocked: $reason")
                return@withContext Result.failure(IllegalStateException("The DJ's request was blocked ($reason)"))
            }
            val candidates = root.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                KLog.w(TAG, "AI DJ instant mix: no candidates")
                return@withContext Result.failure(IllegalStateException("The DJ came back empty-handed"))
            }
            val first = candidates.getJSONObject(0)
            val finishReason = first.optString("finishReason").ifBlank { "unknown" }
            val parts = first.optJSONObject("content")?.optJSONArray("parts")
            if (parts == null || parts.length() == 0) {
                KLog.w(TAG, "AI DJ instant mix: no content parts (finishReason=$finishReason)")
                return@withContext Result.failure(IllegalStateException("The DJ came back empty-handed"))
            }
            val text = parts.getJSONObject(0).optString("text").orEmpty()
            if (text.isBlank()) {
                KLog.w(TAG, "AI DJ instant mix: empty text part (finishReason=$finishReason)")
                return@withContext Result.failure(IllegalStateException("The DJ came back empty-handed"))
            }
            val trimmed = text.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            val parsed = runCatching {
                val start = trimmed.indexOf('{')
                val arrayStart = trimmed.indexOf('[')
                when {
                    // Bare array reply: the whole text is the track list.
                    trimmed.startsWith("[") -> parseTrackListValue(JSONArray(trimmed))
                    start >= 0 -> {
                        val end = trimmed.lastIndexOf('}')
                        if (end <= start) null
                        else parseTrackListValue(JSONObject(trimmed.substring(start, end + 1)))
                    }
                    arrayStart >= 0 -> parseTrackListValue(
                        JSONArray(trimmed.substring(arrayStart, trimmed.lastIndexOf(']') + 1))
                    )
                    else -> null
                }
            }.getOrNull()
            val result = parsed?.takeIf { it.second.isNotEmpty() }
                ?: salvageEntries(text)
            if (result == null) {
                KLog.w(
                    TAG,
                    "AI DJ instant mix: unusable reply " +
                        "(finishReason=$finishReason, textLen=${text.length}, " +
                        "head=${text.take(200)})"
                )
                return@withContext Result.failure(IllegalStateException("Couldn't understand the DJ's reply"))
            }
            if (parsed == null) {
                KLog.w(
                    TAG,
                    "AI DJ instant mix: salvaged ${result.second.size} entries " +
                        "from a malformed reply (finishReason=$finishReason)"
                )
            }
            result
        } catch (e: Exception) {
            KLog.w(TAG, "Failed to parse AI DJ instant mix response: ${e.javaClass.simpleName}", e)
            return@withContext Result.failure(IllegalStateException("Couldn't understand the DJ's reply"))
        }

        // Resolve discoveries in parallel via YouTube search.
        val tracks = items.map { item ->
            async {
                val song = when {
                    item.libraryIndex != null -> pool[item.libraryIndex - 1]
                    item.discovery != null -> resolveDiscovery(item.discovery)
                    else -> null
                }
                song?.let { AiDjTrack(song = it, djLine = item.line) }
            }
        }.awaitAll().filterNotNull()

        if (tracks.isEmpty()) {
            Result.failure(IllegalStateException("The DJ came back with no playable picks"))
        } else {
            // Safety cap: no more than 1 in 4 tracks carries a spoken line.
            // If the model ignored the prompt constraint, thin the lines out,
            // keeping them spread through the set.
            val maxLines = (tracks.size / 4).coerceAtLeast(1)
            val withLines = tracks.mapIndexedNotNull { i, t ->
                if (t.djLine != null) i else null
            }
            val capped = if (withLines.size > maxLines) {
                // Keep lines evenly spread through the set.
                val step = withLines.size.toFloat() / maxLines
                val keepPos = (0 until maxLines).map { withLines[(it * step).toInt()] }.toSet()
                tracks.mapIndexed { i, t ->
                    if (t.djLine != null && i !in keepPos) t.copy(djLine = null) else t
                }
            } else tracks
            Result.success(AiDjMix(title = title, tracks = dropMisattributedLines(capped)))
        }
    }

    /**
     * Resolve a discovery to a playable YouTube song via search. Returns null
     * when nothing suitable is found.
     */
    private suspend fun resolveDiscovery(discovery: AiDjDiscovery): Song? {
        return try {
            val results = youTubeRepository.search("${discovery.title} ${discovery.artist}")
            results.firstOrNull()
        } catch (e: Exception) {
            KLog.w(TAG, "Discovery search failed for ${discovery.title}", e)
            null
        }
    }

    /**
     * Map the listener's favorite artists to subgenres (e.g. "Conscious Rap")
     * with similar artists worth discovering. Used by the Explore tab.
     */
    suspend fun suggestSubgenres(
        artists: List<String>,
    ): Result<List<SubgenreSuggestion>> = withContext(Dispatchers.IO) {
        val apiKey = prefs.getString(KEY_API_KEY, null)?.trim().orEmpty()
        if (apiKey.isEmpty() || artists.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("No API key or artists"))
        }
        val userText = buildString {
            append("These are a listener's favorite artists: ")
            append(artists.joinToString(", "))
            append(". Group them into 2-3 subgenres (be specific: \"Conscious Rap\" not just \"Hip-Hop\", ")
            append("\"Neo-Soul\" not just \"R&B\"). For each subgenre, name 4 similar artists they would ")
            append("probably love but who are NOT in the list above. Respond with ONLY this JSON array ")
            append("and no other text: [{\"subgenre\": \"...\", \"based_on\": [\"...\"], \"artists\": [\"...\", \"...\"]}]")
        }
        val body = JSONObject()
            .put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", userText)
                ))
            ))
            .put("generationConfig", JSONObject()
                .put("temperature", 0.7)
                .put("response_mime_type", "application/json")
                .put("maxOutputTokens", 1024)
            )
            .toString()
        try {
            val request = Request.Builder()
                .url("$GENERATE_URL_BASE/${_model.value}:generateContent")
                .header("x-goog-api-key", apiKey)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Google AI request failed (HTTP ${response.code})")
                    )
                }
                return@withContext parseSubgenres(responseBody)
            }
        } catch (e: Exception) {
            KLog.w(TAG, "Subgenre suggestions failed", e)
            return@withContext Result.failure(e)
        }
    }

    private fun parseSubgenres(responseBody: String): Result<List<SubgenreSuggestion>> {
        return runCatching {
            val root = JSONObject(responseBody)
            val text = root.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
            val array = JSONArray(text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
            List(array.length()) { i ->
                val obj = array.getJSONObject(i)
                SubgenreSuggestion(
                    subgenre = obj.optString("subgenre"),
                    basedOn = obj.optJSONArray("based_on")?.let { arr ->
                        List(arr.length()) { j -> arr.optString(j) }
                    }.orEmpty(),
                    artists = obj.optJSONArray("artists")?.let { arr ->
                        List(arr.length()) { j -> arr.optString(j) }
                    }.orEmpty(),
                )
            }.filter { it.subgenre.isNotBlank() && it.artists.isNotEmpty() }
        }
    }

    private fun parseMix(responseBody: String, pool: List<Song>): Result<AiDjMix> {
        return try {
            val root = JSONObject(responseBody)
            root.optJSONObject("usageMetadata")?.let { usage ->
                recordTokenUsage(
                    promptTokens = usage.optLong("promptTokenCount", 0L),
                    outputTokens = usage.optLong("candidatesTokenCount", 0L),
                )
            }
            // Surface safety blocks explicitly instead of a generic parse error.
            root.optJSONObject("promptFeedback")?.optString("blockReason")?.takeIf { it.isNotBlank() }?.let { reason ->
                KLog.w(TAG, "AI DJ prompt blocked: $reason")
                return Result.failure(IllegalStateException("The DJ's request was blocked ($reason)"))
            }
            val candidates = root.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                KLog.w(TAG, "AI DJ: no candidates in response: ${responseBody.take(300)}")
                return Result.failure(IllegalStateException("The DJ came back empty-handed"))
            }
            val text = candidates.getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
            // The model is told to answer with pure JSON, but be lenient:
            // slice from the first { to the last }.
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) {
                KLog.w(TAG, "AI DJ: no JSON object in response text: ${text.take(300)}")
                return Result.failure(IllegalStateException("Couldn't understand the DJ's reply"))
            }
            val jsonText = text.substring(start, end + 1)
            val mix = JSONObject(jsonText)
            val title = stripSessionNumberFromTitle(mix.optString("title").ifBlank { "AI DJ Mix" })
            val tracksJson = mix.getJSONArray("tracks")
            val seen = HashSet<Int>()
            val tracks = (0 until tracksJson.length()).mapNotNull { i ->
                val entry = tracksJson.getJSONObject(i)
                val n = entry.optInt("n", -1)
                if (n < 1 || n > pool.size || !seen.add(n)) return@mapNotNull null
                AiDjTrack(
                    song = pool[n - 1],
                    djLine = entry.optString("line").takeIf { it.isNotBlank() }?.let(::stripSessionNumberFromLine),
                )
            }
            if (tracks.isEmpty()) {
                Result.failure(IllegalStateException("The DJ came back with no playable picks"))
            } else {
                Result.success(AiDjMix(title = title, tracks = dropMisattributedLines(tracks)))
            }
        } catch (e: Exception) {
            KLog.w(TAG, "Failed to parse AI DJ response", e)
            Result.failure(IllegalStateException("Couldn't understand the DJ's reply"))
        }
    }

    companion object {
        private const val TAG = "AiDj"
        private const val PREFS_NAME = "ivor_music_ai_dj"
        private const val KEY_API_KEY = "google_ai_api_key"
        private const val KEY_PROMPT = "dj_prompt"
        private const val KEY_TRACK_COUNT = "track_count"
        private const val KEY_MODEL = "ai_model"
        private const val KEY_SAFE_TOKEN_MODE = "safe_token_mode"
        private const val KEY_DJ_VOICE = "dj_voice_enabled"
        private const val KEY_AUTO_REFRESH = "dj_auto_refresh"
        private const val KEY_TOKENS_PROMPT = "tokens_prompt"
        private const val KEY_TOKENS_OUTPUT = "tokens_output"
        private const val KEY_CACHED_MIX_TITLE = "cached_mix_title"
        private const val KEY_CACHED_MIX_TRACKS = "cached_mix_tracks"
        private const val KEY_CACHED_MIX_TIME = "cached_mix_time"
        private const val KEY_CACHED_MIX_LINES_SCRUBBED = "cached_mix_lines_scrubbed"

        private const val GENERATE_URL_BASE =
            "https://generativelanguage.googleapis.com/v1beta/models"

        private const val MAX_LIBRARY_SONGS = 600
        private const val MAX_LIBRARY_SONGS_SAFE = 120
        private const val DEFAULT_TRACK_COUNT = 15
        /** The one-press DJ set length. */
        const val INSTANT_MIX_TRACKS = 60
        private const val INSTANT_MIX_RECENT = 30
        private const val INSTANT_MIX_LIKED = 200
        val TRACK_COUNT_OPTIONS = listOf(10, 15, 20, 25, 40, 60)

        /** The default; the API reports older models as retired, so this stays current. */
        const val DEFAULT_MODEL = "gemini-3.8-flash"

        /** Selectable models, newest first. */
        val AI_MODELS = listOf(
            "gemini-3.8-flash",
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "gemini-flash-latest",
            "gemini-flash-lite-latest",
            "gemini-pro-latest",
        )

        private const val SYSTEM_INSTRUCTION =
            "You are Koda DJ, a charismatic radio DJ with impeccable taste. " +
                "You craft mixes with a real arc: open strong, build energy, " +
                "and land the ending. Your intro lines are spoken aloud between songs, " +
                "so keep each to one vivid sentence. Every reply is exactly the JSON object requested, nothing else."

        const val DEFAULT_PROMPT =
            "You are a late-night radio DJ with great taste. Read the room, " +
                "build energy across the set, and make every transition feel inevitable."

        /** One-tap prompt presets, like a starting personality for the DJ. */
        val PROMPT_PRESETS = listOf(
            "Professional Curator" to
                "You are 'Vibe-Engine', a professional music curator. Analyze the " +
                    "listener's library to provide perfect music recommendations. " +
                    "Always prioritize flow, emotional resonance, and discovery.",
            "Creative Maverick" to
                "You are a fearless college-radio DJ who lives for the unexpected " +
                    "left turn. Take big swings between genres, but make every " +
                    "transition feel intentional. Surprise the listener.",
        )
    }
}
