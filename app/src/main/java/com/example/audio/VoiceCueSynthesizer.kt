package com.example.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Generates real spoken WAV audio clips via Android TextToSpeech.synthesizeToFile
 * and decodes them into float PCM buffers for sample-accurate DSP panning.
 * Immediately provides synthesized chime/formant fallback clips if TTS is unavailable.
 */
class VoiceCueSynthesizer(
    private val context: Context,
    private val sampleRate: Int
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clipCache = ConcurrentHashMap<String, FloatArray>()
    private val pendingListeners = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private var tts: TextToSpeech? = null

    @Volatile
    private var isTtsReady = false

    init {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.US
                    tts?.setSpeechRate(1.12f)
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            utteranceId?.let { pendingListeners.remove(it)?.complete(true) }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            utteranceId?.let { pendingListeners.remove(it)?.complete(false) }
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            utteranceId?.let { pendingListeners.remove(it)?.complete(false) }
                        }
                    })
                    isTtsReady = true
                    prewarmDefaultCues()
                }
            }
        } catch (_: Exception) {
            isTtsReady = false
        }
    }

    private fun prewarmDefaultCues() {
        val phrases = listOf(
            "Intro", "Verse", "Chorus", "Bridge", "Ending",
            "One", "Two", "Three", "Four"
        )
        scope.launch {
            phrases.forEach { phrase ->
                ensureSpokenClip(phrase, pitch = 1.0f, cacheKey = cacheKey(phrase, "voice_studio"))
                ensureSpokenClip(phrase, pitch = 0.86f, cacheKey = cacheKey(phrase, "voice_stage"))
            }
        }
    }

    fun getClip(phrase: String, profileId: String): FloatArray {
        val key = cacheKey(phrase, profileId)
        clipCache[key]?.let { return it }

        val fallback = SoundSynthesizer.generateFallbackCueSample(
            phrase = phrase,
            isStageStyle = profileId == "voice_stage",
            isChimeStyle = profileId == "chime_cue",
            sampleRate = sampleRate
        )
        clipCache[key] = fallback

        if (isTtsReady) {
            scope.launch {
                val pitch = when (profileId) {
                    "voice_stage" -> 0.85f
                    "chime_cue" -> 1.12f
                    else -> 1.0f
                }
                ensureSpokenClip(phrase, pitch, key)
            }
        }
        return fallback
    }

    fun registerCustomCueClip(profileId: String, pcm: DecodedPcmAudio) {
        // Mix custom WAV to mono float array for crisp directional panning
        val mono = FloatArray(pcm.left.size) { i ->
            if (pcm.isStereo) 0.5f * (pcm.left[i] + pcm.right[i]) else pcm.left[i]
        }
        clipCache["custom_$profileId"] = mono
    }

    fun getCustomProfileClip(profileId: String): FloatArray? = clipCache["custom_$profileId"]

    private suspend fun ensureSpokenClip(phrase: String, pitch: Float, cacheKey: String) {
        val engine = tts ?: return
        if (!isTtsReady) return
        withContext(Dispatchers.IO) {
            try {
                val outFile = File(context.cacheDir, "cue_${cacheKey.hashCode()}.wav")
                val utteranceId = "utt_${cacheKey}_${System.nanoTime()}"
                val deferred = CompletableDeferred<Boolean>()
                pendingListeners[utteranceId] = deferred

                engine.setPitch(pitch)
                val params = Bundle()
                val res = engine.synthesizeToFile(phrase, params, outFile, utteranceId)
                if (res == TextToSpeech.SUCCESS) {
                    val ok = deferred.await()
                    if (ok && outFile.exists() && outFile.length() > 44) {
                        outFile.inputStream().use { stream ->
                            val decoded = WavDecoder.decodeFromStream(stream, sampleRate)
                            if (decoded != null && decoded.left.isNotEmpty()) {
                                val mono = FloatArray(decoded.left.size) { idx ->
                                    if (decoded.isStereo) {
                                        0.5f * (decoded.left[idx] + decoded.right[idx])
                                    } else {
                                        decoded.left[idx]
                                    }
                                }
                                clipCache[cacheKey] = normalizePeak(mono, targetPeak = 0.88f)
                            }
                        }
                    }
                } else {
                    pendingListeners.remove(utteranceId)
                }
            } catch (_: Exception) {
                // Keep fallback clip
            }
        }
    }

    private fun normalizePeak(samples: FloatArray, targetPeak: Float): FloatArray {
        var maxAbs = 0f
        for (s in samples) {
            val a = kotlin.math.abs(s)
            if (a > maxAbs) maxAbs = a
        }
        if (maxAbs < 0.01f) return samples
        val scale = targetPeak / maxAbs
        return FloatArray(samples.size) { i -> (samples[i] * scale).coerceIn(-0.98f, 0.98f) }
    }

    private fun cacheKey(phrase: String, profileId: String): String =
        "${profileId}_${phrase.trim().lowercase(Locale.US)}"

    fun release() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
    }
}
