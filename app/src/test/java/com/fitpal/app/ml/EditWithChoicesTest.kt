package com.fitpal.app.ml

import com.fitpal.app.domain.model.withChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Edit with AI sees "Not sure about" (the 4 Nov review #8, docs/redesign/17). */
class EditWithChoicesTest {

    private fun item(name: String, grams: Int, kcal: Int) =
        """{"name":"$name","grams":$grams,"kcalPer100g":$kcal,"proteinPer100g":0,"fatPer100g":0,"carbsPer100g":0}"""

    private val pirozhki = FoodJsonParser.parseFoods(
        """{"foods":[{"name":"Fried Pirozhki","isDrink":false,"grams":480,"items":[
            ${item("Fried yeast dough", 300, 303)},${item("Mashed potato filling", 180, 107)}],
            "choices":[{"label":"Filling","item":"Mashed potato filling","options":[
              ${item("Mashed potato filling", 180, 107)},${item("Cabbage filling", 180, 95)},${item("Minced meat filling", 180, 250)}]}]}]}"""
    ).single()

    @Test
    fun thePromptListsTheGroupsAndAsksWhatsStillUnsure() {
        val prompt = FoodPrompts.editFood(pirozhki, "add everything from those variations")
        assertTrue(prompt.contains("NOT SURE ABOUT"))
        assertTrue(prompt.contains("- Filling: Mashed potato filling (in the dish now, 180g) | Cabbage filling (95 kcal/100g) | Minced meat filling (250 kcal/100g)"))
        assertTrue(prompt.contains("\"variations\""))
        assertTrue(prompt.contains("still uncertain"))
        assertTrue(prompt.contains("The user's correction: \"add everything from those variations\""))
    }

    @Test
    fun theChosenOptionIsTheOneMarkedInTheDish() {
        val cabbage = pirozhki.withChoice(0, 1)!!
        val prompt = FoodPrompts.editFood(cabbage, "it was fried in more oil")
        assertTrue(prompt.contains("Cabbage filling (in the dish now, 180g)"))
        assertTrue(prompt.contains("Mashed potato filling (107 kcal/100g)"))
    }

    @Test
    fun noGroupsNoSection() {
        val prompt = FoodPrompts.editFood(pirozhki.copy(choices = emptyList()), "no oil")
        assertFalse(prompt.contains("NOT SURE ABOUT"))
        // A group whose item was removed isn't shown on the page, so it isn't sent either.
        val gone = pirozhki.copy(ingredients = pirozhki.ingredients.filterNot { it.name == "Mashed potato filling" })
        assertFalse(FoodPrompts.editFood(gone, "no oil").contains("NOT SURE ABOUT"))
    }

    @Test
    fun anAnswerThatSettlesTheGroupDropsIt() {
        val answer = """{"foods":[{"name":"Fried Pirozhki","isDrink":false,"grams":480,"items":[
            ${item("Fried yeast dough", 300, 303)},${item("Mashed potato filling", 60, 107)},
            ${item("Cabbage filling", 60, 95)},${item("Minced meat filling", 60, 250)}],"choices":[]}]}"""
        val refined = FoodJsonParser.parseFoods(answer).single()
        val after = FoodAnalysisPipeline.withChoicesAfterEdit(pirozhki, refined, FoodJsonParser.hasChoicesKey(answer))
        assertTrue(after.choices.isEmpty())
        assertEquals(4, after.ingredients.size)
        assertEquals(480f, after.totalGrams, 0.5f)
    }

    @Test
    fun anAnswerWithoutTheKeyKeepsTheGroupsThatStillFit() {
        val answer = """{"foods":[{"name":"Fried Pirozhki","isDrink":false,"grams":480,"items":[
            ${item("Fried yeast dough", 280, 303)},${item("Mashed potato filling", 200, 107)}]}]}"""
        val refined = FoodJsonParser.parseFoods(answer).single()
        assertFalse(FoodJsonParser.hasChoicesKey(answer))
        val after = FoodAnalysisPipeline.withChoicesAfterEdit(pirozhki, refined, answered = false)
        assertEquals(listOf("Filling"), after.choices.map { it.label })
    }
}
