package com.confused.anikuta.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.confused.anikuta.core.designsystem.component.BackAction
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.EmptyState
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.component.SettingsGroupCard
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.playbackcache.CacheRanges
import com.confused.anikuta.core.playbackcache.PlaybackCachePreferences
import com.confused.anikuta.core.playbackcache.PlaybackCacheStore
import org.koin.compose.viewmodel.koinViewModel

/**
 * Video caching settings screen (Video Caching plan — PLAN.md Part A + Session-2 addendum).
 *
 * Reached from SettingsScreen → "Video caching" (Player section).
 *
 * Sections:
 * 1. **General** — master toggle (default ON) + storage-limit slider (100 MB..2 GB).
 * 2. **Storage** — usage summary (used / limit · episode count) + "Clear cache".
 * 3. **Cached episodes** — the reactive list of cached entries: anime, episode,
 *    server·quality, the cached point (contiguous prefix % or segment count), and
 *    the size on disk. **Tapping a row plays that episode directly** — same
 *    server/quality/resolution (guaranteed by the cache identity), resuming from
 *    watch progress. Per-entry delete remains available.
 *
 * @param onBack Pops this screen.
 * @param onPlayEntry Launches playback for a cached entry (wired in MainActivity to
 *   a WatchKey built from the entry — resume position comes from watch progress).
 */
@Composable
fun VideoCachingScreen(
    onBack: () -> Unit,
    onPlayEntry: (PlaybackCacheStore.Entry) -> Unit,
    viewModel: VideoCachingViewModel = koinViewModel(),
) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val maxCacheMb by viewModel.maxCacheMb.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val totalBytes by viewModel.totalBytes.collectAsStateWithLifecycle()

    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0
    var showClearDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Video caching",
                collapsed = collapsed,
                actions = { BackAction(onBack) },
            )

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 110.dp),
                ) {
                    // ── General: toggle + storage limit ──
                    item {
                        SettingsGroupCard(label = "General") {
                            SettingRow(
                                title = "Enable video caching",
                                description = "Cache streamed video locally — replays of the same episode, server and resolution start instantly without network",
                                showDivider = false,
                                trailing = {
                                    Switch(checked = enabled, onCheckedChange = viewModel::setEnabled)
                                },
                            )
                        }
                    }
                    item {
                        // Continuous slider + snap-on-change: the 100..2048 MB range
                        // doesn't divide into even 100-MB steps (2048 = 2 GB), so we
                        // snap to 100-MB multiples and treat the top 50 MB as "max".
                        SliderRow(
                            label = "Storage limit",
                            value = maxCacheMb.toFloat(),
                            range = PlaybackCachePreferences.MIN_MB.toFloat()..PlaybackCachePreferences.MAX_MB.toFloat(),
                            steps = 0,
                            valueText = formatBytes(maxCacheMb.toLong() * 1024L * 1024L),
                            onChange = { v ->
                                val snapped = if (v >= PlaybackCachePreferences.MAX_MB - 50f) {
                                    PlaybackCachePreferences.MAX_MB
                                } else {
                                    ((v.toInt() / 100) * 100).coerceIn(
                                        PlaybackCachePreferences.MIN_MB,
                                        PlaybackCachePreferences.MAX_MB,
                                    )
                                }
                                viewModel.setMaxCacheMb(snapped)
                            },
                        )
                    }

                    // ── Storage usage ──
                    item {
                        SettingsGroupCard(label = "Storage") {
                            SettingRow(
                                title = "Used",
                                description = "${formatBytes(totalBytes)} of ${formatBytes(maxCacheMb.toLong() * 1024L * 1024L)} · ${entries.size} episode${if (entries.size == 1) "" else "s"} cached",
                                showDivider = entries.isNotEmpty(),
                            )
                            if (entries.isNotEmpty()) {
                                SettingRow(
                                    title = "Clear cache",
                                    description = "Remove all cached episodes",
                                    showDivider = false,
                                    trailing = {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteSweep,
                                            contentDescription = "Clear cache",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.clickable { showClearDialog = true },
                                        )
                                    },
                                )
                            }
                        }
                    }

                    // ── Cached episodes list ──
                    if (entries.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                                EmptyState(
                                    title = if (enabled) "No cached episodes yet" else "Caching is off",
                                    description = if (enabled) {
                                        "Episodes you stream appear here as they cache"
                                    } else {
                                        "Turn on video caching to cache streamed episodes"
                                    },
                                    icon = Icons.Filled.VideoLibrary,
                                )
                            }
                        }
                    } else {
                        item {
                            Text(
                                text = "Cached episodes — tap to play",
                                fontFamily = RobotoFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
                            )
                        }
                        items(entries, key = { it.cacheKey }) { entry ->
                            CachedEpisodeRow(
                                entry = entry,
                                onPlay = { onPlayEntry(entry) },
                                onDelete = { viewModel.removeEntry(entry.cacheKey) },
                            )
                        }
                    }
                }

                ScrollBlurOverlay(
                    scrollOffset = {
                        if (lazyListState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                        else lazyListState.firstVisibleItemScrollOffset.toFloat()
                    },
                    backgroundColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear cache?") },
            text = {
                Text("Removes all ${entries.size} cached episode${if (entries.size == 1) "" else "s"} (${formatBytes(totalBytes)}). This can't be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    showClearDialog = false
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            },
        )
    }
}

// ── Rows ──

@Composable
private fun CachedEpisodeRow(
    entry: PlaybackCacheStore.Entry,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onPlay),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Play affordance — the whole row is clickable; the icon reinforces it.
            Icon(
                imageVector = Icons.Filled.PlayCircleOutline,
                contentDescription = "Play cached episode",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(32.dp)
                    .padding(end = 4.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.animeTitle.ifBlank { "Unknown anime" },
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "EP ${formatEpisodeNumber(entry.episodeNumber)}" +
                        (if (entry.episodeTitle.isNotBlank()) " · ${entry.episodeTitle}" else ""),
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = buildCachedPointText(entry),
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = listOfNotNull(
                        entry.serverKey.take(40).ifBlank { null },
                        formatBytes(entry.cachedBytes),
                        if (entry.complete) "fully cached" else null,
                    ).joinToString(" · "),
                    fontFamily = RobotoFamily,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete cached episode",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * "From which point the episode was cached": HLS entries show the segment count;
 * progressive entries show the contiguous cached prefix + percent of total
 * (+ extra fragmented segments when the cache has holes).
 */
private fun buildCachedPointText(entry: PlaybackCacheStore.Entry): String {
    if (entry.isHls) {
        return if (entry.complete) {
            "Cached: full episode (${entry.segmentTotal} segments)"
        } else {
            "Cached: ${entry.segmentsCached}/${entry.segmentTotal} segments"
        }
    }
    val prefixEnd = CacheRanges.contiguousPrefixEnd(entry.cachedRanges)
    val total = entry.contentLength
    return when {
        entry.complete && total != null ->
            "Cached: full episode (${formatBytes(total)})"
        total != null && total > 0 -> {
            val pct = (prefixEnd * 100 / total).coerceIn(0, 100)
            val extraSegments = entry.cachedRanges.size - 1
            buildString {
                append("Cached: start → ${formatBytes(prefixEnd)} · $pct% of ${formatBytes(total)}")
                if (extraSegments > 0) append(" · +$extraSegments segment${if (extraSegments == 1) "" else "s"}")
            }
        }
        else -> "Cached: ${formatBytes(entry.cachedBytes)} (unknown total)"
    }
}

// ── Helpers (private copies — SliderRow is private in DownloadSettingsScreen;
// formatBytes has ~10 private copies in the codebase already) ──

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueText: String,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                valueText,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
}

private fun formatEpisodeNumber(number: Double): String =
    if (number == number.toLong().toDouble()) {
        number.toLong().toString()
    } else {
        number.toString()
    }
