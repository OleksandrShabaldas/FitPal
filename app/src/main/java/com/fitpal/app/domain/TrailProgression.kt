package com.fitpal.app.domain

/**
 * Drip-feeds the game instead of dumping every mechanic on the user at once.
 *
 * Features unlock from **real progress** (projects built, challenges claimed) rather than a
 * separate counter, so the state can't drift out of sync with what the player has actually
 * done. Each unlock is paired with a one-time coach-mark (see [TutorialStep]).
 */

enum class TrailFeature {
    /** Collecting the banked growth — the very first thing you ever do. */
    COLLECT,
    /** Spending growth on restoration projects. */
    PROJECTS,
    /** The trail map showing where you are and what's behind you. */
    MAP,
    /** Daily/weekly/monthly challenges — the ⭐ source. */
    TASKS,
    /** ⭐-gated centrepiece projects. Pointless before you can earn ⭐. */
    KEYSTONES,
    /** Cases, curios and scene themes. */
    SHOP
}

/** One-time visual coach-marks, shown in this order as they become relevant. */
enum class TutorialStep(val bit: Int) {
    WELCOME(1 shl 0),
    COLLECT(1 shl 1),
    BUILD(1 shl 2),
    MAP_UNLOCKED(1 shl 3),
    TASKS_UNLOCKED(1 shl 4),
    KEYSTONES_UNLOCKED(1 shl 5),
    SHOP_UNLOCKED(1 shl 6),
    VITALITY(1 shl 7)
}

object TrailProgressionRules {
    /** Projects built before the map appears. */
    const val PROJECTS_FOR_MAP = 1
    /** Projects built before challenges appear. */
    const val PROJECTS_FOR_TASKS = 3
    /** Challenges claimed before the shop appears. */
    const val CLAIMS_FOR_SHOP = 1
}

data class TrailProgression(
    val projectsBuilt: Int,
    val challengesClaimed: Int,
    /** Bitmask of [TutorialStep]s already shown. */
    val seenMask: Int
) {
    fun has(feature: TrailFeature): Boolean = when (feature) {
        TrailFeature.COLLECT, TrailFeature.PROJECTS -> true
        TrailFeature.MAP -> projectsBuilt >= TrailProgressionRules.PROJECTS_FOR_MAP
        TrailFeature.TASKS -> projectsBuilt >= TrailProgressionRules.PROJECTS_FOR_TASKS
        // Keystones cost ⭐, which only challenges provide — showing them earlier is just noise.
        TrailFeature.KEYSTONES -> has(TrailFeature.TASKS)
        TrailFeature.SHOP -> challengesClaimed >= TrailProgressionRules.CLAIMS_FOR_SHOP
    }

    fun isSeen(step: TutorialStep): Boolean = (seenMask and step.bit) != 0

    /** How many projects still to build before the next thing appears (0 = nothing pending). */
    fun projectsUntilNextUnlock(): Int = when {
        !has(TrailFeature.MAP) -> TrailProgressionRules.PROJECTS_FOR_MAP - projectsBuilt
        !has(TrailFeature.TASKS) -> TrailProgressionRules.PROJECTS_FOR_TASKS - projectsBuilt
        else -> 0
    }

    /** What unlocks next, for the "keep going" hint. */
    fun nextUnlockLabel(): String? = when {
        !has(TrailFeature.MAP) -> "the trail map"
        !has(TrailFeature.TASKS) -> "challenges"
        !has(TrailFeature.SHOP) -> "the shop"
        else -> null
    }
}

/**
 * Which coach-mark to show right now, if any. Ordered — earlier steps take priority, so the
 * player is never shown two things at once.
 */
fun nextTutorialStep(display: TrailDisplay): TutorialStep? {
    val p = display.progression
    fun unseen(step: TutorialStep) = !p.isSeen(step)

    return when {
        unseen(TutorialStep.WELCOME) -> TutorialStep.WELCOME
        // Only teach collecting once there's actually something to collect.
        unseen(TutorialStep.COLLECT) && display.bankedGrowth > 0L -> TutorialStep.COLLECT
        // Only teach building once the first project is affordable.
        unseen(TutorialStep.BUILD) && display.projects.any { !it.built && it.affordable } ->
            TutorialStep.BUILD
        unseen(TutorialStep.MAP_UNLOCKED) && p.has(TrailFeature.MAP) -> TutorialStep.MAP_UNLOCKED
        unseen(TutorialStep.TASKS_UNLOCKED) && p.has(TrailFeature.TASKS) -> TutorialStep.TASKS_UNLOCKED
        unseen(TutorialStep.KEYSTONES_UNLOCKED) && p.has(TrailFeature.KEYSTONES) ->
            TutorialStep.KEYSTONES_UNLOCKED
        unseen(TutorialStep.SHOP_UNLOCKED) && p.has(TrailFeature.SHOP) -> TutorialStep.SHOP_UNLOCKED
        // Explain decay the first time it actually bites, not before.
        unseen(TutorialStep.VITALITY) && display.vitality < TrailRules.VITALITY_START ->
            TutorialStep.VITALITY
        else -> null
    }
}

/** Copy for each coach-mark, plus which on-screen element it points at. */
data class TutorialCopy(val title: String, val body: String, val targetId: String?)

object TutorialText {
    const val TARGET_COLLECT = "collect"
    const val TARGET_PROJECTS = "projects"
    const val TARGET_MAP = "map"
    const val TARGET_TABS = "tabs"
    const val TARGET_VITALITY = "vitality"

    fun of(step: TutorialStep): TutorialCopy = when (step) {
        TutorialStep.WELCOME -> TutorialCopy(
            "This is the trail",
            "A run of neglected places waiting to be put right. It only moves forward when you " +
                "log your food — nothing here grows on its own.",
            null
        )
        TutorialStep.COLLECT -> TutorialCopy(
            "Your first day's growth",
            "Every day you log becomes one step along the trail. Tap here to collect what's " +
                "waiting — and take a few ⭐ for doing it by hand.",
            TARGET_COLLECT
        )
        TutorialStep.BUILD -> TutorialCopy(
            "Now fix something",
            "Spend growth to restore this place, one job at a time — you pick how each one is " +
                "built, and it stands there for good. Everything you build keeps producing " +
                "forever, so the next one comes faster.",
            TARGET_PROJECTS
        )
        TutorialStep.MAP_UNLOCKED -> TutorialCopy(
            "The map is open",
            "You can see the road now. Finish everything here and the way onward opens up.",
            TARGET_MAP
        )
        TutorialStep.TASKS_UNLOCKED -> TutorialCopy(
            "Challenges unlocked",
            "Daily, weekly and monthly goals. They're the only place ⭐ comes from — and the " +
                "points only land when you tap claim.",
            TARGET_TABS
        )
        TutorialStep.KEYSTONES_UNLOCKED -> TutorialCopy(
            "Keystone projects",
            "The gold ones are the centrepieces. They cost ⭐, pay triple, and never block your " +
                "way — you can walk on without them.",
            TARGET_PROJECTS
        )
        TutorialStep.SHOP_UNLOCKED -> TutorialCopy(
            "The shop is open",
            "Spend ⭐ on a new look for the place, or gamble it on a case and start filling out " +
                "your curios.",
            TARGET_TABS
        )
        TutorialStep.VITALITY -> TutorialCopy(
            "The wild is creeping back",
            "You missed a day, so everything produces less and the overgrowth returns. Nothing " +
                "you built is ever lost — start logging again and it clears.",
            TARGET_VITALITY
        )
    }
}
