package com.oiuy.volumecontrol.volume

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeLevelTextTest {
    @Test
    fun appReadout() {
        assertEquals("Level 7 of 15", VolumeLevelText.levelOf(7, 15))
        assertEquals("47% volume level", VolumeLevelText.percentLabel(7, 15))
        assertEquals("Level 0 of 25", VolumeLevelText.levelOf(0, 25))
        assertEquals("100% volume level", VolumeLevelText.percentLabel(25, 25))
        assertEquals("Level 1 of 150", VolumeLevelText.levelOf(1, 150))
    }

    @Test
    fun widgetReadout() {
        assertEquals("7/15", VolumeLevelText.widgetLevel(7, 15))
        assertEquals("47% level", VolumeLevelText.widgetPercent(7, 15))
        assertEquals("30/30", VolumeLevelText.widgetLevel(30, 30))
        assertEquals("100% level", VolumeLevelText.widgetPercent(30, 30))
        assertEquals("0/0", VolumeLevelText.widgetLevel(3, 0))
        assertEquals("0% level", VolumeLevelText.widgetPercent(3, 0))
    }

    @Test
    fun accessibilityDescription() {
        assertEquals(
            "Media volume level 7 of 15, 47 percent of the volume range",
            VolumeLevelText.contentDescription(7, 15),
        )
    }
}
