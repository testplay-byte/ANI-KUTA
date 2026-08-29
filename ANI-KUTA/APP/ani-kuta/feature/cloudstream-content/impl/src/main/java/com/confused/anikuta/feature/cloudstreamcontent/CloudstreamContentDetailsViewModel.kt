package com.confused.anikuta.feature.cloudstreamcontent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.data.cloudstream.content.CloudstreamContentRepository
import com.confused.anikuta.data.cloudstream.content.CsContentDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the CloudStream content details screen (session 3, provider
 * execution phase 1).
 *
 * Loads ONE content entry through [CloudstreamContentRepository.load] (the
 * provider's MainAPI.load) and exposes a sealed UI state. Retry re-runs the
 * same load — the (providerName, contentUrl) pair is immutable in the nav key,
 * so a config change or process death simply re-loads (CORE_RULES §30 debug
 * builds have no persisted details cache yet — that lands with the data-layer
 * session, doc 17).
 *
 * CORE_RULES §20: logged with tag "Anikuta:Feature:CloudContent".
 * CORE_RULES §23: reactive state (StateFlow).
 */
class CloudstreamContentDetailsViewModel(
    private val repository: CloudstreamContentRepository,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Success(val details: CsContentDetails) : UiState
        data class Error(val message: String) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /** Loads (or re-loads) the content; safe to call repeatedly — one job at a time. */
    fun load(providerName: String, contentUrl: String) {
        if (loadJob?.isActive == true) return
        _uiState.value = UiState.Loading
        loadJob = viewModelScope.launch {
            try {
                Logger.i(TAG) { "Opening content from '$providerName': $contentUrl" }
                val details = repository.load(providerName, contentUrl)
                _uiState.value = UiState.Success(details)
            } catch (e: Throwable) {
                // Cancellation must propagate (screen left mid-load).
                if (e is CancellationException) throw e
                // Catch Throwable — plugin bytecode can throw NoClassDefFoundError etc.
                // D-295/D-296: the real reason, never silent.
                val message = "${e::class.java.simpleName}: ${e.message ?: "Unknown error"}"
                Logger.e(TAG, e) { "Content load failed for '$providerName': $message" }
                _uiState.value = UiState.Error(message)
            }
        }
    }

    fun retry(providerName: String, contentUrl: String) {
        loadJob?.cancel()
        loadJob = null
        load(providerName, contentUrl)
    }

    companion object {
        private const val TAG = "Anikuta:Feature:CloudContent"
    }
}
