package com.confused.anikuta.core.smartmatcher

/**
 * Result of an auto-link attempt for an extension anime entry.
 *
 * Sealed so the caller (DetailsViewModel) must handle every case explicitly.
 * CORE_RULES §7 — explicit contracts between layers.
 */
sealed class AutoLinkResult {
    /**
     * A previously-cached link was found for this (sourceId, animeUrl).
     * The caller should fetch + merge the AniList data for this ID.
     */
    data class Cached(val anilistId: Int) : AutoLinkResult()

    /**
     * Auto-link found a confident AniList match.
     */
    data class Matched(val anilistId: Int, val score: Float) : AutoLinkResult()

    /**
     * No confident match — caller shows the manual link sheet.
     */
    data class NoMatch(val bestScore: Float, val searchedTitle: String) : AutoLinkResult()

    /**
     * Auto-link was skipped (disabled for this source, or strategy = MANUAL).
     */
    data class Skipped(val reason: String) : AutoLinkResult()

    /**
     * An error occurred (e.g. AniList API failed, network down).
     */
    data class Error(val message: String) : AutoLinkResult()
}
