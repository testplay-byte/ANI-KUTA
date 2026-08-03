package com.confused.anikuta.feature.animesearch

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.Motion
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import org.koin.compose.viewmodel.koinViewModel

/**
 * The Search screen — a dual-source search experience.
 *
 * Layout:
 *  - SearchTopBar (collapsing — title + source toggle + search bar + filters/sort row).
 *  - Scrollable content below:
 *    - Recent searches (shown when query is blank, no filters, and recents exist).
 *    - Results grid (or loading / empty / error / not-available state).
 *  - ScrollBlurOverlay at the top edge.
 *  - FilterSheet (ModalBottomSheet, dragHandle = null, Accordion + Flat views).
 *
 * CORE_RULES §22: smooth animations (300ms FastOutSlowInEasing, scale on press).
 * CORE_RULES §23: reactive state (StateFlow from ViewModel).
 */
@Composable
fun SearchScreen(
    onNavigateToDetails: (Int) -> Unit,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val query by viewModel.query.collectAsState()
    val source by viewModel.source.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val recents by viewModel.recents.collectAsState()
    val recentsCollapsed by viewModel.recentsCollapsed.collectAsState()
    val pendingFilters by viewModel.pendingFilters.collectAsState()

    val scrollState = rememberScrollState()
    val gridState = rememberLazyGridState()
    val collapsed = scrollState.value > 20

    var showFilterSheet by remember { mutableStateOf(false) }
    val activeFilterCount = pendingFilters.activeCount

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SearchTopBar(
            collapsed = collapsed,
            query = query,
            onQueryChange = viewModel::onQueryChange,
            onClearQuery = viewModel::onClearQuery,
            source = source,
            onSourceSelect = viewModel::onSourceChange,
            onSubmit = viewModel::onSubmit,
            onOpenFilters = { showFilterSheet = true },
            activeFilterCount = activeFilterCount,
            sort = sort,
            onSortChange = viewModel::onSortChange,
        )

        // Scrollable content
        Box(modifier = Modifier.fillMaxSize()) {
            when (uiState) {
                SearchUiState.Idle -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(top = 0.dp, bottom = 110.dp),
                    ) {
                        if (recents.isNotEmpty()) {
                            RecentSearchesCard(
                                recents = recents,
                                collapsed = recentsCollapsed,
                                onToggleCollapsed = viewModel::toggleRecentsCollapsed,
                                onPick = viewModel::onPickRecent,
                                onRemove = viewModel::onRemoveRecent,
                                onClear = viewModel::onClearRecents,
                            )
                        } else {
                            SearchPromptCard(
                                title = "Search anime",
                                description = "Find series on AniList by title. Tap a result to view details.",
                                icon = Icons.AutoMirrored.Filled.ManageSearch,
                            )
                        }
                    }
                }

                SearchUiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                SearchUiState.Empty -> SearchPromptCard(
                    title = "No results",
                    description = "Try a different title or sort option.",
                    icon = Icons.Filled.SearchOff,
                )

                SearchUiState.Error -> SearchPromptCard(
                    title = "AniList is being a tsundere",
                    description = "It won't share results right now. Try again in a moment.",
                    icon = Icons.Filled.SentimentDissatisfied,
                )

                SearchUiState.ExtensionNotAvailable -> SearchPromptCard(
                    title = "Extension search coming soon",
                    description = "Extension sources need the extension system (Phase 5). " +
                        "For now, use the AniList tab.",
                    icon = Icons.Filled.HourglassEmpty,
                )

                is SearchUiState.Success -> {
                    val results = (uiState as SearchUiState.Success).results
                    ResultsGrid(
                        results = results,
                        gridState = gridState,
                        onResultTap = onNavigateToDetails,
                    )
                }
            }

            ScrollBlurOverlay(
                scrollOffset = {
                    when (uiState) {
                        is SearchUiState.Success -> {
                            if (gridState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                            else gridState.firstVisibleItemScrollOffset.toFloat()
                        }
                        else -> scrollState.value.toFloat()
                    }
                },
                backgroundColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }

    // ── Filter sheet ──
    FilterSheet(
        show = showFilterSheet,
        pendingFilters = pendingFilters,
        appliedSort = sort.apiValue,
        onPendingFiltersChange = viewModel::onPendingFiltersChange,
        onSortChange = { apiValue ->
            SearchSort.entries.firstOrNull { it.apiValue == apiValue }?.let(viewModel::onSortChange)
        },
        onClearAll = viewModel::onClearAllFilters,
        onApply = {
            viewModel.onApplyFilters()
            showFilterSheet = false
        },
        onDismiss = { showFilterSheet = false },
    )
}

// ── Results grid ──

@Composable
private fun ResultsGrid(
    results: List<AniListAnime>,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onResultTap: (Int) -> Unit,
) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 4.dp,
            bottom = 110.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(results, key = { it.id }) { anime ->
            ResultCard(anime, onResultTap)
        }
    }
}

@Composable
private fun ResultCard(anime: AniListAnime, onClick: (Int) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(Motion.DurationShort, easing = FastOutSlowInEasing),
        label = "resultCardScale",
    )

    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onClick(anime.id) },
            ),
    ) {
        AsyncImage(
            model = anime.coverUrl,
            contentDescription = anime.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp)),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
            contentAlignment = Alignment.BottomStart,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                MaterialTheme.colorScheme.surface,
                            ),
                        ),
                    ),
            )
            Text(
                text = anime.displayName,
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

// ── Empty / error / not-available prompt card ──

@Composable
private fun SearchPromptCard(
    title: String,
    description: String,
    icon: ImageVector,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            fontFamily = RobotoFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = description,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
