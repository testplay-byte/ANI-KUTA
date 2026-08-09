package com.confused.anikuta.feature.debugbubble.panel

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.feature.debugbubble.data.DebugDatabaseBrowser
import org.koin.compose.koinInject

/**
 * The Database tab — a read-only browser (Phase DB-3, revised).
 *
 * Two-screen flow:
 * 1. **Table list** — a vertical list of all tables (name + row count). Tapping
 *    a table navigates to its detail view.
 * 2. **Table detail** — shows the table's columns + first 100 rows in a single
 *    scrollable column (no dual-scroll — header + rows scroll together). Search
 *    filters rows. BLOB columns render as `<BLOB: N bytes>`.
 *
 * @param onSelectTable Optional callback when a table is selected (for the
 *        Current Screen tab's "View in DB" buttons — not used yet).
 */
@Composable
fun DatabaseTab(
    onSelectTable: (String) -> Unit = {},
) {
    val browser = koinInject<DebugDatabaseBrowser>()
    var tables by remember { mutableStateOf<List<TableSummary>>(emptyList()) }
    var selectedTable by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    // Load the table list (with row counts).
    LaunchedEffect(Unit) {
        loading = true
        tables = browser.listTables().map { name ->
            TableSummary(name, browser.countRows(name))
        }
        loading = false
    }

    val table = selectedTable
    if (table == null) {
        // ── Table list ──
        if (loading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Loading tables…", fontFamily = RobotoFamily, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(tables) { summary ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedTable = summary.name
                                onSelectTable(summary.name)
                            },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = summary.name,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 10.dp).weight(1f),
                            )
                            Text(
                                text = "${summary.rowCount} rows",
                                fontFamily = RobotoFamily,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    } else {
        // ── Table detail view ──
        TableDetailView(
            tableName = table,
            browser = browser,
            onBack = { selectedTable = null },
        )
    }
}

@Composable
private fun TableDetailView(
    tableName: String,
    browser: DebugDatabaseBrowser,
    onBack: () -> Unit,
) {
    var columns by remember { mutableStateOf<List<DebugDatabaseBrowser.ColumnInfo>>(emptyList()) }
    var rows by remember { mutableStateOf<List<List<String>>>(emptyList()) }
    var rowCount by remember { mutableStateOf(0L) }
    var searchQuery by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(tableName, searchQuery, refreshTrigger) {
        loading = true
        rowCount = browser.countRows(tableName)
        val (cols, data) = if (searchQuery.isNotBlank()) {
            val firstTextCol = browser.getColumns(tableName).firstOrNull { !it.isBlob }?.name
            if (firstTextCol != null) browser.search(tableName, firstTextCol, searchQuery)
            else browser.queryTable(tableName)
        } else {
            browser.queryTable(tableName)
        }
        columns = cols
        rows = data
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Header: back + table name + refresh ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                text = tableName,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { refreshTrigger++ }) {
                Icon(Icons.Filled.Refresh, "Refresh", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ── Row count ──
        Text(
            text = "$rowCount rows${if (rows.size < rowCount) " · showing ${rows.size}" else ""}",
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        // ── Search ──
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = RobotoFamily, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (searchQuery.isEmpty()) {
                            Text("Search $tableName…", fontFamily = RobotoFamily, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        inner()
                    },
                )
            }
        }

        // ── Single-scroll column: header + rows together ──
        if (loading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Loading…", fontFamily = RobotoFamily, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(if (searchQuery.isNotBlank()) "No matches" else "No rows", fontFamily = RobotoFamily, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            // Each row is a card showing column→value pairs (no horizontal scroll needed).
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(rows) { row ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            row.forEachIndexed { idx, cell ->
                                val col = columns.getOrNull(idx)
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                                    Text(
                                        text = col?.name ?: "",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(100.dp),
                                    )
                                    Text(
                                        text = cell,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
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

private data class TableSummary(val name: String, val rowCount: Long)
