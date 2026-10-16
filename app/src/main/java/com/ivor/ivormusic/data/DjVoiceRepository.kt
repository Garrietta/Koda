package com.ivor.ivormusic.data

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/**
 * The DJ's voice: synthesizes the DJ's spoken intro lines to audio files
 * with the device's text-to-speech engine, so they can be interleaved with
 * the songs in the playback queue (Spotify AI DJ style).
 *
 * Synthesis is serialized through a mutex because the utterance listener is
 * per-engine; callers can still run it from any dispatcher.
 */
class DjVoiceRepository(context: Context) {

    private val appContext = context.applicationContext
    private val initLock = Mutex()
    private val synthLock = Mutex()
    private var tts: TextToSpeech? = null
    private var ready = false

    private suspend fun ensureReady(): Boolean {
        if (ready) return true
        return initLock.withLock {
            if (ready) return true
            suspendCancellableCoroutine { cont ->
                try {
                    tts = TextToSpeech(appContext) { status ->
                        if (status == TextToSpeech.SUCCESS) {
                            val engine = tts
                            val langStatus = engine?.setLanguage(Locale.US)
                                ?: TextToSpeech.LANG_MISSING_DATA
                            if (langStatus == TextToSpeech.LANG_MISSING_DATA ||
                                langStatus == TextToSpeech.LANG_NOT_SUPPORTED
                            ) {
                                KLog.w(TAG, "TTS language not available")
                                cont.resume(false)
                                return@TextToSpeech
                            }
                            // A touch slower than conversational pace reads as
                            // a DJ leaning into the mic.
                            engine?.setSpeechRate(0.95f)
                            ready = true
                            cont.resume(true)
                        } else {
                            KLog.w(TAG, "TTS init failed: $status")
                            cont.resume(false)
                        }
                    }
                } catch (e: Exception) {
                    KLog.w(TAG, "TTS init threw", e)
                    cont.resume(false)
                }
            }
        }
    }

    /**
     * Synthesize [text] to [file]. Returns true when the file was written.
     */
    suspend fun synthesize(text: String, file: File): Boolean =
        withContext(Dispatchers.IO) {
            synthLock.withLock {
                if (!ensureReady()) return@withContext false
                val engine = tts ?: return@withContext false
                try {
                    file.parentFile?.mkdirs()
                    if (file.exists()) file.delete()
                } catch (_: Exception) { }
                suspendCancellableCoroutine { cont ->
                    val utteranceId = UUID.randomUUID().toString()
                    engine.setOnUtteranceProgressListener(
                        object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) = Unit
                            override fun onDone(id: String?) {
                                if (id == utteranceId) cont.resume(true)
                            }
                            @Deprecated("Deprecated in Java")
                            override fun onError(id: String?) {
                                if (id == utteranceId) cont.resume(false)
                            }
                            override fun onError(id: String?, errorCode: Int) {
                                if (id == utteranceId) cont.resume(false)
                            }
                        }
                    )
                    val result = engine.synthesizeToFile(text, Bundle(), file, utteranceId)
                    if (result != TextToSpeech.SUCCESS) cont.resume(false)
                }
            }
        }

    fun shutdown() {
        try {
            tts?.shutdown()
        } catch (_: Exception) { }
        tts = null
        ready = false
    }

    companion object {
        private const val TAG = "DjVoice"
    }
}
