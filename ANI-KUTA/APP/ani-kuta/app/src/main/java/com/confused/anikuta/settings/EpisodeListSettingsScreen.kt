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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.confused.anikuta.R
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.datacache.CachedEpisodeMetadata
import com.confused.anikuta.core.datacache.DataCacheRepository
import com.confused.anikuta.core.datacache.EpisodeAudioAggregates
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.metadata.EpisodeMetadata
import com.confused.anikuta.core.preferences.EpisodeListPreferences
import com.confused.anikuta.feature.animedetails.EpisodeDownloadState
import com.confused.anikuta.feature.animedetails.EpisodeListDisplayStyle
import com.confused.anikuta.feature.animedetails.EpisodeListEntry
import com.confused.anikuta.feature.animedetails.EpisodeListRowStyle
import com.confused.anikuta.settings.search.SettingsHighlightTarget
import com.confused.anikuta.settings.search.rememberSettingsAnchorScroll
import eu.kanade.tachiyomi.animesource.model.SEpisode
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * D-554 → D-555 → D-556: the episode-list appearance page — reached from
 * Appearance → "Episode list".
 *
 * # The pattern (the D-477/D-481/D-525 doctrine, applied to lists)
 *
 * The same shape as the notification-poster page: a **live preview** region
 * at the top and the options in a LazyColumn below it (the D-525 single 8dp
 * gutter). The preview is NOT a mock-up: it renders the SAME
 * [EpisodeListEntry] dispatcher the details page draws, fed from the SAME
 * [EpisodeListPreferences] keys this screen writes (what you tune is exactly
 * what you'll get — D-481 honored literally). One source of truth, zero
 * drift: every flip here re-shapes the preview above AND the real list on
 * the details screen.
 *
 * # D-556: the preview shows YOUR library (the v1.1.29 device round)
 *
 * The user: "I was hoping for the live previews to show actual live previews
 * from my library … it will pick any random series … should have its episode
 * list loaded … with images … proper titles, proper descriptions, and other
 * details like the available audio versions marked on them, the release date
 * marked on them. If none of that is available … demo data."
 *
 * So the preview's content is loaded on entry ([loadLibraryPreviewEpisodes]):
 * a RANDOM qualifying library series — ≥2 cached episodes, thumbnails
 * preferred — reconstructed EXACTLY like the details screen's cache-restore
 * path (same SEpisode fields, same EpisodeMetadata fields), so titles,
 * descriptions, air dates and thumbnails are the app's real data, and the
 * audio pills come from the app's own per-series audio aggregates (no
 * fabrication: a series that only knows SUB shows SUB). If the library has
 * no qualifying series (fresh install), the demo samples render instead —
 * two episodes with real dates, synopses and the SUB/DUB/HSUB vocabulary.
 *
 * # D-556: the demo thumbnails are DRAWABLE RESOURCES (the image bug's root)
 *
 * The D-555 preview thumbnails STILL rendered empty on device. Root cause,
 * proven this round by unzipping coil-core-android-3.0.4.aar: **Coil 3 has
 * NO DataUriFetcher** — its fetcher registry is Asset/Bitmap/ByteArray/
 * ByteBuffer/ContentUri/Drawable/FileUri/JarFile/ResourceUri. The D-554
 * bare-base64 constant AND the D-555 `data:image/jpeg;base64,` scheme could
 * therefore NEVER match a fetcher — AsyncImage silently drew nothing and the
 * user saw the null-thumbnail number discs. The fix removes the data-URI
 * layer entirely: the two stills ship as real JPEG drawable resources
 * (ep_preview_still_1/2.jpg) referenced through `android.resource://`
 * URIs — ResourceUriFetcher's bread and butter, offline, tiny, and they
 * render with the SAME AsyncImage path the real network thumbnails use.
 *
 * # D-556: the preview is INTERACTIVE
 *
 * The user: "I am able to swipe right or left on the live previews, but
 * apparently their state never changes … I do want the option to manually
 * change it" and "clicking the download button … its state should change to
 * another view … cycle between all the possible states". So the two preview
 * slots carry LIVE local state: slot 1 boots fresh (40% watch-progress bar)
 * and slot 2 boots watched (the dim/grayscale treatment) — the default
 * experience the user described — and swipe-to-toggle (long-press on GRID)
 * flips the watched state, while every tap on the download control/badge
 * advances a state machine through ALL EIGHT download states (nothing →
 * resolving → queued → downloading → paused → downloading → error →
 * retrying → downloaded → nothing). The rows are still not playable — these
 * are appearance demos, not real episodes.
 *
 * # D-556: the preview COLLAPSES as you scroll (the second episode stays)
 *
 * The user: "if the user scrolls the bottom section, then the top live
 * preview section will scroll along with it … only this much that the first
 * episode list is hidden, and the second one will remain there and will
 * always be shown properly. When the user scrolls to the very top again,
 * then both of them will start to show up again." The collapse fraction
 * tracks the options list's scroll (GRID is exempt — "already compressed
 * enough"): the preview's content slides up under a clip so the first
 * episode exits and the second pins at the top; scrolling back restores
 * both. Layout switches animate smoothly ([animateContentSize]) so the
 * options below glide instead of snapping ("the layout section should move
 * down slowly").
 *
 * # D-556: the preview COLLAPSES as you scroll (the second episode stays)
 *
 * The user: "if the user scrolls the bottom section, then the top live
 * preview section will scroll along with it … only this much that the first
 * episode list is hidden, and the second one will remain there and will
 * always be shown properly. When the user scrolls to the very top again,
 * then both of them will start to show up again." Layout switches animate
 * smoothly ([animateContentSize]) so the options below glide instead of
 * snapping ("the layout section should move down slowly").
 *
 * # D-557: the collapse became a TWO-PHASE SNAP … and D-558 made it a
 * PRIORITY SCROLL
 *
 * The v1.1.30 device round: "the very first scroll should not scroll the
 * bottom section … midway it will automatically snap … And the bottom scroll
 * will happen afterwards." The v1.1.31 round kept the snap but exposed two
 * blind spots — fast flings slipped past the drag-only accumulator, and the
 * up-direction consumed PRE-scroll so the preview expanded while the list
 * was still scrolled down. The D-558 connection is SPLIT BY DIRECTION:
 * DOWN-while-open consumes EVERYTHING until the preview snaps collapsed
 * (then releases the same gesture into the list, and a consumed fling hands
 * its momentum to the list through a spline-decay scroll); UP never consumes
 * in pre-scroll — the list scrolls to the very top FIRST and only the
 * post-scroll leftover (or the finished fling's leftover) expands the
 * preview. Deterministic order BY CONSTRUCTION, however fast the finger is.
 * GRID is exempt; a layout switch always re-opens.
 *
 * # D-557: the download demo became a LIVE cycle
 *
 * The Downloading step animates +10% per second and auto-advances to
 * Downloaded at 100% ("it will move to the next state automatically without
 * me even pressing"); a tap at any point advances immediately and discards
 * the progress. EVERY state is tappable via the entry's previewTapAll (the
 * production widgets keep their visuals; the old dead Resolving spinner was
 * also fixed at the source — it cancels now, CORE_RULES §23). The swipe
 * toggle reads the watched map at execution time (the stale pointerInput
 * closure that killed the second swipe is fixed in the gesture itself).
 *
 * NOT part of this page (deliberately — the surfaces coexist): sort /
 * filter / grouping live in the list-settings SHEET on the details page
 * (list SHAPING); this page is list APPEARANCE only. The D-555 "More"
 * pointer card is gone per the device round ("most definitely not needed").
 */
@Composable
fun EpisodeListSettingsScreen(
    onBack: () -> Unit,
    /** D-558: the search-landing anchor (see SettingsSearchNavigator). */
    highlightAnchor: String? = null,
    episodeListPrefs: EpisodeListPreferences = koinInject(),
    contentRepository: ContentRepository = koinInject(),
    dataCacheRepository: DataCacheRepository = koinInject(),
) {
    // ── ROUND 111 (D-729): the Elements grid's per-row button count — the
    //    heading-tap testing aid ("when I click on the Elements heading …
    //    it will switch the grid layout to Three buttons per row. So make
    //    sure to give this functionality so I can test out how the things
    //    will overall look like"). Session-local (survives rotation, never
    //    a pref — an experiment knob, not a setting). ──
    var elementsThreePerRow by rememberSaveable { mutableStateOf(false) }

    // ── The reactive reads — the SAME prefs the details screen collects.
    // Every write below updates the pref; `changes` re-emits; the preview
    // AND the details list re-shape from the same emission (no local
    // mirror state, the D-481 drift killer).
    val rowStyleKey by episodeListPrefs.rowStyle.changes.collectAsState(
        initial = episodeListPrefs.rowStyle.get(),
    )
    val showSynopsis by episodeListPrefs.showSynopsis.changes.collectAsState(
        initial = episodeListPrefs.showSynopsis.get(),
    )
    val showDatePill by episodeListPrefs.showDatePill.changes.collectAsState(
        initial = episodeListPrefs.showDatePill.get(),
    )
    val showAudioPills by episodeListPrefs.showAudioPills.changes.collectAsState(
        initial = episodeListPrefs.showAudioPills.get(),
    )
    val showWatchProgress by episodeListPrefs.showWatchProgress.changes.collectAsState(
        initial = episodeListPrefs.showWatchProgress.get(),
    )
    val dimWatched by episodeListPrefs.dimWatched.changes.collectAsState(
        initial = episodeListPrefs.dimWatched.get(),
    )
    val showDownloadControl by episodeListPrefs.showDownloadControl.changes.collectAsState(
        initial = episodeListPrefs.showDownloadControl.get(),
    )
    // ── D-558: the CINEMA customizability knobs (the same reactive reads).
    val cinemaNumberCorner by episodeListPrefs.cinemaNumberCorner.changes.collectAsState(
        initial = episodeListPrefs.cinemaNumberCorner.get(),
    )
    val cinemaNumberStyle by episodeListPrefs.cinemaNumberStyle.changes.collectAsState(
        initial = episodeListPrefs.cinemaNumberStyle.get(),
    )
    val cinemaWatchedCheck by episodeListPrefs.cinemaWatchedCheck.changes.collectAsState(
        initial = episodeListPrefs.cinemaWatchedCheck.get(),
    )
    // ── ROUND 108 (D-713): the GRID's title-line mode (the same reactive
    // reads — the new knob, resolved through the lenient fromKey).
    val gridTitleModeKey by episodeListPrefs.gridTitleMode.changes.collectAsState(
        initial = episodeListPrefs.gridTitleMode.get(),
    )
    val gridTitleMode = remember(gridTitleModeKey) {
        com.confused.anikuta.core.common.GridTitleMode.fromKey(gridTitleModeKey)
    }
    // ROUND 110 (D-723): the GRID's number-label placement — the same
    // reactive read, resolved through the lenient fromKey.
    val gridNumberPositionKey by episodeListPrefs.gridNumberPosition.changes.collectAsState(
        initial = episodeListPrefs.gridNumberPosition.get(),
    )
    val gridNumberPosition = remember(gridNumberPositionKey) {
        com.confused.anikuta.core.common.GridNumberPosition.fromKey(gridNumberPositionKey)
    }
    // D-529 lesson: seed the toggle through the lenient fromKey so the
    // highlighted segment is ALWAYS the style the renderer will draw.
    val selectedStyle = EpisodeListRowStyle.fromKey(rowStyleKey)

    val style = EpisodeListDisplayStyle(
        rowStyle = selectedStyle,
        showSynopsis = showSynopsis,
        showDatePill = showDatePill,
        showAudioPills = showAudioPills,
        showWatchProgress = showWatchProgress,
        dimWatched = dimWatched,
        showDownloadControl = showDownloadControl,
        cinemaNumberAtTopStart = cinemaNumberCorner.trim().equals("LEFT", ignoreCase = true),
        cinemaNumberFrosted = cinemaNumberStyle.trim().equals("FROSTED", ignoreCase = true),
        cinemaWatchedCheckBadge = cinemaWatchedCheck,
        gridTitleMode = gridTitleMode,
        gridNumberPosition = gridNumberPosition,
    )

    // ── D-556: the preview's content — demo samples first (instant paint),
    // then a RANDOM qualifying library series replaces them when one exists.
    val context = LocalContext.current
    var previewItems by remember {
        mutableStateOf(demoPreviewItems(context.packageName))
    }
    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            loadLibraryPreviewItems(contentRepository, dataCacheRepository)
        }
        if (loaded != null) previewItems = loaded
    }

    // ── D-556: the LIVE preview state. Watched defaults: slot 1 fresh,
    // slot 2 watched (the user's spec) — a swipe (or GRID long-press)
    // overrides. The download state advances through the FULL 8-state
    // machine on every tap of the control/badge.
    val watchedOverride = remember { mutableStateMapOf<String, Boolean>() }
    val cycleIndex = remember { mutableStateMapOf<String, Int>() }

    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

    // ═══ ROUND 108 (D-713): THE SHARED PREVIEW-COLLAPSE SCROLL ═══
    // The D-557/D-558 machinery moved into ONE shared controller
    // (PreviewCollapseScroll.kt) that BOTH episode-list settings screens
    // mount — the v1.1.64 device round exposed two defects in the inline
    // copies, both fixed at the source:
    //  • THE ENTRY SCROLL — "whenever I enter the … episode list settings,
    //    then it should always be scrolled to the very top … apparently
    //    sometimes … they were scrolled to the very bottom. Like it
    //    remembered the previous situations." The rememberSaveable-backed
    //    list state restored the old offset on re-entry; the entry reset
    //    now forces item 0 + an open preview (skipped only when a
    //    settings-search anchor is steering).
    //  • THE FLICK CHOREOGRAPHY — "if I flicked my finger, then it would not
    //    scroll automatically to the bottom… it should smoothly go on and
    //    scroll the top section first, and then by scrolling it one of the
    //    episodes will hide, and it will hide properly and smoothly as such.
    //    And after it has hidden, then it will not allow the user to scroll
    //    for a few bit for a few time, and after that it will automatically
    //    start scrolling the bottom section as it is. And it will depend on
    //    how fast the user scrolled." The flick now runs the ordered
    //    sequence — smooth hide → the lock beat → the momentum handoff
    //    through the canonical scroll-scope decay (the old
    //    dispatchRawDelta handoff never moved on device; the old -1000px/s
    //    threshold let weaker flicks scroll the list under an OPEN preview).
    // The drag semantics (the two-phase snap) are unchanged; see the
    // controller's file header for the full contract.
    val previewCollapse = rememberPreviewCollapseScroll(
        listState = lazyListState,
        enabled = selectedStyle != EpisodeListRowStyle.GRID,
    )
    PreviewCollapseEntryReset(previewCollapse, hasAnchor = highlightAnchor != null)
    // A layout switch always re-opens the preview — a stale collapsed state
    // under GRID (whose connection never engages) would clip it forever.
    LaunchedEffect(selectedStyle) { previewCollapse.reopen() }
    // The clip+shift layout's hide distance — the first episode's measured
    // height + the row gap, scaled by the collapse fraction.
    val hidePx = previewCollapse.hidePx

    // ── D-558: the search-landing scroll (the anchor map is this screen's
    // half of the search contract: 0 layout · 1 cinema · 2 grid · 3
    // elements). ROUND 110 (D-725): the round-108 off-by-one FIXED — the
    // Grid card joined the list at item 2 but el_* still mapped there;
    // the elements now map to 3 and the grid's own anchors to 2.
    rememberSettingsAnchorScroll(
        anchor = highlightAnchor,
        anchorIndexFor = { anchor ->
            when (anchor) {
                "episode_list", "layout" -> 0
                "cinema_corner", "cinema_style", "cinema_check" -> 1
                "grid_title", "grid_number" -> 2
                "el_synopsis", "el_date", "el_audio", "el_progress", "el_dim", "el_download" -> 3
                else -> null
            }
        },
        listState = lazyListState,
    )

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                // ROUND 105 (WS-C): retitled to match the Appearance entry
                // ("name the first one… as details page").
                title = "Details page",
                collapsed = collapsed,
                onBack = onBack,
            )

            // ── THE LIVE PREVIEW — real library data (or the demo samples).
            // The clip+shift layout collapses it as the options scroll; the
            // animateContentSize makes LAYOUT SWITCHES glide ("the layout
            // section should move down slowly").
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(
                                    animationSpec = tween(
                                        durationMillis = 360,
                                        easing = FastOutSlowInEasing,
                                    ),
                                ),
                        ) {
                            if (selectedStyle == EpisodeListRowStyle.GRID) {
                                // The wall pairs its cells two-across — the
                                // preview mirrors the real list's shape.
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        PreviewEpisodeSlot(
                                            item = previewItems[0],
                                            slot = 0,
                                            style = style,
                                            watchedOverride = watchedOverride,
                                            cycleIndex = cycleIndex,
                                        )
                                    }
                                    Box(modifier = Modifier.weight(1f)) {
                                        PreviewEpisodeSlot(
                                            item = previewItems[1],
                                            slot = 1,
                                            style = style,
                                            watchedOverride = watchedOverride,
                                            cycleIndex = cycleIndex,
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Box(
                                        modifier = Modifier.onSizeChanged { size ->
                                            previewCollapse.firstRowHeightPx = size.height.toFloat()
                                        },
                                    ) {
                                        PreviewEpisodeSlot(
                                            item = previewItems[0],
                                            slot = 0,
                                            style = style,
                                            watchedOverride = watchedOverride,
                                            cycleIndex = cycleIndex,
                                        )
                                    }
                                    PreviewEpisodeSlot(
                                        item = previewItems[1],
                                        slot = 1,
                                        style = style,
                                        watchedOverride = watchedOverride,
                                        cycleIndex = cycleIndex,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── THE SCROLLABLE REGION — the options. Same single-gutter
            // contentPadding as the poster page (the D-525 8dp rule). The
            // nestedScroll connection intercepts the collapse-phase drags
            // BEFORE the list sees them (the D-557 two-phase snap).
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(previewCollapse.connection),
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    // ── the layout — the FOUR-WAY selector. D-556: no
                    // description lines (the device round: the "four
                    // completely different designs" line and the per-option
                    // identity line are "not needed" / "not good") — the
                    // LIVE PREVIEW is the description; the animated pill
                    // (SegmentedToggle) is the selector.
                    item {
                        EpisodeSettingsCard(label = "Layout") {
                            SettingsHighlightTarget(anchorId = "layout", activeAnchor = highlightAnchor) {
                            // ROUND 110 (D-725): the duplicated inner heading is
                            // GONE (the player screen's round-105 fix, now both
                            // screens' rule — the card label IS the heading); the
                            // LIVE PREVIEW is the description, the animated pill
                            // (SegmentedToggle) is the selector.
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                SegmentedToggle(
                                    options = listOf(
                                        "Classic",
                                        "Grid",
                                        "Timeline",
                                        "Cinema",
                                    ),
                                    selectedIndex = selectedStyle.ordinal,
                                    onSelect = { idx ->
                                        episodeListPrefs.rowStyle.set(
                                            EpisodeListRowStyle.entries[idx].name,
                                        )
                                    },
                                )
                            }
                            }
                        }
                    }

                    // ── the CINEMA customizability (D-558) — a dedicated
                    // section that exists ONLY while the Cinema layout is
                    // selected: "This section will be shown below the layout
                    // section and above the element section … this will only
                    // show for the cinema section … if we switch to any other
                    // section, then it will smoothly, with beautiful clean
                    // animations, disappear." The option rows are single-row
                    // segmented toggles in the Layout section's format — no
                    // description lines (the user's explicit spec).
                    item {
                        Column {
                        AnimatedVisibility(
                            visible = selectedStyle == EpisodeListRowStyle.CINEMA,
                            enter = fadeIn(animationSpec = tween(300)) +
                                expandVertically(
                                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                                ),
                            exit = fadeOut(animationSpec = tween(240)) +
                                shrinkVertically(
                                    animationSpec = tween(240, easing = FastOutSlowInEasing),
                                ),
                        ) {
                            EpisodeSettingsCard(label = "Cinema") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    // ROUND 110 (D-725): the duplicated inner
                                    // heading is gone; the rows render through
                                    // the shared SegmentedRow (the same widget
                                    // the player screen uses — ONE anatomy).
                                    SettingsHighlightTarget(anchorId = "cinema_corner", activeAnchor = highlightAnchor) {
                                        SegmentedRow(
                                            title = "Number position",
                                            options = listOf("Top left", "Top right"),
                                            selectedIndex = if (
                                                cinemaNumberCorner.trim().equals("LEFT", ignoreCase = true)
                                            ) 0 else 1,
                                            onSelect = { idx ->
                                                episodeListPrefs.cinemaNumberCorner.set(
                                                    if (idx == 0) "LEFT" else "RIGHT",
                                                )
                                            },
                                        )
                                    }
                                    SettingsHighlightTarget(anchorId = "cinema_style", activeAnchor = highlightAnchor) {
                                        SegmentedRow(
                                            title = "Number style",
                                            options = listOf("Solid", "Frosted"),
                                            selectedIndex = if (
                                                cinemaNumberStyle.trim().equals("FROSTED", ignoreCase = true)
                                            ) 1 else 0,
                                            onSelect = { idx ->
                                                episodeListPrefs.cinemaNumberStyle.set(
                                                    if (idx == 1) "FROSTED" else "SOLID",
                                                )
                                            },
                                        )
                                    }
                                    SettingsHighlightTarget(anchorId = "cinema_check", activeAnchor = highlightAnchor) {
                                        SwitchRow(
                                            title = "Watched check mark",
                                            checked = cinemaWatchedCheck,
                                            onChecked = { episodeListPrefs.cinemaWatchedCheck.set(it) },
                                        )
                                    }
                                }
                            }
                        }
                        }
                    }

                    // ── ROUND 108 (D-713): the GRID customizability — the
                    //    Cinema card's pattern, ported: a dedicated section
                    //    that exists ONLY while the Grid layout is selected,
                    //    with the same smooth appear/disappear. ROUND 110
                    //    (D-723/D-725): the rows render through the shared
                    //    kit, the duplicated inner heading is gone, and the
                    //    "Episode number" placement row joins (the new
                    //    knob). ──
                    item {
                        Column {
                        AnimatedVisibility(
                            visible = selectedStyle == EpisodeListRowStyle.GRID,
                            enter = fadeIn(animationSpec = tween(300)) +
                                expandVertically(
                                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                                ),
                            exit = fadeOut(animationSpec = tween(240)) +
                                shrinkVertically(
                                    animationSpec = tween(240, easing = FastOutSlowInEasing),
                                ),
                        ) {
                            EpisodeSettingsCard(label = "Grid") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    SettingsHighlightTarget(anchorId = "grid_title", activeAnchor = highlightAnchor) {
                                        SegmentedRow(
                                            title = "Episode titles",
                                            options = listOf("Off", "1 line", "Full"),
                                            selectedIndex = when (gridTitleMode) {
                                                com.confused.anikuta.core.common.GridTitleMode.OFF -> 0
                                                com.confused.anikuta.core.common.GridTitleMode.ONE_LINE -> 1
                                                com.confused.anikuta.core.common.GridTitleMode.TWO_LINES -> 2
                                            },
                                            onSelect = { idx ->
                                                episodeListPrefs.gridTitleMode.set(
                                                    when (idx) {
                                                        0 -> "OFF"
                                                        1 -> "ONE"
                                                        else -> "TWO"
                                                    },
                                                )
                                            },
                                            description = "Only real English titles render the line — " +
                                                "a bare episode number never does",
                                        )
                                    }
                                    SettingsHighlightTarget(anchorId = "grid_number", activeAnchor = highlightAnchor) {
                                        SegmentedRow(
                                            title = "Episode number",
                                            options = listOf("Below thumbnail", "Beside title"),
                                            selectedIndex = if (gridNumberPosition ==
                                                com.confused.anikuta.core.common.GridNumberPosition.BESIDE_DETAILS
                                            ) 1 else 0,
                                            onSelect = { idx ->
                                                episodeListPrefs.gridNumberPosition.set(
                                                    if (idx == 1) "BESIDE_DETAILS" else "UNDER_THUMB",
                                                )
                                            },
                                            description = "Where the EP label sits in each cell",
                                        )
                                    }
                                }
                            }
                        }
                        }
                    }

                    // ── the elements — ROUND 111 (D-729): THE GRID OF
                    //    BUTTONS ("a grid layout of buttons which I can click
                    //    and turn to toggle them on or to toggle them off …
                    //    two options per row"), NO descriptions, the clean
                    //    color + check animation, and the card-heading tap
                    //    flipping 2 ↔ 3 buttons per row (the user's testing
                    //    aid for the layout experiment). The search anchors
                    //    ride the buttons themselves now. ──
                    item {
                        EpisodeSettingsCard(
                            label = "Elements",
                            onLabelClick = { elementsThreePerRow = !elementsThreePerRow },
                        ) {
                            ElementToggleGrid(
                                entries = listOf(
                                    ElementToggleEntry(
                                        title = "Synopsis",
                                        checked = showSynopsis,
                                        onToggle = { episodeListPrefs.showSynopsis.set(!showSynopsis) },
                                        anchorId = "el_synopsis",
                                    ),
                                    ElementToggleEntry(
                                        title = "Release date",
                                        checked = showDatePill,
                                        onToggle = { episodeListPrefs.showDatePill.set(!showDatePill) },
                                        anchorId = "el_date",
                                    ),
                                    ElementToggleEntry(
                                        title = "Audio pills",
                                        checked = showAudioPills,
                                        onToggle = { episodeListPrefs.showAudioPills.set(!showAudioPills) },
                                        anchorId = "el_audio",
                                    ),
                                    ElementToggleEntry(
                                        title = "Watch progress",
                                        checked = showWatchProgress,
                                        onToggle = { episodeListPrefs.showWatchProgress.set(!showWatchProgress) },
                                        anchorId = "el_progress",
                                    ),
                                    ElementToggleEntry(
                                        title = "Dim watched",
                                        checked = dimWatched,
                                        onToggle = { episodeListPrefs.dimWatched.set(!dimWatched) },
                                        anchorId = "el_dim",
                                    ),
                                    ElementToggleEntry(
                                        title = "Download buttons",
                                        checked = showDownloadControl,
                                        onToggle = { episodeListPrefs.showDownloadControl.set(!showDownloadControl) },
                                        anchorId = "el_download",
                                    ),
                                ),
                                columns = if (elementsThreePerRow) 3 else 2,
                                highlightAnchor = highlightAnchor,
                            )
                        }
                    }

                    // ── the footer hint (D-558) — the user's ask: "at the
                    // very bottom of this screen … a dedicated section … it
                    // should say that can be further customized by clicking
                    // episodes on the page." A quiet centered caption — the
                    // interactive preview slots (swipe-to-toggle, the
                    // download tap-cycle) ARE the click-customization it
                    // points at.
                    item {
                        Caption(
                            text = "Can be further customized by clicking episodes on the page",
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                        )
                    }
                }
                ScrollBlurOverlay(
                    scrollOffset = { lazyListState.firstVisibleItemScrollOffset.toFloat() },
                    backgroundColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// D-556: the preview's content plumbing — real library data with a demo
// fallback, plus the LIVE slot state (watched toggle + download cycling).
// ─────────────────────────────────────────────────────────────────────────────

/** One preview slot's content: a REAL episode (library cache) or a demo one. */
// ROUND 103 (WS-4): internal — the PLAYER settings screen (same :app
// module) reuses this loader stack for its own actual-data preview.
internal data class PreviewItem(
    val episode: SEpisode,
    val metadata: EpisodeMetadata?,
    val fallbackCoverUrl: String?,
)

/**
 * The 8-state download machine, in demonstration order: nothing → resolving
 * → queued → DOWNLOADING (LIVE) → paused → error → retrying → downloaded →
 * (wraps to nothing). D-557 refinement of the user's spec: the Downloading
 * step is a LIVE animation — the progress jumps +10% every second, and on
 * reaching 100% it AUTO-ADVANCES to Downloaded "without me even pressing".
 * Tapping at ANY point advances to the next step immediately ("if I click
 * on it before it finishes, then it will go to the next one … and the
 * progress will disappear automatically") — the tap flows through
 * [EpisodeListEntry]'s previewTapAll so EVERY state (including the old dead
 * spinner) responds on the production widgets' own visuals.
 */
private val downloadStateCycle: List<EpisodeDownloadState> = listOf(
    EpisodeDownloadState.NotDownloaded,
    EpisodeDownloadState.Resolving,
    EpisodeDownloadState.Queued,
    EpisodeDownloadState.Downloading(0),
    EpisodeDownloadState.Paused,
    EpisodeDownloadState.Error("Preview failure (simulated)"),
    EpisodeDownloadState.Retrying,
    EpisodeDownloadState.Downloaded,
)

/**
 * ONE preview slot rendered through the REAL dispatcher (the D-481
 * doctrine). Slot 0 boots fresh with the 40% progress fixture; slot 1 boots
 * watched (the dim/grayscale treatment). Swipe (long-press on GRID) flips
 * the watched state; every download tap advances [downloadStateCycle].
 */
@Composable
private fun PreviewEpisodeSlot(
    item: PreviewItem,
    slot: Int,
    style: EpisodeListDisplayStyle,
    watchedOverride: MutableMap<String, Boolean>,
    cycleIndex: MutableMap<String, Int>,
) {
    val defaultWatched = slot == 1
    val url = item.episode.url
    val isWatched = watchedOverride[url] ?: defaultWatched
    val step = cycleIndex[url] ?: 0
    val baseState = downloadStateCycle[step % downloadStateCycle.size]
    // D-557: the LIVE Downloading demo — +10% per second; at 100% it
    // auto-advances to Downloaded without a tap. The effect is keyed on the
    // step: a tap that leaves Downloading cancels it mid-flight (the
    // "progress will disappear automatically" behavior).
    var liveProgress by remember(url) { mutableStateOf(0) }
    val isDownloading = baseState is EpisodeDownloadState.Downloading
    LaunchedEffect(url, step) {
        if (isDownloading) {
            liveProgress = 0
            while (liveProgress < 100) {
                kotlinx.coroutines.delay(1000)
                liveProgress = (liveProgress + 10).coerceAtMost(100)
            }
            cycleIndex[url] = downloadStateCycle
                .indexOf(EpisodeDownloadState.Downloaded)
                .coerceAtLeast(0)
        }
    }
    val state = if (isDownloading) EpisodeDownloadState.Downloading(liveProgress) else baseState
    fun advance() {
        cycleIndex[url] = ((cycleIndex[url] ?: 0) + 1) % downloadStateCycle.size
    }
    EpisodeListEntry(
        episode = item.episode,
        metadata = item.metadata,
        onClick = { /* the preview is inert — no playback from settings */ },
        downloadState = state,
        fallbackCoverUrl = item.fallbackCoverUrl,
        // The cycling taps: the previewTapAll makes the WHOLE control/badge
        // one tap target — every state (spinner included) advances. The
        // per-action callbacks stay wired as a fallback.
        onDownload = { advance() },
        onPause = { advance() },
        onResume = { advance() },
        onCancel = { advance() },
        onRetry = { advance() },
        onDelete = {},
        onPlayDownloaded = { advance() },
        isWatched = isWatched,
        progressFraction = if (slot == 0) 0.4f else 0f,
        // D-557: the value-INDEPENDENT toggle — the swipe gesture's stale
        // pointerInput closure held the old `isWatched`, so the SECOND swipe
        // wrote the same value back ("it was still not getting marked").
        // Reading the map at execution time is always correct (and the
        // gesture itself now reads the freshest callback too).
        onToggleWatched = {
            watchedOverride[url] = !(watchedOverride[url] ?: defaultWatched)
        },
        style = style,
        previewTapAll = { advance() },
    )
}

/**
 * The demo fallback (fresh installs / no qualifying library series): TWO
 * episodes with real dates, full synopses, and the SUB/DUB/HSUB vocabulary,
 * riding two REAL anime stills shipped as drawable resources through
 * `android.resource://` URIs (see the screen KDoc for why data URIs can
 * never work on Coil 3 — no DataUriFetcher).
 */
internal fun demoPreviewItems(packageName: String): List<PreviewItem> {
    val thumb1 = "android.resource://$packageName/${R.drawable.ep_preview_still_1}"
    val thumb2 = "android.resource://$packageName/${R.drawable.ep_preview_still_2}"
    val fresh = SEpisode.create().apply {
        url = "preview://sample-1"
        name = "The Journey Begins"
        summary = "A quiet morning is interrupted when the first gate opens " +
            "over the harbor, and everything the crew trained for finally matters."
        scanlator = "SUB DUB" // the demo's honest audio vocabulary → SUB · DUB pills
        date_upload = 1735689600000L // Jan 1, 2025 — a stable sample date
        episode_number = 1f
        preview_url = thumb1
    }
    val watched = SEpisode.create().apply {
        url = "preview://sample-2"
        name = "Signal in the Rain"
        summary = "The lantern district goes dark one street at a time, and " +
            "the only witness is a courier who was never supposed to be there."
        scanlator = "HSUB"
        date_upload = 1736294400000L // Jan 8, 2025
        episode_number = 2f
        preview_url = thumb2
    }
    return listOf(
        PreviewItem(episode = fresh, metadata = null, fallbackCoverUrl = null),
        PreviewItem(episode = watched, metadata = null, fallbackCoverUrl = null),
    )
}

/**
 * The REAL preview content: a RANDOM qualifying library series — ≥2 cached
 * episodes, thumbnails preferred, never fabricated. Reads the SAME stores
 * the library and details screens read (SQLDelight, synchronous — call on
 * Dispatchers.IO). Returns null when nothing qualifies → the caller keeps
 * the demo samples.
 *
 * The reconstruction mirrors DetailsViewModel.fetchEpisodes' cache-restore
 * path FIELD FOR FIELD (url/number/name/date/scanlator/summary/preview_url
 * + the full D-190 EpisodeMetadata), so the preview rows are byte-for-byte
 * the rows the details screen would draw for that series.
 */
internal fun loadLibraryPreviewItems(
    contentRepository: ContentRepository,
    dataCacheRepository: DataCacheRepository,
): List<PreviewItem>? {
    // ONE batch query for the audio aggregates + the covers map + the
    // library items (newest first — the same reads LibraryViewModel does).
    val aggregates = dataCacheRepository.getAllEpisodeAudioAggregates()
    val detailsById = contentRepository.getAllContentDetailsMap()
    val candidates = contentRepository.getAllLibraryItems()
        .map { it.mainId }
        .distinct()
        // A series qualifies only when its episode list is actually LOADED
        // (the user's spec) — the data-cache table is exactly that evidence.
        .filter { (aggregates[it]?.releasedCount ?: 0) >= 2 }
        // "it will pick any random series" — the order is shuffled per visit.
        .shuffled()

    var firstQualified: List<PreviewItem>? = null
    for (mainId in candidates.take(8)) {
        val episodes = dataCacheRepository.getEpisodeMetadata(mainId)
            .filter { it.episodeNumber > 0f }
            .sortedBy { it.episodeNumber }
        if (episodes.size < 2) continue
        val picked = pickPreviewEpisodes(episodes)
        val cover = detailsById[mainId]?.let { it.dataCoverUrl ?: it.extThumbnailUrl }
        val items = picked.mapIndexed { index, meta ->
            PreviewItem(
                episode = reconstructEpisode(meta, mainId, aggregates[mainId]),
                metadata = reconstructMetadata(meta, mainId),
                // The series cover backs any thumbnail-less episode — REAL
                // imagery even when the provider gave no per-episode stills.
                fallbackCoverUrl = cover,
            )
        }
        // Prefer a candidate whose two preview episodes carry real imagery
        // (episode stills or at least the series cover); a candidate without
        // either is kept as the last resort over the demo data (it still
        // shows the user's REAL titles/dates/audio).
        val hasImagery = items.any { !it.episode.preview_url.isNullOrBlank() || cover != null }
        if (hasImagery) return items
        if (firstQualified == null) firstQualified = items
    }
    return firstQualified
}

/**
 * The two preview episodes: the lowest-numbered ones that carry a thumbnail
 * (thumbnails are the preview's point); when only one episode has a still,
 * pair it with the next-lowest numbered episode; when none do, episodes 1+2
 * (the cover fallback carries the imagery).
 */
internal fun pickPreviewEpisodes(
    episodes: List<CachedEpisodeMetadata>,
): List<CachedEpisodeMetadata> {
    val withThumb = episodes.filter { !it.thumbnailUrl.isNullOrBlank() }
    return when {
        withThumb.size >= 2 -> listOf(withThumb[0], withThumb[1])
        // The size>=2 guard on the pairing branch makes the function total
        // (the caller filters size<2 today, but this is unit-test surface).
        withThumb.size == 1 && episodes.size >= 2 -> listOf(
            withThumb[0],
            episodes.first { it !== withThumb[0] },
        )
        else -> episodes.take(2)
    }
}

/** The SEpisode reconstruction — the details screen's cache-restore, verbatim. */
private fun reconstructEpisode(
    meta: CachedEpisodeMetadata,
    mainId: String,
    aggregates: EpisodeAudioAggregates?,
): SEpisode = SEpisode.create().apply {
    url = meta.episodeUrl ?: "preview://$mainId/${meta.episodeNumber}"
    episode_number = meta.episodeNumber
    name = meta.sourceName ?: meta.title ?: "Episode ${meta.episodeNumber.toInt()}"
    date_upload = meta.airDate ?: 0L
    // Real scanlator when the cache has one; otherwise the app's OWN audio
    // aggregates speak (no fabrication — a series that only knows SUB shows
    // SUB). The tokens ride the same parseAudioAvailability path the
    // details rows use.
    scanlator = meta.scanlator?.takeIf { it.isNotBlank() }
        ?: aggregates?.let { audioScanlatorHint(it) }
    summary = meta.description
    preview_url = meta.thumbnailUrl
}

/** The EpisodeMetadata reconstruction — the D-190 full-field restore, verbatim. */
private fun reconstructMetadata(
    meta: CachedEpisodeMetadata,
    mainId: String,
): EpisodeMetadata = EpisodeMetadata(
    episodeKey = mainId + "|" + String.format("%05d", meta.episodeNumber.toInt()),
    number = meta.episodeNumber.toDouble(),
    title = meta.title,
    thumbnailUrl = meta.thumbnailUrl,
    airDate = meta.airDate,
    description = meta.description,
    isFiller = meta.isFiller,
    isRecap = meta.isRecap,
    titleJapanese = meta.titleJapanese,
    titleRomaji = meta.titleRomaji,
    runtime = meta.runtime,
    seasonNumber = meta.seasonNumber,
    episodeNumberInSeason = meta.episodeNumberInSeason,
    score = meta.score,
)

/**
 * The audio-aggregate → scanlator hint. ONLY used when the cached episode
 * has no scanlator of its own. The encoding respects the row's HSUB-distinct
 * parse: an HSUB presence collapses everything to HSUB (the same lossiness
 * the details page shows for real scanlator strings), otherwise SUB/DUB
 * tokens as the aggregates know them.
 */
internal fun audioScanlatorHint(agg: EpisodeAudioAggregates): String? = when {
    agg.hasHsub -> "HSUB"
    agg.hasSub && agg.hasDub -> "SUB DUB"
    agg.hasSub -> "SUB"
    agg.hasDub -> "DUB"
    else -> null
}

// Local card + row shapes — the shared kit (EpisodeListSettingsWidgets.kt)
// owns them now (ROUND 110 / D-725: this screen's two privates and the
// player screen's three all folded into ONE anatomy).
