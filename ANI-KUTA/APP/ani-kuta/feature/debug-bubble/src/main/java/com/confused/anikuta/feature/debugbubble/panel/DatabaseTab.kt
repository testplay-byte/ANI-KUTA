package com.confused.anikuta.feature.debugbubble.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.feature.debugbubble.data.DebugDatabaseBrowser
import org.koin.compose.koinInject

/**
 * The Database tab — a read-only browser for all SQLDelight tables (Phase DB-3).
 *
 * Shows a horizontally-scrollable chip list of tables. Selecting a table shows
 * its columns + first 100 rows in a scrollable grid. A search field filters
 * rows by a LIKE query on the first text column (bound parameter — no injection).
 * BLOB columns render as `<BLOB: N bytes>`.
 *
 * Data is fetched when the tab is opened + on Refresh (no polling). Read-only —
 * a banner notes mutations are a future phase.
 *
 * CORE_RULES §20: logged via DebugDatabaseBrowser.
 */
@Composable
fun DatabaseTab() {
    val browser = koinInject<DebugDatabaseBrowser>()

    var tables by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedTable by remember { mutableStateOf<String?>(null) }
    var columns by remember { mutableStateOf<List<DebugDatabaseBrowser.ColumnInfo>>(emptyList()) }
    var rows by remember { mutableStateOf<List<List<String>>>(emptyList()) }
    var rowCount by remember { mutableStateOf(0L) }
    var searchQuery by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // Load the table list once + on refresh.
    LaunchedEffect(refreshTrigger) {
        loading = true
        tables = browser.listTables()
        if (selectedTable == null && tables.isNotEmpty()) {
            selectedTable = tables.first()
        }
        loading = false
    }

    // Load the selected table's data (or search results) when it changes or
    // when the search query / refresh changes.
    LaunchedEffect(selectedTable, searchQuery, refreshTrigger) {
        val table = selectedTable ?: return@LaunchedEffect
        loading = true
        rowCount = browser.countRows(table)
        val (cols, data) = if (searchQuery.isNotBlank()) {
            val firstTextCol = browser.getColumns(table).firstOrNull { !it.isBlob }?.name
            if (firstTextCol != null) {
                browser.search(table, firstTextCol, searchQuery)
            } else {
                browser.queryTable(table)
            }
        } else {
            browser.queryTable(table)
        }
        columns = cols
        rows = data
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Table chips ──
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
        ) {
            items(tables) { table ->
                val isSelected = table == selectedTable
                val bg = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                val fg = if (isSelected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
                Surface(
                    color = bg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { selectedTable = table },
                ) {
                    Text(
                        text = table,
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = fg,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }

        // ── Search bar + refresh ──
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
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = RobotoFamily,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search ${selectedTable ?: "table"}…",
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
            IconButton(onClick = { refreshTrigger++ }) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── Row count + read-only banner ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$rowCount rows${if (rows.size < rowCount) " · showing first ${rows.size}" else ""}",
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(4.dp),
            ) {
                Text(
                    text = "read-only",
                    fontFamily = RobotoFamily,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Table grid (scrollable) ──
        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading…", fontFamily = RobotoFamily, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (columns.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No table selected", fontFamily = RobotoFamily, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            // Scrollable grid: header row + data rows.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                ) {
                    columns.forEach { col ->
                        Text(
                            text = "${col.name}\n${col.type}",
                            fontFamily = RobotoFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .width(120.dp)
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                }
                // Data rows
                if (rows.isEmpty()) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "No matches" else "No rows",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    rows.forEach { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                        ) {
                            row.forEach { cell ->
                                Text(
                                    text = cell,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .width(120.dp)
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
