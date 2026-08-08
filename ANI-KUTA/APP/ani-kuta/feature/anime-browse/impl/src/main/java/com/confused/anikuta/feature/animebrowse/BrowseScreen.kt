package com.confused.anikuta.feature.animebrowse

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.common.HapticHelper
import com.confused.anikuta.core.navigation.NavKey
import com.confused.anikuta.feature.animedetails.AnimeDetailsKey
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.Motion
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import org.koin.compose.viewmodel.koinViewModel

/**
 * Browse screen — the home tab.
 *
 * Cache-first: reads from browse_cache → displays instantly, then background-
 * fetches if the 6-hour TTL expired. Pull-to-refresh forces a network fetch
 * regardless of cache state.
 *
 * Pull-to-refresh uses the official Material 3 [PullToRefreshBox], which
 * installs its own [androidx.compose.ui.input.nestedscroll.NestedScrollConnection].
 * This cooperates with the inner [LazyVerticalGrid]'s scroll: the pull gesture
 * ONLY activates when the grid has reached the top AND the user continues
 * dragging down. No spinner on normal upward scroll, no fling jank.
 *
 * Haptic feedback fires once when the pull crosses the refresh threshold
 * (distanceFraction >= 1f) via [HapticHelper.stageCross] — no
 * [android.os.Vibrator] / VIBRATE-permission-dependent code path.
 *
 * CORE_RULES §22: smooth animations. §23: reactive state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    onNavigate: (NavKey) -> Unit,
    viewModel: BrowseViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val gridState = rememberLazyGridState()
    val collapsed = gridState.firstVisibleItemIndex > 0 ||
        gridState.firstVisibleItemScrollOffset > 20

    // DB-7: provide debug context for the Current Screen tab.
    val updateDebugContext = com.confused.anikuta.core.debugapi.LocalDebugContextUpdater.current
    val browseCtx = remember(state) {
        val animeCount = when (state) {
            is BrowseState.Success -> (state as BrowseState.Success).anime.size
            else -> 0
        }
        com.confused.anikuta.core.debugapi.DebugContext(
            screenName = "Browse",
            screenData = mapOf(
                "state" to (state::class.simpleName ?: "Unknown"),
                "animeCount" to animeCount.toString(),
                "isRefreshing" to isRefreshing.toString(),
            ),
        )
    }
    androidx.compose.runtime.LaunchedEffect(browseCtx) { updateDebugContext(browseCtx) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { updateDebugContext(null) }
    }

    val ptrState = rememberPullToRefreshState()
    val context = LocalContext.current

    // Fire a haptic exactly once when the pull first crosses the refresh
    // threshold (distanceFraction >= 1f). The LaunchedEffect re-runs only on
    // the false → true transition, so it never buzzes continuously.
    // Uses HapticHelper (Vibrator service) for reliability across devices +
    // battery-saver modes (performHapticFeedback can be silenced by OEMs).
    val thresholdCrossed = ptrState.distanceFraction >= 1f
    LaunchedEffect(thresholdCrossed) {
        if (thresholdCrossed) {
            HapticHelper.stageCross(context)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(title = "Browse", collapsed = collapsed)

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh() },
                state = ptrState,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (val s = state) {
                    is BrowseState.Loading -> LoadingScreen()
                    is BrowseState.Error -> ErrorScreen(s.message, viewModel::loadTrending)
                    is BrowseState.Success -> AnimeGrid(s.anime, gridState) { anime ->
                        onNavigate(AnimeDetailsKey.AniList(anime.id))
                    }
                }

                // Scroll-blur overlay stays as a child of the box content — drawn
                // UNDER the M3 indicator (which PullToRefreshBox paints on top).
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
}

@Composable
private fun AnimeGrid(
    anime: List<AniListAnime>,
    gridState: LazyGridState,
    onClick: (AniListAnime) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 12.dp,
            bottom = 90.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(anime, key = { it.id }) { item ->
            AnimeCard(item, onClick)
        }
    }
}

@Composable
private fun AnimeCard(anime: AniListAnime, onClick: (AniListAnime) -> Unit) {
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
                onClick = { onClick(anime) },
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
            fontFamily = RobotoFamily,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )

        anime.averageScore?.let { score ->
            Text(
                text = "★ ${score}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = RobotoFamily,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 2.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Failed to load",
                fontFamily = RobotoFamily,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
