package com.confused.anikuta.feature.extensionssettings.testing

import android.content.Context
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.data.cloudstream.content.CloudstreamContentRepository
import com.confused.anikuta.data.cloudstream.playback.CloudstreamLinkResolver
import com.confused.anikuta.data.extension.manager.ExtensionManager
import com.confused.anikuta.data.extension.model.AnimeExtension
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.koin.core.context.GlobalContext

/**
 * The app-scoped RUN CONTROLLER (round 84, D-583) — the single owner of the
 * extension-testing run lifecycle, shared by all five testing pages.
 *
 * WHY A SINGLETON (and not screen state): the round-83 overlay died with the
 * screen (leaving it mid-run cancelled the batch) and gated every control on
 * a screen-local `isBatchRunning` — which is exactly how the wedged-run
 * report became "the whole screen is unresponsive and Stop does nothing".
 * The controller's session lives in an APP-LEVEL scope, so:
 *   • navigating between Home / List / Run / Detail / Stats never kills it,
 *   • Stop and Skip are always wired to a live controller method, and
 *   • a hung extension can no longer freeze the UI (the engine's
 *     [TestIsolation] guarantees the run loop always advances).
 *
 * DI: resolved through Koin's GlobalContext (the PluginImportActivity
 * precedent) — the extensions-settings impl module has no Koin module of its
 * own and this file deliberately adds zero DI wiring.
 *
 * CORE_RULES §20: logged with tag "Anikuta:Feature:ExtensionsTesting".
 */
class ExtensionTestRunController private constructor(appContext: Context) {

    companion object {
        private const val TAG = "Anikuta:Feature:ExtensionsTesting"

        @Volatile
        private var instance: ExtensionTestRunController? = null

        /** Double-checked singleton — call with ANY context; the app's is kept. */
        fun get(context: Context): ExtensionTestRunController =
            instance ?: synchronized(this) {
                instance ?: ExtensionTestRunController(context.applicationContext)
                    .also { instance = it }
            }
    }

    private val extensionManager: ExtensionManager =
        GlobalContext.get().get()
    private val csContentRepository: CloudstreamContentRepository =
        GlobalContext.get().get()
    private val csResolver: CloudstreamLinkResolver =
        GlobalContext.get().get()

    /** The app-level scope — survives every navigation. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The result store (persisted verdicts + run history). */
    val resultStore = ExtensionTestResultStore(appContext)

    /** The test HTTP client (ping + stream play) — short timeouts + a hard callTimeout. */
    val testClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        // D-583: bounds the WHOLE call — a drip-feed server can otherwise
        // reset the read timeout per read and stretch STREAM_PLAY forever.
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    // ── Targets (live; rebuilt whenever the managers change) ────────────────

    private val _targets = MutableStateFlow<List<TestableTarget>>(emptyList())

    /** Every testable target across BOTH ecosystems, ecosystem-sorted. */
    val targets: StateFlow<List<TestableTarget>> = _targets.asStateFlow()

    init {
        scope.launch {
            combine(
                extensionManager.sources,
                extensionManager.installedExtensions,
                csContentRepository.sources,
            ) { sourcesMap, installed, csSources ->
                buildTargets(sourcesMap, installed, csSources)
            }.collect { list ->
                _targets.value = list
            }
        }
    }

    /** The live source object behind a target id (the tests call it). */
    fun sourceById(id: Long): AnimeCatalogueSource? =
        extensionManager.sources.value.values
            .filterIsInstance<AnimeCatalogueSource>()
            .firstOrNull { it.id == id }

    // ── Session state ────────────────────────────────────────────────────────

    private val _session = MutableStateFlow<RunSession?>(null)

    /** The live/completed run session (null = nothing has run yet). */
    val session: StateFlow<RunSession?> = _session.asStateFlow()

    private val skipSignal = AtomicBoolean(false)
    private var runJob: Job? = null

    /** The custom search query the Search test tries first (user-editable). */
    val searchQuery = MutableStateFlow("")

    // ── API ──────────────────────────────────────────────────────────────────

    /**
     * Starts a run over the given targets. `null` = every target; an empty
     * resolved queue (or a live run) refuses the start. Returns false when
     * the run did NOT start.
     */
    fun start(targetIds: List<Long>?, label: String): Boolean {
        if (_session.value?.phase == RunPhase.RUNNING) return false
        val all = _targets.value
        val queue = if (targetIds == null) {
            all.map { it.id }
        } else {
            targetIds.filter { id -> all.any { it.id == id } }
        }
        if (queue.isEmpty()) return false
        val byId = all.associateBy { it.id }
        _session.value = RunSession(
            phase = RunPhase.RUNNING,
            label = label,
            startedAtMs = System.currentTimeMillis(),
            queue = queue,
            cursor = 0,
            states = queue.associateWith { TargetRunState(isRunning = true) },
        )
        runJob = scope.launch {
            queue.forEachIndexed { index, id ->
                val target = byId[id] ?: return@forEachIndexed
                setSession { it?.copy(cursor = index + 1, currentTargetId = id) }
                skipSignal.set(false)
                runOne(target)
            }
            setSession { it?.copy(phase = RunPhase.COMPLETED) }
            appendHistoryFromSession()
            Logger.i(TAG) { "Run completed: \"$label\"" }
        }.also { job ->
            job.invokeOnCompletion {
                if (_session.value?.phase == RunPhase.RUNNING) {
                    // A cancel (Stop) — settle any mid-run spinner and record
                    // the session as STOPPED with a partial summary. Partial
                    // targets stay unfinished (a killed run is not a verdict).
                    setSession { session ->
                        session?.copy(
                            phase = RunPhase.STOPPED,
                            states = session.states.mapValues { (_, s) ->
                                if (s.isRunning) s.copy(isRunning = false, runningKind = null) else s
                            },
                        )
                    }
                    appendHistoryFromSession()
                }
            }
        }
        Logger.i(TAG) { "Run started: \"$label\" over ${queue.size} target(s)" }
        return true
    }

    /** Stops the live run (safe to call anytime — no-op when idle). */
    fun stop() {
        runJob?.cancel()
        runJob = null
    }

    /**
     * Skips the CURRENT target — the "stuck test" escape hatch (the round-84
     * report asked for it explicitly). The engine marks the current + every
     * remaining kind of this target SKIPPED and the run advances.
     */
    fun skipCurrent() {
        skipSignal.set(true)
    }

    /** The failed/aborted ids of the last session (fallback: the store). */
    private fun failedTargetIds(): List<Long> {
        val s = _session.value
        if (s != null) {
            val ids = s.queue.filter { id ->
                s.states[id]?.let { it.finished && !it.isHealthy } == true
            }
            if (ids.isNotEmpty()) return ids
        }
        return resultStore.loadAll()
            .filter { (_, run) ->
                run.finished && (run.abortedByUser ||
                    run.results.values.any { it.status == TestStatus.FAILED })
            }
            .keys
            .toList()
    }

    /** The healthy ids of the last session (fallback: the store). */
    private fun passedTargetIds(): List<Long> {
        val s = _session.value
        if (s != null) {
            val ids = s.queue.filter { id -> s.states[id]?.isHealthy == true }
            if (ids.isNotEmpty()) return ids
        }
        return resultStore.loadAll()
            .filter { (_, run) ->
                run.finished && !run.abortedByUser &&
                    run.results.values.none { it.status == TestStatus.FAILED } &&
                    run.results.values.any { it.status == TestStatus.PASSED }
            }
            .keys
            .toList()
    }

    /** Re-runs exactly the failed/aborted targets. */
    fun rerunFailed(): Boolean {
        val ids = failedTargetIds()
        return ids.isNotEmpty() && start(ids, "Failed only")
    }

    /** Re-runs exactly the healthy targets. */
    fun rerunPassed(): Boolean {
        val ids = passedTargetIds()
        return ids.isNotEmpty() && start(ids, "Passed only")
    }

    /** Clears the persisted store AND the in-memory session verdicts. */
    fun clearResults() {
        resultStore.clear()
        if (_session.value?.phase != RunPhase.RUNNING) {
            _session.value = null
        } else {
            setSession { it?.copy(states = it.states.mapValues { (_, s) -> s.copy(results = emptyMap()) }) }
        }
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private inline fun setSession(transform: (RunSession?) -> RunSession?) {
        _session.value = transform(_session.value)
    }

    /** One target's full chain run (suspend — the run loop awaits it). */
    private suspend fun runOne(target: TestableTarget) {
        val source = sourceById(target.id) ?: return
        val engine = ExtensionTestEngine(ExtensionTestChain.build(testClient, csResolver))
        val testContext = ExtensionTestContext(
            source = source,
            target = target,
            // Blank → the Search test runs ONLY the smart phrase ladder
            // (the custom query is tried first when the user typed one).
            searchQuery = searchQuery.value.trim(),
        )
        val outcome = engine.run(testContext, skipSignal) { kind, result ->
            setSession { session ->
                session ?: return@setSession null
                val prev = session.states[target.id] ?: TargetRunState()
                val updated = prev.copy(
                    results = prev.results + (kind to result),
                    runningKind = if (result.status == TestStatus.RUNNING) kind else null,
                )
                session.copy(states = session.states + (target.id to updated))
            }
        }
        val finalState = (_session.value?.states?.get(target.id) ?: TargetRunState()).copy(
            isRunning = false,
            runningKind = null,
            finished = true,
            abortedByUser = outcome.abortedByUser,
        )
        setSession { session ->
            session?.copy(states = session.states + (target.id to finalState))
        }
        // Persist the finished verdict immediately — a crash or a process
        // death mid-run never loses completed targets (the D-579 contract).
        resultStore.saveTarget(target, finalState)
    }

    /** Computes + persists the run summary for the stats page's history. */
    private fun appendHistoryFromSession() {
        val s = _session.value ?: return
        val states = s.states.values.filter { it.finished }
        val passed = states.count { it.isHealthy }
        val failed = states.count { it.finished && !it.isHealthy && !it.abortedByUser }
        val aborted = states.count { it.finished && it.abortedByUser }
        val kinds = mutableMapOf<String, ExtensionTestResultStore.StoredKindStat>()
        states.forEach { state ->
            state.results.forEach { (kind, result) ->
                if (result.status == TestStatus.PENDING || result.status == TestStatus.RUNNING) return@forEach
                val key = kind.name
                val prev = kinds[key]
                val runCount = (prev?.runCount ?: 0) + 1
                val failCount = (prev?.failCount ?: 0) +
                    if (result.status == TestStatus.FAILED) 1 else 0
                val totalMs = (prev?.avgMs ?: 0L) * (runCount - 1) + result.durationMs
                kinds[key] = ExtensionTestResultStore.StoredKindStat(
                    avgMs = totalMs / runCount,
                    failCount = failCount,
                    runCount = runCount,
                )
            }
        }
        val allTargets = s.queue.size
        resultStore.appendHistory(
            ExtensionTestResultStore.StoredRunSummary(
                startedAtMs = s.startedAtMs,
                finishedAtMs = System.currentTimeMillis(),
                scope = when {
                    allTargets == _targets.value.size && allTargets > 1 -> "all"
                    s.label.contains("Aniyomi", ignoreCase = true) -> "aniyomi"
                    s.label.contains("CloudStream", ignoreCase = true) -> "cloudstream"
                    s.label.contains("Failed", ignoreCase = true) -> "failed"
                    s.label.contains("Passed", ignoreCase = true) -> "passed"
                    allTargets == 1 -> "single"
                    else -> "selection"
                },
                label = s.label,
                totalTargets = allTargets,
                passed = passed,
                failed = failed,
                aborted = aborted,
                kinds = kinds,
            ),
        )
    }
}

/** The run's lifecycle phase. */
enum class RunPhase { RUNNING, COMPLETED, STOPPED }

/**
 * The live run session — the single source of truth every testing page
 * renders (immutable snapshots on every emission).
 */
data class RunSession(
    val phase: RunPhase,
    val label: String,
    val startedAtMs: Long,
    /** Resolved target ids, run order preserved. */
    val queue: List<Long>,
    /** 1-based index of the target currently running / last completed. */
    val cursor: Int = 0,
    val currentTargetId: Long? = null,
    val states: Map<Long, TargetRunState> = emptyMap(),
) {
    val testedCount: Int get() = states.values.count { it.finished }
    val passedCount: Int get() = states.values.count { it.isHealthy }
    val failedCount: Int
        get() = states.values.count { it.finished && !it.isHealthy && !it.abortedByUser }
    val abortedCount: Int get() = states.values.count { it.finished && it.abortedByUser }
}

/**
 * The unified source list split into testable targets (aniyomi first,
 * name-sorted inside ecosystems) — moved from the round-82 screen so the
 * controller (and every page) reads the same list.
 */
private fun buildTargets(
    sourcesMap: Map<Long, AnimeSource>,
    installedExtensions: List<AnimeExtension.Installed>,
    csProviderSources: List<com.confused.anikuta.data.cloudstream.content.CsProviderSource>,
): List<TestableTarget> {
    val aniyomiIcons = buildMap {
        installedExtensions.filter { it.isEnabled }.forEach { ext ->
            ext.sources.forEach { src ->
                ext.icon?.let { put(src.id, it) }
            }
        }
    }
    val csIcons = csProviderSources.associate { it.providerName to it.pluginIconUrl }

    return sourcesMap.values
        .filterIsInstance<AnimeCatalogueSource>()
        .map { src ->
            val bridged = (src as? eu.kanade.tachiyomi.animesource.online.AnimeHttpSource)
                ?.isCloudStreamBridged == true
            TestableTarget(
                id = src.id,
                name = src.name,
                ecosystem = if (bridged) TestEcosystem.CLOUDSTREAM else TestEcosystem.ANIYOMI,
                lang = src.lang,
                iconDrawable = if (bridged) null else aniyomiIcons[src.id],
                iconUrl = if (bridged) csIcons[src.name] else null,
                providerName = if (bridged) src.name else null,
                baseUrl = (src as? eu.kanade.tachiyomi.animesource.online.AnimeHttpSource)?.baseUrl,
            )
        }
        .sortedWith(compareBy({ it.ecosystem.ordinal }, { it.name.lowercase() }))
}
