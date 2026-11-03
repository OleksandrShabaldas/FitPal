package com.fitpal.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure pieces behind the November fixes (docs/redesign/12). */
class NovemberFixesTest {

    // ---- #12 steps by hour ----

    @Test
    fun hoursScaleToTheShownCountExactly() {
        val raw = listOf(0L, 0L, 0L, 0L, 0L, 0L, 40L, 620L, 880L, 310L) + List(14) { 0L }
        val scaled = StepHours.scaleTo(raw, 2_000L)
        assertEquals(2_000L, scaled.sum())
        // The shape stays: empty hours stay empty, the busiest stays the busiest.
        assertTrue(scaled.take(6).all { it == 0L })
        assertEquals(8, StepHours.busiest(scaled))
        assertNull(StepHours.busiest(List(24) { 0L }))
    }

    // ---- #14 water and the database safety net ----

    @Test
    fun zeroCalorieDrinksAreRecognised() {
        listOf("Tap water", "Water", "Sparkling water", "Mineral water", "Black coffee", "Espresso", "Green tea",
            "Coke Zero", "Pepsi Max", "Diet Coke", "Ice cubes").forEach { assertTrue(it, Drinks.isZeroCalorie(it)) }
        listOf("Coconut water", "Tonic water", "Latte", "Coffee with milk", "Sweet tea", "Iced tea", "Orange juice",
            "Watermelon", "Ice cream", "Beer", "Chocolate", "Sugar-free chocolate").forEach { assertFalse(it, Drinks.isZeroCalorie(it)) }
    }

    @Test
    fun theSafetyNetOnlyTakesTheSameFood() {
        assertTrue(FoodNameMatch.sameFood("Banana", "Banana, raw"))
        assertTrue(FoodNameMatch.sameFood("Boiled potatoes", "Potatoes, boiled, cooked in skin, flesh, without salt"))
        assertTrue(FoodNameMatch.sameFood("Chicken breast", "Chicken breast, with skin"))
        assertFalse(FoodNameMatch.sameFood("Tap water", "Beverages, hot cocoa, prepared with tap water"))
        assertFalse(FoodNameMatch.sameFood("Water", "Watermelon"))
        assertFalse(FoodNameMatch.sameFood("Potato", "Potato chips, salted"))
        assertEquals("potatoes", FoodNameMatch.mainWord("Boiled potatoes")?.lowercase())
    }

    // ---- #1–#4 caffeine ----

    @Test
    fun theSleepLineIsFortyAndRedStartsAtSixtyFive() {
        assertEquals(40, CaffeineSettings.SLEEP_FRIENDLY_MG)
        assertEquals(65, CaffeineSettings.KEEPS_YOU_UP_MG)
    }

    @Test
    fun defaultDrinksAreTheSevenSortedLowToHigh() {
        assertEquals(
            listOf("Green tea", "Black tea", "Espresso", "Latte / cappuccino", "Red Bull", "Double espresso", "Monster"),
            Caffeine.DEFAULT_DRINKS.map { it.label }
        )
        assertEquals(63, Caffeine.DEFAULT_DRINKS.first { it.label == "Latte / cappuccino" }.mg)
        assertTrue(Caffeine.SUGGESTED_DRINKS.none { s -> Caffeine.DEFAULT_DRINKS.any { it.label == s.label } })
    }

    @Test
    fun thePlannerShowsTheStarredThreeToppedUp() {
        val drinks = Caffeine.DEFAULT_DRINKS
        assertEquals(listOf("Espresso", "Latte / cappuccino", "Double espresso"),
            Caffeine.quickDrinks(drinks, Caffeine.DEFAULT_QUICK_DRINKS).map { it.label })
        // One star: the first unstarred drinks fill the card, least caffeine first.
        assertEquals(listOf("Green tea", "Black tea", "Monster"), Caffeine.quickDrinks(drinks, listOf("Monster")).map { it.label })
        // A star on a drink that was removed is ignored.
        assertEquals(3, Caffeine.quickDrinks(drinks.filterNot { it.label == "Espresso" }, Caffeine.DEFAULT_QUICK_DRINKS).size)
    }

    @Test
    fun yourDrinksSurviveTheirJson() {
        val mine = Caffeine.DEFAULT_DRINKS + CaffeinePreset("Kofola", "500 ml", 40)
        val back = Caffeine.drinksFromJson(Caffeine.drinksToJson(mine))!!
        assertEquals(mine.sortedWith(Caffeine.drinkOrder), back)
        assertNull(Caffeine.drinksFromJson("[]"))
        assertNull(Caffeine.drinksFromJson("not json"))
    }
}
