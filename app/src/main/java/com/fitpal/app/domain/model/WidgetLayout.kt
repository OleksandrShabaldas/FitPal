package com.fitpal.app.domain.model

/** How big a card is on its screen: S = half width (two sit side by side), M = full, L = full and roomy. */
enum class CardSize(val label: String) { S("Small"), M("Medium"), L("Large") }

/** One way a card can be drawn ("ring", "numbers", …) and its human label. */
data class CardVariant(val id: String, val label: String)

/**
 * One card's persisted state: its key, whether it's shown, and the size / style picked for it
 * (null = the card's default). List position = display order.
 */
data class WidgetSetting(
    val key: String,
    val enabled: Boolean = true,
    val size: CardSize? = null,
    val variant: String? = null
)

/** A card that can appear on a customizable screen: key, label, the sizes and styles it supports. */
data class WidgetDef(
    val key: String,
    val label: String,
    val sizes: List<CardSize> = listOf(CardSize.M),
    val variants: List<CardVariant> = emptyList(),
    /** Shown on a fresh install / when it's first added to the app. */
    val defaultOn: Boolean = true
) {
    val defaultSize: CardSize get() = sizes.first()
    val defaultVariant: String? get() = variants.firstOrNull()?.id
}

/** A card as it will be drawn: its definition plus the effective (validated) size and style. */
data class ResolvedCard(val def: WidgetDef, val setting: WidgetSetting) {
    val key: String get() = def.key
    val enabled: Boolean get() = setting.enabled
    val size: CardSize get() = setting.size?.takeIf { it in def.sizes } ?: def.defaultSize
    val variant: String? get() = setting.variant?.takeIf { v -> def.variants.any { it.id == v } } ?: def.defaultVariant
    /** Chart height multiplier for this card: Large cards get taller charts. */
    val chartScale: Float get() = if (size == CardSize.L) 1.6f else 1f
}

/**
 * Edits to one screen's layout (pure — the caller persists the result). Every operation starts from
 * the resolved list, so a card that's never been touched still has a place to move from.
 */
object LayoutEdits {
    fun move(list: List<WidgetSetting>, fromKey: String, toKey: String): List<WidgetSetting> {
        val from = list.indexOfFirst { it.key == fromKey }
        val to = list.indexOfFirst { it.key == toKey }
        if (from < 0 || to < 0 || from == to) return list
        return list.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun update(list: List<WidgetSetting>, key: String, change: (WidgetSetting) -> WidgetSetting): List<WidgetSetting> =
        list.map { if (it.key == key) change(it) else it }

    /** A hidden card brought back goes to the end, where it's easy to see. */
    fun show(list: List<WidgetSetting>, key: String): List<WidgetSetting> {
        val card = list.firstOrNull { it.key == key } ?: return list
        return list.filterNot { it.key == key } + card.copy(enabled = true)
    }
}

/**
 * The screens whose cards the user can reorder / show / hide / resize / restyle, and the default
 * card set for each (in the order they ship). Keep these keys in sync with the `when(key)` rendering
 * in the matching screen. The default order reproduces each screen's original layout, so an
 * un-customized screen looks exactly as before.
 */
object ScreenWidgets {
    const val HOME = "home"
    const val ANALYTICS = "analytics"
    const val ADD_FOOD = "add_food"

    /** Screen id -> display title, for the customization UI. */
    val titles: Map<String, String> = mapOf(
        HOME to "Home",
        ANALYTICS to "Analytics",
        ADD_FOOD to "Add food"
    )

    /** The customizable screens, in the order shown in Settings. */
    val screens: List<String> = listOf(HOME, ANALYTICS, ADD_FOOD)

    private val SM = listOf(CardSize.M, CardSize.S)
    private val ML = listOf(CardSize.M, CardSize.L)

    private val defaults: Map<String, List<WidgetDef>> = mapOf(
        HOME to listOf(
            WidgetDef(
                "hero", "Calories & macros ring", sizes = listOf(CardSize.L, CardSize.M),
                variants = listOf(CardVariant("ring", "Ring"), CardVariant("numbers", "Big number"))
            ),
            WidgetDef("ai_review", "AI daily overview", sizes = SM),
            WidgetDef("meals", "Meals", sizes = listOf(CardSize.L)),
            WidgetDef("water", "Water", sizes = SM),
            WidgetDef("steps", "Steps", sizes = SM),
            WidgetDef("exercise", "Exercise", sizes = SM),
            WidgetDef("weight", "Log weight", sizes = SM),
            // Off until picked: pieces of the hero, on their own.
            WidgetDef(
                "macros", "Macros", sizes = listOf(CardSize.M),
                variants = listOf(CardVariant("rings", "Rings"), CardVariant("bars", "Bars")), defaultOn = false
            ),
            WidgetDef("fasting", "Fasting timer", sizes = SM, defaultOn = false),
            WidgetDef("caffeine", "Caffeine", sizes = SM, defaultOn = false)
        ),
        ANALYTICS to listOf(
            WidgetDef("tiles", "Summary tiles"),
            WidgetDef("balance", "Balance score ring"),
            WidgetDef("heatmap", "Logged days"),
            WidgetDef("score", "Balance score chart", sizes = ML),
            WidgetDef("calories", "Calories", sizes = ML),
            WidgetDef("macros", "Macro balance"),
            WidgetDef("micros", "Vitamins & minerals"),
            WidgetDef("activity", "Activity", sizes = ML),
            WidgetDef("water", "Water", sizes = ML),
            WidgetDef("weight", "Weight", sizes = ML),
            WidgetDef("fasting", "Fasting"),
            WidgetDef("maintenance", "Maintenance estimate"),
            WidgetDef("averages", "Averages"),
            WidgetDef("ai", "AI reviews")
        ),
        ADD_FOOD to listOf(
            WidgetDef("usuals", "Your usuals"),
            WidgetDef("photo", "Take photo", sizes = listOf(CardSize.S, CardSize.M)),
            WidgetDef("gallery", "Choose photo", sizes = listOf(CardSize.S, CardSize.M)),
            WidgetDef("describe", "Describe to AI", sizes = listOf(CardSize.S, CardSize.M)),
            WidgetDef("barcode", "Scan barcode", sizes = listOf(CardSize.S, CardSize.M)),
            WidgetDef("saved", "Saved foods", sizes = listOf(CardSize.M, CardSize.S)),
            WidgetDef("search", "Search foods", sizes = listOf(CardSize.M, CardSize.S)),
            WidgetDef("custom", "Custom food", sizes = listOf(CardSize.M, CardSize.S)),
            WidgetDef("exercise", "Exercise", sizes = listOf(CardSize.S, CardSize.M)),
            WidgetDef("weight", "Weight", sizes = listOf(CardSize.S, CardSize.M))
        )
    )

    fun defaultsFor(screenId: String): List<WidgetDef> = defaults[screenId] ?: emptyList()

    fun defFor(screenId: String, key: String): WidgetDef? = defaultsFor(screenId).firstOrNull { it.key == key }

    fun labelFor(screenId: String, key: String): String = defFor(screenId, key)?.label ?: key

    /**
     * Merge a saved layout with the code registry: keep the saved order (and size/style) for cards
     * that still exist, drop ones that no longer do, and append any newly-added cards at the end
     * (shown or not per their [WidgetDef.defaultOn]). Guarantees every current card appears exactly once.
     */
    fun resolve(saved: List<WidgetSetting>, registry: List<WidgetDef>): List<WidgetSetting> {
        val known = registry.associateBy { it.key }
        val used = HashSet<String>()
        val result = ArrayList<WidgetSetting>(registry.size)
        for (s in saved) if (s.key in known && used.add(s.key)) result.add(s)
        for (def in registry) if (used.add(def.key)) result.add(WidgetSetting(def.key, def.defaultOn))
        return result
    }

    /** [resolve], then pair each setting with its definition (sizes and styles validated). */
    fun resolveCards(screenId: String, saved: List<WidgetSetting>): List<ResolvedCard> {
        val registry = defaultsFor(screenId)
        val byKey = registry.associateBy { it.key }
        return resolve(saved, registry).mapNotNull { s -> byKey[s.key]?.let { ResolvedCard(it, s) } }
    }

    /** The layout a fresh install gets. */
    fun defaultLayout(screenId: String): List<WidgetSetting> =
        defaultsFor(screenId).map { WidgetSetting(it.key, it.defaultOn) }

    /**
     * Group cards into display rows: two consecutive Small cards share a row; everything else gets
     * its own. A lone Small card (no Small neighbour) sits at half width on the left.
     */
    fun rows(cards: List<ResolvedCard>): List<List<ResolvedCard>> {
        val rows = ArrayList<List<ResolvedCard>>()
        var pending: ResolvedCard? = null
        for (c in cards) {
            if (c.size == CardSize.S) {
                val p = pending
                if (p != null) { rows.add(listOf(p, c)); pending = null } else pending = c
            } else {
                pending?.let { rows.add(listOf(it)); pending = null }
                rows.add(listOf(c))
            }
        }
        pending?.let { rows.add(listOf(it)) }
        return rows
    }
}
