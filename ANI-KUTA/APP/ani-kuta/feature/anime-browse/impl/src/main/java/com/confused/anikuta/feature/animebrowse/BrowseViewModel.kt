package com.confused.anikuta.feature.animebrowse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.anilist.api.AniListApi
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.datacache.DataCacheRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * ViewModel for the Browse screen (Phase D.2).
 *
 * Local-first: reads from browse_cache first → displays instantly.
 * If cache is expired (6 hours) → fetches from network in background → updates cache.
 * If no cache → fetches from network → caches the result.
 *
 * Pull-to-refresh: force-fetches from network + updates cache.
 * 6-hour auto-update: checked on init. Only on the homepage (not other pages).
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
    }

    private val _state = MutableStateFlow<BrowseState>(BrowseState.Loading)
    val state: StateFlow<BrowseState> = _state.asStateFlow()

    /** Whether a background refresh is in progress (shows a subtle indicator). */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        loadTrending()
    }

    /**
     * Load trending anime. D.2: Cache-first approach.
     * 1. Check browse_cache → if cached, display instantly.
     * 2. If cache is expired (6h) → fetch from network in background → update cache.
     * 3. If no cache → fetch from network → cache the result.
     */
    fun loadTrending() {
        viewModelScope.launch {
            // D.2: Check cache first.
            val cached = dataCacheRepository.getBrowseCache(SECTION_TRENDING)
            if (cached != null) {
                // Display cached data instantly.
                val cachedAnime = parseBrowseCache(cached.dataJson)
                if (cachedAnime.isNotEmpty()) {
                    Logger.i(TAG) { "Loaded ${cachedAnime.size} trending from cache (age=${(System.currentTimeMillis() - cached.fetchedAt) / 3600000}h)" }
                    _state.value = BrowseState.Success(cachedAnime)
                }
            }

            // Check if cache is expired (or missing) → fetch from network.
            if (cached == null || dataCacheRepository.isBrowseCacheExpired(SECTION_TRENDING)) {
                fetchFromNetwork()
            }
        }
    }

    /**
     * Force-refresh from network. Called by pull-to-refresh.
     * Clears the "isRefreshing" indicator when done.
     */
    fun refresh() {
        viewModelScope.launch {
            fetchFromNetwork()
        }
    }

    private suspend fun fetchFromNetwork() {
        _isRefreshing.value = true
        try {
            val anime = anilistApi.fetchTrending()
            Logger.i(TAG) { "Fetched ${anime.size} trending from network" }
            _state.value = BrowseState.Success(anime)

            // D.2: Cache the result.
            val json = serializeBrowseCache(anime)
            dataCacheRepository.upsertBrowseCache(SECTION_TRENDING, json)
            Logger.i(TAG) { "Cached ${anime.size} trending anime" }
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Failed to fetch trending: ${e.message}" }
            // Only show error if we don't have cached data.
            if (_state.value !is BrowseState.Success) {
                _state.value = BrowseState.Error(e.message ?: "Unknown error")
            }
        } finally {
            _isRefreshing.value = false
        }
    }

    // ── Cache serialization ────────────────────────────────────────────────
    // Simple JSON format: [{ "id": 1, "title": "...", "cover": "...", "score": 82, "episodes": 24, "year": 2023 }]

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
