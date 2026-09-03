package com.fitpal.app.ml

import com.fitpal.app.data.repository.AiReviewRepository
import com.fitpal.app.data.repository.ContextNoteRepository
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
    private val contextNoteRepository: ContextNoteRepository,
    private val nutritionAnalytics: NutritionAnalytics,
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
            // Group items back into their meals so each line carries the meal category and the
            // user's situation tag ([Restaurant]/[Family meal]/…) — context the coach reasons from.
            val foodLog = mealRepository.getLoggedFoodsInRange(from, to)
                .filter { it.mealType != "water" }
                .groupBy { it.date }
                .entries.joinToString("\n") { (date, dayItems) ->
                    val meals = dayItems.groupBy { it.mealLogId }.values.joinToString("; ") { meal ->
                        val head = meal.first()
                        val ctx = head.context?.takeIf { it.isNotBlank() }?.let { " [$it]" } ?: ""
                        val type = head.mealType.replaceFirstChar { it.uppercase() }
                        "$type$ctx: " + meal.joinToString(", ") { "${it.name} (${it.calories.toInt()} kcal)" }
                    }
                    "$date — $meals"
                }
            val profile = settingsRepository.userProfile.value
            val manualGoal = settingsRepository.dailyCalorieGoal.value
            val weight = weightRepository.getLatest().first()
            val targets = weight?.let {
                BmrCalculator.dailyTargets(profile, it.weightKg, manualGoal, settingsRepository.macroSelection.value)
            }
            val weights = weightRepository.getAll().first()
            val isDaily = period.equals("daily", ignoreCase = true) || totalDays <= 1
            val reviewLabel = reviewLabelFor(period, from, to, isDaily)
            val extraContext = buildExtraContext(from, to, isDaily, reviewLabel)

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
    private suspend fun buildExtraContext(from: String, to: String, isDaily: Boolean, reviewLabel: String): String {
        val exByDate = exerciseRepository.entriesInRange(from, to).groupBy { it.date }
        val trim = settingsRepository.stepCalorieReductionPercent.value
        val stepRows = stepRepository.getDailySteps(from, to).first()
        val stepCountByDate = stepRows.associate { it.date to it.steps }
        val stepBurnByDate = stepRows.associate { it.date to (it.caloriesBurned * (100 - trim) / 100f) }
        val burnDates = (exByDate.keys + stepBurnByDate.keys).toSortedSet()
        val activityLines = burnDates.joinToString("\n") { d ->
            val ex = exByDate[d].orEmpty()
            val exKcal = ex.sumOf { it.caloriesBurned.toDouble() }.toInt()
            val steps = stepCountByDate[d] ?: 0
            val stepKcal = stepBurnByDate[d]?.toInt() ?: 0
            val exStr = if (ex.isEmpty()) "no workout"
                // Calistapp-imported workouts carry richer detail (intensity, HR, reps, exercises) —
                // fold it in so the coach reasons from the real session, not just a kcal number.
                else ex.joinToString(", ") {
                    "${it.name} ${it.minutes}min ~${it.caloriesBurned.toInt()}kcal" +
                        com.fitpal.app.sync.CalistappDetails.promptSuffix(it)
                }
            // The raw step COUNT is the strongest "unusual day" signal — a huge count almost always
            // means a hike/long walk/event that shaped the eating (improvised snacks, no real meals).
            // Flag it so the coach reasons from it instead of only seeing a burn number.
            val activeFlag = when {
                steps >= 25000 -> " [UNUSUALLY ACTIVE DAY — a huge step count like this usually means a long hike/walk/event; real meals were probably not an option, so judge the food choices in that light]"
                steps >= 15000 -> " [very active day]"
                else -> ""
            }
            "$d: $exStr; ${"%,d".format(steps)} steps (~${stepKcal}kcal); total burned ~${exKcal + stepKcal}kcal$activeFlag"
        }

        return buildString {
            appendLine("REVIEWING: $reviewLabel. Refer to the day/period by this weekday/date. Do NOT say " +
                "\"today\"/\"yesterday\"/\"tomorrow\" — this overview is cached and may be read days later; use " +
                "the date, \"that day\", \"going forward\", or \"next time\" instead.")
            appendLine()
            if (activityLines.isNotBlank()) {
                appendLine("ACTIVITY & CALORIES BURNED (per day — judge intake NET of this; a big step count is a real signal, not a footnote):")
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

            // Pre-computed pattern/trend brief — lets the coach judge "is this unusual / does it matter".
            runCatching { nutritionAnalytics.computeBrief(to, isDaily) }.getOrNull()
                ?.toPromptBlock()?.takeIf { it.isNotBlank() }?.let {
                    appendLine()
                    appendLine(it)
                }

            // The user's own answers to earlier check-in questions — context the numbers can't show.
            val since = runCatching { LocalDate.parse(to).minusDays(30).toString() }.getOrDefault(to)
            val notes = runCatching { contextNoteRepository.getRecentAnswers(since) }.getOrDefault(emptyList())
            if (notes.isNotEmpty()) {
                appendLine()
                appendLine("RECENT CHECK-IN NOTES (the user's own words on days/weeks that looked unusual — weigh these heavily when explaining WHY):")
                notes.forEach { appendLine("- ${it.date} (${it.period}): \"${it.question}\" → \"${it.answer}\"") }
            }

            if (!isDaily) {
                appendLine()
                append(
                    "AFTER the FOCUS and WATCH lines, as the very last line of all, on a new line " +
                        "starting exactly with \"HABITS:\", output an updated, " +
                        "concise (<=200 words) running profile of this user. Cover, in plain sentences: " +
                        "eating PATTERNS (meal timing, frequency, consistency); common TRIGGERS (stress, " +
                        "weekends, social/family meals, boredom); STRENGTHS they show consistently; recurring " +
                        "PROBLEM AREAS (nutrients chronically low, portion control); typical meal CONTEXTS " +
                        "(home vs restaurant vs on-the-go, from the tags); and anything learned from their " +
                        "check-in answers. Merge the existing notes above with what this period shows — keep " +
                        "what's still true, update what changed. That single line is internal memory; keep it " +
                        "out of the review the user reads."
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

    /** A human, absolute label for the reviewed period — so the coach never says a stale "today". */
    private fun reviewLabelFor(period: String, from: String, to: String, isDaily: Boolean): String = runCatching {
        when {
            isDaily -> LocalDate.parse(to).format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy"))
            period.equals("weekly", ignoreCase = true) ->
                "the week of ${LocalDate.parse(from).format(DateTimeFormatter.ofPattern("d MMM"))} – " +
                    LocalDate.parse(to).format(DateTimeFormatter.ofPattern("d MMM yyyy"))
            else -> LocalDate.parse(from).format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        }
    }.getOrDefault(to)

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
