# Phase C — Content Identity System (Plan v3)

> **Status:** FINAL DRAFT — awaiting user review
> **Date:** Post-Phase B, session web-f53f0459
> **Depends on:** Phase A (UnifiedAnime ✅) + Phase B (auto-link + data-source selector ✅)

---

## 1. Overview — The Two-ID System

Per user's design, there are **TWO separate ID concepts**:

### 1.1 Main ID (stable, internal)
- A randomly generated UUID.
- Assigned once when a content is first seen.
- **NEVER changes** — not when sources switch, not when data sources change.
- Used as the primary key for ALL data stores (watch progress, library, history).
- Only changes if the user deletes the app + reinstalls + opens the same anime (new UUID).
- **NOT shown in the UI** — internal tracking only.

### 1.2 Content ID (changing, structured)
- A **structured keyword string** — NOT random.
- Deterministically generated from the current source configuration.
- Encodes: data source + system + repo + extension + anime URL.
- **Changes** when the user switches sources (e.g. switches from AniList to TMDB, or from one extension to another).
- Used for **quick identification** + **overlapping detection** (if two entries have the same Content ID, they're duplicates from the same source).
- **NOT shown in the UI** — internal tracking only.

**Why both?**
- The Main ID guarantees data continuity (watch progress, library, history never orphaned).
- The Content ID enables deduplication (detect when the same content is opened from the same source twice).

---

## 2. Content ID Format

The Content ID is a colon-delimited string:

```
{dataSource}:{system}:{repoUrl|none}:{extensionPkg|none}:{animeUrl}
```

### 2.1 Field breakdown

| Field | Description | Example values |
|-------|-------------|----------------|
| `dataSource` | Which metadata/data source provides the info | `anilist`, `tmdb`, `kitsu`, `mal`, `none` |
| `system` | Which extension system is used | `aniyomi`, `cloudstream`, `sora`, `mangayomi`, `none` |
| `repoUrl` | Extension repository URL (or `none`) | `https://repo.example.com`, `none` |
| `extensionPkg` | Extension package name (or `none`) | `com.example.animeext`, `none` |
| `animeUrl` | The content's URL on that source | `https://animesite.com/anime/frieren` |

### 2.2 Example Content IDs

```
anilist:aniyomi:https://ani-kuta-repo.github.io:com.aniyomi.anikoto:https://anikoto.cc/anime/frieren
tmdb:aniyomi:none:com.aniyomi.anikoto:https://anikoto.cc/anime/frieren
anilist:none:none:none:none:none
none:aniyomi:https://repo.example.com:com.example.ext:https://source.com/anime/123
```

### 2.3 Overlapping detection

If two content records have the **same Content ID**, they're the same content from the same source. The app can:
- Detect duplicates (warn the user).
- Merge them (keep one Main ID, link all sources).
- This logic is **future work** — Phase C just stores the Content ID; overlap detection comes later.

---

## 3. Database Schema (Normalized)

### 3.1 Table overview

```
┌──────────────────┐     ┌──────────────────┐
│  data_sources    │     │  systems         │
│  (AniList, TMDB) │     │  (Aniyomi, Sora) │
└────────┬─────────┘     └────────┬─────────┘
         │                        │
         │ FK                     │ FK
         │                        │
┌────────▼─────────┐     ┌────────▼─────────┐
│ extension_repos  │     │  extensions      │
│ (repo URLs)      │────►│ (specific exts)  │
└──────────────────┘ FK  └────────┬─────────┘
                                  │ FK
                                  │
┌─────────────────────────────────▼──────────────┐
│  content (MAIN TABLE)                           │
│  ────────────────────────────────────────       │
│  mainId (PK, stable UUID)                       │
│  contentId (structured, changes)                │
│  title (anime name)                             │
│  contentType (anime/manga/novel/movie/series)   │
│  contentFormat (video/image/text)               │
│  dataSourceId (FK → data_sources)               │
│  systemId (FK → systems)                        │
│  extensionRepoId (FK → extension_repos, null)   │
│  extensionId (FK → extensions, null)            │
│  animeUrl (the actual URL)                      │
│  displaySource (which source's data is shown)   │
│  createdAt, updatedAt                           │
└────────────────────────────────────────────────┘
          │ FK
          │
    ┌─────┴──────┬──────────────┬──────────────┐
    │            │              │              │
┌───▼───┐  ┌────▼────┐  ┌──────▼──────┐  ┌───▼──────┐
│ watch │  │ library │  │ watch_      │  │ content_ │
│ _prog │  │         │  │ history     │  │ source_  │
│ ress  │  │         │  │             │  │ link     │
└───────┘  └─────────┘  └─────────────┘  └──────────┘
```

### 3.2 Table: `data_sources`

Stores the metadata/data sources (NOT extension sources — these provide info like synopsis, score, episodes).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | INTEGER | PK AUTOINCREMENT | |
| `name` | TEXT | NOT NULL UNIQUE | `anilist`, `tmdb`, `kitsu`, `mal` |
| `displayName` | TEXT | NOT NULL | `AniList`, `TMDB`, `Kitsu`, `MAL` |
| `type` | TEXT | NOT NULL DEFAULT `metadata` | `metadata`, `tracking` |
| `createdAt` | INTEGER | NOT NULL | epoch millis |

**Demo rows:**
| id | name | displayName | type |
|----|------|-------------|------|
| 1 | `anilist` | `AniList` | `metadata` |
| 2 | `tmdb` | `TMDB` | `metadata` |
| 3 | `kitsu` | `Kitsu` | `metadata` |
| 4 | `mal` | `MAL` | `metadata` |

### 3.3 Table: `systems`

Stores the extension systems (the frameworks that provide extension sources).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | INTEGER | PK AUTOINCREMENT | |
| `name` | TEXT | NOT NULL UNIQUE | `aniyomi`, `cloudstream`, `sora`, `mangayomi` |
| `displayName` | TEXT | NOT NULL | `Aniyomi`, `CloudStream`, `Sora`, `MangaYomi` |
| `packagePrefix` | TEXT | | `eu.kanade.tachiyomi`, `com.lagradost.cloudstream` |
| `createdAt` | INTEGER | NOT NULL | epoch millis |

**Demo rows:**
| id | name | displayName | packagePrefix |
|----|------|-------------|---------------|
| 1 | `aniyomi` | `Aniyomi` | `eu.kanade.tachiyomi` |
| 2 | `cloudstream` | `CloudStream` | `com.lagradost.cloudstream` |
| 3 | `sora` | `Sora` | `com.sora` |

### 3.4 Table: `extension_repos`

Stores extension repository URLs per system.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | INTEGER | PK AUTOINCREMENT | |
| `systemId` | INTEGER | NOT NULL FK → systems(id) | |
| `url` | TEXT | NOT NULL | `https://ani-kuta-repo.github.io` |
| `displayName` | TEXT | | `ANI-KUTA Extensions` |
| `createdAt` | INTEGER | NOT NULL | epoch millis |

**Demo rows:**
| id | systemId | url | displayName |
|----|----------|-----|-------------|
| 1 | 1 | `https://ani-kuta-repo.github.io` | `ANI-KUTA Extensions` |
| 2 | 1 | `https://aniyomi.org/repo` | `Aniyomi Official` |

### 3.5 Table: `extensions`

Stores specific installed extensions.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | INTEGER | PK AUTOINCREMENT | |
| `systemId` | INTEGER | NOT NULL FK → systems(id) | |
| `repoId` | INTEGER | FK → extension_repos(id) | null if no repo |
| `pkgName` | TEXT | NOT NULL | `com.aniyomi.anikoto` |
| `name` | TEXT | NOT NULL | `AniKoto` |
| `sourceId` | INTEGER | NOT NULL | internal source ID within the extension |
| `versionName` | TEXT | | `1.4.3` |
| `isNsfw` | INTEGER | NOT NULL DEFAULT 0 | |
| `createdAt` | INTEGER | NOT NULL | epoch millis |

**Demo rows:**
| id | systemId | repoId | pkgName | name | sourceId | versionName |
|----|----------|--------|---------|------|----------|-------------|
| 1 | 1 | 1 | `com.aniyomi.anikoto` | `AniKoto` | 69023 | `1.4.3` |
| 2 | 1 | 1 | `com.aniyomi.gogoanime` | `GogoAnime` | 69024 | `1.4.2` |

### 3.6 Table: `content` (MAIN TABLE)

The central content record. One row per anime.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `mainId` | TEXT | PK | stable UUID (e.g. `a1b2c3d4-e5f6-...`) |
| `contentId` | TEXT | NOT NULL | structured string (changes when sources switch) |
| `title` | TEXT | NOT NULL | `Frieren: Beyond Journey's End` |
| `contentType` | TEXT | NOT NULL DEFAULT `anime` | `anime`/`manga`/`novel`/`movie`/`series` |
| `contentFormat` | TEXT | NOT NULL DEFAULT `video` | `video`/`image`/`text` |
| `dataSourceId` | INTEGER | FK → data_sources(id) | which metadata source provides info |
| `systemId` | INTEGER | FK → systems(id) | which extension system |
| `extensionRepoId` | INTEGER | FK → extension_repos(id) | null if no repo |
| `extensionId` | INTEGER | FK → extensions(id) | null if no extension |
| `animeUrl` | TEXT | | the content's URL on the source |
| `displaySource` | TEXT | NOT NULL DEFAULT `extension` | `anilist`/`extension` (which data is shown) |
| `createdAt` | INTEGER | NOT NULL | epoch millis |
| `updatedAt` | INTEGER | NOT NULL | epoch millis |

**Demo rows:**
| mainId | contentId | title | contentType | contentFormat | dataSourceId | systemId | extensionId | animeUrl | displaySource |
|--------|-----------|-------|-------------|---------------|--------------|----------|-------------|----------|---------------|
| `a1b2-...` | `anilist:aniyomi:https://...:com.aniyomi.anikoto:https://anikoto.cc/anime/frieren` | `Frieren: Beyond Journey's End` | `anime` | `video` | 1 | 1 | 1 | `https://anikoto.cc/anime/frieren` | `anilist` |
| `c3d4-...` | `none:aniyomi:https://...:com.aniyomi.anikoto:https://anikoto.cc/anime/obscure` | `Obscure Anime` | `anime` | `video` | null | 1 | 1 | `https://anikoto.cc/anime/obscure` | `extension` |
| `e5f6-...` | `anilist:none:none:none:none:none` | `Solo Leveling` | `anime` | `video` | 1 | null | null | null | `anilist` |

### 3.7 Table: `content_source_link`

Tracks which sources are linked to a content (for overlapping detection + history).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | INTEGER | PK AUTOINCREMENT | |
| `mainId` | TEXT | NOT NULL FK → content(mainId) ON DELETE CASCADE | |
| `sourceType` | TEXT | NOT NULL | `anilist`/`tmdb`/`extension` |
| `sourceRef` | TEXT | NOT NULL | anilistId (e.g. `154587`) or animeUrl |
| `linkedAt` | INTEGER | NOT NULL | epoch millis |

**Note:** Only ONE extension source is active per content at a time (per user decision Q-004). When the user switches extension sources, the old link row is deleted + a new one is inserted. The `content` table's `extensionId` + `animeUrl` fields are also updated.

### 3.8 Table: `watch_progress` (NEW — not yet implemented)

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `mainId` | TEXT | NOT NULL FK → content(mainId) ON DELETE CASCADE | |
| `episodeNumber` | REAL | NOT NULL | `1.0`, `2.0`, etc. |
| `position` | REAL | NOT NULL | seconds |
| `duration` | REAL | NOT NULL | seconds |
| `completed` | INTEGER | NOT NULL DEFAULT 0 | 0 or 1 |
| `updatedAt` | INTEGER | NOT NULL | epoch millis |
| **PK** | | composite (mainId, episodeNumber) | |

### 3.9 Table: `library` (NEW — not yet implemented)

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `mainId` | TEXT | PK FK → content(mainId) ON DELETE CASCADE | |
| `addedAt` | INTEGER | NOT NULL | epoch millis |

### 3.10 Table: `watch_history` (NEW — not yet implemented)

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | INTEGER | PK AUTOINCREMENT | |
| `mainId` | TEXT | NOT NULL FK → content(mainId) ON DELETE CASCADE | |
| `episodeNumber` | REAL | NOT NULL | |
| `watchedAt` | INTEGER | NOT NULL | epoch millis |

---

## 4. Architecture (Kotlin modules)

### 4.1 New module: `:core:content`

```
core/content/
├── build.gradle.kts
└── src/main/java/com/confused/anikuta/core/content/
    ├── ContentId.kt              ← data class: mainId (UUID) + contentId (structured string)
    ├── ContentRecord.kt          ← data class: full content row
    ├── ContentIdGenerator.kt     ← generates the structured contentId string
    ├── ContentRepository.kt      ← interface: get/create/update/link/unlink
    ├── ContentResolver.kt        ← resolves anilistId/sourceId/animeUrl → ContentId
    └── ContentModule.kt          ← Koin DI
```

### 4.2 `ContentIdGenerator`

```kotlin
object ContentIdGenerator {
    /**
     * Generate the structured Content ID string.
     * Format: {dataSource}:{system}:{repoUrl|none}:{extensionPkg|none}:{animeUrl|none}
     */
    fun generate(
        dataSource: String?,     // "anilist", "tmdb", null
        system: String?,         // "aniyomi", "cloudstream", null
        repoUrl: String?,        // "https://...", null
        extensionPkg: String?,   // "com.aniyomi.anikoto", null
        animeUrl: String?,       // "https://anikoto.cc/anime/frieren", null
    ): String {
        return listOf(
            dataSource ?: "none",
            system ?: "none",
            repoUrl ?: "none",
            extensionPkg ?: "none",
            animeUrl ?: "none",
        ).joinToString(":")
    }
}
```

### 4.3 `ContentResolver`

```kotlin
class ContentResolver(private val repo: ContentRepository) {
    /** Resolve an AniList entry → ContentId. Creates if not found. */
    suspend fun resolveOrCreateForAniList(anilistId: Int, title: String): ContentId

    /** Resolve an extension entry → ContentId. Creates if not found. */
    suspend fun resolveOrCreateForExtension(
        systemId: Long, extensionId: Long, animeUrl: String, title: String
    ): ContentId

    /** Link an AniList ID to existing content (auto-link). mainId stays the same. */
    suspend fun linkAniList(mainId: String, anilistId: Int)

    /** Switch the extension source (removes old link, adds new). mainId stays the same. */
    suspend fun switchExtensionSource(
        mainId: String, newExtensionId: Long, newAnimeUrl: String
    )

    /** Switch the display source (updates content.displaySource). mainId stays the same. */
    suspend fun switchDisplaySource(mainId: String, source: String)

    /** Regenerate the contentId string (called after any source change). */
    suspend fun regenerateContentId(mainId: String)
}
```

---

## 5. Implementation Phases

### C.1 — Database schema + content module
- Add SQLDelight tables: `content`, `content_source_link`, `data_sources`, `systems`, `extension_repos`, `extensions`.
- Create `:core:content` module with `ContentRepository` + `ContentResolver` + `ContentIdGenerator`.
- Seed `data_sources` + `systems` tables on first launch.
- Register in Koin.

### C.2 — Integrate with DetailsViewModel
- Add `mainId` + `contentId` fields to `UnifiedAnime`.
- `DetailsViewModel.loadFromAniList()` + `loadFromExtension()` call `ContentResolver`.
- `linkSource()` + `unlinkSource()` + `linkAniListEntry()` + `unlinkAniList()` update the content record.
- `switchDataSource()` updates `displaySource` in the database.
- Content ID is regenerated on every source change.

### C.3 — Watch progress (uses mainId from the start)
- Add `watch_progress` table.
- `WatchProgressStore` keys on `mainId + episodeNumber`.
- `WatchKey` carries `mainId`.

### C.4 — Library (uses mainId from the start)
- Add `library` table.
- Library screen shows entries by `mainId`.
- Adding from any source links to the same `mainId`.

### C.5 — History (uses mainId from the start)
- Add `watch_history` table.
- History logs by `mainId`.

---

## 6. Confirmed Decisions (from user)

| # | Question | Answer |
|---|----------|--------|
| Q-001 | Stable vs changing contentId | **Both**: stable Main ID (UUID) + changing Content ID (structured string) |
| Q-002 | contentId generation | Main ID = UUID. Content ID = structured string (deterministic from sources) |
| Q-003 | contentType | `anime` for now. Future: `manga`, `novel`, `movie`, `series`. Also `contentFormat`: `video`/`image`/`text` |
| Q-004 | Multiple extension sources per content? | **No** — one at a time. Switching = migrate (remove old, add new) |
| Q-005 | Default display source when auto-link matches | User decides (no default — the data-source selector lets them pick) |
| Q-006 | contentId shown in UI? | **No** — internal only |

---

## 7. Future Work (NOT in Phase C)

- **Overlapping detection**: If two content records have the same Content ID, detect + offer to merge.
- **Backup/restore**: Export/import the `content` table (mainId + contentId + all links).
- **Multi-system support**: CloudStream, Sora, MangaYomi extensions (the schema supports this via the `systems` table).
- **Multi-data-source**: TMDB, Kitsu, MAL providers (the schema supports this via the `data_sources` table).
