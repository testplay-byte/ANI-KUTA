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
import com.confused.anikuta.data.extension.provider.toUnifiedAnime
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.CloudflareException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    private val episodeMetadataEngine: com.confused.anikuta.core.metadata.EpisodeMetadataEngine,
    private val extensionProvider: com.confused.anikuta.data.extension.provider.ExtensionDetailsProvider,
    private val anilistProvider: AniListDetailsProvider,
    private val autoLinkService: AutoLinkService,
    private val autoLinkPreferences: AutoLinkPreferences,
    private val contentResolver: com.confused.anikuta.core.content.ContentResolver,
    private val contentRepository: com.confused.anikuta.core.content.ContentRepository,
    private val dataCacheRepository: com.confused.anikuta.core.datacache.DataCacheRepository,
    private val downloadManager: com.confused.anikuta.core.download.DownloadManager,
    private val watchProgressStore: com.confused.anikuta.core.watchprogress.WatchProgressStore,
    private val ratingStore: com.confused.anikuta.core.ratings.RatingStore,
    private val playerPreferences: com.confused.anikuta.core.preferences.PlayerPreferences,
    private val genreRepository: com.confused.anikuta.core.content.genre.GenreRepository,
    private val activityTracker: com.confused.anikuta.core.activitytracker.ActivityTracker,
    private val updateEngine: com.confused.anikuta.core.updates.UpdateEngine,
    // D-223: Cover color extractor for adaptive theming.
    private val coverColorExtractor: com.confused.anikuta.core.designsystem.color.CoverColorExtractor? = null,
    // D-225: Reverse auto-link service (AniList → extensions).
    private val reverseAutoLinkService: com.confused.anikuta.core.smartmatcher.ReverseAutoLinkService? = null,
) : ViewModel() {

    companion object {
        private const val TAG = "Anikuta:Feature:Details"
        private const val KEY_SOURCE_LINK_PREFIX = "details_source_link:"
    }

    // D-192 Phase 5: Load generation counter — prevents stale-state flash.
    // Incremented on every load. Async blocks capture the generation at start
    // + check before writing state. If the generation changed, the async result
    // is from a previous content load → discarded (no flash of old data).
    private var loadGeneration = 0

    private val _state = MutableStateFlow<DetailsState>(DetailsState.Loading)
    val state: StateFlow<DetailsState> = _state.asStateFlow()

    /**
     * D-223: The per-anime accent color (ARGB Int) extracted from the cover image.
     * Null = not yet extracted or extraction failed → use the default app accent.
     * The UI observes this + wraps the screen in AnikutaTheme(accentSeed = Color(argb))
     * when adaptive colors are enabled.
     */
    private val _coverAccent = MutableStateFlow<Int?>(null)
    val coverAccent: StateFlow<Int?> = _coverAccent.asStateFlow()

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

    /**
     * D-225c: Reverse auto-link state — tracks the AniList → extensions search
     * lifecycle. Drives the [AutoLinkPopup] so the user sees live feedback
     * ("Searching extensions…" → "Linked to {source}" / "No source found").
     */
    private val _reverseAutoLinkState = MutableStateFlow<ReverseAutoLinkState>(ReverseAutoLinkState.Idle)
    val reverseAutoLinkState: StateFlow<ReverseAutoLinkState> = _reverseAutoLinkState.asStateFlow()

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

    /** Library state — whether the current anime is in the user's library. */
    private val _isInLibrary = MutableStateFlow(false)
    val isInLibrary: StateFlow<Boolean> = _isInLibrary.asStateFlow()

    /** The contentId of the current anime (for logging/debugging). */
    private val _contentId = MutableStateFlow("")
    val contentId: StateFlow<String> = _contentId.asStateFlow()

    /**
     * D.6: Per-episode download states — collected from [DownloadManager.episodeDownloadStates]
     * + mapped to the sealed [EpisodeDownloadState] (NOT the core typealias — that one
     * is `Pair<DownloadStatus, Int>`). The mapping is one-way: core → feature.
     *
     * Key: `"$mainId|$episodeKey"`.
     */
    val downloadStates: StateFlow<Map<String, EpisodeDownloadState>> =
        downloadManager.episodeDownloadStates
            .map { coreMap ->
                // D.FIX: Also check the downloaded_episode DB table for episodes that
                // were completed + auto-cleared (no longer in the active queue).
                // The queue auto-clears COMPLETED tasks after 10s — without this check,
                // those episodes would show as NotDownloaded instead of Downloaded.
                val result = mutableMapOf<String, EpisodeDownloadState>()
                // 1. Map all active queue tasks.
                coreMap.forEach { (key, coreState) ->
                    val (status, progress) = coreState
                    result[key] = when (status) {
                        com.confused.anikuta.core.download.DownloadStatus.QUEUED ->
                            EpisodeDownloadState.Queued
                        com.confused.anikuta.core.download.DownloadStatus.DOWNLOADING ->
                            EpisodeDownloadState.Downloading(progress)
                        com.confused.anikuta.core.download.DownloadStatus.RETRYING ->
                            EpisodeDownloadState.Retrying
                        com.confused.anikuta.core.download.DownloadStatus.PAUSED ->
                            EpisodeDownloadState.Paused
                        com.confused.anikuta.core.download.DownloadStatus.ERROR ->
                            EpisodeDownloadState.Error(null)
                        com.confused.anikuta.core.download.DownloadStatus.COMPLETED ->
                            EpisodeDownloadState.Downloaded
                        com.confused.anikuta.core.download.DownloadStatus.CANCELLED ->
                            EpisodeDownloadState.NotDownloaded
                    }
                }
                // 2. For episodes NOT in the queue, check if they're downloaded.
                //    This covers completed+auto-cleared episodes.
                val mainId = currentMainId ?: return@map result
                val episodeState = _episodeState.value
                if (episodeState is EpisodeState.Loaded) {
                    for (episode in episodeState.episodes) {
                        val key = "$mainId|${episode.url}"
                        if (key !in result) {
                            if (downloadManager.isEpisodeDownloaded(mainId, episode.url)) {
                                result[key] = EpisodeDownloadState.Downloaded
                            }
                        }
                    }
                }
                result
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyMap(),
            )

    private var currentAnimeId: Int = 0
    // D.FIX: Made internal (was private) so DetailsScreen can read it for offline playback.
    internal var currentMainId: String? = null

    // ── Phase WP: Watch progress for the current anime's episodes ──
    // Observe watch_progress by mainId. Emits a map keyed by episode_key for O(1) lookup
    // in the episode list. Reactive — updates live when the player saves progress.
    private val _mainIdFlow = MutableStateFlow<String?>(null)
    val watchProgress: StateFlow<Map<String, com.confused.anikuta.core.watchprogress.WatchProgress>> =
        _mainIdFlow
            .flatMapLatest { mainId ->
                if (mainId != null) {
                    watchProgressStore.observeByMainId(mainId)
                } else {
                    kotlinx.coroutines.flow.flowOf(emptyList())
                }
            }
            .map { list: List<com.confused.anikuta.core.watchprogress.WatchProgress> ->
                list.associateBy { it.episodeKey }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Phase WP: Toggle the watched state of an episode (swipe-to-toggle). */
    fun toggleWatched(episodeKey: String) {
        viewModelScope.launch {
            runCatching {
                watchProgressStore.toggleWatched(episodeKey)
                Logger.i(TAG) { "toggleWatched: episodeKey=$episodeKey" }
            }.onFailure { e ->
                Logger.e(TAG, e) { "toggleWatched failed: ${e.message}" }
            }
        }
    }

    // ── Phase 4: Per-anime user rating ──
    // Reactive: observes the user_rating table for the current anime.
    // Rating scale: 0-100 (backend) / 0-10 stars (UI, each star = 10 points).
    val animeRating: StateFlow<Int?> =
        _mainIdFlow
            .flatMapLatest { mainId ->
                if (mainId != null) {
                    ratingStore.observeAnimeRating(mainId)
                } else {
                    kotlinx.coroutines.flow.flowOf(null)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Phase 4: Set the per-anime rating. [stars] is 0-10 (multiplied by 10 for the 0-100 backend). */
    fun setAnimeRating(stars: Int) {
        val mid = currentMainId ?: return
        viewModelScope.launch {
            runCatching {
                if (stars <= 0) {
                    ratingStore.deleteAnimeRating(mid)
                } else {
                    ratingStore.setAnimeRating(mid, stars * 10)
                }
                Logger.i(TAG) { "setAnimeRating: stars=$stars → rating=${stars * 10}" }
                activityTracker.track(
                    eventType = com.confused.anikuta.core.activitytracker.ActivityEventType.RATING,
                    contentKey = mid,
                    route = "details",
                    contentType = "anime",
                    payload = stars.toString(),
                )
            }.onFailure { e ->
                Logger.e(TAG, e) { "setAnimeRating failed: ${e.message}" }
            }
        }
    }

    // ── Phase 2: Auto-select video for playback ──
    // When autoSelectVideo is ON, tries to auto-pick the best video using the
    // PlayerPreferences (server, audio, quality, fallback). Returns the picked
    // ResolvedVideo, or null if auto-select is off / no match found (→ show picker).

    /** Whether auto-select video is enabled (for the UI to decide flow). */
    fun isAutoSelectEnabled(): Boolean = playerPreferences.autoSelectVideo.get()

    fun tryAutoSelect(success: ResolverState.Success? = null): com.confused.anikuta.core.videoresolver.ResolvedVideo? {
        if (!playerPreferences.autoSelectVideo.get()) {
            Logger.w(TAG) { "tryAutoSelect: autoSelectVideo is OFF" }
            return null
        }
        // Use the passed-in Success state (from the LaunchedEffect) to avoid stale reads.
        val successState = success ?: (resolverState.value as? ResolverState.Success)
        if (successState == null) {
            Logger.w(TAG) { "tryAutoSelect: resolverState is not Success (actual: ${resolverState.value::class.simpleName})" }
            return null
        }
        if (successState.servers.isEmpty()) {
            Logger.w(TAG) { "tryAutoSelect: servers list is empty (videos: ${successState.videos.size})" }
            return null
        }
        Logger.i(TAG) { "tryAutoSelect: ${successState.servers.size} servers, ${successState.videos.size} videos — running engine..." }

        return try {
            val selection = com.confused.anikuta.core.download.AutoDownloadEngine.selectBestVideo(
                servers = successState.servers,
                dimensionPriority = playerPreferences.dimensionPriority.get()
                    .map { com.confused.anikuta.core.download.AutoDownloadEngine.PreferenceDimension.valueOf(it) },
                preferredAudio = playerPreferences.preferredAudio.get(),
                preferredQualities = playerPreferences.preferredQualities.get(),
                preferredServers = playerPreferences.preferredServers.get(),
                audioFallback = com.confused.anikuta.core.download.AutoDownloadEngine.FallbackStrategy
                    .valueOf(playerPreferences.audioFallback.get()),
                qualityFallback = com.confused.anikuta.core.download.AutoDownloadEngine.FallbackStrategy
                    .valueOf(playerPreferences.qualityFallback.get()),
                serverFallback = com.confused.anikuta.core.download.AutoDownloadEngine.FallbackStrategy
                    .valueOf(playerPreferences.serverFallback.get()),
                globalFallback = com.confused.anikuta.core.download.AutoDownloadEngine.GlobalFallback
                    .valueOf(playerPreferences.globalFallback.get()),
            )
            when (selection) {
                is com.confused.anikuta.core.download.AutoDownloadEngine.Selection.Selected -> {
                    val v = selection.candidate.video
                    Logger.i(TAG) { "Auto-select: picked ${v.quality} on ${selection.candidate.server} (${selection.candidate.audio})" }
                    com.confused.anikuta.core.videoresolver.ResolvedVideo(
                        url = v.url,
                        quality = v.quality,
                        directUrl = v.directUrl,
                        headers = v.videoHeaders ?: "",
                        subtitleTracks = v.subtitleTracks,
                        audioTracks = v.audioTracks,
                    )
                }
                is com.confused.anikuta.core.download.AutoDownloadEngine.Selection.NoCandidates,
                is com.confused.anikuta.core.download.AutoDownloadEngine.Selection.DoNotDownload -> {
                    Logger.i(TAG) { "Auto-select: no perfect match — falling back to picker" }
                    null
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Auto-select failed: ${e.message}" }
            null
        }
    }

    // ── D-134: Original data bases (for data-source switching) ──
    // The bug: merging with ANILIST priority overwrites extension fields.
    // Switching back to EXTENSION priority can't recover the original extension
    // data because it was overwritten.
    // Fix: keep the ORIGINAL extension data + ORIGINAL AniList data as separate
    // fields. The displayed UnifiedAnime is always computed by merging the two
    // bases with the current priority. Switching priority never loses data.

    /** The original extension data (null for AniList-only entries). */
    private var extensionBase: UnifiedAnime? = null

    /** The original AniList data (null for extension-only entries, set after linking). */
    private var anilistBase: UnifiedAnime? = null

    /**
     * Re-merge [extensionBase] + [anilistBase] with the given [priority].
     * Updates [_state] with the merged result.
     *
     * - If only [extensionBase] exists → display it as-is (extension-only).
     * - If only [anilistBase] exists → display it as-is (AniList-only).
     * - If both exist → merge by priority:
     *   - ANILIST: AniList values win; extension fills nulls.
     *   - EXTENSION: Extension values win; AniList fills nulls.
     *
     * Identity fields (sourceId, sourceName, animeUrl, anilistId, entryMode) are
     * always preserved from whichever base has them — they're NOT subject to priority.
     */
    private fun remergeBases(priority: com.confused.anikuta.core.common.model.DataSourcePriority) {
        val ext = extensionBase
        val al = anilistBase
        if (ext == null && al == null) {
            Logger.w(TAG) { "remergeBases: both bases null — nothing to display" }
            return
        }
        if (ext == null) {
            // AniList-only
            _state.value = DetailsState.Success(al!!.copy(dataSourcePriority = priority))
            return
        }
        if (al == null) {
            // Extension-only
            _state.value = DetailsState.Success(ext.copy(dataSourcePriority = priority))
            return
        }
        // Both exist — merge by priority.
        // D-139: STRICT switching — the primary source's values are used AS-IS.
        // No fallback to secondary. When the user picks "Extension", they see ONLY
        // extension data (even if some fields are null). When they pick "AniList",
        // they see ONLY AniList data. This is the expected behavior — the user
        // explicitly chose which source to display.
        val (primary, _) = if (priority == com.confused.anikuta.core.common.model.DataSourcePriority.ANILIST) {
            al to ext
        } else {
            ext to al
        }
        val merged = primary.copy(
            // Identity fields — always from whichever base has them (NOT subject to priority).
            anilistId = al.anilistId ?: ext.anilistId,
            sourceId = ext.sourceId ?: al.sourceId,
            sourceName = ext.sourceName ?: al.sourceName,
            animeUrl = ext.animeUrl ?: al.animeUrl,
            entryMode = ext.entryMode, // Entry mode stays from extension (how the user opened it).
            dataSourcePriority = priority,
            // Metadata fields — STRICT: primary values ONLY, no fallback to secondary.
            description = primary.description,
            genres = primary.genres,
            status = primary.status,
            episodes = primary.episodes,
            averageScore = primary.averageScore,
            season = primary.season,
            seasonYear = primary.seasonYear,
            bannerUrl = primary.bannerUrl,
            idMal = primary.idMal,
            coverUrl = primary.coverUrl,
        )
        _state.value = DetailsState.Success(merged)
        Logger.d(TAG) { "remergeBases: priority=$priority, merged ${merged.displayName}" }
    }

    // ── Load from AniList (existing flow) ──

    fun loadFromAniList(animeId: Int) {
        currentAnimeId = animeId
        // D-192 Phase 5: Increment generation — async blocks from previous loads will be discarded.
        loadGeneration++
        val gen = loadGeneration
        // CRITICAL: Reset ALL state when loading a new anime (D-131).
        _state.value = DetailsState.Loading
        _autoLinkState.value = AutoLinkState.Idle
        _reverseAutoLinkState.value = ReverseAutoLinkState.Idle
        _showManualLinkSheet.value = false
        _anilistSearchState.value = AniListSearchState.Idle
        _linkedSource.value = null
        _episodeState.value = EpisodeState.Idle
        _episodeMetadata.value = emptyMap()
        _resolverState.value = ResolverState.Idle
        _resolvedVideosKey.value = ""
        _manualSearchState.value = ManualSearchState.Idle
        _isInLibrary.value = false
        _contentId.value = ""
        currentMainId = null; _mainIdFlow.value = null
        // D-134: Reset the data bases.
        extensionBase = null
        anilistBase = null

        // D-192 Phase 5: Synchronous source-link pre-read (fixes "no source linked" race — #21).
        // Read the saved source link from PreferenceStore SYNCHRONOUSLY (SharedPreferences
        // is in-memory cached) so the UI shows the correct linked source immediately —
        // no async gap where "No Source" is shown despite being linked.
        val savedLink = preferenceStore.getString(KEY_SOURCE_LINK_PREFIX + animeId, "")
        if (savedLink.isNotBlank()) {
            val parts = savedLink.split(":", limit = 2)
            if (parts.size == 2) {
                val sourceId = parts[0].toLongOrNull()
                if (sourceId != null) {
                    _linkedSource.value = LinkedSource(sourceId, "", parts[1])
                }
            }
        }

        viewModelScope.launch {
            try {
                // D.1: Check the local data cache first — if cached, display instantly.
                // D-198: anime_metadata_cache absorbed into content_details (data-source axis).
                val cachedMainId = contentRepository.getMainEntryByAniListId(animeId)?.mainId
                var cachedMeta: com.confused.anikuta.core.content.ContentDetails? = null
                if (cachedMainId != null) {
                    cachedMeta = contentRepository.getContentDetails(cachedMainId)
                    if (cachedMeta != null && cachedMeta.hasDataSourceLink) {
                        Logger.i(TAG) { "Loaded from cache (content_details): $animeId" }
                        anilistBase = com.confused.anikuta.core.common.model.UnifiedAnime(
                            title = contentRepository.getMainEntryByMainId(cachedMainId)?.title ?: "",
                            coverUrl = cachedMeta.dataCoverUrl,
                            bannerUrl = cachedMeta.dataBannerUrl,
                            description = cachedMeta.dataSynopsis,
                            genres = cachedMeta.dataGenres?.split(", ")?.filter { it.isNotBlank() } ?: emptyList(),
                            status = cachedMeta.dataStatus,
                            episodes = cachedMeta.dataEpisodes?.toInt(),
                            averageScore = cachedMeta.dataScore?.toInt(),
                            season = cachedMeta.dataSeason,
                            seasonYear = cachedMeta.dataSeasonYear?.toInt(),
                            anilistId = animeId,
                            entryMode = com.confused.anikuta.core.common.model.EntryMode.ANILIST,
                        )
                        remergeBases(com.confused.anikuta.core.common.model.DataSourcePriority.ANILIST)
                        currentMainId = cachedMainId; _mainIdFlow.value = cachedMainId
                        refreshContentAndLibraryStatus(cachedMainId)
                        // D-223: Trigger cover color extraction for the AniList cache-first path.
                        triggerCoverColorExtraction(cachedMainId, cachedMeta.dataCoverUrl)
                        loadLinkedSource(animeId)
                    }
                }

                // D-146: If we already displayed from cache, DON'T re-fetch from
                // network every time. Only fetch if:
                // 1. No cache existed (first time opening this anime).
                // 2. The user manually refreshes (via three-dot menu or pull-to-refresh).
                if (cachedMainId == null || cachedMeta == null) {
                    try {
                    // No cache — fetch from network.
                    val anime = anilistApi.fetchAnimeDetails(animeId)
                    Logger.i(TAG) { "Loaded AniList details for $animeId (network)" }
                    anilistBase = anime.toUnifiedAnime()
                    remergeBases(com.confused.anikuta.core.common.model.DataSourcePriority.ANILIST)

                    // Phase C: Resolve/create content record + check library status.
                    resolveContentForAniList(animeId, anime.displayName, anime)

                    // D.1: Cache the fetched metadata locally.
                    // D-198: anime_metadata_cache → content_details (data-source axis).
                    val mainIdForCache = currentMainId
                    if (mainIdForCache != null) {
                        contentRepository.updateDataSourceAxis(
                            com.confused.anikuta.core.content.ContentDetails(
                                mainId = mainIdForCache,
                                dataSourceType = "anilist",
                                dataSourceRefId = animeId.toString(),
                                dataScore = anime.averageScore?.toLong(),
                                dataEpisodes = anime.episodes?.toLong(),
                                dataSeason = anime.season,
                                dataSeasonYear = anime.seasonYear?.toLong(),
                                dataStatus = anime.status,
                                dataGenres = anime.genres?.joinToString(", "),
                                dataSynopsis = anime.description,
                                dataCoverUrl = anime.coverUrl,
                                dataBannerUrl = anime.bannerImage,
                                dataUpdatedAt = System.currentTimeMillis(),
                            ),
                        )
                        Logger.i(TAG) { "Cached anime metadata for mainId=$mainIdForCache" }
                    }
                    } catch (netErr: Exception) {
                        // D-146: Network failed — if we have cached data, show it.
                        // Don't show an error if the cache already displayed data.
                        if (_state.value !is DetailsState.Success) {
                            Logger.w(TAG) { "Network failed, no cache available: ${netErr.message}" }
                            _state.value = DetailsState.Error(netErr.message ?: "Unknown error")
                        } else {
                            Logger.w(TAG) { "Network failed but cache is displayed: ${netErr.message}" }
                        }
                    }
                } // end if (no cache — fetch from network)

                // Check for a persisted source link (if not already loaded from cache).
                if (cachedMainId == null) {
                    loadLinkedSource(animeId)
                }

                // D-225: Reverse auto-link — if no source is linked, search extensions.
                val savedLink = preferenceStore.getString(KEY_SOURCE_LINK_PREFIX + animeId, "")
                if (savedLink.isBlank() && reverseAutoLinkService != null) {
                    val anime = anilistBase
                    if (anime != null) {
                        Logger.i(TAG) { "D-225: Reverse auto-link — no source linked, searching extensions..." }
                        // D-225c: Show the popup immediately so the user sees "Searching…".
                        _reverseAutoLinkState.value = ReverseAutoLinkState.Searching
                        viewModelScope.launch {
                            try {
                                val result = reverseAutoLinkService.attemptReverseAutoLink(
                                    anilistTitle = anime.displayName,
                                    anilistYear = anime.seasonYear,
                                )
                                when (result) {
                                    is com.confused.anikuta.core.smartmatcher.ReverseAutoLinkResult.Matched -> {
                                        Logger.i(TAG) {
                                            "D-225: Reverse auto-link MATCHED: source=${result.source.name}, " +
                                                "anime='${result.sAnime.title}', score=${result.score}"
                                        }
                                        // Reuse the existing linkSource() — it does everything:
                                        // persist KEY_SOURCE_LINK_PREFIX, linkExtensionToExisting,
                                        // set extensionBase, fetch episodes, cache forward link.
                                        linkSource(result.source, result.sAnime)
                                        // D-225c: popup confirms the match.
                                        _reverseAutoLinkState.value = ReverseAutoLinkState.Matched(
                                            sourceName = result.source.name,
                                            animeTitle = result.sAnime.title,
                                            score = result.score,
                                        )
                                    }
                                    is com.confused.anikuta.core.smartmatcher.ReverseAutoLinkResult.NoMatch -> {
                                        Logger.i(TAG) {
                                            "D-225: Reverse auto-link NO MATCH (bestScore=${result.bestScore})"
                                        }
                                        // D-225c: popup offers a "Link manually" action.
                                        _reverseAutoLinkState.value = ReverseAutoLinkState.NoMatch(
                                            bestScore = result.bestScore,
                                            searchedTitle = result.searchedTitle,
                                        )
                                    }
                                    is com.confused.anikuta.core.smartmatcher.ReverseAutoLinkResult.Skipped -> {
                                        Logger.d(TAG) { "D-225: Reverse auto-link skipped: ${result.reason}" }
                                        // Silent — don't bother the user when the feature is off.
                                        _reverseAutoLinkState.value = ReverseAutoLinkState.Idle
                                    }
                                    is com.confused.anikuta.core.smartmatcher.ReverseAutoLinkResult.Error -> {
                                        Logger.e(TAG) { "D-225: Reverse auto-link error: ${result.message}" }
                                        _reverseAutoLinkState.value = ReverseAutoLinkState.Error(result.message)
                                    }
                                }
                            } catch (e: Exception) {
                                Logger.e(TAG, e) { "D-225: Reverse auto-link failed: ${e.message}" }
                                _reverseAutoLinkState.value = ReverseAutoLinkState.Error(
                                    e.message ?: "Unknown error"
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, e) { "Failed: ${e.message}" }
                _state.value = DetailsState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * D.3: Refresh stage 1 — refresh episodes list only (from extension source).
     * Only fetches the episode list from the extension. Does NOT touch metadata.
     * If new episodes are found, auto-fetch their metadata.
     */
    fun refreshEpisodesList() {
        val anime = (_state.value as? DetailsState.Success)?.anime ?: return
        val sourceId = anime.sourceId ?: run {
            Logger.w(TAG) { "refreshEpisodesList: no sourceId" }
            return
        }
        val animeUrl = anime.animeUrl ?: run {
            Logger.w(TAG) { "refreshEpisodesList: no animeUrl" }
            return
        }
        Logger.i(TAG) { "D.3 Stage 1: Refreshing episodes list for ${anime.displayName}" }
        val source = extensionManager.getSource(sourceId) as? AnimeCatalogueSource ?: run {
            Logger.w(TAG) { "refreshEpisodesList: source not found" }
            return
        }
        val animeTitle = anime.displayName
        viewModelScope.launch {
            try {
                val sAnime = eu.kanade.tachiyomi.animesource.model.SAnime.create().apply {
                    url = animeUrl
                    title = animeTitle
                    initialized = false
                }
                val episodes = withContext(Dispatchers.IO) { source.getEpisodeList(sAnime) }
                Logger.i(TAG) { "D.3 Stage 1: Fetched ${episodes.size} fresh episodes" }
                val sorted = episodes.sortedByDescending { it.episode_number }
                _episodeState.value = if (episodes.isEmpty()) EpisodeState.Empty else EpisodeState.Loaded(sorted)

                // D.FIX: Update the cache with fresh episodes so the next open
                // shows the latest data (not stale cache). Include episodeUrl!
                val mainId = currentMainId
                if (mainId != null && episodes.isNotEmpty()) {
                    val now = System.currentTimeMillis()
                    val cachedList = episodes.map { ep ->
                        com.confused.anikuta.core.datacache.CachedEpisodeMetadata(
                            mainId = mainId,
                            episodeNumber = ep.episode_number,
                            title = ep.name,
                            description = ep.summary,
                            thumbnailUrl = null,
                            airDate = if (ep.date_upload > 0) ep.date_upload else null,
                            fetchedAt = now,
                            episodeUrl = ep.url,
                            sourceName = ep.name,
                            scanlator = ep.scanlator,
                        )
                    }
                    dataCacheRepository.upsertEpisodeMetadataBatch(cachedList)
                    Logger.i(TAG) { "D.3 Stage 1: Updated episode cache with ${cachedList.size} fresh episodes (incl. episodeUrl)" }
                }

                // If we have an anilistId, auto-fetch episode metadata + write back to cache.
                // D.FIX: The old code only updated _episodeMetadata in memory but never
                // wrote the enriched metadata back to the cache. This meant the cache
                // retained sparse extension data (from the upsert above), and on the
                // next open, the cache restore would show sparse data — no rich titles,
                // descriptions, or thumbnails.
                val anilistId = anime.anilistId
                if (anilistId != null && anilistId > 0 && episodes.isNotEmpty()) {
                    val malId = (_state.value as? DetailsState.Success)?.anime?.idMal
                    val episodesForCache = episodes // capture for inner lambda
                    val mainIdForCache = mainId // capture for inner lambda
                    viewModelScope.launch {
                        try {
                        val metadata = episodeMetadataEngine.fetchEpisodeMetadata(
                            anilistId = anilistId,
                            malId = malId,
                            episodeCount = episodesForCache.size,
                        )
                        if (metadata.isNotEmpty()) {
                            _episodeMetadata.value = metadata
                            Logger.i(TAG) { "D.3 Stage 1: Auto-fetched ${metadata.size} episode metadata entries" }

                            // Write enriched metadata back to the cache, preserving
                            // episodeUrl from the extension episodes.
                            if (mainIdForCache != null) {
                                val now = System.currentTimeMillis()
                                val epNumToUrl = episodesForCache.associate { it.episode_number.toInt() to it.url }
                                val epNumToSourceName = episodesForCache.associate { it.episode_number.toInt() to it.name }
                                val epNumToScanlator = episodesForCache.associate { it.episode_number.toInt() to (it.scanlator ?: "") }
                                val enrichedCache = metadata.entries.map { (epNum, meta) ->
                                    com.confused.anikuta.core.datacache.CachedEpisodeMetadata(
                                        mainId = mainIdForCache,
                                        episodeNumber = epNum.toFloat(),
                                        title = meta.title,
                                        description = meta.description,
                                        thumbnailUrl = meta.thumbnailUrl,
                                        airDate = meta.airDate,
                                        fetchedAt = now,
                                        episodeUrl = epNumToUrl[epNum],
                                        sourceName = epNumToSourceName[epNum],
                                        scanlator = epNumToScanlator[epNum]?.takeIf { it.isNotEmpty() },
                                        isFiller = meta.isFiller,
                                        isRecap = meta.isRecap,
                                        titleJapanese = meta.titleJapanese,
                                        titleRomaji = meta.titleRomaji,
                                        runtime = meta.runtime,
                                        seasonNumber = meta.seasonNumber,
                                        episodeNumberInSeason = meta.episodeNumberInSeason,
                                        score = meta.score,
                                    )
                                }
                                dataCacheRepository.upsertEpisodeMetadataBatch(enrichedCache)
                                Logger.i(TAG) { "D.3 Stage 1: Wrote ${enrichedCache.size} enriched metadata entries to cache (episodeUrl + sourceName + scanlator preserved)" }
                                }
                            } else {
                                Logger.w(TAG) { "D.3 Stage 1: Episode metadata fetch returned empty — keeping existing metadata" }
                            }
                        } catch (e: Exception) {
                            Logger.w(TAG) { "D.3 Stage 1: Episode metadata fetch failed: ${e.message}" }
                        }
                    }
                }
            } catch (e: Throwable) {
                Logger.e(TAG, e) { "D.3 Stage 1: Episodes refresh failed: ${e.message}" }
            }
        }
    }

    /**
     * D.3: Refresh stage 2 — refresh metadata only (from data source).
     * Only fetches metadata (synopsis, score, etc.) from AniList/extension.
     * Does NOT touch the episodes list.
     */
    fun refreshMetadata() {
        val anime = (_state.value as? DetailsState.Success)?.anime ?: return
        Logger.i(TAG) { "D.3 Stage 2: Refreshing metadata for ${anime.displayName}" }

        if (anime.anilistId != null) {
            val anilistId = anime.anilistId!!
            viewModelScope.launch {
                try {
                    val fresh = anilistApi.fetchAnimeDetails(anilistId)
                    anilistBase = fresh.toUnifiedAnime()
                    remergeBases(
                        (_state.value as? DetailsState.Success)?.anime?.dataSourcePriority
                            ?: com.confused.anikuta.core.common.model.DataSourcePriority.ANILIST
                    )
                    // D.1: Update the cache.
                    // D-198: anime_metadata_cache → content_details (data-source axis).
                    val mainId = currentMainId
                    if (mainId != null) {
                        contentRepository.updateDataSourceAxis(
                            com.confused.anikuta.core.content.ContentDetails(
                                mainId = mainId,
                                dataSourceType = "anilist",
                                dataSourceRefId = anilistId.toString(),
                                dataScore = fresh.averageScore?.toLong(),
                                dataEpisodes = fresh.episodes?.toLong(),
                                dataSeason = fresh.season,
                                dataSeasonYear = fresh.seasonYear?.toLong(),
                                dataStatus = fresh.status,
                                dataGenres = fresh.genres?.joinToString(", "),
                                dataSynopsis = fresh.description,
                                dataCoverUrl = fresh.coverUrl,
                                dataBannerUrl = fresh.bannerImage,
                                dataUpdatedAt = System.currentTimeMillis(),
                            ),
                        )
                    }
                    Logger.i(TAG) { "D.3 Stage 2: Refreshed AniList metadata" }
                } catch (e: Exception) {
                    Logger.e(TAG, e) { "D.3 Stage 2: Metadata refresh failed: ${e.message}" }
                }
            }
        } else if (anime.sourceId != null && anime.animeUrl != null) {
            val sourceId = anime.sourceId!!
            val animeUrl = anime.animeUrl!!
            viewModelScope.launch {
                try {
                    val enriched = extensionProvider.fetchFromExtension(
                        sourceId, animeUrl, anime.displayName, anime.coverUrl,
                    )
                    if (enriched != null) {
                        extensionBase = enriched
                        remergeBases(
                            (_state.value as? DetailsState.Success)?.anime?.dataSourcePriority
                                ?: com.confused.anikuta.core.common.model.DataSourcePriority.EXTENSION
                        )
                    }
                    Logger.i(TAG) { "D.3 Stage 2: Refreshed extension metadata" }
                } catch (e: Exception) {
                    Logger.e(TAG, e) { "D.3 Stage 2: Extension metadata refresh failed: ${e.message}" }
                }
            }
        }
    }

    /**
     * D.3: Refresh stage 3 — refresh ALL (episodes + metadata + cover images).
     * This is the full refresh. Also called by the three-dot menu "Refresh" button.
     */
    fun refreshAll() {
        Logger.i(TAG) { "D.3 Stage 3: Full refresh" }
        _isRefreshing.value = true
        viewModelScope.launch {
            refreshMetadata()
            refreshEpisodesList()
            kotlinx.coroutines.delay(500) // Brief delay so the spinner is visible
            _isRefreshing.value = false
            Logger.i(TAG) { "D.3 Stage 3: Refresh complete" }
        }
    }

    /** Refresh state for the D.3 multi-stage refresh UI. */
    private val _refreshState = MutableStateFlow<RefreshState>(RefreshState.Idle)
    val refreshState: StateFlow<RefreshState> = _refreshState.asStateFlow()

    /** D-146: Whether a refresh is in progress (for visual feedback). */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Called by the DetailsScreen when the user scrolls past a refresh threshold. */
    fun setRefreshStage(stage: RefreshStage) {
        _refreshState.value = RefreshState.StageReached(stage)
    }

    /** Called when the user releases at a refresh threshold. */
    fun executeRefresh(stage: RefreshStage) {
        _refreshState.value = RefreshState.Refreshing(stage)
        when (stage) {
            RefreshStage.EPISODES -> {
                refreshEpisodesList()
                _refreshState.value = RefreshState.Idle
            }
            RefreshStage.METADATA -> {
                refreshMetadata()
                _refreshState.value = RefreshState.Idle
            }
            RefreshStage.ALL -> {
                refreshAll()
                _refreshState.value = RefreshState.Idle
            }
        }
    }

    fun clearRefreshState() {
        _refreshState.value = RefreshState.Idle
    }

    /**
     * D-141: Refresh the current anime's data.
     * Delegates to [refreshAll] (stage 3 — full refresh).
     */
    fun refresh() = refreshAll()

    // ── Load from Extension (Phase A + Phase B auto-link) ──

    fun loadFromExtension(sourceId: Long, animeUrl: String, title: String, thumbnailUrl: String?) {
        currentAnimeId = 0 // No AniList ID yet — will be set by auto-link if it matches.
        // D-192 Phase 5: Increment generation — async blocks from previous loads will be discarded.
        loadGeneration++
        // CRITICAL: Reset ALL state when loading a new anime (D-131).
        _state.value = DetailsState.Loading
        _autoLinkState.value = AutoLinkState.Idle
        _reverseAutoLinkState.value = ReverseAutoLinkState.Idle
        _showManualLinkSheet.value = false
        _anilistSearchState.value = AniListSearchState.Idle
        _linkedSource.value = null
        _episodeState.value = EpisodeState.Idle
        _episodeMetadata.value = emptyMap()
        _resolverState.value = ResolverState.Idle
        _resolvedVideosKey.value = ""
        _manualSearchState.value = ManualSearchState.Idle
        _isInLibrary.value = false
        _contentId.value = ""
        currentMainId = null; _mainIdFlow.value = null
        // D-134: Reset the data bases.
        extensionBase = null
        anilistBase = null
        viewModelScope.launch {
            try {
                // D-210 FIX: Cache-first for instant open (mirrors loadFromAniList's
                // pattern at lines 472-503). If we already have ext_* data in
                // content_details, load from DB instantly via tryCachedExtensionData
                // (which also calls fetchEpisodes — itself cache-first + bg-refresh).
                // Then fire-and-forget a SILENT background refresh — only updates the
                // UI if the data actually changed. This eliminates the ~1s loading
                // delay the user saw when opening extension-saved entries from Library.
                val existingContent = contentRepository.getMainEntryByExtension(sourceId, animeUrl)
                val cachedDetails = existingContent?.let {
                    contentRepository.getContentDetails(it.mainId)
                }
                val hasCachedExtData = cachedDetails != null &&
                    cachedDetails.hasExtensionLink &&
                    cachedDetails.extUpdatedAt != null

                if (hasCachedExtData && existingContent != null) {
                    // Instant DB load — no network call, no loading spinner.
                    Logger.i(TAG) { "Opening from cache (ext_* data exists): $title" }
                    tryCachedExtensionData(sourceId, animeUrl, title, thumbnailUrl)

                    // Silent background refresh — updates UI only if data changed.
                    // Doesn't block the user — they see the cached data immediately.
                    viewModelScope.launch {
                        try {
                            val refreshed = extensionProvider.fetchFromExtension(
                                sourceId, animeUrl, title,
                                cachedDetails.extThumbnailUrl ?: thumbnailUrl,
                            )
                            if (refreshed != null) {
                                extensionBase = refreshed
                                remergeBases(
                                    com.confused.anikuta.core.common.model.DataSourcePriority.EXTENSION
                                )
                                // Persist the refreshed ext_* axis (silent).
                                resolveContentForExtension(sourceId, animeUrl, title, refreshed)
                            }
                        } catch (e: Exception) {
                            Logger.w(TAG) { "Background extension refresh failed: ${e.message}" }
                        }
                    }
                    // Skip the network-first path below — we already loaded from DB.
                    // performAutoLink needs a non-null UnifiedAnime — capture in a local
                    // val so Kotlin can smart-cast (extensionBase is a mutable var).
                    val extBase = extensionBase
                    if (extBase != null) {
                        performAutoLink(sourceId, animeUrl, extBase)
                    }
                    return@launch
                }

                // D-200: When opening from Library, thumbnailUrl may be null (the
                // LibraryEntry.coverUrl comes from ext_thumbnail_url in DB which may
                // not have been stored yet). Fall back to the DB-stored ext_thumbnail_url
                // before calling fetchFromExtension — so the stub SAnime.thumbnail_url
                // is non-null and the D-199 fallback in ExtensionDetailsProvider works.
                val effectiveThumbnailUrl = thumbnailUrl ?: run {
                    val existingContent = contentRepository.getMainEntryByExtension(sourceId, animeUrl)
                    existingContent?.let {
                        contentRepository.getContentDetails(it.mainId)?.extThumbnailUrl
                    }
                }
                // Use the ExtensionDetailsProvider to fetch full details.
                val unifiedAnime = extensionProvider.fetchFromExtension(sourceId, animeUrl, title, effectiveThumbnailUrl)

                if (unifiedAnime != null) {
                    Logger.i(TAG) { "Loaded extension details: $title from source $sourceId" }
                    extensionBase = unifiedAnime // D-134: store original extension data.
                    remergeBases(com.confused.anikuta.core.common.model.DataSourcePriority.EXTENSION)

                    // Phase C: Resolve/create content record + check library status.
                    // D-142: Pass the unifiedAnime so the extension detail (with coverUrl) is stored.
                    resolveContentForExtension(sourceId, animeUrl, title, unifiedAnime)

                    // Fetch episodes from the extension source directly.
                    fetchEpisodesFromSource(sourceId, animeUrl, title)

                    // ── Phase B: Kick off auto-link (non-blocking) ──
                    performAutoLink(sourceId, animeUrl, unifiedAnime)
                } else {
                    // D-147: Extension fetch returned null — try cached data.
                    tryCachedExtensionData(sourceId, animeUrl, title, thumbnailUrl)
                }
            } catch (e: Exception) {
                // D-147: Network failed (offline) — try cached data.
                Logger.w(TAG) { "Extension fetch failed (offline?): ${e.message}" }
                tryCachedExtensionData(sourceId, animeUrl, title, thumbnailUrl)
            }
        }
    }

    /**
     * D-147: Fallback when the extension fetch fails (offline).
     * Tries to load from the content database (extension_detail + anime_metadata_cache).
     */
    private suspend fun tryCachedExtensionData(
        sourceId: Long,
        animeUrl: String,
        title: String,
        thumbnailUrl: String?,
    ) {
        // Check if we have a content record for this extension entry.
        val existingContent = contentRepository.getMainEntryByExtension(sourceId, animeUrl)
        if (existingContent != null) {
            Logger.i(TAG) { "Found cached content for extension: ${existingContent.title}" }
            currentMainId = existingContent.mainId; _mainIdFlow.value = existingContent.mainId

            // D-198: getExtensionDetail → getContentDetails; build extensionBase from ext_* axis.
            val details = contentRepository.getContentDetails(existingContent.mainId)
            if (details != null && details.hasExtensionLink) {
                extensionBase = com.confused.anikuta.core.common.model.UnifiedAnime(
                    title = existingContent.title,
                    description = details.extDescription,
                    genres = details.extGenres?.split(", ")?.filter { it.isNotBlank() } ?: emptyList(),
                    status = details.extStatus,
                    author = details.extAuthor,
                    artist = details.extArtist,
                    coverUrl = details.extThumbnailUrl ?: thumbnailUrl,
                    sourceId = details.sourceId,
                    sourceName = null,
                    animeUrl = details.animeUrl,
                    entryMode = com.confused.anikuta.core.common.model.EntryMode.EXTENSION,
                )
            } else {
                extensionBase = com.confused.anikuta.core.common.model.UnifiedAnime(
                    title = title,
                    coverUrl = thumbnailUrl,
                    sourceId = sourceId,
                    animeUrl = animeUrl,
                    entryMode = com.confused.anikuta.core.common.model.EntryMode.EXTENSION,
                )
            }
            remergeBases(com.confused.anikuta.core.common.model.DataSourcePriority.EXTENSION)
            refreshContentAndLibraryStatus(existingContent.mainId)

            // D-223: Trigger cover color extraction for the cache-first path too.
            // Without this, anime opened from Library (which takes the cache-first path)
            // would never get the adaptive accent color.
            val cacheCoverUrl = details?.extThumbnailUrl ?: details?.dataCoverUrl ?: thumbnailUrl
            triggerCoverColorExtraction(existingContent.mainId, cacheCoverUrl)

            // Try to load cached episodes.
            val source = extensionManager.getSource(sourceId) as? AnimeCatalogueSource
            if (source != null) {
                _linkedSource.value = LinkedSource(sourceId, source.name, animeUrl)
                fetchEpisodes(source, animeUrl, title)
            } else {
                // Source not available — try cached episodes directly.
                val cachedEpisodes = dataCacheRepository.getEpisodeMetadata(existingContent.mainId)
                if (cachedEpisodes.isNotEmpty()) {
                    val episodes = cachedEpisodes.map { meta ->
                        SEpisode.create().apply {
                            url = meta.episodeUrl ?: animeUrl
                            episode_number = meta.episodeNumber
                            name = meta.sourceName ?: meta.title ?: "Episode ${meta.episodeNumber.toInt()}"
                            date_upload = meta.airDate ?: 0L
                            scanlator = meta.scanlator
                        }
                    }.sortedByDescending { it.episode_number }
                    _episodeState.value = EpisodeState.Loaded(episodes)
                    Logger.i(TAG) { "Loaded ${episodes.size} episodes from cache (offline)" }
                } else {
                    _episodeState.value = EpisodeState.Error("No cached episodes available offline")
                }
            }

            // Check if we also have AniList data cached.
            // D-198: getAniListDetail → getContentDetails; anime_metadata_cache absorbed into data axis.
            // Reuse the `details` variable from above (same mainId, same getContentDetails call).
            if (details != null && details.hasDataSourceLink) {
                val content = contentRepository.getMainEntryByMainId(existingContent.mainId)
                anilistBase = com.confused.anikuta.core.common.model.UnifiedAnime(
                    title = content?.title ?: existingContent.title,
                    coverUrl = details.dataCoverUrl,
                    bannerUrl = details.dataBannerUrl,
                    description = details.dataSynopsis,
                    genres = details.dataGenres?.split(", ")?.filter { it.isNotBlank() } ?: emptyList(),
                    status = details.dataStatus,
                    episodes = details.dataEpisodes?.toInt(),
                    averageScore = details.dataScore?.toInt(),
                    season = details.dataSeason,
                    seasonYear = details.dataSeasonYear?.toInt(),
                    anilistId = details.anilistId,
                    entryMode = com.confused.anikuta.core.common.model.EntryMode.ANILIST,
                )
                remergeBases(
                    (_state.value as? DetailsState.Success)?.anime?.dataSourcePriority
                        ?: com.confused.anikuta.core.common.model.DataSourcePriority.EXTENSION
                )
            }
        } else {
            // No cached data at all — show error.
            Logger.w(TAG) { "No cached data for extension: $sourceId/$animeUrl" }
            _state.value = DetailsState.Error("Failed to load extension details (offline)")
        }
    }

    // ── Phase B: Auto-link ──

    // ── Phase C: Content identity + library ──

    /**
     * Resolve/create a content record for an AniList entry + check library status.
     * Called from [loadFromAniList].
     */
    private suspend fun resolveContentForAniList(
        anilistId: Int,
        title: String,
        anime: com.confused.anikuta.core.anilist.model.AniListAnime,
    ) {
        try {
            // D-198: AniListDetail → ContentDetails (data-source axis).
            val detail = com.confused.anikuta.core.content.ContentDetails(
                mainId = "", // Will be set by resolver.
                dataSourceType = "anilist",
                dataSourceRefId = anilistId.toString(),
                dataScore = anime.averageScore?.toLong(),
                dataEpisodes = anime.episodes?.toLong(),
                dataSeason = anime.season,
                dataSeasonYear = anime.seasonYear?.toLong(),
                dataStatus = anime.status,
                dataGenres = anime.genres?.joinToString(", "),
                dataSynopsis = anime.description,
                dataCoverUrl = anime.coverUrl,
                dataBannerUrl = anime.bannerImage,
                dataUpdatedAt = System.currentTimeMillis(),
            )
            val mainId = contentResolver.resolveOrCreateForAniList(anilistId, title, detail)
            currentMainId = mainId; _mainIdFlow.value = mainId
            refreshContentAndLibraryStatus(mainId)
            // D-223: Trigger cover color extraction for adaptive theming.
            triggerCoverColorExtraction(mainId, detail.dataCoverUrl)
        } catch (e: Exception) {
            Logger.e(TAG, e) { "resolveContentForAniList failed: ${e.message}" }
        }
    }

    /**
     * Resolve/create a content record for an extension entry + check library status.
     * Called from [loadFromExtension].
     *
     * D-137: Cross-source deduplication — before creating a new content record,
     * check the auto-link cache for a cached anilistId. If found, check if a
     * content record already exists for that anilistId (saved from AniList).
     * If yes → return THAT mainId (same content, different entry point).
     */
    private suspend fun resolveContentForExtension(
        sourceId: Long,
        animeUrl: String,
        title: String,
        unifiedAnime: UnifiedAnime? = null,
    ) {
        try {
            // D-137: Check auto-link cache first.
            val cachedAniListId = autoLinkPreferences.getCachedAniListId(sourceId, animeUrl)
            if (cachedAniListId > 0) {
                // Check if a content record already exists for this anilistId.
                val existingContent = contentRepository.getMainEntryByAniListId(cachedAniListId)
                if (existingContent != null) {
                    Logger.i(TAG) { "Cross-source match: extension ($sourceId, $animeUrl) → existing mainId=${existingContent.mainId} (via cached anilistId=$cachedAniListId)" }
                    // D-198: link the extension entry + atomically store the ext_* axis
                    // (single transaction in the resolver). When unifiedAnime is null, fall
                    // back to the 5-arg overload (no metadata to persist).
                    if (unifiedAnime != null) {
                        val extensionDetail = com.confused.anikuta.core.content.ContentDetails(
                            mainId = existingContent.mainId,
                            extensionType = "aniyomi",
                            extensionId = sourceId.toString(),
                            sourceId = sourceId,
                            animeUrl = animeUrl,
                            extDescription = unifiedAnime.description,
                            extGenres = unifiedAnime.genres.joinToString(", "),
                            extStatus = unifiedAnime.status,
                            extAuthor = unifiedAnime.author,
                            extArtist = unifiedAnime.artist,
                            extThumbnailUrl = unifiedAnime.coverUrl,
                            extUpdatedAt = System.currentTimeMillis(),
                        )
                        contentResolver.linkExtensionToExisting(
                            mainId = existingContent.mainId,
                            extensionId = sourceId,
                            sourceId = sourceId,
                            animeUrl = animeUrl,
                            title = title,
                            extensionDetail = extensionDetail,
                        )
                    } else {
                        contentResolver.linkExtensionToExisting(
                            mainId = existingContent.mainId,
                            extensionId = sourceId,
                            sourceId = sourceId,
                            animeUrl = animeUrl,
                            title = title,
                        )
                    }
                    currentMainId = existingContent.mainId; _mainIdFlow.value = existingContent.mainId
                    refreshContentAndLibraryStatus(existingContent.mainId)
                    return
                }
            }

            // No cross-source match — create a new content record.
            val mainId = contentResolver.resolveOrCreateForExtension(
                extensionId = sourceId,
                sourceId = sourceId,
                animeUrl = animeUrl,
                title = title,
                systemName = "aniyomi",
                repoUrl = null,
                extensionPkg = null,
            )
            currentMainId = mainId; _mainIdFlow.value = mainId

            // D-223: Trigger cover color extraction for adaptive theming.
            triggerCoverColorExtraction(mainId, unifiedAnime?.coverUrl)

            // D-142 + D-198: Store the extension detail (with coverUrl) for library display.
            // Without this, the library can't show cover images for extension-only entries.
            if (unifiedAnime != null) {
                contentRepository.updateExtensionAxis(
                    com.confused.anikuta.core.content.ContentDetails(
                        mainId = mainId,
                        extensionType = "aniyomi",
                        extensionId = sourceId.toString(),
                        sourceId = sourceId,
                        animeUrl = animeUrl,
                        extDescription = unifiedAnime.description,
                        extGenres = unifiedAnime.genres.joinToString(", "),
                        extStatus = unifiedAnime.status,
                        extAuthor = unifiedAnime.author,
                        extArtist = unifiedAnime.artist,
                        extThumbnailUrl = unifiedAnime.coverUrl,
                        extUpdatedAt = System.currentTimeMillis(),
                    ),
                )
                Logger.i(TAG) { "Extension detail stored with coverUrl=${unifiedAnime.coverUrl?.take(60)}" }
            }

            refreshContentAndLibraryStatus(mainId)
        } catch (e: Exception) {
            Logger.e(TAG, e) { "resolveContentForExtension failed: ${e.message}" }
        }
    }

    /**
     * Refresh the contentId + library status from the repository.
     */
    private fun refreshContentAndLibraryStatus(mainId: String) {
        val content = contentRepository.getMainEntryByMainId(mainId)
        if (content != null) {
            _contentId.value = content.contentId
            Logger.i(TAG) { "Content ID: ${content.contentId}" }
        }
        _isInLibrary.value = contentRepository.isInLibrary(mainId)
        Logger.i(TAG) { "Library status: ${if (_isInLibrary.value) "in library" else "not in library"}" }
    }

    /**
     * D-223: Trigger cover color extraction for adaptive theming.
     *
     * Checks if the cover accent color is already stored in the DB. If yes,
     * sets the [_coverAccent] StateFlow immediately (instant — no network).
     * If no, kicks off background extraction via [CoverColorExtractor] +
     * stores the result in the DB for next time.
     *
     * Safe to call multiple times — no-ops if the color is already extracted.
     */
    private fun triggerCoverColorExtraction(mainId: String, coverUrl: String?) {
        // First, check if the DB already has a stored color.
        val details = contentRepository.getContentDetails(mainId)
        val storedArgb = details?.coverAccentArgb
        if (storedArgb != null) {
            _coverAccent.value = storedArgb.toInt()
            Logger.d(TAG) { "Cover accent already stored: 0x${"%08X".format(storedArgb.toInt())}" }
            return
        }
        // No stored color — clear the state + trigger extraction if we have a cover URL.
        _coverAccent.value = null
        if (coverUrl.isNullOrBlank() || coverColorExtractor == null) return

        viewModelScope.launch {
            val argb = coverColorExtractor.extract(coverUrl)
            if (argb != null) {
                contentRepository.updateCoverAccent(mainId, argb.toLong())
                _coverAccent.value = argb
                Logger.i(TAG) { "Cover accent extracted + stored: 0x${"%08X".format(argb)}" }
            }
        }
    }

    /**
     * Refresh just the contentId (after a link/unlink operation).
     */
    private fun refreshContentId(mainId: String) {
        val content = contentRepository.getMainEntryByMainId(mainId)
        if (content != null) {
            _contentId.value = content.contentId
            Logger.i(TAG) { "Content ID refreshed: ${content.contentId}" }
        }
    }

    /**
     * Toggle the current anime's library status.
     * If in library → remove from ALL categories. If not → add to Default category.
     */
    fun toggleLibrary() {
        val mainId = currentMainId ?: run {
            Logger.w(TAG) { "toggleLibrary: no currentMainId" }
            return
        }
        if (_isInLibrary.value) {
            contentRepository.removeFromLibrary(mainId)
            _isInLibrary.value = false
            Logger.i(TAG) { "Removed from library: mainId=$mainId" }
            activityTracker.track(
                eventType = com.confused.anikuta.core.activitytracker.ActivityEventType.LIBRARY_REMOVE,
                contentKey = mainId,
                route = "details",
                contentType = "anime",
            )
        } else {
            contentRepository.addToDefaultCategory(mainId)
            _isInLibrary.value = true
            Logger.i(TAG) { "Added to library (Default): mainId=$mainId" }
            activityTracker.track(
                eventType = com.confused.anikuta.core.activitytracker.ActivityEventType.LIBRARY_ADD,
                contentKey = mainId,
                route = "details",
                contentType = "anime",
            )
            // D-192 Phase 3: ensure update state exists so the Updates engine can track new episodes.
            updateEngine.ensureUpdateState(mainId)
        }
    }

    // ── Category management (D-138) ──

    /** All library categories (for the category picker popup). */
    private val _categories = MutableStateFlow<List<com.confused.anikuta.core.content.LibraryCategory>>(emptyList())
    val categories: StateFlow<List<com.confused.anikuta.core.content.LibraryCategory>> = _categories.asStateFlow()

    /** Categories the current anime is in (for the category picker popup checkboxes). */
    private val _contentCategories = MutableStateFlow<Set<Long>>(emptySet())
    val contentCategories: StateFlow<Set<Long>> = _contentCategories.asStateFlow()

    /** Whether the category picker sheet is shown. */
    private val _showCategorySheet = MutableStateFlow(false)
    val showCategorySheet: StateFlow<Boolean> = _showCategorySheet.asStateFlow()

    /**
     * Load all categories + the current content's categories.
     * Called when the user long-presses the save button.
     */
    fun openCategorySheet() {
        val mainId = currentMainId ?: run {
            Logger.w(TAG) { "openCategorySheet: no currentMainId" }
            return
        }
        _categories.value = contentRepository.getAllCategories()
        _contentCategories.value = contentRepository.getCategoriesForContent(mainId).map { it.id }.toSet()
        _showCategorySheet.value = true
        Logger.i(TAG) { "Opened category sheet: ${_categories.value.size} categories, content in ${_contentCategories.value.size}" }
    }

    fun dismissCategorySheet() {
        _showCategorySheet.value = false
    }

    /**
     * Toggle a category for the current content.
     * If in category → remove. If not → add.
     */
    fun toggleCategory(categoryId: Long) {
        val mainId = currentMainId ?: return
        val current = _contentCategories.value.toMutableSet()
        if (categoryId in current) {
            contentRepository.removeFromCategory(mainId, categoryId)
            current.remove(categoryId)
        } else {
            contentRepository.addToCategory(mainId, categoryId)
            current.add(categoryId)
        }
        _contentCategories.value = current
        // Update isInLibrary — if content is in ANY category, it's "in library".
        _isInLibrary.value = current.isNotEmpty()
    }

    /**
     * Create a new category + add the current content to it.
     */
    fun createCategoryAndAdd(name: String) {
        val mainId = currentMainId ?: return
        val newId = contentRepository.createCategory(name)
        if (newId > 0) {
            contentRepository.addToCategory(mainId, newId)
            _categories.value = contentRepository.getAllCategories()
            _contentCategories.value = _contentCategories.value + newId
            _isInLibrary.value = true
            Logger.i(TAG) { "Created category '$name' + added content" }
        }
    }

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
                // D-193 Phase 1: Persist the source link so loadFromAniList can restore it
                // synchronously (fixes "no source from library" race — #21).
                preferenceStore.putString(KEY_SOURCE_LINK_PREFIX + result.anilistId, "$sourceId:$animeUrl")
                // D-130: Auto-link uses EXTENSION priority (non-intrusive) —
                // AniList only fills nulls, doesn't overwrite extension data.
                // The user can manually switch to ANILIST priority via the
                // data-source selector if they want AniList to take over.
                mergeAniListIntoUnified(
                    result.anilistId,
                    com.confused.anikuta.core.common.model.DataSourcePriority.EXTENSION,
                )
                _autoLinkState.value = AutoLinkState.Matched(result.anilistId, 1.0f, cached = true)
            }
            is AutoLinkResult.Matched -> {
                Logger.i(TAG) { "Auto-link MATCH: anilistId=${result.anilistId} (score=${result.score})" }
                // D-193 Phase 1: Persist the source link (same as Cached case).
                preferenceStore.putString(KEY_SOURCE_LINK_PREFIX + result.anilistId, "$sourceId:$animeUrl")
                mergeAniListIntoUnified(
                    result.anilistId,
                    com.confused.anikuta.core.common.model.DataSourcePriority.EXTENSION,
                )
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
     * Fetch AniList data + store it in [anilistBase], then re-merge.
     *
     * D-134: Instead of overwriting the current UnifiedAnime, we store the
     * fetched AniList data as [anilistBase] and call [remergeBases]. This way:
     * - The original extension data ([extensionBase]) is never lost.
     * - Switching priority back to EXTENSION recovers the original extension data.
     * - Switching priority to ANILIST shows AniList data.
     *
     * @param anilistId The AniList ID to fetch.
     * @param priority The priority to use for the re-merge.
     */
    private suspend fun mergeAniListIntoUnified(
        anilistId: Int,
        priority: com.confused.anikuta.core.common.model.DataSourcePriority =
            com.confused.anikuta.core.common.model.DataSourcePriority.ANILIST,
    ) {
        try {
            // Fetch fresh AniList data + store as anilistBase.
            val anilistData = anilistProvider.fetchFromAniList(anilistId)
            if (anilistData != null) {
                anilistBase = anilistData
                Logger.i(TAG) { "AniList base stored: ${anilistData.displayName} (anilistId=$anilistId)" }
            } else {
                Logger.w(TAG) { "AniList fetch returned null for anilistId=$anilistId" }
                return
            }
            currentAnimeId = anilistId // So episode metadata fetch can use it.
            remergeBases(priority)

            // D-137: Persist the AniList link in the content database.
            // D-198: anilist_detail → content_details (data-source axis).
            // This ensures the content_details row is created + the main_entry's
            // dataSourceId is set. When the same anime is opened from another source
            // later, the content resolver can find this mainId via the anilistId.
            val mainId = currentMainId
            if (mainId != null) {
                val detail = com.confused.anikuta.core.content.ContentDetails(
                    mainId = mainId,
                    dataSourceType = "anilist",
                    dataSourceRefId = anilistId.toString(),
                    dataScore = anilistData.averageScore?.toLong(),
                    dataEpisodes = anilistData.episodes?.toLong(),
                    dataSeason = anilistData.season,
                    dataSeasonYear = anilistData.seasonYear?.toLong(),
                    dataStatus = anilistData.status,
                    dataGenres = anilistData.genres?.joinToString(", "),
                    dataSynopsis = anilistData.description,
                    dataCoverUrl = anilistData.coverUrl,
                    dataBannerUrl = anilistData.bannerUrl,
                    dataUpdatedAt = System.currentTimeMillis(),
                )
                contentResolver.linkAniList(mainId, anilistId, detail)
                // Genre System: normalize + store genres in the junction table.
                anilistData.genres?.let { genres ->
                    genreRepository.setGenresForContent(mainId, genres, "anilist")
                }
                // Refresh the contentId (it changed after linking).
                refreshContentId(mainId)
            }

            // Now that we have an anilistId, kick off episode metadata fetch.
            val malId = anilistData.idMal
            val episodes = (_episodeState.value as? EpisodeState.Loaded)?.episodes ?: emptyList()
            if (episodes.isNotEmpty()) {
                viewModelScope.launch {
                    try {
                        val metadata = episodeMetadataEngine.fetchEpisodeMetadata(
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

    /**
     * Switch the displayed data source between AniList and Extension (D-130, D-134).
     *
     * Only works when the entry is linked (both anilistId + sourceId non-null).
     * Re-merges [extensionBase] + [anilistBase] with the new priority.
     * Both bases are preserved — switching back doesn't lose data.
     */
    fun switchDataSource(priority: com.confused.anikuta.core.common.model.DataSourcePriority) {
        val anime = (_state.value as? DetailsState.Success)?.anime ?: return
        // D-134: Both bases must be available for switching to make sense.
        // If only one base exists, switching does nothing (there's nothing to switch to).
        if (extensionBase == null || anilistBase == null) {
            Logger.w(TAG) { "switchDataSource: need both bases (ext=${extensionBase != null}, al=${anilistBase != null})" }
            return
        }
        Logger.i(TAG) { "Switching data source to $priority" }
        // D-134: Just re-merge the existing bases with the new priority.
        // No network call needed — both bases are already in memory.
        remergeBases(priority)
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
        // D-199: Save the source link so loadLinkedSource() can find the extension
        // source when reopening from Library. Without this, the Library open path
        // reads KEY_SOURCE_LINK_PREFIX + anilistId → empty → source not found →
        // episodes not fetched → UI shows "source removed" + empty episode list.
        preferenceStore.putString(KEY_SOURCE_LINK_PREFIX + anilistId, "$sourceId:$animeUrl")

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

        // D-137: Persist the unlink in the content database.
        val mainId = currentMainId
        if (mainId != null) {
            contentResolver.unlinkAniList(mainId)
            refreshContentId(mainId)
        }

        // D-134: Clear the AniList base + re-merge (shows extension data only).
        anilistBase = null
        currentAnimeId = 0
        remergeBases(com.confused.anikuta.core.common.model.DataSourcePriority.EXTENSION)
        _autoLinkState.value = AutoLinkState.Idle
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

    // ── D-225c: Auto-link popup dismiss handlers ──
    // Called by the [AutoLinkPopup] when its auto-dismiss timer fires OR the
    // user swipes/taps it away. Resets the corresponding state to Idle so the
    // popup hides cleanly without flickering on the next recomposition.

    /** Dismiss the FORWARD auto-link popup (extension → AniList). */
    fun dismissAutoLinkPopup() {
        if (_autoLinkState.value !is AutoLinkState.Searching) {
            _autoLinkState.value = AutoLinkState.Idle
        }
    }

    /** Dismiss the REVERSE auto-link popup (AniList → extensions). */
    fun dismissReverseAutoLinkPopup() {
        if (_reverseAutoLinkState.value !is ReverseAutoLinkState.Searching) {
            _reverseAutoLinkState.value = ReverseAutoLinkState.Idle
        }
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
     * If found, fetch the episode list + restore extensionBase.
     *
     * D-140: Also restores extensionBase from the content database so the
     * data-source selector shows immediately on reopen (not just after
     * the user re-links the source).
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

        // D-140: Restore extensionBase from the content database.
        // This makes the data-source selector available immediately on reopen.
        // D-198: getExtensionDetail → getContentDetails; ext_* axis.
        val mainId = currentMainId
        if (mainId != null && extensionBase == null) {
            val details = contentRepository.getContentDetails(mainId)
            if (details != null && details.hasExtensionLink) {
                extensionBase = com.confused.anikuta.core.common.model.UnifiedAnime(
                    title = (details.animeUrl ?: animeUrl).substringAfterLast("/").replace("-", " "),
                    description = details.extDescription,
                    genres = details.extGenres?.split(", ")?.filter { it.isNotBlank() } ?: emptyList(),
                    status = details.extStatus,
                    author = details.extAuthor,
                    artist = details.extArtist,
                    coverUrl = details.extThumbnailUrl,
                    sourceId = details.sourceId,
                    sourceName = source.name,
                    animeUrl = details.animeUrl ?: animeUrl,
                    entryMode = com.confused.anikuta.core.common.model.EntryMode.EXTENSION,
                )
                Logger.i(TAG) { "Extension base restored from DB for reopen" }
                remergeBases(
                    (_state.value as? DetailsState.Success)?.anime?.dataSourcePriority
                        ?: com.confused.anikuta.core.common.model.DataSourcePriority.ANILIST
                )
            } else {
                // No extension detail in DB — create a minimal extensionBase from the linked source.
                val sAnime = eu.kanade.tachiyomi.animesource.model.SAnime.create().apply {
                    url = animeUrl
                    title = (_state.value as? DetailsState.Success)?.anime?.displayName ?: animeUrl
                    initialized = false
                }
                extensionBase = sAnime.toUnifiedAnime(sourceId, source.name)
                Logger.i(TAG) { "Extension base created from linked source (minimal)" }
                remergeBases(
                    (_state.value as? DetailsState.Success)?.anime?.dataSourcePriority
                        ?: com.confused.anikuta.core.common.model.DataSourcePriority.ANILIST
                )

                // D-139: Fetch full extension details in the background to enrich.
                viewModelScope.launch {
                    try {
                        val enriched = extensionProvider.fetchFromExtension(
                            sourceId, animeUrl, sAnime.title, null,
                        )
                        if (enriched != null) {
                            extensionBase = enriched
                            remergeBases(
                                (_state.value as? DetailsState.Success)?.anime?.dataSourcePriority
                                    ?: com.confused.anikuta.core.common.model.DataSourcePriority.ANILIST
                            )
                        }
                    } catch (e: Exception) {
                        Logger.w(TAG) { "Extension enrichment on reopen failed: ${e.message}" }
                    }
                }
            }
        }

        // Get the anime title from the current state (for the SAnime.title lateinit field).
        val animeTitle = (_state.value as? DetailsState.Success)?.anime?.displayName ?: animeUrl
        fetchEpisodes(source, animeUrl, animeTitle)
    }

    /**
     * Link a source + SAnime to the current anime. Persists the link + fetches episodes.
     *
     * D-134: For AniList entries, this also creates [extensionBase] from the picked
     * SAnime (so the data-source selector becomes available — the user can now
     * switch between AniList data and Extension data).
     */
    fun linkSource(source: AnimeCatalogueSource, sAnime: SAnime) {
        val animeId = currentAnimeId
        Logger.i(TAG) { "Linking anime $animeId to source ${source.name} (${sAnime.url})" }
        preferenceStore.putString(
            KEY_SOURCE_LINK_PREFIX + animeId,
            "${source.id}:${sAnime.url}",
        )
        _linkedSource.value = LinkedSource(source.id, source.name, sAnime.url)

        // D-139: Cache the reverse mapping (sourceId, animeUrl) → anilistId.
        // This ensures that when the user opens the SAME anime from the extension
        // later, resolveContentForExtension finds the cached anilistId → finds
        // the existing content record → uses the SAME mainId (no duplicate).
        if (animeId > 0) {
            autoLinkPreferences.cacheAniListId(source.id, sAnime.url, animeId)
            Logger.i(TAG) { "Cached reverse mapping: (${source.id}, ${sAnime.url}) → anilistId=$animeId" }
        }

        // D-139: Persist the extension link in the content database.
        // This updates the content record with the extension fields + regenerates
        // the contentId. D-198: also stores the ext_* axis in content_details
        // via linkExtensionToExisting's overload that takes extensionDetail.
        val mainId = currentMainId
        if (mainId != null) {
            // D-198: ExtensionDetail → ContentDetails (extension axis). Pass via the
            // linkExtensionToExisting overload that takes extensionDetail to atomically
            // update both main_entry + content_details in a single transaction.
            val extensionDetail = com.confused.anikuta.core.content.ContentDetails(
                mainId = mainId,
                extensionType = "aniyomi",
                extensionId = source.id.toString(),
                sourceId = source.id,
                animeUrl = sAnime.url,
                extDescription = sAnime.description,
                extGenres = sAnime.genre,
                extStatus = sAnime.status.toString(),
                extAuthor = sAnime.author,
                extArtist = sAnime.artist,
                extThumbnailUrl = sAnime.thumbnail_url,
                extUpdatedAt = System.currentTimeMillis(),
            )
            contentResolver.linkExtensionToExisting(
                mainId = mainId,
                extensionId = source.id,
                sourceId = source.id,
                animeUrl = sAnime.url,
                title = sAnime.title,
                extensionDetail = extensionDetail,
            )
            refreshContentId(mainId)
        }

        // D-134: Create extensionBase from the picked SAnime (for AniList entries).
        // This makes the data-source selector available.
        if (extensionBase == null) {
            extensionBase = sAnime.toUnifiedAnime(source.id, source.name)
            Logger.i(TAG) { "Extension base created from picked SAnime: ${sAnime.title}" }
            // Re-merge to update the display (keeps current priority).
            val currentPriority = (_state.value as? DetailsState.Success)?.anime?.dataSourcePriority
                ?: com.confused.anikuta.core.common.model.DataSourcePriority.ANILIST
            remergeBases(currentPriority)

            // D-139: Fetch full extension details in the background.
            // The SAnime from search is sparse (no description, score, etc.).
            // Fetch the full details via getAnimeDetails to enrich extensionBase.
            viewModelScope.launch {
                try {
                    val enriched = extensionProvider.fetchFromExtension(
                        source.id, sAnime.url, sAnime.title, sAnime.thumbnail_url,
                    )
                    if (enriched != null) {
                        extensionBase = enriched
                        Logger.i(TAG) { "Extension base enriched with full details: ${enriched.displayName}" }
                        remergeBases(
                            (_state.value as? DetailsState.Success)?.anime?.dataSourcePriority
                                ?: com.confused.anikuta.core.common.model.DataSourcePriority.ANILIST
                        )
                    }
                } catch (e: Exception) {
                    Logger.w(TAG) { "Extension detail enrichment failed: ${e.message}" }
                }
            }
        }

        fetchEpisodes(source, sAnime.url, sAnime.title)
    }

    /**
     * Unlink the current source.
     *
     * D-134: Clears [extensionBase] + re-merge (shows AniList data only, if available).
     * D-198: also calls [ContentResolver.unlinkExtension] to NULL the ext_* axis on
     * content_details + regenerate content_id + flip display_source (fixes the
     * orphan-row bug).
     */
    fun unlinkSource() {
        val animeId = currentAnimeId
        Logger.i(TAG) { "Unlinking source for anime $animeId" }
        preferenceStore.putString(KEY_SOURCE_LINK_PREFIX + animeId, "")
        _linkedSource.value = null
        _episodeState.value = EpisodeState.Idle

        // D-198: persist the unlink in the content database.
        val mainId = currentMainId
        if (mainId != null) {
            contentResolver.unlinkExtension(mainId)
            refreshContentId(mainId)
        }

        // D-134: Clear the extension base + re-merge.
        extensionBase = null
        val currentPriority = (_state.value as? DetailsState.Success)?.anime?.dataSourcePriority
            ?: com.confused.anikuta.core.common.model.DataSourcePriority.ANILIST
        remergeBases(currentPriority)
    }

    // ── Episode fetching ──

    private fun fetchEpisodes(source: AnimeCatalogueSource, animeUrl: String, animeTitle: String) {
        _episodeState.value = EpisodeState.Loading
        viewModelScope.launch {
            // D-147: Check the local cache first — if episodes are cached, display instantly.
            // D.FIX: Also do a BACKGROUND refresh from the network so the cache stays fresh.
            val mainId = currentMainId
            if (mainId != null) {
                val cachedEpisodes = dataCacheRepository.getEpisodeMetadata(mainId)
                if (cachedEpisodes.isNotEmpty()) {
                    Logger.i(TAG) { "Loaded ${cachedEpisodes.size} episodes from cache (last updated: ${cachedEpisodes.firstOrNull()?.fetchedAt ?: "unknown"})" }
                    // Reconstruct SEpisode objects from cached metadata.
                    // D.FIX: Use the cached episode URL if available, fall back to animeUrl.
                    val episodes = cachedEpisodes.map { meta ->
                        SEpisode.create().apply {
                            url = meta.episodeUrl ?: animeUrl
                            episode_number = meta.episodeNumber
                            name = meta.sourceName ?: meta.title ?: "Episode ${meta.episodeNumber.toInt()}"
                            date_upload = meta.airDate ?: 0L
                            scanlator = meta.scanlator
                        }
                    }.sortedByDescending { it.episode_number }
                    _episodeState.value = EpisodeState.Loaded(episodes)

                    // Also restore episode metadata map.
                    // D-190: restore ALL fields from cache (including new AniZip/Jikan/Kitsu fields).
                    // Previously this only restored 4 fields (title, thumbnail, description, airDate) —
                    // silently losing isFiller, isRecap, titleJapanese, etc. on cache restore.
                    val metadataMap = cachedEpisodes.associate { meta ->
                        meta.episodeNumber.toInt() to com.confused.anikuta.core.metadata.EpisodeMetadata(
                            episodeKey = mainId + "|" + String.format("%05d", meta.episodeNumber.toInt()),
                            number = meta.episodeNumber.toDouble(),
                            title = meta.title,
                            thumbnailUrl = meta.thumbnailUrl,
                            description = meta.description,
                            airDate = meta.airDate,
                            isFiller = meta.isFiller,
                            isRecap = meta.isRecap,
                            titleJapanese = meta.titleJapanese,
                            titleRomaji = meta.titleRomaji,
                            runtime = meta.runtime,
                            seasonNumber = meta.seasonNumber,
                            episodeNumberInSeason = meta.episodeNumberInSeason,
                            score = meta.score,
                        )
                    }
                    _episodeMetadata.value = metadataMap
                    Logger.i(TAG) { "Episode metadata restored from cache: ${metadataMap.size} entries" }

                    // ── Background refresh: fetch fresh episodes + compare with cache ──
                    //
                    // KEY DESIGN (user requirement):
                    // - If fresh episodes == cached episodes (same URLs): SKIP the update
                    //   entirely. Don't replace _episodeState, don't touch the cache.
                    //   This prevents the "metadata disappears" bug where the background
                    //   refresh destructively overwrites rich AniList metadata with sparse
                    //   extension data (INSERT OR REPLACE overwrites ALL columns).
                    // - If fresh episodes != cached (new episodes found): replace
                    //   _episodeState, insert ONLY new episodes into the cache (preserve
                    //   existing rich metadata), and auto-fetch metadata for the new
                    //   episodes.
                    try {
                        val sAnime = SAnime.create().apply {
                            url = animeUrl
                            title = animeTitle
                            initialized = false
                        }
                        val freshEpisodes = withContext(Dispatchers.IO) {
                            source.getEpisodeList(sAnime)
                        }
                        Logger.i(TAG) { "Background refresh: fetched ${freshEpisodes.size} fresh episodes from ${source.name}" }

                        if (freshEpisodes.isEmpty()) {
                            Logger.i(TAG) { "Background refresh: fresh episode list is empty — keeping cached data untouched" }
                            return@launch
                        }

                        // Compare fresh episodes with cached episodes by URL set.
                        val cachedUrls = cachedEpisodes.mapNotNull { it.episodeUrl }.toSet()
                        val freshUrls = freshEpisodes.map { it.url }.toSet()
                        val hasNewEpisodes = freshUrls != cachedUrls

                        if (!hasNewEpisodes) {
                            // No changes — skip the update entirely. This is the critical
                            // fix: the old code ALWAYS replaced _episodeState + ALWAYS
                            // overwrote the cache, destroying rich AniList metadata.
                            Logger.i(TAG) { "Background refresh: no new episodes (same ${freshUrls.size} URLs) — cache + display untouched, metadata preserved" }
                            return@launch
                        }

                        // New episodes found — update the display + cache.
                        val newEpisodes = freshEpisodes.filter { it.url !in cachedUrls }
                        Logger.i(TAG) { "Background refresh: ${newEpisodes.size} new episode(s) detected (cached=${cachedUrls.size}, fresh=${freshUrls.size})" }

                        val sorted = freshEpisodes.sortedByDescending { it.episode_number }
                        _episodeState.value = EpisodeState.Loaded(sorted)

                        // Insert ONLY new episodes into the cache — don't overwrite
                        // existing rich metadata (titles, descriptions, thumbnails from
                        // AniList) for episodes that are already cached.
                        val now = System.currentTimeMillis()
                        val newCacheEntries = newEpisodes.map { ep ->
                            com.confused.anikuta.core.datacache.CachedEpisodeMetadata(
                                mainId = mainId,
                                episodeNumber = ep.episode_number,
                                title = ep.name,
                                description = ep.summary,
                                thumbnailUrl = null,
                                airDate = if (ep.date_upload > 0) ep.date_upload else null,
                                fetchedAt = now,
                                episodeUrl = ep.url,
                                sourceName = ep.name,
                                scanlator = ep.scanlator,
                            )
                        }
                        if (newCacheEntries.isNotEmpty()) {
                            dataCacheRepository.upsertEpisodeMetadataBatch(newCacheEntries)
                            Logger.i(TAG) { "Background refresh: inserted ${newCacheEntries.size} new episode(s) into cache (existing metadata preserved)" }
                        }

                        // Auto-fetch metadata for the new episodes (if we have an AniList ID).
                        // This is the user's requirement: "if new episodes are found,
                        // launch the metadata functionality automatically."
                        val animeId = currentAnimeId
                        val malId = (_state.value as? DetailsState.Success)?.anime?.idMal
                        if (animeId > 0) {
                            viewModelScope.launch {
                                try {
                                    val metadata = episodeMetadataEngine.fetchEpisodeMetadata(
                                        anilistId = animeId,
                                        malId = malId,
                                        episodeCount = freshEpisodes.size,
                                    )
                                    if (metadata.isNotEmpty()) {
                                        // Merge: keep existing metadata, add/update with fresh.
                                        val merged = _episodeMetadata.value.toMutableMap()
                                        merged.putAll(metadata)
                                        _episodeMetadata.value = merged
                                        Logger.i(TAG) { "Background refresh: merged ${metadata.size} metadata entries (total: ${merged.size})" }

                                        // Update cache with enriched metadata for ALL episodes,
                                        // preserving episodeUrl from the fresh extension episodes.
                                        val epNumToUrl = freshEpisodes.associate { it.episode_number.toInt() to it.url }
                                        val epNumToSourceName = freshEpisodes.associate { it.episode_number.toInt() to it.name }
                                        val epNumToScanlator = freshEpisodes.associate { it.episode_number.toInt() to (it.scanlator ?: "") }
                                        val enrichedCache = metadata.entries.map { (epNum, meta) ->
                                            com.confused.anikuta.core.datacache.CachedEpisodeMetadata(
                                                mainId = mainId,
                                                episodeNumber = epNum.toFloat(),
                                                title = meta.title,
                                                description = meta.description,
                                                thumbnailUrl = meta.thumbnailUrl,
                                                airDate = meta.airDate,
                                                fetchedAt = now,
                                                episodeUrl = epNumToUrl[epNum],
                                                sourceName = epNumToSourceName[epNum],
                                                scanlator = epNumToScanlator[epNum]?.takeIf { it.isNotEmpty() },
                                                isFiller = meta.isFiller,
                                                isRecap = meta.isRecap,
                                                titleJapanese = meta.titleJapanese,
                                                titleRomaji = meta.titleRomaji,
                                                runtime = meta.runtime,
                                                seasonNumber = meta.seasonNumber,
                                                episodeNumberInSeason = meta.episodeNumberInSeason,
                                                score = meta.score,
                                            )
                                        }
                                        dataCacheRepository.upsertEpisodeMetadataBatch(enrichedCache)
                                        Logger.i(TAG) { "Background refresh: enriched cache with ${enrichedCache.size} metadata entries (episodeUrl preserved)" }
                                    } else {
                                        Logger.w(TAG) { "Background refresh: metadata fetch returned empty — keeping existing metadata" }
                                    }
                                } catch (e: Exception) {
                                    Logger.w(TAG) { "Background refresh: metadata fetch failed: ${e.message}" }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Logger.w(TAG) { "Background refresh failed (non-fatal): ${e.message}" }
                    }
                    return@launch
                }
            }

            // No cache — fetch from network.
            try {
                Logger.i(TAG) { "Fetching episodes from ${source.name} for $animeUrl (title: $animeTitle)" }
                val sAnime = SAnime.create().apply {
                    url = animeUrl
                    title = animeTitle
                    initialized = false
                }

                val episodes = withContext(Dispatchers.IO) {
                    source.getEpisodeList(sAnime)
                }
                Logger.i(TAG) { "Fetched ${episodes.size} episodes from ${source.name}" }
                _episodeState.value = if (episodes.isEmpty()) {
                    EpisodeState.Empty
                } else {
                    val sorted = episodes.sortedByDescending { it.episode_number }
                    EpisodeState.Loaded(sorted)
                }

                // D-147: Cache the episode list locally. D.FIX: Include episodeUrl.
                if (mainId != null && episodes.isNotEmpty()) {
                    val now = System.currentTimeMillis()
                    val cachedList = episodes.map { ep ->
                        com.confused.anikuta.core.datacache.CachedEpisodeMetadata(
                            mainId = mainId,
                            episodeNumber = ep.episode_number,
                            title = ep.name,
                            description = ep.summary,
                            thumbnailUrl = null,
                            airDate = if (ep.date_upload > 0) ep.date_upload else null,
                            fetchedAt = now,
                            episodeUrl = ep.url,
                            sourceName = ep.name,
                            scanlator = ep.scanlator,
                        )
                    }
                    dataCacheRepository.upsertEpisodeMetadataBatch(cachedList)
                    Logger.i(TAG) { "Cached ${episodes.size} episodes locally (incl. episodeUrl)" }
                }

                // D-192 Phase 3: Notify the Updates engine about the episode count.
                // This sets the baseline (lastKnownEpisodeCount) + creates update rows for new episodes.
                val midForUpdates = currentMainId
                if (midForUpdates != null && episodes.isNotEmpty()) {
                    viewModelScope.launch {
                        try {
                            updateEngine.onEpisodesRefreshed(midForUpdates, episodes.size)
                            Logger.d(TAG) { "UpdateEngine notified: ${episodes.size} episodes for $midForUpdates" }
                        } catch (e: Exception) {
                            Logger.w(TAG) { "onEpisodesRefreshed failed: ${e.message}" }
                        }
                    }
                }

                // Fetch episode metadata (titles, thumbnails, descriptions, dates).
                val animeId = currentAnimeId
                val malId = (_state.value as? DetailsState.Success)?.anime?.idMal
                if (animeId > 0 && episodes.isNotEmpty()) {
                    viewModelScope.launch {
                        try {
                            val metadata = episodeMetadataEngine.fetchEpisodeMetadata(
                                anilistId = animeId,
                                malId = malId,
                                episodeCount = episodes.size,
                            )
                            // D.FIX: Only overwrite if the new metadata is non-empty.
                            // Otherwise keep the cached metadata (which may have been
                            // loaded from a previous successful fetch).
                            if (metadata.isNotEmpty()) {
                                _episodeMetadata.value = metadata
                                Logger.i(TAG) { "Episode metadata loaded: ${metadata.size} entries" }
                            } else {
                                Logger.w(TAG) { "Episode metadata fetch returned empty — keeping cached metadata (${_episodeMetadata.value.size} entries)" }
                            }

                            // D-147: Update the cache with the fetched metadata.
                            // D.FIX: Preserve episodeUrl from the extension episodes —
                            // the AniList metadata only has episode numbers, not URLs.
                            // Without this, INSERT OR REPLACE would overwrite the
                            // episode_url column with NULL (destructive!), causing all
                            // episodes to fall back to the anime URL on the next open.
                            if (mainId != null) {
                                val now = System.currentTimeMillis()
                                val epNumToUrl = episodes.associate { it.episode_number.toInt() to it.url }
                                val epNumToSourceName = episodes.associate { it.episode_number.toInt() to it.name }
                                val epNumToScanlator = episodes.associate { it.episode_number.toInt() to (it.scanlator ?: "") }
                                val enrichedCache = metadata.entries.map { (epNum, meta) ->
                                    com.confused.anikuta.core.datacache.CachedEpisodeMetadata(
                                        mainId = mainId,
                                        episodeNumber = epNum.toFloat(),
                                        title = meta.title,
                                        description = meta.description,
                                        thumbnailUrl = meta.thumbnailUrl,
                                        airDate = meta.airDate,
                                        fetchedAt = now,
                                        episodeUrl = epNumToUrl[epNum],
                                        sourceName = epNumToSourceName[epNum],
                                        scanlator = epNumToScanlator[epNum]?.takeIf { it.isNotEmpty() },
                                        isFiller = meta.isFiller,
                                        isRecap = meta.isRecap,
                                        titleJapanese = meta.titleJapanese,
                                        titleRomaji = meta.titleRomaji,
                                        runtime = meta.runtime,
                                        seasonNumber = meta.seasonNumber,
                                        episodeNumberInSeason = meta.episodeNumberInSeason,
                                        score = meta.score,
                                    )
                                }
                                dataCacheRepository.upsertEpisodeMetadataBatch(enrichedCache)
                                Logger.i(TAG) { "Updated episode cache with enriched metadata (episodeUrl + sourceName + scanlator preserved for ${epNumToUrl.size} episodes)" }
                            }
                        } catch (e: Exception) {
                            Logger.w(TAG) { "Episode metadata fetch failed: ${e.message}" }
                        }
                    }
                }
            } catch (e: Throwable) {
                // Catch Throwable — binary-incompat throws NoClassDefFoundError,
                // OkHttp version mismatch throws IncompatibleClassChangeError.
                // D-209: detect CloudflareException → show the "Open in WebView" button.
                if (e is CloudflareException) {
                    Logger.w(TAG) { "Cloudflare blocked ${source.name}: ${e.reason} (url=${e.url})" }
                    _episodeState.value = EpisodeState.CloudflareBlocked(
                        url = e.url, sourceName = source.name,
                    )
                } else {
                    val errorMsg = "${e::class.java.simpleName}: ${e.message ?: "Unknown error"}"
                    Logger.e(TAG, e) { "Episode fetch failed for ${source.name}: $errorMsg" }
                    _episodeState.value = EpisodeState.Error(errorMsg)
                }
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

    /**
     * D-210: Returns the source's episode page URL for the WebView.
     * Constructs baseUrl + animeUrl from the currently linked source.
     * Used by the ResolverSheet's "Open in WebView" button when video
     * resolution fails (e.g. Cloudflare blocked the episode page).
     *
     * @return the full URL (e.g. "https://miruro.tv/watch/sakamoto-days"),
     *   or null if no source is linked or the source doesn't expose a baseUrl.
     */
    fun getSourceEpisodeUrl(): String? {
        val linked = _linkedSource.value ?: return null
        val source = extensionManager.getSource(linked.sourceId) as? AnimeHttpSource ?: return null
        val baseUrl = source.baseUrl
        val animeUrl = linked.animeUrl
        return if (animeUrl.startsWith("http")) animeUrl
               else baseUrl.trimEnd('/') + "/" + animeUrl.trimStart('/')
    }

    // ── D.6: Episode download management ────────────────────────────────────
    //
    // The enqueue path is handled by the host (MainActivity → DownloadOrchestrator)
    // via a callback lambda — :feature:anime-details doesn't depend on :app's
    // DownloadOrchestrator (it would create a circular dep). Pause/resume/cancel/
    // retry/delete go directly through [downloadManager] (the :core:download
    // boundary is fine — :feature:anime-details depends on :core:download).
    //
    // The episodeKey used here is the SEpisode.url — stable across re-resolves
    // + matches the DownloadOrchestrator's enqueue path (which derives episodeKey
    // from the same source).

    /** Builds the per-episode lookup key used by [downloadStates]. */
    fun episodeDownloadStateKey(episode: eu.kanade.tachiyomi.animesource.model.SEpisode): String? {
        val mainId = currentMainId ?: return null
        return "$mainId|${episode.url}"
    }

    /** Pauses the download for [episode] (if a task exists). */
    fun pauseEpisodeDownload(episode: eu.kanade.tachiyomi.animesource.model.SEpisode) {
        val taskId = findTaskId(episode) ?: return
        viewModelScope.launch { downloadManager.pauseDownload(taskId) }
    }

    /** Resumes the download for [episode] (if a task exists). */
    fun resumeEpisodeDownload(episode: eu.kanade.tachiyomi.animesource.model.SEpisode) {
        val taskId = findTaskId(episode) ?: return
        viewModelScope.launch { downloadManager.resumeDownload(taskId) }
    }

    /** Cancels the download for [episode] (if a task exists). */
    fun cancelEpisodeDownload(episode: eu.kanade.tachiyomi.animesource.model.SEpisode) {
        val taskId = findTaskId(episode) ?: return
        viewModelScope.launch { downloadManager.cancelDownload(taskId) }
    }

    /** Retries the errored download for [episode] (if a task exists). */
    fun retryEpisodeDownload(episode: eu.kanade.tachiyomi.animesource.model.SEpisode) {
        val taskId = findTaskId(episode) ?: return
        viewModelScope.launch { downloadManager.retryDownload(taskId) }
    }

    /** Deletes the downloaded episode (file + DB row). */
    fun deleteDownloadedEpisode(episode: eu.kanade.tachiyomi.animesource.model.SEpisode) {
        val mainId = currentMainId ?: return
        viewModelScope.launch {
            downloadManager.deleteDownloadedEpisode(mainId, episode.url)
        }
    }

    /** Looks up a task ID by (mainId, episodeKey) in the live queue. */
    private fun findTaskId(episode: eu.kanade.tachiyomi.animesource.model.SEpisode): Long? {
        val mainId = currentMainId ?: return null
        val task = downloadManager.getQueue().value.firstOrNull {
            it.content.mainId == mainId && it.episode.episodeKey == episode.url
        }
        return task?.id
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

/**
 * D-225c: Reverse auto-link state — drives the [AutoLinkPopup] for the
 * AniList → extensions search direction.
 *
 * - [Idle]: nothing happening (initial / dismissed / skipped).
 * - [Searching]: extensions are being queried (popup shows spinner + "Searching…").
 * - [Matched]: a source was linked (popup confirms + auto-dismisses).
 * - [NoMatch]: no confident match (popup offers a "Link manually" action).
 * - [Error]: search failed (popup shows the error + auto-dismisses).
 */
sealed interface ReverseAutoLinkState {
    data object Idle : ReverseAutoLinkState
    data object Searching : ReverseAutoLinkState
    data class Matched(
        val sourceName: String,
        val animeTitle: String,
        val score: Float,
    ) : ReverseAutoLinkState
    data class NoMatch(
        val bestScore: Float,
        val searchedTitle: String,
    ) : ReverseAutoLinkState
    data class Error(val message: String) : ReverseAutoLinkState
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
    /**
     * D-209: Cloudflare blocked the episode fetch + the headless solver failed.
     * The UI shows an "Open in WebView" button + a "Refresh" button.
     */
    data class CloudflareBlocked(val url: String, val sourceName: String) : EpisodeState
}

// ── D.3: Multi-stage refresh types ──

/** The three refresh stages (triggered by scroll position). */
enum class RefreshStage(val label: String) {
    EPISODES("Refresh episodes list"),
    METADATA("Refresh metadata"),
    ALL("Refresh all"),
}

/** State of the multi-stage refresh. */
sealed interface RefreshState {
    data object Idle : RefreshState
    data class StageReached(val stage: RefreshStage) : RefreshState
    data class Refreshing(val stage: RefreshStage) : RefreshState
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
