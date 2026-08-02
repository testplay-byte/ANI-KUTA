package com.confused.anikuta.feature.animedetails

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.anilist.model.AniListAnime
import org.koin.compose.viewmodel.koinViewModel

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

    when (val s = state) {
        is DetailsState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        is DetailsState.Error -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(s.message, color = MaterialTheme.colorScheme.error)
        }
        is DetailsState.Success -> DetailsContent(s.anime, onBack)
    }
}

@Composable
private fun DetailsContent(anime: AniListAnime, onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            Box {
                // Banner
                anime.bannerImage?.let { banner ->
                    AsyncImage(
                        model = banner,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                    )
                }
                // Back button
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text("←", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Cover
                AsyncImage(
                    model = anime.coverUrl,
                    contentDescription = anime.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(120.dp)
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
                // Info
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        anime.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    anime.seasonYear?.let { year ->
                        Text("$year", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    anime.episodes?.let { eps ->
                        Text("$eps episodes", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    anime.averageScore?.let { score ->
                        Text("★ ${score}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

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
