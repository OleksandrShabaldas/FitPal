package com.fitpal.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** The 17 Nov review (docs/redesign/18). */
class Nov17ChangesTest {

    @org.junit.Before
    fun englishDates() { java.util.Locale.setDefault(java.util.Locale.UK) }

    private val day = LocalDate.of(2026, 11, 17).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    private fun at(h: Int, m: Int = 0) = day + (h * 60 + m) * 60_000L
    private fun minutes(millis: Long) = ((millis - day) / 60_000L).toInt()

    // ---- #1 peak focus windows ----

    @Test
    fun exampleDayHasTwoWindows() {
        // The review's example: a double espresso at 8:30 and a latte at 14:00, 5-hour half-life.
        val doses = listOf(CaffeineDose(at(8, 30), 126f, "Double espresso", false), CaffeineDose(at(14), 63f, "Latte", false))
        val w = Caffeine.peakWindows(doses, at(6), at(30), 5f)
        assertEquals(2, w.size)
        assertNear(8 * 60 + 50, minutes(w[0].startMillis)); assertNear(11 * 60 + 50, minutes(w[0].endMillis))
        assertNear(14 * 60 + 10, minutes(w[1].startMillis)); assertNear(17 * 60 + 5, minutes(w[1].endMillis))
        assertTrue(w.all { it.peakMg >= CaffeineSettings.SLEEP_FRIENDLY_MG })
    }

    @Test
    fun aSmallDrinkHasNoWindow() {
        val w = Caffeine.peakWindows(listOf(CaffeineDose(at(9), 30f, "Green tea", false)), at(6), at(30), 5f)
        assertTrue(w.isEmpty())
    }

    @Test
    fun nothingDrunkHasNoWindow() {
        assertTrue(Caffeine.peakWindows(emptyList(), at(6), at(30), 5f).isEmpty())
    }

    @Test
    fun twoCoffeesCloseTogetherMakeOneWindow() {
        val doses = listOf(CaffeineDose(at(9), 80f, "Coffee", false), CaffeineDose(at(10), 80f, "Coffee", false))
        val w = Caffeine.peakWindows(doses, at(6), at(30), 5f)
        assertEquals(1, w.size)
        // The level keeps climbing after the second coffee, so the one window starts after it and runs well on.
        assertTrue(w[0].startMillis in at(10)..at(11) && w[0].endMillis > at(12))
    }

    @Test
    fun aLongerHalfLifeWidensTheWindow() {
        val dose = listOf(CaffeineDose(at(9), 100f, "Coffee", false))
        val short = Caffeine.peakWindows(dose, at(6), at(30), 3f).single()
        val long = Caffeine.peakWindows(dose, at(6), at(30), 8f).single()
        assertTrue(long.endMillis - long.startMillis > short.endMillis - short.startMillis)
    }

    // ---- #3 skipped weeks and months ----

    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun finishedWeeksAndMonthsWithFoodAreSkipped() {
        // Thursday 19 Nov 2026: the This week row points at Monday 16 Nov, This month at November.
        val today = d("2026-11-19")
        val logged = setOf(d("2026-11-04"), d("2026-11-05"), d("2026-10-28"), d("2026-10-10"), d("2026-11-18"))
        val list = SkippedReviews.find(today, logged, written = emptySet(), dismissed = emptySet())
        val ids = list.map { it.id }
        assertTrue(ids.toString(), "weekly|2026-11-02" in ids)   // 2–8 Nov
        assertTrue(ids.toString(), "weekly|2026-10-26" in ids)   // 26 Oct – 1 Nov
        assertTrue(ids.toString(), "weekly|2026-10-05" in ids)   // 5–11 Oct
        assertTrue(ids.toString(), "monthly|2026-10" in ids)
        assertTrue("weekly|2026-11-16" !in ids)         // this week: not over, has its own row
        assertTrue("monthly|2026-11" !in ids)           // this month
        assertTrue("weekly|2026-11-09" !in ids)         // nothing logged that week
        assertEquals("weekly|2026-11-02", ids.first())  // newest first
        val w = list.first { it.id == "weekly|2026-11-02" }
        assertEquals("Week of 2 Nov", w.title)
        assertEquals("2–8 Nov · 2 days logged", w.subtitle)
        assertEquals("26 Oct – 1 Nov · 1 day logged", list.first { it.id == "weekly|2026-10-26" }.subtitle)
        assertEquals("2 days logged", list.first { it.id == "monthly|2026-10" }.subtitle)
    }

    @Test
    fun writtenDismissedAndOldPeriodsAreLeftOut() {
        val today = d("2026-11-19")
        val logged = setOf(d("2026-11-04"), d("2026-10-10"), d("2026-07-15"))
        val list = SkippedReviews.find(today, logged, written = setOf("weekly|2026-11-02"), dismissed = setOf("monthly|2026-10"))
        val ids = list.map { it.id }
        assertTrue("weekly|2026-11-02" !in ids)
        assertTrue("monthly|2026-10" !in ids)
        assertTrue(ids.none { it.contains("2026-07") })  // older than three months
    }

    @Test
    fun onAMondayLastWeekHasItsOwnRow() {
        // Monday 16 Nov 2026: "This week" opens last week (9–15 Nov), so it isn't listed as skipped.
        val list = SkippedReviews.find(d("2026-11-16"), setOf(d("2026-11-12")), emptySet(), emptySet())
        assertTrue(list.none { it.id == "weekly|2026-11-09" })
    }

    @Test
    fun earlyInAMonthLastMonthHasItsOwnRow() {
        // 3 Nov: "This month" opens October for the first week, so October isn't listed as skipped.
        val list = SkippedReviews.find(d("2026-11-03"), setOf(d("2026-10-12")), emptySet(), emptySet())
        assertTrue(list.none { it.id == "monthly|2026-10" })
    }

    // ---- #2 the coach memory ----

    private val lines = listOf(
        com.fitpal.app.domain.model.MemoryLine(1, com.fitpal.app.domain.model.MemorySection.TRIGGERS, "Long shifts lead to big dinners.", "2026-11-12", mine = false),
        com.fitpal.app.domain.model.MemoryLine(2, com.fitpal.app.domain.model.MemorySection.LIFE, "Weekend dinners are with family.", "2026-11-10", mine = true),
        com.fitpal.app.domain.model.MemoryLine(3, com.fitpal.app.domain.model.MemorySection.OPEN, "Did the 17:00 snack help?", "2026-11-14", mine = false)
    )

    @Test
    fun memoryLineIsSplitOffTheOverview() {
        val text = listOf("Prose about the day.", "FOCUS: a", "WATCH: b", "QUESTION: c?", "ANSWERS: x | y", "MEMORY: {\"add\":[]}")
            .joinToString("\n")
        val (review, raw) = com.fitpal.app.domain.model.CoachMemoryParser.split(text)
        assertTrue(review.endsWith("ANSWERS: x | y"))
        assertEquals("{\"add\":[]}", raw)
        // "memory:" inside the prose doesn't cut anything.
        assertEquals("Your memory: fine.", com.fitpal.app.domain.model.CoachMemoryParser.split("Your memory: fine.").first)
    }

    @Test
    fun memoryDiffNeverTouchesTheUsersOwnLines() {
        val raw = """{"add":[{"section":"helps","text":"A planned 17:00 snack keeps dinner smaller.","date":"2026-11-16"},
            {"section":"nonsense","text":"x"},{"section":"triggers","text":"Long shifts lead to big dinners."}],
            "update":[{"id":2,"text":"changed"},{"id":1,"text":"Long shifts with no lunch lead to big dinners."},{"id":99,"text":"?"}],
            "remove":[2,3,42]}"""
        val diff = com.fitpal.app.domain.model.CoachMemoryParser.parse(raw, lines, "2026-11-19")!!
        assertEquals(1, diff.add.size)                                  // unknown section and duplicate dropped
        assertEquals("2026-11-16", diff.add.single().date)
        assertEquals(mapOf(1L to "Long shifts with no lunch lead to big dinners."), diff.update)  // not #2 (theirs), not #99
        assertEquals(setOf(3L), diff.remove)                           // not #2 (theirs), not #42
    }

    @Test
    fun unreadableMemoryLineChangesNothing() {
        assertEquals(null, com.fitpal.app.domain.model.CoachMemoryParser.parse("not json", lines, "2026-11-19"))
        assertEquals(null, com.fitpal.app.domain.model.CoachMemoryParser.parse(null, lines, "2026-11-19"))
    }

    // ---- #4 tags ----

    @Test
    fun aMealsTagsSplitIntoSituationAndFeelings() {
        val tags = com.fitpal.app.domain.model.TagSettings()
        val (situation, feelings) = tags.split(setOf("Home", "Tired", "Gym day"))
        assertEquals(setOf("Home", "Gym day"), situation)   // an unknown tag goes with the situation row
        assertEquals(setOf("Tired"), feelings)
        assertTrue(!tags.canAdd("home"))                     // already there, any case
        assertTrue(tags.canAdd("Anxious"))
        val back = com.fitpal.app.domain.model.TagSettings.fromJson(tags.copy(feelings = tags.feelings.drop(1)).toJson())
        assertEquals(tags.feelings.drop(1), back.feelings)
    }

    private fun assertNear(expected: Int, actual: Int, slack: Int = 5) =
        assertTrue("expected ~$expected, was $actual", kotlin.math.abs(expected - actual) <= slack)
}
