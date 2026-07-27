package com.fitpal.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.fitpal.app.data.DataManager
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.data.repository.StepRepository
import com.fitpal.app.data.repository.WeightRepository
import com.fitpal.app.domain.model.FitnessGoal
import com.fitpal.app.domain.model.ServingPreset
import com.fitpal.app.domain.model.Sex
import com.fitpal.app.domain.model.UserProfile
import com.fitpal.app.ml.GeminiClient
import com.fitpal.app.ml.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val stepRepository: StepRepository,
    private val weightRepository: WeightRepository,
    private val dataManager: DataManager,
    private val geminiClient: GeminiClient,
    private val networkMonitor: NetworkMonitor,
    private val reminderManager: com.fitpal.app.reminder.ReminderManager,
    private val watchLink: com.fitpal.app.wear.WatchLink
) : ViewModel() {

    // ---- Daily reminder ----
    val reminderEnabled: StateFlow<Boolean> = settingsRepository.reminderEnabled
    val reminderMinutes: StateFlow<Int> = settingsRepository.reminderMinutes
    fun setReminderEnabled(enabled: Boolean) = reminderManager.setEnabled(enabled)
    fun setReminderTime(minutesSinceMidnight: Int) = reminderManager.setTime(minutesSinceMidnight)

    // ---- Meal / weigh-in / AI-overview reminders (keyed by ReminderKind) ----
    val reminders: StateFlow<Map<String, com.fitpal.app.domain.model.ReminderState>> = settingsRepository.reminders
    fun setReminder(kind: com.fitpal.app.domain.model.ReminderKind, enabled: Boolean, minutes: Int) =
        reminderManager.setReminder(kind, enabled, minutes)

    /** Whether exact alarms are currently permitted (drives the "Background reliability" prompt). */
    fun canScheduleExactAlarms(): Boolean = reminderManager.canScheduleExactAlarms()
    /** Re-apply the schedule (e.g. after the user grants the exact-alarm permission). */
    fun rescheduleReminders() = reminderManager.reschedule()

    // ---- Meal time windows (which meal a logged food falls into) ----
    val mealWindows: StateFlow<com.fitpal.app.domain.model.MealWindows> = settingsRepository.mealWindows
    fun setMealWindows(w: com.fitpal.app.domain.model.MealWindows) = settingsRepository.setMealWindows(w)

    // ---- Per-macro target presets ----
    val macroSelection: StateFlow<com.fitpal.app.domain.MacroSelection> = settingsRepository.macroSelection
    fun setMacroTarget(macro: com.fitpal.app.domain.Macro, key: String) = settingsRepository.setMacroTarget(macro, key)

    val dailyCalorieGoal: StateFlow<Int> = settingsRepository.dailyCalorieGoal
    val mealPresets: StateFlow<List<ServingPreset>> = settingsRepository.mealPresets
    val drinkPresets: StateFlow<List<ServingPreset>> = settingsRepository.drinkPresets
    val userProfile: StateFlow<UserProfile> = settingsRepository.userProfile
    val stepCalorieReductionPercent: StateFlow<Int> = settingsRepository.stepCalorieReductionPercent

    /** Latest weight — shown in Profile & goals so setup includes the number targets need most. */
    val latestWeight: StateFlow<com.fitpal.app.data.local.entity.WeightEntryEntity?> =
        weightRepository.getLatest()
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), null)

    fun logWeight(kg: Float) {
        viewModelScope.launch { weightRepository.logWeight(kg) }
    }

    // ---- Home display ----
    val compactEmptyMeals: StateFlow<Boolean> = settingsRepository.compactEmptyMeals
    fun setCompactEmptyMeals(enabled: Boolean) = settingsRepository.setCompactEmptyMeals(enabled)

    // ---- Online AI (Gemini) ----
    val geminiApiKey: StateFlow<String?> = settingsRepository.geminiApiKey
    val onlineAiEnabled: StateFlow<Boolean> = settingsRepository.onlineAiEnabled
    val geminiModel: StateFlow<String> = settingsRepository.geminiModel
    val geminiModel2: StateFlow<String> = settingsRepository.geminiModel2
    val geminiModel3: StateFlow<String> = settingsRepository.geminiModel3

    fun setGeminiApiKey(token: String) {
        settingsRepository.setGeminiApiKey(token)
    }

    fun setOnlineAiEnabled(enabled: Boolean) {
        settingsRepository.setOnlineAiEnabled(enabled)
    }

    fun setGeminiModel(model: String) {
        settingsRepository.setGeminiModel(model)
    }

    fun setGeminiModel2(model: String) {
        settingsRepository.setGeminiModel2(model)
    }

    fun setGeminiModel3(model: String) {
        settingsRepository.setGeminiModel3(model)
    }

    // ---- Personal context / limitations for AI overviews ----
    val personalContext: StateFlow<String> = settingsRepository.personalContext
    fun setPersonalContext(value: String) = settingsRepository.setPersonalContext(value)

    // ---- Wear OS watch link ----

    private val _watchStatus = MutableStateFlow(com.fitpal.app.wear.WatchStatus())
    val watchStatus: StateFlow<com.fitpal.app.wear.WatchStatus> = _watchStatus

    private val _watchChecking = MutableStateFlow(false)
    val watchChecking: StateFlow<Boolean> = _watchChecking

    /** Refresh the "is my watch connected" indicator. */
    fun refreshWatchStatus() {
        viewModelScope.launch {
            _watchChecking.value = true
            _watchStatus.value = watchLink.status()
            _watchChecking.value = false
        }
    }

    /** Re-check the link and push today's numbers to the watch now. */
    fun reconnectWatch() {
        viewModelScope.launch {
            _watchChecking.value = true
            _watchStatus.value = watchLink.reconnectAndPush()
            _watchChecking.value = false
        }
    }

    // ---- Online AI connection test (diagnostics) ----

    private val _onlineTesting = MutableStateFlow(false)
    val onlineTesting: StateFlow<Boolean> = _onlineTesting

    private val _onlineTestStatus = MutableStateFlow<String?>(null)
    val onlineTestStatus: StateFlow<String?> = _onlineTestStatus

    /**
     * Hit Gemini directly (bypassing the router) so we can see the REAL result instead of a
     * silent fallback: a green "Connected" or the exact error (bad model id, invalid key, …).
     * Also reports whether the device looks online and whether today's quota flag is set, which
     * are the other two reasons real analysis would quietly use the on-device model.
     */
    fun testOnlineConnection() {
        viewModelScope.launch {
            _onlineTesting.value = true
            _onlineTestStatus.value = null
            val online = networkMonitor.isOnline()
            val models = settingsRepository.activeModels()
            if (models.isEmpty()) {
                _onlineTesting.value = false
                _onlineTestStatus.value = "No model set — add one in the Model field above."
                return@launch
            }
            // Test the primary AND both fallback models, reporting each separately.
            val report = StringBuilder()
            for (model in models) {
                val result = runCatching { geminiClient.pingModel(model) }
                result.fold(
                    onSuccess = { reply -> report.append("✓ $model — replied \"${reply.take(20)}\"\n") },
                    onFailure = { e -> report.append("✗ $model — ${e.message ?: "unknown error"}\n") }
                )
            }
            if (!online) report.append("Note: the phone looks offline, so live analysis will use on-device.\n")
            _onlineTesting.value = false
            _onlineTestStatus.value = report.toString().trim()
        }
    }

    // ---- Per-model connection test ----

    /** Which model id is currently being tested (null = none). */
    private val _modelTesting = MutableStateFlow<String?>(null)
    val modelTesting: StateFlow<String?> = _modelTesting

    /** Last test result per model id ("✓ …" / "✗ …"). */
    private val _modelTestStatus = MutableStateFlow<Map<String, String>>(emptyMap())
    val modelTestStatus: StateFlow<Map<String, String>> = _modelTestStatus

    /** Test a single model directly (its own button next to the field). */
    fun testModel(model: String) {
        val id = model.trim()
        if (id.isEmpty()) return
        viewModelScope.launch {
            _modelTesting.value = id
            _modelTestStatus.value = _modelTestStatus.value - id  // drop the stale result while testing
            val result = runCatching { geminiClient.pingModel(id) }
            _modelTesting.value = null
            _modelTestStatus.value = _modelTestStatus.value + (id to result.fold(
                onSuccess = { reply -> "✓ Connected — replied \"${reply.take(20)}\"" },
                onFailure = { e -> "✗ ${e.message ?: "failed"}" }
            ))
        }
    }

    fun setDailyCalorieGoal(value: Int) {
        settingsRepository.setDailyCalorieGoal(value)
    }

    fun setStepCalorieReductionPercent(value: Int) {
        settingsRepository.setStepCalorieReductionPercent(value)
    }

    // ---- Data export / import / clear ----

    private val _dataMessage = MutableStateFlow<String?>(null)
    val dataMessage: StateFlow<String?> = _dataMessage

    fun exportData(uri: Uri) {
        viewModelScope.launch {
            _dataMessage.value = if (dataManager.export(uri)) "Data exported" else "Export failed"
        }
    }

    fun importData(uri: Uri) {
        viewModelScope.launch {
            _dataMessage.value =
                if (dataManager.import(uri)) "Data imported — reopen the app to see everything" else "Import failed"
        }
    }

    fun clearData() {
        viewModelScope.launch {
            dataManager.clearUserData()
            _dataMessage.value = "All your data was cleared"
        }
    }

    // ---- Health Connect (Samsung Health steps) ----

    val healthConnectAvailable: Boolean = stepRepository.healthConnectAvailable()
    val healthConnectPermissions: Set<String> = stepRepository.healthConnectPermissions

    private val _healthConnected = MutableStateFlow(false)
    val healthConnected: StateFlow<Boolean> = _healthConnected

    private val _healthSyncMessage = MutableStateFlow<String?>(null)
    val healthSyncMessage: StateFlow<String?> = _healthSyncMessage

    init {
        refreshHealthConnect()
    }

    fun refreshHealthConnect() {
        viewModelScope.launch { _healthConnected.value = stepRepository.hasHealthConnectPermission() }
    }

    /** Called after the Health Connect permission dialog returns. */
    fun onHealthPermissionResult() {
        viewModelScope.launch {
            val granted = stepRepository.hasHealthConnectPermission()
            _healthConnected.value = granted
            if (granted) syncSteps()
        }
    }

    fun syncSteps() {
        viewModelScope.launch {
            val kg = weightRepository.getLatest().first()?.weightKg ?: 70f
            val n = stepRepository.syncFromHealthConnect(kg)
            _healthSyncMessage.value =
                if (n > 0) "Synced $n day${if (n == 1) "" else "s"} of steps"
                else "Connected — no step data found yet"
        }
    }

    fun setUserProfile(profile: UserProfile) {
        settingsRepository.setUserProfile(profile)
    }

    fun setSex(sex: Sex) {
        setUserProfile(userProfile.value.copy(sex = sex))
    }

    fun setAge(age: Int) {
        if (age in 10..120) setUserProfile(userProfile.value.copy(ageYears = age))
    }

    fun setHeight(cm: Float) {
        if (cm in 50f..300f) setUserProfile(userProfile.value.copy(heightCm = cm))
    }

    fun setFitnessGoal(goal: FitnessGoal) {
        setUserProfile(userProfile.value.copy(fitnessGoal = goal))
    }

    // --- Meal presets (grams) ---

    fun addMealPreset(label: String, grams: Float) {
        val clean = label.trim()
        if (clean.isEmpty() || grams <= 0f) return
        settingsRepository.setMealPresets(
            settingsRepository.mealPresets.value + ServingPreset(clean, grams)
        )
    }

    fun updateMealPreset(index: Int, label: String, grams: Float) {
        val clean = label.trim()
        if (clean.isEmpty() || grams <= 0f) return
        val list = settingsRepository.mealPresets.value.toMutableList()
        if (index in list.indices) {
            list[index] = ServingPreset(clean, grams)
            settingsRepository.setMealPresets(list)
        }
    }

    fun removeMealPreset(preset: ServingPreset) {
        settingsRepository.setMealPresets(settingsRepository.mealPresets.value - preset)
    }

    // --- Drink presets (millilitres) ---

    fun addDrinkPreset(label: String, ml: Float) {
        val clean = label.trim()
        if (clean.isEmpty() || ml <= 0f) return
        settingsRepository.setDrinkPresets(
            settingsRepository.drinkPresets.value + ServingPreset(clean, ml)
        )
    }

    fun updateDrinkPreset(index: Int, label: String, ml: Float) {
        val clean = label.trim()
        if (clean.isEmpty() || ml <= 0f) return
        val list = settingsRepository.drinkPresets.value.toMutableList()
        if (index in list.indices) {
            list[index] = ServingPreset(clean, ml)
            settingsRepository.setDrinkPresets(list)
        }
    }

    fun removeDrinkPreset(preset: ServingPreset) {
        settingsRepository.setDrinkPresets(settingsRepository.drinkPresets.value - preset)
    }
}
