package com.oiuy.volumecontrol.volume

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DialMathTest {
    @Test
    fun fullTurnRaisesTenPercentOfMax() {
        val max = 100
        val delta = DialMath.volumeDeltaFromDegrees(360f, max)
        assertEquals(10f, delta, 0.001f)
    }

    @Test
    fun halfTurnRaisesFivePercent() {
        val max = 50
        val delta = DialMath.volumeDeltaFromDegrees(180f, max)
        assertEquals(2.5f, delta, 0.001f)
    }

    @Test
    fun counterclockwiseLowersVolume() {
        val delta = DialMath.volumeDeltaFromDegrees(-360f, 100)
        assertEquals(-10f, delta, 0.001f)
    }

    @Test
    fun clampAndIndex() {
        assertEquals(0f, DialMath.clampFractional(-3f, 25), 0.001f)
        assertEquals(25f, DialMath.clampFractional(30f, 25), 0.001f)
        assertEquals(10, DialMath.toStreamIndex(9.6f, 25))
        assertEquals(0, DialMath.toStreamIndex(-1f, 25))
    }

    @Test
    fun percentStepTenPercent() {
        assertEquals(15, DialMath.applyPercentStep(10, 0.10f, 50))
        assertEquals(5, DialMath.applyPercentStep(10, -0.10f, 50))
    }

    @Test
    fun shortestAngleDeltaWraps() {
        assertEquals(20f, DialMath.shortestAngleDelta(170f, -170f), 0.001f)
        assertEquals(-20f, DialMath.shortestAngleDelta(-170f, 170f), 0.001f)
    }

    @Test
    fun percentOf() {
        assertEquals(50, DialMath.percentOf(25, 50))
        assertEquals(0, DialMath.percentOf(0, 0))
        assertTrue(DialMath.percentOf(3, 7) in 42..43)
    }

    @Test
    fun fractionalPercentOf() {
        assertEquals(50.0f, DialMath.fractionalPercentOf(25f, 50), 0.001f)
        assertEquals(0.0f, DialMath.fractionalPercentOf(0f, 0), 0.001f)
        assertEquals(47.3f, DialMath.fractionalPercentOf(7.095f, 15), 0.05f)
        assertEquals(100.0f, DialMath.fractionalPercentOf(20f, 15), 0.001f)
        assertEquals(0.0f, DialMath.fractionalPercentOf(-1f, 15), 0.001f)
    }

    @Test
    fun tickConstants() {
        assertEquals(100, DialMath.TICKS_PER_TURN)
        assertEquals(3.6f, DialMath.DEGREES_PER_TICK, 0.0001f)
        assertEquals(10, DialMath.MAJOR_TICK_EVERY)
        assertEquals(10f, DialMath.DISPLAY_PERCENT_PER_FULL_TURN, 0.0001f)
    }

    @Test
    fun tickIndexClockwise() {
        assertEquals(0, DialMath.tickIndex(0f))
        assertEquals(0, DialMath.tickIndex(3.5f))
        assertEquals(1, DialMath.tickIndex(3.7f))
        assertEquals(100, DialMath.tickIndex(360.1f))
        assertEquals(250, DialMath.tickIndex(900.5f))
    }

    @Test
    fun tickIndexCounterclockwiseUsesFloor() {
        assertEquals(-1, DialMath.tickIndex(-0.1f))
        assertEquals(-1, DialMath.tickIndex(-3.5f))
        assertEquals(-2, DialMath.tickIndex(-3.7f))
        assertEquals(-101, DialMath.tickIndex(-360.1f))
    }

    @Test
    fun accumulatedRotationAcrossWrapCountsTicks() {
        // Finger moves 170 -> -170 (a 20 degree clockwise move across the atan2 seam).
        var acc = 170f
        val before = DialMath.tickIndex(acc)
        acc += DialMath.shortestAngleDelta(170f, -170f)
        val after = DialMath.tickIndex(acc)
        assertEquals(5, DialMath.ticksCrossed(before, after))
    }

    @Test
    fun hundredTicksEqualTenPercent() {
        assertEquals(10f, DialMath.volumeDeltaFromTickSteps(100, 100), 0.001f)
        assertEquals(0.1f, DialMath.volumeDeltaFromTickSteps(1, 100), 0.0001f)
        assertEquals(-0.1f, DialMath.volumeDeltaFromTickSteps(-1, 100), 0.0001f)
    }

    @Test
    fun tickIndexFromPercent() {
        assertEquals(0, DialMath.tickIndexFromPercent(0f))
        assertEquals(473, DialMath.tickIndexFromPercent(47.3f))
        assertEquals(1000, DialMath.tickIndexFromPercent(100f))
        assertEquals(100, DialMath.tickIndexFromPercent(10f))
        assertEquals(99, DialMath.tickIndexFromPercent(9.9f))
    }

    @Test
    fun indicatorDegreesAtTenPercentLandmarks() {
        assertEquals(0f, DialMath.indicatorDegreesFromPercent(0f), 0.001f)
        assertEquals(0f, DialMath.indicatorDegreesFromPercent(10f), 0.001f)
        assertEquals(0f, DialMath.indicatorDegreesFromPercent(20f), 0.001f)
        assertEquals(0f, DialMath.indicatorDegreesFromPercent(50f), 0.001f)
        assertEquals(0f, DialMath.indicatorDegreesFromPercent(100f), 0.001f)
    }

    @Test
    fun indicatorDegreesWithinDecade() {
        // 5% is halfway through a decade → 180°
        assertEquals(180f, DialMath.indicatorDegreesFromPercent(5f), 0.001f)
        assertEquals(180f, DialMath.indicatorDegreesFromPercent(15f), 0.001f)
        // one fine tick (0.1%) past a landmark → 3.6°
        assertEquals(3.6f, DialMath.indicatorDegreesFromPercent(10.1f), 0.001f)
        assertEquals(356.4f, DialMath.indicatorDegreesFromPercent(9.9f), 0.05f)
    }

    @Test
    fun clampRotationAtZeroBlocksCounterclockwise() {
        assertEquals(0f, DialMath.clampRotationDegrees(-90f, 0f), 0.001f)
        assertEquals(0f, DialMath.clampRotationDegrees(-1f, 0f), 0.001f)
        // Clockwise from 0 is allowed
        assertEquals(90f, DialMath.clampRotationDegrees(90f, 0f), 0.001f)
    }

    @Test
    fun clampRotationAtHundredBlocksClockwise() {
        assertEquals(0f, DialMath.clampRotationDegrees(90f, 100f), 0.001f)
        assertEquals(0f, DialMath.clampRotationDegrees(1f, 100f), 0.001f)
        // Counterclockwise from 100 is allowed
        assertEquals(-90f, DialMath.clampRotationDegrees(-90f, 100f), 0.001f)
    }

    @Test
    fun clampRotationPartialNearLimit() {
        // 0.5% room upward → max 18° (since 360° = 10%)
        assertEquals(18f, DialMath.clampRotationDegrees(90f, 99.5f), 0.001f)
        // 0.5% room downward → max -18°
        assertEquals(-18f, DialMath.clampRotationDegrees(-90f, 0.5f), 0.001f)
        // Within room, unchanged
        assertEquals(36f, DialMath.clampRotationDegrees(36f, 50f), 0.001f)
    }
}
