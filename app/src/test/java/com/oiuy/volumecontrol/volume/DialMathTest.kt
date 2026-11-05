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



    @Test
    fun isMajorTickIndexEveryTen() {
        assertTrue(DialMath.isMajorTickIndex(0))
        assertTrue(DialMath.isMajorTickIndex(10))
        assertTrue(DialMath.isMajorTickIndex(470))
        assertTrue(DialMath.isMajorTickIndex(1000))
        assertTrue(!DialMath.isMajorTickIndex(1))
        assertTrue(!DialMath.isMajorTickIndex(469))
        assertTrue(DialMath.isMajorTickIndex(-10))
        assertTrue(!DialMath.isMajorTickIndex(-1))
    }

    @Test
    fun crossedIncludesMajorDetectsLandingAndCrossing() {
        // Enter whole percent 47 (tick 470) from below
        assertTrue(DialMath.crossedIncludesMajor(465, 470))
        assertTrue(DialMath.crossedIncludesMajor(469, 470))
        // Leaving a major does not count the starting tick (exclusive of from)
        assertTrue(!DialMath.crossedIncludesMajor(470, 475))
        // Fast spin across a major (enters 470 from below)
        assertTrue(DialMath.crossedIncludesMajor(465, 475))
        // No major between fine ticks in the same decade gap
        assertTrue(!DialMath.crossedIncludesMajor(471, 475))
        assertTrue(!DialMath.crossedIncludesMajor(465, 469))
        assertTrue(!DialMath.crossedIncludesMajor(470, 470))
        // Counterclockwise
        assertTrue(DialMath.crossedIncludesMajor(475, 470))
        assertTrue(DialMath.crossedIncludesMajor(471, 469))
    }


    @Test
    fun tinyMagnetSnapsWithinOneFineTick() {
        assertEquals(47f, DialMath.snapToWholePercentIfNear(47.0f), 0.001f)
        assertEquals(47f, DialMath.snapToWholePercentIfNear(47.1f), 0.001f)
        assertEquals(47f, DialMath.snapToWholePercentIfNear(46.9f), 0.001f)
        assertEquals(47.15f, DialMath.snapToWholePercentIfNear(47.15f), 0.001f)
        assertEquals(46.85f, DialMath.snapToWholePercentIfNear(46.85f), 0.001f)
        assertTrue(DialMath.isInWholePercentMagnetZone(46.9f))
        assertTrue(!DialMath.isInWholePercentMagnetZone(47.15f))
    }

    @Test
    fun magnetZoneConstant() {
        assertEquals(0.1f, DialMath.WHOLE_PERCENT_MAGNET_ZONE, 0.0001f)
    }


    @Test
    fun magnetLatchClearsAfterHalfPercent() {
        assertTrue(DialMath.isMagnetLatchCleared(47f, Float.NaN))
        assertTrue(!DialMath.isMagnetLatchCleared(47.1f, 47f))
        assertTrue(!DialMath.isMagnetLatchCleared(46.6f, 47f))
        assertTrue(DialMath.isMagnetLatchCleared(47.5f, 47f))
        assertTrue(DialMath.isMagnetLatchCleared(46.5f, 47f))
        assertEquals(0.5f, DialMath.MAGNET_RESNAP_CLEAR_DISTANCE, 0.0001f)
    }

    @Test
    fun resolveMagnetOverlayEngagesAndBlocksResnapUntilClear() {
        // In zone → engage (no speed gate)
        var r = DialMath.resolveMagnetOverlay(
            rawPercent = 47.05f,
            lastSnappedWhole = Float.NaN,
            currentlyHolding = false,
        )
        assertEquals(47f, r.reportedPercent, 0.001f)
        assertEquals(47f, r.lastSnappedWhole, 0.001f)
        assertTrue(r.holding)

        // Leave zone while holding — release; latch remains
        r = DialMath.resolveMagnetOverlay(
            rawPercent = 47.2f,
            lastSnappedWhole = r.lastSnappedWhole,
            currentlyHolding = true,
        )
        assertEquals(47.2f, r.reportedPercent, 0.001f)
        assertEquals(47f, r.lastSnappedWhole, 0.001f)
        assertTrue(!r.holding)

        // Re-enter zone but only 0.05 away — blocked (jitter guard)
        r = DialMath.resolveMagnetOverlay(
            rawPercent = 47.05f,
            lastSnappedWhole = r.lastSnappedWhole,
            currentlyHolding = false,
        )
        assertEquals(47.05f, r.reportedPercent, 0.001f)
        assertEquals(47f, r.lastSnappedWhole, 0.001f)
        assertTrue(!r.holding)

        // Move 0.5% away → latch clears; still outside zone so no engage
        r = DialMath.resolveMagnetOverlay(
            rawPercent = 47.5f,
            lastSnappedWhole = r.lastSnappedWhole,
            currentlyHolding = false,
        )
        assertEquals(47.5f, r.reportedPercent, 0.001f)
        assertTrue(r.lastSnappedWhole.isNaN())
        assertTrue(!r.holding)

        // Approach another whole → can engage again
        r = DialMath.resolveMagnetOverlay(
            rawPercent = 48.05f,
            lastSnappedWhole = r.lastSnappedWhole,
            currentlyHolding = false,
        )
        assertEquals(48f, r.reportedPercent, 0.001f)
        assertEquals(48f, r.lastSnappedWhole, 0.001f)
        assertTrue(r.holding)
    }

    @Test
    fun resolveMagnetOverlayKeepsHoldWhileInZone() {
        var r = DialMath.resolveMagnetOverlay(46.95f, Float.NaN, false)
        assertEquals(47f, r.reportedPercent, 0.001f)
        assertTrue(r.holding)
        // Stay in zone — keep hold on same whole
        r = DialMath.resolveMagnetOverlay(47.08f, r.lastSnappedWhole, true)
        assertEquals(47f, r.reportedPercent, 0.001f)
        assertTrue(r.holding)
        // Leave zone — release but latch remains
        r = DialMath.resolveMagnetOverlay(47.2f, r.lastSnappedWhole, true)
        assertEquals(47.2f, r.reportedPercent, 0.001f)
        assertEquals(47f, r.lastSnappedWhole, 0.001f)
        assertTrue(!r.holding)
    }

    @Test
    fun nearestWholePercentHalfUp() {
        assertEquals(47f, DialMath.nearestWholePercent(47.4f), 0.001f)
        assertEquals(47f, DialMath.nearestWholePercent(46.5f), 0.001f)
        assertEquals(0f, DialMath.nearestWholePercent(-1f), 0.001f)
        assertEquals(100f, DialMath.nearestWholePercent(100.4f), 0.001f)
    }

    @Test
    fun clampAtEndsStillHardStops() {
        assertEquals(0f, DialMath.clampRotationDegrees(90f, 100f), 0.001f)
        assertEquals(0f, DialMath.clampRotationDegrees(-90f, 0f), 0.001f)
        assertEquals(-90f, DialMath.clampRotationDegrees(-90f, 100f), 0.001f)
        assertEquals(90f, DialMath.clampRotationDegrees(90f, 0f), 0.001f)
        assertEquals(18f, DialMath.clampRotationDegrees(90f, 99.5f), 0.001f)
        assertEquals(0f, DialMath.clampRotationDegrees(-1f, 0f), 0.001f)
    }
}
