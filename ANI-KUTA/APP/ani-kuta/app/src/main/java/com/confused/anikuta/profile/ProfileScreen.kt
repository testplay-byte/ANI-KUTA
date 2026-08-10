package com.confused.anikuta.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.settings.SegmentedToggle
import org.koin.compose.viewmodel.koinViewModel

/**
 * My Profile screen — complete UI restructure.
 *
 * Two-tab layout: "Stats" (main) and "Timeline" (other).
 *
 * Stats tab (top to bottom):
 * 1. Profile header (avatar + name + AniList status)
 * 2. Quick stats row (4 cards: anime count, watch time, mean score, streak)
 * 3. Watch flow bar graph (Mon-Sun)
 * 4. Time DNA (preferred watch time — clickable → detail screen)
 * 5. Genre radar chart (ported from old project)
 * 6. Activity heatmap (themed + avg daily time)
 *
 * Timeline tab:
 * - User's activity feed (anime watched, episodes, ratings, timestamps)
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onNavigateToAnime: (Int) -> Unit,
    onOpenTimeDna: () -> Unit = {},
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val collapsed = listState.firstVisibleItemIndex > 0 ||
        listState.firstVisibleItemScrollOffset > 20
    var selectedTab by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "My Profile",
                collapsed = collapsed,
                actions = { BackAction(onBack) },
            )

            // Tab bar (outside the scroll — doesn't scroll away)
            SegmentedToggle(
                options = listOf("Stats", "Timeline"),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    0 -> StatsTab(state, listState, viewModel, onNavigateToAnime, onOpenTimeDna)
                    1 -> TimelineTab(state, onNavigateToAnime)
                }

                ScrollBlurOverlay(
                    scrollOffset = {
                        if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                        else listState.firstVisibleItemScrollOffset.toFloat()
                    },
                    backgroundColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
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

// ════════════════════════════════════════════════════════════════════════════
//  Stats Tab
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun StatsTab(
    state: ProfileUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    viewModel: ProfileViewModel,
    onNavigateToAnime: (Int) -> Unit,
    onOpenTimeDna: () -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 1. Profile header
        item { ProfileHeader(state) }

        // 2. Quick stats row (4 cards)
        item { QuickStatsRow(state) }

        // 3. Watch flow bar graph (Mon-Sun)
        item { WatchFlowGraph(state.watchFlowByDay) }

        // 4. Time DNA
        item { TimeDnaCard(state.timeDna, onOpenTimeDna) }

        // 5. Genre radar chart
        if (state.genreDistribution.isNotEmpty()) {
            item {
                GenreRadarChart(
                    genres = state.genreDistribution,
                    onGenreClick = { viewModel.onGenreClick(it) },
                    selectedGenre = state.selectedGenre,
                )
            }
        }

        // 6. Activity heatmap
        item { ActivityHeatmapCard(state.activityData, state.avgDailyWatchTime) }
    }
}

// ── Profile Header (horizontal layout) ────────────────────────────────────────

@Composable
private fun ProfileHeader(state: ProfileUiState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar
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
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = "Profile",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = state.displayName,
                fontFamily = RobotoFamily,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (state.anilistUsername != null) "AniList connected" else "Not connected",
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                color = if (state.anilistUsername != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Quick Stats Row (4 cards in a single row) ─────────────────────────────────

@Composable
private fun QuickStatsRow(state: ProfileUiState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuickStatCard("Anime", "${state.totalAnime}", Modifier.weight(1f))
        QuickStatCard("Time", state.watchTimeFormatted, Modifier.weight(1f))
        QuickStatCard("Score", state.avgRatingFormatted, Modifier.weight(1f))
        QuickStatCard("Streak", "${state.currentStreak}", Modifier.weight(1f))
    }
}

@Composable
private fun QuickStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                value,
                fontFamily = RobotoFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                label,
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Back Action ───────────────────────────────────────────────────────────────

@Composable
private fun BackAction(onBack: () -> Unit) {
    Box(
        modifier = Modifier.size(36.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
