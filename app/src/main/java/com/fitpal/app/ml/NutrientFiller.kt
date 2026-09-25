package com.fitpal.app.ml

import android.util.Log
import com.fitpal.app.data.local.dao.FoodExtrasDao
import com.fitpal.app.data.local.entity.FoodExtrasEntity
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.domain.model.Ingredient
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Fills the gaps in foods that didn't come from the AI.
 *
 * The food database (USDA seeds, Open Food Facts, barcodes) only carries calories + protein/fat/carbs,
 * so a food logged from it arrives with **0 fibre, 0 vitamins, 0 minerals and unknown caffeine** —
 * which quietly drags down fibre and vitamin tracking. This asks the quick AI (the dedicated
 * flash-lite trio, same as [DietaryClassifier] — never the main analysis models' quota) what those
 * foods really contain, in ONE batched call per meal, and caches the answer per food in
 * [FoodExtrasEntity]: the same food logged again is filled from the cache instantly, offline, with no
 * AI at all.
 *
 * Rules that keep it honest:
 *  - **Only zeros are filled.** A value the database (or a label) gave is never changed.
 *  - **Only database foods get the full check.** A food you typed in or snapped from a label keeps
 *    your values; it only gets the caffeine check (and only while the caffeine tracker is on).
 *  - Fails **open and quiet**: offline, keyless or out of quota it just leaves the gaps (the worker
 *    retries later); it never blocks or breaks a log.
 */
@Singleton
class NutrientFiller @Inject constructor(
    private val extrasDao: FoodExtrasDao,
    private val gemini: GeminiClient,
    private val settings: SettingsRepository,
    private val networkMonitor: NetworkMonitor
) {
    /** What an ingredient still needs. */
    enum class Need { NONE, FULL, CAFFEINE }

    /** One food to ask the AI about. [key] is the cache key it'll be stored under. */
    data class Target(
        val key: String,
        val ingredient: Ingredient,
        val isDrink: Boolean,
        /** The fields to ask for (AI JSON keys, see [ALL_FIELDS]). */
        val fields: List<String>
    )

    /**
     * Whether the check could ever run — online AI switched on with a key. Without that there's no
     * point queueing work (it would only retry and give up); cache hits still apply either way.
     */
    fun isConfigured(): Boolean = settings.onlineAiEnabled.value && gemini.hasKey() && settings.activeFastModels().isNotEmpty()

    /** Whether the quick trio can run right now (key + master switch + network + some quota). */
    fun canUseOnline(): Boolean =
        settings.onlineAiEnabled.value &&
            gemini.hasKey() &&
            networkMonitor.isOnline() &&
            settings.activeFastModels().any { !settings.isModelQuotaExhaustedToday(it) }

    // ---- Cache keys ----

    fun dbKey(fdcId: Int): String = "db:$fdcId"

    /** Name + rounded density, so "Coffee" black (2 kcal) and "Coffee" latte (50 kcal) stay apart. */
    fun nameKey(ing: Ingredient): String =
        "name:${ing.name.trim().lowercase()}|${(ing.caloriesPer100g / 5f).roundToInt() * 5}"

    // ---- Applying cached answers (no network) ----

    /**
     * Fill every ingredient from the cache where an answer exists — database foods get their zero
     * fields + caffeine, anything else just its unknown caffeine. Ingredients with nothing cached come
     * back unchanged (the background check handles them).
     */
    suspend fun applyCached(ingredients: List<Ingredient>): List<Ingredient> = ingredients.map { ing ->
        val src = ing.sourceFoodId
        when {
            src != null -> cached(dbKey(src))?.let { ing.filledWith(it, zeroFill = true) } ?: ing
            ing.caffeineMgPer100g == null -> cached(nameKey(ing))?.let { ing.filledWith(it, zeroFill = false) } ?: ing
            else -> ing
        }
    }

    /** What [ing] still needs, given the cache. [caffeineTracking] gates the caffeine-only check. */
    suspend fun needOf(ing: Ingredient, caffeineTracking: Boolean): Need {
        val src = ing.sourceFoodId
        return when {
            src != null && cached(dbKey(src)) == null -> Need.FULL
            src == null && ing.caffeineMgPer100g == null && caffeineTracking &&
                cached(nameKey(ing))?.containsKey(CAFFEINE) != true -> Need.CAFFEINE
            else -> Need.NONE
        }
    }

    /** Build the AI target for an ingredient that needs a check. */
    fun targetFor(ing: Ingredient, isDrink: Boolean, need: Need): Target? = when (need) {
        Need.NONE -> null
        Need.CAFFEINE -> Target(nameKey(ing), ing, isDrink, listOf(CAFFEINE))
        Need.FULL -> {
            val missing = ALL_FIELDS.filter { key -> ing.valueOf(key) == 0f } +
                (if (ing.caffeineMgPer100g == null) listOf(CAFFEINE) else emptyList())
            Target(dbKey(ing.sourceFoodId ?: 0), ing, isDrink, missing.distinct())
        }
    }

    // ---- The AI check ----

    /**
     * Ask the quick AI about [targets] (batched, ≤ [BATCH] per call), cache every answer, and return
     * them keyed by cache key. Null when the AI couldn't run at all (offline / no key / out of
     * quota / failed) — the caller should retry later.
     */
    suspend fun check(targets: List<Target>): Map<String, Map<String, Float>>? {
        if (targets.isEmpty()) return emptyMap()
        if (!canUseOnline()) return null
        val unique = targets.distinctBy { it.key }
        val results = HashMap<String, Map<String, Float>>()
        var anyFailed = false
        for (chunk in unique.chunked(BATCH)) {
            var model: String? = null
            val raw = withTimeoutOrNull(TIMEOUT_MS) {
                runCatching {
                    gemini.generate(
                        prompt = prompt(chunk),
                        temperature = 0.1f,
                        jsonMode = true,
                        thinkingLevel = null,          // lite models are non-thinking
                        models = settings.activeFastModels(),
                        onModel = { model = it },
                        fast = true
                    )
                }.onFailure { Log.d(TAG, "Nutrient check failed: ${it.message}") }.getOrNull()
            }
            if (raw == null) { anyFailed = true; continue }
            val parsed = parse(raw, chunk)
            chunk.forEachIndexed { i, target ->
                val values = parsed.getOrNull(i)?.takeIf { it.isNotEmpty() } ?: return@forEachIndexed
                val clean = sanitise(values, target.ingredient)
                results[target.key] = clean
                store(target.key, clean, model)
                // A database food is also cached by name, so re-logging it from the collection (where
                // the database link is gone) still finds the answer.
                if (target.key.startsWith("db:")) store(nameKey(target.ingredient), clean, model)
            }
        }
        return if (results.isEmpty() && anyFailed) null else results
    }

    private suspend fun cached(key: String): Map<String, Float>? =
        extrasDao.get(key)?.let { decode(it.valuesJson) }

    private suspend fun store(key: String, values: Map<String, Float>, model: String?) {
        // Merge with anything already cached under this key (a caffeine-only answer + a later full one).
        val merged = (cached(key).orEmpty() + values)
        runCatching { extrasDao.upsert(FoodExtrasEntity(key, encode(merged), model)) }
    }

    private fun prompt(targets: List<Target>): String = buildString {
        append("You fill in missing nutrition data for foods, mostly from a food database.\n")
        append("For EACH food below give its values per 100 g (per 100 ml for drinks). The known values ")
        append("are listed so you can tell exactly which product it is — don't change them. For every ")
        append("field listed as missing, give the real typical amount; use 0 ONLY when the food genuinely ")
        append("contains none of it (no fibre in cola, no caffeine in rice). Fibre can never exceed carbs.\n\n")
        append("Foods:\n")
        targets.forEachIndexed { i, t ->
            val ing = t.ingredient
            val unit = if (t.isDrink) "100 ml" else "100 g"
            append(i + 1).append(". \"").append(ing.name.take(120).replace("\"", "'")).append("\" — ")
            append(if (t.isDrink) "drink" else "food").append(". Known per ").append(unit).append(": ")
            append(ing.caloriesPer100g.roundToInt()).append(" kcal, protein ").append(fmt(ing.proteinPer100g))
            append(" g, fat ").append(fmt(ing.fatPer100g)).append(" g, carbs ").append(fmt(ing.carbsPer100g)).append(" g")
            if (ing.fiberPer100g > 0f) append(", fibre ").append(fmt(ing.fiberPer100g)).append(" g")
            append(". Missing: ").append(t.fields.joinToString(", ")).append(".\n")
        }
        append("\nUnits: kcal; protein/fat/carbs/fiber in g; water in ml; caffeine in mg (brewed coffee 40, ")
        append("espresso 210, energy drink 32, cola 10, black tea 20, green tea 12, dark chocolate 60); ")
        append("vitA/vitD/b12/folate in mcg; vitC/calcium/iron/potassium/sodium/b6/magnesium/zinc/vitE in mg.\n")
        append("Reply with ONLY a JSON array of ").append(targets.size)
        append(" objects, one per food in the same order, each holding exactly that food's missing keys ")
        append("with numbers. Example for two foods: [{\"fiber\":0,\"water\":98,\"caffeine\":32},{\"caffeine\":0}]")
    }

    private fun parse(raw: String, chunk: List<Target>): List<Map<String, Float>> {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        val arr = runCatching { JSONArray(raw.substring(start, end + 1)) }.getOrNull() ?: return emptyList()
        return chunk.indices.map { i ->
            val o = arr.optJSONObject(i) ?: return@map emptyMap()
            chunk[i].fields.mapNotNull { key ->
                if (!o.has(key) || o.isNull(key)) return@mapNotNull null
                val d = o.optDouble(key, Double.NaN)
                val v = if (!d.isNaN()) d.toFloat() else o.optString(key).trim().toFloatOrNull()
                v?.let { key to it }
            }.toMap()
        }
    }

    /** Clamp to physically plausible per-100 g values (models occasionally slip a unit). */
    private fun sanitise(values: Map<String, Float>, ing: Ingredient): Map<String, Float> =
        values.mapValues { (key, raw) ->
            val v = raw.coerceAtLeast(0f)
            when (key) {
                "kcal" -> v.coerceAtMost(900f)
                "protein", "fat", "carbs" -> v.coerceAtMost(100f)
                // Fibre is part of carbs — never let it exceed them when carbs are known.
                "fiber" -> if (ing.carbsPer100g > 0f) v.coerceAtMost(ing.carbsPer100g) else v.coerceAtMost(100f)
                "water" -> v.coerceAtMost(100f)
                CAFFEINE -> v.coerceAtMost(4000f)
                else -> v
            }
        }

    private fun fmt(v: Float): String =
        if (v == v.roundToInt().toFloat()) v.roundToInt().toString() else "%.1f".format(v)

    companion object {
        private const val TAG = "NutrientFiller"
        private const val BATCH = 10
        // Just above GeminiClient's fast-mode socket timeouts (~8 s connect + 12 s read).
        private const val TIMEOUT_MS = 24_000L
        const val CAFFEINE = "caffeine"

        /** Every per-100 g field the check can fill (AI JSON keys). */
        val ALL_FIELDS = listOf(
            "kcal", "protein", "fat", "carbs", "fiber", "water",
            "vitA", "vitC", "vitD", "calcium", "iron", "potassium", "sodium",
            "b12", "folate", "b6", "magnesium", "zinc", "vitE"
        )

        fun encode(values: Map<String, Float>): String =
            JSONObject().apply { values.forEach { (k, v) -> put(k, v.toDouble()) } }.toString()

        fun decode(json: String?): Map<String, Float>? {
            if (json.isNullOrBlank()) return null
            return runCatching {
                val o = JSONObject(json)
                buildMap { o.keys().forEach { k -> put(k, o.optDouble(k, 0.0).toFloat()) } }
            }.getOrNull()
        }
    }
}

/** An ingredient's current per-100 g value for one of [NutrientFiller.ALL_FIELDS]. */
private fun Ingredient.valueOf(key: String): Float = when (key) {
    "kcal" -> caloriesPer100g
    "protein" -> proteinPer100g
    "fat" -> fatPer100g
    "carbs" -> carbsPer100g
    "fiber" -> fiberPer100g
    "water" -> waterMlPer100g
    "vitA" -> microsPer100g.vitaminAMcg
    "vitC" -> microsPer100g.vitaminCMg
    "vitD" -> microsPer100g.vitaminDMcg
    "calcium" -> microsPer100g.calciumMg
    "iron" -> microsPer100g.ironMg
    "potassium" -> microsPer100g.potassiumMg
    "sodium" -> microsPer100g.sodiumMg
    "b12" -> microsPer100g.vitaminB12Mcg
    "folate" -> microsPer100g.folateMcg
    "b6" -> microsPer100g.vitaminB6Mg
    "magnesium" -> microsPer100g.magnesiumMg
    "zinc" -> microsPer100g.zincMg
    "vitE" -> microsPer100g.vitaminEMg
    else -> 0f
}

/**
 * Fill an ingredient from per-100 g [values]: with [zeroFill], every field that's 0 takes the
 * answer (fields that already have a value are never touched); caffeine is set whenever unknown.
 */
fun Ingredient.filledWith(values: Map<String, Float>, zeroFill: Boolean): Ingredient {
    fun f(current: Float, key: String): Float = if (zeroFill && current == 0f) values[key] ?: current else current
    val m = microsPer100g
    return copy(
        caloriesPer100g = f(caloriesPer100g, "kcal"),
        proteinPer100g = f(proteinPer100g, "protein"),
        fatPer100g = f(fatPer100g, "fat"),
        carbsPer100g = f(carbsPer100g, "carbs"),
        fiberPer100g = f(fiberPer100g, "fiber"),
        waterMlPer100g = f(waterMlPer100g, "water"),
        microsPer100g = m.copy(
            vitaminAMcg = f(m.vitaminAMcg, "vitA"),
            vitaminCMg = f(m.vitaminCMg, "vitC"),
            vitaminDMcg = f(m.vitaminDMcg, "vitD"),
            calciumMg = f(m.calciumMg, "calcium"),
            ironMg = f(m.ironMg, "iron"),
            potassiumMg = f(m.potassiumMg, "potassium"),
            sodiumMg = f(m.sodiumMg, "sodium"),
            vitaminB12Mcg = f(m.vitaminB12Mcg, "b12"),
            folateMcg = f(m.folateMcg, "folate"),
            vitaminB6Mg = f(m.vitaminB6Mg, "b6"),
            magnesiumMg = f(m.magnesiumMg, "magnesium"),
            zincMg = f(m.zincMg, "zinc"),
            vitaminEMg = f(m.vitaminEMg, "vitE")
        ),
        caffeineMgPer100g = caffeineMgPer100g ?: values[NutrientFiller.CAFFEINE]
    )
}
