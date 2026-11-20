package com.oiuy.volumecontrol.ui

import com.oiuy.volumecontrol.volume.TickDial
import kotlin.math.abs
import kotlin.math.min

/**
 * Light model for the dark dial, in the style of the Windows 10 Reveal
 * highlight: a few light sources, and every other mark is lit only by how
 * close it is to them.
 *
 * The light sources are the major bezel marks ([TickDial.isDrawnMajorMark],
 * the same marks that have the magnet and the strong haptic) and the knob
 * pointer. Minor marks are nearly dark on their own ([MINOR_BASE]). Each light
 * adds brightness with a smooth falloff over angular distance, so as the knob
 * turns, the pointer's light sweeps across the minor marks.
 *
 * Pure Kotlin with no Android types so it can be unit tested. All functions
 * are allocation-free except [fillStaticLight], which writes into a caller
 * array.
 */
object DialGlow {
    /** Brightness of a minor mark with no light near it (barely visible). */
    const val MINOR_BASE = 0.06f

    /** Brightness of a major mark with the pointer far away. */
    const val MAJOR_BASE = 0.78f

    /** How far the pointer's light reaches on each side, in degrees. */
    const val POINTER_REACH_DEGREES = 54f

    /** Peak light the pointer adds to a mark directly under it. */
    const val POINTER_INTENSITY = 1f

    /** How far a major mark's light reaches, in mark spacings. */
    const val MAJOR_REACH_MARKS = 2.6f

    /** Upper limit on a major mark's reach in degrees (Android-steps mode has wide spacing). */
    const val MAJOR_REACH_MAX_DEGREES = 20f

    /** Peak light a major mark adds to a neighbouring minor mark. */
    const val MAJOR_INTENSITY = 0.28f

    /** Shortest angle between two directions, 0..180 degrees. */
    fun angularDistance(aDegrees: Float, bDegrees: Float): Float {
        var d = abs(aDegrees - bDegrees) % 360f
        if (d > 180f) d = 360f - d
        return d
    }

    /**
     * Smooth falloff: 1 at distance 0, 0 at [reach] and beyond, with zero slope
     * at both ends (smoothstep), so lit regions have soft edges.
     */
    fun falloff(distance: Float, reach: Float): Float {
        if (reach <= 0f || distance >= reach) return 0f
        val t = 1f - (distance.coerceAtLeast(0f) / reach)
        return t * t * (3f - 2f * t)
    }

    /** Light the pointer at [pointerDegrees] puts on a mark at [markDegrees]. */
    fun pointerLight(markDegrees: Float, pointerDegrees: Float): Float =
        POINTER_INTENSITY * falloff(angularDistance(markDegrees, pointerDegrees), POINTER_REACH_DEGREES)

    /** Reach of a major mark's light in degrees for a turn of [marks] marks. */
    fun majorReachDegrees(marks: Int): Float {
        if (marks <= 0) return 0f
        return min(MAJOR_REACH_MARKS * TickDial.degreesPerTick(marks), MAJOR_REACH_MAX_DEGREES)
    }

    /**
     * Static light that the major marks put on mark [index] of a turn of [marks]
     * marks (0 for the major marks themselves, which are light sources).
     * Sums every major mark within reach; the result is not clamped.
     */
    fun staticLight(index: Int, marks: Int): Float {
        if (marks <= 0 || TickDial.isDrawnMajorMark(index, marks)) return 0f
        val step = TickDial.degreesPerTick(marks)
        val reach = majorReachDegrees(marks)
        val markDegrees = index * step
        var sum = 0f
        for (j in 0 until marks) {
            if (!TickDial.isDrawnMajorMark(j, marks)) continue
            sum += MAJOR_INTENSITY * falloff(angularDistance(markDegrees, j * step), reach)
        }
        return sum
    }

    /** Fills [out] (size at least [marks]) with [staticLight] for every mark. */
    fun fillStaticLight(marks: Int, out: FloatArray) {
        for (i in 0 until min(marks, out.size)) out[i] = staticLight(i, marks)
    }

    /** Final brightness of a minor mark, 0..1. */
    fun minorBrightness(staticLight: Float, pointerLight: Float): Float =
        (MINOR_BASE + staticLight + pointerLight).coerceIn(0f, 1f)

    /** Final brightness of a major mark, 0..1: always lit, brighter under the pointer. */
    fun majorBrightness(pointerLight: Float): Float =
        (MAJOR_BASE + (1f - MAJOR_BASE) * pointerLight).coerceIn(0f, 1f)
}
