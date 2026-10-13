package com.fitpal.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One answer the user gave to the AI coach's per-period check-in question.
 *
 * When a day (or week) looks unusual, the review screen shows an AI-generated question
 * ("Today ran higher than usual — what was going on?") with tappable options. The user's answer
 * is stored here and fed back into that review and later ones, so the coach remembers context the
 * numbers can't show (a family dinner, a stressful week, a skipped breakfast).
 *
 *  - period = "daily" | "weekly"
 *  - date   = the day the question was asked (daily: that day; weekly: the week's anchor date)
 *  - answer = the option the user picked, their free text, or "skipped" if they dismissed it
 *  - kind   = [KIND_CHECKIN] (asked before an unusual period's overview) or [KIND_FOLLOW_UP] (the
 *             coach's question at the end of an overview; `date` is then that overview's period key)
 */
@Entity(
    tableName = "context_notes",
    indices = [Index("date")]
)
data class ContextNoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,
    val period: String,
    val question: String,
    val answer: String,
    val timestamp: Long = System.currentTimeMillis(),
    val kind: String = KIND_CHECKIN
) {
    companion object {
        const val KIND_CHECKIN = "checkin"
        const val KIND_FOLLOW_UP = "followup"
    }
}
