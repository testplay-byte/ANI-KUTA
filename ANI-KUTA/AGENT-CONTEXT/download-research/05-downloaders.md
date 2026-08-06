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
