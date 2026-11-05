package com.oiuy.volumecontrol.volume

import android.content.Context
import android.media.AudioManager

/**
 * Thin wrapper around [AudioManager] for STREAM_MUSIC.
 * Changing media volume does not require a runtime permission.
 *
 * Keeps a fractional level so fine dial turns accumulate correctly against
 * coarse stream indices (often only ~15 steps).
 */
class MediaVolumeController(context: Context) {
    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var fractionalLevel: Float = currentVolume.toFloat()

    val maxVolume: Int
        get() = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    val currentVolume: Int
        get() = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    val percent: Int
        get() = DialMath.percentOf(currentVolume, maxVolume)

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

    fun adjustByDegrees(deltaDegrees: Float): Int {
        val delta = DialMath.volumeDeltaFromDegrees(deltaDegrees, maxVolume)
        // Resync if the system volume was changed elsewhere since last adjust.
        if (kotlin.math.abs(fractionalLevel - currentVolume) > 1.01f) {
            syncFromSystem()
        }
        fractionalLevel = DialMath.clampFractional(fractionalLevel + delta, maxVolume)
        val next = DialMath.toStreamIndex(fractionalLevel, maxVolume)
        if (next != currentVolume) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
        }
        return next
    }

    fun adjustByPercentStep(percentOfMax: Float): Int {
        val next = DialMath.applyPercentStep(currentVolume, percentOfMax, maxVolume)
        setVolume(next)
        return next
    }
}
