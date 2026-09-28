package com.confused.anikuta.feature.extensionssettings.testing

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import com.confused.anikuta.core.designsystem.theme.Motion
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
// ROUND 99 (D-676/D-678): the shared list-icon request policy lives in the
// parent package's ExtensionListChrome (same Gradle module).
import com.confused.anikuta.feature.extensionssettings.buildListIconRequest
import kotlinx.coroutines.delay

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
            // ROUND 87 (D-594): neutral gray — the old Material-baseline
            // tertiary (pale pink) read as a third system color.
            Triple("Queued", MaterialTheme.colorScheme.onSurfaceVariant, 0.8f)
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
 * The animated pass/fail/untested proportion bar (the round-83 summary
 * card's, reworked round 87 D-594):
 *   • the TRACK is VISIBLE on its own (the round-87 report: an all-untested
 *     bar "blends into the background" — now a lit track + a hairline ring),
 *   • the untested segment gets a REAL fill (no more empty gap),
 *   • the segment colors are PARAMETER so a system card can paint its own
 *     ecosystem's hues,
 *   • 2dp gaps let the track show through as hairline dividers.
 */
@Composable
internal fun ProportionBar(
    passedCount: Int,
    failedCount: Int,
    untestedCount: Int,
    modifier: Modifier = Modifier,
    passedColor: Color = MaterialTheme.colorScheme.primary,
    failedColor: Color = MaterialTheme.colorScheme.error,
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
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(50))
            .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f), RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.20f)),
    ) {
        if (passedCount > 0) {
            Box(
                modifier = Modifier
                    .weight(passedWeight.coerceAtLeast(0.01f))
                    .fillMaxHeight()
                    .background(passedColor),
            )
        }
        if (failedCount > 0) {
            Box(
                modifier = Modifier
                    .weight(failedWeight.coerceAtLeast(0.01f))
                    .fillMaxHeight()
                    .background(failedColor),
            )
        }
        if (untestedCount > 0) {
            Box(
                modifier = Modifier
                    .weight(restWeight.coerceAtLeast(0.01f))
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)),
            )
        }
    }
}

/**
 * The target's icon — Aniyomi Drawable, CloudStream URL, or the letter tile.
 *
 * ROUND 99 (D-678): SubcomposeAsyncImage RETIRED from the URL branch — the
 * D-673 treatment (CsPluginIcon, round 98) extended to the testing list: this
 * renders once per visible ROW of the targets list (a scroll surface with the
 * full 90-plugin set), and SubcomposeAsyncImage ran a real subcomposition per
 * icon. The plain AsyncImage + an onState-tracked tile behind it delivers the
 * identical visuals (tile while loading, tile on error) with zero
 * subcomposition, and the request rides [buildListIconRequest] — the D-676
 * list-icon policy (no crossfade, no cache-header expiry: raw.githubusercontent's
 * max-age=300 used to refetch every icon >5min old on its next view).
 */
@Composable
internal fun TargetIconView(target: TestableTarget, size: Dp = 40.dp) {
    when {
        target.iconDrawable != null -> AsyncImage(
            model = target.iconDrawable,
            contentDescription = target.name,
            modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp)),
        )
        target.iconUrl != null -> {
            var loadSucceeded by remember(target.iconUrl) { mutableStateOf(false) }
            Box(modifier = Modifier.size(size)) {
                // The tile stays composed until Coil reports Success — it is
                // the loading AND the error slot in one (the CsPluginIcon
                // pattern from ExtensionListChrome).
                if (!loadSucceeded) {
                    TargetLetterTile(target.name, size)
                }
                AsyncImage(
                    model = buildListIconRequest(target.iconUrl),
                    contentDescription = target.name,
                    modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp)),
                    onState = { state ->
                        loadSucceeded = state is coil3.compose.AsyncImagePainter.State.Success
                    },
                )
            }
        }
        else -> TargetLetterTile(target.name, size)
    }
}

/** The colorful letter tile — the never-blank icon fallback. */
@Composable
internal fun TargetLetterTile(name: String, size: Dp = 40.dp) {
    val firstLetter = name.firstOrNull()?.uppercase() ?: "?"
    Surface(
        color = letterTileColor(name),
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
 * ROUND 91 (D-630): the letter tile's hue, extracted — the "Recently tested"
 * chips fall back to THIS color when an icon's own tint can't be extracted,
 * so a chip and its fallback icon always agree (the never-blank contract,
 * extended to the tint).
 */
internal fun letterTileColor(name: String): Color {
    val colors = listOf(
        Color(0xFFB1F256), Color(0xFF7CC8FA), Color(0xFFFF8A65),
        Color(0xFFE57C9F), Color(0xFFFFB300),
    )
    return colors[name.hashCode().and(0x7FFFFFFF) % colors.size]
}

/**
 * THE LIVE ELAPSED TIMER (round 87, D-597) — one shared ticker for every
 * place a RUNNING test must show its live elapsed time. The round-87 device
 * report: the run page and the detail page froze at "0 ms" while a test ran
 * (the engine's RUNNING emission carries durationMs = 0, and only the list
 * page had a ticker). The controller ALREADY publishes the kind's wall-clock
 * start (`runningKindStartedAtMs`) — this composable just ticks against it.
 */
@Composable
internal fun LiveElapsedText(
    startedAtMs: Long,
    color: Color,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 11.sp,
    fontWeight: FontWeight = FontWeight.ExtraBold,
) {
    var elapsedMs by remember(startedAtMs) {
        mutableLongStateOf(System.currentTimeMillis() - startedAtMs)
    }
    LaunchedEffect(startedAtMs) {
        while (true) {
            elapsedMs = System.currentTimeMillis() - startedAtMs
            delay(200)
        }
    }
    Text(
        text = TestTimeFormat.format(elapsedMs),
        fontFamily = RobotoFamily,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier,
    )
}

/**
 * One test's one-line result row (the kind icon + label + duration +
 * short message) — the same anatomy in the Run page, the list expansion
 * and the detail page.
 *
 * ROUND 87 (D-597): pass [runningStartedAtMs] and the RUNNING row's
 * duration slot ticks LIVE instead of freezing at "0 ms".
 */
@Composable
internal fun KindResultRow(
    kind: ExtensionTestKind,
    result: TestResult?,
    modifier: Modifier = Modifier,
    messageMaxLines: Int = 1,
    runningStartedAtMs: Long? = null,
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
        val running = result?.status == TestStatus.RUNNING && runningStartedAtMs != null
        if (running) {
            LiveElapsedText(
                startedAtMs = runningStartedAtMs!!,  // checked above
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(62.dp),
            )
        } else {
            Text(
                text = result?.let { TestTimeFormat.format(it.durationMs) } ?: "—",
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(62.dp),
            )
        }
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
 * The GLANCEABLE kind row (round 85; the LIVE rework round 86, D-589/D-592):
 * status icon + label + duration, NO message column — the list screen's
 * expansion shows ONLY this. Round 86 adds the device report's quality-of-life
 * set, now part of the design language:
 *   • SEPARATION — every row sits in its own soft rounded card (no more one
 *     merged wall of rows);
 *   • LIVE TIMER — while the kind runs, the duration ticks up in real time
 *     ("if an extension takes about 10 seconds to search, the timer moves up
 *     along with it");
 *   • LEADER DOTS — an animated dots trail between the test name and the
 *     live timer, running ONLY while that test is running;
 *   • HONEST SKIPS — a genuine SKIPPED verdict carries its reason in a small
 *     dim line (failures read as FAILED since the D-590 engine gate).
 *
 * @param runningStartedAtMs the wall-clock moment the RUNNING kind started
 *   (recorded by the controller on the RUNNING emission) — null on terminals.
 * @param runningDetail the LIVE per-phrase search status (the D-592 pipe).
 */
@Composable
internal fun KindCompactRow(
    kind: ExtensionTestKind,
    result: TestResult?,
    modifier: Modifier = Modifier,
    runningStartedAtMs: Long? = null,
    runningDetail: String? = null,
) {
    val status = result?.status ?: TestStatus.PENDING
    val running = status == TestStatus.RUNNING

    // The LEADER DOTS — an animated 1→3 dot trail between the name and the
    // timer, running only while the kind runs (the user's animation ask).
    // (The live timer itself is the shared LiveElapsedText, D-597.)
    val dotsPhase = rememberInfiniteTransition(label = "kindDots").animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "dotsPhase",
    )

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        shape = RoundedCornerShape(9.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TestStatusIcon(status, size = 15.dp)
                Spacer(Modifier.width(9.dp))
                Text(
                    text = kind.label,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (running) {
                    Text(
                        text = ".".repeat((dotsPhase.value.toInt().coerceIn(0, 2)) + 1),
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(18.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
                if (running && runningStartedAtMs != null) {
                    LiveElapsedText(
                        startedAtMs = runningStartedAtMs,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                    )
                } else {
                    Text(
                        text = result?.let { TestTimeFormat.format(it.durationMs) } ?: "—",
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // The live per-phrase status — which search phrase is being tried.
            if (running && !runningDetail.isNullOrBlank()) {
                Text(
                    text = runningDetail,
                    fontFamily = RobotoFamily,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 24.dp, top = 2.dp),
                )
            }
            // The honest skip — a genuine SKIPPED verdict says WHY (tiny, dim).
            if (status == TestStatus.SKIPPED && !result?.message.isNullOrBlank()) {
                Text(
                    text = result?.message.orEmpty(),
                    fontFamily = RobotoFamily,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 24.dp, top = 2.dp),
                )
            }
        }
    }
}

/**
 * The per-row ENTRANCE animation wrapper — every newly-appearing kind row
 * fades + expands in (the device report liked the "one test shows at a time"
 * reveal; this makes the list page feel the same as the run page).
 */
@Composable
internal fun AppearingKindRow(content: @Composable () -> Unit) {
    val appear = remember { MutableTransitionState(false) }.apply { targetState = true }
    androidx.compose.animation.AnimatedVisibility(
        visibleState = appear,
        enter = androidx.compose.animation.fadeIn(tween(200)) +
            androidx.compose.animation.expandVertically(tween(260, easing = Motion.EasingEmphasized)),
    ) {
        content()
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
    autoPlayPreview: Boolean = true,
) {
    if (payload == null) return
    when (kind) {
        // D-592 (round 86): the PING metric chips are GONE — the row's detail
        // line ("Responded HTTP 200 in 523 ms" + the URL) already says it all;
        // the tags duplicated it at the bottom of every card.
        ExtensionTestKind.PING -> Unit

        ExtensionTestKind.SEARCH, ExtensionTestKind.HOME_PAGE -> PayloadEntriesGrid(payload.entries, modifier)

        ExtensionTestKind.DETAILS -> PayloadDetailsDossier(payload, modifier)

        ExtensionTestKind.EPISODE_LIST -> PayloadEpisodeList(payload, modifier)

        ExtensionTestKind.VIDEO_RESOLVE -> PayloadVideoRows(payload.videos, modifier)

        ExtensionTestKind.STREAM_PLAY -> {
            // ROUND 95 (D-656): THE STREAM FACTS — "the UI of it will be made
            // quite simple and quite clean. Like it will show the details of
            // the stream, like resolved link which it played, and other
            // details like the amount loaded… in a proper formatted way":
            // the resolved link as its own monospace line and the loaded
            // amount (+ the HTTP code) as a labeled fact, THEN the live
            // preview (which keeps its last frame after success below).
            payload.streamUrl?.let { url ->
                Column(modifier = modifier.fillMaxWidth()) {
                    StreamFactRow(label = "LINK", value = url, monospace = true)
                    payload.streamBytesLabel?.let { bytes ->
                        val loaded = if (payload.streamHttpCode != null) {
                            "$bytes · HTTP ${payload.streamHttpCode}"
                        } else {
                            bytes
                        }
                        StreamFactRow(label = "LOADED", value = loaded, monospace = false)
                    }
                    Spacer(Modifier.height(8.dp))
                    StreamPreviewPlayer(
                        url = url,
                        referer = payload.streamReferer,
                        userAgent = payload.streamUserAgent,
                        headers = payload.streamHeaders,
                        modifier = Modifier,
                        autoPlay = autoPlayPreview,
                    )
                }
            }
        }
    }
}

/**
 * ROUND 95 (D-656): one stream-play fact row — a small bold label on the
 * left, the value on the right (the resolved link renders monospace, one
 * ellipsized line).
 */
@Composable
private fun StreamFactRow(label: String, value: String, monospace: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.6.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.width(52.dp),
        )
        Text(
            text = value,
            fontFamily = if (monospace) FontFamily.Monospace else RobotoFamily,
            fontSize = 10.sp,
            fontWeight = if (monospace) FontWeight.Normal else FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Whether a payload renders anything for [kind] (round 88, D-622) — the
 * detail page's results section consults this so a kind whose payload view
 * is intentionally empty (PING since D-592) never shows an empty labeled
 * box: the block's message/detail lines carry the whole verdict instead.
 */
internal fun kindPayloadHasContent(kind: ExtensionTestKind, payload: TestPayload?): Boolean {
    if (payload == null) return false
    return when (kind) {
        ExtensionTestKind.PING -> false
        ExtensionTestKind.SEARCH, ExtensionTestKind.HOME_PAGE -> !payload.entries.isNullOrEmpty()
        ExtensionTestKind.DETAILS -> payload.detailsTitle != null ||
            payload.detailsUrl != null ||
            payload.detailsSynopsis != null
        ExtensionTestKind.EPISODE_LIST -> !payload.episodes.isNullOrEmpty()
        ExtensionTestKind.VIDEO_RESOLVE -> !payload.videos.isNullOrEmpty()
        ExtensionTestKind.STREAM_PLAY -> payload.streamUrl != null
    }
}

/**
 * The ACTUAL search/home entries — ROUND 95 (D-656): the TOP THREE by
 * default, one clean 3-wide row, with an EXPAND button revealing the rest
 * of the captured list ("it should only show by default the top three
 * results. And below it there should be an expand button which I can click
 * to see the other results"). The round-88 all-12 grid is the EXPANDED
 * state now — the resting card loads a third of the thumbnails (a real
 * perf win on stored pages).
 */
@Composable
private fun PayloadEntriesGrid(
    entries: List<TestPayloadEntry>?,
    modifier: Modifier = Modifier,
) {
    if (entries.isNullOrEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    // The captured cap is 12 — the grid reveals it row by row on expand.
    val shown = if (expanded) entries.take(12) else entries.take(3)
    val grid = shown.chunked(3)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(tween(220)),
    ) {
        grid.forEach { rowEntries ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                rowEntries.forEach { entry ->
                    Column(modifier = Modifier.weight(1f)) {
                        SubcomposeAsyncImage(
                            model = entry.thumbnailUrl,
                            contentDescription = entry.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.76f)
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
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                // Pad the last row out to a true 3-wide grid.
                repeat(3 - rowEntries.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
        if (entries.size > 3) {
            PayloadExpandButton(
                expanded = expanded,
                hiddenCount = entries.size - shown.size,
                onToggle = { expanded = !expanded },
            )
        }
    }
}

/**
 * ROUND 95 (D-656): THE EXPAND BUTTON — the shared top-3 affordance. A quiet
 * full-width outlined strip with a chevron: "Expand · 9 more" when
 * collapsed (the hidden count says what the click buys), "Collapse" when
 * open. Used by the results grid, the episode list and the resolved links.
 */
@Composable
private fun PayloadExpandButton(
    expanded: Boolean,
    hiddenCount: Int,
    onToggle: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = if (expanded) {
                    "Collapse"
                } else {
                    "Expand" + if (hiddenCount > 0) " · $hiddenCount more" else ""
                },
                fontFamily = RobotoFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * The details dossier — ROUND 95 (D-656): "in the loaded details, what it
 * will show is the title of it, and the title will be shown in only one
 * single line, and the other few details will be shown below it but in a
 * proper formatted way." So: poster + the ONE-LINE title; the status as a
 * primary-tinted pill and every genre as its own quiet pill below (the old
 * crammed status·genre·genre string could not be formatted properly); the
 * URL as its own monospace line. The synopsis is retired from the CARD (the
 * compression pass — it stays captured in the payload/store).
 */
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
            Text(
                text = payload.detailsTitle ?: "",
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        // The few details, PROPERLY FORMATTED: the status as a tinted pill,
        // the genres as quiet pills — never one crammed string again.
        val status = payload.detailsStatus
        val genres = payload.detailsGenres.orEmpty().take(8)
        if (status != null || genres.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                status?.let { PayloadMetaChip(text = it, emphasized = true) }
                genres.forEach { genre -> PayloadMetaChip(text = genre, emphasized = false) }
            }
        }
        // The entry's URL as its own formatted line — kept from D-592 ("the
        // title, the details, the URL properly shown").
        payload.detailsUrl?.let { url ->
            Text(
                text = url,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * ROUND 95 (D-656): one details pill — the status wears the primary tint,
 * the genres stay quiet surfaceVariant.
 */
@Composable
private fun PayloadMetaChip(text: String, emphasized: Boolean) {
    val tint = if (emphasized) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = if (emphasized) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = text,
            fontFamily = RobotoFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/**
 * The ACTUAL episodes — ROUND 95 (D-656): a LIST VIEW now, "and below it it
 * will not say any description like episode 13 details or anything like
 * that. It would just show the episode found, and it would show those
 * episodes in a list view, and only the top three will be shown, and the
 * others can be shown by expanding it. But by default it will be
 * collapsed." Each row: the episode number bold + its name (one ellipsized
 * line); the captured cap (48) is the EXPANDED state; a quiet trailing row
 * states the uncaptured remainder ("+512 more on the site").
 */
@Composable
private fun PayloadEpisodeList(payload: TestPayload, modifier: Modifier = Modifier) {
    val episodes = payload.episodes.orEmpty()
    if (episodes.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val shown = if (expanded) episodes else episodes.take(3)
    val total = payload.episodeCount ?: episodes.size
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(tween(220)),
    ) {
        shown.forEach { episode ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "EP ${episode.number}",
                        fontFamily = RobotoFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    episode.name.takeIf { it.isNotBlank() }?.let { name ->
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = name,
                            fontFamily = RobotoFamily,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        // The uncaptured remainder — honest, one quiet line.
        val uncaptured = total - episodes.size
        if (expanded && uncaptured > 0) {
            Text(
                text = "+$uncaptured more on the site",
                fontFamily = RobotoFamily,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 2.dp),
            )
        }
        if (total > 3 || episodes.size > 3) {
            PayloadExpandButton(
                expanded = expanded,
                hiddenCount = (if (expanded) uncaptured else episodes.size - shown.size).coerceAtLeast(0),
                onToggle = { expanded = !expanded },
            )
        }
    }
}

/**
 * The ACTUAL resolved servers/qualities — ROUND 95 (D-656): the TOP THREE
 * rows by default with the shared EXPAND button below ("it would only show
 * the top three links, and others can be seen by expanding it if
 * available"); each row is the round-86 formatted anatomy (index + dot +
 * label + quality pill), and the full captured list (24) is the EXPANDED
 * state.
 */
@Composable
private fun PayloadVideoRows(
    videos: List<TestPayloadVideo>?,
    modifier: Modifier = Modifier,
) {
    if (videos.isNullOrEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val shown = if (expanded) videos else videos.take(3)
    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(tween(220)),
    ) {
        shown.forEachIndexed { index, video ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = "${index + 1}",
                        fontFamily = RobotoFamily,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.width(14.dp),
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                    )
                    Spacer(Modifier.width(7.dp))
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
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(50),
                        ) {
                            Text(
                                text = quality,
                                fontFamily = RobotoFamily,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
        if (videos.size > 3) {
            PayloadExpandButton(
                expanded = expanded,
                hiddenCount = videos.size - shown.size,
                onToggle = { expanded = !expanded },
            )
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

// ════════════════════════════════════════════════════════════════════════════
//  D-592 (round 86) — THE STREAM LIVE PREVIEW; ROUND 87 (D-598) — THE CAP.
//  A small muted ExoPlayer card renders the resolved stream inline whenever
//  the STREAM_PLAY payload carries its URL. Round 87's rules (the user's
//  exact spec): playback is capped at THIRTY SECONDS — after that it stops
//  ITSELF, says "stream played successfully", and everything (player,
//  surface, resources) closes cleanly. No looping anymore: REPEAT_MODE_OFF
//  + a listener, so a stream that ends early ALSO reports success honestly.
// ════════════════════════════════════════════════════════════════════════════

/** Where the capped preview is in its (short) life. */
private enum class StreamPreviewPhase {
    /** Playing — the countdown is running. */
    PLAYING,

    /** Ended by the 30s cap or by the stream finishing — a SUCCESS. */
    ENDED,

    /** The player errored before delivering any playback. */
    FAILED,
}

/** The preview's hard cap (the user: "30 seconds at max"). */
private const val STREAM_PREVIEW_CAP_S = 30

@Composable
internal fun StreamPreviewPlayer(
    url: String,
    referer: String?,
    userAgent: String?,
    headers: Map<String, String>?,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // ROUND 88 (D-622): the TAP-TO-PLAY GATE. A stored page (an old verdict
    // being reviewed) must NOT start streaming video the moment it opens —
    // that was data, battery and surprise spent without consent. The gate
    // opens only when (a) the preview belongs to a run this screen watched
    // live ([autoPlay] = true) or (b) the user taps the play strip.
    var userRequested by remember(url) { mutableStateOf(autoPlay) }
    if (!userRequested) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            shape = RoundedCornerShape(10.dp),
            modifier = modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { userRequested = true }
                    .padding(horizontal = 11.dp, vertical = 9.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play the 30-second preview",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(9.dp))
                Text(
                    text = "Tap to preview the stream — muted, capped at ${STREAM_PREVIEW_CAP_S}s",
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        return
    }

    var phase by remember(url) { mutableStateOf(StreamPreviewPhase.PLAYING) }
    var secondsLeft by remember(url) { mutableIntStateOf(STREAM_PREVIEW_CAP_S) }

    // ROUND 95 (D-656): THE LAST-FRAME KEEP — "the live preview will show
    // properly even after it has been successful, like the live preview
    // will show the very last frame… and it would be properly cached
    // temporarily." The player + surface now stay composed in the ENDED
    // phase too: the 30s cap PAUSES playback (a paused ExoPlayer holds its
    // current frame on the surface — the "temporary cache"), the caption
    // bar swaps to the success line, and everything releases when the card
    // leaves composition. Only the FAILED path collapses to the quiet strip
    // (there is no frame to hold).
    if (phase != StreamPreviewPhase.FAILED) {
        // The player object lives while the preview is on screen — PLAYING
        // or paused-at-its-last-frame (ENDED). When the card leaves
        // composition, the DisposableEffect below releases the player, the
        // surface and every buffer.
        val player = remember(url) {
            // The stream's own request headers (Referer / User-Agent / the flat
            // map the capture kept) ride a dedicated DefaultHttpDataSource — the
            // same contract the StreamPlayTest used to prove the bytes flow.
            val requestHeaders = buildMap {
                userAgent?.let { put("User-Agent", it) }
                referer?.let { put("Referer", it) }
                headers?.forEach { (name, value) ->
                    if (!name.equals("Range", ignoreCase = true) &&
                        !name.equals("User-Agent", ignoreCase = true) &&
                        !name.equals("Referer", ignoreCase = true)
                    ) {
                        put(name, value)
                    }
                }
            }
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(10_000)
                .setReadTimeoutMs(15_000)
                .setDefaultRequestProperties(requestHeaders)
            // ExoPlayer.Builder takes a MediaSource.Factory (not a raw
            // DataSource.Factory) — DefaultMediaSourceFactory wraps it so the
            // stream's own request headers ride every request.
            ExoPlayer.Builder(context, DefaultMediaSourceFactory(dataSourceFactory))
                .build()
                .apply {
                    volume = 0f
                    // D-598: NO looping — a capped preview ends, and a stream
                    // that finishes before the cap reports success honestly.
                    repeatMode = Player.REPEAT_MODE_OFF
                    playWhenReady = true
                    setMediaItem(MediaItem.fromUri(url))
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED) {
                                phase = StreamPreviewPhase.ENDED
                            }
                        }

                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            phase = StreamPreviewPhase.FAILED
                        }
                    })
                    prepare()
                }
        }
        // THE 30-SECOND CAP — a one-second countdown, then playback PAUSES
        // (the frame stays on the surface — D-656's last-frame keep) and the
        // caption reports success. Nothing plays past the cap.
        LaunchedEffect(url) {
            while (phase == StreamPreviewPhase.PLAYING && secondsLeft > 0) {
                delay(1_000)
                if (phase == StreamPreviewPhase.PLAYING) secondsLeft--
            }
            if (phase == StreamPreviewPhase.PLAYING) {
                player.pause()
                secondsLeft = 0
                phase = StreamPreviewPhase.ENDED
            }
        }
        DisposableEffect(url) {
            onDispose {
                player.stop()
                player.release()
            }
        }
        Surface(
            color = Color.Black.copy(alpha = 0.85f),
            shape = RoundedCornerShape(10.dp),
            modifier = modifier.fillMaxWidth(),
        ) {
            Column {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            this.player = player
                        }
                    },
                    // D-598: the surface detaches BEFORE the player releases
                    // — no leaked texture buffers, no dangling view reference.
                    onRelease = { view -> view.player = null },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                ) {
                    if (phase == StreamPreviewPhase.PLAYING) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFB1F256)),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "LIVE PREVIEW · muted · ${secondsLeft}s left",
                            fontFamily = RobotoFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFB1F256),
                        )
                    } else {
                        // D-656: the success caption — playback paused at the
                        // cap, the very last frame held on the surface.
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFFB1F256),
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Stream played successfully · last frame held",
                            fontFamily = RobotoFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFB1F256),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    } else {
        // THE FAILED STRIP — the player never delivered a frame, so there is
        // nothing to hold: one quiet row (D-622's language; only the error
        // path collapses now — D-656).
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            shape = RoundedCornerShape(10.dp),
            modifier = modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = if (phase == StreamPreviewPhase.ENDED) {
                        Icons.Filled.CheckCircle
                    } else {
                        Icons.Filled.Cancel
                    },
                    contentDescription = null,
                    tint = if (phase == StreamPreviewPhase.ENDED) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (phase == StreamPreviewPhase.ENDED) {
                        "Stream played successfully"
                    } else {
                        "Preview unavailable — the verdict came from the stream's data"
                    },
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (phase == StreamPreviewPhase.ENDED) {
                        "capped at ${STREAM_PREVIEW_CAP_S}s"
                    } else {
                        "no playback"
                    },
                    fontFamily = RobotoFamily,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
