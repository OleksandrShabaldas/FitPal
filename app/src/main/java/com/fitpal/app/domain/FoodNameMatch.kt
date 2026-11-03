package com.fitpal.app.domain

/**
 * Is a food-database name the same food as a name the AI gave? Used before borrowing the database's
 * nutrition for an ingredient the AI left at 0 kcal (`NutritionRepository.lookupIngredientStrict`).
 *
 * The same food means: the database name's first part ("Banana" in "Banana, raw") is the AI's name,
 * or every word of the AI's name is in the database name and the rest are plain describing words
 * (raw, boiled, without salt…). "Hot cocoa, prepared with tap water" is not "Tap water". Plurals are
 * folded ("potatoes" = "potato"), case and punctuation ignored.
 */
object FoodNameMatch {

    fun sameFood(query: String, description: String): Boolean {
        val q = words(query).toSet()
        if (q.isEmpty()) return false
        val head = words(description.substringBefore(',')).toSet()
        if (head == q) return true
        val all = words(description)
        if (!q.all { it in all }) return false
        return all.filterNot { it in q }.all { it in QUALIFIERS }
    }

    /** The longest word of [name] (4+ letters), for a second, wider search. */
    fun mainWord(name: String): String? = name.split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.length >= 4 }
        .maxByOrNull { it.length }

    private fun words(s: String): List<String> =
        s.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }.map(::stem)

    private fun stem(w: String): String = when {
        w.length > 4 && w.endsWith("oes") -> w.dropLast(2)
        w.length > 4 && w.endsWith("ies") -> w.dropLast(3) + "y"
        w.length > 3 && w.endsWith("s") && !w.endsWith("ss") -> w.dropLast(1)
        else -> w
    }

    /** Words that describe a food's state or cut, never another food. "with" and "prepared" are left out on purpose. */
    private val QUALIFIERS = setOf(
        "raw", "fresh", "cooked", "boiled", "steamed", "plain", "whole", "ripe", "peeled", "unpeeled", "unsalted",
        "salted", "salt", "without", "no", "added", "dried", "dry", "frozen", "canned", "drained", "baked", "roasted",
        "grilled", "broiled", "fried", "pan", "stewed", "poached", "mashed", "chopped", "sliced", "diced", "skinless",
        "boneless", "skin", "flesh", "lean", "medium", "large", "small", "all", "variety", "regular", "average",
        "commercial", "homemade", "home", "made", "in", "and", "or", "of", "only", "meat", "ns", "as", "to", "eaten",
        "uncooked", "ready", "type", "style", "white", "yellow", "red", "green"
    )
}
