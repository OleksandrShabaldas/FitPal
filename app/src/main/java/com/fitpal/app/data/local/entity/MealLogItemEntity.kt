package com.fitpal.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single food item within a meal log.
 * Stores the actual nutritional values as-eaten (not a reference to gallery),
 * so editing the gallery food later won't change historical logs.
 *
 * galleryFoodId is optional — null for manually entered foods that weren't saved.
 */
@Entity(
    tableName = "meal_log_items",
    foreignKeys = [
        ForeignKey(
            entity = MealLogEntity::class,
            parentColumns = ["id"],
            childColumns = ["mealLogId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("mealLogId")]
)
data class MealLogItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mealLogId: Long,
    val name: String,
    val grams: Float,
    val calories: Float,
    val protein: Float,
    val fat: Float,
    val carbs: Float,
    @ColumnInfo(defaultValue = "0")
    val fiber: Float = 0f,
    @ColumnInfo(defaultValue = "0")
    val isDrink: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val waterMl: Float = 0f,
    // Micronutrients (actual amounts eaten, not per-100g)
    @ColumnInfo(defaultValue = "0") val vitaminA: Float = 0f,
    @ColumnInfo(defaultValue = "0") val vitaminC: Float = 0f,
    @ColumnInfo(defaultValue = "0") val vitaminD: Float = 0f,
    @ColumnInfo(defaultValue = "0") val calcium: Float = 0f,
    @ColumnInfo(defaultValue = "0") val iron: Float = 0f,
    @ColumnInfo(defaultValue = "0") val potassium: Float = 0f,
    @ColumnInfo(defaultValue = "0") val sodium: Float = 0f,
    @ColumnInfo(defaultValue = "0") val vitaminB12: Float = 0f,
    @ColumnInfo(defaultValue = "0") val folate: Float = 0f,
    @ColumnInfo(defaultValue = "0") val vitaminB6: Float = 0f,
    @ColumnInfo(defaultValue = "0") val magnesium: Float = 0f,
    @ColumnInfo(defaultValue = "0") val zinc: Float = 0f,
    @ColumnInfo(defaultValue = "0") val vitaminE: Float = 0f,
    val galleryFoodId: Long? = null,
    val photoPath: String? = null,
    /** JSON list of the ingredients that make up this item (for the detail editor). */
    val ingredientsJson: String? = null,
    /** Cached AI health insights as JSON, so we don't regenerate every time. */
    val insightsJson: String? = null,
    /** When the cached insights were generated (epoch millis); 0 = none. */
    @ColumnInfo(defaultValue = "0")
    val insightsGeneratedAt: Long = 0L,
    /** Which AI produced this item — "ONLINE" / "OFFLINE" (see [com.fitpal.app.ml.AiSource]); null if unknown. */
    val aiSource: String? = null,
    /** The exact model that produced it ("gemini-3-flash-preview", "Gemma 3n E4B"); null if unknown. */
    val aiModel: String? = null
)
