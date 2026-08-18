package com.fitpal.app.ml

import com.fitpal.app.domain.HealthScorer
import com.fitpal.app.domain.model.MealInsights
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates a food's AI **overview** (health score + swaps + energy/mood + pairings) — the same
 * per-item insight the meal-detail and collection screens show. Extracted so the background
 * [InsightsWorker] and the on-demand screens produce identical results. The health score/factors are
 * deterministic ([HealthScorer]); only the coaching text comes from the model.
 */
@Singleton
class InsightsGenerator @Inject constructor(
    private val pipeline: FoodAnalysisPipeline
) {
    suspend fun generate(
        name: String,
        grams: Float,
        calories: Float,
        protein: Float,
        fat: Float,
        carbs: Float,
        fiber: Float,
        isDrink: Boolean,
        ingredients: List<String>
    ): Pair<MealInsights, AiSource> {
        val scored = HealthScorer.score(
            calories = calories,
            protein = protein,
            fat = fat,
            carbs = carbs,
            fiber = fiber,
            grams = grams,
            isDrink = isDrink
        )
        val unit = if (isDrink) "ml" else "g"
        val prompt = FoodPrompts.itemInsights(
            name = name,
            amountLabel = "${grams.toInt()}$unit",
            kcal = calories.toInt(),
            protein = protein.toInt(),
            fat = fat.toInt(),
            carbs = carbs.toInt(),
            fiber = fiber.toInt(),
            ingredients = ingredients.joinToString(", ")
        )
        val (response, source) = pipeline.generateRawTextWithSource(prompt)

        val swaps = FoodJsonParser.parseItemSwaps(response, listOf(name) + ingredients)
        val energy = Regex("ENERGY:\\s*(.+)").find(response)?.groupValues?.get(1)?.trim() ?: ""
        val mood = Regex("MOOD:\\s*(.+)").find(response)?.groupValues?.get(1)?.trim() ?: ""
        val energyScore = Regex("ENERGY_SCORE:\\s*(\\d+)").find(response)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, 5) ?: 0
        val moodScore = Regex("MOOD_SCORE:\\s*(\\d+)").find(response)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, 5) ?: 0
        val pairings = Regex("PAIR:\\s*(.+)").findAll(response).map { it.groupValues[1].trim() }.toList()

        val insights = MealInsights(
            healthScore = scored.score,
            scoreFactors = scored.factors,
            healthSwaps = swaps,
            energyImpact = energy,
            moodImpact = mood,
            pairingRecommendations = pairings,
            energyScore = energyScore,
            moodScore = moodScore
        )
        return insights to source
    }
}
