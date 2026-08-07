# 16 — Quality-of-Life Features (NEW — DL-PLAN-REWRITE)

> **Task ID:** DL-PLAN-REWRITE
> This is a NEW doc — the OLD project doesn't have a dedicated QoL doc. The user explicitly requested "auto error handling/retry (small features, huge impact)" + other small-but-impactful features.
> **Cross-references:** `02-queue-management.md` §13 (the queue's `onNetworkChanged` callback) · `05-downloaders.md` §11.4 (the HLS per-segment retry) · `13-implementation-plan.md` Phase D.7 (the implementation plan for these features).

The QoL features are scattered across the engine + the queue + the notification manager. This doc is the consolidated spec for all of them.

---

## 1. Auto error handling/retry (the headline QoL feature)

**Goal:** when a download fails (network blip, server 5xx, proxy-churn, etc.), the engine should automatically retry — without the user having to tap "Retry". The user sees a single "Retrying (attempt 2/3)..." state in the UI, not a hard ERROR.

### 1.1 The retry policy

| Error type | Retry? | Max attempts | Backoff |
|---|---|---|---|
| `IOException` (network blip, connection reset) | ✅ | 3 | Exponential: 1s, 2s, 4s |
| `DownloadException` wrapping an `IOException` | ✅ | 3 | Exponential: 1s, 2s, 4s |
| HTTP 5xx (server error) | ✅ | 3 | Exponential: 1s, 2s, 4s |
| HTTP 4xx (client error — 401, 403, 404) | ❌ | — | — (the URL is wrong, retrying won't help) |
| HTTP 429 (rate limit) | ✅ | 3 | Use `Retry-After` header if present, else 5s, 10s, 20s |
| `DownloadException("Encrypted HLS stream...")` | ❌ | — | — (the engine can't decrypt) |
| `DownloadException("Video download failed: Connection refused")` on localhost URL | ✅ via ReResolver | 2 (1 initial + 1 re-resolve) | Immediate (no backoff — the proxy is already dead) |
| `CancellationException` (pause/cancel) | ❌ | — | — (not an error, it's a user action) |
| Any other `Exception` | ❌ | — | — (unknown error — don't retry blindly) |

### 1.2 The implementation (in `DownloadQueue.launchDownload`)

> **REVIEW-5 M11:** `setRetryingStatus` + `setErrorStatus` are now defined in `02-queue-management.md`
> §13.3 (were undefined in the OLD draft).
>
> **REVIEW-5 M19:** this retry loop is the OUTER retry loop. The INNER re-resolve (proxy-churn
> fix in `05-downloaders.md` §11.3 / `10-player-integration.md` §14.1) is bounded at 1 attempt per
> outer attempt. **Cap composition: outer caps at 3 attempts × inner caps at 2 attempts = 6
> download attempts maximum before ERROR.** See REVIEW-5 §6.3.
>
> **REVIEW-5 M50:** the `CancellationException` branch is unreachable here (the catch above it
> re-throws) — removed from `RetryPolicy.forException` for clarity.
>
> **REVIEW-5 M49:** `HttpException` is defined LOCALLY in `:core:download` (see §1.2.1 below) —
> does NOT depend on `:core:source-api`. `HttpDownloader.downloadNormal` throws it for HTTP errors.

```kotlin
private fun launchDownload(task: DownloadTask) {
    val job = scope.launch {
        var attempt = 0
        val maxAttempts = 3  // for retryable errors
        while (true) {
            attempt++
            try {
                permits.withPermit {
                    // ... (the existing download logic — see 02-queue-management.md §13.3)
                }
                return@launch  // success — exit the retry loop
            } catch (e: CancellationException) {
                throw e  // pause/cancel — don't retry
            } catch (e: Exception) {
                val policy = RetryPolicy.forException(e)
                if (!policy.shouldRetry || attempt >= policy.maxAttempts) {
                    // Final failure — set status = ERROR.
                    setErrorStatus(task.id, e.message ?: e.javaClass.simpleName)
                    onTaskError?.invoke(...)
                    return@launch
                }
                // Retryable — set status = RETRYING with the attempt number.
                setRetryingStatus(task.id, attempt, policy.maxAttempts, e.message ?: e.javaClass.simpleName)
                delay(policy.backoffMs(attempt))
                // Loop back to the top — re-attempt the download.
            }
        }
    }
    jobs[task.id] = job
}

object RetryPolicy {
    data class Policy(val shouldRetry: Boolean, val maxAttempts: Int, val backoffMs: (Int) -> Long)

    fun forException(e: Exception): Policy = when {
        // REVIEW-5 M48 (R2-I6): use exception TYPE matching, not string matching on the message.
        // The OLD draft's `e.message?.contains("Connection refused")` was fragile (different
        // locales, different JVM versions, different CDN error messages).
        e is ConnectException || e is SocketException ->
            Policy(true, 2, { 0 })  // proxy-churn — handled by ReResolver inside downloadNormal
        e is HttpException && e.code in 500..599 ->
            Policy(true, 3, { attempt -> (1000L * (1 shl (attempt - 1))) })
        e is HttpException && e.code == 429 ->
            Policy(true, 3, { attempt -> 5000L * (1 shl (attempt - 1)) })  // 5s, 10s, 20s
        e is HttpException && e.code in 400..499 ->
            Policy(false, 0, { 0 })  // client error — don't retry
        e is DownloadException && e.message?.contains("Encrypted HLS") == true ->
            Policy(false, 0, { 0 })  // can't decrypt — don't retry
        e is DownloadException && e.cause is IOException ->
            Policy(true, 3, { attempt -> (1000L * (1 shl (attempt - 1))) })
        e is IOException ->
            Policy(true, 3, { attempt -> (1000L * (1 shl (attempt - 1))) })
        else -> Policy(false, 0, { 0 })  // unknown — don't retry blindly
    }
}
```

#### 1.2.1 The `HttpException` class (REVIEW-5 M49)

> **REVIEW-5 M49 (R3-C5 / R4-C5):** `HttpException` IS defined in `:core:source-api`
> (`OkHttpExtensions.kt:183`), but `:core:download/build.gradle.kts` does NOT depend on
> `:core:source-api`. Rather than add a heavyweight dep, we define a LOCAL `HttpException`
> in `:core:download`. `HttpDownloader.downloadNormal` (per `05-downloaders.md` §11.3) throws
> it for HTTP errors (instead of the OLD draft's generic `DownloadException("HTTP $code …")`
> which had no `code` field + made RetryPolicy's HTTP branches dead code).

```kotlin
// In :core:download/.../HttpException.kt (NEW)
package com.confused.anikuta.core.download

/**
 * Thrown by [HttpDownloader.downloadNormal] (and HlsDownloader.fetchText / downloadSegment)
 * when the server returns a non-2xx HTTP status. Carries the [code] so [RetryPolicy.forException]
 * can match on it (5xx → retry, 429 → retry with Retry-After, 4xx → don't retry).
 *
 * REVIEW-5 M49: defined LOCALLY in :core:download (does NOT depend on :core:source-api where
 * a same-named class lives — we deliberately don't reuse that one to keep :core:download's
 * dependency graph minimal).
 */
class HttpException(
    val code: Int,
    override val message: String,
    cause: Throwable? = null,
) : DownloadException(message, cause)
```

Throw sites (per `05-downloaders.md` §11.3 + §11.4):
- `HttpDownloader.downloadNormal` — `if (!response.isSuccessful) throw HttpException(response.code, "HTTP ${response.code} for video URL")`.
- `HlsDownloader.fetchText` — same pattern.
- `HlsDownloader.downloadSegment` — same pattern.
- `AdvancedHttpDownloader.probeServer` — same pattern.

### 1.3 The "RETRYING" state in the UI

> **REVIEW-5 M12:** the canonical `DownloadStatus` is `enum class` (per `03-state-machine.md` §1).
> The OLD draft here proposed `sealed interface DownloadStatus` with `data class RETRYING(...)` —
> that's REVISED. The enum constant `RETRYING` can't carry per-instance data, so the retry
> metadata (`attempt`, `maxAttempts`, `lastError`) lives on `DownloadTask` instead.

Add a new state to the download state machine (per `03-state-machine.md`):

```kotlin
enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    RETRYING,   // NEW — REVIEW-5 M9
    PAUSED,
    COMPLETED,
    ERROR,
    CANCELLED;

    val isTerminal: Boolean get() = this == COMPLETED || this == CANCELLED
    val isActive: Boolean get() = this == DOWNLOADING || this == RETRYING
}

// The retry metadata lives on DownloadTask (the enum constant can't carry per-instance data):
@Serializable
data class DownloadTask(
    // ... existing fields ...
    val retryAttempt: Int = 0,
    val retryMaxAttempts: Int = 3,
    val lastError: String? = null,
)
```

The `EpisodeDownloadControl` UI (per `15-ui-and-bug-analysis.md` §A.9 + `09-details-page-download-ui.md` §1)
renders RETRYING as:
- The same spinner as `Queued` (gray, `onSurfaceVariant`).
- The pill text: `"Retrying (2/3)…"` (instead of `"Queued"`) — driven by `task.retryAttempt` + `task.retryMaxAttempts`.
- The same Cancel button as `Queued` (the user can cancel a retry).

The state persists in the DB (`download_queue.state = 'RETRYING'`) — survives app restart (though the retry loop itself doesn't; on restart, the queue's `resetDownloadingToQueued` resets BOTH DOWNLOADING AND RETRYING → QUEUED — REVIEW-5 M6).

### 1.4 Why this is "huge impact"

- The user doesn't have to babysit the queue. A flaky CDN that drops every 5th segment no longer fails the whole download — the engine retries silently + the user sees a successful download.
- The proxy-churn bug (per `10-player-integration.md` §14) is now invisible to the user — the re-resolve happens inside the retry loop, the user just sees "Retrying (2/3)..." briefly.
- The retry policy is table-driven (`RetryPolicy.forException`) — easy to tune per error type without changing the queue's logic.

---

## 2. Auto-resume on network change

**Goal:** when the network drops mid-download (user walks out of Wi-Fi range, subway, etc.) + comes back, the queue automatically resumes the paused downloads.

### 2.1 The `NetworkCallback` registration

```kotlin
// In DownloadManager init (or a dedicated NetworkMonitor class):
private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
private val networkCallback = object : NetworkCallback() {
    override fun onAvailable(network: Network) {
        Logger.i(TAG) { "Network available — resuming paused downloads" }
        scope.launch { queue.onNetworkChanged(isWifi = isWifi(), hasInternet = true) }
    }
    override fun onLost(network: Network) {
        Logger.i(TAG) { "Network lost — pausing downloading tasks" }
        scope.launch { queue.onNetworkChanged(isWifi = false, hasInternet = false) }
    }
    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
        val isWifi = capabilities.hasTransport(TRANSPORT_WIFI)
        val hasInternet = capabilities.hasCapability(NET_CAPABILITY_INTERNET)
        scope.launch { queue.onNetworkChanged(isWifi, hasInternet) }
    }
}

init {
    connectivityManager.registerNetworkCallback(
        NetworkRequest.Builder()
            .addCapability(NET_CAPABILITY_INTERNET)
            .build(),
        networkCallback,
    )
}
```

### 2.2 The `DownloadQueue.onNetworkChanged` callback

> **REVIEW-5 M42 (R4-C8 + R4-M5):** this is the CANONICAL definition. The OLD draft here was
> divergent from `02-queue-management.md` §13.3 + deadlocked — `mutex.withLock { pause(it.id) }`
> where `pause` ALSO acquires the mutex = non-reentrant Mutex deadlock. The fix: extract
> `pauseInternal(taskId)` that ASSUMES the mutex is held (no `mutex.withLock` inside).
> Both `pause` (public) + `onNetworkChanged` (mutex-holding caller) use the right variant.
> See `02-queue-management.md` §13.4 for the full implementation.

```kotlin
// CANONICAL definition lives in 02-queue-management.md §13.4 — duplicated here for context.
fun onNetworkChanged(isWifi: Boolean, hasInternet: Boolean) {
    scope.launch {
        mutex.withLock {
            if (!hasInternet) {
                // Network lost — pause all DOWNLOADING + RETRYING tasks (preserve their progress).
                _tasks.value
                    .filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.RETRYING }
                    .forEach { pauseInternal(it.id) }  // M42 — assumes mutex held; no re-acquire.
            } else if (preferences.wifiOnly().get() && !isWifi) {
                // On metered network + Wi-Fi-only is on — pause all DOWNLOADING + RETRYING tasks.
                _tasks.value
                    .filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.RETRYING }
                    .forEach { pauseInternal(it.id) }
            } else {
                // Network is back + matches the constraint — try to start queued tasks.
                // (Paused tasks stay paused — the user must explicitly resume them.
                // QUEUED tasks (including auto-paused ones from a prior network loss)
                // are auto-started.)
                // `tryStartNext` itself acquires the mutex; we're holding it, so release first.
            }
        }
        // Outside the lock — tryStartNext re-acquires the mutex itself.
        if (hasInternet && (!preferences.wifiOnly().get() || isWifi)) {
            tryStartNext()
        }
    }
}
```

### 2.3 Why paused tasks stay paused (not auto-resumed)

When the network drops, DOWNLOADING tasks are paused (preserving their progress). When the network comes back, the user might have walked away — auto-resuming might burn data they didn't intend to use. So:
- **DOWNLOADING → PAUSED** on network loss (preserves progress).
- **PAUSED stays PAUSED** on network return (user must explicitly resume).
- **QUEUED tasks auto-start** on network return (they hadn't started yet, so no progress to preserve).

This matches the OLD project's behavior (the OLD project's `connectivityCheck` only runs on `tryStartNext` — paused tasks stay paused).

---

## 3. Auto-pause on metered network

**Goal:** when the user is on a metered network (mobile data, metered Wi-Fi) AND `pref_dl_wifi_only` is ON, the queue pauses all in-flight downloads. When the user reconnects to unmetered Wi-Fi, the queue auto-resumes QUEUED tasks.

### 3.1 The implementation

This is the same `onNetworkChanged` callback as §2 above — the `preferences.wifiOnly().get()` check determines whether to pause.

### 3.2 The notification

When the queue auto-pauses due to metered network, post a one-shot notification:
- Title: "Downloads paused"
- Text: "Wi-Fi only is on — connect to Wi-Fi to resume."
- Channel: `anikuta_downloads_progress` (silent).
- Auto-cancel.

This tells the user WHY their downloads stopped (so they don't think the app is broken).

---

## 4. Download verification (file size + magic bytes)

**Goal:** before publishing a downloaded file to the user's SAF folder, verify it's actually a valid video (not an error page, a redirect, or a PNG masquerading as a video).

### 4.1 The size check

```kotlin
private fun validateDownloadedFile(url: String, tempFile: File, downloadedBytes: Long) {
    if (!tempFile.exists() || tempFile.length() == 0L) {
        throw DownloadException("Downloaded file is empty — the source returned no data.")
    }
    if (tempFile.length() < MIN_VALID_VIDEO_BYTES) {  // 500 KB
        // Log the first 200 bytes as text for debugging.
        val first200 = tempFile.inputStream().use { it.readBytes().take(200).joinToString("") { "%02x".format(it) } }
        Logger.w(TAG) { "Downloaded file is only ${tempFile.length()} bytes — first 200: $first200" }
        throw DownloadException(
            "Downloaded file is only ${tempFile.length()} bytes — the server returned an error page or redirect instead of the video. " +
            "Try a different server or quality. (URL: ${url.take(80)}...)"
        )
    }
}
```

500 KB minimum — a real video episode is at least hundreds of KB. Anything smaller is an error page / playlist / corrupt download.

### 4.2 The magic-byte check (non-fatal)

```kotlin
private fun verifyVideoMagicBytes(tempFile: File) {
    try {
        val first16 = tempFile.inputStream().use { it.readBytes().take(16) }
        // HTML: 3C 21 (<!) or 3C 68 (<h)
        if (first16.size >= 2 && first16[0] == 0x3C.toByte() && (first16[1] == 0x21.toByte() || first16[1] == 0x68.toByte())) {
            Logger.w(TAG) { "Downloaded file starts with HTML — likely an error page (non-fatal, size check should have caught it)" }
        }
        // PNG: 89 50 4E 47 (only reject if file < 10 MB AND not a valid MPEG-TS — some HLS streams have PNG posters as the first segment)
        // JPEG: FF D8 FF (same logic)
        // MP4: ftyp at offset 4
        // MKV/WebM: 1A 45 DF A3
        // FLV: FLV
        // AVI: RIFF
        // MPEG-TS: 0x47 at positions 0, 188, 376, 564, 752 (at least 2 sync bytes)
        // If none match — log a warning but DON'T reject (some video formats have non-standard headers).
    } catch (e: Exception) {
        Logger.w(TAG, e) { "Magic byte check failed (non-fatal)" }
    }
}
```

The magic-byte check is non-fatal — failures are logged, not thrown. The size check (§4.1) is the authoritative validation.

### 4.3 The post-publish verification

After `publishToUserFolder`, the `downloaded_episode` row is inserted with a `verified_at` timestamp. A periodic background job (e.g. once a week) re-verifies:
- The file still exists at the recorded URI.
- The file size matches the recorded `file_size` (within 1% — some SAF providers may report slightly different sizes due to block alignment).

If verification fails, the row is marked "missing" (per `04-storage-paths.md` §7.3) — the user is prompted to re-download.

---

## 5. Orphan-file cleanup

**Goal:** keep the user's SAF folder + the temp cache clean of orphaned files (from crashes, failed downloads, manual file deletions).

### 5.1 Temp cache cleanup (on startup)

```kotlin
class TempDownloadCache(context: Context) {
    private val rootDir = File(context.cacheDir, "anikuta_downloads").also { it.mkdirs() }

    fun cleanupStale() {
        rootDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                // Any temp dir present at startup is from a crashed/interrupted download.
                // Safe to delete — the user's SAF folder has nothing partial.
                dir.deleteRecursively()
            }
        }
    }
}
```

Called once at app startup (from `DownloadModule.kt`'s `single { TempDownloadCache(get()).also { it.cleanupStale() } }`).

### 5.2 SAF folder reconciliation (the scan-on-startup)

The `DownloadScanner.scanAndReconcile()` (per `04-storage-paths.md` §7) handles the SAF side:
- Walks `video/`, `images/`, `text/`.
- Reads each `data.json`.
- For each episode entry, verifies the video file exists + is non-empty.
- Marks missing episodes as "missing" in the DB (via `markEpisodeMissing`).

### 5.3 Half-written SAF file cleanup

If `publishToUserFolder` crashes mid-write (e.g. the app is killed while copying the video to SAF), the user's folder may have a partial `Jujutsu Kaisen - E00001.mp4` file. The next download attempt for the same episode handles this:
- `publishToUserFolder` calls `contentDir.findFile(videoName)?.delete()` before creating the new file (per `04-storage-paths.md` §6.3 step 3).
- So the partial file is overwritten on the next attempt.

### 5.4 Empty content folder cleanup

After deleting an episode, if the content folder is now empty (no episodes left in `data.json` AND no other files besides `data.json` itself), the whole folder is deleted (per `04-storage-paths.md` §8.3). Keeps the user's folder clean.

### 5.5 The `.anikuta/` hidden folder

The `.anikuta/` folder under the user's SAF root contains the scan-state cache (`scan_state.json`). It's created lazily on first scan. Deleting it is safe — the next scan re-creates it. (It's a cache, not durable data.)

---

## 6. Auto-clear completed entries after 10s (the OLD project's behavior)

**Goal:** keep the live queue tidy — completed downloads auto-clear from the in-memory task list (and the DB queue row) 10 seconds after completion. The file stays on disk; the `downloaded_episode` row stays in the DB (it's the durable record).

### 6.1 The implementation

Per `02-queue-management.md` §13.3:

```kotlin
private fun scheduleAutoClear(taskId: Long) {
    if (taskId in autoClearScheduled) return  // guard against the leak per 15-ui-and-bug-analysis.md §A.11 fix #12
    autoClearScheduled.add(taskId)
    scope.launch {
        delay(10_000)
        mutex.withLock {
            autoClearScheduled.remove(taskId)
            _tasks.value = _tasks.value.filterNot { it.id == taskId }
            store.delete(taskId)  // deletes from download_queue (NOT downloaded_episode)
        }
    }
}
```

### 6.2 Why 10s

- Long enough for the user to see the "Download complete" notification + the green ✓ in the UI.
- Short enough that the queue doesn't pile up with completed entries.

The user can change this in settings (future enhancement) — for now, 10s is the default.

---

## 7. Notification tap deep-link

**Goal:** tapping a download notification deep-links to the Downloads screen (not just the launcher activity).

### 7.1 The implementation

Per `06-notifications-foreground-service.md` §13.5:

```kotlin
private fun openDownloadsScreenIntent(): PendingIntent {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setClassName(context.packageName, "com.confused.anikuta.MainActivity")
        data = Uri.parse("anikuta://downloads")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    return PendingIntent.getActivity(context, 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
```

The host's `MainActivity` checks for `anikuta://downloads` in `onNewIntent` + pushes the `DownloadsKey` onto the Nav3 stack.

---

## 8. Notification action buttons

**Goal:** the summary notification has Pause all / Cancel all action buttons (so the user can control the queue without opening the app).

Per `06-notifications-foreground-service.md` §13.6 — the actions are wired to `DownloadService` Intents (`ACTION_PAUSE_ALL`, `ACTION_CANCEL_ALL`) → the service calls `manager.pauseAll()` / `manager.cancelAll()`.

---

## 9. Other small-but-impactful features

### 9.1 The "smoking gun" log line

Per `15-ui-and-bug-analysis.md` §B.7 rule 5 — the download engine logs the URL it's fetching from:

```kotlin
Logger.i(TAG) { "Downloading: ${task.content.title} EP ${task.episode.episodeNumber} — URL: ${task.request.videoUrl}" }
if (task.request.videoUrl.startsWith("http://localhost")) {
    Logger.w(TAG) { "Download depends on extension proxy server — may fail if the proxy is killed by another resolve call." }
}
```

When debugging the proxy-churn bug, this log line is the smoking gun — if the URL is `http://localhost:PORT/...`, the bug is proxy churn; if it's `https://cdn.example.com/...`, the bug is something else.

### 9.2 The auto-clear guard (the OLD project's leak fix)

Per `15-ui-and-bug-analysis.md` §A.11 fix #12 — the 10s auto-clear is guarded by a `Set<Long>` of already-scheduled task IDs. Without this guard, the OLD project launches a new coroutine per emission (a leak — the same task gets scheduled for auto-clear multiple times).

### 9.3 The queue's reactive concurrency (the OLD project's bug fix)

Per `02-queue-management.md` §13.1 — the queue's `init` block has a Flow collector that calls `refreshConcurrency()` when the `concurrentDownloads` pref changes. Without this, the OLD project's new limit only takes effect after restart.

### 9.4 The queue's Mutex (the OLD project's threading fix)

Per `02-queue-management.md` §13.2 — all queue mutations are wrapped in `mutex.withLock { ... }`. Without this, the OLD project's "best-effort" threading is risky under high concurrency.

### 9.5 The `DOWNLOADING`-on-restart fix

Per `02-queue-management.md` §13.1 — on startup, `store.resetDownloadingToQueued()` resets any stale DOWNLOADING tasks to QUEUED. Without this, a crash mid-download leaves the task stuck in DOWNLOADING forever.

### 9.6 The `Episode NNN` floor bug fix (N/A — we use 5-digit padded `E00001.5`)

The OLD project's `Episode NNN` folder name uses `.toInt()` (floor) — special episodes (.5) collide with their integer counterparts. The NEW project uses `formatEpisodeNumber` (per `04-storage-paths.md` §4.2) which preserves the fractional suffix: `E00012.5`. No collision.

### 9.7 The `AnimatedContent` in `EpisodeDownloadControl` (optional polish)

Per `15-ui-and-bug-analysis.md` §A.11 fix #13 — the OLD project's KDoc promises `AnimatedContent` for smooth state transitions, but the code doesn't deliver. The NEW project should actually use it (optional polish).

### 9.8 The `group by mainId` (not title) fix

Per `15-ui-and-bug-analysis.md` §A.11 fix #11 — the OLD project's live-queue grouping by `anime.title` would conflate two different anime with the same title. The NEW project groups by `mainId` (the stable UUID).

### 9.9 The `formatBytes` shared util

Per `06-notifications-foreground-service.md` §13.11 — move the `formatBytes` helper to `:core:common` (it's duplicated in 3 places in the OLD project). The NEW project has ONE shared util.

---

## 10. Summary — the QoL feature list

| # | Feature | Where implemented | Why it's "huge impact" |
|---|---|---|---|
| 1 | Auto error handling/retry (3 attempts with backoff) | `DownloadQueue.launchDownload` + `RetryPolicy` | The user doesn't have to babysit the queue. Flaky CDNs + the proxy-churn bug become invisible. |
| 2 | Auto-resume on network change | `NetworkCallback` + `DownloadQueue.onNetworkChanged` | Subway Wi-Fi drops don't strand downloads. |
| 3 | Auto-pause on metered network | `NetworkCallback` + `onNetworkChanged` + `wifiOnly` pref | No surprise data charges. |
| 4 | Download verification (size + magic bytes) | `HttpDownloader.validateDownloadedFile` + `verifyVideoMagicBytes` | Error pages / corrupt files don't end up in the user's folder. |
| 5 | Orphan-file cleanup (temp cache + SAF reconciliation) | `TempDownloadCache.cleanupStale` + `DownloadScanner.scanAndReconcile` | Crashes don't leave junk in the user's folder. |
| 6 | Auto-clear completed after 10s | `DownloadQueue.scheduleAutoClear` | The live queue stays tidy. |
| 7 | Notification tap deep-link | `DownloadNotificationManager.openDownloadsScreenIntent` | Tapping a notification goes straight to the Downloads screen. |
| 8 | Notification action buttons (Pause all / Cancel all) | `DownloadService.ACTION_PAUSE_ALL` / `ACTION_CANCEL_ALL` | Control the queue without opening the app. |
| 9 | The "smoking gun" log line | `HttpDownloader.download` | Makes the proxy-churn bug diagnosable. |
| 10 | The auto-clear guard (`Set<Long>`) | `DownloadQueue.autoClearScheduled` | Fixes a coroutine leak. |
| 11 | Reactive concurrency | `DownloadQueue.init` Flow collector | Pref changes take effect immediately. |
| 12 | Mutex-protected queue mutations | `DownloadQueue.mutex` | Fixes a race condition. |
| 13 | `DOWNLOADING`-on-restart fix | `DownloadQueue.init` `resetDownloadingToQueued` | Crashes don't strand tasks. |
| 14 | 5-digit padded episode numbers (no floor bug) | `formatEpisodeNumber` | Special episodes (.5) don't collide. |
| 15 | `AnimatedContent` in `EpisodeDownloadControl` | (optional polish) | Smooth state transitions. |
| 16 | `group by mainId` (not title) | `DownloadViewModel` | Two same-title anime don't conflate. |
| 17 | Shared `formatBytes` util | `:core:common` `FormatUtils` | No code duplication. |

---

## 11. Cross-references

- `02-queue-management.md` §13 — the queue's `onNetworkChanged` callback + the `Mutex` + the reactive concurrency + the auto-clear guard.
- `04-storage-paths.md` §7 — the scan-on-startup reconciliation (orphan-file cleanup on the SAF side).
- `05-downloaders.md` §11.4 — the HLS per-segment retry (a specific case of the auto-retry feature).
- `06-notifications-foreground-service.md` §13.5 + §13.6 — the deep-link tap intent + the action buttons.
- `10-player-integration.md` §14 — the proxy-churn fix (the `ReResolver` integration with the auto-retry loop).
- `13-implementation-plan.md` Phase D.7 — the implementation plan for these features.
- `15-ui-and-bug-analysis.md` §A.11 — the "replicate exactly" checklist (the fixes that are part of the QoL work).
