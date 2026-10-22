package com.fitpal.app.ml

import com.fitpal.app.data.local.dao.DailyNutritionRow
import com.fitpal.app.data.local.dao.LoggedFoodRow
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
import com.fitpal.app.domain.model.FastingDayResult
import com.fitpal.app.domain.model.FastingIntake
import com.fitpal.app.domain.model.FastingSchedule
import com.fitpal.app.domain.model.adherenceByDay
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
            // Items grouped back into their meals, so each line carries when it was eaten, the meal
            // category, the user's situation tag ([Restaurant]/[Family meal]/…) and whether it fell in
            // their fast — the context the coach reasons from.
            val meals = coachMeals(mealRepository.getLoggedFoodsInRange(from, to))
            val schedule = settingsRepository.fastingSchedule.value
            val grace = settingsRepository.fastingGrace.value.keys
            val foodLog = meals.groupBy { it.date }.entries.joinToString("\n") { (date, dayMeals) ->
                "$date — " + dayMeals.joinToString("; ") { m ->
                    val ctx = m.context?.let { " [$it]" } ?: ""
                    val fast = if (breaksFast(m, schedule, grace)) " [eaten during their fast]" else ""
                    "${clock(m.minute)} ${m.typeLabel}$ctx, ${m.kcal.roundToInt()} kcal$fast: " +
                        m.items.joinToString(", ") { "${it.name} (${it.calories.toInt()} kcal)" }
                }
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
            val extraContext = buildExtraContext(period, key, from, to, isDaily, reviewLabel, rows, targets, meals)

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
        targets: DailyTargets?,
        meals: List<CoachMeal>
    ): String {
        val schedule = settingsRepository.fastingSchedule.value
        val grace = settingsRepository.fastingGrace.value.keys
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
            inProgressNote(period, from, to, isDaily, schedule)?.let { appendLine(it) }
            appendLine()
            appendLine(dayAndFastingBlock(meals, schedule, grace, from, to))
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

            runCatching {
                notableMoments(meals, rows, targets, activity.mapValues { it.value.totalKcal }, schedule, grace, period, from, to, isDaily)
            }.getOrNull()?.let {
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
                        "eating PATTERNS (meal timing, frequency, consistency, how they keep their fast); common " +
                        "TRIGGERS — emotional and situational (stress, boredom, tiredness, weekends, social/family " +
                        "meals) — and which tools have helped with them; STRENGTHS they show consistently; recurring " +
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
    private fun inProgressNote(
        period: String,
        from: String,
        to: String,
        isDaily: Boolean,
        schedule: FastingSchedule
    ): String? = runCatching {
        val today = LocalDate.now()
        val start = LocalDate.parse(from)
        val end = LocalDate.parse(to)
        if (today.isBefore(start) || today.isAfter(end)) return@runCatching null
        if (isDaily) {
            val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
            val now = clock(nowMin)
            val bed = settingsRepository.bedMinutes.value
            val windowClosed = schedule.enabled && !schedule.isEatingAt(nowMin) &&
                schedule.eatEndMin > schedule.eatStartMin && nowMin >= schedule.eatEndMin
            // Bedtime is close (or just passed): whatever the clock says, the day's eating is done.
            val late = minutesBetween(nowMin, bed) <= LATE_BEFORE_BED_MIN || minutesBetween(bed, nowMin) <= AFTER_BED_MIN
            when {
                windowClosed ->
                    "THE DAY'S EATING IS DONE: it's $now and their eating window closed at ${clock(schedule.eatEndMin)} — " +
                        "they won't eat again until it opens tomorrow. Treat every total as final for this day: a protein " +
                        "or fibre shortfall is something to plan for next time (inside the window), NEVER something to " +
                        "fix with food tonight."
                late ->
                    "THE DAY'S EATING IS ESSENTIALLY DONE: it's $now and they go to bed around ${clock(bed)}. Don't " +
                        "suggest another meal or snack tonight to reach a target — talk about next time instead."
                else -> {
                    val window = if (schedule.enabled) {
                        if (schedule.isEatingAt(nowMin)) " Their eating window is open until ${clock(schedule.eatEndMin)} — anything you suggest adding has to fit before then."
                        else " Their eating window opens at ${clock(schedule.eatStartMin)}."
                    } else ""
                    "IN PROGRESS: this day isn't over — it's $now and more food may still be logged. For the reach " +
                        "goals (protein, fibre) talk about what's left to reach them; never call them missed yet.$window"
                }
            }
        } else {
            val elapsed = ChronoUnit.DAYS.between(start, today) + 1
            val total = ChronoUnit.DAYS.between(start, end) + 1
            "IN PROGRESS: this $period isn't over — it's day $elapsed of $total. Judge the trend so far, not a final verdict."
        }
    }.getOrNull()

    /** One logged meal (not plain water) as the coach reads it: when, what, how much, and whether it was only drinks. */
    private class CoachMeal(
        val date: String,
        val type: String,
        val context: String?,
        val timestamp: Long,
        val kcal: Float,
        val items: List<LoggedFoodRow>,
        val drinkOnly: Boolean,
        val loggedDuringFast: Boolean
    ) {
        val minute: Int get() = minuteOfDay(timestamp)
        val typeLabel: String get() = type.replaceFirstChar { it.uppercase() }
    }

    private fun coachMeals(rows: List<LoggedFoodRow>): List<CoachMeal> =
        rows.filter { it.mealType != "water" }
            .groupBy { it.mealLogId }.values
            .map { items ->
                val head = items.first()
                CoachMeal(
                    date = head.date,
                    type = head.mealType,
                    context = head.context?.takeIf { it.isNotBlank() },
                    timestamp = head.timestamp,
                    kcal = items.sumOf { it.calories.toDouble() }.toFloat(),
                    items = items,
                    drinkOnly = items.all { it.isDrink },
                    loggedDuringFast = items.any { it.loggedDuringFast }
                )
            }
            .sortedWith(compareBy({ it.date }, { it.timestamp }))

    /**
     * Whether [meal] broke the fast — the same rules as Analytics and the Fasting page: a drink log the
     * drink rule allows never does, an "I ate earlier" day never does, a "log anyway" meal always does.
     */
    private fun breaksFast(meal: CoachMeal, schedule: FastingSchedule, grace: Set<String>): Boolean =
        schedule.enabled && meal.date !in grace &&
            !schedule.allowsDuringFast(FastingIntake(meal.drinkOnly, meal.kcal)) &&
            (meal.loggedDuringFast || !schedule.isEatingAt(meal.minute))

    /**
     * When this person eats: their day (Profile wake-up / bedtime) and, when fasting is on, the eating
     * window, the drink rule and how each reviewed day's fast went — plus the rule that follows from it.
     * The coach once suggested a protein meal at 11 PM, mid-fast, to reach the protein target.
     */
    private fun dayAndFastingBlock(
        meals: List<CoachMeal>,
        schedule: FastingSchedule,
        grace: Set<String>,
        from: String,
        to: String
    ): String = buildString {
        val wake = settingsRepository.wakeMinutes.value
        val bed = settingsRepository.bedMinutes.value
        appendLine("YOUR DAY & FASTING:")
        appendLine("- Their day: up around ${clock(wake)}, in bed around ${clock(bed)} (from their profile).")
        if (schedule.enabled) {
            val fastHours = schedule.fastingLengthMin() / 60f
            val hoursText = if (fastHours % 1f == 0f) fastHours.toInt().toString() else "%.1f".format(fastHours)
            appendLine("- They do intermittent fasting: they eat only between ${clock(schedule.eatStartMin)} and " +
                "${clock(schedule.eatEndMin)} (a $hoursText-hour fast). Drinks during the fast: ${schedule.drinkRule.blurb}")
            val breaking = meals.filter { breaksFast(it, schedule, grace) }
            val results = schedule.adherenceByDay(
                meals.filterNot { schedule.allowsDuringFast(FastingIntake(it.drinkOnly, it.kcal)) }.map { it.date to it.minute },
                grace,
                meals.filter { it.loggedDuringFast }.map { it.date }.toSet()
            )
            if (results.isNotEmpty()) {
                val kept = results.count { it.value == FastingDayResult.HELD }
                appendLine("- This period the fast was kept on $kept of ${results.size} day(s) with food logged.")
                results.filterValues { it == FastingDayResult.BROKE }.keys.sorted().take(MAX_LISTED_DAYS).forEach { d ->
                    val what = breaking.filter { it.date == d }.joinToString(", ") { m ->
                        "${m.typeLabel} at ${clock(m.minute)} (${m.kcal.roundToInt()} kcal)" +
                            if (m.loggedDuringFast && schedule.isEatingAt(m.minute)) ", logged during the fast" else ""
                    }.ifBlank { "a meal logged during the fast" }
                    appendLine("  · ${dayLabel(d)}: broken — $what")
                }
                grace.filter { it in from..to && it in results }.sorted().forEach { d ->
                    appendLine("  · ${dayLabel(d)}: kept with an \"I ate earlier\" pass")
                }
            }
        } else {
            appendLine("- No fasting schedule.")
        }
        append("RULE: never suggest eating ")
        if (schedule.enabled) append("outside the eating window or ")
        append("within about 2 hours of bedtime just to reach a target. Anything you'd add (protein, fibre) goes into ")
        append(if (schedule.enabled) "meals inside the window" else "meals they already eat")
        append(", or the shortfall is simply let go for that day — a missed target does far less harm than a late meal")
        append(if (schedule.enabled) " or a broken fast." else ".")
    }

    /**
     * What stood out in the period, computed from the log and ranked by impact on their goal — the
     * oversized meal, the over-budget day, eating after the budget was used up, late or fast-breaking
     * meals, food limits passed. The coach is told to lead with these and to ask about the top one:
     * it used to circle protein for days while saying nothing about an 1,800 kcal dinner.
     */
    private suspend fun notableMoments(
        meals: List<CoachMeal>,
        rows: List<DailyNutritionRow>,
        targets: DailyTargets?,
        burnByDate: Map<String, Int>,
        schedule: FastingSchedule,
        grace: Set<String>,
        period: String,
        from: String,
        to: String,
        isDaily: Boolean
    ): String {
        val base = targets?.calories ?: 0
        val bed = settingsRepository.bedMinutes.value
        val typical = typicalMealKcal(to)

        class Moment(val weight: Float, val text: String)
        val moments = mutableListOf<Moment>()

        // Meals: one line each, with everything notable about it.
        meals.groupBy { it.date }.forEach { (date, dayMeals) ->
            val budget = base + (burnByDate[date] ?: 0)
            var before = 0f
            dayMeals.forEach { m ->
                if (m.drinkOnly || m.kcal < MIN_MEAL_KCAL) {
                    before += m.kcal
                    return@forEach
                }
                val flags = mutableListOf<String>()
                var weight = 0f
                if (base > 0) {
                    val share = m.kcal / base
                    val bigVsUsual = typical > 0f && m.kcal >= typical * BIG_VS_TYPICAL
                    if (share >= BIG_SHARE || (share >= BIG_SHARE_IF_UNUSUAL && bigVsUsual)) {
                        flags += "${(share * 100).roundToInt()}% of the day's base target in one meal" +
                            if (bigVsUsual) " (about ${"%.1f".format(m.kcal / typical)}× their usual meal)" else ""
                        weight += m.kcal
                    }
                    if (before >= budget && budget > 0) {
                        flags += "eaten after the day's ${budget} kcal budget was already used up"
                        weight += m.kcal * 0.8f
                    }
                } else if (m.kcal >= BIG_MEAL_NO_TARGET) {
                    flags += "a very large meal"
                    weight += m.kcal
                }
                if (minutesBetween(m.minute, bed) <= LATE_BEFORE_BED_MIN || minutesBetween(bed, m.minute) <= AFTER_BED_MIN) {
                    flags += "late — bedtime is around ${clock(bed)}"
                    weight += m.kcal * 0.4f
                }
                if (breaksFast(m, schedule, grace)) {
                    flags += "broke their fast"
                    weight += m.kcal * 0.5f + 100f
                }
                before += m.kcal
                if (flags.isNotEmpty()) {
                    val ctx = m.context?.let { " [$it]" } ?: ""
                    val foods = m.items.sortedByDescending { it.calories }.take(3).joinToString(", ") { it.name }
                    moments += Moment(
                        weight,
                        "${dayLabel(date)}, ${m.typeLabel.lowercase()} at ${clock(m.minute)}$ctx: ${m.kcal.roundToInt()} kcal " +
                            "($foods) — ${flags.joinToString("; ")}"
                    )
                }
            }
        }

        // Days over the calorie budget (base + that day's activity, as on Home).
        if (base > 0) rows.filter { it.calories > 0f }.forEach { r ->
            val budget = base + (burnByDate[r.date] ?: 0)
            val over = r.calories - budget
            if (over > budget * OVER_BUDGET_SHARE) {
                moments += Moment(over, "${dayLabel(r.date)}: ${over.roundToInt()} kcal over the ${budget} kcal budget for the day")
            }
        }

        // Food limits passed (the exact tally, partly-counted dishes included).
        settingsRepository.dietaryRules.value.filter { it.enabled && it.dailyLimitKcal > 0 }.forEach { rule ->
            mealRepository.dietaryConsumedByDay(from, to, rule.kind).first()
                .filter { it.kcal > rule.dailyLimitKcal }
                .forEach {
                    moments += Moment(
                        (it.kcal - rule.dailyLimitKcal) * 0.5f,
                        "${dayLabel(it.date)}: ${rule.kind.title.lowercase()} came to ${it.kcal.roundToInt()} kcal against their own ${rule.dailyLimitKcal} kcal limit"
                    )
                }
        }

        val scope = if (isDaily) "day" else period
        if (moments.isEmpty()) {
            return "WHAT STOOD OUT: nothing unusual — no oversized or late meals, no eating past the budget, no " +
                "broken fast or food limit. A steady $scope: don't invent a problem; use the QUESTION to learn what " +
                "made it work or what would make the next one easier."
        }
        return buildString {
            appendLine("WHAT STOOD OUT (computed from the log, biggest impact on their goal first — this is where the " +
                "coaching and your QUESTION start, unless their notes already explain it):")
            moments.sortedByDescending { it.weight }.take(if (isDaily) 5 else 8).forEach { appendLine("- ${it.text}") }
            append("A meal much bigger than usual, eating after the budget was used up, or eating late are often about " +
                "more than hunger (stress, boredom, tiredness, a social setting, or simply not eating enough earlier) — " +
                "be curious about the WHY, not only the food.")
        }
    }

    /** The median kcal of a real meal over the 30 days up to [to] — what "bigger than usual" is measured against. */
    private suspend fun typicalMealKcal(to: String): Float = runCatching {
        val from = LocalDate.parse(to).minusDays(30).toString()
        val sizes = coachMeals(mealRepository.getLoggedFoodsInRange(from, to))
            .filter { !it.drinkOnly && it.kcal >= MIN_MEAL_KCAL }.map { it.kcal }.sorted()
        if (sizes.size < 5) 0f else sizes[sizes.size / 2]
    }.getOrDefault(0f)

    private fun clock(minute: Int): String =
        LocalTime.of((minute / 60).mod(24), minute.mod(60)).format(DateTimeFormatter.ofPattern("h:mm a"))

    private fun dayLabel(date: String): String = runCatching {
        LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE d MMM"))
    }.getOrDefault(date)

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
        // Every recent question, whatever the overview kind — so the coach can see itself circling one theme.
        val asked = past.sortedByDescending { it.generatedAt }.take(RECENT_QUESTIONS).mapNotNull { p ->
            val answer = contextNoteRepository.getFollowUp(p.period, p.periodKey)
            when {
                answer != null -> "\"${answer.question}\" → \"${answer.answer}\""
                else -> ReviewText.parse(p.text).question?.let { "\"$it\" (not answered)" }
            }
        }

        return buildString {
            appendLine("PREVIOUS COACHING (your own earlier overviews for this user, newest first — this is your memory):")
            chosen.forEach { p -> appendLine("- " + describePast(p)) }
            if (ownAnswer != null) {
                appendLine("- An earlier version of THIS overview asked \"${ownAnswer.question}\" → they answered \"${ownAnswer.answer}\".")
            }
            if (asked.isNotEmpty()) {
                appendLine("QUESTIONS YOU ASKED RECENTLY (newest first): ${asked.joinToString("; ")}")
                appendLine("- Your next QUESTION must open a NEW topic, not rephrase one of these. If several of them circle " +
                    "one theme (protein, say), leave that theme out of the question unless it is the top item in WHAT STOOD OUT.")
            }
            appendLine("HOW TO USE THIS MEMORY:")
            appendLine("- Before any new advice, check this period's data against what you advised last time and say " +
                "plainly whether they followed it and whether it showed — point to the food or number that proves it. " +
                "Give real credit for follow-through.")
            appendLine("- Don't repeat earlier advice or observations in the same words. If a point still applies, say " +
                "it has come up before and change the angle (a smaller first step, a different example, or what's " +
                "getting in the way); otherwise move on to what's new.")
            appendLine("- Treat their answers as their own context — build on them, never contradict them.")
            append("- An answer is context for ALL your coaching, not a thread to keep pulling: \"I don't like plain protein " +
                "foods\" means respect it in what you suggest — not ask about protein again next time. Follow what's happening " +
                "in THIS period instead.")
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
        /** How many of the coach's recent questions it's shown, so it stops asking about one theme. */
        const val RECENT_QUESTIONS = 6

        // ---- What stood out ----
        /** Smaller than this isn't a meal worth flagging (a coffee, a piece of fruit). */
        const val MIN_MEAL_KCAL = 150f
        /** One meal this share of the base calorie target is notable on its own… */
        const val BIG_SHARE = 0.6f
        /** …and from this share when it's also well above their usual meal. */
        const val BIG_SHARE_IF_UNUSUAL = 0.4f
        const val BIG_VS_TYPICAL = 1.5f
        /** With no calorie target set, a meal this big is flagged. */
        const val BIG_MEAL_NO_TARGET = 1200f
        /** A day this far over its budget (share of the budget) is flagged. */
        const val OVER_BUDGET_SHARE = 0.05f
        /** "Late": within this long before bedtime… */
        const val LATE_BEFORE_BED_MIN = 120
        /** …or up to this long after it. */
        const val AFTER_BED_MIN = 240
        const val MAX_LISTED_DAYS = 6
    }
}

private fun minuteOfDay(epochMillis: Long): Int =
    java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault()).let { it.hour * 60 + it.minute }

/** Minutes forward from [fromMin] to [toMin] on the clock (0–1439), wrapping past midnight. */
private fun minutesBetween(fromMin: Int, toMin: Int): Int = (toMin - fromMin).mod(24 * 60)
