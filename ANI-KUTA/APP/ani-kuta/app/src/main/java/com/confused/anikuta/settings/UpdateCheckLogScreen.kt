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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.designsystem.component.BackAction
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.updates.UpdateCheckLogEntry
import com.confused.anikuta.core.updates.UpdateCheckItemLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Task 64 (round 24 — the content-update HISTORY page): "keep track of when
 * the app actually checked for updates… a dedicated option to check out the
 * updates log, like a content update history… based on which content it
 * started the search, on which content, whether it was successful or not,
 * what was the next probable action which it thought of taking, and all
 * other stuff like that."
 *
 * One card per check session (newest first): when it ran, what triggered it,
 * how many anime were checked / new episodes found / how long it took — tap
 * to expand the per-content outcomes (title, outcome chip, detail, the
 * engine's next action).
 *
 * Data comes from [UpdateCheckLogStore] (the JSON file the engine's logger
 * writes). Reachable from Settings → Updates → "Update check history".
 */
@Composable
fun UpdateCheckLogScreen(
    onBack: () -> Unit,
    viewModel: UpdateCheckLogViewModel = koinViewModel(),
) {
    val sessions = viewModel.sessions
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
                if (sessions.isEmpty()) {
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
                        items(sessions.size, key = { sessions[it].id }) { index ->
                            CheckSessionCard(sessions[index])
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

/** One session card — tap to expand the per-content outcomes. */
@Composable
private fun CheckSessionCard(entry: UpdateCheckLogEntry) {
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
                        text = formatSessionTime(entry.startedAt),
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

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    entry.items.forEachIndexed { itemIndex, item ->
                        if (itemIndex > 0) Spacer(Modifier.height(8.dp))
                        CheckItemRow(item)
                    }
                }
            }
        }
    }
}

/** One per-content outcome row: title, outcome chip, detail, next action. */
@Composable
private fun CheckItemRow(item: UpdateCheckItemLog) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.title,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            OutcomeChip(item.outcome, item.newEpisodes)
        }
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

private fun formatSessionTime(epochMs: Long): String {
    val fmt = SimpleDateFormat("EEE, MMM d · HH:mm", Locale.getDefault())
    return fmt.format(Date(epochMs))
}

private fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000L).coerceAtLeast(0)
    return if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s"
}

/**
 * The history screen's VM — loads the store's sessions (newest first) on
 * init + on refresh. Deliberately tiny: the store does all the work.
 * Registered via viewModelOf in :app's appModule.
 */
class UpdateCheckLogViewModel(
    private val store: UpdateCheckLogStore,
) : ViewModel() {

    private val _sessions = MutableStateFlow<List<UpdateCheckLogEntry>>(emptyList())
    val sessions: StateFlow<List<UpdateCheckLogEntry>> = _sessions.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _sessions.value = store.sessions()
        }
    }
}
