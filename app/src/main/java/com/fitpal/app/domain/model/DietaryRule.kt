package com.fitpal.app.domain.model

/**
 * A **dietary rule**: a food category with a daily calorie cap. When you approach or pass the cap,
 * Home shows a quiet line, logging a matching food asks first (optional), and you get a one-off
 * notification (optional). Modelled on [FastingSchedule] — a small config the UI reads; how much
 * you've had *today* is derived from the tagged logged items, not stored here.
 *
 * Three rules ship, all the same shape (a kcal/day cap): dessert, fried/fast food, and sugary
 * drinks. A single food can match several at once (a milkshake is a dessert AND a sugary drink),
 * so a logged item carries a **set** of these tags — see [DietaryRuleKind.tagsToDb].
 */
enum class DietaryRuleKind(
    /** Stable id — the DB tag token and the settings key. **Never change these** (stored data). */
    val id: String,
    val title: String,
    /** One-line description shown in Settings. */
    val blurb: String,
    val defaultLimitKcal: Int,
    /**
     * High-confidence words that make a food an obvious member. Used as the **offline fallback**
     * when the online classifier can't run (no key / offline / out of quota). Kept deliberately
     * precise — the online model handles the fuzzy cases (and the user's own "what counts" note).
     */
    val heuristicWords: List<String>
) {
    DESSERT(
        id = "dessert",
        title = "Dessert budget",
        blurb = "A daily calorie limit for sweets and desserts.",
        defaultLimitKcal = 400,
        heuristicWords = listOf(
            "cake", "cookie", "brownie", "ice cream", "icecream", "gelato", "chocolate bar",
            "candy", "donut", "doughnut", "pastry", "cheesecake", "pudding", "cupcake",
            "tiramisu", "macaron", "dessert", "sweets", "mousse", "fudge", "éclair", "eclair",
            "churro", "baklava", "waffle", "croissant", "sundae"
        )
    ),
    FRIED(
        id = "fried",
        title = "Fried & fast food",
        blurb = "A daily calorie limit for fried, fast and ultra-processed food.",
        defaultLimitKcal = 600,
        heuristicWords = listOf(
            "fried", "fries", "nugget", "tempura", "deep-fried", "deep fried", "fast food",
            "cheeseburger", "hamburger", "pizza", "hot dog", "hotdog", "fried chicken",
            "kfc", "mcdonald", "burger king", "instant noodle", "crisps", "onion ring",
            "corn dog", "schnitzel", "chicken wings", "kebab"
        )
    ),
    SUGARY_DRINK(
        id = "drink",
        title = "Sugary drinks",
        blurb = "A daily calorie limit for soft drinks, juice and sweet coffees.",
        defaultLimitKcal = 300,
        heuristicWords = listOf(
            "soda", "cola", "coke", "pepsi", "sprite", "fanta", "lemonade", "energy drink",
            "red bull", "monster", "milkshake", "frappuccino", "soft drink", "mountain dew",
            "gatorade", "sweet tea", "fruit juice", "orange juice", "apple juice", "slushie"
        )
    );

    /** The `LIKE` pattern that matches a logged item tagged with this kind (comma-wrapped column). */
    val likePattern: String get() = "%,$id,%"

    companion object {
        fun fromId(id: String?): DietaryRuleKind? = entries.firstOrNull { it.id == id }

        /**
         * DB value for a set of tags: comma-wrapped ("`,dessert,drink,`") so a `LIKE '%,dessert,%'`
         * can't false-match a substring. Empty set → null (stored as no tags).
         */
        fun tagsToDb(kinds: Set<DietaryRuleKind>): String? =
            if (kinds.isEmpty()) null
            else kinds.joinToString(separator = ",", prefix = ",", postfix = ",") { it.id }

        /** Parse a comma-wrapped tag column back to the set of kinds it names. */
        fun tagsFromDb(value: String?): Set<DietaryRuleKind> =
            value?.split(",").orEmpty()
                .mapNotNull { fromId(it.trim().takeIf(String::isNotEmpty)) }
                .toSet()
    }
}

/**
 * The user's config for one [DietaryRuleKind]. Stored per-kind in `SettingsRepository`, the same way
 * the fasting schedule is. [definition] is an optional free-text tuner ("fruit never counts") fed to
 * the classifier so the AI's idea of the category matches the user's.
 */
data class DietaryRule(
    val kind: DietaryRuleKind,
    val enabled: Boolean = false,
    val dailyLimitKcal: Int = kind.defaultLimitKcal,
    /** Ask before logging a matching food once you're near/over the cap. */
    val warnOnLog: Boolean = true,
    /** Post a one-off notification the moment the cap is passed. */
    val notify: Boolean = true,
    val definition: String = ""
)

/**
 * Where a rule stands **today**: how much of the capped category you've eaten vs the cap. Built by
 * pairing a [DietaryRule] with the summed calories of its tagged items — see the Home view model.
 */
data class DietaryRuleStatus(
    val kind: DietaryRuleKind,
    val consumedKcal: Int,
    val limitKcal: Int,
    /** Whether logging is gated for this rule (mirrors [DietaryRule.warnOnLog]). */
    val warnOnLog: Boolean = true
) {
    val remainingKcal: Int get() = limitKcal - consumedKcal
    val fraction: Float get() = if (limitKcal <= 0) 0f else consumedKcal.toFloat() / limitKcal
    val isOver: Boolean get() = consumedKcal >= limitKcal && limitKcal > 0
    /** "Almost over": within 15% (or 50 kcal, whichever is larger) of the cap, but not yet over. */
    val isNear: Boolean get() = !isOver && limitKcal > 0 &&
        remainingKcal <= maxOf((limitKcal * NEAR_FRACTION).toInt(), NEAR_MIN_KCAL)

    /** True once the rule matters enough to surface on Home / gate a log. */
    val isRelevant: Boolean get() = isOver || isNear

    companion object {
        const val NEAR_FRACTION = 0.15f
        const val NEAR_MIN_KCAL = 50
    }
}

/**
 * A pre-log warning the gate raises: the rule about to be broken, where the day stands, and whether
 * it's already over (vs merely near / about to cross). UI-agnostic data — the dialog builds the copy.
 */
data class DietaryWarning(
    val kind: DietaryRuleKind,
    val consumedKcal: Int,
    val limitKcal: Int,
    val alreadyOver: Boolean
)
