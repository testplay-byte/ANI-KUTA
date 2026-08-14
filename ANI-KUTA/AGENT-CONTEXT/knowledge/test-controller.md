# Test Controller — Autonomous Remote Testing System

> **Status:** ✅ Built + CI green on `TEST_BETA_FEATURE` branch (D-197..D-202).
> **Last updated:** Autonomous test controller session.

## Overview

The **Test Controller** is a debug-only feature that lets a remote AI agent inspect and control the running ANI-KUTA app on the user's physical phone — interact with the UI, take screenshots, read logcat/database/network logs — for autonomous testing.

It mirrors the **debug-bubble pattern** (`:core:debug-api` types + `:feature:debug-bubble` impl, `debugImplementation` in `:app`). Zero test-controller code in release APKs.

## Architecture

```
┌─ AGENT (sandbox, Python) ───────────────┐  ┌─ PHONE (ANI-KUTA app, debug build) ──┐
│                                         │  │                                       │
│  mqtt-agent.py (one-shot per command)   │  │  TestAccessibilityService             │
│   │                                     │  │   ├─ onServiceConnected()             │
│   │  wss://broker.hivemq.com:8884/mqtt  │  │   │   ├─ resolves deps from Koin     │
│   ├─ connect                            │  │   │   ├─ builds executor + providers│
│   ├─ subscribe result + shot/#         │  │   │   └─ starts MqttBridge          │
│   ├─ publish cmd                        │  │   │                                   │
│   │                                     │  │  MqttBridge (persistent, auto-reconnect)
│   │  (wait for result, ≤30s)            │  │   ├─ connects to broker (hardcoded)  │
│   │  ← result JSON                      │  │   ├─ subscribes to cmd topic          │
│   │  ← screenshot bytes (if any)        │  │   ├─ on message → executor.execute()  │
│   ├─ write result to data/results/     │  │   └─ publishes result + screenshot    │
│   ├─ write screenshot to data/shots/    │  │                                       │
│   └─ disconnect                         │  │  TestControllerExecutor               │
│                                         │  │   ├─ UI: get_state, tap, swipe, ...  │
│  No persistent process — one-shot.      │  │   ├─ Nav: push_route, pop, backstack  │
│  Survives sandbox process-killing.      │  │   └─ Internals: logcat, DB, network  │
└─────────────────────────────────────────┘  └───────────────────────────────────────┘
                    │                                          │
                    └────────── public MQTT broker ────────────┘
                    wss://broker.hivemq.com:8884/mqtt
                    (free, no signup, no API key, no rate limit)
```

### 4 Components

| Component | Location | Purpose |
|---|---|---|
| **`:core:test-api`** | `ANI-KUTA/APP/ani-kuta/core/test-api/` | Types (always on classpath): `TestCommand`/`TestResult` sealed classes, `DebugNavRegistry`, `DebugWindowRegistry`, `AppRouteRegistry`, `TestControllerConstants` (MQTT broker + topics). |
| **`:core:test-controller`** | `ANI-KUTA/APP/ani-kuta/core/test-controller/` | Debug-only impl: `TestAccessibilityService`, `MqttBridge`, `AccessibilityTreeSerializer`, `GestureExecutor`, `ScreenshotCapture`, `TestControllerExecutor`, 6 providers. |
| **`:app` wiring** | `:app/src/debug/` | `AppRouteRegistryImpl` (route → NavKey), `DebugNavBinder` (binds Compose backstack), `DebugInit` extensions (Koin + ActivityLifecycleCallbacks). `MainActivity` hooks (`DebugNavBinder` + `testTagsAsResourceId`). |
| **Agent-side helper** | `mini-services/agent-bridge/` | Python `mqtt-agent.py` (one-shot MQTT client) + `agent.sh` (bash wrapper). |

## Communication Flow (MQTT)

### Topics (hardcoded — both sides use the same strings)

| Topic | Direction | Payload |
|---|---|---|
| `anikuta/test/v1/cmd` | Agent → Phone | `TestCommand` JSON |
| `anikuta/test/v1/result` | Phone → Agent | `TestResult` JSON |
| `anikuta/test/v1/shot/<commandId>` | Phone → Agent | JPEG bytes (screenshot) |

### Flow

1. **Phone** (on AccessibilityService start): connects to `wss://broker.hivemq.com:8884/mqtt`, subscribes to `anikuta/test/v1/cmd`. Auto-reconnects on disconnect.
2. **Agent** (per command): runs `mqtt-agent.py '<json>'`:
   - Connects to the same broker.
   - Subscribes to `anikuta/test/v1/result` + `anikuta/test/v1/shot/#`.
   - Publishes the command to `anikuta/test/v1/cmd`.
   - Waits up to 30s for the result (+ screenshot if expected).
   - Writes result to `data/results/<id>.json` + screenshot to `data/screenshots/<id>.png`.
   - Disconnects. **No persistent process.**

### Why MQTT (not ntfy.sh + Bun relay)

| Issue | ntfy + relay (v1) | MQTT (v2) |
|---|---|---|
| User config | Needed relay URL + token (no UI built) | **None** — hardcoded broker + topics |
| URL discovery | Agent can't find its own public URL | **Not needed** — both connect to a known broker |
| Persistent process | Bun relay dies between Bash calls | **None** — agent is one-shot per command |
| Rate limits | ntfy 250 msg/day (too low) | **None** (reasonable use) |

## How to Build + Install

### For the user

1. **CI builds the APK** on every push to `TEST_BETA_FEATURE`. Download from GitHub Actions (the `Build APK` workflow → latest run → Artifacts → `anikuta-apk`).
2. **Install the APK** on the phone (it's signed with the debug keystore — updates over the existing install without uninstalling).
3. **Enable the AccessibilityService**: Settings → Accessibility → "ANI-KUTA Test Controller" → toggle ON.
4. The app auto-connects to the MQTT broker. **No other config needed.**

### For a new AI agent

```bash
# Clone the repo (read CORE_RULES.md first per §1)
git clone https://github.com/testplay-byte/ANI-KUTA.git
cd ANI-KUTA

# The test-controller code is on the TEST_BETA_FEATURE branch
git checkout TEST_BETA_FEATURE

# Read this file + the decisions D-197..D-202 in AGENT-CONTEXT/memory/decisions.md
```

## How to Send Commands (Agent-side)

### Prerequisites (sandbox)

```bash
# Python 3.13 + paho-mqtt (already installed in the sandbox)
python3.13 -c "import paho.mqtt.client; print('OK')"

# The agent helper
cd /home/z/my-project/mini-services/agent-bridge
```

### Commands

```bash
# Smoke test — send ping, wait for pong (device info + navKey)
./agent.sh ping

# Get the current UI state + accessibility tree + screenshot
./agent.sh get_state
# → prints TestResult JSON to stdout
# → screenshot saved to data/screenshots/<id>.png (view via VLM skill)

# Navigate to a named route
./agent.sh push_route library
./agent.sh push_route settings

# Tap at coordinates
./agent.sh tap 540 1200

# Swipe
./agent.sh swipe 540 1500 540 500

# Press Back
./agent.sh back

# List database tables
./agent.sh db_tables

# Send a raw command (any of the 30 TestCommand types)
./agent.sh send '{"type":"db_query","id":"q1","table":"content","limit":10}'

# Re-read a stored result
./agent.sh result <commandId>

# View screenshot path
./agent.sh shot <commandId>
```

### Reading Results

Results are stored in two places:
1. **stdout** of the `agent.sh` command (the `TestResult` JSON).
2. **File**: `mini-services/agent-bridge/data/results/<commandId>.json` (for direct Read tool access).

Screenshots: `mini-services/agent-bridge/data/screenshots/<commandId>.png` — use the **VLM skill** (z-ai-web-dev-sdk) to analyze them.

## Command Set (30 types)

### Session / Control
- `ping` → `pong` with device info (manufacturer, model, SDK, screen, ABIs, app version).
- `get_device_info` → same as pong.
- `keep_screen_on(bool)` → toggles `FLAG_KEEP_SCREEN_ON` on the foreground Activity.
- `wait(ms)` → sleep + return ok.
- `restart_app` → kill process + relaunch MainActivity.

### UI Inspection
- `get_state(includeScreenshot)` → returns the accessibility tree (navKey, packageName, tree of NodeInfo with bounds/text/clickable/actions) + optional screenshot.
- `find_nodes(text?, resourceId?, className?, limit)` → search the tree by text/id/class.
- `screenshot` → capture a screenshot only.

### UI Interaction
- `tap(nodeId | {x,y})` → tap by node (ACTION_CLICK) or coordinates (dispatchGesture).
- `long_click(nodeId | {x,y}, durationMs)` → long-click.
- `swipe(x1,y1,x2,y2,durationMs)` → swipe gesture.
- `scroll(x?,y?,direction,amount)` → scroll up/down/left/right.
- `set_text(nodeId, text)` → ACTION_SET_TEXT.
- `back()` / `home()` / `recents()` / `notifications()` → global actions.

### Navigation
- `push_route(route, args)` → navigate to a named route (see AppRouteRegistryImpl for the full list).
- `pop()` → pop the backstack.
- `clear_to_root(root)` → clear + set root.
- `get_backstack()` → list current backstack as screen names.

### App Internals
- `get_logcat(lines, filter?, level?)` → recent Logger output from DebugLogBuffer.
- `get_network_logs(lines, filter?)` → recent OkHttp requests from DebugNetworkStats.
- `get_activity_logs(lines, eventType?)` → recent activity_event DB rows.
- `db_list_tables()` → all SQLDelight tables + row counts.
- `db_query(table, limit, offset)` → SELECT * from a table.
- `db_query_sql(sql, limit)` → arbitrary SELECT (validated, read-only).
- `db_count(table)` → row count.
- `get_preference(key)` → read from SettingsRepository (app_settings table).
- `set_preference(key, value)` → write to SettingsRepository.

## Credentials

**None needed.** The MQTT broker (`broker.hivemq.com:8884`) is a free public broker — no signup, no API key, no account. Both the app + the agent use hardcoded broker URI + topic names (in `TestControllerConstants.kt` on the app side, + in `mqtt-agent.py` on the agent side).

**Security note:** The topics are public (in the source code). Anyone who subscribes to `anikuta/test/v1/#` can see commands + results. For a debug-only tool with one user + one phone, this is acceptable. If stronger auth is needed:
- Add a per-session token: agent generates a random 32-hex token, includes it in every command body; the phone validates it before executing. The token can be delivered via a 6-digit PIN the user enters in the app's Test settings (requires building a settings UI — not yet done).

## How to Remove Before Publish

The test-controller is entirely debug-only. To remove:

1. Delete the 2 modules: `:core:test-api` + `:core:test-controller` (from `settings.gradle.kts` + the filesystem).
2. Remove `debugImplementation(project(":core:test-controller"))` + `implementation(project(":core:test-api"))` from `:app/build.gradle.kts`.
3. Remove `DebugNavBinder(backstack)` call + `testTagsAsResourceId` modifier from `MainActivity.kt`.
4. Remove `AppRouteRegistryImpl.kt` + `DebugNavBinder.kt` from `:app/src/debug/`.
5. Remove `AppRouteRegistry` Koin binding + ActivityLifecycleCallbacks from `:app/src/debug/DebugInit.kt`.
6. Remove the `agent-bridge` mini-service.
7. Revert `build-apk.yml` trigger (remove `TEST_BETA_FEATURE` from the branch list).

Release builds already contain zero test-controller code (it's all `debugImplementation`).

## Troubleshooting

### `timeout (30s) — no result received`
The phone didn't respond within 30s. Check:
1. **Is the app installed?** The debug APK must be installed (from CI artifacts).
2. **Is the AccessibilityService enabled?** Settings → Accessibility → "ANI-KUTA Test Controller" → ON.
3. **Is the phone online?** The phone needs internet to reach the MQTT broker.
4. **Is the HiveMQ broker up?** If down, switch to EMQX: change `BROKER_HOST` in both `mqtt-agent.py` + `MqttBridge.kt` to `broker.emqx.io`, port `8084`.
5. **Check logcat** on the phone (via the debug bubble's Console tab) for `Anikuta:Test:Mqtt` or `Anikuta:Test:Service` errors.

### `MQTT start failed: ...`
The Paho client couldn't connect. Usually a network issue. Auto-reconnect will retry. If persistent, switch broker (see above).

### Screenshot is black
On API 24-29, `PixelCopy(Window)` captures the Activity's window. If the Activity is paused (not in foreground), `DebugWindowRegistry.window` is null → screenshot fails with `NO_WINDOW`. Ensure the app is in the foreground when capturing.

### `STALE_SNAPSHOT` error
The `nodeId` from a previous `get_state` is no longer valid (the UI changed). Re-send `get_state` to get fresh nodeIds, or use `{x,y}` coordinates instead.

### `NAV_NOT_BOUND` error
`DebugNavRegistry.current` is null — the Compose `AppRoot` hasn't composed yet, or the app is in a release build. Ensure the app is in the foreground + it's a debug build.

## Decisions

See `AGENT-CONTEXT/memory/decisions.md`:
- **D-197** — Module structure (`:core:test-api` + `:core:test-controller`).
- **D-198 v2** — MQTT transport (replaced ntfy+relay).
- **D-199** — AccessibilityService UI mechanism + triple addressing.
- **D-200** — PixelCopy screenshots (all API levels).
- **D-201** — Reuse of debug-bubble facilities.
- **D-202** — Debug-only removability.
