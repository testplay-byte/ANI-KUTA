package com.confused.anikuta.core.metadata

import com.confused.anikuta.core.common.Logger

/**
 * Merges metadata from multiple [MetadataProvider] sources.
 *
 * Priority (highest wins):
 * 1. LocalMetadataProvider — user overrides (custom title, thumbnail, description).
 * 2. AniListMetadataProvider — AniList GraphQL (canonical metadata).
 * 3. Extension source — the source's own metadata (lowest priority).
 *
 * For each field: the first non-null value (in priority order) wins.
 *
 * CORE_RULES §20: Logged with tag "Anikuta:Core:Metadata:Merger".
 */
class MetadataMerger {

    companion object {
        private const val TAG = "Anikuta:Core:Metadata:Merger"
    }

    /**
     * Merge content metadata from multiple sources.
     *
     * @param sources Metadata from different providers (order = priority, first = highest).
     * @return The merged metadata.
     */
    fun mergeContent(sources: List<ContentMetadata?>): ContentMetadata? {
        val nonNull = sources.filterNotNull()
        if (nonNull.isEmpty()) return null

        // Use the first non-null contentKey + title as base
        val base = nonNull.first()
        val merged = ContentMetadata(
            contentKey = base.contentKey,
            title = nonNull.firstNotNullOfOrNull { it.title } ?: base.title,
            description = nonNull.firstNotNullOfOrNull { it.description },
            genres = nonNull.firstNotNullOfOrNull { it.genres }?.takeIf { it.isNotEmpty() } ?: emptyList(),
            status = nonNull.firstNotNullOfOrNull { it.status },
            year = nonNull.firstNotNullOfOrNull { it.year },
            coverUrl = nonNull.firstNotNullOfOrNull { it.coverUrl },
            bannerUrl = nonNull.firstNotNullOfOrNull { it.bannerUrl },
            episodes = nonNull.firstNotNullOfOrNull { it.episodes },
            author = nonNull.firstNotNullOfOrNull { it.author },
            artist = nonNull.firstNotNullOfOrNull { it.artist },
        )

        Logger.v(TAG) { "Merged ${nonNull.size} sources → ${merged.title}" }
        return merged
    }

    /**
     * Merge episode metadata from multiple sources.
     */
    fun mergeEpisode(sources: List<EpisodeMetadata?>): EpisodeMetadata? {
        val nonNull = sources.filterNotNull()
        if (nonNull.isEmpty()) return null

        val base = nonNull.first()
        return EpisodeMetadata(
            episodeKey = base.episodeKey,
            number = base.number,
            title = nonNull.firstNotNullOfOrNull { it.title },
            thumbnailUrl = nonNull.firstNotNullOfOrNull { it.thumbnailUrl },
            airDate = nonNull.firstNotNullOfOrNull { it.airDate },
            description = nonNull.firstNotNullOfOrNull { it.description },
        )
    }
}
