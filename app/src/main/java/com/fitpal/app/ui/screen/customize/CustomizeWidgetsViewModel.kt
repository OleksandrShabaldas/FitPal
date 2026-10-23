package com.fitpal.app.ui.screen.customize

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.domain.model.CardSize
import com.fitpal.app.domain.model.LayoutEdits
import com.fitpal.app.domain.model.ResolvedCard
import com.fitpal.app.domain.model.ScreenWidgets
import com.fitpal.app.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * The list editor for one screen's cards (Settings → Home & screens → a screen): order, shown or
 * hidden, and each card's size and style. Every change is saved at once; the screen itself follows.
 */
@HiltViewModel
class CustomizeWidgetsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val screenId: String = savedStateHandle.get<String>(Screen.CustomizeWidgets.ARG_SCREEN_ID) ?: ScreenWidgets.HOME
    val screenTitle: String = ScreenWidgets.titles[screenId] ?: "Screen"

    private val _cards = MutableStateFlow(load())
    val cards: StateFlow<List<ResolvedCard>> = _cards

    private fun load(): List<ResolvedCard> =
        ScreenWidgets.resolveCards(screenId, settingsRepository.widgetLayouts.value[screenId].orEmpty())

    private fun edit(change: (List<com.fitpal.app.domain.model.WidgetSetting>) -> List<com.fitpal.app.domain.model.WidgetSetting>) {
        val updated = change(_cards.value.map { it.setting })
        settingsRepository.setWidgetLayout(screenId, updated)
        _cards.value = ScreenWidgets.resolveCards(screenId, updated)
    }

    fun move(fromKey: String, toKey: String) = edit { LayoutEdits.move(it, fromKey, toKey) }
    fun toggle(key: String) = edit { l -> LayoutEdits.update(l, key) { it.copy(enabled = !it.enabled) } }
    fun setSize(key: String, size: CardSize) = edit { l -> LayoutEdits.update(l, key) { it.copy(size = size) } }
    fun setVariant(key: String, variant: String) = edit { l -> LayoutEdits.update(l, key) { it.copy(variant = variant) } }
    fun setPlacement(key: String, placement: com.fitpal.app.domain.model.TrackerPlacement) = edit { LayoutEdits.place(it, key, placement) }
    fun resetToDefault() = edit { ScreenWidgets.defaultLayout(screenId) }

    /** False for a card of a feature that's off — those aren't listed (docs/redesign/06: off = invisible). */
    fun inUse(key: String): Boolean = when {
        key == "fasting" -> settingsRepository.fastingSchedule.value.enabled
        key == "caffeine" -> settingsRepository.caffeineSettings.value.enabled
        key == "limits" -> settingsRepository.dietaryRules.value.any { it.enabled }
        key == "ai_review" || key == "ai" -> settingsRepository.coachEnabled.value
        key.startsWith("limit_") -> settingsRepository.dietaryRules.value.any { it.enabled && "limit_${it.kind.id}" == key }
        else -> true
    }
}
