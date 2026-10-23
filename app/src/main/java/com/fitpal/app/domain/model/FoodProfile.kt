package com.fitpal.app.domain.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * "Your food profile" (docs/redesign/10): the form filled in once — diet, allergies, what you won't
 * eat and what you love, cuisines, cooking skill, kitchen, who you cook for, budget, notes. Every
 * meal plan is made with it; allergies are hard rules (and re-checked in code, see `Allergens`).
 * All optional. Stored as JSON in `SettingsRepository.foodProfile` and in the backup.
 */
data class FoodProfile(
    val diet: String = DIET_NONE,
    val dietOther: String = "",
    val allergies: Set<String> = emptySet(),
    val allergyOther: String = "",
    val wontEat: List<String> = emptyList(),
    val love: List<String> = emptyList(),
    val cuisines: Set<String> = emptySet(),
    val skill: String = SKILL_COMFORTABLE,
    val kitchen: Set<String> = setOf("Stovetop", "Oven", "Microwave"),
    val cookingFor: Int = 1,
    /** Typical budget in the phone's currency, per [budgetPer] ("meal" / "day"); null = not set. */
    val budget: Float? = null,
    val budgetPer: String = "meal",
    val notes: String = ""
) {
    /** Allergies as the words the allergen check matches, the free-text ones included. */
    val allAllergies: Set<String>
        get() = allergies + allergyOther.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /** One line for the Coach card: "Vegetarian · no peanuts, gluten". */
    val summary: String
        get() = buildList {
            add(if (diet == DIET_OTHER && dietOther.isNotBlank()) dietOther.trim() else diet)
            if (allAllergies.isNotEmpty()) add("no " + allAllergies.joinToString(", ") { it.lowercase() })
        }.filter { it != DIET_NONE || allAllergies.isEmpty() }.joinToString(" · ").ifBlank { DIET_NONE }

    fun toJson(): String = JSONObject()
        .put("diet", diet).put("dietOther", dietOther)
        .put("allergies", JSONArray(allergies.toList())).put("allergyOther", allergyOther)
        .put("wontEat", JSONArray(wontEat)).put("love", JSONArray(love))
        .put("cuisines", JSONArray(cuisines.toList()))
        .put("skill", skill).put("kitchen", JSONArray(kitchen.toList()))
        .put("cookingFor", cookingFor)
        .apply { budget?.let { put("budget", it.toDouble()) } }
        .put("budgetPer", budgetPer).put("notes", notes)
        .toString()

    companion object {
        const val DIET_NONE = "No restriction"
        const val DIET_OTHER = "Other"
        val DIETS = listOf(DIET_NONE, "Vegetarian", "Vegan", "Pescatarian", "Low-carb", "Keto", "Halal", "Kosher", DIET_OTHER)
        val ALLERGIES = listOf("Peanuts", "Tree nuts", "Gluten", "Lactose", "Eggs", "Soy", "Fish", "Shellfish", "Sesame")
        val CUISINES = listOf(
            "Italian", "Mexican", "Japanese", "Chinese", "Indian", "Thai", "Middle Eastern", "Mediterranean",
            "Ukrainian / Eastern European", "American", "Korean", "French"
        )
        const val SKILL_COMFORTABLE = "Comfortable"
        val SKILLS = listOf("Just starting", SKILL_COMFORTABLE, "Confident", "Loves a challenge")
        val KITCHEN = listOf("Stovetop", "Oven", "Microwave", "Air fryer", "Blender", "Slow cooker", "No kitchen (assembly only)")

        fun fromJson(json: String?): FoodProfile {
            if (json.isNullOrBlank()) return FoodProfile()
            return runCatching {
                val o = JSONObject(json)
                fun strings(key: String) = o.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
                FoodProfile(
                    diet = o.optString("diet", DIET_NONE).ifBlank { DIET_NONE },
                    dietOther = o.optString("dietOther"),
                    allergies = strings("allergies").toSet(),
                    allergyOther = o.optString("allergyOther"),
                    wontEat = strings("wontEat"),
                    love = strings("love"),
                    cuisines = strings("cuisines").toSet(),
                    skill = o.optString("skill", SKILL_COMFORTABLE).ifBlank { SKILL_COMFORTABLE },
                    kitchen = if (o.has("kitchen")) strings("kitchen").toSet() else FoodProfile().kitchen,
                    cookingFor = o.optInt("cookingFor", 1).coerceIn(1, 8),
                    budget = if (o.has("budget")) o.optDouble("budget").toFloat().takeIf { it > 0f } else null,
                    budgetPer = o.optString("budgetPer", "meal").ifBlank { "meal" },
                    notes = o.optString("notes")
                )
            }.getOrDefault(FoodProfile())
        }
    }
}
