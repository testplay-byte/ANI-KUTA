package com.confused.anikuta.data.extension.loader

import android.content.Context
import android.content.pm.PackageManager
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.data.extension.model.AnimeExtension
import com.confused.anikuta.data.extension.model.LoadResult
import com.confused.anikuta.data.extension.trust.TrustService
import dalvik.system.PathClassLoader
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import java.security.MessageDigest

/**
 * Loads extension APKs at runtime using a DEX classloader.
 *
 * Ported from the old project with adaptations for the new AnimeExtension sealed
 * class. Scans for installed packages that declare the extension metadata,
 * loads their DEX files via [PathClassLoader], and instantiates the [AnimeSource]
 * implementations.
 *
 * CORE_RULES §20: All operations logged with tag "Anikuta:Data:Extension:Loader".
 */
class ExtensionLoader(
    private val context: Context,
    private val trustService: TrustService,
) {

    companion object {
        private const val TAG = "Anikuta:Data:Extension:Loader"

        /** The meta-data key declaring the extension's source class. */
        private const val METADATA_SOURCE_CLASS = "ani.source.class"

        /** The meta-data key for the NSFW flag. */
        private const val METADATA_IS_NSFW = "ani.extension.nsfw"
    }

    /**
     * Load all installed extensions. Returns a list of [LoadResult] — the caller
     * partitions them into trusted/untrusted/error.
     */
    fun loadAll(): List<LoadResult> {
        Logger.i(TAG) { "Loading all extensions..." }

        val packageManager = context.packageManager
        val flags = PackageManager.GET_META_DATA or PackageManager.GET_SIGNATURES

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

            val sourceClassName = appInfo.metaData?.getString(METADATA_SOURCE_CLASS)
            if (sourceClassName == null) {
                Logger.w(TAG) { "No source class in metadata for $packageName" }
                return LoadResult.Error(packageName, "No source class in metadata")
            }

            val signatureFingerprint = packageInfo.signatures?.firstOrNull()?.let {
                calculateFingerprint(it.toByteArray())
            }

            val versionName = packageInfo.versionName ?: "unknown"
            val versionCode = if (packageInfo.longVersionCode != 0L) {
                packageInfo.longVersionCode
            } else {
                packageInfo.versionCode.toLong()
            }
            val libVersion = AnimeExtension.parseLibVersion(versionName)
            val isNsfw = appInfo.metaData?.getBoolean(METADATA_IS_NSFW, false) ?: false
            val displayName = packageManager.getApplicationLabel(appInfo).toString()

            // Check trust.
            if (!trustService.isTrusted(signatureFingerprint)) {
                Logger.w(TAG) { "Extension $packageName is untrusted (fingerprint: $signatureFingerprint)" }
                return LoadResult.Untrusted(
                    AnimeExtension.Untrusted(
                        name = displayName,
                        pkgName = packageName,
                        versionName = versionName,
                        versionCode = versionCode,
                        libVersion = libVersion,
                        signatureHash = signatureFingerprint ?: "",
                        isNsfw = isNsfw,
                    )
                )
            }

            // Load the DEX file + instantiate sources.
            val sourceApk = appInfo.sourceDir
            val nativeLibDir = appInfo.nativeLibraryDir
            val classLoader = PathClassLoader(sourceApk, nativeLibDir, context.classLoader)
            val sources = loadSources(classLoader, sourceClassName)

            val extension = AnimeExtension.Installed(
                name = displayName,
                pkgName = packageName,
                versionName = versionName,
                versionCode = versionCode,
                libVersion = libVersion,
                lang = null,
                isNsfw = isNsfw,
                isTorrent = false,
                sources = sources,
            )

            Logger.i(TAG) { "Loaded: ${extension.name} v${extension.versionName} (${sources.size} sources)" }
            LoadResult.Success(extension)
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Failed to load $packageName: ${e.message}" }
            LoadResult.Error(packageName, e.message ?: "Unknown error")
        }
    }

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

    private fun calculateFingerprint(signature: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(signature)
        return hash.joinToString(":") { "%02X".format(it) }
    }
}
