package com.oiuy.volumecontrol.buttons

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Display
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.oiuy.volumecontrol.volume.AndroidPlainVolumeKeyPort
import com.oiuy.volumecontrol.volume.CoalescingWriter
import com.oiuy.volumecontrol.volume.FineVolumePort
import com.oiuy.volumecontrol.volume.PortResult
import com.oiuy.volumecontrol.volume.ReflectiveSamsungFineVolumePort
import com.oiuy.volumecontrol.volume.isSamsungManufacturer
import com.oiuy.volumecontrol.widget.VolumeDialWidgetReceiver

/**
 * Reads only the hardware volume buttons (key event filtering). It asks for no accessibility
 * events and cannot read window content. Fine steps happen only while the screen is on. If a
 * volume key reaches it with the screen off, it makes Android's normal one-step change itself
 * (see [VolumeKeyHandler]).
 */
class VolumeButtonService : AccessibilityService() {
    private var handler: VolumeKeyHandler? = null
    private var screenOffReceiver: BroadcastReceiver? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val app = applicationContext
        val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val power = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        val displays = app.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
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
            // Live on every key event; never a cached broadcast state.
            override fun screenOn(): Boolean =
                isScreenOn(power.isInteractive, displays.getDisplay(Display.DEFAULT_DISPLAY)?.state)
            override fun audioMode(): Int = audioManager.mode
            override fun musicActive(): Boolean = audioManager.isMusicActive
            override fun publicMax(): Int = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        }
        handler = VolumeKeyHandler(
            env = env,
            fine = fine,
            plain = AndroidPlainVolumeKeyPort(app),
            scheduler = MainScheduler(),
            repeatTimeoutMs = ViewConfiguration.getKeyRepeatTimeout().toLong(),
            repeatIntervalMs = ViewConfiguration.getKeyRepeatDelay().toLong(),
        ).apply {
            onStepped = { notifyApp() }
            onPressEnded = { VolumeDialWidgetReceiver.requestUpdate(app) }
        }
        unregisterScreenOff()
        // Extra safety on top of the live check: stop any app repeat the moment the screen goes off.
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                handler?.onScreenOff()
            }
        }
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        screenOffReceiver = receiver
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val h = handler ?: return false
        return try {
            h.onKeyEvent(event.keyCode, event.action, event.repeatCount, event.downTime)
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
        unregisterScreenOff()
        handler?.reset()
        handler = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        unregisterScreenOff()
        handler?.reset()
        handler = null
        super.onDestroy()
    }

    private fun unregisterScreenOff() {
        screenOffReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
                // Already unregistered.
            }
        }
        screenOffReceiver = null
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
