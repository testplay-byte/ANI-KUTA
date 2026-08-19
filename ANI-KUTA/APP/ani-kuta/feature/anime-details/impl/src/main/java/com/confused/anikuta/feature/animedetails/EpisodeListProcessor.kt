package com.confused.anikuta.feature.animedetails

import com.confused.anikuta.core.metadata.EpisodeMetadata
import com.confused.anikuta.core.preferences.EpisodeListPreferences
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
 * @param episodes The raw episode list (from the source).
 * @param metadata Per-episode metadata (keyed by episode number).
 * @param downloadStates Download state map (keyed by "$mainId|$episodeUrl").
 * @param watchProgress Watch progress map (keyed by "$mainId|%05d episodeNumber").
 * @param mainId The current content's mainId (for building lookup keys).
 * @param prefs The user's episode list preferences.
 * @return The filtered + sorted episode list.
 */
fun applyEpisodeListPreferences(
    episodes: List<SEpisode>,
    metadata: Map<Int, EpisodeMetadata>,
    downloadStates: Map<String, EpisodeDownloadState>,
    watchProgress: Map<String, WatchProgress>,
    mainId: String?,
    prefs: EpisodeListPreferences,
): List<SEpisode> {
    // ── 1. Filter ──
    val downloadedFilter = prefs.downloadedFilter.get()
    val watchedFilter = prefs.watchedFilter.get()
    val audioFilter = prefs.audioFilter.get()

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

    // ── 2. Sort ──
    val sortMode = prefs.sortMode.get()
    val sortDescending = prefs.sortDescending.get()

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

    return sorted
}

// ════════════════════════════════════════════════════════════════════════════
//  Grouping — splits the episode list into chunks for long series
// ════════════════════════════════════════════════════════════════════════════

/**
 * Represents a group of episodes (for the group switcher UI).
 *
 * @param index The group index (0-based).
 * @param startEpisode The first episode number in this group.
 * @param endEpisode The last episode number in this group.
 * @param episodes The episodes in this group.
 */
data class EpisodeGroup(
    val index: Int,
    val startEpisode: Int,
    val endEpisode: Int,
    val episodes: List<SEpisode>,
)

/**
 * Splits the episode list into groups of [groupSize] episodes each.
 * Returns a single group (the full list) if groupSize is 0 or the episode
 * count doesn't exceed the group size.
 *
 * Episodes are sorted descending by episode number before grouping (so the
 * latest episodes are in group 0 — matches the default descending display).
 */
fun groupEpisodes(
    episodes: List<SEpisode>,
    groupSize: Int,
): List<EpisodeGroup> {
    if (groupSize <= 0 || episodes.size <= groupSize) {
        return listOf(EpisodeGroup(0, 0, 0, episodes))
    }

    // Sort descending by episode number (latest first).
    val sorted = episodes.sortedByDescending { it.episode_number }

    val groups = mutableListOf<EpisodeGroup>()
    var index = 0
    var startIndex = 0
    while (startIndex < sorted.size) {
        val endIndex = minOf(startIndex + groupSize, sorted.size)
        val chunk = sorted.subList(startIndex, endIndex)
        val startEp = chunk.firstOrNull()?.episode_number?.toInt() ?: 0
        val endEp = chunk.lastOrNull()?.episode_number?.toInt() ?: 0
        groups.add(EpisodeGroup(index, startEp, endEp, chunk))
        index++
        startIndex = endIndex
    }
    return groups
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
