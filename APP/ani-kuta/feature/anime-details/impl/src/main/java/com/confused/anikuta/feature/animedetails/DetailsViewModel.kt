package com.confused.anikuta.feature.animedetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.anilist.api.AniListApi
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.preferences.PreferenceStore
import com.confused.anikuta.core.videoresolver.ResolvedVideo
import com.confused.anikuta.core.videoresolver.ResolvedVideosRegistry
import com.confused.anikuta.core.videoresolver.ResolverServer
import com.confused.anikuta.core.videoresolver.VideoResolver
import com.confused.anikuta.data.extension.manager.ExtensionManager
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the Details screen.
 *
 * Manages:
 * 1. AniList metadata loading (the anime's title, cover, description, etc.).
 * 2. Source selection — the user searches installed sources for a matching
 *    SAnime and links it. The link is persisted per-anilist-id.
 * 3. Episode fetching — once a source is linked, fetches the episode list.
 * 4. Video resolution — when the user taps an episode, resolves available videos.
 *
 * Architecture (Phase 5B — temporary):
 * - Uses `AniListAnime` for metadata (not `UnifiedAnime` — that's Phase 5d).
 * - Source linking stored in PreferenceStore as a JSON-ish string keyed by
 *   `"details_source_link:$anilistId"` → `"$sourceId:$animeUrl"`.
 *   Phase 5d will migrate this to ContentUID + ExternalReference.
 * - Episode list is NOT persisted — re-fetched on each Details open. Phase 5e
 *   will add caching + new-episode detection.
 *
 * CORE_RULES §20: Logged with tag "Anikuta:Feature:Details".
 * CORE_RULES §23: Reactive state (StateFlow).
 */
class DetailsViewModel(
    private val anilistApi: AniListApi,
    private val extensionManager: ExtensionManager,
    private val preferenceStore: PreferenceStore,
    private val videoResolver: VideoResolver,
) : ViewModel() {

    companion object {
        private const val TAG = "Anikuta:Feature:Details"
        private const val KEY_SOURCE_LINK_PREFIX = "details_source_link:"
    }

    private val _state = MutableStateFlow<DetailsState>(DetailsState.Loading)
    val state: StateFlow<DetailsState> = _state.asStateFlow()

    /** The available trusted sources (for the manual search sheet). */
    val availableSources: StateFlow<List<AnimeCatalogueSource>> =
        extensionManager.sources.map { sourceMap ->
            sourceMap.values.filterIsInstance<AnimeCatalogueSource>()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The currently linked source (null = no source linked). */
    private val _linkedSource = MutableStateFlow<LinkedSource?>(null)
    val linkedSource: StateFlow<LinkedSource?> = _linkedSource.asStateFlow()

    /** Manual search state. */
    private val _manualSearchState = MutableStateFlow<ManualSearchState>(ManualSearchState.Idle)
    val manualSearchState: StateFlow<ManualSearchState> = _manualSearchState.asStateFlow()

    /** Episode list state. */
    private val _episodeState = MutableStateFlow<EpisodeState>(EpisodeState.Idle)
    val episodeState: StateFlow<EpisodeState> = _episodeState.asStateFlow()

    /** Video resolution state (for the resolver sheet). */
    private val _resolverState = MutableStateFlow<ResolverState>(ResolverState.Idle)
    val resolverState: StateFlow<ResolverState> = _resolverState.asStateFlow()

    /** Registry key for the structured resolved servers (for QualitySheet in watch screen). */
    private val _resolvedVideosKey = MutableStateFlow("")
    val resolvedVideosKey: StateFlow<String> = _resolvedVideosKey.asStateFlow()

    private var currentAnimeId: Int = 0

    fun loadDetails(animeId: Int) {
        currentAnimeId = animeId
        _state.value = DetailsState.Loading
        viewModelScope.launch {
            try {
                val anime = anilistApi.fetchAnimeDetails(animeId)
                Logger.i(TAG) { "Loaded details for $animeId" }
                _state.value = DetailsState.Success(anime)

                // Check for a persisted source link.
                loadLinkedSource(animeId)
            } catch (e: Exception) {
                Logger.e(TAG, e) { "Failed: ${e.message}" }
                _state.value = DetailsState.Error(e.message ?: "Unknown error")
            }
        }
    }

    // ── Source linking ──

    /**
     * Load the persisted source link for this anime (if any).
     * If found, fetch the episode list.
     */
    private fun loadLinkedSource(animeId: Int) {
        val linkStr = preferenceStore.getString(KEY_SOURCE_LINK_PREFIX + animeId, "")
        if (linkStr.isBlank()) return

        val parts = linkStr.split(":", limit = 2)
        if (parts.size != 2) return
        val sourceId = parts[0].toLongOrNull() ?: return
        val animeUrl = parts[1]

        val source = extensionManager.getSource(sourceId) as? AnimeCatalogueSource ?: run {
            Logger.w(TAG) { "Linked source $sourceId not found (uninstalled?)" }
            return
        }

        _linkedSource.value = LinkedSource(sourceId, source.name, animeUrl)
        // Get the anime title from the current state (for the SAnime.title lateinit field).
        val animeTitle = (_state.value as? DetailsState.Success)?.anime?.displayName ?: animeUrl
        fetchEpisodes(source, animeUrl, animeTitle)
    }

    /**
     * Link a source + SAnime to the current anime. Persists the link + fetches episodes.
     */
    fun linkSource(source: AnimeCatalogueSource, sAnime: SAnime) {
        val animeId = currentAnimeId
        Logger.i(TAG) { "Linking anime $animeId to source ${source.name} (${sAnime.url})" }
        preferenceStore.putString(
            KEY_SOURCE_LINK_PREFIX + animeId,
            "${source.id}:${sAnime.url}",
        )
        _linkedSource.value = LinkedSource(source.id, source.name, sAnime.url)
        fetchEpisodes(source, sAnime.url, sAnime.title)
    }

    /**
     * Unlink the current source.
     */
    fun unlinkSource() {
        val animeId = currentAnimeId
        Logger.i(TAG) { "Unlinking source for anime $animeId" }
        preferenceStore.putString(KEY_SOURCE_LINK_PREFIX + animeId, "")
        _linkedSource.value = null
        _episodeState.value = EpisodeState.Idle
    }

    // ── Episode fetching ──

    private fun fetchEpisodes(source: AnimeCatalogueSource, animeUrl: String, animeTitle: String) {
        _episodeState.value = EpisodeState.Loading
        viewModelScope.launch {
            try {
                Logger.i(TAG) { "Fetching episodes from ${source.name} for $animeUrl (title: $animeTitle)" }
                // CRITICAL: SAnime.title is lateinit — MUST be set before passing to
                // getEpisodeList. Extensions may read sAnime.title to construct API URLs.
                val sAnime = SAnime.create().apply {
                    url = animeUrl
                    title = animeTitle
                    initialized = false
                }

                // CRITICAL: Call getEpisodeList (suspend), NOT fetchEpisodeList (Observable).
                // Extensions like AniKotoS override getEpisodeList (the suspend version) to
                // use a WebView-based fetch. If we call fetchEpisodeList().awaitSingle(), the
                // DEFAULT AnimeHttpSource.fetchEpisodeList is used instead, which builds
                // `baseUrl + anime.url` (missing "/" → UnknownHostException).
                // The old project calls source.getEpisodeList(sAnime) directly.
                val episodes = withContext(Dispatchers.IO) {
                    source.getEpisodeList(sAnime)
                }
                Logger.i(TAG) { "Fetched ${episodes.size} episodes from ${source.name}" }
                _episodeState.value = if (episodes.isEmpty()) {
                    EpisodeState.Empty
                } else {
                    // Sort descending (newest first) per D-056.
                    val sorted = episodes.sortedByDescending { it.episode_number }
                    EpisodeState.Loaded(sorted)
                }
            } catch (e: Throwable) {
                // Catch Throwable — binary-incompat throws NoClassDefFoundError,
                // OkHttp version mismatch throws IncompatibleClassChangeError.
                val errorMsg = "${e::class.java.simpleName}: ${e.message ?: "Unknown error"}"
                Logger.e(TAG, e) { "Episode fetch failed for ${source.name}: $errorMsg" }
                _episodeState.value = EpisodeState.Error(errorMsg)
            }
        }
    }

    // ── Manual search ──

    /**
     * Search a single source by title. Updates [manualSearchState].
     */
    fun searchSource(source: AnimeCatalogueSource, query: String) {
        _manualSearchState.value = ManualSearchState.Searching
        viewModelScope.launch {
            try {
                Logger.i(TAG) { "Searching source ${source.name} for '$query'" }
                val page = withContext(Dispatchers.IO) {
                    source.getSearchAnime(1, query, AnimeFilterList())
                }
                val results = page.animes
                Logger.i(TAG) { "Got ${results.size} results from ${source.name}" }
                _manualSearchState.value = ManualSearchState.Results(source, results)
            } catch (e: Throwable) {
                val errorMsg = "${e::class.java.simpleName}: ${e.message ?: "Unknown error"}"
                Logger.e(TAG, e) { "Manual search failed for ${source.name}: $errorMsg" }
                _manualSearchState.value = ManualSearchState.Error(source.name, errorMsg)
            }
        }
    }

    fun clearManualSearch() {
        _manualSearchState.value = ManualSearchState.Idle
    }

    // ── Video resolution ──

    /**
     * Resolve videos for an episode. Updates [resolverState].
     * The UI shows the resolver sheet when the state is Success.
     */
    fun resolveEpisode(episode: SEpisode) {
        val linked = _linkedSource.value ?: run {
            Logger.w(TAG) { "Cannot resolve — no source linked" }
            return
        }
        val source = extensionManager.getSource(linked.sourceId) as? AnimeHttpSource ?: run {
            Logger.w(TAG) { "Source ${linked.sourceId} not found or not an AnimeHttpSource" }
            _resolverState.value = ResolverState.Error("Source not available")
            return
        }

        _resolverState.value = ResolverState.Loading
        viewModelScope.launch {
            try {
                Logger.i(TAG) { "Resolving videos for episode ${episode.url} (epNum: ${episode.episode_number}, name: ${episode.name})" }
                // Pass the FULL SEpisode — extensions may read episode_number, name, etc.
                // to construct API URLs. The old resolver created a minimal SEpisode which
                // left episode_number at -1f → wrong URLs → 404.
                val state = videoResolver.resolve(source, episode)
                state.collect { s ->
                    _resolverState.value = when (s) {
                        is com.confused.anikuta.core.videoresolver.ResolverState.Idle ->
                            ResolverState.Idle
                        is com.confused.anikuta.core.videoresolver.ResolverState.Loading ->
                            ResolverState.Loading
                        is com.confused.anikuta.core.videoresolver.ResolverState.Success -> {
                            // Also resolve structured servers (for QualitySheet) and store in registry.
                            // We do this AFTER the flat list succeeds so the user sees the
                            // resolver sheet immediately, while structured resolution continues
                            // in the background.
                            viewModelScope.launch {
                                videoResolver.resolveStructured(source, episode).collect { ss ->
                                    if (ss is com.confused.anikuta.core.videoresolver.StructuredResolverState.Success) {
                                        val key = ResolvedVideosRegistry.put(ss.servers)
                                        _resolvedVideosKey.value = key
                                        Logger.d(TAG) { "Stored ${ss.servers.size} servers in registry (key: $key)" }
                                    }
                                }
                            }
                            ResolverState.Success(s.videos)
                        }
                        is com.confused.anikuta.core.videoresolver.ResolverState.Error ->
                            ResolverState.Error(s.message)
                    }
                }
            } catch (e: Throwable) {
                Logger.e(TAG, e) { "Resolution failed: ${e.message}" }
                _resolverState.value = ResolverState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun clearResolver() {
        _resolverState.value = ResolverState.Idle
        _resolvedVideosKey.value = ""
    }
}

// ── State types ──

sealed interface DetailsState {
    data object Loading : DetailsState
    data class Success(val anime: AniListAnime) : DetailsState
    data class Error(val message: String) : DetailsState
}

/** A linked source — the source ID + name + the SAnime's URL on that source. */
data class LinkedSource(
    val sourceId: Long,
    val sourceName: String,
    val animeUrl: String,
)

/** Manual search state. */
sealed interface ManualSearchState {
    data object Idle : ManualSearchState
    data object Searching : ManualSearchState
    data class Results(val source: AnimeCatalogueSource, val sAnimes: List<SAnime>) : ManualSearchState
    data class Error(val sourceName: String, val message: String) : ManualSearchState
}

/** Episode list state. */
sealed interface EpisodeState {
    data object Idle : EpisodeState
    data object Loading : EpisodeState
    data object Empty : EpisodeState
    data class Loaded(val episodes: List<SEpisode>) : EpisodeState
    data class Error(val message: String) : EpisodeState
}

/** Video resolution state (for the resolver sheet). */
sealed interface ResolverState {
    data object Idle : ResolverState
    data object Loading : ResolverState
    data class Success(val videos: List<ResolvedVideo>) : ResolverState
    data class Error(val message: String) : ResolverState
}
