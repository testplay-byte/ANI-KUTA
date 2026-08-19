package com.confused.anikuta.core.metadata

/**
 * The type of external ID used to look up content in metadata APIs.
 *
 * D-190 (episode metadata engine): providers declare which ID types they accept
 * via [EpisodeMetadataProvider.supportedIdTypes]. The engine picks providers
 * based on the content's [ContentId.type]. This makes the system future-proof:
 * adding a new ID type (e.g. TMDB) = add a new enum value + a provider that
 * supports it — zero engine changes.
 *
 * ## Current providers
 * - [ANILIST] — AniZip (`api.ani.zip/mappings?anilist_id=X`) + Kitsu GraphQL (`lookupMapping(externalSite: ANILIST_ANIME)`)
 * - [MAL] — Jikan (`api.jikan.moe/v4/anime/{malId}/episodes`) — has filler/recap booleans
 *
 * ## Future providers (not yet built)
 * - [TMDB] — a future `TmdbEpisodeProvider` in a new `:core:metadata-tmdb` module, activated when content has a TMDB ID but no AniList ID.
 * - [KITSU] — if we ever need to query Kitsu by Kitsu ID (currently we use AniList ID via GraphQL `lookupMapping`).
 */
enum class ContentIdType {
    ANILIST,
    MAL,
    TMDB,
    KITSU,
}

/**
 * Identifies a piece of content for metadata lookup.
 *
 * @param type The ID type (ANILIST, MAL, TMDB, KITSU).
 * @param value The primary ID value (e.g. AniList ID, MAL ID, TMDB ID).
 * @param malId Optional MAL ID — used by providers that require MAL (e.g. Jikan).
 *   For AniList content, this is populated from AniList's `idMal` field or from
 *   AniZip's `mappings.mal_id`. For non-AniList content, it may be null.
 */
data class ContentId(
    val type: ContentIdType,
    val value: Long,
    val malId: Long? = null,
) {
    companion object {
        /** Convenience constructor for AniList-keyed content (the current common case). */
        fun anilist(anilistId: Int, malId: Int? = null): ContentId =
            ContentId(ContentIdType.ANILIST, anilistId.toLong(), malId?.toLong())
    }
}

/**
 * A pluggable episode metadata source. D-190.
 *
 * Each provider fetches episode-level metadata (titles, thumbnails, air dates,
 * filler info, etc.) from one external API. The [EpisodeMetadataEngine] queries
 * all providers whose [supportedIdTypes] include the content's [ContentId.type],
 * fetches in parallel (with per-provider failure isolation), and merges the
 * results via [MetadataMerger.mergeEpisodeBatch].
 *
 * ## Adding a new provider
 * 1. Implement this interface (put it in `:core:metadata/providers/` or a new
 *    `:core:metadata-<name>` module if it needs special deps).
 * 2. Register it in `MetadataModule.kt` via Koin multi-binding
 *    (`named("episodeMetadataProviders")` list — order = priority).
 * 3. Done. The engine picks it up automatically based on `supportedIdTypes`.
 *
 * ## Failure isolation
 * Each provider's `fetchEpisodes` call is wrapped in a try/catch by the engine.
 * A failure (network error, 429, parse error) returns an empty map from that
 * provider — other providers are NOT affected.
 *
 * ## Rate limiting
 * Providers are called in parallel. If a provider has rate limits (e.g. Jikan:
 * 3 req/sec), it must handle them internally (delays between pages, backoff on
 * 429). The engine does not coordinate rate limits across providers.
 *
 * CORE_RULES §20: implementations should log with tag "Anikuta:Core:Metadata:Episodes:<ProviderName>".
 * CORE_RULES §7: backend logic — no UI.
 */
interface EpisodeMetadataProvider {

    /** Unique identifier for this provider (e.g. "anizip", "jikan", "kitsu"). */
    val id: String

    /** Display name for logging (e.g. "AniZip", "Jikan (MAL)", "Kitsu"). */
    val displayName: String

    /**
     * Which [ContentIdType]s this provider accepts. The engine queries only
     * providers whose `supportedIdTypes` includes the content's ID type.
     *
     * Example: AniZip accepts `{ANILIST}`; Jikan accepts `{MAL}`; a future TMDB
     * provider would accept `{TMDB}`.
     */
    val supportedIdTypes: Set<ContentIdType>

    /**
     * Fetch episode metadata for a content.
     *
     * @param contentId The content to fetch metadata for.
     * @param episodeCount The number of episodes (from the extension's episode
     *   list). Providers SHOULD filter out episodes with numbers outside
     *   `1..episodeCount` (AniZip returns specials/recaps with high numbers).
     * @return Map of episode number → EpisodeMetadata. May be empty if the
     *   provider has no data or fails. NEVER throws — the engine wraps calls in
     *   try/catch, but providers should handle their own errors gracefully.
     */
    suspend fun fetchEpisodes(
        contentId: ContentId,
        episodeCount: Int,
    ): Map<Int, EpisodeMetadata>
}
