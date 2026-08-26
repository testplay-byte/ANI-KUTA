package com.confused.anikuta.core.datacache

/**
 * D-198: CachedAnimeMetadata data class REMOVED — the anime_metadata_cache table
 * was absorbed into content_details (data-source axis). Callers now use
 * ContentDetails directly (dataSynopsis / dataCoverUrl / dataScore / etc.).
 */

/**
 * Cached episode metadata.
 *
 * Phase DB-OPT (audio-variants fix): `sourceName` + `scanlator` preserve the
 * extension's original episode name + scanlator through the AniList-enriched
 * cache write. Audio pills (SUB/DUB/HSUB) are parsed from these fields; without
 * them, the enriched cache overwrites `title` with the AniList title (no
 * SUB/DUB markers) and `scanlator` is null → pills don't show on cache-first load.
 *
 * D-190 (episode metadata engine): 8 new fields from AniZip + Jikan + Kitsu.
 * `isFiller`/`isRecap` are nullable (null = unknown, false = confirmed-not,
 * true = confirmed-yes) — Jikan is the only source with filler info, so if
 * Jikan fails, the field stays null (UI shows no badge) rather than
 * incorrectly showing "non-filler".
 */
data class CachedEpisodeMetadata(
    val mainId: String,
    val episodeNumber: Float,
    val title: String? = null,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val airDate: Long? = null,
    val fetchedAt: Long,
    val episodeUrl: String? = null,
    val sourceName: String? = null,
    val scanlator: String? = null,
    // D-190: new fields from AniZip + Jikan + Kitsu
    val isFiller: Boolean? = null,
    val isRecap: Boolean? = null,
    val titleJapanese: String? = null,
    val titleRomaji: String? = null,
    val runtime: Int? = null,
    val seasonNumber: Int? = null,
    val episodeNumberInSeason: Int? = null,
    val score: Double? = null,
)

/**
 * Cached browse page section.
 */
data class CachedBrowseSection(
    val sectionKey: String,
    val dataJson: String,
    val fetchedAt: Long,
    val expiresAt: Long,
) {
    /** Whether this cached section has expired (6-hour auto-update). */
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAt
}

/**
 * D-285: Per-anime episode/audio aggregates for the Library's batch loader.
 *
 * Computed by [DataCacheRepository.getAllEpisodeAudioAggregates] from ONE batch
 * read of the episode table — replaces the per-entry getEpisodeMetadata loop
 * (a 653-item library used to run 653 full-row episode queries per load).
 *
 * Semantics are identical to the old per-entry enrichment:
 * - [releasedCount] = number of cached episode rows (the aired/cached count)
 * - [hasSub]/[hasDub]/[hasHsub] = aggregated across ALL cached episodes
 * - [subCount]/[dubCount] = per-audio-type episode counts (advanced badges)
 */
data class EpisodeAudioAggregates(
    val releasedCount: Int,
    val hasSub: Boolean,
    val hasDub: Boolean,
    val hasHsub: Boolean,
    val subCount: Int,
    val dubCount: Int,
)

