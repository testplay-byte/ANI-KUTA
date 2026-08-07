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

    /** AniList entry — carries the AniList anime ID. */
    @Serializable
    data class AniList(
        val animeId: Int,
    ) : AnimeDetailsKey

    /** Extension entry — carries the source ID, anime URL, and sparse metadata. */
    @Serializable
    data class Extension(
        val sourceId: Long,
        val animeUrl: String,
        val title: String,
        val thumbnailUrl: String? = null,
    ) : AnimeDetailsKey
}
