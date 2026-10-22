package com.fitpal.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StripLayoutTest {

    private val keys = (1..9).map { "t$it" }
    private fun widths(n: Int, bigger: String? = null) =
        StripLayout.rows(keys.take(n), bigger).map { row -> row.map { it.width } }

    private val W = StripWidth.WIDE
    private val H = StripWidth.HALF
    private val Q = StripWidth.QUARTER

    @Test fun none() = assertEquals(emptyList<List<StripWidth>>(), widths(0))
    @Test fun one() = assertEquals(listOf(listOf(W)), widths(1))
    @Test fun two() = assertEquals(listOf(listOf(H, H)), widths(2))
    @Test fun three() = assertEquals(listOf(listOf(H, Q, Q)), widths(3))
    @Test fun four() = assertEquals(listOf(listOf(Q, Q, Q, Q)), widths(4))
    @Test fun five() = assertEquals(listOf(listOf(Q, Q, Q, Q), listOf(W)), widths(5))
    @Test fun six() = assertEquals(listOf(listOf(Q, Q, Q, Q), listOf(H, H)), widths(6))
    @Test fun seven() = assertEquals(listOf(listOf(Q, Q, Q, Q), listOf(H, Q, Q)), widths(7))
    @Test fun eight() = assertEquals(listOf(listOf(Q, Q, Q, Q), listOf(Q, Q, Q, Q)), widths(8))
    @Test fun nine() = assertEquals(listOf(listOf(Q, Q, Q, Q), listOf(Q, Q, Q, Q), listOf(W)), widths(9))

    @Test fun biggerStaysInItsPlace() = assertEquals(listOf(listOf(Q, H, Q)), widths(3, bigger = "t2"))

    @Test fun biggerInAnotherRowFallsBackToFirst() = assertEquals(listOf(listOf(Q, Q, Q, Q), listOf(H, Q, Q)), widths(7, bigger = "t2"))

    @Test fun biggerInTheLastRow() = assertEquals(listOf(listOf(Q, Q, Q, Q), listOf(Q, Q, H)), widths(7, bigger = "t7"))

    @Test fun keepsOrder() = assertEquals(keys.take(6), StripLayout.rows(keys.take(6), null).flatten().map { it.key })

    @Test fun rowOfThree() {
        assertTrue(StripLayout.hasRowOfThree(3))
        assertTrue(StripLayout.hasRowOfThree(7))
        assertEquals(false, StripLayout.hasRowOfThree(4))
    }
}
