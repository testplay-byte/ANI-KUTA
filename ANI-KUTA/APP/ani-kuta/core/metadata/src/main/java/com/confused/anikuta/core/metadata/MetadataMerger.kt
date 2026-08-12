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
            // D-190: new fields — first-non-null-wins (same priority order as above)
            isFiller = nonNull.firstNotNullOfOrNull { it.isFiller },
            isRecap = nonNull.firstNotNullOfOrNull { it.isRecap },
            titleJapanese = nonNull.firstNotNullOfOrNull { it.titleJapanese },
            titleRomaji = nonNull.firstNotNullOfOrNull { it.titleRomaji },
            runtime = nonNull.firstNotNullOfOrNull { it.runtime },
            seasonNumber = nonNull.firstNotNullOfOrNull { it.seasonNumber },
            episodeNumberInSeason = nonNull.firstNotNullOfOrNull { it.episodeNumberInSeason },
            score = nonNull.firstNotNullOfOrNull { it.score },
        )
    }

    /**
     * Merge BATCH episode metadata from multiple provider results. D-190.
     *
     * Each entry in [sources] is a complete episode map from one provider (keyed
     * by episode number). The [providerOrder] list specifies the merge priority
     * (first = highest priority). For each episode number present in ANY source,
     * the merged result takes each field from the highest-priority source that
     * has a non-null value for that field.
     *
     * ## Per-field strategy
     * - Most fields: first-non-null-wins by provider priority (AniZip > Jikan > Kitsu).
     * - `isFiller` / `isRecap`: OR-true across sources (if ANY source says filler,
     *   it's filler). Currently only Jikan has these, but OR-true future-proofs
     *   for TMDB/etc. Null stays null only if ALL sources are null.
     * - `score`: first-non-null (Jikan preferred — AniZip's `rating` is AniDB scale).
     *
     * @param sources List of (providerId, episodeMap) pairs.
     * @param providerOrder Provider IDs in priority order (first = highest).
     * @return Merged map of episode number → EpisodeMetadata.
     */
    fun mergeEpisodeBatch(
        sources: List<Pair<String, Map<Int, EpisodeMetadata>>>,
        providerOrder: List<String>,
    ): Map<Int, EpisodeMetadata> {
        if (sources.isEmpty()) return emptyMap()

        // Collect all episode numbers across all sources.
        val allEpisodeNumbers = sources.flatMap { it.second.keys }.toSet()
        if (allEpisodeNumbers.isEmpty()) return emptyMap()

        val merged = mutableMapOf<Int, EpisodeMetadata>()
        for (epNum in allEpisodeNumbers) {
            // Gather this episode from each provider (in priority order).
            val perProvider: List<EpisodeMetadata> = providerOrder.mapNotNull { pid ->
                sources.firstOrNull { it.first == pid }?.second?.get(epNum)
            }
            if (perProvider.isEmpty()) continue

            val base = perProvider.first()
            merged[epNum] = EpisodeMetadata(
                episodeKey = base.episodeKey,
                number = base.number,
                // Standard fields: first-non-null-wins by priority
                title = perProvider.firstNotNullOfOrNull { it.title },
                thumbnailUrl = perProvider.firstNotNullOfOrNull { it.thumbnailUrl },
                airDate = perProvider.firstNotNullOfOrNull { it.airDate },
                description = perProvider.firstNotNullOfOrNull { it.description },
                titleJapanese = perProvider.firstNotNullOfOrNull { it.titleJapanese },
                titleRomaji = perProvider.firstNotNullOfOrNull { it.titleRomaji },
                runtime = perProvider.firstNotNullOfOrNull { it.runtime },
                seasonNumber = perProvider.firstNotNullOfOrNull { it.seasonNumber },
                episodeNumberInSeason = perProvider.firstNotNullOfOrNull { it.episodeNumberInSeason },
                score = perProvider.firstNotNullOfOrNull { it.score },
                // Filler/recap: OR-true across sources (if any says filler, it's filler).
                // Null only if ALL sources are null (unknown). 0 (confirmed-not) from one
                // source + null (unknown) from others = 0 (trust the confirmed source).
                isFiller = mergeBooleanOrTrue(perProvider.map { it.isFiller }),
                isRecap = mergeBooleanOrTrue(perProvider.map { it.isRecap }),
            )
        }

        Logger.v(TAG) { "Merged ${sources.size} provider results → ${merged.size} episodes" }
        return merged
    }

    /**
     * Merge nullable booleans with OR-true semantics:
     * - If ANY source is `true` → `true`
     * - Else if ANY source is `false` → `false` (trust the confirmed-not)
     * - Else (all null) → `null` (unknown)
     */
    private fun mergeBooleanOrTrue(values: List<Boolean?>): Boolean? {
        if (values.any { it == true }) return true
        if (values.any { it == false }) return false
        return null
    }
}
