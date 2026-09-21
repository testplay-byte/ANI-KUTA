package com.confused.anikuta.feature.cswatch.impl

import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.csplayer.CsAudioTag
import com.confused.anikuta.core.csplayer.CsLinkType
import com.confused.anikuta.core.csplayer.CsServerNames
import com.confused.anikuta.core.csplayer.CsVideoLink
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.DebugPreferences
import org.koin.compose.koinInject

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
 * Task 57 (round 17 — P4 + P5): [serverNameOf] also strips BRACKETED
 * audio/quality decorations glued to segments (P5), and both lists grow the
 * debug affordances — a per-row copy icon + raw url/type line, plus the
 * [buildResolveDebugReport] header copy (P4). All gated by DebugPreferences
 * flags (Settings → Debug options), default OFF: the default path renders
 * byte-identical to the pre-Task-57 lists.
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
 *
 * D-551 (the MovieBox device round): the derivation now understands the
 * LANGUAGE-AUDIO decorations — "MovieBox (Hindi Audio)" + "MovieBox
 * (Original Audio)" collapse into ONE server card whose audio versions are
 * "Hindi" and "Original" (they used to render as two separate servers, and
 * the D-550 sibling matcher missed them the same way). When a version is a
 * single DASH link whose manifest was probed, [CsAudioGroup.availableQualities]
 * carries every resolution the stream offers — the card renders the
 * "Available:" line under the chips.
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
                    // "Default" always last. Language versions ("Hindi",
                    // "Original") sort as real flavors in encounter order.
                    compareBy({ entry -> entry.key == CsAudioTag.DEFAULT }, { entry -> entry.key == "DUB" }),
                )
                .map { (label, versionLinks) ->
                    val sorted = versionLinks.sortedByDescending { qualityRank(it.quality) }
                    val labels = sorted.map { it.qualityLabel }
                    CsAudioGroup(
                        label = label,
                        links = sorted,
                        disambiguateType = labels.groupingBy { it }.eachCount().any { it.value > 1 },
                        // D-551: a version that is ONE DASH link with a probed
                        // manifest exposes the stream's full resolution list.
                        availableQualities = sorted.singleOrNull()
                            ?.takeIf { it.type == CsLinkType.DASH }
                            ?.availableQualities
                            ?.takeIf { it.size > 1 },
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
 * D-553 — the COLLAPSED header's short audio-version forms.
 *
 * The header renders the version labels as chips at intrinsic width beside the
 * weighted server name; with 3+ versions (MovieBox ships 4: Hindi / Original /
 * Malayalam / Tamil) the row overflows and the names truncate. The device
 * verdict: "if there is not enough space to show the full names of the
 * available audio versions then their simplified or minimified versions
 * should show."
 *
 * Deterministic, pure, unit-tested — no text measuring, no layout feedback
 * loops. The render rule (≤2 chips = full, ≥3 = short) lives at the call
 * site; [shortAudioLabel] only translates one label.
 */
internal fun shortAudioLabel(label: String): String {
    val base = label.trim().removeSuffix(" Audio").trim()
    if (base.isEmpty()) return label
    SHORT_AUDIO_LABELS[base.lowercase()]?.let { return it }
    // SUB/DUB (and any ≤4-char tag) are already minimal.
    if (base.length <= 4) return base
    return base.take(3).uppercase()
}

/** The curated short forms — the languages the current providers ship plus
 *  the common fallbacks; anything unknown falls back to its first 3 letters. */
private val SHORT_AUDIO_LABELS: Map<String, String> = mapOf(
    "hindi" to "HIN",
    "original" to "ORIG",
    "malayalam" to "MAL",
    "tamil" to "TAM",
    "telugu" to "TEL",
    "kannada" to "KAN",
    "bengali" to "BEN",
    "marathi" to "MAR",
    "punjabi" to "PUN",
    "english" to "ENG",
    "japanese" to "JPN",
    "korean" to "KOR",
    "chinese" to "CHN",
    "mandarin" to "CHN",
    "spanish" to "SPA",
    "portuguese" to "POR",
    "french" to "FRE",
    "german" to "GER",
    "russian" to "RUS",
    "arabic" to "ARA",
    "turkish" to "TUR",
    "indonesian" to "IND",
    "thai" to "THA",
    "vietnamese" to "VIE",
    "filipino" to "FIL",
    "urdu" to "URD",
)

/**
 * The SERVER part of a link name — D-551: the derivation MOVED to
 * :core:cs-player's [CsServerNames] (the :app sibling matcher needs the exact
 * same function — one vocabulary, all consumers; see the D-550 sibling miss).
 * This delegate keeps every call site + test in this module unchanged.
 */
internal fun serverNameOf(name: String): String = CsServerNames.of(name)

/** One accordion server: the label + its audio versions (each w/ links). */
internal data class CsServerGroup(
    val name: String,
    val audioVersions: List<CsAudioGroup>,
)

/** One audio version within a server (SUB/DUB/… or "Default", D-551:
 *  language versions like "Hindi" / "Original"). */
internal data class CsAudioGroup(
    val label: String,
    val links: List<CsVideoLink>,
    /** True when two links of this version share a quality label — chips then
     *  carry the type badge (HLS/DASH) so rows stay distinguishable. */
    val disambiguateType: Boolean,
    /**
     * D-551 (D-552): every resolution the version's single DASH link offers
     * (the probed manifest's video rep heights, descending); null = unknown/
     * not probed/multi-link version — the version then keeps its declared
     * chip and NO per-height chips render. Non-null: the per-height chips
     * REPLACE the declared chip + the old "Available:" text (the chips ARE
     * the list now).
     */
    val availableQualities: List<Int>? = null,
)

// ════════════════════════════════════════════════════════════════════════════
//  Task 57 (P4) — the resolve debug report (pure — unit-tested)
// ════════════════════════════════════════════════════════════════════════════

/**
 * Task 57 (P4 — debug tooling): the plain-text resolve report a bug report
 * needs — provider/anime/episode header + one block per link (name, derived
 * server, audio label, quality + chip rank, type, referer, sorted header
 * KEYS, url). Deterministic by construction (numbered links, no timestamps)
 * so the same resolve copies byte-identically every time. Header KEYS only:
 * header VALUES never ride the clipboard (tokens stay on the device).
 *
 * Consumed by the resolve sheet's header copy action when Debug options'
 * "Copy button" flag is ON; [buildLinkDetail] is the one-link form for the
 * per-row copy actions.
 */
internal fun buildResolveDebugReport(
    provider: String,
    animeTitle: String,
    episodeNumber: Float,
    links: List<CsVideoLink>,
): String = buildString {
    appendLine("ANI-KUTA resolve report (v2 debug)")
    appendLine("provider: $provider")
    appendLine("anime: $animeTitle")
    appendLine("episode: $episodeNumber")
    appendLine("links: ${links.size}")
    if (links.isNotEmpty()) {
        appendLine("---")
        links.forEachIndexed { index, link ->
            appendLinkDetail(this, index + 1, link)
        }
    }
}

/**
 * Task 57 (P4): the ONE-LINK form of [buildResolveDebugReport] — the payload
 * of the per-chip / per-row copy actions (same per-link block format).
 */
internal fun buildLinkDetail(link: CsVideoLink): String = buildString {
    appendLinkDetail(this, 1, link)
}

/** One link's numbered detail block (shared by the report + the one-link copy). */
private fun appendLinkDetail(sb: StringBuilder, number: Int, link: CsVideoLink) {
    sb.appendLine("$number. name: ${link.name}")
    sb.appendLine("   server: ${serverNameOf(link.name)}")
    sb.appendLine("   audio: ${link.audioLabel}")
    sb.appendLine("   quality: ${link.qualityLabel} (${qualityRank(link.quality)})")
    sb.appendLine("   type: ${link.type}")
    sb.appendLine("   referer: ${link.referer}")
    sb.appendLine("   headers: ${link.allHeaders.keys.sorted()}")
    sb.appendLine("   url: ${link.url}")
}

/**
 * Task 57 (P4): the resolve lists' copy feedback — the repo precedent
 * (LibraryScreen / DetailsScreen / ErrorActivity) is LocalClipboardManager +
 * [AnnotatedString] with a short Toast confirmation (no silent taps). Returns
 * a `(text, toast)` invoker shared by the per-row and header-level actions.
 */
@Composable
internal fun rememberCopyFeedback(): (String, String) -> Unit {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    return remember(clipboardManager, context) {
        val copy: (String, String) -> Unit = { text, toast ->
            clipboardManager.setText(AnnotatedString(text))
            Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
        }
        copy
    }
}

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
    onPickVideo: (CsVideoLink, Int?) -> Unit,
    /** The server to open first (remembered/preferred); null = first server. */
    preferredServer: String? = null,
    /** The currently-playing link's URL (in-player sheet: highlight + open). */
    currentLinkUrl: String? = null,
    /** URLs that errored in the engine (in-player sheet: strike-through). */
    failedLinkUrls: Set<String> = emptySet(),
    /**
     * D-553: the LIVE decoder height of the playing stream (null elsewhere).
     * With [currentLinkUrl] it lights exactly ONE probed chip — the resolution
     * actually playing of the version actually playing — instead of the old
     * D-552 "no marking at all" compromise.
     */
    currentPlayingHeight: Int? = null,
    onCopyUrl: ((String) -> Unit)? = null,
    /** Task 57 (P4): the debug affordances' gate (copy icon + raw source
     *  lines). Default OFF — see [rememberCopyFeedback]. */
    debugPreferences: DebugPreferences = koinInject(),
) {
    // Task 57 (P4): LIVE-collected debug flags — toggling Settings → Debug
    // options while a sheet is open applies immediately; both default false,
    // so the default path renders byte-identical to the pre-Task-57 accordion.
    val copyEnabled by debugPreferences.resolveCopyButtonFlow()
        .collectAsState(initial = false)
    val showSources by debugPreferences.showResolveSourcesFlow()
        .collectAsState(initial = false)

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

    // D-553: a plain Column — the host sheets own the scrolling now (the
    // player's links sheet is ONE scrollable body so the "Quality for this
    // stream" section can never be clipped out of reach again). Server counts
    // are tiny (≤ a handful); laziness bought nothing and cost the section.
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        servers.forEach { server ->
            val isExpanded = expandedServer == server.name
            CsServerCard(
                server = server,
                isExpanded = isExpanded,
                currentLinkUrl = currentLinkUrl,
                failedLinkUrls = failedLinkUrls,
                currentPlayingHeight = currentPlayingHeight,
                onToggle = {
                    expandedServer = if (isExpanded) null else server.name
                },
                onPickVideo = onPickVideo,
                onCopyUrl = onCopyUrl,
                copyEnabled = copyEnabled,
                showSources = showSources,
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
    currentPlayingHeight: Int?,
    onToggle: () -> Unit,
    onPickVideo: (CsVideoLink, Int?) -> Unit,
    onCopyUrl: ((String) -> Unit)?,
    /** Task 57 (P4): per-chip copy icon + raw source line gates. */
    copyEnabled: Boolean,
    showSources: Boolean,
) {
    // Task 57 (P4): the per-chip copy action's clipboard + toast feedback.
    val copyFeedback = rememberCopyFeedback()
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
                    // D-553: 3+ versions render the SHORT forms — four full
                    // labels ("Hindi Original Malayalam Tamil") overflow the
                    // row beside the server name; the device verdict asked for
                    // exactly this minimification. One/two versions keep the
                    // full names (they always fit).
                    val shortForms = audioChips.size > 2
                    audioChips.reversed().forEach { version ->
                        // D-553: the CURRENTLY-PLAYING version's label lights
                        // up (in-player context only; the resolve sheet passes
                        // no currentLinkUrl so nothing lights there).
                        val isCurrentVersion = version.links.any { it.url == currentLinkUrl }
                        Surface(
                            color = if (isCurrentVersion) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            },
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = if (shortForms) shortAudioLabel(version.label) else version.label,
                                fontFamily = RobotoFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isCurrentVersion) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
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
                            // D-553: the playing version's label is tinted —
                            // the same current marker the header chips carry.
                            val isCurrentVersion = version.links.any { it.url == currentLinkUrl }
                            Text(
                                text = version.label,
                                fontFamily = RobotoFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isCurrentVersion) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val probedHeights = version.availableQualities
                            val probedLink = version.links.singleOrNull()
                            if (probedHeights != null && probedLink != null) {
                                // D-552: ONE CLICKABLE CHIP PER PROBED RESOLUTION —
                                // the pick target is (link, height): playback pins
                                // that rep via the engine's one-shot start-height
                                // override, download passes the height as the
                                // request's quality. This REPLACES the declared
                                // chip + the D-551 "Available:" text: the declared
                                // max rep was a lie about the version ("1080p is
                                // the Hindi one, 720p the Original") — every
                                // variant's manifest carries the same spread, and
                                // the user gets the other extensions' experience:
                                // every resolution visible, every resolution
                                // tappable. D-553: the marking now EXISTS —
                                // the current link's chip at the live decoder
                                // height lights (see the isSelected below); the
                                // resolve sheet still marks nothing. copy/debug
                                // affordances ride the same single link as before.
                                probedHeights.forEach { height ->
                                    CsQualityChip(
                                        quality = "${height}p",
                                        // D-553: the D-552 "no marking" compromise
                                        // dies — with the LIVE decoder height riding
                                        // in, exactly ONE chip lights: the resolution
                                        // actually playing of the version actually
                                        // playing. The resolve sheet (no
                                        // currentLinkUrl) still marks nothing.
                                        isSelected = probedLink.url == currentLinkUrl &&
                                            currentPlayingHeight != null &&
                                            height == currentPlayingHeight,
                                        isFailed = probedLink.url in failedLinkUrls,
                                        onClick = { onPickVideo(probedLink, height) },
                                        onLongClick = onCopyUrl?.let { cb -> { cb(probedLink.url) } },
                                        onCopyDetails = if (copyEnabled) {
                                            { copyFeedback(buildLinkDetail(probedLink), "Copied 1 link details") }
                                        } else null,
                                        sourceDetail = if (showSources) "${probedLink.type} · ${probedLink.url}" else null,
                                    )
                                }
                            } else {
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
                                        onClick = { onPickVideo(link, null) },
                                        onLongClick = onCopyUrl?.let { cb -> { cb(link.url) } },
                                        onCopyDetails = if (copyEnabled) {
                                            // Task 57 (P4): copies THIS link's full details.
                                            { copyFeedback(buildLinkDetail(link), "Copied 1 link details") }
                                        } else null,
                                        sourceDetail = if (showSources) "${link.type} · ${link.url}" else null,
                                    )
                                }
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
 * Task 57 (P4 — debug affordances, both default-OFF):
 *  - [onCopyDetails]: a small copy icon on the chip's RIGHT that copies this
 *    link's full resolve details ([buildLinkDetail]);
 *  - [sourceDetail]: the raw url/type as a tiny secondary line (10sp,
 *    onSurfaceVariant, end-ellipsized — URLs are long).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CsQualityChip(
    quality: String,
    onClick: () -> Unit,
    isSelected: Boolean = false,
    isFailed: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onCopyDetails: (() -> Unit)? = null,
    sourceDetail: String? = null,
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
                if (onCopyDetails != null) {
                    // Task 57 (P4): the copy affordance on the chip's right.
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy link details",
                        tint = contentColor,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onCopyDetails() },
                    )
                }
            }
            if (sourceDetail != null) {
                // Task 57 (P4): the raw source debug line (type + url).
                Text(
                    text = sourceDetail,
                    fontFamily = RobotoFamily,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
 * URL twice (multi-quality DASH manifests) must never render a duplicated row
 * with duplicate keys (the resolver dedups by URL; this is defense in depth).
 *
 * Task 57 (P4 — debug affordances, both default-OFF): a trailing copy icon
 * per row (copies the full link detail block, [buildLinkDetail]) and the raw
 * url/type line under the label — same gates as the accordion's chips.
 */
@Composable
internal fun CsRawLinkList(
    links: List<CsVideoLink>,
    onPickVideo: (CsVideoLink, Int?) -> Unit,
    currentLinkUrl: String? = null,
    failedLinkUrls: Set<String> = emptySet(),
    /** Task 57 (P4): the debug affordances' gate (copy icon + raw source
     *  lines). Default OFF. */
    debugPreferences: DebugPreferences = koinInject(),
) {
    // D-553: plain Column — see the CsServerAccordion note (the host sheet's
    // single scroll body owns the scrolling; duplicate URLs stay guarded by
    // the resolver's dedup, and the per-row key was only the LazyColumn's).
    val copyEnabled by debugPreferences.resolveCopyButtonFlow()
        .collectAsState(initial = false)
    val showSources by debugPreferences.showResolveSourcesFlow()
        .collectAsState(initial = false)
    val copyFeedback = rememberCopyFeedback()

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        links.forEachIndexed { _, link ->
            val label = link.displayLabel +
                (link.audioLabel.takeIf { it != CsAudioTag.DEFAULT }?.let { " · $it" } ?: "")
            val isCurrent = link.url == currentLinkUrl
            val isFailed = link.url in failedLinkUrls
            Surface(
                color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(10.dp),
                border = if (isCurrent) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier.fillMaxWidth().clickable(enabled = !isFailed) { onPickVideo(link, null) },
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
                        if (copyEnabled) {
                            // Task 57 (P4): trailing copy icon — full link details.
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy link details",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable {
                                        copyFeedback(buildLinkDetail(link), "Copied 1 link details")
                                    },
                            )
                        }
                        if (isCurrent) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    if (showSources) {
                        // Task 57 (P4): the raw source debug line (type + url).
                        Text(
                            text = "${link.type} · ${link.url}",
                            fontFamily = RobotoFamily,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
