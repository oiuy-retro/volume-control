package com.oiuy.volumecontrol.volume

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Dial geometry where every visible tick is one output unit.
 *
 * A dial position is a fractional **level** in output units (0..maxUnits).
 * One full turn is [unitsPerTurn] units, and the bezel shows [unitsPerTurn]
 * marks, so each mark is exactly one volume change:
 * - Samsung fine mode: [FINE_UNITS_PER_TURN] (50) units per turn. On a
 *   150-unit phone that is 150 / 50 = 3 turns.
 * - Android steps: [FINE_UNITS_PER_TURN] / [VolumeEngine.FINE_UNITS_PER_STEP]
 *   (5) steps per turn, so the same turn covers the same share of the range in
 *   either mode.
 */
object TickDial {
    const val FINE_UNITS_PER_TURN: Int = 50
    const val FULL_TURN_DEGREES: Float = 360f

    /**
     * The bezel draws a longer major mark every this many units when a turn has more
     * units than this: 10 majors per turn (every 36°) on the 50-tick fine dial, with the
     * twelve o'clock mark as the first of them.
     */
    const val MAJOR_MARK_SPACING_UNITS: Int = 5

    /**
     * Magnet capture zone as a share of the spacing between magnet marks. Before the
     * 50-tick dial the magnet caught whole-percent majors within ±0.1% (a tenth of their
     * spacing, ±3.6°); the same share of the 5-unit major spacing is ±0.5 units (±3.6°).
     */
    const val MAGNET_ZONE_FRACTION: Float = 0.1f

    /**
     * Post-snap deadzone: after a snap, raw must move this many ticks (units)
     * from that tick before the magnet can snap again. Two ticks of the 50-tick
     * dial = 2 fine units = 14.4°. (The earlier 100-mark dial used 0.5% =
     * 5 of its 3.6° ticks = 18°; this is retuned for the 7.2° ticks.)
     */
    const val MAGNET_RESNAP_CLEAR_UNITS: Float = 2f

    fun unitsPerTurn(mode: VolumeMode): Int = when (mode) {
        VolumeMode.SAMSUNG_FINE -> FINE_UNITS_PER_TURN
        VolumeMode.ANDROID_STEPS ->
            (FINE_UNITS_PER_TURN / VolumeEngine.FINE_UNITS_PER_STEP).coerceAtLeast(1)
    }

    /** Full turns needed to go from 0 to [maxUnits]. */
    fun turns(maxUnits: Int, unitsPerTurn: Int): Float =
        if (unitsPerTurn <= 0) 0f else maxUnits.coerceAtLeast(0).toFloat() / unitsPerTurn

    fun degreesPerTick(unitsPerTurn: Int): Float =
        if (unitsPerTurn <= 0) FULL_TURN_DEGREES else FULL_TURN_DEGREES / unitsPerTurn

    /** Units moved by a rotation of [deltaDegrees] (clockwise positive). */
    fun unitsFromDegrees(deltaDegrees: Float, unitsPerTurn: Int): Float =
        if (unitsPerTurn <= 0) 0f else deltaDegrees / FULL_TURN_DEGREES * unitsPerTurn

    fun clampLevel(level: Float, maxUnits: Int): Float =
        if (maxUnits <= 0) 0f else level.coerceIn(0f, maxUnits.toFloat())

    /** The output unit for [level]; the same rounding the controller writes with. */
    fun tickIndex(level: Float, maxUnits: Int): Int = DialMath.toStreamIndex(clampLevel(level, maxUnits), maxUnits)

    /**
     * Units between drawn major marks: [MAJOR_MARK_SPACING_UNITS] when a turn has more marks
     * than that (fine mode), otherwise one full turn (Android steps: only twelve o'clock).
     */
    fun majorSpacing(unitsPerTurn: Int): Int = when {
        unitsPerTurn <= 0 -> 0
        unitsPerTurn > MAJOR_MARK_SPACING_UNITS -> MAJOR_MARK_SPACING_UNITS
        else -> unitsPerTurn
    }

    /** Whether bezel mark [indexInTurn] (0 = twelve o'clock) is drawn as a major mark. */
    fun isDrawnMajorMark(indexInTurn: Int, unitsPerTurn: Int): Boolean {
        val spacing = majorSpacing(unitsPerTurn)
        return spacing > 0 && Math.floorMod(indexInTurn, spacing) == 0
    }

    /** Strong detent at every drawn major mark (twelve o'clock included) and at the top end. */
    fun isMajorTick(tick: Int, unitsPerTurn: Int, maxUnits: Int): Boolean {
        val spacing = majorSpacing(unitsPerTurn)
        if (spacing <= 0) return false
        return tick == maxUnits || Math.floorMod(tick, spacing) == 0
    }

    /**
     * Units between magnet marks: the drawn major marks in fine mode. In Android-steps mode
     * every tick is a whole step and keeps its own magnet, as before.
     */
    fun magnetSpacing(unitsPerTurn: Int): Int = when {
        unitsPerTurn <= 0 -> 1
        unitsPerTurn > MAJOR_MARK_SPACING_UNITS -> MAJOR_MARK_SPACING_UNITS
        else -> 1
    }

    /** Capture zone in units: ±0.5 in fine mode (±3.6°), ±0.1 step in Android-steps mode. */
    fun magnetZoneUnits(unitsPerTurn: Int): Float = MAGNET_ZONE_FRACTION * magnetSpacing(unitsPerTurn)

    /** Magnet marks: multiples of [magnetSpacing], plus 0 and [maxUnits]. */
    fun isMagnetMark(tick: Int, unitsPerTurn: Int, maxUnits: Int): Boolean {
        if (tick < 0 || tick > maxUnits) return false
        return tick == 0 || tick == maxUnits || Math.floorMod(tick, magnetSpacing(unitsPerTurn)) == 0
    }

    fun crossedIncludesMajor(fromTick: Int, toTick: Int, unitsPerTurn: Int, maxUnits: Int): Boolean {
        if (fromTick == toTick) return false
        val step = if (toTick > fromTick) 1 else -1
        var t = fromTick
        while (t != toTick) {
            t += step
            if (isMajorTick(t, unitsPerTurn, maxUnits)) return true
        }
        return false
    }

    /**
     * Indicator angle for [level]: 0° (twelve o'clock) at every full turn,
     * advancing [degreesPerTick] per unit. The top of the range points at the
     * angle its unit falls on within its turn (noon when maxUnits is a whole
     * number of turns).
     */
    fun indicatorDegrees(level: Float, unitsPerTurn: Int, maxUnits: Int): Float {
        if (unitsPerTurn <= 0) return 0f
        val l = clampLevel(level, maxUnits)
        val within = l - floor(l / unitsPerTurn) * unitsPerTurn
        return within / unitsPerTurn * FULL_TURN_DEGREES
    }

    /** Portion of [deltaDegrees] that keeps [level] inside 0..[maxUnits] (hard stops). */
    fun clampRotationDegrees(deltaDegrees: Float, level: Float, unitsPerTurn: Int, maxUnits: Int): Float {
        if (deltaDegrees == 0f || unitsPerTurn <= 0 || maxUnits <= 0) return 0f
        val l = clampLevel(level, maxUnits)
        val unitsPerDegree = unitsPerTurn / FULL_TURN_DEGREES
        return if (deltaDegrees > 0f) {
            val room = maxUnits - l
            if (room <= 0f) 0f else deltaDegrees.coerceAtMost(room / unitsPerDegree)
        } else {
            if (l <= 0f) 0f else deltaDegrees.coerceAtLeast(-l / unitsPerDegree)
        }
    }

    /** Ticks swept moving from [from] to [to], in travel order (upward (from, to], downward [to, from)). */
    fun ticksCrossedBySweep(from: Float, to: Float, maxUnits: Int): List<Int> {
        val a = clampLevel(from, maxUnits)
        val b = clampLevel(to, maxUnits)
        if (a == b) return emptyList()
        val out = ArrayList<Int>(4)
        if (b > a) {
            var t = floor(a.toDouble()).toInt() + 1
            while (t <= b + 1e-4f) {
                if (t > a + 1e-4f) out += t
                t++
            }
        } else {
            var t = ceil(a.toDouble()).toInt() - 1
            while (t >= b - 1e-4f) {
                if (t < a - 1e-4f) out += t
                t--
            }
        }
        return out
    }

    /** Nearest magnet mark to [level] (a multiple of [magnetSpacing], or 0 / [maxUnits]). */
    fun nearestMagnetMark(level: Float, unitsPerTurn: Int, maxUnits: Int): Int {
        val max = maxUnits.coerceAtLeast(0)
        val l = clampLevel(level, max)
        val spacing = magnetSpacing(unitsPerTurn)
        val down = (floor(l / spacing).toInt() * spacing).coerceIn(0, max)
        val up = (down + spacing).coerceAtMost(max)
        return listOf(down, up, max).minByOrNull { abs(l - it) } ?: 0
    }

    private fun inZone(level: Float, mark: Int, unitsPerTurn: Int): Boolean =
        abs(level - mark) <= magnetZoneUnits(unitsPerTurn) + 1e-4f

    /**
     * Magnet overlay for one drag frame, on the magnet marks (the drawn major marks in fine
     * mode, every step in Android-steps mode). Same rules as the earlier whole-percent magnet:
     * snap when raw is within [magnetZoneUnits] of a mark or a fast frame sweeps across one;
     * after a snap, no new snap until raw moves [MAGNET_RESNAP_CLEAR_UNITS] away. The overlay
     * only changes the reported (drawn) level; callers keep the raw level for the next delta.
     */
    fun resolveMagnet(
        previousRaw: Float,
        raw: Float,
        lastSnapped: Float,
        holding: Boolean,
        unitsPerTurn: Int,
        maxUnits: Int,
    ): TickMagnetResult {
        val prev = clampLevel(previousRaw, maxUnits)
        val r = clampLevel(raw, maxUnits)
        var latch = lastSnapped
        if (!latch.isNaN() && abs(r - latch) >= MAGNET_RESNAP_CLEAR_UNITS - 1e-4f) latch = Float.NaN
        val mark = nearestMagnetMark(r, unitsPerTurn, maxUnits)
        val zone = inZone(r, mark, unitsPerTurn)
        val nearest = mark.toFloat()
        // Keep holding only on the same tick; a different tick is a new snap (deadzone rules).
        if (holding && zone && (latch.isNaN() || latch == nearest)) {
            return TickMagnetResult(nearest, nearest, true)
        }
        if (latch.isNaN()) {
            if (zone) return TickMagnetResult(nearest, nearest, true)
            val crossed = ticksCrossedBySweep(prev, r, maxUnits)
                .filter { isMagnetMark(it, unitsPerTurn, maxUnits) }
            if (crossed.isNotEmpty()) {
                val snapped = crossed.first().toFloat()
                return TickMagnetResult(snapped, snapped, false)
            }
        }
        return TickMagnetResult(r, latch, false)
    }
}

data class TickMagnetResult(
    val reportedLevel: Float,
    val lastSnapped: Float,
    val holding: Boolean,
)
