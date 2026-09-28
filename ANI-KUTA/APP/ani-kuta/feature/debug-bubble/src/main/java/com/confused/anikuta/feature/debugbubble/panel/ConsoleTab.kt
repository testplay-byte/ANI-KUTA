package com.confused.anikuta.feature.debugbubble.panel

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.common.LogLevel
import com.confused.anikuta.feature.debugbubble.data.DebugLogcatReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ROUND 100 (D-683) — THE CONSOLE TAB (the debug line's proper logging tool,
 * rebuilt). The round-24 removal ("remove the console logs only") is honored
 * in spirit: nothing logs MORE than before — the tab only makes the lines
 * the app ALREADY emits browsable on-device, which is what the round-99
 * report asked for ("proper console logging" + a better "logcat logging
 * tool" on the debug version).
 *
 * Two sources, one honest scope each:
 *  - APP CONSOLE — the Logger's bounded in-memory ring buffer: every line
 *    our own code logs, captured with timestamp/level/tag + throwable
 *    digests. Process memory only (never persisted), but it never misses a
 *    line that passed the Logger's level gate.
 *  - LOGCAT — a live `logcat --pid=<our pid>` stream: everything the
 *    process prints, including output that never goes through our Logger
 *    (OkHttp's client, ART's GC notes, system warnings about us). Starts
 *    with the most recent single line and follows every new one; the
 *    logcat process is destroyed the moment the tab leaves the source.
 *
 * The tooling around the feed (the parts that make it "proper"):
 *  - a LEVEL filter (min severity, one tap);
 *  - a SEARCH box (tag or message, case-insensitive);
 *  - FOLLOW ↔ PAUSE (auto-scroll to the tail vs freeze for reading);
 *  - COPY-ALL / SHARE / CLEAR actions;
 *  - tap any line to copy just that line.
 *
 * Fixed dark-terminal palette (deliberately independent of the theme — the
 * DebugPanel family's rule: debug surfaces keep constant colors so they
 * read the same in light and dark mode).
 */
@Composable
fun ConsoleTab() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val reader = remember { DebugLogcatReader() }

    var source by remember { mutableStateOf(ConsoleSource.APP) }
    var minLevel by remember { mutableStateOf(LogLevel.VERBOSE) }
    var query by remember { mutableStateOf("") }
    var follow by remember { mutableStateOf(true) }

    // ── The app-console feed: poll the Logger's revision (cheap volatile
    // read), snapshot the ring buffer only when it actually changed. ──
    var appEntries by remember { mutableStateOf<List<Logger.ConsoleEntry>>(emptyList()) }
    var lastRevision by remember { mutableLongStateOf(Logger.consoleRevisionNow) }
    LaunchedEffect(Unit) {
        // Prime the visible snapshot once (the poll refreshes on change).
        appEntries = Logger.consoleSnapshot()
        lastRevision = Logger.consoleRevisionNow
        while (true) {
            kotlinx.coroutines.delay(400)
            val now = Logger.consoleRevisionNow
            if (now != lastRevision) {
                lastRevision = now
                appEntries = Logger.consoleSnapshot()
            }
        }
    }

    // ── The logcat feed: collect the process stream into a bounded local
    // buffer for the WHOLE time the Console tab is composed (switching to
    // the app-console source just changes what renders — the logcat buffer
    // keeps filling so no lines are lost). Leaving the tab cancels the
    // collection — the reader destroys its process. ──
    val logcatLines = remember { mutableStateListOf<DebugLogcatReader.LogcatLine>() }
    DisposableEffect(Unit) {
        onDispose { logcatLines.clear() }
    }
    LaunchedEffect(Unit) {
        reader.stream().collect { line ->
            if (logcatLines.size >= LOGCAT_CAP) {
                logcatLines.removeAt(0)
            }
            logcatLines.add(line)
        }
    }

    // ── The rendered rows (filtered by level + search). ──
    val needle = query.trim().lowercase(Locale.ROOT)
    val rows: List<ConsoleRow> = when (source) {
        ConsoleSource.APP -> appEntries
            .filter { it.level.severity >= minLevel.severity }
            .filter { entry ->
                needle.isEmpty() ||
                    entry.tag.lowercase(Locale.ROOT).contains(needle) ||
                    entry.message.lowercase(Locale.ROOT).contains(needle)
            }
            .map { entry ->
                ConsoleRow(
                    time = TIME_FORMAT.format(Date(entry.atMs)),
                    level = entry.level.name.first(),
                    tag = entry.tag,
                    text = entry.message + (entry.errorDigest?.let { "\n$it" } ?: ""),
                )
            }
        ConsoleSource.LOGCAT -> logcatLines
            .filter { row ->
                val severity = when (row.level) {
                    'V' -> 0
                    'D' -> 1
                    'I' -> 2
                    'W' -> 3
                    'E', 'F' -> 4
                    else -> 0
                }
                severity >= minLevel.severity
            }
            .filter { row -> needle.isEmpty() || row.raw.lowercase(Locale.ROOT).contains(needle) }
            .map { row ->
                ConsoleRow(
                    time = extractLogcatTime(row.raw),
                    level = row.level ?: ' ',
                    tag = row.tag ?: "",
                    text = extractLogcatMessage(row.raw),
                )
            }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(rows.size, follow) {
        if (follow && rows.isNotEmpty()) {
            listState.scrollToItem(rows.size - 1)
        }
    }

    fun exportText(): String = rows.joinToString("\n") { row ->
        "${row.time} ${row.level}/${row.tag}: ${row.text}"
    }

    fun copyAll() {
        clipboard.setText(AnnotatedString(exportText().ifEmpty { "(no lines)" }))
        Toast.makeText(context, "Copied ${rows.size} line(s)", Toast.LENGTH_SHORT).show()
    }

    fun share() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, exportText().ifEmpty { "(no lines)" })
        }
        context.startActivity(Intent.createChooser(intent, "Share console log"))
    }

    fun clear() {
        when (source) {
            ConsoleSource.APP -> Logger.clearConsole()
            ConsoleSource.LOGCAT -> logcatLines.clear()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Source selector + actions ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ConsoleSource.entries.forEach { s ->
                val selected = s == source
                Surface(
                    color = if (selected) ConsoleAccent else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { source = s },
                ) {
                    Text(
                        text = s.label,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) ConsoleBg else ConsoleText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { follow = !follow }, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = if (follow) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (follow) "Pause auto-scroll" else "Follow the tail",
                    tint = if (follow) ConsoleAccent else ConsoleDim,
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(onClick = { copyAll() }, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "Copy all lines",
                    tint = ConsoleDim,
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(onClick = { share() }, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = "Share the console log",
                    tint = ConsoleDim,
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(onClick = { clear() }, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Filled.DeleteSweep,
                    contentDescription = "Clear",
                    tint = ConsoleDim,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // ── Level filter + search ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        ) {
            LevelChip("All", minLevel == LogLevel.VERBOSE) { minLevel = LogLevel.VERBOSE }
            LevelChip("D", minLevel == LogLevel.DEBUG) { minLevel = LogLevel.DEBUG }
            LevelChip("I", minLevel == LogLevel.INFO) { minLevel = LogLevel.INFO }
            LevelChip("W", minLevel == LogLevel.WARN) { minLevel = LogLevel.WARN }
            LevelChip("E", minLevel == LogLevel.ERROR) { minLevel = LogLevel.ERROR }
            Spacer(Modifier.width(6.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = ConsoleText,
                ),
                placeholder = {
                    Text(
                        "filter…",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = ConsoleDim,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = ConsoleDim,
                        modifier = Modifier.size(14.dp),
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ConsoleText,
                    unfocusedTextColor = ConsoleText,
                    focusedBorderColor = ConsoleAccent,
                    unfocusedBorderColor = ConsoleLine,
                    cursorColor = ConsoleAccent,
                    focusedLeadingIconColor = ConsoleDim,
                    unfocusedLeadingIconColor = ConsoleDim,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            )
        }

        // ── The feed ──
        Spacer(Modifier.height(6.dp))
        Surface(
            color = ConsoleBg,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            if (rows.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    Text(
                        text = when (source) {
                            ConsoleSource.APP ->
                                "No captured lines yet — the app console captures " +
                                    "everything the app logs (debug line only)."
                            ConsoleSource.LOGCAT ->
                                "Waiting for logcat lines from this process…"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = ConsoleDim,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    itemsIndexed(rows) { _, row ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    clipboard.setText(
                                        AnnotatedString(
                                            "${row.time} ${row.level}/${row.tag}: ${row.text}",
                                        ),
                                    )
                                    Toast.makeText(context, "Line copied", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 2.dp),
                        ) {
                            Text(
                                text = "${row.time} ${row.level}/${row.tag}: ",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = levelColor(row.level),
                            )
                            Text(
                                text = row.text,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                lineHeight = 13.sp,
                                color = ConsoleText,
                                maxLines = 8,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Which feed the console shows. */
private enum class ConsoleSource(val label: String) {
    APP("App console"),
    LOGCAT("Logcat"),
}

/** One rendered console line (normalized across both sources). */
private data class ConsoleRow(
    val time: String,
    val level: Char,
    val tag: String,
    val text: String,
)

/** "MM-DD HH:MM:SS.mmm ..." → "HH:MM:SS.mmm" (logcat's clock field). */
private fun extractLogcatTime(raw: String): String {
    val first = raw.indexOf(' ')
    if (first <= 0) return ""
    val second = raw.indexOf(' ', first + 1)
    return if (second > first) raw.substring(first + 1, second) else raw.substring(first + 1)
}

/** "MM-DD HH:MM:SS.mmm L/Tag( pid): message" → "message". */
private fun extractLogcatMessage(raw: String): String {
    val idx = raw.indexOf("): ")
    return if (idx >= 0) raw.substring(idx + 3) else raw
}

@Composable
private fun LevelChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) ConsoleAccent.copy(alpha = 0.25f) else Color.Transparent,
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) ConsoleAccent else ConsoleLine,
        ),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) ConsoleAccent else ConsoleDim,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** The per-level accent on the terminal palette. */
private fun levelColor(level: Char): Color = when (level) {
    'V' -> ConsoleDim
    'D' -> Color(0xFF7EC8E3)
    'I' -> Color(0xFF81C784)
    'W' -> Color(0xFFFFB74D)
    'E', 'F' -> Color(0xFFE57373)
    else -> ConsoleDim
}

// ── The fixed terminal palette (constant across themes — the debug-panel rule) ──
private val ConsoleBg = Color(0xFF16110E) // warm near-black (the bubble family's tint)
private val ConsoleText = Color(0xFFF5E6D3) // the panel's cream
private val ConsoleDim = Color(0xFFF5E6D3).copy(alpha = 0.55f)
private val ConsoleLine = Color(0xFFD4A574).copy(alpha = 0.35f) // the panel's amber, dimmed
private val ConsoleAccent = Color(0xFFE8C170) // the panel's golden label hue

/** The logcat feed's local buffer bound (matches the app console's ring). */
private const val LOGCAT_CAP = 2500

/** App-console capture time format (clock time — the console is one session). */
private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
