package com.fitpal.app.domain

import com.fitpal.app.domain.model.Micronutrients
import com.fitpal.app.domain.model.ScoreFactor
import kotlin.math.roundToInt

/**
 * A transparent, deterministic 1–10 health score for a meal.
 *
 * Why this exists: the LLM's score was opaque and routinely disagreed with the
 * "factors" shown underneath it. Here a single rubric produces BOTH the number
 * and the reasons, so they can never contradict each other and the breakdown is
 * always complete. The AI is still used for the qualitative stuff (energy, mood,
 * swaps, pairings) — just not for this number.
 *
 * Rubric (starts neutral at 5.5, then nudges up/down on nutrient quality):
 *  + protein density, fiber, balanced fat ratio, light calorie density, micros
 *  − low protein, low fiber for the carbs, high/very-high fat, refined carbs,
 *    calorie density, high sodium
 * Each nudge also emits a [ScoreFactor] so the user sees exactly what moved it.
 */
object HealthScorer {

    data class Result(val score: Int, val factors: List<ScoreFactor>)

    private data class Nudge(val delta: Float, val text: String) {
        val positive get() = delta >= 0f
    }

    fun score(
        calories: Float,
        protein: Float,
        fat: Float,
        carbs: Float,
        fiber: Float,
        grams: Float,
        isDrink: Boolean,
        micros: Micronutrients = Micronutrients()
    ): Result {
        // Calorie-free things (water, black coffee) — nothing to grade.
        if (calories < 5f) {
            return Result(7, listOf(ScoreFactor("Calorie-free", true)))
        }

        val nudges = mutableListOf<Nudge>()

        // --- Protein density (g per 100 kcal) ---
        val proteinPer100 = protein / calories * 100f
        when {
            proteinPer100 >= 8f -> nudges += Nudge(1.6f, "High in protein (${protein.toInt()}g)")
            proteinPer100 >= 4f -> nudges += Nudge(0.6f, "Decent protein (${protein.toInt()}g)")
            protein < 3f -> nudges += Nudge(-1.2f, "Low in protein (${protein.toInt()}g)")
        }

        // --- Fiber ---
        val fiberPer100 = fiber / calories * 100f
        when {
            fiber >= 8f || fiberPer100 >= 3f -> nudges += Nudge(1.6f, "High in fiber (${fiber.toInt()}g)")
            fiberPer100 >= 1.5f -> nudges += Nudge(0.6f, "Some fiber (${fiber.toInt()}g)")
            carbs > 15f && fiber < 1.5f -> nudges += Nudge(-1.2f, "Low fiber for the carbs")
        }

        // --- Fat ratio (% of calories from fat) ---
        val fatPct = (fat * 9f) / calories * 100f
        when {
            fatPct > 55f -> nudges += Nudge(-1.5f, "Very high in fat (${fatPct.toInt()}% of calories)")
            fatPct > 42f -> nudges += Nudge(-0.8f, "High fat ratio (${fatPct.toInt()}%)")
            fatPct in 18f..40f -> nudges += Nudge(0.5f, "Balanced fat (${fatPct.toInt()}%)")
        }

        // --- Refined carbs proxy (carbs minus fiber, with little fiber) ---
        val netCarbsPer100 = ((carbs - fiber).coerceAtLeast(0f)) / calories * 100f
        if (netCarbsPer100 > 16f && fiber < 2f) {
            nudges += Nudge(-1.0f, "Mostly refined carbs")
        }

        // --- Calorie density (solids only) ---
        if (!isDrink && grams > 0f) {
            val calPerG = calories / grams
            when {
                calPerG > 3.5f -> nudges += Nudge(-1.0f, "Calorie-dense")
                calPerG < 1.2f && grams >= 60f -> nudges += Nudge(0.6f, "Light & filling")
            }
        }

        // --- Sodium (only if we have data) ---
        if (micros.sodiumMg > 0f) {
            val naPer100 = micros.sodiumMg / calories * 100f
            if (micros.sodiumMg >= 800f || naPer100 > 250f) {
                nudges += Nudge(-0.8f, "High in sodium (${micros.sodiumMg.toInt()}mg)")
            }
        }

        // --- Micronutrient richness ---
        val microHits = listOf(
            micros.vitaminCMg >= 15f,
            micros.vitaminAMcg >= 150f,
            micros.calciumMg >= 120f,
            micros.ironMg >= 2f,
            micros.potassiumMg >= 300f
        ).count { it }
        when {
            microHits >= 2 -> nudges += Nudge(1.0f, "Good source of vitamins & minerals")
            microHits == 1 -> nudges += Nudge(0.4f, "Contains some micronutrients")
        }

        val raw = 5.5f + nudges.sumOf { it.delta.toDouble() }.toFloat()
        val score = raw.roundToInt().coerceIn(1, 10)

        // Show the most impactful reasons first (up to 5). Guarantee at least one.
        val factors = nudges
            .sortedByDescending { kotlin.math.abs(it.delta) }
            .take(5)
            .map { ScoreFactor(it.text, it.positive) }
            .ifEmpty { listOf(ScoreFactor("Fairly balanced overall", true)) }

        return Result(score, factors)
    }

    // ---- Full per-dimension breakdown (for the detailed score view) ----

    enum class Level { GOOD, OKAY, POOR }

    data class Dimension(val label: String, val level: Level, val note: String)

    /**
     * A complete read on every dimension (not just the ones that moved the score),
     * so the breakdown always tells the user something useful.
     */
    fun breakdown(
        calories: Float,
        protein: Float,
        fat: Float,
        carbs: Float,
        fiber: Float,
        grams: Float,
        isDrink: Boolean,
        micros: Micronutrients = Micronutrients()
    ): List<Dimension> {
        if (calories < 5f) {
            return listOf(Dimension("Calories", Level.GOOD, "Calorie-free — nothing to grade"))
        }
        val out = mutableListOf<Dimension>()

        val pPer = protein / calories * 100f
        out += when {
            pPer >= 8f -> Dimension("Protein", Level.GOOD, "High — ${protein.toInt()}g")
            pPer >= 4f -> Dimension("Protein", Level.OKAY, "Decent — ${protein.toInt()}g")
            else -> Dimension("Protein", Level.POOR, "Low — ${protein.toInt()}g")
        }

        val fPer = fiber / calories * 100f
        out += when {
            fiber >= 8f || fPer >= 3f -> Dimension("Fiber", Level.GOOD, "High — ${fiber.toInt()}g")
            fPer >= 1.5f -> Dimension("Fiber", Level.OKAY, "Some — ${fiber.toInt()}g")
            else -> Dimension("Fiber", Level.POOR, "Low — ${fiber.toInt()}g")
        }

        val fatPct = (fat * 9f) / calories * 100f
        out += when {
            fatPct > 55f -> Dimension("Fat balance", Level.POOR, "Very high — ${fatPct.toInt()}% of calories")
            fatPct > 42f -> Dimension("Fat balance", Level.OKAY, "A bit high — ${fatPct.toInt()}%")
            fatPct < 18f -> Dimension("Fat balance", Level.OKAY, "Low — ${fatPct.toInt()}%")
            else -> Dimension("Fat balance", Level.GOOD, "Balanced — ${fatPct.toInt()}%")
        }

        if (!isDrink && grams > 0f) {
            val d = calories / grams
            out += when {
                d > 3.5f -> Dimension("Calorie density", Level.POOR, "Calorie-dense — ${"%.1f".format(d)} kcal/g")
                d < 1.5f -> Dimension("Calorie density", Level.GOOD, "Light & filling")
                else -> Dimension("Calorie density", Level.OKAY, "Moderate")
            }
        }

        val microHits = listOf(
            micros.vitaminCMg >= 15f,
            micros.vitaminAMcg >= 150f,
            micros.calciumMg >= 120f,
            micros.ironMg >= 2f,
            micros.potassiumMg >= 300f
        ).count { it }
        out += when {
            microHits >= 2 -> Dimension("Vitamins & minerals", Level.GOOD, "Good source")
            microHits == 1 -> Dimension("Vitamins & minerals", Level.OKAY, "Some present")
            else -> Dimension("Vitamins & minerals", Level.POOR, "Few detected")
        }

        if (micros.sodiumMg > 0f) {
            out += if (micros.sodiumMg >= 800f) {
                Dimension("Sodium", Level.POOR, "High — ${micros.sodiumMg.toInt()}mg")
            } else {
                Dimension("Sodium", Level.GOOD, "In check — ${micros.sodiumMg.toInt()}mg")
            }
        }

        return out
    }
}
