package com.oiuy.volumecontrol.volume

import org.junit.Assert.assertEquals
import org.junit.Test

class DialHapticPolicyTest {
    @Test
    fun majorWinsOverRecentFine() {
        // Fine at t=100; major requested at t=105 (< 12ms after fine) must still fire
        val pulse = DialHapticPolicy.decide(
            ticksCrossed = 3,
            majorCrossing = true,
            nowMs = 105L,
            lastPulseUptimeMs = 100L,
            lastMajorUptimeMs = DialHapticPolicy.initialLastMajorUptimeMs(),
        )
        assertEquals(DialHapticPolicy.Pulse.MAJOR, pulse)
    }

    @Test
    fun fineSuppressedDuringMajorProtectWindow() {
        val pulse = DialHapticPolicy.decide(
            ticksCrossed = 1,
            majorCrossing = false,
            nowMs = 120L,
            lastPulseUptimeMs = 100L,
            lastMajorUptimeMs = 100L,
        )
        assertEquals(DialHapticPolicy.Pulse.NONE, pulse)
    }

    @Test
    fun fineAllowedAfterMajorProtectExpires() {
        val pulse = DialHapticPolicy.decide(
            ticksCrossed = 1,
            majorCrossing = false,
            nowMs = 100L + DialHapticPolicy.MAJOR_PROTECT_MS,
            lastPulseUptimeMs = 100L,
            lastMajorUptimeMs = 100L,
        )
        assertEquals(DialHapticPolicy.Pulse.FINE, pulse)
    }

    @Test
    fun fineRateLimitedAgainstPriorFine() {
        val pulse = DialHapticPolicy.decide(
            ticksCrossed = 1,
            majorCrossing = false,
            nowMs = 110L,
            lastPulseUptimeMs = 100L,
            lastMajorUptimeMs = DialHapticPolicy.initialLastMajorUptimeMs(),
        )
        assertEquals(DialHapticPolicy.Pulse.NONE, pulse)
    }

    @Test
    fun majorRateLimitedAgainstPriorMajorOnly() {
        val blocked = DialHapticPolicy.decide(
            ticksCrossed = 2,
            majorCrossing = true,
            nowMs = 105L,
            lastPulseUptimeMs = 100L,
            lastMajorUptimeMs = 100L,
        )
        assertEquals(DialHapticPolicy.Pulse.NONE, blocked)

        val allowed = DialHapticPolicy.decide(
            ticksCrossed = 2,
            majorCrossing = true,
            nowMs = 100L + DialHapticPolicy.MIN_INTERVAL_MS,
            lastPulseUptimeMs = 100L,
            lastMajorUptimeMs = 100L,
        )
        assertEquals(DialHapticPolicy.Pulse.MAJOR, allowed)
    }

    @Test
    fun noPulseWhenNothingCrossed() {
        assertEquals(
            DialHapticPolicy.Pulse.NONE,
            DialHapticPolicy.decide(0, false, 100L, 0L, 0L),
        )
    }
}
