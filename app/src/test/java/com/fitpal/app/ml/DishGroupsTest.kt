package com.fitpal.app.ml

import com.fitpal.app.domain.model.Ingredient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DishGroupsTest {

    private fun ing(name: String, grams: Float) = Ingredient(name, grams, 200f, 10f, 10f, 10f)
    private val loose = listOf(ing("Cod in batter", 180f), ing("Fries", 150f), ing("Tartar sauce", 30f), ing("Lettuce", 50f), ing("Tomato", 40f))

    @Test fun twoDishesFromOnePlate() {
        val raw = """{"dishes":[
            {"name":"Fish and chips","items":["Cod in batter","Fries","Tartar sauce"],"also":[],"missing":[]},
            {"name":"Side salad","items":["lettuce","Tomato"],"also":[],"missing":[]}]}"""
        val r = FoodJsonParser.parseDishGroups(raw, loose)
        assertEquals(listOf("Fish and chips", "Side salad"), r.dishes.map { it.label })
        assertEquals(listOf("Cod in batter", "Fries", "Tartar sauce"), r.dishes[0].ingredients.map { it.name })
        assertEquals(listOf("Lettuce", "Tomato"), r.dishes[1].ingredients.map { it.name })
        assertTrue(r.candidates.isEmpty())
    }

    @Test fun unclaimedItemsJoinTheFirstDish() {
        val raw = """{"dishes":[{"name":"Fish and chips","items":["cod","fries"]},{"name":"Side salad","items":["Lettuce"]}]}"""
        val r = FoodJsonParser.parseDishGroups(raw, loose)
        assertEquals(5, r.dishes.sumOf { it.ingredients.size })
        assertTrue(r.dishes[0].ingredients.any { it.name == "Tartar sauce" })
    }

    @Test fun oneGroupFallsBackToOneDishWithCandidates() {
        val raw = """{"dishes":[{"name":"Fish and chips","items":["Cod in batter","Fries"],"also":["Fish burger"]}]}"""
        val r = FoodJsonParser.parseDishGroups(raw, loose)
        assertTrue(r.dishes.isEmpty())
        assertEquals(listOf("Fish and chips", "Fish burger"), r.candidates.map { it.label })
        assertEquals(5, r.candidates[0].ingredients.size)
    }

    @Test fun theOlderAnswerIsReadAsCandidates() {
        val raw = """{"dishes":[{"name":"Pizza","missing":[]},{"name":"Calzone","missing":[]}]}"""
        val r = FoodJsonParser.parseDishGroups(raw, loose)
        assertEquals(listOf("Pizza", "Calzone"), r.candidates.map { it.label })
    }

    @Test fun garbageGivesNothing() {
        val r = FoodJsonParser.parseDishGroups("sorry, no idea", loose)
        assertTrue(r.dishes.isEmpty() && r.candidates.isEmpty())
    }
}
