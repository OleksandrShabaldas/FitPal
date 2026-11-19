package com.fitpal.app.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Finished weeks and months that never got an AI overview (the 17 Nov review, #3): the Coach tab lists them
 * under "Skipped" with "✦ Write" and "×". A week is Monday to Sunday and counts once it has ended; a month
 * once it has ended. Only periods with something to review (any food or drink but plain water — the same
 * rule as the automatic overview, [OverviewRule]), with no overview written and not dismissed, within the
 * last [months] months. The periods the "This week" / "This month" rows point at right now
 * ([ReviewPeriods]) are left out — they already have a row. Days are never listed (the user's choice).
 * Pure, so it's unit-tested.
 */
object SkippedReviews {

    /** How far back the list looks (the user's pick: three months). */
    const val LOOKBACK_MONTHS = 3L

    data class Skipped(
        /** "weekly" or "monthly" — the overview screen's period. */
        val period: String,
        /** The overview's period key: the week's Monday ("2026-11-03") or the month ("2026-10"). */
        val key: String,
        /** "Week of 3 Nov" / "October". */
        val title: String,
        /** "3–9 Nov · 6 days logged" / "26 days logged". */
        val subtitle: String,
        /** The last day of the period, for ordering (newest first). */
        val end: LocalDate
    ) {
        /** The id a dismissal is remembered by ("weekly|2026-11-03"). */
        val id: String get() = "$period|$key"
    }

    /**
     * Newest first. [loggedDays] = the days with something to review; [written] and [dismissed] are ids in
     * the [Skipped.id] form.
     */
    fun find(
        today: LocalDate,
        loggedDays: Set<LocalDate>,
        written: Set<String>,
        dismissed: Set<String>,
        months: Long = LOOKBACK_MONTHS
    ): List<Skipped> {
        val since = today.minusMonths(months)
        val thisWeekRow = ReviewPeriods.week(today, today).key
        val thisMonthRow = ReviewPeriods.month(today, today).key
        val out = mutableListOf<Skipped>()

        // Weeks: every Monday-to-Sunday that ended before today and started on or after the lookback.
        var monday = since.minusDays((since.dayOfWeek.value - 1).toLong())
        if (monday.isBefore(since)) monday = monday.plusWeeks(1)
        while (monday.plusDays(6).isBefore(today)) {
            val sunday = monday.plusDays(6)
            val key = monday.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val days = loggedDays.count { !it.isBefore(monday) && !it.isAfter(sunday) }
            val id = "weekly|$key"
            if (days > 0 && key != thisWeekRow && id !in written && id !in dismissed) {
                out += Skipped("weekly", key, "Week of ${monday.format(DAY_MONTH)}", "${weekRange(monday, sunday)} · ${daysLogged(days)}", sunday)
            }
            monday = monday.plusWeeks(1)
        }

        // Months: every month that has ended and ended on or after the lookback.
        var month = YearMonth.from(since)
        val current = YearMonth.from(today)
        while (month.isBefore(current)) {
            val end = month.atEndOfMonth()
            if (!end.isBefore(since)) {
                val key = month.toString()
                val days = loggedDays.count { YearMonth.from(it) == month }
                val id = "monthly|$key"
                if (days > 0 && key != thisMonthRow && id !in written && id !in dismissed) {
                    val name = month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                        if (month.year != today.year) " ${month.year}" else ""
                    out += Skipped("monthly", key, name, daysLogged(days), end)
                }
            }
            month = month.plusMonths(1)
        }
        return out.sortedWith(compareByDescending<Skipped> { it.end }.thenBy { it.period })
    }

    private fun daysLogged(n: Int) = if (n == 1) "1 day logged" else "$n days logged"

    /** "3–9 Nov", or "27 Oct – 2 Nov" across a month's end. */
    private fun weekRange(from: LocalDate, to: LocalDate): String =
        if (from.month == to.month) "${from.dayOfMonth}–${to.format(DAY_MONTH)}"
        else "${from.format(DAY_MONTH)} – ${to.format(DAY_MONTH)}"

    private val DAY_MONTH: DateTimeFormatter get() = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
}
