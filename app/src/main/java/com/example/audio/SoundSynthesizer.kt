package com.example.audio

import com.example.model.MetronomeSoundType
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generates high-definition, zero-latency studio PCM samples for:
 * - 5 distinct metronome sound engines (Woodblock, Digital, Cowbell, Hi-Hat, Rimshot)
 * - Harmonic vocal-cue / fallback cue chimes when TTS is unavailable
 */
object SoundSynthesizer {

    fun buildMetronomeBank(sampleRate: Int): Map<MetronomeSoundType, Pair<FloatArray, FloatArray>> {
        return MetronomeSoundType.entries.associateWith { type ->
            val accent = generateMetronomeSample(type, isAccent = true, sampleRate = sampleRate)
            val regular = generateMetronomeSample(type, isAccent = false, sampleRate = sampleRate)
            accent to regular
        }
    }

    private fun generateMetronomeSample(
        type: MetronomeSoundType,
        isAccent: Boolean,
        sampleRate: Int
    ): FloatArray {
        val durationSec = when (type) {
            MetronomeSoundType.COWBELL -> 0.14f
            MetronomeSoundType.HI_HAT -> 0.09f
            else -> 0.085f
        }
        val length = (sampleRate * durationSec).toInt()
        val out = FloatArray(length)
        val rng = Random(if (isAccent) 101 else 202)
        var lastNoise = 0f

        for (i in 0 until length) {
            val t = i.toFloat() / sampleRate
            val env = exp(-t * when (type) {
                MetronomeSoundType.COWBELL -> 26f
                MetronomeSoundType.HI_HAT -> 46f
                MetronomeSoundType.DIGITAL_SINE -> 55f
                MetronomeSoundType.RIMSHOT -> 48f
                MetronomeSoundType.WOODBLOCK -> 52f
            })
            val gain = if (isAccent) 0.92f else 0.65f
            val sample = when (type) {
                MetronomeSoundType.WOODBLOCK -> {
                    val f1 = if (isAccent) 1046f else 784f
                    val f2 = f1 * 1.48f
                    val body = 0.72f * sin(2f * PI.toFloat() * f1 * t) +
                        0.28f * sin(2f * PI.toFloat() * f2 * t)
                    body * env * gain
                }
                MetronomeSoundType.DIGITAL_SINE -> {
                    val f = if (isAccent) 1560f else 1040f
                    sin(2f * PI.toFloat() * f * t) * env * gain
                }
                MetronomeSoundType.COWBELL -> {
                    val f1 = if (isAccent) 587f else 540f
                    val f2 = if (isAccent) 845f else 800f
                    val s1 = if (sin(2f * PI.toFloat() * f1 * t) >= 0f) 0.5f else -0.5f
                    val s2 = if (sin(2f * PI.toFloat() * f2 * t) >= 0f) 0.5f else -0.5f
                    (s1 + s2) * 0.5f * env * gain
                }
                MetronomeSoundType.HI_HAT -> {
                    val white = rng.nextFloat() * 2f - 1f
                    val hp = white - lastNoise * 0.85f
                    lastNoise = white
                    val metallic = sin(2f * PI.toFloat() * (if (isAccent) 6200f else 5400f) * t)
                    (hp * 0.7f + metallic * 0.3f) * env * gain
                }
                MetronomeSoundType.RIMSHOT -> {
                    val fBody = if (isAccent) 440f else 360f
                    val snap = (rng.nextFloat() * 2f - 1f) * exp(-t * 160f)
                    val knock = sin(2f * PI.toFloat() * fBody * t)
                    (knock * 0.65f + snap * 0.45f) * env * gain
                }
            }
            // Anti-click 1.5ms fade-in
            val attack = (i / (sampleRate * 0.0015f)).coerceIn(0f, 1f)
            out[i] = (sample * attack).coerceIn(-0.98f, 0.98f)
        }
        return out
    }

    /**
     * Synthesizes a clean multi-syllable vocal-formant / chime fallback cue when
     * Android TTS voice files aren't available yet or on headless devices.
     */
    fun generateFallbackCueSample(
        phrase: String,
        isStageStyle: Boolean,
        isChimeStyle: Boolean,
        sampleRate: Int
    ): FloatArray {
        val durationSec = 0.42f
        val length = (sampleRate * durationSec).toInt()
        val out = FloatArray(length)
        val baseFreq = when {
            phrase.contains("Intro", ignoreCase = true) -> 523.25f
            phrase.contains("Verse", ignoreCase = true) -> 587.33f
            phrase.contains("Chorus", ignoreCase = true) -> 659.25f
            phrase.contains("Bridge", ignoreCase = true) -> 698.46f
            phrase.contains("Ending", ignoreCase = true) -> 783.99f
            phrase.contains("1") || phrase.contains("One", ignoreCase = true) -> 440f
            phrase.contains("2") || phrase.contains("Two", ignoreCase = true) -> 493.88f
            phrase.contains("3") || phrase.contains("Three", ignoreCase = true) -> 554.37f
            phrase.contains("4") || phrase.contains("Four", ignoreCase = true) -> 659.25f
            else -> 622.25f
        }
        val pitchShift = if (isStageStyle) 0.85f else 1.0f
        val f1 = baseFreq * pitchShift
        val f2 = f1 * 1.5f

        for (i in 0 until length) {
            val t = i.toFloat() / sampleRate
            // Two-syllable cadence envelope for spoken-like clarity
            val syllable1 = exp(-((t - 0.08f) * 16f) * ((t - 0.08f) * 16f))
            val syllable2 = if (isChimeStyle) {
                0.7f * exp(-((t - 0.24f) * 12f) * ((t - 0.24f) * 12f))
            } else {
                0.85f * exp(-((t - 0.23f) * 15f) * ((t - 0.23f) * 15f))
            }
            val env = (syllable1 + syllable2).coerceAtMost(1f)
            val tone = 0.65f * sin(2f * PI.toFloat() * f1 * t) +
                0.25f * sin(2f * PI.toFloat() * f2 * t) +
                0.10f * sin(2f * PI.toFloat() * f1 * 2f * t)
            val attack = (i / (sampleRate * 0.005f)).coerceIn(0f, 1f)
            out[i] = (tone * env * attack * 0.72f).coerceIn(-0.95f, 0.95f)
        }
        return out
    }
}
