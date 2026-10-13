package com.fitpal.app.ml

/**
 * A saved overview split into the prose the user reads and the tagged lines the coach ends it with:
 *  - `FOCUS:` the one thing to do next (green card), `WATCH:` the one thing to keep an eye on (red card),
 *  - `QUESTION:` the coach's question for the user, with `ANSWERS:` a few tap-answers split by " | ".
 * Older overviews have none of these lines; every field is then null / empty and only prose remains.
 */
data class ParsedReview(
    val prose: String,
    val focus: String?,
    val watch: String?,
    val question: String?,
    val answers: List<String>
)

/** Parses the coach's overview text — shared by the review screen and the coach's own memory. */
object ReviewText {

    /**
     * A tagged card line, tolerating leading markdown (**, -, >, #). Uppercase-only on purpose (the
     * prompt always emits it that way) so a prose sentence like "Watch your portions" can't be misread.
     */
    private val TAGGED_LINE = Regex("^[\\s*_#>\\-]*(FOCUS|WATCH|QUESTION|ANSWERS)\\s*:\\s*(.+)$")

    fun parse(text: String): ParsedReview {
        val tags = mutableMapOf<String, String>()
        val prose = StringBuilder()
        text.lines().forEach { line ->
            val m = TAGGED_LINE.find(line)
            if (m == null) {
                prose.appendLine(line)
                return@forEach
            }
            val body = m.groupValues[2].trim().trim('*', '_', ' ')
            if (body.isNotBlank()) tags.putIfAbsent(m.groupValues[1], body)
        }
        val answers = tags["ANSWERS"].orEmpty()
            .split('|')
            .map { it.trim().trim('"', '*', ' ') }
            .filter { it.isNotEmpty() && it.length <= 60 }
            .distinct()
            .take(4)
        return ParsedReview(
            prose = prose.toString().trim(),
            focus = tags["FOCUS"],
            watch = tags["WATCH"],
            question = tags["QUESTION"]?.takeIf { it.length > 3 },
            answers = answers
        )
    }
}
