package com.fitpal.app.domain

import kotlin.random.Random

/**
 * Garden domain model — the "don't let it wilt" + collection motivator.
 *
 * Growth comes from logging (always) with a bonus for on-goal days; neglect
 * makes the current plant wilt (cushioned by rainy-day buffers) and, if ignored
 * long enough, the seedling dies (but the collection is never lost). Every bloom
 * adds a plant to the collection and starts a fresh seed.
 */

/** Visible growth stage of the current plant. */
enum class PlantStage(val emoji: String, val label: String) {
    SEED("🌰", "Seed"),
    SPROUT("🌱", "Sprout"),
    GROWING("🌿", "Growing"),
    BUD("🌾", "Budding")
}

/** How droopy the current plant looks from missed days. */
enum class WiltState(val label: String) {
    HEALTHY("Healthy"),
    DROOPING("Drooping"),
    WILTING("Wilting"),
    WITHERING("Withering")
}

enum class PlantRarity(val label: String) {
    COMMON("Common"),
    UNCOMMON("Uncommon"),
    RARE("Rare"),
    LEGENDARY("Legendary")
}

data class PlantSpecies(
    val id: String,
    val name: String,
    val emoji: String,
    val rarity: PlantRarity
)

object GardenRules {
    // ---- Water economy ----
    /** 💧 reserve a fresh garden starts with. */
    const val STARTING_WATER = 3
    /** 💧 earned for logging real food on a day. */
    const val WATER_PER_LOG = 1
    /** Extra 💧 when that day was also on-goal (builds your reserve). */
    const val WATER_ON_GOAL_BONUS = 1
    /** Max 💧 you can bank. */
    const val WATER_CAP = 14
    /** Growth from watering the plant once (costs 1 💧). */
    const val GROWTH_PER_WATER = 10
    /** ⭐ points for watering manually (auto-water gives none). */
    const val WATER_POINTS = 5

    /** Points needed to bloom one plant (~7 waterings). */
    const val BLOOM_COST = 70
    /** Days with no 💧 to water before the seedling dies and restarts. */
    const val WILT_DEATH = 4

    /** On-track-week rewards. */
    const val WEEKLY_BONUS_WATER = 3
    const val WEEKLY_BONUS_POINTS = 20
    /** A week needs at least this many logged days to count (anti-skip guard). */
    const val MIN_LOGGED_DAYS_FOR_WEEK = 5

    fun stageFor(growthPoints: Int): PlantStage {
        val f = growthPoints.toFloat() / BLOOM_COST
        return when {
            f < 0.2f -> PlantStage.SEED
            f < 0.5f -> PlantStage.SPROUT
            f < 0.8f -> PlantStage.GROWING
            else -> PlantStage.BUD
        }
    }

    fun wiltFor(wiltLevel: Int): WiltState = when (wiltLevel) {
        0 -> WiltState.HEALTHY
        1 -> WiltState.DROOPING
        2 -> WiltState.WILTING
        else -> WiltState.WITHERING
    }
}

object PlantCatalog {
    val ALL = listOf(
        PlantSpecies("daisy", "Daisy", "🌼", PlantRarity.COMMON),
        PlantSpecies("tulip", "Tulip", "🌷", PlantRarity.COMMON),
        PlantSpecies("blossom", "Blossom", "🌸", PlantRarity.COMMON),
        PlantSpecies("rose", "Rose", "🌹", PlantRarity.UNCOMMON),
        PlantSpecies("sunflower", "Sunflower", "🌻", PlantRarity.UNCOMMON),
        PlantSpecies("hibiscus", "Hibiscus", "🌺", PlantRarity.RARE),
        PlantSpecies("cactus", "Cactus Flower", "🌵", PlantRarity.RARE),
        PlantSpecies("lotus", "Lotus", "🪷", PlantRarity.LEGENDARY),
        PlantSpecies("hyacinth", "Hyacinth", "🪻", PlantRarity.LEGENDARY)
    )

    fun byId(id: String): PlantSpecies? = ALL.firstOrNull { it.id == id }

    /**
     * Roll a species at bloom. Quality (share of on-goal days during this plant's
     * life, 0–1) shifts the odds toward rarer plants — so staying on goal is what
     * collectors chase, without ever punishing an honest off day.
     */
    fun roll(quality: Float, rng: Random = Random.Default): PlantSpecies {
        val q = quality.coerceIn(0f, 1f)
        val weights = mapOf(
            PlantRarity.COMMON to (0.55f - 0.35f * q),
            PlantRarity.UNCOMMON to 0.28f,
            PlantRarity.RARE to (0.13f + 0.18f * q),
            PlantRarity.LEGENDARY to (0.04f + 0.17f * q)
        )
        val total = weights.values.sum()
        var roll = rng.nextFloat() * total
        val tier = weights.entries.firstOrNull { (_, w) -> roll -= w; roll <= 0f }?.key
            ?: PlantRarity.COMMON
        val pool = ALL.filter { it.rarity == tier }.ifEmpty { ALL.filter { it.rarity == PlantRarity.COMMON } }
        return pool.random(rng)
    }
}

/** How the current (partial) week is tracking toward the weekly bonus. */
data class WeekProgress(
    val loggedDays: Int,
    val neededDays: Int,
    val onTrackSoFar: Boolean,
    val daysElapsed: Int
)

/** Everything the Garden screen needs to render, including today's live preview. */
data class GardenDisplay(
    val stage: PlantStage,
    val wilt: WiltState,
    val growthPoints: Int,
    val bloomCost: Int,
    val water: Int,
    val points: Int,
    val canWaterToday: Boolean,
    val streak: Int,
    val bloomCount: Int,
    val onTrackWeeks: Int,
    val loggedToday: Boolean,
    val onGoalToday: Boolean
)
