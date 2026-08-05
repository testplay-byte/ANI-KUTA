package com.confused.anikuta.feature.animebrowse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.anilist.api.AniListApi
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.common.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BrowseViewModel(
    private val anilistApi: AniListApi,
) : ViewModel() {

    private val _state = MutableStateFlow<BrowseState>(BrowseState.Loading)
    val state: StateFlow<BrowseState> = _state

    init {
        loadTrending()
    }

    fun loadTrending() {
        _state.value = BrowseState.Loading
        viewModelScope.launch {
            try {
                val anime = anilistApi.fetchTrending()
                Logger.i("Anikuta:Feature:Browse") { "Loaded ${anime.size} trending anime" }
                _state.value = BrowseState.Success(anime)
            } catch (e: Exception) {
                Logger.e("Anikuta:Feature:Browse", e) { "Failed to load trending: ${e.message}" }
                _state.value = BrowseState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

sealed interface BrowseState {
    data object Loading : BrowseState
    data class Success(val anime: List<AniListAnime>) : BrowseState
    data class Error(val message: String) : BrowseState
}
