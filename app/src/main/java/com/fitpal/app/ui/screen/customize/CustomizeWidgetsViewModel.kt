package com.fitpal.app.ui.screen.customize

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.domain.model.ScreenWidgets
import com.fitpal.app.domain.model.WidgetSetting
import com.fitpal.app.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** A widget row in the customizer: stable key, human label, and whether it's shown. */
data class WidgetRow(val key: String, val label: String, val enabled: Boolean)

@HiltViewModel
class CustomizeWidgetsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val screenId: String = savedStateHandle.get<String>(Screen.CustomizeWidgets.ARG_SCREEN_ID) ?: ScreenWidgets.HOME
    val screenTitle: String = ScreenWidgets.titles[screenId] ?: "Screen"

    private val _rows = MutableStateFlow(loadRows())
    val rows: StateFlow<List<WidgetRow>> = _rows

    private fun loadRows(): List<WidgetRow> {
        val registry = ScreenWidgets.defaultsFor(screenId)
        val resolved = ScreenWidgets.resolve(settingsRepository.widgetLayouts.value[screenId].orEmpty(), registry)
        return resolved.map { WidgetRow(it.key, ScreenWidgets.labelFor(screenId, it.key), it.enabled) }
    }

    /** Move a widget from one position to an adjacent one (called live during a drag). */
    fun move(from: Int, to: Int) {
        val list = _rows.value.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(from))
        _rows.value = list
        persist()
    }

    fun toggle(key: String) {
        _rows.value = _rows.value.map { if (it.key == key) it.copy(enabled = !it.enabled) else it }
        persist()
    }

    fun resetToDefault() {
        val registry = ScreenWidgets.defaultsFor(screenId)
        _rows.value = registry.map { WidgetRow(it.key, it.label, true) }
        persist()
    }

    private fun persist() {
        settingsRepository.setWidgetLayout(screenId, _rows.value.map { WidgetSetting(it.key, it.enabled) })
    }
}
