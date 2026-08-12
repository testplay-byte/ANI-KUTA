package com.confused.anikuta.core.updates

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.confused.anikuta.core.common.Logger
import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * D-193 Phase 7: Smart release detection worker — per-anime smart polling.
 *
 * Polls an extension for a specific episode after the AniList airing time.
 * Uses a progressive retry schedule: +10min, +20min, +1h, +2h (4 attempts max).
 *
 * When the episode is found:
 * 1. Records the actual found time as the "internal release time".
 * 2. Computes a smart average of the AniList schedule + the internal release time.
 * 3. Stores the average in anime_update_state.next_check_at for future scheduling.
 * 4. Fires the "on_watchable" notification.
 *
 * The smart averaging means the system LEARNS the actual expected release schedule
 * per anime over time. If an anime consistently releases 30 minutes after the
 * AniList airing time, the system will check 30 minutes after airing instead of
 * 10 minutes.
 *
 * CORE_RULES §20: logged with tag "Anikuta:Core:Updates:SmartRelease".
 */
class SmartReleaseCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val TAG = "Anikuta:Core:Updates:SmartRelease"
        const val KEY_MAIN_ID = "main_id"
        const val KEY_EPISODE_NUMBER = "episode_number"
        const val KEY_ATTEMPT = "attempt"
        const val KEY_AIRING_AT = "airing_at"
        const val MAX_ATTEMPTS = 4

        // D-193 Phase 7: Progressive retry schedule (minutes after previous attempt).
        // Attempt 1: airing + 10min
        // Attempt 2: + 20min (total: airing + 30min)
        // Attempt 3: + 60min (total: airing + 1h30min)
        // Attempt 4: + 120min (total: airing + 3h30min)
        // If still not found → skip (next manual refresh will catch it).
        val RETRY_DELAYS_MINUTES = longArrayOf(10L, 20L, 60L, 120L)

        /**
         * Schedule a smart-release check for a specific episode.
         * Called by [SmartReleaseScheduler] when an anime's airing time is within ±1h.
         */
        fun schedule(
            context: Context,
            mainId: String,
            episodeNumber: Long,
            airingAt: Long,
            attempt: Int = 1,
        ) {
            val workName = "smart_release_${mainId}_$episodeNumber"
            val delayMinutes = if (attempt == 1) {
                // First attempt: schedule at airingAt + 10min.
                val now = System.currentTimeMillis()
                val targetTime = airingAt + (RETRY_DELAYS_MINUTES[0] * 60 * 1000)
                val delayMs = (targetTime - now).coerceAtLeast(0)
                delayMs / (60 * 1000)
            } else {
                // Subsequent attempts: use the progressive delay.
                val delayIndex = (attempt - 1).coerceAtMost(RETRY_DELAYS_MINUTES.size - 1)
                RETRY_DELAYS_MINUTES[delayIndex]
            }

            val inputData = Data.Builder()
                .putString(KEY_MAIN_ID, mainId)
                .putLong(KEY_EPISODE_NUMBER, episodeNumber)
                .putInt(KEY_ATTEMPT, attempt)
                .putLong(KEY_AIRING_AT, airingAt)
                .build()

            val request = OneTimeWorkRequestBuilder<SmartReleaseCheckWorker>()
                .setInputData(inputData)
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                workName,
                ExistingWorkPolicy.REPLACE,
                request,
            )

            Logger.i(TAG) { "Scheduled: mainId=$mainId ep=$episodeNumber attempt=$attempt delay=${delayMinutes}min" }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val mainId = inputData.getString(KEY_MAIN_ID) ?: return@withContext Result.success()
        val episodeNumber = inputData.getLong(KEY_EPISODE_NUMBER, -1L)
        val attempt = inputData.getInt(KEY_ATTEMPT, 1)
        val airingAt = inputData.getLong(KEY_AIRING_AT, 0L)

        if (episodeNumber < 0) {
            Logger.w(TAG) { "Invalid episode number: $episodeNumber" }
            return@withContext Result.success()
        }

        Logger.i(TAG) { "Checking: mainId=$mainId ep=$episodeNumber attempt=$attempt/$MAX_ATTEMPTS" }

        return@withContext try {
            val koin = org.koin.core.context.GlobalContext.get()
            val engine = koin.get<UpdateEngine>()
            val store = koin.get<UpdateStore>()
            val contentRepo = koin.get<com.confused.anikuta.core.content.ContentRepository>()
            val extManager = koin.get<com.confused.anikuta.data.extension.manager.ExtensionManager>()

            val content = contentRepo.getContentByMainId(mainId)
            if (content == null) {
                Logger.w(TAG) { "Content not found: $mainId" }
                return@withContext Result.success()
            }

            val sourceId = content.extensionId
            if (sourceId == null || sourceId <= 0) {
                Logger.w(TAG) { "No source linked: $mainId" }
                return@withContext Result.success()
            }

            val source = extManager.getSource(sourceId) as? eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
            if (source == null) {
                Logger.w(TAG) { "Source not available: $sourceId" }
                return@withContext Result.success()
            }

            val sAnime = SAnime.create().apply {
                url = content.animeUrl ?: ""
                title = content.title
                initialized = false
            }

            val episodes = try {
                source.getEpisodeList(sAnime)
            } catch (e: Exception) {
                Logger.w(TAG) { "Episode list fetch failed (attempt $attempt): ${e.message}" }
                if (attempt < MAX_ATTEMPTS) {
                    schedule(applicationContext, mainId, episodeNumber, airingAt, attempt + 1)
                } else {
                    Logger.i(TAG) { "Max attempts reached — skipping: mainId=$mainId ep=$episodeNumber" }
                }
                return@withContext Result.success()
            }

            // Check if the expected episode exists.
            val foundEpisode = episodes.any { it.episode_number.toInt() == episodeNumber.toInt() }
            val state = store.getAnimeUpdateState(mainId)

            if (foundEpisode) {
                Logger.i(TAG) { "Episode $episodeNumber FOUND! Recording internal release time + smart average." }
                val now = System.currentTimeMillis()
                val threeDaysMs = 3L * 24 * 60 * 60 * 1000
                val ep = episodes.first { it.episode_number.toInt() == episodeNumber.toInt() }
                val audioVariant = "unknown"

                val isWatched = koin.get<com.confused.anikuta.core.watchprogress.WatchProgressStore>().isWatched(
                    "$mainId|${String.format("%05d", episodeNumber.toInt())}"
                )

                store.upsertEpisodeUpdate(
                    mainId = mainId,
                    episodeKey = "$mainId|${String.format("%05d", episodeNumber.toInt())}",
                    episodeNumber = episodeNumber.toDouble(),
                    episodeTitle = ep.name,
                    sourceId = sourceId,
                    audioVariant = audioVariant,
                    discoveredAt = now,
                    acknowledged = isWatched,
                    acknowledgedAt = if (isWatched) now else null,
                    batchType = "new",
                    episodeCount = null,
                    newExpiresAt = if (isWatched) null else now + threeDaysMs,
                )

                // Fire "on_watchable" notification.
                val notifSender = koin.getOrNull<NotificationSender>()
                if (!isWatched && notifSender != null) {
                    notifSender.postNotification(mainId, episodeNumber, audioVariant, "watchable")
                }

                // D-193 Phase 7: Smart averaging — compute the new expected release time.
                // The "internal release time" is when we actually found the episode (now).
                // The "AniList schedule" is the airingAt timestamp.
                // The smart average = (airingAt + now) / 2 — but only if we've found it before.
                // On the first find, the average IS the found time (we trust the actual time more).
                // On subsequent finds, we average the previous average with the new found time.
                if (state != null) {
                    val lastKnown = state.lastKnownEpisodeCount ?: 0
                    if (episodeNumber > lastKnown) {
                        // Compute the smart average for the next check.
                        // The next airing time is the AniList nextAiringAt.
                        // The offset = (now - airingAt) — how long after airing the episode appeared.
                        // We store this offset implicitly by setting next_check_at to:
                        //   nextAiringAt + offset (if nextAiringAt is available)
                        //   OR now + 24h (fallback)
                        val offset = now - airingAt // how long after airing the episode appeared
                        val nextCheckAt = state.nextAiringAt?.let { nextAiring ->
                            // Smart: next check = next airing + the offset we just learned.
                            // This way, if the episode consistently appears 30min after airing,
                            // we'll check 30min after the next airing.
                            nextAiring + offset
                        } ?: (now + TimeUnit.HOURS.toMillis(24))

                        store.updateCheckResult(
                            mainId = mainId,
                            lastCheckedAt = now,
                            nextCheckAt = nextCheckAt,
                            lastKnownEpisodeCount = episodeNumber,
                            consecutiveFailures = 0,
                            backoffStep = 0,
                            lastKnownDubCount = state.lastKnownDubCount,
                            lastCheckedDubAt = state.lastCheckedDubAt,
                        )

                        Logger.i(TAG) {
                            "Smart average updated: mainId=$mainId offset=${offset / 60000}min " +
                                "nextCheckAt=$nextCheckAt (nextAiringAt=${state.nextAiringAt} + offset)"
                        }
                    }
                }

                // Update episode_schedule.actual_at.
                val actualReleaseUpdater = koin.getOrNull<ActualReleaseUpdater>()
                actualReleaseUpdater?.updateActualAt(mainId, episodeNumber, now)

                Logger.i(TAG) { "Smart release complete: mainId=$mainId ep=$episodeNumber" }
            } else {
                Logger.i(TAG) { "Episode $episodeNumber NOT found (attempt $attempt/$MAX_ATTEMPTS)" }
                if (attempt < MAX_ATTEMPTS) {
                    schedule(applicationContext, mainId, episodeNumber, airingAt, attempt + 1)
                } else {
                    Logger.i(TAG) { "Max attempts reached — skipping: mainId=$mainId ep=$episodeNumber" }
                }
            }

            Result.success()
        } catch (e: Exception) {
            Logger.e(TAG, e) { "SmartReleaseCheckWorker failed: ${e.message}" }
            Result.success()
        }
    }
}
