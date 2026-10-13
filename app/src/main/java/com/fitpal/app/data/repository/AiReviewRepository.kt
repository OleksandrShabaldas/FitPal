package com.fitpal.app.data.repository

import com.fitpal.app.data.local.dao.AiReviewDao
import com.fitpal.app.data.local.entity.AiReviewEntity
import com.fitpal.app.ml.AiSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A saved overview: its text, which AI produced it, and the logged totals it was written from
 * ([dataSignature], null for overviews written before that was recorded).
 */
data class SavedReview(
    val text: String,
    val source: AiSource?,
    val dataSignature: String? = null,
    val generatedAt: Long = 0L
)

/** An earlier overview, for the coach to remember what it said. */
data class PastReview(val period: String, val periodKey: String, val text: String, val generatedAt: Long)

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
        aiReviewDao.get(period, periodKey)?.let {
            SavedReview(it.review, AiSource.fromName(it.source, it.model), it.dataSignature, it.generatedAt)
        }

    suspend fun save(period: String, periodKey: String, review: String, source: AiSource?, dataSignature: String? = null) {
        aiReviewDao.upsert(
            AiReviewEntity(
                period, periodKey, review,
                source = source?.storedName, model = source?.model, dataSignature = dataSignature
            )
        )
    }

    /** The most recently written overviews, newest first. */
    suspend fun recent(limit: Int): List<PastReview> =
        aiReviewDao.recent(limit).map { PastReview(it.period, it.periodKey, it.review, it.generatedAt) }

    suspend fun delete(period: String, periodKey: String) {
        aiReviewDao.delete(period, periodKey)
    }
}
