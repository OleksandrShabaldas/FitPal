package com.fitpal.app.domain

/**
 * How a tracker (water, steps, caffeine, …) is doing right now, as one pill: a state and a short
 * sentence. Worked out in pure functions like [WaterPace] so a tracker's page, its Home card and its
 * widget can't disagree.
 */
enum class StatusKind { OK, CLOSE, OVER, NEUTRAL }

data class TrackerStatus(val kind: StatusKind, val text: String)

/** Whether a tracker's goal is something to reach (water, steps) or a limit to stay under (caffeine). */
enum class GoalKind { REACH, LIMIT }
