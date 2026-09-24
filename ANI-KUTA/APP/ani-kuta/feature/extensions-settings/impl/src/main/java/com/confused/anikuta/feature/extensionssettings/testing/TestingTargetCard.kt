package com.confused.anikuta.feature.extensionssettings.testing

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import com.confused.anikuta.core.common.HapticHelper
import com.confused.anikuta.core.designsystem.theme.RobotoFamily

// ════════════════════════════════════════════════════════════════════════════
//  Testing target card (round 83, D-578 rework).
//
//  INTERACTION MODEL (the round-82 device report: "tap expands the details —
//  not great; long-press does nothing; multi-select is checkbox-only"):
//  • TAP toggles the row's selection (the checkbox mirrors it).
//  • LONG-PRESS also toggles selection (with a light haptic) — the standard
//    multi-select gesture, now wired.
//  • The CHEVRON (right side) is the only expand/collapse affordance — the
//    per-test results live behind a deliberate action, not an accidental tap.
//  • The PLAY circle runs this one target immediately.
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TestingTargetCard(
    target: TestableTarget,
    state: TargetRunState?,
    selected: Boolean,
    expanded: Boolean,
    interactionEnabled: Boolean,
    onToggleSelect: () -> Unit,
    onToggleExpand: () -> Unit,
    onRun: () -> Unit,
    runEnabled: Boolean,
) {
    val context = LocalContext.current
    // Selection ring + tint animate so a tap is unambiguous even at a glance.
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(200),
        label = "testCardBorder",
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        animationSpec = tween(200),
        label = "testCardContainer",
    )

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(14.dp)),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        enabled = interactionEnabled,
                        onClick = onToggleSelect,
                        onLongClick = {
                            HapticHelper.lightTick(context)
                            onToggleSelect()
                        },
                    )
                    .padding(start = 4.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            ) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelect() },
                    enabled = interactionEnabled,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier.size(34.dp),
                )
                TargetIcon(target)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = target.name,
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            append(if (target.ecosystem == TestEcosystem.ANIYOMI) "Aniyomi" else "CloudStream")
                            target.lang?.let { append(" \u00b7 $it") }
                        },
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TargetStatusChip(state)
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse tests" else "Expand tests",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onToggleExpand)
                        .padding(2.dp)
                        .rotate(if (expanded) 180f else 0f),
                )
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Test this extension",
                    tint = if (runEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    },
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(enabled = runEnabled, onClick = onRun)
                        .padding(2.dp),
                )
            }

            androidx.compose.animation.AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 42.dp, end = 12.dp, bottom = 10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    ExtensionTestKind.entries.forEach { kind ->
                        TestRow(kind = kind, result = state?.results?.get(kind))
                    }
                }
            }
        }
    }
}

// ── One test's row (shared by the card + the batch overlay) ─────────────────

@Composable
internal fun TestRow(kind: ExtensionTestKind, result: TestResult?) {
    val status = result?.status ?: TestStatus.PENDING
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        TestStatusIcon(status)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = kind.label,
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val message = when (status) {
                TestStatus.PENDING -> kind.description
                else -> result?.message ?: ""
            }
            if (message.isNotBlank()) {
                Text(
                    text = message,
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    color = when (status) {
                        TestStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // D-578: human durations — "1.2 s", "2m 05s" — never raw ms.
        if (result != null && result.durationMs > 0 && status != TestStatus.RUNNING) {
            Text(
                text = TestTimeFormat.format(result.durationMs),
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun TestStatusIcon(status: TestStatus) {
    when (status) {
        TestStatus.PENDING -> Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), shape = CircleShape),
        )
        TestStatus.RUNNING -> CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        TestStatus.PASSED -> Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = "Passed",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        TestStatus.FAILED -> Icon(
            imageVector = Icons.Filled.Cancel,
            contentDescription = "Failed",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
        TestStatus.SKIPPED -> Icon(
            imageVector = Icons.Filled.RemoveCircleOutline,
            contentDescription = "Skipped",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
internal fun TargetStatusChip(state: TargetRunState?) {
    val (label, fg, bg) = when {
        state?.isRunning == true -> Triple(
            "Testing\u2026",
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        )
        state != null && state.finished && state.isHealthy -> Triple(
            "Passed ${state.passedCount}/${ExtensionTestKind.entries.size}",
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        )
        state != null && state.finished -> Triple(
            "${state.failedCount} failed",
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
        )
        // A cancelled run: partial results, not a verdict.
        state != null && state.results.isNotEmpty() -> Triple(
            "Incomplete",
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        )
        else -> Triple(
            "Untested",
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        )
    }
    Surface(color = bg, shape = RoundedCornerShape(50)) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** The target's icon: aniyomi Drawable, CS plugin URL, or the letter tile. */
@Composable
internal fun TargetIcon(target: TestableTarget) {
    when {
        target.iconDrawable != null -> AsyncImage(
            model = target.iconDrawable,
            contentDescription = "${target.name} icon",
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(7.dp)),
        )
        target.iconUrl != null -> SubcomposeAsyncImage(
            model = target.iconUrl
                .replace("%size%", "64")
                .replace("%exact_size%", "64"),
            contentDescription = "${target.name} icon",
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(7.dp)),
            loading = { LetterTile(target.name) },
            error = { LetterTile(target.name) },
        )
        else -> LetterTile(target.name)
    }
}

@Composable
internal fun LetterTile(name: String) {
    val firstLetter = name.firstOrNull()?.uppercase() ?: "?"
    val colors = listOf(
        Color(0xFFB1F256), Color(0xFF7CC8FA), Color(0xFFFF8A65),
        Color(0xFFE57C9F), Color(0xFFFFB300),
    )
    val color = colors[name.hashCode().and(0x7FFFFFFF) % colors.size]
    Surface(color = color, shape = RoundedCornerShape(7.dp), modifier = Modifier.size(32.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = firstLetter,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black,
            )
        }
    }
}
