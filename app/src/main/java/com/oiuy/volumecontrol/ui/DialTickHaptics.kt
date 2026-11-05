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

/**
 * Two-tier detent feedback for the volume dial.
 *
 * Callers report tick crossings via [onTickStep]. Pass [major] = true for a
 * whole-percent detent (thicker bezel mark); otherwise a fine tick. At most one
 * pulse is emitted per call (a fast spin that crosses several ticks in one
 * frame produces one click, not a buzz). Pulses closer together than
 * [MIN_INTERVAL_MS] are dropped. A major pulse resets the shared timestamp so a
 * fine tick cannot land on top of it.
 *
 * Each pulse uses a platform haptic constant on the view and a direct vibrator
 * one-shot. The one-shot is what keeps the tap solid when system touch feedback
 * is faint or off. Composition primitives are not preferred here.
 */
class DialTickHaptics(context: Context) {
    private val vibrator: Vibrator? = resolveVibrator(context.applicationContext)
    private var lastPulseUptimeMs: Long = 0L

    /**
     * Call once per input frame. Does nothing when [ticksCrossed] is zero and
     * [major] is false.
     */
    fun onTickStep(view: View, ticksCrossed: Int, major: Boolean = false) {
        if (ticksCrossed == 0 && !major) return
        val now = SystemClock.uptimeMillis()
        if (now - lastPulseUptimeMs < MIN_INTERVAL_MS) return
        lastPulseUptimeMs = now

        if (major) {
            view.performHapticFeedback(
                HapticFeedbackConstants.LONG_PRESS,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
            )
            pulseVibrator(MAJOR_ONE_SHOT_MS, MAJOR_ONE_SHOT_AMPLITUDE)
        } else {
            view.performHapticFeedback(
                fineTickConstant(),
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
            )
            pulseVibrator(FINE_ONE_SHOT_MS, FINE_ONE_SHOT_AMPLITUDE)
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
        const val MIN_INTERVAL_MS: Long = 12L

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
