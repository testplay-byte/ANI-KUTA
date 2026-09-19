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

    /**
     * Master kill switch — when false, NO notifications are posted.
     *
     * D-441 (round 39): default flipped true → false. The app's notifications
     * are now OFF by default on a fresh install — the user must opt in via
     * Settings → Updates → Notifications. Users who already toggled the switch
     * keep their stored value (the default only applies when the key is absent,
     * i.e. the toggle was never touched).
     */
    var notificationsEnabled: Boolean
        get() = store.getBoolean(KEY_ENABLED, false)
        set(value) = store.putBoolean(KEY_ENABLED, value)

    fun notificationsEnabledFlow(): Flow<Boolean> =
        store.booleanFlow(KEY_ENABLED, false)

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

    // ── D-477: the poster-notification customization ──────────────────────────
    // The episode notifications render a composed BANNER (cover art + scrim +
    // titles + SUB/DUB badge + optional episode thumbnail) instead of plain
    // text. These keys drive both the composer (:app) and the live preview.

    var posterEnabled: Boolean
        get() = store.getBoolean(KEY_POSTER_ENABLED, true)
        set(value) = store.putBoolean(KEY_POSTER_ENABLED, value)

    fun posterEnabledFlow(): Flow<Boolean> = store.booleanFlow(KEY_POSTER_ENABLED, true)

    /** Background art preference: "banner" (default — falls back to cover),
     * "cover" (always the poster art). */
    var posterBackgroundSource: String
        get() = store.getString(KEY_POSTER_BACKGROUND, "banner")
        set(value) = store.putString(KEY_POSTER_BACKGROUND, value)

    var posterShowEpisodeTitle: Boolean
        get() = store.getBoolean(KEY_POSTER_EP_TITLE, true)
        set(value) = store.putBoolean(KEY_POSTER_EP_TITLE, value)

    var posterShowEpisodeThumbnail: Boolean
        get() = store.getBoolean(KEY_POSTER_THUMB, true)
        set(value) = store.putBoolean(KEY_POSTER_THUMB, value)

    var posterShowAudioBadge: Boolean
        get() = store.getBoolean(KEY_POSTER_AUDIO_BADGE, true)
        set(value) = store.putBoolean(KEY_POSTER_AUDIO_BADGE, value)

    var posterShowBranding: Boolean
        get() = store.getBoolean(KEY_POSTER_BRANDING, true)
        set(value) = store.putBoolean(KEY_POSTER_BRANDING, value)

    /**
     * D-503: the Poster Studio's persisted layout (see PosterLayoutConfig in
     * :app) — one JSON blob for the five customizable banner elements
     * (position/scale/color/visibility). Empty string = the factory flow
     * layout. Parsed leniently by the composer: a garbled save degrades to
     * the DEFAULT config, never to a broken banner.
     */
    var posterLayoutJson: String
        get() = store.getString(KEY_POSTER_LAYOUT_JSON, "")
        set(value) = store.putString(KEY_POSTER_LAYOUT_JSON, value)

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

private companion object {
        private const val KEY_POSTER_ENABLED = "notif_poster_enabled"
        private const val KEY_POSTER_BACKGROUND = "notif_poster_background"
        private const val KEY_POSTER_EP_TITLE = "notif_poster_ep_title"
        private const val KEY_POSTER_THUMB = "notif_poster_thumb"
        private const val KEY_POSTER_AUDIO_BADGE = "notif_poster_audio_badge"
        private const val KEY_POSTER_BRANDING = "notif_poster_branding"
        private const val KEY_POSTER_LAYOUT_JSON = "notif_poster_layout_json"
        private const val KEY_ENABLED = "notif_master_enabled"
        private const val KEY_DEF_SCHEDULE = "notif_def_schedule"
        private const val KEY_DEF_WATCHABLE = "notif_def_watchable"
        private const val KEY_DEF_IMMEDIATE = "notif_def_immediate"
        private const val KEY_DEF_SUB = "notif_def_sub"
        private const val KEY_DEF_DUB = "notif_def_dub"
        private const val KEY_LIBRARY_CUSTOM = "notif_library_custom_enabled"
    }
}
