package com.oiuy.volumecontrol.buttons

import android.media.AudioManager
import android.view.KeyEvent
import com.oiuy.volumecontrol.volume.CoalescingWriter
import com.oiuy.volumecontrol.volume.FineVolumePort
import com.oiuy.volumecontrol.volume.PortResult
import com.oiuy.volumecontrol.volume.VolumeButtonSteps

/** What the handler needs from the phone, read fresh on every new press. */
interface VolumeKeyEnvironment {
    /** The in-app switch. */
    fun featureEnabled(): Boolean
    /** Fine units per press; clamped to 1..15 by the handler. */
    fun stepSize(): Int
    fun screenInteractive(): Boolean
    /** AudioManager.getMode(). Anything other than MODE_NORMAL means a call or ringing. */
    fun audioMode(): Int
    /** Public STREAM_MUSIC maximum. */
    fun publicMax(): Int
}

enum class PassReason { FEATURE_OFF, NO_FINE_VOLUME, SCREEN_OFF, CALL_OR_RINGING, FINE_READ_FAILED, FINE_WRITE_FAILED }

/**
 * Decides whether a volume key event is handled here (moves Samsung fine media volume
 * by the chosen step) or passed to Android unchanged.
 *
 * - Only KEYCODE_VOLUME_UP / KEYCODE_VOLUME_DOWN are considered.
 * - A press is decided on its first ACTION_DOWN. If it is passed through, its repeats and
 *   ACTION_UP are passed through too. If it is consumed, its repeats and ACTION_UP are
 *   consumed too, so Android never sees half a press.
 * - Holding the button: Android may or may not deliver key repeats to an accessibility
 *   service that consumed the first DOWN. The handler steps on every repeat it receives,
 *   and also runs its own repeat timer ([repeatTimeoutMs], then every [repeatIntervalMs]).
 *   As soon as a real repeat arrives for that key the timer stops, so a held button never
 *   steps twice per repeat.
 * - Fine writes use Samsung's FLAG_SHOW_UI so the volume panel can appear.
 */
class VolumeKeyHandler(
    private val env: VolumeKeyEnvironment,
    /** Null when the phone is not Samsung or the fine methods are missing. */
    private val fine: FineVolumePort?,
    private val scheduler: CoalescingWriter.Scheduler,
    private val repeatTimeoutMs: Long,
    private val repeatIntervalMs: Long,
) {
    private enum class Press { CONSUMED, PASSED }

    private val presses = HashMap<Int, Press>()
    private val hardwareRepeats = HashSet<Int>()
    private var timerKey: Int? = null

    /** Called after each successful fine write with the new value. */
    var onStepped: ((Int) -> Unit)? = null

    /** Called on ACTION_UP of a consumed press. */
    var onPressEnded: (() -> Unit)? = null

    /** Why a new press would be passed through right now, or null if it would be handled. */
    fun passReason(): PassReason? = when {
        !env.featureEnabled() -> PassReason.FEATURE_OFF
        fine == null || fine.availability() !is PortResult.Ok -> PassReason.NO_FINE_VOLUME
        VolumeButtonSteps.fineMax(env.publicMax()) <= 0 -> PassReason.NO_FINE_VOLUME
        !env.screenInteractive() -> PassReason.SCREEN_OFF
        env.audioMode() != AudioManager.MODE_NORMAL -> PassReason.CALL_OR_RINGING
        else -> null
    }

    /** Returns true to consume the event, false to let Android handle it. */
    fun onKeyEvent(keyCode: Int, action: Int, repeatCount: Int): Boolean {
        val direction = directionOf(keyCode) ?: return false
        return when (action) {
            KeyEvent.ACTION_DOWN -> onDown(keyCode, direction, repeatCount)
            KeyEvent.ACTION_UP -> onUp(keyCode)
            else -> presses[keyCode] == Press.CONSUMED
        }
    }

    /** Forget all presses and stop the repeat timer (service interrupted or unbound). */
    fun reset() {
        cancelTimer()
        presses.clear()
        hardwareRepeats.clear()
    }

    private fun onDown(keyCode: Int, direction: Int, repeatCount: Int): Boolean {
        val state = presses[keyCode]
        if (repeatCount > 0) {
            when (state) {
                Press.PASSED -> return false
                // Android saw the start of this press; keep it that way.
                null -> {
                    presses[keyCode] = Press.PASSED
                    return false
                }
                Press.CONSUMED -> {
                    hardwareRepeats += keyCode
                    if (timerKey == keyCode) cancelTimer()
                    // Already consumed: a failure here only skips this repeat.
                    step(direction, writeAtLimit = false)
                    return true
                }
            }
        }
        // New press (a DOWN without an UP for the last one also starts over).
        if (timerKey == keyCode) cancelTimer()
        hardwareRepeats -= keyCode
        if (passReason() != null || step(direction, writeAtLimit = true) != null) {
            presses[keyCode] = Press.PASSED
            return false
        }
        presses[keyCode] = Press.CONSUMED
        startTimer(keyCode, direction)
        return true
    }

    private fun onUp(keyCode: Int): Boolean {
        val state = presses.remove(keyCode)
        hardwareRepeats -= keyCode
        if (timerKey == keyCode) cancelTimer()
        return if (state == Press.CONSUMED) {
            onPressEnded?.invoke()
            true
        } else {
            false
        }
    }

    /**
     * One step. Returns null on success, or the failure reason. At the limit the same value is
     * written again on a new press only ([writeAtLimit]), so the volume panel still appears.
     */
    private fun step(direction: Int, writeAtLimit: Boolean): PassReason? {
        val port = fine ?: return PassReason.NO_FINE_VOLUME
        val fineMax = VolumeButtonSteps.fineMax(env.publicMax())
        if (fineMax <= 0) return PassReason.NO_FINE_VOLUME
        val current = when (val r = port.readFine()) {
            is PortResult.Ok -> r.value.coerceIn(0, fineMax)
            is PortResult.Failed -> return PassReason.FINE_READ_FAILED
        }
        val target = VolumeButtonSteps.target(current, direction, env.stepSize(), fineMax)
        if (target == current && !writeAtLimit) return null
        return when (port.stepTo(target, VolumeButtonSteps.FLAG_SHOW_UI)) {
            is PortResult.Ok -> {
                onStepped?.invoke(target)
                null
            }
            is PortResult.Failed -> PassReason.FINE_WRITE_FAILED
        }
    }

    private fun startTimer(keyCode: Int, direction: Int) {
        cancelTimer()
        timerKey = keyCode
        scheduler.schedule(repeatTimeoutMs) { onTimer(keyCode, direction) }
    }

    private fun onTimer(keyCode: Int, direction: Int) {
        if (timerKey != keyCode) return
        timerKey = null
        if (presses[keyCode] != Press.CONSUMED || keyCode in hardwareRepeats) return
        // Stop repeating if a call starts, the screen goes off, or the switch is turned off.
        if (passReason() != null) return
        if (step(direction, writeAtLimit = false) != null) return
        timerKey = keyCode
        scheduler.schedule(repeatIntervalMs) { onTimer(keyCode, direction) }
    }

    private fun cancelTimer() {
        if (timerKey != null) {
            timerKey = null
            scheduler.cancel()
        }
    }

    companion object {
        const val KEY_UP = KeyEvent.KEYCODE_VOLUME_UP
        const val KEY_DOWN = KeyEvent.KEYCODE_VOLUME_DOWN

        fun directionOf(keyCode: Int): Int? = when (keyCode) {
            KEY_UP -> 1
            KEY_DOWN -> -1
            else -> null
        }
    }
}

/**
 * Whether [setting] (Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, colon-separated
 * flattened component names) lists [packageName]/[className]. Accepts both the full and the
 * short ".Class" form.
 */
fun isAccessibilityServiceListed(setting: String?, packageName: String, className: String): Boolean {
    if (setting.isNullOrBlank()) return false
    val shortName = if (className.startsWith("$packageName.")) className.removePrefix(packageName) else null
    return setting.split(':').any { entry ->
        val parts = entry.trim().split('/', limit = 2)
        if (parts.size != 2 || !parts[0].equals(packageName, ignoreCase = true)) return@any false
        val cls = parts[1]
        cls.equals(className, ignoreCase = true) || (shortName != null && cls.equals(shortName, ignoreCase = true))
    }
}
