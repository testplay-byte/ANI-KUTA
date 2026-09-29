package com.confused.anikuta.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.common.EpisodeTitleParser
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.datacache.DataCacheRepository
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.component.ThinSlider
import com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeGridRow
import com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeListEntry
import com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeListStyle
import com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeRowData
import com.confused.anikuta.core.designsystem.component.playerlist.bannerAspectRatio
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.PlayerEpisodeListPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
 *  • THE ELEMENTS ARE STYLE-AWARE now, appearing/disappearing SMOOTHLY
 *    ("the options below, the elements options, should properly adjust
 *    accordingly and should disappear or appear smoothly depending on what's
 *    available to edit or what's not available to edit"):
 *      DETAILED  = Synopsis · Date pill · Dim watched
 *      TRACKLIST = Date pill · Dim watched
 *      GRID      = Dim watched
 *      BANNER    = Date pill · Dim watched · Episode number · Banner size
 *  • THE BANNER'S DENSITY SLIDER — "add a density slider too, like I can
 *    select what the size of them should be easily, and it would properly
 *    show in live view" (21:9 flat strips → 4:3 tall cards; the preview
 *    re-shapes live) + the EPISODE-NUMBER toggle ("it is not customizable").
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
) {
    // ── The reactive reads — the SAME prefs both player pages collect. Every
    // write below updates the pref; `changes` re-emits; the preview AND the
    // player lists re-shape from the same emission (no local mirror state,
    // the D-481 drift killer). ──
    val rowStyleKey by playerListPrefs.rowStyle.changes.collectAsState(
        initial = playerListPrefs.rowStyle.get(),
    )
    val style = remember(rowStyleKey) { PlayerEpisodeListStyle.fromKey(rowStyleKey) }
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
    val bannerDensity by playerListPrefs.bannerDensity.changes.collectAsState(
        initial = playerListPrefs.bannerDensity.get(),
    )
    val sortDescending by playerListPrefs.sortDescending.changes.collectAsState(
        initial = playerListPrefs.sortDescending.get(),
    )
    val display = remember(
        style, showSynopsis, showDatePill, dimWatched, showEpisodeNumber, bannerDensity,
    ) {
        com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeListDisplay(
            style = style,
            showSynopsis = showSynopsis,
            showDatePill = showDatePill,
            dimWatched = dimWatched,
            showEpisodeNumber = showEpisodeNumber,
            bannerDensity = bannerDensity,
        )
    }

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
    val currentData = orderedItems.getOrNull(0)?.toPlayerRowData(
        isCurrent = true,
        isWatched = currentUrl?.let { watchedOverride[it] } ?: false,
        progressFraction = 0.35f,
    )
    val watchedData = orderedItems.getOrNull(1)?.toPlayerRowData(
        isCurrent = false,
        isWatched = watchedUrl?.let { watchedOverride[it] } ?: true,
        progressFraction = 0f,
    )

    val isGrid = style == PlayerEpisodeListStyle.GRID
    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

    // ══════════════════════════════════════════════════════════════════════
    //  ROUND 104 (WS-D): THE D-557/D-558 PRIORITY SCROLL — the details page's
    //  machinery, ported VERBATIM (the user: "the same kind of effect, same
    //  scrolling, the same logics and everything like that to be exactly the
    //  same"). The connection is SPLIT BY DIRECTION, which makes the order
    //  deterministic BY CONSTRUCTION:
    //  - DOWN while open (drag OR fling): the collapse consumes EVERYTHING
    //    first — the list cannot move until the preview has snapped collapsed
    //    ("one of them should get hidden"). After the snap the connection
    //    RELEASES the same gesture: the remaining deltas flow into the list.
    //    A consumed down-fling additionally HANDS ITS MOMENTUM to the list
    //    through a decay scroll once the collapse settles.
    //  - UP while collapsed: the connection never consumes pre-scroll — the
    //    list scrolls first; only the LEFTOVER of an up-drag (the list is at
    //    the very top) can expand the preview, with the same halfway snap.
    //    An up-fling's leftover settles the expansion in onPostFling.
    //  GRID is exempt (the details page's rule — already compressed enough).
    // ══════════════════════════════════════════════════════════════════════
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
    // The decay spec for the fling-momentum handoff — exponential decay (the
    // factory that exists on EVERY Compose line; see the details page's note).
    val flingDecay = remember { exponentialDecay<Float>() }
    val nestedConnection = remember(style) {
        object : NestedScrollConnection {
            // D-557: the crossed latch — a single continuous drag crosses the
            // halfway point EXACTLY ONCE. While latched, further deltas are
            // consumed silently until the gesture ends (the settle window
            // clears the latch).
            private var crossedLatch = false

            private fun clearGesture() {
                crossedLatch = false
                dragAccumulator.value = 0f
            }

            // The settle window: after the last delta of a gesture, clear the
            // latch and settle the preview to its phase anchor.
            private fun settleLater() {
                settleJob?.cancel()
                settleJob = scope.launch {
                    delay(180)
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
                // Programmatic scrolls pass through; GRID never engages.
                if (source == NestedScrollSource.SideEffect) return Offset.Zero
                if (style == PlayerEpisodeListStyle.GRID) return Offset.Zero
                // DOWN while open: THE COLLAPSE PHASE — consume everything.
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
                    // After the snap the connection RELEASES the same gesture.
                    return if (crossedLatch) Offset.Zero else Offset(0f, available.y)
                }
                // UP (and everything else): never consume in PRE-scroll.
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.SideEffect) return Offset.Zero
                if (style == PlayerEpisodeListStyle.GRID) return Offset.Zero
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
                if (style == PlayerEpisodeListStyle.GRID) return available
                // DOWN-fling while open: a fast swipe must collapse the
                // preview FIRST — however fast. Consume the fling, snap the
                // collapse, then hand the momentum to the list.
                if (available.y < -1000f && !collapseCollapsed.value) {
                    val handoffVelocity = available.y
                    collapseCollapsed.value = true
                    clearGesture()
                    flingJob = scope.launch {
                        collapseProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(220, easing = FastOutSlowInEasing),
                        )
                        // THE MOMENTUM HANDOFF — a decay scroll on the options
                        // list with the consumed fling's velocity.
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
                // very top with velocity to spare — settle the expansion now.
                if (style != PlayerEpisodeListStyle.GRID &&
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
    LaunchedEffect(style) {
        collapseCollapsed.value = false
        collapseProgress.snapTo(0f)
    }
    // The first episode's measured height + the row gap = the exact shift
    // that hides episode 1 and pins episode 2 at the clip's top edge.
    val hidePx = collapseProgress.value * (firstRowHeightPx + rowGapPx)

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Player episode list",
                collapsed = collapsed,
                onBack = onBack,
            )

            // ── THE LIVE PREVIEW — the player's OWN renderer (the shared
            // four-paradigm dispatcher), fed the REAL library episodes; the
            // clip+shift layout collapses it per the D-557/D-558 machinery;
            // the animateContentSize makes LAYOUT SWITCHES glide. ──
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                PlayerListCard(label = "Live preview") {
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
                                            firstRowHeightPx = size.height
                                        },
                                    ) {
                                        PlayerEpisodeListEntry(
                                            data = currentData,
                                            display = display,
                                            onClick = { /* inert */ },
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
                    modifier = Modifier.fillMaxSize().nestedScroll(nestedConnection),
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        bottom = 24.dp,
                    ),
                ) {
                    // ── Layout — THE FOUR PARADIGMS (ROUND 104: the compact
                    //    slot's replacement is the TRACKLIST) ──
                    item {
                        PlayerListCard(label = "Layout") {
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
                                        options = listOf("Detailed", "Tracklist", "Grid", "Banner"),
                                        selectedIndex = style.ordinal,
                                        onSelect = { idx ->
                                            playerListPrefs.rowStyle.set(
                                                PlayerEpisodeListStyle.entries[idx].name,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // ── Elements — STYLE-AWARE with the smooth appear/
                    //    disappear ("should properly adjust accordingly and
                    //    should disappear or appear smoothly depending on
                    //    what's available to edit"). ──
                    item {
                        PlayerListCard(label = "Elements") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize(
                                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                                    ),
                            ) {
                                // Synopsis — DETAILED only.
                                AnimatedStyleRow(visible = style == PlayerEpisodeListStyle.DETAILED) {
                                    PlayerSwitchRow(
                                        title = "Synopsis",
                                        checked = showSynopsis,
                                        onChecked = { playerListPrefs.showSynopsis.set(it) },
                                    )
                                }
                                // Date pill — DETAILED, TRACKLIST + BANNER.
                                AnimatedStyleRow(
                                    visible = style != PlayerEpisodeListStyle.GRID,
                                ) {
                                    PlayerSwitchRow(
                                        title = "Date pill",
                                        checked = showDatePill,
                                        onChecked = { playerListPrefs.showDatePill.set(it) },
                                    )
                                }
                                // Episode number — BANNER only (ROUND 104).
                                AnimatedStyleRow(
                                    visible = style == PlayerEpisodeListStyle.BANNER,
                                ) {
                                    PlayerSwitchRow(
                                        title = "Episode number",
                                        checked = showEpisodeNumber,
                                        onChecked = { playerListPrefs.showEpisodeNumber.set(it) },
                                    )
                                }
                                // Dim watched — every style.
                                PlayerSwitchRow(
                                    title = "Dim watched episodes",
                                    checked = dimWatched,
                                    onChecked = { playerListPrefs.dimWatched.set(it) },
                                )
                                // The density slider — BANNER only (ROUND 104):
                                // "add a density slider too, like I can select
                                // what the size of them should be easily, and
                                // it would properly show in live view."
                                AnimatedStyleRow(
                                    visible = style == PlayerEpisodeListStyle.BANNER,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                ) {
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
                                                text = aspectLabel(bannerDensity),
                                                fontFamily = RobotoFamily,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                        ThinSlider(
                                            value = bannerDensity,
                                            onValueChange = { playerListPrefs.bannerDensity.set(it) },
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
                                                text = "Flat",
                                                fontFamily = RobotoFamily,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Text(
                                                text = "Tall",
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

                    // (ROUND 104 (WS-D): THE WATCHED FILTER CARD IS RETIRED —
                    // "Most definitely not needed, and it is apparently
                    // unnecessary… It should be completely removed.")

                    // ── Sort — DIRECTION ONLY ──
                    item {
                        PlayerListCard(label = "Sort") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    text = "Sort episodes",
                                    fontFamily = RobotoFamily,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Box(modifier = Modifier.padding(top = 10.dp)) {
                                    SegmentedToggle(
                                        options = listOf("Ascending", "Descending"),
                                        selectedIndex = if (sortDescending) 1 else 0,
                                        onSelect = { idx ->
                                            playerListPrefs.sortDescending.set(idx == 1)
                                        },
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
 */
private fun aspectLabel(density: Float): String {
    val aspect = bannerAspectRatio(density)
    return when {
        kotlin.math.abs(aspect - 21f / 9f) < 0.06f -> "21:9"
        kotlin.math.abs(aspect - 16f / 9f) < 0.06f -> "16:9"
        kotlin.math.abs(aspect - 4f / 3f) < 0.06f -> "4:3"
        else -> String.format(Locale.US, "%.1f:1", aspect)
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 103 (WS-4): the preview's mapping — a REAL library PreviewItem into
//  the player renderer's data bundle (the same fields the player stacks map:
//  number, title, thumbnail, date, audio, synopsis).
// ════════════════════════════════════════════════════════════════════════════

private fun PreviewItem.toPlayerRowData(
    isCurrent: Boolean,
    isWatched: Boolean,
    progressFraction: Float,
): PlayerEpisodeRowData {
    val ep = episode
    return PlayerEpisodeRowData(
        episodeNumberText = EpisodeTitleParser.formatEpisodeNumber(ep.episode_number),
        displayTitle = EpisodeTitleParser.getDisplayTitle(ep.name, ep.episode_number),
        thumbnailUrl = ep.preview_url ?: fallbackCoverUrl,
        dateText = if (ep.date_upload > 0) formatDate(ep.date_upload) else null,
        audioLabels = parseAudioLabels(ep.scanlator),
        synopsis = ep.summary,
        isCurrent = isCurrent,
        isWatched = isWatched,
        progressFraction = progressFraction,
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
//  The local card + switch shapes (the details settings screen's privates,
//  duplicated per the poster page's established reasoning)
// ════════════════════════════════════════════════════════════════════════════

/** The section card — the SettingsGroupCard look, single-8dp-gutter form. */
@Composable
private fun PlayerListCard(
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

/** The switch row — title + switch (no descriptions; the live preview is the description). */
@Composable
private fun PlayerSwitchRow(
    title: String,
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
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
