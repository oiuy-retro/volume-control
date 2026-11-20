package com.oiuy.volumecontrol.ui

import com.oiuy.volumecontrol.volume.TickDial

/**
 * Drawn bezel marks. The bezel draws [VISUAL_TICKS_PER_UNIT] marks per volume
 * unit (150 per turn in Samsung fine mode, 2.4 degrees apart), but the volume,
 * magnet and haptics still work per unit (50 per turn). Every third mark sits
 * on a unit; the major marks are the unit marks that [TickDial.isDrawnMajorMark]
 * picks (every 5 units), so the drawing cannot drift from the magnet.
 *
 * Pure Kotlin so it can be unit tested.
 */
object DialMarks {
    const val VISUAL_TICKS_PER_UNIT = 3

    /** Number of drawn marks in one turn of [unitsPerTurn] units. */
    fun visualTicks(unitsPerTurn: Int): Int =
        if (unitsPerTurn <= 0) 0 else unitsPerTurn * VISUAL_TICKS_PER_UNIT

    /** Whether drawn mark [visualIndex] sits exactly on a volume unit. */
    fun isUnitTick(visualIndex: Int): Boolean = Math.floorMod(visualIndex, VISUAL_TICKS_PER_UNIT) == 0

    /** The unit at or just before drawn mark [visualIndex] (index within the turn). */
    fun unitOf(visualIndex: Int): Int = Math.floorDiv(visualIndex, VISUAL_TICKS_PER_UNIT)

    /** Whether drawn mark [visualIndex] is a major mark (crisp accent line). */
    fun isMajor(visualIndex: Int, unitsPerTurn: Int): Boolean =
        isUnitTick(visualIndex) && TickDial.isDrawnMajorMark(unitOf(visualIndex), unitsPerTurn)

    /** Angle of drawn mark [visualIndex] clockwise from twelve o'clock. */
    fun degreesOf(visualIndex: Int, unitsPerTurn: Int): Float {
        val n = visualTicks(unitsPerTurn)
        return if (n <= 0) 0f else visualIndex * (360f / n)
    }
}
