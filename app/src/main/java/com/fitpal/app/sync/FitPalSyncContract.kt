package com.fitpal.app.sync

import android.net.Uri

/**
 * The wire contract for the Calistapp ⇄ FitPal bridge.
 *
 * FitPal exposes ONE ContentProvider ([FitPalSyncProvider]) as the single IPC surface; Calistapp
 * is a pure client in both directions:
 *  - Calistapp → FitPal: inserts finished workouts into the [PATH_EXERCISE] path (they land in
 *    `exercise_entries` with [SOURCE_CALISTAPP] and Calistapp's own HR-based calories, de-duped by
 *    [COL_EXTERNAL_ID] = the Calistapp session id).
 *  - FitPal → Calistapp: Calistapp queries the [PATH_STEPS] path for a date range and gets back the
 *    day's steps + FitPal's ALREADY-TRIMMED step-calories (FitPal's reduction % applied), so
 *    Calistapp never has to know the formula.
 *
 * A wake broadcast ([ACTION_PULL_STEPS] → [CALISTAPP_RECEIVER]) lets FitPal nudge Calistapp to pull
 * end-of-day even when Calistapp hasn't been opened.
 *
 * ⚠️ KEEP IN SYNC with Calistapp's copy at
 * `com.calistapp.app.data.fitpal.FitPalContract` — these constant VALUES must stay byte-identical.
 * Only the package/class wrapper differs between the two apps.
 */
object FitPalSyncContract {

    const val AUTHORITY = "com.fitpal.app.sync"

    const val PATH_EXERCISE = "exercise"
    const val PATH_STEPS = "steps"

    val EXERCISE_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_EXERCISE")
    val STEPS_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_STEPS")

    // --- Exercise columns (Calistapp writes these) ---
    const val COL_EXTERNAL_ID = "externalId"   // Calistapp session id — the de-dupe key
    const val COL_DATE = "date"                // "YYYY-MM-DD"
    const val COL_NAME = "name"
    const val COL_MINUTES = "minutes"
    const val COL_MET = "met"                  // display only; FitPal keeps the calories below verbatim
    const val COL_CALORIES = "calories"        // Calistapp's authoritative HR-based total kcal
    const val COL_SOURCE = "source"
    const val COL_DETAILS_JSON = "detailsJson" // exercise breakdown + intensity, opaque to FitPal
    const val COL_START_MS = "startMs"

    // --- Steps columns (FitPal returns these) ---
    const val COL_STEPS = "steps"
    // COL_DATE / COL_CALORIES reused. COL_CALORIES here is POST-trim (reduction already applied).
    const val COL_REDUCTION_PERCENT = "reductionPercent"

    /** Steps query params: ?from=YYYY-MM-DD&to=YYYY-MM-DD (inclusive). */
    const val QUERY_FROM = "from"
    const val QUERY_TO = "to"

    val STEPS_COLUMNS = arrayOf(COL_DATE, COL_STEPS, COL_CALORIES, COL_REDUCTION_PERCENT)

    /** Marks an `exercise_entries` row as originating from Calistapp. */
    const val SOURCE_CALISTAPP = "calistapp"

    // --- Wake nudge (FitPal → Calistapp) ---
    const val ACTION_PULL_STEPS = "com.calistapp.sync.action.PULL_STEPS"

    // --- Package / component identity (for explicit intents + caller allowlisting) ---
    const val FITPAL_PKG = "com.fitpal.app"
    const val CALISTAPP_PKG = "com.calistapp"
    const val CALISTAPP_RECEIVER = "com.calistapp.app.sync.StepPullReceiver"
}
