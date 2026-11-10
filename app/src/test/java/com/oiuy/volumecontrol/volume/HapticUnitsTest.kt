package com.oiuy.volumecontrol.volume

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticUnitsTest {
    @Test
    fun zeroUnitsKeepsTenthPercentTicks() {
        assertEquals(DialMath.tickIndexFromPercent(47.3f), DialMath.hapticTick(47.3f, 0))
        assertEquals(DialMath.isMajorTickIndex(470), DialMath.isHapticMajor(470, 0))
    }

    @Test
    fun fineUnitsGiveOneTickPerUnitMatchingWrites() {
        for (unit in 0..150) {
            val percent = unit * 100f / 150f
            assertEquals(unit, DialMath.hapticTick(percent, 150))
            assertEquals(DialMath.toStreamIndex(percent / 100f * 150f, 150), DialMath.hapticTick(percent, 150))
        }
    }

    @Test
    fun majorsAtTenPercentLandmarks() {
        val majors = (0..150).filter { DialMath.isHapticMajor(it, 150) }
        assertEquals((0..10).map { it * 15 }, majors)
        assertTrue(DialMath.crossedIncludesHapticMajor(14, 16, 150))
        assertFalse(DialMath.crossedIncludesHapticMajor(16, 18, 150))
    }

    @Test
    fun fullTurnCrossesFifteenFineUnits() {
        val start = DialMath.hapticTick(40f, 150)
        val end = DialMath.hapticTick(50f, 150)
        assertEquals(15, DialMath.ticksCrossed(start, end))
    }
}
