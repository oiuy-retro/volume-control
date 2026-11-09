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

}
