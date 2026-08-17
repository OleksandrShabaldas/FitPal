package com.fitpal.app.ui.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.local.entity.ExerciseEntryEntity
import com.fitpal.app.data.local.entity.MealLogItemEntity
import com.fitpal.app.data.repository.ExerciseRepository
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.WeightRepository
import com.fitpal.app.domain.search.FuzzySearch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A single search hit — a food the user logged, or an exercise. */
sealed interface SearchResult {
    /** Fuzzy match score; 0 for the "recent" list shown with an empty query. */
    val score: Int
    /** ISO date the item was last logged — the tiebreaker and the "last logged" label. */
    val lastDate: String

    data class Food(
        val item: MealLogItemEntity,
        override val lastDate: String,
        override val score: Int
    ) : SearchResult

    data class Exercise(
        val entry: ExerciseEntryEntity,
        override val score: Int
    ) : SearchResult {
        override val lastDate: String get() = entry.date
    }
}

/**
 * Backs the global search: everything the user has ever logged (foods + exercises), matched with the
 * offline typo-tolerant [FuzzySearch]. Empty query shows the most recent items so the screen is never
 * blank. Scoring runs off the main thread; the data sets are small (distinct names only).
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val mealRepository: MealRepository,
    private val exerciseRepository: ExerciseRepository,
    private val weightRepository: WeightRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    fun setQuery(q: String) { _query.value = q }

    private val foods = mealRepository.distinctLoggedFoods()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val exercises = exerciseRepository.distinctExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val results: StateFlow<List<SearchResult>> =
        combine(_query, foods, exercises) { q, f, e ->
            val query = q.trim()
            if (query.isEmpty()) {
                // Nothing typed yet — show the most recently logged of each, newest first.
                (f.map { SearchResult.Food(it.item, it.lastDate, 0) } +
                    e.map { SearchResult.Exercise(it, 0) })
                    .sortedByDescending { it.lastDate }
                    .take(MAX_RESULTS)
            } else {
                val scored = ArrayList<SearchResult>()
                for (df in f) {
                    val s = FuzzySearch.score(query, df.item.name)
                    if (s > 0) scored += SearchResult.Food(df.item, df.lastDate, s)
                }
                for (ex in e) {
                    val s = FuzzySearch.score(query, ex.name)
                    if (s > 0) scored += SearchResult.Exercise(ex, s)
                }
                scored.sortedWith(
                    compareByDescending<SearchResult> { it.score }.thenByDescending { it.lastDate }
                ).take(MAX_RESULTS)
            }
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Re-log a food to today (current-time meal category), same as the "Your usuals" one-tap. */
    fun logFoodAgain(item: MealLogItemEntity, onDone: () -> Unit) {
        viewModelScope.launch {
            mealRepository.quickLogRecent(item, mealRepository.defaultMealType())
            onDone()
        }
    }

    /** Re-log an exercise to today, recomputing calories from the current weight. */
    fun logExerciseAgain(entry: ExerciseEntryEntity, onDone: () -> Unit) {
        viewModelScope.launch {
            val kg = weightRepository.getLatest().first()?.weightKg ?: 70f
            exerciseRepository.logExercise(entry.name, entry.minutes, entry.met, kg)
            onDone()
        }
    }

    private companion object {
        const val MAX_RESULTS = 50
    }
}
