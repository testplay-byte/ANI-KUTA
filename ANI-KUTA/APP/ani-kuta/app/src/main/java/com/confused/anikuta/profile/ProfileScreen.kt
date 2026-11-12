package com.confused.anikuta.profile

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.settings.SegmentedToggle
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.koin.compose.viewmodel.koinViewModel

/**
 * My Profile screen — WhatsApp-contact-info-style scroll animation with magnetic snap.
 *
 * Layout:
 * - Pinned [CollapsingHeader] with title "My Profile" + mini tab pill (equal-width
 *   segments, fades in on scroll) + settings gear. A gradient blur scrim sits at
 *   the header's bottom edge when collapsed, so content scrolling underneath
 *   appears to fade into the header.
 * - A [LazyColumn] per tab whose **first item is the full-size tab bar**. As the
 *   user scrolls, item 0 shrinks + fades via a continuous [graphicsLayer] (deferred
 *   read — no "jump"). Once scrolled past, the ProfileHeader lands naturally at the
 *   top of the viewport — fully visible, not cut off.
 * - **Magnetic snap:** when the user lifts their finger after scrolling, the list
 *   animates to either fully-expanded (item 0 at top) or fully-collapsed (item 1
 *   at top), depending on which side of 50% the scroll fraction landed. This gives
 *   the "snap to that point" feel the user requested.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onOpenContent: (String, Int?) -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenTimeDna: () -> Unit = {},
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val statsListState = rememberLazyListState()
    val timelineListState = rememberLazyListState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }

    // ROUND 137 (D-831): the stats refresh on EVERY screen entry — the old
    // one-shot init{loadStats()} left stale numbers when the user came back
    // from a watching session (the report's "inconsistencies going around").
    LaunchedEffect(Unit) { viewModel.refresh() }

    val density = LocalDensity.current
    // ROUND 135: the threshold is the tab band's MEASURED height (minus a
    // small margin), not a hardcoded 56dp. The band is ~42dp — with the old
    // 56dp threshold the collapsingTabBand fixed point sat at f≈0.43 (<0.5),
    // so the half-collapsed state NEVER crossed the snap boundary and the top
    // looked "glitched" with dead space on first entry. The margin keeps the
    // fixed point just PAST 0.5 so the magnetic snap always wins from it.
    var bandHeightPx by remember { mutableStateOf(0f) }
    val collapseThresholdPx = with(density) {
        ((bandHeightPx - 6.dp.toPx()).coerceAtLeast(24.dp.toPx()))
    }

    val activeListState = if (selectedTab == 0) statsListState else timelineListState

    // Continuous scroll fraction [0..1] for the active tab. Read inside graphicsLayer
    // lambdas (deferred — no recomposition on scroll).
    val rawScrollFraction: () -> Float = {
        val ls = activeListState
        val raw = if (ls.firstVisibleItemIndex > 0) collapseThresholdPx
                  else ls.firstVisibleItemScrollOffset.toFloat()
        (raw / collapseThresholdPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
    }
    // ROUND 141 (D-851): the ANIMATED fraction — a fast scroll to item 1 jumps
    // the raw value 0 -> 1 in ONE frame (the report: scrolling back up, the
    // tabs 'disappear without the animation'). Every consumer (the band's
    // alpha + layout collapse, the header pill's alpha) now reads THIS — any
    // jump interpolates over 220ms, both directions.
    val collapseFraction by animateFloatAsState(
        targetValue = rawScrollFraction(),
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "profileCollapseFraction",
    )
    val scrollFraction: () -> Float = { collapseFraction }

    // ROUND 135: keyed on the ACTIVE list state AND the measured band height —
    // the un-keyed remember held the FIRST composition's closure (always the
    // Stats list + the pre-measurement threshold), so after a tab switch (or
    // before the band measured) the header collapse state read the WRONG list.
    val collapsed by remember(activeListState, bandHeightPx) {
        derivedStateOf { scrollFraction() > 0.5f }
    }

    // ── Magnetic snap ────────────────────────────────────────────────────────
    // Only snaps when the user is near the TOP of the list (firstVisibleItemIndex == 0).
    // This prevents the snap from firing when the user is deep in content (which would
    // jump them back to the top). The snap area is limited to the tab-bar threshold.
    // ROUND 135: re-armed on the measured band height too (the threshold the
    // snap reads must be the measured one).
    LaunchedEffect(activeListState, bandHeightPx) {
        snapshotFlow { activeListState.isScrollInProgress }
            .distinctUntilChanged()
            .filter { !it } // only when scroll ENDS
            .collect {
                // Only snap if the user is at item 0 (near the top, in the tab-bar area).
                if (activeListState.firstVisibleItemIndex == 0) {
                    val f = scrollFraction()
                    if (f > 0.5f) {
                        activeListState.animateScrollToItem(1, 0)
                    } else {
                        activeListState.animateScrollToItem(0, 0)
                    }
                }
            }
    }

    val bgColor = MaterialTheme.colorScheme.background

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Pinned header: title + mini tab pill + settings gear ──────────────
            CollapsingHeader(
                title = "My Profile",
                collapsed = collapsed,
                // ROUND 128: the scroll-0 state had too much empty padding above
                // and below the heading (the device report) — a tighter expanded
                // header for THIS screen only (the shared defaults are unchanged).
                // ROUND 140 (D-847): the title a little BIGGER (26 -> 28sp) and a
                // clear gap between the heading and the tabs band below it (the
                // report: 'add some space between the top my profile section and
                // the stats and the timeline button').
                expandedTitleSize = 28f,
                expandedPaddingTop = 2f,
                expandedPaddingBottom = 6f,
                // D-558: the heading IS the back button (leading arrow + tappable title).
                onBack = onBack,
                actions = {
                    // Mini tab pill — equal-width segments. D-559: the pill's
                    // WIDTH now follows the collapse too (the same 300ms
                    // FastOutSlowIn curve as the header's own animation): at
                    // the top it occupies ZERO width, so the 32sp "My
                    // Profile" title gets its full room back. The v1.1.32
                    // device round caught the alpha-0 pill still eating
                    // 120dp of layout width and truncating the title to
                    // "My PR…" until the first scroll collapsed the header
                    // (24sp fit where 32sp didn't). Alpha stays
                    // scroll-driven — it fades in exactly as the full tabs
                    // fade out; the width is the fix. Sits to the LEFT of
                    // the settings gear.
                    val pillWidthFraction by animateFloatAsState(
                        targetValue = if (collapsed) 1f else 0f,
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        label = "profileMiniPillWidth",
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(9.dp),
                        modifier = Modifier
                            .graphicsLayer { alpha = scrollFraction() }
                            .width((120 * pillWidthFraction).dp)
                            // ROUND 139 (D-838 — THE at-rest glitch root cause): at
                            // width 0 the pill's Texts wrapped ONE GRAPHEME PER LINE
                            // at the inherited 24sp line height → a ~204dp-tall
                            // invisible column inflated the whole header row (the
                            // title+gear were vertically centered inside it — "not
                            // showing on the very top part… a lot of padding"), and
                            // the row snapped 204→36dp mid-scroll when the growing
                            // width first fit "Timeline" (the "moves up when I scroll
                            // slightly" jump). maxLines=1 + softWrap=false + an
                            // explicit compact lineHeight pin the pill at ~24dp at
                            // EVERY width; the heightIn cap is belt-and-braces.
                            .heightIn(max = 26.dp),
                    ) {
                        Row(modifier = Modifier.padding(2.dp)) {
                            listOf("Stats", "Timeline").forEachIndexed { idx, label ->
                                val isSelected = idx == selectedTab
                                Surface(
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else androidx.compose.ui.graphics.Color.Transparent,
                                    shape = RoundedCornerShape(7.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedTab = idx },
                                ) {
                                    Text(
                                        text = label,
                                        fontFamily = RobotoFamily,
                                        fontSize = 11.sp,
                                        lineHeight = 12.sp,
                                        softWrap = false,
                                        maxLines = 1,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    // Settings gear — always at top-right
                    Box(
                        modifier = Modifier.size(36.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                            .clickable { showSettings = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Settings, contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
            )

            // ── Tab content + gradient blur scrim ─────────────────────────────────
            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    0 -> StatsTab(
                        state, statsListState, scrollFraction, viewModel, onOpenContent,
                        onBandMeasured = { bandHeightPx = it.toFloat() },
                    ) { selectedTab = it }
                    1 -> TimelineTab(
                        state, timelineListState, scrollFraction, onOpenContent,
                        onBandMeasured = { bandHeightPx = it.toFloat() },
                    ) { selectedTab = it }
                }

                // Gradient blur scrim at the top edge — fades in when collapsed so
                // content scrolling under the header appears to blur/fade into it.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .align(Alignment.TopCenter)
                        .graphicsLayer {
                            val f = scrollFraction()
                            alpha = (f * f * (3 - 2 * f)) // smoothstep
                        }
                        .drawBehind {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        bgColor,
                                        bgColor.copy(alpha = 0.85f),
                                        bgColor.copy(alpha = 0.4f),
                                        bgColor.copy(alpha = 0.0f),
                                    ),
                                    startY = 0f,
                                    endY = size.height,
                                ),
                            )
                        },
                )
            }
        }
    }

    // Settings sheet
    if (showSettings) {
        ProfileSettingsSheet(
            state = state,
            onDismiss = { showSettings = false },
            onUpdateName = { viewModel.updateDisplayName(it) },
            onUpdateAvatar = { viewModel.updateAvatarUrl(it) },
        )
    }

    // Genre anime sheet
    val selectedGenre = state.selectedGenre
    if (selectedGenre != null) {
        GenreAnimeSheet(
            genre = selectedGenre,
            anime = state.genreAnime,
            // Task 64 (round 24 — E): the sheet's subtitle reflects the active
            // category filter ("12 anime in Watching" vs "…in your library").
            scopeLabel = if (state.selectedGenreFilter == "All") "your library" else state.selectedGenreFilter,
            onDismiss = { viewModel.clearGenreSelection() },
            onOpenContent = { mainId, episodeNumber ->
                viewModel.clearGenreSelection()
                onOpenContent(mainId, episodeNumber)
            },
        )
    }
}

// ── Stats Tab ─────────────────────────────────────────────────────────────────

/**
 * ROUND 129 (the device report: "the profile screen is still glitched"): the
 * tab band used to fade/scale VISUALLY only (graphicsLayer) while keeping its
 * full layout height — between the top of the list and the magnetic snap
 * there was a see-through ~42dp hole under the header where the band used to
 * be. This modifier collapses the band in LAYOUT, driven by the same scroll
 * fraction: the band's measured viewport shrinks with the scroll, the content
 * below slides up naturally, and a fully-collapsed band reports a 0dp height.
 * The child is measured at its natural (unconstrained) height every pass, so
 * the shrink never feeds back into the measurement; the LazyColumn's offset
 * clamping converges (the fixed point sits at half-collapse, where the
 * magnetic snap takes over on finger-up).
 */
internal fun Modifier.collapsingTabBand(
    fraction: () -> Float,
): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(
        constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity),
    )
    val f = fraction().coerceIn(0f, 1f)
    val target = (placeable.height * (1f - f)).roundToInt()
    layout(constraints.maxWidth, target) {
        placeable.placeRelative(0, 0)
    }
}.clipToBounds()

@Composable
private fun StatsTab(
    state: ProfileUiState,
    listState: LazyListState,
    scrollFraction: () -> Float,
    viewModel: ProfileViewModel,
    onOpenContent: (String, Int?) -> Unit,
    onBandMeasured: (Int) -> Unit = {},
    onTabSelect: (Int) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp),
        // ROUND 135: 6dp (was 8) — part of the top-section chrome shave.
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Item 0: full-size tab bar — collapses in LAYOUT with the scroll
        // (ROUND 129) + fades/shrinks visually via graphicsLayer.
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // ROUND 140 (D-847): the band's exit is a PURE fade + the
                    // layout collapse — the old simultaneous scale read as a
                    // messy double animation.
                    .graphicsLayer {
                        alpha = (1f - scrollFraction()).coerceIn(0f, 1f)
                    }
                    .collapsingTabBand(scrollFraction)
                    .padding(horizontal = 12.dp),
            ) {
                // ROUND 140 (D-847): the toggle's NATURAL height feeds the collapse
                // threshold (the band Box itself collapses in layout, so IT
                // must never be the measured node).
                // ROUND 141 (D-851): the CARD treatment — a theme-tinted surface
                // with a subtle border and inner padding, matching the page's
                // card language (the bare toggle read as floating buttons).
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    ),
                    modifier = Modifier.onSizeChanged { onBandMeasured(it.height) },
                ) {
                    SegmentedToggle(
                        options = listOf("Stats", "Timeline"),
                        selectedIndex = 0,
                        onSelect = onTabSelect,
                        modifier = Modifier.padding(6.dp),
                    )
                }
            }
        }
        item { ProfileHeader(state) }
        item { QuickStatsRow(state) }
        // ROUND 137 (D-831): the last-7-days watch flow (today rightmost) —
        // the old weekday-bucketed chart is gone.
        item { WatchFlowGraph(state.watchFlowDays, onOpenContent) }
        item { TimeDnaAndRecentCard(state.timeDna, state.recentlyWatched, onOpenContent) }
        if (state.genreDistribution.isNotEmpty()) {
            item {
                GenreRadarChart(
                    // Task 64 (round 24 — E): the radar draws the FILTER-
                    // restricted distribution; the chips section + the
                    // section's visibility use the FULL distribution (the
                    // section must never disappear because a filter's scope
                    // is empty).
                    genres = state.filteredGenreDistribution,
                    allGenres = state.genreDistribution,
                    onGenreClick = { viewModel.onGenreClick(it) },
                    selectedGenre = state.selectedGenre,
                    filterOptions = state.genreFilterOptions,
                    selectedFilter = state.selectedGenreFilter,
                    onFilterSelect = { viewModel.onGenreFilterSelect(it) },
                )
            }
        }
        item { ActivityHeatmapCard(state.activityMinutes, state.avgDailyWatchTime) }
    }
}

// ── Profile Header ────────────────────────────────────────────────────────────

@Composable
private fun ProfileHeader(state: ProfileUiState) {
    Row(
        // ROUND 135: 10dp vertical (was 16) — the top-section chrome shave
        // ("way too much empty space" on first entry).
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            modifier = Modifier.size(72.dp),
        ) {
            if (state.avatarUrl != null) {
                AsyncImage(
                    model = state.avatarUrl,
                    contentDescription = "Avatar",
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Person, contentDescription = "Profile",
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(state.displayName, fontFamily = RobotoFamily, fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(if (state.anilistUsername != null) "AniList connected" else "Not connected",
                fontFamily = RobotoFamily, fontSize = 13.sp,
                color = if (state.anilistUsername != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant)
            // ROUND 137 (D-831): the watching span — "from where to where" the
            // user has watched (the report's explicit ask), e.g.
            // "142h watched · since Mar 3".
            if (state.totalMinutesWatched > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        append(spanLine(state.totalMinutesWatched))
                        state.firstWatchedAt?.let { first ->
                            append(" · since ")
                            append(java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                                .format(java.util.Date(first)))
                        }
                    },
                    fontFamily = RobotoFamily, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "142h watched" / "3d 4h watched" / "48m watched" from whole minutes. */
private fun spanLine(totalMinutes: Int): String {
    val days = totalMinutes / (24 * 60)
    val hours = (totalMinutes % (24 * 60)) / 60
    val mins = totalMinutes % 60
    return when {
        days > 0 -> "${days}d ${hours}h watched"
        hours > 0 -> "${hours}h ${mins}m watched"
        else -> "${mins}m watched"
    }
}

// ── Quick Stats Row ───────────────────────────────────────────────────────────

@Composable
private fun QuickStatsRow(state: ProfileUiState) {
    // ROUND 139 (D-839): DYNAMIC widths — each card takes only the width its
    // content needs (the old weight(1f) gave "Score" the same space as a wide
    // "3d 12h" Time value and forced the Time onto two lines). softWrap=false
    // + maxLines=1 make the line break structurally impossible.
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        QuickStatCard("Anime", "${state.totalAnime}")
        QuickStatCard("Time", state.watchTimeFormatted)
        QuickStatCard("Score", state.avgRatingFormatted)
        QuickStatCard("Streak", "${state.currentStreak}")
    }
}

@Composable
private fun QuickStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontFamily = RobotoFamily, fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary,
                maxLines = 1, softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Clip)
            Text(label, fontFamily = RobotoFamily, fontSize = 11.sp,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, softWrap = false)
        }
    }
}
