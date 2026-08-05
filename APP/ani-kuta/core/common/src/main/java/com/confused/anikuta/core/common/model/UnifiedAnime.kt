package com.confused.anikuta.core.common.model

/**
 * Source-agnostic anime model for the details page.
 *
 * Bridges AniList and extension data into a single type that the DetailsScreen
 * can render without knowing the source. Every field except [title] is nullable
 * — extension-only anime won't have score/season/episodes/etc.
 *
 * ## Identity
 * - [anilistId]: non-null for AniList entries or linked extension entries.
 * - [sourceId] + [animeUrl]: non-null for extension entries.
 * - At least one of (anilistId, sourceId) is always non-null.
 *
 * ## Future: contentId
 * The future contentId system will compute a stable identity from these fields.
 * For now, we use [temporaryContentId] for watch progress.
 */
data class UnifiedAnime(
    val title: String,
    val coverUrl: String? = null,
    val bannerUrl: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: String? = null,
    val episodes: Int? = null,
    val averageScore: Int? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val author: String? = null,
    val artist: String? = null,
    val idMal: Int? = null,
    val anilistId: Int? = null,
    val sourceId: Long? = null,
    val sourceName: String? = null,
    val animeUrl: String? = null,
    val entryMode: EntryMode = EntryMode.ANILIST,
) {
    /** Alias for [title] — for AniListAnime compatibility (displayName property). */
    val displayName: String get() = title

    val isExtensionOnly: Boolean get() = anilistId == null
    val isFromExtension: Boolean get() = entryMode == EntryMode.EXTENSION

    val temporaryContentId: String?
        get() = when {
            anilistId != null -> "al:$anilistId"
            sourceId != null && animeUrl != null -> "aniyomi:$sourceId:$animeUrl"
            else -> null
        }
}

enum class EntryMode {
    ANILIST,
    EXTENSION,
}
