package com.confused.anikuta.data.extension.loader

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.data.extension.model.AnimeExtension
import com.confused.anikuta.data.extension.model.LoadResult
import com.confused.anikuta.data.extension.trust.TrustService
import dalvik.system.PathClassLoader
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import java.security.MessageDigest

/**
 * Loads Aniyomi-compatible anime extensions installed on the device.
 *
 * Ported from the old project's `AnimeExtensionLoader` (which was ported from
 * the Aniyomi reference). An extension is an ordinary APK whose manifest declares:
 * - `<uses-feature android:name="tachiyomi.animeextension"/>` (the feature flag)
 * - `<meta-data android:name="tachiyomi.animeextension.class" android:value="..."/>` (source FQCNs)
 *
 * CRITICAL: The metadata keys MUST match the Aniyomi convention exactly — real
 * Aniyomi extensions use `tachiyomi.animeextension.*`, NOT `ani.source.*`.
 * Using the wrong keys means installed extensions are invisible (D-027).
 *
 * Algorithm:
 * 1. Query [PackageManager] for packages with the `tachiyomi.animeextension` feature.
 * 2. Validate the lib version (parsed from versionName) is in 12.0..16.0.
 * 3. SHA-256 hash the signing certificate → ask [TrustService] if it's trusted.
 * 4. Build a child-first [PathClassLoader] so the extension's bundled deps win.
 * 5. Instantiate each declared source class (or factory).
 * 6. Return a [LoadResult] (Success / Untrusted / Error).
 *
 * CORE_RULES §20: All operations logged with tag "Anikuta:Data:Extension:Loader".
 */
class ExtensionLoader(
    private val context: Context,
    private val trustService: TrustService,
) {

    companion object {
        private const val TAG = "Anikuta:Data:Extension:Loader"

        /** The `<uses-feature>` name that marks a package as an anime extension. */
        private const val EXTENSION_FEATURE = "tachiyomi.animeextension"

        /** Meta-data key listing source class FQCNs (semicolon-separated). */
        private const val METADATA_SOURCE_CLASS = "tachiyomi.animeextension.class"

        /** Meta-data key for the NSFW flag (1 = NSFW). */
        private const val METADATA_NSFW = "tachiyomi.animeextension.nsfw"

        /** Meta-data key for the torrent flag (1 = supports torrents). */
        private const val METADATA_TORRENT = "tachiyomi.animeextension.torrent"

        /** Acceptable source-api library version range (matches Aniyomi). */
        const val LIB_VERSION_MIN = 12.0
        const val LIB_VERSION_MAX = 16.0
    }

    /**
     * Load all installed extensions. Returns a list of [LoadResult] — the caller
     * partitions them into trusted/untrusted/error.
     */
    fun loadAll(): List<LoadResult> {
        Logger.i(TAG) { "Loading all extensions..." }

        val packageManager = context.packageManager
        val flags = packageQueryFlags()

        val installedPkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(flags)
        }

        val extPkgs = installedPkgs.filter { isPackageAnExtension(it) }
        Logger.i(TAG) { "Found ${extPkgs.size} extension packages" }

        return extPkgs.map { pkg -> loadExtension(pkg.packageName) }
    }

    /**
     * Load a single extension by package name.
     */
    fun loadExtension(packageName: String): LoadResult {
        Logger.d(TAG) { "Loading extension: $packageName" }

        val packageInfo = try {
            val flags = packageQueryFlags()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, flags)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Logger.w(TAG) { "Extension package not found: $packageName" }
            return LoadResult.Error(packageName, "Package not found")
        }

        if (!isPackageAnExtension(packageInfo)) {
            return LoadResult.UnrecognizedExtension
        }

        return loadExtensionInternal(packageInfo)
    }

    private fun loadExtensionInternal(packageInfo: PackageInfo): LoadResult {
        val pkgName = packageInfo.packageName
        val appInfo = packageInfo.applicationInfo!!

        val packageManager = context.packageManager
        val rawName = packageManager.getApplicationLabel(appInfo).toString()
        val extName = rawName.substringAfter("Aniyomi: ").substringAfter("Animiru: ")
        val versionName = packageInfo.versionName ?: run {
            Logger.w(TAG) { "Missing versionName for $pkgName" }
            return LoadResult.Error(pkgName, "Missing versionName")
        }
        val versionCode = getLongVersionCode(packageInfo)

        // Parse lib version (best-effort — may be null or out of range for untrusted extensions).
        val libVersion = versionName.substringBeforeLast('.').toDoubleOrNull() ?: 0.0

        // Get signature fingerprint (needed for trust check — must come before lib-version validation).
        val signatureFingerprint = getSignatures(packageInfo)?.firstOrNull()
        if (signatureFingerprint == null) {
            Logger.w(TAG) { "Package $pkgName isn't signed" }
            return LoadResult.Error(pkgName, "Package not signed")
        }

        // Check trust FIRST — untrusted extensions must appear in the untrusted list
        // regardless of lib-version compatibility. The user needs to see them to decide
        // whether to trust or delete. (Was: lib-version check before trust check →
        // extensions with incompatible lib versions were silently dropped as Errors.)
        if (!trustService.isTrusted(signatureFingerprint)) {
            Logger.w(TAG) { "Extension $pkgName is untrusted (fingerprint: $signatureFingerprint)" }
            val icon = runCatching { appInfo.loadIcon(packageManager) }.getOrNull()
            return LoadResult.Untrusted(
                AnimeExtension.Untrusted(
                    name = extName,
                    pkgName = pkgName,
                    versionName = versionName,
                    versionCode = versionCode,
                    libVersion = libVersion,
                    signatureHash = signatureFingerprint,
                    icon = icon,
                )
            )
        }

        // Validate lib version (only for TRUSTED extensions — an incompatible lib version
        // means the extension can't be loaded even if trusted).
        if (libVersion < LIB_VERSION_MIN || libVersion > LIB_VERSION_MAX) {
            Logger.w(TAG) { "Lib version $libVersion out of range for $extName (trusted but incompatible)" }
            return LoadResult.Error(pkgName, "Lib version $libVersion out of range (trusted but incompatible)")
        }

        // Read metadata.
        val isNsfw = appInfo.metaData?.getInt(METADATA_NSFW, 0) == 1
        val isTorrent = appInfo.metaData?.getInt(METADATA_TORRENT, 0) == 1
        val sourceClassName = appInfo.metaData?.getString(METADATA_SOURCE_CLASS)
            ?: run {
                Logger.w(TAG) { "No source class metadata for $pkgName" }
                return LoadResult.Error(pkgName, "No source class metadata")
            }

        // Build a child-first classloader so the extension's bundled deps win.
        val classLoader = try {
            ChildFirstPathClassLoader(appInfo.sourceDir, appInfo.nativeLibraryDir, context.classLoader)
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Failed to create classloader for $pkgName" }
            return LoadResult.Error(pkgName, "Classloader error: ${e.message}")
        }

        // Instantiate sources.
        val sources = sourceClassName.split(";").map { it.trim() }.flatMap { fqcn ->
            val resolved = if (fqcn.startsWith(".")) pkgName + fqcn else fqcn
            instantiateSource(resolved, classLoader, extName)
        }

        if (sources.isEmpty()) {
            Logger.w(TAG) { "No sources instantiated from $pkgName" }
            return LoadResult.Error(pkgName, "No sources instantiated")
        }

        val icon = runCatching { appInfo.loadIcon(packageManager) }.getOrNull()
        val extension = AnimeExtension.Installed(
            name = extName,
            pkgName = pkgName,
            versionName = versionName,
            versionCode = versionCode,
            libVersion = libVersion,
            lang = null,
            isNsfw = isNsfw,
            isTorrent = isTorrent,
            sources = sources,
            icon = icon,
            signatureHash = signatureFingerprint,
        )

        Logger.i(TAG) { "Loaded: ${extension.name} v${extension.versionName} (${sources.size} sources)" }
        return LoadResult.Success(extension)
    }

    /**
     * Check if a package declares the `tachiyomi.animeextension` feature.
     */
    private fun isPackageAnExtension(pkgInfo: PackageInfo): Boolean {
        return pkgInfo.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }
    }

    /**
     * Instantiate a source class. Handles both [AnimeSource] and [AnimeSourceFactory].
     */
    private fun instantiateSource(fqcn: String, classLoader: ClassLoader, extName: String): List<AnimeSource> {
        return try {
            val clazz = Class.forName(fqcn, false, classLoader)
            when {
                AnimeSourceFactory::class.java.isAssignableFrom(clazz) -> {
                    val factory = clazz.getDeclaredConstructor().newInstance() as AnimeSourceFactory
                    factory.createSources()
                }
                AnimeSource::class.java.isAssignableFrom(clazz) -> {
                    listOf(clazz.getDeclaredConstructor().newInstance() as AnimeSource)
                }
                else -> {
                    Logger.w(TAG) { "Class $fqcn in $extName is neither AnimeSource nor AnimeSourceFactory" }
                    emptyList()
                }
            }
        } catch (e: Throwable) {
            // Catch Throwable (not Exception) — binary-incompat throws NoClassDefFoundError (an Error).
            Logger.e(TAG, e) { "Failed to instantiate $fqcn in $extName: ${e.message}" }
            emptyList()
        }
    }

    /**
     * SHA-256 hash the signing certificates. Returns all signatures (history + current).
     */
    private fun getSignatures(pkgInfo: PackageInfo): List<String>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pkgInfo.signingInfo ?: return null
            val certs = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            certs?.map { sha256(it.toByteArray()) }
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.signatures?.map { sha256(it.toByteArray()) }
        }
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun packageQueryFlags(): Int =
        PackageManager.GET_CONFIGURATIONS or
            PackageManager.GET_META_DATA or
            PackageManager.GET_SIGNATURES or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)

    private fun getLongVersionCode(pkgInfo: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkgInfo.longVersionCode
        else @Suppress("DEPRECATION") pkgInfo.versionCode.toLong()
}
