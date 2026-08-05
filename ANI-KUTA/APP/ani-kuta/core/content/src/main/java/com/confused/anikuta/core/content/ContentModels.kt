package com.confused.anikuta.core.content

/**
 * The central content record — one row per anime.
 *
 * Stored in the `content` table. Keyed by [mainId] (stable UUID).
 * The [contentId] is a structured string that changes when sources switch.
 *
 * CORE_RULES §7: Pure data — no logic.
 */
data class ContentRecord(
    val mainId: String,
    val contentId: String,
    val title: String,
    val contentType: String = "anime",
    val contentFormat: String = "video",
    val description: String? = null,
    val dataSourceId: Long? = null,
    val systemId: Long? = null,
    val extensionRepoId: Long? = null,
    val extensionId: Long? = null,
    val sourceId: Long? = null,
    val animeUrl: String? = null,
    val displaySource: String = "extension",
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * AniList-specific metadata for a content. One row per content (if linked).
 */
data class AniListDetail(
    val mainId: String,
    val anilistId: Int,
    val idMal: Int? = null,
    val score: Int? = null,
    val episodes: Int? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val status: String? = null,
    val genres: String? = null,
    val synopsis: String? = null,
    val coverUrl: String? = null,
    val bannerUrl: String? = null,
    val updatedAt: Long,
)

/**
 * Extension-specific metadata for a content. One row per content (if linked).
 */
data class ExtensionDetail(
    val mainId: String,
    val extensionId: Long,
    val sourceId: Long,
    val animeUrl: String,
    val description: String? = null,
    val genres: String? = null,
    val status: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val thumbnailUrl: String? = null,
    val updatedAt: Long,
)

/**
 * Lookup: a data source (AniList, TMDB, Kitsu, MAL).
 */
data class DataSource(
    val id: Long,
    val name: String,
    val displayName: String,
    val type: String,
)

/**
 * Lookup: an extension system (Aniyomi, CloudStream, Sora, MangaYomi).
 */
data class SystemInfo(
    val id: Long,
    val name: String,
    val displayName: String,
    val packagePrefix: String?,
)

/**
 * Lookup: an extension repository URL.
 */
data class ExtensionRepo(
    val id: Long,
    val systemId: Long,
    val url: String,
    val displayName: String?,
)

/**
 * Lookup: an installed extension.
 */
data class ExtensionInfo(
    val id: Long,
    val systemId: Long,
    val repoId: Long?,
    val pkgName: String,
    val name: String,
    val sourceId: Long,
    val versionName: String?,
    val isNsfw: Boolean,
)

/**
 * A library category (e.g. "Default", "Watching").
 */
data class LibraryCategory(
    val id: Long,
    val name: String,
    val displayOrder: Int,
    val isPermanent: Boolean,
    val createdAt: Long,
)

/**
 * A library item — links content to a category.
 */
data class LibraryItem(
    val id: Long,
    val mainId: String,
    val categoryId: Long,
    val displayOrder: Int,
    val addedAt: Long,
)
