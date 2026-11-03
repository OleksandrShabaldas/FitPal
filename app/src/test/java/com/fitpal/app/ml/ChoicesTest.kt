package com.fitpal.app.ml

import com.fitpal.app.domain.model.withChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Not sure about" choices (docs/redesign/15): alternatives that replace an ingredient, never add one. */
class ChoicesTest {

    private fun item(name: String, grams: Int, kcal: Int) =
        """{"name":"$name","grams":$grams,"kcalPer100g":$kcal,"proteinPer100g":0,"fatPer100g":0,"carbsPer100g":0}"""

    private val okroshka = """{"foods":[{"name":"Okroshka","isDrink":false,"grams":600,"items":[
        ${item("Kefir", 250, 41)},${item("Boiled potato", 80, 77)},${item("Boiled ham", 60, 110)},${item("Cucumber", 60, 15)}],
        "choices":[
          {"label":"Base","item":"Kefir","options":[${item("Kefir", 250, 41)},${item("Kvass", 250, 27)},${item("Whey", 250, 18)}]},
          {"label":"Meat","item":"Boiled ham","options":[${item("Boiled ham", 60, 110)},${item("Boiled beef", 60, 220)}]},
          {"label":"Topping","item":"Sour cream","options":[${item("Sour cream", 30, 190)},${item("Mayonnaise", 30, 680)}]},
          {"label":"Veg","item":"Cucumber","options":[${item("Cucumber", 60, 15)},${item("Boiled potato", 60, 77)}]}
        ]}]}"""

    @Test
    fun choicesPointAtAnIngredientAndStartOnIt() {
        val food = FoodJsonParser.parseFoods(okroshka).single()
        // "Sour cream" isn't in the dish (an add-on, dropped); "Veg" offers only another item of the dish (dropped).
        assertEquals(listOf("Base", "Meat"), food.choices.map { it.label })
        val base = food.choices[0]
        assertEquals(listOf("Kefir", "Kvass", "Whey"), base.options.map { it.name })
        assertEquals(0, base.selected)
    }

    @Test
    fun pickingSwapsTheIngredientAtItsCurrentWeight() {
        val food = FoodJsonParser.parseFoods(okroshka).single()
        // The user had made it a bigger bowl: 300 g of kefir.
        val bigger = food.copy(ingredients = food.ingredients.map { if (it.name == "Kefir") it.copy(grams = 300f) else it })
        val kvass = bigger.withChoice(0, 1)!!
        val swapped = kvass.ingredients.first()
        assertEquals("Kvass", swapped.name)
        assertEquals(300f, swapped.grams, 0.01f)
        assertEquals(4, kvass.ingredients.size)
        assertEquals(1, kvass.choices[0].selected)
        // Back to kefir, still at 300 g.
        val back = kvass.withChoice(0, 0)!!
        assertEquals("Kefir", back.ingredients.first().name)
        assertEquals(300f, back.ingredients.first().grams, 0.01f)
        // Same option again: nothing to do.
        assertNull(back.withChoice(0, 0))
    }

    @Test
    fun aRemovedIngredientEndsItsChoice() {
        val food = FoodJsonParser.parseFoods(okroshka).single()
        val noHam = food.copy(ingredients = food.ingredients.filterNot { it.name == "Boiled ham" })
        assertNull(noHam.withChoice(1, 1))
    }

    @Test
    fun plainFoodsHaveNoChoicesAndOldVariationsAreIgnored() {
        val raw = """{"foods":[{"name":"Beef stew with rice","isDrink":false,"grams":280,"items":[
            ${item("White rice", 180, 130)},${item("Beef stew", 100, 155)}],
            "variations":[{"name":"Brown rice","grams":150,"kcalPer100g":112,"description":"Brown rice"}],"choices":[]}]}"""
        val food = FoodJsonParser.parseFoods(raw).single()
        assertTrue(food.choices.isEmpty())
        assertEquals(2, food.ingredients.size)
    }
}
