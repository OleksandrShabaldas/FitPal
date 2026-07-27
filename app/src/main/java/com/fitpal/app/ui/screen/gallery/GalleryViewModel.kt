package com.fitpal.app.ui.screen.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.local.entity.GalleryFoodEntity
import com.fitpal.app.data.repository.GalleryRepository
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.domain.MealLogContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val galleryRepository: GalleryRepository,
    private val mealRepository: MealRepository,
    mealLogContext: MealLogContext
) : ViewModel() {

    // Category for this visit: a Home "+" pick if present, else time-of-day.
    private val mealType = mealLogContext.consume() ?: mealRepository.defaultMealType()
    // The day to log to — starts from Home's "+" (or today), user-editable.
    private val _logDate = MutableStateFlow(
        mealLogContext.consumeDate()?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
            ?: java.time.LocalDate.now()
    )
    val logDate: StateFlow<java.time.LocalDate> = _logDate
    fun setLogDate(date: java.time.LocalDate) { _logDate.value = date }
    private fun logDateIso(): String = _logDate.value.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    /** Gallery foods — filtered by search query, sorted by most recently used. */
    val foods: StateFlow<List<GalleryFoodEntity>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) galleryRepository.getAllFoods()
            else galleryRepository.searchFoods(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _logged = MutableStateFlow(false)
    val logged: StateFlow<Boolean> = _logged

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    /** One-tap: log a saved food to the chosen day, into the chosen (or time-of-day) category. */
    fun quickLog(id: Long) {
        viewModelScope.launch {
            val food = galleryRepository.toDomainModel(id) ?: return@launch
            mealRepository.logMeal(listOf(food), mealType, date = logDateIso())
            galleryRepository.markUsed(id)
            _logged.value = true
        }
    }

    fun deleteFood(food: GalleryFoodEntity) {
        viewModelScope.launch {
            galleryRepository.deleteFood(food)
        }
    }
}
