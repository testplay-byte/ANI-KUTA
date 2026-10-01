package com.confused.anikuta.core.designsystem.component.episodelist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
 *
 * ROUND 111 (D-726): the INITIALS forms join the type map — "S"/"D"/"H"
 * (the meta line's space-constrained simplification) keep their token's
 * color coding, so a compacted row still reads at a glance.
 */
@Composable
fun EpisodeAudioChip(label: String, modifier: Modifier = Modifier) {
    val token = label.trim().uppercase(Locale.US)
    val accent = when (token) {
        "SUB", "S" -> MaterialTheme.colorScheme.primary
        "DUB", "D" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant // HSUB + unknowns stay neutral
    }
    val container = when (token) {
        "HSUB", "H" -> MaterialTheme.colorScheme.outlineVariant
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
 * ROUND 110 (D-723): the grids' shared thumbnail corner — ONE constant both
 * episode grids (and the player grid's current-episode ring) clip through,
 * so the two cells' plate geometry can never drift. 12dp — the calmer
 * corner the v1.1.66 device round's parity pass settled on (the round-109
 * cells carried 16dp).
 */
val GridThumbnailCorner = 12.dp

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

// ════════════════════════════════════════════════════════════════════════
//  ROUND 111 (D-726): THE META LINE — the release date + the SUB/DUB
//  availability, ONE line, never a line break. The v1.1.67 device round's
//  layout-management rule: "the release date and the availability of sub
//  episodes and the availability of dub episodes. All of this info will
//  show in a single line … and there will be no line breaking, never ever.
//  And if there are issues, like there isn't enough space, then what will
//  happen is that it would simplify them. Like sub and dub tags will only
//  switch to the first letters, S or D, and make it customizable in the
//  future too."
//
//  THE DENSITY LADDER (chosen automatically against the line's REAL
//  available width — every variant is MEASURED for real in the layout
//  phase, so narrow phones, landscape, and half-width grid cells all
//  adapt on their own):
//    FULL      — every chip at its full label ("Oct 12, 2025" + SUB + DUB …).
//    INITIALS  — the audio tokens collapse to their first letters
//                (SUB→S, DUB→D, HSUB→H — the user's exact spec); other
//                labels (scanlators) keep their text.
//    CORE-ONLY — the rare bottom rung for ultra-tight cells: the date + the
//                audio INITIALS survive, the other labels drop. The three
//  core facts (date + sub availability + dub availability) ALWAYS render
//  and NEVER wrap.
//
//  [EpisodeMetaDensity] is the future-customization hook the user asked
//  for: AUTO (the ladder), or a pinned tier once the settings surface
//  wants a knob.
//
//  ROUND 112 (D-731): THE SAFE MEASURE — the v1.1.68 crash taught the
//  hard rule: the line lived in a BoxWithConstraints (a SubcomposeLayout),
//  and the timeline's height(IntrinsicSize.Min) parent asked it for its
//  min intrinsic height — "Asking for intrinsic measurements of
//  SubcomposeLayout layouts is not supported" — killing the details page
//  on entry. The line is now a PLAIN custom Layout (a fun-interface
//  MeasurePolicy): the variants are measured as real placeables in the
//  measure phase (their TRUE widths — text, letterSpacing, capsule
//  padding; the old TextMeasurer estimate could drift a few dp and clip
//  the trailing chip), and the plain MeasurePolicy answers intrinsic
//  queries through the interface defaults (the max over the variant
//  Rows = the chip height) — safe under ANY IntrinsicSize parent.
// ══════════════════════════════════════════════════════════════════════

/** The meta line's density — AUTO (the ladder) or a pinned tier. */
enum class EpisodeMetaDensity {
    /** The automatic ladder: FULL → INITIALS → CORE-ONLY against the real width. */
    AUTO,

    /** Always the full labels (the line may clip if the caller pins this in a tight cell). */
    FULL,

    /** Always the S/D/H initials (the user's space-constrained simplification). */
    INITIALS,
}

/** The audio token an initial-form label collapses to (null = not a core token). */
private fun audioInitial(label: String): String? = when (label.trim().uppercase(Locale.US)) {
    "SUB" -> "S"
    "DUB" -> "D"
    "HSUB" -> "H"
    else -> null
}

/** True when the label IS one of the core audio tokens (full or initial form). */
private fun isCoreAudioToken(label: String): Boolean =
    audioInitial(label) != null || label.trim().uppercase(Locale.US) in
        setOf("SUB", "DUB", "HSUB")

/**
 * THE META LINE — the date capsule + the type-coded audio capsules in ONE
 * horizontal line that NEVER breaks. Both pages' rows, both grids, and the
 * timeline render through this single component (the round-111
 * layout-management rule — one name, one implementation, both pages:
 * "we should go with a proper naming scheme for them … make both of them
 * the similar naming, so that it's easier for us").
 *
 * [dateText] is the ALREADY-KNOB-GATED date (null/blank = no date chip);
 * [audioTags] are the ALREADY-GATED audio tags (the tag model's output —
 * SUB/DUB/HSUB + the other labels). The ladder picks the density under
 * [EpisodeMetaDensity.AUTO] unless the caller pins a tier.
 *
 * ROUND 112 (D-731): rendered through a PLAIN custom [Layout] — never a
 * BoxWithConstraints/other SubcomposeLayout — so intrinsic-measuring
 * parents (the timeline's height(IntrinsicSize.Min)) get a real answer
 * instead of a crash, and the ladder's width comparison uses the variants'
 * MEASURED placeable widths (the chips' true geometry — no estimate
 * drift). See [metaLineLadderMeasurePolicy] (each variant Row bakes its
 * own [spacing] in via Arrangement.spacedBy at composition — the policy
 * itself needs no width math).
 */
@Composable
fun EpisodeMetaLine(
    dateText: String?,
    audioTags: List<String>,
    modifier: Modifier = Modifier,
    spacing: Dp = 6.dp,
    density: EpisodeMetaDensity = EpisodeMetaDensity.AUTO,
) {
    val date = dateText?.takeIf { it.isNotBlank() }
    if (date == null && audioTags.isEmpty()) {
        // Defense in depth: a caller may weight an EMPTY line (a sibling
        // must keep its end position) — compose the bare container so the
        // modifier's obligations survive at zero size.
        Box(modifier = modifier)
        return
    }

    // The ladder's variants, in priority order. Each is ONE measurable to
    // the layout below; the measure policy keeps the FIRST that fits the
    // incoming width, and the LAST is the floor (the core facts — they may
    // clip in an ultra-tight cell, but they NEVER wrap and NEVER drop).
    val fullLabels = audioTags
    val initialLabels = audioTags.map { label -> audioInitial(label) ?: label }
    val coreLabels = audioTags.filter { isCoreAudioToken(it) }
        .map { label -> audioInitial(label) ?: label }

    Layout(
        content = {
            when (density) {
                EpisodeMetaDensity.FULL -> MetaLineVariant(date, fullLabels, spacing)
                EpisodeMetaDensity.INITIALS -> MetaLineVariant(date, initialLabels, spacing)
                EpisodeMetaDensity.AUTO -> {
                    MetaLineVariant(date, fullLabels, spacing)
                    MetaLineVariant(date, initialLabels, spacing)
                    MetaLineVariant(date, coreLabels, spacing)
                }
            }
        },
        modifier = modifier,
        measurePolicy = metaLineLadderMeasurePolicy(),
    )
}

/** One density rung — the chips exactly as that tier renders them. */
@Composable
private fun MetaLineVariant(date: String?, labels: List<String>, spacing: Dp) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        date?.let { text -> EpisodeDateChip(text = text) }
        labels.forEach { label -> EpisodeAudioChip(label = label) }
    }
}

/**
 * ROUND 112 (D-731): the ladder's measure policy. Every variant row is
 * measured with UNBOUNDED width (its natural content width — the chips'
 * true rendered widths, letterSpacing and capsule padding included; a
 * bounded max would coerce an overflowing Row's report and break the
 * comparison), and
 * the first whose width fits the incoming maxWidth is the one placed; the
 * LAST variant is the floor when nothing fits (the core facts stay, the
 * line clips rather than wraps — by design).
 *
 * Being a plain [MeasurePolicy], intrinsic queries are answered by the
 * interface defaults (the max over the variant rows — the chip height),
 * which is exactly what the v1.1.68 crash demanded: the old
 * BoxWithConstraints (a SubcomposeLayout) THREW
 * "Asking for intrinsic measurements of SubcomposeLayout layouts is not
 * supported" under the timeline's height(IntrinsicSize.Min) and killed the
 * details page on entry.
 */
private fun metaLineLadderMeasurePolicy() = MeasurePolicy { measurables, constraints ->
    if (measurables.isEmpty()) return@MeasurePolicy layout(0, 0) {}
    // UNBOUNDED max width — the variant Rows must report their TRUE
    // content widths. A finite maxWidth would coerce an overflowing Row's
    // report back under the limit and the ladder would believe FULL
    // always fits (the 5-b audit's catch: AUTO would silently equal FULL
    // and tight lines would clip exactly like v1.1.68 did).
    val loose = constraints.copy(
        minWidth = 0,
        minHeight = 0,
        maxWidth = Constraints.Infinity,
    )
    val variants = measurables.map { it.measure(loose) }
    val chosen = variants.firstOrNull { it.width <= constraints.maxWidth } ?: variants.last()
    // Fill the incoming width when it is tight (a Row weight); wrap the
    // content when it is loose (the grid cells) — the old Box behavior.
    val width = maxOf(chosen.width, constraints.minWidth).coerceAtMost(constraints.maxWidth)
    val height = chosen.height
        .coerceAtLeast(constraints.minHeight)
        .coerceAtMost(constraints.maxHeight)
    layout(width, height) {
        chosen.placeRelative(0, ((height - chosen.height) / 2).coerceAtLeast(0))
    }
}

/**
 * ROUND 111 (D-727): the plain EPISODE NUMBER label — the details CLASSIC
 * row's quiet themed mini-label ("EP 5", 11sp ExtraBold primary), extracted
 * from the details module's [EpisodeNumberLabel] so the player's classic
 * row renders the IDENTICAL line (one implementation, both pages). The
 * details' compound "S-n/E-m" two-shade rendering stays in its module
 * (it owns the EpisodeTag type) and delegates its plain branch here.
 */
@Composable
fun EpisodeNumberLabelPlain(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontFamily = RobotoFamily,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.5.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
        maxLines = 1,
        softWrap = false,
    )
}
