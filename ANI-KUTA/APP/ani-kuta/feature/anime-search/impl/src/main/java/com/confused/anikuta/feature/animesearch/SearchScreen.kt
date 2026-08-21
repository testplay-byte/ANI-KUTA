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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.Motion
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
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
@Composable
fun SearchScreen(
    onNavigateToDetails: (Int) -> Unit,
    onNavigateToExtensionAnime: (Long, String, String, String?) -> Unit = { _, _, _, _ -> },
    // D-209: callback to open the Cloudflare WebView solver (launched from the
    // CloudflareBlocked error state). MainActivity launches CloudflareWebViewActivity.
    onOpenCloudflareWebView: (url: String, sourceName: String) -> Unit = { _, _ -> },
    viewModel: SearchViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val query by viewModel.query.collectAsState()
    val source by viewModel.source.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val recents by viewModel.recents.collectAsState()
    val recentsCollapsed by viewModel.recentsCollapsed.collectAsState()
    val pendingFilters by viewModel.pendingFilters.collectAsState()
    val trustedSources by viewModel.trustedSources.collectAsState()
    val selectedSourceId by viewModel.selectedSourceId.collectAsState()

    val scrollState = rememberScrollState()
    val gridState = rememberLazyGridState()
    // Collapse when EITHER the scroll column OR the grid is scrolled past 20px.
    val collapsed = scrollState.value > 20 ||
        gridState.firstVisibleItemIndex > 0 ||
        gridState.firstVisibleItemScrollOffset > 20

    var showFilterSheet by remember { mutableStateOf(false) }
    var showSourcePicker by remember { mutableStateOf(false) }
    val activeFilterCount = pendingFilters.activeCount

    // D-242-fix4: Always load trending on first enter (AniList + no query).
    // The recents card now renders ABOVE results in all states (collapsed),
    // so loading trending no longer hides the history.
    LaunchedEffect(Unit) {
        if (source == SearchSource.ANILIST &&
            query.isBlank() &&
            uiState is SearchUiState.Idle
        ) {
            viewModel.onSourceChange(SearchSource.ANILIST)
        }
    }

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
            selectedExtensionSourceName = trustedSources.firstOrNull { it.id == selectedSourceId }?.name,
        )

        // Scrollable content
        Box(modifier = Modifier.fillMaxSize()) {
            // D-242-fix3: Show recents ABOVE the results (collapsed by default)
            // when results are displayed. Previously recents only showed in Idle
            // state — they disappeared as soon as results loaded.
            when (uiState) {
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
                                collapsed = recentsCollapsed,
                                onToggleCollapsed = viewModel::toggleRecentsCollapsed,
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

                SearchUiState.Loading -> {
                    // Show collapsed recents above the loading spinner.
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (recents.isNotEmpty()) {
                            RecentSearchesCard(
                                recents = recents,
                                collapsed = true, // always collapsed when loading/results
                                onToggleCollapsed = viewModel::toggleRecentsCollapsed,
                                onPick = viewModel::onPickRecent,
                                onRemove = viewModel::onRemoveRecent,
                                onClear = viewModel::onClearRecents,
                            )
                        }
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                SearchUiState.Empty -> {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                        if (recents.isNotEmpty()) {
                            RecentSearchesCard(
                                recents = recents,
                                collapsed = true,
                                onToggleCollapsed = viewModel::toggleRecentsCollapsed,
                                onPick = viewModel::onPickRecent,
                                onRemove = viewModel::onRemoveRecent,
                                onClear = viewModel::onClearRecents,
                            )
                        }
                        SearchPromptCard(
                            title = "No results",
                            description = "Try a different title or sort option.",
                            icon = Icons.Filled.SearchOff,
                        )
                    }
                }

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

                is SearchUiState.ExtensionError -> {
                    val msg = (uiState as SearchUiState.ExtensionError).message
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
                    val cf = uiState as SearchUiState.CloudflareBlocked
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
                            onOpenCloudflareWebView(cf.url, cf.sourceName)
                        },
                    )
                }

                is SearchUiState.ExtensionEmpty -> {
                    // D-209+D-210+D-212: extension returned 0 results — shorter description +
                    // switched button colors (Refresh=primary, Open in WebView=tertiary).
                    val ee = uiState as SearchUiState.ExtensionEmpty
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
                                onOpenCloudflareWebView(ee.sourceUrl, ee.sourceName)
                            }
                        } else null,
                    )
                }

                is SearchUiState.Success -> {
                    // D-242-fix3: Show collapsed recents above results.
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (recents.isNotEmpty()) {
                            RecentSearchesCard(
                                recents = recents,
                                collapsed = true,
                                onToggleCollapsed = viewModel::toggleRecentsCollapsed,
                                onPick = viewModel::onPickRecent,
                                onRemove = viewModel::onRemoveRecent,
                                onClear = viewModel::onClearRecents,
                            )
                        }
                        val results = (uiState as SearchUiState.Success).results
                        ResultsGrid(
                            results = results,
                            gridState = gridState,
                            onResultTap = onNavigateToDetails,
                        )
                    }
                }

                is SearchUiState.ExtensionSuccess -> {
                    // D-242-fix3: Show collapsed recents above extension results.
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (recents.isNotEmpty()) {
                            RecentSearchesCard(
                                recents = recents,
                                collapsed = true,
                                onToggleCollapsed = viewModel::toggleRecentsCollapsed,
                                onPick = viewModel::onPickRecent,
                                onRemove = viewModel::onRemoveRecent,
                                onClear = viewModel::onClearRecents,
                            )
                        }
                        val results = (uiState as SearchUiState.ExtensionSuccess).results
                        ExtensionResultsGrid(
                            results = results,
                            gridState = gridState,
                            onResultTap = { anime ->
                                onNavigateToExtensionAnime(anime.sourceId, anime.url, anime.title, anime.thumbnailUrl)
                            },
                        )
                    }
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

    // ── Extension source picker sheet ──
    if (showSourcePicker) {
        ExtensionSourcePickerSheet(
            sources = trustedSources,
            sourceIcons = viewModel.sourceIcons.collectAsState().value,
            selectedSourceId = selectedSourceId,
            onSelect = { id ->
                viewModel.onSelectExtensionSource(id)
                showSourcePicker = false
            },
            onDismiss = { showSourcePicker = false },
        )
    }
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
        items(results, key = { "${it.sourceId}:${it.url}" }) { anime ->
            ExtensionResultCard(anime, onResultTap)
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
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}
