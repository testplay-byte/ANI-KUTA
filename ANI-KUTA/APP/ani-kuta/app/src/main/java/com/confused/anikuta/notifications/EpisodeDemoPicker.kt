package com.confused.anikuta.notifications

import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.datacache.DataCacheRepository

/**
 * D-483: picks DEMO content for the notification-poster preview when the
 * update feed has nothing to show yet (freshly-added library anime with no
 * detected new episodes).
 *
 * The rules from the user's round-47 spec:
 * - Pick a RANDOM content from the user's LIBRARY — re-rolled every call.
 * - NEVER pick content that has no cached episodes or isn't linked — the
 *   candidate list is filtered to entries with at least one cached episode,
 *   and the "latest episode" is the highest cached episode number.
 * - When NOTHING qualifies (empty library / nothing cached), the caller
 *   shows the "no episodes available yet" state.
 *
 * D-494: [pickRandom] takes an [excludeMainId] — the Shuffle button passes
 * the content currently on stage so every tap lands on a DIFFERENT library
 * item. The exclusion is a PREFERENCE, not a rule: if the excluded entry is
 * the only eligible one, it is re-picked (a no-op shuffle beats an empty
 * preview).
 *
 * D-495: [pickPureDemo] covers the OTHER empty case — the test
 * notifications with an empty library. The user's spec: "if there are no
 * entries in the library, then the test notification should just show a
 * random test notification." A pure demo has a blank mainId: the composer
 * finds no art and renders the styled fallback stage, and the notification
 * is clearly a test — no library content is impersonated.
 */
class EpisodeDemoPicker(
    private val contentRepository: ContentRepository,
    private val dataCacheRepository: DataCacheRepository,
) {

    data class Demo(
        val mainId: String,
        val title: String,
        val episodeNumber: Double,
        val audioVariant: String,
    )

    /**
     * Random eligible library content + its latest cached episode. Null when
     * no library content has cached episodes. [excludeMainId] is skipped on
     * the first pass (D-494) and considered again on the last-resort pass.
     */
    fun pickRandom(excludeMainId: String? = null): Demo? {
        val candidates = contentRepository.getLibraryMainIds().shuffled()
        for (pass in 0..1) {
            for (mainId in candidates) {
                if (pass == 0 && mainId == excludeMainId) continue
                val episodes = dataCacheRepository.getEpisodeMetadata(mainId)
                if (episodes.isEmpty()) continue  // not linked / no cached episodes — skip
                val latest = episodes.maxBy { it.episodeNumber }
                val title = contentRepository.getMainEntryByMainId(mainId)?.title ?: continue
                return Demo(
                    mainId = mainId,
                    title = title,
                    episodeNumber = latest.episodeNumber.toDouble(),
                    // The cache doesn't track audio per episode for every source —
                    // a random variant keeps the demo badges honest-looking.
                    audioVariant = listOf("sub", "dub").random(),
                )
            }
        }
        return null
    }

    /**
     * D-495: a random PURE-DEMO notification payload for the test
     * notifications when the library has NO entries at all — a built-in
     * title, a random episode, a random audio variant, blank mainId. The
     * composer renders it on the styled no-art stage.
     */
    fun pickPureDemo(): Demo = Demo(
        mainId = "",
        title = PURE_DEMO_TITLES.random(),
        episodeNumber = (1..24).random().toDouble(),
        audioVariant = listOf("sub", "dub").random(),
    )

    private companion object {
        /** D-495: recognizable demo titles for the empty-library test posts. */
        val PURE_DEMO_TITLES = listOf(
            "Demon Slayer",
            "Jujutsu Kaisen",
            "Spy x Family",
            "Frieren: Beyond Journey's End",
            "Chainsaw Man",
            "One Piece",
        )
    }
}
