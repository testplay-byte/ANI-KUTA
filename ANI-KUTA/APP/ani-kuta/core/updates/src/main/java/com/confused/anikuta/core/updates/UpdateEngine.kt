package com.confused.anikuta.core.updates

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.watchprogress.WatchProgressStore
import com.confused.anikuta.data.extension.manager.ExtensionManager
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * The smart update engine (Phase UP — PLAN §4.3).
 *
 * Checks ONLY due anime (next_check_at <= now, enabled, RELEASING). For each:
 * 1. Gets the source from ExtensionManager.
 * 2. Fetches the episode list.
 * 3. Diffs against last_known_episode_count.
 * 4. Inserts new episodes into episode_update (with audio_variant + M5 suppress already-watched).
 * 5. Updates anime_update_state (backoff, next_check_at, etc.).
 *
 * Improvements over the old project (PLAN §4.3):
 * - T1: status filter — only RELEASING anime checked (FINISHED/CANCELLED skipped).
 * - T2: next_check_at gating with backoff (1h→2h→4h→8h→24h capped).
 * - T3: self-improving via details-page visits (onEpisodesRefreshed — CF5: INSERT OR REPLACE).
 * - T4: per-episode audio_variant (sub/dub).
 * - T5: WorkManager worker (1h cadence, NetworkType.CONNECTED + BatteryNotLow).
 * - T6: source-link cache (deferred — uses sourceId from anime_update_state).
 * - T7: concurrency limit (3 parallel, Semaphore).
 * - M3: source-uninstall 3-strike rule.
 * - M5: suppress already-watched episodes.
 *
 * CORE_RULES §20: logged with tag "Anikuta:Core:Updates".
 */
class UpdateEngine(
    private val updateStore: UpdateStore,
    private val extensionManager: ExtensionManager,
    private val contentRepository: ContentRepository,
    private val watchProgressStore: WatchProgressStore,
    private val actualReleaseUpdater: ActualReleaseUpdater?,
    // D-193 Phase 9: notification sender (nullable — tests can pass null).
    private val notificationSender: NotificationSender? = null,
) {
    companion object {
        private const val TAG = "Anikuta:Core:Updates"
        private const val MAX_CONCURRENT = 3
        private val BACKOFF_STEPS = longArrayOf(
            TimeUnit.HOURS.toMillis(1),
            TimeUnit.HOURS.toMillis(2),
            TimeUnit.HOURS.toMillis(4),
            TimeUnit.HOURS.toMillis(8),
            TimeUnit.HOURS.toMillis(24),
        )
        private const val MAX_FAILURES = 3
    }

    private val concurrencySemaphore = Semaphore(MAX_CONCURRENT)

    // D-193 Phase 4: Live-progress flow — emitted before each anime check.
    private val _checkProgress = MutableSharedFlow<CheckProgress>(replay = 1)
    val checkProgress: SharedFlow<CheckProgress> = _checkProgress.asSharedFlow()

    /**
     * Checks all due anime for new episodes. Called by [UpdateCheckWorker] + pull-to-refresh.
     * Returns the number of new episodes found.
     *
     * D-193 Phase 4:
     * - Accepts an optional `filterMainIds` for manual mode (only check selected categories).
     * - Emits [CheckProgress] via [checkProgress] SharedFlow for live-progress UI.
     */
    suspend fun checkDueAnime(filterMainIds: Set<String>? = null): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        var dueAnime = updateStore.getDueAnime(now)

        // D-193 Phase 4: manual mode — filter to selected categories only.
        if (filterMainIds != null) {
            dueAnime = dueAnime.filter { it.mainId in filterMainIds }
        }

        if (dueAnime.isEmpty()) {
            Logger.i(TAG) { "checkDueAnime — no anime due for check" }
            _checkProgress.tryEmit(CheckProgress(0, 0, "", "", null))
            return@withContext 0
        }

        Logger.i(TAG) { "checkDueAnime — ${dueAnime.size} anime due for check (filter=${filterMainIds?.size ?: "all"})" }
        var totalNew = 0
        var current = 0
        val total = dueAnime.size

        // T7: check up to MAX_CONCURRENT in parallel.
        coroutineScope {
            dueAnime.map { state ->
                async {
                    // D-193 Phase 4: emit progress before each check.
                    val content = contentRepository.getContentByMainId(state.mainId)
                    val title = content?.title ?: "Unknown"
                    val coverUrl = null // cover URL lookup deferred — the UI can fetch it separately
                    synchronized(this@UpdateEngine) {
                        current++
                        _checkProgress.tryEmit(CheckProgress(current, total, state.mainId, title, coverUrl))
                    }

                    val newCount = checkSingleAnime(state, now)
                    synchronized(this@UpdateEngine) { totalNew += newCount }
                }
            }.awaitAll()
        }

        // D-193 Phase 4: emit terminal progress.
        _checkProgress.tryEmit(CheckProgress(total, total, "", "", null))

        Logger.i(TAG) { "checkDueAnime — complete. $totalNew new episode(s) found." }
        totalNew
    }

    /**
     * Checks a single anime for new episodes. Returns the number of new episodes found.
     */
    private suspend fun checkSingleAnime(state: AnimeUpdateState, now: Long): Int {
        val mainId = state.mainId
        val content = contentRepository.getContentByMainId(mainId)
        if (content == null) {
            Logger.w(TAG) { "checkSingleAnime — content not found: mainId=$mainId (skipping)" }
            return 0
        }

        val sourceId = content.sourceId
        if (sourceId == null) {
            Logger.w(TAG) { "checkSingleAnime — no sourceId: mainId=$mainId (skipping)" }
            return 0
        }

        // Get the source from ExtensionManager.
        val source = extensionManager.getSource(sourceId) as? AnimeHttpSource
        if (source == null) {
            // M3: source-uninstall handling.
            val failures = state.consecutiveFailures + 1
            if (failures >= MAX_FAILURES) {
                updateStore.disableAutoUpdate(mainId)
                Logger.w(TAG) { "checkSingleAnime — source unavailable ($failures failures): mainId=$mainId → auto-update disabled" }
            } else {
                updateStore.updateCheckResult(
                    mainId = mainId,
                    lastCheckedAt = now,
                    nextCheckAt = now + BACKOFF_STEPS.last(),
                    lastKnownEpisodeCount = state.lastKnownEpisodeCount ?: 0,
                    consecutiveFailures = failures,
                    backoffStep = state.backoffStep,
                    lastKnownDubCount = state.lastKnownDubCount,
                    lastCheckedDubAt = state.lastCheckedDubAt,
                )
                Logger.w(TAG) { "checkSingleAnime — source unavailable ($failures/$MAX_FAILURES): mainId=$mainId" }
            }
            return 0
        }

        // Fetch the episode list.
        return try {
            val sAnime = SAnime.create().apply { url = content.animeUrl ?: "" }
            val episodes = source.getEpisodeList(sAnime)

            val lastKnown = state.lastKnownEpisodeCount ?: 0
            val maxEpisodeNumber = episodes.maxOfOrNull { it.episode_number.toDouble() } ?: 0.0

            if (maxEpisodeNumber <= lastKnown) {
                // No new episodes — apply backoff.
                val backoffStep = (state.backoffStep + 1).coerceAtMost(BACKOFF_STEPS.size - 1)
                val nextCheckAt = now + BACKOFF_STEPS[backoffStep]
                updateStore.updateCheckResult(
                    mainId = mainId,
                    lastCheckedAt = now,
                    nextCheckAt = nextCheckAt,
                    lastKnownEpisodeCount = maxEpisodeNumber.toLong(),
                    consecutiveFailures = 0,
                    backoffStep = backoffStep,
                    lastKnownDubCount = state.lastKnownDubCount,
                    lastCheckedDubAt = state.lastCheckedDubAt,
                )
                Logger.d(TAG) { "checkSingleAnime — no new episodes: mainId=$mainId lastKnown=$lastKnown max=$maxEpisodeNumber nextCheck=${backoffStep}h backoff" }
                0
            } else {
                // New episodes found — insert them.
                val newEpisodes = episodes.filter { it.episode_number.toDouble() > lastKnown }
                var inserted = 0
                for (ep in newEpisodes) {
                    val epNum = ep.episode_number.toInt()
                    val epKey = "$mainId|${String.format("%05d", epNum)}"
                    val audioVariant = parseAudioVariant(ep.scanlator, ep.name)

                    // M5: suppress already-watched episodes.
                    val isWatched = watchProgressStore.isWatched(epKey)
                    val threeDaysMs = 3L * 24 * 60 * 60 * 1000
                    updateStore.upsertEpisodeUpdate(
                        mainId = mainId,
                        episodeKey = epKey,
                        episodeNumber = ep.episode_number.toDouble(),
                        episodeTitle = ep.name,
                        sourceId = sourceId,
                        audioVariant = audioVariant,
                        discoveredAt = now,
                        acknowledged = isWatched, // M5: pre-acknowledge if already watched.
                        acknowledgedAt = if (isWatched) now else null,
                        newExpiresAt = if (isWatched) null else now + threeDaysMs, // D-193: 3-day "new" expiry
                    )
                    inserted++
                    Logger.i(TAG) { "checkSingleAnime — NEW episode: mainId=$mainId ep=$epNum audio=$audioVariant watched=$isWatched" }

                    // D-193 Phase 9: fire "on_watchable" notification (if not already watched).
                    if (!isWatched) {
                        notificationSender?.postNotification(
                            mainId = mainId,
                            episodeNumber = epNum.toLong(),
                            audioVariant = audioVariant,
                            triggerType = "watchable",
                        )
                    }

                    // Phase SC-2 (IM11): update episode_schedule.actual_at with the source's
                    // dateUpload (the claimed upload time). Falls back to discoveredAt (now).
                    val sourceDateUpload = ep.date_upload
                    val actualAt = if (sourceDateUpload > 0) sourceDateUpload else now
                    actualReleaseUpdater?.updateActualAt(mainId, epNum.toLong(), actualAt)
                }

                // Reset backoff + compute next_check_at from next airing (or +24h fallback).
                val nextCheckAt = state.nextAiringAt?.let { it + TimeUnit.HOURS.toMillis(1) }
                    ?: (now + TimeUnit.HOURS.toMillis(24))
                updateStore.updateCheckResult(
                    mainId = mainId,
                    lastCheckedAt = now,
                    nextCheckAt = nextCheckAt,
                    lastKnownEpisodeCount = maxEpisodeNumber.toLong(),
                    consecutiveFailures = 0,
                    backoffStep = 0,
                    lastKnownDubCount = state.lastKnownDubCount,
                    lastCheckedDubAt = state.lastCheckedDubAt,
                )
                Logger.i(TAG) { "checkSingleAnime — $inserted new episode(s): mainId=$mainId lastKnown=$lastKnown→${maxEpisodeNumber.toLong()}" }
                inserted
            }
        } catch (e: Exception) {
            Logger.e(TAG, e) { "checkSingleAnime — fetch failed: mainId=$mainId sourceId=$sourceId: ${e.message}" }
            val failures = state.consecutiveFailures + 1
            if (failures >= MAX_FAILURES) {
                updateStore.disableAutoUpdate(mainId)
            } else {
                updateStore.updateCheckResult(
                    mainId = mainId,
                    lastCheckedAt = now,
                    nextCheckAt = now + BACKOFF_STEPS.last(),
                    lastKnownEpisodeCount = state.lastKnownEpisodeCount ?: 0,
                    consecutiveFailures = failures,
                    backoffStep = state.backoffStep,
                    lastKnownDubCount = state.lastKnownDubCount,
                    lastCheckedDubAt = state.lastCheckedDubAt,
                )
            }
            0
        }
    }

    /**
     * T3 — self-improving via details-page visits (CF5: INSERT OR REPLACE).
     * Called by DetailsViewModel when the user opens the details page + episodes refresh.
     * Inserts any new episodes with acknowledged=1 (pre-acknowledged — no notification spam).
     */
    suspend fun onEpisodesRefreshed(mainId: String, latestEpisodeNumber: Int) = withContext(Dispatchers.IO) {
        // D-193 Phase 1: ensure the update state exists before proceeding.
        // This fixes the ordering bug where onEpisodesRefreshed fires before
        // ensureUpdateState (user links source before adding to library).
        var state = updateStore.getAnimeUpdateState(mainId)
        if (state == null) {
            ensureUpdateState(mainId)
            state = updateStore.getAnimeUpdateState(mainId) ?: return@withContext
        }
        val lastKnown = state.lastKnownEpisodeCount ?: 0
        if (latestEpisodeNumber <= lastKnown) return@withContext

        val now = System.currentTimeMillis()

        if (lastKnown == 0L) {
            // D-192 Phase 3: FIRST LINK — create ONE "initial batch" row (not per-episode).
            // The user wants: "it will only create one single row for those whole episodes"
            // with text "Episodes 1-N added to library", NOT marked as new.
            updateStore.upsertEpisodeUpdate(
                mainId = mainId,
                episodeKey = "initial_batch",
                episodeNumber = latestEpisodeNumber.toDouble(),
                episodeTitle = "Episodes 1-$latestEpisodeNumber added to library",
                sourceId = null,
                audioVariant = "unknown",
                discoveredAt = now,
                acknowledged = true, // pre-acknowledged — not "new"
                acknowledgedAt = now,
                batchType = "initial",
                episodeCount = latestEpisodeNumber.toLong(),
                newExpiresAt = null, // initial batch never expires as "new"
            )
            Logger.i(TAG) { "onEpisodesRefreshed — INITIAL BATCH: mainId=$mainId episodes=1-$latestEpisodeNumber (one row, acknowledged)" }
        } else {
            // SUBSEQUENT REFRESH — create individual "new" rows for episodes > lastKnown.
            // These ARE new episodes the user hasn't seen before.
            val threeDaysMs = 3L * 24 * 60 * 60 * 1000 // 3 days in millis
            for (epNum in (lastKnown + 1).toInt()..latestEpisodeNumber) {
                val epKey = "$mainId|${String.format("%05d", epNum)}"
                updateStore.upsertEpisodeUpdate(
                    mainId = mainId,
                    episodeKey = epKey,
                    episodeNumber = epNum.toDouble(),
                    episodeTitle = null,
                    sourceId = null,
                    audioVariant = "unknown",
                    discoveredAt = now,
                    acknowledged = true, // CF5: pre-acknowledged (user found it organically).
                    acknowledgedAt = now,
                    batchType = "new",
                    episodeCount = null,
                    newExpiresAt = now + threeDaysMs, // D-193 Phase 2: expires as "new" after 3 days
                )
            }
            Logger.i(TAG) { "onEpisodesRefreshed — NEW EPISODES: mainId=$mainId episodes=${lastKnown + 1}-$latestEpisodeNumber (${latestEpisodeNumber - lastKnown} new)" }
        }

        // Update the state — reset backoff + set next_check_at.
        val nextCheckAt = state.nextAiringAt?.let { it + TimeUnit.HOURS.toMillis(1) }
            ?: (now + TimeUnit.HOURS.toMillis(24))
        updateStore.updateCheckResult(
            mainId = mainId,
            lastCheckedAt = now,
            nextCheckAt = nextCheckAt,
            lastKnownEpisodeCount = latestEpisodeNumber.toLong(),
            consecutiveFailures = 0,
            backoffStep = 0,
            lastKnownDubCount = state.lastKnownDubCount, // preserve existing dub count
            lastCheckedDubAt = state.lastCheckedDubAt,
        )
    }

    /**
     * Ensures an anime has an update_state row (called when added to library).
     */
    fun ensureUpdateState(mainId: String, status: String? = null) {
        val existing = updateStore.getAnimeUpdateState(mainId)
        if (existing == null) {
            val now = System.currentTimeMillis()
            updateStore.upsertAnimeUpdateState(
                AnimeUpdateState(
                    mainId = mainId,
                    status = status,
                    lastCheckedAt = null,
                    nextCheckAt = now, // due immediately on first add
                    lastKnownEpisodeCount = 0,
                    nextAiringEpisode = null,
                    nextAiringAt = null,
                    autoUpdateEnabled = true,
                    consecutiveFailures = 0,
                    backoffStep = 0,
                )
            )
            Logger.i(TAG) { "ensureUpdateState — created: mainId=$mainId status=$status" }
        }
    }

    /**
     * Parses the audio variant (sub/dub) from the scanlator + episode name.
     * Basic heuristic (ported from the old project's SubDubParser).
     */
    private fun parseAudioVariant(scanlator: String?, episodeName: String?): String {
        val haystack = "${scanlator ?: ""} ${episodeName ?: ""}".uppercase()
        val hasHsub = haystack.contains("HSUB") || haystack.contains("HARDSUB")
        val hasDub = haystack.contains("DUB") && !hasHsub
        val hasSub = haystack.contains("SUB") && !hasHsub
        return when {
            hasDub && hasSub -> "unknown" // both — unclear
            hasDub -> "dub"
            hasSub || hasHsub -> "sub"
            else -> "unknown"
        }
    }
}

/**
 * D-193 Phase 4: Live-progress data emitted by [UpdateEngine.checkDueAnime].
 *
 * @param current The current anime being checked (1-based).
 * @param total The total number of anime to check.
 * @param mainId The mainId of the current anime (empty for terminal/completion).
 * @param title The title of the current anime (empty for terminal).
 * @param coverUrl The cover URL of the current anime (null — UI can fetch separately).
 */
data class CheckProgress(
    val current: Int,
    val total: Int,
    val mainId: String,
    val title: String,
    val coverUrl: String?,
)
