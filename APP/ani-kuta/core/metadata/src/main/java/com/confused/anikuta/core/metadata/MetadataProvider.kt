package com.confused.anikuta.core.metadata

import com.confused.anikuta.core.common.ContentType
import kotlinx.coroutines.flow.Flow

/**
 * Provides metadata for content (anime, manga, novels).
 *
 * Multiple providers can exist — [MetadataMerger] merges their results.
 * Priority: LocalMetadataProvider (user overrides) > AniListMetadataProvider > extension source.
 *
 * Future providers (not in Phase 3):
 * - TmdbMetadataProvider — movies and series.
 * - JikanMetadataProvider — manga (MyAnimeList API).
 *
 * CORE_RULES §23: Methods return Flow for reactive updates.
 */
interface MetadataProvider {

    /** Unique identifier for this provider. */
    val id: String

    /** Display name. */
    val displayName: String

    /** Which content types this provider supports. */
    val supportedContentTypes: Set<ContentType>

    /**
     * Fetch content-level metadata.
     *
     * @param contentKey The content to fetch metadata for.
     * @param title The title (for search if needed).
     * @return The metadata, or null if not found.
     */
    suspend fun fetchContentMetadata(contentKey: String, title: String): ContentMetadata?

    /**
     * Fetch episode-level metadata.
     *
     * @param episodeKey The episode to fetch metadata for.
     * @param contentKey The parent content.
     * @param episodeNumber The episode number.
     * @return The metadata, or null if not found.
     */
    suspend fun fetchEpisodeMetadata(
        episodeKey: String,
        contentKey: String,
        episodeNumber: Double,
    ): EpisodeMetadata?
}
