package com.fitpal.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.fitpal.app.data.local.entity.PlannedMealEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannedMealDao {
    @Insert
    suspend fun insert(meal: PlannedMealEntity): Long

    @Insert
    suspend fun insertAll(meals: List<PlannedMealEntity>): List<Long>

    @Update
    suspend fun update(meal: PlannedMealEntity)

    @Query("SELECT * FROM planned_meals WHERE id = :id")
    suspend fun get(id: Long): PlannedMealEntity?

    @Query("SELECT * FROM planned_meals WHERE id = :id")
    fun observe(id: Long): Flow<PlannedMealEntity?>

    /** One generation's meals (still on the results screen, or since scheduled / logged). */
    @Query("SELECT * FROM planned_meals WHERE requestId = :requestId AND status != 'DISMISSED' ORDER BY date, id")
    fun forRequest(requestId: String): Flow<List<PlannedMealEntity>>

    /** What's planned for a day and not logged yet — Home shows these, uncounted. */
    @Query("SELECT * FROM planned_meals WHERE date = :date AND status = 'SCHEDULED' ORDER BY id")
    fun scheduledFor(date: String): Flow<List<PlannedMealEntity>>

    /** Scheduled meals from [fromDate] on, soonest first — "Coming up". */
    @Query("SELECT * FROM planned_meals WHERE status = 'SCHEDULED' AND date >= :fromDate ORDER BY date, id")
    fun upcoming(fromDate: String): Flow<List<PlannedMealEntity>>

    @Query("SELECT * FROM planned_meals WHERE status = 'SAVED' ORDER BY createdAt DESC")
    fun saved(): Flow<List<PlannedMealEntity>>

    /** The newest generation that still has meals on its results screen. */
    @Query("SELECT requestId FROM planned_meals WHERE status = 'IDEA' ORDER BY createdAt DESC LIMIT 1")
    fun latestOpenRequest(): Flow<String?>

    @Query("UPDATE planned_meals SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: String)

    @Query("SELECT * FROM planned_meals")
    suspend fun all(): List<PlannedMealEntity>

    @Query("DELETE FROM planned_meals")
    suspend fun clearAll()
}
