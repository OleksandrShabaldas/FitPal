package com.fitpal.app.data.repository

import com.fitpal.app.data.local.dao.ChallengeDao
import com.fitpal.app.data.local.dao.TrailDao
import com.fitpal.app.data.local.entity.ChallengeEntity
import com.fitpal.app.domain.BmrCalculator
import com.fitpal.app.domain.ChallengeKind
import com.fitpal.app.domain.ChallengePeriod
import com.fitpal.app.domain.ChallengeProgress
import com.fitpal.app.domain.ChallengeRules
import com.fitpal.app.domain.ChallengeSlots
import com.fitpal.app.domain.ChallengeView
import com.fitpal.app.domain.ConcreteType
import com.fitpal.app.domain.GoalAdherence
import com.fitpal.app.domain.PeriodFacts
import com.fitpal.app.domain.model.FitnessGoal
import com.fitpal.app.ml.FoodAnalysisPipeline
import com.fitpal.app.ml.ModelManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Assigns, verifies and pays out challenges (GAME_DESIGN.md §4).
 *
 * Concrete challenges are checked by code and show live progress. Creative ones are
 * written *and* judged by the AI — but only ever from logged data, and with a
 * deliberately strict prompt, so there's nothing to argue with.
 *
 * Points are banked only on [claim].
 */
@Singleton
class ChallengeRepository @Inject constructor(
    private val challengeDao: ChallengeDao,
    private val trailDao: TrailDao,
    private val mealRepository: MealRepository,
    private val stepRepository: StepRepository,
    private val exerciseRepository: ExerciseRepository,
    private val settingsRepository: SettingsRepository,
    private val weightRepository: WeightRepository,
    private val pipeline: FoodAnalysisPipeline,
    private val modelManager: ModelManager
) {
    private val fmt: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private fun aiAvailable(): Boolean = pipeline.canUseOnline() || modelManager.isLlmReady

    /** The period keys currently in play. */
    private fun liveKeys(today: LocalDate = LocalDate.now()) = listOf(
        ChallengeRules.dailyKey(today),
        ChallengeRules.weeklyKey(today),
        ChallengeRules.monthlyKey(today)
    )

    // ---------------- Assignment ----------------

    /**
     * Make sure today's / this week's / this month's challenges exist. Idempotent —
     * `insertIfAbsent` means calling this on every screen open can never reroll a
     * challenge that's already in play.
     */
    suspend fun ensureAssigned() {
        val today = LocalDate.now()
        val dailyKey = ChallengeRules.dailyKey(today)
        val weeklyKey = ChallengeRules.weeklyKey(today)
        val monthlyKey = ChallengeRules.monthlyKey(today)

        val targets = targetsOrNull()
        val stepGoal = 8000

        // The anchor — always present, never rotates.
        challengeDao.insertIfAbsent(
            concrete(
                dailyKey, ChallengeSlots.DAILY_GOAL, ChallengePeriod.DAILY,
                ConcreteType.WITHIN_CALORIE_GOAL, 1f
            )
        )

        // Rotating daily.
        val (dType, dThreshold) = ChallengeRules.pick(
            ChallengeRules.dailyPool(targets, stepGoal), dailyKey
        )
        challengeDao.insertIfAbsent(
            concrete(dailyKey, ChallengeSlots.DAILY_EXTRA, ChallengePeriod.DAILY, dType, dThreshold)
        )

        // Weekly + monthly try a creative AI challenge first, then fall back to concrete.
        assignRotating(
            weeklyKey, ChallengeSlots.WEEKLY, ChallengePeriod.WEEKLY,
            ChallengeRules.weeklyPool(targets)
        )
        assignRotating(
            monthlyKey, ChallengeSlots.MONTHLY, ChallengePeriod.MONTHLY,
            ChallengeRules.monthlyPool()
        )

        challengeDao.pruneStale(liveKeys(today))
    }

    private suspend fun assignRotating(
        periodKey: String,
        slot: String,
        period: ChallengePeriod,
        pool: List<Pair<ConcreteType, Float>>
    ) {
        if (challengeDao.get(periodKey, slot) != null) return

        if (aiAvailable()) {
            val idea = runCatching { generateCreative(period) }.getOrNull()?.trim()
            if (!idea.isNullOrBlank()) {
                challengeDao.insertIfAbsent(
                    ChallengeEntity(
                        periodKey = periodKey,
                        slot = slot,
                        period = period.name,
                        kind = ChallengeKind.CREATIVE.name,
                        text = idea,
                        rewardPoints = period.reward
                    )
                )
                return
            }
        }
        val (type, threshold) = ChallengeRules.pick(pool, periodKey)
        challengeDao.insertIfAbsent(concrete(periodKey, slot, period, type, threshold))
    }

    private fun concrete(
        periodKey: String,
        slot: String,
        period: ChallengePeriod,
        type: ConcreteType,
        threshold: Float
    ) = ChallengeEntity(
        periodKey = periodKey,
        slot = slot,
        period = period.name,
        kind = ChallengeKind.CONCRETE.name,
        typeName = type.name,
        threshold = threshold,
        text = ChallengeRules.describe(type, threshold, period),
        rewardPoints = period.reward
    )

    // ---------------- Observation ----------------

    fun observe(): Flow<List<ChallengeView>> =
        challengeDao.observeFor(liveKeys()).map { rows ->
            val today = LocalDate.now()

            // Gather facts once per period that actually needs them (Flow.map can suspend).
            val periodsNeeded = rows
                .filter { it.kind == ChallengeKind.CONCRETE.name && it.typeName != null }
                .mapNotNull { runCatching { ChallengePeriod.valueOf(it.period) }.getOrNull() }
                .toSet()
            val factsByPeriod = mutableMapOf<ChallengePeriod, PeriodFacts>()
            for (p in periodsNeeded) factsByPeriod[p] = gatherFacts(p, today)

            ChallengeSlots.ALL.mapNotNull { slot ->
                val row = rows.firstOrNull { it.slot == slot } ?: return@mapNotNull null
                val period = runCatching { ChallengePeriod.valueOf(row.period) }
                    .getOrDefault(ChallengePeriod.DAILY)
                val kind = runCatching { ChallengeKind.valueOf(row.kind) }
                    .getOrDefault(ChallengeKind.CONCRETE)

                val progress = if (kind == ChallengeKind.CONCRETE && row.typeName != null) {
                    runCatching { ConcreteType.valueOf(row.typeName) }.getOrNull()?.let { type ->
                        factsByPeriod[period]?.let { ChallengeRules.evaluate(type, row.threshold, it) }
                    }
                } else null

                ChallengeView(
                    slot = row.slot,
                    periodKey = row.periodKey,
                    period = period,
                    kind = kind,
                    text = row.text,
                    reward = row.rewardPoints,
                    progress = progress,
                    claimed = row.claimedAt > 0L,
                    completed = row.completedAt > 0L || (progress?.done == true),
                    verdictNote = row.verdictNote
                )
            }
        }

    /** Anything claimable right now — feeds the badge on Home's streak chip. */
    fun claimableCount(): Flow<Int> = observe().map { list -> list.count { it.canClaim } }

    // ---------------- Actions ----------------

    /** Bank the points. The manual tap is deliberate — it's what brings you to the screen. */
    suspend fun claim(periodKey: String, slot: String): Int {
        val row = challengeDao.get(periodKey, slot) ?: return 0
        if (row.claimedAt > 0L) return 0

        // Re-verify concrete challenges at claim time so the button can't pay out stale.
        val verified = if (row.kind == ChallengeKind.CONCRETE.name && row.typeName != null) {
            val type = runCatching { ConcreteType.valueOf(row.typeName) }.getOrNull()
            val period = runCatching { ChallengePeriod.valueOf(row.period) }
                .getOrDefault(ChallengePeriod.DAILY)
            type != null && ChallengeRules
                .evaluate(type, row.threshold, gatherFacts(period, LocalDate.now())).done
        } else {
            row.completedAt > 0L
        }
        if (!verified) return 0

        val now = System.currentTimeMillis()
        challengeDao.upsert(
            row.copy(completedAt = if (row.completedAt > 0L) row.completedAt else now, claimedAt = now)
        )
        trailDao.getState()?.let { state ->
            trailDao.upsertState(state.copy(points = state.points + row.rewardPoints))
        }
        return row.rewardPoints
    }

    /**
     * Ask the AI to judge a creative challenge from the period's logged data.
     * Returns true if it was met. A "no" stores the reason so it isn't mysterious.
     */
    suspend fun runCreativeCheck(periodKey: String, slot: String): Boolean {
        val row = challengeDao.get(periodKey, slot) ?: return false
        if (row.kind != ChallengeKind.CREATIVE.name || row.completedAt > 0L) return false
        if (!aiAvailable()) return false

        val period = runCatching { ChallengePeriod.valueOf(row.period) }
            .getOrDefault(ChallengePeriod.DAILY)
        val (from, to) = rangeFor(period, LocalDate.now())

        val log = loggedFoodSummary(from, to)
        val prompt = buildString {
            appendLine("You are a strict judge deciding whether a nutrition challenge was met.")
            appendLine()
            appendLine("CHALLENGE: \"${row.text}\"")
            appendLine()
            appendLine("EVERYTHING THEY LOGGED (${from} to ${to}):")
            appendLine(log.ifBlank { "(nothing logged)" })
            appendLine()
            appendLine("Rules:")
            appendLine("- Judge ONLY from the logged data above.")
            appendLine("- If the data does not clearly show the challenge was met, answer NO.")
            appendLine("- Do not give the benefit of the doubt. Missing evidence means NO.")
            appendLine()
            appendLine("Reply in exactly this format and nothing else:")
            appendLine("VERDICT: YES or NO")
            appendLine("WHY: <one short sentence>")
        }

        val response = runCatching { pipeline.generateRawText(prompt) }.getOrNull() ?: return false
        val yes = Regex("VERDICT:\\s*YES", RegexOption.IGNORE_CASE).containsMatchIn(response)
        val why = Regex("WHY:\\s*(.+)").find(response)?.groupValues?.get(1)?.trim()

        challengeDao.upsert(
            row.copy(
                completedAt = if (yes) System.currentTimeMillis() else 0L,
                verdictNote = if (yes) null else why
            )
        )
        return yes
    }

    /** Ask the AI for one interesting, checkable challenge based on how they've been eating. */
    private suspend fun generateCreative(period: ChallengePeriod): String {
        val today = LocalDate.now()
        val lookback = loggedFoodSummary(today.minusDays(10), today.minusDays(1))
        val windowLabel = when (period) {
            ChallengePeriod.DAILY -> "day"
            ChallengePeriod.WEEKLY -> "week"
            ChallengePeriod.MONTHLY -> "month"
        }
        val prompt = buildString {
            appendLine("You invent short, interesting nutrition challenges for one person's food tracker.")
            appendLine()
            appendLine("HOW THEY'VE BEEN EATING RECENTLY:")
            appendLine(lookback.ifBlank { "(not much logged yet)" })
            appendLine()
            appendLine("Write ONE challenge for the coming $windowLabel. Rules:")
            appendLine("- It must be verifiable from the foods they log (names, amounts, times).")
            appendLine("- Make it interesting and a little unusual — NOT a plain number target like \"hit 30g of fibre\".")
            appendLine("- Genuinely useful for their health, and achievable within the $windowLabel.")
            appendLine("- One sentence, under 90 characters, warm plain language, sentence case, no emoji.")
            appendLine()
            appendLine("Reply with ONLY the challenge sentence.")
        }
        val raw = pipeline.generateRawText(prompt)
        return raw.lineSequence()
            .map { it.trim().removePrefix("-").removeSurrounding("\"").trim() }
            .firstOrNull { it.length in 10..140 }
            ?: ""
    }

    // ---------------- Facts ----------------

    private fun rangeFor(period: ChallengePeriod, today: LocalDate): Pair<LocalDate, LocalDate> =
        when (period) {
            ChallengePeriod.DAILY -> today to today
            ChallengePeriod.WEEKLY -> today.minusDays((today.dayOfWeek.value - 1).toLong()) to today
            ChallengePeriod.MONTHLY -> YearMonth.from(today).atDay(1) to today
        }

    /** Gather everything a period's challenges could need, in one pass. */
    private suspend fun gatherFacts(period: ChallengePeriod, today: LocalDate): PeriodFacts {
        val (from, to) = rangeFor(period, today)
        val fromIso = from.format(fmt)
        val toIso = to.format(fmt)

        val rows = mealRepository.getDailyNutritionRange(fromIso, toIso).first()
        val water = mealRepository.getDailyWaterRange(fromIso, toIso).first().sumOf { it.water.toDouble() }.toFloat()
        val steps = stepRepository.getDailySteps(fromIso, toIso).first().sumOf { it.steps }
        val burnRows = exerciseRepository.getDailyBurnRange(fromIso, toIso).first()
        val burn = burnRows.sumOf { it.burned.toDouble() }.toFloat()

        val ctx = goalContext()
        val burnByDate = burnRows.associate { it.date to it.burned }
        val logged = rows.filter { it.calories > 0f }
        val onGoalDays = logged.count { r ->
            val effectiveTarget = ctx.target + ((burnByDate[r.date] ?: 0f) * 0.5f).toInt()
            ctx.goal != null && GoalAdherence.isOnGoal(ctx.goal, r.calories, effectiveTarget, ctx.floor)
        }

        val todayIso = today.format(fmt)
        val todayRow = rows.firstOrNull { it.date == todayIso }
        val todayBurn = burnByDate[todayIso] ?: 0f
        val withinGoal = todayRow != null && ctx.goal != null && GoalAdherence.isOnGoal(
            ctx.goal, todayRow.calories, ctx.target + (todayBurn * 0.5f).toInt(), ctx.floor
        )

        val meals = if (period == ChallengePeriod.DAILY) {
            mealRepository.getItemsForDate(todayIso).first().size
        } else 0

        val trail = trailDao.getState()
        val collectedToday = trail != null && trail.bankedGrowth == 0L && trail.lastTickDate == todayIso

        return PeriodFacts(
            calories = rows.sumOf { it.calories.toDouble() }.toFloat(),
            protein = rows.sumOf { it.protein.toDouble() }.toFloat(),
            fiber = rows.sumOf { it.fiber.toDouble() }.toFloat(),
            waterMl = water,
            steps = steps,
            exerciseBurn = burn,
            mealsLogged = meals,
            loggedDays = logged.size,
            totalDaysSoFar = (java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1).toInt().coerceAtLeast(1),
            onGoalDays = onGoalDays,
            withinGoal = withinGoal,
            collectedGrowth = collectedToday
        )
    }

    /** Names of everything logged in a range, day by day — the evidence the AI judges from. */
    private suspend fun loggedFoodSummary(from: LocalDate, to: LocalDate): String {
        val out = StringBuilder()
        var day = from
        while (!day.isAfter(to)) {
            val items = mealRepository.getItemsForDate(day.format(fmt)).first()
            if (items.isNotEmpty()) {
                val names = items.joinToString(", ") { "${it.name} (${it.grams.toInt()}g, ${it.calories.toInt()}kcal)" }
                out.append(day).append(": ").append(names).append('\n')
            }
            day = day.plusDays(1)
        }
        return out.toString().trim()
    }

    // ---- goal context (mirrors TrailRepository; worth extracting if a fourth copy appears) ----

    private data class GoalContext(val target: Int, val floor: Float, val goal: FitnessGoal?)

    private suspend fun goalContext(): GoalContext {
        val profile = settingsRepository.userProfile.value
        val manualGoal = settingsRepository.dailyCalorieGoal.value
        val weight = weightRepository.getLatest().first()?.weightKg
            ?: return GoalContext(0, 0f, null)
        val targets = BmrCalculator.dailyTargets(profile, weight)
        return GoalContext(
            target = if (manualGoal > 0) manualGoal else targets.calories,
            floor = BmrCalculator.bmr(profile, weight),
            goal = profile.fitnessGoal
        )
    }

    private suspend fun targetsOrNull() =
        weightRepository.getLatest().first()?.weightKg?.let {
            BmrCalculator.dailyTargets(settingsRepository.userProfile.value, it)
        }
}
