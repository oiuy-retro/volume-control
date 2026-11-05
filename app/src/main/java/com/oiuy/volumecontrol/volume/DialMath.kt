package com.oiuy.volumecontrol.volume

/**
 * Pure helpers for the volume dial.
 *
 * Android media volume is a small set of whole-number levels
 * (`STREAM_MUSIC` index 0..[maxIndex], where the max is device-dependent and
 * read from `AudioManager.getStreamMaxVolume`). The dial never pretends there
 * is anything in between: the readout, indicator, bezel detents, and haptics
 * all follow the actual stream index.
 *
 * Sensitivity contract: one full 360° turn moves an internal (hidden) target by
 * 10% of the level range, so slow deliberate turning is preserved. The target
 * only decides *when* the next real level is requested; it is never shown as
 * volume and never triggers haptics by itself.
 */
object DialMath {
    const val PERCENT_PER_FULL_TURN: Float = 0.10f
    const val FULL_TURN_DEGREES: Float = 360f

    /**
     * Extra rotation (degrees) past the halfway point between two levels before
     * the next level is requested. Gives a small dead band so finger wobble at a
     * boundary does not flip the real volume back and forth.
     */
    const val LEVEL_HYSTERESIS_DEGREES: Float = 30f

    /** Hysteresis never exceeds this fraction of one level (keeps steps reachable). */
    const val MAX_LEVEL_HYSTERESIS: Float = 0.25f

    /** Level-range delta (fractional levels) for a rotation of [deltaDegrees]. */
    fun levelDeltaFromDegrees(deltaDegrees: Float, maxIndex: Int): Float {
        if (maxIndex <= 0) return 0f
        return (deltaDegrees / FULL_TURN_DEGREES) * PERCENT_PER_FULL_TURN * maxIndex.toFloat()
    }

    /** Clamp a fractional target to 0..[maxIndex]. */
    fun clampTarget(target: Float, maxIndex: Int): Float {
        if (maxIndex <= 0) return 0f
        return target.coerceIn(0f, maxIndex.toFloat())
    }

    /** Hysteresis in level units for [maxIndex] (see [LEVEL_HYSTERESIS_DEGREES]). */
    fun levelHysteresis(maxIndex: Int): Float {
        if (maxIndex <= 0) return 0f
        return minOf(levelDeltaFromDegrees(LEVEL_HYSTERESIS_DEGREES, maxIndex), MAX_LEVEL_HYSTERESIS)
    }

    /**
     * Whole level to request for [target], starting from the [currentIndex] that
     * is actually set. Moves up only once target passes `index + 0.5 + h`, down
     * only once it passes `index - 0.5 - h`.
     */
    fun desiredIndex(target: Float, currentIndex: Int, maxIndex: Int): Int {
        if (maxIndex <= 0) return 0
        val h = levelHysteresis(maxIndex)
        var idx = currentIndex.coerceIn(0, maxIndex)
        while (idx < maxIndex && target >= idx + 0.5f + h) idx++
        while (idx > 0 && target <= idx - 0.5f - h) idx--
        return idx
    }

    /**
     * Portion of [deltaDegrees] that can be applied without driving the hidden
     * [target] past 0 or [maxIndex] (or a tighter [lowerBound]/[upperBound] when
     * the system refused a level). Returns 0 at the hard stop in that direction.
     */
    fun clampRotationDegrees(
        deltaDegrees: Float,
        target: Float,
        maxIndex: Int,
        lowerBound: Float = 0f,
        upperBound: Float = maxIndex.toFloat(),
    ): Float {
        if (deltaDegrees == 0f || maxIndex <= 0) return 0f
        val perDegree = levelDeltaFromDegrees(1f, maxIndex)
        val lo = lowerBound.coerceIn(0f, maxIndex.toFloat())
        val hi = upperBound.coerceIn(lo, maxIndex.toFloat())
        val t = target.coerceIn(lo, hi)
        return if (deltaDegrees > 0f) {
            val room = hi - t
            if (room <= ROOM_EPSILON) 0f else deltaDegrees.coerceAtMost(room / perDegree)
        } else {
            val room = t - lo
            if (room <= ROOM_EPSILON) 0f else deltaDegrees.coerceAtLeast(-room / perDegree)
        }
    }

    private const val ROOM_EPSILON: Float = 1e-4f

    /** Rounded (half-up) percentage of the level range for [index] of [maxIndex]. */
    fun levelPercent(index: Int, maxIndex: Int): Int {
        if (maxIndex <= 0) return 0
        val i = index.coerceIn(0, maxIndex)
        return ((i * 200 + maxIndex) / (2 * maxIndex)).coerceIn(0, 100)
    }

    /**
     * Whole levels moved by one widget +/− press: about 10% of the range,
     * rounded half-up, at least one level, so every press is the same size.
     */
    fun widgetStepLevels(maxIndex: Int): Int {
        if (maxIndex <= 0) return 0
        return maxOf(1, (maxIndex + 5) / 10)
    }

    /** Indicator / progress angle for an actual level: 0 at noon, full turn at max. */
    fun levelSweepDegrees(index: Int, maxIndex: Int): Float {
        if (maxIndex <= 0) return 0f
        return index.coerceIn(0, maxIndex).toFloat() / maxIndex * FULL_TURN_DEGREES
    }

    /** Shortest signed angle delta from [fromDegrees] to [toDegrees] in (-180, 180]. */
    fun shortestAngleDelta(fromDegrees: Float, toDegrees: Float): Float {
        var delta = toDegrees - fromDegrees
        while (delta > 180f) delta -= 360f
        while (delta <= -180f) delta += 360f
        return delta
    }

    fun angleDegrees(dx: Float, dy: Float): Float {
        val radians = kotlin.math.atan2(dy.toDouble(), dx.toDouble())
        return Math.toDegrees(radians).toFloat()
    }
}
