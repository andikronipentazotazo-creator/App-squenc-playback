package com.example

import com.example.audio.PannerDsp
import com.example.model.AutomationCurveType
import com.example.model.PanAutomationPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PannerDspTest {

    @Test
    fun monoConstantPowerPan_preservesEnergyAcrossSpectrum() {
        val (lFull, rFull) = PannerDsp.computeMonoPanGains(-100f)
        assertEquals(1.0f, lFull, 0.001f)
        assertEquals(0.0f, rFull, 0.001f)

        val (lCenter, rCenter) = PannerDsp.computeMonoPanGains(0f)
        assertEquals(lCenter, rCenter, 0.001f)
        assertEquals(1.0f, lCenter * lCenter + rCenter * rCenter, 0.001f)

        val (lRight, rRight) = PannerDsp.computeMonoPanGains(100f)
        assertEquals(0.0f, lRight, 0.001f)
        assertEquals(1.0f, rRight, 0.001f)

        val (l70, r70) = PannerDsp.computeMonoPanGains(-70f)
        assertEquals(1.0f, l70 * l70 + r70 * r70, 0.001f)
        assertTrue(l70 > r70)
    }

    @Test
    fun stereoPan_isolatesHardLeftAndRightWithoutClipping() {
        val (cL, cR) = PannerDsp.applyStereoPan(0.8f, -0.5f, 0f)
        assertEquals(0.8f, cL, 0.0001f)
        assertEquals(-0.5f, cR, 0.0001f)

        val (leftOnlyL, leftOnlyR) = PannerDsp.applyStereoPan(0.9f, 0.9f, -100f)
        assertEquals(0.0f, leftOnlyR, 0.0001f)
        assertTrue(leftOnlyL <= 1.0f)

        val (rightOnlyL, rightOnlyR) = PannerDsp.applyStereoPan(0.9f, 0.9f, 100f)
        assertEquals(0.0f, rightOnlyL, 0.0001f)
        assertTrue(rightOnlyR <= 1.0f)
    }

    @Test
    fun softLimiter_preventsDigitalClipping() {
        val limitedPos = PannerDsp.softLimit(2.4f)
        val limitedNeg = PannerDsp.softLimit(-2.4f)
        assertTrue(limitedPos < 1.0f && limitedPos > 0.85f)
        assertTrue(limitedNeg > -1.0f && limitedNeg < -0.85f)
    }

    @Test
    fun automationEvaluation_handlesLinearAndStepCurves() {
        val points = listOf(
            PanAutomationPoint("1", "voice_cue", bar = 1, beat = 1f, panValue = -100f, curveType = AutomationCurveType.LINEAR),
            PanAutomationPoint("2", "voice_cue", bar = 5, beat = 1f, panValue = 100f, curveType = AutomationCurveType.STEP),
            PanAutomationPoint("3", "voice_cue", bar = 9, beat = 1f, panValue = 0f, curveType = AutomationCurveType.LINEAR)
        )

        val midpoint = PannerDsp.evaluateAutomationPan(points, "voice_cue", currentBarPosition = 3.0f, beatsPerBar = 4, fallbackPan = 0f)
        assertEquals(0.0f, midpoint, 0.5f)

        val stepHold = PannerDsp.evaluateAutomationPan(points, "voice_cue", currentBarPosition = 7.0f, beatsPerBar = 4, fallbackPan = 0f)
        assertEquals(100.0f, stepHold, 0.01f)
    }
}
