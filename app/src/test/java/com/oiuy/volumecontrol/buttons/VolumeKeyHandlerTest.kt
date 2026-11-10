package com.oiuy.volumecontrol.buttons

import com.oiuy.volumecontrol.volume.FakeFine
import com.oiuy.volumecontrol.volume.FakeTime
import com.oiuy.volumecontrol.volume.VolumeButtonSteps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeKeyHandlerTest {
    private class Env(
        var enabled: Boolean = true,
        var step: Int = 1,
        var interactive: Boolean = true,
        var display: Int? = STATE_ON,
        var mode: Int = MODE_NORMAL,
        var max: Int = 15,
    ) : VolumeKeyEnvironment {
        override fun featureEnabled() = enabled
        override fun stepSize() = step
        override fun screenOn() = isScreenOn(interactive, display)
        override fun audioMode() = mode
        override fun publicMax() = max
    }

    private val env = Env()
    private val fine = FakeFine(value = 70)
    private val time = FakeTime()

    private fun handler(port: FakeFine? = fine) =
        VolumeKeyHandler(env, port, time, repeatTimeoutMs = 500, repeatIntervalMs = 50)

    private fun VolumeKeyHandler.down(key: Int, repeat: Int = 0, t: Long = 1L) =
        onKeyEvent(key, ACTION_DOWN, repeat, t)
    private fun VolumeKeyHandler.up(key: Int, t: Long = 1L) = onKeyEvent(key, ACTION_UP, 0, t)

    @Test fun upPressMovesOneFineUnitWithShowUiAndConsumesDownAndUp() {
        val h = handler()
        assertTrue(h.down(UP))
        assertEquals(71, fine.value)
        assertEquals(listOf(VolumeButtonSteps.FLAG_SHOW_UI), fine.writeFlags)
        assertTrue(h.up(UP))
        assertEquals(listOf(71), fine.writes)
    }

    @Test fun downPressUsesStepSize() {
        env.step = 10
        val h = handler()
        assertTrue(h.down(DOWN))
        assertTrue(h.up(DOWN))
        assertEquals(60, fine.value)
    }

    @Test fun stepSizeIsClamped() {
        env.step = 40
        val h = handler()
        h.down(UP); h.up(UP)
        assertEquals(85, fine.value)
        env.step = 0
        h.down(DOWN); h.up(DOWN)
        assertEquals(84, fine.value)
    }

    @Test fun clampsToLiveFineMaxAndStillShowsPanelAtLimit() {
        env.max = 20
        fine.value = 198
        env.step = 5
        val h = handler()
        assertTrue(h.down(UP))
        assertEquals(200, fine.value)
        h.up(UP)
        // At the top: still consumed, same value written once so Samsung can show its panel.
        assertTrue(h.down(UP))
        assertEquals(listOf(200, 200), fine.writes)
        h.up(UP)
    }

    @Test fun clampsAtZero() {
        fine.value = 2
        env.step = 5
        val h = handler()
        assertTrue(h.down(DOWN))
        assertEquals(0, fine.value)
    }

    @Test fun otherKeysAreNeverTouched() {
        val h = handler()
        assertFalse(h.onKeyEvent(KEYCODE_POWER, ACTION_DOWN, 0, 1L))
        assertFalse(h.onKeyEvent(KEYCODE_MUTE, ACTION_DOWN, 0, 1L))
        assertFalse(h.onKeyEvent(KEYCODE_POWER, ACTION_UP, 0, 1L))
        assertEquals(0, fine.reads)
        assertTrue(fine.writes.isEmpty())
    }

    @Test fun passesThroughWhenFeatureOff() {
        env.enabled = false
        val h = handler()
        assertEquals(PassReason.FEATURE_OFF, h.passReason())
        assertFalse(h.down(UP))
        assertFalse(h.up(UP))
        assertTrue(fine.writes.isEmpty())
    }

    @Test fun passesThroughWithoutFinePort() {
        val h = handler(port = null)
        assertEquals(PassReason.NO_FINE_VOLUME, h.passReason())
        assertFalse(h.down(UP))
        assertFalse(h.up(UP))
    }

    @Test fun passesThroughWhenFineMethodsMissing() {
        val h = handler(FakeFine(available = false))
        assertEquals(PassReason.NO_FINE_VOLUME, h.passReason())
        assertFalse(h.down(DOWN))
        assertFalse(h.up(DOWN))
    }

    @Test fun passesThroughWhenMaxUnknown() {
        env.max = 0
        val h = handler()
        assertFalse(h.down(UP))
        assertTrue(fine.writes.isEmpty())
    }


    @Test fun passesThroughInCallRingingOrCommunication() {
        for (mode in listOf(MODE_RINGTONE, MODE_IN_CALL, MODE_IN_COMMUNICATION, MODE_CALL_SCREENING)) {
            env.mode = mode
            val h = handler()
            assertEquals(PassReason.CALL_OR_RINGING, h.passReason())
            assertFalse(h.down(DOWN))
            assertFalse(h.up(DOWN))
        }
        assertTrue(fine.writes.isEmpty())
    }

    @Test fun passesThroughWhenReadFails() {
        fine.readFailure = "boom"
        val h = handler()
        assertNull(h.passReason())
        assertFalse(h.down(UP))
        assertFalse(h.up(UP))
        assertTrue(fine.writes.isEmpty())
    }

    @Test fun passesThroughWhenWriteFails() {
        fine.writeFailure = "denied"
        val h = handler()
        assertFalse(h.down(UP))
        assertFalse(h.up(UP))
        // No timer is left behind for a passed press.
        time.advance(2_000)
        assertEquals(1, fine.writes.size)
    }

    @Test fun recoversOnNextPressAfterFailure() {
        fine.readFailure = "boom"
        val h = handler()
        assertFalse(h.down(UP)); assertFalse(h.up(UP))
        fine.readFailure = null
        assertTrue(h.down(UP)); assertTrue(h.up(UP))
        assertEquals(71, fine.value)
    }

    @Test fun hardwareRepeatsStepEachTimeAndStopTimer() {
        val h = handler()
        assertTrue(h.down(UP))
        assertTrue(h.down(UP, repeat = 1))
        assertTrue(h.down(UP, repeat = 2))
        assertTrue(h.down(UP, repeat = 3))
        assertEquals(74, fine.value)
        // App timer must not add extra steps once real repeats arrive.
        time.advance(1_000)
        assertEquals(74, fine.value)
        assertTrue(h.up(UP))
    }

    @Test fun holdWithoutHardwareRepeatsUsesTimer() {
        val h = handler()
        assertTrue(h.down(DOWN))
        assertEquals(69, fine.value)
        time.advance(499)
        assertEquals(69, fine.value)
        time.advance(1) // 500 ms: first repeat
        assertEquals(68, fine.value)
        repeat(4) { time.advance(50) }
        assertEquals(64, fine.value)
        assertTrue(h.up(DOWN))
        repeat(10) { time.advance(50) }
        assertEquals(64, fine.value)
    }

    @Test fun timerRepeatsStopAtLimitWithoutRewriting() {
        fine.value = 148
        val h = handler()
        h.down(UP)
        time.advance(500)
        assertEquals(150, fine.value)
        val writes = fine.writes.size
        repeat(5) { time.advance(50) }
        assertEquals(writes, fine.writes.size)
        h.up(UP)
    }

    @Test fun timerStopsWhenCallStarts() {
        val h = handler()
        h.down(UP)
        time.advance(500)
        assertEquals(72, fine.value)
        env.mode = MODE_RINGTONE
        repeat(5) { time.advance(50) }
        assertEquals(72, fine.value)
        // The rest of the press stays consumed so Android never sees half a press.
        assertTrue(h.up(UP))
    }

    @Test fun pressPassedAtStartStaysPassedEvenIfConditionsChange() {
        env.enabled = false
        val h = handler()
        assertFalse(h.down(UP))
        env.enabled = true
        assertFalse(h.down(UP, repeat = 1))
        assertFalse(h.up(UP))
        assertTrue(fine.writes.isEmpty())
    }

    @Test fun pressConsumedAtStartStaysConsumedIfFeatureTurnedOff() {
        val h = handler()
        assertTrue(h.down(UP))
        env.enabled = false
        assertTrue(h.down(UP, repeat = 1))
        assertTrue(h.up(UP))
    }

    @Test fun repeatFailureMidPressIsConsumedAndSkipped() {
        val h = handler()
        assertTrue(h.down(UP))
        fine.writeFailure = "denied"
        assertTrue(h.down(UP, repeat = 1))
        assertTrue(h.up(UP))
    }

    @Test fun repeatWithoutSeenStartIsPassedThrough() {
        val h = handler()
        assertFalse(h.down(UP, repeat = 5))
        assertFalse(h.up(UP))
        assertTrue(fine.writes.isEmpty())
    }

    @Test fun strayUpIsPassedThrough() {
        val h = handler()
        assertFalse(h.up(DOWN))
    }

    @Test fun secondKeyTakesOverTheTimer() {
        val h = handler()
        h.down(UP)       // 71
        h.down(DOWN)     // 70
        time.advance(500)
        assertEquals(69, fine.value)
        assertTrue(h.up(UP))
        assertTrue(h.up(DOWN))
    }

    @Test fun resetClearsPressesAndTimer() {
        val h = handler()
        h.down(UP)
        h.reset()
        time.advance(1_000)
        assertEquals(71, fine.value)
        assertFalse(h.up(UP))
    }

    @Test fun callbacksFire() {
        val h = handler()
        val stepped = mutableListOf<Int>()
        var ended = 0
        h.onStepped = { stepped += it }
        h.onPressEnded = { ended++ }
        h.down(UP); h.down(UP, repeat = 1); h.up(UP)
        assertEquals(listOf(71, 72), stepped)
        assertEquals(1, ended)
    }

    @Test fun serviceListParsing() {
        val pkg = "com.oiuy.volumecontrol"
        val cls = "com.oiuy.volumecontrol.buttons.VolumeButtonService"
        assertFalse(isAccessibilityServiceListed(null, pkg, cls))
        assertFalse(isAccessibilityServiceListed("", pkg, cls))
        assertTrue(isAccessibilityServiceListed("$pkg/$cls", pkg, cls))
        assertTrue(isAccessibilityServiceListed("a.b/a.b.C:$pkg/.buttons.VolumeButtonService", pkg, cls))
        assertFalse(isAccessibilityServiceListed("$pkg/.Other:other.pkg/$cls", pkg, cls))
        assertFalse(isAccessibilityServiceListed("garbage", pkg, cls))
    }

    // --- Screen off: always handed back to Android (pre.2's own step is gone) ---

    @Test fun screenStateUsesInteractiveAndDisplayState() {
        assertTrue(isScreenOn(true, STATE_ON))
        assertTrue(isScreenOn(true, STATE_VR))
        assertTrue(isScreenOn(true, STATE_UNKNOWN))
        assertTrue(isScreenOn(true, null))
        for (off in listOf(STATE_OFF, STATE_DOZE, STATE_DOZE_SUSPEND, STATE_ON_SUSPEND)) {
            assertFalse(isScreenOn(true, off))
        }
        assertFalse(isScreenOn(false, STATE_ON))
    }

    @Test fun screenOffPressIsPassedWithItsRepeatsAndUp() {
        env.interactive = false
        val h = handler()
        assertEquals(PassReason.SCREEN_OFF, h.passReason())
        assertFalse(h.down(UP))
        assertFalse(h.down(UP, repeat = 1))
        assertFalse(h.up(UP))
        assertFalse(h.down(DOWN, t = 2))
        assertFalse(h.up(DOWN, t = 2))
        assertEquals(0, fine.reads)
        assertTrue(fine.writes.isEmpty())
        assertFalse(h.repeatPending)
    }

    @Test fun dozeAndAlwaysOnDisplayArePassed() {
        for (state in listOf(STATE_OFF, STATE_DOZE, STATE_DOZE_SUSPEND)) {
            env.display = state
            val h = handler()
            assertFalse(h.down(UP))
            assertFalse(h.up(UP))
        }
        assertTrue(fine.writes.isEmpty())
    }

    @Test fun screenGoesOffMidHoldCancelsTheRepeatAtOnce() {
        val h = handler()
        assertTrue(h.down(UP))
        assertTrue(h.repeatPending)
        env.interactive = false
        h.onScreenOff()
        assertFalse(h.repeatPending)
        time.advance(2_000)
        assertEquals(71, fine.value)
        // The consumed press still ends consumed.
        assertTrue(h.up(UP))
    }

    @Test fun screenGoesOffMidHoldWithoutBroadcastStopsAtNextTick() {
        val h = handler()
        h.down(UP)
        time.advance(500)
        assertEquals(72, fine.value)
        env.display = STATE_DOZE
        repeat(10) { time.advance(50) }
        assertEquals(72, fine.value)
        assertFalse(h.repeatPending)
    }

    @Test fun screenOffHardwareRepeatsOfAFinePressDoNotFineStep() {
        val h = handler()
        assertTrue(h.down(UP))
        env.interactive = false
        assertTrue(h.down(UP, repeat = 1))
        assertTrue(h.down(UP, repeat = 2))
        assertEquals(71, fine.value)
        assertTrue(h.up(UP))
    }

    @Test fun lostUpCannotBlockLaterPresses() {
        val h = handler()
        assertTrue(h.down(UP, t = 1))
        env.interactive = false
        h.onScreenOff()
        // The UP for t=1 never arrives. A new press whose first DOWN did not reach the
        // service shows up only as repeats: they and their UP must go to Android.
        assertFalse(h.down(UP, repeat = 1, t = 2))
        assertFalse(h.down(UP, repeat = 2, t = 2))
        assertFalse(h.up(UP, t = 2))
        // A full screen-off press goes to Android.
        assertFalse(h.down(UP, t = 3))
        assertFalse(h.up(UP, t = 3))
        // Screen back on: the next press is a fine step again.
        env.interactive = true
        assertTrue(h.down(UP, t = 4))
        assertTrue(h.up(UP, t = 4))
        assertEquals(72, fine.value)
    }

    @Test fun pressAfterScreenTurnsBackOnUsesFineStepsAgain() {
        env.interactive = false
        val h = handler()
        assertFalse(h.down(UP, t = 1))
        assertFalse(h.up(UP, t = 1))
        env.interactive = true
        assertTrue(h.down(UP, t = 2))
        assertEquals(71, fine.value)
        assertTrue(h.repeatPending)
        assertTrue(h.up(UP, t = 2))
    }

    @Test fun passedScreenOffPressStaysPassedIfScreenTurnsOnMidHold() {
        env.interactive = false
        val h = handler()
        assertFalse(h.down(DOWN))
        env.interactive = true
        assertFalse(h.down(DOWN, repeat = 1))
        assertFalse(h.up(DOWN))
        assertTrue(fine.writes.isEmpty())
    }

    @Test fun upIsConsumedOnlyForItsOwnConsumedDown() {
        val h = handler()
        // Passed DOWN -> passed UP.
        env.enabled = false
        assertFalse(h.down(UP, t = 1))
        env.enabled = true
        assertFalse(h.up(UP, t = 1))
        // Consumed DOWN -> consumed UP.
        assertTrue(h.down(UP, t = 2))
        assertTrue(h.up(UP, t = 2))
        // An UP from a press this handler never saw is not swallowed.
        assertTrue(h.down(DOWN, t = 3))
        assertFalse(h.up(DOWN, t = 4))
        // ...and the stale consumed press is gone.
        assertFalse(h.up(DOWN, t = 3))
    }

    @Test fun eachKeyIsPairedSeparately() {
        val h = handler()
        assertTrue(h.down(UP, t = 1))
        env.enabled = false
        assertFalse(h.down(DOWN, t = 2))
        assertFalse(h.up(DOWN, t = 2))
        assertTrue(h.up(UP, t = 1))
    }

    private companion object {
        // android.view.KeyEvent and android.media.AudioManager values.
        const val UP = VolumeKeyHandler.KEY_UP
        const val DOWN = VolumeKeyHandler.KEY_DOWN
        const val KEYCODE_POWER = 26
        const val KEYCODE_MUTE = 91
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
        const val MODE_NORMAL = 0
        const val MODE_RINGTONE = 1
        const val MODE_IN_CALL = 2
        const val MODE_IN_COMMUNICATION = 3
        const val MODE_CALL_SCREENING = 4
        // android.view.Display states.
        const val STATE_UNKNOWN = 0
        const val STATE_OFF = 1
        const val STATE_ON = 2
        const val STATE_DOZE = 3
        const val STATE_DOZE_SUSPEND = 4
        const val STATE_VR = 5
        const val STATE_ON_SUSPEND = 6
    }
}
