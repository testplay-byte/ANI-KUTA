package com.confused.anikuta.feature.extensionssettings.testing

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.Motion
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import kotlin.math.min

// ════════════════════════════════════════════════════════════════════════════
//  THE TESTING DASHBOARD'S CHART KIT (round 85).
//
//  The device report: "you were only using a single bar… you did not show any
//  circular donuts, graphs, bars, or anything like that." This file is the
//  bespoke, Canvas-level answer — zero library chrome, dark-theme-native,
//  draw-scope-only animation (transform/opacity friendly):
//    • [DonutChart] — animated-sweep segments (the ProfileSections drawing
//      technique, upgraded with an entry sweep + a center slot);
//    • [AnimatedStatBar] — grow-in horizontal bar rows (the debug-bubble
//      NetworkTab pattern);
//    • [Sparkline] — a line+fill trend over the run history (NetworkTab's
//      path technique);
//    • [CountUpText] — the numbers move on entry.
// ════════════════════════════════════════════════════════════════════════════

/**
 * One donut segment: a count + its color. Zero counts draw nothing.
 * [gapAfterDegrees] (D-588, round 86) carves a small idle-ring gap AFTER the
 * segment — the round-87 suite-health ring uses it to part the verdict
 * groups (5°) and the two systems' sub-arcs inside each group (2.5°).
 */
data class DonutSegment(val count: Int, val color: Color, val gapAfterDegrees: Float = 0f)

/**
 * One VERDICT GROUP for the grouped donut (round 90, D-627): sub-arcs that
 * share ONE contiguous span of the ring — the suite-health ring's "passed"
 * group, for example, is [Aniyomi-passed, CloudStream-passed] rendered as a
 * single arc with NO gap and NO rounding where the two sub-arcs meet; only
 * the GROUP's outer ends are rounded. Groups themselves part by a gap wide
 * enough for both neighbouring round caps.
 */
data class DonutGroup(val segments: List<DonutSegment>)

/**
 * The animated donut. Segments sweep in clockwise from 12 o'clock on first
 * composition; [centerContent] floats in the hole. [sweep] drives the entry
 * animation (0..1).
 */
@Composable
internal fun DonutChart(
    segments: List<DonutSegment>,
    modifier: Modifier = Modifier,
    diameter: Dp = 120.dp,
    strokeFraction: Float = 0.30f,
    centerContent: @Composable () -> Unit = {},
) {
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(segments) {
        sweep.snapTo(0f)
        sweep.animateTo(1f, tween(Motion.DurationLong, easing = Motion.EasingEmphasized))
    }
    val total = segments.sumOf { it.count }.coerceAtLeast(1)
    // ROUND 87 (D-602): the idle track color resolves in COMPOSITION (a
    // Canvas draw lambda is not composable — the color must be captured
    // here, like the rest of the chart's inputs).
    val idleTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(diameter),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val outerRadius = min(size.width, size.height) / 2f * 0.92f
            val strokeWidth = outerRadius * strokeFraction
            val arcSize = Size(outerRadius * 2f, outerRadius * 2f)
            val topLeft = Offset(
                size.width / 2f - outerRadius,
                size.height / 2f - outerRadius,
            )
            // The idle ring behind the segments (the "untested" rest).
            // ROUND 87 (D-602): White@6% was INVISIBLE on the hero card —
            // "the bar blends into the background way too much and I cannot
            // clearly distinguish between it". A lit onSurfaceVariant track
            // reads as an intentional empty state.
            drawArc(
                color = idleTrackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
            )
            var startAngle = -90f
            // D-588: the usable sweep shrinks by the declared gaps so the
            // segments + gaps together complete exactly one revolution.
            val totalGaps = segments.sumOf { it.gapAfterDegrees.toDouble() }.toFloat()
            val usable = (360f - totalGaps).coerceAtLeast(360f * 0.5f)
            segments.forEach { segment ->
                if (segment.count > 0) {
                    val sweepAngle = (segment.count.toFloat() / total) * usable * sweep.value
                    drawArc(
                        color = segment.color,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                    )
                    startAngle += sweepAngle + segment.gapAfterDegrees * sweep.value
                }
            }
        }
        centerContent()
    }
}

/**
 * ROUND 90 (D-627): THE GROUPED DONUT — rounded, spaced slices with
 * combined groups. The user's spec for the suite-health ring:
 *   • the corners of the slices are ROUNDED (not the sharp butt ends);
 *   • the ring splits into THREE verdict groups (passed / failed / new),
 *     not six — the two systems' sub-arcs inside a group are COMBINED:
 *     where they meet there is NO gap and NO rounding, just a clean color
 *     seam;
 *   • only each group's OUTER ends (its very first and very last edge)
 *     carry the rounding — and subtly, never bulbous;
 *   • the groups part by a real gap, so the slices read as spaced.
 *
 * GEOMETRY: a round end cap is a semicircle of strokeWidth/2 centred on
 * the arc's boundary point, so it reaches `capAngle` into the gap on each
 * side — the group gap must exceed 2×capAngle for neighbouring caps never
 * to touch (plus a few degrees of breathing room). The caps are painted as
 * near-zero-sweep arcs with [StrokeCap.Round] (a "dot" exactly on the
 * boundary); the sub-arc bodies use butt caps so the internal seam stays a
 * straight, unrounded color change.
 *
 * Zero-count groups contribute nothing and no gap; the ring still closes
 * exactly. The entry sweep animation and the center slot match [DonutChart].
 */
@Composable
internal fun GroupedDonutChart(
    groups: List<DonutGroup>,
    modifier: Modifier = Modifier,
    diameter: Dp = 116.dp,
    strokeFraction: Float = 0.30f,
    centerContent: @Composable () -> Unit = {},
) {
    // Drop the empty groups up front — they get no span and no gap.
    val live = remember(groups) { groups.filter { g -> g.segments.any { it.count > 0 } } }
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(live) {
        sweep.snapTo(0f)
        sweep.animateTo(1f, tween(Motion.DurationLong, easing = Motion.EasingEmphasized))
    }
    val grandTotal = live.sumOf { g -> g.segments.sumOf { it.count } }.coerceAtLeast(1)
    val idleTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(diameter),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val outerRadius = min(size.width, size.height) / 2f * 0.92f
            val strokeWidth = outerRadius * strokeFraction
            val centerRadius = outerRadius - strokeWidth / 2f
            val arcSize = Size(outerRadius * 2f, outerRadius * 2f)
            val topLeft = Offset(
                size.width / 2f - outerRadius,
                size.height / 2f - outerRadius,
            )

            // The empty state: no groups at all — one honest idle ring.
            if (live.isEmpty()) {
                drawArc(
                    color = idleTrackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                )
                return@Canvas
            }

            // The cap geometry: how many degrees a round end cap reaches
            // along the centerline, and the group gap that keeps two
            // neighbouring caps from ever touching.
            val capAngleDeg = ((strokeWidth / 2f) / centerRadius) * (180f / Math.PI.toFloat())
            val groupGap = 2f * capAngleDeg + 3f
            val usable = (360f - live.size * groupGap).coerceAtLeast(360f * 0.25f)

            // Start half a gap past 12 o'clock so a single-group ring (the
            // "everything is new" state) keeps its gap centred at the top.
            var cursor = -90f + groupGap / 2f
            live.forEach { group ->
                val groupTotal = group.segments.sumOf { it.count }
                if (groupTotal > 0) {
                    val groupSweep = (groupTotal.toFloat() / grandTotal) * usable * sweep.value
                    // The idle track underlay for this group's span — the
                    // entry sweep reads as the arc growing along a track,
                    // and the gaps stay pure card surface (D-602's lit
                    // empty state, now per-group instead of full-circle).
                    drawArc(
                        color = idleTrackColor,
                        startAngle = cursor,
                        sweepAngle = groupSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                    )
                    // The sub-arc bodies — butt caps, meeting with NO gap
                    // and NO rounding at the internal seams (the combined-
                    // group rule). Zero-count sub-arcs skip silently.
                    var sub = cursor
                    var firstColor = group.segments.first { it.count > 0 }.color
                    var lastColor = firstColor
                    group.segments.forEach { segment ->
                        if (segment.count > 0) {
                            val segSweep = (segment.count.toFloat() / groupTotal) * groupSweep
                            drawArc(
                                color = segment.color,
                                startAngle = sub,
                                sweepAngle = segSweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                            )
                            lastColor = segment.color
                            sub += segSweep
                        }
                    }
                    // The ROUNDED outer ends — a near-zero-sweep arc with a
                    // Round cap paints the semicircle end cap exactly on the
                    // group's boundary, in the colour of the sub-arc that
                    // touches it there.
                    drawArc(
                        color = firstColor,
                        startAngle = cursor,
                        sweepAngle = 0.01f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = lastColor,
                        startAngle = cursor + groupSweep,
                        sweepAngle = 0.01f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                    cursor += groupSweep + groupGap
                }
            }
        }
        centerContent()
    }
}

/** The legend row under a donut: color square + "n label". */
@Composable
internal fun DonutLegendRow(color: Color, count: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = color, shape = RoundedCornerShape(3.dp), modifier = Modifier.size(8.dp)) {}
        Spacer(Modifier.width(5.dp))
        Text(
            text = "$count",
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A grow-in horizontal stat bar row — label, track, fill, and a trailing
 * value. Used by the stage-reliability section (fail rate per test kind).
 */
@Composable
internal fun AnimatedStatBarRow(
    label: String,
    fraction: Float,
    valueText: String,
    barColor: Color,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(Motion.DurationLong, easing = Motion.EasingEmphasized),
        label = "statBar-$label",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(84.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated.coerceAtLeast(0.01f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(barColor),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = valueText,
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = barColor,
        )
    }
}

/**
 * The pass-rate trend over the run history — a line + soft fill sparkline
 * (the NetworkTab drawing technique). [values] oldest → newest, each 0..1.
 */
@Composable
internal fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (values.size < 2) return
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(values) {
        sweep.snapTo(0f)
        sweep.animateTo(1f, tween(Motion.DurationSharedFlight, easing = Motion.EasingEmphasized))
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        val stepX = size.width / (values.size - 1)
        val points = values.mapIndexed { i, v ->
            Offset(i * stepX, size.height * (1f - v.coerceIn(0f, 1f)))
        }
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { p -> lineTo(p.x, p.y) }
        }
        // Fill under the line.
        val fill = Path().apply {
            addPath(path)
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }
        drawPath(fill, lineColor.copy(alpha = 0.14f * sweep.value))
        drawPath(
            path,
            lineColor.copy(alpha = sweep.value),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

/** A number that counts up on entry — the stats page feels alive. */
@Composable
internal fun CountUpText(
    value: Int,
    modifier: Modifier = Modifier,
    fontSize: Int = 18,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(value) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(Motion.DurationLong, easing = Motion.EasingEmphasized))
    }
    Text(
        text = "${(value * progress.value).toInt()}",
        fontFamily = RobotoFamily,
        fontSize = fontSize.sp,
        fontWeight = FontWeight.ExtraBold,
        color = color,
        modifier = modifier,
    )
}
