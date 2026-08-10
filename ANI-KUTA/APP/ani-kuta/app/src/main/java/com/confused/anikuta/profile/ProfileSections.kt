package com.confused.anikuta.profile

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import java.util.Calendar
import kotlin.math.min

// ════════════════════════════════════════════════════════════════════════════
//  Watch Flow — y-axis, dynamic height, today color, tap → sidebar
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun WatchFlowGraph(watchFlowByDay: List<Int>) {
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val todayIdx = remember {
        val cal = Calendar.getInstance()
        ((cal.get(Calendar.DAY_OF_WEEK) - 2 + 7) % 7)
    }
    val maxVal = (watchFlowByDay.maxOrNull() ?: 0).coerceAtLeast(1)
    val primaryColor = MaterialTheme.colorScheme.primary
    val todayColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    var selectedDay by remember { mutableStateOf(-1) }
    var showSidebar by remember { mutableStateOf(false) }

    // Y-axis labels (0, mid, max)
    val yLabels = listOf(0, maxVal / 2, maxVal)

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Watch Flow", fontFamily = RobotoFamily, fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp))

        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {

            Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                // Y-axis labels
                Column(modifier = Modifier.width(24.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    yLabels.reversed().forEach { label ->
                        Text("$label", fontFamily = RobotoFamily, fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Bar chart area
                Box(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom) {
                        watchFlowByDay.forEachIndexed { index, count ->
                            val isToday = index == todayIdx
                            val isSelected = index == selectedDay
                            val barHeight = (count.toFloat() / maxVal * 80f).coerceIn(4f, 80f)
                            val barColor = when {
                                isSelected -> primaryColor
                                isToday -> todayColor
                                else -> primaryColor.copy(alpha = 0.25f + 0.35f * count.toFloat() / maxVal)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 2.dp)) {
                                // Dynamic height: show count when selected
                                AnimatedVisibility(visible = isSelected, enter = fadeIn(), exit = fadeOut(), modifier = Modifier) {
                                    Text("$count", fontFamily = RobotoFamily, fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold, color = primaryColor,
                                        modifier = Modifier.padding(bottom = 2.dp))
                                }
                                Box(modifier = Modifier.width(22.dp).height(barHeight.dp)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(barColor)
                                    .clickable {
                                        selectedDay = if (selectedDay == index) -1 else index
                                        showSidebar = selectedDay >= 0
                                    })
                                Spacer(Modifier.height(4.dp))
                                Text(days[index], fontFamily = RobotoFamily, fontSize = 10.sp,
                                    fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Bold,
                                    color = if (isToday) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Sidebar (appears when a bar is tapped)
                    AnimatedVisibility(
                        visible = showSidebar && selectedDay >= 0,
                        enter = slideInHorizontally { it } + fadeIn(),
                        exit = slideOutHorizontally { it } + fadeOut(),
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        WatchFlowSidebar(
                            dayName = if (selectedDay >= 0) days[selectedDay] else "",
                            count = if (selectedDay >= 0) watchFlowByDay[selectedDay] else 0,
                            onDismiss = { selectedDay = -1; showSidebar = false },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchFlowSidebar(dayName: String, count: Int, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 8.dp,
        modifier = Modifier.width(140.dp).padding(4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(dayName, fontFamily = RobotoFamily, fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text("$count episodes", fontFamily = RobotoFamily, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text("Tap anywhere to close", fontFamily = RobotoFamily, fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Time DNA — donut chart, themed colors, right side: recently watched
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun TimeDnaCard(timeDna: TimeDnaData?, recentlyWatched: RecentlyWatchedItem?, onClick: () -> Unit) {
    if (timeDna == null) return

    val morning = (6..11).sumOf { timeDna.hourlyCounts[it] }
    val afternoon = (12..17).sumOf { timeDna.hourlyCounts[it] }
    val evening = (18..22).sumOf { timeDna.hourlyCounts[it] }
    val night = (23..23).sumOf { timeDna.hourlyCounts[it] } + (0..5).sumOf { timeDna.hourlyCounts[it] }
    val total = (morning + afternoon + evening + night).coerceAtLeast(1)

    // Themed colors (closer to app's lime/white palette)
    val morningColor = Color(0xFFFFB74D)    // warm orange
    val afternoonColor = Color(0xFFFFE082)   // light amber
    val eveningColor = Color(0xFFB1F256)     // app primary (lime)
    val nightColor = Color(0xFFFFFFFF)       // white

    val periods = listOf(
        Triple("Morning", morning, morningColor),
        Triple("Afternoon", afternoon, afternoonColor),
        Triple("Evening", evening, eveningColor),
        Triple("Night", night, nightColor),
    )

    // Current period based on time of day
    val cal = Calendar.getInstance()
    val currentHour = cal.get(Calendar.HOUR_OF_DAY)
    val currentPeriodColor = when (currentHour) {
        in 6..11 -> morningColor
        in 12..17 -> afternoonColor
        in 18..22 -> eveningColor
        else -> nightColor
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Time DNA", fontFamily = RobotoFamily, fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                // Left: Donut chart
                Box(modifier = Modifier.size(100.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val outerRadius = min(centerX, centerY) * 0.9f
                        val innerRadius = outerRadius * 0.6f
                        var startAngle = -90f

                        periods.forEach { (_, count, color) ->
                            if (count > 0) {
                                val fraction = count.toFloat() / total
                                val sweepAngle = fraction * 360f
                                drawArc(color = color, startAngle = startAngle, sweepAngle = sweepAngle,
                                    useCenter = true, topLeft = Offset(centerX - outerRadius, centerY - outerRadius),
                                    size = Size(outerRadius * 2, outerRadius * 2))
                                startAngle += sweepAngle
                            }
                        }
                        // Donut hole
                        drawCircle(color = Color(0x0014111F), radius = innerRadius, center = Offset(centerX, centerY))
                    }
                    // Center: current period color dot
                    Surface(color = currentPeriodColor, shape = CircleShape, modifier = Modifier.size(16.dp).align(Alignment.Center)) {}
                }
                Spacer(Modifier.width(16.dp))
                // Right: Legend + recently watched
                Column(modifier = Modifier.weight(1f)) {
                    periods.forEach { (name, count, color) ->
                        val pct = if (total > 0) count * 100 / total else 0
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            Surface(color = color, shape = RoundedCornerShape(4.dp), modifier = Modifier.size(10.dp)) {}
                            Spacer(Modifier.width(6.dp))
                            Text("$pct%", fontFamily = RobotoFamily, fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.width(32.dp))
                            Text(name, fontFamily = RobotoFamily, fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    // Recently watched preview
                    if (recentlyWatched != null) {
                        Spacer(Modifier.height(8.dp))
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (recentlyWatched.coverUrl != null) {
                                    AsyncImage(model = recentlyWatched.coverUrl, contentDescription = recentlyWatched.title,
                                        modifier = Modifier.size(width = 30.dp, height = 42.dp).clip(RoundedCornerShape(4.dp)),
                                        contentScale = ContentScale.Crop)
                                }
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(recentlyWatched.title, fontFamily = RobotoFamily, fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("EP ${recentlyWatched.episodeNumber}", fontFamily = RobotoFamily, fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
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
//  Activity Heatmap — scrollable, square cells, gray empty, month labels
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun ActivityHeatmapCard(activityData: Map<Long, Int>, avgDailyWatchTime: String) {
    val oneDayMs = 24 * 60 * 60 * 1000L
    val now = System.currentTimeMillis()
    val todayStart = (now / oneDayMs) * oneDayMs
    val primaryColor = MaterialTheme.colorScheme.primary
    val emptyColor = Color(0xFF2A2A2A) // gray for empty blocks

    // Generate month labels
    val monthLabels = remember {
        val cal = Calendar.getInstance()
        val labels = mutableListOf<Pair<Int, String>>()
        val monthFormat = java.text.SimpleDateFormat("MMM", java.util.Locale.getDefault())
        var lastMonth = -1
        for (week in 52 downTo 0) {
            val dayOffset = week * 7
            val dayMs = todayStart - dayOffset * oneDayMs
            cal.timeInMillis = dayMs
            val month = cal.get(Calendar.MONTH)
            if (month != lastMonth) {
                labels.add(52 - week to monthFormat.format(cal.time))
                lastMonth = month
            }
        }
        labels
    }

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
                // Scrollable heatmap — horizontal scroll
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    reverseLayout = true, // most recent on the right
                ) {
                    items(53) { week ->
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            for (day in 0 until 7) {
                                val dayOffset = week * 7 + (6 - day)
                                val dayMs = todayStart - dayOffset * oneDayMs
                                val count = activityData[dayMs] ?: 0
                                val color = when {
                                    count == 0 -> emptyColor
                                    count <= 2 -> primaryColor.copy(alpha = 0.3f)
                                    count <= 5 -> primaryColor.copy(alpha = 0.5f)
                                    count <= 10 -> primaryColor.copy(alpha = 0.7f)
                                    else -> primaryColor.copy(alpha = 0.9f)
                                }
                                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                // Month labels
                Text("Tap and scroll to see more →", fontFamily = RobotoFamily, fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Settings Sheet — list format, Change Name, Change Picture
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
                    Text("Customize Profile", fontFamily = RobotoFamily, fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(20.dp))
                    // Change Name option
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().clickable { currentScreen = "name" }) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Edit, contentDescription = "Name", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(16.dp))
                            Text("Change Name", fontFamily = RobotoFamily, fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Open", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // Change Picture option
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().clickable { currentScreen = "picture" }) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Image, contentDescription = "Picture", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(16.dp))
                            Text("Change Picture", fontFamily = RobotoFamily, fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Open", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
                "name" -> {
                    Text("Change Name", fontFamily = RobotoFamily, fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(20.dp))
                    OutlinedTextField(value = nameInput, onValueChange = { nameInput = it },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text("Enter your name", fontFamily = RobotoFamily) })
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { currentScreen = "main" }) { Text("Back", fontFamily = RobotoFamily) }
                        Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).clickable {
                                onUpdateName(nameInput.ifBlank { "Anime Fan" })
                                currentScreen = "main"
                            }) {
                            Text("Save", fontFamily = RobotoFamily, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(vertical = 14.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
                "picture" -> {
                    Text("Change Picture", fontFamily = RobotoFamily, fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(20.dp))
                    // Preview section
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Preview image
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(72.dp)) {
                            if (avatarInput.isNotBlank()) {
                                AsyncImage(model = avatarInput, contentDescription = "Preview",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Person, contentDescription = "Default",
                                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                                }
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Custom Avatar", fontFamily = RobotoFamily, fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                            Text(if (avatarInput.isNotBlank()) "URL set" else "No image set",
                                fontFamily = RobotoFamily, fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    // Mode toggle: Upload | URL
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(color = if (avatarMode == "upload") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f).clickable { avatarMode = "upload" }) {
                            Row(modifier = Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Upload, contentDescription = "Upload",
                                    tint = if (avatarMode == "upload") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Upload", fontFamily = RobotoFamily, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                                    color = if (avatarMode == "upload") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Surface(color = if (avatarMode == "url") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f).clickable { avatarMode = "url" }) {
                            Text("URL", fontFamily = RobotoFamily, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                                color = if (avatarMode == "url") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 10.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    when (avatarMode) {
                        "url" -> {
                            OutlinedTextField(value = avatarInput, onValueChange = { avatarInput = it },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                placeholder = { Text("Paste image URL", fontFamily = RobotoFamily) })
                        }
                        "upload" -> {
                            // Upload button (placeholder — file picker would go here)
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().clickable { /* TODO: file picker */ }) {
                                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically) {
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
                        TextButton(onClick = { currentScreen = "main" }) { Text("Back", fontFamily = RobotoFamily) }
                        Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).clickable {
                                onUpdateAvatar(avatarInput)
                                currentScreen = "main"
                            }) {
                            Text("Save", fontFamily = RobotoFamily, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(vertical = 14.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
fun GenreAnimeSheet(genre: String, anime: List<RecentlyWatchedItem>,
    onDismiss: () -> Unit, onOpenAnime: (Int) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val shuffledAnime = remember(anime) { anime.shuffled() }
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface, dragHandle = null) {
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
    val accentColor = when (item.type) {
        "watch" -> MaterialTheme.colorScheme.primary
        "rating" -> Color(0xFFFFB74D)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable { item.anilistId?.let { onNavigateToAnime(it) } }) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = accentColor, modifier = Modifier.size(width = 4.dp, height = 48.dp)) {}
            Spacer(Modifier.width(12.dp))
            Box(modifier = Modifier.size(width = 44.dp, height = 62.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
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
