package com.fitpal.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fitpal.app.data.local.entity.TrailProjectEntity
import com.fitpal.app.data.local.entity.TrailStateEntity
import com.fitpal.app.data.local.entity.TrailUnlockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrailDao {

    @Query("SELECT * FROM trail_state WHERE id = 1")
    fun observeState(): Flow<TrailStateEntity?>

    @Query("SELECT * FROM trail_state WHERE id = 1")
    suspend fun getState(): TrailStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(state: TrailStateEntity)

    @Query("SELECT * FROM trail_projects")
    fun observeProjects(): Flow<List<TrailProjectEntity>>

    @Query("SELECT projectId FROM trail_projects")
    suspend fun builtIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addProject(project: TrailProjectEntity)

    // ---- Shop: themes + curios ----

    @Query("SELECT * FROM trail_unlocks")
    fun observeUnlocks(): Flow<List<TrailUnlockEntity>>

    @Query("SELECT * FROM trail_unlocks")
    suspend fun unlocks(): List<TrailUnlockEntity>

    @Query("SELECT * FROM trail_unlocks WHERE id = :id")
    suspend fun unlock(id: String): TrailUnlockEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUnlock(unlock: TrailUnlockEntity)

    // ---- Wipe (Settings → clear all data) ----

    @Query("DELETE FROM trail_state")
    suspend fun clearState()

    @Query("DELETE FROM trail_projects")
    suspend fun clearProjects()

    @Query("DELETE FROM trail_unlocks")
    suspend fun clearUnlocks()
}
