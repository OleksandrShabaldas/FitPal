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

    /** The answer already given for this exact period+date, if any — so we don't re-ask. */
    @Query("SELECT * FROM context_notes WHERE period = :period AND date = :date ORDER BY timestamp DESC LIMIT 1")
    suspend fun getForPeriod(period: String, date: String): ContextNoteEntity?

    /**
     * Recent answers (any period) on/after [sinceDate], newest first — fed into a review as
     * remembered context. Skips "skipped" dismissals; the user chose not to say.
     */
    @Query("""
        SELECT * FROM context_notes
        WHERE date >= :sinceDate AND answer != 'skipped'
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun getRecentAnswers(sinceDate: String, limit: Int): List<ContextNoteEntity>

    /** Housekeeping: drop notes older than the given cutoff (epoch millis). */
    @Query("DELETE FROM context_notes WHERE timestamp < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)
}
