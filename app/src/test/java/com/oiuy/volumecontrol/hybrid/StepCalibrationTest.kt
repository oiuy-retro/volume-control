package com.oiuy.volumecontrol.hybrid

import org.junit.Assert.*
import org.junit.Test

class StepCalibrationTest {
    @Test fun nothingCalibratedHasNoCurve() {
        assertNull(StepCalibration(15).curve())
        assertEquals(0, StepCalibration(15).calibratedCount)
        assertEquals(14, StepCalibration(15).calibratablePairs)
    }

    @Test fun singlePairGivesUniformCurveEndingAtZero() {
        val c = StepCalibration(15).with(7, 6f).curve()!!
        assertEquals(0f, c[15], 0f)
        for (i in 1 until 15) assertEquals(6f, c[i + 1] - c[i], 1e-4f)
        assertEquals(Float.NEGATIVE_INFINITY, c[0], 0f)
        assertTrue(HybridMath.curveIsUsable(c))
    }

    @Test fun uncalibratedPairsUseNearestAndTiesAverage() {
        val cal = StepCalibration(15).with(3, 8f).with(7, 4f)
        assertEquals(8f, cal.stepFor(1)!!, 0f)
        assertEquals(8f, cal.stepFor(4)!!, 0f)
        assertEquals(6f, cal.stepFor(5)!!, 0f) // equidistant from 3 and 7
        assertEquals(4f, cal.stepFor(6)!!, 0f)
        assertEquals(4f, cal.stepFor(14)!!, 0f)
        val c = cal.curve()!!
        assertEquals(4f, c[8] - c[7], 1e-4f)
        assertEquals(8f, c[4] - c[3], 1e-4f)
        assertEquals(2, cal.calibratedCount)
    }

    @Test fun storedSizeIsPositiveEvenFromNegativeCut() {
        assertEquals(3.5f, StepCalibration(15).with(2, -3.5f).steps[2]!!, 0f)
    }

    @Test fun muteAndOutOfRangePairsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { StepCalibration(15).with(0, 3f) }
        assertThrows(IllegalArgumentException::class.java) { StepCalibration(15).with(15, 3f) }
    }

    @Test fun serializeParseRoundTripAndIgnoresGarbage() {
        val cal = StepCalibration(15).with(2, 3.25f).with(9, 5f)
        assertEquals(cal.steps, StepCalibration.parse(15, cal.serialize()).steps)
        val bad = StepCalibration.parse(15, "x:1,0:4,15:2,4:NaN,5:2.5,,6")
        assertEquals(mapOf(5 to 2.5f), bad.steps)
        assertTrue(StepCalibration.parse(15, null).steps.isEmpty())
    }

    @Test fun keyPrefersAddressThenProductAndIncludesStepCount() {
        assertEquals("cal|8|AA:BB|15", StepCalibration.key(8, "AA:BB", "Speaker", 15))
        assertEquals("cal|8|Speaker|15", StepCalibration.key(8, "", "Speaker", 15))
        assertEquals("cal|2|unknown|25", StepCalibration.key(2, null, null, 25))
    }

    @Test fun detentSnapping() {
        assertEquals(-2.5f, StepCalibration.snapToDetent(-2.6f), 0f)
        assertEquals(-3.0f, StepCalibration.snapToDetent(-2.8f), 0f)
        assertEquals(0f, StepCalibration.snapToDetent(-0.1f), 0f)
    }
}
