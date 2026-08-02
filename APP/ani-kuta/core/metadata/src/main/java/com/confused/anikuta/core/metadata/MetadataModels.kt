package com.confused.anikuta.core.metadata

import com.confused.anikuta.core.common.ContentType

/**
 * Cached content-level metadata (description, genres, status, year).
 *
 * This is the app's own data class — it doesn't expose AniList or extension types.
 * Filled by [MetadataProvider] implementations and merged by [MetadataMerger].
 *
 * @param contentKey Temporary key: "<ecosystem>:<source_id|->:<external_id>"
 * @param title Canonical title.
 * @param description Synopsis.
 * @param genres List of genre strings.
 * @param status Release status (RELEASING, FINISHED, etc.).
 * @param year Release year.
 * @param coverUrl Cover image URL.
 * @param bannerUrl Banner image URL (nullable).
 * @param episodes Total episodes (nullable — may be unknown).
 * @param author Author (for manga — future).
 * @param artist Artist (for manga — future).
 */
data class ContentMetadata(
    val contentKey: String,
    val title: String,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: String? = null,
    val year: Int? = null,
    val coverUrl: String? = null,
    val bannerUrl: String? = null,
    val episodes: Int? = null,
    val author: String? = null,
    val artist: String? = null,
)

/**
 * Cached episode metadata (thumbnails, titles, air dates).
 *
 * @param episodeKey Temporary key.
 * @param number Episode number.
 * @param title Episode title.
 * @param thumbnailUrl Episode thumbnail URL.
 * @param airDate Air date (epoch millis, nullable).
 * @param description Episode synopsis.
 */
data class EpisodeMetadata(
    val episodeKey: String,
    val number: Double,
    val title: String? = null,
    val thumbnailUrl: String? = null,
    val airDate: Long? = null,
    val description: String? = null,
)
