package com.fitpal.app.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.fitpal.app.data.local.entity.MealLogEntity
import com.fitpal.app.data.local.entity.MealLogItemEntity
import kotlinx.coroutines.flow.Flow

/** A logged item together with the meal category (Breakfast/Lunch/…) it belongs to. */
data class MealLogItemWithType(
    @Embedded val item: MealLogItemEntity,
    val mealType: String
)

/** One logged food (name + calories + its day + meal) — for listing what was actually eaten. */
data class LoggedFoodRow(
    val date: String,
    val name: String,
    val calories: Float,
    val mealType: String
)

/** One row per day — used for analytics charts over a date range. */
data class DailyNutritionRow(
    val date: String,
    val calories: Float,
    val protein: Float,
    val fat: Float,
    val carbs: Float,
    val fiber: Float
)

/** One row per day — total pure water (ml), for the analytics water chart. */
data class DailyWaterRow(
    val date: String,
    val water: Float
)

/** One row per day — water split by source (clear drinks vs. water in food). */
data class DailyWaterSplit(
    val date: String,
    val drinkWater: Float,
    val foodWater: Float
)

/** Daily micronutrient totals — one query returns all thirteen. */
data class DailyMicros(
    val vitaminA: Float,
    val vitaminC: Float,
    val vitaminD: Float,
    val calcium: Float,
    val iron: Float,
    val potassium: Float,
    val sodium: Float,
    val vitaminB12: Float,
    val folate: Float,
    val vitaminB6: Float,
    val magnesium: Float,
    val zinc: Float,
    val vitaminE: Float
)

@Dao
interface MealLogDao {

    @Query("DELETE FROM meal_logs")
    suspend fun clearAllMealLogs()

    @Query("DELETE FROM meal_log_items")
    suspend fun clearAllMealItems()

    // --- Meal logs ---

    @Query("SELECT * FROM meal_logs WHERE date = :date ORDER BY timestamp DESC")
    fun getMealsForDate(date: String): Flow<List<MealLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealLog(mealLog: MealLogEntity): Long

    @Query("DELETE FROM meal_logs WHERE id = :id")
    suspend fun deleteMealLog(id: Long)

    /** Retag a whole meal log's category (Breakfast/Lunch/…). */
    @Query("UPDATE meal_logs SET mealType = :mealType WHERE id = :mealLogId")
    suspend fun updateMealLogType(mealLogId: Long, mealType: String)

    /** The calendar day a meal log belongs to. */
    @Query("SELECT date FROM meal_logs WHERE id = :mealLogId")
    suspend fun getMealDate(mealLogId: Long): String?

    /** Move a single item to a different meal log (keeps the item's own id). */
    @Query("UPDATE meal_log_items SET mealLogId = :mealLogId WHERE id = :itemId")
    suspend fun updateItemMealLog(itemId: Long, mealLogId: Long)

    // --- Meal log items ---

    @Query("SELECT * FROM meal_log_items WHERE mealLogId = :mealLogId")
    suspend fun getItemsForMeal(mealLogId: Long): List<MealLogItemEntity>

    /** Live items of one meal log — drives the multi-dish meal screen (group tap on Home). */
    @Query("SELECT * FROM meal_log_items WHERE mealLogId = :mealLogId")
    fun getItemsForMealFlow(mealLogId: Long): Flow<List<MealLogItemEntity>>

    /** The meal category for a whole meal log. */
    @Query("SELECT mealType FROM meal_logs WHERE id = :mealLogId")
    suspend fun getMealTypeForMeal(mealLogId: Long): String?

    @Query("SELECT * FROM meal_log_items WHERE id = :itemId")
    suspend fun getItemById(itemId: Long): MealLogItemEntity?

    /** Get the meal type for a specific log item (via its parent meal log). */
    @Query("""
        SELECT m.mealType FROM meal_logs m
        JOIN meal_log_items i ON i.mealLogId = m.id
        WHERE i.id = :itemId
    """)
    suspend fun getMealTypeForItem(itemId: Long): String?

    @Query("SELECT * FROM meal_log_items WHERE mealLogId IN (SELECT id FROM meal_logs WHERE date = :date)")
    fun getAllItemsForDate(date: String): Flow<List<MealLogItemEntity>>

    /**
     * The most recently logged real food per distinct name (newest first) — drives the
     * "Your usuals" one-tap re-log row on the Add screen. Skips water-only and zero-calorie rows;
     * de-dupes by name (case-insensitive) keeping the latest version of each.
     */
    @Query("""
        SELECT * FROM meal_log_items WHERE id IN (
            SELECT MAX(i.id) FROM meal_log_items i
            JOIN meal_logs m ON i.mealLogId = m.id
            WHERE i.calories > 0 AND m.mealType != 'water'
            GROUP BY i.name COLLATE NOCASE
        )
        ORDER BY id DESC
        LIMIT :limit
    """)
    fun getRecentDistinctItems(limit: Int): Flow<List<MealLogItemEntity>>

    /**
     * Distinct days (newest first) on which the user logged real food (calories > 0).
     * Drives the logging streak — water-only days don't count as tracking.
     */
    @Query("""
        SELECT DISTINCT m.date FROM meal_logs m
        JOIN meal_log_items i ON i.mealLogId = m.id
        WHERE i.calories > 0
        ORDER BY m.date DESC
    """)
    fun getLoggedDatesDesc(): Flow<List<String>>

    /** Today's items, each tagged with its meal category, for grouping on Home. */
    @Query(
        """
        SELECT i.*, m.mealType AS mealType
        FROM meal_log_items i
        JOIN meal_logs m ON i.mealLogId = m.id
        WHERE m.date = :date
        ORDER BY m.timestamp
        """
    )
    fun getItemsWithTypeForDate(date: String): Flow<List<MealLogItemWithType>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<MealLogItemEntity>)

    @Query("DELETE FROM meal_log_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    /** Save (or clear) cached AI insights for an item. */
    @Query("UPDATE meal_log_items SET insightsJson = :insightsJson, insightsGeneratedAt = :generatedAt WHERE id = :id")
    suspend fun updateInsights(id: Long, insightsJson: String?, generatedAt: Long)

    /**
     * Update an item's ingredient list and all the totals derived from it.
     * Used by the meal-detail editor when the user changes/removes/adds ingredients.
     */
    @Query(
        """UPDATE meal_log_items SET ingredientsJson = :ingredientsJson,
           grams = :grams, calories = :calories, protein = :protein, fat = :fat,
           carbs = :carbs, fiber = :fiber, waterMl = :waterMl,
           vitaminA = :vitaminA, vitaminC = :vitaminC, vitaminD = :vitaminD,
           calcium = :calcium, iron = :iron, potassium = :potassium, sodium = :sodium,
           vitaminB12 = :vitaminB12, folate = :folate, vitaminB6 = :vitaminB6,
           magnesium = :magnesium, zinc = :zinc, vitaminE = :vitaminE
           WHERE id = :id"""
    )
    suspend fun updateItemWithIngredients(
        id: Long,
        ingredientsJson: String?,
        grams: Float,
        calories: Float,
        protein: Float,
        fat: Float,
        carbs: Float,
        fiber: Float,
        waterMl: Float,
        vitaminA: Float,
        vitaminC: Float,
        vitaminD: Float,
        calcium: Float,
        iron: Float,
        potassium: Float,
        sodium: Float,
        vitaminB12: Float,
        folate: Float,
        vitaminB6: Float,
        magnesium: Float,
        zinc: Float,
        vitaminE: Float
    )

    @Query(
        """UPDATE meal_log_items SET grams = :grams, calories = :calories,
           protein = :protein, fat = :fat, carbs = :carbs, fiber = :fiber,
           waterMl = :waterMl,
           vitaminA = :vitaminA, vitaminC = :vitaminC, vitaminD = :vitaminD,
           calcium = :calcium, iron = :iron, potassium = :potassium, sodium = :sodium,
           vitaminB12 = :vitaminB12, folate = :folate, vitaminB6 = :vitaminB6,
           magnesium = :magnesium, zinc = :zinc, vitaminE = :vitaminE
           WHERE id = :id"""
    )
    suspend fun updateItem(
        id: Long,
        grams: Float,
        calories: Float,
        protein: Float,
        fat: Float,
        carbs: Float,
        fiber: Float,
        waterMl: Float,
        vitaminA: Float,
        vitaminC: Float,
        vitaminD: Float,
        calcium: Float,
        iron: Float,
        potassium: Float,
        sodium: Float,
        vitaminB12: Float,
        folate: Float,
        vitaminB6: Float,
        magnesium: Float,
        zinc: Float,
        vitaminE: Float
    )

    /**
     * Log a full meal (e.g. after scanning a plate) in one transaction.
     */
    @Transaction
    suspend fun logMealWithItems(
        mealLog: MealLogEntity,
        items: List<MealLogItemEntity>
    ): Long {
        val mealId = insertMealLog(mealLog)
        val itemsWithMealId = items.map { it.copy(mealLogId = mealId) }
        insertItems(itemsWithMealId)
        return mealId
    }

    // --- Daily totals ---

    @Query("""
        SELECT COALESCE(SUM(calories), 0) FROM meal_log_items
        WHERE mealLogId IN (SELECT id FROM meal_logs WHERE date = :date)
    """)
    fun getTotalCaloriesForDate(date: String): Flow<Float>

    @Query("""
        SELECT COALESCE(SUM(protein), 0) FROM meal_log_items
        WHERE mealLogId IN (SELECT id FROM meal_logs WHERE date = :date)
    """)
    fun getTotalProteinForDate(date: String): Flow<Float>

    @Query("""
        SELECT COALESCE(SUM(fat), 0) FROM meal_log_items
        WHERE mealLogId IN (SELECT id FROM meal_logs WHERE date = :date)
    """)
    fun getTotalFatForDate(date: String): Flow<Float>

    @Query("""
        SELECT COALESCE(SUM(carbs), 0) FROM meal_log_items
        WHERE mealLogId IN (SELECT id FROM meal_logs WHERE date = :date)
    """)
    fun getTotalCarbsForDate(date: String): Flow<Float>

    @Query("""
        SELECT COALESCE(SUM(fiber), 0) FROM meal_log_items
        WHERE mealLogId IN (SELECT id FROM meal_logs WHERE date = :date)
    """)
    fun getTotalFiberForDate(date: String): Flow<Float>

    @Query("""
        SELECT COALESCE(SUM(waterMl), 0) FROM meal_log_items
        WHERE mealLogId IN (SELECT id FROM meal_logs WHERE date = :date)
    """)
    fun getTotalWaterForDate(date: String): Flow<Float>

    /** Per-day nutrition totals for a date range — used by Analytics charts. */
    @Query("""
        SELECT m.date,
               COALESCE(SUM(i.calories), 0)  AS calories,
               COALESCE(SUM(i.protein), 0)   AS protein,
               COALESCE(SUM(i.fat), 0)       AS fat,
               COALESCE(SUM(i.carbs), 0)     AS carbs,
               COALESCE(SUM(i.fiber), 0)     AS fiber
        FROM meal_logs m
        JOIN meal_log_items i ON i.mealLogId = m.id
        WHERE m.date BETWEEN :from AND :to
        GROUP BY m.date
        ORDER BY m.date ASC
    """)
    fun getDailyNutritionRange(from: String, to: String): Flow<List<DailyNutritionRow>>

    /** Every logged food in a date range (so the AI review can see WHAT was eaten, not just totals). */
    @Query("""
        SELECT m.date AS date, i.name AS name, i.calories AS calories, m.mealType AS mealType
        FROM meal_log_items i JOIN meal_logs m ON i.mealLogId = m.id
        WHERE m.date BETWEEN :from AND :to
        ORDER BY m.date ASC, m.id ASC
    """)
    suspend fun getLoggedFoodsInRange(from: String, to: String): List<LoggedFoodRow>

    /** Per-day pure-water totals for a date range — drives the analytics water chart. */
    @Query("""
        SELECT m.date AS date, COALESCE(SUM(i.waterMl), 0) AS water
        FROM meal_logs m
        JOIN meal_log_items i ON i.mealLogId = m.id
        WHERE m.date BETWEEN :from AND :to
        GROUP BY m.date
        ORDER BY m.date ASC
    """)
    fun getDailyWaterRange(from: String, to: String): Flow<List<DailyWaterRow>>

    /** Per-day water split into clear drinks vs. water from food — analytics breakdown. */
    @Query("""
        SELECT m.date AS date,
               COALESCE(SUM(CASE WHEN i.isDrink THEN i.waterMl ELSE 0 END), 0) AS drinkWater,
               COALESCE(SUM(CASE WHEN i.isDrink THEN 0 ELSE i.waterMl END), 0) AS foodWater
        FROM meal_logs m
        JOIN meal_log_items i ON i.mealLogId = m.id
        WHERE m.date BETWEEN :from AND :to
        GROUP BY m.date
        ORDER BY m.date ASC
    """)
    fun getDailyWaterSplitRange(from: String, to: String): Flow<List<DailyWaterSplit>>

    /** All thirteen micronutrient daily totals in one query. */
    @Query("""
        SELECT COALESCE(SUM(vitaminA), 0) AS vitaminA,
               COALESCE(SUM(vitaminC), 0) AS vitaminC,
               COALESCE(SUM(vitaminD), 0) AS vitaminD,
               COALESCE(SUM(calcium), 0)  AS calcium,
               COALESCE(SUM(iron), 0)     AS iron,
               COALESCE(SUM(potassium), 0) AS potassium,
               COALESCE(SUM(sodium), 0)   AS sodium,
               COALESCE(SUM(vitaminB12), 0) AS vitaminB12,
               COALESCE(SUM(folate), 0)   AS folate,
               COALESCE(SUM(vitaminB6), 0) AS vitaminB6,
               COALESCE(SUM(magnesium), 0) AS magnesium,
               COALESCE(SUM(zinc), 0)     AS zinc,
               COALESCE(SUM(vitaminE), 0) AS vitaminE
        FROM meal_log_items
        WHERE mealLogId IN (SELECT id FROM meal_logs WHERE date = :date)
    """)
    fun getDailyMicrosForDate(date: String): Flow<DailyMicros>

    /**
     * Summed micronutrients across a whole date range — for the analytics
     * deficiency/proficiency breakdown. Divide by the number of logged days to get
     * a daily average to compare against each nutrient's target.
     */
    @Query("""
        SELECT COALESCE(SUM(i.vitaminA), 0) AS vitaminA,
               COALESCE(SUM(i.vitaminC), 0) AS vitaminC,
               COALESCE(SUM(i.vitaminD), 0) AS vitaminD,
               COALESCE(SUM(i.calcium), 0)  AS calcium,
               COALESCE(SUM(i.iron), 0)     AS iron,
               COALESCE(SUM(i.potassium), 0) AS potassium,
               COALESCE(SUM(i.sodium), 0)   AS sodium,
               COALESCE(SUM(i.vitaminB12), 0) AS vitaminB12,
               COALESCE(SUM(i.folate), 0)   AS folate,
               COALESCE(SUM(i.vitaminB6), 0) AS vitaminB6,
               COALESCE(SUM(i.magnesium), 0) AS magnesium,
               COALESCE(SUM(i.zinc), 0)     AS zinc,
               COALESCE(SUM(i.vitaminE), 0) AS vitaminE
        FROM meal_logs m
        JOIN meal_log_items i ON i.mealLogId = m.id
        WHERE m.date BETWEEN :from AND :to
    """)
    fun getMicrosForRange(from: String, to: String): Flow<DailyMicros>
}
