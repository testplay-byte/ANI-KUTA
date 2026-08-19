# Debug Bubble — Implementation Plan

> A floating, draggable debug overlay that sits on top of every screen in the app.
> Tap to expand a panel with database, console-log, network, and screen-specific
> debug tools. Debug-only, trivially removable, zero impact on app code when off.

**Status:** PLANNING (not yet implemented). This doc is the spec — implementation
begins after the user reviews + approves. Revised after sub-agent review
(D-162 — see §11 for the issues caught + fixes applied).

**Branch:** `feature/debug-bubble` (all work happens here; main is untouched).

---

## 1. Goals & Non-Goals

### Goals
- **App-wide overlay:** a small floating bubble renders on top of every screen
  (Browse, Library, Search, More, Details, Watch, Downloads, Settings, etc.).
- **Draggable:** the user can drag the bubble anywhere on the screen (X + Y).
  Position persists across sessions.
- **Expandable panel:** tapping the bubble opens a panel beside it. The panel
  expands **left** or **right** depending on which side has more space.
- **Screen-aware:** the panel shows a "Current Screen" section at the top with
  debug options relevant to whatever screen is open (e.g., on a Details page,
  it shows that anime's DB rows; on Browse, it shows the browse cache state).
- **Multi-tool:** the panel has tabs — Current Screen · Database · Console ·
  Network · App Info — each with its own scrollable content.
- **Trivially removable:** removing the debug bubble from the app = delete one
  Gradle line + one composable call. Zero impact on release builds or app logic.
- **Non-intrusive:** when collapsed (just the bubble), it must NOT cause
  recomposition of the underlying screen or interfere with touch handling.

### Non-Goals (this phase)
- **Release-build inclusion:** the bubble is debug-build-only. Release builds
  will not contain the module at all (via `debugImplementation`). If the user
  later wants it in release builds (hidden by default), that's a follow-up.
- **DB write operations:** the database browser is **read-only** this phase.
  Mutations (insert/update/delete) are a future "danger zone" toggle.
- **Remote debugging:** no network-based debug bridge (ADB over WiFi, etc.).
  The bubble is on-device only.
- **Automated UI testing hooks:** the bubble doesn't expose espresso/compose
  test APIs. That's a separate concern.

---

## 2. Architecture

### 2.1 Module structure (two modules — split for correct dependency direction)

Two new Gradle modules. The split is **required** by Compose/Gradle semantics
(see §11 — sub-agent review C2/C4): feature modules (`:feature:anime-details`,
etc.) need to reference `DebugContext` + `LocalDebugContext` to opt in, but
they can't import from a `debugImplementation` module (release builds won't
compile). So the types live in an always-available `:core:debug-api`, and the
bubble UI + heavy data sources live in `:feature:debug-bubble` (debug-only).

**`:core:debug-api`** (always on classpath — `implementation`, tiny):
```
:core:debug-api/
└── src/main/java/com/confused/anikuta/core/debugapi/
    ├── DebugContext.kt          # Data model (DebugContext, DbReference, DebugAction)
    ├── LocalDebugContext.kt     # CompositionLocal for the bubble to READ context
    ├── LocalDebugContextUpdater.kt  # CompositionLocal for screens to WRITE context
    └── LogAppender.kt           # Interface that Logger holds (decouples :core:common
                                 # from the debug-bubble module — see §5.3)
```

**`:feature:debug-bubble`** (debug builds only — `debugImplementation`):
```
:feature:debug-bubble/
├── build.gradle.kts
└── src/main/java/com/confused/anikuta/feature/debugbubble/
    ├── DebugBubble.kt              # The public composable (bubble + panel)
    ├── DebugBubbleState.kt         # State holder (Animatable<Offset>, expanded, tab)
    ├── DebugBubbleViewModel.kt     # Owns the data (DB tables, log snapshot, net stats)
    ├── di/
    │   └── DebugBubbleModule.kt    # Koin DI (provides DebugLogBuffer, browser, stats)
    ├── panel/
    │   ├── DebugPanel.kt           # The expanded panel (tabbed layout)
    │   ├── CurrentScreenTab.kt     # Screen-specific debug content (reads LocalDebugContext)
    │   ├── DatabaseTab.kt          # DB table browser
    │   ├── ConsoleTab.kt           # Log viewer
    │   ├── NetworkTab.kt           # Network stats
    │   └── AppInfoTab.kt           # Build/version/memory info
    └── data/
        ├── DebugDatabaseBrowser.kt # Reads via SqlDriver (read-only, parameterized)
        ├── DebugLogBuffer.kt       # In-memory ring buffer; implements LogAppender
        └── DebugNetworkStats.kt    # OkHttp interceptor (impl of Interceptor)
```

**Why two modules:** `:core:debug-api` is ~5 tiny files with no behavior — it
stays on the release classpath harmlessly (the CompositionLocals default to
`null`, the `LogAppender` interface is never implemented). Removing the bubble
UI = delete `:feature:debug-bubble` only; the `:core:debug-api` references in
feature modules remain valid (they just produce `null`). Feature modules depend
on `:core:debug-api` (always); `:app` depends on `:feature:debug-bubble` via
`debugImplementation`.

### 2.2 Dependencies

```
:core:debug-api depends on:
  :core:common          (LogLevel enum, for LogAppender)
  (nothing else — it's just types + CompositionLocals)

:feature:debug-bubble depends on:
  :core:debug-api       (DebugContext, LocalDebugContext, LogAppender)
  :core:common          (Logger)
  :core:database        (SqlDriver — injected directly; see §5.2)
  :core:designsystem    (theme, components)
  :core:preferences     (bubble position persistence)
  :core:network         (OkHttp Interceptor type)
```

Feature modules (`:feature:anime-details`, etc.) depend on `:core:debug-api`
ONLY (always available). They never reference `:feature:debug-bubble` — so
their `import com.confused.anikuta.core.debugapi.*` lines resolve in both
debug and release builds.

### 2.3 Integration point

In `MainActivity.kt`'s `AppRoot()` composable (~line 605, just before the
closing `}` of the inner `Box` that contains the `when(currentKey)` dispatch),
the debug bubble is added as a **sibling overlay**:

```kotlin
// MainActivity.kt — AppRoot(), inside the existing Box
Box(modifier = Modifier.fillMaxSize()) {
    // …existing ~440 lines of nav content (when(currentKey) dispatch, bottom nav, etc.)…

    // Debug bubble — debug builds only. Sibling of the nav content.
    // The hoisted DebugContext state (below) lets screens write context that
    // this sibling bubble can read — CompositionLocal values DO flow to siblings
    // when the provider wraps BOTH. See §5.1 for the hoisted-state pattern.
    if (com.confused.anikuta.BuildConfig.DEBUG) {
        com.confused.anikuta.feature.debugbubble.DebugBubble()
    }
}
```

**Critical (D-162 C1):** the `CompositionLocalProvider(LocalDebugContext provides
…)` must wrap BOTH the nav content AND the bubble — not just the screen. See
§5.1 for the hoisted-state pattern that makes this work. Screens don't provide
context via their own `CompositionLocalProvider`; they call an updater that
writes to a hoisted `MutableState` in `AppRoot`.

### 2.4 Easy-removal strategy (three layers + honest edit list)

1. **Gradle `debugImplementation`:** `:feature:debug-bubble` is
   `debugImplementation` in `:app`. **Release builds do not include the module
   at all** — the APK has zero debug-bubble code.

2. **`BuildConfig.DEBUG` runtime gate:** even in debug builds, the bubble only
   renders when `BuildConfig.DEBUG` is true.

3. **Runtime toggle:** a preference (`debug_bubble_visible`, default `true`)
   controls whether the bubble is shown. **Visible by default in debug builds**
   (per user decision D-163) — the user wants it always-on during the debugging
   phase. A Settings toggle can disable it when not needed.

**To fully remove the debug bubble (honest edit list — D-162 I8):**
1. Delete `:feature:debug-bubble/` folder.
2. Delete `include(":feature:debug-bubble")` in `settings.gradle.kts`.
3. Delete `debugImplementation(project(":feature:debug-bubble"))` in
   `:app/build.gradle.kts`.
4. Delete the `if (BuildConfig.DEBUG) { DebugBubble() }` block in `AppRoot()`
   (MainActivity.kt ~line 605).
5. Delete `:app/src/debug/java/…/DebugInit.kt` (the debug-only wiring file —
   Koin module registration, Logger appender wiring, OkHttp interceptor
   wrapping; see §5.3/§5.4).
6. (Optional) Delete `:core:debug-api` + the per-screen `LocalDebugContext`
   opt-ins (DB-7). If you keep `:core:debug-api`, the opt-ins are harmless —
   the CompositionLocals default to `null` and the "Current Screen" tab is
   hidden.

That's ~5 mandatory edits (steps 1-5) + 2 optional (step 6). Not "four lines"
as originally claimed, but a small, well-defined, mechanical edit list. No
deep refactoring of app logic — the app's nav content, screens, and data flows
are untouched.

### 2.5 Performance guarantees

- **Collapsed bubble:** a single `Canvas`/`Icon` in a `Box` with `pointerInput`.
  No state reads from the underlying screen. The bubble's drag offset is held
  in its own `Animatable` — recomposing it doesn't trigger recomposition of the
  nav content beneath (they're siblings in a `Box`, not parent-child).
- **Panel content:** lazy-loaded. The DB/log/network data is fetched **only when
  the user opens that tab**, via a "Refresh" button. No polling, no background
  collection. The panel is `AnimatedVisibility`-gated — when collapsed, its
  composables aren't in the tree.
- **Log buffer:** the in-memory ring buffer (see §5.3) is an `ArrayDeque`
  capped at 1000 entries. Appending is O(1). The buffer is only read when the
  Console tab is open + the user taps Refresh.
- **Network interceptor:** the OkHttp interceptor increments atomic counters
  (request count, total bytes, status-code histogram). O(1) per request. No
  per-request logging to memory unless the Console tab is actively capturing.

---

## 3. The Bubble

### 3.1 Visual (D-163 — squircle, not circle)
- **Squircle shape** (rounded square / superellipse), not a pure circle. Per
  user: "a circle and icon" but with a squircle silhouette. Implemented as a
  `RoundedCornerShape(50%)` on a 48dp box (visual squircle) or a custom
  `CornerSize` yielding a superellipse-like curve.
- 48dp size, semi-transparent dark surface (`surfaceVariant.copy(alpha=0.9f)`),
  1dp border.
- Contents: a small bug icon (`Icons.Filled.Bug`) centered, tinted with
  `onSurfaceVariant`.
- Subtle shadow (`Modifier.shadow(4.dp, RoundedCornerShape(50%))`) for depth.
- A long-press on the bubble shows a "Hide bubble" tooltip + a drag handle hint.

### 3.2 Drag mechanics (D-163 — position does NOT persist)
- Position held in `DebugBubbleState.offset: Offset` (px), backed by
  `Animatable<Offset>` for smooth spring-back if the user flings.
- `Modifier.pointerInput(Unit) { detectDragGestures(onDrag = { delta ->
  state.offset.snapTo(state.offset.value + delta) }) }`.
- **Bounds clamping:** after each drag, clamp X to `[0, screenWidth - 48dp]`
  and Y to `[0, screenHeight - 48dp - statusBarHeight]`. Uses
  `LocalConfiguration.screenWidthDp` + `WindowInsets.statusBars`.
- **Position does NOT persist (D-163):** per user decision, the bubble returns
  to its default position (bottom-end, 16dp inset) every time the app is
  reopened. No SharedPreferences write on drag end. The offset is ephemeral —
  held in `remember { DebugBubbleState() }`, reset on process restart. This is
  simpler than persisting + avoids stale positions after rotation/uninstall.
- **Tap vs drag disambiguation:** `detectDragGestures` provides `onDragStart`
  — if the total drag distance is < 8px, treat it as a tap (expand/collapse
  the panel). Implemented by tracking a `dragged` flag in `onDragStart` +
  checking `onDragEnd` distance.

### 3.3 Tap behavior
- Tapping the bubble toggles `state.expanded`.
- When expanding: compute expand direction (see §4.1).
- When collapsing: animate the panel out (fade + slide toward the bubble).

---

## 4. The Panel

### 4.1 Expand-direction detection (D-163 — direction by bubble position)
When `state.expanded` becomes true, the panel expands toward the side with more
space, AND vertically away from the nearest edge — so it's always fully visible:

1. Read the bubble's current X + Y position (px) + screen width/height (px).
2. **Horizontal:** if `bubbleX < screenWidth / 2` → panel opens to the **RIGHT**
   of the bubble; else → **LEFT**. The panel's horizontal anchor is the bubble's
   edge on the chosen side.
3. **Vertical:** if `bubbleY < screenHeight / 2` → panel extends **DOWNWARD**
   from the bubble's Y; else → **UPWARD** (anchored at the bubble's Y, extending
   up). This handles the user's spec: "if the bubble is at the top then it
   expands downwards; if at the bottom then upwards."
4. **Panel width (D-163):** based on the actual device width — the panel takes
   up most of the display width with a little padding on left/right. Width =
   `screenWidth - 2 * horizontalPadding` where `horizontalPadding = 12dp` (the
   panel hugs the screen edges with a small margin). Capped at the available
   space on the chosen side (if the bubble is far to one side, the panel may
   be narrower to fit).
5. The panel is always fully on-screen (clamped). If the bubble is centered,
   the panel opens right + downward by default.

### 4.2 Layout
```
┌─────────────────────────────────┐
│ Debug                    [✕]    │  ← Header (title + close)
├─────────────────────────────────┤
│ [Screen] [DB] [Console] [Net] [App] │  ← Tab strip (horizontally scrollable)
├─────────────────────────────────┤
│                                 │
│  (active tab content —          │  ← Scrollable (verticalScroll)
│   lazy-loaded on tab open)      │
│                                 │
│                                 │
│                      [↻ Refresh]│  ← Per-tab refresh button
└─────────────────────────────────┘
```

- Panel max height: `75% of screen height` (D-163 — user preferred 75% over 80%).
- Panel surface: `surface.copy(alpha=0.95f)` with `backdropBlur` if available
  (RenderEffect, API 31+; falls back to solid surface on lower APIs).
- Close button (✕) collapses the panel.
- Each tab has its own Refresh button (data fetched when the panel/tab opens —
  D-163: "data should be fetched when the menu is opened").
- **Tabs are flexible (D-163):** the 5 default tabs are the baseline, but the
  tab set can vary per screen (a screen's `DebugContext` can declare additional
  screen-specific tabs or hide irrelevant ones). Adding/removing tabs later is
  a low-cost change.

### 4.3 Tabs

#### Tab 1 — Current Screen (conditional)
Only shown if `LocalDebugContext.current != null`. Displays:
- **Screen name** (e.g., "Anime Details — Frieren").
- **Screen data:** key-value pairs the screen chose to expose (e.g., `mainId`,
  `currentEpisode`, `resolverState`).
- **Relevant DB rows:** a "View in DB" button that jumps to the Database tab
  pre-filtered to the relevant table + row (e.g., `content WHERE main_id = ?`).
- **Quick actions:** screen-specific debug actions (e.g., "Force re-resolve",
  "Clear cache for this anime") — opt-in per screen via the `DebugContext`.

If no context is provided, this tab is hidden and the Database tab is default.

#### Tab 2 — Database
- **Table list:** a horizontally-scrollable row of chips, one per SQLDelight
  table (28 tables: `content`, `library_item`, `downloaded_episode`,
  `episode_schedule`, `anime_update_state`, `notification_config`, etc.).
  Tapping a chip selects that table.
- **Table view:** a scrollable grid (rows × columns). Headers = column names
  (from the `.sq` schema). Each cell = the value, rendered as a string
  (truncated with ellipsis if long; tap to expand).
- **Row count** at the top ("142 rows · showing first 100").
- **Search/filter:** a text field that filters rows by a simple
  `column LIKE '%query%'` on the first text column.
- **Read-only:** no edit/delete buttons. A banner notes "Read-only — mutations
  are a future phase."
- Implementation: uses `AnikutaDatabase`'s underlying `SqlDriver` to run
  `SELECT * FROM <table> LIMIT 100`. The table list is derived from
  `sqlite_master` (`SELECT name FROM sqlite_master WHERE type='table'`).

#### Tab 3 — Console
- **Log entries:** a vertically-scrollable list, newest at the bottom
  (auto-scrolls to bottom on open). Each entry shows: timestamp, level
  (color-coded), tag, message.
- **Filter bar:** filter by tag (text input) + level (chips: V/D/I/W/E,
  multi-select).
- **Clear button:** clears the in-memory buffer (not logcat).
- **Export button:** writes the current buffer to a file (via SAF) for sharing.
- Implementation: reads from `DebugLogBuffer` (a ring buffer appended by
  `Logger` — see §5.3).

#### Tab 4 — Network
- **Summary stats:** total requests, total bytes downloaded, avg latency,
  error count.
- **Status-code histogram:** a bar chart (2xx / 3xx / 4xx / 5xx).
- **Recent requests:** a scrollable list of the last 50 requests (method,
  host, status, latency, bytes). Tap a row to see full headers (request +
  response).
- Implementation: reads from `DebugNetworkStats` (an OkHttp interceptor —
  see §5.4).

#### Tab 5 — App Info
- **Build:** version name, version code, build type (debug/release), Git SHA
  (if available via BuildConfig).
- **Modules:** count of Gradle modules (44).
- **DB tables:** count (28).
- **Memory:** used/available heap (`Runtime.totalMemory()` / `maxMemory()`).
- **Preferences:** a read-only view of key SharedPreferences values (app
  preferences, theme, etc. — NOT sensitive values like tokens).

---

## 5. Data Sources & Integration Points

### 5.1 Screen context — hoisted state + two CompositionLocals (D-162 C1 fix)

**The problem with the naive approach:** CompositionLocal values flow DOWN the
composition subtree. If the bubble is a sibling of the screen content in
`AppRoot`'s `Box`, and a screen wraps ITS content in
`CompositionLocalProvider(LocalDebugContext provides …)`, the bubble (being
outside that provider's subtree) will always see `null`. The "Current Screen"
tab would silently never work.

**The fix:** hoist a `MutableState<DebugContext?>` to `AppRoot`. Expose TWO
CompositionLocals — one for the bubble to READ (`LocalDebugContext`), one for
screens to WRITE (`LocalDebugContextUpdater`). The provider wraps BOTH the nav
content AND the bubble, so the bubble is inside the reader's subtree.

```kotlin
// :core:debug-api
data class DebugContext(
    val screenName: String,
    val screenData: Map<String, String> = emptyMap(),
    val relevantTables: List<DbReference> = emptyList(),
    val actions: List<DebugAction> = emptyList(),
)

data class DbReference(val table: String, val filterColumn: String, val filterValue: String, val label: String)
data class DebugAction(val label: String, val action: () -> Unit)

val LocalDebugContext = compositionLocalOf<DebugContext?> { null }
val LocalDebugContextUpdater = compositionLocalOf<(DebugContext?) -> Unit> { {} }
```

```kotlin
// :app — AppRoot() (the provider wraps BOTH nav content + bubble)
var debugContext by remember { mutableStateOf<DebugContext?>(null) }
CompositionLocalProvider(
    LocalDebugContext provides debugContext,
    LocalDebugContextUpdater provides { ctx -> debugContext = ctx },
) {
    Box(Modifier.fillMaxSize()) {
        // …nav content (when(currentKey) dispatch)…
        if (BuildConfig.DEBUG) DebugBubble()  // reads LocalDebugContext.current
    }
}
```

**Screens opt in** by calling the updater (NOT by wrapping their own provider):
```kotlin
// e.g., in DetailsScreen
val updateDebugContext = LocalDebugContextUpdater.current
val ctx = remember(content) {
    DebugContext(
        screenName = "Details — ${content.title}",
        screenData = mapOf("mainId" to content.mainId),
        relevantTables = listOf(DbReference("content", "main_id", content.mainId, "View content row")),
    )
}
LaunchedEffect(ctx) { updateDebugContext(ctx) }
DisposableEffect(Unit) { onDispose { updateDebugContext(null) } }  // clear on exit (D-162 I5)
// …existing screen content (no wrapper)…
```

The `DisposableEffect` is **critical** — without it, navigating Browse → Details
leaves Browse's context (with captured ViewModel references) visible until
Details overwrites it, and the captured lambdas leak the previous screen's VM.

This is **opt-in and non-breaking** — screens that don't call the updater leave
`LocalDebugContext.current == null`, and the "Current Screen" tab is hidden.

### 5.2 Database browser — `DebugDatabaseBrowser`

```kotlin
class DebugDatabaseBrowser(private val driver: SqlDriver) {  // inject SqlDriver directly (D-162 I2)
    fun listTables(): List<String>  // SELECT name FROM sqlite_master WHERE type='table'
    fun queryTable(table: String, limit: Int = 100): Pair<List<ColumnInfo>, List<List<String?>>>
    fun search(table: String, column: String, query: String, limit: Int = 100): …
}
```

**D-162 fixes:**
- **Inject `SqlDriver` directly** (I2) — SQLDelight's generated `AnikutaDatabase`
  doesn't expose `driver` as a public property. `SqlDriver` is already in Koin.
- **Parameterized search** (I3): `WHERE $column LIKE ? LIMIT ?` with bound
  parameters (`%$query%`, `limit`). Column + table names validated against
  `PRAGMA table_info(<table>)` before interpolation (column names can't be
  parameterized in SQLite).
- **BLOB handling** (I4): `PRAGMA table_info` queried for column types. BLOB
  columns render as `<BLOB: N bytes>` (not decoded as string — `getString()` on
  a BLOB returns garbage). Text columns > 4KB render as `<long text: N chars>`.
- **Table-name validation:** table chip list is the only way to select a table
  (no free-text table input) — prevents SQL injection via table name.

### 5.3 Console log — `LogAppender` interface + `DebugLogBuffer` (ring buffer)

**D-162 C2 fix:** `Logger` (in `:core:common`, always on classpath) can't
reference `DebugLogBuffer` (in `:feature:debug-bubble`, debug-only) — that would
break release builds. Instead, define a `LogAppender` interface in
`:core:debug-api` (always available). `Logger` holds a `@Volatile var appender:
LogAppender? = null`. `DebugLogBuffer` (in `:feature:debug-bubble`) implements
`LogAppender`. The wiring (`Logger.setAppender(DebugLogBuffer())`) happens in
`:app/src/debug/java/…/DebugInit.kt` — a debug-only source set, never compiled
into release.

```kotlin
// :core:debug-api — LogAppender.kt
interface LogAppender {
    fun append(level: LogLevel, tag: String, message: String, throwable: Throwable?)
}
```

```kotlin
// :core:common — Logger.kt (extended; LogAppender imported from :core:debug-api)
object Logger {
    @Volatile private var appender: LogAppender? = null
    fun setAppender(a: LogAppender?) { appender = a }

    fun d(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (enabled && minLevel <= LogLevel.DEBUG) {
            val msg = message()
            Log.d(tag, msg, throwable)
            appender?.append(LogLevel.DEBUG, tag, msg, throwable)
        }
    }
    // …same for v/i/w/e…
}
```

```kotlin
// :feature:debug-bubble — DebugLogBuffer.kt
class DebugLogBuffer(private val capacity: Int = 10000) : LogAppender {
    private val deque = ArrayDeque<LogEntry>()
    private val lock = Any()

    override fun append(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        synchronized(lock) {
            if (deque.size >= capacity) deque.removeFirst()
            // D-162 M2: don't store the raw Throwable (can be 20-50KB with stack
            // trace) — store a capped string representation instead.
            val tStr = throwable?.stackTraceToString()?.take(2000)
            deque.addLast(LogEntry(System.currentTimeMillis(), level, tag, message, tStr))
        }
    }
    fun snapshot(): List<LogEntry> = synchronized(lock) { deque.toList() }
    fun clear() = synchronized(lock) { deque.clear() }

    data class LogEntry(val timestamp: Long, val level: LogLevel, val tag: String, val message: String, val throwableString: String?)
}
```

- Capacity 10,000 entries (D-163 — user-specified). Throwable stored as a capped (2KB) string (M2) —
  prevents a single error with a huge stack trace from blowing the buffer.
- `appender` is `@Volatile` + null by default — zero overhead in release builds.
- Wiring in `:app/src/debug/java/…/DebugInit.kt` (debug source set only):
  ```kotlin
  fun initDebugLogging() {
      Logger.setAppender(GlobalContext.get().get<DebugLogBuffer>())
  }
  ```
  Called from `AnikutaApp.onCreate()` guarded by `if (BuildConfig.DEBUG)`.
- **Performance (M1):** `synchronized` per log call is ~1-10μs. If profiling
  shows contention during heavy logging (e.g., playback), swap to
  `ConcurrentLinkedQueue` with size cap-on-read. Optimization lever, not a fix-now.

### 5.4 Network stats — `DebugNetworkStats` (OkHttp interceptor)

```kotlin
class DebugNetworkStats : Interceptor {
    private val requestCount = AtomicLong(0)
    private val totalBytes = AtomicLong(0)
    private val statusBuckets = IntArray(5) // 2xx / 3xx / 4xx / 5xx / network-errors (D-162 M3)
    private val recentRequests = ArrayDeque<RequestRecord>(50) // synchronized, capped

    override fun intercept(chain: Interceptor.Chain): Response { … }
    fun snapshot(): NetworkSnapshot
}
```

**D-162 I1 fixes (interceptor placement):**
- There is no `NetworkModule` in `:app`; the OkHttpClient is built in
  `:core:network`'s `HttpClientFactory.create()` + registered in `AnikutaApp.appModule`.
- The interceptor is wrapped in `:app/src/debug/java/…/DebugInit.kt`:
  ```kotlin
  // DebugInit.kt (debug source set only)
  fun wrapDebugOkHttp(client: OkHttpClient): OkHttpClient =
      client.newBuilder().addInterceptor(DebugNetworkStats()).build()
  ```
  `AnikutaApp.appModule` binds the wrapped client in debug.
- **Extension traffic caveat (honestly disclosed):** extensions use a SEPARATE
  OkHttpClient via `Injekt.addSingleton(fullType<NetworkHelper>(), …)` — the
  interceptor does NOT see extension HTTP calls (image fetches, episode lists,
  video resolving). This is a known limitation. The Network tab shows app-level
  traffic (AniList API, source API calls via the Koin client) only. Capturing
  extension traffic requires wiring the interceptor into `NetworkHelper` (in
  `:core:source-api`) — deferred to a future phase if needed.
- **Download client:** there's a second named OkHttpClient (`DOWNLOAD`). The
  interceptor wraps BOTH in debug builds (the download client streams the
  largest payloads — excluding it would make "total bytes" misleading).
- The interceptor is O(1) per request (atomic increments + capped deque).
- `recentRequests` is capped at 50; oldest evicted.

---

## 6. State & Lifecycle

### 6.1 `DebugBubbleState` (D-162 I7 — Animatable, not mutableStateOf)
```kotlin
class DebugBubbleState {
    val offset = Animatable(Offset.Zero)              // Animatable (spring-back fling support)
    var expanded by mutableStateOf(false)              // panel open?
    var activeTab by mutableStateOf(DebugTab.Screen)   // current tab
    var expandDirection by mutableStateOf(ExpandDirection.Right) // L/R
}
```

**D-162 I7 fix:** §3.2's drag code uses `state.offset.snapTo(state.offset.value + delta)`
— that only works on `Animatable`, not `mutableStateOf`. The declaration now
matches: `offset` is an `Animatable<Offset>` (gives `snapTo` + `animateTo` for
spring-back on release).

Held via `remember { DebugBubbleState() }` in the `DebugBubble()` composable.
Not a ViewModel — the bubble's UI state is ephemeral (resets on process death).
Position is the only persisted field (via SharedPreferences, restored on startup
via `offset.animateTo(savedOffset)`).

### 6.2 `DebugBubbleViewModel`
Owns the **data** (not UI state): DB query results, log snapshot, network
snapshot. Each tab calls a `refresh()` function on the ViewModel when opened.
State exposed as `StateFlow`s. This keeps the heavy data out of the composable
and survives configuration changes.

---

## 7. Security Considerations

- **Debug builds only:** the module is `debugImplementation` — release APKs
  contain zero debug-bubble code. No risk of leaking DB/logs/network data in
  production.
- **Read-only DB:** the browser can't mutate data. A future "danger zone"
  write mode would require an explicit confirmation dialog.
- **No sensitive prefs:** the App Info tab shows only non-sensitive preferences
  (theme, display prefs). Tokens/credentials are excluded by an allowlist.
- **On-device only:** no remote bridge — the data never leaves the device via
  the bubble.

---

## 8. Implementation Phases (for when the user approves)

| Phase | Scope | Est. |
|-------|-------|------|
| **DB-1** | Module scaffold + `DebugBubble` composable (bubble only, draggable, position persistence) + integration in AppRoot | 2-3h |
| **DB-2** | Panel shell (tabs, expand-direction, collapse) + `DebugContext` + `LocalDebugContext` + Current Screen tab (empty context) | 2-3h |
| **DB-3** | Database tab (table list + table view + search) | 3-4h |
| **DB-4** | `DebugLogBuffer` + Logger wiring + Console tab (list + filters + clear) | 2-3h |
| **DB-5** | `DebugNetworkStats` interceptor + OkHttp wiring + Network tab | 2-3h |
| **DB-6** | App Info tab + polish (animations, bounds clamping, edge cases) | 1-2h |
| **DB-7** | Screen opt-ins: Details, Browse, Watch, Downloads provide `LocalDebugContext` | 1-2h |
| **DB-8** | Device testing + docs update | 1h |

**Total estimate:** 14-21h across multiple sessions. Each phase is independently
shippable (the bubble works end-to-end after DB-1; later phases add tabs).

---

## 9. Design Decisions — RESOLVED (D-163, user review)

All open questions answered by the user. Decisions locked in:

1. **Debug builds only?** → ✅ **Yes, debug-only** (`debugImplementation`).
   Release builds will not contain the bubble. When the user is ready to ship a
   release, the bubble is removed via the edit list in §2.4.

2. **DB browser read-only?** → ✅ **Read-only.** No manual writes. When data
   changes (via the app's normal flows), the user sees a toast/snackbar about it;
   they cannot mutate rows from the bubble. (Future "danger zone" write toggle
   is explicitly deferred.)

3. **Log buffer size?** → ✅ **10,000 entries** (not 1000). Per user. At ~200
   bytes/entry average → ~2MB max RAM. Acceptable for a debug tool.

4. **Default visibility?** → ✅ **Visible by default** in debug builds. The user
   wants it always-on during the debugging phase. A Settings toggle can disable
   it when not needed, but the default is ON.

5. **How to toggle?** → ✅ **Settings toggle** ("Show debug bubble" in Settings →
   General, debug builds only), default ON.

6. **Screen opt-in scope?** → ✅ **Details, Browse, Watch, Downloads** provide
   `LocalDebugContext` initially (DB-7). Other screens show the generic tabs.
   More screens can opt in over time.

### Additional user decisions (D-163)
- **Position does NOT persist** — the bubble returns to its default (bottom-end)
  every time the app is reopened. See §3.2.
- **Squircle shape** (not a pure circle) for the bubble. See §3.1.
- **Panel width:** based on actual device width, takes up most of the display
  with a little padding on left/right (12dp). See §4.1.
- **Panel max height: 75%** of screen height (not 80%). See §4.2.
- **Expand direction by bubble position:** top→down, bottom→up, left→right,
  right→left. Always fully visible. See §4.1.
- **Tabs are flexible** — the 5 default tabs are the baseline; per-screen tabs
  can vary. Adding/removing tabs later is low-cost. See §4.2.
- **Data fetched when the panel opens** (not auto-polled). See §4.2.
- **Future: automated testing** — the bubble may eventually have a "testing"
  option that auto-tests the app and produces a report (download/share). Not
  this phase — kept in mind for the future.

---

## 10. WatchScreen carve-out + rotation + IME (D-162 C5/I6)

The Watch screen (player) has a special carve-out per CORE_RULES §7 / ADR-025.
The debug bubble must respect this:

### 10.1 WatchScreen auto-hide
- The Watch screen uses `SurfaceView` for mpv + manages immersive mode itself
  (`WindowInsetsControllerCompat.hide(systemBars)`, `setDecorFitsSystemWindows(false)`,
  `requestedOrientation = SENSOR_LANDSCAPE` in fullscreen).
- **Auto-hide the bubble when Watch enters fullscreen playback.** The bubble
  reads a `LocalWatchFullscreen` CompositionLocal (provided by WatchScreen) —
  when `true`, the bubble animates out (fade + scale to 0). When the user exits
  fullscreen (taps to show controls), the bubble fades back in.
- Alternatively (simpler): a `debugBubbleAutoHideOnWatch` preference (default
  `true`). When on, the bubble is hidden entirely on the Watch screen. This is
  the safest option — avoids any gesture/overlay conflict with the player.
- **Recommendation:** start with the preference approach (hide on Watch) for
  DB-1. The auto-hide-on-fullscreen approach can be refined in DB-7 when the
  Watch screen opt-in is added.

### 10.2 Rotation re-clamping (I6)
- `MainActivity` declares `configChanges="orientation|screenSize|…"` — the
  Activity is NOT recreated on rotation. But `LocalConfiguration` DOES change →
  Compose recomposes.
- The bubble's persisted px offset from portrait is wrong in landscape (the
  bubble ends up off-screen). **Fix:** re-clamp the offset in a
  `LaunchedEffect(LocalConfiguration.current.orientation)` — when the orientation
  changes, clamp the offset to the new screen bounds + animate to the clamped
  position.

### 10.3 IME (keyboard) handling (I6)
- The Database and Console tabs have text fields (search, filter). With
  `enableEdgeToEdge`, opening the keyboard insets the visible area.
- **Fix:** the panel uses `Modifier.imePadding()` so it rises above the IME.
  Alternatively, anchor the panel above the IME inset. The bubble itself (when
  collapsed) also uses `imePadding` so it isn't covered by the keyboard.

### 10.4 SurfaceView overlay confirmation
- Compose overlays DO render above `SurfaceView` by default (the surface sits
  below the window's main surface). So the bubble visually appears on top of
  the player — confirmed. But per §10.1, we hide it on Watch anyway to avoid
  gesture conflicts (the player's swipe-to-seek, pinch, double-tap zones).

---

## 11. Sub-agent review summary (D-162)

The plan was reviewed by a sub-agent (general-purpose). The main agent
critically evaluated each finding — all CRITICAL issues were verified as real
and incorporated above. Summary:

### CRITICAL (all verified real + fixed)
- **C1 — CompositionLocal siblings:** the bubble is a sibling of screen content
  in AppRoot's Box; a screen's `CompositionLocalProvider` doesn't reach siblings.
  Fixed: hoist `MutableState<DebugContext?>` to AppRoot + two CompositionLocals
  (reader + writer). See §5.1.
- **C2 — Logger can't reference DebugLogBuffer:** `:core:common` is always on
  classpath; `:feature:debug-bubble` is debug-only. Fixed: `LogAppender`
  interface in `:core:debug-api`; `Logger` holds the interface; wiring in
  `:app/src/debug`. See §5.3.
- **C3 — Koin module can't be imported in `:app/src/main`:** same root cause as
  C2. Fixed: debug-only source set (`:app/src/debug/java/…/DebugInit.kt`).
- **C4 — Feature modules can't import from debugImplementation:** screens opting
  in would break release builds. Fixed: split into `:core:debug-api` (always
  available) + `:feature:debug-bubble` (debug-only). See §2.1.
- **C5 — WatchScreen carve-out unaddressed:** player immersive mode + gesture
  zones + rotation. Fixed: auto-hide on Watch + rotation re-clamp. See §10.

### IMPORTANT (all verified real + fixed)
- **I1 — Network interceptor placement:** no NetworkModule in `:app`; multiple
  OkHttpClients; extension traffic via Injekt bypasses Koin. Fixed: wrap in
  `DebugInit.kt`; honestly disclose the extension-traffic limitation. See §5.4.
- **I2 — `database.sqlDriver` is private:** SQLDelight doesn't expose it. Fixed:
  inject `SqlDriver` directly via Koin. See §5.2.
- **I3 — SQL injection in search:** string interpolation is unsafe even
  read-only. Fixed: bound parameters + column validation. See §5.2.
- **I4 — BLOB columns:** `getString()` on a BLOB returns garbage. Fixed:
  detect column type via `PRAGMA table_info`, render BLOBs as `<BLOB: N bytes>`.
  See §5.2.
- **I5 — DebugContext cleanup on screen exit:** captured VMs leak if not
  cleared. Fixed: `DisposableEffect { onDispose { updateDebugContext(null) } }`
  in each participating screen. See §5.1.
- **I6 — Rotation + IME:** persisted px offset wrong in landscape; keyboard
  covers panel. Fixed: re-clamp on orientation change + `imePadding()`. See §10.
- **I7 — Animatable vs mutableStateOf inconsistency:** §3.2 used `snapTo` (needs
  Animatable), §6.1 declared mutableStateOf. Fixed: §6.1 now uses Animatable.
- **I8 — "Trivially removable" overstated:** after fixes, removal is ~5 edits,
  not 4 lines. Fixed: §2.4 now has an honest edit list.

### MINOR (noted, not blocking)
- M1 — `synchronized` lock contention on heavy logging (optimization lever).
- M2 — Throwable in ring buffer can blow 200KB budget (fixed: capped string).
- M3 — `statusBuckets: IntArray(6)` mismatch (fixed: 5 buckets, clarified).
- M4 — Backdrop-blur fallback unspecified (specify exact modifier chain in DB-6).
- M5 — AppRoot snippet was simplified (fixed: references actual line ~605).
- M6 — DebugBubbleVM data freshness on screen transitions (auto-invalidate
  Current Screen tab when `LocalDebugContext` changes).

### Main agent's own assessment
The sub-agent's review was thorough and technically accurate. Every CRITICAL
issue was a real compile-time or semantic blocker that would have surfaced
within the first hour of implementation. The fixes (module split, hoisted
state, LogAppender interface, SqlDriver injection, parameterized search, BLOB
handling, WatchScreen auto-hide, rotation/IME) are all correct and have been
incorporated into the plan. No sub-agent findings were dismissed without
verification; no false positives were found in the CRITICAL/IMPORTANT categories.

---

## 12. What this session delivers

This session is **planning only**. The deliverables are:
1. This plan doc (`PLAN.md`) — revised after sub-agent review (D-162).
2. A dashboard page (`/debug-bubble`) that presents the plan visually —
   architecture, UI mockup, tabs, removal strategy, phases, open questions.
3. Sub-agent review of the plan (§11) — with the main agent critically
   evaluating each finding.
4. CI verification that the `feature/debug-bubble` branch builds cleanly.

Implementation (DB-1 through DB-8) begins in the next session, after the user
reviews + approves this plan.
