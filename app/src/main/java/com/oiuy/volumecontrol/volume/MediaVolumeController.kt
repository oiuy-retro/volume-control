package com.oiuy.volumecontrol.volume

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock

/**
 * Thin wrapper around [AudioManager] for STREAM_MUSIC.
 * Changing media volume does not require a runtime permission.
 *
 * Keeps a fractional level so fine dial turns accumulate correctly against
 * coarse stream indices (often only ~15 steps). The UI reads
 * [fractionalPercent] so the center readout can update every 0.1% tick
 * even when the integer stream index has not moved yet.
 */
class MediaVolumeController(context: Context) {
    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var fractionalLevel: Float = currentVolume.toFloat()
    private var lastShowUiUptimeMs: Long = 0L

    val maxVolume: Int
        get() = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    val currentVolume: Int
        get() = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    /** Coarse percent from the live integer stream index (widget / system sync). */
    val percent: Int
        get() = DialMath.percentOf(currentVolume, maxVolume)

    /** Fine percent 0..100 from the fractional level (one decimal place is meaningful). */
    fun fractionalPercent(): Float = DialMath.fractionalPercentOf(fractionalLevel, maxVolume)

    fun syncFromSystem() {
        fractionalLevel = currentVolume.toFloat()
    }

    fun setVolume(index: Int) {
        val clamped = index.coerceIn(0, maxVolume)
        if (clamped != currentVolume) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, 0)
        }
        fractionalLevel = clamped.toFloat()
    }

    /**
     * Drive STREAM_MUSIC from a 0..100 display percent **without** moving the
     * raw [fractionalLevel] accumulator. Used as a tiny-magnet output overlay so
     * the next finger delta still continues from the continuous raw percent.
     */
    fun setFractionalPercent(percent: Float, preserveRawAccumulator: Boolean): Float {
        if (maxVolume <= 0) return 0f
        val p = percent.coerceIn(0f, 100f)
        val level = DialMath.clampFractional((p / 100f) * maxVolume.toFloat(), maxVolume)
        val next = DialMath.toStreamIndex(level, maxVolume)
        if (next != currentVolume) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
        }
        if (!preserveRawAccumulator) {
            fractionalLevel = level
        }
        return if (preserveRawAccumulator) {
            // Reported overlay percent (snapped); raw stays in fractionalLevel.
            p
        } else {
            fractionalPercent()
        }
    }

    /**
     * Apply a dial rotation of [deltaDegrees]. Returns the new fractional percent
     * (0..100). The integer stream index is updated only when rounding changes it.
     * Callers should clamp [deltaDegrees] with [DialMath.clampRotationDegrees] first
     * so hard stops at 0% / 100% are respected in the gesture as well.
     */
    fun adjustByDegrees(deltaDegrees: Float): Float {
        if (deltaDegrees == 0f) return fractionalPercent()
        val delta = DialMath.volumeDeltaFromDegrees(deltaDegrees, maxVolume)
        // Resync if the system volume was changed elsewhere since last adjust.
        if (kotlin.math.abs(fractionalLevel - currentVolume) > 1.01f) {
            syncFromSystem()
        }
        fractionalLevel = DialMath.clampFractional(fractionalLevel + delta, maxVolume)
        val next = DialMath.toStreamIndex(fractionalLevel, maxVolume)
        if (next != currentVolume) {
            // No FLAG_SHOW_UI here: the dial shows the system panel on tick
            // crossings via [showSystemVolumeUi], rate-limited separately.
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
        }
        return fractionalPercent()
    }

    fun adjustByPercentStep(percentOfMax: Float): Int {
        val next = DialMath.applyPercentStep(currentVolume, percentOfMax, maxVolume)
        setVolume(next)
        return next
    }

    /**
     * Bring up the system media-volume panel the same way a hardware volume
     * key does, without necessarily changing the integer stream index.
     * Rate-limited so rapid tick crossings do not flood the system UI.
     */
    fun showSystemVolumeUi() {
        val now = SystemClock.uptimeMillis()
        if (now - lastShowUiUptimeMs < SHOW_UI_MIN_INTERVAL_MS) return
        lastShowUiUptimeMs = now
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_SAME,
            AudioManager.FLAG_SHOW_UI,
        )
    }

    companion object {
        const val SHOW_UI_MIN_INTERVAL_MS: Long = 120L
    }
}
