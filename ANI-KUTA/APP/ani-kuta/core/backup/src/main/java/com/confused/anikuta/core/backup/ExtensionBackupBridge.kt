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
     * Writes a .cs3 plugin file to its device-local install path and
     * re-registers its record (with the backed-up trust state). MUST be
     * callable BEFORE the prefs application — the plugin manager deletes
     * file-less records on load, so files first, records second.
     * Returns true on success.
     */
    fun restoreCloudstreamPlugin(info: CloudstreamPluginInfo, bytes: ByteArray): Boolean

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
