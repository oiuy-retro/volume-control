package com.oiuy.volumecontrol.buttons

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
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
import com.oiuy.volumecontrol.volume.CoalescingWriter
import com.oiuy.volumecontrol.volume.FineVolumePort
import com.oiuy.volumecontrol.volume.PortResult
import com.oiuy.volumecontrol.volume.ReflectiveSamsungFineVolumePort
import com.oiuy.volumecontrol.volume.VolumeButtonSteps
import com.oiuy.volumecontrol.volume.isSamsungManufacturer
import com.oiuy.volumecontrol.widget.VolumeDialWidgetReceiver

/**
 * Reads only the hardware volume buttons. It asks for no accessibility events and cannot
 * read window content.
 *
 * Key filtering (FLAG_REQUEST_FILTER_KEY_EVENTS) is off in the XML config and is switched on
 * at runtime only while the screen is fully on, the in-app switch is on, and Samsung fine
 * volume is available ([KeyFilterPolicy]). With the screen off the service does not filter
 * keys at all, so Android handles the buttons as if the service were not there.
 */
class VolumeButtonService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private var handler: VolumeKeyHandler? = null
    private var filter: KeyFilterController? = null
    private var conditions: (() -> Triple<Boolean, Boolean, Boolean>)? = null
    private var stateReceiver: BroadcastReceiver? = null
    private var displayListener: DisplayManager.DisplayListener? = null
    private var prefs: VolumeButtonPrefs? = null
    private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        stopWatching()
        val app = applicationContext
        val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val power = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        val displays = app.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val buttonPrefs = VolumeButtonPrefs(app)
        prefs = buttonPrefs
        val fine: FineVolumePort? = if (isSamsungManufacturer(Build.MANUFACTURER)) {
            try {
                ReflectiveSamsungFineVolumePort(app).takeIf { it.availability() is PortResult.Ok }
            } catch (_: RuntimeException) {
                null
            }
        } else {
            null
        }
        val publicMax = { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
        val screenOn = {
            isScreenOn(power.isInteractive, displays.getDisplay(Display.DEFAULT_DISPLAY)?.state)
        }
        val env = object : VolumeKeyEnvironment {
            override fun featureEnabled(): Boolean = buttonPrefs.enabled
            override fun stepSize(): Int = buttonPrefs.stepSize
            // Live on every key event; never a cached broadcast state.
            override fun screenOn(): Boolean = screenOn()
            override fun audioMode(): Int = audioManager.mode
            override fun publicMax(): Int = publicMax()
        }
        val keys = VolumeKeyHandler(
            env = env,
            fine = fine,
            scheduler = MainScheduler(main),
            repeatTimeoutMs = ViewConfiguration.getKeyRepeatTimeout().toLong(),
            repeatIntervalMs = ViewConfiguration.getKeyRepeatDelay().toLong(),
        ).apply {
            onStepped = { notifyApp() }
            onPressEnded = { VolumeDialWidgetReceiver.requestUpdate(app) }
        }
        handler = keys
        conditions = {
            Triple(
                screenOn(),
                buttonPrefs.enabled,
                fine != null && VolumeButtonSteps.fineMax(publicMax()) > 0,
            )
        }
        filter = KeyFilterController(::applyKeyFilter).apply {
            onFilteringChanged = { on -> if (!on) keys.reset() }
        }
        // Make sure the service starts without filtering, whatever the XML or a previous run left.
        applyKeyFilter(false)
        startWatching(displays, buttonPrefs)
        refreshFilter()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        refreshFilter()
        // Safety net: anything that arrives while filtering should be off goes straight back.
        if (filter?.filtering != true) return false
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
        shutDown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutDown()
        super.onDestroy()
    }

    private fun shutDown() {
        stopWatching()
        filter?.forceOff()
        filter = null
        conditions = null
        handler?.reset()
        handler = null
    }

    /** Re-reads screen, switch and fine availability and applies the filter state if it changed. */
    private fun refreshFilter() {
        val c = conditions?.invoke() ?: return
        filter?.update(screenOn = c.first, switchOn = c.second, fineAvailable = c.third)
    }

    /** Sets or clears FLAG_REQUEST_FILTER_KEY_EVENTS through setServiceInfo. */
    private fun applyKeyFilter(on: Boolean): Boolean {
        val info: AccessibilityServiceInfo = serviceInfo ?: return false
        val flag = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        info.flags = if (on) info.flags or flag else info.flags and flag.inv()
        serviceInfo = info
        return true
    }

    private fun startWatching(displays: DisplayManager, buttonPrefs: VolumeButtonPrefs) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) handler?.onScreenOff()
                refreshFilter()
            }
        }
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        stateReceiver = receiver

        // Display state changes (on, doze, Always On Display, off) can lag the broadcasts.
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                if (displayId == Display.DEFAULT_DISPLAY) refreshFilter()
            }
        }
        displays.registerDisplayListener(listener, main)
        displayListener = listener

        prefsListener = buttonPrefs.observe { refreshFilter() }
    }

    private fun stopWatching() {
        stateReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
                // Already unregistered.
            }
        }
        stateReceiver = null
        displayListener?.let {
            (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).unregisterDisplayListener(it)
        }
        displayListener = null
        prefsListener?.let { prefs?.stopObserving(it) }
        prefsListener = null
    }

    /** Lets the open app move its dial; package-only broadcast, no data attached. */
    private fun notifyApp() {
        sendBroadcast(Intent(ACTION_FINE_VOLUME_CHANGED).setPackage(packageName))
    }

    private class MainScheduler(private val main: Handler) : CoalescingWriter.Scheduler {
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
