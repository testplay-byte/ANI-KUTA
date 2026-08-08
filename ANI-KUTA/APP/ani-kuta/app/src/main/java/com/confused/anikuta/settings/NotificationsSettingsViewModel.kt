package com.confused.anikuta.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.notifications.AudioPref
import com.confused.anikuta.core.notifications.TriggerState
import com.confused.anikuta.core.preferences.NotificationPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for the Notifications settings screen (Phase NOTIF — UI).
 *
 * Two concerns (the per-anime library config moved to a dedicated page +
 * [NotificationsLibraryViewModel]):
 * 1. **Global master toggle** — the kill switch.
 * 2. **New-anime defaults** — tri-state triggers ([TriggerState]) + tri-state
 *    audio pref ([AudioPref]), seeded into per-anime config on enable.
 */
class NotificationsSettingsViewModel(
    private val preferences: NotificationPreferences,
) : ViewModel() {

    /** Global master kill switch. */
    val masterEnabled: StateFlow<Boolean> =
        preferences.notificationsEnabledFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** Default trigger + audio prefs (applied when enabling a new anime). */
    data class Defaults(
        val onSchedule: TriggerState,
        val onWatchable: TriggerState,
        val onImmediate: TriggerState,
        val audioPref: AudioPref,
    )

    val defaults: StateFlow<Defaults> = combine(
        preferences.defaultNotifyOnScheduleFlow(),
        preferences.defaultNotifyOnWatchableFlow(),
        preferences.defaultNotifyOnImmediateFlow(),
        preferences.defaultAudioPrefFlow(),
    ) { schedule, watchable, immediate, audio ->
        Defaults(schedule, watchable, immediate, audio)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        Defaults(
            onSchedule = TriggerState.OFF,
            onWatchable = TriggerState.ON,
            onImmediate = TriggerState.OFF,
            audioPref = AudioPref.SUB,
        ),
    )

    // ── Global master toggle ───────────────────────────────────────────────

    fun setMasterEnabled(enabled: Boolean) {
        preferences.notificationsEnabled = enabled
    }

    // ── Default trigger states (tri-state) ──────────────────────────────────

    fun setDefaultOnSchedule(v: TriggerState) { preferences.defaultNotifyOnSchedule = v }
    fun setDefaultOnWatchable(v: TriggerState) { preferences.defaultNotifyOnWatchable = v }
    fun setDefaultOnImmediate(v: TriggerState) { preferences.defaultNotifyOnImmediate = v }

    // ── Default audio pref (tri-state) ───────────────────────────────────────

    fun setDefaultAudioPref(v: AudioPref) { preferences.defaultAudioPref = v }
}
