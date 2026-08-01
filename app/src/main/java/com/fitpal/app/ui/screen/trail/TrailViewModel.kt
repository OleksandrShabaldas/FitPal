package com.fitpal.app.ui.screen.trail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.repository.ChallengeRepository
import com.fitpal.app.data.repository.TrailRepository
import com.fitpal.app.domain.CasePack
import com.fitpal.app.domain.CaseReward
import com.fitpal.app.domain.ChallengeView
import com.fitpal.app.domain.SceneTheme
import com.fitpal.app.domain.ShopState
import com.fitpal.app.domain.TrailDisplay
import com.fitpal.app.domain.TrailProject
import com.fitpal.app.domain.TutorialStep
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrailViewModel @Inject constructor(
    private val repository: TrailRepository,
    private val challengeRepository: ChallengeRepository
) : ViewModel() {

    val display: StateFlow<TrailDisplay?> = repository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val challenges: StateFlow<List<ChallengeView>> = challengeRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Which creative challenge is currently being judged, by slot. */
    private val _checking = MutableStateFlow<String?>(null)
    val checking: StateFlow<String?> = _checking

    val shop: StateFlow<ShopState?> = repository.observeShop()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** The last case result, held so the screen can show it once. */
    private val _lastReward = MutableStateFlow<CaseReward?>(null)
    val lastReward: StateFlow<CaseReward?> = _lastReward

    /** Set briefly after collecting, so the screen can celebrate the number. */
    private val _justCollected = MutableStateFlow(0L)
    val justCollected: StateFlow<Long> = _justCollected

    init {
        viewModelScope.launch {
            // Fold in every logged day since the last visit, then make sure this
            // day/week/month actually has challenges assigned.
            repository.evaluate()
            runCatching { challengeRepository.ensureAssigned() }
        }
    }

    fun claim(challenge: ChallengeView) {
        viewModelScope.launch { challengeRepository.claim(challenge.periodKey, challenge.slot) }
    }

    /** Ask the AI to judge a creative challenge from what's been logged. */
    fun checkCreative(challenge: ChallengeView) {
        if (_checking.value != null) return
        viewModelScope.launch {
            _checking.value = challenge.slot
            runCatching { challengeRepository.runCreativeCheck(challenge.periodKey, challenge.slot) }
            _checking.value = null
        }
    }

    fun collect() {
        viewModelScope.launch {
            val amount = repository.collect()
            if (amount > 0L) _justCollected.value = amount
        }
    }

    fun dismissCollected() { _justCollected.value = 0L }

    fun build(project: TrailProject, variantIndex: Int = 0) {
        viewModelScope.launch { repository.build(project, variantIndex) }
    }

    fun advanceSite() {
        viewModelScope.launch { repository.advanceSite() }
    }

    // ---- Shop ----

    fun buyTheme(theme: SceneTheme) {
        viewModelScope.launch { repository.buyTheme(theme) }
    }

    fun equipTheme(themeId: String) {
        viewModelScope.launch { repository.equipTheme(themeId) }
    }

    fun openCase(pack: CasePack) {
        viewModelScope.launch {
            repository.openCase(pack)?.let { _lastReward.value = it }
        }
    }

    fun dismissReward() { _lastReward.value = null }

    /** Mark a coach-mark as shown so it never appears again. */
    fun dismissTutorial(step: TutorialStep) {
        viewModelScope.launch { repository.markTutorialSeen(step) }
    }
}
