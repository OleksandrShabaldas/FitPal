package com.fitpal.app.data.repository

import com.fitpal.app.data.local.dao.DailyBurnRow
import com.fitpal.app.data.local.dao.ExerciseDao
import com.fitpal.app.data.local.entity.ExerciseEntryEntity
import com.fitpal.app.data.local.entity.SavedWorkoutEntity
import com.fitpal.app.domain.ExerciseMet
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseRepository @Inject constructor(
    private val exerciseDao: ExerciseDao
) {
    private val fmt = DateTimeFormatter.ISO_LOCAL_DATE
    fun todayString(): String = LocalDate.now().format(fmt)

    fun getForDate(date: String): Flow<List<ExerciseEntryEntity>> = exerciseDao.getForDate(date)
    fun getTotalBurnedForDate(date: String): Flow<Float> = exerciseDao.getTotalBurnedForDate(date)
    fun getDailyBurnRange(from: String, to: String): Flow<List<DailyBurnRow>> =
        exerciseDao.getDailyBurnRange(from, to)
    suspend fun totalBurnedOnce(date: String): Float = exerciseDao.totalBurnedForDateOnce(date)

    /** Log an activity. Calories are derived from MET × weight × time. */
    suspend fun logExercise(
        name: String,
        minutes: Int,
        met: Float,
        weightKg: Float,
        date: String? = null,
        source: String = "manual",
        aiSource: String? = null,
        suggestions: List<String> = emptyList()
    ): Long {
        val cal = ExerciseMet.calories(met, weightKg, minutes)
        return exerciseDao.insert(
            ExerciseEntryEntity(
                date = date ?: todayString(),
                name = name,
                minutes = minutes,
                met = met,
                caloriesBurned = cal,
                source = source,
                aiSource = aiSource,
                suggestionsJson = if (suggestions.isEmpty()) null else org.json.JSONArray(suggestions).toString()
            )
        )
    }

    /**
     * Change a logged workout's duration after the fact. Calories scale linearly with time (the MET
     * and the body weight used at log time are unchanged), so we scale from the original burn.
     * Returns the updated entry.
     */
    suspend fun updateMinutes(entry: ExerciseEntryEntity, newMinutes: Int): ExerciseEntryEntity {
        val m = newMinutes.coerceIn(1, 1000)
        val newCal = if (entry.minutes > 0) entry.caloriesBurned * m / entry.minutes
            else ExerciseMet.calories(entry.met, 70f, m)
        exerciseDao.updateDuration(entry.id, m, newCal)
        return entry.copy(minutes = m, caloriesBurned = newCal)
    }

    // ---- Saved workouts (collection) ----

    fun getSavedWorkouts(): Flow<List<SavedWorkoutEntity>> = exerciseDao.getSavedWorkouts()

    /** Save a workout template for one-tap re-logging. No-op if one with the same name exists. */
    suspend fun saveWorkout(name: String, met: Float, minutes: Int): Boolean {
        if (name.isBlank() || exerciseDao.savedWorkoutCount(name) > 0) return false
        exerciseDao.insertSavedWorkout(SavedWorkoutEntity(name = name, met = met, minutes = minutes))
        return true
    }

    suspend fun deleteSavedWorkout(id: Long) = exerciseDao.deleteSavedWorkout(id)

    /** Log a saved workout to a date (today by default) and bump its last-used time. */
    suspend fun logSavedWorkout(workout: SavedWorkoutEntity, weightKg: Float, date: String? = null): Long {
        exerciseDao.markSavedWorkoutUsed(workout.id, System.currentTimeMillis())
        return logExercise(workout.name, workout.minutes, workout.met, weightKg, date)
    }

    /** Copy a logged workout onto another day, preserving its calories/MET/duration exactly. */
    suspend fun copyToDate(entry: ExerciseEntryEntity, dateIso: String): Long =
        exerciseDao.insert(entry.copy(id = 0, date = dateIso, timestamp = System.currentTimeMillis()))

    /** All logged workouts in a date range (for the AI overview's activity log). */
    suspend fun entriesInRange(from: String, to: String): List<ExerciseEntryEntity> =
        exerciseDao.getEntriesInRange(from, to)

    /** A logged exercise by id (for the detail / analysis screen). */
    suspend fun getById(id: Long): ExerciseEntryEntity? = exerciseDao.getById(id)

    /** Decode the saved coaching tips for an entry. */
    fun suggestionsFor(entry: ExerciseEntryEntity): List<String> {
        val raw = entry.suggestionsJson ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun delete(id: Long) = exerciseDao.delete(id)
}
