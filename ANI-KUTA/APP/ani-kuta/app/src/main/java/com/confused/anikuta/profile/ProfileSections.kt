package com.confused.anikuta.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import kotlin.math.min

// ════════════════════════════════════════════════════════════════════════════
//  Section: Watch Flow Bar Graph (Mon-Sun)
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun WatchFlowGraph(watchFlowByDay: List<Int>) {
    val days = listOf("M", "T", "W", "T", "F", "S", "S")
    val maxVal = (watchFlowByDay.maxOrNull() ?: 0).coerceAtLeast(1)
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "Watch Flow",
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom,
            ) {
                watchFlowByDay.forEachIndexed { index, count ->
                    val barHeight = (count.toFloat() / maxVal * 100f).coerceIn(4f, 100f)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    ) {
                        // Bar
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(barHeight.dp)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(primaryColor.copy(alpha = 0.3f + 0.7f * count.toFloat() / maxVal)),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            days[index],
                            fontFamily = RobotoFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Section: Time DNA (preferred watch time)
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun TimeDnaCard(timeDna: TimeDnaData?, onClick: () -> Unit) {
    if (timeDna == null) return

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "Time DNA",
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Mini 24-hour bar visualization
                Box(
                    modifier = Modifier.size(width = 120.dp, height = 60.dp),
                ) {
                    val primaryColor = MaterialTheme.colorScheme.primary
                    Canvas(modifier = Modifier.fillMaxWidth().height(60.dp)) {
                        val barWidth = size.width / 24f
                        val maxCount = (timeDna.hourlyCounts.maxOrNull() ?: 0).coerceAtLeast(1)
                        timeDna.hourlyCounts.forEachIndexed { hour, count ->
                            if (count > 0) {
                                val barHeight = size.height * count.toFloat() / maxCount
                                drawRect(
                                    color = primaryColor.copy(alpha = 0.3f + 0.7f * count.toFloat() / maxCount),
                                    topLeft = Offset(hour * barWidth + 1f, size.height - barHeight),
                                    size = Size(barWidth - 2f, barHeight),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        timeDna.preferredTimeLabel,
                        fontFamily = RobotoFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "${timeDna.totalSessions} sessions",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = "Open",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Section: Activity Heatmap (themed + avg daily time)
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun ActivityHeatmapCard(activityData: Map<Long, Int>, avgDailyWatchTime: String) {
    val oneDayMs = 24 * 60 * 60 * 1000L
    val now = System.currentTimeMillis()
    val todayStart = (now / oneDayMs) * oneDayMs
    val primaryColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Watch Activity",
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Avg: $avgDailyWatchTime/day",
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Canvas(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                ) {
                    val weeks = 53
                    val cellSize = min(size.width / weeks, size.height / 7f)
                    val startX = (size.width - cellSize * weeks) / 2f

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

                            drawRect(
                                color = color,
                                topLeft = Offset(startX + week * cellSize, day * cellSize),
                                size = Size(cellSize - 1f, cellSize - 1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Section: Genre Anime Sheet (bottom-up menu)
// ════════════════════════════════════════════════════════════════════════════

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun GenreAnimeSheet(
    genre: String,
    anime: List<RecentlyWatchedItem>,
    onDismiss: () -> Unit,
    onOpenAnime: (Int) -> Unit,
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val shuffledAnime = remember(anime) { anime.shuffled() }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .androidx.compose.foundation.layout.navigationBarsPadding(),
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                genre,
                fontFamily = RobotoFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "${anime.size} anime in your library",
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shuffledAnime.size) { index ->
                    val item = shuffledAnime[index]
                    Column(
                        modifier = Modifier.width(100.dp).clickable {
                            item.anilistId?.let { onOpenAnime(it) }
                        },
                    ) {
                        Box(
                            modifier = Modifier.size(width = 100.dp, height = 140.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        ) {
                            if (item.coverUrl != null) {
                                AsyncImage(
                                    model = item.coverUrl,
                                    contentDescription = item.title,
                                    modifier = Modifier.fillMaxWidth().height(140.dp),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            item.title,
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Timeline Tab
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun TimelineTab(
    state: ProfileUiState,
    onNavigateToAnime: (Int) -> Unit,
) {
    if (state.timeline.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No activity yet. Start watching anime to build your timeline!",
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 12.dp, end = 12.dp, top = 8.dp, bottom = 110.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(state.timeline.size) { index ->
            val item = state.timeline[index]
            TimelineRow(item, onNavigateToAnime)
        }
    }
}

@Composable
private fun TimelineRow(
    item: TimelineItem,
    onNavigateToAnime: (Int) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable {
            item.anilistId?.let { onNavigateToAnime(it) }
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Cover thumbnail
            Box(
                modifier = Modifier.size(width = 44.dp, height = 62.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            ) {
                if (item.coverUrl != null) {
                    AsyncImage(
                        model = item.coverUrl,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxWidth().height(62.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.description,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            // Rating badge (if rated)
            if (item.rating != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        "${item.rating / 10}★",
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

// Helper for LazyColumn items
private fun androidx.compose.foundation.lazy.LazyListScope.items(
    count: Int,
    itemContent: @Composable (Int) -> Unit,
) {
    items(count) { index -> itemContent(index) }
}
