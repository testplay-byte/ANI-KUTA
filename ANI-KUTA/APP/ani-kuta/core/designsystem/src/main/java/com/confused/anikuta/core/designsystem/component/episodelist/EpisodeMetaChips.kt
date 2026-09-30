package com.confused.anikuta.core.designsystem.component.episodelist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import java.util.Locale

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 109 (D-719): THE SHARED EPISODE META PIECES — ONE home for the
//  details page's grid vocabulary so the player page's grid renders the
//  EXACT same chips, progress pill, and short date. The v1.1.65 device
//  round: "I want the player page to have the grid view, which looks and
//  feels exactly the same as the grid view of the details page… like how the
//  sub and dub episode tags are shown and also how the date is shown."
//
//  These are VERBATIM ports of the details module's internal components
//  (EpisodeLayouts.kt) — the details module now imports THESE (its local
//  copies are deleted), so the two grids can never drift again: one
//  implementation, two consumers, pixel parity by construction.
// ════════════════════════════════════════════════════════════════════════════

/**
 * D-556 (now shared, round 109): the date capsule — a quiet surface pill
 * ("Jan 1"). The v1.1.29 device round: the episode tags "look way too bad …
 * they do not look good in our UI". Small pill CAPSULE with a type-coded
 * quiet surface; shared by the classic row + the details grid + the player
 * grid.
 */
@Composable
fun EpisodeDateChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Text(
            text = text,
            fontFamily = RobotoFamily,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * D-556 (now shared, round 109): the per-type audio capsule — SUB is
 * primary-tinted, DUB tertiary-tinted, HSUB outline-tinted (neutral text) so
 * availability reads at a glance. The v1.1.65 device round's tag complaint:
 * the player grid rendered every pill as the same neutral gray rectangle —
 * this component is the details page's color-coded answer, now THE one
 * implementation both grids render.
 */
@Composable
fun EpisodeAudioChip(label: String, modifier: Modifier = Modifier) {
    val accent = when (label.trim().uppercase(Locale.US)) {
        "SUB" -> MaterialTheme.colorScheme.primary
        "DUB" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant // HSUB + unknowns stay neutral
    }
    val container = when (label.trim().uppercase(Locale.US)) {
        "HSUB" -> MaterialTheme.colorScheme.outlineVariant
        else -> accent
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = container.copy(alpha = 0.16f),
        modifier = modifier,
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp,
            color = accent,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * D-557 (now shared, round 109): the inset rounded progress pill — a
 * 4dp-tall translucent track with a solid fill; callers INSET it from the
 * imagery's edges via their own padding (YouTube's treatment, glitch-free on
 * any radius). Both grids' watch-progress AND download-progress bars render
 * through this one pill now (the player grid's old full-bleed 3dp edge bar
 * retired — the details grid's inset look is the standard).
 */
@Composable
fun EpisodeWatchProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = Color.White.copy(alpha = 0.30f),
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(progressColor),
        )
    }
}

/**
 * The short date label ("Jan 1") — the GRID chip's text + the TIMELINE node
 * label. ONE formatter, both pages: the player grid's chip carries the SAME
 * short shape as the details grid's (the v1.1.65 round: the player showed
 * "Oct 12, 2025" where the details showed "Oct 12" — the rows' long dates
 * are untouched, only the grid chip reads short, exactly like the details
 * page's own split).
 */
fun formatShortDate(epochMillis: Long): String {
    if (epochMillis <= 0) return ""
    val sdf = java.text.SimpleDateFormat("MMM d", Locale.getDefault())
    return sdf.format(java.util.Date(epochMillis))
}
