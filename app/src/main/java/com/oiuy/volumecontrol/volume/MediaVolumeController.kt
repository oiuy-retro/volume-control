package com.oiuy.volumecontrol.volume

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock

/**
 * Thin wrapper around [AudioManager] for STREAM_MUSIC.
 * Changing media volume does not require a runtime permission.
 *
 * Media volume is a whole-number index from 0 to [maxVolume] (device-dependent,
 * read every time). Apps cannot set anything finer for the global stream, so
 * this class only ever reports the actual index read back from the system.
 */
class MediaVolumeController(context: Context) : StreamVolumePort {
    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val tracker = LevelTracker(this)
    private var lastShowUiUptimeMs: Long = 0L

    override val maxIndex: Int
        get() = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    override val currentIndex: Int
        get() = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    override fun setIndex(index: Int) {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
    }

    val maxVolume: Int get() = maxIndex
    val currentVolume: Int get() = currentIndex

    fun syncFromSystem() = tracker.syncFromSystem()

    /** Start of a dial drag: retry any level the system refused last gesture. */
    fun beginGesture() = tracker.beginGesture()

    fun clampRotationDegrees(deltaDegrees: Float): Float = tracker.clampRotation(deltaDegrees)

    /** Apply a dial rotation. Returns the actual level change (often none). */
    fun adjustByDegrees(deltaDegrees: Float): LevelChange = tracker.rotate(deltaDegrees)

    /** Widget +/−: move about 10% of the range in whole levels. Returns the actual index. */
    fun stepByWidget(direction: Int): Int = tracker.stepByWidget(direction)

    /**
     * Bring up the system media-volume panel the same way a hardware volume
     * key does, without changing the index. Rate-limited.
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
