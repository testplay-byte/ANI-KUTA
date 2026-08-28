package com.confused.anikuta.data.extension.manager

import android.content.Context
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.data.extension.api.AnimeExtensionApi
import com.confused.anikuta.data.extension.installer.ExtensionInstallReceiver
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
    private val appPreferences: com.confused.anikuta.core.preferences.AppPreferences,
) {

    companion object {
        private const val TAG = "Anikuta:Data:Extension:Manager"

        /** D-301: auto update-check throttle — at most one check per 30 minutes. */
        private const val UPDATE_CHECK_THROTTLE_MS = 30L * 60 * 1000
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val loader = ExtensionLoader(context, trustService)

    // ── Reactive state (CORE_RULES §23 — live updates) ────────────────────────

    private val _installedExtensions = MutableStateFlow<List<AnimeExtension.Installed>>(emptyList())
    val installedExtensions: StateFlow<List<AnimeExtension.Installed>> = _installedExtensions.asStateFlow()

    private val _untrustedExtensions = MutableStateFlow<List<AnimeExtension.Untrusted>>(emptyList())
    val untrustedExtensions: StateFlow<List<AnimeExtension.Untrusted>> = _untrustedExtensions.asStateFlow()

    /** D-296: trusted-but-failed-to-load extensions — VISIBLE, never silently dropped. */
    private val _erroredExtensions = MutableStateFlow<List<AnimeExtension.Errored>>(emptyList())
    val erroredExtensions: StateFlow<List<AnimeExtension.Errored>> = _erroredExtensions.asStateFlow()

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
        val errored = mutableListOf<AnimeExtension.Errored>()
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
                    // D-296: surface load failures instead of dropping them — the
                    // user sees a "Failed to Load" row with the reason + Retry.
                    Logger.e(TAG) { "Failed to load ${result.packageName}: ${result.message}" }
                    errored.add(result.toErrored())
                }
                is LoadResult.UnrecognizedExtension -> {
                    // Skip — not a valid extension.
                }
            }
        }

        _installedExtensions.value = trusted
        _untrustedExtensions.value = untrusted
        _erroredExtensions.value = errored
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
     *
     * D-296: if the load FAILS the extension now lands in [_erroredExtensions]
     * (visible "Failed to Load" row with the reason) instead of silently
     * vanishing from every list.
     */
    fun trustExtension(extension: AnimeExtension.Untrusted) {
        Logger.i(TAG) { "Trusting extension: ${extension.name} (pkg: ${extension.pkgName})" }

        // Phase 3: trust is PER-PACKAGE (not per-signer). Only this specific
        // package gets trusted — other same-signer extensions stay untrusted.
        trustService.trust(extension.pkgName)
        appPreferences.enableExtension(extension.pkgName)

        // Remove from untrusted.
        _untrustedExtensions.value = _untrustedExtensions.value.filter { it.pkgName != extension.pkgName }

        // Re-load the extension to get its sources. Classloading can take a few
        // hundred ms — run off the main thread.
        scope.launch(Dispatchers.Default) {
            applyLoadResult(loader.loadExtension(extension.pkgName))
        }
    }

    /**
     * D-296: apply a single-extension load result to the reactive state. Shared by
     * [trustExtension] + [retryExtension]. Never drops a result silently.
     */
    private fun applyLoadResult(result: LoadResult) {
        when (result) {
            is LoadResult.Success -> {
                val installed = result.extension.copy(isEnabled = true)
                _installedExtensions.value = _installedExtensions.value
                    .filter { it.pkgName != installed.pkgName } + installed
                _erroredExtensions.value = _erroredExtensions.value.filter { it.pkgName != installed.pkgName }
                val sourceMap = _sources.value.toMutableMap()
                installed.sources.forEach { source ->
                    sourceMap[source.id] = source
                }
                _sources.value = sourceMap
                updateInstalledStatuses()
            }
            is LoadResult.Error -> {
                Logger.e(TAG) { "Extension ${result.packageName} failed to load: ${result.message}" }
                val errored = result.toErrored()
                _erroredExtensions.value = _erroredExtensions.value
                    .filter { it.pkgName != errored.pkgName } + errored
                // Make sure it's not lingering in the installed list either.
                _installedExtensions.value = _installedExtensions.value.filter { it.pkgName != errored.pkgName }
            }
            is LoadResult.Untrusted -> {
                // Shouldn't happen (trust was just granted) — but never drop it.
                _untrustedExtensions.value = _untrustedExtensions.value + result.extension
            }
            is LoadResult.UnrecognizedExtension -> Unit
        }
    }

    /**
     * D-296: retry loading a previously-errored extension (e.g. after the user
     * updated the app / cleared state, or just to re-attempt).
     */
    fun retryExtension(extension: AnimeExtension.Errored) {
        Logger.i(TAG) { "Retrying extension: ${extension.name} (pkg: ${extension.pkgName})" }
        scope.launch(Dispatchers.Default) {
            applyLoadResult(loader.loadExtension(extension.pkgName))
        }
    }

    /** Convert a loader [LoadResult.Error] into the UI-facing [AnimeExtension.Errored]. */
    private fun LoadResult.Error.toErrored(): AnimeExtension.Errored = AnimeExtension.Errored(
        name = name,
        pkgName = packageName,
        versionName = "",
        versionCode = 0L,
        libVersion = 0.0,
        message = message,
    )

    /**
     * Revoke trust for an extension (moves it back to untrusted).
     * Also accepts [AnimeExtension.Errored] rows (D-296) — an extension that
     * failed to load can still be untrusted again.
     */
    fun untrustExtension(extension: AnimeExtension) {
        Logger.i(TAG) { "Untrusting: ${extension.name} (pkg: ${extension.pkgName})" }
        // Phase 3: revoke trust PER-PACKAGE. Only this extension gets untrusted —
        // other same-signer extensions are unaffected.
        trustService.revoke(extension.pkgName)
        appPreferences.disableExtension(extension.pkgName)

        // Remove from installed + errored + remove its sources.
        _installedExtensions.value = _installedExtensions.value.filter { it.pkgName != extension.pkgName }
        _erroredExtensions.value = _erroredExtensions.value.filter { it.pkgName != extension.pkgName }
        val sourceMap = _sources.value.toMutableMap()
        (extension as? AnimeExtension.Installed)?.sources?.forEach { source -> sourceMap.remove(source.id) }
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

    // ── Phase 4: Per-source enable/disable ──────────────────────────────────────

    /** Enable a single source (by source ID) in the _sources map. */
    fun enableSource(sourceId: Long) {
        Logger.i(TAG) { "Enabling source: $sourceId" }
        val ext = _installedExtensions.value.find { ext -> ext.sources.any { it.id == sourceId } } ?: return
        val source = ext.sources.find { it.id == sourceId } ?: return
        val sourceMap = _sources.value.toMutableMap()
        sourceMap[sourceId] = source
        _sources.value = sourceMap
    }

    /** Disable a single source (by source ID) from the _sources map. */
    fun disableSource(sourceId: Long) {
        Logger.i(TAG) { "Disabling source: $sourceId" }
        val sourceMap = _sources.value.toMutableMap()
        sourceMap.remove(sourceId)
        _sources.value = sourceMap
    }

    // ── Install / Uninstall ────────────────────────────────────────────────────

    /**
     * Install an available extension. Returns a flow of [InstallStep].
     * Also tracks state in [_installStates] so the UI can show a spinner.
     *
     * D-300: delegates the actual download+install to [ExtensionInstaller] — the
     * single canonical install path (this manager previously had a near-duplicate
     * copy of the whole download+service-dispatch pipeline).
     */
    fun installExtension(extension: AnimeExtension.Available): Flow<InstallStep> {
        val apkUrl = api.getApkUrl(extension)
        return flow {
            installMutex.withLock {
                setInstallState(extension.pkgName, InstallStep.Pending)
                try {
                    installer.downloadAndInstall(apkUrl, extension)
                        .collect { step ->
                            setInstallState(extension.pkgName, step)
                            emit(step)
                        }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // D-309 review fix: the collector's scope died (user left the
                    // screen mid-download) — without this the row sticks on a
                    // frozen Downloading(x%) forever (the OS broadcast never fires
                    // for an aborted download).
                    setInstallState(extension.pkgName, InstallStep.Idle)
                    throw e
                }
            }
        }.flowOn(Dispatchers.IO)
    }

    private val installMutex = kotlinx.coroutines.sync.Mutex()

    private fun setInstallState(pkgName: String, step: InstallStep) {
        _installStates.value = _installStates.value + (pkgName to step)
    }

    /**
     * D-309 review fix: terminal install results reported by
     * [ExtensionInstallService] — the OS-prompt-DENIED (user aborted → Idle) and
     * PackageInstaller-failure paths never fire the PACKAGE_ADDED broadcast,
     * so without this the row would stick on a pulsing "Installing" forever.
     *
     * D-311 (post-update refresh system): on SUCCESS the manager now also
     * triggers a [loadAll] re-scan IMMEDIATELY — the row's version text,
     * `hasUpdate` flag, and install state all refresh at once instead of
     * waiting for the (racy, later-arriving) PACKAGE_ADDED broadcast. This is
     * what lets the UI settle on "new version, no Update pill" cleanly; the
     * UI-side INSTALLED phase covers the brief window in between.
     */
    fun onInstallResult(pkgName: String, step: InstallStep) {
        if (step is InstallStep.Installed || step is InstallStep.Error || step is InstallStep.Idle) {
            setInstallState(pkgName, step)
            if (step is InstallStep.Installed) {
                Logger.i(TAG) { "Install succeeded for $pkgName — triggering post-install refresh (loadAll)" }
                loadAll()
            }
        }
    }

    /**
     * Uninstall an extension.
     */
    fun uninstallExtension(extension: AnimeExtension) {
        installer.uninstallApk(extension.pkgName)
    }

    // ── D-301: update checking ─────────────────────────────────────────────

    /** State of an on-demand update check (for subtle UI indication). */
    enum class UpdateCheckState { Idle, Checking }

    private val _updateCheckState = MutableStateFlow(UpdateCheckState.Idle)
    val updateCheckState: StateFlow<UpdateCheckState> = _updateCheckState.asStateFlow()

    @Volatile
    private var lastUpdateCheckAtMs: Long = 0L

    /**
     * D-301: auto update-check used when the user enters the extensions page.
     * Throttled to once per [UPDATE_CHECK_THROTTLE_MS] — repeated page entries
     * within the window are no-ops ("smoothly", no network hammering).
     */
    fun checkForUpdates(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastUpdateCheckAtMs < UPDATE_CHECK_THROTTLE_MS) {
            Logger.d(TAG) { "Update check throttled (last ${now - lastUpdateCheckAtMs}ms ago)" }
            return
        }
        lastUpdateCheckAtMs = now
        scope.launch(Dispatchers.IO) {
            _updateCheckState.value = UpdateCheckState.Checking
            try {
                findAvailableExtensions()
            } finally {
                _updateCheckState.value = UpdateCheckState.Idle
            }
        }
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
