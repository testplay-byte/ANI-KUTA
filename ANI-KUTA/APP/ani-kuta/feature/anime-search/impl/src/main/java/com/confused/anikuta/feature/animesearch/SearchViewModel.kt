package com.confused.anikuta.feature.animesearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.anilist.api.AniListApi
import com.confused.anikuta.core.anilist.api.BrowseCacheCodec
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.datacache.DataCacheRepository
import com.confused.anikuta.core.preferences.AppPreferences
import com.confused.anikuta.core.preferences.PreferenceStore
import com.confused.anikuta.data.cloudstream.content.CloudstreamContentRepository
import com.confused.anikuta.data.cloudstream.content.CsBrowseDisplay
import com.confused.anikuta.data.cloudstream.content.CsBrowseDisplayRow
import com.confused.anikuta.data.cloudstream.content.CsBrowseSection
import com.confused.anikuta.data.cloudstream.content.CsContentCard
import com.confused.anikuta.data.cloudstream.content.CsProviderSource
import com.confused.anikuta.data.extension.manager.ExtensionManager
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.CloudflareException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ViewModel for the Search screen.
 *
 * Two source modes:
 * - **ANILIST** (default): searches AniList by title. When no query is entered,
 *   shows trending anime (Phase 5a improvement per user request).
 * - **EXTENSION**: browses a selected extension source's popular/latest anime.
 *   The user picks a source via a bottom sheet (all trusted sources listed).
 *   The selection is persisted — the user sees that source's results by default
 *   until they change it or the source is uninstalled.
 *
 * CORE_RULES §20: Logged with tag "Anikuta:Feature:Search".
 * CORE_RULES §23: Reactive state (StateFlow).
 */
class SearchViewModel(
    private val anilistApi: AniListApi,
    private val dataCacheRepository: DataCacheRepository,
    private val preferenceStore: PreferenceStore,
    private val extensionManager: ExtensionManager,
    private val cloudstreamRepository: CloudstreamContentRepository,
    private val appPreferences: AppPreferences,
    private val activityTracker: com.confused.anikuta.core.activitytracker.ActivityTracker,
) : ViewModel() {

    companion object {
        private const val TAG = "Anikuta:Feature:Search"
        private const val KEY_RECENT_SEARCHES = "search_recent_anilist"
        private const val KEY_SELECTED_SOURCE_ID = "search_selected_extension_source_id"

        /** Session 3 (CloudStream execution): which ecosystem the EXTENSION mode browses. */
        private const val KEY_SELECTED_SOURCE_KIND = "search_selected_source_kind"

        /** The selected CloudStream provider name (MainAPI.name) when kind = cloudstream. */
        private const val KEY_SELECTED_CS_PROVIDER = "search_selected_cs_provider"
        private const val DEBOUNCE_MS = 350L
        private const val MAX_RECENTS = 10

        /**
         * Task 62 (round 22): the cross-section uniqueness window of the smart
         * shuffle — the first N items of any randomized category must not
         * appear in the first N of any other one (the device spec: "the first
         * four of any of the categories will not be the same as any other").
         */
        private const val TOP_UNIQUE_ITEMS = 4

        /** Persisted kind flag values (KEY_SELECTED_SOURCE_KIND). */
        private const val KIND_ANIYOMI = "aniyomi"
        private const val KIND_CLOUDSTREAM = "cloudstream"

        /**
         * Task 46: how long the CS-selection heal waits for the plugin
         * manager's first load (activity-gated) before validating anyway.
         * Generous vs. the manager's own 15s activity timeout so a normal
         * cold start ALWAYS has the real provider list first.
         */
        private const val CS_LOAD_WAIT_MS = 20_000L

        /**
         * Task 47: once the manager reports loadedOnce, its `installed` list
         * is final — the derived WhileSubscribed chain only needs a few
         * dispatch hops to propagate it. A short post-load wait for a
         * non-empty list is plenty; longer than this with an empty list means
         * zero providers are actually loaded.
         */
        private const val CS_POST_LOAD_WAIT_MS = 3_000L

        /** Task 47: the persisted top-tab values (KEY_SEARCH_SOURCE). */
        private const val KEY_SEARCH_SOURCE = "search_selected_source"
        private const val SOURCE_ANILIST = "anilist"
        private const val SOURCE_EXTENSION = "extension"
    }

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    // Task 47 (device round 6, "the tab resets to Anime list after restarting"):
    // the top tab (AniList / Extension) is PERSISTED — restored here and written
    // on every switch. Previously it lived only in memory, so a full app restart
    // always landed back on AniList.
    private val _source = MutableStateFlow(
        if (preferenceStore.getString(KEY_SEARCH_SOURCE, SOURCE_ANILIST) == SOURCE_EXTENSION) {
            SearchSource.EXTENSION
        } else {
            SearchSource.ANILIST
        },
    )
    val source: StateFlow<SearchSource> = _source.asStateFlow()

    private val _sort = MutableStateFlow(SearchSort.POPULARITY)
    val sort: StateFlow<SearchSort> = _sort.asStateFlow()

    private val _recents = MutableStateFlow<List<String>>(emptyList())
    val recents: StateFlow<List<String>> = _recents.asStateFlow()

    private val _pendingFilters = MutableStateFlow(SearchFilters.Empty)
    val pendingFilters: StateFlow<SearchFilters> = _pendingFilters.asStateFlow()

    private val _appliedFilters = MutableStateFlow(SearchFilters.Empty)
    val appliedFilters: StateFlow<SearchFilters> = _appliedFilters.asStateFlow()

    // D-258: default-content bookkeeping. `showingDefaults` is true when the
    // current Success/ExtensionSuccess state holds DEFAULT (blank-query)
    // content — trending in AniList mode, the selected source's popular in
    // Extension mode. `defaultsJob` de-dupes concurrent default loads.
    private var showingDefaults = false
    private var defaultsJob: Job? = null

    // D-305: request identity. Every user intent that starts a new load (query
    // change, mode switch, source switch, retry) funnels through
    // [beginRequest], which bumps [requestGeneration] and cancels in-flight
    // loads. Each loader captures the generation at launch and only writes UI
    // state while still current. This kills the device-reported races where a
    // slower, superseded response (an older query or ANOTHER source) completed
    // last and overwrote newer state ("reverts to an older state / shows the
    // result from some other extension").
    private var searchJob: Job? = null
    private var requestGeneration = 0

    // ── Task 61 (round 21): the search page's LOAD-MORE paging state ──
    /** Which loader produced the current content — [loadMore] re-runs it at page+1. */
    private var pagingMode: PagingMode? = null

    /** The page of the CURRENT content (loadMore fetches page+1). */
    private var lastLoadedPage = 1

    /** The in-flight load-more job (canceled by [beginRequest] like the others). */
    private var loadMoreJob: Job? = null

    /**
     * Task 61 (round 21): the loader identities [loadMore] can continue —
     * one per content-producing path (the CS BROWSE feed pages in its own
     * category subpages, not here).
     */
    private enum class PagingMode {
        ANILIST_TRENDING,
        ANILIST_SEARCH,
        ANIYOMI_POPULAR,
        ANIYOMI_SEARCH,
        CS_SEARCH,
    }

    /** D-305: starts a new request — cancels superseded loads, returns its generation. */
    private fun beginRequest(): Int {
        searchJob?.cancel()
        defaultsJob?.cancel()
        loadMoreJob?.cancel()
        searchJob = null
        defaultsJob = null
        loadMoreJob = null
        pagingMode = null
        // The "defaults are showing" bookkeeping is invalidated by any new
        // request; the loader re-establishes it when it actually serves defaults.
        showingDefaults = false
        return ++requestGeneration
    }

    /** D-305: true while [gen] is still the latest request (nothing superseded it). */
    private fun isCurrent(gen: Int): Boolean = gen == requestGeneration

    /**
     * The trusted extension sources available for browsing (the picker's
     * "Anime Extensions" section).
     *
     * Task 45: bridged CloudStream sources now live in the same map (so the
     * standard details screen resolves them) — they are EXCLUDED here: the
     * picker lists CS providers in its own dedicated CloudStream section, and
     * a provider appearing in both would be selectable twice.
     */
    val trustedSources: StateFlow<List<AnimeCatalogueSource>> =
        extensionManager.sources.map { sourceMap ->
            sourceMap.values.filterIsInstance<AnimeCatalogueSource>()
                // R11-REVIEW F2: classify by the ECOSYSTEM MARKER, never the id
                // bit — aniyomi's MD5 ids clear only the sign bit, so bit 62 is
                // random (~50% of real aniyomi sources set it) and the old
                // isCloudstreamId() predicate silently hid them from the picker
                // AND stole their persisted selection on every cold start.
                .filterNot { (it as? eu.kanade.tachiyomi.animesource.online.AnimeHttpSource)?.isCloudStreamBridged == true }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Map of source ID → extension icon Drawable (for the source picker UI).
     * Built from the installed extensions list — each extension has an `icon`
     * field and a list of sources. We map every source ID to its parent
     * extension's icon.
     */
    val sourceIcons: StateFlow<Map<Long, android.graphics.drawable.Drawable>> =
        extensionManager.installedExtensions.map { extensions ->
            buildMap {
                // Phase 1c: only include icons for ENABLED extensions (was: ALL trusted).
                extensions.filter { it.isEnabled }.forEach { ext ->
                    ext.sources.forEach { source ->
                        ext.icon?.let { put(source.id, it) }
                    }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** The currently selected extension source ID (persisted). Null = none selected. */
    private val _selectedSourceId = MutableStateFlow<Long?>(
        preferenceStore.getLong(KEY_SELECTED_SOURCE_ID, -1L).takeIf { it > 0 }
    )
    val selectedSourceId: StateFlow<Long?> = _selectedSourceId.asStateFlow()

    // ── Session 3 (CloudStream execution phase 1): the EXTENSION mode can browse
    // EITHER ecosystem. The selected-source identity is (kind, id) — aniyomi's
    // Long id or a CloudStream provider NAME — persisted as one kind flag + the
    // two existing/legacy values (doc 16 §5.2 string-key discipline; the legacy
    // aniyomi pref is untouched so pre-session installs decode exactly as before).
    private val _selectedKind = MutableStateFlow(
        if (preferenceStore.getString(KEY_SELECTED_SOURCE_KIND, KIND_ANIYOMI) == KIND_CLOUDSTREAM) {
            SelectedSourceKind.CLOUDSTREAM
        } else {
            SelectedSourceKind.ANIYOMI
        },
    )
    val selectedKind: StateFlow<SelectedSourceKind> = _selectedKind.asStateFlow()

    private val _selectedCsProvider = MutableStateFlow<String?>(
        preferenceStore.getString(KEY_SELECTED_CS_PROVIDER, "").takeIf { it.isNotBlank() },
    )
    val selectedCsProvider: StateFlow<String?> = _selectedCsProvider.asStateFlow()

    /**
     * The CloudStream sources available in the picker — every provider of every
     * TRUSTED plugin, filtered by the persisted NSFW gate (G4) when it is OFF.
     *
     * The gate is read inside the map: the flow re-collects on every
     * re-subscription (screen re-entry — the only way the gate can change is
     * via the Extensions settings screen), so the filter is always fresh where
     * it matters; no preference-change plumbing needed.
     */
    val csSources: StateFlow<List<CsProviderSource>> = cloudstreamRepository.sources
        .map { sources ->
            if (appPreferences.cloudstreamShowNsfw) sources else sources.filterNot { it.isNsfw }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadRecents()
        observeQuery()
        // D-248/D-258: load default (trending) content on first entry in
        // AniList mode — routed through loadDefaults() so it shares the
        // dedup guard with the debounced collector's initial blank emission.
        loadDefaults()
        // Auto-select the top trusted source if none is selected (per user spec).
        // When the user switches to Extension mode, they see results immediately.
        viewModelScope.launch {
            trustedSources.collect { sources ->
                if (sources.isNotEmpty()) {
                    val current = _selectedSourceId.value
                    val currentExists = current != null && sources.any { it.id == current }
                    // Session 3: never steal the selection while a CloudStream
                    // provider is the active source — the aniyomi auto-select only
                    // fills an EMPTY/invalid ANIYOMI selection.
                    if (_selectedKind.value == SelectedSourceKind.ANIYOMI &&
                        (current == null || !currentExists)
                    ) {
                        val top = sources.first()
                        _selectedSourceId.value = top.id
                        preferenceStore.putLong(KEY_SELECTED_SOURCE_ID, top.id)
                        // If the user is already in Extension mode, load the new
                        // source's popular — but NEVER interrupt a live search
                        // (D-305: the collector fires on any trust/reload of the
                        // sources map; racing an in-flight search would cancel
                        // and replace the user's results mid-typing).
                        if (_source.value == SearchSource.EXTENSION && _query.value.isBlank()) {
                            loadExtensionPopular()
                        }
                    }
                }
            }
        }
        // Session 3: validate the persisted CloudStream selection — if its
        // plugin was uninstalled/untrusted, fall back to the aniyomi kind (which
        // the collector above then heals with an auto-select).
        //
        // Task 46 gated this heal on `sourcesLoaded` — but the device (round 6)
        // still lost the CloudStream selection on every restart. Root cause: the
        // validation ran against the FIRST emission of `csSources`, a TWO-layer
        // `stateIn(WhileSubscribed)` chain whose initial value is `emptyList()`;
        // the manager finishing its load does NOT mean the derived flow has
        // propagated yet (it needs several dispatch hops AFTER its first
        // subscriber attaches — and this collector was that first subscriber).
        // Result: "provider gone" against the stale empty list → the persisted
        // kind was overwritten with "aniyomi" on virtually every cold start.
        //
        // Task 47 fix: validate against the RAW repository flow and only after
        // it has emitted a NON-EMPTY list (or the wait budget expired, meaning
        // zero plugins are actually loaded). The ongoing collector then watches
        // for RUNTIME uninstalls — an empty emission after the gates is REAL,
        // never a cold-start artifact.
        viewModelScope.launch {
            val current = _selectedCsProvider.value
            if (_selectedKind.value != SelectedSourceKind.CLOUDSTREAM) {
                return@launch // aniyomi selection — nothing to validate.
            }
            val resolved = current?.let { awaitCsSource(it) }
            if (current != null && resolved != null) {
                Logger.i(TAG) { "CloudStream selection '$current' restored (provider is loaded)" }
                // Keep watching for runtime uninstalls while the screen is alive.
                cloudstreamRepository.sources.collect { sources ->
                    // Empty AFTER the non-empty gate = zero providers remain —
                    // the selection can no longer be honored.
                    if (sources.isEmpty() || sources.none { it.providerName == _selectedCsProvider.value }) {
                        if (_selectedKind.value != SelectedSourceKind.CLOUDSTREAM) return@collect
                        Logger.i(TAG) {
                            "Selected CS provider '${_selectedCsProvider.value}' is gone — resetting extension mode to aniyomi"
                        }
                        selectAniyomiKind(persist = true)
                        if (_source.value == SearchSource.EXTENSION && _query.value.isBlank()) {
                            loadExtensionPopular()
                        }
                    }
                }
            } else {
                Logger.i(TAG) {
                    "Selected CS provider '$current' is gone — resetting extension mode to aniyomi"
                }
                selectAniyomiKind(persist = true)
                if (_source.value == SearchSource.EXTENSION && _query.value.isBlank()) {
                    loadExtensionPopular()
                }
            }
        }
    }

    /**
     * Task 47: resolves the RAW (un-NSFW-filtered) provider entry for
     * [providerName], cold-start safe. The two-layer WhileSubscribed chain
     * (repository.sources → csSources) legitimately starts at `emptyList()` —
     * concluding "provider gone" from that initial value destroyed persisted
     * selections on every restart (device round 6). This helper:
     *   1. fast-paths when the live list already carries the provider;
     *   2. waits (bounded) for the manager's activity-gated first load;
     *   3. waits (bounded) for a NON-EMPTY derived list — only a list that
     *      actually loaded can prove a provider missing.
     * Returns null only when the provider is genuinely not loaded.
     */
    private suspend fun awaitCsSource(providerName: String): CsProviderSource? {
        val raw = cloudstreamRepository.sources
        // Fast path: warm list already carries the provider.
        raw.value.firstOrNull { it.providerName == providerName }?.let { return it }
        // Cold path: manager's initial load is activity-gated (≤15s)…
        withTimeoutOrNull(CS_LOAD_WAIT_MS) { cloudstreamRepository.sourcesLoaded.first { it } }
        // …then wait for the derived chain to carry a real (non-empty) list.
        // loadedOnce ⇒ `installed` is final, so a short wait suffices; a
        // timeout here means zero trusted plugins are loaded at all.
        withTimeoutOrNull(CS_POST_LOAD_WAIT_MS) { raw.first { it.isNotEmpty() } }
        return raw.value.firstOrNull { it.providerName == providerName }
    }

    // ── Filter handlers ──

    fun onPendingFiltersChange(filters: SearchFilters) {
        _pendingFilters.value = filters
    }

    fun onClearAllFilters() {
        _pendingFilters.value = SearchFilters.Empty
        _appliedFilters.value = SearchFilters.Empty
    }

    fun onApplyFilters() {
        _appliedFilters.value = _pendingFilters.value
    }

    fun onQueryChange(value: String) {
        _query.value = value
        if (value.isBlank()) {
            // D-258: backspacing to empty restores the default results (was:
            // a hard Idle reset that left the page permanently empty).
            loadDefaults()
        }
    }

    fun onClearQuery() {
        _query.value = ""
        // D-258: clearing via the X button restores the default results —
        // fixes the device-reported bug where the defaults never came back
        // after a search (the old code set Idle and nothing ever reloaded).
        loadDefaults()
    }

    fun onSourceChange(source: SearchSource) {
        _source.value = source
        // Task 47: persist the tab so it survives a full app restart.
        preferenceStore.putString(
            KEY_SEARCH_SOURCE,
            if (source == SearchSource.EXTENSION) SOURCE_EXTENSION else SOURCE_ANILIST,
        )
        if (source == SearchSource.EXTENSION) {
            // D-305: with a live query, SEARCH the selected source immediately.
            // Previously this always loaded popular and then DISCARDED it at the
            // blankness guard — the user saw nothing for the new mode while a
            // stale AniList/old-source response could still land afterwards.
            if (_query.value.isNotBlank()) {
                search(_query.value)
            } else if (_selectedKind.value == SelectedSourceKind.CLOUDSTREAM) {
                loadCloudstreamPopular()
            } else {
                loadExtensionPopular()
            }
        } else {
            if (_query.value.isNotBlank()) {
                search(_query.value)
            } else {
                loadTrending()
            }
        }
    }

    fun onSortChange(sort: SearchSort) {
        _sort.value = sort
        if (_query.value.isNotBlank()) {
            search(_query.value)
        }
    }

    /**
     * D-209: Retry the last extension search/browse.
     * Called from the SearchScreen error cards (CloudflareBlocked, ExtensionEmpty,
     * ExtensionError) when the user taps "Refresh" / "Retry".
     * If the user has a query → re-search; otherwise → reload popular.
     */
    fun retryExtensionSearch() {
        val src = source.value
        if (src != SearchSource.EXTENSION) return
        if (_selectedKind.value == SelectedSourceKind.CLOUDSTREAM) {
            if (_selectedCsProvider.value == null) {
                beginRequest() // D-305 review fix: supersede in-flight requests.
                _uiState.value = SearchUiState.ExtensionNotAvailable
                return
            }
            if (_query.value.isNotBlank()) {
                search(_query.value)
            } else {
                loadCloudstreamPopular()
            }
            return
        }
        if (_selectedSourceId.value == null) {
            beginRequest() // D-305 review fix: supersede in-flight requests.
            _uiState.value = SearchUiState.ExtensionNotAvailable
            return
        }
        if (_query.value.isNotBlank()) {
            search(_query.value)
        } else {
            loadExtensionPopular()
        }
    }

    /**
     * D-210: Set when the user opens the Cloudflare WebView from the Search screen.
     * When the Search screen resumes (user returns from the WebView), it checks
     * this flag + auto-refreshes if true. Cleared after the refresh is triggered.
     */
    var pendingWebViewRefresh: Boolean = false
        private set

    /**
     * Task 62 (round 22 — the randomization TRIGGER rework): called from the
     * screen's LaunchedEffect(Unit) — fires on every FRESH composition of the
     * search screen (tab returns AND subpage returns). The v0.4.9 behavior
     * (reshuffle on every entry) caused the round-22 device reports:
     * returning from a category subpage or from content re-randomized the
     * page. Now the reshuffle ONLY runs when the user actually LEFT the
     * search tab in between (MainActivity's bottom-nav exit marks
     * [SearchTabExitSignal] — subpage pushes stay INSIDE the tab) or after a
     * pull-to-refresh. The persisted display arrangement handles cold
     * reopens (restored in loadCloudstreamPopular — never re-shuffled).
     */
    fun onPageEntered() {
        val current = _uiState.value
        if (_query.value.isBlank() && current is SearchUiState.ExtensionBrowseSuccess) {
            if (SearchTabExitSignal.shouldReshuffleOnEntry()) {
                SearchTabExitSignal.markShuffled()
                val shuffled = smartShuffleSections(current.sections)
                _uiState.value = current.copy(sections = shuffled)
                persistBrowseDisplay(current.sourceName, shuffled)
                Logger.i(TAG) {
                    "onPageEntered — search tab re-entered: ${shuffled.size} section(s) " +
                        "re-randomized (cross-section top-4 uniqueness applied)"
                }
            }
        }
    }

    /** D-210: Called by the SearchScreen when the user taps "Open in WebView". */
    fun onOpenWebView() {
        pendingWebViewRefresh = true
    }

    /** D-210: Called by the SearchScreen on resume — auto-refreshes if the flag is set. */
    fun onScreenResume() {
        if (pendingWebViewRefresh) {
            pendingWebViewRefresh = false
            retryExtensionSearch()
            return
        }
        // D-258: defense-in-depth — if the screen is re-entered while Idle with
        // a blank query (e.g. trending failed earlier), restore the defaults.
        if (_query.value.isBlank() && _uiState.value is SearchUiState.Idle) {
            loadDefaults()
        }
        // Task 62 (round 22): the round-21 "reshuffle on EVERY resume" branch is
        // REMOVED — an activity-level ON_RESUME (returning from the launcher,
        // the Cloudflare WebView, a chat app…) is NOT "leaving the search page".
        // Re-randomization now happens ONLY on a real tab exit + return
        // (SearchTabExitSignal, see onPageEntered) or a pull-to-refresh.
    }

    fun onSubmit() {
        val q = _query.value.trim()
        if (q.isBlank()) return
        addRecent(q)
        search(q)
    }

    fun onPickRecent(term: String) {
        _query.value = term
        search(term)
    }

    fun onRemoveRecent(term: String) {
        val updated = _recents.value.filter { it != term }
        _recents.value = updated
        persistRecents(updated)
    }

    fun onClearRecents() {
        _recents.value = emptyList()
        persistRecents(emptyList())
    }

    /**
     * Select an extension source for browsing. Persists the choice.
     * D-305: with a live query, searches the NEWLY selected source for that
     * query — previously it loaded popular and discarded it, while the OLD
     * source's in-flight search could still complete and show its results
     * under the new selection (the "result from some other extension" bug).
     */
    fun onSelectExtensionSource(sourceId: Long) {
        _selectedSourceId.value = sourceId
        preferenceStore.putLong(KEY_SELECTED_SOURCE_ID, sourceId)
        selectAniyomiKind(persist = true)
        if (_query.value.isNotBlank()) {
            search(_query.value)
        } else {
            loadExtensionPopular()
        }
    }

    /**
     * Session 3: select a CloudStream provider as the EXTENSION-mode source.
     * Persists (kind + provider name) and immediately loads content — browse
     * (mainPage) with a blank query, search otherwise; a provider without
     * mainPage lands on the NoBrowse state prompting the user to type.
     */
    fun onSelectCloudstreamSource(providerName: String) {
        _selectedCsProvider.value = providerName
        preferenceStore.putString(KEY_SELECTED_CS_PROVIDER, providerName)
        _selectedKind.value = SelectedSourceKind.CLOUDSTREAM
        preferenceStore.putString(KEY_SELECTED_SOURCE_KIND, KIND_CLOUDSTREAM)
        Logger.i(TAG) { "Extension source switched to CloudStream provider '$providerName'" }
        if (_query.value.isNotBlank()) {
            search(_query.value)
        } else {
            loadCloudstreamPopular()
        }
    }

    /** Resets the extension-mode kind to aniyomi (selection healing paths). */
    private fun selectAniyomiKind(persist: Boolean) {
        _selectedKind.value = SelectedSourceKind.ANIYOMI
        if (persist) preferenceStore.putString(KEY_SELECTED_SOURCE_KIND, KIND_ANIYOMI)
    }

    @OptIn(FlowPreview::class)
    private fun observeQuery() {
        _query
            .debounce(DEBOUNCE_MS)
            .distinctUntilChanged()
            .onEach { q ->
                if (q.isBlank()) {
                    // D-258: query became blank (X clear / backspace) — restore
                    // the default results. loadDefaults() is idempotent, so the
                    // immediate call from onClearQuery/onQueryChange and this
                    // debounced one don't double-fetch.
                    loadDefaults()
                } else {
                    // D-242-fix: record the search term in history.
                    addRecent(q.trim())
                    search(q)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun search(q: String) {
        if (_source.value == SearchSource.EXTENSION) {
            if (_selectedKind.value == SelectedSourceKind.CLOUDSTREAM) {
                searchCloudstream(q)
            } else {
                searchExtension(q)
            }
            return
        }

        // D-192: track the search event
        activityTracker.track(
            eventType = com.confused.anikuta.core.activitytracker.ActivityEventType.SEARCH,
            route = "search",
            payload = q,
        )

        showingDefaults = false
        val gen = beginRequest()
        _uiState.value = SearchUiState.Loading
        searchJob = viewModelScope.launch {
            try {
                Logger.i(TAG) { "Searching AniList for '$q' (sort=${_sort.value.apiValue})" }
                val results = anilistApi.searchAnime(
                    query = q,
                    page = 1,
                    perPage = 30,
                    sort = _sort.value.apiValue,
                )
                Logger.i(TAG) { "Got ${results.size} results" }
                // D-258 staleness guard: the query was cleared while this
                // search was in flight — don't clobber the restored defaults.
                if (_query.value.isBlank()) return@launch
                // D-305: a newer request superseded this one — drop the result.
                if (!isCurrent(gen)) return@launch
                // Task 61 (round 21): record the paging context — the grid's
                // approach-bottom trigger continues THIS search at page 2+.
                pagingMode = PagingMode.ANILIST_SEARCH
                lastLoadedPage = 1
                _uiState.value = if (results.isEmpty()) {
                    SearchUiState.Empty
                } else {
                    SearchUiState.Success(results = results, hasMore = results.size >= 30)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Logger.e(TAG, e) { "Search failed for '$q': ${e.message}" }
                if (_query.value.isBlank()) return@launch
                if (!isCurrent(gen)) return@launch
                _uiState.value = SearchUiState.Error
            }
        }
    }

    /**
     * D-258: loads the blank-query default content (AniList trending, or the
     * selected extension source's popular). Idempotent: skips while a default
     * load is already in flight or when defaults are already showing. Every
     * "query became blank" path funnels here (X clear, backspace-to-empty,
     * debounced collector, init, screen re-entry) — this fixes the
     * device-reported bug where default results never came back after clearing
     * a search.
     */
    private fun loadDefaults() {
        if (_query.value.isNotBlank()) return
        if (defaultsJob?.isActive == true) return
        if (showingDefaults) return
        defaultsJob = if (_source.value == SearchSource.ANILIST) {
            loadTrending()
        } else if (_selectedKind.value == SelectedSourceKind.CLOUDSTREAM) {
            loadCloudstreamPopular()
        } else {
            loadExtensionPopular()
        }
    }

    /**
     * Load trending anime from AniList (shown when AniList source is active +
     * query is blank).
     *
     * D-278: cache-first. The Browse screen already caches the EXACT same
     * AniList TRENDING query (shared [BrowseCacheCodec] + `browse_cache` table).
     * So we serve that cached payload INSTANTLY (a user who opened Browse once
     * already has it populated), then refresh from network. Lets the search
     * page "show default results without internet" per the user's request —
     * no blank screen, no 30s network timeout before content appears offline.
     *
     * Flow: Loading → (cache hit → Success(cached)) → network refresh →
     *   (success → Success(fresh)) / (fail + cache was shown → keep cache) /
     *   (fail + no cache → Idle).
     */
    private fun loadTrending(): Job {
        val gen = beginRequest()
        _uiState.value = SearchUiState.Loading
        return viewModelScope.launch {
            // D-278: serve the cached trending payload first (instant, offline).
            if (_query.value.isBlank()) {
                val cachedTrending = withContext(Dispatchers.IO) {
                    dataCacheRepository.getBrowseCache(BrowseCacheCodec.SECTION_TRENDING)
                }
                if (cachedTrending != null && _query.value.isBlank()) {
                    val cachedResults = try {
                        BrowseCacheCodec.decode(cachedTrending.dataJson)
                    } catch (parseErr: Exception) {
                        Logger.w(TAG) { "Trending cache parse failed: ${parseErr.message}" }
                        emptyList()
                    }
                    if (cachedResults.isNotEmpty() && _query.value.isBlank() && isCurrent(gen)) {
                        Logger.i(TAG) { "Serving ${cachedResults.size} cached trending as default" }
                        showingDefaults = true
                        // Task 61 (round 21): the cached feed pages too (the
                        // network refresh below re-establishes the context).
                        pagingMode = PagingMode.ANILIST_TRENDING
                        lastLoadedPage = 1
                        _uiState.value = SearchUiState.Success(
                            results = cachedResults,
                            hasMore = cachedResults.size >= 30,
                        )
                    }
                }
            }
            // Then refresh from network (populates/refreshes the cache for the
            // NEXT offline open + shows fresh trending when online).
            try {
                Logger.i(TAG) { "Loading trending anime from AniList" }
                val results = anilistApi.fetchTrending(perPage = 30)
                // D-258 staleness guard: the user typed a query while the
                // trending fetch was in flight — don't clobber the search.
                if (_query.value.isNotBlank()) return@launch
                // D-305: a newer request superseded this one — drop the result.
                if (!isCurrent(gen)) return@launch
                if (results.isEmpty()) {
                    // Network returned empty — only fall to Idle if we have
                    // NO cached fallback already showing (D-278).
                    if (!showingDefaults) _uiState.value = SearchUiState.Idle
                } else {
                    showingDefaults = true
                    // Task 61 (round 21): record the paging context (page 1;
                    // a full page means "probably more").
                    pagingMode = PagingMode.ANILIST_TRENDING
                    lastLoadedPage = 1
                    _uiState.value = SearchUiState.Success(
                        results = results,
                        hasMore = results.size >= 30,
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Logger.e(TAG, e) { "Trending load failed: ${e.message}" }
                if (_query.value.isNotBlank()) return@launch
                if (!isCurrent(gen)) return@launch
                // D-278: if cache already served (showingDefaults), keep it —
                // don't clobber with Idle. Only fall to Idle when we have
                // nothing cached to show.
                if (!showingDefaults) _uiState.value = SearchUiState.Idle
            }
        }.also { defaultsJob = it }
    }

    /**
     * Load the selected extension source's popular anime.
     * If no source is selected, shows ExtensionNotAvailable.
     */
    private fun loadExtensionPopular(): Job {
        val sourceId = _selectedSourceId.value
        if (sourceId == null) {
            // D-305 review fix: early returns must ALSO supersede in-flight
            // requests — otherwise a stale AniList/other-source search can still
            // land afterwards (and renders as Loading-forever in this mode).
            beginRequest()
            _uiState.value = SearchUiState.ExtensionNotAvailable
            return Job().apply { complete() }
        }

        val source = extensionManager.getSource(sourceId) as? AnimeCatalogueSource
        if (source == null) {
            // Source was uninstalled — clear the selection.
            _selectedSourceId.value = null
            preferenceStore.putLong(KEY_SELECTED_SOURCE_ID, -1L)
            beginRequest() // D-305 review fix: supersede in-flight requests.
            _uiState.value = SearchUiState.ExtensionNotAvailable
            return Job().apply { complete() }
        }

        val gen = beginRequest()
        _uiState.value = SearchUiState.Loading
        return viewModelScope.launch {
            try {
                Logger.i(TAG) { "Fetching popular anime from source: ${source.name}" }
                val page = withContext(Dispatchers.IO) { source.getPopularAnime(1) }
                // D-304: some extensions (e.g. moviebox) return the same entry
                // multiple times in one page (overlapping carousel/row sections).
                // Dedupe by URL — the results grid keys rows by "sourceId:url"
                // and LazyGrid CRASHES on duplicate keys (device-reported
                // IllegalArgumentException on a moviebox search).
                val results = page.animes.distinctBy { it.url }.map {
                    it.toExtensionAnime(sourceId, source.name)
                }
                Logger.i(TAG) { "Got ${results.size} results from ${source.name}" }
                // D-258 staleness guard: the user typed a query while the
                // popular fetch was in flight — don't clobber the search.
                if (_query.value.isNotBlank()) return@launch
                // D-305: a newer request superseded this one — drop the result.
                if (!isCurrent(gen)) return@launch
                // Task 61 (round 21): record the paging context — AnimesPage
                // carries hasNextPage natively.
                pagingMode = PagingMode.ANIYOMI_POPULAR
                lastLoadedPage = 1
                _uiState.value = if (results.isEmpty()) {
                    // D-209: distinguish extension-empty from AniList-empty so the
                    // UI can show the source name + a Refresh button (the empty result
                    // might be due to a stale Cloudflare cookie the user just solved).
                    SearchUiState.ExtensionEmpty(source.name, (source as? AnimeHttpSource)?.baseUrl)
                } else {
                    showingDefaults = true
                    SearchUiState.ExtensionSuccess(results = results, hasMore = page.hasNextPage)
                }
            } catch (e: Throwable) {
                // Catch Throwable (not Exception) — binary-incompat throws NoClassDefFoundError.
                // Cancellation must propagate (D-305 request superseding).
                if (e is kotlinx.coroutines.CancellationException) throw e
                // D-209: detect CloudflareException → show the "Open in WebView" button.
                if (_query.value.isNotBlank()) return@launch
                if (!isCurrent(gen)) return@launch
                if (e is CloudflareException) {
                    Logger.w(TAG) { "Cloudflare blocked ${source.name}: ${e.reason} (url=${e.url})" }
                    _uiState.value = SearchUiState.CloudflareBlocked(
                        url = e.url, sourceName = source.name,
                    )
                } else {
                    val errorMsg = "${e::class.java.simpleName}: ${e.message ?: "Unknown error"}"
                    Logger.e(TAG, e) { "Extension popular fetch failed for ${source.name}: $errorMsg" }
                    _uiState.value = SearchUiState.ExtensionError(
                        "${source.name}: $errorMsg"
                    )
                }
            }
        }.also { defaultsJob = it }
    }

    /**
     * Search the selected extension source by query.
     */
    private fun searchExtension(q: String) {
        val sourceId = _selectedSourceId.value
        if (sourceId == null) {
            beginRequest() // D-305 review fix: supersede in-flight requests.
            _uiState.value = SearchUiState.ExtensionNotAvailable
            return
        }

        val source = extensionManager.getSource(sourceId) as? AnimeCatalogueSource
        if (source == null) {
            _selectedSourceId.value = null
            preferenceStore.putLong(KEY_SELECTED_SOURCE_ID, -1L)
            beginRequest() // D-305 review fix: supersede in-flight requests.
            _uiState.value = SearchUiState.ExtensionNotAvailable
            return
        }

        showingDefaults = false
        val gen = beginRequest()
        _uiState.value = SearchUiState.Loading
        searchJob = viewModelScope.launch {
            try {
                Logger.i(TAG) { "Searching source ${source.name} for '$q'" }
                val page = withContext(Dispatchers.IO) {
                    source.getSearchAnime(1, q, AnimeFilterList())
                }
                // D-304: dedupe by URL — see loadExtensionPopular. Extensions can
                // emit the same URL twice in one results page; LazyGrid keys on
                // "sourceId:url" and crashes on duplicates.
                val results = page.animes.distinctBy { it.url }.map {
                    it.toExtensionAnime(sourceId, source.name)
                }
                Logger.i(TAG) { "Got ${results.size} results from ${source.name}" }
                // D-258 staleness guard: the query was cleared while this
                // search was in flight — don't clobber the restored defaults.
                if (_query.value.isBlank()) return@launch
                // D-305: a newer request superseded this one — drop the result.
                if (!isCurrent(gen)) return@launch
                // Task 61 (round 21): record the paging context — the grid's
                // approach-bottom trigger continues THIS search at page 2+.
                pagingMode = PagingMode.ANIYOMI_SEARCH
                lastLoadedPage = 1
                _uiState.value = if (results.isEmpty()) {
                    // D-209: distinguish extension-empty from AniList-empty.
                    SearchUiState.ExtensionEmpty(source.name, (source as? AnimeHttpSource)?.baseUrl)
                } else {
                    SearchUiState.ExtensionSuccess(results = results, hasMore = page.hasNextPage)
                }
            } catch (e: Throwable) {
                // Catch Throwable (not Exception) — binary-incompat throws NoClassDefFoundError.
                // Cancellation must propagate (D-305 request superseding).
                if (e is kotlinx.coroutines.CancellationException) throw e
                // D-209: detect CloudflareException → show the "Open in WebView" button.
                if (_query.value.isBlank()) return@launch
                if (!isCurrent(gen)) return@launch
                if (e is CloudflareException) {
                    Logger.w(TAG) { "Cloudflare blocked ${source.name}: ${e.reason} (url=${e.url})" }
                    _uiState.value = SearchUiState.CloudflareBlocked(
                        url = e.url, sourceName = source.name,
                    )
                } else {
                    val errorMsg = "${e::class.java.simpleName}: ${e.message ?: "Unknown error"}"
                    Logger.e(TAG, e) { "Extension search failed for ${source.name}: $errorMsg" }
                    _uiState.value = SearchUiState.ExtensionError(
                        "${source.name}: $errorMsg"
                    )
                }
            }
        }
    }

    // ── CloudStream loaders (session 3, execution phase 1) ──────────────────

    /**
     * Blank-query browse of the selected CloudStream provider (MainAPI.getMainPage).
     * Mirrors [loadExtensionPopular] exactly: generation + staleness guards,
     * Throwable catch (plugins can throw anything), never-silent errors.
     *
     * Task 44: the browse is SECTIONED — every shelf of the provider's mainPage
     * becomes its own titled row ("Latest Updated", "Most Popular", …), the
     * user's device round-3 request. Only a fully-empty browse renders the
     * ExtensionEmpty card.
     *
     * Task 48 (device round 7 — instant-open cache): the browse feed is served
     * stale-while-revalidate from the repository's memory+disk cache:
     * 1. a cached snapshot (ANY age) renders IMMEDIATELY — before the plugin
     *    manager finishes loading, before any network IO. The user's report:
     *    "keep the whole page cached so that it is instantaneous";
     * 2. a FRESH snapshot (< 10 min) skips the network entirely;
     * 3. a stale one refreshes in the background — on failure the cached feed
     *    STAYS visible (a network hiccup must never blank a page we can
     *    already show; errors only surface when there is no cache at all).
     */
    private fun loadCloudstreamPopular(): Job {
        val providerName = _selectedCsProvider.value
        if (providerName == null) {
            beginRequest() // D-305 review fix: supersede in-flight requests.
            _uiState.value = SearchUiState.ExtensionNotAvailable
            return Job().apply { complete() }
        }

        val gen = beginRequest()
        return viewModelScope.launch {
            // ── Step 1: cache-first instant render (Task 48) ──
            val cached = cloudstreamRepository.cachedBrowseSections(providerName)
            // Task 62 (round 22 — the STABLE randomized browse): the persisted
            // display arrangement. Restored EXACTLY when valid → a cold app
            // reopen shows the page the user last saw (the round-22 report:
            // "it opens up on that exact same search page — the results are
            // reloaded [randomized], this is not how things should be handled"
            // is fixed: NO re-shuffle on reopen). Absent/invalid → the smart
            // shuffle runs inside [arrangeBrowseSections] and is re-persisted.
            val cachedDisplay = cloudstreamRepository.cachedBrowseDisplay(providerName)
            if (cached != null && cached.isNotEmpty()) {
                if (!isCurrent(gen)) return@launch
                showingDefaults = true
                _uiState.value = SearchUiState.ExtensionBrowseSuccess(
                    sourceName = providerName,
                    // Task 62: restore-when-valid, else smart-shuffle (row order
                    // + item order under the cross-section first-4 uniqueness
                    // constraint) + persist. The ORIGINAL shelf index is
                    // captured in display order (the category subpages resolve
                    // their shelf by it).
                    sections = arrangeBrowseSections(
                        raw = cached,
                        display = cachedDisplay,
                        providerName = providerName,
                    ),
                )
                Logger.i(TAG) {
                    "Browse cache HIT for '$providerName' — rendered ${cached.size} section(s) instantly " +
                        "(display ${if (cachedDisplay != null) "restored" else "shuffled"})"
                }
            } else {
                if (!isCurrent(gen)) return@launch
                _uiState.value = SearchUiState.Loading
            }

            // ── Step 2: fresh snapshot → no network at all ──
            if (cached != null && cloudstreamRepository.browseIsFresh(providerName)) {
                Logger.d(TAG) { "Browse cache fresh for '$providerName' — skipping network refresh" }
                return@launch
            }

            // Task 47: cold-start-safe provider resolution — the raw source
            // list legitimately starts empty at app start (WhileSubscribed
            // chain + activity-gated manager load); only a LOADED list may
            // conclude "provider gone". Previously this synchronous lookup
            // reset the persisted kind on every cold entry.
            val source = awaitCsSource(providerName)
            if (source == null) {
                if (!isCurrent(gen)) return@launch
                selectAniyomiKind(persist = true)
                // Task 48: the provider is gone — drop its cached browse too.
                cloudstreamRepository.invalidateBrowseCache(providerName)
                _uiState.value = SearchUiState.ExtensionNotAvailable
                return@launch
            }
            if (!source.hasMainPage) {
                // This provider can't be browsed without a query — honest state,
                // NOT an error (the user just needs to type).
                if (!isCurrent(gen)) return@launch
                _uiState.value = SearchUiState.ExtensionNoBrowse(source.providerName)
                return@launch
            }

            try {
                Logger.i(TAG) { "Browsing CloudStream provider: $providerName" }
                val sections = cloudstreamRepository.browseSections(providerName)
                Logger.i(TAG) { "Got ${sections.size} browse section(s) from $providerName" }
                if (_query.value.isNotBlank()) return@launch
                if (!isCurrent(gen)) return@launch
                _uiState.value = if (sections.isEmpty()) {
                    // Task 48: an empty refresh NEVER blanks a cached feed we
                    // are already showing — only the no-cache path renders the
                    // empty card.
                    if (cached != null && cached.isNotEmpty()) {
                        Logger.w(TAG) {
                            "Browse refresh returned empty for '$providerName' — keeping cached feed"
                        }
                        return@launch
                    }
                    // Task 45: pass the provider's site URL — the empty card's
                    // "Open in WebView" button needs it (round-4 report: the card
                    // said "solve it in the WebView" but offered no button).
                    // Guard: providers that never set mainUrl report "NONE".
                    SearchUiState.ExtensionEmpty(source.providerName, source.mainUrl.takeHttpUrl())
                } else {
                    showingDefaults = true
                    SearchUiState.ExtensionBrowseSuccess(
                        sourceName = source.providerName,
                        // Task 62 (round 22): restore-when-valid, else smart
                        // shuffle + persist. A background refresh (stale cache)
                        // KEEPS the arrangement the user is looking at — the
                        // content swaps in place, the rows do not jump around;
                        // a pull-to-refresh invalidated the cache AND its
                        // display state first, so it lands here with null → a
                        // genuinely fresh random arrangement.
                        // The display is RE-READ here (not the step-1 local):
                        // when step 1 smart-shuffled a cache that had no
                        // persisted arrangement, saveDisplay already wrote the
                        // fresh one to the MEMORY layer — reusing the stale
                        // null local would shuffle a DIFFERENT arrangement
                        // into place a second later.
                        sections = arrangeBrowseSections(
                            raw = sections,
                            display = cloudstreamRepository.cachedBrowseDisplay(providerName),
                            providerName = providerName,
                        ),
                    )
                }
            } catch (e: Throwable) {
                // Catch Throwable (not Exception) — plugin bytecode can throw
                // NoClassDefFoundError etc. Cancellation must propagate (D-305).
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (_query.value.isNotBlank()) return@launch
                if (!isCurrent(gen)) return@launch
                // Task 48: a failed refresh NEVER blanks a cached feed we are
                // already showing — log it and keep the user watching content.
                if (cached != null && cached.isNotEmpty()) {
                    Logger.w(TAG, e) {
                        "Browse refresh failed for '$providerName' " +
                            "(${e::class.java.simpleName}: ${e.message}) — keeping cached feed"
                    }
                    return@launch
                }
                // Task 45: Cloudflare blocks get the dedicated card WITH the
                // "Open in WebView" action (the manual solve feeds cookies back
                // through the system CookieManager — CloudflareKiller merges them).
                if (e is com.lagradost.cloudstream3.network.CloudflareBlockedException) {
                    Logger.w(TAG) { "CloudStream browse blocked by Cloudflare: ${e.message}" }
                    _uiState.value = SearchUiState.CloudflareBlocked(
                        url = source.mainUrl.takeHttpUrl() ?: "https://${e.host}",
                        sourceName = source.providerName,
                    )
                } else {
                    val errorMsg = csErrorMessage(e)
                    Logger.e(TAG, e) { "CloudStream browse failed for $providerName: $errorMsg" }
                    _uiState.value = SearchUiState.ExtensionError("$providerName: $errorMsg")
                }
            }
        }.also { defaultsJob = it }
    }

    // ── Task 62 (round 22): the stable + SMART randomized browse ────────────

    /**
     * Task 62 (round 22): builds the browse sections in DISPLAY order from the
     * raw provider sections. When [display] is present AND still valid for the
     * current raw list, the arrangement is RESTORED exactly (row order +
     * per-row item order) — a cold app reopen shows the page the user last
     * saw, never a re-shuffle. Otherwise the sections are SMART-SHUFFLED
     * (randomized row order + randomized item order under the cross-section
     * first-4 uniqueness constraint) and the arrangement is persisted onto
     * the provider's snapshot for the next restore. The ORIGINAL shelf index
     * rides each row either way (the category subpages resolve their shelf by
     * it — captured before any shuffle).
     */
    private fun arrangeBrowseSections(
        raw: List<CsBrowseSection>,
        display: CsBrowseDisplay?,
        providerName: String,
    ): List<ExtensionBrowseSection> {
        val restored = restoreBrowseDisplay(raw, display)
        if (restored != null) {
            Logger.i(TAG) { "Browse display RESTORED for '$providerName' (${restored.size} rows)" }
            return restored
        }
        val shuffled = smartShuffleSections(
            // Task 64 (round 24 — F): the shelf index comes from the SECTION
            // itself — the repository stamps every section with its ORIGINAL
            // mainPage index (pre-compaction, pre-merge). The old
            // `mapIndexed` position was the MIXING root cause: a failed shelf
            // compacted the list and shifted every later row's subpage onto
            // the WRONG category.
            raw.map { section ->
                ExtensionBrowseSection(
                    title = section.title,
                    shelfIndex = section.shelfIndex,
                    results = section.items.map { it.toExtensionAnime() },
                )
            },
        )
        persistBrowseDisplay(providerName, shuffled)
        return shuffled
    }

    /**
     * Task 62: rebuilds the sections from a persisted display arrangement.
     * Returns null when [display] is absent or INVALID for the current raw
     * list (wrong row count, an out-of-range or repeated shelf index — the
     * provider changed its mainPage): the caller falls back to the smart
     * shuffle. Items are matched by URL (the stable cross-session identity):
     * persisted urls still present keep their display slots, vanished ones
     * drop, and genuinely new items append at the end of their row in the
     * provider's original order.
     */
    private fun restoreBrowseDisplay(
        raw: List<CsBrowseSection>,
        display: CsBrowseDisplay?,
    ): List<ExtensionBrowseSection>? {
        if (display == null) return null
        if (display.rows.size != raw.size) return null
        // Task 64 (round 24 — F): rows resolve by their ORIGINAL shelf index
        // (the repository stamps every section with it), NOT by list position
        // — the old positional lookup mis-restored whenever a failed shelf or
        // a same-title merge changed the raw list's shape. Validation: every
        // row's index must exist in the raw set exactly once (a provider that
        // changed its mainPage fails this → fresh shuffle).
        val rawByIndex = HashMap<Int, CsBrowseSection>(raw.size * 2)
        for (section in raw) {
            if (section.shelfIndex < 0) return null
            if (rawByIndex.put(section.shelfIndex, section) != null) return null
        }
        val seenIndexes = HashSet<Int>(raw.size * 2)
        for (row in display.rows) {
            if (row.shelfIndex !in rawByIndex) return null
            if (!seenIndexes.add(row.shelfIndex)) return null
        }
        return display.rows.map { row ->
            val section = rawByIndex.getValue(row.shelfIndex)
            val byUrl = HashMap<String, CsContentCard>(section.items.size * 2)
            for (card in section.items) {
                if (card.url !in byUrl) byUrl[card.url] = card
            }
            val orderedUrls = HashSet(row.itemUrls)
            val orderedItems = row.itemUrls.mapNotNull { byUrl[it] }
            val appended = section.items.filter { it.url !in orderedUrls }
            ExtensionBrowseSection(
                title = section.title,
                shelfIndex = row.shelfIndex,
                results = (orderedItems + appended).map { it.toExtensionAnime() },
            )
        }
    }

    /**
     * Task 62 (round 22 — the SMART shuffle, per the device round's spec):
     * randomizes BOTH the row order AND the item order within each row, under
     * the cross-section constraint: "the first four of any of the categories
     * will not be the same as any other one of them" — no item (by url) may
     * appear in the top-4 of two different sections.
     *
     * Sections are processed in the (already shuffled) display order; each
     * RANDOMIZED section claims [TOP_UNIQUE_ITEMS] urls for its top-4 that no
     * earlier section claimed. A section that cannot claim 4 unclaimed items
     * (fewer than 4 items total, or its pool overlaps too heavily with earlier
     * top-4s) is NOT randomized at all — the device spec's fallback: "if it
     * cannot apply the proper randomization as needed, then it will just
     * outright not apply the randomization" — but its ORIGINAL top-4 still
     * counts as claimed, so sections processed later keep avoiding it.
     */
    private fun smartShuffleSections(
        sections: List<ExtensionBrowseSection>,
    ): List<ExtensionBrowseSection> {
        if (sections.size < 2) return sections
        val rows = sections.shuffled()
        val claimedTopUrls = HashSet<String>()
        val result = ArrayList<ExtensionBrowseSection>(rows.size)
        var randomizedCount = 0
        for (section in rows) {
            val items = section.results
            val tooSmall = items.size < TOP_UNIQUE_ITEMS
            val enoughUnclaimed = !tooSmall &&
                items.count { it.url !in claimedTopUrls } >= TOP_UNIQUE_ITEMS
            if (!enoughUnclaimed) {
                // NOT randomized — the original order stands; the original
                // head still claims its urls for the later sections.
                items.take(TOP_UNIQUE_ITEMS).forEach { claimedTopUrls.add(it.url) }
                result += section
                continue
            }
            val top = items.filter { it.url !in claimedTopUrls }
                .shuffled()
                .take(TOP_UNIQUE_ITEMS)
            top.forEach { claimedTopUrls.add(it.url) }
            val claimedSet = top.mapTo(HashSet()) { it.url }
            val rest = items.filter { it.url !in claimedSet }.shuffled()
            result += section.copy(results = top + rest)
            randomizedCount++
        }
        Logger.i(TAG) {
            "smartShuffle — ${randomizedCount}/${rows.size} section(s) randomized " +
                "(${rows.size - randomizedCount} kept original order — not enough unique items)"
        }
        return result
    }

    /**
     * Task 62: persists the arrangement (row shelf indexes + per-row item
     * urls) onto the provider's browse snapshot so the NEXT cold reopen
     * restores it exactly instead of re-shuffling.
     */
    private fun persistBrowseDisplay(providerName: String, sections: List<ExtensionBrowseSection>) {
        if (sections.isEmpty()) return
        runCatching {
            cloudstreamRepository.saveBrowseDisplay(
                providerName,
                CsBrowseDisplay(
                    rows = sections.map { section ->
                        CsBrowseDisplayRow(
                            shelfIndex = section.shelfIndex,
                            itemUrls = section.results.map { it.url },
                        )
                    },
                ),
            )
        }.onFailure { t ->
            Logger.w(TAG, t) { "persistBrowseDisplay failed for '$providerName' — the next reopen shuffles fresh" }
        }
    }

    /** Live-query search of the selected CloudStream provider (MainAPI.search). */
    private fun searchCloudstream(q: String) {
        val providerName = _selectedCsProvider.value
        if (providerName == null) {
            beginRequest()
            _uiState.value = SearchUiState.ExtensionNotAvailable
            return
        }

        showingDefaults = false
        val gen = beginRequest()
        _uiState.value = SearchUiState.Loading
        searchJob = viewModelScope.launch {
            // Task 47: cold-start-safe provider resolution (see loadCloudstreamPopular).
            val source = awaitCsSource(providerName)
            if (source == null) {
                if (!isCurrent(gen)) return@launch
                selectAniyomiKind(persist = true)
                _uiState.value = SearchUiState.ExtensionNotAvailable
                return@launch
            }

            try {
                Logger.i(TAG) { "Searching CloudStream provider $providerName for '$q'" }
                val page = cloudstreamRepository.search(providerName, q, 1)
                val results = page.items.map { it.toExtensionAnime() }
                Logger.i(TAG) { "Got ${results.size} results from $providerName" }
                if (_query.value.isBlank()) return@launch
                if (!isCurrent(gen)) return@launch
                // Task 61 (round 21): record the paging context — the CS
                // search page carries hasNext from the repository.
                pagingMode = PagingMode.CS_SEARCH
                lastLoadedPage = 1
                _uiState.value = if (results.isEmpty()) {
                    // Task 45: pass the provider's site URL — the empty card's
                    // "Open in WebView" button needs it.
                    SearchUiState.ExtensionEmpty(source.providerName, source.mainUrl.takeHttpUrl())
                } else {
                    SearchUiState.ExtensionSuccess(results, hasMore = page.hasNext)
                }
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (_query.value.isBlank()) return@launch
                if (!isCurrent(gen)) return@launch
                // Task 45: Cloudflare blocks get the dedicated card WITH the
                // "Open in WebView" action (mirrors the aniyomi error path).
                if (e is com.lagradost.cloudstream3.network.CloudflareBlockedException) {
                    Logger.w(TAG) { "CloudStream search blocked by Cloudflare: ${e.message}" }
                    _uiState.value = SearchUiState.CloudflareBlocked(
                        url = source.mainUrl.takeHttpUrl() ?: "https://${e.host}",
                        sourceName = source.providerName,
                    )
                } else {
                    // Cloudflare blocks get the friendly, actionable message (Task 44).
                    val errorMsg = csErrorMessage(e)
                    Logger.e(TAG, e) { "CloudStream search failed for $providerName: $errorMsg" }
                    _uiState.value = SearchUiState.ExtensionError("$providerName: $errorMsg")
                }
            }
        }
    }

    /**
     * Task 44: the user-facing message for a failed CloudStream call —
     * Cloudflare blocks read as a plain-language sentence (not
     * "CloudflareBlockedException: …"), everything else keeps the
     * exception-class + message form that has served debugging so far.
     */
    private fun csErrorMessage(e: Throwable): String =
        (e as? com.lagradost.cloudstream3.network.CloudflareBlockedException)?.userMessage
            ?: "${e::class.java.simpleName}: ${e.message ?: "Unknown error"}"

    /**
     * A WebView-openable site URL, or null when the provider never set a real
     * one (MainAPI's default mainUrl is the literal "NONE").
     */
    private fun String.takeHttpUrl(): String? = takeIf { it.startsWith("http") }

    /**
     * CsContentCard → the shared results-grid model (sourceKey carries the CS
     * identity; Task 45: sourceId now carries the BRIDGE id so the tapped card
     * opens the standard details screen via AnimeDetailsKey.Extension).
     */
    private fun CsContentCard.toExtensionAnime() = ExtensionAnime(
        sourceId = com.confused.anikuta.data.cloudstream.content.CsSourceIds.idFor(providerName),
        sourceName = providerName,
        url = url,
        title = name,
        thumbnailUrl = posterUrl,
        sourceKey = "cloudstream:$providerName",
        // Task 47 (device round 6, "rating shows but year doesn't"): many
        // providers set `year` on SEARCH responses but omit it on load() —
        // the search-time year now rides along as the details-page fallback
        // seed (see AnimeDetailsKey.Extension.year).
        year = year,
    )

    // ── Task 61 (round 21): the search page's LOAD-MORE + refresh ───────────

    /**
     * Task 61 (round 21): the search page's LOAD-MORE — appends page+1 of
     * whatever produced the current content. The UI fires this as the user
     * APPROACHES the bottom (a pre-fetch, ~2 rows before the end) and shows a
     * "Loading more…" footer while it runs.
     *
     * Guards: content states only (Success / ExtensionSuccess), no re-entry
     * while one is in flight, no paging once `hasMore` is false. A failure is
     * SOFT — the footer spinner disappears and the next approach-bottom
     * trigger retries (a pagination hiccup must never blank the page).
     */
    fun loadMore() {
        val state = _uiState.value
        val canPage = when (state) {
            is SearchUiState.Success -> !state.loadingMore && state.hasMore
            is SearchUiState.ExtensionSuccess -> !state.loadingMore && state.hasMore
            else -> false
        }
        if (!canPage) return
        val mode = pagingMode ?: return
        val sourceId = _selectedSourceId.value
        val providerName = _selectedCsProvider.value
        val query = _query.value
        val nextPage = lastLoadedPage + 1

        // Flip the footer ON (a copy of the current state — results preserved).
        when (state) {
            is SearchUiState.Success -> _uiState.value = state.copy(loadingMore = true)
            is SearchUiState.ExtensionSuccess -> _uiState.value = state.copy(loadingMore = true)
            else -> return
        }

        loadMoreJob = viewModelScope.launch {
            try {
                when (mode) {
                    PagingMode.ANILIST_TRENDING -> {
                        val more = anilistApi.fetchTrending(page = nextPage, perPage = 30)
                        appendAniList(more, mode, nextPage, hasMore = more.size >= 30)
                    }

                    PagingMode.ANILIST_SEARCH -> {
                        val more = anilistApi.searchAnime(
                            query = query,
                            page = nextPage,
                            perPage = 30,
                            sort = _sort.value.apiValue,
                        )
                        // AniList's search API hides pageInfo — a full page
                        // means "probably more" (the standard heuristic).
                        appendAniList(more, mode, nextPage, hasMore = more.size >= 30)
                    }

                    PagingMode.ANIYOMI_POPULAR -> {
                        val source = sourceId?.let { extensionManager.getSource(it) }
                            as? AnimeCatalogueSource
                        if (source == null) {
                            clearLoadingMore()
                            return@launch
                        }
                        val page = withContext(Dispatchers.IO) {
                            source.getPopularAnime(nextPage)
                        }
                        appendExtension(
                            page.animes.distinctBy { it.url }
                                .map { it.toExtensionAnime(source.id, source.name) },
                            mode,
                            nextPage,
                            hasMore = page.hasNextPage,
                        )
                    }

                    PagingMode.ANIYOMI_SEARCH -> {
                        val source = sourceId?.let { extensionManager.getSource(it) }
                            as? AnimeCatalogueSource
                        if (source == null) {
                            clearLoadingMore()
                            return@launch
                        }
                        val page = withContext(Dispatchers.IO) {
                            source.getSearchAnime(nextPage, query, AnimeFilterList())
                        }
                        appendExtension(
                            page.animes.distinctBy { it.url }
                                .map { it.toExtensionAnime(source.id, source.name) },
                            mode,
                            nextPage,
                            hasMore = page.hasNextPage,
                        )
                    }

                    PagingMode.CS_SEARCH -> {
                        if (providerName == null) {
                            clearLoadingMore()
                            return@launch
                        }
                        val page = cloudstreamRepository.search(providerName, query, nextPage)
                        appendExtension(
                            page.items.map { it.toExtensionAnime() },
                            mode,
                            nextPage,
                            hasMore = page.hasNext,
                        )
                    }
                }
            } catch (e: Throwable) {
                // Soft-fail: Cancellation must propagate (a superseded request
                // or a cleared loadMoreJob); anything else just drops the footer.
                if (e is kotlinx.coroutines.CancellationException) throw e
                Logger.w(TAG, e) {
                    "loadMore failed (mode=$mode page=$nextPage) — footer dismissed; the next trigger retries"
                }
                clearLoadingMore()
            }
        }
    }

    /** Turns the footer off without touching the results (the soft-fail path). */
    private fun clearLoadingMore() {
        when (val state = _uiState.value) {
            is SearchUiState.Success -> _uiState.value = state.copy(loadingMore = false)
            is SearchUiState.ExtensionSuccess -> _uiState.value = state.copy(loadingMore = false)
            else -> Unit
        }
    }

    /**
     * Task 61: the AniList append — deduped by AniList id (a page overlap must
     * never duplicate a LazyGrid key). An empty page ENDS the paging.
     */
    private fun appendAniList(more: List<AniListAnime>, mode: PagingMode, page: Int, hasMore: Boolean) {
        val state = _uiState.value as? SearchUiState.Success ?: return
        if (more.isEmpty()) {
            _uiState.value = state.copy(loadingMore = false, hasMore = false)
            return
        }
        pagingMode = mode
        lastLoadedPage = page
        val existingIds = state.results.mapTo(mutableSetOf()) { it.id }
        val merged = state.results + more.filter { it.id !in existingIds }
        _uiState.value = state.copy(results = merged, loadingMore = false, hasMore = hasMore)
        Logger.i(TAG) { "loadMore (AniList, $mode page=$page) — +${merged.size - state.results.size} new" }
    }

    /**
     * Task 61: the extension append (aniyomi + CloudStream search grids) —
     * deduped by the grids' key identity ("sourceKey|url"), the same D-304
     * duplicate-key crash guard the first pages use. An empty page ENDS paging.
     */
    private fun appendExtension(
        more: List<ExtensionAnime>,
        mode: PagingMode,
        page: Int,
        hasMore: Boolean,
    ) {
        val state = _uiState.value as? SearchUiState.ExtensionSuccess ?: return
        if (more.isEmpty()) {
            _uiState.value = state.copy(loadingMore = false, hasMore = false)
            return
        }
        pagingMode = mode
        lastLoadedPage = page
        val keyOf: (ExtensionAnime) -> String = { "${it.sourceKey ?: it.sourceId}:${it.url}" }
        val existingKeys = state.results.mapTo(mutableSetOf()) { keyOf(it) }
        val merged = state.results + more.filter { keyOf(it) !in existingKeys }
        _uiState.value = state.copy(results = merged, loadingMore = false, hasMore = hasMore)
        Logger.i(TAG) { "loadMore (extension, $mode page=$page) — +${merged.size - state.results.size} new" }
    }

    /**
     * Task 61 (round 21): the search page's PULL-TO-REFRESH — reloads the
     * current mode's page 1. For the CS browse the cached feed is invalidated
     * FIRST (the user's spec: "the old cache will be deleted after the refresh
     * is successful"), then the fresh (randomized) sections land; for the
     * other modes this is just the page-1 reload.
     */
    fun refreshCurrent() {
        when (_source.value) {
            SearchSource.ANILIST -> {
                if (_query.value.isNotBlank()) {
                    search(_query.value)
                } else {
                    // No force flag needed — loadTrending() always runs the
                    // network refresh (the cached payload renders instantly,
                    // then the fresh page-1 lands).
                    loadTrending()
                }
            }

            SearchSource.EXTENSION -> {
                if (_selectedKind.value == SelectedSourceKind.CLOUDSTREAM) {
                    // The CS provider's cached browse must go FIRST — the
                    // user's "old cache deleted" spec; after the invalidate,
                    // loadCloudstreamPopular's cache-first path finds nothing
                    // and runs the FULL fresh (randomized) load.
                    _selectedCsProvider.value?.let { cloudstreamRepository.invalidateBrowseCache(it) }
                    if (_query.value.isNotBlank()) {
                        searchCloudstream(_query.value)
                    } else {
                        loadCloudstreamPopular()
                    }
                } else {
                    if (_query.value.isNotBlank()) {
                        searchExtension(_query.value)
                    } else {
                        loadExtensionPopular()
                    }
                }
            }
        }
    }

    // ── Recents persistence ──

    private fun loadRecents() {
        val raw = preferenceStore.getString(KEY_RECENT_SEARCHES, "")
        _recents.value = if (raw.isBlank()) emptyList()
                         else raw.split("\n").filter { it.isNotBlank() }
    }

    private fun addRecent(term: String) {
        val current = _recents.value.toMutableList()
        current.remove(term)
        current.add(0, term)
        while (current.size > MAX_RECENTS) current.removeAt(current.lastIndex)
        _recents.value = current
        persistRecents(current)
    }

    private fun persistRecents(list: List<String>) {
        preferenceStore.putString(KEY_RECENT_SEARCHES, list.joinToString("\n"))
    }
}

/**
 * Task 44: one titled browse row ("Latest Updated", …) for
 * [SearchUiState.ExtensionBrowseSuccess] — the ViewModel-side view of
 * CsBrowseSection, carrying the shared grid model so the rows reuse the
 * existing result cards.
 *
 * Task 61 (round 21): [shelfIndex] — the shelf's ORIGINAL index in the
 * provider's mainPage list (captured BEFORE the random shuffle — the category
 * subpage resolves its shelf by this index to paginate getMainPage).
 */
data class ExtensionBrowseSection(
    val title: String,
    val results: List<ExtensionAnime>,
    val shelfIndex: Int = 0,
)

// ── UI state ──

sealed interface SearchUiState {
    /** No query yet — show recents (if any) or the popular-anime prompt. */
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data object Empty : SearchUiState
    data class Success(
        val results: List<AniListAnime>,
        /** Task 61 (round 21): more pages available (the grid load-more footer). */
        val hasMore: Boolean = false,
        /** Task 61: a load-more is in flight (the "loading more…" footer spinner). */
        val loadingMore: Boolean = false,
    ) : SearchUiState
    /** AniList failed — friendly "tsundere" message per spec. */
    data object Error : SearchUiState
    /** Extension source selected but no source chosen, or source uninstalled. */
    data object ExtensionNotAvailable : SearchUiState
    /**
     * Extension source browse/search success. Task 61 (round 21): carries the
     * paging flags for the grid's approach-bottom load-more (search results +
     * the blank-query popular feed — both flat grids).
     */
    data class ExtensionSuccess(
        val results: List<ExtensionAnime>,
        /** Task 61 (round 21): more pages available (the grid load-more footer). */
        val hasMore: Boolean = false,
        /** Task 61: a load-more is in flight (the "loading more…" footer spinner). */
        val loadingMore: Boolean = false,
    ) : SearchUiState

    /**
     * Task 44: SECTIONED CloudStream browse success — each provider shelf
     * ("Latest Updated", "Most Popular", …) renders as its own titled
     * horizontal row of cards (the device round-3 "sections in row format"
     * request). Search results stay a flat [ExtensionSuccess] grid.
     */
    data class ExtensionBrowseSuccess(
        val sourceName: String,
        val sections: List<ExtensionBrowseSection>,
    ) : SearchUiState
    /** Extension source error — shows the actual error message (source name + reason). */
    data class ExtensionError(val message: String) : SearchUiState
    /**
     * D-209: Cloudflare blocked the request + the headless solver failed.
     * The UI shows an "Open in WebView" button (so the user can solve it
     * manually) + a "Refresh" button (to retry after solving).
     *
     * @param url the URL that was blocked (the source's baseUrl or request URL).
     * @param sourceName the extension source's display name.
     */
    data class CloudflareBlocked(val url: String, val sourceName: String) : SearchUiState
    /**
     * D-209: The extension returned 0 results (distinct from AniList's [Empty]
     * — [ExtensionEmpty] lets the UI show the source name + a "Refresh" button +
     * an "Open in WebView" button, since the empty result might be due to a
     * stale Cloudflare cookie that the user needs to solve in the WebView).
     *
     * D-210: added [sourceUrl] so the UI can launch the WebView directly.
     * Null if the source doesn't expose a baseUrl (rare — most do).
     */
    data class ExtensionEmpty(val sourceName: String, val sourceUrl: String? = null) : SearchUiState

    /**
     * Session 3 (CloudStream): the selected provider has no main page — it can
     * only be SEARCHED, not browsed with a blank query. Not an error: the UI
     * prompts the user to type a query instead of showing a retry button.
     */
    data class ExtensionNoBrowse(val sourceName: String) : SearchUiState
}

/**
 * Session 3: which ecosystem the EXTENSION mode currently browses. The selected
 * source's identity is (kind, Long-id | provider-name) — see SearchViewModel's
 * selection persistence.
 */
enum class SelectedSourceKind {
    ANIYOMI,
    CLOUDSTREAM,
}

enum class SearchSource(val displayName: String) {
    ANILIST("AniList"),
    EXTENSION("Extension"),
}

enum class SearchSort(val label: String, val apiValue: String) {
    POPULARITY("Popularity", "POPULARITY_DESC"),
    SCORE("Score", "SCORE_DESC"),
    NEWEST("Newest", "START_DATE_DESC"),
    TITLE_AZ("Title A-Z", "TITLE_ROMAJI"),
    TRENDING("Trending", "TRENDING_DESC"),
    FAVOURITES("Favourites", "FAVOURITES_DESC"),
}
