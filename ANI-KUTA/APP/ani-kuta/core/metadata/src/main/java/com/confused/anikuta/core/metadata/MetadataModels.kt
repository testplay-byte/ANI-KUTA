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
 * D-190 (episode metadata engine): extended with fields from AniZip + Jikan +
 * Kitsu. `isFiller`/`isRecap` are nullable (null = unknown, false = confirmed-not,
 * true = confirmed-yes) — Jikan is the only source with filler info, so if Jikan
 * fails, the field stays null (UI shows no badge) rather than incorrectly
 * showing "non-filler".
 *
 * @param episodeKey Temporary key.
 * @param number Episode number.
 * @param title Episode title (AniZip title.en > Jikan title > Kitsu canonical).
 * @param thumbnailUrl Episode thumbnail URL (AniZip image > Kitsu thumbnail).
 * @param airDate Air date (epoch millis, nullable).
 * @param description Episode synopsis (AniZip overview > Kitsu description).
 * @param isFiller Whether this is a filler episode (Jikan — null if unknown).
 * @param isRecap Whether this is a recap episode (Jikan — null if unknown).
 * @param titleJapanese Japanese title (AniZip title.ja > Jikan title_japanese).
 * @param titleRomaji Romaji title (AniZip title.x-jat > Jikan title_romanji).
 * @param runtime Episode runtime in minutes (AniZip > Kitsu).
 * @param seasonNumber Season number (AniZip > Kitsu).
 * @param episodeNumberInSeason Episode number within its season (AniZip).
 * @param score Community score 0-10 (Jikan — MAL score).
 */
data class EpisodeMetadata(
    val episodeKey: String,
    val number: Double,
    val title: String? = null,
    val thumbnailUrl: String? = null,
    val airDate: Long? = null,
    val description: String? = null,
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
