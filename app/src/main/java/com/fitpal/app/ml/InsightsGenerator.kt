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

    /** One food to analyse in a batch. */
    data class ItemSpec(
        val name: String,
        val grams: Float,
        val calories: Float,
        val protein: Float,
        val fat: Float,
        val carbs: Float,
        val fiber: Float,
        val isDrink: Boolean,
        val ingredients: List<String>
    )

    /**
     * Generate per-item insights for a whole meal in ONE model call (see
     * [FoodPrompts.batchItemInsights]) instead of one call per dish — the health score/factors stay
     * deterministic per item, only the coaching text is batched. Returns one [MealInsights] per input
     * (aligned to [specs]) plus the engine that answered. Empty input returns empty.
     */
    suspend fun generateBatch(specs: List<ItemSpec>): Pair<List<MealInsights>, AiSource> {
        if (specs.isEmpty()) return emptyList<MealInsights>() to AiSource.offline()
        val scored = specs.map {
            HealthScorer.score(it.calories, it.protein, it.fat, it.carbs, it.fiber, it.grams, it.isDrink)
        }
        val itemsBlock = specs.mapIndexed { i, s ->
            val unit = if (s.isDrink) "ml" else "g"
            val ing = if (s.ingredients.isEmpty()) "" else " — made of: ${s.ingredients.joinToString(", ")}"
            "${i + 1}. ${s.name}, ${s.grams.toInt()}$unit, ${s.calories.toInt()} kcal " +
                "(P${s.protein.toInt()}g F${s.fat.toInt()}g C${s.carbs.toInt()}g Fiber ${s.fiber.toInt()}g)$ing"
        }.joinToString("\n")

        val prompt = FoodPrompts.batchItemInsights(itemsBlock, specs.size)
        val (response, source) = pipeline.generateRawTextWithSource(prompt)
        val present = specs.map { listOf(it.name) + it.ingredients }
        val parsed = FoodJsonParser.parseBatchItemInsights(response, present)

        val out = specs.indices.map { i ->
            val ai = parsed.getOrElse(i) { FoodJsonParser.ItemAiText.EMPTY }
            MealInsights(
                healthScore = scored[i].score,
                scoreFactors = scored[i].factors,
                healthSwaps = ai.swaps,
                energyImpact = ai.energy,
                moodImpact = ai.mood,
                pairingRecommendations = ai.pairings,
                energyScore = ai.energyScore,
                moodScore = ai.moodScore
            )
        }
        return out to source
    }
}
