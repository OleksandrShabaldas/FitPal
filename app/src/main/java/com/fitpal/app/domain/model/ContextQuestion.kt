package com.fitpal.app.domain.model

/**
 * One AI-generated check-in question shown before a review when the day/period looks unusual, with
 * concrete tappable answer options. The user's pick (or free text, or "skip") is stored as a
 * [com.fitpal.app.data.local.entity.ContextNoteEntity] and fed back into this and future reviews.
 */
data class ContextQuestion(
    val question: String,
    val options: List<String>
)
