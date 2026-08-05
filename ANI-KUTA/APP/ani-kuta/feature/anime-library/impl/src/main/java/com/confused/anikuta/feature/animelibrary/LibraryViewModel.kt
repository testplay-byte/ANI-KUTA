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
 * ViewModel for the Library screen (Phase C).
 *
 * Uses the content ID system (Phase C) instead of the old PreferenceStore
 * comma-separated IDs. Library items are stored in the `library_item` table
 * linked to the `content` table via `mainId`.
 *
 * ## Categories
 * For now, there is only ONE category: "Default" (permanent, cannot be deleted).
 * The Default category only shows when it has at least 1 item.
 * Future phases will add user-created categories.
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

        // Customize-sheet preferences (kept from Phase 4).
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
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    /** The library categories. For now, only "Default" exists. */
    private val _categories = MutableStateFlow<List<LibraryCategory>>(emptyList())
    val categories: StateFlow<List<LibraryCategory>> = _categories.asStateFlow()

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

    init {
        loadPreferences()
        loadLibrary()
    }

    /**
     * Load the library from the content ID system.
     *
     * 1. Get all library mainIds from [ContentRepository].
     * 2. Fetch each content record to get the title + display info.
     * 3. For content with anilistId, fetch fresh AniList data for the grid display.
     * 4. For extension-only content, use the stored title + cover (if available).
     */
    fun loadLibrary() {
        _state.value = LibraryState.Loading
        viewModelScope.launch {
            try {
                // Load categories (Default only for now).
                val cats = listOfNotNull(contentRepository.getDefaultCategory())
                _categories.value = cats

                // Get all library mainIds.
                val mainIds = contentRepository.getLibraryMainIds()
                Logger.i(TAG) { "Library has ${mainIds.size} items" }

                if (mainIds.isEmpty()) {
                    _state.value = LibraryState.Empty
                    return@launch
                }

                // Fetch content records + AniList data for display.
                val animeList = mutableListOf<AniListAnime>()
                for (mainId in mainIds) {
                    val content = contentRepository.getContentByMainId(mainId)
                    if (content == null) continue

                    // Try to get AniList detail for rich display.
                    val anilistDetail = contentRepository.getAniListDetail(mainId)
                    if (anilistDetail != null) {
                        try {
                            // Fetch fresh AniList data for the grid (cover, score, etc.)
                            animeList.add(anilistApi.fetchAnimeDetails(anilistDetail.anilistId))
                        } catch (e: Exception) {
                            Logger.w(TAG) { "Failed to fetch AniList ${anilistDetail.anilistId}: ${e.message}" }
                            // Fall back to a minimal entry from stored data.
                            animeList.add(
                                AniListAnime(
                                    id = anilistDetail.anilistId,
                                    title = com.confused.anikuta.core.anilist.model.AnimeTitle(
                                        romaji = content.title,
                                        english = content.title,
                                    ),
                                    coverImage = com.confused.anikuta.core.anilist.model.CoverImage(
                                        large = anilistDetail.coverUrl,
                                        extraLarge = anilistDetail.coverUrl,
                                    ),
                                    averageScore = anilistDetail.score,
                                    episodes = anilistDetail.episodes,
                                    seasonYear = anilistDetail.seasonYear,
                                ),
                            )
                        }
                    } else {
                        // Extension-only content — no AniList data. Create a minimal entry.
                        val extDetail = contentRepository.getExtensionDetail(mainId)
                        animeList.add(
                            AniListAnime(
                                id = 0, // No AniList ID.
                                title = com.confused.anikuta.core.anilist.model.AnimeTitle(
                                    romaji = content.title,
                                    english = content.title,
                                ),
                                coverImage = com.confused.anikuta.core.anilist.model.CoverImage(
                                    large = extDetail?.thumbnailUrl,
                                    extraLarge = extDetail?.thumbnailUrl,
                                ),
                            ),
                        )
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

        val query = _searchQuery.value
        if (query.isNotBlank()) {
            filtered = filtered.filter { it.displayName.contains(query, ignoreCase = true) }
        }

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
