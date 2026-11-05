package com.oiuy.volumecontrol.volume

import android.content.Context
import android.media.AudioManager

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
     * Apply a dial rotation of [deltaDegrees]. Returns the new fractional percent
     * (0..100). The integer stream index is updated only when rounding changes it.
     */
    fun adjustByDegrees(deltaDegrees: Float): Float {
        val delta = DialMath.volumeDeltaFromDegrees(deltaDegrees, maxVolume)
        // Resync if the system volume was changed elsewhere since last adjust.
        if (kotlin.math.abs(fractionalLevel - currentVolume) > 1.01f) {
            syncFromSystem()
        }
        fractionalLevel = DialMath.clampFractional(fractionalLevel + delta, maxVolume)
        val next = DialMath.toStreamIndex(fractionalLevel, maxVolume)
        if (next != currentVolume) {
            // No FLAG_SHOW_UI here: the dial shows the system panel on tick
            // crossings via [showSystemVolumeUi], so the panel appears once per
            // tick frame rather than on every sub-tick pixel move.
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
     * Safe to call once per tick frame (match haptic "one pulse per frame").
     */
    fun showSystemVolumeUi() {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_SAME,
            AudioManager.FLAG_SHOW_UI,
        )
    }
}
