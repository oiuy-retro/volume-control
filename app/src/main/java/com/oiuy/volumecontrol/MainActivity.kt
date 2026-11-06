package com.oiuy.volumecontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.oiuy.volumecontrol.effect.GlobalEffectSession
import com.oiuy.volumecontrol.ui.ExperimentScreen
import com.oiuy.volumecontrol.ui.VolumeControlTheme
import com.oiuy.volumecontrol.widget.VolumeDialWidgetReceiver

/**
 * Experimental build: the dial drives a session-0 audio effect (attenuation
 * only). It does not read or write Android media volume.
 *
 * The effect lives in [GlobalEffectSession] (process scope), so leaving this
 * screen to start another player does not release it. Only "Disable and
 * release" or process death releases it.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VolumeControlTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val context = LocalContext.current
                    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                        GlobalEffectSession.refresh()
                    }
                    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                        ExperimentScreen(
                            state = GlobalEffectSession.state,
                            onEnable = {
                                GlobalEffectSession.enable()
                                VolumeDialWidgetReceiver.requestUpdate(context)
                            },
                            onPreference = { GlobalEffectSession.setPreference(it) },
                            onDisable = {
                                GlobalEffectSession.disable()
                                VolumeDialWidgetReceiver.requestUpdate(context)
                            },
                            onReset = {
                                GlobalEffectSession.reset()
                                VolumeDialWidgetReceiver.requestUpdate(context)
                            },
                            onRequestDb = { GlobalEffectSession.setRequestedDb(it) },
                            onDragEnd = { VolumeDialWidgetReceiver.requestUpdate(context) },
                        )
                    }
                }
            }
        }
    }
}
