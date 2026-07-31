package com.fitpal.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fitpal.app.data.local.FitPalDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backup / restore / wipe of the user's own data — meals, weights, steps,
 * exercises, trail progress and saved foods. Deliberately EXCLUDES the bundled USDA food
 * database (`usda_foods`), which can be hundreds of MB and is re-downloadable.
 *
 * Export/import use a generic cursor↔JSON dump so they don't need per-table code.
 */
@Singleton
class DataManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: FitPalDatabase
) {
    private val userTables = listOf(
        "meal_logs", "meal_log_items", "weight_entries", "step_entries",
        "exercise_entries", "gallery_foods", "gallery_ingredients",
        "gallery_categories", "saved_workouts",
        "trail_state", "trail_projects", "trail_unlocks", "challenges", "ai_reviews"
    )

    /** Write all user data as JSON to [uri]. Returns true on success. */
    suspend fun export(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = database.openHelper.writableDatabase
            val root = JSONObject().apply {
                put("app", "FitPal")
                put("schema", 11)
                put("exportedAt", System.currentTimeMillis())
            }
            for (table in userTables) root.put(table, dumpTable(db, table))
            // Custom barcode foods live in usda_foods (which we otherwise skip) — back up just those.
            root.put("custom_foods", dumpQuery(db, "SELECT * FROM usda_foods WHERE foodCategory = 'Custom'"))
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(root.toString().toByteArray(Charsets.UTF_8))
            } ?: return@withContext false
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Replace user data with the contents of the JSON at [uri]. Returns true on success. */
    suspend fun import(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: return@withContext false
            val root = JSONObject(text)
            val db = database.openHelper.writableDatabase
            db.beginTransaction()
            try {
                for (table in userTables) {
                    val rows = root.optJSONArray(table) ?: continue
                    db.execSQL("DELETE FROM $table")
                    for (i in 0 until rows.length()) {
                        val obj = rows.optJSONObject(i) ?: continue
                        db.insert(table, SQLiteDatabase.CONFLICT_REPLACE, rowToValues(obj))
                    }
                }
                // Custom barcode foods: upsert into usda_foods WITHOUT wiping the food table.
                root.optJSONArray("custom_foods")?.let { rows ->
                    for (i in 0 until rows.length()) {
                        val obj = rows.optJSONObject(i) ?: continue
                        db.insert("usda_foods", SQLiteDatabase.CONFLICT_REPLACE, rowToValues(obj))
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Delete all user data (keeps the downloaded food database). Goes through the
     * Room DAOs so every observing screen (Home, Analytics, Trail…) refreshes at once.
     */
    suspend fun clearUserData() = withContext(Dispatchers.IO) {
        database.mealLogDao().clearAllMealItems()
        database.mealLogDao().clearAllMealLogs()
        database.weightDao().clearAll()
        database.stepDao().clearAll()
        database.exerciseDao().clearAll()
        database.exerciseDao().clearAllSavedWorkouts()
        database.galleryDao().clearAllIngredients()
        database.galleryDao().clearAllFoods()
        database.galleryDao().clearAllCategories()
        database.trailDao().clearState()
        database.trailDao().clearProjects()
        database.trailDao().clearUnlocks()
        database.challengeDao().clearAll()
        database.aiReviewDao().clearAll()
    }

    private fun dumpTable(db: SupportSQLiteDatabase, table: String): JSONArray =
        dumpQuery(db, "SELECT * FROM $table")

    private fun dumpQuery(db: SupportSQLiteDatabase, sql: String): JSONArray {
        val arr = JSONArray()
        db.query(sql).use { c ->
            while (c.moveToNext()) {
                val row = JSONObject()
                for (i in 0 until c.columnCount) {
                    val name = c.getColumnName(i)
                    when (c.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> row.put(name, JSONObject.NULL)
                        Cursor.FIELD_TYPE_INTEGER -> row.put(name, c.getLong(i))
                        Cursor.FIELD_TYPE_FLOAT -> row.put(name, c.getDouble(i))
                        else -> row.put(name, c.getString(i))
                    }
                }
                arr.put(row)
            }
        }
        return arr
    }

    private fun rowToValues(obj: JSONObject): ContentValues {
        val cv = ContentValues()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (obj.isNull(k)) {
                cv.putNull(k)
                continue
            }
            when (val v = obj.get(k)) {
                is Int -> cv.put(k, v)
                is Long -> cv.put(k, v)
                is Double -> cv.put(k, v)
                is Boolean -> cv.put(k, if (v) 1 else 0)
                else -> cv.put(k, v.toString())
            }
        }
        return cv
    }
}
