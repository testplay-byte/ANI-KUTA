package com.confused.anikuta.core.seasons

/**
 * D-312: A season tag parsed from ONE episode name, plus the per-episode
 * assignment produced by a whole-list [SeasonDetector.analyze] run.
 *
 * ## SeasonTag (single-name parse result)
 *
 * @param season The season number (1-based, as written by the extension).
 * @param episodeInSeason The episode number WITHIN the season, when the name
 *        carries one (the `12` in "Season 5 - Episode 12").
 * @param title The clean episode title after the tag — null when the name is
 *        just "( Season N - Episode M )" with no title.
 * @param patternId Which [SeasonPattern] matched (e.g. `"season-episode"`) —
 *        null only on hand-constructed tags. Carried for diagnostics: the
 *        episode-list dumper logs it so format coverage can be verified from
 *        device logs.
 */
data class SeasonTag(
    val season: Int,
    val episodeInSeason: Int?,
    val title: String?,
    val patternId: String? = null,
)

/**
 * D-312: The per-episode outcome of a whole-list [SeasonDetector.analyze] run.
 * Parallel to the input list (same index).
 *
 * @param season The assigned season number, or null when unassigned (no name
 *        tag + no provider hint).
 * @param episodeInSeason The episode's number within its season, when known.
 * @param title The clean title (name-tagged episodes only; null otherwise —
 *        untagged names keep their full raw name for normal title parsing).
 * @param patternId How the season was derived: a [SeasonPattern] id for
 *        name-tagged episodes, `"provider"` for metadata-hinted ones, null
 *        when unassigned.
 */
data class SeasonAssignment(
    val season: Int?,
    val episodeInSeason: Int?,
    val title: String?,
    val patternId: String?,
)

/**
 * D-312: Provider-side season metadata for ONE episode (AniZip/Kitsu/Jikan
 * enrichment carries `seasonNumber` / `episodeNumberInSeason`).
 *
 * Passed into [SeasonDetector.analyze] as a parallel (nullable) list — the
 * fusion rules are:
 *  1. An explicit name tag ALWAYS wins (the extension says so itself).
 *  2. Otherwise a hint with `seasonNumber > 0` assigns the season.
 *  3. Otherwise the episode stays unassigned (lands in the "Other" bucket).
 */
data class ProviderSeasonHint(
    val seasonNumber: Int,
    val episodeNumberInSeason: Int? = null,
)
