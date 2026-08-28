package com.confused.anikuta.core.updates

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.database.AnikutaDatabase
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * SQLDelight wrapper for the episode_update + anime_update_state tables.
 *
 * Phase UP (PLAN §1.3, §1.4). Provides typed access to the update feed +
 * the per-anime smart-update metadata.
 *
 * CORE_RULES §20: logged with tag "Anikuta:Core:Updates".
 */
class UpdateStore(
    private val database: AnikutaDatabase,
) {
    companion object {
        private const val TAG = "Anikuta:Core:Updates:Store"
    }

    // ── episode_update ──

    /** Insert/replace an episode update (CF5: INSERT OR REPLACE — last writer wins). */
    fun upsertEpisodeUpdate(
        mainId: String,
        episodeKey: String,
        episodeNumber: Double,
        episodeTitle: String?,
        sourceId: Long?,
        audioVariant: String,
        discoveredAt: Long,
        acknowledged: Boolean,
        acknowledgedAt: Long?,
        batchType: String = "new",
        episodeCount: Long? = null,
        newExpiresAt: Long? = null,
    ) {
        database.episodeUpdateQueries.upsertEpisodeUpdate(
            main_id = mainId,
            episode_key = episodeKey,
            episode_number = episodeNumber,
            episode_title = episodeTitle,
            source_id = sourceId,
            audio_variant = audioVariant,
            discovered_at = discoveredAt,
            acknowledged = if (acknowledged) 1L else 0L,
            acknowledged_at = acknowledgedAt,
            batch_type = batchType,
            episode_count = episodeCount,
            new_expires_at = newExpiresAt,
        )
    }

    /** Get unacknowledged updates (the "New" feed). D-193 Phase 2: filters by new_expires_at. */
    fun getUnacknowledgedUpdates(limit: Long = 100): List<EpisodeUpdate> {
        val now = System.currentTimeMillis()
        return database.episodeUpdateQueries.getUnacknowledgedUpdates(now, limit).executeAsList().map { it.toEpisodeUpdate() }
    }

    /** Get all updates (New + Earlier). */
    fun getAllUpdates(limit: Long = 100): List<EpisodeUpdate> =
        database.episodeUpdateQueries.getAllUpdates(limit).executeAsList().map { it.toEpisodeUpdate() }

    /** Acknowledge all updates for an anime (user opened the details page). */
    fun acknowledgeUpdatesByMainId(mainId: String) {
        val now = System.currentTimeMillis()
        database.episodeUpdateQueries.acknowledgeUpdatesByMainId(now, mainId)
        Logger.d(TAG) { "acknowledgeUpdatesByMainId: mainId=$mainId" }
    }

    /** D-249: Clear ALL updates (the "Clear" button — removes every row). */
    fun deleteAllUpdates() {
        database.episodeUpdateQueries.deleteAllUpdates()
        Logger.i(TAG) { "deleteAllUpdates: all episode_update rows cleared" }
    }

    /** Retention cleanup: delete acknowledged updates older than the cutoff (M9 — 7-day). */
    fun deleteOldAcknowledged(cutoff: Long) {
        database.episodeUpdateQueries.deleteOldAcknowledged(cutoff)
    }

    /** Count unacknowledged "new" updates (for a badge). D-193 Phase 2: filters by new_expires_at. */
    fun countUnacknowledged(): Long {
        val now = System.currentTimeMillis()
        return database.episodeUpdateQueries.countUnacknowledged(now).executeAsOne()
    }

    // ── Reactive (Phase UP — for the UpdatesViewModel) ──

    /** Observe all updates (New + Earlier), reactive. */
    fun observeAllUpdates(limit: Long = 100): Flow<List<EpisodeUpdate>> =
        database.episodeUpdateQueries.getAllUpdates(limit)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list -> list.map { it.toEpisodeUpdate() } }

    // ── anime_update_state ──

    /** Get the update state for an anime (null if not in the library). */
    fun getAnimeUpdateState(mainId: String): AnimeUpdateState? =
        database.animeUpdateStateQueries.getAnimeUpdateState(mainId).executeAsOneOrNull()?.toAnimeUpdateState()

    /** Get all anime due for a check (next_check_at <= now, enabled, releasing). */
    fun getDueAnime(now: Long): List<AnimeUpdateState> =
        database.animeUpdateStateQueries.getDueAnime(now).executeAsList().map { it.toAnimeUpdateState() }

    /** D-193 Phase 2: Get FINISHED anime due for a dub check. */
    fun getDueDubAnime(now: Long): List<AnimeUpdateState> =
        database.animeUpdateStateQueries.getDueDubAnime(now).executeAsList().map { it.toAnimeUpdateState() }

    /** Upsert the full update state (for new library entries). */
    fun upsertAnimeUpdateState(state: AnimeUpdateState) {
        database.animeUpdateStateQueries.upsertAnimeUpdateState(
            main_id = state.mainId,
            status = state.status,
            last_checked_at = state.lastCheckedAt,
            next_check_at = state.nextCheckAt,
            last_known_episode_count = state.lastKnownEpisodeCount,
            next_airing_episode = state.nextAiringEpisode,
            next_airing_at = state.nextAiringAt,
            auto_update_enabled = if (state.autoUpdateEnabled) 1L else 0L,
            consecutive_failures = state.consecutiveFailures.toLong(),
            backoff_step = state.backoffStep.toLong(),
            last_known_dub_count = state.lastKnownDubCount,
            last_checked_dub_at = state.lastCheckedDubAt,
            total_episodes = state.totalEpisodes,
            learned_offset_ms = state.learnedOffsetMs,
        )
    }

    /** Update the check metadata (after a check completes). D-193 Phase 2: added dub fields. */
    fun updateCheckResult(
        mainId: String,
        lastCheckedAt: Long,
        nextCheckAt: Long,
        lastKnownEpisodeCount: Long,
        consecutiveFailures: Int,
        backoffStep: Int,
        lastKnownDubCount: Long? = null,
        lastCheckedDubAt: Long? = null,
    ) {
        database.animeUpdateStateQueries.updateCheckResult(
            last_checked_at = lastCheckedAt,
            next_check_at = nextCheckAt,
            last_known_episode_count = lastKnownEpisodeCount,
            consecutive_failures = consecutiveFailures.toLong(),
            backoff_step = backoffStep.toLong(),
            last_known_dub_count = lastKnownDubCount,
            last_checked_dub_at = lastCheckedDubAt,
            main_id = mainId,
        )
    }

    /** Update the airing data (from Schedule engine — S4). */
    fun updateAiringData(
        mainId: String,
        nextAiringEpisode: Long?,
        nextAiringAt: Long?,
        status: String?,
    ) {
        database.animeUpdateStateQueries.updateAiringData(
            next_airing_episode = nextAiringEpisode,
            next_airing_at = nextAiringAt,
            status = status,
            main_id = mainId,
        )
    }

    /** D-193 Phase 2: Update total_episodes (from AniList episodes field). */
    fun updateTotalEpisodes(mainId: String, totalEpisodes: Long?) {
        database.animeUpdateStateQueries.updateTotalEpisodes(totalEpisodes, mainId)
    }

    /** D-193 v2: Update the learned offset (smart-release averaging). */
    fun updateLearnedOffset(mainId: String, learnedOffsetMs: Long?) {
        database.animeUpdateStateQueries.updateLearnedOffset(learned_offset_ms = learnedOffsetMs, main_id = mainId)
    }

    /** Disable auto-update (M3: after 3 consecutive failures). */
    fun disableAutoUpdate(mainId: String) {
        database.animeUpdateStateQueries.disableAutoUpdate(mainId)
        Logger.w(TAG) { "disableAutoUpdate: mainId=$mainId (3 consecutive failures)" }
    }

    /** Get all anime with auto-update enabled (for Schedule engine to fetch airing data). */
    fun getAutoUpdateEnabledAnime(): List<AnimeUpdateState> =
        database.animeUpdateStateQueries.getAutoUpdateEnabledAnime().executeAsList().map { it.toAnimeUpdateState() }

    // ── Mappers ──

    private fun com.confused.anikuta.core.database.Episode_update.toEpisodeUpdate() = EpisodeUpdate(
        id = id,
        mainId = main_id,
        episodeKey = episode_key,
        episodeNumber = episode_number,
        episodeTitle = episode_title,
        sourceId = source_id,
        audioVariant = audio_variant,
        discoveredAt = discovered_at,
        acknowledged = acknowledged.toInt() == 1,
        acknowledgedAt = acknowledged_at,
        batchType = batch_type,
        episodeCount = episode_count,
        newExpiresAt = new_expires_at,
    )

    private fun com.confused.anikuta.core.database.Anime_update_state.toAnimeUpdateState() = AnimeUpdateState(
        mainId = main_id,
        status = status,
        lastCheckedAt = last_checked_at,
        nextCheckAt = next_check_at,
        lastKnownEpisodeCount = last_known_episode_count,
        nextAiringEpisode = next_airing_episode,
        nextAiringAt = next_airing_at,
        autoUpdateEnabled = auto_update_enabled.toInt() == 1,
        consecutiveFailures = consecutive_failures.toInt(),
        backoffStep = backoff_step.toInt(),
        lastKnownDubCount = last_known_dub_count,
        lastCheckedDubAt = last_checked_dub_at,
        totalEpisodes = total_episodes,
        learnedOffsetMs = learned_offset_ms,
    )
}

// ── Data classes ──

data class EpisodeUpdate(
    val id: Long,
    val mainId: String,
    val episodeKey: String,
    val episodeNumber: Double,
    val episodeTitle: String?,
    val sourceId: Long?,
    val audioVariant: String,
    val discoveredAt: Long,
    val acknowledged: Boolean,
    val acknowledgedAt: Long?,
    val batchType: String = "new",
    val episodeCount: Long? = null,
    val newExpiresAt: Long? = null,
)

data class AnimeUpdateState(
    val mainId: String,
    val status: String?,
    val lastCheckedAt: Long?,
    val nextCheckAt: Long?,
    val lastKnownEpisodeCount: Long?,
    val nextAiringEpisode: Long?,
    val nextAiringAt: Long?,
    val autoUpdateEnabled: Boolean,
    val consecutiveFailures: Int,
    val backoffStep: Int,
    // D-193 Phase 2: dub tracking + total episodes
    val lastKnownDubCount: Long? = null,
    val lastCheckedDubAt: Long? = null,
    val totalEpisodes: Long? = null,
    // D-193 v2: learned offset for smart-release averaging (ms after airingAt).
    val learnedOffsetMs: Long? = null,
)
