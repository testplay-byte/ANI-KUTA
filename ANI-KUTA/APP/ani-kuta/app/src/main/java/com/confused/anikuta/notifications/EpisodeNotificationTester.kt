package com.confused.anikuta.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.datacache.DataCacheRepository
import com.confused.anikuta.core.notifications.NotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * D-478/D-483/D-495: the "Send test notifications" action.
 *
 * # The round-50 selection spec (the user's own words)
 *
 * - "If there are some entries saved in the library, then it should randomly
 *   pick one of the entries for the first notification and should randomly
 *   pick another entry for the second notification." → the LIBRARY is the
 *   demo source: [count] distinct random entries (ANY entry qualifies — the
 *   old cached-episodes gate is gone; an entry without cached episodes still
 *   has cover art in the details row and composes a full banner). When the
 *   library has fewer entries than requested, the same entries cycle with a
 *   different cached episode where possible, so the second post still goes
 *   out instead of silently not happening.
 * - "If there are no entries in the library, then the test notification
 *   should just show a random test notification." → [EpisodeDemoPicker.pickPureDemo]:
 *   a built-in demo payload that composes on the composer's styled no-art
 *   stage. The tester can therefore ALWAYS deliver — the old "no feed rows
 *   AND no eligible library content — nothing to demo" silent bail is gone.
 *
 * The episode_update FEED is deliberately no longer consulted here (it was
 * the D-483 feed-first rule; the user's round-50 spec replaces it — the feed
 * remains the PREVIEW's initial-selection source).
 *
 * Delivery is STAGGERED: the first posts immediately, the second 30 SECONDS
 * later via WorkManager (survives app death) — D-503: the user moved the
 * stagger up from 5 minutes ("have that one sent after 30 seconds rather
 * than 5 minutes").
 */
class EpisodeNotificationTester(
    private val context: Context,
    private val contentRepository: ContentRepository,
    private val dataCacheRepository: DataCacheRepository,
    private val notificationManager: NotificationManager,
    private val demoPicker: EpisodeDemoPicker,
) {

    /**
     * Posts up to [count] test poster notifications (1 now + the rest
     * staggered 30 seconds apart — D-503). Returns how many were scheduled.
     * Always ≥ 1 when notifications are permitted (D-495) — the empty-library
     * demo guarantees a payload.
     *
     * D-486: the library scan below is BLOCKING SQLDelight reads — they run
     * on Dispatchers.IO, not the caller's main-dispatch
     * rememberCoroutineScope (the composer offloads itself, this wrapper
     * covers the tester's own reads).
     */
    suspend fun postRecentUpdateNotifications(count: Int = 2): Int = withContext(Dispatchers.IO) {
        // The POST_NOTIFICATIONS gate (Android 13+).
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Logger.w(TAG) { "test: POST_NOTIFICATIONS not granted" }
                return@withContext 0
            }
        }
        if (!notificationManager.areNotificationsEnabled()) {
            Logger.w(TAG) { "test: notifications master toggle is OFF" }
            return@withContext 0
        }

        // ── The demo set: random LIBRARY entries (D-495), pure-demo top-up. ──
        val demos = mutableListOf<EpisodeDemoPicker.Demo>()
        for (mainId in contentRepository.getLibraryMainIds().shuffled()) {
            if (demos.size >= count) break
            val title = contentRepository.getMainEntryByMainId(mainId)?.title ?: continue
            val episodes = dataCacheRepository.getEpisodeMetadata(mainId)
            demos.add(
                EpisodeDemoPicker.Demo(
                    mainId = mainId,
                    title = title,
                    // The entry's latest cached episode; an entry whose
                    // episodes were never cached still demos fine (the
                    // banner just shows the episode number, no ep title).
                    episodeNumber = episodes.maxByOrNull { it.episodeNumber }?.episodeNumber?.toDouble() ?: 1.0,
                    audioVariant = listOf("sub", "dub").random(),
                ),
            )
        }
        // Fewer entries than requested → cycle them with a different episode
        // where the cache offers one, so both posts never read identically.
        var cycle = 0
        while (demos.isNotEmpty() && demos.size < count) {
            val base = demos[cycle % demos.size]
            demos.add(base.copy(episodeNumber = nextEpisodeVariant(base.mainId, base.episodeNumber)))
            cycle++
        }
        // D-495: EMPTY library → random pure-demo notifications. Always
        // something to show.
        while (demos.size < count) {
            demos.add(demoPicker.pickPureDemo())
        }

        var scheduled = 0
        demos.take(count).forEachIndexed { index, demo ->
            // D-503: the stagger moved from 5 MINUTES to 30 SECONDS (the
            // user's round-52 spec) — the WorkManager path is kept so the
            // second post still survives app death.
            val delaySeconds = index * 30L  // 1st: now · 2nd: 30 s · (3rd: 60 …)
            if (delaySeconds == 0L) {
                // The FIRST test posts immediately.
                notificationManager.postPosterNotification(
                    notifId = 990 + index,
                    mainId = demo.mainId,
                    title = demo.title,
                    episodeNumber = demo.episodeNumber,
                    audioVariant = demo.audioVariant,
                )
            } else {
                // The SECOND (and any later) test: WorkManager, 30 seconds
                // apart, surviving app death — the payload rides the work
                // data and the worker re-composes the banner at fire time.
                // D-495: the TITLE rides too — a pure demo has no library
                // row to look it up from at fire time.
                val request = OneTimeWorkRequestBuilder<DelayedPosterTestWorker>()
                    .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                    .setInputData(
                        workDataOf(
                            DelayedPosterTestWorker.KEY_MAIN_ID to demo.mainId,
                            DelayedPosterTestWorker.KEY_TITLE to demo.title,
                            DelayedPosterTestWorker.KEY_EPISODE to demo.episodeNumber,
                            DelayedPosterTestWorker.KEY_VARIANT to demo.audioVariant,
                        ),
                    )
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "anikuta_test_poster_delayed_$index",
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
            }
            scheduled++
            Logger.i(TAG) { "test poster #$index scheduled for ${demo.title} (delay=${delaySeconds}s)" }
        }
        scheduled
    }

    /**
     * A different cached episode of the same entry (for cycled re-posts) —
     * the base's own episode is filtered out so the second post never reads
     * identically; falls back to the base episode only when the cache has
     * nothing else (single/zero cached episodes).
     */
    private fun nextEpisodeVariant(mainId: String, fallback: Double): Double {
        val others = dataCacheRepository.getEpisodeMetadata(mainId)
            .map { it.episodeNumber.toDouble() }
            .filter { it != fallback }
        return if (others.isNotEmpty()) others.random() else fallback
    }

    private companion object {
        private const val TAG = "Anikuta:App:NotifTester"
    }
}
