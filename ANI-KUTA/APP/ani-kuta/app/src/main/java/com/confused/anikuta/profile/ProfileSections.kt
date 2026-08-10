package com.confused.anikuta.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.settings.SegmentedToggle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import kotlin.math.min

// ════════════════════════════════════════════════════════════════════════════
//  Watch Flow — tall bar chart with grid, y-axis, per-bar counts, today color,
//  and a floating right-side sidebar overlay with anime covers + duration.
// ════════════════════════════════════════════════════════════════════════════

private const val BARS_HEIGHT_DP = 128 // bars-area height (chart card minus the day-label row)
private const val BAR_WIDTH_DP = 30

@Composable
fun WatchFlowGraph(
    watchFlowByDay: List<Int>,
    watchFlowDetail: List<DayWatchSummary>,
    onNavigateToAnime: (Int) -> Unit,
) {
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val todayIdx = remember {
        val cal = Calendar.getInstance()
        ((cal.get(Calendar.DAY_OF_WEEK) - 2 + 7) % 7)
    }
    val maxVal = (watchFlowByDay.maxOrNull() ?: 0).coerceAtLeast(1)
    val primaryColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    var selectedDay by remember { mutableStateOf(-1) }

    val yLabels = remember(maxVal) { listOf(maxVal, maxVal / 2, 0) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "Watch Flow", fontFamily = RobotoFamily, fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    // Y-axis labels (max / mid / 0), aligned to the bars area
                    Column(
                        modifier = Modifier
                            .width(26.dp)
                            .height(BARS_HEIGHT_DP.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End,
                    ) {
                        yLabels.forEach { label ->
                            Text(
                                "$label", fontFamily = RobotoFamily, fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))

                    // Bars area: grid lines + bars, with floating sidebar overlay
                    Box(modifier = Modifier.weight(1f).height(BARS_HEIGHT_DP.dp)) {
                        // Grid lines (drawn behind bars)
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            // 3 horizontal grid lines: top (max), middle, bottom (0)
                            listOf(0f, 0.5f, 1f).forEach { frac ->
                                val y = h * (1f - frac)
                                drawLine(
                                    color = gridColor,
                                    start = Offset(0f, y),
                                    end = Offset(w, y),
                                    strokeWidth = 1f,
                                )
                            }
                        }

                        // Bars
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            watchFlowByDay.forEachIndexed { index, count ->
                                val isToday = index == todayIdx
                                val isSelected = index == selectedDay
                                val barHeightFraction = (count.toFloat() / maxVal).coerceIn(0.03f, 1f)
                                val barColor = when {
                                    isSelected -> primaryColor
                                    isToday -> primaryColor.copy(alpha = 0.75f)
                                    else -> primaryColor.copy(alpha = 0.25f + 0.4f * count.toFloat() / maxVal)
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(BAR_WIDTH_DP.dp),
                                ) {
                                    // Count label above each bar (always visible)
                                    Text(
                                        "$count", fontFamily = RobotoFamily,
                                        fontSize = if (isSelected || isToday) 11.sp else 9.sp,
                                        fontWeight = if (isSelected || isToday) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (isSelected || isToday) primaryColor
                                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(BAR_WIDTH_DP.dp)
                                            .height((barHeightFraction * (BARS_HEIGHT_DP - 20)).dp)
                                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                            .background(barColor)
                                            .clickable {
                                                selectedDay = if (selectedDay == index) -1 else index
                                            },
                                    )
                                }
                            }
                        }

                        // Floating sidebar overlay (right side) — shows when a bar is tapped.
                        val detail = selectedDay.takeIf { it >= 0 }?.let { watchFlowDetail.getOrNull(it) }
                        AnimatedVisibility(
                            visible = selectedDay >= 0 && detail != null,
                            enter = slideInHorizontally(animationSpec = tween(250)) { it } + fadeIn(tween(250)),
                            exit = slideOutHorizontally(animationSpec = tween(200)) { it } + fadeOut(tween(200)),
                            modifier = Modifier.align(Alignment.TopEnd),
                        ) {
                            if (detail != null) {
                                WatchFlowSidebar(
                                    dayName = days.getOrNull(selectedDay) ?: "",
                                    summary = detail,
                                    onOpenAnime = { anilistId ->
                                        selectedDay = -1
                                        onNavigateToAnime(anilistId)
                                    },
                                    onDismiss = { selectedDay = -1 },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                // Day labels row — below the bars, aligned under each bar column
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(32.dp)) // offset matching y-axis (26) + spacing (6)
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        days.forEachIndexed { index, label ->
                            val isToday = index == todayIdx
                            Text(
                                label, fontFamily = RobotoFamily,
                                fontSize = 10.sp,
                                fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Bold,
                                color = if (isToday) primaryColor
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(BAR_WIDTH_DP.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchFlowSidebar(
    dayName: String,
    summary: DayWatchSummary,
    onOpenAnime: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // Distinct themed background (primary-tinted) so the sidebar stands out from the chart card.
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        shadowElevation = 10.dp,
        modifier = Modifier.width(158.dp).height(BARS_HEIGHT_DP.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    dayName, fontFamily = RobotoFamily, fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier.size(20.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("×", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.ExtraBold)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "${summary.count} ep • ${formatDurationShort(summary.totalDurationSec)}",
                fontFamily = RobotoFamily, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            if (summary.items.isEmpty()) {
                Text(
                    "No anime recorded", fontFamily = RobotoFamily, fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(summary.items) { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                item.anilistId?.let { onOpenAnime(it) }
                            },
                        ) {
                            Box(
                                modifier = Modifier.size(width = 28.dp, height = 40.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            ) {
                                if (item.coverUrl != null) {
                                    AsyncImage(
                                        model = item.coverUrl,
                                        contentDescription = item.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.title, fontFamily = RobotoFamily, fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "EP ${item.episodeNumber}", fontFamily = RobotoFamily, fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDurationShort(seconds: Long): String {
    if (seconds <= 0) return "0m"
    val hours = seconds / 3600
    val mins = (seconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${mins}m"
        else -> "${mins}m"
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Time DNA — donut chart (stroke arcs), current period in center, legend
//  below donut, right side: recently-watched anime row (newest at far right).
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun TimeDnaCard(
    timeDna: TimeDnaData?,
    recentlyWatched: List<RecentlyWatchedItem>,
    onClick: () -> Unit,
) {
    if (timeDna == null) return

    val morning = (6..11).sumOf { timeDna.hourlyCounts[it] }
    val afternoon = (12..17).sumOf { timeDna.hourlyCounts[it] }
    val evening = (18..22).sumOf { timeDna.hourlyCounts[it] }
    val night = (23..23).sumOf { timeDna.hourlyCounts[it] } + (0..5).sumOf { timeDna.hourlyCounts[it] }
    val total = (morning + afternoon + evening + night).coerceAtLeast(1)

    // Theme-adjacent colors (warm → cool, matching time of day; evening = app primary lime).
    val morningColor = Color(0xFFFFB74D)    // warm orange — sunrise
    val afternoonColor = Color(0xFFFFE082)  // light amber — midday
    val eveningColor = Color(0xFFB1F256)    // app primary (lime) — dusk
    val nightColor = Color(0xFFECE6F5)      // soft white — night

    val periods = listOf(
        Triple("Morning", morning, morningColor),
        Triple("Afternoon", afternoon, afternoonColor),
        Triple("Evening", evening, eveningColor),
        Triple("Night", night, nightColor),
    )

    // Current period based on time of day
    val cal = Calendar.getInstance()
    val currentHour = cal.get(Calendar.HOUR_OF_DAY)
    val currentPeriodIdx = when (currentHour) {
        in 6..11 -> 0
        in 12..17 -> 1
        in 18..22 -> 2
        else -> 3
    }
    val currentPeriod = periods[currentPeriodIdx]

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "Time DNA", fontFamily = RobotoFamily, fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // ── Left: donut + legend below ──────────────────────────────────
                Column(modifier = Modifier.width(140.dp)) {
                    Box(modifier = Modifier.size(104.dp)) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val centerX = size.width / 2f
                            val centerY = size.height / 2f
                            val outerRadius = min(centerX, centerY) * 0.92f
                            val strokeWidth = outerRadius * 0.26f
                            var startAngle = -90f
                            periods.forEach { (_, count, color) ->
                                if (count > 0) {
                                    val fraction = count.toFloat() / total
                                    val sweepAngle = fraction * 360f
                                    drawArc(
                                        color = color,
                                        startAngle = startAngle,
                                        sweepAngle = sweepAngle,
                                        useCenter = false,
                                        topLeft = Offset(centerX - outerRadius, centerY - outerRadius),
                                        size = Size(outerRadius * 2, outerRadius * 2),
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                                    )
                                    startAngle += sweepAngle
                                }
                            }
                        }
                        // Center: current period color dot + name
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Surface(
                                color = currentPeriod.third,
                                shape = CircleShape,
                                modifier = Modifier.size(12.dp),
                            ) {}
                            Spacer(Modifier.height(2.dp))
                            Text(
                                currentPeriod.first, fontFamily = RobotoFamily, fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    // Legend below donut — 2x2 grid
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        periods.forEachIndexed { idx, (name, count, color) ->
                            val pct = if (total > 0) count * 100 / total else 0
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(color = color, shape = RoundedCornerShape(3.dp),
                                    modifier = Modifier.size(10.dp)) {}
                                Spacer(Modifier.width(6.dp))
                                Text("$pct%", fontFamily = RobotoFamily, fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.width(34.dp))
                                Text(name, fontFamily = RobotoFamily, fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // ── Right: recently-watched anime section (newest at far right) ──
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        "Recently Watched", fontFamily = RobotoFamily, fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${timeDna.totalSessions} sessions", fontFamily = RobotoFamily, fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(8.dp))
                    if (recentlyWatched.isEmpty()) {
                        Text(
                            "No anime yet", fontFamily = RobotoFamily, fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        // reverseLayout = true → first item (newest) appears at the far right,
                        // visible by default; scroll left to reveal older items.
                        LazyRow(
                            reverseLayout = true,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(recentlyWatched.take(6)) { item ->
                                Column(
                                    modifier = Modifier.width(58.dp),
                                ) {
                                    Box(
                                        modifier = Modifier.size(width = 58.dp, height = 82.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                    ) {
                                        if (item.coverUrl != null) {
                                            AsyncImage(
                                                model = item.coverUrl,
                                                contentDescription = item.title,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        item.title, fontFamily = RobotoFamily, fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "EP ${item.episodeNumber}", fontFamily = RobotoFamily, fontSize = 8.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Activity Heatmap — scrollable square cells, gray empty, left day markers
//  (M/T/W/T/F/S/S), bottom month labels.
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun ActivityHeatmapCard(activityData: Map<Long, Int>, avgDailyWatchTime: String) {
    val oneDayMs = 24 * 60 * 60 * 1000L
    val now = System.currentTimeMillis()
    val todayStart = (now / oneDayMs) * oneDayMs
    val primaryColor = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)

    // Monday of the current week (so each column = Mon..Sun, row 0 = Mon)
    val cal = Calendar.getInstance()
    cal.timeInMillis = todayStart
    val todayDow = (cal.get(Calendar.DAY_OF_WEEK) - 2 + 7) % 7 // Mon=0..Sun=6
    val thisMonday = todayStart - todayDow * oneDayMs

    val dayMarkers = listOf("M", "T", "W", "T", "F", "S", "S")

    // Per-week month labels (label shown at the oldest week of each month)
    val weekMonthLabels = remember(thisMonday) {
        val c = Calendar.getInstance()
        val fmt = java.text.SimpleDateFormat("MMM", java.util.Locale.getDefault())
        val labels = arrayOfNulls<String>(53)
        var lastMonth = -1
        for (w in 52 downTo 0) {
            val weekMonday = thisMonday - w * 7 * oneDayMs
            c.timeInMillis = weekMonday
            val m = c.get(Calendar.MONTH)
            if (m != lastMonth) {
                labels[w] = fmt.format(c.time)
                lastMonth = m
            }
        }
        labels.toList()
    }

    val cellSize = 12.dp
    val cellSpacing = 2.dp

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Watch Activity", fontFamily = RobotoFamily, fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Avg: $avgDailyWatchTime/day", fontFamily = RobotoFamily, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row {
                    // Left: day markers (M/T/W/T/F/S/S), aligned with the 7 rows
                    Column(
                        modifier = Modifier.width(14.dp),
                        verticalArrangement = Arrangement.spacedBy(cellSpacing),
                    ) {
                        dayMarkers.forEach { label ->
                            Box(
                                modifier = Modifier.size(cellSize),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    label, fontFamily = RobotoFamily, fontSize = 8.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    // Scrollable weeks
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(cellSpacing),
                        reverseLayout = true, // most recent week on the right
                    ) {
                        items(53) { w ->
                            Column(
                                verticalArrangement = Arrangement.spacedBy(cellSpacing),
                            ) {
                                for (r in 0 until 7) {
                                    val dayMs = thisMonday - w * 7 * oneDayMs + r * oneDayMs
                                    val count = activityData[dayMs] ?: 0
                                    val color = when {
                                        count == 0 -> emptyColor
                                        count <= 2 -> primaryColor.copy(alpha = 0.3f)
                                        count <= 5 -> primaryColor.copy(alpha = 0.5f)
                                        count <= 10 -> primaryColor.copy(alpha = 0.7f)
                                        else -> primaryColor.copy(alpha = 0.95f)
                                    }
                                    Box(
                                        modifier = Modifier.size(cellSize)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(color),
                                    )
                                }
                                // Month label at the bottom of this column (if applicable)
                                Box(modifier = Modifier.width(cellSize).height(12.dp)) {
                                    weekMonthLabels.getOrNull(w)?.let { label ->
                                        Text(
                                            label, fontFamily = RobotoFamily, fontSize = 8.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            maxLines = 1,
                                        )
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

// ════════════════════════════════════════════════════════════════════════════
//  Settings Sheet — list format, Change Name, Change Picture (with real image
//  picker + URL paste + live preview).
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsSheet(
    state: ProfileUiState,
    onDismiss: () -> Unit,
    onUpdateName: (String) -> Unit,
    onUpdateAvatar: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentScreen by remember { mutableStateOf("main") } // "main", "name", "picture"
    var nameInput by remember { mutableStateOf(state.displayName) }
    var avatarInput by remember { mutableStateOf(state.avatarUrl ?: "") }
    var avatarMode by remember { mutableStateOf("url") } // "url" or "upload"
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Photo picker — copies the chosen image to app-internal storage so it
    // survives across launches (a content:// SAF URI is only valid for this
    // session unless persistable permission is taken, which the photo picker
    // does not grant). The saved file:// URI loads directly via Coil.
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val savedUri = withContext(Dispatchers.IO) {
                    try {
                        val dest = File(context.filesDir, "avatar_${System.currentTimeMillis()}.jpg")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            dest.outputStream().use { output -> input.copyTo(output) }
                        }
                        "file://${dest.absolutePath}"
                    } catch (e: Exception) {
                        null
                    }
                }
                if (savedUri != null) {
                    avatarInput = savedUri
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { currentScreen = "main"; onDismiss() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding()) {
            when (currentScreen) {
                "main" -> {
                    Text(
                        "Customize Profile", fontFamily = RobotoFamily, fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(20.dp))
                    // Change Name option
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().clickable { currentScreen = "name" },
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Edit, contentDescription = "Name",
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(16.dp))
                            Text("Change Name", fontFamily = RobotoFamily, fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Open",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // Change Picture option
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().clickable { currentScreen = "picture" },
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Image, contentDescription = "Picture",
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(16.dp))
                            Text("Change Picture", fontFamily = RobotoFamily, fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Open",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
                "name" -> {
                    Text(
                        "Change Name", fontFamily = RobotoFamily, fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(20.dp))
                    OutlinedTextField(
                        value = nameInput, onValueChange = { nameInput = it },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text("Enter your name", fontFamily = RobotoFamily) },
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { currentScreen = "main" }) {
                            Text("Back", fontFamily = RobotoFamily)
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).clickable {
                                onUpdateName(nameInput.ifBlank { "Anime Fan" })
                                currentScreen = "main"
                            },
                        ) {
                            Text(
                                "Save", fontFamily = RobotoFamily, fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(vertical = 14.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
                "picture" -> {
                    Text(
                        "Change Picture", fontFamily = RobotoFamily, fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(20.dp))
                    // Preview section (golden-ratio-ish layout: avatar + label beside it)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(72.dp),
                        ) {
                            if (avatarInput.isNotBlank()) {
                                AsyncImage(
                                    model = avatarInput.trim(),
                                    contentDescription = "Preview",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Person, contentDescription = "Default",
                                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                                }
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                "Custom Avatar", fontFamily = RobotoFamily, fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                if (avatarInput.isNotBlank()) "Image set" else "No image set",
                                fontFamily = RobotoFamily, fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    // Mode toggle: Upload | URL
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            color = if (avatarMode == "upload") MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).clickable { avatarMode = "upload" },
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Upload, contentDescription = "Upload",
                                    tint = if (avatarMode == "upload") MaterialTheme.colorScheme.onPrimary
                                           else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Upload", fontFamily = RobotoFamily, fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (avatarMode == "upload") MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Surface(
                            color = if (avatarMode == "url") MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).clickable { avatarMode = "url" },
                        ) {
                            Text(
                                "URL", fontFamily = RobotoFamily, fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (avatarMode == "url") MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 10.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    when (avatarMode) {
                        "url" -> {
                            OutlinedTextField(
                                value = avatarInput, onValueChange = { avatarInput = it },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                placeholder = { Text("Paste image URL", fontFamily = RobotoFamily) },
                            )
                        }
                        "upload" -> {
                            // Real photo picker — launches the system photo picker.
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Filled.Upload, contentDescription = "Upload",
                                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Choose Image", fontFamily = RobotoFamily, fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { currentScreen = "main" }) {
                            Text("Back", fontFamily = RobotoFamily)
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).clickable {
                                onUpdateAvatar(avatarInput.trim())
                                currentScreen = "main"
                            },
                        ) {
                            Text(
                                "Save", fontFamily = RobotoFamily, fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(vertical = 14.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Genre Anime Sheet
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreAnimeSheet(
    genre: String,
    anime: List<RecentlyWatchedItem>,
    onDismiss: () -> Unit,
    onOpenAnime: (Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val shuffledAnime = remember(anime) { anime.shuffled() }
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface, dragHandle = null,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding()) {
            Spacer(Modifier.height(16.dp))
            Text(genre, fontFamily = RobotoFamily, fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
            Text("${anime.size} anime in your library", fontFamily = RobotoFamily, fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shuffledAnime.size) { index ->
                    val item = shuffledAnime[index]
                    Column(modifier = Modifier.width(100.dp).clickable { item.anilistId?.let { onOpenAnime(it) } }) {
                        Box(modifier = Modifier.size(width = 100.dp, height = 140.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                            if (item.coverUrl != null) {
                                AsyncImage(model = item.coverUrl, contentDescription = item.title,
                                    modifier = Modifier.fillMaxWidth().height(140.dp), contentScale = ContentScale.Crop)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(item.title, fontFamily = RobotoFamily, fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Timeline Tab — theme colors per action, with collapsible tab bar as item 0.
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun TimelineTab(
    state: ProfileUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    scrollFraction: () -> Float,
    onNavigateToAnime: (Int) -> Unit,
    onTabSelect: (Int) -> Unit,
) {
    if (state.timeline.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "No activity yet. Start watching anime to build your timeline!",
                fontFamily = RobotoFamily, fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 12.dp, end = 12.dp, top = 8.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Item 0: full-size tab bar — scrolls away + shrinks via graphicsLayer.
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val f = scrollFraction()
                        alpha = (1f - f).coerceIn(0f, 1f)
                        val s = 1f - f * 0.25f
                        scaleX = s
                        scaleY = s
                    }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                SegmentedToggle(
                    options = listOf("Stats", "Timeline"),
                    selectedIndex = 1,
                    onSelect = onTabSelect,
                )
            }
        }
        items(state.timeline.size) { index ->
            val item = state.timeline[index]
            TimelineRow(item, onNavigateToAnime)
        }
    }
}

@Composable
private fun TimelineRow(item: TimelineItem, onNavigateToAnime: (Int) -> Unit) {
    val accentColor = when (item.type) {
        "watch" -> MaterialTheme.colorScheme.primary
        "rating" -> Color(0xFFFFB74D)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable { item.anilistId?.let { onNavigateToAnime(it) } },
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = accentColor, modifier = Modifier.size(width = 4.dp, height = 48.dp)) {}
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier.size(width = 44.dp, height = 62.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            ) {
                if (item.coverUrl != null) {
                    AsyncImage(model = item.coverUrl, contentDescription = item.title,
                        modifier = Modifier.fillMaxWidth().height(62.dp), contentScale = ContentScale.Crop)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, fontFamily = RobotoFamily, fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.description, fontFamily = RobotoFamily, fontSize = 12.sp,
                    fontWeight = FontWeight.Medium, color = accentColor, maxLines = 1)
            }
        }
    }
}
