package com.confused.anikuta.core.metadata

import com.confused.anikuta.core.anilist.api.AniListApi
import com.confused.anikuta.core.anilist.model.AniListStreamingEpisode
import com.confused.anikuta.core.common.Logger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Episode metadata engine — the orchestrator for batch episode metadata fetching. D-190.
 *
 * Replaces the old standalone `EpisodeMetadataFetcher` (which used Anikage.cc +
 * Jikan + AniList streaming in a non-pluggable class). This engine uses a
 * pluggable [EpisodeMetadataProvider] architecture: providers are registered via
 * Koin multi-binding, the engine queries all providers whose
 * [EpisodeMetadataProvider.supportedIdTypes] include the content's [ContentId.type],
 * fetches in parallel (with per-provider failure isolation), and merges the
 * results via [MetadataMerger.mergeEpisodeBatch].
 *
 * ## Current providers (priority order)
 * 1. **AniZip** (`api.ani.zip`) — AniList ID — primary (richest: titles, overview, thumbnails, runtime, season)
 * 2. **Jikan** (`api.jikan.moe/v4`) — MAL ID — UNIQUE: filler + recap booleans, title_japanese, score
 * 3. **Kitsu** (`kitsu.io/api/graphql`) — AniList ID — tertiary (canonical titles, descriptions, thumbnails)
 * 4. **AniList streamingEpisodes** (built-in) — AniList ID — quaternary (often empty, last resort)
 *
 * ## Future-proofing
 * The [ContentId] + [ContentIdType] design means adding a new ID type (e.g. TMDB)
 * = add a new provider module that declares `supportedIdTypes = {TMDB}`. The
 * engine auto-selects it when content has a TMDB ID. Zero engine changes.
 *
 * ## Backward-compatible public API
 * The public method `fetchEpisodeMetadata(anilistId, malId, episodeCount)` is
 * kept for the 4 existing call sites in DetailsViewModel. Internally it builds a
 * [ContentId] + delegates to [fetchWithProviders].
 *
 * ## Failure isolation
 * Each provider's `fetchEpisodes` is called inside `async { try { ... } catch { emptyMap() } }`.
 * A failure in one provider (network error, 429, parse error) does NOT cancel
 * sibling providers — they still complete + contribute their data. This is
 * critical because Jikan (filler) + AniZip (titles) are independent; one failing
 * should not lose the other's data.
 *
 * ## Note on naming
 * This class is DISTINCT from [MetadataRegistry.fetchEpisodeMetadata] (the
 * per-episode content-metadata override flow used by Local + AniList providers).
 * This engine handles BATCH episode-list metadata from external APIs.
 *
 * CORE_RULES §20: logged with tag "Anikuta:Core:Metadata:Episodes".
 * CORE_RULES §7: backend logic — no UI.
 */
class EpisodeMetadataEngine(
    private val aniListApi: AniListApi,
    private val providers: List<EpisodeMetadataProvider>,
    private val merger: MetadataMerger,
) {

    companion object {
        private const val TAG = "Anikuta:Core:Metadata:Episodes"
    }

    /**
     * Fetch episode metadata for an anime. Backward-compatible public API.
     *
     * Internally builds a [ContentId] + delegates to [fetchWithProviders].
     *
     * @param anilistId The AniList anime ID.
     * @param malId The MyAnimeList ID (from AniList's idMal field). May be null.
     * @param episodeCount The number of episodes (from the extension's episode list).
     * @return Map of episode number → EpisodeMetadata. May be empty if no source provides data.
     */
    suspend fun fetchEpisodeMetadata(
        anilistId: Int,
        malId: Int?,
        episodeCount: Int,
    ): Map<Int, EpisodeMetadata> {
        val contentId = ContentId.anilist(anilistId, malId)
        return fetchWithProviders(contentId, episodeCount, anilistId)
    }

    /**
     * Fetch + merge episode metadata from all applicable providers + AniList streaming.
     *
     * @param contentId The content to fetch metadata for.
     * @param episodeCount The episode count (for filtering specials).
     * @param anilistId The AniList ID (for the AniList streaming fallback).
     */
    private suspend fun fetchWithProviders(
        contentId: ContentId,
        episodeCount: Int,
        anilistId: Int,
    ): Map<Int, EpisodeMetadata> {
        Logger.i(TAG) { "Fetching episode metadata: contentId=$contentId, episodeCount=$episodeCount" }

        // Determine which providers are applicable for this content ID type.
        // AniZip + Kitsu accept ANILIST; Jikan accepts MAL (or ANILIST with malId).
        val applicableProviders = providers.filter { provider ->
            contentId.type in provider.supportedIdTypes ||
                (contentId.type == ContentIdType.ANILIST && contentId.malId != null && ContentIdType.MAL in provider.supportedIdTypes)
        }
        Logger.d(TAG) { "Applicable providers: ${applicableProviders.map { it.id }}" }

        // Fetch from all providers in PARALLEL with per-provider failure isolation.
        // Each async block wraps in try/catch → returns emptyMap on failure.
        // This ensures one provider's error does NOT cancel sibling providers.
        val providerResults: List<Pair<String, Map<Int, EpisodeMetadata>>> = coroutineScope {
            applicableProviders.map { provider ->
                async {
                    try {
                        provider.id to provider.fetchEpisodes(contentId, episodeCount)
                    } catch (e: Exception) {
                        Logger.w(TAG) { "Provider ${provider.id} (${provider.displayName}) failed: ${e.message}" }
                        provider.id to emptyMap()
                    }
                }
            }.awaitAll()
        }

        // Also fetch from AniList streamingEpisodes (built-in, not a pluggable provider).
        val streamingResult = try {
            val streamingEpisodes = aniListApi.fetchStreamingEpisodes(anilistId)
            Logger.d(TAG) { "AniList streamingEpisodes: ${streamingEpisodes.size} episodes" }
            "anilist-streaming" to streamingEpisodesToMetadata(streamingEpisodes, anilistId, episodeCount)
        } catch (e: Exception) {
            Logger.w(TAG) { "AniList streamingEpisodes fetch failed: ${e.message}" }
            "anilist-streaming" to emptyMap()
        }

        val allResults = providerResults + streamingResult
        // Provider order for merge priority: providers list order (AniZip > Jikan > Kitsu) + streaming last.
        val providerOrder = applicableProviders.map { it.id } + "anilist-streaming"

        val merged = merger.mergeEpisodeBatch(allResults, providerOrder)
        Logger.i(TAG) { "Episode metadata fetch complete: ${merged.size} episodes (from ${allResults.size} sources)" }
        return merged
    }

    /**
     * Convert AniList streamingEpisodes to EpisodeMetadata map (quaternary fallback).
     * AniList streamingEpisodes is indexed by position (1-based) — episode number = index + 1.
     */
    private fun streamingEpisodesToMetadata(
        episodes: List<AniListStreamingEpisode>,
        anilistId: Int,
        episodeCount: Int,
    ): Map<Int, EpisodeMetadata> {
        val results = mutableMapOf<Int, EpisodeMetadata>()
        episodes.forEachIndexed { index, ep ->
            val epNum = index + 1
            if (epNum > episodeCount) return@forEachIndexed
            results[epNum] = EpisodeMetadata(
                episodeKey = "al:$anilistId|ep:$epNum",
                number = epNum.toDouble(),
                title = ep.title?.takeIf { it.isNotBlank() },
                thumbnailUrl = ep.thumbnail?.takeIf { it.isNotBlank() },
            )
        }
        return results
    }
}
