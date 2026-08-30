package com.confused.anikuta.feature.cswatch.impl

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.csplayer.CsLinkType
import com.confused.anikuta.core.csplayer.CsTextTrack
import com.confused.anikuta.core.csplayer.CsVideoLink
import com.confused.anikuta.core.csplayer.CsVideoTrack
import com.confused.anikuta.feature.cswatch.api.CsSimpleEpisode

/**
 * The CS player bottom sheets (task 52 / Phase E):
 *  - [CsLinksSheet] — streams list (name + quality + type badge, live-updating,
 *    failed markers, hidden-count footer, long-press copy URL) + per-stream
 *    quality rows (HLS/DASH variants via the engine's track APIs);
 *  - [CsSubtitlesSheet] — Off + sidecar + embedded text tracks;
 *  - [CsEpisodesSheet] — the episode list with current highlight.
 *
 * UX mirrors the aniyomi watch page's sheets + upstream's sources dialog
 * ("$name ${Qualities.getStringByInt(quality)}" rows, hidden-links footer).
 */

private val TYPE_BADGES = mapOf(
    CsLinkType.VIDEO to "VIDEO",
    CsLinkType.M3U8 to "HLS",
    CsLinkType.DASH to "DASH",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CsLinksSheet(
    links: List<CsVideoLink>,
    currentLinkUrl: String?,
    failedLinkUrls: Set<String>,
    hiddenTorrentCount: Int,
    unsupportedDrmCount: Int,
    resolveCompleted: Boolean,
    videoTracks: List<CsVideoTrack>,
    selectedTrackLabel: String?,
    onLinkSelect: (CsVideoLink) -> Unit,
    onTrackSelect: (CsVideoTrack?) -> Unit,
    onCopyUrl: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(bottom = 20.dp)) {
            SheetHeader(
                title = "Streams",
                subtitle = buildString {
                    append("${links.size} available")
                    if (!resolveCompleted) append(" · still resolving…")
                },
            )
            // Capped scrollable list (long lists get max-height + scroll).
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
            ) {
                items(links, key = { it.url }) { link ->
                    LinkRow(
                        link = link,
                        isCurrent = link.url == currentLinkUrl,
                        isFailed = link.url in failedLinkUrls,
                        onClick = { onLinkSelect(link) },
                        onLongClick = { onCopyUrl(link.url) },
                    )
                }
            }
            if (hiddenTorrentCount > 0 || unsupportedDrmCount > 0) {
                val hidden = buildList {
                    if (hiddenTorrentCount > 0) add("$hiddenTorrentCount torrent link(s) hidden")
                    if (unsupportedDrmCount > 0) add("$unsupportedDrmCount DRM link(s) unsupported")
                }.joinToString(" · ")
                Text(
                    hidden,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            if (videoTracks.size > 1) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                SheetHeader(title = "Quality for this stream", subtitle = "HLS/DASH variants")
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                ) {
                    item(key = "auto") {
                        QualityRow(
                            label = "Auto",
                            selected = selectedTrackLabel == null,
                            onClick = { onTrackSelect(null) },
                        )
                    }
                    items(videoTracks, key = { "${it.groupIndex}-${it.trackIndex}" }) { track ->
                        QualityRow(
                            label = track.label,
                            selected = track.label == selectedTrackLabel,
                            onClick = { onTrackSelect(track) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LinkRow(
    link: CsVideoLink,
    isCurrent: Boolean,
    isFailed: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                link.displayLabel,
                color = when {
                    isFailed -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    isCurrent -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isFailed) {
                Text(
                    "failed — skipped",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                )
            }
        }
        Text(
            TYPE_BADGES[link.type] ?: link.type.name,
            color = if (isCurrent) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(start = 10.dp)
                .background(
                    (if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                    RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun QualityRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        fontSize = 15.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CsSubtitlesSheet(
    tracks: List<CsTextTrack>,
    selectedTrackId: String?,
    onSelect: (CsTextTrack?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(bottom = 20.dp)) {
            SheetHeader(title = "Subtitles", subtitle = "${tracks.size} track(s)")
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
            ) {
                item(key = "off") {
                    SubtitleRow(
                        label = "Off",
                        selected = selectedTrackId == null,
                        onClick = { onSelect(null) },
                    )
                }
                val sidecar = tracks.filter { !it.embedded }
                val embedded = tracks.filter { it.embedded }
                if (sidecar.isNotEmpty()) {
                    item(key = "sec-sidecar") { SheetSectionLabel("From provider") }
                    items(sidecar, key = { "s-${it.groupIndex}-${it.trackIndex}" }) { track ->
                        SubtitleRow(
                            label = track.name,
                            selected = track.id == selectedTrackId,
                            onClick = { onSelect(track) },
                        )
                    }
                }
                if (embedded.isNotEmpty()) {
                    item(key = "sec-embedded") { SheetSectionLabel("Embedded in video") }
                    items(embedded, key = { "e-${it.groupIndex}-${it.trackIndex}" }) { track ->
                        SubtitleRow(
                            label = track.name,
                            selected = track.id == selectedTrackId,
                            onClick = { onSelect(track) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        fontSize = 15.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CsEpisodesSheet(
    episodes: List<CsSimpleEpisode>,
    currentData: String,
    onSelect: (CsSimpleEpisode) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(bottom = 20.dp)) {
            SheetHeader(title = "Episodes", subtitle = "${episodes.size} total")
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
            ) {
                items(episodes, key = { "${it.episodeNumber}-${it.data}" }) { episode ->
                    val isCurrent = episode.data == currentData
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(episode) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        Text(
                            if (episode.episodeNumber % 1f == 0f) "${episode.episodeNumber.toInt()}"
                            else "${episode.episodeNumber}",
                            color = if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                        Text(
                            episode.name.ifBlank { "Episode ${episode.episodeNumber.toInt()}" },
                            color = if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(
            title,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            subtitle,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun SheetSectionLabel(label: String) {
    Text(
        label,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 2.dp),
    )
}
