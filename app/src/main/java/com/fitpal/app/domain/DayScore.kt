package com.fitpal.app.domain

import kotlin.math.roundToInt

/**
 * A 0–100 "how balanced was today" score. It drives the COLOUR of the Home
 * calorie ring — the ring itself still shows calories; the colour signals quality:
 *   gold = good (80–100), green = fair (60–79), blue = low (40–59), red = poor (<40).
 *
 * Blend: calories on-target 40% + macro adherence 35% + micronutrient coverage 25%.
 *  - Calories and "stay-under" macros (fat, carbs) are penalised for going over.
 *  - "Reach" macros (protein, fiber) simply reward getting closer to the goal.
 *  - If no micronutrient data is available, its weight is split between the other two.
 */
object DayScore {

    enum class Tier(val label: String) { GOOD("Good"), FAIR("Fair"), LOW("Low"), POOR("Poor") }

    data class Result(val score: Int, val tier: Tier)

    fun tierOf(score: Int): Tier = when {
        score >= 80 -> Tier.GOOD
        score >= 60 -> Tier.FAIR
        score >= 40 -> Tier.LOW
        else -> Tier.POOR
    }

    fun compute(
        calories: Float,
        calorieGoal: Int,
        protein: Float, proteinTarget: Float,
        fat: Float, fatTarget: Float,
        carbs: Float, carbTarget: Float,
        fiber: Float, fiberTarget: Float,
        microCoverage: Float = -1f   // 0..1, or < 0 if unknown
    ): Result {
        val cal = caloriesScore(calories, calorieGoal)
        val macro = macroScore(protein, proteinTarget, fat, fatTarget, carbs, carbTarget, fiber, fiberTarget)
        val blended = if (microCoverage < 0f) {
            cal * 0.52f + macro * 0.48f
        } else {
            cal * 0.40f + macro * 0.35f + microCoverage.coerceIn(0f, 1f) * 100f * 0.25f
        }
        val s = blended.roundToInt().coerceIn(0, 100)
        return Result(s, tierOf(s))
    }

    private fun caloriesScore(calories: Float, goal: Int): Float {
        if (goal <= 0) return 70f          // no goal set — neutral
        if (calories <= 0f) return 70f     // nothing logged yet — don't punish an empty morning
        val ratio = calories / goal
        // Being within budget is good (the ring's LENGTH shows progress to the goal);
        // only going OVER pulls the score — and the ring's colour — down.
        return if (ratio <= 1.0f) 100f else (100f - (ratio - 1f) * 200f).coerceIn(0f, 100f)
    }

    /** "Reach" goal: more is fine, capped at the target. */
    private fun reachScore(actual: Float, target: Float): Float {
        if (target <= 0f) return 70f
        return (actual / target * 100f).coerceIn(0f, 100f)
    }

    /** "Stay-under" limit: full marks while under target, penalised once over. */
    private fun capScore(actual: Float, target: Float): Float {
        if (target <= 0f) return 70f
        return if (actual <= target) 100f
        else (100f - (actual - target) / target * 120f).coerceIn(0f, 100f)
    }

    private fun macroScore(
        protein: Float, pt: Float, fat: Float, ft: Float,
        carbs: Float, ct: Float, fiber: Float, fit: Float
    ): Float {
        val p = reachScore(protein, pt)
        val fib = reachScore(fiber, fit)
        val f = capScore(fat, ft)
        val c = capScore(carbs, ct)
        return (p + fib + f + c) / 4f
    }
}
