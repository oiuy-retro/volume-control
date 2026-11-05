package com.oiuy.volumecontrol.volume

/**
 * Pure helpers that map dial rotation to media-volume deltas.
 *
 * Contract: one full 360° turn changes volume by 10% of [maxVolume].
 * Fractional turns apply proportionally. Clockwise (positive degrees) raises
 * volume; counterclockwise lowers it.
 *
 * Stationary bezel ticks: 100 positions per turn (3.6°). Twelve o'clock is the
 * landmark for each 10% volume step (0, 10, 20, …, 100). The rotating indicator
 * angle is derived from position within the current 10% decade.
 */
object DialMath {
    const val PERCENT_PER_FULL_TURN: Float = 0.10f
    const val FULL_TURN_DEGREES: Float = 360f

    /** Display percentage points changed by one full turn (10.0 on the 0..100 readout). */
    const val DISPLAY_PERCENT_PER_FULL_TURN: Float = PERCENT_PER_FULL_TURN * 100f

    /** Number of detent positions (indents) in one full turn. */
    const val TICKS_PER_TURN: Int = 100

    /** Angular spacing between adjacent ticks: 360 / 100 = 3.6 degrees. */
    const val DEGREES_PER_TICK: Float = FULL_TURN_DEGREES / TICKS_PER_TURN

    /** Major stationary bezel marks every this many fine ticks (36° = 1% of max). */
    const val MAJOR_TICK_EVERY: Int = 10

    /** Snap zone around each whole display percent (± two fine ticks = ±0.2%). */
    const val WHOLE_PERCENT_SNAP_ZONE: Float = 0.2f

    /**
     * Max angular drag speed (degrees per millisecond) at which whole-percent
     * snap still applies. 0.18 deg/ms ≈ 180°/s: casual slow aiming and careful
     * nudges snap into a detent; faster spins keep continuous fine control.
     */
    const val SNAP_MAX_SPEED_DEG_PER_MS: Float = 0.18f

    /** Display percentage points per degree of dial rotation. */
    val displayPercentPerDegree: Float =
        DISPLAY_PERCENT_PER_FULL_TURN / FULL_TURN_DEGREES

    /**
     * Tick index for an accumulated, unwrapped rotation (never wrapped to ±180).
     * Uses floor semantics so counterclockwise rotation yields -1 for (-3.6, 0).
     */
    fun tickIndex(accumulatedDegrees: Float): Int =
        kotlin.math.floor(accumulatedDegrees.toDouble() / DEGREES_PER_TICK.toDouble()).toInt()

    /**
     * Fine tick index from a 0..100 display percent.
     * Each tick is 0.1 percentage points (100 ticks × 0.1% = 10% per turn).
     */
    fun tickIndexFromPercent(percent: Float): Int {
        val p = percent.coerceIn(0f, 100f)
        return kotlin.math.floor((p * 10f).toDouble() + 1e-4).toInt()
    }

    /** True when [tickIndex] lands on a whole-percent major (every [MAJOR_TICK_EVERY] fine ticks). */
    fun isMajorTickIndex(tickIndex: Int): Boolean {
        // Kotlin rem keeps the dividend sign; normalize so -10, 0, 10, … are majors.
        val m = ((tickIndex % MAJOR_TICK_EVERY) + MAJOR_TICK_EVERY) % MAJOR_TICK_EVERY
        return m == 0
    }

    /**
     * Whether walking from [fromTick] to [toTick] (exclusive of from, inclusive of to)
     * crosses or lands on any major tick index.
     */
    fun crossedIncludesMajor(fromTick: Int, toTick: Int): Boolean {
        if (fromTick == toTick) return false
        val step = if (toTick > fromTick) 1 else -1
        var t = fromTick
        while (t != toTick) {
            t += step
            if (isMajorTickIndex(t)) return true
        }
        return false
    }

    /** Nearest whole display percent in 0..100 (ties round away from zero / half-up). */
    fun nearestWholePercent(percent: Float): Float {
        val p = percent.coerceIn(0f, 100f)
        // Avoid kotlin.math.round / rint banker's rounding so 46.5 → 47.
        return kotlin.math.floor((p + 0.5f).toDouble()).toFloat().coerceIn(0f, 100f)
    }

    /**
     * Snap [percent] to a whole display percent when within [zoneDisplayPercent]
     * of that integer (default ±0.2 = two fine ticks). Otherwise return [percent]
     * unchanged (still clamped to 0..100). Callers should also gate on drag
     * speed via [isSnapSpeed] so fast spins stay continuous.
     */
    fun snapToWholePercentIfNear(
        percent: Float,
        zoneDisplayPercent: Float = WHOLE_PERCENT_SNAP_ZONE,
    ): Float {
        val p = percent.coerceIn(0f, 100f)
        val nearest = nearestWholePercent(p)
        // Small epsilon so float values like 47.2f still count as ±0.2.
        return if (kotlin.math.abs(p - nearest) <= zoneDisplayPercent + 1e-4f) nearest else p
    }

    /** True when [speedDegPerMs] is slow enough for whole-percent snap. */
    fun isSnapSpeed(speedDegPerMs: Float): Boolean =
        speedDegPerMs <= SNAP_MAX_SPEED_DEG_PER_MS

    /** True when [percent] lies inside the whole-percent snap zone. */
    fun isInWholePercentSnapZone(
        percent: Float,
        zoneDisplayPercent: Float = WHOLE_PERCENT_SNAP_ZONE,
    ): Boolean {
        val p = percent.coerceIn(0f, 100f)
        val nearest = nearestWholePercent(p)
        return kotlin.math.abs(p - nearest) <= zoneDisplayPercent + 1e-4f
    }

    /**
     * Indicator rotation in degrees for [percent].
     * Twelve o'clock (0°) at every 10% landmark: 0, 10, 20, …, 100.
     * Within a decade, angle advances clockwise with the fine percent.
     */
    fun indicatorDegreesFromPercent(percent: Float): Float {
        val p = percent.coerceIn(0f, 100f)
        if (p >= 100f) return 0f
        val withinDecade = p % DISPLAY_PERCENT_PER_FULL_TURN
        return (withinDecade / DISPLAY_PERCENT_PER_FULL_TURN) * FULL_TURN_DEGREES
    }

    /**
     * Portion of [deltaDegrees] that can be applied without driving [currentPercent]
     * past 0% or 100%. Returns 0 when already at the hard stop in that direction.
     */
    fun clampRotationDegrees(deltaDegrees: Float, currentPercent: Float): Float {
        if (deltaDegrees == 0f) return 0f
        val percent = currentPercent.coerceIn(0f, 100f)
        val perDegree = displayPercentPerDegree
        return if (deltaDegrees > 0f) {
            val room = (100f - percent).coerceAtLeast(0f)
            if (room <= 0f) 0f else deltaDegrees.coerceAtMost(room / perDegree)
        } else {
            val room = percent.coerceAtLeast(0f)
            if (room <= 0f) 0f else deltaDegrees.coerceAtLeast(-room / perDegree)
        }
    }

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

    /** Fine percent 0..100 from a fractional stream level. */
    fun fractionalPercentOf(level: Float, maxVolume: Int): Float {
        if (maxVolume <= 0) return 0f
        return ((level / maxVolume.toFloat()) * 100f).coerceIn(0f, 100f)
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
