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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        /** Persisted kind flag values (KEY_SELECTED_SOURCE_KIND). */
        private const val KIND_ANIYOMI = "aniyomi"
        private const val KIND_CLOUDSTREAM = "cloudstream"
    }

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _source = MutableStateFlow(SearchSource.ANILIST)
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

    /** D-305: starts a new request — cancels superseded loads, returns its generation. */
    private fun beginRequest(): Int {
        searchJob?.cancel()
        defaultsJob?.cancel()
        searchJob = null
        defaultsJob = null
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
                .filterNot { com.confused.anikuta.data.cloudstream.content.CsSourceIds.isCloudstreamId(it.id) }
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
        viewModelScope.launch {
            csSources.collect { sources ->
                if (_selectedKind.value == SelectedSourceKind.CLOUDSTREAM) {
                    val current = _selectedCsProvider.value
                    if (current == null || sources.none { it.providerName == current }) {
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
        }
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
                _uiState.value = if (results.isEmpty()) {
                    SearchUiState.Empty
                } else {
                    SearchUiState.Success(results = results)
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
                        _uiState.value = SearchUiState.Success(results = cachedResults)
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
                    _uiState.value = SearchUiState.Success(results = results)
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
                _uiState.value = if (results.isEmpty()) {
                    // D-209: distinguish extension-empty from AniList-empty so the
                    // UI can show the source name + a Refresh button (the empty result
                    // might be due to a stale Cloudflare cookie the user just solved).
                    SearchUiState.ExtensionEmpty(source.name, (source as? AnimeHttpSource)?.baseUrl)
                } else {
                    showingDefaults = true
                    SearchUiState.ExtensionSuccess(results = results)
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
                _uiState.value = if (results.isEmpty()) {
                    // D-209: distinguish extension-empty from AniList-empty.
                    SearchUiState.ExtensionEmpty(source.name, (source as? AnimeHttpSource)?.baseUrl)
                } else {
                    SearchUiState.ExtensionSuccess(results = results)
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
     */
    private fun loadCloudstreamPopular(): Job {
        val providerName = _selectedCsProvider.value
        if (providerName == null) {
            beginRequest() // D-305 review fix: supersede in-flight requests.
            _uiState.value = SearchUiState.ExtensionNotAvailable
            return Job().apply { complete() }
        }
        val source = csSources.value.firstOrNull { it.providerName == providerName }
        if (source == null) {
            // Provider gone (untrusted/uninstalled) — heal to aniyomi + NotAvailable.
            selectAniyomiKind(persist = true)
            beginRequest()
            _uiState.value = SearchUiState.ExtensionNotAvailable
            return Job().apply { complete() }
        }
        if (!source.hasMainPage) {
            // This provider can't be browsed without a query — honest state,
            // NOT an error (the user just needs to type).
            beginRequest()
            _uiState.value = SearchUiState.ExtensionNoBrowse(source.providerName)
            return Job().apply { complete() }
        }

        val gen = beginRequest()
        _uiState.value = SearchUiState.Loading
        return viewModelScope.launch {
            try {
                Logger.i(TAG) { "Browsing CloudStream provider: $providerName" }
                val sections = cloudstreamRepository.browseSections(providerName)
                Logger.i(TAG) { "Got ${sections.size} browse section(s) from $providerName" }
                if (_query.value.isNotBlank()) return@launch
                if (!isCurrent(gen)) return@launch
                _uiState.value = if (sections.isEmpty()) {
                    // Task 45: pass the provider's site URL — the empty card's
                    // "Open in WebView" button needs it (round-4 report: the card
                    // said "solve it in the WebView" but offered no button).
                    SearchUiState.ExtensionEmpty(source.providerName, source.mainUrl.takeIf { it.isNotBlank() })
                } else {
                    showingDefaults = true
                    SearchUiState.ExtensionBrowseSuccess(
                        sourceName = source.providerName,
                        sections = sections.map { section ->
                            ExtensionBrowseSection(
                                title = section.title,
                                results = section.items.map { it.toExtensionAnime() },
                            )
                        },
                    )
                }
            } catch (e: Throwable) {
                // Catch Throwable (not Exception) — plugin bytecode can throw
                // NoClassDefFoundError etc. Cancellation must propagate (D-305).
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (_query.value.isNotBlank()) return@launch
                if (!isCurrent(gen)) return@launch
                // Task 45: Cloudflare blocks get the dedicated card WITH the
                // "Open in WebView" action (the manual solve feeds cookies back
                // through the system CookieManager — CloudflareKiller merges them).
                if (e is com.lagradost.cloudstream3.network.CloudflareBlockedException) {
                    Logger.w(TAG) { "CloudStream browse blocked by Cloudflare: ${e.message}" }
                    _uiState.value = SearchUiState.CloudflareBlocked(
                        url = source.mainUrl.takeIf { it.isNotBlank() } ?: "https://${e.host}",
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

    /** Live-query search of the selected CloudStream provider (MainAPI.search). */
    private fun searchCloudstream(q: String) {
        val providerName = _selectedCsProvider.value
        if (providerName == null) {
            beginRequest()
            _uiState.value = SearchUiState.ExtensionNotAvailable
            return
        }
        val source = csSources.value.firstOrNull { it.providerName == providerName }
        if (source == null) {
            selectAniyomiKind(persist = true)
            beginRequest()
            _uiState.value = SearchUiState.ExtensionNotAvailable
            return
        }

        showingDefaults = false
        val gen = beginRequest()
        _uiState.value = SearchUiState.Loading
        searchJob = viewModelScope.launch {
            try {
                Logger.i(TAG) { "Searching CloudStream provider $providerName for '$q'" }
                val page = cloudstreamRepository.search(providerName, q, 1)
                val results = page.items.map { it.toExtensionAnime() }
                Logger.i(TAG) { "Got ${results.size} results from $providerName" }
                if (_query.value.isBlank()) return@launch
                if (!isCurrent(gen)) return@launch
                _uiState.value = if (results.isEmpty()) {
                    // Task 45: pass the provider's site URL — the empty card's
                    // "Open in WebView" button needs it.
                    SearchUiState.ExtensionEmpty(source.providerName, source.mainUrl.takeIf { it.isNotBlank() })
                } else {
                    SearchUiState.ExtensionSuccess(results)
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
                        url = source.mainUrl.takeIf { it.isNotBlank() } ?: "https://${e.host}",
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
    )

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
 */
data class ExtensionBrowseSection(
    val title: String,
    val results: List<ExtensionAnime>,
)

// ── UI state ──

sealed interface SearchUiState {
    /** No query yet — show recents (if any) or the popular-anime prompt. */
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data object Empty : SearchUiState
    data class Success(val results: List<AniListAnime>) : SearchUiState
    /** AniList failed — friendly "tsundere" message per spec. */
    data object Error : SearchUiState
    /** Extension source selected but no source chosen, or source uninstalled. */
    data object ExtensionNotAvailable : SearchUiState
    /** Extension source browse/search success. */
    data class ExtensionSuccess(val results: List<ExtensionAnime>) : SearchUiState

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
