package com.confused.anikuta.core.backup

import com.confused.anikuta.core.preferences.BooleanSerializer
import com.confused.anikuta.core.preferences.preference
import com.confused.anikuta.core.preferences.IntSerializer
import com.confused.anikuta.core.preferences.PreferenceStore

/**
 * The Backup & restore settings (round 134, D-817; round 135 granular rework).
 * Backed by the central PreferenceStore so the keys themselves ride in every
 * backup's `anikuta_prefs`.
 *
 * The granular what-to-include switches (the round-135 order: "divided by
 * Library / Images / Settings / Extensions") persist here — BOTH the manual
 * backup and the scheduled worker read [options] so an automatic backup
 * carries exactly what the user selected.
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

    /** How many backups to keep in the backup folder (the oldest dies on overflow). */
    val keepCount = store.preference(
        "pref_backup_keep_count", 3, IntSerializer,
    )


    // ── LIBRARY ──
    /** Library entries, categories & entry info. */
    val includeLibraryCore = store.preference(
        "pref_backup_library_core", true, BooleanSerializer,
    )
    /** Watch history, progress & ratings. */
    val includeLibraryHistory = store.preference(
        "pref_backup_library_history", true, BooleanSerializer,
    )
    /** Tracking (DB side). */
    val includeLibraryTracking = store.preference(
        "pref_backup_library_tracking", true, BooleanSerializer,
    )
    /** Downloaded episodes, schedules & update state. */
    val includeLibraryDownloads = store.preference(
        "pref_backup_library_downloads", true, BooleanSerializer,
    )

    // ── IMAGES ──
    /** The downloaded series' cover FILES (binary payload) — off by default. */
    val includeImageCovers = store.preference(
        "pref_backup_include_images", false, BooleanSerializer,
    )
    /** Search & browse/home page cover images (the browse text cache). */
    val includeImageSearchBrowse = store.preference(
        "pref_backup_image_browse", true, BooleanSerializer,
    )
    /** Episode thumbnail images (the episode-details text cache). */
    val includeImageEpisodeThumbnails = store.preference(
        "pref_backup_image_thumbnails", true, BooleanSerializer,
    )

    // ── SETTINGS ──
    /** App settings (theme, player, downloads, custom UI design…). */
    val includeSettingsApp = store.preference(
        "pref_backup_settings_app", true, BooleanSerializer,
    )
    /** Extension repo + per-source settings. */
    val includeSettingsSources = store.preference(
        "pref_backup_settings_sources", true, BooleanSerializer,
    )
    /** Sensitive security settings — the tracking login tokens. */
    val includeSettingsSecurity = store.preference(
        "pref_backup_settings_security", true, BooleanSerializer,
    )

    // ── EXTENSIONS ──
    /** The configured extension repositories. */
    val includeExtensionRepos = store.preference(
        "pref_backup_ext_repos", true, BooleanSerializer,
    )
    /** The installed Aniyomi extension APKs. */
    val includeExtensionAniyomi = store.preference(
        "pref_backup_ext_aniyomi", true, BooleanSerializer,
    )
    /** The installed CloudStream .cs3 plugins. */
    val includeExtensionCloudstream = store.preference(
        "pref_backup_ext_cloudstream", true, BooleanSerializer,
    )
    /**
     * ROUND 148: carry ONLY the extensions the current library still uses —
     * off by default (the whole extension setup rides).
     */
    val includeExtensionsLinkedOnly = store.preference(
        "pref_backup_ext_linked_only", false, BooleanSerializer,
    )

    /**
     * The granular selection snapshot — what BOTH the manual and the
     * scheduled backup carry.
     */
    fun options(): BackupOptions = BackupOptions(
        libraryCore = includeLibraryCore.get(),
        libraryHistory = includeLibraryHistory.get(),
        libraryTracking = includeLibraryTracking.get(),
        libraryDownloads = includeLibraryDownloads.get(),
        imageCovers = includeImageCovers.get(),
        imageSearchBrowse = includeImageSearchBrowse.get(),
        imageEpisodeThumbnails = includeImageEpisodeThumbnails.get(),
        settingsApp = includeSettingsApp.get(),
        settingsSources = includeSettingsSources.get(),
        settingsSecurity = includeSettingsSecurity.get(),
        extensionRepos = includeExtensionRepos.get(),
        extensionAniyomi = includeExtensionAniyomi.get(),
        extensionCloudstream = includeExtensionCloudstream.get(),
        extensionsLinkedOnly = includeExtensionsLinkedOnly.get(),
    )
}
