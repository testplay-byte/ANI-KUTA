package com.confused.anikuta.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.rememberSplineBasedDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Velocity
import com.confused.anikuta.R
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.datacache.CachedEpisodeMetadata
import com.confused.anikuta.core.datacache.DataCacheRepository
import com.confused.anikuta.core.datacache.EpisodeAudioAggregates
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.metadata.EpisodeMetadata
import com.confused.anikuta.core.preferences.EpisodeListPreferences
import com.confused.anikuta.feature.animedetails.EpisodeDownloadState
import com.confused.anikuta.feature.animedetails.EpisodeListDisplayStyle
import com.confused.anikuta.feature.animedetails.EpisodeListEntry
import com.confused.anikuta.feature.animedetails.EpisodeListRowStyle
import com.confused.anikuta.settings.search.SettingsHighlightTarget
import com.confused.anikuta.settings.search.rememberSettingsAnchorScroll
import eu.kanade.tachiyomi.animesource.model.SEpisode
import kotlinx.coroutines.Job
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    // ── D-557 → D-558: the collapse became a PRIORITY SCROLL. The v1.1.31
    // device round kept the two-phase snap but exposed two blind spots:
    // "if I quickly swipe up to scroll, then the top live preview does not
    // scroll first. The bottom section scrolls" (fast flings slipped past
    // the drag-only accumulator), and "if I have scrolled to the very bottom
    // and then try to scroll up … the live preview starts to [expand]"
    // (expansion was a PRE-scroll consumer, so it stole deltas while the
    // list was still scrolled down).
    //
    // The v3 connection is SPLIT BY DIRECTION, which makes the order
    // deterministic BY CONSTRUCTION:
    //
    // - DOWN while open (drag OR fling): the collapse consumes EVERYTHING
    //   first — the list cannot move until the preview has snapped collapsed
    //   ("no matter what happens, the first of all thing will be that the
    //   live preview will move up"). After the snap the connection RELEASES
    //   the same gesture: the remaining drag deltas flow into the list
    //   ("and after that then the bottom section will begin to scroll
    //   over"). A consumed down-fling additionally HANDS ITS MOMENTUM to
    //   the list through a spline-decay scroll once the collapse settles —
    //   a fast swipe collapses AND keeps scrolling instead of dying at the
    //   snap.
    //
    // - UP while collapsed: the connection never consumes on the way up in
    //   PRE-scroll — the list always scrolls first; only the LEFTOVER of an
    //   up-drag (the list is at the very top) can expand the preview, with
    //   the same halfway snap. An up-FLING's leftover (the list finished its
    //   fling at the top) settles the expansion in onPostFling — "first of
    //   all the bottom section should scroll to the very top" before the
    //   preview opens.
    //
    // The halfway snap, the crossed latch (one flip per gesture), the
    // animated settles, the GRID exemption and the layout-switch re-open
    // carry over from the D-557 design unchanged.
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var firstRowHeightPx by remember { mutableStateOf(0) }
    val rowGapPx = with(density) { 8.dp.toPx() }
    val collapseDistancePx = (firstRowHeightPx + rowGapPx).coerceAtLeast(1f)
    val collapseProgress = remember { Animatable(0f) }
    val collapseCollapsed = remember { mutableStateOf(false) }
    val dragAccumulator = remember { mutableStateOf(0f) }
    val collapseDistanceState = remember { mutableStateOf(collapseDistancePx) }
    collapseDistanceState.value = collapseDistancePx
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var flingJob by remember { mutableStateOf<Job?>(null) }
    // The spline decay spec for the fling-momentum handoff — the same
    // physics curve the list's own flings ride.
    val flingDecay = rememberSplineBasedDecay<Float>()
    val nestedConnection = remember(selectedStyle) {
        object : NestedScrollConnection {
            // D-557: the crossed latch — a single continuous drag crosses the
            // halfway point EXACTLY ONCE (the D-556-style accumulate-reset
            // let a long drag cross twice: collapse → snap → animated-reopen
            // bounce within one gesture). While latched, further deltas are
            // consumed silently until the gesture ends (the 180ms settle
            // window clears the latch).
            private var crossedLatch = false

            private fun clearGesture() {
                crossedLatch = false
                dragAccumulator.value = 0f
            }

            // The settle window: after the last delta of a gesture, clear
            // the latch and settle the preview to its phase anchor — a drag
            // that ended before the halfway point eases BACK instead of
            // freezing midway.
            private fun settleLater() {
                settleJob?.cancel()
                settleJob = scope.launch {
                    kotlinx.coroutines.delay(180)
                    clearGesture()
                    collapseProgress.animateTo(
                        targetValue = if (collapseCollapsed.value) 1f else 0f,
                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                    )
                }
            }

            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // Programmatic scrolls (scrollToItem etc.) pass through.
                if (source == NestedScrollSource.SideEffect) return Offset.Zero
                if (selectedStyle == EpisodeListRowStyle.GRID) return Offset.Zero
                // DOWN while open: THE COLLAPSE PHASE. Consume everything —
                // the list does not move until the preview has collapsed
                // ("no matter what happens, the first of all thing will be
                // that the live preview will move up").
                if (available.y < 0 && !collapseCollapsed.value) {
                    settleJob?.cancel()
                    flingJob?.cancel()
                    if (!crossedLatch) {
                        dragAccumulator.value += kotlin.math.abs(available.y)
                        val halfway = collapseDistanceState.value / 2f
                        if (dragAccumulator.value >= halfway) {
                            // Snapped collapsed — settle there animated.
                            crossedLatch = true
                            collapseCollapsed.value = true
                            dragAccumulator.value = 0f
                            scope.launch {
                                collapseProgress.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                                )
                            }
                        } else {
                            val ratio = (dragAccumulator.value / halfway).coerceIn(0f, 1f)
                            scope.launch { collapseProgress.snapTo(0.45f * ratio) }
                        }
                    }
                    settleLater()
                    // After the snap the connection RELEASES the same gesture
                    // (the latch is crossed) — the remaining deltas flow into
                    // the list ("and after that then the bottom section will
                    // begin to scroll over").
                    return if (crossedLatch) Offset.Zero else Offset(0f, available.y)
                }
                // UP (and everything else): never consume in PRE-scroll —
                // the list scrolls FIRST; the preview only expands from the
                // POST-scroll leftover.
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.SideEffect) return Offset.Zero
                if (selectedStyle == EpisodeListRowStyle.GRID) return Offset.Zero
                // UP-leftover: the list is at the very top and still has up
                // delta left — THE ONLY DOOR to the expansion.
                if (available.y > 0 && collapseCollapsed.value) {
                    settleJob?.cancel()
                    flingJob?.cancel()
                    if (!crossedLatch) {
                        dragAccumulator.value += available.y
                        val halfway = collapseDistanceState.value / 2f
                        if (dragAccumulator.value >= halfway) {
                            crossedLatch = true
                            collapseCollapsed.value = false
                            dragAccumulator.value = 0f
                            scope.launch {
                                collapseProgress.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                                )
                            }
                        } else {
                            val ratio = (dragAccumulator.value / halfway).coerceIn(0f, 1f)
                            scope.launch { collapseProgress.snapTo(1f - 0.45f * ratio) }
                        }
                    }
                    settleLater()
                    return if (crossedLatch) Offset.Zero else Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (selectedStyle == EpisodeListRowStyle.GRID) return available
                // DOWN-fling while open: a fast swipe must collapse the
                // preview FIRST — however fast. Consume the fling, snap the
                // collapse, then hand the momentum to the list so the same
                // swipe keeps scrolling ("after that then the bottom section
                // will begin to scroll over"). Below the threshold the
                // leftover is negligible — pass it through.
                if (available.y < -1000f && !collapseCollapsed.value) {
                    val handoffVelocity = available.y
                    collapseCollapsed.value = true
                    clearGesture()
                    flingJob = scope.launch {
                        collapseProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(220, easing = FastOutSlowInEasing),
                        )
                        // THE MOMENTUM HANDOFF — a spline-decay scroll on the
                        // options list with the consumed fling's velocity
                        // (the documented AnimationState.animateDecay fling
                        // pattern; dispatchRawDelta bypasses the nested chain
                        // — no re-entrancy).
                        var lastValue = 0f
                        AnimationState(
                            initialValue = 0f,
                            initialVelocity = handoffVelocity,
                        ).animateDecay(flingDecay) {
                            val delta = value - lastValue
                            lastValue = value
                            lazyListState.dispatchRawDelta(delta)
                        }
                    }
                    return Velocity.Zero
                }
                return available
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                // UP-fling leftover: the list's own fling finished at the
                // very top with velocity to spare — settle the expansion now
                // ("first of all the bottom section should scroll to the very
                // top", THEN the preview opens).
                if (selectedStyle != EpisodeListRowStyle.GRID &&
                    collapseCollapsed.value &&
                    available.y > 0f
                ) {
                    collapseCollapsed.value = false
                    clearGesture()
                    collapseProgress.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                    )
                    return available
                }
                return Velocity.Zero
            }
        }
    }
    // A layout switch always re-opens the preview — a stale collapsed state
    // under GRID (whose connection never engages) would clip it forever.
    LaunchedEffect(selectedStyle) {
        collapseCollapsed.value = false
        collapseProgress.snapTo(0f)
    }
    // The first episode's measured height + the row gap = the exact shift
    // that hides episode 1 and pins episode 2 at the clip's top edge.
    val hidePx = collapseProgress.value * (firstRowHeightPx + rowGapPx)

    // ── D-558: the search-landing scroll (the anchor map is this screen's
    // half of the search contract: 0 layout · 1 cinema · 2 elements).
    rememberSettingsAnchorScroll(
        anchor = highlightAnchor,
        anchorIndexFor = { anchor ->
            when (anchor) {
                "episode_list", "layout" -> 0
                "cinema_corner", "cinema_style", "cinema_check" -> 1
                "el_synopsis", "el_date", "el_audio", "el_progress", "el_dim", "el_download" -> 2
                else -> null
            }
        },
        listState = lazyListState,
    )

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Episode list",
                collapsed = collapsed,
                onBack = onBack,
            )

            // ── THE LIVE PREVIEW — real library data (or the demo samples).
            // The clip+shift layout collapses it as the options scroll; the
            // animateContentSize makes LAYOUT SWITCHES glide ("the layout
            // section should move down slowly").
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                EpisodeListCard(label = "Live preview") {
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
                                            firstRowHeightPx = size.height
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
                        .nestedScroll(nestedConnection),
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
                        EpisodeListCard(label = "Layout") {
                            SettingsHighlightTarget(anchorId = "layout", activeAnchor = highlightAnchor) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    text = "Layout",
                                    fontFamily = RobotoFamily,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Box(modifier = Modifier.padding(top = 10.dp)) {
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
                            EpisodeListCard(label = "Cinema") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                ) {
                                    Text(
                                        text = "Cinema",
                                        fontFamily = RobotoFamily,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Column(
                                        modifier = Modifier.padding(top = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        SettingsHighlightTarget(anchorId = "cinema_corner", activeAnchor = highlightAnchor) {
                                        Column {
                                            Text(
                                                text = "Number position",
                                                fontFamily = RobotoFamily,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(bottom = 6.dp),
                                            )
                                            SegmentedToggle(
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
                                        }
                                        SettingsHighlightTarget(anchorId = "cinema_style", activeAnchor = highlightAnchor) {
                                        Column {
                                            Text(
                                                text = "Number style",
                                                fontFamily = RobotoFamily,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(bottom = 6.dp),
                                            )
                                            SegmentedToggle(
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
                                        }
                                        SettingsHighlightTarget(anchorId = "cinema_check", activeAnchor = highlightAnchor) {
                                            EpisodeListSwitchRow(
                                                title = "Watched check mark",
                                                description = null,
                                                checked = cinemaWatchedCheck,
                                                onChecked = { episodeListPrefs.cinemaWatchedCheck.set(it) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── the elements — switches with ONE-LINE descriptions ──
                    item {
                        EpisodeListCard(label = "Elements") {
                            SettingsHighlightTarget(anchorId = "el_synopsis", activeAnchor = highlightAnchor) {
                            EpisodeListSwitchRow(
                                title = "Synopsis",
                                description = "The two-line description (Classic rows only)",
                                checked = showSynopsis,
                                onChecked = { episodeListPrefs.showSynopsis.set(it) },
                            )
                            }
                            SettingsHighlightTarget(anchorId = "el_date", activeAnchor = highlightAnchor) {
                            EpisodeListSwitchRow(
                                title = "Release date",
                                description = "Shown in every layout where it fits",
                                checked = showDatePill,
                                onChecked = { episodeListPrefs.showDatePill.set(it) },
                            )
                            }
                            SettingsHighlightTarget(anchorId = "el_audio", activeAnchor = highlightAnchor) {
                            EpisodeListSwitchRow(
                                title = "Audio pills",
                                description = "SUB · DUB · HSUB availability",
                                checked = showAudioPills,
                                onChecked = { episodeListPrefs.showAudioPills.set(it) },
                            )
                            }
                            SettingsHighlightTarget(anchorId = "el_progress", activeAnchor = highlightAnchor) {
                            EpisodeListSwitchRow(
                                title = "Watch progress",
                                description = "The bar on the imagery's edge",
                                checked = showWatchProgress,
                                onChecked = { episodeListPrefs.showWatchProgress.set(it) },
                            )
                            }
                            SettingsHighlightTarget(anchorId = "el_dim", activeAnchor = highlightAnchor) {
                            EpisodeListSwitchRow(
                                title = "Dim watched",
                                description = "Fade and grayscale watched episodes",
                                checked = dimWatched,
                                onChecked = { episodeListPrefs.dimWatched.set(it) },
                            )
                            }
                            SettingsHighlightTarget(anchorId = "el_download", activeAnchor = highlightAnchor) {
                            EpisodeListSwitchRow(
                                title = "Download buttons",
                                description = "The control/badge on each episode",
                                checked = showDownloadControl,
                                onChecked = { episodeListPrefs.showDownloadControl.set(it) },
                            )
                            }
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
                        Text(
                            text = "Can be further customized by clicking episodes on the page",
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private data class PreviewItem(
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
private fun demoPreviewItems(packageName: String): List<PreviewItem> {
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
private fun loadLibraryPreviewItems(
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
private fun audioScanlatorHint(agg: EpisodeAudioAggregates): String? = when {
    agg.hasHsub -> "HSUB"
    agg.hasSub && agg.hasDub -> "SUB DUB"
    agg.hasSub -> "SUB"
    agg.hasDub -> "DUB"
    else -> null
}

// Local card + row shapes — the poster page's PosterCard/PosterSwitchRow look,
// duplicated here as privates (the poster's are file-private; sharing would
// widen their visibility for no reuse value beyond these two pages).

/**
 * The section card — the SettingsGroupCard look (the primary ExtraBold
 * label, the 12dp-rounded surfaceVariant surface) WITHOUT the 16dp
 * horizontal padding baked into the shared component: this screen carries
 * the single 8dp gutter (the D-525 rule).
 */
@Composable
private fun EpisodeListCard(
    label: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(vertical = 4.dp),
                content = content,
            )
        }
    }
}

/** The switch row — title + optional one-line description + the switch
 *  (D-532; the D-558 Cinema section's rows pass null — no descriptions
 *  there, per the user's explicit spec for that section). */
@Composable
private fun EpisodeListSwitchRow(
    title: String,
    description: String?,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
