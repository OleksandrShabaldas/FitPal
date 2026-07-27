package com.fitpal.app.ml

import android.graphics.RectF
import com.fitpal.app.domain.model.DetectedFood
import com.fitpal.app.domain.model.FoodVariation
import com.fitpal.app.domain.model.HealthSwap
import com.fitpal.app.domain.model.Ingredient
import com.fitpal.app.domain.model.MealInsights
import com.fitpal.app.domain.model.Micronutrients
import com.fitpal.app.domain.model.ScoreFactor
import org.json.JSONArray
import org.json.JSONObject

/** The online vision result: the plate's foods + optional candidate dishes to pick between. */
data class VisionResult(
    val foods: List<DetectedFood>,
    val dishCandidates: List<DetectedFood>
)

/**
 * Turns the AI's JSON reply into our domain models. Shared by the on-device engine
 * ([LlmIngredientEngine]) and the online engine ([RemoteIngredientEngine]) so both
 * speak one contract — see [FoodPrompts] for the JSON shape these expect.
 *
 * Deliberately forgiving: small/old models emit slightly malformed JSON, extra prose,
 * or alternate key spellings, so everything here degrades gracefully rather than throwing.
 */
object FoodJsonParser {

    val EMPTY_INSIGHTS = MealInsights(
        healthScore = 5,
        scoreFactors = emptyList(),
        healthSwaps = emptyList(),
        energyImpact = "",
        moodImpact = "",
        pairingRecommendations = emptyList()
    )

    fun parseFoods(raw: String): List<DetectedFood> {
        val json = extractJsonObject(raw)

        val bareItems = json?.optJSONArray("items")
        if (json?.optJSONArray("foods") == null && bareItems != null) {
            return (0 until bareItems.length())
                .mapNotNull { bareItems.optJSONObject(it) }
                .mapNotNull { obj ->
                    parseIngredientObject(obj)?.let {
                        buildFood(it.name, it.grams, listOf(it), emptyList(), obj.optBoolean("isDrink", false))
                    }
                }
        }

        val foodObjects: List<JSONObject> = json?.optJSONArray("foods")
            ?.let { arr -> (0 until arr.length()).mapNotNull { arr.optJSONObject(it) } }
            ?: salvageArrayObjects(raw)

        return foodObjects.mapNotNull { foodFromObject(it) }
    }

    /**
     * Parse the richer ONLINE vision reply ({"foods":[...],"dishCandidates":[...]}): the confident
     * plate decomposition, plus — only when the plate is one ambiguous dish — 2-3 candidate dishes
     * to pick from. Each candidate re-names the same ingredients and carries its own variations.
     */
    fun parseVisionResult(raw: String): VisionResult {
        val foods = parseFoods(raw)
        val json = extractJsonObject(raw)
        val arr = json?.optJSONArray("dishCandidates")
        val candidates = mutableListOf<DetectedFood>()
        if (arr != null && foods.isNotEmpty()) {
            val baseIngredients = foods.flatMap { it.ingredients }
            val totalGrams = baseIngredients.sumOf { it.grams.toDouble() }.toFloat()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("name").trim()
                if (name.isEmpty()) continue
                candidates.add(
                    buildFood(
                        label = name,
                        grams = totalGrams,
                        ingredients = baseIngredients,
                        variations = parseVariations(obj.optJSONArray("variations")),
                        isDrink = false
                    )
                )
            }
        }
        return VisionResult(foods, candidates)
    }

    fun parseDishCandidates(raw: String, baseIngredients: List<Ingredient>): List<DetectedFood> {
        val json = extractJsonObject(raw) ?: return emptyList()
        val dishes = json.optJSONArray("dishes") ?: return emptyList()
        val totalGrams = baseIngredients.sumOf { it.grams.toDouble() }.toFloat()
        val result = mutableListOf<DetectedFood>()
        for (i in 0 until dishes.length()) {
            val obj = dishes.optJSONObject(i) ?: continue
            val name = obj.optString("name").trim()
            if (name.isEmpty()) continue
            result.add(
                buildFood(
                    label = name,
                    grams = totalGrams,
                    ingredients = baseIngredients,
                    variations = parseVariations(obj.optJSONArray("missing")),
                    isDrink = false
                )
            )
        }
        return result
    }

    fun parseInsights(raw: String): MealInsights {
        val json = extractJsonObject(raw) ?: return EMPTY_INSIGHTS

        val score = json.optInt("healthScore", 5).coerceIn(1, 10)

        val factors = mutableListOf<ScoreFactor>()
        json.optJSONArray("scoreFactors")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val text = obj.optString("text").trim()
                if (text.isNotEmpty()) {
                    factors.add(ScoreFactor(text, obj.optBoolean("positive", true)))
                }
            }
        }

        val swaps = mutableListOf<HealthSwap>()
        json.optJSONArray("swaps")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val from = obj.optString("from").trim()
                val to = obj.optString("to").trim()
                val why = obj.optString("why").trim()
                if (from.isNotEmpty() && to.isNotEmpty()) {
                    swaps.add(HealthSwap(from, to, why))
                }
            }
        }

        return MealInsights(
            healthScore = score,
            scoreFactors = factors,
            healthSwaps = swaps,
            energyImpact = json.optString("energy").trim(),
            moodImpact = json.optString("mood").trim(),
            pairingRecommendations = json.optJSONArray("pairings")
                ?.let { arr -> (0 until arr.length()).mapNotNull { arr.optString(it)?.trim()?.takeIf(String::isNotEmpty) } }
                ?: emptyList(),
            energyScore = json.optInt("energyScore", 0).coerceIn(0, 5),
            moodScore = json.optInt("moodScore", 0).coerceIn(0, 5)
        )
    }

    // ========================== HELPERS ==========================

    private fun foodFromObject(obj: JSONObject): DetectedFood? {
        val name = obj.optString("name").trim()
        var ingredients = parseIngredientArray(
            obj.optJSONArray("items") ?: obj.optJSONArray("ingredients")
        )
        if (ingredients.isEmpty()) {
            parseIngredientObject(obj)?.let { ing ->
                ingredients = listOf(if (name.isNotEmpty()) ing.copy(name = name) else ing)
            }
        }
        if (ingredients.isEmpty()) return null
        val label = name.ifEmpty { ingredients.first().name }
        return buildFood(
            label = label,
            grams = obj.optDouble("grams", 0.0).toFloat(),
            ingredients = ingredients,
            variations = parseVariations(obj.optJSONArray("variations")),
            isDrink = obj.optBoolean("isDrink", false)
        )
    }

    private fun salvageArrayObjects(text: String): List<JSONObject> {
        val keyIdx = listOf("\"foods\"", "\"items\"", "\"ingredients\"")
            .map { text.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: return emptyList()
        val bracket = text.indexOf('[', keyIdx)
        if (bracket < 0) return emptyList()
        val objs = mutableListOf<JSONObject>()
        var depth = 0
        var start = -1
        var inString = false
        var escaped = false
        for (i in bracket until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> { if (depth == 0) start = i; depth++ }
                '}' -> {
                    if (depth > 0) {
                        depth--
                        if (depth == 0 && start >= 0) {
                            try { objs.add(JSONObject(text.substring(start, i + 1))) } catch (_: Exception) {}
                            start = -1
                        }
                    }
                }
            }
        }
        return objs
    }

    private fun buildFood(
        label: String,
        grams: Float,
        ingredients: List<Ingredient>,
        variations: List<FoodVariation>,
        isDrink: Boolean = false
    ): DetectedFood = DetectedFood(
        label = label.replaceFirstChar { it.uppercase() },
        confidence = 1f,
        boundingBox = RectF(),
        estimatedGrams = if (grams > 0f) grams else ingredients.sumOf { it.grams.toDouble() }.toFloat(),
        ingredients = ingredients,
        variations = variations,
        isDrink = isDrink
    )

    private fun parseIngredientArray(items: JSONArray?): List<Ingredient> {
        if (items == null) return emptyList()
        val result = mutableListOf<Ingredient>()
        for (i in 0 until items.length()) {
            val obj = items.optJSONObject(i) ?: continue
            parseIngredientObject(obj)?.let { result.add(it) }
        }
        return result
    }

    private fun parseIngredientObject(obj: JSONObject): Ingredient? {
        val name = obj.optString("name").trim()
        if (name.isEmpty()) return null
        // Use the model's weight, or a realistic per-food default when it's missing — a flat 100 g
        // silently mis-sizes things like one egg (≈50 g) or a slice of bread (≈30 g).
        val grams = optNum(obj, "grams").let { if (it <= 0f) defaultServingGrams(name) else it }
        // Accept the variety of keys a small model might emit, and ignore non-numeric
        // placeholders (e.g. "<kcal>") which would read as 0.
        val protein = optNum(obj, "proteinPer100g", "protein")
        val fat = optNum(obj, "fatPer100g", "fat")
        val carbs = optNum(obj, "carbsPer100g", "carbs", "carbohydrates")
        val fiber = optNum(obj, "fiberPer100g", "fiber", "fibre")
        val modelKcal = optNum(obj, "kcalPer100g", "caloriesPer100g", "kcal", "calories", "energy")
        val calories = reconcileCalories(protein, fat, carbs, fiber, modelKcal)
        return Ingredient(
            name = name.replaceFirstChar { it.uppercase() },
            grams = grams,
            caloriesPer100g = calories,
            proteinPer100g = protein,
            fatPer100g = fat,
            carbsPer100g = carbs,
            fiberPer100g = fiber,
            waterMlPer100g = optNum(obj, "waterMlPer100", "waterMlPer100g", "water"),
            microsPer100g = Micronutrients(
                vitaminAMcg = optNum(obj, "vitA", "vitaminA"),
                vitaminCMg = optNum(obj, "vitC", "vitaminC"),
                vitaminDMcg = optNum(obj, "vitD", "vitaminD"),
                calciumMg = optNum(obj, "calcium"),
                ironMg = optNum(obj, "iron"),
                potassiumMg = optNum(obj, "potassium"),
                sodiumMg = optNum(obj, "sodium"),
                vitaminB12Mcg = optNum(obj, "b12", "vitaminB12", "vitB12"),
                folateMcg = optNum(obj, "folate", "fol", "b9"),
                vitaminB6Mg = optNum(obj, "b6", "vitaminB6", "vitB6"),
                magnesiumMg = optNum(obj, "magnesium", "mg"),
                zincMg = optNum(obj, "zinc", "zn"),
                vitaminEMg = optNum(obj, "vitE", "vitaminE")
            )
        )
    }

    /**
     * Read a numeric field that may appear under several keys and may be a
     * number, a numeric string, or a non-numeric placeholder. Returns the first
     * valid number found, else 0.
     */
    private fun optNum(obj: JSONObject, vararg keys: String): Float {
        for (k in keys) {
            if (!obj.has(k) || obj.isNull(k)) continue
            val d = obj.optDouble(k, Double.NaN)
            if (!d.isNaN()) return d.toFloat()
            obj.optString(k).trim().toFloatOrNull()?.let { return it }
        }
        return 0f
    }

    /** Max plausible energy density: pure oil/fat ≈ 900 kcal/100 g. Anything higher = broken macros. */
    private const val MAX_KCAL_PER_100G = 900f

    /**
     * Reconcile per-100g calories from the macros so the per-macro breakdown sums to the total.
     *
     * Atwater 4/9/4, but dietary fibre is counted at ~2 kcal/g (it's poorly digested): carbs already
     * INCLUDE fibre, so we count the digestible part at 4 and the fibre part at 2 — never on top.
     *
     * We still trust the macros over the model's own kcal (small models routinely report a kcal that
     * disagrees with their own macros). The one exception is a SANITY GUARD: if the macros are
     * physically impossible (>100 g of macro per 100 g of food, or a density above pure oil) the
     * estimate is broken, so we fall back to the model's kcal when it's plausible, else clamp.
     */
    private fun reconcileCalories(protein: Float, fat: Float, carbs: Float, fiber: Float, modelKcal: Float): Float {
        val digestibleCarbs = (carbs - fiber).coerceAtLeast(0f)
        val atwater = protein * 4f + fat * 9f + digestibleCarbs * 4f + fiber * 2f
        val macroGrams = protein + fat + carbs
        val modelOk = modelKcal in 1f..MAX_KCAL_PER_100G
        return when {
            macroGrams > 105f || atwater > MAX_KCAL_PER_100G ->
                if (modelOk) modelKcal else atwater.coerceIn(0f, MAX_KCAL_PER_100G)
            atwater > 0f -> atwater
            else -> modelKcal      // no usable macros — the 0-kcal USDA safety net can still fill in
        }
    }

    /** A realistic default weight (g/ml) for a single serving when the model omits one. */
    private fun defaultServingGrams(name: String): Float {
        val n = name.lowercase()
        return SERVING_DEFAULTS.firstOrNull { n.contains(it.first) }?.second ?: 100f
    }

    /** Keyword → typical single-serving size. First match wins, so list specifics before generics. */
    private val SERVING_DEFAULTS = listOf(
        // drinks (ml)
        "smoothie" to 300f, "milkshake" to 300f, "juice" to 250f, "milk" to 250f,
        "coffee" to 240f, "latte" to 300f, "tea" to 240f, "cola" to 330f, "soda" to 330f,
        "beer" to 330f, "wine" to 150f, "water" to 250f,
        // foods / common units (g)
        "chicken breast" to 150f, "egg" to 50f, "toast" to 30f, "bread" to 30f, "slice" to 30f,
        "banana" to 120f, "apple" to 180f, "orange" to 130f, "rice" to 150f, "pasta" to 150f,
        "potato" to 150f, "cookie" to 25f, "biscuit" to 25f, "chocolate" to 30f, "nuts" to 30f,
        "cheese" to 30f, "yogurt" to 150f, "yoghurt" to 150f, "soup" to 300f, "salad" to 200f
    )

    private fun parseVariations(arr: JSONArray?): List<FoodVariation> {
        if (arr == null) return emptyList()
        val result = mutableListOf<FoodVariation>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val ingredient = parseIngredientObject(obj) ?: continue
            val description = obj.optString("description").trim().ifEmpty { ingredient.name }
            result.add(
                FoodVariation(
                    description = description.replaceFirstChar { it.uppercase() },
                    ingredientChanges = listOf(ingredient)
                )
            )
        }
        return result
    }

    private fun extractJsonObject(text: String): JSONObject? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            JSONObject(text.substring(start, end + 1))
        } catch (e: Exception) {
            null
        }
    }
}
