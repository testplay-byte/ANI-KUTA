package com.confused.anikuta.core.preferences

import com.confused.anikuta.core.common.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User preferences for the Updates system (D-193 Phase 3).
 *
 * Stored via [PreferenceStore] (SharedPreferences) for quick access.
 * Mirrored to `app_settings` table for backup/restore (future).
 *
 * ## Settings
 * - `update_mode`: "auto" | "manual" | "off" (3-way master toggle)
 * - `update_interval_hours`: 6 | 12 | 24 | 48 | 72 | 168
 * - `update_check_sub`: Boolean (default true)
 * - `update_check_dub`: Boolean (default true — per user decision)
 * - `update_check_dub_completed`: Boolean (default true — check dub on completed anime)
 * - `update_selected_categories`: StringSet (for manual mode)
 *
 * CORE_RULES §20: logged with tag "Anikuta:Core:Preferences:Updates".
 * CORE_RULES §23: reactive via StateFlow.
 */
class UpdatePreferences(
    private val store: PreferenceStore,
) {

    companion object {
        private const val TAG = "Anikuta:Core:Preferences:Updates"
        private const val KEY_MODE = "update_mode"
        private const val KEY_INTERVAL = "update_interval_hours"
        private const val KEY_CHECK_SUB = "update_check_sub"
        private const val KEY_CHECK_DUB = "update_check_dub"
        private const val KEY_CHECK_DUB_COMPLETED = "update_check_dub_completed"
        private const val KEY_SELECTED_CATEGORIES = "update_selected_categories"

        const val DEFAULT_INTERVAL_HOURS = 24L
    }

    private val _mode = MutableStateFlow(getMode())
    val mode: StateFlow<UpdateMode> = _mode.asStateFlow()

    private val _intervalHours = MutableStateFlow(getIntervalHours())
    val intervalHours: StateFlow<Long> = _intervalHours.asStateFlow()

    private val _checkSub = MutableStateFlow(getCheckSub())
    val checkSub: StateFlow<Boolean> = _checkSub.asStateFlow()

    private val _checkDub = MutableStateFlow(getCheckDub())
    val checkDub: StateFlow<Boolean> = _checkDub.asStateFlow()

    private val _checkDubCompleted = MutableStateFlow(getCheckDubCompleted())
    val checkDubCompleted: StateFlow<Boolean> = _checkDubCompleted.asStateFlow()

    fun getMode(): UpdateMode {
        val raw = store.getString(KEY_MODE, UpdateMode.AUTO.value)
        return UpdateMode.fromValue(raw)
    }

    fun setMode(mode: UpdateMode) {
        store.putString(KEY_MODE, mode.value)
        _mode.value = mode
        Logger.i(TAG) { "Update mode set: ${mode.value}" }
    }

    fun getIntervalHours(): Long {
        return store.getString(KEY_INTERVAL, DEFAULT_INTERVAL_HOURS.toString()).toLongOrNull()
            ?: DEFAULT_INTERVAL_HOURS
    }

    fun setIntervalHours(hours: Long) {
        store.putString(KEY_INTERVAL, hours.toString())
        _intervalHours.value = hours
        Logger.i(TAG) { "Update interval set: ${hours}h" }
    }

    fun getCheckSub(): Boolean = store.getString(KEY_CHECK_SUB, "true") == "true"

    fun setCheckSub(enabled: Boolean) {
        store.putString(KEY_CHECK_SUB, if (enabled) "true" else "false")
        _checkSub.value = enabled
    }

    fun getCheckDub(): Boolean = store.getString(KEY_CHECK_DUB, "true") == "true"

    fun setCheckDub(enabled: Boolean) {
        store.putString(KEY_CHECK_DUB, if (enabled) "true" else "false")
        _checkDub.value = enabled
    }

    fun getCheckDubCompleted(): Boolean = store.getString(KEY_CHECK_DUB_COMPLETED, "true") == "true"

    fun setCheckDubCompleted(enabled: Boolean) {
        store.putString(KEY_CHECK_DUB_COMPLETED, if (enabled) "true" else "false")
        _checkDubCompleted.value = enabled
    }

    fun getSelectedCategories(): Set<String> {
        return store.getStringSet(KEY_SELECTED_CATEGORIES, emptySet())
    }

    fun setSelectedCategories(categories: Set<String>) {
        store.putStringSet(KEY_SELECTED_CATEGORIES, categories)
        Logger.i(TAG) { "Selected categories: ${categories.size}" }
    }
}

/**
 * The 3-way master toggle for updates.
 */
enum class UpdateMode(val value: String, val displayName: String) {
    AUTO("auto", "Auto"),
    MANUAL("manual", "Manual"),
    OFF("off", "Off"),
    ;

    companion object {
        fun fromValue(value: String): UpdateMode {
            return entries.firstOrNull { it.value == value } ?: AUTO
        }
    }
}
