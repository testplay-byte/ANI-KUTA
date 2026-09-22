package com.confused.anikuta.feature.animedetails

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.designsystem.theme.LocalCardDescriptionColor
import com.confused.anikuta.core.designsystem.theme.LocalCardHeadingColor
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import eu.kanade.tachiyomi.animesource.model.SEpisode
import org.koin.compose.koinInject

/**
 * D-555: the episode-list LAYOUT styles — FOUR completely different
 * paradigms (the user's verdict on the D-554 first draft was explicit:
 * "switching between the three available layouts is definitely not good …
 * a complete UI redesign on switching between each and every single one
 * of them … rather than just hiding some features like synopsis or maybe
 * adjusting the shape a little bit"). Rendered through [EpisodeListEntry]
 * — the ONE dispatcher the real details-page list AND the Appearance →
 * "Episode list" settings page's live preview both call (the D-481
 * doctrine: what you tune is exactly what you get).
 *
 * Each style is a CURATED preset (the D-523 lesson — layouts live in code,
 * not in a free-form editor), and each is a DIFFERENT STRUCTURE, not a
 * variation of one row:
 *
 * - [CLASSIC] (default = the look that has always been): [EpisodeRow] —
 *     thumbnail left · title · date/audio pills · synopsis · download
 *     control; swipe-to-toggle watched.
 * - [GRID]: a two-column poster wall (EpisodeLayouts.kt) — full-bleed
 *     16:9 thumbnail cells, EP badge + download badge overlays, watched
 *     = grayscale + centered check; long-press toggles watched.
 * - [TIMELINE]: a schedule spine (EpisodeLayouts.kt) — a continuous
 *     vertical rail whose node carries the AIR DATE as the primary
 *     element; a compact card hangs to the right of every node.
 * - [CINEMA]: full-bleed banner cards (EpisodeLayouts.kt) — the
 *     thumbnail AS the card, a bottom scrim, a huge ghost episode
 *     number, overlaid title + translucent date/audio chips.
 *
 * The element toggles in [EpisodeListDisplayStyle] are honored WITHIN the
 * style's frame: a layout that never renders a section cannot be talked
 * into rendering it (GRID/TIMELINE/CINEMA have no synopsis by design —
 * that is their identity, not a missing feature).
 */
enum class EpisodeListRowStyle {
    CLASSIC,
    GRID,
    TIMELINE,
    CINEMA;

    companion object {
        /**
         * D-529 lesson applied: seeding goes through THIS lenient lookup so
         * the settings page (and any future caller) always highlights the
         * SAME style the renderer will actually draw.
         *
         * D-555 migration: the D-554 first-draft keys (DETAILED/COMPACT/
         * MINIMAL — one row with sections hidden or shrunk) were REPLACED
         * by the four-layout redesign per the user's own verdict; stored
         * legacy values fall back to CLASSIC (the look that has always
         * been). Unknown/null/blank → CLASSIC too.
         */
        fun fromKey(key: String?): EpisodeListRowStyle = when (key?.trim()?.uppercase()) {
            "GRID" -> GRID
            "TIMELINE" -> TIMELINE
            "CINEMA" -> CINEMA
            else -> CLASSIC
        }
    }
}

/**
 * D-554/D-555: the user-tunable appearance knobs. Every default equals
 * today's behavior, so the zero-prefs experience is byte-identical to the
 * pre-D-554 list (CLASSIC renders the unchanged row).
 *
 * Carried from [com.confused.anikuta.core.preferences.EpisodeListPreferences]
 * (collected ONCE per screen — ONE subscription set for the whole list, not
 * 7×N rows) into each [EpisodeListEntry].
 *
 * Per-layout semantics (a layout that never renders a section cannot be
 * talked into rendering it):
 * - showSynopsis → CLASSIC only (the other layouts' identity).
 * - showDatePill → "Release date": the CLASSIC pill, the GRID chip, the
 *     TIMELINE node label (off → "EP n" nodes), the CINEMA scrim chip.
 * - showAudioPills → every layout's availability chips.
 * - showWatchProgress → the bar on the imagery's bottom edge (TIMELINE:
 *     on its card).
 * - dimWatched → the watched treatment in every layout (CLASSIC fades the
 *     whole row; the others grayscale/dim the imagery + their watched
 *     badge/node).
 * - showDownloadControl → the CLASSIC control, the GRID/TIMELINE badge,
 *     the CINEMA overlay.
 */
data class EpisodeListDisplayStyle(
    val rowStyle: EpisodeListRowStyle = EpisodeListRowStyle.CLASSIC,
    /** The two-line synopsis (CLASSIC only). */
    val showSynopsis: Boolean = true,
    /** The release date — shown in every layout where it fits (see above). */
    val showDatePill: Boolean = true,
    /** The SUB · DUB · HSUB availability pills/chips. */
    val showAudioPills: Boolean = true,
    /**
     * The watch-progress bar on the thumbnail's bottom edge. The
     * download-progress overlay is NOT covered by this — it is transient
     * state feedback (only visible mid-download), not decoration.
     */
    val showWatchProgress: Boolean = true,
    /** The watched alpha-0.5 + grayscale treatment. */
    val dimWatched: Boolean = true,
    /** The per-row [EpisodeDownloadControl]. */
    val showDownloadControl: Boolean = true,
)

/**
 * D-554: the pure (style × content) algebra behind the date/audio pills row's
 * visibility — extracted so the unit test locks it without Compose.
 *
 * The row renders when ANY of its three residents survives the gates: the
 * date pill, the audio pills, or the download control (which moves here
 * whenever the synopsis section is NOT rendered — including when the user
 * toggled the synopsis off or the style omits it, matching the original
 * description.isNullOrBlank() behavior).
 *
 * D-555: this is the CLASSIC-path algebra only (GRID/TIMELINE/CINEMA draw
 * their own chips inline); the old MINIMAL style-level date gate died with
 * the three-variation design.
 */
fun pillsRowVisible(
    style: EpisodeListDisplayStyle,
    hasDate: Boolean,
    hasAudio: Boolean,
    showsSynopsis: Boolean,
): Boolean {
    val showDate = style.showDatePill && hasDate
    val showAudio = style.showAudioPills && hasAudio
    return showDate || showAudio || (style.showDownloadControl && !showsSynopsis)
}

/**
 * D-555: the row's user actions as ONE parameter bag — the dispatcher and
 * the GRID/TIMELINE/CINEMA renderers share it (play + the 7 download
 * callbacks + the watched toggle). The classic row keeps its flat signature.
 */
data class EpisodeRowActions(
    val onClick: () -> Unit = {},
    val onDownload: () -> Unit = {},
    val onPause: () -> Unit = {},
    val onResume: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onPlayDownloaded: () -> Unit = {},
    val onToggleWatched: () -> Unit = {},
)

/**
 * D-555: the RESOLVED display values one episode renders from — extracted
 * from the row's body so the four layouts share ONE resolution pass (the
 * D-306 extension-first rules + the D-230 fallback pref, single source).
 */
internal data class EpisodeDisplayData(
    val title: String,
    val description: String?,
    /** The full date label ("Jan 1, 2025") or null when the episode has none. */
    val dateText: String?,
    /** The short date label ("Jan 1") — the GRID chip + TIMELINE node label. */
    val shortDateText: String?,
    /** The SUB/DUB/HSUB availability labels (the HSUB-distinct row parse). */
    val audioLabels: List<String>,
    /**
     * The resolved thumbnail: extension preview → provider metadata → the
     * anime cover (the D-230 fallback pref) → null.
     */
    val thumbnailUrl: String?,
)

/**
 * D-555: the ONE display-resolution pass — provider metadata fills the
 * gaps behind the extension's own values (D-306), the cover fallback rides
 * the D-230 pref, the audio labels come from the HSUB-distinct parse, and
 * BOTH date label sizes derive from the same epoch (provider air date
 * first, the extension's upload date second).
 */
@Composable
internal fun rememberEpisodeDisplayData(
    episode: SEpisode,
    metadata: com.confused.anikuta.core.metadata.EpisodeMetadata?,
    fallbackCoverUrl: String?,
): EpisodeDisplayData {
    val title = remember(episode, metadata) { EpisodeDisplayResolver.title(episode, metadata) }
    val description = remember(episode, metadata) { EpisodeDisplayResolver.description(episode, metadata) }
    val episodeListPrefs = koinInject<com.confused.anikuta.core.preferences.EpisodeListPreferences>()
    val thumbnailFallback by episodeListPrefs.thumbnailFallback.changes.collectAsState(
        initial = episodeListPrefs.thumbnailFallback.get(),
    )
    val thumbnailUrl = when {
        !episode.preview_url.isNullOrBlank() -> episode.preview_url
        !metadata?.thumbnailUrl.isNullOrBlank() -> metadata?.thumbnailUrl
        thumbnailFallback == "COVER" -> fallbackCoverUrl
        else -> null
    }
    val dateText = remember(episode, metadata) {
        val airDate = metadata?.airDate
        when {
            airDate != null && airDate > 0 -> formatDate(airDate)
            episode.date_upload > 0 -> formatDate(episode.date_upload)
            else -> null
        }
    }
    val shortDateText = remember(episode, metadata) {
        val airDate = metadata?.airDate
        val epoch = if (airDate != null && airDate > 0) airDate else episode.date_upload
        if (epoch > 0) formatShortDate(epoch) else null
    }
    val audioLabels = remember(episode) {
        parseAudioAvailability(episode.scanlator, episode.name).labels
    }
    return EpisodeDisplayData(
        title = title,
        description = description,
        dateText = dateText,
        shortDateText = shortDateText,
        audioLabels = audioLabels,
        thumbnailUrl = thumbnailUrl,
    )
}

/**
 * D-555: THE episode-list entry — the dispatcher BOTH call sites (the
 * details-page list AND the settings page's live preview) go through.
 * CLASSIC delegates to [EpisodeRow] (the unchanged, user-verified row);
 * the other three styles hand the resolved [EpisodeDisplayData] to their
 * EpisodeLayouts.kt renderer. One dispatch, zero drift — the D-481
 * doctrine at the list level.
 */
@Composable
fun EpisodeListEntry(
    episode: SEpisode,
    metadata: com.confused.anikuta.core.metadata.EpisodeMetadata?,
    onClick: () -> Unit,
    episodeTag: EpisodeTag? = null,
    downloadState: EpisodeDownloadState = EpisodeDownloadState.NotDownloaded,
    fallbackCoverUrl: String? = null,
    onDownload: () -> Unit = {},
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onCancel: () -> Unit = {},
    onRetry: () -> Unit = {},
    onDelete: () -> Unit = {},
    onPlayDownloaded: () -> Unit = {},
    isWatched: Boolean = false,
    progressFraction: Float = 0f,
    onToggleWatched: () -> Unit = {},
    style: EpisodeListDisplayStyle = EpisodeListDisplayStyle(),
) {
    val actions = EpisodeRowActions(
        onClick = onClick,
        onDownload = onDownload,
        onPause = onPause,
        onResume = onResume,
        onCancel = onCancel,
        onRetry = onRetry,
        onDelete = onDelete,
        onPlayDownloaded = onPlayDownloaded,
        onToggleWatched = onToggleWatched,
    )
    when (style.rowStyle) {
        EpisodeListRowStyle.CLASSIC -> EpisodeRow(
            episode = episode,
            metadata = metadata,
            onClick = onClick,
            episodeTag = episodeTag,
            downloadState = downloadState,
            fallbackCoverUrl = fallbackCoverUrl,
            onDownload = onDownload,
            onPause = onPause,
            onResume = onResume,
            onCancel = onCancel,
            onRetry = onRetry,
            onDelete = onDelete,
            onPlayDownloaded = onPlayDownloaded,
            isWatched = isWatched,
            progressFraction = progressFraction,
            onToggleWatched = onToggleWatched,
            style = style,
        )
        EpisodeListRowStyle.GRID -> EpisodeGridCell(
            episode = episode,
            display = rememberEpisodeDisplayData(episode, metadata, fallbackCoverUrl),
            actions = actions,
            episodeTag = episodeTag,
            downloadState = downloadState,
            isWatched = isWatched,
            progressFraction = progressFraction,
            style = style,
        )
        EpisodeListRowStyle.TIMELINE -> EpisodeTimelineRow(
            episode = episode,
            display = rememberEpisodeDisplayData(episode, metadata, fallbackCoverUrl),
            actions = actions,
            episodeTag = episodeTag,
            downloadState = downloadState,
            isWatched = isWatched,
            progressFraction = progressFraction,
            style = style,
        )
        EpisodeListRowStyle.CINEMA -> EpisodeCinemaCard(
            episode = episode,
            display = rememberEpisodeDisplayData(episode, metadata, fallbackCoverUrl),
            actions = actions,
            episodeTag = episodeTag,
            downloadState = downloadState,
            isWatched = isWatched,
            progressFraction = progressFraction,
            style = style,
        )
    }
}

/** D-317: contextual episode tag (per-season number / "S-n/E-m" compound). */
data class EpisodeTag(
    val season: Int?,
    val number: String,
)

// ── Audio availability parsing (ported from old project) ──
// FILE-PRIVATE by design (D-554 CI round): EpisodeListProcessor.kt declares a
// file-private parseAudioAvailability with an IDENTICAL parameter list (a
// different, coarser algorithm — its sub check folds HSUB in). Both were
// file-private for rounds (the row's lived in DetailsScreen.kt); the
// extraction made this one PUBLIC, and two same-package top-level functions
// with the same signature are a "Conflicting overloads" compile error, not
// shadowing. Nothing outside this file needs it — the row is the only
// consumer of the HSUB-distinct pills parse.
private data class AudioAvailability(
    val hasSub: Boolean,
    val hasDub: Boolean,
    val hasHsub: Boolean,
) {
    val hasAny: Boolean get() = hasSub || hasDub || hasHsub
    val labels: List<String> get() = buildList {
        if (hasSub) add("SUB")
        if (hasDub) add("DUB")
        if (hasHsub) add("HSUB")
    }
}

private fun parseAudioAvailability(scanlator: String?, episodeName: String): AudioAvailability {
    val haystack = ((scanlator ?: "") + " " + episodeName).uppercase()
    val hasHsub = haystack.contains("HSUB") || haystack.contains("HARDSUB")
    val hasSub = haystack.contains("SUB") && !hasHsub
    val hasDub = haystack.contains("DUB") && !hasHsub
    return AudioAvailability(hasSub = hasSub, hasDub = hasDub, hasHsub = hasHsub)
}

fun formatEpisodeNumber(num: Float): String {
    return com.confused.anikuta.core.common.EpisodeTitleParser.formatEpisodeNumber(num)
}

fun formatDate(epochMillis: Long): String {
    if (epochMillis <= 0) return ""
    val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(epochMillis))
}

/**
 * THE episode row renderer — shared by the details-page list AND the
 * Appearance → "Episode list" settings page's live preview (the D-481
 * doctrine: the preview calls the SAME production renderer, so what you
 * tune is exactly what you'll get).
 *
 * D-554: moved out of DetailsScreen.kt (which had grown past 4,200 lines)
 * so the settings page can render it; the [style] parameter carries the
 * appearance knobs with defaults == the pre-D-554 behavior — the
 * zero-prefs experience is unchanged.
 *
 * D-555: this composable IS the CLASSIC layout renderer now — the
 * [EpisodeListEntry] dispatcher routes CLASSIC here and the other three
 * layouts to their EpisodeLayouts.kt renderers. The display-value
 * resolution moved into [rememberEpisodeDisplayData] (shared by every
 * layout), and the swipe-to-toggle gesture into [SwipeToToggleWatched]
 * (shared by CLASSIC/TIMELINE/CINEMA).
 *
 * History: Phase WP (swipe-to-toggle + watched styling), D-211 (the
 * full-width download progress overlay), D-229 (the fallback cover),
 * D-230 (the configurable thumbnail fallback), D.6/D.8 (the state-driven
 * download control), D-306 (extension-first display resolution), D-317
 * (the compound season tag).
 */
@Composable
fun EpisodeRow(
    episode: SEpisode,
    metadata: com.confused.anikuta.core.metadata.EpisodeMetadata?,
    onClick: () -> Unit,
    // D-317: contextual episode tag (per-season number / "S-n/E-m" compound).
    episodeTag: EpisodeTag? = null,
    downloadState: EpisodeDownloadState = EpisodeDownloadState.NotDownloaded,
    // D-229: Fallback cover URL (the anime's cover image) — used when the
    // episode has no per-episode thumbnail. Prevents bare circle placeholders.
    fallbackCoverUrl: String? = null,
    onDownload: () -> Unit = {},
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onCancel: () -> Unit = {},
    onRetry: () -> Unit = {},
    onDelete: () -> Unit = {},
    onPlayDownloaded: () -> Unit = {},
    // Phase WP: watched state + swipe-to-toggle.
    isWatched: Boolean = false,
    progressFraction: Float = 0f,
    onToggleWatched: () -> Unit = {},
    // D-554: the appearance knobs (defaults == the pre-D-554 look).
    style: EpisodeListDisplayStyle = EpisodeListDisplayStyle(),
) {
    // ── Parse display values ──
    // D-555: the resolution lives in ONE place — rememberEpisodeDisplayData —
    // shared with the GRID/TIMELINE/CINEMA renderers through the dispatcher.
    // The D-306 extension-first rules + the D-230 fallback pref are unchanged.
    val display = rememberEpisodeDisplayData(episode, metadata, fallbackCoverUrl)
    val displayTitle = display.title
    val description = display.description
    val thumbnailUrl = display.thumbnailUrl
    val epNumText = formatEpisodeNumber(episode.episode_number)
    val dateText = display.dateText
    val audioLabels = display.audioLabels

    // ── D-555: the style gates — this renderer IS the CLASSIC layout (the
    // dispatcher guarantees it; the D-554 COMPACT/MINIMAL variations died in
    // the four-layout redesign). The synopsis/date/audio gates keep the
    // user-toggle semantics.
    val showSynopsisSection = style.showSynopsis && !description.isNullOrBlank()
    val showDate = style.showDatePill && dateText != null
    val showAudio = style.showAudioPills && audioLabels.isNotEmpty()

    // ── Phase WP: watched styling (IM4: alpha fade + grayscale on the thumbnail) ──
    // D-554: the treatment is a USER TOGGLE now — dimWatched=false renders
    // watched episodes at full strength.
    val targetAlpha = if (isWatched && style.dimWatched) 0.5f else 1.0f
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        label = "watched_alpha",
    )
    val colorFilter = remember(isWatched, style.dimWatched) {
        if (isWatched && style.dimWatched) {
            ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                0.299f, 0.587f, 0.114f, 0f, 0f,
                0.299f, 0.587f, 0.114f, 0f, 0f,
                0.299f, 0.587f, 0.114f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )))
        } else null
    }

    // ── Card ── (D-555: the swipe-to-toggle gesture lives in
    // SwipeToToggleWatched now — the same wrapper the TIMELINE + CINEMA
    // layouts reuse; the watched alpha stays HERE because the CLASSIC dim
    // treatment fades the WHOLE card, unlike the image-only fades of the
    // other layouts. The D-211 download-progress overlay remains part of
    // the card content.)
    SwipeToToggleWatched(
        isWatched = isWatched,
        onToggleWatched = onToggleWatched,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha },
    ) {
        // The actual card — opaque (NOT transparent). The wrapper carries
        // the swipe offset + gesture + background icon; THIS Box keeps the
        // surface + the click (the D-211 shape).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick),
        ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
        ) {
            // ══ TOP SECTION: thumbnail (left) + title/meta (right) + download (far right) ══
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                // ── Thumbnail (left) with EP tag overlay (TopStart, themed primary) ──
                if (thumbnailUrl != null) {
                    Box(
                        modifier = Modifier.size(width = 120.dp, height = 68.dp),
                    ) {
                        AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = displayTitle,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop,
                            // Phase WP: grayscale when watched (IM4 — GPU-side, cheap).
                            colorFilter = colorFilter,
                        )
                        // EP tag — themed primary background, 6dp corners, Bold White text.
                        // Shows 'EP N' (not just 'N'). Positioned at TopStart (like old project).
                        // D-317: contextual variants — a per-season number inside a season
                        // slice, or the "S-3/E-5" compound tag (season + episode in two
                        // shades of the theme color, slash separator) in the All list when
                        // the "season in episode tag" setting is on.
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                        ) {
                            if (episodeTag != null && episodeTag.season != null) {
                                val tagColor = MaterialTheme.colorScheme.onPrimary
                                val compoundTag = androidx.compose.ui.text.buildAnnotatedString {
                                    withStyle(
                                        SpanStyle(
                                            color = tagColor.copy(alpha = 0.68f),
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    ) { append("S-${episodeTag.season}") }
                                    withStyle(
                                        SpanStyle(
                                            color = tagColor.copy(alpha = 0.45f),
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    ) { append("/") }
                                    withStyle(
                                        SpanStyle(
                                            color = tagColor,
                                            fontWeight = FontWeight.ExtraBold,
                                        ),
                                    ) { append("E-${episodeTag.number}") }
                                }
                                Text(
                                    text = compoundTag,
                                    fontFamily = RobotoFamily,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    letterSpacing = 0.3.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            } else {
                                Text(
                                    text = "EP ${episodeTag?.number ?: epNumText}",
                                    fontFamily = RobotoFamily,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                        // Phase 2d: watch progress bar at the bottom of the thumbnail (like YouTube).
                        // Only shows when the episode is partially watched (not when fully watched —
                        // fully watched is indicated by grayscale + alpha fade instead).
                        // D-554: the bar is a USER TOGGLE now.
                        if (style.showWatchProgress && progressFraction > 0f && !isWatched) {
                            LinearProgressIndicator(
                                progress = { progressFraction },
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .height(3.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                } else {
                    // No thumbnail — circle episode number (40dp disc)
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            // Task 56: the disc shows the same number the EP tag
                            // would — the per-flavor ordinal for CS sub/dub rows.
                            Text(
                                text = episodeTag?.number ?: epNumText,
                                fontFamily = RobotoFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                }

                // ── Right column: title (top) + date/audio pills (bottom) ──
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Title — with subtle background surface
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = displayTitle,
                            fontFamily = RobotoFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = LocalCardHeadingColor.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    // Date + Audio pills + (download button if no synopsis)
                    // D-554: the row shows when ANY resident survives the gates —
                    // the algebra lives in pillsRowVisible (unit-tested) and is
                    // mirrored here.
                    if (pillsRowVisible(style, dateText != null, audioLabels.isNotEmpty(), showSynopsisSection)) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            // Date pill
                            if (showDate) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                ) {
                                    Text(
                                        text = dateText,
                                        fontFamily = RobotoFamily,
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = LocalCardDescriptionColor.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                            }
                            // Audio pills — SUB/DUB/HSUB with dot separators
                            if (showAudio) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        audioLabels.forEachIndexed { idx, label ->
                                            if (idx > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(3.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.onSurfaceVariant),
                                                )
                                            }
                                            Text(
                                                text = label,
                                                fontFamily = RobotoFamily,
                                                fontSize = 10.sp,
                                                lineHeight = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = LocalCardDescriptionColor.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                softWrap = false,
                                            )
                                        }
                                    }
                                }
                            }
                            // Download button — shown here (next to pills) when no synopsis.
                            // D.6: replaced the placeholder toast button with the state-driven
                            // EpisodeDownloadControl (7 states + AnimatedContent transitions).
                            // D-554: it moves here whenever the synopsis section is NOT
                            // rendered (no synopsis, toggled off, or a style without it),
                            // and the download toggle can hide it entirely.
                            if (style.showDownloadControl && !showSynopsisSection) {
                                Spacer(Modifier.weight(1f))
                                EpisodeDownloadControl(
                                    state = downloadState,
                                    onDownload = onDownload,
                                    onPause = onPause,
                                    onResume = onResume,
                                    onCancel = onCancel,
                                    onRetry = onRetry,
                                    onDelete = onDelete,
                                    onPlayDownloaded = onPlayDownloaded,
                                )
                            }
                        }
                    }
                }

                // (Download button moved to the synopsis section below, or to
                //  the date/audio pills row if no synopsis)
            }

            // ══ BOTTOM SECTION: Synopsis (below thumbnail + title row) + download button ══
            // If there IS a synopsis: download button goes at the bottom-right of synopsis.
            // If there is NO synopsis: download button goes at the right of the date/audio pills row.
            // D-554: the section is a USER TOGGLE within DETAILED — and the style
            // frame (COMPACT/MINIMAL) never renders it.
            if (showSynopsisSection) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = description,
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = LocalCardDescriptionColor.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                    if (style.showDownloadControl) {
                        Spacer(Modifier.width(8.dp))
                        EpisodeDownloadControl(
                            state = downloadState,
                            onDownload = onDownload,
                            onPause = onPause,
                            onResume = onResume,
                            onCancel = onCancel,
                            onRetry = onRetry,
                            onDelete = onDelete,
                            onPlayDownloaded = onPlayDownloaded,
                        )
                    }
                }
            } else {
                // No synopsis — move download button up to the date/audio pills row.
                // Show it at the end of the top section's right column.
                // (Already rendered inline in the date/audio pills Row above if no synopsis.)
            }
        }
        // D-211: full-width download progress bar overlay at the bottom of the card.
        // Spans the ENTIRE card width (under the buttons too). Doesn't add height —
        // it's an overlay on the Box, aligned BottomCenter. Only shows when downloading.
        // D-554: NOT covered by the watch-progress toggle — transient state feedback.
        if (downloadState is EpisodeDownloadState.Downloading) {
            LinearProgressIndicator(
                progress = { (downloadState.progress / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            )
        }
    }
    } // close SwipeToToggleWatched (the Phase WP gesture — D-555 extraction)
}
