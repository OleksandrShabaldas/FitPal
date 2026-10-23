package com.fitpal.app.domain

import com.fitpal.app.domain.model.DietaryRuleKind
import com.fitpal.app.domain.model.Feature
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Home's gentle suggestion (docs/redesign/06): after a week of logging, at most once a week, one
 * optional feature the user's own last two weeks point to — never one that's on, never one they
 * dismissed. Worked out offline from logged data; no AI.
 */
object FeatureSuggestions {

    const val STREAK_NEEDED = 7
    const val LOOKBACK_DAYS = 14L
    private const val WEEK = 7L
    private const val ITEMS_NEEDED = 4
    private const val WINDOW_DAYS_NEEDED = 5
    private const val WINDOW_MIN = 10 * 60

    /** One logged food or drink, as the signals see it. */
    data class Item(val date: LocalDate, val name: String, val grams: Float, val caffeineMg: Float?)

    /** When a meal was eaten. */
    data class MealTime(val date: LocalDate, val minuteOfDay: Int)

    data class Suggestion(val feature: Feature, val text: String)

    /**
     * Whether a suggestion may show today: [streak] days logged in a row, and the last one first
     * shown ([shownOn]) a week or more ago — or today, unless "Not now" hid it today ([hiddenOn]).
     */
    fun mayShow(streak: Int, today: LocalDate, shownOn: LocalDate?, hiddenOn: LocalDate?): Boolean {
        if (streak < STREAK_NEEDED) return false
        if (hiddenOn == today) return false
        return shownOn == null || shownOn == today || ChronoUnit.DAYS.between(shownOn, today) >= WEEK
    }

    /** The most relevant feature to suggest, or null. [on] and [dismissed] (feature ids) are skipped. */
    fun pick(items: List<Item>, meals: List<MealTime>, on: Set<Feature>, dismissed: Set<String>): Suggestion? {
        fun open(f: Feature) = f !in on && f.id !in dismissed
        val candidates = ArrayList<Pair<Float, Suggestion>>()

        if (open(Feature.CAFFEINE)) {
            val caffeinated = items.count { (it.caffeineMg ?: 0f) > 0f || (it.caffeineMg == null && Caffeine.estimateMg(it.name, it.grams) != null) }
            if (caffeinated >= ITEMS_NEEDED) {
                candidates += caffeinated / ITEMS_NEEDED.toFloat() to
                    Suggestion(Feature.CAFFEINE, "You've had coffee most days. Want to see when it wears off?")
            }
        }

        if (open(Feature.LIMITS)) {
            val byKind = DietaryRuleKind.entries.associateWith { kind ->
                items.count { TextMatch.containsAny(it.name, kind.heuristicWords) }
            }
            val (kind, count) = byKind.maxBy { it.value }
            if (count >= ITEMS_NEEDED) {
                val what = when (kind) {
                    DietaryRuleKind.DESSERT -> "desserts"
                    DietaryRuleKind.FRIED -> "fried food"
                    DietaryRuleKind.SUGARY_DRINK -> "sugary drinks"
                }
                candidates += count / ITEMS_NEEDED.toFloat() to Suggestion(Feature.LIMITS, "Want a daily cap for $what?")
            }
        }

        if (open(Feature.FASTING)) {
            val days = fittingWindowDays(meals)
            if (days >= WINDOW_DAYS_NEEDED) {
                candidates += days / WINDOW_DAYS_NEEDED.toFloat() to
                    Suggestion(Feature.FASTING, "Your meals already fit an eating window. Want to track it?")
            }
        }

        candidates.maxByOrNull { it.first }?.let { return it.second }
        // No signal: the next feature in line — the Trail, then the AI coach.
        if (open(Feature.TRAIL)) return Suggestion(Feature.TRAIL, "Want a garden that grows as you keep logging?")
        if (open(Feature.COACH)) return Suggestion(Feature.COACH, "Want a short read on your day, with one thing to try?")
        return null
    }

    /**
     * The most days whose meals all fall inside one 10-hour window — tried from each day's first
     * meal, so it finds the window the meals already keep.
     */
    fun fittingWindowDays(meals: List<MealTime>): Int {
        val byDay = meals.groupBy({ it.date }, { it.minuteOfDay })
        if (byDay.isEmpty()) return 0
        return byDay.values.map { it.min() }.distinct().maxOf { start ->
            byDay.values.count { mins -> mins.all { it >= start && it <= start + WINDOW_MIN } }
        }
    }
}
