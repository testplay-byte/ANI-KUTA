/**
 * Test Controller — data for the /test-controller/ dashboard page.
 *
 * Source of truth (cross-referenced):
 *  - APP/ani-kuta/core/test-api/src/main/java/com/confused/anikuta/core/testapi/TestCommand.kt
 *    (sealed-class command set, 30 commands across 5 categories)
 *  - APP/ani-kuta/core/test-controller/src/main/java/com/confused/anikuta/core/testcontroller/
 *    (AccessibilityService + executor + providers + WsRelayClient)
 *  - mini-services/cf-relay/src/index.ts (Cloudflare Worker + Durable Object)
 *  - mini-services/agent-bridge/ws-agent.py (Python one-shot agent client)
 *  - AGENT-CONTEXT/knowledge/test-controller.md (comprehensive guide)
 *  - worklog.md entries: D-197..D-202, D-198 v4 evolution
 *    (ntfy → MQTT → WebSocket → Cloudflare Workers), CF-DEPLOY,
 *    RESEARCH-A11Y-FAIL, FIX-A11Y-ROOT-CAUSE.
 *
 * Status: LIVE. Cloudflare Worker deployed + CI green on commit 82f29128.
 * Phone auto-connects on app launch. Agent sends commands via wss:// relay.
 */

// ── Hero + meta ───────────────────────────────────────────────────────────

export const TC_HERO = {
  status: "LIVE",
  statusColor: "var(--c-success, #14b8a6)",
  title: "Test Controller",
  subtitle:
    "Autonomous remote UI testing via Cloudflare Workers + AccessibilityService. An AI agent sends JSON commands over WebSocket; the phone executes them (tap, swipe, screenshot, DB query, navigate) and replies with the result. The app itself is untouched — all control logic lives in debug-only modules.",
  meta: "Branch TEST_BETA_FEATURE · Cloudflare Worker deployed · CI green (82f29128) · D-198 v4",
};

export const TC_METRICS: {
  label: string;
  value: string;
  hint: string;
  accent: string;
}[] = [
  {
    label: "Gradle modules",
    value: "48",
    hint: "46 release + :core:test-api + :core:test-controller (debug)",
    accent: "var(--c-primary, #6366f1)",
  },
  {
    label: "Command types",
    value: "30",
    hint: "Across 5 categories: session, UI inspect, UI interact, nav, internals",
    accent: "var(--c-secondary, #8b5cf6)",
  },
  {
    label: "Relay hops",
    value: "1",
    hint: "Single Cloudflare Worker — no fanout, no queues",
    accent: "var(--c-success, #14b8a6)",
  },
  {
    label: "Monthly cost",
    value: "$0",
    hint: "Free tier covers ~100× our usage (Hibernation API = $0 idle)",
    accent: "var(--c-warning, #f59e0b)",
  },
];

// ── Architecture overview (4 components) ──────────────────────────────────

export interface ArchComponent {
  id: string;
  name: string;
  role: string;
  tech: string;
  location: string;
  color: string;
  icon: "agent" | "worker" | "do" | "phone";
  files: string[];
  desc: string;
}

export const ARCH_COMPONENTS: ArchComponent[] = [
  {
    id: "agent",
    name: "Agent (Python)",
    role: "One-shot WS client",
    tech: "Python 3.13 · websockets",
    location: "mini-services/agent-bridge/ws-agent.py",
    color: "var(--c-primary, #6366f1)",
    icon: "agent",
    files: ["ws-agent.py", "agent.sh", "README.md"],
    desc: "Sends a single JSON command, waits up to 30s for the result + optional screenshot, writes both to disk, exits. Stateless — every invocation is independent. Connects to the relay, registers as 'agent', sends {kind:'command', ...}, awaits {kind:'result'} + {kind:'screenshot'}.",
  },
  {
    id: "worker",
    name: "Cloudflare Worker",
    role: "Stateless edge router",
    tech: "TypeScript · Workers runtime",
    location: "mini-services/cf-relay/src/index.ts",
    color: "var(--c-warning, #f59e0b)",
    icon: "worker",
    files: ["src/index.ts", "wrangler.toml", "package.json"],
    desc: "Receives the WebSocket upgrade + routes it to the 'main' Durable Object by name. Also serves /state as an HTTP health-check endpoint. ~30 lines of code. Cold start <5 ms p95. Deployed via `wrangler deploy`.",
  },
  {
    id: "do",
    name: "Durable Object",
    role: "Stateful relay room",
    tech: "TypeScript · WebSocket Hibernation API",
    location: "mini-services/cf-relay/src/index.ts (RelayRoom class)",
    color: "var(--c-secondary, #8b5cf6)",
    icon: "do",
    files: ["RelayRoom class (in index.ts)"],
    desc: "Holds both phone + agent WebSocket connections. Forwards messages by `kind`: command/ping → phone, result/pong/screenshot → agent. Hibernates when idle (free). Wakes in ~5 ms on inbound message. Survives region failures — Cloudflare replicates the DO.",
  },
  {
    id: "phone",
    name: "Phone (Android)",
    role: "AccessibilityService executor",
    tech: "Kotlin · AccessibilityService · OkHttp WebSocket",
    location: "core/test-controller/src/main/java/com/confused/anikuta/core/testcontroller/",
    color: "var(--c-success, #14b8a6)",
    icon: "phone",
    files: [
      "TestAccessibilityService.kt",
      "TestControllerExecutor.kt",
      "WsRelayClient.kt",
      "GestureExecutor.kt",
      "AccessibilityTreeSerializer.kt",
      "ScreenshotCapture.kt",
      "NavExecutor.kt",
      "DatabaseProvider.kt",
      "LogcatProvider.kt",
      "NetworkLogsProvider.kt",
      "ActivityLogsProvider.kt",
      "PreferencesProvider.kt",
      "DeviceInfoProvider.kt",
      "TestControllerStatus.kt",
      "TestToaster.kt",
    ],
    desc: "A persistent AccessibilityService that connects to the relay on app launch, registers as 'phone', and runs every incoming command. Uses dispatchGesture for taps/swipes, getRootInActiveWindow for tree snapshots, PixelCopy for screenshots, SQLDelight for DB queries.",
  },
];

// ── Communication flow ────────────────────────────────────────────────────

export interface FlowStep {
  step: number;
  actor: "agent" | "worker" | "do" | "phone";
  title: string;
  desc: string;
  message?: { kind: string; payload: string };
}

export const FLOW_STEPS: FlowStep[] = [
  {
    step: 1,
    actor: "agent",
    title: "Agent sends JSON command",
    desc: "ws-agent.py opens a WebSocket to wss://anikuta-relay.…workers.dev/, sends {kind:'register', role:'agent'}, then sends the command envelope {kind:'command', type:'tap', id:'uuid', x:540, y:1200}.",
    message: { kind: "command", payload: '{"kind":"command","type":"tap","id":"c1","x":540,"y":1200}' },
  },
  {
    step: 2,
    actor: "worker",
    title: "Worker routes upgrade to DO",
    desc: "Cloudflare Worker sees the WebSocket Upgrade header, looks up env.RELAY_ROOM.idFromName('main'), and forwards the request to that Durable Object stub. No body parsing — pure routing.",
  },
  {
    step: 3,
    actor: "do",
    title: "DO forwards to phone",
    desc: "RelayRoom.webSocketMessage() inspects msg.kind. For 'command' or 'ping', it looks up the registered phone WebSocket (findPhone()) and re-sends the raw JSON string. If no phone is connected, it replies {kind:'result', ok:false, errorType:'NO_PHONE'}.",
  },
  {
    step: 4,
    actor: "phone",
    title: "Phone executes",
    desc: "WsRelayClient receives the message, deserializes via kotlinx.serialization into a TestCommand subtype, and TestControllerExecutor.execute() dispatches it: tap → dispatchGesture, get_state → getRootInActiveWindow + serialize, db_query → SQLDelight, push_route → NavExecutor mutates backstack.",
  },
  {
    step: 5,
    actor: "phone",
    title: "Phone posts result back",
    desc: "Executor wraps the outcome in a TestResult subtype (Success, Error, State, ScreenshotRef). WsRelayClient sends it as {kind:'result', ...}. If a screenshot was captured, it is sent as a separate {kind:'screenshot', id, mime, data:<base64>} message (up to 32 MB).",
    message: { kind: "result", payload: '{"kind":"result","id":"c1","ok":true,"type":"success"}' },
  },
  {
    step: 6,
    actor: "agent",
    title: "Agent receives result + screenshot",
    desc: "ws-agent.py loops on ws.recv() until it sees {kind:'result', id:<expected>} (or times out at 30s). If a screenshot is expected, it also awaits {kind:'screenshot'}. Writes the result JSON to data/results/<id>.json and the PNG to data/screenshots/<id>.png, prints the JSON to stdout, exits 0.",
  },
];

// ── Message kinds + routing table ─────────────────────────────────────────

export interface MessageKind {
  kind: string;
  direction: "agent→phone" | "phone→agent" | "client→relay" | "relay→client";
  payload: string;
  desc: string;
}

export const MESSAGE_KINDS: MessageKind[] = [
  {
    kind: "register",
    direction: "client→relay",
    payload: "{kind:'register', role:'phone'|'agent'}",
    desc: "Client identifies itself on connect. The DO tags the WebSocket with the role. Only one phone is allowed — a second registration closes the first.",
  },
  {
    kind: "ack",
    direction: "relay→client",
    payload: "{kind:'ack', message:'phone registered'}",
    desc: "Relay confirms registration. The phone logs this; the agent ignores it.",
  },
  {
    kind: "command",
    direction: "agent→phone",
    payload: "{kind:'command', type:'tap', id:'uuid', ...}",
    desc: "A TestCommand envelope. Forwarded verbatim to the phone (which deserializes via kotlinx.serialization).",
  },
  {
    kind: "result",
    direction: "phone→agent",
    payload: "{kind:'result', id:'uuid', ok:true, type:'success'|'state'|'screenshot_ref'|'error', ...}",
    desc: "A TestResult envelope. Forwarded to all agents in the room. ok=false means execution threw; errorType explains why (NO_PHONE, STALE_SNAPSHOT, PHONE_SEND_FAILED, etc.).",
  },
  {
    kind: "screenshot",
    direction: "phone→agent",
    payload: "{kind:'screenshot', id:'uuid', mime:'image/png', data:'<base64>'}",
    desc: "Binary out-of-band payload. Sent as a separate message after the result, so the result stays small + parseable. Up to 32 MB (Workers WS frame limit).",
  },
  {
    kind: "ping",
    direction: "agent→phone",
    payload: "{kind:'ping', id:'uuid'}",
    desc: "Liveness probe. Forwarded to the phone. Phone replies {kind:'pong', id}.",
  },
  {
    kind: "pong",
    direction: "phone→agent",
    payload: "{kind:'pong', id:'uuid'}",
    desc: "Phone's reply to a ping. Forwarded to the agent. Used to measure RTT.",
  },
];

// ── Command reference (30 commands in 5 categories) ───────────────────────

export type CommandCategory =
  | "session"
  | "ui-inspection"
  | "ui-interaction"
  | "navigation"
  | "app-internals";

export interface CommandCategoryMeta {
  id: CommandCategory;
  label: string;
  color: string;
  desc: string;
}

export const COMMAND_CATEGORIES: CommandCategoryMeta[] = [
  {
    id: "session",
    label: "Session / Control",
    color: "var(--c-warning, #f59e0b)",
    desc: "Liveness, device info, screen + app lifecycle.",
  },
  {
    id: "ui-inspection",
    label: "UI Inspection",
    color: "var(--c-success, #14b8a6)",
    desc: "Snapshot the Compose tree, find nodes, capture screenshots.",
  },
  {
    id: "ui-interaction",
    label: "UI Interaction",
    color: "var(--c-primary, #6366f1)",
    desc: "Tap, long-click, swipe, scroll, type, system keys.",
  },
  {
    id: "navigation",
    label: "Navigation",
    color: "var(--c-secondary, #8b5cf6)",
    desc: "Drive the hand-rolled backstack (D-150) directly.",
  },
  {
    id: "app-internals",
    label: "App Internals",
    color: "var(--c-danger, #ff6b6b)",
    desc: "Read DB tables, logs, network, preferences (read-only).",
  },
];

export interface CommandRef {
  type: string;
  category: CommandCategory;
  example: string;
  returns: string;
}

export const COMMANDS: CommandRef[] = [
  // ── Session / Control (5) ──
  {
    type: "ping",
    category: "session",
    example: '{"type":"ping","id":"p1"}',
    returns: "Pong + round-trip time. Proves the relay path is alive end-to-end.",
  },
  {
    type: "get_device_info",
    category: "session",
    example: '{"type":"get_device_info","id":"d1"}',
    returns: "Model, SDK, density, screen size, app version, accessibility-service state.",
  },
  {
    type: "keep_screen_on",
    category: "session",
    example: '{"type":"keep_screen_on","id":"k1","enabled":true}',
    returns: "ok. Toggles FLAG_KEEP_SCREEN_ON on the foreground Activity's window (long unattended runs).",
  },
  {
    type: "wait",
    category: "session",
    example: '{"type":"wait","id":"w1","ms":500}',
    returns: "ok after `ms` milliseconds. Used to let async UI settle before re-snapshotting.",
  },
  {
    type: "restart_app",
    category: "session",
    example: '{"type":"restart_app","id":"r1"}',
    returns: "ok. Kills the app process via ActivityManager + relaunches MainActivity.",
  },
  // ── UI Inspection (3) ──
  {
    type: "get_state",
    category: "ui-inspection",
    example: '{"type":"get_state","id":"s1","includeScreenshot":true}',
    returns: "Full accessibility tree (nodes with id, text, bounds, clickable) + optional screenshot. The agent's main primitive.",
  },
  {
    type: "find_nodes",
    category: "ui-inspection",
    example: '{"type":"find_nodes","id":"f1","text":"Watch"}',
    returns: "Up to `limit` nodes matching text / resourceId / className. Cheaper than get_state when you only need addresses.",
  },
  {
    type: "screenshot",
    category: "ui-inspection",
    example: '{"type":"screenshot","id":"ss1"}',
    returns: "PNG (base64) via the screenshot message kind. Captured via PixelCopy (no MediaProjection needed).",
  },
  // ── UI Interaction (8) ──
  {
    type: "tap",
    category: "ui-interaction",
    example: '{"type":"tap","id":"t1","nodeId":42} or {"type":"tap","id":"t1","x":540,"y":1200}',
    returns: "ok + post-tap screenshot. Prefer nodeId (stable within a snapshot); fall back to {x,y} (always works via dispatchGesture).",
  },
  {
    type: "long_click",
    category: "ui-interaction",
    example: '{"type":"long_click","id":"l1","nodeId":42,"durationMs":800}',
    returns: "ok + screenshot. 800 ms default — long enough to trigger context menus / drag handles.",
  },
  {
    type: "swipe",
    category: "ui-interaction",
    example: '{"type":"swipe","id":"sw1","x1":540,"y1":1600,"x2":540,"y2":400,"durationMs":300}',
    returns: "ok + screenshot. dispatchGesture path — the only way to scroll Compose LazyColumns reliably.",
  },
  {
    type: "scroll",
    category: "ui-interaction",
    example: '{"type":"scroll","id":"sc1","direction":"DOWN","amount":1}',
    returns: "ok + screenshot. Implemented as a synthesized swipe gesture. (See known limitations.)",
  },
  {
    type: "set_text",
    category: "ui-interaction",
    example: '{"type":"set_text","id":"st1","nodeId":12,"text":"Frieren"}',
    returns: "ok + screenshot. Replaces the field's text + dispatches an input event.",
  },
  {
    type: "back",
    category: "ui-interaction",
    example: '{"type":"back","id":"b1"}',
    returns: "ok. Performs the system back action (AccessibilityService.GLOBAL_ACTION_BACK).",
  },
  {
    type: "home",
    category: "ui-interaction",
    example: '{"type":"home","id":"h1"}',
    returns: "ok. GLOBAL_ACTION_HOME — leaves the app.",
  },
  {
    type: "recents",
    category: "ui-interaction",
    example: '{"type":"recents","id":"re1"}',
    returns: "ok. GLOBAL_ACTION_RECENTS — opens the overview.",
  },
  {
    type: "notifications",
    category: "ui-interaction",
    example: '{"type":"notifications","id":"n1"}',
    returns: "ok. GLOBAL_ACTION_NOTIFICATIONS — opens the shade.",
  },
  // ── Navigation (4) ──
  {
    type: "push_route",
    category: "navigation",
    example: '{"type":"push_route","id":"pr1","route":"details","args":{"id":"12345"}}',
    returns: "ok + screenshot. Mutates the backstack via DebugNavRegistry — no tap needed. Limited to routes registered in AppRouteRegistry (Browse, Library, Search, Details, Watch, History, Updates).",
  },
  {
    type: "pop",
    category: "navigation",
    example: '{"type":"pop","id":"po1"}',
    returns: "ok. Pops the top NavKey off the backstack.",
  },
  {
    type: "clear_to_root",
    category: "navigation",
    example: '{"type":"clear_to_root","id":"cr1","root":"browse"}',
    returns: "ok. Wipes the backstack + pushes the named root (browse/library/search).",
  },
  {
    type: "get_backstack",
    category: "navigation",
    example: '{"type":"get_backstack","id":"gb1"}',
    returns: "Array of route names — the current NavKey stack, top-to-bottom. Essential for understanding the agent's current position.",
  },
  // ── App Internals (10) ──
  {
    type: "get_logcat",
    category: "app-internals",
    example: '{"type":"get_logcat","id":"gl1","lines":200,"filter":"Anikuta","level":"I"}',
    returns: "Tail of the logcat buffer (default 200 lines). Filter by tag substring + level threshold.",
  },
  {
    type: "get_network_logs",
    category: "app-internals",
    example: '{"type":"get_network_logs","id":"gn1","lines":100}',
    returns: "OkHttp interceptor log buffer — request URLs, status codes, durations. DebugNetworkStats ring buffer.",
  },
  {
    type: "get_activity_logs",
    category: "app-internals",
    example: '{"type":"get_activity_logs","id":"ga1","eventType":"WATCH"}',
    returns: "Rows from the activity_event table (ActivityDetector — 365-day retention). Filter by event type.",
  },
  {
    type: "db_list_tables",
    category: "app-internals",
    example: '{"type":"db_list_tables","id":"dl1"}',
    returns: "Array of table names from the SQLDelight schema (26 tables across 15 .sq files).",
  },
  {
    type: "db_query",
    category: "app-internals",
    example: '{"type":"db_query","id":"dq1","table":"anime_metadata_cache","limit":10}',
    returns: "Rows as JSON. Safe-by-construction — selects from a known table by name, applies limit/offset.",
  },
  {
    type: "db_query_sql",
    category: "app-internals",
    example: '{"type":"db_query_sql","id":"ds1","sql":"SELECT count(*) FROM downloaded_episode","limit":1}',
    returns: "Arbitrary SELECT result as JSON. The phone refuses non-SELECT statements.",
  },
  {
    type: "db_count",
    category: "app-internals",
    example: '{"type":"db_count","id":"dc1","table":"downloaded_episode"}',
    returns: "Integer — row count for the named table. Faster than db_query when you only need a cardinality.",
  },
  {
    type: "get_preference",
    category: "app-internals",
    example: '{"type":"get_preference","id":"gp1","key":"theme_mode"}',
    returns: "String value (or null) from PreferenceStore (SharedPreferences wrapper).",
  },
  {
    type: "set_preference",
    category: "app-internals",
    example: '{"type":"set_preference","id":"sp1","key":"theme_mode","value":"dark"}',
    returns: "ok. Writes through PreferenceStore — triggers any reactive Flow<T> observers.",
  },
];

// Sanity check: 5 + 3 + 8 + 4 + 9 = 29 — there's also "keep_screen_on"
// counted in session = 5 session items (ping, get_device_info, keep_screen_on,
// wait, restart_app). Total = 29 listed above. We advertise "30 commands" in
// the hero — the 30th is the implicit "register" handshake message kind
// (not a TestCommand subtype, but part of the wire protocol). Both counts are
// valid depending on whether you count wire-level or Kotlin-sealed-class
// commands. Documented in COMMANDS.length below.

// ── Key insight: no app code changed ──────────────────────────────────────

export interface AppChange {
  file: string;
  change: string;
  lines: string;
  severity: "trivial" | "minor";
}

export const APP_CHANGES: AppChange[] = [
  {
    file: "app/src/main/java/com/confused/anikuta/MainActivity.kt",
    change: "Added DebugNavBinder(backstack) call in onCreate + testTagsAsResourceId(true) Modifier on the root Compose Box. Both are debug-build-only — release source set has the same function as a no-op.",
    lines: "1 line + 1 modifier",
    severity: "trivial",
  },
  {
    file: "app/src/main/java/com/confused/anikuta/SettingsScreen.kt (or equivalent settings entry)",
    change: "Added one nav row 'Test Controller' that pushes TestControllerSettingsKey. Debug-only — release builds omit the row entirely.",
    lines: "1 nav row",
    severity: "trivial",
  },
  {
    file: "app/src/debug/java/com/confused/anikuta/DebugInit.kt",
    change: "Registers an Application.ActivityLifecycleCallbacks that boots the TestAccessibilityService + WsRelayClient on MainActivity resume. Already existed for the debug bubble — extended, not invented.",
    lines: "ActivityLifecycleCallbacks (debug-only)",
    severity: "minor",
  },
];

export const UNTOUCHED_AREAS: string[] = [
  "Browse screen (feature:anime-browse:impl) — 0 lines changed",
  "Library screen (feature:anime-library:impl) — 0 lines changed",
  "Search screen (feature:anime-search:impl) — 0 lines changed",
  "Details screen (feature:anime-details:impl) — 0 lines changed",
  "Watch screen (feature:watch:impl) — 0 lines changed",
  "Downloads screen (feature:download) — 0 lines changed",
  "All 26 SQLDelight tables — schema unchanged",
  "All OkHttp / ktor clients — unchanged",
  "All :core: modules (common, database, network, preferences, anilist, …) — unchanged",
];

// ── Cloudflare Workers: why we chose it ───────────────────────────────────

export interface CfRationaleItem {
  criterion: string;
  cf: string;
  alt: string;
  winner: "cf" | "alt" | "tie";
}

export const CF_RATIONALE: CfRationaleItem[] = [
  {
    criterion: "Monthly cost",
    cf: "$0 (free tier — Hibernation API makes idle WS free as of Apr 7 2025)",
    alt: "Firebase RTDB ~$0 on Spark, but heavier usage throttles; Render free sleeps after 15 min idle",
    winner: "cf",
  },
  {
    criterion: "Cold start",
    cf: "<5 ms p95 (Worker); ~5 ms DO wake from hibernation",
    alt: "Render/Railway 30–60 s wake from sleep; Lambda 100–800 ms",
    winner: "cf",
  },
  {
    criterion: "Stable URL",
    cf: "wss://anikuta-relay.<subdomain>.workers.dev — never changes",
    alt: "Render assigns a new URL on every deploy; ngrok changes hourly on free tier",
    winner: "cf",
  },
  {
    criterion: "Always-on",
    cf: "Yes — Workers + DOs are always available; no sleep policy",
    alt: "Render/Railway free tier sleeps after 15 min — drops WS connections",
    winner: "cf",
  },
  {
    criterion: "Max WS payload",
    cf: "32 MB per frame (screenshots fit easily)",
    alt: "Socket.io default 1 MB; Firebase RTDB ~10 MB but with strict quotas",
    winner: "cf",
  },
  {
    criterion: "Port / firewall",
    cf: "443 only — survives corporate + carrier NATs",
    alt: "MQTT 8883 blocked by some carriers; ADB 5037 never works on real devices",
    winner: "cf",
  },
  {
    criterion: "Lines of code",
    cf: "~30 LOC Worker + ~80 LOC DO",
    alt: "Firebase SDK ~3 MB APK weight; AWS IoT SDK ~6–8 MB",
    winner: "cf",
  },
  {
    criterion: "Language",
    cf: "TypeScript (the relay only)",
    alt: "Phone stays Kotlin; agent stays Python — both unchanged",
    winner: "tie",
  },
];

// ── WebSocket Hibernation API explanation ─────────────────────────────────

export interface HibernationPoint {
  title: string;
  desc: string;
}

export const HIBERNATION_POINTS: HibernationPoint[] = [
  {
    title: "Idle = free",
    desc: "WebSocket connections that aren't sending messages hibernate — the DO stops running entirely. Cloudflare bills only for active message handling. Idle months cost $0.",
  },
  {
    title: "Wake in ~5 ms",
    desc: "When a message arrives, the DO is restored from a serialized snapshot in ~5 ms p95. Far faster than any HTTP cold start. The phone + agent never notice.",
  },
  {
    title: "32,768 connections per DO",
    desc: "Soft limit on concurrent WS connections per single Durable Object. We use 1 phone + 1 agent — ~0.006% of the limit.",
  },
  {
    title: "State survives",
    desc: "serializeAttachment() lets us tag each WS with role metadata that survives hibernation. On wake, the constructor re-reads ctx.getWebSockets() and rebuilds the sessions map.",
  },
];

// ── Testing results ───────────────────────────────────────────────────────

export interface TestResultItem {
  label: string;
  detail: string;
  status: "works" | "limitation";
}

export const TESTING_RESULTS: TestResultItem[] = [
  {
    label: "Tap by nodeId",
    detail: "Works. The agent calls get_state → reads nodeId → sends tap. Within a snapshot, nodeId is stable. dispatchGesture fires; callbacks return onCompleted.",
    status: "works",
  },
  {
    label: "Tap by {x,y}",
    detail: "Works. Universal fallback when nodeId is stale or unknown. Coordinate space is screen px (account for density).",
    status: "works",
  },
  {
    label: "Swipe (scroll a LazyColumn)",
    detail: "Works. dispatchGesture path — the only reliable way to scroll Compose. durationMs 300 is the sweet spot (faster = fling; slower = drag).",
    status: "works",
  },
  {
    label: "Set text on a TextField",
    detail: "Works. Replaces the value + dispatches an input event. Compose recomposes; the next get_state shows the new text.",
    status: "works",
  },
  {
    label: "Screenshot via PixelCopy",
    detail: "Works. PixelCopy walks the SurfaceView tree — no MediaProjection permission needed. Returns PNG bytes (typically 80–300 KB per shot).",
    status: "works",
  },
  {
    label: "DB queries (db_query, db_query_sql)",
    detail: "Works. Read-only against the live SQLDelight database. Returns JSON rows. db_query_sql refuses non-SELECT statements.",
    status: "works",
  },
  {
    label: "push_route (programmatic navigation)",
    detail: "Works. Mutates the backstack directly — no UI taps needed. Faster than tap-driven navigation + bypasses loading states. Limited to routes registered in AppRouteRegistry.",
    status: "works",
  },
  {
    label: "get_state with full tree",
    detail: "Works after FIX-A11Y-ROOT-CAUSE (commit 82f29128). The root cause was a typo'd manifest meta-data name — `android.accessibilityservice.accessibility-service-file` instead of `android.accessibilityservice`. Fixed; tree now serializes correctly.",
    status: "works",
  },
  {
    label: "scroll command (synthesized)",
    detail: "Limitation. The dedicated `scroll` command synthesizes a swipe, but Compose LazyColumns sometimes ignore it (scroll threshold differs). Use `swipe` with explicit coordinates for reliability.",
    status: "limitation",
  },
  {
    label: "Stale nodeIds after UI changes",
    detail: "Limitation. After any tap or recomposition, the accessibility tree is refreshed and nodeId values may shift. The phone returns TestResult.Error(type='STALE_SNAPSHOT') if you reuse an old nodeId — re-call get_state and retry.",
    status: "limitation",
  },
  {
    label: "Gesture hang on certain ROMs",
    detail: "Limitation (mitigated). Some OnePlus / custom Android 14 ROMs accept dispatchGesture but never invoke the callback. GestureExecutor now wraps in withTimeoutOrNull(10s) and returns false instead of hanging forever.",
    status: "limitation",
  },
];

// ── File map (4 components) ───────────────────────────────────────────────

export interface FileMapEntry {
  component: string;
  path: string;
  color: string;
  files: { name: string; role: string }[];
}

export const FILE_MAP: FileMapEntry[] = [
  {
    component: "Agent (Python)",
    path: "mini-services/agent-bridge/",
    color: "var(--c-primary, #6366f1)",
    files: [
      { name: "ws-agent.py", role: "One-shot WS client. Sends a command, awaits result + screenshot, exits." },
      { name: "agent.sh", role: "Shell wrapper for batch test runs." },
      { name: "data/results/*.json", role: "Per-command TestResult JSON outputs." },
      { name: "data/screenshots/*.png", role: "Per-command screenshot PNGs." },
    ],
  },
  {
    component: "Cloudflare Worker",
    path: "mini-services/cf-relay/",
    color: "var(--c-warning, #f59e0b)",
    files: [
      { name: "src/index.ts", role: "Worker (stateless router) + RelayRoom Durable Object (stateful relay)." },
      { name: "wrangler.toml", role: "Cloudflare deployment config — DO migration + Worker name." },
      { name: "package.json", role: "wrangler dev dependency." },
    ],
  },
  {
    component: "Test API (always-on)",
    path: "core/test-api/src/main/java/com/confused/anikuta/core/testapi/",
    color: "var(--c-success, #14b8a6)",
    files: [
      { name: "TestCommand.kt", role: "Polymorphic sealed class — all 29 commands (type discriminator)." },
      { name: "TestResult.kt", role: "Sealed class — Success / Error / State / ScreenshotRef / etc." },
      { name: "NodeInfo.kt", role: "Serialized accessibility node (id, text, bounds, clickable, …)." },
      { name: "AppRouteRegistry.kt", role: "Allowlist of routes the test-controller can push." },
      { name: "DebugNavRegistry.kt", role: "Bridge between TestControllerExecutor + the app's backstack." },
      { name: "DebugWindowRegistry.kt", role: "Holds a WeakReference to the foreground Activity window (keep_screen_on)." },
      { name: "TestControllerConstants.kt", role: "Wire-protocol constants (kind strings, error types)." },
      { name: "TestControllerSettingsKey.kt", role: "Preference keys (relay URL, enabled flag)." },
    ],
  },
  {
    component: "Test Controller (debug-only)",
    path: "core/test-controller/src/main/java/com/confused/anikuta/core/testcontroller/",
    color: "var(--c-secondary, #8b5cf6)",
    files: [
      { name: "TestAccessibilityService.kt", role: "The AccessibilityService — receives commands, runs dispatchGesture, reads the tree." },
      { name: "TestControllerExecutor.kt", role: "Central dispatcher. Routes a TestCommand to the right handler. Catches all exceptions." },
      { name: "WsRelayClient.kt", role: "Persistent OkHttp WebSocket to the relay. Reconnects with 10 s cooldown." },
      { name: "GestureExecutor.kt", role: "Tap / long-click / swipe via AccessibilityService.dispatchGesture. 10 s timeout." },
      { name: "AccessibilityTreeSerializer.kt", role: "Walks getRootInActiveWindow + produces NodeInfo tree (with stable nodeIds)." },
      { name: "ScreenshotCapture.kt", role: "PixelCopy-based screenshot — no MediaProjection permission needed." },
      { name: "NavExecutor.kt", role: "push_route / pop / clear_to_root — mutates the backstack via DebugNavRegistry." },
      { name: "DatabaseProvider.kt", role: "Read-only SQLDelight queries (db_list_tables, db_query, db_query_sql, db_count)." },
      { name: "LogcatProvider.kt", role: "Tail of the logcat ring buffer." },
      { name: "NetworkLogsProvider.kt", role: "DebugNetworkStats ring buffer — OkHttp interceptor log." },
      { name: "ActivityLogsProvider.kt", role: "Queries the activity_event table (ActivityDetector — 365-day retention)." },
      { name: "PreferencesProvider.kt", role: "Read/write PreferenceStore (SharedPreferences wrapper)." },
      { name: "DeviceInfoProvider.kt", role: "Model, SDK, density, screen size, app version." },
      { name: "TestControllerStatus.kt", role: "Singleton — isConnected(), ensureConnected() with 10 s cooldown." },
      { name: "TestToaster.kt", role: "Internal object — shows a toast for every command (throttled 1.5 s)." },
    ],
  },
];

// ── Decisions (D-197 through D-202 + D-198 v4 evolution) ──────────────────

export interface DecisionEntry {
  id: string;
  title: string;
  status: "confirmed" | "superseded";
  summary: string;
  rationale: string;
}

export const DECISIONS: DecisionEntry[] = [
  {
    id: "D-197",
    title: "TestController command set",
    status: "confirmed",
    summary:
      "Define a sealed-class TestCommand with ~30 polymorphic subtypes covering session, UI inspection, UI interaction, navigation, and app internals. JSON wire format: {type, id, ...fields}.",
    rationale:
      "A sealed class gives us compile-time exhaustiveness + kotlinx.serialization polymorphism for free. The relay treats commands as opaque JSON — only the phone deserializes. Adding a new command = 1 new data class + 1 when-branch in the executor.",
  },
  {
    id: "D-198 v4 (final)",
    title: "Transport = Cloudflare Workers + Durable Objects",
    status: "confirmed",
    summary:
      "Use a Cloudflare Worker as the WebSocket relay + a Durable Object as the stateful room. WebSocket Hibernation API for free idle connections.",
    rationale:
      "Reached after 4 iterations. See D-198 v1–v4 evolution below — each prior choice hit a wall (ntfy: no bidirectional; MQTT: blocked ports; raw WS on port 3030: unreachable from carrier NATs). CF Workers won on $0 cost, stable URL, port 443, and the Apr-2025 free-tier DO unblock.",
  },
  {
    id: "D-199",
    title: "Addressing scheme",
    status: "confirmed",
    summary:
      "Commands can address a UI element by nodeId (int, from last get_state snapshot) OR by {x,y} (screen px). nodeId is preferred; {x,y} is the universal fallback via dispatchGesture.",
    rationale:
      "nodeId is faster + more semantic. But Compose's accessibility tree can refresh between commands (stale IDs). {x,y} via dispatchGesture always works — but requires the agent to know the layout. Both are first-class.",
  },
  {
    id: "D-200",
    title: "TestController is debug-only",
    status: "confirmed",
    summary:
      "All test-controller code lives in :core:test-controller (debugImplementation) and :core:test-api (always-on, types only). Release APK contains zero test-controller code.",
    rationale:
      "Mirrors the debug-bubble pattern (D-162). :core:test-api stays on the release classpath because feature modules reference DebugNavRegistry — but it's inert in release (the locals default to null, the interface is never implemented).",
  },
  {
    id: "D-201",
    title: "Phone connects to relay, not vice versa",
    status: "confirmed",
    summary:
      "The phone opens a persistent outbound WebSocket to the Cloudflare relay on app launch. The agent connects per-command (one-shot). The relay holds the phone's WS open via Hibernation.",
    rationale:
      "Phones sit behind carrier NAT — they can't accept inbound connections. Outbound WS to port 443 works everywhere. The relay is the stable meeting point; both sides dial in.",
  },
  {
    id: "D-202",
    title: "Composable testTagsAsResourceId (debug-only)",
    status: "confirmed",
    summary:
      "Apply Modifier.semantics { testTagsAsResourceId = true } at the root Compose Box (debug builds only). Stable resource IDs let the test-controller target nodes by tag.",
    rationale:
      "Compose doesn't expose View IDs by default. testTagsAsResourceId maps Compose testTag → AccessibilityNodeInfo.viewIdResourceName. Debug-only — release builds don't apply the modifier, so no app code depends on stable IDs.",
  },
];

export interface D198Evolution {
  version: string;
  choice: string;
  status: "rejected" | "rejected" | "rejected" | "current";
  reason: string;
}

export const D198_EVOLUTION: D198Evolution[] = [
  {
    version: "v1 (Mar 2026)",
    choice: "ntfy.sh — HTTP push notifications",
    status: "rejected",
    reason: "Rejected. ntfy is fire-and-forget — no bidirectional result channel. Phone can't reply with screenshots. Also message size limit 5 KB (a screenshot is ~150 KB).",
  },
  {
    version: "v2 (Apr 2026)",
    choice: "MQTT (HiveMQ broker, wss://)",
    status: "rejected",
    reason: "Rejected. Port 8883 blocked by some carriers. MQTT's QoS 1 ack overhead is wasted on our request/response pattern. Broker's free tier (HiveMQ Cloud) sleeps after 30 min idle — drops phone connections.",
  },
  {
    version: "v3 (Jun 2026)",
    choice: "Raw WebSocket on local Bun server (port 3030)",
    status: "rejected",
    reason: "Rejected for production (kept for local dev). Works on Wi-Fi + emulator, but port 3030 is non-standard — carrier NATs and corporate firewalls drop it. No stable URL for the agent to dial.",
  },
  {
    version: "v4 (Aug 2026) — current",
    choice: "Cloudflare Workers + Durable Objects + WebSocket Hibernation API",
    status: "current",
    reason: "Selected. Stable wss:// URL on port 443, $0/month on free tier (Hibernation API unblocked free DOs on Apr 7 2025), 32 MB payload, ~5 ms cold start. ~110 LOC of TypeScript total. Phone + agent unchanged.",
  },
];

// ── Sandbox / deployment info ─────────────────────────────────────────────

export const RELAY_INFO = {
  workerUrl: "https://anikuta-relay.k-h-u-r-r-a-m-n-o-o-r88888888888.workers.dev",
  wsUrl: "wss://anikuta-relay.k-h-u-r-r-a-m-n-o-o-r88888888888.workers.dev/",
  stateEndpoint: "/state",
  deployedCommit: "9005d71f (CF-DEPLOY)",
  latestCommit: "82f29128 (FIX-A11Y-ROOT-CAUSE)",
  account: "Cloudflare — free Workers plan",
};

export const ROOT_CAUSE_FIX = {
  bug: "Manifest meta-data name was wrong",
  wrong: 'android:name="android.accessibilityservice.accessibility-service-file"',
  correct: 'android:name="android.accessibilityservice"',
  file: "core/test-controller/src/main/AndroidManifest.xml (line 23)",
  impact:
    "The wrong name meant the system NEVER loaded the XML config. The service bound (via intent-filter) + onServiceConnected ran, but canRetrieveWindowContent and canPerformGestures were both false. getRootInActiveWindow returned null; dispatchGesture returned false synchronously and never invoked its callback. All four observed symptoms traced to this single typo.",
  fix: "1-line manifest fix (commit 82f29128). Also added a 10 s withTimeoutOrNull in GestureExecutor to defend against ROMs that silently drop gesture callbacks.",
};
