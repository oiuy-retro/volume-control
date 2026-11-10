package com.oiuy.volumecontrol.volume

enum class VolumeMode { SAMSUNG_FINE, ANDROID_STEPS }

enum class WriteResult { WROTE, FAILED, MODE_CHANGED }

/**
 * Chooses between Samsung fine media volume and Android's public integer
 * steps, and does all reads and writes in "units" of the active mode.
 *
 * Samsung fine mode: units = public max × [FINE_UNITS_PER_STEP] (150 for a
 * 15-step phone). Reads and writes go through Samsung's fine getter and setter. The
 * public setter is never called in this mode, because it would snap the fine
 * value to a whole step.
 *
 * Any fine failure switches to Android steps for the rest of this engine's
 * life. The failing call is not repeated through the public path; callers
 * resync from the system when they see [WriteResult.MODE_CHANGED].
 */
class VolumeEngine(
    isSamsung: Boolean,
    private val stream: StreamPort,
    fineFactory: () -> FineVolumePort,
) {
    private var fine: FineVolumePort? = null

    /** Why Samsung fine volume is not active, or null when it is. */
    var fallbackReason: String? = null
        private set

    init {
        if (!isSamsung) {
            fallbackReason = "not a Samsung device"
        } else {
            val port = try {
                fineFactory()
            } catch (e: RuntimeException) {
                fallbackReason = "${e.javaClass.simpleName}: ${e.message ?: "no message"}"
                null
            }
            if (port != null) {
                when (val a = port.availability()) {
                    is PortResult.Ok -> fine = port
                    is PortResult.Failed -> fallbackReason = a.reason
                }
            }
        }
    }

    val mode: VolumeMode
        get() = if (fine != null) VolumeMode.SAMSUNG_FINE else VolumeMode.ANDROID_STEPS

    fun maxUnits(): Int {
        val publicMax = stream.max().coerceAtLeast(0)
        return if (fine != null) publicMax * FINE_UNITS_PER_STEP else publicMax
    }

    /** Read only. A failed fine read switches to Android steps and reads those instead. */
    fun readUnits(): Int {
        fine?.let { port ->
            when (val r = port.readFine()) {
                is PortResult.Ok -> return r.value.coerceIn(0, maxUnits())
                is PortResult.Failed -> fallBack("read failed: ${r.reason}")
            }
        }
        return stream.index().coerceIn(0, maxUnits())
    }

    fun writeUnits(target: Int): WriteResult {
        val clamped = target.coerceIn(0, maxUnits())
        fine?.let { port ->
            return when (val r = port.stepTo(clamped)) {
                is PortResult.Ok -> WriteResult.WROTE
                is PortResult.Failed -> {
                    fallBack("write failed: ${r.reason}")
                    WriteResult.MODE_CHANGED
                }
            }
        }
        return when (stream.setIndex(clamped)) {
            is PortResult.Ok -> WriteResult.WROTE
            is PortResult.Failed -> WriteResult.FAILED
        }
    }

    /** System panel only in Android-steps mode (no adjust call is made in fine mode). */
    fun showSystemUi() {
        if (mode == VolumeMode.ANDROID_STEPS) stream.showUi()
    }

    private fun fallBack(reason: String) {
        fine = null
        fallbackReason = reason
    }

    companion object {
        const val FINE_UNITS_PER_STEP = 10
    }
}

/** One-line description of the active mode for the status line. */
fun volumeStatusText(mode: VolumeMode, units: Int, maxUnits: Int, fallbackReason: String?): String =
    when (mode) {
        VolumeMode.SAMSUNG_FINE -> "Fine volume: Samsung ($units / $maxUnits)"
        VolumeMode.ANDROID_STEPS ->
            "Volume: Android steps ($units / $maxUnits)" +
                (fallbackReason?.let { " · Samsung fine volume off: $it" } ?: "")
    }
