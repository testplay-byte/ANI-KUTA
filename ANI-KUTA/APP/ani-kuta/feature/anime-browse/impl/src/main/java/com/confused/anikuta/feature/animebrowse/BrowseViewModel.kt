package com.confused.anikuta.feature.animebrowse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.anilist.api.AniListApi
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.datacache.DataCacheRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * ViewModel for the Browse screen (D-249 multi-section redesign).
 *
 * Local-first: reads from browse_cache first → displays instantly.
 * Sections: Trending (with banners for the hero), Popular, Top Rated.
 * Each section cached independently (6h TTL) in the multi-section browse_cache table.
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
        private const val SECTION_TRENDING = "trending"
        private const val SECTION_POPULAR = "popular"
        private const val SECTION_TOP_RATED = "top_rated"
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
     * D-253: the hero pager's items — up to 5 trending anime WITH banner
     * images (falls back to the first 3 trending items so a hero always
     * renders). Evolves D-249's single-hero-item flow.
     */
    val heroItems: StateFlow<List<AniListAnime>> = _state
        .map { state ->
            val trending = (state as? BrowseState.Success)?.anime ?: emptyList()
            trending.filter { !it.bannerImage.isNullOrBlank() }.take(5)
                .ifEmpty { trending.take(3) }
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
     */
    private fun loadSection(sectionKey: String, sort: String, onResult: (List<AniListAnime>) -> Unit) {
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) {
                dataCacheRepository.getBrowseCache(sectionKey)
            }
            if (cached != null) {
                val cachedAnime = withContext(Dispatchers.IO) {
                    parseBrowseCache(cached.dataJson)
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
            val json = serializeBrowseCache(anime)
            withContext(Dispatchers.IO) {
                dataCacheRepository.upsertBrowseCache(sectionKey, json)
            }
            Logger.i(TAG) { "Cached ${anime.size} $sectionKey anime" }
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Failed to fetch $sectionKey: ${e.message}" }
            if (_state.value !is BrowseState.Success && sectionKey == SECTION_TRENDING) {
                _state.value = BrowseState.Error(e.message ?: "Unknown error")
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

    // ── Cache serialization ────────────────────────────────────────────────
    // D-249: extended with bannerImage + genres + status (for the hero + card meta).

    private fun serializeBrowseCache(anime: List<AniListAnime>): String {
        val array = buildJsonArray {
            for (a in anime) {
                add(buildJsonObject {
                    put("id", a.id)
                    put("title", a.displayName)
                    a.coverUrl?.let { put("cover", it) }
                    a.averageScore?.let { put("score", it) }
                    a.episodes?.let { put("episodes", it) }
                    a.seasonYear?.let { put("year", it) }
                    a.bannerImage?.let { put("banner", it) }
                    a.genres?.takeIf { it.isNotEmpty() }?.let { put("genres", it.joinToString(",")) }
                    a.status?.let { put("status", it) }
                })
            }
        }
        return array.toString()
    }

    private fun parseBrowseCache(json: String): List<AniListAnime> {
        return try {
            val array = Json.parseToJsonElement(json).jsonArray
            array.map { element ->
                val obj = element.jsonObject
                AniListAnime(
                    id = obj["id"]!!.jsonPrimitive.int,
                    title = com.confused.anikuta.core.anilist.model.AnimeTitle(
                        romaji = obj["title"]!!.jsonPrimitive.toString().trim('"'),
                        english = obj["title"]!!.jsonPrimitive.toString().trim('"'),
                    ),
                    coverImage = com.confused.anikuta.core.anilist.model.CoverImage(
                        large = obj["cover"]?.jsonPrimitive?.toString()?.trim('"'),
                        extraLarge = obj["cover"]?.jsonPrimitive?.toString()?.trim('"'),
                    ),
                    averageScore = obj["score"]?.jsonPrimitive?.intOrNull,
                    episodes = obj["episodes"]?.jsonPrimitive?.intOrNull,
                    seasonYear = obj["year"]?.jsonPrimitive?.intOrNull,
                    bannerImage = obj["banner"]?.jsonPrimitive?.toString()?.trim('"')?.takeIf { it != "null" },
                    genres = obj["genres"]?.jsonPrimitive?.toString()?.trim('"')
                        ?.takeIf { it != "null" && it.isNotBlank() }?.split(","),
                    status = obj["status"]?.jsonPrimitive?.toString()?.trim('"')?.takeIf { it != "null" },
                )
            }
        } catch (e: Exception) {
            Logger.w(TAG) { "Failed to parse browse cache: ${e.message}" }
            emptyList()
        }
    }
}

sealed interface BrowseState {
    data object Loading : BrowseState
    data class Success(val anime: List<AniListAnime>) : BrowseState
    data class Error(val message: String) : BrowseState
}
