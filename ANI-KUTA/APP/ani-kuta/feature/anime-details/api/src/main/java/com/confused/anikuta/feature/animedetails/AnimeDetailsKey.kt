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
     *   (`cover:<screen>[:<section>]:<url>` — see the canonical builders in
     *   `SharedTransitionLocals.kt`, D-328). When present, the details cover
     *   morphs from the tapped card's position (experimental cover
     *   transition).
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
     * @param year Task 47: the search-time release year, when the search
     *   response carried one — seeded onto the stub SAnime so the details
     *   page can render Year even when the provider's load() omits it
     *   (common in CloudStream plugins). Additive default keeps previously
     *   saved backstacks deserializable.
     */
    @Serializable
    data class Extension(
        val sourceId: Long,
        val animeUrl: String,
        val title: String,
        val thumbnailUrl: String? = null,
        val autoPlayEpisode: Int? = null,
        val year: Int? = null,
        override val transitionKey: String? = null,
    ) : AnimeDetailsKey
}

/**
 * D-439 (round 39 — the library-crash root fix): diagnostic description of a
 * details key whose runtime class failed to match ANY sealed variant — the
 * `NoWhenBranchMatchedException` crash path reported from the v1.1.1 device
 * round (DetailsScreen.kt:367).
 *
 * The sealed hierarchy declares exactly [AniList] + [Extension], and the app's
 * own navigation code only ever constructs those two — so a runtime object
 * that matches NEITHER `is` check can only exist through a class-identity
 * anomaly (the same class name resolved through two different classloaders —
 * e.g. a stale plugin/extension dex carrying host-class copies, or a hybrid
 * install's leftover runtime state). This helper captures the exact runtime
 * identity of such an object — class name, classloader class + identity hash,
 * and whether the object even still passes `is AnimeDetailsKey` — so a single
 * log line pins the mechanism down if the anomaly ever recurs.
 *
 * Pure JVM (no Android/logging deps — the api module has none): call sites
 * pass the string to their own Logger.
 */
fun describeDetailsKeyAnomaly(key: Any?): String {
    if (key == null) return "detailsKey is null (expected a non-null AnimeDetailsKey)"
    val cls = key.javaClass
    val loader = cls.classLoader
    return buildString {
        append("unknown AnimeDetailsKey variant — runtime-class=")
        append(cls.name)
        append(", class-loader=")
        append(loader?.javaClass?.name ?: "null")
        append("@")
        append(System.identityHashCode(loader))
        append(", implements-AnimeDetailsKey=")
        append(key is AnimeDetailsKey)
        append(", object-identity=")
        append(System.identityHashCode(key))
    }
}
