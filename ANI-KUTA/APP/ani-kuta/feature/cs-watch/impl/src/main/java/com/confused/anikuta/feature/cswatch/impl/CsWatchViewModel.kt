package com.confused.anikuta.feature.cswatch.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.csplayer.CsSubtitle
import com.confused.anikuta.core.csplayer.CsVideoLink
import com.confused.anikuta.core.watchprogress.WatchProgress
import com.confused.anikuta.core.watchprogress.WatchProgressStore
import com.confused.anikuta.data.cloudstream.playback.CloudstreamLinkResolver
import com.confused.anikuta.data.cloudstream.playback.CloudstreamLinkResolver.CsResolveEvent
import com.confused.anikuta.feature.cswatch.api.CsWatchKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The CloudStream watch screen's state coordinator (task 52 / Phase D).
 *
 * Owns everything EXCEPT the engine object itself (the screen composable owns
 * the ExoPlayer lifecycle per the ADR-025 player carve-out): link resolution
 * (progressive snapshots), link selection + failure bookkeeping, episode
 * navigation, and watch-progress persistence.
 *
 * Screen ↔ ViewModel contract: the screen observes [uiState]; whenever
 * [CsWatchUiState.playRequestId] changes it drives the engine
 * (start/switchLink with [CsWatchUiState.playLink] + [CsWatchUiState.playSubtitles]
 * + [CsWatchUiState.playStartPositionMs]). Engine events flow the other way
 * through [onEngineError] / [onEpisodeEnded].
 */
class CsWatchViewModel(
    private val resolver: CloudstreamLinkResolver,
    private val watchProgressStore: WatchProgressStore,
    private val sourceMemory: com.confused.anikuta.data.cloudstream.playback.CsSourceMemory,
    /** Task 55: the sub/dub display mode (episode switching resolves sibling handles in COMBINED). */
    private val episodeListPreferences: com.confused.anikuta.core.preferences.EpisodeListPreferences,
) : ViewModel() {

    companion object {
        internal const val TAG = "Anikuta:CS:Watch"

        /** The app-wide standardized episode key (feature:watch's builder format, replicated — the two modules stay independent). */
        internal fun episodeKey(mainId: String, episodeNumber: Float): String {
            if (mainId.isBlank()) return "unknown|${String.format("%05d", episodeNumber.toInt())}"
            return "$mainId|${String.format("%05d", episodeNumber.toInt())}"
        }
    }

    /** What the screen should render at the top level. */
    enum class Phase { RESOLVING, PLAYING, FAILED, NO_LINKS }

    data class CsWatchUiState(
        val phase: Phase = Phase.RESOLVING,
        val animeTitle: String = "",
        val providerName: String = "",
        val episodeNumber: Float = 0f,
        val episodeTitle: String = "",
        /** The full episode list (rows for the episodes sheet + the watch page). */
        val episodes: List<com.confused.anikuta.feature.cswatch.api.CsSimpleEpisode> = emptyList(),
        /** Task 54 (round 14): per-episode metadata for the watch PAGE's
         *  currently-playing section + episode rows (title/thumb/air date/
         *  description/sub-dub). Keyed by episode number like the aniyomi map. */
        val episodeMetadata: Map<Int, com.confused.anikuta.feature.cswatch.api.CsWatchEpisodeMeta> = emptyMap(),
        val links: List<CsVideoLink> = emptyList(),
        val subtitles: List<CsSubtitle> = emptyList(),
        val hiddenTorrentCount: Int = 0,
        val unsupportedDrmCount: Int = 0,
        /** URLs of links that errored in the engine (auto-skip + sheet badges). */
        val failedLinkUrls: Set<String> = emptySet(),
        /** Task 53 / RC-8: per-URL failure reason ("HTTP 428", "Parsing"…) for the exhausted message. */
        val failureReasons: Map<String, String> = emptyMap(),
        val currentLink: CsVideoLink? = null,
        val resolveCompleted: Boolean = false,
        val resolveError: String? = null,
        /**
         * Task 53 / RC-3 — the generation lock. Every resolution attempt bumps
         * [resolveGeneration]; every play request stamps [playGeneration]. The
         * screen's engine trigger accepts a request ONLY while the two match,
         * killing the collectAsState one-dispatch-lag race that replayed the
         * PREVIOUS episode's link on a fresh engine.
         */
        val resolveGeneration: Int = 0,
        val playGeneration: Int = 0,
        /** Bumped whenever the engine should hard-reset (new episode resolution start). */
        val engineResetTick: Int = 0,
        /** Bumped whenever the engine should (re)load — the screen's play trigger. */
        val playRequestId: Int = 0,
        val playLink: CsVideoLink? = null,
        val playSubtitles: List<CsSubtitle> = emptyList(),
        val playStartPositionMs: Long = 0L,
        /** Whether this play request should SEEK to playStartPositionMs (resume) or reset (link switch). */
        val playIsResume: Boolean = false,
        /** R12-REVIEW F2: true ONLY for same-episode link switches (quality/source
         *  change, error fallback, subtitle reattach) — the engine keeps the
         *  position. FALSE for a new episode's first link (auto-advance would
         *  otherwise inherit the PREVIOUS episode's end-position and cascade
         *  through the whole season). */
        val playKeepPosition: Boolean = false,
        val resumePositionMs: Long = 0L,
    )

    private val _uiState = MutableStateFlow(CsWatchUiState())
    val uiState: StateFlow<CsWatchUiState> = _uiState.asStateFlow()

    private var resolveJob: Job? = null
    private var currentKey: CsWatchKey? = null

    /**
     * Task 53 / RC-6: a resolution produced OUTSIDE this VM (the resolve
     * sheet at the details-page entry) — adopted by [initialize] instead of
     * re-resolving, so the watch screen starts INSTANTLY with the full list.
     */
    private var pendingSeed: PreResolvedSeed? = null

    /** The resolve sheet's handoff payload (see [pendingSeed]). */
    data class PreResolvedSeed(
        val key: CsWatchKey,
        val links: List<CsVideoLink>,
        val subtitles: List<CsSubtitle>,
        val selectedLink: CsVideoLink,
        val hiddenTorrentCount: Int,
        val unsupportedDrmCount: Int,
    )

    /** The resolve sheet calls this right before navigating to the watch screen. */
    fun seedResolution(seed: PreResolvedSeed) {
        pendingSeed = seed
        Logger.i(TAG) {
            "seeded: '${seed.key.animeTitle}' EP ${seed.key.episodeNumber} " +
                "links=${seed.links.size} subs=${seed.subtitles.size} selected=${seed.selectedLink.displayLabel}"
        }
    }

    /**
     * Entry point — the screen calls this on every composition with its nav
     * key. THREE cases (R12-REVIEW F1 — the VM is activity-scoped, so it
     * SURVIVES navigation; a hard-return here made the second CS-watch visit
     * replay the FIRST episode):
     *  - first entry (currentKey == null) → seed + resolve;
     *  - SAME key re-entry (back → tap the same episode) → keep the resolved
     *    state, but re-request playback with a FRESH resume lookup (the new
     *    composition's engine is empty); — the resolution flow re-requests
     *    play on the first snapshot anyway, so a fresh lookup happens there;
     *  - DIFFERENT key (VM survived, new episode/show tapped) → full reset +
     *    resolve (the house DetailsViewModel pattern).
     */
    fun initialize(key: CsWatchKey) {
        // Task 53 / RC-6: the resolve sheet's handoff wins over every other
        // path — one-shot, strictly key-matched (a stale seed is dropped).
        val seed = pendingSeed
        if (seed != null && seed.key == key) {
            pendingSeed = null
            adoptSeed(seed)
            return
        }
        pendingSeed = null
        when {
            currentKey == null -> seedAndResolve(key)
            currentKey == key -> {
                // New composition = new engine; re-request playback with a FRESH
                // resume lookup (the entry-time startPosition is stale after a
                // watched session). Falls back to re-resolving when the first
                // entry never produced a link.
                Logger.i(TAG) { "re-entry: same episode — re-requesting play with fresh resume" }
                viewModelScope.launch {
                    val link = _uiState.value.currentLink
                    if (link != null) {
                        // R13-REVIEW F2: the DB lookup suspends — capture the
                        // decision context and re-validate after; a resolution
                        // that started meanwhile wins and drops this request.
                        val gen = _uiState.value.resolveGeneration
                        val resumeMs = lookupResumePositionMs()
                        if (_uiState.value.resolveGeneration == gen && currentKey == key) {
                            requestPlay(link, resumeMs, isResume = true, keepPosition = false)
                        } else {
                            Logger.w(TAG) { "re-entry play dropped: resolution changed during resume lookup" }
                        }
                    } else {
                        startResolution(key)
                    }
                }
            }
            else -> {
                Logger.i(TAG) { "re-entry: NEW episode '${key.animeTitle}' EP ${key.episodeNumber} — resetting" }
                seedAndResolve(key)
            }
        }
    }

    /** Adopts the resolve sheet's handoff — no re-resolve, instant playback. */
    private fun adoptSeed(seed: PreResolvedSeed) {
        // R13-REVIEW F1: a resolution still running from a PREVIOUS episode
        // (e.g. the user backed out mid-RESOLVING and tapped another episode)
        // must die here — its snapshots would resurrect the stale episode over
        // this seed.
        resolveJob?.cancel()
        resolveJob = null
        pendingSubSelectId = null

        val key = seed.key
        currentKey = key
        val generation = _uiState.value.resolveGeneration + 1
        Logger.i(TAG) {
            "open: SEEDED '${key.animeTitle}' EP ${key.episodeNumber} provider=${key.providerName} " +
                "mainId=${key.mainId.take(8)}… links=${seed.links.size} subs=${seed.subtitles.size} " +
                "gen=$generation — skipping resolve"
        }
        _uiState.value = CsWatchUiState(
            animeTitle = key.animeTitle,
            providerName = key.providerName,
            episodeNumber = key.episodeNumber,
            episodeTitle = key.episodeTitle,
            episodes = key.parseEpisodeList(),
            episodeMetadata = key.parseEpisodeMetadata(),
            links = seed.links,
            subtitles = seed.subtitles,
            hiddenTorrentCount = seed.hiddenTorrentCount,
            unsupportedDrmCount = seed.unsupportedDrmCount,
            currentLink = seed.selectedLink,
            resolveCompleted = true,
            resolveGeneration = generation,
            engineResetTick = _uiState.value.engineResetTick + 1,
        )
        viewModelScope.launch {
            val resumeMs = if (key.startPosition > 0) key.startPosition else lookupResumePositionMs()
            // R13-REVIEW F2: re-validate after the (suspending) resume lookup.
            if (_uiState.value.resolveGeneration == generation && currentKey == key) {
                requestPlay(seed.selectedLink, resumeMs, isResume = resumeMs > 0, keepPosition = false)
            } else {
                Logger.w(TAG) { "seeded play dropped: resolution changed during resume lookup" }
            }
        }

        // R13-REVIEW F4: the sheet's collection died at pick time (mid-flight
        // for early picks), so the handed-off list may be PARTIAL. A quiet
        // top-up walk keeps the Sources sheet + the error-fallback chain fully
        // saturated — append-only, same-generation-guarded, never touches
        // playback. (The resolver caches on completion, so re-taps stay fast.)
        resolveJob = viewModelScope.launch {
            resolver.resolve(key.providerName, key.episodeData).collect { event ->
                val state = _uiState.value
                if (state.resolveGeneration != generation) return@collect
                when (event) {
                    is CsResolveEvent.LinksSnapshot -> {
                        val known = state.links.mapTo(mutableSetOf()) { it.url }
                        val fresh = event.links.filterNot { it.url in known }
                        if (fresh.isNotEmpty()) {
                            val merged = state.links + fresh
                            _uiState.value = state.copy(
                                links = merged,
                                hiddenTorrentCount = event.hiddenTorrentCount,
                                unsupportedDrmCount = event.unsupportedDrmCount,
                            )
                            refreshCurrentLink(merged)
                            Logger.i(TAG) { "seed top-up: links ${state.links.size} → ${merged.size}" }
                        }
                    }
                    is CsResolveEvent.SubtitlesSnapshot -> {
                        val known = state.subtitles.mapTo(mutableSetOf()) { it.id }
                        val fresh = event.subtitles.filterNot { it.id in known }
                        if (fresh.isNotEmpty()) {
                            _uiState.value = state.copy(subtitles = state.subtitles + fresh)
                            Logger.i(TAG) { "seed top-up: subs ${state.subtitles.size} → ${state.subtitles.size + fresh.size}" }
                        }
                    }
                    is CsResolveEvent.Completed,
                    is CsResolveEvent.Failed,
                    -> Unit // saturated (or the sheet already surfaced the failure)
                }
            }
        }
    }

    private fun seedAndResolve(key: CsWatchKey) {
        currentKey = key
        Logger.i(TAG) {
            "open: '${key.animeTitle}' EP ${key.episodeNumber} provider=${key.providerName} " +
                "mainId=${key.mainId.take(8)}… episodes=${key.parseEpisodeList().size} resumeHint=${key.startPosition}ms"
        }
        _uiState.value = CsWatchUiState(
            animeTitle = key.animeTitle,
            providerName = key.providerName,
            episodeNumber = key.episodeNumber,
            episodeTitle = key.episodeTitle,
            episodes = key.parseEpisodeList(),
            episodeMetadata = key.parseEpisodeMetadata(),
        )
        startResolution(key)
    }

    // ── Resolution ────────────────────────────────────────────────────────────

    private fun startResolution(key: CsWatchKey) {
        resolveJob?.cancel()
        val generation = _uiState.value.resolveGeneration + 1
        _uiState.value = _uiState.value.copy(
            phase = Phase.RESOLVING,
            links = emptyList(),
            subtitles = emptyList(),
            hiddenTorrentCount = 0,
            unsupportedDrmCount = 0,
            failedLinkUrls = emptySet(),
            failureReasons = emptyMap(),
            currentLink = null,
            resolveCompleted = false,
            resolveError = null,
            resolveGeneration = generation,
            engineResetTick = _uiState.value.engineResetTick + 1,
        )
        // Task 55: COMBINED sub/dub — resolve the tapped handle + its
        // opposite-flavor sibling (same episode number) and tag each stream
        // so the links sheet's audio chips can separate them. SEPARATE (the
        // default) resolves the one handle, tagged with its own flavor.
        val handles = run {
            val combined = episodeListPreferences.subDubMode.get() == "COMBINED"
            com.confused.anikuta.feature.cswatch.api.CsSubDubSiblings
                .handlesFor(key.parseEpisodeList(), key.episodeData, combined)
        }
        Logger.i(TAG) {
            "resolve: generation=$generation episode=${key.episodeNumber} " +
                "provider=${key.providerName} handles=${handles.size} " +
                "tags=${handles.joinToString { it.audioTag ?: "-" }} — engineResetTick=${_uiState.value.engineResetTick}"
        }
        resolveJob = viewModelScope.launch {
            var firstLinkPlayed = false
            val initialResume = key.startPosition
            // Per-handle snapshots — merged with url dedup (first wins).
            val perHandle = mutableMapOf<Int, List<CsVideoLink>>()
            val perHandleSubs = mutableMapOf<Int, List<com.confused.anikuta.core.csplayer.CsSubtitle>>()
            val perHandleHidden = IntArray(handles.size)
            val perHandleDrm = IntArray(handles.size)
            val finishedHandles = mutableSetOf<Int>()
            handles.forEachIndexed { index, handle ->
                launch {
                    resolver.resolve(key.providerName, handle.data).collect { event ->
                        when (event) {
                            is CsResolveEvent.LinksSnapshot -> {
                                perHandle[index] = event.links.map { link ->
                                    if (handle.audioTag != null) link.copy(audioTag = handle.audioTag) else link
                                }
                                perHandleHidden[index] = event.hiddenTorrentCount
                                perHandleDrm[index] = event.unsupportedDrmCount
                                val known = mutableSetOf<String>()
                                val merged = handles.indices.flatMap { i -> perHandle[i].orEmpty() }
                                    .filter { known.add(it.url) }
                                val hadLinks = _uiState.value.links.isNotEmpty()
                                _uiState.value = _uiState.value.copy(
                                    links = merged,
                                    hiddenTorrentCount = perHandleHidden.sum(),
                                    unsupportedDrmCount = perHandleDrm.sum(),
                                )
                                // Upstream streaming-into-player: start on the FIRST link;
                                // keep collecting for the sheet.
                                if (!firstLinkPlayed && merged.isNotEmpty()) {
                                    firstLinkPlayed = true
                                    val resumeMs = if (initialResume > 0) initialResume else lookupResumePositionMs()
                                    autoStart(merged, resumeMs)
                                } else if (hadLinks) {
                                    refreshCurrentLink(merged)
                                }
                            }
                            is CsResolveEvent.SubtitlesSnapshot -> {
                                perHandleSubs[index] = event.subtitles
                                val knownIds = mutableSetOf<String>()
                                _uiState.value = _uiState.value.copy(
                                    subtitles = handles.indices.flatMap { i -> perHandleSubs[i].orEmpty() }
                                        .filter { knownIds.add(it.id) },
                                )
                            }
                            is CsResolveEvent.Completed -> {
                                finishedHandles += index
                                if (finishedHandles.size == handles.size) {
                                    _uiState.value = _uiState.value.copy(resolveCompleted = true)
                                    if (_uiState.value.links.isEmpty()) {
                                        _uiState.value = _uiState.value.copy(
                                            phase = Phase.NO_LINKS,
                                            resolveError = buildNoLinksMessage(event),
                                        )
                                        Logger.w(TAG) { "resolution completed with ZERO links: ${buildNoLinksMessage(event)}" }
                                    }
                                }
                            }
                            is CsResolveEvent.Failed -> {
                                finishedHandles += index
                                Logger.w(TAG) {
                                    "handle #${index + 1} resolution failed: ${event.message} " +
                                        "(linksSoFar=${event.linksSoFar})"
                                }
                                if (finishedHandles.size == handles.size) {
                                    _uiState.value = _uiState.value.copy(
                                        resolveCompleted = true,
                                        resolveError = event.message,
                                    )
                                    // Links that already arrived stay usable (upstream behavior:
                                    // a late provider error after links = logged, not fatal).
                                    if (_uiState.value.links.isEmpty()) {
                                        _uiState.value = _uiState.value.copy(phase = Phase.FAILED)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Picks the best link (remembered server first, then quality desc, skipping failed) and requests playback. */
    private fun autoStart(links: List<CsVideoLink>, resumeMs: Long) {
        val candidates = links.filterNot { it.url in _uiState.value.failedLinkUrls }
        // Task 56: keep the user's FLAVOR across episode switches — auto-start
        // prefers the target row's flavor pool (a dub watcher auto-advancing
        // stays on dub streams) and falls back to the full pool when the
        // flavor has no streams (dual-audio / untagged links).
        val flavorPool = preferredFlavor
            ?.let { f -> candidates.filter { it.audioLabel == f } }
            ?.takeIf { it.isNotEmpty() }
            ?: candidates
        // Task 53 / RC-6: the remembered server for this anime wins (AnymeX
        // dub/server-consistency scoring, simplified) — auto-advance keeps the
        // same server across episodes; quality within it stays max-first.
        val remembered = currentKey?.let { sourceMemory.recall(it.mainId) }
        val best = remembered
            ?.let { r -> flavorPool.filter { it.name == r }.maxByOrNull { it.quality } }
            ?: flavorPool.maxByOrNull { it.quality }
            ?: links.firstOrNull()
            ?: return
        preferredFlavor = null
        Logger.i(TAG) {
            "autoStart: ${best.displayLabel}" +
                (if (remembered != null && best.name == remembered) " (remembered server)" else "")
        }
        // A new episode's FIRST link is a FRESH start (resume ms or 0) — never
        // position-keeping (R12-REVIEW F2).
        requestPlay(best, resumeMs, isResume = resumeMs > 0, keepPosition = false)
    }

    /** Task 56: the target episode row's flavor (see [selectEpisode] / [autoStart]). */
    private var preferredFlavor: String? = null

    /** Keeps currentLink valid when a snapshot replaces the list objects. */
    private fun refreshCurrentLink(links: List<CsVideoLink>) {
        val current = _uiState.value.currentLink ?: return
        val stillThere = links.firstOrNull { it.url == current.url } ?: return
        if (stillThere !== current) {
            _uiState.value = _uiState.value.copy(currentLink = stillThere)
        }
    }

    private fun buildNoLinksMessage(completed: CsResolveEvent.Completed): String {
        val hidden = buildString {
            if (completed.hiddenTorrentCount > 0) append(" ${completed.hiddenTorrentCount} torrent link(s) hidden")
            if (completed.unsupportedDrmCount > 0) append(" ${completed.unsupportedDrmCount} DRM link(s) unsupported")
        }
        return if (hidden.isBlank()) {
            "No playable streams found for this episode"
        } else {
            "No playable streams found —$hidden"
        }
    }

    private fun requestPlay(
        link: CsVideoLink,
        startPositionMs: Long,
        isResume: Boolean,
        keepPosition: Boolean = false,
    ) {
        val state = _uiState.value
        Logger.i(TAG) {
            "play request: id=${state.playRequestId + 1} generation=${state.resolveGeneration} " +
                "link=${link.displayLabel} resume=$isResume keepPosition=$keepPosition positionMs=$startPositionMs"
        }
        _uiState.value = state.copy(
            phase = Phase.PLAYING,
            currentLink = link,
            playRequestId = state.playRequestId + 1,
            playGeneration = state.resolveGeneration,
            playLink = link,
            playSubtitles = state.subtitles,
            playStartPositionMs = startPositionMs,
            playIsResume = isResume,
            playKeepPosition = keepPosition,
        )
    }

    // ── User + engine actions ─────────────────────────────────────────────────

    /** The links sheet: user picked a specific link (same episode → keep position). */
    fun selectLink(link: CsVideoLink) {
        Logger.i(TAG) { "user selected link: ${link.displayLabel}" }
        currentKey?.let { sourceMemory.remember(it.mainId, link.name) }
        requestPlay(link, 0L, isResume = false, keepPosition = true)
    }

    /**
     * The engine reported an error for [url] — mark bad; auto-advance if it was
     * current. [failureReason] ("HTTP 428" / "Parsing" / …) rides along for the
     * all-links-exhausted message (Task 53 / RC-8 diagnosability).
     */
    fun onEngineError(url: String?, failureReason: String? = null) {
        val state = _uiState.value
        if (url == null || state.currentLink?.url != url) return
        val failed = state.failedLinkUrls + url
        val reasons = if (failureReason != null) {
            state.failureReasons + (url to failureReason)
        } else {
            state.failureReasons
        }
        _uiState.value = state.copy(failedLinkUrls = failed, failureReasons = reasons)
        val remaining = state.links.filterNot { it.url in failed }
        if (remaining.isEmpty()) {
            resolver.invalidate(state.providerName, currentKey?.episodeData ?: "")
            val reasonSummary = reasons.values.distinct().joinToString(", ")
            _uiState.value = _uiState.value.copy(
                phase = Phase.FAILED,
                resolveError = "All ${state.links.size} stream(s) failed" +
                    if (reasonSummary.isNotBlank()) " — $reasonSummary" else " — every link was tried",
            )
            Logger.w(TAG) {
                "ALL links exhausted (${state.links.size}); cache invalidated; reasons: $reasonSummary"
            }
        } else {
            val next = remaining.maxByOrNull { it.quality } ?: remaining.first()
            Logger.i(TAG) {
                "auto-advancing to next link: ${next.displayLabel} (${remaining.size - 1} left)" +
                    (failureReason?.let { " (previous failed: $it)" } ?: "")
            }
            requestPlay(next, 0L, isResume = false, keepPosition = true)
        }
    }

    /** The engine reached the end — auto-advance when there is a next episode. */
    fun onEpisodeEnded(): Boolean {
        val next = nextEpisode() ?: return false
        Logger.i(TAG) { "episode ended — auto-advancing to EP ${next.episodeNumber}" }
        selectEpisode(next)
        return true
    }

    /** The episodes sheet's current-episode highlight key (the CS data handle). */
    fun currentEpisodeData(): String = currentKey?.episodeData ?: ""

    fun nextEpisode(): com.confused.anikuta.feature.cswatch.api.CsSimpleEpisode? {
        val s = _uiState.value
        val idx = s.episodes.indexOfFirst { it.data == currentKey?.episodeData }
        if (idx < 0) return null
        return nextInFlavor(s, idx, +1)
    }

    fun prevEpisode(): com.confused.anikuta.feature.cswatch.api.CsSimpleEpisode? {
        val s = _uiState.value
        val idx = s.episodes.indexOfFirst { it.data == currentKey?.episodeData }
        if (idx < 0) return null
        return nextInFlavor(s, idx, -1)
    }

    /**
     * Task 56 (round 16): next/prev STAY WITHIN the current episode's flavor —
     * auto-advance from sub-12 no longer jumps into dub-13 (a mid-show language
     * switch the user never asked for). The current LINK's explicit audioTag
     * wins (a COMBINED-mode dub pick — the merged row is always the Sub
     * handle, so the row tag would lie about what the user is watching); a
     * tagged row is the fallback (SEPARATE mode — the handle IS the flavor);
     * anything else walks the raw order.
     */
    private fun nextInFlavor(
        s: CsWatchUiState,
        currentIndex: Int,
        direction: Int,
    ): com.confused.anikuta.feature.cswatch.api.CsSimpleEpisode? {
        val current = s.episodes[currentIndex]
        val flavor = s.currentLink?.audioTag?.takeIf { it == "SUB" || it == "DUB" }
            ?: com.confused.anikuta.feature.cswatch.api.CsSubDubSiblings.tagOf(current.name)
        val candidates = if (flavor == null) {
            s.episodes
        } else {
            s.episodes.filter {
                com.confused.anikuta.feature.cswatch.api.CsSubDubSiblings.tagOf(it.name) == flavor
            }
        }
        val position = candidates.indexOfFirst { it.data == current.data }
        return candidates.getOrNull(position + direction)
    }

    /** Switches the whole screen to another episode (sheet tap / next / prev). */
    fun selectEpisode(episode: com.confused.anikuta.feature.cswatch.api.CsSimpleEpisode) {
        val key = currentKey ?: return
        Logger.i(TAG) { "switching to EP ${episode.episodeNumber} (data=${episode.data.take(60)})" }
        // Task 56: carry the TARGET row's flavor into auto-start — the sheet's
        // render rows are tag-stripped, so the flavor comes from the RAW list
        // (SEPARATE: the tapped row's own flavor; auto-advance: the next row in
        // the same flavor — see nextInFlavor).
        preferredFlavor = _uiState.value.episodes
            .firstOrNull { it.data == episode.data }
            ?.let { com.confused.anikuta.feature.cswatch.api.CsSubDubSiblings.tagOf(it.name) }
        currentKey = key.copy(
            episodeData = episode.data,
            episodeNumber = episode.episodeNumber,
            episodeTitle = episode.name,
        )
        _uiState.value = _uiState.value.copy(
            episodeNumber = episode.episodeNumber,
            episodeTitle = episode.name,
        )
        startResolution(currentKey!!)
    }

    /** Re-loads the CURRENT link so late-arriving sidecar subtitles attach (the
     *  upstream REQUIRES_RELOAD pattern): playRequestId++ with isResume=false
     *  → the screen's switchLink keeps the position; the pending auto-select id
     *  is consumed by the screen once the reloaded tracks expose it. */
    fun reattachSubtitles(autoSelectSub: CsSubtitle?) {
        val link = _uiState.value.currentLink ?: return
        Logger.i(TAG) {
            "reattachSubtitles: reloading '${link.displayLabel}' with " +
                "${_uiState.value.subtitles.size} subtitle(s)" +
                if (autoSelectSub != null) ", will select '${autoSelectSub.name}'" else ""
        }
        pendingSubSelectId = autoSelectSub?.id
        requestPlay(link, 0L, isResume = false, keepPosition = true)
    }

    /** One-shot: the subtitle id to auto-select after a reattach (nulls when read). */
    fun consumePendingSubSelectId(): String? = pendingSubSelectId.also { pendingSubSelectId = null }

    private var pendingSubSelectId: String? = null

    /** Re-resolve from scratch (the error overlay's retry button). */
    fun retryResolution() {
        val key = currentKey ?: return
        resolver.invalidate(key.providerName, key.episodeData)
        Logger.i(TAG) { "retry resolution requested" }
        startResolution(key)
    }

    // ── Watch progress ────────────────────────────────────────────────────────

    /** The saved resume position for the CURRENT episode (milliseconds), 0 when fresh. Suspend — DB read. */
    private suspend fun lookupResumePositionMs(): Long {
        val key = currentKey ?: return 0L
        val stored = runCatching {
            watchProgressStore.get(episodeKey(key.mainId, key.episodeNumber))
        }.getOrNull()
        return stored?.takeIf { it.position > 0 && !it.isWatched }?.position?.times(1000L) ?: 0L
    }

    /** Persists progress (position/duration in ms — the store wants seconds). */
    fun saveProgress(positionMs: Long, durationMs: Long) {
        val key = currentKey ?: return
        if (durationMs <= 0) return
        val epKey = episodeKey(key.mainId, key.episodeNumber)
        viewModelScope.launch {
            val previous = runCatching { watchProgressStore.get(epKey) }.getOrNull()
            val progress = WatchProgress(
                episodeKey = epKey,
                mainId = key.mainId.ifBlank { null },
                position = positionMs / 1000L,
                duration = durationMs / 1000L,
                completed = false,
                completedAt = null,
                lastWatchedAt = System.currentTimeMillis(),
                watchCount = previous?.watchCount ?: 0,
                firstWatchedAt = previous?.firstWatchedAt ?: System.currentTimeMillis(),
                autoMarkSuppressed = previous?.autoMarkSuppressed ?: false,
                userMarkedWatched = previous?.userMarkedWatched ?: false,
            )
            runCatching { watchProgressStore.save(epKey, progress) }
                .onFailure { Logger.w(TAG) { "progress save failed: ${it.message}" } }
        }
    }

    override fun onCleared() {
        Logger.i(TAG) { "ViewModel cleared" }
        super.onCleared()
    }
}
