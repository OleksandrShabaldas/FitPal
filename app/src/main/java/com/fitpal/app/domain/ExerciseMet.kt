package com.fitpal.app.domain

/**
 * Calories from exercise the deterministic way: MET × weight(kg) × hours.
 * The AI only names the activity, duration and a MET estimate — the actual
 * number is computed here (same lesson as the health score: don't trust the
 * model for figures it can derive wrong).
 */
object ExerciseMet {

    /** kcal = MET × kg × hours, with sane clamps. */
    fun calories(met: Float, weightKg: Float, minutes: Int): Float {
        val m = met.coerceIn(1f, 23f)            // 23 ≈ all-out sprint, the human ceiling
        val w = if (weightKg > 0f) weightKg else 70f
        return m * w * (minutes.coerceAtLeast(0) / 60f)
    }

    /** Fallback MET when the model doesn't give a usable one — keyword match. */
    fun lookup(activity: String): Float? {
        val a = activity.lowercase()
        return TABLE.firstOrNull { a.contains(it.first) }?.second
    }

    private val TABLE = listOf(
        "sprint" to 14f, "run" to 9.8f, "jog" to 7.0f, "walk" to 3.5f, "hike" to 6f,
        "cycl" to 7.5f, "bike" to 7.5f, "spin" to 8.5f, "swim" to 7f,
        "weight" to 5f, "lift" to 5f, "gym" to 5f, "strength" to 5f,
        "yoga" to 2.5f, "pilates" to 3f, "stretch" to 2.3f,
        "hiit" to 8f, "circuit" to 8f, "ellipt" to 5f, "row" to 7f,
        "dance" to 5f, "zumba" to 6.5f, "soccer" to 7f, "football" to 8f,
        "basketball" to 6.5f, "tennis" to 7f, "badminton" to 5.5f,
        "jump rope" to 11f, "skip" to 11f, "stair" to 8f, "box" to 7.8f,
        "climb" to 8f, "ski" to 7f, "skate" to 7f, "pushup" to 8f, "squat" to 5f
    )
}
