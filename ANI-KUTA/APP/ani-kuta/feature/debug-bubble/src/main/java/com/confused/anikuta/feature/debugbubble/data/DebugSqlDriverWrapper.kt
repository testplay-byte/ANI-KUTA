package com.confused.anikuta.feature.debugbubble.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement

/**
 * Wraps a [SqlDriver] to intercept every `execute()` call (writes) and
 * record it in [DebugDbStats].
 *
 * **How it's wired:** [com.confused.anikuta.wrapDebugSqlDriver] (defined
 * in `:app/src/debug/DebugInit.kt`) fetches the [DebugDbStats] singleton
 * from Koin and wraps the real `AndroidSqliteDriver` with this class. The
 * wrapped driver is what Koin registers as `single<SqlDriver>`, so every
 * repository + ViewModel + WorkManager job that writes to the DB flows
 * through here.
 *
 * **Kotlin interface delegation:** `by delegate` auto-forwards all 7
 * [SqlDriver] methods (executeQuery, newTransaction, currentTransaction,
 * addListener, removeListener, notifyListeners, close) to the underlying
 * driver. We only override `execute` — the single write entry point.
 * Reads (`executeQuery`) are NOT intercepted (no overhead on the read
 * path, + the DB Activity view only cares about writes).
 *
 * **SQL parsing:** the operation (INSERT / UPDATE / DELETE / REPLACE) is
 * the first keyword of the SQL string. The table name is parsed with a
 * regex that handles the common SQLite write patterns:
 * - `INSERT INTO <table>`
 * - `INSERT OR REPLACE INTO <table>`
 * - `REPLACE INTO <table>`
 * - `UPDATE <table>`
 * - `DELETE FROM <table>`
 *
 * If parsing fails (unrecognized pattern), the event is still recorded
 * with an empty table name — it counts toward the total write count.
 *
 * **Zero release overhead:** this class is only on the classpath in debug
 * builds. In release builds, the `wrapDebugSqlDriver` stub is an identity
 * function — this class is never instantiated.
 *
 * @param delegate The real [SqlDriver] (e.g. `AndroidSqliteDriver`).
 * @param stats The [DebugDbStats] singleton to record writes into.
 */
class DebugSqlDriverWrapper(
    private val delegate: SqlDriver,
    private val stats: DebugDbStats,
) : SqlDriver by delegate {

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> {
        // Parse the operation + table from the SQL string BEFORE forwarding
        // to the delegate. This is O(1) — one regex match + one substring.
        parseAndRecord(sql)
        return delegate.execute(identifier, sql, parameters, binders)
    }

    /** Write-operation keywords that should be tracked. */
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
    private val tableRegex = Regex(
        """^\s*(?:INSERT\s+(?:OR\s+\w+\s+)?INTO|REPLACE\s+INTO|UPDATE|DELETE\s+FROM)\s+["`\[]?(\w+)["`\]]?""",
        RegexOption.IGNORE_CASE,
    )

    private fun parseAndRecord(sql: String) {
        val trimmed = sql.trim()
        if (trimmed.isEmpty()) return
        // The operation is the first keyword (uppercase).
        val operation = trimmed.substringBefore(' ').uppercase()
        if (operation !in writeOps) return  // not a write — skip
        // Parse the table name.
        val table = tableRegex.find(trimmed)?.groupValues?.getOrNull(1) ?: ""
        stats.recordWrite(operation, table, trimmed)
    }
}
