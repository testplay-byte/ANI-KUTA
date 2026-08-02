package com.confused.anikuta.feature.animesearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.anilist.api.AniListApi
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.preferences.PreferenceStore
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * ViewModel for the Search screen.
 *
 * Mirrors the old project's SearchViewModel but only the AniList source is wired
 * for now — Extension search requires the extension system (Phase 5).
 *
 * Behavior:
 *  - The query is debounced (350ms) before querying AniList.
 *  - Recent searches are stored in PreferenceStore (comma-separated) and shown
 *    when the query is blank.
 *  - On error, the user-friendly message "AniList is being a tsundere" is shown
 *    instead of the raw exception text (per task spec).
 *
 * CORE_RULES §20: Logged with tag "Anikuta:Feature:Search".
 * CORE_RULES §23: Reactive state (StateFlow).
 */
class SearchViewModel(
    private val anilistApi: AniListApi,
    private val preferenceStore: PreferenceStore,
) : ViewModel() {

    companion object {
        private const val TAG = "Anikuta:Feature:Search"
        private const val KEY_RECENT_SEARCHES = "search_recent_anilist"
        private const val KEY_RECENTS_COLLAPSED = "search_recents_collapsed"
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

    // ── Filters (UI-only for now — applied filter state lives in the VM so
    //    the FilterSheet edits a pending copy + Apply syncs. Phase 5 will wire
    //    these to AniList's filter GraphQL args.) ──
    private val _pendingFilters = MutableStateFlow(SearchFilters.Empty)
    val pendingFilters: StateFlow<SearchFilters> = _pendingFilters.asStateFlow()

    private val _appliedFilters = MutableStateFlow(SearchFilters.Empty)
    val appliedFilters: StateFlow<SearchFilters> = _appliedFilters.asStateFlow()

    /** Recent-searches card collapsed state (persisted across sessions). */
    private val _recentsCollapsed = MutableStateFlow(
        preferenceStore.getBoolean(KEY_RECENTS_COLLAPSED, false),
    )
    val recentsCollapsed: StateFlow<Boolean> = _recentsCollapsed.asStateFlow()

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
        // ponytail: not wired to AniList query yet — Phase 5 will re-search here.
    }

    fun toggleRecentsCollapsed() {
        val newValue = !_recentsCollapsed.value
        _recentsCollapsed.value = newValue
        preferenceStore.putBoolean(KEY_RECENTS_COLLAPSED, newValue)
    }

    fun onQueryChange(value: String) {
        _query.value = value
        // If the query is cleared, drop back to Idle (show recents).
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
        // Re-trigger a search if there's an active query.
        if (_query.value.isNotBlank()) {
            search(_query.value)
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

    @OptIn(FlowPreview::class)
    private fun observeQuery() {
        _query
            .debounce(DEBOUNCE_MS)
            .distinctUntilChanged()
            .onEach { q ->
                if (q.isBlank()) {
                    _uiState.value = SearchUiState.Idle
                } else {
                    search(q)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun search(q: String) {
        // Extension source: not implemented yet (Phase 5). Show a friendly
        // "not available" state instead of crashing.
        if (_source.value == SearchSource.EXTENSION) {
            _uiState.value = SearchUiState.ExtensionNotAvailable
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
                // User-friendly error — never expose raw exception text to the user.
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
    /** Extension source selected — not yet implemented (Phase 5). */
    data object ExtensionNotAvailable : SearchUiState
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
