package com.confused.anikuta.feature.animelibrary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.content.LibraryCategory
import com.confused.anikuta.core.common.HapticHelper
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
 * 1. LibraryHeader (pinned) — MAIN title is "X in Library" when showTotalEntries
 *    is on (no separate "Library" title); "Library" otherwise. In selection mode
 *    (D-141) the title becomes "X selected". HeaderActionGroup (search + settings
 *    buttons in ONE combined pill container, surfaceVariant bg, rounded 50, 34dp
 *    icons) sits to the right.
 * 2. Quick options row (D-141, selection mode only) — Select All / Clear / Invert
 *    text buttons. Replaces the category tabs row.
 * 3. Animated search bar (fade in/out when search toggled) using SearchField.
 * 4. Category tabs (D-140, non-selection mode only) — text-based with underline
 *    indicator; smart visibility ("All" only with 2+ populated cats, "Default"
 *    hidden when empty), no "+" button. Count format "[3] Default" (left, square
 *    brackets) when showCategoryCounts is on. A thin 1dp divider sits below them.
 * 5. Compact grid (3-column) with cover + gradient title overlay
 *    OR list view (horizontal rows).
 * 6. ScrollBlurOverlay at the header's bottom edge.
 * 7. Empty state with proper icon.
 * 8. CustomizeSheet — the library settings bottom sheet (Sort + Display & Badges
 *    in 2 tabs, no drag handle, header "Library Settings").
 * 9. D-141 multi-select — long-press an entry to enter selection mode; the bottom
 *    nav pill is replaced by a SelectionBottomBar (Cancel / Category / Delete);
 *    a MultiSelectCategoryPicker AlertDialog and a DeleteSelectedDialog are
 *    shown on demand. Selected cards get a primary border + checkmark badge.
 *
 * D-140: uses [LibraryEntry] (mainId-keyed) instead of AniListAnime — fixes the
 * "Key 0 already used" crash for extension-only entries + 404 nav for them.
 * D-141: tab switches go through ViewModel.selectCategory (reloadFromCache — no
 * network); LaunchedEffect on resume still calls loadLibrary() for fresh data.
 *
 * CORE_RULES §22: smooth animations (300ms FastOutSlowInEasing, scale on press).
 * CORE_RULES §23: reactive state (StateFlow from ViewModel).
 * All text uses fontFamily = RobotoFamily; titles/labels use FontWeight.ExtraBold.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNavigateToDetails: (LibraryEntry) -> Unit,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    // D-140: live reload on resume — when the user navigates back to the
    // library (e.g. after bookmarking from the details page), the list should
    // refresh. LaunchedEffect(Unit) runs once per composition entering the
    // back stack entry (i.e. each time the screen becomes visible again).
    // D-141: still calls loadLibrary() (NOT reloadFromCache) on resume so the
    // user gets fresh AniList data; tab switches go through selectCategory
    // which uses reloadFromCache internally (no network).
    LaunchedEffect(Unit) {
        viewModel.loadLibrary()
    }

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
    // D-140: per-category item counts + show-counts toggle.
    val categoryCounts by viewModel.categoryCounts.collectAsState()
    val showCategoryCounts by viewModel.showCategoryCounts.collectAsState()
    // D-140: total entries (for the header title "{n} in Library").
    val totalEntries by viewModel.totalEntries.collectAsState()
    // D.5: refresh state for pull-to-refresh.
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    // D-138: category tabs state — list of categories, currently selected
    // category (null = "All"), and the category to show delete/rename dialog for.
    val categories by viewModel.categories.collectAsState()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsState()
    val categoryToManage by viewModel.categoryToManage.collectAsState()

    // D-141: Multi-select state.
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedMainIds by viewModel.selectedMainIds.collectAsState()
    val showMultiSelectCategorySheet by viewModel.showMultiSelectCategorySheet.collectAsState()
    val showDeleteConfirmation by viewModel.showDeleteConfirmation.collectAsState()

    // D-143: Sync selection mode to the shared LibrarySelectionMode so AppRoot
    // can replace the bottom nav bar with the selection action bar.
    val librarySelectionMode = LocalLibrarySelectionMode.current
    androidx.compose.runtime.LaunchedEffect(isSelectionMode, selectedMainIds.size) {
        if (isSelectionMode) {
            librarySelectionMode.enter(
                count = selectedMainIds.size,
                cancel = { viewModel.exitSelectionMode() },
                category = { viewModel.showMultiSelectCategorySheet() },
                delete = { viewModel.showDeleteConfirmation() },
            )
        } else {
            librarySelectionMode.exit()
        }
    }

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    var showSearchBar by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    // Pull-to-refresh state — official Material 3 PullToRefreshBox.
    // Cooperates with the inner LazyVerticalGrid / LazyColumn via its own
    // nestedScrollConnection; pull only activates at the top, so there's no
    // spinner on normal upward scroll and no fling jank.
    val ptrState = rememberPullToRefreshState()
    val context = LocalContext.current
    // Fire a haptic exactly once when the pull first crosses the refresh
    // threshold (distanceFraction >= 1f). LaunchedEffect re-runs only on the
    // false → true transition, so it never buzzes continuously.
    // Uses HapticHelper (Vibrator service) for reliability across devices +
    // battery-saver modes.
    val thresholdCrossed = ptrState.distanceFraction >= 1f
    LaunchedEffect(thresholdCrossed) {
        if (thresholdCrossed) {
            HapticHelper.stageCross(context)
        }
    }

    val isList = displayMode == LibraryDisplayMode.LIST
    val collapsed = if (!isList) {
        gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 20
    } else {
        listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 20
    }

    // D-141: click + long-click handlers — depend on selection mode.
    //  - In selection mode: tap toggles selection; long-press does nothing
    //    (we're already in selection mode).
    //  - Outside selection mode: tap navigates to details; long-press enters
    //    selection mode with that entry pre-selected.
    val onEntryClick: (LibraryEntry) -> Unit = { entry ->
        if (isSelectionMode) {
            viewModel.toggleSelection(entry.mainId)
        } else {
            onNavigateToDetails(entry)
        }
    }
    val onEntryLongClick: (LibraryEntry) -> Unit = { entry ->
        if (!isSelectionMode) {
            viewModel.enterSelectionMode(entry.mainId)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Collapsing header (pinned) ──
            // D-141: the MAIN heading changes based on mode:
            //  - Selection mode → "X selected"
            //  - showTotalEntries on → "X in Library" (the count IS the title,
            //    no separate "Library" title above it).
            //  - showTotalEntries off → "Library".
            val headerTitle = when {
                isSelectionMode -> "${selectedMainIds.size} selected"
                showTotalEntries -> "$totalEntries in Library"
                else -> "Library"
            }
            LibraryHeader(
                title = headerTitle,
                subtitle = null,
                collapsed = collapsed,
                actions = {
                    HeaderActionGroup(
                        onSearch = { showSearchBar = !showSearchBar },
                        onSettings = { showSettingsSheet = true },
                    )
                },
            )

            // ── Quick options row (D-142 — selection mode only) ──
            // Select All / Clear / Invert as styled buttons with icons.
            AnimatedVisibility(visible = isSelectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Select All
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { viewModel.selectAll() },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DoneAll,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Select All",
                                fontFamily = RobotoFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    // Clear
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { viewModel.clearSelection() },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Clear",
                                fontFamily = RobotoFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // Invert
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { viewModel.invertSelection() },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SyncAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Invert",
                                fontFamily = RobotoFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

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

            // ── Category tabs (D-138, D-140) — hidden in selection mode ──
            // Smart visibility rules:
            //  - "All" tab only shows when 2+ categories have ≥1 item.
            //  - "Default" (permanent) tab only shows when it has ≥1 item.
            //  - Non-permanent categories always show (the user created them).
            //  - No "+" button — categories are created from the details page
            //    (long-press bookmark), not from the library page.
            //  - D-141: hidden entirely in selection mode (the quick options row
            //    above takes its place).
            //  - D-141: thin 1dp divider below the tabs separates them from the
            //    grid content.
            val categoriesWithItems = categories.count { (categoryCounts[it.id] ?: 0) > 0 }
            val showAllTab = categoriesWithItems >= 2
            val visibleCategories = categories.filter { cat ->
                if (cat.isPermanent) {
                    // Default — hide when empty.
                    (categoryCounts[cat.id] ?: 0) > 0
                } else {
                    // User-created — always visible.
                    true
                }
            }
            if (visibleCategories.isNotEmpty() && !isSelectionMode) {
                Column {
                    CategoryTabsRow(
                        categories = visibleCategories,
                        categoryCounts = categoryCounts,
                        showCounts = showCategoryCounts,
                        showAllTab = showAllTab,
                        selectedCategoryId = selectedCategoryId,
                        onSelectCategory = viewModel::selectCategory,
                        onLongPressCategory = { category ->
                            // Permanent categories ("Default") can't be managed.
                            if (!category.isPermanent) {
                                viewModel.showCategoryManagement(category)
                            }
                        },
                    )
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    )
                }
            }

            // ── Content ──
            // Pull-to-refresh uses the official Material 3 PullToRefreshBox, which
            // cooperates with the inner LazyVerticalGrid / LazyColumn scroll via its
            // own nestedScrollConnection. The pull gesture ONLY activates when the
            // list is at the top AND the user keeps dragging down — no spinner on
            // normal upward scroll, no fling jank.
            //
            // In selection mode, PTR is DISABLED (plain Box) so long-press selection
            // gestures don't conflict with a pull. Scroll state (gridState / listState)
            // is hoisted above the conditional, so scroll position is preserved when
            // entering / leaving selection mode.
            @Composable
            fun BoxScope.libraryContent() {
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

                    is LibraryState.Error -> {
                        // D-223fix: Truncate long error messages + show copy option.
                        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                        val displayMessage = if (s.message.length > 150) {
                            s.message.take(150) + "..."
                        } else {
                            s.message
                        }
                        Box(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    "Failed to load library",
                                    fontFamily = RobotoFamily,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    displayMessage,
                                    fontFamily = RobotoFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (s.message.length > 150) {
                                    Spacer(Modifier.height(8.dp))
                                    androidx.compose.material3.TextButton(onClick = {
                                        clipboardManager.setText(
                                            androidx.compose.ui.text.AnnotatedString(s.message)
                                        )
                                    }) {
                                        Text("Copy error", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                        }
                    }

                    is LibraryState.Success -> {
                        if (s.entries.isEmpty()) {
                            EmptyState(
                                title = "No anime found",
                                description = "Try a different search query.",
                                icon = Icons.Filled.SearchOff,
                            )
                        } else if (!isList) {
                            LibraryGrid(
                                entries = s.entries,
                                gridState = gridState,
                                columns = columns,
                                titleLines = titleLines,
                                isSelectionMode = isSelectionMode,
                                selectedMainIds = selectedMainIds,
                                onClickEntry = onEntryClick,
                                onLongClickEntry = onEntryLongClick,
                                episodeBadgeMode = episodeBadgeMode,
                                episodeBadgePosition = episodeBadgePosition,
                                showScoreBadge = showScoreBadge,
                                scoreBadgePosition = scoreBadgePosition,
                            )
                        } else {
                            LibraryList(
                                entries = s.entries,
                                listState = listState,
                                titleLines = titleLines,
                                isSelectionMode = isSelectionMode,
                                selectedMainIds = selectedMainIds,
                                onClickEntry = onEntryClick,
                                onLongClickEntry = onEntryLongClick,
                            )
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

            if (isSelectionMode) {
                // Selection mode: plain Box, no pull-to-refresh (long-press is the
                // primary gesture here; a pull could conflict with selection).
                Box(modifier = Modifier.fillMaxSize()) {
                    libraryContent()
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refreshLibrary() },
                    state = ptrState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    libraryContent()
                }
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
                showCategoryCounts = showCategoryCounts,
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
                onShowCategoryCountsChange = viewModel::setShowCategoryCounts,
                onTitleLinesChange = viewModel::setTitleLines,
                onSortChange = viewModel::setSort,
                onDismiss = { showSettingsSheet = false },
            )
        }

        // ── Category management dialog (long-press on a category tab) ──
        // categoryToManage is set by ViewModel.showCategoryManagement. For
        // permanent categories the long-press handler bails out early, so this
        // dialog only ever appears for user-created (non-permanent) categories.
        // D-140: delete confirmation offers 3 options — Cancel, Delete (items
        // removed), Move to Default (items moved to Default then category deleted).
        // D-141: itemCount now comes from categoryCounts (not entries.size) so
        // it reflects the TRUE count in the category even if the current view
        // is filtered by search or another category is selected.
        categoryToManage?.let { category ->
            CategoryManagementDialog(
                category = category,
                itemCount = categoryCounts[category.id] ?: 0,
                onRename = { newName ->
                    viewModel.renameCategory(category.id, newName)
                },
                onDelete = {
                    viewModel.deleteCategory(category.id)
                },
                onDeleteMoveToDefault = {
                    viewModel.deleteCategoryAndMoveToDefault(category.id)
                },
                onDismiss = viewModel::dismissCategoryManagement,
            )
        }

        // D-143: The selection bottom bar is now handled by AppRoot — it
        // replaces the nav bar's content directly. No SelectionBottomBar here.

        // ── D-141: Multi-select category picker ──
        // AlertDialog with a checkbox per category — same style as the
        // CategoryPickerSheet on the details page. Tapping a checked category
        // removes all selected entries from it; tapping an unchecked one adds
        // all selected entries to it. getCategoriesForSelected() returns the
        // initial checkbox state (true if ALL selected entries are in that cat).
        if (showMultiSelectCategorySheet) {
            // D-146: Use the ViewModel's membership set (reactive — updates on toggle).
            val membership by viewModel.multiSelectCategoryMembership.collectAsState()
            val selectedMap = categories.associate { cat ->
                cat.id to (cat.id in membership)
            }
            MultiSelectCategoryPicker(
                categories = categories,
                selectedMap = selectedMap,
                onToggle = { categoryId, isChecked ->
                    if (isChecked) {
                        viewModel.removeSelectedFromCategory(categoryId)
                    } else {
                        viewModel.addSelectedToCategory(categoryId)
                    }
                },
                onDismiss = { viewModel.doneMultiSelectCategorySheet() },
            )
        }

        // ── D-141: Delete confirmation ──
        // "Delete X entries from library?" with Cancel (dismissButton) + Delete
        // (confirmButton, error color).
        if (showDeleteConfirmation) {
            DeleteSelectedDialog(
                count = selectedMainIds.size,
                onConfirm = { viewModel.deleteSelected() },
                onDismiss = { viewModel.dismissDeleteConfirmation() },
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Category tabs row (D-138, D-140) — text-based tabs with underline indicator
// ════════════════════════════════════════════════════════════════════════════

/**
 * Horizontal scrollable row of category tabs, shown above the library grid.
 *
 * D-140: redesigned from bubbles/pills to a simple text-based tab style —
 * matches the old project. Selected tab gets primary color + ExtraBold weight
 * + a small underline indicator; unselected tabs use onSurfaceVariant + Medium.
 *
 * Layout: [All]? [Default]? [UserCat1] [UserCat2] ...
 *
 * - "All" tab only renders when [showAllTab] is true (caller decides — should
 *   be true only when 2+ categories have items).
 * - "All" calls [onSelectCategory] with null.
 * - Long-pressing a non-"All" tab fires [onLongPressCategory] — the caller
 *   decides whether to show the management dialog (permanent categories are
 *   skipped there).
 * - D-140: no trailing "+" pill — categories are created from the details
 *   page (long-press bookmark), not from the library page.
 * - D-140: optional item count next to the tab name when [showCounts] is true.
 */
@Composable
private fun CategoryTabsRow(
    categories: List<LibraryCategory>,
    categoryCounts: Map<Long, Int>,
    showCounts: Boolean,
    showAllTab: Boolean,
    selectedCategoryId: Long?,
    onSelectCategory: (Long?) -> Unit,
    onLongPressCategory: (LibraryCategory) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // ── "All" tab (null selection) — only when 2+ categories have items ──
        if (showAllTab) {
            item(key = "all") {
                CategoryTab(
                    label = "All",
                    isSelected = selectedCategoryId == null,
                    onClick = { onSelectCategory(null) },
                    onLongClick = null, // "All" cannot be managed.
                )
            }
        }

        // ── One tab per (already-filtered) category ──
        items(categories, key = { it.id }) { category ->
            val count = categoryCounts[category.id] ?: 0
            // D-142: count on the RIGHT in rounded brackets — "Default (3)".
            val label = if (showCounts) "${category.name} ($count)" else category.name
            CategoryTab(
                label = label,
                isSelected = selectedCategoryId == category.id,
                onClick = { onSelectCategory(category.id) },
                onLongClick = { onLongPressCategory(category) },
            )
        }
    }
}

/**
 * A single text-based category tab with an underline indicator.
 *
 * - Selected: primary color, FontWeight.ExtraBold, 2dp primary underline.
 * - Unselected: onSurfaceVariant, FontWeight.Medium, transparent underline.
 *
 * Long-press is only wired up when [onLongClick] is non-null (i.e. for real
 * categories, not the "All" tab). No background — just text + underline,
 * matching the old project's tab style.
 */
@Composable
private fun CategoryTab(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        // ── Underline indicator (animated alpha via the isSelected state) ──
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(2.dp)
                .background(
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                            else Color.Transparent,
                    shape = CircleShape,
                ),
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Category management dialog (D-138, D-140)
// ════════════════════════════════════════════════════════════════════════════

/**
 * Long-press category tab → management dialog with rename/delete options.
 *
 * Has 3 internal modes:
 *  - MENU: two rows (Rename / Delete) with icons. Dismissed via "Cancel".
 *  - RENAME: OutlinedTextField pre-filled with current name + Save button.
 *  - DELETE_CONFIRM: warning text + 3 buttons — Cancel / Delete (items removed)
 *    / Move to Default (items moved to Default then category deleted).
 *
 * Switching modes is local UI state; the dialog itself stays open until the
 * caller dismisses it (via [onRename]/[onDelete]/[onDeleteMoveToDefault]/[onDismiss]).
 */
@Composable
private fun CategoryManagementDialog(
    category: LibraryCategory,
    itemCount: Int,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDeleteMoveToDefault: () -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(ManageMode.MENU) }
    var renameText by remember(mode) {
        mutableStateOf(if (mode == ManageMode.RENAME) category.name else "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = when (mode) {
                    ManageMode.MENU -> category.name
                    ManageMode.RENAME -> "Rename category"
                    ManageMode.DELETE_CONFIRM -> "Delete category?"
                },
                fontFamily = RobotoFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            when (mode) {
                ManageMode.MENU -> Column(modifier = Modifier.fillMaxWidth()) {
                    // ── Rename option ──
                    ManagementOptionRow(
                        icon = Icons.Filled.Edit,
                        label = "Rename",
                        onClick = { mode = ManageMode.RENAME },
                    )
                    Spacer(Modifier.height(6.dp))
                    // ── Delete option ──
                    ManagementOptionRow(
                        icon = Icons.Filled.Delete,
                        label = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = { mode = ManageMode.DELETE_CONFIRM },
                    )
                }

                ManageMode.RENAME -> Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        placeholder = {
                            Text(
                                "Category name",
                                fontFamily = RobotoFamily,
                                fontSize = 13.sp,
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            fontFamily = RobotoFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }

                ManageMode.DELETE_CONFIRM -> Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = buildString {
                            append("Are you sure you want to delete \"${category.name}\"?")
                            if (itemCount > 0) {
                                append("\n\n$itemCount item")
                                if (itemCount > 1) append("s")
                                append(" in this category will be removed from it.")
                                append(" Use \"Move to Default\" below to keep them.")
                            }
                        },
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // D-141: Move to Default button — full-width, below the
                    // warning text. Only shown if the category has items.
                    if (itemCount > 0) {
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onDeleteMoveToDefault),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Move to Default",
                                    fontFamily = RobotoFamily,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        },
        // ── confirmButton: mode-dependent primary action ──
        // D-141: DELETE_CONFIRM now shows just "Delete" here (Cancel is the
        // dismissButton on the left); "Move to Default" moved into the text
        // content as a full-width button below the warning (only when the
        // category has items).
        confirmButton = {
            when (mode) {
                ManageMode.MENU -> TextButton(onClick = onDismiss) {
                    Text(
                        "Cancel",
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                ManageMode.RENAME -> TextButton(
                    onClick = {
                        val trimmed = renameText.trim()
                        if (trimmed.isNotEmpty() && trimmed != category.name) {
                            onRename(trimmed)
                        } else {
                            onDismiss()
                        }
                    },
                ) {
                    Text(
                        "Save",
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                ManageMode.DELETE_CONFIRM -> TextButton(onClick = onDelete) {
                    Text(
                        "Delete",
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        // dismissButton is always provided; it renders nothing in MENU mode
        // (the confirmButton already shows "Cancel" there).
        dismissButton = {
            if (mode != ManageMode.MENU) {
                TextButton(onClick = onDismiss) {
                    Text(
                        "Cancel",
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

/** Single option row inside the management MENU. */
@Composable
private fun ManagementOptionRow(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = tint,
            )
        }
    }
}

/** Internal mode for [CategoryManagementDialog]. */
private enum class ManageMode { MENU, RENAME, DELETE_CONFIRM }

// ── LibraryHeader: collapsing header with optional subtitle ──

/**
 * Library-specific collapsing header — same animated-collapse behavior as the
 * shared [CollapsingHeader] component, but adds an optional [subtitle] line
 * UNDER the title (e.g. "{n} in Library").
 *
 * D-140: the shared CollapsingHeader has no subtitle slot, and we can't modify
 * the design-system module from here, so this local composable replicates the
 * collapsing animation (32sp → 24sp, animated paddingTop/Bottom) and tacks on
 * a 12sp Medium subtitle underneath when non-null.
 *
 * - Title: RobotoFamily, ExtraBold, onBackground, animated font size.
 * - Subtitle: RobotoFamily, Medium, onSurfaceVariant, 12sp.
 * - Actions slot sits to the right (SpaceBetween) — same as CollapsingHeader.
 */
@Composable
private fun LibraryHeader(
    title: String,
    subtitle: String?,
    collapsed: Boolean,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val targetFontSize = if (collapsed) 24f else 32f
    val fontSize by animateFloatAsState(
        targetValue = targetFontSize,
        animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing),
        label = "libHeaderFontSize",
    )

    val targetPaddingTop = if (collapsed) 2f else 8f
    val paddingTop by animateFloatAsState(
        targetValue = targetPaddingTop,
        animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing),
        label = "libHeaderPaddingTop",
    )
    val targetPaddingBottom = if (collapsed) 0f else 4f
    val paddingBottom by animateFloatAsState(
        targetValue = targetPaddingBottom,
        animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing),
        label = "libHeaderPaddingBottom",
    )

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = paddingTop.dp,
                    bottom = paddingBottom.dp,
                )
                .statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = RobotoFamily,
                    fontSize = fontSize.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.02).sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            actions()
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
    showCategoryCounts: Boolean,
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
    onShowCategoryCountsChange: (Boolean) -> Unit,
    onTitleLinesChange: (Int) -> Unit,
    onSortChange: (LibrarySortType, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // CORE_RULES §22 + user spec: bottom-up sheets cap at 70% of the device's
    // full screen height. LocalConfiguration.screenHeightDp is the actual device
    // height (window insets excluded by edge-to-edge), so this adapts per-device.
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxSheetHeight = screenHeight * 0.70f

    var activeTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Sort", "Display & Badges")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null, // ── No drag handle per spec ──
    ) {
        // FIX: cap the WHOLE sheet content (header + tabs + divider + list) at
        // 70% screen height. Previously heightIn was on the LazyColumn only, so
        // the sheet grew to list(70%) + header/tabs/divider(~120dp) and exceeded
        // the limit. With the cap on the Column, the LazyColumn is constrained by
        // its parent's remaining space → wraps when short, scrolls when tall.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
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
            // No heightIn here — the parent Column's heightIn(max) constrains
            // this LazyColumn to the remaining space, so it scrolls when the
            // tab's content exceeds the 70% cap (Display & Badges) and wraps
            // when it's short (Sort).
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth(),
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
                        showCategoryCounts = showCategoryCounts,
                        onDisplayModeChange = onDisplayModeChange,
                        onColumnsChange = onColumnsChange,
                        onTitleLinesChange = onTitleLinesChange,
                        onEpisodeBadgeModeChange = onEpisodeBadgeModeChange,
                        onEpisodeBadgePositionChange = onEpisodeBadgePositionChange,
                        onShowScoreBadgeChange = onShowScoreBadgeChange,
                        onScoreBadgePositionChange = onScoreBadgePositionChange,
                        onShowContinueWatchingChange = onShowContinueWatchingChange,
                        onShowTotalEntriesChange = onShowTotalEntriesChange,
                        onShowCategoryCountsChange = onShowCategoryCountsChange,
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
    showCategoryCounts: Boolean,
    onDisplayModeChange: (LibraryDisplayMode) -> Unit,
    onColumnsChange: (Int) -> Unit,
    onTitleLinesChange: (Int) -> Unit,
    onEpisodeBadgeModeChange: (EpisodeBadgeMode) -> Unit,
    onEpisodeBadgePositionChange: (BadgePosition) -> Unit,
    onShowScoreBadgeChange: (Boolean) -> Unit,
    onScoreBadgePositionChange: (BadgePosition) -> Unit,
    onShowContinueWatchingChange: (Boolean) -> Unit,
    onShowTotalEntriesChange: (Boolean) -> Unit,
    onShowCategoryCountsChange: (Boolean) -> Unit,
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
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))
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
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))
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
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))
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
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))
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
    item {
        SwitchRow(
            label = "Show category counts on tabs",
            checked = showCategoryCounts,
            onChange = onShowCategoryCountsChange,
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
    entries: List<LibraryEntry>,
    gridState: LazyGridState,
    columns: Int,
    titleLines: Int,
    isSelectionMode: Boolean,
    selectedMainIds: Set<String>,
    onClickEntry: (LibraryEntry) -> Unit,
    onLongClickEntry: (LibraryEntry) -> Unit,
    episodeBadgeMode: EpisodeBadgeMode = EpisodeBadgeMode.OFF,
    episodeBadgePosition: BadgePosition = BadgePosition.TOP_END,
    showScoreBadge: Boolean = false,
    scoreBadgePosition: BadgePosition = BadgePosition.TOP_START,
) {
    // D-141: in selection mode, reserve extra bottom space for the action bar.
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(columns.coerceIn(2, 5)),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 4.dp,
            bottom = if (isSelectionMode) 160.dp else 90.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(entries, key = { it.mainId }) { item ->
            LibraryGridCard(
                anime = item,
                titleLines = titleLines,
                isSelectionMode = isSelectionMode,
                isSelected = item.mainId in selectedMainIds,
                onClick = onClickEntry,
                onLongClick = onLongClickEntry,
                episodeBadgeMode = episodeBadgeMode,
                episodeBadgePosition = episodeBadgePosition,
                showScoreBadge = showScoreBadge,
                scoreBadgePosition = scoreBadgePosition,
            )
        }
    }
}

@Composable
private fun LibraryGridCard(
    anime: LibraryEntry,
    titleLines: Int,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: (LibraryEntry) -> Unit,
    onLongClick: (LibraryEntry) -> Unit,
    episodeBadgeMode: EpisodeBadgeMode = EpisodeBadgeMode.OFF,
    episodeBadgePosition: BadgePosition = BadgePosition.TOP_END,
    showScoreBadge: Boolean = false,
    scoreBadgePosition: BadgePosition = BadgePosition.TOP_START,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(Motion.DurationShort, easing = FastOutSlowInEasing),
        label = "cardScale",
    )

    // Fade unselected cards to 40% opacity while selection mode is active, so the
    // selected items visually pop and the rest recede. Animated for a smooth fade.
    val cardAlpha by animateFloatAsState(
        targetValue = if (isSelectionMode && !isSelected) 0.4f else 1f,
        animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing),
        label = "cardAlpha",
    )

    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = cardAlpha }
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onClick(anime) },
                onLongClick = { onLongClick(anime) },
            ),
    ) {
        // Cover image — 2:3 aspect ratio
        AsyncImage(
            model = anime.coverUrl,
            contentDescription = anime.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp)),
        )

        // D-242-fix10: Cover badges — edge-to-edge, side-by-side (no overlap).
        // Badges at the same corner are rendered in a Row so they don't overlap.
        // Episode badge uses releasedEpisodes (actual aired) or episodes (planned total).
        // Score badge uses averageScore.
        // Audio badge uses audioAvailability (SUB/DUB/HSUB).
        if (!isSelectionMode) {
            // Top-start badges
            val topStartBadges = mutableListOf<Pair<String, Pair<Color, Color>>>()
            // Top-end badges
            val topEndBadges = mutableListOf<Pair<String, Pair<Color, Color>>>()

            // Build episode badge text
            val epText = when (episodeBadgeMode) {
                EpisodeBadgeMode.TOTAL -> anime.episodes?.let { "EP $it" }
                EpisodeBadgeMode.RELEASED -> (anime.releasedEpisodes ?: anime.episodes)?.let { "EP $it" }
                EpisodeBadgeMode.OFF -> null
            }
            if (epText != null) {
                val colors = MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                if (episodeBadgePosition == BadgePosition.TOP_START) topStartBadges.add(epText to colors)
                else if (episodeBadgePosition == BadgePosition.TOP_END) topEndBadges.add(epText to colors)
            }
            // Score badge
            if (showScoreBadge && anime.averageScore != null && anime.averageScore > 0) {
                val scoreText = "★ ${anime.averageScore}"
                val colors = MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                if (scoreBadgePosition == BadgePosition.TOP_START) topStartBadges.add(scoreText to colors)
                else if (scoreBadgePosition == BadgePosition.TOP_END) topEndBadges.add(scoreText to colors)
            }
            // Audio badge (SUB/DUB)
            val audio = anime.audioAvailability
            if (audio != null && audio.hasAny) {
                val audioText = audio.labels.joinToString("·")
                val colors = MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
                // Audio badge goes to whichever corner has fewer badges (or TOP_END by default)
                if (topStartBadges.size <= topEndBadges.size) {
                    topStartBadges.add(audioText to colors)
                } else {
                    topEndBadges.add(audioText to colors)
                }
            }

            // Render top-start badges (side-by-side in a Row)
            if (topStartBadges.isNotEmpty()) {
                CoverBadgeRow(
                    badges = topStartBadges,
                    position = BadgePosition.TOP_START,
                )
            }
            // Render top-end badges (side-by-side in a Row)
            if (topEndBadges.isNotEmpty()) {
                CoverBadgeRow(
                    badges = topEndBadges,
                    position = BadgePosition.TOP_END,
                )
            }
        }

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
                text = anime.title,
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = titleLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }

        // ── D-141: Selection border overlay (drawn on top of content) ──
        if (isSelected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp),
                    ),
            )
        }

        // ── D-141: Selection checkbox badge (top-right, in selection mode) ──
        // Filled primary circle with a check icon when selected; semi-transparent
        // surface circle (empty) when not selected — so the user can see that
        // tapping will select.
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

// ── List view ──

@Composable
private fun LibraryList(
    entries: List<LibraryEntry>,
    listState: LazyListState,
    titleLines: Int,
    isSelectionMode: Boolean,
    selectedMainIds: Set<String>,
    onClickEntry: (LibraryEntry) -> Unit,
    onLongClickEntry: (LibraryEntry) -> Unit,
) {
    // D-141: in selection mode, reserve extra bottom space for the action bar.
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 4.dp,
            bottom = if (isSelectionMode) 160.dp else 90.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(entries, key = { it.mainId }) { item ->
            LibraryListRow(
                anime = item,
                isSelectionMode = isSelectionMode,
                isSelected = item.mainId in selectedMainIds,
                onClick = onClickEntry,
                onLongClick = onLongClickEntry,
                titleLines = titleLines,
            )
        }
    }
}

@Composable
private fun LibraryListRow(
    anime: LibraryEntry,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: (LibraryEntry) -> Unit,
    onLongClick: (LibraryEntry) -> Unit,
    titleLines: Int = 1,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(Motion.DurationShort, easing = FastOutSlowInEasing),
        label = "rowScale",
    )

    // Fade unselected rows to 40% opacity while selection mode is active (mirrors
    // the grid card behaviour for visual consistency).
    val rowAlpha by animateFloatAsState(
        targetValue = if (isSelectionMode && !isSelected) 0.4f else 1f,
        animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing),
        label = "rowAlpha",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = rowAlpha }
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isSelected) Modifier.background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                ) else Modifier
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onClick(anime) },
                onLongClick = { onLongClick(anime) },
            )
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Cover thumbnail (with optional D-141 selection badge in the corner)
        Box {
            AsyncImage(
                model = anime.coverUrl,
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(56.dp)
                    .height(80.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                anime.title,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = titleLines,
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

// ════════════════════════════════════════════════════════════════════════════
//  D-141: Multi-select — bottom action bar + category picker + delete dialog
// ════════════════════════════════════════════════════════════════════════════

/**
 * Bottom action bar shown in selection mode — replaces the bottom nav pill.
 *
 * Three text buttons in a SpaceBetween Row:
 *  - Cancel (left, onSurfaceVariant) — exits selection mode.
 *  - Category (center, primary) — opens the multi-select category picker.
 *  - Delete (right, error) — opens the delete confirmation dialog.
 *
 * NOTE: the parent MainActivity still renders the floating nav pill on top of
 * LibraryScreen content; this bar is positioned with `padding(bottom = 90.dp)`
 * (passed by the caller) so it sits ABOVE the nav pill's reserved area and
 * stays fully visible/usable.
 */
@Composable
private fun SelectionBottomBar(
    onCancel: () -> Unit,
    onCategory: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // D-142: Replaces the bottom nav bar with an opaque surface that covers it.
    // Uses navigationBarsPadding to respect the system nav bar.
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Cancel — left
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onCancel() },
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Cancel",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    "Cancel",
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Category — center
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onCategory() },
            ) {
                Icon(
                    imageVector = Icons.Filled.Category,
                    contentDescription = "Category",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    "Category",
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            // Delete — right
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onDelete() },
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    "Delete",
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Multi-select category picker — AlertDialog with a checkbox per category.
 *
 * Mirrors the CategoryPickerSheet style from the details page (surface bg,
 * 20dp rounded, LazyColumn of checkbox rows). Tapping a checked category
 * removes ALL selected entries from it; tapping an unchecked one adds ALL
 * selected entries to it.
 *
 * No "New category" button here (unlike CategoryPickerSheet) — the user can
 * create categories from the details page; creating one here wouldn't auto-add
 * the selected items anyway.
 *
 * @param selectedMap categoryId → true if ALL selected entries are in that
 *  category (checkbox checked), false otherwise.
 */
@Composable
private fun MultiSelectCategoryPicker(
    categories: List<LibraryCategory>,
    selectedMap: Map<Long, Boolean>,
    onToggle: (categoryId: Long, isChecked: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Categories",
                fontFamily = RobotoFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                ) {
                    items(categories, key = { it.id }) { category ->
                        val isChecked = selectedMap[category.id] ?: false
                        Surface(
                            color = if (isChecked)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(category.id, isChecked) },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Checkbox indicator
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(
                                            if (isChecked) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isChecked) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = category.name,
                                    fontFamily = RobotoFamily,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Done",
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

/**
 * Delete confirmation dialog for multi-select — "Delete X entries from
 * library?" with Cancel (dismissButton) + Delete (confirmButton, error).
 */
@Composable
private fun DeleteSelectedDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Delete from library?",
                fontFamily = RobotoFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Text(
                text = "Delete $count ${if (count == 1) "entry" else "entries"} from library?",
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    "Delete",
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Cancel",
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

/**
 * D-242-fix10: Renders multiple badges side-by-side in a single Row at a corner.
 * Edge-to-edge — sits flush with the cover corner.
 * Each badge has its own color (containerColor, contentColor).
 * Compact: 8sp font, 1dp vertical padding.
 */
@Composable
private fun BoxScope.CoverBadgeRow(
    badges: List<Pair<String, Pair<Color, Color>>>,
    position: BadgePosition,
) {
    val alignment = when (position) {
        BadgePosition.TOP_START -> Alignment.TopStart
        BadgePosition.TOP_END -> Alignment.TopEnd
        BadgePosition.BOTTOM_START -> Alignment.BottomStart
        BadgePosition.BOTTOM_END -> Alignment.BottomEnd
    }
    val shape = when (position) {
        BadgePosition.TOP_START -> RoundedCornerShape(topStart = 12.dp, topEnd = 0.dp, bottomStart = 0.dp, bottomEnd = 6.dp)
        BadgePosition.TOP_END -> RoundedCornerShape(topStart = 0.dp, topEnd = 12.dp, bottomStart = 6.dp, bottomEnd = 0.dp)
        BadgePosition.BOTTOM_START -> RoundedCornerShape(topStart = 0.dp, topEnd = 6.dp, bottomStart = 12.dp, bottomEnd = 0.dp)
        BadgePosition.BOTTOM_END -> RoundedCornerShape(topStart = 6.dp, topEnd = 0.dp, bottomStart = 0.dp, bottomEnd = 12.dp)
    }
    Surface(
        modifier = Modifier.align(alignment),
        color = Color.Transparent,  // transparent container — each badge has its own color
        shape = shape,
    ) {
        Row {
            badges.forEach { (text, colors) ->
                Surface(
                    color = colors.first,
                    shape = RoundedCornerShape(0.dp),  // square between badges
                ) {
                    Text(
                        text = text,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.second,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * D-242-fix9: Edge-to-edge cover badge — sits flush with the cover corner.
 * Matches the cover's 12dp corner radius on the outer corner; flat on the inner side.
 * Theme-adaptive: uses colorScheme container/content colors (not hardcoded).
 * Compact: minimal padding to avoid taking more height than needed.
 * NOTE: Must be called inside a BoxScope (the parent Box provides the alignment).
 */
@Composable
private fun BoxScope.CoverBadge(
    text: String,
    position: BadgePosition,
    containerColor: Color,
    contentColor: Color,
) {
    val alignment = when (position) {
        BadgePosition.TOP_START -> Alignment.TopStart
        BadgePosition.TOP_END -> Alignment.TopEnd
        BadgePosition.BOTTOM_START -> Alignment.BottomStart
        BadgePosition.BOTTOM_END -> Alignment.BottomEnd
    }
    // Edge-to-edge: NO padding from the corner. The badge clips to match the
    // cover's 12dp rounded corner on the outer side, flat on the inner side.
    Surface(
        modifier = Modifier.align(alignment),
        color = containerColor,
        shape = when (position) {
            BadgePosition.TOP_START -> RoundedCornerShape(topStart = 12.dp, topEnd = 0.dp, bottomStart = 0.dp, bottomEnd = 8.dp)
            BadgePosition.TOP_END -> RoundedCornerShape(topStart = 0.dp, topEnd = 12.dp, bottomStart = 8.dp, bottomEnd = 0.dp)
            BadgePosition.BOTTOM_START -> RoundedCornerShape(topStart = 0.dp, topEnd = 8.dp, bottomStart = 12.dp, bottomEnd = 0.dp)
            BadgePosition.BOTTOM_END -> RoundedCornerShape(topStart = 8.dp, topEnd = 0.dp, bottomStart = 0.dp, bottomEnd = 12.dp)
        },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1,
        )
    }
}
