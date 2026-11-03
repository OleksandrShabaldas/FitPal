package com.fitpal.app.domain

import java.util.Locale
import kotlin.math.roundToInt

/**
 * The numbers the meal coach gets for one just-logged meal (`InsightsWorker`), worked out here so the
 * model never does the sums. It used to get "what's left" with the meal already taken off, labelled
 * as if it were before the meal, so it compared the meal with it a second time: an 856 kcal dinner
 * with 1,430 kcal to spare read as "exceeds your remaining budget". Now every figure says whether
 * it's before or after this meal, the budget is Home's (base + the day's steps and workouts), and
 * the verdict is stated as a fact the prompt tells the model not to redo.
 */
object MealBudgetCheck {

    data class Totals(val kcal: Float, val protein: Float, val fat: Float, val carbs: Float, val fiber: Float) {
        operator fun plus(o: Totals) = Totals(kcal + o.kcal, protein + o.protein, fat + o.fat, carbs + o.carbs, fiber + o.fiber)
    }

    /** The day's targets besides calories, in grams. */
    data class MacroTargets(val protein: Int, val fat: Int, val carbs: Int, val fiber: Int)

    enum class Verdict { FITS, AT_BUDGET, OVER, ALREADY_OVER }

    /** Up to this share over the budget the day is "about at its budget", not over — as in the overviews. */
    const val AT_BUDGET_SHARE = 0.05f

    fun verdict(budget: Int, eatenBefore: Float, meal: Float): Verdict {
        val after = eatenBefore + meal
        return when {
            eatenBefore >= budget -> Verdict.ALREADY_OVER
            after <= budget -> Verdict.FITS
            after <= budget * (1 + AT_BUDGET_SHARE) -> Verdict.AT_BUDGET
            else -> Verdict.OVER
        }
    }

    /**
     * The prompt's block about the day. [baseKcal] null = no targets yet (no weigh-in): only what was
     * eaten is stated, and the coach is told there's no budget to judge the meal against.
     */
    fun factsBlock(baseKcal: Int?, activityKcal: Int, targets: MacroTargets?, before: Totals, meal: Totals): String = buildString {
        val after = before + meal
        val budget = baseKcal?.let { it + activityKcal }
        if (baseKcal == null || budget == null) appendLine("DAY BUDGET: not set yet")
        else appendLine("DAY BUDGET (as on the user's Home screen): ${kcal(budget)} = ${kcal(baseKcal)} base + ${kcal(activityKcal)} from the day's steps and workouts")
        appendLine("EATEN THAT DAY BEFORE THIS MEAL: " + if (before.kcal < 50f) "nothing substantial" else totals(before))
        appendLine("THIS MEAL: " + totals(meal))
        appendLine(
            "THE DAY INCLUDING THIS MEAL: " + if (budget == null || targets == null) totals(after) else
                "${n(after.kcal)} of ${n(budget.toFloat())} kcal · protein ${n(after.protein)} of ${targets.protein} g · " +
                    "fat ${n(after.fat)} of ${targets.fat} g · carbs ${n(after.carbs)} of ${targets.carbs} g · " +
                    "fibre ${n(after.fiber)} of ${targets.fiber} g"
        )
        append("CALORIE CHECK (worked out by the app, a fact): ")
        append(
            if (budget == null) "there's no budget to judge against, so say nothing about calories or portion size"
            else checkLine(budget, before.kcal, meal.kcal)
        )
    }

    fun checkLine(budget: Int, eatenBefore: Float, meal: Float): String {
        val left = (budget - eatenBefore - meal).roundToInt()
        return when (verdict(budget, eatenBefore, meal)) {
            Verdict.FITS -> "this meal FITS the day's budget, with ${kcal(left)} left after it"
            Verdict.AT_BUDGET -> "this meal brings the day to about its budget (${kcal(-left)} over, within 5%), not worth a warning"
            Verdict.OVER -> "this meal takes the day ${kcal(-left)} OVER budget"
            Verdict.ALREADY_OVER -> "the day was already over budget before this meal; with it, the day is ${kcal(-left)} over"
        }
    }

    // Fixed grouping (2,430), so the prompt reads the same in every phone language.
    private fun n(v: Float) = String.format(Locale.US, "%,d", v.roundToInt())
    private fun kcal(v: Int) = String.format(Locale.US, "%,d kcal", v)
    private fun totals(t: Totals) = "${n(t.kcal)} kcal · P ${n(t.protein)} g · F ${n(t.fat)} g · C ${n(t.carbs)} g · fibre ${n(t.fiber)} g"
}
