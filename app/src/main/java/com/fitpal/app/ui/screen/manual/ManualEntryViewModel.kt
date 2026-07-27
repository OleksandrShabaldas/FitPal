package com.fitpal.app.ui.screen.manual

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.local.entity.UsdaFoodEntity
import com.fitpal.app.data.repository.GalleryRepository
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.NutritionRepository
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.domain.MealLogContext
import com.fitpal.app.domain.model.Ingredient
import com.fitpal.app.domain.model.ServingPreset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ManualEntryUiState(
    val query: String = "",
    val searchResults: List<UsdaFoodEntity> = emptyList(),
    val draft: List<Ingredient> = emptyList(),
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    /** Names of draft items already saved to the collection (for the filled-bookmark state). */
    val savedNames: Set<String> = emptySet()
) {
    val totalCalories: Float get() = draft.sumOf { it.calories.toDouble() }.toFloat()
}

@HiltViewModel
class ManualEntryViewModel @Inject constructor(
    private val nutritionRepository: NutritionRepository,
    private val mealRepository: MealRepository,
    private val galleryRepository: GalleryRepository,
    settingsRepository: SettingsRepository,
    mealLogContext: MealLogContext
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManualEntryUiState())
    val uiState: StateFlow<ManualEntryUiState> = _uiState

    val servingPresets: StateFlow<List<ServingPreset>> = settingsRepository.mealPresets
    val drinkPresets: StateFlow<List<ServingPreset>> = settingsRepository.drinkPresets

    private val _mealType =
        MutableStateFlow(mealLogContext.consume() ?: mealRepository.defaultMealType())
    val mealType: StateFlow<String> = _mealType
    fun setMealType(type: String) { _mealType.value = type }

    /** The day to log to — starts from Home's "+" (or today), user-editable. */
    private val _logDate = MutableStateFlow(
        mealLogContext.consumeDate()?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
            ?: java.time.LocalDate.now()
    )
    val logDate: StateFlow<java.time.LocalDate> = _logDate
    fun setLogDate(date: java.time.LocalDate) { _logDate.value = date }
    private fun logDateIso(): String = _logDate.value.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)

    init {
        // Make sure the built-in foods are available for searching.
        viewModelScope.launch { nutritionRepository.ensureSeeded() }
    }

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        // Debounce; search the full local DB (USDA + imported European foods) and fall back to
        // Open Food Facts online when local hits are sparse, so regional products show up too.
        searchJob = viewModelScope.launch {
            delay(250)
            val results = nutritionRepository.searchFoodsOnline(query, limit = 30)
            _uiState.update { it.copy(searchResults = results) }
        }
    }

    /** Add a searched food to the draft meal using its typical serving size. */
    fun addFood(food: UsdaFoodEntity) {
        val grams = food.commonServingGrams ?: 100f
        val drink = com.fitpal.app.domain.Drinks.isDrink(food.description, food.foodCategory)
        val ingredient = Ingredient(
            name = food.description,
            grams = grams,
            caloriesPer100g = food.caloriesPer100g,
            proteinPer100g = food.proteinPer100g,
            fatPer100g = food.fatPer100g,
            carbsPer100g = food.carbsPer100g,
            waterMlPer100g = if (drink) com.fitpal.app.domain.Drinks.estimateWaterPer100(food.carbsPer100g, food.proteinPer100g, food.fatPer100g) else 0f,
            isDrink = drink
        )
        _uiState.update {
            it.copy(
                draft = it.draft + ingredient,
                query = "",
                searchResults = emptyList()
            )
        }
    }

    fun updateGrams(index: Int, newGrams: Float) {
        _uiState.update { state ->
            val draft = state.draft.toMutableList()
            if (index in draft.indices) {
                draft[index] = draft[index].withGrams(newGrams)
            }
            state.copy(draft = draft)
        }
    }

    fun saveToGallery(ingredient: Ingredient) {
        viewModelScope.launch {
            galleryRepository.saveIngredient(ingredient)
            _uiState.update { it.copy(savedNames = it.savedNames + ingredient.name) }
        }
    }

    fun removeItem(index: Int) {
        _uiState.update { state ->
            val draft = state.draft.toMutableList()
            if (index in draft.indices) draft.removeAt(index)
            state.copy(draft = draft)
        }
    }

    fun logMeal() {
        val items = _uiState.value.draft
        if (items.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                mealRepository.logItems(items, _mealType.value, date = logDateIso())
                _uiState.update { it.copy(isSaving = false, saved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }
}
