package com.oiuy.volumecontrol.buttons

import android.media.AudioAttributes

/**
 * Output device types (AudioDeviceInfo.getType()) seen at the moment of a key press.
 *
 * @property defaultMediaOutputTypes where media goes by default
 *   (AudioManager.getAudioDevicesForAttributes, API 33+), or null when unknown.
 * @property playbackOutputTypes one entry per media playback that Android reports
 *   (AudioManager.getActivePlaybackConfigurations); null where its device is unknown.
 */
data class PlaybackRoutes(
    val defaultMediaOutputTypes: Set<Int>?,
    val playbackOutputTypes: List<Int?>,
)

/**
 * Samsung's Separate app sound can send one app to another output (for example a browser to
 * the phone speaker while music plays over Bluetooth). Samsung then decides which output the
 * volume buttons change. A fine write always changes the default media output, so the
 * service hands the press back to Samsung whenever media is playing on more than one output,
 * or on an output other than the default.
 */
object PlaybackRouting {
    /** Usages that play on the media stream. Notification, touch and alarm sounds are ignored. */
    fun isMediaUsage(usage: Int): Boolean = when (usage) {
        AudioAttributes.USAGE_MEDIA, AudioAttributes.USAGE_GAME, AudioAttributes.USAGE_UNKNOWN -> true
        else -> false
    }

    /** True when the press should go to Samsung's own volume handling. */
    fun separateOutputsActive(routes: PlaybackRoutes?): Boolean {
        if (routes == null) return false
        val known = routes.playbackOutputTypes.filterNotNull().toSet()
        if (known.size >= 2) return true
        if (known.isEmpty()) return false
        val defaults = routes.defaultMediaOutputTypes
        if (defaults.isNullOrEmpty()) return false
        return known.any { it !in defaults }
    }
}
