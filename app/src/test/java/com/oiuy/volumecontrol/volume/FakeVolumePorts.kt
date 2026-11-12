package com.oiuy.volumecontrol.volume

class FakeStream(var maxSteps: Int = 15, var current: Int = 7) : StreamPort {
    val writes = mutableListOf<Int>()
    var showUiCalls = 0
    var reads = 0
    override fun max(): Int = maxSteps
    override fun index(): Int {
        reads++
        return current
    }
    override fun setIndex(index: Int): PortResult<Unit> {
        writes += index
        current = index
        return PortResult.Ok(Unit)
    }
    override fun showUi() {
        showUiCalls++
    }
}

class FakeFine(
    var value: Int = 70,
    var available: Boolean = true,
    var readFailure: String? = null,
    var writeFailure: String? = null,
) : FineVolumePort {
    val writes = mutableListOf<Int>()
    val writeFlags = mutableListOf<Int>()
    var reads = 0
    override fun availability(): PortResult<Unit> =
        if (available) PortResult.Ok(Unit) else PortResult.Failed("Method not found")
    override fun readFine(): PortResult<Int> {
        reads++
        readFailure?.let { return PortResult.Failed(it) }
        return PortResult.Ok(value)
    }
    override fun stepTo(index: Int, flags: Int): PortResult<Unit> {
        writes += index
        writeFlags += flags
        writeFailure?.let { return PortResult.Failed(it) }
        value = index
        return PortResult.Ok(Unit)
    }
}

/** Manual clock + single-slot scheduler. */
class FakeTime : CoalescingWriter.Scheduler {
    var now = 1_000L
    var scheduledAt: Long? = null
    private var action: (() -> Unit)? = null
    var scheduleCalls = 0

    override fun schedule(delayMs: Long, action: () -> Unit) {
        check(this.action == null) { "single slot already used" }
        scheduleCalls++
        scheduledAt = now + delayMs
        this.action = action
    }

    override fun cancel() {
        action = null
        scheduledAt = null
    }

    fun advance(ms: Long) {
        now += ms
        val at = scheduledAt
        if (at != null && now >= at) {
            val a = action
            action = null
            scheduledAt = null
            a?.invoke()
        }
    }
}
