package com.fitpal.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fitpal.app.data.local.entity.ContextNoteEntity

@Dao
interface ContextNoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: ContextNoteEntity)

    /** The check-in answer already given for this exact period+date, if any — so we don't re-ask. */
    @Query("SELECT * FROM context_notes WHERE kind = 'checkin' AND period = :period AND date = :date ORDER BY timestamp DESC LIMIT 1")
    suspend fun getForPeriod(period: String, date: String): ContextNoteEntity?

    /** The latest answer to the coach's end-of-overview question for one overview ([date] = its period key). */
    @Query("SELECT * FROM context_notes WHERE kind = 'followup' AND period = :period AND date = :date ORDER BY timestamp DESC LIMIT 1")
    suspend fun getFollowUp(period: String, date: String): ContextNoteEntity?

    /**
     * Recent answers (any period) on/after [sinceDate], newest first — fed into a review as
     * remembered context. Skips "skipped" dismissals; the user chose not to say.
     */
    @Query("""
        SELECT * FROM context_notes
        WHERE kind = 'checkin' AND date >= :sinceDate AND answer != 'skipped'
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun getRecentAnswers(sinceDate: String, limit: Int): List<ContextNoteEntity>

    /** Housekeeping: drop notes older than the given cutoff (epoch millis). */
    @Query("DELETE FROM context_notes WHERE timestamp < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)
}
