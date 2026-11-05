package com.oiuy.volumecontrol.volume

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeStream(
    override var maxIndex: Int,
    var level: Int,
    /** Optional cap the "system" enforces, like a hearing-safety limit. */
    var cap: Int = Int.MAX_VALUE,
) : StreamVolumePort {
    val sets = mutableListOf<Int>()
    override val currentIndex: Int get() = level
    override fun setIndex(index: Int) {
        sets += index
        level = index.coerceIn(0, minOf(maxIndex, cap))
    }
}

class LevelTrackerTest {
    /** Rotate in small 3-degree frames, collecting changes. */
    private fun turn(t: LevelTracker, degrees: Float, frame: Float = 3f): List<LevelChange> {
        val out = mutableListOf<LevelChange>()
        var left = degrees
        val step = if (degrees >= 0) frame else -frame
        while (kotlin.math.abs(left) > 1e-4f) {
            val d = if (kotlin.math.abs(left) < frame) left else step
            val applied = t.clampRotation(d)
            if (applied != 0f) out += t.rotate(applied)
            left -= d
        }
        return out
    }

    @Test
    fun smallDragDoesNotChangeLevelOrSetStream() {
        val s = FakeStream(15, 7)
        val t = LevelTracker(s)
        val changes = turn(t, 90f) // 0.375 level, below 0.5 + h
        assertTrue(changes.none { it.changed })
        assertTrue(s.sets.isEmpty())
        assertEquals(7, s.level)
    }

    @Test
    fun realTransitionsUpThenDownForMax15() {
        val s = FakeStream(15, 7)
        val t = LevelTracker(s)
        val up = turn(t, 360f * 2) // +3 levels of travel
        assertEquals(listOf(8, 9, 10), up.filter { it.changed }.map { it.toIndex })
        assertTrue(up.filter { it.changed }.all { it.steps == 1 })
        val down = turn(t, -360f * 2)
        assertEquals(listOf(9, 8, 7), down.filter { it.changed }.map { it.toIndex })
        assertTrue(down.filter { it.changed }.all { it.steps == -1 })
        assertEquals(7, s.level)
    }

    @Test
    fun wobbleAtBoundaryDoesNotFlapLevel() {
        val s = FakeStream(15, 7)
        val t = LevelTracker(s)
        // Just past the up threshold.
        val h = DialMath.levelHysteresis(15)
        val degToStep = (0.5f + h + 0.01f) / DialMath.levelDeltaFromDegrees(1f, 15)
        turn(t, degToStep)
        assertEquals(8, s.level)
        val setsBefore = s.sets.size
        repeat(10) {
            turn(t, -20f)
            turn(t, 20f)
        }
        assertEquals(8, s.level)
        assertEquals(setsBefore, s.sets.size)
    }

    @Test
    fun hardStopsAtZeroAndMax() {
        val s = FakeStream(15, 1)
        val t = LevelTracker(s)
        turn(t, -720f)
        assertEquals(0, s.level)
        assertEquals(0f, t.clampRotation(-10f), 0f)
        val s2 = FakeStream(15, 14)
        val t2 = LevelTracker(s2)
        turn(t2, 720f)
        assertEquals(15, s2.level)
        assertEquals(0f, t2.clampRotation(10f), 0f)
    }

    @Test
    fun largeMaxStepsMoreLevelsPerTurnButOneAtATimeInSmallFrames() {
        val s = FakeStream(150, 50)
        val t = LevelTracker(s)
        val changes = turn(t, 360f).filter { it.changed }
        assertEquals(65, s.level)
        assertEquals(15, changes.size)
    }

    @Test
    fun maxOfOneAndZeroAreSafe() {
        val s = FakeStream(1, 0)
        val t = LevelTracker(s)
        turn(t, 3600f)
        assertEquals(1, s.level)
        val z = FakeStream(0, 0)
        val tz = LevelTracker(z)
        assertFalse(tz.rotate(90f).changed)
        assertEquals(0f, tz.clampRotation(90f), 0f)
    }

    @Test
    fun readsBackClampedLevelAndDoesNotBuildHiddenTravel() {
        val s = FakeStream(15, 9, cap = 10)
        val t = LevelTracker(s)
        val changes = turn(t, 360f * 4).filter { it.changed }
        assertEquals(listOf(10), changes.map { it.toIndex })
        assertEquals(10, s.level)
        // Refused level acts as a stop for this gesture: no hidden build-up,
        // only one retry, and lowering reacts after a normal half step.
        assertEquals(10f, t.targetForTest, 1e-4f)
        assertEquals(2, s.sets.size)
        assertEquals(0f, t.clampRotation(10f), 0f)
        val down = turn(t, -160f).filter { it.changed }
        assertEquals(listOf(9), down.map { it.toIndex })
    }

    @Test
    fun refusedLevelIsRetriedOnNextGesture() {
        val s = FakeStream(15, 10, cap = 10)
        val t = LevelTracker(s)
        turn(t, 360f)
        assertEquals(10, s.level)
        s.cap = Int.MAX_VALUE // e.g. user accepted the safety warning
        t.beginGesture()
        val up = turn(t, 360f).filter { it.changed }
        assertEquals(listOf(11), up.map { it.toIndex })
    }

    @Test
    fun outsideChangeReanchorsWithoutCountingAsDialStep() {
        val s = FakeStream(15, 7)
        val t = LevelTracker(s)
        turn(t, 60f) // some hidden travel, no change
        s.level = 12 // hardware key / other app
        val r = t.rotate(3f)
        assertEquals(12, r.fromIndex)
        assertEquals(12, r.toIndex)
        assertFalse(r.changed)
        val up = turn(t, 360f).filter { it.changed }
        assertEquals(listOf(13), up.map { it.toIndex })
    }

    @Test
    fun widgetStepsWholeLevelsAndClamps() {
        val s = FakeStream(15, 7)
        val t = LevelTracker(s)
        assertEquals(9, t.stepByWidget(+1))
        assertEquals(7, t.stepByWidget(-1))
        s.level = 14
        assertEquals(15, t.stepByWidget(+1))
        s.level = 1
        assertEquals(0, t.stepByWidget(-1))
        val capped = FakeStream(15, 9, cap = 10)
        assertEquals(10, LevelTracker(capped).stepByWidget(+1))
    }
}
