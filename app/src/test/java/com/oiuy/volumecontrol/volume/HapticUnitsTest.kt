package com.oiuy.volumecontrol.volume

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Haptic detents on the tick dial: one per output unit, strong at each full turn. */
class HapticUnitsTest {
    @Test
    fun oneTickPerFineUnitMatchingWrites() {
        for (unit in 0..150) {
            assertEquals(unit, TickDial.tickIndex(unit.toFloat(), 150))
            assertEquals(DialMath.toStreamIndex(unit + 0.3f, 150).coerceAtMost(150), TickDial.tickIndex(unit + 0.3f, 150))
        }
    }

    @Test
    fun strongHapticAtEveryDrawnMajorMark() {
        val majors = (0..150).filter { TickDial.isMajorTick(it, 50, 150) }
        assertEquals((0..150 step 5).toList(), majors)
        // Full turns (twelve o'clock) are still majors.
        for (t in listOf(0, 50, 100, 150)) assertTrue(TickDial.isMajorTick(t, 50, 150))
        for (t in listOf(5, 10, 15, 20, 25, 30, 35, 40, 45, 55, 145)) assertTrue(TickDial.isMajorTick(t, 50, 150))
        for (t in listOf(1, 4, 6, 49, 51, 144, 149)) assertFalse(TickDial.isMajorTick(t, 50, 150))
        // Landing on or crossing a major.
        assertTrue(TickDial.crossedIncludesMajor(9, 10, 50, 150))
        assertTrue(TickDial.crossedIncludesMajor(13, 16, 50, 150))
        assertTrue(TickDial.crossedIncludesMajor(56, 54, 50, 150))
        assertTrue(TickDial.crossedIncludesMajor(49, 51, 50, 150))
        assertFalse(TickDial.crossedIncludesMajor(11, 14, 50, 150))
        assertFalse(TickDial.crossedIncludesMajor(10, 14, 50, 150))
    }

    @Test
    fun majorsMatchTheDrawnBezel() {
        val drawn = (0 until 50).filter { TickDial.isDrawnMajorMark(it, 50) }
        assertEquals(10, drawn.size)
        assertEquals((0 until 50 step TickDial.MAJOR_MARK_SPACING_UNITS).toList(), drawn)
        assertEquals(36f, TickDial.MAJOR_MARK_SPACING_UNITS * TickDial.degreesPerTick(50), 1e-4f)
        for (t in 0..150) {
            assertEquals(TickDial.isDrawnMajorMark(t % 50, 50) || t == 150, TickDial.isMajorTick(t, 50, 150))
        }
    }

    @Test
    fun topEndIsMajorEvenWhenNotOnAMark() {
        assertTrue(TickDial.isMajorTick(142, 50, 142))
        assertFalse(TickDial.isMajorTick(141, 50, 142))
        assertTrue(TickDial.isMajorTick(140, 50, 142))
    }

    @Test
    fun fullTurnCrossesFiftyTicks() {
        val start = TickDial.tickIndex(20f, 150)
        val end = TickDial.tickIndex(20f + TickDial.unitsFromDegrees(360f, 50), 150)
        assertEquals(50, DialMath.ticksCrossed(start, end))
    }

    @Test
    fun fallbackMajorsEveryFiveSteps() {
        val majors = (0..15).filter { TickDial.isMajorTick(it, 5, 15) }
        assertEquals(listOf(0, 5, 10, 15), majors)
    }
}
