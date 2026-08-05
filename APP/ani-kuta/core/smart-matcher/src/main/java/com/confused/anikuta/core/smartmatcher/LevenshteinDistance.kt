package com.confused.anikuta.core.smartmatcher

/**
 * Character-level Levenshtein edit distance.
 *
 * The classic DP algorithm — O(m*n) time, O(n) space (two-row optimization).
 * Used by [SmartMatcher] to compute similarity ratio between two normalized titles.
 *
 * CORE_RULES §10: No external fuzzy-match library — small, self-contained utility.
 */
object LevenshteinDistance {

    /**
     * Compute the edit distance between [a] and [b].
     *
     * Returns the minimum number of single-character insertions, deletions, or
     * substitutions needed to transform [a] into [b].
     */
    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        // Two-row DP: previous row + current row
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)

        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,        // insertion
                    prev[j] + 1,            // deletion
                    prev[j - 1] + cost,     // substitution
                )
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[b.length]
    }

    /**
     * Compute similarity ratio between [a] and [b] — 0.0 (totally different) to 1.0 (identical).
     *
     * ratio = 1 - (distance / max(len(a), len(b)))
     */
    fun similarity(a: String, b: String): Float {
        if (a.isEmpty() && b.isEmpty()) return 1f
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return 1f
        val dist = distance(a, b)
        return 1f - (dist.toFloat() / maxLen.toFloat())
    }
}
