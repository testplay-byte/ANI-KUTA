package com.confused.anikuta.core.backup

import kotlinx.serialization.Serializable

/**
 * The ANI-KUTA backup format (round 134, D-816; round 135 granular rework).
 *
 * A backup is a ZIP file (extension `.anikuta`) containing:
 * ```
 * manifest.json               — [BackupManifest]: version, contents, counts
 * db/<table>.json             — one JSON array of row objects per DB table
 * prefs/<prefs-file>.json     — one typed JSON object per SharedPreferences file
 * images/<mainId>.jpg         — OPTIONAL: the downloaded series' cover files
 * ext/state.json              — OPTIONAL (round 135): the extension inventory + trust state
 * ext/apk/<pkg>.apk           — OPTIONAL (round 135): the installed Aniyomi extension APKs
 * ext/cs/<name>.cs3           — OPTIONAL (round 135): the installed CloudStream plugin files
 * ```
 *
 * The format is schema-forward-friendly: the DB tables are dumped/restored
 * GENERICALLY (column names/types read from PRAGMA at runtime), so a newer
 * schema's extra columns restore fine on an older app (manifest.formatVersion
 * gates behavioral changes only).
 */
object BackupFormat {
    const val FORMAT_ID = "anikuta-backup"
    const val FORMAT_VERSION = 2
    const val FILE_EXTENSION = ".anikuta"
    const val FILE_PREFIX = "anikuta-backup-"
    const val BACKUP_DIR_NAME = "backup"
    const val MANIFEST_ENTRY = "manifest.json"
    const val DB_DIR = "db"
    const val PREFS_DIR = "prefs"
    const val IMAGES_DIR = "images"
    const val EXT_DIR = "ext"
    const val EXT_STATE_ENTRY = "ext/state.json"
    const val EXT_APK_DIR = "ext/apk"
    const val EXT_CS_DIR = "ext/cs"
    /** ROUND 136: the URL-keyed offline images (SHA-256-named .img files). */
    const val OFFLINE_IMAGES_DIR = "offline_images"
    /** ROUND 137: the profile avatar file (restored to a fresh device-local path). */
    const val PROFILE_AVATAR_ENTRY = "profile/avatar.jpg"
}

/**
 * The DB tables a backup CAN carry, in RESTORE ORDER (parents before children —
 * `PRAGMA foreign_keys = ON` is enforced; main_entry is the FK parent of
 * nearly everything).
 *
 * Deliberately EXCLUDED (round 134 decision, unchanged):
 *  - `download_queue` (transient active-task state; stale rows would
 *    resurrect dead downloads),
 *  - `notification_sent` (the dedup log — the file header marks it
 *    non-backup-eligible),
 *  - `activity_event` (internal analytics; pruned after a year),
 *  - `playback_cache_entry` (a disposable byte-cache index).
 *
 * ROUND 137 additions (the full-capability audit): `watch_session` (the
 * per-day watch-flow ledger — real minutes per day ride the backup now) and
 * `episode_update` (the "New episodes" feed + its SUB/DUB evidence — the
 * audit found it was silently dropped with no documented exclusion).
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
    "watch_session",
    "user_rating",
    "user_episode_rating",
    "track_entry",
    "content_tracking_state",
    "app_settings",
    "downloaded_episode",
    "notification_config",
    "anime_update_state",
    "episode_schedule",
    "episode_update",
    // Text caches last (episode details/synopses + the browse/search page
    // payloads).
    "data_cache_episode",
    "browse_cache",
)

/**
 * The SharedPreferences files a backup CAN carry (round 134, D-817). The
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
    // ROUND 147 (M-6, the device report): the AD-SYSTEM state never rides a
    // restore — the user cleared their data (the 3-day sponsor grace ACTIVE),
    // restored their own backup, and the donor's ads_always_sponsor=true
    // silently re-enabled the debug sponsor popup (bypassing the grace). The
    // recipient keeps THEIR OWN ad state: the grace survives, the timestamps
    // stay local, the debug toggle stays whatever the recipient set.
    "ads_always_sponsor",
    "ads_first_open_timestamp",
    "ads_last_shown_timestamp",
    "ads_first_open_grace_consumed",
    // A tree URI from another device/app state is unusable and would make
    // downloads + backups silently no-op until re-picked (the round-134
    // verifier): the user re-selects the folder on restore.
    "pref_dl_folder_uri",
)

/**
 * The TRACKING LOGIN keys — the "sensitive security settings" bucket (the
 * round-135 order). Carried ONLY when [BackupOptions.settingsSecurity] is on.
 * Prefix match: every AniList OAuth/identity key rides in the same bucket.
 */
val SECURITY_KEY_PREFIXES: Set<String> = setOf(
    "anilist_access_token",
    "anilist_refresh_token",
    "anilist_token_expires",
)

/** The anikuta_prefs keys that belong to the EXTENSIONS bucket, not app settings. */
val EXTENSION_STATE_PREF_KEYS: Set<String> = setOf(
    "trusted_extension_packages",
    "enabled_extensions",
)

/**
 * The GRANULAR what-to-include selection (the round-135 order: "divided by
 * Library / Images / Settings / Extensions" with the items under each).
 * Each flag gates a specific payload slice; the mapping lives in
 * [BackupOptions.tableFor], [BackupOptions.ownsPrefsFile] and the dump
 * pipeline's key filters.
 */
data class BackupOptions(
    // ── LIBRARY ──
    /** Library entries, categories & entry info (series, seasons, custom info). */
    val libraryCore: Boolean = true,
    /** Watch history, progress & ratings. */
    val libraryHistory: Boolean = true,
    /** Tracking (the DB side; the login TOKEN rides with Settings→Security). */
    val libraryTracking: Boolean = true,
    /** Downloaded episodes, schedules & update state. */
    val libraryDownloads: Boolean = true,

    // ── IMAGES ──
    /** The downloaded series' cover FILES (binary payload). */
    val imageCovers: Boolean = false,
    /** Search & browse/home page cover images (the browse text cache). */
    val imageSearchBrowse: Boolean = true,
    /** Episode thumbnail images (the episode-details text cache). */
    val imageEpisodeThumbnails: Boolean = true,

    // ── SETTINGS ──
    /** App settings (theme, player, downloads, custom button/UI design…). */
    val settingsApp: Boolean = true,
    /** Extension repo + per-source settings (repo lists, source preferences). */
    val settingsSources: Boolean = true,
    /** Sensitive security settings — the tracking login tokens (no re-login). */
    val settingsSecurity: Boolean = true,

    // ── EXTENSIONS ──
    /** The configured extension repositories (Aniyomi + CloudStream repo lists). */
    val extensionRepos: Boolean = true,
    /** The installed Aniyomi extension APKs — auto-installed + trusted on restore. */
    val extensionAniyomi: Boolean = true,
    /** The installed CloudStream .cs3 plugins — restored trusted. */
    val extensionCloudstream: Boolean = true,
) {

    /** TRUE when at least one slice is on (an all-off backup would be pointless). */
    val anythingEnabled: Boolean
        get() = libraryCore || libraryHistory || libraryTracking || libraryDownloads ||
            // ROUND 136 (the verifier's F9): imageCovers IS a payload now —
            // the round-136 headline switch — a covers-only backup is legal.
            imageCovers || imageSearchBrowse || imageEpisodeThumbnails ||
            settingsApp || settingsSources || settingsSecurity ||
            extensionRepos || extensionAniyomi || extensionCloudstream

    /** The DB table a Library/Images switch owns (null = not a table gate). */
    fun tableFor(table: String): Boolean = when (table) {
        "data_source", "system", "genre", "main_entry", "content_details",
        "content_genre", "library_category", "library_item",
        -> libraryCore
        // ROUND 137: the per-day session ledger rides the history bucket.
        "watch_progress", "watch_session", "user_rating", "user_episode_rating" -> libraryHistory
        "track_entry", "content_tracking_state" -> libraryTracking
        "downloaded_episode", "notification_config", "anime_update_state",
        "episode_schedule",
        // ROUND 137: the New-episodes feed is user-visible state, not a cache.
        "episode_update",
        -> libraryDownloads
        "data_cache_episode" -> imageEpisodeThumbnails
        "browse_cache" -> imageSearchBrowse
        "app_settings" -> settingsApp
        else -> false
    }

    /** The tables this selection dumps, in restore order. */
    val tables: List<String>
        get() = BACKUP_TABLES.filter { tableFor(it) }

    /**
     * TRUE when the prefs FILE is carried — ANY owning switch includes it
     * (a file can serve two buckets: `anikuta_prefs` is app settings AND,
     * for the trust/enable keys, extensions).
     */
    fun ownsPrefsFile(name: String): Boolean = when (name) {
        "anikuta_prefs" -> settingsApp || settingsSecurity || extensionAniyomi
        "anikuta_extension_repos", "anikuta_cloudstream_repos" ->
            settingsSources || extensionRepos
        "anikuta_cloudstream_plugins" -> extensionCloudstream
        "cs_source_memory" -> settingsSources || extensionCloudstream
        "rebuild_preference" -> settingsApp
        else -> name.startsWith("source_") && settingsSources
    }

    /**
     * The per-KEY filter applied while dumping a prefs file — a key is
     * carried when at least one bucket that owns it is on. FALSE = skip.
     *
     * `plugins_json` (the CloudStream plugin records) is NEVER carried raw:
     * the records ride in `ext/state.json` and are re-registered through the
     * bridge with DEVICE-LOCAL paths on restore — carrying the raw JSON would
     * overwrite the restored records with the donor device's absolute
     * filePaths, and the plugin loader would then delete every file-less
     * record on the next load (the round-135 verifier's P0).
     */
    fun includePrefsKey(file: String, key: String): Boolean {
        if (file == "anikuta_cloudstream_plugins") return key != "plugins_json"
        if (file != "anikuta_prefs") return true
        return when {
            key in PREFS_RESTORE_EXCLUDED_KEYS -> false
            SECURITY_KEY_PREFIXES.any { key.startsWith(it) } -> settingsSecurity
            key in EXTENSION_STATE_PREF_KEYS -> extensionAniyomi
            else -> settingsApp
        }
    }

    companion object {
        /** Everything on (the onboarding restore + the automatic backups). */
        val EVERYTHING = BackupOptions(
            libraryCore = true,
            libraryHistory = true,
            libraryTracking = true,
            libraryDownloads = true,
            imageCovers = true,
            imageSearchBrowse = true,
            imageEpisodeThumbnails = true,
            settingsApp = true,
            settingsSources = true,
            settingsSecurity = true,
            extensionRepos = true,
            extensionAniyomi = true,
            extensionCloudstream = true,
        )
    }
}

@Serializable
data class BackupManifest(
    val format: String = BackupFormat.FORMAT_ID,
    val formatVersion: Int = BackupFormat.FORMAT_VERSION,
    val appVersion: String,
    val createdAt: Long,
    /** The included optional payload (covers). */
    val includesImages: Boolean = false,
    /** ROUND 137: the profile avatar file is carried (restored to a local path). */
    val includesAvatar: Boolean = false,
    /** The DB tables carried (in restore order). */
    val tables: List<String>,
    /** The SharedPreferences files carried. */
    val prefsFiles: List<String>,
    /** Row counts per table (informational). */
    val rowCount: Map<String, Int> = emptyMap(),
    /** The number of cover images carried. */
    val imageCount: Int = 0,
    /** ROUND 136: the offline-image payload (URL-keyed library covers/banners + extension icons). */
    val offlineImageCount: Int = 0,
    /** ROUND 135: the extension payload is carried (state.json present). */
    val includesExtensions: Boolean = false,
    /** ROUND 135: how many Aniyomi extension APKs are carried. */
    val aniyomiApkCount: Int = 0,
    /** ROUND 135: how many CloudStream .cs3 plugins are carried. */
    val csPluginCount: Int = 0,
)

/** One entry in the backup folder's listing. */
data class BackupFileInfo(
    val name: String,
    val uri: String,
    val createdAt: Long,
    val sizeBytes: Long,
)

/**
 * ROUND 139 (D-840): one step of the backup's LIVE progress feed — [stage] is
 * the friendly label of what is being backed up right now ("Watch history",
 * "Offline covers", "Extensions"…); [current]/[total] carry per-item counts
 * when the stage has them, 0/0 = an indeterminate stage.
 */
data class BackupProgress(
    val stage: String,
    val current: Int,
    val total: Int,
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
    /** ROUND 136: offline images (URL-keyed covers/banners/icons) written back. */
    val offlineImagesRestored: Int = 0,
    /** ROUND 135: Aniyomi APKs that finished installing (user confirmed each). */
    val aniyomiInstalled: Int = 0,
    /** ROUND 135: Aniyomi APKs that failed or were cancelled (the names). */
    val aniyomiFailed: List<String> = emptyList(),
    /** ROUND 135: Aniyomi APKs already on the device (skipped, trust re-applied). */
    val aniyomiAlreadyInstalled: Int = 0,
    /** ROUND 135: CloudStream .cs3 plugins written back + re-registered. */
    val csPluginsRestored: Int = 0,
    /** TRUE when the app should be restarted to pick up restored settings. */
    val restartRecommended: Boolean = true,
)

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 135: the extension payload (state.json + the .apk/.cs3 entries).
//  The models live HERE (core:backup) so the engine never depends on the
//  data:extension / data:cloudstream modules; the app-layer bridge impl
//  (ExtensionBackupBridge) translates to/from the live extension system.
// ════════════════════════════════════════════════════════════════════════════

/** One installed Aniyomi extension carried in the payload. */
@Serializable
data class AniyomiApkInfo(
    val pkgName: String,
    val name: String,
    val versionName: String,
    val versionCode: Long,
    /** Whether it was trusted (=> re-trusted after the restore install). */
    val trusted: Boolean,
    /** Whether its sources were enabled. */
    val enabled: Boolean,
    /** ROUND 136: the icon URL (the offline-image store carries its bytes). */
    val iconUrl: String = "",
)

/**
 * One CloudStream plugin carried in the payload — every [CsPluginRecord]-
 * shaped field EXCEPT the absolute filePath (device-specific; re-derived on
 * restore from the repo salt).
 */
@Serializable
data class CloudstreamPluginInfo(
    val internalName: String,
    val name: String,
    val url: String? = null,
    val version: Int = 0,
    val repoUrl: String? = null,
    val repoInternalName: String? = null,
    val fileHash: String? = null,
    val language: String? = null,
    val iconUrl: String? = null,
    val isNsfw: Boolean = false,
    val authors: List<String> = emptyList(),
    val description: String? = null,
    val tvTypes: List<String> = emptyList(),
    val fileSizeBytes: Long? = null,
    /** Gates code execution — restored exactly as backed up. */
    val isTrusted: Boolean = true,
)

/** The `ext/state.json` document (the extension payload's inventory + trust). */
@Serializable
data class ExtensionBackupState(
    val aniyomi: List<AniyomiApkInfo> = emptyList(),
    val cloudstream: List<CloudstreamPluginInfo> = emptyList(),
)
