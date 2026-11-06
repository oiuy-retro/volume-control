package com.oiuy.volumecontrol.effect

enum class BackendKind(val label: String, val shortName: String) {
    DYNAMICS_PROCESSING("DynamicsProcessing input gain (broadband)", "DynamicsProcessing"),
    EQUALIZER("Equalizer: equal band reduction \u2014 not guaranteed flat", "Equalizer"),
}

/** Which backend(s) the user lets "Enable" try. */
enum class BackendPreference(val label: String) {
    AUTO("Auto (DynamicsProcessing, then Equalizer)"),
    DYNAMICS_ONLY("DynamicsProcessing only"),
    EQUALIZER_ONLY("Equalizer only"),
}

/**
 * Failure of one named step of one backend, e.g. stage "setInputGain" with
 * detail "−6.0 dB on 2 channel(s)". [cause] is the original platform exception.
 */
class BackendStageException(
    val backend: BackendKind,
    val stage: String,
    val detail: String?,
    override val cause: Throwable,
) : RuntimeException(cause) {
    override val message: String
        get() = describeStageFailure(backend, stage, detail, cause)
}

fun describeThrowable(t: Throwable): String =
    "${t::class.java.simpleName}${t.message?.let { ": $it" } ?: ""}"

fun describeStageFailure(backend: BackendKind, stage: String, detail: String?, cause: Throwable): String =
    "${backend.shortName} [$stage${detail?.let { ": $it" } ?: ""}] ${describeThrowable(cause)}"

/** Run [block]; wrap any platform exception with backend + stage + detail. */
inline fun <T> stage(backend: BackendKind, name: String, detail: String? = null, block: () -> T): T =
    try {
        block()
    } catch (e: BackendStageException) {
        throw e
    } catch (e: RuntimeException) {
        throw BackendStageException(backend, name, detail, e)
    }

/**
 * DynamicsProcessing engine layout this app requests. Pure data so the choice
 * is unit-tested. The AOSP reference engines only implement the
 * frequency-resolution variant (EffectDynamicsProcessing.cpp DP_changeVariant;
 * aidl DynamicsProcessingContext::setEngineArchitecture), which is also the
 * platform's default (DynamicsProcessing.CONFIG_DEFAULT_VARIANT).
 */
object DynamicsConfigPlan {
    enum class Variant { FAVOR_FREQUENCY_RESOLUTION, FAVOR_TIME_RESOLUTION }

    val variant = Variant.FAVOR_FREQUENCY_RESOLUTION
    const val CHANNELS = 2
    const val PRE_EQ_IN_USE = false
    const val PRE_EQ_BANDS = 0
    const val MBC_IN_USE = false
    const val MBC_BANDS = 0
    const val POST_EQ_IN_USE = false
    const val POST_EQ_BANDS = 0
    const val LIMITER_IN_USE = false

    fun describe(): String =
        "variant=${variant.name}, channels=$CHANNELS, preEq/mbc/postEq/limiter off"
}

/**
 * One effect engine attached to audio session 0 (the deprecated global output
 * mix). Implementations wrap android.media.audiofx classes; tests use fakes.
 * Methods throw [BackendStageException] (or another RuntimeException) on failure.
 */
interface GainBackend {
    val kind: BackendKind

    /** Deepest attenuation this engine reports it can do, in dB (null = unknown). */
    val minDb: Float?

    /** Short human description, e.g. "2 channels" or "5 bands, range −15..+15 dB". */
    val detail: String

    /** Request [db] (≤ 0) and return the values the effect API reports back, one per channel/band. */
    fun apply(db: Float): List<Float>

    /** Enable/disable; returns the enabled state the effect API reports afterwards. */
    fun setEnabled(enabled: Boolean): Boolean

    fun isEnabled(): Boolean
    fun hasControl(): Boolean
    fun setControlListener(listener: ((Boolean) -> Unit)?)
    fun release()
}

fun interface BackendFactory {
    /** Create the engine on session 0. Throws if the device refuses. */
    fun create(): GainBackend
}

data class EffectState(
    val active: Boolean = false,
    val backend: BackendKind? = null,
    val backendDetail: String = "",
    val preference: BackendPreference = BackendPreference.AUTO,
    val floorDb: Float = GainMath.APP_FLOOR_DB,
    val requestedDb: Float = 0f,
    /** What the effect API reported after the last apply (null = nothing confirmed). */
    val confirmedDb: List<Float>? = null,
    val enabledReported: Boolean = false,
    val hasControl: Boolean = false,
    val message: String? = null,
    /** Why each backend was skipped or failed during the last enable (stage, class, message). */
    val attempts: List<String> = emptyList(),
    /** Platform effect list lines (from AudioEffect.queryEffects) captured at enable. */
    val platformEffects: List<String> = emptyList(),
) {
    /** Confirmed values agree with the request (within rounding). */
    val confirmedMatchesRequest: Boolean
        get() = confirmedDb?.isNotEmpty() == true &&
            confirmedDb.all { kotlin.math.abs(it - requestedDb) < 0.051f }
}

/**
 * Pure state machine for the experiment. Starts neutral (inactive, 0 dB).
 * Never touches Android media volume.
 */
class EffectController(
    private val sdkInt: Int,
    private val dynamicsFactory: BackendFactory,
    private val equalizerFactory: BackendFactory,
    private val platformEffects: () -> List<String> = { emptyList() },
    private val onStateChanged: (EffectState) -> Unit = {},
) {
    private var backend: GainBackend? = null

    var state: EffectState = EffectState()
        private set(value) {
            field = value
            onStateChanged(value)
        }

    /** Choose which backend(s) Enable tries. Only while inactive. */
    fun setPreference(p: BackendPreference): EffectState {
        if (backend != null) return state
        state = state.copy(preference = p)
        return state
    }

    fun enable(): EffectState {
        if (backend != null) return refresh()
        val pref = state.preference
        val attempts = mutableListOf<String>()
        val effects = runCatching { platformEffects() }
            .getOrElse { listOf("Effect list unavailable: ${describeThrowable(it)}") }
        val candidates = buildList {
            if (pref != BackendPreference.EQUALIZER_ONLY) {
                if (sdkInt >= DYNAMICS_MIN_SDK) {
                    add(BackendKind.DYNAMICS_PROCESSING to dynamicsFactory)
                } else {
                    attempts += "DynamicsProcessing skipped: needs Android 9 (API 28), device is API $sdkInt"
                }
            }
            if (pref != BackendPreference.DYNAMICS_ONLY) add(BackendKind.EQUALIZER to equalizerFactory)
        }
        for ((kind, factory) in candidates) {
            val b = try {
                factory.create()
            } catch (e: BackendStageException) {
                attempts += "Failed: ${e.message}"
                continue
            } catch (e: RuntimeException) {
                attempts += "Failed: ${describeStageFailure(kind, "create", "session 0", e)}"
                continue
            } catch (e: LinkageError) {
                attempts += "Failed: ${kind.shortName} [create] ${describeThrowable(e)}"
                continue
            }
            try {
                val confirmed = b.apply(0f)
                val enabled = stage(kind, "setEnabled(true)") { b.setEnabled(true) }
                if (!enabled) {
                    attempts += "Failed: ${kind.shortName} [setEnabled(true)] effect API reports still disabled"
                    safeRelease(b)
                    continue
                }
                backend = b
                b.setControlListener { has -> onControlChanged(has) }
                attempts += "Active: ${kind.shortName} (${b.detail})"
                state = EffectState(
                    active = true,
                    backend = kind,
                    backendDetail = b.detail,
                    preference = pref,
                    floorDb = GainMath.effectiveFloorDb(b.minDb),
                    requestedDb = 0f,
                    confirmedDb = confirmed,
                    enabledReported = true,
                    hasControl = safeHasControl(b),
                    message = null,
                    attempts = attempts.toList(),
                    platformEffects = effects,
                )
                return state
            } catch (e: BackendStageException) {
                attempts += "Failed: ${e.message}"
                safeRelease(b)
            } catch (e: RuntimeException) {
                attempts += "Failed: ${describeStageFailure(kind, "setup", null, e)}"
                safeRelease(b)
            }
        }
        state = EffectState(
            active = false,
            preference = pref,
            message = "No global output effect could be attached with \"${pref.label}\". " +
                "This may simply be a device restriction. Nothing was changed; media volume is untouched.",
            attempts = attempts.toList(),
            platformEffects = effects,
        )
        return state
    }

    fun setRequestedDb(db: Float): EffectState {
        val b = backend ?: return state
        val s = state
        val target = GainMath.clampAttenuation(db, s.floorDb)
        if (target == s.requestedDb && s.confirmedDb != null) return s
        state = try {
            val confirmed = b.apply(target)
            s.copy(requestedDb = target, confirmedDb = confirmed, hasControl = safeHasControl(b), message = null)
        } catch (e: RuntimeException) {
            s.copy(
                requestedDb = target,
                confirmedDb = null,
                hasControl = safeHasControl(b),
                message = "Effect API rejected the setting: ${failureText(b.kind, e)}",
            )
        }
        return state
    }

    fun reset(): EffectState = setRequestedDb(0f)

    /** Re-read enabled/control/confirmed values (e.g. when returning to the app). */
    fun refresh(): EffectState {
        val b = backend ?: return state
        val s = state
        state = try {
            val confirmed = b.apply(s.requestedDb)
            val has = safeHasControl(b)
            s.copy(
                confirmedDb = confirmed,
                enabledReported = b.isEnabled(),
                hasControl = has,
                message = if (!has) CONTROL_LOST else null,
            )
        } catch (e: RuntimeException) {
            val has = safeHasControl(b)
            s.copy(
                confirmedDb = null,
                enabledReported = runCatching { b.isEnabled() }.getOrDefault(false),
                hasControl = has,
                message = (if (!has) "$CONTROL_LOST " else "") +
                    "Could not confirm the effect: ${failureText(b.kind, e)}",
            )
        }
        return state
    }

    fun disable(): EffectState {
        val b = backend
        backend = null
        if (b != null) {
            runCatching { b.apply(0f) }
            runCatching { b.setEnabled(false) }
            runCatching { b.setControlListener(null) }
            safeRelease(b)
        }
        state = EffectState(
            preference = state.preference,
            message = if (b != null) "Effect released. Back to neutral." else null,
        )
        return state
    }

    private fun onControlChanged(has: Boolean) {
        if (backend == null) return
        state = state.copy(hasControl = has, message = if (has) null else CONTROL_LOST)
    }

    private fun failureText(kind: BackendKind, e: RuntimeException): String =
        if (e is BackendStageException) e.message else describeStageFailure(kind, "apply", null, e)

    private fun safeHasControl(b: GainBackend): Boolean = runCatching { b.hasControl() }.getOrDefault(false)

    private fun safeRelease(b: GainBackend) {
        runCatching { b.release() }
    }

    companion object {
        const val DYNAMICS_MIN_SDK = 28
        const val CONTROL_LOST =
            "Another app took control of this effect. Your setting may not be applied."
    }
}
