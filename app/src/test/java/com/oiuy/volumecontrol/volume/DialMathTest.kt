package com.oiuy.volumecontrol.volume

import org.junit.Assert.assertEquals
import org.junit.Test

class DialMathTest {
    @Test
    fun fullTurnMovesTenPercentOfRangeForVaryingMax() {
        assertEquals(1.5f, DialMath.levelDeltaFromDegrees(360f, 15), 1e-4f)
        assertEquals(2.5f, DialMath.levelDeltaFromDegrees(360f, 25), 1e-4f)
        assertEquals(15f, DialMath.levelDeltaFromDegrees(360f, 150), 1e-4f)
        assertEquals(-0.75f, DialMath.levelDeltaFromDegrees(-180f, 15), 1e-4f)
        assertEquals(0f, DialMath.levelDeltaFromDegrees(360f, 0), 0f)
    }

    @Test
    fun levelPercentRoundsHalfUpWithEndpoints() {
        assertEquals(0, DialMath.levelPercent(0, 15))
        assertEquals(100, DialMath.levelPercent(15, 15))
        assertEquals(47, DialMath.levelPercent(7, 15)) // 46.67
        assertEquals(7, DialMath.levelPercent(1, 15)) // 6.67
        assertEquals(50, DialMath.levelPercent(1, 2))
        assertEquals(1, DialMath.levelPercent(1, 150)) // 0.67
        assertEquals(100, DialMath.levelPercent(1, 1))
        assertEquals(0, DialMath.levelPercent(5, 0))
        assertEquals(100, DialMath.levelPercent(99, 15)) // clamped
        assertEquals(0, DialMath.levelPercent(-3, 15))
    }

    @Test
    fun hysteresisIsCappedForLargeMax() {
        assertEquals(DialMath.levelDeltaFromDegrees(30f, 15), DialMath.levelHysteresis(15), 1e-5f)
        assertEquals(0.25f, DialMath.levelHysteresis(150), 1e-5f)
        assertEquals(0f, DialMath.levelHysteresis(0), 0f)
    }

    @Test
    fun desiredIndexNeedsHalfLevelPlusDeadBand() {
        val h = DialMath.levelHysteresis(15)
        assertEquals(7, DialMath.desiredIndex(7.4f, 7, 15))
        assertEquals(7, DialMath.desiredIndex(7.5f + h - 0.01f, 7, 15))
        assertEquals(8, DialMath.desiredIndex(7.5f + h + 0.01f, 7, 15))
        assertEquals(7, DialMath.desiredIndex(6.5f - h + 0.01f, 7, 15))
        assertEquals(6, DialMath.desiredIndex(6.5f - h - 0.01f, 7, 15))
        // Dead band: just back below the up-threshold does not undo the step.
        assertEquals(8, DialMath.desiredIndex(7.5f, 8, 15))
        // Endpoints reachable.
        assertEquals(15, DialMath.desiredIndex(15f, 14, 15))
        assertEquals(0, DialMath.desiredIndex(0f, 1, 15))
        assertEquals(0, DialMath.desiredIndex(3f, 0, 0))
    }

    @Test
    fun clampRotationHardStops() {
        assertEquals(0f, DialMath.clampRotationDegrees(-30f, 0f, 15), 0f)
        assertEquals(0f, DialMath.clampRotationDegrees(30f, 15f, 15), 0f)
        assertEquals(30f, DialMath.clampRotationDegrees(30f, 0f, 15), 1e-4f)
        // 0.25 level of room at max 15 = 60 degrees
        assertEquals(60f, DialMath.clampRotationDegrees(90f, 14.75f, 15), 1e-3f)
        assertEquals(-60f, DialMath.clampRotationDegrees(-90f, 0.25f, 15), 1e-3f)
        assertEquals(0f, DialMath.clampRotationDegrees(30f, 3f, 0), 0f)
    }

    @Test
    fun widgetStepIsConsistentWholeLevels() {
        assertEquals(2, DialMath.widgetStepLevels(15))
        assertEquals(3, DialMath.widgetStepLevels(25))
        assertEquals(3, DialMath.widgetStepLevels(30))
        assertEquals(15, DialMath.widgetStepLevels(150))
        assertEquals(1, DialMath.widgetStepLevels(7))
        assertEquals(1, DialMath.widgetStepLevels(1))
        assertEquals(0, DialMath.widgetStepLevels(0))
    }

    @Test
    fun sweepFollowsActualLevel() {
        assertEquals(0f, DialMath.levelSweepDegrees(0, 15), 0f)
        assertEquals(360f, DialMath.levelSweepDegrees(15, 15), 1e-4f)
        assertEquals(168f, DialMath.levelSweepDegrees(7, 15), 1e-3f)
        assertEquals(0f, DialMath.levelSweepDegrees(4, 0), 0f)
    }

    @Test
    fun shortestAngleDeltaWraps() {
        assertEquals(20f, DialMath.shortestAngleDelta(170f, -170f), 1e-4f)
        assertEquals(-20f, DialMath.shortestAngleDelta(-170f, 170f), 1e-4f)
    }
}
