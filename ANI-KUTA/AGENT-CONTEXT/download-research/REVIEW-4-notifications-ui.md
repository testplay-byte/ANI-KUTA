# REVIEW-4 — Notifications + Foreground Service + Workflow/UI + QoL Features

> **Task ID:** DL-REVIEW-4 (Review Round 4 of 5)
> **Reviewer:** senior-review-agent
> **Scope:** `06-notifications-foreground-service.md` (702 lines), `01-workflow-click-to-queue.md` (426 lines), `08-downloads-page-ui.md` (491 lines), `09-details-page-download-ui.md` (347 lines), `16-quality-of-life.md` (455 lines).
> **Carry-over:** Review 1 (storage/DB) · Review 2 (autodl/settings) · Review 3 (queue/state/downloaders). Specifically Review 3 C1 (unbounded recursion in `HttpDownloader.downloadNormal`), C5 (`HttpException` does not exist from the download module's POV), I2 (`resetDownloadingToQueued` doesn't reset RETRYING), I7 (`setRetryingStatus` undefined), I9 (mutex race in `onNetworkChanged`), I15 (`mutateTask` doesn't acquire mutex).

---

## 0. Verification methodology

Every claim in the 5 docs was verified against the actual source files:

- OLD `DownloadNotificationManager.kt` (191 lines) — fully read.
- OLD `core/download/AndroidManifest.xml` (8 lines) + OLD `app/AndroidManifest.xml` (101 lines) — fully read.
- OLD `EpisodeDownloadControl.kt` (177 lines) + `EpisodeDownloadState.kt` (45 lines) — fully read.
- OLD `AppController.kt:1040-1169` — `downloadEpisode` / `enqueuePickedVideo` / `cancelDownload` / `resumeDownload` / `retryDownload` / `deleteDownload` verified verbatim.
- OLD `DownloadOrchestrator.kt:50-170` — `enqueueDownload` + `enqueueSpecific` verified verbatim.
- OLD `DownloadsScreen.kt:80-221` + `:400-569` — full layout + `EpisodeMenuSheet` + `formatBytes` verified.
- OLD `DownloadViewModel.kt` (105 lines) — fully read.
- OLD `DefaultDownloadManager.kt:1-110` — `observeJob` + `activeDownloads` derivation verified.
- OLD `DownloadStatus.kt` (42 lines) — 6-state enum (QUEUED/DOWNLOADING/PAUSED/COMPLETED/ERROR/CANCELLED) verified. NO RETRYING in OLD.
- OLD `HttpDownloader.kt:64-287` — confirmed HTTP errors wrapped in `DownloadException("HTTP ${response.code} for video URL")` (line 239) with NO cause set, vs IOException-caught errors wrapped in `DownloadException("Video download failed: ${e.message}", e)` (line 285) WITH cause.
- NEW `app/AndroidManifest.xml` (63 lines) — fully read.
- NEW `core/download/AndroidManifest.xml` — **DOES NOT EXIST** (the `:core:download` module has no manifest; verified via Glob).
- NEW `AndroidConfig.kt` — confirmed `minSdk = 24, compileSdk = 36, targetSdk = 36`.
- NEW `core/download/build.gradle.kts` — confirmed deps are `:core:common`, `:core:database`, `:core:preferences`, `:core:network`, `okhttp`, `kotlinx.coroutines`, `logcat`, `koin`. **NO dependency on `:core:source-api`** (where `HttpException` lives).
- NEW `gradle/libs.versions.toml` + `ImageLoaderFactory.kt` — confirmed **Coil 3** (`io.coil-kt.coil3`, version 3.0.4), NOT Coil 2.
- NEW `ExtensionInstallService.kt` (128 lines) — fully read as a reference for the correct foreground service pattern (synchronous `startForeground` + explicit `ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC` on API 34+).
- NEW `DownloadManager.kt` (163 lines, stub) + `DownloadState.kt` (12 lines, 5-state sealed interface) — confirmed still stub per Review 3.

---

## 1. Per-checklist verdict

| # | Checklist item | Verdict | Notes |
|---|---|---|---|
| 1 | Foreground service design (`dataSync`, survives app close, START_STICKY restart, channel setup) | **FAIL** | `startForeground` is NOT called synchronously in `onStartCommand`/`onCreate` — racy `queueCollector` may call `stopSelf()` first → `ForegroundServiceDidNotStartInTimeException` crash on Android 12+. Existing `ExtensionInstallService.kt` in the same project shows the correct pattern (`startForegroundCompat(...)` synchronously in `onStartCommand` line 69). |
| 2 | Notification channels (dual silent + sound) | **PASS** | `IMPORTANCE_LOW` (silent progress) + `IMPORTANCE_DEFAULT` (sound on completion) design is correct. `setSilent(true)` on the progress notification is belt-and-braces. The completion channel + `BigPictureStyle` approach is correct. |
| 3 | Notification thumbnails (cover.jpg + Coil fallback) | **FAIL** | Code uses Coil 2 API (`Coil.imageLoader(context)`, `ImageRequest.Builder(context).data(url).size(96).build()`, `drawable?.toBitmap()`) but the NEW project uses **Coil 3** (`io.coil-kt.coil3:coil-compose:3.0.4`). Will not compile. Plus `runBlocking { ... }` on `Dispatchers.Main` (the service's scope) = ANR. |
| 4 | Notification actions (Pause all / Cancel all PendingIntents) | **CONCERN** | Design is sound (PendingIntent.getService → `DownloadService.onStartCommand` action dispatch), but `R.drawable.ic_pause` / `R.drawable.ic_cancel` are referenced without being declared as resources to create, and PendingIntents use hardcoded request codes 1 and 2 (collision risk if app has other PendingIntents). Also: lock-screen visibility is not configured (`setVisibility(VISIBILITY_PUBLIC)` is missing — default is `VISIBILITY_PRIVATE` which on lock-screen hides the action buttons). |
| 5 | Workflow doc (click→queue trace accuracy + NEW 5-step pipeline accounting) | **CONCERN** | The OLD-project trace (AppController → DownloadOrchestrator → selectBestVideo → manager.enqueueDownload → DownloadQueue.enqueue → tryStartNext) is accurate — verified against OLD `AppController.kt:1046-1087` and `DownloadOrchestrator.kt:65-144`. But the doc traces the OLD 3-step `selectBestVideo` algorithm WITHOUT mentioning that the NEW project replaces it with the 5-step `AutoDownloadEngine` (per Review 2's `14-auto-download-engine.md`). The doc is a faithful OLD-project reference but is misleading as a NEW-project spec. |
| 6 | Downloads page UI replication (vs OLD "replicate exactly") | **PASS** | The 08 doc's trace matches the OLD `DownloadsScreen.kt` verbatim (verified: line 80-221 layout, line 264-272 StatChip, line 278-329 AnimeSectionCard, line 468-508 EpisodeMenuSheet, line 564-569 formatBytes). The "don't replicate dead code" warnings (QueueRow.kt, DownloadsEmptyState.kt component, DownloadedAnimeCard.kt component) are accurate. |
| 7 | Details page download control (7 states + RETRYING) | **CONCERN** | The 09 doc correctly covers the OLD 7 states (verified against OLD `EpisodeDownloadState.kt` + `EpisodeDownloadControl.kt`). BUT it does NOT cover the NEW `RETRYING` state that Review 3 + QoL §1.3 introduce. The mapping `DownloadStatus.RETRYING -> EpisodeDownloadState.???` is undefined. The UI rendering for RETRYING (QoL §1.3 promises "spinner + 'Retrying (2/3)...' pill + Cancel") is not specified in 09. |
| 8 | QoL features (auto-retry + auto-resume + auto-pause + orphan cleanup) | **FAIL** | Auto-retry policy uses `e is HttpException` (C5 carry-over from Review 3) — invisible to `:core:download`. The HTTP 5xx/429 retry branches are dead code. The "retry happens inside the retry loop, user sees 'Retrying (2/3)...'" headline is broken by Review 3 C1 (unbounded re-resolve recursion in `HttpDownloader.downloadNormal` — NOT yet fixed). `onNetworkChanged` has a mutex deadlock risk (C8). `scheduleAutoClear`'s `autoClearScheduled.add(taskId)` is outside `mutex.withLock` (I10). |
| 9 | Android version compatibility (minSdk 24) | **CONCERN** | `STOP_FOREGROUND_REMOVE` (API 24+), `NotificationChannel` (API 26+, guarded), `FLAG_IMMUTABLE` (API 23+, guarded), `setSilent` (NotificationCompat — all APIs), `BigPictureStyle` (NotificationCompat — all APIs) — all OK on minSdk 24. BUT: `foregroundServiceType="dataSync"` on Android 14+ has a 6-hour daily runtime cap (not mentioned); `ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC` should be passed explicitly on API 34+ (the existing `ExtensionInstallService.startForegroundCompat` does this; the proposed `DownloadService` does NOT). |
| 10 | User's specific requirements (no sound during download / sound on completion / thumbnails / foreground service survives app close) | **FAIL** | No-sound-during-download: ✓ (IMPORTANCE_LOW channel + `setSilent(true)`). Sound-on-completion: ✓ (IMPORTANCE_DEFAULT channel). Thumbnails: ✗ (Coil 2 API + main-thread `runBlocking`). Foreground service survives app close: ✗ (the startForeground race + missing `ACCESS_NETWORK_STATE` permission + missing `DownloadService` manifest declaration = the service won't even start correctly). |

---

## 2. CRITICAL issues

### C1 — `DownloadService.queueCollector` may call `stopSelf()` without ever calling `startForeground()` → `ForegroundServiceDidNotStartInTimeException` crash on Android 12+

**File:** `06-notifications-foreground-service.md` §13.7 lines 597-642.

The proposed `DownloadService` launches a coroutine in `init` (constructor) that collects `manager.activeDownloads`:

```kotlin
private val queueCollector = scope.launch {
    manager.activeDownloads.collect { active ->
        if (active.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()                      // ← may run BEFORE startForeground was ever called
        } else {
            val notification = notifier.buildSummaryNotification(active)
            if (!isForeground) {
                startForeground(SUMMARY_ID, notification)
                isForeground = true
            } else {
                notificationManager.notify(SUMMARY_ID, notification)
            }
        }
    }
}
```

Per §13.9: "the `DownloadManager` calls `DownloadService.start(context)` on enqueue (if not already running)." If the service is started BEFORE the new task is added to `_tasks` (the natural order — start the service first, then enqueue), the StateFlow's first emission is `emptyList()`. The code path hits `stopSelf()` without ever calling `startForeground()`.

On Android 12+ (API 31+), the system tracks `ContextCompat.startForegroundService(...)` and expects `Service.startForeground(...)` within ~5s. If `stopSelf()` is called instead, the system throws `ForegroundServiceDidNotStartInTimeException` (a fatal crash broadcast to the app).

**The fix is already in the same project.** `ExtensionInstallService.onStartCommand` (NEW project, `data/extension/.../ExtensionInstallService.kt:58-90`) calls `startForegroundCompat("Installing extension…")` SYNCHRONOUSLY at line 69 — before any coroutine work. The proposed `DownloadService` must follow the same pattern: post a placeholder "Downloads" notification via `startForeground` synchronously in `onStartCommand` (or `onCreate`), then update it from the queueCollector. The placeholder guarantees the 5s contract is met regardless of queue state.

**Severity:** CRITICAL — would crash the app on every cold-start enqueue race on Android 12+ (which is most devices in 2026).

### C2 — `downloadCover` uses Coil 2 API, but the NEW project uses Coil 3 — won't compile

**File:** `06-notifications-foreground-service.md` §13.2 lines 463-476.

```kotlin
private fun downloadCover(coverUrl: String?): Bitmap? {
    if (coverUrl.isNullOrBlank()) return null
    return try {
        runBlocking {
            Coil.imageLoader(context).execute(            // ← Coil 2 API
                ImageRequest.Builder(context)              // ← Coil 2 (Coil 3 takes PlatformContext)
                    .data(coverUrl)
                    .size(96)                              // ← Coil 2 size API
                    .build()
            ).drawable?.toBitmap()                         // ← Drawable.toBitmap() works but result type differs
        }
    } catch (e: Exception) { null }
}
```

The NEW project's `gradle/libs.versions.toml` declares `coil = "3.0.4"` with `coil-compose = { group = "io.coil-kt.coil3", ... }`. `ImageLoaderFactory.kt` uses `coil3.ImageLoader`, `coil3.PlatformContext`, `coil3.disk.DiskCache`, `coil3.network.okhttp.OkHttpNetworkFetcherFactory`, `coil3.request.crossfade`. The Coil 3 API differs from Coil 2 in several ways:

- `coil3.imageLoader(platformContext)` extension (NOT `Coil.imageLoader(context)`).
- `coil3.request.ImageRequest.Builder(platformContext: PlatformContext)` — `PlatformContext` is a type-alias that includes `Context`, but the call site must use the Coil 3 import.
- `ImageRequest.Builder.size(96)` exists in both versions, but the result type of `execute()` is `coil3.ImageResult` (not `coil3.request.ImageResult` as in v2). `.drawable` is `coil3.Image?` in v3 (a sealed type, not a `Drawable`); to get a `Drawable` you call `.image.asDrawable(platformContext)` and then `toBitmap()`.
- `drawable?.toBitmap()` requires `import androidx.core.graphics.drawable.toBitmap` — a fine extension, but the receiver is a `Drawable`, not an `Image`.

**The fix:** rewrite `downloadCover` against the Coil 3 API, mirroring the patterns already used in `ImageLoaderFactory.kt`. Or — preferred — make the thumbnail load fully async + cache the result (`ImageLoader.execute` returns a suspend `ImageResult`; the loader already has a 500MB disk cache, so the second + subsequent loads for the same cover URL are essentially free).

**Severity:** CRITICAL — the file as written fails to compile.

### C3 — `runBlocking { Coil.imageLoader(context).execute(...) }` on `Dispatchers.Main` → ANR

**File:** `06-notifications-foreground-service.md` §13.2 lines 467-474 + §13.7 line 600.

```kotlin
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)  // ← Main!
    private val queueCollector = scope.launch {
        manager.activeDownloads.collect { active ->
            ...
            val notification = notifier.buildSummaryNotification(active)  // ← calls loadThumbnail
            ...
        }
    }
}
```

`buildSummaryNotification` calls `loadThumbnail` which calls:
1. `context.contentResolver.openInputStream(coverFile.uri)?.use { BitmapFactory.decodeStream(input) }` — synchronous SAF I/O (DocumentFile I/O on the main thread, which is O(N) per Review 1 I6).
2. `downloadCover(coverUrl)` — `runBlocking { ... Coil.execute(...) }` — a blocking network call on the main thread.

Both will trigger StrictMode violations and, for a slow CDN (the same flaky CDNs the QoL doc §1.4 says we're targeting), an ANR within 5s.

**The fix:** change the scope to `Dispatchers.Default` (or `Main.immediate` with the heavy work explicitly offloaded to `Dispatchers.IO`). Make `buildSummaryNotification` a suspend function and `withContext(Dispatchers.IO) { ... }` the thumbnail load. Or: pre-load + cache the thumbnail as a `Bitmap?` in a `Map<String, Bitmap?>` keyed by `mainId`, updated asynchronously on each task creation, and have `buildSummaryNotification` just read the cache (synchronous, fast).

**Severity:** CRITICAL — the foreground service would ANR the app the moment a download starts.

### C4 — `ACCESS_NETWORK_STATE` permission is MISSING from the NEW project — `registerNetworkCallback` will SecurityException-crash

**File:** `16-quality-of-life.md` §2.1 lines 117-143.

```kotlin
init {
    connectivityManager.registerNetworkCallback(
        NetworkRequest.Builder().addCapability(NET_CAPABILITY_INTERNET).build(),
        networkCallback,
    )
}
```

`ConnectivityManager.registerNetworkCallback` requires `android.permission.ACCESS_NETWORK_STATE`. Verified:

- OLD `core/download/src/main/AndroidManifest.xml` declares it (line 4).
- NEW `app/src/main/AndroidManifest.xml` — does NOT declare it (lines 1-15 listed; only INTERNET, VIBRATE, REQUEST_INSTALL_PACKAGES, FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC, POST_NOTIFICATIONS, QUERY_ALL_PACKAGES).
- NEW `core/download/` module has NO `src/main/AndroidManifest.xml` at all (verified via Glob — the module dir contains only `build.gradle.kts` + `src/main/java/...`).

Without this permission, `registerNetworkCallback` throws `SecurityException` on the first `DownloadManager.init` call. The QoL §2 auto-resume / §3 auto-pause headline features cannot start.

**The fix:** add `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />` to the NEW `core/download/src/main/AndroidManifest.xml` (which must also be created). The OLD project's manifest in the same module is the template.

**Severity:** CRITICAL — the QoL features crash on first init.

### C5 (carry-over from Review 3, NOT fixed) — `RetryPolicy.forException` uses `e is HttpException` — `HttpException` is invisible to `:core:download`

**File:** `16-quality-of-life.md` §1.2 lines 73-75.

```kotlin
e is HttpException && e.code in 500..599 -> Policy(true, 3, { attempt -> (1000L * (1 shl (attempt - 1))) })
e is HttpException && e.code == 429 -> Policy(true, 3, { attempt -> 5000L * (1 shl (attempt - 1)) })
e is HttpException && e.code in 400..499 -> Policy(false, 0, { 0 })
```

`HttpException` IS defined in the project — but at `core/source-api/src/main/kotlin/eu/kanade/tachiyomi/network/OkHttpExtensions.kt:183` (verified), inside the `:core:source-api` module. The NEW `:core:download/build.gradle.kts` does NOT depend on `:core:source-api` (verified). Review 3's C5 was slightly wrong ("doesn't exist") but the operational effect is identical: from the download module's perspective, `HttpException` is unresolved → won't compile.

Even if a dep on `:core:source-api` is added, the policy still won't fire for HTTP errors coming from `HttpDownloader`. Verified against OLD `HttpDownloader.kt:239`:

```kotlin
client.newCall(request).execute().use { response ->
    if (!response.isSuccessful) {
        throw DownloadException("HTTP ${response.code} for video URL")   // ← no cause
    }
```

The HTTP-error path wraps in `DownloadException` (NOT `HttpException`) and does NOT set `cause`. So:
- `e is HttpException` → never matches.
- `e is DownloadException && e.cause is IOException` → never matches (cause is null on the HTTP-error path).
- → HTTP 5xx/429 errors fall through to the `else` branch → `Policy(false, 0, { 0 })` → no retry.

The QoL doc's headline "HTTP 5xx (server error) → retry 3x" and "HTTP 429 (rate limit) → retry 3x with Retry-After" promises are non-functional.

**The fix:** either (a) have `HttpDownloader` throw `HttpException(code)` directly for HTTP errors (requires `:core:download → :core:source-api` dep, or move `HttpException` to `:core:network`), or (b) parse the code from the `DownloadException.message` (`message?.startsWith("HTTP ") == true` → `message.removePrefix("HTTP ").substringBefore(' ').toIntOrNull()`), or (c) introduce a download-module-local `HttpErrorException(code: Int)` and have `HttpDownloader` throw it.

**Severity:** CRITICAL — the auto-retry feature (QoL §1, the headline feature) doesn't cover the most common retryable error class.

### C6 (carry-over from Review 3, NOT fixed) — `setRetryingStatus(task.id, attempt, policy.maxAttempts, e.message)` is called but never defined

**File:** `16-quality-of-life.md` §1.2 line 54.

```kotlin
// Retryable — set status = RETRYING with the attempt number.
setRetryingStatus(task.id, attempt, policy.maxAttempts, e.message ?: e.javaClass.simpleName)
delay(policy.backoffMs(attempt))
```

`setRetryingStatus` is not defined anywhere in `02-queue-management.md`, `03-state-machine.md`, `05-downloaders.md`, `06-notifications-foreground-service.md`, or `16-quality-of-life.md`. Review 3 I7 flagged this; the doc was not updated. The code as written won't compile.

**The fix:** define `setRetryingStatus(taskId, attempt, maxAttempts, lastError)` as a private method on `DownloadQueue` that does `mutex.withLock { mutateTask(taskId) { it.copy(status = DownloadStatus.RETRYING(...), updatedAt = now()) }; store.updateState(taskId, "RETRYING", ...) }` (mirroring `setErrorStatus`).

**Severity:** CRITICAL — won't compile.

### C7 (carry-over from Review 3, NOT fixed) — `resetDownloadingToQueued` does NOT reset RETRYING → QoL §1.3 line 101's claim is FALSE

**File:** `16-quality-of-life.md` §1.3 line 101.

> The state persists in the DB (`download_queue.state = 'RETRYING'`) — survives app restart (though the retry loop itself doesn't; on restart, the queue's `resetDownloadingToQueued` also resets RETRYING → QUEUED).

Verified against `11-db-schema.md` §3 line 248-251 (per Review 3 I2): the SQL `resetDownloadingToQueued` matches only `WHERE state = 'DOWNLOADING'`, NOT `'RETRYING'`. So after an app crash mid-retry, RETRYING tasks are stuck in RETRYING forever (until the user manually retries).

**The fix:** either (a) update the SQL to `WHERE state IN ('DOWNLOADING', 'RETRYING')`, or (b) update the doc to admit that RETRYING tasks need manual retry after restart.

**Severity:** CRITICAL — tasks stuck forever after a crash during retry.

### C8 — `onNetworkChanged` calls `pause(it.id)` inside `mutex.withLock` → non-reentrant Mutex DEADLOCK

**File:** `16-quality-of-life.md` §2.2 lines 149-167.

```kotlin
fun onNetworkChanged(isWifi: Boolean, hasInternet: Boolean) {
    scope.launch {
        mutex.withLock {
            if (!hasInternet) {
                _tasks.value.filter { it.status == DownloadStatus.DOWNLOADING }.forEach { pause(it.id) }
            } ...
        }
    }
}
```

There are TWO definitions of `onNetworkChanged` in the doc set:
1. `02-queue-management.md` §13.3 lines 519-527 — does NOT wrap in `mutex.withLock`; calls `pause(it.id)` directly (which itself is not mutex-protected per §13.3 lines 87-95).
2. `16-quality-of-life.md` §2.2 lines 149-167 — DOES wrap in `mutex.withLock`; calls `pause(it.id)` from inside the lock.

If `pause` is mutex-protected (Review 3 I15's recommendation), the QoL §2.2 version deadlocks — `kotlinx.coroutines.sync.Mutex` is **non-reentrant**: calling `mutex.withLock { ... pause(...) ... }` from the same coroutine that already holds `mutex` will suspend forever waiting for itself to release the lock.

If `pause` is mutex-free (the 02-queue-management §13.3 version), then the QoL §2.2 mutex-wrap provides no protection against concurrent pause calls from `onNetworkChanged` vs. user-initiated pause — the race Review 3 I9 warned about.

**The fix:** (a) reconcile the two docs — pick one definition; (b) extract a `pauseInternal(taskId)` that ASSUMES the mutex is held (no `mutex.withLock` inside), and have both `pause` (public) and `onNetworkChanged` (mutex-holding caller) use the right variant.

**Severity:** CRITICAL — deadlock hangs the download queue the moment Wi-Fi drops, OR race condition.

---

## 3. IMPORTANT issues

### I1 — `DownloadService` references `notificationManager` that is never declared

**File:** `06-notifications-foreground-service.md` §13.7 line 613.

```kotlin
if (!isForeground) {
    startForeground(SUMMARY_ID, notification)
    isForeground = true
} else {
    notificationManager.notify(SUMMARY_ID, notification)   // ← where does this come from?
}
```

The class body declares `manager`, `notifier`, `scope`, `queueCollector`, `isForeground` (implicitly) — but NOT `notificationManager`. The reference is unresolved. Should be either `NotificationManagerCompat.from(this).notify(...)` or `notifier.postSummaryUpdate(notification)` (delegate to the notifier).

### I2 — `DownloadService` uses Koin `inject<>()` delegate but doesn't implement `KoinComponent`

**File:** `06-notifications-foreground-service.md` §13.7 line 598.

```kotlin
class DownloadService : Service() {
    private val manager by inject<DownloadManager>()
    private val notifier by inject<DownloadNotificationManager>()
```

Koin's `by inject<T>()` delegate on a class requires the class to implement `KoinComponent` (or be a `KoinScopeComponent`). `Service` does not implement either. The code as written won't compile. The fix: `class DownloadService : Service(), KoinComponent { ... }` and import `org.koin.core.component.inject`.

### I3 — `loadThumbnail` does synchronous SAF I/O (`openInputStream`) which on `Dispatchers.Main` is ANR

**File:** `06-notifications-foreground-service.md` §13.2 lines 452-461.

```kotlin
return try {
    context.contentResolver.openInputStream(coverFile.uri)?.use { input ->
        BitmapFactory.decodeStream(input)?.let { scaleForNotification(it) }
    }
}
```

This is fine on `Dispatchers.IO` but is called from `buildSummaryNotification` which is called from `queueCollector` on `Dispatchers.Main` (per §13.7 line 600). Combined with C3, the entire thumbnail-load path is on the main thread. See C3 for the fix.

### I4 — `DownloadService.queueCollector` runs on `Dispatchers.Main` — wrong dispatcher for notification building

**File:** `06-notifications-foreground-service.md` §13.7 line 600.

```kotlin
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
```

`Dispatchers.Main` is correct for `startForeground`/`notify` calls (Android notification APIs are main-thread-only) but wrong for the thumbnail loading + `BitmapFactory.decodeStream` + `runBlocking { Coil.execute(...) }` work that `buildSummaryNotification` does. The correct pattern is: collect on `Dispatchers.Default` (or `IO`), do the heavy work, then `withContext(Dispatchers.Main) { startForeground(...) }` only for the notification post.

### I5 — `R.drawable.ic_pause` and `R.drawable.ic_cancel` are referenced but not declared as resources to create

**File:** `06-notifications-foreground-service.md` §13.2 lines 429-430.

```kotlin
.addAction(R.drawable.ic_pause, "Pause all", pauseAllIntent())
.addAction(R.drawable.ic_cancel, "Cancel all", cancelAllIntent())
```

The OLD project has no notification action buttons (verified — `DownloadNotificationManager.kt` does not call `addAction`), so no such drawables exist. The 06 doc doesn't list creating these as an implementation step. Either:
- Add the implementation step "Create `ic_pause.xml` + `ic_cancel.xml` vector drawables in `:core:download/src/main/res/drawable/`", or
- Use framework drawables: `android.R.drawable.ic_media_pause` + `android.R.drawable.ic_menu_close_clear_cancel` (less polished, but zero work).

### I6 — Android 14+ `dataSync` foreground service has a 6-hour daily runtime cap — not mentioned in the doc

**File:** `06-notifications-foreground-service.md` §13 + §10.

Per Android 14 (API 34) docs: `dataSync` foreground services have a 6-hour cumulative daily runtime cap per app. After 6 hours, the system calls `onTimeout(startId, foregroundServiceType)` (API 35+). The 06 doc doesn't mention this. For most anime episodes (1-3GB, 5-30 min each) this is fine, but for users downloading many episodes back-to-back, or for slow-CDN scenarios, the service WILL be killed mid-download at the 6-hour mark.

The doc should:
1. Document the 6-hour cap as a known limitation.
2. Specify an `onTimeout` handler (API 35+) that gracefully pauses the queue + posts a "Downloads paused (time limit reached)" notification.
3. Note that the cap is per-app-per-day, so if `ExtensionInstallService` (also `dataSync`) consumes some of the 6h budget, the download service has less.

### I7 — `e is DownloadException && e.cause is IOException` doesn't catch HTTP errors (which set NO cause)

**File:** `16-quality-of-life.md` §1.2 line 71.

```kotlin
e is DownloadException && e.cause is IOException -> Policy(true, 3, { attempt -> (1000L * (1 shl (attempt - 1))) })
```

This branch catches `DownloadException("Video download failed: ...", ioException)` (OLD `HttpDownloader.kt:285`). But the HTTP-error path `throw DownloadException("HTTP ${response.code} for video URL")` at line 239 has NO cause (cause = null) → doesn't match this branch. See C5 for the fix.

### I8 — 09 doc's `episodeState(episode, contentId)` mapping function is "inferred", not actually present in `AppController`

**File:** `09-details-page-download-ui.md` §2 lines 50-72.

The doc admits: "The actual mapping function isn't a single named function — it's done inline where `EpisodeRow` is composed... The above is the inferred logic from the data flow."

This is honest, but it means the doc's pseudocode may not match the actual implementation. The doc's `DownloadStatus.CANCELLED -> EpisodeDownloadState.NotDownloaded` mapping is plausible but not verified — the OLD `AppController` may map it differently (e.g. to `Error("Cancelled")`). The implementation team should verify against `AppController` (or the NEW host equivalent) before replicating.

### I9 — Workflow doc traces the OLD 3-step `selectBestVideo` without mentioning the NEW 5-step `AutoDownloadEngine`

**File:** `01-workflow-click-to-queue.md` §7 lines 284-295.

The doc traces `DownloadOrchestrator.selectBestVideo` (3-step: audio check → quality check → server×audio×quality combinations). This is the OLD algorithm. Per Review 2's `14-auto-download-engine.md`, the NEW project replaces this with the 5-step `AutoDownloadEngine` pipeline (Step 1 collect → Step 2 rank by dim priority → Step 3 apply per-dim fallbacks → Step 4 dedupe → Step 5 ASK/DO_NOT_DOWNLOAD fallback). The workflow doc's §7 doesn't mention the NEW pipeline, leaving the implementation team to assume the OLD algorithm is the spec.

The fix: add a §7.5 noting "The NEW project replaces `DownloadOrchestrator.selectBestVideo` with `AutoDownloadEngine.selectBestVideo` per `14-auto-download-engine.md` §6. The 5-step pipeline preserves the same API contract (`Selection.Selected`/`Selection.NoMatch`) so the rest of the trace (buildRequest → manager.enqueueDownload) is unchanged."

### I10 — `scheduleAutoClear`'s `autoClearScheduled.add(taskId)` is OUTSIDE `mutex.withLock` — race on the Set

**File:** `16-quality-of-life.md` §6.1 lines 319-330 + `02-queue-management.md` §13.3 lines 490-499.

```kotlin
private fun scheduleAutoClear(taskId: Long) {
    if (taskId in autoClearScheduled) return  // ← outside mutex
    autoClearScheduled.add(taskId)            // ← outside mutex
    scope.launch {
        delay(10_000)
        mutex.withLock {
            autoClearScheduled.remove(taskId) // ← inside mutex
            _tasks.value = _tasks.value.filterNot { it.id == taskId }
            store.delete(taskId)
        }
    }
}
```

`MutableSet<Long>` (`autoClearScheduled`) is not thread-safe. If `scheduleAutoClear` is called concurrently (e.g. from `launchDownload`'s completion handler on Dispatchers.IO while another task is completing on a different dispatcher), the `in`/`add` checks race — two callers can both pass the `in` check, both `.add`, and both schedule a coroutine. The 10s guard against duplicate removal is preserved (the second coroutine's `autoClearScheduled.remove(taskId)` no-ops because the first already removed it), so the leak is fixed, but the Set itself can be in an inconsistent state.

The fix: either wrap `scheduleAutoClear`'s `in`/`add` in `mutex.withLock`, or use `java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()` (thread-safe Set).

### I11 — `loadThumbnail` calls `storage.findContentDir(mainId)` — synchronous SAF DocumentFile traversal, O(N) per Review 1 I6

**File:** `06-notifications-foreground-service.md` §13.2 line 454.

```kotlin
val contentDir = storage.findContentDir(mainId) ?: return downloadCover(coverUrl)
val coverFile = contentDir.findFile("cover.jpg") ?: return downloadCover(coverUrl)
```

Per Review 1 I6: `DocumentFile.findFile()` is O(N) over the children of a directory. For a content folder with many episode files, this can be tens of milliseconds of SAF I/O — on `Dispatchers.Main`, ANR. Plus `storage.findContentDir(mainId)` likely walks the SAF tree to find the right content folder — another O(N) over all content folders. Combined with C3, the entire thumbnail-load path is on the main thread.

### I12 — `RetryPolicy.forException`'s `CancellationException` branch is unreachable

**File:** `16-quality-of-life.md` §1.2 lines 43-44, 67.

```kotlin
} catch (e: CancellationException) {
    throw e  // pause/cancel — don't retry
} catch (e: Exception) {
    val policy = RetryPolicy.forException(e)
    ...
```

`catch (e: CancellationException) { throw e }` re-throws before reaching `catch (e: Exception)`. So `RetryPolicy.forException` is never called with a `CancellationException`. The `e is CancellationException -> Policy(false, 0, { 0 })` branch at line 67 is dead code. Not a bug, but worth removing for clarity.

---

## 4. MINOR issues

### M1 — 01 doc §5 shows `cancelDownload` with `if (task == null) { ...; return }` syntax, but the OLD code uses `?: run { ...; return }`

**File:** `01-workflow-click-to-queue.md` §5 lines 257-275 vs OLD `AppController.kt:1136-1141`.

Logic identical, style mismatch. Worth fixing for fidelity to the OLD code (the implementation team will pattern-match on this doc).

### M2 — 09 doc's "AnimatedContent" KDoc-vs-code mismatch is already flagged in `15-ui-and-bug-analysis.md` §A.11 fix #13

**File:** `09-details-page-download-ui.md` §3 line 159.

Already in 15-ui-and-bug-analysis.md as an optional polish item. The 09 doc restates it accurately; no action needed.

### M3 — Notification ID collision risk if `task.id` exceeds `Int.MAX_VALUE - 20_000`

**File:** `06-notifications-foreground-service.md` §13.3 line 509.

`task.id.toInt() + COMPLETION_OFFSET` — if `task.id` is `Long` and grows beyond `Int.MAX_VALUE - 20_000`, `toInt()` truncates and the IDs can collide with the summary ID (9001) or each other. In practice the queue starts at 0 and increments — unlikely to reach Int.MAX_VALUE. But worth a `coerceIn(0, Int.MAX_VALUE - 30_000)` guard.

### M4 — `object RetryPolicy` placement in `16-quality-of-life.md` §1.2 is syntactically ambiguous

**File:** `16-quality-of-life.md` §1.2 lines 32-78.

The `object RetryPolicy { ... }` appears at the same indent level as `private fun launchDownload`, suggesting it's a class-nested object. That compiles. But the visual presentation is ambiguous (looks like it might be a top-level object). Worth a "// nested object inside DownloadQueue" comment for clarity.

### M5 — `onNetworkChanged` is defined differently in `02-queue-management.md` §13.3 vs `16-quality-of-life.md` §2.2

**File:** `02-queue-management.md` §13.3 lines 519-527 (no mutex) vs `16-quality-of-life.md` §2.2 lines 149-167 (mutex-wrapped).

These are inconsistent. Pick one. See C8.

### M6 — `16-quality-of-life.md` §3.2 "Downloads paused (Wi-Fi only)" notification spec is incomplete

**File:** `16-quality-of-life.md` §3.2 lines 190-197.

The doc says "post a one-shot notification" but doesn't specify: ID, the `setContentIntent` (deep-link? open the Downloads settings screen?), the `setAutoCancel` (true), whether it has a "Resume now" action button. Spec needs completion.

### M7 — `16-quality-of-life.md` §4.2 magic-byte check is non-fatal even for HTML — should be fatal if HTML detected

**File:** `16-quality-of-life.md` §4.2 lines 229-247.

The size check (§4.1) is fatal — throws `DownloadException`. But the magic-byte check (§4.2) is non-fatal — only logs. If the size check passes (file > 500KB) but the magic bytes are HTML (`<!` or `<h`), the file is published to the user's folder. This is exactly the scenario the OLD `HttpDownloader.verifyVideoMagicBytes` rejects (per OLD `HttpDownloader.kt:382-389`: `throw DownloadException("The downloaded file is an HTML page, not a video...")` — verified). The QoL doc regresses this — should throw on HTML detection.

### M8 — 09 doc lists 7 `EpisodeDownloadState` variants but doesn't cover the NEW RETRYING state

**File:** `09-details-page-download-ui.md` §1 + §3.

The doc correctly enumerates the OLD 7 states (verified). But the NEW project adds `RETRYING` (per QoL §1.3 + Review 3). The mapping `DownloadStatus.RETRYING -> EpisodeDownloadState.???` is undefined. The UI rendering for RETRYING (QoL §1.3 promises "spinner + 'Retrying (2/3)...' pill + Cancel") is not specified in 09. The 09 doc should add an 8th state `Retrying(attempt, maxAttempts)` with its rendering spec.

### M9 — 08 doc's bulk "Retry all" iterates `queue.filter { it.status == ERROR }` — what about RETRYING?

**File:** `08-downloads-page-ui.md` §3 lines 86-91.

```kotlin
onRetryAll = { queue.filter { it.status == ERROR }.forEach { viewModel.retry(it.id) } },
```

If RETRYING is added (QoL §1.3), should "Retry all" also retry RETRYING tasks? Or does RETRYING mean "already retrying — skip"? The 08 doc doesn't address this. The natural semantic: RETRYING is already being retried by the engine — "Retry all" should NOT call `viewModel.retry(id)` on RETRYING tasks (the engine's retry loop owns them). But the 08 doc's bulk action bar doesn't show a "Retrying" count chip either, so the user has no visibility into how many tasks are mid-retry.

### M10 — `pauseAllIntent()` / `cancelAllIntent()` use hardcoded request codes 1 and 2

**File:** `06-notifications-foreground-service.md` §13.6 lines 569-587.

```kotlin
PendingIntent.getService(
    context, 1, intent, ...   // ← request code 1
)
PendingIntent.getService(
    context, 2, intent, ...   // ← request code 2
)
```

If the app has other PendingIntents with request codes 1 or 2 (e.g. media-style notifications, widget PendingIntents), they collide. Best practice: use a unique prefix like `1001` and `1002`, or `hashCode()` of the action string.

### M11 — `DownloadService` has no `onTaskRemoved` override

**File:** `06-notifications-foreground-service.md` §13.7.

If the user swipes the app from recents, the service MAY be killed by some OEMs (Xiaomi/Huawei) despite being a foreground service. The standard pattern is `override fun onTaskRemoved(rootIntent: Intent?) { /* optionally restart via startService */ }`. The 06 doc doesn't address this. For downloads, the safer behavior is to keep running (the foreground notification makes the service visible to the user) — but on aggressive OEMs, an `onTaskRemoved` that re-launches the service via `startForegroundService` may be needed. Worth a §13.13 note.

### M12 — 09 doc §11 says "keyed by episode URL (NOT by composite key)" but the OLD `downloadTasksFlow` IS keyed by composite key

**File:** `09-details-page-download-ui.md` §11 line 338 + OLD `AppController.kt:1123` comment ("keyed by `"$contentId|$episodeNumber"`").

The OLD project's `downloadTasksFlow` is keyed by composite key (per AppController's own comment). The `downloadStates: Map<String, EpisodeDownloadState>` passed to `AnimeDetailScreen` IS keyed by `episode.url` (per DetailContent/EpisodesSection). So there's a translation step in AppController: from composite-keyed `downloadTasksFlow` to URL-keyed `downloadStates` (by iterating values and matching `(contentId, episodeUrl)` — confirmed at AppController.kt:1136-1137). The 09 doc's recommendation to "key by episode URL" is the URL-keyed map's key, not the underlying `downloadTasksFlow`'s key. The doc could clarify this layering.

---

## 5. Per-`user-requirement` verification

> The user's 4 explicit requirements per `06-notifications-foreground-service.md` §12:
> 1. Better visual notifications with thumbnail images (cover image per content).
> 2. Foreground service so downloads survive app close.
> 3. No sound during download — sound on completion only.
> 4. (Implied) Pause/cancel from the notification.

| Requirement | Status | Blocker |
|---|---|---|
| 1. Thumbnails | **FAIL** | C2 (Coil 2 API on Coil 3 project — won't compile) + C3 (main-thread `runBlocking` ANR). |
| 2. Foreground service survives app close | **FAIL** | C1 (startForeground race → crash on Android 12+) + C4 (missing ACCESS_NETWORK_STATE → service crashes on init) + the `DownloadService` is not even declared in any manifest yet. |
| 3. No sound during download | **PASS** | Dual-channel design is correct (IMPORTANCE_LOW progress channel + `setSilent(true)`). |
| 3. Sound on completion | **PASS** | IMPORTANCE_DEFAULT completion channel plays the system default sound. |
| 4. Pause/cancel from notification | **CONCERN** | Design is sound (PendingIntent.getService → action dispatch) but I5 (missing drawables), I2 (KoinComponent missing — won't compile), and lock-screen visibility not configured (`setVisibility(VISIBILITY_PUBLIC)` missing — default PRIVATE hides actions on lock screen). |

---

## 6. Cross-doc consistency

| Topic | 02-queue-management.md | 16-quality-of-life.md | 06-notifications-foreground-service.md | Verdict |
|---|---|---|---|---|
| `onNetworkChanged` body | §13.3: NOT mutex-wrapped, calls `pause(it.id)` | §2.2: mutex-wrapped, calls `pause(it.id)` inside lock | (not mentioned) | INCONSISTENT (see C8, M5) |
| `pause(it.id)` mutex behavior | §13.3 line 87-95: NO `mutex.withLock` (mutateTask is non-locking) | §2.2: assumes pause acquires the lock | (not mentioned) | INCONSISTENT — Review 3 I15 said `mutateTask` should be mutex-protected, but neither doc shows the mutex-protected `pause` |
| RETRYING state machine | (not mentioned) | §1.3: claims `resetDownloadingToQueued` resets RETRYING → QUEUED | (not mentioned) | FALSE per Review 3 I2 (C7 here) |
| `setRetryingStatus` | (not defined) | §1.2: called but not defined | (not mentioned) | UNDEFINED (C6) |
| `HttpException` retry | (not mentioned) | §1.2: `e is HttpException` — invisible to `:core:download` | (not mentioned) | BROKEN (C5) |
| Foreground service start | (not mentioned) | (not mentioned) | §13.7: racy queueCollector-only startForeground | DIVERGENT from existing `ExtensionInstallService` pattern (C1) |
| EpisodeDownloadState RETRYING mapping | (not mentioned) | (not mentioned) | (not mentioned) | UNDEFINED (M8) |

---

## 7. Overall verdict

**APPROVED WITH CHANGES — BLOCKED on 8 CRITICAL issues.**

The design — dual notification channels, foreground service with `dataSync`, cover thumbnails, Pause-all/Cancel-all actions, deep-link tap intent, auto-retry with backoff, auto-resume on network change, auto-pause on metered, orphan cleanup — is **fundamentally sound and correctly addresses the user's 4 explicit requirements at the design level**. The "replicate exactly" UI specs (08, 09) are accurate verbatim traces of the OLD code (verified line-by-line). The QoL feature set (16) is the right set of "small features, huge impact" — exactly the kind of polish the user asked for.

**BUT the implementation specs have 8 CRITICAL issues that block Phase D.4/D.7 implementation as-written**:

1. **C1**: Foreground service startForeground race → crash on Android 12+ (the existing `ExtensionInstallService` in the same project shows the correct pattern — copy it).
2. **C2**: Coil 2 API on a Coil 3 project → won't compile (rewrite `downloadCover` against Coil 3, mirroring `ImageLoaderFactory.kt`).
3. **C3**: `runBlocking { ... }` + `BitmapFactory.decodeStream` + SAF I/O on `Dispatchers.Main` → ANR.
4. **C4**: `ACCESS_NETWORK_STATE` permission missing → `registerNetworkCallback` crashes on init (the OLD `:core:download` manifest has it; the NEW `:core:download` module has no manifest at all).
5. **C5** (carry-over Review 3): `HttpException` invisible to `:core:download`; HTTP 5xx/429 retry branches are dead code.
6. **C6** (carry-over Review 3): `setRetryingStatus` called but not defined → won't compile.
7. **C7** (carry-over Review 3): `resetDownloadingToQueued` doesn't reset RETRYING → tasks stuck forever after crash mid-retry.
8. **C8**: `onNetworkChanged` calls `pause` inside `mutex.withLock` → non-reentrant Mutex deadlock (if `pause` is mutex-protected) OR race (if not).

Plus 12 IMPORTANT issues (manifest `notificationManager` undefined, `KoinComponent` missing, `ic_pause`/`ic_cancel` drawables not listed, 6-hour `dataSync` cap not mentioned, etc.) and 12 MINOR issues.

The user's "no sound during download / sound on completion" requirements are met (PASS). The "thumbnails" and "foreground service survives app close" requirements are NOT met as-written (FAIL — blocked by C1-C4).

**The single highest-impact fix** is to align `DownloadService` with the existing `ExtensionInstallService.kt` pattern (synchronous `startForeground` in `onStartCommand` + explicit `ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC` on API 34+). That alone resolves C1 + half of C4's symptom + the API-level concern from checklist #9.

**Recommended next actions for the plan author:**
1. Fix C1-C4 (foreground service + Coil 3 + main-thread + ACCESS_NETWORK_STATE) — these are the user's headline requirements.
2. Fix C5-C7 (carry-over from Review 3 — `HttpException` + `setRetryingStatus` + RETRYING-on-restart) — these block the QoL §1 auto-retry headline.
3. Fix C8 + M5 (reconcile `onNetworkChanged` between 02-queue-management.md and 16-quality-of-life.md — pick one definition).
4. Add the 8th `EpisodeDownloadState.Retrying(attempt, maxAttempts)` variant to 09-details-page-download-ui.md (M8) + the bulk-action-bar RETRYING handling to 08-downloads-page-ui.md (M9).
5. Add a §13.13 to 06 noting the 6-hour `dataSync` cap + `onTimeout` handler (I6).
6. Then proceed to Phase D.4 (notifications + foreground service) + Phase D.6 (UI) + Phase D.7 (QoL).

**Next review round (DL-REVIEW-5)** should focus on the player integration + the proxy-churn fix (10-player-integration.md §14) — specifically the ReResolver + the AutoDownloadEngine integration with the retry loop, and whether the "download fails when playing another episode" bug is actually fixed end-to-end. Also: the implementation plan (13-implementation-plan.md) Phase D sequencing + the cross-doc consistency matrix.
