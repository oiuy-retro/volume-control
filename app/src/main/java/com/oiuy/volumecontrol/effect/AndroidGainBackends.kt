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
    private val channels: Int,
) : GainBackend {
    override val kind = BackendKind.DYNAMICS_PROCESSING
    override val minDb: Float? = null // no documented minimum; app floor applies
    override val detail: String = "$channels channel(s), ${DynamicsConfigPlan.describe()}, session 0"

    override fun apply(db: Float): List<Float> {
        val v = minOf(0f, db)
        val d = "${GainMath.formatDb(v)} on $channels channel(s)"
        stage(kind, "setInputGainAllChannelsTo", d) { effect.setInputGainAllChannelsTo(v) }
        return (0 until channels).map { ch ->
            stage(kind, "getInputGainByChannelIndex", "channel $ch") { effect.getInputGainByChannelIndex(ch) }
        }
    }

    override fun setEnabled(enabled: Boolean): Boolean {
        val code = stage(kind, "setEnabled($enabled)") { effect.setEnabled(enabled) }
        if (code != AudioEffect.SUCCESS) {
            throw BackendStageException(
                kind, "setEnabled($enabled)", "status $code",
                IllegalStateException("AudioEffect.setEnabled returned $code"),
            )
        }
        return stage(kind, "getEnabled") { effect.enabled }
    }

    override fun isEnabled(): Boolean = stage(kind, "getEnabled") { effect.enabled }
    override fun hasControl(): Boolean = effect.hasControl()

    override fun setControlListener(listener: ((Boolean) -> Unit)?) {
        effect.setControlStatusListener(
            listener?.let { l -> AudioEffect.OnControlStatusChangeListener { _, has -> l(has) } },
        )
    }

    override fun release() = effect.release()

    companion object {
        fun create(): GainBackend {
            val k = BackendKind.DYNAMICS_PROCESSING
            val config = stage(k, "Config.Builder", DynamicsConfigPlan.describe()) {
                DynamicsProcessing.Config.Builder(
                    when (DynamicsConfigPlan.variant) {
                        DynamicsConfigPlan.Variant.FAVOR_FREQUENCY_RESOLUTION ->
                            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION
                        DynamicsConfigPlan.Variant.FAVOR_TIME_RESOLUTION ->
                            DynamicsProcessing.VARIANT_FAVOR_TIME_RESOLUTION
                    },
                    DynamicsConfigPlan.CHANNELS,
                    DynamicsConfigPlan.PRE_EQ_IN_USE, DynamicsConfigPlan.PRE_EQ_BANDS,
                    DynamicsConfigPlan.MBC_IN_USE, DynamicsConfigPlan.MBC_BANDS,
                    DynamicsConfigPlan.POST_EQ_IN_USE, DynamicsConfigPlan.POST_EQ_BANDS,
                    DynamicsConfigPlan.LIMITER_IN_USE,
                ).build()
            }
            // The constructor creates the engine, then sets engine architecture and
            // per-channel parameters; any of those can be rejected by the device.
            val effect = stage(k, "constructor (create engine + set architecture)", "session 0, priority $EFFECT_PRIORITY") {
                DynamicsProcessing(EFFECT_PRIORITY, GLOBAL_OUTPUT_SESSION, config)
            }
            return try {
                val channels = stage(k, "getChannelCount") { effect.channelCount }
                if (channels <= 0) {
                    throw BackendStageException(
                        k, "getChannelCount", "reported $channels",
                        IllegalStateException("No channels reported"),
                    )
                }
                DynamicsProcessingGainBackend(effect, channels)
            } catch (e: RuntimeException) {
                runCatching { effect.release() }
                throw e
            }
        }
    }
}

/**
 * Equalizer with every band set to the same negative level. This is equal band
 * reduction, not a true flat gain: band filter shapes can leave ripple.
 */
class EqualizerGainBackend private constructor(
    private val effect: Equalizer,
    private val bands: Short,
    private val range: ShortArray,
) : GainBackend {
    override val kind = BackendKind.EQUALIZER
    override val minDb: Float? = range[0] / 100f
    override val detail: String =
        "$bands bands, reported range ${range[0] / 100f}..${range[1] / 100f} dB, session 0"

    override fun apply(db: Float): List<Float> {
        val level = GainMath.eqBandLevelMillibels(db, range[0], range[1])
        for (b in 0 until bands) {
            stage(kind, "setBandLevel", "band $b = $level mB (range ${range[0]}..${range[1]} mB)") {
                effect.setBandLevel(b.toShort(), level)
            }
        }
        return (0 until bands).map { b ->
            stage(kind, "getBandLevel", "band $b") { effect.getBandLevel(b.toShort()) / 100f }
        }
    }

    override fun setEnabled(enabled: Boolean): Boolean {
        val code = stage(kind, "setEnabled($enabled)") { effect.setEnabled(enabled) }
        if (code != AudioEffect.SUCCESS) {
            throw BackendStageException(
                kind, "setEnabled($enabled)", "status $code",
                IllegalStateException("AudioEffect.setEnabled returned $code"),
            )
        }
        return stage(kind, "getEnabled") { effect.enabled }
    }

    override fun isEnabled(): Boolean = stage(kind, "getEnabled") { effect.enabled }
    override fun hasControl(): Boolean = effect.hasControl()

    override fun setControlListener(listener: ((Boolean) -> Unit)?) {
        effect.setControlStatusListener(
            listener?.let { l -> AudioEffect.OnControlStatusChangeListener { _, has -> l(has) } },
        )
    }

    override fun release() = effect.release()

    companion object {
        fun create(): GainBackend {
            val k = BackendKind.EQUALIZER
            val effect = stage(k, "constructor", "session 0, priority $EFFECT_PRIORITY") {
                Equalizer(EFFECT_PRIORITY, GLOBAL_OUTPUT_SESSION)
            }
            return try {
                val bands = stage(k, "getNumberOfBands") { effect.numberOfBands }
                val range = stage(k, "getBandLevelRange") { effect.bandLevelRange }
                if (bands <= 0 || range.size < 2) {
                    throw BackendStageException(
                        k, "getNumberOfBands/getBandLevelRange", "bands=$bands range=${range.toList()}",
                        IllegalStateException("Unusable equalizer layout"),
                    )
                }
                EqualizerGainBackend(effect, bands, range)
            } catch (e: RuntimeException) {
                runCatching { effect.release() }
                throw e
            }
        }
    }
}

/** Public effect list (AudioEffect.queryEffects) filtered to the two types we use. */
fun describePlatformEffects(): List<String> {
    val all = AudioEffect.queryEffects() ?: return listOf("Platform effect list: unavailable")
    val wanted = buildMap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            put(AudioEffect.EFFECT_TYPE_DYNAMICS_PROCESSING, "DynamicsProcessing")
        }
        put(AudioEffect.EFFECT_TYPE_EQUALIZER, "Equalizer")
    }
    val lines = all.filter { it.type in wanted }.map { d ->
        "Platform lists ${wanted[d.type]}: \"${d.name}\" by ${d.implementor} (mode ${d.connectMode})"
    }
    val missing = wanted.filterKeys { t -> all.none { it.type == t } }.values
        .map { "Platform lists no $it effect" }
    return lines + missing
}
