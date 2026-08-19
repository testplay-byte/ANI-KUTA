package com.confused.anikuta.core.appupdate

import com.confused.anikuta.core.preferences.PreferenceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Preferences for the app self-update system.
 *
 * # Stored data
 *
 * - [updateCheckEnabled] — master on/off toggle for automatic checks on app open.
 * - [lastCheckTimestamp] — when the last check ran (for throttling).
 * - [lastDismissedVersion] — the version the user last dismissed (for the 6-hour cooldown).
 * - [lastDismissedTimestamp] — when the user dismissed it (for the 6-hour cooldown).
 * - [downloadedApks] — list of downloaded APK files (for the "downloaded versions" UI).
 *
 * # Downloaded APK lifecycle
 *
 * When a download completes, the APK file path is recorded in [downloadedApks].
 * The user can:
 * - **Install** — opens the system installer via [ApkInstaller].
 * - **Delete** — [deleteDownloadedApk] removes both the file from disk AND the
 *   record from the list. This frees up storage.
 *
 * Old downloaded APKs (for versions older than the currently installed one) are
 * automatically cleaned up by [AppUpdateManager.cleanupOldDownloads] on app
 * startup to prevent storage bloat.
 *
 * @param preferenceStore the backing preference store.
 */
class AppUpdatePreferences(
    private val preferenceStore: PreferenceStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The version name of the APK the user is *about to* install.
     *
     * Set by [setPendingPostInstall] just before the system installer is
     * launched (see [AppUpdateManager.installDownloadedApk]). On the next
     * app startup, [com.confused.anikuta.AnikutaRoot] checks [getPendingPostInstall]: if non-empty,
     * it means the user just installed an update — the post-install success
     * popup is shown + the value is cleared via [clearPendingPostInstall].
     */

    /**
     * Records the version the user is about to install. Called right before
     * the system installer is launched.
     */
    fun setPendingPostInstall(version: String) {
        preferenceStore.putString(KEY_PENDING_POST_INSTALL, version)
    }

    /**
     * Returns the version name of the most recent "about to install" record,
     * or an empty string if none. The caller should clear it via
     * [clearPendingPostInstall] after handling it.
     */
    fun getPendingPostInstall(): String = preferenceStore.getString(KEY_PENDING_POST_INSTALL, "")

    /** Clears the pending-post-install marker (after the popup has been shown). */
    fun clearPendingPostInstall() {
        preferenceStore.putString(KEY_PENDING_POST_INSTALL, "")
    }

    // ── Enabled ──

    fun isUpdateCheckEnabled(): Boolean = preferenceStore.getBoolean(KEY_ENABLED, true)
    fun setUpdateCheckEnabled(enabled: Boolean) = preferenceStore.putBoolean(KEY_ENABLED, enabled)
    fun observeUpdateCheckEnabled(): Flow<Boolean> = preferenceStore.booleanFlow(KEY_ENABLED, true)

    // ── Last check timestamp ──

    fun getLastCheckTimestamp(): Long = preferenceStore.getLong(KEY_LAST_CHECK, 0L)
    fun setLastCheckTimestamp(timestamp: Long) = preferenceStore.putLong(KEY_LAST_CHECK, timestamp)
    fun observeLastCheckTimestamp(): Flow<Long> = preferenceStore.longFlow(KEY_LAST_CHECK, 0L)

    // ── Dismiss cooldown ──

    fun getLastDismissedVersion(): String = preferenceStore.getString(KEY_DISMISSED_VERSION, "")
    fun getLastDismissedTimestamp(): Long = preferenceStore.getLong(KEY_DISMISSED_TIMESTAMP, 0L)

    fun recordDismissal(version: String) {
        preferenceStore.putString(KEY_DISMISSED_VERSION, version)
        preferenceStore.putLong(KEY_DISMISSED_TIMESTAMP, System.currentTimeMillis())
    }

    fun isDismissedInCooldown(version: String): Boolean {
        val dismissedVersion = preferenceStore.getString(KEY_DISMISSED_VERSION, "")
        if (dismissedVersion != version) return false
        val dismissedAt = preferenceStore.getLong(KEY_DISMISSED_TIMESTAMP, 0L)
        if (dismissedAt == 0L) return false
        val elapsed = System.currentTimeMillis() - dismissedAt
        return elapsed < DISMISS_COOLDOWN_MS
    }

    /** Clears the dismiss cooldown (for testing). */
    fun clearDismissCooldown() {
        preferenceStore.putString(KEY_DISMISSED_VERSION, "")
        preferenceStore.putLong(KEY_DISMISSED_TIMESTAMP, 0L)
    }

    // ── Downloaded APKs ──

    fun getDownloadedApks(): List<DownloadedApk> = parseDownloadedApks(
        preferenceStore.getString(KEY_DOWNLOADED_APKS, ""),
    )

    fun observeDownloadedApks(): Flow<List<DownloadedApk>> =
        preferenceStore.stringFlow(KEY_DOWNLOADED_APKS, "").map { raw -> parseDownloadedApks(raw) }

    /** Adds a downloaded APK to the list (dedupes by filePath). */
    fun addDownloadedApk(apk: DownloadedApk) {
        val current = getDownloadedApks().toMutableList()
        current.removeAll { it.filePath == apk.filePath }
        current.add(0, apk) // newest first
        preferenceStore.putString(KEY_DOWNLOADED_APKS, serializeDownloadedApks(current))
    }

    /**
     * Removes a downloaded APK record from the list (does NOT delete the file).
     * Use [deleteDownloadedApk] to also delete the file from disk.
     */
    fun removeDownloadedApk(filePath: String) {
        val current = getDownloadedApks().toMutableList()
        current.removeAll { it.filePath == filePath }
        preferenceStore.putString(KEY_DOWNLOADED_APKS, serializeDownloadedApks(current))
    }

    /**
     * Deletes the downloaded APK file from disk AND removes its record.
     * Returns true if the file was deleted (or didn't exist).
     */
    fun deleteDownloadedApk(filePath: String): Boolean {
        var deleted = true
        try {
            val file = File(filePath)
            if (file.exists()) {
                deleted = file.delete()
            }
        } catch (e: Exception) {
            // Non-fatal — still remove from the list
            deleted = false
        }
        removeDownloadedApk(filePath)
        return deleted
    }

    /**
     * Checks if an APK for [versionName] has been downloaded.
     * Verifies both the record exists AND the file is present on disk.
     */
    fun isVersionDownloaded(versionName: String): Boolean {
        return getDownloadedApks().any { apk ->
            apk.versionName == versionName && File(apk.filePath).exists()
        }
    }

    /**
     * Gets the file path for a downloaded APK by version name.
     * Returns null if not downloaded or file doesn't exist.
     */
    fun getDownloadedApkPath(versionName: String): String? {
        return getDownloadedApks().firstOrNull { apk ->
            apk.versionName == versionName && File(apk.filePath).exists()
        }?.filePath
    }

    // ── JSON (de)serialization helpers (replace the old getObject API) ──

    private fun parseDownloadedApks(raw: String): List<DownloadedApk> {
        if (raw.isBlank()) return emptyList()
        return try {
            json.decodeFromString(ListSerializer(DownloadedApk.serializer()), raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun serializeDownloadedApks(list: List<DownloadedApk>): String {
        return json.encodeToString(ListSerializer(DownloadedApk.serializer()), list)
    }

    private companion object {
        private const val KEY_ENABLED = "pref_app_update_enabled"
        private const val KEY_LAST_CHECK = "pref_app_update_last_check"
        private const val KEY_DISMISSED_VERSION = "pref_app_update_dismissed_version"
        private const val KEY_DISMISSED_TIMESTAMP = "pref_app_update_dismissed_timestamp"
        private const val KEY_DOWNLOADED_APKS = "pref_app_update_downloaded_apks"
        private const val KEY_PENDING_POST_INSTALL = "pref_app_update_pending_post_install"

        /** 6 hours in milliseconds. */
        private const val DISMISS_COOLDOWN_MS = 6 * 60 * 60 * 1000L
    }
}
