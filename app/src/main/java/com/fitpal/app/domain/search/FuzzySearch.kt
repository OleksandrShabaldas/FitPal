package com.fitpal.app.domain.search

import java.text.Normalizer

/**
 * Small, dependency-free fuzzy matcher for searching the user's logged history offline.
 *
 * It's built to be forgiving in two everyday ways:
 *  - **Typos** — "brwnie" still finds "brownie" (one edit away).
 *  - **A word inside a longer name** — "kurant" finds "pie with apples and currant" (the token
 *    "currant" is two edits from "kurant").
 *
 * [score] returns a higher number for a better match and 0 for no match; [matches] is the boolean
 * shortcut. Everything runs on-device with no libraries, in keeping with the app's offline rule.
 */
object FuzzySearch {

    /** True when [query] is a good enough match for [candidate]. */
    fun matches(query: String, candidate: String): Boolean = score(query, candidate) > 0

    /**
     * Match score for [candidate] against [query]. Takes the stronger of two signals: whole-string
     * prefix/substring hits, and per-token typo-tolerant matching (every query word must land on
     * some candidate word, so "red apple" doesn't match a plain "apple").
     */
    fun score(query: String, candidate: String): Int {
        val qn = normalize(query)
        val cn = normalize(candidate)
        if (qn.isEmpty() || cn.isEmpty()) return 0

        // Whole-string signals are the strongest — the user typed the name, or how it starts.
        var best = when {
            cn == qn -> 1000
            cn.startsWith(qn) -> 800
            cn.contains(qn) -> 600
            else -> 0
        }

        val qTokens = qn.split(' ').filter { it.isNotBlank() }
        val cTokens = cn.split(' ').filter { it.isNotBlank() }
        if (qTokens.isNotEmpty() && cTokens.isNotEmpty()) {
            var sum = 0
            var allMatched = true
            for (qt in qTokens) {
                var tokenBest = 0
                for (ct in cTokens) tokenBest = maxOf(tokenBest, tokenScore(qt, ct))
                if (tokenBest == 0) { allMatched = false; break }
                sum += tokenBest
            }
            if (allMatched) best = maxOf(best, sum)
        }
        return best
    }

    /** Score one query token against one candidate token. */
    private fun tokenScore(q: String, c: String): Int = when {
        q == c -> 100
        c.startsWith(q) -> 85
        q.startsWith(c) -> 65
        c.contains(q) -> 55
        // Only fuzz words long enough that an edit or two isn't just noise.
        q.length >= 4 -> {
            val threshold = when {
                q.length <= 5 -> 1
                q.length <= 8 -> 2
                else -> 3
            }
            val d = osaDistance(q, c)
            if (d <= threshold) 50 - d * 12 else 0
        }
        else -> 0
    }

    /** Lowercase, strip accents, reduce punctuation to single spaces. */
    private fun normalize(s: String): String {
        val stripped = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
        return buildString {
            var lastSpace = false
            for (ch in stripped) {
                if (ch.isLetterOrDigit()) {
                    append(ch); lastSpace = false
                } else if (!lastSpace) {
                    append(' '); lastSpace = true
                }
            }
        }.trim()
    }

    /** Optimal string alignment distance (Levenshtein + adjacent transpositions). */
    private fun osaDistance(a: String, b: String): Int {
        val n = a.length
        val m = b.length
        if (n == 0) return m
        if (m == 0) return n
        val d = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) d[i][0] = i
        for (j in 0..m) d[0][j] = j
        for (i in 1..n) {
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                d[i][j] = minOf(
                    d[i - 1][j] + 1,      // deletion
                    d[i][j - 1] + 1,      // insertion
                    d[i - 1][j - 1] + cost // substitution
                )
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1) // transposition
                }
            }
        }
        return d[n][m]
    }

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
}
