package com.confused.anikuta.feature.updates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import kotlinx.coroutines.launch
import java.util.Calendar

// A soft "lime blue" — harmonizes with the lime-green primary.
private val LimeBlue = Color(0xFF6EC6E6)
private val LimeBlueFg = Color(0xFF0A2A33)

/**
 * The monthly calendar view for the Schedule tab.
 *
 * Adapted from the old project's ScheduleCalendar.kt — same visual design
 * (HorizontalPager, 14 pages = 1mo back + current + 12mo forward, day cells
 * with multi-dot indicators, day-detail bottom sheet).
 *
 * Uses the new project's ScheduleDisplay type (scheduledAt + actualAt).
 *
 * Limits: 1 month back, 1 year forward (hard, per user spec).
 * Neutral "Can't go further" messages (not the old project's snarky ones).
 */
@Composable
fun ScheduleCalendarContent(
    entries: List<ScheduleDisplay>,
    onNavigateToDetails: (String) -> Unit = {},
) {
    // Index entries by "yyyy-MM-dd" for O(1) day-cell lookup.
    val byDay = remember(entries) {
        entries.groupBy { entry ->
            val cal = Calendar.getInstance().apply { timeInMillis = entry.scheduledAt }
            "%04d-%02d-%02d".format(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH),
            )
        }
    }

    val pageCount = 14 // 1 back + current + 12 ahead
    val initialPage = 1
    val minPage = 0
    val maxPage = 13
    val pagerState = rememberPagerState(initialPage = initialPage) { pageCount }
    val scope = rememberCoroutineScope()

    var selectedDay by remember { mutableStateOf<String?>(null) }
    var limitMessage by remember { mutableStateOf<String?>(null) }

    fun pageToMonth(page: Int): Calendar {
        val offset = page - initialPage
        return Calendar.getInstance().apply {
            add(Calendar.MONTH, offset)
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }

    // Auto-dismiss the limit message after 3s.
    LaunchedEffect(limitMessage) {
        if (limitMessage != null) {
            kotlinx.coroutines.delay(3000L)
            limitMessage = null
        }
    }

    val displayedMonth = pageToMonth(pagerState.currentPage)

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: ‹ Month Year ›
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    if (pagerState.currentPage <= minPage) {
                        limitMessage = "Can't go further back than 1 month"
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    }
                }) {
                    Icon(Icons.Filled.ChevronLeft, "Previous month", tint = MaterialTheme.colorScheme.onSurface)
                }
                val fmt = remember { java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()) }
                Text(
                    text = fmt.format(displayedMonth.time),
                    fontFamily = RobotoFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    if (pagerState.currentPage >= maxPage) {
                        limitMessage = "Can't go further forward than 1 year"
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                }) {
                    Icon(Icons.Filled.ChevronRight, "Next month", tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            // Weekday header
            val letters = remember { listOf("S", "M", "T", "W", "T", "F", "S") }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                letters.forEach {
                    Text(
                        text = it,
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Dynamic height based on the displayed month's week count.
            val displayedWeekCount = remember(displayedMonth) { weeksInMonth(displayedMonth) }
            androidx.compose.foundation.layout.BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
            ) {
                val cardWidthPx = maxWidth.value
                val totalSpacingPx = 6f * 4f
                val cellSizePx = (cardWidthPx - totalSpacingPx) / 7f
                val rowCount = displayedWeekCount.coerceIn(5, 6)
                val gridHeightPx = cellSizePx * rowCount + (rowCount - 1) * 4f

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().height(gridHeightPx.dp),
                ) { page ->
                    val month = pageToMonth(page)
                    MonthGrid(
                        month = month,
                        byDay = byDay,
                        selectedDay = selectedDay,
                        onSelectDay = { selectedDay = it },
                    )
                }
            }
        }
    }

    // Limit message overlay
    androidx.compose.animation.AnimatedVisibility(
        visible = limitMessage != null,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)),
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(400)),
        modifier = Modifier.fillMaxSize(),
    ) {
        val msg = limitMessage
        if (msg != null) {
            Box(
                modifier = Modifier.fillMaxSize().clickable { limitMessage = null },
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = msg,
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onError,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }

    // Day-detail bottom sheet
    if (selectedDay != null) {
        val dayEntries = byDay[selectedDay].orEmpty()
        CalendarDaySheet(
            dayKey = selectedDay!!,
            entries = dayEntries,
            onOpenAnime = { mainId -> onNavigateToDetails(mainId) },
            onDismiss = { selectedDay = null },
        )
    }
}

@Composable
private fun MonthGrid(
    month: Calendar,
    byDay: Map<String, List<ScheduleDisplay>>,
    selectedDay: String?,
    onSelectDay: (String) -> Unit,
) {
    val weeks = remember(month) { buildMonthWeeks(month) }
    val todayKey = remember {
        val cal = Calendar.getInstance()
        "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        weeks.forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                week.forEach { dayInfo ->
                    if (dayInfo == null) {
                        Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val dayEntries = byDay[dayInfo.key].orEmpty()
                        DayCell(
                            dayInfo = dayInfo,
                            entries = dayEntries,
                            isToday = dayInfo.key == todayKey,
                            isSelected = dayInfo.key == selectedDay,
                            onTap = { onSelectDay(dayInfo.key) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    dayInfo: DayInfo,
    entries: List<ScheduleDisplay>,
    isToday: Boolean,
    isSelected: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasEpisodes = entries.isNotEmpty()
    val cellColor = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        isToday -> LimeBlue.copy(alpha = 0.22f)
        hasEpisodes -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
    }
    val borderColor = when {
        isToday -> LimeBlue.copy(alpha = 0.7f)
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        hasEpisodes -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    }
    Surface(
        color = cellColor,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.aspectRatio(1f).border(1.dp, borderColor, RoundedCornerShape(8.dp)).clickable(onClick = onTap),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Arrangement.Center,
        ) {
            Text(
                text = dayInfo.dayOfMonth.toString(),
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = if (hasEpisodes || isToday) FontWeight.ExtraBold else FontWeight.Normal,
                color = when {
                    isToday -> LimeBlueFg
                    hasEpisodes -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            if (hasEpisodes) {
                Spacer(modifier = Modifier.height(2.dp))
                // Simple dot count indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
                ) {
                    entries.take(4).forEach {
                        Box(Modifier.size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    }
                    if (entries.size > 4) {
                        Text(
                            text = "+${entries.size - 4}",
                            fontFamily = RobotoFamily,
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarDaySheet(
    dayKey: String,
    entries: List<ScheduleDisplay>,
    onOpenAnime: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = formatSheetHeader(dayKey),
                fontFamily = RobotoFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            if (entries.isEmpty()) {
                Text(
                    text = "No episodes airing this day.",
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                entries.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onOpenAnime(entry.mainId) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (entry.coverUrl != null) {
                            AsyncImage(
                                model = entry.coverUrl,
                                contentDescription = entry.animeTitle,
                                modifier = Modifier.size(width = 40.dp, height = 56.dp).clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(Modifier.size(width = 40.dp, height = 56.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.animeTitle,
                                fontFamily = RobotoFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "EP ${entry.episodeNumber} · ${formatAirTime(entry.scheduledAt)}",
                                fontFamily = RobotoFamily,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Helpers ──

private fun formatSheetHeader(dayKey: String): String {
    val parts = dayKey.split("-")
    if (parts.size != 3) return dayKey
    val cal = Calendar.getInstance().apply { clear(); set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt()) }
    val fmt = java.text.SimpleDateFormat("EEEE, MMM d", java.util.Locale.getDefault())
    return fmt.format(cal.time)
}

private fun formatAirTime(epochMs: Long): String {
    val fmt = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
    return fmt.format(java.util.Date(epochMs))
}

private fun weeksInMonth(month: Calendar): Int = buildMonthWeeks(month).size

private fun buildMonthWeeks(month: Calendar): List<List<DayInfo?>> {
    val cal = (month.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val cells = mutableListOf<DayInfo?>()
    repeat(firstDayOfWeek) { cells.add(null) }
    for (day in 1..daysInMonth) {
        cells.add(DayInfo(
            dayOfMonth = day,
            key = "%04d-%02d-%02d".format(month.get(Calendar.YEAR), month.get(Calendar.MONTH) + 1, day),
        ))
    }
    while (cells.size % 7 != 0) cells.add(null)
    return cells.chunked(7)
}

private data class DayInfo(val dayOfMonth: Int, val key: String)
