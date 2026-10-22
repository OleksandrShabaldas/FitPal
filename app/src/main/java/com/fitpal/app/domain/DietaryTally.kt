package com.fitpal.app.domain

import com.fitpal.app.data.local.MealJson
import com.fitpal.app.data.local.entity.MealLogItemEntity
import com.fitpal.app.domain.model.DietaryRuleKind
import org.json.JSONArray
import org.json.JSONObject

/**
 * How many of a logged item's calories count toward a food limit — the one place it's worked out, so
 * Home, the Limits page, the over-limit notification, the pre-log gate and the coach all agree.
 *
 * An item counts toward a rule when it's tagged with it ([MealLogItemEntity.ruleTags]). For a dish,
 * [MealLogItemEntity.ruleParts] can name the ingredients that are the part that counts (the fried
 * schnitzel, not the potato salad next to it): then only their share of the item's calories counts.
 * The share is worked out from the ingredient breakdown each time, so a portion rescale or an
 * ingredient edit follows — an ingredient swapped or removed simply stops counting. A tagged rule with
 * no parts counts the whole item (a burger, a cake, single foods, and every row from before v33).
 */
object DietaryTally {

    /** The form an ingredient name is stored and matched in. */
    fun key(name: String): String = name.trim().lowercase()

    /** Calories of [item] that count toward [kind] (0 when it isn't tagged with it). */
    fun countedKcal(item: MealLogItemEntity, kind: DietaryRuleKind): Float {
        if (kind !in DietaryRuleKind.tagsFromDb(item.ruleTags)) return 0f
        val names = partsFromDb(item.ruleParts)[kind]?.takeIf { it.isNotEmpty() } ?: return item.calories
        val ingredients = MealJson.decodeIngredients(item.ingredientsJson)
        val total = ingredients.sumOf { it.calories.toDouble() }
        if (ingredients.isEmpty() || total <= 0.0) return item.calories
        val part = ingredients.filter { key(it.name) in names }.sumOf { it.calories.toDouble() }
        return (item.calories * (part / total)).toFloat()
    }

    /** The day's (or any list's) total toward [kind]. */
    fun countedKcal(items: List<MealLogItemEntity>, kind: DietaryRuleKind): Float =
        items.sumOf { countedKcal(it, kind).toDouble() }.toFloat()

    /** True when only part of [item] counts toward [kind] — the Limits page then says "200 of 1,000 kcal". */
    fun isPartial(item: MealLogItemEntity, kind: DietaryRuleKind): Boolean =
        countedKcal(item, kind) < item.calories - 0.5f

    /** DB value for the parts map: `{"fried":["breaded chicken","frying oil"]}`; null when there are none. */
    fun partsToDb(parts: Map<DietaryRuleKind, Set<String>>): String? {
        val kept = parts.filterValues { it.isNotEmpty() }
        if (kept.isEmpty()) return null
        return JSONObject().apply {
            kept.forEach { (kind, names) -> put(kind.id, JSONArray(names.map(::key).sorted())) }
        }.toString()
    }

    /** Parse [partsToDb]'s JSON back (lenient: anything unreadable is "no parts"). */
    fun partsFromDb(value: String?): Map<DietaryRuleKind, Set<String>> {
        if (value.isNullOrBlank()) return emptyMap()
        return runCatching {
            val o = JSONObject(value)
            buildMap {
                o.keys().forEach { id ->
                    val kind = DietaryRuleKind.fromId(id) ?: return@forEach
                    val arr = o.optJSONArray(id) ?: return@forEach
                    val names = (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank)?.let(::key) }.toSet()
                    if (names.isNotEmpty()) put(kind, names)
                }
            }
        }.getOrDefault(emptyMap())
    }
}
