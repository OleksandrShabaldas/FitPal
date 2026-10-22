package com.fitpal.app.domain

import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A section another screen asked Analytics to open at (e.g. Home's streak with the Trail off opens
 * Habits). Analytics takes it once, scrolls there, and clears it.
 */
@Singleton
class AnalyticsFocus @Inject constructor() {
    val section = MutableStateFlow<String?>(null)
    fun request(sectionId: String) { section.value = sectionId }
}
