/*
 * Copyright 2026 Volume Control contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.oiuy.volumecontrol.ui

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import com.oiuy.volumecontrol.volume.DialHapticPolicy

/**
 * Detent feedback for the volume dial.
 *
 * Fires only when the actual media level (STREAM_MUSIC index) changed in a
 * frame. Several levels in one frame play one pulse ([DialHapticPolicy]).
 */
class DialTickHaptics(context: Context) {
    private val vibrator: Vibrator? = resolveVibrator(context.applicationContext)
    private var lastPulseUptimeMs: Long = DialHapticPolicy.initialLastPulseUptimeMs()

    /** Call once per input frame with the signed number of real levels changed. */
    fun onLevelChanged(view: View, levelsChanged: Int) {
        val now = SystemClock.uptimeMillis()
        if (!DialHapticPolicy.shouldPulse(levelsChanged, now, lastPulseUptimeMs)) return
        lastPulseUptimeMs = now
        view.performHapticFeedback(
            HapticFeedbackConstants.LONG_PRESS,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
        )
        pulseVibrator(LEVEL_ONE_SHOT_MS, LEVEL_ONE_SHOT_AMPLITUDE)
    }

    private fun pulseVibrator(durationMs: Long, amplitude: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            v.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
        } catch (_: RuntimeException) {
            // Some OEM vibrator services throw on unsupported effects; ticks are best-effort.
        }
    }

    companion object {
        /** One clear detent per real level change. */
        private const val LEVEL_ONE_SHOT_MS: Long = 28L
        private const val LEVEL_ONE_SHOT_AMPLITUDE: Int = 255

        private fun resolveVibrator(context: Context): Vibrator? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
    }
}
