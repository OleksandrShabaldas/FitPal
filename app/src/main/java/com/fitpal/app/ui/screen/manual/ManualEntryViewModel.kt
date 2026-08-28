package com.fitpal.app.ui.screen.manual

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.local.entity.UsdaFoodEntity
import com.fitpal.app.data.repository.BarcodeRepository
import com.fitpal.app.data.repository.GalleryRepository
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.NutritionRepository
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.domain.Drinks
import com.fitpal.app.domain.MealLogContext
import com.fitpal.app.domain.model.DetectedFood
import com.fitpal.app.domain.model.DietaryWarning
import com.fitpal.app.domain.model.Ingredient
import com.fitpal.app.domain.model.ServingPreset
import com.fitpal.app.ml.DietaryGate
import com.fitpal.app.ml.FoodAnalysisPipeline
import com.fitpal.app.ml.NUTRITION_LABEL_PROMPT
import com.fitpal.app.ml.decodeUprightBitmap
import com.fitpal.app.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

/** The three ways to add a food to the meal, shown as tabs in the builder. */
enum class AddTab { SEARCH, CUSTOM, BARCODE }

data class ManualEntryUiState(
    val query: String = "",
    val searchResults: List<UsdaFoodEntity> = emptyList(),
    val draft: List<DraftItem> = emptyList(),
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    /** Names of draft items already saved to the collection (for the filled-bookmark state). */
    val savedNames: Set<String> = emptySet(),
    /** Pending dietary-rule warning ("near your dessert limit — log anyway?"); null when none. */
    val dietaryWarning: DietaryWarning? = null,
    /** Which add method is showing (Search / Custom / Barcode). */
    val activeTab: AddTab = AddTab.SEARCH,
    /** True while a "describe to AI" call (on the Search tab) is running. */
    val isDescribing: Boolean = false,
    /** True while a scanned barcode is being looked up. */
    val barcodeLookingUp: Boolean = false,
    /** True when the last scanned barcode wasn't in any database (offer the Custom tab instead). */
    val barcodeNotFound: Boolean = false,
    /** The last barcode scanned, so "not found → add custom" can pre-fill / explain. */
    val lastScannedCode: String? = null,
    /** One-shot toast message (e.g. "Added Cola", or an AI-describe miss); cleared after shown. */
    val message: String? = null
) {
    val totalCalories: Float get() = draft.sumOf { it.total.calories.toDouble() }.toFloat()
}

@HiltViewModel
class ManualEntryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val nutritionRepository: NutritionRepository,
    private val mealRepository: MealRepository,
    private val galleryRepository: GalleryRepository,
    private val settingsRepository: SettingsRepository,
    private val dietaryGate: DietaryGate,
    private val barcodeRepository: BarcodeRepository,
    private val pipeline: FoodAnalysisPipeline,
    mealLogContext: MealLogContext
) : ViewModel() {

    // Which tab to open on — the "Custom food" tile lands on Custom, "Search foods" on Search.
    private val startTab: AddTab = runCatching {
        savedStateHandle.get<String>(Screen.ManualEntry.ARG_START_TAB)?.let { AddTab.valueOf(it) }
    }.getOrNull() ?: AddTab.SEARCH

    private val _uiState = MutableStateFlow(ManualEntryUiState(activeTab = startTab))
    val uiState: StateFlow<ManualEntryUiState> = _uiState

    /** Switch the add-method tab (and clear a stale "barcode not found" state). */
    fun setTab(tab: AddTab) = _uiState.update { it.copy(activeTab = tab, barcodeNotFound = false) }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

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

    // ---- Add via "describe to AI" (Search tab) ----

    /**
     * Describe a food to the AI in plain words and add whatever it returns to the meal — the same
     * pipeline the Describe-to-AI screen uses, so an off-database food is still one tap. Each returned
     * dish becomes one meal item.
     */
    fun describeToAi(text: String) {
        val query = text.trim()
        if (query.isBlank()) return
        searchJob?.cancel()
        viewModelScope.launch {
            _uiState.update { it.copy(isDescribing = true) }
            val foods = runCatching { pipeline.describeMeal(query) }.getOrDefault(emptyList())
            val newDrafts = foods
                .filter { it.totalCalories > 0f || it.ingredients.isNotEmpty() }
                .map { DraftItem(it.asSingleIngredient(), portionEdited = true) }
            _uiState.update {
                if (newDrafts.isEmpty()) it.copy(isDescribing = false, message = "The AI couldn't work that out — try wording it differently, or use the Custom tab.")
                else it.copy(draft = it.draft + newDrafts, isDescribing = false, query = "", searchResults = emptyList())
            }
        }
    }

    // ---- Add via barcode (Barcode tab) ----

    /** Look a scanned barcode up (Open Food Facts / local DB) and add the product to the meal. */
    fun scanBarcode(code: String) {
        _uiState.update { it.copy(barcodeLookingUp = true, barcodeNotFound = false, lastScannedCode = code) }
        viewModelScope.launch {
            val product = runCatching { barcodeRepository.lookup(code) }.getOrNull()
            if (product == null) {
                _uiState.update { it.copy(barcodeLookingUp = false, barcodeNotFound = true) }
            } else {
                val drink = Drinks.isDrink(product.description, product.foodCategory)
                val ingredient = Ingredient(
                    name = product.description,
                    grams = product.commonServingGrams ?: 100f,
                    caloriesPer100g = product.caloriesPer100g,
                    proteinPer100g = product.proteinPer100g,
                    fatPer100g = product.fatPer100g,
                    carbsPer100g = product.carbsPer100g,
                    waterMlPer100g = if (drink) Drinks.estimateWaterPer100(product.carbsPer100g, product.proteinPer100g, product.fatPer100g) else 0f,
                    isDrink = drink
                )
                _uiState.update {
                    it.copy(
                        barcodeLookingUp = false,
                        draft = it.draft + DraftItem(ingredient, portionEdited = true),
                        message = "Added ${product.description}"
                    )
                }
            }
        }
    }

    /** Clear a "barcode not found" so the Barcode tab is ready to scan again. */
    fun dismissBarcodeResult() = _uiState.update { it.copy(barcodeNotFound = false, lastScannedCode = null) }

    // ---- Add via custom values (Custom tab) ----

    /** Add a hand-entered food (typed values, or read from a snapped label) to the meal. */
    fun addCustomIngredient(ingredient: Ingredient) {
        if (ingredient.name.isBlank()) return
        _uiState.update {
            it.copy(
                draft = it.draft + DraftItem(ingredient, portionEdited = true),
                message = "Added ${ingredient.name}"
            )
        }
    }

    /**
     * Read a snapped nutrition-facts label with the AI (Custom tab). Returns the single food it read,
     * or null if it couldn't — the tab fills its own fields from the result. Shares the decode + prompt
     * with the standalone Custom food screen.
     */
    suspend fun readNutritionLabel(path: String): DetectedFood? {
        val bitmap = withContext(Dispatchers.IO) { decodeUprightBitmap(path) } ?: return null
        return runCatching { pipeline.analyze(bitmap, note = NUTRITION_LABEL_PROMPT) }.getOrNull()?.firstOrNull()
    }

    /**
     * Collapse a detected/described dish into one per-100 g [Ingredient] so it becomes a single meal
     * item (the manual meal stores one ingredient per item). Preserves macros, water and micros.
     */
    private fun DetectedFood.asSingleIngredient(): Ingredient {
        val g = totalGrams.takeIf { it > 0f } ?: 100f
        val per100 = 100f / g
        return Ingredient(
            name = label,
            grams = g,
            caloriesPer100g = totalCalories * per100,
            proteinPer100g = totalProtein * per100,
            fatPer100g = totalFat * per100,
            carbsPer100g = totalCarbs * per100,
            fiberPer100g = totalFiber * per100,
            waterMlPer100g = totalWaterMl * per100,
            isDrink = isDrink,
            // totalMicros is absolute (at g grams); scaledTo(x) = micros·x/100, so 100·per100 gives per-100 g.
            microsPer100g = totalMicros.scaledTo(100f * per100)
        )
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
            val isToday = _logDate.value == java.time.LocalDate.now()
            val warning = dietaryGate.checkKnownFoods(items.map { it.total.name to it.total.calories }, isToday)
            if (warning != null) _uiState.update { it.copy(dietaryWarning = warning) }
            else performLog()
        }
    }

    /** User confirmed the dietary-rule warning ("log anyway") — go ahead and save. */
    fun confirmDietaryWarning() {
        _uiState.update { it.copy(dietaryWarning = null) }
        viewModelScope.launch { performLog() }
    }

    /** User backed out of the dietary-rule warning — abort the log. */
    fun dismissDietaryWarning() {
        _uiState.update { it.copy(dietaryWarning = null) }
    }

    private suspend fun performLog() {
        val items = _uiState.value.draft
        if (items.isEmpty()) return
        _uiState.update { it.copy(isSaving = true) }
        try {
            mealRepository.logItems(items.map { it.total }, _mealType.value, date = logDateIso())
            _uiState.update { it.copy(isSaving = false, saved = true) }
        } catch (e: Exception) {
            _uiState.update { it.copy(isSaving = false) }
        }
    }
}
