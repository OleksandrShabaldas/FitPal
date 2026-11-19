package com.fitpal.app.domain.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * The user's meal tags (the 17 Nov review, #4): two rows on a meal's "When and where" panel — **where and
 * who with** (Home, Family meal, …) and **how you felt** (Hungry, Stressed, …). Both are edited in Settings →
 * Logging & meal times → Situation tags: renamed, reordered, hidden, and added to. Built-in tags can be hidden
 * but not deleted; the user's own can be deleted.
 *
 * A meal stores its tags as text ([MealContext] for the situation tags in `meal_logs.context`, the feelings in
 * `meal_logs.feelings`), so renaming a tag changes the list for new meals only (the user's choice) and an old
 * meal keeps the words it was logged with.
 */
data class TagItem(val name: String, val builtIn: Boolean = false, val hidden: Boolean = false)

data class TagSettings(
    val situation: List<TagItem> = DEFAULT_SITUATION,
    val feelings: List<TagItem> = DEFAULT_FEELINGS
) {
    /** The tags offered as chips, in the user's order. */
    val shownSituation: List<String> get() = situation.filterNot { it.hidden }.map { it.name }
    val shownFeelings: List<String> get() = feelings.filterNot { it.hidden }.map { it.name }

    /** Is [tag] one of the feelings (so it's stored in `feelings`, not `context`)? Case-insensitive. */
    fun isFeeling(tag: String): Boolean = feelings.any { it.name.equals(tag, ignoreCase = true) }

    /** A meal's one tag set split into (situation, feelings) for storage. */
    fun split(tags: Set<String>): Pair<Set<String>, Set<String>> = tags.partition { !isFeeling(it) }.let { (a, b) -> a.toSet() to b.toSet() }

    /** Is [name] free to use as a new tag (not blank, not too long, not already in either row)? */
    fun canAdd(name: String): Boolean {
        val n = name.trim()
        return n.isNotEmpty() && n.length <= MAX_NAME &&
            (situation + feelings).none { it.name.equals(n, ignoreCase = true) }
    }

    fun toJson(): String = JSONObject()
        .put("situation", itemsJson(situation))
        .put("feelings", itemsJson(feelings))
        .toString()

    companion object {
        /** Up to this many tags in a row, so tagging stays one quick tap. */
        const val MAX_PER_ROW = 16
        const val MAX_NAME = 24

        val DEFAULT_SITUATION = listOf("Home", "Restaurant", "Family meal", "Work/school", "Social", "On the go", "Travel")
            .map { TagItem(it, builtIn = true) }
        val DEFAULT_FEELINGS = listOf("Hungry", "Stressed", "Tired", "Bored", "Celebrating", "Rushed")
            .map { TagItem(it, builtIn = true) }

        /** Reads the stored JSON; anything missing or unreadable falls back to the defaults. */
        fun fromJson(raw: String?): TagSettings {
            if (raw.isNullOrBlank()) return TagSettings()
            return runCatching {
                val o = JSONObject(raw)
                TagSettings(
                    situation = readItems(o.optJSONArray("situation")) ?: DEFAULT_SITUATION,
                    feelings = readItems(o.optJSONArray("feelings")) ?: DEFAULT_FEELINGS
                )
            }.getOrDefault(TagSettings())
        }

        private fun itemsJson(items: List<TagItem>) = JSONArray().apply {
            items.forEach { put(JSONObject().put("name", it.name).put("builtIn", it.builtIn).put("hidden", it.hidden)) }
        }

        private fun readItems(arr: JSONArray?): List<TagItem>? {
            arr ?: return null
            val seen = mutableSetOf<String>()
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = o.optString("name").trim().take(MAX_NAME)
                if (name.isEmpty() || !seen.add(name.lowercase())) return@mapNotNull null
                TagItem(name, o.optBoolean("builtIn", false), o.optBoolean("hidden", false))
            }.take(MAX_PER_ROW)
        }
    }
}
