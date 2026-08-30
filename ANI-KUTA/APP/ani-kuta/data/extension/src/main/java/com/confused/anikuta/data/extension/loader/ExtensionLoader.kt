package com.confused.anikuta.data.extension.loader

import android.content.Context
import android.content.pm.ApplicationInfo
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
 * 2. SHA-256 hash the signing certificate → ask [TrustService] if it's trusted.
 * 3. Instantiate each declared source class (or factory) through a HYBRID
 *    loader policy (Task 50 / round-10 Fix A): child-first via
 *    [ChildFirstPathClassLoader] — the extension's bundled
 *    kotlinx-serialization / kotlinx-coroutines win for its own code, while
 *    boundary types (source API, kotlin stdlib, network stack) are pinned
 *    parent-first — with a per-class parent-first [PathClassLoader] fallback
 *    on [LinkageError] (the old-kuta retry).
 * 4. Validate the lib version (parsed from versionName) — informational only;
 *    D-297: out-of-range versions are still ATTEMPTED (the failure mode is a
 *    visible [LoadResult.Error], never a silent drop).
 * 5. Return a [LoadResult] (Success / Untrusted / Error).
 *
 * Loader history (D-294 → Task 50 / round-10 Fix A): D-294 (v0.2.57) replaced
 * the original child-first loader with unconditional parent-first to stop a
 * mixed-stdlib crash at INSTANTIATION time ("extension disappears after
 * trust"). That worked, but broke RESOLUTION for extensions that bundle
 * kotlinx-serialization 1.x: under parent-first the host's 2.x classes win at
 * class-resolution and extension code then hits NoSuchMethodError /
 * NoClassDefFoundError while browse/search kept working (FM-1 — "some
 * episodes don't resolve"). Round 10 restores the old-kuta hybrid
 * (child-first with a parent-first retry per class — which worked for this
 * user) hardened with parent-first exclusion prefixes old-kuta lacked.
 * Invariant keeping the "kotlin." exclusion safe: the host's Kotlin (2.2.0
 * today) must stay ≥ any extension-bundled stdlib (2.0.x today) — see
 * [ChildFirstPathClassLoader] for the full rationale.
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

        /** Source-api library version we KNOW we are compatible with (lib-17 APIs
         *  exist in :core:source-api — server, getVideoThumbnails, getImageTile).
         *  D-297: versions outside the range are still ATTEMPTED — this is the
         *  documented/known-good range, not a hard gate. */
        const val LIB_VERSION_MIN = 12.0
        const val LIB_VERSION_MAX = 17.0
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

        // Get signature fingerprint (needed for security — extension must be signed).
        val signatureFingerprint = getSignatures(packageInfo)?.firstOrNull()
        if (signatureFingerprint == null) {
            Logger.w(TAG) { "Package $pkgName isn't signed" }
            return LoadResult.Error(pkgName, "Package not signed")
        }

        // Phase 3: Check trust PER-PACKAGE (not per-signer). The old by-signer model
        // caused auto-propagation: trusting one extension auto-trusted ALL same-signer
        // extensions. Now trust is stored by pkgName — each extension is trusted independently.
        if (!trustService.isTrusted(pkgName)) {
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
        // D-297: no hard rejection — versions outside the known-good range are still
        // attempted below; if instantiation fails the user sees a visible Errored row
        // with the reason instead of the extension silently vanishing.
        if (libVersion < LIB_VERSION_MIN || libVersion > LIB_VERSION_MAX) {
            Logger.w(TAG) {
                "Lib version $libVersion outside known-good range ${LIB_VERSION_MIN}..${LIB_VERSION_MAX} " +
                    "for $extName (trusted) — attempting load anyway"
            }
        }

        // Read metadata.
        val isNsfw = appInfo.metaData?.getInt(METADATA_NSFW, 0) == 1
        val isTorrent = appInfo.metaData?.getInt(METADATA_TORRENT, 0) == 1
        val sourceClassName = appInfo.metaData?.getString(METADATA_SOURCE_CLASS)
            ?: run {
                Logger.w(TAG) { "No source class metadata for $pkgName" }
                return LoadResult.Error(pkgName, "No source class metadata")
            }

        // Task 50 (round-10 Fix A): hybrid loader policy — PARTIAL reversal of
        // D-294's unconditional parent-first (see the class KDoc for the history).
        // One child-first loader per extension, created lazily so the DEX is only
        // opened when a source class is actually instantiated; every fqcn of this
        // extension shares it (shared loader = one Class identity per extension;
        // D-294's single-loader identity behavior is preserved). Loader
        // construction failures surface per-class below with the REAL reason
        // (D-295) — a corrupt DEX fails both strategies identically.
        val childFirstLoader = lazy {
            ChildFirstPathClassLoader(appInfo.sourceDir, appInfo.nativeLibraryDir, context.classLoader)
        }

        // Instantiate sources. D-295: collect per-class failures so the Error result
        // carries the REAL reason (exception class + message) instead of a generic
        // "No sources instantiated".
        val sources = mutableListOf<AnimeSource>()
        val failures = mutableListOf<String>()
        sourceClassName.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { fqcn ->
            val resolved = if (fqcn.startsWith(".")) pkgName + fqcn else fqcn
            when (val result = instantiateSource(resolved, appInfo, extName, childFirstLoader)) {
                is SourceInstantiation.Success -> sources.addAll(result.sources)
                is SourceInstantiation.Failure -> failures.add(result.reason)
            }
        }

        if (sources.isEmpty()) {
            val reason = if (failures.isNotEmpty()) {
                failures.joinToString("; ")
            } else {
                "No source classes declared"
            }
            Logger.w(TAG) { "No sources instantiated from $pkgName: $reason" }
            return LoadResult.Error(pkgName, reason, extName)
        }

        val icon = runCatching { appInfo.loadIcon(packageManager) }.getOrNull()
        // D-298: populate lang from the instantiated sources (was always null —
        // the language filter depends on it).
        val lang = sources.map { it.lang }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }?.key
            ?: sources.firstOrNull()?.lang?.takeIf { it.isNotBlank() }
        val extension = AnimeExtension.Installed(
            name = extName,
            pkgName = pkgName,
            versionName = versionName,
            versionCode = versionCode,
            libVersion = libVersion,
            lang = lang,
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

    /** Result of instantiating one declared source class. */
    private sealed interface SourceInstantiation {
        data class Success(val sources: List<AnimeSource>) : SourceInstantiation
        data class Failure(val reason: String) : SourceInstantiation
    }

    /**
     * Instantiate a source class under the hybrid loader policy
     * (Task 50 / round-10 Fix A). Handles both [AnimeSource] and
     * [AnimeSourceFactory]. Strategy per fqcn:
     * 1. Try the extension's shared [ChildFirstPathClassLoader] (child-first,
     *    boundary types excluded — see its KDoc).
     * 2. On [LinkageError] (class load OR instantiation — includes
     *    NoClassDefFoundError, VerifyError, ExceptionInInitializerError, …),
     *    retry the SAME fqcn with a plain parent-first [PathClassLoader]
     *    (the old-kuta fallback: some extensions only link against the host's
     *    copies of everything).
     * 3. Any other failure, or a parent-first retry failure, returns
     *    [SourceInstantiation.Failure]. D-295: failures carry the exception
     *    class + message so callers can surface the actual cause to the user.
     *
     * @param fqcn             fully-qualified source class name (already package-resolved).
     * @param appInfo          the extension's [android.content.pm.ApplicationInfo]
     *                         (sourceDir/nativeLibraryDir for the parent-first fallback loader).
     * @param extName          display name (for logs).
     * @param childFirstLoader lazily-created child-first loader shared by all
     *                         fqcn of this extension (one Class identity per extension).
     */
    private fun instantiateSource(
        fqcn: String,
        appInfo: ApplicationInfo,
        extName: String,
        childFirstLoader: Lazy<ClassLoader>,
    ): SourceInstantiation {
        // Attempt 1: hybrid child-first loader.
        try {
            val result = instantiateWithLoader(fqcn, childFirstLoader.value, extName)
            if (result is SourceInstantiation.Success) {
                Logger.d(TAG) { "child-first strategy won for $fqcn in $extName" }
            }
            return result
        } catch (e: LinkageError) {
            // The extension's own classes fail to link (partial bundled stdlib,
            // verify errors, …). The old app retried the same class parent-first.
            Logger.w(TAG) {
                "child-first failed for $fqcn in $extName " +
                    "(${e.javaClass.simpleName}${if (!e.message.isNullOrBlank()) ": ${e.message}" else ""}), " +
                    "retrying parent-first"
            }
        } catch (e: Throwable) {
            // Non-linkage failure (constructor exception, wrong type, loader
            // construction failure, …) — a different delegation order cannot
            // help. Catch Throwable (not Exception) — binary-incompat throws
            // NoClassDefFoundError (an Error), although that is handled above.
            Logger.e(TAG, e) { "Failed to instantiate $fqcn in $extName: ${e.message}" }
            return SourceInstantiation.Failure(
                "$fqcn: ${e.javaClass.simpleName}${if (!e.message.isNullOrBlank()) ": ${e.message}" else ""}"
            )
        }

        // Attempt 2: plain parent-first PathClassLoader for the SAME fqcn
        // (created on demand — this path is rare).
        return try {
            val result = instantiateWithLoader(
                fqcn,
                PathClassLoader(appInfo.sourceDir, appInfo.nativeLibraryDir, context.classLoader),
                extName,
            )
            if (result is SourceInstantiation.Success) {
                Logger.i(TAG) { "parent-first fallback won for $fqcn in $extName" }
            }
            result
        } catch (e: Throwable) {
            // Catch Throwable (not Exception) — binary-incompat throws NoClassDefFoundError (an Error).
            Logger.e(TAG, e) { "Failed to instantiate $fqcn in $extName (parent-first retry): ${e.message}" }
            SourceInstantiation.Failure(
                "$fqcn: ${e.javaClass.simpleName}${if (!e.message.isNullOrBlank()) ": ${e.message}" else ""}"
            )
        }
    }

    /**
     * Load [fqcn] with [classLoader] and instantiate it as an [AnimeSource]
     * or [AnimeSourceFactory]. Throws whatever the classload/instantiation
     * throws (including [LinkageError]) so the caller can pick the retry
     * strategy; only type-mismatch returns a [SourceInstantiation.Failure].
     */
    private fun instantiateWithLoader(fqcn: String, classLoader: ClassLoader, extName: String): SourceInstantiation {
        val clazz = Class.forName(fqcn, false, classLoader)
        return when {
            AnimeSourceFactory::class.java.isAssignableFrom(clazz) -> {
                val factory = clazz.getDeclaredConstructor().newInstance() as AnimeSourceFactory
                SourceInstantiation.Success(factory.createSources())
            }
            AnimeSource::class.java.isAssignableFrom(clazz) -> {
                SourceInstantiation.Success(listOf(clazz.getDeclaredConstructor().newInstance() as AnimeSource))
            }
            else -> {
                Logger.w(TAG) { "Class $fqcn in $extName is neither AnimeSource nor AnimeSourceFactory" }
                SourceInstantiation.Failure("$fqcn is not an AnimeSource")
            }
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
