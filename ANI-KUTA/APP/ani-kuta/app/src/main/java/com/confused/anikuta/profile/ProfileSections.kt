package com.confused.anikuta.profile

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ── Genre Breakdown ───────────────────────────────────────────────────────────

@Composable
fun GenreBreakdown(genreDistribution: Map<String, Int>) {
    val sorted = genreDistribution.entries.sortedByDescending { it.value }
    val maxCount = sorted.firstOrNull()?.value ?: 1
    val total = sorted.sumOf { it.value }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "Genre Breakdown",
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        sorted.forEach { (genre, count) ->
            val percentage = if (total > 0) (count.toFloat() / total * 100).toInt() else 0
            val barWidth = count.toFloat() / maxCount
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    genre,
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(100.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier.weight(1f).height(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(barWidth)
                            .height(20.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "$count ($percentage%)",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(70.dp),
                )
            }
        }
    }
}

// ── Recently Watched ──────────────────────────────────────────────────────────

@Composable
fun RecentlyWatchedSection(
    items: List<RecentlyWatchedItem>,
    onNavigateToAnime: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            "Recently Watched",
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items.size) { index ->
                val item = items[index]
                RecentlyWatchedCard(item) {
                    item.anilistId?.let { onNavigateToAnime(it) }
                }
            }
        }
    }
}

@Composable
private fun RecentlyWatchedCard(item: RecentlyWatchedItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(100.dp).clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.size(width = 100.dp, height = 140.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        ) {
            if (item.coverUrl != null) {
                AsyncImage(
                    model = item.coverUrl,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    item.title.firstOrNull()?.uppercase() ?: "?",
                    fontFamily = RobotoFamily,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            // Episode number badge
            if (item.episodeNumber > 0) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                ) {
                    Text(
                        "EP ${item.episodeNumber}",
                        fontFamily = RobotoFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }
            // Progress bar
            if (item.progressFraction > 0f) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { item.progressFraction },
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
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

// ── Top Rated ─────────────────────────────────────────────────────────────────

@Composable
fun TopRatedSection(
    items: List<TopRatedItem>,
    onNavigateToAnime: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            "Top Rated",
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onNavigateToAnime(item.anilistId) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Cover thumbnail
                Box(
                    modifier = Modifier.size(width = 50.dp, height = 70.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                ) {
                    if (item.coverUrl != null) {
                        AsyncImage(
                            model = item.coverUrl,
                            contentDescription = item.title,
                            modifier = Modifier.fillMaxWidth().height(70.dp),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    item.title,
                    fontFamily = RobotoFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                // Star rating
                Text(
                    "${item.rating / 10}",
                    fontFamily = RobotoFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    " / 10",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Activity Heatmap ──────────────────────────────────────────────────────────

@Composable
fun ActivityHeatmap(activityData: Map<Long, Int>) {
    val oneDayMs = 24 * 60 * 60 * 1000L
    val now = System.currentTimeMillis()
    val todayStart = (now / oneDayMs) * oneDayMs
    val days = 365

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "Watch Activity (365 days)",
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        // Simple heatmap: 7 rows (days of week) x 52 columns (weeks)
        val weeks = 53
        val primaryColor = MaterialTheme.colorScheme.primary
        val gridColor = MaterialTheme.colorScheme.surfaceVariant

        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxWidth().height(120.dp),
        ) {
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
                        topLeft = androidx.compose.ui.geometry.Offset(
                            startX + week * cellSize,
                            day * cellSize,
                        ),
                        size = androidx.compose.ui.geometry.Size(cellSize - 1f, cellSize - 1f),
                    )
                }
            }
        }
    }
}

// ── Genre Anime Sheet (bottom-up menu) ────────────────────────────────────────

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
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp)
                .androidx_navigationBarsPadding(),
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
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
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
                                    contentScale = ContentScale.Crop,
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

// Helper for navigation bars padding
private fun Modifier.androidx_navigationBarsPadding(): Modifier =
    this.then(androidx.compose.foundation.layout.WindowInsets.navigationBars.let {
        androidx.compose.foundation.layout.windowInsetsPadding(it)
    })
