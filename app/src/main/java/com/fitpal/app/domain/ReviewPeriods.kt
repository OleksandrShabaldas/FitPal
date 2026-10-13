package com.fitpal.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Which week / month an AI overview should cover when you open it from Analytics.
 *
 * A monthly overview is a look back, so right after a month ends "this month" means the month that
 * just finished: opening it on 4 October used to review October's first four days (and cache that
 * thin overview for the rest of the month) instead of September. Likewise on a Monday, the week
 * overview is last week's. Past periods you've navigated back to are always reviewed as themselves.
 */
object ReviewPeriods {

    /** For this many days into a new month, the monthly overview still covers the month just ended. */
    const val MONTH_LOOKBACK_DAYS = 7

    /** The overview to open for a week or month, with the card's wording for it. */
    data class Target(
        /** The period key for the AI review screen ("2026-09", or the week's Monday date). */
        val key: String,
        /** "This month" / "Last month" / "This week" / "Last week". */
        val title: String,
        /** e.g. "Tap for your September overview". */
        val subtitle: String
    )

    fun month(anchor: LocalDate, today: LocalDate = LocalDate.now()): Target {
        val viewed = YearMonth.from(anchor)
        val current = YearMonth.from(today)
        val lookBack = viewed == current && today.dayOfMonth <= MONTH_LOOKBACK_DAYS
        val month = if (lookBack) current.minusMonths(1) else viewed
        val name = month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
            if (month.year != today.year) " ${month.year}" else ""
        return Target(
            key = month.toString(),
            title = when {
                lookBack -> "Last month"
                viewed == current -> "This month"
                else -> name
            },
            subtitle = if (viewed == current && !lookBack) "Tap for your monthly overview so far"
                else "Tap for your $name overview"
        )
    }

    fun week(anchor: LocalDate, today: LocalDate = LocalDate.now()): Target {
        val viewedStart = anchor.minusDays((anchor.dayOfWeek.value - 1).toLong())
        val currentStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val lookBack = viewedStart == currentStart && today.dayOfWeek == DayOfWeek.MONDAY
        val start = if (lookBack) currentStart.minusWeeks(1) else viewedStart
        val range = "${start.format(DAY_MONTH)} – ${start.plusDays(6).format(DAY_MONTH)}"
        return Target(
            key = start.format(DateTimeFormatter.ISO_LOCAL_DATE),
            title = when {
                lookBack -> "Last week"
                viewedStart == currentStart -> "This week"
                else -> "Week of ${start.format(DAY_MONTH)}"
            },
            subtitle = if (viewedStart == currentStart && !lookBack) "Tap for your 7-day overview"
                else "Tap for your overview of $range"
        )
    }

    private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
}
