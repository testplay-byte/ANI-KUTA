package com.confused.anikuta.data.extension.manager

import android.content.Context
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.data.extension.api.AnimeExtensionApi
import com.confused.anikuta.data.extension.installer.ExtensionInstallReceiver
import com.confused.anikuta.data.extension.installer.ExtensionInstallService
import com.confused.anikuta.data.extension.installer.ExtensionInstaller
import com.confused.anikuta.data.extension.installer.InstallStep
import com.confused.anikuta.data.extension.loader.ExtensionLoader
import com.confused.anikuta.data.extension.model.AnimeExtension
import com.confused.anikuta.data.extension.model.LoadResult
import com.confused.anikuta.data.extension.trust.TrustService
import eu.kanade.tachiyomi.animesource.AnimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import java.io.File

/**
 * Manages installed extensions and their sources.
 *
 * The central hub for the extension system. Ported from the old project with
 * adaptations for the new project's package names.
 *
 * - Loads installed extensions on app start.
 * - Maintains a registry of all available [AnimeSource] instances.
 * - Exposes reactive state (CORE_RULES §23) so the UI updates when extensions change.
 * - Handles trust verification (untrusted extensions are flagged, not loaded).
 * - Fetches available extensions from configured repos.
 * - Installs/uninstalls extensions via [ExtensionInstaller].
 *
 * CORE_RULES §20: All operations logged with tag "Anikuta:Data:Extension:Manager".
 */
class ExtensionManager(
    private val context: Context,
    private val trustService: TrustService,
    private val api: AnimeExtensionApi,
    val installer: ExtensionInstaller,
    private val okhttpClient: OkHttpClient,
    private val appPreferences: com.confused.anikuta.core.preferences.AppPreferences,
) {

    companion object {
        private const val TAG = "Anikuta:Data:Extension:Manager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val loader = ExtensionLoader(context, trustService)

    // ── Reactive state (CORE_RULES §23 — live updates) ────────────────────────

    private val _installedExtensions = MutableStateFlow<List<AnimeExtension.Installed>>(emptyList())
    val installedExtensions: StateFlow<List<AnimeExtension.Installed>> = _installedExtensions.asStateFlow()

    private val _untrustedExtensions = MutableStateFlow<List<AnimeExtension.Untrusted>>(emptyList())
    val untrustedExtensions: StateFlow<List<AnimeExtension.Untrusted>> = _untrustedExtensions.asStateFlow()

    private val _availableExtensions = MutableStateFlow<List<AnimeExtension.Available>>(emptyList())
    val availableExtensions: StateFlow<List<AnimeExtension.Available>> = _availableExtensions.asStateFlow()

    private val _sources = MutableStateFlow<Map<Long, AnimeSource>>(emptyMap())
    val sources: StateFlow<Map<Long, AnimeSource>> = _sources.asStateFlow()

    /**
     * Per-package install state (CORE_RULES §23 — live updates).
     *
     * Keyed by package name. When an install is in progress, the UI shows a
     * spinner on the download button. Cleared when the install completes (the
     * package-change broadcast triggers a re-scan, which moves the extension
     * to installed/untrusted).
     */
    private val _installStates = MutableStateFlow<Map<String, InstallStep>>(emptyMap())
    val installStates: StateFlow<Map<String, InstallStep>> = _installStates.asStateFlow()

    private val installReceiver = ExtensionInstallReceiver(InstallationListener())

    init {
        // Register the package-change receiver so we re-scan on install/uninstall.
        installReceiver.register(context)
        loadAll()
    }

    // ── Loading ────────────────────────────────────────────────────────────────

    /**
     * Load all installed extensions. Called on app start + after package changes.
     * Runs on Dispatchers.IO to avoid blocking the main thread (PackageManager
     * queries are binder calls — expensive on devices with many packages).
     */
    fun loadAll() {
        scope.launch(Dispatchers.IO) {
            Logger.i(TAG) { "Loading all extensions (background)..." }

            val results = loader.loadAll()

        // Phase DB-OPT (backward compat): if the enabledExtensions set is empty
        // (first launch after the upgrade that introduced per-package enable),
        // seed it with all currently-trusted pkgNames. This prevents existing
        // trusted extensions from being disabled on upgrade — they stay enabled
        // until the user explicitly toggles them.
        if (appPreferences.enabledExtensions.isEmpty()) {
            val trustedPkgs = results.filterIsInstance<LoadResult.Success>()
                .map { it.extension.pkgName }.toSet()
            if (trustedPkgs.isNotEmpty()) {
                appPreferences.enabledExtensions = trustedPkgs
                Logger.i(TAG) { "Seeded enabledExtensions with ${trustedPkgs.size} existing trusted packages (backward compat)" }
            }
        }
        val trusted = mutableListOf<AnimeExtension.Installed>()
        val untrusted = mutableListOf<AnimeExtension.Untrusted>()
        val sourceMap = mutableMapOf<Long, AnimeSource>()

        for (result in results) {
            when (result) {
                is LoadResult.Success -> {
                    // Phase DB-OPT (extension trust fix): mark each Installed with
                    // isEnabled from AppPreferences. Only enabled extensions' sources
                    // are registered into _sources (shown in pickers).
                    val ext = result.extension
                    val enabled = appPreferences.isExtensionEnabled(ext.pkgName)
                    val marked = if (ext.isEnabled != enabled) ext.copy(isEnabled = enabled) else ext
                    trusted.add(marked)
                    if (enabled) {
                        marked.sources.forEach { source ->
                            sourceMap[source.id] = source
                            Logger.d(TAG) { "Registered source: ${source.name} (id=${source.id})" }
                        }
                    } else {
                        Logger.d(TAG) { "Extension ${marked.name} is trusted but DISABLED — sources not registered" }
                    }
                }
                is LoadResult.Untrusted -> {
                    Logger.w(TAG) { "Extension ${result.extension.name} is untrusted" }
                    untrusted.add(result.extension)
                }
                is LoadResult.Error -> {
                    Logger.e(TAG) { "Failed to load ${result.packageName}: ${result.message}" }
                }
                is LoadResult.UnrecognizedExtension -> {
                    // Skip — not a valid extension.
                }
            }
        }

        _installedExtensions.value = trusted
        _untrustedExtensions.value = untrusted
        _sources.value = sourceMap

        // Clear install states for extensions that have now appeared (installed or untrusted).
        val seenPkgs = trusted.map { it.pkgName } + untrusted.map { it.pkgName }
        if (seenPkgs.isNotEmpty()) {
            val cleared = _installStates.value.filterKeys { it !in seenPkgs }
            if (cleared.size != _installStates.value.size) {
                _installStates.value = cleared
            }
        }

        // Recompute hasUpdate/isObsolete on installed extensions.
        updateInstalledStatuses()

        Logger.i(TAG) {
            "Loaded ${trusted.size} trusted (${sourceMap.size} sources), ${untrusted.size} untrusted, ${trusted.count { !it.isEnabled }} disabled"
        }
        } // end scope.launch(Dispatchers.IO)
    }

    /**
     * Fetch available extensions from all configured repos.
     */
    suspend fun findAvailableExtensions() {
        Logger.i(TAG) { "Finding available extensions..." }
        val available = api.findAvailableExtensions()
        _availableExtensions.value = available
        updateInstalledStatuses()
        Logger.i(TAG) { "Found ${available.size} available extensions" }
    }

    /**
     * Recompute hasUpdate/isObsolete on installed extensions based on available list.
     */
    private fun updateInstalledStatuses() {
        val available = _availableExtensions.value
        if (available.isEmpty()) return

        val availableByPkg = available.associateBy { it.pkgName }
        val updated = _installedExtensions.value.map { installed ->
            val av = availableByPkg[installed.pkgName]
            installed.copy(
                hasUpdate = av != null && (av.versionCode > installed.versionCode),
                isObsolete = av == null,
            )
        }
        _installedExtensions.value = updated
    }

    // ── Trust ──────────────────────────────────────────────────────────────────

    /**
     * Trust an untrusted extension and load its sources.
     */
    fun trustExtension(extension: AnimeExtension.Untrusted) {
        Logger.i(TAG) { "Trusting extension: ${extension.name}" }

        extension.signatureHash.let { trustService.trust(it) }
        // Phase DB-OPT: also enable this specific package (per-package control
        // independent of signer-level trust). Other same-signer extensions stay
        // untrusted until the user explicitly trusts them one-by-one.
        appPreferences.enableExtension(extension.pkgName)

        // Remove from untrusted.
        _untrustedExtensions.value = _untrustedExtensions.value.filter { it.pkgName != extension.pkgName }

        // Re-load the extension to get its sources.
        val result = loader.loadExtension(extension.pkgName)
        if (result is LoadResult.Success) {
            val installed = result.extension.copy(isEnabled = true)
            _installedExtensions.value = _installedExtensions.value + installed
            val sourceMap = _sources.value.toMutableMap()
            installed.sources.forEach { source ->
                sourceMap[source.id] = source
            }
            _sources.value = sourceMap
        }
    }

    /**
     * Revoke trust for an extension (moves it back to untrusted).
     */
    fun untrustExtension(extension: AnimeExtension.Installed) {
        Logger.i(TAG) { "Untrusting: ${extension.name} (fingerprint: ${extension.signatureHash})" }
        // Actually revoke trust by fingerprint — without this, loadAll() would
        // re-trust the same extension.
        if (extension.signatureHash.isNotEmpty()) {
            trustService.revoke(extension.signatureHash)
        }
        // Phase DB-OPT: also remove from enabled set (per-package).
        appPreferences.disableExtension(extension.pkgName)

        // Remove from installed + remove its sources.
        _installedExtensions.value = _installedExtensions.value.filter { it.pkgName != extension.pkgName }
        val sourceMap = _sources.value.toMutableMap()
        extension.sources.forEach { source -> sourceMap.remove(source.id) }
        _sources.value = sourceMap

        // Reload to populate the untrusted list with this extension.
        loadAll()
    }

    // ── Phase DB-OPT (extension trust fix): per-package enable/disable ──────────

    /**
     * Enable an installed extension's sources (without re-trusting — the signer
     * must already be trusted). Adds the package to the enabled set + registers
     * its sources into [_sources]. The extension stays in _installedExtensions.
     */
    fun enableExtension(pkgName: String) {
        Logger.i(TAG) { "Enabling extension: $pkgName" }
        appPreferences.enableExtension(pkgName)
        val ext = _installedExtensions.value.find { it.pkgName == pkgName } ?: return
        if (ext.isEnabled) return
        _installedExtensions.value = _installedExtensions.value.map {
            if (it.pkgName == pkgName) it.copy(isEnabled = true) else it
        }
        val sourceMap = _sources.value.toMutableMap()
        ext.sources.forEach { source -> sourceMap[source.id] = source }
        _sources.value = sourceMap
    }

    /**
     * Disable an installed extension's sources (without untrusting — the signer
     * stays trusted, the extension stays loaded, but its sources are removed
     * from [_sources] so they don't appear in pickers).
     */
    fun disableExtension(pkgName: String) {
        Logger.i(TAG) { "Disabling extension: $pkgName" }
        appPreferences.disableExtension(pkgName)
        val ext = _installedExtensions.value.find { it.pkgName == pkgName } ?: return
        if (!ext.isEnabled) return
        _installedExtensions.value = _installedExtensions.value.map {
            if (it.pkgName == pkgName) it.copy(isEnabled = false) else it
        }
        val sourceMap = _sources.value.toMutableMap()
        ext.sources.forEach { source -> sourceMap.remove(source.id) }
        _sources.value = sourceMap
    }

    // ── Install / Uninstall ────────────────────────────────────────────────────

    /**
     * Install an available extension. Returns a flow of [InstallStep].
     * Also tracks state in [_installStates] so the UI can show a spinner.
     */
    fun installExtension(extension: AnimeExtension.Available): Flow<InstallStep> {
        val apkUrl = api.getApkUrl(extension)
        return flow {
            installMutex.withLock {
                setInstallState(extension.pkgName, InstallStep.Pending)
                emit(InstallStep.Pending)

                // Download
                setInstallState(extension.pkgName, InstallStep.Downloading)
                emit(InstallStep.Downloading)
                val tempFile = File(context.cacheDir, "ext-${extension.pkgName}-${extension.apkName}")
                val downloaded = downloadApk(apkUrl, tempFile)
                if (!downloaded) {
                    tempFile.delete()
                    setInstallState(extension.pkgName, InstallStep.Error)
                    emit(InstallStep.Error)
                    return@withLock
                }

                // Dispatch to install service
                setInstallState(extension.pkgName, InstallStep.Installing)
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
                // Terminal state (Installed/Error) arrives via the package-change
                // broadcast → loadAll() re-scan. Clear the install state when the
                // extension appears in the installed/untrusted list.
            }
        }.flowOn(Dispatchers.IO)
    }

    private val installMutex = kotlinx.coroutines.sync.Mutex()

    private fun setInstallState(pkgName: String, step: InstallStep) {
        _installStates.value = _installStates.value + (pkgName to step)
    }

    private suspend fun downloadApk(url: String, dest: File): Boolean {
        return runCatching {
            dest.parentFile?.mkdirs()
            val response = okhttpClient.newCall(
                okhttp3.Request.Builder().url(url).build()
            ).execute()
            if (!response.isSuccessful) {
                Logger.e(TAG) { "Download failed: HTTP ${response.code}" }
                return false
            }
            response.body?.byteStream()?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return false
            true
        }.getOrElse { e ->
            Logger.e(TAG, e) { "Download failed" }
            false
        }
    }

    /**
     * Uninstall an extension.
     */
    fun uninstallExtension(extension: AnimeExtension) {
        installer.uninstallApk(extension.pkgName)
    }

    // ── Source lookup ──────────────────────────────────────────────────────────

    fun getSource(id: Long): AnimeSource? = _sources.value[id]

    fun getAllSources(): List<AnimeSource> = _sources.value.values.toList()

    // ── Reload ─────────────────────────────────────────────────────────────────

    fun reload() {
        Logger.i(TAG) { "Reloading extensions..." }
        loadAll()
    }

    // ── Install receiver listener ──────────────────────────────────────────────

    private inner class InstallationListener : ExtensionInstallReceiver.Listener {
        override fun onPackageChanged(pkgName: String) {
            Logger.i(TAG) { "Package changed: $pkgName — re-scanning" }
            loadAll()
        }
    }
}
