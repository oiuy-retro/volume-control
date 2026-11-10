package com.oiuy.volumecontrol.buttons

import android.media.AudioManager
import android.view.Display
import android.view.KeyEvent
import com.oiuy.volumecontrol.volume.CoalescingWriter
import com.oiuy.volumecontrol.volume.FineVolumePort
import com.oiuy.volumecontrol.volume.PortResult
import com.oiuy.volumecontrol.volume.VolumeButtonSteps

/** What the handler needs from the phone. Everything is read live, on every key event. */
interface VolumeKeyEnvironment {
    /** The in-app switch. */
    fun featureEnabled(): Boolean
    /** Fine units per press; clamped to 1..15 by the handler. */
    fun stepSize(): Int
    /** Live screen state, see [isScreenOn]. Doze and Always On Display count as off. */
    fun screenOn(): Boolean
    /** AudioManager.getMode(). Anything other than MODE_NORMAL means a call or ringing. */
    fun audioMode(): Int
    /** Public STREAM_MUSIC maximum. */
    fun publicMax(): Int
}

/**
 * The screen counts as on only when PowerManager.isInteractive() is true and the default
 * display is STATE_ON (or STATE_VR). STATE_OFF, STATE_DOZE, STATE_DOZE_SUSPEND (Always On
 * Display) and STATE_ON_SUSPEND count as off. STATE_UNKNOWN leaves the decision to
 * isInteractive().
 */
fun isScreenOn(interactive: Boolean, displayState: Int?): Boolean {
    if (!interactive) return false
    return when (displayState) {
        null, Display.STATE_UNKNOWN, Display.STATE_ON, Display.STATE_VR -> true
        else -> false
    }
}

enum class PassReason { SCREEN_OFF, FEATURE_OFF, NO_FINE_VOLUME, CALL_OR_RINGING, FINE_READ_FAILED, FINE_WRITE_FAILED }

/**
 * Decides what happens to a volume key event that reached the accessibility service.
 *
 * Screen on: moves Samsung fine media volume by the chosen step, or passes the key to
 * Android unchanged (switch off, no fine volume, fine failure, call or ringing).
 *
 * Screen off (checked live on every event): the key is passed to Android unchanged. Normally
 * no key arrives then at all, because [KeyFilterPolicy] has the service drop key filtering
 * while the screen is off (on the S24+ the filter itself broke screen-off volume keys).
 *
 * Pairing: a press is decided on its first ACTION_DOWN and identified by the event's
 * downTime. Its repeats and its ACTION_UP (same downTime) get the same answer, so Android
 * never sees an UP without its DOWN, and a consumed DOWN never leaks its UP. A repeat or UP
 * from a press whose first DOWN was not seen here (different downTime) is passed through,
 * and a new first DOWN always starts over, so a press whose UP was lost cannot get stuck.
 *
 * Holding a button with the screen on: hardware repeats step each time. If Android sends
 * none, an app timer repeats after [repeatTimeoutMs], then every [repeatIntervalMs]. The
 * timer stops on the first hardware repeat, on [onScreenOff], or when any event or timer
 * tick sees the screen off, a call, or the switch off.
 */
class VolumeKeyHandler(
    private val env: VolumeKeyEnvironment,
    /** Null when the phone is not Samsung or the fine methods are missing. */
    private val fine: FineVolumePort?,
    private val scheduler: CoalescingWriter.Scheduler,
    private val repeatTimeoutMs: Long,
    private val repeatIntervalMs: Long,
) {
    private sealed interface Press {
        data object Passed : Press
        data object Fine : Press
    }

    private data class Tracked(val downTime: Long, val press: Press)

    private val presses = HashMap<Int, Tracked>()
    private val hardwareRepeats = HashSet<Int>()
    private var timerKey: Int? = null

    /** Called after each successful fine write with the new value. */
    var onStepped: ((Int) -> Unit)? = null

    /** Called on ACTION_UP of a consumed press. */
    var onPressEnded: (() -> Unit)? = null

    /** True while the app repeat timer is pending (for tests). */
    val repeatPending: Boolean get() = timerKey != null

    /** Why a new screen-on fine press would be passed through right now, or null if handled. */
    fun passReason(): PassReason? = when {
        !env.screenOn() -> PassReason.SCREEN_OFF
        !env.featureEnabled() -> PassReason.FEATURE_OFF
        fine == null || fine.availability() !is PortResult.Ok -> PassReason.NO_FINE_VOLUME
        VolumeButtonSteps.fineMax(env.publicMax()) <= 0 -> PassReason.NO_FINE_VOLUME
        env.audioMode() != AudioManager.MODE_NORMAL -> PassReason.CALL_OR_RINGING
        else -> null
    }

    /**
     * Returns true to consume the event, false to let Android handle it. [downTime] is
     * KeyEvent.getDownTime(), shared by a press's DOWN, repeats and UP.
     */
    fun onKeyEvent(keyCode: Int, action: Int, repeatCount: Int, downTime: Long): Boolean {
        val direction = directionOf(keyCode) ?: return false
        return when (action) {
            KeyEvent.ACTION_DOWN -> onDown(keyCode, direction, repeatCount, downTime)
            KeyEvent.ACTION_UP -> onUp(keyCode, downTime)
            else -> isConsumed(current(keyCode, downTime))
        }
    }

    /** The tracked press for this key if [downTime] matches it, else null. */
    private fun current(keyCode: Int, downTime: Long): Press? =
        presses[keyCode]?.takeIf { it.downTime == downTime }?.press

    /** Screen turned off: stop any app repeat at once. Press pairing is kept. */
    fun onScreenOff() {
        cancelTimer()
    }

    /** Forget all presses and stop the repeat timer (service interrupted or unbound). */
    fun reset() {
        cancelTimer()
        presses.clear()
        hardwareRepeats.clear()
    }

    private fun isConsumed(state: Press?) = state != null && state != Press.Passed

    private fun onDown(keyCode: Int, direction: Int, repeatCount: Int, downTime: Long): Boolean {
        val state = current(keyCode, downTime)
        if (repeatCount > 0) {
            return when (state) {
                // Android saw (or will see) the start of this press; keep it that way.
                null, Press.Passed -> {
                    if (state == null && timerKey == keyCode) cancelTimer()
                    presses[keyCode] = Tracked(downTime, Press.Passed)
                    false
                }
                Press.Fine -> {
                    hardwareRepeats += keyCode
                    if (timerKey == keyCode) cancelTimer()
                    // Screen went off mid-hold: finish the consumed press without stepping.
                    if (env.screenOn()) step(direction, writeAtLimit = false)
                    true
                }
            }
        }
        // New press. A DOWN without an UP for the last one also starts over.
        if (timerKey == keyCode) cancelTimer()
        hardwareRepeats -= keyCode
        presses.remove(keyCode)
        if (!env.screenOn()) cancelTimer()
        if (passReason() != null || step(direction, writeAtLimit = true) != null) {
            presses[keyCode] = Tracked(downTime, Press.Passed)
            return false
        }
        presses[keyCode] = Tracked(downTime, Press.Fine)
        startTimer(keyCode, direction)
        return true
    }

    private fun onUp(keyCode: Int, downTime: Long): Boolean {
        val state = current(keyCode, downTime)
        // Any UP for this key ends whatever was tracked for it.
        presses.remove(keyCode)
        hardwareRepeats -= keyCode
        if (timerKey == keyCode) cancelTimer()
        return if (isConsumed(state)) {
            onPressEnded?.invoke()
            true
        } else {
            false
        }
    }

    /**
     * One fine step. Returns null on success, or the failure reason. At the limit the same value
     * is written again on a new press only ([writeAtLimit]), so the volume panel still appears.
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
        if (presses[keyCode]?.press != Press.Fine || keyCode in hardwareRepeats) return
        // Stop repeating if the screen goes off, a call starts, or the switch is turned off.
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
