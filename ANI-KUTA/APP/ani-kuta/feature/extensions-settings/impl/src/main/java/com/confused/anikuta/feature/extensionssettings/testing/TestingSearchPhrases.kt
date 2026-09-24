package com.confused.anikuta.feature.extensionssettings.testing

/**
 * The SMART SEARCH phrase set (round 83, D-578).
 *
 * WHY THIS EXISTS: the round-82 Search test ran ONE user-typed query ("test")
 * and a working extension was flagged broken when that query legitimately
 * returned nothing. The user's spec: the test should try "a total of three or
 * four well-known phrases, ranging from various categories, like anime,
 * movies, series". These are globally-known titles that essentially every
 * cataloguing site (anime sites, movie sites, series sites) can match.
 *
 * HOW IT IS USED (SearchTest): the user's custom query runs FIRST (when the
 * field is non-empty), then the well-known phrases in order — the first
 * attempt that returns ≥1 result wins and the rest are skipped. One phrase
 * failing (a network error, an empty page) never fails the test on its own;
 * only ALL attempts failing does. See SearchTest for the exact ladder.
 *
 * MODULARITY: this file is ONLY data. Swapping phrases, localizing them or
 * adding categories never touches a test file — and adding a category is one
 * [SearchPhrase] entry (the category label is free text shown in the result).
 */
data class SearchPhrase(
    /** The literal search text sent to the source. */
    val text: String,
    /** The human category shown in the result message (Anime / Movie / Series). */
    val category: String,
)

object TestingSearchPhrases {

    /**
     * The well-known fallback phrases, ordered anime-first (this is an anime
     * app — the most relevant hit should come first), then live-action series,
     * then movies.
     *
     * ROUND 85 (the user's spec): the canonical test anime must be a
     * ~12–24-episode show — One Piece (1000+ episodes) made the EPISODE_LIST
     * and VIDEO_RESOLVE stages a marathon on every healthy source. The anime
     * phrases now lead with globally-indexed SHORT shows, including one
     * CHINESE donghua ("Link Click") so the ladder spans a wider catalog
     * spread. One phrase failing (a network error, an empty page) never fails
     * the test on its own; only ALL attempts failing does.
     */
    val wellKnown: List<SearchPhrase> = listOf(
        SearchPhrase("Jujutsu Kaisen", "Anime"),
        SearchPhrase("Link Click", "Donghua"),
        SearchPhrase("Breaking Bad", "Series"),
        SearchPhrase("Interstellar", "Movie"),
    )
}
