package com.fitpal.app.domain

/**
 * Heuristics for treating database / barcode foods as drinks — so a logged Coca-Cola or juice
 * counts toward hydration (in ml), even though the food database has no water column.
 */
object Drinks {
    private val drinkWords = listOf(
        "cola", "kofola", "juice", "water", "milk", "tea", "coffee", "soda", "lemonade",
        "kvass", "kvas", "beer", "wine", "cider", "drink", "smoothie", "shake", "milkshake",
        "latte", "cappuccino", "espresso", "cocoa", "kombucha", "kefir", "ayran", "tonic",
        "sprite", "fanta", "pepsi", "nectar", "mate", "frappe", "lemonade", "energy drink"
    )

    /** Heuristic: is this food likely a drink (measured in ml, counts toward water)? */
    fun isDrink(name: String, category: String?): Boolean {
        if (category?.contains("drink", ignoreCase = true) == true) return true
        if (category?.contains("beverage", ignoreCase = true) == true) return true
        val n = name.lowercase()
        return drinkWords.any { n.contains(it) }
    }

    /** Water per 100 ml for a drink, estimated from its macros (most of a drink is water). */
    fun estimateWaterPer100(carbs: Float, protein: Float, fat: Float): Float {
        val solids = (carbs + protein + fat).coerceAtLeast(0f)
        return (100f - solids).coerceIn(50f, 100f)
    }
}
