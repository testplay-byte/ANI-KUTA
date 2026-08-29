package com.confused.anikuta.data.cloudstream

import android.content.Context
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.preferences.AppPreferences
import com.confused.anikuta.core.providerapi.InstallStep
import com.confused.anikuta.data.cloudstream.installer.CloudstreamPluginInstaller
import com.confused.anikuta.data.cloudstream.loader.CloudstreamPluginLoader
import com.confused.anikuta.data.cloudstream.loader.PluginLoadResult
import com.confused.anikuta.data.cloudstream.model.CloudstreamExtension
import com.confused.anikuta.data.cloudstream.model.CsProviderInfo
import com.confused.anikuta.data.cloudstream.model.CsProviderInfoFactory
import com.confused.anikuta.data.cloudstream.repo.CloudstreamPluginStore
import com.confused.anikuta.data.cloudstream.repo.CloudstreamRepoApi
import com.confused.anikuta.data.cloudstream.repo.CloudstreamRepoRepository
import com.confused.anikuta.data.cloudstream.repo.CsPluginRecord
import com.lagradost.cloudstream3.PROVIDER_STATUS_DOWN
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.plugins.PLUGIN_VERSION_ALWAYS_UPDATE
import com.lagradost.cloudstream3.plugins.SitePlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * The hub of the CloudStream extension system (doc 23 §5.3) — the direct analog of
 * the aniyomi ExtensionManager, following its conventions: StateFlows consumed by
 * the settings UI via koinInject (no ViewModels), Mutex-serialized state mutations,
 * throttled update checks (D-301 pattern), per-plugin error surfacing (D-295/D-296).
 *
 * Lifecycle: Koin singleton — lazily constructed on first injection; init{}
 * loads all installed TRUSTED plugins from disk.
 *
 * Task 46 (device round 5): the FIRST load is DEFERRED until an Activity is
 * alive (with a timeout fallback) — see [AWAIT_ACTIVITY_TIMEOUT_MS]. The
 * construction path runs inside Application.onCreate, where NO activity
 * exists yet and plugins that cast their load(context) to AppCompatActivity
 * (the MovieBoxProvider pattern, ~census share of real plugins) failed on
 * every cold start. This manager is constructed long before any UI is
 * reachable, so the deferral is invisible to the user.
 *
 * Session-2: every list mutation funnels through [refreshLocked] under the ONE
 * [installMutex] (concurrent loadAll/rebuild calls previously interleaved and
 * produced glitchy section state), and the loader is idempotent so a loaded
 * plugin STAYS loaded across refreshes.
 *
 * Session-3 (device round 2):
 * - **Trust flow** — [CsPluginRecord.isTrusted] gates code execution: untrusted
 *   records are listed ([untrusted]) but NEVER loaded (no classloading, no
 *   provider registration). [trustPlugin] loads + promotes; [untrustPlugin]
 *   unloads + demotes. New installs land untrusted; updates preserve trust.
 * - **Parallel installs** — the Pending state + the DOWNLOAD now run OUTSIDE
 *   [installMutex] (a second install used to block silently on the mutex with
 *   no UI feedback — the device round's "no loading animation on the second
 *   download" report). Only the instance swap + load + list refresh serialize;
 *   the installer's per-plugin temp files make concurrent downloads safe.
 * - **Metadata capture** — authors/description/tvTypes/fileSizeBytes are
 *   persisted at install so the plugin DETAIL page renders fully even after
 *   its repository is deleted.
 */
class CloudstreamPluginManager(
    private val context: Context,
    private val repoRepository: CloudstreamRepoRepository,
    private val repoApi: CloudstreamRepoApi,
    val installer: CloudstreamPluginInstaller,
    private val loader: CloudstreamPluginLoader,
    private val pluginStore: CloudstreamPluginStore,
    private val appPreferences: AppPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _installed = MutableStateFlow<List<CloudstreamExtension.Installed>>(emptyList())
    val installed: StateFlow<List<CloudstreamExtension.Installed>> = _installed.asStateFlow()

    /** Installed but NOT trusted — listed, never loaded (session-3 trust flow). */
    private val _untrusted = MutableStateFlow<List<CloudstreamExtension.Untrusted>>(emptyList())
    val untrusted: StateFlow<List<CloudstreamExtension.Untrusted>> = _untrusted.asStateFlow()

    private val _errored = MutableStateFlow<List<CloudstreamExtension.Errored>>(emptyList())
    val errored: StateFlow<List<CloudstreamExtension.Errored>> = _errored.asStateFlow()

    private val _available = MutableStateFlow<List<CloudstreamExtension.Available>>(emptyList())
    val available: StateFlow<List<CloudstreamExtension.Available>> = _available.asStateFlow()

    private val _installStates = MutableStateFlow<Map<String, InstallStep>>(emptyMap())
    val installStates: StateFlow<Map<String, InstallStep>> = _installStates.asStateFlow()

    /**
     * Task 44 (device round 3): internalNames with a RETRY load in flight —
     * the Failed-to-Load rows + the plugin-detail Retry button render their
     * spinner from this (the round-3 report: "no animation while it was
     * reloading").
     */
    private val _retrying = MutableStateFlow<Set<String>>(emptySet())
    val retrying: StateFlow<Set<String>> = _retrying.asStateFlow()

    /** Update-check throttle state (Idle | Checking | updatedAt). */
    sealed interface UpdateCheckState {
        data object Idle : UpdateCheckState
        data object Checking : UpdateCheckState
        data class Done(val at: Long) : UpdateCheckState
    }

    private val _updateCheckState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateCheckState: StateFlow<UpdateCheckState> = _updateCheckState.asStateFlow()

    /** Latest plugins.json entries per repo, kept for update math + install actions. */
    @Volatile
    private var onlinePlugins: List<Pair<SitePlugin, Pair<String, String>>> = emptyList() // plugin → (repoUrl, repoName)

    /**
     * Serializes store/loader mutations + list rebuilds (one coherent state).
     * Downloads do NOT take this lock (session 3) — see [installPlugin].
     */
    private val installMutex = Mutex()

    /**
     * Task 46 (device round 5): false until the first [loadAll] has completed.
     * Consumers that must distinguish "no plugins installed" from "plugins
     * not loaded YET" (the search page's persisted-selection healing, the
     * "MovieBox forgets my source after restart" report) wait on this signal
     * instead of guessing from an empty [installed] list.
     */
    private val _loadedOnce = MutableStateFlow(false)

    /** See [_loadedOnce] — true once the manager's lists reflect disk state. */
    val loadedOnce: StateFlow<Boolean> = _loadedOnce.asStateFlow()

    @Volatile
    private var lastUpdateCheck = 0L

    init {
        // Task 46 (device round 5, the MovieBox cold-start bug): the manager is
        // constructed inside Application.onCreate — BEFORE MainActivity exists.
        // Loading right there handed every Plugin-style .cs3 the APPLICATION
        // context, and plugins that immediately cast it to AppCompatActivity
        // (MovieBoxProvider & friends) landed in "Failed to load" on EVERY app
        // restart even though trusting/retrying them interactively worked
        // (the activity was alive by then). The fix: SUSPEND the first load
        // until CommonActivity reports a live Activity (MainActivity publishes
        // itself in onCreate, ~instantly after Application.onCreate) with a
        // timeout fallback for process-start edge cases where no activity ever
        // comes (background starts — then plugins load with the app context and
        // activity-hungry ones fail honestly with the Retry button available).
        scope.launch {
            val activity = withTimeoutOrNull(AWAIT_ACTIVITY_TIMEOUT_MS) {
                CommonActivity.activityFlow.first { it != null }
            }
            if (activity != null) {
                Logger.i(TAG) {
                    "Initial plugin load: activity ready (${activity.javaClass.simpleName}) — loading plugins"
                }
            } else {
                Logger.w(TAG) {
                    "Initial plugin load: NO activity after ${AWAIT_ACTIVITY_TIMEOUT_MS}ms " +
                        "— loading with app context (activity-dependent plugins may fail; Retry re-loads them)"
                }
            }
            // NOTE: deliberately NOT under installMutex — the repos collector
            // below holds that mutex across its NETWORK fetches, and waiting
            // behind them would delay plugin availability for seconds. This
            // loadAll is fully synchronous (zero suspension points) on the
            // Main-dispatcher scope, so it executes atomically wrt every other
            // main-thread coroutine — the mutex would add delay, not safety.
            loadAll()
        }
        scope.launch {
            repoRepository.repos.collect {
                runCatching { installMutex.withLock { refreshAvailableInternal() } }
                    .onFailure { Logger.w(TAG) { "Repo refresh failed: ${it.message}" } }
            }
        }
        // Task 47 (playback session): register the BUILT-IN extractor set
        // (StreamWish / VidStack / Filesim / Dood / StreamTape / … families)
        // BEFORE any plugin loads — 53/80 census plugins dispatch embeds via
        // loadExtractor, which only reaches registered extractors. Built-ins
        // register first so plugin-registered MIRROR extractors (same family,
        // custom mainUrl) win the reverse-order dispatch, exactly like the
        // upstream app's startup behavior.
        com.lagradost.cloudstream3.extractors.registerBuiltinExtractors()
    }

    // ── Loading ─────────────────────────────────────────────────────────────

    /**
     * Loads every installed TRUSTED plugin from disk. Already-active plugins are
     * re-reported from the live registry (the loader is idempotent — no unload,
     * no "already loaded" failures); genuinely fresh files get a real load.
     *
     * UNTRUSTED records (session 3) are listed but NEVER loaded — trusting a
     * plugin is what executes its code, exactly like the aniyomi flow where an
     * untrusted APK's sources are never instantiated.
     */
    fun loadAll() {
        val records = pluginStore.loadAll()
        val installedList = mutableListOf<CloudstreamExtension.Installed>()
        val untrustedList = mutableListOf<CloudstreamExtension.Untrusted>()
        val erroredList = mutableListOf<CloudstreamExtension.Errored>()

        for (record in records) {
            val file = File(record.filePath)
            if (!file.exists()) {
                // File vanished (user cleared data / partial state) — drop the record.
                scope.launch { pluginStore.delete(record.internalName) }
                continue
            }
            if (!record.isTrusted) {
                untrustedList += record.toUntrusted()
                continue
            }
            when (val result = loader.loadPlugin(file)) {
                is PluginLoadResult.Success -> {
                    // Refresh the version from the plugin's own manifest (doc 04 §4.5).
                    val version = result.manifest.version ?: record.version
                    if (version != record.version) {
                        scope.launch { pluginStore.update(record.internalName) { it.copy(version = version) } }
                    }
                    installedList += record.copy(version = version)
                        .toInstalled(providers = result.providers.map { CsProviderInfoFactory.from(it) })
                }
                is PluginLoadResult.Failure -> {
                    erroredList += record.toErrored(result.reason)
                }
            }
        }
        _installed.value = installedList
        _untrusted.value = untrustedList
        _errored.value = erroredList
        // Task 46: the lists now reflect disk state — release the waiters that
        // gate on "plugins actually loaded" (search selection healing).
        _loadedOnce.value = true
        Logger.i(TAG) {
            "loadAll: ${installedList.size} installed, ${untrustedList.size} untrusted, " +
                "${erroredList.size} errored"
        }
    }

    // ── Trust flow (session 3) ──────────────────────────────────────────────

    /**
     * Promotes an untrusted plugin: marks the record trusted, LOADS it (its
     * providers register into the live registry), then refreshes — the row
     * moves from Untrusted to Trusted Sources and its sources appear in the
     * search picker.
     */
    fun trustPlugin(extension: CloudstreamExtension.Untrusted) {
        scope.launch {
            installMutex.withLock {
                Logger.i(TAG) { "Trusting plugin ${extension.internalName} — loading its classes" }
                pluginStore.update(extension.internalName) { it.copy(isTrusted = true) }
                when (val result = loader.loadPlugin(File(extension.filePath))) {
                    is PluginLoadResult.Success ->
                        Logger.i(TAG) {
                            "Trusted ${extension.internalName}: ${result.providers.size} provider(s) live"
                        }
                    is PluginLoadResult.Failure ->
                        // The load failed — the refresh will surface an Errored row
                        // (D-295: never silent). The record stays trusted so Retry works.
                        Logger.w(TAG) { "Trusted ${extension.internalName} but load failed: ${result.reason}" }
                }
                refreshLocked()
            }
        }
    }

    /**
     * Demotes a trusted plugin: unloads its providers from the registry and
     * marks the record untrusted — no code from this plugin executes until the
     * user trusts it again. The file stays on disk.
     */
    fun untrustPlugin(extension: CloudstreamExtension.Installed) {
        scope.launch {
            installMutex.withLock {
                Logger.i(TAG) { "Untrusting plugin ${extension.internalName} — unloading its providers" }
                loader.unloadPlugin(extension.filePath)
                pluginStore.update(extension.internalName) { it.copy(isTrusted = false) }
                refreshLocked()
            }
        }
    }

    // ── Available catalog ───────────────────────────────────────────────────

    /**
     * Fetches all repos' plugin lists and updates the Available/Installed states.
     * MUST be called under [installMutex] (network + [rebuildLists]).
     */
    private suspend fun refreshAvailableInternal() {
        val repos = repoRepository.repos.value
        val entries = mutableListOf<Pair<SitePlugin, Pair<String, String>>>()
        for (repo in repos) {
            val repository = repoApi.fetchRepository(repo.url) ?: continue
            repoApi.fetchPlugins(repository, repo.url).forEach { plugin ->
                entries += plugin to (repo.url to repo.name)
            }
        }
        onlinePlugins = entries
        rebuildLists()
    }

    /** Rebuilds the Available list + update/disable flags. Under [installMutex]. */
    private fun rebuildLists() {
        val records = pluginStore.loadAll()
        val installedNames = records.map { it.internalName }.toSet()
        _available.value = onlinePlugins
            .filter { (plugin, _) -> plugin.internalName !in installedNames && plugin.url.isNotBlank() }
            .map { (plugin, repo) -> CloudstreamExtension.Available(plugin, repo.first, repo.second) }

        // Update pills + repo kill-switch state on installed entries.
        val installedNow = _installed.value.map { current ->
            val online = onlinePlugins.firstOrNull { it.first.internalName == current.internalName }?.first
            current.copy(
                availableUpdateVersion = online?.takeIf { isUpdate(it.version, current.version) }?.version,
                isDisabledByRepo = online?.status == PROVIDER_STATUS_DOWN,
            )
        }
        _installed.value = installedNow
    }

    /** loadAll + rebuildLists — the ONE coherent refresh every mutation ends with. */
    private suspend fun refreshLocked() {
        loadAll()
        rebuildLists()
    }

    /** The documented update predicate (doc 04 §4.5). */
    fun isUpdate(onlineVersion: Int, savedVersion: Int): Boolean = isCsUpdate(onlineVersion, savedVersion)

    /** Public entry the UI calls on screen entry (30-min throttle, D-301 pattern). */
    fun checkForUpdates(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastUpdateCheck < UPDATE_CHECK_THROTTLE_MS) return
        if (_updateCheckState.value is UpdateCheckState.Checking) return
        _updateCheckState.value = UpdateCheckState.Checking
        scope.launch(Dispatchers.IO) {
            runCatching { installMutex.withLock { refreshAvailableInternal() } }
                .onFailure { Logger.w(TAG) { "Update check failed: ${it.message}" } }
            lastUpdateCheck = System.currentTimeMillis()
            _updateCheckState.value = UpdateCheckState.Done(lastUpdateCheck)
        }
    }

    // ── Install / uninstall ─────────────────────────────────────────────────

    /**
     * Downloads + verifies + installs + records one available plugin. Emits progress
     * via [installStates] (shared InstallStep model, doc 23 §5.5).
     *
     * PARALLEL BY DESIGN (session-3 device round): the Pending state and the
     * whole download run OUTSIDE [installMutex] — tapping Download on a second
     * plugin while the first is still downloading now shows that row's own
     * progress ring immediately (it previously blocked silently on the mutex
     * with zero UI feedback). Only the serialized section (unload stale →
     * persist record → load fresh) and the post-install refresh take the lock.
     * The installer streams each plugin to its OWN cacheDir temp file, so
     * concurrent downloads never collide.
     *
     * New installs land UNTRUSTED (session-3 trust flow — the row moves to the
     * Untrusted section; the user trusts it to load its providers). Updates
     * PRESERVE the existing trust state so an update never demotes a trusted
     * plugin.
     */
    fun installPlugin(extension: CloudstreamExtension.Available) {
        val plugin = extension.plugin
        val internalName = plugin.internalName

        // Double-tap guard: a second tap on a row whose install is already
        // active is ignored (the running coroutine owns the progress state).
        if (isInstallActive(_installStates.value[internalName])) {
            Logger.i(TAG) { "Install already active for $internalName — ignoring duplicate request" }
            return
        }

        scope.launch {
            // OUTSIDE the lock — visible immediately, even while another
            // plugin's install holds the mutex for its swap section.
            _installStates.value = _installStates.value + (internalName to InstallStep.Pending)
            try {
                val target = CloudstreamPluginInstaller.pluginPath(context.filesDir, internalName, extension.repoUrl)
                installer.download(plugin.url, plugin.fileHash, target).collect { step ->
                    _installStates.value = _installStates.value + (internalName to step)
                }

                // ── Serialized section: swap the in-memory instance + persist + load. ──
                installMutex.withLock {
                    // Update/reinstall replaces the file at the SAME deterministic
                    // path — drop the stale classloader before the fresh dex loads.
                    // (A failed download above leaves the old plugin loaded + untouched.)
                    loader.unloadPlugin(target.absolutePath)

                    // Preserve the existing trust state (updates never demote);
                    // genuinely new installs land untrusted.
                    val previous = pluginStore.loadAll().firstOrNull { it.internalName == internalName }
                    val record = CsPluginRecord(
                        internalName = internalName,
                        name = plugin.name,
                        url = plugin.url,
                        filePath = target.absolutePath,
                        version = plugin.version,
                        repoUrl = extension.repoUrl,
                        fileHash = plugin.fileHash,
                        language = plugin.language,
                        iconUrl = plugin.iconUrl,
                        isNsfw = extension.isNsfw,
                        authors = plugin.authors,
                        description = plugin.description,
                        tvTypes = plugin.tvTypes ?: emptyList(),
                        fileSizeBytes = plugin.fileSize,
                        isTrusted = previous?.isTrusted ?: false,
                    )
                    pluginStore.upsert(record)
                    when (val result = loader.loadPlugin(target)) {
                        is PluginLoadResult.Success ->
                            Logger.i(TAG) {
                                "Installed $internalName (trusted=${record.isTrusted}): " +
                                    "${result.providers.size} provider(s)"
                            }
                        is PluginLoadResult.Failure ->
                            Logger.w(TAG) { "Plugin $internalName installed but load failed: ${result.reason}" }
                    }
                }

                // Terminal state first, THEN the list move (after the beat) —
                // the available row animates to a full ring + "Done" first.
                _installStates.value = _installStates.value + (internalName to InstallStep.Installed)
                delay(COMPLETION_BEAT_MS)
                installMutex.withLock { refreshLocked() }
            } catch (t: Throwable) {
                Logger.e(TAG) { "Install failed for $internalName: ${t.message}" }
                _installStates.value = _installStates.value + (internalName to InstallStep.Error)
            } finally {
                scope.launch {
                    kotlinx.coroutines.delay(INSTALL_STATE_CLEAR_MS)
                    _installStates.value =
                        _installStates.value - internalName // clear terminal state after a beat
                }
            }
        }
    }

    fun uninstallPlugin(extension: CloudstreamExtension) {
        val (internalName, filePath) = when (extension) {
            is CloudstreamExtension.Installed -> extension.internalName to extension.filePath
            is CloudstreamExtension.Untrusted -> extension.internalName to extension.filePath
            is CloudstreamExtension.Errored -> extension.internalName to extension.filePath
            is CloudstreamExtension.Available -> return
        }
        scope.launch {
            installMutex.withLock {
                loader.unloadPlugin(filePath)
                File(filePath).delete()
                // Clean the (now-empty) repo dir if this was its last plugin.
                File(filePath).parentFile?.takeIf { it.list()?.isEmpty() == true }?.delete()
                pluginStore.delete(internalName)
                refreshLocked()
            }
        }
    }

    /** Retry loading an errored plugin (D-296 pattern; Task 44: retrying state
     * drives the row/button spinners — never a silent freeze). */
    fun retryPlugin(extension: CloudstreamExtension.Errored) {
        if (extension.internalName in _retrying.value) return
        scope.launch {
            installMutex.withLock {
                _retrying.value = _retrying.value + extension.internalName
                try {
                    Logger.i(TAG) { "Retrying plugin ${extension.internalName} — unloading + reloading" }
                    loader.unloadPlugin(extension.filePath) // clear any partial state
                    refreshLocked()
                } finally {
                    _retrying.value = _retrying.value - extension.internalName
                }
            }
        }
    }

    fun destroy() {
        scope.cancel()
    }

    private fun isInstallActive(step: InstallStep?): Boolean =
        step is InstallStep.Pending || step is InstallStep.Downloading || step is InstallStep.Installing

    private fun CsPluginRecord.toInstalled(providers: List<CsProviderInfo>): CloudstreamExtension.Installed =
        CloudstreamExtension.Installed(
            internalName = internalName,
            name = name,
            version = version,
            filePath = filePath,
            repoUrl = repoUrl,
            language = language,
            iconUrl = iconUrl,
            isNsfw = isNsfw,
            providerCount = providers.size,
            authors = authors,
            description = description,
            tvTypes = tvTypes,
            fileSizeBytes = fileSizeBytes,
            providers = providers,
        )

    private fun CsPluginRecord.toUntrusted(): CloudstreamExtension.Untrusted =
        CloudstreamExtension.Untrusted(
            internalName = internalName,
            name = name,
            version = version,
            filePath = filePath,
            repoUrl = repoUrl,
            language = language,
            iconUrl = iconUrl,
            isNsfw = isNsfw,
            authors = authors,
            description = description,
            tvTypes = tvTypes,
            fileSizeBytes = fileSizeBytes,
        )

    private fun CsPluginRecord.toErrored(message: String): CloudstreamExtension.Errored =
        CloudstreamExtension.Errored(
            internalName = internalName,
            name = name,
            version = version,
            filePath = filePath,
            language = language,
            iconUrl = iconUrl,
            isNsfw = isNsfw,
            authors = authors,
            description = description,
            tvTypes = tvTypes,
            fileSizeBytes = fileSizeBytes,
            message = message,
        )

    companion object {
        private const val TAG = "Anikuta:Data:Cloudstream:Manager"
        private const val UPDATE_CHECK_THROTTLE_MS = 30 * 60 * 1000L // D-301 pattern

        /**
         * Task 46: how long the deferred initial load waits for the first
         * Activity before falling back to the app context (background process
         * starts). MainActivity publishes itself within ~a second of
         * Application.onCreate, so 15s is a generous safety margin.
         */
        private const val AWAIT_ACTIVITY_TIMEOUT_MS = 15_000L

        /** How long the success state plays on the row before the list reshuffles. */
        private const val COMPLETION_BEAT_MS = 700L

        /** How long a terminal install state lingers after everything settled. */
        private const val INSTALL_STATE_CLEAR_MS = 1500L
    }
}

/**
 * The documented update predicate (doc 04 §4.5): a higher integer version means
 * newer (equality = no update, lower = ignored — no rollback), and -1
 * (PLUGIN_VERSION_ALWAYS_UPDATE) forces an update on every check. Pure + testable.
 */
fun isCsUpdate(onlineVersion: Int, savedVersion: Int): Boolean =
    onlineVersion > savedVersion || onlineVersion == PLUGIN_VERSION_ALWAYS_UPDATE
