package com.fitpal.app.domain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Every clock time FitPal shows goes through here, so the 24-hour setting (Settings → Appearance →
 * Clock) reaches all of them: labels, pickers, widgets and notifications. [use24h] is resolved by
 * `SettingsRepository` (the user's choice, or the phone's own format until they make one). It's
 * snapshot state, so screens showing a time redraw when it changes.
 */
object ClockFormat {
    var use24h: Boolean by mutableStateOf(false)

    /** "7:48 PM", or "19:48" on the 24-hour clock, for a minute of the day (wraps around midnight). */
    fun minuteOfDay(minutes: Int, h24: Boolean = use24h): String {
        val m = minutes.mod(DAY)
        val h = m / 60
        return if (h24) "%02d:%02d".format(h, m % 60)
        else "${((h + 11) % 12) + 1}:${"%02d".format(m % 60)} ${if (h < 12) "AM" else "PM"}"
    }

    /** An hour on its own, for chart axes: "5 PM", or "17:00". */
    fun hour(hour: Int, h24: Boolean = use24h): String {
        val h = hour.mod(24)
        return if (h24) "%02d:00".format(h) else "${((h + 11) % 12) + 1} ${if (h < 12) "AM" else "PM"}"
    }

    private const val DAY = 24 * 60
}
