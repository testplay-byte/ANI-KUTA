# PLAN — Video Playback Cache + Parallel Download Engine

> Branch: `test-feature/video-cache-new-download` (from `main` @ 26e47722, post-merge of functionality/improvements, v0.2.47)
> Status: IMPLEMENTATION IN PROGRESS (this plan is the canonical spec for both features)
> Research: R-A (player pipeline), R-B (download system), R-C (infra patterns) — see sandbox worklog + this doc.

---

## PART A — Video Playback Cache

### A.1 User Requirements

1. While the user streams a video, the buffered/streamed bytes are cached locally.
2. Re-opening the **exact same video (same server + same resolution)** plays from cache instantly — no network round-trips, no resolve dependency.
3. Dedicated "Video caching" settings screen:
   - On/off toggle — **default ON**.
   - Max cache storage — **min 100 MB, max 2 GB**, user-configurable.
   - Cached-episodes view: anime, episode, **from which point it was cached**, storage used, and related info.

### A.2 Architecture

**New Gradle module `:core:playback-cache`** (package `com.confused.anikuta.core.playbackcache`).

```
MPV (libmpv) ──http──> CacheProxyServer (NanoHTTPD, 127.0.0.1, ephemeral port)
                          │ serves cached ranges from disk (.bin file)
                          │ fetches missing ranges from upstream (OkHttp DOWNLOAD client)
                          ▼
                    upstream URL (direct CDN or extension localhost proxy — volatile per session)
```

Why a local proxy (not file interception):
- MPV does its own networking; the app never touches the video stream today (R-A §3).
- A proxy transparently supports MPV's range/seek behavior.
- Fully-cached entries serve from disk at loopback speed — the "instant replay" requirement.

Components:

| Component | File | Job |
|---|---|---|
| `PlaybackCachePreferences` | `PlaybackCachePreferences.kt` | enabled (default true), maxCacheBytes (default 512 MB, clamped 100 MB..2 GB) |
| `PlaybackCacheEntry` | `PlaybackCacheModels.kt` | data class + `ByteRange` + ranges parse/merge/serialize |
| `PlaybackCacheStore` | `PlaybackCacheStore.kt` | SQLDelight-backed CRUD + reactive flows (RatingStore pattern) |
| `CacheProxyServer` | `CacheProxyServer.kt` | NanoHTTPD server — range-aware GET/HEAD serving |
| `PlaybackCacheManager` | `PlaybackCacheManager.kt` | facade: `playbackUrlFor()`, active-tracking, eviction, channels, delete/clear |
| `PlaybackCacheModule` | `di/PlaybackCacheModule.kt` | Koin |
| `playbackCache.sq` | in `:core:database` | `playback_cache_entry` table + queries |

### A.3 Stable identity & cache key (CRITICAL — URLs are volatile)

Extension localhost proxy URLs change **every resolve** (ports change; D-066). A URL-keyed cache would never hit. The codebase already solved stable identity for the player:

- `ResolverVideo.videoTitle = "$server|$audio|$quality|$urlHash"` — documented in `ResolverTypes.kt:53-55` as "a stable identifier used to match the currently-playing video across re-resolutions".

**Identity tuple** → `cache_key = sha256("$mainId\u001F$episodeNumber\u001F$sourceId\u001F$serverKey")` where `serverKey = videoTitle.substringBeforeLast('|')` (drops the volatile urlHash; used only when videoTitle has ≥3 `|` — else falls back to quality).

Identity is available at every load site:
- init: `watchKey` + `initialPickedVideo` (registry lookup; if null → **skip caching**, play direct — safe degradation)
- quality switch: the `ResolverVideo` param
- episode switch: the freshly picked video from `state.videos.first()`

Display metadata travels in the id: `PlaybackVideoId(mainId, animeTitle, episodeNumber, episodeTitle, sourceId, serverKey, quality)`.

### A.4 Database (SQLDelight — new table, no version bump)

`playbackCache.sq` in `:core:database` (no `.sqm`, no version bump — the unconditional `onCreate(db)` in `DatabaseDriverFactory` creates new `CREATE TABLE IF NOT EXISTS` tables on every open; R-C §1):

```sql
CREATE TABLE IF NOT EXISTS playback_cache_entry (
    cache_key TEXT PRIMARY KEY NOT NULL,
    main_id TEXT NOT NULL,
    anime_title TEXT NOT NULL,
    episode_number REAL NOT NULL,
    episode_title TEXT NOT NULL,
    source_id INTEGER NOT NULL,
    server_key TEXT NOT NULL,          -- "server|audio|quality"
    quality TEXT NOT NULL,
    content_type TEXT NOT NULL DEFAULT 'video/mp4',
    upstream_url TEXT NOT NULL,
    upstream_headers TEXT NOT NULL DEFAULT '',  -- MPV comma format
    content_length INTEGER,            -- NULL = unknown
    cached_bytes INTEGER NOT NULL DEFAULT 0,
    cached_ranges TEXT NOT NULL DEFAULT '',     -- "a-b,c-d" merged sorted
    complete INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    last_accessed_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_playback_cache_main ON playback_cache_entry(main_id);
```

Queries: `insertEntry` (**INSERT OR REPLACE** — concurrent first-opens could race a plain INSERT on the PK), `getEntry`, `updateUpstream`, `updateProgress`, `touchEntry`, `deleteEntry`, `deleteAll`, `listEntries` (ORDER BY last_accessed_at DESC), `totalCachedBytes` (SUM), `countEntries`, `deleteEntriesForMain`.

No FK to `main_entry` — cache data is denormalized + disposable (entries must survive/outlive content rows; cleanup is eviction, not cascade).

Storage layout: `<filesDir>/playback-cache/<cache_key>.bin` (app-private, no media scan).

### A.5 Playback flow (hook points)

**Hook: the 5 `MPVLib.command(arrayOf("loadfile", url, "replace"))` sites in WatchScreen** (init L622/L630, auto-retry L501, manual retry L743, quality L781, episode-switch L1008 — offline fd:// L895 excluded). All follow `setOptionString("http-header-fields", headers); command(loadfile, url, "replace")`.

**Header-setting code is NOT touched** (D-199: global headers stay for `audio-add` URL fetches; our proxy ignores what MPV sends it — it uses stored upstream headers). Only the URL is swapped — **`currentVideoUrl` keeps the ORIGINAL upstream URL** (re-wrapping at retry sites stays idempotent; the `127.0.0.1` contains-check at L571-572 only affects a log label).

**Identity is maintained as a `currentCacheId: PlaybackVideoId?` state var** (updated at every new-video site, used by all load sites):
- init: seeded from `watchKey` + `initialPickedVideo` (if `initialPickedVideo == null` → null → **skip caching**, play direct)
- quality switch: from the `ResolverVideo` param + `stateHolder.currentEpisodeNumber`
- episode switch: from `pickedResolverVideo` (the `ResolverVideo` — NOT `state.videos.first()` which is an extension `Video` without `videoTitle`) + the new episode's data
- **CRITICAL: episode number always comes from the LIVE episode state (`stateHolder.currentEpisodeNumber` / the new episode at switch), never from the frozen `watchKey.episodeNumber`** — otherwise ep 2's bytes file under ep 1's cache key (wrong-content replay corruption).

`PlaybackCacheManager.playbackUrlFor(id: PlaybackVideoId, upstreamUrl: String, headers: String): String` — **non-suspend** (called from non-suspend lambdas):
1. `http(s)://` only; else return original (fd:///content:///file:// bypass).
2. enabled == false → original.
3. Free disk space < 256 MB → original (fail-open).
4. Register in-memory descriptor `key → (id, upstreamUrl, headers)` (ConcurrentHashMap — refreshes upstream URL each play; **no DB write on the main/caller thread**).
5. Return `http://127.0.0.1:{port}/v/{cacheKey}` (server pre-started at app startup — see A.6).

Proxy request handling (NanoHTTPD worker thread — blocking IO is fine):
0. **DELETING check**: if the entry is mid-eviction/deletion (atomic state), serve as transparent pass-through WITHOUT caching.
1. Look up descriptor (always present — registered at play time). Create/refresh DB row: new entry → insert with identity metadata; existing → `updateUpstream` (fresh URL+headers) + `touchEntry` (LRU).
2. **Stale-file verification** on channel open: `.bin` missing → reset entry (ranges empty, complete=0); `file.length() < max(cached_ranges)` → clamp ranges to file size; `complete==1` but `file.length() != content_length` → reset. Also at startup sweep.
3. Parse `Range` (`bytes=a-b`, `bytes=a-`, `bytes=-n`; multi-range → ignore, serve 200 full).
4. Serve = for each sub-range of the requested span: cached part → read from `.bin` (FileChannel positional reads); gap → OkHttp GET with `Range: bytes=gapStart-gapEnd` + upstream headers (parsed by an internal MPV-format parser modeled on `DownloadHeaderParser`; **`Accept-Encoding: identity` on ALL upstream requests** — byte-offset integrity requirement; if a response arrives with `Content-Encoding` ≠ identity → pass-through without caching that response), tee-ing every upstream byte to the file (positional write) while forwarding to MPV.
5. Upstream 206 → cache exactly the requested gap. Upstream 200 (range ignored) → cache all received bytes, forward only the requested window. Upstream EOF reached on an open-ended request → mark `complete` + set `content_length` (if null). **Upstream 416 or Content-Range total ≠ recorded `content_length` → reset entry + re-serve from scratch** (upstream re-uploaded content). Unknown total (206 `bytes 0-1/*` or chunked 200 without length) → 200 chunked pass-through without caching.
6. Response: `206` + `Content-Range: bytes a-b/total` + **`Accept-Ranges: bytes` on EVERY response (incl. 200 and HEAD — ffmpeg gates seekability on it)** + `Content-Type` from entry/upstream, fixed-length streaming via a custom InputStream (lazily opens disk slices / upstream calls). No Range header → `200` full (same headers). HEAD → same headers, zero body bytes.
7. Ranges updated in memory (synchronized per entry), flushed to DB throttled (≥2 s or ≥4 MB delta or stream end). **Flusher uses UPDATE-only after deletion (tombstone check)** — never resurrects deleted rows.
8. Client disconnect (MPV seek/abort) → cancel upstream call, flush, decrement active count.

**Complete-entry fast path**: `complete == 1` → proxy serves purely from disk, upstream never contacted → the "instant, no-processing replay" requirement.

### A.5.1 Fail-open at PROXY time (hard requirement)

MPV has **no alternate-URL fallback** — a 502 surfaces as a permanent error for that video (auto-retry re-sends the same proxy URL). Therefore:
- **Pre-body internal error** (descriptor missing, DB/file error, unexpected exception before any body byte is sent) → respond **`302 Found` + `Location: <upstreamUrl>`** — ffmpeg follows redirects; the globally-set MPV headers (D-199) make the upstream request work.
- **Missing/truncated `.bin`** → treat as cache miss (reset ranges, stream-through from upstream).
- **Mid-stream failure** → close the connection (MPV reconnects and hits the pre-body path).

### A.6 Eviction & lifecycle

- Limit: `maxCacheBytes` pref. `evictIfNeeded()`: while `SUM(cached_bytes) > limit` → delete LRU entries **with zero active streams** (file + row + close channel). **Deletion is an atomic per-entry state** (`ConcurrentHashMap<String, EntryState>` with a DELETING marker via `compute()`): `serve()` re-checks after incrementing active count and serves pass-through (no caching) if DELETING.
- **The proxy server is pre-started at app startup** (`AnikutaApp` startup hook, alongside eviction — avoids a bind() on the main thread at first play). Server binds **`NanoHTTPD("127.0.0.1", 0)`** explicitly (never wildcard — app-private video bytes; the `HttpServer.kt` precedent binds 0.0.0.0 and must NOT be copied); port read from `listeningPort` after `start()`.
- Triggers: app startup, after throttled DB flush, after settings limit change.
- Per-entry deletion from settings: immediate if inactive; deferred (deleted on last stream close) if active.
- `clearAll()` from settings.
- Entries whose `main_id` content is deleted from library: kept (disposable cache; no FK). Optional future cleanup.

### A.7 Settings UI ("Video caching")

`:app/settings/VideoCachingScreen.kt` + `VideoCachingViewModel.kt` (NotificationsSettings pattern; `viewModelOf(::VideoCachingViewModel)` Koin registration — 2 params, no D-236 ambiguity):

- Section "General":
  - "Enable video caching" toggle (default ON) — description: caches streamed video for instant replay.
  - "Storage limit" slider — stored as **MB Int pref (100..2048)** (no LongSerializer exists in :core:preferences; converted to bytes in code), 100 MB steps (steps = 19), value text via private `formatBytes` + **private local copies of `SliderRow` and `BackAction`** (both are private in other screens — cannot be imported).
- Section "Storage": usage row (`X of Y used · N episodes`), "Clear cache" row with confirm.
- Section "Cached episodes": reactive list (DB flow): row = anime title; `EP {n} · {episodeTitle}`; `{serverKey} · {quality}`; **cached point**: `Cached to {formatBytes(prefixEnd)} · {pct}% of {formatBytes(total)}` (+ `· +k segments` when fragmented); size = cached_bytes; delete icon. Empty state.
- Nav: `@Serializable object VideoCachingKey : NavKey` + `when(currentKey)` branch + **import** (D-193 lesson) + `SettingsNavRow` in SettingsScreen ("Player" section, `Icons.Filled.VideoLibrary`) + add to `allowedUpdateSheetKeys` (every other settings screen is listed).

### A.8 Failure policy (fail-open, always)

- **Pre-loadfile**: any exception in `playbackUrlFor` → return original URL (MPV never sees the proxy).
- **At proxy time**: pre-body errors → `302 Found` redirect to the upstream URL (A.5.1); mid-stream → connection close; missing file → cache-miss reset. The cache must NEVER permanently break playback.

### A.9 Part A file list

**New:** `core/database/.../playbackCache.sq`; `core/playback-cache/` (build.gradle.kts: `anikuta.library` plugin, namespace `com.confused.anikuta.core.playbackcache`, deps = `:core:common`, `:core:database`, `:core:preferences`, `:core:network` (HttpClientFactory.DOWNLOAD qualifier), `libs.nanohttpd`, `libs.okhttp`, `libs.sqldelight.coroutines.extensions`, `libs.kotlinx.coroutines.core`, `platform(libs.koin.bom)`, `libs.koin.core`, `libs.koin.android` — androidContext() for filesDir) + `PlaybackCachePreferences.kt`, `PlaybackCacheModels.kt` (entry + ByteRange + id + header parser), `PlaybackCacheStore.kt`, `CacheProxyServer.kt`, `PlaybackCacheManager.kt`, `di/PlaybackCacheModule.kt`; `app/.../settings/VideoCachingScreen.kt` + `VideoCachingViewModel.kt`.
**Modified:** `settings.gradle.kts` (+include); `app/build.gradle.kts` (+project dep); **`feature/watch/impl/build.gradle.kts` (+ `implementation(project(":core:playback-cache"))`)**; `AnikutaApp.kt` (Koin module + `viewModelOf(::VideoCachingViewModel)` + startup: pre-start server + eviction); `MainActivity.kt` (NavKey + dispatch + import + allowedUpdateSheetKeys); `SettingsScreen.kt` (row + callback param); `WatchScreen.kt` (`currentCacheId` var + URL swap at load sites).

SQLDelight generated-name notes: accessor = `database.playbackCacheQueries` (from the .sq FILE name), row class = `Playback_cache_entry`; `episode_number REAL` → Double (convert `WatchKey.episodeNumber: Float` via `.toDouble()` once at key construction), `complete INTEGER` → Long (compare `== 1L`), `content_length` nullable → Long?, `SUM` wrapped in `COALESCE(..., 0)` → Long.

---

## PART B — Parallel Download Engine ("new download method")

### B.1 User Requirements

1. A new, MPV-inspired high-performance download method: multi-connection parallel byte-range fetching (progressive), concurrent segment fetching (HLS), in-memory AES-128 decryption, connection reuse, per-chunk exponential backoff, stall handling.
2. Settings toggle to turn the new method on/off.
3. Must integrate with the existing download system (queue, states, notifications, SAF, .data.json, resume/pause/cancel, re-resolve).

### B.2 Key discovery — the settings UI already exists

`DownloadPreferences.advancedDownloader` (`pref_dl_adv_enabled`, Boolean, default false), `advancedThreads` (`pref_dl_adv_threads`, 1..8, default 4), `advancedMaxRetries` (`pref_dl_adv_retries`, 0..10, default 10) — all live in the Downloads settings "Advanced" section (toggle + 2 sliders) but are **dead code today** (zero engine references — R-B §7). This plan wires the engine to them. **Default flips to ON** (user asked for the option; the feature is the point; easy off-switch).

### B.3 Architecture — keep HttpDownloader as the facade

`DownloadQueue` calls `downloader.download(task) { downloaded, total -> }` and the whole post-download pipeline (validation, subtitles, enrich, publish, .data.json upsert, cleanup, completion shape) lives in `HttpDownloader` (R-B §1-2). Duplicating that in a parallel engine would fork the riskiest code (D-241/D-242). Therefore:

**New seam: `VideoFetcher`** — only the "bytes → temp File" stage is pluggable:

```kotlin
interface VideoFetcher {
    suspend fun fetch(
        url: String,
        headers: String?,
        tempFile: File,
        taskId: Long,
        resolveContextJson: String?,
        onProgress: (Long, Long) -> Unit,
    ): Long   // returns total bytes downloaded (file length)
}
```

**`VideoFetcher`, `ParallelHttpFetcher`, `SingleConnectionFetcher` are PUBLIC top-level classes in `com.confused.anikuta.core.download`** (an internal interface injected into the public HttpDownloader constructor = Kotlin visibility error). Each fetcher re-implements a ~20-line `buildRequest` (HttpDownloader's is private; DownloadHeaderParser + Range + localhost `Accept-Encoding: identity` rules). Registered as **concrete-type Koin singles** (no `single<VideoFetcher>` interface bindings — ambiguity). Fetchers take `DownloadStore` (for the public `store.updateDownloadVideoUrl`), `HttpDownloader.ReResolver` (public nested fun interface), and `HlsDownloader` (for probe-time HLS delegation) as constructor params.

`HttpDownloader.downloadVideoToCache` routes:
- HLS → `HlsDownloader.downloadToCache` (unchanged signature; internally gains parallel mode + AES).
- HTTP + `advancedDownloader` ON → `ParallelHttpFetcher.fetch(...)`.
- HTTP + OFF → `SingleConnectionFetcher.fetch(...)` — today's `downloadNormal` body **extracted verbatim** (Range-resume, re-resolve recursion, HttpException mapping, 8 KB loop, ensureActive).

Both fetchers are Koin singles in `DownloadModule`. **Zero changes** to DownloadQueue, models, storage, scanner, notifications, MainActivity routing.

### B.4 ParallelHttpFetcher — progressive files

1. **Probe** (no HEAD — CDNs 405 it; `HlsDownloader.probeSegmentSize` precedent): `Range: bytes=0-1` GET with task headers. **Check the probe response's Content-Type — if `mpegurl`/`m3u8` → delegate to `HlsDownloader` (mirrors downloadNormal's :300-303 second-chance).**
   - `206` + `Content-Range: bytes 0-1/TOTAL` → parallel-capable, size known.
   - `206` + `Content-Range: bytes 0-1/*` (unknown total) or `200` → server ignores ranges → **internal fallback to single-stream** (sequential loop in the same fetcher; total from Content-Length when present).
2. **Resume**: sidecar `<tempdir>/video.<ext>.chunks` (JSON: `{url, total, chunks: [{start, end, pos}]}`). Present + total matches probe → resume per-chunk (pos = written offset). Absent + temp non-empty → **delete + restart**. **Symmetrically: `SingleConnectionFetcher` start detects a leftover `.chunks` sidecar (or `.hls-state.json`/`segments/`) → deletes temp + sidecar + restarts clean** (a sparse pre-allocated file would otherwise be treated as complete contiguous bytes → published with holes); it also clears stale `.hls-state.json`/`segments/`. Parallel-HLS start sweeps stale `.chunks`. Sidecar is written immediately at plan-build + flushed **per-chunk completion and ≥ every 5 s** (atomic: tmp + rename).
   - Extraction copy-list (downloadNormal's private members the fetcher needs): `client`, `hlsDownloader`, `store`, `reResolver` (ctor) + copied private `buildRequest`, `BUFFER_SIZE` + `MAX_RE_RESOLVE_ATTEMPTS` consts, and the `response.contentType()` file-level extension. Zero references to mutable HttpDownloader state — extraction is clean.
3. **Chunk plan**: N = `advancedThreads` (1..8), **effective N reduced when queue concurrency > 1 (connection budget ≤ 16: N_eff = max(1, min(N, 16 / concurrentDownloads)))**. chunk = total/N, merged while < 4 MB (floor 1 chunk). Pre-allocate `RandomAccessFile.setLength(total)` (sparse, instant, no fragmentation).
4. **Workers + progress reporter**: `coroutineScope { repeat(N) { launch(Dispatchers.IO) { worker(chunk) } } }`. **A dedicated progress-reporter coroutine samples the AtomicLong downloaded-total every 250 ms and is the ONLY caller of `onProgress`** (DownloadQueue's progress lambda mutates non-thread-safe state — ArrayDeque/vars — concurrent invocation = CME/corruption). Workers update only the AtomicLong. Each worker:
   - Loop: `Range: bytes=$pos-$end` request (task headers via `DownloadHeaderParser`; `Accept-Encoding: identity` for localhost per D-207).
   - `206` → stream 64 KB buffer: positional RAF write at `pos`, `pos += n`, atomic progress add.
   - `200` (range ignored) → read-and-discard prefix to `pos`, then stream+write as above.
   - `coroutineContext.ensureActive()` every iteration (cooperative pause/cancel — the queue's model).
   - **Premature EOF** (`read == -1` with `pos < end+1`) → retryable chunk failure.
   - **Per-chunk retry**: IOException / HTTP 5xx / 429 / premature-EOF → attempt++, backoff `min(2^attempt * 1000, 30_000)` ms, `maxRetries = advancedMaxRetries`; **other HTTP 4xx → see re-resolve below, else fatal `DownloadException`**; exceeded → fail.
   - **Stall watchdog**: chunk elapsed > `chunkSize / 50_000` B/s (the 50 KB/s floor) → abort call + retry (checked in read loop).
   - **Active-call registry** (synchronized `MutableSet<Call>`): all in-flight OkHttp calls registered; cancelled on cancellation/re-resolve so blocked reads (up to 60 s) don't stall teardown.
   - Progress is monotonic (sum of committed chunk bytes) — `DynamicProgressTracker` safe.
5. **Re-resolve** (localhost proxy death — D-149/D-194/D-207 semantics): **on ANY `HttpException` (incl. 403 — the primary proxy-churn case) or exhausted-IO-retry**, when localhost AND `resolveContextJson != null` AND `reResolver != null` AND attempts < 1: `fresh = reResolver.reResolve(json)` → null → **throw the ORIGINAL exception** (preserving HttpException type for RetryPolicy classification); non-null → `store.updateDownloadVideoUrl(taskId, fresh.url)` → cancel sibling calls, truncate temp + delete sidecar, rebuild plan with fresh URL/headers, restart workers (max 1 re-resolve — matches `MAX_RE_RESOLVE_ATTEMPTS`). The fetcher never touches queue state (RETRYING/ERROR remain queue-owned).
6. **Completion**: all chunks at `end+1` → delete sidecar, return total. Publish pipeline (in HttpDownloader) unchanged.

### B.5 HlsDownloader — parallel mode + AES-128

`downloadToCache` gains a mode split on `preferences.advancedDownloader.get()`:

**Legacy mode (toggle OFF)** — byte-for-byte today's behavior (sequential, first variant, encryption → hard reject). Untouched code path.

**Parallel mode (toggle ON)**:
1. Parse playlist as today (first variant — highest bandwidth; `EXT-X-MAP` first; segment list). **NEW parsing**: `#EXT-X-KEY` (`METHOD=AES-128,URI="...",IV=0x...` — reject METHOD values other than AES-128/NONE, e.g. SAMPLE-AES, with a clear error) + **`#EXT-X-MEDIA-SEQUENCE`** (required for default-IV derivation — 16-byte big-endian sequence number; the current parser does not track it). **KEY URI + segment URIs resolve against the VARIANT playlist URL, not the master** (fixes a pre-existing relative-URI bug — `baseUrl = m3u8Url` is wrong for master→variant flows). Key fetched once with headers; `Cipher.getInstance("AES/CBC/NoPadding")`; segment size validated `% 16 == 0` before decryption (clear error instead of IllegalBlockSizeException). PNG-header stripping happens **before** decryption.
2. **Concurrent fetch**: worker pool (**same connection-budget rule as B.4**: N_eff = max(1, min(threads, 16 / concurrentDownloads))) pulls segment indices from a shared cursor; each segment → fetch into spill file `<tempdir>/segments/<index>.ts` (per-segment retry upgraded to exponential backoff `min(2^n × 1000, 30_000)`, cap 3 attempts). **Spill bound**: fetched-but-not-yet-appended segments capped at `threads + 4` via Semaphore (head-of-line write stall must not accumulate a full episode of spills → 2× disk). **Single-key assumption: scan ALL `#EXT-X-KEY` lines — reject >1 distinct key** with a clear error (rotating keys out of scope).
3. **Ordered writer**: appends completed segments to tempFile strictly in index order (init segment first), deletes spill file after write. Output = same concatenated `.ts` as today → publish/playback/`.data.json` pipeline unchanged. **Sidecar `video.ts.hls-state.json` = `{segmentCount, firstSegmentUrl, lastSegmentUrl, appendedThrough, initDone, appendedBytes}` is updated AFTER each ordered append** (completedIndices = appended-to-tempFile, NOT fetched-to-spill) — atomic write (tmp + rename).
4. **Decryption**: encrypted segments decrypted in memory (before spill write) — no unencrypted temp disk writes; decrypted concat output identical in nature to today's plain output.
5. **Progress**: atomic sum of fetched bytes; a dedicated reporter coroutine samples every 250 ms and is the only `onProgress` caller (same serialization rule as B.4); `onProgress(sum, estimatedTotal)` — same probe-based estimate + running-average refinement as today.
6. **Pause/resume**: on resume, re-fetch playlist; validate sidecar (`segmentCount` + first/last segment URLs match) → mismatch → discard sidecar + restart. **Truncate tempFile to sidecar `appendedBytes`** (RAF.setLength — a crash between append and sidecar write would otherwise re-append already-written bytes → corrupt output). Resume skips appended segments. `ensureActive()` per segment; CancellationException → preserve temp dir. `cleanupTask(preserveForResume = true)` already preserves everything except `subtitle_*` files (verified — **no TempDownloadCache edit needed**).
7. **Legacy mode clears sidecar state**: the legacy (toggle-OFF) path deletes `segments/` + `.hls-state.json` at start (its `FileOutputStream(tempFile)` truncates the output — stale sidecars would corrupt a later parallel resume).

### B.6 Part B file list

**New:** `core/download/.../VideoFetcher.kt` (interface), `ParallelHttpFetcher.kt`, `SingleConnectionFetcher.kt` (extracted).
**Modified:** `HttpDownloader.kt` (route + extraction + fetcher constructor params), `HlsDownloader.kt` (parallel mode + AES + media-sequence + sidecar + variant-URL base fix), `DownloadModule.kt` (fetcher singles + HttpDownloader wiring), `DownloadPreferences.kt` (advancedDownloader default → true), `DownloadSettingsScreen.kt` (description text updates only).
**NOT modified:** `TempDownloadCache.kt` (verified: `cleanupTask(preserveForResume = true)` already preserves everything except `subtitle_*`), `DownloadQueue.kt`, models, storage, scanner, notifications.

---

## Shared integration notes

- **CI**: `build-apk.yml` gains `'test-feature/**'` trigger (branch-only edit, remove-before-merge comment — precedent: TEST_BETA_FEATURE).
- **Koin**: `playbackCacheModule` added to AnikutaApp's 24-module list (→25). Fetchers registered in `DownloadModule`.
- **Logger tags**: `Anikuta:Core:PlaybackCache`, `Anikuta:Core:Download:Parallel`, `Anikuta:Core:Download:Hls`.
- **No dashboard changes** on this branch (dashboard deploys from `main`; a truth-sweep is queued for merge time — noted in progress.md).
- **Docs**: DB docs (`DOCUMENTATION/database/playback-cache.md` + README + changelog), `decisions.md` D-243/D-244, `progress.md`, `changelog.md`, `lessons-learned.md` as they occur.

## Verification plan

1. Sub-agent compile review after each part (imports, module deps, Koin graph, SQLDelight query-name matching, Kotlin syntax — the project's #1 CI failure classes).
2. Push → GitHub Actions `Build APK` on the branch → green required (poll the API — never assume).
3. Device test checklist delivered at the end (per CORE_RULES §31): cache hit/miss, seek behavior, eviction, toggles, parallel download vs legacy, pause/resume, HLS, AES.

## Out of scope (documented, not built)

- Subtitle/audio-track caching (subs already go through OkHttp to local files).
- ffmpeg remux of HLS output (output format intentionally unchanged).
- Per-worker-count UI for playback cache; cache over-metered-network toggles.
- Dashboard data updates (deferred to merge-time truth sweep).

---

## SESSION 2 ADDENDUM — The "registered but not cached" fix + enhancements (2026-08-23)

### User report
Episode appeared in the Video Caching settings list after playback, but `cached_bytes` stayed ~0 — "the episode itself was not cached at all". New requests: tap-to-play from the list (same server/quality/resolution, resume from where left), background loading of the rest while playing, comprehensive logging + logcat filters.

### Root causes (3 — all fixed)
1. **Redirect-on-unknown-length (the killer).** When `contentLength` was unknown — extension localhost proxies commonly answer `200` with no `Content-Length` — `serve()` redirected MPV straight to the upstream URL. Playback worked (the fail-open did its job) but **zero bytes were cached**. The separate `Range: bytes=0-0` probe made this the DEFAULT path for many sources: it either failed outright, or got a 200-without-length and left the total unknown.
2. **HLS segment bypass.** For `.m3u8` URLs only the tiny PLAYLIST went through the proxy — MPV's HLS demuxer then fetched the actual segments (absolute CDN URLs inside the playlist) directly. The entry registered with a few KB "cached" and the video never touched the cache.
3. *(Contributing)* the probe consumed an upstream request before every first serve — some proxy tokens are single-use / rate-limited.

### Fixes
- **Learn-mode serving** (replaces the probe): when the total is unknown, the client's Range header is mirrored upstream VERBATIM; the total/range-support/Content-Type are learned from the actual serving response (206 Content-Range / 200 Content-Length). If even then no length exists: **chunked passthrough that still tees**; total learned on EOF. Redirects are now reserved for genuine pre-body internal errors (always logged with the reason).
- **HLS playlist rewriting**: the proxy rewrites master-playlist variant URIs → `/p/<key>/<i>` (so MPV still selects quality itself — "exact same quality" preserved) and media-playlist segment URIs + `EXT-X-MAP` init → `/s/<key>/<i|init>`. Segments cache as individual files under `<key>.seg/` named `seg_<i>_<urlHash8>.ts` (playlist-drift safe; stale files for a changed URL at the same index are replaced). `EXT-X-BYTERANGE` playlists bypass caching (logged — URL-identity doesn't hold); live playlists (no `EXT-X-ENDLIST`) play but don't background-fill. `#EXT-X-KEY` URIs are left untouched (MPV fetches keys itself — they're small + usually stable).

### New features
- **Background fill** ("while it is playing in the background, everything else of it will start to load"): a per-entry fill job (on the playbackCacheScope) fetches remaining gaps (progressive: 8 MB blocks, skipping the player's ±32 MB read frontier to avoid duplicating the player's own in-flight fetch) or missing segments (HLS: in order, VOD only) until the entry is complete. Bounded retries (3 × 5 s backoff), cancels on delete/evict/disable. Segment stats are recounted from disk after every fetch (self-heals serve/fill races).
- **Tap-to-play**: schema +4 columns (`segment_total`, `segments_cached`, `subtitle_tracks`, `audio_tracks` — ALTER-guarded for existing installs); WatchScreen passes the current video's external track lists through `playbackUrlFor`; the settings screen rows are clickable → MainActivity builds a full `WatchKey` from the entry (upstream URL + headers + tracks; episode list/registry intentionally empty — episode switching from a cache-origin launch opens Details instead) → WP-B3 resume-from-watch-progress applies. Same server/quality/resolution is guaranteed by the cache identity itself.

### CR-C review round (compile probe vs real jars — EXIT 0) caught pre-push
- **Critical**: `response.use { return ... }` in learn-mode closed the upstream body before NanoHTTPD's worker read it (dead stream on every learn-mode serve) — restructured to manual close on non-streaming exits, TeeInputStream owns the body on streaming paths.
- **High**: `segmentsCached += 1` race between proxy workers + fill → recount-from-disk instead.
- Variant-playlist relative-URI base fixed to the VARIANT url; `body!!` removed; TOCTOU on `contentLength` removed.

### Logging (logcat filter)
Tag `Anikuta:Core:PlaybackCache`, short key prefix on every line. Stages: `play` (URL wrap decision), `serve` (request + cached state), `learn` (total discovery), `parts` (disk/gap plan), `gap`/`tee` (fetch + cached progress, 4 MB-throttled), `flush`/`complete`, `hls` (playlist parse/rewrite), `seg` (per-segment cache hit/miss), `fill` (background progress), `evict`/`delete`, `fail-open` (redirect reasons). Android Studio filter:
```
tag:Anikuta:Core:PlaybackCache
```
Wider (player-side too):
```
tag:Anikuta:Core:PlaybackCache | tag:Anikuta:Feature:Watch message~:(?i)(cache|proxy|loadfile|MPV LOAD|resum|FILE_LOADED)
```
