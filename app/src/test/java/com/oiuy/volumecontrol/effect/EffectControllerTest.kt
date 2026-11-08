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
    /** Throw this from apply() (simulates a platform rejection at a named stage). */
    var applyError: RuntimeException? = null
    var listener: ((Boolean) -> Unit)? = null

    override fun apply(db: Float): List<Float> {
        applyError?.let { throw it }
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
        assertTrue(s.attempts.first().contains("API 28"))
        assertEquals("Active: Equalizer (fake)", s.attempts.last())
    }

    @Test
    fun fallsBackToEqualizerWhenDynamicsThrows() {
        val c = controller(34, { throw UnsupportedOperationException("Effect library not loaded") },
            { FakeBackend(BackendKind.EQUALIZER, minDb = -15f) })
        val s = c.enable()
        assertEquals(BackendKind.EQUALIZER, s.backend)
        assertTrue(s.attempts.first().contains("UnsupportedOperationException"))
        assertTrue(s.attempts.first().contains("[create: session 0]"))
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

    private fun badValue() = IllegalArgumentException("AudioEffect: bad parameter value")

    @Test
    fun dynamicsConstructorBadValueFallsBackAndIsReportedWithStage() {
        val c = controller(34, {
            throw BackendStageException(
                BackendKind.DYNAMICS_PROCESSING, "constructor (create engine + set architecture)",
                "session 0, priority 1000", badValue(),
            )
        }, { FakeBackend(BackendKind.EQUALIZER, minDb = -15f) })
        val s = c.enable()
        assertEquals(BackendKind.EQUALIZER, s.backend)
        assertEquals(
            "Failed: DynamicsProcessing [constructor (create engine + set architecture): session 0, priority 1000] " +
                "IllegalArgumentException: AudioEffect: bad parameter value",
            s.attempts.first(),
        )
    }

    @Test
    fun dynamicsInputGainBadValueDuringSetupFallsBackAndReleases() {
        val dp = FakeBackend(BackendKind.DYNAMICS_PROCESSING)
        dp.applyError = BackendStageException(
            BackendKind.DYNAMICS_PROCESSING, "setInputGainAllChannelsTo", "0.0 dB on 2 channel(s)", badValue(),
        )
        val c = controller(34, { dp }, { FakeBackend(BackendKind.EQUALIZER) })
        val s = c.enable()
        assertTrue(dp.released)
        assertEquals(BackendKind.EQUALIZER, s.backend)
        assertTrue(s.attempts.first().contains("[setInputGainAllChannelsTo: 0.0 dB on 2 channel(s)]"))
        assertTrue(s.attempts.first().contains("bad parameter value"))
    }

    @Test
    fun plainExceptionFromSetupGetsGenericStage() {
        val dp = FakeBackend(BackendKind.DYNAMICS_PROCESSING)
        dp.applyError = badValue()
        val c = controller(34, { dp }, { FakeBackend(BackendKind.EQUALIZER) })
        val s = c.enable()
        assertEquals(
            "Failed: DynamicsProcessing [setup] IllegalArgumentException: AudioEffect: bad parameter value",
            s.attempts.first(),
        )
    }

    @Test
    fun dynamicsOnlyPreferenceDoesNotTryEqualizer() {
        var eqTried = false
        val c = controller(34, { throw badValue() }, { eqTried = true; FakeBackend(BackendKind.EQUALIZER) })
        c.setPreference(BackendPreference.DYNAMICS_ONLY)
        val s = c.enable()
        assertFalse(eqTried)
        assertFalse(s.active)
        assertEquals(1, s.attempts.size)
        assertTrue(s.message!!.contains("DynamicsProcessing only"))
        assertTrue(s.message!!.contains("device restriction"))
    }

    @Test
    fun equalizerOnlyPreferenceSkipsDynamics() {
        var dpTried = false
        val c = controller(34, { dpTried = true; FakeBackend(BackendKind.DYNAMICS_PROCESSING) },
            { FakeBackend(BackendKind.EQUALIZER, minDb = -12f) })
        c.setPreference(BackendPreference.EQUALIZER_ONLY)
        val s = c.enable()
        assertFalse(dpTried)
        assertEquals(BackendKind.EQUALIZER, s.backend)
        assertEquals(BackendPreference.EQUALIZER_ONLY, s.preference)
    }

    @Test
    fun preferenceCannotChangeWhileActiveAndSurvivesDisable() {
        val c = controller(34, { FakeBackend(BackendKind.DYNAMICS_PROCESSING) }, { FakeBackend(BackendKind.EQUALIZER) })
        c.setPreference(BackendPreference.DYNAMICS_ONLY)
        c.enable()
        assertEquals(BackendPreference.DYNAMICS_ONLY, c.setPreference(BackendPreference.EQUALIZER_ONLY).preference)
        assertEquals(BackendPreference.DYNAMICS_ONLY, c.disable().preference)
    }

    @Test
    fun laterSettingFailureShowsStageAndKeepsEffectAttached() {
        val dp = FakeBackend(BackendKind.DYNAMICS_PROCESSING)
        val c = controller(34, { dp }, { error("unused") })
        c.enable()
        dp.applyError = BackendStageException(
            BackendKind.DYNAMICS_PROCESSING, "setInputGainAllChannelsTo", "\u22126.0 dB on 2 channel(s)", badValue(),
        )
        val s = c.setRequestedDb(-6f)
        assertTrue(s.active)
        assertNull(s.confirmedDb)
        assertEquals(
            "Effect API rejected the setting: DynamicsProcessing [setInputGainAllChannelsTo: \u22126.0 dB on 2 channel(s)] " +
                "IllegalArgumentException: AudioEffect: bad parameter value",
            s.message,
        )
    }

    @Test
    fun platformEffectListIsCapturedAndItsFailureReported() {
        val c = EffectController(34, { FakeBackend(BackendKind.DYNAMICS_PROCESSING) }, { error("unused") },
            platformEffects = { listOf("Platform lists DynamicsProcessing: \"x\" by y (mode Insert)") })
        assertEquals(1, c.enable().platformEffects.size)
        val c2 = EffectController(34, { throw badValue() }, { throw badValue() },
            platformEffects = { throw IllegalStateException("boom") })
        assertTrue(c2.enable().platformEffects.single().contains("IllegalStateException: boom"))
    }

    @Test
    fun stageHelperWrapsOnceWithContext() {
        val e = runCatching {
            stage(BackendKind.EQUALIZER, "setBandLevel", "band 2 = -600 mB (range -1500..1500 mB)") {
                throw badValue()
            }
        }.exceptionOrNull() as BackendStageException
        assertEquals(
            "Equalizer [setBandLevel: band 2 = -600 mB (range -1500..1500 mB)] IllegalArgumentException: AudioEffect: bad parameter value",
            e.message,
        )
        val again = runCatching { stage(BackendKind.EQUALIZER, "outer") { throw e } }.exceptionOrNull()
        assertTrue(again === e)
    }

    @Test
    fun dynamicsPlanUsesFrequencyVariantAndNoStages() {
        assertEquals(DynamicsConfigPlan.Variant.FAVOR_FREQUENCY_RESOLUTION, DynamicsConfigPlan.variant)
        assertFalse(DynamicsConfigPlan.PRE_EQ_IN_USE || DynamicsConfigPlan.MBC_IN_USE ||
            DynamicsConfigPlan.POST_EQ_IN_USE || DynamicsConfigPlan.LIMITER_IN_USE)
        assertEquals(2, DynamicsConfigPlan.CHANNELS)
    }
}
