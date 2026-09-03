package com.fitpal.app.sync

import com.fitpal.app.data.local.entity.ExerciseEntryEntity
import org.json.JSONObject

/**
 * The rich workout detail Calistapp ships alongside an imported exercise (in
 * [ExerciseEntryEntity.detailsJson]) — intensity, heart rate, reps and the per-exercise breakdown.
 * FitPal only ever READS this: it's shown on the exercise detail screen for Calistapp-sourced rows
 * and folded into the AI overview's activity context. FitPal-generated exercises have no details,
 * so nothing here touches how FitPal logs or estimates its own workouts.
 */
data class CalistappWorkoutDetails(
    val type: String?,
    /** HR-zone intensity label from Calistapp ("Very light".."Maximum"). */
    val intensity: String?,
    val avgHr: Int?,
    val avgActiveHr: Int?,
    val peakHr: Int?,
    val totalReps: Int?,
    val rpe: Int?,
    val activeMinutes: Int?,
    val exercises: List<Exercise>,
) {
    data class Exercise(val name: String, val kcal: Double, val reps: Int, val sets: Int)

    /** The effort HR to show — active-only average is the cleaner signal, falling back to overall. */
    val effortHr: Int? get() = avgActiveHr?.takeIf { it > 0 } ?: avgHr?.takeIf { it > 0 }
}

object CalistappDetails {

    /** True for rows that came from Calistapp (they alone carry [CalistappWorkoutDetails]). */
    fun isFromCalistapp(entry: ExerciseEntryEntity): Boolean =
        entry.source == FitPalSyncContract.SOURCE_CALISTAPP

    /** Parse the details blob; null if absent or malformed (so callers just skip the extra UI). */
    fun parse(detailsJson: String?): CalistappWorkoutDetails? {
        if (detailsJson.isNullOrBlank()) return null
        return runCatching {
            val o = JSONObject(detailsJson)
            val exercises = o.optJSONArray("exercises")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let { e ->
                        CalistappWorkoutDetails.Exercise(
                            name = e.optString("name").ifBlank { return@mapNotNull null },
                            kcal = e.optDouble("kcal", 0.0),
                            reps = e.optInt("reps", 0),
                            sets = e.optInt("sets", 0),
                        )
                    }
                }
            }.orEmpty()
            CalistappWorkoutDetails(
                type = o.optStringOrNull("type"),
                intensity = o.optStringOrNull("intensity"),
                avgHr = o.optIntOrNull("avgHr"),
                avgActiveHr = o.optIntOrNull("avgActiveHr"),
                peakHr = o.optIntOrNull("peakHr"),
                totalReps = o.optIntOrNull("totalReps"),
                rpe = o.optIntOrNull("rpe"),
                activeMinutes = o.optIntOrNull("activeMinutes"),
                exercises = exercises,
            )
        }.getOrNull()
    }

    /**
     * A compact one-line suffix for the AI overview's per-day activity log, so the coach reasons
     * from the real workout (intensity, HR, reps, what was trained), not just a kcal number. Empty
     * for non-Calistapp entries or when there's nothing extra to add.
     */
    fun promptSuffix(entry: ExerciseEntryEntity): String {
        if (!isFromCalistapp(entry)) return ""
        val d = parse(entry.detailsJson) ?: return ""
        val parts = buildList {
            d.intensity?.let { add("$it intensity") }
            d.effortHr?.let { add("avg HR $it") }
            d.peakHr?.takeIf { it > 0 }?.let { add("peak $it") }
            d.totalReps?.takeIf { it > 0 }?.let { add("$it reps") }
            d.rpe?.let { add("RPE $it/10") }
            d.exercises.takeIf { it.isNotEmpty() }
                ?.joinToString(", ") { it.name }
                ?.let { add("exercises: $it") }
        }
        return if (parts.isEmpty()) "" else " [Calistapp — ${parts.joinToString("; ")}]"
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null
}
