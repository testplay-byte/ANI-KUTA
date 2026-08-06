# 04 — Storage Paths: Where Files Are Saved (CRITICAL)

> All line references: `core/download/src/main/java/app/confused/anikuta/core/download/DownloadStorageProvider.kt` (570 lines) + `TempDownloadCache.kt` + `app/src/main/res/xml/file_paths.xml`.

## 1. The exact folder structure (canonical form)

Quoted from `DownloadStorageProvider.kt:18-28` (the class KDoc):

```
<USER_FOLDER>/ANIKUTA/
└── downloads/
    └── anime/
        └── <Anime Title [anilistId]>/        ← (now <Title [contentId-safe]>, see §2)
            └── Episode NNN/
                ├── video.<ext>                ← original format from the extension
                └── data/
                    ├── subtitles/             ← ALL subtitle files
                    └── metadata.json          ← cached episode metadata
```

**Concrete example**:
```
content://com.android.externalstorage.documents/tree/primary%3AAniKuta%20Downloads/
└── ANIKUTA/
    └── downloads/
        └── anime/
            └── Jujutsu Kaisen [al-101522]/
                ├── Episode 001/
                │   ├── video.mp4
                │   └── data/
                │       ├── subtitles/
                │       │   ├── English_0.srt
                │       │   └── Spanish_1.ass
                │       └── metadata.json
                ├── Episode 002/
                │   └── ...
                └── Episode 012.5/   ← (special episode — floored to 012 in the folder name!)
                    └── ...
```

## 2. Folder-name builders

### Anime folder name: `<sanitized-title> [<sanitized-contentId>]`

**`DownloadStorageProvider.kt:95-98`**:
```kotlin
fun animeFolderName(anime: DownloadAnimeInfo): String {
    val safeTitle = sanitizeFileName(anime.title.ifBlank { "Unknown" })
    return "$safeTitle [${sanitizeContentIdForFolder(anime.contentId)}]"
}
```

Examples:
- `"Jujutsu Kaisen"` + `"al:154587"` → `"Jujutsu Kaisen [al-154587]"`
- `"Frieren: Beyond Journey's End"` + `"al:154587"` → `"Frieren  Beyond Journey's End [al-154587]"` (the `:` becomes a space)

### Content-ID sanitizer (the `:` → `-` replacement)

**`DownloadStorageProvider.kt:109-111`**:
```kotlin
private fun sanitizeContentIdForFolder(contentId: String): String {
    return contentId.replace(":", "-").replace("/", "-")
}
```

Why not use `sanitizeFileName` (which replaces `:` with space)? Because the `endsWith(suffix)` lookups in `deleteAnime` (line 373-380) and `findEpisodeDirByNumber` (line 411-415) need a STABLE suffix. If `:` were replaced with a space, the suffix would be `[al 154587]` which is fine — but they chose `-` for unambiguity.

### Episode folder name: `Episode NNN` (zero-padded 3-digit, floored)

**`DownloadStorageProvider.kt:113-117`**:
```kotlin
fun episodeFolderName(episode: DownloadEpisodeInfo): String {
    val n = episode.episodeNumber.toInt().coerceAtLeast(0)
    return "Episode %03d".format(n)
}
```

Examples:
- `1.0f` → `"Episode 001"`
- `12.0f` → `"Episode 012"`
- `12.5f` → `"Episode 012"` ⚠️ (special episode .5 is floored — collides with EP 12!)
- `0.0f` → `"Episode 000"`
- `-1.0f` → `"Episode 000"` (coerced to 0)

**Honest note**: the `.5` floor is a minor bug — special episodes (S1.E5 = episode 5.5) would collide with regular episodes. The old project lives with it; the new project should consider `"Episode 012.5"` formatting for non-integer episode numbers.

### Video file name: `video.<ext>`

**`DownloadStorageProvider.kt:120-123`**:
```kotlin
fun videoFileName(videoUrl: String): String {
    val ext = extractExtension(videoUrl)
    return "video.$ext"
}
```

`extractExtension` (line 499-510) whitelists: `mp4, mkv, webm, avi, mov, m4v, ts` — defaults to `mp4` for unknown. Strips the query string first (`url.substringBefore('?')`).

### Subtitle file name: `<sanitized-lang>_<index>.<ext>`

From `HttpDownloader.kt:461-464` (when writing to temp cache):
```kotlin
val safeLang = track.lang.ifBlank { "track" }
    .replace(Regex("[^A-Za-z0-9 ]"), " ").trim().ifBlank { "track" }
val tempSub = File(tempSubsDir, "${safeLang}_$index.$ext")
```

Examples: `English_0.srt`, `Spanish_1.ass`, `track_2.vtt`.

The subtitle extension comes from `subtitleExtension(url)` (line 512-521): whitelist `ass, srt, vtt, ssa, sub`, default `srt`.

## 3. The user's SAF folder — how it's picked + persisted

### The picker

`DownloadSettingsScreen.kt:109-120`:
```kotlin
val folderLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree(),
) { uri ->
    if (uri != null) {
        try {
            preferences.downloadFolderUri().set(uri.toString())
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: Exception) { }
    }
}
```

`ActivityResultContracts.OpenDocumentTree()` shows the system folder picker. The user picks ANY folder (internal storage, SD card, Google Drive — anything SAF supports). The returned `Uri` is a tree URI like:
```
content://com.android.externalstorage.documents/tree/primary%3AAniKuta%20Downloads
```

### The permission persistence

`DownloadStorageProvider.takeFolderPermission(uri)` (line 55-66):
```kotlin
fun takeFolderPermission(treeUri: Uri) {
    try {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(treeUri, flags)
        preferences.downloadFolderUri().set(treeUri.toString())
        DownloadLogger.i("Download folder permission persisted: $treeUri")
    } catch (e: SecurityException) {
        DownloadLogger.e("Cannot persist folder permission (not granted by picker)", e)
        throw e
    }
}
```

Persistable URI permissions survive app restarts AND device reboots. The URI string is stored in `SharedPreferences` under `pref_dl_folder_uri`.

### The `rootTree()` accessor

`DownloadStorageProvider.rootTree()` (line 69-78):
```kotlin
fun rootTree(): DocumentFile? {
    val uriStr = preferences.downloadFolderUri().get()
    if (uriStr.isBlank()) return null
    val tree = DocumentFile.fromTreeUri(context, Uri.parse(uriStr)) ?: return null
    if (!tree.canWrite()) {
        DownloadLogger.w("Download folder no longer writable (revoked?): $uriStr")
        return null
    }
    return tree
}
```

Returns `null` if: no folder set, URI parse fails, OR write permission was revoked. The queue's `enqueue` checks `storage.isFolderReady()` (line 116 of `DefaultDownloadManager`) before accepting a download.

### Display name (for the settings UI)

`DownloadStorageProvider.folderDisplayName(uriString)` (line 536-548) — static helper:
```kotlin
fun folderDisplayName(uriString: String): String? {
    if (uriString.isBlank()) return null
    return try {
        val uri = Uri.parse(uriString)
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val decoded = Uri.decode(docId)
        decoded.substringAfterLast(':').substringAfterLast('/').ifBlank { decoded }
    } catch (e: Exception) { null }
}
```

Extracts the last segment of the document ID — e.g. `primary:AniKuta Downloads` → `"AniKuta Downloads"`.

## 4. Directory creation — `ensureEpisodeDir`

**`DownloadStorageProvider.kt:128-141`**:
```kotlin
fun ensureEpisodeDir(anime: DownloadAnimeInfo, episode: DownloadEpisodeInfo): DocumentFile? {
    val root = rootTree() ?: run {
        DownloadLogger.e("ensureEpisodeDir: no download folder configured")
        return null
    }
    val anikutaDir = ensureDir(root, "ANIKUTA") ?: return null
    val downloadsDir = ensureDir(anikutaDir, "downloads") ?: return null
    val animeDir = ensureDir(downloadsDir, "anime") ?: return null
    val showDir = ensureDir(animeDir, animeFolderName(anime)) ?: return null
    val epDir = ensureDir(showDir, episodeFolderName(episode)) ?: return null
    ensureDir(epDir, "data") ?: return null
    ensureDir(epDir, "data/subtitles") ?: return null
    return epDir
}
```

`ensureDir` (line 462-467):
```kotlin
private fun ensureDir(parent: DocumentFile, name: String): DocumentFile? {
    parent.findFile(name)?.let { return it }
    return parent.createDirectory(name).also {
        if (it == null) DownloadLogger.e("Failed to create directory: $name")
    }
}
```

Idempotent — finds OR creates. Returns null on failure (logged).

## 5. The publish-to-SAF step (atomic move from temp cache)

**`DownloadStorageProvider.publishToUserFolder(...)` — line 169-233** (the most important method):

```kotlin
fun publishToUserFolder(
    anime: DownloadAnimeInfo,
    episode: DownloadEpisodeInfo,
    tempVideoFile: java.io.File,
    tempSubtitlesDir: java.io.File,
    tempMetadataFile: java.io.File,
    videoExtension: String,
): PublishResult {
    val epDir = ensureEpisodeDir(anime, episode)
        ?: return PublishResult.Error("Download folder not configured or not writable")

    try {
        // 1. Copy the video
        val videoName = "video.$videoExtension"
        epDir.findFile(videoName)?.delete()  // overwrite if re-downloading
        val videoTarget = epDir.createFile("video/*", videoName)
            ?: return PublishResult.Error("Failed to create video file in SAF folder")
        context.contentResolver.openOutputStream(videoTarget.uri, "w")?.use { out ->
            tempVideoFile.inputStream().use { it.copyTo(out) }
        } ?: return PublishResult.Error("Failed to open video output stream in SAF folder")

        // 2. Copy subtitles
        val subtitleUris = mutableListOf<String>()
        val subDir = epDir.findFile("data")?.findFile("subtitles")
        if (subDir != null && tempSubtitlesDir.exists()) {
            tempSubtitlesDir.listFiles()?.forEach { tempSub ->
                if (!tempSub.isFile) return@forEach
                subDir.findFile(tempSub.name)?.delete()
                val target = subDir.createFile("application/octet-stream", tempSub.name)
                if (target != null) {
                    context.contentResolver.openOutputStream(target.uri, "w")?.use { out ->
                        tempSub.inputStream().use { it.copyTo(out) }
                    }
                    subtitleUris.add(target.uri.toString())
                }
            }
        }

        // 3. Copy metadata.json
        if (tempMetadataFile.exists()) {
            val dataDir = epDir.findFile("data") ?: ensureDir(epDir, "data")
            if (dataDir != null) {
                dataDir.findFile("metadata.json")?.delete()
                val metaTarget = dataDir.createFile("application/json", "metadata.json")
                if (metaTarget != null) {
                    context.contentResolver.openOutputStream(metaTarget.uri, "w")?.use { out ->
                        tempMetadataFile.inputStream().use { it.copyTo(out) }
                    }
                }
            }
        }

        val videoSize = tempVideoFile.length()
        return PublishResult.Success(
            videoUri = videoTarget.uri.toString(),
            subtitleUris = subtitleUris,
            sizeBytes = videoSize,
        )
    } catch (e: Exception) {
        return PublishResult.Error("Failed to move download to folder: ${e.message ?: e.javaClass.simpleName}")
    }
}
```

**Critical design points**:
- Video MIME is `video/*` — SAF providers usually auto-detect; specifying `video/*` lets the provider pick the actual MIME.
- Subtitle MIME is `application/octet-stream` — generic, since `.ass`/`.srt`/`.vtt` don't have universally-recognized MIMEs in SAF.
- Metadata MIME is `application/json`.
- Existing files are deleted before creating new ones (handles re-downloads).
- The video URI returned is a **content:// URI** — playable by MPV via `resolveUrlForMpv` (see `10-player-integration.md`).

## 6. The temp cache (internal-cache-first)

**File**: `core/download/src/main/java/app/confused/anikuta/core/download/TempDownloadCache.kt` (93 lines)

```kotlin
class TempDownloadCache(context: Context) {
    private val rootDir = File(context.cacheDir, "anikuta_downloads").also { it.mkdirs() }

    fun taskDir(taskId: Long): File =
        File(rootDir, taskId.toString()).also { it.mkdirs() }

    fun videoFile(taskId: Long, extension: String): File =
        File(taskDir(taskId), "video.$extension")

    fun subtitlesDir(taskId: Long): File =
        File(taskDir(taskId), "subtitles").also { it.mkdirs() }

    fun metadataFile(taskId: Long): File =
        File(taskDir(taskId), "metadata.json")

    fun cleanupTask(taskId: Long) { ... }  // deletes the whole taskDir

    fun cleanupStale() { ... }  // deletes all taskDirs (called on app startup)
}
```

Layout (under `context.cacheDir`, NOT the user's SAF folder):
```
<cacheDir>/anikuta_downloads/
└── <taskId>/
    ├── video.<ext>          ← temp video file (deleted on completion/failure)
    ├── subtitles/
    │   └── <lang>_<i>.<ext>
    ├── metadata.json
    ├── resume.json          ← (only for Advanced method — chunk progress)
    ├── chunk_0.part         ← (only for Advanced method)
    ├── chunk_1.part
    └── ...
```

**Why internal cache first?** Per the class KDoc:
> "the video downloads to the app's internal cache directory first (fast, private, no SAF per-byte overhead). Only after the download is fully validated (correct content-type, non-trivial file size, not corrupt) is it copied to the user's selected SAF folder."

Benefits:
1. **No pollution** — partial/corrupt downloads never appear in the user's folder.
2. **Performance** — writing to internal cache is faster than SAF per-byte writes (no ContentResolver round-trips per buffer flush).
3. **Validation** — can inspect bytes (Content-Type, size, magic bytes) BEFORE committing.
4. **Atomicity** — the user's folder only ever contains complete, valid files.

**Cleanup**:
- `cleanupTask(taskId)` — called in `HttpDownloader.download`'s `finally` block (line 161-165). Always runs, success or failure.
- `cleanupStale()` — called once at app startup (from `DownloadModule.kt:45`: `single { TempDownloadCache(get<Context>()).also { it.cleanupStale() } }`). Cleans up dirs left by a previous crash.

## 7. FileProvider configuration

**File**: `app/src/main/res/xml/file_paths.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <cache-path name="updates" path="updates/" />
    <cache-path name="cache" path="." />
</paths>
```

**Important**: this FileProvider is configured for the **cache directory only** — used by the **app update downloader** (to share APK files with the system installer), NOT for download playback.

**The download system does NOT use a FileProvider for sharing video URIs with MPV**. Instead, MPV plays the SAF content:// URI directly via `resolveUrlForMpv` (the player opens the content URI via Android's ContentResolver and feeds the file descriptor to MPV). This is why `DownloadStorageProvider.publishToUserFolder` returns content:// URIs.

The FileProvider declared in `app/src/main/AndroidManifest.xml:90-98`:
```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

So the FileProvider is wired but only for cache-path (APK install). Downloads don't need it.

## 8. Deletion

### Delete a single episode
**`DownloadStorageProvider.deleteEpisode(anime, episode)` — line 329-339**:
```kotlin
fun deleteEpisode(anime: DownloadAnimeInfo, episode: DownloadEpisodeInfo): Boolean {
    val epDir = findEpisodeDir(anime, episode) ?: return false
    val ok = epDir.delete()
    if (ok) {
        cleanupEmptyAnimeFolder(anime)  // auto-delete the anime folder if it's now empty
    }
    return ok
}
```

Called by `DefaultDownloadManager.deleteDownload(taskId)` (line 142-148) — also calls `queue.removeCompleted(taskId)`.

### Delete all episodes of an anime
**`DownloadStorageProvider.deleteAnime(contentId, title)` — line 373-380**:
```kotlin
fun deleteAnime(contentId: String, animeTitle: String): Boolean {
    val root = rootTree() ?: return false
    val animeDir = findAnimeDir(contentId) ?: return false
    val ok = animeDir.delete()
    return ok
}
```

`findAnimeDir(contentId)` (line 390-398) scans the `anime/` folder for a directory whose name ends with `[sanitized-contentId]`. Called by `DefaultDownloadManager.deleteAnimeDownloads(contentId)` (line 150-159).

### Auto-cleanup of empty anime folders
**`cleanupEmptyAnimeFolder(anime)` — line 345-364**: after deleting an episode, checks if the anime folder has any remaining files/dirs. If empty, deletes it too. Keeps the user's folder clean.

## 9. Source-switching filesystem fallback

**`findEpisodeDirByNumber(contentId, episodeNumber)` — line 411-415**:
```kotlin
fun findEpisodeDirByNumber(contentId: String, episodeNumber: Float): DocumentFile? {
    val animeDir = findAnimeDir(contentId) ?: return null
    val epFolderName = "Episode %03d".format(episodeNumber.toInt().coerceAtLeast(0))
    return animeDir.findFile(epFolderName)?.takeIf { it.isDirectory }
}
```

Used by `DefaultDownloadManager.isEpisodeDownloaded` / `getDownloadedVideoUri` / `getDownloadedSubtitleUris` as a **filesystem fallback** when no in-memory task matches (e.g. after a source switch — the new source has a different `episodeUrl` but the same `episodeNumber`).

This is the "source-switching fix" — the old identity was `anilistId + episodeUrl`, which broke when the user switched extension sources. The new identity is `contentId + episodeNumber`, which survives source switches.

## 10. The `metadata.json` schema

**`DownloadStorageProvider.kt:561-570`**:
```kotlin
@Serializable
data class EpisodeMetadataCache(
    val contentId: String,
    val animeTitle: String,
    val episodeNumber: Float,
    val episodeName: String,
    val videoUrl: String,
    val downloadedAt: Long,
    val sourceId: Long,
)
```

Human-readable JSON (prettyPrint = true). Purpose: a user browsing the folder with a file manager can identify the episode. The cache is informational-only — overwritten on re-download. The old `anilistId: Int` field was replaced by `contentId: String` in Phase 6 (ADR-050); old on-disk files parse cleanly because of `ignoreUnknownKeys = true`.

Written by `HttpDownloader.writeMetadataToCache` (line 481-502) — to the temp cache first, then `publishToUserFolder` copies it to `data/metadata.json`.

## 11. Summary — what the new project must replicate

1. **SAF folder picker** (`ActivityResultContracts.OpenDocumentTree`) + `takePersistableUriPermission` + store the URI string in prefs.
2. **Folder structure** `<root>/ANIKUTA/downloads/anime/<Title [contentId-safe]>/Episode NNN/{video.<ext>, data/{subtitles/, metadata.json}}`.
3. **Internal-cache-first** download pipeline — temp dir under `context.cacheDir/anikuta_downloads/<taskId>/`.
4. **Atomic publish** from temp → SAF via `DocumentFile.createFile` + `ContentResolver.openOutputStream` + `copyTo`.
5. **Source-independent identity** (contentId + episodeNumber) so downloads survive extension source switches.
6. **Auto-cleanup of empty anime folders** after episode deletion.
7. **Filesystem fallback** (`findEpisodeDirByNumber`) for offline lookup when no in-memory task matches.
8. **No FileProvider for video playback** — MPV plays the SAF content:// URI directly. (FileProvider is only for the APK update installer.)
9. **Cleanup of stale temp dirs on app startup** (`TempDownloadCache.cleanupStale()`).

## 12. Honest notes

- The `Episode NNN` folder name uses `.toInt()` (floor) — `.5` specials collide with their integer counterparts. Worth fixing in the new project (e.g. `"Episode 012.5"` for non-integers).
- The `subtitleExtension` whitelist includes `sub` but not `ttml` — some CDNs serve TTML subtitles; they'd default to `.srt`. Minor.
- `ensureEpisodeDir` creates the `data/subtitles/` folder even if there are no subtitles — minor wasted inode but harmless.
- `publishToUserFolder` returns `PublishResult.Error` if video file creation fails, but it has already potentially created some files in the episode dir. The caller marks the task ERROR, but the partial episode dir is left on disk. The next re-download attempt overwrites cleanly, but if the user gives up, the partial dir lingers. Could be improved by tracking created files + rolling back on failure.
- SAF's `DocumentFile.listFiles()` is slow — each call hits the ContentResolver. The code calls it multiple times per operation (`findFile("ANIKUTA")?.findFile("downloads")?.findFile("anime")?.findFile(...)?.findFile(...)`). For large libraries this could be slow. The old project lives with it; the new project could cache the directory tree.
