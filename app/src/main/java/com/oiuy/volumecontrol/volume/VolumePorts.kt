package com.oiuy.volumecontrol.volume

sealed interface PortResult<out T> {
    data class Ok<T>(val value: T) : PortResult<T>
    data class Failed(val reason: String) : PortResult<Nothing>
}

/** Android's public integer media volume (STREAM_MUSIC). */
interface StreamPort {
    fun max(): Int
    fun index(): Int
    /** Public integer write. Used only when Samsung fine volume is not active. */
    fun setIndex(index: Int): PortResult<Unit>
    /** Shows the system volume panel without changing the index. */
    fun showUi()
}

/** Samsung fine media volume. Implementations must report failures instead of throwing. */
interface FineVolumePort {
    /** Whether the getter and setter were found. Must not call either. */
    fun availability(): PortResult<Unit>
    fun readFine(): PortResult<Int>
    /** Write with flags 0 (no system volume panel). Used by the dial and widget. */
    fun stepTo(index: Int): PortResult<Unit> = stepTo(index, 0)
    /** Write with Samsung's flags argument, for example [VolumeButtonSteps.FLAG_SHOW_UI]. */
    fun stepTo(index: Int, flags: Int): PortResult<Unit>
}
