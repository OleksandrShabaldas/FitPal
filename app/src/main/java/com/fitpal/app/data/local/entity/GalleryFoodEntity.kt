package com.fitpal.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A food item saved to the user's personal gallery for quick one-tap logging.
 * This is the core of the app — most daily usage is just tapping gallery items.
 */
@Entity(tableName = "gallery_foods")
data class GalleryFoodEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val photoPath: String? = null,
    val defaultPortionGrams: Float,
    val totalCalories: Float,
    val totalProtein: Float,
    val totalFat: Float,
    val totalCarbs: Float,
    @ColumnInfo(defaultValue = "0")
    val totalFiber: Float = 0f,
    @ColumnInfo(defaultValue = "0")
    val isDrink: Boolean = false,
    /** User-written notes about this food (e.g. "grandma's recipe, use less salt"). */
    @ColumnInfo(defaultValue = "")
    val notes: String = "",
    /** Path to a custom thumbnail image the user uploaded for this food. */
    @ColumnInfo(defaultValue = "")
    val thumbnailPath: String = "",
    /** Cached AI analysis (health insights) generated when this food was saved, if any. */
    val insightsJson: String? = null,
    /** Which engine produced the saved analysis (ONLINE/OFFLINE), for the badge. */
    val aiSource: String? = null,
    /** The category or subcategory this food is filed under (see [GalleryCategoryEntity]); null = unsorted. */
    val categoryId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis(),
    val useCount: Int = 0
)
