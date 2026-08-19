package com.confused.anikuta.core.preferences

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.database.AnikutaDatabase

/**
 * Repository for the `app_settings` table (D-192).
 *
 * A single key-value table for ALL app settings, designed for backup/restore.
 * The primary quick-access layer is [PreferenceStore] (SharedPreferences) —
 * this table is a persistent mirror for backup/restore + cross-device sync.
 *
 * ## Design
 * - `key`: unique setting key (e.g. "download_quality", "theme_accent")
 * - `value`: the setting value as a string (serialized)
 * - `type`: the value type for deserialization (bool/int/long/float/string/set)
 * - `category`: grouping for backup/restore UI (download/player/appearance/notifications/general)
 *
 * ## Usage
 * - **Quick access** (runtime): use `PreferenceStore` (reactive, fast, in-memory cached).
 * - **Backup/restore**: use this repository to export/import all settings.
 * - **Mirror writes**: when a setting changes via PreferenceStore, optionally mirror it here
 *   via `upsertSetting(...)`. This is a follow-up task — not all PreferenceStore keys are
 *   mirrored yet.
 *
 * ## Future-proofing
 * Adding a new setting = one `upsertSetting(key, value, type, category)` call.
 * No schema changes needed. The table is schema-less (key-value).
 *
 * CORE_RULES §20: Logged with tag "Anikuta:Core:Preferences:Settings".
 * CORE_RULES §7: Backend logic — no UI.
 */
class SettingsRepository(
    private val database: AnikutaDatabase,
) {

    companion object {
        private const val TAG = "Anikuta:Core:Preferences:Settings"
    }

    private val queries get() = database.appSettingsQueries

    // ── Single setting CRUD ────────────────────────────────────────────────

    fun getSetting(key: String): String? {
        return queries.getSetting(key).executeAsOneOrNull()?.setting_value
    }

    fun upsertSetting(key: String, value: String, type: String = "string", category: String = "general") {
        queries.upsertSetting(key, value, type, category, System.currentTimeMillis())
        Logger.d(TAG) { "Setting upserted: $key=$value (type=$type, category=$category)" }
    }

    fun deleteSetting(key: String) {
        queries.deleteSetting(key)
        Logger.d(TAG) { "Setting deleted: $key" }
    }

    // ── Bulk operations (backup/restore) ───────────────────────────────────

    fun getAllSettings(): List<SettingEntry> {
        return queries.getAllSettings().executeAsList().map {
            SettingEntry(
                key = it.setting_key,
                value = it.setting_value,
                type = it.setting_type,
                category = it.setting_category,
                updatedAt = it.updated_at,
            )
        }
    }

    fun getSettingsByCategory(category: String): List<SettingEntry> {
        return queries.getSettingsByCategory(category).executeAsList().map {
            SettingEntry(
                key = it.setting_key,
                value = it.setting_value,
                type = it.setting_type,
                category = it.setting_category,
                updatedAt = it.updated_at,
            )
        }
    }

    fun deleteAllSettings() {
        queries.deleteAllSettings()
        Logger.w(TAG) { "All settings deleted" }
    }

    /**
     * Export all settings as a list of [SettingEntry] — for JSON serialization
     * in the backup/restore flow.
     */
    fun exportAll(): List<SettingEntry> = getAllSettings()

    /**
     * Import settings from a list (backup restore). Replaces all existing settings.
     */
    fun importAll(settings: List<SettingEntry>) {
        database.transaction {
            queries.deleteAllSettings()
            for (s in settings) {
                queries.upsertSetting(s.key, s.value, s.type, s.category, s.updatedAt)
            }
        }
        Logger.i(TAG) { "Imported ${settings.size} settings" }
    }
}

/**
 * A single app setting entry (for backup/restore serialization).
 */
data class SettingEntry(
    val key: String,
    val value: String,
    val type: String,
    val category: String,
    val updatedAt: Long,
)

/**
 * Setting categories for grouping in the backup/restore UI.
 */
object SettingCategory {
    const val DOWNLOAD = "download"
    const val PLAYER = "player"
    const val APPEARANCE = "appearance"
    const val NOTIFICATIONS = "notifications"
    const val AUTO_LINK = "auto_link"
    const val DEBUG = "debug"
    const val GENERAL = "general"
}

/**
 * Setting value types for deserialization.
 */
object SettingType {
    const val BOOL = "bool"
    const val INT = "int"
    const val LONG = "long"
    const val FLOAT = "float"
    const val STRING = "string"
    const val SET = "set"
}
