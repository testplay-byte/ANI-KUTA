package com.confused.anikuta.feature.animedetails

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.common.HapticHelper
import com.confused.anikuta.core.designsystem.theme.LocalCardDescriptionColor
import com.confused.anikuta.core.designsystem.theme.LocalCardHeadingColor
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import eu.kanade.tachiyomi.animesource.model.SEpisode
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * D-554: the episode-row LAYOUT styles. Rendered by [EpisodeRow] — the SAME
 * renderer the real details-page list AND the Appearance → "Episode list"
 * settings page's live preview use (the D-481 doctrine: what you tune is
 * exactly what you get).
 *
 * Each style is a CURATED preset (the D-523 lesson — layouts live in code,
 * not in a free-form editor):
 *
 * - [DETAILED] (default = the look that has always been): the 120×68dp
 *     thumbnail (when available), the date/audio pills, the up-to-2-line
 *     synopsis, the download control.
 * - [COMPACT]: the smaller 84×48dp thumbnail; the synopsis NEVER renders
 *     (that is the style's point); pills + download control stay.
 * - [MINIMAL]: no thumbnail at all (the number-disc path), no date pill,
 *     no synopsis — number + title + audio pills + download.
 *
 * The element toggles in [EpisodeListDisplayStyle] are honored WITHIN the
 * style's frame: a style that never renders a section cannot be talked
 * into rendering it.
 */
enum class EpisodeListRowStyle {
    DETAILED,
    COMPACT,
    MINIMAL;

    companion object {
        /**
         * D-529 lesson applied: seeding goes through THIS lenient lookup so
         * the settings page (and any future caller) always highlights the
         * SAME style the renderer will actually draw — a raw key comparison
         * against a legacy/unknown pref value would highlight DETAILED while
         * the fallback style renders.
         */
        fun fromKey(key: String?): EpisodeListRowStyle = when (key?.trim()?.uppercase()) {
            "COMPACT" -> COMPACT
            "MINIMAL" -> MINIMAL
            else -> DETAILED
        }
    }
}

/**
 * D-554: the user-tunable row-appearance knobs. Every default equals
 * today's behavior, so the zero-prefs experience is byte-identical to the
 * pre-D-554 list.
 *
 * Carried from [com.confused.anikuta.core.preferences.EpisodeListPreferences]
 * (collected ONCE per screen — ONE subscription set for the whole list, not
 * 7×N rows) into each [EpisodeRow].
 */
data class EpisodeListDisplayStyle(
    val rowStyle: EpisodeListRowStyle = EpisodeListRowStyle.DETAILED,
    /** The two-line synopsis (honored in DETAILED only). */
    val showSynopsis: Boolean = true,
    /** The release-date pill (honored in DETAILED + COMPACT). */
    val showDatePill: Boolean = true,
    /** The SUB · DUB · HSUB availability pills. */
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
 */
fun pillsRowVisible(
    style: EpisodeListDisplayStyle,
    hasDate: Boolean,
    hasAudio: Boolean,
    showsSynopsis: Boolean,
): Boolean {
    val showDate = style.showDatePill &&
        style.rowStyle != EpisodeListRowStyle.MINIMAL && hasDate
    val showAudio = style.showAudioPills && hasAudio
    return showDate || showAudio || (style.showDownloadControl && !showsSynopsis)
}

/** D-317: contextual episode tag (per-season number / "S-n/E-m" compound). */
data class EpisodeTag(
    val season: Int?,
    val number: String,
)

// ── Audio availability parsing (ported from old project) ──

data class AudioAvailability(
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

fun parseAudioAvailability(scanlator: String?, episodeName: String): AudioAvailability {
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
 * D-554 appearance knobs with defaults == the pre-D-554 behavior — the
 * zero-prefs experience is unchanged.
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
    // D-306: extension-first resolution — the extension's own title/description/
    // thumbnail WIN; provider metadata (AniZip/Jikan/Kitsu/AniList) fills the gaps.
    // Shared rules live in EpisodeDisplayResolver (single source of truth).
    val displayTitle = remember(episode, metadata) {
        EpisodeDisplayResolver.title(episode, metadata)
    }
    val description = remember(episode, metadata) {
        EpisodeDisplayResolver.description(episode, metadata)
    }
    // D-230: Thumbnail fallback is now configurable via EpisodeListPreferences.
    // - "COVER" → fall back to the anime's cover image (default).
    // - "NONE" → no image (bare placeholder).
    val episodeListPrefs = koinInject<com.confused.anikuta.core.preferences.EpisodeListPreferences>()
    val thumbnailFallback by episodeListPrefs.thumbnailFallback.changes.collectAsState(
        initial = episodeListPrefs.thumbnailFallback.get(),
    )
    val thumbnailUrl = when {
        // D-306: extension-provided preview_url first.
        !episode.preview_url.isNullOrBlank() -> episode.preview_url
        !metadata?.thumbnailUrl.isNullOrBlank() -> metadata?.thumbnailUrl
        thumbnailFallback == "COVER" -> fallbackCoverUrl
        else -> null
    }
    val epNumText = formatEpisodeNumber(episode.episode_number)
    val dateText = remember(episode, metadata) {
        val airDate = metadata?.airDate
        when {
            airDate != null && airDate > 0 -> formatDate(airDate)
            episode.date_upload > 0 -> formatDate(episode.date_upload)
            else -> null
        }
    }
    // Audio availability — parsed from scanlator + episode name (like old project).
    val audio = remember(episode) { parseAudioAvailability(episode.scanlator, episode.name) }

    // ── D-554: the style gates (computed ONCE, then the body reads them) ──
    // MINIMAL is DEFINED by the thumbnail's absence — force the number-disc
    // path. COMPACT shrinks the thumbnail. The synopsis is a DETAILED-only
    // section (its absence defines the other styles); the user's synopsis
    // toggle is honored WITHIN DETAILED.
    val minimal = style.rowStyle == EpisodeListRowStyle.MINIMAL
    val compact = style.rowStyle == EpisodeListRowStyle.COMPACT
    val effectiveThumbnailUrl = if (minimal) null else thumbnailUrl
    val thumbWidth = if (compact) 84.dp else 120.dp
    val thumbHeight = if (compact) 48.dp else 68.dp
    val showSynopsisSection = style.rowStyle == EpisodeListRowStyle.DETAILED &&
        style.showSynopsis && !description.isNullOrBlank()
    val showDate = style.showDatePill && !minimal && dateText != null
    val showAudio = style.showAudioPills && audio.hasAny

    // ── Phase WP: swipe-to-toggle watched state ──
    // Custom pointerInput (not SwipeToDismissBox — that's for dismiss, not toggle).
    // Swipe right past threshold → toggle. Spring back smoothly on release.
    // Bidirectional: swipe right to toggle, swipe left to cancel a rightward swipe.
    val swipeOffset = remember { androidx.compose.animation.core.Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthPx = with(LocalDensity.current) {
        configuration.screenWidthDp.dp.toPx()
    }
    val swipeThresholdPx = screenWidthPx * 0.35f // 35% of screen width

    // Track whether the threshold was crossed DURING the drag (for haptic feedback).
    var thresholdCrossed by remember { androidx.compose.runtime.mutableStateOf(false) }

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

    // ── Card ── (wrapped in a Box for the swipe gesture + background icon)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha },
    ) {
        // Background icon — fades in linearly as the user swipes, full opacity past
        // the threshold. matchParentSize (BoxScope) sizes the background to the card's
        // footprint: the wrapper Box wraps its content height (no bounded height), so
        // fillMaxSize() resolves to 0 height here — that was the "background gone" bug.
        // matchParentSize measures the card first, then fills the same space behind it.
        val swipeProgress = (kotlin.math.abs(swipeOffset.value) / swipeThresholdPx).coerceIn(0f, 1f)
        val iconAlpha = if (thresholdCrossed) 1f else swipeProgress
        Surface(
            color = if (isWatched) MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
            else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { this.alpha = iconAlpha },
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                contentAlignment = if (swipeOffset.value > 0) androidx.compose.ui.Alignment.CenterStart
                else androidx.compose.ui.Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = if (isWatched) Icons.Filled.VisibilityOff
                    else Icons.Filled.CheckCircle,
                    contentDescription = if (isWatched) "Mark as unwatched" else "Mark as watched",
                    tint = if (isWatched) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
            }
        }

        // The actual card — opaque (NOT transparent), translates with the swipe.
        // D-211: changed from Surface to Box so we can overlay a full-width download
        // progress bar at the bottom (under the buttons, spanning the entire card width).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(swipeOffset.value.toInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { thresholdCrossed = false },
                        onDragEnd = {
                            // If past threshold → toggle + haptic. Else smooth spring back.
                            if (kotlin.math.abs(swipeOffset.value) > swipeThresholdPx) {
                                HapticHelper.releaseConfirm(context)
                                onToggleWatched()
                            }
                            coroutineScope.launch {
                                swipeOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(
                                        durationMillis = 300,
                                        easing = FastOutSlowInEasing,
                                    ),
                                )
                            }
                            thresholdCrossed = false
                        },
                    ) { _, dragAmount ->
                        val newValue = (swipeOffset.value + dragAmount).coerceIn(
                            minimumValue = -swipeThresholdPx * 1.5f, // allow left cancel
                            maximumValue = swipeThresholdPx * 1.5f,   // allow right toggle
                        )
                        coroutineScope.launch {
                            swipeOffset.snapTo(newValue)
                        }
                        // Haptic feedback when crossing the threshold for the first time.
                        if (!thresholdCrossed && kotlin.math.abs(newValue) > swipeThresholdPx) {
                            thresholdCrossed = true
                            HapticHelper.stageCross(context)
                        } else if (thresholdCrossed && kotlin.math.abs(newValue) <= swipeThresholdPx) {
                            thresholdCrossed = false
                        }
                    }
                }
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
                // D-554: MINIMAL forces the number-disc path (effectiveThumbnailUrl);
                // COMPACT renders the same box at 84×48dp.
                if (effectiveThumbnailUrl != null) {
                    Box(
                        modifier = Modifier.size(width = thumbWidth, height = thumbHeight),
                    ) {
                        AsyncImage(
                            model = effectiveThumbnailUrl,
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
                    if (pillsRowVisible(style, dateText != null, audio.hasAny, showSynopsisSection)) {
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
                                        audio.labels.forEachIndexed { idx, label ->
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
    } // close the swipe wrapper Box (Phase WP)
}
