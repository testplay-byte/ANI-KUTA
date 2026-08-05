# Phase C — Content Identity System (Final Plan v4)

> **Status:** FINAL — ready for implementation
> **Date:** Post-Phase B, session web-f53f0459
> **Scope:** This session focuses ONLY on the content identity system (main table + detail tables + lookup tables). Watch progress, library, history, and tracking tables are DEFERRED to later sessions.

---

## 1. Overview — The Two-ID System (One Table)

Per user's design, there are **TWO ID concepts** stored in ONE main `content` table:

### 1.1 Main ID (stable, internal)
- A randomly generated UUID (e.g. `a1b2c3d4-e5f6-7890-abcd-ef1234567890`).
- Assigned once when a content is first seen.
- **NEVER changes** — not when sources switch, not when data sources change.
- Used as the primary key for ALL data stores (watch progress, library, history — when those are built).
- **NOT shown in the UI** — internal tracking only.

### 1.2 Content ID (changing, structured)
- A **structured keyword string** — NOT random.
- Deterministically generated from the current source configuration.
- Encodes: data source + system + repo + extension + source ID + anime URL.
- **Changes** when the user switches sources.
- Used for **quick identification** + **overlapping detection**.
- **NOT shown in the UI** — internal tracking only.

### 1.3 Why both in one table?
The Main ID and Content ID are two columns on the same `content` row. The Main ID is the primary key (stable). The Content ID is a regular column (regenerated when sources change). All detail tables link to the Main ID.

---

## 2. Content ID Format (v2 — with extension ID + source ID)

### 2.1 Format

```
{dataSource}:{system}:{repoUrl|none}:{extensionPkg|none}:{sourceId|none}:{animeUrl|none}
```

**6 sections** (was 5 — added `sourceId`).

### 2.2 Why repoId instead of repoUrl?

The repo URL is very long (e.g. `https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json`) and contains colons (`:`), which would break the colon-delimited Content ID format. Instead, we use the repo's DB ID (an integer like `1`, `2`) — short, clean, and unambiguous. The full URL is stored in the `extension_repos` lookup table.

### 2.3 Field breakdown

| # | Field | Description | Example values | Source |
|---|-------|-------------|----------------|--------|
| 1 | `dataSource` | Which metadata/data source provides the info | `anilist`, `tmdb`, `kitsu`, `mal`, `none` | `data_sources.name` |
| 2 | `system` | Which extension system is used | `aniyomi`, `cloudstream`, `sora`, `mangayomi`, `none` | `systems.name` |
| 3 | `repoId` | Extension repository DB ID (or `none`) | `1`, `2`, `none` | `extension_repos.id` |
| 4 | `extensionPkg` | Extension package name (or `none`) | `com.aniyomi.anikoto`, `none` | `extensions.pkgName` |
| 5 | `sourceId` | Internal source ID within the extension (or `none`) | `69023`, `none` | `extensions.sourceId` |
| 6 | `animeUrl` | The content's URL on that source (or `none`) | `https://anikoto.cc/anime/frieren`, `none` | `content.animeUrl` |

### 2.4 Example Content IDs

```
anilist:aniyomi:1:com.aniyomi.anikoto:69023:https://anikoto.cc/anime/frieren
tmdb:aniyomi:1:com.aniyomi.anikoto:69023:https://anikoto.cc/anime/frieren
anilist:none:none:none:none:none
none:aniyomi:2:com.example.ext:69024:https://source.com/anime/123
```

### 2.5 Overlapping detection

If two content records have the **same Content ID**, they're the same content from the same source. The app can detect duplicates + offer to merge. This logic is **future work** — Phase C just stores the Content ID.

---

## 3. Database Schema (Final — 9 tables this session)

### 3.1 Architecture overview

```
┌─────────────────────────────────────────────────────────────┐
│  LOOKUP TABLES (normalized — seeded once, rarely change)    │
│  ────────────────────────────────────────────────           │
│  data_sources    systems    extension_repos    extensions   │
│  (AniList, TMDB) (Aniyomi)  (repo URLs)        (installed)  │
└─────────────────────────────────────────────────────────────┘
          │ FK                    │ FK
          │                       │
          ▼                       ▼
┌─────────────────────────────────────────────────────────────┐
│  MAIN TABLE                                                 │
│  ─────────                                                  │
│  content                                                    │
│  ├── mainId (PK, stable UUID)                               │
│  ├── contentId (structured string, changes)                 │
│  ├── title (anime name)                                     │
│  ├── contentType (anime/manga/novel/movie/series)           │
│  ├── contentFormat (video/image/text/audio)                 │
│  ├── description (brief — for display when no detail linked)│
│  ├── dataSourceId (FK → data_sources)                       │
│  ├── systemId (FK → systems)                                │
│  ├── extensionRepoId (FK → extension_repos, null)           │
│  ├── extensionId (FK → extensions, null)                    │
│  ├── sourceId (internal source ID, null)                    │
│  ├── animeUrl (the content's URL, null)                     │
│  ├── displaySource (which detail table's data is shown)     │
│  ├── createdAt, updatedAt                                   │
└─────────────────────────────────────────────────────────────┘
          │ mainId (FK)
          │
    ┌─────┼─────────────┬──────────────────┐
    │     │             │                  │
    ▼     ▼             ▼                  ▼
┌────────────┐  ┌────────────────┐  ┌──────────────────────┐
│ anilist_   │  │ extension_     │  │ other_source_       │
│ details    │  │ details        │  │ details             │
│            │  │                │  │ (future: TMDB, etc.)│
│ Per-source │  │ Per-source     │  │ Per-source           │
│ metadata   │  │ metadata       │  │ metadata             │
└────────────┘  └────────────────┘  └──────────────────────┘
```

### 3.2 Design philosophy

- **Main `content` table** holds the IDENTITY + core display info (title, type, format, description).
- **Detail tables** hold SOURCE-SPECIFIC metadata (one row per linked source per content).
  - `anilist_details` — score, episodes, season, genres, synopsis, coverUrl, bannerUrl, idMal
  - `extension_details` — extension-provided description, genres, status, author, artist
  - `other_source_details` — generic key-value for future sources (TMDB, Kitsu)
- **Lookup tables** hold the normalized source/system/repo/extension data (seeded once).
- **Deferred tables** (watch_progress, library, watch_history, tracking) — NOT in this session.

---

## 4. Table Definitions

### 4.1 Lookup Table: `data_sources`

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

---

### 4.2 Lookup Table: `systems`

Stores the extension systems (the frameworks that provide extension sources).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | INTEGER | PK AUTOINCREMENT | |
| `name` | TEXT | NOT NULL UNIQUE | `aniyomi`, `cloudstream`, `sora`, `mangayomi` |
| `displayName` | TEXT | NOT NULL | `Aniyomi`, `CloudStream`, `Sora`, `MangaYomi` |
| `packagePrefix` | TEXT | | `eu.kanade.tachiyomi` |
| `createdAt` | INTEGER | NOT NULL | epoch millis |

**Demo rows:**

| id | name | displayName | packagePrefix |
|----|------|-------------|---------------|
| 1 | `aniyomi` | `Aniyomi` | `eu.kanade.tachiyomi` |
| 2 | `cloudstream` | `CloudStream` | `com.lagradost.cloudstream` |
| 3 | `sora` | `Sora` | `com.sora` |

---

### 4.3 Lookup Table: `extension_repos`

Stores extension repository URLs per system. The URL points to the `index.min.json` file.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | INTEGER | PK AUTOINCREMENT | |
| `systemId` | INTEGER | NOT NULL FK → systems(id) | |
| `url` | TEXT | NOT NULL | full URL to `index.min.json` |
| `displayName` | TEXT | | human-readable name |
| `createdAt` | INTEGER | NOT NULL | epoch millis |

**Demo rows:**

| id | systemId | url | displayName |
|----|----------|-----|-------------|
| 1 | 1 | `https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json` | `Yuzono Anime Repo` |
| 2 | 1 | `https://raw.githubusercontent.com/aniyomiorg/aniyomi-extensions/repo/index.min.json` | `Aniyomi Official` |

---

### 4.4 Lookup Table: `extensions`

Stores specific installed extensions.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | INTEGER | PK AUTOINCREMENT | our DB ID |
| `systemId` | INTEGER | NOT NULL FK → systems(id) | |
| `repoId` | INTEGER | FK → extension_repos(id) | null if installed from unknown source |
| `pkgName` | TEXT | NOT NULL | `com.aniyomi.anikoto` |
| `name` | TEXT | NOT NULL | `AniKoto` |
| `sourceId` | INTEGER | NOT NULL | internal source ID within the extension (e.g. 69023) |
| `versionName` | TEXT | | `1.4.3` |
| `isNsfw` | INTEGER | NOT NULL DEFAULT 0 | |
| `createdAt` | INTEGER | NOT NULL | epoch millis |

**Demo rows:**

| id | systemId | repoId | pkgName | name | sourceId | versionName | isNsfw |
|----|----------|--------|---------|------|----------|-------------|--------|
| 1 | 1 | 1 | `com.aniyomi.anikoto` | `AniKoto` | 69023 | `1.4.3` | 0 |
| 2 | 1 | 1 | `com.aniyomi.gogoanime` | `GogoAnime` | 69024 | `1.4.2` | 0 |

---

### 4.5 Main Table: `content`

The central content record. One row per anime.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `mainId` | TEXT | PK | stable UUID (e.g. `a1b2c3d4-e5f6-...`) |
| `contentId` | TEXT | NOT NULL | structured string (regenerated on source change) |
| `title` | TEXT | NOT NULL | `Frieren: Beyond Journey's End` |
| `contentType` | TEXT | NOT NULL DEFAULT `anime` | `anime`/`manga`/`novel`/`movie`/`series` |
| `contentFormat` | TEXT | NOT NULL DEFAULT `video` | `video`/`image`/`text`/`audio` |
| `description` | TEXT | | brief description (fallback when no detail linked) |
| `dataSourceId` | INTEGER | FK → data_sources(id) | which metadata source (null if none) |
| `systemId` | INTEGER | FK → systems(id) | which extension system (null if none) |
| `extensionRepoId` | INTEGER | FK → extension_repos(id) | null if no repo |
| `extensionId` | INTEGER | FK → extensions(id) | null if no extension |
| `sourceId` | INTEGER | | internal source ID (from extension, null if none) |
| `animeUrl` | TEXT | | the content's URL on the source |
| `displaySource` | TEXT | NOT NULL DEFAULT `extension` | `anilist`/`extension`/`tmdb`/`kitsu` (which detail to show) |
| `createdAt` | INTEGER | NOT NULL | epoch millis |
| `updatedAt` | INTEGER | NOT NULL | epoch millis |

**Demo rows:**

| mainId | contentId | title | contentType | contentFormat | dataSourceId | systemId | extensionId | sourceId | animeUrl | displaySource |
|--------|-----------|-------|-------------|---------------|--------------|----------|-------------|----------|----------|---------------|
| `a1b2c3d4-...` | `anilist:aniyomi:1:com.aniyomi.anikoto:69023:https://anikoto.cc/anime/frieren` | `Frieren: Beyond Journey's End` | `anime` | `video` | 1 | 1 | 1 | 69023 | `https://anikoto.cc/anime/frieren` | `anilist` |
| `c3d4e5f6-...` | `none:aniyomi:1:com.aniyomi.anikoto:69023:https://anikoto.cc/anime/obscure` | `Obscure Anime` | `anime` | `video` | null | 1 | 1 | 69023 | `https://anikoto.cc/anime/obscure` | `extension` |
| `e5f6a7b8-...` | `anilist:none:none:none:none:none` | `Solo Leveling` | `anime` | `video` | 1 | null | null | null | null | `anilist` |

---

### 4.6 Detail Table: `anilist_details`

Stores AniList-specific metadata for a content. One row per content (if linked to AniList).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `mainId` | TEXT | PK FK → content(mainId) ON DELETE CASCADE | |
| `anilistId` | INTEGER | NOT NULL | AniList anime ID (e.g. 154587) |
| `idMal` | INTEGER | | MAL ID (from AniList) |
| `score` | INTEGER | | 0-100 |
| `episodes` | INTEGER | | episode count |
| `season` | TEXT | | `WINTER`/`SPRING`/`SUMMER`/`FALL` |
| `seasonYear` | INTEGER | | 2023 |
| `status` | TEXT | | `RELEASING`/`FINISHED`/`CANCELLED` |
| `genres` | TEXT | | comma-separated: `Adventure, Drama, Fantasy` |
| `synopsis` | TEXT | | full description |
| `coverUrl` | TEXT | | cover image URL |
| `bannerUrl` | TEXT | | banner image URL |
| `updatedAt` | INTEGER | NOT NULL | epoch millis (last AniList fetch) |

**Demo rows:**

| mainId | anilistId | idMal | score | episodes | season | seasonYear | status | genres | synopsis | coverUrl | bannerUrl |
|--------|-----------|-------|-------|----------|--------|------------|--------|--------|----------|----------|-----------|
| `a1b2c3d4-...` | 154587 | 52991 | 82 | 28 | `FALL` | 2023 | `FINISHED` | `Adventure, Drama, Fantasy` | `Frieren and her party...` | `https://.../frieren-cover.jpg` | `https://.../frieren-banner.jpg` |
| `e5f6a7b8-...` | 154587 | 52991 | 82 | 28 | `FALL` | 2023 | `FINISHED` | `Adventure, Drama, Fantasy` | `Solo Leveling...` | `https://.../sololeveling-cover.jpg` | `https://.../sololeveling-banner.jpg` |

---

### 4.7 Detail Table: `extension_details`

Stores extension-specific metadata for a content. One row per content (if linked to an extension source).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `mainId` | TEXT | PK FK → content(mainId) ON DELETE CASCADE | |
| `extensionId` | INTEGER | NOT NULL FK → extensions(id) | which extension provided this |
| `sourceId` | INTEGER | NOT NULL | internal source ID |
| `animeUrl` | TEXT | NOT NULL | the content's URL on this extension |
| `description` | TEXT | | extension-provided description |
| `genres` | TEXT | | comma-separated |
| `status` | TEXT | | extension status code (1=RELEASING, 2=FINISHED, etc.) |
| `author` | TEXT | | |
| `artist` | TEXT | | |
| `thumbnailUrl` | TEXT | | extension-provided thumbnail |
| `updatedAt` | INTEGER | NOT NULL | epoch millis (last extension fetch) |

**Demo rows:**

| mainId | extensionId | sourceId | animeUrl | description | genres | status | author | artist | thumbnailUrl |
|--------|-------------|----------|----------|-------------|--------|--------|--------|--------|--------------|
| `a1b2c3d4-...` | 1 | 69023 | `https://anikoto.cc/anime/frieren` | `Frieren's journey...` | `Adventure, Fantasy` | 2 | `Yamada Kanehito` | `Tsukasa Abe` | `https://anikoto.cc/img/frieren.jpg` |
| `c3d4e5f6-...` | 1 | 69023 | `https://anikoto.cc/anime/obscure` | `An obscure anime...` | `Drama` | 2 | null | null | `https://anikoto.cc/img/obscure.jpg` |

---

### 4.8 Detail Table: `other_source_details`

Generic key-value table for future data sources (TMDB, Kitsu, MAL, etc.). Allows storing source-specific fields without adding a new table per source.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | INTEGER | PK AUTOINCREMENT | |
| `mainId` | TEXT | NOT NULL FK → content(mainId) ON DELETE CASCADE | |
| `sourceType` | TEXT | NOT NULL | `tmdb`/`kitsu`/`mal`/`custom` |
| `sourceRefId` | TEXT | NOT NULL | the ID on that source (e.g. TMDB ID `12345`) |
| `key` | TEXT | NOT NULL | field name (e.g. `score`, `synopsis`, `genres`) |
| `value` | TEXT | | field value (serialized as string) |
| `updatedAt` | INTEGER | NOT NULL | epoch millis |

**Demo rows:**

| id | mainId | sourceType | sourceRefId | key | value |
|----|--------|------------|-------------|-----|-------|
| 1 | `a1b2c3d4-...` | `tmdb` | `12345` | `score` | `8.2` |
| 2 | `a1b2c3d4-...` | `tmdb` | `12345` | `synopsis` | `TMDB's synopsis...` |
| 3 | `a1b2c3d4-...` | `tmdb` | `12345` | `genres` | `Adventure,Drama,Fantasy` |

---

## 5. Implementation Plan (This Session)

### 5.1 Scope

**In scope (this session):**
- Lookup tables: `data_sources`, `systems`, `extension_repos`, `extensions`
- Main table: `content`
- Detail tables: `anilist_details`, `extension_details`, `other_source_details`
- Content ID generator + resolver
- Integration with DetailsViewModel (store + retrieve content records)
- Console logging for Content ID (per user request — "we just need to have some proper console logging for the content ID")

**Deferred to later sessions:**
- `watch_progress` table
- `library` table
- `watch_history` table
- `tracking` / internal tracking system tables
- Overlapping detection logic
- Backup/restore

### 5.2 Steps

#### C.1 — Database schema
- Add SQLDelight `.sq` files for all 8 tables (4 lookup + 1 main + 3 detail).
- Add migrations (or use `db:push` since we're early in development).
- Seed `data_sources` + `systems` tables on first launch.

#### C.2 — Content module (`:core:content`)
- Create `:core:content` module.
- `ContentIdGenerator` — generates the structured Content ID string from source fields.
- `ContentRepository` — interface for CRUD on the content + detail tables.
- `ContentResolver` — resolves anilistId/sourceId/animeUrl → mainId (creates if not found).
- Register in Koin.

#### C.3 — Integrate with DetailsViewModel
- Add `mainId` + `contentId` fields to `UnifiedAnime`.
- `DetailsViewModel.loadFromAniList()` + `loadFromExtension()` call `ContentResolver`.
- `linkSource()` + `unlinkSource()` + `linkAniListEntry()` + `unlinkAniList()` update the content + detail tables.
- `switchDataSource()` updates `content.displaySource`.
- Content ID is regenerated on every source change + logged.

#### C.4 — Console logging
- Log Content ID generation (tag: `Anikuta:Core:Content:IdGen`).
- Log Content ID resolution (tag: `Anikuta:Core:Content:Resolver`).
- Log source link/unlink operations (tag: `Anikuta:Core:Content:Repo`).

---

## 6. Confirmed Decisions

| # | Question | Answer |
|---|----------|--------|
| Q-001 | Stable vs changing contentId | **Both**: stable Main ID (UUID) + changing Content ID (structured string) — in the SAME table |
| Q-002 | contentId generation | Main ID = UUID. Content ID = structured string (deterministic from sources) |
| Q-003 | contentType | `anime` for now. Future: `manga`, `novel`, `movie`, `series`. Also `contentFormat`: `video`/`image`/`text`/`audio` |
| Q-004 | Multiple extension sources per content? | **No** — one at a time. Switching = migrate (remove old, add new) |
| Q-005 | Default display source | User decides (data-source selector lets them pick) |
| Q-006 | contentId shown in UI? | **No** — internal only |
| Q-007 | Session scope | This session: content ID system + main + detail + lookup tables ONLY. Watch progress/library/history/tracking deferred. |
| Q-008 | Detail table approach | Separate tables per source type (`anilist_details`, `extension_details`, `other_source_details`) linked by mainId |
| Q-009 | Content ID sections | 6 sections: dataSource, system, repoId, extensionPkg, sourceId, animeUrl |
| Q-010 | Repo URL in Content ID | Use repo DB ID (integer) instead of full URL (URL is too long + contains colons) |

---

## 7. Future Work (NOT this session)

- **Watch progress** — `watch_progress` table keyed on mainId + episodeNumber.
- **Library** — `library` table keyed on mainId.
- **Watch history** — `watch_history` table keyed on mainId.
- **Internal tracking system** — activity events, user behavior tracking.
- **Overlapping detection** — detect duplicate Content IDs + offer merge.
- **Backup/restore** — export/import the content table.
- **Multi-system support** — CloudStream, Sora, MangaYomi extensions.
- **Multi-data-source** — TMDB, Kitsu, MAL providers (using `other_source_details`).
