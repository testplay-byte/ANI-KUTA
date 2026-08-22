package com.confused.anikuta.feature.animelibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.anilist.api.AniListApi
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.content.LibraryCategory
import com.confused.anikuta.core.preferences.PreferenceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    init {
        loadPreferences()
        loadLibrary()
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
        _state.value = LibraryState.Loading
        viewModelScope.launch {
            loadLibraryImpl()
        }
    }

    /**
     * The suspend body of [loadLibrary]. Loads categories + counts, builds the
     * [LibraryEntry] list (cache-first, AniList-fetch-on-miss, extension-fallback),
     * and pushes the resulting [LibraryState] to [_state].
     *
     * Does NOT touch [_isRefreshing] — that's the caller's responsibility
     * (see [refreshLibrary]).
     */
    private suspend fun loadLibraryImpl() {
        try {
            // Load ALL categories + counts.
            val cats = contentRepository.getAllCategories()
            _categories.value = cats

            // Count items per category.
            val counts = mutableMapOf<Long, Int>()
            for (cat in cats) {
                counts[cat.id] = contentRepository.countItemsInCategory(cat.id)
            }
            _categoryCounts.value = counts

            // Get library mainIds — filtered by selected category if set.
            val mainIds = if (_selectedCategoryId.value != null) {
                contentRepository.getMainIdsByCategory(_selectedCategoryId.value!!)
            } else {
                contentRepository.getLibraryMainIds()
            }

            // Deduplicate mainIds (a content can be in multiple categories).
            val uniqueMainIds = mainIds.distinct()
            // D-143: totalEntries should show the TOTAL across ALL categories,
            // not just the selected category. Fetch the full count separately.
            val allMainIds = contentRepository.getLibraryMainIds()
            _totalEntries.value = allMainIds.distinct().size
            Logger.i(TAG) { "Library: ${uniqueMainIds.size} items in view, ${_totalEntries.value} total (category=${_selectedCategoryId.value ?: "all"})" }

            if (uniqueMainIds.isEmpty()) {
                _state.value = LibraryState.Empty
                return
            }

            // Build LibraryEntry for each content.
            val entries = mutableListOf<LibraryEntry>()
            for (mainId in uniqueMainIds) {
                val content = contentRepository.getMainEntryByMainId(mainId) ?: continue

                // D-198: anime_metadata_cache was absorbed into content_details (data-axis).
                // Check the data-source axis first — if populated, use it as the cached metadata.
                val details = contentRepository.getContentDetails(mainId)
                if (details != null && details.hasDataSourceLink) {
                    // Use content_details data-axis — instant display.
                    entries.add(
                        LibraryEntry(
                            mainId = mainId,
                            anilistId = details.anilistId,
                            sourceId = content.extensionId,
                            animeUrl = content.animeUrl,
                            title = content.title,
                            coverUrl = details.dataCoverUrl,
                            averageScore = details.dataScore?.toInt(),
                            episodes = details.dataEpisodes?.toInt(),
                            seasonYear = details.dataSeasonYear?.toInt(),
                            status = details.dataStatus,
                        ),
                    )
                    continue
                }

                // Not cached — try AniList detail (for fetching + caching).
                val anilistId = details?.anilistId
                if (details != null && anilistId != null) {
                    // Fetch fresh AniList data (first time only — will be cached after this).
                    try {
                        val anime = anilistApi.fetchAnimeDetails(anilistId)
                        // D-198: cache the AniList metadata in content_details (data-axis).
                        contentRepository.updateDataSourceAxis(
                            com.confused.anikuta.core.content.ContentDetails(
                                mainId = mainId,
                                dataSourceType = "anilist",
                                dataSourceRefId = anilistId.toString(),
                                dataScore = anime.averageScore?.toLong(),
                                dataEpisodes = anime.episodes?.toLong(),
                                dataSeason = anime.season,
                                dataSeasonYear = anime.seasonYear?.toLong(),
                                dataStatus = anime.status,
                                dataGenres = anime.genres?.joinToString(", "),
                                dataSynopsis = anime.description,
                                dataCoverUrl = anime.coverUrl,
                                dataBannerUrl = anime.bannerImage,
                                dataUpdatedAt = System.currentTimeMillis(),
                                // Extension axis preserved (call updateDataSourceAxis — not full upsert).
                                extensionType = details.extensionType,
                                extensionId = details.extensionId,
                                sourceId = details.sourceId,
                                animeUrl = details.animeUrl,
                                extDescription = details.extDescription,
                                extGenres = details.extGenres,
                                extStatus = details.extStatus,
                                extAuthor = details.extAuthor,
                                extArtist = details.extArtist,
                                extThumbnailUrl = details.extThumbnailUrl,
                                extExtraJson = details.extExtraJson,
                                extUpdatedAt = details.extUpdatedAt,
                            ),
                        )
                        entries.add(
                            LibraryEntry.fromAniList(
                                mainId = mainId,
                                anime = anime,
                                sourceId = content.extensionId,
                                animeUrl = content.animeUrl,
                            ),
                        )
                    } catch (e: Exception) {
                        Logger.w(TAG) { "AniList fetch failed for $anilistId: ${e.message}" }
                        // Fall back to stored data.
                        entries.add(
                            LibraryEntry(
                                mainId = mainId,
                                anilistId = anilistId,
                                sourceId = content.extensionId,
                                animeUrl = content.animeUrl,
                                title = content.title,
                                coverUrl = details.dataCoverUrl,
                                averageScore = details.dataScore?.toInt(),
                                episodes = details.dataEpisodes?.toInt(),
                                seasonYear = details.dataSeasonYear?.toInt(),
                                status = details.dataStatus,
                            ),
                        )
                    }
                } else {
                    // Extension-only content — use stored data.
                    entries.add(
                        LibraryEntry.fromExtension(
                            mainId = mainId,
                            title = content.title,
                            coverUrl = details?.extThumbnailUrl,
                            sourceId = content.extensionId ?: details?.sourceId,
                            animeUrl = content.animeUrl ?: details?.animeUrl,
                        ),
                    )
                }
            }

            if (entries.isEmpty()) {
                _state.value = LibraryState.Empty
            } else {
                // D-242-fix10: enrich entries with badge data (released count, audio, watched)
                enrichEntriesWithBadgeData(entries)
                _state.value = LibraryState.Success(entries)
                applyFilters()
            }
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Failed to load library: ${e.message}" }
            _state.value = LibraryState.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Reload library but use cached data only (no network fetch).
     * Used when switching tabs — just re-filters from the content DB.
     */
    fun reloadFromCache() {
        viewModelScope.launch {
            try {
                val mainIds = if (_selectedCategoryId.value != null) {
                    contentRepository.getMainIdsByCategory(_selectedCategoryId.value!!)
                } else {
                    contentRepository.getLibraryMainIds()
                }
                val uniqueMainIds = mainIds.distinct()
                // D-143: totalEntries = total across ALL categories.
                val allMainIds = contentRepository.getLibraryMainIds()
                _totalEntries.value = allMainIds.distinct().size

                if (uniqueMainIds.isEmpty()) {
                    _state.value = LibraryState.Empty
                    return@launch
                }

                val entries = mutableListOf<LibraryEntry>()
                for (mainId in uniqueMainIds) {
                    val content = contentRepository.getMainEntryByMainId(mainId) ?: continue

                    // D-198: anime_metadata_cache absorbed into content_details (data-axis).
                    // Use content_details (no network on tab switch).
                    val details = contentRepository.getContentDetails(mainId)
                    if (details != null && details.hasDataSourceLink) {
                        entries.add(
                            LibraryEntry(
                                mainId = mainId,
                                anilistId = details.anilistId,
                                sourceId = content.extensionId,
                                animeUrl = content.animeUrl,
                                title = content.title,
                                coverUrl = details.dataCoverUrl,
                                averageScore = details.dataScore?.toInt(),
                                episodes = details.dataEpisodes?.toInt(),
                                seasonYear = details.dataSeasonYear?.toInt(),
                                status = details.dataStatus,
                            ),
                        )
                    } else if (details != null) {
                        // Extension-only content — use stored data on the ext_* axis.
                        entries.add(
                            LibraryEntry.fromExtension(
                                mainId = mainId,
                                title = content.title,
                                coverUrl = details.extThumbnailUrl,
                                sourceId = content.extensionId ?: details.sourceId,
                                animeUrl = content.animeUrl ?: details.animeUrl,
                            ),
                        )
                    } else {
                        // D-222 FIX: content_details row is missing (e.g. populate
                        // didn't create it, or it was deleted). Fall back to the
                        // main_entry's title so the entry is at least visible.
                        // This mirrors the fallback in loadLibraryImpl (lines 292-303)
                        // and prevents the Library grid from appearing empty when
                        // switching category tabs.
                        entries.add(
                            LibraryEntry.fromExtension(
                                mainId = mainId,
                                title = content.title,
                                coverUrl = null,
                                sourceId = content.extensionId,
                                animeUrl = content.animeUrl,
                            ),
                        )
                    }
                }

                if (entries.isEmpty()) {
                    _state.value = LibraryState.Empty
                } else {
                    // D-242-fix10: enrich entries with badge data
                    enrichEntriesWithBadgeData(entries)
                    _state.value = LibraryState.Success(entries)
                    applyFilters()
                }
            } catch (e: Exception) {
                Logger.e(TAG, e) { "reloadFromCache failed: ${e.message}" }
            }
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
        _searchQuery.value = query
        applyFilters()
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
        contentRepository.deleteCategory(categoryId)
        _categoryToManage.value = null
        if (_selectedCategoryId.value == categoryId) {
            _selectedCategoryId.value = null
        }
        loadLibrary()
    }

    fun deleteCategoryAndMoveToDefault(categoryId: Long) {
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
        loadLibrary()
    }

    fun renameCategory(categoryId: Long, newName: String) {
        contentRepository.renameCategory(categoryId, newName)
        _categoryToManage.value = null
        loadLibrary()
    }

    fun createCategory(name: String) {
        contentRepository.createCategory(name)
        loadLibrary()
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
        _multiSelectCategoryMembership.value = getCategoriesForSelected()
            .filter { it.value }
            .map { it.key }
            .toSet()
        _showMultiSelectCategorySheet.value = true
    }

    fun dismissMultiSelectCategorySheet() {
        _showMultiSelectCategorySheet.value = false
    }

    /**
     * Add all selected entries to a category.
     * D-146: Does NOT close the sheet — user can select multiple categories.
     * The sheet is closed via dismissMultiSelectCategorySheet() (Done button).
     */
    fun addSelectedToCategory(categoryId: Long) {
        for (mainId in _selectedMainIds.value) {
            contentRepository.addToCategory(mainId, categoryId)
        }
        Logger.i(TAG) { "Added ${_selectedMainIds.value.size} entries to category $categoryId" }
        // Update the category membership state (for checkbox display).
        _multiSelectCategoryMembership.value = _multiSelectCategoryMembership.value + categoryId
    }

    /**
     * Remove all selected entries from a category.
     * D-146: Does NOT close the sheet — user can deselect multiple categories.
     */
    fun removeSelectedFromCategory(categoryId: Long) {
        for (mainId in _selectedMainIds.value) {
            contentRepository.removeFromCategory(mainId, categoryId)
        }
        Logger.i(TAG) { "Removed ${_selectedMainIds.value.size} entries from category $categoryId" }
        _multiSelectCategoryMembership.value = _multiSelectCategoryMembership.value - categoryId
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
     */
    fun deleteSelected() {
        for (mainId in _selectedMainIds.value) {
            contentRepository.removeFromLibrary(mainId)
        }
        Logger.i(TAG) { "Deleted ${_selectedMainIds.value.size} entries from library" }
        _showDeleteConfirmation.value = false
        exitSelectionMode()
        loadLibrary()
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
    fun setSortType(sort: LibrarySortType) {
        _sortType.value = sort
        preferenceStore.putString(KEY_SORT_TYPE, sort.name)
        applyFilters()
    }

    fun setSortAscending(value: Boolean) {
        _sortAscending.value = value
        preferenceStore.putBoolean(KEY_SORT_ASCENDING, value)
        applyFilters()
    }

    fun setSort(sort: LibrarySortType, ascending: Boolean) {
        _sortType.value = sort
        _sortAscending.value = ascending
        preferenceStore.putString(KEY_SORT_TYPE, sort.name)
        preferenceStore.putBoolean(KEY_SORT_ASCENDING, ascending)
        applyFilters()
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

    /**
     * D-242-fix10: Enriches LibraryEntry list with badge data:
     * - releasedEpisodes: count of cached episodes (actual aired count)
     * - audioAvailability: aggregated SUB/DUB/HSUB across all cached episodes
     * - watchedCount: how many episodes the user has watched
     *
     * D-242-fix14: Also counts per-audio-type episode counts (subEpisodeCount,
     * dubEpisodeCount) for the advanced RELEASED badge sub-options. Logs the
     * enrichment result for each entry at DEBUG level.
     */
    private suspend fun enrichEntriesWithBadgeData(entries: MutableList<LibraryEntry>) {
        for (i in entries.indices) {
            val entry = entries[i]
            try {
                val cachedEpisodes = dataCacheRepository.getEpisodeMetadata(entry.mainId)
                val releasedCount = cachedEpisodes.size.takeIf { it > 0 }

                // Aggregate audio availability across all episodes + count per type.
                var hasSub = false
                var hasDub = false
                var hasHsub = false
                var subCount = 0
                var dubCount = 0
                for (ep in cachedEpisodes) {
                    val audio = com.confused.anikuta.core.common.parseAudioAvailability(
                        ep.scanlator,
                        ep.sourceName ?: ep.title ?: "",
                    )
                    if (audio.hasSub) { hasSub = true; subCount++ }
                    if (audio.hasDub) { hasDub = true; dubCount++ }
                    if (audio.hasHsub) hasHsub = true
                }
                val audioAvail = if (hasSub || hasDub || hasHsub) {
                    com.confused.anikuta.core.common.AudioAvailability(hasSub, hasDub, hasHsub)
                } else null

                val watched = watchProgressStore?.getWatchedEpisodeCount(entry.mainId)?.takeIf { it > 0 }

                entries[i] = entry.copy(
                    releasedEpisodes = releasedCount,
                    audioAvailability = audioAvail,
                    watchedCount = watched,
                    subEpisodeCount = subCount.takeIf { it > 0 },
                    dubEpisodeCount = dubCount.takeIf { it > 0 },
                )

                Logger.d(TAG) {
                    "enrichEntriesWithBadgeData — ${entry.title}: " +
                    "released=$releasedCount, sub=$subCount, dub=$dubCount, " +
                    "watched=$watched, audio=$audioAvail"
                }
            } catch (e: Exception) {
                Logger.w(TAG) { "enrichEntriesWithBadgeData — failed for ${entry.mainId}: ${e.message}" }
            }
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

        // D-242-fix3: restore last-selected category across app restarts.
        // -1L sentinel = "All" (null selection).
        val savedCatId = preferenceStore.getLong(KEY_SELECTED_CATEGORY, -1L)
        _selectedCategoryId.value = if (savedCatId == -1L) null else savedCatId
    }

    private fun applyFilters() {
        val current = _state.value
        if (current !is LibraryState.Success) return

        var filtered = current.entries

        val query = _searchQuery.value
        if (query.isNotBlank()) {
            filtered = filtered.filter { it.title.contains(query, ignoreCase = true) }
        }

        filtered = when (_sortType.value) {
            LibrarySortType.TITLE -> if (_sortAscending.value) {
                filtered.sortedBy { it.title.lowercase() }
            } else {
                filtered.sortedByDescending { it.title.lowercase() }
            }
            LibrarySortType.SCORE -> if (_sortAscending.value) {
                filtered.sortedBy { it.averageScore ?: 0 }
            } else {
                filtered.sortedByDescending { it.averageScore ?: 0 }
            }
            LibrarySortType.DATE_ADDED -> if (_sortAscending.value) {
                filtered.asReversed()
            } else {
                filtered
            }
            LibrarySortType.LAST_WATCHED -> filtered
        }

        _state.value = LibraryState.Success(filtered)
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
 * D-242-fix19: List mode density options.
 *
 * Controls the size of list entries in LIST display mode. Text size also
 * scales with density (bigger density = bigger text).
 * - COMPACT: 48×68dp cover, 12sp text.
 * - NORMAL: 60×86dp cover, 14sp text.
 * - COMFORTABLE: 80×115dp cover, 16sp text.
 */
enum class ListDensity(val coverWidth: Int, val coverHeight: Int, val titleFontSize: Int, val displayName: String) {
    COMPACT(48, 68, 12, "Compact"),
    NORMAL(60, 86, 14, "Normal"),
    COMFORTABLE(80, 115, 16, "Comfortable"),
}

/**
 * D-242-fix18: Where to show the title in list mode.
 */
enum class ListTitlePosition(val displayName: String) {
    TOP("Top"),
    BOTTOM("Bottom"),
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
