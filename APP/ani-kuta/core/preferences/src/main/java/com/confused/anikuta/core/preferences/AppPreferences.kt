package com.confused.anikuta.core.preferences

/**
 * App-level preferences (not player-specific, not tracker-specific).
 *
 * CORE_RULES §23: Changes to preferences should propagate live (Flow-based).
 * For now, this is a simple wrapper. Phase 4 will add Flow support when UI needs it.
 */
class AppPreferences(private val store: PreferenceStore) {

    var contentMode: String
        get() = store.getString(KEY_CONTENT_MODE, "anime")
        set(value) = store.putString(KEY_CONTENT_MODE, value)

    var activityTrackingRetentionDays: Int
        get() = store.getInt(KEY_TRACKING_RETENTION, 365) // 0 = unlimited
        set(value) = store.putInt(KEY_TRACKING_RETENTION, value)

    var animationsEnabled: Boolean
        get() = store.getBoolean(KEY_ANIMATIONS_ENABLED, true)
        set(value) = store.putBoolean(KEY_ANIMATIONS_ENABLED, value)

    var loggingEnabled: Boolean
        get() = store.getBoolean(KEY_LOGGING_ENABLED, true)
        set(value) = store.putBoolean(KEY_LOGGING_ENABLED, value)

    companion object {
        private const val KEY_CONTENT_MODE = "content_mode"
        private const val KEY_TRACKING_RETENTION = "tracking_retention_days"
        private const val KEY_ANIMATIONS_ENABLED = "animations_enabled"
        private const val KEY_LOGGING_ENABLED = "logging_enabled"
    }
}
