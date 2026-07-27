package com.fitpal.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.fitpal.app.data.local.entity.UsdaFoodEntity

/**
 * Access to the USDA nutritional database.
 * Used when the user manually adds an ingredient or when
 * the LLM suggests ingredients and we need their nutritional values.
 */
@Dao
interface NutritionDao {

    @Query("SELECT * FROM usda_foods WHERE description LIKE '%' || :query || '%' LIMIT :limit")
    suspend fun searchFoods(query: String, limit: Int = 20): List<UsdaFoodEntity>

    /**
     * Search ONLY the built-in simple/non-branded foods (the curated FoodSeedData set). Excludes
     * Open Food Facts and imported branded foods by category. Shorter names rank first so e.g.
     * "Apple" beats "Apple pie".
     */
    @Query("""
        SELECT * FROM usda_foods
        WHERE foodCategory IS NOT NULL
          AND foodCategory != 'Open Food Facts' AND foodCategory != 'Branded'
          AND description LIKE '%' || :query || '%'
        ORDER BY length(description) ASC
        LIMIT :limit
    """)
    suspend fun searchBasicFoods(query: String, limit: Int = 30): List<UsdaFoodEntity>

    @Query("SELECT * FROM usda_foods WHERE fdcId = :id")
    suspend fun getFoodById(id: Int): UsdaFoodEntity?

    @Query("SELECT * FROM usda_foods WHERE foodCategory = :category LIMIT :limit")
    suspend fun getFoodsByCategory(category: String, limit: Int = 50): List<UsdaFoodEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(foods: List<UsdaFoodEntity>)

    /** Non-suspend batch insert, used by the streaming branded import on a background thread. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllBlocking(foods: List<UsdaFoodEntity>)

    /**
     * Raw search — used for the full-text (FTS) query over the large branded DB.
     * Falls back to [searchFoods] (LIKE) when the FTS table isn't present.
     */
    @RawQuery
    suspend fun searchRaw(query: SupportSQLiteQuery): List<UsdaFoodEntity>

    @Query("SELECT COUNT(*) FROM usda_foods")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM usda_foods WHERE foodCategory = :category")
    suspend fun countByCategory(category: String): Int
}
