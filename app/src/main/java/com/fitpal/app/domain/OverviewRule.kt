package com.fitpal.app.domain

import com.fitpal.app.domain.model.MealTypes

/**
 * Whether a day (or week, or month) has anything for an AI overview to talk about (the 4 Nov review,
 * #3): any food or drink except plain water. The water tracker's glasses don't count, and neither does
 * a glass of water that came in with a photo; a black coffee or a diet soda does. Used by the automatic
 * overviews (no overview and no notification on an empty day) and by the overview screen ("Nothing
 * logged yet"). Pure, so it's unit-tested.
 */
object OverviewRule {

    /** One logged item: does it count as "something logged"? */
    fun counts(mealType: String?, name: String): Boolean =
        mealType != MealTypes.WATER && !Drinks.isPlainWater(name)

    /** Something to review among these logged items ([mealType] to item name). */
    fun hasSomething(items: List<Pair<String?, String>>): Boolean = items.any { (type, name) -> counts(type, name) }
}
