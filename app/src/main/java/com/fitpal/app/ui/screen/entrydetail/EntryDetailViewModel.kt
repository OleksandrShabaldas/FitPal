package com.fitpal.app.ui.screen.entrydetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.graphics.RectF
import com.fitpal.app.data.local.entity.MealLogItemEntity
import com.fitpal.app.data.local.entity.UsdaFoodEntity
import com.fitpal.app.data.repository.BarcodeRepository
import com.fitpal.app.data.repository.GalleryRepository
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.NutritionRepository
import com.fitpal.app.domain.Drinks
import com.fitpal.app.domain.HealthScorer
import com.fitpal.app.domain.model.DetectedFood
import com.fitpal.app.domain.model.Ingredient
import com.fitpal.app.domain.model.MealInsights
import com.fitpal.app.domain.model.Micronutrients
import com.fitpal.app.ml.AiSource
import com.fitpal.app.ml.FoodAnalysisPipeline
import com.fitpal.app.ml.FoodJsonParser
import com.fitpal.app.ml.FoodPrompts
import com.fitpal.app.ml.ModelManager
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

data class EntryDetailUiState(
    val item: MealLogItemEntity? = null,
    val mealType: String = "",
    /** The day this entry is logged on — so "copy to date" can center on it. */
    val entryDate: java.time.LocalDate = java.time.LocalDate.now(),
    val ingredients: List<Ingredient> = emptyList(),
    /**
     * The +/− "Amount" multiplier for this entry (how many of it were eaten). Grams stay the single
     * source of truth for calories/macros — this is a remembered display count
     * ([MealLogItemEntity.servings]) so reopening the entry shows what you last set it to.
     */
    val servings: Int = 1,
    /** The one-tap situation tag on this entry's meal ("Home"/"Restaurant"/…), or null. */
    val context: String? = null,
    /** A meal-aware coaching note the background worker may attach; null when there's nothing useful. */
    val coachingTip: com.fitpal.app.domain.model.CoachingTip? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    // AI insights — full detail, cached in the DB so we don't regenerate.
    val insights: MealInsights? = null,
    /** Which engine produced the freshly generated insights (badge). */
    val insightsSource: AiSource? = null,
    val isLoadingAi: Boolean = false,
    val modelReady: Boolean = true,
    // Add-ingredient search
    val searchQuery: String = "",
    val searchResults: List<UsdaFoodEntity> = emptyList(),
    /** True while the "Add with AI" describe-an-ingredient call is running. */
    val isAiAddingIngredient: Boolean = false,
    /** True while "Edit with AI" is re-evaluating the whole entry. */
    val isRefiningWithAi: Boolean = false,
    /** Set when an AI edit came back with nothing usable, so the screen can say so. */
    val refineError: String? = null,
    /** True once this meal has been saved to the collection (for a confirmation). */
    val savedToCollection: Boolean = false,
    /** True while a scanned barcode (in the Add-ingredient popup) is being looked up. */
    val barcodeLookingUp: Boolean = false,
    /** One-shot toast after adding an ingredient by custom/barcode (e.g. "Added Cola"); then cleared. */
    val addMessage: String? = null,
    /** One-shot confirmation after "copy to another date" — shown as a toast, then cleared. */
    val copyConfirmation: String? = null,
    val deleted: Boolean = false
)

@HiltViewModel
class EntryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mealRepository: MealRepository,
    private val nutritionRepository: NutritionRepository,
    private val galleryRepository: GalleryRepository,
    private val pipeline: FoodAnalysisPipeline,
    private val modelManager: ModelManager,
    private val barcodeRepository: BarcodeRepository
) : ViewModel() {

    private val entryId: Long = savedStateHandle.get<Long>(Screen.EntryDetail.ARG_ENTRY_ID) ?: 0L

    private val _uiState = MutableStateFlow(EntryDetailUiState())
    val uiState: StateFlow<EntryDetailUiState> = _uiState

    init {
        loadEntry()
        observeGeneratedInsights()
        observeMealContextAndTip()
    }

    /** Keep the meal's situation tag and coaching tip live (the tip is written later by the worker). */
    private fun observeMealContextAndTip() {
        viewModelScope.launch {
            val item = mealRepository.getItemById(entryId) ?: return@launch
            mealRepository.observeMealLog(item.mealLogId).collect { meal ->
                _uiState.update {
                    it.copy(
                        context = meal?.context,
                        coachingTip = meal?.let { m -> mealRepository.coachingTipFor(m) }
                    )
                }
            }
        }
    }

    /** Set (or clear, by re-tapping) the one-tap situation tag on this entry's meal. */
    fun setMealContext(context: String?) {
        val item = _uiState.value.item ?: return
        val next = if (context == _uiState.value.context) null else context
        _uiState.update { it.copy(context = next) }
        viewModelScope.launch { mealRepository.updateMealContext(item.mealLogId, next) }
    }

    /**
     * Fill in the AI overview the moment the background [com.fitpal.app.ml.InsightsWorker] writes it,
     * so logging a food and opening it straight away shows the overview without leaving and coming
     * back. Only fills an empty slot — never clobbers an edit or a manual (re)generation in progress.
     */
    private fun observeGeneratedInsights() {
        viewModelScope.launch {
            mealRepository.observeItem(entryId).collect { item ->
                if (item == null || item.insightsJson.isNullOrBlank()) return@collect
                val s = _uiState.value
                if (s.insights != null || s.isLoadingAi || s.isRefiningWithAi) return@collect
                val fresh = item.insightsGeneratedAt > 0 &&
                    (System.currentTimeMillis() - item.insightsGeneratedAt) <= RETENTION_MS
                if (fresh || item.galleryFoodId != null) {
                    mealRepository.insightsForItem(item)?.let { ins ->
                        _uiState.update { it.copy(insights = ins) }
                    }
                }
            }
        }
    }

    private fun loadEntry() {
        viewModelScope.launch {
            val item = mealRepository.getItemById(entryId)
            val mealType = mealRepository.getMealTypeForItem(entryId) ?: ""
            if (item == null) {
                _uiState.update { it.copy(isLoading = false, error = "Entry not found") }
                return@launch
            }
            val entryDate = mealRepository.getMealDate(item.mealLogId)
                ?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: java.time.LocalDate.now()

            // Ingredients: use the saved breakdown, or fall back to a single
            // synthetic ingredient derived from the item totals (older entries).
            val ingredients = mealRepository.ingredientsForItem(item)
                .ifEmpty { listOf(itemAsIngredient(item)) }

            // Insights: honour the 1-month retention rule. Keep cached insights if
            // they're fresh, or if this item is saved to the collection. Otherwise
            // wipe them so revisiting regenerates.
            val savedToCollection = item.galleryFoodId != null
            val fresh = item.insightsGeneratedAt > 0 &&
                (System.currentTimeMillis() - item.insightsGeneratedAt) <= RETENTION_MS
            val insights = if (item.insightsJson != null && (fresh || savedToCollection)) {
                mealRepository.insightsForItem(item)
            } else {
                if (item.insightsJson != null) mealRepository.clearInsights(item.id)
                null
            }

            _uiState.update {
                it.copy(
                    item = item,
                    mealType = mealType,
                    entryDate = entryDate,
                    ingredients = ingredients,
                    servings = item.servings.coerceAtLeast(1),
                    insights = insights,
                    isLoading = false,
                    modelReady = modelManager.isLlmReady
                )
            }
        }
    }

    /** Derive a single per-100g ingredient from an item that has no saved breakdown. */
    private fun itemAsIngredient(item: MealLogItemEntity): Ingredient {
        val g = item.grams.takeIf { it > 0f } ?: 100f
        val f = 100f / g
        return Ingredient(
            name = item.name,
            grams = item.grams,
            caloriesPer100g = item.calories * f,
            proteinPer100g = item.protein * f,
            fatPer100g = item.fat * f,
            carbsPer100g = item.carbs * f,
            fiberPer100g = item.fiber * f,
            waterMlPer100g = item.waterMl * f,
            microsPer100g = Micronutrients(
                vitaminAMcg = item.vitaminA * f,
                vitaminCMg = item.vitaminC * f,
                vitaminDMcg = item.vitaminD * f,
                calciumMg = item.calcium * f,
                ironMg = item.iron * f,
                potassiumMg = item.potassium * f,
                sodiumMg = item.sodium * f,
                vitaminB12Mcg = item.vitaminB12 * f,
                folateMcg = item.folate * f,
                vitaminB6Mg = item.vitaminB6 * f,
                magnesiumMg = item.magnesium * f,
                zincMg = item.zinc * f,
                vitaminEMg = item.vitaminE * f
            )
        )
    }

    // ---------------- Ingredient editing ----------------

    fun updateIngredientGrams(index: Int, newGrams: Float) {
        if (newGrams <= 0f) return
        val current = _uiState.value.ingredients.toMutableList()
        if (index !in current.indices) return
        current[index] = current[index].withGrams(newGrams)
        persistIngredients(current)
    }

    /**
     * The +/− "Amount" stepper: log this meal N times over. Scales every ingredient's weight by the
     * change (so calories/macros track it, exactly like the pre-log photo review — grams stay the
     * source of truth), and persists the count itself ([MealLogItemEntity.servings]) so reopening
     * the entry shows what it was left on instead of resetting to 1. If the amount was hand-edited
     * in between, the next step just scales from whatever's there now.
     */
    fun setServings(newServings: Int) {
        val n = newServings.coerceIn(1, MAX_SERVINGS)
        val current = _uiState.value.servings.coerceAtLeast(1)
        if (n == current) return
        val factor = n.toFloat() / current
        val scaled = _uiState.value.ingredients.map { it.withGrams(it.grams * factor) }
        _uiState.update { it.copy(servings = n) }
        viewModelScope.launch { mealRepository.updateItemServings(entryId, n) }
        persistIngredients(scaled)
    }

    /**
     * Scale the whole meal to a new total weight, keeping the ingredient proportions — so the user
     * can resize a logged meal in one go instead of editing each ingredient separately.
     */
    fun scaleMealTo(newTotalGrams: Float) {
        if (newTotalGrams <= 0f) return
        val current = _uiState.value.ingredients
        val curTotal = current.sumOf { it.grams.toDouble() }.toFloat()
        if (curTotal <= 0f) return
        val factor = newTotalGrams / curTotal
        persistIngredients(current.map { it.withGrams(it.grams * factor) })
    }

    fun removeIngredient(index: Int) {
        val current = _uiState.value.ingredients.toMutableList()
        if (index !in current.indices) return
        current.removeAt(index)
        if (current.isEmpty()) {
            // Removing the last ingredient deletes the whole entry.
            deleteEntry()
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
        val current = _uiState.value.ingredients + ingredient
        // Clear the search after adding.
        _uiState.update { it.copy(searchQuery = "", searchResults = emptyList()) }
        persistIngredients(current)
    }

    /** Describe an ingredient to the AI and add what it returns to this meal item. */
    fun addIngredientWithAi(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAiAddingIngredient = true) }
            val added = runCatching { pipeline.describeMeal(text).flatMap { it.ingredients } }.getOrDefault(emptyList())
            val current = _uiState.value.ingredients + added
            _uiState.update { it.copy(searchQuery = "", searchResults = emptyList(), isAiAddingIngredient = false) }
            persistIngredients(current)
        }
    }

    fun clearAddMessage() = _uiState.update { it.copy(addMessage = null) }

    /** Add a hand-entered ingredient (typed values, or read from a snapped label) to this meal. */
    fun addCustomIngredient(ingredient: Ingredient) {
        if (ingredient.name.isBlank()) return
        persistIngredients(_uiState.value.ingredients + ingredient)
        _uiState.update { it.copy(addMessage = "Added ${ingredient.name}") }
    }

    /** Look up a scanned barcode and add the product to this meal as an ingredient. */
    fun scanBarcodeIngredient(code: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(barcodeLookingUp = true) }
            val product = runCatching { barcodeRepository.lookup(code) }.getOrNull()
            _uiState.update { it.copy(barcodeLookingUp = false) }
            if (product == null) {
                _uiState.update { it.copy(addMessage = "That barcode isn't in any database — add it on the Custom tab.") }
                return@launch
            }
            val drink = Drinks.isDrink(product.description, product.foodCategory)
            addCustomIngredient(
                Ingredient(
                    name = product.description,
                    grams = product.commonServingGrams ?: 100f,
                    caloriesPer100g = product.caloriesPer100g,
                    proteinPer100g = product.proteinPer100g,
                    fatPer100g = product.fatPer100g,
                    carbsPer100g = product.carbsPer100g,
                    waterMlPer100g = if (drink) Drinks.estimateWaterPer100(product.carbsPer100g, product.proteinPer100g, product.fatPer100g) else 0f,
                    isDrink = drink
                )
            )
        }
    }

    /**
     * Read a snapped nutrition-facts label with the AI (Custom tab) and return the single food it read,
     * or null if it couldn't. Shares the decode + prompt with the Custom food screen (NutritionLabel.kt).
     */
    suspend fun readNutritionLabel(path: String): DetectedFood? {
        val bitmap = withContext(Dispatchers.IO) { decodeUprightBitmap(path) } ?: return null
        return runCatching { pipeline.analyze(bitmap, note = NUTRITION_LABEL_PROMPT) }.getOrNull()?.firstOrNull()
    }

    /**
     * Swap the ingredient at [index] for a different one, keeping the amount (grams) the user ate —
     * so a mislabelled item can be corrected in place instead of deleting and re-adding it.
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

    /**
     * "Edit with AI" for an already-logged entry: describe what's wrong in plain language
     * ("there was no cheese", "it was a large portion") and the AI re-evaluates the whole
     * thing — ingredients, amounts and the dish name.
     *
     * Same pipeline call the pre-log Analysis screen uses; this just writes the result back
     * to the stored entry instead of to a draft. Cached insights are cleared because they
     * described the old version of the meal.
     */
    fun refineWithAi(instruction: String) {
        if (instruction.isBlank()) return
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefiningWithAi = true, refineError = null) }
            val food = DetectedFood(
                label = item.name,
                confidence = 1f,
                boundingBox = RectF(),
                estimatedGrams = item.grams,
                ingredients = _uiState.value.ingredients,
                isDrink = item.isDrink
            )
            val refined = runCatching { pipeline.refineFood(food, instruction) }.getOrNull()

            if (refined == null || refined.ingredients.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isRefiningWithAi = false,
                        refineError = "The AI didn't come back with a usable change. Try wording it differently."
                    )
                }
                return@launch
            }

            mealRepository.renameItem(entryId, refined.label.ifBlank { item.name })
            mealRepository.clearInsights(entryId)
            _uiState.update { it.copy(isRefiningWithAi = false, insights = null, insightsSource = null) }
            persistIngredients(refined.ingredients)
        }
    }

    fun clearRefineError() {
        _uiState.update { it.copy(refineError = null) }
    }

    /**
     * Rename this entry. Works whatever it came from — an AI guess, a barcode, or a database food
     * carrying its catalogue name ("Cheese, cheddar, sharp"). Only the label changes; the
     * nutrition, ingredients and history stay exactly as logged.
     */
    fun renameEntry(name: String) {
        val item = _uiState.value.item ?: return
        val clean = name.trim()
        if (clean.isEmpty() || clean == item.name) return
        viewModelScope.launch {
            mealRepository.renameItem(entryId, clean)
            _uiState.update { it.copy(item = it.item?.copy(name = clean)) }
        }
    }

    /** Save the new ingredient list, recompute totals, and refresh the screen. */
    private fun persistIngredients(ingredients: List<Ingredient>) {
        _uiState.update { it.copy(ingredients = ingredients) }
        viewModelScope.launch {
            mealRepository.updateItemIngredients(entryId, ingredients)
            // Reload the item so the header/macro totals reflect the change.
            val updated = mealRepository.getItemById(entryId)
            _uiState.update { it.copy(item = updated) }
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
            // Full local DB (USDA + imported European brands) plus an online Open Food Facts
            // fallback when local results are sparse — so regional products are findable too.
            val results = nutritionRepository.searchFoodsOnline(query, limit = 30)
            _uiState.update { it.copy(searchResults = results) }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update { it.copy(searchQuery = "", searchResults = emptyList()) }
    }

    /** Save this logged meal (its current ingredients) to the collection for quick re-logging. */
    fun saveToCollection() {
        val item = _uiState.value.item ?: return
        val ingredients = _uiState.value.ingredients
        if (ingredients.isEmpty()) return
        viewModelScope.launch {
            val food = DetectedFood(
                label = item.name,
                confidence = 1f,
                boundingBox = RectF(),
                estimatedGrams = item.grams,
                ingredients = ingredients,
                isDrink = item.isDrink
            )
            galleryRepository.saveDetectedFood(
                food = food,
                photoPath = item.photoPath,
                insights = _uiState.value.insights,
                aiSource = com.fitpal.app.ml.AiSource.fromName(item.aiSource, item.aiModel)
            )
            _uiState.update { it.copy(savedToCollection = true) }
        }
    }

    // ---------------- Delete ----------------

    fun deleteEntry() {
        viewModelScope.launch {
            mealRepository.deleteItem(entryId)
            _uiState.update { it.copy(deleted = true) }
        }
    }

    /** Change this entry's meal category (Breakfast/Lunch/Dinner/Snack). */
    fun setMealType(type: String) {
        val item = _uiState.value.item ?: return
        if (type == _uiState.value.mealType) return
        viewModelScope.launch {
            mealRepository.setItemMealType(item, type)
            // The item id is stable; refresh it so its parent log is current for further edits.
            val refreshed = mealRepository.getItemById(entryId)
            _uiState.update { it.copy(mealType = type, item = refreshed ?: it.item) }
        }
    }

    /** Copy this logged entry onto another day ([copies] times), into the chosen meal category. */
    fun copyToDate(date: java.time.LocalDate, mealType: String? = null, copies: Int = 1) {
        val item = _uiState.value.item ?: return
        val n = copies.coerceIn(1, 20)
        viewModelScope.launch {
            try {
                repeat(n) {
                    mealRepository.copyItemToDate(item, date.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE), mealType)
                }
                val label = com.fitpal.app.ui.component.logDateLabel(date).lowercase()
                _uiState.update {
                    it.copy(copyConfirmation = if (n == 1) "Copied to $label" else "$n copies to $label")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(copyConfirmation = "Couldn't copy: ${e.message}") }
            }
        }
    }

    fun clearCopyConfirmation() {
        _uiState.update { it.copy(copyConfirmation = null) }
    }

    // ---------------- AI insights ----------------

    fun generateAiInsights() {
        if (!modelManager.isLlmReady) return
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingAi = true) }
            // Health score + factors are deterministic (always consistent & complete).
            val scored = scoreItem(item)
            try {
                val unit = if (item.isDrink) "ml" else "g"
                val prompt = FoodPrompts.itemInsights(
                    name = item.name,
                    amountLabel = "${item.grams.toInt()}$unit",
                    kcal = item.calories.toInt(),
                    protein = item.protein.toInt(),
                    fat = item.fat.toInt(),
                    carbs = item.carbs.toInt(),
                    fiber = item.fiber.toInt(),
                    ingredients = _uiState.value.ingredients.joinToString(", ") { "${it.name} ${it.grams.toInt()}$unit" }
                )
                val (response, source) = pipeline.generateRawTextWithSource(prompt)

                // Only keep swaps that name something really in this meal.
                val swaps = FoodJsonParser.parseItemSwaps(
                    response,
                    listOf(item.name) + _uiState.value.ingredients.map { it.name }
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

                // Cache so we never regenerate this one (until retention wipes it).
                mealRepository.saveInsights(entryId, insights)
                _uiState.update { it.copy(isLoadingAi = false, insights = insights, insightsSource = source) }
            } catch (e: Exception) {
                // Even if the AI text fails, the deterministic score still shows.
                val insights = MealInsights(scored.score, scored.factors, emptyList(), "", "", emptyList())
                mealRepository.saveInsights(entryId, insights)
                _uiState.update { it.copy(isLoadingAi = false, insights = insights) }
            }
        }
    }

    /** Forget cached insights so the user can regenerate after editing the meal. */
    fun regenerateInsights() {
        viewModelScope.launch {
            mealRepository.clearInsights(entryId)
            _uiState.update { it.copy(insights = null) }
            generateAiInsights()
        }
    }

    /** Run the deterministic health rubric over a logged item. */
    private fun scoreItem(item: MealLogItemEntity): HealthScorer.Result =
        HealthScorer.score(
            calories = item.calories,
            protein = item.protein,
            fat = item.fat,
            carbs = item.carbs,
            fiber = item.fiber,
            grams = item.grams,
            isDrink = item.isDrink,
            micros = Micronutrients(
                vitaminAMcg = item.vitaminA,
                vitaminCMg = item.vitaminC,
                vitaminDMcg = item.vitaminD,
                calciumMg = item.calcium,
                ironMg = item.iron,
                potassiumMg = item.potassium,
                sodiumMg = item.sodium,
                vitaminB12Mcg = item.vitaminB12,
                folateMcg = item.folate,
                vitaminB6Mg = item.vitaminB6,
                magnesiumMg = item.magnesium,
                zincMg = item.zinc,
                vitaminEMg = item.vitaminE
            )
        )

    companion object {
        /** Cached insights live for one month unless the food is saved to the collection. */
        private const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000
        /** Upper bound for the "Amount" multiplier (matches the pre-log review). */
        private const val MAX_SERVINGS = 50
    }
}
