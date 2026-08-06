# 07 — Download Settings + Preferences

> All line references: `core/download/src/main/java/app/confused/anikuta/core/download/DownloadPreferences.kt` (204 lines) + `feature/download/src/main/java/app/confused/anikuta/feature/download/DownloadSettingsScreen.kt` (528 lines).

## 1. Storage medium

`DownloadPreferences` wraps `PreferenceStore` (from `:core:preferences`). Each setting is a `Preference<T>` — which exposes:
- `get(): T` — current value.
- `set(value: T)` — write.
- `changes(): Flow<T>` — reactive stream of changes.

The actual storage is `SharedPreferences` (under the hood). The reactive `changes()` Flow is what enables the DownloadSettingsScreen + DownloadViewModel to update live.

The new project's `PreferenceStore` is currently a simple non-reactive wrapper (`com.confused.anikuta.core.preferences.PreferenceStore` — just `getString/putString/getBoolean/...` with no Flows). **This is a gap** — the new project needs either to extend `PreferenceStore` with reactive Flows, OR use a different mechanism (e.g. a `MutableStateFlow` per setting). See `13-implementation-plan.md`.

## 2. Every setting in `DownloadPreferences`

### General
| Setting | Key | Type | Default | UI label |
|---|---|---|---|---|
| Folder URI | `pref_dl_folder_uri` | String | `""` | "Download folder" |
| Method | `pref_dl_method` | Enum `DownloadMethod` | `ADVANCED` | "Download method" (Normal/Advanced toggle) |
| Wi-Fi only | `pref_dl_wifi_only` | Boolean | `true` | "Wi-Fi only" — "Pause downloads on mobile data" |
| Concurrent downloads | `pref_dl_concurrent` | Int | `1` (UI clamps 1..5) | "Concurrent downloads" (slider 1..5) |
| Show download button | `pref_dl_show_button` | Boolean | `true` | "Show download button" — "Display the download icon on episode rows" |

### Auto-download
| Setting | Key | Type | Default | UI label |
|---|---|---|---|---|
| Auto-pick | `pref_dl_auto_pick` | Boolean | `false` | "Automatic video selection" — "Auto-select your preferences" |

### Preference lists (priority-ordered, only used when auto-pick is ON)
| Setting | Key | Type | Default | UI label |
|---|---|---|---|---|
| Quality prefs | `pref_dl_quality_prefs` | `List<String>` | `["1080p", "720p", "480p", "360p"]` | "Preferred quality — drag to re-order" |
| Audio prefs | `pref_dl_audio_prefs` | `List<String>` | `["SUB", "DUB"]` | "Preferred audio — drag to re-order" |
| Server prefs (per source) | `pref_dl_server_prefs` | `Map<String, List<String>>` (sourceId → names) | `{}` (empty) | "Preferred server — per extension" |

### Fallback strategies (only used when auto-pick is ON)
| Setting | Key | Type | Default | UI label |
|---|---|---|---|---|
| Quality fallback | `pref_dl_quality_fallback` | Enum `FallbackStrategy` | `TRY_NEXT` | "If unavailable" (3-way: Try next / Ask / Don't) |
| Audio fallback | `pref_dl_audio_fallback` | Enum `FallbackStrategy` | `TRY_NEXT` | "If unavailable" |
| Server fallback | `pref_dl_server_fallback` | Enum `FallbackStrategy` | `TRY_NEXT` | "If unavailable" |

### Advanced method settings
| Setting | Key | Type | Default (code) | Default (UI clamps) | UI label |
|---|---|---|---|---|---|
| Parallel threads | `pref_dl_adv_threads` | Int | `8` | 1..8 | "Parallel threads" |
| Max retries per chunk | `pref_dl_adv_retries` | Int | `25` ⚠️ | 0..10 | "Max retries per chunk" |
| Min size for multi-threading | `pref_dl_adv_min_size_mb` | Int | `1` (MB) | 1..20 | "Min size for multi-threading" |

⚠️ The `advancedMaxRetries` default value in code is 25 (line 148), but the UI slider clamps to 0..10 (line 169-170 of `DownloadSettingsScreen`). A user who never opens settings gets 25 retries per chunk. **Inconsistency** — should be 10 in both places, or the UI should allow up to 25.

### Enums

**`DownloadMethod`** — line 183-188:
```kotlin
enum class DownloadMethod {
    NORMAL,    // single-threaded OkHttp, no resume
    ADVANCED,  // multi-threaded Range + resume
}
```

**`FallbackStrategy`** — line 200-204:
```kotlin
enum class FallbackStrategy {
    TRY_NEXT,         // try next option in preference list
    ASK,              // show the picker sheet
    DO_NOT_DOWNLOAD,  // show error, don't download
}
```

## 3. The `DownloadSettingsScreen` UI

**File**: `feature/download/src/main/java/app/confused/anikuta/feature/download/DownloadSettingsScreen.kt` (528 lines)

Layout (top to bottom):
1. **CollapsingHeader** — "Download settings"
2. Section 1: "Download method" — Normal/Advanced toggle. When Advanced is selected, the advanced settings (threads / retries / min size) appear below in the SAME section (combined per the owner's request).
3. Section 2: "General" — folder, show-download-button (ABOVE Wi-Fi-only per owner), Wi-Fi-only, concurrent downloads (slider).
4. Section 3: "Auto-download" — toggle. When ON, sections 5-7 appear.
5. Section 4: "Preferred quality" — collapsible. Header "drag to re-order". Expanded: `DragReorderableList` + 3-way fallback toggle.
6. Section 5: "Preferred audio" — collapsible. Same structure.
7. Section 6: "Preferred server" — collapsible. Per-extension, each also collapsible. Empty-state: "No trusted extensions installed."

### Section components

- `SectionContainer(label, content)` — vertical card with uppercase label + surfaceVariant background (line 320-336).
- `CollapsibleSection(title, subtitle, isExpanded, onToggle, content)` — header row + `AnimatedVisibility` content (line 339-372).
- `ToggleRow(title, subtitle, checked, onCheckedChange)` — label + subtitle + `Switch` (line 447-459).
- `SliderRow(label, value, range, steps, valueText, onChange)` — label + value text + `Slider` (line 463-477).
- `SettingsRow(title, subtitle, onClick)` — tappable row (used for the folder picker, line 434-444).
- `FallbackToggle(label, strategy, onSelect)` — 3-way segmented toggle for `TRY_NEXT`/`ASK`/`DO_NOT_DOWNLOAD` (line 484-501).
- `SegmentedRowLocal(options, onSelect)` — generic N-way segmented toggle (line 504-527).
- `CollapsibleExtensionSection(extSource, ...)` — per-extension server list (line 375-431).

### Folder picker integration

**Lines 109-120**:
```kotlin
val folderLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree(),
) { uri ->
    if (uri != null) {
        try {
            preferences.downloadFolderUri().set(uri.toString())
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: Exception) { }
    }
}
```

The `SettingsRow` for the folder (line 193-197):
```kotlin
SettingsRow(
    title = "Download folder",
    subtitle = if (folderName != null) "Folder: $folderName" else "Not set — tap to choose",
    onClick = { folderLauncher.launch(null) },
)
```

Note: `folderName` comes from `DownloadStorageProvider.folderDisplayName(folderUri)` — a static helper that parses the SAF tree URI's last segment.

### Concurrent downloads slider

**Line 212-219**:
```kotlin
SliderRow(
    label = "Concurrent downloads",
    value = concurrent.toFloat(),
    range = 1f..5f,
    steps = 3,  // 4 discrete values: 1, 2, 3, 4, 5
    valueText = "$concurrent",
    onChange = { preferences.concurrentDownloads().set(it.toInt().coerceIn(1, 5)) },
)
```

**Note**: setting this pref does NOT automatically call `DownloadQueue.refreshConcurrency()` — the new limit only takes effect on app restart (when `DownloadQueue` is reconstructed) or when a task happens to complete (triggering `tryStartNext` with the old Semaphore). **Minor bug** — see `02-queue-management.md` §5.

### Advanced method sliders

**Lines 154-181**:
```kotlin
AnimatedVisibility(visible = downloadMethod == DownloadMethod.ADVANCED, ...) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        SliderRow(
            label = "Parallel threads",
            value = advThreads.toFloat(),
            range = 1f..8f,
            steps = 6,  // 7 discrete values: 1, 2, 3, 4, 5, 6, 7, 8
            valueText = "$advThreads",
            onChange = { preferences.advancedThreadCount().set(it.toInt().coerceIn(1, 8)) },
        )
        SliderRow(
            label = "Max retries per chunk",
            value = advRetries.toFloat(),
            range = 0f..10f,
            steps = 9,  // 10 discrete values: 0..10
            valueText = "$advRetries",
            onChange = { preferences.advancedMaxRetries().set(it.toInt().coerceIn(0, 10)) },
        )
        SliderRow(
            label = "Min size for multi-threading",
            value = advMinSize.toFloat(),
            range = 1f..20f,
            steps = 18,  // 19 discrete values: 1..20
            valueText = "$advMinSize MB",
            onChange = { preferences.advancedMinSizeMb().set(it.toInt().coerceIn(1, 20)) },
        )
    }
}
```

### Preferred quality / audio sections

When `autoDownload` is ON, the collapsible sections appear. Each contains:
- A `DragReorderableList` (see `08-downloads-page-ui.md` §5 for component details).
- A `FallbackToggle` 3-way segmented control.

### Preferred server section (per extension)

The host (MainActivity) passes `extensionSources: List<ExtensionSourceInfo>` into the screen. Each extension gets a `CollapsibleExtensionSection`:
- Lists discovered servers (from `ServerDiscoveryStore.serverMap`).
- Merges with user's saved order (preserving user's manual reorder + appending new discoveries).
- Empty state: "No servers discovered yet. Browse or watch anime from this source to discover servers."

```kotlin
// DownloadSettingsScreen.kt:386
val merged = (userOrder.filter { it in discovered } + discovered.filter { it !in userOrder }).distinct()
```

`ExtensionSourceInfo` DTO (line 12-16 of `ExtensionSourceInfo.kt`):
```kotlin
data class ExtensionSourceInfo(
    val sourceId: Long,
    val sourceName: String,
    val extensionName: String,
)
```

Lives in `:feature:download` to avoid a `:feature:download → :data:extension` dependency (the host maps from `AnimeExtension.Installed` → this DTO).

## 4. `ServerDiscoveryStore` — passive server recording

**File**: `core/download/src/main/java/app/confused/anikuta/core/download/ServerDiscoveryStore.kt` (83 lines)

Server names (e.g. "Vidstreaming", "Streamtape", "Beta Server") are only known AFTER resolving a video for a specific episode — they're not in the extension metadata. `ServerDiscoveryStore` records them passively every time `DownloadOrchestrator` resolves a video:

```kotlin
// ServerDiscoveryStore.kt:66-78
fun recordServers(sourceId: Long, serverNames: List<String>) {
    if (serverNames.isEmpty()) return
    val key = sourceId.toString()
    val current = serverMapPref.get().toMutableMap()
    val existing = current[key] ?: emptyList()
    val merged = (existing + serverNames.filter { it !in existing }).distinct()
    if (merged != existing) {
        current[key] = merged
        serverMapPref.set(current)
    }
}
```

- Merges: existing first (preserves user's manual reorder), then new ones appended.
- Reactive via `serverMap: Flow<Map<String, List<String>>>` — the settings UI updates live.
- Key: `pref_dl_server_discovery_v1`.

Called from `DownloadOrchestrator.enqueueDownload` line 84:
```kotlin
serverDiscovery.recordServers(source.id, result.servers.map { it.name })
```

So servers are recorded only when the user attempts to download (or watch). Over time, this builds a per-source map of known servers.

## 5. Where preferences are read reactively

| Reader | What it reads | How |
|---|---|---|
| `DownloadQueue` | `concurrentDownloads()`, `method()` (indirectly via `HttpDownloader`), `wifiOnly()` (via `DefaultDownloadManager.isNetworkAllowed`) | Direct `.get()` calls (NOT reactive) |
| `DefaultDownloadManager` | `wifiOnly()` | Direct `.get()` (called on every `tryStartNext`) |
| `HttpDownloader` | `method()` | Direct `.get()` per download |
| `AdvancedHttpDownloader` | `advancedThreadCount()`, `advancedMaxRetries()`, `advancedMinSizeMb()` | Direct `.get()` per download |
| `DownloadOrchestrator` | `autoDownload()`, `qualityPreferences()`, `audioPreferences()`, `serverPreferences()`, `qualityFallback()`, `audioFallback()` | Direct `.get()` per enqueue |
| `DownloadViewModel` | `downloadFolderUri()` | `changes()` Flow — drives the `folderReady` flag in UI state |
| `DownloadSettingsScreen` | All settings | `changes()` Flow per setting — drives live UI updates |

So settings are reactive in the UI but read-once-per-action in the engine. The engine would pick up a new value the next time it's read (e.g. the next `tryStartNext` call for Wi-Fi-only).

## 6. UI screenshot (textual)

```
┌────────────────────────────────────────────┐
│  Download settings                         │  ← CollapsingHeader
├────────────────────────────────────────────┤
│  DOWNLOAD METHOD                           │
│  ┌──────────────────────────────────────┐  │
│  │ [ Normal ] [ Advanced ✓ ]            │  │  ← SegmentedRowLocal
│  │  Parallel threads          8         │  │  ← SliderRow (only if Advanced)
│  │  ───●──────────────────────────      │  │
│  │  Max retries per chunk     10        │  │
│  │  ─────●──────────────────────        │  │
│  │  Min size for multi-threading 1 MB   │  │
│  │  ●────────────────────────────       │  │
│  └──────────────────────────────────────┘  │
├────────────────────────────────────────────┤
│  GENERAL                                   │
│  ┌──────────────────────────────────────┐  │
│  │ Download folder                      │  │
│  │ Folder: AniKuta Downloads          › │  │
│  │──────────────────────────────────────│  │
│  │ Show download button             [✓] │  │  ← ToggleRow
│  │ Display the download icon on episodes│  │
│  │──────────────────────────────────────│  │
│  │ Wi-Fi only                       [✓] │  │
│  │ Pause downloads on mobile data       │  │
│  │──────────────────────────────────────│  │
│  │ Concurrent downloads           1     │  │  ← SliderRow
│  │ ●──────────────────────────          │  │
│  └──────────────────────────────────────┘  │
├────────────────────────────────────────────┤
│  AUTO-DOWNLOAD                             │
│  ┌──────────────────────────────────────┐  │
│  │ Automatic video selection        [✓] │  │
│  │ Auto-select your preferences         │  │
│  └──────────────────────────────────────┘  │
├────────────────────────────────────────────┤
│  PREFERRED QUALITY — drag to re-order    › │  ← Collapsible (collapsed)
├────────────────────────────────────────────┤
│  PREFERRED AUDIO — drag to re-order     › │
├────────────────────────────────────────────┤
│  PREFERRED SERVER — per extension        › │
└────────────────────────────────────────────┘
```

When "Preferred quality" is expanded:
```
│  PREFERRED QUALITY — drag to re-order    ⌄ │
│  ┌──────────────────────────────────────┐  │
│  │ 1. 1080p                          ≡  │  │ ← DragReorderableList
│  │ 2. 720p                           ≡  │  │
│  │ 3. 480p                           ≡  │  │
│  │ 4. 360p                           ≡  │  │
│  │──────────────────────────────────────│  │
│  │ If unavailable                       │  │
│  │ [ Try next ] [ Ask ] [ Don't ]       │  │ ← FallbackToggle
│  └──────────────────────────────────────┘  │
```

## 7. What the new project should do

1. **Replicate all 15 settings** (general + auto-download + advanced).
2. **Extend `PreferenceStore`** (or use a wrapper) to expose `changes(): Flow<T>` per setting — the UI depends on reactive prefs.
3. **Fix the concurrent-downloads bug**: when the slider changes, call `DownloadQueue.refreshConcurrency()` explicitly (the engine won't pick it up automatically).
4. **Fix the retries default mismatch**: code says 25, UI says 0..10. Pick one (10 is fine).
5. **Add a deep-link from the notification tap to the Downloads screen** (the old project's KDoc explicitly notes this as a future enhancement).
6. **Consider an "auto-download new episodes" toggle** — the old project doesn't have this. Not strictly needed for parity, but a common request.
7. **Settings persistence**: same `SharedPreferences` approach works (it's simple, no migration cost). The new project's existing `PreferenceStore` is fine — just needs the Flow wrapper.
