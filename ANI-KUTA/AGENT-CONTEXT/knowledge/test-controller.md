# Test Controller — Autonomous Remote Testing System

> **Status:** ✅ Operational. CI green on `TEST_BETA_FEATURE` branch.
> **Transport:** WebSocket relay (D-198 v3) — sandbox-hosted, persistent inside the Next.js dev server.
> **Last updated:** D-198 v3.1 session.

---

## TL;DR — Quick Start for a New AI Agent

```bash
# 1. Ensure the relay is running (hit the API endpoint to start it):
curl http://localhost:3000/api/test-relay
# → {"relay":"running","phoneConnected":true/false,...}

# 2. If phoneConnected is false, ask the user to open the app on their phone
#    (the app auto-connects to the relay on launch).

# 3. Send commands via the agent helper:
cd /home/z/my-project/mini-services/agent-bridge
./agent.sh ping                     # smoke test — returns device info
./agent.sh get_state                # UI tree + screenshot
./agent.sh push_route library       # navigate to Library page
./agent.sh db_tables                # list all SQLDelight tables
./agent.sh send '{"type":"db_query","id":"q1","table":"content","limit":10}'

# 4. Read results:
cat data/results/<commandId>.json   # the TestResult JSON
# Screenshot at: data/screenshots/<commandId>.png (analyze with VLM skill)
```

---

## Architecture Overview

```
┌─ AGENT (sandbox, Python) ───────────────┐  ┌─ RELAY (Next.js dev server, :3030) ──┐  ┌─ PHONE (ANI-KUTA debug build) ──┐
│                                          │  │                                       │  │                                  │
│  ws-agent.py (one-shot per command)     │  │  Next.js API route:                   │  │  TestAccessibilityService        │
│   │                                      │  │  /api/test-relay/route.ts             │  │   ├─ onServiceConnected()         │
│   │  ws://localhost:3030                 │  │  (starts ws npm package WSS on :3030) │  │   │   ├─ resolves Koin deps       │
│   ├─ connect + register as "agent"      │  │                                       │  │   │   ├─ builds executor          │
│   ├─ send {kind:"command", ...}          │  │  Phone (persistent WS):               │  │   │   └─ starts WsRelayClient    │
│   │  ──────────────────────────────►     │  │  ◄── wss://PREVIEW_URL/?XTransformPort=3030 ── connect + register as "phone"
│   │                                      │  │                                       │  │                                  │
│   │  (wait for result, ≤30s)             │  │  Relay routes by "kind" field:        │  │  WsRelayClient (OkHttp WebSocket)│
│   │  ◄── {kind:"result", ...}            │  │  - kind:"command" → forward to phone  │  │   ├─ single-flight Mutex        │
│   │  ◄── {kind:"screenshot",...}        │  │  - kind:"result" → forward to agent   │  │   ├─ auto-reconnect (5s retry)   │
│   ├─ write result to data/results/      │  │  - kind:"screenshot" → forward to agent│  │   └─ reads URL from SettingsRepo│
│   ├─ write screenshot to data/shots/     │  │  - kind:"ping" → forward to phone     │  │                                  │
│   └─ disconnect (one-shot)              │  │  - kind:"pong" → forward to agent      │  │  TestControllerExecutor           │
│                                          │  │                                       │  │   ├─ UI: get_state, tap, swipe   │
│  No persistent process.                  │  │  Heartbeat: 15s ping/pong detects     │  │   ├─ Nav: push_route, pop        │
│  Survives sandbox process-killing.       │  │  dead connections.                    │  │   └─ Internals: logcat, DB       │
└──────────────────────────────────────────┘  └───────────────────────────────────────┘  └──────────────────────────────────┘
```

### 4 Components

| Component | Location | Purpose |
|---|---|---|
| **WS relay** | `/home/z/my-project/src/app/api/test-relay/route.ts` | Next.js API route that starts a `ws` npm WebSocketServer on :3030. Persistent inside the Next.js dev server process. Routes messages by `kind` field. 15s heartbeat. |
| **Phone client** | `ANI-KUTA/APP/ani-kuta/core/test-controller/.../WsRelayClient.kt` | OkHttp WebSocket. Single-flight Mutex. Auto-reconnect. Reads relay URL from `SettingsRepository` (key `debug.test.relay_url`). |
| **Settings UI** | `ANI-KUTA/APP/ani-kuta/app/src/debug/.../TestControllerSettingsScreen.kt` | Compose screen: status card (colored dot), URL text field, Save button, Copy Info button. Reached via More → Settings → Test Controller. |
| **Agent helper** | `/home/z/my-project/mini-services/agent-bridge/` | Python `ws-agent.py` (one-shot: connect→send→wait→disconnect) + `agent.sh` (bash wrapper). |

---

## Communication Protocol

### Message Format

All messages are JSON with a `kind` field that the relay uses for routing:

| `kind` | Direction | Payload | Purpose |
|---|---|---|---|
| `register` | both → relay | `{kind:"register", role:"phone"|"agent"}` | Identify as phone or agent |
| `ack` | relay → phone | `{kind:"ack", message:"phone registered"}` | Registration confirmation |
| `command` | agent → relay → phone | `{kind:"command", type:"<TestCommand.type>", id:"...", ...}` | A TestCommand to execute |
| `ping` | agent → relay → phone | `{kind:"ping", id:"..."}` | Smoke test (expects pong) |
| `pong` | phone → relay → agent | `{kind:"pong", id:"...", deviceInfo:{...}, navKey:"..."}` | Device info response |
| `result` | phone → relay → agent | `{kind:"result", type:"<TestResult.type>", id:"...", ok:true/false, ...}` | Command execution result |
| `screenshot` | phone → relay → agent | `{kind:"screenshot", id:"...", data:"<base64 JPEG>"}` | Screenshot binary (base64) |

### Message Flow (example: `ping`)

```
Agent (Python)                Relay (Next.js :3030)              Phone (Kotlin)
   │                               │                               │
   │── connect ws://localhost:3030 ─►                               │
   │── {kind:"register",role:"agent"}►                               │
   │── {kind:"ping", id:"p1"} ───────►                               │
   │                               │── forward {kind:"ping",...} ──►│
   │                               │                               │ (execute)
   │                               │◄── {kind:"pong", id:"p1",...}─│
   │◄── {kind:"pong", id:"p1",...}──│                               │
   │── disconnect ─────────────────►                               │
   │                               │ (phone stays connected)       │
```

### Key Design Decisions

1. **One-shot agent (no persistent process)**: The sandbox kills background processes between Bash tool calls. The agent script connects → sends → waits → disconnects in a single Bash call. The relay (inside Next.js) stays alive.

2. **Phone is persistent**: The phone maintains a long-lived WebSocket connection to the relay. OkHttp's `pingInterval(30s)` keeps it alive. Auto-reconnect on disconnect (5s retry).

3. **`kind` envelope**: The phone wraps TestResult JSON in `{kind:"result", ...}` so the relay can route it. Without this, the relay's `switch(msg.kind)` would fall through (TestResult uses `type` as its discriminator, not `kind`).

4. **Heartbeat**: The relay pings all clients every 15s. Dead connections are terminated (prevents stale `phoneSocket` when Android kills the service).

5. **Port 443 (no carrier blocks)**: The phone connects via `wss://PREVIEW_URL/?XTransformPort=3030` — Caddy forwards port 443 to :3030. No mobile carrier blocks port 443 (unlike MQTT ports 8884/8084 which were blocked on the user's network).

---

## Command Reference (30 types)

### Session / Control
| Command | Example | Returns |
|---|---|---|
| `ping` | `{"type":"ping","id":"p1"}` | `pong` with deviceInfo (manufacturer, model, SDK, screen, ABIs, appVersion) |
| `get_device_info` | `{"type":"get_device_info","id":"d1"}` | Same as pong |
| `keep_screen_on` | `{"type":"keep_screen_on","id":"k1","enabled":true}` | `ok` |
| `wait` | `{"type":"wait","id":"w1","ms":1000}` | `ok` (after delay) |
| `restart_app` | `{"type":"restart_app","id":"r1"}` | `ok` (process killed + relaunched) |

### UI Inspection
| Command | Example | Returns |
|---|---|---|
| `get_state` | `{"type":"get_state","id":"s1","includeScreenshot":true}` | `state` with navKey, packageName, tree (NodeInfo), hasScreenshot |
| `find_nodes` | `{"type":"find_nodes","id":"f1","text":"Play"}` | `nodes` with matching NodeInfo list |
| `screenshot` | `{"type":"screenshot","id":"sh1"}` | `screenshot_ref` + JPEG binary |

### UI Interaction
| Command | Example | Returns |
|---|---|---|
| `tap` | `{"type":"tap","id":"t1","x":540,"y":1200}` or `{"type":"tap","id":"t1","nodeId":7}` | `ok` |
| `long_click` | `{"type":"long_click","id":"l1","x":540,"y":1200,"durationMs":800}` | `ok` |
| `swipe` | `{"type":"swipe","id":"sw1","x1":540,"y1":1500,"x2":540,"y2":500,"durationMs":300}` | `ok` |
| `scroll` | `{"type":"scroll","id":"sc1","direction":"DOWN","amount":1}` | `ok` |
| `set_text` | `{"type":"set_text","id":"st1","nodeId":5,"text":"hello"}` | `ok` |
| `back` / `home` / `recents` / `notifications` | `{"type":"back","id":"b1"}` | `ok` |

### Navigation
| Command | Example | Returns |
|---|---|---|
| `push_route` | `{"type":"push_route","id":"pr1","route":"library"}` | `ok` |
| `pop` | `{"type":"pop","id":"po1"}` | `ok` |
| `clear_to_root` | `{"type":"clear_to_root","id":"cr1","root":"browse"}` | `ok` |
| `get_backstack` | `{"type":"get_backstack","id":"gb1"}` | `backstack` with key names |

**Supported routes** (see `AppRouteRegistryImpl.kt`):
`browse`, `library`, `search`, `more`, `profile`, `settings`, `notifications`, `notifications_library`, `updates_settings`, `update_categories`, `appearance`, `appearance_general`, `episode_settings`, `player_settings`, `downloads`, `downloaded_files`, `download_settings`, `extensions_settings`, `auto_link_settings`, `extension_repo_settings`, `extension_detail` (args: pkgName), `source_preferences` (args: sourceId), `history`, `updates`, `anime_details_anilist` (args: animeId), `anime_details_extension` (args: sourceId, animeUrl, title), `test_controller_settings`.

### App Internals
| Command | Example | Returns |
|---|---|---|
| `get_logcat` | `{"type":"get_logcat","id":"gl1","lines":200,"filter":null,"level":"WARN"}` | `logcat` with LogEntry list |
| `get_network_logs` | `{"type":"get_network_logs","id":"gn1","lines":50}` | `network_logs` with NetworkLogEntry list |
| `get_activity_logs` | `{"type":"get_activity_logs","id":"ga1","lines":50,"eventType":"WATCH_START"}` | `activity_logs` with ActivityEventSummary list |
| `db_list_tables` | `{"type":"db_list_tables","id":"dt1"}` | `tables` with TableSummary list |
| `db_query` | `{"type":"db_query","id":"dq1","table":"content","limit":10,"offset":0}` | `rows` with columns + rows |
| `db_query_sql` | `{"type":"db_query_sql","id":"ds1","sql":"SELECT * FROM content WHERE title LIKE '%Re%'"}` | `rows` |
| `db_count` | `{"type":"db_count","id":"dc1","table":"library_item"}` | `count` |
| `get_preference` | `{"type":"get_preference","id":"gp1","key":"debug.test.relay_url"}` | `preference` with value |
| `set_preference` | `{"type":"set_preference","id":"sp1","key":"some.key","value":"some.value"}` | `preference` |

---

## How to Set Up (for a new session)

### Agent side (sandbox)
1. **The relay auto-starts** when you hit `http://localhost:3000/api/test-relay`. The `agent.sh` script does this automatically.
2. **Python 3.13 + websockets** must be installed (already in the sandbox).
3. **Check relay status**: `curl http://localhost:3000/api/test-relay` → look for `"relay":"running"` + `"phoneConnected":true`.

### Phone side (user)
1. **Install the debug APK** from CI artifacts (branch `TEST_BETA_FEATURE`).
2. **Enable AccessibilityService**: Settings → Accessibility → "ANI-KUTA Test Controller" → ON.
3. **Configure relay URL**: More → Settings → Test Controller → enter the sandbox preview URL with `?XTransformPort=3030` → Save.
   - Example: `wss://preview-chat-xxxxx.space-z.ai/?XTransformPort=3030`
4. **Open the app** → toast "✅ Test controller online" confirms connection.

### Verifying connection
```bash
curl http://localhost:3000/api/test-relay
# → {"relay":"running","phoneConnected":true,...} ← phone is connected

cd /home/z/my-project/mini-services/agent-bridge
./agent.sh ping
# → {"type":"pong","deviceInfo":{"manufacturer":"OnePlus",...}} ← phone responds
```

---

## How to Build + Push Changes

The test-controller code is on branch `TEST_BETA_FEATURE` (NOT merged to `main`).

```bash
cd /home/z/my-project/ani-kuta-repo
git checkout TEST_BETA_FEATURE

# Make changes to:
# - :core:test-api/ (types, constants, NavKey)
# - :core:test-controller/ (AccessibilityService, WsRelayClient, executor, providers)
# - :app/src/debug/ (AppRouteRegistryImpl, DebugNavBinder, DebugInit, TestControllerSettingsScreen)
# - :app/src/release/ (no-op mirrors)
# - :app/src/main/ (MainActivity dispatch, SettingsScreen nav row)

# Push (CI builds the APK):
git add -A && git commit -m "..." && git push origin TEST_BETA_FEATURE

# Watch CI:
curl -s -H "Authorization: token <TOKEN>" "https://api.github.com/repos/testplay-byte/ANI-KUTA/actions/runs?branch=TEST_BETA_FEATURE&per_page=1"
```

### CI workflow
- `.github/workflows/build-apk.yml` triggers on `TEST_BETA_FEATURE` pushes.
- Downloads as Artifacts → `anikuta-apk` (zip with the APK inside).
- Verifies ABIs (only `arm64-v8a` + `armeabi-v7a`).

---

## Troubleshooting

### `timeout — no result received`
1. Check relay: `curl http://localhost:3000/api/test-relay` → is `phoneConnected: true`?
2. If false: ask the user to reopen the app (the phone auto-connects on launch).
3. If true but still times out: the phoneSocket might be stale. Wait 15s for the heartbeat to detect it, then ask the user to reopen the app.
4. Check relay logs: `grep test-relay /home/z/my-project/.zscripts/dev.log | tail -20`

### `NO_PHONE` error
The relay is running but no phone is connected. Ask the user to:
1. Enable the AccessibilityService (Settings → Accessibility → ANI-KUTA Test Controller).
2. Open the app (triggers the app-open health-check).
3. Check the Test Controller settings screen (More → Settings → Test Controller) — status should be "Connected".

### Screenshot is missing (`hasScreenshot: false`)
- The `PixelCopy(Window)` capture can fail if the Activity isn't in the foreground.
- Try the standalone `screenshot` command instead of `get_state` with `includeScreenshot: true`.
- Ensure the app is in the foreground (not behind another app or the keyguard).

### Empty accessibility tree (`tree: {nodeId: 0, children: []}`)
- Known issue: the `CharSequence == String` comparison in `AccessibilityTreeSerializer.serialize()` can fail on some devices. Fix: change `root.packageName == OUR_PACKAGE` to `root.packageName?.toString() == OUR_PACKAGE`.
- Workaround: use `find_nodes` with text/resourceId query instead of the full tree.

### `NAV_NOT_BOUND` error
`DebugNavRegistry.current` is null — the Compose `AppRoot` hasn't composed yet, or the app is in a release build. Ensure the app is in the foreground + it's a debug build.

---

## Decisions (D-197 through D-202, D-198 v3)

See `AGENT-CONTEXT/memory/decisions.md`:
- **D-197** — Module structure (`:core:test-api` + `:core:test-controller`).
- **D-198 v3** — WebSocket relay (replaced ntfy+relay v1, then MQTT v2 — both failed).
- **D-199** — AccessibilityService UI mechanism + triple addressing.
- **D-200** — PixelCopy screenshots (all API levels).
- **D-201** — Reuse of debug-bubble facilities.
- **D-202** — Debug-only removability.

---

## Future Improvements (researched, not yet implemented)

1. **Firebase Realtime Database** — both sides connect to Firebase (port 443, no carrier blocks). Free tier (1GB storage, 10GB/mo transfer). No URL discovery needed (hardcoded Firebase project URL). More reliable than a sandbox-hosted relay (Firebase is a managed service with 99.9% uptime).
2. **Socket.io** — more robust than raw WebSocket (built-in reconnect, room-based routing, binary framing). Would replace the `ws` npm package with `socket.io`.
3. **Authentication** — add a per-session token (agent generates it, phone validates it before executing commands). Currently anyone who knows the preview URL can send commands (acceptable for debug, but not production).
4. **Settings UI improvements** — add a "Test Connection" button that sends a ping + shows the result inline. Add a QR code for the relay URL (scan with phone camera to configure).
5. **Stable URL** — use Cloudflare Tunnel (`cloudflared`) to create a persistent domain (e.g., `anikuta-test.example.com`) that forwards to the sandbox. Currently the preview URL changes per session.

---

## File Map

### Android (in the ANI-KUTA repo, branch `TEST_BETA_FEATURE`)
```
ANI-KUTA/APP/ani-kuta/
├── core/test-api/src/main/java/.../core/testapi/
│   ├── TestControllerConstants.kt     # MQTT/WS broker + topic constants
│   ├── TestCommand.kt                 # sealed class: 30 command types
│   ├── TestResult.kt                  # sealed class: 15 result types + models
│   ├── DebugNavRegistry.kt            # singleton: binds Compose backstack
│   ├── DebugWindowRegistry.kt         # singleton: binds foreground Activity window
│   ├── AppRouteRegistry.kt            # interface: route name → NavKey
│   └── TestControllerSettingsKey.kt   # NavKey for the settings screen
├── core/test-controller/src/main/java/.../core/testcontroller/
│   ├── TestAccessibilityService.kt    # AccessibilityService (lifecycle, Koin deps)
│   ├── WsRelayClient.kt               # OkHttp WebSocket (single-flight, auto-reconnect)
│   ├── TestControllerStatus.kt        # singleton: app-open health-check
│   ├── TestToaster.kt                 # throttled toast helper
│   ├── TestControllerExecutor.kt      # command dispatcher (30 handlers)
│   ├── AccessibilityTreeSerializer.kt # node tree → JSON + nodeId registry
│   ├── GestureExecutor.kt             # dispatchGesture + performAction
│   ├── ScreenshotCapture.kt           # PixelCopy (all API levels)
│   ├── NavExecutor.kt                 # push_route/pop/clear_to_root
│   ├── DeviceInfoProvider.kt          # Build constants → DeviceInfo
│   ├── LogcatProvider.kt              # DebugLogBuffer → LogEntry list
│   ├── NetworkLogsProvider.kt         # DebugNetworkStats → NetworkLogEntry list
│   ├── ActivityLogsProvider.kt        # AnikutaDatabase → ActivityEventSummary list
│   ├── DatabaseProvider.kt            # read-only SQLiteDatabase queries
│   └── PreferencesProvider.kt         # SettingsRepository get/set
├── app/src/debug/java/.../anikuta/
│   ├── AppRouteRegistryImpl.kt        # route → NavKey mapping
│   ├── DebugNavBinder.kt              # binds backstack to DebugNavRegistry
│   ├── TestControllerSettingsScreen.kt # Compose UI (status, URL, copy, save)
│   └── DebugInit.kt                   # Koin + ActivityLifecycleCallbacks
├── app/src/release/java/.../anikuta/
│   ├── DebugNavBinder.kt              # no-op mirror
│   └── TestControllerSettingsScreen.kt # no-op mirror
├── app/src/main/java/.../anikuta/
│   ├── MainActivity.kt                # DebugNavBinder call + testTagsAsResourceId + dispatch
│   └── settings/SettingsScreen.kt     # "Test Controller" nav row at top
└── core/test-controller/src/main/
    ├── AndroidManifest.xml            # <service> declaration (debug-only)
    └── res/xml/test_controller_service_config.xml  # canPerformGestures, etc.
```

### Sandbox (NOT in the ANI-KUTA repo)
```
/home/z/my-project/
├── src/app/api/test-relay/route.ts    # Next.js API route: starts ws npm WSS on :3030
├── mini-services/agent-bridge/
│   ├── ws-agent.py                    # one-shot Python WebSocket client
│   ├── agent.sh                       # bash helper (ping, get_state, etc.)
│   └── data/
│       ├── results/<id>.json          # result JSONs (direct Read tool access)
│       └── screenshots/<id>.png       # screenshot PNGs (for VLM analysis)
└── package.json                       # has "ws" dependency
```

---

## How to Remove Before Publish

1. Delete modules: `:core:test-api` + `:core:test-controller` (from `settings.gradle.kts` + filesystem).
2. Remove `debugImplementation(:core:test-controller)` + `implementation(:core:test-api)` from `:app/build.gradle.kts`.
3. Remove `DebugNavBinder(backstack)` + `testTagsAsResourceId` from `MainActivity.kt`.
4. Remove `AppRouteRegistryImpl.kt` + `DebugNavBinder.kt` + `TestControllerSettingsScreen.kt` from `:app/src/debug/`.
5. Remove `AppRouteRegistry` Koin binding + `TestControllerStatus.ensureConnected()` from `:app/src/debug/DebugInit.kt`.
6. Remove the `Test Controller` nav row from `SettingsScreen.kt`.
7. Remove the `agent-bridge` mini-service.
8. Revert `build-apk.yml` trigger (remove `TEST_BETA_FEATURE`).

Release builds already contain zero test-controller code (all `debugImplementation`).
