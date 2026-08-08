package com.fitpal.app.ui.screen.describe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.local.entity.UsdaFoodEntity
import com.fitpal.app.data.repository.GalleryRepository
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.NutritionRepository
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.domain.MealLogContext
import com.fitpal.app.domain.model.DetectedFood
import com.fitpal.app.domain.model.Ingredient
import com.fitpal.app.domain.model.ServingPreset
import com.fitpal.app.ml.AiSource
import com.fitpal.app.ml.AnalysisJobManager
import com.fitpal.app.ml.FoodAnalysisPipeline
import com.fitpal.app.ml.JobKind
import com.fitpal.app.ml.JobStatus
import com.fitpal.app.ml.ModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DescribeFoodUiState(
    val description: String = "",
    val isAnalyzing: Boolean = false,
    val hasAnalyzed: Boolean = false,
    val foods: List<DetectedFood> = emptyList(),
    val noMatchesFound: Boolean = false,
    /** Which engine produced this result — drives the "online / on-device" badge. */
    val aiSource: AiSource? = null,
    /** If it fell back to on-device, why online wasn't used (shown beneath the badge). */
    val onlineError: String? = null,
    /** Set when the online model failed and we're awaiting the user's choice (retry / on-device). */
    val onlineFailedReason: String? = null,
    /** True once the user cancels the on-device confirm prompt — the screen should pop back. */
    val cancelled: Boolean = false,
    val needsModel: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<UsdaFoodEntity> = emptyList(),
    /** True while the "Add with AI" describe-an-ingredient call is running. */
    val isAiAddingIngredient: Boolean = false,
    /** Labels of results already saved to the collection (for the filled-bookmark state). */
    val savedLabels: Set<String> = emptySet()
) {
    val totalCalories: Float get() = foods.sumOf { it.totalCalories.toDouble() }.toFloat()
}

@HiltViewModel
class DescribeFoodViewModel @Inject constructor(
    private val modelManager: ModelManager,
    private val mealRepository: MealRepository,
    private val galleryRepository: GalleryRepository,
    private val nutritionRepository: NutritionRepository,
    private val jobManager: AnalysisJobManager,
    private val pipeline: FoodAnalysisPipeline,
    settingsRepository: SettingsRepository,
    mealLogContext: MealLogContext
) : ViewModel() {

    private val _uiState = MutableStateFlow(DescribeFoodUiState())
    val uiState: StateFlow<DescribeFoodUiState> = _uiState

    val mealPresets: StateFlow<List<ServingPreset>> = settingsRepository.mealPresets
    val drinkPresets: StateFlow<List<ServingPreset>> = settingsRepository.drinkPresets

    private val _mealType =
        MutableStateFlow(mealLogContext.consume() ?: mealRepository.defaultMealType())
    val mealType: StateFlow<String> = _mealType
    fun setMealType(type: String) {
        _mealType.value = type
        jobManager.updateMealType(type)
    }

    /** The day to log to — starts from Home's "+" (or today), user-editable. */
    private val _logDate = MutableStateFlow(
        mealLogContext.consumeDate()?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
            ?: java.time.LocalDate.now()
    )
    val logDate: StateFlow<java.time.LocalDate> = _logDate

    private fun logDateIso(): String = _logDate.value.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)

    fun setLogDate(date: java.time.LocalDate) {
        _logDate.value = date
        jobManager.updateTargetDate(logDateIso())
    }

    private var adoptedJobId: String? = null

    init {
        // If a background text analysis is already running/finished, adopt it.
        jobManager.current?.takeIf { it.kind == JobKind.TEXT }?.let { job ->
            _mealType.value = job.mealType
            job.targetDate?.let { iso -> runCatching { java.time.LocalDate.parse(iso) }.getOrNull()?.let { _logDate.value = it } }
            _uiState.update { it.copy(description = job.description) }
        }
        observeJob()
    }

    fun onDescriptionChange(text: String) {
        _uiState.update { it.copy(description = text) }
    }

    /**
     * Called when the user navigates away (back). Drop a finished-but-unlogged result so it isn't
     * auto-saved on app close; leave a still-running analysis alone (it finishes in the background).
     */
    fun onLeaveWithoutLogging() {
        val job = jobManager.current ?: return
        if (job.kind != JobKind.TEXT || job.saved) return
        if (job.status !is JobStatus.Running) jobManager.discard()
    }

    /** From the "online failed" prompt: try the online model again. */
    fun retryOnline() {
        _uiState.update { it.copy(onlineFailedReason = null, isAnalyzing = true) }
        jobManager.chooseRetryOnline()
    }

    /** From the "online failed" prompt: give up on online and run the on-device model. */
    fun useOnDevice() {
        _uiState.update { it.copy(onlineFailedReason = null, isAnalyzing = true) }
        jobManager.chooseUseLocal()
    }

    /** From the "online failed" prompt: cancel the analysis entirely and leave the screen. */
    fun cancelAnalysis() {
        jobManager.chooseCancel()
        _uiState.update { it.copy(onlineFailedReason = null, isAnalyzing = false, cancelled = true) }
    }

    /** Hand the description to the background service so it survives leaving the screen/app. */
    fun analyze() {
        val text = _uiState.value.description
        if (text.isBlank()) return
        if (!modelManager.isLlmReady) {
            _uiState.update { it.copy(needsModel = true) }
            return
        }
        _uiState.update { it.copy(isAnalyzing = true, noMatchesFound = false, needsModel = false) }
        jobManager.startTextJob(text, _mealType.value, logDateIso())
    }

    private fun observeJob() {
        viewModelScope.launch {
            jobManager.state.collect { job ->
                if (job == null || job.kind != JobKind.TEXT) return@collect
                when (val s = job.status) {
                    is JobStatus.Running -> _uiState.update {
                        it.copy(isAnalyzing = true, aiSource = s.source, onlineFailedReason = null)
                    }
                    is JobStatus.OnlineFailed -> _uiState.update {
                        it.copy(isAnalyzing = false, onlineFailedReason = s.reason)
                    }
                    is JobStatus.Done -> {
                        if (adoptedJobId != job.id) {
                            adoptedJobId = job.id
                            _uiState.update {
                                it.copy(
                                    isAnalyzing = false, hasAnalyzed = true,
                                    foods = s.foods, noMatchesFound = s.foods.isEmpty(),
                                    aiSource = s.source, onlineError = s.onlineError
                                )
                            }
                        }
                    }
                    is JobStatus.Failed -> _uiState.update {
                        it.copy(isAnalyzing = false, hasAnalyzed = true, noMatchesFound = true)
                    }
                }
            }
        }
    }

    /** Set one ingredient's weight directly. */
    fun updateIngredientGrams(foodIndex: Int, ingredientIndex: Int, grams: Float) {
        if (grams < 0f) return
        _uiState.update { state ->
            val foods = state.foods.toMutableList()
            val food = foods.getOrNull(foodIndex) ?: return@update state
            val ingredients = food.ingredients.toMutableList()
            if (ingredientIndex in ingredients.indices) {
                ingredients[ingredientIndex] = ingredients[ingredientIndex].withGrams(grams)
                foods[foodIndex] = food.copy(ingredients = ingredients)
            }
            state.copy(foods = foods)
        }
    }

    /** Rename a dish before it's logged — what the AI called it is a suggestion, not a verdict. */
    fun renameFood(foodIndex: Int, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        _uiState.update { state ->
            val foods = state.foods.toMutableList()
            val food = foods.getOrNull(foodIndex) ?: return@update state
            foods[foodIndex] = food.copy(label = clean)
            state.copy(foods = foods)
        }
    }

    /** Set the whole dish's weight, scaling every ingredient proportionally. */
    fun scaleFoodToGrams(foodIndex: Int, newTotalGrams: Float) {
        if (newTotalGrams <= 0f) return
        _uiState.update { state ->
            val foods = state.foods.toMutableList()
            val food = foods.getOrNull(foodIndex) ?: return@update state
            val current = food.ingredients.sumOf { it.grams.toDouble() }.toFloat()
            if (current <= 0f) return@update state
            val factor = newTotalGrams / current
            foods[foodIndex] = food.copy(ingredients = food.ingredients.map { it.withGrams(it.grams * factor) })
            state.copy(foods = foods)
        }
    }

    /** Remove one ingredient; if it was the last one, drop the whole food. */
    fun removeIngredient(foodIndex: Int, ingredientIndex: Int) {
        _uiState.update { state ->
            val foods = state.foods.toMutableList()
            val food = foods.getOrNull(foodIndex) ?: return@update state
            val ingredients = food.ingredients.toMutableList()
            if (ingredientIndex in ingredients.indices) ingredients.removeAt(ingredientIndex)
            if (ingredients.isEmpty()) {
                foods.removeAt(foodIndex)
            } else {
                foods[foodIndex] = food.copy(ingredients = ingredients)
            }
            state.copy(foods = foods)
        }
    }

    fun toggleVariation(foodIndex: Int, variationIndex: Int) {
        _uiState.update { state ->
            val foods = state.foods.toMutableList()
            val food = foods.getOrNull(foodIndex) ?: return@update state
            val variations = food.variations.toMutableList()
            val variation = variations.getOrNull(variationIndex) ?: return@update state
            val nowSelected = !variation.isSelected
            val ingredients = food.ingredients.toMutableList()
            if (nowSelected) {
                ingredients.addAll(variation.ingredientChanges)
            } else {
                variation.ingredientChanges.forEach { change ->
                    val idx = ingredients.indexOfLast { it.name == change.name }
                    if (idx >= 0) ingredients.removeAt(idx)
                }
            }
            variations[variationIndex] = variation.copy(isSelected = nowSelected)
            foods[foodIndex] = food.copy(ingredients = ingredients, variations = variations)
            state.copy(foods = foods)
        }
    }

    /** Save a found food to the personal gallery for one-tap re-logging. */
    fun saveToGallery(foodIndex: Int) {
        val food = _uiState.value.foods.getOrNull(foodIndex) ?: return
        val source = _uiState.value.aiSource
        viewModelScope.launch {
            galleryRepository.saveDetectedFood(food, aiSource = source)
            _uiState.update { it.copy(savedLabels = it.savedLabels + food.label) }
        }
    }

    fun removeFood(foodIndex: Int) {
        _uiState.update { state ->
            val foods = state.foods.toMutableList()
            if (foodIndex in foods.indices) foods.removeAt(foodIndex)
            state.copy(foods = foods)
        }
    }

    // ---- Add an ingredient (searches the offline food DB), before logging ----

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
            // Only the built-in simple, non-branded foods (branded products come via barcode).
            _uiState.update { it.copy(searchResults = nutritionRepository.searchFoodsOnline(query, limit = 30)) }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update { it.copy(searchQuery = "", searchResults = emptyList()) }
    }

    /** Add a searched food as a new ingredient of [foodIndex] (in-memory, before logging). */
    fun addIngredient(foodIndex: Int, food: UsdaFoodEntity) {
        val ingredient = Ingredient(
            name = food.description,
            grams = food.commonServingGrams ?: 100f,
            caloriesPer100g = food.caloriesPer100g,
            proteinPer100g = food.proteinPer100g,
            fatPer100g = food.fatPer100g,
            carbsPer100g = food.carbsPer100g
        )
        _uiState.update { state ->
            val foods = state.foods.toMutableList()
            foods.getOrNull(foodIndex)?.let { f ->
                foods[foodIndex] = f.copy(ingredients = f.ingredients + ingredient)
            }
            state.copy(foods = foods, searchQuery = "", searchResults = emptyList())
        }
    }

    /** Describe an ingredient to the AI (online or on-device) and add what it returns. */
    fun addIngredientWithAi(foodIndex: Int, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAiAddingIngredient = true) }
            val added = runCatching { pipeline.describeMeal(text).flatMap { it.ingredients } }.getOrDefault(emptyList())
            _uiState.update { state ->
                val foods = state.foods.toMutableList()
                foods.getOrNull(foodIndex)?.let { f -> foods[foodIndex] = f.copy(ingredients = f.ingredients + added) }
                state.copy(foods = foods, searchQuery = "", searchResults = emptyList(), isAiAddingIngredient = false)
            }
        }
    }

    fun logMeal() {
        val foods = _uiState.value.foods
        if (foods.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                mealRepository.logMeal(
                    foods, _mealType.value,
                    date = logDateIso(),
                    source = _uiState.value.aiSource
                )
                jobManager.completeByUser()
                _uiState.update { it.copy(isSaving = false, saved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }
}
