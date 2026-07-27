package com.fitpal.app.data

import android.content.Context
import android.net.Uri
import com.fitpal.app.data.local.FitPalDatabase
import com.fitpal.app.data.local.dao.NutritionDao
import com.fitpal.app.data.local.entity.UsdaFoodEntity
import com.fitpal.app.data.repository.BarcodeRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Download/import state of the full USDA nutrition database. */
sealed interface NutritionDbStatus {
    data object NotImported : NutritionDbStatus
    data class Downloading(val progress: Float, val label: String = "") : NutritionDbStatus
    data class Importing(val label: String = "") : NutritionDbStatus
    data class Ready(val foodCount: Int) : NutritionDbStatus
    data class Failed(val message: String) : NutritionDbStatus
}

/**
 * Downloads several official USDA datasets and imports them on-device:
 *  - SR Legacy (~7,800 generic whole foods, frozen 2018 release)
 *  - FNDDS / Survey (~7,000 prepared & mixed dishes)
 *  - Foundation Foods (newer detailed entries)
 *
 * All are ungated CSV ZIPs. Each is streamed and parsed for energy/protein/fat/
 * carbs, then bulk-inserted. (Branded foods — ~1.9M products — are a separate,
 * much heavier import handled elsewhere.)
 */
@Singleton
class NutritionImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val nutritionDao: NutritionDao,
    private val database: FitPalDatabase,
    private val barcodeRepository: BarcodeRepository
) {
    private data class Dataset(val label: String, val url: String)

    private val datasets = listOf(
        Dataset("Generic foods", "${BASE}FoodData_Central_sr_legacy_food_csv_2018-04.zip"),
        Dataset("Prepared dishes", "${BASE}FoodData_Central_survey_food_csv_2024-10-31.zip"),
        Dataset("Detailed foods", "${BASE}FoodData_Central_foundation_food_csv_2026-04-30.zip")
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running = false

    private val _status = MutableStateFlow<NutritionDbStatus>(NutritionDbStatus.NotImported)
    val status: StateFlow<NutritionDbStatus> = _status.asStateFlow()

    private var brandedRunning = false
    private val _brandedStatus = MutableStateFlow<NutritionDbStatus>(NutritionDbStatus.NotImported)
    val brandedStatus: StateFlow<NutritionDbStatus> = _brandedStatus.asStateFlow()

    private var extraRunning = false
    private val _extraStatus = MutableStateFlow<NutritionDbStatus>(NutritionDbStatus.NotImported)
    val extraStatus: StateFlow<NutritionDbStatus> = _extraStatus.asStateFlow()

    // Resume position for the (rate-limited) European download, so re-tapping continues.
    private val offPrefs = context.getSharedPreferences("fitpal_off_import", Context.MODE_PRIVATE)

    suspend fun refreshExtraStatus() {
        if (extraRunning) return
        val count = nutritionDao.countByCategory(OFF_CATEGORY)
        if (count > 0) _extraStatus.value = NutritionDbStatus.Ready(count)
    }

    suspend fun refreshStatus() {
        if (running) return
        val count = nutritionDao.getCount()
        if (count > FULL_DB_THRESHOLD) {
            _status.value = NutritionDbStatus.Ready(count)
        }
    }

    suspend fun refreshBrandedStatus() {
        if (brandedRunning) return
        val count = nutritionDao.getCount()
        if (count > BRANDED_THRESHOLD) {
            _brandedStatus.value = NutritionDbStatus.Ready(count)
        }
    }

    fun startImport() {
        synchronized(this) {
            if (running) return
            running = true
        }
        scope.launch {
            try {
                runImport()
            } catch (e: Exception) {
                _status.value = NutritionDbStatus.Failed(e.message ?: "Import failed")
            } finally {
                synchronized(this) { running = false }
            }
        }
    }

    private suspend fun runImport() {
        val descriptions = HashMap<Int, String>()
        val nutrients = HashMap<Int, FloatArray>()

        datasets.forEachIndexed { index, dataset ->
            val zipFile = File(context.cacheDir, "usda_$index.zip")
            val label = "${dataset.label} (${index + 1}/${datasets.size})"
            downloadZip(dataset.url, zipFile) { progress ->
                _status.value = NutritionDbStatus.Downloading(progress, label)
            }
            _status.value = NutritionDbStatus.Importing(label)
            parseZip(zipFile, descriptions, nutrients)
            zipFile.delete()
        }

        // Build entities (keep foods that have at least one nutrient value).
        val entities = ArrayList<UsdaFoodEntity>(descriptions.size)
        for ((fdcId, desc) in descriptions) {
            val n = nutrients[fdcId] ?: continue
            if (n[0] <= 0f && n[1] <= 0f && n[2] <= 0f && n[3] <= 0f) continue
            entities.add(
                UsdaFoodEntity(
                    fdcId = fdcId,
                    description = desc,
                    caloriesPer100g = n[0],
                    proteinPer100g = n[1],
                    fatPer100g = n[2],
                    carbsPer100g = n[3],
                    commonServingGrams = null,
                    foodCategory = null
                )
            )
        }
        entities.chunked(2000).forEach { batch -> nutritionDao.insertAll(batch) }

        _status.value = NutritionDbStatus.Ready(nutritionDao.getCount())
    }

    // ---- Branded foods (~1.9M products) — separate, heavy, opt-in import ----

    fun startBrandedImport() {
        synchronized(this) {
            if (brandedRunning) return
            brandedRunning = true
        }
        scope.launch {
            try {
                runBrandedImport()
            } catch (e: Exception) {
                _brandedStatus.value = NutritionDbStatus.Failed(e.message ?: "Import failed")
            } finally {
                synchronized(this) { brandedRunning = false }
            }
        }
    }

    private suspend fun runBrandedImport() {
        val zipFile = File(context.cacheDir, "usda_branded.zip")
        downloadZip(BRANDED_URL, zipFile) { progress ->
            _brandedStatus.value = NutritionDbStatus.Downloading(progress, "Branded foods")
        }

        // Two passes over the cached zip keep memory bounded: first the nutrients
        // map, then stream the foods and insert in batches.
        _brandedStatus.value = NutritionDbStatus.Importing("Reading nutrients")
        val nutrients = parseBrandedNutrients(zipFile)

        _brandedStatus.value = NutritionDbStatus.Importing("Adding foods")
        parseBrandedFoods(zipFile, nutrients) { inserted ->
            _brandedStatus.value = NutritionDbStatus.Importing("Added $inserted foods")
        }
        zipFile.delete()

        _brandedStatus.value = NutritionDbStatus.Importing("Building search index")
        try {
            buildFtsIndex()
        } catch (e: Exception) {
            // Index build failed — foods are still imported; search falls back to LIKE.
        }

        _brandedStatus.value = NutritionDbStatus.Ready(nutritionDao.getCount())
    }

    // ---- European foods: automatic in-app download from Open Food Facts ----

    /**
     * Download the most popular Slovak / Czech / Polish products from Open Food Facts straight
     * into the offline food database (no PC script needed). One-time; offline afterwards.
     */
    fun startEuropeanDownload() {
        synchronized(this) {
            if (extraRunning) return
            extraRunning = true
        }
        scope.launch {
            try {
                runEuropeanDownload()
            } catch (e: Exception) {
                _extraStatus.value = NutritionDbStatus.Failed(e.message ?: "Download failed")
            } finally {
                synchronized(this) { extraRunning = false }
            }
        }
    }

    private suspend fun runEuropeanDownload() {
        val countries = listOf("slovakia", "czech-republic", "poland")
        val totalPages = countries.size * MAX_PAGES_PER_COUNTRY

        // Resume where the last run stopped (Open Food Facts rate-limits search to ~10/min, so a
        // single run only gets so far before it's throttled — tapping again continues from here).
        var ci = offPrefs.getInt("country_index", 0)
        var page = offPrefs.getInt("page", 1).coerceAtLeast(1)
        if (ci >= countries.size) { ci = 0; page = 1 }  // already finished a full pass → start fresh

        val batch = ArrayList<UsdaFoodEntity>(BATCH_SIZE)

        outer@ while (ci < countries.size) {
            val country = countries[ci]
            while (page <= MAX_PAGES_PER_COUNTRY) {
                _extraStatus.value = NutritionDbStatus.Downloading(
                    ((ci * MAX_PAGES_PER_COUNTRY + page).toFloat() / totalPages).coerceIn(0f, 1f),
                    "regional foods"
                )
                val result = barcodeRepository.fetchByCountry(country, page)
                if (!result.ok) break@outer            // rate-limited — stop here, resume next tap
                if (result.products.isEmpty()) break   // end of this country
                batch.addAll(result.products)
                if (batch.size >= BATCH_SIZE) {
                    nutritionDao.insertAllBlocking(batch)
                    batch.clear()
                }
                page++
                delay(700)  // pace politely (their search API throttles aggressively)
            }
            // Only advance to the next country if we exhausted this one (not if throttled).
            ci++; page = 1
        }
        if (batch.isNotEmpty()) nutritionDao.insertAllBlocking(batch)

        // Remember where to resume next time.
        offPrefs.edit().putInt("country_index", ci).putInt("page", page).apply()

        val count = nutritionDao.countByCategory(OFF_CATEGORY)
        if (count == 0) {
            _extraStatus.value = NutritionDbStatus.Failed(
                "Couldn't reach Open Food Facts. Check your internet connection and try again."
            )
            return
        }
        _extraStatus.value = NutritionDbStatus.Importing("Building search index")
        try {
            buildFtsIndex()
        } catch (e: Exception) {
            // Index build failed — foods are still imported; search falls back to LIKE.
        }
        // addedThisRun is referenced so a throttled stop is distinguishable in logs; show total.
        _extraStatus.value = NutritionDbStatus.Ready(count)
    }

    // ---- Extra foods from a user-picked CSV (e.g. a bigger Open Food Facts slice) ----

    /**
     * Import foods from a CSV the user generated offline (see tools/build_off_central_europe).
     * Expected columns (header, any order): code, name, kcal_100g, protein_100g, fat_100g,
     * carbs_100g, serving_g. Plain or gzip-compressed. Inserts into the same food table + FTS,
     * so the products are searchable fully offline — and barcode scans of them resolve offline too.
     */
    fun startCsvImport(uri: Uri) {
        synchronized(this) {
            if (extraRunning) return
            extraRunning = true
        }
        scope.launch {
            try {
                runCsvImport(uri)
            } catch (e: Exception) {
                _extraStatus.value = NutritionDbStatus.Failed(e.message ?: "Import failed")
            } finally {
                synchronized(this) { extraRunning = false }
            }
        }
    }

    private fun runCsvImport(uri: Uri) {
        _extraStatus.value = NutritionDbStatus.Importing("Reading file")
        val raw = context.contentResolver.openInputStream(uri) ?: throw IOException("Couldn't open that file")
        val buffered = BufferedInputStream(raw)
        // Transparently handle gzip (magic bytes 1f 8b).
        buffered.mark(2)
        val b0 = buffered.read(); val b1 = buffered.read(); buffered.reset()
        val stream: InputStream = if (b0 == 0x1f && b1 == 0x8b) GZIPInputStream(buffered) else buffered

        stream.bufferedReader().use { reader ->
            val header = parseCsvLine(reader.readLine() ?: throw IOException("The file is empty"))
            fun col(vararg names: String): Int =
                names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } } ?: -1
            val iCode = col("code", "barcode")
            val iName = col("name", "product_name")
            val iKcal = col("kcal_100g", "energy-kcal_100g", "kcal", "calories")
            val iPro = col("protein_100g", "proteins_100g", "protein")
            val iFat = col("fat_100g", "fat")
            val iCarb = col("carbs_100g", "carbohydrates_100g", "carbs")
            val iServ = col("serving_g", "serving_quantity", "serving")
            if (iName < 0 || iKcal < 0) {
                throw IOException("CSV needs at least a name and a kcal column")
            }

            val batch = ArrayList<UsdaFoodEntity>(BATCH_SIZE)
            var inserted = 0
            readLines(reader) { cols ->
                val name = cols.getOrNull(iName)?.trim().orEmpty()
                if (name.isEmpty()) return@readLines
                val kcal = cols.getOrNull(iKcal)?.toFloatOrNull() ?: 0f
                val pro = if (iPro >= 0) cols.getOrNull(iPro)?.toFloatOrNull() ?: 0f else 0f
                val fat = if (iFat >= 0) cols.getOrNull(iFat)?.toFloatOrNull() ?: 0f else 0f
                val carb = if (iCarb >= 0) cols.getOrNull(iCarb)?.toFloatOrNull() ?: 0f else 0f
                if (kcal <= 0f && pro <= 0f && fat <= 0f && carb <= 0f) return@readLines
                val code = if (iCode >= 0) cols.getOrNull(iCode)?.trim().orEmpty() else ""
                val serving = if (iServ >= 0) cols.getOrNull(iServ)?.toFloatOrNull()?.takeIf { it > 0f } else null
                // Same id scheme as barcode scans, so a scan of an imported product resolves offline.
                val id = if (code.isNotEmpty()) ("off:$code").hashCode() else (name + kcal).hashCode()
                batch.add(UsdaFoodEntity(id, name, kcal, pro, fat, carb, serving, "Open Food Facts"))
                if (batch.size >= BATCH_SIZE) {
                    nutritionDao.insertAllBlocking(batch)
                    inserted += batch.size
                    batch.clear()
                    _extraStatus.value = NutritionDbStatus.Importing("Added $inserted foods")
                }
            }
            if (batch.isNotEmpty()) {
                nutritionDao.insertAllBlocking(batch)
                inserted += batch.size
            }

            _extraStatus.value = NutritionDbStatus.Importing("Building search index")
            try {
                buildFtsIndex()
            } catch (e: Exception) {
                // Index build failed — foods are still imported; search falls back to LIKE.
            }
            _extraStatus.value = NutritionDbStatus.Ready(inserted)
        }
    }

    private fun parseBrandedNutrients(zipFile: File): HashMap<Int, FloatArray> {
        val out = HashMap<Int, FloatArray>(1_500_000)
        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name.substringAfterLast('/') == "food_nutrient.csv") {
                    val reader = zip.bufferedReader()
                    val header = parseCsvLine(reader.readLine() ?: break)
                    val idxFdc = header.indexOf("fdc_id")
                    val idxNut = header.indexOf("nutrient_id")
                    val idxAmt = header.indexOf("amount")
                    if (idxFdc >= 0 && idxNut >= 0 && idxAmt >= 0) {
                        readLines(reader) { cols ->
                            val nid = cols.getOrNull(idxNut)?.toIntOrNull() ?: return@readLines
                            val slot = NUTRIENT_SLOTS[nid] ?: return@readLines
                            val fdc = cols.getOrNull(idxFdc)?.toIntOrNull() ?: return@readLines
                            val amt = cols.getOrNull(idxAmt)?.toFloatOrNull() ?: return@readLines
                            out.getOrPut(fdc) { FloatArray(4) }[slot] = amt
                        }
                    }
                    break
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return out
    }

    private fun parseBrandedFoods(
        zipFile: File,
        nutrients: HashMap<Int, FloatArray>,
        onBatch: (Int) -> Unit
    ) {
        val batch = ArrayList<UsdaFoodEntity>(BATCH_SIZE)
        var inserted = 0
        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name.substringAfterLast('/') == "food.csv") {
                    val reader = zip.bufferedReader()
                    val header = parseCsvLine(reader.readLine() ?: break)
                    val idxId = header.indexOf("fdc_id")
                    val idxDesc = header.indexOf("description")
                    if (idxId >= 0 && idxDesc >= 0) {
                        readLines(reader) { cols ->
                            val fdc = cols.getOrNull(idxId)?.toIntOrNull() ?: return@readLines
                            val n = nutrients[fdc] ?: return@readLines
                            if (n[0] <= 0f && n[1] <= 0f && n[2] <= 0f && n[3] <= 0f) return@readLines
                            val desc = cols.getOrNull(idxDesc)?.trim().orEmpty()
                            if (desc.isEmpty()) return@readLines
                            batch.add(UsdaFoodEntity(fdc, desc, n[0], n[1], n[2], n[3], null, "Branded"))
                            if (batch.size >= BATCH_SIZE) {
                                nutritionDao.insertAllBlocking(batch)
                                inserted += batch.size
                                batch.clear()
                                onBatch(inserted)
                            }
                        }
                    }
                    break
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        if (batch.isNotEmpty()) {
            nutritionDao.insertAllBlocking(batch)
            inserted += batch.size
            onBatch(inserted)
        }
    }

    /** Build a full-text search index over all foods so search stays fast at scale. */
    private fun buildFtsIndex() {
        val db = database.openHelper.writableDatabase
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS usda_fts USING fts4(" +
                "description, content='usda_foods', content_rowid='fdcId')"
        )
        db.execSQL("INSERT INTO usda_fts(usda_fts) VALUES('rebuild')")
    }

    private fun parseZip(
        zipFile: File,
        descriptions: HashMap<Int, String>,
        nutrients: HashMap<Int, FloatArray>
    ) {
        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when (entry.name.substringAfterLast('/')) {
                    "food.csv" -> parseFoodCsv(zip, descriptions)
                    "food_nutrient.csv" -> parseFoodNutrientCsv(zip, nutrients)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun downloadZip(url: String, target: File, onProgress: (Float) -> Unit) {
        onProgress(0f)
        var connection: HttpURLConnection? = null
        try {
            if (target.exists()) target.delete()
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "FitPal-Android")
            }
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IOException("Server returned HTTP ${connection.responseCode}")
            }
            val total = if (connection.contentLengthLong > 0) connection.contentLengthLong else -1L
            connection.inputStream.use { input ->
                BufferedOutputStream(FileOutputStream(target)).use { output ->
                    val buffer = ByteArray(1 shl 16)
                    var downloaded = 0L
                    var lastReported = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0 && downloaded - lastReported >= 500_000) {
                            lastReported = downloaded
                            onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                    output.flush()
                }
            }
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseFoodCsv(zip: ZipInputStream, out: HashMap<Int, String>) {
        val reader = zip.bufferedReader()
        val header = parseCsvLine(reader.readLine() ?: return)
        val idxId = header.indexOf("fdc_id")
        val idxDesc = header.indexOf("description")
        if (idxId < 0 || idxDesc < 0) return
        readLines(reader) { cols ->
            val fdcId = cols.getOrNull(idxId)?.toIntOrNull() ?: return@readLines
            val desc = cols.getOrNull(idxDesc)?.trim().orEmpty()
            if (desc.isNotEmpty()) out[fdcId] = desc
        }
    }

    private fun parseFoodNutrientCsv(zip: ZipInputStream, out: HashMap<Int, FloatArray>) {
        val reader = zip.bufferedReader()
        val header = parseCsvLine(reader.readLine() ?: return)
        val idxFdc = header.indexOf("fdc_id")
        val idxNut = header.indexOf("nutrient_id")
        val idxAmt = header.indexOf("amount")
        if (idxFdc < 0 || idxNut < 0 || idxAmt < 0) return
        readLines(reader) { cols ->
            val nutrientId = cols.getOrNull(idxNut)?.toIntOrNull() ?: return@readLines
            val slot = NUTRIENT_SLOTS[nutrientId] ?: return@readLines
            val fdcId = cols.getOrNull(idxFdc)?.toIntOrNull() ?: return@readLines
            val amount = cols.getOrNull(idxAmt)?.toFloatOrNull() ?: return@readLines
            out.getOrPut(fdcId) { FloatArray(4) }[slot] = amount
        }
    }

    private inline fun readLines(reader: BufferedReader, block: (List<String>) -> Unit) {
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) continue
            block(parseCsvLine(line))
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                        sb.append('"'); i++
                    }
                    c == '"' -> inQuotes = false
                    else -> sb.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> { result.add(sb.toString()); sb.setLength(0) }
                else -> sb.append(c)
            }
            i++
        }
        result.add(sb.toString())
        return result
    }

    companion object {
        private const val BASE = "https://fdc.nal.usda.gov/fdc-datasets/"
        private const val FULL_DB_THRESHOLD = 1_000
        private const val BRANDED_THRESHOLD = 500_000
        private const val BATCH_SIZE = 5_000

        // How many 100-product pages to pull per country (most-popular first). 100 ≈ top 10,000.
        private const val MAX_PAGES_PER_COUNTRY = 100

        // foodCategory tag for everything sourced from Open Food Facts (download + scans + search).
        private const val OFF_CATEGORY = "Open Food Facts"
        private const val BRANDED_URL =
            BASE + "FoodData_Central_branded_food_csv_2026-04-30.zip"

        // USDA nutrient ids -> slot in [kcal, protein, fat, carbs]
        private val NUTRIENT_SLOTS = mapOf(
            1008 to 0, // Energy (kcal)
            1003 to 1, // Protein
            1004 to 2, // Total lipid (fat)
            1005 to 3  // Carbohydrate, by difference
        )
    }
}
