package com.oiuy.volumecontrol.volume

/** Step math for the hardware volume buttons in Samsung fine units. */
object VolumeButtonSteps {
    const val MIN_STEP = 1
    const val MAX_STEP = 15
    const val DEFAULT_STEP = 1

    /** Flags argument for the Samsung fine setter; same value as AudioManager.FLAG_SHOW_UI. */
    const val FLAG_SHOW_UI = 1

    fun clampStep(step: Int): Int = step.coerceIn(MIN_STEP, MAX_STEP)

    /** Fine maximum = public media maximum x 10 (150 on a 15-step phone). */
    fun fineMax(publicMax: Int): Int = publicMax.coerceAtLeast(0) * VolumeEngine.FINE_UNITS_PER_STEP

    /**
     * New fine value after one press: [current] moved by [step] units in the sign of
     * [direction] (positive = up, negative = down), clamped to 0..[fineMax].
     */
    fun target(current: Int, direction: Int, step: Int, fineMax: Int): Int {
        val max = fineMax.coerceAtLeast(0)
        val start = current.coerceIn(0, max)
        val delta = Integer.signum(direction) * clampStep(step)
        return (start + delta).coerceIn(0, max)
    }

    /** For example "1 of 150 per press". */
    fun stepLabel(step: Int, fineMax: Int): String = "${clampStep(step)} of $fineMax per press"
}
