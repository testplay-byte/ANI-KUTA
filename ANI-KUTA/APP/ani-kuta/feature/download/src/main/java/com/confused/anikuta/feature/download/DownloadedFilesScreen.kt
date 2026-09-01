package com.confused.anikuta.feature.download

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.rotate
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

/**
 * One anime's downloaded-episode card.
 *
 * Task 61 (round 21) — the downloaded-section UI rework, per the device spec:
 *  - ALL cards render COLLAPSED by default; the expand/collapse chevron (on
 *    the LEFT of the delete button, which is now the FAR-right control)
 *    toggles them with a smooth [animateContentSize] reveal;
 *  - the episode count renders as a highlighted TAG (primary-tinted pill)
 *    under the title — not a plain text line;
 *  - separator lines between the episode rows;
 *  - TWO-STEP delete on both the per-episode buttons AND the delete-all
 *    button: the first tap morphs the button into its confirm state (error
 *    tint + [Icons.Filled.DeleteForever]); tapping THAT deletes; tapping
 *    anywhere else (the row, the header, the chevron, another delete button)
 *    reverts it to the default state. No full-screen dialog.
 */
private const val CONFIRM_DELETE_ALL = "__delete_all__"

@Composable
private fun DownloadedAnimeCard(
    animeKey: DownloadedAnimeKey,
    episodes: List<DownloadTask>,
    onPlay: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDeleteAll: () -> Unit,
    onNavigateToDetails: () -> Unit = {},
) {
    // Task 61: collapsed by default — the round-21 spec ("by default have all
    // the downloaded episodes collapsed so they will not be shown directly").
    var expanded by remember { mutableStateOf(false) }
    // Task 61: the two-step delete state — the episodeKey (or the
    // [CONFIRM_DELETE_ALL] marker) whose delete button is armed; null = all
    // default. Any outside interaction clears it.
    var confirmDeleteKey by remember { mutableStateOf<String?>(null) }
    val sortedEpisodes = remember(episodes) {
        episodes.sortedBy { it.episode.episodeNumber }
    }
    // Task 61: the chevron rotates open (0° → 90°) with the expand animation.
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "downloadedChevronRotation",
    )

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    ) {
        // Task 61: the smooth expand/collapse animation for the episode list.
        Column(modifier = Modifier.animateContentSize()) {
            // Header: cover + title + count tag + expand (LEFT) + delete (RIGHT)
            // D.FIX: Title tap → navigate to details. Only the chevron toggles expand.
            // Task 61: every header interaction disarms a pending delete-confirm.
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable {
                        confirmDeleteKey = null
                        onNavigateToDetails()
                    }
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
                            .clickable {
                                confirmDeleteKey = null
                                onNavigateToDetails()
                            },
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            confirmDeleteKey = null
                            onNavigateToDetails()
                        },
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
                    Spacer(Modifier.height(4.dp))
                    // Task 62 (round 22): the count as a highlighted TAG — the
                    // primary-tinted pill style (matches the resolver's server
                    // chip). The device round asked for the FULL detail in the
                    // tag ("(5 Episodes Downloaded)"), not the old "5 EP".
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = if (episodes.size == 1) {
                                "(1 Episode Downloaded)"
                            } else {
                                "(${episodes.size} Episodes Downloaded)"
                            },
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
                // Task 61: the expand/collapse button is now on the LEFT of the
                // delete button (the round-21 spec: "on the left of it, it
                // should show the expand and collapse button").
                IconButton(
                    onClick = {
                        confirmDeleteKey = null
                        expanded = !expanded
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp).rotate(chevronRotation),
                    )
                }
                // Task 61: the two-step delete-all — armed state morphs the icon
                // (DeleteForever) + error tint; the second tap deletes.
                IconButton(
                    onClick = {
                        if (confirmDeleteKey == CONFIRM_DELETE_ALL) {
                            confirmDeleteKey = null
                            onDeleteAll()
                        } else {
                            confirmDeleteKey = CONFIRM_DELETE_ALL
                        }
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    val armed = confirmDeleteKey == CONFIRM_DELETE_ALL
                    Icon(
                        imageVector = if (armed) Icons.Filled.DeleteForever else Icons.Filled.Delete,
                        contentDescription = if (armed) "Confirm delete all" else "Delete all",
                        tint = if (armed) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Episode list (Task 61: collapsed by default; separator lines
            // between the rows; the per-episode two-step delete below).
            if (expanded) {
                sortedEpisodes.forEachIndexed { index, task ->
                    // Task 61: separator lines between the individual episodes.
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        )
                    }
                    DownloadedEpisodeRow(
                        task = task,
                        armed = confirmDeleteKey == task.episode.episodeKey,
                        onArmDelete = {
                            // Arming another episode's button disarms any other.
                            confirmDeleteKey =
                                if (confirmDeleteKey == task.episode.episodeKey) null
                                else task.episode.episodeKey
                        },
                        onPlayRow = {
                            // Task 61: tapping the row plays AND disarms.
                            confirmDeleteKey = null
                            onPlay(task.episode.episodeKey)
                        },
                        onDeleteConfirmed = {
                            confirmDeleteKey = null
                            onDelete(task.episode.episodeKey)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Task 61: one episode row inside [DownloadedAnimeCard] — the 2-line layout
 * (episode info + metadata chips) is the confirmed-good round-20 rendering,
 * byte-identical; the row click + the delete button's two-step state are the
 * round-21 changes.
 */
@Composable
private fun DownloadedEpisodeRow(
    task: DownloadTask,
    armed: Boolean,
    onArmDelete: () -> Unit,
    onPlayRow: () -> Unit,
    onDeleteConfirmed: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable { onPlayRow() }
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
        // Task 61: the per-episode two-step delete — the FIRST tap arms (the
        // icon morphs to DeleteForever + error tint), the SECOND tap on the
        // SAME button deletes; any other tap on the card disarms (handled by
        // the card's confirmDeleteKey resets).
        IconButton(
            onClick = {
                if (armed) onDeleteConfirmed() else onArmDelete()
            },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = if (armed) Icons.Filled.DeleteForever else Icons.Filled.Delete,
                contentDescription = if (armed) "Confirm delete episode" else "Delete episode",
                tint = if (armed) MaterialTheme.colorScheme.error
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
