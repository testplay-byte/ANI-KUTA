package com.confused.anikuta.data.cloudstream.installer

import android.content.Context
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.providerapi.InstallStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import java.io.File
import java.security.MessageDigest

/**
 * Downloads + verifies + places a .cs3 plugin file (doc 04 §4.4):
 * temp download in cacheDir → stream SHA-256 vs plugins.json fileHash
 * ("sha256-<hex>", only-if-non-null like CS3) → ATOMIC move into the
 * repo-salted install dir (doc 23 §5.3 — the repo salt is what allows the same
 * plugin name from two repos).
 *
 * Unlike the aniyomi installer there is NO PackageInstaller session — a .cs3 is
 * not an APK; the file just needs to be on disk read-only before the loader runs.
 */
class CloudstreamPluginInstaller(
    private val context: Context,
    private val client: OkHttpClient,
) {

    /**
     * Streams the .cs3 to its final location, emitting [InstallStep]s with
     * throttled progress (200ms, D-309 convention). Throws on hash mismatch —
     * the caller maps that to an Error step.
     */
    fun download(
        url: String,
        expectedFileHash: String?,
        targetFile: File,
    ): Flow<InstallStep> = flow {
        emit(InstallStep.Downloading(0))
        val tempFile = File(context.cacheDir, "cs-${targetFile.name}.tmp")
        try {
            val request = okhttp3.Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Download failed: HTTP ${response.code}")
                }
                val body = response.body ?: throw IllegalStateException("Empty response body")
                val total = body.contentLength()
                val digest = MessageDigest.getInstance("SHA-256")

                var lastEmit = 0L
                body.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastEmit > 200) {
                                lastEmit = now
                                val progress = if (total > 0) {
                                    ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                                } else {
                                    -1
                                }
                                emit(InstallStep.Downloading(progress))
                            }
                        }
                    }
                }

                // Hash verification — integrity, not authenticity (doc 04 §7).
                if (expectedFileHash != null) {
                    val computed = "sha256-" + digest.digest().joinToString("") { "%02x".format(it) }
                    if (!computed.equals(expectedFileHash, ignoreCase = true)) {
                        tempFile.delete()
                        throw IllegalStateException("Extension hash mismatch — download corrupted")
                    }
                } else {
                    Logger.w(TAG) { "No fileHash for ${targetFile.name} — download unverified" }
                }
            }

            // Atomic-ish move into place (REPLACE_EXISTING + ATOMIC_MOVE, doc 04 §4.4).
            targetFile.parentFile?.mkdirs()
            if (targetFile.exists()) targetFile.delete()
            runCatching {
                java.nio.file.Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                )
            }.getOrElse {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            // Session-2 device round: small .cs3 files stream in under one progress
            // tick, so the UI ring never visibly fills. Emit the completed 100%
            // explicitly and hold it for a beat — the row's animated ring finishes
            // filling BEFORE the Installing state replaces it.
            emit(InstallStep.Downloading(100))
            delay(DOWNLOAD_FILL_BEAT_MS)
            emit(InstallStep.Installing)
        } catch (t: Throwable) {
            tempFile.delete()
            throw t
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        private const val TAG = "Anikuta:Data:Cloudstream:Installer"

        /** How long the 100% state is held so the progress ring visibly completes. */
        private const val DOWNLOAD_FILL_BEAT_MS = 300L

        /**
         * The repo-salted install path (doc 02 §5.1 / doc 23 §5.3):
         * `filesDir/CloudstreamExtensions/<sanitize(repoUrl)>.<hash>/<sanitize(internalName)>.<hash>.cs3`
         * File existence doubles as the "is installed" check, exactly like CS3.
         */
        fun pluginPath(filesDir: File, internalName: String, repoUrl: String): File {
            val repoSalt = "${sanitizeFilename(repoUrl)}.${repoUrl.hashCode()}"
            val fileName = "${sanitizeFilename(internalName)}.${internalName.hashCode()}.cs3"
            return File(File(File(filesDir, PLUGINS_DIR), repoSalt), fileName)
        }

        private const val PLUGINS_DIR = "CloudstreamExtensions"

        /** Strips path separators + filesystem-hostile characters (our own sanitizer). */
        fun sanitizeFilename(name: String): String {
            return name.replace(Regex("""[\\/:*?"<>|\s]+"""), "_")
                .trim('.', ' ', '_')
                .ifEmpty { "repo" }
        }
    }
}
