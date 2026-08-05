package com.confused.anikuta.core.smartmatcher

/**
 * Result of a single title match attempt.
 *
 * Sealed so the caller must handle every case (CORE_RULES §7 — explicit contracts).
 */
sealed class MatchResult {
    /**
     * A confident match was found.
     *
     * @param score Similarity ratio (0.0..1.0) that exceeded the threshold.
     * @param matchedTitle The normalized title that matched.
     */
    data class Match(val score: Float, val matchedTitle: String) : MatchResult()

    /**
     * No candidate exceeded the threshold.
     *
     * @param bestScore The highest score seen (still below threshold).
     * @param reason Why no match was found (e.g. "best=0.65 threshold=0.80").
     */
    data class NoMatch(val bestScore: Float, val reason: String) : MatchResult()

    /**
     * The matcher skipped (e.g. strategy = MANUAL, or auto-link disabled for this source).
     */
    data class Skipped(val reason: String) : MatchResult()

    /**
     * An error occurred during matching (e.g. AniList API failed).
     */
    data class Error(val message: String) : MatchResult()
}
