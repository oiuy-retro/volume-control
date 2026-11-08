package com.oiuy.volumecontrol.hybrid

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import com.oiuy.volumecontrol.effect.GlobalEffectSession
import com.oiuy.volumecontrol.volume.DialMath

/** What the screen shows under the dial. */
data class HybridReadout(
    val streamIndex: Int,
    val maxIndex: Int,
    val attenuationDb: Float,
    val backendName: String?,
    val curveEstimated: Boolean,
    val message: String?,
)

/**
 * 100 ticks per turn, 10% per turn, like the main dial. Each fine position is
 * produced by a real STREAM_MUSIC level plus a session-0 attenuation from
 * [GlobalEffectSession]. If the effect cannot attach, only whole Android
 * levels are used and the readout says so.
 */
class HybridVolumeController(context: Context) {
    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var fractionalLevel: Float = currentVolume.toFloat()
    private var lastAppliedIndex: Int = currentVolume
    private var lastAttenuation: Float = 0f
    private var curve: FloatArray = FloatArray(0)
    private var curveEstimated: Boolean = true
    private var lastShowUiUptimeMs: Long = 0L

    val maxVolume: Int get() = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val currentVolume: Int get() = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    /** Attach the effect if it is not attached yet and reload the dB curve. */
    fun start() {
        if (!GlobalEffectSession.state.active) GlobalEffectSession.enable()
        reloadCurve()
        syncFromSystem()
    }

    fun fractionalPercent(): Float = DialMath.fractionalPercentOf(fractionalLevel, maxVolume)

    /** Only resync when someone else changed the level; our own writes are ignored. */
    fun syncFromSystem() {
        val cur = currentVolume
        if (cur != lastAppliedIndex) {
            fractionalLevel = cur.toFloat()
            lastAppliedIndex = cur
            setAttenuation(0f)
        }
    }

    fun adjustByDegrees(deltaDegrees: Float): Float {
        if (deltaDegrees == 0f) return fractionalPercent()
        syncFromSystem()
        val max = maxVolume
        fractionalLevel = DialMath.clampFractional(
            fractionalLevel + DialMath.volumeDeltaFromDegrees(deltaDegrees, max), max,
        )
        applyLevel(fractionalLevel)
        return fractionalPercent()
    }

    /** Output a (possibly magnet-snapped) percent without moving the raw accumulator. */
    fun setOutputPercent(percent: Float) {
        val max = maxVolume
        if (max <= 0) return
        applyLevel(DialMath.clampFractional(percent.coerceIn(0f, 100f) / 100f * max, max))
    }

    fun readout(): HybridReadout {
        val s = GlobalEffectSession.state
        return HybridReadout(
            streamIndex = lastAppliedIndex,
            maxIndex = maxVolume,
            attenuationDb = lastAttenuation,
            backendName = if (s.active) s.backend?.shortName else null,
            curveEstimated = curveEstimated,
            message = s.message ?: if (!s.active) {
                "Global effect unavailable: whole Android levels only."
            } else null,
        )
    }

    fun reloadCurve() {
        val max = maxVolume
        val measured = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                val device = currentOutputType()
                FloatArray(max + 1) { i ->
                    audioManager.getStreamVolumeDb(AudioManager.STREAM_MUSIC, i, device)
                }
            }.getOrNull()
        } else null
        if (measured != null && HybridMath.curveIsUsable(measured)) {
            curve = measured
            curveEstimated = false
        } else {
            curve = HybridMath.estimatedCurve(max)
            curveEstimated = true
        }
    }

    fun showSystemVolumeUi() {
        val now = SystemClock.uptimeMillis()
        if (now - lastShowUiUptimeMs < 120L) return
        lastShowUiUptimeMs = now
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI,
        )
    }

    private fun applyLevel(level: Float) {
        val max = maxVolume
        if (curve.size != max + 1) reloadCurve()
        val s = GlobalEffectSession.state
        val plan = HybridMath.plan(level, max, curve, s.floorDb, s.active)
        val cur = currentVolume
        if (HybridMath.effectBeforeStream(cur, plan.streamIndex)) {
            setAttenuation(plan.attenuationDb)
            setStream(cur, plan.streamIndex)
        } else {
            setStream(cur, plan.streamIndex)
            setAttenuation(plan.attenuationDb)
        }
    }

    private fun setStream(cur: Int, next: Int) {
        if (next != cur) audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
        lastAppliedIndex = next
    }

    private fun setAttenuation(db: Float) {
        if (!GlobalEffectSession.state.active) {
            lastAttenuation = 0f
            return
        }
        if (db != lastAttenuation || GlobalEffectSession.state.requestedDb != db) {
            GlobalEffectSession.setRequestedDb(db)
        }
        lastAttenuation = GlobalEffectSession.state.requestedDb
    }

    private fun currentOutputType(): Int {
        if (Build.VERSION.SDK_INT >= 33) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            audioManager.getAudioDevicesForAttributes(attrs).firstOrNull()?.let { return it.type }
        }
        val outs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }
        val preferred = listOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
        )
        return preferred.firstOrNull { it in outs } ?: AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
    }
}
