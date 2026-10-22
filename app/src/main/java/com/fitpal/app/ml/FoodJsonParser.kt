package com.fitpal.app.ml

import android.graphics.RectF
import com.fitpal.app.domain.model.CoachingTip
import com.fitpal.app.domain.model.ContextQuestion
import com.fitpal.app.domain.model.DetectedFood
import com.fitpal.app.domain.model.FoodVariation
import com.fitpal.app.domain.model.HealthSwap
import com.fitpal.app.domain.model.Ingredient
import com.fitpal.app.domain.model.MealInsights
import com.fitpal.app.domain.model.Micronutrients
import com.fitpal.app.domain.model.ScoreFactor
import com.fitpal.app.domain.model.TipType
import org.json.JSONArray
import org.json.JSONObject

/** The online vision result: the plate's foods + optional candidate dishes to pick between. */
data class VisionResult(
    val foods: List<DetectedFood>,
    val dishCandidates: List<DetectedFood>
)

/**
 * The on-device dish step's answer (docs/redesign/08): either the plate's separate [dishes] (fish and
 * chips + a side salad = two), or — when it's one dish — likely names for it as [candidates], most
 * likely first, each holding all the loose items. At most one of the two is non-empty.
 */
data class DishResult(
    val dishes: List<DetectedFood> = emptyList(),
    val candidates: List<DetectedFood> = emptyList()
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

    // ---------------- Health swaps ----------------

    private val SWAP_LINE = Regex("SWAP:\\s*(.+?)\\s*->\\s*(.+?)\\s*\\|\\s*(.+)")

    /** Words too generic to prove a swap is talking about a food that's really in the meal. */
    private val SWAP_STOPWORDS = setOf(
        "with", "and", "the", "of", "your", "some", "fresh", "raw", "cooked", "plain",
        "whole", "large", "small", "medium", "regular", "white", "brown", "portion", "serving"
    )

    /**
     * Parse the `SWAP: x -> y | why` lines of a per-item insight, keeping only swaps that
     * name something actually in the meal.
     *
     * Models like to suggest replacing food that isn't there ("swap the mayo" on a plate with
     * no mayo). The prompt asks them not to; this makes sure. Pass every real name — the
     * dish plus its ingredients — in [presentFoods].
     */
    fun parseItemSwaps(response: String, presentFoods: List<String>): List<HealthSwap> {
        val swaps = SWAP_LINE.findAll(response)
            .map { HealthSwap(it.groupValues[1].trim(), it.groupValues[2].trim(), it.groupValues[3].trim()) }
            .toList()
        return groundSwaps(swaps, presentFoods)
    }

    /** Drop any swap whose "from" doesn't correspond to something in [presentFoods]. */
    fun groundSwaps(swaps: List<HealthSwap>, presentFoods: List<String>): List<HealthSwap> {
        val names = presentFoods.map(::normaliseFoodName).filter { it.isNotEmpty() }
        if (names.isEmpty()) return swaps   // nothing to check against — don't throw away advice
        val tokens = names.flatMap(::significantWords).toSet()
        return swaps.filter { swap ->
            val from = normaliseFoodName(swap.original)
            from.isNotEmpty() && (
                names.any { it.contains(from) || from.contains(it) } ||
                    significantWords(from).any { it in tokens }
                )
        }
    }

    private fun normaliseFoodName(raw: String): String =
        raw.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun significantWords(normalised: String): List<String> =
        normalised.split(" ").filter { it.length >= 4 && it !in SWAP_STOPWORDS }

    fun parseFoods(raw: String): List<DetectedFood> {
        val json = extractJsonObject(raw)

        val bareItems = json?.optJSONArray("items")
        if (json?.optJSONArray("foods") == null && bareItems != null) {
            return (0 until bareItems.length())
                .mapNotNull { bareItems.optJSONObject(it) }
                .mapNotNull { obj ->
                    parseIngredientObject(obj)?.let {
                        val drink = drinkFlag(obj, it.name)
                        buildFood(it.name, it.grams, withDrinkWater(listOf(it), drink), emptyList(), drink)
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
        // Drinks are never merged into a candidate dish — they stay their own foods next to it.
        val dishFoods = foods.filterNot { it.isDrink }
        if (arr != null && dishFoods.isNotEmpty()) {
            val baseIngredients = dishFoods.flatMap { it.ingredients }
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

    /**
     * Read the dish step's groups: `{"dishes":[{"name","items":[…],"also":[…],"missing":[…]}]}` —
     * one entry per separate dish, naming which of the loose [baseIngredients] belong to it. Two or
     * more groups with items → that many dishes (an item no group claimed joins the first, so nothing
     * is lost). One group → one dish of everything, with its name and its "also" names as candidates.
     * An answer in the older shape (several names, no "items") is read as candidates, as before.
     */
    fun parseDishGroups(raw: String, baseIngredients: List<Ingredient>): DishResult {
        val json = extractJsonObject(raw) ?: return DishResult()
        val arr = json.optJSONArray("dishes") ?: return DishResult()
        val groups = (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = obj.optString("name").trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            fun strings(key: String) = obj.optJSONArray(key)?.let { a ->
                (0 until a.length()).mapNotNull { a.optString(it).trim().takeIf { s -> s.isNotEmpty() } }
            }.orEmpty()
            DishGroup(name, strings("items"), strings("also"), obj.optJSONArray("missing"))
        }
        if (groups.isEmpty()) return DishResult()
        val totalGrams = baseIngredients.sumOf { it.grams.toDouble() }.toFloat()
        fun wholePlate(name: String, missing: JSONArray?) =
            buildFood(label = name, grams = totalGrams, ingredients = baseIngredients, variations = parseVariations(missing), isDrink = false)

        // The older answer: every entry is a guess at the same one dish.
        if (groups.none { it.items.isNotEmpty() }) return DishResult(candidates = groups.map { wholePlate(it.name, it.missing) })

        // Hand each loose item to the group that names it: the exact name first, then a containing one.
        val owner = IntArray(baseIngredients.size) { -1 }
        fun norm(s: String) = s.lowercase().trim()
        groups.forEachIndexed { g, group ->
            group.items.forEach { item ->
                val n = norm(item)
                val i = baseIngredients.indices.firstOrNull { owner[it] == -1 && norm(baseIngredients[it].name) == n }
                    ?: baseIngredients.indices.firstOrNull {
                        val b = norm(baseIngredients[it].name)
                        owner[it] == -1 && (b.contains(n) || n.contains(b))
                    }
                if (i != null) owner[i] = g
            }
        }
        val claimed = groups.indices.filter { g -> owner.any { it == g } }
        if (claimed.size <= 1) {
            val main = groups[claimed.firstOrNull() ?: 0]
            return DishResult(candidates = listOf(wholePlate(main.name, main.missing)) + main.also.map { wholePlate(it, null) })
        }
        baseIngredients.indices.forEach { if (owner[it] == -1) owner[it] = claimed.first() }
        val dishes = claimed.map { g ->
            val items = baseIngredients.filterIndexed { i, _ -> owner[i] == g }
            buildFood(
                label = groups[g].name,
                grams = items.sumOf { it.grams.toDouble() }.toFloat(),
                ingredients = items,
                variations = parseVariations(groups[g].missing),
                isDrink = false
            )
        }
        return DishResult(dishes = dishes)
    }

    private class DishGroup(val name: String, val items: List<String>, val also: List<String>, val missing: JSONArray?)

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

    /**
     * Parse a meal coaching tip: `{"type":..,"message":..}` → [CoachingTip], or null when the model
     * declines (a bare "null", empty object, or blank message). Lenient about extra prose around it.
     */
    fun parseCoachingTip(raw: String): CoachingTip? {
        val json = extractJsonObject(raw) ?: return null
        val message = json.optString("message").trim()
        if (message.isBlank() || message.equals("null", ignoreCase = true)) return null
        return CoachingTip(type = TipType.fromString(json.optString("type")), message = message)
    }

    /**
     * Parse a check-in question: `{"question":..,"options":[..]}` → [ContextQuestion], or null if the
     * question is blank or there are fewer than two usable options.
     */
    fun parseContextQuestion(raw: String): ContextQuestion? {
        val json = extractJsonObject(raw) ?: return null
        val question = json.optString("question").trim()
        if (question.isBlank()) return null
        val options = json.optJSONArray("options")
            ?.let { arr -> (0 until arr.length()).mapNotNull { arr.optString(it)?.trim()?.takeIf(String::isNotEmpty) } }
            ?.distinct()
            ?.take(5)
            ?: emptyList()
        if (options.size < 2) return null
        return ContextQuestion(question = question, options = options)
    }

    /** One food's AI text from a batch reply — the score itself is computed deterministically elsewhere. */
    data class ItemAiText(
        val swaps: List<HealthSwap>,
        val energy: String,
        val energyScore: Int,
        val mood: String,
        val moodScore: Int,
        val pairings: List<String>
    ) {
        companion object { val EMPTY = ItemAiText(emptyList(), "", 0, "", 0, emptyList()) }
    }

    /**
     * Parse the JSON array from [batchItemInsights][FoodPrompts.batchItemInsights] into one
     * [ItemAiText] per food, aligned to [presentPerItem] (each item's food name + its ingredients,
     * for grounding swaps). Always returns exactly `presentPerItem.size` entries — a missing/short
     * reply yields [ItemAiText.EMPTY] for the gaps, so the deterministic score still shows.
     */
    fun parseBatchItemInsights(raw: String, presentPerItem: List<List<String>>): List<ItemAiText> {
        val arr = firstJsonArray(raw)
        return presentPerItem.indices.map { i ->
            val o = arr?.optJSONObject(i) ?: return@map ItemAiText.EMPTY
            val swaps = o.optJSONArray("swaps")?.let { s ->
                (0 until s.length()).mapNotNull { j ->
                    val sw = s.optJSONObject(j) ?: return@mapNotNull null
                    val from = sw.optString("from").trim()
                    val to = sw.optString("to").trim()
                    if (from.isNotEmpty() && to.isNotEmpty()) HealthSwap(from, to, sw.optString("why").trim()) else null
                }
            } ?: emptyList()
            val pairings = o.optJSONArray("pairings")?.let { p ->
                (0 until p.length()).mapNotNull { j -> p.optString(j).trim().takeIf { it.isNotEmpty() } }
            } ?: emptyList()
            ItemAiText(
                swaps = groundSwaps(swaps, presentPerItem[i]),
                energy = o.optString("energy").trim(),
                energyScore = o.optInt("energyScore", 0).coerceIn(0, 5),
                mood = o.optString("mood").trim(),
                moodScore = o.optInt("moodScore", 0).coerceIn(0, 5),
                pairings = pairings
            )
        }
    }

    /** First top-level `[ … ]` in the text, tolerant of prose/code fences around it. */
    private fun firstJsonArray(raw: String): JSONArray? {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        return runCatching { JSONArray(raw.substring(start, end + 1)) }.getOrNull()
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
        // A ready-made sauce the model broke into its recipe goes back to being one item.
        if (ingredients.size > 1 && com.fitpal.app.domain.Condiments.isCondiment(label)) {
            ingredients = listOf(collapseCondiment(label, ingredients))
        }
        val drink = drinkFlag(obj, label)
        return buildFood(
            label = label,
            grams = obj.optDouble("grams", 0.0).toFloat(),
            ingredients = withDrinkWater(ingredients, drink),
            variations = parseVariations(obj.optJSONArray("variations")),
            isDrink = drink
        )
    }

    /**
     * Is this food a drink? The model's flag, read leniently (true / "true" / 1, a few alternate keys,
     * or "type":"drink"). Left out → the name decides ([Drinks.isDrink]); an explicit "false" only loses
     * to a name that's unmistakably a drink ("Red Bull", "Espresso") — small models get that wrong.
     */
    private fun drinkFlag(obj: JSONObject, name: String): Boolean {
        val explicit: Boolean? = listOf("isDrink", "is_drink", "drink").firstNotNullOfOrNull { k ->
            if (!obj.has(k) || obj.isNull(k)) null
            else when (val v = obj.opt(k)) {
                is Boolean -> v
                is Number -> v.toInt() != 0
                is String -> v.trim().lowercase().let { it == "true" || it == "yes" || it == "1" }
                else -> null
            }
        } ?: obj.optString("type").takeIf { it.isNotBlank() }
            ?.let { it.equals("drink", ignoreCase = true) || it.equals("beverage", ignoreCase = true) }
        return when (explicit) {
            true -> true
            false -> com.fitpal.app.domain.Drinks.isDefinitelyDrink(name)
            null -> com.fitpal.app.domain.Drinks.isDrink(name, null)
        }
    }

    /** A drink the model gave no water for still counts toward hydration — estimated from its macros. */
    /**
     * One item named [name] carrying the combined nutrition of [parts], per 100 g — "Tartar sauce,
     * 30 g" instead of mayonnaise + pickles + capers (see [com.fitpal.app.domain.Condiments]). The
     * parts are already calorie-reconciled, and a weighted average keeps them so.
     */
    private fun collapseCondiment(name: String, parts: List<Ingredient>): Ingredient {
        val grams = parts.sumOf { it.grams.toDouble() }.toFloat()
        if (grams <= 0f) return parts.first().copy(name = name)
        fun per100(total: Double) = (total / grams * 100.0).toFloat()
        val knownCaffeine = parts.mapNotNull { it.caffeineMg }
        return Ingredient(
            name = name,
            grams = grams,
            caloriesPer100g = per100(parts.sumOf { it.calories.toDouble() }),
            proteinPer100g = per100(parts.sumOf { it.protein.toDouble() }),
            fatPer100g = per100(parts.sumOf { it.fat.toDouble() }),
            carbsPer100g = per100(parts.sumOf { it.carbs.toDouble() }),
            fiberPer100g = per100(parts.sumOf { it.fiber.toDouble() }),
            waterMlPer100g = per100(parts.sumOf { it.waterMl.toDouble() }),
            // scaledTo(g) multiplies by g/100, so 10 000/g turns the summed amounts back into per-100 g.
            microsPer100g = parts.map { it.microsPer100g.scaledTo(it.grams) }
                .reduce { a, b -> a + b }
                .scaledTo(10_000f / grams),
            caffeineMgPer100g = if (knownCaffeine.isEmpty()) null else per100(knownCaffeine.sumOf { it.toDouble() })
        )
    }

    private fun withDrinkWater(ingredients: List<Ingredient>, drink: Boolean): List<Ingredient> =
        if (!drink) ingredients
        else ingredients.map { ing ->
            if (ing.waterMlPer100g > 0f) ing
            else ing.copy(
                waterMlPer100g = com.fitpal.app.domain.Drinks.estimateWaterPer100(ing.carbsPer100g, ing.proteinPer100g, ing.fatPer100g)
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
            ),
            // Null (not 0) when the model left caffeine out, so the tracker knows it's unchecked
            // rather than trusting a silent "none" for a coffee.
            caffeineMgPer100g = optNumOrNull(obj, "caffeine", "caffeineMg", "caffeinePer100g")
        )
    }

    /** Like [optNum] but null when none of the keys holds a usable number. */
    private fun optNumOrNull(obj: JSONObject, vararg keys: String): Float? {
        for (k in keys) {
            if (!obj.has(k) || obj.isNull(k)) continue
            val d = obj.optDouble(k, Double.NaN)
            if (!d.isNaN()) return d.toFloat().coerceAtLeast(0f)
            obj.optString(k).trim().toFloatOrNull()?.let { return it.coerceAtLeast(0f) }
        }
        return null
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
