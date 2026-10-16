package com.ivor.ivormusic.data

import android.content.Context
import android.content.SharedPreferences
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.Dispatchers
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
data class AiDjTrack(
    val song: Song,
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
            append("Respond with ONLY this JSON object and no other text: ")
            append("{\"title\": \"mix title\", \"tracks\": [{\"n\": 3, \"line\": \"short spoken DJ intro line for this track\"}]}")
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

        private const val GENERATE_URL_BASE =
            "https://generativelanguage.googleapis.com/v1beta/models"

        private const val MAX_LIBRARY_SONGS = 600
        private const val MAX_LIBRARY_SONGS_SAFE = 120
        private const val DEFAULT_TRACK_COUNT = 15
        val TRACK_COUNT_OPTIONS = listOf(10, 15, 20, 25)

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
