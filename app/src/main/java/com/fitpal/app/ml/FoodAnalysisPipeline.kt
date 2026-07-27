package com.fitpal.app.ml

import android.graphics.Bitmap
import com.fitpal.app.data.local.dao.DailyNutritionRow
import com.fitpal.app.data.local.entity.WeightEntryEntity
import com.fitpal.app.data.repository.NutritionRepository
import com.fitpal.app.domain.DailyTargets
import com.fitpal.app.domain.model.DetectedFood
import com.fitpal.app.domain.model.Ingredient
import com.fitpal.app.domain.model.MealInsights
import com.fitpal.app.domain.model.UserProfile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Entry point for analyzing a food photo.
 *
 * With the multimodal model, this is a single step: the AI looks at the photo
 * and returns every food on the plate, each with its ingredients and nutrition.
 * (The old detect → classify → portion → ingredients pipeline is no longer
 * needed — the vision model does it all in one shot.)
 *
 * As a safety net, any ingredient the AI returns with 0 calories is looked up in
 * the offline USDA database so the user never ends up logging a "0 kcal" food.
 */
@Singleton
class FoodAnalysisPipeline @Inject constructor(
    private val ingredientEngine: IngredientEngine,
    private val nutritionRepository: NutritionRepository
) {
    suspend fun analyze(
        image: Bitmap,
        note: String = "",
        depthData: FloatArray? = null,
        onProgress: (String) -> Unit = {}
    ): List<DetectedFood> {
        val foods = ingredientEngine.analyzeImage(image, note, onProgress)
        onProgress("Looking up nutrition…")
        return foods.map { enrichZeros(it) }
    }

    /** Text path (Describe-to-AI) -> foods, with the same 0-kcal safety net. */
    suspend fun describeMeal(text: String): List<DetectedFood> =
        ingredientEngine.describeMeal(text).map { enrichZeros(it) }

    // ---- Explicit online / on-device analysis (the service drives the choice, so it can offer
    //      a manual retry instead of silently falling back) ----

    private val routing: RoutingIngredientEngine?
        get() = ingredientEngine as? RoutingIngredientEngine

    /** Whether online is worth attempting right now. */
    fun canUseOnline(): Boolean = routing?.canUseOnline() ?: false

    /** Whether the user has the online AI master switch on (drives the on-device confirm prompt). */
    fun isOnlineAiEnabled(): Boolean = routing?.onlineEnabled() ?: false

    /** A short reason online can't be used (for the badge), when [canUseOnline] is false. */
    fun offlineReason(): String = routing?.offlineReason() ?: "On-device only"

    /** Pin the secondary steps (dish/insights) to on-device for the rest of this job. */
    fun setPreferLocal(value: Boolean) { routing?.setForceLocal(value) }

    /**
     * Online-only photo analysis — returns the plate's foods AND any candidate dishes (single
     * pass). Throws ([GeminiQuotaException]/[GeminiUnavailableException]) on failure. The 0-kcal
     * USDA safety net is applied to both lists.
     */
    suspend fun analyzeImageOnline(image: Bitmap, note: String, onProgress: (String) -> Unit = {}): VisionResult {
        val r = routing ?: throw GeminiUnavailableException("Online engine unavailable")
        return try {
            val result = r.onlineEngine.analyzeImageWithCandidates(image, note, onProgress)
            VisionResult(
                foods = result.foods.map { enrichZeros(it) },
                dishCandidates = result.dishCandidates.map { enrichZeros(it) }
            )
        } catch (e: GeminiQuotaException) {
            throw e // GeminiClient already recorded per-model quota
        }
    }

    /** On-device-only photo analysis. */
    suspend fun analyzeImageLocal(image: Bitmap, note: String, onProgress: (String) -> Unit = {}): List<DetectedFood> {
        val engine = routing?.offlineEngine ?: ingredientEngine
        return engine.analyzeImage(image, note, onProgress).map { enrichZeros(it) }
    }

    /** Online-only text analysis. Throws on failure. */
    suspend fun describeMealOnline(text: String, onProgress: (String) -> Unit = {}): List<DetectedFood> {
        val r = routing ?: throw GeminiUnavailableException("Online engine unavailable")
        return try {
            r.onlineEngine.describeMeal(text, onProgress).map { enrichZeros(it) }
        } catch (e: GeminiQuotaException) {
            throw e // GeminiClient already recorded per-model quota
        }
    }

    /** On-device-only text analysis. */
    suspend fun describeMealLocal(text: String): List<DetectedFood> {
        val engine = routing?.offlineEngine ?: ingredientEngine
        return engine.describeMeal(text).map { enrichZeros(it) }
    }

    /**
     * Second pass: guess which dish a photo's loose ingredients form, returning
     * candidate dishes (most likely first) with suggested missing extras.
     */
    suspend fun identifyDish(
        ingredients: List<Ingredient>,
        image: Bitmap? = null,
        note: String = ""
    ): List<DetectedFood> =
        ingredientEngine.identifyDish(ingredients, image, note).map { enrichZeros(it) }

    /**
     * Fill in nutrition for any ingredient the AI returned with 0 kcal by looking
     * it up by name in the offline food database. Keeps the AI's portion size.
     */
    private suspend fun enrichZeros(food: DetectedFood): DetectedFood {
        if (food.ingredients.all { it.caloriesPer100g > 0f }) return food
        val fixed = food.ingredients.map { ing ->
            if (ing.caloriesPer100g > 0f) return@map ing
            val lookup = nutritionRepository.lookupIngredient(ing.name, ing.grams)
            if (lookup != null && lookup.caloriesPer100g > 0f) {
                ing.copy(
                    caloriesPer100g = lookup.caloriesPer100g,
                    proteinPer100g = lookup.proteinPer100g,
                    fatPer100g = lookup.fatPer100g,
                    carbsPer100g = lookup.carbsPer100g
                )
            } else ing
        }
        return food.copy(ingredients = fixed)
    }

    /**
     * Third pass: generate health insights for identified foods — health score,
     * swaps, energy/mood impact, pairing recommendations.
     */
    suspend fun analyzeMealInsights(foods: List<DetectedFood>): MealInsights =
        ingredientEngine.analyzeMealInsights(foods)

    /**
     * Deep AI nutrition review — daily, weekly, or monthly — with personalised,
     * goal-aware, psychologically-informed suggestions.
     */
    suspend fun generateNutritionReview(
        period: String,
        rows: List<DailyNutritionRow>,
        totalDays: Int,
        profile: UserProfile,
        targets: DailyTargets?,
        weights: List<WeightEntryEntity>,
        foodLog: String = "",
        extraContext: String = ""
    ): String = ingredientEngine.generateNutritionReview(period, rows, totalDays, profile, targets, weights, foodLog, extraContext)

    /** General-purpose text prompt passthrough. */
    suspend fun generateRawText(prompt: String): String =
        ingredientEngine.generateRawText(prompt)

    /**
     * "Edit with AI": apply a natural-language correction to a whole [food] (add / remove / rename /
     * re-weigh its ingredients, keeping the total realistic). Routes online → on-device like the
     * other secondary generations, applies the 0-kcal safety net, and returns the revised food (or
     * null if the model returned nothing usable). The caller keeps the original if this is null.
     */
    suspend fun refineFood(food: DetectedFood, instruction: String): DetectedFood? {
        if (instruction.isBlank()) return null
        val (response, _) = generateRawTextWithSource(FoodPrompts.editFood(food, instruction))
        return FoodJsonParser.parseFoods(response).firstOrNull()?.let { enrichZeros(it) }
    }

    // ---- Source-reporting variants: try online, silently fall back to on-device, and tell the
    //      caller which engine produced the result so the UI can show an online/on-device badge.
    //      (These are for non-photo generations — review, per-item insights, exercise — where a
    //      silent fallback is fine; only the food primary analysis offers a manual choice.) ----

    private suspend fun <T> withSource(
        online: suspend (RemoteIngredientEngine) -> T,
        local: suspend (IngredientEngine) -> T
    ): Pair<T, AiSource> {
        val r = routing
        if (r != null && r.canUseOnline()) {
            try {
                return online(r.onlineEngine) to AiSource.ONLINE
            } catch (e: GeminiQuotaException) {
                // GeminiClient already recorded per-model quota; fall through to on-device.
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // fall through to on-device
            }
        }
        val engine = r?.offlineEngine ?: ingredientEngine
        return local(engine) to AiSource.OFFLINE
    }

    suspend fun generateNutritionReviewWithSource(
        period: String,
        rows: List<DailyNutritionRow>,
        totalDays: Int,
        profile: UserProfile,
        targets: DailyTargets?,
        weights: List<WeightEntryEntity>,
        foodLog: String = "",
        extraContext: String = "",
        onProgress: (String) -> Unit = {}
    ): Pair<String, AiSource> = withSource(
        // Online RemoteIngredientEngine has an onProgress-capable overload; the on-device path
        // has no fine-grained stages, so it just reports a single "thinking" message.
        { it.generateNutritionReview(period, rows, totalDays, profile, targets, weights, foodLog, extraContext, onProgress) },
        {
            onProgress("Thinking on your phone — this can take a moment…")
            it.generateNutritionReview(period, rows, totalDays, profile, targets, weights, foodLog, extraContext)
        }
    )

    suspend fun generateRawTextWithSource(prompt: String): Pair<String, AiSource> =
        withSource({ it.generateRawText(prompt) }, { it.generateRawText(prompt) })

    fun close() {
        ingredientEngine.close()
    }
}
