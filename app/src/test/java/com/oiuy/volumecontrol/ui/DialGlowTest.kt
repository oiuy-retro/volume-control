package com.oiuy.volumecontrol.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DialGlowTest {
    private val eps = 1e-4f

    @Test
    fun angularDistanceTakesTheShortWayRound() {
        assertEquals(0f, DialGlow.angularDistance(10f, 10f), eps)
        assertEquals(20f, DialGlow.angularDistance(350f, 10f), eps)
        assertEquals(20f, DialGlow.angularDistance(10f, 350f), eps)
        assertEquals(180f, DialGlow.angularDistance(0f, 180f), eps)
        assertEquals(90f, DialGlow.angularDistance(-45f, 405f), eps)
    }

    @Test
    fun falloffIsFullAtTheSourceAndZeroAtReach() {
        assertEquals(1f, DialGlow.falloff(0f, 50f), eps)
        assertEquals(0.5f, DialGlow.falloff(25f, 50f), eps)
        assertEquals(0f, DialGlow.falloff(50f, 50f), eps)
        assertEquals(0f, DialGlow.falloff(80f, 50f), eps)
        assertEquals(0f, DialGlow.falloff(1f, 0f), eps)
    }

    @Test
    fun falloffDecreasesWithDistance() {
        var previous = 2f
        var d = 0f
        while (d <= 120f) {
            val f = DialGlow.falloff(d, DialGlow.REVEAL_REACH_DEGREES)
            assertTrue("falloff rose at $d", f <= previous + eps)
            assertTrue(f in 0f..1f)
            previous = f
            d += 0.5f
        }
    }

    @Test
    fun revealPeaksAtThePointerAndStaysSubtle() {
        assertEquals(DialGlow.REVEAL_MAX, DialGlow.reveal(120f, 120f), eps)
        assertTrue(DialGlow.REVEAL_MAX < 1f)
        for (deg in 0 until 360) {
            val r = DialGlow.reveal(deg.toFloat(), 0f)
            assertTrue(r in 0f..DialGlow.REVEAL_MAX)
        }
    }

    @Test
    fun revealIsWideAndSoft() {
        // Half strength at half the reach; still over half lit five units (36 degrees) away.
        assertEquals(DialGlow.REVEAL_MAX / 2f, DialGlow.reveal(DialGlow.REVEAL_REACH_DEGREES / 2f, 0f), eps)
        assertTrue(DialGlow.reveal(36f, 0f) > DialGlow.REVEAL_MAX / 2f)
        assertTrue(DialGlow.reveal(7.2f, 0f) > DialGlow.reveal(14.4f, 0f))
    }

    @Test
    fun revealIsSymmetricAndWrapsAtTwelveOClock() {
        assertEquals(DialGlow.reveal(7.2f, 0f), DialGlow.reveal(352.8f, 0f), eps)
        assertEquals(DialGlow.reveal(10f, 355f), DialGlow.reveal(340f, 355f), eps)
    }

    @Test
    fun marksFarFromThePointerStayAtTheBaseColor() {
        assertEquals(0f, DialGlow.reveal(DialGlow.REVEAL_REACH_DEGREES, 0f), eps)
        assertEquals(0f, DialGlow.reveal(180f, 0f), eps)
        assertEquals(0f, DialGlow.reveal(250f, 90f), eps)
    }
}
