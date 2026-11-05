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
 * Two-tier detent feedback for the volume dial.
 *
 * Callers report tick crossings via [onTickStep]. Pass [major] = true when any
 * crossed tick is a whole-percent major. At most one pulse per call (fast multi-
 * tick frames produce one click, not a buzz).
 *
 * Arbitration ([DialHapticPolicy]): majors are not dropped because a fine fired
 * recently; fines are suppressed for [DialHapticPolicy.MAJOR_PROTECT_MS] after a
 * major so a short one-shot cannot replace the longer major vibration on device.
 */
class DialTickHaptics(context: Context) {
    private val vibrator: Vibrator? = resolveVibrator(context.applicationContext)
    private var lastPulseUptimeMs: Long = DialHapticPolicy.initialLastPulseUptimeMs()
    private var lastMajorUptimeMs: Long = DialHapticPolicy.initialLastMajorUptimeMs()

    /**
     * Call once per input frame. Does nothing when [ticksCrossed] is zero and
     * [major] is false.
     */
    fun onTickStep(view: View, ticksCrossed: Int, major: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        when (
            DialHapticPolicy.decide(
                ticksCrossed = ticksCrossed,
                majorCrossing = major,
                nowMs = now,
                lastPulseUptimeMs = lastPulseUptimeMs,
                lastMajorUptimeMs = lastMajorUptimeMs,
            )
        ) {
            DialHapticPolicy.Pulse.NONE -> return
            DialHapticPolicy.Pulse.MAJOR -> {
                lastPulseUptimeMs = now
                lastMajorUptimeMs = now
                view.performHapticFeedback(
                    HapticFeedbackConstants.LONG_PRESS,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                )
                pulseVibrator(MAJOR_ONE_SHOT_MS, MAJOR_ONE_SHOT_AMPLITUDE)
            }
            DialHapticPolicy.Pulse.FINE -> {
                lastPulseUptimeMs = now
                view.performHapticFeedback(
                    fineTickConstant(),
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                )
                pulseVibrator(FINE_ONE_SHOT_MS, FINE_ONE_SHOT_AMPLITUDE)
            }
        }
    }

    private fun fineTickConstant(): Int = when {
        Build.VERSION.SDK_INT >= 34 -> HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 -> HapticFeedbackConstants.TEXT_HANDLE_MOVE
        else -> HapticFeedbackConstants.CLOCK_TICK
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
        /** Light fine tick (same feel as pre.1 / early builds). */
        private const val FINE_ONE_SHOT_MS: Long = 5L
        private const val FINE_ONE_SHOT_AMPLITUDE: Int = 65

        /** Strong whole-percent detent: longer and full amplitude. */
        private const val MAJOR_ONE_SHOT_MS: Long = 28L
        private const val MAJOR_ONE_SHOT_AMPLITUDE: Int = 255

        private fun resolveVibrator(context: Context): Vibrator? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
    }
}
