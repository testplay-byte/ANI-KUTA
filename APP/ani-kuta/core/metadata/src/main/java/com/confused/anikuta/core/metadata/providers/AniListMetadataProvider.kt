package com.confused.anikuta.core.metadata.providers

import com.confused.anikuta.core.anilist.api.AniListApi
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.common.ContentType
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.metadata.ContentMetadata
import com.confused.anikuta.core.metadata.EpisodeMetadata
import com.confused.anikuta.core.metadata.MetadataProvider

/**
 * Metadata provider that fetches from AniList GraphQL API.
 *
 * Thin adapter over [:core:anilist] (I8 fix — not a re-implementation).
 * Queries AniList by anime ID (extracted from the content key) or by title search.
 *
 * CORE_RULES §20: Logged with tag "Anikuta:Core:Metadata:AniList".
 */
class AniListMetadataProvider(
    private val anilistApi: AniListApi,
) : MetadataProvider {

    companion object {
        private const val TAG = "Anikuta:Core:Metadata:AniList"
    }

    override val id: String = "anilist"
    override val displayName: String = "AniList"
    override val supportedContentTypes: Set<ContentType> = setOf(ContentType.VIDEO)

    override suspend fun fetchContentMetadata(contentKey: String, title: String): ContentMetadata? {
        // Try to extract AniList ID from the content key
        // Format: "anilist:-:<id>" or "aniyomi:<source>:<external>"
        val anilistId = extractAniListId(contentKey)

        val anime = if (anilistId != null) {
            Logger.d(TAG) { "Fetching by AniList ID: $anilistId" }
            anilistApi.fetchAnimeDetails(anilistId)
        } else {
            // Can't search by title via AniList API without a search query
            // Phase 4 will add AniList search — for now, return null if no ID
            Logger.d(TAG) { "No AniList ID in key, skipping" }
            null
        }

        return anime?.let { mapToContentMetadata(contentKey, it) }
    }

    override suspend fun fetchEpisodeMetadata(
        episodeKey: String,
        contentKey: String,
        episodeNumber: Double,
    ): EpisodeMetadata? {
        // Episode-level metadata from AniList requires the media ID + episode number
        // Phase 4 will implement this (needs AniList episode query)
        // For now, return null — the extension source provides episode metadata
        return null
    }

    private fun extractAniListId(contentKey: String): Int? {
        // Format: "anilist:-:<id>"
        if (!contentKey.startsWith("anilist:")) return null
        val parts = contentKey.split(":")
        if (parts.size < 3) return null
        return parts[2].toIntOrNull()
    }

    private fun mapToContentMetadata(contentKey: String, anime: AniListAnime): ContentMetadata {
        return ContentMetadata(
            contentKey = contentKey,
            title = anime.displayName,
            description = anime.description,
            genres = anime.genres ?: emptyList(),
            status = anime.status,
            year = anime.seasonYear,
            coverUrl = anime.coverUrl,
            bannerUrl = anime.bannerImage,
            episodes = anime.episodes,
        )
    }
}
