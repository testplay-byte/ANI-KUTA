package com.confused.anikuta.core.seasons

/**
 * D-312: The app's dedicated season-management engine (promoted from the old
 * :core:common SeasonDetector per the user's "separate module" requirement).
 *
 * ## What this module owns
 *
 * - [parseSeasonTag]: parse ONE episode name → [SeasonTag] (or null when
 *   untagged). The format set lives in [SeasonPatterns] — extend THERE.
 * - [analyze]: whole-list analysis → [SeasonAnalysis] (assignments, season
 *   inventory, confidence, activation decision), fusing name tags with
 *   optional provider hints (AniZip/Kitsu `seasonNumber` metadata).
 *
 * ## Fusion rules (name tag wins)
 *
 *  1. An explicit name tag always wins — the extension says so itself.
 *  2. Otherwise a [ProviderSeasonHint] with `seasonNumber > 0` assigns.
 *  3. Otherwise the episode is unassigned (the UI's "Other" bucket).
 *
 * ## Consumers
 *
 * - `EpisodeTitleParser.parseTitle` (title cleanup for tagged names)
 * - `EpisodeListProcessor.groupEpisodesBySeason` (season buckets + activation)
 * - `EpisodeListDumper` (logs pattern ids so format coverage is verifiable
 *   from device logs — the user sends these back for format tuning)
 *
 * Zero dependencies by design (see module build.gradle.kts).
 */
object SeasonDetector {

    /** Minimum share of episodes that must carry a season for activation. */
    const val ACTIVATION_CONFIDENCE = 0.5f

    /**
     * Parse one episode name for a season tag. Tries every [SeasonPatterns]
     * entry in order; first match wins.
     *
     * @return The [SeasonTag], or null when the name carries no known
     *         season pattern.
     */
    fun parseSeasonTag(name: String): SeasonTag? {
        if (name.isBlank()) return null
        for (pattern in SeasonPatterns.all()) {
            val match = pattern.regex.find(name) ?: continue
            val season = match.groupValues.getOrNull(1)?.toIntOrNull() ?: continue
            if (season <= 0) continue

            val episodeInSeason = match.groups.getOrNull(2)?.value?.toIntOrNull()
            var title = name.substring(match.range.last + 1).trim()
            // Drop the matching trailing paren when the name opened with one
            // (handles "( ... )" and "( ... ) trailing junk" safely).
            val hasOpeningParen = name.trimStart().startsWith("(")
            if (hasOpeningParen && title.endsWith(")")) {
                title = title.removeSuffix(")").trim()
            }
            return SeasonTag(
                season = season,
                episodeInSeason = episodeInSeason,
                title = title.takeIf { it.isNotBlank() },
                patternId = pattern.id,
            )
        }
        return null
    }

    /**
     * Collect the distinct season numbers present in a list of episode names
     * (name tags only — provider hints are ignored here), sorted ascending.
     */
    fun detectSeasons(names: List<String>): List<Int> =
        names.mapNotNull { parseSeasonTag(it)?.season }.distinct().sorted()

    /**
     * D-312: Whole-list analysis with provider-hint fusion.
     *
     * @param names The RAW episode names, in list order.
     * @param providerHints Optional parallel list (same size/index as
     *        [names]) of nullable [ProviderSeasonHint]s — provider metadata
     *        for that episode, or null when the provider has none.
     * @return The full [SeasonAnalysis].
     */
    fun analyze(
        names: List<String>,
        providerHints: List<ProviderSeasonHint?>? = null,
    ): SeasonAnalysis {
        var nameTagged = 0
        var providerHinted = 0
        val assignments = names.mapIndexed { i, name ->
            val tag = parseSeasonTag(name)
            if (tag != null) {
                nameTagged++
                SeasonAssignment(tag.season, tag.episodeInSeason, tag.title, tag.patternId)
            } else {
                val hint = providerHints?.getOrNull(i)
                if (hint != null && hint.seasonNumber > 0) {
                    providerHinted++
                    // No name tag → keep the raw name for normal title parsing
                    // (title = null here means "not season-tagged by name").
                    SeasonAssignment(hint.seasonNumber, hint.episodeNumberInSeason, null, "provider")
                } else {
                    SeasonAssignment(null, null, null, null)
                }
            }
        }
        val seasons = assignments.mapNotNull { it.season }.distinct().sorted()
        val taggedCount = nameTagged + providerHinted
        val confidence = if (names.isEmpty()) 0f else taggedCount.toFloat() / names.size
        return SeasonAnalysis(
            assignments = assignments,
            seasons = seasons,
            taggedCount = taggedCount,
            confidence = confidence,
            isMultiSeason = seasons.size >= 2 && confidence >= ACTIVATION_CONFIDENCE,
            nameTaggedCount = nameTagged,
            providerHintedCount = providerHinted,
        )
    }
}
