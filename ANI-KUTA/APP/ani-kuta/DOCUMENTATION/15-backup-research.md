# 15 — Backup/Restore Research (Multi-App Import Compat)

> Source: web research (Aniyomi / Mihon / Mangayomi / Anikku docs + source code),
> the old ANIKUTA project's `:core:backup` module + Aniyomi translator
> (`REFERENCES/old-kuta/ANIKUTA/core/backup/src/...`), and
> `04-core-modules.md` §`:core:backup`.
>
> Goal: define the import-compat surface so ANI-KUTA can migrate users
> from **Aniyomi / Animiru / Mangayomi / Anikku** with a single
> `BackupImporter` abstraction, and pick ANI-KUTA's own backup format.

---

## ANI-KUTA's Backup Requirements

The backup system must support:

1. **Create / restore ANI-KUTA's own format** — full library, episodes,
   watch progress, categories, tracker bindings, source links, episode
   metadata, preferences, cover images.
2. **Auto-backup** on a WorkManager schedule (per `:core:backup` /
   `AutoBackupWorker` + `AutoBackupScheduler`).
3. **Multi-format restore** (read-only) for Aniyomi/Animiru/Anikku
   `.tachibk` protobuf + Mangayomi `.backup` JSON.
4. **AniList ID resolution** during import — external anime are matched
   to AniList IDs via tracker bindings (AniList → MAL → title search),
   then mapped onto ANI-KUTA's `ContentUID` (two-tier identity:
   `LocalId` per-source + `ContentId` per-content — ADR-050).
5. **Pluggable providers** — `BackupProvider` interface, one impl per
   category (`Library`, `AnimeDetails`, `Episodes`, `EpisodeMetadata`,
   `WatchProgress`, `Categories`, `Tracker`, `SourceLinks`,
   `Preferences`, `CoverImages`), registered as
   `single<List<BackupProvider>>(named("backupProviders"))` (Koin
   multi-binding — preserves the documented footgun fix from the old
   project).
6. **Self-contained covers** — option to bundle cover image bytes in
   the backup zip (avoids re-downloading on restore).
7. **Schema-versioned + format-detected** — `BackupFormatDetector`
   peeks magic bytes to route ZIP / gzip-protobuf / JSON-zip to the
   correct `BackupFormat` reader.

---

## Aniyomi `.tachibk` Format

### Container

- **File extension:** `.tachibk` (also `.proto.gz` — both are valid).
- **Container:** raw protobuf **or** gzip-wrapped protobuf. Aniyomi's
  `BackupDecoder.kt` peeks 2 bytes: `0x1f 0x8b` → gzip-decompress first,
  otherwise treat as raw protobuf.
- **Not a zip.** A `.tachibk` is exactly one protobuf message (gzip
  optional). Cover images are **not** embedded — only their URLs.
- **Encoder/decoder:** `kotlinx.serialization.protobuf` (not Google's
  protobuf-java). Field numbers are declared with `@ProtoNumber(N)`.

### Root schema (`Backup.kt` — modern) vs `LegacyBackup.kt`

Two root shapes exist. Aniyomi's `BackupDecoder` detects legacy via
`BackupDetector.isLegacyBackup(bytes)`:

```kotlin
// Modern (current Aniyomi) — anime at field 501
@Serializable data class Backup(
  @ProtoNumber(1)   backupManga: List<BackupManga>,
  @ProtoNumber(2)   backupCategories: List<BackupCategory>,        // manga categories
  @ProtoNumber(101) backupSources: List<BackupSource>,             // manga sources
  @ProtoNumber(104) backupPreferences: List<BackupPreference>,
  @ProtoNumber(105) backupSourcePreferences: List<BackupSourcePreferences>,
  @ProtoNumber(106) backupMangaExtensionRepo: List<BackupExtensionRepos>,
  @ProtoNumber(500) isLegacy: Boolean = true,                      // detection flag
  @ProtoNumber(501) backupAnime: List<BackupAnime>,                // ← anime entries
  @ProtoNumber(502) backupAnimeCategories: List<BackupCategory>,
  @ProtoNumber(503) backupAnimeSources: List<BackupAnimeSource>,
  @ProtoNumber(504) backupExtensions: List<BackupExtension>,
  @ProtoNumber(505) backupAnimeExtensionRepo: List<BackupExtensionRepos>,
  @ProtoNumber(506) backupCustomButton: List<BackupCustomButtons>,
)

// Legacy (older Aniyomi + Animiru) — anime at field 3
@Serializable data class LegacyBackup(
  @ProtoNumber(1)   backupManga,
  @ProtoNumber(2)   backupCategories,
  @ProtoNumber(3)   backupAnime,                                   // ← anime entries
  @ProtoNumber(4)   backupAnimeCategories,
  @ProtoNumber(101) backupSources,
  @ProtoNumber(103) backupAnimeSources,
  @ProtoNumber(104) backupPreferences,
  @ProtoNumber(105) backupSourcePreferences,
  @ProtoNumber(106) backupExtensions,
  @ProtoNumber(107) backupAnimeExtensionRepo,
  @ProtoNumber(108) backupMangaExtensionRepo,
  @ProtoNumber(109) backupCustomButton,
)
```

### `BackupAnime` schema (the most important model)

```kotlin
@Serializable data class BackupAnime(
  @ProtoNumber(1)   source: Long,                // extension sourceId
  @ProtoNumber(2)   url: String,                 // source-specific URL
  @ProtoNumber(3)   title: String,
  @ProtoNumber(4)   artist: String?,
  @ProtoNumber(5)   author: String?,
  @ProtoNumber(6)   description: String?,
  @ProtoNumber(7)   genre: List<String>,
  @ProtoNumber(8)   status: Int,                 // 0=unknown,1=ongoing,2=completed,3=licensed,4=hiatus
  @ProtoNumber(9)   thumbnailUrl: String?,
  @ProtoNumber(13)  dateAdded: Long,
  @ProtoNumber(16)  episodes: List<BackupEpisode>,
  @ProtoNumber(17)  categories: List<Long>,      // category IDs (junction inline)
  @ProtoNumber(18)  tracking: List<BackupAnimeTracking>,
  @ProtoNumber(100) favorite: Boolean,
  @ProtoNumber(101) episode_flags: Int,
  @ProtoNumber(103) viewer_flags: Int,
  @ProtoNumber(104) history: List<BackupAnimeHistory>,
  @ProtoNumber(105) updateStrategy: Int,         // 0=ALWAYS_UPDATE, 1=ONLY_UPDATE_ONCE
  @ProtoNumber(106) lastModifiedAt: Long,
  @ProtoNumber(107) favoriteModifiedAt: Long?,
  @ProtoNumber(109) version: Long,
  // Aniyomi-specific season / fetch-type (502–507) — see BackupAnime.kt
)
```

### `BackupEpisode`, `BackupAnimeTracking`, `BackupAnimeHistory`

```kotlin
BackupEpisode:
  1 url, 2 name, 3 scanlator, 4 seen, 5 bookmark,
  6 lastSecondSeen, 7 dateFetch, 8 dateUpload, 9 episodeNumber,
  10 sourceOrder, 11 lastModifiedAt, 12 version, 16 totalSeconds,
  501 fillermark (bool), 502 summary (String?), 503 previewUrl (String?)

BackupAnimeTracking:  // ← KEY for identity resolution
  1 syncId (Int),        // 1=MAL, 2=AniList, 3=Kitsu, 4=Shikimori,
                         // 5=Bangumi, 6=Komga, 7=MangaUpdates,
                         // 8=Anime-Planet, 9=MangaDex, 10=Simkl, 11=Kodi, ...
  2 libraryId (Long),    // tracker-side library ID (opaque)
  3 mediaIdInt (Int),    // DEPRECATED — pre-0.14 remote ID (32-bit)
  4 trackingUrl (String),
  5 title (String),
  6 lastEpisodeSeen (Float),
  7 totalEpisodes (Int),
  8 score (Float),
  9 status (Int),        // 0=watching,1=completed,2=on_hold,3=dropped,4=plan_to_watch,5=rewatching
  10 startedReadingDate (Long),
  11 finishedReadingDate (Long),
  12 private (Boolean),
  100 mediaId (Long)     // ← CURRENT remote ID (64-bit)

BackupAnimeHistory:
  1 url (String),        // episode URL
  2 lastRead (Long),     // epoch ms of last watch
  3 readDuration (Long)  // seconds watched
```

### BackupCategory, BackupAnimeSource, BackupPreference

```kotlin
BackupCategory:        1 name, 2 order, 3 id, 100 flags
BackupAnimeSource:     1 name, 2 sourceId (Long)
BackupPreference:      1 key (String), 2 value (PreferenceValue — sealed!)
BackupSourcePreferences: 1 sourceKey, 2 prefs (List<BackupPreference>)
BackupExtension:       1 name, 2 version, 3 signingCertificateFingerprint, ...
BackupExtensionRepos:  1 repo (String)
BackupCustomButtons:   1 name, 2 description, ...
```

### What's included (confirmed by Aniyomi docs + source)

- ✅ Library titles (anime + manga)
- ✅ Categories (separate anime vs manga category lists)
- ✅ Watched episodes / read chapters **for library titles only**
- ✅ Tracker bindings (per anime/manga)
- ✅ Watch/read history (per episode/chapter URL)
- ✅ Series info (author, artist, date added, viewer, read duration, etc.)
- ✅ Extensions used (name + version + signing cert — **not** the APK)
- ✅ Extension repos
- ✅ App preferences + source preferences (incl. tracker tokens if
  "include sensitive settings" was selected)
- ✅ Custom buttons (Aniyomi-specific player quick-actions)
- ❌ Downloaded episode/chapter files (including local source)
- ❌ Custom covers
- ❌ History of titles NOT in the library

### Parsing notes (from the old ANIKUTA `AniyomiBackupModels.kt`)

- The old project deliberately declared a **minimal subset** of fields.
  Unknown protobuf fields are silently skipped at the wire level, so
  declaring fewer fields than the producer writes is safe — schema
  additions on the Aniyomi side don't break our decoder.
- `PreferenceValue` is a sealed class with a specific wire format; the
  old project **does not declare** the preference/extension fields at
  all, sidestepping the issue. ANI-KUTA should follow the same approach
  unless we actively want to import preferences.
- Both `Backup` (modern, anime@501) **and** `LegacyBackup` (legacy,
  anime@3) must be modeled. Try modern first → fall back to legacy
  (matches `BackupDecoder.kt` logic).

### Sources

- Aniyomi docs: https://aniyomi.org/docs/guides/backups
- Mihon docs (same format, more detailed list): https://mihon.app/docs/guides/backups
- Aniyomi `Backup.kt`: `app/src/main/java/eu/kanade/tachiyomi/data/backup/models/Backup.kt` (main branch)
- Aniyomi `BackupDecoder.kt`: `app/src/main/java/eu/kanade/tachiyomi/data/backup/BackupDecoder.kt`
- Converter reference (third-party): https://github.com/Tesdv/tachibk-json

---

## Animiru Backup Format

**Animiru is a direct fork of Aniyomi** (anime-only — the manga half of
Mihon removed) maintained at `github.com/quickdesh/Animiru`. Per its
own `Backup.kt` (master branch, retrieved for this research):

```kotlin
// Animiru — uses the LEGACY schema (anime at field 3), no isLegacy flag
@Serializable data class Backup(
  @ProtoNumber(1)   backupManga,            // empty (no manga)
  @ProtoNumber(2)   backupCategories,
  @ProtoNumber(3)   backupAnime,            // ← anime at field 3
  @ProtoNumber(4)   backupAnimeCategories,
  @ProtoNumber(100) backupBrokenSources,
  @ProtoNumber(101) backupSources,
  @ProtoNumber(102) backupBrokenAnimeSources,
  @ProtoNumber(103) backupAnimeSources,
  @ProtoNumber(104) backupPreferences,
  @ProtoNumber(105) backupExtensionPreferences,
)
```

**Key differences from current Aniyomi:**

- Uses the **legacy** proto layout (anime @ 3, no `isLegacy` flag, no
  `backupExtensions`, `backupAnimeExtensionRepo`, `backupCustomButton`
  fields). The old ANIKUTA `AniyomiLegacyBackup` model decodes this
  correctly.
- Backup filename prefix is still `aniyomi_*.proto.gz` — same naming,
  same extension, same gzip-protobuf wrapper.
- Animiru doesn't add custom proto fields, so the standard Aniyomi
  legacy reader handles it with **zero special-casing**.
- Animiru is anime-only, so `backupManga` is always empty.

**Conclusion:** Animiru → ANIKUTA import uses the **same code path as
Aniyomi legacy backups**. No separate importer needed.

---

## Mangayomi Backup Format

### Container

- **File extension:** `.backup` (a ZIP archive).
- **Inside the zip:** a single `*.backup.db` file (plain UTF-8 JSON,
  optionally zip-compressed with configurable level via
  `backupCompressionLevelProvider`).
- **Container library:** `package:archive/archive_io.dart`
  (`ZipFileEncoder`).
- **Not gzip-wrapped** — the JSON is inside the zip directly (zip
  provides the compression).
- Filename convention: `mangayomi_<timestamp>.backup` (this matters —
  Mangayomi's own `checkBackupType` sniffs `path.toLowerCase().contains
  ("mangayomi")` AND first-archive-entry ends with `.backup.db`).

### JSON structure

```json
{
  "version": "2",
  "manga": [ Manga.toJson(), ... ],                   // library entries (favorites only)
  "categories": [ Category.toJson(), ... ],
  "chapters": [ Chapter.toJson(), ... ],
  "downloads": [ Download.toJson(), ... ],
  "tracks": [ Track.toJson(), ... ],
  "trackPreferences": [ TrackPreference.toJson(), ... ],
  "history": [ History.toJson(), ... ],
  "updates": [ Update.toJson(), ... ],
  "settings": [ Settings.toJson(), ... ],
  "extensions_preferences": [ SourcePreference.toJson(), ... ],
  "extensions": [ Source.toJson(), ... ],
  "customButtons": [ CustomButton.toJson(), ... ]
}
```

Each key is **optional** — the user picks a `List<int>` of category IDs
(0–10) at backup time. ID mapping (from `backup.dart`):

| ID | Key | Isar collection |
|----|-----|-----------------|
| 0  | `manga` | `isar.mangas` (favorites, non-local) |
| 1  | `categories` | `isar.categorys` |
| 2  | `chapters`, `downloads` | `isar.chapters` + `isar.downloads` |
| 3  | `tracks` | `isar.tracks` |
| 4  | `history` | `isar.historys` |
| 5  | `updates` | `isar.updates` |
| 6  | `settings` | `isar.settings` |
| 7  | `extensions_preferences` | `isar.sourcePreferences` |
| 8  | `trackPreferences` | `isar.trackPreferences` (syncId != null) |
| 9  | `extensions` | `isar.sources` |
| 10 | `customButtons` | `isar.customButtons` |

### Models (key fields)

```dart
Manga {
  id, name, link, imageUrl, description, author, artist,
  status (enum), isManga (bool, legacy), itemType (enum: manga|anime),
  genre (List<String>), favorite (bool), source (String),
  lang, dateAdded, lastUpdate, lastRead,
  categories (List<int>), isLocalArchive (bool),
  customCoverImage (List<byte>), customCoverFromTracker (String?),
  smartUpdateDays (int?), updatedAt, sourceId (int?)
}

Track {
  id, libraryId (int?), mediaId (int?), mangaId (int?), syncId (int?),
  title, lastChapterRead (int), totalChapter (int), score (int),
  status (TrackStatus enum), startedReadingDate, finishedReadingDate,
  trackingUrl, isManga (bool, legacy), itemType (enum),
  updatedAt
}

History {
  id, mangaId, chapterId, isManga (legacy), itemType (enum),
  date (String?), updatedAt, readingTimeSeconds (int?)
  // chapter = IsarLink<Chapter>
}

Chapter { /* similar to BackupEpisode — url, name, episodeNumber,
            scanlator, seen/read, lastSecondSeen/lastPageRead,
            totalSeconds/totalPages, dateFetch, dateUpload, sourceOrder,
            isManga, itemType */ }
```

### Restore behavior (`restore.dart`)

- Sniffs format via `checkBackupType(path, archive)`:
  - Mangayomi: path contains `"mangayomi"` AND first zip entry ends
    with `.backup.db`.
  - Aniyomi/Mihon/Neko: path ends with `.tachibk` or `.proto.gz`, then
    sub-sniffs by package prefix (`aniyomi.mi` → aniyomi,
    `tachiyomi`/`mihon` → mihon, `neko` → neko).
  - Kotatsu: zip containing `categories` + `favourites` JSON files.
- For Mangayomi backups: `jsonDecode(utf8.decode(archive.files.first.content))`,
  then `restoreBackupProvider(backup)` clears + re-populates each Isar
  collection.
- For Tachibk backups: `GZipDecoder().decodeBytes(...)` →
  `BackupMihon.mergeFromCodedBufferReader(...)` (uses **generated Dart
  protobuf classes** `BackupMihon.pb.dart` / `BackupAniyomi.pb.dart`).
- Crucially, **Mangayomi CAN import Aniyomi `.tachibk`** — meaning
  Mangayomi knows the proto schema. We need the inverse: ANI-KUTA
  reading Mangayomi's `.backup` JSON.

### Identity mapping

Mangayomi's `Manga` model has **no native AniList ID field** — the
link between a manga and AniList/MAL/etc is via the `Track` model
(`syncId` + `mediaId`), exactly like Aniyomi's `BackupAnimeTracking`.
The `itemType` enum tells us whether a `Manga` row is anime or manga
(`isManga` is the legacy bool — newer backups use `itemType`).

### Sources

- Mangayomi DeepWiki (excellent overview): https://deepwiki.com/kodjodevf/mangayomi/6.2-backup-and-restore
- `lib/modules/more/data_and_storage/providers/backup.dart`
- `lib/modules/more/data_and_storage/providers/restore.dart`
- `lib/models/manga.dart`, `lib/models/track.dart`, `lib/models/history.dart`
  (master branch)

---

## Anikku Backup Format

**Anikku is an Aniyomi fork** (by `komikku-app`) — see
`github.com/komikku-app/anikku` and `anikku-app.github.io`. The Anikku
docs page (`/docs/guides/backups`) is verbatim the Aniyomi page with
only the brand name swapped — the format, file extension, and
contents are **identical**:

> "All Anikku (and Mihon) forks support the `.tachibk` / `.proto.gz`
> format to backup/restore your library."

So Anikku writes/reads the **same protobuf schema as Aniyomi**
(modern `Backup` shape — anime @ 501). The old ANIKUTA modern-format
reader (`AniyomiBackup` model) handles Anikku backups.

**Caveat:** There's an open issue ([komikku-app/anikku#246](
https://github.com/komikku-app/anikku/issues/246)) reporting that
restoring an Aniyomi-created backup in Anikku fails. The cause is on
Anikku's restore side (a parsing bug), not a format incompatibility —
the wire format is identical. ANI-KUTA reading Anikku backups is
unaffected by this Anikku-side bug.

**Conclusion:** Anikku → ANIKUTA import uses the **same code path as
Aniyomi modern backups**. No separate importer needed.

---

## Old ANIKUTA Backup Translator

The old project already shipped a working Aniyomi `.tachibk` importer
in `:core:backup`. The relevant code (read directly for this research):

### Files

```
core/backup/src/main/java/app/confused/anikuta/core/backup/
├── BackupManager.kt                          ← orchestrator
├── BackupEntry.kt                            ← sealed: 10 BackupEntry subclasses
├── format/
│   ├── AnikutaBackupFormat.kt                ← ANIKUTA .anikuta (zip+gz+json)
│   ├── AniyomiBackupFormat.kt                ← .tachibk reader (restore-only)
│   ├── BackupFormatDetector.kt               ← magic-byte sniff
│   └── aniyomi/AniyomiBackupModels.kt        ← minimal @Serializable protobuf
├── translation/
│   └── AniyomiBackupTranslator.kt            ← the brain: Aniyomi → BackupContainer
└── provider/                                 ← 10 BackupProvider impls
```

### The translator (`AniyomiBackupTranslator.kt`)

API:

```kotlin
class AniyomiBackupTranslator(private val anilistApi: AniListApi) {
    val progress: StateFlow<TranslationProgress?>     // live UI updates
    suspend fun translate(aniyomi: AniyomiBackup): TranslationResult
    suspend fun retryRateLimited(aniyomi: AniyomiBackup, previous: List<AnilistResolution>): TranslationResult
}
```

Pipeline (`translate()`):

1. **Filter** — only `favorite == true` anime (library entries).
2. **Resolve AniList IDs** (per anime, in order) — strategy chain:
   1. `tracking.firstOrNull { it.syncId == 2 && mediaId != 0 }` → AniList
      tracker binding. Direct: `mediaId.toInt()` is the AniList ID.
   2. `tracking.firstOrNull { it.syncId == 1 && mediaId != 0 }` → MAL
      binding. `anilistApi.searchByMalId(malId)` → AniList lookup.
   3. Title search: `anilistApi.searchByTitle(ani.title)`.
   - Outcomes: `Resolved(anilistId, anilistAnime, method)`,
     `Failed(title, reason)`, `RateLimited(title, reason)` (retryable).
   - Rate limiter: built into `AniListApi` (max 80 req/min, "fast mode"
     for the first 40 — tuned for backup restore of small libraries).
   - Emits `TranslationProgress(currentIndex, total, currentTitle,
     resolved, failed, resolution)` per anime for UI live update.
3. **Build `BackupContainer`** (`buildContainer()`) — for each resolved
   anime:
   - `BackupEntry.Library` (favorites only)
   - `BackupEntry.AnimeDetails` (all resolved — title/desc/genres from
     AniList, fallback to source)
   - `BackupEntry.Episodes` keyed by `anilistId.toString()` (episode
     URL/name/number/scanlator/seen/bookmark/lastSecondSeen/totalSeconds/
     sourceOrder/dateFetch/dateUpload/fillermark/summary/previewUrl)
   - `BackupEntry.Categories` (categories list + anime→category links
     with `animeId = res.anilistId.toLong()`)
   - `BackupEntry.WatchProgress` keyed by `"${anilistId}:${episodeUrl}"`
     (positionSeconds from `history.readDuration.toInt()`,
     updatedAt from `history.lastRead`)
   - `BackupEntry.Tracker` (`TrackerTrackItem`s with animeId remapped
     to anilistId, trackerId = syncId, remoteId = mediaId)
   - `BackupEntry.SourceLinks` keyed by `anilistId.toString()` →
     `(sourceId, animeUrl, animeTitle)`
4. Hand the in-memory `BackupContainer` to
   `BackupManager.restoreBackupFromContainer()` (skips serialize→file→
   deserialize — direct provider import).

### Fallback direct-mapping path (`AniyomiBackupFormat.mapToContainer()`)

A second, simpler code path exists that **doesn't resolve AniList IDs**:
it maps each anime to an `index` (0, 1, 2, …) and uses that as the
key. Source links are only emitted when an AniList tracker binding
exists. This path is used when the caller wants a "dumb" import
(without AniList lookups). ANI-KUTA should keep this as a fallback.

### Restore orchestration (`BackupManager.kt`)

- `restoreBackup(input, options?)`:
  1. `BackupFormatDetector.detect(bytes)` → `ANIKUTA` (ZIP magic) or
     `ANIYOMI` (gzip magic) — falls back to trying each format.
  2. `format.read(input)` → `BackupContainer`.
  3. Validate `schemaVersion in SUPPORTED_VERSIONS`.
  4. **Sort entries** by `entryPriority` — AnimeDetails(0) → Library(1)
     → Episodes(2) → Categories(3) → WatchProgress(4) →
     EpisodeMetadata(5) → SourceLinks(6) → Tracker(7) →
     Preferences(8) → CoverImages(9). This ordering ensures anime rows
     exist in the DB before CategoryBackupProvider tries to insert
     `anime_category` junction rows.
  5. For each entry: `providerMap[entry.providerId]?.import(entry)`.
     Missing providers are skipped (graceful — supports partial
     restores).
  6. Returns `RestoreSummary` with per-category
     `RestoreCategoryResult(importedCount, skippedCount, errorCount)`.
- `restoreBackupFromContainer(container)` — same as above but starts
  from an in-memory container (used by the translator path).

### Provider registry (Koin footgun — preserved as a fix)

```kotlin
// BackupModule.kt — CRITICAL pattern
single<List<BackupProvider>>(named("backupProviders")) {
    listOf(
        LibraryBackupProvider(),
        AnimeDetailsBackupProvider(),
        EpisodeBackupProvider(),
        EpisodeMetadataBackupProvider(),
        WatchProgressBackupProvider(),
        CategoryBackupProvider(),
        TrackerBackupProviderAdapter(),
        SourceLinkBackupProvider(),
        PreferencesBackupProvider(),
        CoverImageProvider(),
    )
}
```

The old project's docstring explicitly warns: **multiple
`single<BackupProvider>` with no qualifier would silently overwrite**
in Koin (only the last registration wins). The fix is the
`named("backupProviders")` `List<BackupProvider>` binding. ANI-KUTA
MUST preserve this pattern.

---

## Common Data Model

All four apps share the same conceptual categories — they all derive
from Tachiyomi/Mihon, even Mangayomi (which re-implements the same
schema in Dart/Isar). The mapping table:

| Category | Aniyomi `.tachibk` | Animiru `.tachibk` | Mangayomi `.backup` | Anikku `.tachibk` |
|---|---|---|---|---|
| Library entries | `backupAnime` (favorite=true) | `backupAnime` (favorite=true) | `manga` (favorite=true) | `backupAnime` (favorite=true) |
| Anime details | `BackupAnime` (artist/author/desc/genre/status/thumb) | same | `Manga` (itemType=anime) | same |
| Episodes | `BackupAnime.episodes` (`BackupEpisode`) | same | `chapters` (`Chapter`, itemType=anime) | same |
| Categories | `backupAnimeCategories` + `BackupAnime.categories` (inline list of IDs) | same | `categories` + `Manga.categories` (List<int>) | same |
| Tracker bindings | `BackupAnime.tracking` (`BackupAnimeTracking` with syncId/mediaId) | same | `tracks` (`Track` with syncId/mediaId) | same |
| Watch history | `BackupAnime.history` (`BackupAnimeHistory`: url+lastRead+readDuration) | same | `history` (`History`: chapterId+date+readingTimeSeconds) | same |
| Sources | `backupAnimeSources` (name + sourceId) | same | `extensions` (`Source`) | same |
| Extensions | `backupExtensions` + `backupAnimeExtensionRepo` | (legacy: omitted) | (not in JSON — uses installed extensions) | `backupExtensions` |
| App preferences | `backupPreferences` | same | `settings` (key-value list) | same |
| Source preferences | `backupSourcePreferences` | `backupExtensionPreferences` | `extensions_preferences` | same |
| Tracker tokens | inside `backupPreferences` (sensitive) | same | `trackPreferences` (per-syncId) | same |
| Custom buttons | `backupCustomButton` | (legacy: omitted) | `customButtons` | `backupCustomButton` |
| Cover images | **URLs only** (not embedded) | same | URLs only (Manga has no embedded bytes; `customCoverImage` is for user-set local covers) | same |

**Shared identity model:** All four apps link an entry to external
trackers via the same `(syncId, mediaId)` pair. `syncId` semantics:

| syncId | Tracker | Notes |
|---|---|---|
| 1 | MyAnimeList (MAL) | Universal — anime + manga |
| 2 | AniList | Universal — anime + manga (the canonical ID for ANI-KUTA) |
| 3 | Kitsu | Universal |
| 4 | Shikimori | Anime + manga |
| 5 | Bangumi (bgm.tv) | Anime + manga |
| 6 | Komga | Self-hosted manga server |
| 7 | MangaUpdates (Baka-Updates) | Manga only |
| 8 | Anime-Planet | Newer |
| 9 | MangaDex | Newer |
| 10 | Simkl | Newer |
| 11 | Kodi | Newer (via add-on) |

(ANI-KUTA only needs syncId=2 (AniList) and syncId=1 (MAL, via
`AniListApi.searchByMalId`) for canonical resolution. Other trackers
should be stored as `ExternalReference`s but not used for primary
identity.)

---

## Import Strategy Recommendation

### Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                  BackupManager (orchestrator)                    │
│  ─ createBackup(options, output)  ─ restoreBackup(input, options)│
└───────┬──────────────────────────────────────┬──────────────────┘
        │ format detection                      │ per-category import
        ▼                                       ▼
┌──────────────────┐               ┌─────────────────────────────────┐
│ BackupFormat     │               │ List<BackupProvider>            │
│  detector+reader │               │  (Koin named("backupProviders"))│
└──────┬───────────┘               └─────────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────────────────────┐
│  BackupImporter  (NEW — one impl per external format)│
│   ├─ AniyomiTachibkImporter  (.tachibk, modern+legacy)│
│   ├─ MangayomiBackupImporter (.backup JSON-in-zip)    │
│   └─ KotatsuImporter         (future — folder/zip)    │
└──────┬──────────────────────────────────────────────┘
       │ emits
       ▼
┌──────────────────────────────────────────────────────┐
│  AnikutaBackupContainer  (in-memory, pre-provider)    │
│  (same shape as the old BackupContainer)              │
└──────┬──────────────────────────────────────────────┘
       │ handed to BackupManager.restoreBackupFromContainer()
       ▼
   existing provider pipeline
```

### `BackupImporter` interface

```kotlin
interface BackupImporter {
    /** Stable ID — "aniyomi", "animiru", "mangayomi", "anikku", "kotatsu". */
    val sourceApp: String

    /** Detect by file extension + magic bytes. Called in registration order. */
    fun detect(input: InputStream): Boolean

    /** Decode + map to ANI-KUTA's BackupContainer. Resolves AniList IDs. */
    suspend fun import(
        input: InputStream,
        progress: StateFlow<ImportProgress?>,
    ): BackupImporterResult
}

sealed class BackupImporterResult {
    data class Success(val container: BackupContainer, val stats: ImportStats) : BackupImporterResult()
    data class PartialSuccess(val container: BackupContainer, val stats: ImportStats, val unresolved: List<UnresolvedEntry>) : BackupImporterResult()
    data class Error(val message: String, val cause: Throwable?) : BackupImporterResult()
}

data class UnresolvedEntry(
    val title: String,
    val sourceApp: String,
    val sourceId: Long,
    val url: String,
    val reason: String,         // "no tracker", "no AniList match", "rate limited"
    val retryable: Boolean,
)
```

### Per-format impls

1. **`AniyomiTachibkImporter`** — handles **Aniyomi, Animiru, Anikku**
   (all three use the same protobuf wire format). Reuse the old
   project's `AniyomiBackupFormat` + `AniyomiBackupTranslator`:
   - Try `AniyomiBackup` (modern, anime@501) first.
   - Fall back to `AniyomiLegacyBackup` (legacy, anime@3) — handles
     Animiru.
   - Resolve AniList IDs via the translator's strategy chain
     (AniList tracker → MAL tracker → title search).
   - Emit per-anime `ImportProgress` for UI live updates.
   - Build `BackupContainer` with all 7 entry types (Library,
     AnimeDetails, Episodes, Categories, WatchProgress, Tracker,
     SourceLinks).
   - Hand to `BackupManager.restoreBackupFromContainer()`.
   - **No need for 3 separate importers** — one importer + format
     detection handles all three apps.

2. **`MangayomiBackupImporter`** — new impl. Pipeline:
   1. Open zip → read first `.backup.db` entry → `jsonDecode`.
   2. For each `manga` row with `favorite == true` AND
      `itemType == anime` (skip manga — ANI-KUTA is anime-first per
      ADR-009):
      - Look up the matching `tracks` row(s) (joined on `mangaId`).
      - Resolve AniList ID via the same strategy chain (syncId=2 →
        direct; syncId=1 → `searchByMalId`; else title search).
      - Map to `AnimeBackup` (using AniList data when available, else
        source-side `name`/`link`/`imageUrl`).
   3. For each `chapters` row (where parent manga is anime): map to
      `EpisodeBackup`.
   4. For each `history` row: map to `WatchProgressItem` (keyed by
      `anilistId:chapterUrl`).
   5. For each `categories` row + `manga.categories`: map to
      `CategoryBackup` + `AnimeCategoryBackup` links.
   6. For each `tracks` row: map to `TrackerTrackItem`.
   7. For each `extensions` row: emit a `SourceLinkItem` (sourceId +
      url + title) — but since Mangayomi sources are name-string-based
      (not the Aniyomi long-sourceId), we may need a source-name →
      source-id mapping table.
   8. Build `BackupContainer`, hand to
      `BackupManager.restoreBackupFromContainer()`.

### Mapping to ContentUID + ExternalReference (ADR-050)

The translator must emit **both**:

1. **`ContentId`** — the canonical content identity (e.g.
   `"al:154587"` for AniList ID 154587). This survives source switches.
2. **`ExternalReference`** entries recording the original app's
   identity, so a re-import or a future migration can detect duplicates:
   ```kotlin
   data class ExternalReference(
       val sourceApp: String,         // "aniyomi" | "animiru" | "mangayomi" | "anikku"
       val sourceId: Long,            // original extension sourceId (Aniyomi/Animiru/Anikku)
                                      // OR the Mangayomi source name for Mangayomi
       val url: String,               // original per-source URL
       val anilistId: Long?,          // resolved AniList ID (null if unresolved)
       val malId: Long?,              // resolved MAL ID (if from MAL tracker)
       val trackers: Map<Int, Long>,  // all syncId → mediaId pairs from the source backup
   )
   ```
   This way:
   - **Resolved** entries (with AniList ID) become first-class ANI-KUTA
     anime — they show up in the library with full AniList metadata.
   - **Unresolved** entries are kept as `ExternalReference`-only rows
     (visible in a "needs review" inbox) — the user can manually link
     them later from the anime-details page.

### Multi-binding registration (Koin)

```kotlin
// di/BackupImporterModule.kt
single<List<BackupImporter>>(named("backupImporters")) {
    listOf(
        AniyomiTachibkImporter(get()),           // handles Aniyomi + Animiru + Anikku
        MangayomiBackupImporter(get()),
        // KotatsuImporter(get()),  // future
    )
}
```

The `BackupManager.restoreBackup(input)` flow becomes:

1. Try `BackupFormatDetector` → if ANIKUTA, use `AnikutaBackupFormat`.
2. Else, iterate `getAll<List<BackupImporter>>("backupImporters")` →
   first one whose `detect()` returns true wins.
3. `importer.import(input, progress)` → returns `BackupContainer`.
4. `restoreBackupFromContainer(container)` → existing provider
   pipeline runs.

---

## ANI-KUTA's Own Backup Format

**Recommendation: keep the old project's `.anikuta` format, with a
schema-version bump.**

### File layout

```
backup.anikuta  (ZIP)
├── meta.json.gz   ← gzipped JSON of BackupContainer (polymorphic BackupEntry)
└── covers/        ← optional, when COVER_IMAGES category selected
    ├── 154587.jpg
    └── 101534.jpg
```

### Why this format

| Property | .anikuta (ZIP+gz+JSON) | .tachibk (protobuf) | .backup (ZIP+JSON) |
|---|---|---|---|
| Human-readable (debuggable) | ✅ (after gunzip) | ❌ | ✅ |
| Compact (small file size) | ✅ (gzip) | ✅✅ (proto) | ✅ (zip deflate) |
| Bundles cover images | ✅ (zip entry per cover) | ❌ | ❌ |
| Forward-compat (unknown fields skipped) | ✅ (`ignoreUnknownKeys=true`) | ✅ | ✅ |
| Backward-compat (old app reads new) | ✅ (with `ignoreUnknownKeys`) | ✅ | ✅ |
| Schema version | ✅ (`schemaVersion: Int`) | ⚠️ (implicit via field numbers) | ✅ (`version: "2"`) |
| Polymorphic entries | ✅ (kotlinx `classDiscriminator="type"`) | ✅ (sealed class) | ❌ (manual key dispatch) |
| Cross-platform reader | trivial (any JSON lib) | needs proto schema | trivial |

The `.anikuta` format wins on debuggability + cover bundling. The only
downside vs protobuf is file size (~30–40% larger for the same data,
mitigated by gzip).

### Schema version bump

The old project shipped `CURRENT_SCHEMA_VERSION = 1`. ANI-KUTA should
bump to **`SCHEMA_VERSION = 2`** because the rebuild introduces the
two-tier identity system (`ContentId` + `ExternalReference`) — a
breaking change to the `AnimeBackup` model. The `BackupManager` should
support restoring both v1 (old ANIKUTA backups) and v2 (new ANI-KUTA
backups) via `SUPPORTED_VERSIONS = 1..2`, with a v1→v2 migrator.

### `BackupContainer` v2 (proposed)

```kotlin
@Serializable
data class BackupContainer(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,    // = 2
    val createdAt: Long,
    val appVersion: String,
    val deviceName: String,
    val sourceApp: String = "anikuta",                  // NEW — "anikuta" | "aniyomi" | ...
    val entries: List<BackupEntry> = emptyList(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
        val SUPPORTED_VERSIONS = 1..2
    }
}
```

The 10 `BackupEntry` subclasses stay the same, but `AnimeBackup` gains
the new identity fields:

```kotlin
@Serializable
data class AnimeBackup(
    // existing fields...
    val anilistId: Long?,                  // ← ContentId canonical
    val contentId: String? = null,         // NEW — "al:154587" (v2)
    val externalReferences: List<ExternalReference> = emptyList(),  // NEW (v2)
)
```

### Auto-backup

Preserve `AutoBackupWorker` + `AutoBackupScheduler` (WorkManager
periodic). Backup filename: `anikuta_<timestamp>.anikuta` (unique
prefix to avoid collision with other forks' backups in the same SAF
folder — per the Aniyomi docs warning about fork-specific backup
prefixes).

### Don't support exporting to Aniyomi format

The old project was **restore-only** for Aniyomi (write throws
`UnsupportedOperationException`). ANI-KUTA should follow this: there's
no user benefit to exporting in Aniyomi format (Aniyomi/Animiru/Anikku
can't be installed alongside ANI-KUTA anyway, and the data model is
strictly richer on the ANI-KUTA side — exporting would lose
`ContentId` + `ExternalReference` info).

---

## Open Questions / Follow-ups

1. **Mangayomi source-name → Aniyomi sourceId mapping.** Mangayomi
   stores `source` as a string name, not a numeric ID. When importing
   to ANI-KUTA, we need a registry that maps `("mangayomi",
   sourceName)` → ANI-KUTA extension `sourceId`. May require users to
   install the equivalent extension first (and the importer prompts for
   it).
2. **Kotatsu import.** Mangayomi already supports Kotatsu backups
   (folder/zip with `categories` + `favourites` JSON files). ANI-KUTA
   should add a `KotatsuImporter` as a fast-follow — Kotatsu is a
   popular manga reader but has anime-adjacent users.
3. **Preference translation.** The old project skipped Aniyomi
   preferences entirely (the `PreferenceValue` sealed-class wire format
   is fragile). ANI-KUTA should continue skipping preferences on
   import — they're app-specific anyway and rarely map cleanly. User
   re-configures settings after migration.
4. **Resume-position fidelity for Mangayomi.** Mangayomi's `History`
   has `readingTimeSeconds` but no `lastSecondSeen`/`totalSeconds` per
   episode — the per-episode resume position lives in `Chapter`
   (`lastPageRead`/`totalPages` for manga; `lastSecondSeen`/
   `totalSeconds` for anime). The importer must join `history` →
   `chapter` to reconstruct the equivalent of Aniyomi's
   `BackupAnimeHistory` + `BackupEpisode.lastSecondSeen`.
5. **Rate-limit retry UX.** The translator's `retryRateLimited()` path
   needs UI support (a "Retry remaining N entries" button on the
   import-progress screen). Preserve from the old project's
   `AniyomiRestoreFlow.kt`.
