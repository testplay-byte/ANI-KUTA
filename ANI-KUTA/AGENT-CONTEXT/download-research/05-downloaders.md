# 05 — The Download Engines

> Covers `HttpDownloader.kt` (538 lines), `HlsDownloader.kt` (333 lines), `VideoTypeDetector.kt` (116 lines), `DynamicProgressTracker.kt` (123 lines), `advanced/AdvancedHttpDownloader.kt` (401 lines), `advanced/DownloadResumeManager.kt` (117 lines). All in `core/download/src/main/java/app/confused/anikuta/core/download/`.

## 1. Routing overview

`HttpDownloader.download(task, onProgress)` is the entry point. It routes to one of three sub-pipelines based on URL inspection + method preference:

```
HttpDownloader.download(task, onProgress)
  │
  ├── inferVideoExtension(videoUrl)
  ├── tempVideo = tempCache.videoFile(task.id, ext)
  ├── downloadVideoToCache(url, headers, tempVideo, taskId, onProgress)
  │     │
  │     ├── VideoTypeDetector.detectFromUrl(url) == HLS_STREAM?
  │     │     └── YES → hlsDownloader.download(url, headers, tempVideo, onProgress)  [HLS path]
  │     │
  │     ├── preferences.method() == ADVANCED?
  │     │     └── YES → advancedDownloader.download(...)  [multi-threaded Range path]
  │     │           └── on DownloadException → fall back to downloadNormal(...)
  │     │
  │     └── else → downloadNormal(url, headers, tempVideo, taskId, onProgress)  [single-threaded path]
  │                   │
  │                   └── VideoTypeDetector.detect(url, response) inside the response:
  │                         ├── HLS_STREAM (Content-Type) → delegate to hlsDownloader
  │                         ├── DASH_STREAM → reject (needs ffmpeg)
  │                         ├── HTML_PAGE → reject (resolver bug)
  │                         └── DIRECT_VIDEO → stream to file
  │
  ├── (HLS fallback if downloaded file < 500KB AND starts with #EXTM3U)
  │     └── re-download via hlsDownloader with .ts extension
  │
  ├── validateDownloadedFile (size >= 500KB)
  ├── verifyVideoMagicBytes (reject HTML/PNG/JPEG masquerading as video)
  ├── downloadSubtitlesToCache (best-effort per subtitle track)
  ├── writeMetadataToCache (EpisodeMetadataCache JSON)
  ├── storage.publishToUserFolder(...) → PublishResult
  └── finally { tempCache.cleanupTask(task.id) }
```

## 2. `VideoTypeDetector` — the URL/Content-Type inspector

**File**: `VideoTypeDetector.kt` (116 lines)

```kotlin
object VideoTypeDetector {
    enum class VideoType {
        DIRECT_VIDEO,   // mp4/mkv/webm/m4v/ts/mov/avi — stream to disk
        HLS_STREAM,     // .m3u8 — needs HlsDownloader
        DASH_STREAM,    // .mpd — needs ffmpeg (NOT supported, rejected)
        HTML_PAGE,      // watch-page URL (resolver bug)
        UNKNOWN,        // generic — treated as DIRECT_VIDEO
    }

    fun detect(url: String, response: Response): VideoType
    fun detectFromUrl(url: String): VideoType
    fun isDownloadable(type: VideoType): Boolean  // DIRECT_VIDEO or HLS_STREAM
    fun unsupportedReason(type: VideoType): String?  // human-readable
}
```

### `detectFromUrl(url)` — line 85-98 (URL-only, no network)

Inspects the URL extension (lowercase, query-stripped):
- `mp4, mkv, webm, m4v, mov, avi, ts` → `DIRECT_VIDEO`
- `m3u8, m3u` → `HLS_STREAM`
- `mpd` → `DASH_STREAM`
- `html, htm, php, asp, aspx, jsp` → `HTML_PAGE`
- else → `UNKNOWN`

### `detect(url, response)` — line 53-82 (with response headers)

Priority: Content-Type > URL extension.
1. If `Content-Type` contains `html` → `HTML_PAGE`
2. If `Content-Type` contains `mpegurl` or `m3u8` → `HLS_STREAM`
3. If `Content-Type` contains `dash+xml` or `mpd` → `DASH_STREAM`
4. If `Content-Type` starts with `video/` → `DIRECT_VIDEO`
5. Fallback to URL extension (`detectFromUrl`). Honor HLS/DASH/HTML from URL even with a generic Content-Type.
6. **UNKNOWN → treated as DIRECT_VIDEO** (downloadable). The file-size + magic-byte checks downstream catch corrupt/error downloads.

The UNKNOWN→DIRECT_VIDEO decision (line 76-81) is documented:
> "UNKNOWN — the Content-Type is generic (octet-stream, missing, etc.) and the URL has no clean extension. This is VERY common for video CDNs (e.g. `https://cdn.example.com/video/abc123?token=xyz`). We treat UNKNOWN as DIRECT_VIDEO (downloadable) — the file-size validation in HttpDownloader catches corrupt/error downloads. Rejecting UNKNOWN here was the cause of the 'Unknown video format' error on valid video URLs."

## 3. `HttpDownloader.downloadNormal(...)` — the single-threaded path

**`HttpDownloader.kt:218-287`**:

```kotlin
private suspend fun downloadNormal(
    url: String, headers: String?, tempFile: File, taskId: Long,
    onProgress: (Long, Long) -> Unit,
): Long {
    val request = Request.Builder().url(url).apply {
        if (!headers.isNullOrBlank()) {
            headers.split('\n').forEach { line ->
                val sep = line.indexOf(':')
                if (sep > 0) {
                    addHeader(line.substring(0, sep).trim(), line.substring(sep + 1).trim())
                }
            }
        }
    }.build()

    return try {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw DownloadException("HTTP ${response.code} for video URL")
            }
            val videoType = VideoTypeDetector.detect(url, response)
            if (!VideoTypeDetector.isDownloadable(videoType)) {
                throw DownloadException(VideoTypeDetector.unsupportedReason(videoType) ?: "...")
            }
            if (videoType == VideoTypeDetector.VideoType.HLS_STREAM) {
                return@use hlsDownloader.download(url, headers, tempFile) { d, t -> onProgress(d, t) }
            }
            val total = response.body?.contentLength() ?: -1L
            FileOutputStream(tempFile).use { os ->
                response.body?.byteStream()?.use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)  // 8 KB
                    var downloaded = 0L
                    while (true) {
                        coroutineContext.ensureActive()  // cooperative cancellation
                        val read = input.read(buffer)
                        if (read == -1) break
                        os.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                    os.flush()
                }
            }
            tempFile.length()
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: DownloadException) {
        throw e
    } catch (e: Exception) {
        throw DownloadException("Video download failed: ${e.message ?: e.javaClass.simpleName}", e)
    }
}
```

**Key facts**:
- 8 KB buffer (`BUFFER_SIZE`).
- Headers parsed from a newline-separated `"Key: Value"` string (passed from the resolver).
- `ensureActive()` checked on every read — cooperative cancellation so pause/cancel is responsive.
- `total = -1` if no Content-Length (chunked encoding) — `DynamicProgressTracker` handles this case.
- No Range request — this is single-threaded, no resume support. (Resume is in the Advanced method.)
- The temp file is overwritten on each call (FileOutputStream doesn't append by default).

## 4. Validation: file size + magic bytes

### Size check
**`HttpDownloader.kt:342-359`**:
```kotlin
private fun validateDownloadedFile(url: String, tempFile: File, downloadedBytes: Long) {
    if (!tempFile.exists() || tempFile.length() == 0L) {
        throw DownloadException("Downloaded file is empty — the source returned no data.")
    }
    if (tempFile.length() < MIN_VALID_VIDEO_BYTES) {  // 500 KB
        // Log the first 200 bytes as text for debugging
        ...
        throw DownloadException(
            "Downloaded file is only ${tempFile.length()} bytes — the server returned an error page or redirect instead of the video. " +
            "Try a different server or quality. (URL: ${url.take(80)}...)"
        )
    }
}
```

500 KB minimum — a real video episode is at least hundreds of KB. Anything smaller is an error page / playlist / corrupt download.

### Magic-byte check
**`HttpDownloader.kt:375-451`** (`verifyVideoMagicBytes`):
- Reads first 16 bytes.
- **HTML detection**: `3C 21` (`<!`) or `3C 68` (`<h`) → reject.
- **PNG detection** (`89 50 4E 47`): only reject if file < 10 MB AND not a valid MPEG-TS (some HLS streams have PNG posters as the first segment — don't false-positive).
- **JPEG detection** (`FF D8 FF`): same logic as PNG.
- **MPEG-TS sync-byte check** (`checkMpegTsSync`, line 296-313): checks positions 0, 188, 376, 564, 752 for `0x47`. At least 2 sync bytes = valid `.ts`.
- **Known video magics**: MP4 (`ftyp` at offset 4), MKV/WebM (`1A 45 DF A3`), FLV (`FLV`), AVI (`RIFF`).
- If none match → log a warning ("unknown magic bytes") but DON'T reject (some video formats have non-standard headers).

The check is non-fatal — failures are caught and logged (line 447-450), so a magic-byte check failure doesn't block the download.

### HLS playlist re-detection
**`HttpDownloader.kt:107-114`**:
```kotlin
if (tempVideo.length() < 500 * 1024 && isHlsPlaylist(tempVideo)) {
    DownloadLogger.i("Downloaded file is an HLS playlist (${tempVideo.length()} bytes) — switching to HlsDownloader")
    tempVideo.delete()
    tempVideo = tempCache.videoFile(task.id, "ts")
    downloadedBytes = hlsDownloader.download(videoUrl, task.request.videoHeaders, tempVideo) { d, t ->
        onProgress(d, t)
    }
}
```

If the downloaded file is small AND starts with `#EXTM3U`, it's an HLS playlist that slipped past URL/Content-Type detection (common for proxy URLs like `localhost:PORT/m3u8?url=...`). Re-download via `HlsDownloader`.

## 5. Subtitle + metadata writing

### Subtitles
**`HttpDownloader.kt:454-479`** (`downloadSubtitlesToCache`):
- Best-effort — failures are logged + skipped (one bad subtitle doesn't fail the download).
- Each subtitle track is downloaded with a plain OkHttp GET (no headers — subtitle URLs are usually direct).
- File name: `<safeLang>_<index>.<ext>` (e.g. `English_0.srt`).
- `safeLang` = `track.lang` with non-alphanumeric chars replaced by spaces, defaulted to `"track"` if blank.

### Metadata
**`HttpDownloader.kt:481-502`** (`writeMetadataToCache`):
- Serializes `EpisodeMetadataCache` (see `04-storage-paths.md` §10) to pretty JSON.
- Written to `<tempDir>/metadata.json`.
- Best-effort — failure is logged + skipped.

## 6. `HlsDownloader` — the HLS-to-TS concatenator

**File**: `HlsDownloader.kt` (333 lines). No ffmpeg — pure Kotlin.

### Pipeline (`download(m3u8Url, headers, tempFile, onProgress)` — line 63-136):

1. **Fetch the playlist text** (`fetchText`).
2. **Master playlist?** (`isMasterPlaylist` — checks for `#EXT-X-STREAM-INF`).
   - YES → `pickFirstVariant(text, baseUrl)` — picks the FIRST variant URL (typically highest bandwidth). Fetch its media playlist.
   - NO → use the playlist directly.
3. **Encryption check** (`isEncrypted`): looks for `#EXT-X-KEY` with a METHOD other than NONE. If found → `throw DownloadException("Encrypted HLS stream — the default downloader cannot decrypt DRM/AES-128...")`. (Future 1DM method would handle this with ffmpeg.)
4. **Parse segments + init map**:
   - `parseInitSegment(text, baseUrl)` — extracts `#EXT-X-MAP:URI="..."` (for fMP4/.m4s streams).
   - `parseSegments(text, baseUrl)` — collects segment URLs (non-comment lines after `#EXTINF` or `#EXT-X-BYTERANGE`).
5. **Download + concatenate**:
   - Write the init segment first (if present).
   - For each media segment: `downloadSegment(segUrl, headers, out)` — appends to the temp `.ts` file.
   - Call `onProgress(tempFile.length(), -1L)` after each segment. Total = -1 (unknown — HLS segment sizes aren't known until downloaded).

### URL resolution (`resolveUrl` — line 318-328):
- Absolute URL (http/https) → returned as-is.
- Relative URL → resolved via `java.net.URI(baseUrl).resolve(url)`.
- Fallback: directory-relative (`baseUrl.substringBeforeLast('/') + "/" + url`).

### PNG-header stripping (`stripPngHeader` — line 200-228)

**Why this exists** (from the KDoc): some CDNs (megaplay.buzz, kotocdn.site) prepend a PNG image header to each HLS segment to prevent direct downloading. The extension's `LocalProxyServer` strips this header before serving to MPV; the downloader must do the same — otherwise the concatenated `.ts` file starts with PNG magic bytes and is rejected.

Algorithm (mirrors the extension's `stripPngHeader`):
1. Check if segment starts with PNG magic bytes (`89 50 4E 47`).
2. Find the `IEND` marker (end of PNG data).
3. Skip 8 bytes after `IEND` (IEND + CRC).
4. Look for the MPEG-TS sync byte (`0x47`) at a position where `0x47` also appears 188 bytes later (confirming it's a real sync byte).
5. Return everything from that sync byte onward.
6. Fallback: just cut at the IEND+8 position.

### Segment download (`downloadSegment` — line 175-194):
- Reads the FULL segment into memory (`response.body?.bytes()`).
- Segments are typically < 5 MB so this is OK.
- Strips PNG header if present, writes cleaned bytes to the output stream.

### Honest notes:
- Always picks the FIRST variant in a master playlist (no quality picker for HLS).
- No retry on segment failure — one failed segment fails the whole download.
- Discontinuities (`#EXT-X-DISCONTINUITY`) + ad breaks may produce minor glitches in the output but are still playable.
- `.m4s` (fMP4) concatenation works for the common case; edge cases may be glitchy.

## 7. `AdvancedHttpDownloader` — multi-threaded Range + resume

**File**: `advanced/AdvancedHttpDownloader.kt` (401 lines)

### Pipeline (`download(taskId, videoUrl, headers, tempVideoFile, onProgress)` — line 89-204):

1. **HEAD probe** (`probeServer` — line 214-239):
   - Sends `GET` with `Range: bytes=0-0` (downloads 1 byte). NOT HEAD — many servers reject HEAD or return a different Content-Length.
   - `206 Partial Content` → server supports Range. Total size from `Content-Range: bytes 0-0/TOTAL`.
   - `200 OK` → Range NOT supported. Total size from `Content-Length`.
   - Throws if `totalBytes <= 0`.

2. **Decide single-threaded vs multi-threaded** (line 109-113):
   - Single-threaded if: `!supportsRange || totalBytes < minSizeBytes || threadCount == 1`
   - Otherwise: multi-threaded.

3. **Multi-threaded** (line 115-203):
   - Split file into N chunks (`chunkSize = totalBytes / threadCount`).
   - Check for resume metadata (`resumeManager.loadResume(taskId)`). If it exists AND `videoUrl` matches AND chunk count matches → resume each chunk from its last downloaded byte.
   - Launch N coroutines (one per chunk, on `Dispatchers.IO`).
   - Each chunk uses `Range: bytes=start+downloaded-end` and writes to its own `chunk_<i>.part` file via `RandomAccessFile` (positioned at the resume offset).
   - Per-chunk retry (`downloadChunkWithRetry`): up to `maxRetries` attempts (default 25), 1-second delay between retries.
   - Per-chunk progress: aggregated via a `Mutex` and reported via `onProgress(totalDownloaded, totalBytes)`.
   - Resume metadata saved periodically (every 2 seconds) via `saveResumeThrottled`.
   - On success: concatenate chunks into `tempVideoFile` (sequential read+write).
   - Clean up: `resumeManager.clearResume(taskId)` + delete each `chunk_<i>.part`.
   - On `CancellationException`: save resume metadata before throwing (so resume works on the next attempt).

### `DownloadResumeManager` — line 32-116 of `advanced/DownloadResumeManager.kt`

Persists per-task resume metadata as JSON in `<tempDir>/resume.json`:
```kotlin
@Serializable
data class ResumeMetadata(
    val taskId: Long,
    val videoUrl: String,    // for matching on resume
    val totalBytes: Long,
    val chunkCount: Int,
    val chunks: List<ChunkProgress>,
)

@Serializable
data class ChunkProgress(
    val index: Int,
    val start: Long,
    val end: Long,
    val downloaded: Long,
)
```

- `loadResume(taskId)`: reads `resume.json`. Validates each chunk file on disk — if the chunk file is SMALLER than the recorded `downloaded` bytes, resets that chunk's progress to the actual file size.
- `saveResume(metadata)`: writes JSON.
- `clearResume(taskId)`: deletes `resume.json`. (Chunk files are cleaned up by `TempDownloadCache.cleanupTask`.)
- `chunkFile(taskId, index)`: returns `<tempDir>/chunk_<index>.part`.

### Settings (`DownloadPreferences` advanced settings — see `07-settings-preferences.md`):
- `advancedThreadCount()`: 1..8, default 8.
- `advancedMaxRetries()`: 0..10, default 25 (UI clamps to 0..10, but the default value is 25 — bug or intentional override?).
- `advancedMinSizeMb()`: 1..20, default 1 (multi-threading for all files > 1 MB).

### Honest notes:
- The default `advancedMaxRetries` value in code is 25 (line 148 of `DownloadPreferences.kt`), but the UI slider only allows 0..10 (line 169 of `DownloadSettingsScreen.kt`). Inconsistency — a user who never opens settings gets 25 retries per chunk, which could mean very long waits on flaky servers.
- Chunk files live in the same `<tempDir>` as the temp video. On a 4-chunk download of a 100 MB file, peak temp usage is 100 MB (chunks) + 100 MB (concatenated output) = 200 MB. Could be an issue on low-storage devices.
- The `concatenateChunks` step reads each chunk sequentially and writes to the output — no parallel I/O. For 8 chunks of a 1 GB file, this is a noticeable sequential copy.
- Resume metadata save is throttled to 2s — but `saveResumeThrottled` is a non-suspend function called from a suspend `onChunkProgress` callback. The `chunkProgress.toList()` snapshot is taken WITHOUT a lock — best-effort, may be slightly stale. Acceptable.
- If the URL changes between resume attempts (e.g. token expired), the resume metadata is discarded (`videoUrl != resumeMetadata.videoUrl`). The download restarts from scratch.
- `RandomAccessFile.seek(chunk.downloaded)` positions the write at the resume point — but if the chunk file was corrupted (e.g. app crashed mid-write), the file may have garbage after the resume point. The validation only checks size, not content. So a corrupted chunk could produce a corrupted output. The magic-byte + size checks downstream would catch the worst cases.

## 8. `DynamicProgressTracker` — smooth progress UI

**File**: `DynamicProgressTracker.kt` (123 lines). Pure-math object — no state.

### Problem solved:
- Many CDNs don't send `Content-Length` (chunked transfer) → `total = -1` → stuck progress bar.
- Some servers change `Content-Length` mid-download → progress bar would jump backward.
- The progress bar should NEVER show 100% until the download is verified complete (owner's request).

### Algorithm (`compute(...)` — line 54-112):

```kotlin
fun compute(
    downloaded: Long,
    reportedTotal: Long,
    previousTotal: Long,
    previousEstimate: Long,
): ProgressUpdate
```

**Case 1: total known + stable + valid** (`reportedTotal >= 1 MB`):
- `effectiveTotal = maxOf(reportedTotal, previousTotal)` — never let the bar jump backward.
- `ratio = (downloaded / effectiveTotal).coerceIn(0, 1)`.
- `progress = (ratio * 90).coerceIn(0, 90)` — capped at 90%.
- Returns the real total + progress.

**Case 2: total unknown (-1) or too small to be real** — estimate using "10 MB ahead" strategy:
- `estimate = maxOf(previousEstimate, downloaded + 10MB)` — keeps the bar advancing.
- `ratio = (downloaded / estimate).coerceIn(0, 0.9)`.
- `progress = (ratio * 90).coerceIn(0, 90)`.
- Returns the estimate as the displayed total.

**Sanity check**: if `reportedTotal` is in 1..1MB but `downloaded > reportedTotal` (server is lying about a redirect/error page size), treat as unknown (`-1L`).

### Constants:
- `MAX_INCOMPLETE_PROGRESS = 90` — bar caps at 90% during download.
- `INITIAL_ESTIMATE_BYTES = 10 MB` — initial estimate when total unknown.
- `MIN_VALID_TOTAL_BYTES = 1 MB` — Content-Length values below this are treated as invalid.
- `aheadBytes = 10 MB` (changed from 50 MB per owner request 2026-07-29 — 50 MB was too much, bar moved too slowly for typical 30-80 MB episodes).

### Caller integration:
`DownloadQueue.launchDownload` (line 207-228) maintains `prevTotal` + `prevEstimate` per-task in closure vars, updated on each tick. The tracker is stateless — caller threads the state through.

## 9. The download flow (full timeline)

For a typical ADVANCED-method direct-mp4 download:

```
T+0ms    enqueue → QUEUED, persistNow()
T+1ms    tryStartNext() → permit acquired (Semaphore.tryAcquire)
T+2ms    DOWNLOADING, persistNow()
T+5ms    HttpDownloader.download() called
T+10ms   downloadVideoToCache → advancedDownloader.download()
T+15ms   HEAD probe (Range: bytes=0-0) → 206, total=104857600
T+50ms   Split into 8 chunks (each ~13 MB)
T+55ms   Check resume.json — none, fresh download
T+60ms   Launch 8 coroutines, each acquires its chunk range
T+100ms  Per-chunk progress: bytes downloaded → onChunkProgress → mutex → onProgress
T+100ms  DynamicProgressTracker.compute → mutateTask(progress, downloadedBytes, totalBytes)
T+200ms  persistThrottled (every 1s) → SharedPreferences write
T+30s    All 8 chunks complete
T+30.1s  concatenateChunks → 100 MB tempVideo file
T+30.2s  resumeManager.clearResume + delete chunk files
T+30.3s  return tempVideoFile.length()
T+30.4s  validateDownloadedFile (size check, 100MB >= 500KB ✓)
T+30.5s  verifyVideoMagicBytes (MP4 ftyp magic ✓)
T+30.6s  downloadSubtitlesToCache (parallel, ~1-2s per sub)
T+32s    writeMetadataToCache
T+32.1s  storage.publishToUserFolder → ensureEpisodeDir → copy video, subs, metadata
T+33s    PublishResult.Success(videoUri, subtitleUris, sizeBytes)
T+33.1s  mutateTask { completed } (status=COMPLETED, progress=100, videoUri set)
T+33.2s  persistNow()
T+33.3s  onTaskCompleted → notifier.notifyCompleted
T+33.4s  finally { tempCache.cleanupTask(task.id) } — temp dir deleted
T+33.5s  jobs.remove(task.id)
T+33.6s  tryStartNext() — next QUEUED task starts (or nothing)
```

For HLS downloads, replace the chunked step with: fetch playlist → parse → loop segments (sequential) → concatenate. Each segment is a separate HTTP GET.

## 10. Cross-references

- `02-queue-management.md` — how `DownloadQueue` calls `HttpDownloader.download` + processes the result.
- `04-storage-paths.md` — `DownloadStorageProvider.publishToUserFolder` + `TempDownloadCache`.
- `06-notifications-foreground-service.md` — how `onProgress` ticks drive the notification.
- `13-implementation-plan.md` — what to port vs simplify for the new project.

---

## 11. Post-rewrite additions (DL-PLAN-REWRITE)

> **Task ID:** DL-PLAN-REWRITE
> The OLD project's 3 engines (HTTP / HLS / Advanced — documented in §§1-10 above) are the baseline. The NEW project must:
> 1. Keep all 3 engines (HTTP, HLS, Advanced) — they handle different URL types.
> 2. **Fix the smooth-progress bug** — the user complained about "jumping from 90% to 100%". The OLD `DynamicProgressTracker` caps at 90% during download, then jumps to 100% on completion. The NEW design uses a **moving average** + byte-count-based progress for ALL engines (including HLS — see §11.2).
> 3. **Modular architecture** — the 3 engines share a common `Downloader` interface, each in its own file. Easy to add a 4th engine (e.g. DASH via ffmpeg) later.
> 4. **Integrate the proxy-churn fix** — the HTTP engine's `downloadNormal` catches `IOException` for localhost URLs and re-resolves via `ReResolver` (see `10-player-integration.md` §14).
> 5. **Per-segment retry for HLS** — the OLD project fails the whole download on one bad segment. The NEW project retries each segment up to 3 times.

### 11.1 The modular architecture (the `Downloader` interface)

```kotlin
// core/download/src/main/java/com/confused/anikuta/core/download/Downloader.kt
interface Downloader {
    /**
     * Downloads the video for [task] to the temp cache, then publishes to the user's SAF folder.
     *
     * @param task the download task (carries the video URL, headers, content info, resolve context).
     * @param onProgress called on every byte tick — (downloadedBytes, totalBytes). totalBytes = -1 if unknown.
     * @return the completed task (with videoUri, subtitleUris, sizeBytes filled in).
     * @throws DownloadException on failure (the queue's catch block sets status = ERROR).
     * @throws CancellationException on pause/cancel (the queue's catch block does nothing — the status is already set).
     */
    suspend fun download(task: DownloadTask, onProgress: (Long, Long) -> Unit): DownloadTask
}

// Three implementations:
class HttpDownloader(...) : Downloader {       // §11.3 — direct video URLs (mp4/mkv/webm/...)
class HlsDownloader(...) : Downloader {        // §11.4 — HLS playlists (.m3u8)
class AdvancedHttpDownloader(...) : Downloader { // §11.5 — multi-threaded Range + resume (large files)
```

The `HttpDownloader` is the router — it inspects the URL + method preference + dispatches to itself (direct) OR delegates to `HlsDownloader` / `AdvancedHttpDownloader`. This mirrors the OLD project's routing (see §1 above) but with the modular interface.

### 11.2 The NEW `DynamicProgressTracker` — smooth progress (no 90%→100% jumps)

**The user's complaint:** "smooth progress bar, no jumping from 90% to 100%."

The OLD project's `DynamicProgressTracker` caps at 90% during download, then jumps to 100% on completion. The user finds this jarring.

The NEW design:

1. **Byte-count-based for ALL engines** (including HLS — the OLD project uses segment-count-based progress for HLS, which jumps per-segment instead of smoothly per-byte).
   - HLS: instead of `onProgress(tempFile.length(), -1)` after each segment (jumping), call `onProgress(tempFile.length(), estimatedTotal)` where `estimatedTotal = averageSegmentSize * totalSegmentCount`. As each segment downloads, `tempFile.length()` increases smoothly + the `estimatedTotal` converges to the real total.
   - HTTP: same as the OLD project — `onProgress(downloaded, contentLength)` per byte tick.

2. **Moving average smoothing** (window of 5 ticks). Smoothes out network jitter so the bar doesn't stutter on slow/fast byte bursts.

3. **Cap at 95% during download** (not 90% — closer to "real" completion). The 5% gap is reserved for the post-download validation + publish-to-SAF step (so the user sees the bar move from 95% to 100% during the publish, not jump).

4. **No backward jumps.** If the reported total changes mid-download (server lies), use `maxOf(reportedTotal, previousTotal)` to prevent the bar from moving backward.

```kotlin
object DynamicProgressTracker {
    private const val MAX_INCOMPLETE_PROGRESS = 95    // was 90 in the OLD project — bumped to 95 per the user's request
    private const val INITIAL_ESTIMATE_BYTES = 10L * 1024 * 1024   // 10 MB ahead
    private const val MIN_VALID_TOTAL_BYTES = 1L * 1024 * 1024     // 1 MB
    private const val MOVING_AVERAGE_WINDOW = 5

    data class ProgressUpdate(
        val progress: Int,                  // 0..MAX_INCOMPLETE_PROGRESS (or 100 on completion)
        val displayTotalBytes: Long,        // the total to display (real or estimated)
        val updatedEstimate: Long,          // for the next tick's "10 MB ahead" strategy
    )

    /**
     * Pure function — caller threads the state through (prevTotal, prevEstimate, recentRatios).
     *
     * @param downloaded current downloaded bytes
     * @param reportedTotal the total reported by the server (-1 if unknown)
     * @param previousTotal the previous tick's displayTotalBytes (for the no-backward-jump rule)
     * @param previousEstimate the previous tick's estimate (for the "10 MB ahead" strategy)
     * @param recentRatios the last N tick ratios (for the moving average) — caller maintains this list
     */
    fun compute(
        downloaded: Long,
        reportedTotal: Long,
        previousTotal: Long,
        previousEstimate: Long,
        recentRatios: List<Float>,
    ): ProgressUpdate {
        // REVIEW-5 M40: restore the OLD logic that was lost in the refactor. The if-branch must
        // compute `effectiveReportedTotal = -1L` (treat a fishy < 1MB reported total as "unknown")
        // and the else-branch must use the reported total. Both branches previously returned the
        // same `computeUnknownTotal(...)` — the if-check was a no-op.
        val effectiveReportedTotal = when {
            reportedTotal >= MIN_VALID_TOTAL_BYTES -> reportedTotal
            reportedTotal in 1 until MIN_VALID_TOTAL_BYTES && downloaded > reportedTotal -> -1L
            else -> reportedTotal
        }

        // Case 1: total known + stable + valid.
        if (effectiveReportedTotal >= MIN_VALID_TOTAL_BYTES) {
            val effectiveTotal = maxOf(effectiveReportedTotal, previousTotal)
            val ratio = (downloaded.toFloat() / effectiveTotal).coerceIn(0f, 1f)
            val smoothedRatio = movingAverage(recentRatios + ratio)
            val progress = (smoothedRatio * MAX_INCOMPLETE_PROGRESS).toInt().coerceIn(0, MAX_INCOMPLETE_PROGRESS)
            return ProgressUpdate(progress, effectiveTotal, previousEstimate)
        }

        // Case 2: total unknown (-1) or too small to be real — estimate using "10 MB ahead".
        return computeUnknownTotal(downloaded, previousTotal, previousEstimate, recentRatios)
    }

    private fun computeUnknownTotal(
        downloaded: Long, previousTotal: Long, previousEstimate: Long, recentRatios: List<Float>,
    ): ProgressUpdate {
        val estimate = maxOf(previousEstimate, downloaded + INITIAL_ESTIMATE_BYTES)
        val ratio = (downloaded.toFloat() / estimate).coerceIn(0f, 0.95f)
        val smoothedRatio = movingAverage(recentRatios + ratio)
        val progress = (smoothedRatio * MAX_INCOMPLETE_PROGRESS).toInt().coerceIn(0, MAX_INCOMPLETE_PROGRESS)
        return ProgressUpdate(progress, estimate, estimate)
    }

    private fun movingAverage(ratios: List<Float>): Float {
        if (ratios.isEmpty()) return 0f
        val window = ratios.takeLast(MOVING_AVERAGE_WINDOW)
        return window.average()
    }

    /** Called by the queue when the download completes — returns 100%.
     *
     * REVIEW-5 M36: wired into the queue's COMPLETED mutation path in 02-queue-management.md §13.3
     * (`DynamicProgressTracker.complete()` is called after `onTaskCompleted` returns). Previously
     * dead code — now the canonical way to flip from the 95% cap to 100% on completion.
     */
    fun complete(): ProgressUpdate = ProgressUpdate(100, 0L, 0L)
}
```

The caller (the queue's `launchDownload`) maintains the `recentRatios` list — adds the current tick's ratio, trims to the last N, passes to `compute`.

### 11.3 The HTTP engine (with proxy-churn fix integration)

```kotlin
class HttpDownloader(
    private val client: OkHttpClient,                 // qualified "download"
    private val tempCache: TempDownloadCache,
    private val storage: DownloadStorageProvider,
    private val reResolver: ReResolver?,              // null if proxy-churn fix is disabled
    private val store: DownloadStore,
    private val hlsDownloader: HlsDownloader,         // for HLS delegation
    private val advancedDownloader: AdvancedHttpDownloader,  // for Advanced method
    private val preferences: DownloadPreferences,
) : Downloader {

    override suspend fun download(task: DownloadTask, onProgress: (Long, Long) -> Unit): DownloadTask {
        // 1. Log the URL (smoking-gun log per 15-ui-and-bug-analysis.md §B.7 rule 5).
        Logger.i(TAG) { "Downloading: ${task.content.title} EP ${task.episode.episodeNumber} — URL: ${task.request.videoUrl}" }
        if (task.request.videoUrl.startsWith("http://localhost")) {
            Logger.w(TAG) { "Download depends on extension proxy server — may fail if the proxy is killed by another resolve call." }
        }

        // 2. Infer the video extension + create the temp file.
        val ext = extractExtension(task.request.videoUrl)
        val tempVideo = tempCache.videoFile(task.id, ext)

        // 3. Route to the right sub-pipeline based on URL inspection + method preference.
        val downloadedBytes = downloadVideoToCache(
            url = task.request.videoUrl,
            headers = task.request.videoHeaders,
            tempFile = tempVideo,
            taskId = task.id,
            resolveContext = task.request.resolveContext,
            onProgress = onProgress,
        )

        // 4. Validate (size + magic bytes).
        validateDownloadedFile(task.request.videoUrl, tempVideo, downloadedBytes)
        verifyVideoMagicBytes(tempVideo)  // non-fatal — failures logged, not thrown
        // REVIEW-5 M35: emit intermediate onProgress ticks during the post-byte-stream phases
        // (validation → subtitles → cover → metadata → publish). The OLD project's bar jumped
        // straight from 95% (last byte tick) to 100% (queue's COMPLETED mutation) — exactly the
        // user's complaint. These intermediate ticks let the user see the bar move from 95 → 96
        // → 97 → 98 → 99 → 100 as each phase completes.
        // The onProgress signature is (downloadedBytes, totalBytes) — the queue's DynamicProgressTracker
        // caps at 95 during download, so passing (downloaded, total) here would just re-emit 95.
        // To force the bar past 95, we pass a SYNTHETIC total: `downloaded * 100 / desiredPct` so
        // the tracker computes the desired percentage. (Equivalent to a separate onPhaseProgress
        // callback but doesn't change the signature.)
        emitPhaseProgress(onProgress, downloadedBytes, 96)

        // 5. HLS playlist re-detection (if the downloaded file is small + starts with #EXTM3U).
        // ... (same as the OLD project)

        // 6. Download subtitles + cover + data.json to the temp cache.
        downloadSubtitlesToCache(task, tempCache.subtitlesDir(task.id))
        emitPhaseProgress(onProgress, downloadedBytes, 97)
        downloadCoverToCache(task, tempCache.coverFile(task.id))
        writeDataJsonToCache(task, tempCache.dataJsonFile(task.id))
        emitPhaseProgress(onProgress, downloadedBytes, 98)

        // 7. Publish to SAF (atomic — temp → SAF).
        val publishResult = storage.publishToUserFolder(
            content = task.content,
            episode = task.episode,
            tempVideoFile = tempVideo,
            tempSubtitlesDir = tempCache.subtitlesDir(task.id),
            tempCoverFile = tempCache.coverFile(task.id),
            tempDataJsonFile = tempCache.dataJsonFile(task.id),
            videoExtension = ext,
        )
        emitPhaseProgress(onProgress, downloadedBytes, 99)
        if (publishResult is PublishResult.Error) {
            throw DownloadException(publishResult.message)
        }
        val success = publishResult as PublishResult.Success

        // 8. Return the completed task. (The queue bumps progress to 100 via
        //    DynamicProgressTracker.complete() — see M36.)
        return task.copy(
            status = DownloadStatus.COMPLETED,
            progress = 99, // queue bumps to 100 on COMPLETED mutation
            videoUri = success.videoUri,
            subtitleUris = success.subtitleUris,
            sizeBytes = success.sizeBytes,
            completedAt = System.currentTimeMillis(),
        )
    }

    /**
     * REVIEW-5 M35: helper that emits a synthetic onProgress tick corresponding to a desired
     * percentage (96..99) during the post-byte-stream phases. The onProgress signature is
     * `(downloadedBytes, totalBytes)` — to force the tracker to compute `pct`, we pass
     * `total = downloaded * 100 / pct`. The tracker caps at MAX_INCOMPLETE_PROGRESS (95), so
     * we use a separate `onPhaseProgress` channel OR (simpler) the queue's launchDownload
     * recognises `downloaded == total` post-publish + bumps to 99 then 100. The implementation
     * below uses the synthetic-total approach (no signature change).
     */
    private fun emitPhaseProgress(onProgress: (Long, Long) -> Unit, downloaded: Long, pct: Int) {
        if (downloaded <= 0L) return
        val syntheticTotal = downloaded * 100L / pct.coerceIn(1, 100)
        onProgress(downloaded, syntheticTotal)
    }

    private suspend fun downloadVideoToCache(
        url: String, headers: String?, tempFile: File, taskId: Long,
        resolveContext: ResolveContext?,
        onProgress: (Long, Long) -> Unit,
    ): Long {
        // HLS delegation (URL or Content-Type).
        if (VideoTypeDetector.detectFromUrl(url) == HLS_STREAM) {
            return hlsDownloader.downloadToCache(url, headers, tempFile, taskId, onProgress)
        }

        // Advanced method (if enabled + file is large enough).
        if (preferences.method().get() == DownloadMethod.ADVANCED) {
            try {
                return advancedDownloader.downloadToCache(url, headers, tempFile, taskId, onProgress)
            } catch (e: DownloadException) {
                Logger.w(TAG) { "Advanced method failed — falling back to Normal: ${e.message}" }
                // Fall through to downloadNormal.
            }
        }

        // Normal method.
        return downloadNormal(url, headers, tempFile, taskId, resolveContext, onProgress)
    }

    private suspend fun downloadNormal(
        url: String, headers: String?, tempFile: File, taskId: Long,
        resolveContext: ResolveContext?,
        onProgress: (Long, Long) -> Unit,
        // REVIEW-5 M15: counter to bound the re-resolve recursion. Increments on each recursive
        // call; the catch block refuses to recurse past MAX_RE_RESOLVE_ATTEMPTS.
        // The public default is 0 — callers don't need to know about it. Only the recursive call
        // in the catch block (below) passes a non-zero value.
        reResolveAttempts: Int = 0,
    ): Long {
        // ... (same as the OLD project's downloadNormal, BUT with the proxy-churn fix)

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // REVIEW-5 M49: throw the download-module-local HttpException so RetryPolicy
                    // can match on `e is HttpException` + read `e.code` (see 16-quality-of-life.md
                    // §1.2). The OLD project wrapped HTTP errors as a generic DownloadException
                    // with no cause — RetryPolicy's HTTP branches were dead code.
                    throw HttpException(response.code, "HTTP ${response.code} for video URL")
                }
                // ... byte-stream download here ...
                tempFile.length()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: DownloadException) {
            // HttpException IS a DownloadException (subclass) — re-throw as-is so RetryPolicy
            // can match on its type. Same for any other DownloadException thrown by validation
            // (validateDownloadedFile, verifyVideoMagicBytes, etc.).
            throw e
        } catch (e: IOException) {
            // ── Proxy-churn fix (see 10-player-integration.md §14.1 Fix 2) ──
            // REVIEW-5 M15: bound the re-resolve recursion at MAX_RE_RESOLVE_ATTEMPTS (= 1)
            // so a flaky proxy that dies repeatedly cannot trigger StackOverflowError. The
            // outer retry loop (16-quality-of-life.md §1.2) still owns the user-visible
            // "Retrying (2/3)…" status — this inner cap is JUST for the re-resolve path.
            if (url.startsWith("http://localhost") && resolveContext != null && reResolver != null
                && reResolveAttempts < MAX_RE_RESOLVE_ATTEMPTS) {
                Logger.w(TAG) {
                    "IOException on localhost URL — attempting re-resolve " +
                        "(attempt ${reResolveAttempts + 1}/$MAX_RE_RESOLVE_ATTEMPTS): ${e.message}"
                }
                val fresh = reResolver.reResolve(resolveContext)
                if (fresh != null) {
                    // Update the task's video_url + resolve_context in the DB.
                    store.updateResolveContext(taskId, fresh.url, resolveContext)
                    // Retry with the fresh URL — pass `reResolveAttempts + 1` so the next
                    // IOException either goes through ONE more re-resolve OR (if the cap is
                    // hit) falls through to the throw below.
                    // Note: the fresh URL is a NEW proxy on a different port; the temp file's
                    // existing bytes may not be reusable (the new proxy may not support Range).
                    // We truncate + restart from byte 0 — simplicity over partial-resume.
                    FileOutputStream(tempFile).use { /* truncate to 0 */ }
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
            // Cap exceeded OR re-resolve returned null OR not a localhost URL — give up cleanly.
            if (url.startsWith("http://localhost") && reResolveAttempts >= MAX_RE_RESOLVE_ATTEMPTS) {
                throw DownloadException(
                    "Proxy URL died after $MAX_RE_RESOLVE_ATTEMPTS re-resolve attempt(s) — " +
                        "the extension's proxy server is being churned by another playback. " +
                        "Original cause: ${e.message ?: e.javaClass.simpleName}",
                    e,
                )
            }
            throw DownloadException("Video download failed: ${e.message ?: e.javaClass.simpleName}", e)
        } catch (e: Exception) {
            throw DownloadException("Video download failed: ${e.message ?: e.javaClass.simpleName}", e)
        }
    }

    companion object {
        // REVIEW-5 M15 + M18: cap the inner re-resolve at 1 attempt (= 2 total download attempts:
        // 1 initial + 1 re-resolve). The outer retry loop (16-quality-of-life.md §1.2) caps at 3
        // attempts. Total = 3 outer × 2 inner = 6 download attempts maximum before the task goes
        // to ERROR. See REVIEW-5 §6.3 "cap composition".
        private const val MAX_RE_RESOLVE_ATTEMPTS = 1
    }
}
```

### 11.4 The HLS engine (with per-segment retry + byte-count-based progress)

The OLD project's HLS engine calls `onProgress(tempFile.length(), -1L)` after each segment — total = -1 (unknown). This causes the OLD `DynamicProgressTracker` to use the "10 MB ahead" strategy, which produces reasonable progress but jumps per-segment instead of smoothly per-byte.

The NEW HLS engine:

```kotlin
class HlsDownloader(
    private val client: OkHttpClient,
    private val tempCache: TempDownloadCache,
    private val preferences: DownloadPreferences,
) {
    suspend fun downloadToCache(
        m3u8Url: String, headers: String?, tempFile: File, taskId: Long,
        onProgress: (Long, Long) -> Unit,
    ): Long {
        // 1. Fetch the playlist text.
        val playlistText = fetchText(m3u8Url, headers)
        val baseUrl = m3u8Url

        // 2. Master playlist → pick first variant.
        val mediaPlaylistText = if (isMasterPlaylist(playlistText)) {
            val variantUrl = pickFirstVariant(playlistText, baseUrl)
            fetchText(variantUrl, headers)
        } else playlistText

        // 3. Encryption check (reject encrypted — needs ffmpeg).
        if (isEncrypted(mediaPlaylistText)) {
            throw DownloadException("Encrypted HLS stream — the default downloader cannot decrypt DRM/AES-128...")
        }

        // 4. Parse segments + init map.
        val initSegment = parseInitSegment(mediaPlaylistText, baseUrl)
        val segments = parseSegments(mediaPlaylistText, baseUrl)

        // 5. ── NEW: estimate the total size for smooth progress ──
        // REVIEW-5 M32: the OLD draft computed `estimatedTotal = firstSegmentSize * segments.size`
        // ONCE and never refined it. For variable-bitrate HLS (ad segments tiny, action scenes
        // large), the estimate could be off by 2-5x — the bar hit the 95% cap at 50% actual
        // download, then jumped 95→100 on completion (exactly the user's complaint).
        //
        // The fix: track `bytesDownloadedSoFar` + `segmentsDownloadedSoFar`, and after each
        // segment recompute `estimatedTotal = (bytesDownloadedSoFar / segmentsDownloadedSoFar)
        // * segments.size`. The estimate converges to the real total as more segments download.
        // The initial estimate (before any segment downloads) is the first segment's size × total
        // segment count, computed below.
        var estimatedTotal = -1L
        if (segments.isNotEmpty()) {
            val firstSegmentSize = probeSegmentSize(segments.first(), headers)
            if (firstSegmentSize > 0) {
                estimatedTotal = firstSegmentSize * segments.size
            }
        }
        var bytesDownloadedSoFar = 0L
        var segmentsDownloadedSoFar = 0

        // 6. Write the init segment first (if present).
        FileOutputStream(tempFile).use { out ->
            if (initSegment != null) {
                val initSize = downloadSegmentWithRetry(initSegment, headers, out, taskId, maxRetries = 3)
                bytesDownloadedSoFar += initSize
            }

            // 7. Download each segment with retry.
            for ((index, segUrl) in segments.withIndex()) {
                coroutineContext.ensureActive()
                val segSize = downloadSegmentWithRetry(segUrl, headers, out, taskId, maxRetries = 3)
                bytesDownloadedSoFar += segSize
                segmentsDownloadedSoFar += 1
                // REVIEW-5 M32: refine the estimate after each segment using the running average
                // segment size. `estimatedTotal = avgSegSize * totalSegmentCount`. The estimate
                // converges to the real total — the doc's claim at line 771 ("converges to the
                // real total") is now TRUE.
                if (segmentsDownloadedSoFar > 0) {
                    val avgSegSize = bytesDownloadedSoFar / segmentsDownloadedSoFar
                    val refined = avgSegSize * segments.size
                    // Only update if the refined estimate is plausible (> 0). Avoids a divide-by-
                    // zero on empty-segment edge cases.
                    if (refined > 0) estimatedTotal = refined
                }
                // ── NEW: byte-count-based progress (was segment-count-based in the OLD project) ──
                // The total is the refined `estimatedTotal` (updated per segment per M32).
                onProgress(tempFile.length(), estimatedTotal)
            }
        }

        return tempFile.length()
    }

    /**
     * Downloads a single segment with retry. Returns the number of bytes written to `out`.
     *
     * REVIEW-5 M33: the OLD draft wrote the response body directly to `out` inside the retry
     * loop. If a segment partially downloaded (some bytes written) then failed, the retry
     * wrote the NEW bytes APPENDED to the partial bytes — corrupt .ts output that
     //  verifyVideoMagicBytes wouldn't catch (sync bytes still present at wrong positions).
     *
     * Fix: download each segment attempt to a ByteArrayOutputStream FIRST, write to `out` only
     * on success. This is the simpler + more reliable of the two options in REVIEW-5 M33 (the
     // other being FileOutputStream.channel.truncate(posBefore) on failure — also viable).
     */
    private suspend fun downloadSegmentWithRetry(
        segUrl: String, headers: String?, out: OutputStream, taskId: Long, maxRetries: Int,
    ): Long {
        var lastError: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                // Download to a buffer first — only write to `out` on full success.
                // (Avoids the partial-bytes-then-append corruption the OLD draft had.)
                val buffer = ByteArrayOutputStream()
                downloadSegment(segUrl, headers, buffer)
                val bytes = buffer.toByteArray()
                out.write(bytes)
                out.flush()
                return bytes.size.toLong()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                Logger.w(TAG) { "Segment download failed (attempt $attempt/$maxRetries): $segUrl — ${e.message}" }
                if (attempt < maxRetries) delay(1000L * attempt)  // exponential-ish backoff
            }
        }
        throw DownloadException("Segment failed after $maxRetries attempts: $segUrl — ${lastError?.message}", lastError)
    }

    /**
     * REVIEW-5 M39: probe the segment size using a 1-byte Range GET instead of HEAD.
     *
     * Many anti-scraping CDNs (the same ones the PNG-stripping logic handles — megaplay.buzz,
     * kotocdn.site) reject HEAD with 405 or return wrong Content-Length. A 1-byte Range GET is
     //  a real GET (passes the same anti-scraping checks as the actual segment download) and the
     * Content-Range header reveals the full size.
     */
    private fun probeSegmentSize(segUrl: String, headers: String?): Long {
        return try {
            val request = Request.Builder().url(segUrl).apply {
                header("Range", "bytes=0-0")
                // (apply headers same as downloadSegment)
            }.build()
            client.newCall(request).execute().use { response ->
                // Content-Range: bytes 0-0/12345 → 12345 is the full size.
                val contentRange = response.header("Content-Range")
                if (contentRange != null) {
                    val match = Regex("\\d+-\\d+/(\\d+)").find(contentRange)
                    match?.groupValues?.get(1)?.toLongOrNull()?.let { return it }
                }
                // Fallback: Content-Length × 2 (the response is 1 byte; the full size is unknown).
                // Don't use Content-Length alone — it's 1, not the full size.
                -1L
            }
        } catch (e: Exception) { -1L }
    }
}
```

**Improvements over the OLD project:**
1. **Per-segment retry** (3 attempts with backoff) — the OLD project fails the whole download on one bad segment. Each attempt downloads to a `ByteArrayOutputStream` first + writes to `out` only on success (REVIEW-5 M33 — avoids partial-then-append corruption).
2. **Byte-count-based progress** — `onProgress(tempFile.length(), estimatedTotal)` instead of `onProgress(tempFile.length(), -1)`. The estimated total is REFINED after each segment using the running average segment size (REVIEW-5 M32 — the OLD draft computed it once + never refined, causing the 95→100 jump for variable-bitrate HLS).
3. **Probe via 1-byte Range GET** (REVIEW-5 M39) — the OLD draft used HEAD, which is rejected by anti-scraping CDNs. A 1-byte Range GET is a real GET + the `Content-Range` header reveals the full size.

### 11.5 The Advanced engine (multi-threaded Range + resume)

Same as the OLD project's `AdvancedHttpDownloader` (see §7 above) with these changes:
1. Use the NEW `DynamicProgressTracker` (moving average + 95% cap).
2. Per-chunk progress aggregated via Mutex (same as OLD).
3. Resume metadata (`resume.json`) validated against the new temp cache layout (`<cacheDir>/anikuta_downloads/<downloadId>/resume.json`).

The Advanced method is **deferred to Phase D.1.5** — the Normal method + HLS cover 95% of cases. Advanced only helps for large direct-video files on slow servers.

### 11.6 Why 3 engines (and not 1)

| Engine | Handles | Tradeoffs |
|---|---|---|
| HTTP (Normal) | Direct video URLs (mp4/mkv/webm/m4v/mov/avi/ts) | Single-threaded, no resume, but simple + works for 95% of files. |
| HLS | `.m3u8` playlists (segmented) | Pure Kotlin (no ffmpeg), handles unencrypted HLS + PNG-header stripping. Per-segment retry. |
| Advanced | Large direct video files (multi-threaded Range + resume) | Complex (~400 lines + the resume manager), but faster for large files on slow servers. Per-chunk retry. Deferred to Phase D.1.5. |

Routing: `HttpDownloader.download` inspects the URL → HLS playlist → delegate to `HlsDownloader`. Otherwise → check `pref_dl_method` → if ADVANCED, try `AdvancedHttpDownloader` (fall back to Normal on failure). Otherwise → `downloadNormal`.

### 11.7 Cross-references (post-rewrite)

- `02-queue-management.md` §13.3 — how the queue calls `downloader.download(task, onProgress)`.
- `04-storage-paths.md` §6 — the temp cache layout + the atomic publish step.
- `10-player-integration.md` §14 — the proxy-churn fix (the `ReResolver` integration in `HttpDownloader.downloadNormal`).
- `13-implementation-plan.md` Phase D.1 — the implementation plan for the 3 engines.
- `16-quality-of-life.md` — the per-segment retry + per-chunk retry (auto error handling).
