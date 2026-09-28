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
 * 2. SHA-256 hash the signing certificate → ask [TrustService] if it's trusted.
 * 3. Build a **parent-first** [PathClassLoader] (exactly like the reference Aniyomi)
 *    so the host's kotlin-stdlib / okhttp / rx / source-api always win; the extension
 *    APK only supplies classes the host does NOT have (its own source classes,
 *    bundled extractors, apache-commons, keiyoushi/utils, multisrc themes, …).
 * 4. Validate the lib version (parsed from versionName) — informational only;
 *    D-297: out-of-range versions are still ATTEMPTED (the failure mode is a
 *    visible [LoadResult.Error], never a silent drop).
 * 5. Instantiate each declared source class (or factory).
 * 6. Return a [LoadResult] (Success / Untrusted / Error).
 *
 * D-294 (root fix for "extensions disappear after trust"): the previous
 * child-first classloader let an extension's PARTIAL bundled kotlin-stdlib
 * shadow the host's complete stdlib — a mixed-stdlib class-identity breakage
 * that threw during source instantiation. Reference Aniyomi is parent-first;
 * with parent-first the bundled kotlin classes are inert dead weight and the
 * extension resolves the host's (binary-compatible) stdlib instead. Verified
 * against the sb-extensions-source template family (moviebox/anikoto-v16 etc.)
 * which bundle kotlin 2.0.x partials while the app ships kotlin 2.2.0.
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

        // ── ROUND 101 (WS-A): manifest metadata is read BEFORE the trust gate ──
        // The nsfw/torrent flags live in the APK manifest (no class loading
        // needed), so UNTRUSTED rows can carry them too. Before this change the
        // Untrusted model defaulted to isNsfw=false / lang=null, which made the
        // extensions screen's language filter EMPTY the whole untrusted section
        // (every row failed `ext.lang == langFilter` on a null lang) instead of
        // filtering it — the user's "the filters never get applied to the
        // untrusted ones" report. The lang itself comes from the package-name
        // convention (see [parseLangFromPackageName]); the screen additionally
        // enriches untrusted rows from the repo catalog when one matches.
        val isNsfw = appInfo.metaData?.getInt(METADATA_NSFW, 0) == 1
        val isTorrent = appInfo.metaData?.getInt(METADATA_TORRENT, 0) == 1

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
                    // ROUND 101 (WS-A): the untrusted row now participates in the
                    // language + NSFW filters exactly like trusted/available rows.
                    lang = parseLangFromPackageName(pkgName),
                    isNsfw = isNsfw,
                    isTorrent = isTorrent,
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

        // Read the source class list (trusted path — the untrusted early-return
        // above never needs it; classes are NOT loaded for untrusted packages).
        val sourceClassName = appInfo.metaData?.getString(METADATA_SOURCE_CLASS)
            ?: run {
                Logger.w(TAG) { "No source class metadata for $pkgName" }
                return LoadResult.Error(pkgName, "No source class metadata")
            }

        // D-294: parent-first PathClassLoader — EXACTLY like the reference Aniyomi.
        // The extension APK supplies only classes the host lacks; the host's
        // kotlin-stdlib / okhttp / source-api always win. (The old child-first loader
        // shadowed the host stdlib with the extension's partial bundled copy →
        // mixed-stdlib breakage → "extension disappears after trust".)
        val classLoader = try {
            PathClassLoader(appInfo.sourceDir, appInfo.nativeLibraryDir, context.classLoader)
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Failed to create classloader for $pkgName" }
            return LoadResult.Error(pkgName, "Classloader error: ${e.message}", extName)
        }

        // Instantiate sources. D-295: collect per-class failures so the Error result
        // carries the REAL reason (exception class + message) instead of a generic
        // "No sources instantiated".
        val sources = mutableListOf<AnimeSource>()
        val failures = mutableListOf<String>()
        sourceClassName.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { fqcn ->
            val resolved = if (fqcn.startsWith(".")) pkgName + fqcn else fqcn
            when (val result = instantiateSource(resolved, classLoader, extName)) {
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

    /**
     * ROUND 101 (WS-A): best-effort language parse from the extension's package
     * name — the ONLY local source of an untrusted extension's language (its
     * classes are never loaded, so `AnimeSource.lang` is unreachable by design).
     *
     * Two conventions are handled:
     * 1. The upstream Aniyomi scheme: `eu.kanade.tachiyomi.animeextension.<lang>.<id>`
     *    (multi-language extensions use the reserved `all` tag).
     * 2. The short custom-repo scheme used by the sb-extensions family:
     *    `aniyomi-<lang>.<name>` (e.g. `aniyomi-en.anikoto`).
     *
     * Returns null when neither matches — the caller (the extensions screen)
     * then falls back to a repo-catalog join, which is the authoritative source
     * whenever the package is listed in a configured repository.
     */
    private fun parseLangFromPackageName(pkgName: String): String? {
        val parts = pkgName.split('.')
        // Convention 1: the segment after the extension marker (which must NOT be
        // the last segment — a language is always followed by the extension id).
        val marker = parts.indexOfLast {
            it == "animeextension" || it == "animeextensions" || it == "animiruextension"
        }
        if (marker in 0 until parts.size - 2) {
            parts[marker + 1].takeIf(::isPlausibleLanguageTag)?.let { return it }
        }
        // Convention 2: the first segment carries "aniyomi-<lang>" / "animiru-<lang>".
        // P7-G1 fix (sub-agent-verified): the match is case-insensitive but the
        // old removePrefix was not — "Aniyomi-EN.x" silently failed. The strip
        // now uses the matched prefix's LENGTH + lowercases the result so the
        // parsed tag always matches the catalog's casing.
        val first = parts.firstOrNull() ?: return null
        for (prefix in listOf("aniyomi-", "animiru-")) {
            if (first.length > prefix.length && first.startsWith(prefix, ignoreCase = true)) {
                first.substring(prefix.length).lowercase().takeIf(::isPlausibleLanguageTag)?.let { return it }
            }
        }
        return null
    }

    /** A plausible IETF-ish tag: short, ASCII letters only ("en", "all", "ptBR"). */
    private fun isPlausibleLanguageTag(candidate: String): Boolean =
        candidate.isNotEmpty() && candidate.length <= 8 && candidate.all { it.isLetter() }

    /** Result of instantiating one declared source class. */
    private sealed interface SourceInstantiation {
        data class Success(val sources: List<AnimeSource>) : SourceInstantiation
        data class Failure(val reason: String) : SourceInstantiation
    }

    /**
     * Instantiate a source class. Handles both [AnimeSource] and [AnimeSourceFactory].
     * D-295: failures return the exception class + message so callers can surface
     * the actual cause to the user.
     */
    private fun instantiateSource(fqcn: String, classLoader: ClassLoader, extName: String): SourceInstantiation {
        return try {
            val clazz = Class.forName(fqcn, false, classLoader)
            when {
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
        } catch (e: Throwable) {
            // Catch Throwable (not Exception) — binary-incompat throws NoClassDefFoundError (an Error).
            Logger.e(TAG, e) { "Failed to instantiate $fqcn in $extName: ${e.message}" }
            SourceInstantiation.Failure(
                "$fqcn: ${e.javaClass.simpleName}${if (!e.message.isNullOrBlank()) ": ${e.message}" else ""}"
            )
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
