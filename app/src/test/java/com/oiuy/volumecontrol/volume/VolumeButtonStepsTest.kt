package com.oiuy.volumecontrol.volume

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeButtonStepsTest {
    @Test fun fineMaxIsPublicMaxTimesTen() {
        assertEquals(150, VolumeButtonSteps.fineMax(15))
        assertEquals(250, VolumeButtonSteps.fineMax(25))
        assertEquals(0, VolumeButtonSteps.fineMax(-1))
    }

    @Test fun stepIsClampedToOneThroughFifteen() {
        assertEquals(1, VolumeButtonSteps.clampStep(0))
        assertEquals(1, VolumeButtonSteps.clampStep(-5))
        assertEquals(7, VolumeButtonSteps.clampStep(7))
        assertEquals(15, VolumeButtonSteps.clampStep(16))
        assertEquals(1, VolumeButtonSteps.DEFAULT_STEP)
    }

    @Test fun upAndDownMoveByStep() {
        assertEquals(71, VolumeButtonSteps.target(70, 1, 1, 150))
        assertEquals(69, VolumeButtonSteps.target(70, -1, 1, 150))
        assertEquals(85, VolumeButtonSteps.target(70, 1, 15, 150))
        assertEquals(55, VolumeButtonSteps.target(70, -1, 15, 150))
    }

    @Test fun directionUsesSignOnly() {
        assertEquals(73, VolumeButtonSteps.target(70, 5, 3, 150))
        assertEquals(67, VolumeButtonSteps.target(70, -9, 3, 150))
        assertEquals(70, VolumeButtonSteps.target(70, 0, 3, 150))
    }

    @Test fun clampsAtBothEnds() {
        assertEquals(150, VolumeButtonSteps.target(145, 1, 10, 150))
        assertEquals(150, VolumeButtonSteps.target(150, 1, 1, 150))
        assertEquals(0, VolumeButtonSteps.target(4, -1, 10, 150))
        assertEquals(0, VolumeButtonSteps.target(0, -1, 1, 150))
    }

    @Test fun outOfRangeCurrentIsClampedFirst() {
        assertEquals(150, VolumeButtonSteps.target(400, 1, 1, 150))
        assertEquals(149, VolumeButtonSteps.target(400, -1, 1, 150))
        assertEquals(1, VolumeButtonSteps.target(-20, 1, 1, 150))
    }

    @Test fun clampUsesLiveFineMax() {
        assertEquals(250, VolumeButtonSteps.target(245, 1, 15, VolumeButtonSteps.fineMax(25)))
        assertEquals(0, VolumeButtonSteps.target(10, 1, 1, 0))
    }

    @Test fun labelUsesLiveFineMax() {
        assertEquals("1 of 150 per press", VolumeButtonSteps.stepLabel(1, 150))
        assertEquals("15 of 250 per press", VolumeButtonSteps.stepLabel(99, 250))
    }

    @Test fun dialAndWidgetWritesKeepFlagsZero() {
        val fine = FakeFine(value = 70)
        val e = VolumeEngine(true, FakeStream(maxSteps = 15)) { fine }
        e.writeUnits(80)
        assertEquals(listOf(0), fine.writeFlags)
    }
}
