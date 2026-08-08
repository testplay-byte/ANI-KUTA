package com.confused.anikuta.core.preferences

import kotlinx.coroutines.flow.Flow

/**
 * Global notification preferences (Phase NOTIF — settings UI).
 *
 * Two layers of notification control:
 * 1. **Global master toggle** ([notificationsEnabled]) — the kill switch. When off,
 *    [com.confused.anikuta.core.notifications.NotificationManager] suppresses every
 *    notification regardless of per-anime config.
 * 2. **Default trigger + audio prefs** — applied when a user enables notifications
 *    for a new anime (the per-anime [NotificationConfig] is seeded from these
 *    defaults). Per-anime config can then be tweaked individually.
 *
 * Per-anime config lives in [NotificationConfigStore] (DB-backed). This class is
 * only the global preferences (SharedPreferences-backed).
 *
 * Backed by [PreferenceStore]. Reactive via the `*Flow` accessors (for the settings
 * UI to collect as State).
 *
 * CORE_RULES §23: UI toggles flip mutableStateOf snapshots; the underlying
 * SharedPreferences write happens immediately.
 */
class NotificationPreferences(private val store: PreferenceStore) {

    /** Master kill switch — when false, NO notifications are posted. */
    var notificationsEnabled: Boolean
        get() = store.getBoolean(KEY_ENABLED, true)
        set(value) = store.putBoolean(KEY_ENABLED, value)

    fun notificationsEnabledFlow(): Flow<Boolean> =
        store.booleanFlow(KEY_ENABLED, true)

    // ── Default trigger types (applied when enabling a new anime) ─────────────

    /** Notify when the scheduled airing time is reached. */
    var defaultNotifyOnSchedule: Boolean
        get() = store.getBoolean(KEY_DEF_SCHEDULE, false)
        set(value) = store.putBoolean(KEY_DEF_SCHEDULE, value)

    fun defaultNotifyOnScheduleFlow(): Flow<Boolean> =
        store.booleanFlow(KEY_DEF_SCHEDULE, false)

    /** Notify when the episode is found on a source (watchable). */
    var defaultNotifyOnWatchable: Boolean
        get() = store.getBoolean(KEY_DEF_WATCHABLE, true)
        set(value) = store.putBoolean(KEY_DEF_WATCHABLE, value)

    fun defaultNotifyOnWatchableFlow(): Flow<Boolean> =
        store.booleanFlow(KEY_DEF_WATCHABLE, true)

    /** Notify immediately when the schedule says released (no source check). */
    var defaultNotifyOnImmediate: Boolean
        get() = store.getBoolean(KEY_DEF_IMMEDIATE, false)
        set(value) = store.putBoolean(KEY_DEF_IMMEDIATE, value)

    fun defaultNotifyOnImmediateFlow(): Flow<Boolean> =
        store.booleanFlow(KEY_DEF_IMMEDIATE, false)

    // ── Default audio variant prefs ──────────────────────────────────────────

    var defaultNotifySub: Boolean
        get() = store.getBoolean(KEY_DEF_SUB, true)
        set(value) = store.putBoolean(KEY_DEF_SUB, value)

    fun defaultNotifySubFlow(): Flow<Boolean> =
        store.booleanFlow(KEY_DEF_SUB, true)

    var defaultNotifyDub: Boolean
        get() = store.getBoolean(KEY_DEF_DUB, false)
        set(value) = store.putBoolean(KEY_DEF_DUB, value)

    fun defaultNotifyDubFlow(): Flow<Boolean> =
        store.booleanFlow(KEY_DEF_DUB, false)

    companion object {
        private const val KEY_ENABLED = "notif_master_enabled"
        private const val KEY_DEF_SCHEDULE = "notif_def_schedule"
        private const val KEY_DEF_WATCHABLE = "notif_def_watchable"
        private const val KEY_DEF_IMMEDIATE = "notif_def_immediate"
        private const val KEY_DEF_SUB = "notif_def_sub"
        private const val KEY_DEF_DUB = "notif_def_dub"
    }
}
