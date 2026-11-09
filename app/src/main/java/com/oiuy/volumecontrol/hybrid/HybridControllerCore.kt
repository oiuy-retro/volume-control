package com.oiuy.volumecontrol.hybrid

import com.oiuy.volumecontrol.effect.EffectState
import com.oiuy.volumecontrol.effect.GainMath
import com.oiuy.volumecontrol.volume.DialMath

/** Android reads/writes are injected so deferred/clamped volume and effect failures are testable. */
interface HybridVolumePort {
    val maxIndex: Int
    val index: Int
    fun writeIndex(index: Int)
}

interface HybridEffectPort {
    val state: EffectState
    fun apply(db: Float, force: Boolean): EffectState
}

/**
 * Single-threaded coordinator. Raw finger position is never reconstructed from our own writes.
 * An own write has a bounded settlement window; observers and settlement only reapply gain,
 * never write volume. Thus a delayed BT update cannot trigger a volume/effect feedback loop.
 */
class HybridControllerCore(
    private val volume: HybridVolumePort,
    private val effect: HybridEffectPort,
    private val nowMs: () -> Long,
) {
    var rawLevel = volume.index.toFloat()
        private set
    var outputLevel = rawLevel
        private set
    var observedIndex = volume.index
        private set
    var curve = HybridMath.estimatedCurve(volume.maxIndex)
        private set
    var curveEstimated = true
        private set
    var message: String? = null
        private set
    var diagnostic = false
        private set
    var dragging = false
    private var expectedIndex: Int? = null
    private var ownWriteUntil = 0L
    private var wantedDb = effect.state.requestedDb
    private var lastSentDb = wantedDb
    private var lastEventIndex = observedIndex

    val percent: Float get() = DialMath.fractionalPercentOf(rawLevel, volume.maxIndex)
    val settled: Boolean get() = expectedIndex == null
    val fineConfirmed: Boolean get() = effect.state.let {
        it.active && it.enabledReported && it.hasControl && it.confirmedMatchesRequest
    }

    fun updateCurve(measured: FloatArray?) {
        curveEstimated = measured == null || measured.size != volume.maxIndex + 1 ||
            !HybridMath.curveIsUsable(measured)
        curve = if (curveEstimated) HybridMath.estimatedCurve(volume.maxIndex) else measured!!.copyOf()
    }

    fun adjustByDegrees(degrees: Float): Float {
        if (!diagnostic) {
            rawLevel = DialMath.clampFractional(
                rawLevel + DialMath.volumeDeltaFromDegrees(degrees, volume.maxIndex), volume.maxIndex,
            )
        }
        return percent
    }

    fun setOutputPercent(percent: Float) {
        if (diagnostic) return
        outputLevel = percent.coerceIn(0f, 100f) * volume.maxIndex / 100f
        applyOutput()
    }

    private fun applyGain(db: Float, force: Boolean = false): Boolean {
        val target = GainMath.clampAttenuation(db, effect.state.floorDb)
        val s = effect.apply(target, force)
        lastSentDb = s.requestedDb
        val ok = s.active && s.enabledReported && s.hasControl && s.confirmedMatchesRequest
        if (!ok) message = "Fine control unconfirmed. ${s.message ?: "Check enabled, control and API gain below."}"
        else if (message?.startsWith("Fine control unconfirmed.") == true) message = null
        return ok
    }

    private fun applyOutput() {
        message = null
        val s = effect.state
        // Availability is only provisional: every new cut must be API-confirmed before raising volume.
        val available = s.active && s.enabledReported && s.hasControl
        val plan = HybridMath.plan(outputLevel, volume.maxIndex, curve, s.floorDb, available)
        val cur = volume.index
        observedIndex = cur
        if (!available) {
            message = "Whole Android levels only: effect unavailable, disabled or not controlled."
            // Leave an unknown cut alone: clearing it could cause an unexpected rise.
            writeStream(plan.streamIndex)
            return
        }
        wantedDb = plan.attenuationDb
        if (HybridMath.effectBeforeStream(cur, plan.streamIndex)) {
            if (!applyGain(plan.attenuationDb)) return // do not raise to the upper step on a failed cut
            writeStream(plan.streamIndex)
            // Some vendors replace effect parameters during setStreamVolume. Never trust the cache here.
            reapplyGain()
        } else {
            writeStream(plan.streamIndex)
            // Read back first. If a downward write is deferred, don't prematurely release its cut.
            reapplyGain()
        }
    }

    private fun writeStream(next: Int) {
        if (expectedIndex == next || (volume.index == next && expectedIndex == null)) return
        // Reversing across a boundary must supersede an older deferred request even when
        // Android still reports the desired (old) index. Otherwise the old write wins later.
        expectedIndex = next
        ownWriteUntil = nowMs() + SETTLE_MS
        try {
            volume.writeIndex(next)
        } catch (e: RuntimeException) {
            expectedIndex = null
            message = "Android volume write failed: ${e.javaClass.simpleName}"
        }
        observedIndex = volume.index // actual read-back, NOT the requested index
    }

    /** Called by a real volume event or a finite delayed check, never per finger delta. */
    fun onVolumeEvent(forceReapply: Boolean = true): Boolean {
        val actual = volume.index
        val changed = actual != lastEventIndex
        lastEventIndex = actual
        observedIndex = actual
        // Widget +/- intentionally clears the cut through the shared session. Do not fight it.
        if (effect.state.requestedDb != lastSentDb) {
            wantedDb = effect.state.requestedDb
            lastSentDb = wantedDb
            expectedIndex = null
            rebaseWithoutVolumeWrite(actual)
        }
        val expected = expectedIndex
        if (expected != null) {
            // Keep the raw accumulator, including during the asynchronous old-index interval.
            // Retain the window even after an immediate readback, to absorb delayed vendor updates.
            if (nowMs() >= ownWriteUntil && !dragging) {
                val retainedCut = cutForActualIndex(actual)
                expectedIndex = null
                if (actual != expected) {
                    wantedDb = retainedCut
                    rebaseWithoutVolumeWrite(actual)
                    message = "Android kept level $actual instead of $expected (limit or deferred write). Cut retained."
                }
            }
            if (forceReapply || changed) reapplyGain()
        } else if (changed) {
            rebaseWithoutVolumeWrite(actual)
            // External buttons keep the existing cut; never automatically clear it and raise loudness.
            applyGain(wantedDb, force = true)
        }
        return changed
    }

    fun endDrag() {
        dragging = false
        onVolumeEvent()
    }

    /** Reassert even when requestedDb is unchanged, after a BT / output / lifecycle event. */
    fun reapplyGain() {
        applyGain(cutForActualIndex(volume.index), force = true)
    }

    private fun cutForActualIndex(actual: Int): Float {
        val target = expectedIndex ?: return wantedDb
        if (actual <= target) return wantedDb
        // Async downward write: retain enough attenuation at the still-higher actual index.
        if (target == 0) return effect.state.floorDb
        val delta = curve[target.coerceAtMost(curve.lastIndex)] -
            curve[actual.coerceAtMost(curve.lastIndex)]
        return GainMath.clampAttenuation(wantedDb + delta, effect.state.floorDb)
    }

    fun initialize(measured: FloatArray?) {
        wantedDb = effect.state.requestedDb
        lastSentDb = wantedDb
        updateCurve(measured)
        observedIndex = volume.index
        rebaseWithoutVolumeWrite(observedIndex)
    }

    /** Route changes must not restore an old route's higher stream index. */
    fun onRouteChanged(measured: FloatArray?) {
        wantedDb = effect.state.requestedDb // retain the last sent cut, including async compensation
        expectedIndex = null
        updateCurve(measured)
        observedIndex = volume.index
        lastEventIndex = observedIndex
        rebaseWithoutVolumeWrite(observedIndex)
        reapplyGain()
        message = "Output changed; curve reloaded and cut re-sent. API confirmation is not an audible test."
    }

    private fun rebaseWithoutVolumeWrite(index: Int) {
        // Approximate inverse of the plan, retaining attenuation (no automatic volume rise).
        val db = wantedDb
        rawLevel = if (index <= 0 || db >= 0f) index.toFloat() else {
            val top = index.coerceAtMost(curve.lastIndex)
            val targetDb = curve[top] + db
            if (targetDb <= curve[1]) {
                (1f + (targetDb - curve[1]) / HybridMath.BOTTOM_SPAN_DB).coerceIn(0f, 1f)
            } else {
                val hi = (2..top).firstOrNull { curve[it] >= targetDb } ?: top
                val span = (curve[hi] - curve[hi - 1]).coerceAtLeast(0.05f)
                (hi - 1 + (targetDb - curve[hi - 1]) / span).coerceIn(0f, top.toFloat())
            }
        }
        outputLevel = rawLevel
    }

    /** Diagnostic never writes Android volume. Dial is locked; hardware buttons may still change it. */
    fun beginDiagnostic() {
        diagnostic = true
        expectedIndex = null
        observedIndex = volume.index
        message = "TEST MODE: dial locked; app holds Android level (no volume writes). Start at −12 dB."
        wantedDb = maxOf(-12f, effect.state.floorDb)
        applyGain(wantedDb, force = true)
    }

    fun toggleDiagnosticCut() {
        if (!diagnostic) return
        wantedDb = if (wantedDb < -0.05f) 0f else maxOf(-12f, effect.state.floorDb)
        applyGain(wantedDb, force = true)
    }

    fun endDiagnostic() {
        diagnostic = false
        observedIndex = volume.index
        // Keep the last test cut. Leaving the test must not automatically jump up by 12 dB.
        rebaseWithoutVolumeWrite(observedIndex)
        message = "Test ended; last cut retained until you turn the dial."
    }

    /** Manual reattach restores the previous cut, without a stream write. */
    fun afterReattach() = reapplyGain()

    companion object {
        const val SETTLE_MS = 1000L
    }
}
