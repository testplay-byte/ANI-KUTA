package com.confused.anikuta.feature.cswatch.impl

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.component.animateScrollToItemCentered
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.EpisodeListPreferences
import com.confused.anikuta.feature.cswatch.api.CsSimpleEpisode
import com.confused.anikuta.feature.cswatch.api.CsSubDubSiblings
import com.confused.anikuta.feature.cswatch.api.CsWatchEpisodeMeta
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Task 54 (round 14) — the CloudStream watch PAGE (minimized mode).
 *
 * The layout mirrors the aniyomi watch page's MinimizedMode EXACTLY:
 * ```
 * ┌─────────────────────────────────┐
 * │  ┌───────────────────────────┐  │  ← Floating pill top bar
 * │  │ ◁ Back  ANI-KUTA          │  │     (collapses on scroll)
 * │  └───────────────────────────┘  │
 * │  ┌───────────────────────────┐  │  ← Player 16:9 (rounded corners)
 * │  │      PlayerView           │  │     + CsMinimizedControls
 * │  │   [resolving/error state] │  │     + phase overlays INSIDE the box
 * │  └───────────────────────────┘  │
 * │  Currently playing episode N   │  ← description section (scrollable)
 * │  Episode title                  │
 * │  [provider] [quality] pills     │
 * │  Synopsis… (show more)          │
 * │  Episodes (12)                  │  ← episode list (lazy rows)
 * │  ┌──┐ EP 1  Title              │
 * │  └──┘                           │
 * └─────────────────────────────────┘
 * ```
 * RESOLVING / FAILED / NO_LINKS render INSIDE the 16:9 player box — the page
 * content below stays visible (the "watch page shows properly" requirement:
 * description + episodes are always reachable even mid-resolution).
 */
@Composable
internal fun CsWatchPage(
    uiState: CsWatchViewModel.CsWatchUiState,
    playerContent: @Composable () -> Unit,
    onBack: () -> Unit,
    onEpisodeSwitch: (CsSimpleEpisode) -> Unit,
    currentEpisodeData: String,
    ratingStore: com.confused.anikuta.core.ratings.RatingStore = koinInject(),
    mainId: String,
    // ROUND 108 (D-713): the details page's COVER URL — the episode rows'
    // thumbnail fallback (the details page's own rule; blank = no fallback).
    coverUrl: String = "",
    /**
     * Task 57 (round 17 — P1): the current content's watch-progress rows,
     * keyed by [CsWatchViewModel.episodeKey] — the episode rows below render
     * watched dimming + a thin progress bar from this. Sub and dub rows of
     * the SAME episode resolve to the SAME entry (the ordinal identity),
     * so watching sub-EP-5 shows on the dub-EP-5 row too. Default empty —
     * the caller (CsWatchScreen) wires the VM's episodeProgress map.
     */
    progressByEpisodeKey: Map<String, com.confused.anikuta.core.watchprogress.WatchProgress> = emptyMap(),
    /**
     * ROUND 109 (D-721): the FULL watch key — the download badge opens the
     * CS resolve sheet in DOWNLOAD mode now (the details page's flow: no
     * auto-pick; the user picks the stream from the bottom-up resolved
     * list). The sheet resolves per-episode key COPIES of this key.
     */
    watchKey: com.confused.anikuta.feature.cswatch.api.CsWatchKey = com.confused.anikuta.feature.cswatch.api.CsWatchKey(
        providerName = "",
        animeTitle = "",
        episodeData = "",
        episodeNumber = 0f,
        episodeTitle = "",
    ),
) {
    val listState = rememberLazyListState()

    // Task 55: the sub/dub display modes for the page's episode list (the
    // SAME pref as the details page + the episodes sheet — one setting).
    // COMBINED merges sibling rows; SEPARATE adds the Sub | Dub chip row.
    //
    // Task 56 (round 16 — device feedback F3): the RENDERED rows carry
    // per-flavor ordinals + tag-stripped names. The raw episode numbers stay
    // the rows' identity (watch-progress / cache / metadata keys) — the
    // second flavor's numbers globally CONTINUE (the details pipeline
    // normalizes to unique 1..N), so the display layer renumbers per flavor:
    // the Dub list starts at "Episode 1" and the names never repeat the
    // flavor the switcher already shows.
    val episodeListPreferences = koinInject<EpisodeListPreferences>()
    val subDubMode = remember { episodeListPreferences.subDubMode.get() }
    val displayEpisodes = remember(uiState.episodes, subDubMode) {
        if (subDubMode == "COMBINED") CsSubDubSiblings.mergeSiblings(uiState.episodes) else uiState.episodes
    }
    val flavorOrdinals = remember(uiState.episodes) {
        CsSubDubSiblings.flavorOrdinals(uiState.episodes)
    }
    val showSubDubSwitcher = subDubMode != "COMBINED" && CsSubDubSiblings.hasBothFlavors(displayEpisodes)
    var subDubFlavor by rememberSaveable { mutableStateOf("SUB") }
    val episodeRows = if (showSubDubSwitcher) {
        displayEpisodes.filter { CsSubDubSiblings.tagOf(it.name) == subDubFlavor }
    } else displayEpisodes
    // Task 56: render copies — tag stripped from every name (the switcher /
    // pills / stream chips carry the flavor; stripTag is a no-op untagged).
    val renderRowsBase = remember(episodeRows) {
        episodeRows.map { it.copy(name = CsSubDubSiblings.stripTag(it.name)) }
    }

    // ── ROUND 102 (WS-F): the PLAYER episode-list customization's new home ──
    // The in-player gear + search are RETIRED (the user's round-102 order —
    // same as the MPV stack's twin): the customization lives in the dedicated
    // Settings page now (Appearance → "Player page"), the SAME shared
    // PlayerEpisodeListPreferences driving both player pages.
    val playerListPrefs = koinInject<com.confused.anikuta.core.preferences.PlayerEpisodeListPreferences>()
    // ROUND 103 (WS-4): the raw key resolves through the lenient lookup; the
    // display bundle (the four-paradigm knobs) is built from the LIVE values
    // — the settings preview and this list re-shape together (the D-481 rule).
    val csRowStyleKey by playerListPrefs.rowStyle.changes.collectAsState(initial = playerListPrefs.rowStyle.get())
    val csListStyle = remember(csRowStyleKey) {
        com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeListStyle.fromKey(csRowStyleKey)
    }
    val csShowSynopsis by playerListPrefs.showSynopsis.changes.collectAsState(initial = playerListPrefs.showSynopsis.get())
    val csShowDatePill by playerListPrefs.showDatePill.changes.collectAsState(initial = playerListPrefs.showDatePill.get())
    val csDimWatched by playerListPrefs.dimWatched.changes.collectAsState(initial = playerListPrefs.dimWatched.get())
    // ROUND 104 (WS-D): the BANNER's controls — the ghost-number toggle +
    // the density knob (both live in the shared display bundle now).
    // ROUND 105 (WS-D): the density knob re-aimed at the item SIZE + the
    // number position/style + the progress bar + the GRID's three knobs.
    val csShowEpisodeNumber by playerListPrefs.showEpisodeNumber.changes.collectAsState(
        initial = playerListPrefs.showEpisodeNumber.get(),
    )
    val csShowProgressBar by playerListPrefs.showProgressBar.changes.collectAsState(
        initial = playerListPrefs.showProgressBar.get(),
    )
    val csBannerSize by playerListPrefs.bannerSize.changes.collectAsState(
        initial = playerListPrefs.bannerSize.get(),
    )
    val csBannerNumberPositionKey by playerListPrefs.bannerNumberPosition.changes.collectAsState(
        initial = playerListPrefs.bannerNumberPosition.get(),
    )
    val csBannerNumberPosition = remember(csBannerNumberPositionKey) {
        com.confused.anikuta.core.designsystem.component.playerlist.PlayerBannerNumberPosition
            .fromKey(csBannerNumberPositionKey)
    }
    val csBannerNumberStyleKey by playerListPrefs.bannerNumberStyle.changes.collectAsState(
        initial = playerListPrefs.bannerNumberStyle.get(),
    )
    val csBannerNumberStyle = remember(csBannerNumberStyleKey) {
        com.confused.anikuta.core.designsystem.component.playerlist.PlayerBannerNumberStyle
            .fromKey(csBannerNumberStyleKey)
    }
    val csGridWatchedCheckmark by playerListPrefs.gridWatchedCheckmark.changes.collectAsState(
        initial = playerListPrefs.gridWatchedCheckmark.get(),
    )
    val csGridCurrentStyleKey by playerListPrefs.gridCurrentStyle.changes.collectAsState(
        initial = playerListPrefs.gridCurrentStyle.get(),
    )
    val csGridCurrentStyle = remember(csGridCurrentStyleKey) {
        com.confused.anikuta.core.designsystem.component.playerlist.PlayerGridCurrentStyle
            .fromKey(csGridCurrentStyleKey)
    }
    // ROUND 108 (D-713): the GRID's title-line MODE (the round-105 Boolean
    // retired for the richer knob) + the BANNER's watched check mark.
    val csGridTitleModeKey by playerListPrefs.gridTitleMode.changes.collectAsState(
        initial = playerListPrefs.gridTitleMode.get(),
    )
    val csGridTitleMode = remember(csGridTitleModeKey) {
        com.confused.anikuta.core.common.GridTitleMode.fromKey(csGridTitleModeKey)
    }
    val csBannerWatchedCheck by playerListPrefs.bannerWatchedCheck.changes.collectAsState(
        initial = playerListPrefs.bannerWatchedCheck.get(),
    )
    // ROUND 106 (WS-D): the new knobs — the audio pills, the download
    // button, and the BANNER's currently-playing treatment.
    val csShowAudioPills by playerListPrefs.showAudioPills.changes.collectAsState(
        initial = playerListPrefs.showAudioPills.get(),
    )
    val csShowDownloadButton by playerListPrefs.showDownloadButton.changes.collectAsState(
        initial = playerListPrefs.showDownloadButton.get(),
    )
    val csBannerCurrentStyleKey by playerListPrefs.bannerCurrentStyle.changes.collectAsState(
        initial = playerListPrefs.bannerCurrentStyle.get(),
    )
    val csBannerCurrentStyle = remember(csBannerCurrentStyleKey) {
        com.confused.anikuta.core.designsystem.component.playerlist.PlayerBannerCurrentStyle
            .fromKey(csBannerCurrentStyleKey)
    }
    // ROUND 105 (WS-D): the TRACKLIST's column-sizing reference — the LIST's
    // widest DISPLAY number (the per-flavor ordinal where present, the raw
    // number otherwise), pre-formatted the same way toRowData formats it.
    val csTracklistReferenceNumber = remember(uiState.episodes, flavorOrdinals) {
        val maxDisplay = uiState.episodes.maxOfOrNull { ep ->
            flavorOrdinals[ep.data]?.toFloat() ?: ep.episodeNumber
        } ?: 0f
        com.confused.anikuta.core.common.EpisodeTitleParser.formatEpisodeNumber(maxDisplay)
    }
    val csListDisplay = remember(
        csListStyle, csShowSynopsis, csShowDatePill, csShowAudioPills,
        csDimWatched, csShowEpisodeNumber, csShowProgressBar,
        csShowDownloadButton, csBannerSize, csBannerNumberPosition,
        csBannerNumberStyle, csBannerCurrentStyle, csGridWatchedCheckmark,
        csGridCurrentStyle, csGridTitleMode, csTracklistReferenceNumber,
        csBannerWatchedCheck,
    ) {
        com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeListDisplay(
            style = csListStyle,
            showSynopsis = csShowSynopsis,
            showDatePill = csShowDatePill,
            showAudioPills = csShowAudioPills,
            dimWatched = csDimWatched,
            showEpisodeNumber = csShowEpisodeNumber,
            showProgressBar = csShowProgressBar,
            showDownloadButton = csShowDownloadButton,
            bannerSize = csBannerSize,
            bannerNumberPosition = csBannerNumberPosition,
            bannerNumberStyle = csBannerNumberStyle,
            bannerCurrentStyle = csBannerCurrentStyle,
            gridWatchedCheckmark = csGridWatchedCheckmark,
            gridCurrentStyle = csGridCurrentStyle,
            gridTitleMode = csGridTitleMode,
            tracklistReferenceNumber = csTracklistReferenceNumber,
            bannerWatchedCheck = csBannerWatchedCheck,
        )
    }
    // ROUND 104 (WS-D): the watched FILTER is RETIRED (the v1.1.60 order —
    // "it should be completely removed"); the DIM treatment stays.
    // ROUND 103 (WS-4): DIRECTION ONLY — the round-101 sort modes are retired
    // per the device round's order.
    val csSortDescending by playerListPrefs.sortDescending.changes.collectAsState(initial = playerListPrefs.sortDescending.get())

    val renderRows = remember(
        renderRowsBase, csSortDescending,
    ) {
        // ROUND 104 (WS-D): the sort is the episode number + direction ONLY
        // (the watched-filter stage is retired with the pref).
        val rows = renderRowsBase.sortedBy { it.episodeNumber }
        if (csSortDescending) rows.asReversed() else rows
    }

    // ROUND 102 (WS-F): the current episode's position in the DISPLAY list —
    // -1 means it is outside the display list (the sub-dub switcher can
    // exclude it), which hides the header's "Scroll to Current" action.
    // The lazy-index offset accounts for the items above the rows (the
    // Episodes header + the sub/dub switcher when it shows).
    val csCurrentEpisodeIndex = remember(renderRows, currentEpisodeData) {
        renderRows.indexOfFirst { it.data == currentEpisodeData }
    }
    // ROUND 103 (WS-4): under GRID the lazy items are PAIRS — the current
    // episode's lazy index is its PAIR's index (base offset computed at the
    // scroll site where showSubDubSwitcher is in scope).
    val csCurrentPairOffset = if (csListStyle == com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeListStyle.GRID) {
        csCurrentEpisodeIndex / 2
    } else {
        csCurrentEpisodeIndex
    }

    // ── ROUND 104 (WS-C/WS-D): THE UNSTOPPABLE SCROLL's owner-level state —
    // the MPV stack's twin. ROOT-CAUSE FIX: the round-103 scope lived INSIDE
    // the header's lazy item (its disposal mid-glide cancelled the scroll —
    // the "scrolls slightly, then stops" report); the scope now lives at the
    // page level. The ARRIVAL PULSE token bumps only after a COMPLETED glide
    // (a user touch cancels the coroutine BEFORE the bump). The WATCHED
    // STORE feeds the swipe/long-press toggles (the same store the progress
    // map above observes — the map re-emits live on every toggle). ──
    val csPageScrollScope = rememberCoroutineScope()
    var csScrollArrivalPulse by remember { mutableLongStateOf(0L) }
    val csWatchProgressStore = koinInject<com.confused.anikuta.core.watchprogress.WatchProgressStore>()

    // ── ROUND 106 (WS-D): THE PLAYER PAGE'S DOWNLOADS (the CS stack) —
    // the badge's data + actions. The states ride the engine's map (keyed
    // "$mainId|$dataHandle" — the SAME key the details page's CS downloads
    // write).
    //
    // ROUND 109 (D-721): THE AUTO-PICK IS DEAD. The v1.1.65 device round:
    // "it directly, automatically started to load, and after loading, it
    // apparently did not show me any bottom-up resolved video lists for it
    // or anything like that, how it gets handled on the details page… it
    // automatically selects one of the video streams and starts downloading
    // it automatically, which is not a good idea." The badge's download tap
    // now opens the SAME CsResolveSheet the details page opens — in DOWNLOAD
    // mode ("Download EP N", the progressive resolved list, the server /
    // audio / resolution chips) — and only the USER'S pick enqueues, through
    // the same CsDownloadRequestBuilder the details page's pick uses. The
    // sheet resolves internally (its own event stream), so the tap itself is
    // synchronous — no InFlight gap, no inline resolver. ──
    val csDownloadManager = koinInject<com.confused.anikuta.core.download.DownloadManager>()
    val csPlayerDownloadController = koinInject<com.confused.anikuta.core.download.PlayerDownloadController>()
    val csCoreDownloadStates by csDownloadManager.episodeDownloadStates.collectAsState()
    val csDownloadQueueSnapshot by csDownloadManager.getQueue().collectAsState()
    // The download-folder gate (the details page's D-403 pre-check,
    // player-side) + the per-episode sheet state.
    val csDownloadPreferences = koinInject<com.confused.anikuta.core.download.DownloadPreferences>()
    val csDownloadFolderContext = androidx.compose.ui.platform.LocalContext.current
    fun csDownloadFolderReady(): Boolean = runCatching {
        val uriStr = csDownloadPreferences.downloadFolderUri.get()
        if (uriStr.isBlank()) return@runCatching false
        val uri = android.net.Uri.parse(uriStr)
        csDownloadFolderContext.contentResolver.persistedUriPermissions
            .any { it.uri == uri && it.isWritePermission }
    }.getOrDefault(false)
    var csDownloadSheetKey by remember {
        mutableStateOf<com.confused.anikuta.feature.cswatch.api.CsWatchKey?>(null)
    }
    fun csDownloadStateFor(ep: CsSimpleEpisode): com.confused.anikuta.core.designsystem.component.playerlist.PlayerDownloadRenderState? {
        if (mainId.isBlank()) return null
        val key = "$mainId|${ep.data}"
        val raw = csCoreDownloadStates[key]
            ?: return com.confused.anikuta.core.designsystem.component.playerlist.PlayerDownloadRenderState.NotDownloaded
        return when (raw.first) {
            com.confused.anikuta.core.download.DownloadStatus.QUEUED,
            com.confused.anikuta.core.download.DownloadStatus.RETRYING,
                -> com.confused.anikuta.core.designsystem.component.playerlist.PlayerDownloadRenderState.InFlight
            com.confused.anikuta.core.download.DownloadStatus.DOWNLOADING ->
                com.confused.anikuta.core.designsystem.component.playerlist.PlayerDownloadRenderState.Downloading(raw.second)
            com.confused.anikuta.core.download.DownloadStatus.PAUSED ->
                com.confused.anikuta.core.designsystem.component.playerlist.PlayerDownloadRenderState.Paused
            com.confused.anikuta.core.download.DownloadStatus.ERROR ->
                com.confused.anikuta.core.designsystem.component.playerlist.PlayerDownloadRenderState.Error
            com.confused.anikuta.core.download.DownloadStatus.COMPLETED ->
                com.confused.anikuta.core.designsystem.component.playerlist.PlayerDownloadRenderState.Downloaded
            com.confused.anikuta.core.download.DownloadStatus.CANCELLED ->
                com.confused.anikuta.core.designsystem.component.playerlist.PlayerDownloadRenderState.NotDownloaded
        }
    }
    fun csDownloadTaskIdFor(ep: CsSimpleEpisode): Long? {
        if (mainId.isBlank()) return null
        return csDownloadQueueSnapshot.firstOrNull {
            it.content.mainId == mainId && it.episode.episodeKey == ep.data
        }?.id
    }
    fun csDownloadActionsFor(ep: CsSimpleEpisode): com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeDownloadActions {
        return com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeDownloadActions(
            // ROUND 109 (D-721): the details page's flow — the folder gate,
            // then the RESOLVE SHEET in download mode. The per-episode key
            // copy carries the tapped episode's data handle (the sheet's
            // resolution + the combined-mode handles read it); the sheet's
            // pick hands the user-chosen link + subtitles + the full list to
            // the enqueue below.
            onDownload = {
                if (mainId.isBlank()) return@PlayerEpisodeDownloadActions
                if (!csDownloadFolderReady()) {
                    com.confused.anikuta.core.designsystem.component.toast.AppToast.show(
                        "No download folder — pick one in Settings → Downloads first",
                        com.confused.anikuta.core.designsystem.component.toast.AppToastTone.ERROR,
                    )
                    return@PlayerEpisodeDownloadActions
                }
                csDownloadSheetKey = watchKey.copy(
                    episodeData = ep.data,
                    episodeNumber = ep.episodeNumber,
                    episodeTitle = ep.name,
                )
            },
            onPause = {
                csDownloadTaskIdFor(ep)?.let { id ->
                    csPageScrollScope.launch { csDownloadManager.pauseDownload(id) }
                }
            },
            onResume = {
                csDownloadTaskIdFor(ep)?.let { id ->
                    csPageScrollScope.launch { csDownloadManager.resumeDownload(id) }
                }
            },
            onCancel = {
                csDownloadTaskIdFor(ep)?.let { id ->
                    csPageScrollScope.launch { csDownloadManager.cancelDownload(id) }
                }
            },
            onRetry = {
                csDownloadTaskIdFor(ep)?.let { id ->
                    csPageScrollScope.launch { csDownloadManager.retryDownload(id) }
                }
            },
            onPlayDownloaded = {
                // The CS player's own offline routing (the DASH/file
                // handoff) lives in the episode switch — same as the MPV
                // stack's contract.
                if (ep.data != currentEpisodeData) {
                    onEpisodeSwitch(ep)
                }
            },
            // ROUND 108 (D-713): the badge's Downloaded menu offers
            // Play / Delete — the delete lands here (the DownloadManager's
            // own per-episode call; the CS episode key is the data handle).
            onDelete = {
                if (mainId.isNotBlank()) {
                    csPageScrollScope.launch {
                        csDownloadManager.deleteDownloadedEpisode(mainId, ep.data)
                    }
                }
            },
        )
    }

    // Task 57 (P1): the CURRENT episode's rating/progress identity — the
    // flavor ORDINAL for tagged lists (sub-5 ↔ dub-5 share ONE key: one
    // rating, one progress row — the "linked together" contract), else the
    // raw number. .toFloat() — Int? ?: Float infers Number, which a Float
    // param rejects (Kotlin never widens numerics).
    val identityNumber = flavorOrdinals[currentEpisodeData]?.toFloat()
        ?: uiState.episodeNumber
    val ratingEpisodeKey = if (mainId.isNotBlank()) {
        CsWatchViewModel.episodeKey(mainId, identityNumber)
    } else null

    // Per-episode rating (the aniyomi page's Phase-4 star bar — same store,
    // same keys, CS content rides it identically).
    val ratingScope = rememberCoroutineScope()
    var episodeRating by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(mainId, uiState.episodeNumber) {
        if (ratingEpisodeKey != null) {
            episodeRating = runCatching { ratingStore.getEpisodeRating(mainId, ratingEpisodeKey) }.getOrNull()
        }
    }

    // The pill bar collapses as soon as the content scrolls.
    val collapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > 200
        }
    }

    // The player sits FLUSH BELOW the status bar (never behind it).
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // Header height: 48dp when expanded, 0dp when collapsed (animated).
    val headerHeight by animateDpAsState(
        targetValue = if (collapsed) 0.dp else 48.dp,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "headerHeight",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // ── Top bar area — collapses on scroll; the player slides up flush
        //    below the status bar (the aniyomi pattern, replicated). ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight + statusBarInset)
                .clipToBounds(),
        ) {
            if (headerHeight > 0.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .graphicsLayer {
                            alpha = if (headerHeight == 0.dp) 0f else 1f
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(20.dp),
                        tonalElevation = 2.dp,
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.4f),
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable(onClick = onBack),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            Text(
                                text = "ANI-KUTA",
                                fontFamily = RobotoFamily,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            Spacer(Modifier.size(40.dp))
                        }
                    }
                }
            }
        }

        // ── Player 16:9 (rounded corners) — the phase overlays render inside ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black),
            ) {
                playerContent()
            }
        }

        // ── Scrollable content: description + episode list ──
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "current") {
                    CsCurrentlyPlayingSection(
                        uiState = uiState,
                        // Task 56: the current episode's per-flavor ordinal
                        // (falls back to the raw number for untagged rows).
                        // NOTE: .toFloat() — Int? ?: Float infers Number, which
                        // a Float param rejects (Kotlin never widens numerics).
                        currentDisplayNumber = flavorOrdinals[currentEpisodeData]?.toFloat()
                            ?: uiState.episodeNumber,
                        episodeRating = episodeRating,
                        onRate = { stars ->
                            // Same blank-mainId guard as the read above — CS-only
                            // content with no mainId must not write ratings under
                            // the shared "" namespace.
                            if (ratingEpisodeKey != null) {
                                val epKey = ratingEpisodeKey
                                ratingScope.launch {
                                    if (stars <= 0) {
                                        ratingStore.deleteEpisodeRating(mainId, epKey)
                                    } else {
                                        ratingStore.setEpisodeRating(mainId, epKey, stars * 10)
                                    }
                                    episodeRating = if (stars <= 0) null else stars * 10
                                }
                            }
                        },
                    )
                }

                if (episodeRows.isNotEmpty()) {
                    item(key = "episodes-header") {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Episodes",
                                    fontFamily = RobotoFamily,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(50),
                                ) {
                                    Text(
                                        text = "${renderRows.size}",
                                        fontFamily = RobotoFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                // ROUND 102 (WS-F): "Scroll to Current" — the
                                // MPV stack's twin (the round-101 magnifier +
                                // gear are retired per the user's order). A
                                // TEXT action in the primary color; hidden
                                // while the current episode is outside the
                                // display list.
                                if (csCurrentEpisodeIndex >= 0) {
                                    Text(
                                        text = "Scroll to Current",
                                        fontFamily = RobotoFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                // ROUND 104 (WS-C): the PAGE-LEVEL
                                                // scope (the round-103 scope lived
                                                // INSIDE this lazy item — its
                                                // disposal mid-glide cancelled the
                                                // scroll) + the ARRIVAL PULSE after
                                                // a COMPLETED glide (a user touch
                                                // cancels before the bump).
                                                csPageScrollScope.launch {
                                                    listState.animateScrollToItemCentered(
                                                        // ROUND 103 (WS-4): under GRID the items are
                                                        // PAIRS — csCurrentPairOffset already carries
                                                        // the pair math. SA2-F3 fix: EVERY item above
                                                        // the rows — the "Currently playing" card (0)
                                                        // + the Episodes header (1) + the sub/dub
                                                        // switcher when it shows (0/1).
                                                        csCurrentPairOffset +
                                                            2 + if (showSubDubSwitcher) 1 else 0,
                                                    )
                                                    csScrollArrivalPulse += 1L
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                    // ROUND 104 (WS-D): the filtered-empty state is RETIRED
                    // with the watched filter (a non-empty row list can no
                    // longer filter down to nothing).
                    // Task 55: the Sub/Dub switcher chips (SEPARATE mode, both
                    // flavors) — the SeasonSelectorRow chip language.
                    if (showSubDubSwitcher) {
                        item(key = "subdub-switcher") {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                            ) {
                                listOf("SUB" to "Sub", "DUB" to "Dub").forEach { (value, label) ->
                                    val isSelected = subDubFlavor == value
                                    Surface(
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(50),
                                        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                        modifier = Modifier.clickable { subDubFlavor = value },
                                    ) {
                                        Text(
                                            text = label,
                                            fontFamily = RobotoFamily,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // Lazy episode rows — virtualized (D-230 pattern).
                    // Task 56: renderRows — tag-stripped names + the row's EP
                    // tag shows the per-flavor ORDINAL (the raw number is the
                    // identity: progress/cache/metadata keys + the metadata
                    // lookup below still use the raw number).
                    // Task 57 (P1): the ordinal-aware number is also the row's
                    // PROGRESS identity — sub-5 / dub-5 / their COMBINED merge
                    // read ONE progress row; (P2) merged rows carry their
                    // flavor tags as render-only pills.
                    // ROUND 103 (WS-4): the rows render through the ONE shared
                    // four-paradigm dispatcher (the same renderer the settings
                    // preview draws); under GRID the items are two-across PAIRS.
                    fun toRowData(ep: CsSimpleEpisode): com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeRowData {
                        // .toFloat(): the elvis of Int? and Float infers Number —
                        // the displayNumber param is Float (no numeric widening).
                        val displayNumber = flavorOrdinals[ep.data]?.toFloat()
                            ?: ep.episodeNumber
                        val rowKey = if (mainId.isNotBlank()) {
                            CsWatchViewModel.episodeKey(mainId, displayNumber)
                        } else null
                        val rowProgress = rowKey?.let { progressByEpisodeKey[it] }
                        val meta = uiState.episodeMetadata[ep.episodeNumber.toInt()]
                        return com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeRowData(
                            episodeNumberText = com.confused.anikuta.core.common.EpisodeTitleParser
                                .formatEpisodeNumber(displayNumber),
                            displayTitle = meta?.title
                                ?: com.confused.anikuta.core.common.EpisodeTitleParser
                                    .getDisplayTitle(ep.name, displayNumber),
                            // ROUND 108 (D-713): the COVER FALLBACK — the
                            // details page's own thumbnail rule, ported.
                            thumbnailUrl = meta?.thumbnailUrl
                                ?: coverUrl.takeIf { it.isNotBlank() },
                            dateText = if (meta != null && meta.airDateMillis > 0) {
                                formatDate(meta.airDateMillis)
                            } else null,
                            // ROUND 109 (D-719): the SHORT date — the grid
                            // chip's text (the details grid's own shape).
                            dateTextShort = if (meta != null && meta.airDateMillis > 0) {
                                com.confused.anikuta.core.designsystem.component.episodelist
                                    .formatShortDate(meta.airDateMillis)
                            } else null,
                            subDubLabel = meta?.scanlator?.takeIf { it.isNotBlank() },
                            flavorLabels = ep.flavors,
                            synopsis = meta?.description,
                            isCurrent = ep.data == currentEpisodeData,
                            isWatched = rowProgress?.isWatched ?: false,
                            progressFraction = rowProgress?.progressFraction ?: 0f,
                            // ROUND 106 (WS-D): the badge's state.
                            downloadState = if (csShowDownloadButton) csDownloadStateFor(ep) else null,
                        )
                    }
                    if (csListStyle == com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeListStyle.GRID) {
                        val pairs = renderRows.chunked(2)
                        items(
                            count = pairs.size,
                            key = { i -> pairs[i].joinToString("|") { it.data } },
                        ) { pairIndex ->
                            val pair = pairs[pairIndex]
                            com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeGridRow(
                                left = toRowData(pair[0]),
                                right = pair.getOrNull(1)?.let { toRowData(it) },
                                display = csListDisplay,
                                // ROUND 106 (WS-D): the per-cell actions.
                                downloadActions = { data ->
                                    val ep = pair.firstOrNull {
                                        com.confused.anikuta.core.common.EpisodeTitleParser
                                            .formatEpisodeNumber(
                                                flavorOrdinals[it.data]?.toFloat() ?: it.episodeNumber,
                                            ) == data.episodeNumberText
                                    } ?: pair[0]
                                    if (csShowDownloadButton) csDownloadActionsFor(ep) else null
                                },
                                onClick = { data ->
                                    // The tapped cell's episode, matched inside
                                    // its own pair by the bundle's number text.
                                    val ep = pair.firstOrNull {
                                        val dn = flavorOrdinals[it.data]?.toFloat() ?: it.episodeNumber
                                        com.confused.anikuta.core.common.EpisodeTitleParser
                                            .formatEpisodeNumber(dn) == data.episodeNumberText
                                    } ?: pair[0]
                                    if (ep.data != currentEpisodeData) {
                                        onEpisodeSwitch(ep)
                                    }
                                },
                                // ROUND 104 (WS-D): the GRID's long-press watched
                                // toggle — the ordinal identity (the same math
                                // toRowData uses), guarded on a blank mainId.
                                onToggleWatched = { data ->
                                    val ep = pair.firstOrNull {
                                        val dn = flavorOrdinals[it.data]?.toFloat() ?: it.episodeNumber
                                        com.confused.anikuta.core.common.EpisodeTitleParser
                                            .formatEpisodeNumber(dn) == data.episodeNumberText
                                    } ?: pair[0]
                                    if (mainId.isNotBlank()) {
                                        val dn = flavorOrdinals[ep.data]?.toFloat() ?: ep.episodeNumber
                                        csPageScrollScope.launch {
                                            csWatchProgressStore.toggleWatched(
                                                CsWatchViewModel.episodeKey(mainId, dn),
                                            )
                                        }
                                    }
                                },
                                arrivalPulse = csScrollArrivalPulse,
                            )
                        }
                    } else {
                        items(renderRows, key = { it.data }) { ep ->
                            val data = toRowData(ep)
                            com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeListEntry(
                                data = data,
                                display = csListDisplay,
                                onClick = {
                                    if (!data.isCurrent) onEpisodeSwitch(ep)
                                },
                                // ROUND 106 (WS-D): the badge's actions.
                                downloadActions = if (csShowDownloadButton) csDownloadActionsFor(ep) else null,
                                // ROUND 104 (WS-D): the swipe-to-toggle — the
                                // ordinal identity (the toRowData math), guarded
                                // on a blank mainId; the progress map above
                                // re-emits live on every toggle.
                                onToggleWatched = {
                                    if (mainId.isNotBlank()) {
                                        val dn = flavorOrdinals[ep.data]?.toFloat() ?: ep.episodeNumber
                                        csPageScrollScope.launch {
                                            csWatchProgressStore.toggleWatched(
                                                CsWatchViewModel.episodeKey(mainId, dn),
                                            )
                                        }
                                    }
                                },
                                arrivalPulse = csScrollArrivalPulse,
                            )
                        }
                    }
                }
            }

            // ROUND 102 (WS-F): the in-player episode-list settings sheet is
            // RETIRED — the customization moved to the dedicated Settings page
            // (Appearance → "Player page"; the shared
            // PlayerEpisodeListPreferences drive BOTH player stacks).

            // ScrollBlurOverlay — the gradient where content meets the player
            // (shared design-system component, same as the aniyomi page).
            com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay(
                scrollOffset = {
                    if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                    else listState.firstVisibleItemScrollOffset.toFloat()
                },
                backgroundColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }

    // ── ROUND 109 (D-721): THE CS RESOLVE SHEET, DOWNLOAD MODE — the SAME
    //    bottom-up resolved-video list the details page opens (Task 58's
    //    download mode: the "Download EP N" header, the progressive stream
    //    list, the server/audio/resolution chips), mounted right here in the
    //    player's own module. The tap already resolved the folder gate; the
    //    sheet resolves the streams itself; the USER'S pick — and only the
    //    pick — enqueues through the same CsDownloadRequestBuilder chain the
    //    details page's handleCsDownloadPick uses (D-550's sibling DASH
    //    variants ride the full list; D-552's chosen height rides the pick).
    //    Dismissal cancels the resolution and downloads nothing. ──
    csDownloadSheetKey?.let { sheetKey ->
        com.confused.anikuta.feature.cswatch.impl.CsResolveSheet(
            key = sheetKey,
            onDismiss = { csDownloadSheetKey = null },
            onPlay = { csDownloadSheetKey = null },
            onDownload = { pickedKey, link, subtitles, allLinks, chosenHeight ->
                csDownloadSheetKey = null
                if (mainId.isNotBlank()) {
                    csPageScrollScope.launch {
                        try {
                            val content = csPlayerDownloadController.contentInfoFor(mainId)
                            if (content == null) {
                                com.confused.anikuta.core.designsystem.component.toast.AppToast.show(
                                    "No download source for this episode",
                                    com.confused.anikuta.core.designsystem.component.toast.AppToastTone.ERROR,
                                )
                                return@launch
                            }
                            val request = com.confused.anikuta.core.download.cs.CsDownloadRequestBuilder.build(
                                content = content,
                                episode = com.confused.anikuta.core.download.DownloadEpisodeInfo(
                                    episodeKey = pickedKey.episodeData,
                                    episodeNumber = pickedKey.episodeNumber,
                                    name = pickedKey.episodeTitle
                                        .ifBlank { "Episode ${pickedKey.episodeNumber.toInt()}" },
                                ),
                                link = link,
                                subtitles = subtitles,
                                sourceId = pickedKey.sourceId.takeIf { it != 0L },
                                allLinks = allLinks,
                                chosenHeight = chosenHeight,
                            )
                            val taskId = csDownloadManager.enqueueDownload(request)
                            com.confused.anikuta.core.designsystem.component.toast.AppToast.show(
                                "Download queued: ${link.qualityLabel} · ${link.name}",
                                com.confused.anikuta.core.designsystem.component.toast.AppToastTone.SUCCESS,
                            )
                            com.confused.anikuta.core.common.Logger.i("Anikuta:CS:Watch") {
                                "player download enqueued (user pick) taskId=$taskId " +
                                    "(${link.qualityLabel}, ${link.name}${chosenHeight?.let { ", ${it}p" } ?: ""})"
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            com.confused.anikuta.core.common.Logger.e("Anikuta:CS:Watch", e) {
                                "player download failed"
                            }
                            com.confused.anikuta.core.designsystem.component.toast.AppToast.show(
                                "Download failed: ${e.message}",
                                com.confused.anikuta.core.designsystem.component.toast.AppToastTone.ERROR,
                            )
                        }
                    }
                }
            },
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Currently-playing section (the aniyomi "Currently playing episode N" card)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun CsCurrentlyPlayingSection(
    uiState: CsWatchViewModel.CsWatchUiState,
    episodeRating: Int?,
    onRate: (Int) -> Unit,
    // Task 56: the per-flavor ordinal for the current episode (null = the
    // raw number) — "Currently playing episode 1" on a Dub row, not 13.
    currentDisplayNumber: Float = uiState.episodeNumber,
) {
    val currentEpNum = uiState.episodeNumber.toInt()
    val currentMeta = uiState.episodeMetadata[currentEpNum]
    // Task 56: strip the flavor tag from the fallback title (the pills below
    // already show Sub/Dub — the title never repeats it).
    val currentDisplayTitle = currentMeta?.title
        ?: com.confused.anikuta.core.common.EpisodeTitleParser
            .getDisplayTitle(CsSubDubSiblings.stripTag(uiState.episodeTitle), currentDisplayNumber)
    val currentDescription = currentMeta?.description

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        shape = RoundedCornerShape(0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
        ) {
            Text(
                text = "Currently playing episode " +
                    com.confused.anikuta.core.common.EpisodeTitleParser.formatEpisodeNumber(currentDisplayNumber),
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = currentDisplayTitle,
                fontFamily = RobotoFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Per-episode star rating (below the episode title — aniyomi parity).
            Spacer(Modifier.height(6.dp))
            CsStarRatingBar(
                rating = episodeRating,
                onRate = onRate,
            )
            // Provider + quality + sub/dub pills
            val qualityLabel = uiState.currentLink?.qualityLabel
            val subDub = currentMeta?.scanlator?.takeIf { it.isNotBlank() }
            if (qualityLabel != null || subDub != null) {
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (qualityLabel != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        ) {
                            Text(
                                text = qualityLabel,
                                fontFamily = RobotoFamily,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                    if (subDub != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        ) {
                            Text(
                                text = subDub,
                                fontFamily = RobotoFamily,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
            // Synopsis with show more / show less
            if (!currentDescription.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                var expanded by remember(currentEpNum) { mutableStateOf(false) }
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = currentDescription,
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (expanded) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (currentDescription.length > 60) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (expanded) "Show less" else "Show more",
                                fontFamily = RobotoFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { expanded = !expanded },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 103 (WS-4): the private CsEpisodeListRow is RETIRED — both player
//  stacks and the settings preview render through the ONE shared four-paradigm
//  dispatcher (:core:designsystem/component/playerlist/PlayerEpisodeListEntry).
// ════════════════════════════════════════════════════════════════════════════

/** 10 clickable stars, each = 10 points (the aniyomi WatchStarRatingBar replica). */
@Composable
private fun CsStarRatingBar(
    rating: Int?,
    onRate: (Int) -> Unit,
) {
    val currentStars = rating?.let { (it / 10).coerceIn(0, 10) } ?: 0
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 1..10) {
            Icon(
                imageVector = if (i <= currentStars) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = "Rate $i stars",
                tint = if (i <= currentStars) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .clickable {
                        if (i == currentStars) onRate(0) else onRate(i)
                    },
            )
        }
    }
}

/** "MMM d, yyyy" — the aniyomi page's date format. */
private fun formatDate(epochMillis: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.US)
    return sdf.format(Date(epochMillis))
}
