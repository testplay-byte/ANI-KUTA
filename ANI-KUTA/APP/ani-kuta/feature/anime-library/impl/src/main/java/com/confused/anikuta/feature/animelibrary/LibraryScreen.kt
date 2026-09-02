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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
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
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import android.graphics.Bitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.bitmapConfig
import coil3.request.crossfade
import com.confused.anikuta.core.designsystem.animation.coverSharedElement  // D-320
import com.confused.anikuta.core.designsystem.animation.libraryCoverKey  // D-328
import org.koin.compose.koinInject  // D-320: prefs gate for the cover transition
import com.confused.anikuta.core.content.LibraryCategory
import com.confused.anikuta.core.common.HapticHelper
import com.confused.anikuta.core.designsystem.badge.PointedSide
import com.confused.anikuta.core.designsystem.badge.PointedTagShape
import com.confused.anikuta.core.designsystem.badge.rememberBadgeColorScheme
import com.confused.anikuta.core.designsystem.component.EmptyState
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.component.SearchField
import com.confused.anikuta.core.designsystem.theme.LocalCardDescriptionColor
import com.confused.anikuta.core.designsystem.theme.LocalCardHeadingColor
import com.confused.anikuta.core.designsystem.theme.LocalHeadingColor
import com.confused.anikuta.core.designsystem.theme.Motion
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.abs

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
// ── D-291: reveal-once cover animation infrastructure ─────────────────────

/**
 * D-291: the reveal-once animation context threaded from the screen down to
 * every [LibraryCoverImage] cell.
 *
 * Device feedback on v0.2.55: "the loading of the images is not smooth. All
 * the images just outright jump into it … I wanted a smoother experience for
 * the images to come into view, like they would all show up one by one with a
 * smoother animation … The speed of them will be faster as the users scroll
 * faster … if I scroll to the very bottom and then back to the very top it
 * should not be loading any images."
 *
 * - [velocity] — 0f (idle) … 1f (fast fling); sampled NON-reactively when a
 *   load completes, so a fast scroll yields snappy ~70ms fades and a calm
 *   view yields gentle ~240ms fades (reading it inside the cell would
 *   recompose every cell on every scroll frame — exactly the jank D-287
 *   removed).
 * - [isRevealed] / [markRevealed] — the once-only gate (backed by the VM's
 *   revealedCoverKeys, which survives tab switches and is cleared only by
 *   pull-to-refresh).
 */
internal class CoverRevealController(
    val velocity: State<Float>,
    val isRevealed: (String) -> Boolean,
    val markRevealed: (String) -> Unit,
)

/**
 * D-291: tracks how fast the library is scrolling (0f idle … 1f hard fling).
 *
 * Task 64 (round 24): [position] now returns the FIRST VISIBLE ITEM INDEX
 * only. The old signal (index * 4096 + scrollOffset) changed on EVERY scroll
 * FRAME, so the snapshotFlow below emitted (and ran its collect math on the
 * main dispatcher) at frame rate for the whole duration of every scroll —
 * pure per-frame overhead with no benefit, since only a coarse velocity
 * estimate is needed for the fade duration. The index-only signal emits once
 * per item crossed — dozens of times fewer emissions, still plenty of
 * samples during a fling.
 *
 * The signal feeds an EMA speed estimate; a 150ms decay loop fades the
 * factor back to idle after scrolling stops, so covers that finish loading
 * AFTER a fling ends still get the calm (slow) fade — "if the user jumps
 * into some area directly then it will slow down that area smoothly".
 */
@Composable
private fun rememberScrollVelocityFactor(position: () -> Int): State<Float> {
    val factor = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(position) {
        var lastSignal = position()
        var lastTime = 0L
        snapshotFlow { position() }.collect { signal ->
            val now = System.nanoTime()
            if (lastTime == 0L) {
                lastTime = now
                lastSignal = signal
                return@collect
            }
            val dtMs = (now - lastTime) / 1_000_000f
            if (dtMs >= 1f && signal != lastSignal) {
                // items per ms; ~0.02/ms (one item per ~50ms) reads as a hard fling.
                val speed = abs(signal - lastSignal) / dtMs
                val instant = (speed / 0.02f).coerceIn(0f, 1f)
                factor.floatValue = factor.floatValue * 0.4f + instant * 0.6f
                lastSignal = signal
                lastTime = now
            }
        }
    }
    // Decay toward idle when no scroll signals arrive (scroll stopped).
    LaunchedEffect(position) {
        while (true) {
            delay(150)
            if (factor.floatValue > 0.01f) {
                factor.floatValue *= 0.5f
            }
        }
    }
    return factor
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNavigateToDetails: (LibraryEntry) -> Unit,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    // D-140/D-290: live reload on resume — when the user navigates back to the
    // library (e.g. after bookmarking from the details page), the list should
    // refresh. LaunchedEffect(Unit) runs once per composition entering the
    // back stack entry (i.e. each time the screen becomes visible again).
    // With D-290's SINGLE-emission loads + structural-equality conflation, an
    // unchanged library produces an equal Success state that StateFlow DROPS —
    // the resume refresh is now truly invisible (no grid teardown, no flash,
    // no scroll disturbance), while genuinely changed data still swaps in.
    LaunchedEffect(Unit) {
        viewModel.loadLibrary()
    }

    // ── Task 62 (round 22 — M3, the root recomposition amplifier) ──────────
    // This root used to run 34 collectAsState() calls at the top of a
    // ~4,300-line file: ANY of them emitting (a keystroke, a selection tap, a
    // settings toggle, the PTR spinner, the category counts) re-executed the
    // ENTIRE root body. The reads now live in the LOWEST composable that
    // consumes them:
    //  • searchQuery            → the search-bar section below;
    //  • the ~19 customize-sheet states → [LibraryCustomizeSheetHost] (only
    //    composed while the sheet is open — a settings toggle now recomposes
    //    the sheet, not this root);
    //  • the chips states        → [LibraryCategorySection];
    //  • the grid/list settings  → [LibraryGrid]/[LibraryList] themselves;
    //  • isRefreshing            → [LibraryPullRefreshArea];
    //  • the dialog states       → [LibraryDialogsHost].
    // The root keeps only the states that genuinely drive its structure: the
    // content state, the display mode (grid/list branch), the header count,
    // and the selection state.
    val state by viewModel.state.collectAsState()
    val displayMode by viewModel.displayMode.collectAsState()
    // D-140: total entries (for the header title "{n} in Library").
    val showTotalEntries by viewModel.showTotalEntries.collectAsState()
    val totalEntries by viewModel.totalEntries.collectAsState()

    // D-141: Multi-select state.
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedMainIds by viewModel.selectedMainIds.collectAsState()

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

    // D-286: scroll states live in the (Activity-scoped) ViewModel so they
    // SURVIVE tab switches — the old rememberLazyGridState()/rememberLazyListState()
    // died with the composable when the user left the Library tab, snapping the
    // grid back to the top on every return. Coming back now shows the list
    // exactly where the user left it.
    val gridState = viewModel.gridState
    val listState = viewModel.listState

    // D-291: scroll-velocity tracker feeding the reveal-once cover fades.
    // The position signal follows whichever list is actually on screen
    // (comfortable masonry / list / grid). remember(displayMode) keeps the
    // lambda identity STABLE between recompositions (a fresh lambda every
    // recomposition would restart the tracker's LaunchedEffects), while still
    // switching signals when the display mode changes. Task 64: INDEX-ONLY
    // signal (no scrollOffset term — see rememberScrollVelocityFactor).
    val revealPositionSignal = remember(displayMode) {
        when (displayMode) {
            LibraryDisplayMode.COMFORTABLE_GRID -> ({
                viewModel.staggeredState.firstVisibleItemIndex
            })
            LibraryDisplayMode.LIST -> ({
                viewModel.listState.firstVisibleItemIndex
            })
            else -> ({
                viewModel.gridState.firstVisibleItemIndex
            })
        }
    }
    val revealVelocity = rememberScrollVelocityFactor(position = revealPositionSignal)
    // D-291: one shared controller threaded to every cover cell (see
    // [CoverRevealController]).
    val revealController = remember(viewModel) {
        CoverRevealController(
            velocity = revealVelocity,
            isRevealed = { viewModel.isCoverRevealed(it) },
            markRevealed = { viewModel.markCoverRevealed(it) },
        )
    }

    var showSearchBar by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    // D-242-fix21: Hoisted activeTab to LibraryScreen so it persists across
    // CustomizeSheet open/close (the sheet leaves composition when dismissed,
    // which would reset rememberSaveable inside it).
    var customizeSheetActiveTab by rememberSaveable { mutableIntStateOf(0) }

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
    // Task 62 (round 22 — M1): the threshold is now read inside a
    // snapshotFlow + distinctUntilChanged. The old composition-scope read
    // `val thresholdCrossed = ptrState.distanceFraction >= 1f` subscribed the
    // ENTIRE root composable (this ~4,300-line screen) to EVERY pull-drag
    // frame — a whole-screen recomposition per frame; the flow reads it in
    // the observer's scope instead.
    LaunchedEffect(ptrState) {
        snapshotFlow { ptrState.distanceFraction >= 1f }
            .distinctUntilChanged()
            .collect { crossed ->
                if (crossed) {
                    HapticHelper.stageCross(context)
                }
            }
    }

    val isList = displayMode == LibraryDisplayMode.LIST
    // D-269: wrap in derivedStateOf so the parent only recomposes when collapsed
    // FLIPS (true<->false), not on every scroll frame. Without this, every scroll
    // frame reads gridState/listState in the composition body -> parent recomposes
    // -> re-allocates onEntryClick/onEntryLongClick lambdas -> children can't skip
    // -> all per-card anti-patterns re-run (compounds on fling). THE primary scroll-
    // perf fix.
    val collapsed by remember(isList) {
        derivedStateOf {
            if (!isList) {
                gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 20
            } else {
                listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 20
            }
        }
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
                // Task 62 (M3): the query is collected HERE — every keystroke
                // re-executes only this small section (the old root-level read
                // recomposed the entire screen per character).
                val searchQuery by viewModel.searchQuery.collectAsState()
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
            // Task 62 (M3): the chips states (categories, counts, the
            // show-counts toggle, the selection) are collected inside
            // [LibraryCategorySection] — count changes no longer recompose
            // this root. See its KDoc for the visibility rules.
            LibraryCategorySection(
                viewModel = viewModel,
                isSelectionMode = isSelectionMode,
            )

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
                            // Task 62 (M3): the ~15 grid settings (columns,
                            // title lines, badges, borders…) are collected
                            // INSIDE LibraryGrid — a settings toggle now
                            // recomposes the grid, not this root.
                            LibraryGrid(
                                viewModel = viewModel,
                                entries = s.entries,
                                gridState = gridState,
                                staggeredState = viewModel.staggeredState,
                                reveal = revealController,
                                displayMode = displayMode,
                                isSelectionMode = isSelectionMode,
                                selectedMainIds = selectedMainIds,
                                onClickEntry = onEntryClick,
                                onLongClickEntry = onEntryLongClick,
                            )
                        } else {
                            LibraryList(
                                viewModel = viewModel,
                                entries = s.entries,
                                listState = listState,
                                reveal = revealController,
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
                // Task 62 (M3): isRefreshing is collected inside the PTR area —
                // the spinner's lifecycle no longer recomposes this root.
                LibraryPullRefreshArea(
                    viewModel = viewModel,
                    ptrState = ptrState,
                ) {
                    libraryContent()
                }
            }
        }

        // ── Library settings bottom sheet ──
        if (showSettingsSheet) {
            // Task 62 (M3): the ~19 customize-sheet-only states are collected
            // inside the host — toggling a setting re-executes the sheet, not
            // this root.
            LibraryCustomizeSheetHost(
                viewModel = viewModel,
                displayMode = displayMode,
                activeTab = customizeSheetActiveTab,
                onActiveTabChange = { customizeSheetActiveTab = it },
                onDismiss = { showSettingsSheet = false },
            )
        }

        // ── Dialogs (category management, multi-select picker, delete
        // confirmation) ──
        // Task 62 (M3): their states (categoryToManage, the picker's
        // visibility + membership, the delete confirmation) are collected
        // inside [LibraryDialogsHost] — opening/closing a dialog no longer
        // recomposes this root. See the host's KDoc for the dialog behaviors.
        LibraryDialogsHost(
            viewModel = viewModel,
            selectedCount = selectedMainIds.size,
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Task 62 (round 22 — M3): the leaf state-owner composables
// ════════════════════════════════════════════════════════════════════════════

/**
 * Task 62 (round 22 — M3): the category chips section. Collects its own
 * states (categories, per-category counts, the show-counts toggle, the
 * selection) so a count change recomposes only this section.
 *
 * Smart visibility rules (D-138/D-140, unchanged):
 *  - "All" tab only shows when 2+ categories have ≥1 item.
 *  - "Default" (permanent) tab only shows when it has ≥1 item.
 *  - Non-permanent categories always show (the user created them).
 *  - No "+" button — categories are created from the details page
 *    (long-press bookmark), not from the library page.
 *  - D-141: hidden entirely in selection mode (the quick options row above
 *    takes its place); thin 1dp divider below the tabs + an 8dp spacer before
 *    the results (the round-21 spec).
 */
@Composable
private fun LibraryCategorySection(
    viewModel: LibraryViewModel,
    isSelectionMode: Boolean,
) {
    val categories by viewModel.categories.collectAsState()
    val categoryCounts by viewModel.categoryCounts.collectAsState()
    val showCategoryCounts by viewModel.showCategoryCounts.collectAsState()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsState()

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
            // Task 61 (round 21): breathing room between the category section
            // and the results below (the round-21 spec: "add some spacing
            // between the category section and the bottom results themselves").
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Task 62 (round 22 — M3): the pull-to-refresh area. Collects [isRefreshing]
 * internally so the spinner's lifecycle recomposes only this wrapper. The
 * [content] receiver is [BoxScope] — the caller's local libraryContent()
 * composable is a BoxScope extension (it aligns the scroll-blur overlay to
 * the top center) and PullToRefreshBox's own content scope provides the
 * same receiver.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryPullRefreshArea(
    viewModel: LibraryViewModel,
    ptrState: androidx.compose.material3.pulltorefresh.PullToRefreshState,
    content: @Composable BoxScope.() -> Unit,
) {
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refreshLibrary() },
        state = ptrState,
        modifier = Modifier.fillMaxSize(),
    ) {
        content()
    }
}

/**
 * Task 62 (round 22 — M3): the dialogs host. Collects the category-management
 * target, the multi-select picker's visibility + membership + categories, and
 * the delete confirmation — opening/closing any of them recomposes only this
 * host.
 *
 * Behaviors (unchanged, see the individual dialogs):
 *  - CategoryManagementDialog — delete offers Cancel / Delete / Move to
 *    Default; itemCount comes from categoryCounts (the TRUE count).
 *  - MultiSelectCategoryPicker — a checkbox per category; tapping a checked
 *    category removes all selected entries from it, tapping an unchecked one
 *    adds them; Done closes + exits selection mode.
 *  - DeleteSelectedDialog — "Delete X entries from library?".
 */
@Composable
private fun LibraryDialogsHost(
    viewModel: LibraryViewModel,
    selectedCount: Int,
) {
    val categoryToManage by viewModel.categoryToManage.collectAsState()

    categoryToManage?.let { category ->
        val categoryCounts by viewModel.categoryCounts.collectAsState()
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

    val showMultiSelectCategorySheet by viewModel.showMultiSelectCategorySheet.collectAsState()
    if (showMultiSelectCategorySheet) {
        // D-146: Use the ViewModel's membership set (reactive — updates on toggle).
        val membership by viewModel.multiSelectCategoryMembership.collectAsState()
        val categories by viewModel.categories.collectAsState()
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

    val showDeleteConfirmation by viewModel.showDeleteConfirmation.collectAsState()
    if (showDeleteConfirmation) {
        DeleteSelectedDialog(
            count = selectedCount,
            onConfirm = { viewModel.deleteSelected() },
            onDismiss = { viewModel.dismissDeleteConfirmation() },
        )
    }
}

/**
 * Task 62 (round 22 — M3): the customize-sheet host. The ~19 settings states
 * the sheet consumes are collected HERE (the host only composes while the
 * sheet is open) — every toggle in the sheet re-executes the sheet's own
 * subtree, never the library root. The values + callbacks are forwarded to
 * [CustomizeSheet] exactly as the root used to pass them.
 */
@Composable
private fun LibraryCustomizeSheetHost(
    viewModel: LibraryViewModel,
    displayMode: LibraryDisplayMode,
    activeTab: Int,
    onActiveTabChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val columns by viewModel.columns.collectAsState()
    val titleLines by viewModel.titleLines.collectAsState()
    val episodeBadgeMode by viewModel.episodeBadgeMode.collectAsState()
    val showScoreBadge by viewModel.showScoreBadge.collectAsState()
    val showContinueWatching by viewModel.showContinueWatching.collectAsState()
    val showTotalEntries by viewModel.showTotalEntries.collectAsState()
    val showCategoryCounts by viewModel.showCategoryCounts.collectAsState()
    val sortType by viewModel.sortType.collectAsState()
    val sortAscending by viewModel.sortAscending.collectAsState()
    val releasedAudioFilter by viewModel.releasedAudioFilter.collectAsState()
    val releasedUnwatchedOnly by viewModel.releasedUnwatchedOnly.collectAsState()
    val coverBorderEnabled by viewModel.coverBorderEnabled.collectAsState()
    val coverBorderColor by viewModel.coverBorderColor.collectAsState()
    val coverBorderWidth by viewModel.coverBorderWidth.collectAsState()
    val showAllCaughtUpTag by viewModel.showAllCaughtUpTag.collectAsState()
    val listDensity by viewModel.listDensity.collectAsState()
    val listTitlePosition by viewModel.listTitlePosition.collectAsState()
    val comfortableBorderMode by viewModel.comfortableBorderMode.collectAsState()
    val hideTitlesInComfortable by viewModel.hideTitlesInComfortable.collectAsState()

    CustomizeSheet(
        displayMode = displayMode,
        columns = columns,
        titleLines = titleLines,
        episodeBadgeMode = episodeBadgeMode,
        showScoreBadge = showScoreBadge,
        showContinueWatching = showContinueWatching,
        showTotalEntries = showTotalEntries,
        showCategoryCounts = showCategoryCounts,
        sortType = sortType,
        sortAscending = sortAscending,
        releasedAudioFilter = releasedAudioFilter,
        releasedUnwatchedOnly = releasedUnwatchedOnly,
        coverBorderEnabled = coverBorderEnabled,
        coverBorderColor = coverBorderColor,
        coverBorderWidth = coverBorderWidth,
        showAllCaughtUpTag = showAllCaughtUpTag,
        listDensity = listDensity,
        listTitlePosition = listTitlePosition,
        onDisplayModeChange = viewModel::setDisplayMode,
        onColumnsChange = viewModel::setColumns,
        onEpisodeBadgeModeChange = viewModel::setEpisodeBadgeMode,
        onShowScoreBadgeChange = viewModel::setShowScoreBadge,
        onShowContinueWatchingChange = viewModel::setShowContinueWatching,
        onShowTotalEntriesChange = viewModel::setShowTotalEntries,
        onShowCategoryCountsChange = viewModel::setShowCategoryCounts,
        onTitleLinesChange = viewModel::setTitleLines,
        onSortChange = viewModel::setSort,
        onReleasedAudioFilterChange = viewModel::setReleasedAudioFilter,
        onReleasedUnwatchedOnlyChange = viewModel::setReleasedUnwatchedOnly,
        onCoverBorderEnabledChange = viewModel::setCoverBorderEnabled,
        onCoverBorderColorChange = viewModel::setCoverBorderColor,
        onCoverBorderWidthChange = viewModel::setCoverBorderWidth,
        onShowAllCaughtUpTagChange = viewModel::setShowAllCaughtUpTag,
        onListDensityChange = viewModel::setListDensity,
        onListTitlePositionChange = viewModel::setListTitlePosition,
        activeTab = activeTab,
        onActiveTabChange = onActiveTabChange,
        comfortableBorderMode = comfortableBorderMode,
        onComfortableBorderModeChange = viewModel::setComfortableBorderMode,
        hideTitlesInComfortable = hideTitlesInComfortable,
        onHideTitlesInComfortableChange = viewModel::setHideTitlesInComfortable,
        onDismiss = onDismiss,
    )
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
    // Task 61 (round 21): the row's OWN state — needed for the auto-scroll
    // (the selected category must be IN VIEW when the library page opens).
    val listState = rememberLazyListState()

    // Task 64 (round 24): a user-tap marker. The auto-center below ANIMATES
    // only when the selection change came from the user TAPPING a chip; page
    // opens, tab returns, restored selections, and category-list changes
    // (rename/add/delete) land INSTANTLY with the first frame. The chips' click
    // handlers set this before delegating to [onSelectCategory]; the effect
    // consumes it when it runs.
    var animateNextCenter by remember { mutableStateOf(false) }

    // Task 61 (round 21 — the device report: "if I have selected the very
    // first or very last category then when I open up the library page it
    // opens it up in the middle. The right or left categories are not
    // shown") + Task 64 (round 24 — the centering spec, re-done after the
    // round-23 attempt was reverted): the row scrolls so the SELECTED chip
    // is CENTERED in the row whenever possible. Centering is impossible at
    // the two EDGES (LazyRow clamps — the first chip stays at the start, the
    // last at the end), which is exactly the spec's "excluding the left and
    // right side ones".
    //
    // Mechanics: the centered target needs the chip's measured width, which
    // only exists once the item is composed. For the INSTANT paths (open /
    // restore / list change) step 1 snaps the chip into view start-aligned
    // (composing + measuring it), step 2 re-scrolls with the NEGATIVE offset
    // that centers it — both no-animation, landing with the first frame.
    // For a USER TAP the tapped chip is by definition already composed, so
    // the row glides DIRECTLY from wherever it is to the centered target —
    // no visible pre-snap (the round-23 version snapped-then-glided, which
    // read as a backwards jump).
    LaunchedEffect(categories, selectedCategoryId, showAllTab) {
        val selectedIndex = when {
            selectedCategoryId == null && showAllTab -> 0
            selectedCategoryId == null -> -1
            else -> {
                val categoryIndex = categories.indexOfFirst { it.id == selectedCategoryId }
                if (categoryIndex >= 0 && showAllTab) categoryIndex + 1 else categoryIndex
            }
        }
        if (selectedIndex >= 0) {
            val animate = animateNextCenter
            animateNextCenter = false
            if (animate) {
                // The tapped chip is composed — find its measured size now.
                var handled = false
                repeat(3) {
                    if (!handled) {
                        val layout = listState.layoutInfo
                        val item = layout.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
                        if (item != null) {
                            val viewport = layout.viewportEndOffset - layout.viewportStartOffset
                            val centerOffset = -((viewport - item.size) / 2)
                            listState.animateScrollToItem(selectedIndex, centerOffset)
                            handled = true
                        } else {
                            // Not composed this frame (rare) — wait a frame, retry.
                            androidx.compose.runtime.withFrameNanos { }
                        }
                    }
                }
            } else {
                // Step 1: snap start-aligned — composes + measures the chip.
                listState.scrollToItem(selectedIndex)
                // Step 2: center it with the negative offset (clamped at the
                // edges by LazyRow itself).
                var centered = false
                repeat(3) {
                    if (!centered) {
                        val layout = listState.layoutInfo
                        val item = layout.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
                        if (item != null) {
                            val viewport = layout.viewportEndOffset - layout.viewportStartOffset
                            val centerOffset = -((viewport - item.size) / 2)
                            listState.scrollToItem(selectedIndex, centerOffset)
                            centered = true
                        } else {
                            androidx.compose.runtime.withFrameNanos { }
                        }
                    }
                }
            }
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // ── "All" tab (null selection) — only when 2+ categories have items ──
        if (showAllTab) {
            item(key = "all") {
                CategoryTab(
                    label = "All",
                    isSelected = selectedCategoryId == null,
                    onClick = {
                        animateNextCenter = true
                        onSelectCategory(null)
                    },
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
                onClick = {
                    animateNextCenter = true
                    onSelectCategory(category.id)
                },
                onLongClick = { onLongPressCategory(category) },
            )
        }
    }
}

/**
 * A single text-based category tab with an underline indicator.
 *
 * - Selected: primary color, FontWeight.ExtraBold, 3dp primary underline.
 * - Unselected: onSurfaceVariant, FontWeight.Medium, transparent underline.
 *
 * Task 61 (round 21) — the underline reworked per the device spec:
 *  - it is as WIDE as the category text itself (the Column wraps the text;
 *    the bar fills it — was a fixed 20dp);
 *  - a little THICKER (2dp → 3dp) and CLOSER to the text (4dp → 2dp gap);
 *  - the tab's internal vertical padding shrank (4dp → 2dp) with the row's
 *    paddings — a tighter category section overall.
 *
 * Task 64 (round 24 — the device report: "The full category names do not
 * show… without any minimization of the name, like adding dots at the end
 * and shrinking the name or anything like that"): the chip now sizes to the
 * FULL single-line text width. Root cause of the truncation: the Task-62
 * underline fix wrapped the Column in `width(IntrinsicSize.Min)` — a Text's
 * MIN intrinsic width is its widest WORD (intrinsic measurement assumes the
 * paragraph can wrap), so every multi-word category name (or "Name (count)")
 * had its column sized to ONE WORD and the maxLines=1 + Ellipsis text
 * truncated to "My Long Ca…". `IntrinsicSize.Max` measures the FULL single
 * line (the LazyRow already gives items unbounded main-axis width, so the
 * chip renders at its full text width and the row scrolls); the ellipsis is
 * removed outright so a bounded constraint can never re-introduce dots.
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
            // Task 64 (round 24 — the full-name fix): IntrinsicSize.Max sizes
            // the Column to the text's FULL single-line width (the Min variant
            // measured the widest WORD — see the KDoc above). fillMaxWidth()
            // below resolves to exactly the text width, and the clickable
            // area keeps the text's width.
            .width(IntrinsicSize.Max)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            // Task 64: no maxLines / no Ellipsis — the chip always renders the
            // name in full (one line, the row scrolls; a name wider than the
            // viewport simply scrolls inside the row).
            softWrap = false,
        )
        // ── Underline indicator — as wide as the text, 3dp thick, 1dp gap ──
        // Task 62: the gap tightened 2dp → 1dp (the device spec: the line
        // should sit "very close to the bottom of the text").
        Spacer(Modifier.height(1.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
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
                    color = LocalHeadingColor.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = LocalCardDescriptionColor.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurfaceVariant,
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
    showScoreBadge: Boolean,
    showContinueWatching: Boolean,
    showTotalEntries: Boolean,
    showCategoryCounts: Boolean,
    sortType: LibrarySortType,
    sortAscending: Boolean,
    releasedAudioFilter: ReleasedAudioFilter,
    releasedUnwatchedOnly: Boolean,
    coverBorderEnabled: Boolean,
    coverBorderColor: CoverBorderColor,
    coverBorderWidth: CoverBorderWidth,
    showAllCaughtUpTag: Boolean,
    listDensity: ListDensity,
    listTitlePosition: ListTitlePosition,
    onDisplayModeChange: (LibraryDisplayMode) -> Unit,
    onColumnsChange: (Int) -> Unit,
    onEpisodeBadgeModeChange: (EpisodeBadgeMode) -> Unit,
    onShowScoreBadgeChange: (Boolean) -> Unit,
    onShowContinueWatchingChange: (Boolean) -> Unit,
    onShowTotalEntriesChange: (Boolean) -> Unit,
    onShowCategoryCountsChange: (Boolean) -> Unit,
    onTitleLinesChange: (Int) -> Unit,
    onSortChange: (LibrarySortType, Boolean) -> Unit,
    onReleasedAudioFilterChange: (ReleasedAudioFilter) -> Unit,
    onReleasedUnwatchedOnlyChange: (Boolean) -> Unit,
    onCoverBorderEnabledChange: (Boolean) -> Unit,
    onCoverBorderColorChange: (CoverBorderColor) -> Unit,
    onCoverBorderWidthChange: (CoverBorderWidth) -> Unit,
    onShowAllCaughtUpTagChange: (Boolean) -> Unit,
    onListDensityChange: (ListDensity) -> Unit,
    onListTitlePositionChange: (ListTitlePosition) -> Unit,
    // D-242-fix21: Hoisted from parent so tab persists across sheet open/close.
    activeTab: Int,
    onActiveTabChange: (Int) -> Unit,
    comfortableBorderMode: ComfortableBorderMode,
    onComfortableBorderModeChange: (ComfortableBorderMode) -> Unit,
    // D-251: hide-titles toggle for Comfortable mode.
    hideTitlesInComfortable: Boolean,
    onHideTitlesInComfortableChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxSheetHeight = screenHeight * 0.70f
    // D-242-fix18: Compute isDark once at the @Composable level (not inside
    // LazyListScope lambdas which are not @Composable).
    val sheetIsDark = isSystemInDarkTheme()

    // D-242-fix21: activeTab is now hoisted from the parent (persists across
    // sheet open/close). No local state needed.
    // D-242-fix17: Renamed 'Display & Badges' to 'Display', added 'UI' tab.
    val tabs = listOf("Sort", "Display", "UI")

    // D-242-fix13: scroll-to-minimize (like profile page).
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val collapseThresholdPx = with(density) { 40.dp.toPx() } // tab strip height
    val scrollFraction: () -> Float = {
        val raw = if (listState.firstVisibleItemIndex > 0) collapseThresholdPx
                  else listState.firstVisibleItemScrollOffset.toFloat()
        (raw / collapseThresholdPx).coerceIn(0f, 1f)
    }

    // Magnetic snap — animate to fully-collapsed or fully-expanded on scroll end.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .filter { !it }
            .collect {
                if (listState.firstVisibleItemIndex == 0) {
                    if (scrollFraction() > 0.5f) listState.animateScrollToItem(1, 0)
                    else listState.animateScrollToItem(0, 0)
                }
            }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .navigationBarsPadding(),
        ) {
            // ── Pinned header (fixed size, does NOT shrink on scroll) ──
            // D-242-fix15: "Library Settings" text stays at fixed 20sp (per user feedback).
            // D-242-fix16: Mini pill shows ONLY "Display & Badges" (Sort removed per user request).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Library Settings",
                    fontFamily = RobotoFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                // D-242-fix16: Mini pill — single "Display & Badges" label (no Sort).
                // Clicking it switches to the Display & Badges tab (index 1).
                // Fades IN as the full tab strip scrolls away.
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .graphicsLayer { alpha = scrollFraction() }
                        .clickable(
                            enabled = scrollFraction() > 0.5f,
                        ) { onActiveTabChange(1) }, // Switch to "Display"
                ) {
                    Text(
                        text = tabs[1], // Always "Display & Badges"
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        maxLines = 1,
                    )
                }
            }

            // ── LazyColumn + gradient blur scrim ──
            // D-242-fix16: Gradient blur effect at the top edge — content scrolling
            // under the header fades into it (like ProfileScreen's pattern).
            val sheetBgColor = MaterialTheme.colorScheme.surface
            Box(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    // D-242-fix18: Added bottom padding so the last items (buttons,
                    // toggles) are not cut off by the bottom of the sheet.
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                // Item 0: tab strip — shrinks + fades on scroll.
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
                            },
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(4.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                tabs.forEachIndexed { index, label ->
                                    val isActive = index == activeTab
                                    Surface(
                                        color = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { onActiveTabChange(index) },
                                    ) {
                                        Text(
                                            text = label,
                                            fontFamily = RobotoFamily,
                                            fontSize = 13.sp,
                                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                                            color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 8.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                // Divider after tab strip.
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                // Tab content.
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
                        showScoreBadge = showScoreBadge,
                        showContinueWatching = showContinueWatching,
                        showTotalEntries = showTotalEntries,
                        showCategoryCounts = showCategoryCounts,
                        releasedAudioFilter = releasedAudioFilter,
                        releasedUnwatchedOnly = releasedUnwatchedOnly,
                        showAllCaughtUpTag = showAllCaughtUpTag,
                        listDensity = listDensity,
                        listTitlePosition = listTitlePosition,
                        hideTitlesInComfortable = hideTitlesInComfortable,
                        onDisplayModeChange = onDisplayModeChange,
                        onColumnsChange = onColumnsChange,
                        onTitleLinesChange = onTitleLinesChange,
                        onEpisodeBadgeModeChange = onEpisodeBadgeModeChange,
                        onShowScoreBadgeChange = onShowScoreBadgeChange,
                        onShowContinueWatchingChange = onShowContinueWatchingChange,
                        onShowTotalEntriesChange = onShowTotalEntriesChange,
                        onShowCategoryCountsChange = onShowCategoryCountsChange,
                        onReleasedAudioFilterChange = onReleasedAudioFilterChange,
                        onReleasedUnwatchedOnlyChange = onReleasedUnwatchedOnlyChange,
                        onShowAllCaughtUpTagChange = onShowAllCaughtUpTagChange,
                        onListDensityChange = onListDensityChange,
                        onListTitlePositionChange = onListTitlePositionChange,
                        onHideTitlesInComfortableChange = onHideTitlesInComfortableChange,
                    )
                    2 -> uiTab(
                        coverBorderEnabled = coverBorderEnabled,
                        coverBorderColor = coverBorderColor,
                        coverBorderWidth = coverBorderWidth,
                        displayMode = displayMode,
                        listDensity = listDensity,
                        listTitlePosition = listTitlePosition,
                        isDark = sheetIsDark,
                        comfortableBorderMode = comfortableBorderMode,
                        onCoverBorderEnabledChange = onCoverBorderEnabledChange,
                        onCoverBorderColorChange = onCoverBorderColorChange,
                        onCoverBorderWidthChange = onCoverBorderWidthChange,
                        onListDensityChange = onListDensityChange,
                        onListTitlePositionChange = onListTitlePositionChange,
                        onComfortableBorderModeChange = onComfortableBorderModeChange,
                    )
                }
            }

                // D-242-fix16: Gradient blur scrim at the top edge — fades in when
                // content scrolls under the header so it appears to blur into it.
                // Uses smoothstep for a natural fade (same pattern as ProfileScreen).
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .align(Alignment.TopCenter)
                        .graphicsLayer {
                            val f = scrollFraction()
                            alpha = (f * f * (3 - 2 * f)) // smoothstep
                        }
                        .drawBehind {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        sheetBgColor,
                                        sheetBgColor.copy(alpha = 0.85f),
                                        sheetBgColor.copy(alpha = 0.4f),
                                        sheetBgColor.copy(alpha = 0.0f),
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
    showScoreBadge: Boolean,
    showContinueWatching: Boolean,
    showTotalEntries: Boolean,
    showCategoryCounts: Boolean,
    releasedAudioFilter: ReleasedAudioFilter,
    releasedUnwatchedOnly: Boolean,
    showAllCaughtUpTag: Boolean,
    listDensity: ListDensity,
    listTitlePosition: ListTitlePosition,
    hideTitlesInComfortable: Boolean,
    onDisplayModeChange: (LibraryDisplayMode) -> Unit,
    onColumnsChange: (Int) -> Unit,
    onTitleLinesChange: (Int) -> Unit,
    onEpisodeBadgeModeChange: (EpisodeBadgeMode) -> Unit,
    onShowScoreBadgeChange: (Boolean) -> Unit,
    onShowContinueWatchingChange: (Boolean) -> Unit,
    onShowTotalEntriesChange: (Boolean) -> Unit,
    onShowCategoryCountsChange: (Boolean) -> Unit,
    onReleasedAudioFilterChange: (ReleasedAudioFilter) -> Unit,
    onReleasedUnwatchedOnlyChange: (Boolean) -> Unit,
    onShowAllCaughtUpTagChange: (Boolean) -> Unit,
    onListDensityChange: (ListDensity) -> Unit,
    onListTitlePositionChange: (ListTitlePosition) -> Unit,
    onHideTitlesInComfortableChange: (Boolean) -> Unit,
) {
    // ═══════════════════════════════════════════════════════════════════════
    // SECTION 1: DISPLAY (Display Mode, Columns, Title lines)
    // ═══════════════════════════════════════════════════════════════════════
    item { OptionLabel("Display") }

    // ── Display mode (4-grid of visual cards) ──
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
            Spacer(Modifier.height(16.dp))
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

    // ── Title lines (hidden for COVER_ONLY — no titles in that mode; also
    // hidden in COMFORTABLE when the Hide Titles toggle is on) ──
    // D-242-fix17: Smoothly disappears when COVER_ONLY is selected.
    // D-251: Also disappears when Comfortable titles are hidden.
    if (displayMode != LibraryDisplayMode.COVER_ONLY &&
        !(displayMode == LibraryDisplayMode.COMFORTABLE_GRID && hideTitlesInComfortable)
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            OptionLabel("Title lines")
        }
        item {
            SegmentedButtons(
                options = listOf("1" to 1, "2" to 2, "3" to 3),
                selected = titleLines,
                onSelect = onTitleLinesChange,
            )
        }
    }

    // ── D-251: Hide Titles (Comfortable mode only) ──
    // Hides the title text under covers for a cover-only look that KEEPS
    // Comfortable's rounded corners and grid spacing — distinct from the
    // COVER_ONLY mode (square corners, edge-to-edge, zero gaps).
    if (displayMode == LibraryDisplayMode.COMFORTABLE_GRID) {
        item {
            Spacer(Modifier.height(16.dp))
            TwoWayButton(
                label = "Hide Titles",
                selected = hideTitlesInComfortable,
                onChange = onHideTitlesInComfortableChange,
            )
        }
    }

    // D-242-fix19: List mode specific settings — shown in the DISPLAY section
    // (right after Title lines), NOT at the bottom. Only when LIST mode is selected.
    if (displayMode == LibraryDisplayMode.LIST) {
        item {
            Spacer(Modifier.height(16.dp))
            OptionLabel("List Density")
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ListDensity.entries.forEach { density ->
                    val isSelected = listDensity == density
                    Surface(
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).clickable { onListDensityChange(density) },
                    ) {
                        Text(
                            text = density.displayName,
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
        item {
            Spacer(Modifier.height(12.dp))
            OptionLabel("Title Position")
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ListTitlePosition.entries.forEach { pos ->
                    val isSelected = listTitlePosition == pos
                    Surface(
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).clickable { onListTitlePositionChange(pos) },
                    ) {
                        Text(
                            text = pos.displayName,
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

    // ═══════════════════════════════════════════════════════════════════════
    // SECTION 2: BADGES (Episode Badge, Released Audio, Show, Score Badge)
    // ═══════════════════════════════════════════════════════════════════════
    item { SectionSeparator("Badges") }

    // ── Episode badge ──
    item { OptionLabel("Episode Badge") }
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

    // D-242-fix16: Advanced RELEASED sub-options — only when RELEASED is selected.
    if (episodeBadgeMode == EpisodeBadgeMode.RELEASED) {
        // ── Released Audio filter ──
        item {
            Spacer(Modifier.height(12.dp))
            OptionLabel("Released Audio")
        }
        item {
            val isDark = isSystemInDarkTheme()
            val subColor = if (isDark) Color(0xFF64B5F6) else Color(0xFF1976D2)
            val dubColor = if (isDark) Color(0xFFFFB74D) else Color(0xFFF57C00)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReleasedAudioFilterCard(
                    label = "Both",
                    icons = listOf(BadgeIcons.Sub to subColor, BadgeIcons.Dub to dubColor),
                    isSelected = releasedAudioFilter == ReleasedAudioFilter.BOTH,
                    onClick = { onReleasedAudioFilterChange(ReleasedAudioFilter.BOTH) },
                    modifier = Modifier.weight(1f),
                )
                ReleasedAudioFilterCard(
                    label = "Sub",
                    icons = listOf(BadgeIcons.Sub to subColor),
                    isSelected = releasedAudioFilter == ReleasedAudioFilter.SUB,
                    onClick = { onReleasedAudioFilterChange(ReleasedAudioFilter.SUB) },
                    modifier = Modifier.weight(1f),
                )
                ReleasedAudioFilterCard(
                    label = "Dub",
                    icons = listOf(BadgeIcons.Dub to dubColor),
                    isSelected = releasedAudioFilter == ReleasedAudioFilter.DUB,
                    onClick = { onReleasedAudioFilterChange(ReleasedAudioFilter.DUB) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // ── Show: All / Unwatched (two-way button with custom labels) ──
        // D-242-fix17: Clearer labels — "All" vs "Unwatched" (not Off/On).
        item {
            Spacer(Modifier.height(12.dp))
            TwoWayButton(
                label = "Show",
                selected = releasedUnwatchedOnly,
                onChange = onReleasedUnwatchedOnlyChange,
                leftLabel = "All",
                rightLabel = "Unwatched",
            )
        }
        // D-242-fix18: All Caught Up tag toggle — shows "All Caught Up" badge
        // for series with 0 unwatched episodes.
        item {
            Spacer(Modifier.height(12.dp))
            TwoWayButton(
                label = "All Caught Up Tag",
                selected = showAllCaughtUpTag,
                onChange = onShowAllCaughtUpTagChange,
                leftLabel = "Off",
                rightLabel = "On",
            )
        }
    }

    // D-242-fix19: List Density + Title Position moved to Display section (above).
    // Old duplicate block removed.

    // ── Score badge (two-way button, replaces toggle) ──
    item {
        Spacer(Modifier.height(16.dp))
        TwoWayButton(
            label = "Score Badge",
            selected = showScoreBadge,
            onChange = onShowScoreBadgeChange,
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SECTION 3: OPTIONS (Continue watching, Total entries, Category counts)
    // ═══════════════════════════════════════════════════════════════════════
    item { SectionSeparator("Options") }

    item {
        TwoWayButton(
            label = "Continue watching",
            selected = showContinueWatching,
            onChange = onShowContinueWatchingChange,
        )
    }
    item {
        Spacer(Modifier.height(12.dp))
        TwoWayButton(
            label = "Total entries in header",
            selected = showTotalEntries,
            onChange = onShowTotalEntriesChange,
        )
    }
    item {
        Spacer(Modifier.height(12.dp))
        TwoWayButton(
            label = "Category counts on tabs",
            selected = showCategoryCounts,
            onChange = onShowCategoryCountsChange,
        )
    }
}

// ── UI tab (cover borders) ──

private fun androidx.compose.foundation.lazy.LazyListScope.uiTab(
    coverBorderEnabled: Boolean,
    coverBorderColor: CoverBorderColor,
    coverBorderWidth: CoverBorderWidth,
    displayMode: LibraryDisplayMode,
    listDensity: ListDensity,
    listTitlePosition: ListTitlePosition,
    isDark: Boolean,
    comfortableBorderMode: ComfortableBorderMode,
    onCoverBorderEnabledChange: (Boolean) -> Unit,
    onCoverBorderColorChange: (CoverBorderColor) -> Unit,
    onCoverBorderWidthChange: (CoverBorderWidth) -> Unit,
    onListDensityChange: (ListDensity) -> Unit,
    onListTitlePositionChange: (ListTitlePosition) -> Unit,
    onComfortableBorderModeChange: (ComfortableBorderMode) -> Unit,
) {
    // ═══════════════════════════════════════════════════════════════════════
    // SECTION 1: COVER BORDERS
    // ═══════════════════════════════════════════════════════════════════════
    item { OptionLabel("Cover Borders") }

    // ── Enable/Disable border ──
    item {
        TwoWayButton(
            label = "Card Borders",
            selected = coverBorderEnabled,
            onChange = onCoverBorderEnabledChange,
            leftLabel = "Off",
            rightLabel = "On",
        )
    }

    // ── Border color (only shown when enabled) ──
    // D-242-fix18: Colors reordered per user spec:
    //   1. GRAY (default), 2. THEME_ADAPTIVE (white/black), 3. PRIMARY,
    //   4. SURFACE, 5. ADAPTIVE (extracts from cover image).
    if (coverBorderEnabled) {
        item {
            Spacer(Modifier.height(16.dp))
            OptionLabel("Border Color")
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CoverBorderColor.entries.forEach { color ->
                    val isSelected = coverBorderColor == color
                    // Resolve the display color:
                    // - GRAY/PRIMARY/SURFACE → use hex directly.
                    // - THEME_ADAPTIVE → white in dark theme, black in light theme.
                    // - ADAPTIVE → show a gradient (placeholder; actual color
                    //   extracted per-cover at render time).
                    val displayColor = when (color) {
                        CoverBorderColor.THEME_ADAPTIVE -> if (isDark) Color(0xFFFFFFFF) else Color(0xFF000000)
                        CoverBorderColor.ADAPTIVE -> Color(0xFF808080) // placeholder gray
                        else -> Color(color.hex)
                    }
                    Surface(
                        color = displayColor,
                        shape = CircleShape,
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 0.5.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { onCoverBorderColorChange(color) },
                    ) {
                        // ADAPTIVE gets a small "A" label to indicate it's special.
                        if (color == CoverBorderColor.ADAPTIVE) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "A",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Border width ──
        item {
            Spacer(Modifier.height(16.dp))
            OptionLabel("Border Width")
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CoverBorderWidth.entries.forEach { width ->
                    val isSelected = coverBorderWidth == width
                    Surface(
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).clickable { onCoverBorderWidthChange(width) },
                    ) {
                        Text(
                            text = width.displayName,
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

        // D-242-fix21: Comfortable border mode — only shown when COMFORTABLE_GRID.
        // Lets user choose: border around cover only, or around cover + title.
        if (displayMode == LibraryDisplayMode.COMFORTABLE_GRID) {
            item {
                Spacer(Modifier.height(16.dp))
                OptionLabel("Border Scope")
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ComfortableBorderMode.entries.forEach { mode ->
                        val isSelected = comfortableBorderMode == mode
                        Surface(
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).clickable { onComfortableBorderModeChange(mode) },
                        ) {
                            Text(
                                text = mode.displayName,
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
    // D-242-fix11: name on LEFT, icon on RIGHT (horizontal layout).
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
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
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

/**
 * D-242-fix16: A two-way segmented button selector (replaces SwitchRow).
 *
 * Shows a label on the left + two buttons on the right. The selected button
 * is filled with primary color; the other is muted.
 *
 * D-242-fix17: Added [leftLabel] and [rightLabel] params so the buttons can
 * say "All"/"Unwatched" instead of just "Off"/"On" — makes it clearer what
 * each option does (per user feedback about the "Show" button).
 *
 * This matches the Episode Badge Off/Released/Total button style —
 * consistent visual language throughout the Customize sheet.
 */
@Composable
private fun TwoWayButton(
    label: String,
    selected: Boolean,
    onChange: (Boolean) -> Unit,
    leftLabel: String = "Off",
    rightLabel: String = "On",
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(leftLabel to false, rightLabel to true).forEach { (btnLabel, value) ->
                val isSelected = selected == value
                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).clickable { onChange(value) },
                ) {
                    Text(
                        text = btnLabel,
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

/**
 * D-242-fix16: A visual section separator for the Customize sheet.
 *
 * Renders a section title with a divider above it, creating clear visual
 * separation between the 3 sections (Display, Badges, Options).
 */
@Composable
private fun SectionSeparator(title: String) {
    Spacer(Modifier.height(20.dp))
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(12.dp))
    OptionLabel(title)
}

// ── Grid view ──

@Composable
private fun LibraryGrid(
    viewModel: LibraryViewModel,
    entries: List<LibraryEntry>,
    gridState: LazyGridState,
    staggeredState: LazyStaggeredGridState,
    reveal: CoverRevealController?,
    displayMode: LibraryDisplayMode,
    isSelectionMode: Boolean,
    selectedMainIds: Set<String>,
    onClickEntry: (LibraryEntry) -> Unit,
    onLongClickEntry: (LibraryEntry) -> Unit,
) {
    // Task 62 (round 22 — M3): the grid settings are collected HERE (columns,
    // title lines, badge + border options) — a settings toggle recomposes
    // the grid, not the library root that hosts it.
    val columns by viewModel.columns.collectAsState()
    val titleLines by viewModel.titleLines.collectAsState()
    val episodeBadgeMode by viewModel.episodeBadgeMode.collectAsState()
    val episodeBadgePosition by viewModel.episodeBadgePosition.collectAsState()
    val showScoreBadge by viewModel.showScoreBadge.collectAsState()
    val scoreBadgePosition by viewModel.scoreBadgePosition.collectAsState()
    val releasedAudioFilter by viewModel.releasedAudioFilter.collectAsState()
    val releasedUnwatchedOnly by viewModel.releasedUnwatchedOnly.collectAsState()
    val coverBorderEnabled by viewModel.coverBorderEnabled.collectAsState()
    val coverBorderColor by viewModel.coverBorderColor.collectAsState()
    val coverBorderWidth by viewModel.coverBorderWidth.collectAsState()
    val showAllCaughtUpTag by viewModel.showAllCaughtUpTag.collectAsState()
    val comfortableBorderMode by viewModel.comfortableBorderMode.collectAsState()
    val hideTitlesInComfortable by viewModel.hideTitlesInComfortable.collectAsState()

    // Task 62 (round 22 — M2): the shared-element gate, hoisted OUT of the
    // per-cell LibraryCoverImage (which koinInject-ed AppPreferences and did
    // a synchronous SharedPreferences read on EVERY recomposition of EVERY
    // cell). ONE read per grid recomposition here, gated OFF while the
    // layout is actively scrolling — fling-recycled cells skip the
    // SharedTransition registry churn, and a tap can't start the cover morph
    // mid-drag anyway. At rest the registration re-attaches (the morph from
    // a settled tap is unchanged).
    val appPrefs = koinInject<com.confused.anikuta.core.preferences.AppPreferences>()
    val coverTransitionEnabled = appPrefs.coverTransitionEnabled

    // Task 64 (round 24): the shared-element gate is now a LAMBDA threaded to
    // the cells, and its scroll term is evaluated INSIDE [LibraryCoverImage]'s
    // composition scope — not here. The old Boolean param flipped at every
    // scroll START and STOP, and since every cell's content lambda captured
    // it, each flip recomposed ALL visible cards (badges, borders, titles and
    // all — ~2× full-grid recompositions per fling gesture). Now a flip only
    // re-executes the cheap cover-image scopes. The pref itself is still read
    // ONCE here (never inside the cells — the synchronous SharedPreferences
    // read per cell was M2's original fix); the branch below bakes it into a
    // stable remembered lambda bound to that branch's scroll state.
    if (displayMode == LibraryDisplayMode.COMFORTABLE_GRID) {
        val sharedElementsGate: () -> Boolean = remember(coverTransitionEnabled, staggeredState) {
            { coverTransitionEnabled && !staggeredState.isScrollInProgress }
        }
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(columns.coerceIn(2, 5)),
            state = staggeredState,
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 4.dp,
                bottom = if (isSelectionMode) 160.dp else 90.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalItemSpacing = 8.dp,
            modifier = Modifier.fillMaxSize(),
        ) {
            staggeredItems(entries, key = { it.mainId }, contentType = { "card" }) { item ->
                LibraryGridCard(
                    anime = item,
                    reveal = reveal,
                    titleLines = titleLines,
                    isSelectionMode = isSelectionMode,
                    isSelected = item.mainId in selectedMainIds,
                    onClick = onClickEntry,
                    onLongClick = onLongClickEntry,
                    episodeBadgeMode = episodeBadgeMode,
                    episodeBadgePosition = episodeBadgePosition,
                    showScoreBadge = showScoreBadge,
                    scoreBadgePosition = scoreBadgePosition,
                    releasedAudioFilter = releasedAudioFilter,
                    releasedUnwatchedOnly = releasedUnwatchedOnly,
                    coverBorderEnabled = coverBorderEnabled,
                    coverBorderColor = coverBorderColor,
                    coverBorderWidth = coverBorderWidth,
                    displayMode = displayMode,
                    showAllCaughtUpTag = showAllCaughtUpTag,
                    comfortableBorderMode = comfortableBorderMode,
                    hideTitles = hideTitlesInComfortable,
                    sharedElementsGate = sharedElementsGate,
                )
            }
        }
    } else {
        // D-251: COVER_ONLY is a full-bleed cover wall — square covers, zero gaps
        // between neighbors (horizontal AND vertical) and no side/top padding;
        // covers run edge-to-edge. COMPACT_GRID keeps the standard layout.
        val isCoverOnly = displayMode == LibraryDisplayMode.COVER_ONLY
        // Task 64: same lambda treatment for the standard grid branch — the
        // isScrollInProgress read lands inside the cover cells' scope.
        val sharedElementsGate: () -> Boolean = remember(coverTransitionEnabled, gridState) {
            { coverTransitionEnabled && !gridState.isScrollInProgress }
        }
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(columns.coerceIn(2, 5)),
            contentPadding = if (isCoverOnly) {
                PaddingValues(
                    top = 0.dp,
                    bottom = if (isSelectionMode) 160.dp else 90.dp,
                )
            } else {
                PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 4.dp,
                    bottom = if (isSelectionMode) 160.dp else 90.dp,
                )
            },
            horizontalArrangement = Arrangement.spacedBy(if (isCoverOnly) 0.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (isCoverOnly) 0.dp else 8.dp),
        ) {
            items(entries, key = { it.mainId }, contentType = { "card" }) { item ->
                LibraryGridCard(
                    anime = item,
                    reveal = reveal,
                    titleLines = titleLines,
                    isSelectionMode = isSelectionMode,
                    isSelected = item.mainId in selectedMainIds,
                    onClick = onClickEntry,
                    onLongClick = onLongClickEntry,
                    episodeBadgeMode = episodeBadgeMode,
                    episodeBadgePosition = episodeBadgePosition,
                    showScoreBadge = showScoreBadge,
                    scoreBadgePosition = scoreBadgePosition,
                    releasedAudioFilter = releasedAudioFilter,
                    releasedUnwatchedOnly = releasedUnwatchedOnly,
                    coverBorderEnabled = coverBorderEnabled,
                    coverBorderColor = coverBorderColor,
                    coverBorderWidth = coverBorderWidth,
                    displayMode = displayMode,
                    showAllCaughtUpTag = showAllCaughtUpTag,
                    comfortableBorderMode = comfortableBorderMode,
                    sharedElementsGate = sharedElementsGate,
                )
            }
        }
    }
}

/**
 * D-287 + D-291: Library cover AsyncImage with a scroll-tuned Coil request and
 * a REVEAL-ONCE fade-in animation.
 *
 * D-287 kept two request tweaks from the v0.2.54 scroll-perf work:
 * 1. **`crossfade(false)`** — the underlying Coil request never animates; the
 *    reveal-once system below owns ALL animation (one fade per cover, first
 *    load only — no per-cell crossfades re-running on every scroll-back).
 * 2. **`bitmapConfig(RGB_565)`** — 2 bytes/pixel halves each cover's memory
 *    cache footprint, so a 653-cover "All" grid stops evicting itself during a
 *    full scroll; scroll-back hits the memory cache instead of re-decoding.
 *
 * D-291 (device feedback on v0.2.55: "All the images just outright jump into
 * it … show up one by one with a smoother animation … faster as the users
 * scroll faster … if previously loaded then no need to reload"):
 * - **Reveal-once gate** — [CoverRevealController.isRevealed] (VM-backed set
 *   that survives tab switches; cleared only by pull-to-refresh). An
 *   unrevealed cover starts at alpha 0 and fades in when its load succeeds.
 *   A revealed cover renders at full alpha INSTANTLY — scroll-back and
 *   tab-return are smooth sailing, no re-animation, exactly "progressive
 *   loading should only work if they were not loaded".
 * - **Velocity-adaptive duration** — the fade duration is sampled from the
 *   screen-level scroll-velocity factor at the moment the load completes
 *   (non-reactive read — no per-cell recomposition on scroll frames): ~240ms
 *   when calm, ~70ms during a hard fling.
 * - **Draw-phase animation** — the fade alpha is read inside a
 *   `graphicsLayer { }` block, so animating it only re-DRAWS the cell; the
 *   cell (and its 5-column neighbors) never recompose during the fade.
 * - **Soft placeholder** — a low-alpha surfaceVariant tint sits behind the
 *   image so an unrevealed cover reads as "reserved space", not a black hole.
 *
 * Task 64 (round 24): two composition-cost cuts for fling smoothness —
 * - **Reveal fast path** — a cover that is ALREADY revealed (the common case
 *   during scroll-back / tab-return) composes a PLAIN AsyncImage: no
 *   Animatable, no onState machinery, no alpha graphicsLayer. The check is a
 *   `remember`-captured snapshot (NOT a reactive read of the VM set), so the
 *   moment a first load succeeds the cell STAYS on the animated path until
 *   the fade finishes (a reactive branch would swap branches mid-fade and
 *   snap the alpha to 1, killing the animation the reveal system exists to
 *   run). Cells entering composition later re-evaluate and take the fast
 *   path. Cells with no controller at all also take it (they never animate).
 * - **Gate lambda** — [sharedElementsGate] is evaluated HERE, in the cover's
 *   own scope: the old grid-level Boolean flipped at every scroll
 *   start/stop and recomposed every full card; now only these cheap cover
 *   scopes re-run on a flip.
 */
@Composable
private fun LibraryCoverImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    revealKey: String? = null,
    reveal: CoverRevealController? = null,
    // Task 64 (round 24): the grid-level shared-element gate as a LAMBDA —
    // evaluated inside THIS scope (see the KDoc above).
    sharedElementsGate: () -> Boolean = { false },
) {
    val context = LocalContext.current
    // D-320/D-328: shared-element key for the experimental cover transition.
    // Screen-namespaced (cover:library:<url>) so a Library card can never
    // collide with a Search card showing the SAME anime — during a Library ⇄
    // Search switch both screens compose at once, and pre-D-328 both built
    // "cover:<url>", making the shared cover fly BETWEEN the two pages.
    val sharedElementKey = if (sharedElementsGate()) {
        libraryCoverKey(url)
    } else null
    val request = remember(url, context) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(false)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .build()
    }

    // ── Task 64: the reveal fast-path snapshot (see the KDoc) ──
    // Captured ONCE per cell instance: no controller / no key → fast path;
    // controller + already-revealed key → fast path; otherwise animated.
    // A remember (not a direct read) so a mid-flight markRevealed() can never
    // swap the branch out from under a running fade.
    val initiallyRevealed = remember(revealKey, reveal) {
        val controller = reveal
        val key = revealKey
        controller == null || key == null || controller.isRevealed(key)
    }

    Box(
        modifier = modifier.background(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
    ) {
        if (initiallyRevealed) {
            // Task 64 fast path — a previously-revealed (or controller-less)
            // cover: full alpha, ZERO animation machinery on the cell.
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier
                    .fillMaxSize()
                    .coverSharedElement(sharedElementKey),
            )
        } else {
            // ── D-291: the first-load animated path ──
            var revealed by remember(revealKey) { mutableStateOf(false) }
            var fadeDurationMs by remember(revealKey) { mutableStateOf(220) }
            // Target alpha: 0 until first load success. The animate*AsState
            // spec is rebuilt when fadeDurationMs changes, which only happens
            // at reveal time — the tween the fade actually runs with is the
            // one sampled below in onState.
            val revealAlpha = animateFloatAsState(
                targetValue = if (revealed) 1f else 0f,
                animationSpec = tween(
                    durationMillis = fadeDurationMs,
                    easing = FastOutSlowInEasing,
                ),
                label = "coverReveal",
            )
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = contentScale,
                onState = { state ->
                    if (state is AsyncImagePainter.State.Success) {
                        // Sample the scroll velocity NON-reactively right now and
                        // map it to the fade duration: calm ≈ 240ms, fling ≈ 70ms.
                        fadeDurationMs = (240 - 170 * reveal!!.velocity.value)
                            .toInt()
                            .coerceIn(70, 240)
                        reveal!!.markRevealed(revealKey!!)
                        revealed = true
                    }
                },
                // Draw-phase alpha read: animating the fade re-draws ONLY this
                // cell's layer — zero recomposition churn in the grid.
                modifier = Modifier
                    .fillMaxSize()
                    .coverSharedElement(sharedElementKey)
                    .graphicsLayer { alpha = revealAlpha.value },
            )
        }
    }
}

@Composable
private fun LibraryGridCard(
    anime: LibraryEntry,
    reveal: CoverRevealController? = null,
    titleLines: Int,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: (LibraryEntry) -> Unit,
    onLongClick: (LibraryEntry) -> Unit,
    episodeBadgeMode: EpisodeBadgeMode = EpisodeBadgeMode.OFF,
    episodeBadgePosition: BadgePosition = BadgePosition.TOP_END,
    showScoreBadge: Boolean = false,
    scoreBadgePosition: BadgePosition = BadgePosition.TOP_START,
    releasedAudioFilter: ReleasedAudioFilter = ReleasedAudioFilter.BOTH,
    releasedUnwatchedOnly: Boolean = false,
    coverBorderEnabled: Boolean = false,
    coverBorderColor: CoverBorderColor = CoverBorderColor.GRAY,
    coverBorderWidth: CoverBorderWidth = CoverBorderWidth.THIN,
    displayMode: LibraryDisplayMode = LibraryDisplayMode.COMPACT_GRID,
    showAllCaughtUpTag: Boolean = false,
    comfortableBorderMode: ComfortableBorderMode = ComfortableBorderMode.COVER_AND_TITLE,
    hideTitles: Boolean = false,
    // Task 64 (round 24): the grid-level shared-element gate as a LAMBDA —
    // evaluated inside [LibraryCoverImage]'s scope so a scroll start/stop
    // flip only recomposes the cover images, never the full cards (the old
    // Boolean param dragged every visible card's badges/borders/titles into
    // each flip).
    sharedElementsGate: () -> Boolean = { false },
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
    // Task 64 (round 24): the animation is composed ONLY in selection mode —
    // every grid cell used to carry an idle Animatable + LaunchedEffect for
    // this at all times, pure composition overhead during flings (selection
    // mode is the only time the fade is ever visible).
    val cardAlpha: Float = if (isSelectionMode) {
        animateFloatAsState(
            targetValue = if (isSelected) 1f else 0.4f,
            animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing),
            label = "cardAlpha",
        ).value
    } else {
        1f
    }

    // D-242-fix19: Cover border — configurable width + color. Applied to the
    // card Box so it wraps the cover image + title overlay + badges.
    // THEME_ADAPTIVE resolves to white (dark theme) or black (light theme).
    // ADAPTIVE extracts the dominant color from the cover image itself,
    // adjusted for contrast against both cover and background.
    // D-242-fix21: In COMFORTABLE_GRID + COVER_ONLY mode, border goes on the
    // cover Box (not the outer card). In all other cases, border wraps the
    // entire card.
    val isDark = isSystemInDarkTheme()
    // D-292: extract the adaptive color ONLY when it can actually be used —
    // this used to run UNCONDITIONALLY for every card entering the viewport
    // (a 100×100 Coil load + Palette per card during scroll, with generate()
    // on the main thread before the D-292 off-main fix — a major scroll-jank
    // source in the 653-item grid even with borders disabled).
    val adaptiveColor = if (coverBorderEnabled && coverBorderColor == CoverBorderColor.ADAPTIVE) {
        rememberCoverAccentColor(anime.coverUrl)
    } else {
        null
    }
    val resolvedBorderColor = when (coverBorderColor) {
        CoverBorderColor.THEME_ADAPTIVE -> if (isDark) Color(0xFFFFFFFF) else Color(0xFF000000)
        CoverBorderColor.ADAPTIVE -> adaptiveColor ?: MaterialTheme.colorScheme.outline
        else -> Color(coverBorderColor.hex)
    }
    val isComfortable = displayMode == LibraryDisplayMode.COMFORTABLE_GRID
    val borderOnCoverOnly = isComfortable && comfortableBorderMode == ComfortableBorderMode.COVER_ONLY

    // D-251: COVER_ONLY uses perfectly square covers (no rounding); every other
    // grid mode keeps the 12dp rounded corners.
    val isCoverOnly = displayMode == LibraryDisplayMode.COVER_ONLY
    val cardShape = if (isCoverOnly) RectangleShape else RoundedCornerShape(12.dp)

    // D-252: badge rows clip their outer corner to match the cover's corner —
    // 0.dp on COVER_ONLY's square covers so the badge reaches the corner pixel
    // (the old hard-coded 12dp left a curved sliver of cover art visible).
    val badgeCornerRadius = if (isCoverOnly) 0.dp else 12.dp

    val outerBorderModifier = if (coverBorderEnabled && !borderOnCoverOnly) {
        Modifier.border(
            width = coverBorderWidth.widthDp.dp,
            color = resolvedBorderColor,
            shape = cardShape,
        )
    } else {
        Modifier
    }
    val coverBorderModifier = if (coverBorderEnabled && borderOnCoverOnly) {
        Modifier.border(
            width = coverBorderWidth.widthDp.dp,
            color = resolvedBorderColor,
            shape = cardShape,
        )
    } else {
        Modifier
    }

    // D-242-fix20: COMFORTABLE_GRID uses a Column layout (cover on top, title
    // below). All other grid modes use Box layout (title overlaid on cover).

    val cardModifier = Modifier
        .graphicsLayer { scaleX = scale; scaleY = scale; alpha = cardAlpha }
        .clip(cardShape)
        .then(outerBorderModifier)
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = { onClick(anime) },
            onLongClick = { onLongClick(anime) },
        )

    if (isComfortable) {
        // ── COMFORTABLE GRID: Column layout (cover + title below) ──
        Column(modifier = cardModifier) {
            // Cover image with badges — in a Box so badges can overlay.
            // D-242-fix21: When borderOnCoverOnly, border is applied here.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .then(coverBorderModifier),
            ) {
                LibraryCoverImage(
                    url = anime.coverUrl,
                    contentDescription = anime.title,
                    revealKey = anime.coverUrl,
                    reveal = reveal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(cardShape),
                    sharedElementsGate = sharedElementsGate,
                )

                // Cover badges (same as compact grid).
                if (!isSelectionMode) {
                    val badgeColors = rememberBadgeColorScheme()
                    val topStartBadges = mutableListOf<CoverBadgeData>()
                    val topEndBadges = mutableListOf<CoverBadgeData>()

                    val isAllCaughtUp = showAllCaughtUpTag &&
                        anime.releasedEpisodes != null && anime.releasedEpisodes > 0 &&
                        anime.watchedCount != null && anime.watchedCount > 0 &&
                        (anime.unwatchedCount == null || anime.unwatchedCount == 0)

                    if (!isAllCaughtUp) {
                        when (episodeBadgeMode) {
                            EpisodeBadgeMode.OFF -> {}
                            EpisodeBadgeMode.TOTAL -> {
                                (anime.episodes ?: anime.releasedEpisodes)?.let { ep ->
                                    topEndBadges.add(CoverBadgeData(
                                        text = "$ep",
                                        containerColor = badgeColors.totalContainer,
                                        contentColor = badgeColors.totalContent,
                                        icon = BadgeIcons.Total,
                                    ))
                                }
                            }
                            EpisodeBadgeMode.RELEASED -> {
                                when (releasedAudioFilter) {
                                    ReleasedAudioFilter.BOTH -> {
                                        val subCount = if (releasedUnwatchedOnly) anime.subUnwatchedCount else anime.subEpisodeCount
                                        val dubCount = if (releasedUnwatchedOnly) anime.dubUnwatchedCount else anime.dubEpisodeCount
                                        if (subCount != null && subCount > 0 && dubCount != null && dubCount > 0) {
                                            topEndBadges.add(CoverBadgeData("$subCount", badgeColors.subContainer, badgeColors.subContent, BadgeIcons.Sub, BadgeSegment("$dubCount", badgeColors.dubContainer, badgeColors.dubContent, BadgeIcons.Dub)))
                                        } else if (subCount != null && subCount > 0) {
                                            topEndBadges.add(CoverBadgeData("$subCount", badgeColors.subContainer, badgeColors.subContent, BadgeIcons.Sub))
                                        } else if (dubCount != null && dubCount > 0) {
                                            topEndBadges.add(CoverBadgeData("$dubCount", badgeColors.dubContainer, badgeColors.dubContent, BadgeIcons.Dub))
                                        } else {
                                            val hasPerTypeData = if (releasedUnwatchedOnly) anime.subUnwatchedCount != null || anime.dubUnwatchedCount != null else anime.subEpisodeCount != null || anime.dubEpisodeCount != null
                                            if (!hasPerTypeData) {
                                                val fallbackCount = if (releasedUnwatchedOnly) anime.unwatchedCount ?: anime.releasedEpisodes else anime.releasedEpisodes
                                                fallbackCount?.let { ep ->
                                                    topEndBadges.add(CoverBadgeData("$ep", badgeColors.totalContainer, badgeColors.totalContent, BadgeIcons.Total))
                                                }
                                            }
                                        }
                                    }
                                    ReleasedAudioFilter.SUB -> {
                                        val count = if (releasedUnwatchedOnly) anime.subUnwatchedCount ?: anime.unwatchedCount ?: anime.releasedEpisodes else anime.subEpisodeCount ?: anime.releasedEpisodes
                                        if (count != null && count > 0) {
                                            topEndBadges.add(CoverBadgeData("$count", badgeColors.subContainer, badgeColors.subContent, BadgeIcons.Sub))
                                        }
                                    }
                                    ReleasedAudioFilter.DUB -> {
                                        val count = if (releasedUnwatchedOnly) anime.dubUnwatchedCount ?: anime.unwatchedCount ?: anime.releasedEpisodes else anime.dubEpisodeCount ?: anime.releasedEpisodes
                                        if (count != null && count > 0) {
                                            topEndBadges.add(CoverBadgeData("$count", badgeColors.dubContainer, badgeColors.dubContent, BadgeIcons.Dub))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isAllCaughtUp) {
                        topEndBadges.add(CoverBadgeData("All Caught Up", badgeColors.allCaughtUpContainer, badgeColors.allCaughtUpContent))
                    }

                    if (showScoreBadge && anime.averageScore != null && anime.averageScore > 0) {
                        topStartBadges.add(CoverBadgeData("★ ${anime.averageScore}", badgeColors.scoreContainer, badgeColors.scoreContent))
                    }

                    if (topStartBadges.isNotEmpty()) {
                        CoverBadgeRow(
                            badges = topStartBadges,
                            position = BadgePosition.TOP_START,
                            coverCornerRadius = badgeCornerRadius,
                        )
                    }
                    if (topEndBadges.isNotEmpty()) {
                        CoverBadgeRow(
                            badges = topEndBadges,
                            position = BadgePosition.TOP_END,
                            coverCornerRadius = badgeCornerRadius,
                        )
                    }
                }

                // Selection badge in selection mode.
                if (isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Title BELOW the cover (no gradient overlay — clean text on surface).
            // D-242-fix21: Explicit lineHeight to reduce gap between title lines.
            // D-251: Hidden when the Comfortable "Hide Titles" toggle is on —
            // cover-only look, but keeps Comfortable's rounded corners + spacing
            // (distinct from the square edge-to-edge COVER_ONLY mode).
            if (!hideTitles) {
                Text(
                    text = anime.title,
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = LocalCardHeadingColor.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurface,
                    maxLines = titleLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
        }
    } else {
        // ── COMPACT_GRID / COVER_ONLY: Box layout (title overlaid on cover) ──
        Box(
            modifier = cardModifier,
        ) {
            // Cover image — 2:3 aspect ratio
            LibraryCoverImage(
                url = anime.coverUrl,
                contentDescription = anime.title,
                revealKey = anime.coverUrl,
                reveal = reveal,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(cardShape),
                sharedElementsGate = sharedElementsGate,
            )

            // D-242-fix15: Cover badges — positions hardcoded (no user-selectable position).
            // Episode badge = TOP_END (top-right), Score badge = TOP_START (top-left).
            if (!isSelectionMode) {
                val badgeColors = rememberBadgeColorScheme()
                val topStartBadges = mutableListOf<CoverBadgeData>()
                val topEndBadges = mutableListOf<CoverBadgeData>()

                val isAllCaughtUp = showAllCaughtUpTag &&
                    anime.releasedEpisodes != null && anime.releasedEpisodes > 0 &&
                    anime.watchedCount != null && anime.watchedCount > 0 &&
                    (anime.unwatchedCount == null || anime.unwatchedCount == 0)

                if (!isAllCaughtUp) {
                    when (episodeBadgeMode) {
                        EpisodeBadgeMode.OFF -> {}
                        EpisodeBadgeMode.TOTAL -> {
                            (anime.episodes ?: anime.releasedEpisodes)?.let { ep ->
                                topEndBadges.add(CoverBadgeData("$ep", badgeColors.totalContainer, badgeColors.totalContent, BadgeIcons.Total))
                            }
                        }
                        EpisodeBadgeMode.RELEASED -> {
                            when (releasedAudioFilter) {
                                ReleasedAudioFilter.BOTH -> {
                                    val subCount = if (releasedUnwatchedOnly) anime.subUnwatchedCount else anime.subEpisodeCount
                                    val dubCount = if (releasedUnwatchedOnly) anime.dubUnwatchedCount else anime.dubEpisodeCount
                                    if (subCount != null && subCount > 0 && dubCount != null && dubCount > 0) {
                                        topEndBadges.add(CoverBadgeData("$subCount", badgeColors.subContainer, badgeColors.subContent, BadgeIcons.Sub, BadgeSegment("$dubCount", badgeColors.dubContainer, badgeColors.dubContent, BadgeIcons.Dub)))
                                    } else if (subCount != null && subCount > 0) {
                                        topEndBadges.add(CoverBadgeData("$subCount", badgeColors.subContainer, badgeColors.subContent, BadgeIcons.Sub))
                                    } else if (dubCount != null && dubCount > 0) {
                                        topEndBadges.add(CoverBadgeData("$dubCount", badgeColors.dubContainer, badgeColors.dubContent, BadgeIcons.Dub))
                                    } else {
                                        val hasPerTypeData = if (releasedUnwatchedOnly) anime.subUnwatchedCount != null || anime.dubUnwatchedCount != null else anime.subEpisodeCount != null || anime.dubEpisodeCount != null
                                        if (!hasPerTypeData) {
                                            val fallbackCount = if (releasedUnwatchedOnly) anime.unwatchedCount ?: anime.releasedEpisodes else anime.releasedEpisodes
                                            fallbackCount?.let { ep ->
                                                topEndBadges.add(CoverBadgeData("$ep", badgeColors.totalContainer, badgeColors.totalContent, BadgeIcons.Total))
                                            }
                                        }
                                    }
                                }
                                ReleasedAudioFilter.SUB -> {
                                    val count = if (releasedUnwatchedOnly) anime.subUnwatchedCount ?: anime.unwatchedCount ?: anime.releasedEpisodes else anime.subEpisodeCount ?: anime.releasedEpisodes
                                    if (count != null && count > 0) {
                                        topEndBadges.add(CoverBadgeData("$count", badgeColors.subContainer, badgeColors.subContent, BadgeIcons.Sub))
                                    }
                                }
                                ReleasedAudioFilter.DUB -> {
                                    val count = if (releasedUnwatchedOnly) anime.dubUnwatchedCount ?: anime.unwatchedCount ?: anime.releasedEpisodes else anime.dubEpisodeCount ?: anime.releasedEpisodes
                                    if (count != null && count > 0) {
                                        topEndBadges.add(CoverBadgeData("$count", badgeColors.dubContainer, badgeColors.dubContent, BadgeIcons.Dub))
                                    }
                                }
                            }
                        }
                    }
                }

                if (isAllCaughtUp) {
                    topEndBadges.add(CoverBadgeData("All Caught Up", badgeColors.allCaughtUpContainer, badgeColors.allCaughtUpContent))
                }

                if (showScoreBadge && anime.averageScore != null && anime.averageScore > 0) {
                    topStartBadges.add(CoverBadgeData("★ ${anime.averageScore}", badgeColors.scoreContainer, badgeColors.scoreContent))
                }

                if (topStartBadges.isNotEmpty()) {
                    CoverBadgeRow(
                        badges = topStartBadges,
                        position = BadgePosition.TOP_START,
                        coverCornerRadius = badgeCornerRadius,
                    )
                }
                if (topEndBadges.isNotEmpty()) {
                    CoverBadgeRow(
                        badges = topEndBadges,
                        position = BadgePosition.TOP_END,
                        coverCornerRadius = badgeCornerRadius,
                    )
                }
            }

            // Title overlay at bottom with gradient (compact grid style)
            // D-242-fix17: Hide title for COVER_ONLY mode (per user request).
            if (displayMode != LibraryDisplayMode.COVER_ONLY) {
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
                        lineHeight = 12.sp, // D-242-fix21: reduce gap between title lines
                        fontWeight = FontWeight.ExtraBold,
                        color = LocalCardHeadingColor.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurface,
                        maxLines = titleLines,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
            }

            // ── D-141: Selection border overlay (drawn on top of content) ──
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = cardShape,
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
}

// ── List view ──

/**
 * D-242-fix18: A detail tag pill for list mode (year, score, episode count, etc.).
 */
private data class ListDetailTag(val text: String, val container: Color, val content: Color)

@Composable
private fun LibraryList(
    viewModel: LibraryViewModel,
    entries: List<LibraryEntry>,
    listState: LazyListState,
    reveal: CoverRevealController? = null,
    isSelectionMode: Boolean,
    selectedMainIds: Set<String>,
    onClickEntry: (LibraryEntry) -> Unit,
    onLongClickEntry: (LibraryEntry) -> Unit,
) {
    // Task 62 (round 22 — M3): the list settings are collected HERE — a
    // settings toggle recomposes the list, not the library root.
    val titleLines by viewModel.titleLines.collectAsState()
    val episodeBadgeMode by viewModel.episodeBadgeMode.collectAsState()
    val showScoreBadge by viewModel.showScoreBadge.collectAsState()
    val releasedAudioFilter by viewModel.releasedAudioFilter.collectAsState()
    val releasedUnwatchedOnly by viewModel.releasedUnwatchedOnly.collectAsState()
    val showAllCaughtUpTag by viewModel.showAllCaughtUpTag.collectAsState()
    val coverBorderEnabled by viewModel.coverBorderEnabled.collectAsState()
    val coverBorderColor by viewModel.coverBorderColor.collectAsState()
    val coverBorderWidth by viewModel.coverBorderWidth.collectAsState()
    val listDensity by viewModel.listDensity.collectAsState()
    val listTitlePosition by viewModel.listTitlePosition.collectAsState()

    // Task 62 (round 22 — M2): ONE prefs read per list recomposition.
    // Task 64 (round 24): the gate is a LAMBDA evaluated inside the cover
    // cells' scope (see LibraryGrid) — a scroll start/stop flip no longer
    // recomposes every full row.
    val appPrefs = koinInject<com.confused.anikuta.core.preferences.AppPreferences>()
    val coverTransitionEnabled = appPrefs.coverTransitionEnabled
    val sharedElementsGate: () -> Boolean = remember(coverTransitionEnabled, listState) {
        { coverTransitionEnabled && !listState.isScrollInProgress }
    }

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
        items(entries, key = { it.mainId }, contentType = { "row" }) { item ->
            LibraryListRow(
                anime = item,
                reveal = reveal,
                isSelectionMode = isSelectionMode,
                isSelected = item.mainId in selectedMainIds,
                onClick = onClickEntry,
                onLongClick = onLongClickEntry,
                titleLines = titleLines,
                episodeBadgeMode = episodeBadgeMode,
                showScoreBadge = showScoreBadge,
                releasedAudioFilter = releasedAudioFilter,
                releasedUnwatchedOnly = releasedUnwatchedOnly,
                showAllCaughtUpTag = showAllCaughtUpTag,
                coverBorderEnabled = coverBorderEnabled,
                coverBorderColor = coverBorderColor,
                coverBorderWidth = coverBorderWidth,
                listDensity = listDensity,
                listTitlePosition = listTitlePosition,
                sharedElementsGate = sharedElementsGate,
            )
        }
    }
}

/**
 * D-242-fix18: Redesigned list row with:
 * - Configurable border on the WHOLE entry (not just the cover).
 * - Title position (top or bottom) via [listTitlePosition].
 * - Details (year, score, episode tags) shown in a tagged pill format.
 * - Density-controlled cover size via [listDensity].
 * - Episode badge + score badge + All Caught Up tag support.
 */
@Composable
private fun LibraryListRow(
    anime: LibraryEntry,
    reveal: CoverRevealController? = null,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: (LibraryEntry) -> Unit,
    onLongClick: (LibraryEntry) -> Unit,
    titleLines: Int = 1,
    episodeBadgeMode: EpisodeBadgeMode = EpisodeBadgeMode.OFF,
    showScoreBadge: Boolean = false,
    releasedAudioFilter: ReleasedAudioFilter = ReleasedAudioFilter.BOTH,
    releasedUnwatchedOnly: Boolean = false,
    showAllCaughtUpTag: Boolean = false,
    coverBorderEnabled: Boolean = false,
    coverBorderColor: CoverBorderColor = CoverBorderColor.GRAY,
    coverBorderWidth: CoverBorderWidth = CoverBorderWidth.THIN,
    listDensity: ListDensity = ListDensity.NORMAL,
    listTitlePosition: ListTitlePosition = ListTitlePosition.BOTTOM,
    // Task 64 (round 24): the list-level shared-element gate as a LAMBDA —
    // evaluated inside [LibraryCoverImage]'s scope (see LibraryGrid).
    sharedElementsGate: () -> Boolean = { false },
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(Motion.DurationShort, easing = FastOutSlowInEasing),
        label = "rowScale",
    )
    // Task 64 (round 24): composed ONLY in selection mode (see the grid card's
    // cardAlpha note) — no idle Animatable per row during flings.
    val rowAlpha: Float = if (isSelectionMode) {
        animateFloatAsState(
            targetValue = if (isSelected) 1f else 0.4f,
            animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing),
            label = "rowAlpha",
        ).value
    } else {
        1f
    }

    // D-242-fix19: Resolve border color — ADAPTIVE extracts per-cover color.
    val isDark = isSystemInDarkTheme()
    // D-292: extract ONLY when the ADAPTIVE border is actually enabled (same
    // unconditional-per-card scroll cost as the grid card above).
    val adaptiveColor = if (coverBorderEnabled && coverBorderColor == CoverBorderColor.ADAPTIVE) {
        rememberCoverAccentColor(anime.coverUrl)
    } else {
        null
    }
    val resolvedBorderColor = when (coverBorderColor) {
        CoverBorderColor.THEME_ADAPTIVE -> if (isDark) Color(0xFFFFFFFF) else Color(0xFF000000)
        CoverBorderColor.ADAPTIVE -> adaptiveColor ?: MaterialTheme.colorScheme.outline
        else -> Color(coverBorderColor.hex)
    }
    val borderModifier = if (coverBorderEnabled) {
        Modifier.border(
            width = coverBorderWidth.widthDp.dp,
            color = resolvedBorderColor,
            shape = RoundedCornerShape(12.dp),
        )
    } else {
        Modifier
    }

    val badgeColors = rememberBadgeColorScheme()

    // D-242-fix19: Compute All Caught Up status first. When true, episode
    // tags are HIDDEN (only All Caught Up + score + year show).
    val isAllCaughtUp = showAllCaughtUpTag &&
        anime.releasedEpisodes != null && anime.releasedEpisodes > 0 &&
        anime.watchedCount != null && anime.watchedCount > 0 &&
        (anime.unwatchedCount == null || anime.unwatchedCount == 0)

    // D-242-fix18: Build detail tags (year, score, episode info, all caught up).
    val detailTags = mutableListOf<ListDetailTag>()

    // Episode badge tag — skipped when All Caught Up is showing.
    if (!isAllCaughtUp) {
        when (episodeBadgeMode) {
            EpisodeBadgeMode.OFF -> {}
            EpisodeBadgeMode.TOTAL -> {
                (anime.episodes ?: anime.releasedEpisodes)?.let { ep ->
                    detailTags.add(ListDetailTag("EP $ep", badgeColors.totalContainer, badgeColors.totalContent))
                }
            }
            EpisodeBadgeMode.RELEASED -> {
                val subCount = if (releasedUnwatchedOnly) anime.subUnwatchedCount else anime.subEpisodeCount
                val dubCount = if (releasedUnwatchedOnly) anime.dubUnwatchedCount else anime.dubEpisodeCount
                if (subCount != null && subCount > 0) {
                    detailTags.add(ListDetailTag("SUB $subCount", badgeColors.subContainer, badgeColors.subContent))
                }
                if (dubCount != null && dubCount > 0) {
                    detailTags.add(ListDetailTag("DUB $dubCount", badgeColors.dubContainer, badgeColors.dubContent))
                }
                if (detailTags.isEmpty()) {
                    anime.releasedEpisodes?.let { ep ->
                        detailTags.add(ListDetailTag("EP $ep", badgeColors.totalContainer, badgeColors.totalContent))
                    }
                }
            }
        }
    }

    // All Caught Up tag (uses pre-computed isAllCaughtUp).
    if (isAllCaughtUp) {
        detailTags.add(ListDetailTag("All Caught Up", badgeColors.allCaughtUpContainer, badgeColors.allCaughtUpContent))
    }

    // Score tag.
    if (showScoreBadge && anime.averageScore != null && anime.averageScore > 0) {
        detailTags.add(ListDetailTag("★ ${anime.averageScore}", badgeColors.scoreContainer, badgeColors.scoreContent))
    }

    // Year tag (always shown if available).
    anime.seasonYear?.let { year ->
        detailTags.add(ListDetailTag("$year", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = rowAlpha }
            .clip(RoundedCornerShape(12.dp))
            .then(borderModifier)
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
        // D-242-fix19: Top alignment so title can be at top or bottom of cover.
        verticalAlignment = Alignment.Top,
    ) {
        // Cover thumbnail (with optional D-141 selection badge in the corner)
        Box {
            LibraryCoverImage(
                url = anime.coverUrl,
                contentDescription = anime.title,
                revealKey = anime.coverUrl,
                reveal = reveal,
                modifier = Modifier
                    .width(listDensity.coverWidth.dp)
                    .height(listDensity.coverHeight.dp)
                    .clip(RoundedCornerShape(8.dp)),
                sharedElementsGate = sharedElementsGate,
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

        // Info column — title position controls whether title is first or last.
        // D-242-fix19: Text size scales with density.
        // D-242-fix20: BOTTOM position now truly aligns to the bottom of the
        // cover height (uses Spacer.weight(1f) to push title down).
        Column(
            modifier = Modifier
                .weight(1f)
                .height(listDensity.coverHeight.dp),
        ) {
            if (listTitlePosition == ListTitlePosition.TOP) {
                // Title first, then detail tags below.
                // D-242-fix21: Explicit lineHeight to reduce gap between lines.
                Text(
                    anime.title,
                    fontFamily = RobotoFamily,
                    fontSize = listDensity.titleFontSize.sp,
                    lineHeight = (listDensity.titleFontSize + 1).sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = LocalCardHeadingColor.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onBackground,
                    maxLines = titleLines,
                    overflow = TextOverflow.Ellipsis,
                )
                if (detailTags.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    DetailTagRow(detailTags)
                }
            } else {
                // BOTTOM: detail tags at top, title pushed to the very bottom
                // of the cover height via Spacer.weight(1f).
                if (detailTags.isNotEmpty()) {
                    DetailTagRow(detailTags)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    anime.title,
                    fontFamily = RobotoFamily,
                    fontSize = listDensity.titleFontSize.sp,
                    lineHeight = (listDensity.titleFontSize + 1).sp, // D-242-fix21
                    fontWeight = FontWeight.ExtraBold,
                    color = LocalCardHeadingColor.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onBackground,
                    maxLines = titleLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * D-242-fix21: Custom detail tag — rectangular shape, minimal height.
 *
 * Reverted from pill (RoundedCornerShape(50)) back to the original
 * rectangular shape (RoundedCornerShape(4.dp)) per user feedback.
 * The ONLY change from the original is reduced vertical padding (2dp → 1dp)
 * to make the tag height smaller without affecting text size.
 *
 * The Surface wraps ONLY the Text so there's no extra Box/Row adding height.
 */
@Composable
private fun DetailTagPill(tag: ListDetailTag) {
    Surface(
        color = tag.container,
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = tag.text,
            fontFamily = RobotoFamily,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            color = tag.content,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            maxLines = 1,
        )
    }
}

/**
 * D-242-fix20: Renders detail tags as a horizontal scrollable row of custom
 * pill tags. Uses [DetailTagPill] for each tag — no empty space, tight fit.
 */
@Composable
private fun DetailTagRow(tags: List<ListDetailTag>) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(tags) { tag ->
            DetailTagPill(tag)
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
 * D-242-fix15: Data for a single cover badge — text + colors + optional icon.
 *
 * When [icon] is non-null, the badge renders [icon] before [text] (e.g. a
 * subtitle icon before a SUB episode count). The icon uses [contentColor]
 * as its tint.
 *
 * D-242-fix15: Added [secondary] for compound badges (e.g. RELEASED+BOTH
 * shows SUB and DUB in a SINGLE badge with a split background + 45°
 * diagonal separator). When [secondary] is non-null, the badge renders
 * both segments inside one Surface — left half uses [containerColor]/
 * [contentColor], right half uses [secondary.containerColor]/
 * [secondary.contentColor], with a 45° diagonal line between them.
 */
private data class CoverBadgeData(
    val text: String,
    val containerColor: Color,
    val contentColor: Color,
    val icon: ImageVector? = null,
    val secondary: BadgeSegment? = null,
)

/**
 * D-242-fix15: The secondary segment of a compound [CoverBadgeData].
 * Used when RELEASED+BOTH merges SUB and DUB into a single badge.
 */
private data class BadgeSegment(
    val text: String,
    val containerColor: Color,
    val contentColor: Color,
    val icon: ImageVector? = null,
)

/**
 * D-242-fix14: A selection card for the "Released Audio" sub-option in the
 * Customize sheet. Shows one or more icons + a label. Selected = primary
 * border + tinted background (matches DisplayModeCard style).
 *
 * @param icons List of (icon, tint) pairs to render before the label. The
 *   "Both" option passes two icons (Sub + Dub); "Sub"/"Dub" pass one.
 */
@Composable
private fun ReleasedAudioFilterCard(
    label: String,
    icons: List<Pair<ImageVector, Color>>,
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        ) {
            icons.forEach { (icon, tint) ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(14.dp),
                )
            }
            // Label — spacedBy(3.dp) handles the gap between icons and text.
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * D-242-fix11: Renders multiple badges side-by-side in a single Row at a corner.
 * Edge-to-edge — sits flush with the cover corner. Compact: 9sp, 1dp vertical, Bold.
 *
 * D-242-fix15: Now supports compound badges (via [CoverBadgeData.secondary]).
 * When a badge has a secondary segment, it renders as a SINGLE badge with:
 * - Left half: [containerColor] background, [icon] + [text] in [contentColor]
 * - 45° diagonal separator line (semi-transparent white)
 * - Right half: [secondary.containerColor] background, [secondary.icon] +
 *   [secondary.text] in [secondary.contentColor]
 *
 * D-252 (pointed tags): the chip nearest the cover CENTER now tapers into a
 * 45° triangle tip ([PointedTagShape]) — badges read as pointed flags pointing
 * INTO the cover, per the user's "make pointier" request. The outer corner of
 * the whole row clips to [coverCornerRadius] so it stays flush with the cover's
 * corner — 0.dp for COVER_ONLY's square covers (fixes the curved-sliver defect
 * where the old hard-coded 12dp outer rounding left cover art visible behind
 * the badge corner), 12.dp for rounded-cover modes.
 */
@Composable
private fun BoxScope.CoverBadgeRow(
    badges: List<CoverBadgeData>,
    position: BadgePosition,
    coverCornerRadius: Dp = 12.dp,
) {
    val alignment = when (position) {
        BadgePosition.TOP_START -> Alignment.TopStart
        BadgePosition.TOP_END -> Alignment.TopEnd
        BadgePosition.BOTTOM_START -> Alignment.BottomStart
        BadgePosition.BOTTOM_END -> Alignment.BottomEnd
    }
    // Outer shape matches the cover's corner on the OUTER side only (D-252:
    // the old 4dp inner-corner rounding clipped the pointed tip's base — removed).
    val outerShape = when (position) {
        BadgePosition.TOP_START -> RoundedCornerShape(topStart = coverCornerRadius)
        BadgePosition.TOP_END -> RoundedCornerShape(topEnd = coverCornerRadius)
        BadgePosition.BOTTOM_START -> RoundedCornerShape(bottomStart = coverCornerRadius)
        BadgePosition.BOTTOM_END -> RoundedCornerShape(bottomEnd = coverCornerRadius)
    }
    // Which chip is nearest the cover center (the innermost chip) — that one
    // gets the pointed tip. For END-aligned rows the FIRST chip is innermost
    // (point on its START side); for START-aligned rows the LAST chip is
    // innermost (point on its END side).
    val pointFirstChip = position == BadgePosition.TOP_END || position == BadgePosition.BOTTOM_END
    Surface(
        modifier = Modifier.align(alignment),
        color = Color.Transparent,
        shape = outerShape,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            badges.forEachIndexed { idx, badge ->
                if (idx > 0) {
                    // Dot separator between separate badges.
                    Box(
                        modifier = Modifier
                            .size(2.dp)
                            .clip(CircleShape)
                            .background(badge.contentColor.copy(alpha = 0.5f)),
                    )
                }

                // D-252: this chip carries the pointed tip when it is the
                // innermost one. Extra horizontal padding keeps the content
                // clear of the transparent 45° tip (tip depth ≈ height/2 ≈ 7dp).
                val isPointedChip = if (pointFirstChip) idx == 0 else idx == badges.lastIndex
                val pointedShape = when {
                    !isPointedChip -> null
                    pointFirstChip -> PointedTagShape(PointedSide.START)
                    else -> PointedTagShape(PointedSide.END)
                }
                val tipPadding = if (isPointedChip) 4.dp else 0.dp

                if (badge.secondary != null) {
                    // ── Compound badge: single Surface with split background ──
                    // Left half = subContainer, right half = dubContainer,
                    // separated by a 45° diagonal line.
                    // D-242-fix16: Tightened spacing — segments are closer together
                    // to feel like a single cohesive badge (not two separate parts).
                    //
                    // D-252 (clip order): the split-painting drawBehind sits on the
                    // Surface MODIFIER, and M3 Surface applies its own shape-clip
                    // AFTER the user modifier — so the drawn halves would spill past
                    // a pointed shape. Fix: clip(pointedShape) BEFORE drawBehind in
                    // the modifier chain, so the halves are trimmed to the tip.
                    //
                    // D-257: 1dp outline (content color @ 50%) drawn after the fills
                    // so the badge reads crisp against busy cover art (device
                    // feedback: tags need borders). Follows the exact PointedTagShape
                    // geometry (tip = h/2, 45°) — a Surface border param can't be
                    // used here because the paint is hand-drawn.
                    val sec = badge.secondary
                    val compoundShape = pointedShape ?: RoundedCornerShape(0.dp)
                    val outlineColor = badge.contentColor.copy(alpha = 0.5f)
                    Surface(
                        color = Color.Transparent,
                        shape = RoundedCornerShape(0.dp),
                        modifier = Modifier
                            .clip(compoundShape)
                            .drawBehind {
                                val w = size.width
                                val h = size.height
                                // The diagonal is at the horizontal center, tilted
                                // by half the badge height on each side → ~45° angle.
                                val centerX = w * 0.5f
                                val tilt = h * 0.5f

                                // Left half (sub color) — fill entire background first.
                                drawRect(badge.containerColor)

                                // Right half (dub color) — drawn as a path with
                                // a diagonal left edge.
                                val rightPath = Path().apply {
                                    moveTo(centerX + tilt, 0f)
                                    lineTo(w, 0f)
                                    lineTo(w, h)
                                    lineTo(centerX - tilt, h)
                                    close()
                                }
                                drawPath(rightPath, sec.containerColor)

                                // 45° diagonal separator line (white, semi-transparent
                                // for a subtle visual divide).
                                drawLine(
                                    color = Color.White.copy(alpha = 0.5f),
                                    start = Offset(centerX + tilt, 0f),
                                    end = Offset(centerX - tilt, h),
                                    strokeWidth = 0.8.dp.toPx(),
                                )

                                // D-257: outline following the pointed geometry —
                                // mirrors PointedTagShape (tip = h/2, 45° taper).
                                // The outer half of the stroke is clipped by the
                                // shape clip above, so the visible line is ~0.5dp.
                                val outlinePath = Path().apply {
                                    val tip = h * 0.5f
                                    when {
                                        pointedShape == null -> {
                                            // Flat rectangle.
                                            moveTo(0f, 0f)
                                            lineTo(w, 0f)
                                            lineTo(w, h)
                                            lineTo(0f, h)
                                            close()
                                        }
                                        pointFirstChip -> {
                                            // PointedSide.START — left end tapers
                                            // to a point at the vertical center.
                                            moveTo(0f, h / 2f)
                                            lineTo(tip, 0f)
                                            lineTo(w, 0f)
                                            lineTo(w, h)
                                            lineTo(tip, h)
                                            close()
                                        }
                                        else -> {
                                            // PointedSide.END — right end tapers.
                                            moveTo(0f, 0f)
                                            lineTo(w - tip, 0f)
                                            lineTo(w, h / 2f)
                                            lineTo(w - tip, h)
                                            lineTo(0f, h)
                                            close()
                                        }
                                    }
                                }
                                drawPath(
                                    path = outlinePath,
                                    color = outlineColor,
                                    style = Stroke(width = 1.dp.toPx()),
                                )
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                start = 4.dp + tipPadding,
                                end = 4.dp + tipPadding,
                                top = 1.dp,
                                bottom = 1.dp,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // ── Left segment (SUB) ──
                            if (badge.icon != null) {
                                Icon(
                                    imageVector = badge.icon,
                                    contentDescription = null,
                                    tint = badge.contentColor,
                                    modifier = Modifier.size(9.dp), // D-242-fix18: slightly bigger
                                )
                            }
                            Spacer(Modifier.width(1.dp))
                            Text(
                                text = badge.text,
                                fontSize = 9.sp, // D-242-fix18: slightly bigger
                                lineHeight = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = badge.contentColor,
                                maxLines = 1,
                                softWrap = false,
                            )
                            // D-242-fix16: Reduced from 6dp to 3dp — tighter,
                            // feels like one badge not two.
                            Spacer(Modifier.width(3.dp))
                            // ── Right segment (DUB) ──
                            if (sec.icon != null) {
                                Icon(
                                    imageVector = sec.icon,
                                    contentDescription = null,
                                    tint = sec.contentColor,
                                    modifier = Modifier.size(9.dp), // D-242-fix18: slightly bigger
                                )
                            }
                            Spacer(Modifier.width(1.dp))
                            Text(
                                text = sec.text,
                                fontSize = 9.sp, // D-242-fix18: slightly bigger
                                lineHeight = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = sec.contentColor,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                } else {
                    // ── Simple badge (no secondary) ──
                    // D-252: pointed tip on the innermost chip; flat otherwise.
                    // D-257: 1dp border (content color @ 50%) so the tag reads
                    // crisp against busy cover art (device feedback: tags need
                    // borders) — same treatment as the Browse score tag.
                    val chipShape = pointedShape ?: RoundedCornerShape(0.dp)
                    Surface(
                        color = badge.containerColor,
                        shape = chipShape,
                        border = BorderStroke(1.dp, badge.contentColor.copy(alpha = 0.5f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                start = 5.dp + tipPadding,
                                end = 5.dp + tipPadding,
                                top = 1.dp,
                                bottom = 1.dp,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            if (badge.icon != null) {
                                Icon(
                                    imageVector = badge.icon,
                                    contentDescription = null,
                                    tint = badge.contentColor,
                                    modifier = Modifier.size(9.dp), // D-242-fix18: slightly bigger
                                )
                            }
                            Text(
                                text = badge.text,
                                fontSize = 9.sp, // D-242-fix18: slightly bigger
                                lineHeight = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = badge.contentColor,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

// D-252: the legacy single `CoverBadge` composable (D-242-fix9) was removed —
// it had zero call sites since CoverBadgeRow superseded it.
