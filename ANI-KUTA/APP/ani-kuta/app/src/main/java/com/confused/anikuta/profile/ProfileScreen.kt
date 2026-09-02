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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.settings.SegmentedToggle
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
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit,
    onNavigateToAnime: (Int) -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenTimeDna: () -> Unit = {},
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val statsListState = rememberLazyListState()
    val timelineListState = rememberLazyListState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    // Threshold = the full-size tab bar height (~56dp). Scrolling past this fully
    // collapses the tabs. Also used as the magnetic-snap decision boundary.
    val collapseThresholdPx = with(density) { 56.dp.toPx() }

    val activeListState = if (selectedTab == 0) statsListState else timelineListState

    // Continuous scroll fraction [0..1] for the active tab. Read inside graphicsLayer
    // lambdas (deferred — no recomposition on scroll).
    val scrollFraction: () -> Float = {
        val ls = activeListState
        val raw = if (ls.firstVisibleItemIndex > 0) collapseThresholdPx
                  else ls.firstVisibleItemScrollOffset.toFloat()
        (raw / collapseThresholdPx).coerceIn(0f, 1f)
    }

    val collapsed by remember {
        derivedStateOf { scrollFraction() > 0.5f }
    }

    // ── Magnetic snap ────────────────────────────────────────────────────────
    // Only snaps when the user is near the TOP of the list (firstVisibleItemIndex == 0).
    // This prevents the snap from firing when the user is deep in content (which would
    // jump them back to the top). The snap area is limited to the tab-bar threshold.
    LaunchedEffect(activeListState) {
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
                actions = {
                    // Mini tab pill — equal-width segments. Alpha driven by scroll
                    // fraction so it fades in exactly as the full tabs fade out.
                    // Sits to the LEFT of the settings gear.
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(9.dp),
                        modifier = Modifier
                            .graphicsLayer { alpha = scrollFraction() }
                            .width(120.dp),
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
                    0 -> StatsTab(state, statsListState, scrollFraction, viewModel, onNavigateToAnime) { selectedTab = it }
                    1 -> TimelineTab(state, timelineListState, scrollFraction, onNavigateToAnime) { selectedTab = it }
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
    scrollFraction: () -> Float,
    viewModel: ProfileViewModel,
    onNavigateToAnime: (Int) -> Unit,
    onTabSelect: (Int) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Item 0: full-size tab bar — scrolls away + shrinks via graphicsLayer.
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val f = scrollFraction()
                        alpha = (1f - f).coerceIn(0f, 1f)
                        val s = 1f - f * 0.25f
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin.Center
                    }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                SegmentedToggle(
                    options = listOf("Stats", "Timeline"),
                    selectedIndex = 0,
                    onSelect = onTabSelect,
                )
            }
        }
        item { ProfileHeader(state) }
        item { QuickStatsRow(state) }
        item { WatchFlowGraph(state.watchFlowByDay, state.watchFlowDetail, onNavigateToAnime, listState) }
        item { TimeDnaAndRecentCard(state.timeDna, state.recentlyWatched, onNavigateToAnime) }
        if (state.genreDistribution.isNotEmpty()) {
            item {
                GenreRadarChart(
                    genres = state.genreDistribution,
                    onGenreClick = { viewModel.onGenreClick(it) },
                    selectedGenre = state.selectedGenre,
                    // Task 63 (round 23 — E): the category filter chips right of
                    // the heading — "All" + library categories; persisted choice
                    // re-applied on entry (VM), sheet honors the same scope.
                    filterOptions = state.genreFilterOptions,
                    selectedFilter = state.selectedGenreFilter,
                    onFilterSelect = { viewModel.onGenreFilterSelect(it) },
                )
            }
        }
        item { ActivityHeatmapCard(state.activityData, state.avgDailyWatchTime) }
    }
}

// ── Profile Header ────────────────────────────────────────────────────────────

@Composable
private fun ProfileHeader(state: ProfileUiState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
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
        }
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
