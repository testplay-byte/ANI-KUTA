package com.confused.anikuta.feature.cswatch.impl

import android.graphics.Typeface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.csplayer.CsCue
import com.confused.anikuta.core.csplayer.CsSubtitleGeometry
import com.confused.anikuta.core.csplayer.CsSubtitleStyle

/**
 * Task 57 (round 17 — the overlay subtitle system): renders the ACTIVE cue of
 * a fetched provider subtitle on top of the video, in OUR own Compose renderer
 * (the user's directive: "use our own subtitle displaying technique, overlay on
 * top of it… like how it is being handled in the old one").
 *
 * Sits between the PlayerView and the player controls in both display modes;
 * sized by the box it is placed in (the 16:9 player box / the fullscreen
 * surface) so the text fraction matches the Media3 view's semantics:
 *
 *  - font size = box height × 0.0533 × (fontSize / 55) × fontScale
 *    (byte parity with [CsPlayerEngine.applySubtitleStyle]'s mapping — see
 *    [CsSubtitleGeometry.fontFraction]);
 *  - position = MPV sub-pos (0..100, 100 = flush bottom) → the same
 *    ((100 - pos) / 100) × 0.12 bottom-padding fraction the engine uses;
 *  - delay shifts cue visibility (positive = later — MPV sub-delay).
 *
 * Task 58 (round 18 — the formatting/accuracy round, the v0.4.5 device
 * findings "border size is not shown properly" + "the background, like
 * borders, does not show in the correct positions"): MPV unit parity for the
 * border/shadow widths (see [CsSubtitleGeometry]).
 *
 * **Task 59 (round 19 — the accuracy round 2, the v0.4.6 device findings
 * "way too much spacing between the lines" / "lines overlapping" / "the
 * border was showing somewhere else from the font — subtitle at top, border
 * at bottom") — THE LAYOUT IS ONE PASS NOW:**
 *
 *  - v0.4.6 rendered each cue LINE as its own `Text` inside a `Column`, so
 *    every line carried a FULL platform line box (ascent + descent + leading)
 *    — the inter-line gap was double-ledged and uncontrolled ("way too much
 *    spacing" at small sizes), while the stroke passes (which extend beyond
 *    the glyph bounds) poked into the next line's box unopposed at large
 *    sizes ("no line spacing, like they were overlapping").
 *  - Round 19 renders the WHOLE cue as ONE multi-line `Text` — the platform's
 *    natural line spacing (what Media3's own SubtitleView and every normal
 *    text renderer use): ONE leading per line break, a CONSTANT fraction of
 *    the font at every size, identical in both display modes.
 *  - Every decoration pass — the per-line background boxes (ASS
 *    BorderStyle=3 / MPV sub-back-color), the shadow stroke and the border
 *    stroke — is drawn from the SAME [TextLayoutResult] the fill pass
 *    measured (captured via `onTextLayout`, rendered through
 *    [drawText]/`drawRect` in a `drawBehind` scope UNDER the fill). The
 *    passes share one layout object, so a stroke can never detach from its
 *    glyphs ("the border was showing somewhere else from the font" is
 *    structurally impossible now); the fill of line N+1 covers line N's
 *    stroke bleed, so the visible inter-line gap is exactly the line spacing.
 *  - The SHADOW stays MPV's semantics: drawn IN ADDITION to the border,
 *    offset DOWN by the shadow width, border-colored at 75% alpha (the
 *    v0.4.6 offset stroke was exactly what read as "a border showing at the
 *    bottom" — it now provably renders at the glyphs' position + the offset).
 *  - The cue block wraps at [CsSubtitleGeometry.HORIZONTAL_INSET_FRACTION]
 *    from each side — long lines never touch the screen edge.
 *
 * The active cue lookup is O(n) over sorted cues at the 100 ms ticker cadence
 * (episode files carry hundreds, not millions, of cues).
 *
 * Style reactivity: the style rides the caller's Compose state (the watch
 * screen's hoisted live snapshot) — every slider change recomposes this
 * overlay immediately, PAUSED or playing.
 */
@Composable
internal fun CsSubtitleOverlay(
    cues: List<CsCue>,
    positionMs: Long,
    style: CsSubtitleStyle,
    modifier: Modifier = Modifier,
) {
    if (cues.isEmpty()) return
    val effectiveMs = positionMs + style.delayMs
    val active = cues.firstOrNull { effectiveMs >= it.startMs && effectiveMs < it.endMs }
        ?: return

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val density = LocalDensity.current
        // Font size: fractional of the overlay height — the Media3 parity math.
        val fontSizePx = constraints.maxHeight *
            CsSubtitleGeometry.fontFraction(style.fontSize, style.fontScale)
        val fontSizeSp = with(density) { fontSizePx.coerceAtLeast(8f).toSp() }
        // MPV unit parity (linear, generous ceiling) — see the geometry object.
        val borderWidthPx = fontSizePx * CsSubtitleGeometry.borderWidthFraction(style.borderSize)
        // The shadow follows the border's MPV units, drawn IN ADDITION.
        val shadowPx = fontSizePx * CsSubtitleGeometry.shadowFraction(style.shadowOffset)
        // Position: MPV sub-pos → bottom padding fraction (the engine mapping).
        val bottomFraction = CsSubtitleGeometry.bottomPaddingFraction(style.position)
        val bottomPadding = with(density) { (constraints.maxHeight * bottomFraction).toDp() }
        // Task 59: the cue block's wrap margin — 4% of the overlay width/side.
        val horizontalInset = with(density) {
            CsSubtitleGeometry.horizontalInsetPx(constraints.maxWidth).toDp()
        }

        val typeface = Typeface.create(
            familyOf(style.fontFamilyName),
            when {
                style.bold && style.italic -> Typeface.BOLD_ITALIC
                style.bold -> Typeface.BOLD
                style.italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            },
        )
        val textColor = Color(style.textColor)
        val borderColor = Color(style.borderColor)
        val bgColor = Color(style.backgroundColor)
        val hasBg = style.backgroundColor ushr 24 != 0
        val hasBorder = style.borderSize > 0
        val hasShadow = style.shadowOffset > 0

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = bottomPadding),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Task 59: THE layout the decoration passes share with the fill —
            // assigned by the fill Text's onTextLayout (fires on every
            // measure: cue change, style change, size change). Reading it
            // inside drawBehind re-executes the pass when a new layout lands.
            var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

            Box(
                modifier = Modifier
                    .padding(horizontal = horizontalInset)
                    // All decoration passes, drawn UNDER the fill (drawBehind
                    // runs before the content) from the SAME layout the fill
                    // renders — the alignment is structural, not simulated.
                    .drawBehind {
                        val layout = textLayout ?: return@drawBehind
                        // 1) Per-line BACK-COLOR boxes (ASS BorderStyle=3):
                        //    each line's glyph bounds padded by the border
                        //    width — short lines get short boxes, positions
                        //    guaranteed correct (they ARE the fill's lines).
                        if (hasBg) {
                            for (line in 0 until layout.lineCount) {
                                val left = layout.getLineLeft(line) - borderWidthPx
                                val right = layout.getLineRight(line) + borderWidthPx
                                val top = layout.getLineTop(line) - borderWidthPx
                                val bottom = layout.getLineBottom(line) + borderWidthPx
                                drawRect(
                                    color = bgColor,
                                    topLeft = Offset(left, top),
                                    size = Size(right - left, bottom - top),
                                )
                            }
                        }
                        // 2) The SHADOW pass — MPV sub-shadow: border-colored,
                        //    75% alpha, offset DOWN by the shadow width, drawn
                        //    in ADDITION to the border, UNDER everything.
                        if (hasShadow) {
                            drawText(
                                textLayoutResult = layout,
                                color = borderColor.copy(alpha = 0.75f),
                                topLeft = Offset(0f, shadowPx),
                                drawStyle = Stroke(width = shadowPx * 1.5f),
                            )
                        }
                        // 3) The BORDER stroke pass — the outline exactly on
                        //    the fill's glyphs (same layout object).
                        if (hasBorder) {
                            drawText(
                                textLayoutResult = layout,
                                color = borderColor,
                                drawStyle = Stroke(width = borderWidthPx),
                            )
                        }
                    },
            ) {
                // 4) THE fill pass — the whole multi-line cue as ONE Text
                //    (natural line spacing; measures the box; publishes the
                //    layout the passes above render). No maxLines truncation
                //    (MPV never truncates; long cues wrap at the inset).
                //
                //    Task 60 (round 20 — the v0.4.7 line-gap findings): the
                //    lineHeight is EXPLICIT — fontSizeSp × LINE_HEIGHT_RATIO —
                //    with a Proportional/None lineHeightStyle. The round-19
                //    Text overrode only `fontSize`, so the AMBIENT
                //    LocalTextStyle's fixed 24sp lineHeight (Material3's
                //    bodyLarge) leaked in: at 0.5× scale the 24sp line box was
                //    ~2× the glyphs ("way too much gap"), at 2×+ the glyphs
                //    outgrew it ("overlapping, distorted"). One explicit,
                //    font-proportional line height keeps the inter-line gap a
                //    CONSTANT ~20% of the glyph height at every size, scale,
                //    and display mode — and immune to any ambient style.
                //    Proportional alignment distributes the extra space by
                //    ascent/descent (leading-like), Trim.None keeps the block's
                //    outer padding so the decoration passes' line boxes stay
                //    symmetric top and bottom.
                Text(
                    text = active.text,
                    color = textColor,
                    fontSize = fontSizeSp,
                    lineHeight = CsSubtitleGeometry.lineHeightSp(fontSizeSp.value).sp,
                    // The lineHeightStyle rides the STYLE param (Text has no
                    // lineHeightStyle parameter) — a BARE TextStyle, not a
                    // merge over LocalTextStyle: the overlay's line metric is
                    // fully self-contained (see the Task-60 note above).
                    style = TextStyle(
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Proportional,
                            trim = LineHeightStyle.Trim.None,
                        ),
                    ),
                    fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
                    fontFamily = FontFamily(typeface),
                    textAlign = TextAlign.Center,
                    onTextLayout = { textLayout = it },
                )
            }
        }
    }
}

/** The MPV font-name → Android typeface mapping (unknown names → sans-serif). */
private fun familyOf(name: String?): Typeface = when (name?.lowercase()) {
    "serif", "times new roman", "georgia" -> Typeface.SERIF
    "monospace", "mono", "courier new", "consolas" -> Typeface.MONOSPACE
    else -> Typeface.SANS_SERIF
}
