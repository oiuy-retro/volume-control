package com.oiuy.volumecontrol.volume

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DialHapticPolicyTest {
    private val init = DialHapticPolicy.initialLastPulseUptimeMs()

    @Test
    fun noPulseWithoutRealLevelChange() {
        assertFalse(DialHapticPolicy.shouldPulse(0, 1000L, init))
    }

    @Test
    fun pulsesOnLevelChangeEitherDirection() {
        assertTrue(DialHapticPolicy.shouldPulse(1, 0L, init))
        assertTrue(DialHapticPolicy.shouldPulse(-1, 0L, init))
        assertTrue(DialHapticPolicy.shouldPulse(3, 0L, init))
    }

    @Test
    fun rateLimitsBackToBackChanges() {
        assertFalse(DialHapticPolicy.shouldPulse(1, 105L, 100L))
        assertTrue(DialHapticPolicy.shouldPulse(1, 112L, 100L))
    }
}
