package com.fitpal.app.domain.model

import com.fitpal.shared.FastingWindow

/**
 * An intermittent-fasting schedule: a single daily **eating window** (minutes since midnight).
 * Everything outside the window is a fast. This is deliberately *stateless* — the current phase and
 * countdown are derived from the clock via [stateAt] (the pure math lives in [FastingWindow], shared
 * with the watch), so there's no timer to start, stop, or persist. Mirrors [MealWindows].
 *
 * The eating window may cross midnight (e.g. a late eater on 18:00–02:00): when [eatEndMin] is less
 * than or equal to [eatStartMin] the window is treated as wrapping past midnight.
 */
data class FastingSchedule(
    val enabled: Boolean = false,
    val eatStartMin: Int = 12 * 60,   // 12:00
    val eatEndMin: Int = 20 * 60,     // 20:00
    val warnOnLog: Boolean = true,
    /** Post a notification when the fast begins (window closes) and ends (window opens). */
    val notify: Boolean = false
) {
    /** True if [nowMin] (minutes since midnight) is inside the eating window. */
    fun isEatingAt(nowMin: Int): Boolean = FastingWindow.isEating(eatStartMin, eatEndMin, nowMin)

    /**
     * The current [FastingState] evaluated at [nowMin] (minutes since midnight): which phase we're in,
     * how long is left in it, when it next flips, and how far through it we are.
     */
    fun stateAt(nowMin: Int): FastingState {
        val s = FastingWindow.stateAt(eatStartMin, eatEndMin, nowMin)
        return FastingState(
            phase = if (s.isEating) FastingPhase.EATING else FastingPhase.FASTING,
            minutesLeftInPhase = s.minutesLeftInPhase,
            nextChangeMin = s.nextChangeMin,
            progressFraction = s.progressFraction
        )
    }

    /** Length of the eating window in minutes (handles the midnight wrap). */
    fun eatingLengthMin(): Int = FastingWindow.eatingLengthMin(eatStartMin, eatEndMin)

    /** Length of the fasting window in minutes — the rest of the day. */
    fun fastingLengthMin(): Int = DAY - eatingLengthMin()

    companion object {
        const val DAY = 24 * 60
        val DISABLED = FastingSchedule(enabled = false)
    }
}

/** Which side of the eating window "now" falls on. */
enum class FastingPhase { FASTING, EATING }

/**
 * A snapshot of where the clock sits in the fasting cycle. [minutesLeftInPhase] counts down to the
 * next flip at [nextChangeMin]; [progressFraction] runs 0→1 as the current phase completes (so a bar
 * fills up as the fast finishes).
 */
data class FastingState(
    val phase: FastingPhase,
    val minutesLeftInPhase: Int,
    val nextChangeMin: Int,
    val progressFraction: Float
) {
    val isFasting: Boolean get() = phase == FastingPhase.FASTING
    val canEatNow: Boolean get() = phase == FastingPhase.EATING
}

/** One-tap eating-window presets offered in Settings. Labels are the familiar fast:eat ratios. */
enum class FastingPreset(val label: String, val eatStartMin: Int, val eatEndMin: Int) {
    SIXTEEN_EIGHT("16:8", 12 * 60, 20 * 60),   // eat 12:00–20:00
    EIGHTEEN_SIX("18:6", 13 * 60, 19 * 60),    // eat 13:00–19:00
    TWENTY_FOUR("20:4", 14 * 60, 18 * 60),     // eat 14:00–18:00
    OMAD("OMAD", 18 * 60, 19 * 60);            // one meal a day: eat 18:00–19:00
}

/** Whether a given day's fast was kept or broken — derived, no extra storage needed. */
enum class FastingDayResult { HELD, BROKE }

/**
 * Decide per day whether the fast was **held** or **broken**, from when meals were logged. Each meal
 * is `(dayIso, minuteOfDayItWasLogged)`. A day where anything was logged inside the fasting window is
 * BROKE; a day with meals all inside the eating window is HELD. Days with no logged food don't appear
 * (we can't tell whether you fasted or just didn't track). Returns empty when fasting is off.
 */
fun FastingSchedule.adherenceByDay(meals: List<Pair<String, Int>>): Map<String, FastingDayResult> {
    if (!enabled) return emptyMap()
    return meals.groupBy({ it.first }, { it.second }).mapValues { (_, minutes) ->
        if (minutes.any { !isEatingAt(it) }) FastingDayResult.BROKE else FastingDayResult.HELD
    }
}
