package com.confused.anikuta.core.updates

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.preferences.UpdatePreferences
import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * D-193 Phase 5: Smart release detection worker.
 *
 * Polls an extension for a specific episode at +10min, +20min, +30min after the
 * AniList airing time. If the episode is found → marks it as released + fires
 * the "on_watchable" notification. If not found after 3 attempts → skips.
 *
 * Scheduled via [SmartReleaseScheduler] as a OneTimeWorkRequest with a 10-min delay.
 * The worker re-schedules itself (with attempt+1) if the episode isn't found +
 * attempts remain.
 *
 * Process death safety: WorkManager survives process death. The inputData carries
 * mainId, episodeNumber, attempt counter, + airingAt so the chain resumes correctly.
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
        const val MAX_ATTEMPTS = 3
        const val RETRY_DELAY_MINUTES = 10L

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
                val targetTime = airingAt + (RETRY_DELAY_MINUTES * 60 * 1000)
                val delayMs = (targetTime - now).coerceAtLeast(0)
                delayMs / (60 * 1000)
            } else {
                RETRY_DELAY_MINUTES
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

            // Get the content + its linked source.
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

            // Fetch the episode list from the extension.
            val sAnime = SAnime.create().apply {
                url = content.animeUrl ?: ""
                title = content.title
                initialized = false
            }

            val episodes = try {
                source.getEpisodeList(sAnime)
            } catch (e: Exception) {
                Logger.w(TAG) { "Episode list fetch failed (attempt $attempt): ${e.message}" }
                // Retry if attempts remain.
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
                Logger.i(TAG) { "Episode $episodeNumber FOUND on extension! Marking as released." }

                // Insert episode_update row + fire notification (via engine's checkSingleAnime-like logic).
                // For simplicity, we delegate to the engine's notification path.
                val now = System.currentTimeMillis()
                val threeDaysMs = 3L * 24 * 60 * 60 * 1000
                val ep = episodes.first { it.episode_number.toInt() == episodeNumber.toInt() }
                val audioVariant = "unknown" // parseAudioVariant is private — deferred to Phase 6 rewrite

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

                // Update the state.
                if (state != null) {
                    val lastKnown = state.lastKnownEpisodeCount ?: 0
                    if (episodeNumber > lastKnown) {
                        val nextCheckAt = state.nextAiringAt?.let { it + TimeUnit.HOURS.toMillis(1) }
                            ?: (now + TimeUnit.HOURS.toMillis(24))
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
                    }
                }

                Logger.i(TAG) { "Smart release complete: mainId=$mainId ep=$episodeNumber" }
            } else {
                Logger.i(TAG) { "Episode $episodeNumber NOT found (attempt $attempt/$MAX_ATTEMPTS)" }
                // Retry if attempts remain.
                if (attempt < MAX_ATTEMPTS) {
                    schedule(applicationContext, mainId, episodeNumber, airingAt, attempt + 1)
                } else {
                    Logger.i(TAG) { "Max attempts reached — skipping: mainId=$mainId ep=$episodeNumber" }
                }
            }

            Result.success()
        } catch (e: Exception) {
            Logger.e(TAG, e) { "SmartReleaseCheckWorker failed: ${e.message}" }
            Result.success() // Don't retry via WorkManager — we handle retries ourselves.
        }
    }
}
