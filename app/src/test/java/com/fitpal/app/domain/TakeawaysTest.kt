package com.fitpal.app.domain

import com.fitpal.app.domain.model.FitnessGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TakeawaysTest {

    private val monday = LocalDate.of(2026, 10, 12)

    private fun week(make: (Int, LocalDate) -> TakeawayDay) = (0 until 7).map { make(it, monday.plusDays(it.toLong())) }

    private fun input(
        days: List<TakeawayDay>,
        previous: List<TakeawayDay> = emptyList(),
        goal: FitnessGoal = FitnessGoal.MAINTAIN,
        protein: Float = 0f,
        fiber: Float = 0f,
        caffeine: Int? = null,
        limits: Map<String, Int> = emptyMap(),
        fastingOn: Boolean = false,
        weight: Float? = null,
        onTrack: Boolean? = null,
        waterGoal: Int = 0,
        stepGoal: Int = 0
    ) = TakeawayInput(
        days = days, previous = previous, periodPhrase = "this week", previousLabel = "last week",
        calorieGoal = 2000, proteinTarget = protein, fatTarget = 0f, carbTarget = 0f, fiberTarget = fiber,
        goal = goal, waterGoalMl = waterGoal, stepGoal = stepGoal, caffeineLimitMg = caffeine, limits = limits,
        fastingOn = fastingOn, fastingStreak = 4, weightChangeKg = weight, weightOnTrack = onTrack, lowNutrients = emptyList()
    )

    private fun byId(i: TakeawayInput, id: String) = Takeaways.all(i).firstOrNull { it.id == id }

    @Test fun energyUnderForAFatLossGoalIsOnTrack() {
        val t = byId(input(week { _, d -> TakeawayDay(d, kcal = 1700f) }, goal = FitnessGoal.LOSE_FAT), "energy")
        assertNotNull(t)
        assertEquals(TakeawayStatus.OK, t!!.status)
        assertTrue(t.text.startsWith("300 kcal a day under your goal."))
        assertEquals("balance", t.cardKey)
    }

    @Test fun energyOverWhenMaintainingNeedsAttention() {
        val t = byId(input(week { _, d -> TakeawayDay(d, kcal = 2400f) }), "energy")!!
        assertEquals(TakeawayStatus.ATTENTION, t.status)
    }

    @Test fun burnedCaloriesWidenTheBudget() {
        val t = byId(input(week { _, d -> TakeawayDay(d, kcal = 2300f, burned = 300f) }), "energy")!!
        assertEquals("Right around your calorie goal on average.", t.text)
    }

    @Test fun energyNeedsTwoLoggedDays() {
        val days = week { i, d -> TakeawayDay(d, kcal = if (i == 0) 2500f else 0f) }
        assertNull(byId(input(days), "energy"))
    }

    @Test fun proteinWithAWeekendPattern() {
        val days = week { i, d -> TakeawayDay(d, kcal = 2000f, protein = if (i >= 5) 60f else 130f) }
        val t = byId(input(days, protein = 120f), "protein")!!
        assertEquals("Protein reached its target on 5 of 7 days, missing mostly on weekends.", t.text)
        assertEquals(TakeawayStatus.OK, t.status)
    }

    @Test fun loggingCountsMissingDays() {
        val days = week { i, d -> TakeawayDay(d, kcal = if (i < 4) 2000f else 0f) }
        val t = byId(input(days), "logging")!!
        assertEquals("3 days not logged — the averages may be off.", t.text)
        assertEquals(TakeawayStatus.ATTENTION, t.status)
    }

    @Test fun fastingHeldWithStreak() {
        val days = week { i, d -> TakeawayDay(d, kcal = 2000f, fastKept = i != 2) }
        assertEquals("Fasting held 6 of 7 days. 4 in a row now.", byId(input(days, fastingOn = true), "fasting")!!.text)
        assertNull(byId(input(days, fastingOn = false), "fasting"))
    }

    @Test fun caffeineAndLimitsOnlyWhenOver() {
        val days = week { i, d -> TakeawayDay(d, kcal = 2000f, caffeineMg = if (i < 2) 450f else 100f, limitKcal = mapOf("Dessert" to if (i == 3) 400f else 50f)) }
        val i = input(days, caffeine = 400, limits = mapOf("Dessert" to 300))
        assertEquals("Over 400 mg of caffeine on 2 days.", byId(i, "caffeine")!!.text)
        assertEquals("Dessert over your cap on 1 day.", byId(i, "limit_Dessert")!!.text)
        assertNull(byId(input(days), "caffeine"))
    }

    @Test fun biggestChange() {
        val now = week { _, d -> TakeawayDay(d, kcal = 2000f, steps = 9000) }
        val before = week { _, d -> TakeawayDay(d.minusWeeks(1), kcal = 2000f, steps = 7000) }
        val t = byId(input(now, before), "change_steps")!!
        assertEquals("You walked 29% more than last week.", t.text)
        assertEquals("activity", t.cardKey)
    }

    @Test fun atMostTwoAttentionRowsAndAnOkOneFillsIn() {
        val days = week { i, d -> TakeawayDay(d, kcal = if (i < 4) 2600f else 0f, protein = 20f, fiber = 2f, steps = 3000) }
        val shown = Takeaways.compute(input(days, protein = 120f, fiber = 30f, weight = -0.4f, onTrack = true, stepGoal = 3000))
        assertEquals(3, shown.size)
        assertEquals(2, shown.count { it.status == TakeawayStatus.ATTENTION })
        assertTrue(shown.any { it.status == TakeawayStatus.OK })
    }

    @Test fun stableOrder() {
        val days = week { _, d -> TakeawayDay(d, kcal = 2000f, protein = 130f, fiber = 35f) }
        val i = input(days, protein = 120f, fiber = 30f)
        assertEquals(Takeaways.compute(i), Takeaways.compute(i))
    }

    @Test fun sectionSummaries() {
        val now = week { _, d -> TakeawayDay(d, kcal = 1800f, protein = 60f, steps = 7000, waterMl = 1800f, fastKept = true) }
        val before = week { _, d -> TakeawayDay(d.minusWeeks(1), kcal = 2000f) }
        val s = Takeaways.sections(input(now, before, protein = 120f, weight = -0.4f, waterGoal = 2000, fastingOn = true))
        assertEquals("1,800 kcal a day · ↓10%", s.energy)
        assertEquals("Protein short 7 of 7 days", s.nutrition)
        assertEquals("−0.4 kg · 7,000 steps · water 90%", s.body)
        assertEquals("Logged 7 of 7 · fasting 7/7", s.habits)
    }
}
