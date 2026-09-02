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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.confused.anikuta.core.designsystem.animation.coverSharedElement  // D-320
import com.confused.anikuta.core.designsystem.animation.searchCoverKey  // D-328
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.LocalCardDescriptionColor
import com.confused.anikuta.core.designsystem.theme.LocalCardHeadingColor
import com.confused.anikuta.core.designsystem.theme.Motion
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import org.koin.compose.koinInject  // D-320: prefs gate for the cover transition
import com.confused.anikuta.feature.animesearch.ExtensionAnime
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    // D-320: passes the full anime so the nav key can carry the cover/title
    // + the shared-element key (experimental cover transition).
    onNavigateToDetails: (AniListAnime) -> Unit,
    // Session 3: +sourceKey — "cloudstream:<provider>" for CloudStream results
    // (null = aniyomi; MainActivity branches the details destination on it).
    // Task 47: +year — the search-time release year (CloudStream providers
    // often set it on search responses but omit it on load(); the details
    // screen seeds SAnime.year with it as a fallback).
    onNavigateToExtensionAnime: (Long, String?, String, String, String?, Int?) -> Unit = { _, _, _, _, _, _ -> },
    // D-209: callback to open the Cloudflare WebView solver (launched from the
    // CloudflareBlocked error state). MainActivity launches CloudflareWebViewActivity.
    // Task 45: +userAgent — CloudStream providers must solve with the CS client's
    // pinned UA (clearance cookies are UA-bound); null = aniyomi default.
    onOpenCloudflareWebView: (url: String, sourceName: String, userAgent: String?) -> Unit = { _, _, _ -> },
    // Task 61 (round 21 — the category subpages): tapping a CloudStream
    // section's TITLE opens that category's own page (heading + grid +
    // infinite scroll). The provider name + the shelf's index identify the
    // category (the subpage resolves the shelf from provider.mainPage).
    onNavigateToCategory: (providerName: String, sectionTitle: String, sectionIndex: Int) -> Unit = { _, _, _ -> },
    viewModel: SearchViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val query by viewModel.query.collectAsState()
    val source by viewModel.source.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val recents by viewModel.recents.collectAsState()
    val pendingFilters by viewModel.pendingFilters.collectAsState()
    val trustedSources by viewModel.trustedSources.collectAsState()
    val selectedSourceId by viewModel.selectedSourceId.collectAsState()

    // Session 3 (CloudStream execution): the picker + top-bar label read both
    // ecosystems' selections.
    val csSources by viewModel.csSources.collectAsState()
    val selectedKind by viewModel.selectedKind.collectAsState()
    val selectedCsProvider by viewModel.selectedCsProvider.collectAsState()
    val selectedExtensionSourceName = when (selectedKind) {
        SelectedSourceKind.CLOUDSTREAM ->
            csSources.firstOrNull { it.providerName == selectedCsProvider }?.providerName
        SelectedSourceKind.ANIYOMI ->
            trustedSources.firstOrNull { it.id == selectedSourceId }?.name
    }

    // Task 45: CloudStream providers must solve Cloudflare with the CS client's
    // pinned USER_AGENT — cf_clearance is UA-bound and the plugin HTTP client
    // pins com.lagradost.cloudstream3.USER_AGENT, so the manual WebView must use
    // the SAME one or the earned cookie won't validate on replay.
    val cloudflareWebViewUserAgent: String? = when (selectedKind) {
        SelectedSourceKind.CLOUDSTREAM -> com.lagradost.cloudstream3.USER_AGENT
        SelectedSourceKind.ANIYOMI -> null
    }

    // Task 62 (round 22 — the randomization TRIGGER rework): every fresh
    // composition of the search screen (tab return, subpage return) calls
    // onPageEntered, which re-shuffles ONLY when the user actually LEFT the
    // search tab in between (SearchTabExitSignal — marked by MainActivity's
    // bottom-nav). Subpage/details returns and app resumes keep the
    // arrangement; cold reopens restore the persisted one.
    LaunchedEffect(Unit) {
        viewModel.onPageEntered()
    }

    val scrollState = rememberScrollState()
    val gridState = rememberLazyGridState()
    // Task 44: the sectioned CloudStream browse renders a LazyColumn of rows —
    // its own scroll state drives the top-bar collapse + blur overlay.
    val browseListState = rememberLazyListState()
    // Collapse when EITHER the scroll column OR the grid is scrolled past 20px.
    val collapsed = scrollState.value > 20 ||
        gridState.firstVisibleItemIndex > 0 ||
        gridState.firstVisibleItemScrollOffset > 20 ||
        browseListState.firstVisibleItemIndex > 0 ||
        browseListState.firstVisibleItemScrollOffset > 20

    var showFilterSheet by remember { mutableStateOf(false) }
    var showSourcePicker by remember { mutableStateOf(false) }
    val activeFilterCount = pendingFilters.activeCount

    // D-248: trending now auto-loads on first entry (SearchViewModel.init) — recents
    // are no longer Idle-exclusive (they render as the results grid's header item and
    // scroll away with the content; the top bar collapses to title + compact search
    // bar). Recents hide only when the user actually searches (query non-blank).

    // D-210: Auto-refresh when the user returns from the Cloudflare WebView.
    // The ViewModel sets pendingWebViewRefresh=true when the user taps "Open in
    // WebView". On resume (ON_RESUME lifecycle event), the ViewModel checks the
    // flag + auto-refreshes the search if true.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.onScreenResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
            // Extension source picker — opens when the extension button is tapped
            // (even if already selected, per user spec).
            onExtensionSourceClick = { showSourcePicker = true },
            selectedExtensionSourceName = selectedExtensionSourceName,
        )

        // Scrollable content
        // Task 61 (round 21): PULL-TO-REFRESH — the official m3 PullToRefreshBox
        // (the same component as the Library page). The pull only activates
        // while the inner lazy list is at the top; onRefresh reloads the current
        // mode's page 1 (the CS browse cache is invalidated first — the user's
        // "old cache deleted" spec). Any settled uiState emission dismisses the
        // indicator (content landed — that IS the refresh's visible result).
        var pullRefreshing by remember { mutableStateOf(false) }
        val ptrState = rememberPullToRefreshState()
        LaunchedEffect(uiState) { pullRefreshing = false }
        PullToRefreshBox(
            isRefreshing = pullRefreshing,
            onRefresh = {
                pullRefreshing = true
                viewModel.refreshCurrent()
            },
            state = ptrState,
            modifier = Modifier.fillMaxSize(),
        ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // D-242-fix3: Show recents ABOVE the results (collapsed by default)
            // when results are displayed. Previously recents only showed in Idle
            // state — they disappeared as soon as results loaded.
            //
            // D-305: mode-consistent rendering. The sealed UI state is shared by
            // both modes, so an AniList state must NEVER render in Extension mode
            // and vice versa. A mismatch can only be transient (every mode/source
            // change launches a fresh load via onSourceChange), so it renders as
            // Loading until the matching state lands — previously a stale
            // cross-mode state rendered (e.g. AniList results inside the
            // Extension tab after a fast switch).
            val effectiveState = when (source) {
                SearchSource.ANILIST -> when (uiState) {
                    is SearchUiState.ExtensionSuccess,
                    is SearchUiState.ExtensionBrowseSuccess,
                    is SearchUiState.ExtensionError,
                    is SearchUiState.ExtensionEmpty,
                    SearchUiState.ExtensionNotAvailable,
                    is SearchUiState.ExtensionNoBrowse,
                    is SearchUiState.CloudflareBlocked -> SearchUiState.Loading
                    else -> uiState
                }
                SearchSource.EXTENSION -> when (uiState) {
                    is SearchUiState.Success,
                    SearchUiState.Empty,
                    SearchUiState.Error -> SearchUiState.Loading
                    else -> uiState
                }
            }
            when (effectiveState) {
                is SearchUiState.Idle -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(top = 0.dp, bottom = 110.dp),
                    ) {
                        if (recents.isNotEmpty()) {
                            RecentSearchesCard(
                                recents = recents,
                                onPick = viewModel::onPickRecent,
                                onRemove = viewModel::onRemoveRecent,
                                onClear = viewModel::onClearRecents,
                            )
                        } else {
                            SearchPromptCard(
                                title = "Search anime",
                                description = if (source == SearchSource.EXTENSION) {
                                    "Select an extension source to browse its catalog."
                                } else {
                                    "Find series on AniList by title. Tap a result to view details."
                                },
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
                    title = "No extension source selected",
                    description = "Tap the Extension button at the top to pick a source to browse.",
                    icon = Icons.Filled.HourglassEmpty,
                )

                // Session 3 (CloudStream): the provider has no browse page — it
                // is search-only. Prompt to type, NOT an error/retry card.
                is SearchUiState.ExtensionNoBrowse -> {
                    val nb = effectiveState as SearchUiState.ExtensionNoBrowse
                    SearchPromptCard(
                        title = "${nb.sourceName} is search-only",
                        description = "This source has no browse page. Type a query above to search it.",
                        icon = Icons.Filled.HourglassEmpty,
                    )
                }

                is SearchUiState.ExtensionError -> {
                    val msg = (effectiveState as SearchUiState.ExtensionError).message
                    SearchPromptCard(
                        title = "Source error",
                        description = msg,
                        icon = Icons.Filled.SentimentDissatisfied,
                        actionLabel = "Retry",
                        onAction = { viewModel.retryExtensionSearch() },
                    )
                }

                is SearchUiState.CloudflareBlocked -> {
                    // D-209+D-212: Cloudflare blocked the request + the headless solver failed.
                    // D-212: shorter description + switched button colors (Refresh=primary,
                    // Open in WebView=tertiary — the user asked to switch them).
                    val cf = effectiveState as SearchUiState.CloudflareBlocked
                    SearchPromptCard(
                        title = "Cloudflare protection",
                        description = "${cf.sourceName} is behind Cloudflare. Solve it in " +
                            "the WebView, then come back.",
                        icon = Icons.Filled.Security,
                        actionLabel = "Refresh",
                        onAction = { viewModel.retryExtensionSearch() },
                        secondaryActionLabel = "Open in WebView",
                        onSecondaryAction = {
                            viewModel.onOpenWebView()
                            onOpenCloudflareWebView(cf.url, cf.sourceName, cloudflareWebViewUserAgent)
                        },
                    )
                }

                is SearchUiState.ExtensionEmpty -> {
                    // D-209+D-210+D-212: extension returned 0 results — shorter description +
                    // switched button colors (Refresh=primary, Open in WebView=tertiary).
                    val ee = effectiveState as SearchUiState.ExtensionEmpty
                    SearchPromptCard(
                        title = "No results from ${ee.sourceName}",
                        description = "0 results. If Cloudflare-protected, solve it in the WebView.",
                        icon = Icons.Filled.SearchOff,
                        actionLabel = "Refresh",
                        onAction = { viewModel.retryExtensionSearch() },
                        secondaryActionLabel = if (ee.sourceUrl != null) "Open in WebView" else null,
                        onSecondaryAction = if (ee.sourceUrl != null) {
                            {
                                viewModel.onOpenWebView()
                                onOpenCloudflareWebView(ee.sourceUrl, ee.sourceName, cloudflareWebViewUserAgent)
                            }
                        } else null,
                    )
                }

                is SearchUiState.Success -> {
                    val state = effectiveState as SearchUiState.Success
                    ResultsGrid(
                        results = state.results,
                        gridState = gridState,
                        onResultTap = onNavigateToDetails,
                        // Task 61 (round 21): the approach-bottom load-more.
                        loadingMore = state.loadingMore,
                        canLoadMore = state.hasMore,
                        onLoadMore = viewModel::loadMore,
                        // D-248: recents coexist with the default/trending results — shown as
                        // the grid's header (scrolls away with content; the top bar collapses
                        // to title + compact search bar). They hide only when the user
                        // actually searches (query non-blank → Loading).
                        recentsHeader = if (query.isBlank() && recents.isNotEmpty()) {
                            RecentsHeaderData(recents, viewModel::onPickRecent, viewModel::onRemoveRecent, viewModel::onClearRecents)
                        } else null,
                    )
                }

                is SearchUiState.ExtensionSuccess -> {
                    val state = effectiveState as SearchUiState.ExtensionSuccess
                    ExtensionResultsGrid(
                        results = state.results,
                        gridState = gridState,
                        onResultTap = { anime ->
                            onNavigateToExtensionAnime(
                                anime.sourceId,
                                anime.sourceKey,
                                anime.url,
                                anime.title,
                                anime.thumbnailUrl,
                                anime.year,
                            )
                        },
                        // Task 61 (round 21): the approach-bottom load-more.
                        loadingMore = state.loadingMore,
                        canLoadMore = state.hasMore,
                        onLoadMore = viewModel::loadMore,
                        recentsHeader = if (query.isBlank() && recents.isNotEmpty()) {
                            RecentsHeaderData(recents, viewModel::onPickRecent, viewModel::onRemoveRecent, viewModel::onClearRecents)
                        } else null,
                    )
                }

                // Task 44: SECTIONED CloudStream browse — every provider shelf
                // ("Latest Updated", "Most Popular", …) renders as its own titled
                // horizontal row (the device round-3 "sections in row format"
                // request); search results stay the flat grid above.
                is SearchUiState.ExtensionBrowseSuccess -> {
                    val browse = effectiveState as SearchUiState.ExtensionBrowseSuccess
                    ExtensionBrowseSections(
                        sections = browse.sections,
                        listState = browseListState,
                        // Task 61 (round 21): the provider's name + the shelf's
                        // ORIGINAL index — tapping a section TITLE opens the
                        // category subpage (heading + grid + infinite scroll).
                        providerName = browse.sourceName,
                        onNavigateToCategory = onNavigateToCategory,
                        onResultTap = { anime ->
                            onNavigateToExtensionAnime(
                                anime.sourceId,
                                anime.sourceKey,
                                anime.url,
                                anime.title,
                                anime.thumbnailUrl,
                                anime.year,
                            )
                        },
                        recentsHeader = if (query.isBlank() && recents.isNotEmpty()) {
                            RecentsHeaderData(recents, viewModel::onPickRecent, viewModel::onRemoveRecent, viewModel::onClearRecents)
                        } else null,
                    )
                }
            }

            ScrollBlurOverlay(
                scrollOffset = {
                    when (uiState) {
                        is SearchUiState.Success,
                        is SearchUiState.ExtensionSuccess -> {
                            if (gridState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                            else gridState.firstVisibleItemScrollOffset.toFloat()
                        }
                        is SearchUiState.ExtensionBrowseSuccess -> {
                            if (browseListState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                            else browseListState.firstVisibleItemScrollOffset.toFloat()
                        }
                        else -> scrollState.value.toFloat()
                    }
                },
                backgroundColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
        } // Task 61: closes the PullToRefreshBox wrapper.
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

    // ── Extension source picker sheet ──
    if (showSourcePicker) {
        ExtensionSourcePickerSheet(
            sources = trustedSources,
            sourceIcons = viewModel.sourceIcons.collectAsState().value,
            // Task 46 (device round 5, the double-checkmark bug): BOTH params
            // are now kind-gated symmetrically. The aniyomi id used to be passed
            // unconditionally, so after picking a CloudStream source the sheet
            // showed checkmarks on the (stale) aniyomi row AND the CS row at
            // once. Only the ecosystem the kind flag points at can render a
            // checkmark now; the other side's remembered selection stays
            // persisted for switching back, it just isn't "selected".
            selectedSourceId = if (selectedKind == SelectedSourceKind.ANIYOMI) selectedSourceId else null,
            onSelect = { id ->
                viewModel.onSelectExtensionSource(id)
                showSourcePicker = false
            },
            onDismiss = { showSourcePicker = false },
            // Session 3: the CloudStream section (hidden entirely when no CS
            // plugins are trusted + loaded — the sheet renders exactly as before).
            csSources = csSources,
            selectedCsProvider = if (selectedKind == SelectedSourceKind.CLOUDSTREAM) selectedCsProvider else null,
            onSelectCs = { name ->
                viewModel.onSelectCloudstreamSource(name)
                showSourcePicker = false
            },
        )
    }
}

// ── Results grid ──

// D-248/D-258: everything the results grids need to render the recents card as a header item.
private class RecentsHeaderData(
    val recents: List<String>,
    val onPick: (String) -> Unit,
    val onRemove: (String) -> Unit,
    val onClear: () -> Unit,
)

/**
 * Task 61 (round 21): the approach-bottom threshold — the load-more fires
 * ~2 grid rows (6 items) BEFORE the end, per the user's spec ("as the user
 * scrolls… he is about to reach the bottom, then the pagination will start").
 */
private const val LOAD_MORE_THRESHOLD_ITEMS = 6

/**
 * Task 61 (round 21): the shared approach-bottom trigger for the results
 * grids. Reading [totalItemsCount] / [visibleItemsInfo] in composition
 * subscribes to item-count changes, so after EVERY append the near-bottom
 * check re-runs (the LaunchedEffect keys change) — continuous infinite scroll
 * without snapshotFlow plumbing.
 */
@Composable
private fun ApproachBottomEffect(
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
) {
    val total = gridState.layoutInfo.totalItemsCount
    val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
    val nearBottom = total > 0 && lastVisible >= total - LOAD_MORE_THRESHOLD_ITEMS
    LaunchedEffect(nearBottom, total, canLoadMore) {
        if (canLoadMore && nearBottom) onLoadMore()
    }
}

/**
 * Task 61 (round 21): the grid's load-more footer — a spinner + "Loading
 * more…" label. Rendered as the grid's last (full-span) item while a page
 * is in flight, so a user who BEATS the approach-bottom pre-fetch sees the
 * loading animation at the bottom (the user's spec).
 */
@Composable
private fun LoadingMoreFooter() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Loading more…",
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResultsGrid(
    results: List<AniListAnime>,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onResultTap: (AniListAnime) -> Unit,
    recentsHeader: RecentsHeaderData? = null,
    // Task 61 (round 21): the approach-bottom load-more.
    loadingMore: Boolean = false,
    canLoadMore: Boolean = false,
    onLoadMore: () -> Unit = {},
) {
    ApproachBottomEffect(gridState, canLoadMore, onLoadMore)
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
        if (recentsHeader != null) {
            item(key = "recents-header", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                RecentSearchesCard(
                    recents = recentsHeader.recents,
                    onPick = recentsHeader.onPick,
                    onRemove = recentsHeader.onRemove,
                    onClear = recentsHeader.onClear,
                )
            }
        }
        items(results, key = { it.id }) { anime ->
            ResultCard(anime, onResultTap)
        }
        // Task 61 (round 21): the load-more footer (full span).
        if (loadingMore) {
            item(
                key = "load-more-footer",
                span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
            ) {
                LoadingMoreFooter()
            }
        }
    }
}

@Composable
private fun ResultCard(anime: AniListAnime, onClick: (AniListAnime) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(Motion.DurationShort, easing = FastOutSlowInEasing),
        label = "resultCardScale",
    )
    // D-320/D-328: shared-element key — screen-namespaced (cover:search:<url>)
    // so a Search card can never collide with a Library card showing the SAME
    // anime (pre-D-328 both built "cover:<url>", and the shared cover flew
    // between the two pages on every Library ⇄ Search switch). Null when the
    // experimental transition is disabled or the cover is missing.
    val appPrefs = koinInject<com.confused.anikuta.core.preferences.AppPreferences>()
    val transitionKey = if (appPrefs.coverTransitionEnabled) {
        searchCoverKey(anime.coverUrl)
    } else null

    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onClick(anime) },
            ),
    ) {
        AsyncImage(
            model = anime.coverUrl,
            contentDescription = anime.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                // Task 61 (round 21 — the performance round): a dim placeholder
                // surface behind the cover — the loading phase shows a quiet tone
                // (no white pop-in flash) + the global crossfade lands the image
                // softly. Cheap (no subcomposition) + zero jank.
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .coverSharedElement(transitionKey)
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
                color = LocalCardHeadingColor.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
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
    // D-209: optional primary action button (e.g. "Open in WebView" / "Retry").
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    // D-209: optional secondary action button (e.g. "Refresh" alongside "Open in WebView").
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
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
            color = LocalCardHeadingColor.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = description,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = LocalCardDescriptionColor.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            // D-209: allow more lines for the longer Cloudflare description.
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
        )
        // D-209+D-211: action buttons row — both buttons are filled (not outlined),
        // less rounded corners (4dp), proper coloring. Primary action = primary color,
        // secondary action = tertiary color (filled, not outlined).
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(actionLabel, fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
                if (secondaryActionLabel != null && onSecondaryAction != null) {
                    Button(
                        onClick = onSecondaryAction,
                        shape = RoundedCornerShape(4.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(secondaryActionLabel, fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

// ── Extension results grid (browse a source's popular/latest) ──

@Composable
private fun ExtensionResultsGrid(
    results: List<ExtensionAnime>,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onResultTap: (ExtensionAnime) -> Unit,
    recentsHeader: RecentsHeaderData? = null,
    // Task 61 (round 21): the approach-bottom load-more.
    loadingMore: Boolean = false,
    canLoadMore: Boolean = false,
    onLoadMore: () -> Unit = {},
) {
    // D-304 defense-in-depth: even though the ViewModel dedupes by URL, the
    // grid keys rows by "sourceId:url" — a duplicate would CRASH LazyGrid
    // (device-reported IllegalArgumentException). Dedupe again at render time
    // so no future code path can reintroduce the crash.
    val distinctResults = remember(results) {
        // Session 3: the identity is sourceKey-first (cloudstream:<provider>) with the
        // aniyomi Long id as fallback — unique across BOTH ecosystems.
        results.distinctBy { "${it.sourceKey ?: it.sourceId}:${it.url}" }
    }
    ApproachBottomEffect(gridState, canLoadMore, onLoadMore)
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
        if (recentsHeader != null) {
            item(key = "recents-header", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                RecentSearchesCard(
                    recents = recentsHeader.recents,
                    onPick = recentsHeader.onPick,
                    onRemove = recentsHeader.onRemove,
                    onClear = recentsHeader.onClear,
                )
            }
        }
        items(distinctResults, key = { "${it.sourceKey ?: it.sourceId}:${it.url}" }) { anime ->
            ExtensionResultCard(anime, onResultTap)
        }
        // Task 61 (round 21): the load-more footer (full span).
        if (loadingMore) {
            item(
                key = "load-more-footer",
                span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
            ) {
                LoadingMoreFooter()
            }
        }
    }
}

// ── Sectioned CloudStream browse (Task 44: shelves as titled rows) ──────────

/**
 * The sectioned CloudStream browse view: one titled horizontal row of cards per
 * provider shelf ("Latest Updated", "Most Popular", …) — the device round-3
 * "popular, latest and other sections in row format" request. Cards reuse the
 * flat-grid [ExtensionResultCard] at a fixed row width; taps route through the
 * same extension-details navigation.
 */
@Composable
private fun ExtensionBrowseSections(
    sections: List<ExtensionBrowseSection>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onResultTap: (ExtensionAnime) -> Unit,
    recentsHeader: RecentsHeaderData? = null,
    // Task 61 (round 21): the category subpages — tapping a section's TITLE
    // opens that category's page (heading + grid + infinite scroll).
    providerName: String = "",
    onNavigateToCategory: (providerName: String, sectionTitle: String, sectionIndex: Int) -> Unit = { _, _, _ -> },
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (recentsHeader != null) {
            item(key = "recents-header") {
                RecentSearchesCard(
                    recents = recentsHeader.recents,
                    onPick = recentsHeader.onPick,
                    onRemove = recentsHeader.onRemove,
                    onClear = recentsHeader.onClear,
                )
            }
        }

        sections.forEach { section ->
            // Task 63 (round 23 — F4): the key is the section's UNIQUE original
            // shelf index — stable across shuffles (a row moving positions keeps
            // its identity: LazyColumn treats it as moved, not recreated). The
            // pre-fix key mixed the list POSITION into it, so every re-shuffle
            // re-keyed every row (full recomposition) and duplicate titles were
            // only saved by the position prefix.
            item(key = "section-${section.shelfIndex}") {
                // D-304 defense-in-depth: dedupe within the row so LazyRow keys
                // stay unique even if a future code path reintroduces dupes.
                val distinct = remember(section) {
                    section.results.distinctBy { "${it.sourceKey ?: it.sourceId}:${it.url}" }
                }
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Task 61 (round 21): the TITLE is now a touch target —
                    // tapping it opens the category's subpage (heading + grid
                    // + infinite scroll); the shelf's ORIGINAL provider index
                    // (captured before the random shuffle) identifies it.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onNavigateToCategory(providerName, section.title, section.shelfIndex)
                            }
                            .padding(vertical = 2.dp),
                    ) {
                        Text(
                            text = section.title,
                            fontFamily = RobotoFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = LocalCardHeadingColor.current.takeIf { it != Color.Unspecified }
                                ?: MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        lazyRowItems(
                            distinct,
                            key = { "${it.sourceKey ?: it.sourceId}:${it.url}" },
                        ) { anime ->
                            Box(modifier = Modifier.width(110.dp)) {
                                ExtensionResultCard(anime, onResultTap)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtensionResultCard(anime: ExtensionAnime, onClick: (ExtensionAnime) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(Motion.DurationShort, easing = FastOutSlowInEasing),
        label = "extResultCardScale",
    )
    // D-320/D-328: shared-element key for extension results (cover:search:<url>
    // — same namespace as the AniList result cards above; the two lists never
    // compose together, so uniqueness within the screen is what matters).
    val appPrefs = koinInject<com.confused.anikuta.core.preferences.AppPreferences>()
    val transitionKey = if (appPrefs.coverTransitionEnabled) {
        searchCoverKey(anime.thumbnailUrl)
    } else null

    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onClick(anime) },
            ),
    ) {
        AsyncImage(
            model = anime.thumbnailUrl,
            contentDescription = anime.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                // Task 61 (round 21 — the performance round): the dim loading
                // placeholder (see ResultCard).
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .coverSharedElement(transitionKey)
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
                text = anime.title,
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = LocalCardHeadingColor.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}
