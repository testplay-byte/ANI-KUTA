/**
 * Debug Bubble — plan data for the dashboard page.
 *
 * Source of truth: APP/ani-kuta/DOCUMENTATION/planning/debug-bubble/PLAN.md
 * This file is a structured mirror of that doc for the dashboard to render.
 * Keep in sync with PLAN.md.
 */

export const DEBUG_BUBBLE_HERO = {
  status: "PLANNING",
  statusColor: "var(--c-warning, #f59e0b)",
  title: "Debug Bubble",
  subtitle:
    "A floating, draggable debug overlay on every screen. Tap to expand a panel with database, console-log, network, and screen-specific debug tools. Debug-only, trivially removable, zero impact on app code when off.",
  meta: "Branch feature/debug-bubble · Planning phase · Sub-agent reviewed (D-162)",
};

// ── Goals ─────────────────────────────────────────────────────────────────────

export const GOALS: { title: string; desc: string }[] = [
  {
    title: "App-wide overlay",
    desc: "A small floating bubble renders on top of every screen — Browse, Library, Search, Details, Watch, Downloads, Settings, etc.",
  },
  {
    title: "Draggable",
    desc: "Drag the bubble anywhere on screen (X + Y). Position persists across sessions via SharedPreferences.",
  },
  {
    title: "Expandable panel",
    desc: "Tap the bubble → a panel opens beside it. Expands left or right depending on which side has more space.",
  },
  {
    title: "Screen-aware",
    desc: 'A "Current Screen" section at the top shows debug options relevant to the open screen (e.g., on Details → that anime\'s DB rows).',
  },
  {
    title: "Multi-tool tabs",
    desc: "Current Screen · Database · Console · Network · App Info — each with its own scrollable content.",
  },
  {
    title: "Trivially removable",
    desc: "~5 mechanical edits to fully remove. Release builds contain zero debug-bubble code (debugImplementation).",
  },
  {
    title: "Non-intrusive",
    desc: "When collapsed, the bubble causes no recomposition of the underlying screen and doesn't interfere with touch handling.",
  },
];

export const NON_GOALS: string[] = [
  "Release-build inclusion (debug-only via debugImplementation)",
  "DB write operations (read-only this phase; mutations are a future 'danger zone')",
  "Remote debugging / ADB bridge (on-device only)",
  "Automated UI testing hooks (separate concern)",
];

// ── Architecture ──────────────────────────────────────────────────────────────

export const MODULES: {
  name: string;
  type: string;
  scope: string;
  desc: string;
  files: string[];
}[] = [
  {
    name: ":core:debug-api",
    type: "implementation (always on classpath)",
    scope: "Tiny — types + CompositionLocals only",
    desc: "DebugContext, DbReference, DebugAction, LocalDebugContext (reader), LocalDebugContextUpdater (writer), LogAppender interface. Stays on the release classpath harmlessly — the locals default to null, the interface is never implemented.",
    files: [
      "DebugContext.kt",
      "LocalDebugContext.kt",
      "LocalDebugContextUpdater.kt",
      "LogAppender.kt",
    ],
  },
  {
    name: ":feature:debug-bubble",
    type: "debugImplementation (debug builds only)",
    scope: "The bubble UI + heavy data sources",
    desc: "The DebugBubble composable, the panel with all tabs, DebugDatabaseBrowser, DebugLogBuffer (implements LogAppender), DebugNetworkStats (OkHttp interceptor), Koin module. NOT on the release classpath.",
    files: [
      "DebugBubble.kt",
      "DebugBubbleState.kt",
      "DebugBubbleViewModel.kt",
      "panel/DebugPanel.kt",
      "panel/CurrentScreenTab.kt",
      "panel/DatabaseTab.kt",
      "panel/ConsoleTab.kt",
      "panel/NetworkTab.kt",
      "panel/AppInfoTab.kt",
      "data/DebugDatabaseBrowser.kt",
      "data/DebugLogBuffer.kt",
      "data/DebugNetworkStats.kt",
      "di/DebugBubbleModule.kt",
    ],
  },
];

export const INTEGRATION = {
  location: "MainActivity.kt — AppRoot(), inside the existing Box (~line 605)",
  snippet: `Box(modifier = Modifier.fillMaxSize()) {
    // …existing ~440 lines of nav content (when(currentKey) dispatch, bottom nav)…

    // Debug bubble — debug builds only. Sibling of the nav content.
    if (com.confused.anikuta.BuildConfig.DEBUG) {
        com.confused.anikuta.feature.debugbubble.DebugBubble()
    }
}`,
  note: "The CompositionLocalProvider(LocalDebugContext provides …) wraps BOTH the nav content AND the bubble — so the bubble is inside the reader's subtree. See the hoisted-state pattern in §5.1 of PLAN.md.",
};

// ── Removal strategy ─────────────────────────────────────────────────────────

export const REMOVAL_LAYERS: { layer: string; desc: string }[] = [
  {
    layer: "1. Gradle debugImplementation",
    desc: ":feature:debug-bubble is debugImplementation in :app. Release builds do not include the module at all — the APK has zero debug-bubble code.",
  },
  {
    layer: "2. BuildConfig.DEBUG runtime gate",
    desc: "Even in debug builds, the bubble only renders when BuildConfig.DEBUG is true. Defense-in-depth.",
  },
  {
    layer: "3. Runtime toggle",
    desc: "A preference (debug_bubble_visible, default false) controls whether the bubble is shown. Hidden by default in debug builds — never gets in the way.",
  },
];

export const REMOVAL_STEPS: string[] = [
  "Delete :feature:debug-bubble/ folder",
  "Delete include(\":feature:debug-bubble\") in settings.gradle.kts",
  "Delete debugImplementation(project(\":feature:debug-bubble\")) in :app/build.gradle.kts",
  "Delete the if (BuildConfig.DEBUG) { DebugBubble() } block in AppRoot() (~line 605)",
  "Delete :app/src/debug/java/…/DebugInit.kt (Koin module + Logger appender + OkHttp interceptor wiring)",
  "(Optional) Delete :core:debug-api + per-screen LocalDebugContext opt-ins — or keep them (harmless, locals default to null)",
];

// ── The bubble ────────────────────────────────────────────────────────────────

export const BUBBLE_SPECS: { label: string; value: string }[] = [
  { label: "Size", value: "48dp circle" },
  { label: "Surface", value: "surfaceVariant.copy(alpha=0.9f), 1dp border, CircleShape" },
  { label: "Icon", value: "Icons.Filled.Bug, tinted onSurfaceVariant" },
  { label: "Shadow", value: "Modifier.shadow(4.dp, CircleShape)" },
  { label: "Drag", value: "detectDragGestures → Animatable<Offset>.snapTo" },
  { label: "Tap vs drag", value: "< 8px total drag = tap (expand/collapse)" },
  { label: "Bounds", value: "Clamped to [0, screenWidth-48dp] × [0, screenHeight-48dp-statusBar]" },
  { label: "Persistence", value: "SharedPreferences (debug_bubble_x/y as Dp); default bottom-end" },
];

// ── The panel ─────────────────────────────────────────────────────────────────

export const PANEL_SPECS: { label: string; value: string }[] = [
  { label: "Width", value: "min(360dp, availableSpace - 8dp)" },
  { label: "Max height", value: "80% of screen height" },
  { label: "Expand direction", value: "bubbleX < screenWidth/2 → RIGHT; else LEFT" },
  { label: "Surface", value: "surface.copy(alpha=0.95f) + backdrop blur (API 31+, else solid)" },
  { label: "Tabs", value: "Screen · Database · Console · Network · App Info (horizontally scrollable)" },
  { label: "Data loading", value: "Lazy — fetched on tab open via Refresh button (no polling)" },
];

export const TABS: {
  name: string;
  icon: string;
  conditional: boolean;
  desc: string;
  features: string[];
}[] = [
  {
    name: "Current Screen",
    icon: "📱",
    conditional: true,
    desc: "Only shown if the current screen provides a DebugContext via LocalDebugContextUpdater. Shows screen-specific data + relevant DB rows + quick actions.",
    features: [
      "Screen name (e.g., 'Details — Frieren')",
      "Screen data: key-value pairs (mainId, sourceId, resolverState)",
      "'View in DB' buttons that jump to the Database tab pre-filtered",
      "Quick actions: screen-specific debug callbacks (e.g., 'Force re-resolve')",
    ],
  },
  {
    name: "Database",
    icon: "🗄️",
    conditional: false,
    desc: "Read-only browser for all 28 SQLDelight tables. Table chips + scrollable grid + search.",
    features: [
      "Table list as horizontally-scrollable chips (28 tables)",
      "Scrollable grid: column headers + rows (first 100)",
      "Search/filter: column LIKE '%query%' (parameterized — bound params, no injection)",
      "BLOB columns render as <BLOB: N bytes>; long text as <long text: N chars>",
      "Read-only banner — mutations are a future 'danger zone' phase",
    ],
  },
  {
    name: "Console",
    icon: "📜",
    conditional: false,
    desc: "In-memory ring buffer of the last 1000 log entries. Filterable by tag + level. Color-coded.",
    features: [
      "Scrollable list, newest at bottom (auto-scroll on open)",
      "Filter by tag (text input) + level (V/D/I/W/E multi-select chips)",
      "Clear button (clears in-memory buffer, not logcat)",
      "Export to file (via SAF) for sharing",
      "Throwable stored as capped 2KB string (prevents stack-trace memory blowup)",
    ],
  },
  {
    name: "Network",
    icon: "🌐",
    conditional: false,
    desc: "OkHttp interceptor stats. App-level traffic only (extension traffic via Injekt is a known limitation).",
    features: [
      "Summary: total requests, total bytes, avg latency, error count",
      "Status-code histogram (2xx / 3xx / 4xx / 5xx / network-errors)",
      "Recent requests: last 50 (method, host, status, latency, bytes)",
      "Tap a row for full request + response headers",
      "Wraps both the default + download OkHttpClients in debug builds",
    ],
  },
  {
    name: "App Info",
    icon: "ℹ️",
    conditional: false,
    desc: "Build/version/memory/module counts + read-only view of non-sensitive preferences.",
    features: [
      "Build: version name, version code, build type, Git SHA",
      "Module count (44), DB table count (28)",
      "Memory: used/available heap (Runtime.totalMemory/maxMemory)",
      "Preferences: non-sensitive values only (theme, display prefs — tokens excluded by allowlist)",
    ],
  },
];

// ── Data sources ──────────────────────────────────────────────────────────────

export const DATA_SOURCES: {
  name: string;
  pattern: string;
  desc: string;
  keyFix: string;
}[] = [
  {
    name: "Screen context",
    pattern: "Hoisted MutableState + two CompositionLocals (reader + writer)",
    desc: "Screens call LocalDebugContextUpdater to set the context; the bubble reads LocalDebugContext. The provider wraps both nav content + bubble in AppRoot.",
    keyFix: "D-162 C1: CompositionLocal values don't flow across siblings — the naive pattern (screen wraps its own provider) would leave the bubble always reading null.",
  },
  {
    name: "Database browser",
    pattern: "SqlDriver injected directly via Koin",
    desc: "Runs SELECT * FROM <table> LIMIT 100 via the raw SqlDriver. Table list from sqlite_master. Search with bound parameters.",
    keyFix: "D-162 I2/I3/I4: AnikutaDatabase doesn't expose sqlDriver publicly; string-interpolated search is SQL injection; BLOB columns need type-aware rendering.",
  },
  {
    name: "Console log",
    pattern: "LogAppender interface in :core:debug-api + DebugLogBuffer impl in :feature:debug-bubble",
    desc: "Logger holds a @Volatile appender: LogAppender? = null. DebugLogBuffer implements it (ring buffer, 1000 entries). Wiring in :app/src/debug/DebugInit.kt.",
    keyFix: "D-162 C2: :core:common (always on classpath) can't reference DebugLogBuffer (debug-only) — would break release builds. The interface breaks the cycle.",
  },
  {
    name: "Network stats",
    pattern: "OkHttp interceptor wrapped in :app/src/debug/DebugInit.kt",
    desc: "DebugNetworkStats : Interceptor counts requests/bytes/status codes. Wraps both the default + download clients in debug builds.",
    keyFix: "D-162 I1: no NetworkModule in :app; extensions use a separate Injekt client (extension traffic is a known, disclosed limitation).",
  },
];

// ── Implementation phases ─────────────────────────────────────────────────────

export const PHASES: {
  id: string;
  scope: string;
  est: string;
}[] = [
  { id: "DB-1", scope: "Module scaffold + DebugBubble composable (bubble only, draggable, position persistence) + integration in AppRoot", est: "2-3h" },
  { id: "DB-2", scope: "Panel shell (tabs, expand-direction, collapse) + DebugContext + LocalDebugContext + Current Screen tab (empty context)", est: "2-3h" },
  { id: "DB-3", scope: "Database tab (table list + table view + parameterized search + BLOB handling)", est: "3-4h" },
  { id: "DB-4", scope: "DebugLogBuffer + LogAppender + Logger wiring + Console tab (list + filters + clear)", est: "2-3h" },
  { id: "DB-5", scope: "DebugNetworkStats interceptor + OkHttp wiring + Network tab", est: "2-3h" },
  { id: "DB-6", scope: "App Info tab + polish (animations, bounds clamping, rotation, IME, edge cases)", est: "1-2h" },
  { id: "DB-7", scope: "Screen opt-ins: Details, Browse, Watch, Downloads provide LocalDebugContext", est: "1-2h" },
  { id: "DB-8", scope: "Device testing + docs update", est: "1h" },
];

export const PHASE_TOTAL = "14-21h across multiple sessions. Each phase is independently shippable.";

// ── Sub-agent review ──────────────────────────────────────────────────────────

export const REVIEW_SUMMARY = {
  critical: [
    { id: "C1", issue: "CompositionLocal siblings — bubble can't read screen-provided context", fix: "Hoist MutableState to AppRoot + reader/writer CompositionLocals" },
    { id: "C2", issue: "Logger (:core:common) can't reference DebugLogBuffer (debug-only)", fix: "LogAppender interface in :core:debug-api; wiring in :app/src/debug" },
    { id: "C3", issue: "Koin module can't be imported in :app/src/main (debug-only dep)", fix: "Debug-only source set (DebugInit.kt)" },
    { id: "C4", issue: "Feature modules can't import from debugImplementation", fix: "Split into :core:debug-api (always) + :feature:debug-bubble (debug)" },
    { id: "C5", issue: "WatchScreen carve-out unaddressed (player gestures, immersive mode, rotation)", fix: "Auto-hide on Watch + rotation re-clamp + IME padding" },
  ],
  important: [
    { id: "I1", issue: "Network interceptor placement — no NetworkModule; extension traffic bypasses Koin", fix: "Wrap in DebugInit.kt; disclose extension-traffic limitation" },
    { id: "I2", issue: "database.sqlDriver is private (SQLDelight doesn't expose it)", fix: "Inject SqlDriver directly via Koin" },
    { id: "I3", issue: "SQL injection in search (string interpolation)", fix: "Bound parameters + column validation via PRAGMA table_info" },
    { id: "I4", issue: "BLOB columns — getString() returns garbage", fix: "Detect type, render as <BLOB: N bytes>" },
    { id: "I5", issue: "DebugContext cleanup on screen exit (VM leak)", fix: "DisposableEffect { onDispose { updateDebugContext(null) } }" },
    { id: "I6", issue: "Rotation + IME — persisted offset wrong in landscape; keyboard covers panel", fix: "Re-clamp on orientation change + Modifier.imePadding()" },
    { id: "I7", issue: "Animatable vs mutableStateOf inconsistency", fix: "Use Animatable<Offset> (matches snapTo calls)" },
    { id: "I8", issue: "'Trivially removable' overstated (claimed 4 lines)", fix: "Honest edit list — ~5 mandatory + 2 optional edits" },
  ],
  assessment:
    "The sub-agent's review was thorough and technically accurate. Every CRITICAL issue was a real compile-time or semantic blocker that would have surfaced within the first hour of implementation. All fixes have been incorporated into the plan. No false positives found in CRITICAL/IMPORTANT categories.",
};

// ── Open questions ────────────────────────────────────────────────────────────

export const OPEN_QUESTIONS: {
  q: string;
  recommendation: string;
}[] = [
  {
    q: "Debug builds only? (debugImplementation vs implementation)",
    recommendation: "Debug-only for now. Release builds contain zero debug-bubble code. If you want it in release (hidden by default), swap one Gradle keyword.",
  },
  {
    q: "DB browser read-only or read-write?",
    recommendation: "Read-only this phase. Mutations via the app's proper flows are safer. A 'danger zone' write toggle can be added later.",
  },
  {
    q: "Log buffer size? (1000 entries ~200KB)",
    recommendation: "1000 entries covers a typical session. One-line change if you want more (e.g., 5000).",
  },
  {
    q: "Default visibility in debug builds?",
    recommendation: "Hidden by default — toggled on when needed. Never gets in the way of normal use.",
  },
  {
    q: "How to toggle the bubble on?",
    recommendation: "A simple 'Show debug bubble' toggle in Settings → General (debug builds only). Alternatives: easter-egg (tap version 7×) or developer-options screen.",
  },
  {
    q: "Screen opt-in scope? (which screens provide context initially)",
    recommendation: "Details + Browse + Watch + Downloads initially. Other screens show generic tabs. Incremental — more screens can opt in over time.",
  },
];
