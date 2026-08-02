package com.confused.anikuta.feature.animedetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.anilist.api.AniListApi
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.common.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DetailsViewModel(
    private val anilistApi: AniListApi,
) : ViewModel() {

    private val _state = MutableStateFlow<DetailsState>(DetailsState.Loading)
    val state: StateFlow<DetailsState> = _state

    fun loadDetails(animeId: Int) {
        _state.value = DetailsState.Loading
        viewModelScope.launch {
            try {
                val anime = anilistApi.fetchAnimeDetails(animeId)
                Logger.i("Anikuta:Feature:Details") { "Loaded details for $animeId" }
                _state.value = DetailsState.Success(anime)
            } catch (e: Exception) {
                Logger.e("Anikuta:Feature:Details", e) { "Failed: ${e.message}" }
                _state.value = DetailsState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

sealed interface DetailsState {
    data object Loading : DetailsState
    data class Success(val anime: AniListAnime) : DetailsState
    data class Error(val message: String) : DetailsState
}
