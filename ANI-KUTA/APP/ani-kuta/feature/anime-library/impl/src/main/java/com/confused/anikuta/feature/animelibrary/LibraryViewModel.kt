package com.confused.anikuta.feature.animelibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.anilist.api.AniListApi
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.content.LibraryCategory
import com.confused.anikuta.core.preferences.PreferenceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Library screen (Phase C, D-140).
 *
 * Uses [LibraryEntry] (with mainId as the key) instead of AniListAnime.
 * This prevents the "Key 0 already used" crash when multiple extension-only
 * entries exist (all had anilistId=0).
 *
 * Also handles:
 * - Category filtering (select a category tab).
 * - Category management (create, delete with move-to-default, rename).
 * - Live reload (called from LibraryScreen on resume).
 *
 * CORE_RULES §20: Logged with tag "Anikuta:Feature:Library".
 * CORE_RULES §23: Reactive state (StateFlow).
 */
class LibraryViewModel(
    private val anilistApi: AniListApi,
    private val contentRepository: ContentRepository,
    private val preferenceStore: PreferenceStore,
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
    }

    private val _state = MutableStateFlow<LibraryState>(LibraryState.Loading)
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    /** All library categories. */
    private val _categories = MutableStateFlow<List<LibraryCategory>>(emptyList())
    val categories: StateFlow<List<LibraryCategory>> = _categories.asStateFlow()

    /** Item counts per category (for showing counts on tabs). */
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

    private val _titleLines = MutableStateFlow(2)
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

    /** D-140: Show item count next to each category tab. */
    private val _showCategoryCounts = MutableStateFlow(false)
    val showCategoryCounts: StateFlow<Boolean> = _showCategoryCounts

    init {
        loadPreferences()
        loadLibrary()
    }

    /**
     * Load the library from the content ID system.
     * D-140: Uses LibraryEntry (mainId as key) instead of AniListAnime.
     */
    fun loadLibrary() {
        _state.value = LibraryState.Loading
        viewModelScope.launch {
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
                _totalEntries.value = uniqueMainIds.size
                Logger.i(TAG) { "Library: ${uniqueMainIds.size} items (category=${_selectedCategoryId.value ?: "all"})" }

                if (uniqueMainIds.isEmpty()) {
                    _state.value = LibraryState.Empty
                    return@launch
                }

                // Build LibraryEntry for each content.
                val entries = mutableListOf<LibraryEntry>()
                for (mainId in uniqueMainIds) {
                    val content = contentRepository.getContentByMainId(mainId) ?: continue

                    // Try AniList detail first (for rich display data).
                    val anilistDetail = contentRepository.getAniListDetail(mainId)
                    if (anilistDetail != null) {
                        // Fetch fresh AniList data for the grid (cover, score, etc.).
                        try {
                            val anime = anilistApi.fetchAnimeDetails(anilistDetail.anilistId)
                            entries.add(
                                LibraryEntry.fromAniList(
                                    mainId = mainId,
                                    anime = anime,
                                    sourceId = content.extensionId,
                                    animeUrl = content.animeUrl,
                                ),
                            )
                        } catch (e: Exception) {
                            Logger.w(TAG) { "AniList fetch failed for ${anilistDetail.anilistId}: ${e.message}" }
                            // Fall back to stored data.
                            entries.add(
                                LibraryEntry(
                                    mainId = mainId,
                                    anilistId = anilistDetail.anilistId,
                                    sourceId = content.extensionId,
                                    animeUrl = content.animeUrl,
                                    title = content.title,
                                    coverUrl = anilistDetail.coverUrl,
                                    averageScore = anilistDetail.score,
                                    episodes = anilistDetail.episodes,
                                    seasonYear = anilistDetail.seasonYear,
                                    status = anilistDetail.status,
                                ),
                            )
                        }
                    } else {
                        // Extension-only content — use stored data.
                        val extDetail = contentRepository.getExtensionDetail(mainId)
                        entries.add(
                            LibraryEntry.fromExtension(
                                mainId = mainId,
                                title = content.title,
                                coverUrl = extDetail?.thumbnailUrl ?: content.description?.let { null },
                                sourceId = content.extensionId ?: extDetail?.sourceId,
                                animeUrl = content.animeUrl ?: extDetail?.animeUrl,
                            ),
                        )
                    }
                }

                if (entries.isEmpty()) {
                    _state.value = LibraryState.Empty
                } else {
                    _state.value = LibraryState.Success(entries)
                    applyFilters()
                }
            } catch (e: Exception) {
                Logger.e(TAG, e) { "Failed to load library: ${e.message}" }
                _state.value = LibraryState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        applyFilters()
    }

    // ── Category management (D-138, D-140) ──

    fun selectCategory(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
        loadLibrary()
    }

    fun showCategoryManagement(category: LibraryCategory) {
        _categoryToManage.value = category
    }

    fun dismissCategoryManagement() {
        _categoryToManage.value = null
    }

    /**
     * Delete a category. Only non-permanent categories can be deleted.
     * Items in the category are deleted too (CASCADE).
     */
    fun deleteCategory(categoryId: Long) {
        contentRepository.deleteCategory(categoryId)
        _categoryToManage.value = null
        if (_selectedCategoryId.value == categoryId) {
            _selectedCategoryId.value = null
        }
        loadLibrary()
    }

    /**
     * D-140: Delete a category + move its items to the Default category.
     * The items are NOT removed from the library — just moved.
     */
    fun deleteCategoryAndMoveToDefault(categoryId: Long) {
        val defaultCat = contentRepository.getDefaultCategory()
        if (defaultCat != null) {
            // Move items to Default.
            val mainIds = contentRepository.getMainIdsByCategory(categoryId)
            for (mainId in mainIds) {
                contentRepository.addToCategory(mainId, defaultCat.id)
            }
        }
        // Delete the category (items in it are CASCADE deleted, but they're already moved).
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
        _titleLines.value = preferenceStore.getInt(KEY_TITLE_LINES, 2).coerceIn(1, 3)

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

enum class BadgePosition {
    TOP_START,
    TOP_END,
    BOTTOM_START,
    BOTTOM_END,
}
