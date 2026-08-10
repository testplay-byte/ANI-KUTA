package com.confused.anikuta.feature.animedetails

import com.confused.anikuta.core.navigation.NavKey
import kotlinx.serialization.Serializable

/**
 * NavKey for the Anime Details screen.
 *
 * Sealed interface with two variants:
 * - [AniList] — user tapped an AniList entry (Browse, Library, AniList search)
 * - [Extension] — user tapped an extension search result
 *
 * Both variants render the SAME DetailsScreen — the difference is which
 * AnimeDetailsProvider is used to fetch the data.
 *
 * @Serializable with kotlinx.serialization polymorphism for Nav3.
 */
@Serializable
sealed interface AnimeDetailsKey : NavKey {

    /** AniList entry — carries the AniList anime ID.
     *  @param autoPlayEpisode Phase 3: if non-null, auto-trigger this episode when the page loads
     *    (used by Continue Watching — navigates to Details, auto-resolves, opens the player). */
    @Serializable
    data class AniList(
        val animeId: Int,
        val autoPlayEpisode: Int? = null,
    ) : AnimeDetailsKey

    /** Extension entry — carries the source ID, anime URL, and sparse metadata.
     *  @param autoPlayEpisode Phase 3: if non-null, auto-trigger this episode when the page loads. */
    @Serializable
    data class Extension(
        val sourceId: Long,
        val animeUrl: String,
        val title: String,
        val thumbnailUrl: String? = null,
        val autoPlayEpisode: Int? = null,
    ) : AnimeDetailsKey
}
