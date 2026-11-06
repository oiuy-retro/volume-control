package com.oiuy.volumecontrol.effect

import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Process-wide holder so the effect stays attached while the user switches to
 * another player app. Nothing is saved to disk: a new process always starts
 * neutral (effect off, 0 dB). If Android kills this process the effect is
 * released by the system and the app shows it as off on next launch.
 */
object GlobalEffectSession {
    var state: EffectState by mutableStateOf(EffectState())
        private set

    private val controller = EffectController(
        sdkInt = Build.VERSION.SDK_INT,
        dynamicsFactory = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                DynamicsProcessingGainBackend.create()
            } else {
                throw UnsupportedOperationException("API < 28")
            }
        },
        equalizerFactory = { EqualizerGainBackend.create() },
        platformEffects = { describePlatformEffects() },
        onStateChanged = { state = it },
    )

    fun enable() = controller.enable()
    fun setPreference(p: BackendPreference) = controller.setPreference(p)
    fun disable() = controller.disable()
    fun reset() = controller.reset()
    fun refresh() = controller.refresh()
    fun setRequestedDb(db: Float) = controller.setRequestedDb(db)
}
