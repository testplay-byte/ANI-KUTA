package com.confused.anikuta.feature.extensionssettings.testing

import android.graphics.drawable.Drawable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
 *
 * ROUND 85: `queued` — during a batch run, a target that is in the queue but
 * not yet live reads "Queued" (the old code showed every queued row as
 * "Testing…" or "Untested", both lies).
 */
@Composable
internal fun TargetStatusChip(
    state: TargetRunState?,
    modifier: Modifier = Modifier,
    queued: Boolean = false,
) {
    val (label, color, alpha) = when {
        queued && (state == null || (!state.finished && !state.isRunning)) ->
            Triple("Queued", MaterialTheme.colorScheme.tertiary, 0.85f)
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

/**
 * The GLANCEABLE kind row (round 85) — status icon + label + duration, NO
 * message column. The list screen's expansion shows ONLY this (the device
 * report: "in this screen only the simple tests and their time duration
 * should be shown"); the message/detail text belongs to the run + detail
 * pages.
 */
@Composable
internal fun KindCompactRow(
    kind: ExtensionTestKind,
    result: TestResult?,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        TestStatusIcon(result?.status ?: TestStatus.PENDING, size = 16.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = kind.label,
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = result?.let { TestTimeFormat.format(it.durationMs) } ?: "—",
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  THE PAYLOAD RENDERER (round 85) — "show me the ACTUAL search results…
//  same goes for the homepage… the details page… the episode list… the video
//  result too and for the stream play too." One composable, kind-aware:
//  thumbnails for search/home, a dossier header for details, episode chips,
//  server rows, and metric chips for ping/stream-play.
// ════════════════════════════════════════════════════════════════════════════

@Composable
internal fun KindPayloadView(
    kind: ExtensionTestKind,
    payload: TestPayload?,
    modifier: Modifier = Modifier,
) {
    if (payload == null) return
    when (kind) {
        ExtensionTestKind.PING -> PayloadMetricRow(
            listOfNotNull(
                payload.httpCode?.let { "HTTP $it" },
                payload.rttMs?.let { "$it ms" },
            ),
            modifier,
        )

        ExtensionTestKind.SEARCH, ExtensionTestKind.HOME_PAGE -> PayloadEntriesRow(payload.entries, modifier)

        ExtensionTestKind.DETAILS -> PayloadDetailsDossier(payload, modifier)

        ExtensionTestKind.EPISODE_LIST -> PayloadEpisodeChips(payload, modifier)

        ExtensionTestKind.VIDEO_RESOLVE -> PayloadVideoRows(payload.videos, modifier)

        ExtensionTestKind.STREAM_PLAY -> PayloadMetricRow(
            listOfNotNull(
                payload.streamHttpCode?.let { "HTTP $it" },
                payload.streamBytesLabel?.let { "$it delivered" },
            ),
            modifier,
        )
    }
}

/** A row of small metric chips (HTTP code, RTT, bytes…). */
@Composable
private fun PayloadMetricRow(values: List<String>, modifier: Modifier = Modifier) {
    if (values.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        values.forEach { value ->
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    text = value,
                    fontFamily = RobotoFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** The ACTUAL search/home entries — a horizontal strip of poster cards. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PayloadEntriesRow(
    entries: List<TestPayloadEntry>?,
    modifier: Modifier = Modifier,
) {
    if (entries.isNullOrEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        entries.forEach { entry ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(76.dp),
            ) {
                SubcomposeAsyncImage(
                    model = entry.thumbnailUrl,
                    contentDescription = entry.title,
                    modifier = Modifier
                        .size(width = 68.dp, height = 88.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    loading = {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            TargetLetterTile(entry.title.take(1).ifBlank { "?" }, 30.dp)
                        }
                    },
                    error = {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            TargetLetterTile(entry.title.take(1).ifBlank { "?" }, 30.dp)
                        }
                    },
                )
                Text(
                    text = entry.title,
                    fontFamily = RobotoFamily,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** The details dossier — poster + title + genres + status (+ synopsis). */
@Composable
private fun PayloadDetailsDossier(payload: TestPayload, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val thumb = payload.detailsThumbnailUrl
            if (thumb != null) {
                AsyncImage(
                    model = thumb,
                    contentDescription = payload.detailsTitle,
                    modifier = Modifier
                        .size(width = 52.dp, height = 70.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = payload.detailsTitle ?: "",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = buildList {
                    payload.detailsStatus?.let { add(it) }
                    payload.detailsGenres?.take(3)?.let { addAll(it) }
                }
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta.joinToString(" · "),
                        fontFamily = RobotoFamily,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        payload.detailsSynopsis?.let { synopsis ->
            Text(
                text = synopsis,
                fontFamily = RobotoFamily,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** The ACTUAL episode chips — EP 1 … EP n (+N more when capped). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PayloadEpisodeChips(payload: TestPayload, modifier: Modifier = Modifier) {
    val episodes = payload.episodes.orEmpty()
    if (episodes.isEmpty()) return
    val shown = episodes.take(24)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        shown.forEach { episode ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(7.dp),
            ) {
                Text(
                    text = "EP ${episode.number}",
                    fontFamily = RobotoFamily,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                )
            }
        }
        val hidden = (payload.episodeCount ?: episodes.size) - shown.size
        if (hidden > 0) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(7.dp),
            ) {
                Text(
                    text = "+$hidden more",
                    fontFamily = RobotoFamily,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** The ACTUAL resolved servers/qualities — one row each. */
@Composable
private fun PayloadVideoRows(
    videos: List<TestPayloadVideo>?,
    modifier: Modifier = Modifier,
) {
    if (videos.isNullOrEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
        videos.forEach { video ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = video.label,
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                video.quality?.let { quality ->
                    Text(
                        text = quality,
                        fontFamily = RobotoFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
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
