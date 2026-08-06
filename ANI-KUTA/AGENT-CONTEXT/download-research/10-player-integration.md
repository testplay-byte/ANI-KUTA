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

1. **Offline short-circuit in the host** (AppController-equivalent): before resolving a stream, check `isEpisodeDownloaded(contentId, episodeNumber)`. If true, build a `WatchRequest` with the local content:// URI + null headers + "Offline" server label + downloaded subtitle URIs.
2. **`isEpisodeDownloaded` with filesystem fallback**: in-memory task lookup first, then scan `<root>/ANIKUTA/downloads/anime/<... [contentId-safe]>/Episode NNN/` for a `video.*` file. Handles source switches + prior-install files.
3. **`getDownloadedVideoUri` returns a content:// URI** (playable by MPV via fd:// conversion in the player library).
4. **`getDownloadedSubtitleUris` returns content:// URIs** for each subtitle in `Episode NNN/data/subtitles/`.
5. **The WatchScreen treats the local URI the same as a remote URL** — no special-casing. The player library handles content:// → fd:// conversion.
6. **Episode switching re-runs the offline check** for each new episode.
7. **Watch progress** is recorded normally (keyed by contentId + episodeNumber).
8. **Add an "Offline" badge** in the WatchScreen for clarity (not in old project).
9. **Handle the deleted-file case** — if `getDownloadedVideoUri` returns null but `isEpisodeDownloaded` was true (race), fall through to streaming or show an error.

## 13. Cross-references

- `04-storage-paths.md` — the folder structure + `findEpisodeDirByNumber` filesystem fallback.
- `03-state-machine.md` — `episodeDownloadStates` Flow that powers the offline check.
- `09-details-page-download-ui.md` — how the episode row indicates "Downloaded" state (green ✓).
- `13-implementation-plan.md` — the new project's player integration plan.
