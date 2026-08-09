package com.confused.anikuta.feature.debugbubble.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
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

@Composable
fun NetworkTab(
    minimized: Boolean = false,
) {
    val stats = koinInject<DebugNetworkStats>()
    var snapshot by remember { mutableStateOf(DebugNetworkStats.NetworkSnapshot.EMPTY) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // Auto-refresh every 2 seconds (live updating).
    LaunchedEffect(Unit) {
        while (true) {
            snapshot = stats.snapshot()
            kotlinx.coroutines.delay(2000)
        }
    }
    LaunchedEffect(refreshTrigger) { snapshot = stats.snapshot() }

    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Header (refresh + clear) — hidden when minimized ──
        if (!minimized) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = { refreshTrigger++ }) {
                    Icon(Icons.Filled.Refresh, "Refresh", tint = NetTextVariantColor, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { stats.clear(); refreshTrigger++ }) {
                    Icon(Icons.Filled.Delete, "Clear", tint = NetRed, modifier = Modifier.size(18.dp))
                }
            }
        }

        // ── Bandwidth summary (4 stat cards: requests, received, sent, errors) ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            NetStatCard("Requests", snapshot.totalRequests.toString(), NetBlue, Modifier.weight(1f))
            NetStatCard("Received", formatBytes(snapshot.totalBytesReceived), NetGreen, Modifier.weight(1f))
            NetStatCard("Sent", formatBytes(snapshot.totalBytesSent), NetOrange, Modifier.weight(1f))
            NetStatCard("Errors", snapshot.errorCount.toString(), NetRed, Modifier.weight(1f))
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
    }
}

@Composable
private fun NetStatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = NetCardColor,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(value, fontFamily = RobotoFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NetTextColor)
            Text(label, fontFamily = RobotoFamily, fontSize = 9.sp, color = NetTextVariantColor)
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
