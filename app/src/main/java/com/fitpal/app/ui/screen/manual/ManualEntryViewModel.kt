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

/**
 * A food that was just tapped in the search results and is being sized before it joins the meal.
 * [base] is **one helping** — its `grams` is the portion, not the total — and [count] is how many
 * of those helpings.
 */
data class PickedFood(
    val base: Ingredient,
    val count: Int = 1,
    /** The database's own serving size, kept so a flip back to Food can restore it. */
    val foodPortion: Float = DEFAULT_FOOD_PORTION,
    /** True once the portion has been typed or tapped — after that nothing overwrites it. */
    val portionEdited: Boolean = false
) {
    /** What actually gets added to the meal: the portion multiplied by the amount. */
    val total: Ingredient get() = base.withGrams(base.grams * count)

    companion object {
        const val DEFAULT_FOOD_PORTION = 100f
        /** A glass — what "some of this drink" means when the database has no serving size. */
        const val DEFAULT_DRINK_PORTION = 250f
        const val MAX_COUNT = 99
    }
}

data class ManualEntryUiState(
    val query: String = "",
    val searchResults: List<UsdaFoodEntity> = emptyList(),
    val draft: List<Ingredient> = emptyList(),
    /** The food being sized in the portion sheet; null when the sheet is closed. */
    val picked: PickedFood? = null,
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

    /**
     * Tapping a search result opens the portion sheet — nothing joins the meal until it's
     * confirmed. Tapping used to add a default helping straight to a growing pile, which meant
     * every food still had to be found again in that pile and resized.
     */
    fun pickFood(food: UsdaFoodEntity) {
        val drink = com.fitpal.app.domain.Drinks.isDrink(food.description, food.foodCategory)
        val foodPortion = food.commonServingGrams ?: PickedFood.DEFAULT_FOOD_PORTION
        val base = Ingredient(
            name = food.description,
            // A typical serving if the database knows one; otherwise a glass / a plateful.
            grams = food.commonServingGrams ?: (if (drink) PickedFood.DEFAULT_DRINK_PORTION else foodPortion),
            caloriesPer100g = food.caloriesPer100g,
            proteinPer100g = food.proteinPer100g,
            fatPer100g = food.fatPer100g,
            carbsPer100g = food.carbsPer100g,
            waterMlPer100g = waterFor(drink, food.carbsPer100g, food.proteinPer100g, food.fatPer100g),
            isDrink = drink
        )
        _uiState.update { it.copy(picked = PickedFood(base, foodPortion = foodPortion)) }
    }

    /** The portion — one helping, in g or ml. */
    fun setPickedPortion(grams: Float) =
        updatePicked { it.copy(base = it.base.withGrams(grams), portionEdited = true) }

    /** How many helpings; the sheet adds them as one item of the multiplied size. */
    fun setPickedCount(count: Int) =
        updatePicked { it.copy(count = count.coerceIn(1, PickedFood.MAX_COUNT)) }

    /**
     * Food ↔ drink. Besides swapping the unit and the presets in the sheet, a drink carries its
     * water content, which is what makes it count toward the day's hydration once logged.
     *
     * An amount the user hasn't touched also moves to the new unit's default — "30 ml" of a drink
     * you flipped from a 30 g food is never what was meant. A typed amount is left alone.
     */
    fun setPickedDrink(drink: Boolean) = updatePicked { picked ->
        val base = picked.base
        val grams = when {
            picked.portionEdited -> base.grams
            drink -> PickedFood.DEFAULT_DRINK_PORTION
            else -> picked.foodPortion
        }
        picked.copy(
            base = base.copy(
                grams = grams,
                isDrink = drink,
                waterMlPer100g = waterFor(drink, base.carbsPer100g, base.proteinPer100g, base.fatPer100g)
            )
        )
    }

    private fun waterFor(drink: Boolean, carbs: Float, protein: Float, fat: Float): Float =
        if (drink) com.fitpal.app.domain.Drinks.estimateWaterPer100(carbs, protein, fat) else 0f

    private fun updatePicked(transform: (PickedFood) -> PickedFood) {
        _uiState.update { state ->
            state.picked?.let { state.copy(picked = transform(it)) } ?: state
        }
    }

    fun dismissPicked() {
        _uiState.update { it.copy(picked = null) }
    }

    /**
     * Add the sized food to the meal. The search is cleared on the way out so you land back on
     * the meal you're building and can see what you just added.
     */
    fun confirmPicked() {
        val picked = _uiState.value.picked ?: return
        val item = picked.total
        if (item.grams <= 0f) return
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                draft = it.draft + item,
                picked = null,
                query = "",
                searchResults = emptyList()
            )
        }
    }

    /** Dismiss the search results to get back to the meal you're building. */
    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update { it.copy(query = "", searchResults = emptyList()) }
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
