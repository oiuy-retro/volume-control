package com.oiuy.volumecontrol.ui

import com.oiuy.volumecontrol.volume.TickDial
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
        while (d <= 60f) {
            val f = DialGlow.falloff(d, 54f)
            assertTrue("falloff rose at $d", f <= previous + eps)
            assertTrue(f in 0f..1f)
            previous = f
            d += 0.5f
        }
    }

    @Test
    fun pointerLightsMarksUnderItAndFadesAcrossTheWrap() {
        assertEquals(DialGlow.POINTER_INTENSITY, DialGlow.pointerLight(0f, 0f), eps)
        // Twelve o'clock is lit from just either side of it.
        assertEquals(DialGlow.pointerLight(7.2f, 0f), DialGlow.pointerLight(352.8f, 0f), eps)
        assertTrue(DialGlow.pointerLight(7.2f, 0f) > DialGlow.pointerLight(14.4f, 0f))
        assertEquals(0f, DialGlow.pointerLight(90f, 0f), eps)
        assertEquals(0f, DialGlow.pointerLight(DialGlow.POINTER_REACH_DEGREES, 0f), eps)
    }

    @Test
    fun majorMarksAreLightSourcesNotLitByThemselves() {
        for (i in 0 until 50) {
            if (TickDial.isDrawnMajorMark(i, 50)) {
                assertEquals(0f, DialGlow.staticLight(i, 50), eps)
            }
        }
    }

    @Test
    fun minorMarksNextToAMajorAreBrighterThanMarksBetweenMajors() {
        // Fine mode: majors at 0, 5, 10 ... (36 degrees apart).
        val nextToMajor = DialGlow.staticLight(1, 50)
        val between = DialGlow.staticLight(2, 50)
        assertTrue(nextToMajor > between)
        assertTrue(nextToMajor > 0f)
        // Mirror image around a major.
        assertEquals(DialGlow.staticLight(1, 50), DialGlow.staticLight(49, 50), eps)
        assertEquals(DialGlow.staticLight(2, 50), DialGlow.staticLight(3, 50), eps)
        // Without the pointer, minor marks stay dim.
        for (i in 0 until 50) {
            assertTrue(DialGlow.minorBrightness(DialGlow.staticLight(i, 50), 0f) < 0.3f)
        }
    }

    @Test
    fun majorReachIsCappedForWideAndroidStepMarks() {
        assertEquals(2.6f * 7.2f, DialGlow.majorReachDegrees(50), eps)
        assertEquals(DialGlow.MAJOR_REACH_MAX_DEGREES, DialGlow.majorReachDegrees(5), eps)
        assertEquals(0f, DialGlow.majorReachDegrees(0), eps)
        // 72 degrees between Android-step marks: out of the twelve o'clock mark's reach.
        assertEquals(0f, DialGlow.staticLight(1, 5), eps)
    }

    @Test
    fun fillStaticLightMatchesPerMarkValues() {
        val out = FloatArray(50)
        DialGlow.fillStaticLight(50, out)
        for (i in 0 until 50) assertEquals(DialGlow.staticLight(i, 50), out[i], eps)
    }

    @Test
    fun brightnessIsClampedAndPointerMakesMinorsBright() {
        assertEquals(DialGlow.MINOR_BASE, DialGlow.minorBrightness(0f, 0f), eps)
        assertEquals(1f, DialGlow.minorBrightness(0.5f, 1f), eps)
        assertTrue(DialGlow.minorBrightness(0f, DialGlow.pointerLight(0f, 0f)) > 0.9f)
        assertEquals(DialGlow.MAJOR_BASE, DialGlow.majorBrightness(0f), eps)
        assertEquals(1f, DialGlow.majorBrightness(1f), eps)
    }
}
