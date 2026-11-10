package com.oiuy.volumecontrol.diagnostic

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Static guard for the diagnostic build: no ordinary path may write volume.
 * Unit tests run with the app module as the working directory.
 */
class DiagnosticSourceGuardTest {
    private val root = File("src/main/java/com/oiuy/volumecontrol")
    private val sources: Map<String, String> by lazy {
        assertTrue("source root not found: ${root.absolutePath}", root.isDirectory)
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .associate { it.relativeTo(root).invariantSeparatorsPath to it.readText() }
    }

    private fun filesMatching(regex: Regex) = sources.filterValues { regex.containsMatchIn(it) }.keys.sorted()

    @Test
    fun legacyCoarseWritesAreUnreachable() {
        val coarse = Regex("""\b(setStreamVolume|adjustStreamVolume|adjustVolume|adjustSuggestedStreamVolume|setStreamMute|setRingerMode)\b""")
        assertEquals(listOf("volume/MediaVolumeController.kt"), filesMatching(coarse))
        assertEquals(listOf("volume/MediaVolumeController.kt"), filesMatching(Regex("""\bMediaVolumeController\b""")))
    }

    @Test
    fun fineSetterIsOnlyNamedInTheAdapterAndOnlyCalledFromTheTest() {
        assertEquals(listOf("diagnostic/AndroidPorts.kt"), filesMatching(Regex("semSetFineVolume")))
        assertEquals(listOf("diagnostic/AndroidPorts.kt"), filesMatching(Regex("""\bgetMethod\(""")))
        assertEquals(2, Regex("""\bgetMethod\(""").findAll(sources.getValue("diagnostic/AndroidPorts.kt")).count())
        assertEquals(
            listOf("diagnostic/AndroidPorts.kt", "diagnostic/FineVolumeDiagnostic.kt"),
            filesMatching(Regex("""\bstepTo\(""")),
        )
        assertEquals(1, Regex("""\.stepTo\(""").findAll(sources.getValue("diagnostic/FineVolumeDiagnostic.kt")).count())
        assertEquals(
            listOf("diagnostic/DiagnosticViewModel.kt", "diagnostic/FineVolumeDiagnostic.kt"),
            filesMatching(Regex("""\btestOneFineStepQuieter\b""")),
        )
        assertEquals(listOf("MainActivity.kt", "diagnostic/DiagnosticViewModel.kt"), filesMatching(Regex("""\bonTestTapped\b""")))
    }

    @Test
    fun noRestrictedAccessTechniques() {
        val forbidden = Regex(
            """setAccessible|isAccessible\s*=|getDeclaredMethod|getDeclaredField|Class\.forName|FLAG_FINE_VOLUME|\bsetFineVolume\b|\bgetFineVolume\b|IAudioService|ServiceManager|VMRuntime|HiddenApiBypass|setHiddenApiExemptions|Shizuku|\bsu\b""",
        )
        assertEquals(emptyList<String>(), filesMatching(forbidden))
    }
}
