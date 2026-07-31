package com.fitpal.app.domain

import java.time.LocalDate

/**
 * Logging-streak maths, shared by the Home streak chip and the Trail.
 */
object Streaks {

    /**
     * Current run of consecutive logged days ending today.
     *
     * If today isn't logged yet, we count the run up to *yesterday* — so the
     * streak doesn't read as broken before the day's first meal. The streak is
     * only truly 0 once both today and yesterday are missing.
     */
    fun current(loggedDates: Set<LocalDate>, today: LocalDate = LocalDate.now()): Int {
        var day = if (loggedDates.contains(today)) today else today.minusDays(1)
        var count = 0
        while (loggedDates.contains(day)) {
            count++
            day = day.minusDays(1)
        }
        return count
    }
}
