package com.fitpal.app.domain

import com.fitpal.app.domain.model.weightLabel
import com.fitpal.app.ml.FoodJsonParser
import com.fitpal.app.ui.component.dishesTitle
import com.fitpal.app.ui.component.withPhrase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The meal page of 2.16 (docs/redesign/16): food or drink per ingredient, weight labels, phrases, titles, the clock. */
class MealPageTest {

    private fun item(name: String, grams: Int, kcal: Int, water: Int = 0) =
        """{"name":"$name","grams":$grams,"kcalPer100g":$kcal,"proteinPer100g":0,"fatPer100g":0,"carbsPer100g":${kcal / 4},"waterMlPer100":$water}"""

    private val plate = """{"foods":[{"name":"Breakfast plate","isDrink":false,"grams":410,"items":[
        ${item("Scrambled eggs", 150, 148, 70)},${item("Toast", 60, 264)},${item("Orange juice", 200, 44, 88)}]}]}"""

    @Test fun aDrinkPartStaysInsideAFoodDish() {
        val food = FoodJsonParser.parseFoods(plate).single()
        assertTrue(food.ingredients.none { it.isDrink })
        val mixed = food.withIngredientDrink(2, true)
        assertFalse(mixed.isDrink)
        assertTrue(mixed.ingredients[2].isDrink)
        // The juice's water counts as drinks, the eggs' as "from food".
        assertEquals(176f, mixed.drinkWaterMl, 0.5f)
        assertEquals(176f + 105f, mixed.totalWaterMl, 0.5f)
        assertEquals("210 g + 200 ml", weightLabel(mixed.ingredients))
    }

    @Test fun aDishIsADrinkOnlyWhenEveryPartIs() {
        val food = FoodJsonParser.parseFoods(plate).single()
        val all = food.withIngredientDrink(0, true).withIngredientDrink(1, true).withIngredientDrink(2, true)
        assertTrue(all.isDrink)
        // A part with no water of its own gets an estimate when it becomes a drink.
        assertTrue(all.ingredients[1].waterMlPer100g > 0f)
        assertFalse(all.withIngredientDrink(1, false).isDrink)
    }

    @Test fun parsedDrinksFlagEveryPart() {
        val cola = FoodJsonParser.parseFoods("""{"foods":[{"name":"Coca-Cola","isDrink":true,"grams":330,"items":[${item("Coca-Cola", 330, 42, 89)}]}]}""").single()
        assertTrue(cola.isDrink)
        assertTrue(cola.ingredients.all { it.isDrink })
        assertEquals(cola.totalWaterMl, cola.drinkWaterMl, 0.01f)
    }

    @Test fun olderDrinksCountEveryPartAsADrink() {
        val cola = FoodJsonParser.parseFoods("""{"foods":[{"name":"Coca-Cola","isDrink":true,"grams":330,"items":[${item("Coca-Cola", 330, 42, 89)}]}]}""").single()
        // Logged before the flag went per ingredient: the dish says drink, its parts say nothing.
        val old = cola.copy(ingredients = cola.ingredients.map { it.copy(isDrink = false) })
        assertTrue(old.drinkAwareIngredients.all { it.isDrink })
        assertEquals(old.totalWaterMl, old.drinkWaterMl, 0.01f)
    }

    @Test fun weightLabelsSayWhatsInIt() {
        val food = FoodJsonParser.parseFoods(plate).single()
        assertEquals("410 g", weightLabel(food.ingredients))
        assertEquals("250 ml", weightLabel(emptyList(), 250f, fallbackDrink = true))
    }

    @Test fun quickPhrasesJoinTheNote() {
        assertEquals("Ate half", withPhrase("", "Ate half"))
        assertEquals("large bowl, ate half", withPhrase("large bowl", "Ate half"))
        assertEquals("large bowl, fried in oil", withPhrase("large bowl, ", "Fried in oil"))
    }

    @Test fun aMealIsTitledByItsDishes() {
        assertEquals("Meatballs with rice", dishesTitle(listOf("Meatballs with rice")))
        assertEquals("Fish and chips & side salad", dishesTitle(listOf("Fish and chips", "Side salad")))
        assertEquals("Soup, Bread & tea", dishesTitle(listOf("Soup", "Bread", "Tea")))
    }

    @Test fun theClockFollowsTheSetting() {
        assertEquals("7:48 PM", ClockFormat.minuteOfDay(19 * 60 + 48, h24 = false))
        assertEquals("19:48", ClockFormat.minuteOfDay(19 * 60 + 48, h24 = true))
        assertEquals("12:05 AM", ClockFormat.minuteOfDay(5, h24 = false))
        assertEquals("00:05", ClockFormat.minuteOfDay(5 + 24 * 60, h24 = true))
        assertEquals("5 PM", ClockFormat.hour(17, h24 = false))
        assertEquals("17:00", ClockFormat.hour(17, h24 = true))
    }
}
