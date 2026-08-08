package com.confused.anikuta.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.preferences.NotificationPreferences

/**
 * Posts system notifications for new episode releases (Phase NOTIF).
 *
 * Checks the per-content config + dedup log before posting.
 * Uses a dedicated notification channel ("New episodes").
 *
 * Future-proofing: the trigger types are designed to support multiple
 * sources (AniList, extension sources, our own local estimator). The
 * NotificationConfigStore + notification_sent table are source-agnostic —
 * they key off main_id + episode_number + audio_variant + trigger_type.
 *
 * CORE_RULES §20: logged with tag "Anikuta:Core:Notifications".
 */
class NotificationManager(
    private val context: Context,
    private val configStore: NotificationConfigStore,
    private val contentRepository: ContentRepository,
    private val preferences: NotificationPreferences,
) {
    companion object {
        private const val TAG = "Anikuta:Core:Notifications"
        private const val CHANNEL_ID = "anikuta_new_episodes"
        private const val CHANNEL_NAME = "New episodes"
        private const val NOTIFICATION_ID_BASE = 30000
    }

    init {
        ensureChannel()
    }

    /**
     * Posts a notification for a new episode release.
     *
     * @param mainId The content's main_id.
     * @param episodeNumber The episode number.
     * @param audioVariant "sub" | "dub" | "unknown".
     * @param triggerType "schedule" | "watchable" | "immediate".
     * @return true if the notification was posted, false if suppressed.
     */
    suspend fun postNotification(
        mainId: String,
        episodeNumber: Long,
        audioVariant: String,
        triggerType: String,
    ): Boolean {
        // 0. Global master toggle — the kill switch. When off, suppress everything.
        if (!preferences.notificationsEnabled) {
            Logger.d(TAG) { "postNotification — suppressed (global master off)" }
            return false
        }

        // 1. Check config.
        val config = configStore.getConfig(mainId)
        if (config == null || !config.enabled) {
            Logger.d(TAG) { "postNotification — suppressed (not enabled): mainId=$mainId" }
            return false
        }

        // 2. Check trigger type.
        val triggerEnabled = when (triggerType) {
            "schedule" -> config.notifyOnSchedule
            "watchable" -> config.notifyOnWatchable
            "immediate" -> config.notifyOnImmediate
            else -> false
        }
        if (!triggerEnabled) {
            Logger.d(TAG) { "postNotification — suppressed (trigger $triggerType not enabled): mainId=$mainId" }
            return false
        }

        // 3. Check sub/dub.
        val audioOk = when (audioVariant) {
            "sub" -> config.notifySub
            "dub" -> config.notifyDub
            else -> true // unknown — notify (best-effort)
        }
        if (!audioOk) {
            Logger.d(TAG) { "postNotification — suppressed ($audioVariant not enabled): mainId=$mainId" }
            return false
        }

        // 4. Check dedup.
        if (configStore.wasNotified(mainId, episodeNumber, audioVariant, triggerType)) {
            Logger.d(TAG) { "postNotification — suppressed (already sent): mainId=$mainId ep=$episodeNumber $triggerType" }
            return false
        }

        // 5. Get anime title.
        val content = contentRepository.getContentByMainId(mainId)
        val title = content?.title ?: "Unknown anime"

        // 6. Post the notification.
        val notifId = (NOTIFICATION_ID_BASE + (mainId.hashCode() and 0x3FFF) + episodeNumber.toInt()).coerceAtMost(Int.MAX_VALUE)
        val displayAudio = if (audioVariant == "sub") "SUB" else if (audioVariant == "dub") "DUB" else ""
        val text = "EP $episodeNumber${if (displayAudio.isNotBlank()) " · $displayAudio" else ""} is now available"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifId, notification)

        // 7. Record in dedup log.
        configStore.recordSent(mainId, episodeNumber, audioVariant, triggerType)

        Logger.i(TAG) { "postNotification — POSTED: mainId=$mainId title=$title ep=$episodeNumber audio=$audioVariant trigger=$triggerType" }
        return true
    }

    private fun ensureChannel() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Notifications for new episode releases"
            }
            nm.createNotificationChannel(channel)
            Logger.i(TAG) { "Notification channel created: $CHANNEL_ID" }
        }
    }
}
