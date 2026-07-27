package com.fitpal.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.fitpal.app.data.local.entity.ExerciseEntryEntity
import com.fitpal.app.data.local.entity.SavedWorkoutEntity
import kotlinx.coroutines.flow.Flow

/** One row per day — total exercise calories burned, for charts + eat-back. */
data class DailyBurnRow(
    val date: String,
    val burned: Float
)

@Dao
interface ExerciseDao {

    @Query("DELETE FROM exercise_entries")
    suspend fun clearAll()

    @Insert
    suspend fun insert(entry: ExerciseEntryEntity): Long

    @Query("SELECT * FROM exercise_entries WHERE date = :date ORDER BY timestamp DESC")
    fun getForDate(date: String): Flow<List<ExerciseEntryEntity>>

    @Query("SELECT * FROM exercise_entries WHERE id = :id")
    suspend fun getById(id: Long): ExerciseEntryEntity?

    /** All logged workouts in a date range — used to give the AI overview the activity log. */
    @Query("SELECT * FROM exercise_entries WHERE date BETWEEN :from AND :to ORDER BY date ASC, timestamp ASC")
    suspend fun getEntriesInRange(from: String, to: String): List<ExerciseEntryEntity>

    @Query("SELECT COALESCE(SUM(caloriesBurned), 0) FROM exercise_entries WHERE date = :date")
    fun getTotalBurnedForDate(date: String): Flow<Float>

    @Query("""
        SELECT date, COALESCE(SUM(caloriesBurned), 0) AS burned
        FROM exercise_entries
        WHERE date BETWEEN :from AND :to
        GROUP BY date ORDER BY date ASC
    """)
    fun getDailyBurnRange(from: String, to: String): Flow<List<DailyBurnRow>>

    /** Non-reactive total for a date — used by the garden's catch-up evaluation. */
    @Query("SELECT COALESCE(SUM(caloriesBurned), 0) FROM exercise_entries WHERE date = :date")
    suspend fun totalBurnedForDateOnce(date: String): Float

    @Query("DELETE FROM exercise_entries WHERE id = :id")
    suspend fun delete(id: Long)

    /** Edit a logged entry's duration; calories are recomputed by the caller and passed in. */
    @Query("UPDATE exercise_entries SET minutes = :minutes, caloriesBurned = :calories WHERE id = :id")
    suspend fun updateDuration(id: Long, minutes: Int, calories: Float)

    // ---- Saved workouts (collection templates for one-tap re-logging) ----

    @Insert
    suspend fun insertSavedWorkout(workout: SavedWorkoutEntity): Long

    @Query("SELECT * FROM saved_workouts ORDER BY lastUsed DESC")
    fun getSavedWorkouts(): Flow<List<SavedWorkoutEntity>>

    @Query("SELECT COUNT(*) FROM saved_workouts WHERE name = :name")
    suspend fun savedWorkoutCount(name: String): Int

    @Query("UPDATE saved_workouts SET lastUsed = :ts WHERE id = :id")
    suspend fun markSavedWorkoutUsed(id: Long, ts: Long)

    @Query("DELETE FROM saved_workouts WHERE id = :id")
    suspend fun deleteSavedWorkout(id: Long)
}
