package com.fitpal.app.domain.model

/**
 * The optional features (docs/redesign/06). Each has one master switch, and a feature that's off
 * shows nothing anywhere but the Features page and its own settings. Calories and macros, meals,
 * water, steps, weight and exercise are core: they can be moved off Home but never turned off.
 * The AI coach and the meal planner came with the Coach tab (docs/redesign/10); the planner needs the coach.
 */
enum class Feature(
    val id: String,
    val title: String,
    /** One line on what it does — the Features page and the setup's "what to track" grid. */
    val line: String,
    /** The settings category that holds its details (null = nothing more to set). */
    val settingsCategory: String?
) {
    FASTING("fasting", "Fasting", "An eating window, and a timer that keeps you to it", "fasting"),
    CAFFEINE("caffeine", "Caffeine", "What's still in you, and when it'll let you sleep", "caffeine"),
    LIMITS("limits", "Food limits", "A daily cap for treats, with a heads-up as you get close", "rules"),
    TRAIL("trail", "The Trail", "A garden that grows as you log, with challenges to claim", null),
    COACH("coach", "AI coach", "Daily and weekly overviews that ask about the why", "ai"),
    PLANNER("planner", "Meal planner", "Complete recipes that fit your day, to log or schedule", null);

    companion object {
        fun byId(id: String): Feature? = entries.firstOrNull { it.id == id }
    }
}

/**
 * What a feature's basic setup picks — one sheet, at most three controls, defaults filled in. Shared
 * by the Features page, Home's suggestion card and the first-run setup, so all three set a feature
 * up the same way. Only the fields of the feature being set up are used.
 */
data class FeatureSetup(
    val eatStartMin: Int = FastingPreset.SIXTEEN_EIGHT.eatStartMin,
    val eatEndMin: Int = FastingPreset.SIXTEEN_EIGHT.eatEndMin,
    val intensity: FastingIntensity = FastingIntensity.NORMAL,
    val caffeineLimitMg: Int = 400,
    val caffeineHalfLifeHours: Float = 5f,
    /** The food limits to turn on, each with its daily cap (kcal). */
    val limits: Map<DietaryRuleKind, Int> = DietaryRuleKind.entries.associateWith { it.defaultLimitKcal },
    /** AI coach: a Gemini key to add (blank = keep what's set; it works on-device without one). */
    val geminiKey: String = "",
    /** Meal planner: the food profile's two most important answers. */
    val diet: String = FoodProfile.DIET_NONE,
    val allergies: Set<String> = emptySet()
) {
    /** Whether this is enough to turn [feature] on (food limits need at least one picked). */
    fun canTurnOn(feature: Feature): Boolean = feature != Feature.LIMITS || limits.isNotEmpty()

    companion object {
        /** The window presets the setup offers, in the order shown. */
        val WINDOW_PRESETS = listOf(FastingPreset.FOURTEEN_TEN, FastingPreset.SIXTEEN_EIGHT, FastingPreset.EIGHTEEN_SIX)

        /** Caffeine half-life choices: (label, hours). 5 h is typical for adults. */
        val HALF_LIVES = listOf("Fast · 3 h" to 3f, "Typical · 5 h" to 5f, "Slow · 7 h" to 7f)
    }
}
