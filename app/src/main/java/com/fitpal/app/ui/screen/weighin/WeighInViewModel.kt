package com.fitpal.app.ui.screen.weighin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.local.entity.WeightEntryEntity
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.data.repository.WeightRepository
import com.fitpal.app.domain.model.FitnessGoal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the weigh-in card needs: the last two weigh-ins and the goal (to colour the change). */
data class WeighInData(
    val latest: WeightEntryEntity?,
    val previous: WeightEntryEntity?,
    val goal: FitnessGoal
)

/**
 * Backs the weigh-in destination (`Screen.WeighIn`) — the one place a weight is logged from, whether
 * it's opened by the morning notification, the Home weight card or the Analytics weight card.
 */
@HiltViewModel
class WeighInViewModel @Inject constructor(
    private val weightRepository: WeightRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    /** Null until the history has loaded, so the card never flashes "first weigh-in" by mistake. */
    val data: StateFlow<WeighInData?> = combine(
        weightRepository.getAll(),
        settingsRepository.userProfile
    ) { all, profile ->
        WeighInData(
            latest = all.lastOrNull(),
            previous = all.getOrNull(all.size - 2),
            goal = profile.fitnessGoal
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Log today's weight, then [onSaved] — only after the write lands, so closing can't cancel it. */
    fun save(kg: Float, onSaved: () -> Unit) {
        viewModelScope.launch {
            weightRepository.logWeight(kg)
            onSaved()
        }
    }
}
