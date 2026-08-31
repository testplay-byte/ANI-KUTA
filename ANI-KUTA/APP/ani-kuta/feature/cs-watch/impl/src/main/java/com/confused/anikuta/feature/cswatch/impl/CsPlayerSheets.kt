package com.confused.anikuta.feature.cswatch.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.csplayer.CsLinkType
import com.confused.anikuta.core.csplayer.CsTextTrack
import com.confused.anikuta.core.csplayer.CsVideoLink
import com.confused.anikuta.core.csplayer.CsVideoTrack
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.feature.cswatch.api.CsSimpleEpisode

/**
 * The CS player bottom sheets (task 52 / Phase E; task 54 / round 14 —
 * aniyomi sheet-language parity):
 *  - [CsLinksSheet] — "Qualities and Servers": the SAME server accordion +
 *    quality-chip design as the aniyomi QualitySheet / the resolve sheet
 *    (one-open-at-a-time, selected chip highlighted, failed chips struck),
 *    plus the CS-specific variant tracks (HLS/DASH) section + hidden-count
 *    footer + long-press copy URL;
 *  - [CsSubtitlesSheet] — "Subtitles": the aniyomi SubtitleTracksSheet design
 *    (Off-first TrackRows, check marks, sections for provider sidecars /
 *    embedded tracks / needs-reload tracks) + the embedded-audio section;
 *  - [CsEpisodesSheet] — "Episodes" in the same sheet language with the
 *    current-episode highlight;
 *  - [CsSpeedSheet] — the aniyomi SpeedSheet design (presets + slider).
 */

// ════════════════════════════════════════════════════════════════════════════
//  Shared sheet scaffolding (the aniyomi sheets' chrome, replicated)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun csSheetMaxHeight() = LocalConfiguration.current.screenHeightDp.dp * 0.70f

@Composable
private fun CsSheetCloseButton(onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = CircleShape,
        modifier = Modifier
            .size(32.dp)
            .clickable(onClick = onDismiss),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** The aniyomi TrackRow: 10dp rounded card, selected = primary tint + border + check. */
@Composable
private fun CsTrackRow(
    label: String,
    isSelected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(10.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        else if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun CsSheetSectionLabel(label: String) {
    Text(
        text = label,
        fontFamily = RobotoFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp),
    )
}

// ════════════════════════════════════════════════════════════════════════════
//  Links sheet — "Qualities and Servers" (QualitySheet parity)
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun CsLinksSheet(
    links: List<CsVideoLink>,
    currentLinkUrl: String?,
    failedLinkUrls: Set<String>,
    failureReasons: Map<String, String>,
    hiddenTorrentCount: Int,
    unsupportedDrmCount: Int,
    resolveCompleted: Boolean,
    subtitleCount: Int,
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
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = csSheetMaxHeight())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            // ── Header: "Qualities and Servers" + close ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Qualities and Servers",
                    fontFamily = RobotoFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                CsSheetCloseButton(onDismiss)
            }
            // Subtitle line (QualitySheet parity + CS live state).
            Text(
                text = buildString {
                    append("Tap a server to expand, then pick a quality.")
                    if (!resolveCompleted) append(" Still resolving…")
                    if (subtitleCount > 0) append(" $subtitleCount subtitle track(s).")
                },
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            if (links.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No video sources available",
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            } else {
                // Group the flat link list into servers (same grouping as the
                // resolve sheet — one visual language across the whole flow).
                val servers = remember(links) { groupSheetServers(links) }
                CsSheetServerAccordion(
                    servers = servers,
                    currentLinkUrl = currentLinkUrl,
                    failedLinkUrls = failedLinkUrls,
                    onPickVideo = { video ->
                        onLinkSelect(video)
                    },
                    onCopyUrl = onCopyUrl,
                )
            }

            // Hidden/failed footer (the CS honest-counts line).
            val footerText = buildString {
                if (hiddenTorrentCount > 0) append(" · $hiddenTorrentCount torrent link(s) hidden")
                if (unsupportedDrmCount > 0) append(" · $unsupportedDrmCount DRM link(s) unsupported")
                val failedReasons = failureReasons.values.distinct()
                if (failedReasons.isNotEmpty()) {
                    append(" · failed: ")
                    append(failedReasons.joinToString(", "))
                }
            }
            if (footerText.isNotBlank()) {
                Text(
                    text = footerText.removePrefix(" · "),
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }

            // ── Variant tracks (HLS/DASH ABR) — the "Quality for this stream" section ──
            if (videoTracks.size > 1) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Quality for this stream",
                    fontFamily = RobotoFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Text(
                    text = "HLS/DASH variants",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item(key = "auto") {
                        CsTrackRow(
                            label = "Auto",
                            isSelected = selectedTrackLabel == null,
                            onClick = { onTrackSelect(null) },
                        )
                    }
                    items(videoTracks, key = { "${it.groupIndex}-${it.trackIndex}" }) { track ->
                        CsTrackRow(
                            label = track.label,
                            isSelected = track.label == selectedTrackLabel,
                            onClick = { onTrackSelect(track) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Server grouping for the sheets (identical semantics to the resolve sheet's). */
private fun groupSheetServers(links: List<CsVideoLink>): List<CsSheetServer> =
    links
        .groupBy { it.name }
        .map { (name, groupLinks) ->
            val sorted = groupLinks.sortedByDescending { it.quality }
            val labels = sorted.map { it.qualityLabel }
            CsSheetServer(
                name = name,
                links = sorted,
                disambiguateType = labels.groupingBy { it }.eachCount().any { it.value > 1 },
            )
        }

private data class CsSheetServer(
    val name: String,
    val links: List<CsVideoLink>,
    val disambiguateType: Boolean,
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun CsSheetServerAccordion(
    servers: List<CsSheetServer>,
    currentLinkUrl: String?,
    failedLinkUrls: Set<String>,
    onPickVideo: (CsVideoLink) -> Unit,
    onCopyUrl: (String) -> Unit,
) {
    // One open at a time; the CURRENT link's server (or the first) starts open.
    var expandedServer by remember(servers, currentLinkUrl) {
        mutableStateOf(
            servers.firstOrNull { s -> s.links.any { it.url == currentLinkUrl } }?.name
                ?: servers.firstOrNull()?.name,
        )
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(servers, key = { it.name }) { server ->
            val isExpanded = expandedServer == server.name
            CsSheetServerCard(
                server = server,
                isExpanded = isExpanded,
                currentLinkUrl = currentLinkUrl,
                failedLinkUrls = failedLinkUrls,
                onToggle = {
                    expandedServer = if (isExpanded) null else server.name
                },
                onPickVideo = onPickVideo,
                onCopyUrl = onCopyUrl,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun CsSheetServerCard(
    server: CsSheetServer,
    isExpanded: Boolean,
    currentLinkUrl: String?,
    failedLinkUrls: Set<String>,
    onToggle: () -> Unit,
    onPickVideo: (CsVideoLink) -> Unit,
    onCopyUrl: (String) -> Unit,
) {
    Surface(
        color = if (isExpanded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            // ── Header row: server name + count chip + chevron ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = server.name,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (server.links.size > 1) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = "${server.links.size}",
                                fontFamily = RobotoFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // ── Expanded content: quality chips (selected = playing, failed = struck) ──
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    server.links.forEach { link ->
                        val label = if (server.disambiguateType) {
                            "${link.qualityLabel} · ${link.type.sheetBadge()}"
                        } else {
                            link.qualityLabel
                        }
                        val isCurrent = link.url == currentLinkUrl
                        val isFailed = link.url in failedLinkUrls
                        CsSheetQualityChip(
                            quality = label,
                            isSelected = isCurrent,
                            isFailed = isFailed,
                            onClick = { onPickVideo(link) },
                            onLongClick = { onCopyUrl(link.url) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CsSheetQualityChip(
    quality: String,
    isSelected: Boolean,
    isFailed: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        color = when {
            isSelected -> MaterialTheme.colorScheme.primary
            isFailed -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        shape = RoundedCornerShape(8.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
            enabled = !isFailed,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                       else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = if (isFailed) 0.4f else 1f),
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (isFailed) "$quality (failed)" else quality,
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                       else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = if (isFailed) 0.4f else 1f),
            )
        }
    }
}

private fun CsLinkType.sheetBadge(): String = when (this) {
    CsLinkType.VIDEO -> "VIDEO"
    CsLinkType.M3U8 -> "HLS"
    CsLinkType.DASH -> "DASH"
}

// ════════════════════════════════════════════════════════════════════════════
//  Subtitles sheet — the aniyomi SubtitleTracksSheet design
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CsSubtitlesSheet(
    tracks: List<CsTextTrack>,
    selectedTrackId: String?,
    onSelect: (CsTextTrack?) -> Unit,
    /** Sidecar subs NOT yet attached to the player (arrived after playback started) —
     *  selecting one reloads the stream so they attach (upstream REQUIRES_RELOAD). */
    pendingSubs: List<com.confused.anikuta.core.csplayer.CsSubtitle> = emptyList(),
    onPendingSubSelect: (com.confused.anikuta.core.csplayer.CsSubtitle) -> Unit = {},
    /** Embedded audio tracks (Task 53 / RC-7) — sectioned in when there is a real choice. */
    audioTracks: List<com.confused.anikuta.core.csplayer.CsAudioTrackInfo> = emptyList(),
    selectedAudioId: String? = null,
    onAudioSelect: (com.confused.anikuta.core.csplayer.CsAudioTrackInfo?) -> Unit = {},
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = csSheetMaxHeight())
                .padding(top = 20.dp)
                .navigationBarsPadding(),
        ) {
            // ── Header: "Subtitles" (or "Audio & Subtitles") + close ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val showAudio = audioTracks.size > 1
                Text(
                    text = if (showAudio) "Audio & Subtitles" else "Subtitles",
                    fontFamily = RobotoFamily,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                CsSheetCloseButton(onDismiss)
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // ── Audio section (embedded DASH multi-audio) ──
                if (audioTracks.size > 1) {
                    item(key = "sec-audio") { CsSheetSectionLabel("Audio language") }
                    item(key = "audio-auto") {
                        CsTrackRow(
                            label = "Auto",
                            isSelected = selectedAudioId == null,
                            onClick = { onAudioSelect(null) },
                        )
                    }
                    items(audioTracks, key = { "a-${it.groupIndex}-${it.trackIndex}" }) { audio ->
                        CsTrackRow(
                            label = audio.label,
                            isSelected = audio.id == selectedAudioId,
                            onClick = { onAudioSelect(audio) },
                        )
                    }
                    item(key = "audio-divider") {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    }
                }

                // ── "Off" — ALWAYS the first subtitle entry ──
                item(key = "off") {
                    CsTrackRow(
                        label = "Off",
                        isSelected = selectedTrackId == null,
                        onClick = {
                            onSelect(null)
                        },
                    )
                }

                // ── Subtitle sections ──
                val sidecar = tracks.filter { !it.embedded }
                val embedded = tracks.filter { it.embedded }
                if (sidecar.isNotEmpty()) {
                    item(key = "sec-sidecar") { CsSheetSectionLabel("From provider") }
                    items(sidecar, key = { "s-${it.groupIndex}-${it.trackIndex}" }) { track ->
                        CsTrackRow(
                            label = track.name,
                            isSelected = track.id == selectedTrackId,
                            onClick = { onSelect(track) },
                        )
                    }
                }
                if (embedded.isNotEmpty()) {
                    item(key = "sec-embedded") { CsSheetSectionLabel("Embedded in video") }
                    items(embedded, key = { "e-${it.groupIndex}-${it.trackIndex}" }) { track ->
                        CsTrackRow(
                            label = track.name,
                            isSelected = track.id == selectedTrackId,
                            onClick = { onSelect(track) },
                        )
                    }
                }
                if (pendingSubs.isNotEmpty()) {
                    item(key = "sec-pending") { CsSheetSectionLabel("Needs a quick reload to attach") }
                    items(pendingSubs, key = { "p-${it.id}" }) { sub ->
                        CsTrackRow(
                            label = "${sub.name}  ↻",
                            isSelected = false,
                            onClick = { onPendingSubSelect(sub) },
                        )
                    }
                }

                // Empty message (below "Off" — the aniyomi pattern).
                if (tracks.isEmpty() && pendingSubs.isEmpty()) {
                    item(key = "empty-msg") {
                        Text(
                            text = "No subtitles found in this stream.\nThe provider may not provide external subtitles.",
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Episodes sheet — the same sheet language, current-episode highlight
// ════════════════════════════════════════════════════════════════════════════

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
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = csSheetMaxHeight())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            // ── Header: "Episodes" + count + close ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Episodes",
                    fontFamily = RobotoFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        text = "${episodes.size}",
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                CsSheetCloseButton(onDismiss)
            }
            Text(
                text = "Tap an episode to switch",
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(episodes, key = { "${it.episodeNumber}-${it.data}" }) { episode ->
                    val isCurrent = episode.data == currentData
                    val epNumText = if (episode.episodeNumber % 1f == 0f) {
                        "${episode.episodeNumber.toInt()}"
                    } else {
                        "${episode.episodeNumber}"
                    }
                    CsTrackRow(
                        label = "EP $epNumText · " +
                            (episode.name.ifBlank { "Episode ${episode.episodeNumber.toInt()}" }),
                        isSelected = isCurrent,
                        onClick = { onSelect(episode) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Speed sheet — the aniyomi SpeedSheet design (presets + custom slider)
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CsSpeedSheet(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val presets = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    var sliderValue by remember { mutableFloatStateOf(currentSpeed) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
                .padding(top = 20.dp)
                .navigationBarsPadding(),
        ) {
            // ── Header: "Playback speed" + close ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Playback speed",
                    fontFamily = RobotoFamily,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                CsSheetCloseButton(onDismiss)
            }

            // Current speed readout.
            Text(
                text = String.format("%.2fx", sliderValue),
                fontFamily = RobotoFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 16.dp, start = 20.dp, bottom = 12.dp),
            )

            // Preset chips (scrollable row).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presets.forEach { preset ->
                    val isSelected = kotlin.math.abs(preset - currentSpeed) < 0.01f
                    Surface(
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier.clickable {
                            onSpeedSelected(preset)
                        },
                    ) {
                        Text(
                            text = "${preset}x",
                            fontFamily = RobotoFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                   else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            // Custom slider: 0.1x – 2.0x (ExoPlayer handles high speeds poorly
            // on some devices — cap at 2x, the preset range).
            Text(
                text = "Custom",
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, start = 20.dp, bottom = 4.dp),
            )
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = {
                    onSpeedSelected(sliderValue)
                },
                valueRange = 0.1f..2.0f,
                steps = 18,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}
