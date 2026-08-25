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
 *
 * D-242-fix14: Added per-audio-type episode counts for advanced RELEASED badges:
 *  - [subEpisodeCount]: how many cached episodes have SUB audio.
 *  - [dubEpisodeCount]: how many cached episodes have DUB audio.
 *  These enable the "Show sub / dub / both released episodes" sub-options in
 *  the Customize sheet, and the per-type unwatched count when "only unwatched"
 *  is toggled on.
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
    val lastWatchedAt: Long? = null,        // D-268: most recent last_watched_at (for LAST_WATCHED sort)
    // D-242-fix14: per-audio-type episode counts (for advanced RELEASED badges).
    val subEpisodeCount: Int? = null,       // # of cached episodes with SUB audio
    val dubEpisodeCount: Int? = null,       // # of cached episodes with DUB audio
) {
    /** Whether this entry can be opened via AniList (has a valid anilistId). */
    val hasAniListId: Boolean get() = anilistId != null && anilistId > 0

    /** Whether this entry can be opened via an extension source. */
    val hasExtensionSource: Boolean get() = sourceId != null && animeUrl != null

    /** Unwatched count = released - watched (null if either is null). */
    val unwatchedCount: Int? get() = releasedEpisodes?.let { r -> watchedCount?.let { w -> (r - w).coerceAtLeast(0) } }

    /**
     * D-242-fix14: Best-effort unwatched count for SUB episodes.
     *
     * = subEpisodeCount - watchedCount (clamped to 0).
     *
     * This is an approximation — we don't track which audio type the user
     * watched. If the user watched N episodes (presumably subs), this assumes
     * those N were subtitled, so subUnwatched = max(0, subCount - N).
     */
    val subUnwatchedCount: Int? get() =
        subEpisodeCount?.let { s -> watchedCount?.let { w -> (s - w).coerceAtLeast(0) } }

    /**
     * D-242-fix14: Best-effort unwatched count for DUB episodes.
     *
     * = dubEpisodeCount - watchedCount (clamped to 0).
     *
     * If the user watched N episodes (presumably subs), all dubs are still
     * unwatched, so dubUnwatched = dubCount (unless N > dubCount, then 0).
     */
    val dubUnwatchedCount: Int? get() =
        dubEpisodeCount?.let { d -> watchedCount?.let { w -> (d - w).coerceAtLeast(0) } }

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
