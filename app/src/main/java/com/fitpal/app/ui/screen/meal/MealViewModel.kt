package com.fitpal.app.ui.screen.meal

import android.graphics.RectF
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.local.entity.MealLogItemEntity
import com.fitpal.app.data.local.entity.UsdaFoodEntity
import com.fitpal.app.data.repository.BarcodeRepository
import com.fitpal.app.data.repository.GalleryRepository
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.NutritionRepository
import com.fitpal.app.domain.Drinks
import com.fitpal.app.domain.HealthScorer
import com.fitpal.app.domain.model.CoachingTip
import com.fitpal.app.domain.model.DetectedFood
import com.fitpal.app.domain.model.EatenAt
import com.fitpal.app.domain.model.Ingredient
import com.fitpal.app.domain.model.MealInsights
import com.fitpal.app.domain.model.Micronutrients
import com.fitpal.app.domain.model.asDrinkPart
import com.fitpal.app.ml.AiSource
import com.fitpal.app.ml.FoodAnalysisPipeline
import com.fitpal.app.ml.FoodJsonParser
import com.fitpal.app.ml.FoodPrompts
import com.fitpal.app.ml.ModelManager
import com.fitpal.app.ml.NUTRITION_LABEL_PROMPT
import com.fitpal.app.ml.decodeUprightBitmap
import com.fitpal.app.ui.component.logDateLabel
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** One dish of a logged meal: the item, its ingredients (each part's food/drink flag set), and its insights. */
data class MealDish(
    val item: MealLogItemEntity,
    val ingredients: List<Ingredient>,
    /** Its cached AI overview (fresh, or kept because it's in the collection), or null. */
    val insights: MealInsights? = null,
    /** Which model wrote the overview just now (a freshly generated one). */
    val insightsSource: AiSource? = null,
    val loadingInsights: Boolean = false
)

data class MealUiState(
    val dishes: List<MealDish> = emptyList(),
    val mealType: String = "",
    /** The name the user gave this whole meal, or null while it's unnamed. */
    val mealName: String? = null,
    /** The one-tap situation tags on this meal ("Home, Family meal"), or null if untagged. */
    val context: String? = null,
    /** When this meal was eaten (its time source says whether it came from the photo). */
    val eatenAt: EatenAt = EatenAt(),
    /** A meal-aware coaching note the background worker may attach; null when there's nothing useful. */
    val coachingTip: CoachingTip? = null,
    /** The planner's meal this was logged from, if any — the page offers its recipe. */
    val plannedMealId: Long? = null,
    /** The day this meal is logged on. */
    val date: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    /** True once the whole meal was deleted — the page pops back. */
    val deleted: Boolean = false,
    /** The dish that was tapped to get here (a single row on Home, the Water page…) — opened at it. */
    val focusItemId: Long? = null,
    /** Dishes saved to the collection from this page. */
    val savedItemIds: Set<Long> = emptySet(),
    val modelReady: Boolean = true,
    // Adding or replacing an ingredient (one dialog at a time, for one dish).
    val searchQuery: String = "",
    val searchResults: List<UsdaFoodEntity> = emptyList(),
    val isAiAddingIngredient: Boolean = false,
    val barcodeLookingUp: Boolean = false,
    /** One-shot toast after adding an ingredient by custom/barcode; then cleared. */
    val addMessage: String? = null,
    /** The dish "Edit with AI" is re-reading, if any. */
    val refiningItemId: Long? = null,
    val refineError: String? = null,
    /** One-shot confirmation after "copy to another day" — a toast, then cleared. */
    val copyConfirmation: String? = null
) {
    val totalCalories: Float get() = dishes.sumOf { it.item.calories.toDouble() }.toFloat()
    val totalProtein: Float get() = dishes.sumOf { it.item.protein.toDouble() }.toFloat()
    val totalFat: Float get() = dishes.sumOf { it.item.fat.toDouble() }.toFloat()
    val totalCarbs: Float get() = dishes.sumOf { it.item.carbs.toDouble() }.toFloat()
    val totalFiber: Float get() = dishes.sumOf { it.item.fiber.toDouble() }.toFloat()

    /** The shared photo of the generation, if any. */
    val photoPath: String? get() = dishes.firstOrNull { !it.item.photoPath.isNullOrBlank() }?.item?.photoPath

    /** Which AI produced this meal — every dish came from the same generation, so the first that recorded one speaks for all. */
    val aiSource: AiSource? get() = dishes.firstNotNullOfOrNull { AiSource.fromName(it.item.aiSource, it.item.aiModel) }

    /**
     * Logged from Analysis, every dish carries the meal's own overview: shown once for the meal.
     * Null when the dishes have their own (or none).
     */
    val sharedInsights: MealInsights?
        get() {
            if (dishes.size < 2) return null
            val json = dishes.first().item.insightsJson ?: return null
            return if (dishes.all { it.item.insightsJson == json }) dishes.first().insights else null
        }

    fun saved(itemId: Long): Boolean = itemId in savedItemIds || dishes.any { it.item.id == itemId && it.item.galleryFoodId != null }
}

/**
 * The one Meal page (docs/redesign/16) for every logged meal, whether it has one dish or several —
 * it replaced the meal group overview and the single-entry screen. Opened by meal (a meal group on
 * Home, the Fasting page) or by one of its items (a single row on Home, the Water, Caffeine and Limits
 * pages), which opens that item's meal at that dish. Everything the old entry screen did now works on
 * any dish: ingredients, food or drink per ingredient, amount, add / replace (database, AI, custom,
 * barcode, label), Edit with AI, insights, save, copy, remove.
 */
@HiltViewModel
class MealViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mealRepository: MealRepository,
    private val nutritionRepository: NutritionRepository,
    private val galleryRepository: GalleryRepository,
    private val pipeline: FoodAnalysisPipeline,
    private val modelManager: ModelManager,
    private val barcodeRepository: BarcodeRepository
) : ViewModel() {

    private val mealArg: Long? = savedStateHandle.get<Long>(Screen.MealGroup.ARG_MEAL_LOG_ID)
    private val itemArg: Long? = savedStateHandle.get<Long>(Screen.EntryDetail.ARG_ENTRY_ID)
    private var mealLogId: Long = 0L

    private val _uiState = MutableStateFlow(MealUiState())
    val uiState: StateFlow<MealUiState> = _uiState

    init {
        viewModelScope.launch {
            val id = mealArg ?: itemArg?.let { mealRepository.getItemById(it)?.mealLogId }
            if (id == null) {
                _uiState.update { it.copy(isLoading = false, notFound = true) }
                return@launch
            }
            mealLogId = id
            _uiState.update { it.copy(focusItemId = itemArg, modelReady = modelManager.isLlmReady) }
            watch()
        }
    }

    private fun watch() {
        viewModelScope.launch {
            val type = mealRepository.getMealTypeForMeal(mealLogId) ?: ""
            val date = mealRepository.getMealDate(mealLogId)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
            val name = mealRepository.getMealName(mealLogId)
            _uiState.update { it.copy(mealType = type, date = date, mealName = name) }
        }
        viewModelScope.launch {
            var first = true
            mealRepository.getItemsForMealFlow(mealLogId).collect { items ->
                val before = _uiState.value.dishes.associateBy { it.item.id }
                val dishes = items.map { item ->
                    val prev = before[item.id]
                    MealDish(
                        item = item,
                        ingredients = mealRepository.ingredientsForItem(item).ifEmpty { listOf(itemAsIngredient(item)) },
                        // A generation in progress keeps its spinner; otherwise read what's stored (the
                        // background worker fills it in after a log).
                        insights = if (prev?.loadingInsights == true) prev.insights else storedInsights(item, clearStale = first),
                        insightsSource = prev?.insightsSource,
                        loadingInsights = prev?.loadingInsights ?: false
                    )
                }
                first = false
                _uiState.update { it.copy(dishes = dishes, isLoading = false) }
            }
        }
        // The meal row: the tags, the coaching note (written later by the worker), the recipe link
        // and the eaten time stay live. Name and type edits are applied optimistically elsewhere.
        viewModelScope.launch {
            mealRepository.observeMealLog(mealLogId).collect { meal ->
                _uiState.update {
                    it.copy(
                        context = meal?.context,
                        coachingTip = meal?.let { m -> mealRepository.coachingTipFor(m) },
                        plannedMealId = meal?.plannedMealId,
                        eatenAt = meal?.let { m -> com.fitpal.app.domain.model.eatenAtOf(m.timestamp, m.timeSource) } ?: it.eatenAt
                    )
                }
            }
        }
    }

    /**
     * An item's cached overview, honouring the one-month rule: kept while fresh or while the food is
     * in the collection; an old one is wiped (once, on opening) so it can be generated again.
     */
    private suspend fun storedInsights(item: MealLogItemEntity, clearStale: Boolean): MealInsights? {
        if (item.insightsJson.isNullOrBlank()) return null
        val fresh = item.insightsGeneratedAt > 0 && System.currentTimeMillis() - item.insightsGeneratedAt <= RETENTION_MS
        if (fresh || item.galleryFoodId != null) return mealRepository.insightsForItem(item)
        if (clearStale) mealRepository.clearInsights(item.id)
        return null
    }

    /** A single per-100 g ingredient for an item logged without a breakdown (older entries). */
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
            isDrink = item.isDrink,
            microsPer100g = Micronutrients(
                vitaminAMcg = item.vitaminA * f, vitaminCMg = item.vitaminC * f, vitaminDMcg = item.vitaminD * f,
                calciumMg = item.calcium * f, ironMg = item.iron * f, potassiumMg = item.potassium * f,
                sodiumMg = item.sodium * f, vitaminB12Mcg = item.vitaminB12 * f, folateMcg = item.folate * f,
                vitaminB6Mg = item.vitaminB6 * f, magnesiumMg = item.magnesium * f, zincMg = item.zinc * f,
                vitaminEMg = item.vitaminE * f
            )
        )
    }

    private fun dish(itemId: Long): MealDish? = _uiState.value.dishes.firstOrNull { it.item.id == itemId }

    // ---- The meal: when, where, which meal, its name ----

    /** Toggle one situation tag on this meal (a meal can carry several). */
    fun toggleMealContext(tag: String) {
        val next = com.fitpal.app.domain.model.MealContext.format(
            com.fitpal.app.domain.model.MealContext.toggle(com.fitpal.app.domain.model.MealContext.parse(_uiState.value.context), tag)
        )
        _uiState.update { it.copy(context = next) }
        viewModelScope.launch { mealRepository.updateMealContext(mealLogId, next) }
    }

    /** Move this meal to another time on its day. Free — it never spends or refunds a fasting pass. */
    fun setMealTime(time: java.time.LocalTime) {
        viewModelScope.launch { mealRepository.updateMealTime(mealLogId, time) }
    }

    /** Change this meal's category — all its dishes move together. */
    fun setMealType(type: String) {
        if (type == _uiState.value.mealType) return
        _uiState.update { it.copy(mealType = type) }
        viewModelScope.launch { mealRepository.setMealMealType(mealLogId, type) }
    }

    /** Name the whole meal ("Sunday roast"); empty clears it, and it's named by its dishes again. */
    fun renameMeal(name: String) {
        val clean = name.trim().takeIf { it.isNotEmpty() }
        _uiState.update { it.copy(mealName = clean) }
        viewModelScope.launch { mealRepository.renameMeal(mealLogId, clean) }
    }

    /** Rename one dish — only its label; nutrition, ingredients and history stay as logged. */
    fun renameDish(itemId: Long, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { mealRepository.renameItem(itemId, clean) }
    }

    /** Copy the whole meal onto another day ([copies] times), into the chosen meal category. */
    fun copyToDate(date: LocalDate, mealType: String?, copies: Int, decision: com.fitpal.app.domain.model.LogDecision) =
        copy(date, copies) { iso -> mealRepository.copyMealToDate(mealLogId, iso, mealType, decision) }

    /** Copy one dish onto another day. */
    fun copyDishToDate(itemId: Long, date: LocalDate, mealType: String?, copies: Int, decision: com.fitpal.app.domain.model.LogDecision) {
        val item = dish(itemId)?.item ?: return
        copy(date, copies) { iso -> mealRepository.copyItemToDate(item, iso, mealType, decision) }
    }

    private fun copy(date: LocalDate, copies: Int, once: suspend (String) -> Unit) {
        val n = copies.coerceIn(1, 20)
        viewModelScope.launch {
            try {
                repeat(n) { once(date.format(DateTimeFormatter.ISO_LOCAL_DATE)) }
                val label = logDateLabel(date).lowercase()
                _uiState.update { it.copy(copyConfirmation = if (n == 1) "Copied to $label" else "$n copies to $label") }
            } catch (e: Exception) {
                _uiState.update { it.copy(copyConfirmation = "Couldn't copy: ${e.message}") }
            }
        }
    }

    fun clearCopyConfirmation() = _uiState.update { it.copy(copyConfirmation = null) }

    /** Delete the whole meal (every dish). */
    fun deleteMeal() {
        viewModelScope.launch {
            mealRepository.deleteMeal(mealLogId)
            _uiState.update { it.copy(deleted = true) }
        }
    }

    // ---- A dish's ingredients (persisted; the items flow reconciles the totals) ----

    fun updateIngredientGrams(itemId: Long, index: Int, grams: Float) {
        if (grams <= 0f) return
        editDish(itemId) { list -> list.toMutableList().also { if (index in it.indices) it[index] = it[index].withGrams(grams) } }
    }

    /** Scale a whole dish to a new total, keeping the proportions. */
    fun scaleDishToGrams(itemId: Long, grams: Float) {
        if (grams <= 0f) return
        editDish(itemId) { list ->
            val now = list.sumOf { it.grams.toDouble() }.toFloat()
            if (now <= 0f) list else list.map { it.withGrams(it.grams * grams / now) }
        }
    }

    /** Remove one ingredient; removing the last one removes the dish. */
    fun removeIngredient(itemId: Long, index: Int) = editDish(itemId) { list -> list.filterIndexed { i, _ -> i != index } }

    /** The cup on one ingredient (docs/redesign/16): that part is a drink (ml, counts as drinks) or food. */
    fun setIngredientDrink(itemId: Long, index: Int, drink: Boolean) = editDish(itemId) { list ->
        list.toMutableList().also { if (index in it.indices) it[index] = it[index].asDrinkPart(drink) }
    }

    /**
     * The +/− amount on a dish: N of it. Scales every ingredient by the change (grams stay the source
     * of truth) and remembers the count ([MealLogItemEntity.servings]).
     */
    fun setServings(itemId: Long, servings: Int) {
        val d = dish(itemId) ?: return
        val n = servings.coerceIn(1, MAX_SERVINGS)
        val current = d.item.servings.coerceAtLeast(1)
        if (n == current) return
        _uiState.update { s -> s.copy(dishes = s.dishes.map { if (it.item.id == itemId) it.copy(item = it.item.copy(servings = n)) else it }) }
        viewModelScope.launch { mealRepository.updateItemServings(itemId, n) }
        val factor = n.toFloat() / current
        editDish(itemId) { list -> list.map { it.withGrams(it.grams * factor) } }
    }

    /** Remove one dish; the last one leaves the page. */
    fun removeDish(itemId: Long) {
        viewModelScope.launch { mealRepository.deleteItem(itemId) }
    }

    /**
     * Apply [transform] to a dish's ingredients: update the page first (so quick edits build on the
     * latest values), then persist. An empty result removes the dish.
     */
    private fun editDish(itemId: Long, transform: (List<Ingredient>) -> List<Ingredient>) {
        val d = dish(itemId) ?: return
        val next = transform(d.ingredients)
        if (next.isEmpty()) { removeDish(itemId); return }
        _uiState.update { s -> s.copy(dishes = s.dishes.map { if (it.item.id == itemId) it.copy(ingredients = next) else it }) }
        viewModelScope.launch { mealRepository.updateItemIngredients(itemId, next) }
    }

    // ---- Adding and replacing ingredients ----

    fun addIngredient(itemId: Long, food: UsdaFoodEntity) {
        _uiState.update { it.copy(searchQuery = "", searchResults = emptyList()) }
        editDish(itemId) { it + food.asIngredient(food.commonServingGrams ?: 100f) }
    }

    /** Describe an ingredient to the AI and add what it returns to the dish. */
    fun addIngredientWithAi(itemId: Long, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAiAddingIngredient = true) }
            val added = runCatching { pipeline.describeMeal(text).flatMap { it.ingredients } }.getOrDefault(emptyList())
            _uiState.update { it.copy(searchQuery = "", searchResults = emptyList(), isAiAddingIngredient = false) }
            if (added.isNotEmpty()) editDish(itemId) { it + added }
        }
    }

    /** Add a hand-entered ingredient (typed values, or read from a snapped label). */
    fun addCustomIngredient(itemId: Long, ingredient: Ingredient) {
        if (ingredient.name.isBlank()) return
        editDish(itemId) { it + ingredient }
        _uiState.update { it.copy(addMessage = "Added ${ingredient.name}") }
    }

    /** Look up a scanned barcode and add the product to the dish. */
    fun scanBarcodeIngredient(itemId: Long, code: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(barcodeLookingUp = true) }
            val product = runCatching { barcodeRepository.lookup(code) }.getOrNull()
            _uiState.update { it.copy(barcodeLookingUp = false) }
            if (product == null) {
                _uiState.update { it.copy(addMessage = "That barcode isn't in any database — add it on the Custom tab.") }
                return@launch
            }
            val drink = product.isDrink || Drinks.isDrink(product.description, product.foodCategory)
            addCustomIngredient(
                itemId,
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

    /** Read a snapped nutrition-facts label with the AI (Custom tab): the one food it read, or null. */
    suspend fun readNutritionLabel(path: String): DetectedFood? {
        val bitmap = withContext(Dispatchers.IO) { decodeUprightBitmap(path) } ?: return null
        return runCatching { pipeline.analyze(bitmap, note = NUTRITION_LABEL_PROMPT) }.getOrNull()?.firstOrNull()
    }

    /** Swap the ingredient at [index] for a database food, keeping the amount eaten. */
    fun replaceIngredient(itemId: Long, index: Int, food: UsdaFoodEntity) {
        _uiState.update { it.copy(searchQuery = "", searchResults = emptyList()) }
        editDish(itemId) { list ->
            list.toMutableList().also { if (index in it.indices) it[index] = food.asIngredient(it[index].grams) }
        }
    }

    /** Describe the right ingredient to the AI and swap it in at [index] (keeping the amount). */
    fun replaceIngredientWithAi(itemId: Long, index: Int, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAiAddingIngredient = true) }
            val added = runCatching { pipeline.describeMeal(text).flatMap { it.ingredients } }.getOrDefault(emptyList())
            _uiState.update { it.copy(searchQuery = "", searchResults = emptyList(), isAiAddingIngredient = false) }
            if (added.isNotEmpty()) editDish(itemId) { list ->
                list.toMutableList().also {
                    if (index in it.indices) {
                        it[index] = added.first().withGrams(it[index].grams)
                        if (added.size > 1) it.addAll(index + 1, added.drop(1))
                    }
                }
            }
        }
    }

    private fun UsdaFoodEntity.asIngredient(grams: Float) = Ingredient(
        name = description,
        grams = grams,
        caloriesPer100g = caloriesPer100g,
        proteinPer100g = proteinPer100g,
        fatPer100g = fatPer100g,
        carbsPer100g = carbsPer100g,
        fiberPer100g = fiberPer100g,
        sourceFoodId = fdcId
    )

    // ---- Search (the add / replace dialogs) ----

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
            // The local database plus Open Food Facts when local results are sparse.
            val results = nutritionRepository.searchFoodsOnline(query, limit = 30)
            _uiState.update { it.copy(searchResults = results) }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update { it.copy(searchQuery = "", searchResults = emptyList()) }
    }

    fun clearAddMessage() = _uiState.update { it.copy(addMessage = null) }

    // ---- Edit with AI ----

    /**
     * "Edit with AI" on a logged dish: describe what's wrong ("there was no cheese", "a large
     * portion") and the AI re-reads it — ingredients, amounts and name. Its old overview is cleared.
     */
    fun refineWithAi(itemId: Long, instruction: String) {
        if (instruction.isBlank()) return
        val d = dish(itemId) ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(refiningItemId = itemId, refineError = null) }
            val food = DetectedFood(
                label = d.item.name, confidence = 1f, boundingBox = RectF(), estimatedGrams = d.item.grams,
                ingredients = d.ingredients, isDrink = d.item.isDrink
            )
            val refined = runCatching { pipeline.refineFood(food, instruction) }.getOrNull()
            if (refined == null || refined.ingredients.isEmpty()) {
                _uiState.update {
                    it.copy(refiningItemId = null, refineError = "The AI didn't come back with a usable change. Try wording it differently.")
                }
                return@launch
            }
            mealRepository.renameItem(itemId, refined.label.ifBlank { d.item.name })
            mealRepository.clearInsights(itemId)
            _uiState.update { s ->
                s.copy(refiningItemId = null, dishes = s.dishes.map { if (it.item.id == itemId) it.copy(insights = null, insightsSource = null) else it })
            }
            editDish(itemId) { refined.drinkAwareIngredients }
        }
    }

    fun clearRefineError() = _uiState.update { it.copy(refineError = null) }

    // ---- The collection ----

    /** Save one dish (its current ingredients) to the collection. */
    fun saveDishToCollection(itemId: Long) {
        val d = dish(itemId) ?: return
        if (_uiState.value.saved(itemId)) return
        viewModelScope.launch {
            galleryRepository.saveDetectedFood(
                food = DetectedFood(
                    label = d.item.name, confidence = 1f, boundingBox = RectF(), estimatedGrams = d.item.grams,
                    ingredients = d.ingredients, isDrink = d.item.isDrink
                ),
                photoPath = d.item.photoPath,
                insights = d.insights,
                aiSource = AiSource.fromName(d.item.aiSource, d.item.aiModel)
            )
            _uiState.update { it.copy(savedItemIds = it.savedItemIds + itemId) }
        }
    }

    /** Save every dish of the meal to the collection. */
    fun saveAllToCollection() = _uiState.value.dishes.forEach { saveDishToCollection(it.item.id) }

    // ---- AI insights (per dish) ----

    fun generateInsights(itemId: Long) {
        if (!modelManager.isLlmReady) return
        val d = dish(itemId) ?: return
        val item = d.item
        setDishInsights(itemId) { it.copy(loadingInsights = true) }
        viewModelScope.launch {
            // Health score + factors are deterministic (always consistent & complete).
            val scored = scoreItem(item)
            val insights = try {
                val prompt = FoodPrompts.itemInsights(
                    name = item.name,
                    amountLabel = com.fitpal.app.domain.model.weightLabel(d.ingredients, item.grams, item.isDrink).replace(" ", ""),
                    kcal = item.calories.toInt(),
                    protein = item.protein.toInt(),
                    fat = item.fat.toInt(),
                    carbs = item.carbs.toInt(),
                    fiber = item.fiber.toInt(),
                    ingredients = d.ingredients.joinToString(", ") { "${it.name} ${it.grams.toInt()}${if (it.isDrink) "ml" else "g"}" }
                )
                val (response, source) = pipeline.generateRawTextWithSource(prompt)
                // Only keep swaps that name something really in this dish.
                val swaps = FoodJsonParser.parseItemSwaps(response, listOf(item.name) + d.ingredients.map { it.name })
                fun line(key: String) = Regex("$key:\\s*(.+)").find(response)?.groupValues?.get(1)?.trim() ?: ""
                fun score(key: String) = Regex("$key:\\s*(\\d+)").find(response)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, 5) ?: 0
                setDishInsights(itemId) { it.copy(insightsSource = source) }
                MealInsights(
                    healthScore = scored.score,
                    scoreFactors = scored.factors,
                    healthSwaps = swaps,
                    energyImpact = line("ENERGY"),
                    moodImpact = line("MOOD"),
                    pairingRecommendations = Regex("PAIR:\\s*(.+)").findAll(response).map { it.groupValues[1].trim() }.toList(),
                    energyScore = score("ENERGY_SCORE"),
                    moodScore = score("MOOD_SCORE")
                )
            } catch (e: Exception) {
                // Even if the AI text fails, the deterministic score still shows.
                MealInsights(scored.score, scored.factors, emptyList(), "", "", emptyList())
            }
            // Cached so it isn't generated again (until the retention sweep).
            mealRepository.saveInsights(itemId, insights)
            setDishInsights(itemId) { it.copy(loadingInsights = false, insights = insights) }
        }
    }

    /** Forget a dish's overview and write it again (after editing the dish). */
    fun regenerateInsights(itemId: Long) {
        viewModelScope.launch {
            mealRepository.clearInsights(itemId)
            setDishInsights(itemId) { it.copy(insights = null) }
            generateInsights(itemId)
        }
    }

    private fun setDishInsights(itemId: Long, change: (MealDish) -> MealDish) =
        _uiState.update { s -> s.copy(dishes = s.dishes.map { if (it.item.id == itemId) change(it) else it }) }

    /** The deterministic health rubric over a logged item. */
    private fun scoreItem(item: MealLogItemEntity): HealthScorer.Result =
        HealthScorer.score(
            calories = item.calories, protein = item.protein, fat = item.fat, carbs = item.carbs,
            fiber = item.fiber, grams = item.grams, isDrink = item.isDrink, micros = microsOf(item)
        )

    companion object {
        /** Cached insights live for one month unless the food is saved to the collection. */
        private const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000
        /** Upper bound for the amount multiplier (matches the pre-log review). */
        private const val MAX_SERVINGS = 50
    }
}

/** A logged item's vitamins and minerals. */
internal fun microsOf(item: MealLogItemEntity) = Micronutrients(
    vitaminAMcg = item.vitaminA, vitaminCMg = item.vitaminC, vitaminDMcg = item.vitaminD,
    calciumMg = item.calcium, ironMg = item.iron, potassiumMg = item.potassium, sodiumMg = item.sodium,
    vitaminB12Mcg = item.vitaminB12, folateMcg = item.folate, vitaminB6Mg = item.vitaminB6,
    magnesiumMg = item.magnesium, zincMg = item.zinc, vitaminEMg = item.vitaminE
)
