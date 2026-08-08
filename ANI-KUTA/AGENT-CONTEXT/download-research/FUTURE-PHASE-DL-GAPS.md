# Future Phase — Download System Known Gaps & Implementation Plan

> **Purpose:** Consolidates the deferred download-system items into one place so
> a future phase can pick them up with full context. These are NOT bugs the user
> experiences today (downloads work for non-churning URLs; manual retry works) —
> they are known gaps to address together in a dedicated future phase.
>
> **Created:** analysis-and-doc-update session (D-151). Branch: `download-system-plan`.
> **Status:** All items DEFERRED per user. Not started.
> **Companion docs:** sandbox `ani-kuta-analysis/04-proxy-churn-explanation.md`
> (deep technical plan), `ani-kuta-analysis/02a-app-core-infra-analysis.md` §6.
> **Related decisions:** D-148 (download architecture), D-149 (proxy-churn gap),
> D-151 (this future-phase plan).

---

## Why these are grouped together

Items 1, 2, and 4 below all live in the same code path (`HttpDownloader`'s
catch block + `DownloadQueue.launchDownload`'s error handling) and compose with
each other (the proxy-churn re-resolve is an *inner* retry; the outer retry loop
*wraps* it). Doing them in one phase ensures they compose correctly and can be
tested together. Item 3 is a small cleanup that can ride along.

---

## Item 1 — Proxy-churn re-resolve: BUILT but NOT WIRED (D-149)

**Severity:** Functional gap (downloads of localhost-proxy URLs fail on churn).
**User impact:** If an extension (e.g. AniKotoS) spins up a local proxy per
`getHosterList()` call, a second resolve (playing another anime) kills the first
proxy → the in-flight download's next byte-read throws `Connection refused` →
download fails. Streaming is unaffected (seconds, not minutes).

### What's built
- `HttpDownloader` has a `reResolver: ReResolver? = null` constructor param (`HttpDownloader.kt:59`).
- The retry path (`HttpDownloader.kt:261-284`) calls `reResolver.reResolve(resolveContextJson)` to get a fresh URL, then re-downloads. Inner cap `MAX_RE_RESOLVE_ATTEMPTS = 1` (`HttpDownloader.kt:449`).
- The `:app` `ReResolver` class (`app/.../download/ReResolver.kt`) IS registered in Koin (`AnikutaApp.kt:154`) but is never injected into `HttpDownloader`.

### Why it's not wired
- `DownloadModule.kt:92` constructs `HttpDownloader` with `reResolver = null` (comment: "wired in D.2 via the :app module's downloadAppModule" — **no such module exists**; grep returns 0 matches).
- The two `ReResolver` interfaces are **signature-incompatible**:
  - `HttpDownloader.ReResolver` (fun interface, `:core:download`): `reResolve(resolveContextJson: String): ReResolvedVideo?`
  - `:app` `ReResolver` (class): `reResolve(context: ResolveContext, source: AnimeHttpSource, episode: SEpisode): ResolverVideo?`
  - An **adapter** is required, not just a Koin binding. (This split is intentional — `:core:download` shouldn't depend on `:core:source-api` / Aniyomi types for binary compat.)

### Wiring plan (when this phase starts)
1. **Create the adapter** (~50 lines, in `:app`):
   ```kotlin
   // app/src/main/java/com/confused/anikuta/download/ReResolverAdapter.kt
   class ReResolverAdapter(
       private val appReResolver: ReResolver,            // the :app class
       private val extensionManager: ExtensionManager,
   ) : HttpDownloader.ReResolver {
       override suspend fun reResolve(resolveContextJson: String): ReResolvedVideo? {
           val ctx = Json.decodeFromString<ResolveContext>(resolveContextJson)
           val source = extensionManager.getSource(ctx.sourceId) as? AnimeHttpSource ?: return null
           val episode = SEpisodeImpl(url = ctx.episodeUrl)  // only url is needed by reResolve
           val fresh = appReResolver.reResolve(ctx, source, episode) ?: return null
           return ReResolvedVideo(fresh.videoUrl, fresh.headers)  // map types
       }
   }
   ```
   (Exact field names to verify against `ResolveContext.kt` + `ResolverVideo.kt` at implementation time.)
2. **Register in Koin** (in `:app`'s existing `appModule` — NOT a new `downloadAppModule`, minimal change):
   ```kotlin
   single<HttpDownloader.ReResolver> { ReResolverAdapter(get(), get()) }
   ```
3. **Change `DownloadModule.kt:92`** from `reResolver = null` to `reResolver = getOrNull<HttpDownloader.ReResolver>()` (optional/lazy — keeps `:core:download` independent of `:app`, and the `null` guard in the catch block still handles the disabled case).
4. **Logging** (CORE_RULES §20): tag `Anikuta:Core:Download` (matches `DownloadLogger`).
5. **Device test**: trigger a localhost-URL download, open another anime from the same source (to churn the proxy), confirm the download recovers via re-resolve.

---

## Item 2 — Two bugs in the (currently dead) re-resolve code path

**Severity:** Latent — these are inside the dead code from Item 1 (the whole
block is unreachable while `reResolver = null`). They have **zero functional
effect today**, but must be fixed WHEN Item 1 is wired, or the re-resolve will
silently fail.

### Bug 2a — `127.0.0.1` not matched by the guard
- **Location:** `HttpDownloader.kt:261` — `if (url.startsWith("http://localhost") && ...)`.
- **Problem:** AniKotoS uses `http://127.0.0.1:<port>/...` (per `lessons-learned.md` D-092), NOT `http://localhost`. The guard only checks `localhost`, so a `127.0.0.1` URL bypasses the re-resolve path entirely and goes straight to the generic error.
- **Fix:** change the guard to `if ((url.startsWith("http://localhost") || url.startsWith("http://127.0.0.1")) && ...)`. Apply the same fix at line 285 (the second guard for the "exhausted attempts" message).

### Bug 2b — Fresh URL written to the wrong DB column
- **Location:** `HttpDownloader.kt:271` — `store.updateResult(taskId, fresh.url, emptyList())`.
- **Problem:** `updateResult` writes to the `video_uri` column, but the download's read path uses `video_url`. After a re-resolve, the fresh URL is stored in the wrong column → the next download attempt (or a resume) reads the STALE URL → fails again. A `DownloadStore.updateDownloadVideoUrl` query must be added (or `updateResult`'s implementation fixed to write `video_url`).
- **Fix:** verify which column `DownloadTask.videoUrl` maps to; add/fix the query so the fresh URL lands in the column the read path uses. Inspect `DownloadStore.kt` `updateResult` + the `download_queue` / `downloaded_episode` schema.

### Why NOT fix now
These are in dead code (Item 1 unwired). Fixing them now:
- Has zero functional effect (the code path doesn't run).
- Cannot be verified locally (CORE_RULES §8 — no local builds; verification is CI-only).
- Risks introducing a bug that only surfaces months later when Item 1 is wired (harder to trace).
Fixing them **in context** (alongside Item 1 wiring) means they're tested together in the same device-test pass.

---

## Item 3 — `DownloadVideoPickerSheet`: redundant, not a user-facing bug

**Severity:** Not a bug. Dead/redundant code. Documented here to correct an
earlier analysis claim + record the cleanup option.

### The situation (corrected)
An earlier analysis (sandbox discrepancy D006 item 11) flagged "`DownloadVideoPickerSheet`
built but not wired" as a gap. That was **technically true but misleadingly framed** —
the user's download experience is NOT broken:

- **The download button works** via the **ResolverSheet** (the same sheet used for watching). The user clicks download → the ResolverSheet appears → the user picks a video → `handleDownloadSpecificVideo()` enqueues the download. This path is fully wired (`MainActivity.kt:228`, `:254`, `:633`).
- **`DownloadVideoPickerSheet`** (`feature/download/.../DownloadVideoPickerSheet.kt`, 236 LOC) is a *separate, dedicated* sheet for the `EnqueueResult.ShowPicker` case — when the **auto-download engine** returns ASK (couldn't auto-pick a video). That case is logged-only today (`MainActivity.kt:595-601`, `// TODO: show the DownloadVideoPickerSheet`).

### Why it's rarely hit
The auto-download engine handles the common case (it picks based on user preferences: 360p/HSUB defaults per commit `f30b290`). `ShowPicker` (ASK fallback) only fires when the engine can't decide — rare in practice (the code comment says "99% of cases"). And the manual path (ResolverSheet → `handleDownloadSpecificVideo`) is the primary UX anyway.

### Options (decide in the future phase)
- **(A) Delete `DownloadVideoPickerSheet`** — it's dead code; the ResolverSheet path covers the UX. Simplest.
- **(B) Wire it for the `ShowPicker` case** — if the auto-download ASK fallback should show a picker (instead of just logging). More work; only matters if auto-download ASK becomes common.
- **Recommendation:** (A) delete, unless a future need for the ASK-fallback picker emerges. Either way, NOT urgent — downloads work.

---

## Item 4 — Outer automatic retry loop (not implemented)

**Severity:** Functional gap (transient errors go straight to ERROR; user must
manually tap retry). **User impact:** a download that fails due to a transient
network blip or 5xx goes to ERROR immediately. Manual retry exists
(`DefaultDownloadManager.retryDownload()` → `queue.retry(id)`, line 135) and
works, so the user CAN recover — but it's not automatic.

### What's built (the scaffolding)
- `DownloadStatus.RETRYING` enum value exists (`DownloadStatus.kt:36`) + `canRetry` (ERROR → retry).
- `DownloadQueue.setRetryingStatus()` private method exists (`DownloadQueue.kt:516`) — **NEVER CALLED** (dead code).
- `DownloadStore.setRetryingStatus()` query exists + `retry_attempt` / `retry_max_attempts` DB columns + `DEFAULT_RETRY_MAX = 3` (`DownloadStore.kt:435`).
- `RetryPolicy` class is **referenced in KDoc** (`HttpException.kt:8`, `HttpDownloader.kt:31`, `:212`, `:254`) but **does not exist**.

### What's missing (the implementation)
- On failure (`DownloadQueue.kt:497-504`), the catch block calls `setErrorStatus()` **immediately** — no retry. It should: check a `RetryPolicy` → if retryable + `attempt < DEFAULT_RETRY_MAX` → `setRetryingStatus()` → backoff delay → re-run `download()` → increment attempt → if exhausted, `setErrorStatus()`.

### Design decisions needed (NOT a quick fix — these need user input)
1. **Which exceptions are retryable?**
   - `HttpException` 5xx (server error) → yes.
   - `HttpException` 4xx (client error) → no (won't fix itself; e.g. 404).
   - `IOException` (network blip) → yes.
   - `DownloadException` (validation/empty file) → no (the source returned junk; retrying won't help).
   - Proxy-churn `DownloadException` → handled by Item 1's inner re-resolve, not the outer loop.
2. **Backoff strategy?** Immediate retry? Fixed delay (e.g. 5s)? Exponential (5s, 15s, 45s)? Exponential is best practice but adds complexity (a coroutine delay that's cancellable by pause/cancel).
3. **Notification UX during RETRYING?** Show "Retrying (2/3)..." in the foreground notification? Or keep the progress notification + a small "retrying" indicator?
4. **Interaction with pause/cancel during the backoff delay?** If the user pauses during a retry delay, the retry should be cancelled (not fire after resume).
5. **Interaction with Item 1 (proxy-churn inner retry)?** The outer loop wraps the inner re-resolve. A single outer attempt may trigger an inner re-resolve. The attempt counters must be independent (outer = 3, inner = 1 → max 3×1 = 3 effective re-downloads). Confirm this composes correctly.
6. **Max attempts:** `DEFAULT_RETRY_MAX = 3` is set — confirm this is the desired number.

### Implementation sketch (when this phase starts)
1. Create `RetryPolicy` class in `:core:download`:
   ```kotlin
   class RetryPolicy(private val maxAttempts: Int = 3) {
       fun shouldRetry(e: Throwable, currentAttempt: Int): Boolean {
           if (currentAttempt >= maxAttempts) return false
           return when (e) {
               is HttpException -> e.code in 500..599
               is IOException -> true
               is DownloadException -> false  // validation/empty — retrying won't help
               else -> false
           }
       }
   }
   ```
2. In `DownloadQueue.launchDownload`'s catch block (`:497`), before `setErrorStatus`:
   ```kotlin
   val attempt = task.retryAttempt + 1
   if (retryPolicy.shouldRetry(e, attempt)) {
       setRetryingStatus(task.id, attempt, retryPolicy.maxAttempts, e.message ?: "unknown")
       delay(backoffMillis(attempt))  // exponential: 5_000 * 2^(attempt-1)
       // re-enqueue: re-fetch the task (its status is now RETRYING) + re-run download
       startDownload(task.copy(retryAttempt = attempt, status = RETRYING))
   } else {
       setErrorStatus(task.id, e.message ?: e.javaClass.simpleName)
   }
   ```
   (Exact re-enqueue mechanism to verify against `DownloadQueue`'s `startDownload`/`tryStartNext` — the task must transition RETRYING → DOWNLOADING cleanly.)
3. Update `DownloadNotificationManager` to show "Retrying (n/N)..." during RETRYING.
4. Make the backoff delay cancellable (StructuredConcurrency — the `delay` is inside the job, so `pause()`/`cancel()` will cancel it via `CancellationException`).
5. Register `RetryPolicy` in Koin (`single { RetryPolicy() }`), inject into `DownloadQueue`.

### Why NOT do this now
- It's a medium-effort feature (not the "easily implementable" bar the user set), with 6 design decisions needing user input.
- Manual retry already works as a user fallback.
- It composes with Item 1 — doing them together in one phase is cleaner (they share the catch-block + the RETRYING state + device-test pass).

---

## Recommended future-phase scope

When this phase starts, do **Items 1 + 2 + 4 together** (they share the error-
handling code path + the RETRYING state + a single device-test pass), and
**Item 3 (delete DownloadVideoPickerSheet)** as a quick cleanup.

### Suggested task breakdown
1. Item 1: wire the proxy-churn adapter + Koin binding + `DownloadModule.kt` change.
2. Item 2a: add `127.0.0.1` to the guard (2 lines).
3. Item 2b: fix the `video_uri`/`video_url` column (verify + add/fix the query).
4. Item 4: create `RetryPolicy` + wire the outer loop + notification UX + backoff.
5. Item 3: delete `DownloadVideoPickerSheet.kt` + the `ShowPicker` TODO (or wire it — user's call).
6. Device test: localhost-proxy download + churn + recover; transient 5xx + auto-retry; pause-during-retry; manual retry still works.

### Estimated effort
- Items 1 + 2: ~2-3 hours (adapter + 2 small fixes + Koin).
- Item 4: ~3-4 hours (RetryPolicy + loop + notification + backoff + testing).
- Item 3: ~15 min (delete).
- Device testing: ~1 hour.
- **Total: ~6-8 hours** (one focused phase).

---

## What works today (so the future phase knows the baseline)
- Downloads of direct (non-proxy) URLs: ✅ work end-to-end (enqueue → download → offline playback).
- Downloads of localhost-proxy URLs: ⚠️ work ONLY if the proxy isn't churned mid-download (no concurrent resolve). If churned → ERROR (manual retry recovers).
- Manual retry (user taps retry on ERROR): ✅ works (`retryDownload()`).
- Pause/resume: ✅ works (Range-resume).
- Offline playback of downloaded files: ✅ works (`content://` → `fd://`).
- Auto-download engine: ✅ works (5-step pipeline).
- Foreground service + notifications: ✅ work (2 channels, NetworkCallback auto-pause/resume).
