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

---

## 8. Post-rewrite additions (DL-PLAN-REWRITE)

> **Task ID:** DL-PLAN-REWRITE
> The user's requirements for the NEW settings:
> 1. **The download settings page UI must replicate the old project EXACTLY** (same UI, look, feel) — see `14-auto-download-engine.md` §5 + `15-ui-and-bug-analysis.md` Part A.
> 2. **NEW `dimensionPriority` + `globalFallback` prefs** for the 3-dimensional priority engine (see `14-auto-download-engine.md` §6).
> 3. **The new project uses `core/preferences`** (`PreferenceStore`) — currently non-reactive. Needs to become reactive for the drag-reorder UI.

### 8.1 The 17 settings (the OLD 15 + 2 new)

| # | Key | Type | Default | UI label | What it controls | NEW? |
|---|---|---|---|---|---|---|
| 1 | `pref_dl_folder_uri` | String | `""` | "Download folder" | SAF tree URI of the user's library root. | Existing |
| 2 | `pref_dl_method` | enum `DownloadMethod` | `ADVANCED` | "Download method" (Normal/Advanced) | NORMAL = single-threaded; ADVANCED = multi-threaded Range + resume. | Existing |
| 3 | `pref_dl_wifi_only` | Boolean | `true` | "Wi-Fi only" — "Pause downloads on mobile data" | Checked on every `tryStartNext` + every network change. | Existing |
| 4 | `pref_dl_concurrent` | Int | `1` (UI clamps 1..5) | "Concurrent downloads" | Max parallel downloads. **NEW: reactive — Flow collector calls `refreshConcurrency()` immediately.** | Existing (fixed) |
| 5 | `pref_dl_show_button` | Boolean | `true` | "Show download button" | Hides the per-episode download button when OFF. | Existing |
| 6 | `pref_dl_adv_threads` | Int | `8` (UI 1..8) | "Parallel threads" | Per-download thread count for Advanced method. | Existing |
| 7 | `pref_dl_adv_retries` | Int | `10` (UI 0..10) — **FIXED: was 25 in code** | "Max retries per chunk" | Per-chunk retry cap. | Existing (fixed) |
| 8 | `pref_dl_adv_min_size_mb` | Int | `1` (UI 1..20) | "Min size for multi-threading" | Files below this use a single thread. | Existing |
| 9 | `pref_dl_auto_pick` | Boolean | `false` | "Automatic video selection" — "Auto-select your preferences" | Master switch for the auto-download engine. | Existing |
| 10 | `pref_dl_quality_prefs` | `List<String>` (JSON) | `["1080p", "720p", "480p", "360p"]` | "Preferred quality — drag to re-order" | Ordered list of acceptable quality strings. | Existing |
| 11 | `pref_dl_audio_prefs` | `List<String>` (JSON) | `["SUB", "DUB"]` | "Preferred audio — drag to re-order" | Ordered list of acceptable audio versions. | Existing |
| 12 | `pref_dl_server_prefs` | `Map<String, List<String>>` (JSON; `sourceId` (String) → ordered server names) | `{}` | "Preferred server — per extension" | Per-source ordered server names. | Existing |
| 13 | `pref_dl_quality_fallback` | enum `FallbackStrategy` | `TRY_NEXT` | "If unavailable" (3-way: Try next / Ask / Don't) | What to do when no preferred quality matches. | Existing |
| 14 | `pref_dl_audio_fallback` | enum `FallbackStrategy` | `TRY_NEXT` | "If unavailable" | What to do when no preferred audio matches. | Existing |
| 15 | `pref_dl_server_fallback` | enum `FallbackStrategy` | `TRY_NEXT` | "If unavailable" | What to do when no preferred server matches. **NEW: now ACTUALLY consulted by the engine (was dead code in the OLD project — see `14-auto-download-engine.md` §1.7).** | Existing (fixed) |
| **16** | **`pref_dl_dimension_priority`** | `List<PreferenceDimension>` (JSON) | `[AUDIO, QUALITY, SERVER]` | "Priority order — what matters most?" | **The unified dimension-priority list. User reorders via `DragReorderableList`.** | **NEW** |
| **17** | **`pref_dl_global_fallback`** | enum `GlobalFallbackStrategy` | `BEST_EFFORT` | "If no preferred match anywhere" (3-way: Best effort / Ask / Don't) | **The global fallback when all per-dimension fallbacks are exhausted.** | **NEW** |

### 8.2 The NEW enums

```kotlin
enum class PreferenceDimension {
    AUDIO, QUALITY, SERVER;
    companion object {
        val DEFAULT_ORDER = listOf(AUDIO, QUALITY, SERVER)
    }
}

enum class GlobalFallbackStrategy {
    BEST_EFFORT,         // fall back to ANY available video (the OLD project's Step 4 behaviour)
    ASK,                 // show the picker sheet
    DO_NOT_DOWNLOAD,     // fail with an error
}

enum class FallbackStrategy {           // per-dimension (existing)
    TRY_NEXT, ASK, DO_NOT_DOWNLOAD,
}

enum class DownloadMethod {             // existing
    NORMAL, ADVANCED,
}
```

### 8.3 The NEW `DownloadPreferences` API (the 17 settings as reactive Flows)

```kotlin
class DownloadPreferences(private val store: PreferenceStore) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ── General ──
    fun downloadFolderUri(): Preference<String> = store.stringPref("pref_dl_folder_uri", "")
    fun method(): Preference<DownloadMethod> = store.enumPref("pref_dl_method", DownloadMethod.ADVANCED)
    fun wifiOnly(): Preference<Boolean> = store.booleanPref("pref_dl_wifi_only", true)
    fun concurrentDownloads(): Preference<Int> = store.intPref("pref_dl_concurrent", 1)
    fun showDownloadButton(): Preference<Boolean> = store.booleanPref("pref_dl_show_button", true)

    // ── Advanced method ──
    fun advancedThreadCount(): Preference<Int> = store.intPref("pref_dl_adv_threads", 8)
    fun advancedMaxRetries(): Preference<Int> = store.intPref("pref_dl_adv_retries", 10)  // FIXED: was 25
    fun advancedMinSizeMb(): Preference<Int> = store.intPref("pref_dl_adv_min_size_mb", 1)

    // ── Auto-download ──
    fun autoDownload(): Preference<Boolean> = store.booleanPref("pref_dl_auto_pick", false)

    // ── Preference lists (priority-ordered) ──
    fun qualityPreferences(): Preference<List<String>> = store.jsonListPref("pref_dl_quality_prefs", DEFAULT_QUALITY_PREFS)
    fun audioPreferences(): Preference<List<String>> = store.jsonListPref("pref_dl_audio_prefs", DEFAULT_AUDIO_PREFS)
    fun serverPreferences(): Preference<Map<String, List<String>>> = store.jsonMapPref("pref_dl_server_prefs", emptyMap())

    // ── Per-dimension fallbacks ──
    fun qualityFallback(): Preference<FallbackStrategy> = store.enumPref("pref_dl_quality_fallback", FallbackStrategy.TRY_NEXT)
    fun audioFallback(): Preference<FallbackStrategy> = store.enumPref("pref_dl_audio_fallback", FallbackStrategy.TRY_NEXT)
    fun serverFallback(): Preference<FallbackStrategy> = store.enumPref("pref_dl_server_fallback", FallbackStrategy.TRY_NEXT)

    // ── NEW: dimension priority + global fallback ──
    fun dimensionPriority(): Preference<List<PreferenceDimension>> =
        store.jsonListPref("pref_dl_dimension_priority", PreferenceDimension.DEFAULT_ORDER, PreferenceDimension.serializer())
    fun globalFallback(): Preference<GlobalFallbackStrategy> =
        store.enumPref("pref_dl_global_fallback", GlobalFallbackStrategy.BEST_EFFORT)

    companion object {
        val DEFAULT_QUALITY_PREFS = listOf("1080p", "720p", "480p", "360p")
        val DEFAULT_AUDIO_PREFS = listOf("SUB", "DUB")
    }
}
```

### 8.4 The reactive `PreferenceStore` (REQUIRED for the drag-reorder UI)

The new project's current `PreferenceStore` is non-reactive (just `getString/putString/...` with no Flows — see `core/preferences/PreferenceStore.kt`). The drag-reorder UI + the live-updating settings screen need reactive prefs.

**The fix:** extend `PreferenceStore` with a `Preference<T>` interface that exposes `get()`, `set(T)`, and `changes(): Flow<T>`. Implementation via `SharedPreferences.OnSharedPreferenceChangeListener` (option (b) in `13-implementation-plan.md` D4 — lighter than per-key `MutableStateFlow`).

```kotlin
// core/preferences/src/main/java/com/confused/anikuta/core/preferences/PreferenceStore.kt (EXTENDED)
class PreferenceStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("anikuta_prefs", Context.MODE_PRIVATE)

    // ── Existing non-reactive API (kept for backward compat) ──
    fun getString(key: String, default: String = ""): String = prefs.getString(key, default) ?: default
    fun putString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    fun getBoolean(key: String, default: Boolean = false): Boolean = prefs.getBoolean(key, default)
    fun putBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    fun getInt(key: String, default: Int = 0): Int = prefs.getInt(key, default)
    fun putInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }
    fun getFloat(key: String, default: Float = 0f): Float = prefs.getFloat(key, default)
    fun putFloat(key: String, value: Float) { prefs.edit().putFloat(key, value).apply() }
    fun getLong(key: String, default: Long = 0L): Long = prefs.getLong(key, default)
    fun putLong(key: String, value: Long) { prefs.edit().putLong(key, value).apply() }

    // ── NEW: reactive API ──
    private val _changes = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val changes: SharedFlow<String> = _changes.asSharedFlow()  // emits the changed key

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null) _changes.tryEmit(key)
    }
    init { prefs.registerOnSharedPreferenceChangeListener(listener) }

    /** Returns a reactive Preference for the given key. */
    fun <T> preference(key: String, default: T, encode: (T) -> String, decode: (String) -> T): Preference<T> =
        PreferenceImpl(this, key, default, encode, decode)

    // ── Convenience builders ──
    fun stringPref(key: String, default: String): Preference<String> =
        preference(key, default, { it }, { it })
    fun booleanPref(key: String, default: Boolean): Preference<Boolean> =
        preference(key, default, { it.toString() }, { it.toBooleanStrictOrNull() ?: default })
    fun intPref(key: String, default: Int): Preference<Int> =
        preference(key, default, { it.toString() }, { it.toIntOrNull() ?: default })
    fun <E : Enum<E>> enumPref(key: String, default: E): Preference<E> =
        preference(key, default, { it.name }, { v -> runCatching { enumValueOf<E>(v) }.getOrDefault(default) })
    fun <T> jsonListPref(key: String, default: List<T>, serializer: KSerializer<T>): Preference<List<T>> =
        preference(key, default,
            { list -> Json.encodeToString(ListSerializer(serializer), list) },
            { str -> runCatching { Json.decodeFromString(ListSerializer(serializer), str) }.getOrDefault(default) })
    fun <T> jsonListPref(key: String, default: List<String>): Preference<List<String>> =
        jsonListPref(key, default, String.serializer())
    fun jsonMapPref(key: String, default: Map<String, List<String>>): Preference<Map<String, List<String>>> =
        preference(key, default,
            { map -> Json.encodeToString(MapSerializer(String.serializer(), ListSerializer(String.serializer())), map) },
            { str -> runCatching { Json.decodeFromString(MapSerializer(String.serializer(), ListSerializer(String.serializer())), str) }.getOrDefault(default) })
}

interface Preference<T> {
    /**
     * The storage key (snake_case `pref_dl_*` for download prefs).
     *
     * REVIEW-5 M46 (R2-I4): the OLD draft's interface had only `get`/`set`/`changes` (3 methods).
     * The OLD project's `Preference<T>` interface has 7 methods: `key`/`get`/`set`/`isSet`/`delete`/
     * `defaultValue`/`changes` (+ optionally `stateIn(scope)`). The 3-method version was a regression
     * — code that reads `key()` (e.g. for diagnostics, backup/restore, migration) wouldn't compile.
     */
    fun key(): String
    fun get(): T
    fun set(value: T)
    fun isSet(): Boolean
    fun delete()
    fun defaultValue(): T
    fun changes(): Flow<T>

    /** Optional helper — converts the cold `changes()` Flow into a hot StateFlow for `collectAsState`. */
    fun stateIn(scope: CoroutineScope): StateFlow<T> =
        changes().stateIn(scope, SharingStarted.Eagerly, get())
}

private class PreferenceImpl<T>(
    private val store: PreferenceStore,
    private val key: String,
    private val default: T,
    private val encode: (T) -> String,
    private val decode: (String) -> T,
) : Preference<T> {
    override fun key(): String = key
    override fun get(): T {
        val raw = store.getString(key, "__DEFAULT__")
        return if (raw == "__DEFAULT__") default else decode(raw)
    }
    override fun set(value: T) { store.putString(key, encode(value)) }
    override fun isSet(): Boolean = store.getString(key, "__DEFAULT__") != "__DEFAULT__"
    override fun delete() { store.remove(key) }
    override fun defaultValue(): T = default
    override fun changes(): Flow<T> = store.changes
        .filter { it == key }
        .map { get() }
        .distinctUntilChanged()
        // REVIEW-5 M47 (R2-M4): removed `onStart { emit(get()) }` — it's redundant with
        // `collectAsState(initial = prefs.x().get())` (the `initial` parameter provides the first
        // value synchronously). Keeping both caused a double-emit on first collection. The OLD
        // project's `Preference.changes()` does NOT have `onStart`.
}
```

**Why this matters for the drag-reorder UI:**
- The `DragReorderableList` calls `onReorder(newOrder)` on drag END → the screen calls `preferences.X().set(newOrder)` → the reactive Flow emits → other parts of the UI (e.g. the dimension-priority section if other screens display it) re-render with the new order.
- The settings screen collects each setting via `collectAsState(initial = prefs.x().get())` → the screen re-renders whenever any pref changes.

### 8.5 The NEW "Priority order" section in the settings UI

Per `14-auto-download-engine.md` §6.5, ADD ONE new collapsible section ABOVE the existing 3 preference-list sections:

```
┌────────────────────────────────────────────┐
│ AUTO-DOWNLOAD                              │
│ ┌────────────────────────────────────────┐ │
│ │ Automatic video selection          [✓] │ │
│ │ Auto-select your preferences           │ │
│ └────────────────────────────────────────┘ │
├────────────────────────────────────────────┤
│ PRIORITY ORDER — what matters most?     ⌄  │  ← NEW collapsible section
│ ┌────────────────────────────────────────┐ │
│ │ 1. Audio                          ≡    │ │  ← DragReorderableList (3 items)
│ │ 2. Quality                        ≡    │ │     (reorder: drag to put the most
│ │ 3. Server                         ≡    │ │      important dimension on top)
│ │──────────────────────────────────────│ │
│ │ If no preferred match anywhere        │ │  ← NEW global fallback toggle
│ │ [ Best effort ] [ Ask ] [ Don't ]     │ │
│ └────────────────────────────────────────┘ │
├────────────────────────────────────────────┤
│ PREFERRED QUALITY — drag to re-order    ›  │  ← existing
│ PREFERRED AUDIO — drag to re-order     ›   │  ← existing
│ PREFERRED SERVER — per extension        ›  │  ← existing
└────────────────────────────────────────────┘
```

The `DragReorderableList` component takes `List<String>` — so we render the dimension names as strings (`["Audio", "Quality", "Server"]`) and map back to enum values when persisting:

```kotlin
// In DownloadSettingsScreen.kt, INSIDE the new CollapsibleSection("Priority order — what matters most?"):
val dimensionLabels = mapOf(
    PreferenceDimension.AUDIO to "Audio",
    PreferenceDimension.QUALITY to "Quality",
    PreferenceDimension.SERVER to "Server",
)
val dimensionOrder = dimensionPriority.map { dimensionLabels[it] ?: it.name }
val reverseLabels = dimensionLabels.entries.associate { (k, v) -> v to k }

DragReorderableList(
    items = dimensionOrder,
    onReorder = { newOrder ->
        preferences.dimensionPriority().set(newOrder.mapNotNull { reverseLabels[it] })
    },
)
// Then the global fallback toggle (FallbackToggle re-used, or a new SegmentedRowLocal).
```

### 8.6 The exact UI replication (per `14-auto-download-engine.md` §5 + `15-ui-and-bug-analysis.md` Part A)

**Replicate EXACTLY** (no deviations):
- The 528-line `DownloadSettingsScreen.kt` layout — sections, components, colors, spacings, animations.
- The 8 private composables: `SectionContainer`, `CollapsibleSection`, `CollapsibleExtensionSection`, `SettingsRow`, `ToggleRow`, `SliderRow`, `FallbackToggle`, `SegmentedRowLocal`.
- The `DragReorderableList` component (193 lines) — replicate as-is.
- The `DownloadVideoPickerSheet` (233 lines) — replicate as-is.
- The visual design tokens (per `14-auto-download-engine.md` §5.4):
  - Font: `RobotoFamily` everywhere.
  - Colors: `MaterialTheme.colorScheme` (`surface`, `surfaceVariant` at 30%/20% alpha, `primary`, `onPrimary`, `onSurface`, `onSurfaceVariant`).
  - Shapes: `RoundedCornerShape(16.dp)` for outer section cards, `12.dp` for inner rows + extension cards, `8.dp` for segmented chips.
  - Spacings: section horizontal padding 12.dp, row padding 12-16.dp horizontal × 10-14.dp vertical, drag handle 48×48dp, slider section padding 12.dp horizontal × 8.dp vertical.
  - Animations: `expandVertically() + fadeIn()` for show, `shrinkVertically() + fadeOut()` for hide.

**Add ONE new section** (per §8.5 above): the "Priority order" collapsible section ABOVE the existing 3.

**Fix the OLD project's bugs while replicating:**
1. The `concurrentDownloads` pref change must call `DownloadQueue.refreshConcurrency()` explicitly — now done reactively via the Flow collector in `DownloadQueue.init` (see `02-queue-management.md` §13.1).
2. The `advancedMaxRetries` default mismatch (code=25, UI=0..10) — set both to 10.
3. The `serverFallback` dead-code bug — now ACTUALLY consulted by the engine (see `14-auto-download-engine.md` §6.2.3 Step 3).

### 8.7 Cross-references (post-rewrite)

- `14-auto-download-engine.md` §5 — the EXACT settings UI structure to replicate.
- `14-auto-download-engine.md` §6 — the NEW priority engine design (the `dimensionPriority` + `globalFallback` prefs are the inputs).
- `15-ui-and-bug-analysis.md` Part A — the Downloads page UI replication spec (related but separate from the settings page).
- `13-implementation-plan.md` Phase D.5 — the implementation plan for the settings page.
- `02-queue-management.md` §13.1 — the reactive `refreshConcurrency()` Flow collector.
