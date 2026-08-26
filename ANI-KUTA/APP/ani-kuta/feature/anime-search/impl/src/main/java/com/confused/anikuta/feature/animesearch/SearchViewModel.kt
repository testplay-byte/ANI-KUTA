package com.confused.anikuta.feature.animesearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.anilist.api.AniListApi
import com.confused.anikuta.core.anilist.api.BrowseCacheCodec
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.datacache.DataCacheRepository
import com.confused.anikuta.core.preferences.PreferenceStore
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
    private val activityTracker: com.confused.anikuta.core.activitytracker.ActivityTracker,
) : ViewModel() {

    companion object {
        private const val TAG = "Anikuta:Feature:Search"
        private const val KEY_RECENT_SEARCHES = "search_recent_anilist"
        private const val KEY_SELECTED_SOURCE_ID = "search_selected_extension_source_id"
        private const val DEBOUNCE_MS = 350L
        private const val MAX_RECENTS = 10
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

    /** The trusted extension sources available for browsing. */
    val trustedSources: StateFlow<List<AnimeCatalogueSource>> =
        extensionManager.sources.map { sourceMap ->
            sourceMap.values.filterIsInstance<AnimeCatalogueSource>()
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
                    if (current == null || !currentExists) {
                        val top = sources.first()
                        _selectedSourceId.value = top.id
                        preferenceStore.putLong(KEY_SELECTED_SOURCE_ID, top.id)
                        // If the user is already in Extension mode, load the new source's popular.
                        if (_source.value == SearchSource.EXTENSION) {
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
            // Load the selected source's popular anime. If no source is selected
            // yet (auto-select hasn't run), the state stays ExtensionNotAvailable
            // until the init block's collector picks a source.
            loadExtensionPopular()
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
        if (_selectedSourceId.value == null) {
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
     * Triggers a load of that source's popular anime.
     */
    fun onSelectExtensionSource(sourceId: Long) {
        _selectedSourceId.value = sourceId
        preferenceStore.putLong(KEY_SELECTED_SOURCE_ID, sourceId)
        loadExtensionPopular()
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
            searchExtension(q)
            return
        }

        // D-192: track the search event
        activityTracker.track(
            eventType = com.confused.anikuta.core.activitytracker.ActivityEventType.SEARCH,
            route = "search",
            payload = q,
        )

        showingDefaults = false
        _uiState.value = SearchUiState.Loading
        viewModelScope.launch {
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
                _uiState.value = if (results.isEmpty()) {
                    SearchUiState.Empty
                } else {
                    SearchUiState.Success(results = results)
                }
            } catch (e: Exception) {
                Logger.e(TAG, e) { "Search failed for '$q': ${e.message}" }
                if (_query.value.isBlank()) return@launch
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
                    if (cachedResults.isNotEmpty() && _query.value.isBlank()) {
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
                if (results.isEmpty()) {
                    // Network returned empty — only fall to Idle if we have
                    // NO cached fallback already showing (D-278).
                    if (!showingDefaults) _uiState.value = SearchUiState.Idle
                } else {
                    showingDefaults = true
                    _uiState.value = SearchUiState.Success(results = results)
                }
            } catch (e: Exception) {
                Logger.e(TAG, e) { "Trending load failed: ${e.message}" }
                if (_query.value.isNotBlank()) return@launch
                // D-278: if cache already served (showingDefaults), keep it —
                // don't clobber with Idle. Only fall to Idle when we have
                // nothing cached to show.
                if (!showingDefaults) _uiState.value = SearchUiState.Idle
            }
        }
    }

    /**
     * Load the selected extension source's popular anime.
     * If no source is selected, shows ExtensionNotAvailable.
     */
    private fun loadExtensionPopular(): Job {
        val sourceId = _selectedSourceId.value
        if (sourceId == null) {
            _uiState.value = SearchUiState.ExtensionNotAvailable
            return Job().apply { complete() }
        }

        val source = extensionManager.getSource(sourceId) as? AnimeCatalogueSource
        if (source == null) {
            // Source was uninstalled — clear the selection.
            _selectedSourceId.value = null
            preferenceStore.putLong(KEY_SELECTED_SOURCE_ID, -1L)
            _uiState.value = SearchUiState.ExtensionNotAvailable
            return Job().apply { complete() }
        }

        _uiState.value = SearchUiState.Loading
        return viewModelScope.launch {
            try {
                Logger.i(TAG) { "Fetching popular anime from source: ${source.name}" }
                val page = withContext(Dispatchers.IO) { source.getPopularAnime(1) }
                val results = page.animes.map {
                    it.toExtensionAnime(sourceId, source.name)
                }
                Logger.i(TAG) { "Got ${results.size} results from ${source.name}" }
                // D-258 staleness guard: the user typed a query while the
                // popular fetch was in flight — don't clobber the search.
                if (_query.value.isNotBlank()) return@launch
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
                // D-209: detect CloudflareException → show the "Open in WebView" button.
                if (_query.value.isNotBlank()) return@launch
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
        }
    }

    /**
     * Search the selected extension source by query.
     */
    private fun searchExtension(q: String) {
        val sourceId = _selectedSourceId.value
        if (sourceId == null) {
            _uiState.value = SearchUiState.ExtensionNotAvailable
            return
        }

        val source = extensionManager.getSource(sourceId) as? AnimeCatalogueSource
        if (source == null) {
            _selectedSourceId.value = null
            preferenceStore.putLong(KEY_SELECTED_SOURCE_ID, -1L)
            _uiState.value = SearchUiState.ExtensionNotAvailable
            return
        }

        showingDefaults = false
        _uiState.value = SearchUiState.Loading
        viewModelScope.launch {
            try {
                Logger.i(TAG) { "Searching source ${source.name} for '$q'" }
                val page = withContext(Dispatchers.IO) {
                    source.getSearchAnime(1, q, AnimeFilterList())
                }
                val results = page.animes.map {
                    it.toExtensionAnime(sourceId, source.name)
                }
                Logger.i(TAG) { "Got ${results.size} results from ${source.name}" }
                // D-258 staleness guard: the query was cleared while this
                // search was in flight — don't clobber the restored defaults.
                if (_query.value.isBlank()) return@launch
                _uiState.value = if (results.isEmpty()) {
                    // D-209: distinguish extension-empty from AniList-empty.
                    SearchUiState.ExtensionEmpty(source.name, (source as? AnimeHttpSource)?.baseUrl)
                } else {
                    SearchUiState.ExtensionSuccess(results = results)
                }
            } catch (e: Throwable) {
                // D-209: detect CloudflareException → show the "Open in WebView" button.
                if (_query.value.isBlank()) return@launch
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
