package com.confused.anikuta.core.common.model

/**
 * Provides anime details data from a specific source (AniList, extension, etc.).
 *
 * Modular provider pattern — each source implements this interface. The
 * DetailsViewModel uses [AnimeDetailsProviderRegistry] to dispatch to the
 * correct provider based on the entry mode.
 *
 * ## Future expansion
 *
 * New providers can be added (MAL, TMDB, Kitsu) without modifying existing
 * code. The registry handles priority + fallback.
 *
 * CORE_RULES §7: Backend logic (data fetching) is separate from the UI.
 * The DetailsScreen only renders UnifiedAnime — it doesn't know which
 * provider supplied the data.
 */
interface AnimeDetailsProvider {
    /** Unique provider ID (e.g. "anilist", "extension"). */
    val id: String

    /** Display name (for logging + future UI). */
    val name: String

    /**
     * Fetch full anime details for an AniList entry.
     *
     * @param anilistId The AniList anime ID.
     * @return UnifiedAnime with AniList fields populated, or null on failure.
     */
    suspend fun fetchFromAniList(anilistId: Int): UnifiedAnime?

    /**
     * Fetch full anime details for an extension entry.
     *
     * Calls `source.getAnimeDetails(sAnime)` to enrich sparse search results
     * into full SAnime, then maps to UnifiedAnime.
     *
     * @param sourceId The extension source ID.
     * @param animeUrl The anime's URL on the source.
     * @param title Initial title (from search result).
     * @param thumbnailUrl Initial thumbnail (from search result, may be null).
     * @return UnifiedAnime with extension fields populated, or null on failure.
     */
    suspend fun fetchFromExtension(
        sourceId: Long,
        animeUrl: String,
        title: String,
        thumbnailUrl: String?,
    ): UnifiedAnime?

    /**
     * Merge metadata from this provider into an existing UnifiedAnime.
     *
     * Used when auto-linking: the extension provider creates the base
     * UnifiedAnime, then the AniList provider merges score/episodes/genres
     * on top.
     *
     * ## Priority (D-130)
     * - [DataSourcePriority.ANILIST]: AniList data wins when both exist.
     *   Used when the user explicitly picks "AniList" as the data source
     *   (manual link or data-source selector).
     * - [DataSourcePriority.EXTENSION]: Extension data wins when both exist
     *   (first-non-null-wins). Used when auto-link enriches extension data
     *   but the user hasn't expressed a preference.
     *
     * @param base The existing UnifiedAnime (from another provider).
     * @param priority Which source's data takes precedence on conflicts.
     * @return A new UnifiedAnime with merged fields, or [base] unchanged if
     *   this provider can't enrich it (e.g. no AniList ID).
     */
    suspend fun mergeInto(
        base: UnifiedAnime,
        priority: DataSourcePriority = DataSourcePriority.EXTENSION,
    ): UnifiedAnime
}

/**
 * Which data source takes priority when merging metadata from multiple providers.
 *
 * D-130: The user wants to choose whether AniList or extension data is displayed
 * when both exist. This enum controls the merge direction.
 *
 * - [ANILIST]: AniList fields overwrite extension fields (when both non-null).
 *   The user explicitly chose AniList as the preferred source.
 * - [EXTENSION]: Extension fields are kept; AniList only fills nulls.
 *   Default for auto-link (non-intrusive enrichment).
 */
enum class DataSourcePriority {
    ANILIST,
    EXTENSION,
}
