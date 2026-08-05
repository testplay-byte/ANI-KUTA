package com.confused.anikuta.core.smartmatcher

/**
 * Configuration for [SmartMatcher].
 *
 * @param strategy How aggressively to match titles.
 *   - [FUZZY]: Levenshtein similarity + year bonus, threshold-based.
 *   - [STRICT]: Exact normalized match only (after punctuation/case normalization).
 *   - [MANUAL]: Never auto-match — always show the manual link sheet.
 * @param threshold Minimum similarity ratio (0.0..1.0) for a FUZZY match.
 *   Default 0.80 — strict enough to reject "Frieren" vs "Bocchi" but lenient
 *   enough to accept "Frieren: Beyond Journey's End" vs "Frieren Sousou no Mahou"
 *   after normalization (these share the "frieren" token).
 *   At 0.80, the Levenshtein ratio needs to be high — year bonus (+0.10) and
 *   contains bonus (+0.05) help push borderline cases over the line.
 * @param yearBonus Bonus added to similarity when years match (capped at 1.0).
 * @param containsBonus Bonus when one title's core tokens fully contain the other's.
 */
data class SmartMatcherConfig(
    val strategy: MatchStrategy = MatchStrategy.FUZZY,
    val threshold: Float = 0.80f,
    val yearBonus: Float = 0.10f,
    val containsBonus: Float = 0.05f,
)

enum class MatchStrategy {
    FUZZY,
    STRICT,
    MANUAL,
}
