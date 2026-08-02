package com.confused.anikuta.feature.animelibrary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.BorderStroke
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.EmptyState
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.component.SearchField
import com.confused.anikuta.core.designsystem.theme.Motion
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import org.koin.compose.viewmodel.koinViewModel

/**
 * Library screen — the user's personal anime collection.
 *
 * Faithfully recreates the old project's Library UI:
 * 1. CollapsingHeader (pinned) — title "Library" + HeaderActionGroup
 *    (search + settings buttons in ONE combined pill container, surfaceVariant bg,
 *    rounded 50, 34dp icons).
 * 2. Animated search bar (fade in/out when search toggled) using SearchField.
 * 3. Compact grid (3-column) with cover + gradient title overlay
 *    OR list view (horizontal rows).
 * 4. ScrollBlurOverlay at the header's bottom edge.
 * 5. Empty state with proper icon.
 * 6. CustomizeSheet — the library settings bottom sheet (Sort + Display & Badges
 *    in 2 tabs, no drag handle, header "Library Settings").
 *
 * CORE_RULES §22: smooth animations (300ms FastOutSlowInEasing, scale on press).
 * CORE_RULES §23: reactive state (StateFlow from ViewModel).
 * All text uses fontFamily = RobotoFamily; titles/labels use FontWeight.ExtraBold.
 */
@Composable
fun LibraryScreen(
    onNavigateToDetails: (Int) -> Unit,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortType by viewModel.sortType.collectAsState()
    val sortAscending by viewModel.sortAscending.collectAsState()
    val displayMode by viewModel.displayMode.collectAsState()
    val columns by viewModel.columns.collectAsState()
    val titleLines by viewModel.titleLines.collectAsState()
    val episodeBadgeMode by viewModel.episodeBadgeMode.collectAsState()
    val episodeBadgePosition by viewModel.episodeBadgePosition.collectAsState()
    val showScoreBadge by viewModel.showScoreBadge.collectAsState()
    val scoreBadgePosition by viewModel.scoreBadgePosition.collectAsState()
    val showContinueWatching by viewModel.showContinueWatching.collectAsState()
    val showTotalEntries by viewModel.showTotalEntries.collectAsState()

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    var showSearchBar by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    val isList = displayMode == LibraryDisplayMode.LIST
    val collapsed = if (!isList) {
        gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 20
    } else {
        listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 20
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Collapsing header (pinned) ──
            CollapsingHeader(
                title = "Library",
                collapsed = collapsed,
                actions = {
                    HeaderActionGroup(
                        onSearch = { showSearchBar = !showSearchBar },
                        onSettings = { showSettingsSheet = true },
                    )
                },
            )

            // ── Search bar (animated — fades in/out) ──
            AnimatedVisibility(
                visible = showSearchBar,
                enter = fadeIn(tween(Motion.DurationStandard, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(Motion.DurationShort, easing = FastOutSlowInEasing)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SearchField(
                        query = searchQuery,
                        onQueryChange = viewModel::setSearchQuery,
                        placeholder = "Search library",
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    HeaderActionButton(
                        icon = Icons.Filled.Close,
                        contentDescription = "Close search",
                        onClick = {
                            showSearchBar = false
                            viewModel.setSearchQuery("")
                        },
                    )
                }
            }

            // ── Content ──
            Box(modifier = Modifier.fillMaxSize()) {
                when (val s = state) {
                    is LibraryState.Loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    is LibraryState.Empty -> EmptyState(
                        title = "Your library is empty",
                        description = "Browse anime and add them to your library.",
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                    )

                    is LibraryState.Error -> Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            s.message,
                            fontFamily = RobotoFamily,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    is LibraryState.Success -> {
                        if (s.anime.isEmpty()) {
                            EmptyState(
                                title = "No anime found",
                                description = "Try a different search query.",
                                icon = Icons.Filled.SearchOff,
                            )
                        } else if (!isList) {
                            LibraryGrid(
                                anime = s.anime,
                                gridState = gridState,
                                columns = columns,
                                titleLines = titleLines,
                                onNavigateToDetails = onNavigateToDetails,
                            )
                        } else {
                            LibraryList(s.anime, listState, onNavigateToDetails)
                        }
                    }
                }

                // ── Scroll blur overlay (fades in when content scrolls under header) ──
                ScrollBlurOverlay(
                    scrollOffset = {
                        if (!isList) {
                            if (gridState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                            else gridState.firstVisibleItemScrollOffset.toFloat()
                        } else {
                            if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                            else listState.firstVisibleItemScrollOffset.toFloat()
                        }
                    },
                    backgroundColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }

        // ── Library settings bottom sheet ──
        if (showSettingsSheet) {
            CustomizeSheet(
                displayMode = displayMode,
                columns = columns,
                titleLines = titleLines,
                episodeBadgeMode = episodeBadgeMode,
                episodeBadgePosition = episodeBadgePosition,
                showScoreBadge = showScoreBadge,
                scoreBadgePosition = scoreBadgePosition,
                showContinueWatching = showContinueWatching,
                showTotalEntries = showTotalEntries,
                sortType = sortType,
                sortAscending = sortAscending,
                onDisplayModeChange = viewModel::setDisplayMode,
                onColumnsChange = viewModel::setColumns,
                onEpisodeBadgeModeChange = viewModel::setEpisodeBadgeMode,
                onEpisodeBadgePositionChange = viewModel::setEpisodeBadgePosition,
                onShowScoreBadgeChange = viewModel::setShowScoreBadge,
                onScoreBadgePositionChange = viewModel::setScoreBadgePosition,
                onShowContinueWatchingChange = viewModel::setShowContinueWatching,
                onShowTotalEntriesChange = viewModel::setShowTotalEntries,
                onTitleLinesChange = viewModel::setTitleLines,
                onSortChange = viewModel::setSort,
                onDismiss = { showSettingsSheet = false },
            )
        }
    }
}

// ── HeaderActionGroup: combined search + settings pill container ──

/**
 * A shared pill-shaped container holding the search + settings buttons together.
 *
 * Mirrors the old project's HeaderActionGroup exactly:
 * - Surface: surfaceVariant, RoundedCornerShape(50)
 * - Inner Row: 4dp padding, 2dp spacing
 * - Each button: 34dp circle, transparent bg, 18dp icon
 */
@Composable
private fun HeaderActionGroup(
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            HeaderActionButton(
                icon = Icons.Filled.Search,
                contentDescription = "Search library",
                onClick = onSearch,
                inGroup = true,
            )
            HeaderActionButton(
                icon = Icons.Filled.Tune,
                contentDescription = "Library settings",
                onClick = onSettings,
                inGroup = true,
            )
        }
    }
}

@Composable
private fun HeaderActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    inGroup: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = tween(Motion.DurationShort, easing = FastOutSlowInEasing),
        label = "headerBtnScale",
    )

    Box(
        modifier = Modifier
            .size(34.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (inGroup) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Library Settings Sheet (CustomizeSheet) — 2 tabs, dragHandle = null
// ════════════════════════════════════════════════════════════════════════════

/**
 * Unified library settings sheet — a SINGLE bottom-up sheet with 2 tabs at the
 * top: Sort, and Display & Badges (combined).
 *
 * Per spec: dragHandle = null on the ModalBottomSheet (no pull-down bar).
 * Header text is "Library Settings" (20sp ExtraBold, RobotoFamily, onSurface).
 * The 2 tabs share a centered background container (surfaceVariant 0.5 alpha,
 * 12dp rounded), with a separator below them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomizeSheet(
    displayMode: LibraryDisplayMode,
    columns: Int,
    titleLines: Int,
    episodeBadgeMode: EpisodeBadgeMode,
    episodeBadgePosition: BadgePosition,
    showScoreBadge: Boolean,
    scoreBadgePosition: BadgePosition,
    showContinueWatching: Boolean,
    showTotalEntries: Boolean,
    sortType: LibrarySortType,
    sortAscending: Boolean,
    onDisplayModeChange: (LibraryDisplayMode) -> Unit,
    onColumnsChange: (Int) -> Unit,
    onEpisodeBadgeModeChange: (EpisodeBadgeMode) -> Unit,
    onEpisodeBadgePositionChange: (BadgePosition) -> Unit,
    onShowScoreBadgeChange: (Boolean) -> Unit,
    onScoreBadgePositionChange: (BadgePosition) -> Unit,
    onShowContinueWatchingChange: (Boolean) -> Unit,
    onShowTotalEntriesChange: (Boolean) -> Unit,
    onTitleLinesChange: (Int) -> Unit,
    onSortChange: (LibrarySortType, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxSheetHeight = screenHeight * 0.75f

    var activeTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Sort", "Display & Badges")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null, // ── No drag handle per spec ──
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            // ── Header ──
            Text(
                text = "Library Settings",
                fontFamily = RobotoFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp, top = 16.dp),
            )

            // ── Tab strip — 2 tabs in a shared centered background ──
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    tabs.forEachIndexed { index, label ->
                        val isActive = index == activeTab
                        Surface(
                            color = if (isActive) MaterialTheme.colorScheme.primary
                                    else Color.Transparent,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { activeTab = index },
                        ) {
                            Text(
                                text = label,
                                fontFamily = RobotoFamily,
                                fontSize = 13.sp,
                                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (isActive) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            // ── Separator below the tabs ──
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 0.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Spacer(Modifier.height(8.dp))

            // ── Tab content ──
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(maxSheetHeight),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                when (activeTab) {
                    0 -> sortTab(
                        sortType = sortType,
                        sortAscending = sortAscending,
                        onSortChange = onSortChange,
                    )
                    1 -> displayBadgesTab(
                        displayMode = displayMode,
                        columns = columns,
                        titleLines = titleLines,
                        episodeBadgeMode = episodeBadgeMode,
                        episodeBadgePosition = episodeBadgePosition,
                        showScoreBadge = showScoreBadge,
                        scoreBadgePosition = scoreBadgePosition,
                        showContinueWatching = showContinueWatching,
                        showTotalEntries = showTotalEntries,
                        onDisplayModeChange = onDisplayModeChange,
                        onColumnsChange = onColumnsChange,
                        onTitleLinesChange = onTitleLinesChange,
                        onEpisodeBadgeModeChange = onEpisodeBadgeModeChange,
                        onEpisodeBadgePositionChange = onEpisodeBadgePositionChange,
                        onShowScoreBadgeChange = onShowScoreBadgeChange,
                        onScoreBadgePositionChange = onScoreBadgePositionChange,
                        onShowContinueWatchingChange = onShowContinueWatchingChange,
                        onShowTotalEntriesChange = onShowTotalEntriesChange,
                    )
                }
            }
        }
    }
}

// ── Sort tab ──

private fun androidx.compose.foundation.lazy.LazyListScope.sortTab(
    sortType: LibrarySortType,
    sortAscending: Boolean,
    onSortChange: (LibrarySortType, Boolean) -> Unit,
) {
    // ── Direction (at the TOP) ──
    item { OptionLabel("Direction") }
    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                Triple("Ascending", true, Icons.Filled.ArrowUpward),
                Triple("Descending", false, Icons.Filled.ArrowDownward),
            ).forEach { (label, asc, icon) ->
                val isSelected = sortAscending == asc
                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).clickable { onSortChange(sortType, asc) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = label,
                            fontFamily = RobotoFamily,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    // ── Sort by (below direction) ──
    item { Spacer(Modifier.height(20.dp)) }
    item { OptionLabel("Sort by") }
    LibrarySortType.entries.forEach { type ->
        item {
            SortOptionCard(
                label = type.displayName,
                isSelected = sortType == type,
                onClick = { onSortChange(type, sortAscending) },
            )
        }
        item { Spacer(Modifier.height(6.dp)) }
    }
}

// ── Display & Badges tab (combined) ──

private fun androidx.compose.foundation.lazy.LazyListScope.displayBadgesTab(
    displayMode: LibraryDisplayMode,
    columns: Int,
    titleLines: Int,
    episodeBadgeMode: EpisodeBadgeMode,
    episodeBadgePosition: BadgePosition,
    showScoreBadge: Boolean,
    scoreBadgePosition: BadgePosition,
    showContinueWatching: Boolean,
    showTotalEntries: Boolean,
    onDisplayModeChange: (LibraryDisplayMode) -> Unit,
    onColumnsChange: (Int) -> Unit,
    onTitleLinesChange: (Int) -> Unit,
    onEpisodeBadgeModeChange: (EpisodeBadgeMode) -> Unit,
    onEpisodeBadgePositionChange: (BadgePosition) -> Unit,
    onShowScoreBadgeChange: (Boolean) -> Unit,
    onScoreBadgePositionChange: (BadgePosition) -> Unit,
    onShowContinueWatchingChange: (Boolean) -> Unit,
    onShowTotalEntriesChange: (Boolean) -> Unit,
) {
    // ── Display mode (4-grid of visual cards) ──
    item { OptionLabel("Display Mode") }
    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DisplayModeCard(
                icon = Icons.Filled.GridView,
                label = "Compact",
                isSelected = displayMode == LibraryDisplayMode.COMPACT_GRID,
                onClick = { onDisplayModeChange(LibraryDisplayMode.COMPACT_GRID) },
                modifier = Modifier.weight(1f),
            )
            DisplayModeCard(
                icon = Icons.Filled.ViewAgenda,
                label = "Comfortable",
                isSelected = displayMode == LibraryDisplayMode.COMFORTABLE_GRID,
                onClick = { onDisplayModeChange(LibraryDisplayMode.COMFORTABLE_GRID) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    item { Spacer(Modifier.height(8.dp)) }
    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DisplayModeCard(
                icon = Icons.Filled.GridView,
                label = "Cover Only",
                isSelected = displayMode == LibraryDisplayMode.COVER_ONLY,
                onClick = { onDisplayModeChange(LibraryDisplayMode.COVER_ONLY) },
                modifier = Modifier.weight(1f),
            )
            DisplayModeCard(
                icon = Icons.Filled.List,
                label = "List",
                isSelected = displayMode == LibraryDisplayMode.LIST,
                onClick = { onDisplayModeChange(LibraryDisplayMode.LIST) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    // ── Columns (grid modes only) ──
    if (displayMode != LibraryDisplayMode.LIST) {
        item {
            Spacer(Modifier.height(20.dp))
            OptionLabel("Columns per row")
        }
        item {
            SegmentedButtons(
                options = listOf("2" to 2, "3" to 3, "4" to 4, "5" to 5),
                selected = columns,
                onSelect = onColumnsChange,
            )
        }
    }

    // ── Title lines ──
    item {
        Spacer(Modifier.height(20.dp))
        OptionLabel("Title lines")
    }
    item {
        SegmentedButtons(
            options = listOf("1" to 1, "2" to 2, "3" to 3),
            selected = titleLines,
            onSelect = onTitleLinesChange,
        )
    }

    // ── Episode badge ──
    // Off uses red theme when selected; Released + Total use primary (green).
    item {
        Spacer(Modifier.height(20.dp))
        OptionLabel("Episode Badge")
    }
    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Order: Off (left, red) → Released (middle) → Total (right)
            listOf(
                "Off" to EpisodeBadgeMode.OFF,
                "Released" to EpisodeBadgeMode.RELEASED,
                "Total" to EpisodeBadgeMode.TOTAL,
            ).forEach { (label, mode) ->
                val isSelected = episodeBadgeMode == mode
                val selectedBg = when (mode) {
                    EpisodeBadgeMode.OFF -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                }
                val selectedFg = when (mode) {
                    EpisodeBadgeMode.OFF -> MaterialTheme.colorScheme.onError
                    else -> MaterialTheme.colorScheme.onPrimary
                }
                Surface(
                    color = if (isSelected) selectedBg
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).clickable { onEpisodeBadgeModeChange(mode) },
                ) {
                    Text(
                        text = label,
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (isSelected) selectedFg
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }

    // ── Episode badge position (compact grid = top only) ──
    if (episodeBadgeMode != EpisodeBadgeMode.OFF) {
        item {
            Spacer(Modifier.height(12.dp))
            OptionLabel("Episode Badge Position")
        }
        item {
            BadgePositionSelector(
                selected = episodeBadgePosition,
                compactMode = displayMode == LibraryDisplayMode.COMPACT_GRID,
                onSelect = onEpisodeBadgePositionChange,
            )
        }
    }

    // ── Score badge ──
    item {
        Spacer(Modifier.height(20.dp))
        OptionLabel("Score Badge")
    }
    item {
        SwitchRow(
            label = "Show score badge",
            checked = showScoreBadge,
            onChange = onShowScoreBadgeChange,
        )
    }

    // ── Score badge position (compact grid = top only) ──
    if (showScoreBadge) {
        item {
            Spacer(Modifier.height(12.dp))
            OptionLabel("Score Badge Position")
        }
        item {
            BadgePositionSelector(
                selected = scoreBadgePosition,
                compactMode = displayMode == LibraryDisplayMode.COMPACT_GRID,
                onSelect = onScoreBadgePositionChange,
            )
        }
    }

    // ── Toggles ──
    item {
        Spacer(Modifier.height(20.dp))
        OptionLabel("Toggles")
    }
    item {
        SwitchRow(
            label = "Show continue watching",
            checked = showContinueWatching,
            onChange = onShowContinueWatchingChange,
        )
    }
    item {
        SwitchRow(
            label = "Show total entries in header",
            checked = showTotalEntries,
            onChange = onShowTotalEntriesChange,
        )
    }
}

// ── Shared sheet components ──

@Composable
private fun OptionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontFamily = RobotoFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.06.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

/**
 * A full-fledged sort-option card — filled background when selected + a check
 * icon on the right.
 */
@Composable
private fun SortOptionCard(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 0.5.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
            )
            if (isSelected) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier.size(20.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * A display-mode visual card — icon on top, label below. Selected = primary
 * border + tinted background.
 */
@Composable
private fun DisplayModeCard(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 0.5.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Badge position selector — 4 quadrants. In compact grid mode, only the top
 * quadrants are available.
 */
@Composable
private fun BadgePositionSelector(
    selected: BadgePosition,
    compactMode: Boolean,
    onSelect: (BadgePosition) -> Unit,
) {
    val positions = if (compactMode) {
        listOf(BadgePosition.TOP_START to "Top Left", BadgePosition.TOP_END to "Top Right")
    } else {
        listOf(
            BadgePosition.TOP_START to "Top Left",
            BadgePosition.TOP_END to "Top Right",
            BadgePosition.BOTTOM_START to "Bottom Left",
            BadgePosition.BOTTOM_END to "Bottom Right",
        )
    }
    // Auto-fallback to TOP_END when current selection isn't in the available set.
    val effectiveSelected = if (positions.any { it.first == selected }) selected
                            else BadgePosition.TOP_END

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        positions.chunked(2).forEach { chunk ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                chunk.forEach { (pos, label) ->
                    val isSelected = effectiveSelected == pos
                    Surface(
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(pos) },
                    ) {
                        Text(
                            text = label,
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentedButtons(
    options: List<Pair<String, Int>>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (label, value) ->
            val isSelected = selected == value
            Surface(
                color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).clickable { onSelect(value) },
            ) {
                Text(
                    text = label,
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * A toggle row with a proper Material3 Switch.
 */
@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ── Grid view ──

@Composable
private fun LibraryGrid(
    anime: List<AniListAnime>,
    gridState: LazyGridState,
    columns: Int,
    titleLines: Int,
    onNavigateToDetails: (Int) -> Unit,
) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(columns.coerceIn(2, 5)),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 4.dp,
            bottom = 90.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(anime, key = { it.id }) { item ->
            LibraryGridCard(item, titleLines, onNavigateToDetails)
        }
    }
}

@Composable
private fun LibraryGridCard(
    anime: AniListAnime,
    titleLines: Int,
    onClick: (Int) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(Motion.DurationShort, easing = FastOutSlowInEasing),
        label = "cardScale",
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
        // Cover image — 2:3 aspect ratio
        AsyncImage(
            model = anime.coverUrl,
            contentDescription = anime.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp)),
        )

        // Title overlay at bottom with gradient (compact grid style)
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
                maxLines = titleLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

// ── List view ──

@Composable
private fun LibraryList(
    anime: List<AniListAnime>,
    listState: LazyListState,
    onNavigateToDetails: (Int) -> Unit,
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 4.dp,
            bottom = 90.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(anime, key = { it.id }) { item ->
            LibraryListRow(item, onNavigateToDetails)
        }
    }
}

@Composable
private fun LibraryListRow(anime: AniListAnime, onClick: (Int) -> Unit) {
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
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            anime.seasonYear?.let { year ->
                Text(
                    "$year",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            anime.averageScore?.let { score ->
                Text(
                    "★ $score",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
