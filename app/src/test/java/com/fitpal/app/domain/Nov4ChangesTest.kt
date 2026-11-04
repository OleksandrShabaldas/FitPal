package com.fitpal.app.domain

import com.fitpal.app.domain.model.MealTypes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The 4 Nov review (docs/redesign/17): what counts as "something logged" for the AI overviews (#3). */
class Nov4ChangesTest {

    @Test
    fun plainWaterIsPlainWater() {
        listOf("Water", "Tap water", "Sparkling water", "Mineral water", "Glass of water", "Rajec", "Voda", "Вода")
            .forEach { assertTrue(it, Drinks.isPlainWater(it)) }
    }

    @Test
    fun otherDrinksAndFoodsAreNot() {
        listOf(
            "Black coffee", "Espresso", "Green tea", "Diet Coke", "Coconut water", "Vitamin water",
            "Flavoured water", "Watermelon", "Tuna in water", "Orange juice", "Fried pirozhki"
        ).forEach { assertFalse(it, Drinks.isPlainWater(it)) }
    }

    @Test
    fun aDayWithOnlyWaterHasNothingToReview() {
        // The water tracker's glasses, and a glass of water that came in with a photo.
        assertFalse(OverviewRule.hasSomething(listOf(MealTypes.WATER to "Water", MealTypes.WATER to "Water")))
        assertFalse(OverviewRule.hasSomething(listOf(MealTypes.DINNER to "Water")))
        assertFalse(OverviewRule.hasSomething(emptyList()))
    }

    @Test
    fun anythingElseCounts() {
        assertTrue(OverviewRule.hasSomething(listOf(MealTypes.WATER to "Water", MealTypes.BREAKFAST to "Black coffee")))
        assertTrue(OverviewRule.hasSomething(listOf(MealTypes.SNACK to "Diet Coke")))
        assertTrue(OverviewRule.hasSomething(listOf(MealTypes.DINNER to "Fried pirozhki")))
    }
}
