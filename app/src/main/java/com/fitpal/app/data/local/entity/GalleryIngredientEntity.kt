package com.fitpal.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An individual ingredient within a gallery food.
 * For example, "Chicken Rice" might have ingredients: chicken breast, white rice, soy sauce.
 * Each ingredient stores its own nutritional values per 100g so we can recalculate
 * when the user changes the weight.
 */
@Entity(
    tableName = "gallery_ingredients",
    foreignKeys = [
        ForeignKey(
            entity = GalleryFoodEntity::class,
            parentColumns = ["id"],
            childColumns = ["galleryFoodId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("galleryFoodId")]
)
data class GalleryIngredientEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val galleryFoodId: Long,
    val name: String,
    val grams: Float,
    val caloriesPer100g: Float,
    val proteinPer100g: Float,
    val fatPer100g: Float,
    val carbsPer100g: Float,
    @ColumnInfo(defaultValue = "0") val fiberPer100g: Float = 0f,
    @ColumnInfo(defaultValue = "0") val waterMlPer100g: Float = 0f,
    // Micronutrients per 100 g
    @ColumnInfo(defaultValue = "0") val vitaminAPer100g: Float = 0f,
    @ColumnInfo(defaultValue = "0") val vitaminCPer100g: Float = 0f,
    @ColumnInfo(defaultValue = "0") val vitaminDPer100g: Float = 0f,
    @ColumnInfo(defaultValue = "0") val calciumPer100g: Float = 0f,
    @ColumnInfo(defaultValue = "0") val ironPer100g: Float = 0f,
    @ColumnInfo(defaultValue = "0") val potassiumPer100g: Float = 0f,
    @ColumnInfo(defaultValue = "0") val sodiumPer100g: Float = 0f,
    @ColumnInfo(defaultValue = "0") val vitaminB12Per100g: Float = 0f,
    @ColumnInfo(defaultValue = "0") val folatePer100g: Float = 0f,
    @ColumnInfo(defaultValue = "0") val vitaminB6Per100g: Float = 0f,
    @ColumnInfo(defaultValue = "0") val magnesiumPer100g: Float = 0f,
    @ColumnInfo(defaultValue = "0") val zincPer100g: Float = 0f,
    @ColumnInfo(defaultValue = "0") val vitaminEPer100g: Float = 0f
)
