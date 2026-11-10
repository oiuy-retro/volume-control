package com.oiuy.volumecontrol.buttons

/**
 * When the service asks Android for key events at all.
 *
 * On the Galaxy S24+ (One UI, Android 17) an accessibility service that merely has key
 * filtering on breaks screen-off volume buttons, whatever it answers. So the service keeps
 * FLAG_REQUEST_FILTER_KEY_EVENTS off by default and turns it on only while all of these
 * hold: the screen is interactive and fully on, the in-app switch is on, and Samsung fine
 * volume is available. Otherwise Android never routes volume keys through the service.
 */
object KeyFilterPolicy {
    fun shouldFilter(screenOn: Boolean, switchOn: Boolean, fineAvailable: Boolean): Boolean =
        screenOn && switchOn && fineAvailable
}

/**
 * Tracks and applies the key-filter state. [apply] sets or clears the flag and returns
 * whether that worked; it is called only when the wanted state differs from the last
 * applied one, or after a failed attempt. Starts as "not filtering".
 */
class KeyFilterController(private val apply: (Boolean) -> Boolean) {
    private var applied: Boolean? = null

    /** True only while filtering is on and was applied successfully. */
    var filtering: Boolean = false
        private set

    /** Called after [filtering] changes. */
    var onFilteringChanged: ((Boolean) -> Unit)? = null

    /** Re-evaluate from the live conditions. Returns true if [apply] was called. */
    fun update(screenOn: Boolean, switchOn: Boolean, fineAvailable: Boolean): Boolean =
        set(KeyFilterPolicy.shouldFilter(screenOn, switchOn, fineAvailable))

    /** Turn filtering off regardless of conditions (service unbinding). */
    fun forceOff(): Boolean = set(false)

    private fun set(want: Boolean): Boolean {
        if (applied == want) return false
        val ok = try {
            apply(want)
        } catch (_: RuntimeException) {
            false
        }
        // A failed attempt is retried on the next update.
        applied = if (ok) want else null
        val now = want && ok
        if (now != filtering) {
            filtering = now
            onFilteringChanged?.invoke(now)
        }
        return true
    }
}
