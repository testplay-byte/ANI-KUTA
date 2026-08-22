package com.confused.anikuta.feature.animelibrary

import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.common.AudioAvailability

/**
 * A library entry — carries both display data + navigation info.
 *
 * D-242-fix10: Added fields for badge data:
 *  - [releasedEpisodes]: actual count of episodes that have aired (from cache).
 *    Different from [episodes] which is AniList's planned total.
 *  - [audioAvailability]: aggregated SUB/DUB/HSUB availability across all episodes.
 *  - [watchedCount]: how many episodes the user has watched.
 */
data class LibraryEntry(
    val mainId: String,
    val anilistId: Int?,      // null or 0 for extension-only
    val sourceId: Long?,      // null for AniList-only without linked source
    val animeUrl: String?,    // for extension navigation
    val title: String,
    val coverUrl: String?,
    val averageScore: Int?,
    val episodes: Int?,       // AniList planned total
    val seasonYear: Int?,
    val status: String?,
    // D-242-fix10: badge data
    val releasedEpisodes: Int? = null,      // actual aired count (from cache)
    val audioAvailability: AudioAvailability? = null,  // SUB/DUB/HSUB
    val watchedCount: Int? = null,          // user's watched episode count
) {
    /** Whether this entry can be opened via AniList (has a valid anilistId). */
    val hasAniListId: Boolean get() = anilistId != null && anilistId > 0

    /** Whether this entry can be opened via an extension source. */
    val hasExtensionSource: Boolean get() = sourceId != null && animeUrl != null

    /** Unwatched count = released - watched (null if either is null). */
    val unwatchedCount: Int? get() = releasedEpisodes?.let { r -> watchedCount?.let { w -> (r - w).coerceAtLeast(0) } }

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
