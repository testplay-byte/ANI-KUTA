package com.confused.anikuta.feature.extensionssettings.testing

import android.graphics.drawable.Drawable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import com.confused.anikuta.core.designsystem.theme.RobotoFamily

// ════════════════════════════════════════════════════════════════════════════
//  D-583 (round 84): the FIVE testing pages' SHARED UI PIECES — status icons,
//  verdict chips, the proportion bar, target icons and result rows. One file
//  so all pages render identical semantics (the round-84 report: the testing
//  UI must look and behave like ONE clean system, not five ad-hoc screens).
// ════════════════════════════════════════════════════════════════════════════

/** The lifecycle icon for one test's status (draw-phase sized). */
@Composable
internal fun TestStatusIcon(status: TestStatus, size: Dp = 18.dp) {
    when (status) {
        TestStatus.PENDING -> Icon(
            imageVector = Icons.Filled.Schedule,
            contentDescription = "Pending",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(size),
        )
        TestStatus.RUNNING -> CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.dp,
            modifier = Modifier.size(size),
        )
        TestStatus.PASSED -> Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = "Passed",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(size),
        )
        TestStatus.FAILED -> Icon(
            imageVector = Icons.Filled.Cancel,
            contentDescription = "Failed",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(size),
        )
        TestStatus.SKIPPED -> Icon(
            imageVector = Icons.Filled.SkipNext,
            contentDescription = "Skipped",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size),
        )
    }
}

/**
 * The COMPACT verdict chip — one small badge per target row (the round-84
 * report: the old right edge stacked three different indicators; now there
 * is exactly ONE).
 */
@Composable
internal fun TargetStatusChip(state: TargetRunState?, modifier: Modifier = Modifier) {
    val (label, color, alpha) = when {
        state == null || (!state.finished && !state.isRunning && state.results.isEmpty()) ->
            Triple("Untested", MaterialTheme.colorScheme.onSurfaceVariant, 0.55f)
        state.isRunning -> Triple("Testing…", MaterialTheme.colorScheme.primary, 0.9f)
        state.abortedByUser -> Triple("Skipped", MaterialTheme.colorScheme.onSurfaceVariant, 0.8f)
        state.isHealthy -> Triple("Passed", MaterialTheme.colorScheme.primary, 0.9f)
        state.failedCount > 0 -> Triple(
            "${state.failedCount} failed",
            MaterialTheme.colorScheme.error,
            0.9f,
        )
        else -> Triple("Incomplete", MaterialTheme.colorScheme.onSurfaceVariant, 0.8f)
    }
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(50),
        modifier = modifier,
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color.copy(alpha = alpha),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/**
 * The animated pass/fail/untested proportion bar (moved from the round-83
 * summary card) — three weight segments, spring-animated.
 */
@Composable
internal fun ProportionBar(
    passedCount: Int,
    failedCount: Int,
    untestedCount: Int,
    modifier: Modifier = Modifier,
) {
    val total = (passedCount + failedCount + untestedCount).coerceAtLeast(1)
    val passedWeight by animateFloatAsState(
        targetValue = passedCount.toFloat() / total,
        animationSpec = tween(450),
        label = "proportionPassed",
    )
    val failedWeight by animateFloatAsState(
        targetValue = failedCount.toFloat() / total,
        animationSpec = tween(450),
        label = "proportionFailed",
    )
    val restWeight = (1f - passedWeight - failedWeight).coerceAtLeast(0f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        if (passedCount > 0) {
            Box(
                modifier = Modifier
                    .weight(passedWeight.coerceAtLeast(0.01f))
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        if (failedCount > 0) {
            Box(
                modifier = Modifier
                    .weight(failedWeight.coerceAtLeast(0.01f))
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(MaterialTheme.colorScheme.error),
            )
        }
        if (untestedCount > 0) {
            Box(
                modifier = Modifier
                    .weight(restWeight.coerceAtLeast(0.01f))
                    .fillMaxWidth()
                    .height(10.dp),
            )
        }
    }
}

/** The target's icon — Aniyomi Drawable, CloudStream URL, or the letter tile. */
@Composable
internal fun TargetIconView(target: TestableTarget, size: Dp = 40.dp) {
    when {
        target.iconDrawable != null -> AsyncImage(
            model = target.iconDrawable,
            contentDescription = target.name,
            modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp)),
        )
        target.iconUrl != null -> SubcomposeAsyncImage(
            model = target.iconUrl,
            contentDescription = target.name,
            modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp)),
            loading = { TargetLetterTile(target.name, size) },
            error = { TargetLetterTile(target.name, size) },
        )
        else -> TargetLetterTile(target.name, size)
    }
}

/** The colorful letter tile — the never-blank icon fallback. */
@Composable
internal fun TargetLetterTile(name: String, size: Dp = 40.dp) {
    val firstLetter = name.firstOrNull()?.uppercase() ?: "?"
    val colors = listOf(
        Color(0xFFB1F256), Color(0xFF7CC8FA), Color(0xFFFF8A65),
        Color(0xFFE57C9F), Color(0xFFFFB300),
    )
    val color = colors[name.hashCode().and(0x7FFFFFFF) % colors.size]
    Surface(
        color = color,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = firstLetter,
                fontFamily = RobotoFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black,
            )
        }
    }
}

/**
 * One test's one-line result row (the kind icon + label + duration +
 * short message) — the same anatomy in the Run page, the list expansion
 * and the detail page.
 */
@Composable
internal fun KindResultRow(
    kind: ExtensionTestKind,
    result: TestResult?,
    modifier: Modifier = Modifier,
    messageMaxLines: Int = 1,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        TestStatusIcon(result?.status ?: TestStatus.PENDING)
        Spacer(Modifier.width(10.dp))
        Text(
            text = kind.label,
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(84.dp),
        )
        Text(
            text = result?.let { TestTimeFormat.format(it.durationMs) } ?: "—",
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(62.dp),
        )
        val message = when {
            result == null -> "Queued"
            result.status == TestStatus.RUNNING -> "Running…"
            result.message.isNotBlank() -> result.message
            else -> ""
        }
        Text(
            text = message,
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = messageMaxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** "aniyomi"/"cloudstream" ↔ ecosystem, tolerantly. */
internal fun String.toEcosystem(): TestEcosystem? = when (lowercase()) {
    "aniyomi" -> TestEcosystem.ANIYOMI
    "cloudstream" -> TestEcosystem.CLOUDSTREAM
    else -> null
}

internal fun TestEcosystem.displayName(): String = when (this) {
    TestEcosystem.ANIYOMI -> "Aniyomi"
    TestEcosystem.CLOUDSTREAM -> "CloudStream"
}

/**
 * The persisted verdict of one target rendered as a live [TargetRunState] —
 * pages read the live session FIRST and fall back to this store snapshot.
 */
internal fun storedToRunState(run: ExtensionTestResultStore.StoredTargetRun): TargetRunState =
    TargetRunState(
        results = run.results,
        isRunning = false,
        finished = run.finished,
        abortedByUser = run.abortedByUser,
    )

/** A short "n of m" helper shared by the run + home pages. */
internal fun runProgressLabel(cursor: Int, total: Int): String = "$cursor of $total"
