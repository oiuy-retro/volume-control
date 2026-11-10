package com.oiuy.volumecontrol.volume

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaVolumeControllerTest {
    private val time = FakeTime()

    private fun controller(stream: FakeStream, fine: FakeFine?, samsung: Boolean = true) =
        MediaVolumeController(VolumeEngine(samsung, stream) { fine ?: FakeFine(available = false) }, time) { time.now }

    @Test
    fun syncOnlyReads() {
        val stream = FakeStream()
        val fine = FakeFine(value = 73)
        val c = controller(stream, fine)
        repeat(5) { c.syncFromSystem() }
        assertEquals(73, c.currentUnits)
        assertEquals(150, c.maxUnits)
        assertTrue(fine.writes.isEmpty())
        assertTrue(stream.writes.isEmpty())
        assertEquals("Fine volume: Samsung (73 / 150)", c.statusText())
    }

    @Test
    fun fullTurnIsTenPercentOfFineMax() {
        val fine = FakeFine(value = 60)
        val c = controller(FakeStream(), fine)
        c.adjustByDegrees(360f)
        c.flush()
        assertEquals(75, c.currentUnits)
        assertEquals(75, fine.writes.last())
    }

    @Test
    fun writesOnlyWhenIntegerTargetChanges() {
        val fine = FakeFine(value = 60)
        val c = controller(FakeStream(), fine)
        // 24 degrees = exactly one fine unit at 150 units; 6 degrees = a quarter unit.
        c.adjustByDegrees(6f)
        time.advance(100)
        assertTrue(fine.writes.isEmpty())
        c.adjustByDegrees(6f) // 0.5 unit -> rounds (half-even) to 60
        time.advance(100)
        c.adjustByDegrees(6f) // 0.75 -> 61
        time.advance(100)
        assertEquals(listOf(61), fine.writes)
    }

    @Test
    fun rapidDragCoalescesWrites() {
        val fine = FakeFine(value = 60)
        val c = controller(FakeStream(), fine)
        repeat(10) {
            c.adjustByDegrees(24f)
            time.advance(4)
        }
        // Ten targets over 40 ms: first immediately, then at most one per 30 ms, latest wins.
        assertEquals(listOf(61, 68), fine.writes)
        time.advance(100)
        assertEquals(listOf(61, 68, 70), fine.writes)
    }

    @Test
    fun clampsAtZeroAndMax() {
        val fine = FakeFine(value = 2)
        val c = controller(FakeStream(), fine)
        c.adjustByDegrees(-720f)
        c.flush()
        assertEquals(0, c.currentUnits)
        assertEquals(0f, c.fractionalPercent())
        c.syncFromSystem()
        fine.value = 148
        c.syncFromSystem()
        c.adjustByDegrees(720f)
        c.flush()
        assertEquals(150, c.currentUnits)
        assertEquals(listOf(0, 150), fine.writes)
    }

    @Test
    fun widgetStepIsTenPercentOfFineMax() {
        val stream = FakeStream()
        val fine = FakeFine(value = 70)
        val c = controller(stream, fine)
        assertEquals(85, c.adjustByPercentStep(0.10f))
        assertEquals(70, c.adjustByPercentStep(-0.10f))
        fine.value = 140
        assertEquals(150, c.adjustByPercentStep(0.10f))
        fine.value = 5
        assertEquals(0, c.adjustByPercentStep(-0.10f))
        assertEquals(listOf(85, 70, 150, 0), fine.writes)
        assertTrue(stream.writes.isEmpty())
        assertEquals(0, c.percent)
    }

    @Test
    fun widgetPercentFromFineValue() {
        val c = controller(FakeStream(), FakeFine(value = 75))
        assertEquals(50, c.percent)
    }

    @Test
    fun publicFallbackUsesIntegerSteps() {
        val stream = FakeStream(current = 7)
        val c = controller(stream, null, samsung = false)
        assertEquals(VolumeMode.ANDROID_STEPS, c.mode)
        assertEquals(15, c.maxUnits)
        c.adjustByDegrees(360f) // +1.5 -> 8.5 rounds half-even to 8
        c.flush()
        assertEquals(listOf(8), stream.writes)
        c.showSystemVolumeUi()
        assertEquals(1, stream.showUiCalls)
    }

    @Test
    fun fineWriteFailureRebasesOnPublicWithoutWritingOldTarget() {
        val stream = FakeStream(current = 7)
        val fine = FakeFine(value = 70, writeFailure = "Method threw SecurityException")
        val c = controller(stream, fine)
        var modeChanges = 0
        c.onModeChanged = { modeChanges++ }
        c.adjustByDegrees(24f)
        assertEquals(listOf(71), fine.writes)
        assertEquals(1, modeChanges)
        assertEquals(VolumeMode.ANDROID_STEPS, c.mode)
        assertEquals(15, c.maxUnits)
        assertEquals(7, c.currentUnits)
        assertTrue(stream.writes.isEmpty())
        time.advance(100)
        c.flush()
        assertTrue(stream.writes.isEmpty())
    }

    @Test
    fun syncDropsPendingDragWrite() {
        val fine = FakeFine(value = 60)
        val c = controller(FakeStream(), fine)
        c.adjustByDegrees(24f)
        time.advance(5)
        c.adjustByDegrees(48f)
        fine.value = 100
        c.syncFromSystem()
        time.advance(100)
        c.flush()
        assertEquals(listOf(61), fine.writes)
        assertEquals(100, c.currentUnits)
    }

    @Test
    fun noShowUiCallInFineMode() {
        val stream = FakeStream()
        val c = controller(stream, FakeFine())
        c.showSystemVolumeUi()
        assertEquals(0, stream.showUiCalls)
    }
}
