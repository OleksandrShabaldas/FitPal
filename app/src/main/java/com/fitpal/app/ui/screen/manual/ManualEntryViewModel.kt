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
 * One food in the meal being built, still being sized. [base] is **one helping** — its `grams` is
 * the portion, not the total — and [count] is how many of those helpings. Each item is edited in
 * place in the meal (no separate portion popup).
 */
data class DraftItem(
    val base: Ingredient,
    val count: Int = 1,
    /** The database's own serving size, kept so a flip back to Food can restore it. */
    val foodPortion: Float = DEFAULT_FOOD_PORTION,
    /** True once the portion has been typed or tapped — after that nothing overwrites it. */
    val portionEdited: Boolean = false
) {
    /** What actually gets logged: the portion multiplied by the amount. */
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
    val draft: List<DraftItem> = emptyList(),
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    /** Names of draft items already saved to the collection (for the filled-bookmark state). */
    val savedNames: Set<String> = emptySet()
) {
    val totalCalories: Float get() = draft.sumOf { it.total.calories.toDouble() }.toFloat()
}

@HiltViewModel
class ManualEntryViewModel @Inject constructor(
    private val nutritionRepository: NutritionRepository,
    private val mealRepository: MealRepository,
    private val galleryRepository: GalleryRepository,
    private val settingsRepository: SettingsRepository,
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

    /** Hide a food from every future database search (a local flag), and drop it from the current results. */
    fun hideFood(food: UsdaFoodEntity) {
        settingsRepository.hideFood(food.fdcId)
        _uiState.update { s -> s.copy(searchResults = s.searchResults.filterNot { it.fdcId == food.fdcId }) }
    }

    /**
     * Tapping a search result adds the food straight to the meal — sized to a typical helping — and
     * lands you back on the meal, where its portion, amount and food/drink are all editable inline.
     * There's no separate popup, and it's never a blind add: the controls sit right on the item, so
     * nothing has to be found again and resized (the reason the old tap-to-append was dropped).
     */
    fun pickFood(food: UsdaFoodEntity) {
        val drink = com.fitpal.app.domain.Drinks.isDrink(food.description, food.foodCategory)
        val foodPortion = food.commonServingGrams ?: DraftItem.DEFAULT_FOOD_PORTION
        val base = Ingredient(
            name = food.description,
            // A typical serving if the database knows one; otherwise a glass / a plateful.
            grams = food.commonServingGrams ?: (if (drink) DraftItem.DEFAULT_DRINK_PORTION else foodPortion),
            caloriesPer100g = food.caloriesPer100g,
            proteinPer100g = food.proteinPer100g,
            fatPer100g = food.fatPer100g,
            carbsPer100g = food.carbsPer100g,
            waterMlPer100g = waterFor(drink, food.carbsPer100g, food.proteinPer100g, food.fatPer100g),
            isDrink = drink
        )
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                draft = it.draft + DraftItem(base, foodPortion = foodPortion),
                query = "",
                searchResults = emptyList()
            )
        }
    }

    /** The portion — one helping, in g or ml. */
    fun setDraftPortion(index: Int, grams: Float) =
        updateDraft(index) { it.copy(base = it.base.withGrams(grams), portionEdited = true) }

    /** How many helpings; the meal carries them as one item of the multiplied size. */
    fun setDraftCount(index: Int, count: Int) =
        updateDraft(index) { it.copy(count = count.coerceIn(1, DraftItem.MAX_COUNT)) }

    /**
     * Food ↔ drink for a draft item. Besides swapping the unit and the presets, a drink carries its
     * water content, which is what makes it count toward the day's hydration once logged. A portion
     * the user hasn't touched also moves to the new unit's default — "30 ml" of a drink you flipped
     * from a 30 g food is never what was meant. A typed portion is left alone.
     */
    fun setDraftDrink(index: Int, drink: Boolean) = updateDraft(index) { item ->
        val base = item.base
        val grams = when {
            item.portionEdited -> base.grams
            drink -> DraftItem.DEFAULT_DRINK_PORTION
            else -> item.foodPortion
        }
        item.copy(
            base = base.copy(
                grams = grams,
                isDrink = drink,
                waterMlPer100g = waterFor(drink, base.carbsPer100g, base.proteinPer100g, base.fatPer100g)
            )
        )
    }

    /** Rename a draft item — database entries come with catalogue names you'd never read back. */
    fun renameDraftItem(index: Int, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        updateDraft(index) { it.copy(base = it.base.copy(name = clean)) }
    }

    private fun waterFor(drink: Boolean, carbs: Float, protein: Float, fat: Float): Float =
        if (drink) com.fitpal.app.domain.Drinks.estimateWaterPer100(carbs, protein, fat) else 0f

    private fun updateDraft(index: Int, transform: (DraftItem) -> DraftItem) {
        _uiState.update { state ->
            if (index !in state.draft.indices) return@update state
            val draft = state.draft.toMutableList()
            draft[index] = transform(draft[index])
            state.copy(draft = draft)
        }
    }

    /** Dismiss the search results to get back to the meal you're building. */
    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update { it.copy(query = "", searchResults = emptyList()) }
    }

    fun saveToGallery(index: Int) {
        val ingredient = _uiState.value.draft.getOrNull(index)?.total ?: return
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
                mealRepository.logItems(items.map { it.total }, _mealType.value, date = logDateIso())
                _uiState.update { it.copy(isSaving = false, saved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }
}
