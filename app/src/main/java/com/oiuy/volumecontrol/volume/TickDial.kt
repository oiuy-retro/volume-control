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

    /** Snap the reported level onto a tick within this many units of it. */
    const val MAGNET_ZONE_UNITS: Float = 0.1f

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

    /** Strong detent at every full turn (and at the top end). */
    fun isMajorTick(tick: Int, unitsPerTurn: Int, maxUnits: Int): Boolean {
        if (unitsPerTurn <= 0) return false
        return tick == maxUnits || Math.floorMod(tick, unitsPerTurn) == 0
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

    private fun nearestTick(level: Float, maxUnits: Int): Int =
        floor(clampLevel(level, maxUnits) + 0.5f).toInt().coerceIn(0, maxUnits.coerceAtLeast(0))

    private fun inZone(level: Float, maxUnits: Int): Boolean =
        abs(clampLevel(level, maxUnits) - nearestTick(level, maxUnits)) <= MAGNET_ZONE_UNITS + 1e-4f

    /**
     * Magnet overlay for one drag frame, on the unit ticks. Same rules as the
     * earlier whole-percent magnet: snap when raw is within [MAGNET_ZONE_UNITS]
     * of a tick or a fast frame sweeps across one; after a snap, no new snap
     * until raw moves [MAGNET_RESNAP_CLEAR_UNITS] away. The overlay only changes
     * the reported (drawn) level; callers keep the raw level for the next delta.
     */
    fun resolveMagnet(
        previousRaw: Float,
        raw: Float,
        lastSnapped: Float,
        holding: Boolean,
        maxUnits: Int,
    ): TickMagnetResult {
        val prev = clampLevel(previousRaw, maxUnits)
        val r = clampLevel(raw, maxUnits)
        var latch = lastSnapped
        if (!latch.isNaN() && abs(r - latch) >= MAGNET_RESNAP_CLEAR_UNITS - 1e-4f) latch = Float.NaN
        val zone = inZone(r, maxUnits)
        val nearest = nearestTick(r, maxUnits).toFloat()
        // Keep holding only on the same tick; a different tick is a new snap (deadzone rules).
        if (holding && zone && (latch.isNaN() || latch == nearest)) {
            return TickMagnetResult(nearest, nearest, true)
        }
        if (latch.isNaN()) {
            if (zone) return TickMagnetResult(nearest, nearest, true)
            val crossed = ticksCrossedBySweep(prev, r, maxUnits)
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
