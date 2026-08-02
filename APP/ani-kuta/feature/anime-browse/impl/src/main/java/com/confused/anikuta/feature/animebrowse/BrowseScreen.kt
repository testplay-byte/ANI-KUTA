package com.confused.anikuta.feature.animebrowse

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.navigation.NavKey
import com.confused.anikuta.feature.animedetails.AnimeDetailsKey
import com.confused.anikuta.core.designsystem.theme.Motion
import org.koin.compose.viewmodel.koinViewModel

/**
 * Browse screen — the home tab.
 *
 * Shows a grid of trending anime from AniList. Tapping a card navigates to Details.
 *
 * CORE_RULES §22: smooth animations — cards scale on press, no ripple.
 * CORE_RULES §23: reactive state — UI updates when data loads.
 * DESIGN-LANGUAGE.md: lime accent, warm darks, 16dp rounded corners.
 *
 * Customizable: grid columns, padding, card aspect ratio are all parameters
 * that can be made user-configurable in the future (D-037).
 */
@Composable
fun BrowseScreen(
    onNavigate: (NavKey) -> Unit,
    viewModel: BrowseViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        when (val s = state) {
            is BrowseState.Loading -> LoadingScreen()
            is BrowseState.Error -> ErrorScreen(s.message, viewModel::loadTrending)
            is BrowseState.Success -> AnimeGrid(s.anime) { anime ->
                onNavigate(AnimeDetailsKey(anime.id))
            }
        }
    }
}

@Composable
private fun AnimeGrid(anime: List<AniListAnime>, onClick: (AniListAnime) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(
            start = 12.dp,   // Reduced from 16dp (user feedback: too much padding)
            end = 12.dp,
            top = 12.dp,
            bottom = 90.dp,   // Space for the floating bottom nav
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
    // No ripple — clean press animation instead (user feedback)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(Motion.DurationShort, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "cardScale",
    )

    Column(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null, // No ripple
                onClick = { onClick(anime) },
            ),
    ) {
        // Cover image — 2:3 aspect ratio (standard anime poster)
        AsyncImage(
            model = anime.coverUrl,
            contentDescription = anime.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(16.dp)),
        )

        // Title — use onBackground (NOT default black) so it's visible on dark theme
        Text(
            text = anime.displayName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground, // Fixed: was default (black on dark)
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )

        // Score — lime accent (DESIGN-LANGUAGE.md), properly formatted
        anime.averageScore?.let { score ->
            Text(
                text = "★ ${score}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary, // Lime accent
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
