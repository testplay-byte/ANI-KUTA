package com.confused.anikuta.feature.animelibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.anilist.api.AniListApi
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.content.LibraryCategory
import com.confused.anikuta.core.preferences.PreferenceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the Library screen (Phase C, D-140, D-141).
 *
 * Uses [LibraryEntry] (with mainId as the key) instead of AniListAnime.
 *
 * D-141 improvements:
 * - In-memory cache for AniList data (prevents re-fetching on every tab switch).
 * - Multi-select mode state.
 * - Category count for delete dialog (to decide if "Move to Default" shows).
 *
 * CORE_RULES §20: Logged with tag "Anikuta:Feature:Library".
 * CORE_RULES §23: Reactive state (StateFlow).
 */
class LibraryViewModel(
    private val anilistApi: AniListApi,
    private val contentRepository: ContentRepository,
    private val preferenceStore: PreferenceStore,
    private val dataCacheRepository: com.confused.anikuta.core.datacache.DataCacheRepository,
    // D-242-fix10: injected for unwatched episode count badges
    private val watchProgressStore: com.confused.anikuta.core.watchprogress.WatchProgressStore? = null,
    // Task 62 (round 22 — the library performance round): the bulk DB
    // mutations (H1) + the debounced filter/sort pipeline (H2) + the
    // preference preload (M4) run OFF the Main thread through this. Registered
    // in Koin by anilistModule (DefaultDispatcherProvider) — the auto-resolved
    // constructor param keeps the DI wiring untouched.
    private val dispatchers: com.confused.anikuta.core.common.DispatcherProvider,
) : ViewModel() {

    companion object {
        private const val TAG = "Anikuta:Feature:Library"

        // Customize-sheet preferences.
        private const val KEY_DISPLAY_MODE = "library_display_mode"
        private const val KEY_COLUMNS = "library_columns"
        private const val KEY_TITLE_LINES = "library_title_lines"
        private const val KEY_EPISODE_BADGE_MODE = "library_episode_badge_mode"
        private const val KEY_EPISODE_BADGE_POS = "library_episode_badge_pos"
        private const val KEY_SHOW_SCORE_BADGE = "library_show_score_badge"
        private const val KEY_SCORE_BADGE_POS = "library_score_badge_pos"
        private const val KEY_SHOW_CONTINUE_WATCHING = "library_show_continue_watching"
        private const val KEY_SHOW_TOTAL_ENTRIES = "library_show_total_entries"
        private const val KEY_SHOW_CATEGORY_COUNTS = "library_show_category_counts"
        private const val KEY_SORT_TYPE = "library_sort_type"
        private const val KEY_SORT_ASCENDING = "library_sort_ascending"
        private const val KEY_SELECTED_CATEGORY = "library_selected_category_id"
        // D-242-fix14: advanced RELEASED badge sub-options.
        private const val KEY_RELEASED_AUDIO_FILTER = "library_released_audio_filter"
        private const val KEY_RELEASED_UNWATCHED_ONLY = "library_released_unwatched_only"
        // D-242-fix17: cover border settings.
        private const val KEY_COVER_BORDER_ENABLED = "library_cover_border_enabled"
        private const val KEY_COVER_BORDER_COLOR = "library_cover_border_color"
        private const val KEY_COVER_BORDER_WIDTH = "library_cover_border_width"
        // D-242-fix18: All Caught Up tag + list mode settings.
        private const val KEY_SHOW_ALL_CAUGHT_UP_TAG = "library_show_all_caught_up_tag"
        private const val KEY_LIST_DENSITY = "library_list_density"
        private const val KEY_LIST_TITLE_POSITION = "library_list_title_position"
        // D-242-fix21: Comfortable border mode.
        private const val KEY_COMFORTABLE_BORDER_MODE = "library_comfortable_border_mode"
        // D-251: Hide titles in Comfortable mode (cover-only look, rounded corners kept).
        private const val KEY_COMFORTABLE_HIDE_TITLES = "library_comfortable_hide_titles"
    }

    private val _state = MutableStateFlow<LibraryState>(LibraryState.Loading)
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    /** All library categories. */
    private val _categories = MutableStateFlow<List<LibraryCategory>>(emptyList())
    val categories: StateFlow<List<LibraryCategory>> = _categories.asStateFlow()

    /** Item counts per category (for showing counts on tabs + delete dialog). */
    private val _categoryCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val categoryCounts: StateFlow<Map<Long, Int>> = _categoryCounts.asStateFlow()

    /** The currently selected category (null = all). */
    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    val selectedCategoryId: StateFlow<Long?> = _selectedCategoryId.asStateFlow()

    /** Category management state — for rename/delete dialogs. */
    private val _categoryToManage = MutableStateFlow<LibraryCategory?>(null)
    val categoryToManage: StateFlow<LibraryCategory?> = _categoryToManage.asStateFlow()

    /** Total entries in the library (all categories combined, deduplicated). */
    private val _totalEntries = MutableStateFlow(0)
    val totalEntries: StateFlow<Int> = _totalEntries.asStateFlow()

    // ── D-141: Multi-select state ──

    /** Whether multi-select mode is active. */
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    /** Set of selected mainIds in multi-select mode. */
    private val _selectedMainIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedMainIds: StateFlow<Set<String>> = _selectedMainIds.asStateFlow()

    /** Whether the category picker popup is shown (from multi-select bottom bar). */
    private val _showMultiSelectCategorySheet = MutableStateFlow(false)
    val showMultiSelectCategorySheet: StateFlow<Boolean> = _showMultiSelectCategorySheet.asStateFlow()

    /** Whether the delete confirmation dialog is shown (from multi-select bottom bar). */
    private val _showDeleteConfirmation = MutableStateFlow(false)
    val showDeleteConfirmation: StateFlow<Boolean> = _showDeleteConfirmation.asStateFlow()

    // ── D-141: In-memory cache for AniList data ──
    /** Caches AniListAnime by anilistId to prevent re-fetching on every tab switch. */
    private val anilistCache = mutableMapOf<Int, AniListAnime>()

    // ── Sort ──
    private val _sortType = MutableStateFlow(LibrarySortType.TITLE)
    val sortType: StateFlow<LibrarySortType> = _sortType

    private val _sortAscending = MutableStateFlow(true)
    val sortAscending: StateFlow<Boolean> = _sortAscending

    // ── Display & badges ──
    private val _displayMode = MutableStateFlow(LibraryDisplayMode.COMPACT_GRID)
    val displayMode: StateFlow<LibraryDisplayMode> = _displayMode

    private val _columns = MutableStateFlow(3)
    val columns: StateFlow<Int> = _columns

    // D-242-fix: default to 1 line (user can select 2 or 3 in the Customize sheet).
    private val _titleLines = MutableStateFlow(1)
    val titleLines: StateFlow<Int> = _titleLines

    private val _episodeBadgeMode = MutableStateFlow(EpisodeBadgeMode.OFF)
    val episodeBadgeMode: StateFlow<EpisodeBadgeMode> = _episodeBadgeMode

    private val _episodeBadgePosition = MutableStateFlow(BadgePosition.TOP_END)
    val episodeBadgePosition: StateFlow<BadgePosition> = _episodeBadgePosition

    private val _showScoreBadge = MutableStateFlow(false)
    val showScoreBadge: StateFlow<Boolean> = _showScoreBadge

    private val _scoreBadgePosition = MutableStateFlow(BadgePosition.TOP_START)
    val scoreBadgePosition: StateFlow<BadgePosition> = _scoreBadgePosition

    private val _showContinueWatching = MutableStateFlow(true)
    val showContinueWatching: StateFlow<Boolean> = _showContinueWatching

    private val _showTotalEntries = MutableStateFlow(true)
    val showTotalEntries: StateFlow<Boolean> = _showTotalEntries

    private val _showCategoryCounts = MutableStateFlow(false)
    val showCategoryCounts: StateFlow<Boolean> = _showCategoryCounts

    // D-242-fix14: Advanced RELEASED badge sub-options.
    /** Which audio type's released episodes to show when badge mode = RELEASED. */
    private val _releasedAudioFilter = MutableStateFlow(ReleasedAudioFilter.BOTH)
    val releasedAudioFilter: StateFlow<ReleasedAudioFilter> = _releasedAudioFilter

    /** When true + badge mode = RELEASED, show unwatched counts instead of total released. */
    private val _releasedUnwatchedOnly = MutableStateFlow(false)
    val releasedUnwatchedOnly: StateFlow<Boolean> = _releasedUnwatchedOnly

    // D-242-fix17: Cover border settings.
    /** Whether cover borders are enabled. */
    private val _coverBorderEnabled = MutableStateFlow(false)
    val coverBorderEnabled: StateFlow<Boolean> = _coverBorderEnabled

    /** The color of the cover border (when enabled). */
    private val _coverBorderColor = MutableStateFlow(CoverBorderColor.GRAY)
    val coverBorderColor: StateFlow<CoverBorderColor> = _coverBorderColor

    /** The width of the cover border (when enabled). */
    private val _coverBorderWidth = MutableStateFlow(CoverBorderWidth.THIN)
    val coverBorderWidth: StateFlow<CoverBorderWidth> = _coverBorderWidth

    // D-242-fix18: All Caught Up tag + list mode settings.
    /** When true, shows "All Caught Up" tag for series with 0 unwatched episodes. */
    private val _showAllCaughtUpTag = MutableStateFlow(false)
    val showAllCaughtUpTag: StateFlow<Boolean> = _showAllCaughtUpTag

    /** List mode density (controls entry size). */
    private val _listDensity = MutableStateFlow(ListDensity.NORMAL)
    val listDensity: StateFlow<ListDensity> = _listDensity

    /** List mode title position (top or bottom). */
    private val _listTitlePosition = MutableStateFlow(ListTitlePosition.BOTTOM)
    val listTitlePosition: StateFlow<ListTitlePosition> = _listTitlePosition

    // D-242-fix21: Comfortable border mode (cover-only vs full).
    private val _comfortableBorderMode = MutableStateFlow(ComfortableBorderMode.COVER_AND_TITLE)
    val comfortableBorderMode: StateFlow<ComfortableBorderMode> = _comfortableBorderMode

    // D-251: When true + display mode = COMFORTABLE_GRID, the title text under
    // covers is hidden (cover-only look, but keeps Comfortable's rounded corners
    // and grid spacing — distinct from the square, edge-to-edge COVER_ONLY mode).
    private val _hideTitlesInComfortable = MutableStateFlow(false)
    val hideTitlesInComfortable: StateFlow<Boolean> = _hideTitlesInComfortable

    init {
        // Task 62 (round 22 — H2, the un-debounced per-keystroke sort): every
        // character typed used to run the FULL filter+sort (O(n log n) + a new
        // 653-entry list emission) SYNCHRONOUSLY on the Main thread inside the
        // text-input frame. The pipeline below collapses query/sort changes
        // through ONE combined, debounced collector that runs the actual
        // filter+sort on the Default dispatcher — typing stays butter-smooth.
        startFilterPipeline()
        // Task 62 (round 22 — M4, the 23 synchronous SharedPreferences reads in
        // VM init): the first construction used to parse the whole prefs XML on
        // the Main thread inside the first composition frame. The reads now run
        // on Default; the library load runs AFTER them (its single emission
        // applies the loaded preferences — the ordering is the whole point).
        viewModelScope.launch {
            withContext(dispatchers.default) { loadPreferences() }
            if (_state.value !is LibraryState.Success) _state.value = LibraryState.Loading
            loadLibraryImpl()
        }
    }

    /**
     * Task 62 (H2): the debounced query/sort pipeline (see init). Its own
     * function so the FlowPreview opt-in (debounce) scopes cleanly.
     */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun startFilterPipeline() {
        viewModelScope.launch {
            combine(
                _searchQuery,
                _sortType,
                _sortAscending,
            ) { query, sort, ascending -> Triple(query, sort, ascending) }
                .debounce(200L)
                .collect { runFiltersOffMain() }
        }
    }

    // ── D-286: scroll state survives tab switches ──────────────────────────
    //
    // The Library composable leaves composition when the user switches to
    // Browse/Search/More — rememberLazyGridState()/rememberLazyListState() die
    // with it, so returning to the Library always snapped back to the top (part
    // of the "whole page reloads" feel). The ViewModel is Activity-scoped
    // (koinViewModel resolves against the Activity's ViewModelStore), so states
    // held HERE persist across tab switches: coming back shows the grid exactly
    // where the user left it. LazyGridState/LazyListState are plain @Stable
    // classes — constructing them outside composition is a standard pattern.
    /** Grid-mode scroll position — shared with LibraryScreen (D-286). */
    val gridState = androidx.compose.foundation.lazy.grid.LazyGridState()

    /** List-mode scroll position — shared with LibraryScreen (D-286). */
    val listState = androidx.compose.foundation.lazy.LazyListState()

    // D-290: Comfortable-mode masonry scroll position — SAME retention as
    // gridState/listState. It used to be rememberLazyStaggeredGridState()
    // INSIDE LibraryGrid (died on every tab switch → Comfortable users lost
    // their scroll on every return) while the VM-held gridState went stale at
    // an old index — switching display modes re-attached that stale index.
    /** Comfortable-grid (masonry) scroll position — shared with LibraryScreen (D-290). */
    val staggeredState = androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState()

    /**
     * D-290: resets all three scroll states to the top. Called when the
     * DATASET identity changes (category switch, search query change) — a new
     * dataset should present from its top, and a stale retained index would
     * otherwise land the grid mid-list (or clamp to a bottom when the new set
     * is smaller). requestScrollToItem is the non-suspend 1.7+ API: it
     * schedules the position for the next remeasure, so it is safe to call
     * while the grid is between compositions.
     */
    private fun resetScrollToTop() {
        runCatching {
            gridState.requestScrollToItem(0, 0)
            listState.requestScrollToItem(0, 0)
            staggeredState.requestScrollToItem(0, 0)
        }
    }

    // ── D-290: master list + single-emission state pipeline ────────────────
    //
    // Device feedback on v0.2.55: "the library page … scrolled way too much
    // down automatically by itself … about the middle" after refresh.
    // Root cause (R-1 research): loadLibraryImpl emitted Success(entries) in
    // DATE_ADDED order and THEN applyFilters() re-emitted the sorted list. If
    // a recomposition landed between the two writes (preemption/GC pause on
    // the Default dispatcher), the grid composed the UNSORTED list and
    // LazyGrid's key-based anchoring (key = mainId) followed the previously
    // first-visible item to its DATE_ADDED rank — the middle of the list.
    // Fix: the master list is stored unfiltered here, and the state flow is
    // only ever written ONCE per load, with the final filtered+sorted list —
    // no intermediate ordering is ever visible to composition. (This also
    // fixes a latent bug: applyFilters() used to re-filter the ALREADY
    // filtered state — clearing a search query could never restore removed
    // entries until a full reload.)
    /** The unfiltered, unsorted entries of the current category view (D-290). */
    private var masterEntries: List<LibraryEntry> = emptyList()

    /**
     * D-291: cover-URL keys whose image has been revealed at least once.
     *
     * Drives the reveal-once cover animation in LibraryCoverImage: a cover
     * fades in the FIRST time it loads; after that it renders instantly (no
     * re-animation on scroll-back or tab switches — "if they were previously
     * loaded then there is no need to reload them completely"). Lives in the
     * Activity-scoped VM so it survives tab switches; cleared ONLY by
     * [refreshLibrary] (a pull-to-refresh is the user's explicit "reload
     * everything" signal).
     */
    val revealedCoverKeys = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** Read-only check — is this cover's key already revealed? (D-291) */
    fun isCoverRevealed(key: String): Boolean = key in revealedCoverKeys

    /** Records that a cover's key has started its reveal (D-291). */
    fun markCoverRevealed(key: String) {
        revealedCoverKeys.add(key)
    }

    /**
     * Load the library from the content ID system.
     * D-141: Uses in-memory cache for AniList data to prevent re-fetching.
     *
     * Delegates to [loadLibraryImpl] (which does the actual suspend work) so that
     * [refreshLibrary] can await the same body and toggle [_isRefreshing] for the
     * EXACT duration of the load — no hardcoded delays.
     */
    fun loadLibrary() {
        // D-286: INSTANT TAB SWITCH — when the grid is already showing, keep it
        // on screen and refresh silently in the background. The old unconditional
        // `_state.value = LibraryState.Loading` tore the whole grid down on EVERY
        // tab switch (Browse → Library → Browse → …), flashing a loading spinner
        // and re-showing the list from scratch; now the already-loaded entries
        // stay visible while the (batched, fast) reload swaps the result in.
        if (_state.value is LibraryState.Success) {
            viewModelScope.launch {
                loadLibraryImpl()
            }
        } else {
            _state.value = LibraryState.Loading
            viewModelScope.launch {
                loadLibraryImpl()
            }
        }
    }

    /**
     * The suspend body of [loadLibrary]. D-285: BATCHED load — the whole library
     * comes from 7 queries total (categories, library items, main entries,
     * content details, watched counts, last-watched timestamps, episode audio
     * rows), assembled in memory. The previous implementation issued
     * getMainEntryByMainId + getContentDetails + getEpisodeMetadata +
     * getWatchedEpisodeCount + getLastWatchedAt PER ENTRY (5×N queries — a
     * 653-item library cost ~3,300 queries, 4-5 seconds) and ran them all on
     * the MAIN thread (viewModelScope defaults to Main.immediate), freezing the
     * UI for the full duration. Now: [Dispatchers.Default] + batch reads.
     *
     * Fully offline: entries without a data-source link show the stored
     * extension-axis fallback — the OLD "fetch AniList on miss" branch was
     * unreachable dead code (anilistId is non-null only when dataSourceType ==
     * "anilist", which makes hasDataSourceLink true, which always takes the
     * cached branch first); entries with no data link have no stored AniList
     * ID to fetch with, so there is nothing to backfill.
     *
     * Does NOT touch [_isRefreshing] — that's the caller's responsibility
     * (see [refreshLibrary]).
     */
    private suspend fun loadLibraryImpl() = withContext(Dispatchers.Default) {
        try {
            // Batch query 1: all categories.
            val cats = contentRepository.getAllCategories()
            _categories.value = cats

            // Batch query 2: every library_item row (mainId + categoryId + addedAt,
            // newest first). The category filter, per-category counts, and the
            // total count all derive from this ONE list in memory.
            val items = contentRepository.getAllLibraryItems()
            val selectedCategoryId = _selectedCategoryId.value

            // Per-category counts + total across ALL categories (D-143). The
            // unique (main_id, category_id) index guarantees at most one row per
            // pair, so counting rows == counting distinct mainIds per category.
            val counts = mutableMapOf<Long, Int>()
            for (item in items) {
                counts[item.categoryId] = (counts[item.categoryId] ?: 0) + 1
            }
            _categoryCounts.value = counts
            _totalEntries.value = items.map { it.mainId }.distinct().size

            // mainIds in view — filtered by the selected category when set.
            // Preserves the added_at DESC order (the DATE_ADDED sort relies on it).
            val uniqueMainIds = if (selectedCategoryId != null) {
                items.filter { it.categoryId == selectedCategoryId }.map { it.mainId }.distinct()
            } else {
                items.map { it.mainId }.distinct()
            }

            Logger.i(TAG) { "Library: ${uniqueMainIds.size} items in view, ${_totalEntries.value} total (category=${selectedCategoryId ?: "all"})" }

            if (uniqueMainIds.isEmpty()) {
                _state.value = LibraryState.Empty
                return@withContext
            }

            // Batch queries 3+4: every library main_entry (added_at DESC) + every
            // content_details row — joined in memory by mainId.
            val recordsById = contentRepository.getAllLibraryContentRecords()
                .associateBy { it.mainId }
            val detailsById = contentRepository.getAllContentDetailsMap()

            val entries = mutableListOf<LibraryEntry>()

            for (mainId in uniqueMainIds) {
                val content = recordsById[mainId] ?: continue
                val details = detailsById[mainId]

                if (details != null && details.hasDataSourceLink) {
                    // data-axis populated — instant display (D-198).
                    entries.add(
                        LibraryEntry(
                            mainId = mainId,
                            anilistId = details.anilistId,
                            sourceId = content.extensionId,
                            animeUrl = content.animeUrl,
                            title = content.title,
                            // D-248: two-way cover fallback — AniList cover first,
                            // extension cover when AniList's is missing/null.
                            coverUrl = details.dataCoverUrl ?: details.extThumbnailUrl,
                            averageScore = details.dataScore?.toInt(),
                            episodes = details.dataEpisodes?.toInt(),
                            seasonYear = details.dataSeasonYear?.toInt(),
                            status = details.dataStatus,
                        ),
                    )
                } else {
                    // No data-axis link — stored fallback (extension axis, or bare
                    // main_entry title when even the details row is missing — D-222).
                    entries.add(
                        LibraryEntry.fromExtension(
                            mainId = mainId,
                            title = content.title,
                            // D-248: two-way cover fallback — extension cover first,
                            // AniList cover when the extension's is missing/null.
                            coverUrl = details?.extThumbnailUrl ?: details?.dataCoverUrl,
                            sourceId = content.extensionId ?: details?.sourceId,
                            animeUrl = content.animeUrl ?: details?.animeUrl,
                        ),
                    )
                }
            }

            if (entries.isEmpty()) {
                masterEntries = emptyList()
                _state.value = LibraryState.Empty
            } else {
                // Batch queries 5-7: badge enrichment (released count, audio, watched).
                enrichEntriesWithBadgeData(entries)
                // D-290: SINGLE emission — the final filtered+sorted list is
                // computed BEFORE any state write, so no unsorted intermediate
                // ordering can ever be composed (the key-anchor jump bug).
                masterEntries = entries
                _state.value = LibraryState.Success(
                    filterAndSort(
                        entries = entries,
                        query = _searchQuery.value,
                        sortType = _sortType.value,
                        ascending = _sortAscending.value,
                    ),
                )
            }
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Failed to load library: ${e.message}" }
            _state.value = LibraryState.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Reload library but use cached data only (no network fetch).
     * Used when switching category tabs — just re-filters from the content DB.
     *
     * D-285: delegates to the BATCHED [loadLibraryImpl] — the per-entry query
     * loop this used to duplicate is gone (one implementation, 7 queries total,
     * background-dispatched). The old entries stay on screen until the fresh
     * state arrives (~tens of ms) — no loading flash on category switches.
     */
    fun reloadFromCache() {
        viewModelScope.launch {
            loadLibraryImpl()
        }
    }

    /**
     * Clear the AniList cache. Called when the user pulls to refresh or
     * when the app needs fresh data.
     */
    fun clearCache() {
        anilistCache.clear()
        Logger.i(TAG) { "AniList cache cleared" }
    }

    fun setSearchQuery(query: String) {
        val changed = _searchQuery.value != query
        _searchQuery.value = query
        // Task 62 (H2): NO synchronous applyFilters() here — the debounced
        // pipeline in init re-derives the list off Main (200ms after the last
        // keystroke). The scroll reset stays immediate: a changed query is a
        // changed dataset, and it presents from the top.
        if (changed) resetScrollToTop()
    }

    // ── Category management (D-138, D-140) ──

    /**
     * Select a category. D-141: Uses reloadFromCache instead of loadLibrary
     * to avoid re-fetching AniList data on every tab switch.
     */
    fun selectCategory(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
        // D-242-fix3: persist the selected category across app restarts.
        // -1L sentinel = "All" (null selection).
        preferenceStore.putLong(KEY_SELECTED_CATEGORY, categoryId ?: -1L)
        // D-290: switching category switches DATASET — start it from the top
        // (a retained index from the previous category would land mid-list or
        // clamp to the bottom of a smaller set).
        resetScrollToTop()
        reloadFromCache()
    }

    /** D.5: Whether a pull-to-refresh is in progress. */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /**
     * Pull-to-refresh entry point. Clears the in-memory AniList cache, then
     * re-runs [loadLibraryImpl] (the actual suspend load) inside a single
     * coroutine so [_isRefreshing] tracks the TRUE duration of the refresh.
     *
     * The M3 PullToRefreshBox indicator spins for exactly as long as the load
     * takes — no hardcoded 500ms delay, no early dismiss, no lingering spinner.
     */
    fun refreshLibrary() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                clearCache()
                // D-291: a pull-to-refresh is the user's explicit "reload
                // everything" signal — clear the reveal-once set so covers
                // fade back in as they re-load (progressive loading "should
                // only work if they were not loaded … unless the user refreshes
                // the whole page again").
                revealedCoverKeys.clear()
                loadLibraryImpl()
            } catch (e: Exception) {
                Logger.e(TAG, e) { "Library refresh failed: ${e.message}" }
            } finally {
                _isRefreshing.value = false
                Logger.i(TAG) { "Library refresh complete" }
            }
        }
    }

    fun showCategoryManagement(category: LibraryCategory) {
        _categoryToManage.value = category
    }

    fun dismissCategoryManagement() {
        _categoryToManage.value = null
    }

    fun deleteCategory(categoryId: Long) {
        // Task 62 (round 22 — H1, synchronous SQLite on Main): the category
        // mutations + the follow-up reload now run on the IO dispatcher — the
        // old path executed the writes (and for the move-to-default variant a
        // per-item COUNT+INSERT loop) inside the click handler on Main, an
        // ANR risk on large libraries.
        viewModelScope.launch(dispatchers.io) {
            contentRepository.deleteCategory(categoryId)
            _categoryToManage.value = null
            if (_selectedCategoryId.value == categoryId) {
                _selectedCategoryId.value = null
            }
            loadLibraryImpl()
        }
    }

    fun deleteCategoryAndMoveToDefault(categoryId: Long) {
        viewModelScope.launch(dispatchers.io) {
            val defaultCat = contentRepository.getDefaultCategory()
            if (defaultCat != null) {
                val mainIds = contentRepository.getMainIdsByCategory(categoryId)
                for (mainId in mainIds) {
                    contentRepository.addToCategory(mainId, defaultCat.id)
                }
            }
            contentRepository.deleteCategory(categoryId)
            _categoryToManage.value = null
            if (_selectedCategoryId.value == categoryId) {
                _selectedCategoryId.value = null
            }
            loadLibraryImpl()
        }
    }

    fun renameCategory(categoryId: Long, newName: String) {
        viewModelScope.launch(dispatchers.io) {
            contentRepository.renameCategory(categoryId, newName)
            _categoryToManage.value = null
            loadLibraryImpl()
        }
    }

    fun createCategory(name: String) {
        viewModelScope.launch(dispatchers.io) {
            contentRepository.createCategory(name)
            loadLibraryImpl()
        }
    }

    // ── D-141: Multi-select ──

    /** Enter selection mode + select the given mainId. */
    fun enterSelectionMode(mainId: String) {
        _isSelectionMode.value = true
        _selectedMainIds.value = setOf(mainId)
        Logger.i(TAG) { "Selection mode: started with $mainId" }
    }

    /** Toggle a selection in multi-select mode. */
    fun toggleSelection(mainId: String) {
        val current = _selectedMainIds.value.toMutableSet()
        if (mainId in current) {
            current.remove(mainId)
        } else {
            current.add(mainId)
        }
        _selectedMainIds.value = current
        // If nothing is selected, exit selection mode.
        if (current.isEmpty()) {
            exitSelectionMode()
        }
    }

    /** Select all visible entries. */
    fun selectAll() {
        val current = (_state.value as? LibraryState.Success)?.entries ?: return
        _selectedMainIds.value = current.map { it.mainId }.toSet()
    }

    /** Clear selection but stay in selection mode. */
    fun clearSelection() {
        _selectedMainIds.value = emptySet()
    }

    /** Invert selection. */
    fun invertSelection() {
        val current = (_state.value as? LibraryState.Success)?.entries ?: return
        val all = current.map { it.mainId }.toSet()
        val selected = _selectedMainIds.value
        _selectedMainIds.value = all - selected
    }

    /** Exit selection mode. */
    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedMainIds.value = emptySet()
    }

    /** Show the category picker popup (from multi-select bottom bar). */
    fun showMultiSelectCategorySheet() {
        // D-146: Initialize the membership set before showing the sheet.
        // Task 62 (H1): the categories×selected point queries used to run on
        // Main inside this click handler — they run on IO now; the sheet
        // opens a beat later with the ready membership.
        viewModelScope.launch(dispatchers.io) {
            _multiSelectCategoryMembership.value = getCategoriesForSelected()
                .filter { it.value }
                .map { it.key }
                .toSet()
            _showMultiSelectCategorySheet.value = true
        }
    }

    fun dismissMultiSelectCategorySheet() {
        _showMultiSelectCategorySheet.value = false
    }

    fun addSelectedToCategory(categoryId: Long) {
        // Task 62 (H1): the N×(COUNT+INSERT) loop for the selected entries ran
        // SYNCHRONOUSLY on Main inside this click handler (200 selected ≈ 400
        // SQL statements — a visible freeze). Off Main now; the selection is
        // snapshotted at tap time (the sheet stays open for further toggles).
        // D-146: Does NOT close the sheet — user can select multiple categories.
        // The sheet is closed via dismissMultiSelectCategorySheet() (Done button).
        val selected = _selectedMainIds.value.toList()
        viewModelScope.launch(dispatchers.io) {
            for (mainId in selected) {
                contentRepository.addToCategory(mainId, categoryId)
            }
            Logger.i(TAG) { "Added ${selected.size} entries to category $categoryId" }
            // Update the category membership state (for checkbox display).
            _multiSelectCategoryMembership.value = _multiSelectCategoryMembership.value + categoryId
        }
    }

    /**
     * Remove all selected entries from a category.
     * D-146: Does NOT close the sheet — user can deselect multiple categories.
     * Task 62 (H1): the N×DELETE loop runs off Main (see addSelectedToCategory).
     */
    fun removeSelectedFromCategory(categoryId: Long) {
        val selected = _selectedMainIds.value.toList()
        viewModelScope.launch(dispatchers.io) {
            for (mainId in selected) {
                contentRepository.removeFromCategory(mainId, categoryId)
            }
            Logger.i(TAG) { "Removed ${selected.size} entries from category $categoryId" }
            _multiSelectCategoryMembership.value = _multiSelectCategoryMembership.value - categoryId
        }
    }

    /**
     * D-146: Called when the user taps "Done" in the multi-select category picker.
     * Closes the sheet + exits selection mode + reloads the library.
     */
    fun doneMultiSelectCategorySheet() {
        _showMultiSelectCategorySheet.value = false
        exitSelectionMode()
        loadLibrary()
    }

    /** Tracks which categories ALL selected entries are currently in (for checkbox display). */
    private val _multiSelectCategoryMembership = MutableStateFlow<Set<Long>>(emptySet())
    val multiSelectCategoryMembership: StateFlow<Set<Long>> = _multiSelectCategoryMembership.asStateFlow()

    /** Show the delete confirmation dialog (from multi-select bottom bar). */
    fun showDeleteConfirmation() {
        _showDeleteConfirmation.value = true
    }

    fun dismissDeleteConfirmation() {
        _showDeleteConfirmation.value = false
    }

    /**
     * Delete all selected entries from the library.
     * Task 62 (H1): the N×DELETE (CASCADE) loop runs off Main.
     */
    fun deleteSelected() {
        val selected = _selectedMainIds.value.toList()
        viewModelScope.launch(dispatchers.io) {
            for (mainId in selected) {
                contentRepository.removeFromLibrary(mainId)
            }
            Logger.i(TAG) { "Deleted ${selected.size} entries from library" }
            _showDeleteConfirmation.value = false
            exitSelectionMode()
            loadLibraryImpl()
        }
    }

    /** Get categories that ALL selected entries are in (for the category picker). */
    fun getCategoriesForSelected(): Map<Long, Boolean> {
        val cats = _categories.value
        val selected = _selectedMainIds.value
        if (selected.isEmpty()) return emptyMap()
        return cats.associate { cat ->
            // True if ALL selected entries are in this category.
            val allIn = selected.all { contentRepository.isInCategory(it, cat.id) }
            cat.id to allIn
        }
    }

    // ── Sort setters ──
    // Task 62 (H2): none of these call the filter pipeline directly anymore —
    // the combined+debounced collector in init picks up the flow change and
    // re-derives the list off Main (the sort lands ~200ms after the tap,
    // imperceptibly). The preference writes stay (a single SharedPreferences
    // edit per tap).
    fun setSortType(sort: LibrarySortType) {
        _sortType.value = sort
        preferenceStore.putString(KEY_SORT_TYPE, sort.name)
    }

    fun setSortAscending(value: Boolean) {
        _sortAscending.value = value
        preferenceStore.putBoolean(KEY_SORT_ASCENDING, value)
    }

    fun setSort(sort: LibrarySortType, ascending: Boolean) {
        _sortType.value = sort
        _sortAscending.value = ascending
        preferenceStore.putString(KEY_SORT_TYPE, sort.name)
        preferenceStore.putBoolean(KEY_SORT_ASCENDING, ascending)
    }

    // ── Display & badge setters ──
    fun setDisplayMode(mode: LibraryDisplayMode) {
        _displayMode.value = mode
        preferenceStore.putString(KEY_DISPLAY_MODE, mode.name)
    }

    fun setColumns(value: Int) {
        _columns.value = value
        preferenceStore.putInt(KEY_COLUMNS, value)
    }

    fun setTitleLines(value: Int) {
        _titleLines.value = value
        preferenceStore.putInt(KEY_TITLE_LINES, value)
    }

    fun setEpisodeBadgeMode(mode: EpisodeBadgeMode) {
        _episodeBadgeMode.value = mode
        preferenceStore.putString(KEY_EPISODE_BADGE_MODE, mode.name)
    }

    fun setEpisodeBadgePosition(pos: BadgePosition) {
        _episodeBadgePosition.value = pos
        preferenceStore.putString(KEY_EPISODE_BADGE_POS, pos.name)
    }

    fun setShowScoreBadge(value: Boolean) {
        _showScoreBadge.value = value
        preferenceStore.putBoolean(KEY_SHOW_SCORE_BADGE, value)
    }

    fun setScoreBadgePosition(pos: BadgePosition) {
        _scoreBadgePosition.value = pos
        preferenceStore.putString(KEY_SCORE_BADGE_POS, pos.name)
    }

    fun setShowContinueWatching(value: Boolean) {
        _showContinueWatching.value = value
        preferenceStore.putBoolean(KEY_SHOW_CONTINUE_WATCHING, value)
    }

    fun setShowTotalEntries(value: Boolean) {
        _showTotalEntries.value = value
        preferenceStore.putBoolean(KEY_SHOW_TOTAL_ENTRIES, value)
    }

    fun setShowCategoryCounts(value: Boolean) {
        _showCategoryCounts.value = value
        preferenceStore.putBoolean(KEY_SHOW_CATEGORY_COUNTS, value)
    }

    // D-242-fix14: Advanced RELEASED badge sub-option setters.
    fun setReleasedAudioFilter(filter: ReleasedAudioFilter) {
        _releasedAudioFilter.value = filter
        preferenceStore.putString(KEY_RELEASED_AUDIO_FILTER, filter.name)
        Logger.i(TAG) { "setReleasedAudioFilter — $filter" }
    }

    fun setReleasedUnwatchedOnly(value: Boolean) {
        _releasedUnwatchedOnly.value = value
        preferenceStore.putBoolean(KEY_RELEASED_UNWATCHED_ONLY, value)
        Logger.i(TAG) { "setReleasedUnwatchedOnly — $value" }
    }

    // D-242-fix17: Cover border setters.
    fun setCoverBorderEnabled(value: Boolean) {
        _coverBorderEnabled.value = value
        preferenceStore.putBoolean(KEY_COVER_BORDER_ENABLED, value)
        Logger.i(TAG) { "setCoverBorderEnabled — $value" }
    }

    fun setCoverBorderColor(color: CoverBorderColor) {
        _coverBorderColor.value = color
        preferenceStore.putString(KEY_COVER_BORDER_COLOR, color.name)
        Logger.i(TAG) { "setCoverBorderColor — $color" }
    }

    fun setCoverBorderWidth(width: CoverBorderWidth) {
        _coverBorderWidth.value = width
        preferenceStore.putString(KEY_COVER_BORDER_WIDTH, width.name)
        Logger.i(TAG) { "setCoverBorderWidth — $width" }
    }

    // D-242-fix18: All Caught Up tag + list mode setters.
    fun setShowAllCaughtUpTag(value: Boolean) {
        _showAllCaughtUpTag.value = value
        preferenceStore.putBoolean(KEY_SHOW_ALL_CAUGHT_UP_TAG, value)
        Logger.i(TAG) { "setShowAllCaughtUpTag — $value" }
    }

    fun setListDensity(density: ListDensity) {
        _listDensity.value = density
        preferenceStore.putString(KEY_LIST_DENSITY, density.name)
        Logger.i(TAG) { "setListDensity — $density" }
    }

    fun setListTitlePosition(position: ListTitlePosition) {
        _listTitlePosition.value = position
        preferenceStore.putString(KEY_LIST_TITLE_POSITION, position.name)
        Logger.i(TAG) { "setListTitlePosition — $position" }
    }

    // D-242-fix21: Comfortable border mode setter.
    fun setComfortableBorderMode(mode: ComfortableBorderMode) {
        _comfortableBorderMode.value = mode
        preferenceStore.putString(KEY_COMFORTABLE_BORDER_MODE, mode.name)
        Logger.i(TAG) { "setComfortableBorderMode — $mode" }
    }

    // D-251: Hide-titles-in-Comfortable setter.
    fun setHideTitlesInComfortable(value: Boolean) {
        _hideTitlesInComfortable.value = value
        preferenceStore.putBoolean(KEY_COMFORTABLE_HIDE_TITLES, value)
        Logger.i(TAG) { "setHideTitlesInComfortable — $value" }
    }

    /**
     * D-242-fix10: Enriches LibraryEntry list with badge data:
     * - releasedEpisodes: count of cached episodes (actual aired count)
     * - audioAvailability: aggregated SUB/DUB/HSUB across all cached episodes
     * - watchedCount: how many episodes the user has watched
     *
     * D-242-fix14: Also counts per-audio-type episode counts (subEpisodeCount,
     * dubEpisodeCount) for the advanced RELEASED badge sub-options.
     *
     * D-285: BATCHED — 3 queries for the WHOLE library (audio aggregates,
     * watched counts, last-watched timestamps) instead of the old per-entry loop
     * (3 queries × N entries; a 653-item library cost ~1,959 queries here).
     * Called from [loadLibraryImpl]'s Dispatchers.Default context, so the reads
     * stay off the main thread. Semantics are identical to the old loop.
     */
    private suspend fun enrichEntriesWithBadgeData(entries: MutableList<LibraryEntry>) {
        val audioAggregates = dataCacheRepository.getAllEpisodeAudioAggregates()
        val watchedCounts = watchProgressStore?.getAllWatchedCounts() ?: emptyMap()
        val lastWatchedMap = watchProgressStore?.getAllLastWatchedAt() ?: emptyMap()

        for (i in entries.indices) {
            val entry = entries[i]
            val audio = audioAggregates[entry.mainId]
            val audioAvail = audio?.let {
                if (it.hasSub || it.hasDub || it.hasHsub) {
                    com.confused.anikuta.core.common.AudioAvailability(it.hasSub, it.hasDub, it.hasHsub)
                } else null
            }

            entries[i] = entry.copy(
                releasedEpisodes = audio?.releasedCount?.takeIf { it > 0 },
                audioAvailability = audioAvail,
                watchedCount = watchedCounts[entry.mainId]?.takeIf { it > 0 },
                lastWatchedAt = lastWatchedMap[entry.mainId],
                subEpisodeCount = audio?.subCount?.takeIf { it > 0 },
                dubEpisodeCount = audio?.dubCount?.takeIf { it > 0 },
            )
        }

        Logger.d(TAG) {
            "enrichEntriesWithBadgeData — batched: ${entries.size} entries enriched from 3 queries"
        }
    }

    // ── Persistence ──
    private fun loadPreferences() {
        _sortType.value = preferenceStore
            .getString(KEY_SORT_TYPE, LibrarySortType.TITLE.name)
            .let { runCatching { LibrarySortType.valueOf(it) }.getOrDefault(LibrarySortType.TITLE) }
        _sortAscending.value = preferenceStore.getBoolean(KEY_SORT_ASCENDING, true)

        _displayMode.value = preferenceStore
            .getString(KEY_DISPLAY_MODE, LibraryDisplayMode.COMPACT_GRID.name)
            .let { runCatching { LibraryDisplayMode.valueOf(it) }.getOrDefault(LibraryDisplayMode.COMPACT_GRID) }
        _columns.value = preferenceStore.getInt(KEY_COLUMNS, 3).coerceIn(2, 5)
        _titleLines.value = preferenceStore.getInt(KEY_TITLE_LINES, 1).coerceIn(1, 3)

        _episodeBadgeMode.value = preferenceStore
            .getString(KEY_EPISODE_BADGE_MODE, EpisodeBadgeMode.OFF.name)
            .let { runCatching { EpisodeBadgeMode.valueOf(it) }.getOrDefault(EpisodeBadgeMode.OFF) }
        _episodeBadgePosition.value = preferenceStore
            .getString(KEY_EPISODE_BADGE_POS, BadgePosition.TOP_END.name)
            .let { runCatching { BadgePosition.valueOf(it) }.getOrDefault(BadgePosition.TOP_END) }

        _showScoreBadge.value = preferenceStore.getBoolean(KEY_SHOW_SCORE_BADGE, false)
        _scoreBadgePosition.value = preferenceStore
            .getString(KEY_SCORE_BADGE_POS, BadgePosition.TOP_START.name)
            .let { runCatching { BadgePosition.valueOf(it) }.getOrDefault(BadgePosition.TOP_START) }

        _showContinueWatching.value = preferenceStore.getBoolean(KEY_SHOW_CONTINUE_WATCHING, true)
        _showTotalEntries.value = preferenceStore.getBoolean(KEY_SHOW_TOTAL_ENTRIES, true)
        _showCategoryCounts.value = preferenceStore.getBoolean(KEY_SHOW_CATEGORY_COUNTS, false)

        // D-242-fix14: load advanced RELEASED badge sub-options.
        _releasedAudioFilter.value = preferenceStore
            .getString(KEY_RELEASED_AUDIO_FILTER, ReleasedAudioFilter.BOTH.name)
            .let { runCatching { ReleasedAudioFilter.valueOf(it) }.getOrDefault(ReleasedAudioFilter.BOTH) }
        _releasedUnwatchedOnly.value = preferenceStore.getBoolean(KEY_RELEASED_UNWATCHED_ONLY, false)

        // D-242-fix17: load cover border settings.
        _coverBorderEnabled.value = preferenceStore.getBoolean(KEY_COVER_BORDER_ENABLED, false)
        _coverBorderColor.value = preferenceStore
            .getString(KEY_COVER_BORDER_COLOR, CoverBorderColor.GRAY.name)
            .let { runCatching { CoverBorderColor.valueOf(it) }.getOrDefault(CoverBorderColor.GRAY) }
        _coverBorderWidth.value = preferenceStore
            .getString(KEY_COVER_BORDER_WIDTH, CoverBorderWidth.THIN.name)
            .let { runCatching { CoverBorderWidth.valueOf(it) }.getOrDefault(CoverBorderWidth.THIN) }

        // D-242-fix18: load All Caught Up tag + list mode settings.
        _showAllCaughtUpTag.value = preferenceStore.getBoolean(KEY_SHOW_ALL_CAUGHT_UP_TAG, false)
        _listDensity.value = preferenceStore
            .getString(KEY_LIST_DENSITY, ListDensity.NORMAL.name)
            .let { runCatching { ListDensity.valueOf(it) }.getOrDefault(ListDensity.NORMAL) }
        _listTitlePosition.value = preferenceStore
            .getString(KEY_LIST_TITLE_POSITION, ListTitlePosition.BOTTOM.name)
            .let { runCatching { ListTitlePosition.valueOf(it) }.getOrDefault(ListTitlePosition.BOTTOM) }

        // D-242-fix21: load comfortable border mode.
        _comfortableBorderMode.value = preferenceStore
            .getString(KEY_COMFORTABLE_BORDER_MODE, ComfortableBorderMode.COVER_AND_TITLE.name)
            .let { runCatching { ComfortableBorderMode.valueOf(it) }.getOrDefault(ComfortableBorderMode.COVER_AND_TITLE) }

        // D-251: load hide-titles-in-Comfortable toggle.
        _hideTitlesInComfortable.value = preferenceStore.getBoolean(KEY_COMFORTABLE_HIDE_TITLES, false)

        // D-242-fix3: restore last-selected category across app restarts.
        // -1L sentinel = "All" (null selection).
        val savedCatId = preferenceStore.getLong(KEY_SELECTED_CATEGORY, -1L)
        _selectedCategoryId.value = if (savedCatId == -1L) null else savedCatId
    }

    /**
     * Task 62 (round 22 — H2): the debounced pipeline's body. Applies the
     * filter+sort OFF Main and emits; the emission is guarded so a concurrent
     * master-list reload (its own single emission already applies these
     * inputs) always wins over a stale derived list.
     */
    private suspend fun runFiltersOffMain() {
        val current = _state.value
        if (current !is LibraryState.Success) return

        // D-290: re-derive from the MASTER list, not the already-filtered
        // state (the old re-filter could never restore entries removed by a
        // previous query once the query was cleared).
        val query = _searchQuery.value
        val sortType = _sortType.value
        val ascending = _sortAscending.value
        val entries = masterEntries
        val filtered = withContext(dispatchers.default) {
            filterAndSort(entries, query, sortType, ascending)
        }
        // Emit only when the inputs are STILL the ones this result was
        // derived from (a library reload that already emitted supersedes it).
        if (_searchQuery.value == query &&
            _sortType.value == sortType &&
            _sortAscending.value == ascending &&
            _state.value === current
        ) {
            _state.value = LibraryState.Success(filtered)
        }
    }

    /**
     * D-290: the pure filter+sort pipeline shared by [loadLibraryImpl] (single
     * emission) and [runFiltersOffMain] (query/sort changes). No state writes —
     * callers decide what to emit.
     */
    private fun filterAndSort(
        entries: List<LibraryEntry>,
        query: String,
        sortType: LibrarySortType,
        ascending: Boolean,
    ): List<LibraryEntry> {
        var filtered = entries

        if (query.isNotBlank()) {
            filtered = filtered.filter { it.title.contains(query, ignoreCase = true) }
        }

        filtered = when (sortType) {
            LibrarySortType.TITLE -> if (ascending) {
                filtered.sortedBy { it.title.lowercase() }
            } else {
                filtered.sortedByDescending { it.title.lowercase() }
            }
            LibrarySortType.SCORE -> if (ascending) {
                filtered.sortedBy { it.averageScore ?: 0 }
            } else {
                filtered.sortedByDescending { it.averageScore ?: 0 }
            }
            LibrarySortType.DATE_ADDED -> if (ascending) {
                filtered.asReversed()
            } else {
                filtered
            }
            // D-268: LAST_WATCHED — was a no-op stub; now sorts by last_watched_at.
            // ascending = oldest-watched first; descending = most-recent first.
            LibrarySortType.LAST_WATCHED -> if (ascending) {
                filtered.sortedBy { it.lastWatchedAt ?: 0L }
            } else {
                filtered.sortedByDescending { it.lastWatchedAt ?: 0L }
            }
            // D-268: BEHIND — caught-up (unwatchedCount 0) at top, behind (positive) at bottom.
            // ascending = caught-up first; descending = behind first.
            LibrarySortType.BEHIND -> if (ascending) {
                filtered.sortedWith(
                    compareBy<LibraryEntry> { it.unwatchedCount ?: 0 }
                        .thenBy { it.title.lowercase() }
                )
            } else {
                filtered.sortedWith(
                    compareByDescending<LibraryEntry> { it.unwatchedCount ?: 0 }
                        .thenBy { it.title.lowercase() }
                )
            }
            // D-268: SEASON_YEAR — ascending = oldest year first; descending = newest first.
            LibrarySortType.SEASON_YEAR -> if (ascending) {
                filtered.sortedBy { it.seasonYear ?: 0 }
            } else {
                filtered.sortedByDescending { it.seasonYear ?: 0 }
            }
        }

        return filtered
    }
}

sealed interface LibraryState {
    data object Loading : LibraryState
    data object Empty : LibraryState
    data class Success(val entries: List<LibraryEntry>) : LibraryState
    data class Error(val message: String) : LibraryState
}

enum class LibrarySortType(val displayName: String) {
    TITLE("Title"),
    SCORE("Score"),
    DATE_ADDED("Date Added"),
    LAST_WATCHED("Last Watched"),
    BEHIND("Behind"),        // D-268: caught-up top, behind bottom (by unwatchedCount)
    SEASON_YEAR("Year"),     // D-268: by season year
}

enum class LibraryDisplayMode {
    COMPACT_GRID,
    COMFORTABLE_GRID,
    COVER_ONLY,
    LIST,
}

enum class EpisodeBadgeMode {
    OFF,
    RELEASED,
    TOTAL,
}

/**
 * D-242-fix14: Which audio type's released episodes to show when the episode
 * badge mode is [EpisodeBadgeMode.RELEASED].
 *
 * - [BOTH]: show separate SUB and DUB badges side-by-side.
 * - [SUB]: show only the SUB released count (blue badge, subtitle icon).
 * - [DUB]: show only the DUB released count (orange badge, microphone icon).
 */
enum class ReleasedAudioFilter {
    BOTH,
    SUB,
    DUB,
}

enum class BadgePosition {
    TOP_START,
    TOP_END,
    BOTTOM_START,
    BOTTOM_END,
}

/**
 * D-242-fix18: Predefined cover border colors.
 *
 * The order is intentional (per user spec):
 * 1. GRAY — the default (neutral, works on any theme).
 * 2. THEME_ADAPTIVE — white in dark theme, black in light theme.
 * 3. PRIMARY — the app's lime accent color.
 * 4. SURFACE — dark gray (the "remaining" color).
 * 5. ADAPTIVE — special: extracts the dominant color from the cover image
 *    and adjusts it for contrast. Handled at render time, not a fixed hex.
 *
 * The [hex] value is an ARGB color (0xAARRGGBB). For ADAPTIVE, [hex] is
 * unused (0 means "extract at runtime").
 */
enum class CoverBorderColor(val hex: Long, val displayName: String) {
    GRAY(0xFF9E9E9E, "Gray"),
    THEME_ADAPTIVE(0xFF000000, "Theme"),  // hex unused; resolved at render time
    PRIMARY(0xFFB1F256, "Lime"),
    SURFACE(0xFF424242, "Dark Gray"),
    ADAPTIVE(0x00000000, "Adaptive"),     // extracts color from cover image
}

/**
 * D-242-fix20: List mode density options.
 *
 * Controls the size of list entries in LIST display mode. Text size also
 * scales with density (bigger density = bigger text).
 * - COMPACT: 48×68dp cover, 12sp text.
 * - NORMAL: 60×86dp cover, 15sp text.
 * - COMFORTABLE: 80×115dp cover, 18sp text.
 */
enum class ListDensity(val coverWidth: Int, val coverHeight: Int, val titleFontSize: Int, val displayName: String) {
    COMPACT(48, 68, 12, "Compact"),
    NORMAL(60, 86, 15, "Normal"),
    COMFORTABLE(80, 115, 18, "Comfortable"),
}

/**
 * D-242-fix18: Where to show the title in list mode.
 */
enum class ListTitlePosition(val displayName: String) {
    TOP("Top"),
    BOTTOM("Bottom"),
}

/**
 * D-242-fix21: Border mode for comfortable grid view.
 *
 * - [COVER_ONLY]: border wraps only the cover image (not the title below it).
 * - [COVER_AND_TITLE]: border wraps the entire card (cover + title).
 */
enum class ComfortableBorderMode(val displayName: String) {
    COVER_ONLY("Cover Only"),
    COVER_AND_TITLE("Full"),
}

/**
 * D-242-fix17: Cover border width options (in dp).
 *
 * The [widthDp] property holds the integer dp value. Call `.widthDp.dp` to
 * convert to a Compose [androidx.compose.ui.unit.Dp].
 */
enum class CoverBorderWidth(val widthDp: Int, val displayName: String) {
    THIN(1, "Thin"),
    MEDIUM(2, "Medium"),
    THICK(3, "Thick"),
}
