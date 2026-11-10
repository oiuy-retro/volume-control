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
    fun majorsAtEveryFullTurnOfFifty() {
        val majors = (0..150).filter { TickDial.isMajorTick(it, 50, 150) }
        assertEquals(listOf(0, 50, 100, 150), majors)
        assertTrue(TickDial.crossedIncludesMajor(49, 51, 50, 150))
        assertFalse(TickDial.crossedIncludesMajor(51, 60, 50, 150))
    }

    @Test
    fun topEndIsMajorEvenWhenNotAWholeTurn() {
        assertTrue(TickDial.isMajorTick(140, 50, 140))
        assertFalse(TickDial.isMajorTick(139, 50, 140))
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
