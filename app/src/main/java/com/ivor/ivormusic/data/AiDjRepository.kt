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

    private val appContext = context.applicationContext
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
        _cachedMix.value = mix
        try {
            val json = kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(AiDjTrack.serializer()),
                mix.tracks,
            )
            prefs.edit()
                .putString(KEY_CACHED_MIX_TITLE, mix.title)
                .putString(KEY_CACHED_MIX_TRACKS, json)
                .apply()
        } catch (e: Exception) {
            KLog.w(TAG, "Failed to cache DJ mix", e)
        }
    }

    private fun loadCachedMix(): AiDjMix? {
        return try {
            val title = prefs.getString(KEY_CACHED_MIX_TITLE, null) ?: return null
            val json = prefs.getString(KEY_CACHED_MIX_TRACKS, null) ?: return null
            val tracks = kotlinx.serialization.json.Json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(AiDjTrack.serializer()),
                json,
            )
            if (tracks.isEmpty()) null else AiDjMix(title, tracks)
        } catch (e: Exception) {
            KLog.w(TAG, "Failed to load cached DJ mix", e)
            null
        }
    }

    fun setApiKey(key: String) {
        prefs.edit().putString(KEY_API_KEY, key.trim()).apply()
        _hasApiKey.value = key.isNotBlank()
    }

    fun clearApiKey() {
        prefs.edit().remove(KEY_API_KEY).apply()
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

        val numbered = pool.mapIndexed { i, song ->
            "${i + 1}. ${song.title} — ${song.artist}"
        }.joinToString("\n")

        val userText = buildString {
            appendLine(_prompt.value.trim().ifEmpty { DEFAULT_PROMPT })
            appendLine()
            appendLine("The listener's library (numbered as \"title — artist\"):")
            appendLine(numbered)
            appendLine()
            append("Pick exactly $count tracks by their 1-based numbers, in play order, ")
            append("for a flowing DJ set. You may only pick from the list above. ")
            append("CRITICAL - SPOKEN LINES: Most tracks get NO line. Add a \"line\" ONLY for a genuine ")
            append("shift worth announcing (new genre, big energy change, different era). NO MORE than ")
            append("1 in 4 tracks may have a line. When in doubt, leave it off. ")
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
                .put("maxOutputTokens", 4096)
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
                return@withContext parseMix(responseBody, pool)
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
        // One numbered pool; the model sees which list each track came from.
        val pool = recentPool + likedPool
        val numbered = buildString {
            appendLine("Recently played:")
            recentPool.forEachIndexed { i, song ->
                appendLine("${i + 1}. ${song.title} — ${song.artist}")
            }
            if (likedPool.isNotEmpty()) {
                appendLine("Liked favorites (taste context):")
                likedPool.forEachIndexed { i, song ->
                    appendLine("${recentPool.size + i + 1}. ${song.title} — ${song.artist}")
                }
            }
        }

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
            append("next track is a genuine shift worth announcing: a new genre, a big energy change, ")
            append("a different era, or a discovery that needs context. NO MORE than 1 in 4 tracks may ")
            append("have a line. When in doubt, leave it off. A line marks a moment, not every song. ")
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
                    KLog.w(TAG, "Gemini instant mix failed: HTTP ${response.code} $message")
                    return@withContext Result.failure(
                        IllegalStateException(
                            if (message.isNotBlank()) "Google AI: $message"
                            else "Google AI request failed (HTTP ${response.code})"
                        )
                    )
                }
                return@withContext parseMixWithDiscoveries(responseBody, pool)
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
    private suspend fun parseMixWithDiscoveries(
        responseBody: String,
        pool: List<Song>,
    ): Result<AiDjMix> = withContext(Dispatchers.IO) {
        data class ParsedItem(
            val libraryIndex: Int?,
            val discovery: AiDjDiscovery?,
            val line: String?,
        )
        val (title, items) = try {
            val root = JSONObject(responseBody)
            root.optJSONObject("usageMetadata")?.let { usage ->
                recordTokenUsage(
                    promptTokens = usage.optLong("promptTokenCount", 0L),
                    outputTokens = usage.optLong("candidatesTokenCount", 0L),
                )
            }
            val text = root.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
            val jsonText = text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1)
            val mix = JSONObject(jsonText)
            val mixTitle = mix.optString("title").ifBlank { "AI DJ Mix" }
            val tracksJson = mix.getJSONArray("tracks")
            val seen = HashSet<Int>()
            val parsedItems = (0 until tracksJson.length()).mapNotNull { i ->
                val entry = tracksJson.getJSONObject(i)
                val line = entry.optString("line").takeIf { it.isNotBlank() }
                val n = entry.optInt("n", -1)
                if (n >= 1 && n <= pool.size && seen.add(n)) {
                    ParsedItem(libraryIndex = n, discovery = null, line = line)
                } else {
                    val newObj = entry.optJSONObject("new") ?: return@mapNotNull null
                    val dTitle = newObj.optString("title").trim()
                    val dArtist = newObj.optString("artist").trim()
                    if (dTitle.isBlank() || dArtist.isBlank()) return@mapNotNull null
                    // Skip discoveries already in the library.
                    if (pool.any {
                            it.title.equals(dTitle, ignoreCase = true) &&
                                it.artist.equals(dArtist, ignoreCase = true)
                        }
                    ) return@mapNotNull null
                    ParsedItem(
                        libraryIndex = null,
                        discovery = AiDjDiscovery(dTitle, dArtist, line),
                        line = line,
                    )
                }
            }
            mixTitle to parsedItems
        } catch (e: Exception) {
            KLog.w(TAG, "Failed to parse AI DJ response", e)
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
            Result.success(AiDjMix(title = title, tracks = capped))
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
            val text = root.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
            // The model is told to answer with pure JSON, but be lenient:
            // slice from the first { to the last }.
            val jsonText = text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1)
            val mix = JSONObject(jsonText)
            val title = mix.optString("title").ifBlank { "AI DJ Mix" }
            val tracksJson = mix.getJSONArray("tracks")
            val seen = HashSet<Int>()
            val tracks = (0 until tracksJson.length()).mapNotNull { i ->
                val entry = tracksJson.getJSONObject(i)
                val n = entry.optInt("n", -1)
                if (n < 1 || n > pool.size || !seen.add(n)) return@mapNotNull null
                AiDjTrack(
                    song = pool[n - 1],
                    djLine = entry.optString("line").takeIf { it.isNotBlank() },
                )
            }
            if (tracks.isEmpty()) {
                Result.failure(IllegalStateException("The DJ came back with no playable picks"))
            } else {
                Result.success(AiDjMix(title = title, tracks = tracks))
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
        private const val KEY_TOKENS_PROMPT = "tokens_prompt"
        private const val KEY_TOKENS_OUTPUT = "tokens_output"
        private const val KEY_CACHED_MIX_TITLE = "cached_mix_title"
        private const val KEY_CACHED_MIX_TRACKS = "cached_mix_tracks"

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
