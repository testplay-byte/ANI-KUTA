package com.confused.anikuta.feature.animesearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.data.cloudstream.content.CloudstreamContentRepository
import com.confused.anikuta.data.cloudstream.content.CsContentCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Task 61 (round 21 — the category subpages): the ViewModel behind
 * [CsCategoryScreen] — ONE CloudStream provider shelf as a paginated grid.
 *
 * Mirrors the search ViewModel's paging discipline (the approach-bottom
 * load-more + dedupe guards + soft-fail pagination) at subpage scale:
 *  - [load] fetches page 1 of the shelf (idempotent per (provider, shelf) —
 *    recomposition can call it freely);
 *  - [loadMore] appends page+1 while the shelf's `hasNext` holds;
 *  - failures THROW from the repository (the honest-error contract) and land
 *    as [CsCategoryUiState.Error] with the friendly CloudStream message form.
 */
class CsCategoryViewModel(
    private val repository: CloudstreamContentRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "Anikuta:Feature:Search:CsCategory"
    }

    private val _state = MutableStateFlow<CsCategoryUiState>(CsCategoryUiState.Loading)
    val state: StateFlow<CsCategoryUiState> = _state.asStateFlow()

    /** The loaded (provider, shelf) identity — the load() idempotence key. */
    private var loadedProvider: String? = null
    private var loadedShelfIndex = -1

    /** The last page of the current content (loadMore fetches page+1). */
    private var loadedPage = 0

    private var loadJob: Job? = null

    /**
     * Loads page 1 of the shelf. Idempotent ONLY for a successful load — a
     * re-call with the SAME (provider, shelf) while Content is showing is a
     * no-op (the screen's LaunchedEffect re-fires on recomposition); the
     * Error/Empty prompt cards' Retry actions re-call this and MUST reload.
     */
    fun load(providerName: String, shelfIndex: Int) {
        if (loadedProvider == providerName && loadedShelfIndex == shelfIndex &&
            _state.value is CsCategoryUiState.Content
        ) {
            return
        }
        loadedProvider = providerName
        loadedShelfIndex = shelfIndex
        loadJob?.cancel()
        _state.value = CsCategoryUiState.Loading
        loadJob = viewModelScope.launch {
            try {
                Logger.i(TAG) { "loading category: $providerName shelf #$shelfIndex" }
                val page = repository.browseShelf(providerName, shelfIndex, 1)
                loadedPage = 1
                _state.value = if (page.items.isEmpty()) {
                    CsCategoryUiState.Empty
                } else {
                    CsCategoryUiState.Content(
                        items = page.items.map { it.toExtensionAnime(providerName) },
                        hasMore = page.hasNext,
                        loadingMore = false,
                    )
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Logger.e(TAG, e) { "category load failed for $providerName shelf #$shelfIndex" }
                _state.value = CsCategoryUiState.Error(errorMessage(e))
            }
        }
    }

    /**
     * The approach-bottom load-more (the same contract as the search page):
     * appends page+1, dedupes by the grid's key identity, soft-fails (the
     * footer disappears; the next trigger retries).
     */
    fun loadMore() {
        val state = _state.value as? CsCategoryUiState.Content ?: return
        if (state.loadingMore || !state.hasMore) return
        val providerName = loadedProvider ?: return
        val shelfIndex = loadedShelfIndex
        val nextPage = loadedPage + 1
        _state.value = state.copy(loadingMore = true)
        loadJob = viewModelScope.launch {
            try {
                val page = repository.browseShelf(providerName, shelfIndex, nextPage)
                if (page.items.isEmpty()) {
                    _state.value = (state).copy(loadingMore = false, hasMore = false)
                    return@launch
                }
                loadedPage = nextPage
                val keyOf: (ExtensionAnime) -> String =
                    { "${it.sourceKey ?: it.sourceId}:${it.url}" }
                val existingKeys = state.items.mapTo(mutableSetOf()) { keyOf(it) }
                val merged = state.items +
                    page.items.map { it.toExtensionAnime(providerName) }
                        .filter { keyOf(it) !in existingKeys }
                _state.value = state.copy(items = merged, loadingMore = false, hasMore = page.hasNext)
                Logger.i(TAG) {
                    "loadMore (category $providerName shelf #$shelfIndex page=$nextPage) — " +
                        "+${merged.size - state.items.size} new"
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Logger.w(TAG, e) { "category loadMore failed — footer dismissed; next trigger retries" }
                _state.value = state.copy(loadingMore = false)
            }
        }
    }

    /** The friendly CloudStream error form (mirrors the search ViewModel). */
    private fun errorMessage(e: Throwable): String =
        (e as? com.lagradost.cloudstream3.network.CloudflareBlockedException)?.userMessage
            ?: "${e::class.java.simpleName}: ${e.message ?: "Unknown error"}"

    /**
     * CsContentCard → the shared results-grid model (the same mapping the
     * search page uses — the tapped card opens the standard details screen
     * via AnimeDetailsKey.Extension).
     */
    private fun CsContentCard.toExtensionAnime(providerName: String) = ExtensionAnime(
        sourceId = com.confused.anikuta.data.cloudstream.content.CsSourceIds.idFor(providerName),
        sourceName = providerName,
        url = url,
        title = name,
        thumbnailUrl = posterUrl,
        sourceKey = "cloudstream:$providerName",
        year = year,
    )
}

/** The category subpage's UI state. */
sealed interface CsCategoryUiState {
    data object Loading : CsCategoryUiState
    data object Empty : CsCategoryUiState
    data class Error(val message: String) : CsCategoryUiState
    data class Content(
        val items: List<ExtensionAnime>,
        val hasMore: Boolean,
        val loadingMore: Boolean,
    ) : CsCategoryUiState
}
