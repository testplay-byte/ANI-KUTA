package com.confused.anikuta.feature.debugbubble.panel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
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
 * 1. **Table list** — vertical list of all tables (name + row count). Tapping
 *    a table navigates to its detail view.
 * 2. **Table detail** — search bar inline with back (left) + refresh (right).
 *    Rows shown as cards with colored separator bars. Search searches ALL text
 *    columns (not just the first). Matching rows are highlighted; arrow buttons
 *    jump between matches. Tap any cell to copy its value.
 */
@Composable
fun DatabaseTab(
    initialTable: String? = null,
    initialSearch: String = "",
    onSelectTable: (String) -> Unit = {},
) {
    val browser = koinInject<DebugDatabaseBrowser>()
    val context = LocalContext.current
    var tables by remember { mutableStateOf<List<TableSummary>>(emptyList()) }
    var selectedTable by remember(initialTable) { mutableStateOf(initialTable) }
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
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(tables) { summary ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedTable = summary.name
                                onSelectTable(summary.name)
                            },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
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
            context = context,
            initialSearch = initialSearch,
            onBack = { selectedTable = null },
        )
    }
}

@Composable
private fun TableDetailView(
    tableName: String,
    browser: DebugDatabaseBrowser,
    context: Context,
    initialSearch: String,
    onBack: () -> Unit,
) {
    var columns by remember { mutableStateOf<List<DebugDatabaseBrowser.ColumnInfo>>(emptyList()) }
    var rows by remember { mutableStateOf<List<List<String>>>(emptyList()) }
    var rowCount by remember { mutableStateOf(0L) }
    var searchQuery by remember(initialSearch) { mutableStateOf(initialSearch) }
    var loading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // Load the table's data.
    LaunchedEffect(tableName, searchQuery, refreshTrigger) {
        loading = true
        rowCount = browser.countRows(tableName)
        val allColumns = browser.getColumns(tableName)
        columns = allColumns
        // If search is non-empty, search ALL text columns + merge results.
        rows = if (searchQuery.isNotBlank()) {
            // Search each text column + collect matching rows (dedup by row index).
            val matchedRowIndices = mutableSetOf<Int>()
            val fullData = browser.queryTable(tableName).second
            fullData.forEachIndexed { rowIdx, row ->
                // Check if any cell in this row contains the query (case-insensitive).
                if (row.any { it.contains(searchQuery, ignoreCase = true) }) {
                    matchedRowIndices.add(rowIdx)
                }
            }
            matchedRowIndices.map { fullData[it] }
        } else {
            browser.queryTable(tableName).second
        }
        loading = false
    }

    // Match navigation: which rows match + current match index.
    val matchIndices = remember(rows, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else rows.indices.filter { idx ->
            rows[idx].any { it.contains(searchQuery, ignoreCase = true) }
        }
    }
    var currentMatch by remember { mutableStateOf(0) }
    val lazyListState = rememberLazyListState()

    // Scroll to the current match.
    LaunchedEffect(currentMatch, matchIndices) {
        if (matchIndices.isNotEmpty()) {
            val target = matchIndices[currentMatch.coerceIn(0, matchIndices.lastIndex)]
            lazyListState.scrollToItem(target)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Header: back (left) + search (center) + refresh (right) ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it; currentMatch = 0 },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = RobotoFamily, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) {
                                Text("Search…", fontFamily = RobotoFamily, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            inner()
                        },
                    )
                    // Match count + arrow navigation.
                    if (matchIndices.isNotEmpty()) {
                        Text(
                            text = "${currentMatch + 1}/${matchIndices.size}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                        IconButton(
                            onClick = { if (currentMatch > 0) currentMatch-- },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, "Prev", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            IconButton(onClick = { refreshTrigger++ }) {
                Icon(Icons.Filled.Refresh, "Refresh", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }

        // ── Row count ──
        Text(
            text = "$rowCount rows${if (rows.size < rowCount) " · showing ${rows.size}" else ""}",
            fontFamily = RobotoFamily,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        // ── Rows (single LazyColumn, colored separators, tap-to-copy) ──
        if (loading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Loading…", fontFamily = RobotoFamily, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(if (searchQuery.isNotBlank()) "No matches" else "No rows", fontFamily = RobotoFamily, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(rows.size) { rowIdx ->
                    val row = rows[rowIdx]
                    val isMatch = rowIdx in matchIndices
                    val isCurrentMatch = rowIdx == matchIndices.getOrElse(currentMatch) { -1 }
                    // Highlight: current match = primary, other matches = primaryContainer.
                    val cardColor = when {
                        isCurrentMatch -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        isMatch -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.surface
                    }
                    val borderColor = when {
                        isCurrentMatch -> MaterialTheme.colorScheme.primary
                        isMatch -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.outlineVariant
                    }
                    Surface(
                        color = cardColor,
                        tonalElevation = if (isMatch) 0.dp else 2.dp,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            if (isCurrentMatch) 2.dp else 1.dp,
                            borderColor,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            row.forEachIndexed { colIdx, cell ->
                                val col = columns.getOrNull(colIdx)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .clickable {
                                            // Tap any cell to copy its value.
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText(col?.name ?: "value", cell))
                                        },
                                ) {
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
                                        color = if (isMatch && searchQuery.isNotBlank() && cell.contains(searchQuery, ignoreCase = true))
                                            MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.size(12.dp),
                                    )
                                }
                                // Colored separator between cells (themed spacer bar).
                                if (colIdx < row.lastIndex) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
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
