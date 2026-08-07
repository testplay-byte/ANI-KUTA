package com.confused.anikuta.core.datacache

/**
 * Cached anime metadata — stored locally for instant display.
 *
 * Never expires — the user manually refreshes via the refresh button
 * or pull-to-refresh.
 */
data class CachedAnimeMetadata(
    val mainId: String,
    val title: String,
    val description: String? = null,
    val coverUrl: String? = null,
    val bannerUrl: String? = null,
    val score: Int? = null,
    val episodes: Int? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val status: String? = null,
    val genres: String? = null,
    val sourceType: String = "anilist",
    val fetchedAt: Long,
)

/**
 * Cached episode metadata.
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
