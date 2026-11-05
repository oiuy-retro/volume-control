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
}
