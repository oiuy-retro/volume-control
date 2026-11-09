package com.oiuy.volumecontrol.hybrid

import com.oiuy.volumecontrol.effect.BackendKind
import com.oiuy.volumecontrol.effect.EffectState
import com.oiuy.volumecontrol.calibrationText
import com.oiuy.volumecontrol.readoutText
import org.junit.Assert.*
import org.junit.Test

class HybridReadoutTest {
    private val good = EffectState(active = true, backend = BackendKind.DYNAMICS_PROCESSING,
        requestedDb = -1.8f, confirmedDb = listOf(-1.8f, -1.8f), enabledReported = true, hasControl = true)
    private fun text(s: EffectState) = readoutText(HybridReadout(2, 15, s, true, false, null))

    @Test fun matchingReadbackIsNotClaimedToBeAnAudibleCut() {
        val t = text(good)
        assertTrue(t.contains("API matches request"))
        assertTrue(t.contains("not proof of audible application"))
        assertTrue(t.contains("requested −1.8 dB"))
        assertTrue(t.contains("Step sizes estimated"))
    }
    @Test fun rejectedRequestShowsUnconfirmedInsteadOfAppliedDb() {
        val t = text(good.copy(confirmedDb = null, message = "Effect API rejected"))
        assertTrue(t.contains("not confirmed"))
        assertTrue(t.contains("Fine control NOT confirmed"))
        assertTrue(t.contains("Effect API rejected"))
        assertFalse(t.contains("API matches request"))
    }
    @Test fun attemptsAndControlAreVisible() {
        val t = text(good.copy(hasControl = false, attempts = listOf("Failed: backend X")))
        assertTrue(t.contains("control: false"))
        assertTrue(t.contains("Failed: backend X"))
        assertTrue(t.contains("Fine control NOT confirmed"))
    }
    @Test fun disabledEngineNeverLooksConfirmed() {
        val t = text(good.copy(enabledReported = false))
        assertTrue(t.contains("enabled: false"))
        assertFalse(t.contains("API matches request"))
    }
    @Test fun calibratedCurveIsLabelledWithCount() {
        val r = HybridReadout(7, 15, good, false, false, null, curveSource = CurveSource.CALIBRATED, calibratedSteps = 3)
        val t = readoutText(r)
        assertTrue(t.contains("calibrated 3 of 14 steps"))
        assertFalse(t.contains("estimated"))
    }
    @Test fun estimatedCurveSaysEstimatedAndSuggestsCalibration() {
        assertTrue(text(good).contains("Calibrate"))
    }
    @Test fun calibrationTextExplainsABAndWarnsAtFloor() {
        val r = HybridReadout(7, 15, good.copy(floorDb = -15f), true, false, null)
        val normal = calibrationText(r, CalibrationSession(6, CalibrationSide.B, -4f, -4f))
        assertTrue(normal.contains("A = level 6, no cut. B = level 7 with cut C"))
        assertTrue(normal.contains("Now playing: B"))
        assertFalse(normal.contains("backend floor"))
        val floor = calibrationText(r, CalibrationSession(6, CalibrationSide.A, -15f, -15f))
        assertTrue(floor.contains("backend floor"))
    }

}
