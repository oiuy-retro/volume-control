package com.oiuy.volumecontrol.volume

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Dial and widget volume control in "units" of the active [VolumeEngine] mode:
 * Samsung fine units (public max × 10) when available, otherwise Android's
 * integer media steps.
 *
 * Keeps a fractional level so dial turns accumulate smoothly; writes only when
 * the rounded integer target changes, coalesced through [CoalescingWriter].
 * One full dial turn is [unitsPerTurn] units ([TickDial]): every dial tick is
 * one output unit.
 * [syncFromSystem] only reads.
 */
class MediaVolumeController(
    private val engine: VolumeEngine,
    scheduler: CoalescingWriter.Scheduler,
    private val now: () -> Long,
) {
    constructor(context: Context) : this(
        engine = createVolumeEngine(context),
        scheduler = HandlerScheduler(),
        now = SystemClock::uptimeMillis,
    )

    private val writer = CoalescingWriter(now, WRITE_INTERVAL_MS, scheduler, ::writeNow)
    private var fractionalLevel: Float = 0f
    private var lastShowUiUptimeMs: Long = Long.MIN_VALUE / 2

    /** Called after a fine failure switched the engine to Android steps. */
    var onModeChanged: (() -> Unit)? = null

    var maxUnits: Int = 0
        private set

    init {
        syncFromSystem()
    }

    val mode: VolumeMode get() = engine.mode
    val fallbackReason: String? get() = engine.fallbackReason

    /** Integer target the dial currently represents. */
    val currentUnits: Int get() = DialMath.toStreamIndex(fractionalLevel, maxUnits)

    /** Whole percent for the widget. */
    val percent: Int get() = DialMath.percentOf(currentUnits, maxUnits)

    fun fractionalPercent(): Float = DialMath.fractionalPercentOf(fractionalLevel, maxUnits)

    /** Raw fractional level in units (0..[maxUnits]). */
    fun level(): Float = fractionalLevel

    /** Units (and dial ticks) per full turn for the active mode. */
    val unitsPerTurn: Int get() = TickDial.unitsPerTurn(mode)

    fun statusText(): String = volumeStatusText(mode, currentUnits, maxUnits, fallbackReason)

    /** Read only: drops any pending write and rebases on the system value. */
    fun syncFromSystem() {
        val value = engine.readUnits()
        maxUnits = engine.maxUnits()
        fractionalLevel = value.coerceIn(0, maxUnits).toFloat()
        writer.reset(value)
    }

    /**
     * Apply a dial rotation: one full turn = [unitsPerTurn] units. Writes only
     * when the rounded unit changes. Returns the new raw level in units.
     */
    fun adjustByDegrees(deltaDegrees: Float): Float {
        if (deltaDegrees == 0f) return fractionalLevel
        val delta = TickDial.unitsFromDegrees(deltaDegrees, unitsPerTurn)
        fractionalLevel = TickDial.clampLevel(fractionalLevel + delta, maxUnits)
        writer.submit(TickDial.tickIndex(fractionalLevel, maxUnits))
        return fractionalLevel
    }

    /** Write any coalesced target now (end of gesture). */
    fun flush() = writer.flush()

    /** Widget ±: reads the system value, then writes once. Returns the new units. */
    fun adjustByPercentStep(percentOfMax: Float): Int {
        syncFromSystem()
        val next = DialMath.applyPercentStep(currentUnits, percentOfMax, maxUnits)
        if (next != currentUnits) {
            if (engine.writeUnits(next) == WriteResult.MODE_CHANGED) {
                syncFromSystem()
                onModeChanged?.invoke()
                return currentUnits
            }
            fractionalLevel = next.toFloat()
            writer.reset(next)
        }
        return next
    }

    /** System volume panel on tick crossings, Android-steps mode only, rate-limited. */
    fun showSystemVolumeUi() {
        val t = now()
        if (t - lastShowUiUptimeMs < SHOW_UI_MIN_INTERVAL_MS) return
        lastShowUiUptimeMs = t
        engine.showSystemUi()
    }

    private fun writeNow(target: Int) {
        if (engine.writeUnits(target) == WriteResult.MODE_CHANGED) {
            // Units changed meaning (e.g. 150 -> 15). Rebase; do not write the old target.
            syncFromSystem()
            onModeChanged?.invoke()
        }
    }

    private class HandlerScheduler : CoalescingWriter.Scheduler {
        private val handler = Handler(Looper.getMainLooper())
        private val token = Any()

        override fun schedule(delayMs: Long, action: () -> Unit) {
            handler.postAtTime({ action() }, token, SystemClock.uptimeMillis() + delayMs)
        }

        override fun cancel() = handler.removeCallbacksAndMessages(token)
    }

    companion object {
        const val SHOW_UI_MIN_INTERVAL_MS: Long = 120L
        /** About two display frames at 60 Hz; latest target wins. */
        const val WRITE_INTERVAL_MS: Long = 30L
    }
}
