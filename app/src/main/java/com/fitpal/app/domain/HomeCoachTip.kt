package com.fitpal.app.domain

import com.fitpal.app.domain.model.FitnessGoal
import java.time.DayOfWeek
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The Home header's coaching one-liner.
 *
 * It's a **priority-ordered decision tree**: the single most useful thing for the current moment
 * wins, and each outcome draws from a *pool* of phrasings so the wording stays fresh. The pick is
 * deterministic from a [Input.seed] (built from the date + hour at the call site), so the line is
 * stable within the hour — it won't flicker while you log — but rotates through the day and across
 * days, so you rarely see the same one twice.
 *
 * Two things make it smarter than a flat "calories left" message:
 *  - **Goal-aware.** The calorie *target already bakes in the deficit/surplus* (Lose fat builds in
 *    −500 kcal). So "a little left" means different things per goal: for Lose fat it means *you're
 *    done* (don't squeeze in a meal), for Build muscle it means *top up to grow*.
 *  - **Week-aware.** A running surplus/deficit over recent days ([Input.weeklyBalanceKcal]) lets it
 *    say "you ran high this week — bank today's room to catch up" or "you're well under, stay the
 *    course" — instead of nudging you to eat when you shouldn't.
 *
 * Pure + deterministic on purpose: no clock/random reads inside, so it's unit-testable and the UI
 * can call it straight from composition.
 */
object HomeCoachTip {

    data class Input(
        val isToday: Boolean,
        val hasGoal: Boolean,
        val hour: Int,
        val dayOfWeek: DayOfWeek,
        val consumed: Int,
        /** Effective goal: the eating target with today's exercise/steps added back. */
        val goal: Int,
        val leftKcal: Int,
        val goalType: FitnessGoal,
        val protein: Int,
        val proteinTarget: Int,
        val waterMl: Int,
        val waterGoalMl: Int,
        val tier: DayScore.Tier,
        val streak: Int,
        /**
         * Net kcal vs target over recent fully-logged days, **today excluded**. Positive = ate above
         * target (a surplus to claw back); negative = banked room. `null` when there isn't enough
         * trustworthy history yet (see [weeklyBalance]).
         */
        val weeklyBalanceKcal: Int?,
        /** Same input + seed → same line; changing the seed rotates the phrasing. */
        val seed: Long
    )

    /** One candidate line: its [priority] (higher wins) and a pool of interchangeable [phrasings]. */
    private data class Tip(val priority: Int, val phrasings: List<String>)

    /** A calorie surplus/deficit over this many kcal is treated as a real weekly signal. */
    private const val WEEKLY_SIGNAL = 400

    fun line(i: Input): String {
        if (!i.isToday) return pick(PAST_DAY, i.seed)
        if (!i.hasGoal) return pick(NO_GOAL, i.seed)

        // Nothing eaten yet — a time-of-day nudge to start logging.
        if (i.consumed <= 0) {
            val pool = when {
                i.hour < 11 -> EMPTY_MORNING
                i.hour < 15 -> EMPTY_MIDDAY
                i.hour < 20 -> EMPTY_AFTERNOON
                else -> EMPTY_NIGHT
            }
            return pick(pool, i.seed)
        }

        val tips = buildList {
            val left = i.leftKcal
            val pctLeft = if (i.goal > 0) left.toFloat() / i.goal else 0f
            val gainGoal = i.goalType == FitnessGoal.BUILD_MUSCLE
            val loseGoal = i.goalType == FitnessGoal.LOSE_FAT || i.goalType == FitnessGoal.RECOMP

            // ---- 100: over budget (always the headline when it happens) ----
            if (left < 0) {
                val over = -left
                add(Tip(100, overBudget(over, i.hour, loseGoal)))
            } else {
                // ---- 95: tiny remainder — the goal-aware "you're done / top up" call ----
                val tiny = left in 0..120 || pctLeft <= 0.10f
                if (tiny) add(Tip(95, tinyRemainder(left, i.goalType)))

                // ---- 90: weekly catch-up — only when there's still real room today to act on ----
                val wb = i.weeklyBalanceKcal
                if (!tiny && left > 120 && wb != null && abs(wb) >= WEEKLY_SIGNAL) {
                    add(Tip(90, weeklyCatchUp(wb, left, i.goalType)))
                }

                // ---- 50: the mid band — several mild signals that rotate for variety ----
                if (left in 121..600) add(Tip(50, remainderGoal(left, i.goalType)))
                if (i.hour >= 16 && pctLeft > 0.5f) add(Tip(50, plentyLeftLate(left, i.goalType)))
                if (i.hour < 12 && pctLeft > 0.6f) add(Tip(50, morningStart(left)))
                proteinBehind(i)?.let { add(Tip(50, it)) }
                hydrationLow(i)?.let { add(Tip(50, it)) }
            }

            // ---- 10: the ambient band — a baseline plus the occasional flourish ----
            add(Tip(10, tierDefault(i.tier, i.leftKcal.coerceAtLeast(0))))
            if (isStreakMilestone(i.streak)) add(Tip(10, streakLine(i.streak)))
            if (i.dayOfWeek == DayOfWeek.SATURDAY || i.dayOfWeek == DayOfWeek.SUNDAY) {
                add(Tip(10, WEEKEND))
            }
        }

        // Highest priority wins; if several share it, the seed rotates the topic, then the wording.
        val maxP = tips.maxOf { it.priority }
        val top = tips.filter { it.priority == maxP }
        val chosen = top[indexFor(i.seed / 7, top.size)]
        return pick(chosen.phrasings, i.seed)
    }

    // ----------------------------------------------------------------------------------------------
    // Weekly balance (pure helper the ViewModel feeds with the trailing week's per-day calories).
    // ----------------------------------------------------------------------------------------------

    /**
     * Net kcal vs [dailyTarget] across [priorDayCalories] (one entry per logged day in the trailing
     * window, today excluded). Positive = ran a surplus; negative = banked a deficit.
     *
     * Days under [completeFraction] of target are dropped: a half-logged day reads as a huge fake
     * deficit, which would wrongly tell the user they've "earned" room. Returns `null` until at
     * least [minDays] reasonably-complete days exist, so the weekly tip only fires on data worth
     * trusting.
     */
    fun weeklyBalance(
        priorDayCalories: List<Float>,
        dailyTarget: Int,
        minDays: Int = 3,
        completeFraction: Float = 0.4f
    ): Int? {
        if (dailyTarget <= 0) return null
        val complete = priorDayCalories.filter { it >= dailyTarget * completeFraction }
        if (complete.size < minDays) return null
        return complete.sumOf { (it - dailyTarget).toDouble() }.roundToInt()
    }

    // ----------------------------------------------------------------------------------------------
    // Phrasing builders (goal-aware where it matters).
    // ----------------------------------------------------------------------------------------------

    private fun overBudget(over: Int, hour: Int, loseGoal: Boolean): List<String> = when {
        hour >= 20 -> listOf(
            "Over for today — keep tomorrow light",
            "Done eating? You're $over over — reset tomorrow",
            "$over past budget — tomorrow's a clean slate"
        )
        over < 150 && loseGoal -> listOf(
            "Just over by $over — tomorrow's a fresh deficit",
            "A touch over ($over) — no harm, ease back tonight",
            "$over past budget — one light day undoes it"
        )
        over < 150 -> listOf(
            "Just over by $over — no big deal",
            "A hair over ($over) — call it even",
            "$over over — nothing a short walk won't square"
        )
        else -> listOf(
            "Over by $over kcal — go easy from here",
            "$over above budget — water and a walk help",
            "Past budget by $over — keep the rest light",
            "$over over — lean on protein and veg from here"
        )
    }

    private fun tinyRemainder(left: Int, goal: FitnessGoal): List<String> = when (goal) {
        FitnessGoal.LOSE_FAT, FitnessGoal.RECOMP -> listOf(
            "You've hit your deficit — no need to squeeze in more",
            "That's your fat-loss target met — call it a day",
            "Right on your deficit. Stopping here is the win",
            "Budget's basically done — leaving it as-is helps the goal",
            "You're at the line that loses fat — nothing more needed"
        )
        FitnessGoal.BUILD_MUSCLE -> listOf(
            "Almost at target — one more small bite to fuel growth",
            "So close — a little more food tops off the surplus",
            "$left to go for the build — don't leave it on the table",
            "Nearly there — growth wants those last $left kcal"
        )
        FitnessGoal.MAINTAIN -> listOf(
            "Right at maintenance — a light bite only if you're hungry",
            "Bang on maintenance — no need for more",
            "You've matched today's burn — leave it, or something tiny"
        )
    }

    private fun weeklyCatchUp(wb: Int, left: Int, goal: FitnessGoal): List<String> {
        val abs = abs(wb)
        val surplus = wb > 0
        return when (goal) {
            FitnessGoal.LOSE_FAT, FitnessGoal.RECOMP ->
                if (surplus) listOf(
                    "You're ~$abs over for the week — leave today's $left to claw it back",
                    "Up $abs across the week — banking today's room helps you catch up",
                    "This week ran high (+$abs) — a lighter today balances it",
                    "A $abs surplus this week — today's a good day to trim it"
                ) else listOf(
                    "You're $abs under this week — nicely ahead, stay the course",
                    "Well under for the week — you've built a real buffer",
                    "$abs banked this week — momentum's on your side"
                )
            FitnessGoal.BUILD_MUSCLE ->
                if (surplus) listOf(
                    "Surplus on track (+$abs this week) — keep feeding the build",
                    "Up $abs this week — right where a bulk should be"
                ) else listOf(
                    "You're $abs short this week — don't skip today's $left, growth needs it",
                    "Under by $abs across the week — eat the full budget today",
                    "Behind on fuel this week ($abs) — top today right up"
                )
            FitnessGoal.MAINTAIN ->
                if (surplus) listOf(
                    "A bit high this week (+$abs) — ease off today to even out",
                    "Up $abs this week — a lighter today brings it level"
                ) else listOf(
                    "A touch under this week ($abs) — eat the rest today to hold steady",
                    "Down $abs this week — no need to hold back today"
                )
        }
    }

    private fun remainderGoal(left: Int, goal: FitnessGoal): List<String> = when (goal) {
        FitnessGoal.LOSE_FAT, FitnessGoal.RECOMP -> listOf(
            "$left kcal left — enough for a light, protein-led bite",
            "A gap of $left — a snack's fine, or bank it for faster loss",
            "$left to go — keep it lean to protect the deficit"
        )
        FitnessGoal.BUILD_MUSCLE -> listOf(
            "$left kcal left — room for a solid snack to keep building",
            "$left to go — a protein + carb meal puts it to work",
            "Still $left for growth — use it"
        )
        FitnessGoal.MAINTAIN -> listOf(
            "$left kcal left — a balanced meal fits nicely",
            "Room for $left — eat normally, you're on track"
        )
    }

    private fun plentyLeftLate(left: Int, goal: FitnessGoal): List<String> = when (goal) {
        FitnessGoal.LOSE_FAT, FitnessGoal.RECOMP -> listOf(
            "Plenty in budget — a proper, protein-led dinner fits",
            "$left to go — room for a satisfying, lighter dinner",
            "Loads left ($left) — fuel up without breaking the deficit"
        )
        FitnessGoal.BUILD_MUSCLE -> listOf(
            "$left left — get a big protein + carb dinner in",
            "Plenty to grow on ($left) — don't undereat tonight"
        )
        FitnessGoal.MAINTAIN -> listOf(
            "Plenty left — treat yourself to a good dinner",
            "$left to go — enjoy a full meal"
        )
    }

    private fun morningStart(left: Int): List<String> = listOf(
        "Off to a good start — $left kcal to go",
        "Good first meal in — $left left for the day",
        "Strong start — $left to spend"
    )

    private fun proteinBehind(i: Input): List<String>? {
        if (i.proteinTarget <= 0) return null
        val ratio = i.protein.toFloat() / i.proteinTarget
        val behind = (i.hour >= 18 && ratio < 0.8f) || (i.hour >= 14 && ratio < 0.6f)
        if (!behind) return null
        val gap = (i.proteinTarget - i.protein).coerceAtLeast(0)
        return listOf(
            "Protein's lagging — about ${gap}g to go; eggs, yogurt or chicken help",
            "You're ${gap}g short on protein — lean meals from here",
            "Behind on protein (${gap}g left) — make the next meal count",
            "Top up protein — ${gap}g to target keeps muscle on board"
        )
    }

    private fun hydrationLow(i: Input): List<String>? {
        if (i.waterGoalMl <= 0 || i.hour !in 10..21) return null
        // Roughly how much you'd want by this hour (waking ~7am, goal by ~9pm).
        val expected = ((i.hour - 7).toFloat() / 14f).coerceIn(0f, 1f)
        if (i.waterMl >= i.waterGoalMl * expected * 0.6f) return null
        val toGo = (i.waterGoalMl - i.waterMl).coerceAtLeast(0)
        return listOf(
            "Time to hydrate — only ${i.waterMl} ml so far",
            "Drink up — you're at ${i.waterMl} of ${i.waterGoalMl} ml",
            "Water's behind — a glass or two now helps",
            "$toGo ml to your water goal — sip through the day"
        )
    }

    private fun tierDefault(tier: DayScore.Tier, left: Int): List<String> = when (tier) {
        DayScore.Tier.GOOD -> listOf(
            "Looking balanced — $left kcal left",
            "Solid day so far — $left to go",
            "Nicely on track — $left left"
        )
        DayScore.Tier.FAIR -> listOf(
            "Steady so far — $left kcal left",
            "Coming together — $left to go",
            "Decent day — $left left"
        )
        DayScore.Tier.LOW -> listOf(
            "A bit unbalanced — lean on protein and veg",
            "Tilted today — add some colour and protein",
            "Even it out — veg and protein from here"
        )
        DayScore.Tier.POOR -> listOf(
            "Let's round the day out — $left kcal left",
            "Room to finish strong — $left left",
            "Pull it together — protein and veg help"
        )
    }

    private fun isStreakMilestone(streak: Int): Boolean =
        streak in intArrayOf(3, 5, 7, 10, 14, 21, 30, 50, 75, 100) || (streak >= 100 && streak % 50 == 0)

    private fun streakLine(streak: Int): List<String> = listOf(
        "$streak-day streak — keep it rolling",
        "$streak days logged in a row, nice",
        "Streak at $streak — don't break the chain"
    )

    // ----------------------------------------------------------------------------------------------
    // Static pools.
    // ----------------------------------------------------------------------------------------------

    private val PAST_DAY = listOf(
        "Reviewing a past day",
        "Looking back at this day",
        "A day in the rear-view"
    )
    private val NO_GOAL = listOf(
        "Add your weight in Settings to set targets",
        "Set your weight in Settings so I can tailor this",
        "Pop your weight in Settings and I'll size your day"
    )
    private val EMPTY_MORNING = listOf(
        "Good morning — log your first meal to start the day",
        "Fresh day — add your first meal when you're ready",
        "Morning — let's get the first meal in"
    )
    private val EMPTY_MIDDAY = listOf(
        "Nothing logged yet — add your first meal",
        "Halfway through — pop in what you've had",
        "No meals yet — start whenever you eat"
    )
    private val EMPTY_AFTERNOON = listOf(
        "The day's half gone — log what you've eaten",
        "Afternoon already — catch up your log",
        "Don't let today slip — add your meals"
    )
    private val EMPTY_NIGHT = listOf(
        "Don't forget to log today's meals",
        "Late one — jot down what you ate",
        "Before bed — log today so the streak holds"
    )
    private val WEEKEND = listOf(
        "Weekend — a quick log keeps you honest",
        "It's the weekend — plan the day a little and you're set",
        "Weekends are where goals wobble — stay loosely on it"
    )

    // ----------------------------------------------------------------------------------------------
    // Deterministic pickers.
    // ----------------------------------------------------------------------------------------------

    private fun indexFor(seed: Long, size: Int): Int =
        if (size <= 0) 0 else (((seed % size) + size) % size).toInt()

    private fun pick(pool: List<String>, seed: Long): String =
        if (pool.isEmpty()) "" else pool[indexFor(seed, pool.size)]
}
