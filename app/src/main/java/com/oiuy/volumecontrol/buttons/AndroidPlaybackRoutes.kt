package com.oiuy.volumecontrol.buttons

import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Reads [PlaybackRoutes] with public AudioManager calls only. Returns null below API 31,
 * where a playback's output device is not available, or if a call fails.
 */
class AndroidPlaybackRoutes(private val audioManager: AudioManager) {
    fun snapshot(): PlaybackRoutes? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return try {
            read()
        } catch (_: RuntimeException) {
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun read(): PlaybackRoutes {
        val playbacks = audioManager.activePlaybackConfigurations
            .filter { PlaybackRouting.isMediaUsage(it.audioAttributes.usage) }
            .map { config ->
                // Deprecated in API 36 for multi-device output; the single device is still reported.
                @Suppress("DEPRECATION")
                config.audioDeviceInfo?.type
            }
        val defaults = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            defaultMediaOutputs()
        } else {
            null
        }
        return PlaybackRoutes(defaults, playbacks)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun defaultMediaOutputs(): Set<Int> =
        audioManager.getAudioDevicesForAttributes(MEDIA).map { it.type }.toSet()

    private companion object {
        val MEDIA: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }
}
