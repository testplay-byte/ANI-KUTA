package com.confused.anikuta.feature.animesearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.anilist.api.AniListApi
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.preferences.PreferenceStore
import com.confused.anikuta.data.extension.manager.ExtensionManager
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
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
    private val preferenceStore: PreferenceStore,
    private val extensionManager: ExtensionManager,
) : ViewModel() {

    companion object {
        private const val TAG = "Anikuta:Feature:Search"
        private const val KEY_RECENT_SEARCHES = "search_recent_anilist"
        private const val KEY_RECENTS_COLLAPSED = "search_recents_collapsed"
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

    private val _recentsCollapsed = MutableStateFlow(
        preferenceStore.getBoolean(KEY_RECENTS_COLLAPSED, false),
    )
    val recentsCollapsed: StateFlow<Boolean> = _recentsCollapsed.asStateFlow()

    /** The trusted extension sources available for browsing. */
    val trustedSources: StateFlow<List<AnimeCatalogueSource>> =
        extensionManager.sources.map { sourceMap ->
            sourceMap.values.filterIsInstance<AnimeCatalogueSource>()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The currently selected extension source ID (persisted). Null = none selected. */
    private val _selectedSourceId = MutableStateFlow<Long?>(
        preferenceStore.getLong(KEY_SELECTED_SOURCE_ID, -1L).takeIf { it > 0 }
    )
    val selectedSourceId: StateFlow<Long?> = _selectedSourceId.asStateFlow()

    init {
        loadRecents()
        observeQuery()
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

    fun toggleRecentsCollapsed() {
        val newValue = !_recentsCollapsed.value
        _recentsCollapsed.value = newValue
        preferenceStore.putBoolean(KEY_RECENTS_COLLAPSED, newValue)
    }

    fun onQueryChange(value: String) {
        _query.value = value
        if (value.isBlank()) {
            _uiState.value = SearchUiState.Idle
        }
    }

    fun onClearQuery() {
        _query.value = ""
        _uiState.value = SearchUiState.Idle
    }

    fun onSourceChange(source: SearchSource) {
        _source.value = source
        if (source == SearchSource.EXTENSION) {
            // Load the selected source's popular anime (or show empty if none selected).
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
                    if (_source.value == SearchSource.ANILIST) {
                        loadTrending()
                    } else {
                        loadExtensionPopular()
                    }
                } else {
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
                _uiState.value = if (results.isEmpty()) {
                    SearchUiState.Empty
                } else {
                    SearchUiState.Success(results = results)
                }
            } catch (e: Exception) {
                Logger.e(TAG, e) { "Search failed for '$q': ${e.message}" }
                _uiState.value = SearchUiState.Error
            }
        }
    }

    /**
     * Load trending anime from AniList (shown when AniList source is active + query is blank).
     */
    private fun loadTrending() {
        _uiState.value = SearchUiState.Loading
        viewModelScope.launch {
            try {
                Logger.i(TAG) { "Loading trending anime from AniList" }
                val results = anilistApi.fetchTrending(perPage = 30)
                _uiState.value = if (results.isEmpty()) {
                    SearchUiState.Idle
                } else {
                    SearchUiState.Success(results = results)
                }
            } catch (e: Exception) {
                Logger.e(TAG, e) { "Trending load failed: ${e.message}" }
                _uiState.value = SearchUiState.Idle
            }
        }
    }

    /**
     * Load the selected extension source's popular anime.
     * If no source is selected, shows ExtensionNotAvailable.
     */
    private fun loadExtensionPopular() {
        val sourceId = _selectedSourceId.value
        if (sourceId == null) {
            _uiState.value = SearchUiState.ExtensionNotAvailable
            return
        }

        val source = extensionManager.getSource(sourceId) as? AnimeCatalogueSource
        if (source == null) {
            // Source was uninstalled — clear the selection.
            _selectedSourceId.value = null
            preferenceStore.putLong(KEY_SELECTED_SOURCE_ID, -1L)
            _uiState.value = SearchUiState.ExtensionNotAvailable
            return
        }

        _uiState.value = SearchUiState.Loading
        viewModelScope.launch {
            try {
                Logger.i(TAG) { "Fetching popular anime from source: ${source.name}" }
                val page = withContext(Dispatchers.IO) { source.getPopularAnime(1) }
                val results = page.animes.map {
                    ExtensionAnime.fromSAnime(sourceId, source.name, it)
                }
                Logger.i(TAG) { "Got ${results.size} results from ${source.name}" }
                _uiState.value = if (results.isEmpty()) {
                    SearchUiState.Empty
                } else {
                    SearchUiState.ExtensionSuccess(results = results)
                }
            } catch (e: Exception) {
                Logger.e(TAG, e) { "Extension popular fetch failed: ${e.message}" }
                _uiState.value = SearchUiState.Error
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

        _uiState.value = SearchUiState.Loading
        viewModelScope.launch {
            try {
                Logger.i(TAG) { "Searching source ${source.name} for '$q'" }
                val page = withContext(Dispatchers.IO) {
                    source.getSearchAnime(1, q, AnimeFilterList())
                }
                val results = page.animes.map {
                    ExtensionAnime.fromSAnime(sourceId, source.name, it)
                }
                Logger.i(TAG) { "Got ${results.size} results from ${source.name}" }
                _uiState.value = if (results.isEmpty()) {
                    SearchUiState.Empty
                } else {
                    SearchUiState.ExtensionSuccess(results = results)
                }
            } catch (e: Exception) {
                Logger.e(TAG, e) { "Extension search failed: ${e.message}" }
                _uiState.value = SearchUiState.Error
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
