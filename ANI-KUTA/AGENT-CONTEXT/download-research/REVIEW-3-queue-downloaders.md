# REVIEW 3 — Queue Management + State Machine + Downloaders + Dynamic Progress

**Task ID:** DL-REVIEW-3
**Agent:** senior-review-agent
**Scope:** `02-queue-management.md` (561 lines) · `03-state-machine.md` (296 lines) · `05-downloaders.md` (800 lines)
**Cross-referenced against:**
- OLD project source: `DownloadQueue.kt` (315) · `DownloadStatus.kt` (42) · `HttpDownloader.kt` (538) · `HlsDownloader.kt` (333) · `DynamicProgressTracker.kt` (123) · `advanced/AdvancedHttpDownloader.kt` (401) · `advanced/DownloadResumeManager.kt` (117) · `TempDownloadCache.kt` (93) · `DownloadPreferences.kt`
- NEW project stub: `APP/ani-kuta/core/download/.../DownloadManager.kt` (163) · `DownloadState.kt` (31)
- Other docs: `16-quality-of-life.md` §1 (RETRYING + RetryPolicy — interacts), `11-db-schema.md` §3 (the `resetDownloadingToQueued` SQL)

**Verdict: APPROVED WITH CHANGES — BLOCKED on 5 CRITICAL issues that must be fixed before Phase D.2/D.3 implementation.**

The architecture is sound in principle (SQLDelight-backed queue, Mutex serialization, modular Downloader interface, 3-engine routing). BUT 5 CRITICAL issues — including **the unbounded-recursion C1 from Review 2 STILL NOT FIXED** — make the docs unimplementable as-written. The user's "90%→100% jump" complaint is **not actually solved** by the NEW `DynamicProgressTracker` design (the 95% cap is a cosmetic tweak; the jump still happens, just later).

---

## Per-checklist findings

### 1. Queue concurrency (Mutex + Semaphore) — CONCERN

The NEW design (02 §13.1, §13.2, §13.4):
- `mutex: Mutex` serializes all `_tasks` mutations.
- `permits: Semaphore` (1..5, default 1 — verified `DownloadPreferences.kt:57` `KEY_CONCURRENT, 1`) gates concurrent downloads.
- `tryStartNext` acquires the mutex, computes `slotsAvailable = currentLimit - activeCount`, launches `launchDownload` for the first N QUEUED tasks.

**No deadlock risk** — `tryStartNext`'s `launchDownload` is `scope.launch {…}` (fire-and-forget), so the mutex is released before the launched coroutine tries to re-acquire it inside its own `permits.withPermit { mutex.withLock {…} }` block. The `finally { tryStartNext() }` in `launchDownload` runs outside any held mutex. ✅

**But:**
- `refreshConcurrency` rebuilds the Semaphore in-place (`permits = Semaphore(newLimit)`). In-flight tasks holding permits from the OLD semaphore keep them; the OLD semaphore is GC'd once all its permits are released. If the user shrinks the limit (5→1) while 5 downloads are running, all 5 continue — the new limit only takes effect for FUTURE starts. The doc acknowledges this (§4 caveat) but the QoL `onNetworkChanged` pause-all-DOWNLOADING path doesn't help here. Acceptable for MVP. ⚠️
- The mutex is held during `launchDownload(task)` invocation — but `launchDownload` is fire-and-forget (just `scope.launch {…}` + `jobs[task.id] = job`), so the critical section is microseconds. ✅
- Default concurrency = 1. Verified `DownloadPreferences.kt:57` `KEY_CONCURRENT, 1`. ✅
- Max = 5. UI clamp verified (Review 2 already covered this). ✅

### 2. Start-next logic — FAIL (concurrency + race)

The OLD project's `tryStartNext` (line 180 of `DownloadQueue.kt`) is **synchronous**: it picks the first QUEUED task + calls `launchDownload` directly. The NEW project's `tryStartNext` (§13.2) wraps everything in `scope.launch { mutex.withLock {…} }` — **async**.

**Race condition (I9 below)**: `enqueue` returns `task.id` to the caller, but the task is NOT YET in `_tasks.value` (the launch hasn't run yet). If the UI immediately queries `tasks` (e.g. `episodeDownloadStates`), the task is missing. The OLD project's synchronous `tryStartNext` doesn't have this race. **CONCERN**.

**Reactive vs polling**: the start-next is **event-driven** (called from `enqueue`/`pause`/`resume`/`cancel`/`retry`/`refreshConcurrency`/`onNetworkChanged`/`finally`), NOT polling. There's no `collect` on `_tasks` that auto-starts. This is correct (polling would be wasteful). ✅

**The `finally { tryStartNext() }` chain**: when a permit frees up, `tryStartNext` is called once. The NEW version starts MULTIPLE tasks per call (`toStart = queuedTasks.take(slotsAvailable)`), so the chain is shorter. ✅ improvement.

**But the per-tick progress callback fires `scope.launch { mutex.withLock {…} }` on every byte tick (§13.3)** — see I3 below. This is a severe performance + correctness flaw that essentially negates the Mutex design.

### 3. State machine — FAIL (RETRYING not integrated; resetDownloadingToQueued doesn't reset RETRYING)

- States in 03-state-machine.md: QUEUED, DOWNLOADING, PAUSED, COMPLETED, ERROR, CANCELLED. **No RETRYING.**
- States in 16-quality-of-life.md §1.3: QUEUED, DOWNLOADING, PAUSED, COMPLETED, ERROR, **RETRYING** (NEW).
- These two docs **disagree** on the state set. 03-state-machine.md was not updated when 16-quality-of-life.md introduced RETRYING. **CRITICAL cross-doc inconsistency (I1).**

- The §3 transition table doesn't include RETRYING transitions (DOWNLOADING→RETRYING, RETRYING→DOWNLOADING, RETRYING→ERROR, RETRYING→PAUSED, RETRYING→QUEUED-via-restart). **Undefined behaviour for pause/cancel/retry on a RETRYING task.**
- `resetDownloadingToQueued` in 11-db-schema.md §3 (verified lines 248-251) only does `WHERE state = 'DOWNLOADING'`. It does **NOT** reset `state = 'RETRYING'`. So 16-quality-of-life.md §1.3's claim "on restart, the queue's `resetDownloadingToQueued` also resets RETRYING → QUEUED" is **FALSE**. A task that crashed mid-retry stays RETRYING forever on restart. **CRITICAL (I2).**
- 02-queue-management.md §9 transition table doesn't include the RETRYING transitions either.
- The OLD project's `pause` (line 110-113) checks `status != DOWNLOADING && status != QUEUED` → silently no-ops on RETRYING. The NEW design inherits this — pausing a RETRYING task does nothing. **CONCERN (M6).**

### 4. HTTP downloader — FAIL (C1 from Review 2 NOT FIXED)

The §11.3 catch block (05-downloaders.md line 655-670) still reads:

```kotlin
} catch (e: IOException) {
    if (url.startsWith("http://localhost") && resolveContext != null && reResolver != null) {
        Logger.w(TAG) { "IOException on localhost URL — attempting re-resolve: ${e.message}" }
        val fresh = reResolver.reResolve(resolveContext)
        if (fresh != null) {
            store.updateResolveContext(taskId, fresh.url, resolveContext)
            return downloadNormal(fresh.url, fresh.headers, tempFile, taskId, resolveContext, onProgress)
        }
    }
    throw DownloadException("Video download failed: ${e.message ?: e.javaClass.simpleName}", e)
}
```

**The recursive call `return downloadNormal(fresh.url, …)` has NO `reResolveAttempts` counter.** This is **exactly** the unbounded-recursion C1 flagged in Review 2. The doc was not updated to address it.

If the fresh proxy URL is also killed (e.g. the user keeps playing new episodes, churning the proxy), the recursive call's catch block fires AGAIN, calling `reResolver.reResolve` AGAIN, recursing AGAIN. → `StackOverflowError` → the queue's catch block sets status=ERROR with `"StackOverflowError"` (unhelpful to the user). **WORSE than the original proxy-churn bug** because the original at least left a usable error message.

**Required fix**: add a `reResolveAttempts: Int = 0` parameter to `downloadNormal`, increment on each recursive call, fail with `DownloadException("Proxy URL died after $N re-resolve attempts")` when it exceeds 2.

### 5. HLS downloader — FAIL (estimatedTotal never refined; per-segment retry corrupts output)

#### 5a. The estimatedTotal is computed once + never updated

§11.4 line 713-719:
```kotlin
var estimatedTotal = -1L
if (segments.isNotEmpty()) {
    val firstSegmentSize = probeSegmentSize(segments.first(), headers)
    if (firstSegmentSize > 0) {
        estimatedTotal = firstSegmentSize * segments.size
    }
}
```

Then line 733: `onProgress(tempFile.length(), estimatedTotal)` — **the same `estimatedTotal` for the entire download**.

The doc's claim ("estimated total converges to the real total as more segments download" — line 771) is **FALSE**. The code never refines `estimatedTotal` based on actual downloaded bytes per segment. For variable-bitrate HLS (very common — ad segments are tiny, action scenes are large), the estimate can be off by 2-5x. If the real total is 2x the estimate, the bar hits 95% (the cap) at 50% actual download, then **jumps from 95% to 100% on completion** — **exactly the user's complaint**.

**Required fix**: track `actualBytesPerSegment: MutableList<Long>`; after each segment, recompute `estimatedTotal = (sumOf(actualBytesPerSegment) / actualBytesPerSegment.size) * segments.size` OR use the running average of segment sizes.

#### 5b. Per-segment retry corrupts output (C4)

§11.4 line 740-757: `downloadSegmentWithRetry` calls `downloadSegment(segUrl, headers, out)` which writes the response body directly to the `out: OutputStream`. If the segment partially downloads (some bytes written to `out`), then the connection drops, the retry writes the NEW (re-fetched) bytes **APPENDED** to the partial bytes from the failed attempt.

Result: corrupt `.ts` file with duplicated/partial segments. The downstream `verifyVideoMagicBytes` may not catch it (MPEG-TS sync bytes are still present, just at wrong positions; the file plays but glitches at the corruption point).

**Required fix**: in `downloadSegment`, capture the file position before the call (`val pos = out.channel.position()` for `FileOutputStream`); on failure, truncate back to `pos` before retrying. OR download to a `ByteArrayOutputStream` first, write to `out` only on success.

#### 5c. `probeSegmentSize` uses HEAD (I11)

Many anti-scraping CDNs (the same ones the PNG-stripping logic handles — megaplay.buzz, kotocdn.site) reject HEAD (405) or return wrong Content-Length. If `probeSegmentSize` returns -1, `estimatedTotal` stays -1 → falls back to "10MB ahead" strategy → same jump-per-segment as OLD project. The "byte-count-based progress for HLS" feature is brittle for exactly the CDNs where it matters most.

#### 5d. HLS still passes -1 in many cases

If the master playlist has no variants (the doc's "pickFirstVariant" returns null → throws), or if the probe fails, `estimatedTotal = -1`. The DynamicProgressTracker's `computeUnknownTotal` uses the "10MB ahead" strategy. For a 100-segment HLS stream at 5MB/segment (500MB total), the bar would cap at 95% after ~475MB downloaded (50MB ahead / 500MB ≈ 90% of the way). That's tolerable. But for a 10-segment stream at 50MB/segment (also 500MB), the bar caps at 95% after ~45MB downloaded (50MB ahead / 95MB ≈ 47% of the way → progress = 0.47 * 0.95 * 95 = 42%). The bar sits at ~42% for most of the download, then jumps to 95%, then to 100%. **The "10MB ahead" strategy is fundamentally wrong for HLS** — it was designed for direct HTTP downloads where `Content-Length: -1` is rare.

### 6. Advanced downloader + DownloadResumeManager — CONCERN (resume-on-pause broken by HttpDownloader's finally)

The OLD project's `HttpDownloader.download` has `finally { tempCache.cleanupTask(task.id) }` (line 161-165 — verified). `TempDownloadCache.cleanupTask` calls `dir.deleteRecursively()` on the entire task directory (verified `TempDownloadCache.kt:63-73`).

The Advanced downloader's `catch (CancellationException)` block (line 193-203) saves resume metadata. But then HttpDownloader's `finally` block DELETES that metadata (along with the chunk files). So **resume-after-pause DOES NOT WORK** in the OLD project. The doc's claim ("Resume capability: if the download is interrupted… the resume metadata + chunk files persist" — §7 line 49-53) is **FALSE for the pause case** (it's true only for app-crash-mid-chunk-write where the process is killed before the finally runs).

The NEW project inherits this design (05 §11.3 step "finally" + 04-storage-paths.md temp cache). The conflict is not addressed. **CRITICAL (I6).**

**Required fix**: the HttpDownloader's finally must NOT delete the task dir on CancellationException if the downloader was the Advanced method (so resume works on next attempt). OR: the queue's `pause` should call a method that preserves resume metadata, and the cleanup should happen only on `cancel`/completion/error (not pause).

#### Other Advanced downloader concerns
- Default `advancedMaxRetries` = 25 (UI clamps to 0..10). Already flagged in Review 2. The NEW doc §11.5 says "Same as the OLD project's `AdvancedHttpDownloader`" — so the bug is inherited.
- `RandomAccessFile.seek(chunk.downloaded)` positions at the resume point, but doesn't truncate the file if it's LONGER than `chunk.downloaded` (e.g. a previous attempt wrote extra garbage). The `loadResume` validation checks `actualSize < chunk.downloaded` (resets to actualSize) but NOT `actualSize > chunk.downloaded` (leaves extra bytes that won't be overwritten if the new attempt writes fewer bytes — but Range requests always write exactly `end - resumeFrom` bytes, so this is OK in practice). ⚠️ minor.
- Peak temp usage = 2x file size (chunks + concatenated output). Not addressed for low-storage devices. Already flagged in OLD doc §7 "Honest notes".
- Concatenation is sequential (no parallel I/O). Acceptable for MVP.

### 7. DynamicProgressTracker — FAIL (moving average not wired; 95% cap doesn't actually smooth the final jump)

#### 7a. The `recentRatios` parameter is NOT threaded through by the queue (C2)

The NEW `DynamicProgressTracker.compute` signature (§11.2 line 496-502):
```kotlin
fun compute(
    downloaded: Long,
    reportedTotal: Long,
    previousTotal: Long,
    previousEstimate: Long,
    recentRatios: List<Float>,    // ← NEW parameter for the moving average
): ProgressUpdate
```

But the queue's `launchDownload` (02 §13.3 line 427-435):
```kotlin
var prevTotal = 0L
var prevEstimate = 0L
val completed = downloader.download(task) { downloaded, total ->
    val update = DynamicProgressTracker.compute(
        downloaded = downloaded,
        reportedTotal = total,
        previousTotal = prevTotal,
        previousEstimate = prevEstimate,
        // ← recentRatios NOT PASSED — won't compile!
    )
    prevTotal = update.displayTotalBytes
    prevEstimate = update.updatedEstimate
    // ← recentRatios list NOT MAINTAINED
    ...
}
```

The queue does NOT maintain a `recentRatios` list. Either:
- The code won't compile (Kotlin requires all parameters), OR
- The implementation silently drops the moving average (uses an empty list internally).

Either way, **the moving-average smoothing feature is non-functional**. The doc's claim "Moving average smoothing (window of 5 ticks). Smoothes out network jitter so the bar doesn't stutter" (§11.2 line 468) is **FALSE**.

**Required fix**: the queue's `launchDownload` must maintain a `val recentRatios = ArrayDeque<Float>(5)` per-task; on each tick, compute the current ratio, add it to the deque (evicting the oldest if size > 5), pass it to `compute`.

#### 7b. The 95% cap doesn't actually smooth the final jump (I4)

The user's complaint: "While it is downloading it will show that the progress is 90% or 100. Suddenly it went to 100%."

The OLD `DynamicProgressTracker` caps at 90%, then snaps to 100% on completion. **The user's complaint is about the 90→100 jump, NOT about the cap value.**

The NEW design bumps the cap to 95%. The justification (§11.2 line 470): "The 5% gap is reserved for the post-download validation + publish-to-SAF step (so the user sees the bar move from 95% to 100% during the publish, not jump)."

**But the NEW HttpDownloader.download (§11.3) does NOT call `onProgress` during the validation/publish steps.** Look at the code (line 597-613): `validateDownloadedFile`, `verifyVideoMagicBytes`, `downloadSubtitlesToCache`, `downloadCoverToCache`, `writeDataJsonToCache`, `storage.publishToUserFolder` — none of these call `onProgress`. The downloader returns the completed task with `progress = 100` set directly (`task.copy(status = COMPLETED, progress = 100, …)` line 606-613).

So the bar still jumps from 95% (last onProgress tick during byte-stream download) to 100% (when the queue mutates the task to COMPLETED). **The jump is just from 95→100 instead of 90→100. The complaint is NOT fixed.**

**Required fix**: between the last byte-stream `onProgress` and the COMPLETED mutation, the downloader should call `onProgress(downloaded, total, 96)`, `onProgress(downloaded, total, 97)`, `onProgress(downloaded, total, 98)`, `onProgress(downloaded, total, 99)` at meaningful intermediate points (after validation, after subtitles, after metadata, after publish). This requires either:
- A separate `onPhaseProgress(phase: DownloadPhase, percent: Int)` callback, OR
- The downloader returns the completed task with `progress = 99`, and the queue bumps to 100 after the `onTaskCompleted` callback.

#### 7c. `DynamicProgressTracker.complete()` is dead code (I5)

The NEW design adds `fun complete(): ProgressUpdate = ProgressUpdate(100, 0L, 0L)` (§11.2 line 537). **Never called anywhere** — the queue just `mutateTask(task.id) { it.copy(progress = 100, status = COMPLETED, …) }` directly. Dead code.

#### 7d. The "sanity check" if-branch is a no-op (I12)

§11.2 line 513-517:
```kotlin
if (reportedTotal in 1 until MIN_VALID_TOTAL_BYTES && downloaded > reportedTotal) {
    return computeUnknownTotal(downloaded, previousTotal, previousEstimate, recentRatios)
}
return computeUnknownTotal(downloaded, previousTotal, previousEstimate, recentRatios)
```

Both branches return the SAME thing. The if-check is dead code. The OLD project's version (DynamicProgressTracker.kt line 65-70) computed `effectiveReportedTotal = -1L` in the if-branch and `effectiveReportedTotal = reportedTotal` in the else-branch — then used `effectiveReportedTotal` in the subsequent logic. The NEW refactor lost the distinction.

#### 7e. Pause/resume resets prevTotal + prevEstimate (I14)

When a download is paused, the `launchDownload` coroutine is cancelled (its closure variables `prevTotal` + `prevEstimate` are GC'd). On resume, a new `launchDownload` starts with `prevTotal = 0L` + `prevEstimate = 0L`. The first onProgress tick after resume will compute progress based on the fresh state — if the download restarts from scratch (the OLD project's behavior, per `DownloadManager.kt:21-22` KDoc + §116 of 02-queue-management.md), the bar goes from e.g. 70% (when paused) to 0% (when resumed). **Backward jump — exactly what the user complained about.**

For the Advanced method (which does support per-chunk resume), the bar would jump from 70% (paused) to e.g. 60% (resumed, prevTotal reset to 0, contentLength is the full size, downloaded is the partial). Slightly better but still a backward jump.

**Required fix**: persist `prevTotal` + `prevEstimate` + `recentRatios` in the DB row (or in `resume.json` for Advanced), restore them on resume.

#### 7f. The "10MB ahead" strategy is wrong for HLS (see §5d above)

For HLS with `estimatedTotal = -1`, the "10MB ahead" strategy was designed for direct HTTP downloads (where -1 is rare). For HLS (where -1 is the default if the HEAD probe fails), the bar sits low for most of the download then jumps to 95%. The OLD project had the same bug; the NEW design doesn't fix it for HLS.

### 8. Error handling — FAIL (RetryPolicy uses HttpException that doesn't exist; re-resolve happens before retry loop)

#### 8a. RetryPolicy.forException uses `e is HttpException` (C5)

16-quality-of-life.md §1.2 line 73-75:
```kotlin
e is HttpException && e.code in 500..599 -> Policy(true, 3, { attempt -> (1000L * (1 shl (attempt - 1))) })
e is HttpException && e.code == 429 -> Policy(true, 3, { attempt -> 5000L * (1 shl (attempt - 1)) })
e is HttpException && e.code in 400..499 -> Policy(false, 0, { 0 })
```

**There is no `HttpException` class in either the OLD or NEW project.** Verified by grepping the OLD `HttpDownloader.kt` — HTTP errors are wrapped as `DownloadException("HTTP ${response.code} for video URL")` (line 239, 301, 354, etc.). The `code` is NOT preserved as a structured field — it's just a string in the message.

So:
- All 3 `HttpException` branches are **dead code**.
- HTTP 5xx / 429 errors fall through to `else -> Policy(false, 0, { 0 })` → **never retried**.
- HTTP 4xx errors fall through too — but those SHOULDN'T retry, so the outcome is accidentally correct.
- The only retries that actually fire are: `DownloadException` wrapping `IOException` (line 71) + the "Connection refused" string-match (line 68-69, flagged as fragile in Review 2 I6).

**The auto-retry feature is largely non-functional for HTTP errors.** Combined with C1 (the re-resolve recursion happens BEFORE the IOException reaches the retry loop), the proxy-churn case is handled by the unbounded recursion (potentially StackOverflow) before the retry loop ever sees it.

**Required fix**: introduce an `HttpException(val code: Int, message: String) : DownloadException(message)` class, throw it in `downloadNormal` (line 108 of §11.3) + `HlsDownloader.fetchText` + `downloadSegment` instead of the generic `DownloadException("HTTP $code…")`. Then the RetryPolicy will match.

#### 8b. The retry loop holds the permit during backoff (I8)

16-quality-of-life.md §1.2:
```kotlin
while (true) {
    attempt++
    try {
        permits.withPermit { /* download */ }   // ← permit held during download
        return@launch
    } catch (e: Exception) {
        ...
        delay(policy.backoffMs(attempt))   // ← but delay happens AFTER withPermit returns
    }
}
```

Wait — re-reading: the `delay` is OUTSIDE the `withPermit` block. So the permit IS released before the backoff. ✅ (My initial concern was wrong — the structure is correct.)

But there's a subtler issue: the retry loop re-enters `permits.withPermit { … }` on each attempt. So a retry competes for a fresh permit against other queued tasks. If concurrency=1 and 3 tasks are queued, a retrying task A may release the permit during backoff, task B grabs it, runs to completion, releases, then task A's retry has to re-acquire. This is actually correct behaviour (fair), but it means retries can be delayed significantly. Acceptable. ✅

#### 8c. `setRetryingStatus` + `setErrorStatus` undefined (I7)

The retry loop calls `setRetryingStatus(task.id, attempt, policy.maxAttempts, …)` + `setErrorStatus(task.id, …)`. Neither is defined in 02-queue-management.md or 16-quality-of-life.md. If they internally acquire `mutex.withLock`, they could deadlock if called from inside a mutex context (they're called from the retry loop's catch block, which is NOT inside `mutex.withLock` — OK). But the implementation is undefined. **CONCERN.**

### 9. Pause/resume/cancel — CONCERN

#### 9a. Cancel cleans up temp files — PARTIAL

`cancel(taskId)` (02 §5 line 123-135 + §13.3 not shown but presumably same): calls `jobs.remove(taskId)?.cancel()` (cancels the Job, triggering the launchDownload's finally block) + removes the task from `_tasks.value`.

The launchDownload's finally block (§13.3 not shown for the cancel path, but §11.3 step "finally" of HttpDownloader.download does `tempCache.cleanupTask(task.id)` — verified OLD HttpDownloader.kt line 161-165). So the temp files ARE cleaned up on cancel. ✅

**But**: if the task was QUEUED (never started) and is cancelled, no temp files exist. `jobs.remove(taskId)?.cancel()` returns null (no Job in the map), so no cancel happens. The task is just removed from the list. ✅

#### 9b. Pause does NOT survive app restart — PARTIAL

The OLD project's `DownloadStore` persists the entire task list as JSON to SharedPreferences (verified `DownloadStore.kt`). The NEW project moves to SQLDelight (per 11-db-schema.md). Either way, the PAUSED status IS persisted.

On restart:
- `store.getAll()` returns the persisted list (including PAUSED tasks). ✅
- `store.resetDownloadingToQueued()` resets stale DOWNLOADING tasks. PAUSED tasks are NOT reset (correctly — they should stay PAUSED). ✅
- `tryStartNext()` is NOT called automatically on construction (per OLD project's bug — flagged in §7 of 03-state-machine.md). The NEW design's `init` block (§13.1) does NOT call `tryStartNext()` either — it just sets up the Flow collectors for `concurrentDownloads().changes()` + `wifiOnly().changes()`. So PAUSED tasks stay PAUSED until the user manually resumes them. ✅ (correct behaviour).

**But**: the partial temp file is GONE on restart (either the OLD project's `tempCache.cleanupStale()` on startup, or just Android clearing the cache directory under memory pressure). So a PAUSED-then-restarted download loses its partial progress (except for the Advanced method, which uses resume.json — but that's also in the cache dir, also gone on restart). **Resume-from-where-it-left-off does NOT survive app restart.** The doc acknowledges this for the OLD project (§7 of 03-state-machine.md + §116 of 02-queue-management.md) but the NEW design inherits the limitation without flagging it.

#### 9c. Pause during RETRYING — UNDEFINED

The OLD project's `pause` checks `status != DOWNLOADING && status != QUEUED` → silently no-ops. The NEW RETRYING state is NOT in the allowed-pause set. So pausing a RETRYING task does nothing. The retry loop continues, eventually re-attempting. **The user can't cancel a retry in progress.** Bad UX. (M6)

---

## CRITICAL issues (5)

### C1 (NOT FIXED from Review 2) — HttpDownloader.downloadNormal re-resolve catch block still has UNBOUNDED RECURSION
**File**: `05-downloaders.md` §11.3 line 655-670.
**Symptom**: if the fresh proxy URL also dies, the recursive `downloadNormal(fresh.url, …)` call's catch block fires AGAIN, recursing indefinitely → `StackOverflowError` → worse than the original proxy-churn bug.
**Required fix**: add `reResolveAttempts: Int = 0` parameter to `downloadNormal`; increment on each recursive call; fail with `DownloadException("Proxy URL died after $N re-resolve attempts")` when `reResolveAttempts >= 2`. Update the call site in `downloadVideoToCache` (§11.3 line 637) to pass `reResolveAttempts = 0` initially.

### C2 — The `recentRatios` parameter of the NEW `DynamicProgressTracker.compute` is NOT threaded through by the queue
**File**: `02-queue-management.md` §13.3 (line 427-435) vs `05-downloaders.md` §11.2 (line 496-502).
**Symptom**: the queue's `launchDownload` does NOT maintain a `recentRatios` list + does NOT pass it to `compute`. Either the code won't compile (Kotlin requires all parameters), OR the moving average is silently dropped. Either way, the "moving average smoothing" feature is non-functional. The user's "stuttering bar" complaint (the cause of the 90→100 jump perception) is NOT addressed.
**Required fix**: the queue must maintain `val recentRatios = ArrayDeque<Float>(5)` per-task in closure vars; on each tick, compute the current ratio, add it to the deque (evict oldest if size > 5), pass it to `compute`. Update §13.3 of 02-queue-management.md to show this.

### C3 — HLS `estimatedTotal` is computed ONCE + never refined
**File**: `05-downloaders.md` §11.4 line 713-733.
**Symptom**: `estimatedTotal = firstSegmentSize * segments.size` is set once before the segment loop + never updated. For variable-bitrate HLS (ads, action scenes), the estimate can be off by 2-5x. If the real total is 2x the estimate, the bar hits the 95% cap at 50% actual download, then jumps 95→100 on completion — **exactly the user's complaint**.
**Required fix**: after each segment, recompute `estimatedTotal = runningAverageSegmentSize * segments.size` where `runningAverageSegmentSize = totalDownloadedBytes / segmentsDownloadedSoFar`. Update §11.4 line 731-734 to show this. The doc's claim "estimated total converges to the real total as more segments download" (line 771) must become TRUE.

### C4 — HLS per-segment retry writes to the SAME FileOutputStream → corrupt output on partial-then-retry
**File**: `05-downloaders.md` §11.4 line 740-757.
**Symptom**: `downloadSegmentWithRetry` calls `downloadSegment(segUrl, headers, out)` which streams the response body to `out`. If the segment partially downloads (some bytes written) then fails, the retry writes the new bytes APPENDED to the partial bytes. Result: corrupt `.ts` file with duplicated/partial segments. `verifyVideoMagicBytes` won't catch it (sync bytes still present, just at wrong positions; file plays but glitches).
**Required fix**: in `downloadSegmentWithRetry`, capture `val posBefore = (out as FileOutputStream).channel.position()` before each attempt; on failure, truncate back: `(out as FileOutputStream).channel.truncate(posBefore)`. OR: download each segment to a `ByteArrayOutputStream` first, write to `out` only on success.

### C5 — `RetryPolicy.forException` uses `e is HttpException` — but no `HttpException` class exists
**File**: `16-quality-of-life.md` §1.2 line 73-75.
**Symptom**: all 3 `HttpException` branches are dead code. HTTP 5xx / 429 errors fall through to `else -> Policy(false, 0, { 0 })` → never retried. The "auto error handling" headline QoL feature is non-functional for the most common retryable error class (server errors).
**Required fix**: introduce `class HttpException(val code: Int, message: String) : DownloadException(message)`; throw it in `downloadNormal` (§11.3 line 108), `HlsDownloader.fetchText` (§11.4 line 692), `downloadSegment` (§11.4 line 740), and `probeServer` (Advanced — §7 line 99). Update RetryPolicy to match on `HttpException`. Also: extract the HTTP code from the DownloadException message via regex as a transitional fix (fragile, but better than nothing).

---

## IMPORTANT issues (15)

### I1 — RETRYING state is NOT in `03-state-machine.md` diagram or transition table
**File**: `03-state-machine.md` §§2-3.
The 16-quality-of-life.md §1.3 introduces `RETRYING` as a new state. But 03-state-machine.md (the dedicated state-machine doc) was never updated — its diagram (§2) + transition table (§3) show only the original 6 states. Cross-doc inconsistency. Implementers reading 03-state-machine.md will not know RETRYING exists.

### I2 — `resetDownloadingToQueued` does NOT reset RETRYING → QUEUED on restart (despite QoL doc claiming it does)
**File**: `11-db-schema.md` §3 line 248-251 + `16-quality-of-life.md` §1.3 line 101.
The SQL `UPDATE … WHERE state = 'DOWNLOADING'` only resets DOWNLOADING. 16-quality-of-life.md §1.3 says "on restart, the queue's `resetDownloadingToQueued` also resets RETRYING → QUEUED" — **FALSE**. A task that crashed mid-retry stays RETRYING forever on restart. Required fix: change the SQL to `WHERE state IN ('DOWNLOADING', 'RETRYING')` OR add a separate `resetRetryingToQueued` query.

### I3 — Per-tick `scope.launch { mutex.withLock {…} }` is a severe performance + correctness flaw
**File**: `02-queue-management.md` §13.3 line 436-449.
The progress callback fires `scope.launch { mutex.withLock { mutateTask(…); store.updateProgress(…) } }` on EVERY byte tick. With an 8KB buffer + 100MB file = ~12,500 ticks. Each launch allocates a coroutine + queues for the mutex. UI sees the OLDEST queued update, not the LATEST (FIFO mutex queue). For 5 concurrent downloads, this is 60,000+ pending coroutines. Severe jank + stale progress.
**Required fix**: either (a) update `_tasks.value` inline (no launch, no mutex — `MutableStateFlow.value = …` is atomic), and write to the DB via a Channel<ProgressUpdate> consumed by a single coroutine, OR (b) throttle the onProgress callback itself to once per 100ms inside the downloader.

### I4 — The 95% cap doesn't actually smooth the final jump (the user's complaint is NOT fixed)
**File**: `05-downloaders.md` §11.2 line 470 + §11.3 line 597-613.
The justification for bumping 90→95 was "the user sees the bar move from 95% to 100% during the publish, not jump". But `HttpDownloader.download` does NOT call `onProgress` during validation/subtitle/metadata/publish steps. The bar still jumps from 95→100. The user's complaint ("Suddenly it went to 100%") is NOT addressed — the jump is just 95→100 instead of 90→100.
**Required fix**: the downloader must call `onProgress(downloaded, total, 96/97/98/99)` at meaningful intermediate points (after validation, after subtitles, after metadata, after publish). OR: return `progress = 99` from the downloader, let the queue bump to 100 only after `onTaskCompleted` returns.

### I5 — `DynamicProgressTracker.complete()` is dead code
**File**: `05-downloaders.md` §11.2 line 537.
The function exists but is never called. Either remove it, or wire it up in the queue's COMPLETED mutation path.

### I6 — `HttpDownloader.download`'s `finally { tempCache.cleanupTask(task.id) }` deletes Advanced downloader's resume metadata
**File**: OLD `HttpDownloader.kt` line 161-165 + `TempDownloadCache.kt` line 63-73 (verified); NEW §11.3 inherits.
On ANY exit (success, failure, cancellation), the entire task directory is deleted — including `resume.json` + `chunk_*.part` files saved by the Advanced downloader's CancellationException handler. So **resume-after-pause does NOT work** for the Advanced method. The doc's claim ("Resume capability… the resume metadata + chunk files persist" — §7 line 49-53) is FALSE for the pause case.
**Required fix**: the HttpDownloader's finally must distinguish CancellationException (preserve resume metadata for Advanced) from completion/error (delete everything). OR: the queue's `pause` should call a method that preserves resume metadata, and the cleanup should happen only on `cancel`/completion/error.

### I7 — `setRetryingStatus` + `setErrorStatus` are called but never defined
**File**: `16-quality-of-life.md` §1.2 line 49, 54.
Neither function is shown in 02-queue-management.md or 16-quality-of-life.md. If they internally acquire `mutex.withLock`, they could deadlock (depending on caller context — they're called from the retry loop's catch block, which is NOT inside `mutex.withLock`, so probably OK). But the implementation is undefined. Specify them.

### I8 — NEW `tryStartNext` is async (`scope.launch { mutex.withLock {…} }`) — race with `enqueue` returning before the task is in the queue
**File**: `02-queue-management.md` §13.2 line 380-399.
The OLD project's `tryStartNext` is synchronous. The NEW version wraps in `scope.launch { mutex.withLock {…} }`. So `enqueue` returns `task.id` to the caller BEFORE the launched coroutine has run. If the UI queries `tasks` immediately (e.g. `episodeDownloadStates` collect), the task may not yet be in `_tasks.value`. Race condition.
**Required fix**: either (a) make `enqueue` itself suspend + acquire the mutex inline (not via `scope.launch`), OR (b) add the task to `_tasks.value` SYNCHRONOUSLY in `enqueue` (before calling `tryStartNext`), then let `tryStartNext` be async. The current §13.1 `enqueue` (not shown but presumably same as OLD §5 line 86-108) already does (b) — so this is OK in practice. **Downgrade from CRITICAL to IMPORTANT.** But the comment "mutex.withLock" in §13.2 implies the launched coroutine also mutates `_tasks.value` — verify it doesn't double-add.

### I9 — `onNetworkChanged` fires N async `pause` calls in a tight loop — race
**File**: `02-queue-management.md` §13.4 line 519-527.
`_tasks.value.filter { DOWNLOADING }.forEach { pause(it.id) }` — each `pause` calls `tryStartNext` which does `scope.launch { mutex.withLock {…} }`. So N async launches fire in a tight loop. Between the pause loop + the tryStartNext calls, the queue state is inconsistent (some tasks PAUSED, others still DOWNLOADING). For N=5 concurrent downloads, this is 5 async mutex acquisitions serialised behind each other. Probably OK functionally (the mutex serialises them), but the user sees flickering state. **CONCERN.**

### I10 — Pause/resume resets prevTotal + prevEstimate → bar jumps backward on resume (I14 above, restated)
**File**: `02-queue-management.md` §13.3 line 424-435.
On pause, the `launchDownload` coroutine is cancelled → `prevTotal` + `prevEstimate` closure vars are GC'd. On resume, a new `launchDownload` starts with `prevTotal = 0L` + `prevEstimate = 0L`. The bar jumps from e.g. 70% (paused) to 0% (resumed, fresh state). **Backward jump — exactly the user's complaint.**
**Required fix**: persist `prevTotal` + `prevEstimate` + `recentRatios` in the DB row (or `resume.json` for Advanced), restore on resume.

### I11 — `probeSegmentSize` uses HEAD — many anti-scraping CDNs reject HEAD or return wrong Content-Length
**File**: `05-downloaders.md` §11.4 line 759-765.
The CDNs that need the PNG-stripping logic (megaplay.buzz, kotocdn.site) are exactly the ones that reject HEAD (405) or lie about Content-Length. If `probeSegmentSize` returns -1, `estimatedTotal` stays -1 → falls back to "10MB ahead" strategy → bar sits low for most of the download, then jumps to 95% → user's complaint stands for these CDNs.
**Required fix**: use a 1-byte Range GET (like the Advanced downloader's `probeServer`) instead of HEAD. OR: download the first segment, get its size from the response, multiply by segment count, then re-download the first segment for the actual concat (wasteful but reliable).

### I12 — The "sanity check" if-branch in NEW DynamicProgressTracker is a no-op
**File**: `05-downloaders.md` §11.2 line 513-517.
Both branches of the `if (reportedTotal in 1 until MIN_VALID_TOTAL_BYTES && downloaded > reportedTotal)` return the SAME `computeUnknownTotal(…)` call. The if-check is dead code. The OLD project's version (DynamicProgressTracker.kt line 65-70) computed `effectiveReportedTotal = -1L` in the if-branch and `= reportedTotal` in the else-branch — distinct values used in subsequent logic. The NEW refactor lost the distinction.
**Required fix**: restore the OLD logic — compute `effectiveReportedTotal` then use it in the `if (effectiveReportedTotal >= MIN_VALID_TOTAL_BYTES)` check below.

### I13 — The 95% cap "reserves 5% for publish" but no onProgress call happens during publish (I4 above, restated)
See I4. The justification for 95 (vs 90) is unpublished — the doc claims the bar moves 95→100 during publish, but the code doesn't do this.

### I14 — Pause/resume resets progress tracker state → backward jump (I10 above, restated)
See I10.

### I15 — `mutateTask` doesn't acquire the mutex itself — API is fragile
**File**: `02-queue-management.md` §13.3 (called inside `mutex.withLock` blocks).
`mutateTask` does a read-modify-write on `_tasks.value`. If a caller forgets to wrap in `mutex.withLock`, the race is back. The API is fragile — easy to misuse.
**Required fix**: make `mutateTask` a `suspend fun` that acquires the mutex internally. OR: rename to `mutateTaskLocked` + document that the caller MUST hold the mutex.

---

## MINOR issues (10)

- **M1**: NEW `DownloadState` sealed interface (03-state-machine.md §9) has `Failed` not `ERROR`. The recommendation is to adopt the OLD `DownloadStatus` enum (with `ERROR` + `CANCELLED` + `isTerminal`/`isActive` helpers). Naming inconsistency to resolve.
- **M2**: Retry loop `attempt` starts at 0, incremented to 1 on first iteration. `setRetryingStatus(task.id, attempt, …)` shows "Retrying (1/3)" but the user has only seen 1 attempt so far — the "1/3" is the count of attempts INCLUDING the upcoming retry, which is confusing. Should be `attempt-1` for the retry count, or change the UI text to "Attempt 2/3…".
- **M3**: `currentConcurrentLimit()` clamps to 1..5. UI doc (07-settings-preferences.md) — verify slider range matches. (Review 4 will cover.)
- **M4**: `mutateTask` returns Unit — caller can't tell if the mutation was applied (e.g. task was already removed). Inherited from OLD. Acceptable for MVP.
- **M5**: `enqueue` on an ERROR task calls `resumeInternal` silently. What about enqueue on a RETRYING task? Undefined — probably returns the existing ID without resetting. Could be confusing if the user "re-taps download" on a retrying task.
- **M6**: `pause` (OLD line 110-113) accepts only DOWNLOADING + QUEUED. NEW RETRYING state isn't in the allowed set → pause on RETRYING silently no-ops. Should accept RETRYING too (cancel the retry loop's delay + transition to PAUSED). Bad UX otherwise.
- **M7**: `cancel` removes the task entirely. No undo. If the user accidentally cancels, the download is gone. Minor UX concern; consider a 5-second "Undo" snackbar.
- **M8**: The OLD `DownloadQueue`'s `tryStartNext` (line 180) is synchronous. The NEW version is async (`scope.launch`). This changes the semantics — `enqueue` returns before the queue is updated. See I8.
- **M9**: `autoClearScheduled` set is unbounded in theory, but in practice it shrinks (`autoClearScheduled.remove(taskId)` inside the launched coroutine). OK.
- **M10**: `cancel()` doesn't call `tempCache.cleanupTask(taskId)` directly — relies on the launchDownload's finally block. If the app is killed mid-download, the finally doesn't run, temp files are left behind. The OLD project's `TempDownloadCache.cleanupStale()` (called on startup) handles this. The NEW docs don't mention preserving `cleanupStale()`. **Verify in Phase D.0.**

---

## Overall verdict

**APPROVED WITH CHANGES — BLOCKED on 5 CRITICAL issues.**

The architecture is sound: SQLDelight-backed queue, Mutex serialization, modular `Downloader` interface, 3-engine routing (HTTP/HLS/Advanced), per-task coroutine + Semaphore permits, reactive Flow-based UI. The state machine concept (terminal/active helpers, transition table) is correct. The QoL features (auto-retry, auto-resume on network change, auto-pause on metered) are well-conceived.

**BUT** the implementation details have 5 CRITICAL issues that block Phase D.2/D.3:

1. **C1 (NOT FIXED from Review 2)**: the HttpDownloader.downloadNormal re-resolve catch block STILL has unbounded recursion. Review 2 explicitly demanded a `reResolveAttempts` counter — the doc was not updated. Must be fixed before any downloader code is written.
2. **C2**: the moving-average `recentRatios` parameter is documented but NOT threaded through by the queue's `launchDownload`. Either won't compile or silently drops the feature. The "smooth progress" headline is non-functional.
3. **C3**: HLS `estimatedTotal` is computed once + never refined. For variable-bitrate HLS, the bar still jumps 95→100 — exactly the user's complaint. The doc's "converges to the real total" claim is false.
4. **C4**: HLS per-segment retry writes to the SAME FileOutputStream. A partial-then-retry produces corrupt output. `verifyVideoMagicBytes` won't catch it.
5. **C5**: `RetryPolicy.forException` uses `e is HttpException` — no such class exists. All 3 HTTP branches are dead code. HTTP 5xx / 429 errors never retry. The auto-retry headline QoL feature is non-functional for the most common retryable error class.

Plus the user's core complaint — "the progress bar suddenly went to 100%" — is **NOT actually fixed** by the NEW design. The 95% cap is a cosmetic tweak (90→95); the bar still jumps from 95→100 because no `onProgress` call happens during validation/subtitle/metadata/publish (I4). The fix requires the downloader to explicitly emit 96/97/98/99% ticks during the post-byte-stream phases. This must be specified in the doc before implementation.

**Next action for the plan author**:
1. Fix C1 — add `reResolveAttempts` counter to `downloadNormal` in 05-downloaders.md §11.3.
2. Fix C2 — update 02-queue-management.md §13.3 to maintain + pass `recentRatios`.
3. Fix C3 — update 05-downloaders.md §11.4 to refine `estimatedTotal` after each segment.
4. Fix C4 — update 05-downloaders.md §11.4 `downloadSegmentWithRetry` to truncate the output stream on failure before retrying.
5. Fix C5 — introduce `HttpException` class in 16-quality-of-life.md §1.2 + update `downloadNormal`/`fetchText`/`downloadSegment` to throw it.
6. Address I1 (add RETRYING to 03-state-machine.md diagram + transition table) + I2 (update `resetDownloadingToQueued` SQL to also reset RETRYING).
7. Address I3 (replace per-tick `scope.launch` with inline `_tasks.value =` + Channel-based DB writes).
8. Address I4 (add intermediate `onProgress` calls during validation/publish, OR have the downloader return progress=99 + the queue bump to 100).
9. Address I6 (HttpDownloader's finally must preserve resume metadata on CancellationException for the Advanced method).
10. Address I10 (persist `prevTotal`/`prevEstimate`/`recentRatios` across pause/resume).

Then proceed to Phase D.2 (download engine) + Phase D.3 (queue).

**Next review round (DL-REVIEW-4) should focus on**: the foreground service + notifications (06) + the workflow/UI (01, 08, 09) — specifically how the foreground service survives backgrounding, how the notification's progress is throttled, + how the details-page download button state maps to the (now-RETRYING-inclusive) state machine.
