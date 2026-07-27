package com.fitpal.app.data.repository

import com.fitpal.app.data.local.dao.NutritionDao
import com.fitpal.app.data.local.entity.UsdaFoodEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Open Food Facts access: barcode lookup, text search, and country browsing (~4M products,
 * strong European / Central-European coverage — Horalky, Kofola, …). Results are cached locally
 * so re-finding the same product later works offline. Uses the v2 API (the legacy search.pl
 * endpoint returns 503 for bulk use) and retries transient 503s where it's worth waiting.
 */
@Singleton
class BarcodeRepository @Inject constructor(
    private val nutritionDao: NutritionDao
) {
    /** A scanned barcode → product (local cache first, then Open Food Facts). */
    suspend fun lookup(barcode: String): UsdaFoodEntity? = withContext(Dispatchers.IO) {
        val cacheId = offId(barcode)
        nutritionDao.getFoodById(cacheId)?.let { return@withContext it }

        val url = "https://world.openfoodfacts.org/api/v2/product/$barcode.json" +
            "?fields=code,product_name,brands,nutriments,serving_quantity"
        val body = httpGet(url, retries = 2) ?: return@withContext null
        val product = runCatching { JSONObject(body).optJSONObject("product") }.getOrNull()
            ?: return@withContext null
        val entity = parseProductObject(barcode, product)?.copy(fdcId = cacheId) ?: return@withContext null
        nutritionDao.insertAll(listOf(entity))
        entity
    }

    /**
     * Free-text search of Open Food Facts (online). Fails fast (no retries) so typing stays
     * snappy; returns [] if offline / rate-limited. Caches what it finds.
     */
    suspend fun searchOpenFoodFacts(query: String, limit: Int = 12): List<UsdaFoodEntity> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.length < 2) return@withContext emptyList()
            val url = "https://world.openfoodfacts.org/cgi/search.pl" +
                "?search_terms=${URLEncoder.encode(q, "UTF-8")}" +
                "&search_simple=1&action=process&json=1&page_size=$limit" +
                "&fields=code,product_name,brands,nutriments,serving_quantity"
            val body = httpGet(url, retries = 0) ?: return@withContext emptyList()
            val results = parseProductsArray(body)
            if (results.isNotEmpty()) runCatching { nutritionDao.insertAll(results) }
            results
        }

    /** Result of one country page: the products, plus whether the request actually succeeded. */
    data class CountryPage(val products: List<UsdaFoodEntity>, val ok: Boolean)

    /**
     * One page of a country's products from Open Food Facts (v2), most-popular first.
     * [ok] = false means the request failed (vs. a genuinely empty / past-the-end page).
     */
    suspend fun fetchByCountry(country: String, page: Int, pageSize: Int = 100): CountryPage =
        withContext(Dispatchers.IO) {
            val url = "https://world.openfoodfacts.org/api/v2/search" +
                "?countries_tags_en=${URLEncoder.encode(country, "UTF-8")}" +
                "&sort_by=popularity_key&page_size=$pageSize&page=$page" +
                "&fields=code,product_name,brands,nutriments,serving_quantity"
            val body = httpGet(url, retries = 2) ?: return@withContext CountryPage(emptyList(), ok = false)
            CountryPage(parseProductsArray(body), ok = true)
        }

    private fun parseProductsArray(body: String): List<UsdaFoodEntity> = runCatching {
        val arr = JSONObject(body).optJSONArray("products") ?: return@runCatching emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val product = arr.optJSONObject(i) ?: return@mapNotNull null
            val code = product.optString("code").ifBlank { return@mapNotNull null }
            parseProductObject(code, product)?.copy(fdcId = offId(code))
        }
    }.getOrDefault(emptyList())

    private fun offId(code: String): Int = cacheIdFor(code)

    /** The stable local-DB id used to cache a barcode's product, so a saved custom food with this
     *  id is found on the next scan. Public so a manually-added product can reuse it. */
    fun cacheIdFor(barcode: String): Int = ("off:$barcode").hashCode()

    /**
     * GET as text. Retries transient 5xx/timeouts up to [retries] times (back-off); never
     * retries a 4xx (e.g. "product not found"). Returns null on failure.
     */
    private suspend fun httpGet(url: String, retries: Int = 2): String? {
        repeat(retries + 1) { attempt ->
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12_000
                    readTimeout = 12_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "FitPal/0.1 (Android)")
                }
                connection.connect()
                val code = connection.responseCode
                when {
                    code in 200..299 -> return connection.inputStream.bufferedReader().use { it.readText() }
                    code in 400..499 -> return null  // definitive — don't retry
                    // else: 5xx / other → fall through and retry
                }
            } catch (e: Exception) {
                // network / timeout → retry
            } finally {
                connection?.disconnect()
            }
            if (attempt < retries) delay(800L * (attempt + 1))
        }
        return null
    }

    /** Convert one Open Food Facts product object into our food entity (null if no nutrition). */
    private fun parseProductObject(code: String, product: JSONObject): UsdaFoodEntity? {
        val nutriments = product.optJSONObject("nutriments") ?: return null

        val rawName = product.optString("product_name").trim()
        val brand = product.optString("brands").trim().substringBefore(",").trim()
        val name = when {
            rawName.isNotEmpty() && brand.isNotEmpty() -> "$rawName ($brand)"
            rawName.isNotEmpty() -> rawName
            else -> "Product $code"
        }

        val kcal = nutriments.optDouble("energy-kcal_100g", 0.0).toFloat()
        val protein = nutriments.optDouble("proteins_100g", 0.0).toFloat()
        val fat = nutriments.optDouble("fat_100g", 0.0).toFloat()
        val carbs = nutriments.optDouble("carbohydrates_100g", 0.0).toFloat()

        if (kcal <= 0f && protein <= 0f && fat <= 0f && carbs <= 0f) return null

        val serving = product.optDouble("serving_quantity", 0.0).toFloat().takeIf { it > 0f }

        return UsdaFoodEntity(
            fdcId = 0, // replaced with the cache id by the caller
            description = name,
            caloriesPer100g = kcal,
            proteinPer100g = protein,
            fatPer100g = fat,
            carbsPer100g = carbs,
            commonServingGrams = serving,
            foodCategory = "Open Food Facts"
        )
    }
}
