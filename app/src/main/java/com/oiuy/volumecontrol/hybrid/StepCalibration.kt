package com.oiuy.volumecontrol.hybrid

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Per-output step sizes measured by ear. Key = lower level of a pair: pair n is n -> n+1.
 * Pair 0 (mute -> 1) cannot be loudness-matched, so only pairs 1..max-1 are calibratable.
 */
data class StepCalibration(val maxIndex: Int, val steps: Map<Int, Float> = emptyMap()) {
    val calibratablePairs: Int get() = (maxIndex - 1).coerceAtLeast(0)
    val calibratedCount: Int get() = steps.keys.count { it in 1 until maxIndex }

    fun with(pair: Int, stepDb: Float): StepCalibration {
        require(pair in 1 until maxIndex) { "pair $pair outside 1..${maxIndex - 1}" }
        return copy(steps = steps + (pair to abs(stepDb)))
    }

    /** Saved step, else nearest calibrated pair (ties: average), else null. */
    fun stepFor(pair: Int): Float? {
        steps[pair]?.let { return it }
        val valid = steps.filterKeys { it in 1 until maxIndex }
        if (valid.isEmpty()) return null
        val best = valid.keys.minOf { abs(it - pair) }
        val nearest = valid.filterKeys { abs(it - pair) == best }.values
        return nearest.sum() / nearest.size
    }

    /** dB curve with 0 dB at max, or null when nothing is calibrated. */
    fun curve(): FloatArray? {
        if (calibratedCount == 0 || maxIndex < 2) return null
        val c = FloatArray(maxIndex + 1)
        c[maxIndex] = 0f
        for (i in maxIndex - 1 downTo 1) c[i] = c[i + 1] - (stepFor(i) ?: 0f).coerceAtLeast(MIN_STEP_DB)
        c[0] = Float.NEGATIVE_INFINITY
        return c
    }

    fun serialize(): String = steps.toSortedMap().entries.joinToString(",") {
        "${it.key}:${(it.value * 100f).roundToInt() / 100f}"
    }

    companion object {
        /** Stored step sizes are non-zero so the dB curve stays strictly rising. */
        const val MIN_STEP_DB = 0.1f
        const val DETENT_DB = 0.5f

        fun parse(maxIndex: Int, text: String?): StepCalibration {
            if (text.isNullOrBlank()) return StepCalibration(maxIndex)
            val map = text.split(',').mapNotNull { part ->
                val kv = part.split(':')
                val k = kv.getOrNull(0)?.toIntOrNull()
                val v = kv.getOrNull(1)?.toFloatOrNull()
                if (k == null || v == null || !v.isFinite() || k !in 1 until maxIndex) null else k to abs(v)
            }.toMap()
            return StepCalibration(maxIndex, map)
        }

        /** Storage key: output type + address (or product name) + step count. */
        fun key(type: Int, address: String?, productName: String?, maxIndex: Int): String {
            val id = address?.takeIf { it.isNotBlank() } ?: productName?.takeIf { it.isNotBlank() } ?: "unknown"
            return "cal|$type|$id|$maxIndex"
        }

        fun snapToDetent(db: Float): Float {
            val r = (db / DETENT_DB).roundToInt() * DETENT_DB
            return if (r == -0f) 0f else r
        }
    }
}
