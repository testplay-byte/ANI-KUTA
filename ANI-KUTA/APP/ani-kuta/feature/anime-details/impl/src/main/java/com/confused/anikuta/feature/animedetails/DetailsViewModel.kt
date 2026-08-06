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
    private val contentResolver: com.confused.anikuta.core.content.ContentResolver,
    private val contentRepository: com.confused.anikuta.core.content.ContentRepository,
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

    /** Library state — whether the current anime is in the user's library. */
    private val _isInLibrary = MutableStateFlow(false)
    val isInLibrary: StateFlow<Boolean> = _isInLibrary.asStateFlow()

    /** The contentId of the current anime (for logging/debugging). */
    private val _contentId = MutableStateFlow("")
    val contentId: StateFlow<String> = _contentId.asStateFlow()

    private var currentAnimeId: Int = 0
    private var currentMainId: String? = null

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
        val (primary, secondary) = if (priority == com.confused.anikuta.core.common.model.DataSourcePriority.ANILIST) {
            al to ext
        } else {
            ext to al
        }
        val merged = primary.copy(
            // Identity fields — always from whichever base has them.
            anilistId = al.anilistId ?: ext.anilistId,
            sourceId = ext.sourceId ?: al.sourceId,
            sourceName = ext.sourceName ?: al.sourceName,
            animeUrl = ext.animeUrl ?: al.animeUrl,
            entryMode = ext.entryMode, // Entry mode stays from extension (how the user opened it).
            dataSourcePriority = priority,
            // Metadata fields — primary wins, secondary fills nulls.
            description = primary.description ?: secondary.description,
            genres = if (primary.genres.isNotEmpty()) primary.genres else secondary.genres,
            status = primary.status ?: secondary.status,
            episodes = primary.episodes ?: secondary.episodes,
            averageScore = primary.averageScore ?: secondary.averageScore,
            season = primary.season ?: secondary.season,
            seasonYear = primary.seasonYear ?: secondary.seasonYear,
            bannerUrl = primary.bannerUrl ?: secondary.bannerUrl,
            idMal = primary.idMal ?: secondary.idMal,
            coverUrl = primary.coverUrl ?: secondary.coverUrl,
        )
        _state.value = DetailsState.Success(merged)
        Logger.d(TAG) { "remergeBases: priority=$priority, merged ${merged.displayName}" }
    }

    // ── Load from AniList (existing flow) ──

    fun loadFromAniList(animeId: Int) {
        currentAnimeId = animeId
        // CRITICAL: Reset ALL state when loading a new anime (D-131).
        _state.value = DetailsState.Loading
        _autoLinkState.value = AutoLinkState.Idle
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
        currentMainId = null
        // D-134: Reset the data bases.
        extensionBase = null
        anilistBase = null
        viewModelScope.launch {
            try {
                val anime = anilistApi.fetchAnimeDetails(animeId)
                Logger.i(TAG) { "Loaded AniList details for $animeId" }
                anilistBase = anime.toUnifiedAnime() // D-134: store original AniList data.
                remergeBases(com.confused.anikuta.core.common.model.DataSourcePriority.ANILIST)

                // Phase C: Resolve/create content record + check library status.
                resolveContentForAniList(animeId, anime.displayName, anime)

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
        // CRITICAL: Reset ALL state when loading a new anime (D-131).
        _state.value = DetailsState.Loading
        _autoLinkState.value = AutoLinkState.Idle
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
        currentMainId = null
        // D-134: Reset the data bases.
        extensionBase = null
        anilistBase = null
        viewModelScope.launch {
            try {
                // Use the ExtensionDetailsProvider to fetch full details.
                val unifiedAnime = extensionProvider.fetchFromExtension(sourceId, animeUrl, title, thumbnailUrl)

                if (unifiedAnime != null) {
                    Logger.i(TAG) { "Loaded extension details: $title from source $sourceId" }
                    extensionBase = unifiedAnime // D-134: store original extension data.
                    remergeBases(com.confused.anikuta.core.common.model.DataSourcePriority.EXTENSION)

                    // Phase C: Resolve/create content record + check library status.
                    resolveContentForExtension(sourceId, animeUrl, title)

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
            val detail = com.confused.anikuta.core.content.AniListDetail(
                mainId = "", // Will be set by resolver.
                anilistId = anilistId,
                idMal = anime.idMal,
                score = anime.averageScore,
                episodes = anime.episodes,
                season = anime.season,
                seasonYear = anime.seasonYear,
                status = anime.status,
                genres = anime.genres?.joinToString(", "),
                synopsis = anime.description,
                coverUrl = anime.coverUrl,
                bannerUrl = anime.bannerImage,
                updatedAt = System.currentTimeMillis(),
            )
            val mainId = contentResolver.resolveOrCreateForAniList(anilistId, title, detail)
            currentMainId = mainId
            refreshContentAndLibraryStatus(mainId)
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
    ) {
        try {
            // D-137: Check auto-link cache first.
            val cachedAniListId = autoLinkPreferences.getCachedAniListId(sourceId, animeUrl)
            if (cachedAniListId > 0) {
                // Check if a content record already exists for this anilistId.
                val existingContent = contentRepository.getContentByAniListId(cachedAniListId)
                if (existingContent != null) {
                    Logger.i(TAG) { "Cross-source match: extension ($sourceId, $animeUrl) → existing mainId=${existingContent.mainId} (via cached anilistId=$cachedAniListId)" }
                    // Link this extension entry to the existing content record.
                    contentResolver.linkExtensionToExisting(
                        mainId = existingContent.mainId,
                        extensionId = sourceId,
                        sourceId = sourceId,
                        animeUrl = animeUrl,
                        title = title,
                    )
                    currentMainId = existingContent.mainId
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
            currentMainId = mainId
            refreshContentAndLibraryStatus(mainId)
        } catch (e: Exception) {
            Logger.e(TAG, e) { "resolveContentForExtension failed: ${e.message}" }
        }
    }

    /**
     * Refresh the contentId + library status from the repository.
     */
    private fun refreshContentAndLibraryStatus(mainId: String) {
        val content = contentRepository.getContentByMainId(mainId)
        if (content != null) {
            _contentId.value = content.contentId
            Logger.i(TAG) { "Content ID: ${content.contentId}" }
        }
        _isInLibrary.value = contentRepository.isInLibrary(mainId)
        Logger.i(TAG) { "Library status: ${if (_isInLibrary.value) "in library" else "not in library"}" }
    }

    /**
     * Refresh just the contentId (after a link/unlink operation).
     */
    private fun refreshContentId(mainId: String) {
        val content = contentRepository.getContentByMainId(mainId)
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
        } else {
            contentRepository.addToDefaultCategory(mainId)
            _isInLibrary.value = true
            Logger.i(TAG) { "Added to library (Default): mainId=$mainId" }
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
            // This ensures the anilist_detail row is created + the content record's
            // dataSourceId is set. When the same anime is opened from another source
            // later, the content resolver can find this mainId via the anilistId.
            val mainId = currentMainId
            if (mainId != null) {
                val detail = com.confused.anikuta.core.content.AniListDetail(
                    mainId = mainId,
                    anilistId = anilistId,
                    idMal = anilistData.idMal,
                    score = anilistData.averageScore,
                    episodes = anilistData.episodes,
                    season = anilistData.season,
                    seasonYear = anilistData.seasonYear,
                    status = anilistData.status,
                    genres = anilistData.genres?.joinToString(", "),
                    synopsis = anilistData.description,
                    coverUrl = anilistData.coverUrl,
                    bannerUrl = anilistData.bannerUrl,
                    updatedAt = System.currentTimeMillis(),
                )
                contentResolver.linkAniList(mainId, anilistId, detail)
                // Refresh the contentId (it changed after linking).
                refreshContentId(mainId)
            }

            // Now that we have an anilistId, kick off episode metadata fetch.
            val malId = anilistData.idMal
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

        // D-134: Create extensionBase from the picked SAnime (for AniList entries).
        // This makes the data-source selector available.
        if (extensionBase == null) {
            extensionBase = sAnime.toUnifiedAnime(source.id, source.name)
            Logger.i(TAG) { "Extension base created from picked SAnime: ${sAnime.title}" }
            // Re-merge to update the display (keeps current priority).
            val currentPriority = (_state.value as? DetailsState.Success)?.anime?.dataSourcePriority
                ?: com.confused.anikuta.core.common.model.DataSourcePriority.ANILIST
            remergeBases(currentPriority)
        }

        fetchEpisodes(source, sAnime.url, sAnime.title)
    }

    /**
     * Unlink the current source.
     *
     * D-134: Clears [extensionBase] + re-merge (shows AniList data only, if available).
     */
    fun unlinkSource() {
        val animeId = currentAnimeId
        Logger.i(TAG) { "Unlinking source for anime $animeId" }
        preferenceStore.putString(KEY_SOURCE_LINK_PREFIX + animeId, "")
        _linkedSource.value = null
        _episodeState.value = EpisodeState.Idle

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
