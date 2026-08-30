package com.confused.anikuta.core.content

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The central identity record — one row per content (anime, manga, novel).
 *
 * Stored in the `main_entry` table (renamed from `content` per D-198).
 * Keyed by [mainId] (stable UUID). The [contentId] is a structured string
 * that changes when sources switch.
 *
 * D-198: `description` column DROPPED — readers now use [ContentDetails.dataSynopsis]
 * or [ContentDetails.extDescription]. The [title] stays on main_entry for fast
 * display without a JOIN (it's the identity's display name).
 * D-198: [extensionRepoId] KEPT per user directive (will be wired up later).
 * D-198: [displaySource] values are axis-level: 'data_source' | 'extension'.
 */
data class ContentRecord(
    val mainId: String,
    val contentId: String,
    val title: String,
    val contentType: String = "anime",
    val contentFormat: String = "video",
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
 * Unified content metadata — merges anilist_detail + extension_detail + anime_metadata_cache.
 *
 * One row per content (1:1 with main_entry). Two axes in one row:
 *   - data_* fields: data-source metadata (AniList now; Kitsu/MAL/TMDB future)
 *   - ext_* fields: extension metadata (Aniyomi now; CloudStream/Sora/MangaYomi future)
 *
 * Each axis is independently switchable via [ContentRepository.updateDataSourceAxis] /
 * [ContentRepository.updateExtensionAxis]. Each axis is independently unlinkable via
 * [ContentRepository.clearDataSourceAxis] / [ContentRepository.clearExtensionAxis].
 *
 * All axis columns are NULLABLE — NULL means "that axis is not linked."
 *
 * D-198: data_episodes is nullable — NULL for manga/novels. Chapter/volume counts
 * for manga go in [extExtraJson] when manga support is added. Zero schema change.
 */
data class ContentDetails(
    val mainId: String,
    // ── Data-source (metadata) axis ──
    val dataSourceType: String? = null,
    val dataSourceRefId: String? = null,
    val dataScore: Long? = null,
    val dataEpisodes: Long? = null,
    val dataSeason: String? = null,
    val dataSeasonYear: Long? = null,
    val dataStatus: String? = null,
    val dataGenres: String? = null,
    val dataSynopsis: String? = null,
    val dataCoverUrl: String? = null,
    val dataBannerUrl: String? = null,
    val dataExtraJson: String? = null,
    val dataUpdatedAt: Long? = null,
    // ── Extension (episode source) axis ──
    val extensionType: String? = null,
    val extensionId: String? = null,
    val sourceId: Long? = null,
    val animeUrl: String? = null,
    val extDescription: String? = null,
    val extGenres: String? = null,
    val extStatus: String? = null,
    val extAuthor: String? = null,
    val extArtist: String? = null,
    val extThumbnailUrl: String? = null,
    val extExtraJson: String? = null,
    val extUpdatedAt: Long? = null,
    // ── D-223: Adaptive color ──
    /** ARGB int (0xFFRRGGBB) extracted from the cover image via Palette API. Null = not yet extracted. */
    val coverAccentArgb: Long? = null,
) {
    // ── Typed accessors for external IDs (per PLAN §4.8) ──

    /** The AniList ID if the data source is AniList, else null. */
    val anilistId: Int? get() = if (dataSourceType == "anilist") dataSourceRefId?.toIntOrNull() else null

    /** The MAL ID if the data source is MAL, else falls back to extras.idMal (for Jikan API calls when AniList is active). */
    val malId: Int? get() = if (dataSourceType == "mal") dataSourceRefId?.toIntOrNull() else dataExtras.idMal?.toInt()

    /** The Kitsu ID if the data source is Kitsu, else null. */
    val kitsuId: Int? get() = if (dataSourceType == "kitsu") dataSourceRefId?.toIntOrNull() else null

    /** The TMDB ID if the data source is TMDB, else null. */
    val tmdbId: Int? get() = if (dataSourceType == "tmdb") dataSourceRefId?.toIntOrNull() else null

    /** Parsed data-source extras (idMal, trailerUrl, ageRating, etc.). */
    val dataExtras: DataSourceExtras get() = DataSourceExtras.fromJson(dataExtraJson)

    /** Parsed extension extras (scanlatorGroup, chapterCount, etc.). */
    val extExtras: ExtensionExtras get() = ExtensionExtras.fromJson(extExtraJson)

    /** True if an AniList data source is linked. */
    val hasAnilistLink: Boolean get() = dataSourceType == "anilist"

    /** True if any data source is linked. */
    val hasDataSourceLink: Boolean get() = dataSourceType != null

    /** True if any extension is linked. */
    val hasExtensionLink: Boolean get() = extensionType != null

    /**
     * The extension ID as Long (for Aniyomi back-compat — callers that expect Long?).
     * Returns null if the extension axis is not linked or the ID can't be parsed as Long.
     */
    val extensionIdLong: Long? get() = extensionId?.toLongOrNull()
}

/**
 * Source-specific extras stored in [ContentDetails.dataExtraJson].
 * Parsed with ignoreUnknownKeys = true so adding fields doesn't break existing rows.
 */
@Serializable
data class DataSourceExtras(
    val idMal: Long? = null,
    val trailerUrl: String? = null,
    val ageRating: String? = null,
    val studio: String? = null,
    val coverUrlLarge: String? = null,
    val coverUrlSmall: String? = null,
) {
    fun toJson(): String = extrasJson.encodeToString(DataSourceExtras.serializer(), this)

    companion object {
        private val extrasJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        fun fromJson(json: String?): DataSourceExtras =
            if (json.isNullOrBlank()) DataSourceExtras()
            else runCatching { extrasJson.decodeFromString(DataSourceExtras.serializer(), json) }
                .getOrDefault(DataSourceExtras())
    }
}

/**
 * Extension-specific extras stored in [ContentDetails.extExtraJson].
 * Parsed with ignoreUnknownKeys = true so adding fields doesn't break existing rows.
 */
@Serializable
data class ExtensionExtras(
    val scanlatorGroup: String? = null,
    val chapterCount: Int? = null,
    val volumeCount: Int? = null,
    /** Task 47: the source-provided release year (persisted so cache-first
     * details reopens keep the Year row; additive JSON — old rows decode
     * without it, new rows decode on old readers via ignoreUnknownKeys). */
    val year: Int? = null,
    /** Task 47: the source-provided rating on the 0..100 scale the details
     * screen displays (SAnime.score is 0..10 — converted before persisting). */
    val score: Int? = null,
) {
    fun toJson(): String = extrasJson.encodeToString(ExtensionExtras.serializer(), this)

    companion object {
        private val extrasJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        fun fromJson(json: String?): ExtensionExtras =
            if (json.isNullOrBlank()) ExtensionExtras()
            else runCatching { extrasJson.decodeFromString(ExtensionExtras.serializer(), json) }
                .getOrDefault(ExtensionExtras())
    }
}

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

// D-192: ExtensionRepo data class removed — content_ext_repo table dropped (dead code).

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
    val displayOrder: Long,
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
    val displayOrder: Long,
    val addedAt: Long,
)

/**
 * D-285: Lightweight library_item row for the Library's batch loader — one
 * query returns every (mainId, categoryId, addedAt) triple; the caller derives
 * the category filter, per-category counts, and totals in memory.
 */
data class LibraryItemRecord(
    val mainId: String,
    val categoryId: Long,
    val addedAt: Long,
)

