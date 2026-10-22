package com.fitpal.app.domain.model

/**
 * Ready-made arrangements (Settings → Home & screens → Start from a preset): a Home layout, an Add
 * food layout and a bottom bar in one tap. Everything stays editable afterwards.
 */
enum class LayoutPreset(val label: String, val blurb: String) {
    ORIGINAL("Original", "FitPal as it ships."),
    MINIMAL("Minimal", "Just the ring and your meals."),
    QUICK_LOGGER("Quick logger", "Log in a tap — the camera is one press away."),
    DATA_NERD("Data nerd", "Every number on Home, small cards side by side."),
    COACH("Coach", "The AI's take first, then your day.");

    fun home(): List<WidgetSetting> {
        fun on(key: String, size: CardSize? = null, variant: String? = null) = WidgetSetting(key, true, size, variant)
        fun strip(key: String) = WidgetSetting(key, true, placement = TrackerPlacement.STRIP)
        val fastingMain = WidgetSetting("fasting", true, placement = TrackerPlacement.MAIN)
        val chosen: List<WidgetSetting> = when (this) {
            ORIGINAL -> return ScreenWidgets.defaultLayout(ScreenWidgets.HOME)
            // Fasting stays on the main card in every preset but Data nerd: hiding it would drop the
            // fasting ring. Trackers given a size are cards; the rest of the strip comes from "strip".
            MINIMAL -> listOf(on("hero", CardSize.M, "ring"), fastingMain, on("meals"))
            QUICK_LOGGER -> listOf(
                on("hero", CardSize.M, "numbers"), fastingMain, on(ScreenWidgets.STRIP),
                on("meals"), on("water", CardSize.S), on("weight", CardSize.S)
            )
            DATA_NERD -> listOf(
                on("hero", CardSize.L, "ring"), on(ScreenWidgets.STRIP),
                strip("limit_dessert"), strip("limit_fried"), strip("limit_drink"),
                on("macros", variant = "bars"),
                on("water", CardSize.S), on("steps", CardSize.S),
                on("exercise", CardSize.S), on("caffeine", CardSize.S),
                on("fasting", CardSize.S), on("weight", CardSize.S),
                on("ai_review", CardSize.M), on("meals")
            )
            COACH -> listOf(
                on("ai_review", CardSize.M), on("hero", CardSize.L), fastingMain, on(ScreenWidgets.STRIP),
                strip("caffeine"), strip("limit_dessert"), strip("limit_fried"), strip("limit_drink"),
                on("meals"), on("water", CardSize.S), on("steps", CardSize.S), on("weight", CardSize.M)
            )
        }
        // Everything not chosen is kept, hidden, so it can be switched back on.
        val keys = chosen.map { it.key }.toSet()
        return chosen + ScreenWidgets.defaultsFor(ScreenWidgets.HOME).filter { it.key !in keys }.map { WidgetSetting(it.key, false) }
    }

    fun addFood(): List<WidgetSetting> = when (this) {
        QUICK_LOGGER -> listOf(
            WidgetSetting("photo", true, CardSize.S), WidgetSetting("describe", true, CardSize.S),
            WidgetSetting("usuals", true),
            WidgetSetting("barcode", true, CardSize.S), WidgetSetting("gallery", true, CardSize.S),
            WidgetSetting("search", true, CardSize.M)
        ) + listOf("saved", "custom", "exercise", "weight").map { WidgetSetting(it, false) }
        else -> ScreenWidgets.defaultLayout(ScreenWidgets.ADD_FOOD)
    }

    fun nav(): NavConfig = when (this) {
        MINIMAL -> NavConfig(tabs = listOf(NavTab.HOME, NavTab.ANALYTICS, NavTab.SETTINGS))
        QUICK_LOGGER -> NavConfig(plus = PlusAction.CAMERA)
        DATA_NERD -> NavConfig(tabs = listOf(NavTab.HOME, NavTab.ANALYTICS, NavTab.TRAIL, NavTab.SETTINGS), labels = true)
        else -> NavConfig()
    }
}
