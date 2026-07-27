package com.fitpal.shared

/**
 * The wire contract between the FitPal phone app and the Wear OS companion. Kept in a module both
 * apps depend on so the message/data paths are single-sourced (never duplicated per side).
 *
 * - **Messages** (`MessageClient`) are one-shot commands the watch sends to the phone. Google Play
 *   Services cold-starts the phone's listener service to receive them, even if the app is dead.
 * - The **DataItem** at [PATH_STATS] is the synced today-snapshot the phone pushes to the watch;
 *   the watch renders it and feeds it to the tile + complications.
 */
object WearContract {

    // ---- Messages: watch -> phone ----

    /** Log plain water. Payload = the amount in millilitres as a decimal ASCII string (e.g. "250"). */
    const val PATH_LOG_WATER = "/fitpal/log_water"

    /** Analyse a spoken/typed meal on the phone. Payload = the raw description text (UTF-8). */
    const val PATH_DESCRIBE_MEAL = "/fitpal/describe_meal"

    /** Estimate a spoken/typed exercise on the phone. Payload = the raw description text (UTF-8). */
    const val PATH_DESCRIBE_EXERCISE = "/fitpal/describe_exercise"

    /** Ask the phone to compute + push a fresh [PATH_STATS] snapshot. No payload. */
    const val PATH_REQUEST_STATS = "/fitpal/request_stats"

    /**
     * Report the watch's own step count for a day, read from Wear OS Health Services on the watch
     * (so it works even when Samsung Health never writes watch steps into Health Connect).
     * Payload = "yyyy-MM-dd|steps" as ASCII, e.g. "2026-07-05|8421". The phone treats it as a
     * floor: the day's synced steps become max(Health Connect, this report).
     */
    const val PATH_LOG_STEPS = "/fitpal/log_steps"

    // ---- Data: phone -> watch ----

    /** DataItem path carrying the current-day [StatsSnapshot] as a DataMap. */
    const val PATH_STATS = "/fitpal/stats"

    /**
     * Capability the phone app advertises (declared in its wearable config) so the watch can
     * resolve which connected node hosts FitPal. Optional — the watch also falls back to sending
     * to every connected node.
     */
    const val CAPABILITY_PHONE_APP = "fitpal_phone_app"
}
