package com.oiuy.volumecontrol.effect

import com.oiuy.volumecontrol.ui.ExperimentText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentTextTest {
    @Test
    fun inactiveState() {
        val s = EffectState()
        assertEquals("Backend: none (effect off)", ExperimentText.backendLine(s))
        assertEquals("Effect API reports: nothing (not attached)", ExperimentText.confirmedLine(s))
        assertEquals("Control: none", ExperimentText.controlLine(s))
    }

    @Test
    fun dynamicsConfirmed() {
        val s = EffectState(
            active = true, backend = BackendKind.DYNAMICS_PROCESSING, requestedDb = -6f,
            confirmedDb = listOf(-6f, -6f), enabledReported = true, hasControl = true,
        )
        assertEquals("Backend: DynamicsProcessing input gain (broadband)", ExperimentText.backendLine(s))
        assertEquals("Requested: \u22126.0 dB (limit \u221230.0 dB)", ExperimentText.requestedLine(s))
        assertEquals("Effect API reports: \u22126.0 dB on 2 channel(s)", ExperimentText.confirmedLine(s))
        assertEquals("Control: this app", ExperimentText.controlLine(s))
    }

    @Test
    fun equalizerLabelAndMismatch() {
        val s = EffectState(
            active = true, backend = BackendKind.EQUALIZER, floorDb = -15f, requestedDb = -15f,
            confirmedDb = listOf(-15f, -12f, -15f), enabledReported = true, hasControl = false,
        )
        assertTrue(ExperimentText.backendLine(s).contains("not guaranteed flat"))
        assertEquals(
            "Effect API reports: \u221215.0 dB to \u221212.0 dB on 3 band(s) (differs from request)",
            ExperimentText.confirmedLine(s),
        )
        assertEquals("Control: lost to another app", ExperimentText.controlLine(s))
    }
}
