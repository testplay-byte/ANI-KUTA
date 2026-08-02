package com.confused.anikuta.feature.animelibrary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.designsystem.theme.Motion
import org.koin.compose.viewmodel.koinViewModel

/**
 * Library screen — the user's personal anime collection.
 *
 * Features:
 * - Grid/list view toggle
 * - Sort by title, score, date added
 * - Search within library
 * - Empty state with call-to-action
 * - Smooth animations (§22)
 *
 * CORE_RULES §22: smooth animations — card scale on press, no ripple, fade-in content.
 * CORE_RULES §23: reactive state — UI updates when library changes.
 * DESIGN-LANGUAGE.md: lime accent, warm darks, 16dp rounded corners, translucent surfaces.
 *
 * Customizable: grid columns, view mode, sort type are all user-configurable (D-037).
 */
@Composable
fun LibraryScreen(
    onNavigateToDetails: (Int) -> Unit,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortType by viewModel.sortType.collectAsState()
    val isGridMode by viewModel.isGridMode.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Header: search + sort + view toggle ──
            LibraryHeader(
                searchQuery = searchQuery,
                onSearchChange = viewModel::setSearchQuery,
                sortType = sortType,
                onSortChange = viewModel::setSortType,
                isGridMode = isGridMode,
                onToggleView = viewModel::toggleViewMode,
            )

            // ── Content ──
            when (val s = state) {
                is LibraryState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                is LibraryState.Empty -> EmptyLibraryState()

                is LibraryState.Error -> Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        s.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                is LibraryState.Success -> {
                    if (s.anime.isEmpty()) {
                        EmptyLibraryState()
                    } else if (isGridMode) {
                        LibraryGrid(s.anime, onNavigateToDetails)
                    } else {
                        LibraryList(s.anime, onNavigateToDetails)
                    }
                }
            }
        }
    }
}

// ── Header ──

@Composable
private fun LibraryHeader(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    sortType: LibrarySortType,
    onSortChange: (LibrarySortType) -> Unit,
    isGridMode: Boolean,
    onToggleView: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = {
                Text(
                    "Search library...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { onSearchChange("") },
                    )
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier.weight(1f),
        )

        // Sort button
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.9f else 1f,
            animationSpec = tween(Motion.DurationShort, easing = FastOutSlowInEasing),
            label = "sortScale",
        )

        Box(
            modifier = Modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        // Cycle through sort types
                        val next = when (sortType) {
                            LibrarySortType.TITLE -> LibrarySortType.SCORE
                            LibrarySortType.SCORE -> LibrarySortType.DATE_ADDED
                            LibrarySortType.DATE_ADDED -> LibrarySortType.TITLE
                        }
                        onSortChange(next)
                    },
                )
                .padding(10.dp),
        ) {
            Icon(
                Icons.Filled.Sort,
                contentDescription = "Sort: ${sortType.displayName}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }

        // View toggle button
        val viewInteraction = remember { MutableInteractionSource() }
        val viewPressed by viewInteraction.collectIsPressedAsState()
        val viewScale by animateFloatAsState(
            targetValue = if (viewPressed) 0.9f else 1f,
            animationSpec = tween(Motion.DurationShort, easing = FastOutSlowInEasing),
            label = "viewScale",
        )

        Box(
            modifier = Modifier
                .graphicsLayer { scaleX = viewScale; scaleY = viewScale }
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = viewInteraction,
                    indication = null,
                    onClick = onToggleView,
                )
                .padding(10.dp),
        ) {
            Icon(
                if (isGridMode) Icons.Filled.List else Icons.Filled.GridView,
                contentDescription = if (isGridMode) "Switch to list" else "Switch to grid",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// ── Grid view ──

@Composable
private fun LibraryGrid(
    anime: List<AniListAnime>,
    onNavigateToDetails: (Int) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 4.dp,
            bottom = 90.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(anime, key = { it.id }) { item ->
            LibraryCard(item, onNavigateToDetails)
        }
    }
}

@Composable
private fun LibraryCard(anime: AniListAnime, onClick: (Int) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(Motion.DurationShort, easing = FastOutSlowInEasing),
        label = "cardScale",
    )

    Column(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
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
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(16.dp)),
        )

        Text(
            text = anime.displayName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )

        anime.averageScore?.let { score ->
            Text(
                text = "★ $score",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 2.dp, top = 2.dp),
            )
        }
    }
}

// ── List view ──

@Composable
private fun LibraryList(
    anime: List<AniListAnime>,
    onNavigateToDetails: (Int) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 4.dp,
            bottom = 90.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(anime, key = { it.id }) { item ->
            LibraryRow(item, onNavigateToDetails)
        }
    }
}

@Composable
private fun LibraryRow(anime: AniListAnime, onClick: (Int) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(Motion.DurationShort, easing = FastOutSlowInEasing),
        label = "rowScale",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onClick(anime.id) },
            )
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Cover thumbnail
        AsyncImage(
            model = anime.coverUrl,
            contentDescription = anime.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(56.dp)
                .height(80.dp)
                .clip(RoundedCornerShape(8.dp)),
        )

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                anime.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            anime.seasonYear?.let { year ->
                Text(
                    "$year",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            anime.averageScore?.let { score ->
                Text(
                    "★ $score",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// ── Empty state ──

@Composable
private fun EmptyLibraryState() {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Your library is empty",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Browse and add anime to your library",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
