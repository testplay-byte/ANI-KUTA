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

    /**
     * D-320: the shared-element key the SOURCE card used (null = no cover
     * transition). Declared on the interface so consumers can read it without
     * a `when` on the variant.
     */
    val transitionKey: String?

    /**
     * AniList entry — carries the AniList anime ID.
     *
     * @param autoPlayEpisode Phase 3: if non-null, auto-trigger this episode
     *   when the page loads (used by Continue Watching — navigates to Details,
     *   auto-resolves, opens the player).
     * @param coverUrl D-320: display hint for the shared-element cover
     *   transition + the details loading skeleton (the cover the SOURCE screen
     *   rendered). Never used for data fetching; defaults keep deserialization
     *   compatible with previously saved backstacks.
     * @param title D-320: display hint (the title the source screen showed).
     * @param transitionKey D-320: the shared-element key the SOURCE card used
     *   (`"cover:<section>:<url>"`). When present, the details cover morphs
     *   from the tapped card's position (experimental cover transition).
     */
    @Serializable
    data class AniList(
        val animeId: Int,
        val autoPlayEpisode: Int? = null,
        val coverUrl: String? = null,
        val title: String? = null,
        override val transitionKey: String? = null,
    ) : AnimeDetailsKey

    /**
     * Extension entry — carries the source ID, anime URL, and sparse metadata.
     *
     * @param autoPlayEpisode Phase 3: if non-null, auto-trigger this episode
     *   when the page loads.
     * @param transitionKey D-320: the shared-element key the SOURCE card used.
     */
    @Serializable
    data class Extension(
        val sourceId: Long,
        val animeUrl: String,
        val title: String,
        val thumbnailUrl: String? = null,
        val autoPlayEpisode: Int? = null,
        override val transitionKey: String? = null,
    ) : AnimeDetailsKey
}
