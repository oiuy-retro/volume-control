package com.oiuy.volumecontrol.effect

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Pure mapping for the experimental attenuation dial (no Android types).
 *
 * Attenuation only: values are always between a negative floor and 0 dB.
 * Clockwise moves toward 0 dB (less attenuation), counterclockwise adds
 * attenuation. One full turn is [DB_PER_FULL_TURN] dB.
 */
object GainMath {
    const val DB_PER_FULL_TURN: Float = 6f
    const val FULL_TURN_DEGREES: Float = 360f

    /** Requested values are kept on a 0.1 dB grid. */
    const val DB_STEP: Float = 0.1f

    /** App floor: never request more than this much attenuation. */
    const val APP_FLOOR_DB: Float = -30f

    /** Bezel/haptic detent spacing. */
    const val DETENT_DB: Float = 1f

    fun dbDeltaFromDegrees(deltaDegrees: Float): Float =
        deltaDegrees / FULL_TURN_DEGREES * DB_PER_FULL_TURN

    /** Effective floor: the app floor or the backend's own minimum, whichever is less deep. */
    fun effectiveFloorDb(backendMinDb: Float?): Float {
        if (backendMinDb == null || backendMinDb.isNaN()) return APP_FLOOR_DB
        return maxOf(APP_FLOOR_DB, minOf(0f, backendMinDb))
    }

    /** Clamp to [floorDb, 0] and quantize to [DB_STEP]. Never positive (no boost). */
    fun clampAttenuation(db: Float, floorDb: Float): Float {
        if (db.isNaN()) return 0f
        val f = minOf(0f, floorDb)
        val q = (db.coerceIn(f, 0f) / DB_STEP).roundToInt() * DB_STEP
        val r = q.coerceIn(f, 0f)
        return if (r == -0f) 0f else r
    }

    /** Portion of [deltaDegrees] allowed before hitting 0 dB or [floorDb]. */
    fun clampRotationDegrees(deltaDegrees: Float, currentDb: Float, floorDb: Float): Float {
        if (deltaDegrees == 0f) return 0f
        val perDegree = dbDeltaFromDegrees(1f)
        val cur = currentDb.coerceIn(minOf(0f, floorDb), 0f)
        return if (deltaDegrees > 0f) {
            val room = 0f - cur
            if (room <= 1e-4f) 0f else deltaDegrees.coerceAtMost(room / perDegree)
        } else {
            val room = cur - minOf(0f, floorDb)
            if (room <= 1e-4f) 0f else deltaDegrees.coerceAtLeast(-room / perDegree)
        }
    }

    /** Number of whole-dB detents crossed moving from [fromDb] to [toDb]. */
    fun detentsCrossed(fromDb: Float, toDb: Float): Int {
        val a = floor(fromDb / DETENT_DB + 1e-4f).toInt()
        val b = floor(toDb / DETENT_DB + 1e-4f).toInt()
        return abs(b - a)
    }

    /** Knob angle that follows the requested value continuously. */
    fun knobDegrees(db: Float): Float {
        val raw = db / DB_PER_FULL_TURN * FULL_TURN_DEGREES
        val m = raw % FULL_TURN_DEGREES
        return if (m < 0f) m + FULL_TURN_DEGREES else m
    }

    /** Ring sweep: full at 0 dB, empty at the floor. */
    fun ringSweepDegrees(db: Float, floorDb: Float): Float {
        val f = minOf(-1e-3f, floorDb)
        return ((1f - db.coerceIn(f, 0f) / f) * FULL_TURN_DEGREES).coerceIn(0f, FULL_TURN_DEGREES)
    }

    /** "−6.0 dB", "0.0 dB". Uses a real minus sign. */
    fun formatDb(db: Float): String {
        val tenths = (db * 10f).roundToInt()
        if (tenths == 0) return "0.0 dB"
        val sign = if (tenths < 0) "\u2212" else "+"
        val a = abs(tenths)
        return "$sign${a / 10}.${a % 10} dB"
    }

    /**
     * Equalizer band level in millibels for [db], limited to the device's
     * reported band range and never above 0 mB (no boost).
     */
    fun eqBandLevelMillibels(db: Float, rangeMinMb: Short, rangeMaxMb: Short): Short {
        val lo = minOf(rangeMinMb.toInt(), rangeMaxMb.toInt())
        val hi = minOf(0, maxOf(rangeMinMb.toInt(), rangeMaxMb.toInt()))
        val want = (minOf(0f, db) * 100f).roundToInt()
        return want.coerceIn(lo, maxOf(lo, hi)).toShort()
    }
}
