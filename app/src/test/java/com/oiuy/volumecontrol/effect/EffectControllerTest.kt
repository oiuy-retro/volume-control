package com.oiuy.volumecontrol.effect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeBackend(
    override val kind: BackendKind,
    override val minDb: Float? = null,
    val units: Int = 2,
    var reportEnabled: Boolean = true,
    var control: Boolean = true,
    /** Engine stores at most this deep (simulates its own clamp). */
    var engineFloor: Float = -1000f,
) : GainBackend {
    override val detail = "fake"
    val applied = mutableListOf<Float>()
    var enabled = false
    var released = false
    var failApply = false
    var listener: ((Boolean) -> Unit)? = null

    override fun apply(db: Float): List<Float> {
        if (failApply) throw IllegalStateException("rejected")
        applied += db
        return List(units) { maxOf(db, engineFloor) }
    }

    override fun setEnabled(enabled: Boolean): Boolean {
        this.enabled = enabled && reportEnabled
        return this.enabled
    }

    override fun isEnabled() = enabled
    override fun hasControl() = control
    override fun setControlListener(listener: ((Boolean) -> Unit)?) {
        this.listener = listener
    }
    override fun release() {
        released = true
    }
}

class EffectControllerTest {
    private fun controller(sdk: Int, dp: BackendFactory, eq: BackendFactory) =
        EffectController(sdk, dp, eq)

    @Test
    fun startsNeutralAndInactive() {
        val c = controller(34, { error("no") }, { error("no") })
        assertFalse(c.state.active)
        assertEquals(0f, c.state.requestedDb, 0f)
        assertNull(c.state.backend)
    }

    @Test
    fun prefersDynamicsProcessingOnApi28Plus() {
        val dp = FakeBackend(BackendKind.DYNAMICS_PROCESSING)
        var eqCreated = false
        val c = controller(34, { dp }, { eqCreated = true; FakeBackend(BackendKind.EQUALIZER) })
        val s = c.enable()
        assertTrue(s.active)
        assertEquals(BackendKind.DYNAMICS_PROCESSING, s.backend)
        assertFalse(eqCreated)
        assertEquals(listOf(0f), dp.applied) // neutral first
        assertEquals(-30f, s.floorDb, 0f)
    }

    @Test
    fun skipsDynamicsBelowApi28() {
        var dpCreated = false
        val c = controller(27, { dpCreated = true; FakeBackend(BackendKind.DYNAMICS_PROCESSING) },
            { FakeBackend(BackendKind.EQUALIZER, minDb = -15f) })
        val s = c.enable()
        assertFalse(dpCreated)
        assertEquals(BackendKind.EQUALIZER, s.backend)
        assertEquals(-15f, s.floorDb, 0f)
        assertTrue(s.attempts.single().contains("API 28"))
    }

    @Test
    fun fallsBackToEqualizerWhenDynamicsThrows() {
        val c = controller(34, { throw UnsupportedOperationException("Effect library not loaded") },
            { FakeBackend(BackendKind.EQUALIZER, minDb = -15f) })
        val s = c.enable()
        assertEquals(BackendKind.EQUALIZER, s.backend)
        assertTrue(s.attempts.single().contains("UnsupportedOperationException"))
    }

    @Test
    fun fallsBackWhenDynamicsWillNotEnableAndReleasesIt() {
        val dp = FakeBackend(BackendKind.DYNAMICS_PROCESSING, reportEnabled = false)
        val c = controller(34, { dp }, { FakeBackend(BackendKind.EQUALIZER) })
        val s = c.enable()
        assertTrue(dp.released)
        assertEquals(BackendKind.EQUALIZER, s.backend)
    }

    @Test
    fun unsupportedWhenBothFailAndStaysInactive() {
        val c = controller(34, { throw IllegalArgumentException("bad session") },
            { throw RuntimeException("Cannot initialize effect engine") })
        val s = c.enable()
        assertFalse(s.active)
        assertNull(s.backend)
        assertEquals(2, s.attempts.size)
        assertTrue(s.message!!.contains("media volume is untouched"))
        // Requests while unsupported do nothing.
        assertFalse(c.setRequestedDb(-6f).active)
        assertEquals(0f, c.state.requestedDb, 0f)
    }

    @Test
    fun requestsAreClampedConfirmedAndNeverBoost() {
        val dp = FakeBackend(BackendKind.DYNAMICS_PROCESSING)
        val c = controller(34, { dp }, { error("unused") })
        c.enable()
        var s = c.setRequestedDb(-6.04f)
        assertEquals(-6f, s.requestedDb, 1e-4f)
        assertTrue(s.confirmedMatchesRequest)
        s = c.setRequestedDb(5f)
        assertEquals(0f, s.requestedDb, 0f)
        s = c.setRequestedDb(-99f)
        assertEquals(-30f, s.requestedDb, 1e-4f)
        assertTrue(dp.applied.all { it <= 0f })
    }

    @Test
    fun engineClampShowsAsMismatch() {
        val dp = FakeBackend(BackendKind.DYNAMICS_PROCESSING, engineFloor = -10f)
        val c = controller(34, { dp }, { error("unused") })
        c.enable()
        val s = c.setRequestedDb(-20f)
        assertEquals(-20f, s.requestedDb, 1e-4f)
        assertFalse(s.confirmedMatchesRequest)
        assertEquals(listOf(-10f, -10f), s.confirmedDb)
    }

    @Test
    fun applyFailureIsReportedNotHidden() {
        val dp = FakeBackend(BackendKind.DYNAMICS_PROCESSING)
        val c = controller(34, { dp }, { error("unused") })
        c.enable()
        dp.failApply = true
        val s = c.setRequestedDb(-3f)
        assertTrue(s.active)
        assertNull(s.confirmedDb)
        assertTrue(s.message!!.contains("rejected"))
    }

    @Test
    fun controlLossIsShown() {
        val dp = FakeBackend(BackendKind.DYNAMICS_PROCESSING)
        val c = controller(34, { dp }, { error("unused") })
        c.enable()
        dp.control = false
        dp.listener!!(false)
        assertFalse(c.state.hasControl)
        assertEquals(EffectController.CONTROL_LOST, c.state.message)
        dp.control = true
        dp.listener!!(true)
        assertTrue(c.state.hasControl)
        assertNull(c.state.message)
    }

    @Test
    fun resetAndDisableReturnToNeutralAndRelease() {
        val dp = FakeBackend(BackendKind.DYNAMICS_PROCESSING)
        val c = controller(34, { dp }, { error("unused") })
        c.enable()
        c.setRequestedDb(-9f)
        assertEquals(0f, c.reset().requestedDb, 0f)
        c.setRequestedDb(-9f)
        val s = c.disable()
        assertFalse(s.active)
        assertEquals(0f, s.requestedDb, 0f)
        assertTrue(dp.released)
        assertFalse(dp.enabled)
        assertEquals(0f, dp.applied.last(), 0f)
        assertNull(dp.listener)
    }

    @Test
    fun reEnableStartsNeutral() {
        var made = 0
        val c = controller(34, { made++; FakeBackend(BackendKind.DYNAMICS_PROCESSING) }, { error("unused") })
        c.enable()
        c.setRequestedDb(-12f)
        c.disable()
        val s = c.enable()
        assertEquals(2, made)
        assertEquals(0f, s.requestedDb, 0f)
    }

    @Test
    fun enableWhileActiveDoesNotCreateSecondEffect() {
        var made = 0
        val c = controller(34, { made++; FakeBackend(BackendKind.DYNAMICS_PROCESSING) }, { error("unused") })
        c.enable()
        c.setRequestedDb(-4f)
        val s = c.enable()
        assertEquals(1, made)
        assertEquals(-4f, s.requestedDb, 1e-4f)
    }
}
