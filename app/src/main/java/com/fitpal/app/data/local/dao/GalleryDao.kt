package com.fitpal.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.fitpal.app.data.local.entity.GalleryCategoryEntity
import com.fitpal.app.data.local.entity.GalleryFoodEntity
import com.fitpal.app.data.local.entity.GalleryIngredientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GalleryDao {

    @Query("DELETE FROM gallery_foods")
    suspend fun clearAllFoods()

    @Query("DELETE FROM gallery_ingredients")
    suspend fun clearAllIngredients()

    // --- Gallery foods ---

    @Query("SELECT * FROM gallery_foods ORDER BY lastUsedAt DESC")
    fun getAllFoods(): Flow<List<GalleryFoodEntity>>

    @Query("SELECT * FROM gallery_foods ORDER BY useCount DESC LIMIT :limit")
    fun getMostUsedFoods(limit: Int = 20): Flow<List<GalleryFoodEntity>>

    @Query("SELECT * FROM gallery_foods WHERE name LIKE '%' || :query || '%'")
    fun searchFoods(query: String): Flow<List<GalleryFoodEntity>>

    @Query("SELECT * FROM gallery_foods WHERE id = :id")
    suspend fun getFoodById(id: Long): GalleryFoodEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFood(food: GalleryFoodEntity): Long

    @Update
    suspend fun updateFood(food: GalleryFoodEntity)

    @Delete
    suspend fun deleteFood(food: GalleryFoodEntity)

    @Query("UPDATE gallery_foods SET lastUsedAt = :timestamp, useCount = useCount + 1 WHERE id = :id")
    suspend fun markUsed(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE gallery_foods SET name = :name WHERE id = :id")
    suspend fun updateName(id: Long, name: String)

    @Query("UPDATE gallery_foods SET notes = :notes WHERE id = :id")
    suspend fun updateNotes(id: Long, notes: String)

    @Query("UPDATE gallery_foods SET thumbnailPath = :path WHERE id = :id")
    suspend fun updateThumbnail(id: Long, path: String)

    @Query("UPDATE gallery_foods SET categoryId = :categoryId WHERE id = :id")
    suspend fun updateFoodCategory(id: Long, categoryId: Long?)

    /** Un-file every food sitting in any of the given categories/subcategories. */
    @Query("UPDATE gallery_foods SET categoryId = NULL WHERE categoryId IN (:categoryIds)")
    suspend fun clearFoodsInCategories(categoryIds: List<Long>)

    // --- Categories / subcategories ---

    @Query("SELECT * FROM gallery_categories ORDER BY sortOrder ASC, createdAt ASC")
    fun getCategories(): Flow<List<GalleryCategoryEntity>>

    @Query("SELECT * FROM gallery_categories WHERE parentId = :parentId")
    suspend fun getSubcategories(parentId: Long): List<GalleryCategoryEntity>

    @Insert
    suspend fun insertCategory(category: GalleryCategoryEntity): Long

    @Update
    suspend fun updateCategory(category: GalleryCategoryEntity)

    @Query("DELETE FROM gallery_categories WHERE id = :id OR parentId = :id")
    suspend fun deleteCategoryAndChildren(id: Long)

    // --- Ingredients for a gallery food ---

    @Query("SELECT * FROM gallery_ingredients WHERE galleryFoodId = :foodId")
    suspend fun getIngredients(foodId: Long): List<GalleryIngredientEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredients(ingredients: List<GalleryIngredientEntity>)

    @Query("DELETE FROM gallery_ingredients WHERE galleryFoodId = :foodId")
    suspend fun deleteIngredients(foodId: Long)

    /**
     * Save a food with all its ingredients in one transaction.
     * Replaces existing ingredients if the food already exists.
     */
    @Transaction
    suspend fun saveFoodWithIngredients(
        food: GalleryFoodEntity,
        ingredients: List<GalleryIngredientEntity>
    ): Long {
        val foodId = insertFood(food)
        deleteIngredients(foodId)
        val ingredientsWithFoodId = ingredients.map { it.copy(galleryFoodId = foodId) }
        insertIngredients(ingredientsWithFoodId)
        return foodId
    }
}
