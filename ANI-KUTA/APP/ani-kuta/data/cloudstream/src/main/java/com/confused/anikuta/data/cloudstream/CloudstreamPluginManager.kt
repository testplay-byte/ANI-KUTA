package com.confused.anikuta.data.cloudstream

import android.content.Context
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.preferences.AppPreferences
import com.confused.anikuta.core.providerapi.InstallStep
import com.confused.anikuta.data.cloudstream.installer.CloudstreamPluginInstaller
import com.confused.anikuta.data.cloudstream.installer.CsSharedPluginFormat
import com.confused.anikuta.data.cloudstream.loader.CloudstreamPluginLoader
import com.confused.anikuta.data.cloudstream.loader.PluginLoadResult
import com.confused.anikuta.data.cloudstream.model.CloudstreamExtension
import com.confused.anikuta.data.cloudstream.model.CsProviderInfo
import com.confused.anikuta.data.cloudstream.model.CsProviderInfoFactory
import com.confused.anikuta.data.cloudstream.repo.CloudstreamPluginStore
import com.confused.anikuta.data.cloudstream.repo.CloudstreamRepoApi
import com.confused.anikuta.data.cloudstream.repo.CloudstreamRepoRepository
import com.confused.anikuta.data.cloudstream.repo.CsPluginIdentity
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
     * CloudStream V2 (Task 51) — the SEQUENTIAL install queue. User
     * requirement (round 11, verbatim intent): "multiple download
     * functionality tools where I can download multiple at a time and they
     * will get downloaded one by one at a time" — queue several plugins,
     * execute strictly one install at a time.
     *
     * The reference implementation ran downloads in PARALLEL (each tap got
     * its own coroutine racing the network); V2 wraps the WHOLE install
     * (download → verify → swap → load) in this dedicated lock so queued
     * rows show their own Pending state immediately while exactly ONE
     * install runs. Deliberately separate from [installMutex] so a long
     * download never blocks trust/untrust/refresh mutations.
     */
    private val installQueueMutex = Mutex()

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

            // Task 48.1 (device round 8 — MovieBox dead after a crash): when the
            // app restarts INTO ErrorActivity (the crash handler's screen), no
            // activity registers with CommonActivity (only MainActivity does),
            // so the initial load ran with the APP context and every
            // AppCompatActivity-casting plugin (MovieBoxProvider & friends)
            // errored for the WHOLE session — even after the user reopened
            // MainActivity. Self-heal: the moment a real activity appears, load
            // everything again (the loader is idempotent — successes are
            // re-reported from the live registry, failures get a real retry).
            if (activity == null) {
                scope.launch {
                    // The predicate guarantees non-null, but the flow's element
                    // type is Activity? — elvis for the smart cast.
                    val late = CommonActivity.activityFlow.first { it != null } ?: return@launch
                    Logger.i(TAG) {
                        "Activity arrived late (${late.javaClass.simpleName}) after app-context load — " +
                            "reloading plugins (activity-dependent self-heal)"
                    }
                    loadAll()
                }
            }
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
        // Task 62 (round 22 — plugin ↔ repository LINKAGE): match installed
        // records — INCLUDING manual imports whose derived internalName
        // drifted from the repo's — against the online catalog with the
        // ordered identity ladder (CsPluginIdentity: exact name → linked repo
        // name → URL → file hash → normalized names). On a hit, back-fill the
        // record's repoUrl/repoInternalName/url/fileHash so a manually
        // installed plugin behaves EXACTLY like a repo install the moment its
        // repository shows up (update pills, kill-switch) — the round-22
        // device report: "it can properly recognize the cloud stream
        // extensions and their repositories even after the repository was
        // added later on". Idempotent: the store write only happens when a
        // value actually changed, so the frequent rebuilds stay cheap.
        val recordOnline = HashMap<String, Pair<SitePlugin, Pair<String, String>>>()
        val linkedRecords = records.map { record ->
            val online = onlinePlugins.firstOrNull { CsPluginIdentity.matches(record, it.first) }
            if (online == null) {
                record
            } else {
                recordOnline[record.internalName] = online
                val plugin = online.first
                val updated = record.copy(
                    repoUrl = online.second.first,
                    repoInternalName = plugin.internalName,
                    url = record.url ?: plugin.url,
                    fileHash = record.fileHash ?: plugin.fileHash,
                )
                if (updated != record) {
                    Logger.i(TAG) {
                        "rebuildLists — linked '${record.internalName}' to repository " +
                            "${online.second.second} (catalog internalName '${plugin.internalName}')"
                    }
                    scope.launch {
                        pluginStore.update(record.internalName) { updated }
                    }
                }
                updated
            }
        }
        // The Available list drops every catalog entry that matches ANY
        // installed record under the same ladder — the duplicate-row fix (a
        // manually imported plugin no longer ALSO renders as "available").
        _available.value = onlinePlugins
            .filter { (plugin, _) ->
                plugin.url.isNotBlank() &&
                    linkedRecords.none { CsPluginIdentity.matches(it, plugin) }
            }
            .map { (plugin, repo) -> CloudstreamExtension.Available(plugin, repo.first, repo.second) }

        // Update pills + repo kill-switch state on installed entries.
        val installedNow = _installed.value.map { current ->
            val online = recordOnline[current.internalName]?.first
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

    /**
     * Task 62 (round 22): the Available-row target an INSTALLED plugin's Update
     * pill should install. A LINKED manual import is no longer present in the
     * Available list under its own (drifted) internalName — the old exact-name
     * lookup in the extensions section missed it and the pill did nothing.
     * This resolves the ONLINE catalog entry through the identity ladder and
     * wraps it as [CloudstreamExtension.Available] for [installPlugin] (which
     * is itself linkage-aware — it updates the existing record in place).
     */
    fun availableUpdateTarget(internalName: String): CloudstreamExtension.Available? {
        val record = pluginStore.loadAll().firstOrNull { it.internalName == internalName }
            ?: return null
        val online = onlinePlugins.firstOrNull { CsPluginIdentity.matches(record, it.first) }
            ?: return null
        return CloudstreamExtension.Available(online.first, online.second.first, online.second.second)
    }

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
     * SEQUENTIAL QUEUE (CloudStream V2 / Task 51 — user requirement): tapping
     * Download on several plugins queues them ALL instantly (each row shows
     * its own Pending state) and they install strictly ONE BY ONE — the
     * whole run (download → verify → swap → load) holds [installQueueMutex].
     * (The reference ran downloads in parallel; the user explicitly asked
     * for one-by-one execution on this branch.) The serialized swap section
     * additionally takes [installMutex]; trust/untrust/refresh stay queue-
     * independent — a long download never blocks them.
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
        // active (or queued) is ignored (the running coroutine owns the progress state).
        if (isInstallActive(_installStates.value[internalName])) {
            Logger.i(TAG) { "Install already active for $internalName — ignoring duplicate request" }
            return
        }

        scope.launch {
            // OUTSIDE the queue lock — visible immediately, so queued rows
            // show their own Pending state while an earlier install runs.
            _installStates.value = _installStates.value + (internalName to InstallStep.Pending)
            try {
                installQueueMutex.withLock {
                    // Re-check after dequeuing: only run when this row is
                    // still PENDING (queued) or stateless — a terminal state
                    // (Installed/Error in the completion-beat window) means a
                    // stale duplicate already finished and must not re-run.
                    val currentStep = _installStates.value[internalName]
                    if (currentStep == null || currentStep == InstallStep.Pending) {
                        // Task 62 (round 22 — linkage-aware installs): resolve
                        // the existing record through the identity ladder
                        // BEFORE the download. An exact-name repo install is the
                        // historical path; a LINKED manual import (whose stored
                        // internalName drifted from the catalog's) updates IN
                        // PLACE — the SAME record name + the SAME file path —
                        // so no second record/file can ever appear (the
                        // round-22 duplicate-row bug, update-side).
                        val existingRecord = pluginStore.loadAll().firstOrNull { record ->
                            record.internalName == internalName ||
                                CsPluginIdentity.matches(record, plugin)
                        }
                        val recordInternalName = existingRecord?.internalName ?: internalName
                        val target = existingRecord?.filePath?.let(::File)
                            ?: CloudstreamPluginInstaller.pluginPath(context.filesDir, internalName, extension.repoUrl)
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
                            val record = CsPluginRecord(
                                internalName = recordInternalName,
                                name = plugin.name,
                                url = plugin.url,
                                filePath = target.absolutePath,
                                version = plugin.version,
                                repoUrl = extension.repoUrl,
                                // Task 62: the catalog name is captured for exact
                                // future identity comparisons either way.
                                repoInternalName = plugin.internalName,
                                fileHash = plugin.fileHash,
                                language = plugin.language,
                                iconUrl = plugin.iconUrl,
                                isNsfw = extension.isNsfw,
                                authors = plugin.authors,
                                description = plugin.description,
                                tvTypes = plugin.tvTypes ?: emptyList(),
                                fileSizeBytes = plugin.fileSize,
                                isTrusted = existingRecord?.isTrusted ?: false,
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

    // ── Task 58/59: the shared-file import (.WHITECAT + export metadata) ──────

    /**
     * The result of [importSharedPlugin] — the import activity renders each
     * case (added → success + trust hint; already installed → the existing
     * record's state; invalid → the reason, no side effects).
     */
    sealed interface CsImportResult {
        /** Placed + recorded (UNTRUSTED — the user trusts it from the detail page). */
        data class Added(val record: CsPluginRecord, val linkedRepoUrl: String?) : CsImportResult

        /** The same internalName is already installed (from a repo or an earlier share). */
        data class AlreadyInstalled(val record: CsPluginRecord) : CsImportResult

        /** The file isn't a valid CloudStream plugin (reason shown to the user). */
        data class Invalid(val reason: String) : CsImportResult
    }

    /**
     * Task 58 (round 18) — installs a plugin from a SHARED
     * `<internalName>.WHITECAT` file (the user's spec: plugins shared
     * device-to-device, added REGARDLESS of repositories).
     *
     * The bytes are the .cs3 zip (plus the sender's `anikuta/` metadata
     * entries — Task 59); the file NAME carries the identity (the stem =
     * internalName, mirroring the repo .cs3 naming; renamed/.bin files fall
     * back to the manifest name — the activity validates by CONTENT first
     * now). Works with or without repositories:
     *  - a repo that catalogs the SAME internalName LINKS the record to it
     *    (repoUrl set — update checks + repo updates then apply);
     *  - otherwise the record is repo-less (path salted by
     *    [CsSharedPluginFormat.SHARED_PATH_SALT]) — Task 59: the EXPORT
     *    METADATA still rides along (the source repository URL, the icon,
     *    the catalog display fields) so the row + detail page render exactly
     *    like the sender's, and an embedded icon lands as a LOCAL file the
     *    record points at (no network needed).
     *
     * The confirm dialog happened in the activity BEFORE this call — an
     * [CsImportResult.Added] IS the user's consent. Like every fresh install,
     * the record lands UNTRUSTED: the row appears in the Untrusted section and
     * the user trusts it from the plugin's detail page (the session-3 trust
     * model — the import consent adds the FILE, trust gates the CODE).
     *
     * File safety: the source is copied to a temp NEXT TO the target and moved
     * atomically; the caller keeps ownership of [sourceFile] (it deletes it).
     */
    suspend fun importSharedPlugin(sourceFile: File, displayName: String): CsImportResult {
        val manifest = CsSharedPluginFormat.readManifest(sourceFile)
            ?: return CsImportResult.Invalid("Not a CloudStream plugin file (no manifest.json inside)")
        val internalName = CsSharedPluginFormat.internalNameFor(displayName, manifest)
            ?: return CsImportResult.Invalid("Could not determine the plugin's name from the file")
        if (manifest.pluginClassName.isNullOrBlank()) {
            return CsImportResult.Invalid("The plugin file has no pluginClassName (incomplete export)")
        }
        // Task 62 (round 22): the file's sha256 ("sha256-<hex>", the repo's
        // own format). A raw .cs3 re-share of a repo-hosted file hashes
        // IDENTICALLY to the catalog's fileHash — a strong identity rung for
        // the already-installed check, the import-time repo linkage, AND the
        // later back-fill in rebuildLists. (.WHITECAT exports carry extra
        // anikuta/ entries and hash differently — the name/URL rungs cover
        // those.) Computed on the caller's IO dispatcher; a read failure
        // degrades to null (the ladder just loses that rung).
        val importFileHash = computeFileHash(sourceFile)

        return installMutex.withLock {
            // Already installed — from a repo, an earlier share, or a record
            // that was LINKED to a repo by the Task 62 back-fill (its identity
            // may have drifted from this file's derived name — the identity
            // ladder matches it anyway)? The shared file is redundant; report
            // + no side effects.
            val existing = pluginStore.loadAll().firstOrNull {
                CsPluginIdentity.matchesImport(it, internalName, manifest.name, importFileHash)
            }
            if (existing != null) {
                Logger.i(TAG) {
                    "importSharedPlugin — $internalName already installed " +
                        "(repo=${existing.repoUrl ?: "none"}, trusted=${existing.isTrusted})"
                }
                return@withLock CsImportResult.AlreadyInstalled(existing)
            }

            // Repo linkage (the user's spec): an added repository that catalogs
            // this plugin links the record to it — the plugin then behaves
            // exactly like a repo install (update checks, repo updates). The
            // probe record carries the file's identity so the SAME ordered
            // ladder decides the match (exact/normalized name, hash, display
            // name) instead of the old exact-internalName-only check that
            // missed drifted manual-import identities.
            val probe = CsPluginRecord(
                internalName = internalName,
                name = manifest.name ?: internalName,
                url = null,
                filePath = "",
                version = manifest.version ?: 1,
                repoUrl = null,
                fileHash = importFileHash,
            )
            val online = onlinePlugins.firstOrNull { CsPluginIdentity.matches(probe, it.first) }
            val linkedRepoUrl: String? = online?.second?.first
            if (online != null) {
                Logger.i(TAG) {
                    "importSharedPlugin — linking $internalName to repository " +
                        "${online.second.second} (${online.first.version}) for updates"
                }
            }

            // Task 59 — the sender's export metadata: the fallback for the
            // repo-less case (source repository URL, icon, catalog fields).
            // Null for plain .cs3 files (the round-18 behavior).
            val exportInfo = CsSharedPluginFormat.readExportInfo(sourceFile)

            // Place the file: the repo-salted path when linked, the shared
            // salt otherwise (repo-less installs coexist with repo installs).
            val target = CloudstreamPluginInstaller.pluginPath(
                context.filesDir,
                internalName,
                linkedRepoUrl ?: CsSharedPluginFormat.SHARED_PATH_SALT,
            )
            target.parentFile?.mkdirs()
            // Stale-swap guard (idempotent — a no-op for a fresh name).
            loader.unloadPlugin(target.absolutePath)
            val tmp = File(target.parentFile, target.name + ".importing")
            try {
                sourceFile.copyTo(tmp, overwrite = true)
                if (target.exists()) target.delete()
                java.nio.file.Files.move(tmp.toPath(), target.toPath())
            } finally {
                tmp.delete()
            }

            // Task 59 — the embedded icon (when the sender could fetch it):
            // materialized as a LOCAL file the record points at (a file://
            // URI — Coil's AsyncImage loads it directly; no network, no
            // placeholder). Falls back to the catalog/URL icons.
            val localIconUri = CsSharedPluginFormat.readExportIcon(sourceFile)?.let { bytes ->
                runCatching {
                    val iconDir = File(context.filesDir, "plugin_icons").apply { mkdirs() }
                    val iconFile = File(iconDir, "$internalName.png")
                    iconFile.writeBytes(bytes)
                    iconFile.toURI().toString()
                }.onFailure { t ->
                    Logger.w(TAG) { "importSharedPlugin — could not write the embedded icon: ${t.message}" }
                }.getOrNull()
            }

            val record = CsPluginRecord(
                internalName = internalName,
                name = manifest.name ?: internalName,
                url = online?.first?.url,
                filePath = target.absolutePath,
                version = manifest.version ?: online?.first?.version ?: 1,
                // Task 59: the source repository URL rides the record even
                // repo-less (displayed on the detail page; update math still
                // keys on ADDED repositories).
                repoUrl = linkedRepoUrl ?: exportInfo?.repoUrl,
                // Task 62: the catalog's internalName captured at link time —
                // every FUTURE identity comparison for this record is exact.
                repoInternalName = online?.first?.internalName,
                fileHash = importFileHash ?: online?.first?.fileHash,
                language = online?.first?.language ?: exportInfo?.language,
                // Task 59: the LOCAL embedded icon wins, then the catalog's,
                // then the exported URL.
                iconUrl = localIconUri ?: online?.first?.iconUrl ?: exportInfo?.iconUrl,
                isNsfw = false,
                authors = online?.first?.authors ?: exportInfo?.authors ?: emptyList(),
                description = online?.first?.description ?: exportInfo?.description,
                tvTypes = online?.first?.tvTypes ?: exportInfo?.tvTypes ?: emptyList(),
                fileSizeBytes = sourceFile.length(),
                // Fresh import = untrusted (the session-3 trust model; the
                // activity's confirm dialog adds the FILE, trust gates CODE).
                isTrusted = false,
            )
            pluginStore.upsert(record)
            // NOT loaded: untrusted records never classload (loadAll's rule).
            // The user trusts it from the detail page → trustPlugin loads it.
            refreshLocked()
            Logger.i(TAG) {
                "importSharedPlugin — placed $internalName v${record.version} " +
                    "(repo=${linkedRepoUrl ?: exportInfo?.repoUrl ?: "shared file"}, untrusted, " +
                    "icon=${if (localIconUri != null) "embedded" else if (record.iconUrl != null) "url" else "none"}, " +
                    "${sourceFile.length() / 1024} KB)"
            }
            CsImportResult.Added(record, linkedRepoUrl)
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

    /**
     * Task 62 (round 22): the sha256 of a plugin FILE in the repo's own
     * "sha256-<hex>" format (see CloudstreamPluginInstaller's verification) —
     * the shared identity rung for manual imports. Streamed (a .cs3 is a few
     * MB); a read failure returns null (the identity ladder degrades, never
     * crashes).
     */
    private fun computeFileHash(file: File): String? = runCatching {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        "sha256-" + digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

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
