package com.oiuy.volumecontrol.hybrid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridMathTest {
    private val curve = floatArrayOf(Float.NEGATIVE_INFINITY, -40f, -30f, -24f, -18f, -12f, -6f, 0f)
    private val max = 7

    @Test fun wholeLevelsUseNoEffect() {
        for (i in 0..max) assertEquals(HybridMath.Plan(i, 0f), HybridMath.plan(i.toFloat(), max, curve, -30f, true))
    }

    @Test fun halfwayIsHalfTheStepBelowUpperLevel() {
        assertEquals(HybridMath.Plan(5, -3f), HybridMath.plan(4.5f, max, curve, -30f, true))
        assertEquals(HybridMath.Plan(2, -5f), HybridMath.plan(1.5f, max, curve, -30f, true))
    }

    @Test fun loudnessIsMonotonicAcrossWholeRange() {
        var prev = Float.NEGATIVE_INFINITY
        var l = 1f
        while (l <= max.toFloat()) {
            val p = HybridMath.plan(l, max, curve, -30f, true)
            val out = curve[p.streamIndex] + p.attenuationDb
            assertTrue("level $l gave $out after $prev", out >= prev - 0.051f)
            prev = out
            l += 0.01f
        }
    }

    @Test fun continuousAtStepBoundaries() {
        val below = HybridMath.plan(3.999f - 1e-3f, max, curve, -30f, true)
        assertEquals(-18f, curve[below.streamIndex] + below.attenuationDb, 0.1f)
        val above = HybridMath.plan(4.002f, max, curve, -30f, true)
        assertEquals(-18f, curve[above.streamIndex] + above.attenuationDb, 0.1f)
    }

    @Test fun bottomGapFadesFromFloor() {
        val p = HybridMath.plan(0.5f, max, curve, -30f, true)
        assertEquals(1, p.streamIndex)
        assertEquals(-15f, p.attenuationDb, 0.01f)
    }

    @Test fun attenuationClampedToBackendFloorAndNeverPositive() {
        val p = HybridMath.plan(0.1f, max, curve, -15f, true)
        assertEquals(-15f, p.attenuationDb, 0.001f)
        for (k in 0..700) assertTrue(HybridMath.plan(k / 100f, max, curve, -30f, true).attenuationDb <= 0f)
    }

    @Test fun noEffectFallsBackToNearestWholeLevel() {
        assertEquals(HybridMath.Plan(4, 0f), HybridMath.plan(4.4f, max, curve, -30f, false))
        assertEquals(HybridMath.Plan(5, 0f), HybridMath.plan(4.6f, max, curve, -30f, false))
    }

    @Test fun flatBluetoothCurveIsNotUsable() {
        assertFalse(HybridMath.curveIsUsable(FloatArray(16) { 0f }))
        assertFalse(HybridMath.curveIsUsable(floatArrayOf(0f)))
        assertTrue(HybridMath.curveIsUsable(curve))
    }

    @Test fun estimatedCurveIsEvenAndEndsAtZero() {
        val e = HybridMath.estimatedCurve(15)
        assertEquals(0f, e[15], 0f)
        assertEquals(-2.5f, e[14], 0f)
        assertTrue(HybridMath.curveIsUsable(e))
        assertEquals(HybridMath.Plan(15, -1.0f), HybridMath.plan(14.6f, 15, e, -30f, true))
    }

    @Test fun writeOrderAvoidsOvershoot() {
        assertTrue(HybridMath.effectBeforeStream(4, 5))
        assertFalse(HybridMath.effectBeforeStream(5, 4))
        assertFalse(HybridMath.effectBeforeStream(5, 5))
    }

    @Test fun formatsDb() {
        assertEquals("\u22121.8 dB", HybridMath.formatDb(-1.8f))
        assertEquals("0.0 dB", HybridMath.formatDb(-0.01f))
    }
}
