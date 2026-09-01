package com.confused.anikuta.feature.download

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.download.DownloadTask
import org.koin.compose.viewmodel.koinViewModel

/**
 * The Downloaded Files page — shows all completed downloads grouped by anime.
 *
 * D.6: Reached from the Downloads screen's "Downloaded" icon (only shows if the
 * user has at least one completed download).
 *
 * **Layout:**
 * - CollapsingHeader ("Downloaded")
 * - Anime-sectioned cards: each anime has a header (cover + title + episode
 *   count) + a list of downloaded episodes with delete buttons.
 * - Tap an episode → plays it offline (wired by the host via [onPlayEpisode]).
 * - Delete button per episode → removes the file + the task.
 * - Delete-all button per anime → removes every downloaded episode.
 *
 * Ported from the old project's `DownloadedFilesScreen.kt`.
 *
 * @param onBack Called when the user taps the back arrow.
 * @param onPlayEpisode Called when the user taps a downloaded episode. Receives
 *   the mainId + the episodeKey (the host uses these to look up the content:// URI).
 */
@Composable
fun DownloadedFilesScreen(
    onBack: () -> Unit,
    onPlayEpisode: (mainId: String, episodeKey: String) -> Unit = { _, _ -> },
    onNavigateToDetails: (mainId: String) -> Unit = {},
    viewModel: DownloadViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemIndex > 0 ||
        lazyListState.firstVisibleItemScrollOffset > 20

    val downloaded = state.downloaded

    Column(modifier = Modifier.fillMaxSize()) {
        CollapsingHeader(
            title = "Downloaded",
            collapsed = collapsed,
            actions = {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
        )

        if (downloaded.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No downloaded files",
                        fontFamily = RobotoFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Downloaded episodes will appear here",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                downloaded.forEach { (animeKey, episodes) ->
                    item(key = "downloaded_${animeKey.contentId}") {
                        DownloadedAnimeCard(
                            animeKey = animeKey,
                            episodes = episodes,
                            onPlay = { episodeKey ->
                                onPlayEpisode(animeKey.mainId, episodeKey)
                            },
                            onDelete = { episodeKey ->
                                viewModel.deleteEpisode(animeKey.mainId, episodeKey)
                            },
                            onDeleteAll = { viewModel.deleteAnime(animeKey.mainId) },
                            onNavigateToDetails = { onNavigateToDetails(animeKey.mainId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadedAnimeCard(
    animeKey: DownloadedAnimeKey,
    episodes: List<DownloadTask>,
    onPlay: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDeleteAll: () -> Unit,
    onNavigateToDetails: () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(true) }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    ) {
        Column {
            // Header: cover + title + count + delete-all + expand
            // D.FIX: Title tap → navigate to details. Only the chevron toggles expand.
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!animeKey.coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = animeKey.coverUrl,
                        contentDescription = animeKey.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 44.dp, height = 62.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onNavigateToDetails() },
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToDetails() },
                ) {
                    Text(
                        animeKey.title,
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${episodes.size} episode${if (episodes.size != 1) "s" else ""}",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDeleteAll, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete all",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                // D.FIX: Only the chevron button toggles expand/collapse.
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Episode list.
            if (expanded) {
                episodes.sortedBy { it.episode.episodeNumber }.forEach { task ->
                    // D-151-fix: 2-line row — episode info on top, metadata chips below.
                    // Server name uses primary color (matches ResolverSheet ServerCard).
                    // Audio chip uses secondaryContainer (matches ResolverSheet audio chips).
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onPlay(task.episode.episodeKey) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            // ── Top line: EP label + episode name ──
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "EP ${task.episode.episodeNumber.toInt()}",
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(48.dp),
                                )
                                Text(
                                    task.episode.name.ifBlank {
                                        "Episode ${task.episode.episodeNumber.toInt()}"
                                    },
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            // ── Bottom line: server (primary) + audio chip + quality chip + size ──
                            val hasServer = task.videoServer.isNotBlank()
                            val hasAudio = task.videoAudio.isNotBlank()
                            val hasQuality = task.videoQuality.isNotBlank()
                            val hasSize = task.totalBytes > 0
                            if (hasServer || hasAudio || hasQuality || hasSize) {
                                Row(
                                    modifier = Modifier.padding(top = 3.dp, start = 48.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // Server name — D-215: now has a proper background (primary.copy(0.15f))
                                    // matching the Downloads page InfoPill(highlight=true) style.
                                    // Task 60 (round 20): the chip FLEXES — weight(1f, fill = false)
                                    // + ellipsis, so a long resolver server name shortens with a
                                    // trailing "…" instead of overflowing the row (the user's
                                    // "three dots" spec; applies to BOTH stacks' downloaded rows).
                                    if (hasServer) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            modifier = Modifier.weight(1f, fill = false),
                                        ) {
                                            Text(
                                                task.videoServer,
                                                fontFamily = RobotoFamily,
                                                fontSize = 10.sp,
                                                lineHeight = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    // Audio version — secondaryContainer chip (matches ResolverSheet).
                                    if (hasAudio) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(6.dp),
                                        ) {
                                            Text(
                                                task.videoAudio.uppercase(),
                                                fontFamily = RobotoFamily,
                                                fontSize = 9.sp,
                                                lineHeight = 13.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                                maxLines = 1,
                                                softWrap = false,
                                            )
                                        }
                                    }
                                    // Quality chip — D-215: changed to outlineVariant (was surfaceVariant)
                                    // for consistency with the Downloads page InfoPill style.
                                    if (hasQuality) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                        ) {
                                            Text(
                                                task.videoQuality,
                                                fontFamily = RobotoFamily,
                                                fontSize = 9.sp,
                                                lineHeight = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                                maxLines = 1,
                                                softWrap = false,
                                            )
                                        }
                                    }
                                    // File size — D-215: now has a proper background (secondaryContainer)
                                    // matching the Downloads page SizePill style.
                                    if (hasSize) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                        ) {
                                            Text(
                                                formatBytes(task.totalBytes),
                                                fontFamily = RobotoFamily,
                                                fontSize = 9.sp,
                                                lineHeight = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                                maxLines = 1,
                                                softWrap = false,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        IconButton(
                            onClick = { onDelete(task.episode.episodeKey) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete episode",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
