package com.fitpal.app.ui.screen.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.data.repository.WeightRepository
import com.fitpal.app.domain.model.FitnessGoal
import com.fitpal.app.domain.model.Sex
import com.fitpal.app.domain.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * First-run setup. Captures the few stats targets actually need (sex, age, height, current weight,
 * goal), writes them to the profile + logs the first weight, then marks onboarding done so the app
 * lands on Home with accurate targets from day one.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val weightRepository: WeightRepository
) : ViewModel() {

    fun complete(sex: Sex, ageYears: Int, heightCm: Float, weightKg: Float, goal: FitnessGoal) {
        settingsRepository.setUserProfile(
            UserProfile(
                sex = sex,
                ageYears = ageYears.coerceIn(10, 120),
                heightCm = heightCm.coerceIn(50f, 300f),
                fitnessGoal = goal
            )
        )
        viewModelScope.launch {
            if (weightKg > 0f) weightRepository.logWeight(weightKg)
            settingsRepository.setOnboarded()
        }
    }

    /** Skip setup — still mark onboarded so we don't nag; targets stay at defaults until edited. */
    fun skip() = settingsRepository.setOnboarded()
}
