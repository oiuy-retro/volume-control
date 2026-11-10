package com.oiuy.volumecontrol.volume

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TickDialTest {
    private val eps = 1e-4f

    @Test
    fun fineModeHasFiftyTicksPerTurnAndThreeTurnsAt150() {
        assertEquals(50, TickDial.unitsPerTurn(VolumeMode.SAMSUNG_FINE))
        assertEquals(7.2f, TickDial.degreesPerTick(50), eps)
        assertEquals(3f, TickDial.turns(150, 50), eps)
        assertEquals(4f, TickDial.turns(200, 50), eps)
    }

    @Test
    fun fallbackTicksAreWholePublicSteps() {
        val perTurn = TickDial.unitsPerTurn(VolumeMode.ANDROID_STEPS)
        assertEquals(5, perTurn)
        assertEquals(72f, TickDial.degreesPerTick(perTurn), eps)
        // Same turns as fine mode on the same phone: 15 steps vs 150 fine units.
        assertEquals(TickDial.turns(150, 50), TickDial.turns(15, perTurn), eps)
        assertEquals(1f, TickDial.unitsFromDegrees(TickDial.degreesPerTick(perTurn), perTurn), eps)
    }

    @Test
    fun oneTickOfRotationIsOneUnit() {
        assertEquals(1f, TickDial.unitsFromDegrees(7.2f, 50), eps)
        assertEquals(-50f, TickDial.unitsFromDegrees(-360f, 50), eps)
    }

    @Test
    fun indicatorReturnsToNoonEveryTurn() {
        assertEquals(0f, TickDial.indicatorDegrees(0f, 50, 150), eps)
        assertEquals(7.2f, TickDial.indicatorDegrees(1f, 50, 150), eps)
        assertEquals(180f, TickDial.indicatorDegrees(25f, 50, 150), eps)
        assertEquals(0f, TickDial.indicatorDegrees(50f, 50, 150), eps)
        assertEquals(0f, TickDial.indicatorDegrees(100f, 50, 150), eps)
        assertEquals(0f, TickDial.indicatorDegrees(150f, 50, 150), eps)
        assertEquals(0f, TickDial.indicatorDegrees(170f, 50, 150), eps)
    }

    @Test
    fun hardStopsAtZeroAndMax() {
        assertEquals(0f, TickDial.clampRotationDegrees(-10f, 0f, 50, 150), eps)
        assertEquals(0f, TickDial.clampRotationDegrees(10f, 150f, 50, 150), eps)
        assertEquals(-7.2f, TickDial.clampRotationDegrees(-30f, 1f, 50, 150), eps)
        assertEquals(14.4f, TickDial.clampRotationDegrees(400f, 148f, 50, 150), eps)
        assertEquals(10f, TickDial.clampRotationDegrees(10f, 75f, 50, 150), eps)
        assertEquals(0f, TickDial.clampLevel(-3f, 150), eps)
        assertEquals(150f, TickDial.clampLevel(151f, 150), eps)
    }

    @Test
    fun sweepListsTicksInTravelOrder() {
        assertEquals(listOf(11, 12, 13), TickDial.ticksCrossedBySweep(10.4f, 13.2f, 150))
        assertEquals(listOf(12, 11), TickDial.ticksCrossedBySweep(12.6f, 10.9f, 150))
        assertEquals(emptyList<Int>(), TickDial.ticksCrossedBySweep(10.2f, 10.8f, 150))
    }

    @Test
    fun deadzoneIsTwoTicksOrFourteenPointFourDegrees() {
        assertEquals(2f, TickDial.MAGNET_RESNAP_CLEAR_UNITS, eps)
        assertEquals(14.4f, TickDial.MAGNET_RESNAP_CLEAR_UNITS * TickDial.degreesPerTick(50), eps)
        // Scaled from the previous dial: 0.5% at 10%/turn = 18 degrees there.
        val previousDegrees = 0.5f / 10f * 360f
        assertEquals(18f, previousDegrees, eps)
    }

    @Test
    fun magnetSnapsInZoneOntoTicks() {
        val r = TickDial.resolveMagnet(9.8f, 10.05f, Float.NaN, false, 150)
        assertEquals(10f, r.reportedLevel, eps)
        assertTrue(r.holding)
        val far = TickDial.resolveMagnet(10.2f, 10.4f, Float.NaN, false, 150)
        assertEquals(10.4f, far.reportedLevel, eps)
        assertFalse(far.holding)
    }

    @Test
    fun noResnapUntilRawMovesTwoTicksAway() {
        var r = TickDial.resolveMagnet(9.8f, 10.0f, Float.NaN, false, 150)
        assertEquals(10f, r.lastSnapped, eps)
        // Leave the zone and pass tick 11: still inside the 2-tick deadzone, no snap.
        r = TickDial.resolveMagnet(10.0f, 11.0f, r.lastSnapped, r.holding, 150)
        assertEquals(11.0f, r.reportedLevel, eps)
        assertEquals(10f, r.lastSnapped, eps)
        r = TickDial.resolveMagnet(11.0f, 11.9f, r.lastSnapped, r.holding, 150)
        assertEquals(11.9f, r.reportedLevel, eps)
        assertEquals(10f, r.lastSnapped, eps)
        // Two ticks away from 10 clears the latch; tick 12 can snap.
        r = TickDial.resolveMagnet(11.9f, 12.0f, r.lastSnapped, r.holding, 150)
        assertEquals(12f, r.reportedLevel, eps)
        assertEquals(12f, r.lastSnapped, eps)
    }

    @Test
    fun deadzoneAppliesInBothDirections() {
        var r = TickDial.resolveMagnet(20.3f, 20.0f, Float.NaN, false, 150)
        assertEquals(20f, r.lastSnapped, eps)
        r = TickDial.resolveMagnet(20.0f, 19.0f, r.lastSnapped, r.holding, 150)
        assertEquals(19.0f, r.reportedLevel, eps)
        r = TickDial.resolveMagnet(19.0f, 18.0f, r.lastSnapped, r.holding, 150)
        assertEquals(18f, r.lastSnapped, eps)
    }

    @Test
    fun fastSweepCapturesFirstCrossedTickOnce() {
        val r = TickDial.resolveMagnet(10.4f, 13.4f, Float.NaN, false, 150)
        assertEquals(11f, r.reportedLevel, eps)
        assertEquals(11f, r.lastSnapped, eps)
        assertFalse(r.holding)
        // Next frame continues raw: 13.6 is 2.6 ticks from 11, latch clears, no tick in zone.
        val next = TickDial.resolveMagnet(13.4f, 13.6f, r.lastSnapped, r.holding, 150)
        assertEquals(13.6f, next.reportedLevel, eps)
    }

    @Test
    fun magnetStaysInRange() {
        val top = TickDial.resolveMagnet(149.8f, 150f, Float.NaN, false, 150)
        assertEquals(150f, top.reportedLevel, eps)
        val bottom = TickDial.resolveMagnet(0.3f, 0f, Float.NaN, false, 150)
        assertEquals(0f, bottom.reportedLevel, eps)
    }
}
