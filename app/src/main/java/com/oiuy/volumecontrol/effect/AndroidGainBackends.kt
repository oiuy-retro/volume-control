package com.oiuy.volumecontrol.effect

import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import androidx.annotation.RequiresApi

/** Audio session 0 = global output mix. Deprecated by Android and device-dependent. */
const val GLOBAL_OUTPUT_SESSION: Int = 0

/** Above-normal priority so we are likely (not guaranteed) to get control. */
private const val EFFECT_PRIORITY: Int = 1000

/**
 * DynamicsProcessing with every stage off except the per-channel input gain,
 * which is a broadband gain in dB. Read-back uses getInputGainByChannelIndex,
 * which queries the effect engine (AOSP DynamicsProcessing.getTwoFloat).
 */
@RequiresApi(Build.VERSION_CODES.P)
class DynamicsProcessingGainBackend private constructor(
    private val effect: DynamicsProcessing,
) : GainBackend {
    override val kind = BackendKind.DYNAMICS_PROCESSING
    override val minDb: Float? = null // no documented minimum; app floor applies
    private val channels: Int = runCatching { effect.channelCount }.getOrDefault(0)
    override val detail: String = "$channels channel(s), session 0"

    override fun apply(db: Float): List<Float> {
        val v = minOf(0f, db)
        effect.setInputGainAllChannelsTo(v)
        return (0 until channels).map { effect.getInputGainByChannelIndex(it) }
    }

    override fun setEnabled(enabled: Boolean): Boolean {
        effect.setEnabled(enabled)
        return effect.enabled
    }

    override fun isEnabled(): Boolean = effect.enabled
    override fun hasControl(): Boolean = effect.hasControl()

    override fun setControlListener(listener: ((Boolean) -> Unit)?) {
        effect.setControlStatusListener(
            listener?.let { l -> AudioEffect.OnControlStatusChangeListener { _, has -> l(has) } },
        )
    }

    override fun release() = effect.release()

    companion object {
        fun create(): GainBackend {
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_TIME_RESOLUTION,
                2,
                false, 0, // pre-EQ off
                false, 0, // multi-band compressor off
                false, 0, // post-EQ off
                false, // limiter off
            ).build()
            val effect = DynamicsProcessing(EFFECT_PRIORITY, GLOBAL_OUTPUT_SESSION, config)
            return DynamicsProcessingGainBackend(effect)
        }
    }
}

/**
 * Equalizer with every band set to the same negative level. This is equal band
 * reduction, not a true flat gain: band filter shapes can leave ripple.
 */
class EqualizerGainBackend private constructor(
    private val effect: Equalizer,
) : GainBackend {
    override val kind = BackendKind.EQUALIZER
    private val bands: Short = effect.numberOfBands
    private val range: ShortArray = effect.bandLevelRange
    override val minDb: Float? = range.getOrNull(0)?.let { it / 100f }
    override val detail: String =
        "$bands bands, reported range ${range.getOrNull(0)?.div(100f)}..${range.getOrNull(1)?.div(100f)} dB, session 0"

    override fun apply(db: Float): List<Float> {
        val level = GainMath.eqBandLevelMillibels(db, range[0], range[1])
        for (b in 0 until bands) effect.setBandLevel(b.toShort(), level)
        return (0 until bands).map { effect.getBandLevel(it.toShort()) / 100f }
    }

    override fun setEnabled(enabled: Boolean): Boolean {
        effect.setEnabled(enabled)
        return effect.enabled
    }

    override fun isEnabled(): Boolean = effect.enabled
    override fun hasControl(): Boolean = effect.hasControl()

    override fun setControlListener(listener: ((Boolean) -> Unit)?) {
        effect.setControlStatusListener(
            listener?.let { l -> AudioEffect.OnControlStatusChangeListener { _, has -> l(has) } },
        )
    }

    override fun release() = effect.release()

    companion object {
        fun create(): GainBackend {
            val effect = Equalizer(EFFECT_PRIORITY, GLOBAL_OUTPUT_SESSION)
            return try {
                EqualizerGainBackend(effect)
            } catch (e: RuntimeException) {
                runCatching { effect.release() }
                throw e
            }
        }
    }
}
