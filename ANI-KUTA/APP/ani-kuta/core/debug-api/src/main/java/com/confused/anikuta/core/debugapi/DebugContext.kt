package com.confused.anikuta.core.debugapi

/**
 * Data model for screen-specific debug context (Phase DB — debug bubble).
 *
 * Screens that want to expose debug info to the bubble call
 * [LocalDebugContextUpdater] with a [DebugContext] instance. The bubble reads
 * it via [LocalDebugContext] and shows it in the "Current Screen" tab.
 *
 * This type lives in `:core:debug-api` (always on the classpath) so feature
 * modules can reference it without depending on the debug-only
 * `:feature:debug-bubble` module. When the bubble module isn't present (release
 * builds, or debug builds with the bubble disabled), the CompositionLocals
 * default to `null` / a no-op updater — the screen's `DebugContext` is simply
 * never read.
 *
 * @property screenName Human-readable screen label (e.g. "Details — Frieren").
 * @property screenData Key-value pairs the screen wants to expose (mainId,
 *           sourceId, resolverState, etc.).
 * @property relevantTables DB rows relevant to this screen — a "View in DB"
 *           button jumps to the Database tab pre-filtered.
 * @property actions Screen-specific debug callbacks (e.g. "Force re-resolve").
 *           The lambdas capture the screen's VM — the screen MUST clear the
 *           context via the updater on dispose to avoid leaking the VM.
 */
data class DebugContext(
    val screenName: String,
    val screenData: Map<String, String> = emptyMap(),
    val relevantTables: List<DbReference> = emptyList(),
    val actions: List<DebugAction> = emptyList(),
)

/**
 * A reference to a DB row relevant to the current screen.
 *
 * @property table The SQLDelight table name (e.g. "content").
 * @property filterColumn The column to filter on (e.g. "main_id").
 * @property filterValue The value to match (e.g. "uuid-...").
 * @property label The button label (e.g. "View content row").
 */
data class DbReference(
    val table: String,
    val filterColumn: String,
    val filterValue: String,
    val label: String,
)

/**
 * A screen-specific debug action.
 *
 * @property label The button label (e.g. "Force re-resolve").
 * @property action The callback. Captures the screen's VM — the screen must
 *           clear the context on dispose (see [DebugContext] doc).
 */
data class DebugAction(
    val label: String,
    val action: () -> Unit,
)
