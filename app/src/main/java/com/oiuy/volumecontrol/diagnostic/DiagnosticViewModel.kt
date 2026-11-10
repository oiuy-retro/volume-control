package com.oiuy.volumecontrol.diagnostic

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

/**
 * Holds the diagnostic across configuration changes. Creating it, recreating
 * the activity, lifecycle callbacks and broadcasts only call [refresh], which
 * reads. [onTestTapped] is wired to the test button and nothing else.
 */
class DiagnosticViewModel(application: Application) : AndroidViewModel(application) {
    private val diagnostic = FineVolumeDiagnostic(
        device = currentDeviceInfo(),
        publicPort = AndroidPublicVolumePort(application),
        fineFactory = { ReflectiveSamsungFineVolumePort(application) },
    )

    var state: DiagnosticSnapshot by mutableStateOf(diagnostic.snapshot())
        private set

    var testRunning: Boolean by mutableStateOf(false)
        private set

    /** Read-only. Safe to call from lifecycle events and volume/route callbacks. */
    fun refresh() {
        state = diagnostic.snapshot()
    }

    /** The only caller of the fine-volume setter. Runs synchronously, once. */
    fun onTestTapped() {
        if (testRunning) return
        testRunning = true
        try {
            diagnostic.testOneFineStepQuieter()
        } finally {
            testRunning = false
            refresh()
        }
    }
}
