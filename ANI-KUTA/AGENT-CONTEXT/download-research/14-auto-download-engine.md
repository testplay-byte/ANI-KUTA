# 14 — Auto-Download Engine & Priority Resolution

> **Task ID:** DL-AUTODL-RESEARCH
> **Source files analysed** (all under `REFERENCES/old-kuta/ANIKUTA/`):
> - `core/download/src/main/java/app/confused/anikuta/core/download/DownloadPreferences.kt` (205 lines) — all settings + keys + defaults + enums.
> - `core/download/src/main/java/app/confused/anikuta/core/download/DownloadManager.kt` (134 lines) — the manager contract (no auto-download logic; just queue ops).
> - `core/download/src/main/java/app/confused/anikuta/core/download/DefaultDownloadManager.kt` (256 lines) — the manager implementation (no auto-download logic; orchestrator does the picking).
> - `core/download/src/main/java/app/confused/anikuta/core/download/ServerDiscoveryStore.kt` (84 lines) — passive per-source server recording.
> - `app/src/main/java/app/confused/anikuta/download/DownloadOrchestrator.kt` (400 lines) — **THE** auto-download resolution engine (`selectBestVideo`).
> - `feature/download/src/main/java/app/confused/anikuta/feature/download/DownloadSettingsScreen.kt` (528 lines) — the settings page UI.
> - `feature/download/src/main/java/app/confused/anikuta/feature/download/components/DragReorderableList.kt` (193 lines) — the drag-and-drop reorder component.
> - `feature/download/src/main/java/app/confused/anikuta/feature/download/DownloadsMoreEntries.kt` (38 lines) — the More-screen entry to Downloads.
> - `feature/download/src/main/java/app/confused/anikuta/feature/download/DownloadVideoPickerSheet.kt` (233 lines) — manual-mode picker sheet (fallback when auto is OFF or ASK).
> - `core/video-resolver/src/main/java/app/confused/anikuta/core/videoresolver/VideoResolverState.kt` (70 lines) — the `ResolverServer` / `ResolverAudioVersion` / `ResolverVideo` data model.

This doc supersedes the brief overview in `07-settings-preferences.md` §2 (which only listed settings) — here we trace the EXACT resolution algorithm, document the gap the user identified, and propose a new 3-dimensional priority engine design.

---

## 1. Auto-download settings inventory

Source: `DownloadPreferences.kt` (full file read). All settings are `Preference<T>` exposed by a `PreferenceStore` (SharedPreferences under the hood, with reactive `changes(): Flow<T>` for the UI).

### 1.1 General settings (NOT auto-download-specific, but listed for completeness)

| # | Key | Type | Default | UI label | What it controls |
|---|---|---|---|---|---|
| 1 | `pref_dl_folder_uri` | `String` | `""` | "Download folder" | SAF tree URI of the user's ANIKUTA root folder. |
| 2 | `pref_dl_method` | enum `DownloadMethod` | `ADVANCED` | "Download method" (Normal/Advanced) | NORMAL = single-threaded OkHttp; ADVANCED = multi-threaded Range + resume. |
| 3 | `pref_dl_wifi_only` | `Boolean` | `true` | "Wi-Fi only" — "Pause downloads on mobile data" | Checked on every `tryStartNext` by `DefaultDownloadManager.isNetworkAllowed()`. |
| 4 | `pref_dl_concurrent` | `Int` | `1` (UI clamps 1..5) | "Concurrent downloads" | Max parallel downloads via the queue's Semaphore. |
| 5 | `pref_dl_show_button` | `Boolean` | `true` | "Show download button" — "Display the download icon on episode rows" | Hides the per-episode download button when OFF (existing tasks still show). |
| 6 | `pref_dl_adv_threads` | `Int` | `8` (UI 1..8) | "Parallel threads" | Per-download thread count for the Advanced method. |
| 7 | `pref_dl_adv_retries` | `Int` | `25` ⚠️ (UI 0..10) | "Max retries per chunk" | Per-chunk retry cap. **Bug: code default is 25 but UI clamps 0..10** — a user who never opens settings gets 25. |
| 8 | `pref_dl_adv_min_size_mb` | `Int` | `1` (UI 1..20) | "Min size for multi-threading" | Files below this use a single thread (multi-thread overhead not worth it). |

### 1.2 Auto-download settings (the heart of this doc)

```kotlin
// DownloadPreferences.kt:70-71
fun autoDownload(): Preference<Boolean> =
    store.getBoolean(KEY_AUTO_PICK, false)   // KEY_AUTO_PICK = "pref_dl_auto_pick"
```

| # | Key | Type | Default | UI label | What it controls |
|---|---|---|---|---|---|
| 9 | `pref_dl_auto_pick` | `Boolean` | `false` | "Automatic video selection" — "Auto-select your preferences" | **Master switch.** When ON, tapping download auto-resolves + auto-picks (no picker sheet). When OFF, always shows `DownloadVideoPickerSheet`. |

### 1.3 Preference lists (priority-ordered; only consulted when `pref_dl_auto_pick` is ON)

| # | Key | Type | Default | UI label | What it controls |
|---|---|---|---|---|---|
| 10 | `pref_dl_quality_prefs` | `List<String>` (JSON) | `["1080p", "720p", "480p", "360p"]` (`DEFAULT_QUALITY_PREFS`) | "Preferred quality — drag to re-order" | Ordered list of acceptable quality strings. Top = highest priority. Reorderable via `DragReorderableList`. |
| 11 | `pref_dl_audio_prefs` | `List<String>` (JSON) | `["SUB", "DUB"]` (`DEFAULT_AUDIO_PREFS`) | "Preferred audio — drag to re-order" | Ordered list of acceptable audio versions (SUB/DUB/HSUB). Top = preferred. Reorderable. |
| 12 | `pref_dl_server_prefs` | `Map<String, List<String>>` (JSON; `sourceId` (String) → ordered server names) | `{}` (empty) | "Preferred server — per extension" | Per-source ordered server names. Each extension has its own reorderable list. New servers discovered via `ServerDiscoveryStore.recordServers` are appended. |

**Storage format** (lines 81-124):
- Quality + audio: JSON-encoded via `kotlinx.serialization.builtins.ListSerializer(String.serializer())`. Decode failures fall back to the default list (not crash).
- Server prefs: JSON-encoded via `MapSerializer(String.serializer(), ListSerializer(String.serializer()))`. Decode failures fall back to empty map.

### 1.4 Fallback strategies (only consulted when `pref_dl_auto_pick` is ON)

```kotlin
// DownloadPreferences.kt:200-204
enum class FallbackStrategy {
    TRY_NEXT,         // try next option in preference list
    ASK,              // show the picker sheet
    DO_NOT_DOWNLOAD,  // show error, don't download
}
```

| # | Key | Type | Default | UI label | What it controls |
|---|---|---|---|---|---|
| 13 | `pref_dl_quality_fallback` | enum `FallbackStrategy` | `TRY_NEXT` | "If unavailable" (3-way: Try next / Ask / Don't) | What to do when no preferred quality matches. |
| 14 | `pref_dl_audio_fallback` | enum `FallbackStrategy` | `TRY_NEXT` | "If unavailable" | What to do when no preferred audio matches. |
| 15 | `pref_dl_server_fallback` | enum `FallbackStrategy` | `TRY_NEXT` | "If unavailable" | What to do when no preferred server matches. |

### 1.5 The actual `Preference.getObject` code for the lists (verbatim)

```kotlin
// DownloadPreferences.kt:81-90  (qualityPreferences)
fun qualityPreferences(): Preference<List<String>> =
    store.getObject(
        KEY_QUALITY_PREFS,
        DEFAULT_QUALITY_PREFS,
        { json.encodeToString(ListSerializer(String.serializer()), it) },
        { str ->
            try { json.decodeFromString(ListSerializer(String.serializer()), str) }
            catch (e: Exception) { DEFAULT_QUALITY_PREFS }
        },
    )

// Same pattern for audioPreferences (lines 96-105) and serverPreferences (lines 113-124, MapSerializer).
```

### 1.6 The reactive stream wiring (how the UI sees changes)

The `Preference<T>` interface (from `:core:preferences`) exposes:
- `get(): T` — current value (snapshot read; the engine uses this).
- `set(value: T)` — write (UI calls this).
- `changes(): Flow<T>` — reactive stream (UI collects this).

In the engine, **only `.get()` is used** (read-once-per-action):
```kotlin
// DownloadOrchestrator.kt:212-216
val qualityPrefs = preferences.qualityPreferences().get()
val audioPrefs = preferences.audioPreferences().get()
val serverPrefs = preferences.serverPreferences().get()[sourceId.toString()] ?: emptyList()
val audioFallback = preferences.audioFallback().get()
val qualityFallback = preferences.qualityFallback().get()
```

In the UI, every setting is collected as a `State` via `collectAsState(initial = prefs.x().get())` so the screen rebuilds whenever any pref changes (DownloadSettingsScreen.kt:79-106).

### 1.7 ⚠️ Critical bug — `serverFallback` is declared but NEVER consulted

A full grep of the old project for `serverFallback` shows:
- **Declared** in `DownloadPreferences.kt:137-138` (`pref_dl_server_fallback`).
- **Read reactively** in `DownloadSettingsScreen.kt:97-98` + written by `FallbackToggle` on line 303-307.
- **NEVER READ** by `DownloadOrchestrator.selectBestVideo()` — only `audioFallback` + `qualityFallback` are pulled (lines 215-216).

So the user can toggle "Server — If unavailable: Don't" in the settings, and the auto-download engine will IGNORE it. The server preference list is used only as a soft sort key (servers in the list are tried first; unlisted servers are tried last but still tried). This is a silent UX bug, and it's also one of the symptoms of the gap the user identified (see §4).

---

## 2. The drag-reorder UI

### 2.1 The component — `DragReorderableList.kt` (193 lines)

A self-contained, performant drag-and-drop reorder list. **Only takes `List<String>`** — not generic.

**Signature** (line 70-73):
```kotlin
@Composable
fun DragReorderableList(
    items: List<String>,
    onReorder: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
)
```

**Visual layout per row:**
```
┌────────────────────────────────────────────────────────┐
│ 1.  1080p                                       ≡      │  ← 48dp tall row
│    ↑                                            ↑      │
│    index label (12sp, onSurfaceVariant)        drag   │
│    value (14sp, Bold, onSurface)               handle │
└────────────────────────────────────────────────────────┘
```

- **Item height:** fixed 48.dp (line 76). Spacing 2.dp between items (line 94).
- **Left side:** 24dp-wide index label ("1.", "2.", ...) + the item value (`Modifier.weight(1f)`).
- **Right side:** a 48×48dp touch-target Box containing a `Icons.Filled.DragHandle` (24dp). **Only this handle captures drag gestures** — the rest of the row passes through to the parent scroll (line 137-141).

**Performance design** (from the KDoc, lines 41-67):
- **No per-item `animateFloatAsState`** — that was the source of scroll jank in an earlier version.
- The dragged item follows the finger via `graphicsLayer.translationY` — draw-phase only, no recomposition.
- Non-dragged items SNAP to their new positions (no animation) — intentional, the animation was the jank source.
- An internal `mutableStateListOf<String>` holds the reordered copy during drag. `onReorder` is called **only on drag END** (line 147-153), never during — so the parent doesn't recompose during drag.

**Drag math** (lines 160-175):
```kotlin
onDrag = { change, dragAmount ->
    change.consume()
    dragOffset += dragAmount.y
    val shift = (dragOffset / itemHeightPx).roundToInt()
    val targetIndex = (draggedIndex + shift).coerceIn(0, internalItems.size - 1)
    if (targetIndex != draggedIndex && draggedIndex >= 0) {
        val moved = internalItems.removeAt(draggedIndex)
        internalItems.add(targetIndex, moved)
        val indexShift = targetIndex - draggedIndex
        dragOffset -= indexShift * itemHeightPx
        draggedIndex = targetIndex
    }
},
```

So when the dragged item's offset crosses another item's midpoint, they swap. `dragOffset` is adjusted by `indexShift * itemHeightPx` so the finger stays "on" the same logical position.

**Visual feedback during drag:**
- Dragged item: `primaryContainer` colour at 60% alpha + 8dp shadow.
- Dragged item's handle icon: tinted `primary`.
- Non-dragged: `surfaceVariant` at 40% alpha, handle tinted `onSurfaceVariant`.

**Cancel handling:** `onDragCancel` (line 154-159) reverts the internal list back to `items` (the original order). No `onReorder` call.

### 2.2 What lists can the user reorder?

Three reorderable lists, all rendered via the same `DragReorderableList` component:

| List | Backing pref | Where rendered | Default |
|---|---|---|---|
| **Preferred quality** | `pref_dl_quality_prefs` (List<String>) | `DownloadSettingsScreen.kt:244-247` — inside `CollapsibleSection("Preferred quality", "drag to re-order")` | `["1080p", "720p", "480p", "360p"]` |
| **Preferred audio** | `pref_dl_audio_prefs` (List<String>) | `DownloadSettingsScreen.kt:263-266` — inside `CollapsibleSection("Preferred audio", "drag to re-order")` | `["SUB", "DUB"]` |
| **Preferred server (per extension)** | `pref_dl_server_prefs` (Map<sourceId, List<String>>) | `DownloadSettingsScreen.kt:418-425` — inside `CollapsibleExtensionSection` (one per installed extension) | `{}` (no defaults — populated by `ServerDiscoveryStore`) |

**Key limitation:** the lists are independent — the user reorders each separately. There is NO unified drag list that lets the user say "audio matters more than quality matters more than server". This is exactly the gap the user identified (see §4).

### 2.3 How is the order persisted?

```kotlin
// DownloadSettingsScreen.kt:244-247  (quality)
app.confused.anikuta.feature.download.components.DragReorderableList(
    items = qualityPrefs,
    onReorder = { newOrder -> preferences.qualityPreferences().set(newOrder) },
)
```

The pattern:
1. `DragReorderableList` calls `onReorder(newOrder)` once on drag END.
2. The screen calls `preferences.X().set(newOrder)` which writes through to SharedPreferences (JSON-encoded).
3. The reactive `changes()` Flow re-emits, the screen's `collectAsState` updates, the list re-renders with the new order.

For server preferences (per extension):
```kotlin
// DownloadSettingsScreen.kt:420-424
onReorder = { newOrder ->
    val updated = serverPrefs.toMutableMap()
    updated[extSource.sourceId.toString()] = newOrder
    preferences.serverPreferences().set(updated)
},
```

### 2.4 Server-list merge logic (user order + discovered servers)

```kotlin
// DownloadSettingsScreen.kt:384-386
val discovered = serverMap[extSource.sourceId.toString()] ?: emptyList()
val userOrder = serverPrefs[extSource.sourceId.toString()] ?: emptyList()
val merged = (userOrder.filter { it in discovered } + discovered.filter { it !in userOrder }).distinct()
```

So: **user's manual order first (filtered to still-discovered)**, then **any new discoveries appended**. Deleted-from-source servers silently drop out (the `filter { it in discovered }`). The user's reorder is preserved across new discoveries.

---

## 3. The current priority resolution logic (the exact algorithm)

### 3.1 Where auto-download fires

The trigger is in `AppController.downloadEpisode()` (in `:app`). It calls:

```kotlin
// AppController.kt (the host)
val result = downloadOrchestrator.enqueueDownload(animeInfo, episode, source)
when (result) {
    is EnqueueResult.Success -> Toast("Download started")
    is EnqueueResult.ShowPicker -> downloadPickerTarget = result  // shows DownloadVideoPickerSheet
    is EnqueueResult.NoSources -> Toast("No video sources")
    is EnqueueResult.Error -> Toast(result.message)
}
```

`DownloadOrchestrator.enqueueDownload` (lines 65-144) is the entry point:

```kotlin
suspend fun enqueueDownload(anime, episode, source): EnqueueResult {
    if (!manager.isFolderReady()) return Error("No download folder set...")
    return try {
        when (val result = resolver.resolve(source, episode)) {
            is ResolverResult.Success -> {
                if (result.servers.isEmpty()) return NoSources
                serverDiscovery.recordServers(source.id, result.servers.map { it.name })

                // If auto-download is OFF → always show picker.
                if (!preferences.autoDownload().get()) {
                    return ShowPicker(result.servers, anime, episode, source)
                }

                // Auto-download ON — select the best video.
                val selection = selectBestVideo(source.id, result.servers)
                when (selection) {
                    is Selected -> {
                        val request = buildRequest(anime, episode, source, selection)
                        val taskId = manager.enqueueDownload(request)
                        if (taskId < 0) Error("Failed to enqueue (invalid request).")
                        else Success(taskId)
                    }
                    is NoMatch -> { /* fallback strategy decides — see §3.3 */ }
                }
            }
            is ResolverResult.NoSources -> NoSources
            is ResolverResult.Error -> Error(result.message)
        }
    } catch (e: Exception) { Error(e.message ?: ...) }
}
```

So **auto-download ONLY fires inside `selectBestVideo`** — once the resolver returns the 3-tier hierarchy (`List<ResolverServer>` → each has `List<ResolverAudioVersion>` → each has `List<ResolverVideo>`).

### 3.2 The data model the resolver returns

```kotlin
// VideoResolverState.kt:37-60
data class ResolverServer(
    val name: String,
    val audioVersions: List<ResolverAudioVersion>,
)
data class ResolverAudioVersion(
    val label: String,           // e.g. "SUB", "DUB", "HSUB"
    val videos: List<ResolverVideo>,
)
data class ResolverVideo(
    val quality: String,         // e.g. "1080p", "720p"
    val url: String,
    val videoTitle: String = "",
    val videoHeaders: String? = null,
    val subtitleTracks: List<SubtitleTrack> = emptyList(),
    val audioTracks: List<SubtitleTrack> = emptyList(),
)
```

So a single episode's resolved video tree might look like:
```
Server: "Vidstreaming"
├── Audio: "SUB"
│   ├── Video: 1080p (url=...)
│   ├── Video: 720p (url=...)
│   └── Video: 360p (url=...)
└── Audio: "DUB"
    └── Video: 720p (url=...)
Server: "Streamtape"
└── Audio: "SUB"
    └── Video: 1080p (url=...)
```

The engine must pick ONE leaf `ResolverVideo` (+ remember which server + which audio version it came from, for the download record).

### 3.3 The full `selectBestVideo` algorithm (verbatim pseudocode from `DownloadOrchestrator.kt:211-311`)

Inputs (read once at the start of `selectBestVideo`):
- `qualityPrefs: List<String>` — e.g. `["1080p", "720p", "480p", "360p"]`
- `audioPrefs: List<String>` — e.g. `["SUB", "DUB"]`
- `serverPrefs: List<String>` — for THIS source only (looked up by `sourceId.toString()` in the map). May be empty if no servers discovered yet.
- `audioFallback: FallbackStrategy` (default `TRY_NEXT`)
- `qualityFallback: FallbackStrategy` (default `TRY_NEXT`)
- ⚠️ **`serverFallback` is NOT read** — see §1.7.

Helpers:
- `orderByName(items, prefs, nameOf)`: returns `items` sorted so that items whose name matches a `prefs` entry come first (in pref order), then unmatched items at the end (`Int.MAX_VALUE` sort key). Used for servers + audios.
- `orderByQuality(videos, qualityPrefs)`: same idea for the video list.
- `matchesAudio(label, audioPrefs)`: `true` if `label` is in `audioPrefs` (case-insensitive). Empty `audioPrefs` = accept any.
- `matchesQuality(video, qualityPrefs)`: same for quality.

**The algorithm** (4 steps):

```
STEP 1 — Check if the TOP-preferred audio is available
  topAudioPref = audioPrefs.firstOrNull()
  if (topAudioPref != null):
    topAudioAvailable = servers.any { server ->
      server.audioVersions.any { it.label.equals(topAudioPref, ignoreCase) }
    }
    if (!topAudioAvailable):
      when (audioFallback):
        ASK → return NoMatch        // caller shows picker
        DO_NOT_DOWNLOAD → return NoMatch  // caller shows error
        TRY_NEXT → continue         // try remaining preferred audios

STEP 2 — Check if the TOP-preferred quality is available
           (within any preferred audio)
  topQualityPref = qualityPrefs.firstOrNull()
  if (topQualityPref != null):
    topQualityAvailable = servers.any { server ->
      server.audioVersions.any { audio ->
        matchesAudio(audio.label, audioPrefs) &&  // ← only preferred audios
        audio.videos.any { it.quality.equals(topQualityPref, ignoreCase) }
      }
    }
    if (!topQualityAvailable):
      when (qualityFallback):
        ASK → return NoMatch
        DO_NOT_DOWNLOAD → return NoMatch
        TRY_NEXT → continue

STEP 3 — Try all preferred audio × preferred quality combinations
          (server acts as soft sort key)
  orderedServers = orderByName(servers, serverPrefs) { it.name }
  for (server in orderedServers):                          // ← outer loop = SERVER
    orderedAudios = orderByName(server.audioVersions, audioPrefs) { it.label }
    for (audio in orderedAudios):                           // ← middle loop = AUDIO
      if (!matchesAudio(audio.label, audioPrefs)) continue  // ← HARD FILTER on audio
      orderedVideos = orderByQuality(audio.videos, qualityPrefs)
      match = orderedVideos.firstOrNull { matchesQuality(it, qualityPrefs) }  // ← HARD FILTER on quality
      if (match != null):
        return Selected(match, server.name, audio.label)

STEP 4 — No preferred combination matched
  if (audioFallback == TRY_NEXT && qualityFallback == TRY_NEXT):
    // Best-effort: pick first available (ignoring preference lists)
    for (server in orderedServers):
      for (audio in orderedAudios):
        first = orderByQuality(audio.videos, qualityPrefs).firstOrNull()
        if (first != null):
          return Selected(first, server.name, audio.label)

  return NoMatch
```

### 3.4 The implicit priority order

Tracing the loops, the **effective priority** is:

```
Iteration order (Step 3):
  Outermost = Server  →  picks the server first (soft, via orderByName sort)
  Middle    = Audio   →  HARD filter (only preferred audios considered)
  Innermost = Quality →  HARD filter (only preferred qualities considered)
```

But the **early-exit checks** in Steps 1-2 act at a different layer:
- Step 1 aborts the whole resolution if the **top audio** is missing AND audioFallback ≠ TRY_NEXT.
- Step 2 aborts the whole resolution if the **top quality** is missing AND qualityFallback ≠ TRY_NEXT.

So the runtime priority semantics is muddy:
1. At the **availability-check** layer: AUDIO is checked first, then QUALITY. (Server isn't checked at all.)
2. At the **iteration** layer: SERVER is the outermost loop (so the first server in `orderedServers` is preferred; even if it doesn't have the user's preferred audio, it might still be the one picked — but Step 3's hard audio filter will skip it).
3. The effective priority is roughly: **Audio (hard filter + top-check) > Quality (hard filter + top-check) > Server (soft sort only)**.
4. **Server has NO real authority** — even if the user reorders servers, the engine will still pick the FIRST server (in pref order) that has a preferred audio + preferred quality. If the user's #1 server doesn't have preferred audio+quality but their #2 server does, #2 wins.

### 3.5 Worked example (current behaviour)

**User settings:**
- `qualityPrefs = ["1080p", "720p"]`
- `audioPrefs = ["DUB", "SUB"]`
- `serverPrefs = ["Streamtape", "Vidstreaming"]` (for this source)
- All fallbacks = `TRY_NEXT`

**Resolved video tree:**
```
Streamtape (user's #1):
  SUB:  1080p, 720p       (no DUB)
Vidstreaming (user's #2):
  SUB:  720p
  DUB:  1080p             ← user's preferred combo is HERE
```

Trace:
- Step 1: topAudioPref = "DUB". `topAudioAvailable = true` (Vidstreaming has DUB). ✓ continue.
- Step 2: topQualityPref = "1080p". `topQualityAvailable = true` (Streamtape has 1080p under SUB — and `matchesAudio("SUB", ["DUB","SUB"])` is true). ✓ continue.
- Step 3: `orderedServers = [Streamtape, Vidstreaming]` (per serverPrefs).
  - Streamtape: `orderedAudios = [SUB]` (only SUB available). `matchesAudio("SUB", ["DUB","SUB"])` = true. `orderedVideos = [1080p, 720p]` (per qualityPrefs). First match: **1080p**. **→ RETURN Selected(1080p, "Streamtape", "SUB").**

**Result: Streamtape / SUB / 1080p.**

Even though the user's TOP audio pref is DUB, and DUB+1080p IS available on Vidstreaming, the engine picks Streamtape/SUB/1080p because:
- Streamtape is the #1 preferred server.
- Streamtape has a preferred audio (SUB) + preferred quality (1080p) combination.
- The hard filter passes immediately on the first server.

**This is the gap.** The user said: "we don't have a system to properly configure which thing is the most important, like is the audio the most important or is the quality the most important or is the server the most important." — and this trace demonstrates exactly that. There's no way for the user to say "I'd rather get DUB on Vidstreaming than SUB on Streamtape", because server is the outermost loop AND server has no real fallback strategy.

---

## 4. The gap the user identified

### 4.1 What the user said

> "The auto download functionality of it is very proper. It has a proper full-fledged system where the user can easily drag and drop and rearrange the preferred qualities and other stuff like that and it will download based on it... there are some downsides to it too. Now we don't have a system to properly configure which thing is the most important, like is the audio the most important or is the quality the most important or is the server the most important."

### 4.2 The current priority order is HARDCODED + INCONSISTENT

There are exactly three "preference dimensions": Audio, Quality, Server. The current code has NO user-facing way to set their relative importance. Worse, the **implicit priority is internally inconsistent**:

| Layer | Effective priority | Hard or soft? |
|---|---|---|
| Step 1 (availability check) | Audio → Quality (no Server) | Hard — aborts resolution |
| Step 2 (availability check) | Audio → Quality (no Server) | Hard — aborts resolution |
| Step 3 (iteration) | **Server → Audio → Quality** | Soft for Server (sorted but not filtered), Hard filter for Audio + Quality |
| Step 4 (best-effort) | Server → Audio → Quality (any) | Soft — picks first available |

So:
- If the user's #1 server has a non-preferred audio + non-preferred quality combo, AND #2 server has the preferred audio+quality combo → the engine picks #2 server (because the audio hard-filter rejects #1). This is **Server being implicitly demoted below Audio**.
- If the user's #1 server has a preferred audio + non-preferred quality, AND #2 server has the preferred audio + preferred quality → the engine picks #2 server (because the quality hard-filter rejects #1's videos). This is **Server being implicitly demoted below Quality**.
- If the user's #1 audio is missing on all servers AND audioFallback = ASK → the engine aborts BEFORE even checking quality or server. This is **Audio being implicitly promoted above Quality + Server**.

The user has no way to express: "I care about Server above all — if my #1 server only has 360p SUB, that's fine, take it" — because the audio+quality hard filters in Step 3 will reject it (unless both fallbacks are TRY_NEXT, in which case Step 4 will pick it but ONLY after the preferred-combo search across all servers fails).

### 4.3 The `serverFallback` dead code (compounds the gap)

As documented in §1.7, the `pref_dl_server_fallback` setting is exposed in the UI and stored in preferences, but **the engine never reads it**. So even the existing 3-way fallback model is incomplete — the user can configure "Server — If unavailable: Don't" and the engine will silently ignore it. This is a silent UX bug, and from the user's perspective it reinforces the feeling that "I can't control what's most important".

### 4.4 The "fallback strategy per dimension" model is also limited

Each dimension has its OWN independent fallback (`TRY_NEXT` / `ASK` / `DO_NOT_DOWNLOAD`). But the dimension priority is fixed (audio > quality > server for aborts; server > audio > quality for iteration). So:
- The user can't say "if the preferred QUALITY isn't on my preferred server, don't fall back to a different server — fall back to a lower quality on the same server."
- The user can't say "if my preferred audio isn't available on ANY server, that's a dealbreaker — but if it's available on my #2 server, take it (don't settle for SUB on my #1 server)."

These are exactly the conflicts the user wants resolved.

---

## 5. The settings page UI structure (for exact replication)

Source: `DownloadSettingsScreen.kt` (528 lines). The user said they want the EXACT same look/feel in the new project. Documenting the layout precisely here.

### 5.1 Top-level structure

```
Column(fillMaxSize) {
    CollapsingHeader(title = "Download settings", collapsed = collapsed)
    LazyColumn(
        state = lazyListState,
        contentPadding = PaddingValues(bottom = 110.dp),  // ← room for bottom nav
        verticalArrangement = spacedBy(8.dp),
    ) {
        item { SectionContainer("Download method") { ... } }
        item { SectionContainer("General") { ... } }
        item { SectionContainer("Auto-download") { ... } }
        if (autoDownload) {
            item { CollapsibleSection("Preferred quality", "drag to re-order") { ... } }
            item { CollapsibleSection("Preferred audio", "drag to re-order") { ... } }
            item { CollapsibleSection("Preferred server", "per extension") { ... } }
        }
    }
}
```

**`collapsed` (line 76-77)**: `lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 20` — the header shrinks once the user scrolls past row 0.

**`expandedSection` (line 122)**: `mutableIntStateOf(0)` — only one collapsible can be open at a time (1 = quality, 2 = audio, 3 = server). Tapping the open one closes it.

### 5.2 Section-by-section UI

#### Section 1: Download method (lines 134-184)

Container: `SectionContainer("Download method")`.

```
┌────────────────────────────────────────────┐
│ DOWNLOAD METHOD                            │  ← uppercase label, 11sp ExtraBold
│ ┌────────────────────────────────────────┐ │  ← surfaceVariant at 30% alpha,
│ │  [ Normal ]  [ Advanced ✓ ]            │ │     RoundedCornerShape(16.dp),
│ │                                         │ │     8.dp padding
│ │  Parallel threads          8           │ │  ← AnimatedVisibility(Advanced)
│ │  ───●──────────────────────────        │ │     SliderRow (1..8, 6 steps)
│ │  Max retries per chunk     10          │ │  ← SliderRow (0..10, 9 steps)
│ │  ─────●──────────────────────          │ │
│ │  Min size for multi-threading 1 MB     │ │  ← SliderRow (1..20, 18 steps)
│ │  ●────────────────────────────         │ │
│ └────────────────────────────────────────┘ │
└────────────────────────────────────────────┘
```

The 2-way toggle uses `SegmentedRowLocal` (defined locally, lines 504-527) — two `Surface` chips in a row, the selected one filled with `primary` colour + `onPrimary` text, the other transparent + `onSurfaceVariant`. Each chip is `weight(1f)`, 8.dp vertical padding, 13sp text.

The advanced sliders only appear when `downloadMethod == ADVANCED` via `AnimatedVisibility(visible = ..., enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut())`.

#### Section 2: General (lines 187-221)

Container: `SectionContainer("General")`. Rows in order: folder, show-button toggle, wifi-only toggle, concurrent slider.

```
┌────────────────────────────────────────────┐
│ GENERAL                                    │
│ ┌────────────────────────────────────────┐ │
│ │ Download folder                        │ │  ← SettingsRow
│ │ Folder: AniKuta Downloads            › │ │     (or "Not set — tap to choose")
│ │──────────────────────────────────────│ │
│ │ Show download button               [✓] │ │  ← ToggleRow
│ │ Display the download icon on episodes  │ │
│ │──────────────────────────────────────│ │
│ │ Wi-Fi only                         [✓] │ │
│ │ Pause downloads on mobile data         │ │
│ │──────────────────────────────────────│ │
│ │ Concurrent downloads           1       │ │  ← SliderRow (1..5, 3 steps)
│ │ ●──────────────────────────            │ │
│ └────────────────────────────────────────┘ │
└────────────────────────────────────────────┘
```

The folder row uses `ActivityResultContracts.OpenDocumentTree()` (line 109-120) — tapping opens the system folder picker. On URI return, the pref is written + persistable read/write permission taken.

The `ToggleRow` (lines 447-459) is: `Row(padding(horizontal=12.dp, vertical=10.dp)) { Column(weight=1f) { Text(title, 14sp, ExtraBold, onSurface); Text(subtitle, 12sp, onSurfaceVariant) }; Spacer(12.dp); Switch(...) }`.

The `SliderRow` (lines 463-477) is: `Column(padding(horizontal=12.dp, vertical=8.dp)) { Row { Text(label, weight=1f, 14sp ExtraBold onSurface); Text(valueText, 14sp Bold primary) }; Slider(...) }`.

#### Section 3: Auto-download (lines 224-233)

Container: `SectionContainer("Auto-download")`. Just one ToggleRow.

```
┌────────────────────────────────────────────┐
│ AUTO-DOWNLOAD                              │
│ ┌────────────────────────────────────────┐ │
│ │ Automatic video selection          [✓] │ │
│ │ Auto-select your preferences           │ │
│ └────────────────────────────────────────┘ │
└────────────────────────────────────────────┘
```

When toggled ON, sections 4-6 (below) appear. When OFF, they're hidden entirely.

#### Sections 4-6: Preferred quality / audio / server (only when autoDownload is ON, lines 236-310)

Each is a `CollapsibleSection` (lines 339-372). The header is `Row(padding(horizontal=16.dp, vertical=14.dp)) { Text(title, 16sp ExtraBold onSurface, weight=1f); Text(subtitle, 11sp onSurfaceVariant, padding(end=8.dp)); Icon(ChevronRight, 20dp, rotate(if expanded 90f else 0f)) }`. Tapping toggles `expandedSection` (mutual exclusion — only one open at a time).

When collapsed:
```
┌────────────────────────────────────────────┐
│ PREFERRED QUALITY — drag to re-order    ›  │
├────────────────────────────────────────────┤
│ PREFERRED AUDIO — drag to re-order     ›   │
├────────────────────────────────────────────┤
│ PREFERRED SERVER — per extension        ›  │
└────────────────────────────────────────────┘
```

When quality is expanded:
```
┌────────────────────────────────────────────┐
│ PREFERRED QUALITY — drag to re-order    ⌄  │
│ ┌────────────────────────────────────────┐ │
│ │ 1. 1080p                          ≡    │ │  ← DragReorderableList
│ │ 2. 720p                           ≡    │ │     (48dp rows, drag handle right)
│ │ 3. 480p                           ≡    │ │
│ │ 4. 360p                           ≡    │ │
│ │──────────────────────────────────────│ │
│ │ If unavailable                         │ │  ← FallbackToggle label
│ │ [ Try next ] [ Ask ] [ Don't ]         │ │  ← SegmentedRowLocal (3-way)
│ └────────────────────────────────────────┘ │
└────────────────────────────────────────────┘
```

Audio section: identical structure, with `["SUB", "DUB"]` items + a `FallbackToggle`.

Server section: shows "No trusted extensions installed. Install an extension from Browse → Extensions to get started." if `extensionSources.isEmpty()`. Otherwise, one `CollapsibleExtensionSection` per extension (mutual exclusion via `expandedExtension` index, line 291):

```
┌────────────────────────────────────────────┐
│ PREFERRED SERVER — per extension        ⌄  │
│ ┌────────────────────────────────────────┐ │
│ │ ▸ Aniyomi — aniyomi.org             ›  │ │  ← extension row (name=primary,
│ │   (no servers discovered yet)          │ │     source=onSurfaceVariant)
│ │──────────────────────────────────────│ │
│ │ ▾ Animepahe — animepahe.com         ⌄  │ │  ← expanded
│ │ ┌──────────────────────────────────┐ │ │
│ │ │ 1. Vidstreaming              ≡   │ │ │  ← DragReorderableList of servers
│ │ │ 2. Streamtape                ≡   │ │ │
│ │ │ 3. Beta Server               ≡   │ │ │
│ │ └──────────────────────────────────┘ │ │
│ │──────────────────────────────────────│ │
│ │ ▸ Crunchyroll — crunchy.com        ›  │ │
│ └────────────────────────────────────────┘ │
│──────────────────────────────────────────│ │
│ If unavailable                            │ │  ← server fallback (3-way)
│ [ Try next ] [ Ask ] [ Don't ]            │ │
└────────────────────────────────────────────┘
```

`CollapsibleExtensionSection` (lines 375-431) computes the merged server list (user order + discovered) on every recomposition. Empty state: "No servers discovered yet. Browse or watch anime from this source to discover servers." (line 413).

### 5.3 Section container components (the private composables)

| Component | Lines | Purpose |
|---|---|---|
| `SectionContainer(label, content)` | 320-336 | Outermost card. Uppercase label (11sp ExtraBold onSurfaceVariant) + `Surface(surfaceVariant@30%, RoundedCornerShape(16.dp))` wrapping the content (8.dp padding). |
| `CollapsibleSection(title, subtitle, isExpanded, onToggle, content)` | 339-372 | Header row + `AnimatedVisibility` content. Same surface styling. Header chevron rotates 90° when expanded. |
| `CollapsibleExtensionSection(extSource, serverDiscovery, preferences, isExpanded, onToggle)` | 375-431 | Per-extension collapsible. Slightly different surface colour (`surfaceVariant@20%`, `RoundedCornerShape(12.dp)`). |
| `SettingsRow(title, subtitle, onClick)` | 434-444 | Tappable row (no trailing icon). Used for folder picker. |
| `ToggleRow(title, subtitle, checked, onCheckedChange)` | 447-459 | Label + subtitle on left, `Switch` on right. |
| `SliderRow(label, value, range, steps, valueText, onChange)` | 463-477 | Label + value on top, `Slider` below. |
| `FallbackToggle(label, strategy, onSelect)` | 484-501 | Label + 3-way `SegmentedRowLocal` (Try next / Ask / Don't). |
| `SegmentedRowLocal(options, onSelect)` | 504-527 | N-way segmented toggle (used for 2-way method toggle + 3-way fallback). |

### 5.4 Visual design tokens (replicate exactly)

- **Font family:** `RobotoFamily` everywhere (imported from `:core:designsystem`).
- **Colours:** all from `MaterialTheme.colorScheme` — `surface`, `surfaceVariant` (30% alpha for cards, 20% for nested extension cards), `primary` (selected segment + value text + extension name), `onPrimary` (selected segment text), `onSurface` (titles + values), `onSurfaceVariant` (subtitles + uppercase labels + chevrons).
- **Shapes:** `RoundedCornerShape(16.dp)` for outer section cards, `12.dp` for inner rows + extension cards + drag-reorder rows + segmented chips, `8.dp` for segmented chip inner Surface.
- **Spacings:** section horizontal padding 12.dp (vertical 4.dp), label padding `start=8.dp, top=8.dp, bottom=4.dp`, row padding `horizontal=12-16.dp, vertical=10-14.dp`, drag handle 48×48dp, slider section padding `horizontal=12.dp, vertical=8.dp`.
- **Animations:** `expandVertically() + fadeIn()` for show, `shrinkVertically() + fadeOut()` for hide. Used for both the Advanced sub-section and the collapsible content.

### 5.5 The picker sheet (manual mode fallback) — `DownloadVideoPickerSheet.kt`

When `autoDownload` is OFF (or fallback = ASK), the orchestrator returns `EnqueueResult.ShowPicker` and the host shows this sheet. The user wants the SAME sheet in the new project (it's the manual-mode counterpart to the auto engine).

```
┌────────────────────────────────────────────┐  ← ModalBottomSheet (no drag handle,
│ Choose video to download                   │     RoundedCornerShape(topStart=24, topEnd=24),
│ Frieren: Beyond Journey's End — EP 1       │     containerColor = surface)
│                                            │
│ ┌────────────────────────────────────────┐ │  ← ServerCard (single-expand accordion)
│ │ ▾ Vidstreaming                      ⌄  │ │
│ │   SUB                                  │ │  ← audio label (12sp ExtraBold onSurfaceVariant)
│ │   [▶ 1080p] [▶ 720p] [▶ 360p]          │ │  ← FlowRow of QualityButton chips
│ │   DUB                                  │ │     (primaryContainer, RoundedCornerShape(6.dp),
│ │   [▶ 720p]                             │ │      10×6 padding, PlayArrow icon + 12sp ExtraBold)
│ │──────────────────────────────────────│ │
│ │ ▸ Streamtape                        ›  │ │  ← collapsed ServerCard
│ └────────────────────────────────────────┘ │
└────────────────────────────────────────────┘
```

Single-expand accordion (`expandedServer: String?`, line 70) — only one server open at a time. First server expanded by default. Tapping a `QualityButton` calls `onVideoSelected(video, server.name, audio.label)` which routes to `DownloadOrchestrator.enqueueSpecific` (no auto-resolution — the user explicitly chose).

### 5.6 The More-screen entry — `DownloadsMoreEntries.kt`

```kotlin
@Composable
fun DownloadsMoreEntries(onOpenDownloads: () -> Unit) {
    Column {
        MoreSectionLabel(text = "Library")
        MoreListRow(
            icon = Icons.Filled.Download,
            title = "Downloads",
            subtitle = "Manage downloaded episodes and the download queue",
            onClick = onOpenDownloads,
        )
    }
}
```

The host (`AnikutaRoot`) wires this into the More screen's `LazyColumn` with one `item { DownloadsMoreEntries(...) }`. **The Download settings screen is reached via the gear icon in the top bar of the Downloads screen** (`DownloadsScreen.kt` — the settings gear is `IconButton(onClick = onOpenSettings)` in the `CollapsingHeader`'s actions slot). NOT directly from the More menu.

---

## 6. Design recommendation: the NEW priority resolution engine

This is the proposal for the new ANI-KUTA project. The goal: keep the drag-reorder UI the user likes, but ADD a unified 3-dimensional priority system where the user can rearrange the DIMENSIONS themselves (audio vs quality vs server) AND the resolution engine handles conflicts gracefully + is highly customizable for future changes.

### 6.1 The data model

#### 6.1.1 New preference: dimension priority order

Add a FOURTH preference list — the dimension priority order. This is the user's answer to "which thing is the most important":

```kotlin
enum class PreferenceDimension {
    AUDIO,
    QUALITY,
    SERVER,
}

// In DownloadPreferences.kt (new):
fun dimensionPriority(): Preference<List<PreferenceDimension>> =
    store.getObject(
        KEY_DIMENSION_PRIORITY,
        // REVIEW-5 M45 (R2-I1): the OLD draft claimed this default "preserves old behaviour" —
        // that was FALSE. The OLD project's effective priority was INCONSISTENT (check-layer:
        // AUDIO > QUALITY; iteration-layer: SERVER > AUDIO > QUALITY). Neither matches
        // [AUDIO, QUALITY, SERVER]. This default is a DELIBERATE behavioural change reflecting
        // typical user intent (audio is usually the most important for sub/dub preferences).
        // The OLD behaviour is NOT preserved — users who relied on the OLD iteration order
        // (server-first) can flip the dimension priority to [SERVER, AUDIO, QUALITY] in Settings.
        DEFAULT_DIMENSION_PRIORITY,  // [AUDIO, QUALITY, SERVER] — DELIBERATE change (see M45)
        { json.encodeToString(ListSerializer(...), it) },
        { ... decode ... },
    )

companion object {
    val DEFAULT_DIMENSION_PRIORITY = listOf(
        PreferenceDimension.AUDIO,
        PreferenceDimension.QUALITY,
        PreferenceDimension.SERVER,
    )
}
```

So the user has FOUR reorderable lists in the new settings UI:
1. **Dimension priority** (new) — the 3 dimensions in order of importance.
2. Preferred audio values (existing).
3. Preferred quality values (existing).
4. Preferred server values per extension (existing).

#### 6.1.2 Unified per-dimension fallback strategy

Keep the existing `FallbackStrategy` enum (`TRY_NEXT` / `ASK` / `DO_NOT_DOWNLOAD`). Each dimension has its own fallback (already the case in the old prefs). Plus a NEW global fallback strategy:

```kotlin
enum class GlobalFallbackStrategy {
    // After all dimensions' per-value lists are exhausted, fall back to ANY available
    // video (best-effort, ignoring preference lists). The old Step 4 behaviour.
    BEST_EFFORT,
    // After all dimensions' per-value lists are exhausted, show the picker sheet.
    ASK,
    // After all dimensions' per-value lists are exhausted, fail with an error.
    DO_NOT_DOWNLOAD,
}

fun globalFallback(): Preference<GlobalFallbackStrategy> =
    store.getEnum(KEY_GLOBAL_FALLBACK, GlobalFallbackStrategy.BEST_EFFORT)
```

#### 6.1.3 The resolved video tree is unchanged

Same `ResolverServer → ResolverAudioVersion → ResolverVideo` model. The new engine consumes the same tree.

### 6.2 The resolution algorithm

The core idea: **rank every leaf `ResolverVideo` by a tuple of preference-ranks, where the tuple order is the dimension priority.** Then pick the highest-ranked leaf. If no leaf has all-preferred values, apply the per-dimension fallback strategies + the global fallback.

#### 6.2.1 Step 1 — Flatten the tree

Flatten the resolved tree into a list of "candidate" records, each carrying the video + its server name + its audio label:

```kotlin
data class Candidate(
    val video: ResolverVideo,
    val serverName: String,
    val audioLabel: String,
    val serverRank: Int,    // 0 = top-pref server, Int.MAX_VALUE = unlisted
    val audioRank: Int,     // 0 = top-pref audio, Int.MAX_VALUE = unlisted
    val qualityRank: Int,   // 0 = top-pref quality, Int.MAX_VALUE = unlisted
    val isServerPreferred: Boolean,  // serverRank < Int.MAX_VALUE
    val isAudioPreferred: Boolean,
    val isQualityPreferred: Boolean,
)
```

#### 6.2.2 Step 2 — Compute the rank tuple per the dimension priority

For each candidate, build a rank tuple in the user's dimension-priority order. E.g. if `dimensionPriority = [AUDIO, QUALITY, SERVER]`, the tuple is `(audioRank, qualityRank, serverRank)`. If `[SERVER, AUDIO, QUALITY]`, it's `(serverRank, audioRank, qualityRank)`.

Sort all candidates by this tuple (ascending — lower rank = better). The first candidate is the strict best.

#### 6.2.3 Step 3 — Apply per-dimension fallback strategies

For each dimension IN dimension-priority order, check whether ANY candidate has its preferred value for that dimension:

```
for (dim in dimensionPriority):
    topPref = prefsFor(dim).firstOrNull()  // the user's #1 pick for this dim
    if (topPref == null) continue          // no pref configured → skip the check
    hasTopPref = candidates.any { it.matchesTopPref(dim) }
    if (!hasTopPref):
        when (fallbackFor(dim)):
            ASK → return ShowPicker(...)
            DO_NOT_DOWNLOAD → return Error("No {dim} matching your preferences...")
            TRY_NEXT → continue   // the rank tuple already prefers the next-best
```

This step is equivalent to the old Steps 1 + 2, but generalized to all three dimensions + applied in the user-defined priority order. It fixes the silent `serverFallback` dead-code bug — now ALL three fallbacks are consulted, AND in the right order.

#### 6.2.4 Step 4 — Pick the best candidate (with conflict resolution)

After Step 3 passes (or `TRY_NEXT` continues), iterate the sorted candidates:

```
for (candidate in sortedCandidates):
    // Hard filter: respect per-dimension DO_NOT_DOWNLOAD semantics —
    // if a dimension's fallback is DO_NOT_DOWNLOAD, we already returned in Step 3.
    // For TRY_NEXT, accept the candidate even if some dimensions are non-preferred.
    return Selected(candidate.video, candidate.serverName, candidate.audioLabel)
```

So the candidate with the lowest rank tuple wins. The rank tuple naturally handles conflicts:
- If dimension priority is `[AUDIO, QUALITY, SERVER]` and the user's top audio is available on multiple candidates, the tiebreaker is quality (next in priority), then server.
- If dimension priority is `[SERVER, AUDIO, QUALITY]` and the user's top server is available, ALL candidates on that server are considered before any candidate on the #2 server — even if the #2 server has the user's top audio + top quality.

#### 6.2.5 Step 5 — Global fallback (REVIEW-5 M44 — fires on non-perfect matches, NOT on empty candidates)

> **REVIEW-5 M44 (R2-C2):** the OLD draft's Step 5 only fired when `sortedCandidates.isEmpty()`.
> This is useless UX — if there are zero candidates, there are also zero servers to show in the
> picker (`ASK` would show an empty picker). The proper semantic: fire based on the picked
> candidate's MATCH QUALITY (perfect vs. best-effort), not on `sortedCandidates.isEmpty()`.
>
> The new logic:
> 1. If `sortedCandidates.isEmpty()` → always `DO_NOT_DOWNLOAD`/`ERROR` (no candidates at all).
> 2. Otherwise, pick the first sorted candidate (Step 4).
> 3. Check the picked candidate's `isPerfectMatch` flag (= `audioRank == 0 && qualityRank == 0 &&
>    serverRank == 0` — all three dimensions are at the user's top preference).
> 4. If `isPerfectMatch` → return the picked candidate (success).
> 5. If NOT `isPerfectMatch` → fire `globalFallback`:
>    - `BEST_EFFORT` → return the picked candidate (best-effort).
>    - `ASK` → return `ShowPicker(sortedCandidates)` (the user picks from the best-effort set).
>    - `DO_NOT_DOWNLOAD` → return `Error("No perfect match — user chose to not download best-effort")`.

```kotlin
// Step 4 — pick the first sorted candidate.
val picked = sortedCandidates.firstOrNull()
    ?: return when (globalFallback) {
        // Step 5a — zero candidates at all.
        GlobalFallbackStrategy.BEST_EFFORT,
        GlobalFallbackStrategy.DO_NOT_DOWNLOAD -> Selection.NoMatch("No video sources available at all")
        GlobalFallbackStrategy.ASK -> Selection.ShowPicker(emptyList())
    }

// Step 5b — REVIEW-5 M44: fire globalFallback based on the picked candidate's match quality.
val isPerfectMatch = picked.audioRank == 0 && picked.qualityRank == 0 && picked.serverRank == 0
if (isPerfectMatch) {
    return Selection.Selected(picked.video, picked.context)
}
// Best-effort pick (one or more dimensions are non-preferred).
return when (globalFallback) {
    GlobalFallbackStrategy.BEST_EFFORT -> Selection.Selected(picked.video, picked.context)
    GlobalFallbackStrategy.ASK -> Selection.ShowPicker(sortedCandidates)  // user picks from the best-effort set
    GlobalFallbackStrategy.DO_NOT_DOWNLOAD -> Selection.NoMatch(
        "No perfect match for your preferences (audio/quality/server) — " +
            "global fallback is DO_NOT_DOWNLOAD. Picked: audio=${picked.audio}, " +
            "quality=${picked.quality}, server=${picked.server}"
    )
}
```

This makes `ASK` useful (shows a non-empty picker of best-effort candidates) + `DO_NOT_DOWNLOAD`
honorable (fails when the user's preferences can't be perfectly met, not just when zero candidates exist).

### 6.3 Concrete worked example

**User settings:**
- `dimensionPriority = [AUDIO, QUALITY, SERVER]`  (user says audio matters most)
- `audioPrefs = ["DUB", "SUB"]`
- `qualityPrefs = ["1080p", "720p"]`
- `serverPrefs = ["Streamtape", "Vidstreaming"]`
- `audioFallback = TRY_NEXT`, `qualityFallback = TRY_NEXT`, `serverFallback = TRY_NEXT`
- `globalFallback = BEST_EFFORT`

**Resolved video tree** (same as §3.5):
```
Streamtape (user's #1 server):
  SUB:  1080p, 720p       (no DUB)
Vidstreaming (user's #2 server):
  SUB:  720p
  DUB:  1080p
```

**Step 1 — Flatten:**
| Candidate | server | audio | quality | serverRank | audioRank | qualityRank |
|---|---|---|---|---|---|---|
| A | Streamtape | SUB | 1080p | 0 | 1 | 0 |
| B | Streamtape | SUB | 720p  | 0 | 1 | 1 |
| C | Vidstreaming | SUB | 720p  | 1 | 1 | 1 |
| D | Vidstreaming | DUB | 1080p | 1 | 0 | 0 |

**Step 2 — Sort by `(audioRank, qualityRank, serverRank)`:**
1. **D** — `(0, 0, 1)` ← best (top audio, top quality, #2 server)
2. A — `(1, 0, 0)` (SUB, 1080p, Streamtape)
3. B — `(1, 1, 0)` (SUB, 720p, Streamtape)
4. C — `(1, 1, 1)` (SUB, 720p, Vidstreaming)

**Step 3 — Per-dim fallback checks** (in `[AUDIO, QUALITY, SERVER]` order):
- AUDIO: topPref = "DUB". `hasTopPref = true` (candidate D). ✓ continue.
- QUALITY: topPref = "1080p". `hasTopPref = true` (candidates A, D). ✓ continue.
- SERVER: topPref = "Streamtape". `hasTopPref = true` (candidates A, B). ✓ continue.

**Step 4 — Pick first:** Candidate D = **Vidstreaming / DUB / 1080p.**

**Compare to the old engine's result:** Streamtape / SUB / 1080p (§3.5).

**The difference:** With audio as the top-priority dimension, the new engine correctly picks DUB on Vidstreaming (the only server with DUB), even though Streamtape is the user's #1 server. This is exactly what the user wants — "audio is the most important".

#### 6.3.1 Now flip the dimension priority — `[SERVER, QUALITY, AUDIO]`

Same candidates, same prefs. Sort by `(serverRank, qualityRank, audioRank)`:
1. **A** — `(0, 0, 1)` ← best (Streamtape, 1080p, SUB)
2. B — `(0, 1, 1)` (Streamtape, 720p, SUB)
3. D — `(1, 0, 0)` (Vidstreaming, 1080p, DUB)
4. C — `(1, 1, 1)` (Vidstreaming, 720p, SUB)

**Step 4 — Pick first:** Candidate A = **Streamtape / SUB / 1080p.**

**Compare:** Now the engine picks Streamtape/SUB/1080p — the user said "server is the most important, then quality, then audio". This matches the old engine's behaviour for this case (coincidentally), but now it's CONFIGURABLE + the resolution path is consistent (no separate "check then iterate" layers).

#### 6.3.2 Edge case — top-preferred audio is unavailable entirely

**User settings:**
- `dimensionPriority = [AUDIO, QUALITY, SERVER]`
- `audioPrefs = ["DUB"]` (no fallback to SUB)
- `audioFallback = DO_NOT_DOWNLOAD`
- Other prefs as above.

**Resolved tree:** Same as above (DUB only on Vidstreaming — but DUB IS available).

**Step 3 — AUDIO check:** topPref = "DUB". `hasTopPref = true` (candidate D). ✓ continue.

**Result:** D = Vidstreaming / DUB / 1080p.

Now suppose DUB isn't available anywhere (only SUB):
- **Step 3 — AUDIO check:** `hasTopPref = false`. `audioFallback = DO_NOT_DOWNLOAD` → return `Error("No audio version matching your preferences ([DUB]). Adjust your download settings or switch to manual mode.")`.

This is the same error UX as the old engine (line 121-125) — but generalized + applied per the dimension-priority order.

### 6.4 How this stays "highly customizable so that in the future we can change this logic easily"

The new engine is structured as a pipeline of pure functions, each testable in isolation:

```kotlin
object AutoDownloadEngine {
    // Pure: tree → flat candidate list.
    fun flatten(servers: List<ResolverServer>, qualityPrefs, audioPrefs, serverPrefs): List<Candidate>

    // Pure: candidates + dimensionPriority → sorted candidates.
    fun rank(candidates: List<Candidate>, dimensionPriority: List<PreferenceDimension>): List<Candidate>

    // Pure: ranked candidates + per-dim fallbacks → either a Selected or a FallbackDecision.
    fun applyFallbacks(
        ranked: List<Candidate>,
        dimensionPriority: List<PreferenceDimension>,
        prefs: Map<PreferenceDimension, List<String>>,
        fallbacks: Map<PreferenceDimension, FallbackStrategy>,
    ): FallbackDecision

    // The orchestrator-facing entry point. Composes the above + handles ASK / DO_NOT_DOWNLOAD / BEST_EFFORT.
    fun selectBestVideo(
        sourceId: Long,
        servers: List<ResolverServer>,
        preferences: DownloadPreferences,
    ): Selection
}
```

Future customizations are easy:
- **Add a 4th dimension** (e.g. "subtitles language"): add a `PreferenceDimension.SUBTITLES` enum value + a corresponding prefs list + the rank tuple naturally extends. No algorithm change.
- **Per-dimension weights** (instead of strict lexicographic order): swap the rank tuple for a weighted score. `rank(candidates, weights)` instead of `rank(candidates, dimensionPriority)`. The pipeline shape doesn't change.
- **Per-source dimension priority**: lift `dimensionPriority` from a single global pref to `Map<sourceId, List<PreferenceDimension>>` (same shape as `serverPreferences`). The engine doesn't care where the priority comes from.
- **"Strict mode"** (must match ALL preferred dimensions, no TRY_NEXT): add a per-dimension "strict" flag — already partially captured by `DO_NOT_DOWNLOAD`, but a separate `STRICT` enum value would let the UI distinguish "don't download" from "must match exactly".
- **Conflict resolution rules**: instead of `applyFallbacks` returning a single decision, it could return a `List<Conflict>` that the UI surfaces to the user as "Your #1 audio (DUB) isn't on your #1 server (Streamtape). Pick: DUB on Vidstreaming / SUB on Streamtape / Show picker" — the engine already has the data to do this, just needs a richer return type.

The pipeline's purity also makes it trivially unit-testable: `flatten` + `rank` + `applyFallbacks` are pure functions over data classes. The old `selectBestVideo` is a 100-line method with interleaved reads + branching — much harder to test.

### 6.5 The new settings UI (additive to the existing one)

To keep the user's beloved drag-reorder UI, ADD ONE new collapsible section ABOVE the existing three preference-list sections:

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

The `DragReorderableList` component is reused as-is (it takes `List<String>`, so we render the dimension names as strings — `["Audio", "Quality", "Server"]` — and map back to enum values when persisting). The user gets the SAME drag-and-drop UX they're familiar with, applied at the new dimension-priority level.

A future enhancement could make the dimensions display richer (e.g. an icon next to each: 🎵 / 📐 / 🌐) — but that's a visual polish, not a structural change. The `DragReorderableList` would need to be generalized to `List<T>` + a `toString` mapping for that.

### 6.6 Summary of the proposed changes

| Change | Old | New |
|---|---|---|
| Dimension priority order | Hardcoded (audio > quality > server at check layer; server > audio > quality at iteration layer — inconsistent) | User-configurable via a 4th drag-reorder list. Default `[AUDIO, QUALITY, SERVER]` (preserves old semantics as the starting point). |
| Server fallback strategy | Declared in prefs but NEVER consulted by the engine | Consulted in Step 3, in the user-defined dimension order |
| Resolution algorithm | 4-step imperative with early exits + iteration | 5-step pipeline: flatten → rank → fallback-check → pick → global-fallback. All pure functions. |
| Conflict handling | Implicit via loop order | Explicit via rank tuple (lexicographic over the dimension priority) |
| Customizability for future changes | Adding a dimension = rewriting `selectBestVideo` | Adding a dimension = adding an enum value + a prefs list (the pipeline doesn't change) |
| UI for the new dimension priority | N/A | One new `CollapsibleSection` above the existing 3, using the same `DragReorderableList` component |
| Per-dimension fallback strategies | 3 (one per dimension, but server's is ignored) | 3 (one per dimension, ALL consulted) + 1 global fallback (BEST_EFFORT / ASK / DO_NOT_DOWNLOAD) |

### 6.7 What to NOT change (preserve user's beloved UX)

- The 528-line `DownloadSettingsScreen.kt` layout — sections, components, colours, spacings, animations — replicate as-is.
- The `DragReorderableList` component — replicate as-is (the new dimension-priority list uses the same component).
- The `DownloadVideoPickerSheet` — replicate as-is (still used when autoDownload is OFF, or when ANY per-dimension fallback is ASK, or when global fallback is ASK).
- The `DownloadsMoreEntries` More-screen entry — replicate as-is.
- The `ServerDiscoveryStore` passive recording pattern — replicate as-is.
- The `DownloadOrchestrator`'s two-mode API (`enqueueDownload` for auto + `enqueueSpecific` for manual) — replicate as-is. Only the internal `selectBestVideo` impl changes.

---

## 7. TL;DR for the implementation team

1. **The current auto-download system is solid** — 15 settings (5 general, 1 auto-pick master switch, 3 preference lists, 3 per-dim fallbacks, 3 advanced method params), a clean drag-reorder UI, a manual-mode picker sheet, and a 4-step resolution algorithm.
2. **The gap the user identified is real** — the priority order between audio/quality/server is HARDCODED + INCONSISTENT (different priority at the check layer vs the iteration layer). And `serverFallback` is dead code (declared but never consulted).
3. **The fix is additive** — keep ALL the existing UI + settings, ADD one new preference list (`dimensionPriority`) + one new global fallback, replace the `selectBestVideo` impl with a 5-step pure-function pipeline (`flatten → rank → fallback-check → pick → global-fallback`).
4. **The new UI is one new collapsible section** above the existing three — uses the same `DragReorderableList` component the user already loves. No new component needed.
5. **The new engine is highly customizable** — adding a 4th dimension, weighted scoring, per-source priority, conflict-surfacing UI — all are incremental additions to the pipeline, not rewrites.

The user's exact words — "highly customizable so that in the future we can change this logic easily" — are satisfied by the pure-function pipeline design + the dimension-priority abstraction. The user's "drag and drop and rearrange" UX is preserved 1:1, just extended to also reorder the dimensions themselves.
