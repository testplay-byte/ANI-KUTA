package com.confused.anikuta.profile

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
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
    onNavigateToAnime: (Int) -> Unit,
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

    // ROUND 138 (the device report: the at-rest top was "ugly, glitchy… but as
    // soon as I scroll slightly, it moves up to the very top where it needs to
    // be"): the thing that made at-rest DIFFERENT from the settled state was the
    // in-list TAB BAND and its collapse/snap machinery. It is gone — the tab
    // pill now lives PERMANENTLY in the pinned header, the avatar row is the
    // first list item, and the collapse signal is the app's standard one. There
    // is no half-state left to look glitchy.
    val activeListState = if (selectedTab == 0) statsListState else timelineListState
    val collapsed by remember(activeListState) {
        derivedStateOf {
            activeListState.firstVisibleItemIndex > 0 ||
                activeListState.firstVisibleItemScrollOffset > 20
        }
    }
    // A gentle scrim driver for the top fade (deferred reads — no recomposition).
    val scrollFraction: () -> Float = {
        val ls = activeListState
        if (ls.firstVisibleItemIndex > 0) 1f
        else (ls.firstVisibleItemScrollOffset / 140f).coerceIn(0f, 1f)
    }

    val bgColor = MaterialTheme.colorScheme.background

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Pinned header: title + mini tab pill + settings gear ──────────────
            CollapsingHeader(
                title = "My Profile",
                collapsed = collapsed,
                // ROUND 138: the title is PINNED at the settled 24sp — with the
                // permanent tab pill sharing this row, a larger expanded size
                // truncated "My Profile" on narrow screens (the D-559 bug
                // class), and the report called the settled state "where it
                // needs to be". Expanded == collapsed here: no size animation.
                expandedTitleSize = 24f,
                expandedPaddingTop = 2f,
                expandedPaddingBottom = 0f,
                // D-558: the heading IS the back button (leading arrow + tappable title).
                onBack = onBack,
                actions = {
                    // ROUND 138: the tab pill is the PERMANENT tab control —
                    // always visible, equal-width segments, LEFT of the
                    // settings gear. (The old pattern — a full-size band INSIDE
                    // the list that faded/collapsed/snap-scrolled — was the
                    // glitchy at-rest top the report called out; with the pill
                    // here the avatar row is the first thing under the header
                    // at EVERY scroll position.)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(9.dp),
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
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 10.dp),
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
                        state, statsListState, viewModel, onNavigateToAnime,
                    )
                    1 -> TimelineTab(
                        state, timelineListState, onNavigateToAnime,
                    )
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
            onOpenAnime = { anilistId ->
                viewModel.clearGenreSelection()
                onNavigateToAnime(anilistId)
            },
        )
    }
}

// ── Stats Tab ─────────────────────────────────────────────────────────────────

@Composable
private fun StatsTab(
    state: ProfileUiState,
    listState: LazyListState,
    viewModel: ProfileViewModel,
    onNavigateToAnime: (Int) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 6.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // ROUND 138: the tab band is GONE — the avatar row is item 0, so the
        // at-rest top is EXACTLY the settled state the report called "where it
        // needs to be".
        item { ProfileHeader(state) }
        item { QuickStatsRow(state) }
        // ROUND 137 (D-831): the last-7-days watch flow (today rightmost) —
        // the old weekday-bucketed chart is gone.
        item { WatchFlowGraph(state.watchFlowDays, onNavigateToAnime) }
        item { TimeDnaAndRecentCard(state.timeDna, state.recentlyWatched, onNavigateToAnime) }
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
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickStatCard("Anime", "${state.totalAnime}", Modifier.weight(1f))
        QuickStatCard("Time", state.watchTimeFormatted, Modifier.weight(1f))
        QuickStatCard("Score", state.avgRatingFormatted, Modifier.weight(1f))
        QuickStatCard("Streak", "${state.currentStreak}", Modifier.weight(1f))
    }
}

@Composable
private fun QuickStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontFamily = RobotoFamily, fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Text(label, fontFamily = RobotoFamily, fontSize = 11.sp,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
