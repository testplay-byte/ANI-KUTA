package com.confused.anikuta.core.common

/**
 * D-307: Detects season structure from episode title prefixes.
 *
 * ## The pattern (v1, per user spec)
 *
 * Some extensions embed season metadata at the START of every episode title:
 *
 * ```
 * ( Season 5 - Episode 12 - The Black Cat )
 * (Season 5 - Episode 12)
 * Season 5 - Episode 12 - The Black Cat
 * Season 5 - Episode 12
 * ```
 *
 * with case variants (`season`, `SEASON`), flexible separators (`-`, `–`, `—`,
 * `:`), optional parentheses, and an optional trailing title.
 *
 * ## What this module owns
 *
 * - [parseSeasonTag]: parse ONE episode name → [SeasonTag] (season number,
 *   episode-in-season number, clean title) or null when untagged.
 * - [detectSeasons]: collect the distinct season numbers across a list of
 *   episode names.
 *
 * ## Extensibility (deliberate design)
 *
 * This is the dedicated season-detection MODULE. v1 handles the
 * `"Season N - Episode M - Title"` prefix format the user specified; future
 * iterations can add more sniffers (e.g. `"S5E12"`, provider-side
 * `seasonNumber` metadata, per-extension patches) by extending the regex set
 * here — the rest of the app only ever sees [SeasonTag]s.
 *
 * Lives in `:core:common` (pure string parsing, zero dependencies — mirrors
 * [EpisodeTitleParser], which delegates season-prefixed names here).
 */
object SeasonDetector {

    /**
     * A parsed season tag from an episode name.
     *
     * @param season The season number (1-based, as written by the extension).
     * @param episodeInSeason The episode number WITHIN the season, when the
     *        name carries one (e.g. the `12` in "Season 5 - Episode 12").
     * @param title The clean episode title after the tag — null when the name
     *        is just "( Season N - Episode M )" with no title.
     */
    data class SeasonTag(
        val season: Int,
        val episodeInSeason: Int?,
        val title: String?,
    )

    /**
     * Matches the season prefix: optional `(`, "Season", number, separator,
     * "Episode"/"Ep"/"EP", number, optional second separator.
     * Case-insensitive (handles "season"/"SEASON"/"Season" + "episode"/"EP").
     * Title chars are NOT consumed here — the remainder is handled in
     * [parseSeasonTag] so titles may contain separators + parentheses
     * (e.g. "The Black Cat (2024)") without confusing the parser.
     */
    private val PREFIX_REGEX = Regex(
        """^\(?\s*season\s+(\d+)\s*[-:–—]\s*(?:episode|ep)\.?\s*(\d+)\s*(?:[-:–—]\s*)?""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Parse one episode name for a season tag.
     *
     * @return The [SeasonTag], or null when the name doesn't carry the
     *         v1 season-prefix pattern.
     */
    fun parseSeasonTag(name: String): SeasonTag? {
        if (name.isBlank()) return null
        val match = PREFIX_REGEX.find(name) ?: return null
        val season = match.groupValues[1].toIntOrNull() ?: return null
        val episodeInSeason = match.groupValues[2].toIntOrNull()

        var title = name.substring(match.range.last + 1).trim()
        // If the name opened with a paren, drop the matching trailing paren
        // (handles both "( ... )" and "( ... ) trailing junk" safely).
        val hasOpeningParen = name.trimStart().startsWith("(")
        if (hasOpeningParen && title.endsWith(")")) {
            title = title.removeSuffix(")").trim()
        }
        return SeasonTag(
            season = season,
            episodeInSeason = episodeInSeason,
            title = title.takeIf { it.isNotBlank() },
        )
    }

    /**
     * Collect the distinct season numbers present in a list of episode names,
     * sorted ascending. Empty when nothing is tagged.
     *
     * The caller decides the activation threshold (the Details screen uses
     * `size >= 2` = "the series is divided into multiple seasons").
     */
    fun detectSeasons(names: List<String>): List<Int> =
        names.mapNotNull { parseSeasonTag(it)?.season }.distinct().sorted()
}
