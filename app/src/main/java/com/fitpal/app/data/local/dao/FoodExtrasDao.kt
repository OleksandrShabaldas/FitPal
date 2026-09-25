package com.fitpal.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fitpal.app.data.local.entity.FoodExtrasEntity

/** The per-food cache of background nutrient-check results (see [FoodExtrasEntity]). */
@Dao
interface FoodExtrasDao {
    @Query("SELECT * FROM food_extras WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): FoodExtrasEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FoodExtrasEntity)
}
