package com.oiuy.volumecontrol.ui

import com.oiuy.volumecontrol.volume.TickDial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DialMarksTest {
    private val eps = 1e-4f

    @Test
    fun fineModeDraws150MarksPerTurnForFiftyUnits() {
        assertEquals(150, DialMarks.visualTicks(50))
        assertEquals(2.4f, DialMarks.degreesOf(1, 50), eps)
        assertEquals(15, DialMarks.visualTicks(5))
        assertEquals(0, DialMarks.visualTicks(0))
        assertEquals(0f, DialMarks.degreesOf(3, 0), eps)
    }

    @Test
    fun everyThirdMarkIsAUnitAndLinesUpWithTheUnitAngle() {
        for (v in 0 until 150) {
            assertEquals(v % 3 == 0, DialMarks.isUnitTick(v))
            assertEquals(v / 3, DialMarks.unitOf(v))
            if (DialMarks.isUnitTick(v)) {
                assertEquals(DialMarks.unitOf(v) * TickDial.degreesPerTick(50), DialMarks.degreesOf(v, 50), 1e-3f)
            }
        }
    }

    @Test
    fun tenMajorMarksPerTurnEveryFiveUnits() {
        val majors = (0 until 150).filter { DialMarks.isMajor(it, 50) }
        assertEquals(10, majors.size)
        assertEquals((0 until 10).map { it * 15 }, majors)
        // Same marks as the magnet and strong haptic.
        for (v in majors) assertTrue(TickDial.isMajorTick(DialMarks.unitOf(v), 50, 150))
        assertFalse(DialMarks.isMajor(1, 50))
        assertFalse(DialMarks.isMajor(3, 50))
        assertEquals(36f, DialMarks.degreesOf(15, 50), eps)
    }

    @Test
    fun androidStepsModeHasOnlyTwelveOClockAsMajor() {
        val majors = (0 until DialMarks.visualTicks(5)).filter { DialMarks.isMajor(it, 5) }
        assertEquals(listOf(0), majors)
    }
}
