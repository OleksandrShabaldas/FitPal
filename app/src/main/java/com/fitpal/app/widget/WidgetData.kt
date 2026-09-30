package com.fitpal.app.widget

import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.data.repository.WeightRepository
import com.fitpal.app.domain.Caffeine
import com.fitpal.app.domain.CaffeineDose
import com.fitpal.app.domain.CaffeineSettings
import com.fitpal.app.domain.Streaks
import com.fitpal.app.domain.WeightTrend
import com.fitpal.app.domain.model.FastingSchedule
import com.fitpal.app.domain.model.FitnessGoal
import com.fitpal.app.wear.WearStatsPublisher
import com.fitpal.shared.StatsSnapshot
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** One day's bar on the week widget. */
data class WidgetDay(val date: LocalDate, val kcal: Int)

/** The caffeine widget's numbers (only when the tracker is on). */
data class WidgetCaffeine(
    val settings: CaffeineSettings,
    val nowMg: Float,
    val todayMg: Float,
    /** When it drops to sleep-friendly (epoch ms); null when already there or not within ~a day. */
    val sleepFriendlyAt: Long?,
    val anyEstimated: Boolean
) {
    val overLimit: Boolean get() = settings.dailyLimitMg > 0 && todayMg > settings.dailyLimitMg
}

/**
 * Everything a widget can draw, loaded once per refresh and shared by every placed widget. Today's
 * numbers are the watch's [StatsSnapshot] — built the same way Home builds them — so the widgets,
 * the watch and Home can't disagree.
 */
data class WidgetData(
    val today: StatsSnapshot,
    val fasting: FastingSchedule,
    val week: List<WidgetDay>,
    val streak: Int,
    val weights: List<Pair<LocalDate, Float>>,
    /** Change over the last 30 days (kg), same rule as Analytics' chip; null without two weigh-ins. */
    val weightChange30: Float?,
    val goal: FitnessGoal,
    val caffeine: WidgetCaffeine?,
    val nowMillis: Long
) {
    /** "Still available to eat" — the goal plus what's been burned, minus what's been eaten. */
    val caloriesLeft: Int get() = today.caloriesLeft
    val calorieBudget: Int get() = today.caloriesTarget + today.totalBurned
    val hasGoal: Boolean get() = today.caloriesTarget > 0

    companion object {
        /** Placeholder numbers for previews before anything's been loaded (and for the picker). */
        fun sample(): WidgetData {
            val today = LocalDate.now()
            return WidgetData(
                today = StatsSnapshot(
                    dateIso = today.toString(), caloriesConsumed = 1240, caloriesTarget = 2150,
                    exerciseBurned = 180, stepCalories = 140, steps = 7420,
                    proteinG = 82, proteinTargetG = 140, fatG = 48, fatTargetG = 70,
                    carbsG = 150, carbsTargetG = 230, fiberG = 17, fiberTargetG = 30,
                    waterMl = 1250, waterGoalMl = 2500, waterPresets = listOf(250, 330, 500),
                    fastingEnabled = true, fastEatStartMin = 12 * 60, fastEatEndMin = 20 * 60
                ),
                fasting = FastingSchedule(enabled = true),
                week = (6 downTo 0).map { WidgetDay(today.minusDays(it.toLong()), listOf(1980, 2240, 1870, 2105, 1760, 2310, 1240)[6 - it]) },
                streak = 12,
                weights = (0..12).map { today.minusDays((12 - it) * 7L) to (80.6f - it * 0.18f + (if (it % 3 == 0) 0.25f else 0f)) },
                weightChange30 = -0.8f,
                goal = FitnessGoal.LOSE_FAT,
                caffeine = WidgetCaffeine(CaffeineSettings(enabled = true), 96f, 180f, System.currentTimeMillis() + 3 * 3_600_000L, false),
                nowMillis = System.currentTimeMillis()
            )
        }
    }
}

/** Loads [WidgetData] from the same repositories Home reads. Only fetches what the placed kinds need. */
@Singleton
class WidgetDataSource @Inject constructor(
    private val statsPublisher: WearStatsPublisher,
    private val mealRepository: MealRepository,
    private val weightRepository: WeightRepository,
    private val settingsRepository: SettingsRepository
) {
    suspend fun load(kinds: Set<WidgetKind>): WidgetData {
        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        val snapshot = statsPublisher.buildSnapshot()

        val week = if (WidgetKind.WEEK in kinds) {
            val from = today.minusDays(6)
            val rows = mealRepository.getDailyNutritionRange(from.toString(), today.toString()).first()
                .associate { it.date to it.calories.toInt() }
            (0..6).map { i -> from.plusDays(i.toLong()).let { d -> WidgetDay(d, rows[d.toString()] ?: 0) } }
        } else emptyList()

        val streak = if (WidgetKind.WEEK in kinds) {
            val dates = mealRepository.getLoggedDatesDesc().first()
                .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
            Streaks.current(dates, today)
        } else 0

        var weights = emptyList<Pair<LocalDate, Float>>()
        var change30: Float? = null
        if (WidgetKind.WEIGHT in kinds) {
            val rows = weightRepository.getRange(today.minusDays(120).toString(), today.toString()).first()
            weights = rows.mapNotNull { w -> runCatching { LocalDate.parse(w.date) }.getOrNull()?.let { it to w.weightKg } }
                .sortedBy { it.first }
            change30 = WeightTrend.changeOver(rows, today.minusDays(29), today)
        }

        val caffeine = if (WidgetKind.CAFFEINE in kinds && settingsRepository.caffeineSettings.value.enabled) {
            val s = settingsRepository.caffeineSettings.value
            val rows = mealRepository.caffeineRowsInRange(today.minusDays(1).toString(), today.toString()).first()
            val doses = rows.mapNotNull { r -> Caffeine.dose(r.timestamp, r.name, r.grams, r.caffeineMg)?.let { r.date to it } }
            val all: List<CaffeineDose> = doses.map { it.second }
            WidgetCaffeine(
                settings = s,
                nowMg = Caffeine.amountAt(all, now, s.halfLifeHours),
                todayMg = doses.filter { it.first == today.toString() }.sumOf { it.second.mg.toDouble() }.toFloat(),
                sleepFriendlyAt = Caffeine.clearTime(all, now, s.halfLifeHours, CaffeineSettings.SLEEP_FRIENDLY_MG),
                anyEstimated = all.any { it.estimated }
            )
        } else null

        return WidgetData(
            today = snapshot,
            fasting = settingsRepository.fastingSchedule.value,
            week = week,
            streak = streak,
            weights = weights,
            weightChange30 = change30,
            goal = settingsRepository.userProfile.value.fitnessGoal,
            caffeine = caffeine,
            nowMillis = now
        )
    }
}
