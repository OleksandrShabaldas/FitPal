package com.fitpal.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fitpal.app.data.local.entity.CollectedPlantEntity
import com.fitpal.app.data.local.entity.GardenStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GardenDao {

    @Query("DELETE FROM garden_state")
    suspend fun clearState()

    @Query("DELETE FROM collected_plants")
    suspend fun clearCollected()

    @Query("SELECT * FROM garden_state WHERE id = 1")
    fun observeState(): Flow<GardenStateEntity?>

    @Query("SELECT * FROM garden_state WHERE id = 1")
    suspend fun getState(): GardenStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(state: GardenStateEntity)

    @Query("SELECT * FROM collected_plants ORDER BY orderIndex DESC")
    fun observeCollection(): Flow<List<CollectedPlantEntity>>

    /** Blooms the user hasn't celebrated yet (orderIndex beyond what they've seen). */
    @Query("SELECT * FROM collected_plants WHERE orderIndex > :index ORDER BY orderIndex ASC")
    suspend fun collectedAfter(index: Int): List<CollectedPlantEntity>

    @Insert
    suspend fun addCollected(plant: CollectedPlantEntity)
}
