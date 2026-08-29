package com.confused.anikuta.data.cloudstream

import android.content.Context
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.preferences.AppPreferences
import com.confused.anikuta.core.providerapi.InstallStep
import com.confused.anikuta.data.cloudstream.installer.CloudstreamPluginInstaller
import com.confused.anikuta.data.cloudstream.loader.CloudstreamPluginLoader
import com.confused.anikuta.data.cloudstream.loader.PluginLoadResult
import com.confused.anikuta.data.cloudstream.model.CloudstreamExtension
import com.confused.anikuta.data.cloudstream.repo.CloudstreamPluginStore
import com.confused.anikuta.data.cloudstream.repo.CloudstreamRepoApi
import com.confused.anikuta.data.cloudstream.repo.CloudstreamRepoRepository
import com.confused.anikuta.data.cloudstream.repo.CsPluginRecord
import com.lagradost.cloudstream3.PROVIDER_STATUS_DOWN
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * The hub of the CloudStream extension system (doc 23 §5.3) — the direct analog of
 * the aniyomi ExtensionManager, following its conventions: StateFlows consumed by
 * the settings UI via koinInject (no ViewModels), Mutex-serialized installs,
 * throttled update checks (D-301 pattern), per-plugin error surfacing (D-295/D-296).
 *
 * Lifecycle: Koin singleton — lazily constructed on first injection; init{}
 * loads all installed plugins from disk.
 *
 * Session-2 device round: every list mutation now funnels through
 * [refreshLocked] under the ONE [installMutex] (concurrent loadAll/rebuild calls
 * previously interleaved and produced glitchy section state), and the loader is
 * idempotent so a loaded plugin STAYS loaded across refreshes (the
 * "Plugin already loaded" → Failed-to-load loop is gone — see the loader KDoc).
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

    private val _errored = MutableStateFlow<List<CloudstreamExtension.Errored>>(emptyList())
    val errored: StateFlow<List<CloudstreamExtension.Errored>> = _errored.asStateFlow()

    private val _available = MutableStateFlow<List<CloudstreamExtension.Available>>(emptyList())
    val available: StateFlow<List<CloudstreamExtension.Available>> = _available.asStateFlow()

    private val _installStates = MutableStateFlow<Map<String, InstallStep>>(emptyMap())
    val installStates: StateFlow<Map<String, InstallStep>> = _installStates.asStateFlow()

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

    /** Serializes EVERY store/loader mutation + list rebuild (one coherent state). */
    private val installMutex = Mutex()

    @Volatile
    private var lastUpdateCheck = 0L

    init {
        loadAll()
        scope.launch {
            repoRepository.repos.collect {
                runCatching { installMutex.withLock { refreshAvailableInternal() } }
                    .onFailure { Logger.w(TAG) { "Repo refresh failed: ${it.message}" } }
            }
        }
    }

    // ── Loading ─────────────────────────────────────────────────────────────

    /**
     * Loads every installed plugin from disk. Already-active plugins are
     * re-reported from the live registry (the loader is idempotent — no unload,
     * no "already loaded" failures); genuinely fresh files get a real load.
     */
    fun loadAll() {
        val records = pluginStore.loadAll()
        val installedList = mutableListOf<CloudstreamExtension.Installed>()
        val erroredList = mutableListOf<CloudstreamExtension.Errored>()

        for (record in records) {
            val file = File(record.filePath)
            if (!file.exists()) {
                // File vanished (user cleared data / partial state) — drop the record.
                scope.launch { pluginStore.delete(record.internalName) }
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
                        .toInstalled(providerCount = result.providers.size)
                }
                is PluginLoadResult.Failure -> {
                    erroredList += record.toErrored(result.reason)
                }
            }
        }
        _installed.value = installedList
        _errored.value = erroredList
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
     * Downloads + verifies + installs + loads one available plugin. Emits progress
     * via [installStates] (shared InstallStep model, doc 23 §5.5).
     *
     * Session-2 sequencing (device round): the installer explicitly emits
     * Downloading(100) + a beat before Installing, and after the load completes
     * the terminal Installed state is held for [COMPLETION_BEAT_MS] BEFORE the
     * lists refresh — so the row's ring visibly fills to 100% and the "Done"
     * check plays out before the plugin moves into Trusted Sources.
     */
    fun installPlugin(extension: CloudstreamExtension.Available) {
        val plugin = extension.plugin
        val internalName = plugin.internalName
        scope.launch {
            installMutex.withLock {
                val target = CloudstreamPluginInstaller.pluginPath(context.filesDir, internalName, extension.repoUrl)
                _installStates.value = _installStates.value + (internalName to InstallStep.Pending)
                try {
                    installer.download(plugin.url, plugin.fileHash, target).collect { step ->
                        _installStates.value = _installStates.value + (internalName to step)
                    }
                    // Download verified + moved into place — NOW swap the in-memory
                    // instance: update/reinstall replaces the file at the SAME
                    // deterministic path, so drop the stale classloader before the
                    // fresh dex loads. (A failed download above leaves the old
                    // plugin loaded and its file untouched.)
                    loader.unloadPlugin(target.absolutePath)
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
                    )
                    pluginStore.upsert(record)
                    when (val result = loader.loadPlugin(target)) {
                        is PluginLoadResult.Success -> Unit
                        is PluginLoadResult.Failure ->
                            // Installed but failed to load — honest Errored row (D-295).
                            Logger.w(TAG) { "Plugin $internalName installed but load failed: ${result.reason}" }
                    }
                    // Terminal state first, THEN the list move (after the beat) —
                    // the available row animates to a full ring + "Done" first.
                    _installStates.value = _installStates.value + (internalName to InstallStep.Installed)
                    delay(COMPLETION_BEAT_MS)
                    refreshLocked()
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
    }

    fun uninstallPlugin(extension: CloudstreamExtension) {
        val (internalName, filePath) = when (extension) {
            is CloudstreamExtension.Installed -> extension.internalName to extension.filePath
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

    /** Retry loading an errored plugin (D-296 pattern). */
    fun retryPlugin(extension: CloudstreamExtension.Errored) {
        scope.launch {
            installMutex.withLock {
                loader.unloadPlugin(extension.filePath) // clear any partial state
                refreshLocked()
            }
        }
    }

    fun destroy() {
        scope.cancel()
    }

    private fun CsPluginRecord.toInstalled(providerCount: Int): CloudstreamExtension.Installed =
        CloudstreamExtension.Installed(
            internalName = internalName,
            name = name,
            version = version,
            filePath = filePath,
            repoUrl = repoUrl,
            language = language,
            iconUrl = iconUrl,
            isNsfw = isNsfw,
            providerCount = providerCount,
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
            message = message,
        )

    companion object {
        private const val TAG = "Anikuta:Data:Cloudstream:Manager"
        private const val UPDATE_CHECK_THROTTLE_MS = 30 * 60 * 1000L // D-301 pattern

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
