package com.fitpal.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.fitpal.app.data.local.entity.CoachMemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CoachMemoryDao {
    @Query("SELECT * FROM coach_memory ORDER BY section, createdAt")
    fun observeAll(): Flow<List<CoachMemoryEntity>>

    @Query("SELECT * FROM coach_memory ORDER BY section, createdAt")
    suspend fun getAll(): List<CoachMemoryEntity>

    @Insert
    suspend fun insert(line: CoachMemoryEntity): Long

    @Insert
    suspend fun insertAll(lines: List<CoachMemoryEntity>)

    @Update
    suspend fun update(line: CoachMemoryEntity)

    @Query("DELETE FROM coach_memory WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM coach_memory")
    suspend fun clearAll()
}
