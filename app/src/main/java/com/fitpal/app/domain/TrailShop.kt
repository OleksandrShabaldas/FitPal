package com.fitpal.app.domain

import kotlin.random.Random

/**
 * Where ⭐ goes besides keystone projects (GAME_DESIGN.md §4, phase D).
 *
 * Two sinks, deliberately different in feel:
 * - **Scene themes** — a sure thing. You know exactly what you're buying, and the diorama
 *   visibly changes.
 * - **Cases** — a gamble that feeds the **curio collection**, which is the
 *   "collect and complete" motivator rehomed from the old garden.
 *
 * Cosmetics are all code-drawn (palettes), so none of this needs art assets.
 */

/** A look for the diorama. Colours are resolved in the UI layer, not here. */
data class SceneTheme(
    val id: String,
    val name: String,
    val blurb: String,
    val cost: Int
)

object ThemeCatalog {
    const val DEFAULT_ID = "meadow"

    val ALL: List<SceneTheme> = listOf(
        SceneTheme("meadow", "Meadow", "Green and gold. Where you started.", 0),
        SceneTheme("dusk", "Dusk", "Violet light and warm amber lanterns.", 220),
        SceneTheme("autumn", "Autumn", "Rust, ochre and low afternoon sun.", 260),
        SceneTheme("frost", "Frost", "Pale blue, still air, everything crisp.", 320),
        SceneTheme("moonlit", "Moonlit", "Silver and deep blue. Very quiet.", 380),
        SceneTheme("ember", "Ember", "Banked fire reds against the dark.", 460)
    )

    fun byId(id: String): SceneTheme = ALL.firstOrNull { it.id == id } ?: ALL.first()
}

enum class CurioRarity(val label: String, val weight: Float) {
    COMMON("Common", 0.50f),
    UNCOMMON("Uncommon", 0.30f),
    RARE("Rare", 0.15f),
    LEGENDARY("Legendary", 0.05f)
}

/** Something you find along the trail. Pure collectible — no gameplay effect. */
data class Curio(
    val id: String,
    val name: String,
    val glyph: String,
    val rarity: CurioRarity
)

object CurioCatalog {
    val ALL: List<Curio> = listOf(
        Curio("pebble", "Smooth pebble", "🪨", CurioRarity.COMMON),
        Curio("pinecone", "Pinecone", "🌰", CurioRarity.COMMON),
        Curio("feather", "Grey feather", "🪶", CurioRarity.COMMON),
        Curio("acorn", "Acorn", "🌱", CurioRarity.COMMON),
        Curio("shell", "Fossil shell", "🐚", CurioRarity.UNCOMMON),
        Curio("key", "Brass key", "🗝️", CurioRarity.UNCOMMON),
        Curio("float", "Glass float", "🔮", CurioRarity.UNCOMMON),
        Curio("compass", "Old compass", "🧭", CurioRarity.RARE),
        Curio("map", "Map fragment", "🗺️", CurioRarity.RARE),
        Curio("locket", "Silver locket", "📿", CurioRarity.RARE),
        Curio("starchart", "Star chart", "✴️", CurioRarity.LEGENDARY),
        Curio("lantern", "Wayfarer's lantern", "🏮", CurioRarity.LEGENDARY)
    )

    fun byId(id: String): Curio? = ALL.firstOrNull { it.id == id }

    /** Weighted pick, with rarer things more likely from a better case. */
    fun roll(rareBoost: Float, rng: Random = Random.Default): Curio {
        val weights = CurioRarity.entries.associateWith { r ->
            when (r) {
                CurioRarity.COMMON -> r.weight * (1f - rareBoost * 0.6f)
                CurioRarity.UNCOMMON -> r.weight
                CurioRarity.RARE -> r.weight * (1f + rareBoost)
                CurioRarity.LEGENDARY -> r.weight * (1f + rareBoost * 2f)
            }
        }
        val total = weights.values.sum()
        var pick = rng.nextFloat() * total
        val tier = weights.entries.firstOrNull { (_, w) -> pick -= w; pick <= 0f }?.key
            ?: CurioRarity.COMMON
        val pool = ALL.filter { it.rarity == tier }.ifEmpty { ALL }
        return pool.random(rng)
    }
}

/** A buyable case. */
data class CasePack(
    val id: String,
    val name: String,
    val blurb: String,
    val cost: Int,
    /** 0 = base odds; higher shifts curio rolls toward rare and themes more likely. */
    val rareBoost: Float,
    val themeChance: Float
)

object CaseCatalog {
    val ALL: List<CasePack> = listOf(
        CasePack(
            id = "pack", name = "Traveller's pack",
            blurb = "Something small you picked up on the way.",
            cost = 90, rareBoost = 0f, themeChance = 0.10f
        ),
        CasePack(
            id = "chest", name = "Wayfarer's chest",
            blurb = "Heavier, older, and far more promising.",
            cost = 260, rareBoost = 0.9f, themeChance = 0.30f
        )
    )

    fun byId(id: String): CasePack? = ALL.firstOrNull { it.id == id }
}

/** What came out of a case. */
sealed interface CaseReward {
    data class GotCurio(val curio: Curio, val duplicate: Boolean, val growthBonus: Long) : CaseReward
    data class GotTheme(val theme: SceneTheme) : CaseReward
    data class GotGrowth(val amount: Long) : CaseReward
}

/** Shop + collection state for the screen. */
data class ShopState(
    val points: Int,
    val activeTheme: String,
    val ownedThemes: Set<String>,
    /** curio id → how many found. */
    val curios: Map<String, Int>
) {
    val curiosFound: Int get() = curios.keys.count { CurioCatalog.byId(it) != null }
    val curiosTotal: Int get() = CurioCatalog.ALL.size
    val completionPercent: Int
        get() = if (curiosTotal == 0) 0 else curiosFound * 100 / curiosTotal
}
