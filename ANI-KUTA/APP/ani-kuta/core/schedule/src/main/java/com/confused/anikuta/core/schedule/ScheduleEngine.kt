package com.confused.anikuta.core.schedule

import com.confused.anikuta.core.anilist.api.AniListApi
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.updates.ScheduleNotificationWorker
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
    private val notificationManager: com.confused.anikuta.core.notifications.NotificationManager?,
    // D-193 v2: needed to schedule precise on_schedule OneTimeWorkers at airing time.
    private val appContext: android.content.Context,
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
        var enabledAnime = updateStore.getAutoUpdateEnabledAnime()

        // FIX: if anime_update_state is empty, populate it from the library.
        // This happens when the user hasn't explicitly enabled auto-update for any anime
        // (the ensureUpdateState was never called). We scan ALL library content that has
        // an AniList ID + create update states for them.
        if (enabledAnime.isEmpty()) {
            Logger.i(TAG) { "fetchSchedule — no auto-update-enabled anime. Scanning library for AniList-linked content..." }
            val libraryMainIds = contentRepository.getLibraryMainIds()
            var created = 0
            for (mainId in libraryMainIds) {
                val anilistDetail = contentRepository.getAniListDetail(mainId)
                if (anilistDetail != null) {
                    updateStore.upsertAnimeUpdateState(
                        com.confused.anikuta.core.updates.AnimeUpdateState(
                            mainId = mainId,
                            status = null, // will be filled by the airing fetch
                            lastCheckedAt = null,
                            nextCheckAt = System.currentTimeMillis(), // due immediately
                            lastKnownEpisodeCount = 0,
                            nextAiringEpisode = null,
                            nextAiringAt = null,
                            autoUpdateEnabled = true,
                            consecutiveFailures = 0,
                            backoffStep = 0,
                        )
                    )
                    created++
                }
            }
            if (created > 0) {
                Logger.i(TAG) { "fetchSchedule — created $created anime_update_state rows from library" }
                enabledAnime = updateStore.getAutoUpdateEnabledAnime()
            }
        }

        if (enabledAnime.isEmpty()) {
            Logger.i(TAG) { "fetchSchedule — no AniList-linked library anime found" }
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
                        val airingAtMs = node.airingAt.times(1000)
                        scheduleStore.upsertScheduleEntry(
                            mainId = mainId,
                            anilistId = media.id.toLong(),
                            episodeNumber = node.episode.toLong(),
                            scheduledAt = airingAtMs,
                            actualAt = null,
                            audioVariant = "unknown",
                            source = "anilist",
                            fetchedAt = now,
                        )
                        totalEntries++

                        // D-193 v2 cleanup: the "immediate" trigger was removed from the UI
                        // (NotificationsSettingsViewModel). Stop firing it here — it was being
                        // suppressed by default-OFF anyway, so this is dead code removal.
                        // The "on_schedule" trigger below is the user-facing "airing time reached"
                        // reminder; "on_watchable" fires separately when the engine confirms the
                        // episode exists on the source.

                        // D-193 Phase 7: fire "on_schedule" notification when the airing time
                        // is reached (within the last hour — this catches episodes that just aired).
                        // This is a REMINDER ("should be available now") — distinct from "on_watchable"
                        // (which fires when the episode is confirmed on the extension).
                        val oneHourAgo = now - (60 * 60 * 1000L)
                        if (airingAtMs <= now && airingAtMs > oneHourAgo && notificationManager != null) {
                            notificationManager.postNotification(
                                mainId = mainId,
                                episodeNumber = node.episode.toLong(),
                                audioVariant = "unknown",
                                triggerType = "schedule",
                            )
                        }

                        // D-193 v2: for FUTURE airings, schedule a precise on_schedule
                        // notification at the exact airing time via a OneTimeWorker. This
                        // replaces the old "opportunistic during refresh" approach with a
                        // true timer. The REPLACE policy means schedule changes reschedule it.
                        if (airingAtMs > now) {
                            try {
                                ScheduleNotificationWorker.schedule(
                                    context = appContext,
                                    mainId = mainId,
                                    episodeNumber = node.episode.toLong(),
                                    airingAt = airingAtMs,
                                )
                            } catch (e: Exception) {
                                Logger.w(TAG) { "Failed to schedule on_schedule notification: ${e.message}" }
                            }
                        }
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
