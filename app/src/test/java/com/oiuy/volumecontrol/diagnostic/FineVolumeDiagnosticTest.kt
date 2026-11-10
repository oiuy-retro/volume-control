package com.oiuy.volumecontrol.diagnostic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FineVolumeDiagnosticTest {

    private class FakePublic(
        var index: Int = 7,
        var max: Int = 15,
        var muted: Boolean = false,
        var route: String = "Bluetooth A2DP",
        var fail: String? = null,
    ) : PublicVolumePort {
        var reads = 0
        override fun read(): PortResult<PublicVolume> {
            reads++
            fail?.let { return PortResult.Failed(it) }
            return PortResult.Ok(PublicVolume(index, 0, max, muted, route))
        }
    }

    private class FakeFine(
        var value: Int = 73,
        var available: Boolean = true,
        var readFailure: String? = null,
        var setFailure: String? = null,
        /** Whether a successful set changes the value read back. */
        var applies: Boolean = true,
    ) : FineVolumePort {
        val setCalls = mutableListOf<Int>()
        var reads = 0
        var onSet: (() -> Unit)? = null

        override fun availability(): PortResult<Unit> =
            if (available) PortResult.Ok(Unit) else PortResult.Failed("Method not found: semSetFineVolume")

        override fun readFine(): PortResult<Int> {
            reads++
            readFailure?.let { return PortResult.Failed(it) }
            return PortResult.Ok(value)
        }

        override fun stepTo(index: Int): PortResult<Unit> {
            setCalls += index
            onSet?.invoke()
            setFailure?.let { return PortResult.Failed(it) }
            if (applies) value = index
            return PortResult.Ok(Unit)
        }
    }

    private fun samsung() = DeviceInfo("samsung", "SM-S926B", "e2s", "17", 37, "build")
    private fun pixel() = DeviceInfo("Google", "Pixel 9", "tokay", "16", 36, "build")

    private fun diag(
        device: DeviceInfo = samsung(),
        pub: FakePublic = FakePublic(),
        fine: FakeFine = FakeFine(),
    ) = FineVolumeDiagnostic(device, pub, { fine })

    @Test
    fun passiveSnapshotsNeverWrite() {
        val fine = FakeFine()
        val d = diag(fine = fine)
        repeat(50) { d.snapshot() }
        assertTrue(fine.setCalls.isEmpty())
        assertEquals(50, fine.reads)
    }

    @Test
    fun snapshotReportsValuesAndEnablesTest() {
        val s = diag().snapshot()
        assertEquals(PortResult.Ok(73), s.fineValue)
        assertEquals(PortResult.Ok(Unit), s.fineAvailability)
        assertTrue(s.canTest)
        assertNull(s.lastTest)
    }

    @Test
    fun nonSamsungNeverLooksUpOrWrites() {
        var factoryCalls = 0
        val d = FineVolumeDiagnostic(pixel(), FakePublic()) { factoryCalls++; FakeFine() }
        val s = d.snapshot()
        assertFalse(s.canTest)
        assertNull(s.fineValue)
        assertTrue(s.fineAvailability is PortResult.Failed)
        assertTrue(d.testOneFineStepQuieter() is TestOutcome.NotAttempted)
        assertEquals(0, factoryCalls)
    }

    @Test
    fun manufacturerGateIgnoresCaseAndSpaces() {
        assertTrue(DeviceInfo(" SAMSUNG ", "", "", "", 0, "").isSamsung)
        assertFalse(DeviceInfo("samsung-ish", "", "", "", 0, "").isSamsung)
    }

    @Test
    fun unavailableMethodsMeanNoReadAndNoWrite() {
        val fine = FakeFine(available = false)
        val d = diag(fine = fine)
        val s = d.snapshot()
        assertNull(s.fineValue)
        assertFalse(s.canTest)
        assertTrue(d.testOneFineStepQuieter() is TestOutcome.NotAttempted)
        assertTrue(fine.setCalls.isEmpty())
        assertEquals(0, fine.reads)
    }

    @Test
    fun tapWritesExactlyOnceUsingFreshCurrentMinusOne() {
        val fine = FakeFine(value = 73)
        val d = diag(fine = fine)
        d.snapshot()
        fine.value = 41 // changed by the native bar after the last displayed reading
        val outcome = d.testOneFineStepQuieter() as TestOutcome.Attempted
        assertEquals(listOf(40), fine.setCalls)
        assertEquals(41, outcome.fineBefore)
        assertEquals(40, outcome.requested)
        assertEquals(PortResult.Ok(40), outcome.fineAfter)
        assertTrue(outcome.readBackMatches)
    }

    @Test
    fun zeroValueMakesNoCallAndDisablesTest() {
        val fine = FakeFine(value = 0)
        val d = diag(fine = fine)
        assertFalse(d.snapshot().canTest)
        assertTrue(d.testOneFineStepQuieter() is TestOutcome.NotAttempted)
        assertTrue(fine.setCalls.isEmpty())
    }

    @Test
    fun negativeValueMakesNoCall() {
        val fine = FakeFine(value = -1)
        val d = diag(fine = fine)
        assertTrue(d.testOneFineStepQuieter() is TestOutcome.NotAttempted)
        assertTrue(fine.setCalls.isEmpty())
    }

    @Test
    fun mutedMakesNoCall() {
        val fine = FakeFine()
        val d = diag(pub = FakePublic(muted = true), fine = fine)
        assertFalse(d.snapshot().canTest)
        assertTrue(d.testOneFineStepQuieter() is TestOutcome.NotAttempted)
        assertTrue(fine.setCalls.isEmpty())
    }

    @Test
    fun failedFineReadMakesNoCall() {
        val fine = FakeFine(readFailure = "SecurityException: denied")
        val d = diag(fine = fine)
        assertFalse(d.snapshot().canTest)
        val outcome = d.testOneFineStepQuieter() as TestOutcome.NotAttempted
        assertTrue(outcome.reason.contains("SecurityException"))
        assertTrue(fine.setCalls.isEmpty())
    }

    @Test
    fun failedPublicReadMakesNoCall() {
        val fine = FakeFine()
        val d = diag(pub = FakePublic(fail = "RuntimeException: x"), fine = fine)
        assertFalse(d.snapshot().canTest)
        assertTrue(d.testOneFineStepQuieter() is TestOutcome.NotAttempted)
        assertTrue(fine.setCalls.isEmpty())
    }

    @Test
    fun failedSetIsReportedWithoutRetryRestoreOrFallback() {
        val fine = FakeFine(value = 50, setFailure = "Method threw SecurityException: no")
        val pub = FakePublic()
        val d = diag(pub = pub, fine = fine)
        val outcome = d.testOneFineStepQuieter() as TestOutcome.Attempted
        assertEquals(listOf(49), fine.setCalls)
        assertTrue(outcome.call is PortResult.Failed)
        assertEquals(PortResult.Ok(50), outcome.fineAfter)
        assertFalse(outcome.readBackMatches)
    }

    @Test
    fun ignoredSetIsReportedAsNoChangeWithoutRetry() {
        val fine = FakeFine(value = 50, applies = false)
        val d = diag(fine = fine)
        val outcome = d.testOneFineStepQuieter() as TestOutcome.Attempted
        assertEquals(listOf(49), fine.setCalls)
        assertEquals(PortResult.Ok(50), outcome.fineAfter)
        assertFalse(outcome.readBackMatches)
    }

    @Test
    fun successfulTestDoesNotRestoreOriginal() {
        val fine = FakeFine(value = 20)
        val d = diag(fine = fine)
        d.testOneFineStepQuieter()
        repeat(5) { d.snapshot() }
        assertEquals(listOf(19), fine.setCalls)
        assertEquals(19, fine.value)
    }

    @Test
    fun refreshesKeepLastTestResult() {
        val fine = FakeFine(value = 20)
        val pub = FakePublic(index = 2)
        val d = diag(pub = pub, fine = fine)
        val outcome = d.testOneFineStepQuieter()
        fine.value = 90 // native bar moved later
        pub.index = 9
        val s = d.snapshot()
        assertSame(outcome, s.lastTest)
        assertEquals(PortResult.Ok(90), s.fineValue)
    }

    @Test
    fun repeatedTapsCallOnceEachWithFreshValues() {
        val fine = FakeFine(value = 10)
        val d = diag(fine = fine)
        d.testOneFineStepQuieter()
        fine.value = 30
        d.testOneFineStepQuieter()
        assertEquals(listOf(9, 29), fine.setCalls)
    }

    @Test
    fun reachingZeroStopsFurtherCalls() {
        val fine = FakeFine(value = 1)
        val d = diag(fine = fine)
        d.testOneFineStepQuieter()
        assertFalse(d.snapshot().canTest)
        assertTrue(d.testOneFineStepQuieter() is TestOutcome.NotAttempted)
        assertEquals(listOf(0), fine.setCalls)
    }

    @Test
    fun reentrantTapDuringCallIsRejectedAndCallbackSnapshotsDoNotWrite() {
        val fine = FakeFine(value = 60)
        val d = diag(fine = fine)
        var nested: TestOutcome? = null
        var snapshotDuring: DiagnosticSnapshot? = null
        fine.onSet = {
            // A volume broadcast arriving mid-call refreshes, and a second tap is ignored.
            snapshotDuring = d.snapshot()
            nested = d.testOneFineStepQuieter()
        }
        val outcome = d.testOneFineStepQuieter()
        assertEquals(listOf(59), fine.setCalls)
        assertTrue(nested is TestOutcome.NotAttempted)
        assertFalse(snapshotDuring!!.canTest)
        assertSame(outcome, d.lastTest)
    }

    @Test
    fun publicBeforeAndAfterAreRecordedForRouteRaces() {
        val pub = FakePublic(route = "Bluetooth A2DP")
        val fine = FakeFine(value = 33)
        fine.onSet = { pub.route = "Built-in speaker" }
        val d = diag(pub = pub, fine = fine)
        val outcome = d.testOneFineStepQuieter() as TestOutcome.Attempted
        assertEquals("Bluetooth A2DP", (outcome.publicBefore as PortResult.Ok).value.route)
        assertEquals("Built-in speaker", (outcome.publicAfter as PortResult.Ok).value.route)
        assertEquals(listOf(32), fine.setCalls)
    }
}
