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
    fun fullTurnIsFiftyFineUnits() {
        val fine = FakeFine(value = 60)
        val c = controller(FakeStream(), fine)
        assertEquals(50, c.unitsPerTurn)
        c.adjustByDegrees(360f)
        c.flush()
        assertEquals(110, c.currentUnits)
        assertEquals(110, fine.writes.last())
    }

    @Test
    fun turnsToCoverRangeFollowPublicMax() {
        val stream = FakeStream(maxSteps = 15)
        val fine = FakeFine(value = 0)
        val c = controller(stream, fine)
        assertEquals(3f, TickDial.turns(c.maxUnits, c.unitsPerTurn))
        repeat(3) {
            c.adjustByDegrees(360f)
            c.flush()
        }
        assertEquals(150, c.currentUnits)
        c.adjustByDegrees(360f)
        c.flush()
        assertEquals(150, c.currentUnits)
        stream.maxSteps = 20
        fine.value = 0
        c.syncFromSystem()
        assertEquals(4f, TickDial.turns(c.maxUnits, c.unitsPerTurn))
    }

    @Test
    fun everyTickIsOneWrittenUnit() {
        val fine = FakeFine(value = 0)
        val c = controller(FakeStream(), fine)
        repeat(50) {
            c.adjustByDegrees(7.2f)
            time.advance(100)
        }
        assertEquals((1..50).toList(), fine.writes)
    }

    @Test
    fun writesOnlyWhenIntegerTargetChanges() {
        val fine = FakeFine(value = 60)
        val c = controller(FakeStream(), fine)
        // 7.2 degrees = one fine unit at 50 per turn; 1.8 degrees = a quarter unit.
        c.adjustByDegrees(1.8f)
        time.advance(100)
        assertTrue(fine.writes.isEmpty())
        c.adjustByDegrees(1.8f) // 0.5 unit -> rounds (half-even) to 60
        time.advance(100)
        c.adjustByDegrees(1.8f) // 0.75 -> 61
        time.advance(100)
        assertEquals(listOf(61), fine.writes)
    }

    @Test
    fun rapidDragCoalescesWrites() {
        val fine = FakeFine(value = 60)
        val c = controller(FakeStream(), fine)
        repeat(10) {
            c.adjustByDegrees(7.2f)
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
        assertEquals(5, c.unitsPerTurn)
        c.adjustByDegrees(360f) // one turn = 5 public steps
        c.flush()
        assertEquals(listOf(12), stream.writes)
        c.adjustByDegrees(72f) // one tick = one public step
        c.flush()
        assertEquals(listOf(12, 13), stream.writes)
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
        c.adjustByDegrees(7.2f)
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
        c.adjustByDegrees(7.2f)
        time.advance(5)
        c.adjustByDegrees(14.4f)
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
