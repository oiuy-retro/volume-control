package com.oiuy.volumecontrol.ui

import kotlin.math.abs

/**
 * Reveal highlight for the dark dial, in the style of the Windows 10 Reveal
 * border light: a faint, wide brightening of lines near the knob pointer.
 *
 * Only lines are lit (minor marks, the outer bezel line, the knob edge); no
 * area is filled and nothing has a halo. The major marks and the pointer are
 * plain solid accent lines and are not affected. Marks far from the pointer
 * stay at their near-black base color.
 *
 * Pure Kotlin with no Android types so it can be unit tested. All functions
 * are allocation-free.
 */
object DialGlow {
    /** How far the highlight reaches on each side of the pointer, in degrees. */
    const val REVEAL_REACH_DEGREES = 80f

    /**
     * Strongest highlight, directly at the pointer, as the fraction of the way
     * from a line's near-black base color to the reveal color. The unlit parts
     * are almost invisible, so the highlight is what makes the lines readable.
     */
    const val REVEAL_MAX = 0.75f

    /** Shortest angle between two directions, 0..180 degrees. */
    fun angularDistance(aDegrees: Float, bDegrees: Float): Float {
        var d = abs(aDegrees - bDegrees) % 360f
        if (d > 180f) d = 360f - d
        return d
    }

    /**
     * Smooth falloff: 1 at distance 0, 0 at [reach] and beyond, with zero slope
     * at both ends (smoothstep), so the highlight has no visible edge.
     */
    fun falloff(distance: Float, reach: Float): Float {
        if (reach <= 0f || distance >= reach) return 0f
        val t = 1f - (distance.coerceAtLeast(0f) / reach)
        return t * t * (3f - 2f * t)
    }

    /**
     * Highlight on a line at [lineDegrees] with the pointer at [pointerDegrees]:
     * 0 (dim base color) to [REVEAL_MAX].
     */
    fun reveal(lineDegrees: Float, pointerDegrees: Float): Float =
        REVEAL_MAX * falloff(angularDistance(lineDegrees, pointerDegrees), REVEAL_REACH_DEGREES)
}
