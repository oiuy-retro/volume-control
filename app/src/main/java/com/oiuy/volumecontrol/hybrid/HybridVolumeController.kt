package com.oiuy.volumecontrol.hybrid

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.oiuy.volumecontrol.effect.EffectState
import com.oiuy.volumecontrol.effect.GlobalEffectSession

data class HybridReadout(
    val streamIndex: Int,
    val maxIndex: Int,
    val effect: EffectState,
    val curveEstimated: Boolean,
    val diagnostic: Boolean,
    val message: String?,
)

/** Android plumbing; all coordinator decisions are exercised by fake-port unit tests. */
class HybridVolumeController(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var started = false
    var onChanged: () -> Unit = {}
    val maxVolume: Int get() = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val currentVolume: Int get() = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    private val core = HybridControllerCore(
        volume = object : HybridVolumePort {
            override val maxIndex get() = maxVolume
            override val index get() = currentVolume
            override fun writeIndex(index: Int) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
                scheduleSettlement()
            }
        },
        effect = object : HybridEffectPort {
            override val state get() = GlobalEffectSession.state
            override fun apply(db: Float, force: Boolean) = GlobalEffectSession.setRequestedDb(db, force)
        },
        nowMs = SystemClock::uptimeMillis,
    )
    private var routeKey = ""
    private val settlementChecks = listOf(80L, 250L, 600L, 1150L).map {
        it to Runnable {
            checkRoute()
            core.onVolumeEvent(forceReapply = false)
            core.reapplyGain() // forced API write; a vendor may reset gain after its async stream update
            onChanged()
        }
    }
    private val volumeEvent = Runnable {
        checkRoute()
        core.onVolumeEvent()
        core.reapplyGain()
        onChanged()
    }
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) { deviceTopologyChanged() }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) { deviceTopologyChanged() }
    }
    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1) != AudioManager.STREAM_MUSIC) return
            // No stream write in this path, so it cannot recursively generate volume broadcasts.
            handler.removeCallbacks(volumeEvent)
            handler.postDelayed(volumeEvent, 50L)
        }
    }

    fun start() {
        if (started) return
        started = true
        if (!GlobalEffectSession.state.active) GlobalEffectSession.enable()
        routeKey = outputRouteKey()
        core.initialize(measuredCurve())
        audioManager.registerAudioDeviceCallback(deviceCallback, handler)
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        if (Build.VERSION.SDK_INT >= 33) {
            appContext.registerReceiver(volumeReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appContext.registerReceiver(volumeReceiver, filter)
        }
        // Reconfirm the existing cut, but do not write the stream just for opening the app.
        core.reapplyGain()
    }

    fun stop() {
        if (!started) return
        started = false
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        appContext.unregisterReceiver(volumeReceiver)
        settlementChecks.forEach { handler.removeCallbacks(it.second) }
        handler.removeCallbacks(volumeEvent)
        onChanged = {}
        // Effect remains process-scoped, like the working effect-only experiment.
    }

    private fun scheduleSettlement() {
        settlementChecks.forEach { (delay, check) ->
            handler.removeCallbacks(check)
            handler.postDelayed(check, delay)
        }
    }

    fun fractionalPercent() = core.percent
    fun adjustByDegrees(degrees: Float) = core.adjustByDegrees(degrees)
    fun setOutputPercent(percent: Float) = core.setOutputPercent(percent)
    fun beginDrag() { core.dragging = true }
    fun endDrag() { core.endDrag(); scheduleSettlement() }
    fun syncFromSystem() { checkRoute(); core.onVolumeEvent(forceReapply = false) }
    fun onResume() {
        start()
        checkRoute()
        reloadCurve()
        core.onVolumeEvent()
        core.reapplyGain()
        scheduleSettlement()
        onChanged()
    }

    fun reattach() {
        // Explicit user action; disabling an old cut may briefly make sound louder.
        GlobalEffectSession.disable()
        GlobalEffectSession.enable()
        core.afterReattach()
        scheduleSettlement()
        onChanged()
    }
    fun beginDiagnostic() { core.beginDiagnostic(); onChanged() }
    fun toggleDiagnosticCut() { core.toggleDiagnosticCut(); onChanged() }
    fun endDiagnostic() { core.endDiagnostic(); onChanged() }

    fun readout() = HybridReadout(
        streamIndex = currentVolume,
        maxIndex = maxVolume,
        effect = GlobalEffectSession.state,
        curveEstimated = core.curveEstimated,
        diagnostic = core.diagnostic,
        message = core.message,
    )

    fun reloadCurve() = core.updateCurve(measuredCurve())
    // currentOutput uses output-device APIs only; unsupported sink types fall back to estimates.
    @SuppressLint("WrongConstant")
    private fun measuredCurve(): FloatArray? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        runCatching {
            val device = currentOutput()?.type ?: AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            FloatArray(maxVolume + 1) { audioManager.getStreamVolumeDb(AudioManager.STREAM_MUSIC, it, device) }
        }.getOrNull()
    } else null

    private fun deviceTopologyChanged() {
        checkRoute()
        // Routing can lag the topology callback; check again, but only for a bounded interval.
        scheduleSettlement()
    }
    private fun checkRoute() {
        val key = outputRouteKey()
        if (key == routeKey) return
        routeKey = key
        core.onRouteChanged(measuredCurve())
        scheduleSettlement()
        onChanged()
    }
    private fun outputRouteKey(): String = currentOutput()?.let { "${it.id}:${it.type}:$maxVolume" } ?: "speaker:$maxVolume"
    private fun currentOutput(): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT >= 33) {
            val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            runCatching { audioManager.getAudioDevicesForAttributes(attrs).firstOrNull() }
                .getOrNull()?.let { return it }
        }
        // Older APIs expose connected, not necessarily routed, devices. This is a best effort.
        val outs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val preferred = listOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET)
        return preferred.firstNotNullOfOrNull { t -> outs.firstOrNull { it.type == t } }
            ?: outs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
    }

}
