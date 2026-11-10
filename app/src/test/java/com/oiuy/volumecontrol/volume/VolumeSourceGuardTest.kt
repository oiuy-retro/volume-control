package com.oiuy.volumecontrol.volume

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Static guard: one place for each platform write, no restricted access techniques. */
class VolumeSourceGuardTest {
    private val root = File("src/main/java/com/oiuy/volumecontrol")
    private val sources: Map<String, String> by lazy {
        assertTrue("source root not found: ${root.absolutePath}", root.isDirectory)
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .associate { it.relativeTo(root).invariantSeparatorsPath to it.readText() }
    }

    private fun filesMatching(regex: Regex) = sources.filterValues { regex.containsMatchIn(it) }.keys.sorted()

    @Test
    fun platformWritesLiveOnlyInTheAdapter() {
        val writes = Regex("""\b(setStreamVolume|adjustStreamVolume|adjustVolume|adjustSuggestedStreamVolume|setStreamMute)\b|semSetFineVolume""")
        assertEquals(listOf("volume/AndroidVolumePorts.kt"), filesMatching(writes))
        assertEquals(listOf("volume/AndroidVolumePorts.kt"), filesMatching(Regex("""\bgetMethod\(""")))
    }

    @Test
    fun noRestrictedAccessTechniques() {
        val forbidden = Regex(
            """setAccessible|isAccessible\s*=|getDeclaredMethod|getDeclaredField|Class\.forName|FLAG_FINE_VOLUME|\bsetFineVolume\b|\bgetFineVolume\b|IAudioService|ServiceManager|VMRuntime|HiddenApiBypass|setHiddenApiExemptions|Shizuku|AudioEffect|LoudnessEnhancer|DynamicsProcessing""",
        )
        assertEquals(emptyList<String>(), filesMatching(forbidden))
    }
}
