package com.fitpal.app.ml

import android.graphics.Bitmap
import com.fitpal.app.data.local.dao.DailyNutritionRow
import com.fitpal.app.data.local.entity.WeightEntryEntity
import com.fitpal.app.domain.DailyTargets
import com.fitpal.app.domain.model.DetectedFood
import com.fitpal.app.domain.model.Ingredient
import com.fitpal.app.domain.model.MealInsights
import com.fitpal.app.domain.model.UserProfile

/**
 * The on-device AI engine. Backed by a single multimodal Gemma model that does
 * both jobs:
 *  - text: turn a typed description into the foods/drinks it contains (Describe-to-AI)
 *  - vision: look at a food photo and return every food on the plate
 *
 * Both return [DetectedFood]s: a packaged product/drink comes back as one food with
 * a single ingredient; a composite dish comes back with its ingredients and any
 * common variations the user can choose.
 */
interface IngredientEngine {

    /** Text path: turn a typed meal/food description into foods. */
    suspend fun describeMeal(text: String): List<DetectedFood>

    /**
     * Vision path: look at a food photo and return every food found.
     * [note] is optional user-supplied context (ingredients, size, grammage…).
     */
    suspend fun analyzeImage(bitmap: Bitmap, note: String = "", onProgress: (String) -> Unit = {}): List<DetectedFood>

    /**
     * Second pass over a photo's loose ingredients: guess which dish they form.
     * Passes the [image] (when available) so the dish can be named from what's
     * actually on the plate, plus the optional user [note]. Returns one or more
     * candidate dishes (most likely first); each holds the same ingredients plus
     * dish-specific "missing" extras (e.g. a dressing) as variations.
     */
    suspend fun identifyDish(ingredients: List<Ingredient>, image: Bitmap? = null, note: String = ""): List<DetectedFood>

    /**
     * Third pass: generate health insights for an identified meal — health score,
     * score breakdown, swap suggestions, energy/mood impact, and pairing ideas.
     */
    suspend fun analyzeMealInsights(foods: List<DetectedFood>): MealInsights

    /**
     * Generate a deep, personalised AI nutrition review for a time period.
     * Returns plain-text advice (not JSON) — specific, actionable, even
     * psychological when the goal demands it.
     */
    suspend fun generateNutritionReview(
        period: String,
        rows: List<DailyNutritionRow>,
        totalDays: Int,
        profile: UserProfile,
        targets: DailyTargets?,
        weights: List<WeightEntryEntity>,
        /** Pre-formatted list of the actual foods eaten in the period, so advice can be specific. */
        foodLog: String = "",
        /** Extra context: activity/calories burned, personal limitations, long-term habit notes. */
        extraContext: String = ""
    ): String

    /** General-purpose text prompt — returns raw AI response text. */
    suspend fun generateRawText(prompt: String): String

    fun close()
}
