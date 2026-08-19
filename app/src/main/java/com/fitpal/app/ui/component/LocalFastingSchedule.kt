package com.fitpal.app.ui.component

import androidx.compose.runtime.compositionLocalOf
import com.fitpal.app.domain.model.FastingSchedule

/**
 * The user's fasting schedule, published app-wide so both the Home strip and the log-time warning
 * read one source (filled by `MainActivity` from `SettingsRepository.fastingSchedule`, the same way
 * [LocalAiModelSlots] is). Defaults to [FastingSchedule.DISABLED] so anything reading it before it's
 * provided simply behaves as "fasting off".
 */
val LocalFastingSchedule = compositionLocalOf { FastingSchedule.DISABLED }
