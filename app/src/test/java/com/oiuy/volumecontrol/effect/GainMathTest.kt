package com.oiuy.volumecontrol.effect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GainMathTest {
    @Test
    fun fullTurnIsSixDb() {
        assertEquals(6f, GainMath.dbDeltaFromDegrees(360f), 1e-4f)
        assertEquals(-0.1f, GainMath.dbDeltaFromDegrees(-6f), 1e-4f)
    }

    @Test
    fun clampNeverBoostsAndRespectsFloor() {
        assertEquals(0f, GainMath.clampAttenuation(3f, -30f), 0f)
        assertEquals(-30f, GainMath.clampAttenuation(-45f, -30f), 1e-4f)
        assertEquals(-15f, GainMath.clampAttenuation(-20f, -15f), 1e-4f)
        assertEquals(-6.3f, GainMath.clampAttenuation(-6.27f, -30f), 1e-4f)
        assertEquals(0f, GainMath.clampAttenuation(Float.NaN, -30f), 0f)
        // Positive floor is treated as 0 (no boost range).
        assertEquals(0f, GainMath.clampAttenuation(-5f, 2f), 0f)
        assertTrue(GainMath.clampAttenuation(-0.01f, -30f).let { it == 0f && 1f / it > 0 })
    }

    @Test
    fun effectiveFloorUsesShallowerOfAppAndBackend() {
        assertEquals(-30f, GainMath.effectiveFloorDb(null), 0f)
        assertEquals(-15f, GainMath.effectiveFloorDb(-15f), 0f)
        assertEquals(-30f, GainMath.effectiveFloorDb(-96f), 0f)
        assertEquals(0f, GainMath.effectiveFloorDb(3f), 0f)
    }

    @Test
    fun rotationHardStopsAtZeroAndFloor() {
        assertEquals(0f, GainMath.clampRotationDegrees(10f, 0f, -30f), 0f)
        assertEquals(0f, GainMath.clampRotationDegrees(-10f, -30f, -30f), 0f)
        assertEquals(-10f, GainMath.clampRotationDegrees(-10f, 0f, -30f), 1e-4f)
        // 0.5 dB of room = 30 degrees
        assertEquals(30f, GainMath.clampRotationDegrees(90f, -0.5f, -30f), 1e-3f)
        assertEquals(-30f, GainMath.clampRotationDegrees(-90f, -14.5f, -15f), 1e-3f)
    }

    @Test
    fun detentsCountWholeDbCrossings() {
        assertEquals(0, GainMath.detentsCrossed(-1.2f, -1.8f))
        assertEquals(1, GainMath.detentsCrossed(-1.8f, -2.1f))
        assertEquals(1, GainMath.detentsCrossed(-2.1f, -1.9f))
        assertEquals(3, GainMath.detentsCrossed(-0.5f, -3.5f))
    }

    @Test
    fun formatting() {
        assertEquals("0.0 dB", GainMath.formatDb(0f))
        assertEquals("0.0 dB", GainMath.formatDb(-0.04f))
        assertEquals("\u22126.0 dB", GainMath.formatDb(-6f))
        assertEquals("\u221212.3 dB", GainMath.formatDb(-12.3f))
    }

    @Test
    fun ringAndKnob() {
        assertEquals(360f, GainMath.ringSweepDegrees(0f, -30f), 1e-3f)
        assertEquals(0f, GainMath.ringSweepDegrees(-30f, -30f), 1e-3f)
        assertEquals(180f, GainMath.ringSweepDegrees(-15f, -30f), 1e-3f)
        assertEquals(0f, GainMath.knobDegrees(0f), 1e-3f)
        assertEquals(300f, GainMath.knobDegrees(-1f), 1e-3f)
    }

    @Test
    fun eqBandLevelClampsToRangeAndNoBoost() {
        assertEquals((-600).toShort(), GainMath.eqBandLevelMillibels(-6f, -1500, 1500))
        assertEquals((-1500).toShort(), GainMath.eqBandLevelMillibels(-25f, -1500, 1500))
        assertEquals(0.toShort(), GainMath.eqBandLevelMillibels(4f, -1500, 1500))
        assertEquals((-1230).toShort(), GainMath.eqBandLevelMillibels(-12.3f, -1500, 1500))
        // Swapped/odd range still safe.
        assertEquals((-900).toShort(), GainMath.eqBandLevelMillibels(-12f, 900, -900))
    }
}
