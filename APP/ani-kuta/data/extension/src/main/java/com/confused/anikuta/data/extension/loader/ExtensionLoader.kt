package com.confused.anikuta.data.extension.loader

import android.content.Context
import android.content.pm.PackageManager
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.data.extension.model.Extension
import com.confused.anikuta.data.extension.model.LoadResult
import dalvik.system.PathClassLoader
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import java.security.MessageDigest

/**
 * Loads extension APKs at runtime using a DEX classloader.
 *
 * The loader scans for installed packages that declare the extension metadata,
 * loads their DEX files via [PathClassLoader], and instantiates the [AnimeSource]
 * implementations defined in the extension.
 *
 * CORE_RULES §20: All operations are logged with tag "Anikuta:Data:Extension:Loader".
 */
class ExtensionLoader(
    private val context: Context,
) {

    companion object {
        private const val TAG = "Anikuta:Data:Extension:Loader"

        /** The meta-data key in the extension's AndroidManifest that declares the source class. */
        private const val METADATA_SOURCE_CLASS = "ani.source.class"

        /** The meta-data key for the extension's NSFW flag. */
        private const val METADATA_IS_NSFW = "ani.extension.nsfw"
    }

    /**
     * Load all installed extensions.
     *
     * Scans [PackageManager] for packages with the extension metadata,
     * loads each one, and returns the results.
     */
    fun loadAll(): List<LoadResult> {
        Logger.i(TAG) { "Loading all extensions..." }

        val packageManager = context.packageManager
        val flags = PackageManager.GET_META_DATA or PackageManager.GET_SIGNATURES

        // Find all packages with our extension metadata
        val extensionPackages = packageManager.getInstalledPackages(flags)
            .filter { it.applicationInfo?.metaData?.containsKey(METADATA_SOURCE_CLASS) == true }

        Logger.i(TAG) { "Found ${extensionPackages.size} extension packages" }

        return extensionPackages.map { pkg -> loadExtension(pkg.packageName) }
    }

    /**
     * Load a single extension by package name.
     */
    fun loadExtension(packageName: String): LoadResult {
        Logger.d(TAG) { "Loading extension: $packageName" }

        return try {
            val packageManager = context.packageManager
            val flags = PackageManager.GET_META_DATA or PackageManager.GET_SIGNATURES
            val packageInfo = packageManager.getPackageInfo(packageName, flags)
            val appInfo = packageInfo.applicationInfo!!

            // Get the source class name from metadata
            val sourceClassName = appInfo.metaData?.getString(METADATA_SOURCE_CLASS)
            if (sourceClassName == null) {
                Logger.w(TAG) { "No source class in metadata for $packageName" }
                return LoadResult.Error(packageName, "No source class in metadata")
            }

            // Get the signature fingerprint
            val signatureFingerprint = packageInfo.signatures?.firstOrNull()?.let {
                calculateFingerprint(it.toByteArray())
            }

            // Load the DEX file
            val sourceApk = appInfo.sourceDir
            val nativeLibDir = appInfo.nativeLibraryDir
            val classLoader = PathClassLoader(sourceApk, nativeLibDir, context.classLoader)

            // Instantiate the source(s)
            val sources = loadSources(classLoader, sourceClassName)

            // Check NSFW flag
            val isNsfw = appInfo.metaData?.getBoolean(METADATA_IS_NSFW, false) ?: false

            val extension = Extension(
                packageName = packageName,
                name = packageManager.getApplicationLabel(appInfo).toString(),
                versionName = packageInfo.versionName ?: "unknown",
                versionCode = if (packageInfo.longVersionCode != 0L) packageInfo.longVersionCode else packageInfo.versionCode.toLong(),
                sources = sources,
                isNsfw = isNsfw,
                signatureFingerprint = signatureFingerprint,
                isEnabled = true,
            )

            Logger.i(TAG) { "Loaded: ${extension.name} v${extension.versionName} (${sources.size} sources)" }
            LoadResult.Success(extension)

        } catch (e: Exception) {
            Logger.e(TAG, e) { "Failed to load $packageName: ${e.message}" }
            LoadResult.Error(packageName, e.message ?: "Unknown error")
        }
    }

    /**
     * Instantiate sources from a class loaded by the extension's classloader.
     *
     * If the class implements [AnimeSourceFactory], it creates multiple sources.
     * If it implements [AnimeSource] directly, it's a single source.
     */
    private fun loadSources(classLoader: ClassLoader, className: String): List<AnimeSource> {
        val clazz = Class.forName(className, false, classLoader)

        return when {
            AnimeSourceFactory::class.java.isAssignableFrom(clazz) -> {
                val factory = clazz.getDeclaredConstructor().newInstance() as AnimeSourceFactory
                factory.createSources()
            }
            AnimeSource::class.java.isAssignableFrom(clazz) -> {
                listOf(clazz.getDeclaredConstructor().newInstance() as AnimeSource)
            }
            else -> {
                Logger.w(TAG) { "Class $className is neither AnimeSource nor AnimeSourceFactory" }
                emptyList()
            }
        }
    }

    /**
     * Calculate the SHA-256 fingerprint of the extension's signing certificate.
     */
    private fun calculateFingerprint(signature: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(signature)
        return hash.joinToString(":") { "%02X".format(it) }
    }
}
