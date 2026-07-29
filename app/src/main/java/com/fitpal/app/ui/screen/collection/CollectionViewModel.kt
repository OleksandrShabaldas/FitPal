package com.fitpal.app.ui.screen.collection

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.local.entity.GalleryCategoryEntity
import com.fitpal.app.data.local.entity.GalleryFoodEntity
import com.fitpal.app.data.local.entity.GalleryIngredientEntity
import com.fitpal.app.data.local.entity.SavedWorkoutEntity
import com.fitpal.app.data.repository.ExerciseRepository
import com.fitpal.app.data.repository.GalleryRepository
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.data.repository.WeightRepository
import com.fitpal.app.domain.HealthScorer
import com.fitpal.app.domain.model.HealthSwap
import com.fitpal.app.domain.model.MealInsights
import com.fitpal.app.ml.AiSource
import com.fitpal.app.ml.FoodAnalysisPipeline
import com.fitpal.app.ml.FoodPrompts
import com.fitpal.app.ml.ModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CollectionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val galleryRepository: GalleryRepository,
    private val mealRepository: MealRepository,
    private val exerciseRepository: ExerciseRepository,
    private val weightRepository: WeightRepository,
    private val settingsRepository: SettingsRepository,
    private val pipeline: FoodAnalysisPipeline,
    private val modelManager: ModelManager
) : ViewModel() {

    // ---------- Tab & search ----------

    private val _selectedTab = MutableStateFlow(CollectionTab.FOOD)
    val selectedTab: StateFlow<CollectionTab> = _selectedTab

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    /** Gallery foods — filtered by search query, sorted by most recently used. */
    val foods: StateFlow<List<GalleryFoodEntity>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) galleryRepository.getAllFoods()
            else galleryRepository.searchFoods(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectTab(tab: CollectionTab) { _selectedTab.value = tab }
    fun onSearchQueryChanged(query: String) { _searchQuery.value = query }

    // ---------- Categories ----------

    /** The currently selected top-level category tab, or null for "All". */
    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    val selectedCategoryId: StateFlow<Long?> = _selectedCategoryId
    fun selectCategory(id: Long?) { _selectedCategoryId.value = id }

    private val categories: StateFlow<List<GalleryCategoryEntity>> =
        galleryRepository.getCategories()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Top-level categories — shown as tabs under the search bar. */
    val topCategories: StateFlow<List<GalleryCategoryEntity>> = categories
        .map { cats -> cats.filter { it.parentId == null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * The ready-to-render view: which foods sit directly in the selected category, and the
     * collapsible subcategory sections beneath it. When "All" is selected everything is flat.
     */
    data class CategorySection(val subcategory: GalleryCategoryEntity, val foods: List<GalleryFoodEntity>)
    /** A top-level category group in the "All" view: its own foods + collapsible subcategory sections. */
    data class CategoryGroup(
        val category: GalleryCategoryEntity?,   // null = the "Unsorted" catch-all
        val directFoods: List<GalleryFoodEntity>,
        val sections: List<CategorySection>
    )
    data class CollectionView(
        val selectedCategoryId: Long?,
        val directFoods: List<GalleryFoodEntity>,   // used when a specific category is selected
        val sections: List<CategorySection>,        // used when a specific category is selected
        val groups: List<CategoryGroup>             // used for "All" — everything grouped by category
    )

    val collectionView: StateFlow<CollectionView> =
        combine(foods, categories, _selectedCategoryId) { allFoods, cats, selected ->
            if (selected == null) {
                // "All": nest everything under collapsible category → subcategory headers.
                val topCats = cats.filter { it.parentId == null }
                val knownIds = cats.map { it.id }.toSet()
                val groups = buildList {
                    topCats.forEach { top ->
                        val subs = cats.filter { it.parentId == top.id }
                        val direct = allFoods.filter { it.categoryId == top.id }
                        val sections = subs
                            .map { sub -> CategorySection(sub, allFoods.filter { it.categoryId == sub.id }) }
                            .filter { it.foods.isNotEmpty() }
                        // Only show a category header if it actually holds something.
                        if (direct.isNotEmpty() || sections.isNotEmpty()) add(CategoryGroup(top, direct, sections))
                    }
                    // Anything unsorted (or filed under a deleted category) collects in one group at the end.
                    val unsorted = allFoods.filter { it.categoryId == null || it.categoryId !in knownIds }
                    if (unsorted.isNotEmpty()) add(CategoryGroup(null, unsorted, emptyList()))
                }
                CollectionView(null, emptyList(), emptyList(), groups)
            } else {
                val subs = cats.filter { it.parentId == selected }
                val direct = allFoods.filter { it.categoryId == selected }
                val sections = subs.map { sub -> CategorySection(sub, allFoods.filter { it.categoryId == sub.id }) }
                CollectionView(selected, direct, sections, emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CollectionView(null, emptyList(), emptyList(), emptyList()))

    // ---------- Collapsed sections ----------

    /**
     * Which category / subcategory headers the user collapsed. Keys are the screen's stable
     * strings ("cat_<id>", "sub_<id>", "unsorted"). Persisted, so a collapsed category stays
     * collapsed after the app is restarted.
     */
    val collapsedSections: StateFlow<Set<String>> = settingsRepository.collectionCollapsed

    /** Fold a section open/closed. */
    fun toggleCollapsed(key: String) {
        val next = collapsedSections.value.toMutableSet().apply { if (!add(key)) remove(key) }
        settingsRepository.setCollectionCollapsed(next)
    }

    /** Subcategories of a given top category (for the assign-to-category picker). */
    fun subcategoriesOf(parentId: Long): List<GalleryCategoryEntity> =
        categories.value.filter { it.parentId == parentId }

    fun addCategory(name: String, parentId: Long? = null) {
        if (name.isBlank()) return
        viewModelScope.launch { galleryRepository.addCategory(name, parentId) }
    }

    fun renameCategory(category: GalleryCategoryEntity, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch { galleryRepository.renameCategory(category, newName) }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            galleryRepository.deleteCategory(id)
            if (_selectedCategoryId.value == id) _selectedCategoryId.value = null
        }
    }

    /** File a saved food under a category/subcategory (or null to make it unsorted). */
    fun assignFoodToCategory(foodId: Long, categoryId: Long?) {
        viewModelScope.launch { galleryRepository.assignFoodToCategory(foodId, categoryId) }
    }

    /** Flat list of categories for pickers (top categories + their subcategories). */
    val allCategories: StateFlow<List<GalleryCategoryEntity>> = categories

    // ---------- Actions ----------

    fun deleteFood(food: GalleryFoodEntity) {
        viewModelScope.launch { galleryRepository.deleteFood(food) }
    }

    fun updateNotes(foodId: Long, notes: String) {
        viewModelScope.launch { galleryRepository.updateNotes(foodId, notes) }
    }

    /** Rename a saved food (blank names are ignored). */
    fun renameFood(foodId: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { galleryRepository.renameFood(foodId, name) }
    }

    /**
     * Copy a user-picked image into the app's private files and save the path
     * as the food's custom thumbnail.
     */
    fun setThumbnail(foodId: Long, imageUri: Uri) {
        viewModelScope.launch {
            val dir = File(context.filesDir, "thumbnails").also { it.mkdirs() }
            val dest = File(dir, "thumb_${foodId}_${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(imageUri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            galleryRepository.updateThumbnail(foodId, dest.absolutePath)
        }
    }

    /** Load ingredients for a food so we can show them in the detail. */
    suspend fun getIngredients(foodId: Long): List<GalleryIngredientEntity> {
        val pair = galleryRepository.getFoodWithIngredients(foodId) ?: return emptyList()
        return pair.second
    }

    // ---------- AI Insights ----------

    data class FoodInsightState(
        val isLoading: Boolean = false,
        val insights: MealInsights? = null,
        /** Which engine produced these insights (badge). */
        val source: AiSource? = null
    )

    private val _foodInsights = MutableStateFlow<Map<Long, FoodInsightState>>(emptyMap())
    val foodInsights: StateFlow<Map<Long, FoodInsightState>> = _foodInsights

    fun generateInsights(food: GalleryFoodEntity) {
        if (!modelManager.isLlmReady) return
        val current = _foodInsights.value[food.id]
        if (current?.isLoading == true || current?.insights != null) return

        _foodInsights.value = _foodInsights.value + (food.id to FoodInsightState(isLoading = true))
        viewModelScope.launch {
            // Health score + factors are deterministic (always consistent & complete).
            val scored = HealthScorer.score(
                calories = food.totalCalories,
                protein = food.totalProtein,
                fat = food.totalFat,
                carbs = food.totalCarbs,
                fiber = food.totalFiber,
                grams = food.defaultPortionGrams,
                isDrink = food.isDrink
            )
            try {
                val prompt = FoodPrompts.itemInsights(
                    name = food.name,
                    amountLabel = "${food.defaultPortionGrams.toInt()}${if (food.isDrink) "ml" else "g"}",
                    kcal = food.totalCalories.toInt(),
                    protein = food.totalProtein.toInt(),
                    fat = food.totalFat.toInt(),
                    carbs = food.totalCarbs.toInt(),
                    fiber = food.totalFiber.toInt()
                )
                val (response, source) = pipeline.generateRawTextWithSource(prompt)

                val swaps = Regex("SWAP:\\s*(.+?)\\s*->\\s*(.+?)\\s*\\|\\s*(.+)").findAll(response)
                    .map { HealthSwap(it.groupValues[1].trim(), it.groupValues[2].trim(), it.groupValues[3].trim()) }.toList()
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
                _foodInsights.value = _foodInsights.value + (food.id to FoodInsightState(insights = insights, source = source))
            } catch (e: Exception) {
                val fallback = MealInsights(scored.score, scored.factors, emptyList(), "", "", emptyList())
                _foodInsights.value = _foodInsights.value + (food.id to FoodInsightState(insights = fallback))
            }
        }
    }

    /** Quick-log a food from the collection to today's meals. */
    fun quickLog(foodId: Long) {
        viewModelScope.launch {
            val food = galleryRepository.toDomainModel(foodId) ?: return@launch
            mealRepository.logMeal(listOf(food), mealRepository.defaultMealType())
            galleryRepository.markUsed(foodId)
        }
    }

    // ---------- Saved workouts (Exercise tab) ----------

    /** Workouts saved to the collection, most recently used first. */
    val savedWorkouts: StateFlow<List<SavedWorkoutEntity>> = exerciseRepository.getSavedWorkouts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** One-shot confirmation shown after logging a saved workout (as a toast), then cleared. */
    private val _logConfirmation = MutableStateFlow<String?>(null)
    val logConfirmation: StateFlow<String?> = _logConfirmation
    fun clearLogConfirmation() { _logConfirmation.value = null }

    /** One-tap re-log a saved workout to today (calories recompute from current weight). */
    fun quickLogWorkout(workout: SavedWorkoutEntity) {
        viewModelScope.launch {
            val kg = weightRepository.getLatest().first()?.weightKg ?: 70f
            exerciseRepository.logSavedWorkout(workout, kg)
            _logConfirmation.value = "Logged ${workout.name.replaceFirstChar { it.uppercase() }} to today"
        }
    }

    fun deleteWorkout(workout: SavedWorkoutEntity) {
        viewModelScope.launch { exerciseRepository.deleteSavedWorkout(workout.id) }
    }
}

enum class CollectionTab(val label: String) {
    FOOD("Food"),
    EXERCISE("Exercise")
}
