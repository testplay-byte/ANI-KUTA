package com.confused.anikuta.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.notifications.TriggerState
import com.confused.anikuta.core.preferences.NotificationPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for the Notifications settings screen (Phase NOTIF — UI).
 *
 * D-193 v2: Simplified per user feedback:
 * - Removed on_immediate trigger (redundant with on_schedule)
 * - Removed AudioPref (redundant with the Updates section's sub/dub toggle)
 * - Triggers are 2-way (On/Off) — Off = silent (background checks, no notification)
 */
class NotificationsSettingsViewModel(
    private val preferences: NotificationPreferences,
) : ViewModel() {

    /** Global master kill switch. */
    val masterEnabled: StateFlow<Boolean> =
        preferences.notificationsEnabledFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** Default trigger prefs (2-way: On/Off). Applied when enabling a new anime. */
    data class Defaults(
        val onSchedule: TriggerState,
        val onWatchable: TriggerState,
    )

    val defaults: StateFlow<Defaults> = combine(
        preferences.defaultNotifyOnScheduleFlow(),
        preferences.defaultNotifyOnWatchableFlow(),
    ) { schedule, watchable ->
        Defaults(schedule, watchable)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        Defaults(
            onSchedule = TriggerState.OFF,
            onWatchable = TriggerState.ON,
        ),
    )

    fun setMasterEnabled(enabled: Boolean) {
        preferences.notificationsEnabled = enabled
    }

    fun setDefaultOnSchedule(v: TriggerState) { preferences.defaultNotifyOnSchedule = v }
    fun setDefaultOnWatchable(v: TriggerState) { preferences.defaultNotifyOnWatchable = v }

    /** D-193 v2: library customization toggle. */
    val libraryCustomizationEnabled: StateFlow<Boolean> =
        preferences.libraryCustomizationEnabledFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setLibraryCustomizationEnabled(enabled: Boolean) {
        preferences.libraryCustomizationEnabled = enabled
    }
}
