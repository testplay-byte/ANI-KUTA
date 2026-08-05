package com.confused.anikuta.feature.animedetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.anilist.api.AniListApi
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.anilist.provider.AniListDetailsProvider
import com.confused.anikuta.core.anilist.provider.toUnifiedAnime
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.common.model.UnifiedAnime
import com.confused.anikuta.core.preferences.AutoLinkPreferences
import com.confused.anikuta.core.preferences.PreferenceStore
import com.confused.anikuta.core.smartmatcher.AutoLinkResult
import com.confused.anikuta.core.smartmatcher.AutoLinkService
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
 * 5. **Auto-link (Phase B)** — for extension entries, searches AniList by title
 *    and merges metadata if a match is found. Falls back to a manual link sheet.
 *
 * ## Auto-link flow (Phase B)
 * - `loadFromExtension()` → fetches extension details → kicks off `performAutoLink()`.
 * - `performAutoLink()` → checks per-source setting → cache check → AniList search →
 *   SmartMatcher → on match, merges AniList data via `AniListDetailsProvider.mergeInto()`.
 * - On NoMatch → UI shows `ManualLinkSheet` (user picks the right AniList entry).
 * - On Skipped (auto-link disabled) → UI stays on extension data only.
 *
 * CORE_RULES §20: Logged with tag "Anikuta:Feature:Details".
 * CORE_RULES §23: Reactive state (StateFlow).
 */
class DetailsViewModel(
    private val anilistApi: AniListApi,
    private val extensionManager: ExtensionManager,
    private val preferenceStore: PreferenceStore,
    private val videoResolver: VideoResolver,
    private val episodeMetadataFetcher: com.confused.anikuta.core.metadata.EpisodeMetadataFetcher,
    private val extensionProvider: com.confused.anikuta.data.extension.provider.ExtensionDetailsProvider,
    private val anilistProvider: AniListDetailsProvider,
    private val autoLinkService: AutoLinkService,
    private val autoLinkPreferences: AutoLinkPreferences,
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

    /** Manual search state (for source linking — AniList entries only). */
    private val _manualSearchState = MutableStateFlow<ManualSearchState>(ManualSearchState.Idle)
    val manualSearchState: StateFlow<ManualSearchState> = _manualSearchState.asStateFlow()

    /** Episode list state. */
    private val _episodeState = MutableStateFlow<EpisodeState>(EpisodeState.Idle)
    val episodeState: StateFlow<EpisodeState> = _episodeState.asStateFlow()

    /** Episode metadata (titles, thumbnails, descriptions, air dates). */
    private val _episodeMetadata = MutableStateFlow<Map<Int, com.confused.anikuta.core.metadata.EpisodeMetadata>>(emptyMap())
    val episodeMetadata: StateFlow<Map<Int, com.confused.anikuta.core.metadata.EpisodeMetadata>> = _episodeMetadata.asStateFlow()

    /** Video resolution state (for the resolver sheet). */
    private val _resolverState = MutableStateFlow<ResolverState>(ResolverState.Idle)
    val resolverState: StateFlow<ResolverState> = _resolverState.asStateFlow()

    /** Registry key for the structured resolved servers (for QualitySheet in watch screen). */
    private val _resolvedVideosKey = MutableStateFlow("")
    val resolvedVideosKey: StateFlow<String> = _resolvedVideosKey.asStateFlow()

    // ── Phase B: Auto-link state ──

    /** Auto-link state — tracks the auto-linking lifecycle for extension entries. */
    private val _autoLinkState = MutableStateFlow<AutoLinkState>(AutoLinkState.Idle)
    val autoLinkState: StateFlow<AutoLinkState> = _autoLinkState.asStateFlow()

    /** AniList search state for the manual link sheet. */
    private val _anilistSearchState = MutableStateFlow<AniListSearchState>(AniListSearchState.Idle)
    val anilistSearchState: StateFlow<AniListSearchState> = _anilistSearchState.asStateFlow()

    /**
     * Whether the manual link sheet should be shown.
     * Set to true when auto-link returns NoMatch (or user taps "Link to AniList" in the menu).
     * Set to false when the user picks/skips/dismisses.
     */
    private val _showManualLinkSheet = MutableStateFlow(false)
    val showManualLinkSheet: StateFlow<Boolean> = _showManualLinkSheet.asStateFlow()

    private var currentAnimeId: Int = 0

    // ── Load from AniList (existing flow) ──

    fun loadFromAniList(animeId: Int) {
        currentAnimeId = animeId
        _state.value = DetailsState.Loading
        _autoLinkState.value = AutoLinkState.Idle
        _showManualLinkSheet.value = false
        _anilistSearchState.value = AniListSearchState.Idle
        viewModelScope.launch {
            try {
                val anime = anilistApi.fetchAnimeDetails(animeId)
                Logger.i(TAG) { "Loaded AniList details for $animeId" }
                _state.value = DetailsState.Success(anime.toUnifiedAnime())

                // Check for a persisted source link.
                loadLinkedSource(animeId)
            } catch (e: Exception) {
                Logger.e(TAG, e) { "Failed: ${e.message}" }
                _state.value = DetailsState.Error(e.message ?: "Unknown error")
            }
        }
    }

    // ── Load from Extension (Phase A + Phase B auto-link) ──

    fun loadFromExtension(sourceId: Long, animeUrl: String, title: String, thumbnailUrl: String?) {
        currentAnimeId = 0 // No AniList ID yet — will be set by auto-link if it matches.
        _state.value = DetailsState.Loading
        _autoLinkState.value = AutoLinkState.Idle
        _showManualLinkSheet.value = false
        _anilistSearchState.value = AniListSearchState.Idle
        viewModelScope.launch {
            try {
                // Use the ExtensionDetailsProvider to fetch full details.
                val unifiedAnime = extensionProvider.fetchFromExtension(sourceId, animeUrl, title, thumbnailUrl)

                if (unifiedAnime != null) {
                    Logger.i(TAG) { "Loaded extension details: $title from source $sourceId" }
                    _state.value = DetailsState.Success(unifiedAnime)

                    // Fetch episodes from the extension source directly.
                    fetchEpisodesFromSource(sourceId, animeUrl, title)

                    // ── Phase B: Kick off auto-link (non-blocking) ──
                    performAutoLink(sourceId, animeUrl, unifiedAnime)
                } else {
                    _state.value = DetailsState.Error("Failed to load extension details")
                }
            } catch (e: Exception) {
                Logger.e(TAG, e) { "Extension details failed: ${e.message}" }
                _state.value = DetailsState.Error(e.message ?: "Unknown error")
            }
        }
    }

    // ── Phase B: Auto-link ──

    /**
     * Attempt to auto-link an extension entry to AniList.
     *
     * Delegates to [AutoLinkService]. On match/cached, merges AniList data into
     * the current UnifiedAnime. On NoMatch, shows the manual link sheet.
     */
    private suspend fun performAutoLink(sourceId: Long, animeUrl: String, anime: UnifiedAnime) {
        _autoLinkState.value = AutoLinkState.Searching
        val title = anime.displayName
        val year = anime.seasonYear

        Logger.i(TAG) { "Auto-link started: sourceId=$sourceId, title='$title', year=$year" }

        val result = autoLinkService.attemptAutoLink(sourceId, animeUrl, title, year)
        when (result) {
            is AutoLinkResult.Cached -> {
                Logger.i(TAG) { "Auto-link cache HIT: anilistId=${result.anilistId}" }
                mergeAniListIntoUnified(result.anilistId)
                _autoLinkState.value = AutoLinkState.Matched(result.anilistId, 1.0f, cached = true)
            }
            is AutoLinkResult.Matched -> {
                Logger.i(TAG) { "Auto-link MATCH: anilistId=${result.anilistId} (score=${result.score})" }
                mergeAniListIntoUnified(result.anilistId)
                _autoLinkState.value = AutoLinkState.Matched(result.anilistId, result.score, cached = false)
            }
            is AutoLinkResult.NoMatch -> {
                Logger.i(TAG) { "Auto-link NO MATCH: best=${result.bestScore} — showing manual sheet" }
                _autoLinkState.value = AutoLinkState.NoMatch(result.bestScore, result.searchedTitle)
                _showManualLinkSheet.value = true
            }
            is AutoLinkResult.Skipped -> {
                Logger.i(TAG) { "Auto-link SKIPPED: ${result.reason}" }
                _autoLinkState.value = AutoLinkState.Skipped(result.reason)
            }
            is AutoLinkResult.Error -> {
                Logger.e(TAG) { "Auto-link ERROR: ${result.message}" }
                _autoLinkState.value = AutoLinkState.Error(result.message)
            }
        }
    }

    /**
     * Merge AniList metadata into the current UnifiedAnime.
     *
     * Sets the anilistId, fetches AniList details via [AniListDetailsProvider.mergeInto],
     * and updates the state. Also kicks off episode metadata fetch (now that we
     * have an anilistId).
     */
    private suspend fun mergeAniListIntoUnified(anilistId: Int) {
        try {
            val current = (_state.value as? DetailsState.Success)?.anime ?: return
            // Set the anilistId first so mergeInto knows what to fetch.
            // NOTE: Do NOT change entryMode — the entry was opened from an extension
            // search result; auto-linking only enriches it with AniList metadata.
            val baseWithId = current.copy(anilistId = anilistId)
            val merged = anilistProvider.mergeInto(baseWithId)
            _state.value = DetailsState.Success(merged)
            currentAnimeId = anilistId // So episode metadata fetch can use it.

            // Now that we have an anilistId, kick off episode metadata fetch.
            val malId = merged.idMal
            val episodes = (_episodeState.value as? EpisodeState.Loaded)?.episodes ?: emptyList()
            if (episodes.isNotEmpty()) {
                viewModelScope.launch {
                    try {
                        val metadata = episodeMetadataFetcher.fetchEpisodeMetadata(
                            anilistId = anilistId,
                            malId = malId,
                            episodeCount = episodes.size,
                        )
                        _episodeMetadata.value = metadata
                        Logger.i(TAG) { "Episode metadata loaded post-link: ${metadata.size} entries" }
                    } catch (e: Exception) {
                        Logger.w(TAG) { "Episode metadata fetch (post-link) failed: ${e.message}" }
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, e) { "mergeAniListIntoUnified failed for anilistId=$anilistId" }
        }
    }

    // ── Phase B: Manual link sheet ──

    /**
     * Search AniList for the manual link sheet.
     * Pre-fills with the extension title if [query] is blank.
     */
    fun searchAniListForLink(query: String) {
        val effectiveQuery = if (query.isBlank()) {
            (_state.value as? DetailsState.Success)?.anime?.displayName ?: ""
        } else query
        if (effectiveQuery.isBlank()) {
            Logger.w(TAG) { "searchAniListForLink: no query + no current title" }
            return
        }

        _anilistSearchState.value = AniListSearchState.Searching
        viewModelScope.launch {
            try {
                val results = anilistApi.searchAnime(effectiveQuery, page = 1, perPage = 20)
                Logger.i(TAG) { "AniList manual search: ${results.size} results for '$effectiveQuery'" }
                _anilistSearchState.value = if (results.isEmpty()) {
                    AniListSearchState.Empty
                } else {
                    AniListSearchState.Results(results)
                }
            } catch (e: Exception) {
                Logger.e(TAG, e) { "AniList manual search failed: ${e.message}" }
                _anilistSearchState.value = AniListSearchState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Manually link the current extension entry to an AniList anime.
     * Caches the link, merges AniList data, closes the manual sheet.
     */
    fun linkAniListEntry(anilistId: Int) {
        val anime = (_state.value as? DetailsState.Success)?.anime ?: run {
            Logger.w(TAG) { "linkAniListEntry: no current anime" }
            return
        }
        val sourceId = anime.sourceId ?: run {
            Logger.w(TAG) { "linkAniListEntry: no sourceId (not an extension entry)" }
            return
        }
        val animeUrl = anime.animeUrl ?: run {
            Logger.w(TAG) { "linkAniListEntry: no animeUrl" }
            return
        }

        Logger.i(TAG) { "Manually linking extension entry to anilistId=$anilistId" }
        autoLinkService.cacheManualLink(sourceId, animeUrl, anilistId)

        viewModelScope.launch {
            mergeAniListIntoUnified(anilistId)
            _autoLinkState.value = AutoLinkState.Matched(anilistId, 1.0f, cached = false)
            _anilistSearchState.value = AniListSearchState.Idle
            _showManualLinkSheet.value = false
        }
    }

    /**
     * User skipped the manual link sheet — proceed without linking.
     */
    fun skipAniListLink() {
        Logger.i(TAG) { "User skipped AniList link" }
        _autoLinkState.value = AutoLinkState.Skipped("User skipped manual link")
        _anilistSearchState.value = AniListSearchState.Idle
        _showManualLinkSheet.value = false
    }

    /**
     * Unlink the current AniList entry (from the three-dot menu).
     * Clears the cache + removes AniList-specific fields from the UnifiedAnime.
     */
    fun unlinkAniList() {
        val anime = (_state.value as? DetailsState.Success)?.anime ?: return
        val sourceId = anime.sourceId ?: return
        val animeUrl = anime.animeUrl ?: return
        val anilistId = anime.anilistId ?: return

        Logger.i(TAG) { "Unlinking AniList entry: sourceId=$sourceId, url=$animeUrl, anilistId=$anilistId" }
        autoLinkService.clearCachedLink(sourceId, animeUrl)

        // Clear AniList-specific fields. The extension data stays.
        val cleared = anime.copy(
            anilistId = null,
            idMal = null,
        )
        _state.value = DetailsState.Success(cleared)
        _autoLinkState.value = AutoLinkState.Idle
        currentAnimeId = 0
        _episodeMetadata.value = emptyMap() // Clear AniList-sourced metadata.
    }

    /**
     * Force-open the manual link sheet (from the three-dot menu "Link to AniList").
     */
    fun openManualLinkSheet() {
        Logger.i(TAG) { "User opened manual link sheet from menu" }
        _anilistSearchState.value = AniListSearchState.Idle
        _showManualLinkSheet.value = true
    }

    fun clearAniListSearch() {
        _anilistSearchState.value = AniListSearchState.Idle
    }

    fun dismissManualLinkSheet() {
        _showManualLinkSheet.value = false
    }

    // ── Fetch episodes from a specific source (used by extension flow) ──

    private fun fetchEpisodesFromSource(sourceId: Long, animeUrl: String, animeTitle: String) {
        val source = extensionManager.getSource(sourceId) as? AnimeCatalogueSource
        if (source == null) {
            Logger.w(TAG) { "Source not found for sourceId=$sourceId" }
            _episodeState.value = EpisodeState.Error("Source not available")
            return
        }
        fetchEpisodes(source, animeUrl, animeTitle)
    }

    // Legacy method (kept for compatibility — delegates to loadFromAniList)
    fun loadDetails(animeId: Int) = loadFromAniList(animeId)

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

                // Fetch episode metadata (titles, thumbnails, descriptions, dates).
                // Uses Anikage.cc (primary), Jikan/MAL (secondary), AniList streaming (tertiary).
                // Runs in parallel — doesn't block the episode list display.
                val animeId = currentAnimeId
                val malId = (_state.value as? DetailsState.Success)?.anime?.idMal
                if (animeId > 0 && episodes.isNotEmpty()) {
                    viewModelScope.launch {
                        try {
                            val metadata = episodeMetadataFetcher.fetchEpisodeMetadata(
                                anilistId = animeId,
                                malId = malId,
                                episodeCount = episodes.size,
                            )
                            _episodeMetadata.value = metadata
                            Logger.i(TAG) { "Episode metadata loaded: ${metadata.size} entries" }
                        } catch (e: Exception) {
                            Logger.w(TAG) { "Episode metadata fetch failed: ${e.message}" }
                        }
                    }
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

    // ── Manual search (source linking — AniList entries) ──

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
        // For extension entries, the linked source may not be set (source linking
        // is for AniList entries). Fall back to the UnifiedAnime's sourceId.
        val linked = _linkedSource.value ?: run {
            val anime = (_state.value as? DetailsState.Success)?.anime
            val sourceId = anime?.sourceId
            val sourceName = anime?.sourceName
            if (sourceId != null && sourceName != null) {
                LinkedSource(sourceId, sourceName, anime.animeUrl ?: "")
            } else null
        }
        if (linked == null) {
            Logger.w(TAG) { "Cannot resolve — no source linked and no extension sourceId" }
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
                            // Build structured servers from the SAME video list — NO second
                            // getHosterList call. This prevents the double-resolve bug where
                            // the second call kills the first call's proxy URLs.
                            val servers = videoResolver.buildServers(s.rawEntries, source.name)
                            if (servers.isNotEmpty()) {
                                val key = ResolvedVideosRegistry.put(servers)
                                _resolvedVideosKey.value = key
                                Logger.d(TAG) { "Stored ${servers.size} servers in registry (key: $key) — derived from same resolve() call, no double-resolve" }
                            }
                            ResolverState.Success(s.videos, servers)
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
    data class Success(val anime: UnifiedAnime) : DetailsState
    data class Error(val message: String) : DetailsState
}

/** A linked source — the source ID + name + the SAnime's URL on that source. */
data class LinkedSource(
    val sourceId: Long,
    val sourceName: String,
    val animeUrl: String,
)

/** Manual search state (source linking — AniList entries). */
sealed interface ManualSearchState {
    data object Idle : ManualSearchState
    data object Searching : ManualSearchState
    data class Results(val source: AnimeCatalogueSource, val sAnimes: List<SAnime>) : ManualSearchState
    data class Error(val sourceName: String, val message: String) : ManualSearchState
}

// ── Phase B: Auto-link + AniList search states ──

/**
 * Auto-link lifecycle for extension entries.
 *
 * - [Idle]: Not an extension entry, or not yet started.
 * - [Searching]: AutoLinkService is running (cache check + AniList search + SmartMatcher).
 * - [Matched]: A confident match was found (and merged into UnifiedAnime).
 * - [NoMatch]: No confident match — manual link sheet should be shown.
 * - [Skipped]: Auto-link disabled for this source, or strategy = MANUAL, or user skipped.
 * - [Error]: AniList search or matching failed.
 */
sealed interface AutoLinkState {
    data object Idle : AutoLinkState
    data object Searching : AutoLinkState
    data class Matched(val anilistId: Int, val score: Float, val cached: Boolean) : AutoLinkState
    data class NoMatch(val bestScore: Float, val searchedTitle: String) : AutoLinkState
    data class Skipped(val reason: String) : AutoLinkState
    data class Error(val message: String) : AutoLinkState
}

/** AniList search state for the manual link sheet. */
sealed interface AniListSearchState {
    data object Idle : AniListSearchState
    data object Searching : AniListSearchState
    data object Empty : AniListSearchState
    data class Results(val anime: List<AniListAnime>) : AniListSearchState
    data class Error(val message: String) : AniListSearchState
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
    data class Success(
        val videos: List<ResolvedVideo>,
        val servers: List<ResolverServer> = emptyList(),
    ) : ResolverState
    data class Error(val message: String) : ResolverState
}
