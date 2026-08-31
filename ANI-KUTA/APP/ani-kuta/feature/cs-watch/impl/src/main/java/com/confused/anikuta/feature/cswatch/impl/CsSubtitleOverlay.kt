package com.confused.anikuta.feature.cswatch.impl

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.csplayer.CsCue
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
 *    (the Media3 fractional default is 0.0533 of the viewport height — byte
 *    parity with [CsPlayerEngine.applySubtitleStyle]'s mapping);
 *  - position = MPV sub-pos (0..100, 100 = flush bottom) → the same
 *    ((100 - pos) / 100) × 0.12 bottom-padding fraction the engine uses;
 *  - border > 0 → the outline edge (a stroke pass stacked UNDER the fill
 *    pass — the Compose outline technique); shadow > 0 (no border) → a
 *    drop-shadow pass (the same stacked-stroke technique, offset downward —
 *    NOT TextStyle.textShadow: that API has no precedent in this repo's
 *    pinned Compose and CI is the only compiler; the stroke pass is the
 *    proven AvatarCropScreen/NetworkTab pattern); background ARGB ≠ 0 →
 *    the cue's backdrop box;
 *  - delay shifts cue visibility (positive = later — MPV sub-delay).
 *
 * The active cue lookup is O(n) over sorted cues at the 100 ms ticker cadence
 * (episode files carry hundreds, not millions, of cues).
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
            0.0533f * (style.fontSize / 55f) * style.fontScale.coerceIn(0.25f, 4f)
        val fontSizeSp = with(density) { fontSizePx.coerceAtLeast(8f).toSp() }
        // Border: ~3.5% of the font height per MPV border unit (≈10% at the
        // default 3), capped so extreme settings stay readable.
        val borderWidthPx = fontSizePx *
            (0.035f * style.borderSize).coerceIn(0.02f, 0.15f)
        // Shadow: ~3% of the font height per unit, drawn downward.
        val shadowPx = fontSizePx * (0.03f * style.shadowOffset).coerceIn(0f, 0.15f)
        // Position: MPV sub-pos → bottom padding fraction (the engine mapping).
        val bottomFraction = ((100 - style.position.coerceIn(0, 100)) / 100f) * 0.12f
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
        val hasShadow = style.shadowOffset > 0 && !hasBorder

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = bottomPadding),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .then(
                        if (hasBg) Modifier.background(bgColor) else Modifier,
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                // The stroke pass UNDER the fill pass (stacked in a Box — a
                // Column would stack them vertically). The fill pass sizes the
                // box; the stroke pass overlays the exact same text.
                if (hasBorder) {
                    Text(
                        text = active.text,
                        color = borderColor,
                        fontSize = fontSizeSp,
                        lineHeight = fontSizeSp * 1.25f,
                        fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
                        fontFamily = FontFamily(typeface),
                        textAlign = TextAlign.Center,
                        style = TextStyle(drawStyle = Stroke(width = borderWidthPx)),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.matchParentSize(),
                    )
                }
                // The SHADOW pass (no-border mode): the same stacked-stroke
                // technique shifted downward + slightly translucent — the
                // repo-proven replacement for TextStyle.textShadow (see the
                // class KDoc: zero TextShadow precedent under the pinned
                // Compose; CI is the only real compiler).
                if (hasShadow) {
                    val shadowDp = with(density) { shadowPx.toDp() }
                    Text(
                        text = active.text,
                        color = borderColor.copy(alpha = 0.75f),
                        fontSize = fontSizeSp,
                        lineHeight = fontSizeSp * 1.25f,
                        fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
                        fontFamily = FontFamily(typeface),
                        textAlign = TextAlign.Center,
                        style = TextStyle(drawStyle = Stroke(width = shadowPx * 1.5f)),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .matchParentSize()
                            .offset(y = shadowDp),
                    )
                }
                Text(
                    text = active.text,
                    color = textColor,
                    fontSize = fontSizeSp,
                    lineHeight = fontSizeSp * 1.25f,
                    fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
                    fontFamily = FontFamily(typeface),
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
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
