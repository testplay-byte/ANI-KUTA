package com.confused.anikuta.feature.debugbubble.panel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.feature.debugbubble.data.DebugDatabaseBrowser
import org.koin.compose.koinInject

/**
 * The App Info tab — build/version/memory/module info (Phase DB-6).
 *
 * Shows: build label (debug/release), version name + code, module count, DB
 * table count, memory usage (used/available heap).
 *
 * Build info (version name/code, build type) is passed in via [buildInfo]
 * (provided by a debug/release source-set helper — `:feature:debug-bubble`
 * can't reference `:app`'s BuildConfig directly).
 *
 * Data is fetched on tab open + on Refresh.
 */
@Composable
fun AppInfoTab(
    buildInfo: DebugBuildInfo = koinInject(),
) {
    val browser = koinInject<DebugDatabaseBrowser>()

    var tableCount by remember { mutableStateOf(0) }
    var memoryUsed by remember { mutableLongStateOf(0L) }
    var memoryMax by remember { mutableLongStateOf(0L) }
    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTrigger) {
        tableCount = browser.listTables().size
        val runtime = Runtime.getRuntime()
        memoryUsed = runtime.totalMemory() - runtime.freeMemory()
        memoryMax = runtime.maxMemory()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Build section ──
        SectionLabel("Build")
        InfoRow("Build type", buildInfo.buildType)
        InfoRow("Version name", buildInfo.versionName)
        InfoRow("Version code", buildInfo.versionCode)

        Spacer(Modifier.height(12.dp))

        // ── Project section ──
        SectionLabel("Project")
        InfoRow("Gradle modules", "44")
        InfoRow("DB tables", tableCount.toString())
        InfoRow("DB name", "anikuta.db")

        Spacer(Modifier.height(12.dp))

        // ── Memory section ──
        SectionLabel("Memory")
        InfoRow("Used heap", formatBytes(memoryUsed))
        InfoRow("Max heap", formatBytes(memoryMax))
        val usedPct = if (memoryMax > 0) (memoryUsed * 100 / memoryMax).toInt() else 0
        InfoRow("Heap usage", "$usedPct%")

        Spacer(Modifier.height(12.dp))

        // ── Refresh button ──
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clickable { refreshTrigger++ },
        ) {
            Text(
                text = "↻ Refresh",
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = RobotoFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.1f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}
