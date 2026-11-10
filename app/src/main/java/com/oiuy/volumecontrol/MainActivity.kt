package com.oiuy.volumecontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.oiuy.volumecontrol.diagnostic.DiagnosticScreen
import com.oiuy.volumecontrol.diagnostic.DiagnosticViewModel
import com.oiuy.volumecontrol.ui.VolumeControlTheme
import com.oiuy.volumecontrol.widget.VolumeDialWidgetReceiver

/**
 * Diagnostic build (0.1.5): this activity only shows the Samsung fine-volume
 * diagnostic. Lifecycle events, volume broadcasts and output-device changes
 * only refresh readings. The only volume write is the explicit test button.
 */
class MainActivity : ComponentActivity() {
    private val viewModel: DiagnosticViewModel by viewModels()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            viewModel.refresh()
            VolumeDialWidgetReceiver.requestUpdate(context)
        }
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            viewModel.refresh()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            viewModel.refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VolumeControlTheme {
                DiagnosticScreen(
                    state = viewModel.state,
                    testRunning = viewModel.testRunning,
                    onRefresh = viewModel::refresh,
                    onTest = viewModel::onTestTapped,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            volumeReceiver,
            IntentFilter(VOLUME_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        audioManager().registerAudioDeviceCallback(deviceCallback, mainHandler)
        viewModel.refresh()
    }

    override fun onStop() {
        audioManager().unregisterAudioDeviceCallback(deviceCallback)
        unregisterReceiver(volumeReceiver)
        super.onStop()
    }

    private fun audioManager(): AudioManager = getSystemService(AUDIO_SERVICE) as AudioManager

    private companion object {
        const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
    }
}
