package com.fitpal.app.ui.screen.coach

import android.graphics.RectF
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.local.MealJson
import com.fitpal.app.data.local.entity.PlannedMealEntity
import com.fitpal.app.data.local.entity.UsdaFoodEntity
import com.fitpal.app.data.repository.ExerciseRepository
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.NutritionRepository
import com.fitpal.app.data.repository.PlannerRepository
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.data.repository.StepRepository
import com.fitpal.app.data.repository.WeightRepository
import com.fitpal.app.data.repository.mealLogMeta
import com.fitpal.app.domain.Allergens
import com.fitpal.app.domain.BmrCalculator
import com.fitpal.app.domain.DailyTargets
import com.fitpal.app.domain.model.DetectedFood
import com.fitpal.app.domain.model.EatenAt
import com.fitpal.app.domain.model.Ingredient
import com.fitpal.app.domain.model.LogDecision
import com.fitpal.app.domain.model.PlannedRecipe
import com.fitpal.app.domain.model.RecipeIngredient
import com.fitpal.app.ml.AiSource
import com.fitpal.app.ml.FoodAnalysisPipeline
import com.fitpal.app.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * One planned meal's recipe (docs/redesign/10): read it, change the ingredients if you cooked it
 * differently (saved as you go, the allergen check re-run), cook along, and log it — through the
 * normal log path, as one meal linked back to the plan.
 */
@HiltViewModel
class RecipeViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val planner: PlannerRepository,
    private val meals: MealRepository,
    private val nutrition: NutritionRepository,
    private val pipeline: FoodAnalysisPipeline,
    private val settings: SettingsRepository,
    private val weights: WeightRepository,
    private val steps: StepRepository,
    private val exercise: ExerciseRepository
) : ViewModel() {

    private val id: Long = savedState.get<String>(Screen.PlannedMeal.ARG_ID)?.toLongOrNull()
        ?: savedState.get<Long>(Screen.PlannedMeal.ARG_ID) ?: -1L

    private val _entity = MutableStateFlow<PlannedMealEntity?>(null)
    val entity: StateFlow<PlannedMealEntity?> = _entity
    private val _recipe = MutableStateFlow<PlannedRecipe?>(null)
    val recipe: StateFlow<PlannedRecipe?> = _recipe

    val searchQuery = MutableStateFlow("")
    private val _results = MutableStateFlow<List<UsdaFoodEntity>>(emptyList())
    val results: StateFlow<List<UsdaFoodEntity>> = _results
    private val _aiAdding = MutableStateFlow(false)
    val aiAdding: StateFlow<Boolean> = _aiAdding
    private val _logged = MutableStateFlow(false)
    val logged: StateFlow<Boolean> = _logged
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving
    private var searchJob: Job? = null

    val targets: StateFlow<DailyTargets?> = combine(weights.getLatest(), settings.userProfile) { w, profile ->
        w?.let { BmrCalculator.dailyTargets(profile, it.weightKg, settings.dailyCalorieGoal.value, settings.macroSelection.value) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _leftToday = MutableStateFlow<Int?>(null)
    /** Kcal left today (Home ring's maths) — for "fits your day". */
    val leftToday: StateFlow<Int?> = _leftToday

    init {
        viewModelScope.launch {
            val e = planner.get(id) ?: return@launch
            _entity.value = e
            _recipe.value = MealJson.decodeRecipe(e.recipeJson)
            val kg = weights.getLatest().first()?.weightKg
            if (kg != null) {
                val t = BmrCalculator.dailyTargets(settings.userProfile.value, kg, settings.dailyCalorieGoal.value, settings.macroSelection.value)
                val iso = LocalDate.now().toString()
                val activity = steps.getCaloriesBurned(iso).first() + exercise.getTotalBurnedForDate(iso).first()
                _leftToday.value = (t.calories + activity - meals.getDailyNutrition(iso).first().calories).roundToInt()
            }
        }
    }

    val source: AiSource? get() = _entity.value?.let { AiSource.fromName(it.aiSource, it.aiModel) }

    /** The day it logs to: its planned day when that's today or earlier (a late log), else today — you're eating it now. */
    val logDate: LocalDate
        get() = _entity.value?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.takeIf { !it.isAfter(LocalDate.now()) } ?: LocalDate.now()

    // ---- Edits (saved straight away; the allergen check follows) ----

    private fun edit(change: (List<RecipeIngredient>) -> List<RecipeIngredient>) {
        val r = _recipe.value ?: return
        val ingredients = change(r.ingredients)
        val next = r.copy(
            ingredients = ingredients,
            allergens = Allergens.found(ingredients.map { it.ingredient.name }, settings.foodProfile.value.allAllergies)
        )
        _recipe.value = next
        viewModelScope.launch { planner.updateRecipe(id, next) }
    }

    fun setGrams(index: Int, grams: Float) = edit { list -> list.mapIndexed { i, ri -> if (i == index) ri.copy(ingredient = ri.ingredient.withGrams(grams)) else ri } }
    fun remove(index: Int) = edit { list -> list.filterIndexed { i, _ -> i != index } }

    fun onSearch(query: String) {
        searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) { _results.value = emptyList(); return }
        searchJob = viewModelScope.launch {
            delay(250)
            _results.value = nutrition.searchFoodsOnline(query, limit = 30)
        }
    }

    fun clearSearch() { searchQuery.value = ""; _results.value = emptyList() }

    fun addFromDatabase(food: UsdaFoodEntity) {
        val ingredient = Ingredient(
            name = food.description,
            grams = food.commonServingGrams ?: 100f,
            caloriesPer100g = food.caloriesPer100g,
            proteinPer100g = food.proteinPer100g,
            fatPer100g = food.fatPer100g,
            carbsPer100g = food.carbsPer100g,
            fiberPer100g = food.fiberPer100g,
            sourceFoodId = food.fdcId
        )
        edit { it + RecipeIngredient(ingredient, have = true) }
        clearSearch()
    }

    fun addWithAi(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _aiAdding.value = true
            val added = runCatching { pipeline.describeMeal(text).flatMap { it.ingredients } }.getOrDefault(emptyList())
            edit { it + added.map { ing -> RecipeIngredient(ing, have = true) } }
            _aiAdding.value = false
            clearSearch()
        }
    }

    /** Log it as one meal (one dish holding its ingredients), linked to the plan. */
    fun log(decision: LogDecision) {
        val e = _entity.value ?: return
        val r = _recipe.value ?: return
        if (r.ingredients.isEmpty()) return
        viewModelScope.launch {
            _saving.value = true
            val food = DetectedFood(
                label = r.name, confidence = 1f, boundingBox = RectF(),
                estimatedGrams = r.ingredients.sumOf { it.ingredient.grams.toDouble() }.toFloat(),
                ingredients = r.ingredients.map { it.ingredient }
            )
            val date = logDate
            val mealLogId = meals.logMeal(
                foods = listOf(food),
                mealType = e.mealType ?: r.mealType,
                date = date.toString(),
                source = source,
                meta = mealLogMeta(date, EatenAt(), decision, emptySet()).copy(plannedMealId = e.id)
            )
            planner.markLogged(e.id, mealLogId)
            _saving.value = false
            _logged.value = true
        }
    }

    /** The food as it would be logged — for the fasting check's intake. */
    fun asFood(): DetectedFood? = _recipe.value?.let { r ->
        DetectedFood(r.name, 1f, RectF(), 0f, r.ingredients.map { it.ingredient })
    }
}
