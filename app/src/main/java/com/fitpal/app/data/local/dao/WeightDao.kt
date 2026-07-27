package com.fitpal.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.fitpal.app.data.local.entity.WeightEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {

    @Query("DELETE FROM weight_entries")
    suspend fun clearAll()

    /** Most recent entry first — for "current weight". */
    @Query("SELECT * FROM weight_entries ORDER BY date DESC LIMIT 1")
    fun getLatest(): Flow<WeightEntryEntity?>

    /** All entries in a date range, oldest first — for charts. */
    @Query("SELECT * FROM weight_entries WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    fun getRange(from: String, to: String): Flow<List<WeightEntryEntity>>

    /** All entries ever, oldest first. */
    @Query("SELECT * FROM weight_entries ORDER BY date ASC")
    fun getAll(): Flow<List<WeightEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: WeightEntryEntity): Long

    /** Delete any existing entry for this date, then insert the new one (one per day). */
    @Transaction
    suspend fun upsertForDate(entry: WeightEntryEntity) {
        deleteByDate(entry.date)
        insert(entry)
    }

    @Query("DELETE FROM weight_entries WHERE date = :date")
    suspend fun deleteByDate(date: String)

    @Query("DELETE FROM weight_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}
