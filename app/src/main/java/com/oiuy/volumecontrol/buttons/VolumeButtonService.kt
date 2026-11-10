package com.oiuy.volumecontrol.buttons

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import com.oiuy.volumecontrol.volume.CoalescingWriter
import com.oiuy.volumecontrol.volume.FineVolumePort
import com.oiuy.volumecontrol.volume.PortResult
import com.oiuy.volumecontrol.volume.ReflectiveSamsungFineVolumePort
import com.oiuy.volumecontrol.volume.isSamsungManufacturer
import com.oiuy.volumecontrol.widget.VolumeDialWidgetReceiver

/**
 * Reads only the hardware volume buttons (key event filtering). It asks for no accessibility
 * events and cannot read window content. Android delivers volume keys to it only while the
 * screen is on; with the screen off the buttons keep Android's normal steps.
 */
class VolumeButtonService : AccessibilityService() {
    private var handler: VolumeKeyHandler? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val app = applicationContext
        val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val power = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        val prefs = VolumeButtonPrefs(app)
        val fine: FineVolumePort? = if (isSamsungManufacturer(Build.MANUFACTURER)) {
            try {
                ReflectiveSamsungFineVolumePort(app).takeIf { it.availability() is PortResult.Ok }
            } catch (_: RuntimeException) {
                null
            }
        } else {
            null
        }
        val env = object : VolumeKeyEnvironment {
            override fun featureEnabled(): Boolean = prefs.enabled
            override fun stepSize(): Int = prefs.stepSize
            override fun screenInteractive(): Boolean = power.isInteractive
            override fun audioMode(): Int = audioManager.mode
            override fun publicMax(): Int = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        }
        handler = VolumeKeyHandler(
            env = env,
            fine = fine,
            scheduler = MainScheduler(),
            repeatTimeoutMs = ViewConfiguration.getKeyRepeatTimeout().toLong(),
            repeatIntervalMs = ViewConfiguration.getKeyRepeatDelay().toLong(),
        ).apply {
            onStepped = { notifyApp() }
            onPressEnded = { VolumeDialWidgetReceiver.requestUpdate(app) }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val h = handler ?: return false
        return try {
            h.onKeyEvent(event.keyCode, event.action, event.repeatCount)
        } catch (_: RuntimeException) {
            false
        }
    }

    // No event types are requested, so nothing arrives here.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        handler?.reset()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        handler?.reset()
        handler = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        handler?.reset()
        handler = null
        super.onDestroy()
    }

    /** Lets the open app move its dial; package-only broadcast, no data attached. */
    private fun notifyApp() {
        sendBroadcast(Intent(ACTION_FINE_VOLUME_CHANGED).setPackage(packageName))
    }

    private class MainScheduler : CoalescingWriter.Scheduler {
        private val main = Handler(Looper.getMainLooper())
        private val token = Any()

        override fun schedule(delayMs: Long, action: () -> Unit) {
            main.postAtTime({ action() }, token, SystemClock.uptimeMillis() + delayMs)
        }

        override fun cancel() = main.removeCallbacksAndMessages(token)
    }

    companion object {
        const val ACTION_FINE_VOLUME_CHANGED = "com.oiuy.volumecontrol.action.FINE_VOLUME_CHANGED"
    }
}
