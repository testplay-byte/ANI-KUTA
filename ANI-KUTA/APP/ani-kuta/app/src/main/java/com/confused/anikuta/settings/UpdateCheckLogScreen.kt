package com.confused.anikuta.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.confused.anikuta.core.designsystem.component.BackAction
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.UpdateMode
import com.confused.anikuta.core.updates.UpdateCheckLogEntry
import com.confused.anikuta.core.updates.UpdateCheckItemLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Task 64 (round 24 — the content-update HISTORY page) — D-388 (round 25):
 * the FULL rework per the device round's spec.
 *
 * Round-25 asks mapped here:
 *  - "It should show me in the 12-hour format and according to my device's
 *    time" → every timestamp renders via the DEVICE's 12/24-hour setting
 *    ([formatDeviceTime]).
 *  - "At the very top it should show me one special entry. That will be the
 *    one which will tell when it will check for the next one. It will show
 *    the proper timer for when it will initiate the next search, on which
 *    content it will initiate the search on, and how it will initiate" →
 *    the pinned [NextCheckCard]: a live 1-second countdown, the exact
 *    WorkManager fire time, the anime queued for that check (with covers),
 *    and the "how" (automatic WorkManager job / manual / off).
 *  - "If it does find any results of updates then it should show me the
 *    proper results there too… it should show me the cover images there too
 *    when I click on them" → every per-content row carries the anime's
 *    cover (captured at check time).
 *  - "Clicking it does not lead me to the Details page" → every item row
 *    (and the due-preview rows of the next-check card) navigates to the
 *    anime's Details page.
 *  - "The whole history in scrollable formatting" → unchanged LazyColumn.
 *
 * Reachable from Settings → Updates → "Update check history", from the
 * Debug options page, and by tapping a check-results notification.
 */
@Composable
fun UpdateCheckLogScreen(
    onBack: () -> Unit,
    onNavigateToDetails: (mainId: String) -> Unit = {},
    viewModel: UpdateCheckLogViewModel = koinViewModel(),
) {
    val sessions by viewModel.sessions.collectAsState()
    val nextCheck by viewModel.nextCheck.collectAsState()
    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Update check history",
                collapsed = collapsed,
                actions = { BackAction(onBack) },
            )
            Box(modifier = Modifier.fillMaxSize()) {
                if (sessions.isEmpty() && nextCheck == null) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            "No update checks recorded yet",
                            fontFamily = RobotoFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Every episode check lands here — when it ran, what it checked, " +
                                "what it found, and what it decided to do next.",
                            fontFamily = RobotoFamily,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 12.dp, end = 12.dp, top = 4.dp, bottom = 110.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // D-388: the pinned NEXT-CHECK entry (the round-25
                        // "special entry at the very top").
                        nextCheck?.let { info ->
                            item(key = "next-check-card") {
                                NextCheckCard(info = info, onNavigateToDetails = onNavigateToDetails)
                            }
                        }
                        item(key = "history-label") { SettingsSectionLabel("History") }
                        items(sessions.size, key = { sessions[it].id }) { index ->
                            CheckSessionCard(sessions[index], onNavigateToDetails)
                        }
                    }
                }
                ScrollBlurOverlay(
                    scrollOffset = {
                        if (lazyListState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                        else lazyListState.firstVisibleItemScrollOffset.toFloat()
                    },
                    backgroundColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
//  D-388 (round 25): the pinned NEXT-CHECK card
// ══════════════════════════════════════════════════════════════════════════

/**
 * The "special entry" the round-25 spec asked for at the very top of the
 * history: when the next check fires (LIVE countdown + the scheduled time in
 * the device's clock), WHICH anime it will search (with covers, tappable to
 * their Details pages), and HOW it will initiate.
 */
@Composable
private fun NextCheckCard(
    info: NextCheckInfo,
    onNavigateToDetails: (String) -> Unit,
) {
    // The live countdown — one tick per second, recomposition-local (only
    // this card recomposes; the big history list below is untouched).
    var nowTick by remember(info.nextCheckAt) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(info.nextCheckAt) {
        while (true) {
            nowTick = System.currentTimeMillis()
            delay(1_000)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // ── Header: the "NEXT CHECK" label + mode icon ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, iconTint) = when (info.mode) {
                    UpdateMode.AUTO -> Icons.Filled.Autorenew to MaterialTheme.colorScheme.primary
                    UpdateMode.MANUAL -> Icons.Filled.TouchApp to MaterialTheme.colorScheme.primary
                    UpdateMode.OFF -> Icons.Filled.PauseCircle to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "NEXT CHECK",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))

            if (info.nextCheckAt != null) {
                // ── The live countdown (the "proper timer") ──
                val remaining = (info.nextCheckAt - nowTick).coerceAtLeast(0)
                Text(
                    text = formatCountdown(remaining),
                    fontFamily = RobotoFamily,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = formatDeviceTime(info.nextCheckAt),
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = when (info.mode) {
                        UpdateMode.MANUAL -> "Manual — you start the checks"
                        UpdateMode.OFF -> "Update checks are OFF"
                        else -> "Not scheduled"
                    },
                    fontFamily = RobotoFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // ── HOW it will initiate (the "how it will initiate and such") ──
            Spacer(Modifier.height(8.dp))
            Text(
                text = when (info.mode) {
                    UpdateMode.AUTO ->
                        "Automatic — a WorkManager periodic job${info.intervalHours?.let { " every ${it}h" } ?: ""}, " +
                            "runs on network + battery-not-low"
                    UpdateMode.MANUAL ->
                        "Manual — pull-to-refresh on the Updates tab, filtered to your selected categories"
                    UpdateMode.OFF ->
                        "No background job is scheduled — enable Updates in Settings to resume checking"
                },
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ── WHICH content the next search will cover ──
            if (info.due.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Will check ${info.due.size} anime" +
                        (if (info.dueTotal > info.due.size) " (+${info.dueTotal - info.due.size} more)" else ""),
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                info.due.forEach { preview ->
                    DuePreviewRow(preview, onNavigateToDetails)
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

/** One queued-for-next-check anime row — cover + title, tappable to Details. */
@Composable
private fun DuePreviewRow(
    preview: DueAnimePreview,
    onNavigateToDetails: (String) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onNavigateToDetails(preview.mainId) }
            .padding(vertical = 2.dp, horizontal = 2.dp),
    ) {
        if (preview.coverUrl != null) {
            AsyncImage(
                model = preview.coverUrl,
                contentDescription = preview.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 28.dp, height = 40.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
        } else {
            Box(
                modifier = Modifier.size(width = 28.dp, height = 40.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = preview.title,
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════
//  The history session cards
// ══════════════════════════════════════════════════════════════════════════

/** One session card — tap to expand the per-content outcomes. */
@Composable
private fun CheckSessionCard(
    entry: UpdateCheckLogEntry,
    onNavigateToDetails: (String) -> Unit,
) {
    var expanded by remember(entry.id) { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatDeviceTime(entry.startedAt),
                        fontFamily = RobotoFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = buildString {
                            append(entry.trigger)
                            append(" · ")
                            append("${entry.totalChecked} checked")
                            if (entry.totalNewEpisodes > 0) {
                                append(" · ${entry.totalNewEpisodes} new")
                            }
                            append(" · ${formatDuration(entry.finishedAt - entry.startedAt)}")
                        },
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutcomeDot(success = entry.success, outcome = null)
            }

            if (!entry.success) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Failed: ${entry.error ?: "unknown error"}",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (entry.items.isEmpty() && entry.success) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (entry.totalChecked == 0) {
                        "Nothing was due for a check"
                    } else {
                        "No per-content details recorded"
                    },
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // A collapsed hint when there are per-content details to expand.
            if (entry.items.isNotEmpty() && !expanded) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Tap for ${entry.items.size} content result${if (entry.items.size == 1) "" else "s"}",
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    entry.items.forEachIndexed { itemIndex, item ->
                        if (itemIndex > 0) Spacer(Modifier.height(8.dp))
                        CheckItemRow(item, onNavigateToDetails)
                    }
                }
            }
        }
    }
}

/**
 * One per-content outcome row: cover, title, outcome chip, detail, next
 * action. D-388 (round 25): the COVER renders (captured at check time) and
 * the row navigates to the anime's Details page.
 */
@Composable
private fun CheckItemRow(
    item: UpdateCheckItemLog,
    onNavigateToDetails: (String) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onNavigateToDetails(item.mainId) }
            .padding(vertical = 2.dp),
    ) {
        if (item.coverUrl != null) {
            AsyncImage(
                model = item.coverUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 40.dp, height = 56.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
        } else {
            // Legacy entries (pre-round-25 JSON) have no cover stored.
            Box(
                modifier = Modifier.size(width = 40.dp, height = 56.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.detail,
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Next: ${item.nextAction}",
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(8.dp))
        OutcomeChip(item.outcome, item.newEpisodes)
    }
}

/** The outcome pill, color-coded: new = primary, failed = error, else muted. */
@Composable
private fun OutcomeChip(outcome: String, newEpisodes: Int) {
    val (label, container, content) = when (outcome) {
        "new-episodes" -> Triple(
            if (newEpisodes > 0) "+$newEpisodes EP" else "NEW",
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimary,
        )
        "failed", "source-unavailable" -> Triple(
            outcome,
            MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
            MaterialTheme.colorScheme.error,
        )
        "skipped" -> Triple(
            outcome,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> Triple(  // no-new-episodes
            "no new",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Surface(
        color = container,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = content,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** The small success/fail indicator dot on the session header. */
@Composable
private fun OutcomeDot(success: Boolean, outcome: String?) {
    val color = if (success) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(color, CircleShape),
    )
}

// ── Time formatting (D-388: the DEVICE's 12/24-hour setting) ──

/**
 * D-388 (round 25 — "it should show me in the 12-hour format and according
 * to my device's time"): the pattern follows the DEVICE's system clock
 * setting (Settings → System → Date & time → Use 24-hour format), rendered
 * in the device locale.
 */
@Composable
fun formatDeviceTime(epochMs: Long): String {
    val context = LocalContext.current
    val is24h = android.text.format.DateFormat.is24HourFormat(context)
    val pattern = if (is24h) "EEE, MMM d · HH:mm" else "EEE, MMM d · h:mm a"
    return remember(epochMs, is24h) {
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(epochMs))
    }
}

/** The live countdown text: "2h 14m 06s" (compact, monospace-stable width). */
private fun formatCountdown(ms: Long): String {
    val totalSeconds = ms / 1000
    val days = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return when {
        days > 0 -> "${days}d ${hours}h ${minutes}m"
        hours > 0 -> "${hours}h ${minutes}m ${seconds.toString().padStart(2, '0')}s"
        minutes > 0 -> "${minutes}m ${seconds.toString().padStart(2, '0')}s"
        else -> "${seconds}s"
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000L).coerceAtLeast(0)
    return if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s"
}

// ══════════════════════════════════════════════════════════════════════════
//  The ViewModel (D-388: extended with the next-check projection)
// ══════════════════════════════════════════════════════════════════════════

/** One anime queued for the next check (title + cover for the next-check card). */
data class DueAnimePreview(
    val mainId: String,
    val title: String,
    val coverUrl: String?,
)

/**
 * D-388 (round 25): everything the pinned next-check card renders.
 *
 * [nextCheckAt] prefers WorkManager's REAL next fire time for the periodic
 * job ([com.confused.anikuta.core.updates.UpdateCheckWorker]'s unique work —
 * `nextScheduleTimeMillis`), falling back to the LAST logged session's
 * projection (the engine writes it into the history entry). Null = manual
 * mode / off / nothing scheduled.
 */
data class NextCheckInfo(
    val mode: UpdateMode,
    val intervalHours: Long?,
    val nextCheckAt: Long?,
    /** The anime due for the next check — [due] shows up to 3, [dueTotal] is all. */
    val due: List<DueAnimePreview>,
    val dueTotal: Int,
)

/**
 * The history screen's VM — loads the store's sessions (newest first) + the
 * next-check projection. Registered in :app's appModule.
 */
class UpdateCheckLogViewModel(
    private val store: UpdateCheckLogStore,
    private val updateStore: com.confused.anikuta.core.updates.UpdateStore,
    private val contentRepository: com.confused.anikuta.core.content.ContentRepository,
    private val updatePreferences: com.confused.anikuta.core.preferences.UpdatePreferences,
    private val appContext: android.content.Context,
) : ViewModel() {

    private val _sessions = MutableStateFlow<List<UpdateCheckLogEntry>>(emptyList())
    val sessions: StateFlow<List<UpdateCheckLogEntry>> = _sessions.asStateFlow()

    private val _nextCheck = MutableStateFlow<NextCheckInfo?>(null)
    val nextCheck: StateFlow<NextCheckInfo?> = _nextCheck.asStateFlow()

    companion object {
        /** How many due-anime preview rows the card shows. */
        private const val DUE_PREVIEW_ROWS = 3
    }

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _sessions.value = store.sessions()
            _nextCheck.value = buildNextCheck(_sessions.value.firstOrNull())
        }
    }

    /**
     * Builds the next-check projection:
     *  - AUTO → WorkManager's nextScheduleTimeMillis for the periodic job
     *    (the REAL fire time), falling back to the latest session's logged
     *    projection, falling back to now + interval;
     *  - MANUAL/OFF → null (the card explains the mode instead).
     *
     * The due anime are whatever [updateStore.getDueAnime] returns for the
     * next fire time (mirroring the engine's own due set, dub-completed
     * union included) — exactly "on which content it will initiate the
     * search on".
     */
    private suspend fun buildNextCheck(latest: UpdateCheckLogEntry?): NextCheckInfo? {
        return runCatching {
            val mode = updatePreferences.getMode()
            val interval = updatePreferences.getIntervalHours()

            val nextCheckAt: Long? = when (mode) {
                UpdateMode.OFF -> null
                UpdateMode.MANUAL -> null
                UpdateMode.AUTO -> {
                    val workNext = queryWorkManagerNextRun()
                    when {
                        workNext != null && workNext > 0L -> workNext
                        latest?.nextCheckAt != null && latest.nextCheckAt > System.currentTimeMillis() ->
                            latest.nextCheckAt
                        else -> System.currentTimeMillis() + interval * 3_600_000L
                    }
                }
            }

            // The due set at the next fire time — what the next search covers.
            val horizon = nextCheckAt ?: System.currentTimeMillis()
            val dueStates = updateStore.getDueAnime(horizon) +
                updateStore.getDueDubAnime(horizon)
            val distinct = dueStates.distinctBy { it.mainId }

            val previews = distinct
                .take(DUE_PREVIEW_ROWS + 3) // small over-fetch, then cap for display
                .mapNotNull { state ->
                    val content = contentRepository.getMainEntryByMainId(state.mainId) ?: return@mapNotNull null
                    val details = runCatching {
                        contentRepository.getContentDetails(state.mainId)
                    }.getOrNull()
                    DueAnimePreview(
                        mainId = state.mainId,
                        title = content.title,
                        coverUrl = details?.dataCoverUrl ?: details?.extThumbnailUrl,
                    )
                }
                .take(DUE_PREVIEW_ROWS)

            NextCheckInfo(
                mode = mode,
                intervalHours = interval,
                nextCheckAt = nextCheckAt,
                due = previews,
                dueTotal = distinct.size,
            )
        }.getOrNull()
    }

    /**
     * WorkManager's REAL next periodic fire time (null when the API or the
     * work isn't available). `nextScheduleTimeMillis` exists since
     * work-runtime 2.8 (we pin 2.10).
     */
    private fun queryWorkManagerNextRun(): Long? {
        return runCatching {
            val wm = androidx.work.WorkManager.getInstance(appContext)
            val infos = wm.getWorkInfosForUniqueWork(
                com.confused.anikuta.core.updates.UpdateCheckWorker.PERIODIC_WORK_NAME,
            ).get()
            infos.firstOrNull {
                it.state == androidx.work.WorkInfo.State.ENQUEUED ||
                    it.state == androidx.work.WorkInfo.State.RUNNING
            }?.nextScheduleTimeMillis
        }.getOrNull()
    }
}
