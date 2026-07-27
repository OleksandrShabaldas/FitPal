package com.fitpal.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fitpal.app.data.local.entity.AiReviewEntity

@Dao
interface AiReviewDao {

    @Query("DELETE FROM ai_reviews")
    suspend fun clearAll()

    @Query("SELECT * FROM ai_reviews WHERE period = :period AND periodKey = :periodKey LIMIT 1")
    suspend fun get(period: String, periodKey: String): AiReviewEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(review: AiReviewEntity)

    @Query("DELETE FROM ai_reviews WHERE period = :period AND periodKey = :periodKey")
    suspend fun delete(period: String, periodKey: String)

    /** Housekeeping: drop saved reviews older than the given cutoff (epoch millis). */
    @Query("DELETE FROM ai_reviews WHERE generatedAt < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)
}
