package com.confused.anikuta.core.testcontroller

import com.confused.anikuta.core.preferences.SettingsRepository

/**
 * Provides read/write access to [SettingsRepository] (the `app_settings` table — D-201 reuse).
 *
 * The agent can read/write any setting by key. Writes are mirrored to the `app_settings` table
 * (NOT to [com.confused.anikuta.core.preferences.PreferenceStore] — that's a separate layer). For
 * preferences that live only in SharedPreferences (not mirrored to app_settings), this provider
 * won't see them. Sufficient for debug-test use (relay config, test flags, etc.).
 */
class PreferencesProvider(
    private val settings: SettingsRepository,
) {
    fun get(key: String): String? = settings.getSetting(key)
    fun set(key: String, value: String) = settings.upsertSetting(key, value, type = "string", category = "debug")
}
