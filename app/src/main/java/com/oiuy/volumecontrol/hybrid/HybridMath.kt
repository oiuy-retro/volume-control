package com.oiuy.volumecontrol.hybrid

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Pure math for the hybrid dial: real Android media level plus a global
 * output attenuation that fills the gap between two adjacent levels.
 *
 * For a fine level L between whole levels n and n+1 (fraction f):
 *  - the stream is set to n+1 (the upper real level), and
 *  - the effect attenuates by dB(n) + f * (dB(n+1) - dB(n)) - dB(n+1),
 *    which is (1 - f) * (dB(n+1) - dB(n)) below level n+1.
 * So loudness moves linearly in decibels from level n to level n+1.
 * Attenuation is never positive (no boost).
 */
object HybridMath {
    /** Used when the device does not report a usable dB curve for the route. */
    const val ESTIMATED_DB_PER_STEP: Float = 2.5f

    /** Between mute (level 0) and level 1 there is no finite dB; fade over this span. */
    const val BOTTOM_SPAN_DB: Float = 30f

    /** Fractions closer than this to a whole level count as that level. */
    const val WHOLE_EPSILON: Float = 1e-3f

    data class Plan(val streamIndex: Int, val attenuationDb: Float)

    /**
     * A curve is usable when levels 1..max are finite and strictly rising by
     * a meaningful amount. Bluetooth absolute volume often reports the same
     * value (usually 0 dB) at every level, which is not usable.
     */
    fun curveIsUsable(curve: FloatArray): Boolean {
        val max = curve.size - 1
        if (max < 1) return false
        for (i in 1..max) {
            if (!curve[i].isFinite() || curve[i] > 0.5f) return false
            if (i >= 2 && curve[i] - curve[i - 1] < 0.05f) return false
        }
        return true
    }

    /** Evenly spaced estimate ending at 0 dB at the top level. Index 0 is mute. */
    fun estimatedCurve(max: Int): FloatArray =
        FloatArray(max.coerceAtLeast(0) + 1) { i ->
            if (i == 0) Float.NEGATIVE_INFINITY else -(max - i) * ESTIMATED_DB_PER_STEP
        }

    /** Quantize to 0.1 dB and clamp to [floorDb, 0]. */
    fun clampDb(db: Float, floorDb: Float): Float {
        if (db.isNaN()) return 0f
        val f = minOf(0f, floorDb)
        val r = ((db.coerceIn(f, 0f) * 10f).roundToInt() / 10f).coerceIn(f, 0f)
        return if (r == -0f) 0f else r
    }

    /**
     * Plan for fine level [level] (0..max). [curve] holds dB per index (size max+1).
     * With [effectAvailable] false, falls back to the nearest whole level, no effect.
     */
    fun plan(level: Float, max: Int, curve: FloatArray, floorDb: Float, effectAvailable: Boolean): Plan {
        if (max <= 0) return Plan(0, 0f)
        val l = if (level.isNaN()) 0f else level.coerceIn(0f, max.toFloat())
        if (!effectAvailable) return Plan(kotlin.math.round(l).toInt().coerceIn(0, max), 0f)
        val lo = floor(l).toInt().coerceIn(0, max)
        val f = l - lo
        if (f < WHOLE_EPSILON || lo >= max) return Plan(lo, 0f)
        val hi = lo + 1
        if (f > 1f - WHOLE_EPSILON) return Plan(hi, 0f)
        val att = if (lo == 0) {
            -(1f - f) * BOTTOM_SPAN_DB
        } else {
            val step = curve[hi] - curve[lo]
            -(1f - f) * step.coerceAtLeast(0f)
        }
        return Plan(hi, clampDb(att, floorDb))
    }

    /**
     * Order of the two writes so loudness never overshoots in between:
     * when the stream index goes up, apply the new (deeper) attenuation first;
     * otherwise lower the index first and then change the attenuation.
     */
    fun effectBeforeStream(currentIndex: Int, nextIndex: Int): Boolean = nextIndex > currentIndex

    /** "−1.8 dB" with a real minus sign; "0.0 dB". */
    fun formatDb(db: Float): String {
        val tenths = (db * 10f).roundToInt()
        if (tenths == 0) return "0.0 dB"
        val a = kotlin.math.abs(tenths)
        return "${if (tenths < 0) "\u2212" else "+"}${a / 10}.${a % 10} dB"
    }
}
