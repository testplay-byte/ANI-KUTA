package com.confused.anikuta.feature.animelibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.anilist.api.AniListApi
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.preferences.PreferenceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Library screen.
 *
 * Phase 4: For now, the library shows anime the user has "added" (stored as a
 * comma-separated list of AniList IDs in PreferenceStore). Phase 5 will replace
 * this with the proper identity system + SQLDelight library_entry table.
 *
 * The library supports:
 * - Display modes: Compact / Comfortable / Cover Only / List grid.
 * - Configurable columns per row (2-5).
 * - Configurable title lines (1-3).
 * - Episode badges (off / released / total) with position.
 * - Score badge toggle + position.
 * - Continue-watching + total-entries toggles.
 * - Sort by title / score / date added / last watched (asc/desc).
 * - Search within library.
 *
 * CORE_RULES §20: Logged with tag "Anikuta:Feature:Library".
 * CORE_RULES §23: Reactive state (StateFlow).
 */
class LibraryViewModel(
    private val anilistApi: AniListApi,
    private val preferenceStore: PreferenceStore,
) : ViewModel() {

    companion object {
        private const val TAG = "Anikuta:Feature:Library"

        // Library IDs (existing Phase 4 approach)
        private const val KEY_LIBRARY_IDS = "library_anilist_ids"

        // Customize-sheet preferences (per old project's CustomizeSheet).
        private const val KEY_DISPLAY_MODE = "library_display_mode"
        private const val KEY_COLUMNS = "library_columns"
        private const val KEY_TITLE_LINES = "library_title_lines"
        private const val KEY_EPISODE_BADGE_MODE = "library_episode_badge_mode"
        private const val KEY_EPISODE_BADGE_POS = "library_episode_badge_pos"
        private const val KEY_SHOW_SCORE_BADGE = "library_show_score_badge"
        private const val KEY_SCORE_BADGE_POS = "library_score_badge_pos"
        private const val KEY_SHOW_CONTINUE_WATCHING = "library_show_continue_watching"
        private const val KEY_SHOW_TOTAL_ENTRIES = "library_show_total_entries"
        private const val KEY_SORT_TYPE = "library_sort_type"
        private const val KEY_SORT_ASCENDING = "library_sort_ascending"
    }

    private val _state = MutableStateFlow<LibraryState>(LibraryState.Loading)
    val state: StateFlow<LibraryState> = _state

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // ── Sort ──────────────────────────────────────────────────────────────
    private val _sortType = MutableStateFlow(LibrarySortType.TITLE)
    val sortType: StateFlow<LibrarySortType> = _sortType

    private val _sortAscending = MutableStateFlow(true)
    val sortAscending: StateFlow<Boolean> = _sortAscending

    // ── Display & badges ──────────────────────────────────────────────────
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

    init {
        loadPreferences()
        loadLibrary()
    }

    /**
     * Load the user's library anime from AniList.
     * Reads the stored AniList IDs, fetches their details.
     */
    fun loadLibrary() {
        _state.value = LibraryState.Loading
        viewModelScope.launch {
            try {
                val idsStr = preferenceStore.getString(KEY_LIBRARY_IDS, "")
                if (idsStr.isBlank()) {
                    Logger.i(TAG) { "Library is empty" }
                    _state.value = LibraryState.Empty
                    return@launch
                }

                val ids = idsStr.split(",").mapNotNull { it.trim().toIntOrNull() }
                Logger.i(TAG) { "Loading ${ids.size} library anime" }

                val animeList = mutableListOf<AniListAnime>()
                for (id in ids) {
                    try {
                        animeList.add(anilistApi.fetchAnimeDetails(id))
                    } catch (e: Exception) {
                        Logger.w(TAG) { "Failed to load anime $id: ${e.message}" }
                    }
                }

                if (animeList.isEmpty()) {
                    _state.value = LibraryState.Empty
                } else {
                    _state.value = LibraryState.Success(animeList)
                    applyFilters()
                }
            } catch (e: Exception) {
                Logger.e(TAG, e) { "Failed to load library: ${e.message}" }
                _state.value = LibraryState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Add an anime to the library.
     */
    fun addToLibrary(anilistId: Int) {
        Logger.i(TAG) { "Adding to library: $anilistId" }
        val idsStr = preferenceStore.getString(KEY_LIBRARY_IDS, "")
        val ids = if (idsStr.isBlank()) emptyList()
                  else idsStr.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (anilistId !in ids) {
            val newIds = (ids + anilistId).joinToString(",")
            preferenceStore.putString(KEY_LIBRARY_IDS, newIds)
            Logger.i(TAG) { "Added. Library now has ${ids.size + 1} items" }
            loadLibrary()
        }
    }

    /**
     * Remove an anime from the library.
     */
    fun removeFromLibrary(anilistId: Int) {
        Logger.i(TAG) { "Removing from library: $anilistId" }
        val idsStr = preferenceStore.getString(KEY_LIBRARY_IDS, "")
        val ids = idsStr.split(",").mapNotNull { it.trim().toIntOrNull() }
        val newIds = ids.filter { it != anilistId }.joinToString(",")
        preferenceStore.putString(KEY_LIBRARY_IDS, newIds)
        Logger.i(TAG) { "Removed. Library now has ${ids.size - 1} items" }
        loadLibrary()
    }

    /**
     * Check if an anime is in the library.
     */
    fun isInLibrary(anilistId: Int): Boolean {
        val idsStr = preferenceStore.getString(KEY_LIBRARY_IDS, "")
        val ids = idsStr.split(",").mapNotNull { it.trim().toIntOrNull() }
        return anilistId in ids
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        applyFilters()
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

    /**
     * Combined setter used by the CustomizeSheet's sort callbacks.
     */
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
    }

    private fun applyFilters() {
        val current = _state.value
        if (current !is LibraryState.Success) return

        var filtered = current.anime

        // Apply search
        val query = _searchQuery.value
        if (query.isNotBlank()) {
            filtered = filtered.filter { it.displayName.contains(query, ignoreCase = true) }
        }

        // Apply sort (respect ascending/descending)
        filtered = when (_sortType.value) {
            LibrarySortType.TITLE -> if (_sortAscending.value) {
                filtered.sortedBy { it.displayName.lowercase() }
            } else {
                filtered.sortedByDescending { it.displayName.lowercase() }
            }
            LibrarySortType.SCORE -> if (_sortAscending.value) {
                filtered.sortedBy { it.averageScore ?: 0 }
            } else {
                filtered.sortedByDescending { it.averageScore ?: 0 }
            }
            LibrarySortType.DATE_ADDED -> if (_sortAscending.value) {
                filtered.asReversed() // oldest first
            } else {
                filtered // keep insertion order (newest first)
            }
            LibrarySortType.LAST_WATCHED -> filtered
            // ponytail: no watch history yet — keep order. Phase 5 wiring.
        }

        _state.value = LibraryState.Success(filtered)
    }
}

sealed interface LibraryState {
    data object Loading : LibraryState
    data object Empty : LibraryState
    data class Success(val anime: List<AniListAnime>) : LibraryState
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
