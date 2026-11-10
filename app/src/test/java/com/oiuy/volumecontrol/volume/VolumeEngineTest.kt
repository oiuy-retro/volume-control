package com.oiuy.volumecontrol.volume

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeEngineTest {
    @Test
    fun samsungWithMethodsUsesFineUnitsComputedFromPublicMax() {
        val stream = FakeStream(maxSteps = 15)
        val e = VolumeEngine(true, stream) { FakeFine() }
        assertEquals(VolumeMode.SAMSUNG_FINE, e.mode)
        assertEquals(150, e.maxUnits())
        stream.maxSteps = 20
        assertEquals(200, e.maxUnits())
        assertNull(e.fallbackReason)
    }

    @Test
    fun nonSamsungNeverLooksUpFineMethods() {
        var factoryCalls = 0
        val e = VolumeEngine(false, FakeStream()) { factoryCalls++; FakeFine() }
        assertEquals(VolumeMode.ANDROID_STEPS, e.mode)
        assertEquals(15, e.maxUnits())
        assertEquals(0, factoryCalls)
        assertEquals("not a Samsung device", e.fallbackReason)
    }

    @Test
    fun missingMethodsFallBackToPublicSteps() {
        val e = VolumeEngine(true, FakeStream()) { FakeFine(available = false) }
        assertEquals(VolumeMode.ANDROID_STEPS, e.mode)
        assertEquals("Method not found", e.fallbackReason)
    }

    @Test
    fun factoryExceptionFallsBack() {
        val e = VolumeEngine(true, FakeStream()) { throw SecurityException("nope") }
        assertEquals(VolumeMode.ANDROID_STEPS, e.mode)
        assertTrue(e.fallbackReason!!.contains("SecurityException"))
    }

    @Test
    fun readsNeverWrite() {
        val stream = FakeStream()
        val fine = FakeFine(value = 73)
        val e = VolumeEngine(true, stream) { fine }
        repeat(10) { assertEquals(73, e.readUnits()) }
        assertTrue(fine.writes.isEmpty())
        assertTrue(stream.writes.isEmpty())
    }

    @Test
    fun fineWritesNeverUsePublicSetter() {
        val stream = FakeStream()
        val fine = FakeFine()
        val e = VolumeEngine(true, stream) { fine }
        assertEquals(WriteResult.WROTE, e.writeUnits(71))
        assertEquals(listOf(71), fine.writes)
        assertTrue(stream.writes.isEmpty())
        e.showSystemUi()
        assertEquals(0, stream.showUiCalls)
    }

    @Test
    fun writesAndReadsAreClampedToRange() {
        val fine = FakeFine(value = 999)
        val e = VolumeEngine(true, FakeStream()) { fine }
        assertEquals(150, e.readUnits())
        e.writeUnits(-5)
        e.writeUnits(400)
        assertEquals(listOf(0, 150), fine.writes)
        val stream = FakeStream()
        val p = VolumeEngine(false, stream) { FakeFine() }
        p.writeUnits(99)
        assertEquals(listOf(15), stream.writes)
    }

    @Test
    fun fineReadFailureFallsBackAndReadsPublic() {
        val stream = FakeStream(current = 9)
        val e = VolumeEngine(true, stream) { FakeFine(readFailure = "SecurityException: x") }
        assertEquals(9, e.readUnits())
        assertEquals(VolumeMode.ANDROID_STEPS, e.mode)
        assertEquals(15, e.maxUnits())
        assertTrue(stream.writes.isEmpty())
    }

    @Test
    fun fineWriteFailureSwitchesModeWithoutPublicWrite() {
        val stream = FakeStream()
        val fine = FakeFine(writeFailure = "Method threw SecurityException")
        val e = VolumeEngine(true, stream) { fine }
        assertEquals(WriteResult.MODE_CHANGED, e.writeUnits(60))
        assertEquals(VolumeMode.ANDROID_STEPS, e.mode)
        assertTrue(stream.writes.isEmpty())
        assertTrue(e.fallbackReason!!.startsWith("write failed"))
        // Later writes go to the public path only.
        e.writeUnits(5)
        assertEquals(listOf(60), fine.writes)
        assertEquals(listOf(5), stream.writes)
    }

    @Test
    fun publicModeShowsPanel() {
        val stream = FakeStream()
        VolumeEngine(false, stream) { FakeFine() }.showSystemUi()
        assertEquals(1, stream.showUiCalls)
    }

    @Test
    fun statusText() {
        assertEquals("Fine volume: Samsung (73 / 150)", volumeStatusText(VolumeMode.SAMSUNG_FINE, 73, 150, null))
        assertEquals(
            "Volume: Android steps (7 / 15) · Samsung fine volume off: not a Samsung device",
            volumeStatusText(VolumeMode.ANDROID_STEPS, 7, 15, "not a Samsung device"),
        )
    }
}
