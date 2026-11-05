package com.oiuy.volumecontrol.effect

enum class BackendKind(val label: String) {
    DYNAMICS_PROCESSING("DynamicsProcessing input gain (broadband)"),
    EQUALIZER("Equalizer: equal band reduction \u2014 not guaranteed flat"),
}

/**
 * One effect engine attached to audio session 0 (the deprecated global output
 * mix). Implementations wrap android.media.audiofx classes; tests use fakes.
 * Methods may throw RuntimeException (unsupported, lost control, dead object).
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
    val floorDb: Float = GainMath.APP_FLOOR_DB,
    val requestedDb: Float = 0f,
    /** What the effect API reported after the last apply (null = nothing confirmed). */
    val confirmedDb: List<Float>? = null,
    val enabledReported: Boolean = false,
    val hasControl: Boolean = false,
    val message: String? = null,
    /** Why each backend was skipped or failed during the last enable. */
    val attempts: List<String> = emptyList(),
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
    private val onStateChanged: (EffectState) -> Unit = {},
) {
    private var backend: GainBackend? = null

    var state: EffectState = EffectState()
        private set(value) {
            field = value
            onStateChanged(value)
        }

    fun enable(): EffectState {
        if (backend != null) return refresh()
        val attempts = mutableListOf<String>()
        val candidates = buildList {
            if (sdkInt >= DYNAMICS_MIN_SDK) {
                add(BackendKind.DYNAMICS_PROCESSING to dynamicsFactory)
            } else {
                attempts += "DynamicsProcessing skipped: needs Android 9 (API 28), device is API $sdkInt"
            }
            add(BackendKind.EQUALIZER to equalizerFactory)
        }
        for ((kind, factory) in candidates) {
            val b = try {
                factory.create()
            } catch (e: RuntimeException) {
                attempts += "${kind.shortName()} unavailable on session 0: ${e.describe()}"
                continue
            } catch (e: LinkageError) {
                attempts += "${kind.shortName()} unavailable: ${e.describe()}"
                continue
            }
            try {
                val confirmed = b.apply(0f)
                val enabled = b.setEnabled(true)
                if (!enabled) {
                    attempts += "${kind.shortName()} created but did not report enabled"
                    safeRelease(b)
                    continue
                }
                backend = b
                b.setControlListener { has -> onControlChanged(has) }
                state = EffectState(
                    active = true,
                    backend = kind,
                    backendDetail = b.detail,
                    floorDb = GainMath.effectiveFloorDb(b.minDb),
                    requestedDb = 0f,
                    confirmedDb = confirmed,
                    enabledReported = true,
                    hasControl = safeHasControl(b),
                    message = null,
                    attempts = attempts.toList(),
                )
                return state
            } catch (e: RuntimeException) {
                attempts += "${kind.shortName()} rejected setup: ${e.describe()}"
                safeRelease(b)
            }
        }
        state = EffectState(
            active = false,
            message = "No global output effect could be attached on this device. " +
                "Nothing was changed; media volume is untouched.",
            attempts = attempts.toList(),
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
                message = "Effect API rejected the setting: ${e.describe()}",
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
            s.copy(
                confirmedDb = confirmed,
                enabledReported = b.isEnabled(),
                hasControl = safeHasControl(b),
                message = if (!safeHasControl(b)) CONTROL_LOST else null,
            )
        } catch (e: RuntimeException) {
            s.copy(
                confirmedDb = null,
                enabledReported = runCatching { b.isEnabled() }.getOrDefault(false),
                hasControl = safeHasControl(b),
                message = (if (!safeHasControl(b)) "$CONTROL_LOST " else "") +
                    "Could not confirm the effect: ${e.describe()}",
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
            b.setControlListener(null)
            safeRelease(b)
        }
        state = EffectState(message = if (b != null) "Effect released. Back to neutral." else null)
        return state
    }

    private fun onControlChanged(has: Boolean) {
        if (backend == null) return
        state = state.copy(hasControl = has, message = if (has) null else CONTROL_LOST)
    }

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

private fun BackendKind.shortName(): String = when (this) {
    BackendKind.DYNAMICS_PROCESSING -> "DynamicsProcessing"
    BackendKind.EQUALIZER -> "Equalizer"
}

private fun Throwable.describe(): String =
    "${this::class.java.simpleName}${message?.let { ": $it" } ?: ""}"
