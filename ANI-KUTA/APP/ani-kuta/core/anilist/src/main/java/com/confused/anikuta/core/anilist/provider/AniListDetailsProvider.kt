package com.confused.anikuta.core.anilist.provider

import com.confused.anikuta.core.anilist.api.AniListApi
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.common.model.AnimeDetailsProvider
import com.confused.anikuta.core.common.model.DataSourcePriority
import com.confused.anikuta.core.common.model.EntryMode
import com.confused.anikuta.core.common.model.UnifiedAnime

class AniListDetailsProvider(
    private val anilistApi: AniListApi,
) : AnimeDetailsProvider {

    override val id = "anilist"
    override val name = "AniList"

    override suspend fun fetchFromAniList(anilistId: Int): UnifiedAnime? {
        return try {
            val anime = anilistApi.fetchAnimeDetails(anilistId)
            Logger.i("AniList:Provider") { "Fetched AniList details for id=$anilistId: ${anime.displayName}" }
            anime.toUnifiedAnime()
        } catch (e: Exception) {
            Logger.e("AniList:Provider", e) { "Failed to fetch AniList details for id=$anilistId" }
            null
        }
    }

    override suspend fun fetchFromExtension(
        sourceId: Long, animeUrl: String, title: String, thumbnailUrl: String?,
    ): UnifiedAnime? = null

    /**
     * Merge AniList metadata into [base].
     *
     * ## Priority (D-130)
     * - [DataSourcePriority.ANILIST]: AniList values overwrite [base] values
     *   (when both non-null). Used when the user explicitly picks "AniList".
     * - [DataSourcePriority.EXTENSION]: [base] values are kept; AniList only
     *   fills nulls. Default for non-intrusive auto-link enrichment.
     *
     * The `title` field ALWAYS comes from [base] (the extension's title is the
     * user's entry point — changing it would be disorienting). Same for
     * `sourceId`, `sourceName`, `animeUrl`, `entryMode` — those are identity
     * fields, not metadata.
     */
    override suspend fun mergeInto(
        base: UnifiedAnime,
        priority: DataSourcePriority,
    ): UnifiedAnime {
        val anilistId = base.anilistId ?: return base
        val anilistData = fetchFromAniList(anilistId) ?: return base

        // Helper: picks which value wins based on priority.
        // ANILIST → anilistData wins (if non-null).
        // EXTENSION → base wins (if non-null), anilistData fills nulls.
        fun <T> pick(baseVal: T?, alVal: T?): T? = when (priority) {
            DataSourcePriority.ANILIST -> alVal ?: baseVal
            DataSourcePriority.EXTENSION -> baseVal ?: alVal
        }

        return base.copy(
            // Metadata fields — merged by priority.
            description = pick(base.description, anilistData.description),
            genres = if (priority == DataSourcePriority.ANILIST && anilistData.genres.isNotEmpty()) {
                anilistData.genres
            } else if (base.genres.isNotEmpty()) {
                base.genres
            } else {
                anilistData.genres
            },
            status = pick(base.status, anilistData.status),
            episodes = pick(base.episodes, anilistData.episodes),
            averageScore = pick(base.averageScore, anilistData.averageScore),
            season = pick(base.season, anilistData.season),
            seasonYear = pick(base.seasonYear, anilistData.seasonYear),
            bannerUrl = pick(base.bannerUrl, anilistData.bannerUrl),
            idMal = pick(base.idMal, anilistData.idMal),
            coverUrl = pick(base.coverUrl, anilistData.coverUrl),
            // Record which priority was used (so the UI can show the current mode).
            dataSourcePriority = priority,
            // Identity fields — ALWAYS from base (never overwritten by AniList).
            // title, sourceId, sourceName, animeUrl, entryMode stay as-is.
        )
    }
}

fun AniListAnime.toUnifiedAnime(): UnifiedAnime = UnifiedAnime(
    title = displayName,
    coverUrl = coverUrl,
    bannerUrl = bannerImage,
    description = description,
    genres = genres ?: emptyList(),
    status = status,
    episodes = episodes,
    averageScore = averageScore,
    season = season,
    seasonYear = seasonYear,
    idMal = idMal,
    anilistId = id,
    entryMode = EntryMode.ANILIST,
)
