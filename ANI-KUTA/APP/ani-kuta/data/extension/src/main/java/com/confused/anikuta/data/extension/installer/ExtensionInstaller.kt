package com.confused.anikuta.data.extension.installer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.data.extension.model.AnimeExtension
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Downloads an extension APK via OkHttp and dispatches it to
 * [ExtensionInstallService] for installation.
 *
 * ROUND 93 (D-642) — THE SPLIT: this class used to expose ONE
 * `downloadAndInstall` flow that ENDED the moment the installer service was
 * dispatched. The v1.1.49 device report exposed the flaw: a batch loop over
 * that flow fired every system prompt back-to-back ("I clicked install and
 * it was showing the exact same pop-up again and again, even while Android
 * was already installing") because "dispatched" is not "answered". The
 * pieces are now separate:
 *
 *  • [downloadToTemp] — just the download (parallel-safe; the batch path
 *    runs several at once, per the user's explicit allowance);
 *  • [dispatchInstall] — hands ONE downloaded apk to the service (ONE system
 *    prompt per call; the caller awaits the manager's terminal result
 *    before dispatching the next);
 *  • [onInstallSettled] / [sweepAbandonedTempApks] — the page-exit hygiene:
 *    downloaded-but-never-installed files are deleted, and a file owned by a
 *    live dispatch is never touched.
 *
 * D-309 lineage: the download STREAMS progress — [InstallStep.Downloading]
 * carries a percent (0..100, or -1 for unknown size) emitted at most every
 * 200ms, so the UI can render a real download animation.
 *
 * CORE_RULES §20: All operations logged with tag "Anikuta:Data:Extension:Installer".
 */
class ExtensionInstaller(
    private val context: Context,
    private val client: OkHttpClient,
) {

    companion object {
        private const val TAG = "Anikuta:Data:Extension:Installer"
        private const val DOWNLOAD_BUFFER_BYTES = 8192
        private const val PROGRESS_EMIT_INTERVAL_MS = 200L

        /**
         * ROUND 93 (D-642): files younger than this are left alone by the
         * sweep — a download that just finished (or a dispatch whose service
         * has not reported yet) still owns its APK.
         */
        private const val SWEEP_MIN_AGE_MS = 60_000L
    }

    /**
     * ROUND 93 (D-642): the DISPATCHED-APK registry (pkg → temp path) — the
     * files ExtensionInstallService may still be reading. The sweep skips
     * these; entries drop when the manager reports the install settled.
     */
    private val dispatchedApks = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * ROUND 93 (D-642): downloads [extension]'s APK to the shared temp
     * location WITHOUT dispatching the installer. Returns the file, or null
     * when the download failed (the partial is already deleted). CANCELLATION
     * (the caller's scope died — e.g. the user left the extensions page)
     * deletes the partial before unwinding: no orphaned half-APKs on disk.
     */
    suspend fun downloadToTemp(
        apkUrl: String,
        extension: AnimeExtension.Available,
        onProgress: suspend (Int) -> Unit,
    ): File? {
        val tempFile = File(context.cacheDir, "ext-${extension.pkgName}-${extension.apkName}")
        try {
            onProgress(0)
            val downloaded = downloadApk(apkUrl, tempFile) { progress ->
                onProgress(progress)
            }
            if (!downloaded) {
                tempFile.delete()
                return null
            }
            Logger.d(TAG) { "Downloaded ${extension.pkgName} to ${tempFile.name} (awaiting dispatch)" }
            return tempFile
        } catch (ce: kotlinx.coroutines.CancellationException) {
            tempFile.delete()
            Logger.w(TAG) { "Download of ${extension.pkgName} cancelled — partial deleted" }
            throw ce
        } catch (t: Throwable) {
            tempFile.delete()
            Logger.e(TAG, t) { "Download of ${extension.pkgName} failed" }
            return null
        }
    }

    /**
     * ROUND 93 (D-642): hands a DOWNLOADED apk to the install service — ONE
     * system prompt per call. The caller (the manager) awaits the terminal
     * result before dispatching the next package.
     */
    fun dispatchInstall(tempFile: File, extension: AnimeExtension.Available) {
        dispatchedApks[extension.pkgName] = tempFile.absolutePath
        val serviceIntent = ExtensionInstallService.newIntent(
            context,
            tempFile.absolutePath,
            extension.pkgName,
            downloadId = extension.versionCode,
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    /** ROUND 93 (D-642): the manager reports an install settled — its file is no longer shielded. */
    fun onInstallSettled(pkgName: String) {
        dispatchedApks.remove(pkgName)
    }

    /**
     * ROUND 93 (D-643): deletes leftover temp APKs — downloaded but never
     * installed (a cancelled batch, a scope that died mid-download). Files
     * owned by a live DISPATCH are skipped, and so is anything younger than
     * [SWEEP_MIN_AGE_MS].
     */
    fun sweepAbandonedTempApks() {
        val active = dispatchedApks.values.toSet()
        val now = System.currentTimeMillis()
        val abandoned = context.cacheDir.listFiles { file -> file.name.startsWith("ext-") }
            ?.filter { it.absolutePath !in active && now - it.lastModified() > SWEEP_MIN_AGE_MS }
            .orEmpty()
        if (abandoned.isEmpty()) return
        abandoned.forEach { file ->
            if (file.delete()) {
                Logger.i(TAG) { "Swept abandoned temp APK ${file.name}" }
            }
        }
    }

    /**
     * Uninstall an extension APK via the SYSTEM uninstaller.
     *
     * Round 82 (D-571): this call IS the user confirmation — the Extensions
     * screen no longer shows its own "Uninstall extension?" dialog before
     * reaching here, so Android's own prompt ("Do you want to uninstall this
     * app?" with the app name on top and Cancel / OK) is the one and only
     * confirmation step, exactly as the user specified.
     *
     * Intent ladder (each step logged, every failure surfaced with a toast —
     * the uninstall can never be a silent no-op again):
     * 1. [Intent.ACTION_DELETE] — the modern standard uninstall intent; the
     *    system package installer renders the confirmation prompt.
     * 2. `android.intent.action.UNINSTALL_PACKAGE` — the legacy action
     *    (deprecated API 28 but still handled by the platform uninstaller on
     *    every release since); covers OEM ROMs that don't export a handler
     *    for ACTION_DELETE.
     * 3. [android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS] —
     *    last resort: the app info page where the user uninstalls manually.
     *
     * Note (kept from the original implementation): do NOT guard with
     * resolveActivity() — on Android 11+ package-visibility filtering makes it
     * return null for ACTION_DELETE even though startActivity() succeeds. The
     * manifest carries both the ACTION_DELETE <queries> entry AND
     * QUERY_ALL_PACKAGES, so visibility is not the bottleneck; the ladder
     * exists for ROMs that genuinely lack an uninstall handler.
     */
    fun uninstallApk(pkgName: String) {
        Logger.i(TAG) { "Uninstalling: $pkgName" }
        val uri = Uri.fromParts("package", pkgName, null)

        val primary = Intent(Intent.ACTION_DELETE, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(primary)
            return
        } catch (e: ActivityNotFoundException) {
            Logger.w(TAG) { "ACTION_DELETE not resolved for $pkgName — trying the legacy UNINSTALL_PACKAGE action" }
        } catch (e: Exception) {
            Logger.e(TAG, e) { "ACTION_DELETE launch failed for $pkgName — trying the legacy action" }
        }

        // Legacy uninstall action (deprecated API 28, still resolved by the
        // platform uninstaller — the step-2 rung of the ladder).
        val legacy = Intent("android.intent.action.UNINSTALL_PACKAGE", uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            // EXTRA_RETURN_RESULT only matters for onActivityResult callers;
            // harmless here, kept for parity with the documented contract.
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
        try {
            context.startActivity(legacy)
            return
        } catch (e: ActivityNotFoundException) {
            Logger.w(TAG) { "UNINSTALL_PACKAGE not resolved for $pkgName — opening app details" }
        } catch (e: Exception) {
            Logger.e(TAG, e) { "UNINSTALL_PACKAGE launch failed for $pkgName — opening app details" }
        }

        // Last resort: the app info page (the user uninstalls from there).
        val fallback = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = uri
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(fallback)
            Toast.makeText(
                context,
                "System uninstaller unavailable — opened App info; uninstall from there",
                Toast.LENGTH_LONG,
            ).show()
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Uninstall completely failed for $pkgName (all three intents)" }
            Toast.makeText(context, "Uninstall failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Streams [url] → [dest], reporting percent progress (0..100, or -1 when
     * the server sent no Content-Length). Mirrors UpdateDownloader's throttled
     * emission (D-309).
     */
    private suspend fun downloadApk(
        url: String,
        dest: File,
        onProgress: suspend (Int) -> Unit,
    ): Boolean {
        return runCatching {
            dest.parentFile?.mkdirs()
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            if (!response.isSuccessful) {
                Logger.e(TAG) { "Download failed: HTTP ${response.code} for $url" }
                return false
            }
            val body = response.body ?: return false
            val totalBytes = body.contentLength()
            var bytesDownloaded = 0L
            var lastEmitAt = 0L
            body.byteStream().use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesDownloaded += read

                        val now = System.currentTimeMillis()
                        val finished = totalBytes > 0 && bytesDownloaded >= totalBytes
                        if (now - lastEmitAt >= PROGRESS_EMIT_INTERVAL_MS || finished) {
                            lastEmitAt = now
                            if (totalBytes > 0) {
                                onProgress(((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100))
                            } else {
                                onProgress(-1) // unknown size → indeterminate
                            }
                        }
                    }
                    output.flush()
                }
            }
            Logger.d(TAG) { "Downloaded $bytesDownloaded bytes to ${dest.name}" }
            true
        }.getOrElse { e ->
            Logger.e(TAG, e) { "Download failed for $url" }
            false
        }
    }
}
