package com.confused.anikuta.core.schedule

import com.confused.anikuta.core.anilist.api.AniListApi
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.updates.UpdateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The schedule engine (Phase SC — PLAN §5.4).
 *
 * Fetches airing schedule data from AniList + populates:
 * 1. `episode_schedule` table (the schedule entries for each anime).
 * 2. `anime_update_state.next_airing_*` + `status` (S4 — unified airing data,
 *    shared with the Updates engine so both use the same AniList data).
 *
 * CORE_RULES §20: logged with tag "Anikuta:Core:Schedule".
 */
class ScheduleEngine(
    private val aniListApi: AniListApi,
    private val contentRepository: ContentRepository,
    private val scheduleStore: ScheduleStore,
    private val updateStore: UpdateStore,
) {
    companion object {
        private const val TAG = "Anikuta:Core:Schedule"
        private const val BATCH_SIZE = 50
    }

    /**
     * Fetches airing schedule data for all auto-update-enabled anime + populates
     * the episode_schedule + anime_update_state tables.
     *
     * Called by: Schedule screen open, pull-to-refresh, the WorkManager worker (M4).
     */
    suspend fun fetchSchedule() = withContext(Dispatchers.IO) {
        val enabledAnime = updateStore.getAutoUpdateEnabledAnime()
        if (enabledAnime.isEmpty()) {
            Logger.i(TAG) { "fetchSchedule — no auto-update-enabled anime" }
            return@withContext
        }

        // Build a map of anilistId → mainId.
        val anilistToMainId = mutableMapOf<Int, String>()
        for (state in enabledAnime) {
            val anilistDetail = contentRepository.getAniListDetail(state.mainId)
            if (anilistDetail != null) {
                anilistToMainId[anilistDetail.anilistId] = state.mainId
            }
        }
        if (anilistToMainId.isEmpty()) {
            Logger.i(TAG) { "fetchSchedule — no AniList-linked anime" }
            return@withContext
        }

        Logger.i(TAG) { "fetchSchedule — ${anilistToMainId.size} anime to fetch" }
        val now = System.currentTimeMillis()
        val anilistIds = anilistToMainId.keys.toList()
        var totalEntries = 0

        // Chunk into batches of 50 (AniList API limit).
        for (batch in anilistIds.chunked(BATCH_SIZE)) {
            try {
                val results = aniListApi.fetchAiringSchedule(batch)
                for (media in results) {
                    val mainId = anilistToMainId[media.id] ?: continue

                    // S4: update anime_update_state with airing data + status.
                    val nextAiring = media.nextAiringEpisode
                    updateStore.updateAiringData(
                        mainId = mainId,
                        nextAiringEpisode = nextAiring?.episode?.toLong(),
                        nextAiringAt = nextAiring?.airingAt?.times(1000), // seconds → millis
                        status = media.status,
                    )

                    // Write episode_schedule entries from the airingSchedule nodes.
                    val nodes = media.airingSchedule?.nodes ?: emptyList()
                    for (node in nodes) {
                        scheduleStore.upsertScheduleEntry(
                            mainId = mainId,
                            anilistId = media.id.toLong(),
                            episodeNumber = node.episode.toLong(),
                            scheduledAt = node.airingAt.times(1000), // seconds → millis
                            actualAt = null, // Phase SC-2: set when UpdateEngine finds the episode
                            audioVariant = "unknown", // AniList doesn't distinguish sub/dub airing
                            source = "anilist",
                            fetchedAt = now,
                        )
                        totalEntries++
                    }
                }
                Logger.d(TAG) { "fetchSchedule — batch of ${batch.size} fetched (${results.size} results)" }
            } catch (e: Exception) {
                Logger.e(TAG, e) { "fetchSchedule — batch failed: ${e.message}" }
            }
        }

        Logger.i(TAG) { "fetchSchedule — complete. $totalEntries schedule entries written." }
    }
}
