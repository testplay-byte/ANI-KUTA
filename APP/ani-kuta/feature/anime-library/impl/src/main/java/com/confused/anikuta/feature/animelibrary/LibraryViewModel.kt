package com.confused.anikuta.feature.animelibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.anilist.api.AniListApi
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.common.Logger
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
 * - Grid/list view toggle
 * - Sort by title, score, date added
 * - Search within library
 * - Category tabs (future — needs identity system)
 *
 * CORE_RULES §20: Logged with tag "Anikuta:Feature:Library".
 * CORE_RULES §23: Reactive state (StateFlow).
 */
class LibraryViewModel(
    private val anilistApi: AniListApi,
    private val preferenceStore: com.confused.anikuta.core.preferences.PreferenceStore,
) : ViewModel() {

    companion object {
        private const val TAG = "Anikuta:Feature:Library"
        private const val KEY_LIBRARY_IDS = "library_anilist_ids"
    }

    private val _state = MutableStateFlow<LibraryState>(LibraryState.Loading)
    val state: StateFlow<LibraryState> = _state

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _sortType = MutableStateFlow(LibrarySortType.TITLE)
    val sortType: StateFlow<LibrarySortType> = _sortType

    private val _isGridMode = MutableStateFlow(true)
    val isGridMode: StateFlow<Boolean> = _isGridMode

    init {
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
        val ids = if (idsStr.isBlank()) emptyList() else idsStr.split(",").mapNotNull { it.trim().toIntOrNull() }
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

    fun setSortType(sort: LibrarySortType) {
        _sortType.value = sort
        applyFilters()
    }

    fun toggleViewMode() {
        _isGridMode.value = !_isGridMode.value
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

        // Apply sort
        filtered = when (_sortType.value) {
            LibrarySortType.TITLE -> filtered.sortedBy { it.displayName.lowercase() }
            LibrarySortType.SCORE -> filtered.sortedByDescending { it.averageScore ?: 0 }
            LibrarySortType.DATE_ADDED -> filtered // Keep original order (date added)
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
}
