package com.fitpal.app.data.repository

import com.fitpal.app.data.local.MealJson
import com.fitpal.app.data.local.dao.DailyMicros
import com.fitpal.app.data.local.dao.DailyNutritionRow
import com.fitpal.app.data.local.dao.DailyWaterRow
import com.fitpal.app.data.local.dao.DailyWaterSplit
import com.fitpal.app.data.local.dao.LoggedFoodRow
import com.fitpal.app.data.local.dao.MealLogDao
import com.fitpal.app.data.local.dao.MealLogItemWithType
import com.fitpal.app.data.local.entity.MealLogEntity
import com.fitpal.app.data.local.entity.MealLogItemEntity
import com.fitpal.app.domain.model.DetectedFood
import com.fitpal.app.domain.model.Ingredient
import com.fitpal.app.domain.model.MealInsights
import com.fitpal.app.domain.model.Micronutrients
import com.fitpal.app.domain.model.MealTypes
import com.fitpal.app.domain.model.NutritionInfo
import com.fitpal.app.ml.AiSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MealRepository @Inject constructor(
    private val mealLogDao: MealLogDao,
    private val settingsRepository: SettingsRepository
) {
    private val dateFormat = DateTimeFormatter.ISO_LOCAL_DATE

    fun todayString(): String = LocalDate.now().format(dateFormat)

    fun getMealsForDate(date: String): Flow<List<MealLogEntity>> =
        mealLogDao.getMealsForDate(date)

    fun getItemsForDate(date: String): Flow<List<MealLogItemEntity>> =
        mealLogDao.getAllItemsForDate(date)

    /** Today's items tagged with their meal category, for grouped display. */
    fun getItemsWithTypeForDate(date: String): Flow<List<MealLogItemWithType>> =
        mealLogDao.getItemsWithTypeForDate(date)

    /** The meal category for right now, per the user's configurable meal-time windows (#3). */
    fun defaultMealType(): String = settingsRepository.mealTypeForNow()

    /**
     * Get today's nutritional totals as a live-updating flow.
     */
    fun getDailyNutrition(date: String): Flow<NutritionInfo> = combine(
        mealLogDao.getTotalCaloriesForDate(date),
        mealLogDao.getTotalProteinForDate(date),
        mealLogDao.getTotalFatForDate(date),
        mealLogDao.getTotalCarbsForDate(date),
        mealLogDao.getTotalFiberForDate(date)
    ) { cal, pro, fat, carb, fib ->
        NutritionInfo(calories = cal, protein = pro, fat = fat, carbs = carb, fiber = fib)
    }

    /** Daily micronutrient totals (all seven in one query). */
    fun getDailyMicros(date: String): Flow<DailyMicros> =
        mealLogDao.getDailyMicrosForDate(date)

    /** Summed micronutrients across a date range — analytics deficiency breakdown. */
    fun getMicrosForRange(from: String, to: String): Flow<DailyMicros> =
        mealLogDao.getMicrosForRange(from, to)

    /** Per-day nutrition rows for a date range — drives analytics charts. */
    fun getDailyNutritionRange(from: String, to: String): Flow<List<DailyNutritionRow>> =
        mealLogDao.getDailyNutritionRange(from, to)

    /** Every logged food in a date range (name + calories + day) — so the AI review sees the meals. */
    suspend fun getLoggedFoodsInRange(from: String, to: String): List<LoggedFoodRow> =
        mealLogDao.getLoggedFoodsInRange(from, to)

    /** Per-day water totals for a date range — analytics water chart. */
    fun getDailyWaterRange(from: String, to: String): Flow<List<DailyWaterRow>> =
        mealLogDao.getDailyWaterRange(from, to)

    /** Per-day water split into clear drinks vs. water from food. */
    fun getDailyWaterSplitRange(from: String, to: String): Flow<List<DailyWaterSplit>> =
        mealLogDao.getDailyWaterSplitRange(from, to)

    /** Days (newest first) the user logged real food — for the streak / garden. */
    fun getLoggedDatesDesc(): Flow<List<String>> = mealLogDao.getLoggedDatesDesc()

    /** Recently logged distinct foods (newest first) — the Add screen's one-tap "Your usuals" row. */
    fun recentDistinctFoods(limit: Int = 12): Flow<List<MealLogItemEntity>> =
        mealLogDao.getRecentDistinctItems(limit)

    /**
     * Re-log a previously logged item as a fresh one-food meal (one tap from "Your usuals"). Uses
     * the saved ingredient breakdown when present so it re-logs as the same composite dish, else
     * rebuilds a single ingredient from the item's totals.
     */
    suspend fun quickLogRecent(item: MealLogItemEntity, mealType: String, date: String? = null): Long {
        val ingredients = ingredientsForItem(item).ifEmpty { listOf(itemAsIngredient(item)) }
        val food = DetectedFood(
            label = item.name,
            confidence = 1f,
            boundingBox = android.graphics.RectF(),
            estimatedGrams = item.grams,
            ingredients = ingredients,
            isDrink = item.isDrink
        )
        return logMeal(listOf(food), mealType, date = date)
    }

    /** Rebuild a single per-100g ingredient from a logged item that has no saved breakdown. */
    private fun itemAsIngredient(item: MealLogItemEntity): Ingredient {
        val g = item.grams.takeIf { it > 0f } ?: 100f
        val f = 100f / g
        return Ingredient(
            name = item.name,
            grams = item.grams,
            caloriesPer100g = item.calories * f,
            proteinPer100g = item.protein * f,
            fatPer100g = item.fat * f,
            carbsPer100g = item.carbs * f,
            fiberPer100g = item.fiber * f,
            waterMlPer100g = item.waterMl * f,
            isDrink = item.isDrink,
            microsPer100g = Micronutrients(
                vitaminAMcg = item.vitaminA * f,
                vitaminCMg = item.vitaminC * f,
                vitaminDMcg = item.vitaminD * f,
                calciumMg = item.calcium * f,
                ironMg = item.iron * f,
                potassiumMg = item.potassium * f,
                sodiumMg = item.sodium * f,
                vitaminB12Mcg = item.vitaminB12 * f,
                folateMcg = item.folate * f,
                vitaminB6Mg = item.vitaminB6 * f,
                magnesiumMg = item.magnesium * f,
                zincMg = item.zinc * f,
                vitaminEMg = item.vitaminE * f
            )
        )
    }

    /**
     * Log a list of detected foods as a meal (e.g. everything on the plate).
     */
    suspend fun logMeal(
        foods: List<DetectedFood>,
        mealType: String,
        photoPath: String? = null,
        insights: MealInsights? = null,
        date: String? = null,
        source: AiSource? = null
    ): Long {
        val logDate = date ?: todayString()
        val mealLog = MealLogEntity(date = logDate, mealType = mealType)
        // Carry the meal's AI analysis onto each item so opening it later shows the
        // saved review instead of regenerating.
        val insightsJson = insights?.let { MealJson.encodeInsights(it) }
        val insightsAt = if (insights != null) System.currentTimeMillis() else 0L
        val items = foods.map { food ->
            val micros = food.totalMicros
            MealLogItemEntity(
                mealLogId = 0, // set by DAO
                name = food.label,
                grams = food.totalGrams,
                calories = food.totalCalories,
                protein = food.totalProtein,
                fat = food.totalFat,
                carbs = food.totalCarbs,
                fiber = food.totalFiber,
                isDrink = food.isDrink,
                // Count water from every source — drinks AND watery foods (fruit, soup…).
                waterMl = food.totalWaterMl,
                vitaminA = micros.vitaminAMcg,
                vitaminC = micros.vitaminCMg,
                vitaminD = micros.vitaminDMcg,
                calcium = micros.calciumMg,
                iron = micros.ironMg,
                potassium = micros.potassiumMg,
                sodium = micros.sodiumMg,
                vitaminB12 = micros.vitaminB12Mcg,
                folate = micros.folateMcg,
                vitaminB6 = micros.vitaminB6Mg,
                magnesium = micros.magnesiumMg,
                zinc = micros.zincMg,
                vitaminE = micros.vitaminEMg,
                photoPath = photoPath,
                // Keep the ingredient breakdown so the detail screen can show/edit it.
                ingredientsJson = MealJson.encodeIngredients(food.ingredients),
                insightsJson = insightsJson,
                insightsGeneratedAt = insightsAt,
                aiSource = source?.name
            )
        }
        return mealLogDao.logMealWithItems(mealLog, items)
    }

    /**
     * Log a list of individual food items as one meal.
     * Used by Manual Entry and Describe-to-AI, where each item is a single ingredient.
     */
    suspend fun logItems(
        items: List<Ingredient>,
        mealType: String,
        date: String? = null
    ): Long {
        val logDate = date ?: todayString()
        val mealLog = MealLogEntity(date = logDate, mealType = mealType)
        val logItems = items.map { ingredient ->
            val m = ingredient.micros
            MealLogItemEntity(
                mealLogId = 0, // set by DAO
                name = ingredient.name,
                grams = ingredient.grams,
                calories = ingredient.calories,
                protein = ingredient.protein,
                fat = ingredient.fat,
                carbs = ingredient.carbs,
                fiber = ingredient.fiber,
                isDrink = ingredient.isDrink,
                waterMl = ingredient.waterMl,
                vitaminA = m.vitaminAMcg,
                vitaminC = m.vitaminCMg,
                vitaminD = m.vitaminDMcg,
                calcium = m.calciumMg,
                iron = m.ironMg,
                potassium = m.potassiumMg,
                sodium = m.sodiumMg,
                vitaminB12 = m.vitaminB12Mcg,
                folate = m.folateMcg,
                vitaminB6 = m.vitaminB6Mg,
                magnesium = m.magnesiumMg,
                zinc = m.zincMg,
                vitaminE = m.vitaminEMg,
                // Store the item itself as its single ingredient so the detail editor works.
                ingredientsJson = MealJson.encodeIngredients(listOf(ingredient))
            )
        }
        return mealLogDao.logMealWithItems(mealLog, logItems)
    }

    /** Log plain water (a quick-add). Counts fully toward water intake, never shown among meals. */
    suspend fun logWater(ml: Float, date: String? = null): Long {
        val logDate = date ?: todayString()
        // Tag with the "water" sentinel category so it stays out of Breakfast/Lunch/… on Home.
        val mealLog = MealLogEntity(date = logDate, mealType = MealTypes.WATER)
        val item = MealLogItemEntity(
            mealLogId = 0,
            name = "Water",
            grams = ml,
            calories = 0f,
            protein = 0f,
            fat = 0f,
            carbs = 0f,
            fiber = 0f,
            isDrink = true,
            waterMl = ml
        )
        return mealLogDao.logMealWithItems(mealLog, listOf(item))
    }

    fun getTotalWaterForDate(date: String): Flow<Float> =
        mealLogDao.getTotalWaterForDate(date)

    suspend fun getItemById(itemId: Long): MealLogItemEntity? = mealLogDao.getItemById(itemId)

    suspend fun getMealTypeForItem(itemId: Long): String? = mealLogDao.getMealTypeForItem(itemId)

    suspend fun deleteMeal(id: Long) = mealLogDao.deleteMealLog(id)

    suspend fun deleteItem(id: Long) = mealLogDao.deleteItem(id)

    /**
     * Duplicate a single logged item onto another day, keeping its meal category. Used by the
     * "copy to another date" action. The copy gets fresh ids (id/mealLogId reset to 0).
     */
    suspend fun copyItemToDate(item: MealLogItemEntity, date: String, mealType: String? = null) {
        val type = mealType ?: mealLogDao.getMealTypeForItem(item.id) ?: defaultMealType()
        val mealLog = MealLogEntity(date = date, mealType = type)
        mealLogDao.logMealWithItems(mealLog, listOf(item.copy(id = 0, mealLogId = 0)))
    }

    // ---- Change meal category (Breakfast/Lunch/Dinner/Snack) after logging ----

    /** Change a whole logged meal's category (all its dishes move together). */
    suspend fun setMealMealType(mealLogId: Long, newMealType: String) =
        mealLogDao.updateMealLogType(mealLogId, newMealType)

    /**
     * Change a single logged item's category. If it's the only item in its meal log, just retag
     * the log; otherwise move the item to its own new meal log of the new category (same day),
     * keeping the item's id stable so any open detail screen stays valid.
     */
    suspend fun setItemMealType(item: MealLogItemEntity, newMealType: String) {
        val siblings = mealLogDao.getItemsForMeal(item.mealLogId)
        if (siblings.size <= 1) {
            mealLogDao.updateMealLogType(item.mealLogId, newMealType)
        } else {
            val date = mealLogDao.getMealDate(item.mealLogId) ?: todayString()
            val newLogId = mealLogDao.insertMealLog(MealLogEntity(date = date, mealType = newMealType))
            mealLogDao.updateItemMealLog(item.id, newLogId)
        }
    }

    // ---- Whole meal group (one generation = one photo/describe), for the multi-dish meal screen ----

    /** Live items of one meal log. */
    fun getItemsForMealFlow(mealLogId: Long): Flow<List<MealLogItemEntity>> =
        mealLogDao.getItemsForMealFlow(mealLogId)

    /** The meal category of a whole meal log. */
    suspend fun getMealTypeForMeal(mealLogId: Long): String? =
        mealLogDao.getMealTypeForMeal(mealLogId)

    /** The calendar day (ISO) a meal log belongs to. */
    suspend fun getMealDate(mealLogId: Long): String? =
        mealLogDao.getMealDate(mealLogId)

    /** Duplicate a whole meal (all its dishes) onto another day. Keeps its category unless one is given. */
    suspend fun copyMealToDate(mealLogId: Long, date: String, mealType: String? = null) {
        val items = mealLogDao.getItemsForMeal(mealLogId)
        if (items.isEmpty()) return
        val type = mealType ?: mealLogDao.getMealTypeForMeal(mealLogId) ?: defaultMealType()
        val mealLog = MealLogEntity(date = date, mealType = type)
        mealLogDao.logMealWithItems(mealLog, items.map { it.copy(id = 0, mealLogId = 0) })
    }

    // ---- Ingredient breakdown + cached AI insights (meal detail screen) ----

    /** The ingredients that make up a logged item (empty if none were saved). */
    fun ingredientsForItem(item: MealLogItemEntity): List<Ingredient> =
        MealJson.decodeIngredients(item.ingredientsJson)

    /** Cached AI insights for an item, or null if none / wiped by retention. */
    fun insightsForItem(item: MealLogItemEntity): MealInsights? =
        MealJson.decodeInsights(item.insightsJson)

    /** Save freshly generated insights so we don't regenerate next time. */
    suspend fun saveInsights(itemId: Long, insights: MealInsights) {
        mealLogDao.updateInsights(itemId, MealJson.encodeInsights(insights), System.currentTimeMillis())
    }

    /** Forget cached insights (used by the 1-month retention sweep). */
    suspend fun clearInsights(itemId: Long) {
        mealLogDao.updateInsights(itemId, null, 0L)
    }

    /**
     * Replace an item's ingredient list and recompute every total from it.
     * Drives the add/remove/edit-grams controls on the meal detail screen.
     */
    suspend fun updateItemIngredients(itemId: Long, ingredients: List<Ingredient>) {
        val micros = ingredients.fold(Micronutrients()) { acc, ing -> acc + ing.micros }
        mealLogDao.updateItemWithIngredients(
            id = itemId,
            ingredientsJson = MealJson.encodeIngredients(ingredients),
            grams = ingredients.sumOf { it.grams.toDouble() }.toFloat(),
            calories = ingredients.sumOf { it.calories.toDouble() }.toFloat(),
            protein = ingredients.sumOf { it.protein.toDouble() }.toFloat(),
            fat = ingredients.sumOf { it.fat.toDouble() }.toFloat(),
            carbs = ingredients.sumOf { it.carbs.toDouble() }.toFloat(),
            fiber = ingredients.sumOf { it.fiber.toDouble() }.toFloat(),
            waterMl = ingredients.sumOf { it.waterMl.toDouble() }.toFloat(),
            vitaminA = micros.vitaminAMcg,
            vitaminC = micros.vitaminCMg,
            vitaminD = micros.vitaminDMcg,
            calcium = micros.calciumMg,
            iron = micros.ironMg,
            potassium = micros.potassiumMg,
            sodium = micros.sodiumMg,
            vitaminB12 = micros.vitaminB12Mcg,
            folate = micros.folateMcg,
            vitaminB6 = micros.vitaminB6Mg,
            magnesium = micros.magnesiumMg,
            zinc = micros.zincMg,
            vitaminE = micros.vitaminEMg
        )
    }

    /**
     * Change a logged item's weight, scaling its calories and macros proportionally.
     *
     * If the item has a saved ingredient breakdown, scale that too and re-persist through the same
     * path the detail editor uses — otherwise `ingredientsJson` would keep the OLD weight, and the
     * detail screen (which reads the breakdown) would silently revert this edit the next time it's
     * touched. For older entries with no breakdown we scale the stored totals directly.
     */
    suspend fun updateItemGrams(item: MealLogItemEntity, newGrams: Float) {
        if (newGrams <= 0f || item.grams <= 0f) return
        val breakdown = ingredientsForItem(item)
        val breakdownGrams = breakdown.sumOf { it.grams.toDouble() }.toFloat()
        if (breakdown.isNotEmpty() && breakdownGrams > 0f) {
            // Scale the breakdown so it sums to the new total (relative to the breakdown's own
            // weight, which also heals any row left inconsistent by the old code path), then
            // re-persist through the detail editor's path so ingredientsJson stays consistent.
            val f = newGrams / breakdownGrams
            updateItemIngredients(item.id, breakdown.map { it.withGrams(it.grams * f) })
            return
        }
        val factor = newGrams / item.grams
        mealLogDao.updateItem(
            id = item.id,
            grams = newGrams,
            calories = item.calories * factor,
            protein = item.protein * factor,
            fat = item.fat * factor,
            carbs = item.carbs * factor,
            fiber = item.fiber * factor,
            waterMl = item.waterMl * factor,
            vitaminA = item.vitaminA * factor,
            vitaminC = item.vitaminC * factor,
            vitaminD = item.vitaminD * factor,
            calcium = item.calcium * factor,
            iron = item.iron * factor,
            potassium = item.potassium * factor,
            sodium = item.sodium * factor,
            vitaminB12 = item.vitaminB12 * factor,
            folate = item.folate * factor,
            vitaminB6 = item.vitaminB6 * factor,
            magnesium = item.magnesium * factor,
            zinc = item.zinc * factor,
            vitaminE = item.vitaminE * factor
        )
    }
}
