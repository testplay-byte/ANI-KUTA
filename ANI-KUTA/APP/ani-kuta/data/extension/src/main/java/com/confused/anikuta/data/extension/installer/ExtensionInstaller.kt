package com.confused.anikuta.data.extension.installer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.data.extension.model.AnimeExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import com.confused.anikuta.core.providerapi.InstallStep

/**
 * Downloads an extension APK via OkHttp and dispatches it to
 * [ExtensionInstallService] for installation.
 *
 * Ported from the old project. Serializes concurrent installs with a [Mutex]
 * (one install at a time, app-wide).
 *
 * D-309: the download STREAMS progress — [InstallStep.Downloading] carries a
 * percent (0..100, or -1 for unknown size) emitted at most every 200ms, so the
 * UI can render a real download animation (was: one opaque Downloading state,
 * no feedback until the OS install prompt appeared).
 *
 * The [downloadAndInstall] flow only emits up to [InstallStep.Installing] — the
 * terminal [InstallStep.Installed] / [InstallStep.Error] arrives asynchronously
 * via the system PACKAGE_ADDED broadcast → [ExtensionInstallReceiver] →
 * [ExtensionManager] re-scan.
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
    }

    private val installMutex = Mutex()

    /**
     * Download + install [extension]'s APK.
     *
     * Emits: Pending → Downloading(progress…) → Installing.
     * The terminal state (Installed/Error) arrives via the system broadcast,
     * NOT from this flow.
     */
    fun downloadAndInstall(apkUrl: String, extension: AnimeExtension.Available): Flow<InstallStep> = flow {
        installMutex.withLock {
            emit(InstallStep.Pending)

            val tempFile = File(context.cacheDir, "ext-${extension.pkgName}-${extension.apkName}")

            // Download (D-309: with streamed progress).
            emit(InstallStep.Downloading(0))
            val downloaded = downloadApk(apkUrl, tempFile) { progress ->
                emit(InstallStep.Downloading(progress))
            }
            if (!downloaded) {
                tempFile.delete()
                emit(InstallStep.Error)
                return@withLock
            }

            // Dispatch to install service
            emit(InstallStep.Installing)
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
    }.flowOn(Dispatchers.IO)

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
