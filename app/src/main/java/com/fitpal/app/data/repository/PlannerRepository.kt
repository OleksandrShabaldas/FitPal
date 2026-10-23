package com.fitpal.app.data.repository

import com.fitpal.app.data.local.MealJson
import com.fitpal.app.data.local.dao.PlannedMealDao
import com.fitpal.app.data.local.entity.PlannedMealEntity
import com.fitpal.app.domain.model.PlannedRecipe
import com.fitpal.app.ml.AiSource
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The meal planner's meals (docs/redesign/10): ideas on the results screen, saved ideas, scheduled
 * meals (shown on Home, never counted) and the link to the log they became. Logging itself goes
 * through [MealRepository] like every other log.
 */
@Singleton
class PlannerRepository @Inject constructor(
    private val dao: PlannedMealDao
) {
    /** Store one generation's recipes as ideas. [day] = the plan's first day. */
    suspend fun addIdeas(requestId: String, day: LocalDate, recipes: List<PlannedRecipe>, source: AiSource?): List<Long> =
        dao.insertAll(recipes.map { r ->
            PlannedMealEntity(
                requestId = requestId,
                status = PlannedMealEntity.IDEA,
                date = day.plusDays(r.dayOffset.toLong()).toString(),
                mealType = r.mealType,
                name = r.name,
                recipeJson = MealJson.encodeRecipe(r),
                kcalPerServing = r.kcal,
                proteinPerServing = r.protein,
                servings = r.servings,
                aiSource = source?.engine?.name,
                aiModel = source?.model
            )
        })

    fun forRequest(requestId: String): Flow<List<PlannedMealEntity>> = dao.forRequest(requestId)
    fun scheduledFor(date: LocalDate): Flow<List<PlannedMealEntity>> = dao.scheduledFor(date.toString())
    fun upcoming(): Flow<List<PlannedMealEntity>> = dao.upcoming(LocalDate.now().toString())
    fun saved(): Flow<List<PlannedMealEntity>> = dao.saved()
    fun latestOpenRequest(): Flow<String?> = dao.latestOpenRequest()
    fun observe(id: Long): Flow<PlannedMealEntity?> = dao.observe(id)
    suspend fun get(id: Long): PlannedMealEntity? = dao.get(id)

    /** Put a meal on a day (it shows on Home that day, uncounted). */
    suspend fun schedule(id: Long, date: LocalDate, mealType: String) {
        val m = dao.get(id) ?: return
        dao.update(m.copy(status = PlannedMealEntity.SCHEDULED, date = date.toString(), mealType = mealType))
    }

    suspend fun saveIdea(id: Long) = dao.setStatus(id, PlannedMealEntity.SAVED)
    suspend fun dismiss(id: Long) = dao.setStatus(id, PlannedMealEntity.DISMISSED)

    /** Back to an idea (e.g. "Remove" on a scheduled row keeps the recipe, off the day). */
    suspend fun unschedule(id: Long) = dao.setStatus(id, PlannedMealEntity.SAVED)

    /** The recipe was edited (ingredients changed before logging). */
    suspend fun updateRecipe(id: Long, recipe: PlannedRecipe) {
        val m = dao.get(id) ?: return
        dao.update(
            m.copy(
                name = recipe.name, recipeJson = MealJson.encodeRecipe(recipe),
                kcalPerServing = recipe.kcal, proteinPerServing = recipe.protein
            )
        )
    }

    /** Replace one idea with a regenerated recipe, in place. */
    suspend fun replace(id: Long, recipe: PlannedRecipe, source: AiSource?) {
        val m = dao.get(id) ?: return
        dao.update(
            m.copy(
                name = recipe.name, recipeJson = MealJson.encodeRecipe(recipe), mealType = recipe.mealType,
                kcalPerServing = recipe.kcal, proteinPerServing = recipe.protein, servings = recipe.servings,
                aiSource = source?.engine?.name, aiModel = source?.model, createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun markLogged(id: Long, mealLogId: Long) {
        val m = dao.get(id) ?: return
        dao.update(m.copy(status = PlannedMealEntity.LOGGED, loggedMealLogId = mealLogId))
    }

    // ---- Backup ----
    suspend fun all(): List<PlannedMealEntity> = dao.all()
    suspend fun replaceAll(meals: List<PlannedMealEntity>) {
        dao.clearAll()
        dao.insertAll(meals)
    }
}
