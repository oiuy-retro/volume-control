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
 * Light detent feedback for the volume dial.
 *
 * Callers report integer tick changes via [onTickStep]. At most one pulse is
 * emitted per call (so a fast spin that crosses several ticks in one frame
 * produces one click, not a buzz), and pulses closer together than
 * [MIN_INTERVAL_MS] are dropped.
 *
 * Each pulse uses the platform haptic constant on the view and also a short
 * vibrator effect, because some devices ignore the constants or the user has
 * system touch feedback turned off.
 */
class DialTickHaptics(context: Context) {
    private val vibrator: Vibrator? = resolveVibrator(context.applicationContext)
    private var lastPulseUptimeMs: Long = 0L

    private val useLowTickPrimitive: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            vibrator?.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
            ) == true

    /**
     * Call once per input frame with how many ticks were crossed (signed).
     * Does nothing when [ticksCrossed] is zero.
     */
    fun onTickStep(view: View, ticksCrossed: Int) {
        if (ticksCrossed == 0) return
        val now = SystemClock.uptimeMillis()
        if (now - lastPulseUptimeMs < MIN_INTERVAL_MS) return
        lastPulseUptimeMs = now

        view.performHapticFeedback(tickConstant())
        pulseVibrator()
    }

    private fun tickConstant(): Int = when {
        Build.VERSION.SDK_INT >= 34 -> HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 -> HapticFeedbackConstants.TEXT_HANDLE_MOVE
        else -> HapticFeedbackConstants.CLOCK_TICK
    }

    private fun pulseVibrator() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            val effect = if (useLowTickPrimitive) {
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 1f)
                    .compose()
            } else {
                VibrationEffect.createOneShot(ONE_SHOT_MS, ONE_SHOT_AMPLITUDE)
            }
            v.vibrate(effect)
        } catch (_: RuntimeException) {
            // Some OEM vibrator services throw on unsupported effects; ticks are best-effort.
        }
    }

    companion object {
        const val MIN_INTERVAL_MS: Long = 12L
        private const val ONE_SHOT_MS: Long = 5L
        private const val ONE_SHOT_AMPLITUDE: Int = 65

        private fun resolveVibrator(context: Context): Vibrator? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
    }
}
