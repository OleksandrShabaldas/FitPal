package com.fitpal.app.data.repository

import com.fitpal.app.data.local.MealJson
import com.fitpal.app.data.local.dao.GalleryDao
import com.fitpal.app.data.local.entity.GalleryCategoryEntity
import com.fitpal.app.data.local.entity.GalleryFoodEntity
import com.fitpal.app.data.local.entity.GalleryIngredientEntity
import com.fitpal.app.domain.model.DetectedFood
import com.fitpal.app.domain.model.Ingredient
import com.fitpal.app.domain.model.MealInsights
import com.fitpal.app.domain.model.Micronutrients
import com.fitpal.app.ml.AiSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GalleryRepository @Inject constructor(
    private val galleryDao: GalleryDao
) {
    fun getAllFoods(): Flow<List<GalleryFoodEntity>> = galleryDao.getAllFoods()

    fun getMostUsed(limit: Int = 20): Flow<List<GalleryFoodEntity>> =
        galleryDao.getMostUsedFoods(limit)

    fun searchFoods(query: String): Flow<List<GalleryFoodEntity>> =
        galleryDao.searchFoods(query)

    suspend fun getFoodWithIngredients(id: Long): Pair<GalleryFoodEntity, List<GalleryIngredientEntity>>? {
        val food = galleryDao.getFoodById(id) ?: return null
        val ingredients = galleryDao.getIngredients(id)
        return food to ingredients
    }

    /**
     * Save a detected food (from the analysis screen) to the gallery.
     * Converts domain model to database entities.
     */
    suspend fun saveDetectedFood(
        food: DetectedFood,
        photoPath: String? = null,
        insights: MealInsights? = null,
        aiSource: AiSource? = null
    ): Long {
        val entity = GalleryFoodEntity(
            name = food.label,
            photoPath = photoPath,
            defaultPortionGrams = food.estimatedGrams,
            totalCalories = food.totalCalories,
            totalProtein = food.totalProtein,
            totalFat = food.totalFat,
            totalCarbs = food.totalCarbs,
            totalFiber = food.totalFiber,
            isDrink = food.isDrink,
            insightsJson = insights?.let { MealJson.encodeInsights(it) },
            aiSource = aiSource?.storedName,
            aiModel = aiSource?.model
        )
        val ingredientEntities = food.ingredients.map { galleryIngredientOf(it) }
        return galleryDao.saveFoodWithIngredients(entity, ingredientEntities)
    }

    /** Map a domain [Ingredient] to its gallery row (galleryFoodId is set by the DAO transaction). */
    private fun galleryIngredientOf(ingredient: Ingredient): GalleryIngredientEntity {
        val m = ingredient.microsPer100g
        return GalleryIngredientEntity(
            galleryFoodId = 0,
            name = ingredient.name,
            grams = ingredient.grams,
            caloriesPer100g = ingredient.caloriesPer100g,
            proteinPer100g = ingredient.proteinPer100g,
            fatPer100g = ingredient.fatPer100g,
            carbsPer100g = ingredient.carbsPer100g,
            fiberPer100g = ingredient.fiberPer100g,
            waterMlPer100g = ingredient.waterMlPer100g,
            vitaminAPer100g = m.vitaminAMcg,
            vitaminCPer100g = m.vitaminCMg,
            vitaminDPer100g = m.vitaminDMcg,
            calciumPer100g = m.calciumMg,
            ironPer100g = m.ironMg,
            potassiumPer100g = m.potassiumMg,
            sodiumPer100g = m.sodiumMg,
            vitaminB12Per100g = m.vitaminB12Mcg,
            folatePer100g = m.folateMcg,
            vitaminB6Per100g = m.vitaminB6Mg,
            magnesiumPer100g = m.magnesiumMg,
            zincPer100g = m.zincMg,
            vitaminEPer100g = m.vitaminEMg
        )
    }

    /**
     * Replace a saved food's ingredient list and recompute its totals — used when the user edits
     * grams or adds/removes ingredients on the saved-food detail screen. Keeps the food's photo,
     * notes, thumbnail and cached AI analysis intact.
     */
    suspend fun updateFoodIngredients(foodId: Long, ingredients: List<Ingredient>) {
        val existing = galleryDao.getFoodById(foodId) ?: return
        val food = DetectedFood(
            label = existing.name,
            confidence = 1f,
            boundingBox = android.graphics.RectF(),
            estimatedGrams = ingredients.sumOf { it.grams.toDouble() }.toFloat(),
            ingredients = ingredients,
            isDrink = existing.isDrink
        )
        val updated = existing.copy(
            defaultPortionGrams = food.totalGrams,
            totalCalories = food.totalCalories,
            totalProtein = food.totalProtein,
            totalFat = food.totalFat,
            totalCarbs = food.totalCarbs,
            totalFiber = food.totalFiber
        )
        galleryDao.saveFoodWithIngredients(updated, ingredients.map { galleryIngredientOf(it) })
    }

    /** Persist a freshly generated AI analysis onto a saved food so it sticks for next time. */
    suspend fun saveInsights(foodId: Long, insights: MealInsights, source: AiSource?) {
        val existing = galleryDao.getFoodById(foodId) ?: return
        galleryDao.updateFood(
            existing.copy(
                insightsJson = MealJson.encodeInsights(insights),
                aiSource = source?.storedName ?: existing.aiSource,
                aiModel = source?.model ?: existing.aiModel
            )
        )
    }

    /** Save a single ingredient (e.g. from Manual Entry or a scanned product) to the gallery. */
    suspend fun saveIngredient(ingredient: Ingredient): Long {
        val food = DetectedFood(
            label = ingredient.name,
            confidence = 1f,
            boundingBox = android.graphics.RectF(),
            estimatedGrams = ingredient.grams,
            ingredients = listOf(ingredient),
            variations = emptyList()
        )
        return saveDetectedFood(food)
    }

    /** The cached AI analysis saved with a gallery food, or null if none. */
    fun insightsForFood(food: GalleryFoodEntity): MealInsights? =
        food.insightsJson?.let { MealJson.decodeInsights(it) }

    suspend fun markUsed(id: Long) = galleryDao.markUsed(id)

    suspend fun deleteFood(food: GalleryFoodEntity) = galleryDao.deleteFood(food)

    // ---- Categories / subcategories ----

    fun getCategories(): Flow<List<GalleryCategoryEntity>> = galleryDao.getCategories()

    /** Create a top-level category ([parentId] == null) or a subcategory under one. */
    suspend fun addCategory(name: String, parentId: Long? = null): Long =
        galleryDao.insertCategory(GalleryCategoryEntity(name = name.trim(), parentId = parentId))

    suspend fun renameCategory(category: GalleryCategoryEntity, newName: String) =
        galleryDao.updateCategory(category.copy(name = newName.trim()))

    /**
     * Delete a category and (if it's top-level) its subcategories, un-filing any foods that were in
     * them so nothing is orphaned to a missing category.
     */
    suspend fun deleteCategory(id: Long) {
        val subIds = galleryDao.getSubcategories(id).map { it.id }
        galleryDao.clearFoodsInCategories(listOf(id) + subIds)
        galleryDao.deleteCategoryAndChildren(id)
    }

    /** File a saved food under a category/subcategory, or null to make it unsorted. */
    suspend fun assignFoodToCategory(foodId: Long, categoryId: Long?) =
        galleryDao.updateFoodCategory(foodId, categoryId)

    /** Rename a saved food (its logged history keeps the name it was logged under). */
    suspend fun renameFood(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) galleryDao.updateName(id, trimmed)
    }

    suspend fun updateNotes(id: Long, notes: String) = galleryDao.updateNotes(id, notes)

    suspend fun updateThumbnail(id: Long, path: String) = galleryDao.updateThumbnail(id, path)

    /**
     * Convert a gallery food + ingredients back to domain model for editing.
     */
    suspend fun toDomainModel(id: Long): DetectedFood? {
        val (food, ingredients) = getFoodWithIngredients(id) ?: return null
        return DetectedFood(
            label = food.name,
            confidence = 1f,
            boundingBox = android.graphics.RectF(),
            estimatedGrams = food.defaultPortionGrams,
            ingredients = ingredients.map { ing ->
                Ingredient(
                    name = ing.name,
                    grams = ing.grams,
                    caloriesPer100g = ing.caloriesPer100g,
                    proteinPer100g = ing.proteinPer100g,
                    fatPer100g = ing.fatPer100g,
                    carbsPer100g = ing.carbsPer100g,
                    fiberPer100g = ing.fiberPer100g,
                    waterMlPer100g = ing.waterMlPer100g,
                    microsPer100g = Micronutrients(
                        vitaminAMcg = ing.vitaminAPer100g,
                        vitaminCMg = ing.vitaminCPer100g,
                        vitaminDMcg = ing.vitaminDPer100g,
                        calciumMg = ing.calciumPer100g,
                        ironMg = ing.ironPer100g,
                        potassiumMg = ing.potassiumPer100g,
                        sodiumMg = ing.sodiumPer100g,
                        vitaminB12Mcg = ing.vitaminB12Per100g,
                        folateMcg = ing.folatePer100g,
                        vitaminB6Mg = ing.vitaminB6Per100g,
                        magnesiumMg = ing.magnesiumPer100g,
                        zincMg = ing.zincPer100g,
                        vitaminEMg = ing.vitaminEPer100g
                    )
                )
            },
            isDrink = food.isDrink
        )
    }
}
