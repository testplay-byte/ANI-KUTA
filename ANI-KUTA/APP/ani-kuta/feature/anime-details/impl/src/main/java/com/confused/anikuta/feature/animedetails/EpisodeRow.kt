package com.confused.anikuta.feature.animedetails

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
// ROUND 109 (D-719): the shared meta pieces (chips + progress pill + the
// short date formatter) moved to :core:designsystem's episodelist package —
// the classic row's calls are unchanged, only the import source moved.
// ROUND 111 (D-727): the classic row's own ANATOMY moved there too
// (EpisodeClassicRow) — one implementation, both pages.
import com.confused.anikuta.core.designsystem.component.episodelist.EpisodeClassicRow
import com.confused.anikuta.core.designsystem.component.episodelist.formatShortDate
import com.confused.anikuta.core.designsystem.theme.LocalCardDescriptionColor
import com.confused.anikuta.core.designsystem.theme.LocalCardHeadingColor
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
    /**
     * D-558: CINEMA's ghost number in the banner's TOP-START corner (false =
     * the historical top-end; the pref default keeps today's behavior).
     */
    val cinemaNumberAtTopStart: Boolean = false,
    /**
     * D-558: CINEMA's ghost number rendered through a frosted-glass plate —
     * a translucent material behind the number AND a frost veil on top of it
     * (false = the historical solid themed number).
     */
    val cinemaNumberFrosted: Boolean = false,
    /**
     * D-558: the big centered circular check on watched CINEMA banners
     * (default OFF per the user's spec — the grayscale/dim treatment stays
     * regardless; only the badge is optional).
     */
    val cinemaWatchedCheckBadge: Boolean = false,
    /**
     * ROUND 108 (D-713): the GRID's title-line mode — "TWO" (the default —
     * the grid's historical two-line title), "ONE" (a single ellipsized
     * line), "OFF" (no title line). Resolved through
     * [com.confused.anikuta.core.common.GridTitleMode.fromKey] by whichever
     * screen builds this style; independently of the mode, the line only
     * ever renders a REAL English-readable title
     * ([com.confused.anikuta.core.common.gridShowableTitle] — the "not
     * available in English / only shows the episode number" gate).
     */
    val gridTitleMode: com.confused.anikuta.core.common.GridTitleMode =
        com.confused.anikuta.core.common.GridTitleMode.TWO_LINES,
    /**
     * ROUND 110 (D-723): the GRID's number-label placement — UNDER_THUMB
     * (the default — the label's own line under the plate) or BESIDE_DETAILS
     * (the label rides the title's line). Resolved through
     * [com.confused.anikuta.core.common.GridNumberPosition.fromKey] by
     * whichever screen builds this style.
     */
    val gridNumberPosition: com.confused.anikuta.core.common.GridNumberPosition =
        com.confused.anikuta.core.common.GridNumberPosition.UNDER_THUMB,
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
    /**
     * D-557: the settings preview's state-cycling demo tap — non-null makes
     * the download control/badge ONE tap target (see
     * EpisodeDownloadControl.previewTapAll). Production callers leave null.
     */
    val previewTapAll: (() -> Unit)? = null,
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
    previewTapAll: (() -> Unit)? = null,
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
        previewTapAll = previewTapAll,
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
            previewTapAll = previewTapAll,
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
    // D-557: the settings preview's demo tap (see EpisodeListEntry).
    previewTapAll: (() -> Unit)? = null,
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

    // ── ROUND 111 (D-727): THE CLASSIC ROW, ONE TRUTH — the body is the
    //    shared EpisodeClassicRow now (the verbatim extraction of this very
    //    anatomy into :core:designsystem), so the player page's classic
    //    layout renders the EXACT same row. This adapter keeps what is the
    //    details page's own: the display resolution, the gates, the
    //    compound-capable number label, the EpisodeDownloadControl, and the
    //    swipe-to-toggle wrapper (the animated watched fade + the grayscale
    //    moved INTO the shared row). ──
    SwipeToToggleWatched(
        isWatched = isWatched,
        onToggleWatched = onToggleWatched,
        modifier = Modifier.fillMaxWidth(),
    ) {
        EpisodeClassicRow(
            thumbnailUrl = thumbnailUrl,
            fallbackNumberText = episodeTag?.number ?: epNumText,
            numberLabel = {
                EpisodeNumberLabel(
                    episodeTag = episodeTag,
                    epNumText = epNumText,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            },
            displayTitle = displayTitle,
            dateText = if (showDate) dateText else null,
            audioTags = if (showAudio) audioLabels else emptyList(),
            synopsis = if (showSynopsisSection) description else null,
            pillsRowVisible = pillsRowVisible(
                style = style,
                hasDate = dateText != null,
                hasAudio = audioLabels.isNotEmpty(),
                showsSynopsis = showSynopsisSection,
            ),
            showWatchProgress = style.showWatchProgress,
            progressFraction = progressFraction,
            downloadControl = {
                EpisodeDownloadControl(
                    state = downloadState,
                    onDownload = onDownload,
                    onPause = onPause,
                    onResume = onResume,
                    onCancel = onCancel,
                    onRetry = onRetry,
                    onDelete = onDelete,
                    onPlayDownloaded = onPlayDownloaded,
                    previewTapAll = previewTapAll,
                )
            },
            showDownloadControl = style.showDownloadControl,
            downloadProgress = (downloadState as? EpisodeDownloadState.Downloading)
                ?.progress?.div(100f),
            isWatched = isWatched,
            dimWatched = style.dimWatched,
            onClick = onClick,
            titleColor = LocalCardHeadingColor.current,
            descriptionColor = LocalCardDescriptionColor.current,
        )
    }
}

