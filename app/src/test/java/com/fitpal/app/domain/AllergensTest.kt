package com.fitpal.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AllergensTest {

    @Test fun hiddenInDishes() {
        assertEquals(setOf("Tree nuts"), Allergens.found(listOf("Basil pesto", "Spaghetti"), setOf("Tree nuts")))
        assertEquals(setOf("Sesame"), Allergens.found(listOf("Hummus"), setOf("Sesame", "Peanuts")))
        assertEquals(setOf("Gluten", "Eggs"), Allergens.found(listOf("Spaghetti", "Egg yolk"), setOf("Gluten", "Eggs")))
    }

    @Test fun lookalikesDontCount() {
        assertEquals(emptySet<String>(), Allergens.found(listOf("Almond milk", "Peanut butter"), setOf("Lactose")))
        assertEquals(emptySet<String>(), Allergens.found(listOf("Rice noodles", "Eggplant"), setOf("Gluten", "Eggs")))
        assertEquals(emptySet<String>(), Allergens.found(listOf("Coconut milk", "Nutmeg"), setOf("Tree nuts")))
        assertEquals(emptySet<String>(), Allergens.found(listOf("Peanuts"), setOf("Tree nuts")))
    }

    @Test fun typedAllergyMatchesItsOwnName() {
        assertEquals(setOf("kiwi"), Allergens.found(listOf("Kiwi slices"), setOf("kiwi")))
    }
}
