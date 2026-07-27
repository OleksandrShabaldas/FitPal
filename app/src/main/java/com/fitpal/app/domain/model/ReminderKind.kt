package com.fitpal.app.domain.model

/** Enabled state + time (minutes since midnight) for one reminder. */
data class ReminderState(val enabled: Boolean, val minutes: Int)

/**
 * The optional, user-configurable reminders the app can post. Each is an independent daily (or
 * weekly) alarm with its own on/off + time, scheduled by `ReminderManager` and fired through
 * `ReminderReceiver`. The two "overview" kinds also kick off AI generation when they fire.
 */
enum class ReminderKind(
    val key: String,
    val defaultEnabled: Boolean,
    val defaultMinutes: Int,
    /** Settings label + helper line. */
    val settingTitle: String,
    val settingDesc: String,
    /** Notification copy. */
    val notifTitle: String,
    val notifText: String,
    /** Fires once a week (on Sundays) instead of daily. */
    val weekly: Boolean = false
) {
    BREAKFAST("breakfast", false, 8 * 60, "Breakfast", "Remind me to log breakfast.",
        "Breakfast time", "Tap to log what you had for breakfast."),
    LUNCH("lunch", false, 13 * 60, "Lunch", "Remind me to log lunch.",
        "Lunch time", "Tap to log your lunch."),
    DINNER("dinner", false, 19 * 60, "Dinner", "Remind me to log dinner.",
        "Dinner time", "Tap to log your dinner."),
    WEIGHT("weight", false, 8 * 60, "Weigh-in", "Remind me to log my weight.",
        "Time to weigh in", "Tap to log today's weight."),
    DAILY_OVERVIEW("daily_overview", false, 21 * 60, "Daily AI overview",
        "Each evening, generate an AI overview of your day and notify me.",
        "Your daily overview is ready", "Tap to see how today went."),
    WEEKLY_OVERVIEW("weekly_overview", false, 19 * 60, "Weekly AI overview",
        "Every Sunday evening, generate an AI overview of your week and notify me.",
        "Your weekly overview is ready", "Tap to see how your week went.", weekly = true);

    /** True for the two reminders that generate an AI review when they fire. */
    val isOverview: Boolean get() = this == DAILY_OVERVIEW || this == WEEKLY_OVERVIEW
}
