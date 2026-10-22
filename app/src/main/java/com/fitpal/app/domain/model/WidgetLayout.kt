package com.fitpal.app.domain.model

/** How big a card is on its screen: S = half width (two sit side by side), M = full, L = full and roomy. */
enum class CardSize(val label: String) { S("Small"), M("Medium"), L("Large") }

/**
 * Where a tracker (water, steps, caffeine, fasting, weight, each food limit) shows on Home
 * (docs/redesign/05): on the main card (fasting only), as a card, a small card, in the strip, or
 * not on Home. OFF_HOME is the card being hidden — the placement it had is kept, so switching it
 * back on returns it there.
 */
enum class TrackerPlacement(val label: String) {
    MAIN("Main card"), CARD_M("Card"), CARD_S("Small"), STRIP("Strip"), OFF_HOME("Off Home")
}

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
    val variant: String? = null,
    /** For tracker cards: where it shows (null = from [size], else the card's default). */
    val placement: TrackerPlacement? = null
)

/** A card that can appear on a customizable screen: key, label, the sizes and styles it supports. */
data class WidgetDef(
    val key: String,
    val label: String,
    val sizes: List<CardSize> = listOf(CardSize.M),
    val variants: List<CardVariant> = emptyList(),
    /** Shown on a fresh install / when it's first added to the app. */
    val defaultOn: Boolean = true,
    /** Non-empty for trackers: where on Home it may go (OFF_HOME is always allowed, as hiding). */
    val placements: List<TrackerPlacement> = emptyList(),
    val defaultPlacement: TrackerPlacement? = null
) {
    val isTracker: Boolean get() = placements.isNotEmpty()
    val defaultSize: CardSize get() = sizes.first()
    val defaultVariant: String? get() = variants.firstOrNull()?.id
}

/** A card as it will be drawn: its definition plus the effective (validated) size and style. */
data class ResolvedCard(val def: WidgetDef, val setting: WidgetSetting) {
    val key: String get() = def.key
    val enabled: Boolean get() = setting.enabled
    val size: CardSize get() = when (placement) {
        TrackerPlacement.CARD_S -> CardSize.S
        TrackerPlacement.CARD_M -> CardSize.M
        else -> setting.size?.takeIf { it in def.sizes } ?: def.defaultSize
    }

    /**
     * Where a tracker shows (null for other cards). A layout saved before placements existed has
     * only a size, so S → small card, M/L → card.
     */
    val placement: TrackerPlacement?
        get() {
            if (!def.isTracker) return null
            if (!setting.enabled) return TrackerPlacement.OFF_HOME
            setting.placement?.takeIf { it in def.placements }?.let { return it }
            val fromSize = when (setting.size) {
                CardSize.S -> TrackerPlacement.CARD_S
                CardSize.M, CardSize.L -> TrackerPlacement.CARD_M
                null -> null
            }
            return fromSize?.takeIf { it in def.placements } ?: def.defaultPlacement ?: def.placements.first()
        }

    /** Drawn as a card of its own (not in the strip, not on the main card, not hidden). */
    val isOwnCard: Boolean get() = enabled && (placement == null || placement == TrackerPlacement.CARD_M || placement == TrackerPlacement.CARD_S)
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

    /**
     * Put a tracker somewhere: OFF_HOME hides it (keeping its placement for when it comes back);
     * anything else shows it there, with the matching size for cards.
     */
    fun place(list: List<WidgetSetting>, key: String, placement: TrackerPlacement): List<WidgetSetting> =
        update(list, key) { s ->
            when (placement) {
                TrackerPlacement.OFF_HOME -> s.copy(enabled = false)
                TrackerPlacement.CARD_S -> s.copy(enabled = true, placement = placement, size = CardSize.S)
                TrackerPlacement.CARD_M -> s.copy(enabled = true, placement = placement, size = CardSize.M)
                else -> s.copy(enabled = true, placement = placement)
            }
        }

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

    /** The Home card that holds every tracker placed in the strip. */
    const val STRIP = "strip"

    private val SM = listOf(CardSize.M, CardSize.S)
    private val CARD_PLACES = listOf(TrackerPlacement.CARD_M, TrackerPlacement.CARD_S, TrackerPlacement.STRIP)
    private val LIMIT_PLACES = listOf(TrackerPlacement.CARD_S, TrackerPlacement.STRIP)
    private val ML = listOf(CardSize.M, CardSize.L)

    private val defaults: Map<String, List<WidgetDef>> = mapOf(
        // The order is a new user's Home (docs/redesign/05): the strip right under the hero, water and
        // steps as two small cards, then the overview, meals and exercise. Trackers in the strip or on
        // the main card take no row of their own; their position is their order in the strip.
        HOME to listOf(
            WidgetDef(
                "hero", "Calories & macros ring", sizes = listOf(CardSize.L, CardSize.M),
                variants = listOf(CardVariant("ring", "Ring"), CardVariant("numbers", "Big number"))
            ),
            WidgetDef(
                STRIP, "Today strip", sizes = listOf(CardSize.M),
                variants = listOf(CardVariant("rings", "Rings"), CardVariant("numbers", "Numbers"))
            ),
            WidgetDef("water", "Water", sizes = SM, placements = CARD_PLACES, defaultPlacement = TrackerPlacement.CARD_S),
            WidgetDef("steps", "Steps", sizes = SM, placements = CARD_PLACES, defaultPlacement = TrackerPlacement.CARD_S),
            WidgetDef("ai_review", "AI daily overview", sizes = SM),
            WidgetDef("meals", "Meals", sizes = listOf(CardSize.L)),
            WidgetDef("exercise", "Exercise", sizes = SM),
            WidgetDef("caffeine", "Caffeine", sizes = SM, placements = CARD_PLACES, defaultPlacement = TrackerPlacement.STRIP),
            WidgetDef("limit_dessert", "Dessert limit", sizes = listOf(CardSize.S), placements = LIMIT_PLACES, defaultPlacement = TrackerPlacement.STRIP),
            WidgetDef("limit_fried", "Fried food limit", sizes = listOf(CardSize.S), placements = LIMIT_PLACES, defaultPlacement = TrackerPlacement.STRIP),
            WidgetDef("limit_drink", "Sugary drinks limit", sizes = listOf(CardSize.S), placements = LIMIT_PLACES, defaultPlacement = TrackerPlacement.STRIP),
            WidgetDef("weight", "Weight", sizes = SM, placements = CARD_PLACES, defaultPlacement = TrackerPlacement.STRIP),
            WidgetDef(
                "fasting", "Fasting", sizes = SM,
                placements = listOf(TrackerPlacement.MAIN) + CARD_PLACES, defaultPlacement = TrackerPlacement.MAIN
            ),
            // Off until picked: a piece of the hero, on its own.
            WidgetDef(
                "macros", "Macros", sizes = listOf(CardSize.M),
                variants = listOf(CardVariant("rings", "Rings"), CardVariant("bars", "Bars")), defaultOn = false
            )
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

    /**
     * Home as every install had it before the strip (2.8 and earlier), for a user who never
     * changed it — so the move to the strip keeps what they were used to.
     */
    private val LEGACY_HOME: List<WidgetSetting> =
        listOf("hero", "ai_review", "meals", "water", "steps", "exercise", "weight").map { WidgetSetting(it) } +
            listOf("macros", "fasting", "caffeine").map { WidgetSetting(it, enabled = false) }

    /**
     * A Home layout saved before the strip (docs/redesign/05) → the same Home with placements: cards
     * keep their order, visibility and size; what the hero's drawer held (food limits, caffeine)
     * becomes the strip, right under the hero, where the drawer was; fasting stays on the main card
     * unless its own card was on. Pass the saved list — empty for a user who never customized.
     * A layout that already has the strip is returned as it is.
     */
    fun migrateHomeToStrip(saved: List<WidgetSetting>): List<WidgetSetting> {
        if (saved.any { it.key == STRIP }) return saved
        val asCard: (WidgetSetting) -> TrackerPlacement = { s ->
            if (s.size == CardSize.S) TrackerPlacement.CARD_S else TrackerPlacement.CARD_M
        }
        val moved = saved.ifEmpty { LEGACY_HOME }.map { s ->
            when (s.key) {
                "caffeine" -> if (s.enabled) s.copy(placement = asCard(s)) else s.copy(enabled = true, placement = TrackerPlacement.STRIP)
                "fasting" -> if (s.enabled) s.copy(placement = asCard(s)) else s.copy(enabled = true, placement = TrackerPlacement.MAIN)
                "water", "steps", "weight" -> s.copy(placement = asCard(s))
                else -> s
            }
        }
        val strip = listOf(WidgetSetting(STRIP)) +
            listOf("limit_dessert", "limit_fried", "limit_drink").map { WidgetSetting(it, placement = TrackerPlacement.STRIP) }
        val at = moved.indexOfFirst { it.key == "hero" } + 1
        return moved.take(at) + strip + moved.drop(at)
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
