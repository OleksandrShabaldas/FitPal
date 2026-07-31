package com.fitpal.app.domain

import kotlin.math.pow

/**
 * "The Trail" — the motivation game. Full design in GAME_DESIGN.md.
 *
 * **The one rule: one tick = one logged day.** Nothing accrues from elapsed time, so
 * the game can never pay out on its own and quietly replace the habit it exists to
 * build. Neglect hits the *rate* (see vitality) — never anything you already bought.
 *
 * Shape: Trail → Sites → Projects. Each Project costs 🌿 growth and permanently adds
 * production, so progress compounds *within* a site as well as between sites.
 */

/**
 * What a built project looks like in the diorama. A deliberately small shared library —
 * reused across sites with different arrangements — so the scene never becomes an
 * endless art project. Drawing lives in `ui/screen/trail/TrailProps.kt`.
 */
enum class PropKind {
    SOIL, SPROUTS, FLOWERS, HERBS, TREE,
    FENCE, PATH, STONE, POST, LANTERN,
    BIN, SHED, GREENHOUSE, WELL, HIVE,
    POND, BRIDGE, ARCH, BENCH
}

/** One restoration job at a Site. Keystones cost ⭐ and pay triple production. */
data class TrailProject(
    val id: String,
    val name: String,
    /** 🌿 growth cost. */
    val cost: Long,
    /** Production permanently added once built. */
    val production: Long,
    /** What appears in the scene once built. */
    val prop: PropKind,
    /** ⭐ cost — keystones only (0 = an ordinary project). */
    val pointCost: Int = 0
) {
    val isKeystone: Boolean get() = pointCost > 0
}

data class TrailSite(
    val id: String,
    val name: String,
    val blurb: String,
    val projects: List<TrailProject>
)

object TrailRules {
    /** Production before you've built anything. */
    const val BASE_PRODUCTION = 10L

    // ---- 💧 water: fuels ticks ----
    const val STARTING_WATER = 3
    const val WATER_PER_LOG = 1
    const val WATER_ON_GOAL_BONUS = 1
    const val WATER_CAP = 14

    // ---- vitality: the whole decay system ----
    const val VITALITY_START = 1.0f
    const val VITALITY_MAX = 2.0f
    const val VITALITY_MIN = 0.5f
    const val VITALITY_GAIN = 0.1f      // per logged day
    const val VITALITY_LOSS = 0.4f      // per missed day

    /** Bonus on a day that also hit the calorie goal. */
    const val ON_GOAL_MULTIPLIER = 1.25f
    /** ⭐ for collecting by hand (the daily ritual). */
    const val COLLECT_POINTS = 5

    /** Cost curve within a site. */
    const val COST_GROWTH = 1.25

    /** How overgrown things look, from vitality. */
    fun overgrowth(vitality: Float): Float =
        ((VITALITY_START - vitality) / (VITALITY_START - VITALITY_MIN)).coerceIn(0f, 1f)

    fun vitalityLabel(vitality: Float): String = when {
        vitality >= 1.8f -> "Flourishing"
        vitality >= 1.3f -> "Thriving"
        vitality >= 1.0f -> "Steady"
        vitality >= 0.7f -> "Overgrown"
        else -> "Reclaimed by the wild"
    }
}

object TrailCatalog {

    /** Region 1 — handcrafted. Beyond this the trail continues procedurally. */
    val REGION_1: List<TrailSite> = listOf(
        site(
            id = "home_garden", name = "The home garden",
            blurb = "Where it all starts. Wild now, but it remembers being loved.",
            baseCost = 15, prodPer = 4, keystones = mapOf(5 to 60, 9 to 90),
            projects = listOf(
                "Clear the overgrowth" to PropKind.STONE,
                "Turn the soil" to PropKind.SOIL,
                "Mend the picket fence" to PropKind.FENCE,
                "Sow the first seed bed" to PropKind.SPROUTS,
                "Lay the stone path" to PropKind.PATH,
                "Raise the greenhouse frame" to PropKind.GREENHOUSE,
                "Plant the herb spiral" to PropKind.HERBS,
                "Hang the bird feeder" to PropKind.POST,
                "Build the compost bin" to PropKind.BIN,
                "Light the garden lanterns" to PropKind.LANTERN
            )
        ),
        site(
            id = "greenhouse", name = "The old greenhouse",
            blurb = "Panes cracked, but the frame is sound and the air still smells of tomatoes.",
            baseCost = 110, prodPer = 12, keystones = mapOf(5 to 90, 10 to 130),
            projects = listOf(
                "Sweep out the broken glass" to PropKind.STONE,
                "Re-glaze the panes" to PropKind.GREENHOUSE,
                "Fix the roof vents" to PropKind.SHED,
                "Rebuild the potting bench" to PropKind.BENCH,
                "Run the water line" to PropKind.POND,
                "Restore the brass boiler" to PropKind.SHED,
                "Hang the shade cloth" to PropKind.POST,
                "Pot the seedlings" to PropKind.SPROUTS,
                "Train the climbing vine" to PropKind.HERBS,
                "Set the thermometer" to PropKind.POST,
                "Grow the first orchid" to PropKind.FLOWERS
            )
        ),
        site(
            id = "well", name = "The dry well",
            blurb = "It has been thirsty a long time. So has everything downhill of it.",
            baseCost = 380, prodPer = 40, keystones = mapOf(4 to 130, 9 to 180),
            projects = listOf(
                "Haul away the rubble" to PropKind.STONE,
                "Re-point the stonework" to PropKind.STONE,
                "Replace the rotted beam" to PropKind.POST,
                "Fit a new bucket and rope" to PropKind.WELL,
                "Dig down to the water table" to PropKind.WELL,
                "Build the wellhead roof" to PropKind.SHED,
                "Lay the drainage channel" to PropKind.POND,
                "Set the drinking trough" to PropKind.POND,
                "Plant the willow ring" to PropKind.TREE,
                "Ring the well bell" to PropKind.ARCH
            )
        ),
        site(
            id = "orchard", name = "The orchard",
            blurb = "Old rootstock under the bramble. Someone planted these for a future they'd never see.",
            baseCost = 1250, prodPer = 130, keystones = mapOf(5 to 180, 10 to 240),
            projects = listOf(
                "Cut back the bramble" to PropKind.STONE,
                "Map the old rootstock" to PropKind.SOIL,
                "Graft the first branches" to PropKind.TREE,
                "Stake the young trees" to PropKind.TREE,
                "Mulch the roots" to PropKind.SOIL,
                "Raise the irrigation cistern" to PropKind.POND,
                "Build the ladder store" to PropKind.SHED,
                "Hang the fruit nets" to PropKind.POST,
                "Set the pressing table" to PropKind.BENCH,
                "Sow the wildflower strip" to PropKind.FLOWERS,
                "Harvest the first crop" to PropKind.TREE
            )
        ),
        site(
            id = "apiary", name = "The apiary",
            blurb = "Nothing here fruits properly until the bees come home.",
            baseCost = 4100, prodPer = 430, keystones = mapOf(4 to 240, 9 to 320),
            projects = listOf(
                "Level the hive stands" to PropKind.SOIL,
                "Build the first hive" to PropKind.HIVE,
                "Weave the smoker" to PropKind.BIN,
                "Plant the nectar border" to PropKind.FLOWERS,
                "Welcome the first colony" to PropKind.HIVE,
                "Add the honey supers" to PropKind.HIVE,
                "Build the extraction shed" to PropKind.SHED,
                "Set the water dish" to PropKind.POND,
                "Fence the paddock" to PropKind.FENCE,
                "Draw the first honey" to PropKind.BENCH
            )
        ),
        site(
            id = "lantern_path", name = "The lantern path",
            blurb = "The way onward. Light it, and the trail keeps going.",
            baseCost = 13500, prodPer = 1400, keystones = mapOf(5 to 320, 11 to 420),
            projects = listOf(
                "Clear the trailhead" to PropKind.STONE,
                "Lay the gravel" to PropKind.PATH,
                "Build the footbridge" to PropKind.BRIDGE,
                "Set the waymarkers" to PropKind.POST,
                "Carve the resting bench" to PropKind.BENCH,
                "Raise the stone arch" to PropKind.ARCH,
                "Hang the first lanterns" to PropKind.LANTERN,
                "Plant the night-scented border" to PropKind.FLOWERS,
                "Build the shelter" to PropKind.SHED,
                "Set the star map" to PropKind.STONE,
                "Ring the path with beacons" to PropKind.LANTERN,
                "Light the way onward" to PropKind.LANTERN
            )
        )
    )

    /** The site at a given index — handcrafted for region 1, generated after that. */
    fun siteAt(index: Int): TrailSite =
        REGION_1.getOrNull(index) ?: generated(index - REGION_1.size)

    /** Total sites in the handcrafted region (for "Site 3 of 6"). */
    val region1Size: Int get() = REGION_1.size

    // ---- Endless continuation ----

    private val WILD_SITES = listOf(
        "The far meadow", "The birch hollow", "The old mill", "The lakeside camp",
        "The high pasture", "The stone circle", "The pine ridge", "The river bend"
    )

    private val WILD_PROJECTS = listOf(
        "Clear the way in" to PropKind.STONE,
        "Shore up the foundations" to PropKind.STONE,
        "Draw fresh water" to PropKind.WELL,
        "Raise the shelter" to PropKind.SHED,
        "Break new ground" to PropKind.SOIL,
        "Set the boundary stones" to PropKind.POST,
        "Build the store house" to PropKind.SHED,
        "Plant the first beds" to PropKind.SPROUTS,
        "Lay the winter path" to PropKind.PATH,
        "Kindle the waystone" to PropKind.ARCH,
        "Hang the season's lanterns" to PropKind.LANTERN
    )

    private fun generated(n: Int): TrailSite {
        val scale = 3.3.pow((n + 1).toDouble())
        val cycle = n / WILD_SITES.size
        val name = WILD_SITES[n % WILD_SITES.size] + if (cycle > 0) " ${cycle + 1}" else ""
        return site(
            id = "wild_$n", name = name,
            blurb = "Further down the trail. Nobody has tended this in a long time.",
            baseCost = (13500.0 * scale).toLong().coerceAtLeast(1),
            prodPer = (1400.0 * scale).toLong().coerceAtLeast(1),
            keystones = mapOf(4 to (420 + n * 60), 9 to (520 + n * 80)),
            projects = WILD_PROJECTS
        )
    }

    // ---- Builder ----

    /**
     * Costs follow `baseCost × 1.25ⁿ` and every project adds `prodPer` production
     * (keystones add triple), which is what keeps a site at ~16–17 ticks with
     * something completing every 1.5–2.5 days.
     */
    private fun site(
        id: String,
        name: String,
        blurb: String,
        baseCost: Long,
        prodPer: Long,
        projects: List<Pair<String, PropKind>>,
        keystones: Map<Int, Int> = emptyMap()
    ): TrailSite {
        val built = projects.mapIndexed { i, (label, prop) ->
            val keystonePoints = keystones[i] ?: 0
            TrailProject(
                id = "${id}_$i",
                name = label,
                cost = (baseCost * TrailRules.COST_GROWTH.pow(i.toDouble())).toLong().coerceAtLeast(1),
                production = if (keystonePoints > 0) prodPer * 3 else prodPer,
                prop = prop,
                pointCost = keystonePoints
            )
        }
        return TrailSite(id, name, blurb, built)
    }
}

/** One project as the screen needs it. */
data class ProjectView(
    val project: TrailProject,
    val built: Boolean,
    val affordable: Boolean
)

/** Everything the Trail screen renders. */
data class TrailDisplay(
    val siteIndex: Int,
    val site: TrailSite,
    val projects: List<ProjectView>,
    val growth: Long,
    val bankedGrowth: Long,
    val water: Int,
    val points: Int,
    val vitality: Float,
    /** Production per tick before multipliers. */
    val production: Long,
    /** What the next tick would actually pay. */
    val perTick: Long,
    val streak: Int,
    val loggedToday: Boolean,
    val onGoalToday: Boolean,
    val tickedToday: Boolean,
    /** Equipped scene theme (see ThemeCatalog) — drives the diorama palette. */
    val themeId: String = ThemeCatalog.DEFAULT_ID
) {
    val builtCount: Int get() = projects.count { it.built }
    val totalCount: Int get() = projects.size
    /** Non-keystone projects still to build — keystones never block the path. */
    val requiredRemaining: Int get() = projects.count { !it.built && !it.project.isKeystone }
    val canAdvance: Boolean get() = requiredRemaining == 0
    val canCollect: Boolean get() = bankedGrowth > 0
    val siteNumber: Int get() = siteIndex + 1
    val overgrowth: Float get() = TrailRules.overgrowth(vitality)
}
