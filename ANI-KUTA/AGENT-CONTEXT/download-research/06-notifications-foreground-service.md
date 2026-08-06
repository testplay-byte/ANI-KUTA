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

## 12. Summary — what the new project must replicate + improve

| Aspect | Old project | New project recommendation |
|---|---|---|
| Channel | Single `anikuta_downloads` IMPORTANCE_LOW | Same |
| Summary notification | ID 9001, ongoing, progress bar, throttled 800ms | Same + add **Pause/Cancel action buttons** |
| Completion notification | ID `taskId + 10_000`, `stat_sys_download_done`, auto-cancel | Same |
| Error notification | ID `taskId + 20_000`, `stat_notify_error`, auto-cancel, PRIORITY_DEFAULT | Same |
| Tap intent | Opens launcher activity (no deep-link) | Same initially; add deep-link later |
| POST_NOTIFICATIONS | Requested on first Downloads-page open | Same |
| Foreground Service | **NONE** (runs in CoroutineScope) | **ADD** — `DownloadService` with `foregroundServiceType="dataSync"` + `startForeground` with the summary notification |
| Permissions | ACCESS_NETWORK_STATE, POST_NOTIFICATIONS, INTERNET | Same + FOREGROUND_SERVICE + FOREGROUND_SERVICE_DATA_SYNC (already declared in the new app manifest, just unused) |

See `13-implementation-plan.md` §6 for the full Service implementation sketch.
