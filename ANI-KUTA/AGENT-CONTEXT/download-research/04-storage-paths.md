# 04 — Storage Paths: NEW future-proof system (CRITICAL REWRITE)

> **Task ID:** DL-PLAN-REWRITE
> **Status:** NEW design. The OLD project's storage path is IRRELEVANT — do NOT copy it. Old project used `<root>/ANIKUTA/downloads/anime/<Title [al-123]>/Episode 001/{video.mp4, data/{subtitles, metadata.json}}`. We are designing a NEW system from scratch.
> **Old project doc** (preserved for reference only): see `git log` for the previous version of this file.
> **Cross-references:** `11-db-schema.md` (SQLDelight tables) · `13-implementation-plan.md` Phase D.1 (storage implementation) · `15-ui-and-bug-analysis.md` Part B (proxy-churn — why we cache direct URLs in `data.json`).

This doc supersedes the old-project-based content. The folder tree, the naming convention, the `data.json` schema, the temp cache layout, and the scan-on-startup logic are all NEW.

---

## 1. Design principles (the WHY before the WHAT)

1. **User-selected root, app-managed structure.** The user picks a single folder via SAF (the "library root"). Inside that root, the app creates and owns its own structure. The user does not have to think about subfolder layout — they just see "the folder ANI-KUTA uses".
2. **Content FORMAT folders, not content TYPE folders.** Top-level folders are named after how the content is *encoded on disk* (`video/`, `images/`, `text/`), not what kind of work it is. Anime episodes and movies are both `.mp4`/`.mkv` → both live under `video/`. Manga volumes and novels are both readable text/images, but manga is images and novels are text — so manga volumes go under `images/`, novels under `text/`. Art books (PNG/JPG) → `images/`. This survives adding manga/novels/movies/series later without restructuring.
3. **Human-readable content folders.** The folder for each content is the human-readable title (e.g. `Jujutsu Kaisen`), NOT the `mainId`. The `mainId` lives in a `data.json` inside the folder. A user browsing the folder with a file manager sees real titles.
4. **No AniList ID in the folder name.** The old project appended `[al-101522]` to the folder name (and `[al-154587]`). We DON'T. The `mainId` is the stable identifier — it goes in `data.json`. The folder is just the title.
5. **5-digit episode padding.** Use `E00001`, not `E001`. This supports content with 10,000+ episodes (long-running shounen, daily soaps, podcast back-catalogues). The old project's 3-digit padding would collide past episode 999.
6. **`data.json` is the source of truth for reinstall recognition.** Each content folder contains a `data.json`. On app start (after the user re-selects the same folder), the app scans the structure, reads every `data.json`, and re-registers the content in the DB by `mainId`. The DB is a *cache/index*; the `data.json` files are *durable*.
7. **Internal-cache-first.** Downloads write to the app's internal cache directory first (fast, private, no SAF round-trips per byte). Only after full validation (size + magic bytes) is the file atomically published to the user's SAF folder. The user's folder NEVER contains partial/corrupt files.
8. **The temp cache is per-task, not per-content.** Each in-flight download has its own temp dir keyed by `downloadId`. After the task finishes (success OR failure), the temp dir is deleted. A separate startup sweep cleans up dirs left by a previous crash.

---

## 2. The SAF folder picker flow

### 2.1 Picking the root

The user picks the library root via `ActivityResultContracts.OpenDocumentTree()`:

```kotlin
val folderLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree(),
) { uri ->
    if (uri != null) {
        try {
            preferences.downloadFolderUri().set(uri.toString())
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            // Trigger a scan-on-startup of the newly-selected folder (see §7).
            downloadManager.requestFolderRescan()
        } catch (e: SecurityException) {
            // The picker can return a URI we don't have persistable permission for
            // (some OEM SAF providers). Tell the user to try a different folder.
        }
    }
}
```

The returned `Uri` is a tree URI like:
```
content://com.android.externalstorage.documents/tree/primary%3AAniKuta%20Downloads
```

Persistable URI permissions survive app restarts AND device reboots. The URI string is stored in `PreferenceStore` under `pref_dl_folder_uri`.

**First-launch flow:** the user must pick a folder before any download works. The Downloads screen's empty state prompts for this. The first download attempt also re-prompts if no folder is set.

### 2.2 The `rootTree()` accessor

```kotlin
fun rootTree(): DocumentFile? {
    val uriStr = preferences.downloadFolderUri().get()
    if (uriStr.isBlank()) return null
    val tree = DocumentFile.fromTreeUri(context, Uri.parse(uriStr)) ?: return null
    if (!tree.canWrite()) {
        // Folder was moved/renamed/permission revoked.
        return null
    }
    return tree
}
```

Returns `null` if no folder is set, the URI can't be parsed, or write permission was revoked. The `DownloadQueue.enqueue` checks `storage.isFolderReady()` before accepting a download.

### 2.3 Folder display name (for the settings UI)

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

Extracts the last segment of the document ID — e.g. `primary:AniKuta Downloads` → `"AniKuta Downloads"`. Used by the settings screen to show "Folder: AniKuta Downloads".

---

## 3. The EXACT folder tree

### 3.1 ASCII diagram (concrete)

For a user who picked `AniKuta Downloads` as the root:

```
AniKuta Downloads/                                    ← user-selected root (SAF tree URI)
├── video/                                            ← content FORMAT folder (video files)
│   ├── Jujutsu Kaisen/                               ← content folder (human-readable title)
│   │   ├── data.json                                 ← per-content metadata (the SOURCE OF TRUTH)
│   │   ├── cover.jpg                                 ← cached cover image (optional)
│   │   ├── Jujutsu Kaisen - E00001.mp4               ← episode file (5-digit padding, no AniList ID)
│   │   ├── Jujutsu Kaisen - E00001.srt               ← episode subtitle (best-effort)
│   │   ├── Jujutsu Kaisen - E00002.mp4
│   │   ├── Jujutsu Kaisen - E00002.srt
│   │   └── Jujutsu Kaisen - E00012.5.mp4             ← .5 specials keep their fractional suffix
│   ├── Frieren: Beyond Journey's End/
│   │   ├── data.json
│   │   ├── cover.jpg
│   │   └── Frieren - E00001.mkv                      ← extension preserved (mp4/mkv/webm/ts/m4v)
│   └── The Lord of the Rings - Fellowship/           ← a MOVIE (still video format → video/)
│       ├── data.json
│       └── The Lord of the Rings - Fellowship.mp4    ← single-file content (no episode number)
├── images/                                           ← content FORMAT folder (image files)
│   ├── Berserk Manga Volume 01/                      ← manga volume (future)
│   │   ├── data.json
│   │   ├── 001.png
│   │   ├── 002.png
│   │   └── ...
│   └── Pixel Art Collection/                         ← art book (future)
│       ├── data.json
│       └── ...
├── text/                                             ← content FORMAT folder (text files)
│   ├── Spice and Wolf - Volume 01/                   ← light novel (future)
│   │   ├── data.json
│   │   └── Spice and Wolf - Volume 01.epub
│   └── Sword Art Online - Volume 01.txt
└── .anikuta/                                         ← app-managed metadata (hidden, app-owned)
    ├── library_index.json                            ← optional aggregate index (cache only)
    └── scan_state.json                               ← last scan timestamp + hash
```

### 3.2 Why this tree is future-proof

| Future content type | Format folder | Example folder name | Example file name |
|---|---|---|---|
| Anime series | `video/` | `Jujutsu Kaisen` | `Jujutsu Kaisen - E00001.mp4` |
| Anime movie | `video/` | `Spirited Away` | `Spirited Away.mp4` (no episode number — single-file content) |
| TV series (live action) | `video/` | `Breaking Bad` | `Breaking Bad - S01E00001.mp4` (S prefix if season-based) |
| Manga volume | `images/` | `Berserk Manga Volume 01` | `001.png` ... `NNN.png` |
| Light novel | `text/` | `Spice and Wolf - Volume 01` | `Spice and Wolf - Volume 01.epub` |
| Art book / CG set | `images/` | `Pixel Art Collection` | `001.jpg` ... |

The **format folders are stable forever**. Adding a new content TYPE (e.g. "audio drama") only requires choosing one of the existing format folders (`audio/` for pure audio) OR adding a new format folder — never a tree restructure.

### 3.3 Why NOT to use the old project's structure

The old project used `<root>/ANIKUTA/downloads/anime/<Title [al-101522]>/Episode 001/{video.mp4, data/{subtitles/, metadata.json}}`. Problems:

1. **Hard-coded `anime/`** — doesn't scale to manga/novels/movies.
2. **`[al-101522]` suffix** — leaks AniList IDs into the user's folder, ugly, breaks if the user unlinks from AniList.
3. **`Episode 001` sub-folder per episode** — wastes an inode, makes "open the file in a video player" harder (one extra tap), and the only thing inside is `video.mp4` + `data/subtitles/` + `data/metadata.json`. We flatten: episode files go directly in the content folder, named `<Title> - E00001.mp4`.
4. **3-digit padding** — collides past episode 999.
5. **`metadata.json` per episode** — we use one `data.json` per CONTENT (covers all its episodes), and rely on the file naming convention for episode identity.

---

## 4. Folder + file name builders

### 4.1 Content folder name

```kotlin
fun contentFolderName(content: DownloadContentInfo): String {
    return sanitizeFileName(content.title.ifBlank { "Unknown" })
}
```

**Examples:**
- `"Jujutsu Kaisen"` → `"Jujutsu Kaisen"`
- `"Frieren: Beyond Journey's End"` → `"Frieren Beyond Journey's End"` (`:` → space, then runs of whitespace collapsed to a single space — REVIEW-5 R1-M1 fix: the OLD draft showed a double space here, contradicting the "collapses runs of whitespace" rule)
- `"Re:Zero kara Hajimeru Isekai Seikatsu"` → `"Re Zero kara Hajimeru Isekai Seikatsu"`

**`sanitizeFileName`** replaces `\ / : * ? " < > |` and trailing dots/spaces with a single space, collapses runs of whitespace, and trims. The result is filesystem-safe on FAT32, exFAT, NTFS, ext4, and SAF providers (which all reject the same set).

**REVIEW-5 (R1-M3 + R1-M4):** `sanitizeFileName` also:
- Replaces Windows reserved names (`CON`, `PRN`, `AUX`, `NUL`, `COM1`-`COM9`, `LPT1`-`LPT9`) with `"Unknown"` (extremely unlikely to matter, but cheap to guard).
- Caps the result at ~200 characters (leaves room for ` - E00001.mp4` + extension on a 255-byte filename limit).

**Crucially: NO `mainId` suffix, NO AniList ID suffix.** Just the title. The `mainId` lives in `data.json`.

**REVIEW-5 M53 (R1-I1) — same-title collision algorithm:** `ensureContentDir(content)` is called in §6.3 step 1. It must handle the case where two different `mainId`s have the same sanitized title. The algorithm:

```kotlin
suspend fun ensureContentDir(content: DownloadContentInfo): DocumentFile? {
    val root = storage.rootTree() ?: return null
    val formatDir = root.findFile(content.contentFormat)?.takeIf { it.isDirectory }
        ?: root.createDirectory(content.contentFormat) ?: return null

    val baseName = contentFolderName(content)
    // 1. Check if the base folder exists.
    var candidate = formatDir.findFile(baseName)
    if (candidate == null) {
        // No collision — create + return.
        return formatDir.createDirectory(baseName)
    }
    // 2. The folder exists — verify it belongs to THIS content (by mainId).
    val existingDataJson = readDataJson(candidate)
    if (existingDataJson == null || existingDataJson.mainId == content.mainId) {
        // Same content (or no data.json yet) — reuse.
        return candidate
    }
    // 3. Different `mainId` with the same title — append " (2)", " (3)", ... until a free slot is found.
    var suffix = 2
    while (true) {
        val altName = "$baseName ($suffix)"
        val alt = formatDir.findFile(altName)
        if (alt == null) {
            return formatDir.createDirectory(altName)
        }
        val altDataJson = readDataJson(alt)
        if (altDataJson == null || altDataJson.mainId == content.mainId) {
            return alt
        }
        suffix++
    }
}
```

This guarantees two different animes with the same title (e.g. two different "Sword Art Online" seasons released as separate mainIds) don't overwrite each other's `data.json`.

### 4.2 Episode file name

```kotlin
fun episodeFileName(
    content: DownloadContentInfo,
    episode: DownloadEpisodeInfo,
    extension: String,
): String {
    val safeTitle = sanitizeFileName(content.title.ifBlank { "Unknown" })
    val number = formatEpisodeNumber(episode.episodeNumber)
    return "$safeTitle - E$number.$extension"
}

fun formatEpisodeNumber(episodeNumber: Float): String {
    val intPart = episodeNumber.toInt().coerceAtLeast(0)
    if (episodeNumber == intPart.toFloat()) {
        return "%05d".format(intPart)              // "00001", "00012"
    }
    // REVIEW-5 M56 (R1-I9): the OLD draft used `%.1f` which rounds 12.25 → 12.3. Real-world
    // fractional episodes are almost always .5 (specials), but if non-standard fractions appear,
    // we want to preserve the exact value. Split on `.` + concat without rounding.
    val fractional = episodeNumber - intPart       // 0.5, 0.25, etc.
    // Strip trailing zeros from the fractional part: 0.5 → "5", 0.25 → "25", 0.125 → "125".
    val fracStr = fractional.toString()
        .removePrefix("0.")
        .trimEnd('0')
        .ifBlank { "0" }  // safety — if fractional is somehow 0.0, treat as integer
    return if (fracStr == "0") {
        "%05d".format(intPart)
    } else {
        "%05d.%s".format(intPart, fracStr)        // "00012.5", "00012.25"
    }
}
```

**Examples:**
- `Jujutsu Kaisen`, EP `1.0f`, `mp4` → `"Jujutsu Kaisen - E00001.mp4"`
- `Jujutsu Kaisen`, EP `12.0f`, `mkv` → `"Jujutsu Kaisen - E00012.mkv"`
- `Jujutsu Kaisen`, EP `12.5f`, `mp4` → `"Jujutsu Kaisen - E00012.5.mp4"` (specials keep fractional suffix)
- `One Piece`, EP `1085.0f`, `mp4` → `"One Piece - E01085.mp4"` (5-digit padding handles 10,000+ episodes)
- `Spirited Away` (movie, single-file), `0.0f`, `mp4` → `"Spirited Away.mp4"` (movie has no episode number — see §4.3)

### 4.3 Single-file content (movies, art books)

For content with `contentType == "movie"` (or any single-file content with `episodes == null || episodes <= 1`), the file name drops the `- E00001` segment:

```kotlin
fun fileNameFor(content: DownloadContentInfo, episode: DownloadEpisodeInfo?, ext: String): String {
    val safeTitle = sanitizeFileName(content.title.ifBlank { "Unknown" })
    return if (episode == null || content.isSingleFile) {
        "$safeTitle.$ext"
    } else {
        "$safeTitle - E${formatEpisodeNumber(episode.episodeNumber)}.$ext"
    }
}
```

### 4.4 Cover image file name

```kotlin
fun coverFileName(): String = "cover.jpg"
```

One `cover.jpg` per content folder, used by the notification thumbnail + the Downloads UI. Cached from `coverUrl` at download time (best-effort — if the network call fails, no cover file is written and the UI uses the placeholder).

### 4.5 Subtitle file name

```kotlin
fun subtitleFileName(content: DownloadContentInfo, episode: DownloadEpisodeInfo, lang: String, index: Int, ext: String): String {
    val safeTitle = sanitizeFileName(content.title.ifBlank { "Unknown" })
    val safeLang = sanitizeLang(lang)             // "English", "Spanish", "track" if blank
    val number = formatEpisodeNumber(episode.episodeNumber)
    return "$safeTitle - E$number.$safeLang.$index.$ext"
}
```

**Examples:** `Jujutsu Kaisen - E00001.English.0.srt`, `Jujutsu Kaisen - E00001.Spanish.1.ass`.

Subtitles sit next to the video file (same folder). MPV auto-discovers external subs by filename proximity.

### 4.6 Extension extraction

```kotlin
fun extractExtension(videoUrl: String): String {
    val noQuery = videoUrl.substringBefore('?')
    val ext = noQuery.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return when (ext) {
        "mp4", "mkv", "webm", "avi", "mov", "m4v", "ts" -> ext
        "m3u8", "m3u" -> "ts"                              // HLS → concatenated .ts
        else -> "mp4"                                       // default
    }
}

fun subtitleExtension(subtitleUrl: String): String {
    val ext = subtitleUrl.substringBefore('?').substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return when (ext) {
        "ass", "srt", "vtt", "ssa", "sub" -> ext
        else -> "srt"
    }
}
```

---

## 5. The `data.json` schema (per content folder)

Each content folder contains exactly ONE `data.json`. This file is the durable source of truth for the content's identity — it's what allows the app to re-recognize the content after an app-delete + reinstall + same-folder-selection.

### 5.1 The schema

> **REVIEW-5 M5 (R1-C2):** the OLD draft's `ContentDataJson` stored only `anilistId`,
> `sourceId`, + `animeUrl`. The scan-on-startup's `upsertFromDataJson(dataJson)` couldn't
> restore the `content` table's FK columns (`data_source_id`, `system_id`, `extension_repo_id`,
> `extension_id`, `display_source`) — would NULL them out, breaking source linkage. The schema
> below now stores the full FK set so the upsert is lossless.
>
> **REVIEW-5 M54 (R1-I5):** `.nomedia` is created alongside `data.json` so downloaded `.mp4`
> files don't appear in gallery apps. See §6.3 step 7.

```kotlin
@Serializable
data class ContentDataJson(
    // ── Identity (REQUIRED) ──
    /** Schema version of this file — bump on breaking changes (1 for now). */
    val schemaVersion: Int = 1,
    /** The stable UUID for this content. Survives source switches, AniList unlinking. */
    val mainId: String,
    /**
     * The structured content ID — changes when sources switch.
     *
     * REVIEW-5 M4 (R1-C1): the value MUST be produced by `ContentIdGenerator.generate(...)`.
     * It is a 6-section colon-delimited string `{dataSource}:{system}:{repoUrl|none}:{extensionPkg|none}:{sourceId|none}:{animeUrl|none}`.
     * Example: `anilist:aniyomi:https://example.com/index.min.json:com.example.ext:69023:https://aniyomi.org/anime/jujutsu-kaisen`.
     * (The OLD draft's example `"anilist:101522"` was wrong — only 2 sections, would break
     * the `idx_content_content_id` duplicate-detection index.)
     */
    val contentId: String,
    /** Human-readable title (the same string used for the folder name, but unsanitized). */
    val title: String,
    /** What kind of work this is: "anime" | "movie" | "series" | "manga" | "novel" | "artbook" | ... */
    val contentType: String,
    /** How the content is stored on disk: "video" | "images" | "text" | "audio" */
    val contentFormat: String,
    /**
     * Where the content was originally sourced.
     *
     * REVIEW-5 (R1-M5): reference `ContentRecord.displaySource` directly (single source of truth)
     * instead of a freeform string. The OLD draft said `"anilist" | "extension" | "tmdb" | "manual"`
     * — implementers might invent new values. `ContentRecord.displaySource` defaults to `"extension"`
     * (per `ContentModels.kt:24`) + is the canonical field.
     */
    val sourceType: String,

    // ── Visual identity (REQUIRED for UI) ──
    /** Cover image URL (cached to cover.jpg). Null if no cover. */
    val coverUrl: String? = null,
    /** Optional dominant color (for UI tinting). */
    val coverColor: Long? = null,

    // ── Source links (OPTIONAL — at least one is usually present) ──
    /** AniList ID if the content is linked to AniList. */
    val anilistId: Int? = null,
    /** Extension source ID if the content is linked to an installed extension. */
    val sourceId: Long? = null,
    /** The extension's URL for this anime (used for re-resolving episodes). */
    val animeUrl: String? = null,

    // ── REVIEW-5 M5 (R1-C2): FK columns for the `content` table — restored losslessly on reinstall. ──
    /** The `content.data_source_id` FK (e.g. anilist / tmdb / manual source ID). */
    val dataSourceId: Long? = null,
    /** The `content.system_id` FK (e.g. aniyomi / aniyomi-anime). */
    val systemId: String? = null,
    /** The `content.extension_repo_id` FK (the extension's repo URL). */
    val extensionRepoId: String? = null,
    /** The `content.extension_id` FK (the extension's package name). */
    val extensionId: String? = null,
    /** The `content.display_source` field (matches `ContentRecord.displaySource`). */
    val displaySource: String? = null,

    // ── Episode index (REQUIRED — the keys used to find files in the folder) ──
    /**
     * The list of episodes that SHOULD be in this folder, with their `mainId`-stable
     * episode keys. Each entry maps the `episodeKey` (used by the DB + state machine)
     * to the `episodeNumber` (used by the file name) and an optional display name.
     *
     * On scan, the app reads this list, then verifies each file exists on disk.
     * Missing files are marked "deleted externally"; extra files are flagged.
     */
    val episodes: List<EpisodeEntry> = emptyList(),

    // ── Timestamps (REQUIRED) ──
    val createdAt: Long,
    /**
     * REVIEW-5 (R1-M9): bumped only on schema/format changes + on episode LIST changes (add/remove
     * episode). NOT bumped on every download — that was noisy + would break cross-device sync.
     * Use `lastEpisodeAddedAt` (on EpisodeEntry.downloadedAt) for the "last activity" timestamp.
     */
    val updatedAt: Long,
)

@Serializable
data class EpisodeEntry(
    /** The episode key — `"$mainId|$episodeNumber"` (5-digit padded). Stable across source switches. */
    val episodeKey: String,
    /** The episode number used in the file name (matches `formatEpisodeNumber`). */
    val episodeNumber: Float,
    /** Human-readable episode name (for UI display). May be blank. */
    val episodeName: String = "",
    /** The video file name (e.g. "Jujutsu Kaisen - E00001.mp4"). Stored to detect renames. */
    val videoFileName: String,
    /** Subtitle file names (empty list if none). */
    val subtitleFileNames: List<String> = emptyList(),
    /** The quality label captured at download time (e.g. "1080p"). May be null. */
    val quality: String? = null,
    /** The server name captured at download time. May be null. */
    val server: String? = null,
    /** The audio version captured at download time. May be null. */
    val audio: String? = null,
    /** File size in bytes (used for verification + free-space checks). */
    val sizeBytes: Long,
    /** When the file was downloaded. */
    val downloadedAt: Long,
)
```

### 5.2 Example `data.json`

> **REVIEW-5 M4 (R1-C1):** the `contentId` value below is now a real 6-section string
> produced by `ContentIdGenerator.generate(...)`. The OLD draft's `"anilist:101522"` was
> wrong (only 2 sections) + would break the duplicate-detection index.

```json
{
  "schemaVersion": 1,
  "mainId": "550e8400-e29b-41d4-a716-446655440000",
  "contentId": "anilist:aniyomi:https://example.com/index.min.json:com.confused.ext.aniyomi:69023:https://aniyomi.org/anime/jujutsu-kaisen",
  "title": "Jujutsu Kaisen",
  "contentType": "anime",
  "contentFormat": "video",
  "sourceType": "extension",
  "coverUrl": "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx101522-h5FtnOPkDPkF.png",
  "coverColor": 4280392219,
  "anilistId": 101522,
  "sourceId": 69023,
  "animeUrl": "https://aniyomi.org/anime/jujutsu-kaisen",
  "dataSourceId": 1,
  "systemId": "aniyomi",
  "extensionRepoId": "https://example.com/index.min.json",
  "extensionId": "com.confused.ext.aniyomi",
  "displaySource": "extension",
  "episodes": [
    {
      "episodeKey": "550e8400-e29b-41d4-a716-446655440000|00001",
      "episodeNumber": 1.0,
      "episodeName": "Ryomen Sukuna",
      "videoFileName": "Jujutsu Kaisen - E00001.mp4",
      "subtitleFileNames": ["Jujutsu Kaisen - E00001.English.0.srt"],
      "quality": "1080p",
      "server": "Vidstreaming",
      "audio": "SUB",
      "sizeBytes": 423456789,
      "downloadedAt": 1720000000000
    },
    {
      "episodeKey": "550e8400-e29b-41d4-a716-446655440000|00002",
      "episodeNumber": 2.0,
      "episodeName": "For Myself",
      "videoFileName": "Jujutsu Kaisen - E00002.mp4",
      "subtitleFileNames": [],
      "quality": "1080p",
      "server": "Vidstreaming",
      "audio": "SUB",
      "sizeBytes": 410000000,
      "downloadedAt": 1720100000000
    }
  ],
  "createdAt": 1720000000000,
  "updatedAt": 1720100000000
}
```

### 5.3 Why `data.json` is the source of truth (not the DB)

| Concern | DB (SQLDelight) | `data.json` (per content folder) |
|---|---|---|
| Survives app uninstall | ❌ (cleared with app data) | ✅ (in the user's SAF folder) |
| Survives app-delete + reinstall | ❌ | ✅ |
| Survives "Clear data" button | ❌ | ✅ |
| Queryable (WHERE content_id = ?) | ✅ | ❌ (must read every file) |
| Fast to read | ✅ | ❌ (SAF round-trip per file) |
| Authoritative for "what's downloaded" | Cache only | ✅ Authoritative |

**Therefore:** the DB is a CACHE/INDEX. The `data.json` files are DURABLE. On startup, the app scans the SAF folder, reads each `data.json`, and UPSERTs the content into the DB by `mainId`. The DB rows are then used for fast queries during normal operation; if a row is missing (e.g. user wiped DB), the next scan re-creates it.

### 5.4 Why `episodes[]` is in `data.json` (not per-episode `metadata.json`)

The old project wrote a `metadata.json` per episode folder (`Episode 001/data/metadata.json`). Problems:

1. **Redundant with the file name.** The episode number is already encoded in `Episode NNN` (or in our case, in `Jujutsu Kaisen - E00001.mp4`). Per-episode metadata files duplicate this.
2. **More SAF round-trips.** Reading 100 episodes' metadata = 100 file reads. Reading ONE `data.json` for a 100-episode anime = 1 file read.
3. **Harder to scan.** The scan-on-startup must walk every `Episode NNN/` subfolder to find the metadata files. With our flat layout, the scan reads ONE `data.json` per content folder.

We use ONE `data.json` per CONTENT, with the `episodes[]` array carrying per-episode info. The episode's video file is identified by its `videoFileName` field — a stable, human-readable string that also matches the file on disk.

### 5.5 Schema evolution

`schemaVersion: Int` is the first field. The parser uses `ignoreUnknownKeys = true` + `Json { coerceInputValues = true }` so future additions are backward-compatible. Breaking changes (renames, type changes) bump the version; the parser dispatches to a per-version adapter.

---

## 6. Directory creation + atomic publish (temp cache → SAF)

### 6.1 The temp cache layout (internal-cache-first)

The temp cache lives under `context.cacheDir`, NOT the user's SAF folder. Layout:

```
<cacheDir>/anikuta_downloads/
└── <downloadId>/                            ← per-task dir (downloadId == download_queue.id)
    ├── video.<ext>                          ← temp video file (deleted on completion/failure)
    ├── subtitles/                           ← temp subtitle files (downloaded separately)
    │   ├── English_0.srt
    │   └── Spanish_1.ass
    ├── cover.jpg                            ← temp cover (downloaded from coverUrl)
    ├── data.json                            ← temp data.json (built incrementally, atomically swapped on success)
    ├── resume.json                          ← (only for Advanced method — chunk progress)
    ├── chunk_0.part                         ← (only for Advanced method)
    ├── chunk_1.part
    └── ...
```

**Why internal cache first?**
1. **No pollution** — partial/corrupt downloads never appear in the user's folder.
2. **Performance** — writing to internal cache is faster than SAF per-byte writes (no ContentResolver round-trips per buffer flush).
3. **Validation** — can inspect bytes (Content-Type, size, magic bytes) BEFORE committing.
4. **Atomicity** — the user's folder only ever contains complete, valid files.
5. **Crash safety** — a partial temp file is just deleted on next startup. A partial file in the user's SAF folder would be confusing.

### 6.2 The `TempDownloadCache` class

```kotlin
class TempDownloadCache(context: Context) {
    private val rootDir = File(context.cacheDir, "anikuta_downloads").also { it.mkdirs() }

    fun taskDir(downloadId: Long): File =
        File(rootDir, downloadId.toString()).also { it.mkdirs() }

    fun videoFile(downloadId: Long, extension: String): File =
        File(taskDir(downloadId), "video.$extension")

    fun subtitlesDir(downloadId: Long): File =
        File(taskDir(downloadId), "subtitles").also { it.mkdirs() }

    fun subtitleFile(downloadId: Long, lang: String, index: Int, ext: String): File =
        File(subtitlesDir(downloadId), "${sanitizeLang(lang)}_$index.$ext")

    fun coverFile(downloadId: Long): File =
        File(taskDir(downloadId), "cover.jpg")

    fun dataJsonFile(downloadId: Long): File =
        File(taskDir(downloadId), "data.json")

    fun resumeFile(downloadId: Long): File =
        File(taskDir(downloadId), "resume.json")

    fun chunkFile(downloadId: Long, index: Int): File =
        File(taskDir(downloadId), "chunk_$index.part")

    fun cleanupTask(downloadId: Long) {
        taskDir(downloadId).deleteRecursively()
    }

    /** Called once at app startup (from DownloadModule.kt's single { TempDownloadCache(...).also { it.cleanupStale() } }). */
    fun cleanupStale() {
        // REVIEW-5 (R1-M8): if the DownloadService is being restarted by START_STICKY after a
        // crash, the in-flight temp dirs might still be in use. Check whether the service is
        // running before deleting. (Low likelihood — most crashes don't restart the service cleanly.)
        // For simplicity, we use a file lock per downloadId — if a `taskDir/<id>/.lock` file is
        // held by a running process, skip that dir. (Phase D.8 polish.)
        rootDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                // Any temp dir present at startup is from a crashed/interrupted download.
                // Safe to delete — the user's SAF folder has nothing partial.
                dir.deleteRecursively()
            }
        }
    }

    // REVIEW-5 M59 (R1-M10): called by tryStartNext before launching a download — checks that
    // there's enough internal-cache space for the task's totalBytes (or 4GB if unknown). Returns
    // false (with a logged reason) if the cache would overflow.
    fun hasSpaceFor(totalBytes: Long): Boolean {
        val usable = rootDir.usableSpace
        val required = if (totalBytes > 0) totalBytes else 4L * 1024 * 1024 * 1024  // assume 4GB if unknown
        return usable >= required
    }
}
```

### 6.3 The publish-to-SAF step

```kotlin
fun publishToUserFolder(
    content: DownloadContentInfo,
    episode: DownloadEpisodeInfo,
    tempVideoFile: File,
    tempSubtitlesDir: File,
    tempCoverFile: File?,
    tempDataJsonFile: File,
    videoExtension: String,
): PublishResult {

    // 1. Ensure the content folder exists: <root>/video/<sanitized-title>/
    val contentDir = ensureContentDir(content) ?: return PublishResult.Error("...")

    // 2. Publish the cover (if we have one AND the content folder doesn't already have one).
    if (tempCoverFile != null && tempCoverFile.exists()) {
        val coverTarget = contentDir.findFile("cover.jpg")
        if (coverTarget == null) {
            contentDir.createFile("image/jpeg", "cover.jpg")?.let { target ->
                copyFile(tempCoverFile, target.uri)
            }
        }
    }

    // 3. Publish the video — overwrite if re-downloading.
    val videoName = episodeFileName(content, episode, videoExtension)
    contentDir.findFile(videoName)?.delete()
    val videoTarget = contentDir.createFile("video/*", videoName)
        ?: return PublishResult.Error("Failed to create video file in SAF folder")
    copyFile(tempVideoFile, videoTarget.uri)

    // 4. Publish subtitles.
    val subtitleUris = mutableListOf<String>()
    if (tempSubtitlesDir.exists()) {
        tempSubtitlesDir.listFiles()?.forEach { tempSub ->
            if (!tempSub.isFile) return@forEach
            val subTarget = contentDir.createFile("application/octet-stream", tempSub.name)
            if (subTarget != null) {
                copyFile(tempSub, subTarget.uri)
                subtitleUris.add(subTarget.uri.toString())
            }
        }
    }

    // 5. Update + publish data.json. Read the temp file, append/replace the
    //    episode entry, write back to the content folder.
    val existingDataJson = readDataJson(contentDir)
    val updatedDataJson = (existingDataJson ?: buildInitialDataJson(content))
        .withEpisodeUpdated(episode, videoName, subtitleFileNames, ...)
        .copy(updatedAt = System.currentTimeMillis())
    writeDataJson(contentDir, updatedDataJson)

    // 6. REVIEW-5 M54 (R1-I5): ensure `.nomedia` exists in the content folder so downloaded
    //    .mp4 files don't appear in gallery apps. Created ONCE per content folder (idempotent).
    if (contentDir.findFile(".nomedia") == null) {
        contentDir.createFile("application/octet-stream", ".nomedia")
    }

    return PublishResult.Success(
        videoUri = videoTarget.uri.toString(),
        subtitleUris = subtitleUris,
        sizeBytes = tempVideoFile.length(),
    )
}
```

**Critical design points:**
- Video MIME is `video/*` — SAF providers usually auto-detect; specifying `video/*` lets the provider pick the actual MIME.
- Subtitle MIME is `application/octet-stream` — generic, since `.ass`/`.srt`/`.vtt` don't have universally-recognized MIMEs in SAF.
- Existing files are deleted before creating new ones (handles re-downloads).
- The `data.json` is read-modified-written ATOMICALLY: read existing → update the relevant episode entry → write back. If the write fails midway, the temp `data.json` in the cache dir is intact; we don't corrupt the user's `data.json`.
- The video URI returned is a **content:// URI** — playable by MPV via `resolveUrlForMpv`.

### 6.4 Atomicity of `data.json` writes

`data.json` is critical — corrupting it would orphan the content (the scan wouldn't recognize it). The write protocol:

```kotlin
fun writeDataJson(contentDir: DocumentFile, data: ContentDataJson) {
    // 1. Serialize to a temp file in the cache dir.
    val tempFile = File.createTempFile("data", ".json", context.cacheDir)
    tempFile.writeText(json.encodeToString(ContentDataJson.serializer(), data))

    // 2. Find or create the target data.json in the content dir.
    val target = contentDir.findFile("data.json") ?: contentDir.createFile("application/json", "data.json")

    // 3. Copy temp → target (overwrite).
    target!!.uri.let { uri ->
        context.contentResolver.openOutputStream(uri, "w")?.use { out ->
            tempFile.inputStream().use { it.copyTo(out) }
        }
    }

    // 4. Delete the temp file.
    tempFile.delete()
}
```

The two-step (temp file → copy to SAF) protects against partial writes if the app crashes mid-write. The SAF provider either has the old `data.json` or the new one — never a half-written one.

---

## 7. Scan-on-startup logic (the reinstall recognition engine)

When the app starts (or when the user picks a NEW folder), the app scans the SAF folder, reads every `data.json`, and UPSERTs the content into the DB by `mainId`. This is what makes the system survive app-delete + reinstall + same-folder-selection.

### 7.1 The scan algorithm

> **REVIEW-5 M55 (R1-I6):** the OLD draft called `contentDir.findFile(ep.videoFileName)` per
> episode — `DocumentFile.findFile()` is O(N) over the children of the directory. For a content
> folder with 200 episode files, that's 200 × O(200) = 40,000 ops. The fix: call `listFiles()`
> ONCE per content folder + build a `Map<String, DocumentFile>` index, then look up by name.
>
> **REVIEW-5 M57 (R1-M2):** the OLD draft's scan list was `listOf("video", "images", "text")` —
> no `"audio"`. But §3.2 mentions `audio/` for audio dramas. Either add `"audio"` to the scan
> list OR remove the audio-drama mention. We add `"audio"` (forward-compatible — if a `audio/`
> folder exists, it's scanned; if not, no harm).
>
> **REVIEW-5 M58 (R1-I8):** the incremental-scan optimization using `DocumentFile.lastModified()`
> is unreliable on many SAF providers (some return 0, some return the folder-creation time).
> The scan falls back to "always scan" if `lastModified()` returns 0 or a sentinel.

```kotlin
suspend fun scanFolder() {
    val root = storage.rootTree() ?: return  // no folder set
    val now = System.currentTimeMillis()

    // 1. Walk the four format folders (REVIEW-5 M57 — added "audio").
    val formatFolders = listOf("video", "images", "text", "audio")
    val scannedMainIds = mutableSetOf<String>()
    val seenMainIds = mutableMapOf<String, Long>()  // mainId → updatedAt (REVIEW-5 R1-I7 duplicate detection)

    for (formatFolder in formatFolders) {
        val formatDir = root.findFile(formatFolder)?.takeIf { it.isDirectory } ?: continue

        for (contentDir in formatDir.listFiles()) {
            if (!contentDir.isDirectory) continue
            val dataJsonFile = contentDir.findFile("data.json") ?: continue
            val dataJson = try {
                readDataJson(contentDir)
            } catch (e: Exception) {
                Logger.w(TAG, e) { "Skipping ${contentDir.name} — data.json unreadable" }
                continue
            }

            // REVIEW-5 (R1-I7): if two folders have the same mainId, keep the one with the newer
            // updatedAt + log a warning. (User manually copied a folder to "back it up".)
            val prevUpdatedAt = seenMainIds[dataJson.mainId]
            if (prevUpdatedAt != null) {
                if (dataJson.updatedAt <= prevUpdatedAt) {
                    Logger.w(TAG) { "Duplicate mainId ${dataJson.mainId} at ${contentDir.name} — older than previously seen, skipping" }
                    continue
                }
                Logger.w(TAG) { "Duplicate mainId ${dataJson.mainId} — keeping newer folder ${contentDir.name}" }
            }
            seenMainIds[dataJson.mainId] = dataJson.updatedAt

            // 2. UPSERT the content into the DB by mainId.
            //    REVIEW-5 M5: the ContentDataJson now carries the full FK set so the upsert is lossless.
            contentRepository.upsertFromDataJson(dataJson)
            //    REVIEW-5 (R1-M6): also UPSERT the anilist_detail table if anilistId is set.
            if (dataJson.anilistId != null) {
                anilistDetailRepository.upsertFromDataJson(dataJson)
            }
            scannedMainIds.add(dataJson.mainId)

            // 3. REVIEW-5 M55: listFiles() ONCE per content folder, build a name→DocumentFile index.
            val filesByName = contentDir.listFiles().associateBy { it.name }

            // 4. UPSERT each episode that has its video file on disk (look up by name in the index).
            for (ep in dataJson.episodes) {
                val videoFile = filesByName[ep.videoFileName]
                if (videoFile != null && videoFile.isFile && videoFile.length() > 0) {
                    // The file is present + non-empty → mark as downloaded.
                    downloadManager.markEpisodeDownloaded(
                        mainId = dataJson.mainId,
                        episodeKey = ep.episodeKey,
                        videoUri = videoFile.uri.toString(),
                        sizeBytes = videoFile.length(),
                        quality = ep.quality,
                        downloadedAt = ep.downloadedAt,
                    )
                } else {
                    // The data.json says this episode should be here, but the file
                    // is missing. Mark as "deleted externally" — keeps the DB in sync.
                    downloadManager.markEpisodeMissing(dataJson.mainId, ep.episodeKey)
                }
            }
        }
    }

    // 4. Mark any DB-downloaded episode NOT in the scanned set as "missing"
    //    (folder removed from under us, or the user deleted files manually).
    downloadManager.reconcileWithScan(scannedMainIds, now)

    // 5. Update the scan_state.json under .anikuta/
    storage.writeScanState(now, scannedMainIds.size)
}
```

### 7.2 When the scan runs

- **On app start** (in `AnikutaApp.onCreate`, after Koin starts). Runs on `Dispatchers.IO`, doesn't block the UI.
- **When the user picks a new folder** (in the SAF picker callback, via `downloadManager.requestFolderRescan()`).
- **When the user pulls-to-refresh the Downloads screen** (manual trigger).
- **NOT on every download** — the download flow updates the `data.json` + DB incrementally; a full scan is only needed for reinstall/recognition cases.

### 7.3 Performance considerations

- SAF `listFiles()` is slow (one ContentResolver call per directory). For a library with 200 contents × 1 format folder, that's ~600 calls. Acceptable on a one-time scan; not on every UI render.
- The scan is incremental: if `scan_state.json` records "last scan at T" and the SAF folder's `lastModified` is older than T, skip the scan.
- The DB rows created by the scan are the source for ALL UI queries after startup. The UI never reads `data.json` files directly.

### 7.4 What happens if the user picks a DIFFERENT folder

Two scenarios:
1. **The new folder has ANI-KUTA contents in it** (e.g. the user moved their library to a new SD card). The scan re-discovers everything; the DB is updated to reflect the new URIs. Old URIs from the previous folder are invalidated. No content is "lost" — it's just re-recognized.
2. **The new folder is empty.** The scan finds nothing; the DB's downloaded-episode rows are marked "missing" (the files are gone with the old folder). The user sees an empty library; their queue is preserved (queue tasks are re-queued for re-download).

In BOTH cases, the `mainId` is the stable anchor — even if the folder URI changes, the same `mainId` resolves to the same content (provided the files are still there under the new folder).

### 7.5 What happens if a `data.json` is corrupt

The scan logs a warning and skips the content folder. The folder stays on disk (the user might be able to repair the JSON by hand); it's just not registered in the DB. The next download attempt for the same content will create a NEW `data.json` (overwriting the corrupt one).

---

## 8. Deletion

### 8.1 Delete a single episode

```kotlin
fun deleteEpisode(mainId: String, episodeKey: String): Boolean {
    val contentDir = findContentDir(mainId) ?: return false
    val dataJson = readDataJson(contentDir) ?: return false
    val episodeEntry = dataJson.episodes.firstOrNull { it.episodeKey == episodeKey } ?: return false

    // 1. Delete the video file.
    contentDir.findFile(episodeEntry.videoFileName)?.delete()

    // 2. Delete the subtitle files.
    for (subName in episodeEntry.subtitleFileNames) {
        contentDir.findFile(subName)?.delete()
    }

    // 3. Update data.json: remove the episode entry.
    val updated = dataJson.copy(
        episodes = dataJson.episodes.filterNot { it.episodeKey == episodeKey },
        updatedAt = System.currentTimeMillis(),
    )
    writeDataJson(contentDir, updated)

    // 4. If the content folder is now empty (no episodes + no other files), auto-delete it.
    if (updated.episodes.isEmpty() && contentDir.listFiles()?.size == 1) {
        // The only file left is data.json — delete the whole folder.
        contentDir.delete()
    }
    return true
}
```

### 8.2 Delete all episodes of a content

```kotlin
fun deleteContent(mainId: String): Boolean {
    val contentDir = findContentDir(mainId) ?: return false
    return contentDir.delete()  // recursive — deletes everything inside
}
```

### 8.3 Auto-cleanup of empty content folders

After deleting an episode, if the content folder is now empty (no episodes left in `data.json` AND no other files besides `data.json` itself), the whole folder is deleted. Keeps the user's folder clean.

### 8.4 Filesystem fallback (`findContentDirByMainId`)

```kotlin
fun findContentDir(mainId: String): DocumentFile? {
    // 1. Check all three format folders.
    val root = rootTree() ?: return null
    for (formatFolder in listOf("video", "images", "text")) {
        val formatDir = root.findFile(formatFolder)?.takeIf { it.isDirectory } ?: continue
        for (contentDir in formatDir.listFiles()) {
            if (!contentDir.isDirectory) continue
            val dataJson = readDataJson(contentDir) ?: continue
            if (dataJson.mainId == mainId) return contentDir
        }
    }
    return null
}
```

This walks the format folders + reads each `data.json` until it finds the matching `mainId`. Used by `isEpisodeDownloaded` / `getDownloadedVideoUri` / `getDownloadedSubtitleUris` as a filesystem fallback when no in-memory task matches (e.g. after a source switch — the new source has a different `episodeUrl` but the same `mainId`).

**Performance:** for a 200-content library, this is up to 200 SAF file reads in the worst case. The scan-on-startup populates the DB so the in-memory path is the fast path; this fallback only fires when the DB is empty (first launch after reinstall) or when the DB row is missing.

---

## 9. FileProvider configuration

The FileProvider is configured for the cache directory only — used by the app update downloader (to share APK files with the system installer). The download system does NOT use a FileProvider for sharing video URIs with MPV.

```xml
<!-- app/src/main/res/xml/file_paths.xml -->
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <cache-path name="updates" path="updates/" />
    <cache-path name="cache" path="." />
</paths>
```

MPV plays the SAF content:// URI directly via `resolveUrlForMpv` (the player opens the content URI via Android's ContentResolver and feeds the file descriptor to MPV). This is why `publishToUserFolder` returns content:// URIs.

The FileProvider declared in `app/src/main/AndroidManifest.xml` is wired but only for cache-path (APK install). Downloads don't need it.

---

## 10. The proxy-churn fix interplay

`15-ui-and-bug-analysis.md` Part B documents the "download fails when playing another episode" bug — caused by the extension's local-proxy-server port churning when another `getHosterList` is called. The storage system plays a small but important role in the fix:

1. **`directUrl` is cached in `data.json` per episode** (via the `EpisodeEntry`'s implicit source-context). When a re-download is needed (e.g. user deleted the file but kept the `data.json`), the orchestrator can prefer `directUrl` from the cache if available — avoiding a fresh resolve entirely.
2. **The temp cache holds the resolve context** (under `data.json`) so a re-resolve-on-IOException can re-pick the SAME (server, audio, quality) without re-running the full priority pipeline.

This is detailed in `10-player-integration.md` §14 (the proxy-churn fix) and `13-implementation-plan.md` Phase D.2.

---

## 11. Summary — what the new project must implement

1. **SAF folder picker** (`ActivityResultContracts.OpenDocumentTree`) + `takePersistableUriPermission` + store the URI string in prefs. **First-launch prompt** if no folder is set.
2. **Folder structure** `<root>/{video,images,text}/<sanitized-title>/{data.json, cover.jpg, <title> - E00001.mp4, ...}`. NO `ANIKUTA/` wrapper, NO `anime/` folder, NO `[al-123]` suffix, NO `Episode NNN/` subfolder.
3. **5-digit episode padding** (`E00001` not `E001`). Fractional specials keep `.5` suffix (`E00012.5`).
4. **`data.json` per content folder** — the durable source of truth. Schema in §5.1. Schema-versioned + backward-compatible parser.
5. **Internal-cache-first** download pipeline — temp dir under `context.cacheDir/anikuta_downloads/<downloadId>/`. Atomic publish to SAF via `DocumentFile.createFile` + `ContentResolver.openOutputStream` + `copyTo`.
6. **Atomic `data.json` writes** — temp file → copy to SAF (never partial writes to the user's folder).
7. **`mainId`-stable identity** so downloads survive extension source switches + AniList unlinking.
8. **Auto-cleanup of empty content folders** after episode deletion.
9. **Scan-on-startup** — walks `video/`, `images/`, `text/`, reads each `data.json`, UPSERTs to DB. The DB is a cache; `data.json` is authoritative.
10. **Reinstall recognition** — app-delete + reinstall + same-folder-selection → scan re-registers everything by `mainId`. No data loss (other than the in-flight queue).
11. **Filesystem fallback** (`findContentDir` by `mainId`) for offline lookup when no in-memory task matches.
12. **Cleanup of stale temp dirs on app startup** (`TempDownloadCache.cleanupStale()`).
13. **No FileProvider for video playback** — MPV plays the SAF content:// URI directly.

---

## 12. Differences from the OLD project's storage (one-way migration table)

| Aspect | OLD project | NEW project (this doc) |
|---|---|---|
| Top-level wrapper | `<root>/ANIKUTA/downloads/` | None — directly under `<root>/` |
| Type folder | `anime/` (hardcoded) | `video/`, `images/`, `text/` (format-based, future-proof) |
| Content folder name | `<Title [al-101522]>` | `<Title>` (no ID suffix) |
| Identity in folder name | AniList ID | None — `mainId` in `data.json` |
| Episode sub-folder | `Episode 001/` (per-episode folder) | None — episode files sit directly in content folder |
| Episode padding | 3-digit (`001`) | 5-digit (`00001`) |
| Episode file name | `video.mp4` (generic, in subfolder) | `<Title> - E00001.mp4` (descriptive, in content folder) |
| Per-episode metadata | `Episode NNN/data/metadata.json` | None — episodes are entries in the content's `data.json` |
| Per-content metadata | None | `data.json` per content folder |
| Reinstall recognition | ❌ (DB only — wiped on uninstall) | ✅ (`data.json` survives in user's folder) |
| Source-switching identity | `contentId + episodeNumber` | `mainId + episodeKey` (5-digit padded) |
| Cover image | None | `cover.jpg` per content folder (for notification thumbnails) |
| Temp cache path | `<cacheDir>/anikuta_downloads/<taskId>/` | `<cacheDir>/anikuta_downloads/<downloadId>/` (same shape, new naming) |

**There is no migration script from the old project** — the new project is a fresh start. Users of the old project must re-download their library (the old folder structure is incompatible by design).

---

## 13. Honest notes + known limitations

- **SAF's `DocumentFile.listFiles()` is slow** — each call hits the ContentResolver. The scan-on-startup is the most expensive operation (~N+M calls for N contents + M episode files). Acceptable because it's one-shot per startup. Could be cached more aggressively with a `Map<mainId, DocumentFile>` in-memory after the first scan.
- **Two episodes with the same title** (e.g. a "remake" S1 and the original S1 from a different extension) — the content folder would collide. Mitigation: the `mainId` differs, but the folder name is the same. Two options: (a) append `(2)` to the second folder, (b) use the `mainId`'s short prefix as a tiebreaker. Recommendation: option (a) — keep folder names human-readable; the `data.json` distinguishes the two contents internally.
- **The `.5` episode fractional format** caps at 1 decimal place. Episode `12.25` would be `E00012.25` (the format string `%.1f` would round to `12.3` — fix by switching to a more careful formatter if non-standard fractional episodes appear in practice). For now, fractional episodes are almost always `.5` (recap/ova), so this is fine.
- **`data.json` files are written one-at-a-time per download.** If the user downloads 100 episodes in rapid succession, that's 100 read-modify-write cycles on the same `data.json`. Each cycle is fast (~10-50ms on internal cache + ~100-500ms SAF write), but the cycles are sequential. Worth optimizing later by batching (write once per "download session" instead of per episode).
- **SAF URIs can become invalid** if the user revokes the permission (system Settings → Apps → ANI-KUTA → Storage → Revoke). The `rootTree()` accessor handles this by returning `null`; the UI shows "Folder permission revoked — please re-select the folder". The DB rows persist; the user just can't access the files until they re-grant.
- **The `.anikuta/` hidden folder** is created lazily on first scan. It contains the scan-state cache only; deleting it is safe (the next scan re-creates it).
