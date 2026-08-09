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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.feature.debugbubble.data.DebugNetworkStats
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Network tab — OkHttp interceptor stats (Phase DB-5).
 *
 * Shows: summary (total requests, bytes, errors), status-code histogram
 * (2xx/3xx/4xx/5xx/errors), + a scrollable list of the last 50 requests.
 *
 * Data is fetched on tab open + on Refresh (snapshot from [DebugNetworkStats]).
 *
 * **Extension traffic caveat (D-162 I1):** extensions use a separate Injekt
 * OkHttpClient — their HTTP calls are NOT captured. The tab shows app-level
 * traffic only (disclosed in a banner).
 */
@Composable
fun NetworkTab() {
    val stats = koinInject<DebugNetworkStats>()

    var snapshot by remember { mutableStateOf(DebugNetworkStats.NetworkSnapshot.EMPTY) }
    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTrigger) {
        snapshot = stats.snapshot()
    }

    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Header row: refresh + clear ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { refreshTrigger++ }) {
                Icon(Icons.Filled.Refresh, "Refresh", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = {
                stats.clear()
                refreshTrigger++
            }) {
                Icon(Icons.Filled.Delete, "Clear", tint = MaterialTheme.colorScheme.error)
            }
        }

        // ── Summary stats ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatCard("Requests", snapshot.totalRequests.toString(), Modifier.weight(1f))
            StatCard("Bytes", formatBytes(snapshot.totalBytes), Modifier.weight(1f))
            StatCard("Errors", snapshot.errorCount.toString(), Modifier.weight(1f))
        }

        // ── Status-code histogram ──
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Status codes",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                listOf(
                    "2xx" to snapshot.statusBuckets[0] to Color(0xFF4CAF50),
                    "3xx" to snapshot.statusBuckets[1] to Color(0xFF2196F3),
                    "4xx" to snapshot.statusBuckets[2] to Color(0xFFFF9800),
                    "5xx" to snapshot.statusBuckets[3] to Color(0xFFF44336),
                    "errors" to snapshot.statusBuckets[4] to Color(0xFF9E9E9E),
                ).forEach { (pair, color) ->
                    val (label, count) = pair
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = color,
                            modifier = Modifier.width(50.dp),
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(12.dp)
                                .background(color.copy(alpha = 0.25f), RoundedCornerShape(2.dp)),
                        )
                        Text(
                            text = count.toString(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }

        // ── Category breakdown (metadata / video / image / other) ──
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Request categories",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                DebugNetworkStats.RequestCategory.values().forEachIndexed { idx, cat ->
                    val count = snapshot.categoryCounts.getOrElse(idx) { 0 }
                    val color = when (cat) {
                        DebugNetworkStats.RequestCategory.METADATA -> Color(0xFF9C27B0)
                        DebugNetworkStats.RequestCategory.VIDEO -> Color(0xFF4CAF50)
                        DebugNetworkStats.RequestCategory.IMAGE -> Color(0xFF2196F3)
                        DebugNetworkStats.RequestCategory.OTHER -> Color(0xFF9E9E9E)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = cat.label,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = color,
                            modifier = Modifier.width(70.dp),
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(12.dp)
                                .background(color.copy(alpha = 0.25f), RoundedCornerShape(2.dp)),
                        )
                        Text(
                            text = count.toString(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }

        // ── Extension-traffic caveat ──
        Surface(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Text(
                text = "Note: extension HTTP calls (via Injekt) are not captured.",
                fontFamily = RobotoFamily,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        Spacer(Modifier.height(8.dp))

        // ── Recent requests ──
        Text(
            text = "Recent requests (${snapshot.recentRequests.size})",
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        if (snapshot.recentRequests.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No requests yet", fontFamily = RobotoFamily, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(snapshot.recentRequests.reversed()) { req ->  // newest first
                    RequestRow(req, timeFmt)
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = value,
                fontFamily = RobotoFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RequestRow(req: DebugNetworkStats.RequestRecord, timeFmt: SimpleDateFormat) {
    val statusColor = when {
        req.status < 0 -> Color(0xFF9E9E9E)  // network error
        req.status < 300 -> Color(0xFF4CAF50)  // 2xx
        req.status < 400 -> Color(0xFF2196F3)  // 3xx
        req.status < 500 -> Color(0xFFFF9800)  // 4xx
        else -> Color(0xFFF44336)  // 5xx
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = timeFmt.format(Date(req.timestamp)),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = req.method,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = req.category.label.take(3).uppercase(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (req.category) {
                        DebugNetworkStats.RequestCategory.METADATA -> Color(0xFF9C27B0)
                        DebugNetworkStats.RequestCategory.VIDEO -> Color(0xFF4CAF50)
                        DebugNetworkStats.RequestCategory.IMAGE -> Color(0xFF2196F3)
                        DebugNetworkStats.RequestCategory.OTHER -> Color(0xFF9E9E9E)
                    },
                )
                Text(
                    text = if (req.status < 0) "ERR" else req.status.toString(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                )
                Text(
                    text = "${req.latencyMs}ms",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (req.bytes > 0) {
                    Text(
                        text = formatBytes(req.bytes),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "${req.host}${req.path}",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (req.error != null) {
                Text(
                    text = req.error,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.1f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}
