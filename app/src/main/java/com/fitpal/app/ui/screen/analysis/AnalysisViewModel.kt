package com.fitpal.app.ui.screen.analysis

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.local.entity.UsdaFoodEntity
import com.fitpal.app.data.repository.GalleryRepository
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.NutritionRepository
import com.fitpal.app.domain.MealLogContext
import com.fitpal.app.domain.HealthScorer
import com.fitpal.app.domain.model.DetectedFood
import com.fitpal.app.domain.model.Micronutrients
import com.fitpal.app.domain.model.Ingredient
import com.fitpal.app.domain.model.MealInsights
import com.fitpal.app.ml.AiSource
import com.fitpal.app.ml.AnalysisJobManager
import com.fitpal.app.ml.FoodAnalysisPipeline
import com.fitpal.app.ml.JobKind
import com.fitpal.app.ml.JobStatus
import com.fitpal.app.ml.ModelManager
import com.fitpal.app.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class AnalysisUiState(
    val isAnalyzing: Boolean = false,
    val progressMessage: String = "",
    /** Optional note the user adds before generation (ingredients, size, grammage…). */
    val note: String = "",
    /** True once a photo is ready but the user hasn't started analysis yet. */
    val readyToAnalyze: Boolean = false,
    val detectedFoods: List<DetectedFood> = emptyList(),
    // Candidate dishes the loose photo ingredients might form (most likely first).
    val dishCandidates: List<DetectedFood> = emptyList(),
    val selectedDishIndex: Int = 0,
    val error: String? = null,
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false,
    val savedFoodIndices: Set<Int> = emptySet(),
    /** Maps food index → gallery DB id so we can unsave. */
    val savedGalleryIds: Map<Int, Long> = emptyMap(),
    val needsModel: Boolean = false,
    val needsImage: Boolean = false,
    /** The source photo being analyzed — shown at the top of the screen. */
    val imageUri: String? = null,
    // AI insights — health score, swaps, energy/mood, pairings
    val isLoadingInsights: Boolean = false,
    val insights: MealInsights? = null,
    /** Which engine produced this result — drives the "online / on-device" badge. */
    val aiSource: AiSource? = null,
    /** If it fell back to on-device, why online wasn't used (shown beneath the badge). */
    val onlineError: String? = null,
    /** Set when the online model failed and we're awaiting the user's choice (retry / on-device). */
    val onlineFailedReason: String? = null,
    /** True once the user cancels the on-device confirm prompt — the screen should pop back. */
    val cancelled: Boolean = false,
    // Add-ingredient search (before logging)
    val searchQuery: String = "",
    val searchResults: List<UsdaFoodEntity> = emptyList(),
    /** True while the "Add with AI" describe-an-ingredient call is running. */
    val isAiAddingIngredient: Boolean = false,
    /** Which food card is being re-evaluated by "Edit with AI" (null = none) — drives its spinner. */
    val aiEditingFoodIndex: Int? = null,
    /** One-shot confirmation after "copy to another date" — shown as a toast, then cleared. */
    val copyConfirmation: String? = null
)

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val mealRepository: MealRepository,
    private val galleryRepository: GalleryRepository,
    private val nutritionRepository: NutritionRepository,
    private val modelManager: ModelManager,
    private val jobManager: AnalysisJobManager,
    private val pipeline: FoodAnalysisPipeline,
    mealLogContext: MealLogContext
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState

    private val _mealType =
        MutableStateFlow(mealLogContext.consume() ?: mealRepository.defaultMealType())
    val mealType: StateFlow<String> = _mealType
    fun setMealType(type: String) {
        _mealType.value = type
        jobManager.updateMealType(type)
    }

    /**
     * On-demand health analysis for the current foods, shown right here before logging. The service
     * also generates this automatically after recognition; this is the explicit "Generate / Refresh"
     * path so it's always available and re-runnable from the review screen.
     */
    fun generateInsights() {
        val foods = _uiState.value.detectedFoods
        if (foods.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingInsights = true) }
            val ai = runCatching { pipeline.analyzeMealInsights(foods) }.getOrNull()
            _uiState.update { it.copy(isLoadingInsights = false, insights = ai ?: deterministicInsights(foods)) }
        }
    }

    /** A guaranteed result when the AI text can't be produced: the deterministic score + factors. */
    private fun deterministicInsights(foods: List<DetectedFood>): MealInsights {
        val micros = foods.fold(Micronutrients()) { acc, f -> acc + f.totalMicros }
        val scored = HealthScorer.score(
            calories = foods.sumOf { it.totalCalories.toDouble() }.toFloat(),
            protein = foods.sumOf { it.totalProtein.toDouble() }.toFloat(),
            fat = foods.sumOf { it.totalFat.toDouble() }.toFloat(),
            carbs = foods.sumOf { it.totalCarbs.toDouble() }.toFloat(),
            fiber = foods.sumOf { it.totalFiber.toDouble() }.toFloat(),
            grams = foods.sumOf { it.totalGrams.toDouble() }.toFloat(),
            isDrink = foods.all { it.isDrink },
            micros = micros
        )
        return MealInsights(scored.score, scored.factors, emptyList(), "", "", emptyList())
    }

    /** The day this meal will be logged to — starts from Home's "+" (or today), user-editable. */
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

    private val sourceImageUri: String? = savedStateHandle.get<String>(Screen.Analysis.ARG_IMAGE_URI)

    /** So we only copy the generated result into editable state once (then the user owns it). */
    private var adoptedJobId: String? = null

    init {
        val imageUri = sourceImageUri
        _uiState.update { it.copy(imageUri = imageUri) }
        val existing = jobManager.current
        when {
            // A background analysis for THIS photo is already running or finished — adopt it
            // (e.g. the user tapped the "meal analysed" notification). A different photo
            // falls through and starts fresh.
            existing != null && existing.kind == JobKind.IMAGE && existing.imageUri == imageUri -> {
                _mealType.value = existing.mealType
                existing.targetDate?.let { iso -> runCatching { java.time.LocalDate.parse(iso) }.getOrNull()?.let { _logDate.value = it } }
                _uiState.update { it.copy(imageUri = existing.imageUri ?: imageUri) }
            }
            imageUri == null -> _uiState.update { it.copy(needsImage = true) }
            !modelManager.isLlmReady -> _uiState.update { it.copy(needsModel = true) }
            else -> _uiState.update { it.copy(readyToAnalyze = true) }
        }
        observeJob()
    }

    fun setNote(text: String) {
        _uiState.update { it.copy(note = text) }
    }

    /**
     * Called when the user navigates away from this screen (back). If a FINISHED result was never
     * logged, drop it so it isn't auto-saved on app close (the user reviewed it and declined).
     * A still-running analysis is left alone — it finishes in the background and can be resumed
     * from the notification, and leaving the whole app (home) doesn't dispose this screen.
     */
    fun onLeaveWithoutLogging() {
        val job = jobManager.current ?: return
        if (job.kind != JobKind.IMAGE || job.saved) return
        if (job.status !is JobStatus.Running) jobManager.discard()
    }

    /** From the "online failed" prompt: try the online model again. */
    fun retryOnline() {
        _uiState.update { it.copy(onlineFailedReason = null, isAnalyzing = true, progressMessage = "Retrying online…") }
        jobManager.chooseRetryOnline()
    }

    /** From the "online failed" prompt: give up on online and run the on-device model. */
    fun useOnDevice() {
        _uiState.update { it.copy(onlineFailedReason = null, isAnalyzing = true, progressMessage = "Switching to on-device…") }
        jobManager.chooseUseLocal()
    }

    /** From the "online failed" prompt: cancel the analysis entirely and leave the screen. */
    fun cancelAnalysis() {
        jobManager.chooseCancel()
        _uiState.update { it.copy(onlineFailedReason = null, isAnalyzing = false, cancelled = true) }
    }

    /** Hand the photo to the background service; it survives leaving the screen/app. */
    fun startAnalysis() {
        val uri = sourceImageUri ?: return
        _uiState.update { it.copy(readyToAnalyze = false, isAnalyzing = true, error = null, progressMessage = "Starting…") }
        jobManager.startImageJob(uri, _uiState.value.note, _mealType.value, logDateIso())
    }

    /** Mirror the background job's progress / result into the screen state. */
    private fun observeJob() {
        viewModelScope.launch {
            jobManager.state.collect { job ->
                if (job == null || job.kind != JobKind.IMAGE) return@collect
                when (val s = job.status) {
                    is JobStatus.Running -> _uiState.update {
                        it.copy(
                            isAnalyzing = true, readyToAnalyze = false, error = null,
                            progressMessage = s.message, aiSource = s.source, onlineFailedReason = null
                        )
                    }
                    is JobStatus.OnlineFailed -> _uiState.update {
                        it.copy(isAnalyzing = false, onlineFailedReason = s.reason)
                    }
                    is JobStatus.Done -> {
                        if (adoptedJobId != job.id) {
                            adoptedJobId = job.id
                            _uiState.update {
                                it.copy(
                                    isAnalyzing = false, readyToAnalyze = false,
                                    detectedFoods = s.foods, dishCandidates = s.dishCandidates, selectedDishIndex = 0
                                )
                            }
                        }
                        // Insights arrive a little after the foods — track them live without
                        // disturbing any edits the user has made to the foods.
                        _uiState.update {
                            it.copy(
                                aiSource = s.source, onlineError = s.onlineError, onlineFailedReason = null,
                                insights = s.insights, isLoadingInsights = s.insights == null
                            )
                        }
                    }
                    is JobStatus.Failed -> _uiState.update { it.copy(isAnalyzing = false, error = s.message) }
                }
            }
        }
    }

    fun updateIngredientGrams(foodIndex: Int, ingredientIndex: Int, newGrams: Float) {
        _uiState.update { state ->
            val foods = state.detectedFoods.toMutableList()
            if (foodIndex in foods.indices) {
                val food = foods[foodIndex]
                val ingredients = food.ingredients.toMutableList()
                if (ingredientIndex in ingredients.indices) {
                    ingredients[ingredientIndex] = ingredients[ingredientIndex].withGrams(newGrams)
                    foods[foodIndex] = food.copy(ingredients = ingredients)
                }
            }
            state.copy(detectedFoods = foods)
        }
    }

    /** Override a food's identified water content (ml), spread across its ingredients by weight. */
    fun setFoodWater(foodIndex: Int, newTotalMl: Float) {
        if (newTotalMl < 0f) return
        _uiState.update { state ->
            val foods = state.detectedFoods.toMutableList()
            val food = foods.getOrNull(foodIndex) ?: return@update state
            val totalGrams = food.ingredients.sumOf { it.grams.toDouble() }.toFloat()
            if (totalGrams <= 0f) return@update state
            val currentWater = food.ingredients.sumOf { it.waterMl.toDouble() }.toFloat()
            val updated = if (currentWater > 0f) {
                val factor = newTotalMl / currentWater
                food.ingredients.map { it.copy(waterMlPer100g = it.waterMlPer100g * factor) }
            } else {
                // No water yet — spread the requested amount uniformly per 100 g across the dish.
                val per100 = newTotalMl / totalGrams * 100f
                food.ingredients.map { it.copy(waterMlPer100g = per100) }
            }
            foods[foodIndex] = food.copy(ingredients = updated)
            state.copy(detectedFoods = foods)
        }
    }

    /**
     * Set how many of this item were eaten (the +/− "Amount" stepper). Scales every ingredient's
     * weight by the change so the totals track it — grams stay the single source of truth, so
     * logging needs no special handling. If the user later hand-edits a weight, the count is just a
     * shortcut that already applied; the next +/− scales from whatever's currently there.
     */
    fun setServings(foodIndex: Int, newServings: Int) {
        val n = newServings.coerceIn(1, 50)
        _uiState.update { state ->
            val foods = state.detectedFoods.toMutableList()
            val food = foods.getOrNull(foodIndex) ?: return@update state
            val current = food.servings.coerceAtLeast(1)
            if (n == current) return@update state
            val factor = n.toFloat() / current
            foods[foodIndex] = food.copy(
                servings = n,
                ingredients = food.ingredients.map { it.withGrams(it.grams * factor) }
            )
            state.copy(detectedFoods = foods)
        }
    }

    /** Set the whole dish's weight, scaling every ingredient proportionally. */
    fun scaleFoodToGrams(foodIndex: Int, newTotalGrams: Float) {
        if (newTotalGrams <= 0f) return
        _uiState.update { state ->
            val foods = state.detectedFoods.toMutableList()
            val food = foods.getOrNull(foodIndex) ?: return@update state
            val current = food.ingredients.sumOf { it.grams.toDouble() }.toFloat()
            if (current <= 0f) return@update state
            val factor = newTotalGrams / current
            foods[foodIndex] = food.copy(ingredients = food.ingredients.map { it.withGrams(it.grams * factor) })
            state.copy(detectedFoods = foods)
        }
    }

    /** Remove one ingredient from a dish; if it was the last, drop the whole food. */
    fun removeIngredient(foodIndex: Int, ingredientIndex: Int) {
        _uiState.update { state ->
            val foods = state.detectedFoods.toMutableList()
            val food = foods.getOrNull(foodIndex) ?: return@update state
            val ingredients = food.ingredients.toMutableList()
            if (ingredientIndex in ingredients.indices) ingredients.removeAt(ingredientIndex)
            if (ingredients.isEmpty()) foods.removeAt(foodIndex)
            else foods[foodIndex] = food.copy(ingredients = ingredients)
            state.copy(detectedFoods = foods)
        }
    }

    /** Toggle a variation on/off — adds or removes its add-on ingredient. */
    fun toggleVariation(foodIndex: Int, variationIndex: Int) {
        _uiState.update { state ->
            val foods = state.detectedFoods.toMutableList()
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
            state.copy(detectedFoods = foods)
        }
    }

    /** Log everything on screen as a meal. */
    fun logMeal() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val savedPhoto = withContext(Dispatchers.IO) { persistImage(sourceImageUri) }
                mealRepository.logMeal(
                    foods = _uiState.value.detectedFoods,
                    mealType = _mealType.value,
                    photoPath = savedPhoto,
                    insights = _uiState.value.insights,
                    date = logDateIso(),
                    source = _uiState.value.aiSource
                )
                // The user handled it — stop the background service so it doesn't auto-save again.
                jobManager.completeByUser()
                _uiState.update { it.copy(isSaving = false, savedSuccessfully = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = "Failed to save: ${e.message}") }
            }
        }
    }

    /**
     * #2 — "copy to another date": log the current meal to an ADDITIONAL day, without leaving the
     * screen (so the user can still log it to the primary date too). Sets a one-shot confirmation.
     */
    fun copyToDate(date: java.time.LocalDate, mealType: String? = null, copies: Int = 1) {
        val n = copies.coerceIn(1, 20)
        viewModelScope.launch {
            try {
                repeat(n) {
                    // Each copy persists its own photo file so deleting one meal can't orphan another's image.
                    val savedPhoto = withContext(Dispatchers.IO) { persistImage(sourceImageUri) }
                    mealRepository.logMeal(
                        foods = _uiState.value.detectedFoods,
                        mealType = mealType ?: _mealType.value,
                        photoPath = savedPhoto,
                        insights = _uiState.value.insights,
                        date = date.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE),
                        source = _uiState.value.aiSource
                    )
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

    private fun persistImage(uriString: String?): String? {
        if (uriString.isNullOrBlank()) return null
        return try {
            val uri = Uri.parse(uriString)
            val dir = File(context.filesDir, "meal_photos").also { it.mkdirs() }
            val dest = File(dir, "meal_${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return uriString
            dest.absolutePath
        } catch (e: Exception) {
            uriString
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
            val foods = state.detectedFoods.toMutableList()
            foods.getOrNull(foodIndex)?.let { f ->
                foods[foodIndex] = f.copy(ingredients = f.ingredients + ingredient)
            }
            state.copy(detectedFoods = foods, searchQuery = "", searchResults = emptyList())
        }
    }

    /** Describe an ingredient to the AI (online or on-device) and add what it returns. */
    fun addIngredientWithAi(foodIndex: Int, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAiAddingIngredient = true) }
            val added = runCatching { pipeline.describeMeal(text).flatMap { it.ingredients } }.getOrDefault(emptyList())
            _uiState.update { state ->
                val foods = state.detectedFoods.toMutableList()
                foods.getOrNull(foodIndex)?.let { f -> foods[foodIndex] = f.copy(ingredients = f.ingredients + added) }
                state.copy(detectedFoods = foods, searchQuery = "", searchResults = emptyList(), isAiAddingIngredient = false)
            }
        }
    }

    /**
     * "Edit with AI": re-evaluate a whole food from a free-text correction ("there are also beans",
     * "no cheese", "it's grilled not fried"). Unlike add-ingredient, the AI rebalances every
     * ingredient's weight to fit the original total and may add/remove/rename items — then it
     * replaces the food in place. On failure (no AI / unparseable) the food is left unchanged.
     */
    fun refineFoodWithAi(foodIndex: Int, instruction: String) {
        if (instruction.isBlank()) return
        val food = _uiState.value.detectedFoods.getOrNull(foodIndex) ?: return
        _uiState.update { it.copy(aiEditingFoodIndex = foodIndex) }
        viewModelScope.launch {
            val refined = runCatching { pipeline.refineFood(food, instruction) }.getOrNull()
            _uiState.update { state ->
                val foods = state.detectedFoods.toMutableList()
                if (refined != null && foodIndex in foods.indices) {
                    // A correction shouldn't flip food↔drink or blank the name; keep those anchored.
                    foods[foodIndex] = refined.copy(
                        label = refined.label.ifBlank { food.label },
                        isDrink = food.isDrink
                    )
                }
                state.copy(detectedFoods = foods, aiEditingFoodIndex = null)
            }
        }
    }

    /** Remove a food the AI got wrong (common with a full plate of several items). */
    fun removeFood(foodIndex: Int) {
        _uiState.update { state ->
            val foods = state.detectedFoods.toMutableList()
            if (foodIndex in foods.indices) foods.removeAt(foodIndex)
            state.copy(detectedFoods = foods)
        }
    }

    /** Pick which candidate dish to log — swaps in its ingredients + variations. */
    fun selectDish(index: Int) {
        _uiState.update { state ->
            val candidate = state.dishCandidates.getOrNull(index) ?: return@update state
            state.copy(selectedDishIndex = index, detectedFoods = listOf(candidate))
        }
    }

    /** Save a single detected food item to the gallery for future quick-logging — with its photo
     *  and the generated AI analysis, so opening the saved food shows the full screen. */
    fun saveToGallery(foodIndex: Int) {
        if (_uiState.value.savedFoodIndices.contains(foodIndex)) return
        viewModelScope.launch {
            val food = _uiState.value.detectedFoods.getOrNull(foodIndex) ?: return@launch
            val photo = withContext(Dispatchers.IO) { persistImage(sourceImageUri) }
            val galleryId = galleryRepository.saveDetectedFood(
                food = food,
                photoPath = photo,
                insights = _uiState.value.insights,
                aiSource = _uiState.value.aiSource
            )
            _uiState.update {
                it.copy(
                    savedFoodIndices = it.savedFoodIndices + foodIndex,
                    savedGalleryIds = it.savedGalleryIds + (foodIndex to galleryId)
                )
            }
        }
    }

    /** Remove a previously-saved food from the gallery (toggle off). */
    fun removeFromGallery(foodIndex: Int) {
        val galleryId = _uiState.value.savedGalleryIds[foodIndex] ?: return
        viewModelScope.launch {
            val pair = galleryRepository.getFoodWithIngredients(galleryId)
            if (pair != null) galleryRepository.deleteFood(pair.first)
            _uiState.update {
                it.copy(
                    savedFoodIndices = it.savedFoodIndices - foodIndex,
                    savedGalleryIds = it.savedGalleryIds - foodIndex
                )
            }
        }
    }
}
