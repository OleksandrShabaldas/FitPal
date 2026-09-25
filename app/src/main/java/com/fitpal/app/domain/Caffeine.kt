package com.fitpal.app.domain

import kotlin.math.exp
import kotlin.math.ln

/**
 * The caffeine tracker's settings (Settings → Caffeine). [halfLifeHours] is how long the body takes
 * to clear half of it — about 5 h for most adults, shorter for smokers, longer in pregnancy or on
 * some medicines — so it's a user setting rather than a constant.
 */
data class CaffeineSettings(
    val enabled: Boolean = false,
    /** Daily intake limit — 400 mg is the usual guidance for healthy adults. */
    val dailyLimitMg: Int = 400,
    val halfLifeHours: Float = 5f
) {
    companion object {
        /** Below roughly this much still in the body, caffeine is unlikely to disturb sleep. */
        const val SLEEP_FRIENDLY_MG = 50
    }
}

/** One caffeinated thing eaten or drunk: when, how much, and whether the amount is a rough guess. */
data class CaffeineDose(
    val atMillis: Long,
    val mg: Float,
    val name: String,
    /** True when [mg] came from the built-in name list, not a real check — shown as "about". */
    val estimated: Boolean
)

/**
 * The caffeine maths — all offline, all deterministic.
 *
 * Each dose follows the classic one-compartment curve (the Bateman function): it's absorbed over
 * roughly the first hour (peaking ~45–60 min after the drink) and then leaves the body with the
 * user's half-life. "In your body now" is the sum of what's left of every dose. It's an estimate —
 * people clear caffeine at very different speeds — which is why the half-life is adjustable.
 */
object Caffeine {
    /** Absorption half-life: most caffeine is absorbed within ~45 min of drinking it. */
    private const val ABSORPTION_HALF_LIFE_H = 0.2
    private const val HOUR_MS = 3_600_000.0

    /** How much of a single dose is still in the body [hoursSince] after it was taken (0..~0.9). */
    fun fractionRemaining(hoursSince: Double, halfLifeHours: Float): Double {
        if (hoursSince <= 0.0) return 0.0
        val ka = ln(2.0) / ABSORPTION_HALF_LIFE_H
        val ke = ln(2.0) / halfLifeHours.coerceIn(1f, 12f)
        return (ka / (ka - ke)) * (exp(-ke * hoursSince) - exp(-ka * hoursSince))
    }

    /** Caffeine in the body at [atMillis] (mg), summed over every dose. */
    fun amountAt(doses: List<CaffeineDose>, atMillis: Long, halfLifeHours: Float): Float =
        doses.sumOf { d ->
            d.mg * fractionRemaining((atMillis - d.atMillis) / HOUR_MS, halfLifeHours)
        }.toFloat().coerceAtLeast(0f)

    /**
     * When the level falls to [thresholdMg] or below and stays falling — i.e. "sleep-friendly by".
     * Null when it's already there and nothing is still being absorbed, or it won't get there within
     * a day and a half (so the UI never promises a time it can't back up).
     */
    fun clearTime(doses: List<CaffeineDose>, fromMillis: Long, halfLifeHours: Float, thresholdMg: Int): Long? {
        if (doses.isEmpty()) return null
        val step = 5 * 60_000L
        val now = amountAt(doses, fromMillis, halfLifeHours)
        val soon = amountAt(doses, fromMillis + step, halfLifeHours)
        if (now <= thresholdMg && soon <= now) return null
        var t = fromMillis
        val limit = fromMillis + 36 * HOUR_MS.toLong()
        while (t < limit) {
            t += step
            val a = amountAt(doses, t, halfLifeHours)
            if (a <= thresholdMg && amountAt(doses, t + step, halfLifeHours) <= a) return t
        }
        return null
    }

    /** The level sampled evenly from [fromMillis] to [toMillis] — the detail chart's line. */
    fun curve(doses: List<CaffeineDose>, fromMillis: Long, toMillis: Long, points: Int, halfLifeHours: Float): List<Float> {
        if (points < 2 || toMillis <= fromMillis) return emptyList()
        val span = (toMillis - fromMillis).toDouble()
        return (0 until points).map { i ->
            amountAt(doses, fromMillis + (span * i / (points - 1)).toLong(), halfLifeHours)
        }
    }

    /**
     * A rough built-in guess (mg) for a food whose caffeine hasn't been checked yet, from its name —
     * the offline fallback so the tracker is never blind to a plain coffee or an energy drink. Null
     * when the name gives no clue (most foods have none). Deliberately conservative: specific,
     * well-known drinks only, and caffeine-free lookalikes (decaf, herbal tea) first.
     */
    fun estimateMg(name: String, grams: Float): Float? {
        if (grams <= 0f) return null
        // Padded so whole-word entries like " tea" also match at the very start or end of a name.
        val n = " " + name.lowercase() + " "
        val per100 = ESTIMATES.firstOrNull { (words, _) -> words.any { n.contains(it) } }?.second ?: return null
        return per100 * grams / 100f
    }

    /** Keyword → mg per 100 ml/g. First match wins, so caffeine-free and specific entries come first. */
    private val ESTIMATES: List<Pair<List<String>, Float>> = listOf(
        listOf("decaf", "caffeine free", "caffeine-free", "koffeinfrei", "bez kofeinu") to 1f,
        listOf("herbal", "rooibos", "chamomile", "camomile", "peppermint tea", "mint tea", "fruit tea",
            "hibiscus", "turmeric latte", "golden milk", "coffee cake", "creamer", "coffee mate") to 0f,
        listOf("espresso", "ristretto", "doppio") to 210f,
        listOf("cold brew") to 45f,
        listOf("celsius", "bang energy", "reign") to 60f,
        listOf("red bull", "monster", "rockstar", "energy drink", "hell energy", "burn energy", "tiger energy", "big shock") to 32f,
        listOf("matcha") to 25f,
        listOf("yerba", "mate tea", "club-mate", "club mate") to 30f,
        listOf("flat white") to 45f,
        listOf("americano", "long black", "cappuccino", "latte", "macchiato", "cortado", "mocha", "frappuccino", "iced coffee") to 30f,
        listOf("instant coffee") to 25f,
        listOf("coffee", "káva", "kava ") to 40f,
        listOf("chai") to 15f,
        listOf("green tea", "white tea", "oolong") to 12f,
        listOf("iced tea", "ice tea") to 8f,
        listOf("black tea", "earl grey", "english breakfast", "assam", "darjeeling", " tea") to 20f,
        listOf("mountain dew") to 15f,
        listOf("diet coke", "coca-cola light", "coke light", "pepsi max") to 13f,
        listOf("kofola", "dr pepper", "cola", "coke", "pepsi") to 10f,
        listOf("dark chocolate") to 60f,
        listOf("milk chocolate") to 20f,
        listOf("tiramisu") to 8f
    )
}
