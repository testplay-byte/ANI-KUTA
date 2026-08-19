package com.confused.anikuta.core.preferences

import com.confused.anikuta.core.notifications.AudioPref
import com.confused.anikuta.core.notifications.TriggerState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Global notification preferences (Phase NOTIF — settings UI).
 *
 * Two layers of notification control:
 * 1. **Global master toggle** ([notificationsEnabled]) — the kill switch. When off,
 *    [com.confused.anikuta.core.notifications.NotificationManager] suppresses every
 *    notification regardless of per-anime config.
 * 2. **Default trigger + audio prefs** — applied when a user enables notifications
 *    for a new anime (the per-anime [com.confused.anikuta.core.notifications.NotificationConfig]
 *    is seeded from these defaults). Per-anime config can then be tweaked individually.
 *
 * Triggers are tri-state ([TriggerState]: ON / SILENT / OFF). Audio is tri-state
 * ([AudioPref]: SUB / DUB / BOTH), stored as two booleans (sub + dub) in
 * SharedPreferences for symmetry with the DB schema.
 *
 * Backed by [PreferenceStore]. Reactive via the `*Flow` accessors.
 */
class NotificationPreferences(private val store: PreferenceStore) {

    init {
        // One-time migration: the trigger defaults were stored as Boolean in the
        // previous build (notif_def_schedule/watchable/immediate = true/false). This
        // build stores them as Int (0=OFF, 1=ON, 2=SILENT). SharedPreferences does NOT
        // auto-convert types — getInt on a Boolean key throws ClassCastException at
        // runtime (crash on opening the Notifications page). Migrate each key: if it
        // currently holds a Boolean, map true→1 (ON) / false→0 (OFF) and write it as
        // Int. Idempotent + safe: if the key is absent or already an Int, nothing
        // happens. Runs at singleton construction (before any flow is collected).
        // SharedPreferences.apply() updates the in-memory cache synchronously, so the
        // subsequent flow reads see the migrated Int values — no race.
        migrateLegacyBooleanTriggersToInt()
    }

    private fun migrateLegacyBooleanTriggersToInt() {
        for (key in listOf(KEY_DEF_SCHEDULE, KEY_DEF_WATCHABLE, KEY_DEF_IMMEDIATE)) {
            try {
                // If the key is absent OR already an Int, this succeeds (no migration).
                // Absent → returns 0 without writing (preserves "use default" semantics).
                store.getInt(key, 0)
            } catch (e: ClassCastException) {
                // The key holds a Boolean from the old build. Read it + map to Int.
                val oldBool = try {
                    store.getBoolean(key, false)
                } catch (e2: ClassCastException) {
                    false // Unexpected type — fall back to OFF.
                }
                store.putInt(key, if (oldBool) 1 else 0)
            }
        }
    }

    /** Master kill switch — when false, NO notifications are posted. */
    var notificationsEnabled: Boolean
        get() = store.getBoolean(KEY_ENABLED, true)
        set(value) = store.putBoolean(KEY_ENABLED, value)

    fun notificationsEnabledFlow(): Flow<Boolean> =
        store.booleanFlow(KEY_ENABLED, true)

    // ── Default trigger states (tri-state) ─────────────────────────────────────
    // Stored as Int (0=OFF, 1=ON, 2=SILENT) — matches TriggerState.dbValue.

    var defaultNotifyOnSchedule: TriggerState
        get() = TriggerState.fromDb(store.getInt(KEY_DEF_SCHEDULE, TriggerState.OFF.dbValue.toInt()).toLong())
        set(value) = store.putInt(KEY_DEF_SCHEDULE, value.dbValue.toInt())

    fun defaultNotifyOnScheduleFlow(): Flow<TriggerState> =
        store.intFlow(KEY_DEF_SCHEDULE, TriggerState.OFF.dbValue.toInt())
            .map { TriggerState.fromDb(it.toLong()) }

    var defaultNotifyOnWatchable: TriggerState
        get() = TriggerState.fromDb(store.getInt(KEY_DEF_WATCHABLE, TriggerState.ON.dbValue.toInt()).toLong())
        set(value) = store.putInt(KEY_DEF_WATCHABLE, value.dbValue.toInt())

    fun defaultNotifyOnWatchableFlow(): Flow<TriggerState> =
        store.intFlow(KEY_DEF_WATCHABLE, TriggerState.ON.dbValue.toInt())
            .map { TriggerState.fromDb(it.toLong()) }

    var defaultNotifyOnImmediate: TriggerState
        get() = TriggerState.fromDb(store.getInt(KEY_DEF_IMMEDIATE, TriggerState.OFF.dbValue.toInt()).toLong())
        set(value) = store.putInt(KEY_DEF_IMMEDIATE, value.dbValue.toInt())

    fun defaultNotifyOnImmediateFlow(): Flow<TriggerState> =
        store.intFlow(KEY_DEF_IMMEDIATE, TriggerState.OFF.dbValue.toInt())
            .map { TriggerState.fromDb(it.toLong()) }

    // ── Default audio pref (tri-state, stored as two booleans) ─────────────────

    var defaultAudioPref: AudioPref
        get() = AudioPref.fromBooleans(
            store.getBoolean(KEY_DEF_SUB, true),
            store.getBoolean(KEY_DEF_DUB, false),
        )
        set(value) {
            store.putBoolean(KEY_DEF_SUB, value.subBoolean())
            store.putBoolean(KEY_DEF_DUB, value.dubBoolean())
        }

    /** Reactive flow combining the two audio booleans into an [AudioPref]. */
    fun defaultAudioPrefFlow(): Flow<AudioPref> =
        kotlinx.coroutines.flow.combine(
            store.booleanFlow(KEY_DEF_SUB, true),
            store.booleanFlow(KEY_DEF_DUB, false),
        ) { sub, dub -> AudioPref.fromBooleans(sub, dub) }

    // ── Library customization toggle (D-193 v2) ────────────────────────────────
    // When OFF (default): the default triggers above apply to every anime in the
    // library. No per-anime notification UI appears on the details page.
    // When ON: each anime's details page gains a notifications section where the
    // user can enable/disable + override triggers per anime individually.

    var libraryCustomizationEnabled: Boolean
        get() = store.getBoolean(KEY_LIBRARY_CUSTOM, false)
        set(value) = store.putBoolean(KEY_LIBRARY_CUSTOM, value)

    fun libraryCustomizationEnabledFlow(): Flow<Boolean> =
        store.booleanFlow(KEY_LIBRARY_CUSTOM, false)

    companion object {
        private const val KEY_ENABLED = "notif_master_enabled"
        private const val KEY_DEF_SCHEDULE = "notif_def_schedule"
        private const val KEY_DEF_WATCHABLE = "notif_def_watchable"
        private const val KEY_DEF_IMMEDIATE = "notif_def_immediate"
        private const val KEY_DEF_SUB = "notif_def_sub"
        private const val KEY_DEF_DUB = "notif_def_dub"
        private const val KEY_LIBRARY_CUSTOM = "notif_library_custom_enabled"
    }
}
