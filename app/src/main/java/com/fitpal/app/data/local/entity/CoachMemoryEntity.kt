package com.fitpal.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One line of what the AI coach knows about the user (the 17 Nov review, #2 — see
 * [com.fitpal.app.domain.model.MemorySection]). Written by the coach inside an overview's own call (the
 * `MEMORY:` line), or by the user on the Coach tab's "What your coach knows" page.
 *
 *  - [section] = a [com.fitpal.app.domain.model.MemorySection] id ("triggers", …)
 *  - [sourceDate] = the day what it came from happened or was said ("2026-11-12")
 *  - [mine] = written or edited by the user: the coach reads it but never changes or removes it
 */
@Entity(tableName = "coach_memory")
data class CoachMemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val section: String,
    val text: String,
    val sourceDate: String,
    @ColumnInfo(defaultValue = "0")
    val mine: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
