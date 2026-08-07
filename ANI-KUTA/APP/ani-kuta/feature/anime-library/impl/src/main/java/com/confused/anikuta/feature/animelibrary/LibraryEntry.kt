package com.confused.anikuta.feature.animelibrary

import com.confused.anikuta.core.anilist.model.AniListAnime

/**
 * A library entry — carries both display data + navigation info.
 *
 * D-140: The old approach used `AniListAnime.id` (anilistId) as the LazyGrid key
 * + navigation parameter. This crashed when multiple extension-only entries
 * existed (all had anilistId=0 → duplicate key "0" crash). It also caused 404
 * errors when opening extension-only entries from the library (tried to fetch
 * AniList ID 0).
 *
 * The fix: use `mainId` (stable UUID) as the key. For navigation, check if
 * anilistId is available → navigate via AniList. If not → navigate via Extension.
 */
data class LibraryEntry(
    val mainId: String,
    val anilistId: Int?,      // null or 0 for extension-only
    val sourceId: Long?,      // null for AniList-only without linked source
    val animeUrl: String?,    // for extension navigation
    val title: String,
    val coverUrl: String?,
    val averageScore: Int?,
    val episodes: Int?,
    val seasonYear: Int?,
    val status: String?,
) {
    /** Whether this entry can be opened via AniList (has a valid anilistId). */
    val hasAniListId: Boolean get() = anilistId != null && anilistId > 0

    /** Whether this entry can be opened via an extension source. */
    val hasExtensionSource: Boolean get() = sourceId != null && animeUrl != null

    companion object {
        /** Create from an AniListAnime (for AniList-linked entries). */
        fun fromAniList(mainId: String, anime: AniListAnime, sourceId: Long? = null, animeUrl: String? = null): LibraryEntry {
            return LibraryEntry(
                mainId = mainId,
                anilistId = anime.id,
                sourceId = sourceId,
                animeUrl = animeUrl,
                title = anime.displayName,
                coverUrl = anime.coverUrl,
                averageScore = anime.averageScore,
                episodes = anime.episodes,
                seasonYear = anime.seasonYear,
                status = anime.status,
            )
        }

        /** Create from content record + extension detail (for extension-only entries). */
        fun fromExtension(
            mainId: String,
            title: String,
            coverUrl: String?,
            sourceId: Long?,
            animeUrl: String?,
        ): LibraryEntry {
            return LibraryEntry(
                mainId = mainId,
                anilistId = null,
                sourceId = sourceId,
                animeUrl = animeUrl,
                title = title,
                coverUrl = coverUrl,
                averageScore = null,
                episodes = null,
                seasonYear = null,
                status = null,
            )
        }
    }
}
