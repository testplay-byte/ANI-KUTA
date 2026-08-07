# 10 — Player Integration: How Downloaded Files Are Played

> All line references: `app/src/main/java/app/confused/anikuta/navigation/AppController.kt` + `feature/watch/src/main/java/app/confused/anikuta/feature/watch/WatchScreen.kt` + `feature/watch/src/main/java/app/confused/anikuta/feature/watch/WatchRequest.kt`.

## 1. The short-circuit (offline playback entry point)

**File**: `AppController.kt:871-920` (the `resolveEpisode` / `pushWatch` flow)

When the user taps an episode row on the details page, `AppController.resolveEpisode(...)` is called. **Before** doing the streaming-resolver dance, it checks if the episode is already downloaded:

```kotlin
// AppController.kt:896-920 (reconstructed from the Grep results)
scope.launch {
    // ── Offline-playback short-circuit ──
    try {
        if (downloadManager.isEpisodeDownloaded(contentId, episode.episode_number)) {
            Log.i(TAG, "Offline hit: contentId=$contentId EP ${episode.episode_number} — using local copy")
            val videoUri = downloadManager.getDownloadedVideoUri(contentId, episode.episode_number)
            val subUris = downloadManager.getDownloadedSubtitleUris(contentId, episode.episode_number)
            if (videoUri != null) {
                Log.i(TAG, "Playing offline: ${episode.name} ($videoUri)")
                pushWatch(
                    WatchRequest(
                        videoUrl = videoUri,            // content:// URI
                        videoHeaders = null,            // no headers for local file
                        videoTitle = ...,
                        anilistId = ...,
                        animeTitle = ...,
                        coverUrl = ...,
                        coverColor = ...,
                        episodeUrl = episode.url,
                        episodeNumber = episode.episode_number,
                        sourceId = ...,
                        source = null,                  // no source needed for offline
                        videoServer = "Offline",
                        videoAudio = "",
                        videoQuality = 0,
                        episodeList = episodeList,
                        episodeMetadata = episodeMetadata,
                        subtitleTracks = subUris.map { SubtitleTrack(it, "External") },
                        audioTracks = emptyList(),
                        resolvedServers = emptyList(),
                    )
                )
                return@launch
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Offline lookup failed — falling through to streaming", e)
    }

    // ── Streaming path (fall-through) ──
    // ... the normal resolver sheet flow ...
}
```

**Key things**:
- The lookup uses `(contentId, episodeNumber)` — source-independent. Survives source switches.
- `isEpisodeDownloaded` falls back to a filesystem scan if no in-memory task matches (see `04-storage-paths.md` §9).
- `getDownloadedVideoUri` returns a **content:// URI** (or null if the file was deleted out from under us).
- `getDownloadedSubtitleUris` returns a list of subtitle content:// URIs.
- Subtitles are passed as `SubtitleTrack(uri, "External")` — same shape as streaming subtitles, but the URL is a content:// URI.
- The `WatchRequest` is constructed with the local URI + `videoServer = "Offline"` + `videoHeaders = null` + `source = null`. The player doesn't need to know it's playing a local file — it just plays whatever URL it gets.
- If the offline lookup throws OR returns null videoUri, the flow falls through to the streaming resolver.

## 2. `isEpisodeDownloaded` — the offline check

**File**: `DefaultDownloadManager.kt:168-180`

```kotlin
override suspend fun isEpisodeDownloaded(contentId: String, episodeNumber: Float): Boolean {
    // 1. Try the in-memory task lookup (fast path).
    val task = findTask(contentId, episodeNumber)
    if (task?.status == DownloadStatus.COMPLETED) return true

    // 2. Filesystem fallback (source-switching fix): scan the on-disk folder
    //    by contentId + episodeNumber. The folder structure is
    //    <root>/ANIKUTA/downloads/anime/<Title [contentId-safe]>/Episode NNN/,
    //    so a match by episode number works even if the episodeUrl changed.
    return storage.findEpisodeDirByNumber(contentId, episodeNumber)?.let { epDir ->
        epDir.listFiles().any { it.isFile && it.name?.startsWith("video.") == true }
    } ?: false
}
```

`findTask(contentId, episodeNumber)` (line 236-239):
```kotlin
private fun findTask(contentId: String, episodeNumber: Float): DownloadTask? {
    val key = "$contentId|${"%.3f".format(episodeNumber)}"
    return queue.tasks.value.firstOrNull { it.key == key }
}
```

`storage.findEpisodeDirByNumber(contentId, episodeNumber)` — see `04-storage-paths.md` §9. Scans `<root>/ANIKUTA/downloads/anime/` for a folder ending with `[sanitized-contentId]`, then looks for `Episode NNN/` inside it.

## 3. `getDownloadedVideoUri` — getting the playable URI

**File**: `DefaultDownloadManager.kt:182-194`

```kotlin
override suspend fun getDownloadedVideoUri(contentId: String, episodeNumber: Float): String? {
    // 1. Try the in-memory task (has the exact videoUri).
    val task = findTask(contentId, episodeNumber)
    if (task?.status == DownloadStatus.COMPLETED) {
        return storage.getVideoUri(task.request.anime, task.request.episode) ?: task.videoUri
    }

    // 2. Filesystem fallback (source-switching): find the episode dir by
    //    number + look for a video file inside it.
    return storage.findEpisodeDirByNumber(contentId, episodeNumber)?.let { epDir ->
        epDir.listFiles().firstOrNull { it.isFile && it.name?.startsWith("video.") == true }?.uri?.toString()
    }
}
```

`storage.getVideoUri(anime, episode)` — `DownloadStorageProvider.kt:296-301`:
```kotlin
fun getVideoUri(anime: DownloadAnimeInfo, episode: DownloadEpisodeInfo): String? {
    val epDir = findEpisodeDir(anime, episode) ?: return null
    val videoFile = epDir.listFiles().firstOrNull { it.name?.startsWith("video.") == true }
    return videoFile?.uri?.toString()
}
```

Returns a content:// URI like `content://com.android.externalstorage.documents/document/primary%3AAniKuta%20Downloads%2FANIKUTA%2Fdownloads%2Fanime%2FJujutsu%20Kaisen%20%5Bal-101522%5D%2FEpisode%20001%2Fvideo.mp4`.

**The `?: task.videoUri` fallback** (line 186): if the storage lookup fails (e.g. the folder was moved or the SAF provider is misbehaving), use the URI stashed in the task record from when the download completed. Belt-and-suspenders.

## 4. `getDownloadedSubtitleUris` — subtitle URIs

**File**: `DefaultDownloadManager.kt:196-214`

```kotlin
override suspend fun getDownloadedSubtitleUris(
    contentId: String, episodeNumber: Float,
): List<String> {
    // 1. Try the in-memory task.
    val task = findTask(contentId, episodeNumber)
    if (task?.status == DownloadStatus.COMPLETED) {
        return storage.getSubtitleUris(task.request.anime, task.request.episode)
            .ifEmpty { task.subtitleUris }
    }

    // 2. Filesystem fallback: scan the subtitles/ folder.
    return storage.findEpisodeDirByNumber(contentId, episodeNumber)?.let { epDir ->
        epDir.findFile("data")?.findFile("subtitles")?.listFiles()
            ?.filter { it.isFile }
            ?.map { it.uri.toString() }
            ?: emptyList()
    } ?: emptyList()
}
```

`storage.getSubtitleUris(anime, episode)` — `DownloadStorageProvider.kt:304-308`:
```kotlin
fun getSubtitleUris(anime: DownloadAnimeInfo, episode: DownloadEpisodeInfo): List<String> {
    val epDir = findEpisodeDir(anime, episode) ?: return emptyList()
    val subDir = epDir.findFile("data")?.findFile("subtitles") ?: return emptyList()
    return subDir.listFiles().map { it.uri.toString() }
}
```

Returns content:// URIs for each subtitle file in `Episode NNN/data/subtitles/`.

## 5. `WatchRequest` — what the player receives

**File**: `feature/watch/src/main/java/app/confused/anikuta/feature/watch/WatchRequest.kt`

```kotlin
data class WatchRequest(
    val videoUrl: String,
    val videoHeaders: String?,
    val videoTitle: String,
    val anilistId: Int,
    val animeTitle: String,
    val coverUrl: String?,
    val coverColor: Int?,
    val episodeUrl: String,
    val episodeNumber: Float,
    val sourceId: Long,
    val source: AnimeSource? = null,
    val videoServer: String,
    val videoAudio: String,
    val videoQuality: Int,
    val episodeList: List<SEpisode>,
    val episodeMetadata: Map<Int, EpisodeMetadata> = emptyMap(),
    val subtitleTracks: List<SubtitleTrack> = emptyList(),
    val audioTracks: List<SubtitleTrack> = emptyList(),
    val resolvedServers: List<ResolverServer> = emptyList(),
)
```

For offline playback, the relevant fields:
- `videoUrl` = content:// URI (the local video file).
- `videoHeaders` = null (no HTTP headers for local files).
- `source` = null (no source needed — can't re-resolve offline).
- `videoServer` = "Offline" (UI label).
- `videoAudio` = "" (no audio version info stored separately).
- `videoQuality` = 0 (no quality info — the file is whatever was downloaded).
- `subtitleTracks` = list of `SubtitleTrack(uri, "External")` for each downloaded subtitle.

## 6. The WatchScreen — does it know it's offline?

**File**: `feature/watch/src/main/java/app/confused/anikuta/feature/watch/WatchScreen.kt`

Looking at the references to "offline" / "downloaded" / `isEpisodeDownloaded` in `WatchScreen.kt` — there are NONE. The WatchScreen treats the local content:// URI the same as a remote URL. It hands the URL to MPV via `resolveUrlForMpv` (a function that converts URLs to forms MPV can play).

From `WatchScreen.kt:516-517`:
```kotlin
val contentId = if (watchRequest.anilistId != 0) "al:${watchRequest.anilistId}" else null
val progress = contentId?.let { watchProgressStore.get(it, currentEpNum) }
```

The WatchScreen uses `watchRequest.anilistId` to derive the contentId for watch-progress tracking. For offline playback, `anilistId` is still passed through (it's an anime identity, not a source identity), so watch progress is recorded normally.

### How MPV plays a content:// URI

The player needs a file descriptor or a path it can open. Android's `ContentResolver.openFileDescriptor(uri, "r")` returns a `ParcelFileDescriptor` for any content:// URI. MPV can play via `fd://<fd_number>` (a file descriptor URL).

The `resolveUrlForMpv` function (not shown — referenced in `DownloadManager.kt:113` KDoc) likely:
1. If the URL is a content:// URI → open via ContentResolver → get fd → return `"fd://<fd>"`.
2. If the URL is a http(s):// URL with headers → return as-is (MPV plays it natively with `--http-header-fields`).

The watch screen passes the resolved URL to MPV via the player library (`AnikutaMPVView` in the new project).

**For the new project**: the existing player infrastructure should already handle content:// URIs (it's the same approach the new project uses for the resolver-streaming path with the LocalProxyServer). The download integration just needs to pass the content:// URI as the `videoUrl` — no special-casing needed in the player.

## 7. Episode switching while offline

The `WatchRequest.episodeList: List<SEpisode>` carries the full episode list (passed from the details screen). When the user taps "next episode" in the player, the WatchScreen calls back into `AppController` to resolve the next episode — which **re-runs the offline short-circuit** for that next episode.

If the next episode is also downloaded → seamless offline transition.
If not → falls through to the streaming resolver (which may fail if the device is truly offline — the resolver shows an error sheet).

## 8. Subtitle loading from local files

The downloaded subtitle URIs are passed to MPV as `SubtitleTrack(uri, "External")`. The player loads them as external sub tracks — same as streaming subtitles.

For content:// subtitle URIs, MPV needs the same fd:// conversion. The player library handles this transparently.

## 9. What if the file was deleted out from under us?

If the user deletes the file via a file manager (bypassing the app), `isEpisodeDownloaded` returns false (no in-memory task matches if the app was restarted, OR the storage fallback finds no video file). The flow falls through to streaming — no crash.

If the in-memory task says COMPLETED but the file is gone (deleted mid-session), `getDownloadedVideoUri` returns null (storage lookup fails + `task.videoUri` was the deleted URI → `storage.getVideoUri` returns null + fallback to `task.videoUri` which points to the deleted file). The WatchScreen would try to play a non-existent URI → MPV error.

**Mitigation**: the WatchScreen should handle MPV errors gracefully (it does — there's a `PlayerErrorOverlay`). The user would see an error and need to re-download.

## 10. Offline indicator in the UI

**There is no explicit "Offline" badge** in the WatchScreen or the episode row. The only indicator is:
- On the episode row: the green ✓ icon in `EpisodeDownloadControl` (when state is `Downloaded`).
- In the WatchScreen: `videoServer = "Offline"` is set on the WatchRequest, but I don't see the WatchScreen actually displaying this label prominently (it would show in the quality/server sheet, but not as a banner).

**For the new project**: an explicit "Playing offline" badge in the WatchScreen would be a UX improvement.

## 11. Watch progress for offline playback

Watch progress is recorded via `watchProgressStore` keyed by `(contentId, episodeNumber)` — same as streaming. The WatchScreen calls `watchProgressStore.set(contentId, episodeNumber, position, duration)` periodically.

For offline playback, `contentId` is derived from `watchRequest.anilistId` (`"al:$anilistId"`). For unlinked extension anime (no anilistId), watch progress may not be recorded — depends on whether the WatchScreen handles `anilistId == 0`.

Looking at `WatchScreen.kt:516`:
```kotlin
val contentId = if (watchRequest.anilistId != 0) "al:${watchRequest.anilistId}" else null
val progress = contentId?.let { watchProgressStore.get(it, currentEpNum) }
```

If `anilistId == 0`, `contentId` is null → no progress recorded. **This is a gap for unlinked extension anime** — but it's a WatchScreen issue, not a download issue.

## 12. Summary — what the new project should replicate

> **POST-REWRITE NOTE:** Items #2 + #4 reference the OLD project's storage path (`<root>/ANIKUTA/downloads/anime/<... [contentId-safe]>/Episode NNN/`). The NEW project uses `<root>/{video,images,text}/<Title>/{<Title> - E00001.mp4, ...}` — see `04-storage-paths.md`. The filesystem fallback is now `findContentDir(mainId)` + reading the `data.json` for the `videoFileName` field. See §13 below for the updated path lookup.

1. **Offline short-circuit in the host** (AppController-equivalent): before resolving a stream, check `isEpisodeDownloaded(mainId, episodeNumber)`. If true, build a `WatchRequest` with the local content:// URI + null headers + "Offline" server label + downloaded subtitle URIs.
2. **`isEpisodeDownloaded` with filesystem fallback** (NEW): in-memory task lookup first (DB hit on `downloaded_episode`), then `findContentDir(mainId)` (walks `video/`/`images/`/`text/`, reads each `data.json`, matches `mainId`). Handles source switches + reinstall-with-same-folder cases.
3. **`getDownloadedVideoUri` returns a content:// URI** (playable by MPV via fd:// conversion in the player library).
4. **`getDownloadedSubtitleUris` returns content:// URIs** for each subtitle next to the video file (named `<Title> - E00001.<lang>.<index>.<ext>` per `04-storage-paths.md` §4.5).
5. **The WatchScreen treats the local URI the same as a remote URL** — no special-casing. The player library handles content:// → fd:// conversion.
6. **Episode switching re-runs the offline check** for each new episode.
7. **Watch progress** is recorded normally (keyed by `mainId` + `episodeNumber` — NOT `anilistId`, so unlinked extension anime get progress too — fixes the OLD project's gap from §11).
8. **Add an "Offline" badge** in the WatchScreen for clarity (not in old project).
9. **Handle the deleted-file case** — if `getDownloadedVideoUri` returns null but `isEpisodeDownloaded` was true (race), fall through to streaming or show an error.

## 13. The NEW offline-lookup path (post-rewrite)

Per `04-storage-paths.md`, the file layout is now:

```
<root>/video/<sanitized-title>/
├── data.json                          ← contains the mainId + episode list
├── cover.jpg                          ← for notification thumbnails
├── <Title> - E00001.mp4               ← video file
├── <Title> - E00001.English.0.srt     ← subtitle file
└── ...
```

The new offline-lookup path:

```kotlin
override suspend fun isEpisodeDownloaded(mainId: String, episodeNumber: Float): Boolean {
    // 1. Fast path: DB lookup.
    val episodeKey = buildEpisodeKey(mainId, episodeNumber)
    if (downloadStore.isDownloaded(mainId, episodeKey)) return true

    // 2. Filesystem fallback (reinstall + source-switch cases): walk the SAF folder.
    val contentDir = storage.findContentDir(mainId) ?: return false
    val dataJson = storage.readDataJson(contentDir) ?: return false
    val episodeEntry = dataJson.episodes.firstOrNull { it.episodeKey == episodeKey } ?: return false
    val videoFile = contentDir.findFile(episodeEntry.videoFileName) ?: return false
    return videoFile.isFile && videoFile.length() > 0
}

override suspend fun getDownloadedVideoUri(mainId: String, episodeNumber: Float): String? {
    val episodeKey = buildEpisodeKey(mainId, episodeNumber)

    // 1. Fast path: DB has the video_uri.
    downloadStore.getVideoUri(mainId, episodeKey)?.let { return it }

    // 2. Filesystem fallback.
    val contentDir = storage.findContentDir(mainId) ?: return null
    val dataJson = storage.readDataJson(contentDir) ?: return null
    val episodeEntry = dataJson.episodes.firstOrNull { it.episodeKey == episodeKey } ?: return null
    return contentDir.findFile(episodeEntry.videoFileName)?.uri?.toString()
}
```

The `findContentDir(mainId)` walks `video/`, `images/`, `text/` under the user's selected SAF root, reading each `data.json` until it finds the matching `mainId`. See `04-storage-paths.md` §8.4.

## 14. The proxy-churn bug fix (CRITICAL — post-rewrite)

> **Task ID:** DL-PLAN-REWRITE
> Source: `15-ui-and-bug-analysis.md` Part B (the root-cause analysis + the 4 fix layers).
> The bug: a download is in progress → user plays another episode from the same extension source → the extension's `getHosterList` is called again → a NEW local proxy server is created on a different port → the OLD proxy (whose URL is captured in the in-flight download's `DownloadRequest.videoUrl`) dies → the download's next `input.read(buffer)` throws `IOException("Connection refused")` → the task flips to ERROR.

This bug is NOT in the OLD project's architecture — the OLD project cannot fix it because the download captures the `videoUrl` at enqueue time and never re-resolves. The NEW project must architecturally avoid this.

### 14.1 The 4 fix layers

#### Fix 1 (PRIMARY) — `directUrl` on `ResolverVideo` + prefer it for downloads

When the resolver returns a `Video`, it carries BOTH:
- `videoUrl` — the proxy URL (for streaming via MPV — fast, supports anti-scraping stripping).
- `directUrl: String?` — the underlying CDN URL (for downloading — stable, no proxy dependency).

The download orchestrator prefers `directUrl` for downloads. If `directUrl` is null (the extension truly only exposes the proxy), fall through to Fix 2.

**Implementation:**
- `core/video-resolver/.../ResolverTypes.kt` — add `directUrl: String? = null` field to `ResolverVideo`.
- The resolver strategy extracts the direct URL by calling a new `Video.directVideoUrl` extension hook (similar to how the existing `videoUrl` is exposed). Extensions that proxy can override this to return the underlying CDN URL.
- `DownloadOrchestrator.buildRequest` uses `selection.video.directUrl ?: selection.video.url` for `DownloadRequest.videoUrl`.
- The download engine then makes a direct HTTP call to the CDN — no proxy dependency, no churn.

#### Fix 2 (SECONDARY) — Re-resolve-on-IOException for localhost-URL downloads

If `directUrl` is null (the extension only exposes a proxy URL), the download engine must treat the proxy URL as **ephemeral** and re-resolve on failure:

- Add a `DownloadRequest.resolveContext: ResolveContext?` field capturing `(sourceId, episodeUrl, serverName, audioLabel, quality, mainId, episodeKey)` — enough to re-resolve.
- In `HttpDownloader.downloadNormal`, catch `IOException` specifically. If the request URL is a `localhost` URL AND the task has a `resolveContext`, BEFORE throwing `DownloadException`, attempt ONE re-resolve.

  ```kotlin
  // REVIEW-5 M15 + M16 + M17 — the canonical catch-block body is in `05-downloaders.md` §11.3.
  // This snippet is the same logic, kept here for the traceability of §14.
  catch (e: IOException) {
      if (url.startsWith("http://localhost") && resolveContext != null && reResolver != null
          && reResolveAttempts < MAX_RE_RESOLVE_ATTEMPTS) {
          val fresh = reResolver.reResolve(resolveContext)
          if (fresh != null) {
              store.updateResolveContext(taskId, fresh.url, resolveContext)
              // Truncate the temp file (the fresh URL is a NEW proxy on a different port; the
              // existing bytes may not be reusable because the new proxy may not support Range).
              FileOutputStream(tempFile).use { /* truncate to 0 */ }
              // REVIEW-5 M16: recurse on `downloadNormal` (NOT `downloadVideoToCache`) + pass
              // `resolveContext` (was missing in the OLD draft — wouldn't compile) + pass
              // `reResolveAttempts + 1` to bound the recursion (was missing — unbounded recursion).
              return downloadNormal(
                  url = fresh.url,
                  headers = fresh.headers,
                  tempFile = tempFile,
                  taskId = taskId,
                  resolveContext = resolveContext,
                  onProgress = onProgress,
                  reResolveAttempts = reResolveAttempts + 1,
              )
          }
      }
      if (url.startsWith("http://localhost") && reResolveAttempts >= MAX_RE_RESOLVE_ATTEMPTS) {
          throw DownloadException(
              "Proxy URL died after $MAX_RE_RESOLVE_ATTEMPTS re-resolve attempt(s) — the extension's " +
                  "proxy server is being churned by another playback.",
              e,
          )
      }
      throw DownloadException("Video download failed: ${e.message ?: e.javaClass.simpleName}", e)
  }
  ```

- REVIEW-5 M17: the re-resolve does a **DIRECT lookup** by pinned `(server, audio, quality)` via `ReResolver.reResolve` — it does **NOT** re-run the `AutoDownloadEngine`. The OLD draft's claim ("uses the SAME `AutoDownloadEngine`") was FALSE — `ReResolver`'s constructor took an `AutoDownloadEngine` DI param but never called it (dead DI param). The `autoDownloadEngine` parameter has been removed from `ReResolver`'s constructor (see §14.3 below) + from the Koin binding in `12-di-wiring.md` §11.2.
- REVIEW-5 M15 + M18: **Cap the inner re-resolve at 1 attempt (= 2 total download attempts: 1 initial + 1 re-resolve)**. The outer retry loop (`16-quality-of-life.md` §1.2) caps at 3 attempts. Total = 3 outer × 2 inner = **6 download attempts maximum before the task goes to ERROR**. See REVIEW-5 §6.3 "cap composition".
- Log a one-time warning when the URL is detected as a localhost URL: `"Download depends on extension proxy server — may fail if the proxy is killed by another resolve call."` (per `15-ui-and-bug-analysis.md` §B.7 rule 5).

#### Fix 3 (TERTIARY) — `ProxyLeaseCoordinator`

The strongest fix is a `ProxyLeaseCoordinator` (in `:core:video-resolver` or `:app`) that:

- Tracks active leases: `Map<ProxyKey, LeaseRefcount>` where `ProxyKey = (sourceId, serverName)` and `LeaseRefcount` counts how many consumers (MPV + each download task) are currently using the proxy.
- Exposes `acquireLease(source, serverName): Lease` and `releaseLease(lease)`.
- Wraps `VideoResolver.resolve` so that BEFORE calling `source.getHosterList`, it checks if a lease for `(source.id, ...)` already exists. If yes, reuses the existing resolved videos (whose proxy URLs are still alive). If no, calls `getHosterList` and creates a new lease.
- The download engine calls `acquireLease` before starting the download + `releaseLease` in its `finally` block. The player does the same.
- **Result:** a second `getHosterList` for the same source is SUPPRESSED while a download is using the proxy. Only when the lease count drops to zero (download finished + player stopped) is the proxy allowed to be re-created.

This is the heaviest fix but the most correct. It eliminates the bug class entirely — no proxy churn means no download failures from proxy death.

**Decision:** Implement Fix 1 + Fix 2 in Phase D.2 (required). Defer Fix 3 to a later phase — it's only needed if extensions consistently expose only proxy URLs (no `directUrl`).

#### Fix 4 (QUATERNARY) — Foreground service for download durability

Independent of the proxy-churn bug, the new project MUST add a foreground service for downloads (per `06-notifications-foreground-service.md`). This prevents Android from killing the download when the app goes to background, which is a SEPARATE failure mode from the proxy-churn one but worth fixing in the same pass.

### 14.2 The `ResolveContext` data class

```kotlin
@Serializable
data class ResolveContext(
    val sourceId: Long,
    val episodeUrl: String,
    val serverName: String,
    val audioLabel: String,
    val quality: String,
    /** The mainId of the content (for DB lookups during re-resolve). */
    val mainId: String,
    /** The episode key (for DB lookups during re-resolve). */
    val episodeKey: String,
)
```

Persisted in `download_queue.resolve_context` as a JSON-encoded string. Read by `HttpDownloader` on IOException. The `ReResolver` uses it to re-run the `AutoDownloadEngine` with pinned `(server, audio, quality)`.

### 14.3 The `ReResolver` class

```kotlin
// REVIEW-5 M17: removed the `autoDownloadEngine: AutoDownloadEngine` constructor param — it was
// dead DI (the OLD draft claimed the re-resolve "uses the SAME AutoDownloadEngine" but the
// reResolve implementation below does a DIRECT lookup by pinned (server, audio, quality),
// never calling the engine). The Koin binding in 12-di-wiring.md §11.2 was updated to match.
class ReResolver(
    private val videoResolver: VideoResolver,
    private val preferences: DownloadPreferences,
) {
    /**
     * Re-resolves the video for the given context, picking the SAME (server, audio, quality)
     * combination. Returns the fresh ResolverVideo (with a new proxy URL) or null if re-resolve
     * fails or returns a different (server, audio, quality) than pinned.
     *
     * REVIEW-5 M17: this is a DIRECT lookup, NOT a re-run of the AutoDownloadEngine. The engine
     * might pick a DIFFERENT (server, audio, quality) on re-resolve, which would defeat the
     * purpose (the user's pinned choice must be preserved).
     *
     * Caps at 1 attempt (the caller has already failed once — see M15).
     */
    suspend fun reResolve(context: ResolveContext): FreshVideo? {
        val result = videoResolver.resolve(context.sourceId, context.episodeUrl) ?: return null
        // Find the server with the pinned name.
        val server = result.servers.firstOrNull { it.name == context.serverName } ?: return null
        // Find the audio version with the pinned label.
        val audio = server.audioVersions.firstOrNull { it.label == context.audioLabel } ?: return null
        // Find the video with the pinned quality.
        val video = audio.videos.firstOrNull { it.quality == context.quality } ?: return null
        return FreshVideo(
            url = video.directUrl ?: video.url,
            headers = video.videoHeaders,
        )
    }
}

data class FreshVideo(val url: String, val headers: String?)
```

### 14.4 Architectural rules to prevent the bug class

Per `15-ui-and-bug-analysis.md` §B.7:

1. **The download engine must NEVER depend on the lifetime of a side-effect created by the resolver.** If the resolver creates a resource (proxy server, file descriptor, session token) whose lifetime is shorter than the download's, the download engine must either (a) not use that resource, or (b) hold an explicit lease that prevents the resource from being killed.
2. **URLs captured at enqueue time are NOT durable.** The download engine must treat `videoUrl` as potentially ephemeral. Either:
   - Capture a `directUrl` (no proxy) and use that, OR
   - Capture a `resolveContext` (enough info to re-resolve) and use the proxy URL with re-resolve-on-failure.
3. **`VideoResolver.resolve` is NOT idempotent with respect to side-effects.** Calling it twice for the same `(source, episode)` may kill the proxy from the first call. The new project must EITHER make `resolve` idempotent (cache the result + reuse it for the same `(source, episode)` while a lease is held) OR coordinate via a lease coordinator so that the second call doesn't kill the first.
4. **The download scope must be architecturally separate from the playback scope.** The OLD project actually gets this right (`DefaultDownloadManager`'s private scope vs `AppController`'s scope vs MPV's no-scope). The new project should preserve this separation — but ALSO ensure the download doesn't depend on resources held by the playback scope (which the OLD project fails to do, hence this bug).
5. **The download engine must log the URL it's fetching from** (the OLD project does this in `HttpDownloader.download`'s `DownloadLogger.i("  URL: $videoUrl")`). When debugging this bug, that log line is the smoking gun — if the URL is `http://localhost:PORT/...`, the bug is proxy churn; if it's `https://cdn.example.com/...`, the bug is something else (network/server-side). The new project should preserve this logging AND ADD a one-time warning when the URL is detected as a localhost URL.
6. **Add an integration test that exercises the bug scenario.** The test: enqueue a download from a mock source that returns a localhost-proxy URL, then call `resolve` on the same source for a different episode (which kills the proxy), then assert that the download EITHER (a) completes via `directUrl`, OR (b) re-resolves and completes, OR (c) fails gracefully with a clear error message ("proxy server killed by another resolve call — retry will re-resolve"). The test must FAIL if the download just goes to ERROR with a generic "Connection refused" message.

### 14.5 The end-to-end fixed trace

Concrete trace — anime A EP1 is downloading, user opens anime B (same extension source) and taps play on EP1:

| Step | Component | Action | Effect |
|---|---|---|---|
| 1-7 | (same as the OLD project's trace) | User enqueues A EP1 → orchestrator resolves → `directUrl` extracted → `DownloadRequest.videoUrl = directUrl` → download starts reading from CDN URL (NOT localhost) | Download in progress, NO proxy dependency |
| 8 | User opens anime B, taps play | `AppController.resolveEpisode(B EP1)` → `videoResolver.resolve(source, B EP1)` → extension's `getHosterList(B EP1)` → creates NEW proxy on port 39073 (the OLD proxy on 39369 dies) | Proxy A is dead, but the download doesn't depend on it (using directUrl) |
| 9 | Back in `HttpDownloader.downloadNormal` (still in step 7's `while (true) { input.read(buffer) }` loop) | The next `input.read(buffer)` on the CDN URL SUCCEEDS — the CDN URL is unrelated to the proxy | Download continues normally |
| 10 | Download completes | `tempVideo` validated, `publishToUserFolder` writes to SAF, `data.json` updated, `downloaded_episode` row inserted | Task status = COMPLETED. UI shows the green ✓. |

If the source DOESN'T expose `directUrl` (only the proxy URL):

| Step | Component | Action | Effect |
|---|---|---|---|
| 1-7 | (same as above, except `videoUrl = localhost:39369/...`) | Download reads from proxy on port 39369. `resolveContext` saved in `download_queue.resolve_context`. | Download in progress, depends on proxy |
| 8 | User opens anime B, taps play | New proxy on port 39073. Proxy on port 39369 dies. | Proxy A is dead. |
| 9 | `HttpDownloader.downloadNormal` `input.read(buffer)` throws `IOException("Connection refused")` | Catch block fires | — |
| 10 | `ReResolver.reResolve(resolveContext)` | `videoResolver.resolve(source, episodeUrl)` again → new proxy on port 39073 → find pinned (server, audio, quality) → return `FreshVideo("http://localhost:39073/...", headers)` | New URL acquired |
| 11 | `store.updateResolveContext(task.id, fresh.url, fresh.context)` + retry `downloadNormal(fresh.url, ..., resolveContext, onProgress, reResolveAttempts = 1)` | REVIEW-5 M15 + M16: the recursive call now passes `resolveContext` (was missing — wouldn't compile) + `reResolveAttempts = 1` (was missing — unbounded recursion). The temp file is truncated to 0 (the new proxy on port 39073 may not support Range — simplicity over partial-resume). The retry reads from port 39073 (alive). | Download continues |
| 12 | Download completes | `tempVideo` validated + published + DB row inserted | Task status = COMPLETED. |

This is the architectural fix. The OLD project would have hit ERROR at step 9 with no recovery path.

## 15. Cross-references (post-rewrite)

- `04-storage-paths.md` — **NEW storage system** + the `findContentDir` filesystem fallback.
- `03-state-machine.md` — `episodeDownloadStates` Flow that powers the offline check.
- `09-details-page-download-ui.md` — how the episode row indicates "Downloaded" state (green ✓).
- `13-implementation-plan.md` — the new project's player integration plan (Phase D.6) + the proxy-churn fix (Phase D.2).
- `14-auto-download-engine.md` — the new 5-step `AutoDownloadEngine` (used by `ReResolver` to re-pick the pinned (server, audio, quality)).
- `15-ui-and-bug-analysis.md` Part B — **the proxy-churn bug root-cause analysis + the 4 fix layers** (this section is the implementation spec for that analysis).
