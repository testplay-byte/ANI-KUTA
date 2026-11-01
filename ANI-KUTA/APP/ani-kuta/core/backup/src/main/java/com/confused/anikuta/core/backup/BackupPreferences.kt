package com.confused.anikuta.core.backup

import com.confused.anikuta.core.preferences.BooleanSerializer
import com.confused.anikuta.core.preferences.preference
import com.confused.anikuta.core.preferences.IntSerializer
import com.confused.anikuta.core.preferences.PreferenceStore

/**
 * The Backup & restore settings (round 134, D-817). Backed by the central
 * PreferenceStore so the keys themselves ride in every backup's
 * `anikuta_prefs`.
 */
class BackupPreferences(private val store: PreferenceStore) {

    /** Whether the scheduled automatic backup runs at all. */
    val autoBackupEnabled = store.preference(
        "pref_backup_auto_enabled", false, BooleanSerializer,
    )

    /**
     * The automatic backup interval in HOURS: 6, 12, 24 (daily) or 168
     * (weekly) — the user's four options.
     */
    val autoBackupIntervalHours = store.preference(
        "pref_backup_interval_hours", 24, IntSerializer,
    )

    /** Whether automatic + manual backups carry the downloaded cover images. */
    val includeImages = store.preference(
        "pref_backup_include_images", false, BooleanSerializer,
    )

    /** How many backups to keep in the backup folder (the oldest dies on overflow). */
    val keepCount = store.preference(
        "pref_backup_keep_count", 3, IntSerializer,
    )
}
