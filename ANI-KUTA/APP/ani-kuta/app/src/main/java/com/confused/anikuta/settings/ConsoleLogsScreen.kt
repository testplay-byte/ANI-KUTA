package com.confused.anikuta.settings

import android.content.Intent
import android.os.Process
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.confused.anikuta.core.common.LogLevel
import com.confused.anikuta.core.common.RingLogBuffer
import com.confused.anikuta.core.designsystem.component.BackAction
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Console fixed colors (same palette as the debug bubble's console) ──
private val ConsoleBgColor = Color(0xFF1E1E1E)
private val ConsoleCardColor = Color(0xFF2D2D2D)
private val ConsoleTextColor = Color(0xFFE0E0E0)
private val ConsoleTextVariantColor = Color(0xFFE0E0E0).copy(alpha = 0.6f)

private val LevelVerboseColor = Color(0xFF9E9E9E)
private val LevelDebugColor = Color(0xFF4FC3F7)
private val LevelInfoColor = Color(0xFF81C784)
private val LevelWarnColor = Color(0xFFFFB74D)
private val LevelErrorColor = Color(0xFFE57373)

/** Quick filter chips: log namespace presets. */
private data class NamespaceFilter(val label: String, val prefix: String?)

private val NamespaceFilters = listOf(
    NamespaceFilter("All", null),
    // Task 52 (round 12): the whole CS playback pipeline (Resolver + Player +
    // Subs + Watch) — matches the one-filter logcat recipe in doc cloudstream-v2/02.
    NamespaceFilter("CS Playback", "Anikuta:CS:"),
    NamespaceFilter("Cloudstream", "Anikuta:Data:Cloudstream"),
    NamespaceFilter("Resolver", "Anikuta:Core:VideoResolver"),
    NamespaceFilter("Player", "Anikuta:Core:Player"),
    NamespaceFilter("Core", "Anikuta:Core"),
    NamespaceFilter("Data", "Anikuta:Data"),
    NamespaceFilter("Feature", "Anikuta:Feature"),
)

private fun levelColor(level: LogLevel): Color = when (level) {
    LogLevel.VERBOSE -> LevelVerboseColor
    LogLevel.DEBUG -> LevelDebugColor
    LogLevel.INFO -> LevelInfoColor
    LogLevel.WARN -> LevelWarnColor
    LogLevel.ERROR -> LevelErrorColor
    LogLevel.NONE -> LevelVerboseColor
}

private fun formatTime(timestampMillis: Long): String =
    SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestampMillis))

/**
 * Task 49 (round 9 — the console logging tool): a RELEASE-AVAILABLE, filterable
 * in-app log console over [RingLogBuffer].
 *
 * Reachable from Settings → Developer tools → Console logs in EVERY build.
 * The user can:
 *  • filter by text (tag + message substring), level (V/D/I/W/E) and namespace
 *    (Cloudstream / Resolver / Player / … quick chips);
 *  • watch live (1 s auto-refresh) or pause the stream to read;
 *  • copy the filtered view, or EXPORT a full diagnostics report (ring snapshot
 *    + the process's own logcat + version/device header) and share it as a
 *    file — the report is what a device-round bug report should attach.
 */
@Composable
fun ConsoleLogsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    var entries by remember { mutableStateOf<List<RingLogBuffer.Entry>>(emptyList()) }
    var searchFilter by remember { mutableStateOf("") }
    var levelFilter by remember { mutableStateOf(setOf<LogLevel>()) }
    var namespaceFilter by remember { mutableStateOf<String?>(null) }
    var paused by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()

    // Live auto-refresh (1 s) — freezes while paused so the list can be read.
    // Follows the tail ONLY when the user is already at (or near) the bottom,
    // so reading history doesn't fight the autoscroll.
    LaunchedEffect(paused) {
        if (!paused) {
            while (true) {
                val nearBottom = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()
                    ?.let { it.index >= entries.size - 3 } != false
                entries = RingLogBuffer.snapshot()
                if (nearBottom && entries.isNotEmpty()) {
                    runCatching { lazyListState.scrollToItem(entries.lastIndex) }
                }
                kotlinx.coroutines.delay(1_000)
            }
        }
    }

    val filtered = remember(entries, searchFilter, levelFilter, namespaceFilter) {
        entries.filter { entry ->
            (namespaceFilter == null || entry.tag.startsWith(namespaceFilter!!)) &&
                (
                searchFilter.isBlank() ||
                    entry.tag.contains(searchFilter, ignoreCase = true) ||
                    entry.message.contains(searchFilter, ignoreCase = true)
                ) &&
                (levelFilter.isEmpty() || entry.level in levelFilter)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(ConsoleBgColor)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Console logs",
                collapsed = true,
                actions = { BackAction(onBack) },
            )

            // ── Search + copy + clear ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(
                    color = ConsoleCardColor,
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
                            tint = ConsoleTextVariantColor,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        BasicTextField(
                            value = searchFilter,
                            onValueChange = { searchFilter = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = ConsoleTextColor,
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(
                                MaterialTheme.colorScheme.primary,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (searchFilter.isEmpty()) {
                                    Text(
                                        text = "Filter tag or message…",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        color = ConsoleTextVariantColor,
                                    )
                                }
                                inner()
                            },
                        )
                    }
                }
                IconButton(onClick = { paused = !paused }) {
                    Icon(
                        imageVector = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (paused) "Resume" else "Pause",
                        tint = if (paused) LevelInfoColor else ConsoleTextVariantColor,
                    )
                }
                IconButton(onClick = {
                    copyToClipboard(context, renderEntries(filtered))
                }) {
                    Icon(Icons.Filled.ContentCopy, "Copy filtered logs", tint = ConsoleTextVariantColor)
                }
                IconButton(onClick = { RingLogBuffer.clear() }) {
                    Icon(Icons.Filled.Delete, "Clear", tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = { shareLogReport(context) }) {
                    Icon(Icons.Filled.IosShare, "Export and share diagnostics report", tint = LevelWarnColor)
                }
            }

            // ── Namespace quick chips (horizontal scroll) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                NamespaceFilters.forEach { ns ->
                    val selected = namespaceFilter == ns.prefix
                    Surface(
                        color = if (selected) MaterialTheme.colorScheme.primary else ConsoleCardColor,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.clickable { namespaceFilter = ns.prefix },
                    ) {
                        Text(
                            text = ns.label,
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) ConsoleBgColor else ConsoleTextColor,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            // ── Level chips + count ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(
                    LogLevel.VERBOSE to "V",
                    LogLevel.DEBUG to "D",
                    LogLevel.INFO to "I",
                    LogLevel.WARN to "W",
                    LogLevel.ERROR to "E",
                ).forEach { (level, label) ->
                    val selected = level in levelFilter
                    Surface(
                        color = if (selected) levelColor(level) else ConsoleCardColor,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.clickable {
                            levelFilter = if (selected) levelFilter - level else levelFilter + level
                        },
                    ) {
                        Text(
                            text = label,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) ConsoleBgColor else ConsoleTextColor,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${filtered.size}/${entries.size}" + if (paused) " ⏸" else "",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = ConsoleTextVariantColor,
                )
            }

            // ── Log list ──
            Box(modifier = Modifier.fillMaxSize()) {
                if (filtered.isEmpty()) {
                    Text(
                        text = if (entries.isEmpty()) {
                            "No logs captured yet — use the app and come back.\n" +
                                "Everything tagged Anikuta:* lands here (release builds: INFO+)."
                        } else {
                            "No entries match the current filters."
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = ConsoleTextVariantColor,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                } else {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 12.dp, end = 12.dp, bottom = 90.dp,
                        ),
                    ) {
                        itemsIndexed(filtered, key = { index, entry -> "${entry.timestampMillis}-$index" }) { _, entry ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                                Text(
                                    text = formatTime(entry.timestampMillis),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = ConsoleTextVariantColor,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = entry.level.name.first().toString(),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = levelColor(entry.level),
                                )
                                Spacer(Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.message,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = ConsoleTextColor,
                                    )
                                    Text(
                                        text = entry.tag,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = ConsoleTextVariantColor,
                                    )
                                    entry.throwableString?.let {
                                        Text(
                                            text = it,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = LevelErrorColor,
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

// ── Export / share / clipboard ──────────────────────────────────────────────

private fun renderEntries(entries: List<RingLogBuffer.Entry>): String =
    entries.joinToString("\n") { e ->
        "${formatTime(e.timestampMillis)} ${e.level.name.first()} ${e.tag}: ${e.message}" +
            (e.throwableString?.let { "\n$it" } ?: "")
    }

private fun copyToClipboard(context: android.content.Context, text: String) {
    val clipboard = android.content.ClipData.newPlainText("ANI-KUTA logs", text)
    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
        as android.content.ClipboardManager
    cm.setPrimaryClip(clipboard)
}

/** Bounded own-process logcat dump — captures mpv/OkHttp native lines the ring can't. */
private fun ownLogcatDump(maxBytes: Int = 2_000_000): String = runCatching {
    val process = Runtime.getRuntime()
        .exec(arrayOf("logcat", "-d", "-v", "time", "--pid=${Process.myPid()}"))
    val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
    process.waitFor()
    if (output.length > maxBytes) output.take(maxBytes) + "\n… (logcat truncated at $maxBytes chars)" else output
}.getOrDefault("(logcat dump unavailable: ${android.os.Build.VERSION.SDK_INT})")

/**
 * Assembles the diagnostics report: header (version/device/time), the ring
 * snapshot, and the own-process logcat — then hands it to the system share
 * sheet as a cache-dir file via the existing FileProvider.
 */
private fun shareLogReport(context: android.content.Context) {
    runCatching {
        val header = buildString {
            appendLine("ANI-KUTA diagnostics report")
            appendLine("version: ${com.confused.anikuta.BuildConfig.VERSION_NAME} (${com.confused.anikuta.BuildConfig.VERSION_CODE})")
            appendLine("buildType: ${if (com.confused.anikuta.BuildConfig.DEBUG) "debug" else "release"}")
            appendLine("device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE}, API ${android.os.Build.VERSION.SDK_INT})")
            appendLine("time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine("ring: ${RingLogBuffer.size()} entries")
            appendLine()
        }
        val ringSection = "──── ring buffer (${RingLogBuffer.size()} entries) ────\n" +
            renderEntries(RingLogBuffer.snapshot())
        val logcatSection = "\n\n──── own-process logcat ────\n" + ownLogcatDump()
        val report = header + ringSection + logcatSection

        val logsDir = File(context.cacheDir, "logs").apply { mkdirs() }
        val file = File(logsDir, "anikuta-logs-${System.currentTimeMillis()}.txt")
        file.writeText(report)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "ANI-KUTA diagnostics report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri("log report", uri)
        }
        context.startActivity(Intent.createChooser(intent, "Share ANI-KUTA logs"))
    }.onFailure {
        android.util.Log.e("Anikuta:Settings:Console", "share failed: ${it.message}")
    }
}
