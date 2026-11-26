package com.example.audio

import com.example.model.AutomationCurveType
import com.example.model.PanAutomationPoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Pure DSP math for constant-power stereo panning, anti-clipping limiting,
 * gain/dB conversion, and timeline automation interpolation.
 */
object PannerDsp {

    /**
     * Constant-power (-3dB center law) panning gains for mono sources.
     * At pan = 0 (Center): left = 0.7071, right = 0.7071 (left^2 + right^2 = 1.0).
     * At pan = -100 (Full Left): left = 1.0, right = 0.0.
     * At pan = +100 (Full Right): left = 0.0, right = 1.0.
     */
    fun computeMonoPanGains(pan: Float): Pair<Float, Float> {
        val clamped = pan.coerceIn(-100f, 100f)
        val normalized = (clamped + 100f) / 200f // 0.0 .. 1.0
        val angle = normalized * (PI / 2.0)
        val left = cos(angle).toFloat()
        val right = sin(angle).toFloat()
        return left to right
    }

    /**
     * True stereo balance + controlled cross-feed panner for stereo sources
     * (Backing Track, MIDI Synth, Stereo Tracks).
     * Guarantees:
     * - Zero phase cancellation or loudness jump at Center (pan = 0 -> exact unity L/R)
     * - Zero clipping at extreme L/R panning
     * - Full signal audibility on the active side when hard-panned
     */
    fun applyStereoPan(inL: Float, inR: Float, pan: Float): Pair<Float, Float> {
        val p = pan.coerceIn(-100f, 100f) / 100f // -1.0 .. +1.0
        if (abs(p) < 0.0005f) {
            return inL to inR
        }
        return if (p < 0f) {
            val amount = -p // 0..1 towards Left
            val cross = amount * 0.35f
            val norm = 1f / (1f + cross)
            val outL = (inL + inR * cross) * norm
            val rightAtten = cos(amount * (PI / 2.0)).toFloat()
            val outR = inR * rightAtten
            outL to outR
        } else {
            val amount = p // 0..1 towards Right
            val cross = amount * 0.35f
            val norm = 1f / (1f + cross)
            val leftAtten = cos(amount * (PI / 2.0)).toFloat()
            val outL = inL * leftAtten
            val outR = (inR + inL * cross) * norm
            outL to outR
        }
    }

    /**
     * Soft-knee analog-style master limiter to prevent harsh digital clipping
     * when multiple tracks and panned sources sum together.
     */
    fun softLimit(sample: Float): Float {
        val absVal = abs(sample)
        if (absVal <= 0.85f) return sample
        val sign = if (sample >= 0f) 1f else -1f
        val excess = (absVal - 0.85f) / 0.15f
        val compressed = 0.85f + 0.145f * tanh(excess)
        return sign * compressed.coerceAtMost(0.995f)
    }

    /**
     * Converts linear fader position [0f..1.41f] + trimDb [-12f..12f] to linear gain multiplier.
     * Fader 1.0 = 0 dB, 1.41 = +3 dB, 0.0 = -Inf (silent).
     */
    fun computeEffectiveGain(faderLevel: Float, trimDb: Float): Float {
        if (faderLevel <= 0.001f) return 0f
        val trimLinear = 10f.pow(trimDb.coerceIn(-12f, 12f) / 20f)
        // Smooth audio taper: fader^1.6 gives natural DAW resolution around 0 dB
        val faderGain = faderLevel.coerceIn(0f, 1.45f).pow(1.4f)
        return faderGain * trimLinear
    }

    /**
     * Converts linear gain to human-readable dB string.
     */
    fun gainToDbLabel(faderLevel: Float): String {
        if (faderLevel <= 0.005f) return "-∞ dB"
        val gain = faderLevel.pow(1.4f)
        val db = 20f * kotlin.math.log10(gain.coerceAtLeast(1e-4f))
        return if (db >= 0f) {
            String.format("+%.1f dB", db)
        } else {
            String.format("%.1f dB", db)
        }
    }

    /**
     * Formats pan (-100..+100) into clear DAW readout like "L 100", "C", "R 70".
     */
    fun formatPanLabel(pan: Float): String {
        val rounded = kotlin.math.round(pan).toInt().coerceIn(-100, 100)
        return when {
            rounded == 0 -> "C (0)"
            rounded < 0 -> "L ${-rounded}"
            else -> "R $rounded"
        }
    }

    /**
     * Evaluates timeline pan automation at a given fractional bar position
     * (e.g. Bar 9, Beat 1.0 -> 9.0f; Bar 9, Beat 3.0 in 4/4 -> 9.5f).
     */
    fun evaluateAutomationPan(
        points: List<PanAutomationPoint>,
        targetId: String,
        currentBarPosition: Float,
        beatsPerBar: Int,
        fallbackPan: Float
    ): Float {
        val lanePoints = points
            .asSequence()
            .filter { it.targetId == targetId }
            .sortedBy { pointPosition(it, beatsPerBar) }
            .toList()

        if (lanePoints.isEmpty()) return fallbackPan

        val firstPos = pointPosition(lanePoints.first(), beatsPerBar)
        if (currentBarPosition <= firstPos) {
            return lanePoints.first().panValue.coerceIn(-100f, 100f)
        }

        val lastPos = pointPosition(lanePoints.last(), beatsPerBar)
        if (currentBarPosition >= lastPos) {
            return lanePoints.last().panValue.coerceIn(-100f, 100f)
        }

        for (i in 0 until lanePoints.size - 1) {
            val a = lanePoints[i]
            val b = lanePoints[i + 1]
            val posA = pointPosition(a, beatsPerBar)
            val posB = pointPosition(b, beatsPerBar)
            if (currentBarPosition in posA..posB) {
                return if (a.curveType == AutomationCurveType.STEP || posB <= posA) {
                    a.panValue.coerceIn(-100f, 100f)
                } else {
                    val t = ((currentBarPosition - posA) / (posB - posA)).coerceIn(0f, 1f)
                    (a.panValue + (b.panValue - a.panValue) * t).coerceIn(-100f, 100f)
                }
            }
        }
        return fallbackPan
    }

    fun pointPosition(point: PanAutomationPoint, beatsPerBar: Int): Float {
        val safeBeats = beatsPerBar.coerceAtLeast(1)
        return point.bar.toFloat() + ((point.beat - 1f).coerceAtLeast(0f) / safeBeats.toFloat())
    }
}
