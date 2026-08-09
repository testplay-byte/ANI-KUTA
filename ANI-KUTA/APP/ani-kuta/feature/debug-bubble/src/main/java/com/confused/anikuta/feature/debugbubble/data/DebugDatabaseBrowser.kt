package com.confused.anikuta.feature.debugbubble.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase

/**
 * Read-only database browser for the debug bubble (Phase DB-3).
 *
 * Opens a SEPARATE read-only [SQLiteDatabase] connection to the app's database
 * (anikuta.db) via [Context.getDatabasePath] + [SQLiteDatabase.openDatabase]
 * with [SQLiteDatabase.OPEN_READONLY]. This bypasses SQLDelight entirely —
 * avoids version-specific SqlDriver API quirks + guarantees zero write access
 * (the connection is read-only at the SQLite level).
 *
 * All table/column names are validated against `sqlite_master` / `PRAGMA
 * table_info` before interpolation (column names can't be parameterized in
 * SQLite) to prevent SQL injection (D-162 I3). Search queries use bound `?`
 * parameters for the user-supplied value.
 *
 * BLOB columns are detected via `PRAGMA table_info` and rendered as
 * `<BLOB: N bytes>` instead of being decoded as strings (D-162 I4 —
 * `cursor.getString()` on a BLOB returns garbage).
 *
 * CORE_RULES §20: logged with tag "Anikuta:Feature:DebugBubble:DB".
 *
 * @param context The app context (for locating the DB file).
 * @param dbName The database file name (must match DatabaseDriverFactory —
 *               "anikuta.db").
 */
class DebugDatabaseBrowser(
    private val context: Context,
    private val dbName: String = "anikuta.db",
) {
    companion object {
        private const val ROW_LIMIT = 100
    }

    /** List all user tables (excludes sqlite_* + android_* internal tables). */
    fun listTables(): List<String> {
        return withReadDb { db ->
            val result = mutableListOf<String>()
            db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%' ORDER BY name", null).use { c ->
                while (c.moveToNext()) {
                    c.getString(0)?.let { result.add(it) }
                }
            }
            result
        }
    }

    /**
     * Column metadata for a table.
     *
     * @property name The column name.
     * @property type The SQLite declared type (TEXT, INTEGER, REAL, BLOB, etc.).
     * @property isBlob True if the type is BLOB (or untyped — SQLite defaults to
     *           BLOB affinity for untyped columns).
     */
    data class ColumnInfo(val name: String, val type: String, val isBlob: Boolean)

    /** Get column metadata for a table via PRAGMA table_info. */
    fun getColumns(table: String): List<ColumnInfo> {
        // Validate the table name (defend against injection — table names can't
        // be parameterized in PRAGMA).
        if (!isValidTable(table)) return emptyList()
        return withReadDb { db ->
            val result = mutableListOf<ColumnInfo>()
            db.rawQuery("PRAGMA table_info($table)", null).use { c ->
                // Columns: cid, name, type, notnull, dflt_value, pk
                val nameIdx = c.getColumnIndex("name")
                val typeIdx = c.getColumnIndex("type")
                while (c.moveToNext()) {
                    val name = c.getString(nameIdx) ?: continue
                    val type = (c.getString(typeIdx) ?: "").uppercase()
                    val isBlob = type == "BLOB" || type.isEmpty()
                    result.add(ColumnInfo(name, type.ifEmpty { "BLOB" }, isBlob))
                }
            }
            result
        }
    }

    /**
     * Query a table (first [ROW_LIMIT] rows).
     *
     * @return the column metadata + the rows (each cell as a display string).
     */
    fun queryTable(table: String): Pair<List<ColumnInfo>, List<List<String>>> {
        val columns = getColumns(table)
        if (columns.isEmpty()) return emptyList<ColumnInfo>() to emptyList()
        val colList = columns.joinToString(", ") { it.name }
        return withReadDb { db ->
            val rows = mutableListOf<List<String>>()
            db.rawQuery("SELECT $colList FROM $table LIMIT $ROW_LIMIT", null).use { c ->
                while (c.moveToNext()) {
                    val row = columns.indices.map { colIdx -> renderCell(c, colIdx, columns[colIdx]) }
                    rows.add(row)
                }
            }
            columns to rows
        }
    }

    /**
     * Query ALL rows from a table (no LIMIT). Used by [exportAsJson] to export
     * the complete database. Per user: "download the whole completed database —
     * not leaving anything behind, even if it is very big."
     */
    fun queryAllRows(table: String): Pair<List<ColumnInfo>, List<List<String>>> {
        val columns = getColumns(table)
        if (columns.isEmpty()) return emptyList<ColumnInfo>() to emptyList()
        val colList = columns.joinToString(", ") { it.name }
        return withReadDb { db ->
            val rows = mutableListOf<List<String>>()
            db.rawQuery("SELECT $colList FROM $table", null).use { c ->
                while (c.moveToNext()) {
                    val row = columns.indices.map { colIdx -> renderCell(c, colIdx, columns[colIdx]) }
                    rows.add(row)
                }
            }
            columns to rows
        }
    }

    /**
     * Search a table for rows where [column] LIKE '%[query]%'. Bound parameter
     * (no injection — D-162 I3). Returns the column metadata + matching rows.
     */
    fun search(table: String, column: String, query: String): Pair<List<ColumnInfo>, List<List<String>>> {
        val columns = getColumns(table)
        if (columns.isEmpty()) return emptyList<ColumnInfo>() to emptyList()
        // Validate the column name (can't be parameterized).
        if (columns.none { it.name == column }) return emptyList<ColumnInfo>() to emptyList()
        val colList = columns.joinToString(", ") { it.name }
        return withReadDb { db ->
            val rows = mutableListOf<List<String>>()
            // Bound parameter for the LIKE value — no string interpolation.
            db.rawQuery("SELECT $colList FROM $table WHERE $column LIKE ? LIMIT $ROW_LIMIT", arrayOf("%$query%")).use { c ->
                while (c.moveToNext()) {
                    val row = columns.indices.map { colIdx -> renderCell(c, colIdx, columns[colIdx]) }
                    rows.add(row)
                }
            }
            columns to rows
        }
    }

    /** Count rows in a table. */
    fun countRows(table: String): Long {
        if (!isValidTable(table)) return 0L
        return withReadDb { db ->
            var count = 0L
            db.rawQuery("SELECT COUNT(*) FROM $table", null).use { c ->
                if (c.moveToFirst()) count = c.getLong(0)
            }
            count
        }
    }

    // ── Helpers ──

    /** Validate a table name against sqlite_master (no injection via PRAGMA). */
    private fun isValidTable(table: String): Boolean = table in listTables()

    /**
     * Render a cell as a display string. BLOB columns show `<BLOB: N bytes>`.
     * Long text (> 4KB) shows `<long text: N chars>` (D-162 I4).
     */
    private fun renderCell(cursor: android.database.Cursor, index: Int, column: ColumnInfo): String {
        return if (column.isBlob) {
            val bytes = cursor.getBlob(index)
            if (cursor.isNull(index)) "NULL" else "<BLOB: ${bytes.size} bytes>"
        } else {
            when {
                cursor.isNull(index) -> "NULL"
                else -> {
                    val str = cursor.getString(index)
                    if (str.length > 4096) "<long text: ${str.length} chars>" else str
                }
            }
        }
    }

    /**
     * Open a read-only connection, run [block], close the connection.
     * The connection is separate from the app's main DB connection (read-only
     * at the SQLite level — no write access possible).
     */
    private inline fun <T> withReadDb(block: (SQLiteDatabase) -> T): T {
        val dbPath = context.getDatabasePath(dbName)
        val db = SQLiteDatabase.openDatabase(dbPath.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            block(db)
        } finally {
            db.close()
        }
    }

    /**
     * Export the entire database as a JSON string. Each table is a key in the
     * top-level object; the value is an array of row objects (column → value).
     *
     * BLOB columns are base64-encoded. NULL values are JSON null.
     *
     * Used by the Database tab's export button (per user: "download the whole
     * database in a proper well-organized format — most probably a .json").
     */
    fun exportAsJson(): String {
        val tables = listTables()
        val sb = StringBuilder()
        sb.append("{")
        tables.forEachIndexed { tableIdx, tableName ->
            sb.append("\"").append(tableName).append("\":[")
            val columns = getColumns(tableName)
            val (_, rows) = queryAllRows(tableName)
            rows.forEachIndexed { rowIdx, row ->
                sb.append("{")
                row.forEachIndexed { colIdx, cell ->
                    sb.append("\"").append(columns.getOrNull(colIdx)?.name ?: "col$colIdx").append("\":")
                    if (cell == "NULL") {
                        sb.append("null")
                    } else if (cell.startsWith("<BLOB:") || cell.startsWith("<long text:")) {
                        sb.append("\"").append(cell).append("\"")
                    } else {
                        // Escape quotes + backslashes in the value.
                        val escaped = cell.replace("\\", "\\\\").replace("\"", "\\\"")
                            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
                        sb.append("\"").append(escaped).append("\"")
                    }
                    if (colIdx < row.lastIndex) sb.append(",")
                }
                sb.append("}")
                if (rowIdx < rows.lastIndex) sb.append(",")
            }
            sb.append("]")
            if (tableIdx < tables.lastIndex) sb.append(",")
        }
        sb.append("}")
        return sb.toString()
    }
}
