package com.confused.anikuta.backup

import android.content.Context
import com.confused.anikuta.core.backup.AniyomiApkInfo
import com.confused.anikuta.core.backup.BackupFormat
import com.confused.anikuta.core.backup.CloudstreamPluginInfo
import com.confused.anikuta.core.backup.ExtensionBackupBridge
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.preferences.AppPreferences
import com.confused.anikuta.core.providerapi.InstallStep
import com.confused.anikuta.data.cloudstream.installer.CloudstreamPluginInstaller
import com.confused.anikuta.data.cloudstream.repo.CloudstreamPluginStore
import com.confused.anikuta.data.cloudstream.repo.CloudstreamRepoRepository
import com.confused.anikuta.data.cloudstream.repo.CsPluginRecord
import com.confused.anikuta.data.cloudstream.CloudstreamPluginManager
import com.confused.anikuta.data.extension.installer.PackageInstallerBackend
import com.confused.anikuta.data.extension.manager.ExtensionManager
import com.confused.anikuta.data.extension.repo.ExtensionRepoRepository
import com.confused.anikuta.data.extension.trust.TrustService
import org.koin.android.ext.koin.androidContext
import java.io.File

/**
 * The app-layer [ExtensionBackupBridge] — the ONLY place that can see both
 * extension ecosystems and the backup engine. Collects the installed
 * Aniyomi APKs (read from each package's `sourceDir`) and the CloudStream
 * .cs3 files for a backup; on restore it auto-installs the APKs (the user
 * confirms each system prompt through [PackageInstallerBackend]) and writes
 * the .cs3 files + records back with their exact backed-up trust state.
 */
class ExtensionBackupBridgeImpl(
    private val context: Context,
    private val extensionManager: ExtensionManager,
    private val trustService: TrustService,
    private val appPreferences: AppPreferences,
    private val csPluginStore: CloudstreamPluginStore,
    private val extensionRepoRepository: ExtensionRepoRepository,
    private val csRepoRepository: CloudstreamRepoRepository,
    private val csPluginManager: CloudstreamPluginManager,
) : ExtensionBackupBridge {

    private val installerBackend = PackageInstallerBackend(context)

    // ── Collection ──────────────────────────────────────────────────────────

    override fun collectAniyomiApks(): List<ExtensionBackupBridge.AniyomiApkEntry> {
        // The manager's live lists cover trusted-AND-loaded (Installed) and
        // installed-but-untrusted (Untrusted) — both are carried, each with
        // its own trust state, so the restore reproduces the exact setup.
        // ROUND 136: the icon URLs come from the REPO CATALOG (the Installed
        // model carries no icon URL of its own) — an entry the catalog lists
        // gets its icon carried into the offline-image store; sideloaded ones
        // keep the letter-tile fallback.
        val catalogIconByPkg = extensionManager.availableExtensions.value
            .associate { it.pkgName to it.iconUrl }
        val entries = mutableListOf<ExtensionBackupBridge.AniyomiApkEntry>()
        val seen = mutableSetOf<String>()

        extensionManager.installedExtensions.value.forEach { ext ->
            seen += ext.pkgName
            val entry = collectOne(
                pkgName = ext.pkgName,
                name = ext.name,
                versionName = ext.versionName,
                versionCode = ext.versionCode,
                trusted = true,
                enabled = appPreferences.isExtensionEnabled(ext.pkgName),
                iconUrl = catalogIconByPkg[ext.pkgName].orEmpty(),
            )
            if (entry != null) entries += entry
        }
        extensionManager.untrustedExtensions.value.forEach { ext ->
            if (ext.pkgName in seen) return@forEach
            seen += ext.pkgName
            val entry = collectOne(
                pkgName = ext.pkgName,
                name = ext.name,
                versionName = ext.versionName,
                versionCode = ext.versionCode,
                trusted = false,
                enabled = false,
                iconUrl = catalogIconByPkg[ext.pkgName].orEmpty(),
            )
            if (entry != null) entries += entry
        }
        Logger.i(TAG) { "collectAniyomiApks: ${entries.size} APK(s) collected" }
        return entries
    }

    private fun collectOne(
        pkgName: String,
        name: String,
        versionName: String,
        versionCode: Long,
        trusted: Boolean,
        enabled: Boolean,
        iconUrl: String,
    ): ExtensionBackupBridge.AniyomiApkEntry? {
        val apkFile = try {
            val appInfo = context.packageManager.getApplicationInfo(pkgName, 0)
            val path = appInfo.sourceDir
            if (path.isNullOrBlank()) {
                Logger.w(TAG) { "collectAniyomiApks: $pkgName has no sourceDir" }
                return null
            }
            File(path)
        } catch (e: Exception) {
            Logger.w(TAG, e) { "collectAniyomiApks: cannot resolve $pkgName" }
            return null
        }
        val bytes = try {
            apkFile.readBytes()
        } catch (e: Exception) {
            Logger.w(TAG, e) { "collectAniyomiApks: cannot read $pkgName APK" }
            return null
        }
        return ExtensionBackupBridge.AniyomiApkEntry(
            info = AniyomiApkInfo(
                pkgName = pkgName,
                name = name,
                versionName = versionName,
                versionCode = versionCode,
                trusted = trusted,
                enabled = enabled,
                iconUrl = iconUrl,
            ),
            bytes = bytes,
        )
    }

    override fun collectCloudstreamPlugins(): List<ExtensionBackupBridge.CloudstreamPluginEntry> {
        val entries = mutableListOf<ExtensionBackupBridge.CloudstreamPluginEntry>()
        csPluginStore.loadAll().forEach { record ->
            // The record's own absolute path first; the repo-salted derive as
            // the fallback (a record from before a path change still resolves).
            val file = File(record.filePath).takeIf { it.exists() }
                ?: CloudstreamPluginInstaller.pluginPath(
                    context.filesDir,
                    record.internalName,
                    record.repoUrl ?: "",
                ).takeIf { it.exists() }
            if (file == null) {
                // A file-less record MUST NOT be carried: the loader deletes
                // file-less records on load, so restoring one would resurrect
                // a ghost entry.
                Logger.w(TAG) { "collectCloudstreamPlugins: ${record.internalName} file missing — skipped" }
                return@forEach
            }
            val bytes = try {
                file.readBytes()
            } catch (e: Exception) {
                Logger.w(TAG, e) { "collectCloudstreamPlugins: unreadable ${record.internalName}" }
                return@forEach
            }
            entries += ExtensionBackupBridge.CloudstreamPluginEntry(
                info = CloudstreamPluginInfo(
                    internalName = record.internalName,
                    name = record.name,
                    url = record.url,
                    version = record.version,
                    repoUrl = record.repoUrl,
                    repoInternalName = record.repoInternalName,
                    fileHash = record.fileHash,
                    language = record.language,
                    iconUrl = record.iconUrl,
                    isNsfw = record.isNsfw,
                    authors = record.authors,
                    description = record.description,
                    tvTypes = record.tvTypes,
                    fileSizeBytes = record.fileSizeBytes,
                    isTrusted = record.isTrusted,
                ),
                bytes = bytes,
            )
        }
        Logger.i(TAG) { "collectCloudstreamPlugins: ${entries.size} plugin(s) collected" }
        return entries
    }

    // ── Restore ─────────────────────────────────────────────────────────────

    override fun isPackageInstalled(pkgName: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(pkgName, 0) }.isSuccess

    override suspend fun installAniyomiApk(pkgName: String, bytes: ByteArray): Boolean {
        val temp = File(context.cacheDir, "restore-${pkgName}.apk")
        try {
            temp.writeBytes(bytes)
            val step = installerBackend.install(temp, pkgName)
            return step is InstallStep.Installed
        } catch (e: Exception) {
            Logger.e(TAG, e) { "installAniyomiApk: $pkgName failed" }
            return false
        } finally {
            temp.delete()
        }
    }

    override fun applyAniyomiState(pkgName: String, trusted: Boolean, enabled: Boolean) {
        // ROUND 135 (the verifier's F6): the generic prefs pipeline just wrote
        // `trusted_extension_packages` BEHIND the service's back — refresh the
        // in-memory set from the store FIRST, or the next persist() would
        // revert the restored keys.
        trustService.refresh()
        // ADD-only: a restore widens what exists, never revokes.
        if (trusted && !trustService.isTrusted(pkgName)) {
            trustService.trust(pkgName)
        }
        if (enabled && !appPreferences.isExtensionEnabled(pkgName)) {
            appPreferences.enableExtension(pkgName)
        }
    }

    /**
     * ROUND 136 (the device report: "the repositories were not added"): the
     * prefs application writes SharedPreferences BEHIND the repositories'
     * backs — refresh every in-memory store so the Extensions page reflects
     * the restored repos WITHOUT a process restart.
     */
    override fun onPrefsRestored() {
        runCatching { extensionRepoRepository.refresh() }
            .onFailure { Logger.w(TAG, it) { "onPrefsRestored: aniyomi repo refresh failed" } }
        runCatching { csRepoRepository.refresh() }
            .onFailure { Logger.w(TAG, it) { "onPrefsRestored: cs repo refresh failed" } }
        trustService.refresh()
    }

    /**
     * ROUND 136: the .cs3 files + records are on disk — refresh the plugin
     * manager so the restored plugins appear in the Extensions page LIVE.
     * [reloadAfterRestore] (NOT a bare loadAll) keeps the manager's
     * single-worker contract intact and never holds the restore hostage to a
     * slow plugin load.
     */
    override fun onCloudstreamRestored() {
        runCatching { csPluginManager.reloadAfterRestore() }
            .onFailure { Logger.w(TAG, it) { "onCloudstreamRestored: cs reload failed" } }
    }

    override fun restoreCloudstreamPlugin(info: CloudstreamPluginInfo, bytes: ByteArray): Boolean {
        // The DEVICE-LOCAL install path — the backed-up record's absolute
        // filePath belongs to the donor device and is never reused.
        val target = CloudstreamPluginInstaller.pluginPath(
            context.filesDir,
            info.internalName,
            info.repoUrl ?: "",
        )
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.restore-tmp")
        try {
            temp.writeBytes(bytes)
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        } catch (e: Exception) {
            Logger.e(TAG, e) { "restoreCloudstreamPlugin: cannot write ${info.internalName}" }
            temp.delete()
            return false
        }

        // Re-register the record with the new local path + the backed-up
        // trust state (upsert keyed by internalName — an existing record for
        // the same plugin is replaced wholesale).
        val record = CsPluginRecord(
            internalName = info.internalName,
            name = info.name,
            url = info.url,
            filePath = target.absolutePath,
            version = info.version,
            repoUrl = info.repoUrl,
            repoInternalName = info.repoInternalName,
            fileHash = info.fileHash,
            language = info.language,
            iconUrl = info.iconUrl,
            isNsfw = info.isNsfw,
            authors = info.authors,
            description = info.description,
            tvTypes = info.tvTypes,
            fileSizeBytes = info.fileSizeBytes,
            isTrusted = info.isTrusted,
        )
        kotlinx.coroutines.runBlocking {
            csPluginStore.upsert(record)
        }
        Logger.i(TAG) {
            "restoreCloudstreamPlugin: ${info.internalName} → ${target.absolutePath} (trusted=${info.isTrusted})"
        }
        return true
    }

    companion object {
        private const val TAG = "Anikuta:App:BackupBridge"

        /** The state.json schema marker (informational — matches [BackupFormat]). */
        const val STATE_SCHEMA = BackupFormat.FORMAT_VERSION
    }
}

/** The bridge's Koin registration (the app layer — see the interface KDoc). */
val extensionBackupBridgeModule = org.koin.dsl.module {
    single<ExtensionBackupBridge> {
        ExtensionBackupBridgeImpl(
            context = androidContext(),
            extensionManager = get(),
            trustService = get(),
            appPreferences = get(),
            csPluginStore = get(),
            extensionRepoRepository = get(),
            csRepoRepository = get(),
            csPluginManager = get(),
        )
    }
}
