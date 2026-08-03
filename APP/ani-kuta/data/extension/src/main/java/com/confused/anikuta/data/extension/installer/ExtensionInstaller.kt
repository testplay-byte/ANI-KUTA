package com.confused.anikuta.data.extension.installer

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

/**
 * Downloads an extension APK via OkHttp and dispatches it to
 * [ExtensionInstallService] for installation.
 *
 * Ported from the old project. Serializes concurrent installs with a [Mutex]
 * (one install at a time, app-wide).
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
    }

    private val installMutex = Mutex()

    /**
     * Download + install [extension]'s APK.
     *
     * Emits: Pending → Downloading → Installing.
     * The terminal state (Installed/Error) arrives via the system broadcast,
     * NOT from this flow.
     */
    fun downloadAndInstall(apkUrl: String, extension: AnimeExtension.Available): Flow<InstallStep> = flow {
        installMutex.withLock {
            emit(InstallStep.Pending)

            val tempFile = File(context.cacheDir, "ext-${extension.pkgName}-${extension.apkName}")

            // Download
            emit(InstallStep.Downloading)
            val downloaded = downloadApk(apkUrl, tempFile)
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

    /** Uninstall an extension via the system uninstall intent. */
    fun uninstallApk(pkgName: String) {
        Logger.i(TAG) { "Uninstalling: $pkgName" }
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.fromParts("package", pkgName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Logger.e(TAG, e) { "ACTION_DELETE failed, falling back to app details" }
            val fallback = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", pkgName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallback)
            Toast.makeText(context, "Could not auto-uninstall. Please uninstall manually.", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun downloadApk(url: String, dest: File): Boolean {
        return runCatching {
            dest.parentFile?.mkdirs()
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            if (!response.isSuccessful) {
                Logger.e(TAG) { "Download failed: HTTP ${response.code} for $url" }
                return false
            }
            response.body?.byteStream()?.use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return false
            Logger.d(TAG) { "Downloaded ${dest.length()} bytes to ${dest.name}" }
            true
        }.getOrElse { e ->
            Logger.e(TAG, e) { "Download failed for $url" }
            false
        }
    }
}
