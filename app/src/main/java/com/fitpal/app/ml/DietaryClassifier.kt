package com.fitpal.app.ml

import android.graphics.Bitmap
import android.util.Log
import com.fitpal.app.data.repository.SettingsRepository
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

    private fun heuristicTags(name: String, kinds: Set<DietaryRuleKind>): Set<DietaryRuleKind> {
        val lower = name.lowercase()
        return kinds.filterTo(mutableSetOf()) { kind ->
            kind.heuristicWords.any { lower.contains(it) }
        }
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
    }
}
