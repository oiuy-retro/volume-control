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

    private fun magnet(prev: Float, raw: Float, latch: Float = Float.NaN, holding: Boolean = false, perTurn: Int = 50, max: Int = 150) =
        TickDial.resolveMagnet(prev, raw, latch, holding, perTurn, max)

    @Test
    fun magnetZoneIsScaledFromThePreviousDial() {
        assertEquals(0.5f, TickDial.magnetZoneUnits(50), eps)
        // ±3.6 degrees, the same angle as the old ±0.1% around whole-percent majors.
        assertEquals(3.6f, TickDial.magnetZoneUnits(50) * TickDial.degreesPerTick(50), eps)
        assertEquals(3.6f, 0.1f / 10f * 360f, eps)
        // Android steps keep the per-step magnet.
        assertEquals(0.1f, TickDial.magnetZoneUnits(5), eps)
    }

    @Test
    fun magnetSnapsToEveryMajorWithinATurn() {
        for (m in listOf(5, 10, 15, 20, 25, 30, 35, 40, 45)) {
            val up = magnet(m - 0.8f, m - 0.4f)
            assertEquals("approach $m from below", m.toFloat(), up.reportedLevel, eps)
            assertTrue(up.holding)
            val down = magnet(m + 0.9f, m + 0.45f)
            assertEquals("approach $m from above", m.toFloat(), down.reportedLevel, eps)
        }
    }

    @Test
    fun magnetSnapsToMajorsAcrossTurns() {
        for (m in listOf(55, 60, 85, 140, 145)) {
            assertEquals(m.toFloat(), magnet(m - 1f, m + 0.3f).reportedLevel, eps)
        }
    }

    @Test
    fun noRegressionAtFullTurnsAndEnds() {
        for (m in listOf(0, 50, 100, 150)) {
            val r = magnet((m + 0.8f).coerceAtMost(150f), (m + 0.2f).coerceAtMost(150f))
            assertEquals(m.toFloat(), r.reportedLevel, eps)
        }
        assertEquals(150f, magnet(149.2f, 149.7f).reportedLevel, eps)
        assertEquals(0f, magnet(0.8f, 0.3f).reportedLevel, eps)
    }

    @Test
    fun minorTicksDoNotSnap() {
        for (raw in listOf(11.05f, 12.0f, 13.1f, 61.0f, 148.0f)) {
            val r = magnet(raw - 0.2f, raw)
            assertEquals(raw, r.reportedLevel, eps)
            assertFalse(r.holding)
            assertTrue(r.lastSnapped.isNaN())
        }
    }

    @Test
    fun topEndThatIsNotOnAMarkStillSnaps() {
        assertEquals(142f, magnet(141.5f, 141.7f, max = 142).reportedLevel, eps)
        assertEquals(140f, magnet(140.8f, 140.4f, max = 142).reportedLevel, eps)
    }

    @Test
    fun fastSweepCapturesTheFirstCrossedMajorOnce() {
        val r = magnet(8.4f, 13.4f)
        assertEquals(10f, r.reportedLevel, eps)
        assertEquals(10f, r.lastSnapped, eps)
        assertFalse(r.holding)
        // 13.6 is more than 2 units from 10: latch clears, no major in zone, no snap.
        val next = magnet(13.4f, 13.6f, r.lastSnapped, r.holding)
        assertEquals(13.6f, next.reportedLevel, eps)
        // A sweep across only minors does not snap.
        assertEquals(13.9f, magnet(11.2f, 13.9f).reportedLevel, eps)
    }

    @Test
    fun holdsWhileInsideTheZone() {
        var r = magnet(19.0f, 19.6f)
        assertEquals(20f, r.reportedLevel, eps)
        r = magnet(19.6f, 20.4f, r.lastSnapped, r.holding)
        assertEquals(20f, r.reportedLevel, eps)
        assertTrue(r.holding)
        r = magnet(20.4f, 20.8f, r.lastSnapped, r.holding)
        assertEquals(20.8f, r.reportedLevel, eps)
        assertFalse(r.holding)
    }

    @Test
    fun deadzoneAfterAJumpIsTwoTicks() {
        var r = magnet(9.0f, 9.6f)
        assertEquals(10f, r.lastSnapped, eps)
        // Leave the zone, then come back toward 10 without going 2 units away: no re-snap.
        r = magnet(9.6f, 11.5f, r.lastSnapped, r.holding)
        assertEquals(11.5f, r.reportedLevel, eps)
        assertEquals(10f, r.lastSnapped, eps)
        r = magnet(11.5f, 10.3f, r.lastSnapped, r.holding)
        assertEquals(10.3f, r.reportedLevel, eps)
        assertEquals(10f, r.lastSnapped, eps)
        // Move 2 units away, latch clears, then 15 can snap.
        r = magnet(10.3f, 12.1f, r.lastSnapped, r.holding)
        assertTrue(r.lastSnapped.isNaN())
        r = magnet(12.1f, 14.6f, r.lastSnapped, r.holding)
        assertEquals(15f, r.reportedLevel, eps)
    }

    @Test
    fun deadzoneBlocksAQuickReturnSweep() {
        // Snap to 10 by sweep, swing back across 10 within 2 units: no new snap.
        var r = magnet(8.0f, 10.2f)
        assertEquals(10f, r.lastSnapped, eps)
        r = magnet(10.2f, 11.4f, r.lastSnapped, r.holding)
        r = magnet(11.4f, 9.2f, r.lastSnapped, r.holding)
        assertEquals(9.2f, r.reportedLevel, eps)
    }

    @Test
    fun androidStepsKeepsTheMagnetOnEveryStep() {
        val r = magnet(6.8f, 7.05f, perTurn = 5, max = 15)
        assertEquals(7f, r.reportedLevel, eps)
        val far = magnet(7.2f, 7.4f, perTurn = 5, max = 15)
        assertEquals(7.4f, far.reportedLevel, eps)
        assertTrue(TickDial.isMagnetMark(7, 5, 15))
        assertFalse(TickDial.isMagnetMark(7, 50, 150))
    }
}
