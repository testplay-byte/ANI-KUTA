package com.confused.anikuta.profile

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import java.util.Calendar
import kotlin.math.min

// ════════════════════════════════════════════════════════════════════════════
//  Watch Flow Bar Graph (Mon-Sun) — with today highlight + tap info
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun WatchFlowGraph(watchFlowByDay: List<Int>) {
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val todayIdx = remember {
        val cal = Calendar.getInstance()
        ((cal.get(Calendar.DAY_OF_WEEK) - 2 + 7) % 7) // Mon=0, Sun=6
    }
    val maxVal = (watchFlowByDay.maxOrNull() ?: 0).coerceAtLeast(1)
    val primaryColor = MaterialTheme.colorScheme.primary
    val todayColor = MaterialTheme.colorScheme.primary
    var selectedDay by remember { mutableStateOf(-1) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Watch Flow", fontFamily = RobotoFamily, fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                watchFlowByDay.forEachIndexed { index, count ->
                    val isToday = index == todayIdx
                    val isSelected = index == selectedDay
                    val barHeight = (count.toFloat() / maxVal * 100f).coerceIn(4f, 100f)
                    val barColor = when {
                        isSelected -> primaryColor
                        isToday -> primaryColor.copy(alpha = 0.8f)
                        else -> primaryColor.copy(alpha = 0.3f + 0.4f * count.toFloat() / maxVal)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 2.dp)) {
                        // Count label (shown when selected or today)
                        if (isSelected || (isToday && selectedDay == -1)) {
                            Text("$count", fontFamily = RobotoFamily, fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold, color = primaryColor,
                                modifier = Modifier.padding(bottom = 2.dp))
                        }
                        Box(modifier = Modifier.width(22.dp).height(barHeight.dp)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(barColor).clickable { selectedDay = if (selectedDay == index) -1 else index })
                        Spacer(Modifier.height(4.dp))
                        Text(days[index], fontFamily = RobotoFamily, fontSize = 10.sp,
                            fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Bold,
                            color = if (isToday) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Time DNA — half-row, pie chart, 4 colors, percentages
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun TimeDnaCard(timeDna: TimeDnaData?, onClick: () -> Unit) {
    if (timeDna == null) return

    // Calculate 4 time periods
    val morning = (6..11).sumOf { timeDna.hourlyCounts[it] }
    val afternoon = (12..17).sumOf { timeDna.hourlyCounts[it] }
    val evening = (18..22).sumOf { timeDna.hourlyCounts[it] }
    val night = (23..23).sumOf { timeDna.hourlyCounts[it] } + (0..5).sumOf { timeDna.hourlyCounts[it] }
    val total = (morning + afternoon + evening + night).coerceAtLeast(1)

    val morningPct = morning * 100 / total
    val afternoonPct = afternoon * 100 / total
    val eveningPct = evening * 100 / total
    val nightPct = night * 100 / total

    // Colors: morning=amber, afternoon=cyan, evening=purple, night=indigo
    val morningColor = Color(0xFFFFB300)
    val afternoonColor = Color(0xFF00BCD4)
    val eveningColor = Color(0xFFAB47BC)
    val nightColor = Color(0xFF5C6BC0)

    val periods = listOf(
        Triple("Morning", morningPct, morningColor),
        Triple("Afternoon", afternoonPct, afternoonColor),
        Triple("Evening", eveningPct, eveningColor),
        Triple("Night", nightPct, nightColor),
    )

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Time DNA", fontFamily = RobotoFamily, fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                // Left: Pie chart (half-row width)
                Box(modifier = Modifier.size(100.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val radius = min(centerX, centerY) * 0.9f
                        var startAngle = -90f // start at top

                        listOf(
                            morning.toFloat() / total to morningColor,
                            afternoon.toFloat() / total to afternoonColor,
                            evening.toFloat() / total to eveningColor,
                            night.toFloat() / total to nightColor,
                        ).forEach { (fraction, color) ->
                            if (fraction > 0f) {
                                val sweepAngle = fraction * 360f
                                drawArc(
                                    color = color,
                                    startAngle = startAngle,
                                    sweepAngle = sweepAngle,
                                    useCenter = true,
                                    topLeft = Offset(centerX - radius, centerY - radius),
                                    size = Size(radius * 2, radius * 2),
                                )
                                startAngle += sweepAngle
                            }
                        }
                        // Center hole (donut)
                        drawCircle(color = Color.Black.copy(alpha = 0.0f), radius = radius * 0.4f, center = Offset(centerX, centerY))
                    }
                    // Center text
                    Text(timeDna.preferredTimeLabel, fontFamily = RobotoFamily, fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.align(Alignment.Center))
                }
                Spacer(Modifier.width(16.dp))
                // Right: Legend with percentages
                Column(modifier = Modifier.weight(1f)) {
                    periods.forEach { (name, pct, color) ->
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 3.dp)) {
                            Surface(color = color, shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.size(12.dp)) {}
                            Spacer(Modifier.width(8.dp))
                            Text("$pct%", fontFamily = RobotoFamily, fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.width(36.dp))
                            Text(name, fontFamily = RobotoFamily, fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = "Open",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Activity Heatmap — rounded corners, show empty blocks, themed
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun ActivityHeatmapCard(activityData: Map<Long, Int>, avgDailyWatchTime: String) {
    val oneDayMs = 24 * 60 * 60 * 1000L
    val now = System.currentTimeMillis()
    val todayStart = (now / oneDayMs) * oneDayMs
    val primaryColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.surfaceVariant
    val cellSize = 12.dp
    val cellSpacing = 2.dp
    val cornerRadius = 3.dp

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Watch Activity", fontFamily = RobotoFamily, fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Avg: $avgDailyWatchTime/day", fontFamily = RobotoFamily, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                    val weeks = 53
                    val cellW = size.width / weeks
                    val cellH = size.height / 7f
                    val startX = (size.width - cellW * weeks) / 2f

                    for (week in 0 until weeks) {
                        for (day in 0 until 7) {
                            val dayOffset = (weeks - 1 - week) * 7 + (6 - day)
                            val dayMs = todayStart - dayOffset * oneDayMs
                            val count = activityData[dayMs] ?: 0
                            val color = when {
                                count == 0 -> gridColor.copy(alpha = 0.15f)
                                count <= 2 -> primaryColor.copy(alpha = 0.3f)
                                count <= 5 -> primaryColor.copy(alpha = 0.5f)
                                count <= 10 -> primaryColor.copy(alpha = 0.7f)
                                else -> primaryColor.copy(alpha = 0.9f)
                            }
                            // Rounded rect
                            drawRoundRect(
                                color = color,
                                topLeft = Offset(startX + week * cellW + 1f, day * cellH + 1f),
                                size = Size(cellW - 2f, cellH - 2f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Genre Anime Sheet
// ════════════════════════════════════════════════════════════════════════════

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun GenreAnimeSheet(genre: String, anime: List<RecentlyWatchedItem>,
    onDismiss: () -> Unit, onOpenAnime: (Int) -> Unit) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val shuffledAnime = remember(anime) { anime.shuffled() }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface, dragHandle = null) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .navigationBarsPadding()) {
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
                                    modifier = Modifier.fillMaxWidth().height(140.dp),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop)
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
//  Timeline Tab — theme colors per action, highlighted key info
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun TimelineTab(state: ProfileUiState, onNavigateToAnime: (Int) -> Unit) {
    if (state.timeline.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("No activity yet. Start watching anime to build your timeline!",
                fontFamily = RobotoFamily, fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(state.timeline.size) { index ->
            val item = state.timeline[index]
            TimelineRow(item, onNavigateToAnime)
        }
    }
}

@Composable
private fun TimelineRow(item: TimelineItem, onNavigateToAnime: (Int) -> Unit) {
    // Theme color per action type
    val accentColor = when (item.type) {
        "watch" -> MaterialTheme.colorScheme.primary
        "rating" -> Color(0xFFFFB300) // amber for ratings
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable { item.anilistId?.let { onNavigateToAnime(it) } }) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Colored left border indicator
            Surface(color = accentColor, modifier = Modifier.size(width = 4.dp, height = 48.dp)) {}
            Spacer(Modifier.width(12.dp))
            // Cover thumbnail
            Box(modifier = Modifier.size(width = 44.dp, height = 62.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                if (item.coverUrl != null) {
                    AsyncImage(model = item.coverUrl, contentDescription = item.title,
                        modifier = Modifier.fillMaxWidth().height(62.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, fontFamily = RobotoFamily, fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Highlighted description with accent color for key info
                Text(item.description, fontFamily = RobotoFamily, fontSize = 12.sp,
                    fontWeight = FontWeight.Medium, color = accentColor, maxLines = 1)
            }
        }
    }
}
