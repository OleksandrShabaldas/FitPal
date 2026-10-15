package com.fitpal.app.domain

import java.util.concurrent.ConcurrentHashMap

/**
 * Whole-word matching for the app's food keyword lists (drinks, caffeine, the food-limit heuristics).
 *
 * Plain `contains` is wrong for food names — "cola" is inside "chocolate", "tea" inside "steak",
 * "water" inside "watermelon", "soda" inside "baking soda". Here a term matches only on word
 * boundaries (letters and digits in any script, so Czech and Cyrillic names work too):
 *  - `"tea"` matches "Green tea", "teas" and "tea, black" but not "steak" or "teaspoon";
 *    a trailing plural ("s"/"es") is allowed, and a multi-word term tolerates spaces or hyphens
 *    between its words ("energy drink" ↔ "energy-drink").
 *  - `"káv*"` (trailing star) matches any word *starting* with it — for inflected languages
 *    ("káva", "kávu", "kávy") and compounds ("milkshake" from `"milkshake*"`).
 */
object TextMatch {
    private val cache = ConcurrentHashMap<String, Regex>()

    fun containsTerm(text: String, term: String): Boolean = regexFor(term).containsMatchIn(text)

    fun containsAny(text: String, terms: Collection<String>): Boolean = terms.any { containsTerm(text, it) }

    /** The first of [terms] found in [text], or null. */
    fun firstMatch(text: String, terms: Collection<String>): String? = terms.firstOrNull { containsTerm(text, it) }

    private fun regexFor(term: String): Regex = cache.getOrPut(term) {
        val prefix = term.endsWith("*")
        val words = term.removeSuffix("*").trim().lowercase().split(Regex("[\\s-]+")).filter { it.isNotEmpty() }
        val body = words.joinToString("[\\s\\-]+") { Regex.escape(it) }
        val tail = if (prefix) "" else "(?:s|es)?(?![\\p{L}\\p{N}])"
        Regex("(?<![\\p{L}\\p{N}])$body$tail", setOf(RegexOption.IGNORE_CASE))
    }
}
