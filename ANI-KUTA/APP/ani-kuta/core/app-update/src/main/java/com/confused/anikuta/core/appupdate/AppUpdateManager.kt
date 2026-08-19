package com.confused.anikuta.core.appupdate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.confused.anikuta.core.common.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * The central orchestrator for the app self-update system.
 *
 * # Responsibilities
 *
 * 1. **Check for updates** — queries all registered [UpdateSource]s for the
 *    latest release. Returns the first non-null result (source priority order).
 * 2. **Decide whether to show the update dialog** — checks the 6-hour dismiss
 *    cooldown + the auto-check enabled setting.
 * 3. **Download updates** — delegates to [UpdateDownloader], tracks progress
 *    via a [StateFlow].
 * 4. **Install updates** — delegates to [ApkInstaller] to launch the system
 *    installer after download completes.
 * 5. **Track downloaded versions** — records each completed download in
 *    [AppUpdatePreferences] for the "downloaded versions" UI.
 *
 * # State
 *
 * - [latestUpdate] — the most recent update info found (or null).
 * - [downloadProgress] — live progress of the current download (or null).
 * - [isChecking] — true while a check is in progress.
 *
 * # Integration
 *
 * - **App open** → [com.confused.anikuta.AppRoot] calls [cleanupOldDownloads] +
 *   [clearUpdateState] + (if [shouldCheckForUpdate]) [checkForUpdate]. The
 *   check sets [shouldShowUpdateSheet] to true when an update is found AND not
 *   in the dismiss cooldown — AppRoot observes the StateFlow + renders
 *   `UpdateBottomSheet` (page-gated so it never overlays the player).
 * - **Manual check** → [checkForUpdate] (from Settings → About → "Check for
 *   updates"). Same sheet-shows-when-found logic.
 * - **Download** → [startDownload] (from the update sheet or settings).
 * - **Install** → [installDownloadedApk] (after download completes, or from
 *   the "downloaded versions" list).
 *
 * @param context the app context.
 * @param preferences the update preferences.
 * @param sources the registered update sources (priority order).
 */
class AppUpdateManager(
    private val context: Context,
    private val preferences: AppUpdatePreferences,
    private val sources: List<UpdateSource>,
) {
    private val downloader = UpdateDownloader(context, createOkHttpClient())
    private val installer = ApkInstaller(context)

    /**
     * Manages the system notification (progress + completion + cancel action).
     * Lazily-initialized on first use (creates the notification channel in [init]).
     */
    private val updateNotificationManager = UpdateNotificationManager(context)

    /**
     * The coroutine job for the active download flow. Stored so that
     * [cancelDownload] can call `cancel()` on it — without this, the
     * download flow would keep emitting progress after the user tapped
     * Cancel, causing the UI to flicker (null → non-null → null → non-null)
     * and the download to continue.
     */
    private var downloadJob: Job? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * BroadcastReceiver for the notification's "Cancel" action button.
     *
     * Registered in [init] (package-scoped on API 33+). When the user taps
     * "Cancel" in the system shade notification, the
     * [UpdateNotificationManager] fires a broadcast with the
     * [UpdateNotificationManager.ACTION_CANCEL] intent → this receiver
     * catches it → calls [cancelDownload].
     */
    private val cancelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == UpdateNotificationManager.ACTION_CANCEL) {
                Logger.i(TAG) { "cancelReceiver: cancel broadcast received from notification" }
                cancelDownload()
            }
        }
    }

    init {
        // Create the notification channel up-front (idempotent).
        updateNotificationManager.createChannel()

        // Register the cancel-action receiver. Package-scoped (the broadcast
        // is sent with setPackage(context.packageName) by
        // UpdateNotificationManager, so other apps can't trigger it).
        try {
            val filter = IntentFilter(UpdateNotificationManager.ACTION_CANCEL)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(cancelReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(cancelReceiver, filter)
            }
            Logger.i(TAG) { "init: cancel broadcast receiver registered" }
        } catch (e: Exception) {
            // Best-effort — the receiver is only used to wire the notification's
            // Cancel button to cancelDownload(). If registration fails (e.g.
            // receiver-heavy context), the in-app UI still works.
            Logger.w(TAG, e) { "init: failed to register cancel receiver" }
        }
    }

    private val _latestUpdate = MutableStateFlow<AppUpdateInfo?>(null)
    val latestUpdate: StateFlow<AppUpdateInfo?> = _latestUpdate.asStateFlow()

    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    private val _lastCheckError = MutableStateFlow<String?>(null)
    val lastCheckError: StateFlow<String?> = _lastCheckError.asStateFlow()

    // ── Update sheet visibility (UI-gated) ──
    // True when the UpdateBottomSheet should be rendered by [com.confused.anikuta.AppRoot].
    // Set to true by [checkForUpdate] when an update is found AND not in the
    // 6-hour dismiss cooldown. Set to false by [dismissUpdateSheet] (which also
    // records the cooldown so the same version doesn't re-prompt for 6h).
    private val _shouldShowUpdateSheet = MutableStateFlow(false)
    val shouldShowUpdateSheet: StateFlow<Boolean> = _shouldShowUpdateSheet.asStateFlow()

    // D-199: transient "up to date" state — true for ~3 seconds after a manual
    // check finds no update, then auto-resets to false. The UI uses this to show
    // "You are on the latest version" briefly before reverting to "Check for updates".
    private val _isUpToDate = MutableStateFlow(false)
    val isUpToDate: StateFlow<Boolean> = _isUpToDate.asStateFlow()

    /**
     * Gets the installed app's version name from the package manager.
     */
    private fun getInstalledVersionName(): String = try {
        val pm = context.packageManager
        val info = pm.getPackageInfo(context.packageName, 0)
        info.versionName ?: "0.0.0"
    } catch (e: Exception) {
        Logger.w(TAG, e) { "getInstalledVersionName: failed" }
        "0.0.0"
    }

    /**
     * Gets the installed app's version code from the package manager.
     */
    private fun getInstalledVersionCode(): Long = try {
        val pm = context.packageManager
        val info = pm.getPackageInfo(context.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    } catch (e: Exception) {
        Logger.w(TAG, e) { "getInstalledVersionCode: failed" }
        0L
    }

    /**
     * Checks for updates on app startup.
     *
     * Respects:
     * - [AppUpdatePreferences.isUpdateCheckEnabled] — if OFF, does nothing.
     * - [AppUpdatePreferences.isDismissedInCooldown] — if the latest version
     *   was dismissed < 6 hours ago, does nothing.
     *
     * On success, sets [latestUpdate]. The caller (UI) can then check
     * [shouldShowDialog] to decide whether to show the update dialog.
     */
    fun checkForUpdateOnStartup() {
        if (!preferences.isUpdateCheckEnabled()) {
            Logger.d(TAG) { "checkForUpdateOnStartup: auto-check disabled — skipping" }
            return
        }
        scope.launch {
            val update = checkForUpdate()
            if (update != null && preferences.isDismissedInCooldown(update.versionName)) {
                Logger.i(TAG) { "checkForUpdateOnStartup: update ${update.versionName} is in dismiss cooldown — not surfacing" }
                // Still store it (so manual check in settings is instant), just don't show the dialog.
                _latestUpdate.value = update
            }
        }
    }

    /**
     * Manually checks for updates (from Settings → About → "Check for updates").
     *
     * Always runs regardless of the auto-check setting or dismiss cooldown.
     * Sets [latestUpdate] + returns the result.
     *
     * If an update is found, [shouldShowUpdateSheet] is set to true so the
     * UpdateBottomSheet renders (the About page is in the allowed keys list).
     */
    suspend fun checkForUpdate(): AppUpdateInfo? {
        _isChecking.value = true
        _lastCheckError.value = null
        try {
            val currentCode = getInstalledVersionCode()
            val currentName = getInstalledVersionName()
            Logger.i(TAG) { "checkForUpdate: current=$currentName/$currentCode, sources=${sources.map { it.id }}" }

            for (source in sources) {
                try {
                    val update = source.fetchLatestUpdate(currentCode, currentName)
                    if (update != null) {
                        _latestUpdate.value = update
                        preferences.setLastCheckTimestamp(System.currentTimeMillis())
                        Logger.i(TAG) { "checkForUpdate: found update ${update.versionName} from ${source.id}" }
                        // D-199: Manual check ALWAYS surfaces the sheet (even if in
                        // cooldown). The cooldown only suppresses STARTUP checks.
                        // If the user explicitly tapped "Check for updates", they
                        // want to see the result.
                        _shouldShowUpdateSheet.value = true
                        Logger.i(TAG) { "checkForUpdate: surfacing update sheet for ${update.versionName}" }
                        return update
                    }
                } catch (e: Exception) {
                    Logger.w(TAG, e) { "checkForUpdate: source ${source.id} failed" }
                }
            }

            // No update found from any source.
            _latestUpdate.value = null
            _shouldShowUpdateSheet.value = false
            preferences.setLastCheckTimestamp(System.currentTimeMillis())
            // D-199: flash "up to date" for 3 seconds.
            _isUpToDate.value = true
            scope.launch {
                delay(3000)
                _isUpToDate.value = false
            }
            Logger.i(TAG) { "checkForUpdate: no update available" }
            return null
        } catch (e: Exception) {
            Logger.e(TAG, e) { "checkForUpdate: failed" }
            _lastCheckError.value = e.message ?: "Check failed"
            return null
        } finally {
            _isChecking.value = false
        }
    }

    /**
     * Determines if the update dialog should be shown automatically on app open.
     *
     * Returns true only if:
     * 1. [latestUpdate] is non-null.
     * 2. The update is NOT in the dismiss cooldown.
     */
    fun shouldShowDialog(): Boolean {
        val update = _latestUpdate.value ?: return false
        return !preferences.isDismissedInCooldown(update.versionName)
    }

    /**
     * Returns true if the auto-update check is enabled in preferences.
     *
     * Called by [com.confused.anikuta.AppRoot] in its startup LaunchedEffect to
     * gate the [checkForUpdate] call. Respects the user's "Auto-check for
     * updates" toggle on About → Updates.
     */
    fun shouldCheckForUpdate(): Boolean = preferences.isUpdateCheckEnabled()

    /**
     * Hides the update bottom sheet + records the 6-hour dismiss cooldown.
     *
     * Called by [com.confused.anikuta.updates.UpdateBottomSheet]'s onDismiss
     * (X button or sheet scrim dismiss). The cooldown prevents the same
     * version from re-prompting on the next app open.
     *
     * Side effects:
     * 1. Records the dismissal via [dismissUpdate] (writes the cooldown prefs).
     * 2. Sets [shouldShowUpdateSheet] to false (hides the sheet on next frame).
     */
    /**
     * Dismisses the sheet WITHOUT recording the 6-hour cooldown.
     *
     * Used when the user swipes down or taps outside the sheet — the sheet
     * closes but the update is NOT snoozed. On the next app open (or
     * navigation to an allowed page), the sheet re-appears.
     */
    fun hideUpdateSheet() {
        Logger.i(TAG) { "hideUpdateSheet: hiding sheet (NO cooldown — will re-show)" }
        _shouldShowUpdateSheet.value = false
    }

    /**
     * Dismisses the sheet AND records the 6-hour cooldown.
     *
     * Used ONLY when the user taps the X button — the update is snoozed
     * for 6 hours. On the next app open, the sheet does NOT re-appear
     * (unless 6 hours have passed).
     */
    fun dismissUpdateSheet() {
        Logger.i(TAG) { "dismissUpdateSheet: hiding sheet + recording cooldown (X button)" }
        dismissUpdate()
        _shouldShowUpdateSheet.value = false
    }

    /**
     * Records that the user dismissed the update dialog.
     *
     * This triggers the 6-hour cooldown for the current version.
     */
    fun dismissUpdate() {
        val update = _latestUpdate.value ?: return
        preferences.recordDismissal(update.versionName)
        Logger.i(TAG) { "dismissUpdate: user dismissed ${update.versionName} (6h cooldown)" }
    }

    /**
     * Starts downloading the update APK.
     *
     * Progress is reported via [downloadProgress]. When complete, the APK is
     * recorded in [AppUpdatePreferences] for the "downloaded versions" list.
     * The caller should observe [downloadProgress] + call [installDownloadedApk]
     * when [DownloadProgress.isComplete] is true.
     *
     * If a download is already in progress, this is a no-op.
     */
    fun startDownload() {
        val update = _latestUpdate.value ?: run {
            Logger.w(TAG) { "startDownload: no update to download" }
            return
        }
        if (_downloadProgress.value != null && _downloadProgress.value?.isComplete == false) {
            Logger.w(TAG) { "startDownload: download already in progress" }
            return
        }

        // ── Retry cleanup ──
        // If the previous download errored, delete the partial APK file +
        // clear the download progress state before starting a fresh download.
        // Without this, the retry would either append to a corrupted file or
        // the UI would stay stuck on the "Retry" button.
        _downloadProgress.value?.let { existing ->
            if (existing.error != null) {
                Logger.i(TAG) { "startDownload: previous download errored — cleaning up before retry" }
                val apkFile = downloader.getApkFile(update.versionName)
                if (apkFile.exists()) {
                    val deleted = apkFile.delete()
                    Logger.d(TAG) { "startDownload: deleted partial APK (${apkFile.absolutePath}) — $deleted" }
                }
                // Remove any stale record from the downloaded APKs list
                preferences.removeDownloadedApk(apkFile.absolutePath)
                // Clear the error state so the UI shows "downloading" immediately
                _downloadProgress.value = null
            }
        }

        Logger.i(TAG) { "startDownload: starting download of ${update.versionName}" }
        // D-199: set a "starting" state immediately so the UI shows progress
        // right away (instead of staying on "Download" until the first byte arrives).
        _downloadProgress.value = DownloadProgress.downloading(0L, update.apkSizeBytes, null)

        // ── Start the foreground service + post the initial notification ──
        // The service keeps the process alive if the user swipes the app
        // from recents. The notification is shared (same NOTIFICATION_ID)
        // between the service's startForeground + UpdateNotificationManager.
        try {
            UpdateDownloadService.start(context, update.versionName)
            updateNotificationManager.showProgress(
                versionName = update.versionName,
                percent = 0,
                downloadedBytes = 0L,
                totalBytes = update.apkSizeBytes,
            )
        } catch (e: Exception) {
            // Best-effort — the download can still proceed without the
            // foreground service / notification.
            Logger.w(TAG, e) { "startDownload: failed to start foreground service / notification" }
        }

        downloadJob = scope.launch {
            try {
                downloader.download(update).collect { progress ->
                    _downloadProgress.value = progress
                    // Update the system notification on every progress emission
                    // (the manager dedupes via setOnlyAlertOnce).
                    if (!progress.isComplete && progress.error == null) {
                        val percent = progress.percent ?: 0
                        updateNotificationManager.showProgress(
                            versionName = update.versionName,
                            percent = percent,
                            downloadedBytes = progress.bytesDownloaded,
                            totalBytes = progress.totalBytes,
                        )
                    }
                    if (progress.isComplete && progress.error == null) {
                        // Record the downloaded APK.
                        val apkFile = downloader.getApkFile(update.versionName)
                        preferences.addDownloadedApk(
                            DownloadedApk(
                                versionName = update.versionName,
                                filePath = apkFile.absolutePath,
                                downloadedAt = System.currentTimeMillis(),
                                sizeBytes = apkFile.length(),
                                source = update.source,
                            ),
                        )
                        Logger.i(TAG) { "startDownload: download complete + recorded — ${apkFile.absolutePath}" }
                        // Replace the progress notification with the
                        // "ready to install" notification (auto-cancel on tap).
                        updateNotificationManager.showComplete(update.versionName)
                        // Stop the foreground service — the download is done,
                        // no need to keep the process at foreground priority.
                        UpdateDownloadService.stop(context)
                    }
                    if (progress.error != null) {
                        // Download errored — stop the foreground service +
                        // cancel the notification. The UI shows a Retry button
                        // via the downloadProgress StateFlow.
                        updateNotificationManager.cancel()
                        UpdateDownloadService.stop(context)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Expected — cancelDownload() cancelled the job. Re-throw so
                // the coroutine machinery marks it as cancelled (not failed).
                Logger.i(TAG) { "startDownload: download coroutine cancelled by cancelDownload()" }
                throw e
            } catch (e: Exception) {
                // D-199: defensive catch — if the flow itself throws (not just
                // emits an error), capture it here so the app doesn't crash.
                Logger.e(TAG, e) { "startDownload: download flow threw exception" }
                _downloadProgress.value = DownloadProgress.error(
                    "${e::class.java.simpleName}: ${e.message ?: "Download failed"}",
                )
                // Clean up partial file.
                val apkFile = downloader.getApkFile(update.versionName)
                if (apkFile.exists()) apkFile.delete()
                // Stop the foreground service + cancel the notification.
                updateNotificationManager.cancel()
                UpdateDownloadService.stop(context)
            }
        }
    }

    /**
     * Installs a downloaded APK file.
     *
     * Launches the system installer. After the user confirms, the app is
     * updated (and likely restarted).
     *
     * **Post-install popup:** before launching the installer, this records
     * the version being installed via [AppUpdatePreferences.setPendingPostInstall].
     * On the next app startup, [com.confused.anikuta.AppRoot] (in MainActivity.kt)
     * checks the pending-post-install marker — if non-empty, the post-install
     * success popup is shown (with the "Cleaning up downloaded APK…" animation)
     * and the marker is cleared.
     *
     * @param apkPath the absolute path to the APK file. If null, uses the
     *   latest update's APK path.
     * @return true if the installer was launched successfully.
     */
    fun installDownloadedApk(apkPath: String? = null): Boolean {
        val path = apkPath ?: run {
            val update = _latestUpdate.value ?: return false
            downloader.getApkFile(update.versionName).absolutePath
        }

        // ── Record the version we're about to install ──
        // Look up the version name from the downloaded APK record first
        // (handles both "install from update sheet" + "install from
        // About → downloaded versions list" entry points). Falls back to
        // the latest update's version name if no record is found.
        val versionName = preferences.getDownloadedApks()
            .firstOrNull { it.filePath == path }
            ?.versionName
            ?: _latestUpdate.value?.versionName
        if (!versionName.isNullOrEmpty()) {
            preferences.setPendingPostInstall(versionName)
            Logger.i(TAG) { "installDownloadedApk: recorded pending post-install for v$versionName" }
        }

        return installer.installApk(path)
    }

    /**
     * Cancels the current download + deletes the partial APK file.
     *
     * Called when the user taps the cancel (X) button on the About page's
     * download progress bar, OR when they tap the delete button on the
     * download error card, OR when they tap "Cancel" on the system
     * notification's progress bar (which fires a broadcast received by
     * [cancelReceiver]). Clears `_downloadProgress` so the UI reverts
     * to the "Download" button state.
     *
     * # Why we cancel the job
     *
     * The download flow (`downloader.download(update).collect { ... }`)
     * runs in a coroutine launched by [startDownload]. If we just set
     * `_downloadProgress.value = null` without cancelling the job, the
     * flow would keep emitting progress + the collect block would re-set
     * `_downloadProgress` back to non-null — the UI would flicker (null →
     * non-null → null → non-null) and the download would continue.
     *
     * Cancelling the job propagates a `CancellationException` into the
     * `collect` block, which the existing try-catch handles gracefully
     * (the catch clause for `CancellationException` just logs + re-throws
     * so the coroutine machinery marks it as cancelled, not failed).
     */
    fun cancelDownload() {
        Logger.i(TAG) { "cancelDownload: cancelling + cleaning up" }
        // ── Cancel the download coroutine FIRST ──
        // This stops new progress emissions from re-setting _downloadProgress
        // after we null it out below. The flow's `collect` will throw a
        // CancellationException which the try-catch in startDownload handles.
        downloadJob?.cancel()
        downloadJob = null
        // ── Cancel the system notification ──
        updateNotificationManager.cancel()
        // ── Stop the foreground service ──
        UpdateDownloadService.stop(context)
        // ── Clear the progress state ──
        _downloadProgress.value = null
        // ── Delete the partial APK file if it exists ──
        _latestUpdate.value?.let { update ->
            val apkFile = downloader.getApkFile(update.versionName)
            if (apkFile.exists()) {
                val deleted = apkFile.delete()
                Logger.i(TAG) { "cancelDownload: deleted partial APK (${apkFile.absolutePath}) — $deleted" }
            }
            preferences.removeDownloadedApk(downloader.getApkFile(update.versionName).absolutePath)
        }
    }

    /**
     * Clears the download progress state (for UI reset after the dialog closes).
     */
    fun clearDownloadProgress() {
        _downloadProgress.value = null
    }

    /**
     * Clears the latest update (for UI reset).
     */
    fun clearLatestUpdate() {
        _latestUpdate.value = null
    }

    // ── Downloaded state helpers ──

    /**
     * Checks if the latest update has already been downloaded.
     *
     * Returns true if [latestUpdate] is non-null AND an APK file for that
     * version exists on disk. The UI uses this to show "Install" instead of
     * "Download" when the user re-opens the update sheet.
     */
    fun isLatestUpdateDownloaded(): Boolean {
        val update = _latestUpdate.value ?: return false
        return preferences.isVersionDownloaded(update.versionName)
    }

    /**
     * Gets the file path for the latest update's downloaded APK.
     * Returns null if not downloaded.
     */
    fun getDownloadedApkPath(): String? {
        val update = _latestUpdate.value ?: return null
        return preferences.getDownloadedApkPath(update.versionName)
    }

    /**
     * Deletes a downloaded APK file AND removes its record.
     * Also clears download progress if it matches the current download.
     *
     * @param filePath the file path of the APK to delete.
     * @return true if the file was deleted (or didn't exist).
     */
    fun deleteDownloadedApk(filePath: String): Boolean {
        // Clear download progress if it's for the same file
        _downloadProgress.value = null
        return preferences.deleteDownloadedApk(filePath)
    }

    /**
     * Cleans up old downloaded APKs — deletes any APK whose version is older
     * than or equal to the currently installed version.
     *
     * Called on app startup to prevent storage bloat. After a successful
     * update install, the old APK files are no longer needed.
     *
     * # The "just installed" case
     *
     * After the user installs an update, the app restarts with the new version.
     * The downloaded APK file is still on disk. This method detects it by
     * checking if the APK's actual version code (read from the APK's manifest
     * via PackageManager) matches the installed version code — if so, the APK
     * was just installed and should be deleted.
     *
     * However, reading the APK's version code requires `PackageParser` (deprecated)
     * or `PackageInstaller` which is complex. Instead, we use a simpler heuristic:
     * if the APK file's version name (from the GitHub release tag) does NOT match
     * any known future update, AND the installed version code is >= the APK's
     * parsed version code, delete it.
     *
     * For the testing loop (where the release tag v0.3.0 has versionCode 300 but
     * the actual APK has versionCode 5), we ALSO delete the APK if its version
     * name matches the installed version name — this handles the "just installed"
     * case correctly.
     */
    fun cleanupOldDownloads() {
        val currentCode = getInstalledVersionCode()
        val currentName = getInstalledVersionName()
        val downloaded = preferences.getDownloadedApks()
        if (downloaded.isEmpty()) return

        Logger.i(TAG) { "cleanupOldDownloads: checking ${downloaded.size} downloaded APKs against current=$currentName/$currentCode" }
        var cleaned = 0
        downloaded.forEach { apk ->
            val apkCode = parseVersionCode(apk.versionName)
            // Delete if:
            // 1. The APK's version code <= current (it's an old version), OR
            // 2. The APK's version name matches the installed version name
            //    (the user just installed it — the file is no longer needed).
            val shouldDelete = apkCode <= currentCode || apk.versionName == currentName
            if (shouldDelete) {
                if (preferences.deleteDownloadedApk(apk.filePath)) {
                    cleaned++
                    Logger.d(TAG) { "cleanupOldDownloads: deleted ${apk.versionName} (code=$apkCode, current=$currentCode)" }
                }
            }
        }
        if (cleaned > 0) {
            Logger.i(TAG) { "cleanupOldDownloads: cleaned up $cleaned old APK(s)" }
        }
    }

    /**
     * Clears the download progress + latest update state.
     *
     * Called after the user successfully installs an update (the app restarts,
     * so this is called on the next startup to reset the UI state). Also
     * resets [shouldShowUpdateSheet] so a stale sheet doesn't render before
     * [checkForUpdate] decides whether to re-surface one.
     */
    fun clearUpdateState() {
        _downloadProgress.value = null
        _latestUpdate.value = null
        _shouldShowUpdateSheet.value = false
    }

    /**
     * Deletes ALL downloaded APK files + records — unconditionally.
     *
     * Called by the post-install success popup after the user installs an
     * update. At that point, ALL downloaded APKs are stale (the just-installed
     * one has been consumed by the system installer, and any older ones are
     * definitely not needed).
     *
     * This is more aggressive than [cleanupOldDownloads] (which only deletes
     * APKs whose version <= the installed version) — but it's the correct
     * behavior for the post-install case because the GitHub release tag
     * version (e.g., "0.3.0" → code 300) doesn't match the APK's actual
     * build versionCode (e.g., 7), so the version comparison in
     * [cleanupOldDownloads] would fail to delete the APK.
     */
    fun deleteAllDownloadedApks() {
        val downloaded = preferences.getDownloadedApks()
        if (downloaded.isEmpty()) return
        Logger.i(TAG) { "deleteAllDownloadedApks: deleting ${downloaded.size} APK(s)" }
        downloaded.forEach { apk ->
            preferences.deleteDownloadedApk(apk.filePath)
        }
        // Also clear the downloader's cache directory for good measure
        try {
            downloader.clearAllDownloads()
        } catch (e: Exception) {
            Logger.w(TAG, e) { "deleteAllDownloadedApks: clearAllDownloads failed (non-fatal)" }
        }
        Logger.i(TAG) { "deleteAllDownloadedApks: complete" }
    }

    /**
     * Parses a semantic version string ("MAJOR.MINOR.PATCH") into a comparable
     * long: `major * 10000 + minor * 100 + patch`.
     */
    private fun parseVersionCode(versionName: String): Long {
        val cleanName = versionName.removePrefix("v").removePrefix("V")
            .substringBefore("-").substringBefore("+").trim()
        val parts = cleanName.split(".")
        return try {
            val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
            major * 10000L + minor * 100L + patch
        } catch (e: Exception) {
            0L
        }
    }

    private fun createOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS) // D-199: 5 min for large APKs
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private companion object {
        private const val TAG = "Anikuta:Core:AppUpdate:Manager"
    }
}
