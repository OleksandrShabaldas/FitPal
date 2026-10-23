package com.fitpal.app.data.repository

import com.fitpal.app.domain.model.DietaryRuleKind
import com.fitpal.app.domain.model.Feature
import com.fitpal.app.domain.model.FeatureSetup
import com.fitpal.app.reminder.ReminderManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The optional features' master switches in one place (docs/redesign/06): the Features page, Home's
 * suggestion card and the first-run setup all turn a feature on — with its basic setup — or off
 * through here, so the side effects (fasting alarms, the caffeine backfill) can't be forgotten.
 * Turning a feature off keeps its settings and data; turning it on again restores them.
 */
@Singleton
class FeatureSwitches @Inject constructor(
    private val settings: SettingsRepository,
    private val reminders: ReminderManager,
    private val meals: MealRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The features that are on right now. */
    val on: Flow<Set<Feature>> = combine(
        settings.fastingSchedule, settings.caffeineSettings, settings.dietaryRules, settings.trailEnabled,
        combine(settings.coachEnabled, settings.plannerEnabled) { c, p -> c to (c && p) }
    ) { fasting, caffeine, rules, trail, (coach, planner) ->
        buildSet {
            if (fasting.enabled) add(Feature.FASTING)
            if (caffeine.enabled) add(Feature.CAFFEINE)
            if (rules.any { it.enabled }) add(Feature.LIMITS)
            if (trail) add(Feature.TRAIL)
            if (coach) add(Feature.COACH)
            if (planner) add(Feature.PLANNER)
        }
    }

    fun isOn(feature: Feature): Boolean = when (feature) {
        Feature.FASTING -> settings.fastingSchedule.value.enabled
        Feature.CAFFEINE -> settings.caffeineSettings.value.enabled
        Feature.LIMITS -> settings.dietaryRules.value.any { it.enabled }
        Feature.TRAIL -> settings.trailEnabled.value
        Feature.COACH -> settings.coachEnabled.value
        Feature.PLANNER -> settings.coachEnabled.value && settings.plannerEnabled.value
    }

    /** The setup as things stand — what a setup sheet starts from. */
    fun current(): FeatureSetup {
        val fasting = settings.fastingSchedule.value
        val caffeine = settings.caffeineSettings.value
        val rules = settings.dietaryRules.value
        val enabled = rules.filter { it.enabled }
        return FeatureSetup(
            eatStartMin = fasting.eatStartMin,
            eatEndMin = fasting.eatEndMin,
            intensity = fasting.intensity,
            caffeineLimitMg = caffeine.dailyLimitMg,
            caffeineHalfLifeHours = caffeine.halfLifeHours,
            // None on yet: offer every limit at its saved cap.
            limits = (enabled.ifEmpty { rules }).associate { it.kind to it.dailyLimitKcal },
            diet = settings.foodProfile.value.diet,
            allergies = settings.foodProfile.value.allergies
        )
    }

    /** Turn [feature] on with [setup]'s choices for it. */
    fun turnOn(feature: Feature, setup: FeatureSetup) {
        when (feature) {
            Feature.FASTING -> {
                settings.setFastingWindow(setup.eatStartMin, setup.eatEndMin)
                settings.setFastingIntensity(setup.intensity)
                settings.setFastingEnabled(true)
                reminders.reschedule()
            }
            Feature.CAFFEINE -> {
                val wasOn = settings.caffeineSettings.value.enabled
                settings.setCaffeineDailyLimit(setup.caffeineLimitMg)
                settings.setCaffeineHalfLife(setup.caffeineHalfLifeHours)
                settings.setCaffeineEnabled(true)
                // The last two days' drinks get their caffeine checked, so the tracker isn't empty.
                if (!wasOn) scope.launch { runCatching { meals.backfillCaffeine() } }
            }
            Feature.LIMITS -> DietaryRuleKind.entries.forEach { kind ->
                setup.limits[kind]?.let { settings.setDietaryRuleLimit(kind, it) }
                settings.setDietaryRuleEnabled(kind, kind in setup.limits)
            }
            Feature.TRAIL -> settings.setTrailEnabled(true)
            Feature.COACH -> {
                setup.geminiKey.trim().takeIf { it.isNotEmpty() }?.let {
                    settings.setGeminiApiKey(it)
                    settings.setOnlineAiEnabled(true)
                }
                settings.setCoachEnabled(true)
                reminders.reschedule()
            }
            Feature.PLANNER -> {
                settings.setFoodProfile(settings.foodProfile.value.copy(diet = setup.diet, allergies = setup.allergies))
                // The planner lives inside the coach.
                if (!settings.coachEnabled.value) { settings.setCoachEnabled(true); reminders.reschedule() }
                settings.setPlannerEnabled(true)
            }
        }
    }

    fun turnOff(feature: Feature) {
        when (feature) {
            Feature.FASTING -> {
                settings.setFastingEnabled(false)
                reminders.reschedule()
            }
            Feature.CAFFEINE -> settings.setCaffeineEnabled(false)
            Feature.LIMITS -> DietaryRuleKind.entries.forEach { settings.setDietaryRuleEnabled(it, false) }
            Feature.TRAIL -> settings.setTrailEnabled(false)
            Feature.COACH -> {
                // Off with it go the planner, the tab, Home's overview card and the overview notifications.
                settings.setCoachEnabled(false)
                reminders.reschedule()
            }
            Feature.PLANNER -> settings.setPlannerEnabled(false)
        }
    }
}
