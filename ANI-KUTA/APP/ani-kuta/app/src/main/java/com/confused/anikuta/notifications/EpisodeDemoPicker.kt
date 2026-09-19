package com.confused.anikuta.notifications

import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.datacache.DataCacheRepository

/**
 * D-483: picks DEMO content for the notification-poster preview and the test
 * notifications when the update feed has nothing to show yet (freshly-added
 * library anime with no detected new episodes).
 *
 * The rules from the user's round-47 spec:
 * - Pick a RANDOM content from the user's LIBRARY — a different one every
 *   call (the preview re-rolls per screen open).
 * - NEVER pick content that has no cached episodes or isn't linked — the
 *   candidate list is filtered to entries with at least one cached episode,
 *   and the "latest episode" is the highest cached episode number.
 * - When NOTHING qualifies (empty library / nothing cached), the caller
 *   shows the "no episodes available yet" state.
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
     * no library content has cached episodes.
     */
    fun pickRandom(): Demo? {
        val candidates = contentRepository.getLibraryMainIds().shuffled()
        for (mainId in candidates) {
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
        return null
    }

    /** Up to [count] DISTINCT random eligible contents (for the two test posts). */
    fun pickRandomDistinct(count: Int): List<Demo> {
        val demos = mutableListOf<Demo>()
        val candidates = contentRepository.getLibraryMainIds().shuffled()
        for (mainId in candidates) {
            if (demos.size >= count) break
            val episodes = dataCacheRepository.getEpisodeMetadata(mainId)
            if (episodes.isEmpty()) continue
            val latest = episodes.maxBy { it.episodeNumber }
            val title = contentRepository.getMainEntryByMainId(mainId)?.title ?: continue
            demos.add(
                Demo(
                    mainId = mainId,
                    title = title,
                    episodeNumber = latest.episodeNumber.toDouble(),
                    audioVariant = listOf("sub", "dub").random(),
                ),
            )
        }
        return demos
    }
}
