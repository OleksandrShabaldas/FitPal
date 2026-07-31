package com.fitpal.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fitpal.app.data.local.entity.ChallengeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChallengeDao {

    @Query("SELECT * FROM challenges WHERE periodKey IN (:keys)")
    fun observeFor(keys: List<String>): Flow<List<ChallengeEntity>>

    @Query("SELECT * FROM challenges WHERE periodKey = :periodKey AND slot = :slot")
    suspend fun get(periodKey: String, slot: String): ChallengeEntity?

    /** IGNORE so assigning on every open never rerolls a challenge already in play. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(challenge: ChallengeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(challenge: ChallengeEntity)

    /** Housekeeping: forget challenges long past their claim window. */
    @Query("DELETE FROM challenges WHERE periodKey NOT IN (:keepKeys) AND claimedAt = 0 AND completedAt = 0")
    suspend fun pruneStale(keepKeys: List<String>)

    @Query("DELETE FROM challenges")
    suspend fun clearAll()
}
