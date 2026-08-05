package com.confused.anikuta.core.anilist.provider

import com.confused.anikuta.core.anilist.api.AniListApi
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.common.model.AnimeDetailsProvider
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

    override suspend fun mergeInto(base: UnifiedAnime): UnifiedAnime {
        val anilistId = base.anilistId ?: return base
        val anilistData = fetchFromAniList(anilistId) ?: return base
        return base.copy(
            description = base.description ?: anilistData.description,
            genres = if (base.genres.isNotEmpty()) base.genres else anilistData.genres,
            status = base.status ?: anilistData.status,
            episodes = base.episodes ?: anilistData.episodes,
            averageScore = base.averageScore ?: anilistData.averageScore,
            season = base.season ?: anilistData.season,
            seasonYear = base.seasonYear ?: anilistData.seasonYear,
            bannerUrl = base.bannerUrl ?: anilistData.bannerUrl,
            idMal = base.idMal ?: anilistData.idMal,
            coverUrl = base.coverUrl ?: anilistData.coverUrl,
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
