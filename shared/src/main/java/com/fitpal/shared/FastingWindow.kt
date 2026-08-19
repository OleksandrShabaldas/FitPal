package com.fitpal.shared

/**
 * Pure intermittent-fasting window math, shared by the phone app and the watch tile — both compute
 * the current phase from their own clock, so nothing time-varying (a live countdown) ever has to be
 * sent over the wire; only the window itself does. The eating window is `[eatStartMin, eatEndMin)`
 * in minutes since midnight and may wrap past midnight (e.g. a late eater on 18:00–02:00).
 */
object FastingWindow {
    const val DAY = 24 * 60

    fun isEating(eatStartMin: Int, eatEndMin: Int, nowMin: Int): Boolean {
        val n = nowMin.mod(DAY)
        return when {
            eatStartMin == eatEndMin -> false                     // zero-length window → always fasting
            eatStartMin < eatEndMin -> n >= eatStartMin && n < eatEndMin
            else -> n >= eatStartMin || n < eatEndMin              // window wraps past midnight
        }
    }

    /** Length of the eating window in minutes (handles the midnight wrap). */
    fun eatingLengthMin(eatStartMin: Int, eatEndMin: Int): Int = (eatEndMin - eatStartMin).mod(DAY)

    /** Current phase + how long is left in it + when it next flips, evaluated at [nowMin]. */
    fun stateAt(eatStartMin: Int, eatEndMin: Int, nowMin: Int): FastingWindowState {
        val n = nowMin.mod(DAY)
        val eating = isEating(eatStartMin, eatEndMin, n)
        val boundary = if (eating) eatEndMin else eatStartMin
        // Half-open windows keep this in (0, phaseLength]; guard the exact-boundary instant.
        val left = (boundary - n).mod(DAY).let { if (it == 0) DAY else it }
        val eatLen = eatingLengthMin(eatStartMin, eatEndMin)
        val phaseLen = if (eating) eatLen else DAY - eatLen
        val progress = if (phaseLen > 0) ((phaseLen - left).toFloat() / phaseLen).coerceIn(0f, 1f) else 0f
        return FastingWindowState(eating, left, boundary.mod(DAY), progress)
    }
}

/** A snapshot of where the clock sits in the fasting cycle. [progressFraction] runs 0→1 as the phase completes. */
data class FastingWindowState(
    val isEating: Boolean,
    val minutesLeftInPhase: Int,
    val nextChangeMin: Int,
    val progressFraction: Float
)
