package com.oiuy.volumecontrol.volume

/** Minimal view of the system media stream so [LevelTracker] is unit-testable. */
interface StreamVolumePort {
    val maxIndex: Int
    val currentIndex: Int
    fun setIndex(index: Int)
}

/**
 * Result of one dial frame.
 *
 * @param fromIndex actual index before this frame (after picking up any outside change)
 * @param toIndex actual index read back from the system after this frame
 */
data class LevelChange(val fromIndex: Int, val toIndex: Int, val maxIndex: Int) {
    val steps: Int get() = toIndex - fromIndex
    val changed: Boolean get() = steps != 0
}

/**
 * Turns dial rotation into real `STREAM_MUSIC` level changes.
 *
 * A hidden fractional target accumulates finger rotation (10% of the level
 * range per full turn). When it passes a level boundary (plus a small dead
 * band) the next whole level is requested, then the actual index is read back
 * so clamps such as a hearing-safety limit are respected. Only that read-back
 * index is reported for display and haptics.
 */
class LevelTracker(private val port: StreamVolumePort) {
    private var target: Float = port.currentIndex.toFloat()
    private var lastKnownIndex: Int = port.currentIndex

    /**
     * Tighter stops learned when the system refused a level this gesture
     * (for example a hearing-safety cap). Cleared on [beginGesture] and when
     * the level changes outside the dial.
     */
    private var refusedAbove: Int? = null
    private var refusedBelow: Int? = null

    /** Hidden target, exposed for tests only. Never show this as volume. */
    internal val targetForTest: Float get() = target

    /** Re-anchor if the stream changed outside this tracker (keys, other apps). */
    fun syncFromSystem() {
        val now = port.currentIndex
        if (now != lastKnownIndex) {
            target = now.toFloat()
            lastKnownIndex = now
            clearRefusals()
        }
        target = DialMath.clampTarget(target, port.maxIndex)
    }

    /** Call at drag start so a refused level is retried on a new gesture. */
    fun beginGesture() {
        clearRefusals()
        syncFromSystem()
    }

    private fun clearRefusals() {
        refusedAbove = null
        refusedBelow = null
    }

    private fun lowerBound(): Float = refusedBelow?.toFloat() ?: 0f
    private fun upperBound(max: Int): Float = refusedAbove?.toFloat() ?: max.toFloat()

    /** Hard-stop clamp of a rotation against the hidden target. */
    fun clampRotation(deltaDegrees: Float): Float {
        syncFromSystem()
        val max = port.maxIndex
        return DialMath.clampRotationDegrees(deltaDegrees, target, max, lowerBound(), upperBound(max))
    }

    fun rotate(deltaDegrees: Float): LevelChange {
        val max = port.maxIndex
        syncFromSystem()
        val from = lastKnownIndex.coerceIn(0, maxOf(0, max))
        if (max <= 0 || deltaDegrees == 0f) return LevelChange(from, from, max)
        target = (target + DialMath.levelDeltaFromDegrees(deltaDegrees, max))
            .coerceIn(lowerBound(), upperBound(max).coerceAtLeast(lowerBound()))
        val desired = DialMath.desiredIndex(target, from, max)
        var actual = from
        if (desired != from) {
            port.setIndex(desired)
            actual = port.currentIndex
            if (actual != desired) {
                // System refused or clamped the level. Treat the real level as a
                // stop for the rest of this gesture so hidden travel cannot build up.
                target = actual.toFloat()
                if (desired > actual) refusedAbove = actual else refusedBelow = actual
            }
        }
        lastKnownIndex = actual
        return LevelChange(from, actual, max)
    }

    /** Set a whole level (widget buttons). Returns the index read back. */
    fun setIndex(index: Int): Int {
        val max = port.maxIndex
        val clamped = index.coerceIn(0, maxOf(0, max))
        if (clamped != port.currentIndex) port.setIndex(clamped)
        val actual = port.currentIndex
        target = actual.toFloat()
        lastKnownIndex = actual
        clearRefusals()
        return actual
    }

    fun stepByWidget(direction: Int): Int {
        syncFromSystem()
        val step = DialMath.widgetStepLevels(port.maxIndex)
        return setIndex(port.currentIndex + direction.coerceIn(-1, 1) * step)
    }
}
