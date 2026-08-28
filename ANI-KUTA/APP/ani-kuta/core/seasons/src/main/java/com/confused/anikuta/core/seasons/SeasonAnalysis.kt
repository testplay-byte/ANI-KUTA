package com.confused.anikuta.core.seasons

/**
 * D-312: Whole-list season analysis result from [SeasonDetector.analyze].
 *
 * Carries everything a consumer needs in ONE call — the per-episode
 * assignments, the season inventory, and the activation decision — so callers
 * never re-derive thresholds (the D-307 activation rule used to live in the
 * feature module; now it's owned here, consistently).
 */
data class SeasonAnalysis(
    /** Per-episode assignments, parallel to the analyzed name list. */
    val assignments: List<SeasonAssignment>,
    /** Distinct assigned season numbers, ascending. */
    val seasons: List<Int>,
    /** Episodes with ANY season assignment (name tag or provider hint). */
    val taggedCount: Int,
    /** taggedCount / total (0..1). */
    val confidence: Float,
    /**
     * The activation decision: does this list genuinely represent a
     * multi-season series? Requires BOTH ≥2 distinct seasons AND ≥50%
     * coverage (a couple of mislabeled episodes in a 1000-episode list must
     * not hijack it into season mode). The user's "Organize by" settings
     * toggle remains the manual override on top of this.
     */
    val isMultiSeason: Boolean,
    /** How many episodes were season-tagged by NAME vs by provider hint. */
    val nameTaggedCount: Int,
    val providerHintedCount: Int,
)
