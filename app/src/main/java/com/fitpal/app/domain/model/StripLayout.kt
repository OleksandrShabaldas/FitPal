package com.fitpal.app.domain.model

/** How wide one item in Home's strip is: the whole row, half of it, or a quarter. */
enum class StripWidth { WIDE, HALF, QUARTER }

/** One item in the strip and how wide it's drawn. */
data class StripCell(val key: String, val width: StripWidth)

/**
 * The strip's layout rule (docs/redesign/05): rows of up to four, and every row fills the whole
 * width — 1 item is wide, 2 are halves, 3 are a half and two quarters (the half is the user's
 * "bigger" pick when it's in that row, else the row's first), 4 are quarters. Five or more wrap:
 * rows of four in order, the rest laid out by the same rule. Nothing scrolls or hides.
 */
object StripLayout {
    const val PER_ROW = 4

    fun rows(items: List<String>, bigger: String?): List<List<StripCell>> =
        items.chunked(PER_ROW).map { row ->
            when (row.size) {
                1 -> listOf(StripCell(row[0], StripWidth.WIDE))
                2 -> row.map { StripCell(it, StripWidth.HALF) }
                3 -> {
                    val big = bigger?.takeIf { it in row } ?: row.first()
                    row.map { StripCell(it, if (it == big) StripWidth.HALF else StripWidth.QUARTER) }
                }
                else -> row.map { StripCell(it, StripWidth.QUARTER) }
            }
        }

    /** True when some row has three items, so the "bigger in a row of three" choice matters. */
    fun hasRowOfThree(count: Int): Boolean = count % PER_ROW == 3
}
