package com.oiuy.volumecontrol.volume

/**
 * Pure helpers that map dial rotation to media-volume deltas.
 *
 * Contract: one full 360° turn changes volume by 10% of [maxVolume].
 * Fractional turns apply proportionally. Clockwise (positive degrees) raises
 * volume; counterclockwise lowers it.
 */
object DialMath {
    const val PERCENT_PER_FULL_TURN: Float = 0.10f
    const val FULL_TURN_DEGREES: Float = 360f

    /** Number of detent positions (indents) in one full turn. */
    const val TICKS_PER_TURN: Int = 100

    /** Angular spacing between adjacent ticks: 360 / 100 = 3.6 degrees. */
    const val DEGREES_PER_TICK: Float = FULL_TURN_DEGREES / TICKS_PER_TURN

    /**
     * Tick index for an accumulated, unwrapped rotation (never wrapped to ±180).
     * Uses floor semantics so counterclockwise rotation yields -1 for (-3.6, 0).
     */
    fun tickIndex(accumulatedDegrees: Float): Int =
        kotlin.math.floor(accumulatedDegrees.toDouble() / DEGREES_PER_TICK.toDouble()).toInt()

    /** Signed number of tick boundaries crossed between two tick indices. */
    fun ticksCrossed(fromTick: Int, toTick: Int): Int = toTick - fromTick

    /** Volume index delta for [steps] ticks: each tick is 0.1% of [maxVolume]. */
    fun volumeDeltaFromTickSteps(steps: Int, maxVolume: Int): Float =
        volumeDeltaFromDegrees(steps * DEGREES_PER_TICK, maxVolume)

    /** Volume index delta (may be fractional) for a rotation of [deltaDegrees]. */
    fun volumeDeltaFromDegrees(deltaDegrees: Float, maxVolume: Int): Float {
        if (maxVolume <= 0) return 0f
        return (deltaDegrees / FULL_TURN_DEGREES) * PERCENT_PER_FULL_TURN * maxVolume.toFloat()
    }

    /** Clamp a fractional volume level to 0..max. */
    fun clampFractional(level: Float, maxVolume: Int): Float {
        if (maxVolume <= 0) return 0f
        return level.coerceIn(0f, maxVolume.toFloat())
    }

    /** Integer stream index from a fractional level. */
    fun toStreamIndex(level: Float, maxVolume: Int): Int {
        if (maxVolume <= 0) return 0
        return kotlin.math.round(level).toInt().coerceIn(0, maxVolume)
    }

    /** Apply a fixed percentage step of the stream max (e.g. ±0.10f for widget buttons). */
    fun applyPercentStep(current: Int, percentOfMax: Float, maxVolume: Int): Int {
        if (maxVolume <= 0) return 0
        val delta = percentOfMax * maxVolume
        return kotlin.math.round(current + delta).toInt().coerceIn(0, maxVolume)
    }

    /** Percent 0..100 for display. */
    fun percentOf(current: Int, maxVolume: Int): Int {
        if (maxVolume <= 0) return 0
        return ((current.toFloat() / maxVolume.toFloat()) * 100f)
            .toInt()
            .coerceIn(0, 100)
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
