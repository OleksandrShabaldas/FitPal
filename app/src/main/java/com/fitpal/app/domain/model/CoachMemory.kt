package com.fitpal.app.domain.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * What the AI coach knows about the user (the 17 Nov review, #2), in the sections that matter for coaching.
 * The coach writes it inside an overview's own call (no extra API call), from the answers the user gave since
 * the last overview; the user can read, edit, remove and add lines on the Coach tab. Every overview, the meal
 * coach's note and the meal planner read it.
 */
enum class MemorySection(val id: String, val title: String, val hint: String) {
    PATTERNS("patterns", "Patterns", "when and how they eat, how they keep their fast"),
    TRIGGERS("triggers", "What sets it off", "stress, tiredness, boredom, social or family meals, long days"),
    HELPS("helps", "What helps", "tools and habits that worked, and what didn't"),
    LIFE("life", "Your life right now", "work, schedule, family, health, preferences"),
    OPEN("open", "Open questions", "things to check in a later overview");

    companion object {
        fun of(id: String?): MemorySection? = entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) }
    }
}

/** One line of the memory, as the coach and the page see it. */
data class MemoryLine(
    val id: Long,
    val section: MemorySection,
    val text: String,
    /** The day it came from ("2026-11-12"). */
    val sourceDate: String,
    /** Written or edited by the user — the coach never changes or removes it. */
    val mine: Boolean
)

/** The coach's changes to its memory from one overview — see [CoachMemoryParser]. */
data class MemoryDiff(
    val add: List<Added> = emptyList(),
    val update: Map<Long, String> = emptyMap(),
    val remove: Set<Long> = emptySet()
) {
    data class Added(val section: MemorySection, val text: String, val date: String)
    val isEmpty: Boolean get() = add.isEmpty() && update.isEmpty() && remove.isEmpty()
}

/**
 * Reads the coach's `MEMORY:` line off the end of an overview: one JSON object
 * `{"add":[{"section","text","date"}],"update":[{"id","text"}],"remove":[ids]}`. Lenient about shape, strict
 * about what it may touch: unknown sections, unknown ids, empty text and any change to a line the user wrote
 * ([MemoryLine.mine]) are dropped. Pure, so it's unit-tested.
 */
object CoachMemoryParser {

    /** A memory line is one plain sentence; anything longer is cut. */
    const val MAX_LINE = 240

    /** Most lines the memory keeps; extra additions beyond it are dropped. */
    const val MAX_LINES = 40

    /**
     * Split the `MEMORY:` line (the last line starting with it) off [text]: (what the user reads, the raw
     * JSON or null). Anchored to the start of a line so "memory:" in the prose can't cut the overview.
     */
    fun split(text: String): Pair<String, String?> {
        val lines = text.lines()
        val idx = lines.indexOfLast { it.trimStart().startsWith("MEMORY:", ignoreCase = true) }
        if (idx < 0) return text to null
        val before = lines.subList(0, idx).joinToString("\n").trimEnd()
        val raw = lines.subList(idx, lines.size).joinToString("\n").trimStart().substring("MEMORY:".length).trim()
        return before to raw.ifBlank { null }
    }

    /** The changes in [raw], checked against the [current] lines; null when it can't be read at all. */
    fun parse(raw: String?, current: List<MemoryLine>, fallbackDate: String): MemoryDiff? {
        if (raw.isNullOrBlank()) return null
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val o = runCatching { JSONObject(raw.substring(start, end + 1)) }.getOrNull() ?: return null
        val byId = current.associateBy { it.id }
        fun clean(s: String) = s.replace(Regex("\\s+"), " ").trim().take(MAX_LINE)

        val update = mutableMapOf<Long, String>()
        o.optJSONArray("update")?.forEachObject { u ->
            val id = u.optLong("id", -1)
            val line = byId[id] ?: return@forEachObject
            val text = clean(u.optString("text"))
            if (!line.mine && text.isNotEmpty() && text != line.text) update[id] = text
        }
        val remove = mutableSetOf<Long>()
        o.optJSONArray("remove")?.let { arr ->
            for (i in 0 until arr.length()) {
                val id = arr.optLong(i, -1)
                val line = byId[id] ?: continue
                if (!line.mine) remove += id
            }
        }
        val known = current.filter { it.id !in remove }.map { it.text.lowercase() }.toMutableSet()
        val room = (MAX_LINES - (current.size - remove.size)).coerceAtLeast(0)
        val add = mutableListOf<MemoryDiff.Added>()
        o.optJSONArray("add")?.forEachObject { a ->
            if (add.size >= room) return@forEachObject
            val section = MemorySection.of(a.optString("section")) ?: return@forEachObject
            val text = clean(a.optString("text"))
            if (text.isEmpty() || !known.add(text.lowercase())) return@forEachObject
            val date = a.optString("date").trim().takeIf { Regex("\\d{4}-\\d{2}-\\d{2}").matches(it) } ?: fallbackDate
            add += MemoryDiff.Added(section, text, date)
        }
        return MemoryDiff(add, update - remove, remove)
    }

    /** The memory as the prompts show it: by section, each line with its id and date; "their words" for theirs. */
    fun promptBlock(lines: List<MemoryLine>, withIds: Boolean): String = buildString {
        MemorySection.entries.forEach { s ->
            val inSection = lines.filter { it.section == s }
            if (inSection.isEmpty()) return@forEach
            appendLine("${s.title}:")
            inSection.forEach { l ->
                val id = if (withIds) "[${l.id}] " else ""
                val whose = if (l.mine) ", their words" else ""
                appendLine("- $id(${l.sourceDate}$whose) ${l.text}")
            }
        }
    }.trimEnd()

    private inline fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
        for (i in 0 until length()) optJSONObject(i)?.let(block)
    }
}
