package com.fitpal.app.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanParserTest {

    private val raw = """
        Here's your plan:
        {"meals":[{"mealType":"Dinner","name":"salmon, rice and greens","cuisine":"Japanese","timeMinutes":25,"servings":2,
          "difficulty":"easy","whyItFits":"42 g protein","ingredients":[
            {"name":"Salmon fillet","grams":150,"have":true,"kcalPer100g":208,"proteinPer100g":20,"fatPer100g":13,"carbsPer100g":0},
            {"name":"Spinach","grams":100,"have":false,"proteinPer100g":2.9,"fatPer100g":0.4,"carbsPer100g":3.6,"fiberPer100g":2.2}],
          "steps":["1. Rinse the rice.","2) Cook the salmon."],"tips":["Swap salmon for tofu."]},
          {"mealType":"elevenses","name":"Apple","ingredients":[{"name":"Apple","grams":180,"carbsPer100g":14,"fiberPer100g":2.4}],"steps":[]},
          {"mealType":"lunch","name":"Nothing in it","ingredients":[]}],
         "shopping":[{"name":"Spinach","amount":"100 g","estimatedCost":1.2}]}
    """.trimIndent()

    @Test fun readsRecipes() {
        val plan = FoodJsonParser.parsePlan(raw, fallbackMealType = "snack", dayOffset = 2)
        assertEquals(2, plan.meals.size) // the empty one is dropped
        val dinner = plan.meals[0]
        assertEquals("Salmon, rice and greens", dinner.name)
        assertEquals("dinner", dinner.mealType)
        assertEquals(2, dinner.dayOffset)
        assertEquals(listOf(true, false), dinner.ingredients.map { it.have })
        assertEquals(listOf("Rinse the rice.", "Cook the salmon."), dinner.steps)
        assertTrue(dinner.kcal > 300f)
        assertEquals("snack", plan.meals[1].mealType) // unknown type → the fallback
        assertEquals(1, plan.shopping.size)
    }
}
