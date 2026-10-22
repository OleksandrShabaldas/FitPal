package com.fitpal.app.ml

import android.graphics.Bitmap
import android.util.Log
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.domain.DietaryTally
import com.fitpal.app.domain.model.DietaryRuleKind
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides which dietary-rule categories a food belongs to — "is this a dessert / fried food /
 * sugary drink?" — for both the daily tally (tagging every logged item) and the pre-log gate
 * ([DietaryGate]).
 *
 * Two layers, so it always answers and always works offline:
 *  - **Online (preferred):** a quick call to the dedicated fast model trio ([SettingsRepository]
 *    `fastModel`…), with its own lean prompt and no "thinking" — cheap enough to run per log, and on
 *    a separate quota from the main food analysis. It also sees the user's per-rule "what counts"
 *    note, so the AI's idea of the category matches theirs.
 *  - **Offline fallback:** a high-precision keyword match ([DietaryRuleKind.heuristicWords]) — free,
 *    instant, no network. Used whenever online can't run (no key / offline / out of quota) or fails.
 *
 * Every method fails **open and quiet**: on any error it degrades to the heuristic (or empty), never
 * throwing, so classification can never block or break a log.
 *
 * **Logging never waits on the network.** [quickTags] answers instantly from the AI answers already
 * remembered for a food (or the keyword match); the log is saved with those, and [tagFoods] then
 * refines the tags in the background (see `MealRepository`). The AI answers are remembered per food +
 * rule note, so the pre-log gate's check and the log right after it never ask the model twice.
 */
@Singleton
class DietaryClassifier @Inject constructor(
    private val gemini: GeminiClient,
    private val settings: SettingsRepository,
    private val networkMonitor: NetworkMonitor
) {

    /** Whether the fast online classifier is usable right now (key + master switch + net + quota). */
    private fun canUseOnline(): Boolean =
        settings.onlineAiEnabled.value &&
            gemini.hasKey() &&
            networkMonitor.isOnline() &&
            settings.activeFastModels().any { !settings.isModelQuotaExhaustedToday(it) }

    /**
     * Tag each of [names] with the subset of [kinds] it belongs to (aligned to the input order), asking
     * the online model about any food it hasn't judged yet. Used by the gate for known foods (with a
     * short [timeoutMs] — someone is waiting on it) and by the background refinement after a log.
     */
    suspend fun tagFoods(
        names: List<String>,
        kinds: Set<DietaryRuleKind>,
        timeoutMs: Long = TIMEOUT_MS
    ): List<Set<DietaryRuleKind>> {
        if (names.isEmpty() || kinds.isEmpty()) return names.map { emptySet() }
        val unknown = names.filter { cachedTags(it, kinds) == null }.distinct()
        if (unknown.isNotEmpty() && canUseOnline()) {
            aiTagFoods(unknown, kinds, timeoutMs)?.let { answers ->
                unknown.forEachIndexed { i, name -> remember(name, kinds, answers.getOrElse(i) { emptySet() }) }
            }
        }
        return quickTags(names, kinds)
    }

    /**
     * The tags for [names] right now, with no network: the AI's remembered answer for a food when
     * there is one, otherwise the offline keyword match. This is what a log is saved with, so pressing
     * "Log" never waits on the AI.
     */
    fun quickTags(names: List<String>, kinds: Set<DietaryRuleKind>): List<Set<DietaryRuleKind>> {
        if (kinds.isEmpty()) return names.map { emptySet() }
        return names.map { cachedTags(it, kinds) ?: heuristicTags(it, kinds) }
    }

    /**
     * The tags for logged items — and, for a dish (two or more ingredients), which of its ingredients
     * are the part that counts toward each rule ([RuleTagging.parts]), so a dish with a little fried
     * food in it counts only that part (see `DietaryTally`). Single foods go through [tagFoods]; dishes
     * get one online call with their ingredient lists. When that can't run, a dish keeps its whole-dish
     * tags. Used by the background refinement after a log.
     */
    suspend fun tagItems(
        foods: List<RuleFood>,
        kinds: Set<DietaryRuleKind>,
        timeoutMs: Long = TIMEOUT_MS
    ): List<RuleTagging> {
        if (foods.isEmpty() || kinds.isEmpty()) return foods.map { RuleTagging(emptySet()) }
        val result = arrayOfNulls<RuleTagging>(foods.size)

        val simple = foods.indices.filter { !foods[it].isDish }
        if (simple.isNotEmpty()) {
            val tags = tagFoods(simple.map { foods[it].name }, kinds, timeoutMs)
            simple.forEachIndexed { j, i -> result[i] = RuleTagging(tags.getOrElse(j) { emptySet() }) }
        }

        val dishes = foods.indices.filter { foods[it].isDish }
        val unknown = dishes.filter { cachedTagging(foods[it], kinds) == null }
        if (unknown.isNotEmpty() && canUseOnline()) {
            aiTagDishes(unknown.map { foods[it] }, kinds, timeoutMs)?.let { answers ->
                unknown.forEachIndexed { j, i -> answers.getOrNull(j)?.let { rememberTagging(foods[i], kinds, it) } }
            }
        }
        dishes.forEach { i -> result[i] = cachedTagging(foods[i], kinds) ?: RuleTagging(quickTags(listOf(foods[i].name), kinds).first()) }

        return result.map { it ?: RuleTagging(emptySet()) }
    }

    /**
     * [tagItems] with no network — what a log is saved with: a dish's remembered answer (parts and
     * all), otherwise the whole-food tags from [quickTags].
     */
    fun quickTagging(foods: List<RuleFood>, kinds: Set<DietaryRuleKind>): List<RuleTagging> {
        if (kinds.isEmpty()) return foods.map { RuleTagging(emptySet()) }
        val quick = quickTags(foods.map { it.name }, kinds)
        return foods.mapIndexed { i, food ->
            (if (food.isDish) cachedTagging(food, kinds) else null) ?: RuleTagging(quick[i])
        }
    }

    private val dishAnswers = object : LinkedHashMap<String, RuleTagging>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RuleTagging>?) = size > MAX_REMEMBERED_DISHES
    }

    private fun dishKey(food: RuleFood, kinds: Set<DietaryRuleKind>): String =
        kinds.sortedBy { it.id }.joinToString(",") { "${it.id}:${settings.dietaryRuleFor(it).definition.trim().hashCode()}" } +
            "|" + food.name.trim().lowercase() + "|" + food.ingredients.map { DietaryTally.key(it) }.sorted().joinToString(";")

    private fun cachedTagging(food: RuleFood, kinds: Set<DietaryRuleKind>): RuleTagging? =
        synchronized(dishAnswers) { dishAnswers[dishKey(food, kinds)] }

    private fun rememberTagging(food: RuleFood, kinds: Set<DietaryRuleKind>, tagging: RuleTagging) =
        synchronized(dishAnswers) { dishAnswers[dishKey(food, kinds)] = tagging }

    // ---- Remembered AI answers (per food + per rule note, so editing a note re-asks) ----

    private val answers = object : LinkedHashMap<String, Boolean>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > MAX_REMEMBERED
    }

    private fun answerKey(name: String, kind: DietaryRuleKind): String =
        "${kind.id}|${settings.dietaryRuleFor(kind).definition.trim().hashCode()}|${name.trim().lowercase()}"

    /** The AI's remembered tags for [name] across [kinds], or null if any kind hasn't been judged. */
    private fun cachedTags(name: String, kinds: Set<DietaryRuleKind>): Set<DietaryRuleKind>? = synchronized(answers) {
        val result = mutableSetOf<DietaryRuleKind>()
        for (kind in kinds) {
            val yes = answers[answerKey(name, kind)] ?: return null
            if (yes) result += kind
        }
        result
    }

    private fun remember(name: String, kinds: Set<DietaryRuleKind>, tags: Set<DietaryRuleKind>) = synchronized(answers) {
        kinds.forEach { kind -> answers[answerKey(name, kind)] = kind in tags }
    }

    /**
     * Which of [kinds] appear in a food **photo**. Vision needs the online model; offline there's no
     * text to match, so this returns empty (the gate then simply doesn't fire for photos offline).
     */
    suspend fun screenImage(bitmap: Bitmap, note: String, kinds: Set<DietaryRuleKind>): Set<DietaryRuleKind> {
        if (kinds.isEmpty() || !canUseOnline()) return emptySet()
        val raw = safeGenerate(prompt = screenPrompt(kinds, imageNote = note), image = bitmap, timeoutMs = GATE_TIMEOUT_MS)
            ?: return emptySet()
        return parseIdList(raw).mapNotNull { DietaryRuleKind.fromId(it) }.toSet().intersect(kinds)
    }

    /** Which of [kinds] appear in a free-text meal **description** (AI online, keyword match offline). */
    suspend fun screenText(text: String, kinds: Set<DietaryRuleKind>): Set<DietaryRuleKind> {
        if (kinds.isEmpty() || text.isBlank()) return emptySet()
        if (canUseOnline()) {
            val raw = safeGenerate(prompt = screenPrompt(kinds, description = text), timeoutMs = GATE_TIMEOUT_MS)
            if (raw != null) return parseIdList(raw).mapNotNull { DietaryRuleKind.fromId(it) }.toSet().intersect(kinds)
        }
        return heuristicTags(text, kinds)
    }

    // ---- Online ----

    private suspend fun aiTagFoods(
        names: List<String>,
        kinds: Set<DietaryRuleKind>,
        timeoutMs: Long
    ): List<Set<DietaryRuleKind>>? {
        val foodsJson = JSONArray().apply { names.forEach { put(it) } }.toString()
        val prompt = buildString {
            append("You label foods by which of these categories each one belongs to.\n\n")
            append(categoryLines(kinds))
            append("\nFor EACH food in the list, output the ids of every category it belongs to. A food ")
            append("can belong to several, or to none. Plain, unsweetened or whole foods (fruit, ")
            append("vegetables, grilled/boiled meat, plain rice, water, black coffee) belong to none.\n\n")
            append("Foods (JSON array, keep this order): ").append(foodsJson).append("\n\n")
            append("Reply with ONLY a JSON array of arrays — same length and order as the input — ")
            append("each inner array holding the matching category ids. ")
            append("Example for [\"Tiramisu\",\"Grilled chicken\",\"Cola\"]: [[\"dessert\"],[],[\"drink\"]]")
        }
        val raw = safeGenerate(prompt = prompt, timeoutMs = timeoutMs) ?: return null
        val matrix = parseIdMatrix(raw, names.size) ?: return null
        return matrix.map { ids -> ids.mapNotNull { DietaryRuleKind.fromId(it) }.toSet().intersect(kinds) }
    }

    /**
     * Dishes with their ingredients: which categories each counts toward and, per category, which
     * ingredients are the part that counts ("all" for a burger or a pizza). Null when the call fails.
     */
    private suspend fun aiTagDishes(
        dishes: List<RuleFood>,
        kinds: Set<DietaryRuleKind>,
        timeoutMs: Long
    ): List<RuleTagging>? {
        val input = JSONArray().apply {
            dishes.forEach { d ->
                put(org.json.JSONObject().put("food", d.name).put("ingredients", JSONArray(d.ingredients)))
            }
        }.toString()
        val prompt = buildString {
            append("You label dishes by which of these categories they count toward, and which of their ingredients are the part that counts.\n\n")
            append(categoryLines(kinds))
            append("\nEach entry is a dish with its ingredients (numbered from 0 in list order). A dish counts toward ")
            append("a category when it IS that kind of food as a whole — a burger, a pizza, a cake, a milkshake: ")
            append("then that category's part is \"all\". It also counts when only PART of it is — fried croutons on a ")
            append("salad, a fried schnitzel next to potato salad, fries beside a grilled steak: then the part is the ")
            append("indexes of just those ingredients (with any oil they were fried in, batter or breading). Plain, ")
            append("unsweetened or whole foods (vegetables, grilled/boiled meat, plain rice, salad, sauces on their own) ")
            append("are never the part that counts. A dish can count toward several categories, or none.\n\n")
            append("Dishes (JSON, keep this order): ").append(input).append("\n\n")
            append("Reply with ONLY a JSON array — same length and order as the input — of objects ")
            append("{\"tags\":[category ids],\"parts\":{\"<id>\":\"all\" or [ingredient indexes]}}. ")
            append("Example for [{\"food\":\"Caesar salad\",\"ingredients\":[\"romaine\",\"grilled chicken\",\"fried croutons\",\"dressing\"]},")
            append("{\"food\":\"Cheeseburger\",\"ingredients\":[\"bun\",\"beef patty\",\"cheese\"]},")
            append("{\"food\":\"Chicken rice bowl\",\"ingredients\":[\"rice\",\"chicken\",\"broccoli\"]}]: ")
            append("[{\"tags\":[\"fried\"],\"parts\":{\"fried\":[2]}},{\"tags\":[\"fried\"],\"parts\":{\"fried\":\"all\"}},{\"tags\":[],\"parts\":{}}]")
        }
        val raw = safeGenerate(prompt = prompt, timeoutMs = timeoutMs) ?: return null
        val arr = firstJsonArray(raw) ?: return null
        return dishes.mapIndexed { i, dish -> parseDishTagging(arr, i, dish, kinds) }
    }

    /** One dish's answer — an object as asked, or (leniently) a bare array of ids, which means "the whole dish". */
    private fun parseDishTagging(arr: JSONArray, i: Int, dish: RuleFood, kinds: Set<DietaryRuleKind>): RuleTagging {
        arr.optJSONArray(i)?.let { ids ->
            return RuleTagging((0 until ids.length()).mapNotNull { DietaryRuleKind.fromId(ids.optString(it).trim()) }.toSet().intersect(kinds))
        }
        val o = arr.optJSONObject(i) ?: return RuleTagging(emptySet())
        val tags = o.optJSONArray("tags")?.let { ids ->
            (0 until ids.length()).mapNotNull { DietaryRuleKind.fromId(ids.optString(it).trim()) }.toMutableSet()
        } ?: mutableSetOf()
        val parts = mutableMapOf<DietaryRuleKind, Set<String>>()
        val allNames = dish.ingredients.map { DietaryTally.key(it) }.toSet()
        o.optJSONObject("parts")?.let { p ->
            p.keys().forEach { id ->
                val kind = DietaryRuleKind.fromId(id.trim()) ?: return@forEach
                val idx = p.optJSONArray(id)
                if (idx == null) { tags += kind; return@forEach }  // "all" (or anything else): the whole dish
                val names = (0 until idx.length()).mapNotNull { dish.ingredients.getOrNull(idx.optInt(it, -1)) }
                    .map { DietaryTally.key(it) }.toSet()
                if (names.isEmpty()) return@forEach
                tags += kind
                // Every ingredient named is the whole dish anyway — store nothing, which means just that.
                if (names != allNames) parts[kind] = names
            }
        }
        val kept = tags.intersect(kinds)
        return RuleTagging(kept, parts.filterKeys { it in kept })
    }

    private fun screenPrompt(kinds: Set<DietaryRuleKind>, description: String? = null, imageNote: String = ""): String =
        buildString {
            if (description != null) {
                append("Does this meal description mention any of these food categories?\n\n")
                append(categoryLines(kinds))
                append("\nDescription: \"").append(description.take(500)).append("\"\n\n")
            } else {
                append("Look at this food photo. Which of these categories are present on it?\n\n")
                append(categoryLines(kinds))
                if (imageNote.isNotBlank()) append("\nUser note about the photo: \"").append(imageNote.take(200)).append("\"\n")
                append("\n")
            }
            append("Reply with ONLY a JSON array of the matching category ids (empty [] if none). ")
            append("Example: [\"dessert\"]")
        }

    /** Human-readable list of the categories in play, folding in each rule's "what counts" note. */
    private fun categoryLines(kinds: Set<DietaryRuleKind>): String = buildString {
        kinds.forEach { kind ->
            append("- \"").append(kind.id).append("\": ")
            append(
                when (kind) {
                    DietaryRuleKind.DESSERT -> "sweets and desserts — cakes, cookies, ice cream, chocolate, pastries, puddings, sweet baked goods."
                    DietaryRuleKind.FRIED -> "fried, fast or ultra-processed food — fries, fried chicken, burgers, pizza, nuggets, crisps, deep-fried items."
                    DietaryRuleKind.SUGARY_DRINK -> "sugary drinks — soft drinks, cola, sweetened juice, energy drinks, milkshakes, sweetened coffees."
                }
            )
            val note = settings.dietaryRuleFor(kind).definition.trim()
            if (note.isNotEmpty()) append(" The user adds: \"").append(note.take(200)).append("\".")
            append("\n")
        }
    }

    private suspend fun safeGenerate(prompt: String, image: Bitmap? = null, timeoutMs: Long): String? =
        withTimeoutOrNull(timeoutMs) {
            runCatching {
                gemini.generate(
                    prompt = prompt,
                    images = listOfNotNull(image),
                    temperature = 0f,
                    jsonMode = true,
                    // Non-thinking models — don't send a thinkingConfig they'd reject.
                    thinkingLevel = null,
                    models = settings.activeFastModels(),
                    // Single attempt + short socket timeouts, so this can't stall a log.
                    fast = true
                )
            }.onFailure { Log.d(TAG, "Fast classify failed, using heuristic: ${it.message}") }.getOrNull()
        }

    // ---- Offline heuristic ----

    private fun heuristicTags(name: String, kinds: Set<DietaryRuleKind>): Set<DietaryRuleKind> =
        // Whole words only — "cola" mustn't tag a chocolate bar, nor "soda" baking soda.
        kinds.filterTo(mutableSetOf()) { kind ->
            com.fitpal.app.domain.TextMatch.containsAny(name, kind.heuristicWords)
        }

    // ---- Parsing (tolerant of stray text around the JSON) ----

    /** Parse a JSON array of strings, e.g. `["dessert","drink"]`. */
    private fun parseIdList(raw: String): List<String> {
        val arr = firstJsonArray(raw) ?: return emptyList()
        return buildList { for (i in 0 until arr.length()) arr.optString(i).trim().takeIf { it.isNotEmpty() }?.let { add(it) } }
    }

    /** Parse a JSON array of arrays, padded/truncated to [size] rows. */
    private fun parseIdMatrix(raw: String, size: Int): List<List<String>>? {
        val arr = firstJsonArray(raw) ?: return null
        val rows = ArrayList<List<String>>(size)
        for (i in 0 until size) {
            val inner = arr.optJSONArray(i)
            rows.add(
                if (inner == null) emptyList()
                else buildList { for (j in 0 until inner.length()) inner.optString(j).trim().takeIf { it.isNotEmpty() }?.let { add(it) } }
            )
        }
        return rows
    }

    /** Grab the first `[ … ]` in the reply and parse it, tolerating code fences / prose around it. */
    private fun firstJsonArray(raw: String): JSONArray? {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        return runCatching { JSONArray(raw.substring(start, end + 1)) }.getOrNull()
    }

    companion object {
        private const val TAG = "DietaryClassifier"
        // Background refinement bound — sits just above GeminiClient's fast-mode socket timeouts
        // (~8s connect + 12s read); on timeout we keep the heuristic. Cancellation closes the socket,
        // so this is a real cap now, not a hint.
        private const val TIMEOUT_MS = 22_000L
        /** When a person is waiting on the answer (the pre-log gate): short, then the keyword match. */
        const val GATE_TIMEOUT_MS = 6_000L
        private const val MAX_REMEMBERED = 400
        private const val MAX_REMEMBERED_DISHES = 100
    }
}

/** A logged item to tag for the food-limit tally: its name and, for a dish, its ingredients' names. */
data class RuleFood(val name: String, val ingredients: List<String> = emptyList()) {
    /** Two or more distinct ingredients — only then can part of it count. */
    val isDish: Boolean get() = ingredients.map { DietaryTally.key(it) }.distinct().size >= 2
}

/**
 * Which food limits an item counts toward ([kinds]), and for a dish which of its ingredients are the
 * part that counts toward each ([parts], ingredient names as [DietaryTally.key]). A kind with no parts
 * counts the whole item.
 */
data class RuleTagging(
    val kinds: Set<DietaryRuleKind>,
    val parts: Map<DietaryRuleKind, Set<String>> = emptyMap()
)
