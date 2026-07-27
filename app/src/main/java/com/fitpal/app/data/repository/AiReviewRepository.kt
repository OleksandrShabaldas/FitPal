package com.fitpal.app.data.repository

import com.fitpal.app.data.local.dao.AiReviewDao
import com.fitpal.app.data.local.entity.AiReviewEntity
import com.fitpal.app.ml.AiSource
import javax.inject.Inject
import javax.inject.Singleton

/** A saved overview: its text plus which AI produced it. */
data class SavedReview(val text: String, val source: AiSource?)

/**
 * Stores AI nutrition overviews so each day/week/month is generated once and
 * then re-shown instantly on later visits.
 */
@Singleton
class AiReviewRepository @Inject constructor(
    private val aiReviewDao: AiReviewDao
) {
    /** Saved review for a period (text + which AI made it), or null if not generated yet. */
    suspend fun get(period: String, periodKey: String): SavedReview? =
        aiReviewDao.get(period, periodKey)?.let { SavedReview(it.review, AiSource.fromName(it.source)) }

    suspend fun save(period: String, periodKey: String, review: String, source: AiSource?) {
        aiReviewDao.upsert(AiReviewEntity(period, periodKey, review, source = source?.name))
    }

    suspend fun delete(period: String, periodKey: String) {
        aiReviewDao.delete(period, periodKey)
    }
}
