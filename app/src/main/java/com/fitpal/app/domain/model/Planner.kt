package com.fitpal.app.domain.model

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/*
 * The meal planner's shapes (docs/redesign/10): what to plan (the short form before each plan) and a
 * planned recipe (what comes back). Recipes keep the app's own Ingredient so a planned meal's numbers
 * mean exactly what a logged one's do.
 */

/** What to plan: one meal, a whole day or a whole week. */
enum class PlanScope(val label: String, val mealType: String?) {
    BREAKFAST("Breakfast", MealTypes.BREAKFAST),
    LUNCH("Lunch", MealTypes.LUNCH),
    DINNER("Dinner", MealTypes.DINNER),
    SNACK("Snack", MealTypes.SNACK),
    DAY("Whole day", null),
    WEEK("Whole week", null);

    val isOneMeal: Boolean get() = mealType != null
}

/** What the meal is for. COACH = let the coach decide (the default). */
enum class PlanGoal(val label: String, val prompt: String) {
    COACH("Let the coach decide", "whatever helps most today"),
    PROTEIN("Hit my protein", "plenty of protein toward the day's target"),
    COMFORT("Comfort after a long day", "a warm, comforting meal after a long day"),
    LOW_CAL("Low calorie but filling", "few calories but very filling (volume, protein, fibre)"),
    FIT_LEFT("Fit what's left today", "fitting what's left of today's calories and protein")
}

/**
 * The per-plan form, every step with a default so "Generate" works from step one. Kept as the last
 * request (JSON in settings) to prefill the next.
 */
data class PlanRequest(
    val scope: PlanScope = PlanScope.DINNER,
    /** The day to plan (one meal / whole day) or the first day (week). */
    val date: LocalDate = LocalDate.now(),
    val have: List<String> = emptyList(),
    val onlyHave: Boolean = false,
    /** Budget in the phone's currency per [budgetPer]; null = no budget. Only with [onlyHave] off. */
    val budget: Float? = null,
    val budgetPer: String = "meal",
    val feel: String = "",
    val feelPills: Set<String> = emptySet(),
    val goal: PlanGoal = PlanGoal.COACH,
    /** null = any cuisine. */
    val cuisine: String? = null,
    val timeMinutes: Int = 30,
    val servings: Int = 1
) {
    /** The days this plan covers. */
    val days: List<LocalDate> get() = if (scope == PlanScope.WEEK) (0L until 7L).map { date.plusDays(it) } else listOf(date)

    fun toJson(): String = JSONObject()
        .put("scope", scope.name).put("date", date.toString())
        .put("have", JSONArray(have)).put("onlyHave", onlyHave)
        .apply { budget?.let { put("budget", it.toDouble()) } }
        .put("budgetPer", budgetPer).put("feel", feel).put("feelPills", JSONArray(feelPills.toList()))
        .put("goal", goal.name).apply { cuisine?.let { put("cuisine", it) } }
        .put("timeMinutes", timeMinutes).put("servings", servings)
        .toString()

    companion object {
        val FEEL_PILLS = listOf("Something warm", "Fresh & light", "Hearty", "Sweet", "Crunchy", "Spicy", "Surprise me")
        val TIMES = listOf(10, 20, 30, 45, 60)

        /** The last request, with its day moved to today when it's in the past. */
        fun fromJson(json: String?): PlanRequest {
            if (json.isNullOrBlank()) return PlanRequest()
            return runCatching {
                val o = JSONObject(json)
                fun strings(key: String) = o.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
                val date = runCatching { LocalDate.parse(o.optString("date")) }.getOrNull()?.takeIf { !it.isBefore(LocalDate.now()) } ?: LocalDate.now()
                PlanRequest(
                    scope = PlanScope.entries.firstOrNull { it.name == o.optString("scope") } ?: PlanScope.DINNER,
                    date = date,
                    have = strings("have"),
                    onlyHave = o.optBoolean("onlyHave", false),
                    budget = if (o.has("budget")) o.optDouble("budget").toFloat().takeIf { it > 0f } else null,
                    budgetPer = o.optString("budgetPer", "meal").ifBlank { "meal" },
                    feel = o.optString("feel"),
                    feelPills = strings("feelPills").toSet(),
                    goal = PlanGoal.entries.firstOrNull { it.name == o.optString("goal") } ?: PlanGoal.COACH,
                    cuisine = o.optString("cuisine").takeIf { it.isNotBlank() },
                    timeMinutes = o.optInt("timeMinutes", 30),
                    servings = o.optInt("servings", 1).coerceIn(1, 8)
                )
            }.getOrDefault(PlanRequest())
        }
    }
}

/** One ingredient of a recipe, per serving the user eats, and whether they already have it. */
data class RecipeIngredient(val ingredient: Ingredient, val have: Boolean)

/** A complete planned meal: what to cook, how, and what it brings per serving. */
data class PlannedRecipe(
    val name: String,
    val mealType: String,
    /** Which day of the plan (0 = its first day). */
    val dayOffset: Int = 0,
    val cuisine: String = "",
    val timeMinutes: Int = 0,
    val servings: Int = 1,
    val difficulty: String = "",
    val whyItFits: String = "",
    val ingredients: List<RecipeIngredient> = emptyList(),
    val steps: List<String> = emptyList(),
    val tips: List<String> = emptyList(),
    /** Allergies found in the ingredients by the code check (see `Allergens`) — blocks scheduling. */
    val allergens: Set<String> = emptySet()
) {
    val kcal: Float get() = ingredients.sumOf { it.ingredient.calories.toDouble() }.toFloat()
    val protein: Float get() = ingredients.sumOf { it.ingredient.protein.toDouble() }.toFloat()
    val fat: Float get() = ingredients.sumOf { it.ingredient.fat.toDouble() }.toFloat()
    val carbs: Float get() = ingredients.sumOf { it.ingredient.carbs.toDouble() }.toFloat()
    val fiber: Float get() = ingredients.sumOf { it.ingredient.fiber.toDouble() }.toFloat()
}

/** One line of a plan's shopping list. */
data class ShoppingItem(val name: String, val amount: String, val estimatedCost: Float? = null)
