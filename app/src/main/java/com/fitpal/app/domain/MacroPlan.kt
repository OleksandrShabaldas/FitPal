package com.fitpal.app.domain

import com.fitpal.app.domain.model.FitnessGoal

/** The four macro targets the user can tune. */
enum class Macro { PROTEIN, FAT, CARBS, FIBER }

/**
 * One selectable target preset for a macro. [grams] computes the gram goal from body weight and the
 * calorie target; a null [grams] means "Auto" — let the app derive it from goal + weight as before.
 */
data class MacroOption(
    val key: String,
    val label: String,
    val grams: ((weightKg: Float, calories: Int) -> Int)?
)

/** The user's chosen preset key for each macro ("auto" = derive automatically). */
data class MacroSelection(
    val protein: String = "auto",
    val fat: String = "auto",
    val carbs: String = "auto",
    val fiber: String = "auto"
) {
    fun keyFor(macro: Macro): String = when (macro) {
        Macro.PROTEIN -> protein
        Macro.FAT -> fat
        Macro.CARBS -> carbs
        Macro.FIBER -> fiber
    }
}

/**
 * Sensible per-macro target presets, chosen so each macro uses the units that actually generalise:
 * protein / fat / carbs scale with body weight (g per kg), while fibre is an absolute daily amount
 * (it doesn't scale with body size). Each macro also has an "Auto" option, and a recommended preset
 * for the user's goal that the UI highlights.
 */
object MacroPlan {

    private val AUTO = MacroOption("auto", "Auto", null)
    private fun perKg(g: Float) = MacroOption(g.toString(), "${trim(g)} g/kg") { w, _ -> (g * w).toInt() }
    private fun grams(g: Int) = MacroOption("abs_$g", "$g g") { _, _ -> g }
    private fun trim(g: Float): String = if (g == g.toInt().toFloat()) g.toInt().toString() else g.toString()

    val proteinOptions = listOf(perKg(0.8f), perKg(1.2f), perKg(1.6f), perKg(1.8f), perKg(2.0f), perKg(2.2f), AUTO)
    val fatOptions = listOf(perKg(0.6f), perKg(0.8f), perKg(1.0f), perKg(1.2f), AUTO)
    val carbsOptions = listOf(AUTO, perKg(2f), perKg(3f), perKg(4f), perKg(5f))
    val fiberOptions = listOf(grams(25), grams(30), grams(38), AUTO)

    fun optionsFor(macro: Macro): List<MacroOption> = when (macro) {
        Macro.PROTEIN -> proteinOptions
        Macro.FAT -> fatOptions
        Macro.CARBS -> carbsOptions
        Macro.FIBER -> fiberOptions
    }

    /** The chosen option for a macro, falling back to Auto if the stored key is unknown. */
    fun optionFor(macro: Macro, key: String): MacroOption =
        optionsFor(macro).firstOrNull { it.key == key } ?: optionsFor(macro).first { it.grams == null }

    /** The preset the UI highlights as recommended for the user's goal. */
    fun recommendedKey(macro: Macro, goal: FitnessGoal): String = when (macro) {
        Macro.PROTEIN -> when (goal) {
            FitnessGoal.LOSE_FAT -> "2.0"
            FitnessGoal.RECOMP -> "2.2"
            FitnessGoal.BUILD_MUSCLE -> "1.8"
            FitnessGoal.MAINTAIN -> "1.6"
        }
        Macro.FAT -> if (goal == FitnessGoal.BUILD_MUSCLE) "1.0" else "0.8"
        Macro.CARBS -> "auto"
        Macro.FIBER -> "abs_30"
    }
}
