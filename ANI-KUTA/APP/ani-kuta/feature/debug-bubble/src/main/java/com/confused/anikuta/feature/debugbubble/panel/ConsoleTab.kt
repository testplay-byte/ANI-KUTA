package com.confused.anikuta.feature.debugbubble.panel

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.common.LogLevel
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.feature.debugbubble.data.DebugLogBuffer
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Console tab — an in-memory log viewer (Phase DB-4).
 *
 * Shows the last 10,000 log entries from [DebugLogBuffer] (wired into Logger
 * via `Logger.setAppender`). Filterable by tag (text input) + level (V/D/I/W/E
 * multi-select chips). Color-coded by level. Clear button wipes the buffer.
 *
 * Data is fetched on tab open + on Refresh (no polling — the buffer is a
 * snapshot). Auto-scrolls to the bottom (newest) on open.
 *
 * CORE_RULES §20: the buffer itself doesn't log (would recurse).
 */
@Composable
fun ConsoleTab(
    minimized: Boolean = false,
) {
    val buffer = koinInject<DebugLogBuffer>()
    val context = LocalContext.current

    var entries by remember { mutableStateOf<List<DebugLogBuffer.LogEntry>>(emptyList()) }
    var tagFilter by remember { mutableStateOf("") }
    var levelFilter by remember { mutableStateOf(setOf<LogLevel>()) }
    var refreshTrigger by remember { mutableStateOf(0) }
    val lazyListState = rememberLazyListState()

    // Auto-refresh: poll the buffer every 1 second (live updating).
    LaunchedEffect(Unit) {
        while (true) {
            entries = buffer.snapshot()
            if (entries.isNotEmpty()) {
                lazyListState.scrollToItem(entries.lastIndex)
            }
            kotlinx.coroutines.delay(1000)
        }
    }

    // Load the snapshot on open + on Refresh.
    LaunchedEffect(refreshTrigger) {
        entries = buffer.snapshot()
        // Auto-scroll to the bottom (newest).
        if (entries.isNotEmpty()) {
            lazyListState.scrollToItem(entries.lastIndex)
        }
    }

    // Filtered entries (applied locally — no re-query needed).
    val filtered = remember(entries, tagFilter, levelFilter) {
        entries.filter { entry ->
            (tagFilter.isBlank() || entry.tag.contains(tagFilter, ignoreCase = true)) &&
                (levelFilter.isEmpty() || entry.level in levelFilter)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // In minimized mode: hide all buttons (search/copy/delete), show only
        // the live console list. Auto-refresh every 1s.
        // In full mode: show all buttons.
        if (!minimized) {
        // ── Tag filter + copy + clear ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = tagFilter,
                        onValueChange = { tagFilter = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = RobotoFamily,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (tagFilter.isEmpty()) {
                                Text(
                                    text = "Filter by tag…",
                                    fontFamily = RobotoFamily,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        },
                    )
                }
            }
            IconButton(onClick = {
                // Copy the filtered logs to the clipboard.
                val text = filtered.joinToString("\n") { e ->
                    "${SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(e.timestamp))} ${e.level.name.first()} ${e.tag}: ${e.message}" +
                        (e.throwableString?.let { "\n$it" } ?: "")
                }
                val clipboard = android.content.ClipData.newPlainText("Debug logs", text)
                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(clipboard)
            }) {
                Icon(Icons.Filled.ContentCopy, "Copy", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = {
                buffer.clear()
                refreshTrigger++
            }) {
                Icon(Icons.Filled.Delete, "Clear", tint = MaterialTheme.colorScheme.error)
            }
        }

        // ── Level filter chips ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                LogLevel.VERBOSE to "V",
                LogLevel.DEBUG to "D",
                LogLevel.INFO to "I",
                LogLevel.WARN to "W",
                LogLevel.ERROR to "E",
            ).forEach { (level, label) ->
                val isSelected = level in levelFilter
                val bg = if (isSelected) levelColor(level).copy(alpha = 0.25f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                val fg = if (isSelected) levelColor(level)
                else MaterialTheme.colorScheme.onSurfaceVariant
                Surface(
                    color = bg,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.clickable {
                        levelFilter = if (isSelected) levelFilter - level else levelFilter + level
                    },
                ) {
                    Text(
                        text = label,
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = fg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "${filtered.size}/${entries.size}",
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
        }  // end if (!minimized)

        Spacer(Modifier.height(4.dp))

        // ── Log entries list ──
        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (entries.isEmpty()) "No logs yet" else "No matches",
                    fontFamily = RobotoFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(filtered) { entry ->
                    LogEntryRow(entry)
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: DebugLogBuffer.LogEntry) {
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val color = levelColor(entry.level)
    // Per-entry background color by level (per user: "dedicated background color
    // depending on what it is"). ERROR = red tint, WARN = orange tint, etc.
    val bgColor = when (entry.level) {
        LogLevel.ERROR -> Color(0xFFEF4433).copy(alpha = 0.12f)
        LogLevel.WARN -> Color(0xFFFF9800).copy(alpha = 0.10f)
        LogLevel.INFO -> Color(0xFF4CAF50).copy(alpha = 0.08f)
        LogLevel.DEBUG -> Color(0xFF2196F3).copy(alpha = 0.06f)
        LogLevel.VERBOSE -> Color(0xFF9E9E9E).copy(alpha = 0.05f)
        LogLevel.NONE -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = timeFmt.format(Date(entry.timestamp)),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = entry.level.name.first().toString(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
                Text(
                    text = entry.tag,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = entry.message,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (entry.throwableString != null) {
                Text(
                    text = entry.throwableString,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Color for each log level. */
@Composable
private fun levelColor(level: LogLevel): Color = when (level) {
    LogLevel.VERBOSE -> Color(0xFF9E9E9E)  // gray
    LogLevel.DEBUG -> Color(0xFF2196F3)    // blue
    LogLevel.INFO -> Color(0xFF4CAF50)     // green
    LogLevel.WARN -> Color(0xFFFF9800)     // orange
    LogLevel.ERROR -> Color(0xFFF44336)    // red
    LogLevel.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
}
