package com.fitpal.app.ml

import com.fitpal.app.data.repository.AiReviewRepository
import com.fitpal.app.data.repository.ExerciseRepository
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.data.repository.StepRepository
import com.fitpal.app.data.repository.WeightRepository
import com.fitpal.app.domain.BmrCalculator
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates + caches an AI nutrition overview for a period (daily/weekly/monthly). Shared by the
 * AI-review screen (on-demand) and the reminder receiver (auto-generate at the scheduled time), so
 * the logic lives in one place.
 */
@Singleton
class ReviewGenerator @Inject constructor(
    private val mealRepository: MealRepository,
    private val settingsRepository: SettingsRepository,
    private val weightRepository: WeightRepository,
    private val exerciseRepository: ExerciseRepository,
    private val stepRepository: StepRepository,
    private val aiReviewRepository: AiReviewRepository,
    private val pipeline: FoodAnalysisPipeline,
    private val modelManager: ModelManager
) {
    private val isoFormat = DateTimeFormatter.ISO_LOCAL_DATE

    /** Whether a review can be produced right now (online available, or the on-device model ready). */
    fun canGenerate(): Boolean = pipeline.canUseOnline() || modelManager.isLlmReady

    /**
     * Return the cached review for [period]/[key] if present, otherwise generate, cache and return
     * it. Returns null if generation isn't possible or fails.
     */
    suspend fun generateAndCache(
        period: String,
        key: String,
        onProgress: (String) -> Unit = {}
    ): String? {
        aiReviewRepository.get(period, key)?.let { return it.text }
        if (!canGenerate()) return null
        return try {
            onProgress("Gathering your meals and totals…")
            val (from, to, totalDays) = rangeFor(period, key)
            val rows = mealRepository.getDailyNutritionRange(from, to).first()
            val foodLog = mealRepository.getLoggedFoodsInRange(from, to)
                .groupBy { it.date }
                .entries.joinToString("\n") { (date, items) ->
                    "$date: " + items.joinToString(", ") { "${it.name} (${it.calories.toInt()} kcal)" }
                }
            val profile = settingsRepository.userProfile.value
            val manualGoal = settingsRepository.dailyCalorieGoal.value
            val weight = weightRepository.getLatest().first()
            val targets = weight?.let {
                BmrCalculator.dailyTargets(profile, it.weightKg, manualGoal, settingsRepository.macroSelection.value)
            }
            val weights = weightRepository.getAll().first()
            val isDaily = period.equals("daily", ignoreCase = true) || totalDays <= 1
            val extraContext = buildExtraContext(from, to, isDaily)

            val (rawReview, source) = pipeline.generateNutritionReviewWithSource(
                period, rows, totalDays, profile, targets, weights, foodLog, extraContext
            ) { msg -> onProgress(msg) }

            // The model may append a private "HABITS:" line (weekly/monthly) — pull it out as the
            // updated long-term memory and keep it out of what the user reads.
            val (review, newHabits) = splitHabits(rawReview)
            if (!newHabits.isNullOrBlank()) settingsRepository.setHabitSummary(newHabits)
            aiReviewRepository.save(period, key, review, source)
            review
        } catch (e: Exception) {
            null
        }
    }

    /**
     * The extra block appended to the review prompt: per-day activity + calories burned (so intake
     * is judged net of exercise), the user's personal limitations, the long-term habit notes, and —
     * for weekly/monthly — an instruction to distil an updated, short habit summary at the end.
     */
    private suspend fun buildExtraContext(from: String, to: String, isDaily: Boolean): String {
        val exByDate = exerciseRepository.entriesInRange(from, to).groupBy { it.date }
        val trim = settingsRepository.stepCalorieReductionPercent.value
        val stepBurnByDate = stepRepository.getDailySteps(from, to).first()
            .associate { it.date to (it.caloriesBurned * (100 - trim) / 100f) }
        val burnDates = (exByDate.keys + stepBurnByDate.keys).toSortedSet()
        val activityLines = burnDates.joinToString("\n") { d ->
            val ex = exByDate[d].orEmpty()
            val exKcal = ex.sumOf { it.caloriesBurned.toDouble() }.toInt()
            val stepKcal = stepBurnByDate[d]?.toInt() ?: 0
            val exStr = if (ex.isEmpty()) "no workout"
                else ex.joinToString(", ") { "${it.name} ${it.minutes}min ~${it.caloriesBurned.toInt()}kcal" }
            "$d: $exStr; steps ~${stepKcal}kcal; total burned ~${exKcal + stepKcal}kcal"
        }

        return buildString {
            if (activityLines.isNotBlank()) {
                appendLine("ACTIVITY & CALORIES BURNED (per day — judge intake NET of this):")
                appendLine(activityLines)
            } else {
                appendLine("ACTIVITY & CALORIES BURNED: no workouts or step data logged this period.")
            }
            settingsRepository.personalContext.value.trim().takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine("PERSONAL CONTEXT / LIMITATIONS (respect these, never suggest what they rule out): $it")
            }
            settingsRepository.habitSummary.value.trim().takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine("LONG-TERM HABIT NOTES (what we've learned about this user over time): $it")
            }
            if (!isDaily) {
                appendLine()
                append(
                    "AT THE VERY END, on a new line starting exactly with \"HABITS:\", output an updated, " +
                        "concise (<=70 words) running profile of this user's eating habits, patterns, triggers " +
                        "and what works for them — merging the notes above with what this period shows. That " +
                        "single line is internal memory; keep it out of the review the user reads."
                )
            }
        }.trim()
    }

    /**
     * Split a trailing "HABITS:" line off the review: (review-for-user, new-habit-notes-or-null).
     * Anchored to the start of a line (and takes the LAST such line) so an in-prose "…your habits:"
     * can't truncate the review the user sees.
     */
    private fun splitHabits(text: String): Pair<String, String?> {
        val lines = text.lines()
        val idx = lines.indexOfLast { it.trimStart().startsWith("HABITS:", ignoreCase = true) }
        if (idx < 0) return text to null
        val before = lines.subList(0, idx).joinToString("\n").trimEnd()
        val habits = lines.subList(idx, lines.size).joinToString("\n").substringAfter(":").trim()
        return before to habits.ifBlank { null }
    }

    private data class Range(val from: String, val to: String, val totalDays: Int)

    private fun rangeFor(period: String, key: String): Range = when (period) {
        "weekly" -> {
            val start = parseDateOr(key, LocalDate.now().minusDays(6))
            Range(start.format(isoFormat), start.plusDays(6).format(isoFormat), 7)
        }
        "monthly" -> {
            val ym = parseMonthOr(key, YearMonth.now())
            Range(ym.atDay(1).format(isoFormat), ym.atEndOfMonth().format(isoFormat), ym.lengthOfMonth())
        }
        else -> {
            val d = parseDateOr(key, LocalDate.now())
            Range(d.format(isoFormat), d.format(isoFormat), 1)
        }
    }

    private fun parseDateOr(s: String, fallback: LocalDate): LocalDate =
        try { LocalDate.parse(s) } catch (e: Exception) { fallback }

    private fun parseMonthOr(s: String, fallback: YearMonth): YearMonth =
        try { YearMonth.parse(s) } catch (e: Exception) { fallback }
}
