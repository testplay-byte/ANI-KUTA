package com.confused.anikuta.feature.animesearch

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.confused.anikuta.core.designsystem.animation.coverSharedElement
import com.confused.anikuta.core.designsystem.animation.searchCoverKey
import com.confused.anikuta.core.designsystem.component.BackAction
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.LocalCardHeadingColor
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import org.koin.compose.koinInject
import com.confused.anikuta.core.preferences.AppPreferences
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Task 61 (round 21 — the category subpages): one CloudStream provider
 * shelf's OWN page, opened by tapping a section title on the search page.
 *
 * Layout (the user's spec):
 *  - the category's heading at the top (CollapsingHeader + back) with the
 *    provider's name as the subtitle;
 *  - ALL of the shelf's results in a GRID below;
 *  - as the user scrolls, the same approach-bottom pagination as the search
 *    page (pre-fetch ~2 rows before the end + the "Loading more…" footer).
 */
@Composable
fun CsCategoryScreen(
    providerName: String,
    sectionTitle: String,
    shelfIndex: Int,
    onBack: () -> Unit,
    // The same extension-details navigation the search page uses.
    onNavigateToExtensionAnime: (ExtensionAnime) -> Unit,
    viewModel: CsCategoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(providerName, shelfIndex) {
        viewModel.load(providerName, shelfIndex)
    }

    val gridState = rememberLazyGridState()
    val collapsed = gridState.firstVisibleItemIndex > 0 ||
        gridState.firstVisibleItemScrollOffset > 20

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        CollapsingHeader(
            title = sectionTitle,
            collapsed = collapsed,
            actions = { BackAction(onBack) },
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when (val s = state) {
                is CsCategoryUiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                is CsCategoryUiState.Empty -> CategoryPromptCard(
                    title = "Nothing here yet",
                    description = "$providerName returned no results for \"$sectionTitle\".",
                    icon = Icons.Filled.SearchOff,
                    actionLabel = "Try again",
                    onAction = { viewModel.load(providerName, shelfIndex) },
                )

                is CsCategoryUiState.Error -> CategoryPromptCard(
                    title = "Couldn't load this category",
                    description = "$providerName: ${s.message}",
                    icon = Icons.Filled.WarningAmber,
                    actionLabel = "Retry",
                    onAction = { viewModel.load(providerName, shelfIndex) },
                )

                is CsCategoryUiState.Content -> {
                    // The approach-bottom load-more (the same threshold +
                    // footer pattern as the search grids).
                    CategoryGrid(
                        items = s.items,
                        loadingMore = s.loadingMore,
                        canLoadMore = s.hasMore,
                        onLoadMore = viewModel::loadMore,
                        gridState = gridState,
                        onResultTap = onNavigateToExtensionAnime,
                    )
                }
            }

            ScrollBlurOverlay(
                scrollOffset = {
                    if (gridState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                    else gridState.firstVisibleItemScrollOffset.toFloat()
                },
                backgroundColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

// ── The grid ────────────────────────────────────────────────────────────────

/**
 * The category's results grid — 3 columns, the search page's card design.
 * Task 61: the same approach-bottom trigger + footer contract (the threshold
 * re-checks after every append via the item-count read).
 */
@Composable
private fun CategoryGrid(
    items: List<ExtensionAnime>,
    loadingMore: Boolean,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onResultTap: (ExtensionAnime) -> Unit,
) {
    // D-304 defense-in-depth: dedupe by the grid's key identity at render
    // time (the ViewModel already dedupes on append).
    val distinct = remember(items) {
        items.distinctBy { "${it.sourceKey ?: it.sourceId}:${it.url}" }
    }

    // Task 61: the approach-bottom pre-fetch (~2 rows before the end).
    val total = gridState.layoutInfo.totalItemsCount
    val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
    val nearBottom = total > 0 && lastVisible >= total - 6
    LaunchedEffect(nearBottom, total, canLoadMore) {
        if (canLoadMore && nearBottom) onLoadMore()
    }

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
        items(distinct, key = { "${it.sourceKey ?: it.sourceId}:${it.url}" }) { anime ->
            CategoryResultCard(anime, onResultTap)
        }
        if (loadingMore) {
            item(
                key = "load-more-footer",
                span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
            ) {
                CategoryLoadingMoreFooter()
            }
        }
    }
}

/** The footer (identical design to the search grids'). */
@Composable
private fun CategoryLoadingMoreFooter() {
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

// ── The card (the search page's ExtensionResultCard design) ─────────────────

@Composable
private fun CategoryResultCard(anime: ExtensionAnime, onClick: (ExtensionAnime) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "categoryCardScale",
    )
    // D-320/D-328: the same namespaced shared-element key as the search cards.
    val appPrefs = koinInject<AppPreferences>()
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
                // placeholder (see SearchScreen's ResultCard).
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
                color = LocalCardHeadingColor.current.takeIf { it != Color.Unspecified }
                    ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

// ── The empty / error prompt card (the search page's design) ─────────────────

@Composable
private fun CategoryPromptCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp),
        )
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(4.dp),
            ) {
                Text(actionLabel, fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
