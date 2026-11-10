package com.oiuy.volumecontrol.volume

/**
 * Coalesces volume writes to the latest target. At most one write per
 * [minIntervalMs]; targets that arrive sooner replace a single pending value
 * (no queue). A target equal to the last written value is not written again.
 */
class CoalescingWriter(
    private val now: () -> Long,
    private val minIntervalMs: Long,
    private val scheduler: Scheduler,
    private val sink: (Int) -> Unit,
) {
    /** Single-slot timer. [schedule] is only called when nothing is scheduled. */
    interface Scheduler {
        fun schedule(delayMs: Long, action: () -> Unit)
        fun cancel()
    }

    private var lastWritten: Int? = null
    private var lastWriteAt: Long = 0L
    private var hasWritten = false
    private var pending: Int? = null
    private var scheduled = false

    fun submit(target: Int) {
        if (!scheduled && target == lastWritten) return
        val t = now()
        val elapsed = t - lastWriteAt
        if (!scheduled && (!hasWritten || elapsed >= minIntervalMs)) {
            write(target)
            return
        }
        pending = target
        if (!scheduled) {
            scheduled = true
            scheduler.schedule((minIntervalMs - elapsed).coerceAtLeast(0L)) { onTimer() }
        }
    }

    /** Write any pending target now (e.g. at the end of a gesture). */
    fun flush() {
        if (scheduled) {
            scheduler.cancel()
            scheduled = false
        }
        val p = pending
        pending = null
        if (p != null && p != lastWritten) write(p)
    }

    /** Drop any pending target without writing and record [known] as the current value. */
    fun reset(known: Int?) {
        if (scheduled) {
            scheduler.cancel()
            scheduled = false
        }
        pending = null
        lastWritten = known
    }

    private fun onTimer() {
        scheduled = false
        val p = pending ?: return
        pending = null
        if (p != lastWritten) write(p)
    }

    private fun write(value: Int) {
        lastWritten = value
        lastWriteAt = now()
        hasWritten = true
        sink(value)
    }
}
