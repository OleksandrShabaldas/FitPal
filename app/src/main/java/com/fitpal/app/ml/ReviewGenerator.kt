package com.fitpal.app.ml

import com.fitpal.app.data.local.dao.DailyNutritionRow
import com.fitpal.app.data.repository.AiReviewRepository
import com.fitpal.app.data.repository.ContextNoteRepository
import com.fitpal.app.data.repository.ExerciseRepository
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.PastReview
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.data.repository.StepRepository
import com.fitpal.app.data.repository.WeightRepository
import com.fitpal.app.domain.BmrCalculator
import com.fitpal.app.domain.DailyTargets
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Generates + caches an AI nutrition overview for a period (daily/weekly/monthly). Shared by the
 * AI-review screen (on-demand) and the reminder receiver (auto-generate at the scheduled time), so
 * the logic lives in one place.
 *
 * Three things keep the coach honest and moving forward (all fed through the prompt's extra context):
 *  - a **TARGET CHECK** computed here, exactly, so the model can't say a target was missed when the
 *    numbers say it was met (it once told a user fibre fell short on a 51 g day against a 30 g goal);
 *  - its **memory** — the advice from the last few overviews and the user's answers to its questions,
 *    so it checks follow-through instead of repeating itself;
 *  - an **in-progress** note when the period isn't over, plus a [dataSignature] saved with the review
 *    so a later visit can tell it was written before the rest of the day was logged.
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
            onProgress("Looking back at your earlier overviews…")
            val extraContext = buildExtraContext(period, key, from, to, isDaily, reviewLabel, rows, targets)

            val (rawReview, source) = pipeline.generateNutritionReviewWithSource(
                period, rows, totalDays, profile, targets, weights, foodLog, extraContext
            ) { msg -> onProgress(msg) }

            // The model may append a private "HABITS:" line (weekly/monthly) — pull it out as the
            // updated long-term memory and keep it out of what the user reads.
            val (review, newHabits) = splitHabits(rawReview)
            if (!newHabits.isNullOrBlank()) settingsRepository.setHabitSummary(newHabits)
            aiReviewRepository.save(period, key, review, source, dataSignature(rows))
            review
        } catch (e: Exception) {
            null
        }
    }

    /**
     * The logged totals [period]/[key] covers right now, in the same form saved with a review — so
     * the screen can tell when an overview was written before food that's since been logged.
     */
    suspend fun currentSignature(period: String, key: String): String {
        val (from, to, _) = rangeFor(period, key)
        return dataSignature(mealRepository.getDailyNutritionRange(from, to).first())
    }

    /** A compact fingerprint of the period's logged totals (per day: kcal, protein, fat, carbs, fibre). */
    private fun dataSignature(rows: List<DailyNutritionRow>): String =
        rows.sortedBy { it.date }.joinToString("|") {
            "${it.date}:${it.calories.roundToInt()}:${it.protein.roundToInt()}:${it.fat.roundToInt()}:" +
                "${it.carbs.roundToInt()}:${it.fiber.roundToInt()}"
        }

    /** One day's activity: steps, their (trimmed) burn, and any workouts. */
    private class DayActivity(val steps: Int, val stepKcal: Int, val workouts: String?, val workoutKcal: Int) {
        val totalKcal: Int get() = stepKcal + workoutKcal
    }

    private suspend fun activityByDate(from: String, to: String): Map<String, DayActivity> {
        val exByDate = exerciseRepository.entriesInRange(from, to).groupBy { it.date }
        val trim = settingsRepository.stepCalorieReductionPercent.value
        val stepRows = stepRepository.getDailySteps(from, to).first()
        val stepCountByDate = stepRows.associate { it.date to it.steps }
        val stepBurnByDate = stepRows.associate { it.date to (it.caloriesBurned * (100 - trim) / 100f) }
        return (exByDate.keys + stepBurnByDate.keys).toSortedSet().associateWith { d ->
            val ex = exByDate[d].orEmpty()
            DayActivity(
                steps = stepCountByDate[d] ?: 0,
                stepKcal = stepBurnByDate[d]?.toInt() ?: 0,
                // Calistapp-imported workouts carry richer detail (intensity, HR, reps, exercises) —
                // fold it in so the coach reasons from the real session, not just a kcal number.
                workouts = if (ex.isEmpty()) null else ex.joinToString(", ") {
                    "${it.name} ${it.minutes}min ~${it.caloriesBurned.toInt()}kcal" +
                        com.fitpal.app.sync.CalistappDetails.promptSuffix(it)
                },
                workoutKcal = ex.sumOf { it.caloriesBurned.toDouble() }.toInt()
            )
        }
    }

    /**
     * The extra block appended to the review prompt: per-day activity + calories burned (so intake
     * is judged net of exercise), the exact target check, the user's personal limitations, the
     * long-term habit notes, the coach's own earlier advice and the user's answers to it, and — for
     * weekly/monthly — an instruction to distil an updated, short habit summary at the end.
     */
    private suspend fun buildExtraContext(
        period: String,
        key: String,
        from: String,
        to: String,
        isDaily: Boolean,
        reviewLabel: String,
        rows: List<DailyNutritionRow>,
        targets: DailyTargets?
    ): String {
        val activity = activityByDate(from, to)
        val activityLines = activity.entries.joinToString("\n") { (d, a) ->
            // The raw step COUNT is the strongest "unusual day" signal — a huge count almost always
            // means a hike/long walk/event that shaped the eating (improvised snacks, no real meals).
            // Flag it so the coach reasons from it instead of only seeing a burn number.
            val activeFlag = when {
                a.steps >= 25000 -> " [UNUSUALLY ACTIVE DAY — a huge step count like this usually means a long hike/walk/event; real meals were probably not an option, so judge the food choices in that light]"
                a.steps >= 15000 -> " [very active day]"
                else -> ""
            }
            "$d: ${a.workouts ?: "no workout"}; ${"%,d".format(a.steps)} steps (~${a.stepKcal}kcal); total burned ~${a.totalKcal}kcal$activeFlag"
        }

        return buildString {
            appendLine("REVIEWING: $reviewLabel. Refer to the day/period by this weekday/date. Do NOT say " +
                "\"today\"/\"yesterday\"/\"tomorrow\" — this overview is cached and may be read days later; use " +
                "the date, \"that day\", \"going forward\", or \"next time\" instead.")
            inProgressNote(period, from, to, isDaily)?.let { appendLine(it) }
            appendLine()
            if (activityLines.isNotBlank()) {
                appendLine("ACTIVITY & CALORIES BURNED (per day — judge intake NET of this; a big step count is a real signal, not a footnote):")
                appendLine(activityLines)
            } else {
                appendLine("ACTIVITY & CALORIES BURNED: no workouts or step data logged this period.")
            }

            targetCheck(rows, targets, activity.mapValues { it.value.totalKcal }, isDaily, reviewLabel)
                .takeIf { it.isNotBlank() }?.let {
                    appendLine()
                    appendLine(it)
                }

            settingsRepository.personalContext.value.trim().takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine("PERSONAL CONTEXT / LIMITATIONS (respect these, never suggest what they rule out): $it")
            }
            settingsRepository.habitSummary.value.trim().takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine("LONG-TERM HABIT NOTES (what we've learned about this user over time — background, not a verdict on this period): $it")
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

            // The coach's own earlier advice + the user's answers to its questions.
            runCatching { previousCoaching(period, key, to) }.getOrDefault("").takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine(it)
            }

            if (!isDaily) {
                appendLine()
                append(
                    "AFTER the FOCUS, WATCH, QUESTION and ANSWERS lines, as the very last line of all, on a new line " +
                        "starting exactly with \"HABITS:\", output an updated, " +
                        "concise (<=200 words) running profile of this user. Cover, in plain sentences: " +
                        "eating PATTERNS (meal timing, frequency, consistency); common TRIGGERS (stress, " +
                        "weekends, social/family meals, boredom); STRENGTHS they show consistently; recurring " +
                        "PROBLEM AREAS (nutrients chronically low, portion control); typical meal CONTEXTS " +
                        "(home vs restaurant vs on-the-go, from the tags); what they've said in answer to your " +
                        "questions, and which of your advice they've taken up. Merge the existing notes above with " +
                        "what this period shows — keep what's still true, update what changed (if a 'problem area' " +
                        "is now being met, say so). That single line is internal memory; keep it " +
                        "out of the review the user reads."
                )
            }
        }.trim()
    }

    /**
     * When the reviewed period isn't over yet, say so — otherwise a midday overview reads protein
     * "missed" when dinner simply hasn't been logged (and that's then cached as the day's verdict).
     */
    private fun inProgressNote(period: String, from: String, to: String, isDaily: Boolean): String? = runCatching {
        val today = LocalDate.now()
        val start = LocalDate.parse(from)
        val end = LocalDate.parse(to)
        if (today.isBefore(start) || today.isAfter(end)) return@runCatching null
        if (isDaily) {
            val now = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a"))
            "IN PROGRESS: this day isn't over — it's $now and more food may still be logged. For the reach " +
                "goals (protein, fibre) talk about what's left to reach them; never call them missed yet."
        } else {
            val elapsed = ChronoUnit.DAYS.between(start, today) + 1
            val total = ChronoUnit.DAYS.between(start, end) + 1
            "IN PROGRESS: this $period isn't over — it's day $elapsed of $total. Judge the trend so far, not a final verdict."
        }
    }.getOrNull()

    /**
     * The exact verdict on every target, computed from the same totals the Home rings show (the calorie
     * budget includes the day's activity, as on Home). Handed to the model as fact: when it disagrees
     * with the habit notes or earlier coaching, this wins.
     */
    private fun targetCheck(
        rows: List<DailyNutritionRow>,
        targets: DailyTargets?,
        burnByDate: Map<String, Int>,
        isDaily: Boolean,
        label: String
    ): String {
        if (targets == null) return ""
        val logged = rows.filter { it.calories > 0f }
        if (logged.isEmpty()) return ""
        return buildString {
            appendLine("TARGET CHECK for $label — computed exactly from the log; treat it as FACT and never contradict it. " +
                "Protein and fibre are goals to REACH; calories, fat and carbs are LIMITS to stay under.")
            if (isDaily) {
                val r = logged.first()
                val burn = burnByDate[r.date] ?: 0
                val budget = targets.calories + burn
                appendLine("- Calories: ${r.calories.roundToInt()} eaten vs a $budget kcal budget (${targets.calories} base + $burn activity) → ${calorieVerdict(r.calories, budget)}")
                appendLine("- Protein: ${grams(r.protein)} of ${targets.proteinG} g → ${reachVerdict(r.protein, targets.proteinG)}")
                appendLine("- Fibre: ${grams(r.fiber)} of ${targets.fiberG} g → ${reachVerdict(r.fiber, targets.fiberG)}")
                appendLine("- Fat: ${grams(r.fat)} of a ${targets.fatG} g limit → ${limitVerdict(r.fat, targets.fatG)}")
                appendLine("- Carbs: ${grams(r.carbs)} of a ${targets.carbsG} g limit → ${limitVerdict(r.carbs, targets.carbsG)}")
            } else {
                val n = logged.size
                fun avg(pick: (DailyNutritionRow) -> Float) = logged.sumOf { pick(it).toDouble() }.toFloat() / n
                val underBudget = logged.count { it.calories <= targets.calories + (burnByDate[it.date] ?: 0) }
                val avgBudget = logged.sumOf { targets.calories + (burnByDate[it.date] ?: 0) } / n
                appendLine("- Calories: at or under budget on $underBudget of $n logged days; average ${avg { it.calories }.roundToInt()} eaten vs a ${avgBudget} kcal average budget")
                appendLine("- Protein: met on ${logged.count { reached(it.protein, targets.proteinG) }} of $n days; average ${grams(avg { it.protein })} vs ${targets.proteinG} g (${percent(avg { it.protein }, targets.proteinG)})")
                appendLine("- Fibre: met on ${logged.count { reached(it.fiber, targets.fiberG) }} of $n days; average ${grams(avg { it.fiber })} vs ${targets.fiberG} g (${percent(avg { it.fiber }, targets.fiberG)})")
                appendLine("- Fat: within the limit on ${logged.count { it.fat <= targets.fatG }} of $n days; average ${grams(avg { it.fat })} vs ${targets.fatG} g")
                appendLine("- Carbs: within the limit on ${logged.count { it.carbs <= targets.carbsG }} of $n days; average ${grams(avg { it.carbs })} vs ${targets.carbsG} g")
            }
            append("If the habit notes, the analytics brief or your earlier coaching describe a pattern this check " +
                "contradicts (say \"fibre is usually low\" in a period where fibre was met), the check wins — never " +
                "carry an old pattern into a period where the numbers show otherwise.")
        }
    }

    private fun reached(actual: Float, target: Int) = target > 0 && actual >= target

    private fun reachVerdict(actual: Float, target: Int): String {
        if (target <= 0) return "no target set"
        val ratio = actual / target
        val short = (target - actual).roundToInt()
        return when {
            ratio >= 1f -> "MET (${percent(actual, target)})"
            ratio >= 0.9f -> "just under — ${short} g short (${percent(actual, target)}), close rather than a miss"
            ratio >= 0.7f -> "under — ${short} g short (${percent(actual, target)})"
            else -> "well under — ${short} g short (${percent(actual, target)})"
        }
    }

    private fun limitVerdict(actual: Float, limit: Int): String {
        if (limit <= 0) return "no limit set"
        val over = (actual - limit).roundToInt()
        return when {
            actual <= limit -> "within the limit (${percent(actual, limit)})"
            actual <= limit * 1.1f -> "slightly over (+$over g)"
            else -> "over (+$over g, ${percent(actual, limit)})"
        }
    }

    private fun calorieVerdict(eaten: Float, budget: Int): String {
        val diff = (eaten - budget).roundToInt()
        return when {
            diff <= 0 -> "${-diff} kcal under budget"
            diff <= budget * 0.05f -> "$diff kcal over (within 5%)"
            else -> "$diff kcal over budget"
        }
    }

    private fun grams(v: Float) = "${v.roundToInt()} g"

    private fun percent(actual: Float, target: Int) =
        if (target <= 0) "—" else "${(actual / target * 100f).roundToInt()}%"

    /**
     * The coach's memory: the advice from the last few overviews written before this period ended
     * (up to two of the same kind + two others), and the user's answers to the questions they ended
     * with — plus how to use it. Empty when there's no history yet.
     */
    private suspend fun previousCoaching(period: String, key: String, to: String): String {
        val end = runCatching { LocalDate.parse(to) }.getOrNull() ?: return ""
        val past = aiReviewRepository.recent(RECENT_POOL)
            .filter { !(it.period == period && it.periodKey == key) }
            .filter { p -> startOf(p.period, p.periodKey)?.let { !it.isAfter(end) } ?: false }
        val chosen = (past.filter { it.period == period }.take(2) + past.filter { it.period != period }.take(2))
            .sortedByDescending { it.generatedAt }
        // A refresh rewrites this period's overview — but the answer they gave to its question stays.
        val ownAnswer = contextNoteRepository.getFollowUp(period, key)
        if (chosen.isEmpty() && ownAnswer == null) return ""

        return buildString {
            appendLine("PREVIOUS COACHING (your own earlier overviews for this user, newest first — this is your memory):")
            chosen.forEach { p -> appendLine("- " + describePast(p)) }
            if (ownAnswer != null) {
                appendLine("- An earlier version of THIS overview asked \"${ownAnswer.question}\" → they answered \"${ownAnswer.answer}\".")
            }
            appendLine("HOW TO USE THIS MEMORY:")
            appendLine("- Before any new advice, check this period's data against what you advised last time and say " +
                "plainly whether they followed it and whether it showed — point to the food or number that proves it. " +
                "Give real credit for follow-through.")
            appendLine("- Don't repeat earlier advice or observations in the same words. If a point still applies, say " +
                "it has come up before and change the angle (a smaller first step, a different example, or what's " +
                "getting in the way); otherwise move on to what's new.")
            append("- Treat their answers as their own context — build on them, never contradict them.")
        }
    }

    private suspend fun describePast(p: PastReview): String {
        val parsed = ReviewText.parse(p.text)
        val said = buildList {
            parsed.focus?.let { add("you advised \"$it\"") }
            parsed.watch?.let { add("you flagged \"$it\"") }
        }.ifEmpty { listOf("you wrote: \"${parsed.prose.replace('\n', ' ').take(240).trim()}…\"") }
        val answer = contextNoteRepository.getFollowUp(p.period, p.periodKey)
        val exchange = when {
            answer != null -> " You asked \"${answer.question}\" → they answered \"${answer.answer}\"."
            parsed.question != null -> " You asked \"${parsed.question}\" (not answered)."
            else -> ""
        }
        return "${pastLabel(p.period, p.periodKey)}: ${said.joinToString("; ")}.$exchange"
    }

    private fun pastLabel(period: String, key: String): String = runCatching {
        when (period) {
            "weekly" -> {
                val start = LocalDate.parse(key)
                "Weekly overview, ${start.format(DateTimeFormatter.ofPattern("d MMM"))}–" +
                    start.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM"))
            }
            "monthly" -> "Monthly overview, " + YearMonth.parse(key).atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy"))
            else -> "Daily overview, " + LocalDate.parse(key).format(DateTimeFormatter.ofPattern("EEE d MMM"))
        }
    }.getOrDefault("$period overview ($key)")

    private fun startOf(period: String, key: String): LocalDate? = runCatching {
        if (period == "monthly") YearMonth.parse(key).atDay(1) else LocalDate.parse(key)
    }.getOrNull()

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

    private companion object {
        /** How many recent overviews to look through when picking the coach's memory. */
        const val RECENT_POOL = 12
    }
}
