package com.oiuy.volumecontrol.volume

/**
 * Pure arbitration for dial haptics (no Android types).
 *
 * One detent pulse per frame in which the actual media level changed. Several
 * levels in one frame still produce one pulse. A short minimum interval keeps
 * rapid changes from turning into a buzz.
 */
object DialHapticPolicy {
    const val MIN_INTERVAL_MS: Long = 12L

    fun shouldPulse(levelsChanged: Int, nowMs: Long, lastPulseUptimeMs: Long): Boolean {
        if (levelsChanged == 0) return false
        return nowMs - lastPulseUptimeMs >= MIN_INTERVAL_MS
    }

    /** Initial timestamp so the first pulse is not blocked by a fake t=0 event. */
    fun initialLastPulseUptimeMs(): Long = -MIN_INTERVAL_MS
}
