# 01 — Workflow: From Download Button Click → Queue Entry

> Trace of EXACTLY what happens when the user taps the download button on an episode row in the anime-details page. All file:line references are from the old project at `REFERENCES/old-kuta/ANIKUTA/`.

## 1. The trigger UI: `EpisodeDownloadControl` (per-episode row)

**File**: `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/EpisodeDownloadControl.kt`

A state-driven composable that renders different controls based on `EpisodeDownloadState`:

```
NotDownloaded → IconButton(Icons.Filled.Download, onClick = onDownload)   [line 64-72]
Resolving     → CircularProgressIndicator + CancelButton                  [line 75-85]
Queued        → CircularProgressIndicator + CancelButton                  [line 87-95]
Downloading(p)→ LinearProgressIndicator(p) + CancelButton                 [line 97-118]
Paused        → IconButton(Icons.Filled.PlayArrow, onResume) + Cancel     [line 120-130]
Error(msg)    → IconButton(Icons.Filled.Refresh, onRetry) + Cancel        [line 132-142]
Downloaded    → Icon(Icons.Filled.CheckCircle) + IconButton(Delete)       [line 144-161]
```

`EpisodeDownloadState` is a sealed interface defined in **`EpisodeDownloadState.kt`** — lives in `:feature:anime-details` so the feature module does NOT depend on `:core:download`.

## 2. How `EpisodeDownloadControl` is wired into the episode row

**File**: `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/EpisodesSection.kt`

`EpisodeRow` composable (line 440) takes these download callbacks (lines 448-453):

```kotlin
onDownload: () -> Unit = {},
downloadState: EpisodeDownloadState = EpisodeDownloadState.NotDownloaded,
onDownloadCancel: () -> Unit = {},
onDownloadResume: () -> Unit = {},
onDownloadRetry: () -> Unit = {},
onDownloadDelete: () -> Unit = {},
```

The row renders the control at line 642-651:

```kotlin
// EpisodesSection.kt:638-651
if (showDownloadBtn || downloadState != EpisodeDownloadState.NotDownloaded) {
    EpisodeDownloadControl(
        state = downloadState,
        onDownload = onDownload,
        onCancel = onDownloadCancel,
        onResume = onDownloadResume,
        onRetry = onDownloadRetry,
        onDelete = onDownloadDelete,
    )
}
```

`showDownloadBtn` comes from `displayPrefs?.showDownloadButton` (the user's "Show download button" pref, default `true`). Note: if the episode has ANY non-`NotDownloaded` state, the control is shown regardless — so the user can always manage an existing download.

## 3. `EpisodesSection` → `DetailContent` → `AnimeDetailScreen` → host

`EpisodesSection` (line 108) takes the download callbacks at the section level:

```kotlin
// EpisodesSection.kt:107-114
onDownloadEpisode: (SEpisode, AnimeSource) -> Unit = { _, _ -> },
downloadStates: Map<String, EpisodeDownloadState> = emptyMap(),
onDownloadCancel: (String) -> Unit = {},
onDownloadResume: (String) -> Unit = {},
onDownloadRetry: (String) -> Unit = {},
onDownloadDelete: (String) -> Unit = {},
```

`downloadStates` is keyed by **episode URL** (`SEpisode.url`). The row looks up its state via `downloadStates[episode.url]`.

**`DetailContent.kt:216-219`** is the next layer up:

```kotlin
onDownloadEpisode = { episode, source ->
    onDownloadEpisode(episode, source, watchCtx)
},
downloadStates = downloadStates,
```

It adds the `WatchEpisodeContext` (anime title + cover URL + anilistId) and re-exports the same `downloadStates` map.

**`AnimeDetailScreen.kt:83-86`** takes the host-provided callbacks:

```kotlin
onDownloadEpisode: (SEpisode, AnimeSource, WatchEpisodeContext) -> Unit = { _, _, _ -> },
downloadStates: Map<String, EpisodeDownloadState> = emptyMap(),
```

And `AnimeDetailScreen.kt:276-277` passes them down into `DetailContent`.

## 4. The host: `AppController.downloadEpisode(...)` (the actual work)

**File**: `app/src/main/java/app/confused/anikuta/navigation/AppController.kt`

`AppController` is the app-wide state holder. It is Koin-injected with `DownloadManager` and `DownloadOrchestrator`. The download-tap entry point:

```kotlin
// AppController.kt:1046-1087
fun downloadEpisode(
    episode: SEpisode,
    source: AnimeSource,
    watchCtx: WatchEpisodeContext,
    contentId: String,
) {
    if (contentId.isBlank()) {
        Log.w(TAG, "downloadEpisode: blank contentId — cannot enqueue (anilistId fallback should have produced one)")
        Toast.makeText(context, "Cannot download — no content identity for this anime", Toast.LENGTH_SHORT).show()
        return
    }
    val animeInfo = app.confused.anikuta.core.download.DownloadAnimeInfo(
        contentId = contentId,
        title = watchCtx.animeTitle.ifBlank { "Anime" },
        coverUrl = watchCtx.coverUrl,
    )
    // Immediate Resolving state on the row — instant feedback.
    resolvingEpisodes[episode.url] = true
    Log.i(TAG, "Download requested: ${animeInfo.title} EP ${episode.episode_number} (contentId=$contentId)")
    scope.launch {
        try {
            val result = downloadOrchestrator.enqueueDownload(animeInfo, episode, source)
            when (result) {
                is EnqueueResult.Success ->
                    Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
                is EnqueueResult.ShowPicker -> {
                    downloadPickerTarget = result
                }
                is EnqueueResult.NoSources ->
                    Toast.makeText(context, "No video sources available for this episode", Toast.LENGTH_LONG).show()
                is EnqueueResult.Error ->
                    Toast.makeText(context, "Download failed: ${result.message}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download enqueue failed", e)
            Toast.makeText(context, "Download failed: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        } finally {
            resolvingEpisodes.remove(episode.url)
        }
    }
}
```

Key things:
- `resolvingEpisodes: SnapshotStateMap<String, Boolean>` — drives the row's `Resolving` spinner state (instant feedback before the resolver returns).
- The `contentId` is the source-independent identity (e.g. `"al:154587"` for AniList-linked, `"aniyomi:123:url"` for unlinked extension anime). The OLD pre-Phase-6 hard gate `if (anilistId == 0) return` was REMOVED — unlinked anime are now downloadable.
- On `ShowPicker` it stashes the result in `downloadPickerTarget` state — `AnikutaRoot.kt:301-313` observes this and renders `DownloadVideoPickerSheet`.

## 5. The picker sheet (manual mode)

**File**: `feature/download/src/main/java/app/confused/anikuta/feature/download/DownloadVideoPickerSheet.kt`

A `ModalBottomSheet` that shows the resolver's `List<ResolverServer>` as an accordion:
- Each `Server` is an expandable card.
- Inside: each `AudioVersion` (`audio.label` — e.g. "SUB", "DUB") with a `FlowRow` of `QualityButton`s.
- Tapping a quality button calls `onVideoSelected(video, serverName, audioLabel)`.

```kotlin
// DownloadVideoPickerSheet.kt:60-125
@Composable
fun DownloadVideoPickerSheet(
    servers: List<ResolverServer>,
    animeTitle: String,
    episodeName: String,
    onVideoSelected: (ResolverVideo, String, String) -> Unit,
    onDismiss: () -> Unit,
)
```

**When it's shown**: when `preferences.autoDownload().get() == false`, OR when auto-download is ON but `FallbackStrategy.ASK` triggers (preferred quality/audio unavailable).

`AnikutaRoot.kt:301-313` renders it:

```kotlin
val downloadPickerTarget = appController.downloadPickerTarget
if (downloadPickerTarget != null) {
    DownloadVideoPickerSheet(
        servers = downloadPickerTarget.servers,
        animeTitle = downloadPickerTarget.anime.title,
        episodeName = downloadPickerTarget.episode.name,
        onVideoSelected = { video, serverName, audioLabel ->
            appController.enqueuePickedVideo(video, serverName, audioLabel)
        },
        onDismiss = { appController.dismissDownloadPicker() },
    )
}
```

## 6. `AppController.enqueuePickedVideo(...)` — the manual path

```kotlin
// AppController.kt:1090-1117
fun enqueuePickedVideo(
    video: ResolverVideo,
    serverName: String,
    audioLabel: String,
) {
    val target = downloadPickerTarget ?: return
    downloadPickerTarget = null
    resolvingEpisodes[target.episode.url] = true
    scope.launch {
        try {
            val ctx = PickerContext(
                anime = target.anime,
                episode = target.episode,
                source = target.source,
            )
            val result = downloadOrchestrator.enqueueSpecific(video, serverName, audioLabel, ctx)
            when (result) {
                is EnqueueResult.Success ->
                    Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
                is EnqueueResult.Error ->
                    Toast.makeText(context, "Download failed: ${result.message}", Toast.LENGTH_LONG).show()
                else -> {}
            }
        } finally {
            resolvingEpisodes.remove(target.episode.url)
        }
    }
}
```

Note: `enqueueSpecific` skips re-resolution — uses the `PickerContext` (anime/episode/source) stashed at resolve time.

## 7. `DownloadOrchestrator` — the resolver → engine bridge

**File**: `app/src/main/java/app/confused/anikuta/download/DownloadOrchestrator.kt`

Constructor (lines 52-57):
```kotlin
class DownloadOrchestrator(
    private val resolver: ResolverService,
    private val manager: DownloadManager,
    private val preferences: DownloadPreferences,
    private val serverDiscovery: ServerDiscoveryStore,
)
```

### `enqueueDownload` (auto path) — lines 65-144

```kotlin
suspend fun enqueueDownload(
    anime: DownloadAnimeInfo,
    episode: SEpisode,
    source: AnimeSource,
): EnqueueResult {
    if (!manager.isFolderReady()) {
        return EnqueueResult.Error("No download folder set. Open Downloads → settings to pick one.")
    }
    return try {
        when (val result = resolver.resolve(source, episode)) {
            is ResolverResult.Success -> {
                if (result.servers.isEmpty()) return EnqueueResult.NoSources

                // Passively record discovered server names for this source
                serverDiscovery.recordServers(source.id, result.servers.map { it.name })

                // If auto-download is OFF, always show the picker.
                if (!preferences.autoDownload().get()) {
                    return EnqueueResult.ShowPicker(
                        servers = result.servers,
                        anime = anime, episode = episode, source = source,
                    )
                }

                // Auto-download ON — select the best video.
                val selection = selectBestVideo(source.id, result.servers)
                when (selection) {
                    is Selection.Selected -> {
                        val request = buildRequest(anime, episode, source, selection)
                        val taskId = manager.enqueueDownload(request)
                        if (taskId < 0) EnqueueResult.Error("Failed to enqueue (invalid request).")
                        else EnqueueResult.Success(taskId)
                    }
                    is Selection.NoMatch -> { /* apply fallback strategies */ }
                }
            }
            is ResolverResult.NoSources -> EnqueueResult.NoSources
            is ResolverResult.Error -> EnqueueResult.Error(result.message)
        }
    } catch (e: Exception) { ... }
}
```

### `selectBestVideo` — lines 211-311

Algorithm (from the KDoc):
1. **Check if the TOP-preferred audio is available** (across all servers). If not → audio `FallbackStrategy` applies:
   - `ASK` → return `NoMatch` (host shows picker)
   - `DO_NOT_DOWNLOAD` → return `NoMatch` (host shows error)
   - `TRY_NEXT` → continue (try remaining preferred audios)
2. **Check if the TOP-preferred quality is available** (within any preferred audio). If not → quality fallback applies.
3. **Try all (server × audio × quality) combinations** in priority order (servers ordered by user pref, audios filtered to preferred set, qualities ordered by user pref). First match → `Selected`.
4. **If no preferred combination matches** and BOTH fallbacks are `TRY_NEXT`: pick the first available (best-effort).

Helper: `orderByName(items, prefs, nameOf)` — sorts items by their position in the user's preference list (unknowns last). `orderByQuality` is the same for videos.

### 7.5 NEW project: `selectBestVideo` is replaced by the 5-step `AutoDownloadEngine` (REVIEW-5 M52)

> **REVIEW-5 M52 (R4-I9):** the OLD-project trace above (§7) is a faithful reference for the
> OLD `DownloadOrchestrator.selectBestVideo` 3-step algorithm (audio check → quality check →
> server×audio×quality combinations). The NEW project REPLACES this with the 5-step
> `AutoDownloadEngine.selectBestVideo` pipeline per `14-auto-download-engine.md` §6.2:
>
> 1. **Flatten** — `List<ResolverServer>` → `List<Candidate>` with per-dimension `*Rank` fields.
> 2. **Rank** — sort by the rank tuple in the user's `dimensionPriority` order (lexicographic).
> 3. **applyFallbacks** — per-dimension fallback checks (generalizes the OLD Steps 1+2 to ALL 3 dimensions).
> 4. **Pick** — return the first sorted candidate.
> 5. **globalFallback** — REVIEW-5 M44: fire based on the picked candidate's match quality
>    (perfect vs. best-effort), NOT on `sortedCandidates.isEmpty()`.
>
> The 5-step pipeline preserves the same API contract (`Selection.Selected` / `Selection.NoMatch`)
> so the rest of the trace (`buildRequest` → `manager.enqueueDownload` → `DownloadQueue.enqueue`
> → `tryStartNext`) is UNCHANGED. The `directUrl` field on `ResolverVideo` (per
> `10-player-integration.md` §14.1 Fix 1) is preferred for `DownloadRequest.videoUrl` — falls
> back to `selection.video.url` (the proxy URL) if `directUrl` is null.

### `buildRequest` — lines 336-360

```kotlin
private fun buildRequest(
    anime: DownloadAnimeInfo,
    episode: SEpisode,
    source: AnimeSource,
    selection: Selection.Selected,
): DownloadRequest {
    val epInfo = DownloadEpisodeInfo(
        episodeUrl = episode.url,
        episodeNumber = episode.episode_number,
        name = episode.name,
        scanlator = episode.scanlator,
    )
    return DownloadRequest(
        anime = anime,
        episode = epInfo,
        videoUrl = selection.video.url,
        videoHeaders = selection.video.videoHeaders,
        subtitleTracks = selection.video.subtitleTracks.map { it.toDownloadTrack(TrackKind.SUBTITLE) },
        audioTracks = selection.video.audioTracks.map { it.toDownloadTrack(TrackKind.AUDIO) },
        sourceId = source.id,
        videoServer = selection.serverName,
        videoQuality = selection.video.quality,
        videoAudio = selection.audioLabel,
    )
}
```

## 8. `DownloadManager.enqueueDownload` (the engine entry point)

**File**: `core/download/src/main/java/app/confused/anikuta/core/download/DefaultDownloadManager.kt:111-121`

```kotlin
override suspend fun enqueueDownload(request: DownloadRequest): Long {
    if (request.videoUrl.isBlank()) {
        DownloadLogger.e("enqueueDownload rejected: blank videoUrl")
        return -1L
    }
    if (!storage.isFolderReady()) {
        DownloadLogger.e("enqueueDownload rejected: no download folder configured")
        return -1L
    }
    return queue.enqueue(request)
}
```

Returns `-1L` on invalid request, otherwise the task ID.

## 9. `DownloadQueue.enqueue` (the queue)

**File**: `core/download/src/main/java/app/confused/anikuta/core/download/DownloadQueue.kt:86-108`

```kotlin
fun enqueue(request: DownloadRequest): Long {
    val existing = _tasks.value.firstOrNull { it.key == keyFor(request) }
    if (existing != null) {
        DownloadLogger.d("Download already exists (id=${existing.id}, status=${existing.status})")
        // If it was completed, keep it completed (don't re-download). If errored, retry.
        if (existing.status == DownloadStatus.ERROR) {
            resumeInternal(existing.id)
        }
        return existing.id
    }

    val task = DownloadTask(
        id = idCounter.getAndIncrement(),
        request = request,
        status = DownloadStatus.QUEUED,
        createdAt = System.currentTimeMillis(),
    )
    updateTasks(_tasks.value + task)
    persistNow()
    DownloadLogger.i("Enqueued: ${request.anime.title} EP ${request.episode.episodeNumber} (id=${task.id})")
    tryStartNext()
    return task.id
}
```

The composite key is `"$contentId|$episodeNumber"` (3-decimal format). See `DownloadQueue.kt:309-310`:
```kotlin
private fun keyFor(request: DownloadRequest): String =
    "${request.anime.contentId}|${"%.3f".format(request.episode.episodeNumber)}"
```

`tryStartNext()` (line 180) is called immediately — if a Semaphore permit is free + connectivity check passes, it launches the download job (`launchDownload(task)` at line 190). See `02-queue-management.md` for the full queue internals.

## 10. Data passed at each step

| Step | Data carried |
|---|---|
| UI tap → AppController | `SEpisode` + `AnimeSource` + `WatchEpisodeContext` (animeTitle, coverUrl, anilistId) + `contentId: String` |
| AppController → Orchestrator | `DownloadAnimeInfo` (contentId, title, coverUrl, coverColor?) + `SEpisode` + `AnimeSource` |
| Orchestrator → Resolver | `AnimeSource` + `SEpisode` → returns `ResolverResult.Success(List<ResolverServer>)` |
| Orchestrator → Manager | `DownloadRequest` (anime + episode + videoUrl + headers + subtitleTracks + audioTracks + sourceId + videoServer + videoQuality + videoAudio) |
| Manager → Queue | same `DownloadRequest` |
| Queue → Task | `DownloadTask(id, request, status=QUEUED, ...)` |
| Queue → HttpDownloader | `DownloadTask` + `(downloaded, total) -> Unit` progress callback |
| HttpDownloader → Storage | `anime + episode + tempVideoFile + tempSubsDir + tempMetadataFile + videoExtension` |

## 11. What `SEpisode` looks like (source-api type)

From `core/source-api/src/main/kotlin/eu/kanade/tachiyomi/animesource/model/SEpisode.kt` (Aniyomi-equivalent):
- `url: String` — the source's episode URL (stable key for offline-playback lookup)
- `name: String` — display name
- `episode_number: Float` — drives the `Episode NNN` folder name
- `scanlator: String?` — scan/auditory hint
- `date_upload: Long` — epoch millis

## 12. What `ResolverVideo` looks like

The resolver returns a 3-tier hierarchy: `ResolverServer → AudioVersion → ResolverVideo`. `ResolverVideo` carries:
- `url: String` — the direct video URL
- `quality: String` — e.g. `"1080p"`
- `videoHeaders: String?` — newline-separated `"Key: Value"` HTTP headers (Referer, User-Agent)
- `subtitleTracks: List<SubtitleTrack>`
- `audioTracks: List<SubtitleTrack>` (yes, same type — they share the `Track` shape)

## 13. End state — what the user sees

After a successful enqueue:
- The episode row's spinner (`Resolving`) flips to a `Queued` spinner, then to `Downloading(progress)` once a permit is acquired.
- The Downloads page (`DownloadsScreen`) shows a new card in the queue section, with progress pills + bar.
- The system notification (`DownloadNotificationManager.updateProgress`) shows an ongoing summary notification with the top task's progress.

After completion:
- Row state → `Downloaded` (green checkmark + delete button).
- A one-shot "Download complete" notification is posted.
- The Downloads-page entry auto-clears from the active queue after 10 seconds (file stays on disk). Implemented in `DownloadViewModel.kt:51-68`.
