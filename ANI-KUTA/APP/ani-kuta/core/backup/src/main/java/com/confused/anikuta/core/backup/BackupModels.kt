package com.confused.anikuta.core.backup

import kotlinx.serialization.Serializable

/**
 * The ANI-KUTA backup format (round 134, D-816).
 *
 * A backup is a ZIP file (extension `.anikuta`) containing:
 * ```
 * manifest.json               — [BackupManifest]: version, contents, counts
 * db/<table>.json             — one JSON array of row objects per DB table
 * prefs/<prefs-file>.json     — one typed JSON object per SharedPreferences file
 * images/<mainId>.jpg         — OPTIONAL: the downloaded series' .cover.jpg files
 * ```
 *
 * The format is schema-forward-friendly: the DB tables are dumped/restored
 * GENERICALLY (column names/types read from PRAGMA at runtime), so a newer
 * schema's extra columns restore fine on an older app (manifest.formatVersion
 * gates behavioral changes only).
 */
object BackupFormat {
    const val FORMAT_ID = "anikuta-backup"
    const val FORMAT_VERSION = 1
    const val FILE_EXTENSION = ".anikuta"
    const val FILE_PREFIX = "anikuta-backup-"
    const val BACKUP_DIR_NAME = "backup"
    const val MANIFEST_ENTRY = "manifest.json"
    const val DB_DIR = "db"
    const val PREFS_DIR = "prefs"
    const val IMAGES_DIR = "images"
}

/**
 * The DB tables a backup carries, in RESTORE ORDER (parents before children —
 * `PRAGMA foreign_keys = ON` is enforced; main_entry is the FK parent of
 * nearly everything).
 *
 * Deliberately EXCLUDED:
 *  - `download_queue` (transient active-task state; stale rows would
 *    resurrect dead downloads),
 *  - `notification_sent` (the dedup log — the file header marks it
 *    non-backup-eligible),
 *  - `activity_event` (internal analytics; pruned after a year),
 *  - `playback_cache_entry` / `browse_cache`-style caches are PARTIALLY in —
 *    browse_cache rides along (it feeds the browse page's thumbnails the user
 *    asked for) while playback_cache_entry (a disposable byte-cache index)
 *    stays out.
 */
val BACKUP_TABLES: List<String> = listOf(
    // Parents first.
    "data_source",
    "system",
    "genre",
    "main_entry",
    "content_details",
    "content_genre",
    "library_category",
    "library_item",
    "watch_progress",
    "user_rating",
    "user_episode_rating",
    "track_entry",
    "content_tracking_state",
    "app_settings",
    "downloaded_episode",
    "notification_config",
    "anime_update_state",
    "episode_schedule",
    // Text caches last (episode details/synopses + the browse/search page
    // payloads — the round-134 order: details are ALWAYS backed up, no
    // configuration needed).
    "data_cache_episode",
    "browse_cache",
)

/**
 * The SharedPreferences files a backup carries (round 134, D-817). The
 * central `anikuta_prefs` carries every user setting INCLUDING the AniList
 * OAuth token (`anilist_access_token`); the repo/plugin files carry the
 * extension + content-provider setup that has no DB representation.
 *
 * `source_<id>` per-extension-source files are discovered dynamically at
 * backup time. Excluded (transient/debug): `anikuta_pending_nav`,
 * `extension_test_results`.
 */
val BACKUP_PREFS_FILES: List<String> = listOf(
    "anikuta_prefs",
    "anikuta_extension_repos",
    "anikuta_cloudstream_repos",
    "anikuta_cloudstream_plugins",
    "cs_source_memory",
    "rebuild_preference",
)

/** Never restored: the app self-update bookkeeping names invalid paths. */
val PREFS_RESTORE_EXCLUDED_KEYS: Set<String> = setOf(
    "pref_app_update_downloaded_apks",
    "pref_app_update_pending_post_install",
    // A tree URI from another device/app state is unusable and would make
    // downloads + backups silently no-op until re-picked (the round-134
    // verifier): the user re-selects the folder on restore.
    "pref_dl_folder_uri",
)

/** What the user can toggle (the Backup & restore settings page). */
data class BackupOptions(
    /** Copy the downloaded series' cover files into the backup. */
    val includeImages: Boolean = false,
)

@Serializable
data class BackupManifest(
    val format: String = BackupFormat.FORMAT_ID,
    val formatVersion: Int = BackupFormat.FORMAT_VERSION,
    val appVersion: String,
    val createdAt: Long,
    /** The included optional payload (covers). */
    val includesImages: Boolean = false,
    /** The DB tables carried (in restore order). */
    val tables: List<String>,
    /** The SharedPreferences files carried. */
    val prefsFiles: List<String>,
    /** Row counts per table (informational). */
    val rowCount: Map<String, Int> = emptyMap(),
    /** The number of cover images carried. */
    val imageCount: Int = 0,
)

/** One entry in the backup folder's listing. */
data class BackupFileInfo(
    val name: String,
    val uri: String,
    val createdAt: Long,
    val sizeBytes: Long,
)

/** The restore result summary (the UI's completion report). */
data class RestoreSummary(
    val manifest: BackupManifest,
    /** Rows restored per table. */
    val tableRowCount: Map<String, Int>,
    /** The prefs files applied. */
    val prefsFilesApplied: List<String>,
    /** Cover images copied back into the SAF tree. */
    val imagesRestored: Int,
    /** TRUE when the app should be restarted to pick up restored settings. */
    val restartRecommended: Boolean = true,
)
