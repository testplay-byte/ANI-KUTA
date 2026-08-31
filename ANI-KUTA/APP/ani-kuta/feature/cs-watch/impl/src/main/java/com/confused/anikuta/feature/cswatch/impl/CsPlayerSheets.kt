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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.csplayer.CsTextTrack
import com.confused.anikuta.core.csplayer.CsVideoLink
import com.confused.anikuta.core.csplayer.CsVideoTrack
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.EpisodeListPreferences
import com.confused.anikuta.core.preferences.PlayerPreferences
import com.confused.anikuta.feature.cswatch.api.CsSimpleEpisode
import com.confused.anikuta.feature.cswatch.api.CsSubDubSiblings
import org.koin.compose.koinInject

/**
 * The CS player bottom sheets (task 52 / Phase E; task 54 / round 14 —
 * aniyomi sheet-language parity; task 55 / round 15):
 *  - [CsLinksSheet] — "Qualities and Servers": the SHARED server/audio
 *    accordion from [CsSourceListUi] (same as the resolve sheet) with the
 *    current-link highlight + failed strikes + long-press copy, plus the
 *    formatting popup + RAW flat mode; the counts footer / still-resolving /
 *    subtitle-count hint lines are GONE (round-15 feedback — aniyomi
 *    QualitySheet parity: one hint line only);
 *  - [CsSubtitlesSheet] — "Subtitles": the aniyomi SubtitleTracksSheet design
 *    (Off-first TrackRows, check marks, the "Subtitle Settings" navigation
 *    row → [CsSubtitleSettingsSheet]) with language display names (round-15:
 *    rows must never show URLs) + the embedded-audio section;
 *  - [CsEpisodesSheet] — "Episodes" in the same sheet language with the
 *    current-episode highlight + the sub/dub display modes (Task 55);
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CsLinksSheet(
    links: List<CsVideoLink>,
    currentLinkUrl: String?,
    failedLinkUrls: Set<String>,
    videoTracks: List<CsVideoTrack>,
    selectedTrackLabel: String?,
    onLinkSelect: (CsVideoLink) -> Unit,
    onTrackSelect: (CsVideoTrack?) -> Unit,
    onCopyUrl: (String) -> Unit,
    onDismiss: () -> Unit,
    playerPreferences: PlayerPreferences = koinInject(),
) {
    // Task 55: the formatting toggle (shared pref; read at open).
    var formatted by remember { mutableStateOf(playerPreferences.resolveSheetFormatted) }

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
            // ── Header: "Qualities and Servers" (tappable → formatting popup) + close ──
            // Task 55: the "via/still-resolving/subtitle-count" hint line and
            // the hidden/DRM/failed footer are GONE (round-15 device feedback:
            // parity with the aniyomi QualitySheet — ONE hint line only).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CsFormattingHeader(
                    title = "Qualities and Servers",
                    formatted = formatted,
                    onToggleFormatting = {
                        formatted = it
                        playerPreferences.resolveSheetFormatted = it
                    },
                    modifier = Modifier.weight(1f),
                )
                CsSheetCloseButton(onDismiss)
            }
            Text(
                text = "Tap a server to expand, then pick a quality.",
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
            } else if (formatted) {
                // The SAME server/audio accordion as the resolve sheet (one
                // visual language across the whole flow) + current/failed.
                val servers = remember(links) { groupServers(links) }
                CsServerAccordion(
                    servers = servers,
                    preferredServer = null,
                    currentLinkUrl = currentLinkUrl,
                    failedLinkUrls = failedLinkUrls,
                    onPickVideo = onLinkSelect,
                    onCopyUrl = onCopyUrl,
                )
            } else {
                // Task 55: RAW mode — flat unformatted rows.
                CsRawLinkList(
                    links = links,
                    onPickVideo = onLinkSelect,
                    currentLinkUrl = currentLinkUrl,
                    failedLinkUrls = failedLinkUrls,
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
    /** Task 55: opens the subtitle STYLE settings sheet (aniyomi parity). */
    onOpenSettings: () -> Unit = {},
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

            // ── "Subtitle Settings" navigation row (the aniyomi
            //    SubtitleTracksSheet pattern — Task 55) ──
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clickable { onOpenSettings() },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(32.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        Text(
                            text = "Subtitle Settings",
                            fontFamily = RobotoFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

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
                    // Task 55: friendlier label + language display names (the
                    // v0.4.2 round saw URLs here — never again).
                    item(key = "sec-pending") {
                        CsSheetSectionLabel("From provider (needs reload)")
                    }
                    items(pendingSubs, key = { "p-${it.id}" }) { sub ->
                        CsTrackRow(
                            label = "${sub.displayName}  ↻",
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
    episodeListPreferences: EpisodeListPreferences = koinInject(),
) {
    // Task 55: the sub/dub display modes (same pref as the details page + the
    // watch page — one setting, every CS episode list).
    //  - SEPARATE: Sub | Dub chips at the top switch between the two lists
    //    (only when both flavors exist);
    //  - COMBINED: sibling rows merge into one (tag stripped).
    val subDubMode by remember { mutableStateOf(episodeListPreferences.subDubMode.get()) }
    val subDubEpisodes = remember(episodes, subDubMode) {
        if (subDubMode == "COMBINED") CsSubDubSiblings.mergeSiblings(episodes) else episodes
    }
    val showSwitcher = subDubMode != "COMBINED" && CsSubDubSiblings.hasBothFlavors(subDubEpisodes)
    var selectedFlavor by rememberSaveable { mutableStateOf("SUB") }
    val rows = if (showSwitcher) {
        subDubEpisodes.filter { CsSubDubSiblings.tagOf(it.name) == selectedFlavor }
    } else subDubEpisodes

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
                        text = "${rows.size}",
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

            // ── Task 55: Sub/Dub switcher (SEPARATE mode, both flavors) ──
            if (showSwitcher) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                ) {
                    listOf("SUB" to "Sub", "DUB" to "Dub").forEach { (value, label) ->
                        val isSelected = selectedFlavor == value
                        Surface(
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(50),
                            border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.clickable { selectedFlavor = value },
                        ) {
                            Text(
                                text = label,
                                fontFamily = RobotoFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(rows, key = { "${it.episodeNumber}-${it.data}" }) { episode ->
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
