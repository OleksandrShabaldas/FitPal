package com.fitpal.app.ui.screen.gallery

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.local.entity.UsdaFoodEntity
import com.fitpal.app.data.repository.GalleryRepository
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.NutritionRepository
import com.fitpal.app.domain.HealthScorer
import com.fitpal.app.domain.model.DetectedFood
import com.fitpal.app.domain.model.Ingredient
import com.fitpal.app.domain.model.MealInsights
import com.fitpal.app.ml.AiSource
import com.fitpal.app.ml.FoodAnalysisPipeline
import com.fitpal.app.ml.FoodJsonParser
import com.fitpal.app.ml.FoodPrompts
import com.fitpal.app.ml.ModelManager
import com.fitpal.app.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class GalleryFoodDetailUiState(
    val food: DetectedFood? = null,
    val name: String = "",
    val photoPath: String? = null,
    val ingredients: List<Ingredient> = emptyList(),
    val insights: MealInsights? = null,
    val aiSource: AiSource? = null,
    /** Which engine produced freshly generated insights (badge). */
    val insightsSource: AiSource? = null,
    val isLoading: Boolean = true,
    val isLoadingAi: Boolean = false,
    val modelReady: Boolean = true,
    // Add-ingredient search
    val searchQuery: String = "",
    val searchResults: List<UsdaFoodEntity> = emptyList(),
    val isAiAddingIngredient: Boolean = false,
    val logged: Boolean = false,
    val deleted: Boolean = false
)

/**
 * Opens a saved (gallery) food as a full editable detail — the same capabilities as a logged meal:
 * edit ingredient grams, add/remove ingredients, see the full AI analysis (or generate it), and log
 * it from here. Edits are written straight back to the saved food.
 */
@HiltViewModel
class GalleryFoodDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val galleryRepository: GalleryRepository,
    private val mealRepository: MealRepository,
    private val nutritionRepository: NutritionRepository,
    private val pipeline: FoodAnalysisPipeline,
    private val modelManager: ModelManager
) : ViewModel() {

    private val foodId: Long = savedStateHandle.get<Long>(Screen.GalleryFoodDetail.ARG_FOOD_ID) ?: 0L

    /** AI is usable if the online model is available OR the on-device model is downloaded. */
    private fun aiAvailable(): Boolean = pipeline.canUseOnline() || modelManager.isLlmReady

    private val _uiState = MutableStateFlow(GalleryFoodDetailUiState())
    val uiState: StateFlow<GalleryFoodDetailUiState> = _uiState

    private val _mealType = MutableStateFlow(mealRepository.defaultMealType())
    val mealType: StateFlow<String> = _mealType
    fun setMealType(type: String) { _mealType.value = type }

    private val _logDate = MutableStateFlow(LocalDate.now())
    val logDate: StateFlow<LocalDate> = _logDate
    fun setLogDate(date: LocalDate) { _logDate.value = date }

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val entity = galleryRepository.getFoodWithIngredients(foodId)?.first
            val domain = galleryRepository.toDomainModel(foodId)
            if (entity == null || domain == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            _uiState.update {
                it.copy(
                    food = domain,
                    name = entity.name,
                    photoPath = entity.photoPath,
                    ingredients = domain.ingredients,
                    insights = galleryRepository.insightsForFood(entity),
                    aiSource = AiSource.fromName(entity.aiSource, entity.aiModel),
                    isLoading = false,
                    modelReady = aiAvailable()
                )
            }
        }
    }

    // ---------------- Ingredient editing ----------------

    fun updateIngredientGrams(index: Int, newGrams: Float) {
        if (newGrams <= 0f) return
        val current = _uiState.value.ingredients.toMutableList()
        if (index !in current.indices) return
        current[index] = current[index].withGrams(newGrams)
        persistIngredients(current)
    }

    fun removeIngredient(index: Int) {
        val current = _uiState.value.ingredients.toMutableList()
        if (index !in current.indices) return
        current.removeAt(index)
        if (current.isEmpty()) {
            // Removing the last ingredient deletes the whole saved food.
            delete()
        } else {
            persistIngredients(current)
        }
    }

    fun addIngredient(food: UsdaFoodEntity) {
        val grams = food.commonServingGrams ?: 100f
        val ingredient = Ingredient(
            name = food.description,
            grams = grams,
            caloriesPer100g = food.caloriesPer100g,
            proteinPer100g = food.proteinPer100g,
            fatPer100g = food.fatPer100g,
            carbsPer100g = food.carbsPer100g
        )
        _uiState.update { it.copy(searchQuery = "", searchResults = emptyList()) }
        persistIngredients(_uiState.value.ingredients + ingredient)
    }

    /** Describe an ingredient to the AI and add what it returns to this saved food. */
    fun addIngredientWithAi(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAiAddingIngredient = true) }
            val added = runCatching { pipeline.describeMeal(text).flatMap { it.ingredients } }.getOrDefault(emptyList())
            _uiState.update { it.copy(searchQuery = "", searchResults = emptyList(), isAiAddingIngredient = false) }
            persistIngredients(_uiState.value.ingredients + added)
        }
    }

    /**
     * Swap the ingredient at [index] for a different one, keeping its amount (grams) — so a
     * mislabelled item can be corrected in place instead of deleting and re-adding it.
     */
    fun replaceIngredient(index: Int, food: UsdaFoodEntity) {
        val current = _uiState.value.ingredients.toMutableList()
        if (index !in current.indices) return
        val grams = current[index].grams
        current[index] = Ingredient(
            name = food.description,
            grams = grams,
            caloriesPer100g = food.caloriesPer100g,
            proteinPer100g = food.proteinPer100g,
            fatPer100g = food.fatPer100g,
            carbsPer100g = food.carbsPer100g
        )
        _uiState.update { it.copy(searchQuery = "", searchResults = emptyList()) }
        persistIngredients(current)
    }

    /** Describe the correct ingredient to the AI and swap it in at [index] (keeping the amount). */
    fun replaceIngredientWithAi(index: Int, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAiAddingIngredient = true) }
            val added = runCatching { pipeline.describeMeal(text).flatMap { it.ingredients } }.getOrDefault(emptyList())
            val current = _uiState.value.ingredients.toMutableList()
            if (index in current.indices && added.isNotEmpty()) {
                val grams = current[index].grams
                current[index] = added.first().withGrams(grams)
                if (added.size > 1) current.addAll(index + 1, added.drop(1))
            }
            _uiState.update { it.copy(searchQuery = "", searchResults = emptyList(), isAiAddingIngredient = false) }
            persistIngredients(current)
        }
    }

    /** Save the new ingredient list, recompute totals, and refresh the screen. */
    private fun persistIngredients(ingredients: List<Ingredient>) {
        _uiState.update { it.copy(ingredients = ingredients) }
        viewModelScope.launch {
            galleryRepository.updateFoodIngredients(foodId, ingredients)
            // Reload the domain model so header/macro totals reflect the change.
            val refreshed = galleryRepository.toDomainModel(foodId)
            _uiState.update { it.copy(food = refreshed ?: it.food) }
        }
    }

    // ---------------- Add-ingredient search ----------------

    private var searchJob: Job? = null

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(250)
            val results = nutritionRepository.searchFoodsOnline(query, limit = 30)
            _uiState.update { it.copy(searchResults = results) }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update { it.copy(searchQuery = "", searchResults = emptyList()) }
    }

    // ---------------- AI insights ----------------

    fun generateAiInsights() {
        if (!aiAvailable()) return
        val food = _uiState.value.food ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingAi = true) }
            val scored = HealthScorer.score(
                calories = food.totalCalories,
                protein = food.totalProtein,
                fat = food.totalFat,
                carbs = food.totalCarbs,
                fiber = food.totalFiber,
                grams = food.totalGrams,
                isDrink = food.isDrink,
                micros = food.totalMicros
            )
            try {
                val unit = if (food.isDrink) "ml" else "g"
                val prompt = FoodPrompts.itemInsights(
                    name = food.label,
                    amountLabel = "${food.totalGrams.toInt()}$unit",
                    kcal = food.totalCalories.toInt(),
                    protein = food.totalProtein.toInt(),
                    fat = food.totalFat.toInt(),
                    carbs = food.totalCarbs.toInt(),
                    fiber = food.totalFiber.toInt(),
                    ingredients = _uiState.value.ingredients.joinToString(", ") { "${it.name} ${it.grams.toInt()}$unit" }
                )
                val (response, source) = pipeline.generateRawTextWithSource(prompt)

                // Only keep swaps that name something really in this food.
                val swaps = FoodJsonParser.parseItemSwaps(
                    response,
                    listOf(food.label) + _uiState.value.ingredients.map { it.name }
                )
                val energy = Regex("ENERGY:\\s*(.+)").find(response)?.groupValues?.get(1)?.trim() ?: ""
                val mood = Regex("MOOD:\\s*(.+)").find(response)?.groupValues?.get(1)?.trim() ?: ""
                val energyScore = Regex("ENERGY_SCORE:\\s*(\\d+)").find(response)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, 5) ?: 0
                val moodScore = Regex("MOOD_SCORE:\\s*(\\d+)").find(response)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, 5) ?: 0
                val pairings = Regex("PAIR:\\s*(.+)").findAll(response).map { it.groupValues[1].trim() }.toList()

                val insights = MealInsights(
                    healthScore = scored.score,
                    scoreFactors = scored.factors,
                    healthSwaps = swaps,
                    energyImpact = energy,
                    moodImpact = mood,
                    pairingRecommendations = pairings,
                    energyScore = energyScore,
                    moodScore = moodScore
                )
                galleryRepository.saveInsights(foodId, insights, source)
                _uiState.update { it.copy(isLoadingAi = false, insights = insights, insightsSource = source) }
            } catch (e: Exception) {
                val insights = MealInsights(scored.score, scored.factors, emptyList(), "", "", emptyList())
                galleryRepository.saveInsights(foodId, insights, null)
                _uiState.update { it.copy(isLoadingAi = false, insights = insights) }
            }
        }
    }

    /** Forget the cached analysis and regenerate (after editing the food). */
    fun regenerateInsights() {
        _uiState.update { it.copy(insights = null) }
        generateAiInsights()
    }

    // ---------------- Log / delete ----------------

    /** Log this saved food to the chosen day + meal, carrying its photo and AI analysis. */
    fun logIt() {
        val food = _uiState.value.food ?: return
        viewModelScope.launch {
            mealRepository.logMeal(
                foods = listOf(food),
                mealType = _mealType.value,
                photoPath = _uiState.value.photoPath,
                insights = _uiState.value.insights,
                date = _logDate.value.format(DateTimeFormatter.ISO_LOCAL_DATE),
                source = _uiState.value.aiSource
            )
            galleryRepository.markUsed(foodId)
            _uiState.update { it.copy(logged = true) }
        }
    }

    fun delete() {
        viewModelScope.launch {
            galleryRepository.getFoodWithIngredients(foodId)?.let { galleryRepository.deleteFood(it.first) }
            _uiState.update { it.copy(deleted = true) }
        }
    }
}
