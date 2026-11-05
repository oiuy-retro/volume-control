package com.oiuy.volumecontrol.volume

/**
 * Readout strings. The percentage is the position within Android's level range
 * (index / max), not measured loudness.
 */
object VolumeLevelText {
    fun levelOf(index: Int, maxIndex: Int): String =
        "Level ${index.coerceIn(0, maxOf(0, maxIndex))} of ${maxOf(0, maxIndex)}"

    fun percentLabel(index: Int, maxIndex: Int): String =
        "${DialMath.levelPercent(index, maxIndex)}% volume level"

    /** Compact widget form, e.g. "7/15". */
    fun widgetLevel(index: Int, maxIndex: Int): String =
        "${index.coerceIn(0, maxOf(0, maxIndex))}/${maxOf(0, maxIndex)}"

    /** Compact widget percentage line, e.g. "47% level". */
    fun widgetPercent(index: Int, maxIndex: Int): String =
        "${DialMath.levelPercent(index, maxIndex)}% level"

    fun contentDescription(index: Int, maxIndex: Int): String =
        "Media volume ${levelOf(index, maxIndex).lowercase()}, " +
            "${DialMath.levelPercent(index, maxIndex)} percent of the volume range"
}
