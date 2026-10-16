package com.fitpal.app.domain

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How a tracker (water, steps, caffeine, …) is doing right now, as one pill: a state and a short
 * sentence. Worked out in pure functions here and in [WaterPace] so a tracker's page, its Home card
 * and its widget can't disagree.
 */
enum class StatusKind { OK, CLOSE, OVER, NEUTRAL }

data class TrackerStatus(val kind: StatusKind, val text: String)

/** Whether a tracker's goal is something to reach (water, steps) or a limit to stay under (caffeine). */
enum class GoalKind { REACH, LIMIT }

/** The trackers that have a page of their own. */
enum class TrackerId { WATER, STEPS, CAFFEINE, FASTING, WEIGHT, LIMITS }

/**
 * One tracker's state for a day, in the form every surface shows it: the number, the goal or limit,
 * a short form for chips, and the status pill. Built by the functions in [Trackers].
 */
data class TrackerSnapshot(
    val id: TrackerId,
    val value: Float,
    val goal: Float?,
    val goalKind: GoalKind,
    /** "6,240 steps", "1,450 ml". */
    val display: String,
    /** "6,240", "1.45 L" — for chips. */
    val shortDisplay: String,
    val status: TrackerStatus?
) {
    /** How far along the goal (or limit) the day is, 0..1; null without a goal. */
    val fraction: Float? get() = goal?.takeIf { it > 0f }?.let { (value / it).coerceIn(0f, 1f) }
}

/** The per-tracker snapshot rules. */
object Trackers {

    fun water(totalMl: Float, goalMl: Int, status: TrackerStatus?): TrackerSnapshot = TrackerSnapshot(
        id = TrackerId.WATER, value = totalMl, goal = goalMl.toFloat().takeIf { goalMl > 0 }, goalKind = GoalKind.REACH,
        display = "%,d ml".format(totalMl.roundToInt()),
        shortDisplay = if (totalMl >= 1000f) "%.2f L".format(totalMl / 1000f) else "${totalMl.roundToInt()} ml",
        status = status
    )

    /** Steps against the goal. [isToday] decides between "N to go" and how the day ended. */
    fun steps(steps: Int, goal: Int, isToday: Boolean): TrackerSnapshot {
        val status = when {
            goal <= 0 -> null
            steps >= goal -> TrackerStatus(StatusKind.OK, "Goal reached")
            isToday -> TrackerStatus(StatusKind.NEUTRAL, "%,d to go".format(goal - steps))
            else -> TrackerStatus(StatusKind.NEUTRAL, "${(steps * 100f / goal).roundToInt()}% of your goal")
        }
        return TrackerSnapshot(
            id = TrackerId.STEPS, value = steps.toFloat(), goal = goal.toFloat().takeIf { goal > 0 }, goalKind = GoalKind.REACH,
            display = "%,d steps".format(steps), shortDisplay = "%,d".format(steps), status = status
        )
    }

    /** Caffeine in the body now, against the day's limit; the pill is the bedtime verdict. */
    fun caffeine(nowMg: Float, dayTotalMg: Float, limitMg: Int, bedtime: TrackerStatus?): TrackerSnapshot = TrackerSnapshot(
        id = TrackerId.CAFFEINE, value = dayTotalMg, goal = limitMg.toFloat().takeIf { limitMg > 0 }, goalKind = GoalKind.LIMIT,
        display = "${nowMg.roundToInt()} mg", shortDisplay = "${nowMg.roundToInt()} mg", status = bedtime
    )

    /** The latest weight and how it moved this week, coloured by the user's goal. */
    fun weight(latestKg: Float?, weekChangeKg: Float?, onTrack: Boolean?): TrackerSnapshot {
        val status = when {
            latestKg == null -> TrackerStatus(StatusKind.NEUTRAL, "Log your first weigh-in")
            weekChangeKg == null -> TrackerStatus(StatusKind.NEUTRAL, "A few more weigh-ins show your trend")
            else -> {
                val change = (if (weekChangeKg > 0) "+" else if (weekChangeKg < 0) "−" else "") + "%.1f kg this week".format(abs(weekChangeKg))
                when (onTrack) {
                    true -> TrackerStatus(StatusKind.OK, "$change · on track")
                    false -> TrackerStatus(StatusKind.CLOSE, "$change · away from your goal")
                    null -> TrackerStatus(StatusKind.NEUTRAL, change)
                }
            }
        }
        return TrackerSnapshot(
            id = TrackerId.WEIGHT, value = latestKg ?: 0f, goal = null, goalKind = GoalKind.REACH,
            display = latestKg?.let { "%.1f kg".format(it) } ?: "— kg", shortDisplay = latestKg?.let { "%.1f".format(it) } ?: "—",
            status = status
        )
    }

    /** One food limit's day: kcal eaten in it against its cap. */
    fun limit(name: String, consumedKcal: Int, capKcal: Int): TrackerSnapshot {
        val left = capKcal - consumedKcal
        // The same "almost over" line the Home drawer and the pre-log gate use.
        val near = left <= maxOf(
            (capKcal * com.fitpal.app.domain.model.DietaryRuleStatus.NEAR_FRACTION).toInt(),
            com.fitpal.app.domain.model.DietaryRuleStatus.NEAR_MIN_KCAL
        )
        val status = when {
            capKcal <= 0 -> null
            consumedKcal >= capKcal -> TrackerStatus(
                StatusKind.OVER,
                if (consumedKcal == capKcal) "$name: at your cap" else "$name: over by ${consumedKcal - capKcal} kcal"
            )
            near -> TrackerStatus(StatusKind.CLOSE, "$name: $left kcal left today")
            else -> TrackerStatus(StatusKind.OK, "$name: $left kcal left")
        }
        return TrackerSnapshot(
            id = TrackerId.LIMITS, value = consumedKcal.toFloat(), goal = capKcal.toFloat().takeIf { capKcal > 0 }, goalKind = GoalKind.LIMIT,
            display = "$consumedKcal of $capKcal kcal", shortDisplay = "$consumedKcal/$capKcal", status = status
        )
    }
}
