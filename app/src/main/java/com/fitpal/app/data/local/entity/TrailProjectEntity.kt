package com.fitpal.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A restoration project the user has built. Built is forever — never undone by neglect. */
@Entity(tableName = "trail_projects")
data class TrailProjectEntity(
    @PrimaryKey val projectId: String,
    val builtAt: Long = System.currentTimeMillis()
)
