package com.confused.anikuta.core.testcontroller

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.confused.anikuta.core.testapi.TableSummary
import com.confused.anikuta.feature.debugbubble.data.DebugDatabaseBrowser

/**
 * Provides DB inspection for the test-controller (D-201 reuse of [DebugDatabaseBrowser] +
 * a raw-SQL path for arbitrary SELECTs).
 *
 *  - [listTables]: uses [DebugDatabaseBrowser.listTables] + [DebugDatabaseBrowser.countRows]
 *    (validated against `sqlite_master` — no injection).
 *  - [queryTable]: own read-only connection, `SELECT * FROM <table> LIMIT ? OFFSET ?`.
 *    Table name validated against [DebugDatabaseBrowser.listTables].
 *  - [querySql]: own read-only connection, raw SELECT. SQL is validated to start with `SELECT`
 *    (case-insensitive) + reject `INSERT/UPDATE/DELETE/DROP/ALTER/ATTACH/DETACH/PRAGMA` keywords.
 *    Read-only at the SQLite level (`OPEN_READONLY`).
 *
 * Opens a SEPARATE read-only connection (mirrors [DebugDatabaseBrowser.withReadDb] pattern) —
 * zero write access possible, even if the agent crafts a malicious SQL.
 */
class DatabaseProvider(
    private val context: Context,
    private val browser: DebugDatabaseBrowser,
    private val dbName: String = "anikuta.db",
) {
    fun listTables(): List<TableSummary> {
        return browser.listTables().map { TableSummary(name = it, rowCount = browser.countRows(it)) }
    }

    fun count(table: String): Long = browser.countRows(table)

    /** Query a table with limit + offset. Returns columns + rows (each row = Map<col, value>). */
    fun queryTable(table: String, limit: Int, offset: Int): QueryResult {
        if (!browser.listTables().contains(table)) {
            return QueryResult(table, emptyList(), emptyList(), truncated = false, error = "unknown table: $table")
        }
        return withReadDb { db ->
            val columns = db.queryTableColumns(table)
            val rows = mutableListOf<Map<String, String>>()
            db.rawQuery(
                "SELECT * FROM $table LIMIT ? OFFSET ?",
                arrayOf(limit.toString(), offset.toString()),
            ).use { c ->
                while (c.moveToNext()) {
                    val row = LinkedHashMap<String, String>(c.columnCount)
                    for (i in 0 until c.columnCount) {
                        val name = c.getColumnName(i)
                        row[name] = if (c.isNull(i)) "NULL" else c.getString(i) ?: ""
                    }
                    rows.add(row)
                }
            }
            QueryResult(table, columns, rows, truncated = rows.size >= limit, error = null)
        }
    }

    /** Run an arbitrary SELECT (validated). Returns columns + rows. */
    fun querySql(sql: String, limit: Int): QueryResult {
        val trimmed = sql.trim()
        val upper = trimmed.uppercase()
        if (!upper.startsWith("SELECT") && !upper.startsWith("WITH")) {
            return QueryResult("__sql__", emptyList(), emptyList(), truncated = false, error = "only SELECT/WITH allowed")
        }
        // Reject dangerous keywords anywhere in the SQL (defence in depth).
        val forbidden = listOf("INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "ATTACH", "DETACH", "PRAGMA", "CREATE", "REPLACE")
        // Crude token check — sufficient since OPEN_READONLY blocks writes anyway, this is belt-and-suspenders.
        for (kw in forbidden) {
            if (upper.matches(Regex("(?i).*\\b$kw\\b.*"))) {
                return QueryResult("__sql__", emptyList(), emptyList(), truncated = false, error = "forbidden keyword: $kw")
            }
        }
        // D-198 v5.3: always enforce our own LIMIT — strip any user-provided LIMIT first,
        // then append our safety limit. Prevents unbounded result sets (OOM risk).
        // v5.4: also strip trailing semicolons (breaks the regex → invalid SQL).
        val cleaned = trimmed.trimEnd(';').trim()
        val withoutUserLimit = cleaned.replace(Regex("(?i)\\s+LIMIT\\s+\\d+(?:\\s*,\\s*\\d+)?\\s*$", RegexOption.IGNORE_CASE), "")
        val enforced = "$withoutUserLimit LIMIT $limit"
        return withReadDb { db ->
            val rows = mutableListOf<Map<String, String>>()
            var columns = emptyList<String>()
            var rowCount = 0
            db.rawQuery(enforced, null).use { c ->
                columns = (0 until c.columnCount).map { c.getColumnName(it) }
                while (c.moveToNext() && rowCount < limit) {
                    val row = LinkedHashMap<String, String>(c.columnCount)
                    for (i in 0 until c.columnCount) {
                        row[c.getColumnName(i)] = if (c.isNull(i)) "NULL" else c.getString(i) ?: ""
                    }
                    rows.add(row)
                    rowCount++
                }
            }
            QueryResult("__sql__", columns, rows, truncated = rows.size >= limit, error = null)
        }
    }

    data class QueryResult(
        val table: String,
        val columns: List<String>,
        val rows: List<Map<String, String>>,
        val truncated: Boolean,
        val error: String?,
    )

    private fun SQLiteDatabase.queryTableColumns(table: String): List<String> {
        val cols = mutableListOf<String>()
        rawQuery("PRAGMA table_info($table)", null).use { c ->
            while (c.moveToNext()) cols.add(c.getString(1)) // 'name' column
        }
        return cols
    }

    private inline fun <T> withReadDb(block: (SQLiteDatabase) -> T): T {
        val path = context.getDatabasePath(dbName)
        val db = SQLiteDatabase.openDatabase(path.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return try { block(db) } finally { db.close() }
    }
}
