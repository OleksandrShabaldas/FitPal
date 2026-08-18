package com.fitpal.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fitpal.app.data.local.entity.FoodInsightsCacheEntity

@Dao
interface InsightsCacheDao {
    @Query("SELECT * FROM food_insights_cache WHERE signature = :signature LIMIT 1")
    suspend fun getBySignature(signature: String): FoodInsightsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FoodInsightsCacheEntity)
}
