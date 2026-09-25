package com.fitpal.app.ui.screen.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.data.repository.WeightRepository
import com.fitpal.app.domain.BmrCalculator
import com.fitpal.app.domain.DailyTargets
import com.fitpal.app.domain.model.DietaryRuleKind
import com.fitpal.app.domain.model.FastingPreset
import com.fitpal.app.domain.model.FitnessGoal
import com.fitpal.app.domain.model.ReminderKind
import com.fitpal.app.domain.model.Sex
import com.fitpal.app.domain.model.UserProfile
import com.fitpal.app.reminder.ReminderManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Everything the intro collects, as typed — committed in one go at the end. */
data class OnboardingDraft(
    val sex: Sex = Sex.MALE,
    val age: String = "25",
    val height: String = "175",
    val weight: String = "",
    /** True once the weight field was edited — a replay mustn't re-log an old weight as today's. */
    val weightTouched: Boolean = false,
    val goal: FitnessGoal = FitnessGoal.RECOMP,
    val geminiKey: String = "",
    val fastingOn: Boolean = false,
    val fastingPreset: FastingPreset = FastingPreset.SIXTEEN_EIGHT,
    val rules: Set<DietaryRuleKind> = emptySet(),
    val caffeineOn: Boolean = false,
    val mealReminders: Boolean = false,
    val weighInReminder: Boolean = false
) {
    val ageYears: Int? get() = age.toIntOrNull()?.takeIf { it in 10..120 }
    val heightCm: Float? get() = height.toFloatOrNull()?.takeIf { it in 50f..300f }
    val weightKg: Float? get() = weight.replace(',', '.').toFloatOrNull()?.takeIf { it in 20f..400f }
    val aboutYouValid: Boolean get() = ageYears != null && heightCm != null && weightKg != null

    val profile: UserProfile
        get() = UserProfile(sex = sex, ageYears = ageYears ?: 25, heightCm = heightCm ?: 175f, fitnessGoal = goal)

    /** The daily targets these answers work out to (null until there's a weight to base them on). */
    val targets: DailyTargets? get() = weightKg?.let { BmrCalculator.dailyTargets(profile, it) }

    /** ~35 ml per kg — the same automatic water goal the app uses. */
    val waterGoalMl: Int? get() = weightKg?.let { SettingsRepository.autoWaterGoal(it) }
}

/**
 * The first-run intro (and its replay from Settings). Walks through what FitPal does, collects the
 * few stats targets need, and lets the user switch on the optional features — all held as a
 * [OnboardingDraft] and written only when they finish, so backing out of a replay changes nothing.
 * A replay starts from the current settings, so finishing it without touching anything is a no-op.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val weightRepository: WeightRepository,
    private val reminderManager: ReminderManager,
    private val mealRepository: MealRepository
) : ViewModel() {

    private val _draft = MutableStateFlow(OnboardingDraft())
    val draft: StateFlow<OnboardingDraft> = _draft

    init {
        // Start from whatever is already set (a replay, or a first run after an earlier skip).
        viewModelScope.launch {
            val profile = settingsRepository.userProfile.value
            val latest = weightRepository.getLatest().first()
            val fasting = settingsRepository.fastingSchedule.value
            val preset = FastingPreset.entries.firstOrNull {
                it.eatStartMin == fasting.eatStartMin && it.eatEndMin == fasting.eatEndMin
            } ?: FastingPreset.SIXTEEN_EIGHT
            val reminders = settingsRepository.reminders.value
            _draft.value = OnboardingDraft(
                sex = profile.sex,
                age = profile.ageYears.toString(),
                height = profile.heightCm.toInt().toString(),
                weight = latest?.weightKg?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "",
                goal = profile.fitnessGoal,
                geminiKey = settingsRepository.geminiApiKey.value.orEmpty(),
                fastingOn = fasting.enabled,
                fastingPreset = preset,
                rules = settingsRepository.dietaryRules.value.filter { it.enabled }.map { it.kind }.toSet(),
                caffeineOn = settingsRepository.caffeineSettings.value.enabled,
                mealReminders = listOf(ReminderKind.BREAKFAST, ReminderKind.LUNCH, ReminderKind.DINNER)
                    .any { reminders[it.key]?.enabled == true },
                weighInReminder = reminders[ReminderKind.WEIGHT.key]?.enabled == true
            )
        }
    }

    fun update(transform: (OnboardingDraft) -> OnboardingDraft) = _draft.update(transform)

    fun setWeight(text: String) = _draft.update {
        it.copy(weight = text.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6), weightTouched = true)
    }

    fun toggleRule(kind: DietaryRuleKind) = _draft.update {
        it.copy(rules = if (kind in it.rules) it.rules - kind else it.rules + kind)
    }

    /**
     * Write everything and mark the intro done, then [onDone] — only once the writes have landed, so
     * leaving the intro can't cancel them halfway.
     */
    fun finish(onDone: () -> Unit) {
        val d = _draft.value
        if (d.ageYears != null && d.heightCm != null) settingsRepository.setUserProfile(d.profile)
        else settingsRepository.setUserProfile(settingsRepository.userProfile.value.copy(fitnessGoal = d.goal))

        val key = d.geminiKey.trim()
        if (key != settingsRepository.geminiApiKey.value.orEmpty()) {
            settingsRepository.setGeminiApiKey(key)
            if (key.isNotEmpty()) settingsRepository.setOnlineAiEnabled(true)
        }

        settingsRepository.setFastingEnabled(d.fastingOn)
        if (d.fastingOn) settingsRepository.setFastingWindow(d.fastingPreset.eatStartMin, d.fastingPreset.eatEndMin)

        DietaryRuleKind.entries.forEach { kind ->
            settingsRepository.setDietaryRuleEnabled(kind, kind in d.rules)
        }

        val caffeineWasOn = settingsRepository.caffeineSettings.value.enabled
        settingsRepository.setCaffeineEnabled(d.caffeineOn)

        listOf(ReminderKind.BREAKFAST, ReminderKind.LUNCH, ReminderKind.DINNER).forEach { kind ->
            reminderManager.setReminder(kind, d.mealReminders, settingsRepository.reminderStateFor(kind).minutes)
        }
        reminderManager.setReminder(
            ReminderKind.WEIGHT, d.weighInReminder, settingsRepository.reminderStateFor(ReminderKind.WEIGHT).minutes
        )
        // The fasting notifications follow the window too.
        reminderManager.reschedule()

        settingsRepository.setOnboarded()
        viewModelScope.launch {
            // Log a weight only if it was typed now (a first run, or a real change on a replay).
            val kg = d.weightKg
            if (kg != null && d.weightTouched) weightRepository.logWeight(kg)
            if (d.caffeineOn && !caffeineWasOn) runCatching { mealRepository.backfillCaffeine() }
            onDone()
        }
    }

    /** Skip the whole intro — still marked done so it doesn't come back; defaults stay until edited. */
    fun skip() = settingsRepository.setOnboarded()
}
