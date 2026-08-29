package com.confused.anikuta.feature.cloudstreamcontent

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.designsystem.component.BackAction
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.data.cloudstream.content.CsContentDetails
import com.confused.anikuta.data.cloudstream.content.CsEpisode
import org.koin.compose.viewmodel.koinViewModel

/**
 * The CloudStream CONTENT details screen (session 3, provider-execution phase 1
 * — the "go to the details page with these" device-round request).
 *
 * Renders everything MainAPI.load() returned through the repository:
 * - Hero: poster (or banner) + title + type chip + year · score · status · duration.
 * - Tags/genres chips.
 * - Description.
 * - Episodes: season-grouped list for series/anime (with Dub/Sub labels for
 *   anime track lists), or a single Movie entry for movie types.
 * - A deliberate, honest "playback next session" note — episodes display
 *   phase-1 data only (loadLinks + the real extractors are doc 23 §7's next
 *   session; no dead buttons).
 *
 * Design language: RobotoFamily ExtraBold hierarchy, translucent
 * surfaceVariant cards, 12/16dp rounded corners, lime accent — identical to the
 * extension/anime detail pages. CORE_RULES §22/§23.
 */
@Composable
fun CloudstreamContentDetailsScreen(
    providerName: String,
    contentUrl: String,
    title: String,
    thumbnailUrl: String? = null,
    onBack: () -> Unit,
    viewModel: CloudstreamContentDetailsViewModel = koinViewModel(),
) {
    LaunchedEffect(providerName, contentUrl) {
        viewModel.load(providerName, contentUrl)
    }

    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val collapsed = listState.firstVisibleItemIndex > 0 ||
        listState.firstVisibleItemScrollOffset > 20

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = title,
                collapsed = collapsed,
                actions = { BackAction(onBack) },
            )

            when (val state = uiState) {
                is CloudstreamContentDetailsViewModel.UiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                is CloudstreamContentDetailsViewModel.UiState.Error -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.SentimentDissatisfied,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp),
                        )
                        Text(
                            text = "Couldn't load this content",
                            fontFamily = RobotoFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Text(
                            text = state.message,
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp),
                        )
                        TextButton(
                            onClick = { viewModel.retry(providerName, contentUrl) },
                            modifier = Modifier.padding(top = 12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Retry", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                is CloudstreamContentDetailsViewModel.UiState.Success -> Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 110.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "hero") { ContentHero(state.details, thumbnailUrl) }

                        if (state.details.tags.isNotEmpty()) {
                            item(key = "tags") { TagsRow(state.details.tags) }
                        }

                        state.details.description?.takeIf { it.isNotBlank() }?.let { plot ->
                            item(key = "description") {
                                DetailSection(title = "Description") {
                                    Text(
                                        text = plot,
                                        fontFamily = RobotoFamily,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }

                        item(key = "playback-note") {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayCircleOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "This is the browse phase — link loading and playback " +
                                            "arrive in the next update.",
                                        fontFamily = RobotoFamily,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        // ── Episodes (series) / Movie entry ──
                        if (state.details.isMovie) {
                            item(key = "movie") {
                                DetailSection(title = "Movie") {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Movie,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(22.dp),
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = state.details.title,
                                                    fontFamily = RobotoFamily,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = state.details.durationMinutes?.let { "$it min" }
                                                        ?: "Single video",
                                                    fontFamily = RobotoFamily,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(top = 2.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (state.details.episodes.isNotEmpty()) {
                            // Season-grouped (null/1 seasons render flat — a "Season 1"
                            // header on single-season content is noise).
                            val seasons = state.details.episodes.groupBy { it.season ?: 1 }
                                    .toSortedMap()
                            seasons.forEach { (season, episodes) ->
                                val multi = seasons.size > 1
                                if (multi) {
                                    item(key = "season-$season-header") {
                                        Text(
                                            text = "Season $season",
                                            fontFamily = RobotoFamily,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
                                        )
                                    }
                                } else {
                                    item(key = "episodes-header") {
                                        Text(
                                            text = "Episodes (${episodes.size})",
                                            fontFamily = RobotoFamily,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
                                        )
                                    }
                                }
                                episodes.forEach { episode ->
                                    item(key = "ep-${season}-${episode.episode}-${episode.dubLabel}-${episode.data.hashCode()}") {
                                        EpisodeRow(episode)
                                    }
                                }
                            }
                        } else {
                            item(key = "no-episodes") {
                                DetailSection(title = "Episodes") {
                                    Text(
                                        text = "This provider returned no episode list.",
                                        fontFamily = RobotoFamily,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        // Source footer — which provider served this content.
                        item(key = "source-footer") {
                            Text(
                                text = "Source: ${state.details.providerName} (CloudStream)",
                                fontFamily = RobotoFamily,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                            )
                        }
                    }

                    ScrollBlurOverlay(
                        scrollOffset = {
                            if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                            else listState.firstVisibleItemScrollOffset.toFloat()
                        },
                        backgroundColor = MaterialTheme.colorScheme.background,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Pieces
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ContentHero(details: CsContentDetails, fallbackThumb: String?) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            AsyncImage(
                model = details.posterUrl ?: fallbackThumb,
                contentDescription = details.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(100.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    details.type?.let {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = it,
                                fontFamily = RobotoFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                }
                Text(
                    text = details.title,
                    fontFamily = RobotoFamily,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
                val metaLine = buildString {
                    details.year?.let { append(it) }
                    details.score10?.let { if (isNotEmpty()) append(" · "); append("★ $it/10") }
                    details.status?.let { if (isNotEmpty()) append(" · "); append(it) }
                    details.durationMinutes?.let { if (isNotEmpty()) append(" · "); append("$it min") }
                }
                if (metaLine.isNotEmpty()) {
                    Text(
                        text = metaLine,
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (details.contentRating != null) {
                    Text(
                        text = details.contentRating,
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TagsRow(tags: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tags.forEach { tag ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = tag,
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = title,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun EpisodeRow(episode: CsEpisode) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = "EP ${episode.episode ?: "?"}",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.name ?: "Episode ${episode.episode ?: "?"}",
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                episode.dubLabel?.let {
                    Text(
                        text = it,
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
        }
    }
}
