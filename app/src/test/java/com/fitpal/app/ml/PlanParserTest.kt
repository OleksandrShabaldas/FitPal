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
        // Plain-string steps (every plan before 2.15) still read, numbering dropped.
        assertEquals(listOf("Rinse the rice.", "Cook the salmon."), dinner.steps.map { it.text })
        assertTrue(dinner.steps.all { it.title.isEmpty() && it.minutes == null })
        assertTrue(dinner.kcal > 300f)
        assertEquals("snack", plan.meals[1].mealType) // unknown type → the fallback
        assertEquals(1, plan.shopping.size)
    }

    @Test fun readsStructuredSteps() {
        val withSteps = """{"meals":[{"mealType":"dinner","name":"Paprika chicken","ingredients":[
            {"name":"Chicken breast","grams":150,"proteinPer100g":31,"fatPer100g":3.6}],
          "steps":[
            {"phase":"Preparation","title":"Season the chicken","text":"1. Rub with 1 tsp (2 g) paprika.","uses":["Chicken breast 150 g","Paprika 2 g"]},
            {"phase":"cook","title":"Sear","text":"Medium-high until golden.","minutes":5},
            {"phase":"plate","title":"Rest","text":"Rest before slicing.","timer":3},
            {"title":"","text":""},
            {"phase":"cook","title":"Simmer","text":"Low heat.","minutes":9999}]}]}"""
        val steps = FoodJsonParser.parsePlan(withSteps, fallbackMealType = "dinner").meals.single().steps
        assertEquals(4, steps.size) // the empty one is dropped
        assertEquals("prep", steps[0].phase)
        assertEquals("Rub with 1 tsp (2 g) paprika.", steps[0].text)
        assertEquals(listOf("Chicken breast 150 g", "Paprika 2 g"), steps[0].uses)
        assertEquals(5, steps[1].minutes)
        assertEquals("serve", steps[2].phase)
        assertEquals(3, steps[2].minutes)
        assertEquals(null, steps[3].minutes) // out of range: no timer
        // A stored recipe keeps them.
        val r = FoodJsonParser.parsePlan(withSteps, fallbackMealType = "dinner").meals.single()
        val back = com.fitpal.app.data.local.MealJson.decodeRecipe(com.fitpal.app.data.local.MealJson.encodeRecipe(r))!!
        assertEquals(r.steps, back.steps)
    }
}
