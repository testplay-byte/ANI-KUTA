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
import com.lagradost.cloudstream3.plugins.PLUGIN_VERSION_ALWAYS_UPDATE
import com.lagradost.cloudstream3.plugins.PROVIDER_STATUS_DOWN
import com.lagradost.cloudstream3.plugins.SitePlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
 * loads all enabled installed plugins from disk. Disabled plugins stay on disk
 * unloaded (the G4 "highly customizable later" direction).
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

    private val installMutex = Mutex()
    @Volatile
    private var lastUpdateCheck = 0L

    init {
        loadAll()
        scope.launch { repoRepository.repos.collect { refreshAvailableInternal() } }
    }

    // ── Loading ─────────────────────────────────────────────────────────────

    /** Loads every enabled installed plugin from disk; disabled ones stay unloaded. */
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
            if (!record.isEnabled) {
                installedList += record.toInstalled(providerCount = 0)
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
                    erroredList += CloudstreamExtension.Errored(
                        internalName = record.internalName,
                        name = record.name,
                        version = record.version,
                        filePath = record.filePath,
                        message = result.reason,
                    )
                }
            }
        }
        _installed.value = installedList
        _errored.value = erroredList
    }

    // ── Available catalog ───────────────────────────────────────────────────

    /** Fetches all repos' plugin lists and updates the Available/Installed states. */
    suspend fun refreshAvailableInternal() {
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

    /** The documented update predicate (doc 04 §4.5). */
    fun isUpdate(onlineVersion: Int, savedVersion: Int): Boolean = isCsUpdate(onlineVersion, savedVersion)

    /** Public entry the UI calls on screen entry (30-min throttle, D-301 pattern). */
    fun checkForUpdates(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastUpdateCheck < UPDATE_CHECK_THROTTLE_MS) return
        if (_updateCheckState.value is UpdateCheckState.Checking) return
        _updateCheckState.value = UpdateCheckState.Checking
        scope.launch(Dispatchers.IO) {
            runCatching { refreshAvailableInternal() }
                .onFailure { Logger.w(TAG) { "Update check failed: ${it.message}" } }
            lastUpdateCheck = System.currentTimeMillis()
            _updateCheckState.value = UpdateCheckState.Done(lastUpdateCheck)
        }
    }

    // ── Install / uninstall / enable ────────────────────────────────────────

    /**
     * Downloads + verifies + installs + loads one available plugin. Emits progress
     * via [installStates] (shared InstallStep model, doc 23 §5.5).
     */
    fun installPlugin(extension: CloudstreamExtension.Available) {
        val plugin = extension.plugin
        val internalName = plugin.internalName
        scope.launch {
            installMutex.withLock {
                val target = installer.pluginPath(context, internalName, extension.repoUrl)
                _installStates.value = _installStates.value + (internalName to InstallStep.Pending)
                try {
                    installer.download(plugin.url, plugin.fileHash, target).collect { step ->
                        _installStates.value = _installStates.value + (internalName to step)
                    }
                    val record = CsPluginRecord(
                        internalName = internalName,
                        name = plugin.name,
                        url = plugin.url,
                        filePath = target.absolutePath,
                        version = plugin.version,
                        repoUrl = extension.repoUrl,
                        fileHash = plugin.fileHash,
                        isEnabled = true,
                    )
                    pluginStore.upsert(record)
                    when (val result = loader.loadPlugin(target)) {
                        is PluginLoadResult.Success -> {
                            _installStates.value = _installStates.value + (internalName to InstallStep.Installed)
                            loadAll()
                            rebuildLists()
                        }
                        is PluginLoadResult.Failure -> {
                            // Installed but failed to load — visible Errored row (honest state).
                            _installStates.value = _installStates.value + (internalName to InstallStep.Installed)
                            loadAll()
                            rebuildLists()
                            Logger.w(TAG) { "Plugin $internalName installed but load failed: ${result.reason}" }
                        }
                    }
                } catch (t: Throwable) {
                    Logger.e(TAG) { "Install failed for $internalName: ${t.message}" }
                    _installStates.value = _installStates.value + (internalName to InstallStep.Error)
                } finally {
                    scope.launch {
                        kotlinx.coroutines.delay(1500)
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
                loadAll()
                rebuildLists()
            }
        }
    }

    /** Retry loading an errored plugin (D-296 pattern). */
    fun retryPlugin(extension: CloudstreamExtension.Errored) {
        scope.launch {
            installMutex.withLock {
                loader.unloadPlugin(extension.filePath) // clear any partial state
                loadAll()
                rebuildLists()
            }
        }
    }

    fun setEnabled(extension: CloudstreamExtension.Installed, enabled: Boolean) {
        scope.launch {
            pluginStore.update(extension.internalName) { it.copy(isEnabled = enabled) }
            if (!enabled) {
                loader.unloadPlugin(extension.filePath)
            }
            loadAll()
            rebuildLists()
        }
    }

    /** Repo deletion: unload + delete every plugin from that repo (doc 04 §4.6). */
    fun deleteRepoPlugins(repoUrl: String) {
        scope.launch {
            installMutex.withLock {
                val removed = pluginStore.deleteForRepo(repoUrl)
                removed.forEach { record ->
                    loader.unloadPlugin(record.filePath)
                    File(record.filePath).delete()
                }
                File(
                    CloudstreamPluginInstaller.pluginPath(context, "x", repoUrl).parentFile,
                ).takeIf { it.exists() }?.deleteRecursively()
                loadAll()
                rebuildLists()
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
            repoName = repoUrl?.let { repoRepository.find(it)?.name },
            isEnabled = isEnabled,
            providerCount = providerCount,
        )

    companion object {
        private const val TAG = "Anikuta:Data:Cloudstream:Manager"
        private const val UPDATE_CHECK_THROTTLE_MS = 30 * 60 * 1000L // D-301 pattern
    }
}

/**
 * The documented update predicate (doc 04 §4.5): a higher integer version means
 * newer (equality = no update, lower = ignored — no rollback), and -1
 * (PLUGIN_VERSION_ALWAYS_UPDATE) forces an update on every check. Pure + testable.
 */
fun isCsUpdate(onlineVersion: Int, savedVersion: Int): Boolean =
    onlineVersion > savedVersion || onlineVersion == PLUGIN_VERSION_ALWAYS_UPDATE
