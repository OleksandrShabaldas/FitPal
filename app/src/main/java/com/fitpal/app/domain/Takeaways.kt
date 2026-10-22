package com.fitpal.app.domain

import com.fitpal.app.domain.model.FitnessGoal
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Analytics' takeaways (docs/redesign/07): a few rule-computed sentences about the viewed period —
 * never AI, always offline — ranked so the card at the top of Analytics says what matters most.
 * Also the one-line summaries under each section header, from the same inputs.
 */

enum class TakeawayStatus { OK, ATTENTION }

/** One sentence and the card it's about (tap = jump there). [weight] ranks it. */
data class Takeaway(
    val id: String,
    val status: TakeawayStatus,
    val text: String,
    val cardKey: String,
    val weight: Float
)

/** One day of the period, as the rules see it. Null fields = not tracked / no data that day. */
data class TakeawayDay(
    val date: LocalDate,
    val kcal: Float = 0f,
    val protein: Float = 0f,
    val fat: Float = 0f,
    val carbs: Float = 0f,
    val fiber: Float = 0f,
    /** Steps + workouts (kcal), added to the day's budget like on Home. */
    val burned: Float = 0f,
    val waterMl: Float? = null,
    val steps: Int? = null,
    /** true = fast kept, false = broken, null = not judged. */
    val fastKept: Boolean? = null,
    val caffeineMg: Float? = null,
    /** Kcal counted toward each food limit (by its short name). */
    val limitKcal: Map<String, Float> = emptyMap()
) {
    /** A day with real food logged (a water-only day logs as 0 kcal and doesn't count). */
    val logged: Boolean get() = kcal > 0f
}

data class TakeawayInput(
    /** The period's days so far (logged or not), oldest first. */
    val days: List<TakeawayDay>,
    /** The period before, for "vs last week". */
    val previous: List<TakeawayDay>,
    /** "this week", "these 30 days", "that week" — for sentences. */
    val periodPhrase: String,
    /** "last week" / "the 30 days before". */
    val previousLabel: String,
    val calorieGoal: Int,
    val proteinTarget: Float,
    val fatTarget: Float,
    val carbTarget: Float,
    val fiberTarget: Float,
    val goal: FitnessGoal,
    val waterGoalMl: Int,
    val stepGoal: Int,
    /** Null while the caffeine tracker is off. */
    val caffeineLimitMg: Int?,
    /** Food limits that are on: short name → daily cap (kcal). */
    val limits: Map<String, Int>,
    val fastingOn: Boolean,
    val fastingStreak: Int,
    /** Weight change over the period (null = too few weigh-ins) and whether it suits the goal. */
    val weightChangeKg: Float?,
    val weightOnTrack: Boolean?,
    /** Vitamins and minerals averaging under half their target (needs ≥ 5 logged days). */
    val lowNutrients: List<String>
)

/** The four Analytics sections' one-line summaries. */
data class SectionSummaries(val energy: String, val nutrition: String, val body: String, val habits: String)

object Takeaways {

    const val MAX_SHOWN = 3
    private const val MAX_ATTENTION = 2
    private const val KCAL_PER_KG = 7700f

    /** Every rule that fires, unranked (in rule order). */
    fun all(input: TakeawayInput): List<Takeaway> {
        val out = ArrayList<Takeaway>()
        val logged = input.days.filter { it.logged }
        val n = logged.size

        // ---- Energy balance ----
        if (n >= 2 && input.calorieGoal > 0) {
            val avgNet = logged.map { it.kcal - input.calorieGoal - it.burned }.average().toFloat()
            val r = avgNet.roundToInt()
            val ok = when {
                abs(avgNet) <= 150f -> true
                input.goal == FitnessGoal.LOSE_FAT || input.goal == FitnessGoal.RECOMP -> avgNet < 0f
                input.goal == FitnessGoal.BUILD_MUSCLE -> avgNet > 0f
                else -> false
            }
            val pace = abs(avgNet) * 7f / KCAL_PER_KG
            val text = if (abs(avgNet) < 50f) "Right around your calorie goal on average."
            else buildString {
                append("%,d kcal a day %s your goal.".format(abs(r), if (avgNet < 0) "under" else "over"))
                if (pace >= 0.05f) append(" About %.1f kg a week %s than planned.".format(pace, if (avgNet < 0) "more loss" else "more gain"))
            }
            out += take("energy", ok, text, "balance", 1.0f, (abs(avgNet) / 500f))
        }

        // ---- Protein ----
        if (n >= 2 && input.proteinTarget > 0f) {
            val hits = logged.filter { it.protein >= input.proteinTarget }
            val misses = logged.filter { it.protein < input.proteinTarget }
            val pattern = weekdayPattern(misses.map { it.date })
            val text = "Protein reached its target on ${hits.size} of $n days" +
                (if (pattern != null && misses.size >= 2) ", missing mostly on $pattern." else ".")
            out += take("protein", hits.size >= n * 0.7f, text, "macros", 0.9f, misses.size / n.toFloat())
        }

        // ---- Fibre ----
        if (n >= 2 && input.fiberTarget > 0f) {
            val short = logged.count { it.fiber < input.fiberTarget }
            val text = if (short == 0) "Fibre on target every logged day." else "Fibre short on $short of $n days."
            out += take("fiber", short <= n * 0.3f, text, "macros", 0.6f, short / n.toFloat())
        }

        // ---- Fat / carbs over their limits ----
        listOf("fat" to input.fatTarget, "carbs" to input.carbTarget).forEach { (macro, target) ->
            if (target <= 0f || n == 0) return@forEach
            val over = logged.count { (if (macro == "fat") it.fat else it.carbs) > target }
            if (over >= 2) {
                val name = if (macro == "fat") "Fat" else "Carbs"
                out += take(macro, false, "$name over your limit on $over days.", "macros", 0.6f, over / n.toFloat())
            }
        }

        // ---- Low vitamins / minerals ----
        if (n >= 5 && input.lowNutrients.isNotEmpty()) {
            val names = input.lowNutrients.take(2).joinToString(" and ") { it.replaceFirstChar { c -> c.lowercase() }.let(::nutrientCase) }
            out += take("nutrient", false, "Low on $names ${input.periodPhrase}.", "micros", 0.5f, 0.7f)
        }

        // ---- Logging ----
        if (input.days.isNotEmpty()) {
            val d = input.days.size
            val missing = d - n
            val text = when {
                missing == 0 -> "Logged $n of $d days."
                missing >= 2 -> "$missing days not logged — the averages may be off."
                else -> "Logged $n of $d days."
            }
            out += take("logging", missing < 2, text, "heatmap", 0.8f, missing / d.toFloat())
        }

        // ---- Fasting ----
        if (input.fastingOn) {
            val judged = input.days.mapNotNull { it.fastKept }
            if (judged.size >= 2) {
                val held = judged.count { it }
                val streak = if (input.fastingStreak >= 2) " ${input.fastingStreak} in a row now." else ""
                out += take(
                    "fasting", held >= judged.size * 0.7f, "Fasting held $held of ${judged.size} days.$streak",
                    "fasting", 0.8f, 1f - held / judged.size.toFloat()
                )
            }
        }

        // ---- Caffeine ----
        input.caffeineLimitMg?.let { limit ->
            val withData = input.days.mapNotNull { it.caffeineMg }
            if (withData.isNotEmpty()) {
                val over = if (limit > 0) withData.count { it > limit } else 0
                val avg = withData.average().toFloat()
                if (over > 0 || avg >= 300f) {
                    val text = if (over > 0) "Over $limit mg of caffeine on ${days(over)}."
                    else "About ${avg.roundToInt()} mg of caffeine a day."
                    out += take("caffeine", false, text, "caffeine", 0.6f, (over / withData.size.toFloat()).coerceAtLeast(0.4f))
                }
            }
        }

        // ---- Food limits ----
        input.limits.forEach { (name, cap) ->
            if (cap <= 0) return@forEach
            val over = input.days.count { (it.limitKcal[name] ?: 0f) > cap }
            if (over > 0) {
                out += take("limit_$name", false, "$name over your cap on ${days(over)}.", "limits", 0.7f, over / input.days.size.coerceAtLeast(1).toFloat())
            }
        }

        // ---- Water ----
        if (input.waterGoalMl > 0) {
            val water = input.days.mapNotNull { it.waterMl }.filter { it > 0f }
            if (water.isNotEmpty()) {
                val pct = (water.average() / input.waterGoalMl * 100).roundToInt()
                out += take("water", pct >= 90, "Water at $pct% of your goal on average.", "water", 0.5f, (1f - pct / 100f).coerceAtLeast(0f))
            }
        }

        // ---- Steps ----
        if (input.stepGoal > 0) {
            val steps = input.days.mapNotNull { it.steps }.filter { it > 0 }
            if (steps.isNotEmpty()) {
                val avg = steps.average().roundToInt()
                val atGoal = steps.count { it >= input.stepGoal }
                out += take(
                    "steps", avg >= input.stepGoal * 0.9f, "%,d steps a day, %s at your goal.".format(avg, days(atGoal)),
                    "activity", 0.5f, (1f - avg / input.stepGoal.toFloat()).coerceAtLeast(0f)
                )
            }
        }

        // ---- Weight ----
        input.weightChangeKg?.let { delta ->
            if (abs(delta) < 0.05f) {
                out += take("weight", input.goal == FitnessGoal.MAINTAIN || input.weightOnTrack != false, "Weight steady ${input.periodPhrase}.", "weight", 0.8f, 0.2f)
            } else {
                val sign = if (delta > 0) "+" else "−"
                val track = when (input.weightOnTrack) {
                    true -> ", on track for your goal."
                    false -> ", away from your goal."
                    null -> "."
                }
                out += take(
                    "weight", input.weightOnTrack != false, "$sign%.1f kg ${input.periodPhrase}$track".format(abs(delta)),
                    "weight", 0.8f, if (input.weightOnTrack == false) 0.7f else 0.3f
                )
            }
        }

        // ---- The biggest change vs the period before ----
        biggestChange(input)?.let { out += it }

        return out
    }

    /**
     * The card's rows: the top [MAX_SHOWN] by weight, with at most two needing attention — when more
     * would, the best "on track" one takes the last place (a page that only scolds gets ignored).
     * Ties keep rule order, so the card doesn't reshuffle between recompositions.
     */
    fun pick(all: List<Takeaway>): List<Takeaway> {
        val ranked = all.withIndex().sortedWith(compareByDescending<IndexedValue<Takeaway>> { it.value.weight }.thenBy { it.index }).map { it.value }
        val shown = ArrayList<Takeaway>()
        var attention = 0
        for (t in ranked) {
            if (shown.size == MAX_SHOWN) break
            if (t.status == TakeawayStatus.ATTENTION) {
                if (attention == MAX_ATTENTION) continue
                attention++
            }
            shown += t
        }
        return shown
    }

    fun compute(input: TakeawayInput): List<Takeaway> = pick(all(input))

    /** The one-line summary under each section header. */
    fun sections(input: TakeawayInput): SectionSummaries {
        val logged = input.days.filter { it.logged }
        val prevLogged = input.previous.filter { it.logged }
        val avgKcal = logged.takeIf { it.isNotEmpty() }?.map { it.kcal }?.average()?.toFloat()
        val prevKcal = prevLogged.takeIf { it.isNotEmpty() }?.map { it.kcal }?.average()?.toFloat()
        val energy = if (avgKcal == null) "Nothing logged yet" else buildString {
            append("%,d kcal a day".format(avgKcal.roundToInt()))
            percent(avgKcal, prevKcal)?.let { p -> if (p != 0) append(" · ${if (p < 0) "↓" else "↑"}${abs(p)}%") }
        }

        val n = logged.size
        val nutrition = when {
            n == 0 -> "Nothing logged yet"
            else -> {
                val facts = buildList {
                    if (input.proteinTarget > 0f) logged.count { it.protein < input.proteinTarget }.let { if (it > 0) add(it to "Protein short $it of $n days") }
                    if (input.fiberTarget > 0f) logged.count { it.fiber < input.fiberTarget }.let { if (it > 0) add(it to "Fibre short $it of $n days") }
                    if (input.fatTarget > 0f) logged.count { it.fat > input.fatTarget }.let { if (it > 0) add(it to "Fat over on ${days(it)}") }
                    if (input.carbTarget > 0f) logged.count { it.carbs > input.carbTarget }.let { if (it > 0) add(it to "Carbs over on ${days(it)}") }
                }
                facts.maxByOrNull { it.first }?.second ?: "Macros on target"
            }
        }

        val body = buildList {
            input.weightChangeKg?.let { add((if (it > 0) "+" else if (it < 0) "−" else "") + "%.1f kg".format(abs(it))) }
            input.days.mapNotNull { it.steps }.filter { it > 0 }.takeIf { it.isNotEmpty() }
                ?.let { add("%,d steps".format(it.average().roundToInt())) }
            if (input.waterGoalMl > 0) input.days.mapNotNull { it.waterMl }.filter { it > 0f }.takeIf { it.isNotEmpty() }
                ?.let { add("water ${(it.average() / input.waterGoalMl * 100).roundToInt()}%") }
        }.joinToString(" · ").ifEmpty { "No data yet" }

        val habits = buildList {
            add("Logged $n of ${input.days.size}")
            if (input.fastingOn) {
                val judged = input.days.mapNotNull { it.fastKept }
                if (judged.isNotEmpty()) add("fasting ${judged.count { it }}/${judged.size}")
            }
            val extras = buildList {
                input.caffeineLimitMg?.takeIf { it > 0 }?.let { limit ->
                    val over = input.days.count { (it.caffeineMg ?: 0f) > limit }
                    if (over > 0) add("caffeine over ${over}×")
                }
                input.limits.forEach { (name, cap) ->
                    val over = input.days.count { (it.limitKcal[name] ?: 0f) > cap }
                    if (cap > 0 && over > 0) add("${name.lowercase()} over ${over}×")
                }
            }.sortedBy { it.length }
            addAll(extras)
        }.joinToString(" · ")

        return SectionSummaries(energy, nutrition, body, habits)
    }

    // ---- helpers ----

    private fun take(id: String, ok: Boolean, text: String, card: String, importance: Float, distance: Float) =
        Takeaway(
            id, if (ok) TakeawayStatus.OK else TakeawayStatus.ATTENTION, text, card,
            // An on-track fact still ranks by how much it matters, just below the problems.
            importance * distance.coerceIn(0.1f, 1f)
        )

    private fun days(n: Int) = if (n == 1) "1 day" else "$n days"

    /** "vitamin d" → "vitamin D", "vitamin b12" → "vitamin B12". */
    private fun nutrientCase(name: String): String =
        if (name.startsWith("vitamin ")) "vitamin " + name.removePrefix("vitamin ").uppercase() else name

    /** "weekends" / "weekdays" when at least 75% of [dates] fall on one of them. */
    private fun weekdayPattern(dates: List<LocalDate>): String? {
        if (dates.isEmpty()) return null
        val weekend = dates.count { it.dayOfWeek == DayOfWeek.SATURDAY || it.dayOfWeek == DayOfWeek.SUNDAY }
        return when {
            weekend >= dates.size * 0.75f -> "weekends"
            dates.size - weekend >= dates.size * 0.75f && weekend < dates.size -> "weekdays"
            else -> null
        }
    }

    private fun percent(now: Float?, before: Float?): Int? {
        if (now == null || before == null || before <= 0f) return null
        return ((now - before) / before * 100f).roundToInt()
    }

    /** The largest move (≥ 15%) in calories, steps or water vs the period before. */
    private fun biggestChange(input: TakeawayInput): Takeaway? {
        fun avg(days: List<TakeawayDay>, f: (TakeawayDay) -> Float?) =
            days.mapNotNull(f).filter { it > 0f }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        val candidates = listOfNotNull(
            percent(avg(input.days) { it.kcal }, avg(input.previous) { it.kcal })?.let { Triple("kcal", it, "calories") },
            percent(avg(input.days) { it.steps?.toFloat() }, avg(input.previous) { it.steps?.toFloat() })?.let { Triple("steps", it, "activity") },
            percent(avg(input.days) { it.waterMl }, avg(input.previous) { it.waterMl })?.let { Triple("water", it, "water") }
        ).filter { abs(it.second) >= 15 }
        val (what, p, card) = candidates.maxByOrNull { abs(it.second) } ?: return null
        val more = p > 0
        val text = when (what) {
            "kcal" -> "You ate ${abs(p)}% ${if (more) "more" else "less"} than ${input.previousLabel}."
            "steps" -> "You walked ${abs(p)}% ${if (more) "more" else "less"} than ${input.previousLabel}."
            else -> "You drank ${abs(p)}% ${if (more) "more" else "less"} water than ${input.previousLabel}."
        }
        val ok = when (what) {
            "kcal" -> when (input.goal) {
                FitnessGoal.LOSE_FAT, FitnessGoal.RECOMP -> !more
                FitnessGoal.BUILD_MUSCLE -> more
                FitnessGoal.MAINTAIN -> true
            }
            else -> more
        }
        return take("change_$what", ok, text, card, 0.6f, abs(p) / 50f)
    }
}
