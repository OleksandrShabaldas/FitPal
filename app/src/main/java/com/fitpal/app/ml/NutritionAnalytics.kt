package com.fitpal.app.ml

import com.fitpal.app.data.local.dao.DailyNutritionRow
import com.fitpal.app.data.local.dao.LoggedFoodRow
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.data.repository.WeightRepository
import com.fitpal.app.domain.BmrCalculator
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Averaged macros over a set of days. */
data class MacroAverages(
    val calories: Float,
    val protein: Float,
    val fat: Float,
    val carbs: Float,
    val fiber: Float
)

/** Which way a metric is moving over the trend window. Deliberately value-neutral —
 *  whether "rising calories" is good depends on the goal, which the AI knows. */
enum class TrendDirection { RISING, FALLING, STABLE }

/** Today's macros as a fraction above/below the recent average (e.g. +0.16 = 16% over), plus flags. */
data class TodayComparison(
    val caloriesPct: Float,
    val proteinPct: Float,
    val fatPct: Float,
    val carbsPct: Float,
    val fiberPct: Float,
    val flags: List<String>
)

data class WeightTrend(val kgPerWeek: Float, val direction: TrendDirection)

/** Days meeting each target out of the days that had data in the last week. */
data class TargetHitRates(
    val days: Int,
    val caloriesUnderOrAt: Int,
    val proteinMet: Int,
    val fiberMet: Int
)

data class NutritionTargets(
    val calories: Int,
    val protein: Int,
    val fat: Int,
    val carbs: Int,
    val fiber: Int
)

data class TopFood(val name: String, val count: Int, val avgCalories: Int)
data class TopMeal(val date: String, val label: String, val totalCalories: Int)

/**
 * The pre-computed pattern/trend picture handed to the AI coach so it can reason about whether a
 * day/period is *unusual* and whether it *matters* — questions the raw daily totals can't answer.
 *
 * Every field degrades gracefully: too little history ⇒ nulls / empty, and [toPromptBlock] simply
 * omits those lines. Nothing here is shown to the user directly; it only shapes the prompt and the
 * context-question triggers.
 */
data class NutritionBrief(
    val isDaily: Boolean,
    val daysWithData: Int,
    val today: MacroAverages?,
    val avg7d: MacroAverages?,
    val avg30d: MacroAverages?,
    val todayVsAvg: TodayComparison?,
    val trends: Map<String, TrendDirection>,
    val weightTrend: WeightTrend?,
    val weekdayAvgKcal: Float?,
    val weekendAvgKcal: Float?,
    val targetHitRates: TargetHitRates?,
    val proteinByMeal: Map<String, Float>,
    val topFoods: List<TopFood>,
    val topMeals: List<TopMeal>,
    val calorieStreak: Int,
    val todayOutlier: Boolean,
    val consecutiveUnloggedBeforeToday: Int,
    val targets: NutritionTargets?
) {
    /** True when there's essentially nothing to reason about (brand-new user). */
    val isSparse: Boolean get() = daysWithData < 3

    /** Render as a compact text block for the review / question prompts. Omits empty sections. */
    fun toPromptBlock(): String {
        if (daysWithData == 0) return ""
        val sb = StringBuilder()
        sb.appendLine("NUTRITION ANALYTICS (use to judge if this is unusual and whether it matters — don't just repeat these numbers):")

        avg7d?.let { sb.appendLine("• 7-day averages: ${it.macroLine()}") }
        avg30d?.let { sb.appendLine("• 30-day averages: ${it.macroLine()}") }

        todayVsAvg?.let { c ->
            val parts = mutableListOf(
                "Calories ${pct(c.caloriesPct)}",
                "Protein ${pct(c.proteinPct)}",
                "Fibre ${pct(c.fiberPct)}"
            )
            val flag = if (c.flags.isNotEmpty()) " [${c.flags.joinToString("; ")}]" else ""
            sb.appendLine("• Today vs 7-day avg: ${parts.joinToString(", ")}$flag")
        }
        if (isDaily && todayOutlier) {
            sb.appendLine("• Today is a statistical OUTLIER vs the last 14 days (well outside the usual range).")
        }

        if (trends.isNotEmpty()) {
            sb.appendLine("• 14-day trends: " + trends.entries.joinToString(", ") { "${it.key} ${it.value}" })
        }

        weightTrend?.let {
            val sign = if (it.kgPerWeek >= 0) "+" else ""
            sb.appendLine("• Weight trend: $sign${"%.2f".format(it.kgPerWeek)} kg/week (${it.direction})")
        }

        if (weekdayAvgKcal != null && weekendAvgKcal != null) {
            sb.appendLine("• Weekday avg: ${weekdayAvgKcal.roundToInt()} kcal | Weekend avg: ${weekendAvgKcal.roundToInt()} kcal")
        }

        targetHitRates?.let {
            sb.appendLine("• Last ${it.days} days met: calories at/under target ${it.caloriesUnderOrAt}/${it.days}, protein ${it.proteinMet}/${it.days}, fibre ${it.fiberMet}/${it.days}")
        }

        if (proteinByMeal.isNotEmpty()) {
            sb.appendLine("• Avg protein by meal: " + proteinByMeal.entries.joinToString(", ") { "${it.key.replaceFirstChar { c -> c.uppercase() }} ${it.value.roundToInt()}g" })
        }

        if (topFoods.isNotEmpty()) {
            sb.appendLine("• Most-logged foods (30d): " + topFoods.joinToString(", ") { "${it.name} (${it.count}×)" })
        }

        if (topMeals.isNotEmpty()) {
            sb.appendLine("• Highest-calorie meals: " + topMeals.joinToString(", ") { "${it.label} ${it.totalCalories}kcal" })
        }

        if (calorieStreak >= 2) {
            sb.appendLine("• Streak: $calorieStreak days in a row at/under the calorie target.")
        }

        return sb.toString().trim()
    }

    private fun MacroAverages.macroLine(): String =
        "${calories.roundToInt()} kcal, P${protein.roundToInt()}g, F${fat.roundToInt()}g, C${carbs.roundToInt()}g, Fiber ${fiber.roundToInt()}g"

    private fun pct(fraction: Float): String {
        val p = (fraction * 100).roundToInt()
        return if (p >= 0) "+$p%" else "$p%"
    }
}

/**
 * Computes a [NutritionBrief] from existing logged data — pure Kotlin math over the repositories,
 * no new persistence. Shared by [ReviewGenerator] (to enrich the review prompt) and
 * [ContextQuestionGenerator] (to decide whether to ask a check-in question).
 */
@Singleton
class NutritionAnalytics @Inject constructor(
    private val mealRepository: MealRepository,
    private val weightRepository: WeightRepository,
    private val settingsRepository: SettingsRepository
) {
    /**
     * Build the brief anchored at [referenceDate] (ISO). Looks back 30 days. When [isDaily] is true,
     * "today" = [referenceDate] and the averages exclude it (so "today vs usual" is a fair compare);
     * otherwise averages include the whole window and the today-specific fields are null/false.
     */
    suspend fun computeBrief(referenceDate: String, isDaily: Boolean): NutritionBrief {
        val ref = runCatching { LocalDate.parse(referenceDate) }.getOrDefault(LocalDate.now())
        val windowStart = ref.minusDays(29)
        val from = windowStart.toString()
        val to = ref.toString()

        val rows = mealRepository.getDailyNutritionRange(from, to).first()
            .sortedBy { it.date }
        val byDate = rows.associateBy { it.date }
        val today = if (isDaily) byDate[referenceDate]?.toAverages() else null

        // Comparison set for averages: prior days only for the daily "today vs usual" framing.
        val comparisonRows = if (isDaily) rows.filter { it.date < referenceDate } else rows
        val last7 = comparisonRows.filter { LocalDate.parse(it.date) >= ref.minusDays(7) }
        val avg7d = averagesOf(last7, minDays = 3)
        val avg30d = averagesOf(comparisonRows, minDays = 7)

        val targets = computeTargets()

        val todayVsAvg = if (isDaily && today != null && avg7d != null)
            compareToday(today, avg7d, targets) else null

        val trends = computeTrends(rows.filter { LocalDate.parse(it.date) >= ref.minusDays(14) })

        val weightTrend = computeWeightTrend(ref)

        val (weekdayAvg, weekendAvg) = weekdayWeekendSplit(comparisonRows)

        val hitRates = targets?.let { targetHitRates(last7, it) }

        val foods = mealRepository.getLoggedFoodsInRange(from, to)
        val topFoods = topFoods(foods)
        val topMeals = topMeals(foods)

        val proteinByMeal = proteinByMeal(from, to)

        val streak = calorieStreak(byDate, ref, targets)
        val outlier = if (isDaily && today != null) isOutlier(today.calories, rows, ref) else false
        val unlogged = consecutiveUnloggedBefore(byDate, ref)

        return NutritionBrief(
            isDaily = isDaily,
            daysWithData = rows.size,
            today = today,
            avg7d = avg7d,
            avg30d = avg30d,
            todayVsAvg = todayVsAvg,
            trends = trends,
            weightTrend = weightTrend,
            weekdayAvgKcal = weekdayAvg,
            weekendAvgKcal = weekendAvg,
            targetHitRates = hitRates,
            proteinByMeal = proteinByMeal,
            topFoods = topFoods,
            topMeals = topMeals,
            calorieStreak = streak,
            todayOutlier = outlier,
            consecutiveUnloggedBeforeToday = unlogged,
            targets = targets
        )
    }

    // ---- helpers ----

    private fun DailyNutritionRow.toAverages() =
        MacroAverages(calories, protein, fat, carbs, fiber)

    private fun averagesOf(rows: List<DailyNutritionRow>, minDays: Int): MacroAverages? {
        if (rows.size < minDays) return null
        val n = rows.size.toFloat()
        return MacroAverages(
            calories = rows.sumOf { it.calories.toDouble() }.toFloat() / n,
            protein = rows.sumOf { it.protein.toDouble() }.toFloat() / n,
            fat = rows.sumOf { it.fat.toDouble() }.toFloat() / n,
            carbs = rows.sumOf { it.carbs.toDouble() }.toFloat() / n,
            fiber = rows.sumOf { it.fiber.toDouble() }.toFloat() / n
        )
    }

    private suspend fun computeTargets(): NutritionTargets? {
        val profile = settingsRepository.userProfile.value
        val weight = weightRepository.getLatest().first() ?: return null
        val t = BmrCalculator.dailyTargets(
            profile, weight.weightKg,
            settingsRepository.dailyCalorieGoal.value,
            settingsRepository.macroSelection.value
        )
        return NutritionTargets(t.calories, t.proteinG, t.fatG, t.carbsG, t.fiberG)
    }

    private fun compareToday(today: MacroAverages, avg: MacroAverages, targets: NutritionTargets?): TodayComparison {
        fun frac(now: Float, base: Float) = if (base > 0f) (now - base) / base else 0f
        val flags = mutableListOf<String>()
        val calFrac = frac(today.calories, avg.calories)
        val proFrac = frac(today.protein, avg.protein)
        if (calFrac > 0.4f) flags += "much higher than usual"
        if (calFrac < -0.4f) flags += "much lower than usual"
        targets?.let {
            if (it.protein > 0 && today.protein < it.protein * 0.5f) flags += "protein under half the goal"
            val totalCal = today.calories
            if (totalCal > 0 && (today.fat * 9f) / totalCal > 0.45f) flags += "fat-heavy day"
        }
        return TodayComparison(
            caloriesPct = calFrac,
            proteinPct = proFrac,
            fatPct = frac(today.fat, avg.fat),
            carbsPct = frac(today.carbs, avg.carbs),
            fiberPct = frac(today.fiber, avg.fiber),
            flags = flags
        )
    }

    /** Value-neutral trend per macro via least-squares slope over epoch-day, thresholded per macro. */
    private fun computeTrends(rows: List<DailyNutritionRow>): Map<String, TrendDirection> {
        if (rows.size < 5) return emptyMap()
        fun dir(values: List<Pair<Long, Float>>, threshold: Float): TrendDirection {
            val slope = slope(values) ?: return TrendDirection.STABLE
            return when {
                slope > threshold -> TrendDirection.RISING
                slope < -threshold -> TrendDirection.FALLING
                else -> TrendDirection.STABLE
            }
        }
        fun series(pick: (DailyNutritionRow) -> Float) =
            rows.map { LocalDate.parse(it.date).toEpochDay() to pick(it) }
        return linkedMapOf(
            "Calories" to dir(series { it.calories }, 25f),   // ~25 kcal/day
            "Protein" to dir(series { it.protein }, 2f),      // ~2 g/day
            "Fibre" to dir(series { it.fiber }, 1f)           // ~1 g/day
        )
    }

    /** Least-squares slope (y per unit x) or null if x has no spread. */
    private fun slope(points: List<Pair<Long, Float>>): Float? {
        if (points.size < 2) return null
        val n = points.size
        val meanX = points.sumOf { it.first.toDouble() } / n
        val meanY = points.sumOf { it.second.toDouble() } / n
        var num = 0.0
        var den = 0.0
        points.forEach { (x, y) ->
            val dx = x - meanX
            num += dx * (y - meanY)
            den += dx * dx
        }
        return if (den == 0.0) null else (num / den).toFloat()
    }

    private suspend fun computeWeightTrend(ref: LocalDate): WeightTrend? {
        val entries = weightRepository.getAll().first()
            .filter {
                val d = runCatching { LocalDate.parse(it.date) }.getOrNull()
                d != null && !d.isAfter(ref) && d.isAfter(ref.minusDays(30))
            }
            .sortedBy { it.date }
        if (entries.size < 3) return null
        val span = ChronoUnit.DAYS.between(
            LocalDate.parse(entries.first().date), LocalDate.parse(entries.last().date)
        )
        if (span < 7) return null
        val points = entries.map { LocalDate.parse(it.date).toEpochDay() to it.weightKg }
        val perDay = slope(points) ?: return null
        val perWeek = perDay * 7f
        val direction = when {
            perWeek > 0.05f -> TrendDirection.RISING
            perWeek < -0.05f -> TrendDirection.FALLING
            else -> TrendDirection.STABLE
        }
        return WeightTrend(perWeek, direction)
    }

    private fun weekdayWeekendSplit(rows: List<DailyNutritionRow>): Pair<Float?, Float?> {
        if (rows.size < 4) return null to null
        val (weekend, weekday) = rows.partition {
            val dow = LocalDate.parse(it.date).dayOfWeek
            dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY
        }
        val weekdayAvg = weekday.takeIf { it.isNotEmpty() }?.let { l -> l.sumOf { it.calories.toDouble() }.toFloat() / l.size }
        val weekendAvg = weekend.takeIf { it.isNotEmpty() }?.let { l -> l.sumOf { it.calories.toDouble() }.toFloat() / l.size }
        // Only meaningful when both sides exist.
        return if (weekdayAvg != null && weekendAvg != null) weekdayAvg to weekendAvg else null to null
    }

    private fun targetHitRates(rows: List<DailyNutritionRow>, t: NutritionTargets): TargetHitRates? {
        if (rows.isEmpty()) return null
        return TargetHitRates(
            days = rows.size,
            caloriesUnderOrAt = rows.count { t.calories > 0 && it.calories <= t.calories },
            proteinMet = rows.count { t.protein > 0 && it.protein >= t.protein },
            fiberMet = rows.count { t.fiber > 0 && it.fiber >= t.fiber }
        )
    }

    private fun topFoods(foods: List<LoggedFoodRow>): List<TopFood> =
        foods.filter { it.mealType != "water" && it.calories > 0 }
            .groupBy { it.name.trim().lowercase() }
            .map { (_, items) ->
                TopFood(
                    name = items.first().name,
                    count = items.size,
                    avgCalories = (items.sumOf { it.calories.toDouble() } / items.size).roundToInt()
                )
            }
            .filter { it.count >= 3 }
            .sortedByDescending { it.count }
            .take(5)

    private fun topMeals(foods: List<LoggedFoodRow>): List<TopMeal> =
        foods.filter { it.mealType != "water" }
            .groupBy { it.mealLogId }
            .map { (_, items) ->
                val total = items.sumOf { it.calories.toDouble() }.roundToInt()
                val first = items.first()
                val names = items.take(2).joinToString(" + ") { it.name }
                val more = if (items.size > 2) " +${items.size - 2}" else ""
                TopMeal(
                    date = first.date,
                    label = "${first.mealType.replaceFirstChar { it.uppercase() }} ($names$more)",
                    totalCalories = total
                )
            }
            .filter { it.totalCalories >= 400 }
            .sortedByDescending { it.totalCalories }
            .take(5)

    private suspend fun proteinByMeal(from: String, to: String): Map<String, Float> =
        mealRepository.getProteinByMealTypeInRange(from, to)
            .filter { it.days > 0 }
            .associate { it.mealType to (it.protein / it.days) }

    private fun calorieStreak(byDate: Map<String, DailyNutritionRow>, ref: LocalDate, targets: NutritionTargets?): Int {
        val target = targets?.calories ?: return 0
        if (target <= 0) return 0
        var streak = 0
        var day = ref
        while (true) {
            val row = byDate[day.toString()] ?: break
            if (row.calories <= target) streak++ else break
            day = day.minusDays(1)
        }
        return streak
    }

    private fun isOutlier(todayCalories: Float, rows: List<DailyNutritionRow>, ref: LocalDate): Boolean {
        val window = rows.filter {
            val d = LocalDate.parse(it.date)
            d.isBefore(ref) && d >= ref.minusDays(14)
        }
        if (window.size < 5) return false
        val mean = window.sumOf { it.calories.toDouble() } / window.size
        val variance = window.sumOf { (it.calories - mean) * (it.calories - mean) } / window.size
        val sd = sqrt(variance)
        if (sd == 0.0) return false
        return abs(todayCalories - mean) > 1.5 * sd
    }

    private fun consecutiveUnloggedBefore(byDate: Map<String, DailyNutritionRow>, ref: LocalDate): Int {
        var count = 0
        var day = ref.minusDays(1)
        var guard = 0
        while (guard < 30) {
            if (byDate.containsKey(day.toString())) break
            count++
            day = day.minusDays(1)
            guard++
        }
        return count
    }
}
