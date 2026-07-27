package com.fitpal.app.domain.model

/** One widget's persisted state: its key and whether it's shown. List position = display order. */
data class WidgetSetting(val key: String, val enabled: Boolean = true)

/** A widget that can appear on a customizable screen (its key + a human label). */
data class WidgetDef(val key: String, val label: String)

/**
 * The screens whose widgets the user can reorder / show / hide, and the default widget set for
 * each (in the order they ship). Keep these keys in sync with the `when(key)` rendering in the
 * matching screen. The default order here reproduces each screen's original layout, so an
 * un-customized screen looks exactly as before.
 */
object ScreenWidgets {
    const val HOME = "home"
    const val ANALYTICS = "analytics"

    /** Screen id -> display title, for the customization UI. */
    val titles: Map<String, String> = mapOf(
        HOME to "Home",
        ANALYTICS to "Analytics"
    )

    /** The customizable screens, in the order shown in Settings. */
    val screens: List<String> = listOf(HOME, ANALYTICS)

    private val defaults: Map<String, List<WidgetDef>> = mapOf(
        HOME to listOf(
            WidgetDef("hero", "Calories & macros ring"),
            WidgetDef("ai_review", "AI daily overview"),
            WidgetDef("meals", "Meals"),
            WidgetDef("water", "Water"),
            WidgetDef("steps", "Steps"),
            WidgetDef("exercise", "Exercise"),
            WidgetDef("weight", "Log weight")
        ),
        ANALYTICS to listOf(
            WidgetDef("tiles", "Summary tiles"),
            WidgetDef("balance", "Balance score ring"),
            WidgetDef("heatmap", "Logged days"),
            WidgetDef("score", "Balance score chart"),
            WidgetDef("calories", "Calories"),
            WidgetDef("macros", "Macro balance"),
            WidgetDef("micros", "Vitamins & minerals"),
            WidgetDef("activity", "Activity"),
            WidgetDef("water", "Water"),
            WidgetDef("weight", "Weight"),
            WidgetDef("maintenance", "Maintenance estimate"),
            WidgetDef("averages", "Averages"),
            WidgetDef("ai", "AI reviews")
        )
    )

    fun defaultsFor(screenId: String): List<WidgetDef> = defaults[screenId] ?: emptyList()

    fun labelFor(screenId: String, key: String): String =
        defaultsFor(screenId).firstOrNull { it.key == key }?.label ?: key

    /**
     * Merge a saved layout with the code registry: keep the saved order for widgets that still
     * exist, drop ones that no longer do, and append any newly-added widgets (enabled) at the end.
     * Guarantees every current widget appears exactly once.
     */
    fun resolve(saved: List<WidgetSetting>, registry: List<WidgetDef>): List<WidgetSetting> {
        val known = registry.associateBy { it.key }
        val used = HashSet<String>()
        val result = ArrayList<WidgetSetting>(registry.size)
        for (s in saved) if (s.key in known && used.add(s.key)) result.add(s)
        for (def in registry) if (used.add(def.key)) result.add(WidgetSetting(def.key, true))
        return result
    }
}
