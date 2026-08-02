package com.confused.anikuta.feature.animedetails

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.anilist.model.AniListAnime
import org.koin.compose.viewmodel.koinViewModel

/**
 * Details screen — shows full anime details.
 *
 * Phase 4a: enhanced with banner image, better layout, scroll-behind-nav padding.
 *
 * CORE_RULES §22: smooth animations on content load.
 * CORE_RULES §23: reactive state from ViewModel.
 * DESIGN-LANGUAGE.md: banner image at top, cover + info side-by-side, 16dp rounded corners.
 */
@Composable
fun DetailsScreen(
    animeId: Int,
    onBack: () -> Unit,
    viewModel: DetailsViewModel = koinViewModel(),
) {
    LaunchedEffect(animeId) {
        viewModel.loadDetails(animeId)
    }

    val state by viewModel.state.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        when (val s = state) {
            is DetailsState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            is DetailsState.Error -> Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
            }

            is DetailsState.Success -> DetailsContent(s.anime, onBack)
        }
    }
}

@Composable
private fun DetailsContent(anime: AniListAnime, onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 90.dp), // Space for floating nav
    ) {
        // Banner image
        item {
            Box {
                anime.bannerImage?.let { banner ->
                    AsyncImage(
                        model = banner,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                    )
                }

                // Back button — simple text for now, Phase 4b will use proper icon
                Text(
                    text = "←",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .padding(16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onBack() }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }

        // Cover + info row
        item {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Cover image
                AsyncImage(
                    model = anime.coverUrl,
                    contentDescription = anime.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(120.dp)
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )

                // Info column
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        anime.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))

                    anime.seasonYear?.let { year ->
                        InfoText("$year")
                    }
                    anime.episodes?.let { eps ->
                        InfoText("$eps episodes")
                    }
                    anime.averageScore?.let { score ->
                        Text(
                            "★ $score",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    anime.status?.let { status ->
                        InfoText(status.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() })
                    }
                }
            }
        }

        // Genres
        anime.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
            item {
                Text(
                    genres.joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        // Description
        anime.description?.let { desc ->
            item {
                Text(
                    desc,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun InfoText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(2.dp))
}
