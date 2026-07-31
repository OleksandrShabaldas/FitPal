package com.fitpal.app.domain

import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Challenges — the only source of ⭐ points, and the reason to open the game screen.
 * See GAME_DESIGN.md §4.
 *
 * **Hybrid verification, on purpose:**
 * - [ChallengeKind.CONCRETE] challenges are measurable predicates checked by *code* against
 *   logged data. Rock-solid and un-abusable, and they show live progress.
 * - [ChallengeKind.CREATIVE] challenges are written *and judged* by the AI from the period's
 *   logged data. The user never converses with the judge, so it can't be talked into a yes.
 *
 * Points are only banked when the user taps **Claim** — that friction is the whole point.
 */

enum class ChallengePeriod(val label: String, val reward: Int) {
    DAILY("Today", 10),
    WEEKLY("This week", 40),
    MONTHLY("This month", 150)
}

enum class ChallengeKind { CONCRETE, CREATIVE }

/** The fixed slots. `DAILY_GOAL` is always the calorie-goal anchor. */
object ChallengeSlots {
    const val DAILY_GOAL = "daily_goal"
    const val DAILY_EXTRA = "daily_extra"
    const val WEEKLY = "weekly"
    const val MONTHLY = "monthly"

    val ALL = listOf(DAILY_GOAL, DAILY_EXTRA, WEEKLY, MONTHLY)

    fun periodOf(slot: String): ChallengePeriod = when (slot) {
        WEEKLY -> ChallengePeriod.WEEKLY
        MONTHLY -> ChallengePeriod.MONTHLY
        else -> ChallengePeriod.DAILY
    }
}

/** Measurable things we can verify in code. */
enum class ConcreteType {
    WITHIN_CALORIE_GOAL,
    PROTEIN_AT_LEAST,
    FIBER_AT_LEAST,
    WATER_AT_LEAST,
    STEPS_AT_LEAST,
    EXERCISE_BURN_AT_LEAST,
    MEALS_LOGGED_AT_LEAST,
    LOGGED_DAYS_AT_LEAST,
    ON_GOAL_DAYS_AT_LEAST,
    NO_SKIPPED_DAYS,
    COLLECT_GROWTH
}

/** Everything measured for a period, gathered once and then evaluated against. */
data class PeriodFacts(
    val calories: Float = 0f,
    val protein: Float = 0f,
    val fiber: Float = 0f,
    val waterMl: Float = 0f,
    val steps: Int = 0,
    val exerciseBurn: Float = 0f,
    val mealsLogged: Int = 0,
    val loggedDays: Int = 0,
    val totalDaysSoFar: Int = 1,
    val onGoalDays: Int = 0,
    val withinGoal: Boolean = false,
    val collectedGrowth: Boolean = false
)

/** Where a challenge stands right now. */
data class ChallengeProgress(val current: Float, val target: Float) {
    val done: Boolean get() = current >= target
    val fraction: Float get() = if (target <= 0f) 0f else (current / target).coerceIn(0f, 1f)
    /** "18 / 30" — whole numbers, since every target here is countable. */
    val label: String get() = "${current.roundToInt()} / ${target.roundToInt()}"
}

object ChallengeRules {

    /** Human text for a concrete challenge. Warm, plain, sentence case. */
    fun describe(type: ConcreteType, threshold: Float, period: ChallengePeriod): String {
        val t = threshold.roundToInt()
        return when (type) {
            ConcreteType.WITHIN_CALORIE_GOAL -> "Stay within your calorie goal"
            ConcreteType.PROTEIN_AT_LEAST -> "Hit ${t}g of protein"
            ConcreteType.FIBER_AT_LEAST -> "Hit ${t}g of fibre"
            ConcreteType.WATER_AT_LEAST -> "Drink ${t}ml of water"
            ConcreteType.STEPS_AT_LEAST ->
                if (period == ChallengePeriod.DAILY) "Walk $t steps" else "Walk $t steps in total"
            ConcreteType.EXERCISE_BURN_AT_LEAST -> "Burn $t kcal through exercise"
            ConcreteType.MEALS_LOGGED_AT_LEAST -> "Log $t separate things you eat"
            ConcreteType.LOGGED_DAYS_AT_LEAST -> "Log food on $t days"
            ConcreteType.ON_GOAL_DAYS_AT_LEAST -> "Finish $t days on your calorie goal"
            ConcreteType.NO_SKIPPED_DAYS -> "Don't skip a single day"
            ConcreteType.COLLECT_GROWTH -> "Collect your growth on the trail"
        }
    }

    /** Evaluate a concrete challenge against the gathered facts. */
    fun evaluate(type: ConcreteType, threshold: Float, facts: PeriodFacts): ChallengeProgress =
        when (type) {
            ConcreteType.WITHIN_CALORIE_GOAL ->
                ChallengeProgress(if (facts.withinGoal) 1f else 0f, 1f)
            ConcreteType.PROTEIN_AT_LEAST -> ChallengeProgress(facts.protein, threshold)
            ConcreteType.FIBER_AT_LEAST -> ChallengeProgress(facts.fiber, threshold)
            ConcreteType.WATER_AT_LEAST -> ChallengeProgress(facts.waterMl, threshold)
            ConcreteType.STEPS_AT_LEAST -> ChallengeProgress(facts.steps.toFloat(), threshold)
            ConcreteType.EXERCISE_BURN_AT_LEAST -> ChallengeProgress(facts.exerciseBurn, threshold)
            ConcreteType.MEALS_LOGGED_AT_LEAST -> ChallengeProgress(facts.mealsLogged.toFloat(), threshold)
            ConcreteType.LOGGED_DAYS_AT_LEAST -> ChallengeProgress(facts.loggedDays.toFloat(), threshold)
            ConcreteType.ON_GOAL_DAYS_AT_LEAST -> ChallengeProgress(facts.onGoalDays.toFloat(), threshold)
            ConcreteType.NO_SKIPPED_DAYS ->
                ChallengeProgress(facts.loggedDays.toFloat(), facts.totalDaysSoFar.toFloat())
            ConcreteType.COLLECT_GROWTH ->
                ChallengeProgress(if (facts.collectedGrowth) 1f else 0f, 1f)
        }

    // ---- Rotating pools ----

    /** Daily options for the second slot. Thresholds scale off the user's own targets. */
    fun dailyPool(targets: DailyTargets?, stepGoal: Int): List<Pair<ConcreteType, Float>> {
        val protein = (targets?.proteinG ?: 100).toFloat()
        val fiber = (targets?.fiberG ?: 30).toFloat()
        return listOf(
            ConcreteType.PROTEIN_AT_LEAST to protein,
            ConcreteType.FIBER_AT_LEAST to fiber,
            ConcreteType.WATER_AT_LEAST to 2000f,
            ConcreteType.STEPS_AT_LEAST to stepGoal.toFloat(),
            ConcreteType.MEALS_LOGGED_AT_LEAST to 3f,
            ConcreteType.EXERCISE_BURN_AT_LEAST to 200f,
            ConcreteType.COLLECT_GROWTH to 1f
        )
    }

    fun weeklyPool(targets: DailyTargets?): List<Pair<ConcreteType, Float>> {
        val protein = (targets?.proteinG ?: 100).toFloat()
        return listOf(
            ConcreteType.LOGGED_DAYS_AT_LEAST to 6f,
            ConcreteType.ON_GOAL_DAYS_AT_LEAST to 4f,
            ConcreteType.NO_SKIPPED_DAYS to 7f,
            ConcreteType.STEPS_AT_LEAST to 50_000f,
            ConcreteType.PROTEIN_AT_LEAST to protein * 6f,
            ConcreteType.EXERCISE_BURN_AT_LEAST to 900f
        )
    }

    fun monthlyPool(): List<Pair<ConcreteType, Float>> = listOf(
        ConcreteType.LOGGED_DAYS_AT_LEAST to 25f,
        ConcreteType.ON_GOAL_DAYS_AT_LEAST to 18f,
        ConcreteType.STEPS_AT_LEAST to 200_000f,
        ConcreteType.EXERCISE_BURN_AT_LEAST to 4000f
    )

    /** Stable pick per period, so a challenge doesn't change under you mid-day. */
    fun <T> pick(pool: List<T>, seed: String): T =
        pool[(seed.hashCode().toUInt() % pool.size.toUInt()).toInt()]

    // ---- Period keys ----

    fun dailyKey(date: LocalDate): String = "D-$date"
    fun weeklyKey(date: LocalDate): String = "W-${date.minusDays((date.dayOfWeek.value - 1).toLong())}"
    fun monthlyKey(date: LocalDate): String = "M-${date.year}-${"%02d".format(date.monthValue)}"
}

/** A challenge as the screen shows it. */
data class ChallengeView(
    val slot: String,
    val periodKey: String,
    val period: ChallengePeriod,
    val kind: ChallengeKind,
    val text: String,
    val reward: Int,
    val progress: ChallengeProgress?,
    val claimed: Boolean,
    val completed: Boolean,
    /** Last note from the AI judge, when it declined. */
    val verdictNote: String? = null
) {
    val canClaim: Boolean get() = completed && !claimed
    /** Creative challenges need an explicit check — there's nothing to count. */
    val needsCheck: Boolean get() = kind == ChallengeKind.CREATIVE && !completed
}
