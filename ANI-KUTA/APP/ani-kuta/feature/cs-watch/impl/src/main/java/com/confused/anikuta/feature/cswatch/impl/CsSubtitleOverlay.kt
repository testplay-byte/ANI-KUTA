package com.confused.anikuta.feature.cswatch.impl

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
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
 * borders, does not show in the correct positions"):
 *
 *  - BORDER: MPV unit parity — the width is `borderSize / 55` of the font
 *    height (the v0.4.5 0.035f-per-unit was ≈1.9× MPV and its 0.15 fraction
 *    cap saturated every setting ≥ 5). LINEAR across the sheet's 0..10 range
 *    now (see [CsSubtitleGeometry.borderWidthFraction]).
 *  - BACKGROUND: ASS BorderStyle=3 / MPV sub-back-color semantics — the box
 *    is drawn PER LINE, hugs the line's glyph bounds, and is padded by the
 *    BORDER width in the same scaled units (the v0.4.5 fixed 6.dp/2.dp
 *    padding didn't scale with the font and let the outline poke outside).
 *    No fixed dp anywhere; no 1.25× lineHeight half-leading padding artifacts.
 *  - SHADOW: drawn IN ADDITION to the border (MPV behavior — v0.4.5
 *    suppressed the shadow whenever a border was set), stacked UNDER the
 *    border + fill passes.
 *  - No maxLines=4 truncation (MPV never truncates; long cues wrap).
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
        // Task 58: MPV unit parity (linear, generous ceiling) — see the object KDoc.
        val borderWidthPx = fontSizePx * CsSubtitleGeometry.borderWidthFraction(style.borderSize)
        // Task 58: the shadow follows the border's MPV units, drawn IN ADDITION.
        val shadowPx = fontSizePx * CsSubtitleGeometry.shadowFraction(style.shadowOffset)
        // Position: MPV sub-pos → bottom padding fraction (the engine mapping).
        val bottomFraction = CsSubtitleGeometry.bottomPaddingFraction(style.position)
        val bottomPadding = with(density) { (constraints.maxHeight * bottomFraction).toDp() }

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
        // Task 58: MPV draws sub-shadow-offset IN ADDITION to the border.
        val hasShadow = style.shadowOffset > 0

        // The ASS border-width box padding (in Dp for Modifier.padding).
        val boxPaddingDp = with(density) { borderWidthPx.toDp() }
        val shadowDp = with(density) { shadowPx.toDp() }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = bottomPadding),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Task 58: PER-LINE rendering — one box per cue line (ASS draws the
            // back-color box per line, hugging that line's glyphs). Lines are
            // centered relative to the widest line (a centered multi-line Text
            // behaves the same); each line's box sizes to ITS OWN glyphs, so
            // short lines get short boxes — no full-width slab.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                active.text.split('\n').forEach { line ->
                    SubtitleLineBox(
                        line = line,
                        hasBg = hasBg,
                        bgColor = bgColor,
                        hasBorder = hasBorder,
                        borderColor = borderColor,
                        borderWidthPx = borderWidthPx,
                        hasShadow = hasShadow,
                        shadowPx = shadowPx,
                        shadowDp = shadowDp,
                        textColor = textColor,
                        fontSizeSp = fontSizeSp,
                        typeface = typeface,
                        bold = style.bold,
                        italic = style.italic,
                        boxPaddingDp = boxPaddingDp,
                    )
                }
            }
        }
    }
}

/**
 * ONE cue line: the stacked passes (shadow → border → fill) inside a box that
 * hugs the line's glyph bounds and — when the background is on — is padded by
 * the border width (ASS BorderStyle=3 / MPV sub-back-color semantics).
 *
 * The fill pass sizes the box; the stroke/shadow passes overlay the exact same
 * text (`matchParentSize` — the stacked-Box technique; a Column would stack
 * them vertically). No fixed dp padding anywhere; no maxLines truncation.
 */
@Composable
private fun SubtitleLineBox(
    line: String,
    hasBg: Boolean,
    bgColor: Color,
    hasBorder: Boolean,
    borderColor: Color,
    borderWidthPx: Float,
    hasShadow: Boolean,
    shadowPx: Float,
    shadowDp: Dp,
    textColor: Color,
    fontSizeSp: TextUnit,
    typeface: Typeface,
    bold: Boolean,
    italic: Boolean,
    boxPaddingDp: Dp,
) {
    Box(
        modifier = Modifier
            .then(
                if (hasBg) {
                    // ASS: the box padding IS the border width (same scaled
                    // units) — it scales with the font and always covers the
                    // outline's outer half (the v0.4.5 2.dp vertical padding
                    // let thick outlines poke outside the box).
                    Modifier
                        .background(bgColor)
                        .padding(horizontal = boxPaddingDp, vertical = boxPaddingDp)
                } else {
                    Modifier
                },
            ),
    ) {
        // The SHADOW pass — stacked UNDER everything (MPV draws the shadow in
        // addition to the border; v0.4.5 suppressed it when a border was set).
        // The same stacked-stroke technique shifted downward + slightly
        // translucent (NOT TextStyle.textShadow: that API has no precedent in
        // this repo's pinned Compose and CI is the only compiler).
        if (hasShadow) {
            Text(
                text = line,
                color = borderColor.copy(alpha = 0.75f),
                fontSize = fontSizeSp,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                fontFamily = FontFamily(typeface),
                textAlign = TextAlign.Center,
                style = TextStyle(drawStyle = Stroke(width = shadowPx * 1.5f)),
                modifier = Modifier
                    .matchParentSize()
                    .offset(y = shadowDp),
            )
        }
        // The BORDER stroke pass UNDER the fill pass.
        if (hasBorder) {
            Text(
                text = line,
                color = borderColor,
                fontSize = fontSizeSp,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                fontFamily = FontFamily(typeface),
                textAlign = TextAlign.Center,
                style = TextStyle(drawStyle = Stroke(width = borderWidthPx)),
                modifier = Modifier.matchParentSize(),
            )
        }
        // The fill pass — sizes the box.
        Text(
            text = line,
            color = textColor,
            fontSize = fontSizeSp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            fontFamily = FontFamily(typeface),
            textAlign = TextAlign.Center,
        )
    }
}

/** The MPV font-name → Android typeface mapping (unknown names → sans-serif). */
private fun familyOf(name: String?): Typeface = when (name?.lowercase()) {
    "serif", "times new roman", "georgia" -> Typeface.SERIF
    "monospace", "mono", "courier new", "consolas" -> Typeface.MONOSPACE
    else -> Typeface.SANS_SERIF
}
