package com.fitpal.app.domain.model

/**
 * The kind of a meal-level coaching tip — picks the icon shown beside it. Kept small and concrete
 * so the tip is always actionable, never vague "eat healthier" advice.
 */
enum class TipType {
    /** Portion size — this meal is large for the day's remaining budget. */
    PORTION,
    /** Swap one component for a better-fitting one. */
    SWAP,
    /** Add something that rounds the meal out (fibre, protein, a vegetable). */
    ADDITION,
    /** When to eat it — timing relative to the rest of the day / training. */
    TIMING,
    /** How this meal moves the user toward their specific goal. */
    GOAL,
    /** The macro/plate balance of this meal. */
    BALANCE;

    companion object {
        /** Parse a model-supplied type string leniently; defaults to [BALANCE]. */
        fun fromString(s: String?): TipType =
            entries.firstOrNull { it.name.equals(s?.trim(), ignoreCase = true) } ?: BALANCE
    }
}

/**
 * One short, meal-aware coaching note shown as a green card on a logged meal — generated only when
 * genuinely useful (see [com.fitpal.app.ml.InsightsWorker]). Not the same as the per-item swaps /
 * energy / mood insights; this is a single sentence about *this plate given the day so far*.
 */
data class CoachingTip(
    val type: TipType,
    val message: String
)
