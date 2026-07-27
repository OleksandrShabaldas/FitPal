package com.fitpal.app.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import com.fitpal.app.data.local.FoodSeedData
import com.fitpal.app.data.local.dao.NutritionDao
import com.fitpal.app.data.local.entity.UsdaFoodEntity
import com.fitpal.app.domain.model.Ingredient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Access to the USDA nutritional database.
 * Used for manual ingredient search and for looking up nutritional values
 * when the LLM suggests ingredient names.
 */
@Singleton
class NutritionRepository @Inject constructor(
    private val nutritionDao: NutritionDao,
    private val barcodeRepository: BarcodeRepository
) {
    // Words to ignore when matching a free-text description against the food database.
    private val stopWords = setOf(
        "and", "the", "with", "some", "for", "plus", "had", "ate", "was", "were",
        "this", "that", "have", "has", "from", "into", "over", "side", "served",
        "lunch", "dinner", "breakfast", "meal", "plate", "bowl", "cup", "piece"
    )

    // Common words mapped to a food that exists in the database.
    private val synonyms = mapOf(
        "toast" to "bread",
        "steak" to "beef",
        "mince" to "beef",
        "spaghetti" to "pasta",
        "noodles" to "pasta",
        "macaroni" to "pasta",
        "penne" to "pasta",
        "fries" to "french fries",
        "chips" to "french fries",
        "yoghurt" to "yogurt",
        "prawn" to "shrimp",
        "prawns" to "shrimp",
        "oat" to "oats",
        "oatmeal" to "oats",
        "porridge" to "oats",
        "spuds" to "potato",
        "capsicum" to "bell pepper",
        "courgette" to "vegetable",
        "veggies" to "vegetable",
        "cheese" to "cheddar"
    )

    /**
     * Populate the database with the built-in seed foods if it's empty.
     * Safe to call repeatedly — it only inserts on the first call.
     */
    suspend fun ensureSeeded() {
        if (nutritionDao.getCount() == 0) {
            nutritionDao.insertAll(FoodSeedData.foods)
        }
    }

    // Once per process, upsert ALL built-in simple foods (fixed negative ids, REPLACE) so existing
    // installs pick up newly-added ones on the next launch — unlike ensureSeeded's only-if-empty.
    @Volatile private var basicsSeeded = false

    private suspend fun ensureBasicSeeded() {
        if (basicsSeeded) return
        nutritionDao.insertAll(FoodSeedData.foods)
        basicsSeeded = true
    }

    /**
     * Search ONLY the built-in simple, non-branded foods — used everywhere the user "selects from
     * the database" (manual entry, add-ingredient dialog). Branded / Open Food Facts foods are
     * excluded; those come in via barcode scan instead.
     */
    suspend fun searchBasicFoods(query: String, limit: Int = 30): List<UsdaFoodEntity> {
        ensureBasicSeeded()
        return nutritionDao.searchBasicFoods(query.trim(), limit)
    }

    /**
     * Foolproof food search. Tolerant of case, plurals/declensions ("horalka" → "Horalky") and
     * one- or two-letter typos. Strategy:
     *   1. Pull a candidate pool from the full-text index using *lenient* prefixes (each token
     *      also matches with its last 1–2 letters dropped, so suffix changes still hit). Falls
     *      back to a LIKE scan when the FTS index isn't built yet.
     *   2. If nothing matched the prefixes (a typo in the opening letters), widen the net with a
     *      short 3-letter seed.
     *   3. Re-rank everything by fuzzy closeness to the query (edit distance per word) and return
     *      the best matches.
     */
    suspend fun searchFoods(query: String, limit: Int = 20): List<UsdaFoodEntity> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val tokens = tokenize(q)
        if (tokens.isEmpty()) return nutritionDao.searchFoods(q, limit)

        var pool = ftsCandidates(tokens, POOL_SIZE)        // null = FTS index missing
        if (pool == null) pool = likeCandidates(tokens, POOL_SIZE)
        if (pool.isEmpty()) pool = fuzzyFallbackCandidates(tokens, POOL_SIZE)

        return rankByFuzzy(pool, tokens).take(limit)
    }

    private fun tokenize(s: String): List<String> =
        s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }

    /** FTS MATCH with lenient prefixes per token: "horalka" -> "(horalka* OR horalk* OR horal*)". */
    private fun ftsMatchExpr(tokens: List<String>): String =
        tokens.joinToString(" ") { t ->
            val variants = LinkedHashSet<String>()
            variants.add("$t*")
            if (t.length >= 5) variants.add(t.dropLast(1) + "*")
            if (t.length >= 7) variants.add(t.dropLast(2) + "*")
            "(" + variants.joinToString(" OR ") + ")"
        }

    private suspend fun ftsCandidates(tokens: List<String>, poolSize: Int): List<UsdaFoodEntity>? =
        try {
            val sql = "SELECT f.* FROM usda_foods f JOIN usda_fts x ON f.fdcId = x.rowid " +
                "WHERE usda_fts MATCH ? LIMIT ?"
            nutritionDao.searchRaw(SimpleSQLiteQuery(sql, arrayOf(ftsMatchExpr(tokens), poolSize)))
        } catch (e: Exception) {
            null  // FTS table not present yet (branded/full DB not imported)
        }

    /** LIKE scan on the longest token's stem — for the small seed-only DB before FTS exists. */
    private suspend fun likeCandidates(tokens: List<String>, poolSize: Int): List<UsdaFoodEntity> {
        val stem = tokens.maxByOrNull { it.length } ?: return emptyList()
        val prefix = when {
            stem.length >= 6 -> stem.dropLast(2)
            stem.length >= 4 -> stem.dropLast(1)
            else -> stem
        }
        return nutritionDao.searchFoods(prefix, poolSize)
    }

    /** Last resort for an opening-letter typo: a 3-letter seed, then fuzzy-ranked. */
    private suspend fun fuzzyFallbackCandidates(tokens: List<String>, poolSize: Int): List<UsdaFoodEntity> {
        val longest = tokens.maxByOrNull { it.length } ?: return emptyList()
        val seed = longest.take(3)
        if (seed.length < 2) return emptyList()
        return nutritionDao.searchFoods(seed, poolSize)
    }

    /** Sort candidates by fuzzy closeness to the query; drop ones that don't match all words. */
    private fun rankByFuzzy(candidates: List<UsdaFoodEntity>, queryTokens: List<String>): List<UsdaFoodEntity> {
        val scored = ArrayList<Pair<UsdaFoodEntity, Int>>(candidates.size)
        val seen = HashSet<Int>()
        for (food in candidates) {
            if (!seen.add(food.fdcId)) continue
            val s = fuzzyScore(food.description, queryTokens) ?: continue
            scored.add(food to s)
        }
        scored.sortBy { it.second }
        return scored.map { it.first }
    }

    /** Lower = closer. null when a query word has no acceptable match in the name. */
    private fun fuzzyScore(description: String, queryTokens: List<String>): Int? {
        val nameTokens = tokenize(description)
        if (nameTokens.isEmpty()) return null
        var total = 0
        for (qt in queryTokens) {
            var best = Int.MAX_VALUE
            for (nt in nameTokens) {
                val d = tokenDistance(qt, nt)
                if (d < best) best = d
                if (best == 0) break
            }
            if (best > fuzzThreshold(qt.length)) return null
            total += best
        }
        // Tie-break: shorter, simpler names are usually the closer match (e.g. "Apple" > "Apple pie").
        return total * 1000 + description.length.coerceAtMost(999)
    }

    /** A prefix/suffix relation counts as a near-miss (1); otherwise true edit distance. */
    private fun tokenDistance(q: String, n: String): Int {
        if (q == n) return 0
        if (n.startsWith(q) || q.startsWith(n)) return 1
        return levenshtein(q, n)
    }

    /** Allowed typos scale with word length: short words must be near-exact, longer ones tolerant. */
    private fun fuzzThreshold(len: Int): Int = when {
        len <= 3 -> 1
        len <= 6 -> 2
        else -> 3
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
            }
            System.arraycopy(curr, 0, prev, 0, curr.size)
        }
        return prev[b.length]
    }

    /**
     * Best-effort: find foods mentioned in a free-text meal description.
     * Splits the text into words and matches each against the database,
     * handling plurals ("eggs" -> "egg") and common synonyms ("toast" -> "bread").
     * This is a simple keyword matcher — the on-device LLM will replace it later.
     */
    suspend fun findFoodsInText(text: String): List<UsdaFoodEntity> {
        ensureSeeded()
        val tokens = text.lowercase()
            .split(Regex("[^a-z]+"))
            .filter { it.length >= 3 && it !in stopWords }
            .distinct()

        val found = LinkedHashMap<Int, UsdaFoodEntity>()
        for (token in tokens) {
            // Try each variant of the word until one matches a food.
            for (candidate in expandToken(token)) {
                val match = nutritionDao.searchFoods(candidate, limit = 1).firstOrNull()
                if (match != null) {
                    found[match.fdcId] = match
                    break
                }
            }
        }
        return found.values.toList()
    }

    /**
     * Produce search variants for a word: synonym, the word itself, and singular forms.
     * Ordered most-specific first so e.g. "fries" -> "french fries" before "fries".
     */
    private fun expandToken(token: String): List<String> {
        val variants = LinkedHashSet<String>()
        synonyms[token]?.let { variants.add(it) }
        variants.add(token)
        // Singular forms so plurals match (eggs -> egg, tomatoes -> tomato)
        if (token.endsWith("es") && token.length > 4) {
            val singular = token.dropLast(2)
            synonyms[singular]?.let { variants.add(it) }
            variants.add(singular)
        }
        if (token.endsWith("s") && token.length > 3) {
            val singular = token.dropLast(1)
            synonyms[singular]?.let { variants.add(it) }
            variants.add(singular)
        }
        return variants.toList()
    }

    /**
     * Search the local database, and when it returns little (e.g. a regional product like
     * "horalka" that USDA doesn't have), also search Open Food Facts online and merge the
     * results in. Common foods with plenty of local hits skip the network entirely.
     */
    suspend fun searchFoodsOnline(query: String, limit: Int = 20): List<UsdaFoodEntity> {
        // Always keep the built-in simple foods available, so basic search works offline even
        // before the full USDA / European databases are imported.
        ensureBasicSeeded()
        val local = searchFoods(query, limit)
        if (local.size >= 5) return local
        val off = barcodeRepository.searchOpenFoodFacts(query)
        val seenIds = HashSet<Int>()
        val seenNames = HashSet<String>()
        val merged = ArrayList<UsdaFoodEntity>()
        for (food in local + off) {
            val nameKey = food.description.lowercase()
            if (seenIds.add(food.fdcId) && seenNames.add(nameKey)) merged.add(food)
        }
        return merged.take(limit)
    }

    suspend fun getFoodById(fdcId: Int): UsdaFoodEntity? =
        nutritionDao.getFoodById(fdcId)

    /**
     * Look up a food by name and convert it to an Ingredient with a given weight.
     * Returns null if not found in the USDA database.
     */
    suspend fun lookupIngredient(name: String, grams: Float): Ingredient? {
        ensureSeeded()
        val results = nutritionDao.searchFoods(name, limit = 1)
        val food = results.firstOrNull() ?: return null
        return Ingredient(
            name = food.description,
            grams = grams,
            caloriesPer100g = food.caloriesPer100g,
            proteinPer100g = food.proteinPer100g,
            fatPer100g = food.fatPer100g,
            carbsPer100g = food.carbsPer100g
        )
    }

    suspend fun isDatabasePopulated(): Boolean = nutritionDao.getCount() > 0

    /**
     * Save a manually-entered product against a scanned barcode so the NEXT scan resolves it
     * locally (offline). Stored in the food table with the same cache id a barcode lookup uses,
     * and tagged "Custom" so it can be backed up via export/import.
     */
    suspend fun saveCustomBarcodeFood(
        barcode: String,
        name: String,
        caloriesPer100g: Float,
        proteinPer100g: Float,
        fatPer100g: Float,
        carbsPer100g: Float,
        servingGrams: Float?
    ) {
        val entity = UsdaFoodEntity(
            fdcId = barcodeRepository.cacheIdFor(barcode),
            description = name,
            caloriesPer100g = caloriesPer100g,
            proteinPer100g = proteinPer100g,
            fatPer100g = fatPer100g,
            carbsPer100g = carbsPer100g,
            commonServingGrams = servingGrams,
            foodCategory = CUSTOM_CATEGORY
        )
        nutritionDao.insertAll(listOf(entity))
    }

    private companion object {
        /** Candidate pool size pulled before fuzzy re-ranking. Big enough to catch the best match,
         *  small enough that the per-word edit-distance pass stays instant. */
        const val POOL_SIZE = 200

        /** foodCategory for user-added barcode products (so export/import can carry them). */
        const val CUSTOM_CATEGORY = "Custom"
    }
}
