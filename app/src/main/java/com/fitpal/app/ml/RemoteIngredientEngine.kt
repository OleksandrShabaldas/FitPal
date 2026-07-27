package com.fitpal.app.ml

import android.graphics.Bitmap
import com.fitpal.app.data.local.dao.DailyNutritionRow
import com.fitpal.app.data.local.entity.WeightEntryEntity
import com.fitpal.app.domain.DailyTargets
import com.fitpal.app.domain.model.DetectedFood
import com.fitpal.app.domain.model.Ingredient
import com.fitpal.app.domain.model.MealInsights
import com.fitpal.app.domain.model.UserProfile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Online engine powered by Google's Gemini (via [GeminiClient]). It implements the exact
 * same [IngredientEngine] interface as the on-device [LlmIngredientEngine] and reuses the
 * shared [FoodPrompts] and [FoodJsonParser], so its results are a drop-in for the offline ones.
 *
 * This engine makes no decisions about WHEN to run — it just runs and throws
 * ([GeminiQuotaException] / [GeminiUnavailableException]) when it can't. Choosing between
 * this and the offline engine (and falling back) is [RoutingIngredientEngine]'s job.
 *
 * Temperatures mirror the on-device engine per task so the two feel consistent.
 */
@Singleton
class RemoteIngredientEngine @Inject constructor(
    private val gemini: GeminiClient
) : IngredientEngine {

    override suspend fun describeMeal(text: String): List<DetectedFood> = describeMeal(text) {}

    /** Like [describeMeal] but reports the live request stage for the UI tooltip. */
    suspend fun describeMeal(text: String, onProgress: (String) -> Unit): List<DetectedFood> {
        // Pure extraction (turn words into foods) — perception, not reasoning. Lean prompt, fast.
        val raw = gemini.generate(
            FoodPrompts.describeOnline(text), temperature = 0.15f, jsonMode = true,
            thinkingLevel = FAST, onProgress = onProgress
        )
        return FoodJsonParser.parseFoods(raw)
    }

    override suspend fun analyzeImage(
        bitmap: Bitmap,
        note: String,
        onProgress: (String) -> Unit
    ): List<DetectedFood> = analyzeImageWithCandidates(bitmap, note, onProgress).foods

    /**
     * Online single pass: identify ingredients AND name composite dishes AND suggest variations
     * AND (when the plate is one ambiguous dish) offer 2-3 candidates to pick from — all from the
     * photo, in one call. No separate dish-naming pass (that two-pass design was an on-device
     * crutch). Extraction stays on the fast thinking level.
     */
    suspend fun analyzeImageWithCandidates(
        bitmap: Bitmap,
        note: String,
        onProgress: (String) -> Unit = {}
    ): VisionResult {
        val raw = gemini.generate(
            prompt = FoodPrompts.visionOnline(note),
            images = listOf(bitmap),
            temperature = 0.2f,
            jsonMode = true,
            thinkingLevel = FAST,
            onProgress = onProgress
        )
        onProgress("Reading the AI's answer…")
        return FoodJsonParser.parseVisionResult(raw)
    }

    override suspend fun identifyDish(
        ingredients: List<Ingredient>,
        image: Bitmap?,
        note: String
    ): List<DetectedFood> {
        // Not used by the online photo flow (analyzeImage already names dishes); kept for the
        // interface. Naming a dish is reasoning, so give it the deeper level if it's ever called.
        if (ingredients.isEmpty()) return emptyList()
        val raw = gemini.generate(
            prompt = FoodPrompts.dish(ingredients, note),
            images = listOfNotNull(image),
            temperature = 0.2f,
            jsonMode = true,
            thinkingLevel = DEEP
        )
        return FoodJsonParser.parseDishCandidates(raw, ingredients)
    }

    override suspend fun analyzeMealInsights(foods: List<DetectedFood>): MealInsights {
        // Health scoring / swaps / energy & mood — reasoning. Deeper prompt + deeper thinking.
        val raw = gemini.generate(FoodPrompts.insightsOnline(foods), temperature = 0.3f, jsonMode = true, thinkingLevel = DEEP)
        return FoodJsonParser.parseInsights(raw)
    }

    override suspend fun generateNutritionReview(
        period: String,
        rows: List<DailyNutritionRow>,
        totalDays: Int,
        profile: UserProfile,
        targets: DailyTargets?,
        weights: List<WeightEntryEntity>,
        foodLog: String,
        extraContext: String
    ): String = generateNutritionReview(period, rows, totalDays, profile, targets, weights, foodLog, extraContext) {}

    /** Like [generateNutritionReview] but reports the live request stage for the UI tooltip. */
    suspend fun generateNutritionReview(
        period: String,
        rows: List<DailyNutritionRow>,
        totalDays: Int,
        profile: UserProfile,
        targets: DailyTargets?,
        weights: List<WeightEntryEntity>,
        foodLog: String,
        extraContext: String,
        onProgress: (String) -> Unit
    ): String {
        // Deep, personalised daily/weekly/monthly review — the showcase reasoning task. Leaner
        // online prompt, deeper thinking.
        return gemini.generate(
            prompt = FoodPrompts.reviewOnline(period, rows, totalDays, profile, targets, weights, foodLog, extraContext),
            temperature = 0.5f,
            jsonMode = false,
            thinkingLevel = DEEP,
            onProgress = onProgress
        ).trim()
    }

    override suspend fun generateRawText(prompt: String): String =
        gemini.generate(prompt, temperature = 0.5f, jsonMode = false, thinkingLevel = DEEP).trim()

    override fun close() {
        // Nothing to release — calls are stateless HTTP requests.
    }

    companion object {
        /** Thinking levels: fast for perception/extraction, deep for analysis/reasoning. */
        private const val FAST = "low"
        private const val DEEP = "high"
    }
}
