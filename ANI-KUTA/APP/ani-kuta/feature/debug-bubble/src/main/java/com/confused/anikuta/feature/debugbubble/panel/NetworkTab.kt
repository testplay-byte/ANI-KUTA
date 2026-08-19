package com.confused.anikuta.feature.debugbubble.panel

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.feature.debugbubble.data.DebugDbStats
import com.confused.anikuta.feature.debugbubble.data.DebugNetworkStats
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Network tab colors (match the panel's coral/sienna theme).
private val NetCardColor = Color(0xFF8B4A3A)
private val NetTextColor = Color(0xFFF5E6D3)
private val NetTextVariantColor = Color(0xFFF5E6D3).copy(alpha = 0.6f)
private val NetBorderColor = Color(0xFFD4A574)
private val NetLabelColor = Color(0xFFE8C170)
private val NetGreen = Color(0xFF66BB6A)
private val NetBlue = Color(0xFF42A5F5)
private val NetOrange = Color(0xFFFFA726)
private val NetRed = Color(0xFFEF5350)
private val NetPurple = Color(0xFFAB47BC)
private val NetGray = Color(0xFF9E9E9E)
private val NetCyan = Color(0xFF26C6DA)  // for reads (SELECT)

@Composable
fun NetworkTab(
    minimized: Boolean = false,
    viewMode: String = "network",
    onViewModeChange: (String) -> Unit = {},
    onViewInDb: (table: String, filterCol: String, filterVal: String) -> Unit = { _, _, _ -> },
) {
    val stats = koinInject<DebugNetworkStats>()
    val dbStats = koinInject<DebugDbStats>()
    var snapshot by remember { mutableStateOf(DebugNetworkStats.NetworkSnapshot.EMPTY) }
    var dbSnapshot by remember { mutableStateOf(DebugDbStats.DbSnapshot.EMPTY) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // Auto-refresh every 2 seconds (live updating).
    // This also drives the chart's time-series gap-fill (DebugNetworkStats
    // .snapshot() calls advanceToNow() which inserts zero-buckets for idle
    // seconds → the chart slides forward even with zero traffic).
    LaunchedEffect(Unit) {
        while (true) {
            snapshot = stats.snapshot()
            dbSnapshot = dbStats.snapshot()
            kotlinx.coroutines.delay(2000)
        }
    }
    LaunchedEffect(refreshTrigger) {
        snapshot = stats.snapshot()
        dbSnapshot = dbStats.snapshot()
    }

    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val context = LocalContext.current

    // ── SAF launchers for exporting logs ──
    // Uses Storage Access Framework (CreateDocument) — same pattern as
    // DatabaseTab's JSON export. The user picks a file location; we write
    // the log text on a background thread.
    val exportNetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) {
            Thread {
                val text = stats.exportAsText()
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(text.toByteArray())
                }
            }.start()
        }
    }
    val exportDbLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) {
            Thread {
                val text = dbStats.exportAsText()
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(text.toByteArray())
                }
            }.start()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // ── View switcher (Network / DB Activity) — hidden when minimized ──
        // The viewMode state is HOISTED to DebugPanel so it survives the
        // EXPANDED↔MINIMIZED transition (previously it was local to this
        // composable and was lost on minimize, causing the mini-window to
        // always fall back to the Network view).
        if (!minimized) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf("network" to "Network", "db" to "DB Activity").forEach { (mode, label) ->
                    val isSelected = viewMode == mode
                    Surface(
                        color = if (isSelected) NetBorderColor.copy(alpha = 0.3f) else NetCardColor,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) NetBorderColor else NetBorderColor.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f).clickable { onViewModeChange(mode) },
                    ) {
                        Text(
                            text = label,
                            fontFamily = RobotoFamily,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) NetTextColor else NetTextVariantColor,
                            modifier = Modifier.padding(vertical = 6.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }

        if (viewMode == "db") {
            // ── DB Activity view ──
            // Shows real-time DB events tracked by DebugDbStats (wired via
            // DebugSqlDriverWrapper → wrapDebugSqlDriver). Every SELECT,
            // INSERT, UPDATE, DELETE, REPLACE that flows through SqlDriver
            // is recorded.
            DbActivityContent(
                dbSnapshot = dbSnapshot,
                minimized = minimized,
                timeFmt = timeFmt,
                onClear = { dbStats.clear(); refreshTrigger++ },
                onExport = { exportDbLauncher.launch("anikuta_db_activity_${System.currentTimeMillis()}.log") },
                onViewInDb = onViewInDb,
            )
        } else {
        // ── Network view ──

        // ── Header (refresh + export + clear) — hidden when minimized ──
        if (!minimized) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = { refreshTrigger++ }) {
                    Icon(Icons.Filled.Refresh, "Refresh", tint = NetTextVariantColor, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { exportNetLauncher.launch("anikuta_network_${System.currentTimeMillis()}.log") }) {
                    Icon(Icons.Filled.Download, "Export network log", tint = NetLabelColor, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { stats.clear(); refreshTrigger++ }) {
                    Icon(Icons.Filled.Delete, "Clear", tint = NetRed, modifier = Modifier.size(18.dp))
                }
            }
        }

        // ── Bandwidth summary (4 stat cards) ──
        // In minimized mode: smaller text + compact layout.
        val statFontSize = if (minimized) 11.sp else 14.sp
        val statLabelSize = if (minimized) 8.sp else 9.sp
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            NetStatCard("Req", snapshot.totalRequests.toString(), NetBlue, Modifier.weight(1f), statFontSize, statLabelSize)
            NetStatCard("Recv", formatBytes(snapshot.totalBytesReceived), NetGreen, Modifier.weight(1f), statFontSize, statLabelSize)
            NetStatCard("Sent", formatBytes(snapshot.totalBytesSent), NetOrange, Modifier.weight(1f), statFontSize, statLabelSize)
            NetStatCard("Err", snapshot.errorCount.toString(), NetRed, Modifier.weight(1f), statFontSize, statLabelSize)
        }

        // ── 5-minute graphs (requests + data usage) ──
        // Always shown — even when empty (flat line at zero). Per user:
        // "show the graph even when nothing has been done."
        //
        // X-axis is TIMESTAMP-based (not bucket-index-based) so the chart
        // slides forward in real time. Each bucket's X position is
        // `(bucket.timestamp - windowStart) / windowSpan * width`. Zero-
        // valued gap-fill buckets (from DebugNetworkStats.advanceToNow)
        // appear as a flat baseline at the bottom that moves left over time.
        // The window is the last 5 minutes (300_000 ms).
        val nowMs = System.currentTimeMillis()
        val windowSpan = 300_000L  // 5 minutes in ms
        val windowStart = nowMs - windowSpan

            // Requests over time graph.
            NetSectionCard("Requests (5 min)") {
                val maxReq = snapshot.timeSeries.maxOfOrNull { it.requestCount }?.coerceAtLeast(1) ?: 1
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (minimized) 30.dp else 50.dp),
                ) {
                    val w = size.width
                    val h = size.height
                    val n = snapshot.timeSeries.size
                    if (n >= 1) {
                        // Timestamp → X: map [windowStart, nowMs] → [0, w].
                        fun tsToX(ts: Long): Float =
                            ((ts - windowStart).toFloat() / windowSpan.toFloat() * w).coerceIn(0f, w)

                        val strokePath = androidx.compose.ui.graphics.Path()
                        val fillPath = androidx.compose.ui.graphics.Path()
                        // Start the fill at the bottom-left of the first bucket's X.
                        val firstX = tsToX(snapshot.timeSeries.first().timestamp)
                        fillPath.moveTo(firstX, h)
                        snapshot.timeSeries.forEachIndexed { i, bucket ->
                            val x = tsToX(bucket.timestamp)
                            val y = h - (bucket.requestCount.toFloat() / maxReq) * h
                            if (i == 0) strokePath.moveTo(x, y) else strokePath.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }
                        // Close the fill at the right edge.
                        val lastX = tsToX(snapshot.timeSeries.last().timestamp)
                        fillPath.lineTo(lastX, h)
                        fillPath.close()
                        drawPath(fillPath, color = NetBlue.copy(alpha = 0.15f))
                        drawPath(strokePath, color = NetBlue, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                    }
                }
                Text(
                    text = "Peak: $maxReq req/s",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = NetTextVariantColor,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            // Data usage over time graph.
            NetSectionCard("Data usage (5 min)") {
                val maxBytes = snapshot.timeSeries.maxOfOrNull { it.bytesReceived }?.coerceAtLeast(1L) ?: 1L
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (minimized) 30.dp else 50.dp),
                ) {
                    val w = size.width
                    val h = size.height
                    val n = snapshot.timeSeries.size
                    if (n >= 1) {
                        fun tsToX(ts: Long): Float =
                            ((ts - windowStart).toFloat() / windowSpan.toFloat() * w).coerceIn(0f, w)

                        val strokePath = androidx.compose.ui.graphics.Path()
                        val fillPath = androidx.compose.ui.graphics.Path()
                        val firstX = tsToX(snapshot.timeSeries.first().timestamp)
                        fillPath.moveTo(firstX, h)
                        snapshot.timeSeries.forEachIndexed { i, bucket ->
                            val x = tsToX(bucket.timestamp)
                            val y = h - (bucket.bytesReceived.toFloat() / maxBytes) * h
                            if (i == 0) strokePath.moveTo(x, y) else strokePath.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }
                        val lastX = tsToX(snapshot.timeSeries.last().timestamp)
                        fillPath.lineTo(lastX, h)
                        fillPath.close()
                        drawPath(fillPath, color = NetGreen.copy(alpha = 0.15f))
                        drawPath(strokePath, color = NetGreen, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                    }
                }
                Text(
                    text = "Peak: ${formatBytes(maxBytes)}/s",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = NetTextVariantColor,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

        // ── Status codes (compact bars) ──
        NetSectionCard("Status codes") {
            val statusData = listOf(
                "2xx" to snapshot.statusBuckets[0] to NetGreen,
                "3xx" to snapshot.statusBuckets[1] to NetBlue,
                "4xx" to snapshot.statusBuckets[2] to NetOrange,
                "5xx" to snapshot.statusBuckets[3] to NetRed,
                "err" to snapshot.statusBuckets[4] to NetGray,
            )
            val maxCount = statusData.maxOf { it.first.second }.coerceAtLeast(1)
            statusData.forEach { (pair, color) ->
                val (label, count) = pair
                val barWidth = (count.toFloat() / maxCount).coerceIn(0f, 1f)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.width(30.dp))
                    Box(modifier = Modifier.weight(1f).height(10.dp)) {
                        Box(modifier = Modifier.fillMaxSize().background(color.copy(alpha = 0.15f), RoundedCornerShape(2.dp)))
                        Box(modifier = Modifier.fillMaxWidth(barWidth).fillMaxSize().background(color, RoundedCornerShape(2.dp)))
                    }
                    Text(count.toString(), fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NetTextColor, modifier = Modifier.padding(start = 6.dp).width(30.dp))
                }
            }
        }

        // ── Request categories ──
        NetSectionCard("Categories") {
            val catData = DebugNetworkStats.RequestCategory.values()
            val catColors = listOf(NetPurple, NetGreen, NetBlue, NetGray)
            val maxCat = snapshot.categoryCounts.maxOrNull()?.coerceAtLeast(1) ?: 1
            catData.forEachIndexed { idx, cat ->
                val count = snapshot.categoryCounts.getOrElse(idx) { 0 }
                val color = catColors[idx]
                val barWidth = (count.toFloat() / maxCat).coerceIn(0f, 1f)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(cat.label, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.width(70.dp))
                    Box(modifier = Modifier.weight(1f).height(10.dp)) {
                        Box(modifier = Modifier.fillMaxSize().background(color.copy(alpha = 0.15f), RoundedCornerShape(2.dp)))
                        Box(modifier = Modifier.fillMaxWidth(barWidth).fillMaxSize().background(color, RoundedCornerShape(2.dp)))
                    }
                    Text(count.toString(), fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NetTextColor, modifier = Modifier.padding(start = 6.dp).width(30.dp))
                }
            }
        }

        // ── Top hosts (per-source breakdown) ──
        if (snapshot.hostCounts.isNotEmpty()) {
            NetSectionCard("Top sources") {
                val topHosts = snapshot.hostCounts.entries.sortedByDescending { it.value }.take(5)
                val maxHost = topHosts.maxOf { it.value }.coerceAtLeast(1)
                topHosts.forEach { (host, count) ->
                    val barWidth = (count.toFloat() / maxHost).coerceIn(0f, 1f)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(host, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = NetTextVariantColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Box(modifier = Modifier.width(60.dp).height(8.dp)) {
                            Box(modifier = Modifier.fillMaxSize().background(NetBorderColor.copy(alpha = 0.15f), RoundedCornerShape(2.dp)))
                            Box(modifier = Modifier.fillMaxWidth(barWidth).fillMaxSize().background(NetBorderColor, RoundedCornerShape(2.dp)))
                        }
                        Text(count.toString(), fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NetTextColor, modifier = Modifier.padding(start = 6.dp).width(24.dp))
                    }
                }
            }
        }

        // ── Extension traffic caveat ──
        Surface(
            color = Color.Black.copy(alpha = 0.15f),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        ) {
            Text(
                text = "Extension HTTP calls (Injekt) are not captured.",
                fontFamily = RobotoFamily,
                fontSize = 9.sp,
                color = NetTextVariantColor,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        // ── Recent requests ──
        if (!minimized) {
            Text(
                text = "Recent (${snapshot.recentRequests.size})",
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = NetLabelColor,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        if (snapshot.recentRequests.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                Text("No requests yet", fontFamily = RobotoFamily, fontSize = 12.sp, color = NetTextVariantColor)
            }
        } else {
            snapshot.recentRequests.reversed().forEach { req ->
                NetRequestRow(req, timeFmt)
            }
        }
        }  // end else (network view)
    }
}

@Composable
private fun NetStatCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    valueFontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    labelFontSize: androidx.compose.ui.unit.TextUnit = 9.sp,
) {
    Surface(
        color = NetCardColor,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Text(value, fontFamily = RobotoFamily, fontSize = valueFontSize, fontWeight = FontWeight.Bold, color = NetTextColor, maxLines = 1)
            Text(label, fontFamily = RobotoFamily, fontSize = labelFontSize, color = NetTextVariantColor)
        }
    }
}

@Composable
private fun NetSectionCard(title: String, content: @Composable () -> Unit) {
    Surface(
        color = NetCardColor,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NetBorderColor.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(title, fontFamily = RobotoFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NetLabelColor, modifier = Modifier.padding(bottom = 6.dp))
            content()
        }
    }
}

@Composable
private fun NetRequestRow(req: DebugNetworkStats.RequestRecord, timeFmt: SimpleDateFormat) {
    val statusColor = when {
        req.status < 0 -> NetGray
        req.status < 300 -> NetGreen
        req.status < 400 -> NetBlue
        req.status < 500 -> NetOrange
        else -> NetRed
    }
    val catColor = when (req.category) {
        DebugNetworkStats.RequestCategory.METADATA -> NetPurple
        DebugNetworkStats.RequestCategory.VIDEO -> NetGreen
        DebugNetworkStats.RequestCategory.IMAGE -> NetBlue
        DebugNetworkStats.RequestCategory.OTHER -> NetGray
    }
    Surface(
        color = Color.Black.copy(alpha = 0.15f),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(timeFmt.format(Date(req.timestamp)), fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = NetTextVariantColor)
                Text(req.method, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = NetTextColor)
                Text(req.category.label.take(3).uppercase(), fontFamily = FontFamily.Monospace, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = catColor)
                Text(if (req.status < 0) "ERR" else req.status.toString(), fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = statusColor)
                Text("${req.latencyMs}ms", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = NetTextVariantColor, modifier = Modifier.weight(1f))
                if (req.bytes > 0) Text(formatBytes(req.bytes), fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = NetTextVariantColor)
            }
            Text("${req.host}${req.path}", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = NetTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (req.error != null) {
                Text(req.error, fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = NetRed, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1fMB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.1fKB".format(bytes / 1_000.0)
    else -> "${bytes}B"
}

/* ---------------------------------------------------------------------------
 * DB Activity view — shows real-time DB events (reads + writes) tracked by
 * DebugDbStats. Mirrors the Network view's layout: stat cards, a 5-min
 * reads+writes/sec chart (timestamp-based X-axis, slides forward every 2s),
 * top tables, + a filterable recent-events list. Tapping an event navigates
 * to the Database tab with the affected table pre-selected.
 * ------------------------------------------------------------------------- */

/** Colors for the DB Activity view's reads/writes chart. */
private val DbWritesChartColor = Color(0xFFE8C170)  // gold (writes)
private val DbReadsChartColor = Color(0xFF26C6DA)   // cyan (reads)

@Composable
private fun DbActivityContent(
    dbSnapshot: DebugDbStats.DbSnapshot,
    minimized: Boolean,
    timeFmt: SimpleDateFormat,
    onClear: () -> Unit,
    onExport: () -> Unit,
    onViewInDb: (table: String, filterCol: String, filterVal: String) -> Unit,
) {
    // ── Event filter state (All / Reads / Writes) — hoisted here so it
    // survives recompositions. Default is "writes" since reads are far
    // more frequent and would flood the list. ──
    var eventFilter by remember { mutableStateOf("writes") }

    // ── Header (export + clear buttons) — hidden when minimized ──
    if (!minimized) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = onExport) {
                Icon(Icons.Filled.Download, "Export DB activity log", tint = NetLabelColor, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Delete, "Clear", tint = NetRed, modifier = Modifier.size(18.dp))
            }
        }
    }

    // ── Stat cards (5): reads, writes, inserts, updates, deletes ──
    val statFontSize = if (minimized) 11.sp else 14.sp
    val statLabelSize = if (minimized) 8.sp else 9.sp
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        NetStatCard("Reads", dbSnapshot.totalReads.toString(), DbReadsChartColor, Modifier.weight(1f), statFontSize, statLabelSize)
        NetStatCard("Writes", dbSnapshot.totalWrites.toString(), DbWritesChartColor, Modifier.weight(1f), statFontSize, statLabelSize)
        NetStatCard("Ins", dbSnapshot.insertCount.toString(), NetGreen, Modifier.weight(1f), statFontSize, statLabelSize)
        NetStatCard("Upd", dbSnapshot.updateCount.toString(), NetBlue, Modifier.weight(1f), statFontSize, statLabelSize)
        NetStatCard("Del", dbSnapshot.deleteCount.toString(), NetRed, Modifier.weight(1f), statFontSize, statLabelSize)
    }

    // ── 5-minute reads+writes/sec chart (dual-line) ──
    // Same timestamp-based X-axis as the Network charts so it slides forward
    // every 2s even with zero activity (gap-filled by DebugDbStats.advanceToNow).
    // Two overlaid lines: cyan (reads) + gold (writes). The max is computed
    // across BOTH dimensions so neither line is clipped.
    val nowMs = System.currentTimeMillis()
    val windowSpan = 300_000L  // 5 minutes in ms
    val windowStart = nowMs - windowSpan
    NetSectionCard("Activity (5 min)") {
        val maxReads = dbSnapshot.timeSeries.maxOfOrNull { it.readCount } ?: 0
        val maxWrites = dbSnapshot.timeSeries.maxOfOrNull { it.writeCount } ?: 0
        val maxVal = maxOf(maxReads, maxWrites).coerceAtLeast(1)
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (minimized) 30.dp else 50.dp),
        ) {
            val w = size.width
            val h = size.height
            val n = dbSnapshot.timeSeries.size
            if (n >= 1) {
                fun tsToX(ts: Long): Float =
                    ((ts - windowStart).toFloat() / windowSpan.toFloat() * w).coerceIn(0f, w)

                // ── Writes line (gold) with fill ──
                val writesStroke = androidx.compose.ui.graphics.Path()
                val writesFill = androidx.compose.ui.graphics.Path()
                val firstX = tsToX(dbSnapshot.timeSeries.first().timestamp)
                writesFill.moveTo(firstX, h)
                dbSnapshot.timeSeries.forEachIndexed { i, bucket ->
                    val x = tsToX(bucket.timestamp)
                    val y = h - (bucket.writeCount.toFloat() / maxVal) * h
                    if (i == 0) writesStroke.moveTo(x, y) else writesStroke.lineTo(x, y)
                    writesFill.lineTo(x, y)
                }
                val lastX = tsToX(dbSnapshot.timeSeries.last().timestamp)
                writesFill.lineTo(lastX, h)
                writesFill.close()
                drawPath(writesFill, color = DbWritesChartColor.copy(alpha = 0.15f))
                drawPath(writesStroke, color = DbWritesChartColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))

                // ── Reads line (cyan) with fill — drawn on top ──
                val readsStroke = androidx.compose.ui.graphics.Path()
                val readsFill = androidx.compose.ui.graphics.Path()
                readsFill.moveTo(firstX, h)
                dbSnapshot.timeSeries.forEachIndexed { i, bucket ->
                    val x = tsToX(bucket.timestamp)
                    val y = h - (bucket.readCount.toFloat() / maxVal) * h
                    if (i == 0) readsStroke.moveTo(x, y) else readsStroke.lineTo(x, y)
                    readsFill.lineTo(x, y)
                }
                readsFill.lineTo(lastX, h)
                readsFill.close()
                drawPath(readsFill, color = DbReadsChartColor.copy(alpha = 0.10f))
                drawPath(readsStroke, color = DbReadsChartColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Peak: $maxReads reads/s",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = DbReadsChartColor,
            )
            Text(
                text = "Peak: $maxWrites writes/s",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = DbWritesChartColor,
            )
        }
    }

    // ── Top tables (writes) — hidden when minimized ──
    if (!minimized && dbSnapshot.writeTableCounts.isNotEmpty()) {
        NetSectionCard("Top tables (writes)") {
            val topTables = dbSnapshot.writeTableCounts.entries.sortedByDescending { it.value }.take(5)
            val maxCount = topTables.maxOf { it.value }.coerceAtLeast(1)
            topTables.forEach { (table, count) ->
                val barWidth = (count.toFloat() / maxCount).coerceIn(0f, 1f)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable { onViewInDb(table, "", "") },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(table, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = NetTextVariantColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Box(modifier = Modifier.width(60.dp).height(8.dp)) {
                        Box(modifier = Modifier.fillMaxSize().background(DbWritesChartColor.copy(alpha = 0.15f), RoundedCornerShape(2.dp)))
                        Box(modifier = Modifier.fillMaxWidth(barWidth).fillMaxSize().background(DbWritesChartColor, RoundedCornerShape(2.dp)))
                    }
                    Text(count.toString(), fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NetTextColor, modifier = Modifier.padding(start = 6.dp).width(30.dp))
                }
            }
        }
    }

    // ── Top tables (reads) — hidden when minimized ──
    if (!minimized && dbSnapshot.readTableCounts.isNotEmpty()) {
        NetSectionCard("Top tables (reads)") {
            val topTables = dbSnapshot.readTableCounts.entries.sortedByDescending { it.value }.take(5)
            val maxCount = topTables.maxOf { it.value }.coerceAtLeast(1)
            topTables.forEach { (table, count) ->
                val barWidth = (count.toFloat() / maxCount).coerceIn(0f, 1f)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable { onViewInDb(table, "", "") },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(table, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = NetTextVariantColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Box(modifier = Modifier.width(60.dp).height(8.dp)) {
                        Box(modifier = Modifier.fillMaxSize().background(DbReadsChartColor.copy(alpha = 0.15f), RoundedCornerShape(2.dp)))
                        Box(modifier = Modifier.fillMaxWidth(barWidth).fillMaxSize().background(DbReadsChartColor, RoundedCornerShape(2.dp)))
                    }
                    Text(count.toString(), fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NetTextColor, modifier = Modifier.padding(start = 6.dp).width(30.dp))
                }
            }
        }
    }

    // ── Event filter toggle (All / Reads / Writes) — hidden when minimized ──
    if (!minimized) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf("all" to "All", "reads" to "Reads", "writes" to "Writes").forEach { (mode, label) ->
                val isSelected = eventFilter == mode
                Surface(
                    color = if (isSelected) NetBorderColor.copy(alpha = 0.3f) else NetCardColor,
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) NetBorderColor else NetBorderColor.copy(alpha = 0.3f)),
                    modifier = Modifier.weight(1f).clickable { eventFilter = mode },
                ) {
                    Text(
                        text = label,
                        fontFamily = RobotoFamily,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) NetTextColor else NetTextVariantColor,
                        modifier = Modifier.padding(vertical = 4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }

    // ── Recent events (filtered) ──
    val filteredEvents = if (minimized) {
        // In minimized mode, show only writes (reads would flood the mini-window).
        dbSnapshot.recentEvents.filter { !it.isRead }
    } else {
        when (eventFilter) {
            "reads" -> dbSnapshot.recentEvents.filter { it.isRead }
            "writes" -> dbSnapshot.recentEvents.filter { !it.isRead }
            else -> dbSnapshot.recentEvents
        }
    }

    if (!minimized) {
        Text(
            text = "Recent (${filteredEvents.size})",
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = NetLabelColor,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }

    if (filteredEvents.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
            Text(
                text = when {
                    minimized -> "No DB writes"
                    eventFilter == "reads" -> "No reads recorded"
                    eventFilter == "writes" -> "No writes recorded"
                    else -> "No DB activity yet"
                },
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                color = NetTextVariantColor,
            )
        }
    } else {
        filteredEvents.reversed().forEach { event ->
            DbEventRow(event, timeFmt, minimized, onViewInDb)
        }
    }
}

@Composable
private fun DbEventRow(
    event: DebugDbStats.DbEvent,
    timeFmt: SimpleDateFormat,
    minimized: Boolean,
    onViewInDb: (table: String, filterCol: String, filterVal: String) -> Unit,
) {
    val opColor = when {
        event.isRead -> NetCyan
        event.operation.startsWith("INSERT") -> NetGreen
        event.operation.startsWith("UPDATE") -> NetBlue
        event.operation.startsWith("DELETE") -> NetRed
        event.operation.startsWith("REPLACE") -> NetOrange
        else -> NetGray
    }
    val clickable = event.table.isNotEmpty()
    Surface(
        color = Color.Black.copy(alpha = 0.15f),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, opColor.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .then(if (clickable) Modifier.clickable { onViewInDb(event.table, "", "") } else Modifier),
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(timeFmt.format(Date(event.timestamp)), fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = NetTextVariantColor)
                Text(event.operation.take(6), fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = opColor)
                if (event.table.isNotEmpty()) {
                    Text(event.table, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = NetTextColor, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Text("(unknown table)", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = NetTextVariantColor, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // SQL preview line — hidden in minimized mode to save space.
            if (!minimized) {
                Text(event.sql, fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = NetTextVariantColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
