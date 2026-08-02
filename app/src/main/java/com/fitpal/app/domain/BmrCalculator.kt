package com.fitpal.app.domain

import com.fitpal.app.domain.model.FitnessGoal
import com.fitpal.app.domain.model.Sex
import com.fitpal.app.domain.model.UserProfile

/**
 * Recommended daily intake based on the Mifflin-St Jeor BMR equation.
 *
 * Mifflin-St Jeor is the most widely validated BMR formula:
 *   Men:   BMR = 10 × weight(kg) + 6.25 × height(cm) − 5 × age(years) + 5
 *   Women: BMR = 10 × weight(kg) + 6.25 × height(cm) − 5 × age(years) − 161
 *
 * The raw BMR is multiplied by 1.2 (sedentary/NEAT multiplier) because BMR alone
 * only covers lying completely still — even without exercise your body burns more
 * from walking, digesting food, sitting upright, etc. The user logs exercise
 * separately, so this 1.2× is the safe baseline that avoids dangerously low targets.
 */
data class DailyTargets(
    val calories: Int,
    val proteinG: Int,
    val fatG: Int,
    val carbsG: Int,
    val fiberG: Int
)

object BmrCalculator {

    /**
     * Calculate BMR using Mifflin-St Jeor (no activity multiplier).
     * @return kcal/day at complete rest.
     */
    fun bmr(profile: UserProfile, weightKg: Float): Float {
        val base = 10f * weightKg + 6.25f * profile.heightCm - 5f * profile.ageYears
        return when (profile.sex) {
            Sex.MALE -> base + 5f
            Sex.FEMALE -> base - 161f
        }
    }

    /**
     * Sedentary multiplier — accounts for NEAT (non-exercise activity
     * thermogenesis): digestion, walking around the house, sitting upright, etc.
     * Exercise calories are logged separately, so we only apply the minimum 1.2×.
     */
    private const val SEDENTARY_MULTIPLIER = 1.2f

    /**
     * What the body burns in a day with no logged exercise: BMR plus everyday living
     * (NEAT). This is the exact baseline the calorie target is built on, so anything that
     * shows "natural burn" to the user must use this and not raw [bmr] — otherwise the
     * screens quietly disagree with the goal.
     */
    fun restingBurn(profile: UserProfile, weightKg: Float): Float =
        bmr(profile, weightKg) * SEDENTARY_MULTIPLIER

    /**
     * Full daily targets adjusted for the user's goal. The calorie target is
     * derived from BMR × 1.2 (sedentary baseline) and then offset by the goal.
     * Macro split follows evidence-based ranges per goal.
     */
    fun dailyTargets(profile: UserProfile, weightKg: Float): DailyTargets {
        val rawBmr = bmr(profile, weightKg) * SEDENTARY_MULTIPLIER

        val (calorieOffset, proteinPerKg, fatPerKg) = when (profile.fitnessGoal) {
            // Aggressive deficit, very high protein to preserve muscle
            FitnessGoal.LOSE_FAT -> Triple(-500f, 2.0f, 0.8f)
            // Slight deficit, highest protein for simultaneous muscle gain
            FitnessGoal.RECOMP -> Triple(-300f, 2.2f, 0.9f)
            // Moderate surplus, high protein + high carbs for growth
            FitnessGoal.BUILD_MUSCLE -> Triple(+300f, 1.8f, 1.0f)
            // At maintenance
            FitnessGoal.MAINTAIN -> Triple(0f, 1.6f, 0.9f)
        }

        val calories = (rawBmr + calorieOffset).toInt().coerceAtLeast(1200)
        val proteinG = (proteinPerKg * weightKg).toInt()
        val fatG = (fatPerKg * weightKg).toInt()
        // Remaining calories → carbs (4 kcal/g protein, 9 kcal/g fat, 4 kcal/g carbs)
        val proteinCal = proteinG * 4
        val fatCal = fatG * 9
        val carbsG = ((calories - proteinCal - fatCal) / 4).coerceAtLeast(50)
        val fiberG = if (profile.sex == Sex.MALE) 30 else 25

        return DailyTargets(
            calories = calories,
            proteinG = proteinG,
            fatG = fatG,
            carbsG = carbsG,
            fiberG = fiberG
        )
    }

    /**
     * Daily targets with the user's manual calorie override and per-macro target presets applied.
     * A macro left on "Auto" keeps the goal-derived value; an explicit preset replaces it. Carbs on
     * Auto are recomputed as the calories left after the (possibly overridden) protein and fat, so
     * the plate still balances.
     */
    fun dailyTargets(
        profile: UserProfile,
        weightKg: Float,
        calorieOverride: Int,
        macros: MacroSelection
    ): DailyTargets {
        val base = dailyTargets(profile, weightKg)
        val calories = if (calorieOverride > 0) calorieOverride else base.calories
        val proteinG = MacroPlan.optionFor(Macro.PROTEIN, macros.protein).grams?.invoke(weightKg, calories) ?: base.proteinG
        val fatG = MacroPlan.optionFor(Macro.FAT, macros.fat).grams?.invoke(weightKg, calories) ?: base.fatG
        val carbsG = MacroPlan.optionFor(Macro.CARBS, macros.carbs).grams?.invoke(weightKg, calories)
            ?: ((calories - proteinG * 4 - fatG * 9) / 4).coerceAtLeast(50)
        val fiberG = MacroPlan.optionFor(Macro.FIBER, macros.fiber).grams?.invoke(weightKg, calories) ?: base.fiberG
        return DailyTargets(calories, proteinG, fatG, carbsG, fiberG)
    }
}
