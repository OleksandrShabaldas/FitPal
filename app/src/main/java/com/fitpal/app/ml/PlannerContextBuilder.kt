package com.fitpal.app.ml

import com.fitpal.app.data.repository.AiReviewRepository
import com.fitpal.app.data.repository.ContextNoteRepository
import com.fitpal.app.data.repository.ExerciseRepository
import com.fitpal.app.data.repository.GalleryRepository
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.data.repository.StepRepository
import com.fitpal.app.data.repository.WeightRepository
import com.fitpal.app.domain.BmrCalculator
import com.fitpal.app.domain.model.FoodProfile
import com.fitpal.app.domain.model.PlanRequest
import com.fitpal.app.ui.component.fastingClockLabel
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.Currency
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * What the meal planner's AI is told (docs/redesign/10) — assembled in code and marked as facts, like
 * `ReviewGenerator`: both forms, the day's targets and what's left (the Home ring's maths), the fitness
 * goal, food limits, the fasting window, what the person likes and eats, what they've been short on,
 * and the coach's recent advice with their answers.
 */
@Singleton
class PlannerContextBuilder @Inject constructor(
    private val settings: SettingsRepository,
    private val meals: MealRepository,
    private val weights: WeightRepository,
    private val steps: StepRepository,
    private val exercise: ExerciseRepository,
    private val notes: ContextNoteRepository,
    private val reviews: AiReviewRepository,
    private val gallery: GalleryRepository
) {
    /** The facts block for planning [day] under [request]. */
    suspend fun build(request: PlanRequest, day: LocalDate): String = buildString {
        val profile = settings.foodProfile.value
        appendLine("FOOD PROFILE (fact):")
        appendLine(profileLines(profile))
        appendLine()
        appendLine("THIS REQUEST (fact):")
        appendLine(requestLines(request, profile))
        appendLine()
        appendLine(targetsBlock(day))
        appendLine()
        appendLine(rulesBlock())
        appendLine()
        appendLine(likesBlock())
        needsBlock()?.let { appendLine(); appendLine(it) }
        memoryBlock()?.let { appendLine(); appendLine(it) }
    }.trim()

    private fun currency(): String = runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrDefault("EUR")

    private fun profileLines(p: FoodProfile): String = buildList {
        add("- Diet: " + if (p.diet == FoodProfile.DIET_OTHER) p.dietOther.ifBlank { "other" } else p.diet)
        add("- Allergies and intolerances (NEVER use): " + p.allAllergies.joinToString(", ").ifEmpty { "none" })
        if (p.wontEat.isNotEmpty()) add("- Won't eat: " + p.wontEat.joinToString(", "))
        if (p.love.isNotEmpty()) add("- Loves: " + p.love.joinToString(", "))
        if (p.cuisines.isNotEmpty()) add("- Favourite cuisines: " + p.cuisines.joinToString(", "))
        add("- Cooking skill: ${p.skill}")
        add("- Kitchen: " + p.kitchen.joinToString(", ").ifEmpty { "basic" })
        add("- Usually cooks for: ${p.cookingFor}")
        p.budget?.let { add("- Typical budget: %.2f %s per %s".format(it, currency(), p.budgetPer)) }
        if (p.notes.isNotBlank()) add("- Notes: ${p.notes.trim()}")
    }.joinToString("\n")

    private fun requestLines(r: PlanRequest, p: FoodProfile): String = buildList {
        add("- Plan: ${r.scope.label}")
        if (r.have.isNotEmpty()) add("- Has: " + r.have.joinToString(", "))
        add(if (r.onlyHave) "- Use ONLY what they have (plus salt, pepper, oil, water)" else "- Can buy more")
        if (!r.onlyHave) (r.budget ?: p.budget)?.let { add("- Budget: %.2f %s per %s".format(it, currency(), if (r.budget != null) r.budgetPer else p.budgetPer)) }
        val feel = (r.feelPills + listOfNotNull(r.feel.trim().takeIf { it.isNotEmpty() })).joinToString(", ")
        if (feel.isNotEmpty()) add("- Feels like: $feel")
        add("- Goal of the meal: ${r.goal.prompt}")
        add("- Cuisine: " + (r.cuisine ?: "any"))
        add("- Cooking time: at most ${r.timeMinutes} minutes")
        add("- Servings the recipe makes: ${r.servings} (nutrition per portion one person eats)")
    }.joinToString("\n")

    /** The day's targets and — for today — what's left, with the same maths as the Home ring. */
    private suspend fun targetsBlock(day: LocalDate): String {
        val profile = settings.userProfile.value
        val kg = weights.getLatest().first()?.weightKg ?: return "TARGETS: unknown (no weight logged yet)."
        val t = BmrCalculator.dailyTargets(profile, kg, settings.dailyCalorieGoal.value, settings.macroSelection.value)
        val iso = day.toString()
        val isToday = day == LocalDate.now()
        val activity = if (isToday) (steps.getCaloriesBurned(iso).first() + exercise.getTotalBurnedForDate(iso).first()).roundToInt() else 0
        val budget = t.calories + activity
        return buildString {
            appendLine("TARGETS for ${if (isToday) "today" else day.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }} (fact):")
            appendLine("- Fitness goal: ${profile.fitnessGoal.label}")
            appendLine("- Calories ${t.calories}${if (activity > 0) " + $activity from activity = $budget" else ""} kcal; protein ${t.proteinG} g; fat ${t.fatG} g; carbs ${t.carbsG} g; fibre ${t.fiberG} g")
            if (isToday) {
                val eaten = meals.getDailyNutrition(iso).first()
                val logged = meals.getItemsWithTypeForDate(iso).first().filter { it.mealType != com.fitpal.app.domain.model.MealTypes.WATER }
                appendLine("- Eaten so far today: ${eaten.calories.roundToInt()} kcal, protein ${eaten.protein.roundToInt()} g" +
                    (if (logged.isNotEmpty()) " (" + logged.joinToString(", ") { it.item.name } + ")" else ""))
                appendLine("- LEFT today: ${(budget - eaten.calories).roundToInt().coerceAtLeast(0)} kcal, protein ${(t.proteinG - eaten.protein).roundToInt().coerceAtLeast(0)} g, fibre ${(t.fiberG - eaten.fiber).roundToInt().coerceAtLeast(0)} g")
                val loggedTypes = logged.map { it.mealType }.toSet()
                if (loggedTypes.isNotEmpty()) appendLine("- Already logged today: " + loggedTypes.joinToString(", ") + " (don't plan those again)")
            }
        }.trim()
    }

    private fun rulesBlock(): String = buildList {
        add("THEIR RULES (fact):")
        val wake = settings.wakeMinutes.value
        val bed = settings.bedMinutes.value
        add("- Day: wakes ${fastingClockLabel(wake)}, bed ${fastingClockLabel(bed)}")
        val f = settings.fastingSchedule.value
        if (f.enabled) add("- Fasting: eats only between ${fastingClockLabel(f.eatStartMin)} and ${fastingClockLabel(f.eatEndMin)}; every meal must fit inside")
        val limits = settings.dietaryRules.value.filter { it.enabled }
        if (limits.isNotEmpty()) add("- Daily caps: " + limits.joinToString(", ") { "${it.kind.title} ${it.dailyLimitKcal} kcal" })
        val c = settings.caffeineSettings.value
        if (c.enabled) add("- Caffeine: under ${c.dailyLimitMg} mg a day, little after mid-afternoon")
    }.joinToString("\n")

    /** What they actually eat — the planner's best guide to what they'll like. */
    private suspend fun likesBlock(): String {
        val today = LocalDate.now()
        val rows = meals.caffeineRowsInRange(today.minusDays(60).toString(), today.toString()).first()
        val counts = rows.groupingBy { it.name.trim() }.eachCount().entries.sortedByDescending { it.value }
        val favourites = counts.filter { it.value >= 3 }.take(10).joinToString(", ") { "${it.key} (${it.value}×)" }
        val recent = rows.filter { it.date >= today.minusDays(7).toString() }.map { it.name.trim() }.distinct().take(15)
        val saved = runCatching { gallery.getMostUsed(10).first().map { it.name } }.getOrDefault(emptyList())
        return buildList {
            add("WHAT THEY EAT (fact):")
            if (favourites.isNotEmpty()) add("- Often: $favourites")
            if (recent.isNotEmpty()) add("- This past week: " + recent.joinToString(", ") + " (vary from these)")
            if (saved.isNotEmpty()) add("- Saved favourites: " + saved.joinToString(", "))
            if (size == 1) add("- Not much logged yet")
        }.joinToString("\n")
    }

    /** What they've been short on over two weeks — the same rules as the Analytics takeaways. */
    private suspend fun needsBlock(): String? {
        val today = LocalDate.now()
        val from = today.minusDays(14).toString()
        val rows = meals.getDailyNutritionRange(from, today.toString()).first().filter { it.calories > 0f }
        if (rows.size < 3) return null
        val low = com.fitpal.app.ui.screen.analytics.lowMicros(meals.getMicrosForRange(from, today.toString()).first(), rows.size)
        val profile = settings.userProfile.value
        val kg = weights.getLatest().first()?.weightKg
        val proteinTarget = kg?.let { BmrCalculator.dailyTargets(profile, it, settings.dailyCalorieGoal.value, settings.macroSelection.value).proteinG } ?: 0
        val short = if (proteinTarget > 0) rows.count { it.protein < proteinTarget } else 0
        val lines = buildList {
            if (low.isNotEmpty()) add("- Low on: " + low.take(4).joinToString(", ") { it.lowercase() } + " (favour foods rich in these)")
            if (short >= rows.size / 2 && short > 0) add("- Protein short on $short of ${rows.size} days")
        }
        return if (lines.isEmpty()) null else (listOf("WHAT THEY NEED (fact, last 14 days):") + lines).joinToString("\n")
    }

    /** The coach's recent advice and what they answered — so plans follow through on it. */
    private suspend fun memoryBlock(): String? {
        val advice = runCatching { reviews.recent(3) }.getOrDefault(emptyList()).mapNotNull { r ->
            val p = ReviewText.parse(r.text)
            listOfNotNull(p.focus?.let { "focus: $it" }, p.watch?.let { "watch: $it" }).joinToString("; ").takeIf { it.isNotEmpty() }
        }
        val answers = runCatching { notes.getRecentAnswers(LocalDate.now().minusDays(30).toString(), 5) }.getOrDefault(emptyList())
            .filter { it.answer != "skipped" }.map { "\"${it.question}\" → \"${it.answer}\"" }
        if (advice.isEmpty() && answers.isEmpty()) return null
        return (listOf("COACH'S RECENT ADVICE (follow it where it fits):") + advice.map { "- $it" } + answers.map { "- They said: $it" })
            .joinToString("\n")
    }
}
