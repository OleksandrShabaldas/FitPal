package com.fitpal.app.ml

import com.fitpal.app.domain.ExerciseMet

/** The AI's exercise estimate, parsed from the model's `ACTIVITY/MINUTES/MET/TIP` text reply. */
data class ParsedExerciseEstimate(
    val activity: String,
    val minutes: Int,
    val met: Float,
    val calories: Float,
    val suggestions: List<String> = emptyList()
)

/**
 * Turns the exercise model's free-text reply into a structured estimate. Extracted from
 * `ExerciseViewModel` so the on-screen path and the watch-triggered [com.fitpal.app.wear
 * .ExerciseAnalysisWorker] parse identically (single-sourced regexes + defaults).
 */
object ExerciseEstimateParser {
    fun parse(response: String, fallbackDescription: String, weightKg: Float): ParsedExerciseEstimate {
        val activity = Regex("ACTIVITY:\\s*(.+)").find(response)?.groupValues?.get(1)?.trim()
            ?.ifEmpty { null } ?: fallbackDescription.take(40)
        val minutes = Regex("MINUTES:\\s*(\\d+)").find(response)?.groupValues?.get(1)?.toIntOrNull()
            ?.coerceIn(1, 1000) ?: 30
        val metRaw = Regex("MET:\\s*([0-9]+(?:\\.[0-9]+)?)").find(response)?.groupValues?.get(1)?.toFloatOrNull()
        val met = (metRaw ?: ExerciseMet.lookup(activity) ?: 5f).coerceIn(1f, 23f)
        val calories = ExerciseMet.calories(met, weightKg, minutes)
        val tips = Regex("TIP:\\s*(.+)").findAll(response)
            .map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.take(3).toList()
        return ParsedExerciseEstimate(activity, minutes, met, calories, tips)
    }
}
