package com.fitpal.app.sync

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.fitpal.app.data.repository.ExerciseRepository
import com.fitpal.app.data.repository.StepRepository
import kotlinx.coroutines.runBlocking

/**
 * The single IPC surface of the Calistapp ⇄ FitPal bridge. See [FitPalSyncContract].
 *
 *  - `insert(exercise)` — Calistapp pushes a finished workout; we upsert it into `exercise_entries`
 *    keyed by the Calistapp session id (idempotent), keeping Calistapp's calories verbatim.
 *  - `query(steps?from&to)` — Calistapp pulls the day's steps + FitPal's already-trimmed
 *    step-calories.
 *
 * Security: exported, but every call must come from the Calistapp package (checked at runtime via
 * [getCallingPackage], which a ContentProvider reports reliably). We deliberately DON'T use a
 * custom `android:permission` — the two apps are signed with different keys, so a signature perm
 * can't work, and a `normal` perm brings install-order fragility. The package check is the guard.
 */
class FitPalSyncProvider : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncEntryPoint {
        fun exerciseRepository(): ExerciseRepository
        fun stepRepository(): StepRepository
    }

    private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(FitPalSyncContract.AUTHORITY, FitPalSyncContract.PATH_EXERCISE, MATCH_EXERCISE)
        addURI(FitPalSyncContract.AUTHORITY, FitPalSyncContract.PATH_STEPS, MATCH_STEPS)
    }

    // Fetched lazily (NOT in onCreate) so we never touch the Hilt graph before it's ready.
    private fun entryPoint(): SyncEntryPoint {
        val ctx = context!!.applicationContext
        return EntryPointAccessors.fromApplication(ctx, SyncEntryPoint::class.java)
    }

    override fun onCreate(): Boolean = true

    /** Reject anything that isn't Calistapp before it can touch our data. */
    private fun assertTrustedCaller() {
        val caller = callingPackage
        if (caller != FitPalSyncContract.CALISTAPP_PKG && caller != FitPalSyncContract.FITPAL_PKG) {
            throw SecurityException("FitPalSyncProvider: rejected caller $caller")
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        assertTrustedCaller()
        return when (matcher.match(uri)) {
            MATCH_STEPS -> {
                val from = uri.getQueryParameter(FitPalSyncContract.QUERY_FROM)
                val to = uri.getQueryParameter(FitPalSyncContract.QUERY_TO)
                if (from.isNullOrBlank() || to.isNullOrBlank()) return null
                val cursor = MatrixCursor(FitPalSyncContract.STEPS_COLUMNS)
                val rows = runBlocking { entryPoint().stepRepository().dailyStepsForRange(from, to) }
                rows.forEach { r ->
                    cursor.addRow(arrayOf<Any?>(r.date, r.steps, r.trimmedCalories, r.reductionPercent))
                }
                cursor
            }
            else -> null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        assertTrustedCaller()
        if (matcher.match(uri) != MATCH_EXERCISE || values == null) return null
        val externalId = values.getAsString(FitPalSyncContract.COL_EXTERNAL_ID) ?: return null
        val id = runBlocking {
            entryPoint().exerciseRepository().upsertFromCalistapp(
                externalId = externalId,
                date = values.getAsString(FitPalSyncContract.COL_DATE) ?: return@runBlocking -1L,
                name = values.getAsString(FitPalSyncContract.COL_NAME) ?: "Workout",
                minutes = values.getAsInteger(FitPalSyncContract.COL_MINUTES) ?: 0,
                met = values.getAsFloat(FitPalSyncContract.COL_MET) ?: 0f,
                calories = values.getAsFloat(FitPalSyncContract.COL_CALORIES) ?: 0f,
                detailsJson = values.getAsString(FitPalSyncContract.COL_DETAILS_JSON),
                startMs = values.getAsLong(FitPalSyncContract.COL_START_MS) ?: 0L,
                source = values.getAsString(FitPalSyncContract.COL_SOURCE)
                    ?: FitPalSyncContract.SOURCE_CALISTAPP
            )
        }
        if (id <= 0) return null
        return ContentUris.withAppendedId(FitPalSyncContract.EXERCISE_URI, id)
    }

    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?
    ): Int = 0 // Exercise upserts go through insert(); nothing else is updatable.

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun getType(uri: Uri): String? = when (matcher.match(uri)) {
        MATCH_EXERCISE -> "vnd.android.cursor.dir/vnd.fitpal.exercise"
        MATCH_STEPS -> "vnd.android.cursor.dir/vnd.fitpal.steps"
        else -> null
    }

    companion object {
        private const val MATCH_EXERCISE = 1
        private const val MATCH_STEPS = 2
    }
}
