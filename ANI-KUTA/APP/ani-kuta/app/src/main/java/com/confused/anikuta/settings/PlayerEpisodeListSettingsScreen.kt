package com.confused.anikuta.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.common.EpisodeTitleParser
import com.confused.anikuta.settings.search.SettingsHighlightTarget
import com.confused.anikuta.settings.search.rememberSettingsAnchorScroll
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.datacache.DataCacheRepository
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.component.ThinSlider
import com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeGridRow
import com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeListEntry
import com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeListStyle
import com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeRowData
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.PlayerEpisodeListPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * ══════════════════════════════════════════════════════════════════════════
 *  ROUND 102 (WS-G) → ROUND 103 (WS-4) → ROUND 104 (WS-D / D-703) REWORKED:
 *  PlayerEpisodeListSettingsScreen
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  The v1.1.60 device round's orders, verbatim intent:
 *  • THE WATCHED FILTER IS GONE — "Most definitely not needed, and it is
 *    apparently unnecessary… It should be completely removed." (The card, the
 *    slot guards, and both stacks' filter branches all retired together.)
 *  • THE PREVIEW'S SCROLLING = THE DETAILS PAGE'S — "Just like how it is
 *    being handled properly for the episode list page… I want the same kind
 *    of effect, same scrolling, the same logics and everything like that to
 *    be exactly the same. Like if I scroll that, then one of them should get
 *    hidden and the other one should snap properly, and same goes for the
 *    scrolling up too." → THE D-557/D-558 MACHINERY, ported verbatim: the
 *    direction-split priority scroll (DOWN-while-open consumes everything
 *    until the preview snaps collapsed, then releases the same gesture into
 *    the list; UP never consumes pre-scroll — the list scrolls first, the
 *    leftover expands the preview), the halfway snap, the crossed latch, the
 *    settle windows, the fling-momentum handoff, and the GRID exemption.
 *  • THE ELEMENTS ARE STYLE-AWARE, appearing/disappearing SMOOTHLY ("the
 *    options below, the elements options, should properly adjust
 *    accordingly and should disappear or appear smoothly depending on what's
 *    available to edit or what's not available to edit"). ROUND 113
 *    (D-736): the Elements card carries the on/off toggles ONLY; the
 *    multi-state knobs live in the dedicated Grid / Banner cards (the
 *    details page's own Cinema/Grid card structure, mirrored — the parity
 *    order). The style-filtered toggle sets:
 *      CLASSIC   = Synopsis · Release date · Audio pills · Watch progress · Dim watched
 *      TRACKLIST = Synopsis · Release date · Audio pills · Watch progress · Dim watched
 *      GRID      = Release date · Audio pills · Watch progress · Watched checkmark · Dim watched
 *      BANNER    = Release date · Audio pills · Watch progress · Episode
 *                  number · Watched check mark · Dim watched
 *  • THE BANNER'S SIZE SLIDER — ROUND 105 re-aimed the round-104 knob
 *    ("it should not change the height of the banner, but… the actual size
 *    of the whole thumbnail cover image banner itself"): the aspect is
 *    FIXED 16:9 and the slider scales the item's width fraction, centered,
 *    with growing inter-item padding (the live % + the Small/Full ends).
 *  • THE PREVIEW IS SWIPEABLE — the details preview's interactive pattern
 *    (the local watchedOverride flip through the SAME shared gesture the
 *    player lists use).
 *
 *  The preview's two slots: slot 1 styled as the CURRENTLY PLAYING episode
 *  (the ring/border treatment + a partial progress bar), slot 2 as a WATCHED
 *  one (the dim/grayscale treatment) — every element the player list can
 *  draw, drawn by the player list's own renderer (the D-481 rule).
 */
@Composable
fun PlayerEpisodeListSettingsScreen(
    onBack: () -> Unit,
    playerListPrefs: PlayerEpisodeListPreferences = koinInject(),
    contentRepository: ContentRepository = koinInject(),
    dataCacheRepository: DataCacheRepository = koinInject(),
    // ROUND 105 (WS-C): the search-landing anchor (the page joined the
    // settings search index this round).
    highlightAnchor: String? = null,
) {
    // ── The reactive reads — the SAME prefs both player pages collect. Every
    // write below updates the pref; `changes` re-emits; the preview AND the
    // player lists re-shape from the same emission (no local mirror state,
    // the D-481 drift killer). ──
    val rowStyleKey by playerListPrefs.rowStyle.changes.collectAsState(
        initial = playerListPrefs.rowStyle.get(),
    )
    val style = remember(rowStyleKey) { PlayerEpisodeListStyle.fromKey(rowStyleKey) }
    // ── ROUND 111 (D-729): the Elements grid's per-row button count — the
    //    heading-tap testing aid ("when I click on the Elements heading …
    //    it will switch the grid layout to Three buttons per row. So make
    //    sure to give this functionality so I can test out how the things
    //    will overall look like"). Session-local (survives rotation, never
    //    a pref — an experiment knob, not a setting). ──
    var elementsThreePerRow by rememberSaveable { mutableStateOf(false) }
    val showSynopsis by playerListPrefs.showSynopsis.changes.collectAsState(
        initial = playerListPrefs.showSynopsis.get(),
    )
    val showDatePill by playerListPrefs.showDatePill.changes.collectAsState(
        initial = playerListPrefs.showDatePill.get(),
    )
    val dimWatched by playerListPrefs.dimWatched.changes.collectAsState(
        initial = playerListPrefs.dimWatched.get(),
    )
    // ROUND 104 (WS-D): the BANNER's controls.
    val showEpisodeNumber by playerListPrefs.showEpisodeNumber.changes.collectAsState(
        initial = playerListPrefs.showEpisodeNumber.get(),
    )
    // ROUND 105 (WS-D): the re-aimed size + the position/style + the progress
    // bar + the GRID's three knobs.
    val showProgressBar by playerListPrefs.showProgressBar.changes.collectAsState(
        initial = playerListPrefs.showProgressBar.get(),
    )
    val bannerSize by playerListPrefs.bannerSize.changes.collectAsState(
        initial = playerListPrefs.bannerSize.get(),
    )
    val bannerNumberPositionKey by playerListPrefs.bannerNumberPosition.changes.collectAsState(
        initial = playerListPrefs.bannerNumberPosition.get(),
    )
    val bannerNumberPosition = remember(bannerNumberPositionKey) {
        com.confused.anikuta.core.designsystem.component.playerlist.PlayerBannerNumberPosition
            .fromKey(bannerNumberPositionKey)
    }
    val bannerNumberStyleKey by playerListPrefs.bannerNumberStyle.changes.collectAsState(
        initial = playerListPrefs.bannerNumberStyle.get(),
    )
    val bannerNumberStyle = remember(bannerNumberStyleKey) {
        com.confused.anikuta.core.designsystem.component.playerlist.PlayerBannerNumberStyle
            .fromKey(bannerNumberStyleKey)
    }
    val gridWatchedCheckmark by playerListPrefs.gridWatchedCheckmark.changes.collectAsState(
        initial = playerListPrefs.gridWatchedCheckmark.get(),
    )
    val gridCurrentStyleKey by playerListPrefs.gridCurrentStyle.changes.collectAsState(
        initial = playerListPrefs.gridCurrentStyle.get(),
    )
    val gridCurrentStyle = remember(gridCurrentStyleKey) {
        com.confused.anikuta.core.designsystem.component.playerlist.PlayerGridCurrentStyle
            .fromKey(gridCurrentStyleKey)
    }
    // ROUND 108 (D-713): the GRID's title-line MODE — the round-105 Boolean
    // (gridTitles) is retired for the richer knob ("whether to show the
    // episode title or not… or only one line"), resolved through the
    // lenient fromKey (the D-529 lesson).
    val gridTitleModeKey by playerListPrefs.gridTitleMode.changes.collectAsState(
        initial = playerListPrefs.gridTitleMode.get(),
    )
    val gridTitleMode = remember(gridTitleModeKey) {
        com.confused.anikuta.core.common.GridTitleMode.fromKey(gridTitleModeKey)
    }
    // ROUND 110 (D-723): the GRID's number-label placement — the same
    // reactive read, resolved through the lenient fromKey.
    val gridNumberPositionKey by playerListPrefs.gridNumberPosition.changes.collectAsState(
        initial = playerListPrefs.gridNumberPosition.get(),
    )
    val gridNumberPosition = remember(gridNumberPositionKey) {
        com.confused.anikuta.core.common.GridNumberPosition.fromKey(gridNumberPositionKey)
    }
    // ROUND 108 (D-713): the BANNER's watched check mark (the details
    // CINEMA's twin — "a similar kind of thing for the banner view too").
    val bannerWatchedCheck by playerListPrefs.bannerWatchedCheck.changes.collectAsState(
        initial = playerListPrefs.bannerWatchedCheck.get(),
    )
    // ROUND 106 (WS-D): the new knobs — the audio pills, the download
    // button, and the BANNER's currently-playing treatment.
    val showAudioPills by playerListPrefs.showAudioPills.changes.collectAsState(
        initial = playerListPrefs.showAudioPills.get(),
    )
    val showDownloadButton by playerListPrefs.showDownloadButton.changes.collectAsState(
        initial = playerListPrefs.showDownloadButton.get(),
    )
    val bannerCurrentStyleKey by playerListPrefs.bannerCurrentStyle.changes.collectAsState(
        initial = playerListPrefs.bannerCurrentStyle.get(),
    )
    val bannerCurrentStyle = remember(bannerCurrentStyleKey) {
        com.confused.anikuta.core.designsystem.component.playerlist.PlayerBannerCurrentStyle
            .fromKey(bannerCurrentStyleKey)
    }
    val sortDescending by playerListPrefs.sortDescending.changes.collectAsState(
        initial = playerListPrefs.sortDescending.get(),
    )

    // ── THE ACTUAL-DATA PREVIEW — the details page's loader, verbatim (demo
    // samples first for the instant paint, then a RANDOM qualifying library
    // series with REAL titles/dates/audio/imagery replaces them). ──
    val context = LocalContext.current
    var previewItems by remember {
        mutableStateOf(demoPreviewItems(context.packageName))
    }
    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching { loadLibraryPreviewItems(contentRepository, dataCacheRepository) }.getOrNull()
        }
        if (loaded != null) previewItems = loaded
    }
    // ROUND 105 (WS-D): the preview's TRACKLIST column-sizing reference —
    // the preview's own widest episode number (the real library set once it
    // lands; the demo samples until then). Both stacks pass their own list's
    // widest number the same way.
    val tracklistReferenceNumber = remember(previewItems) {
        val maxNumber = previewItems.maxOfOrNull { it.episode.episode_number } ?: 24f
        EpisodeTitleParser.formatEpisodeNumber(maxNumber)
    }
    val display = remember(
        style, showSynopsis, showDatePill, showAudioPills, dimWatched,
        showEpisodeNumber, showProgressBar, showDownloadButton, bannerSize,
        bannerNumberPosition, bannerNumberStyle, bannerCurrentStyle,
        gridWatchedCheckmark, gridCurrentStyle, gridTitleMode, tracklistReferenceNumber,
        bannerWatchedCheck, gridNumberPosition,
    ) {
        com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeListDisplay(
            style = style,
            showSynopsis = showSynopsis,
            showDatePill = showDatePill,
            showAudioPills = showAudioPills,
            dimWatched = dimWatched,
            showEpisodeNumber = showEpisodeNumber,
            showProgressBar = showProgressBar,
            showDownloadButton = showDownloadButton,
            bannerSize = bannerSize,
            bannerNumberPosition = bannerNumberPosition,
            bannerNumberStyle = bannerNumberStyle,
            bannerCurrentStyle = bannerCurrentStyle,
            gridWatchedCheckmark = gridWatchedCheckmark,
            gridCurrentStyle = gridCurrentStyle,
            gridTitleMode = gridTitleMode,
            tracklistReferenceNumber = tracklistReferenceNumber,
            bannerWatchedCheck = bannerWatchedCheck,
            gridNumberPosition = gridNumberPosition,
        )
    }

    // ── ROUND 106 (WS-D): THE PREVIEW'S DOWNLOAD BADGE DEMO — the D-557
    // pattern: a cycling render state + a previewTapAll bag (one tap walks
    // every state; the preview's rows stay inert otherwise). The CURRENT
    // row carries the demo badge (the row the eye sits on). ──
    val demoDownloadStates = remember {
        listOf(
            com.confused.anikuta.core.designsystem.component.playerlist.PlayerDownloadRenderState.NotDownloaded,
            com.confused.anikuta.core.designsystem.component.playerlist.PlayerDownloadRenderState.InFlight,
            com.confused.anikuta.core.designsystem.component.playerlist.PlayerDownloadRenderState.Downloading(64),
            com.confused.anikuta.core.designsystem.component.playerlist.PlayerDownloadRenderState.Paused,
            com.confused.anikuta.core.designsystem.component.playerlist.PlayerDownloadRenderState.Error,
            com.confused.anikuta.core.designsystem.component.playerlist.PlayerDownloadRenderState.Downloaded,
        )
    }
    var demoDownloadIndex by remember { mutableStateOf(0) }
    val demoDownloadState = demoDownloadStates[demoDownloadIndex % demoDownloadStates.size]
    val demoDownloadActions = remember {
        com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeDownloadActions(
            onDownload = {},
            onPause = {},
            onResume = {},
            onCancel = {},
            onRetry = {},
            onPlayDownloaded = {},
            previewTapAll = { demoDownloadIndex += 1 },
        )
    }

    // The preview's slot data — ordered by the LIVE direction; the WATCHED
    // slot's state is a local override the preview's swipe flips (the details
    // preview's interactive pattern; the filter guards are gone with the
    // filter itself).
    val orderedItems = remember(previewItems, sortDescending) {
        if (sortDescending) previewItems.reversed() else previewItems
    }
    val watchedOverride = remember { mutableStateMapOf<String, Boolean>() }
    val currentUrl = orderedItems.getOrNull(0)?.episode?.url
    val watchedUrl = orderedItems.getOrNull(1)?.episode?.url
    // ROUND 106 (WS-D): the CURRENT row carries the demo badge when the
    // download toggle is on (tap it to cycle every state — the D-557 demo).
    val previewDownloadState = if (showDownloadButton) demoDownloadState else null
    val currentData = orderedItems.getOrNull(0)?.toPlayerRowData(
        isCurrent = true,
        isWatched = currentUrl?.let { watchedOverride[it] } ?: false,
        progressFraction = 0.35f,
        downloadState = previewDownloadState,
    )
    val watchedData = orderedItems.getOrNull(1)?.toPlayerRowData(
        isCurrent = false,
        isWatched = watchedUrl?.let { watchedOverride[it] } ?: true,
        progressFraction = 0f,
        downloadState = if (showDownloadButton) {
            com.confused.anikuta.core.designsystem.component.playerlist.PlayerDownloadRenderState.NotDownloaded
        } else {
            null
        },
    )

    val isGrid = style == PlayerEpisodeListStyle.GRID
    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0
    // ROUND 105 (WS-C): the search-landing scroll — the page joined the
    // settings search index; the anchors map to the options' items.
    // ROUND 113 (D-736): the two new style cards (Grid / Banner) sit at
    // items 2 and 3 — the download/sort indices shifted with them, and the
    // new cards' anchors joined the map.
    rememberSettingsAnchorScroll(
        anchor = highlightAnchor,
        anchorIndexFor = { anchor ->
            when (anchor) {
                "player_episode_list", "player_layout" -> 0
                "player_elements" -> 1
                "player_grid" -> 2
                "player_banner" -> 3
                "player_download" -> 4
                "player_sort" -> 5
                else -> null
            }
        },
        listState = lazyListState,
    )

    // ═══ ROUND 108 (D-713): THE SHARED PREVIEW-COLLAPSE SCROLL ═══
    // The round-104 port of the D-557/D-558 machinery is retired — BOTH
    // episode-list settings screens mount the ONE shared controller now
    // (PreviewCollapseScroll.kt), which fixes the v1.1.64 device round's
    // two defects at the source: the entry scroll now ALWAYS opens at the
    // very top with the preview open (the rememberSaveable restore used to
    // bring the old offset back), and the flick runs the ordered
    // choreography — the smooth hide, the lock beat ("it will not allow the
    // user to scroll for a few bit for a few time"), then the automatic
    // velocity-proportional scroll of the options (the canonical
    // scroll-scope decay; the old dispatchRawDelta handoff never moved on
    // device, and the old -1000px/s threshold let weaker flicks scroll the
    // list under an OPEN preview). The drag semantics (the two-phase snap,
    // the up-leftover expansion) are unchanged — the details page's
    // machinery, still exactly the same, now literally one implementation.
    val previewCollapse = rememberPreviewCollapseScroll(
        listState = lazyListState,
        enabled = !isGrid,
    )
    PreviewCollapseEntryReset(previewCollapse, hasAnchor = highlightAnchor != null)
    // A layout switch always re-opens the preview — a stale collapsed state
    // under GRID (whose connection never engages) would clip it forever.
    LaunchedEffect(style) { previewCollapse.reopen() }
    // The clip+shift layout's hide distance — the first episode's measured
    // height + the row gap, scaled by the collapse fraction.
    val hidePx = previewCollapse.hidePx

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                // ROUND 105 (WS-C): retitled to match the Appearance entry
                // ("the bottom one… as player page").
                title = "Player page",
                collapsed = collapsed,
                onBack = onBack,
            )

            // ── THE LIVE PREVIEW — the player's OWN renderer (the shared
            // four-paradigm dispatcher), fed the REAL library episodes; the
            // clip+shift layout collapses it per the D-557/D-558 machinery;
            // the animateContentSize makes LAYOUT SWITCHES glide. ──
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                EpisodeSettingsCard(label = "Live preview") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clipToBounds()
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                val shift = hidePx.roundToInt()
                                val visible = (placeable.height - shift).coerceAtLeast(1)
                                layout(placeable.width, visible) {
                                    placeable.placeRelative(0, -shift)
                                }
                            },
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(
                                    animationSpec = tween(360),
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (isGrid) {
                                // The wall pairs its cells two-across — the
                                // preview mirrors the real list's shape. The
                                // preview's long-press flips the slot's
                                // watched state (the same override the swipe
                                // writes on the row styles).
                                if (currentData != null) {
                                    PlayerEpisodeGridRow(
                                        left = currentData,
                                        right = watchedData,
                                        display = display,
                                        onClick = { /* the preview is inert */ },
                                        downloadActions = { if (showDownloadButton) demoDownloadActions else null },
                                        onToggleWatched = { data ->
                                            val url = when (data) {
                                                currentData -> currentUrl
                                                else -> watchedUrl
                                            }
                                            val default = when (data) {
                                                currentData -> false
                                                else -> true
                                            }
                                            if (url != null) {
                                                watchedOverride[url] =
                                                    !(watchedOverride[url] ?: default)
                                            }
                                        },
                                    )
                                }
                            } else {
                                if (currentData != null) {
                                    Box(
                                        modifier = Modifier.onSizeChanged { size ->
                                            previewCollapse.firstRowHeightPx = size.height.toFloat()
                                        },
                                    ) {
                                        PlayerEpisodeListEntry(
                                            data = currentData,
                                            display = display,
                                            onClick = { /* inert */ },
                                            downloadActions = if (showDownloadButton) demoDownloadActions else null,
                                            // The preview's swipe — the same
                                            // shared gesture, flipping the
                                            // slot's local watched override.
                                            onToggleWatched = {
                                                if (currentUrl != null) {
                                                    watchedOverride[currentUrl] =
                                                        !(watchedOverride[currentUrl] ?: false)
                                                }
                                            },
                                        )
                                    }
                                }
                                if (watchedData != null) {
                                    PlayerEpisodeListEntry(
                                        data = watchedData,
                                        display = display,
                                        onClick = { /* inert */ },
                                        downloadActions = if (showDownloadButton) demoDownloadActions else null,
                                        onToggleWatched = {
                                            if (watchedUrl != null) {
                                                watchedOverride[watchedUrl] =
                                                    !(watchedOverride[watchedUrl] ?: true)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── THE SCROLLABLE REGION — the options (the D-525 8dp gutter).
            // The nestedScroll connection intercepts the collapse-phase drags
            // BEFORE the list sees them (the D-557 two-phase snap). ──
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize().nestedScroll(previewCollapse.connection),
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        bottom = 24.dp,
                    ),
                ) {
                    // ── Layout — THE FOUR PARADIGMS (ROUND 104: the compact
                    //    slot's replacement is the TRACKLIST). ROUND 105: the
                    //    card label IS the title (the duplicated inner heading
                    //    is gone — "the options are not that well formatted"),
                    //    and a per-style caption speaks the paradigm's shape. ──
                    item {
                        SettingsHighlightTarget(anchorId = "player_layout", activeAnchor = highlightAnchor) {
                        SettingsHighlightTarget(anchorId = "player_episode_list", activeAnchor = highlightAnchor) {
                        EpisodeSettingsCard(label = "Layout") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                SegmentedToggle(
                                    options = listOf("Classic", "Tracklist", "Grid", "Banner"),
                                    selectedIndex = style.ordinal,
                                    onSelect = { idx ->
                                        playerListPrefs.rowStyle.set(
                                            PlayerEpisodeListStyle.entries[idx].name,
                                        )
                                    },
                                )
                                Caption(
                                    text = when (style) {
                                        PlayerEpisodeListStyle.CLASSIC ->
                                            "The details page's Classic row, verbatim"
                                        PlayerEpisodeListStyle.TRACKLIST ->
                                            "The big number, a spine, and the title"
                                        PlayerEpisodeListStyle.GRID ->
                                            "A two-across wall of covers"
                                        PlayerEpisodeListStyle.BANNER ->
                                            "Full-width cinematic cover cards"
                                    },
                                    modifier = Modifier.padding(top = 10.dp, start = 2.dp),
                                )
                            }
                        }
                        }
                        }
                    }

                    // ── Elements — ROUND 113 (D-736): THE PURE CARD — the
                    //    on/off toggles ONLY ("The elements should be in a
                    //    dedicated separate section"). The style-specific
                    //    multi-state knobs (the GRID's current/titles/number,
                    //    the BANNER's current/number-position/number-style/
                    //    size) moved to their own dedicated Grid / Banner
                    //    cards below — the details page's own Cinema/Grid
                    //    card structure, mirrored (the round's parity order:
                    //    "the player page and the details page episodes
                    //    lists layouts both should almost have similar
                    //    customizability"). The card stays STYLE-AWARE (its
                    //    entries filter to what the selected layout can
                    //    draw — the ROUND 105 doctrine) and the heading tap
                    //    keeps the 2 ↔ 3 testing aid (ROUND 111, D-729). ──
                    item {
                        SettingsHighlightTarget(anchorId = "player_elements", activeAnchor = highlightAnchor) {
                        EpisodeSettingsCard(
                            label = "Elements",
                            onLabelClick = { elementsThreePerRow = !elementsThreePerRow },
                        ) {
                                // ── ROUND 111 (D-729): THE GRID OF BUTTONS —
                                //    the on/off elements as 2-per-row toggle
                                //    buttons ("a grid layout of buttons which
                                //    I can click and turn to toggle them on or
                                //    to toggle them off … two options per
                                //    row"), NO descriptions, and the card-heading
                                //    tap flipping 2 ↔ 3 per row (the testing
                                //    aid). Style-filtered exactly as the old
                                //    rows were; the element titles match the
                                //    details page's own ("the similar
                                //    naming, so that it's easier for us").
                                //    ROUND 113 (D-733): the buttons wear the
                                //    LAYOUT SELECTOR'S OWN segment anatomy;
                                //    the multi-state knobs are NOT on/off
                                //    toggles — they live in the dedicated
                                //    Grid / Banner cards now. —–
                                ElementToggleGrid(
                                    entries = buildList {
                                        if (style == PlayerEpisodeListStyle.CLASSIC ||
                                            style == PlayerEpisodeListStyle.TRACKLIST
                                        ) {
                                            add(
                                                ElementToggleEntry(
                                                    title = "Synopsis",
                                                    checked = showSynopsis,
                                                    onToggle = { playerListPrefs.showSynopsis.set(!showSynopsis) },
                                                ),
                                            )
                                        }
                                        add(
                                            ElementToggleEntry(
                                                title = "Release date",
                                                checked = showDatePill,
                                                onToggle = { playerListPrefs.showDatePill.set(!showDatePill) },
                                            ),
                                        )
                                        add(
                                            ElementToggleEntry(
                                                title = "Audio pills",
                                                checked = showAudioPills,
                                                onToggle = { playerListPrefs.showAudioPills.set(!showAudioPills) },
                                            ),
                                        )
                                        add(
                                            ElementToggleEntry(
                                                title = "Watch progress",
                                                checked = showProgressBar,
                                                onToggle = { playerListPrefs.showProgressBar.set(!showProgressBar) },
                                            ),
                                        )
                                        if (style == PlayerEpisodeListStyle.GRID) {
                                            // ROUND 109 (D-720): the check ONLY —
                                            // the grayness follows the Dim knob.
                                            add(
                                                ElementToggleEntry(
                                                    title = "Watched checkmark",
                                                    checked = gridWatchedCheckmark,
                                                    onToggle = { playerListPrefs.gridWatchedCheckmark.set(!gridWatchedCheckmark) },
                                                ),
                                            )
                                        }
                                        if (style == PlayerEpisodeListStyle.BANNER) {
                                            add(
                                                ElementToggleEntry(
                                                    title = "Episode number",
                                                    checked = showEpisodeNumber,
                                                    onToggle = { playerListPrefs.showEpisodeNumber.set(!showEpisodeNumber) },
                                                ),
                                            )
                                            add(
                                                ElementToggleEntry(
                                                    title = "Watched check mark",
                                                    checked = bannerWatchedCheck,
                                                    onToggle = { playerListPrefs.bannerWatchedCheck.set(!bannerWatchedCheck) },
                                                ),
                                            )
                                        }
                                        add(
                                            ElementToggleEntry(
                                                title = "Dim watched",
                                                checked = dimWatched,
                                                onToggle = { playerListPrefs.dimWatched.set(!dimWatched) },
                                            ),
                                        )
                                    },
                                    columns = if (elementsThreePerRow) 3 else 2,
                                )
                        }
                        }
                    }

                    // ── ROUND 113 (D-736): THE GRID CARD — the GRID's own
                    //    multi-state knobs in their dedicated section, the
                    //    details page's Grid card mirrored (the same
                    //    smooth appear/disappear the details screen's
                    //    Cinema/Grid cards own — the wrapper Column is the
                    //    D-498 AnimatedVisibility-receiver pattern both
                    //    screens' style cards share). ──
                    item {
                        Column {
                        AnimatedVisibility(
                            visible = style == PlayerEpisodeListStyle.GRID,
                            enter = fadeIn(animationSpec = tween(300)) +
                                expandVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)),
                            exit = fadeOut(animationSpec = tween(240)) +
                                shrinkVertically(animationSpec = tween(240, easing = FastOutSlowInEasing)),
                        ) {
                            SettingsHighlightTarget(anchorId = "player_grid", activeAnchor = highlightAnchor) {
                            EpisodeSettingsCard(label = "Grid") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                SegmentedRow(
                                    title = "Currently playing",
                                    description = "How the playing episode is highlighted",
                                        options = listOf("Play button", "Themed tint"),
                                        selectedIndex = if (gridCurrentStyle ==
                                            com.confused.anikuta.core.designsystem.component.playerlist.PlayerGridCurrentStyle.TINT
                                        ) 1 else 0,
                                        onSelect = { idx ->
                                            playerListPrefs.gridCurrentStyle.set(
                                                if (idx == 1) "TINT" else "PLAY",
                                            )
                                        },
                                    )
                                // ── ROUND 108 (D-713): THE GRID'S TITLE MODE —
                                //    the round-105 Boolean is retired for the
                                //    v1.1.64 order: "he can select whether to
                                //    show the episode title or not, and also he
                                //    can decide whether to show the full
                                //    episode title or only one line" — ONE
                                //    segmented speaks all three states. The
                                //    line itself only ever renders a REAL
                                //    English-readable title (the gate lives in
                                //    the renderer, gridShowableTitle). ──
                                SegmentedRow(
                                    title = "Episode titles",
                                        description = "Only real English titles render the line",
                                        options = listOf("Off", "1 line", "Full"),
                                        selectedIndex = when (gridTitleMode) {
                                            com.confused.anikuta.core.common.GridTitleMode.OFF -> 0
                                            com.confused.anikuta.core.common.GridTitleMode.ONE_LINE -> 1
                                            com.confused.anikuta.core.common.GridTitleMode.TWO_LINES -> 2
                                        },
                                        onSelect = { idx ->
                                            playerListPrefs.gridTitleMode.set(
                                                when (idx) {
                                                    0 -> "OFF"
                                                    1 -> "ONE"
                                                    else -> "TWO"
                                                },
                                            )
                                        },
                                    )
                                // ── ROUND 110 (D-723): the GRID's number-label
                                //    placement — the details grid's new knob,
                                //    shared (UNDER_THUMB keeps today's anatomy;
                                //    BESIDE_DETAILS rides the EP label on the
                                //    title's line). ──
                                SegmentedRow(
                                    title = "Episode number",
                                        description = "Where the EP label sits in each cell",
                                        options = listOf("Below thumbnail", "Beside title"),
                                        selectedIndex = if (gridNumberPosition ==
                                            com.confused.anikuta.core.common.GridNumberPosition.BESIDE_DETAILS
                                        ) 1 else 0,
                                        onSelect = { idx ->
                                            playerListPrefs.gridNumberPosition.set(
                                                if (idx == 1) "BESIDE_DETAILS" else "UNDER_THUMB",
                                            )
                                        },
                                    )
                                }
                            }
                            }
                        }
                        }
                    }

                    // ── ROUND 113 (D-736): THE BANNER CARD — the BANNER's own
                    //    multi-state knobs in their dedicated section (the
                    //    Grid card's twin; the wrapper Column carries the
                    //    same D-498 receiver pattern). The number position/
                    //    style rows stay nested under the Episode-number
                    //    toggle's gate (the ROUND 105 nesting, kept). ──
                    item {
                        Column {
                        AnimatedVisibility(
                            visible = style == PlayerEpisodeListStyle.BANNER,
                            enter = fadeIn(animationSpec = tween(300)) +
                                expandVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)),
                            exit = fadeOut(animationSpec = tween(240)) +
                                shrinkVertically(animationSpec = tween(240, easing = FastOutSlowInEasing)),
                        ) {
                            SettingsHighlightTarget(anchorId = "player_banner", activeAnchor = highlightAnchor) {
                            EpisodeSettingsCard(label = "Banner") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                // ── ROUND 106 (WS-D): the BANNER's
                                //    currently-playing treatment — the GRID's
                                //    PLAY/TINT knob, ported ("the grid view
                                //    has the ability to select between play
                                //    button and the themed tint, but the
                                //    banner does not have it"). ──
                                SegmentedRow(
                                    title = "Currently playing",
                                    description = "How the playing episode is highlighted",
                                    options = listOf("Play button", "Themed tint"),
                                    selectedIndex = if (bannerCurrentStyle ==
                                        com.confused.anikuta.core.designsystem.component.playerlist.PlayerBannerCurrentStyle.TINT
                                    ) 1 else 0,
                                    onSelect = { idx ->
                                        playerListPrefs.bannerCurrentStyle.set(
                                            if (idx == 1) "TINT" else "PLAY",
                                        )
                                    },
                                )
                                AnimatedStyleRow(
                                    visible = showEpisodeNumber,
                                    modifier = Modifier.padding(start = 12.dp),
                                ) {
                                    SegmentedRow(
                                        title = "Number position",
                                        description = "Which top corner carries the number",
                                        options = listOf("Top left", "Top right"),
                                        selectedIndex = if (bannerNumberPosition ==
                                            com.confused.anikuta.core.designsystem.component.playerlist.PlayerBannerNumberPosition.TOP_START
                                        ) 0 else 1,
                                        onSelect = { idx ->
                                            playerListPrefs.bannerNumberPosition.set(
                                                if (idx == 0) "TOP_START" else "TOP_END",
                                            )
                                        },
                                    )
                                }
                                AnimatedStyleRow(
                                    visible = showEpisodeNumber,
                                    modifier = Modifier.padding(start = 12.dp),
                                ) {
                                    SegmentedRow(
                                        title = "Number style",
                                        description = "Solid themed or frosted glass",
                                        options = listOf("Frosted", "Solid"),
                                        selectedIndex = if (bannerNumberStyle ==
                                            com.confused.anikuta.core.designsystem.component.playerlist.PlayerBannerNumberStyle.SOLID
                                        ) 1 else 0,
                                        onSelect = { idx ->
                                            playerListPrefs.bannerNumberStyle.set(
                                                if (idx == 1) "SOLID" else "FROSTED",
                                            )
                                        },
                                    )
                                }
                                // ── The BANNER's SIZE slider (ROUND 105,
                                //    re-aimed): "It should not change the
                                //    height of the banner, but what it should
                                //    change is the actual size of the whole
                                //    thumbnail cover image banner itself… If
                                //    the user has selected it to be smaller,
                                //    then there will be some padding on the
                                //    left and right sides and also some
                                //    padding between each individual episodes
                                //    themselves." The ends speak the truth
                                //    now; the live value is the width %. ──
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text(
                                                text = "Banner size",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(
                                                text = "${(bannerSize.coerceIn(0f, 1f) * 100).toInt()}%",
                                                fontFamily = RobotoFamily,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                        Text(
                                            text = "How wide each banner sits on the page",
                                            fontFamily = RobotoFamily,
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                        ThinSlider(
                                            value = bannerSize,
                                            onValueChange = { playerListPrefs.bannerSize.set(it) },
                                            valueRange = 0f..1f,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 6.dp),
                                            contentDescription = "Banner size",
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(
                                                text = "Small",
                                                fontFamily = RobotoFamily,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Text(
                                                text = "Full",
                                                fontFamily = RobotoFamily,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        }
                        }
                    }

                    // (ROUND 104 (WS-D): THE WATCHED FILTER CARD IS RETIRED —
                    // "Most definitely not needed, and it is apparently
                    // unnecessary… It should be completely removed.")

                    // ── ROUND 106 (WS-D): THE DOWNLOAD BUTTON — the
                    //    dedicated section the v1.1.62 round ordered: "in
                    //    the player page there could be a toggle for this,
                    //    like a dedicated separate toggle, like given a
                    //    proper dedicated section for it, like download, and
                    //    the toggle will be used to turn on or turn off the
                    //    download button for every single one of them. If
                    //    turned off, then on the player page the download
                    //    button will not show. But if it is turned on, then
                    //    the download button will show." ──
                    item {
                        SettingsHighlightTarget(anchorId = "player_download", activeAnchor = highlightAnchor) {
                        EpisodeSettingsCard(label = "Download") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                SwitchRow(
                                    title = "Download button",
                                    description = "The download control on every episode",
                                    checked = showDownloadButton,
                                    onChecked = { playerListPrefs.showDownloadButton.set(it) },
                                )
                                Caption(
                                    text = "When on, every layout carries the badge — " +
                                        "tap to download, watch the progress ring, " +
                                        "pause/resume/retry in place, and tap the check " +
                                        "to play the offline file.",
                                    modifier = Modifier.padding(top = 8.dp, start = 2.dp),
                                )
                            }
                        }
                        }
                    }

                    // ── Sort — DIRECTION ONLY (ROUND 105: the card label is
                    //    the title; the description carries the key). ──
                    item {
                        SettingsHighlightTarget(anchorId = "player_sort", activeAnchor = highlightAnchor) {
                        EpisodeSettingsCard(label = "Sort") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                SegmentedToggle(
                                    options = listOf("Ascending", "Descending"),
                                    selectedIndex = if (sortDescending) 1 else 0,
                                    onSelect = { idx ->
                                        playerListPrefs.sortDescending.set(idx == 1)
                                    },
                                )
                                Caption(
                                    text = "The episode number's order",
                                    modifier = Modifier.padding(top = 10.dp, start = 2.dp),
                                )
                            }
                        }
                        }
                    }
                }

                // The settings-page scroll blur (the shared design-system
                // overlay — the same top-edge fade every settings page wears).
                ScrollBlurOverlay(
                    scrollOffset = {
                        if (lazyListState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                        else lazyListState.firstVisibleItemScrollOffset.toFloat()
                    },
                    backgroundColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 104 (WS-D): the style-aware elements row — the smooth appear/
//  disappear wrapper ("disappear or appear smoothly depending on what's
//  available to edit").
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun AnimatedStyleRow(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(tween(300, easing = FastOutSlowInEasing)) +
            fadeIn(tween(300)),
        exit = shrinkVertically(tween(300, easing = FastOutSlowInEasing)) +
            fadeOut(tween(300)),
        modifier = modifier,
    ) {
        content()
    }
}

/**
 * The density slider's live aspect label — the known cinematic ratios snap to
 * their names (21:9 / 16:9 / 4:3); everything in between renders one decimal.
 * ROUND 105: RETIRED with the aspect-driven knob (the size slider speaks in
 * width % inline now).
 */

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 103 (WS-4): the preview's mapping — a REAL library PreviewItem into
//  the player renderer's data bundle (the same fields the player stacks map:
//  number, title, thumbnail, date, audio, synopsis).
// ════════════════════════════════════════════════════════════════════════════

private fun PreviewItem.toPlayerRowData(
    isCurrent: Boolean,
    isWatched: Boolean,
    progressFraction: Float,
    // ROUND 106 (WS-D): the preview's demo download state (the cycling
    // badge — null hides it).
    downloadState: com.confused.anikuta.core.designsystem.component.playerlist.PlayerDownloadRenderState? = null,
): PlayerEpisodeRowData {
    val ep = episode
    return PlayerEpisodeRowData(
        episodeNumberText = EpisodeTitleParser.formatEpisodeNumber(ep.episode_number),
        displayTitle = EpisodeTitleParser.getDisplayTitle(ep.name, ep.episode_number),
        thumbnailUrl = ep.preview_url ?: fallbackCoverUrl,
        dateText = if (ep.date_upload > 0) formatDate(ep.date_upload) else null,
        // ROUND 109 (D-719): the SHORT date — the preview's GRID chip shows
        // exactly what the player page's grid chip shows ("Jan 1", the
        // details grid's shape).
        dateTextShort = if (ep.date_upload > 0) {
            com.confused.anikuta.core.designsystem.component.episodelist
                .formatShortDate(ep.date_upload)
        } else null,
        audioLabels = parseAudioLabels(ep.scanlator),
        synopsis = ep.summary,
        isCurrent = isCurrent,
        isWatched = isWatched,
        progressFraction = progressFraction,
        downloadState = downloadState,
    )
}

/** The audio pills — the player stacks' SUB/DUB/HSUB parse, scanlator-only
 * (the reconstructed scanlator already carries the library's aggregates). */
private fun parseAudioLabels(scanlator: String?): List<String> {
    val haystack = (scanlator ?: "").uppercase()
    val hasHsub = haystack.contains("HSUB") || haystack.contains("HARDSUB")
    val hasSub = haystack.contains("SUB") && !hasHsub
    val hasDub = haystack.contains("DUB") && !hasHsub
    return buildList {
        if (hasSub) add("SUB")
        if (hasDub) add("DUB")
        if (hasHsub) add("HSUB")
    }
}

/** "MMM d, yyyy" — the player pages' date format. */
private fun formatDate(epochMillis: Long): String {
    if (epochMillis <= 0) return ""
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(epochMillis))
}

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 110 (D-725): the local card/switch/segmented shapes are GONE — the
//  shared kit (EpisodeListSettingsWidgets.kt) owns them now; this screen and
//  the details screen render ONE anatomy.
// ════════════════════════════════════════════════════════════════════════════
