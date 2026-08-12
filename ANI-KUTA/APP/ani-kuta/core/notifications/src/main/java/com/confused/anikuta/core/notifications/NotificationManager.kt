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
        private const val CHANNEL_ID_SILENT = "anikuta_new_episodes_silent"
        private const val CHANNEL_NAME_SILENT = "New episodes (silent)"
        private const val NOTIFICATION_ID_BASE = 30000
    }

    init {
        ensureChannel()
        ensureSilentChannel()
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

        // 2. Check trigger type — tri-state (ON / SILENT / OFF). OFF suppresses;
        //    ON + SILENT both post but SILENT uses the low-importance channel.
        val triggerState = when (triggerType) {
            "schedule" -> config.notifyOnSchedule
            "watchable" -> config.notifyOnWatchable
            "immediate" -> config.notifyOnImmediate
            else -> TriggerState.OFF
        }
        if (triggerState == TriggerState.OFF) {
            Logger.d(TAG) { "postNotification — suppressed (trigger $triggerType off): mainId=$mainId" }
            return false
        }
        val silent = triggerState == TriggerState.SILENT

        // 3. Check sub/dub (derived from AudioPref).
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

        // 6. Post the notification. Silent → low-importance channel (no sound);
        //    otherwise the default channel (sound + popup).
        val notifId = (NOTIFICATION_ID_BASE + (mainId.hashCode() and 0x3FFF) + episodeNumber.toInt()).coerceAtMost(Int.MAX_VALUE)
        val displayAudio = if (audioVariant == "sub") "SUB" else if (audioVariant == "dub") "DUB" else ""
        val text = "EP $episodeNumber${if (displayAudio.isNotBlank()) " · $displayAudio" else ""} is now available"
        val channel = if (silent) CHANNEL_ID_SILENT else CHANNEL_ID
        val priority = if (silent) NotificationCompat.PRIORITY_LOW
        else NotificationCompat.PRIORITY_DEFAULT

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(priority)
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

    /** Low-importance channel for SILENT trigger notifications (no sound). */
    private fun ensureSilentChannel() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID_SILENT) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID_SILENT,
                CHANNEL_NAME_SILENT,
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Silent notifications for new episode releases (no sound)"
            }
            nm.createNotificationChannel(channel)
            Logger.i(TAG) { "Silent notification channel created: $CHANNEL_ID_SILENT" }
        }
    }

    /**
     * D-193 Phase 7: Post a test notification (for the "Send test notification" button).
     *
     * Posts a demo notification with hardcoded content: "Demon Slayer — Episode 6 DUB".
     * Bypasses per-anime config (it's a test). Uses the default channel (with sound).
     * Dedicated notification ID (999) for cancellation.
     */
    suspend fun postTestNotification() {
        if (!preferences.notificationsEnabled) {
            Logger.w(TAG) { "postTestNotification — master toggle is OFF, not posting" }
            return
        }

        // Check POST_NOTIFICATIONS permission on Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Logger.w(TAG) { "postTestNotification — POST_NOTIFICATIONS not granted" }
                return
            }
        }

        ensureChannel()

        val title = "New episode available"
        val text = "Demon Slayer — Episode 6 DUB"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(999, builder.build())
            Logger.i(TAG) { "Test notification posted: '$text'" }
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Failed to post test notification: ${e.message}" }
        }
    }
}
