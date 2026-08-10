package com.confused.anikuta.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
 * My Profile screen — WhatsApp-style scroll animation + redesigned sections.
 *
 * The tab bar (Stats/Timeline) smoothly shrinks and moves into the header
 * as the user scrolls, similar to WhatsApp's contact info page.
 * Settings button is at top-right; back button removed (use gesture/nav back).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onNavigateToAnime: (Int) -> Unit,
    onOpenTimeDna: () -> Unit = {},
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val scrollOffset = remember { derivedStateOf {
        if (listState.firstVisibleItemIndex > 0) 1f
        else (listState.firstVisibleItemScrollOffset / 200f).coerceIn(0f, 1f)
    }}
    val collapsed = scrollOffset.value > 0.5f
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with settings button + animated tab overlay
            Box {
                CollapsingHeader(
                    title = "My Profile",
                    collapsed = collapsed,
                    actions = {
                        // Settings gear — always at top-right
                        Box(
                            modifier = Modifier.size(36.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                                .clickable { showSettings = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    },
                )

                // Animated tab overlay — slides into header as user scrolls
                val tabAlpha by animateFloatAsState(
                    targetValue = if (collapsed) 1f else 0f,
                    animationSpec = tween(300), label = "tabAlpha",
                )
                val tabScale by animateFloatAsState(
                    targetValue = if (collapsed) 1f else 1f,
                    animationSpec = tween(300), label = "tabScale",
                )
                // When collapsed: show mini tabs in the header area (right side, before settings)
                if (tabAlpha > 0.01f) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 16.dp)
                            .graphicsLayer { alpha = tabAlpha },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Mini segmented toggle (smaller, fits in header)
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Row(modifier = Modifier.padding(2.dp)) {
                                listOf("Stats", "Timeline").forEachIndexed { idx, label ->
                                    val isSelected = idx == selectedTab
                                    Surface(
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.clickable { selectedTab = idx },
                                    ) {
                                        Text(label, fontFamily = RobotoFamily, fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Full-size tab bar — visible when NOT collapsed, shrinks away when scrolling
            val fullTabAlpha by animateFloatAsState(
                targetValue = if (collapsed) 0f else 1f,
                animationSpec = tween(300), label = "fullTabAlpha",
            )
            val fullTabScale by animateFloatAsState(
                targetValue = if (collapsed) 0.7f else 1f,
                animationSpec = tween(300), label = "fullTabScale",
            )
            if (fullTabAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = fullTabAlpha
                            scaleX = fullTabScale
                            scaleY = fullTabScale
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                        }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    SegmentedToggle(
                        options = listOf("Stats", "Timeline"),
                        selectedIndex = selectedTab,
                        onSelect = { selectedTab = it },
                    )
                }
            }

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
        item { ProfileHeader(state) }
        item { QuickStatsRow(state) }
        item { WatchFlowGraph(state.watchFlowByDay) }
        item { TimeDnaCard(state.timeDna, state.recentlyWatched.firstOrNull(), onOpenTimeDna) }
        if (state.genreDistribution.isNotEmpty()) {
            item {
                GenreRadarChart(
                    genres = state.genreDistribution,
                    onGenreClick = { viewModel.onGenreClick(it) },
                    selectedGenre = state.selectedGenre,
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
