package com.fitpal.app.domain

import kotlin.math.roundToInt

/**
 * Water pace: where you'd usually be by now if the day's goal were spread evenly between waking up
 * and bedtime (the Profile's "Your day" times, 7:00 AM and 11:00 PM by default), and what to say
 * about it. Times are minutes of the day.
 */
object WaterPace {

    /** The ml you'd have by [nowMin] if [goalMl] were spread evenly over the waking day. */
    fun expectedBy(goalMl: Int, nowMin: Int, wakeMin: Int, bedMin: Int): Float {
        if (goalMl <= 0) return 0f
        val (wake, bed, now) = normalise(wakeMin, bedMin, nowMin)
        val frac = ((now - wake).toFloat() / (bed - wake)).coerceIn(0f, 1f)
        return goalMl * frac
    }

    /**
     * The pace pill for today. [glassMl] is the first quick-add amount — "one glass behind" reads
     * better than "250 ml behind". Null when there's no goal to pace against.
     */
    fun statusToday(totalMl: Float, goalMl: Int, nowMin: Int, wakeMin: Int, bedMin: Int, glassMl: Int): TrackerStatus? {
        if (goalMl <= 0) return null
        if (totalMl >= goalMl) return TrackerStatus(StatusKind.OK, "Goal reached")
        val (wake, _, now) = normalise(wakeMin, bedMin, nowMin)
        if (now < wake) return TrackerStatus(StatusKind.NEUTRAL, "Your day starts at ${clockLabel(wakeMin)}")
        val deficit = expectedBy(goalMl, nowMin, wakeMin, bedMin) - totalMl
        val glass = glassMl.coerceAtLeast(100)
        return when {
            deficit <= 150f -> TrackerStatus(StatusKind.OK, "On pace")
            deficit < glass * 1.5f -> TrackerStatus(StatusKind.CLOSE, "About one glass behind")
            deficit < glass * 2.5f -> TrackerStatus(StatusKind.CLOSE, "About two glasses behind")
            else -> TrackerStatus(StatusKind.CLOSE, "About ${"%,d".format(roundTo50(deficit))} ml behind")
        }
    }

    /** The pill for a day that's over: reached, or how much of the goal it got to. */
    fun statusPast(totalMl: Float, goalMl: Int): TrackerStatus? {
        if (goalMl <= 0) return null
        if (totalMl >= goalMl) return TrackerStatus(StatusKind.OK, "Goal reached")
        return TrackerStatus(StatusKind.NEUTRAL, "${(totalMl / goalMl * 100f).roundToInt()}% of your goal")
    }

    /**
     * Wake, bed and now on one timeline. A bedtime at or before waking (a night shift, or past
     * midnight) means the next day; "now" in the small hours belongs to the day that started yesterday.
     */
    private fun normalise(wakeMin: Int, bedMin: Int, nowMin: Int): Triple<Int, Int, Int> {
        val bed = if (bedMin <= wakeMin) bedMin + DAY else bedMin
        val now = if (nowMin < wakeMin && nowMin + DAY <= bed) nowMin + DAY else nowMin
        return Triple(wakeMin, bed, now)
    }

    private fun roundTo50(ml: Float): Int = ((ml / 50f).roundToInt() * 50).coerceAtLeast(50)

    /** "7:00 AM". */
    fun clockLabel(minuteOfDay: Int): String {
        val m = ((minuteOfDay % DAY) + DAY) % DAY
        val h = m / 60
        val hr12 = ((h + 11) % 12) + 1
        return "$hr12:${"%02d".format(m % 60)} ${if (h < 12) "AM" else "PM"}"
    }

    private const val DAY = 24 * 60
}
