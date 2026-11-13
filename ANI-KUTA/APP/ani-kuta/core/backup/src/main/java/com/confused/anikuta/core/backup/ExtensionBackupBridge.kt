package com.confused.anikuta.core.backup

import kotlinx.serialization.Serializable

/**
 * The extension-system bridge (round 135). The backup engine (:core:backup)
 * cannot see :data:extension / :data:cloudstream (layering), so the payload
 * work — collecting the installed Aniyomi APKs + CloudStream .cs3 files, and
 * restoring them (auto-install + trust) — is delegated to an implementation
 * registered in the app layer (Koin: `single<ExtensionBackupBridge>`).
 *
 * All methods run on the caller's dispatcher (the engine already sits on
 * Dispatchers.IO); implementations may block freely.
 */
interface ExtensionBackupBridge {

    // ── Collection (backup side) ────────────────────────────────────────────

    /**
     * The installed Aniyomi extensions, with their APK BYTES (read from each
     * package's sourceDir). An extension whose APK cannot be read is dropped
     * (logged) — the backup still carries the rest.
     */
    fun collectAniyomiApks(): List<AniyomiApkEntry>

    /**
     * The installed CloudStream plugins, with their .cs3 BYTES. A plugin whose
     * file is missing is dropped (logged) — its stale record must NOT be
     * carried (the loader deletes file-less records).
     */
    fun collectCloudstreamPlugins(): List<CloudstreamPluginEntry>

    // ── Restore side ────────────────────────────────────────────────────────

    /** TRUE when [pkgName] is already installed on THIS device. */
    fun isPackageInstalled(pkgName: String): Boolean

    /**
     * Installs a local Aniyomi APK — hands it to the system installer and
     * AWAITS the verdict (the user confirms the system dialog). Returns true
     * only on a confirmed install.
     */
    suspend fun installAniyomiApk(pkgName: String, bytes: ByteArray): Boolean

    /**
     * Applies the backed-up trust/enable state to an Aniyomi package — ADDs
     * (never removes) so a restore only widens what exists.
     */
    fun applyAniyomiState(pkgName: String, trusted: Boolean, enabled: Boolean)

    /**
     * ROUND 148 (the DeadSystem crash): ONE extension-manager reload after
     * the WHOLE install loop — the old per-install reloads stacked up to 2N
     * overlapping PackageManager scans while system_server was mid-install
     * (one transient DeadSystemException killed the process). The install
     * receiver still refreshes per-install for liveness; this call lands
     * AFTER the last install, so the final scan reads the fresh trust set.
     */
    fun reloadAniyomiExtensions()

    /**
     * Writes a .cs3 plugin file to its device-local install path and
     * re-registers its record (with the backed-up trust state). The engine
     * calls it AFTER the prefs application (the bridge's upserts are the
     * record authority — a carried donor plugins_json can never clobber the
     * device-local paths); the files land before any loader read, so the
     * manager's file-less-record deletion can never touch them.
     * Returns true on success.
     */
    fun restoreCloudstreamPlugin(info: CloudstreamPluginInfo, bytes: ByteArray): Boolean

    /**
     * Called after the restore's prefs application (round 136): the generic
     * pipeline wrote SharedPreferences BEHIND the extension systems' backs —
     * the repo stores + the trust set hold IN-MEMORY StateFlows built at app
     * start, so without this the Extensions page kept showing the pre-restore
     * (often empty) lists until a full process restart (the device report:
     * "the repositories were not added"). Refreshes them all.
     */
    fun onPrefsRestored()

    /**
     * Called after the .cs3 files + records are written (round 136): reloads
     * the CloudStream plugin manager so the restored plugins appear LIVE
     * (all records have their files on disk at this point — the loader's
     * file-less-record deletion cannot touch them).
     */
    fun onCloudstreamRestored()

    /** One Aniyomi extension + its APK bytes (the backup-side collection). */
    data class AniyomiApkEntry(
        val info: AniyomiApkInfo,
        val bytes: ByteArray,
    )

    /** One CloudStream plugin + its .cs3 bytes (the backup-side collection). */
    data class CloudstreamPluginEntry(
        val info: CloudstreamPluginInfo,
        val bytes: ByteArray,
    )
}
