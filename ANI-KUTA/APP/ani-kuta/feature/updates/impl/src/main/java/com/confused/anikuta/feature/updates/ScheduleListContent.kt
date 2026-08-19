package com.confused.anikuta.feature.updates

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import org.koin.compose.viewmodel.koinViewModel
import java.util.concurrent.TimeUnit

/**
 * Schedule list view (Phase SC — PLAN §5.2).
 *
 * Day-grouped chronological (ascending): Today / Tomorrow / EEE, MMM d.
 * Row: cover + title + "EP N" pill + countdown ("in 14h 36m" for Today/Tomorrow).
 * Live-ticking countdown via 1s LaunchedEffect.
 *
 * This is the schedule content — embedded in the UpdatesScreen via the Updates | Schedule
 * tab strip. A List / Calendar toggle switches between this list view and the monthly
 * calendar ([ScheduleCalendarContent] — HorizontalPager, day cells, day-detail sheet).
 * Auto-fetches airing data once on first open if the DB is empty.
 */
@Composable
fun ScheduleListContent(
    onNavigateToDetails: (String) -> Unit = {},
    viewModel: ScheduleViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fetching by viewModel.fetching.collectAsStateWithLifecycle()
    var calendarView by remember { androidx.compose.runtime.mutableStateOf(false) }
    // Tick every second for the live countdown.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }

    // Auto-fetch schedule data once if the DB is empty on first open — otherwise
    // the list/calendar stays empty until the user manually taps refresh.
    var autoFetched by remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(state) {
        if (!autoFetched && state is ScheduleUiState.Loaded &&
            (state as ScheduleUiState.Loaded).groups.isEmpty() && !fetching
        ) {
            autoFetched = true
            viewModel.fetchSchedule()
        }
    }

    // "Today" button trigger counter — increments when the user taps the Today
    // button; ScheduleCalendarContent observes it + animates the pager to today.
    var scrollToTodayRequest by remember { androidx.compose.runtime.mutableStateOf(0) }

    // Toggle + content wrapped in a Column so the toggle sits ABOVE the content.
    // Previously these were siblings emitted into the parent Box — the fillMaxSize
    // list/calendar drew ON TOP of the toggle (a Box stacks later children above
    // earlier ones), hiding it. That was the "can't click the calendar button" bug.
    Column(modifier = Modifier.fillMaxSize()) {
        // View toggle bar — styled to match the Updates | Schedule pill (same
        // container Surface + per-tab Surface). When in calendar view, the pill
        // shrinks to the left (weight 1f) and a "Today" button appears on the right.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ── List / Calendar pill (matches the Updates | Schedule tab strip) ──
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf("List" to false, "Calendar" to true).forEach { (label, isCal) ->
                        val isSelected = calendarView == isCal
                        val bgColor by androidx.compose.animation.animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                            else androidx.compose.ui.graphics.Color.Transparent,
                            animationSpec = androidx.compose.animation.core.tween(200),
                            label = "schedView_$isCal",
                        )
                        val textColor by androidx.compose.animation.animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = androidx.compose.animation.core.tween(200),
                            label = "schedText_$isCal",
                        )
                        Surface(
                            color = bgColor,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).clickable { calendarView = isCal },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (isCal) Icons.Filled.CalendarMonth
                                    else Icons.AutoMirrored.Filled.List,
                                    contentDescription = null,
                                    tint = textColor,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = label,
                                    color = textColor,
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }

            // ── "Today" button — only in calendar view. Jumps the pager to today. ──
            if (calendarView) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.clickable { scrollToTodayRequest++ },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Today,
                            contentDescription = "Today",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Today",
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        // Content fills the remaining space below the toggle.
        Box(modifier = Modifier.fillMaxSize()) {
            if (calendarView) {
                // Calendar view
                when (val s = state) {
                    is ScheduleUiState.Loading -> {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text("Loading…", fontFamily = RobotoFamily, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    is ScheduleUiState.Loaded -> {
                        val allEntries = s.groups.flatMap { it.entries }
                        // verticalScroll so the calendar + hint are never cut off on short screens.
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            ScheduleCalendarContent(
                                entries = allEntries,
                                onNavigateToDetails = onNavigateToDetails,
                                scrollToTodayRequest = scrollToTodayRequest,
                            )
                            if (allEntries.isEmpty()) {
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = "No upcoming episodes scheduled yet.\nTap refresh to fetch airing data from AniList.",
                                    fontFamily = RobotoFamily,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                                )
                            }
                        }
                    }
                }
            } else {
                // List view
                when (val s = state) {
                    is ScheduleUiState.Loading -> {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text("Loading…", fontFamily = RobotoFamily, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    is ScheduleUiState.Loaded -> {
                        if (s.groups.isEmpty()) {
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("No upcoming episodes", fontFamily = RobotoFamily, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(Modifier.height(8.dp))
                                    Text("Tap the refresh button to fetch airing data from AniList.", fontFamily = RobotoFamily, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 110.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                s.groups.forEach { group ->
                                    item(key = "header_${group.label}") {
                                        Text(
                                            text = group.label,
                                            fontFamily = RobotoFamily,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
                                        )
                                    }
                                    // D-237: Include the entry id in the key to prevent
                                    // duplicate-key crashes when two schedule entries have
                                    // the same mainId + episodeNumber (e.g., different audio variants).
                                    items(group.entries, key = { "${it.id}" }) { entry ->
                                        ScheduleRow(entry = entry, now = now, isAired = group.isAired, onClick = { onNavigateToDetails(entry.mainId) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleRow(
    entry: ScheduleDisplay,
    now: Long,
    isAired: Boolean = false,
    onClick: () -> Unit,
) {
    // D-193 Phase 6: Gray out entries that have already aired today.
    val contentAlpha = if (isAired) 0.4f else 1f
    Surface(
        color = if (isAired) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Cover
            if (entry.coverUrl != null) {
                AsyncImage(
                    model = entry.coverUrl,
                    contentDescription = entry.animeTitle,
                    modifier = Modifier.size(width = 56.dp, height = 80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .graphicsLayer(alpha = contentAlpha),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier.size(width = 56.dp, height = 80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .graphicsLayer(alpha = contentAlpha),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("EP ${entry.episodeNumber}", fontFamily = RobotoFamily, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            // Right column — content fills the full height of the cover (80dp).
            Column(
                modifier = Modifier.weight(1f).height(80.dp).graphicsLayer(alpha = contentAlpha),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Title (1 line)
                Text(
                    text = entry.animeTitle,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Bottom row: EP pill (left) + countdown with subtle background (right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        color = if (isAired) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = "EP ${entry.episodeNumber}",
                            fontFamily = RobotoFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isAired) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    // Countdown or "Aired" label
                    val displayTime = entry.actualAt ?: entry.scheduledAt
                    val diff = displayTime - now
                    val countdown = when {
                        isAired -> "Aired"
                        diff > 0 -> formatCountdown(diff)
                        else -> "Released"
                    }
                    Surface(
                        color = if (isAired) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        else if (diff > 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = countdown,
                            fontFamily = RobotoFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isAired) MaterialTheme.colorScheme.onSurfaceVariant
                            else if (diff > 0) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun formatCountdown(millis: Long): String {
    val days = TimeUnit.MILLISECONDS.toDays(millis)
    val hours = TimeUnit.MILLISECONDS.toHours(millis) % 24
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return when {
        days > 0 -> "in ${days}d ${hours}h"
        hours > 0 -> "in ${hours}h ${minutes}m ${seconds}s"
        minutes > 0 -> "in ${minutes}m ${seconds}s"
        else -> "in ${seconds}s"
    }
}
