package com.confused.anikuta.feature.animebrowse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.anilist.api.AniListApi
import com.confused.anikuta.core.anilist.api.BrowseCacheCodec
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.datacache.DataCacheRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the Browse screen (D-249 multi-section redesign).
 *
 * Local-first: reads from browse_cache first → displays instantly.
 * Sections: Trending (with banners for the hero), Popular, Top Rated.
 * Each section cached independently (6h TTL) in the multi-section browse_cache table.
 *
 * D-278: browse-cache JSON serialize/parse moved to [BrowseCacheCodec] in
 * `:core:anilist` so the Search feature can reuse it (Search now serves the
 * cached trending payload as its offline default — "search shows default
 * results without internet").
 *
 * D-279: partial-success offline — when trending fails on a cold start but
 * popular/topRated cache loaded, surface Success(empty) instead of Error so
 * the user sees the cached sections. Hero falls back to popular when trending
 * is empty (so the hero still renders offline with whatever cache exists).
 *
 * CORE_RULES §20: Logged with tag "Anikuta:Feature:Browse".
 * CORE_RULES §23: Reactive state (StateFlow).
 */
class BrowseViewModel(
    private val anilistApi: AniListApi,
    private val dataCacheRepository: DataCacheRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "Anikuta:Feature:Browse"
        private const val SECTION_TRENDING = BrowseCacheCodec.SECTION_TRENDING
        private const val SECTION_POPULAR = BrowseCacheCodec.SECTION_POPULAR
        private const val SECTION_TOP_RATED = BrowseCacheCodec.SECTION_TOP_RATED
    }

    private val _state = MutableStateFlow<BrowseState>(BrowseState.Loading)
    val state: StateFlow<BrowseState> = _state.asStateFlow()

    /** Whether a background refresh is in progress (shows a subtle indicator). */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** D-249: Popular + Top Rated sections (fetched independently, cached independently). */
    private val _popular = MutableStateFlow<List<AniListAnime>>(emptyList())
    val popular: StateFlow<List<AniListAnime>> = _popular.asStateFlow()

    private val _topRated = MutableStateFlow<List<AniListAnime>>(emptyList())
    val topRated: StateFlow<List<AniListAnime>> = _topRated.asStateFlow()

    /**
     * D-253 / D-279: the hero pager's items — up to 5 trending anime WITH
     * banner images (falls back to the first 3 trending items so a hero
     * always renders). D-279: when trending is empty (e.g. cold start +
     * trending cache miss, but popular cache hit), the hero falls back to
     * popular so the hero still renders with whatever cache exists.
     */
    val heroItems: StateFlow<List<AniListAnime>> =
        combine(_state, _popular) { state, popular ->
            val trending = (state as? BrowseState.Success)?.anime ?: emptyList()
            // D-279: prefer trending; fall back to popular so the hero renders
            // offline even when only popular was cached.
            val heroSource = trending.ifEmpty { popular }
            heroSource.filter { !it.bannerImage.isNullOrBlank() }.take(5)
                .ifEmpty { heroSource.take(3) }
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadAll()
    }

    /** Loads all sections (trending + popular + top-rated), cache-first. */
    private fun loadAll() {
        loadSection(SECTION_TRENDING, "TRENDING_DESC") { anime ->
            _state.value = BrowseState.Success(anime)
        }
        loadSection(SECTION_POPULAR, "POPULARITY_DESC") { anime ->
            _popular.value = anime
        }
        loadSection(SECTION_TOP_RATED, "SCORE_DESC") { anime ->
            _topRated.value = anime
        }
    }

    /**
     * Loads a section: cache-first (instant display) → fetch from network when expired.
     * D-249: uses fetchBrowseSection (includes bannerImage + genres for the hero).
     * D-253: cache reads + JSON parsing moved to Dispatchers.IO (they're sync
     * SQL/JSON work that previously ran on the Main dispatcher).
     * D-278: JSON parsing via [BrowseCacheCodec.decode] (shared with Search).
     */
    private fun loadSection(sectionKey: String, sort: String, onResult: (List<AniListAnime>) -> Unit) {
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) {
                dataCacheRepository.getBrowseCache(sectionKey)
            }
            if (cached != null) {
                // D-278: parse via the shared codec. A corrupt row returns
                // empty (codec throws → caught → empty) — same graceful
                // degradation as before, just centralized.
                val cachedAnime = withContext(Dispatchers.IO) {
                    try {
                        BrowseCacheCodec.decode(cached.dataJson)
                    } catch (e: Exception) {
                        Logger.w(TAG) { "Failed to parse $sectionKey cache: ${e.message}" }
                        emptyList()
                    }
                }
                if (cachedAnime.isNotEmpty()) {
                    Logger.i(TAG) { "Loaded ${cachedAnime.size} $sectionKey from cache" }
                    onResult(cachedAnime)
                }
            }
            if (cached == null || dataCacheRepository.isBrowseCacheExpired(sectionKey)) {
                fetchSection(sectionKey, sort, onResult)
            }
        }
    }

    /**
     * In-flight network fetch counter. viewModelScope launches on Main, so a
     * plain Int is safe (single-threaded). D-253: fixes the isRefreshing race
     * where the first of 3 parallel fetches cleared the spinner while the
     * others were still in flight.
     */
    private var inFlightFetches = 0

    private suspend fun fetchSection(sectionKey: String, sort: String, onResult: (List<AniListAnime>) -> Unit) {
        inFlightFetches++
        _isRefreshing.value = true
        try {
            val anime = anilistApi.fetchBrowseSection(sort)
            Logger.i(TAG) { "Fetched ${anime.size} $sectionKey from network" }
            onResult(anime)
            // D-278: serialize via the shared codec (so Search can read it).
            val json = BrowseCacheCodec.encode(anime)
            withContext(Dispatchers.IO) {
                dataCacheRepository.upsertBrowseCache(sectionKey, json)
            }
            Logger.i(TAG) { "Cached ${anime.size} $sectionKey anime" }
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Failed to fetch $sectionKey: ${e.message}" }
            // D-279: partial-success offline. When trending fails on a cold
            // start (no cache yet → state is still Loading) AND at least one
            // of popular/topRated DID load from cache, surface Success(empty)
            // so the Browse screen renders those sections instead of a hard
            // Error. The hero (which derives from trending, with a popular
            // fallback per heroItems) will show popular items. Falls to Error
            // only when ALL three sections have no cache (true cold start +
            // no network).
            if (_state.value !is BrowseState.Success && sectionKey == SECTION_TRENDING) {
                if (_popular.value.isEmpty() && _topRated.value.isEmpty()) {
                    _state.value = BrowseState.Error(e.message ?: "Unknown error")
                } else {
                    Logger.i(TAG) { "Trending fetch failed but popular/topRated cached → partial Success" }
                    _state.value = BrowseState.Success(emptyList())
                }
            }
        } finally {
            inFlightFetches--
            if (inFlightFetches <= 0) {
                inFlightFetches = 0
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Force-refresh all sections from network. Called by pull-to-refresh + the
     * error-state Retry button. D-253: the three fetches run in PARALLEL (the
     * old sequential loop made refresh 3× slower than the parallel init path).
     */
    fun refresh() {
        viewModelScope.launch { fetchSection(SECTION_TRENDING, "TRENDING_DESC") { _state.value = BrowseState.Success(it) } }
        viewModelScope.launch { fetchSection(SECTION_POPULAR, "POPULARITY_DESC") { _popular.value = it } }
        viewModelScope.launch { fetchSection(SECTION_TOP_RATED, "SCORE_DESC") { _topRated.value = it } }
    }
}

sealed interface BrowseState {
    data object Loading : BrowseState
    data class Success(val anime: List<AniListAnime>) : BrowseState
    data class Error(val message: String) : BrowseState
}
