package com.ivor.ivormusic.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import android.util.Base64
import com.ivor.ivormusic.util.KLog

/**
 * Natural-sounding DJ voice via Gemini's native text-to-speech.
 *
 * Unlike the on-device TextToSpeech (robotic), Gemini TTS produces
 * human-like speech with selectable voices. Uses the same Google AI API key
 * as the DJ's playlist generation — no extra setup.
 *
 * The API returns 24kHz 16-bit mono PCM; we wrap it in a WAV header so the
 * player can play the file directly.
 */
class GeminiTtsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    /** The selected TTS voice. */
    var voice: String
        get() = prefs.getString(KEY_VOICE, DEFAULT_VOICE) ?: DEFAULT_VOICE
        set(value) { prefs.edit().putString(KEY_VOICE, value).apply() }

    /**
     * Synthesize [text] to [outputFile] (WAV). Returns true on success.
     */
    suspend fun synthesize(text: String, outputFile: File): Boolean =
        synthesize(text, voice, outputFile)

    suspend fun synthesize(text: String, voiceName: String, outputFile: File): Boolean =
        withContext(Dispatchers.IO) {
            val apiKey = prefs.getString(KEY_API_KEY, null)?.trim().orEmpty()
                // Fall back to the DJ's key — same Google AI credential.
                .ifEmpty {
                    appContext.getSharedPreferences(DJ_PREFS_NAME, Context.MODE_PRIVATE)
                        .getString(KEY_API_KEY, null)?.trim().orEmpty()
                }
            if (apiKey.isEmpty()) {
                KLog.w(TAG, "Gemini TTS: no API key")
                return@withContext false
            }
            // A little direction helps the voice sound like a DJ, not a reader.
            val directed = "Say in a warm, engaging radio DJ voice: $text"
            val body = JSONObject()
                .put("contents", JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(
                        JSONObject().put("text", directed)
                    ))
                ))
                .put("generationConfig", JSONObject()
                    .put("responseModalities", JSONArray().put("AUDIO"))
                    .put("speechConfig", JSONObject()
                        .put("voiceConfig", JSONObject()
                            .put("prebuiltVoiceConfig", JSONObject()
                                .put("voiceName", voiceName)
                            )
                        )
                    )
                )
                .toString()
            // Try TTS models in order; the 2.5 preview may be retired.
            for (model in TTS_MODELS) {
                try {
                    val request = Request.Builder()
                        .url("$TTS_URL_BASE/$model:generateContent")
                        .header("x-goog-api-key", apiKey)
                        .post(body.toRequestBody("application/json".toMediaType()))
                        .build()
                    httpClient.newCall(request).execute().use { response ->
                        val responseBody = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            KLog.w(TAG, "Gemini TTS ($model) failed: HTTP ${response.code} ${responseBody.take(200)}")
                            // Try the next model on any failure, not just 404:
                            // a 400 on one model shouldn't kill the fallback chain.
                            continue
                        }
                        val pcmBase64 = runCatching {
                            JSONObject(responseBody)
                                .getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getJSONObject("inlineData")
                                .getString("data")
                        }.getOrNull() ?: continue
                        val pcm = Base64.decode(pcmBase64, Base64.DEFAULT)
                        writeWav(outputFile, pcm)
                        return@withContext true
                    }
                } catch (e: Exception) {
                    KLog.w(TAG, "Gemini TTS ($model) failed", e)
                    continue
                }
            }
            return@withContext false
        }

    /**
     * Wrap 24kHz 16-bit mono PCM in a WAV header.
     */
    private fun writeWav(file: File, pcm: ByteArray) {
        val sampleRate = 24000
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = (channels * bitsPerSample / 8).toShort()
        val dataSize = pcm.size
        val totalSize = 36 + dataSize

        file.outputStream().buffered().use { out ->
            // RIFF header
            out.write("RIFF".toByteArray())
            out.write(intToBytes(totalSize))
            out.write("WAVE".toByteArray())
            // fmt chunk
            out.write("fmt ".toByteArray())
            out.write(intToBytes(16)) // chunk size
            out.write(shortToBytes(1)) // PCM
            out.write(shortToBytes(channels.toShort()))
            out.write(intToBytes(sampleRate))
            out.write(intToBytes(byteRate))
            out.write(shortToBytes(blockAlign))
            out.write(shortToBytes(bitsPerSample.toShort()))
            // data chunk
            out.write("data".toByteArray())
            out.write(intToBytes(dataSize))
            out.write(pcm)
        }
    }

    private fun intToBytes(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )

    private fun shortToBytes(value: Short): ByteArray = byteArrayOf(
        (value.toInt() and 0xFF).toByte(),
        ((value.toInt() shr 8) and 0xFF).toByte(),
    )

    companion object {
        private const val TAG = "GeminiTts"
        private const val PREFS_NAME = "gemini_tts"
        private const val DJ_PREFS_NAME = "ivor_music_ai_dj"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_VOICE = "tts_voice"
        private const val TTS_URL_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
        /** TTS models to try in order; the 2.5 preview is the fallback. */
        private val TTS_MODELS = listOf(
            "gemini-3.1-flash-tts-preview",
            "gemini-2.5-flash-preview-tts",
        )
        const val DEFAULT_VOICE = "Kore"

        /** Curated voices that suit a DJ. */
        val VOICES = listOf(
            "Kore" to "Warm and engaging",
            "Charon" to "Deep and smooth",
            "Puck" to "Playful and bright",
            "Zephyr" to "Bright and clear",
            "Fenrir" to "Smooth and relaxed",
        )
    }
}
