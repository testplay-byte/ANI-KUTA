package com.confused.anikuta.feature.animebrowse

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.navigation.NavKey
import com.confused.anikuta.feature.animedetails.AnimeDetailsKey
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.Motion
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import org.koin.compose.viewmodel.koinViewModel

/**
 * Browse screen — the home tab (Phase D.2).
 *
 * D.2: Cache-first — reads from browse_cache → displays instantly.
 * Pull-to-refresh: drag down at the top → vibration → release → refreshes.
 * 6-hour auto-update: checked on load (homepage only).
 *
 * CORE_RULES §22: smooth animations.
 * CORE_RULES §23: reactive state.
 */
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

    // D.2: Pull-to-refresh state.
    val context = LocalContext.current
    var pullDistance by remember { mutableFloatStateOf(0f) }
    var isPulling by remember { mutableStateOf(false) }
    val pullThreshold = 200f // pixels to trigger refresh

    // Smooth animation for the pull indicator.
    val pullProgress by animateFloatAsState(
        targetValue = (pullDistance / pullThreshold).coerceIn(0f, 1f),
        animationSpec = tween(Motion.DurationShort, easing = FastOutSlowInEasing),
        label = "pullProgress",
    )

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(title = "Browse", collapsed = collapsed)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                // Only allow pull-to-refresh when at the top of the grid.
                                if (gridState.firstVisibleItemIndex == 0 &&
                                    gridState.firstVisibleItemScrollOffset == 0
                                ) {
                                    isPulling = true
                                }
                            },
                            onDragEnd = {
                                if (isPulling && pullDistance >= pullThreshold) {
                                    // Vibrate + trigger refresh.
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
                                        vibrator.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK))
                                    }
                                    viewModel.refresh()
                                }
                                isPulling = false
                                pullDistance = 0f
                            },
                            onVerticalDrag = { _, dragAmount ->
                                if (isPulling && dragAmount > 0) {
                                    pullDistance = (pullDistance + dragAmount).coerceIn(0f, pullThreshold * 1.5f)
                                }
                            },
                        )
                    },
            ) {
                when (val s = state) {
                    is BrowseState.Loading -> LoadingScreen()
                    is BrowseState.Error -> ErrorScreen(s.message, viewModel::loadTrending)
                    is BrowseState.Success -> AnimeGrid(s.anime, gridState) { anime ->
                        onNavigate(AnimeDetailsKey.AniList(anime.id))
                    }
                }

                // D.2: Pull-to-refresh indicator.
                if (isPulling && pullDistance > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = (pullDistance * 0.5f).dp)
                            .size(36.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            progress = { pullProgress },
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }

                // D.2: Background refresh indicator (subtle, when auto-updating).
                if (isRefreshing && !isPulling) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp),
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
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
