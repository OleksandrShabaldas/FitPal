package com.fitpal.app.domain

import kotlin.math.exp
import kotlin.math.roundToInt
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
        const val SLEEP_FRIENDLY_MG = 40
        /** This much or more left at bedtime is likely to keep you up — the red verdict. */
        const val KEEPS_YOU_UP_MG = 65
    }
}

/** A drink for the sleep calculator (Settings → Caffeine → Your drinks): name, serving, caffeine. */
data class CaffeinePreset(val label: String, val serving: String, val mg: Int)

/** What a (hypothetical) dose would mean for sleep — see [Caffeine.sleepVerdict]. */
sealed interface SleepVerdict {
    /** It never takes the level above the sleep-friendly line. */
    data object NeverAffects : SleepVerdict
    /** Sleep-friendly again from [atMillis]. */
    data class ClearAt(val atMillis: Long) : SleepVerdict
    /** Still above the line a day and a half later. */
    data object NotWithin36h : SleepVerdict
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

    /**
     * A logged item as a dose: its checked caffeine ([checkedMg]), else the built-in estimate from its
     * name (flagged as an estimate); null when it has none worth counting. The one rule Home's tracker
     * and the caffeine widget share.
     */
    fun dose(atMillis: Long, name: String, grams: Float, checkedMg: Float?): CaffeineDose? {
        val mg = checkedMg ?: estimateMg(name, grams) ?: return null
        if (mg < 1f) return null
        return CaffeineDose(atMillis, mg, name, estimated = checkedMg == null)
    }

    /**
     * The sleep calculator: if [extra] is added to [existing], when is the level back at or below
     * [thresholdMg] for good? The search starts at the later of [nowMillis] and the dose's own time —
     * starting earlier would report when the *earlier* caffeine clears, before this drink is even had.
     * A dose that never lifts the level over the line says so instead of a time.
     */
    fun sleepVerdict(
        existing: List<CaffeineDose>,
        extra: CaffeineDose,
        nowMillis: Long,
        halfLifeHours: Float,
        thresholdMg: Int
    ): SleepVerdict {
        val doses = existing + extra
        val from = maxOf(nowMillis, extra.atMillis)
        // The peak comes ~1 h after a dose; look a few hours ahead from the later of now / the dose.
        val peak = curve(doses, from, from + 4 * HOUR_MS.toLong(), 49, halfLifeHours).maxOrNull() ?: 0f
        if (peak <= thresholdMg) return SleepVerdict.NeverAffects
        return clearTime(doses, from, halfLifeHours, thresholdMg)?.let { SleepVerdict.ClearAt(it) }
            ?: SleepVerdict.NotWithin36h
    }

    /** The next time it's [bedMinute] (minute of the day) after [afterMillis]. */
    fun nextBedtime(afterMillis: Long, bedMinute: Int, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Long {
        val after = java.time.Instant.ofEpochMilli(afterMillis).atZone(zone)
        var bed = after.toLocalDate().atTime(bedMinute / 60, bedMinute % 60).atZone(zone)
        if (!bed.isAfter(after)) bed = bed.plusDays(1)
        return bed.toInstant().toEpochMilli()
    }

    /**
     * The verdict a person actually wants — will tonight's sleep be fine? — judged at [bedMillis]
     * against the sleep-friendly line (40 mg): low enough already; under the line by a time before
     * bed; or roughly how much will still be in them at bedtime (red from [CaffeineSettings.KEEPS_YOU_UP_MG]).
     */
    fun bedtimeStatus(doses: List<CaffeineDose>, nowMillis: Long, bedMillis: Long, halfLifeHours: Float): TrackerStatus {
        val line = CaffeineSettings.SLEEP_FRIENDLY_MG
        // Nothing now and nothing still being absorbed over the next few hours.
        val ahead = curve(doses, nowMillis, nowMillis + 4 * HOUR_MS.toLong(), 25, halfLifeHours).maxOrNull() ?: 0f
        if (doses.isEmpty() || ahead < 1f) return TrackerStatus(StatusKind.OK, "Nothing in you")
        val atBed = amountAt(doses, bedMillis, halfLifeHours)
        val clear = clearTime(doses, nowMillis, halfLifeHours, line)
        return when {
            atBed <= line && clear == null -> TrackerStatus(StatusKind.OK, "Low enough for sleep")
            atBed <= line && clear != null -> TrackerStatus(StatusKind.OK, "Under $line mg by ${clockLabel(clear)}, ${gapBeforeBed(bedMillis - clear)}")
            atBed <= CaffeineSettings.KEEPS_YOU_UP_MG -> TrackerStatus(StatusKind.CLOSE, "About ${atBed.roundToInt()} mg left at bedtime")
            else -> TrackerStatus(StatusKind.OVER, "About ${atBed.roundToInt()} mg left at bedtime, it may keep you up")
        }
    }

    /**
     * "Planning another one?": what [extra] (called [what], e.g. "A coffee now") would do to tonight,
     * on top of what's already in the body. Bedtime is the next one after the drink.
     */
    fun planSentence(
        existing: List<CaffeineDose>,
        extra: CaffeineDose,
        what: String,
        nowMillis: Long,
        bedMinute: Int,
        halfLifeHours: Float
    ): String {
        val line = CaffeineSettings.SLEEP_FRIENDLY_MG
        val doses = existing + extra
        val from = maxOf(nowMillis, extra.atMillis)
        val bed = nextBedtime(from, bedMinute)
        val atBed = amountAt(doses, bed, halfLifeHours)
        return when (val v = sleepVerdict(existing, extra, nowMillis, halfLifeHours, line)) {
            SleepVerdict.NeverAffects -> "$what stays under $line mg, no problem for sleep."
            is SleepVerdict.ClearAt ->
                if (v.atMillis <= bed) "$what: under $line mg by ${clockLabel(v.atMillis)}, still before bed."
                else "$what leaves about ${atBed.roundToInt()} mg at bedtime" + (if (atBed > CaffeineSettings.KEEPS_YOU_UP_MG) ", enough to keep you up." else ".")
            SleepVerdict.NotWithin36h -> "$what leaves about ${atBed.roundToInt()} mg at bedtime, enough to keep you up."
        }
    }

    private fun gapBeforeBed(gapMillis: Long): String {
        val minutes = gapMillis / 60_000L
        return when {
            minutes < 45 -> "just before bed"
            minutes < 90 -> "an hour before bed"
            else -> "${(minutes / 60.0).roundToInt()} hours before bed"
        }
    }

    /** "11:20 PM". */
    private fun clockLabel(epochMillis: Long): String {
        val t = java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        return WaterPace.clockLabel(t.hour * 60 + t.minute)
    }

    /** Every list of drinks: least caffeine first, then by name. */
    val drinkOrder: Comparator<CaffeinePreset> = compareBy<CaffeinePreset> { it.mg }.thenBy { it.label.lowercase() }

    /** The drinks a new install starts with (Settings → Caffeine → Your drinks). Rough, well-known figures. */
    val DEFAULT_DRINKS: List<CaffeinePreset> = listOf(
        CaffeinePreset("Green tea", "250 ml", 28),
        CaffeinePreset("Black tea", "250 ml", 47),
        CaffeinePreset("Espresso", "single shot", 63),
        CaffeinePreset("Latte / cappuccino", "1 shot", 63),
        CaffeinePreset("Red Bull", "250 ml can", 80),
        CaffeinePreset("Double espresso", "2 shots", 126),
        CaffeinePreset("Monster", "500 ml can", 160)
    ).sortedWith(drinkOrder)

    /** Offered as one-tap adds in Your drinks, when they're not in the list already. */
    val SUGGESTED_DRINKS: List<CaffeinePreset> = listOf(
        CaffeinePreset("Cola", "330 ml can", 32),
        CaffeinePreset("Matcha", "1 tsp", 70),
        CaffeinePreset("Coffee", "mug, 250 ml", 95),
        CaffeinePreset("Cold brew", "350 ml", 155),
        CaffeinePreset("Celsius", "355 ml can", 200),
        CaffeinePreset("Caffeine pill", "1 tablet", 200)
    ).sortedWith(drinkOrder)

    /** The planner's quick picks on a new install (the user's stars replace them). */
    val DEFAULT_QUICK_DRINKS: List<String> = listOf("Espresso", "Latte / cappuccino", "Double espresso")

    /** How many drinks sit on the planner card; the rest are behind "More…". */
    const val QUICK_DRINK_COUNT = 3

    /**
     * The planner card's drinks: the starred ones that are still in [drinks], topped up with the first
     * unstarred ones so there are always [QUICK_DRINK_COUNT] (or as many as exist), least caffeine first.
     */
    fun quickDrinks(drinks: List<CaffeinePreset>, starred: List<String>): List<CaffeinePreset> {
        val sorted = drinks.sortedWith(drinkOrder)
        val picked = sorted.filter { d -> starred.any { it.equals(d.label, ignoreCase = true) } }.take(QUICK_DRINK_COUNT)
        val fill = sorted.filterNot { it in picked }.take(QUICK_DRINK_COUNT - picked.size)
        return (picked + fill).sortedWith(drinkOrder)
    }

    /** Your drinks as stored: `[{"label","serving","mg"}]`. */
    fun drinksToJson(drinks: List<CaffeinePreset>): String = org.json.JSONArray().apply {
        drinks.forEach { put(org.json.JSONObject().put("label", it.label).put("serving", it.serving).put("mg", it.mg)) }
    }.toString()

    /** Null when [raw] is missing or unreadable (the defaults apply); never an empty list. */
    fun drinksFromJson(raw: String?): List<CaffeinePreset>? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val label = o.optString("label").trim()
                val mg = o.optInt("mg", 0)
                if (label.isEmpty() || mg <= 0) null else CaffeinePreset(label, o.optString("serving").trim(), mg)
            }.distinctBy { it.label.lowercase() }.sortedWith(drinkOrder).takeIf { it.isNotEmpty() }
        }.getOrNull()
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
        // Whole words only (TextMatch) — "cola" mustn't match "chocolate", nor "tea" "steak".
        val per100 = ESTIMATES.firstOrNull { (words, _) -> TextMatch.containsAny(name, words) }?.second ?: return null
        return per100 * grams / 100f
    }

    /** Keyword → mg per 100 ml/g. First match wins, so caffeine-free and specific entries come first. */
    private val ESTIMATES: List<Pair<List<String>, Float>> = listOf(
        listOf("decaf*", "caffeine free", "koffeinfrei", "bez kofeinu", "bezkofeinov*") to 1f,
        listOf("herbal", "rooibos", "chamomile", "camomile", "peppermint tea", "mint tea", "fruit tea",
            "hibiscus", "turmeric latte", "golden milk", "coffee cake", "creamer", "coffee mate", "hot chocolate") to 0f,
        listOf("dark chocolate", "hořká čokoláda", "horká čokoláda") to 60f,
        listOf("milk chocolate", "mléčná čokoláda", "mliečna čokoláda") to 20f,
        listOf("tiramisu") to 8f,
        listOf("espresso", "ristretto", "doppio") to 210f,
        listOf("cold brew") to 45f,
        listOf("celsius", "bang energy", "reign") to 60f,
        listOf("red bull", "monster", "rockstar", "energy drink", "hell energy", "burn energy", "tiger energy",
            "big shock", "semtex", "енергетик", "енергетичний напій") to 32f,
        listOf("matcha") to 25f,
        listOf("yerba", "mate tea", "club-mate", "club mate") to 30f,
        listOf("chai") to 15f,
        listOf("flat white") to 45f,
        listOf("americano", "long black", "cappuccino", "latte", "macchiato", "cortado", "mocha", "frappuccino", "iced coffee") to 30f,
        listOf("instant coffee") to 25f,
        listOf("coffee", "káv*", "kava", "кава", "кофе") to 40f,
        listOf("green tea", "white tea", "oolong") to 12f,
        listOf("iced tea", "ice tea") to 8f,
        listOf("black tea", "earl grey", "english breakfast", "assam", "darjeeling", "tea", "čaj", "чай") to 20f,
        listOf("mountain dew") to 15f,
        listOf("diet coke", "coca-cola light", "coke light", "pepsi max", "coke zero", "coca-cola zero") to 13f,
        listOf("kofola", "dr pepper", "cola", "coca-cola", "coke", "pepsi") to 10f
    )
}
