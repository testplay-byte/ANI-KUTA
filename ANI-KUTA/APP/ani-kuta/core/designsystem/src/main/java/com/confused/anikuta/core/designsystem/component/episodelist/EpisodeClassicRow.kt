package com.confused.anikuta.core.designsystem.component.episodelist

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.designsystem.theme.RobotoFamily

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 111 (D-727): THE CLASSIC ROW, ONE TRUTH — the v1.1.67 device
//  round: "In the player page on the detailed view, currently the things
//  are apparently not handled that well … the classic view on the details
//  page and the detailed view on the player page is most definitely not
//  perfect. They are different. So what I want you to do is that I want
//  you to utilize the exact same classic layout of the details page on the
//  player page too."
//
//  This file IS the details page's CLASSIC row, extracted verbatim from
//  EpisodeRow.kt into the shared episodelist home — ONE anatomy both pages
//  render through (the two-grids-are-one doctrine, extended to the row):
//
//    • the thumbnail (120×68) renders IMAGERY ONLY — no EP overlay (the
//      D-557 verdict: "the image gets covered way too much") — with the
//      rounded INSET watch-progress pill; the fallback is the 40dp number
//      DISC (the player's old number-box tile is retired).
//    • the right column (SpaceBetween): the quiet EPISODE NUMBER label
//      (each page's own — the details' compound-capable EpisodeNumberLabel,
//      the player's shared EpisodeNumberLabelPlain), the one-line title
//      plate (ONLY while the synopsis renders below — round 112), and the
//      META LINE ([EpisodeMetaLine] — the date + audio capsules, ONE line,
//      never a break) as PURE meta: the download control NEVER joins this
//      row (the v1.1.68 squeeze clipped the trailing DUB chip).
//    • the BOTTOM section, always rendered: the SYNOPSIS plate when the
//      synopsis is on; otherwise the TITLE relocated there (the round-112
//      arrangement — "instead of the synopsis, it would show the title
//      there in one single line") — with the download control at its end,
//      exactly as it is in both cases.
//    • the full-width 3dp download-progress bar across the card's bottom.
//    • the watched treatment: the WHOLE card's animated 0.5 fade + the
//      thumbnail's grayscale (the details' own treatment).
//
//  The player's own chrome survives as PARAMETERS: [isCurrent] renders the
//  primary-tinted surface + the 2dp ring (the player's identity anchor),
//  and [overlay] carries the arrival pulse. The pages inject their own
//  [downloadControl] (the details' EpisodeDownloadControl, the player's
//  badge) — ONE geometry, two controls, zero drift.
// ════════════════════════════════════════════════════════════════════════════

/**
 * THE CLASSIC ROW — the details page's CLASSIC anatomy, shared verbatim by
 * the player page's classic layout. All text is PRE-GATED by the caller
 * (the date/audio knobs, the synopsis knob); this row renders what it is
 * given and nothing more.
 *
 * @param thumbnailUrl the episode's thumbnail (null = the 40dp number disc).
 * @param fallbackNumberText the disc's numeral (the per-flavor ordinal or
 *   the raw number — each page's own resolution).
 * @param numberLabel the quiet EP-label line above the title (each page's
 *   own — the details' compound-capable label, the player's plain one).
 * @param dateText the ALREADY-GATED release date (null = no date chip).
 * @param audioTags the ALREADY-GATED audio tags (the tag model's output).
 * @param synopsis the ALREADY-GATED synopsis (null = the TITLE relocates
 *   to the bottom section's plate, one single line — the round-112
 *   arrangement; the download control stays at that section's end).
 * @param pillsRowVisible the caller's meta-line row gate (the details'
 *   unit-locked pillsRowVisible algebra; the player's own) — PURE meta:
 *   date or audio, never the control.
 * @param downloadControl the page's own download control, rendered at the
 *   BOTTOM section's end — beside the synopsis plate when the synopsis is
 *   on, beside the relocated title plate when it is off (round 112: never
 *   in the meta line's row).
 * @param showDownloadControl gates both control placements.
 * @param downloadProgress the 0..1 download fraction (null = no bar).
 * @param dimWatched the ALREADY-FOLDED dim gate (the player folds
 *   !isCurrent into it; the details passes its knob as-is).
 * @param isCurrent the player's current-episode chrome (tint + ring).
 * @param overlay the page's own overlay inside the card (the arrival
 *   pulse); drawn ABOVE the content, BELOW nothing.
 * @param titleColor the title's color override (the details'
 *   LocalCardHeadingColor resolution; [Color.Unspecified] = onSurface).
 * @param descriptionColor the synopsis' color override (the details'
 *   LocalCardDescriptionColor resolution; [Color.Unspecified] =
 *   onSurfaceVariant).
 */
@Composable
fun EpisodeClassicRow(
    thumbnailUrl: String?,
    fallbackNumberText: String,
    numberLabel: @Composable () -> Unit,
    displayTitle: String,
    dateText: String?,
    audioTags: List<String>,
    synopsis: String?,
    pillsRowVisible: Boolean,
    showWatchProgress: Boolean,
    progressFraction: Float,
    downloadControl: @Composable () -> Unit,
    showDownloadControl: Boolean,
    downloadProgress: Float?,
    isWatched: Boolean,
    dimWatched: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    overlay: @Composable BoxScope.() -> Unit = {},
    titleColor: Color = Color.Unspecified,
    descriptionColor: Color = Color.Unspecified,
) {
    // ── The watched treatment (the details' own): the WHOLE card's
    //    animated 0.5 fade + the thumbnail's grayscale filter. ──
    val targetAlpha = if (isWatched && dimWatched) 0.5f else 1.0f
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        label = "classic_watched_alpha",
    )
    val colorFilter = remember(isWatched, dimWatched) {
        if (isWatched && dimWatched) {
            ColorFilter.colorMatrix(
                androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                )),
            )
        } else null
    }
    val resolvedTitleColor = if (titleColor == Color.Unspecified) {
        MaterialTheme.colorScheme.onSurface
    } else titleColor
    val resolvedDescriptionColor = if (descriptionColor == Color.Unspecified) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else descriptionColor

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha },
    ) {
        Surface(
            color = if (isCurrent) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            shape = RoundedCornerShape(12.dp),
            border = if (isCurrent) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        ) {
            Box {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                ) {
                    // ══ TOP: thumbnail (left) + title/meta (right) ══
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        // ── The thumbnail — imagery ONLY (D-557: the EP pill
                        //    is GONE from the image; the number lives in the
                        //    label line) + the rounded inset watch pill. ──
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
                                    colorFilter = colorFilter,
                                )
                                if (showWatchProgress && progressFraction > 0f && !isWatched) {
                                    EpisodeWatchProgressBar(
                                        fraction = progressFraction,
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(horizontal = 6.dp, vertical = 5.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                        } else {
                            // No thumbnail — the 40dp number DISC.
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(40.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = fallbackNumberText,
                                        fontFamily = RobotoFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                        }

                        // ── Right column: the number label + the title +
                        //    the meta line (top-anchored content, the
                        //    SpaceBetween rhythm the details row owns). ──
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            numberLabel()
                            // The title plate — one line, ellipsized. It
                            // lives here ONLY while the synopsis section
                            // renders below; with the synopsis off, the
                            // round-112 arrangement relocates it to the
                            // bottom section (the meta line keeps the
                            // right column's full width for its chips).
                            if (synopsis != null) {
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
                                        color = resolvedTitleColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                            // The meta line — PURE meta (round 112): the
                            // download control NEVER joins this row. On
                            // v1.1.68 the control squeezed the line's width
                            // budget past the ladder's core floor and the
                            // trailing DUB chip clipped — the user's report:
                            // "when I hit the synopsis, then the dub episode
                            // was not being shown".
                            if (pillsRowVisible) {
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    EpisodeMetaLine(
                                        dateText = dateText,
                                        audioTags = audioTags,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }

                    // ══ BOTTOM: the wide section — ALWAYS rendered (round
                    //    112): the SYNOPSIS plate when the synopsis is on;
                    //    otherwise the TITLE relocated here — "instead of
                    //    the synopsis, it would show the title there in one
                    //    single line" — with the download control at its
                    //    end, exactly as it is. ══
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        if (synopsis != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = synopsis,
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    lineHeight = 15.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = resolvedDescriptionColor,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                )
                            }
                        } else {
                            // The relocated title plate — the SAME plate
                            // styling as the right column's, one single line.
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = displayTitle,
                                    fontFamily = RobotoFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = resolvedTitleColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                        if (showDownloadControl) {
                            Spacer(Modifier.width(8.dp))
                            downloadControl()
                        }
                    }
                }

                // ── D-211: the full-width download-progress bar — spans the
                //    ENTIRE card width at the bottom edge, over the content's
                //    Box (transient state feedback; NOT covered by the watch
                //    progress knob). ──
                if (downloadProgress != null) {
                    LinearProgressIndicator(
                        progress = { downloadProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    )
                }

                // The page's own overlay (the player's arrival pulse).
                overlay()
            }
        }
    }
}
