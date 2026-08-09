package com.confused.anikuta.feature.debugbubble.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.db.SqlCursor

/**
 * Wraps a [SqlDriver] to intercept every `execute()` call (writes) AND every
 * `executeQuery()` call (reads), recording them in [DebugDbStats].
 *
 * **How it's wired:** [com.confused.anikuta.wrapDebugSqlDriver] (defined
 * in `:app/src/debug/DebugInit.kt`) fetches the [DebugDbStats] singleton
 * from Koin and wraps the real `AndroidSqliteDriver` with this class. The
 * wrapped driver is what Koin registers as `single<SqlDriver>`, so every
 * repository + ViewModel + WorkManager job that touches the DB flows
 * through here.
 *
 * **Kotlin interface delegation:** `by delegate` auto-forwards all 7
 * [SqlDriver] methods to the underlying driver. We override two:
 * - `execute` — the single write entry point (INSERT / UPDATE / DELETE / REPLACE).
 * - `executeQuery` — the single read entry point (SELECT).
 *
 * **SQL parsing:** the operation (first keyword of the SQL string) determines
 * whether it's a read or write. Table names are parsed with regexes:
 * - Write regex: `INSERT INTO`, `INSERT OR REPLACE INTO`, `REPLACE INTO`,
 *   `UPDATE`, `DELETE FROM`.
 * - Read regex: `SELECT ... FROM <table>`.
 *
 * If parsing fails (unrecognized pattern), the event is still recorded with
 * an empty table name — it counts toward the totals.
 *
 * **Performance note:** reads (`executeQuery`) happen far more frequently than
 * writes (every Flow emission, every screen load). The parsing is O(1) — one
 * regex match per call. The [DebugDbStats] ring buffer is capped at 200 events
 * to avoid unbounded memory growth. If read tracking causes performance issues
 * in the future, the `executeQuery` override can be disabled independently.
 *
 * **Zero release overhead:** this class is only on the classpath in debug
 * builds. In release builds, the `wrapDebugSqlDriver` stub is an identity
 * function — this class is never instantiated.
 *
 * @param delegate The real [SqlDriver] (e.g. `AndroidSqliteDriver`).
 * @param stats The [DebugDbStats] singleton to record events into.
 */
class DebugSqlDriverWrapper(
    private val delegate: SqlDriver,
    private val stats: DebugDbStats,
) : SqlDriver by delegate {

    /** Write-operation keywords that should be tracked as writes. */
    private val writeOps = setOf("INSERT", "UPDATE", "DELETE", "REPLACE")

    /**
     * Regex that extracts the table name from the common SQLite write
     * patterns. Handles: INSERT INTO, INSERT OR REPLACE INTO, REPLACE INTO,
     * UPDATE, DELETE FROM.
     *
     * The table name is captured in group 1. Case-insensitive, whitespace-
     * tolerant. Only matches the first table reference (sufficient for
     * tracking — we don't need JOIN targets).
     */
    private val writeTableRegex = Regex(
        """^\s*(?:INSERT\s+(?:OR\s+\w+\s+)?INTO|REPLACE\s+INTO|UPDATE|DELETE\s+FROM)\s+["`\[]?(\w+)["`\]]?""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Regex that extracts the table name from a SELECT statement.
     * Matches: `SELECT ... FROM <table>` (including `SELECT DISTINCT ... FROM`).
     *
     * The table name is captured in group 1. Case-insensitive. Handles
     * optional quoting chars (backtick, double-quote, square bracket).
     * Does NOT match subqueries or JOINs (only the first FROM clause).
     */
    private val selectTableRegex = Regex(
        """^\s*(?:SELECT\s+(?:DISTINCT\s+)?[\s\S]*?\s+FROM)\s+["`\[]?(\w+)["`\]]?""",
        RegexOption.IGNORE_CASE,
    )

    // ── Write interception ──

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> {
        // Parse the operation + table from the SQL string BEFORE forwarding
        // to the delegate. This is O(1) — one regex match + one substring.
        parseAndRecordWrite(sql)
        return delegate.execute(identifier, sql, parameters, binders)
    }

    private fun parseAndRecordWrite(sql: String) {
        val trimmed = sql.trim()
        if (trimmed.isEmpty()) return
        // The operation is the first keyword (uppercase).
        val operation = trimmed.substringBefore(' ').uppercase()
        if (operation !in writeOps) return  // not a write — skip
        // Parse the table name.
        val table = writeTableRegex.find(trimmed)?.groupValues?.getOrNull(1) ?: ""
        stats.recordWrite(operation, table, trimmed)
    }

    // ── Read interception ──

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> {
        // Parse the table name from the SELECT statement BEFORE forwarding.
        parseAndRecordRead(sql)
        return delegate.executeQuery(identifier, sql, mapper, parameters, binders)
    }

    private fun parseAndRecordRead(sql: String) {
        val trimmed = sql.trim()
        if (trimmed.isEmpty()) return
        // Track SELECT (and WITH ... SELECT for CTEs) statements.
        val operation = trimmed.substringBefore(' ').uppercase()
        if (operation != "SELECT" && operation != "WITH") return
        // Parse the table name from the FROM clause.
        val table = selectTableRegex.find(trimmed)?.groupValues?.getOrNull(1) ?: ""
        stats.recordRead(table, trimmed)
    }
}
