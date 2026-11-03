package com.fitpal.app.domain

import com.fitpal.app.domain.MealBudgetCheck.Totals
import com.fitpal.app.domain.MealBudgetCheck.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MealBudgetCheckTest {

    /** The dinner the coach called over budget: 856 kcal with 1,430 left before it. */
    @Test fun dinnerThatFitsIsSaidToFit() {
        assertEquals(Verdict.FITS, MealBudgetCheck.verdict(budget = 2430, eatenBefore = 1000f, meal = 856f))
        assertEquals("this meal FITS the day's budget, with 574 kcal left after it", MealBudgetCheck.checkLine(2430, 1000f, 856f))
    }

    @Test fun overByAHairIsAtBudget() {
        // 2,060 of 2,000 = 3% over.
        assertEquals(Verdict.AT_BUDGET, MealBudgetCheck.verdict(2000, 1500f, 560f))
        assertEquals(Verdict.OVER, MealBudgetCheck.verdict(2000, 1500f, 700f))
        assertEquals(Verdict.ALREADY_OVER, MealBudgetCheck.verdict(2000, 2100f, 300f))
        assertTrue(MealBudgetCheck.checkLine(2000, 1500f, 700f).contains("200 kcal OVER"))
    }

    @Test fun factsSayBeforeAndAfterAndCountActivity() {
        val block = MealBudgetCheck.factsBlock(
            baseKcal = 2100, activityKcal = 330,
            targets = MealBudgetCheck.MacroTargets(protein = 140, fat = 70, carbs = 250, fiber = 30),
            before = Totals(1000f, 61f, 38f, 112f, 14f),
            meal = Totals(856f, 41f, 29f, 104f, 9f)
        )
        assertTrue(block.contains("2,430 kcal = 2,100 kcal base + 330 kcal"))
        assertTrue(block.contains("BEFORE THIS MEAL: 1,000 kcal"))
        assertTrue(block.contains("THIS MEAL: 856 kcal"))
        assertTrue(block.contains("INCLUDING THIS MEAL: 1,856 of 2,430 kcal · protein 102 of 140 g"))
        assertTrue(block.contains("FITS the day's budget, with 574 kcal left"))
        // The old label that made the model count the meal twice is gone.
        assertFalse(block.contains("REMAINING"))
    }

    @Test fun noTargetsMeansNoCalorieVerdict() {
        val block = MealBudgetCheck.factsBlock(null, 200, null, Totals(0f, 0f, 0f, 0f, 0f), Totals(650f, 30f, 20f, 80f, 6f))
        assertTrue(block.contains("DAY BUDGET: not set yet"))
        assertTrue(block.contains("BEFORE THIS MEAL: nothing substantial"))
        assertTrue(block.contains("say nothing about calories"))
    }
}
