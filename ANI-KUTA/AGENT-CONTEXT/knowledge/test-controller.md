# ANI-KUTA Test Controller — Complete Autonomous Testing Guide

> **Purpose:** This document gives any AI agent everything needed to autonomously connect to, control, inspect, and test the ANI-KUTA Android app running on the user's physical phone — without ADB, without modifying the app's code, and without user intervention beyond the initial setup.
>
> **Audience:** AI agents (Claude, GPT, Gemini, etc.) who have access to a cloud sandbox with Bash + Python + web tools.
>
> **Last updated:** D-198 v4.3 session (sha bf8b8360, branch TEST_BETA_FEATURE).

---

## TL;DR — Quick Start (Copy-Paste Ready)

```bash
# 1. Set the relay URL (Cloudflare Workers — always-on, $0/month)
export RELAY_URL="wss://anikuta-relay.k-h-u-r-r-a-m-n-o-o-r88888888888.workers.dev/"

# 2. Verify the phone is connected
cd /home/z/my-project/mini-services/agent-bridge
./agent.sh relay_status
# Expected: {"relay":"running","phoneConnected":true,...}

# 3. Send your first command (smoke test)
./agent.sh ping
# Expected: {"type":"pong","deviceInfo":{"manufacturer":"OnePlus",...},"navKey":"AnimeBrowseKey"}

# 4. Get the current screen state (UI tree + screenshot)
./agent.sh get_state
# Result JSON printed to stdout
# Screenshot saved to data/screenshots/<id>.png

# 5. Analyze the screenshot with VLM
z-ai vision -p "Describe what's on this screen" -i "./data/screenshots/<id>.png"
```

---

## Table of Contents

1. [System Overview](#1-system-overview)
2. [Architecture (How It Works)](#2-architecture-how-it-works)
3. [Communication Protocol](#3-communication-protocol)
4. [Prerequisites + Setup](#4-prerequisites--setup)
5. [The Agent Helper Script (agent.sh)](#5-the-agent-helper-script-agentsh)
6. [Complete Command Reference (30 commands)](#6-complete-command-reference-30-commands)
7. [How to Navigate the App](#7-how-to-navigate-the-app)
8. [How to Interact with UI Elements](#8-how-to-interact-with-ui-elements)
9. [How to Read App Internals (DB, logs, network)](#9-how-to-read-app-internals-db-logs-network)
10. [How to Take + Analyze Screenshots](#10-how-to-take--analyze-screenshots)
11. [Credentials + Configuration](#11-credentials--configuration)
12. [How to Build + Install the APK](#12-how-to-build--install-the-apk)
13. [How to Deploy the Cloudflare Relay](#13-how-to-deploy-the-cloudflare-relay)
14. [Troubleshooting](#14-troubleshooting)
15. [Known Limitations + Workarounds](#15-known-limitations--workarounds)
16. [File Map (Everything's Location)](#16-file-map-everythings-location)
17. [How to Remove Before Publish](#17-how-to-remove-before-publish)

---

## 1. System Overview

The ANI-KUTA Test Controller is a **debug-only autonomous testing system** that lets an AI agent remotely control the ANI-KUTA Android app on a user's physical phone. It uses:

- **Android AccessibilityService** — to inspect the UI tree + perform taps/swipes/scrolls (no app code changes needed)
- **Cloudflare Workers + Durable Objects** — as a persistent WebSocket relay between the agent + the phone
- **Python `websockets` library** — for the agent-side one-shot client

### Key properties

| Property | Value |
|---|---|
| **Transport** | WebSocket (wss://, port 443) |
| **Relay** | Cloudflare Workers (always-on, $0/month, 99.9% SLA) |
| **Relay URL** | `wss://anikuta-relay.k-h-u-r-r-a-m-n-o-o-r88888888888.workers.dev/` |
| **Latency** | ~20ms edge + ~100ms phone round-trip |
| **Payload limit** | 32MB (screenshots are 50-200KB) |
| **Free tier** | 100K req/day (we use ~2K/day = 2% headroom) |
| **App changes** | ZERO page code changes. Only 3 minimal debug-only additions. |
| **Release builds** | Zero test-controller code in release APKs |

---

## 2. Architecture (How It Works)

```
┌─ AI AGENT (sandbox) ────────────────────┐    ┌─ CLOUDFLARE WORKERS ──────────┐    ┌─ PHONE (ANI-KUTA debug build) ────┐
│                                         │    │                                │    │                                   │
│  Python ws-agent.py (one-shot)          │    │  Worker (stateless router)    │    │  TestAccessibilityService         │
│  ┌───────────────────────────────────┐  │    │  ┌────────────────────────┐  │    │  ┌─────────────────────────────┐  │
│  │ 1. Connect to relay (ws://)       │  │    │  │ Routes WS upgrades to  │  │    │  │ onServiceConnected():       │  │
│  │ 2. Register as "agent"            │  │    │  │ the Durable Object     │  │    │  │   resolves Koin deps        │  │
│  │ 3. Send {kind:"command",...}      │──┼────┼─►│                        │  │    │  │   builds executor            │  │
│  │ 4. Wait for result (≤30s)         │  │    │  │ Durable Object "main"  │  │    │  │   starts WsRelayClient       │  │
│  │ 5. Receive {kind:"result",...}    │  │    │  │ ┌────────────────────┐ │  │    │  │                             │  │
│  │ 6. Receive screenshot (base64)    │◄─┼────┼──┤ │ Holds phone WS    │ │  │    │  │ WsRelayClient (persistent): │  │
│  │ 7. Write result to data/results/  │  │    │  │ │ + agent WS         │ │  │    │  │   connects to CF relay      │  │
│  │ 8. Write screenshot to data/shots/│  │    │  │ │ Forwards by "kind" │ │  │    │  │   registers as "phone"      │  │
│  │ 9. Disconnect (one-shot)          │  │    │  │ │ Heartbeat 15s      │ │  │    │  │   auto-reconnects on drop   │  │
│  └───────────────────────────────────┘  │    │  │ └─────────┬──────────┘ │  │    │  │                             │  │
│                                         │    │  └───────────┼────────────┘  │    │  │ TestControllerExecutor:     │  │
│  No persistent process.                 │    │              │               │    │  │   dispatches 30 command types│  │
│  Survives sandbox process-killing.      │    │              │               │    │  │                             │  │
│                                         │    │              │               │    │  │ AccessibilityTreeSerializer:│  │
│  Tools used:                            │    │              │               │    │  │   getRootInActiveWindow()   │  │
│  - Bash (agent.sh)                      │    │              │               │    │  │   → JSON tree with nodeIds  │  │
│  - Python 3.13 + websockets             │    │              │               │    │  │                             │  │
│  - z-ai vision (VLM for screenshots)    │    │              │               │    │  │ GestureExecutor:            │  │
│                                         │    │              │               │    │  │   dispatchGesture (taps)    │  │
│  Files:                                 │    │              │               │    │  │   performAction (clicks)    │  │
│  /home/z/my-project/mini-services/      │    │              │               │    │  │   performGlobalAction (back)│  │
│    agent-bridge/ws-agent.py             │    │              │               │    │  │                             │  │
│    agent-bridge/agent.sh                │    │              │               │    │  │ ScreenshotCapture:          │  │
│    agent-bridge/data/results/<id>.json  │    │              │               │    │  │   PixelCopy(Window)         │  │
│    agent-bridge/data/screenshots/<id>.png│   │              │               │    │  │   → JPEG q70, max 1080px   │  │
│                                         │    │              │               │    │  │                             │  │
│                                         │    │              │               │    │  │ Providers (reuse debug-bubble):│
│                                         │    │              │               │    │  │   LogcatProvider (DebugLogBuffer)│
│                                         │    │              │               │    │  │   NetworkLogsProvider (DebugNetworkStats)│
│                                         │    │              │               │    │  │   ActivityLogsProvider (AnikutaDatabase)│
│                                         │    │              │               │    │  │   DatabaseProvider (read-only SQLite)│
│                                         │    │              │               │    │  │   PreferencesProvider (SettingsRepository)│
│                                         │    │              │               │    │  │   NavExecutor (DebugNavRegistry)│
│                                         │    │              │               │    │  │   DeviceInfoProvider (Build constants)│
│                                         │    │              │               │    │  └──────────────┬──────────────┘  │
│                                         │    │              │               │    │                 │                 │
│                                         │    │  wss://anikuta-relay...workers.dev/   │◄────────────────┘                 │
│                                         │    │  (port 443, no carrier blocks)        │   persistent WebSocket              │
│                                         │    │  WebSocket Hibernation (idle = $0)    │   auto-reconnect (5s retry)         │
└─────────────────────────────────────────┘    └────────────────────────────────┘    └─────────────────────────────────────┘
```

### Why this works without modifying the app

The AccessibilityService is an **Android system-level API** — like a screen reader. It can:
- **Read** any app's UI tree (every button, text field, image, their bounds, text, clickability)
- **Interact** with any app's UI (tap, long-click, scroll, set text, press back/home)
- **Capture screenshots** via PixelCopy

The app doesn't know it's being controlled. The app's code (BrowseScreen, LibraryScreen, SearchScreen, DetailsScreen, etc.) is **completely untouched**. The only app changes are 3 minimal debug-only additions (see §17).

---

## 3. Communication Protocol

### Message format

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

### Message flow (example: `tap` command)

```
Agent (Python)                Cloudflare Worker (DO)             Phone (Kotlin)
   │                               │                                │
   │── connect wss://anikuta-relay──►                                │
   │── {kind:"register",role:"agent"}►                                │
   │── {kind:"command",type:"tap",  ─►│                                │
   │    id:"t1",nodeId:37}           │── forward to phone ──────────►│
   │                               │                                │ (execute: tap node 37)
   │                               │◄── {kind:"result",type:"ok", ──│
   │                               │     id:"t1",ok:true,...}        │
   │◄── {kind:"result",type:"ok", ──│                                │
   │     id:"t1",ok:true,...}       │                                │
   │── disconnect ──────────────────►                                │
   │                               │ (phone stays connected)         │
```

### Key design decisions

1. **One-shot agent**: The agent connects, sends one command, waits for the result, disconnects. No persistent process (the sandbox kills background processes between Bash calls).

2. **Persistent phone**: The phone maintains a long-lived WebSocket connection to the relay. OkHttp's `pingInterval(30s)` keeps it alive. Auto-reconnect on disconnect (5s retry).

3. **`kind` envelope**: The phone wraps TestResult JSON in `{kind:"result", ...}` so the relay can route it. TestResult uses `type` as its polymorphic discriminator (not `kind`), so the extra field is ignored by the phone's JSON parser (`ignoreUnknownKeys=true`).

4. **Single-phone enforcement**: Only one phone can be connected at a time. If a new phone registers, the old connection is closed.

5. **WebSocket Hibernation**: The Cloudflare Durable Object uses `ctx.acceptWebSocket(ws)` — idle connections hibernate server-side ($0 cost). When a message arrives, the DO wakes in ~5ms.

---

## 4. Prerequisites + Setup

### Agent side (sandbox — already configured)

```bash
# Python 3.13 + websockets (already installed in the sandbox)
python3.13 -c "import websockets; print('OK')"

# The agent helper script
ls /home/z/my-project/mini-services/agent-bridge/agent.sh
# Should exist + be executable

# The Cloudflare relay URL (hardcoded as the default)
echo $RELAY_URL
# If empty, set it:
export RELAY_URL="wss://anikuta-relay.k-h-u-r-r-a-m-n-o-o-r88888888888.workers.dev/"
```

### Phone side (user — one-time setup)

1. **Install the debug APK** (from CI artifacts, branch `TEST_BETA_FEATURE`).
2. **Enable AccessibilityService**: Settings → Accessibility → "ANI-KUTA Test Controller" → ON.
3. **Open the app** — it auto-connects to the Cloudflare relay (hardcoded URL, no config needed).
4. The user sees a toast: "✅ Test controller connected".

### Verifying connection

```bash
cd /home/z/my-project/mini-services/agent-bridge
./agent.sh relay_status
# Expected: {"relay":"running","phoneConnected":true,...}

./agent.sh ping
# Expected: {"type":"pong","deviceInfo":{"manufacturer":"OnePlus",...},...}
```

---

## 5. The Agent Helper Script (agent.sh)

Location: `/home/z/my-project/mini-services/agent-bridge/agent.sh`

### Usage

```bash
# Set the relay URL (or use the default)
export RELAY_URL="wss://anikuta-relay.k-h-u-r-r-a-m-n-o-o-r88888888888.workers.dev/"

cd /home/z/my-project/mini-services/agent-bridge

# Commands:
./agent.sh ping                     # Smoke test → returns device info
./agent.sh get_state                # UI tree + screenshot
./agent.sh screenshot               # Screenshot only
./agent.sh back                     # Press Back button
./agent.sh home                     # Press Home button
./agent.sh push_route library       # Navigate to Library page
./agent.sh tap 540 1200             # Tap at coordinates
./agent.sh swipe 540 1800 540 400   # Swipe (scroll down)
./agent.sh db_tables                # List all DB tables
./agent.sh relay_status             # Check relay + phone status

# Send raw JSON (any of the 30 command types):
./agent.sh send '{"type":"db_query","id":"q1","table":"content","limit":10}'

# Read stored results:
cat data/results/<commandId>.json

# View screenshot path:
./agent.sh shot <commandId>
# → data/screenshots/<commandId>.png
```

### Output format

- **stdout**: The TestResult JSON (for the agent to read + parse).
- **stderr**: Diagnostic messages (command sent, result received, etc.).
- **File**: `data/results/<id>.json` — the result JSON (for direct Read tool access).
- **File**: `data/screenshots/<id>.png` — the screenshot PNG (for VLM analysis).

---

## 6. Complete Command Reference (30 commands)

### Session / Control (5 commands)

| Command | Example | Returns |
|---|---|---|
| `ping` | `{"type":"ping","id":"p1"}` | `pong` with `deviceInfo` (manufacturer, model, SDK, screen, ABIs, appVersion) + `navKey` (current screen) |
| `get_device_info` | `{"type":"get_device_info","id":"d1"}` | Same as pong |
| `keep_screen_on` | `{"type":"keep_screen_on","id":"k1","enabled":true}` | `ok` — toggles `FLAG_KEEP_SCREEN_ON` on the foreground Activity |
| `wait` | `{"type":"wait","id":"w1","ms":1000}` | `ok` (after delay) |
| `restart_app` | `{"type":"restart_app","id":"r1"}` | `ok` (process killed + relaunched) |

### UI Inspection (3 commands)

| Command | Example | Returns |
|---|---|---|
| `get_state` | `{"type":"get_state","id":"s1","includeScreenshot":true}` | `state` with `navKey`, `packageName`, `tree` (NodeInfo with bounds/text/clickable/actions), `hasScreenshot` |
| `find_nodes` | `{"type":"find_nodes","id":"f1","text":"Play"}` | `nodes` with matching NodeInfo list |
| `screenshot` | `{"type":"screenshot","id":"sh1"}` | `screenshot_ref` + JPEG binary (saved to `data/screenshots/<id>.png`) |

### UI Interaction (8 commands)

| Command | Example | Returns |
|---|---|---|
| `tap` | `{"type":"tap","id":"t1","nodeId":37}` or `{"type":"tap","id":"t1","x":540,"y":1200}` | `ok` |
| `long_click` | `{"type":"long_click","id":"l1","nodeId":5}` or `{"type":"long_click","id":"l1","x":540,"y":1200,"durationMs":800}` | `ok` |
| `swipe` | `{"type":"swipe","id":"sw1","x1":540,"y1":1800,"x2":540,"y2":400,"durationMs":300}` | `ok` |
| `scroll` | `{"type":"scroll","id":"sc1","x":540,"y":1500,"direction":"DOWN","amount":1}` | `ok` (⚠️ use `swipe` instead — more reliable) |
| `set_text` | `{"type":"set_text","id":"st1","nodeId":16,"text":"Frieren"}` | `ok` |
| `back` | `{"type":"back","id":"b1"}` | `ok` |
| `home` | `{"type":"home","id":"h1"}` | `ok` |
| `recents` | `{"type":"recents","id":"r1"}` | `ok` |
| `notifications` | `{"type":"notifications","id":"n1"}` | `ok` |

### Navigation (4 commands)

| Command | Example | Returns |
|---|---|---|
| `push_route` | `{"type":"push_route","id":"pr1","route":"library"}` | `ok` |
| `pop` | `{"type":"pop","id":"po1"}` | `ok` |
| `clear_to_root` | `{"type":"clear_to_root","id":"cr1","root":"browse"}` | `ok` |
| `get_backstack` | `{"type":"get_backstack","id":"gb1"}` | `backstack` with key names |

**Supported routes**: `browse`, `library`, `search`, `more`, `profile`, `settings`, `notifications`, `notifications_library`, `updates_settings`, `update_categories`, `appearance`, `appearance_general`, `episode_settings`, `player_settings`, `downloads`, `downloaded_files`, `download_settings`, `extensions_settings`, `auto_link_settings`, `extension_repo_settings`, `extension_detail` (args: `pkgName`), `source_preferences` (args: `sourceId`), `history`, `updates`, `anime_details_anilist` (args: `animeId`), `anime_details_extension` (args: `sourceId`, `animeUrl`, `title`), `test_controller_settings`.

### App Internals (9 commands)

| Command | Example | Returns |
|---|---|---|
| `get_logcat` | `{"type":"get_logcat","id":"gl1","lines":200,"filter":null,"level":"WARN"}` | `logcat` with LogEntry list (timestamp, level, tag, message) |
| `get_network_logs` | `{"type":"get_network_logs","id":"gn1","lines":50}` | `network_logs` with NetworkLogEntry list |
| `get_activity_logs` | `{"type":"get_activity_logs","id":"ga1","lines":50,"eventType":"WATCH_START"}` | `activity_logs` with ActivityEventSummary list |
| `db_list_tables` | `{"type":"db_list_tables","id":"dt1"}` | `tables` with TableSummary list (name + rowCount) |
| `db_query` | `{"type":"db_query","id":"dq1","table":"content","limit":10,"offset":0}` | `rows` with columns + rows |
| `db_query_sql` | `{"type":"db_query_sql","id":"ds1","sql":"SELECT * FROM content WHERE title LIKE '%Re%'"}` | `rows` (arbitrary SELECT, read-only) |
| `db_count` | `{"type":"db_count","id":"dc1","table":"library_item"}` | `count` |
| `get_preference` | `{"type":"get_preference","id":"gp1","key":"debug.test.relay_url"}` | `preference` with value |
| `set_preference` | `{"type":"set_preference","id":"sp1","key":"some.key","value":"some.value"}` | `preference` |

---

## 7. How to Navigate the App

### Method 1: Programmatic navigation (fast, reliable)

Use `push_route` to navigate directly to a named route. This bypasses the UI (no taps needed) + is the fastest way to get to a specific screen:

```bash
./agent.sh push_route browse      # Home page
./agent.sh push_route library     # Library page
./agent.sh push_route search      # Search page
./agent.sh push_route settings    # Settings page
./agent.sh push_route downloads   # Downloads page
./agent.sh push_route history     # History page
```

### Method 2: Real UI taps (slower, tests the actual UI)

Use `get_state` to find clickable nodes, then `tap` them by `nodeId`:

```bash
# 1. Get the current UI tree
./agent.sh send '{"type":"get_state","id":"s1","includeScreenshot":false}'

# 2. Find the bottom nav tab you want (e.g., Library)
# The tree will show clickable nodes with nodeId + bounds

# 3. Tap it
./agent.sh send '{"type":"tap","id":"t1","nodeId":37}'

# 4. Press Back to return
./agent.sh back
```

### Method 3: Open a specific anime's details page

```bash
# Navigate to an AniList anime by ID
./agent.sh send '{"type":"push_route","id":"pr1","route":"anime_details_anilist","args":{"animeId":189046}}'

# Navigate to an extension anime by sourceId + URL
./agent.sh send '{"type":"push_route","id":"pr2","route":"anime_details_extension","args":{"sourceId":"123","animeUrl":"https://example.com/anime","title":"Title"}}'
```

---

## 8. How to Interact with UI Elements

### Step 1: Get the UI tree

```bash
./agent.sh send '{"type":"get_state","id":"s1","includeScreenshot":false}'
```

The result contains a `tree` field — a recursive `NodeInfo` structure:

```json
{
  "nodeId": 0,
  "text": null,
  "contentDescription": null,
  "className": "android.view.View",
  "bounds": {"left": 0, "top": 0, "right": 1080, "bottom": 2297},
  "isClickable": false,
  "isScrollable": true,
  "children": [
    {
      "nodeId": 8,
      "text": null,
      "bounds": {"left": 160, "top": 600, "right": 400, "bottom": 900},
      "isClickable": true,
      "children": []
    },
    ...
  ]
}
```

### Step 2: Find the element you want

Look for nodes with `isClickable: true`. The `nodeId` is a short-lived integer — valid only until the next `get_state` call. The `bounds` give you the screen coordinates.

### Step 3: Tap / set text / scroll

```bash
# Tap by nodeId (preferred — semantic, triggers the app's click handler)
./agent.sh send '{"type":"tap","id":"t1","nodeId":8}'

# Tap by coordinates (universal fallback — works even if the tree is stale)
./agent.sh send '{"type":"tap","id":"t2","x":280,"y":750}'

# Type text into an editable field (requires a valid nodeId from the current tree)
./agent.sh send '{"type":"set_text","id":"st1","nodeId":16,"text":"Frieren"}'

# Swipe (for scrolling — use this instead of the `scroll` command)
./agent.sh swipe 540 1800 540 400   # swipe up = scroll down
```

### Important: NodeIds are short-lived

After ANY UI change (navigation, typing, async content load), the nodeIds from the previous `get_state` are **stale**. Always call `get_state` again before tapping after a UI change.

---

## 9. How to Read App Internals (DB, logs, network)

### Database queries

```bash
# List all tables + row counts
./agent.sh db_tables

# Query a specific table
./agent.sh send '{"type":"db_query","id":"q1","table":"library_item","limit":50}'

# Run arbitrary SQL (read-only, validated — no INSERT/UPDATE/DELETE)
./agent.sh send '{"type":"db_query_sql","id":"q2","sql":"SELECT c.title, a.anilist_id, a.score FROM library_item li JOIN content c ON li.main_id = c.main_id LEFT JOIN anilist_detail a ON c.main_id = a.main_id"}'

# Count rows
./agent.sh send '{"type":"db_count","id":"c1","table":"content"}'
```

### Logcat (app's Logger output)

```bash
# Get last 200 log lines
./agent.sh send '{"type":"get_logcat","id":"l1","lines":200}'

# Filter by tag or message
./agent.sh send '{"type":"get_logcat","id":"l2","lines":50,"filter":"Test"}'

# Filter by level (VERBOSE/DEBUG/INFO/WARN/ERROR)
./agent.sh send '{"type":"get_logcat","id":"l3","lines":50,"level":"WARN"}'
```

### Network logs (OkHttp requests)

```bash
# Get last 50 network requests
./agent.sh send '{"type":"get_network_logs","id":"n1","lines":50}'
```

### Activity tracker events

```bash
# Get last 50 activity events (APP_OPEN, WATCH_START, LIBRARY_ADD, etc.)
./agent.sh send '{"type":"get_activity_logs","id":"a1","lines":50}'

# Filter by event type
./agent.sh send '{"type":"get_activity_logs","id":"a2","lines":50,"eventType":"LIBRARY_ADD"}'
```

### Preferences

```bash
# Read a preference
./agent.sh send '{"type":"get_preference","id":"p1","key":"debug.test.relay_url"}'

# Write a preference
./agent.sh send '{"type":"set_preference","id":"p2","key":"some.key","value":"some.value"}'
```

---

## 10. How to Take + Analyze Screenshots

### Capture a screenshot

```bash
# Screenshot only
./agent.sh screenshot
# → saved to data/screenshots/<id>.png

# Get state + screenshot (includes the UI tree)
./agent.sh get_state
# → result JSON printed to stdout
# → screenshot saved to data/screenshots/<id>.png
```

### Analyze with VLM (vision language model)

```bash
# Describe what's on screen
z-ai vision -p "Describe what's on this screen of an anime streaming app" -i "./data/screenshots/<id>.png"

# Find UI element coordinates
z-ai vision -p "Where is the search bar? Give (x,y) center coordinates (screen is 1080x2297)" -i "./data/screenshots/<id>.png"

# Compare two screenshots
z-ai vision -p "Did the page scroll? Compare these two screenshots." -i "./before.png" -i "./after.png"

# Save VLM output to a file
z-ai vision -p "What anime are visible?" -i "./data/screenshots/<id>.png" -o /tmp/vlm-result.json
python3.13 -c "import json; d=json.load(open('/tmp/vlm-result.json')); print(d['choices'][0]['message']['content'])"
```

### Screenshot coordinate system

The phone screen is **1080×2297 pixels** (OnePlus KB2001, density 3.0). Screenshots are downscaled to max 1080px wide (JPEG q70). When the VLM gives coordinates, they're in the **screenshot's coordinate space** — you may need to scale them to screen coordinates:

```python
# Screenshot is 486x1080 (downscaled from 1080x2297)
# Scale factor: 2297 / 1080 = 2.127
# VLM y=946 → screen y = 946 * 2.127 = 2012
```

However, if you use `get_state` + tap by `nodeId`, you don't need to calculate coordinates at all — the accessibility tree gives you exact screen coordinates in the `bounds` field.

---

## 11. Credentials + Configuration

### ⚠️ Does the AI agent need the Cloudflare token?

**NO.** The AI agent does **NOT** need the Cloudflare API token to perform autonomous tests. The agent only needs:
1. The **relay URL** (`wss://anikuta-relay.k-h-u-r-r-a-m-n-o-o-r88888888888.workers.dev/`) — hardcoded in `agent.sh`
2. Python 3.13 + the `websockets` library (already in the sandbox)
3. The `agent.sh` + `ws-agent.py` scripts

The agent connects to the relay as a **WebSocket client** — no authentication needed (the relay is a public endpoint). The Cloudflare API token is **only** needed for:
- Deploying/redeploying the Worker (`wrangler deploy`)
- Viewing real-time logs (`wrangler tail`)
- Deleting the Worker

### Cloudflare Workers relay

- **Relay URL**: `wss://anikuta-relay.k-h-u-r-r-a-m-n-o-o-r88888888888.workers.dev/`
- **Health check**: `https://anikuta-relay.k-h-u-r-r-a-m-n-o-o-r88888888888.workers.dev/state`
- **Cloudflare account**: `K.h.u.r.r.a.m.n.o.o.r88888888888@gmail.com`
- **Account ID**: `b073e9f3898336783a15aa371381f96e`
- **Worker name**: `anikuta-relay`
- **API token**: Stored in `/home/z/my-project/mini-services/cf-relay/.env` (sandbox-only, NOT committed to the repo — GitHub's secret scanning blocks it). Also backed up in the zip at `ANI-KUTA/REFERENCES/test-controller-sandbox/test-controller-mini-services.zip` (but the .env file is excluded from the zip — the user must provide the token if the sandbox is cleared).
  - Template: "Edit Cloudflare Workers"
  - Permissions: Workers Scripts (Edit), Account (Read), Zone resources: All zones

### GitHub (for CI builds)

- **Repo**: `https://github.com/testplay-byte/ANI-KUTA`
- **Branch**: `TEST_BETA_FEATURE` (all test-controller Android code is here, NOT on `main`)
- **Dashboard**: Pushed to `main` (deployed to GitHub Pages at `https://testplay-byte.github.io/ANI-KUTA/test-controller/`)
- **GitHub token**: Stored in `/home/z/my-project/.git-credentials` (sandbox-only). Used for: polling CI status, downloading artifacts, pushing commits.
- **CI workflows**:
  - `build-apk.yml` — builds the debug APK (triggers on `main`, `feature/**`, `TEST_BETA_FEATURE`)
  - `deploy-dashboard.yml` — deploys the dashboard to GitHub Pages (triggers on `main`)

### Agent-side environment

```bash
# Set these in your shell (or they default to the correct values):
export RELAY_URL="wss://anikuta-relay.k-h-u-r-r-a-m-n-o-o-r88888888888.workers.dev/"

# Python 3.13 is at:
which python3.13
# /usr/bin/python3.13

# The agent helper is at:
ls /home/z/my-project/mini-services/agent-bridge/agent.sh
```

### Phone-side configuration

The phone needs **NO configuration** — the relay URL is hardcoded in the app (`WsRelayClient.DEFAULT_RELAY_URL`). The user just needs to:
1. Install the debug APK
2. Enable the AccessibilityService (Settings → Accessibility → ANI-KUTA Test Controller → ON)
3. Open the app

The user CAN optionally override the relay URL in More → Settings → Test Controller (useful for testing a local relay during development).

---

## 12. How to Build + Install the APK

### Step 1: Trigger a CI build

```bash
# Push to TEST_BETA_FEATURE (CI triggers automatically)
cd /home/z/my-project/ani-kuta-repo
git add -A && git commit -m "..." && git push origin TEST_BETA_FEATURE
```

### Step 2: Wait for CI to complete

```bash
# Poll CI status
curl -s -H "Authorization: token $GH_TOKEN" \
  "https://api.github.com/repos/testplay-byte/ANI-KUTA/actions/runs?branch=TEST_BETA_FEATURE&per_page=1" \
  | python3.13 -c "import json,sys; r=json.load(sys.stdin)['workflow_runs'][0]; print(f'{r[\"status\"]} | {r[\"conclusion\"] or \"-\"}')"
```

### Step 3: Download the APK

The APK is available as a CI artifact:
- Go to: https://github.com/testplay-byte/ANI-KUTA/actions
- Click the latest "Build APK" run on `TEST_BETA_FEATURE`
- Scroll to **Artifacts** → download `anikuta-apk` (zip file)
- Unzip → `app-debug.apk`

### Step 4: Install on the phone

Send the APK to the user. They install it over the existing app (it's signed with the debug keystore, so no uninstall needed).

### Step 5: Enable AccessibilityService (if not already)

Settings → Accessibility → ANI-KUTA Test Controller → ON.

If the service was already enabled but gestures aren't working (after a code change), toggle it OFF → wait 3s → ON to force the system to re-read the XML config.

---

## 13. How to Deploy the Cloudflare Relay

The relay is already deployed at `wss://anikuta-relay.k-h-u-r-r-a-m-n-o-o-r88888888888.workers.dev/`. You only need to redeploy if you change the Worker code.

### Redeploy

```bash
cd /home/z/my-project/mini-services/cf-relay
export CLOUDFLARE_API_TOKEN="<see /home/z/my-project/mini-services/cf-relay/.env>"
export CLOUDFLARE_ACCOUNT_ID="b073e9f3898336783a15aa371381f96e"
npx wrangler deploy
```

### Worker code location

```
/home/z/my-project/mini-services/cf-relay/
├── wrangler.toml     # Cloudflare config (DO binding, migration)
├── package.json      # wrangler + @cloudflare/workers-types
├── tsconfig.json     # TypeScript config
└── src/
    └── index.ts      # Worker (router) + RelayRoom (Durable Object with Hibernation API)
```

### Verify deployment

```bash
# Health check
curl https://anikuta-relay.k-h-u-r-r-a-m-n-o-o-r88888888888.workers.dev/state
# Expected: {"relay":"running","phoneConnected":true/false,...}

# View real-time logs
cd /home/z/my-project/mini-services/cf-relay
npx wrangler tail
```

---

## 14. Troubleshooting

### `timeout — no result received` (30s)

1. Check relay status: `./agent.sh relay_status` → is `phoneConnected: true`?
2. If false: ask the user to open the app (the phone auto-connects on launch).
3. If true but still times out: the phone's WebSocket might be stale. Ask the user to:
   - Close the app (swipe away from recents)
   - Reopen it
   - Keep the screen on + app in foreground
4. Check relay logs for errors:
   ```bash
   cd /home/z/my-project/mini-services/cf-relay
   npx wrangler tail
   ```

### `NO_PHONE` error

The relay is running but no phone is connected. Ask the user to:
1. Enable the AccessibilityService (Settings → Accessibility → ANI-KUTA Test Controller).
2. Open the app (triggers the app-open health-check).
3. Check More → Settings → Test Controller — status should be "Connected".

### Empty accessibility tree (`tree: {nodeId: 0, children: []}`)

This means `getRootInActiveWindow()` returned null. Causes:
1. **Service just connected** — wait 2s + try again.
2. **App not in foreground** — ask the user to bring the app to the foreground.
3. **XML config not loaded** — the manifest meta-data name must be `android.accessibilityservice` (NOT `android.accessibilityservice.accessibility-service-file`). This was a bug that was fixed in commit 82f29128.
4. **AccessibilityService toggled off/on too quickly** — wait 3s between OFF + ON.

### Gestures (tap/swipe) time out

1. **`canPerformGestures` not set** — ensure the XML config has `canPerformGestures="true"`. The manifest must reference it correctly (see above).
2. **OnePlus ROM bug** — on some custom ROMs, `dispatchGesture` returns `true` but the callback never fires. The GestureExecutor has a 10s timeout — it returns `false` instead of hanging forever.
3. **Use `performAction(ACTION_CLICK)` instead** — tap by `nodeId` uses `performAction` (semantic click), which is more reliable than `dispatchGesture` (physical gesture).

### `STALE_SNAPSHOT` error

The `nodeId` from a previous `get_state` is no longer valid (the UI changed). Re-send `get_state` to get fresh nodeIds, or use `{x, y}` coordinates instead.

### Preview dot (green marker) appears offset from the actual tap

The actual tap (via `dispatchGesture`) lands correctly, but the green preview dot appears shifted (usually DOWN by the status bar height).

**Cause:** `dispatchGesture` uses **raw screen coordinates** (origin = physical screen top-left, including status bar), but `WindowManager.LayoutParams` with `Gravity.TOP | Gravity.START` (default) uses **content-area coordinates** (origin = below the status bar). The screenshot includes the status bar, so the dashboard sends coordinates in raw screen space — the tap consumer matches, but the overlay consumer didn't.

**Fix (v5.9):** The overlay's `LayoutParams` now includes `FLAG_LAYOUT_IN_SCREEN`, which makes its `(x, y)` origin the physical screen's top-left — matching `dispatchGesture`. See `ActionPreviewOverlay.createOverlayParams()`.

### `NAV_NOT_BOUND` error

`DebugNavRegistry.current` is null — the Compose `AppRoot` hasn't composed yet, or the app is in a release build. Ensure the app is in the foreground + it's a debug build.

### App crash ("Something went wrong" screen)

Check the crash log. Common causes:
1. **Serialization conflict** — if a `TestResult` subtype has a field named `type`, it conflicts with the polymorphic discriminator. Use `errorCode` instead (fixed in commit e77d1449).
2. **Database query error** — ensure the table name is valid (use `db_list_tables` to check).
3. **Null pointer in the executor** — check the logcat for the stack trace.

---

## 15. Known Limitations + Workarounds

| Limitation | Workaround |
|---|---|
| `scroll` command doesn't work (returns success but page doesn't scroll) | Use `swipe` instead: `./agent.sh swipe 540 1800 540 400` |
| NodeIds are stale after UI changes | Call `get_state` again before tapping after any UI change (navigation, typing, async load) |
| Screenshot coordinates from VLM may be in downscaled space | Use `nodeId`-based taps (from the accessibility tree) instead of coordinate-based taps |
| Extension traffic not captured in network logs | Extensions use a separate Injekt OkHttpClient — only the app's own OkHttp traffic is logged |
| Phone disconnects when screen sleeps | Use `keep_screen_on` command: `{"type":"keep_screen_on","id":"k1","enabled":true}` |
| Bottom nav wipes backstack on tab switch | The bottom nav does `backstack.clear() + backstack.add(rootKey)`. Re-query the backstack after tab switches. |
| `testTagsAsResourceId` only works in debug builds | Gated by `BuildConfig.DEBUG`. In release builds, Compose testTags don't surface as accessibility IDs. |

---

## 16. File Map (Everything's Location)

### Android (in the ANI-KUTA repo, branch `TEST_BETA_FEATURE`)

```
ANI-KUTA/APP/ani-kuta/
├── core/test-api/src/main/java/.../core/testapi/
│   ├── TestControllerConstants.kt     # Relay URL, settings keys, screenshot config
│   ├── TestCommand.kt                 # Sealed class: 30 command types
│   ├── TestResult.kt                  # Sealed class: 15 result types + models
│   ├── DebugNavRegistry.kt            # Singleton: binds Compose backstack
│   ├── DebugWindowRegistry.kt         # Singleton: binds foreground Activity window
│   ├── AppRouteRegistry.kt            # Interface: route name → NavKey
│   └── TestControllerSettingsKey.kt   # NavKey for the settings screen
├── core/test-controller/src/main/java/.../core/testcontroller/
│   ├── TestAccessibilityService.kt    # AccessibilityService (lifecycle, Koin deps)
│   ├── WsRelayClient.kt               # OkHttp WebSocket (single-flight, auto-reconnect)
│   ├── TestControllerStatus.kt        # Singleton: app-open health-check + toggle
│   ├── TestToaster.kt                 # Throttled toast helper
│   ├── TestControllerExecutor.kt      # Command dispatcher (30 handlers + toast labels)
│   ├── AccessibilityTreeSerializer.kt # Node tree → JSON + nodeId registry
│   ├── GestureExecutor.kt             # dispatchGesture + performAction (10s timeout)
│   ├── ScreenshotCapture.kt           # PixelCopy (all API levels, 10s timeout)
│   ├── NavExecutor.kt                 # push_route/pop/clear_to_root
│   ├── DeviceInfoProvider.kt          # Build constants → DeviceInfo
│   ├── LogcatProvider.kt              # DebugLogBuffer → LogEntry list
│   ├── NetworkLogsProvider.kt         # DebugNetworkStats → NetworkLogEntry list
│   ├── ActivityLogsProvider.kt        # AnikutaDatabase → ActivityEventSummary list
│   ├── DatabaseProvider.kt            # Read-only SQLiteDatabase queries
│   └── PreferencesProvider.kt         # SettingsRepository get/set
├── app/src/debug/java/.../anikuta/
│   ├── AppRouteRegistryImpl.kt        # Route name → NavKey mapping
│   ├── DebugNavBinder.kt              # Binds backstack to DebugNavRegistry
│   ├── TestControllerSettingsScreen.kt # Compose UI (status, toggle, URL, a11y button)
│   └── DebugInit.kt                   # Koin + ActivityLifecycleCallbacks
├── app/src/release/java/.../anikuta/
│   ├── DebugNavBinder.kt              # No-op mirror
│   └── TestControllerSettingsScreen.kt # No-op mirror
├── app/src/main/java/.../anikuta/
│   ├── MainActivity.kt                # DebugNavBinder call + testTagsAsResourceId + dispatch
│   └── settings/SettingsScreen.kt     # "Test Controller" nav row at top
└── core/test-controller/src/main/
    ├── AndroidManifest.xml            # <service> declaration (debug-only)
    └── res/xml/test_controller_service_config.xml  # canPerformGestures, canRetrieveWindowContent
```

### Sandbox (NOT in the ANI-KUTA repo)

```
/home/z/my-project/
├── src/app/api/test-relay/route.ts    # Next.js API route (legacy relay — not used with Cloudflare)
├── mini-services/
│   ├── agent-bridge/
│   │   ├── ws-agent.py                # One-shot Python WebSocket client
│   │   ├── agent.sh                   # Bash helper (ping, get_state, tap, etc.)
│   │   ├── mqtt-agent.py              # Legacy MQTT client (not used)
│   │   └── data/
│   │       ├── results/<id>.json      # Result JSONs (direct Read tool access)
│   │       └── screenshots/<id>.png   # Screenshot PNGs (for VLM analysis)
│   └── cf-relay/
│       ├── wrangler.toml              # Cloudflare config (DO binding, migration)
│       ├── package.json               # wrangler + @cloudflare/workers-types
│       ├── tsconfig.json              # TypeScript config
│       ├── src/index.ts               # Worker + RelayRoom Durable Object
│       └── README.md                  # Deployment guide
└── package.json                       # Has "ws" dependency (for legacy relay)
```

### Cloudflare (deployed)

- **Worker**: `anikuta-relay` (account `b073e9f3898336783a15aa371381f96e`)
- **URL**: `wss://anikuta-relay.k-h-u-r-r-a-m-n-o-o-r88888888888.workers.dev/`
- **Durable Object**: `RelayRoom` (SQLite-backed, WebSocket Hibernation API)

### Sandbox backup (zip)

The sandbox-side mini-services are backed up as a zip in the repo:
- **Location**: `ANI-KUTA/REFERENCES/test-controller-sandbox/test-controller-mini-services.zip`
- **Contents**: `agent-bridge/` (Python client + agent.sh), `cf-relay/` (Worker code), `nextjs-api/` (legacy), `RESTORE.md`
- **Excluded**: `node_modules/`, `data/` (results/screenshots), `.env` (tokens)
- **Restore**: unzip to `/home/z/my-project/` + follow `RESTORE.md`

---

## 17. How to Remove Before Publish

The test-controller is entirely debug-only. To remove:

1. **Delete the 2 modules**: `:core:test-api` + `:core:test-controller` (from `settings.gradle.kts` + the filesystem).
2. **Remove from `:app/build.gradle.kts`**:
   - `debugImplementation(project(":core:test-controller"))`
   - `implementation(project(":core:test-api"))`
3. **Remove from `MainActivity.kt`**:
   - `DebugNavBinder(backstack)` call
   - `Modifier.semantics { testTagsAsResourceId = true }` modifier
   - `is TestControllerSettingsKey -> TestControllerSettingsScreen(onBack = pop)` dispatch branch
4. **Remove from `SettingsScreen.kt`**: the "Test Controller" nav row + the `onOpenTestController` parameter.
5. **Remove from `:app/src/debug/DebugInit.kt`**: the `AppRouteRegistry` Koin binding + the `TestControllerStatus.ensureConnected()` call.
6. **Delete `:app/src/debug/` files**: `AppRouteRegistryImpl.kt`, `DebugNavBinder.kt`, `TestControllerSettingsScreen.kt`.
7. **Delete `:app/src/release/` files**: `DebugNavBinder.kt`, `TestControllerSettingsScreen.kt`.
8. **Delete the sandbox files**: `mini-services/agent-bridge/`, `mini-services/cf-relay/`, `src/app/api/test-relay/`.
9. **Revert CI trigger changes**: remove `TEST_BETA_FEATURE` from `build-apk.yml` + `deploy-dashboard.yml`.
10. **Delete the Cloudflare Worker** (optional):
    ```bash
    export CLOUDFLARE_API_TOKEN="<see /home/z/my-project/mini-services/cf-relay/.env>"
    npx wrangler delete --name anikuta-relay
    ```

Release builds already contain **zero test-controller code** (it's all `debugImplementation`).

---

## Appendix A: Design Decisions (D-197 through D-202, D-198 v4)

| Decision | What | Why |
|---|---|---|
| **D-197** | Module structure: `:core:test-api` (types) + `:core:test-controller` (impl, debug-only) | Mirrors the debug-bubble pattern. Zero release code. |
| **D-198 v4** | Cloudflare Workers + Durable Objects relay | Stable URL, always-on, port 443, $0/month, 32MB payload. Replaced ntfy (v1, rate limits), MQTT (v2, carrier blocks), Next.js relay (v3, dies on server restart). |
| **D-199** | AccessibilityService + triple addressing (nodeId / coordinates / text query) | Standard Android UI-automation API. Works at runtime without instrumentation. |
| **D-200** | PixelCopy screenshots (all API levels) | `takeScreenshot` was removed in SDK 36. PixelCopy works on API 24+, captures SurfaceView (MPV video). |
| **D-201** | Reuse debug-bubble facilities (DebugLogBuffer, DebugNetworkStats, DebugDatabaseBrowser) | No code duplication. Same data as the debug bubble. |
| **D-202** | Debug-only removability | `debugImplementation` + debug manifest + debug source-set. Delete 2 modules + 3 files to remove. |
| **D-198 v5.9** | ActionPreviewOverlay `FLAG_LAYOUT_IN_SCREEN` fix | The green tap-dot was offset DOWN by the status bar height because `WindowManager.LayoutParams` (default) uses content-area coordinates (origin below status bar), while `dispatchGesture` uses raw screen coordinates (origin = physical screen top-left, includes status bar). Adding `FLAG_LAYOUT_IN_SCREEN` aligns the overlay's coordinate system with `dispatchGesture`. Also upgraded the dot to a ring + center-dot design (white stroke border for visibility on any background). |

### D-198 evolution (transport)

```
v1: ntfy.sh + Bun HTTP relay → FAILED (250 msg/day rate limit, URL discovery problem)
v2: MQTT (HiveMQ/EMQX public brokers) → FAILED (carrier blocks port 8884)
v3: WebSocket relay inside Next.js dev server → WORKED but URL changes per session
v4: Cloudflare Workers + Durable Objects → ✅ STABLE (permanent URL, always-on, $0/month)
```

---

## Appendix B: Testing Workflow for an AI Agent

### Pre-test checklist

```bash
# 1. Verify the relay is running + phone is connected
export RELAY_URL="wss://anikuta-relay.k-h-u-r-r-a-m-n-o-o-r88888888888.workers.dev/"
cd /home/z/my-project/mini-services/agent-bridge
./agent.sh relay_status
# → phoneConnected must be true

# 2. Ping to verify responsiveness
./agent.sh ping
# → must return device info

# 3. Get the current screen
./agent.sh get_state
# → note the navKey (current screen) + the tree
```

### Standard test flow

1. **Navigate** to the screen you want to test (via `push_route` or real taps).
2. **Get the UI tree** (`get_state` with `includeScreenshot: false` for speed).
3. **Find the element** you want to interact with (by text, className, or bounds).
4. **Tap / type / swipe** (by `nodeId` for reliability, or coordinates for fallback).
5. **Verify the result** (screenshot + VLM analysis, or DB query to check state change).
6. **Repeat** for each step of the test scenario.

### Post-test verification

```bash
# Check the library state
./agent.sh send '{"type":"db_query_sql","id":"q1","sql":"SELECT c.title FROM library_item li JOIN content c ON li.main_id = c.main_id"}'

# Check the activity log
./agent.sh send '{"type":"get_activity_logs","id":"a1","lines":10}'

# Take a final screenshot
./agent.sh screenshot
```

### Tips for reliable testing

1. **Always `get_state` before tapping** — nodeIds change after any UI update.
2. **Use `nodeId` taps over coordinate taps** — semantic, more reliable.
3. **Use `swipe` for scrolling** — the `scroll` command doesn't work reliably.
4. **Wait after async operations** — after typing a search query, wait 2-3s for results to load before tapping.
5. **Use VLM for visual verification** — screenshots + VLM analysis confirm the UI state.
6. **Use DB queries for state verification** — faster + more reliable than screenshots for checking if data changed.
7. **Keep the screen on** — send `keep_screen_on` at the start of a long test session.
8. **Handle errors gracefully** — if a command fails, check the error message + retry or use an alternative approach.

---

*This document is the canonical reference for the ANI-KUTA Test Controller. Any AI agent who reads this can autonomously connect to, control, inspect, and test the ANI-KUTA app on the user's phone.*
