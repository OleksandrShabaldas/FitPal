package com.fitpal.app.domain

import com.fitpal.app.domain.FeatureSuggestions.Item
import com.fitpal.app.domain.FeatureSuggestions.MealTime
import com.fitpal.app.domain.model.Feature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FeatureSuggestionsTest {

    private val today = LocalDate.of(2026, 10, 22)
    private fun day(n: Int) = today.minusDays(n.toLong())

    @Test fun needsAWeekOfLogging() {
        assertFalse(FeatureSuggestions.mayShow(6, today, null, null))
        assertTrue(FeatureSuggestions.mayShow(7, today, null, null))
    }

    @Test fun atMostOnceAWeek() {
        assertTrue(FeatureSuggestions.mayShow(9, today, today, null))
        assertFalse(FeatureSuggestions.mayShow(9, today, day(3), null))
        assertTrue(FeatureSuggestions.mayShow(9, today, day(7), null))
        assertFalse(FeatureSuggestions.mayShow(9, today, today, today))
    }

    @Test fun coffeeMostDaysSuggestsCaffeine() {
        val items = (0 until 5).map { Item(day(it), "Flat white", 250f, null) }
        val s = FeatureSuggestions.pick(items, emptyList(), emptySet(), emptySet())
        assertEquals(Feature.CAFFEINE, s?.feature)
    }

    @Test fun dessertsSuggestALimit() {
        val items = (0 until 4).map { Item(day(it), "Chocolate cake", 120f, 0f) }
        val s = FeatureSuggestions.pick(items, emptyList(), emptySet(), emptySet())
        assertEquals(Feature.LIMITS, s?.feature)
        assertEquals("Want a daily cap for desserts?", s?.text)
    }

    @Test fun mealsInsideTenHoursSuggestFasting() {
        val meals = (0 until 6).flatMap { listOf(MealTime(day(it), 11 * 60 + 30), MealTime(day(it), 19 * 60)) }
        assertEquals(6, FeatureSuggestions.fittingWindowDays(meals))
        assertEquals(Feature.FASTING, FeatureSuggestions.pick(emptyList(), meals, emptySet(), emptySet())?.feature)
    }

    @Test fun spreadOutMealsDontSuggestFasting() {
        val meals = (0 until 6).flatMap { listOf(MealTime(day(it), 7 * 60), MealTime(day(it), 22 * 60)) }
        assertEquals(Feature.TRAIL, FeatureSuggestions.pick(emptyList(), meals, emptySet(), emptySet())?.feature)
    }

    @Test fun neverWhatsOnOrDismissed() {
        val items = (0 until 5).map { Item(day(it), "Espresso", 30f, null) }
        val s = FeatureSuggestions.pick(items, emptyList(), setOf(Feature.CAFFEINE), setOf("trail"))
        assertNull(s)
    }
}
