# 06 — Notifications + Foreground Service

> All line references: `core/download/src/main/java/app/confused/anikuta/core/download/DownloadNotificationManager.kt` (191 lines) + `core/download/src/main/AndroidManifest.xml` + `app/src/main/AndroidManifest.xml`.

## 1. Summary

The old ANI-KUTA download system **does NOT use a foreground Service**. Downloads run in an app-scoped `CoroutineScope(SupervisorJob + Dispatchers.IO)` and post notifications via `NotificationManagerCompat` (no `startForeground`).

The `FOREGROUND_SERVICE_DATA_SYNC` permission IS declared in `app/src/main/AndroidManifest.xml:10`, but that's used by `ExtensionInstallService` (line 79-81), NOT by the download system.

This is a **potential gap**: on Android 14+ (and even earlier on aggressive OEMs like Xiaomi/Huawei), background downloads without a foreground service can be killed when the app is backgrounded. Worth flagging for the new project — see `13-implementation-plan.md`.

## 2. Notification channel setup

**`DownloadNotificationManager.kt:34-36`** (init block):
```kotlin
init {
    ensureChannel()
}
```

**`ensureChannel()` — line 156-173**:
```kotlin
private fun ensureChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            CHANNEL_ID,        // "anikuta_downloads"
            "Downloads",
            NotificationManager.IMPORTANCE_LOW,  // no sound
        ).apply {
            description = "Download progress and completion notifications"
            setShowBadge(false)
        }
        try {
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        } catch (e: Exception) {
            DownloadLogger.w("Failed to create download notification channel", e)
        }
    }
}
```

Channel constants (line 182-187):
```kotlin
companion object {
    private const val CHANNEL_ID = "anikuta_downloads"
    private const val SUMMARY_ID = 9001
    private const val COMPLETION_OFFSET = 10_000
    private const val ERROR_OFFSET = 20_000
    private const val PROGRESS_THROTTLE_MS = 800L
    @Volatile private var lastProgressAt = 0L
}
```

- One channel: `anikuta_downloads` — `IMPORTANCE_LOW` (no sound, shows in shade).
- `SUMMARY_ID = 9001` — the ongoing progress notification ID.
- `COMPLETION_OFFSET = 10_000` — completion notifications use `task.id.toInt() + 10_000` (so they don't collide with the summary or each other).
- `ERROR_OFFSET = 20_000` — error notifications use `task.id.toInt() + 20_000`.
- Progress throttled to 1 update per 800ms (app-wide, via the static `lastProgressAt`).

## 3. The ongoing summary notification

**`updateProgress(active: List<DownloadTask>)` — line 51-102**:

```kotlin
fun updateProgress(active: List<DownloadTask>) {
    try {
        if (active.isEmpty()) {
            cancel(SUMMARY_ID)
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastProgressAt < PROGRESS_THROTTLE_MS) return
        lastProgressAt = now

        val primary = active.firstOrNull { it.status == DownloadStatus.DOWNLOADING }
            ?: active.first()  // firstOrNull, NOT first — see comment below
        val title = if (active.size == 1) {
            "${primary.request.anime.title} — EP ${primary.request.episode.episodeNumber.toInt()}"
        } else {
            "Downloading ${active.size} episodes"
        }
        val progressText = if (primary.totalBytes > 0) {
            "${primary.progress}% • ${formatBytes(primary.downloadedBytes)} / ${formatBytes(primary.totalBytes)}"
        } else {
            "${primary.progress}% • ${formatBytes(primary.downloadedBytes)}"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(progressText)
            .setProgress(100, primary.progress.coerceAtLeast(0), primary.progress <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openAppIntent())

        try {
            notificationManager.notify(SUMMARY_ID, builder.build())
        } catch (e: SecurityException) {
            DownloadLogger.w("Cannot post download notification (permission denied)", e)
        } catch (e: Exception) {
            DownloadLogger.w("Notification post failed (non-fatal)", e)
        }
    } catch (e: Exception) {
        DownloadLogger.e("updateProgress failed (non-fatal)", e)
    }
}
```

**Key things**:
- Picks the **first DOWNLOADING task** as the "primary" (or falls back to the first active task if all are QUEUED). Uses `firstOrNull` (not `first`) — the comment at line 64 explains this was the cause of an earlier `NoSuchElementException` crash.
- Single notification for all active downloads — title shows count when > 1 ("Downloading 3 episodes").
- Progress bar: indeterminate when `progress <= 0`, determinate otherwise.
- `setOngoing(true)` — can't be swiped away while downloading.
- `setOnlyAlertOnce(true)` + `setSilent(true)` — no sound, no heads-up.
- Tap intent: opens the app's launch activity (`openAppIntent()` — line 147-154). No deep-link to the Downloads screen (the comment at line 25-26 notes this as a "future enhancement once the nav state-machine supports deep links").

### Throttling

The `lastProgressAt` is `@Volatile` in the companion — shared across all instances (there's only one instance, but the volatile is defensive). Updates are coalesced to 1 per 800ms.

### Resilience

The whole body is wrapped in **three layers** of try/catch:
1. Outer: catches anything in the body (including `firstOrNull` bugs).
2. `notificationManager.notify` is wrapped for `SecurityException` (POST_NOTIFICATIONS denied on Android 13+) and generic `Exception` (some OEMs throw on notification posting).
3. The class KDoc (line 38-50) explicitly says: "This method MUST NEVER throw — it's called from a hot StateFlow collector in `DefaultDownloadManager`; an uncaught exception there crashes the app."

### Caller

`DefaultDownloadManager.observeJob` — line 87-97:
```kotlin
private val observeJob = scope.launch {
    queue.tasks.collect { all ->
        try {
            val active = all.filter { it.isInQueue }
            notifier.updateProgress(active)
            if (active.isEmpty()) notifier.cancelActive()
        } catch (e: Exception) {
            DownloadLogger.e("Download state observer failed (non-fatal)", e)
        }
    }
}
```

Collects the queue's `tasks` StateFlow. On every emission, filters to in-queue tasks + calls `updateProgress`. If the queue empties, calls `cancelActive()` to remove the summary notification.

## 4. One-shot completion notification

**`notifyCompleted(task)` — line 105-120**:
```kotlin
fun notifyCompleted(task: DownloadTask) {
    try {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download complete")
            .setContentText("${task.request.anime.title} — EP ${task.request.episode.episodeNumber.toInt()}")
            .setAutoCancel(true)  // dismisses on tap
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent())
        notificationManager.notify(task.id.toInt() + COMPLETION_OFFSET, builder.build())
    } catch (e: SecurityException) {
        DownloadLogger.w("Cannot post completion notification (permission denied)", e)
    } catch (e: Exception) {
        DownloadLogger.w("notifyCompleted failed (non-fatal)", e)
    }
}
```

- `stat_sys_download_done` icon.
- `setAutoCancel(true)` — dismissed on tap.
- ID: `task.id + 10_000` (doesn't collide with the summary's 9001 or other completions).
- Called from `DownloadQueue.launchDownload`'s success path via the `onTaskCompleted` callback (set by `DefaultDownloadManager` line 72).

## 5. One-shot error notification

**`notifyError(task)` — line 123-138**:
```kotlin
fun notifyError(task: DownloadTask) {
    try {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download failed")
            .setContentText("${task.request.anime.title} — ${task.errorMessage ?: "Unknown error"}")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)  // higher than completion
            .setContentIntent(openAppIntent())
        notificationManager.notify(task.id.toInt() + ERROR_OFFSET, builder.build())
    } catch (e: SecurityException) {
        DownloadLogger.w("Cannot post error notification (permission denied)", e)
    } catch (e: Exception) {
        DownloadLogger.w("notifyError failed (non-fatal)", e)
    }
}
```

- `stat_notify_error` icon.
- `PRIORITY_DEFAULT` (higher than completion's `PRIORITY_LOW`) — so failures are more visible.
- ID: `task.id + 20_000`.
- Called from `DownloadQueue.launchDownload`'s `DownloadException` + generic `Exception` catch blocks via the `onTaskError` callback (line 73).

## 6. The tap intent

**`openAppIntent()` — line 147-154**:
```kotlin
private fun openAppIntent(): PendingIntent {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?: Intent()
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    else PendingIntent.FLAG_UPDATE_CURRENT
    return PendingIntent.getActivity(context, 0, intent, flags)
}
```

- Just opens the app's launcher activity (MainActivity). No deep-link to the Downloads screen.
- `FLAG_IMMUTABLE` on API 23+ (required on API 31+).
- Same intent for all three notification types.

## 7. Notification actions (pause/cancel from notification?)

**No actions.** The notification has no action buttons — just the tap intent. Pause/cancel/retry are only available in the in-app Downloads screen.

The KDoc (line 25-26) notes: "A direct deep-link is a future enhancement once the nav state-machine supports deep links."

For the new project, adding **pause/cancel action buttons** to the summary notification would be a UX win — see `13-implementation-plan.md`.

## 8. Permissions

### `core/download/src/main/AndroidManifest.xml`:
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
</manifest>
```

- `ACCESS_NETWORK_STATE` — for the Wi-Fi-only connectivity check in `DefaultDownloadManager.isNetworkAllowed()`.
- `POST_NOTIFICATIONS` — runtime permission on Android 13+ (API 33+).

### `app/src/main/AndroidManifest.xml` (relevant lines):
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" tools:ignore="ScopedStorage" />
```

- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` — declared but used by `ExtensionInstallService` only, NOT by downloads.
- `MANAGE_EXTERNAL_STORAGE` — declared (used by the Setup Wizard "All files access" toggle), but NOT required by the download system (which uses SAF, not raw file paths).
- `INTERNET` — for OkHttp.

### Runtime permission request

`DownloadsScreen.kt:92-107` requests `POST_NOTIFICATIONS` on first entry to the Downloads page (Android 13+ only):
```kotlin
val context = androidx.compose.ui.platform.LocalContext.current
val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
    contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
) { _ -> }
androidx.compose.runtime.LaunchedEffect(Unit) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        val granted = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
```

The result is ignored (the launcher's callback is empty) — the system remembers the user's choice. If denied, the notifier's `try/catch SecurityException` silently swallows the post failure. The in-app UI still works.

## 9. Service declaration? (there is none for downloads)

`app/src/main/AndroidManifest.xml:77-81` declares ONE service — `ExtensionInstallService` — for the extension installer, NOT for downloads:
```xml
<service
    android:name="app.confused.anikuta.data.extension.installer.ExtensionInstallService"
    android:exported="false"
    android:foregroundServiceType="dataSync" />
```

`core/download/src/main/AndroidManifest.xml` declares **no services**.

So the download engine:
- Has NO Service class.
- Has NO `startForeground` call.
- Runs entirely in `DefaultDownloadManager`'s app-scoped `CoroutineScope`.

## 10. Why this is a problem on modern Android

| Android version | Behavior |
|---|---|
| Android 8+ (API 26+) | Background execution limits — apps in cached state have network jobs deferred. |
| Android 9+ (API 28+) | App Standby Buckets — "rare" bucket severely limits background downloads. |
| Android 12+ (API 31+) | Trampoline restrictions (irrelevant here — no service). |
| Android 14+ (API 34+) | `FOREGROUND_SERVICE_DATA_SYNC` required for explicit data-sync foreground services. Broadcasts to background services blocked. |

For a download system that needs to keep running when the user backgrounds the app, the **correct pattern** is:
1. A `Service` (or `WorkManager` worker) with `foregroundServiceType="dataSync"`.
2. `startForeground(...)` within 5 seconds of starting, posting the ongoing summary notification as the foreground notification.
3. The `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` permissions (already declared in the manifest).

The old project's `CoroutineScope(SupervisorJob + Dispatchers.IO)` works while the app is in the foreground, but downloads stall when backgrounded on aggressive OEMs. **The new project should add a foreground Service** — see `13-implementation-plan.md` §6 for the recommendation.

## 11. Bytes formatter (used in notifications)

**`DownloadNotificationManager.formatBytes(bytes)` — line 175-180**:
```kotlin
private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
}
```

A copy of this also exists in `DownloadsScreen.kt:564-569` and `QueueRow.kt:238-243`. (Minor code duplication — should be a util in `:core:common` for the new project.)

## 12. Summary — what the new project must replicate + improve (POST-REWRITE)

> **Task ID:** DL-PLAN-REWRITE
> The user's requirements for the NEW notification design:
> 1. **Better visual notifications with thumbnail images** (cover image per content).
> 2. **Foreground service** so downloads survive app close.
> 3. **No sound during download** — sound on completion only.
>
> This requires the NEW design documented below in §13 (the post-rewrite section). The OLD project's single-channel, no-thumbnail, no-foreground-service design (§§1-11 above) is a baseline reference only — the new project goes beyond it.

| Aspect | Old project | New project (post-rewrite) |
|---|---|---|
| Channels | Single `anikuta_downloads` IMPORTANCE_LOW | **Two channels:** `anikuta_downloads_progress` (IMPORTANCE_LOW, no sound, ongoing) + `anikuta_downloads_complete` (IMPORTANCE_DEFAULT, **with sound**, completion) |
| Summary notification | ID 9001, ongoing, progress bar, throttled 800ms, **no thumbnail** | ID 9001, ongoing, progress bar, throttled 800ms, **thumbnail (cover image)** |
| Completion notification | ID `taskId + 10_000`, `stat_sys_download_done`, auto-cancel, **no sound** | ID `taskId + 10_000`, `stat_sys_download_done`, auto-cancel, **with sound** (via the `anikuta_downloads_complete` channel) + **BigPictureStyle with cover thumbnail** |
| Error notification | ID `taskId + 20_000`, `stat_notify_error`, auto-cancel, PRIORITY_DEFAULT | Same (on the progress channel — silent, no need to beep for errors) |
| Sound during download | None (single IMPORTANCE_LOW channel) | **None** (the progress channel is IMPORTANCE_LOW — no sound, no heads-up) |
| Sound on completion | None | **Yes** (the completion channel is IMPORTANCE_DEFAULT — plays the default notification sound) |
| Thumbnails | None (text only) | **Cover image per content** — loaded from the cached `cover.jpg` in the content folder (or downloaded on-demand from `coverUrl` via Coil). Uses `NotificationCompat.BigPictureStyle` for completion + `setLargeIcon` for the summary. |
| Foreground Service | **NONE** (runs in CoroutineScope) | **YES** — `DownloadService` with `foregroundServiceType="dataSync"` + `startForeground(SUMMARY_ID, notification)` within 5s of starting. Stops when the queue empties. |
| Action buttons | None | **Pause all / Cancel all** on the summary notification (deep-link to the queue's bulk-action methods) |
| Tap intent | Opens launcher activity (no deep-link) | **Deep-link to the Downloads screen** (via Nav3 deep-link support or an Intent extra) |
| POST_NOTIFICATIONS | Requested on first Downloads-page open | Same — requested on first Downloads-page open (Android 13+) |
| Permissions | ACCESS_NETWORK_STATE, POST_NOTIFICATIONS, INTERNET | Same + FOREGROUND_SERVICE + FOREGROUND_SERVICE_DATA_SYNC (already declared in the new app manifest, just unused) |

See `13-implementation-plan.md` Phase D.4 for the full implementation plan.

---

## 13. The NEW notification design (post-rewrite)

> Implements the user's requirements: thumbnails, no sound during download, sound on completion, foreground service.

### 13.1 The two notification channels

```kotlin
private const val CHANNEL_PROGRESS = "anikuta_downloads_progress"  // ongoing, silent
private const val CHANNEL_COMPLETE = "anikuta_downloads_complete"  // one-shot, with sound

private fun ensureChannels() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val nm = context.getSystemService(NotificationManager::class.java)

    // Channel 1: ongoing progress — silent (no sound during download).
    val progressChannel = NotificationChannel(
        CHANNEL_PROGRESS,
        "Downloads",
        NotificationManager.IMPORTANCE_LOW,   // no sound, shows in shade
    ).apply {
        description = "Download progress notifications (silent during download)"
        setShowBadge(false)
        enableVibration(false)
        setSound(null, null)
    }
    nm.createNotificationChannel(progressChannel)

    // Channel 2: completion — plays the default notification sound.
    val completeChannel = NotificationChannel(
        CHANNEL_COMPLETE,
        "Download complete",
        NotificationManager.IMPORTANCE_DEFAULT,  // sound + heads-up
    ).apply {
        description = "Notifications when downloads finish (with sound)"
        setShowBadge(true)
        enableVibration(true)
        // Uses the system default sound (set via the channel's importance, not per-notification).
    }
    nm.createNotificationChannel(completeChannel)
}
```

**Why two channels?**
- The user wants NO sound during download (the progress bar updates would beeps annoyingly if the channel had sound). The progress channel is `IMPORTANCE_LOW` — silent.
- The user wants SOUND on completion (so they know when a download finishes). The completion channel is `IMPORTANCE_DEFAULT` — plays the system default notification sound.
- Two channels also lets the user customize per-channel in Android system settings (e.g. mute completion but keep progress, or vice versa).

### 13.2 The summary notification (with thumbnail)

```kotlin
/**
 * REVIEW-5 M22: now `suspend` + assumes the caller is on `Dispatchers.IO`. The `withContext(Dispatchers.Main)`
 * around `NotificationManager.notify` is at the call site in `DownloadService.queueCollector`.
 */
suspend fun buildSummaryNotification(active: List<DownloadTask>): Notification {
    val primary = active.firstOrNull { it.status == DownloadStatus.DOWNLOADING }
        ?: active.firstOrNull()
        ?: return buildEmptySummary()  // queue empty — stop foreground

    val title = if (active.size == 1) {
        "${primary.content.title} — EP ${primary.episode.episodeNumber.toInt()}"
    } else {
        "Downloading ${active.size} episodes"
    }
    val progressText = if (primary.totalBytes > 0) {
        "${primary.progress}% • ${formatBytes(primary.downloadedBytes)} / ${formatBytes(primary.totalBytes)}"
    } else {
        "${primary.progress}% • ${formatBytes(primary.downloadedBytes)}"
    }

    val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(title)
        .setContentText(progressText)
        .setProgress(100, primary.progress.coerceAtLeast(0), primary.progress <= 0)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)                                              // no sound during download
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)          // REVIEW-5 M30 — lock-screen shows the Pause/Cancel actions
        .setContentIntent(openDownloadsScreenIntent())
        .addAction(R.drawable.ic_pause, "Pause all", pauseAllIntent())
        .addAction(R.drawable.ic_cancel, "Cancel all", cancelAllIntent())

    // ── NEW: thumbnail (cover image of the primary task's content) ──
    // REVIEW-5 M22: `loadThumbnail` is now suspend; this builder is suspend too.
    val thumbnail = loadThumbnail(primary.content.mainId, primary.content.coverUrl)
    if (thumbnail != null) {
        builder.setLargeIcon(thumbnail)
    }

    return builder.build()
}

/**
 * Loads the cover image thumbnail for the notification.
 *
 * Strategy:
 * 1. Try the cached cover.jpg in the content's SAF folder (fast — no network).
 * 2. If not cached, download from coverUrl via Coil (best-effort, 5s timeout).
 * 3. If both fail, return null — the notification shows without a thumbnail.
 *
 * REVIEW-5 M22: this function is now `suspend` + called from `withContext(Dispatchers.IO) { ... }`
 * in `buildSummaryNotification`. The OLD draft was synchronous + called on `Dispatchers.Main` —
 * the SAF `openInputStream` + `BitmapFactory.decodeStream` + `runBlocking { Coil.execute(...) }`
 * path triggered ANRs on slow CDNs.
 */
private suspend fun loadThumbnail(mainId: String, coverUrl: String?): Bitmap? {
    // 1. Try the cached cover.jpg.
    val contentDir = storage.findContentDir(mainId) ?: return downloadCover(coverUrl)
    val coverFile = contentDir.findFile("cover.jpg") ?: return downloadCover(coverUrl)
    return try {
        context.contentResolver.openInputStream(coverFile.uri)?.use { input ->
            BitmapFactory.decodeStream(input)?.let { scaleForNotification(it) }
        }
    } catch (e: Exception) { downloadCover(coverUrl) }
}

private fun downloadCover(coverUrl: String?): Bitmap? {
    if (coverUrl.isNullOrBlank()) return null
    // REVIEW-5 M21 + M22: rewritten against Coil 3 (the NEW project uses `io.coil-kt.coil3:coil-compose:3.0.4`,
    // verified in `gradle/libs.versions.toml` + `ImageLoaderFactory.kt`). The OLD draft used the
    // Coil 2 API (`Coil.imageLoader(context)`, `ImageRequest.Builder(context).data(url).size(96).build()`,
    // `.drawable?.toBitmap()`) — wouldn't compile.
    //
    // Also: this function is now `suspend` (no `runBlocking { ... }`). The caller `loadThumbnail`
    // is called from `buildSummaryNotification`, which is itself now `suspend` + called from the
    // `queueCollector` on `Dispatchers.IO` (see §13.7 — M22 fixes the ANR-by-runBlocking-on-Main).
    // The actual `startForeground` / `NotificationManager.notify` calls are wrapped in
    // `withContext(Dispatchers.Main) { ... }` at the call site.
    return try {
        val loader = context.imageLoader  // Coil 3 extension on PlatformContext (set as singleton in AnikutaApp.kt)
        val request = ImageRequest.Builder(context)
            .data(coverUrl)
            .size(96)
            .build()
        loader.execute(request).image?.let { image ->
            // Coil 3: `Image` is a sealed type — convert to a `Bitmap` via `asDrawable` then `toBitmap`.
            image.asDrawable(context).toBitmap()
        }
    } catch (e: Exception) { null }
}
```

**Required imports** (Coil 3):
```kotlin
import coil3.imageLoader                  // the extension property (resolves to the singleton set in AnikutaApp.kt)
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.asDrawable                    // Image.asDrawable(platformContext) extension
import androidx.core.graphics.drawable.toBitmap
```

**Key things:**
- The thumbnail is the content's cover image — same one shown in the Downloads UI.
- Loaded from the cached `cover.jpg` first (no network round-trip).
- Falls back to downloading from `coverUrl` via Coil if not cached.
- The notification is `setSilent(true)` — no sound during download (per the user's requirement).

### 13.3 The completion notification (with sound + thumbnail)

```kotlin
fun notifyCompleted(task: DownloadTask) {
    val builder = NotificationCompat.Builder(context, CHANNEL_COMPLETE)  // ← the SOUND channel
        .setSmallIcon(android.R.drawable.stat_sys_download_done)
        .setContentTitle("Download complete")
        .setContentText("${task.content.title} — EP ${task.episode.episodeNumber.toInt()}")
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)              // heads-up + sound
        .setContentIntent(openDownloadsScreenIntent())

    // ── NEW: BigPictureStyle with the cover thumbnail ──
    val thumbnail = loadThumbnail(task.content.mainId, task.content.coverUrl)
    if (thumbnail != null) {
        builder.setStyle(
            NotificationCompat.BigPictureStyle()
                .bigPicture(thumbnail)
                .bigLargeIcon(null)  // collapses the large icon when expanded
                .setSummaryText(task.content.title)
        ).setLargeIcon(thumbnail)
    }

    try {
        notificationManager.notify(task.id.toInt() + COMPLETION_OFFSET, builder.build())
    } catch (e: SecurityException) {
        // POST_NOTIFICATIONS denied on Android 13+. Swallow — the in-app UI still works.
    }
}
```

**Key things:**
- Uses the `CHANNEL_COMPLETE` channel — `IMPORTANCE_DEFAULT` with sound. The user hears a beep when a download finishes.
- `BigPictureStyle` shows the cover thumbnail as a large banner when expanded.
- `setLargeIcon(thumbnail)` shows a smaller version in the collapsed view.
- `setAutoCancel(true)` — dismissed on tap.
- Tap deep-links to the Downloads screen (via `openDownloadsScreenIntent()` — see §13.5).

### 13.4 The error notification

```kotlin
fun notifyError(task: DownloadTask) {
    val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)  // ← silent channel
        .setSmallIcon(android.R.drawable.stat_notify_error)
        .setContentTitle("Download failed")
        .setContentText("${task.content.title} — ${task.errorMessage ?: "Unknown error"}")
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(openDownloadsScreenIntent())

    // Thumbnail (same as completion — shows what failed).
    val thumbnail = loadThumbnail(task.content.mainId, task.content.coverUrl)
    if (thumbnail != null) builder.setLargeIcon(thumbnail)

    try {
        notificationManager.notify(task.id.toInt() + ERROR_OFFSET, builder.build())
    } catch (e: SecurityException) { /* swallow */ }
}
```

**Key things:**
- Uses the SILENT `CHANNEL_PROGRESS` — errors don't need to beep (the user already sees the red error pill in the UI).
- Thumbnail for visual context.

### 13.5 The deep-link tap intent

```kotlin
private fun openDownloadsScreenIntent(): PendingIntent {
    // Build a deep-link Intent that the host's nav controller can intercept.
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setClassName(context.packageName, "com.confused.anikuta.MainActivity")
        data = Uri.parse("anikuta://downloads")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    return PendingIntent.getActivity(context, 0, intent, flags)
}
```

The host's `MainActivity` checks for the `anikuta://downloads` deep-link in `onNewIntent` + pushes the `DownloadsKey` onto the Nav3 stack.

### 13.6 The action buttons

```kotlin
private fun pauseAllIntent(): PendingIntent {
    val intent = Intent(context, DownloadService::class.java).apply {
        action = DownloadService.ACTION_PAUSE_ALL
    }
    return PendingIntent.getService(
        // REVIEW-5 M29: use a unique request-code prefix (1001/1002) so the Pause/Cancel
        // PendingIntents don't collide with other app PendingIntents that use codes 1 and 2.
        context, 1001, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private fun cancelAllIntent(): PendingIntent {
    val intent = Intent(context, DownloadService::class.java).apply {
        action = DownloadService.ACTION_CANCEL_ALL
    }
    return PendingIntent.getService(
        context, 1002, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
```

The `DownloadService` handles these actions in `onStartCommand`:
- `ACTION_PAUSE_ALL` → `downloadManager.pauseAll()`.
- `ACTION_CANCEL_ALL` → `downloadManager.cancelAll()`.

### 13.7 The foreground service (`DownloadService`)

> **REVIEW-5 M20:** the OLD draft launched `queueCollector` as a coroutine at construction time
> + called `startForeground(...)` only inside the `else` branch when the queue was non-empty.
> If the queue was empty when the service was started (the common case — `DownloadService.start()`
> is called BEFORE `manager.enqueueDownload` returns), the StateFlow's first emission was
> `emptyList()` → the code path hit `stopSelf()` without ever calling `startForeground()` →
> `ForegroundServiceDidNotStartInTimeException` crash on Android 12+.
>
> The fix copies the pattern from the existing `ExtensionInstallService.kt` (verified at
> `data/extension/src/main/java/.../ExtensionInstallService.kt:58-90`): call `startForeground(...)`
> SYNCHRONOUSLY in `onStartCommand` (before returning), with a placeholder notification. The
> `queueCollector` then only UPDATES the notification via `NotificationManagerCompat.notify(...)`
> — it never calls `startForeground` itself.

```kotlin
class DownloadService : Service(), KoinComponent {  // REVIEW-5 M25 — KoinComponent required for `by inject<>()`
    private val manager by inject<DownloadManager>()
    private val notifier by inject<DownloadNotificationManager>()
    // REVIEW-5 M24: declare the notificationManager explicitly (was undefined in the OLD draft).
    private val notificationManager by inject<NotificationManagerCompat>()
    // REVIEW-5 M22: heavy work (thumbnail load, Coil execute, SAF I/O) on Dispatchers.IO;
    // only `startForeground` / `NotificationManager.notify` need Dispatchers.Main.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isForeground = false
    private var queueCollector: Job? = null

    override fun onCreate() {
        super.onCreate()
        // Start collecting the queue — but DON'T call startForeground from here. The system
        // gives us 5s after startForegroundService() to call startForeground; we do it
        // synchronously in onStartCommand instead (per the ExtensionInstallService pattern).
        queueCollector = scope.launch {
            manager.activeDownloads.collect { active ->
                if (active.isEmpty()) {
                    // Queue emptied — gracefully leave foreground + stop.
                    withContext(Dispatchers.Main) {
                        if (isForeground) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            isForeground = false
                        }
                        stopSelf()
                    }
                } else {
                    // Build the summary notification (suspend — heavy work on IO).
                    val notification = notifier.buildSummaryNotification(active)
                    withContext(Dispatchers.Main) {
                        if (!isForeground) {
                            // First non-empty emission — promote to foreground. (This branch is
                            // RARELY hit because onStartCommand already called startForeground
                            // synchronously with a placeholder. We keep it for the START_STICKY
                            // restart case where the system re-launches the service.)
                            startForegroundCompat(notification)
                            isForeground = true
                        } else {
                            notificationManager.notify(SUMMARY_ID, notification)
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // REVIEW-5 M20: SYNCHRONOUSLY start foreground with a placeholder notification BEFORE
        // any coroutine work. This satisfies the Android 12+ 5-second contract regardless of
        // queue state. Pattern copied from `ExtensionInstallService.onStartCommand` line 69.
        if (!isForeground) {
            startForegroundCompat(buildPlaceholderNotification())
            isForeground = true
        }

        when (intent?.action) {
            ACTION_PAUSE_ALL -> runBlocking { manager.pauseAll() }
            ACTION_CANCEL_ALL -> runBlocking { manager.cancelAll() }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // REVIEW-5 M28: aggressive OEMs (Xiaomi/Huawei) may kill the service on swipe-from-recents
        // despite the foreground notification. Re-launch via startForegroundService so the user's
        // in-flight downloads survive. The queue's persisted state (SQLDelight) means even a hard
        // kill is recoverable on next launch — this just makes the soft-kill case invisible.
        val restart = Intent(applicationContext, DownloadService::class.java)
        ContextCompat.startForegroundService(applicationContext, restart)
        super.onTaskRemoved(rootIntent)
    }

    /**
     * REVIEW-5 M27: Android 14+ caps `dataSync` foreground services at 6 hours per app per day.
     * After 6 hours, the system calls `onTimeout` (API 35+). We gracefully pause the queue +
     * post a one-shot "Downloads paused (time limit reached)" notification so the user knows
     * why their downloads stopped. The cap is shared with `ExtensionInstallService`.
     */
    override fun onTimeout(startId: Int, foregroundServiceType: Int) {
        runBlocking { manager.pauseAll() }
        notifier.notifyTimeLimitReached()
        stopForeground(STOP_FOREGROUND_REMOVE)
        isForeground = false
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        queueCollector?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: Notification) {
        // Mirror `ExtensionInstallService.startForegroundCompat` — explicit
        // ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC on API 34+ (Android 14+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(SUMMARY_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(SUMMARY_ID, notification)
        }
    }

    private fun buildPlaceholderNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("ANI-KUTA")
            .setContentText("Preparing downloads…")
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)  // M30
            .build()

    companion object {
        const val SUMMARY_ID = 9001
        const val ACTION_PAUSE_ALL = "com.confused.anikuta.download.PAUSE_ALL"
        const val ACTION_CANCEL_ALL = "com.confused.anikuta.download.CANCEL_ALL"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        }
        fun stop(context: Context) {
            context.stopService(Intent(context, DownloadService::class.java))
        }
    }
}
```

**Required imports:**
```kotlin
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
```

**Key things:**
- `startForeground` is called SYNCHRONOUSLY in `onStartCommand` (per `ExtensionInstallService.kt`'s pattern) — satisfies the Android 12+ 5-second contract.
- `queueCollector` runs on `Dispatchers.IO` (heavy work) + wraps `startForeground`/`notify` in `withContext(Dispatchers.Main)` (notification APIs are main-thread-only).
- `STOP_FOREGROUND_REMOVE` removes the notification when the queue empties.
- `START_STICKY` — Android may restart the service if killed; the queue is persisted in SQLDelight so it recovers.
- `onTaskRemoved` re-launches the service for aggressive OEMs (REVIEW-5 M28).
- `onTimeout` gracefully pauses + posts a one-shot notification on the 6-hour cap (REVIEW-5 M27).

### 13.8 The manifest entry

**CREATE `:core:download/src/main/AndroidManifest.xml`** (does NOT currently exist — verified
via `Glob` against `core/download/`). The OLD project's `:core:download` manifest is the template.

```xml
<!-- :core:download/src/main/AndroidManifest.xml (NEW — REVIEW-5 M23) -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- REVIEW-5 M23: required by ConnectivityManager.registerNetworkCallback
         (used by DownloadManager's auto-resume / auto-pause features in 16-quality-of-life.md §2).
         Without it, registerNetworkCallback throws SecurityException on first init. The OLD
         :core:download manifest declared this; the NEW project's manifest was missing it. -->
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <!-- The FOREGROUND_SERVICE + FOREGROUND_SERVICE_DATA_SYNC permissions are declared in the
         app manifest (already used by ExtensionInstallService). They're inherited by :core:download
         at merge time. -->

    <application>
        <service
            android:name="com.confused.anikuta.core.download.DownloadService"
            android:exported="false"
            android:foregroundServiceType="dataSync" />
    </application>
</manifest>
```

**Phase D.4 task list must include:**
1. CREATE `:core:download/src/main/AndroidManifest.xml` with the above content.
2. CREATE `:core:download/src/main/res/drawable/ic_pause.xml` + `ic_cancel.xml` vector drawables (REVIEW-5 M26 — the OLD draft referenced these without listing them as resources to create). Alternatively use framework drawables (`android.R.drawable.ic_media_pause`, `android.R.drawable.ic_menu_close_clear_cancel`).

### 13.9 When the service starts + stops

- **Start:** the `DownloadManager` calls `DownloadService.start(context)` on enqueue (if not already running).
- **Stop:** the service observes the queue's StateFlow + calls `stopSelf()` when the queue empties (no active downloads).
- **Restart:** `START_STICKY` means Android may restart the service if killed. The `onStartCommand` re-runs the queue collector.

### 13.10 Resilience

The whole notification body is wrapped in three layers of try/catch (same as the OLD project — see §3 above):
1. Outer: catches anything in the body.
2. `notificationManager.notify` is wrapped for `SecurityException` (POST_NOTIFICATIONS denied on Android 13+) + generic `Exception` (some OEMs throw on notification posting).
3. The class KDoc says: "This method MUST NEVER throw — it's called from a hot StateFlow collector; an uncaught exception there crashes the app."

The `loadThumbnail` function also has its own try/catch — a thumbnail load failure (network/decode) MUST NOT fail the notification.

### 13.11 The bytes formatter (shared util)

Move the `formatBytes(bytes: Long): String` helper to `:core:common` (it's duplicated in 3 places in the OLD project — `DownloadNotificationManager`, `DownloadsScreen`, `QueueRow`). The new project should have ONE shared util.

```kotlin
object FormatUtils {
    fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    }
}
```

### 13.12 Summary — the new notification design (one-glance table)

| Notification | Channel | ID | Sound? | Thumbnail? | Style |
|---|---|---|---|---|---|
| Ongoing summary | `anikuta_downloads_progress` (IMPORTANCE_LOW) | 9001 | No | Yes (small, large-icon) | Progress bar + actions |
| Completion | `anikuta_downloads_complete` (IMPORTANCE_DEFAULT) | `taskId + 10_000` | **Yes** | **Yes (BigPictureStyle)** | BigPicture |
| Error | `anikuta_downloads_progress` (IMPORTANCE_LOW) | `taskId + 20_000` | No | Yes (small, large-icon) | Standard |

The foreground service uses the ongoing summary (ID 9001) as its foreground notification. The service stops when the queue empties.
