package com.fitpal.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fitpal.app.data.local.entity.StepEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StepDao {

    @Query("DELETE FROM step_entries")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: StepEntryEntity): Long

    /** Total steps for a given day across all sources. */
    @Query("SELECT COALESCE(SUM(steps), 0) FROM step_entries WHERE date = :date")
    fun getTotalStepsForDate(date: String): Flow<Int>

    /** Steps for a given day from one source only (e.g. "manual" vs "health_connect"). */
    @Query("SELECT COALESCE(SUM(steps), 0) FROM step_entries WHERE date = :date AND source = :source")
    fun getStepsForSource(date: String, source: String): Flow<Int>

    /** One-shot read of a day's steps from one source (for compare-and-raise on watch reports). */
    @Query("SELECT COALESCE(SUM(steps), 0) FROM step_entries WHERE date = :date AND source = :source")
    suspend fun getStepsForSourceOnce(date: String, source: String): Int

    /** Total calories burned from steps for a given day. */
    @Query("SELECT COALESCE(SUM(caloriesBurned), 0) FROM step_entries WHERE date = :date")
    fun getCaloriesBurnedForDate(date: String): Flow<Float>

    /** All entries for a specific day (to show breakdown). */
    @Query("SELECT * FROM step_entries WHERE date = :date ORDER BY timestamp DESC")
    fun getEntriesForDate(date: String): Flow<List<StepEntryEntity>>

    /** Latest entry by timestamp (for Samsung Health sync dedup). */
    @Query("SELECT * FROM step_entries WHERE source = 'samsung_health' AND date = :date LIMIT 1")
    suspend fun getSamsungEntryForDate(date: String): StepEntryEntity?

    /** Delete all Samsung Health entries for a date (before replacing with fresh sync). */
    @Query("DELETE FROM step_entries WHERE source = 'samsung_health' AND date = :date")
    suspend fun deleteSamsungEntriesForDate(date: String)

    /** Delete prior synced entries of a given source for a date (re-sync dedup). */
    @Query("DELETE FROM step_entries WHERE source = :source AND date = :date")
    suspend fun deleteBySourceForDate(date: String, source: String)

    /** Step totals for a date range (for analytics). */
    @Query("SELECT date, SUM(steps) as steps, SUM(caloriesBurned) as caloriesBurned FROM step_entries WHERE date BETWEEN :from AND :to GROUP BY date ORDER BY date")
    fun getDailySteps(from: String, to: String): Flow<List<DailyStepRow>>
}

data class DailyStepRow(
    val date: String,
    val steps: Int,
    val caloriesBurned: Float
)
