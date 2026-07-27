package com.fitpal.app.ui.screen.garden

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.local.entity.CollectedPlantEntity
import com.fitpal.app.data.repository.GardenRepository
import com.fitpal.app.domain.GardenDisplay
import com.fitpal.app.domain.WeekProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GardenViewModel @Inject constructor(
    private val gardenRepository: GardenRepository
) : ViewModel() {

    val display: StateFlow<GardenDisplay?> = gardenRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val collection: StateFlow<List<CollectedPlantEntity>> = gardenRepository.collection
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weekProgress: StateFlow<WeekProgress?> = gardenRepository.weekProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Newly-bloomed plants to celebrate (empty = nothing to show). */
    private val _celebration = MutableStateFlow<List<CollectedPlantEntity>>(emptyList())
    val celebration: StateFlow<List<CollectedPlantEntity>> = _celebration

    init {
        // Catch up on any completed days/weeks, then check for blooms to celebrate.
        viewModelScope.launch {
            gardenRepository.evaluate()
            val fresh = gardenRepository.newlyBloomed()
            if (fresh.isNotEmpty()) _celebration.value = fresh
        }
    }

    fun dismissCelebration() {
        viewModelScope.launch {
            gardenRepository.markBloomsSeen()
            _celebration.value = emptyList()
        }
    }

    /** Manually water today's plant (grows it + earns points). */
    fun waterPlant() {
        viewModelScope.launch { gardenRepository.waterPlant() }
    }
}
