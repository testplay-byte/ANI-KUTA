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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.csplayer.CsAudioTag
import com.confused.anikuta.core.csplayer.CsLinkType
import com.confused.anikuta.core.csplayer.CsVideoLink
import com.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * Task 55 (round 15) — the SHARED source-list UI for the CS module.
 *
 * One definition of the aniyomi ResolverSheet's source list (Server →
 * AudioVersion → Quality accordion + the RAW flat list + the formatting
 * popup) consumed by BOTH sheets:
 *  - [CsResolveSheet] (the details-page entry — no selection states),
 *  - [CsLinksSheet] (the in-player "Qualities and Servers" — current-link
 *    highlight, failed strikes, long-press copy).
 *
 * Everything here is `internal` to :feature:cs-watch:impl — the aniyomi stack
 * keeps its own copies (the replication rule, doc 05 §1).
 */

// ════════════════════════════════════════════════════════════════════════════
//  Grouping (pure — unit-tested)
// ════════════════════════════════════════════════════════════════════════════

/**
 * Groups the flat link list into the aniyomi 3-tier hierarchy:
 * server (link.name) → audio version (link.audioLabel) → links
 * (quality-desc). Chip labels disambiguate duplicate quality labels within a
 * version with the stream type (HLS/DASH).
 */
internal fun groupServers(links: List<CsVideoLink>): List<CsServerGroup> =
    links
        .groupBy { it.name }
        .map { (name, serverLinks) ->
            val versions = serverLinks
                .groupBy { it.audioLabel }
                .entries
                .sortedWith(
                    // Real flavors first (SUB before DUB — the aniyomi order);
                    // "Default" always last.
                    compareBy({ entry -> entry.key == CsAudioTag.DEFAULT }, { entry -> entry.key == "DUB" }),
                )
                .map { (label, versionLinks) ->
                    val sorted = versionLinks.sortedByDescending { it.quality }
                    val labels = sorted.map { it.qualityLabel }
                    CsAudioGroup(
                        label = label,
                        links = sorted,
                        disambiguateType = labels.groupingBy { it }.eachCount().any { it.value > 1 },
                    )
                }
            CsServerGroup(name = name, audioVersions = versions)
        }
        .sortedBy { it.name }

/** One accordion server: the label + its audio versions (each w/ links). */
internal data class CsServerGroup(
    val name: String,
    val audioVersions: List<CsAudioGroup>,
)

/** One audio version within a server (SUB/DUB/… or "Default"). */
internal data class CsAudioGroup(
    val label: String,
    val links: List<CsVideoLink>,
    /** True when two links of this version share a quality label — chips then
     *  carry the type badge (HLS/DASH) so rows stay distinguishable. */
    val disambiguateType: Boolean,
)

// ════════════════════════════════════════════════════════════════════════════
//  The formatting popup (header anchor)
// ════════════════════════════════════════════════════════════════════════════

/**
 * A tappable sheet-title row. Tapping opens a SMALL DropdownMenu ABOVE the
 * anchor (user spec: "a small menu which will show above it", not a bottom
 * sheet) with the source-formatting toggle.
 * ON  (default) = the aniyomi server/audio accordion.
 * OFF = the raw flat list (unformatted labels, tap = play directly).
 *
 * The aniyomi sheets implement the SAME interaction with their own local
 * copies (the two stacks share only the preference, not code).
 */
@Composable
internal fun CsFormattingHeader(
    title: String,
    formatted: Boolean,
    onToggleFormatting: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Text(
            text = title,
            fontFamily = RobotoFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { menuOpen = true },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            offset = DpOffset(0.dp, (-72).dp),
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Formatted sources",
                        fontFamily = RobotoFamily,
                        fontWeight = FontWeight.Bold,
                    )
                },
                leadingIcon = {
                    if (formatted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "On",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                onClick = {
                    menuOpen = false
                    onToggleFormatting(!formatted)
                },
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  The accordion (server cards, one open at a time)
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CsServerAccordion(
    servers: List<CsServerGroup>,
    onPickVideo: (CsVideoLink) -> Unit,
    /** The server to open first (remembered/preferred); null = first server. */
    preferredServer: String? = null,
    /** The currently-playing link's URL (in-player sheet: highlight + open). */
    currentLinkUrl: String? = null,
    /** URLs that errored in the engine (in-player sheet: strike-through). */
    failedLinkUrls: Set<String> = emptySet(),
    onCopyUrl: ((String) -> Unit)? = null,
) {
    // Track which server is expanded (only one at a time). null = all collapsed.
    // The CURRENT link's server (in-player) or the remembered server (entry)
    // opens FIRST.
    var expandedServer by remember(servers, preferredServer, currentLinkUrl) {
        mutableStateOf(
            servers.firstOrNull { s -> s.audioVersions.any { v -> v.links.any { it.url == currentLinkUrl } } }?.name
                ?: servers.firstOrNull { it.name == preferredServer }?.name
                ?: servers.firstOrNull()?.name,
        )
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(servers, key = { it.name }) { server ->
            val isExpanded = expandedServer == server.name
            CsServerCard(
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
private fun CsServerCard(
    server: CsServerGroup,
    isExpanded: Boolean,
    currentLinkUrl: String?,
    failedLinkUrls: Set<String>,
    onToggle: () -> Unit,
    onPickVideo: (CsVideoLink) -> Unit,
    onCopyUrl: ((String) -> Unit)?,
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
            // ── Header row: server name (left) + audio chips + chevron ──
            // (the aniyomi ServerCard: audio chips show just the label,
            //  "Default" is skipped, reversed so SUB sits rightmost.)
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
                    val audioChips = server.audioVersions.filter { it.label != CsAudioTag.DEFAULT }
                    audioChips.reversed().forEach { version ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = version.label,
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

            // ── Expanded content: per-audio-version quality chips ──
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    server.audioVersions.forEach { version ->
                        // Audio version label (e.g. "SUB", "DUB") — only when
                        // the server actually has multiple versions (aniyomi).
                        if (server.audioVersions.size > 1) {
                            Text(
                                text = version.label,
                                fontFamily = RobotoFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            version.links.forEach { link ->
                                val label = if (version.disambiguateType) {
                                    "${link.qualityLabel} · ${link.type.badgeLabel()}"
                                } else {
                                    link.qualityLabel
                                }
                                val isCurrent = link.url == currentLinkUrl
                                val isFailed = link.url in failedLinkUrls
                                CsQualityChip(
                                    quality = label,
                                    isSelected = isCurrent,
                                    isFailed = isFailed,
                                    onClick = { onPickVideo(link) },
                                    onLongClick = onCopyUrl?.let { cb -> { cb(link.url) } },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The aniyomi ResolverSheet's chip: PlayArrow + quality, primaryContainer. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CsQualityChip(
    quality: String,
    onClick: () -> Unit,
    isSelected: Boolean = false,
    isFailed: Boolean = false,
    onLongClick: (() -> Unit)? = null,
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

// ════════════════════════════════════════════════════════════════════════════
//  The RAW flat list (formatting OFF)
// ════════════════════════════════════════════════════════════════════════════

/**
 * The unformatted view: one row per stream — the raw label the resolver
 * produced ("Mirror 1080p") plus the audio tag when one exists. TrackRow
 * styling; tap = pick directly. Current/failed states ride the row when the
 * in-player sheet provides them.
 */
@Composable
internal fun CsRawLinkList(
    links: List<CsVideoLink>,
    onPickVideo: (CsVideoLink) -> Unit,
    currentLinkUrl: String? = null,
    failedLinkUrls: Set<String> = emptySet(),
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(links, key = { it.url }) { link ->
            val label = link.displayLabel +
                (link.audioLabel.takeIf { it != CsAudioTag.DEFAULT }?.let { " · $it" } ?: "")
            val isCurrent = link.url == currentLinkUrl
            val isFailed = link.url in failedLinkUrls
            Surface(
                color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(10.dp),
                border = if (isCurrent) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier.fillMaxWidth().clickable(enabled = !isFailed) { onPickVideo(link) },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (isCurrent) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isFailed) 0.4f else 1f),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isFailed) "$label (failed)" else label,
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = if (isFailed) 0.4f else 1f),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isCurrent) {
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
    }
}

private fun CsLinkType.badgeLabel(): String = when (this) {
    CsLinkType.VIDEO -> "VIDEO"
    CsLinkType.M3U8 -> "HLS"
    CsLinkType.DASH -> "DASH"
}
