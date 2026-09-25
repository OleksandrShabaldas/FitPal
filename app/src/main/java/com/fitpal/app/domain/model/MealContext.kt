package com.fitpal.app.domain.model

/**
 * The one-tap meal situation tags ("Home", "Family meal", …). A meal can carry several — a family
 * dinner at home is both — stored on [com.fitpal.app.data.local.entity.MealLogEntity.context] joined
 * as "Home, Family meal", which also reads naturally in the AI review's food log ("[Home, Family
 * meal]"). A single older tag ("Restaurant") parses back as a one-tag set.
 */
object MealContext {
    /** The fixed, curated list — per-meal tagging must stay one instant tap. */
    val ALL = listOf("Home", "Restaurant", "Family meal", "Work/school", "Social", "On the go", "Travel")

    private const val SEPARATOR = ", "

    /** The tags stored in a context column (empty when untagged). */
    fun parse(value: String?): Set<String> =
        value?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet().orEmpty()

    /** The column value for a set of tags, in the curated order; null when empty. */
    fun format(tags: Set<String>): String? {
        if (tags.isEmpty()) return null
        val ordered = ALL.filter { it in tags } + tags.filter { it !in ALL }.sorted()
        return ordered.joinToString(SEPARATOR)
    }

    /** Flip one tag on/off within [current]. */
    fun toggle(current: Set<String>, tag: String): Set<String> =
        if (tag in current) current - tag else current + tag
}
