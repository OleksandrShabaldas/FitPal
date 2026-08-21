package com.fitpal.app.data.repository

import com.fitpal.app.data.local.dao.ContextNoteDao
import com.fitpal.app.data.local.entity.ContextNoteEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the user's answers to the AI coach's per-period check-in questions, and reads them back
 * so later reviews remember the context (a family dinner, a stressful week) the numbers can't show.
 */
@Singleton
class ContextNoteRepository @Inject constructor(
    private val contextNoteDao: ContextNoteDao
) {
    /** Record an answer (or "skipped"). */
    suspend fun save(date: String, period: String, question: String, answer: String) {
        contextNoteDao.insert(
            ContextNoteEntity(date = date, period = period, question = question, answer = answer)
        )
    }

    /** The answer already given for this period+date, if any — so the screen doesn't re-ask. */
    suspend fun getForPeriod(period: String, date: String): ContextNoteEntity? =
        contextNoteDao.getForPeriod(period, date)

    /** Recent real answers (newest first) since [sinceDate], for feeding into a review. */
    suspend fun getRecentAnswers(sinceDate: String, limit: Int = 10): List<ContextNoteEntity> =
        contextNoteDao.getRecentAnswers(sinceDate, limit)
}
