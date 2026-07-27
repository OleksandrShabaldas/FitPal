package com.fitpal.app.domain.model

/** The meal categories a logged item can belong to. Stored as lowercase strings. */
object MealTypes {
    const val BREAKFAST = "breakfast"
    const val LUNCH = "lunch"
    const val DINNER = "dinner"
    const val SNACK = "snack"

    /** Plain water quick-adds — deliberately NOT a food category, so they show only in the
     *  Water widget and never among the meals. */
    const val WATER = "water"

    /** Display order on the Home screen and in the picker. */
    val ALL = listOf(BREAKFAST, LUNCH, DINNER, SNACK)

    fun label(type: String): String = when (type) {
        BREAKFAST -> "Breakfast"
        LUNCH -> "Lunch"
        DINNER -> "Dinner"
        SNACK -> "Snack"
        else -> type.replaceFirstChar { it.uppercase() }
    }

    /** A sensible default based on the time of day. */
    fun defaultForHour(hour: Int): String = when {
        hour < 11 -> BREAKFAST
        hour < 16 -> LUNCH
        hour < 21 -> DINNER
        else -> SNACK
    }
}

/**
 * User-configurable time windows that decide which meal a freshly-logged food falls into. Each meal
 * has its own start and end, so there can be gaps between them — any time outside all three windows
 * (between meals, or late at night) becomes a snack. Times are minutes since midnight.
 */
data class MealWindows(
    val breakfastStart: Int = 5 * 60,    // 05:00
    val breakfastEnd: Int = 11 * 60,     // 11:00
    val lunchStart: Int = 11 * 60,       // 11:00
    val lunchEnd: Int = 16 * 60,         // 16:00
    val dinnerStart: Int = 16 * 60,      // 16:00
    val dinnerEnd: Int = 21 * 60         // 21:00
) {
    /** The meal category for a given minute-of-day; falls back to snack outside every window. */
    fun mealTypeForMinutes(min: Int): String = when {
        min in breakfastStart until breakfastEnd -> MealTypes.BREAKFAST
        min in lunchStart until lunchEnd -> MealTypes.LUNCH
        min in dinnerStart until dinnerEnd -> MealTypes.DINNER
        else -> MealTypes.SNACK
    }
}
