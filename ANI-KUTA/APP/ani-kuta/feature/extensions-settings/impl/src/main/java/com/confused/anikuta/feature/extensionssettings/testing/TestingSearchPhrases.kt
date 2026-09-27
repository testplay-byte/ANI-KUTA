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
     * ROUND 93 (D-646): the user's exact spec — ONLY these three, in THIS
     * order: "only these three will be searched, and these will be searched
     * in order. If the first one fails, then the next one will be searched.
     * But if the first one passes, then the other ones will not be tested
     * for":
     *   1. Jujutsu Kaisen — the test anime (globally indexed, short);
     *   2. Interstellar — a very common, popular, well-known movie;
     *   3. Link Click — a popular, widely available Chinese donghua.
     * The ladder semantics are unchanged (first hit wins, the rest skipped;
     * one phrase failing never fails the test on its own — only ALL of them
     * does).
     */
    val wellKnown: List<SearchPhrase> = listOf(
        SearchPhrase("Jujutsu Kaisen", "Anime"),
        SearchPhrase("Interstellar", "Movie"),
        SearchPhrase("Link Click", "Donghua"),
    )
}
