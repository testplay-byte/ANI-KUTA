package com.confused.anikuta.feature.animedetails

import com.confused.anikuta.core.metadata.EpisodeMetadata
import com.confused.anikuta.core.watchprogress.WatchProgress
import eu.kanade.tachiyomi.animesource.model.SEpisode

// ════════════════════════════════════════════════════════════════════════════
//  D-231: EpisodeListProcessor — applies filters + sort from EpisodeListPreferences
// ════════════════════════════════════════════════════════════════════════════
//
//  Pure function (no Compose dependency) that takes the raw episode list +
//  the current preferences + the lookup maps (downloadStates, watchProgress,
//  episodeMetadata) and returns the filtered + sorted list.
//
//  Used by BOTH the Details screen + the Watch screen (shared logic).
// ════════════════════════════════════════════════════════════════════════════

/**
 * Applies the user's filter + sort preferences to the episode list.
 *
 * D-232: Takes individual preference VALUES (not the EpisodeListPreferences object)
 * so Compose can track reads + recompose when any preference changes.
 *
 * @param episodes The raw episode list (from the source).
 * @param metadata Per-episode metadata (keyed by episode number).
 * @param downloadStates Download state map (keyed by "$mainId|$episodeUrl").
 * @param watchProgress Watch progress map (keyed by "$mainId|%05d episodeNumber").
 * @param mainId The current content's mainId (for building lookup keys).
 * @param downloadedFilter "OFF" / "SHOW" / "HIDE".
 * @param watchedFilter "OFF" / "SHOW" / "HIDE".
 * @param audioFilter "BOTH" / "SUB" / "DUB".
 * @param sortMode "EPISODE_NUMBER" / "UPLOAD_DATE" / "ALPHABETICAL".
 * @param sortDescending true = descending.
 * @return The filtered + sorted episode list.
 */
fun applyEpisodeListPreferences(
    episodes: List<SEpisode>,
    metadata: Map<Int, EpisodeMetadata>,
    downloadStates: Map<String, EpisodeDownloadState>,
    watchProgress: Map<String, WatchProgress>,
    mainId: String?,
    downloadedFilter: String,
    watchedFilter: String,
    audioFilter: String,
    sortMode: String,
    sortDescending: Boolean,
): List<SEpisode> {
    // ── 1. Filter ──
    val filtered = episodes.filter { episode ->
        val epNum = episode.episode_number.toInt()

        // Downloaded filter.
        val downloadStateKey = if (mainId != null) "$mainId|${episode.url}" else null
        val downloadState = downloadStateKey?.let { downloadStates[it] }
            ?: EpisodeDownloadState.NotDownloaded
        val isDownloaded = downloadState is EpisodeDownloadState.Downloaded
        val passesDownloadedFilter = when (downloadedFilter) {
            "SHOW" -> isDownloaded
            "HIDE" -> !isDownloaded
            else -> true // "OFF"
        }

        // Watched filter.
        val watchKey = if (mainId != null) "$mainId|${String.format("%05d", epNum)}" else null
        val progress = watchKey?.let { watchProgress[it] }
        val isWatched = progress?.isWatched ?: false
        val passesWatchedFilter = when (watchedFilter) {
            "SHOW" -> isWatched
            "HIDE" -> !isWatched
            else -> true // "OFF"
        }

        // Audio filter (sub/dub) — parsed from scanlator + episode name.
        val (hasSub, hasDub) = parseAudioAvailability(episode.scanlator, episode.name)
        val passesAudioFilter = when (audioFilter) {
            "SUB" -> hasSub
            "DUB" -> hasDub
            else -> true // "BOTH"
        }

        passesDownloadedFilter && passesWatchedFilter && passesAudioFilter
    }

    // ── 2. Sort ── (sortMode + sortDescending are now params)
    val sorted = when (sortMode) {
        "UPLOAD_DATE" -> {
            val comparator = compareBy<SEpisode> { ep ->
                val epNum = ep.episode_number.toInt()
                metadata[epNum]?.airDate ?: ep.date_upload
            }
            if (sortDescending) filtered.sortedWith(comparator.reversed())
            else filtered.sortedWith(comparator)
        }
        "ALPHABETICAL" -> {
            val comparator = compareBy<SEpisode> { ep ->
                val epNum = ep.episode_number.toInt()
                metadata[epNum]?.title ?: ep.name
            }
            if (sortDescending) filtered.sortedWith(comparator.reversed())
            else filtered.sortedWith(comparator)
        }
        else -> { // "EPISODE_NUMBER" (default)
            val comparator = compareBy<SEpisode> { it.episode_number }
            if (sortDescending) filtered.sortedWith(comparator.reversed())
            else filtered.sortedWith(comparator)
        }
    }

    // D-304 review tip: extensions can return the same URL twice in one episode
    // list (the same duplicate-key crash class the search grid hit). The episode
    // rows are keyed by URL — dedupe here so no source can crash the list.
    return sorted.distinctBy { it.url }
}

// ════════════════════════════════════════════════════════════════════════════
//  Grouping — splits the episode list into chunks for long series
// ════════════════════════════════════════════════════════════════════════════

/**
 * Represents a group of episodes (for the group switcher UI).
 *
 * D-233: `lowEpisode` is always the smaller number, `highEpisode` the bigger.
 * The display label is "EP {low}-{high}".
 *
 * @param index The group index (0-based).
 * @param lowEpisode The smaller episode number in this group.
 * @param highEpisode The larger episode number in this group.
 * @param episodes The episodes in this group (in the user's chosen sort order).
 */
data class EpisodeGroup(
    val index: Int,
    val lowEpisode: Int,
    val highEpisode: Int,
    val episodes: List<SEpisode>,
)

/**
 * D-233: Splits the episode list into groups by EPISODE-NUMBER RANGE.
 *
 * For groupSize=100: Group 0 = episodes 1-100, Group 1 = 101-200, etc.
 * This produces round-number boundaries (1-100, 101-200) regardless of
 * the total episode count — matches the user's mental model.
 *
 * The episode list is NOT re-sorted here — it preserves the user's chosen
 * sort order (from applyEpisodeListPreferences). Episodes are assigned to
 * groups by their episode NUMBER, then each group's episodes are in the
 * user's sort order.
 *
 * Returns a single group (the full list) if groupSize is 0 or the episode
 * count doesn't exceed the group size.
 */
fun groupEpisodes(
    episodes: List<SEpisode>,
    groupSize: Int,
): List<EpisodeGroup> {
    if (groupSize <= 0 || episodes.size <= groupSize) {
        return listOf(EpisodeGroup(0, 0, 0, episodes))
    }

    // D-233: Group by episode-number RANGE (not by count).
    // Find the min + max episode numbers to determine the range.
    val epNumbers = episodes.map { it.episode_number.toInt() }
    val minEp = epNumbers.minOrNull() ?: 0
    val maxEp = epNumbers.maxOrNull() ?: 0

    // Create range-based groups: [1..100], [101..200], etc.
    val groups = mutableListOf<EpisodeGroup>()
    var rangeStart = ((minEp - 1) / groupSize) * groupSize + 1 // round down to group boundary
    var index = 0
    while (rangeStart <= maxEp) {
        val rangeEnd = rangeStart + groupSize - 1
        // Episodes whose number falls in [rangeStart, rangeEnd].
        val chunk = episodes.filter { ep ->
            val n = ep.episode_number.toInt()
            n in rangeStart..rangeEnd
        }
        if (chunk.isNotEmpty()) {
            groups.add(EpisodeGroup(index, rangeStart, rangeEnd, chunk))
            index++
        }
        rangeStart = rangeEnd + 1
    }
    return groups
}

// ════════════════════════════════════════════════════════════════════════════
//  D-307: Season grouping — splits the episode list by detected season tags
// ════════════════════════════════════════════════════════════════════════════

/**
 * A season bucket for the season selector UI.
 *
 * @param season The season number, or null for the "Other" bucket (episodes
 *        WITHOUT a season tag — specials/OVAs/mislabeled rows).
 * @param episodes The episodes in this season (the user's sort order from
 *        [applyEpisodeListPreferences] is preserved).
 */
data class SeasonGroup(
    val season: Int?,
    val episodes: List<SEpisode>,
)

/**
 * D-307: Splits episodes into season groups using [SeasonDetector] on each
 * episode's name.
 *
 * Returns **null** when the list has no detectable multi-season structure
 * (fewer than 2 distinct season tags) — the caller then falls back to the
 * normal number-range grouping pipeline. Season mode only activates for
 * series that are genuinely "divided into multiple seasons" (user spec).
 *
 * Untagged episodes (no "Season N" prefix) land in a trailing "Other" bucket
 * (season = null), only included when non-empty.
 */
fun groupEpisodesBySeason(episodes: List<SEpisode>): List<SeasonGroup>? {
    val tags = episodes.map { com.confused.anikuta.core.common.SeasonDetector.parseSeasonTag(it.name) }
    val seasonNumbers = com.confused.anikuta.core.common.SeasonDetector.detectSeasons(
        episodes.map { it.name },
    )
    // Activation guard (D-307 review fix): seasons take over the default
    // organization only when BOTH (a) ≥2 distinct seasons AND (b) a MAJORITY of
    // episodes carry tags — a couple of mislabeled episodes in a 1000-episode
    // series must not hijack it into season mode (number-range grouping would
    // silently disappear). The settings-sheet toggle remains the manual override.
    val taggedCount = tags.count { it != null }
    if (seasonNumbers.size < 2 || taggedCount * 2 < episodes.size) return null

    val seasonBuckets = seasonNumbers.map { season ->
        SeasonGroup(season, episodes.filterIndexed { i, _ -> tags[i]?.season == season })
    }
    val untagged = episodes.filterIndexed { i, _ -> tags[i] == null }
    return if (untagged.isNotEmpty()) seasonBuckets + SeasonGroup(null, untagged) else seasonBuckets
}

// ════════════════════════════════════════════════════════════════════════════
//  Audio availability parser (mirrors DetailsScreen.parseAudioAvailability)
// ════════════════════════════════════════════════════════════════════════════

/**
 * Returns (hasSub, hasDub) by parsing the scanlator + episode name.
 */
private fun parseAudioAvailability(scanlator: String?, episodeName: String): Pair<Boolean, Boolean> {
    val combined = ((scanlator ?: "") + " " + (episodeName ?: "")).uppercase()
    val hasSub = combined.contains("SUB") && !combined.contains("DUB")
        || combined.contains("SUBBED")
        || combined.contains("HSUB") || combined.contains("HARDSUB")
    val hasDub = combined.contains("DUB") || combined.contains("DUBBED")
    return Pair(hasSub, hasDub)
}
