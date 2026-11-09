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
    var curveSource = CurveSource.ESTIMATED
        private set
    var calibration = StepCalibration(volume.maxIndex)
        private set
    var calibrationSession: CalibrationSession? = null
        private set
    /** Persist callback for saved / reset calibration of the current output. */
    var onCalibrationChanged: (StepCalibration) -> Unit = {}
    /**
     * Remote-volume hold (Bluetooth absolute volume): the phone reads back a lower index
     * immediately, but the speaker may still play the old level for a moment. During this
     * window the cut is computed as if the old, louder level were still playing.
     */
    var remoteHoldMs = 0L
    private var holdFrom = -1
    private var holdUntil = 0L
    private var lastMeasured: FloatArray? = null
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

    val percent: Float get() = calibrationSession?.let { calibrationPercent(it.rawCutDb) }
        ?: DialMath.fractionalPercentOf(rawLevel, volume.maxIndex)
    val settled: Boolean get() = expectedIndex == null
    val fineConfirmed: Boolean get() = effect.state.let {
        it.active && it.enabledReported && it.hasControl && it.confirmedMatchesRequest
    }

    fun updateCurve(measured: FloatArray?) {
        lastMeasured = measured?.copyOf()
        val max = volume.maxIndex
        if (calibration.maxIndex != max) calibration = StepCalibration(max)
        val calibrated = calibration.curve()
        val measuredOk = measured != null && measured.size == max + 1 && HybridMath.curveIsUsable(measured)
        when {
            // Ear-matched steps beat both Android's reported curve and the flat estimate.
            calibrated != null -> { curve = calibrated; curveSource = CurveSource.CALIBRATED }
            measuredOk -> { curve = measured!!.copyOf(); curveSource = CurveSource.MEASURED }
            else -> { curve = HybridMath.estimatedCurve(max); curveSource = CurveSource.ESTIMATED }
        }
        curveEstimated = curveSource == CurveSource.ESTIMATED
    }

    /** Load the saved calibration for the current output (or an empty one) and rebuild the curve. */
    fun setCalibration(cal: StepCalibration, measured: FloatArray? = lastMeasured) {
        calibration = if (cal.maxIndex == volume.maxIndex) cal else StepCalibration(volume.maxIndex)
        updateCurve(measured)
    }

    fun adjustByDegrees(degrees: Float): Float {
        calibrationSession?.let { c ->
            val raw = (c.rawCutDb + degrees / DialMath.FULL_TURN_DEGREES * CAL_DB_PER_TURN)
                .coerceIn(floorDb(), 0f)
            calibrationSession = c.copy(rawCutDb = raw)
            return percent
        }
        if (!diagnostic) {
            rawLevel = DialMath.clampFractional(
                rawLevel + DialMath.volumeDeltaFromDegrees(degrees, volume.maxIndex), volume.maxIndex,
            )
        }
        return percent
    }

    fun setOutputPercent(percent: Float) {
        calibrationSession?.let { c ->
            val cut = StepCalibration.snapToDetent(floorDb() + percent / CAL_PERCENT_PER_DB)
                .coerceIn(floorDb(), 0f)
            if (cut != c.cutDb) {
                calibrationSession = c.copy(cutDb = cut)
                // On A the cut is only stored; it is audible on B.
                if (c.side == CalibrationSide.B) reapplyGain()
            }
            return
        }
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
        val before = volume.index
        if (remoteHoldMs > 0 && next < before) {
            val now = nowMs()
            holdFrom = if (now < holdUntil) maxOf(holdFrom, before) else before
            holdUntil = now + remoteHoldMs
        }
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
        calibrationSession?.let { c ->
            val pending = expectedIndex
            if (pending != null && nowMs() >= ownWriteUntil) expectedIndex = null
            if (pending == null && changed && actual != c.targetIndex) {
                endCalibration()
                message = "Android level changed outside calibration (now $actual). Calibration stopped; nothing new was saved."
                return changed
            }
            if (forceReapply || changed) reapplyGain()
            return changed
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
        calibrationSession?.let { applyGain(calibrationCut(it), force = true); return }
        applyGain(cutForActualIndex(volume.index), force = true)
    }

    /**
     * Cut to send for what is actually playing. Upward write still pending (actual below target):
     * keep the target's deeper cut. That can briefly be quieter, never louder, when the stream
     * lands later. Downward write pending, or within the remote hold: compensate as if the old,
     * higher level were still playing.
     */
    private fun cutForActualIndex(actual: Int): Float {
        val target = expectedIndex ?: return wantedDb
        val eff = if (nowMs() < holdUntil) maxOf(actual, holdFrom) else actual
        if (eff <= target) return wantedDb
        if (target == 0) return effect.state.floorDb
        val delta = curve[target.coerceAtMost(curve.lastIndex)] -
            curve[eff.coerceAtMost(curve.lastIndex)]
        return GainMath.clampAttenuation(wantedDb + delta, effect.state.floorDb)
    }

    // ---------------- Calibration ----------------

    private fun floorDb() = minOf(0f, effect.state.floorDb)
    private fun calibrationPercent(cutDb: Float) = ((cutDb - floorDb()) * CAL_PERCENT_PER_DB).coerceIn(0f, 100f)
    private fun effectReady() = effect.state.let { it.active && it.enabledReported && it.hasControl }

    /** Side A: cut 0, but keep C while a lower write is pending or within the remote hold. */
    private fun calibrationCut(c: CalibrationSession): Float {
        val wanted = if (c.side == CalibrationSide.B) c.cutDb else 0f
        val stillHigh = volume.index > c.targetIndex || nowMs() < holdUntil
        return if (stillHigh) minOf(wanted, c.holdCutDb) else wanted
    }

    private fun initialCut(pair: Int, atMost: Float): Float {
        val guess = calibration.steps[pair] ?: (curve[pair + 1] - curve[pair])
        // Deeper of the guess and the cut already playing, so starting is never louder.
        val c = minOf(-kotlin.math.abs(guess), atMost)
        val snapped = kotlin.math.floor(c / StepCalibration.DETENT_DB) * StepCalibration.DETENT_DB
        return snapped.coerceIn(floorDb(), 0f)
    }

    /** Start on side B of pair (current-1 -> current). Never writes the stream. */
    fun beginCalibration(): Boolean {
        if (diagnostic) endDiagnostic()
        val cur = volume.index
        if (!effectReady()) {
            message = "Calibration needs the effect attached, enabled and in control."
            return false
        }
        if (cur < 2 || volume.maxIndex < 2) {
            message = "Raise Android volume to level 2 or higher to calibrate. The app will not raise it for you."
            return false
        }
        expectedIndex = null
        val pair = cur - 1
        val cut = initialCut(pair, effect.state.requestedDb)
        calibrationSession = CalibrationSession(pair, CalibrationSide.B, cut, cut)
        if (!applyGain(cut, force = true)) {
            message = "Calibration could not confirm the cut. ${message ?: ""}".trim()
        } else {
            message = null
        }
        return true
    }

    /** A -> B: confirmed cut first, then raise. B -> A: lower first, then remove the cut. */
    fun toggleCalibrationSide() {
        val c = calibrationSession ?: return
        if (c.side == CalibrationSide.A) {
            if (!applyGain(c.cutDb, force = true)) {
                message = "Cut not confirmed, so level ${c.pair + 1} was not played."
                return
            }
            calibrationSession = c.copy(side = CalibrationSide.B, holdCutDb = 0f)
            writeStream(c.pair + 1)
            reapplyGain()
        } else {
            calibrationSession = c.copy(side = CalibrationSide.A, holdCutDb = minOf(c.cutDb, effect.state.requestedDb))
            writeStream(c.pair)
            reapplyGain() // keeps C while the lower level is pending / within the remote hold
        }
    }

    /** Move to an adjacent pair, staying on the same side. Raising is an explicit user action. */
    fun stepCalibrationPair(delta: Int) {
        val c = calibrationSession ?: return
        val pair = (c.pair + delta).coerceIn(1, volume.maxIndex - 1)
        if (pair == c.pair) return
        val cut = initialCut(pair, 0f)
        val next = CalibrationSession(pair, c.side, cut, cut)
        val target = next.targetIndex
        if (target > volume.index) {
            val firstCut = if (next.side == CalibrationSide.B) cut else 0f
            if (!applyGain(firstCut, force = true)) {
                message = "Cut not confirmed, so the higher pair was not played."
                return
            }
            calibrationSession = next.copy(holdCutDb = 0f)
            writeStream(target)
            reapplyGain()
        } else {
            // Lower first; on A, C' (deeper than 0) is held until the lower level has landed.
            // Lower first. Until the lower level has landed, keep the deeper of old and new cut.
            calibrationSession = next.copy(holdCutDb = minOf(c.holdCutDb, effect.state.requestedDb))
            writeStream(target)
            reapplyGain()
        }
        message = null
    }

    /** Save |C| as dB(N+1) - dB(N) for the current pair. */
    fun saveCalibrationStep(): Boolean {
        val c = calibrationSession ?: return false
        if (c.cutDb > -StepCalibration.MIN_STEP_DB) {
            message = "A cut of 0 dB means no step; nothing saved."
            return false
        }
        calibration = calibration.with(c.pair, -c.cutDb)
        onCalibrationChanged(calibration)
        updateCurve(lastMeasured)
        val atFloor = c.cutDb <= floorDb() + 0.01f
        message = if (atFloor) {
            "Saved ${-c.cutDb} dB for ${c.pair}→${c.pair + 1}, but that is the backend floor. " +
                "If B was still louder, the real step is bigger and this step will still jump."
        } else "Saved ${-c.cutDb} dB for level ${c.pair}→${c.pair + 1}."
        return true
    }

    fun resetCalibration() {
        calibration = StepCalibration(volume.maxIndex)
        onCalibrationChanged(calibration)
        updateCurve(lastMeasured)
        message = "Calibration for this output cleared."
    }

    /** Leave calibration where it is (no stream write, current cut kept); dial resumes from here. */
    fun endCalibration() {
        val c = calibrationSession ?: return
        val cut = calibrationCut(c)
        calibrationSession = null
        expectedIndex = null
        wantedDb = cut
        observedIndex = volume.index
        lastEventIndex = observedIndex
        rebaseWithoutVolumeWrite(observedIndex)
        message = "Calibration closed. Dial continues from the current loudness."
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
        holdUntil = 0L
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
        if (calibrationSession != null) endCalibration()
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
        /** Calibration dial: 1% per 0.5 dB detent, so 5 dB per full turn. */
        const val CAL_PERCENT_PER_DB = 2f
        const val CAL_DB_PER_TURN = DialMath.DISPLAY_PERCENT_PER_FULL_TURN / CAL_PERCENT_PER_DB
        /** Default remote-volume hold for Bluetooth routes; a guess at AVRCP latency, not measured. */
        const val BLUETOOTH_HOLD_MS = 350L
    }
}

enum class CurveSource { MEASURED, CALIBRATED, ESTIMATED }
enum class CalibrationSide { A, B }

/** A = level [pair] with no cut; B = level [pair]+1 with cut [cutDb] (≤ 0). */
data class CalibrationSession(
    val pair: Int,
    val side: CalibrationSide,
    val cutDb: Float,
    val rawCutDb: Float,
    /** Cut kept while a lower level is still pending or within the remote hold. */
    val holdCutDb: Float = 0f,
) {
    val targetIndex: Int get() = if (side == CalibrationSide.A) pair else pair + 1
}
