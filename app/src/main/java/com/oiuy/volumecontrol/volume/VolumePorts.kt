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

/** Which stream a plain Android volume step should move. */
enum class PlainStream {
    /** STREAM_MUSIC. */
    MEDIA,
    /** Let Android pick (call volume in a call), like a normal button press. */
    SYSTEM_DEFAULT,
}

/**
 * Android's ordinary one-step volume change, as the hardware buttons would make it.
 * Used only for screen-off volume buttons that reached the accessibility service.
 */
interface PlainVolumeKeyPort {
    /** [direction] > 0 raises, < 0 lowers. No volume panel. */
    fun step(direction: Int, stream: PlainStream): PortResult<Unit>
}
