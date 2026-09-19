package com.confused.anikuta.notifications

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.notifications.NotificationManager
import com.confused.anikuta.core.updates.UpdateStore
import com.confused.anikuta.core.common.Logger

/**
 * D-478: the "Send test notification" action, REDEFINED — instead of two
 * hardcoded sample strings ("Demon Slayer — Episode 6 DUB"), the tester posts
 * poster-style notifications built from the USER'S OWN last two updated
 * contents (the newest rows in the episode_update feed): real cover/banner
 * art, real episode numbers, real audio variants, real episode titles.
 *
 * The user thereby sees EXACTLY what future notifications will look like for
 * content they actually follow — different for every user, changing as their
 * library updates.
 *
 * Falls back to the old sample when the feed is empty (a fresh install).
 */
class EpisodeNotificationTester(
    private val context: Context,
    private val updateStore: UpdateStore,
    private val contentRepository: ContentRepository,
    private val composer: EpisodeBannerComposer,
    private val notificationManager: NotificationManager,
) {

    /**
     * Posts up to [count] poster notifications built from the most recently
     * updated contents (distinct by mainId, newest first). Returns how many
     * were posted.
     */
    suspend fun postRecentUpdateNotifications(count: Int = 2): Int {
        // The POST_NOTIFICATIONS gate (Android 13+) — same check the old test flow did.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Logger.w(TAG) { "test: POST_NOTIFICATIONS not granted" }
                return 0
            }
        }
        if (!notificationManager.areNotificationsEnabled()) {
            Logger.w(TAG) { "test: notifications master toggle is OFF" }
            return 0
        }

        val feed = updateStore.getAllUpdates(limit = 50)
        // The latest row per content (distinct mainId, newest first).
        val latestPerContent = LinkedHashSet<String>().also { seen ->
            feed.forEach { row -> if (row.mainId !in seen) seen.add(row.mainId) }
        }.take(count)

        if (latestPerContent.isEmpty()) {
            Logger.i(TAG) { "test: the update feed is empty — falling back to the sample" }
            notificationManager.postSingleTestNotification(
                notifId = 999,
                title = "New episode available",
                text = "Demon Slayer — Episode 6 DUB",
            )
            return 1
        }

        var posted = 0
        latestPerContent.forEachIndexed { index, mainId ->
            val row = feed.first { it.mainId == mainId }
            val details = contentRepository.getContentDetails(mainId)
            val title = details?.title ?: "Unknown anime"
            val displayAudio = when (row.audioVariant) {
                "sub" -> "SUB"
                "dub" -> "DUB"
                else -> ""
            }
            val text = "EP ${row.episodeNumber.toInt()}" +
                (if (displayAudio.isNotBlank()) " · $displayAudio" else "") +
                " is now available"

            // The composed banner — the same art the real notifications use.
            val banner = composer.buildBanner(
                mainId = mainId,
                title = title,
                episodeNumber = row.episodeNumber,
                audioVariant = row.audioVariant,
            )

            val style = if (banner != null) {
                NotificationCompat.BigPictureStyle()
                    .bigPicture(banner)
                    .bigLargeIcon(null as? android.graphics.Bitmap)
                    .setBigContentTitle(title)
                    .setSummaryText(text)
            } else {
                NotificationCompat.BigTextStyle().bigText(text)
            }

            val notification = NotificationCompat.Builder(context, NotificationManager.CHANNEL_ID)
                .setSmallIcon(com.confused.anikuta.core.notifications.R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(style)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(TEST_ID_BASE + index, notification)
            posted++
            Logger.i(TAG) { "test: posted a poster notification for $title (mainId=$mainId)" }
        }
        return posted
    }

    private companion object {
        private const val TAG = "Anikuta:App:NotifTester"
        private const val TEST_ID_BASE = 900
    }
}
