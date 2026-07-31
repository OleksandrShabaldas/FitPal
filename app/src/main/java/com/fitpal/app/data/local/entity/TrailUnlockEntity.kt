package com.fitpal.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Something the user owns on the trail — a scene theme or a curio.
 * Ids are namespaced (`theme:dusk`, `curio:compass`) so one table covers both.
 */
@Entity(tableName = "trail_unlocks")
data class TrailUnlockEntity(
    @PrimaryKey val id: String,
    /** THEME or CURIO. */
    val kind: String,
    /** How many found — curios can repeat. */
    val count: Int = 1,
    val unlockedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val KIND_THEME = "THEME"
        const val KIND_CURIO = "CURIO"
        fun themeId(id: String) = "theme:$id"
        fun curioId(id: String) = "curio:$id"
    }
}
