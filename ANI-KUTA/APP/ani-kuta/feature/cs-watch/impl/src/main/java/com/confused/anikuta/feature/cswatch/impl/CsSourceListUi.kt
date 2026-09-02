package com.confused.anikuta.feature.cswatch.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
 * Task 57 (round 17 — P5): [serverNameOf] also strips BRACKETED
 * audio/quality decorations glued to segments.
 *
 * Task 63 (round 23 — D): the Task-57 P4 debug affordances (per-row copy
 * icon, raw url/type line, the resolve-report header copy) are REMOVED with
 * the Developer-tools toolkit; the user-facing LONG-PRESS url copy and the
 * formatting popup stay.
 *
 * Everything here is `internal` to :feature:cs-watch:impl — the aniyomi stack
 * keeps its own copies (the replication rule, doc 05 §1).
 */

// ════════════════════════════════════════════════════════════════════════════
//  Grouping (pure — unit-tested)
// ════════════════════════════════════════════════════════════════════════════

/**
 * Groups the flat link list into the aniyomi 3-tier hierarchy:
 * server ([serverNameOf]) → audio version (link.audioLabel) → links.
 * Chip labels disambiguate duplicate quality labels within a version with the
 * stream type (HLS/DASH).
 *
 * Task 56 (round 16 — device feedback F2): the quality chips sort by RANK,
 * highest on the LEFT: real pixel heights descending (2160 → 144), then
 * Unknown(400), then Auto(0) at the far right ("any other options"). A raw
 * value sort would seat Unknown left of 144p — the rank pushes it past every
 * real height.
 */
internal fun groupServers(links: List<CsVideoLink>): List<CsServerGroup> =
    links
        .groupBy { serverNameOf(it.name) }
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
                    val sorted = versionLinks.sortedByDescending { qualityRank(it.quality) }
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

/**
 * The chip-order rank: real heights sort by their pixel value (descending in
 * [groupServers]); Unknown (400) ranks BELOW every real height; Auto (0)
 * ranks below Unknown — the user's "then any other options" tail.
 */
internal fun qualityRank(quality: Int): Int = when (quality) {
    0 -> -2 // Auto
    400 -> -1 // Unknown
    else -> quality
}

/**
 * The SERVER part of a link name — the aniyomi derivation: audio-version
 * tokens and quality tokens are stripped when they appear as separate
 * " - " segments ("HD-1 - Sub - 1080p" → "HD-1"; "Vidstream-2 - Dub - 720p"
 * → "Vidstream-2").
 *
 * Task 57 (P5 — smarter server/audio/resolution detection): a decoration
 * pass ALSO strips tokens GLUED to a segment — bracketed audio-version tags
 * ("[SUB]", "(Dub)", "[Multi Audio]", "(Softsub)") and bracketed quality
 * tokens ("[1080p]", "(4k)"), plus the bare quality words that trail them
 * (the audio chip and quality chip own that vocabulary):
 *   "Mirror [SUB] 1080p"    → "Mirror"
 *   "Streamtape (Dub) 720p" → "Streamtape"
 *   "Server [1080p]"        → "Server"
 * A name that is ONLY tokens ("HSUB - 360p") keeps its full original form
 * (blank guard); names without separators or brackets pass through unchanged
 * (no over-stripping of hyphenated server names like "HD-1" / "Vidstream-2").
 */
internal fun serverNameOf(name: String): String {
    val kept = name.split(SEPARATOR).map { seg ->
        // Decoration pass: strip glued brackets + bare quality words, then
        // re-join the surviving words with single spaces (collapses runs
        // of spaces the bracket removal leaves behind).
        seg.trim()
            .replace(BRACKETED_AUDIO_TAG, "")
            .replace(BRACKETED_QUALITY_TAG, "")
            .split(WHITESPACE)
            .filter { word -> !QUALITY_TOKEN.matches(word) }
            .joinToString(" ")
    }.filter { seg ->
        val s = seg.lowercase()
        s.isNotBlank() && s !in AUDIO_SEGMENT_WORDS && !QUALITY_TOKEN.matches(s)
    }
    return kept.joinToString(" - ").trim().ifBlank { name.trim() }
}

/** Segment separator: " - " with flexible spacing. */
private val SEPARATOR = Regex("\\s+-\\s+")

/** Task 57 (P5): a bracketed audio-version token glued to a segment —
 *  "[SUB]", "(Dub)", "[Dubbed]", "[Multi Audio]", "(Softsub)". */
private val BRACKETED_AUDIO_TAG =
    Regex("[\\[(]\\s*(?:sub(?:bed)?|dub(?:bed)?|hsub|hardsub|multi[ -]?audio|softsub)\\s*[\\])]", RegexOption.IGNORE_CASE)

/** Task 57 (P5): a bracketed quality token glued to a segment — "[1080p]", "(720p)", "[4k]". */
private val BRACKETED_QUALITY_TAG =
    Regex("[\\[(]\\s*(?:\\d{3,4}[pi]?|[48]k)\\s*[\\])]", RegexOption.IGNORE_CASE)

/** Task 57 (P5): the decoration pass's word splitter. */
private val WHITESPACE = Regex("\\s+")

/** Audio-version words (the [CsAudioTag.parse] vocabulary, whole-segment). */
private val AUDIO_SEGMENT_WORDS = setOf(
    "sub", "subbed", "dubbed", "dub", "hsub", "hardsub", "h-hardsub", "mix", "raw",
)

/** A standalone quality token: 240p–2160p/i, 4K, 8K. */
private val QUALITY_TOKEN = Regex("^[1-9]\\d{2,3}[pi]?$|^[48]k$")

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
//  Task 60 (round 20): the formatting menu ON the sheet's heading
// ════════════════════════════════════════════════════════════════════════════

/**
 * Task 60 (round 20 — the v0.4.7 device round): the formatting control lives
 * ON the sheet's HEADING now — the round-19 design was a standalone
 * "Formatted sources" pill above the title, and the user's spec superseded
 * it: "when I click on the episode heading at the top of the bottom-up menu
 * of the resolved video streams, it should open up a small menu with a
 * distinct border around it; in that I should be able to toggle it on and
 * off."
 *
 *  - the TITLE TEXT is the only touch target (intrinsic width, ellipsized —
 *    taps on the surrounding header area do nothing, per the earlier
 *    round-19 "only the episode number and text are clickable" spec);
 *  - tapping it pops a small menu floating ABOVE the heading (Task 62, round
 *    22 — the user's "it was supposed to show above it … outside the bottom
 *    menu"): a custom PopupPositionProvider sits the menu's BOTTOM edge a
 *    8dp gap ABOVE the heading's TOP edge, so it never covers the heading
 *    and floats fully OUTSIDE the sheet (over the scrim).
 *    A 14dp-cornered, flat surface with a DISTINCT 1dp outline border and ONE
 *    row — "Format sources" + a trailing Switch (exact label + guaranteed
 *    24dp gap between the label and the toggle, per the round-21 spec);
 *  - toggling (the row OR the switch) flips the formatted/raw view and the
 *    menu STAYS OPEN so the user can flip it back; outside-tap or back
 *    dismisses (PopupProperties(focusable = true)). The list behind re-renders
 *    live.
 *
 * The aniyomi sheets implement the SAME interaction with their own local
 * copies (the two stacks share the preference, not code — the replication
 * rule).
 */
@Composable
internal fun CsFormattingTitle(
    title: String,
    formatted: Boolean,
    onToggleFormatting: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // Task 62 (round 22 — the menu POSITION rework): the round-21
    // Popup(Alignment.BottomStart) sat the menu's BOTTOM edge on the
    // heading's BOTTOM edge — i.e. the menu covered the heading itself
    // (the "Episode N" text) instead of sitting above it. The device spec:
    // "it was supposed to show above it … and it was supposed to show
    // outside the bottom menu". A custom PopupPositionProvider now places
    // the menu's BOTTOM edge a 8dp gap ABOVE the heading's TOP edge — since
    // the heading is only ~16-30dp below the sheet's top edge, the menu
    // floats fully OUTSIDE the sheet, over the scrim (the ModalBottomSheet's
    // dialog window is full-screen MATCH_PARENT, and a Popup is a real
    // TYPE_APPLICATION_SUB_PANEL sub-window of it — it renders above the
    // sheet's content, clipped only to the screen).
    val menuGapPx = with(LocalDensity.current) { 8.dp.roundToPx() }
    // NOTE: PopupPositionProvider is a plain (non-fun) interface in Compose
    // UI 1.10 — a SAM-constructor lambda does NOT compile; an object
    // expression implements it.
    val aboveAnchorPositionProvider = remember(menuGapPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset = IntOffset(
                x = anchorBounds.left,
                y = anchorBounds.top - popupContentSize.height - menuGapPx,
            )
        }
    }
    Box(modifier = modifier) {
        // ONLY the title text opens the menu — the user's spec.
        Text(
            text = title,
            fontFamily = RobotoFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable { menuOpen = true },
        )
        // The small menu with the DISTINCT border (shape + flat surface +
        // 1dp outline via Modifier.border — version-proof on material3),
        // floating ABOVE the heading — fully outside the sheet.
        if (menuOpen) {
            Popup(
                popupPositionProvider = aboveAnchorPositionProvider,
                onDismissRequest = { menuOpen = false },
                properties = PopupProperties(focusable = true),
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .widthIn(min = 220.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(14.dp),
                        ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleFormatting(!formatted) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Format sources",
                            fontFamily = RobotoFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        // Round 21: a GUARANTEED 24dp gap between the label and
                        // the toggle (a weight-only spacer collapses to zero
                        // when the row wraps its content) + the weight fills
                        // whatever the 220dp minimum menu width leaves.
                        Spacer(Modifier.width(24.dp))
                        Spacer(Modifier.weight(1f))
                        Switch(
                            checked = formatted,
                            onCheckedChange = { onToggleFormatting(it) },
                        )
                    }
                }
            }
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
    // Task 63 (round 23 — D): the Task-57 P4 debug affordances (copy icon +
    // raw source lines + their DebugPreferences gates) are removed with the
    // Developer-tools toolkit; the USER-FACING long-press URL copy
    // ([onCopyUrl]) stays.

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

/**
 * The aniyomi ResolverSheet's chip: PlayArrow + quality, primaryContainer.
 *
 * Task 63 (round 23 — D): the Task-57 P4 debug affordances (the trailing
 * copy icon + the raw url/type line) are removed with the Developer-tools
 * toolkit. The LONG-PRESS url copy stays (user-facing).
 */
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
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = if (isFailed) 0.4f else 1f)
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (isFailed) "$quality (failed)" else quality,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = contentColor,
                )
            }
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
 *
 * Task 56 (F5): keys carry the row INDEX — a provider that emits the same
 * URL twice (multi-quality DASH manifests) must never crash the LazyColumn
 * with duplicate keys (the resolver dedups by URL; this is defense in depth).
 *
 * Task 63 (round 23 — D): the Task-57 P4 debug affordances (trailing copy
 * icon + raw url/type line) are removed with the Developer-tools toolkit.
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
        itemsIndexed(links, key = { index, link -> "${link.url}#$index" }) { _, link ->
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
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Row(
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
}

private fun CsLinkType.badgeLabel(): String = when (this) {
    CsLinkType.VIDEO -> "VIDEO"
    CsLinkType.M3U8 -> "HLS"
    CsLinkType.DASH -> "DASH"
}
