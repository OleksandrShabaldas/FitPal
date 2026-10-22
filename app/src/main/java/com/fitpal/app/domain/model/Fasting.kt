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
    val notify: Boolean = false,
    /** How firmly the fast is held — see [FastingIntensity]. */
    val intensity: FastingIntensity = FastingIntensity.NORMAL,
    /** How Home's main card draws the timer — see [FastingStyle]. */
    val style: FastingStyle = FastingStyle.BAR,
    /** Which drinks don't break the fast — see [FastingDrinkRule]. */
    val drinkRule: FastingDrinkRule = FastingDrinkRule.CALORIE_FREE
) {
    /** Fasting is on and set to [FastingIntensity.INTENSE]: the full-screen stop + the Home reminder. */
    val isIntense: Boolean get() = enabled && intensity == FastingIntensity.INTENSE

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

    /**
     * Whether logging [intake] during the fast leaves it intact under [drinkRule]: only a log made up
     * entirely of drinks can, and only within the rule's calorie cap (ANY has none). Shared by the
     * log-time check, auto-save and the adherence history, so all three agree.
     */
    fun allowsDuringFast(intake: FastingIntake): Boolean {
        if (!intake.allDrinks) return false
        val cap = drinkRule.maxKcal ?: return drinkRule == FastingDrinkRule.ANY
        return intake.kcal <= cap
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
 * How firmly FitPal holds you to the fast (Settings → Fasting). Both keep the log-time check;
 * INTENSE adds friction *before* you start: a full-screen stop on Add food that you swipe past, and a
 * reminder that drops in each time you come to the app.
 */
enum class FastingIntensity(val label: String, val blurb: String) {
    NORMAL("Normal", "A quiet timer on Home, and a check before you log food."),
    INTENSE("Intense", "A full-screen stop on Add food you have to swipe past, and a reminder each time you open the app.")
}

/**
 * Which drinks are fine during a fast (Settings → Fasting). Calories are counted per log — a black
 * coffee is a few kcal, a latte well over a hundred — so "calorie-free" and "light" are caps on
 * the whole log. ANY lets every drink through, even a juice or a protein shake.
 */
enum class FastingDrinkRule(val label: String, val blurb: String, val maxKcal: Float?) {
    OFF("Every drink counts", "Any drink breaks your fast, just like food.", null),
    CALORIE_FREE("Calorie-free only", "Water, black coffee, plain tea and diet soda — up to 15 kcal.", 15f),
    LIGHT("Light drinks", "Up to 50 kcal — a splash of milk in your coffee, a broth.", 50f),
    ANY("Any drink", "Drinks never break your fast — even juice, a latte or a protein shake.", null);

    /** The takeover's reassurance line — what you can still have. */
    val takeoverLine: String
        get() = when (this) {
            OFF -> "Logging a workout or your weight won't break it."
            CALORIE_FREE -> "Water, black coffee and plain tea won't break it — nor will logging a workout or your weight."
            LIGHT -> "Drinks up to 50 kcal won't break it — nor will logging a workout or your weight."
            ANY -> "Drinks won't break it — nor will logging a workout or your weight."
        }
}

/**
 * What a log holds, as far as the fasting rule cares: is it only drinks, and how many kcal in all.
 * [UNKNOWN] (the default) never counts as a drink, so a caller that doesn't say behaves as before.
 */
data class FastingIntake(val allDrinks: Boolean, val kcal: Float) {
    companion object {
        val UNKNOWN = FastingIntake(allDrinks = false, kcal = Float.MAX_VALUE)

        fun of(items: List<Pair<Boolean, Float>>): FastingIntake =
            if (items.isEmpty()) UNKNOWN
            else FastingIntake(items.all { it.first }, items.sumOf { it.second.toDouble() }.toFloat())

        fun ofFoods(foods: List<DetectedFood>): FastingIntake = of(foods.map { it.isDrink to it.totalCalories })
    }
}

/** How the fasting timer is drawn on Home's main card. */
enum class FastingStyle(val label: String, val blurb: String) {
    BAR("Horizontal", "A slim bar above the calorie ring."),
    RING("Circle", "An arc around the calorie ring.")
}

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
    FOURTEEN_TEN("14:10", 10 * 60, 20 * 60),   // eat 10:00–20:00 (the gentle start)
    SIXTEEN_EIGHT("16:8", 12 * 60, 20 * 60),   // eat 12:00–20:00
    EIGHTEEN_SIX("18:6", 13 * 60, 19 * 60),    // eat 13:00–19:00
    TWENTY_FOUR("20:4", 14 * 60, 18 * 60),     // eat 14:00–18:00
    OMAD("OMAD", 18 * 60, 19 * 60);            // one meal a day: eat 18:00–19:00
}

/** Whether a given day's fast was kept or broken — derived, no extra storage needed. */
enum class FastingDayResult { HELD, BROKE }

/**
 * Decide per day whether the fast was **held** or **broken**, from when meals were eaten. Each meal
 * is `(dayIso, minuteOfDayItWasEaten)`. A day where anything was eaten inside the fasting window is
 * BROKE; a day with meals all inside the eating window is HELD. Days with no logged food don't appear
 * (we can't tell whether you fasted or just didn't track). Returns empty when fasting is off.
 *
 * [loggedDuringFastDays] are days where a meal was logged during the fast with "log anyway" (no pass,
 * no photo proof): they stay BROKE even if that meal's time was later edited into the window — the
 * log itself decided it, and a free time edit mustn't stand in for one of the monthly passes.
 * [graceDays] (a pass was used) win over both.
 */
fun FastingSchedule.adherenceByDay(
    meals: List<Pair<String, Int>>,
    graceDays: Set<String> = emptySet(),
    loggedDuringFastDays: Set<String> = emptySet()
): Map<String, FastingDayResult> {
    if (!enabled) return emptyMap()
    return meals.groupBy({ it.first }, { it.second }).mapValues { (date, minutes) ->
        when {
            // The user attested (via "I ate earlier") that they ate within the window that day.
            date in graceDays -> FastingDayResult.HELD
            date in loggedDuringFastDays -> FastingDayResult.BROKE
            minutes.any { !isEatingAt(it) } -> FastingDayResult.BROKE
            else -> FastingDayResult.HELD
        }
    }
}
