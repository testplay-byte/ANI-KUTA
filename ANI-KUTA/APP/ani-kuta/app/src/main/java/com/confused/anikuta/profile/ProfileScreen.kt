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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import org.koin.compose.viewmodel.koinViewModel

/**
 * My Profile screen — shows user identity, stats grid, genre radar chart,
 * watch activity heatmap, recently watched, top rated, + genre breakdown.
 *
 * Modular: each section is a separate composable for easy editing.
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onNavigateToAnime: (Int) -> Unit,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val collapsed = listState.firstVisibleItemIndex > 0 ||
        listState.firstVisibleItemScrollOffset > 20

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "My Profile",
                collapsed = collapsed,
                actions = { BackAction(onBack) },
            )

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 110.dp),
                ) {
                    // ── User identity ──
                    item { ProfileHeader(state) }

                    // ── Stats grid ──
                    item { StatsGrid(state) }

                    // ── Genre radar chart ──
                    if (state.genreDistribution.isNotEmpty()) {
                        item {
                            GenreRadarChart(
                                genres = state.genreDistribution,
                                onGenreClick = { genre ->
                                    viewModel.onGenreClick(genre)
                                },
                                selectedGenre = state.selectedGenre,
                            )
                        }
                    }

                    // ── Genre breakdown ──
                    if (state.genreDistribution.isNotEmpty()) {
                        item { GenreBreakdown(state.genreDistribution) }
                    }

                    // ── Recently watched ──
                    if (state.recentlyWatched.isNotEmpty()) {
                        item { RecentlyWatchedSection(state.recentlyWatched, onNavigateToAnime) }
                    }

                    // ── Top rated ──
                    if (state.topRated.isNotEmpty()) {
                        item { TopRatedSection(state.topRated, onNavigateToAnime) }
                    }

                    // ── Watch activity heatmap ──
                    item { ActivityHeatmap(state.activityData) }
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

// ── User identity header ──────────────────────────────────────────────────────

@Composable
private fun ProfileHeader(state: ProfileUiState) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Avatar
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            modifier = Modifier.size(80.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = "Profile",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = state.displayName,
            fontFamily = RobotoFamily,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (state.anilistUsername != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "@${state.anilistUsername}",
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ── Stats grid (2x3) ──────────────────────────────────────────────────────────

@Composable
private fun StatsGrid(state: ProfileUiState) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatCard("Library", "${state.totalAnime}", "anime", Modifier.weight(1f))
            StatCard("Watched", "${state.totalEpisodesWatched}", "episodes", Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatCard("Watch Time", state.watchTimeFormatted, "", Modifier.weight(1f))
            StatCard("Avg Rating", state.avgRatingFormatted, "/ 10", Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatCard("Top Genre", state.topGenre ?: "—", "", Modifier.weight(1f))
            StatCard("Streak", "${state.currentStreak}", "days", Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                value,
                fontFamily = RobotoFamily,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (unit.isNotEmpty()) {
                Text(
                    unit,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BackAction(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(50),
            )
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
