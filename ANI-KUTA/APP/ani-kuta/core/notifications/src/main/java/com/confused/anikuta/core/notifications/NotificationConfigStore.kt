package com.confused.anikuta.core.notifications

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.database.AnikutaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Store for notification configuration + sent-log (Phase NOTIF).
 *
 * Per-content config: which anime to notify for, which trigger types,
 * sub/dub preference.
 *
 * Sent-log: dedup — prevents sending the same notification twice.
 * NOT backup-eligible (ephemeral — regenerated on first fire after restore).
 *
 * CORE_RULES §20: logged with tag "Anikuta:Core:Notifications".
 */
class NotificationConfigStore(
    private val database: AnikutaDatabase,
) {
    companion object {
        private const val TAG = "Anikuta:Core:Notifications"
    }

    /** Get the notification config for an anime (null = use defaults). */
    suspend fun getConfig(mainId: String): NotificationConfig? = withContext(Dispatchers.IO) {
        database.notificationsQueries.getNotificationConfig(mainId).executeAsOneOrNull()?.let {
            NotificationConfig(
                mainId = it.main_id,
                enabled = it.enabled.toInt() == 1,
                notifyOnSchedule = TriggerState.fromDb(it.notify_on_schedule),
                notifyOnWatchable = TriggerState.fromDb(it.notify_on_watchable),
                notifyOnImmediate = TriggerState.fromDb(it.notify_on_immediate),
                audioPref = AudioPref.fromBooleans(
                    it.notify_sub.toInt() == 1,
                    it.notify_dub.toInt() == 1,
                ),
            )
        }
    }

    /** Get all enabled configs (for the Schedule engine). */
    suspend fun getEnabledConfigs(): List<NotificationConfig> = withContext(Dispatchers.IO) {
        database.notificationsQueries.getEnabledNotificationConfigs().executeAsList().map {
            NotificationConfig(
                mainId = it.main_id,
                enabled = it.enabled.toInt() == 1,
                notifyOnSchedule = TriggerState.fromDb(it.notify_on_schedule),
                notifyOnWatchable = TriggerState.fromDb(it.notify_on_watchable),
                notifyOnImmediate = TriggerState.fromDb(it.notify_on_immediate),
                audioPref = AudioPref.fromBooleans(
                    it.notify_sub.toInt() == 1,
                    it.notify_dub.toInt() == 1,
                ),
            )
        }
    }

    /** Set/update the config for an anime. */
    suspend fun setConfig(config: NotificationConfig) = withContext(Dispatchers.IO) {
        database.notificationsQueries.upsertNotificationConfig(
            main_id = config.mainId,
            enabled = if (config.enabled) 1L else 0L,
            notify_on_schedule = config.notifyOnSchedule.dbValue,
            notify_on_watchable = config.notifyOnWatchable.dbValue,
            notify_on_immediate = config.notifyOnImmediate.dbValue,
            notify_sub = if (config.audioPref.subBoolean()) 1L else 0L,
            notify_dub = if (config.audioPref.dubBoolean()) 1L else 0L,
        )
        Logger.i(TAG) { "setConfig: mainId=${config.mainId} enabled=${config.enabled}" }
    }

    /** Check if a notification was already sent (dedup). */
    suspend fun wasNotified(
        mainId: String,
        episodeNumber: Long,
        audioVariant: String,
        triggerType: String,
    ): Boolean = withContext(Dispatchers.IO) {
        database.notificationsQueries.isNotificationSent(
            mainId, episodeNumber, audioVariant, triggerType,
        ).executeAsOne()
    }

    /** Record a sent notification (for dedup). */
    suspend fun recordSent(
        mainId: String,
        episodeNumber: Long,
        audioVariant: String,
        triggerType: String,
    ) = withContext(Dispatchers.IO) {
        database.notificationsQueries.recordNotificationSent(
            mainId, episodeNumber, audioVariant, triggerType, System.currentTimeMillis(),
        )
    }

    /** Delete old sent records (retention — 90 days). */
    suspend fun cleanupOldSent(cutoff: Long) = withContext(Dispatchers.IO) {
        database.notificationsQueries.deleteOldSentNotifications(cutoff)
    }
}
