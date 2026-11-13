package com.confused.anikuta.feature.animebrowse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.anilist.api.AniListApi
import com.confused.anikuta.core.anilist.api.BrowseCacheCodec
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.datacache.DataCacheRepository
import com.confused.anikuta.core.schedule.ScheduleEntry
import com.confused.anikuta.core.schedule.ScheduleStore
import com.confused.anikuta.core.updates.EpisodeUpdate
import com.confused.anikuta.core.updates.UpdateStore
import com.confused.anikuta.core.watchprogress.WatchProgress
import com.confused.anikuta.core.watchprogress.WatchProgressStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * ViewModel for the HOME screen (round 148 — the "My screen" replacement of
 * the Browse tab; spec: DOCUMENTATION/PROJECT-MAP/11-HOME-MY-SCREEN-SPEC.md).
 *
 * The section stack (top → bottom), each loaded independently (partial
 * success — a failing section never kills the home):
 *
 * | Section            | Source                                                            |
 * |--------------------|-------------------------------------------------------------------|
 * | Hero               | trending (banners) → popular fallback (the BrowseViewModel logic) |
 * | Continue watching  | [WatchProgressStore.observeContinueWatching] (the round-144 v4 query, finally surfaced) |
 * | What to watch next | DERIVED — highest-watched + 1 per recently-watched mainId, plus the RELEASING `anime_update_state.next_airing_episode` ("airing next") |
 * | Releasing soon     | [ScheduleStore.observeUpcoming] (library semi-join, `scheduled_at ASC`, 7-day window) |
 * | Recently released  | [UpdateStore.observeAllUpdates] deduped per mainId to the newest  |
 * | Trending / Popular / Top Rated | the EXACT BrowseViewModel cache-first pattern under the NEW `home_*` browse_cache keys |
 *
 * The OLD "trending"/"popular"/"top_rated" keys stay untouched — the Search
 * tab's offline fallback co-owns them (the D-278 trap).
 *
 * Rate-limit discipline (spec §2): the three discovery fetches reuse the SAME
 * cache-first `loadSection` rhythm Browse uses (cache → expired → ONE fetch →
 * upsert, 6h TTL), run in parallel with partial-success fallback. No new
 * AniList queries — the hero + rails reuse `fetchBrowseSection`.
 *
 * Zero schema change: the personal rails are DERIVED at render time from the
 * tables that already own that truth (`watch_progress` v4, `episode_schedule`,
 * `episode_update`, `anime_update_state`) and enriched with title/cover via
 * [ContentRepository] (`main_entry` + `content_details`) — no new FKs.
 *
 * CORE_RULES §20: logged with tag "Anikuta:Feature:Home". §23: reactive state.
 */
class HomeViewModel(
    private val anilistApi: AniListApi,
    private val dataCacheRepository: DataCacheRepository,
    private val watchProgressStore: WatchProgressStore,
    private val scheduleStore: ScheduleStore,
    private val updateStore: UpdateStore,
    private val contentRepository: ContentRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "Anikuta:Feature:Home"

        // The NEW home_* storage keys (BrowseCacheCodec owns them so no drift).
        // The old "trending"/"popular"/"top_rated" keys are NOT touched —
        // Search co-owns those (D-278).
        private const val SECTION_TRENDING = BrowseCacheCodec.SECTION_HOME_TRENDING
        private const val SECTION_POPULAR = BrowseCacheCodec.SECTION_HOME_POPULAR
        private const val SECTION_TOP_RATED = BrowseCacheCodec.SECTION_HOME_TOP_RATED

        private const val DISCOVERY_SECTION_COUNT = 3

        /** The personal rails' cap (spec: cap 20). */
        private const val RAIL_CAP = 20

        /** Releasing-soon window (spec §1: `getUpcomingSchedule(now, +7d)`). */
        private const val SCHEDULE_WINDOW_MS = 7L * 24 * 60 * 60 * 1000

        /** Recent-watch pool the next-up derivation scans (a superset of the continue rail). */
        private const val RECENT_POOL = 40
    }

    private val _state = MutableStateFlow<HomeState>(HomeState.Loading)
    val state: StateFlow<HomeState> = _state.asStateFlow()

    /** Whether a background refresh is in progress (drives pull-to-refresh). */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // ── Discovery sections (the EXACT BrowseViewModel pattern, home_* keys) ──

    private val _trending = MutableStateFlow<List<AniListAnime>>(emptyList())
    val trending: StateFlow<List<AniListAnime>> = _trending.asStateFlow()

    private val _popular = MutableStateFlow<List<AniListAnime>>(emptyList())
    val popular: StateFlow<List<AniListAnime>> = _popular.asStateFlow()

    private val _topRated = MutableStateFlow<List<AniListAnime>>(emptyList())
    val topRated: StateFlow<List<AniListAnime>> = _topRated.asStateFlow()

    /**
     * The hero pager's items — up to 5 trending anime WITH banners, falling
     * back to the first 3 of the source so a hero always renders; when
     * trending is empty (cold start + trending cache miss) the hero falls
     * back to popular so it still renders offline (the BrowseViewModel logic,
     * reused verbatim over the two home discovery flows).
     */
    val heroItems: StateFlow<List<AniListAnime>> =
        combine(_trending, _popular) { trending, popular ->
            val heroSource = trending.ifEmpty { popular }
            heroSource.filter { !it.bannerImage.isNullOrBlank() }.take(5)
                .ifEmpty { heroSource.take(3) }
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Personal rails (derived + enriched on Dispatchers.IO) ────────────────

    private val _continueWatching = MutableStateFlow<List<HomeContinueItem>>(emptyList())
    val continueWatching: StateFlow<List<HomeContinueItem>> = _continueWatching.asStateFlow()

    private val _nextUp = MutableStateFlow<List<HomeNextUpItem>>(emptyList())
    val nextUp: StateFlow<List<HomeNextUpItem>> = _nextUp.asStateFlow()

    private val _releasingSoon = MutableStateFlow<List<HomeScheduleItem>>(emptyList())
    val releasingSoon: StateFlow<List<HomeScheduleItem>> = _releasingSoon.asStateFlow()

    private val _recentlyReleased = MutableStateFlow<List<HomeReleaseItem>>(emptyList())
    val recentlyReleased: StateFlow<List<HomeReleaseItem>> = _recentlyReleased.asStateFlow()

    init {
        loadDiscovery()
        observePersonalRails()
    }

    /** Loads all three discovery sections (trending + popular + top-rated), cache-first. */
    private fun loadDiscovery() {
        loadSection(SECTION_TRENDING, "TRENDING_DESC") { _trending.value = it }
        loadSection(SECTION_POPULAR, "POPULARITY_DESC") { _popular.value = it }
        loadSection(SECTION_TOP_RATED, "SCORE_DESC") { _topRated.value = it }
    }

    /**
     * Cache-first section load — the BrowseViewModel.loadSection rhythm:
     * cache read on IO → decode via the shared codec → instant display;
     * only when the cache is missing/expired one network fetch + upsert.
     */
    private fun loadSection(sectionKey: String, sort: String, onResult: (List<AniListAnime>) -> Unit) {
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) {
                dataCacheRepository.getBrowseCache(sectionKey)
            }
            var servedFromFreshCache = false
            if (cached != null) {
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
                    servedFromFreshCache = true
                }
            }
            if (cached == null || dataCacheRepository.isBrowseCacheExpired(sectionKey)) {
                fetchSection(sectionKey, sort, onResult)
            } else {
                settleDiscovery(null, servedFromFreshCache)
            }
        }
    }

    /**
     * In-flight network fetch counter. viewModelScope launches on Main, so a
     * plain Int is safe (single-threaded) — the same guard Browse uses so the
     * first of 3 parallel fetches can't clear the spinner early.
     */
    private var inFlightFetches = 0

    private suspend fun fetchSection(sectionKey: String, sort: String, onResult: (List<AniListAnime>) -> Unit) {
        inFlightFetches++
        _isRefreshing.value = true
        try {
            val anime = anilistApi.fetchBrowseSection(sort)
            Logger.i(TAG) { "Fetched ${anime.size} $sectionKey from network" }
            onResult(anime)
            val json = withContext(Dispatchers.IO) { BrowseCacheCodec.encode(anime) }
            withContext(Dispatchers.IO) {
                dataCacheRepository.upsertBrowseCache(sectionKey, json)
            }
            Logger.i(TAG) { "Cached ${anime.size} $sectionKey anime" }
            settleDiscovery(null, anime.isNotEmpty())
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Failed to fetch $sectionKey: ${e.message}" }
            settleDiscovery(e.message ?: "Unknown error", false)
        } finally {
            inFlightFetches--
            if (inFlightFetches <= 0) {
                inFlightFetches = 0
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Partial-success Error policy: home falls to the hard Error state ONLY
     * when ALL three discovery sections settled AND nothing at all loaded
     * (no cached discovery payload AND every personal rail empty). Anything
     * cached → Content; the empty sections simply don't render.
     */
    private var discoverySettled = 0
    private var lastDiscoveryError: String? = null

    private fun settleDiscovery(error: String?, servedSomething: Boolean) {
        if (error != null) lastDiscoveryError = error
        if (servedSomething) {
            if (_state.value is HomeState.Loading) _state.value = HomeState.Content
            promoteOutOfError()
            return
        }
        discoverySettled++
        if (discoverySettled >= DISCOVERY_SECTION_COUNT) {
            if (anyContentLoaded()) {
                _state.value = HomeState.Content
            } else if (_state.value !is HomeState.Error) {
                _state.value = HomeState.Error(lastDiscoveryError ?: "Failed to load")
            }
        }
    }

    /** Every rail collection calls this — a rail with data lifts the home out of Error. */
    private fun promoteOutOfError() {
        if (_state.value is HomeState.Error && anyContentLoaded()) {
            _state.value = HomeState.Content
        }
    }

    private fun anyContentLoaded(): Boolean =
        _trending.value.isNotEmpty() ||
            _popular.value.isNotEmpty() ||
            _topRated.value.isNotEmpty() ||
            _continueWatching.value.isNotEmpty() ||
            _nextUp.value.isNotEmpty() ||
            _releasingSoon.value.isNotEmpty() ||
            _recentlyReleased.value.isNotEmpty()

    /**
     * The four personal rails — each a reactive collection over the store that
     * OWNS its truth, enriched (title + cover) on Dispatchers.IO and capped at
     * [RAIL_CAP]. Every emission re-checks the Error state so offline homes
     * with personal data still render (partial success).
     */
    private fun observePersonalRails() {
        // Continue watching — the round-144 v4 query (in-progress, not
        // suppressed, `last_watched_at DESC`), deduped per mainId to the most
        // recent in-progress episode.
        viewModelScope.launch {
            watchProgressStore.observeContinueWatching(RAIL_CAP)
                .collect { rows ->
                    _continueWatching.value = withContext(Dispatchers.IO) { enrichContinue(rows) }
                    promoteOutOfError()
                }
        }

        // What to watch next — DERIVED (spec §1 #2): per recently-watched
        // mainId the next unwatched episode (highest-watched + 1), plus the
        // RELEASING next-airing card from anime_update_state.
        viewModelScope.launch {
            combine(
                watchProgressStore.observeContinueWatching(RAIL_CAP),
                watchProgressStore.observeRecent(RECENT_POOL),
            ) { continueRows, recentRows ->
                withContext(Dispatchers.IO) { deriveNextUp(continueRows, recentRows) }
            }.collect { items ->
                _nextUp.value = items
                promoteOutOfError()
            }
        }

        // Releasing soon — the schedule store's reactive feed (library
        // semi-join, scheduled_at ASC, next 7 days), weekday + time subtitle.
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            scheduleStore.observeUpcoming(from = now, to = now + SCHEDULE_WINDOW_MS)
                .collect { entries ->
                    _releasingSoon.value = withContext(Dispatchers.IO) { enrichSchedule(entries) }
                    promoteOutOfError()
                }
        }

        // Recently released — the detected-update feed (discovered_at DESC),
        // deduped per mainId to the latest row, covers enriched locally.
        viewModelScope.launch {
            updateStore.observeAllUpdates(limit = 100L)
                .collect { updates ->
                    _recentlyReleased.value = withContext(Dispatchers.IO) { enrichReleases(updates) }
                    promoteOutOfError()
                }
        }
    }

    /**
     * Force-refresh the three discovery sections from network, in PARALLEL
     * (pull-to-refresh + the error-state Retry). The personal rails are
     * reactive store flows — they don't need a refresh trigger.
     */
    fun refresh() {
        viewModelScope.launch { fetchSection(SECTION_TRENDING, "TRENDING_DESC") { _trending.value = it } }
        viewModelScope.launch { fetchSection(SECTION_POPULAR, "POPULARITY_DESC") { _popular.value = it } }
        viewModelScope.launch { fetchSection(SECTION_TOP_RATED, "SCORE_DESC") { _topRated.value = it } }
    }

    // ── Enrichment (all run inside withContext(Dispatchers.IO)) ─────────────

    /**
     * Continue-watching rows → poster cards. Rows arrive `last_watched_at
     * DESC`; the FIRST row per mainId is the most recent in-progress episode.
     * Rows without a mainId (legacy pre-WP rows) are skipped honestly.
     */
    private fun enrichContinue(rows: List<WatchProgress>): List<HomeContinueItem> {
        val items = mutableListOf<HomeContinueItem>()
        for (row in rows.filter { it.mainId != null }.distinctBy { it.mainId }) {
            if (items.size >= RAIL_CAP) break
            val mainId = row.mainId!!
            val content = contentRepository.getMainEntryByMainId(mainId) ?: continue
            val details = contentRepository.getContentDetails(mainId)
            items += HomeContinueItem(
                mainId = mainId,
                title = content.title,
                coverUrl = details?.dataCoverUrl ?: details?.extThumbnailUrl,
                episodeNumber = episodeNumberOf(row),
                progressFraction = row.progressFraction,
                totalEpisodes = details?.dataEpisodes?.toInt(),
            )
        }
        return items
    }

    /**
     * What to watch next — the deterministic round-148 derivation, kept
     * honest and simple:
     *
     * 1. Scan the RECENT watch pool (a superset of the continue rail) — one
     *    candidate per mainId (newest row wins).
     * 2. next = highestWatched + 1 via the store's batched
     *    [WatchProgressStore.getAllHighestWatchedNumbers] (one GROUP BY — no
     *    per-anime queries).
     * 3. Resume ownership: if the item's in-progress episode (what the
     *    Continue rail already surfaces) IS the next episode, skip it here —
     *    the Continue rail owns the resume case, this rail owns "start the
     *    next one".
     * 4. Airing next: `anime_update_state.status == RELEASING` with a
     *    `next_airing_episode` AHEAD of what's watched → the target becomes
     *    that episode with its date as the subtitle.
     * 5. Existence guard: when the data axis knows the episode total
     *    (`content_details.data_episodes`), never point past it. No fabricated
     *    numbers — when the total is unknown the next-episode rule stands.
     */
    private fun deriveNextUp(
        continueRows: List<WatchProgress>,
        recentRows: List<WatchProgress>,
    ): List<HomeNextUpItem> {
        val recentByMain = recentRows.filter { it.mainId != null }.distinctBy { it.mainId }
        if (recentByMain.isEmpty()) return emptyList()
        val continueEpByMain = continueRows
            .filter { it.mainId != null }
            .distinctBy { it.mainId }
            .associate { it.mainId!! to episodeNumberOf(it) }
        val highestMap = runCatching { watchProgressStore.getAllHighestWatchedNumbers() }
            .getOrElse { e ->
                Logger.w(TAG) { "highest-watched batch failed: ${e.message}" }
                emptyMap()
            }

        val items = mutableListOf<HomeNextUpItem>()
        for (row in recentByMain) {
            if (items.size >= RAIL_CAP) break
            val mainId = row.mainId!!
            val highest = highestMap[mainId] ?: 0
            val next = highest + 1
            if (next <= 0) continue

            // The Continue rail already owns this resume — don't duplicate it.
            val continueEp = continueEpByMain[mainId]
            if (continueEp != null && continueEp >= next) continue

            val content = contentRepository.getMainEntryByMainId(mainId) ?: continue
            val details = contentRepository.getContentDetails(mainId)
            val total = details?.dataEpisodes
            val cover = details?.dataCoverUrl ?: details?.extThumbnailUrl

            val state = updateStore.getAnimeUpdateState(mainId)
            val airing = state?.takeIf {
                it.status == "RELEASING" && it.nextAiringEpisode != null
            }
            val airingEp = airing?.nextAiringEpisode?.toInt()
            if (airingEp != null && airingEp > highest) {
                // Point past the total? Never.
                if (total != null && airingEp > total) continue
                items += HomeNextUpItem(
                    mainId = mainId,
                    title = content.title,
                    coverUrl = cover,
                    episodeNumber = airingEp,
                    subtitle = airing!!.nextAiringAt?.let { formatAiringDate(it) } ?: "Airing soon",
                    isAiringNext = true,
                )
            } else {
                if (total != null && next > total) continue
                items += HomeNextUpItem(
                    mainId = mainId,
                    title = content.title,
                    coverUrl = cover,
                    episodeNumber = next,
                    subtitle = "Up next",
                    isAiringNext = false,
                )
            }
        }
        return items
    }

    /**
     * Schedule rows (already ordered `scheduled_at ASC`, library semi-join,
     * deduped defensively the same way ScheduleViewModel dedupes) → poster
     * cards with the weekday + time subtitle (spec §1 #3).
     */
    private fun enrichSchedule(entries: List<ScheduleEntry>): List<HomeScheduleItem> {
        return entries
            .distinctBy { Triple(it.mainId, it.episodeNumber, it.scheduledAt) }
            .take(RAIL_CAP)
            .mapNotNull { entry ->
                val content = contentRepository.getMainEntryByMainId(entry.mainId) ?: return@mapNotNull null
                val details = contentRepository.getContentDetails(entry.mainId)
                HomeScheduleItem(
                    mainId = entry.mainId,
                    title = content.title,
                    coverUrl = details?.dataCoverUrl ?: details?.extThumbnailUrl,
                    episodeNumber = entry.episodeNumber.toInt(),
                    scheduledAt = entry.scheduledAt,
                    subtitle = formatWeekdayTime(entry.scheduledAt),
                )
            }
    }

    /**
     * The detected-update feed (already `discovered_at DESC`) → "just dropped"
     * cards, deduped per mainId keeping the NEWEST row (the first occurrence).
     */
    private fun enrichReleases(updates: List<EpisodeUpdate>): List<HomeReleaseItem> {
        val items = mutableListOf<HomeReleaseItem>()
        for (update in updates.distinctBy { it.mainId }) {
            if (items.size >= RAIL_CAP) break
            val content = contentRepository.getMainEntryByMainId(update.mainId) ?: continue
            val details = contentRepository.getContentDetails(update.mainId)
            items += HomeReleaseItem(
                mainId = update.mainId,
                title = content.title,
                coverUrl = details?.dataCoverUrl ?: details?.extThumbnailUrl,
                episodeNumber = update.episodeNumber.toInt(),
                discoveredAt = update.discoveredAt,
            )
        }
        return items
    }

    // ── Small helpers ────────────────────────────────────────────────────────

    /**
     * The episode number carried by a progress row's standardized key
     * (`"mainId|%05d"` — [com.confused.anikuta.core.watchprogress.EpisodeKeys]).
     */
    private fun episodeNumberOf(row: WatchProgress): Int =
        row.episodeKey.substringAfterLast('|').toIntOrNull() ?: 0

    /** "Fri, Nov 21" — the airing-next subtitle. */
    private fun formatAiringDate(at: Long): String =
        Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("EEE, MMM d"))

    /** "Fri 18:30" — the releasing-soon subtitle (spec: the weekday + time). */
    private fun formatWeekdayTime(at: Long): String =
        Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("EEE, HH:mm"))
}

/** Home's overall state (the BrowseState shape: Loading → Content | Error). */
sealed interface HomeState {
    data object Loading : HomeState
    data object Content : HomeState
    data class Error(val message: String) : HomeState
}

// ── Rail item models ─────────────────────────────────────────────────────────

/** One Continue-watching card (poster + progress fraction + "EP N"). */
data class HomeContinueItem(
    val mainId: String,
    val title: String,
    val coverUrl: String?,
    val episodeNumber: Int,
    val progressFraction: Float,
    val totalEpisodes: Int?,
)

/** One "What to watch next" card (the target episode, optionally airing-dated). */
data class HomeNextUpItem(
    val mainId: String,
    val title: String,
    val coverUrl: String?,
    val episodeNumber: Int,
    val subtitle: String?,
    val isAiringNext: Boolean,
)

/** One Releasing-soon card (weekday + time subtitle). */
data class HomeScheduleItem(
    val mainId: String,
    val title: String,
    val coverUrl: String?,
    val episodeNumber: Int,
    val scheduledAt: Long,
    val subtitle: String?,
)

/** One Recently-released card ("just dropped"). */
data class HomeReleaseItem(
    val mainId: String,
    val title: String,
    val coverUrl: String?,
    val episodeNumber: Int,
    val discoveredAt: Long,
)
