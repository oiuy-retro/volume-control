package com.oiuy.volumecontrol.volume

/**
 * Pure arbitration for dial tick haptics (no Android types).
 *
 * Within a frame the caller already collapses multi-tick motion to one pulse
 * and prefers major when any crossed tick is major. Across frames:
 * - A **major** may fire even if a fine pulse was recent (major wins).
 * - A **fine** is suppressed for [MAJOR_PROTECT_MS] after a major so a short
 *   one-shot cannot replace/cancel the longer major vibration on device.
 * - Fines (and majors vs majors) still respect [MIN_INTERVAL_MS] anti-buzz.
 */
object DialHapticPolicy {
    const val MIN_INTERVAL_MS: Long = 12L

    /** Match the major vibrator one-shot duration so fines cannot cut it short. */
    const val MAJOR_PROTECT_MS: Long = 28L

    enum class Pulse {
        NONE,
        FINE,
        MAJOR,
    }

    /**
     * @param lastPulseUptimeMs uptime of the last fine or major pulse (0 if none)
     * @param lastMajorUptimeMs uptime of the last major pulse (0 if none)
     */
    fun decide(
        ticksCrossed: Int,
        majorCrossing: Boolean,
        nowMs: Long,
        lastPulseUptimeMs: Long,
        lastMajorUptimeMs: Long,
    ): Pulse {
        if (ticksCrossed == 0 && !majorCrossing) return Pulse.NONE

        if (majorCrossing) {
            // Majors are not blocked by a recent fine; only by a recent major.
            if (nowMs - lastMajorUptimeMs < MIN_INTERVAL_MS) {
                return Pulse.NONE
            }
            return Pulse.MAJOR
        }

        if (nowMs - lastMajorUptimeMs < MAJOR_PROTECT_MS) {
            return Pulse.NONE
        }
        if (nowMs - lastPulseUptimeMs < MIN_INTERVAL_MS) {
            return Pulse.NONE
        }
        return Pulse.FINE
    }

    /** Initial timestamp so the first pulse is not blocked by a fake t=0 event. */
    fun initialLastPulseUptimeMs(): Long = -MIN_INTERVAL_MS

    fun initialLastMajorUptimeMs(): Long = -MAJOR_PROTECT_MS
}
