package com.oiuy.volumecontrol.hybrid

import com.oiuy.volumecontrol.effect.BackendKind
import com.oiuy.volumecontrol.effect.EffectState
import org.junit.Assert.*
import org.junit.Test

class HybridControllerCoreTest {
    private class Rig(initial: Int = 7) {
        val log = mutableListOf<String>()
        var time = 0L
        var actual = initial
        var deferred = false
        var clamp: Int? = null
        var pending: Int? = null
        var failGain = false
        var mismatch = false
        var resetGainOnWrite = false
        var engineGain = 0f
        var fxState = EffectState(active = true, backend = BackendKind.DYNAMICS_PROCESSING,
            enabledReported = true, hasControl = true, confirmedDb = listOf(0f, 0f))
        val volume = object : HybridVolumePort {
            override val maxIndex = 15
            override val index get() = actual
            override fun writeIndex(index: Int) {
                log += "stream:$index"
                if (deferred) pending = index else actual = clamp ?: index
                if (resetGainOnWrite) engineGain = 0f
            }
        }
        val effect = object : HybridEffectPort {
            override val state get() = fxState
            override fun apply(db: Float, force: Boolean): EffectState {
                log += "gain:$db:$force"
                engineGain = if (failGain || mismatch) 0f else db
                fxState = fxState.copy(requestedDb = db,
                    confirmedDb = if (failGain) null else listOf(engineGain, engineGain),
                    message = if (failGain) "rejected" else null)
                return fxState
            }
        }
        val c = HybridControllerCore(volume, effect) { time }
        fun level(value: Float) { c.setOutputPercent(value / 15 * 100) }
        fun finger(degrees: Float) {
            val raw = c.adjustByDegrees(degrees)
            c.setOutputPercent(raw)
        }
        fun settle(index: Int = pending ?: actual) {
            actual = index
            pending = null
            engineGain = 0f // emulate a vendor overwriting gain after async stream work
            c.onVolumeEvent()
        }
    }

    @Test fun raisesOnlyAfterConfirmedCutAndReappliesAfterStreamWrite() {
        val r = Rig()
        r.resetGainOnWrite = true
        r.level(7.4f)
        assertTrue(r.log.first().startsWith("gain:"))
        assertTrue(r.log.first().endsWith(":false"))
        assertEquals("stream:8", r.log[1])
        assertTrue(r.log[2].endsWith(":true"))
        assertEquals(-1.5f, r.engineGain, 0.001f)
        assertTrue(r.c.fineConfirmed)
    }

    @Test fun synchronousDownwardWriteComesBeforeReleasingCut() {
        val r = Rig(8)
        r.level(7f)
        assertEquals("stream:7", r.log.first())
        assertEquals("gain:0.0:true", r.log[1])
    }

    @Test fun failedApplyCannotRaiseToUpperStepOrClaimConfirmation() {
        val r = Rig()
        r.failGain = true
        r.level(7.5f)
        assertEquals(7, r.actual)
        assertFalse(r.c.fineConfirmed)
        assertNull(r.fxState.confirmedDb)
        assertTrue(r.c.message!!.contains("unconfirmed"))
        assertFalse(r.log.any { it.startsWith("stream:") })
    }

    @Test fun mismatchedApiReadbackCannotRaiseToUpperStep() {
        val r = Rig()
        r.mismatch = true
        r.level(7.5f)
        assertEquals(7, r.actual)
        assertFalse(r.c.fineConfirmed)
    }

    @Test fun controlLossUsesWholeLevelsAndIsNotFineConfirmed() {
        val r = Rig()
        r.fxState = r.fxState.copy(hasControl = false)
        r.level(7.3f)
        assertEquals(7, r.actual)
        assertFalse(r.c.fineConfirmed)
        assertTrue(r.c.message!!.contains("Whole Android levels"))
    }

    @Test fun disabledEffectCannotBeCalledFineConfirmed() {
        val r = Rig()
        r.fxState = r.fxState.copy(enabledReported = false)
        r.level(7.3f)
        assertFalse(r.c.fineConfirmed)
        assertTrue(r.c.message!!.contains("disabled"))
    }

    @Test fun rawIsNotRebuiltDuringDeferredOwnWritesOrObserverCallbacks() {
        val r = Rig()
        r.deferred = true
        r.c.dragging = true
        r.finger(18f) // raw 7.075, Android still reports 7
        val first = r.c.rawLevel
        r.c.onVolumeEvent()
        assertEquals(first, r.c.rawLevel, 0f)
        r.finger(18f)
        val second = r.c.rawLevel
        assertTrue(second > first)
        r.settle()
        assertEquals(second, r.c.rawLevel, 0f)
        assertEquals(1, r.log.count { it.startsWith("stream:") })
        assertTrue(r.engineGain < 0f)
    }

    @Test fun synchronousOwnObserverAfterFingerReleasePreservesRawAndCut() {
        val r = Rig()
        r.finger(18f)
        val raw = r.c.rawLevel
        r.c.endDrag()
        r.time = 1200
        r.c.onVolumeEvent()
        assertEquals(raw, r.c.rawLevel, 0f)
        assertTrue(r.engineGain < 0f)
        assertTrue(r.c.settled)
    }

    @Test fun sameIndexEventReassertsCutAfterVendorResetWithoutAnotherStreamWrite() {
        val r = Rig()
        r.finger(18f)
        val cut = r.engineGain
        r.engineGain = 0f
        r.time = 250
        r.c.onVolumeEvent()
        assertEquals(cut, r.engineGain, 0f)
        assertEquals(1, r.log.count { it.startsWith("stream:") })
    }

    @Test fun deferredDownwardWriteDoesNotReleaseCutAtStillHigherIndex() {
        val r = Rig(8)
        r.deferred = true
        r.level(7f)
        assertEquals(8, r.actual)
        assertEquals(-2.5f, r.engineGain, 0f)
        r.settle()
        assertEquals(7, r.actual)
        assertEquals(0f, r.engineGain, 0f)
        assertEquals(1, r.log.count { it.startsWith("stream:") })
    }

    @Test fun clampedWriteTimesOutWithoutRetryLoopOrAutomaticCutRelease() {
        val r = Rig()
        r.clamp = 7
        r.finger(18f)
        val raw = r.c.rawLevel
        r.time = 600
        r.c.onVolumeEvent()
        assertEquals(raw, r.c.rawLevel, 0f)
        r.time = 1200
        r.c.onVolumeEvent()
        assertTrue(r.c.settled)
        assertTrue(r.c.message!!.contains("Android kept level 7"))
        assertTrue(r.engineGain < 0f)
        repeat(20) { r.c.onVolumeEvent() }
        assertEquals(1, r.log.count { it.startsWith("stream:") })
    }

    @Test fun routeChangeReloadsCurveAndCutButNeverRestoresOldStreamIndex() {
        val r = Rig()
        r.finger(18f)
        val cut = r.engineGain
        r.log.clear()
        r.actual = 4
        r.engineGain = 0f
        val curve = FloatArray(16) { if (it == 0) Float.NEGATIVE_INFINITY else (it - 15) * 3f }
        r.c.onRouteChanged(curve)
        assertFalse(r.c.curveEstimated)
        assertEquals(cut, r.engineGain, 0f)
        assertFalse(r.log.any { it.startsWith("stream:") })
        assertEquals(4, r.c.observedIndex)
        assertTrue(r.c.settled)
    }

    @Test fun externalHardwareChangeRetainsCutInsteadOfAutomaticallyRaisingLoudness() {
        val r = Rig()
        r.finger(18f)
        r.time = 1200
        r.c.onVolumeEvent()
        val cut = r.engineGain
        r.actual = 6
        r.log.clear()
        r.c.onVolumeEvent()
        assertEquals(cut, r.engineGain, 0f)
        assertFalse(r.log.any { it.startsWith("stream:") })
        assertTrue(r.c.rawLevel < 6f)
    }

    @Test fun diagnosticLocksDialAndNeverWritesStreamEvenOnExit() {
        val r = Rig()
        r.c.beginDiagnostic()
        assertEquals(-12f, r.engineGain, 0f)
        r.finger(90f)
        r.c.setOutputPercent(99f)
        r.c.toggleDiagnosticCut()
        assertEquals(0f, r.engineGain, 0f)
        r.c.toggleDiagnosticCut()
        r.c.endDiagnostic()
        assertEquals(-12f, r.engineGain, 0f)
        assertFalse(r.c.diagnostic)
        assertFalse(r.log.any { it.startsWith("stream:") })
    }

    @Test fun diagnosticRespectsBackendFloor() {
        val r = Rig()
        r.fxState = r.fxState.copy(floorDb = -6f)
        r.c.beginDiagnostic()
        assertEquals(-6f, r.engineGain, 0f)
    }

    @Test fun reattachRestoresCutWithForceAndNoStreamWrite() {
        val r = Rig()
        r.finger(18f)
        val cut = r.engineGain
        r.fxState = r.fxState.copy(requestedDb = 0f, confirmedDb = listOf(0f))
        r.engineGain = 0f
        r.log.clear()
        r.c.afterReattach()
        assertEquals(cut, r.engineGain, 0f)
        assertFalse(r.log.any { it.startsWith("stream:") })
        assertTrue(r.log.single().endsWith(":true"))
    }

    @Test fun magnetOutputDoesNotReplaceRawAndOnlyWritesFinalOutput() {
        val r = Rig()
        val raw = r.c.adjustByDegrees(18f)
        assertTrue(r.log.isEmpty())
        r.c.setOutputPercent(47f)
        assertEquals(raw, r.c.percent, 0f)
        assertEquals(7.05f, r.c.outputLevel, 0.001f)
    }

    @Test fun initializationRetainsProcessScopedCutWithoutStreamWrite() {
        val r = Rig()
        r.fxState = r.fxState.copy(requestedDb = -1.5f, confirmedDb = listOf(-1.5f))
        r.c.initialize(null)
        assertEquals(6.4f, r.c.rawLevel, 0.001f)
        r.c.reapplyGain()
        assertEquals(-1.5f, r.engineGain, 0f)
        assertFalse(r.log.any { it.startsWith("stream:") })
    }
    @Test fun explicitWidgetGainResetIsNotOverwrittenByOwnObserver() {
        val r = Rig()
        r.finger(18f)
        r.fxState = r.fxState.copy(requestedDb = 0f, confirmedDb = listOf(0f))
        r.actual = 9
        r.log.clear()
        r.c.onVolumeEvent()
        r.c.reapplyGain()
        assertEquals(0f, r.engineGain, 0f)
        assertEquals(9f, r.c.rawLevel, 0f)
        assertFalse(r.log.any { it.startsWith("stream:") })
    }

    @Test fun unrelatedSettingsCallbacksCannotCreateAnEffectWriteLoop() {
        val r = Rig()
        r.finger(18f)
        r.c.onVolumeEvent(forceReapply = false) // acknowledge changed stream once
        r.log.clear()
        repeat(100) { r.c.onVolumeEvent(forceReapply = false) }
        assertTrue(r.log.isEmpty())
    }

    @Test fun clampedDownwardWriteRetainsCompensationAfterTimeout() {
        val r = Rig(8)
        r.clamp = 8
        r.level(7f)
        r.time = 1200
        r.c.onVolumeEvent()
        assertTrue(r.c.settled)
        assertEquals(-2.5f, r.engineGain, 0f)
        repeat(10) { r.c.reapplyGain() }
        assertEquals(-2.5f, r.engineGain, 0f)
        assertEquals(1, r.log.count { it.startsWith("stream:") })
    }

    @Test fun successfulManualReconfirmationClearsOldFailureMessage() {
        val r = Rig()
        r.failGain = true
        r.level(7.4f)
        assertFalse(r.c.fineConfirmed)
        r.failGain = false
        r.c.afterReattach()
        assertTrue(r.c.fineConfirmed)
        assertNull(r.c.message)
    }

    @Test fun reversingGestureSupersedesDeferredWriteEvenIfReadbackAlreadyMatches() {
        val r = Rig()
        r.deferred = true
        r.level(7.1f) // pending 8, readback still 7
        r.level(7f) // must send 7 to supersede pending 8, not return early
        assertEquals(listOf("stream:8", "stream:7"), r.log.filter { it.startsWith("stream:") })
        assertEquals(7, r.pending)
        r.settle(8) // older intermediate vendor update must be compensated
        assertEquals(-2.5f, r.engineGain, 0f)
        r.settle(7)
        assertEquals(0f, r.engineGain, 0f)
    }


    // ---------------- Calibration ----------------

    private fun streams(r: Rig) = r.log.filter { it.startsWith("stream:") }

    @Test fun calibrationStartsOnBAtCurrentLevelWithoutRaising() {
        val r = Rig(7)
        assertTrue(r.c.beginCalibration())
        val cal = r.c.calibrationSession!!
        assertEquals(6, cal.pair)
        assertEquals(CalibrationSide.B, cal.side)
        assertEquals(-2.5f, cal.cutDb, 0f)
        assertEquals(-2.5f, r.engineGain, 0f)
        assertTrue(streams(r).isEmpty())
    }

    @Test fun calibrationStartKeepsADeeperExistingCut() {
        val r = Rig(7)
        r.fxState = r.fxState.copy(requestedDb = -4.2f, confirmedDb = listOf(-4.2f, -4.2f))
        r.c.beginCalibration()
        assertEquals(-4.5f, r.c.calibrationSession!!.cutDb, 0f) // deeper detent, never louder
        assertTrue(streams(r).isEmpty())
    }

    @Test fun calibrationRefusesBelowLevelTwoAndWhenEffectNotReady() {
        val low = Rig(1)
        assertFalse(low.c.beginCalibration())
        assertTrue(low.c.message!!.contains("level 2"))
        assertTrue(low.log.isEmpty())
        val off = Rig(7)
        off.fxState = off.fxState.copy(hasControl = false)
        assertFalse(off.c.beginCalibration())
        assertNull(off.c.calibrationSession)
        assertTrue(off.log.isEmpty())
    }

    @Test fun bToALowersLevelBeforeRemovingCut() {
        val r = Rig(7)
        r.c.beginCalibration()
        r.log.clear()
        r.c.toggleCalibrationSide()
        assertEquals("stream:6", r.log.first())
        assertEquals("gain:0.0:true", r.log.last())
        assertEquals(6, r.actual)
        assertEquals(CalibrationSide.A, r.c.calibrationSession!!.side)
    }

    @Test fun aToBConfirmsCutBeforeRaisingLevel() {
        val r = Rig(7)
        r.c.beginCalibration()
        r.c.toggleCalibrationSide()
        r.log.clear()
        r.c.toggleCalibrationSide()
        assertEquals("gain:-2.5:true", r.log.first())
        assertEquals("stream:7", r.log[1])
        assertEquals(-2.5f, r.engineGain, 0f)
    }

    @Test fun aToBWithRejectedCutDoesNotRaise() {
        val r = Rig(7)
        r.c.beginCalibration()
        r.c.toggleCalibrationSide()
        r.failGain = true
        r.log.clear()
        r.c.toggleCalibrationSide()
        assertTrue(streams(r).isEmpty())
        assertEquals(6, r.actual)
        assertEquals(CalibrationSide.A, r.c.calibrationSession!!.side)
    }

    @Test fun bluetoothBToAKeepsCutUntilLowerLevelLandsAndHoldExpires() {
        val r = Rig(7)
        r.c.remoteHoldMs = 350
        r.c.beginCalibration()
        r.deferred = true
        r.c.toggleCalibrationSide()
        assertEquals(-2.5f, r.engineGain, 0f) // still level 7 at the phone: cut held
        r.time = 100
        r.settle() // phone now at 6, but speaker may lag: hold
        assertEquals(-2.5f, r.engineGain, 0f)
        r.time = 400
        r.c.onVolumeEvent()
        assertEquals(0f, r.engineGain, 0f)
    }

    @Test fun dialAdjustsCutInHalfDbDetentsWithinFloorAndZero() {
        val r = Rig(7)
        r.c.beginCalibration()
        // 36 degrees = 1% = 0.5 dB, clockwise is less cut.
        r.c.setOutputPercent(r.c.adjustByDegrees(36f))
        assertEquals(-2.0f, r.c.calibrationSession!!.cutDb, 0f)
        assertEquals(-2.0f, r.engineGain, 0f)
        r.c.setOutputPercent(r.c.adjustByDegrees(10f)) // 0.14 dB: same detent
        assertEquals(-2.0f, r.c.calibrationSession!!.cutDb, 0f)
        r.c.setOutputPercent(r.c.adjustByDegrees(3600f))
        assertEquals(0f, r.c.calibrationSession!!.cutDb, 0f)
        r.c.setOutputPercent(r.c.adjustByDegrees(-36000f))
        assertEquals(-30f, r.c.calibrationSession!!.cutDb, 0f)
        assertTrue(streams(r).isEmpty())
    }

    @Test fun dialOnSideAStoresCutWithoutPlayingIt() {
        val r = Rig(7)
        r.c.beginCalibration()
        r.c.toggleCalibrationSide()
        r.log.clear()
        r.c.setOutputPercent(r.c.adjustByDegrees(-180f))
        assertEquals(-5.0f, r.c.calibrationSession!!.cutDb, 0f)
        assertTrue(r.log.isEmpty())
        assertEquals(0f, r.engineGain, 0f)
    }

    @Test fun saveStoresStepAndCurveBecomesCalibrated() {
        val r = Rig(7)
        var saved: StepCalibration? = null
        r.c.onCalibrationChanged = { saved = it }
        r.c.beginCalibration()
        r.c.setOutputPercent(r.c.adjustByDegrees(-252f)) // -3.5 dB more
        assertTrue(r.c.saveCalibrationStep())
        assertEquals(6f, saved!!.steps[6]!!, 0f)
        assertEquals(CurveSource.CALIBRATED, r.c.curveSource)
        assertFalse(r.c.curveEstimated)
        assertEquals(6f, r.c.curve[7] - r.c.curve[6], 1e-4f)
        assertEquals(6f, r.c.curve[12] - r.c.curve[11], 1e-4f) // uniform from the single pair
    }

    @Test fun saveAtBackendFloorWarnsAndZeroIsRefused() {
        val r = Rig(7)
        r.fxState = r.fxState.copy(floorDb = -6f)
        r.c.beginCalibration()
        r.c.setOutputPercent(r.c.adjustByDegrees(-3600f))
        assertTrue(r.c.saveCalibrationStep())
        assertTrue(r.c.message!!.contains("backend floor"))
        r.c.setOutputPercent(r.c.adjustByDegrees(3600f))
        assertFalse(r.c.saveCalibrationStep())
        assertEquals(1, r.c.calibration.calibratedCount)
    }

    @Test fun severalPairsAndReset() {
        val r = Rig(7)
        r.c.beginCalibration()
        r.c.setOutputPercent(r.c.adjustByDegrees(-252f)) // 6 dB for 6->7
        r.c.saveCalibrationStep()
        r.c.stepCalibrationPair(-1)
        assertEquals(5, r.c.calibrationSession!!.pair)
        r.c.setOutputPercent(r.c.adjustByDegrees(-108f)) // initial 6 (saved neighbour fill) -> 7.5
        r.c.saveCalibrationStep()
        assertEquals(2, r.c.calibration.calibratedCount)
        assertEquals(7.5f, r.c.curve[6] - r.c.curve[5], 1e-4f)
        assertEquals(6f, r.c.curve[7] - r.c.curve[6], 1e-4f)
        var cleared = false
        r.c.onCalibrationChanged = { cleared = it.steps.isEmpty() }
        r.c.resetCalibration()
        assertTrue(cleared)
        assertEquals(CurveSource.ESTIMATED, r.c.curveSource)
    }

    @Test fun lowerPairLowersLevelFirstAndHoldsDeeperCut() {
        val r = Rig(7)
        r.c.beginCalibration()
        r.deferred = true
        r.log.clear()
        r.c.stepCalibrationPair(-1)
        assertEquals("stream:6", r.log.first())
        assertEquals(-2.5f, r.engineGain, 0f)
        r.settle()
        assertEquals(6, r.actual)
        assertEquals(r.c.calibrationSession!!.cutDb, r.engineGain, 0f)
    }

    @Test fun higherPairAppliesCutBeforeRaising() {
        val r = Rig(7)
        r.c.beginCalibration()
        r.log.clear()
        r.c.stepCalibrationPair(1)
        assertTrue(r.log.first().startsWith("gain:-2.5"))
        assertEquals("stream:8", r.log[1])
        assertEquals(7, r.c.calibrationSession!!.pair)
    }

    @Test fun externalLevelChangeStopsCalibrationWithoutSaving() {
        val r = Rig(7)
        r.c.beginCalibration()
        r.actual = 9
        r.c.onVolumeEvent()
        assertNull(r.c.calibrationSession)
        assertTrue(r.c.message!!.contains("outside calibration"))
        assertEquals(0, r.c.calibration.calibratedCount)
    }

    @Test fun endingCalibrationKeepsLoudnessAndDoesNotWriteStream() {
        val r = Rig(7)
        r.c.beginCalibration()
        r.log.clear()
        r.c.endCalibration()
        assertNull(r.c.calibrationSession)
        assertTrue(r.log.isEmpty())
        assertEquals(6f, r.c.rawLevel, 0.001f) // 7 with -2.5 dB = level 6 on the estimate
    }

    @Test fun calibratedStepIsUsedForGapFill() {
        val r = Rig(7)
        r.c.setCalibration(StepCalibration(15).with(7, 6f), null)
        r.level(7.5f)
        assertEquals(8, r.actual)
        assertEquals(-3f, r.engineGain, 0.001f)
    }

    // ---------------- Boundary timing ----------------

    @Test fun deferredUpwardCrossingNeverDropsTheTargetCut() {
        val r = Rig(7)
        r.deferred = true
        r.level(7.2f) // target 8 with -2.0
        val firstGain = r.log.first()
        assertTrue(firstGain.startsWith("gain:-2.0"))
        assertEquals("stream:8", r.log[1])
        val sent = mutableListOf<Float>()
        repeat(5) { i ->
            r.time = 80L * (i + 1)
            r.engineGain = 0f
            r.c.onVolumeEvent()
            sent += r.engineGain
        }
        assertTrue(sent.all { it <= -2.0f + 1e-4f })
        r.settle()
        assertEquals(-2.0f, r.engineGain, 1e-4f)
    }

    @Test fun bluetoothDownwardCrossingCompensatesDuringHoldThenReleases() {
        val r = Rig(8)
        r.c.remoteHoldMs = 350
        r.level(7.6f) // still plans level 8 (cut only)
        r.log.clear()
        r.level(6.88f) // plans level 7 with -0.3 dB
        assertEquals(7, r.actual) // phone read-back already lower
        assertTrue(r.engineGain <= -2.7f) // compensates for the speaker possibly still at 8
        r.time = 200
        r.c.onVolumeEvent()
        assertTrue(r.engineGain <= -2.7f)
        r.time = 400
        r.c.onVolumeEvent()
        assertEquals(-0.3f, r.engineGain, 0.051f)
    }

    @Test fun speakerDownwardCrossingHasNoHold() {
        val r = Rig(8)
        r.level(6.88f)
        assertEquals(7, r.actual)
        assertEquals(-0.3f, r.engineGain, 0.051f)
    }

    @Test fun rapidDownwardWritesHoldTheHighestRecentLevel() {
        val r = Rig(9)
        r.c.remoteHoldMs = 350
        r.level(7.9f)
        r.time = 50
        r.level(6.95f)
        assertEquals(7, r.actual)
        assertTrue(r.engineGain <= -5f) // compensates from 9, the highest level within the hold
    }

}
