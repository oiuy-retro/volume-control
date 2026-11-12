package com.oiuy.volumecontrol.buttons

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Static guard: the accessibility service asks only for volume key events. */
class VolumeButtonServiceConfigTest {
    private val config = File("src/main/res/xml/volume_button_service.xml").readText()
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test fun filtersKeyEventsOnly() {
        assertTrue(config.contains("""android:canRequestFilterKeyEvents="true""""))
        // Filtering is turned on only at runtime; the service starts without it.
        assertFalse(Regex("""accessibilityFlags="[^"]*flagRequestFilterKeyEvents""").containsMatchIn(config))
        assertTrue(config.contains("""android:canRetrieveWindowContent="false""""))
        assertFalse("no event types requested", config.contains("android:accessibilityEventTypes"))
        assertFalse(config.contains("canPerformGestures"))
        assertFalse(config.contains("canTakeScreenshot"))
        assertFalse(config.contains("flagRetrieveInteractiveWindows"))
    }

    @Test fun serviceTogglesFilterAtRuntimeAndHasNoOwnVolumeStep() {
        val src = File("src/main/java/com/oiuy/volumecontrol/buttons/VolumeButtonService.kt").readText()
        assertTrue(src.contains("FLAG_REQUEST_FILTER_KEY_EVENTS"))
        assertTrue(src.contains("ACTION_SCREEN_OFF"))
        assertTrue(src.contains("ACTION_SCREEN_ON"))
        assertTrue(src.contains("ACTION_USER_PRESENT"))
        assertFalse(Regex("""adjustStreamVolume|adjustSuggestedStreamVolume|setStreamVolume""").containsMatchIn(src))
    }

    @Test fun serviceIsBoundOnlyBySystem() {
        assertTrue(manifest.contains("android.permission.BIND_ACCESSIBILITY_SERVICE"))
        assertTrue(manifest.contains("@xml/volume_button_service"))
    }

    @Test fun handlerUsesOnlyVolumeKeys() {
        val src = File("src/main/java/com/oiuy/volumecontrol/buttons/VolumeKeyHandler.kt").readText()
        val keycodes = Regex("""KEYCODE_[A-Z_]+""").findAll(src).map { it.value }.toSet()
        assertTrue(keycodes == setOf("KEYCODE_VOLUME_UP", "KEYCODE_VOLUME_DOWN"))
    }
}
