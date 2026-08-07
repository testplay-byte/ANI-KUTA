# 17 — Database Schema Design

> The complete SQL schema for the ANI-KUTA app. This is the foundation — all Phase 3+ modules depend on it.
> SQLDelight syntax. Tables, indexes, foreign keys, and relationships.

---

## Overview

The database has **6 logical groups**:

| Group | Tables | Purpose |
|-------|--------|---------|
| **Identity** | 5 | ContentUID + ExternalReference + merge/split log |
| **Library** | 3 | User's saved anime, categories, statuses |
| **Watch** | 2 | Watch progress + history |
| **Downloads** | 2 | Download queue + downloaded files |
| **Trackers** | 2 | AniList/MAL sync links + sync state |
| **Extensions** | 2 | Installed sources + extension repos |
| **Metadata** | 2 | Content metadata cache + episode metadata cache |
| **App** | 1 | Key-value store for app flags (already exists) |
| **Activity** (deferred) | 1 | User activity event log (Phase 6) |
| **Ads** (deferred) | 1 | Ad impression log (Phase 6) |

**Total: 21 tables** (19 active + 2 deferred).

---

## Entity Relationship Diagram (text)

```
┌─────────────┐     ┌─────────────────────┐     ┌──────────────┐
│ content_uid │────<│ external_reference   │     │ category     │
│ (uid PK)    │     │ (id PK, uid FK)      │     │ (id PK)      │
└──────┬──────┘     └──────────────────────┘     └──────┬───────┘
       │                                                 │
       │           ┌──────────────────────┐             │
       ├──────────<│ library_entry         │>────────────┘
       │           │ (uid PK, content_uid) │     (many-to-many via
       │           └──────────┬───────────┘      library_entry_category)
       │                      │
       │           ┌──────────▼───────────┐
       ├──────────<│ episode_uid           │
       │           │ (uid PK, content_uid) │
       │           └──────────┬───────────┘
       │                      │
       │     ┌────────────────┼────────────────┐
       │     │                │                │
       │  ┌──▼──────────┐ ┌──▼──────────┐ ┌──▼───────────────┐
       └──│watch_progress│ │  history    │ │ episode_metadata │
          │(episode_uid) │ │(episode_uid)│ │ _cache(episode)  │
          └──────────────┘ └─────────────┘ └─────────────────┘

┌─────────────┐     ┌──────────────────┐
│tracker_link │     │ installed_source  │     ┌──────────────────┐
│(content_uid)│     │ (ecosystem+id PK) │     │ extension_repo   │
└──────┬──────┘     └──────────────────┘     │ (url PK)         │
       │                                      └──────────────────┘
  ┌────▼────────────┐
  │tracker_sync_state│
  │(tracker_type PK) │
  └─────────────────┘
```

---

## Group 1: Identity System (4 tables)

> The backbone. ContentUID is the app's stable UUID. ExternalReference links it to external systems.

### 1.1 `content_uid`
The app's own stable ID for each piece of content (anime, manga, novel).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `uid` | TEXT | PRIMARY KEY | UUID, app-generated, stable forever |
| `content_type` | TEXT | NOT NULL | `VIDEO` \| `IMAGE` \| `TEXT` |
| `title` | TEXT | NOT NULL | Canonical title (best-known) |
| `match_key` | TEXT | NOT NULL | Normalized title + year + type (for fuzzy matching) |
| `cover_url` | TEXT | | Cover image URL (cached) |
| `created_at` | INTEGER | NOT NULL | Epoch millis |

**Indexes:**
- `idx_content_match_key` ON `content_uid(match_key)` — for fuzzy matching.

```sql
CREATE TABLE content_uid (
    uid TEXT NOT NULL PRIMARY KEY,
    content_type TEXT NOT NULL CHECK (content_type IN ('VIDEO', 'IMAGE', 'TEXT')),
    title TEXT NOT NULL,
    match_key TEXT NOT NULL,
    cover_url TEXT,
    year INTEGER,
    created_at INTEGER NOT NULL
);

CREATE INDEX idx_content_match_key ON content_uid(match_key);
CREATE INDEX idx_content_year ON content_uid(year);
```

> **S18 fix**: Added `year INTEGER` for year-distribution charts on the My screen.

### 1.2 `external_reference`
Links a ContentUID to an external system (Aniyomi source, Mangayomi source, AniList, MAL, etc.).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT | |
| `uid` | TEXT | NOT NULL, FK → content_uid(uid) | The app's ContentUID |
| `ecosystem` | TEXT | NOT NULL | `aniyomi` \| `mangayomi` \| `cloudstream` \| `kotatsu` \| `anilist` \| `mal` \| `shikimori` |
| `source_id` | TEXT | | Null for trackers (AniList, MAL) |
| `external_id` | TEXT | NOT NULL | The external system's ID |
| `confidence` | TEXT | NOT NULL | `HIGH` \| `MEDIUM` \| `LOW` |
| `created_at` | INTEGER | NOT NULL | Epoch millis |

**Constraints:**
- **Partial unique indexes** (not inline UNIQUE) — SQLite treats NULL as distinct in UNIQUE constraints, which would allow duplicate tracker refs. We use two partial indexes instead.

**Indexes:**
- `idx_ext_ref_uid` ON `external_reference(uid)` — find all refs for a content.
- `idx_ext_ref_unique_with_source` — partial unique index for extension sources (source_id IS NOT NULL).
- `idx_ext_ref_unique_no_source` — partial unique index for trackers (source_id IS NULL).

```sql
CREATE TABLE external_reference (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    uid TEXT NOT NULL,
    ecosystem TEXT NOT NULL,
    source_id TEXT,
    external_id TEXT NOT NULL,
    confidence TEXT NOT NULL CHECK (confidence IN ('HIGH', 'MEDIUM', 'LOW')),
    is_user_confirmed INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (uid) REFERENCES content_uid(uid) ON DELETE CASCADE
);

CREATE INDEX idx_ext_ref_uid ON external_reference(uid);

-- S1 fix: partial unique indexes (SQLite treats NULL as distinct in UNIQUE)
CREATE UNIQUE INDEX idx_ext_ref_unique_with_source
    ON external_reference(ecosystem, source_id, external_id)
    WHERE source_id IS NOT NULL;
CREATE UNIQUE INDEX idx_ext_ref_unique_no_source
    ON external_reference(ecosystem, external_id)
    WHERE source_id IS NULL;
```

> **S1 fix**: Replaced inline UNIQUE with two partial unique indexes. Without this, two ContentUIDs could both claim AniList ID 16498 (NULL source_id treated as distinct).
>
> **S16 fix**: Added `is_user_confirmed` column. `external_reference` = all known external IDs (including auto-fuzzy-matched). `tracker_link` (below) = user-confirmed sync links. Both can coexist.

### 1.3 `episode_uid`
The app's stable ID for each episode.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `uid` | TEXT | PRIMARY KEY | UUID |
| `content_uid` | TEXT | NOT NULL, FK → content_uid(uid) | Parent content |
| `episode_number` | REAL | NOT NULL | Episode number (supports 5.5 for OVAs) |
| `match_key` | TEXT | NOT NULL | Normalized title + number (for fuzzy matching) |

**Constraints:**
- UNIQUE(`content_uid`, `episode_number`) — one episode per number per content.

```sql
CREATE TABLE episode_uid (
    uid TEXT NOT NULL PRIMARY KEY,
    content_uid TEXT NOT NULL,
    episode_number REAL NOT NULL,
    match_key TEXT NOT NULL,
    FOREIGN KEY (content_uid) REFERENCES content_uid(uid) ON DELETE CASCADE,
    UNIQUE(content_uid, episode_number)
);

CREATE INDEX idx_episode_content ON episode_uid(content_uid);
```

### 1.4 `episode_external_ref`
Links an EpisodeUID to an external system's episode.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT | |
| `episode_uid` | TEXT | NOT NULL, FK → episode_uid(uid) | |
| `ecosystem` | TEXT | NOT NULL | |
| `source_id` | TEXT | | |
| `external_id` | TEXT | NOT NULL | |
| `confidence` | TEXT | NOT NULL | `HIGH` \| `MEDIUM` \| `LOW` |

**Constraints:**
- **Partial unique indexes** (same fix as external_reference — S1).

```sql
CREATE TABLE episode_external_ref (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    episode_uid TEXT NOT NULL,
    ecosystem TEXT NOT NULL,
    source_id TEXT,
    external_id TEXT NOT NULL,
    confidence TEXT NOT NULL CHECK (confidence IN ('HIGH', 'MEDIUM', 'LOW')),
    FOREIGN KEY (episode_uid) REFERENCES episode_uid(uid) ON DELETE CASCADE
);

CREATE INDEX idx_episode_ext_ref_uid ON episode_external_ref(episode_uid);

-- S1 fix: partial unique indexes
CREATE UNIQUE INDEX idx_episode_ext_ref_unique_with_source
    ON episode_external_ref(ecosystem, source_id, external_id)
    WHERE source_id IS NOT NULL;
CREATE UNIQUE INDEX idx_episode_ext_ref_unique_no_source
    ON episode_external_ref(ecosystem, external_id)
    WHERE source_id IS NULL;
```

### 1.5 `identity_event`
Log of user-initiated identity operations (merge, split, auto-link). Enables undo.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT | |
| `event_type` | TEXT | NOT NULL | `MERGE` \| `SPLIT` \| `AUTO_LINK` \| `DISMISS_SUGGESTION` |
| `primary_uid` | TEXT | NOT NULL, FK → content_uid(uid) | The surviving ContentUID (for MERGE) or original (for SPLIT) |
| `secondary_uid` | TEXT | | The merged-away ContentUID (for MERGE) or null |
| `ref_id_affected` | INTEGER | | The external_reference.id that was moved/created/split |
| `reason` | TEXT | | Why this happened (e.g. "user_confirmed", "auto_fuzzy_match") |
| `performed_at` | INTEGER | NOT NULL | Epoch millis |
| `undone_at` | INTEGER | | Null if not undone. Set when user undoes. |

```sql
CREATE TABLE identity_event (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    event_type TEXT NOT NULL CHECK (event_type IN ('MERGE', 'SPLIT', 'AUTO_LINK', 'DISMISS_SUGGESTION')),
    primary_uid TEXT NOT NULL,
    secondary_uid TEXT,
    ref_id_affected INTEGER,
    reason TEXT,
    performed_at INTEGER NOT NULL,
    undone_at INTEGER,
    FOREIGN KEY (primary_uid) REFERENCES content_uid(uid) ON DELETE CASCADE
);

CREATE INDEX idx_identity_event_primary ON identity_event(primary_uid) WHERE undone_at IS NULL;
CREATE INDEX idx_identity_event_undone ON identity_event(undone_at);
```

> **S2 fix**: Added `identity_event` table. The architecture plan §6 says merges/splits are "logged (for undo)" — without this table, undo is impossible. `suggestMerges()` also uses this to filter dismissed suggestions.

---

## Group 2: Library (3 tables)

### 2.1 `category`
User-defined categories (Watching, Completed, Plan to Watch, etc.).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT | |
| `name` | TEXT | NOT NULL | Category name |
| `sort_order` | INTEGER | NOT NULL DEFAULT 0 | Display order |
| `created_at` | INTEGER | NOT NULL | |

**Constraints:**
- UNIQUE(`name`) — no duplicate category names.

```sql
CREATE TABLE category (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    content_type TEXT NOT NULL CHECK (content_type IN ('VIDEO', 'IMAGE', 'TEXT')),
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    UNIQUE(content_type, name COLLATE NOCASE)
);
```

> **S4 fix**: Added `content_type` — Aniyomi maintains separate anime/manga categories. Without this, importing both would conflate them.
>
> **S23 fix**: `COLLATE NOCASE` — category names are case-insensitive ("Watching" = "watching").

### 2.2 `library_entry`
An anime in the user's library.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `uid` | TEXT | PRIMARY KEY | = content_uid.uid (one entry per content) |
| `status` | TEXT | NOT NULL | `WATCHING` \| `COMPLETED` \| `PAUSED` \| `DROPPED` \| `PLAN_TO_WATCH` |
| `score` | INTEGER | | 0-100 (user rating) |
| `notes` | TEXT | | User notes |
| `last_episode_watched` | REAL | | Last watched episode number |
| `total_episodes` | INTEGER | | Total episodes (cached from metadata) |
| `added_at` | INTEGER | NOT NULL | When added to library |
| `updated_at` | INTEGER | NOT NULL | Last modified |

```sql
CREATE TABLE library_entry (
    uid TEXT NOT NULL PRIMARY KEY,
    status TEXT NOT NULL,
    score INTEGER,
    notes TEXT,
    last_episode_watched REAL,
    total_episodes INTEGER,
    added_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (uid) REFERENCES content_uid(uid) ON DELETE CASCADE
);

CREATE INDEX idx_library_status ON library_entry(status);
```

### 2.3 `library_entry_category`
Many-to-many between library entries and categories.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `library_uid` | TEXT | NOT NULL, FK → library_entry(uid) | |
| `category_id` | INTEGER | NOT NULL, FK → category(id) | |

**Constraints:**
- PRIMARY KEY(`library_uid`, `category_id`).

```sql
CREATE TABLE library_entry_category (
    library_uid TEXT NOT NULL,
    category_id INTEGER NOT NULL,
    PRIMARY KEY (library_uid, category_id),
    FOREIGN KEY (library_uid) REFERENCES library_entry(uid) ON DELETE CASCADE,
    FOREIGN KEY (category_id) REFERENCES category(id) ON DELETE CASCADE
);
```

---

## Group 3: Watch Progress + History (2 tables)

### 3.1 `watch_progress`
Per-episode watch progress. Keyed by episode_uid.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `episode_uid` | TEXT | PRIMARY KEY | |
| `position` | INTEGER | NOT NULL | Position in seconds |
| `duration` | INTEGER | NOT NULL | Total duration in seconds |
| `last_watched_at` | INTEGER | NOT NULL | Epoch millis |

```sql
CREATE TABLE watch_progress (
    episode_uid TEXT NOT NULL PRIMARY KEY,
    position INTEGER NOT NULL,
    duration INTEGER NOT NULL,
    completed INTEGER NOT NULL DEFAULT 0,
    completed_at INTEGER,
    last_watched_at INTEGER NOT NULL,
    FOREIGN KEY (episode_uid) REFERENCES episode_uid(uid) ON DELETE CASCADE
);

CREATE INDEX idx_watch_progress_last_watched ON watch_progress(last_watched_at DESC);
```

> **S7 fix**: Added index on `last_watched_at` for "Continue Watching" queries.
>
> **S11 fix**: Added `completed` + `completed_at` — explicit completion flag instead of fragile 90% threshold.

### 3.2 `history`
Watch history log (every time the user watches an episode).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT | |
| `episode_uid` | TEXT | NOT NULL, FK → episode_uid(uid) | |
| `content_uid` | TEXT | NOT NULL, FK → content_uid(uid) | Denormalized for fast queries |
| `watched_at` | INTEGER | NOT NULL | Epoch millis |
| `duration_watched` | INTEGER | NOT NULL | Seconds watched in this session |

**Indexes:**
- `idx_history_watched_at` ON `history(watched_at DESC)` — recent history.
- `idx_history_content` ON `history(content_uid)` — per-content history.

```sql
CREATE TABLE history (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    episode_uid TEXT NOT NULL,
    content_uid TEXT NOT NULL,
    watched_at INTEGER NOT NULL,
    duration_watched INTEGER NOT NULL,
    episode_duration INTEGER,
    FOREIGN KEY (episode_uid) REFERENCES episode_uid(uid) ON DELETE CASCADE,
    FOREIGN KEY (content_uid) REFERENCES content_uid(uid) ON DELETE CASCADE
);

CREATE INDEX idx_history_watched_at ON history(watched_at DESC);
CREATE INDEX idx_history_content ON history(content_uid);

-- S9 fix: unique constraint for merge dedup (architecture plan §7.5: UNION by (contentUid, episodeUid, timestamp))
CREATE UNIQUE INDEX idx_history_unique ON history(content_uid, episode_uid, watched_at);
```

> **S9 fix**: Unique index for merge dedup — prevents duplicate history rows when importing multiple backups.
>
> **S20 fix**: Added `episode_duration` — duration at watch time (for accurate historical stats, since `watch_progress.duration` may change after source switch).

---

## Group 4: Downloads (2 tables)

### 4.1 `download_queue`
The download queue (episodes to download).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT | |
| `episode_uid` | TEXT | NOT NULL, FK → episode_uid(uid) | |
| `state` | TEXT | NOT NULL | `QUEUED` \| `DOWNLOADING` \| `PAUSED` \| `COMPLETED` \| `FAILED` |
| `progress` | INTEGER | NOT NULL DEFAULT 0 | 0-100 |
| `error_message` | TEXT | | If failed |
| `queued_at` | INTEGER | NOT NULL | |
| `started_at` | INTEGER | | |
| `completed_at` | INTEGER | | |

```sql
CREATE TABLE download_queue (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    episode_uid TEXT NOT NULL,
    state TEXT NOT NULL,
    progress INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    queued_at INTEGER NOT NULL,
    started_at INTEGER,
    completed_at INTEGER,
    FOREIGN KEY (episode_uid) REFERENCES episode_uid(uid) ON DELETE CASCADE
);

CREATE INDEX idx_download_state ON download_queue(state);
```

### 4.2 `downloaded_episode`
Downloaded episode files on disk.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `episode_uid` | TEXT | PRIMARY KEY | |
| `file_path` | TEXT | NOT NULL | Path to downloaded file |
| `file_size` | INTEGER | NOT NULL | Bytes |
| `quality` | TEXT | | e.g. `1080p`, `720p` |
| `downloaded_at` | INTEGER | NOT NULL | |

```sql
CREATE TABLE downloaded_episode (
    episode_uid TEXT NOT NULL PRIMARY KEY,
    file_path TEXT NOT NULL,
    file_size INTEGER NOT NULL,
    quality TEXT,
    downloaded_at INTEGER NOT NULL,
    FOREIGN KEY (episode_uid) REFERENCES episode_uid(uid) ON DELETE CASCADE
);
```

---

## Group 5: Trackers (2 tables)

### 5.1 `tracker_link`
Links a ContentUID to a tracker (AniList, MAL, Shikimori).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `content_uid` | TEXT | NOT NULL, FK → content_uid(uid) | |
| `tracker_type` | TEXT | NOT NULL | `anilist` \| `mal` \| `shikimori` |
| `tracker_id` | INTEGER | NOT NULL | The tracker's ID for this anime |

**Constraints:**
- PRIMARY KEY(`content_uid`, `tracker_type`).

```sql
CREATE TABLE tracker_link (
    content_uid TEXT NOT NULL,
    tracker_type TEXT NOT NULL,
    tracker_id INTEGER NOT NULL,
    PRIMARY KEY (content_uid, tracker_type),
    FOREIGN KEY (content_uid) REFERENCES content_uid(uid) ON DELETE CASCADE
);
```

### 5.2 `tracker_sync_state`
Sync state per tracker (when did we last sync?).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `tracker_type` | TEXT | PRIMARY KEY | `anilist` \| `mal` \| `shikimori` |
| `username` | TEXT | | Logged-in user |
| `last_synced_at` | INTEGER | | Epoch millis |
| `access_token` | TEXT | | OAuth token (encrypted) |
| `token_expires_at` | INTEGER | | |

```sql
CREATE TABLE tracker_sync_state (
    tracker_type TEXT NOT NULL PRIMARY KEY,
    username TEXT,
    last_synced_at INTEGER,
    token_expires_at INTEGER
);
```

> **S6 fix**: OAuth tokens (`access_token` + `refresh_token`) are stored in **EncryptedSharedPreferences** (Android Keystore-backed), NOT in the database. The DB only stores non-sensitive metadata. This is consistent with the "What's NOT in the Schema" section (user credentials → Keystore/EncryptedSharedPreferences).

---

## Group 6: Extensions (2 tables)

### 6.1 `installed_source`
Installed extension sources.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `ecosystem` | TEXT | NOT NULL | `aniyomi` \| `mangayomi` \| etc. |
| `source_id` | TEXT | NOT NULL | Source ID within the ecosystem |
| `name` | TEXT | NOT NULL | Display name |
| `version` | TEXT | NOT NULL | Extension version |
| `is_enabled` | INTEGER | NOT NULL DEFAULT 1 | 0 or 1 |
| `installed_at` | INTEGER | NOT NULL | |

**Constraints:**
- PRIMARY KEY(`ecosystem`, `source_id`).

```sql
CREATE TABLE installed_source (
    ecosystem TEXT NOT NULL,
    source_id TEXT NOT NULL,
    name TEXT NOT NULL,
    version TEXT NOT NULL,
    package_name TEXT NOT NULL,
    signature_fingerprint TEXT,
    is_enabled INTEGER NOT NULL DEFAULT 1,
    installed_at INTEGER NOT NULL,
    last_updated_at INTEGER,
    PRIMARY KEY (ecosystem, source_id)
);

CREATE INDEX idx_installed_source_package ON installed_source(package_name);
```

> **S10 fix**: Added `package_name` + `signature_fingerprint` — needed for PackageInstaller integration (install/uninstall) and trust verification (SHA-256).

### 6.2 `extension_repo`
Extension repositories (URLs that serve extension APKs).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `url` | TEXT | PRIMARY KEY | Repo URL |
| `name` | TEXT | NOT NULL | Display name |
| `added_at` | INTEGER | NOT NULL | |

```sql
CREATE TABLE extension_repo (
    ecosystem TEXT NOT NULL,
    url TEXT NOT NULL,
    name TEXT NOT NULL,
    added_at INTEGER NOT NULL,
    PRIMARY KEY (ecosystem, url)
);
```

> **S5 fix**: Added `ecosystem` — Aniyomi repos and Mangayomi repos must be distinguished. PK is now `(ecosystem, url)`.

---

## Group 7: Metadata Cache (2 tables)

### 7.1 `content_metadata_cache`
Content-level metadata (description, genres, status, year, author, artist). Cached from AniList/extension sources.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `content_uid` | TEXT | PRIMARY KEY, FK → content_uid(uid) | |
| `description` | TEXT | | Synopsis / description |
| `genres` | TEXT | | JSON array of genre strings |
| `status` | TEXT | | `RELEASING` \| `FINISHED` \| `NOT_YET_RELEASED` \| `CANCELLED` |
| `year` | INTEGER | | Release year |
| `author` | TEXT | | Author/artist (for manga — future) |
| `artist` | TEXT | | Artist (for manga — future) |
| `source` | TEXT | NOT NULL | Which provider provided this (`anilist`, `extension`, etc.) |
| `updated_at` | INTEGER | NOT NULL | When cached |

```sql
CREATE TABLE content_metadata_cache (
    content_uid TEXT NOT NULL PRIMARY KEY,
    description TEXT,
    genres TEXT,
    status TEXT,
    year INTEGER,
    author TEXT,
    artist TEXT,
    source TEXT NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (content_uid) REFERENCES content_uid(uid) ON DELETE CASCADE
);
```

> **S3 fix**: Added `content_metadata_cache`. Without this, the Details screen can't render description/genres without re-fetching from AniList every time. Backup's `AnimeDetailsBackupProvider` also needs a target table.

### 7.2 `episode_metadata_cache`
Cached episode metadata (thumbnails, titles, air dates).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `episode_uid` | TEXT | PRIMARY KEY, FK → episode_uid(uid) | |
| `title` | TEXT | | Episode title |
| `thumbnail_url` | TEXT | | Episode thumbnail |
| `air_date` | INTEGER | | Air date (epoch millis) |
| `description` | TEXT | | Episode synopsis |
| `updated_at` | INTEGER | NOT NULL | When cached |

```sql
CREATE TABLE episode_metadata_cache (
    episode_uid TEXT NOT NULL PRIMARY KEY,
    title TEXT,
    thumbnail_url TEXT,
    air_date INTEGER,
    description TEXT,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (episode_uid) REFERENCES episode_uid(uid) ON DELETE CASCADE
);
```

---

## Group 8: App Metadata (1 table — already exists)

### 8.1 `app_metadata`
Key-value store for app-level flags (schema version, migration flags, etc.).

```sql
-- Already exists in Phase 2
CREATE TABLE app_metadata (
    key TEXT NOT NULL PRIMARY KEY,
    value TEXT NOT NULL
);
```

---

## Group 9: Activity Tracking (DEFERRED — Phase 6)

### 9.1 `activity_event`
User activity event log. Retention: 365 days default, unlimited option.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT | |
| `event_type` | TEXT | NOT NULL | `WATCH` \| `SEARCH` \| `BROWSE` \| `DOWNLOAD` \| `AD_SHOWN` \| etc. |
| `content_uid` | TEXT | | FK → content_uid(uid) (nullable for non-content events) |
| `episode_uid` | TEXT | | FK → episode_uid(uid) (nullable) |
| `session_id` | TEXT | NOT NULL | App session ID (for grouping) |
| `route` | TEXT | | Screen route when event occurred |
| `content_type` | TEXT | | VIDEO \| IMAGE \| TEXT |
| `duration_ms` | INTEGER | | Event duration (e.g. watch time) |
| `payload` | TEXT | | JSON blob for extra data |
| `timestamp` | INTEGER | NOT NULL | Epoch millis |

**Retention**: 365 days default. Prune worker deletes events older than retention.

```sql
CREATE TABLE activity_event (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    event_type TEXT NOT NULL,
    content_uid TEXT,
    episode_uid TEXT,
    session_id TEXT NOT NULL,
    route TEXT,
    content_type TEXT,
    duration_ms INTEGER,
    payload TEXT,
    timestamp INTEGER NOT NULL,
    FOREIGN KEY (content_uid) REFERENCES content_uid(uid) ON DELETE SET NULL,
    FOREIGN KEY (episode_uid) REFERENCES episode_uid(uid) ON DELETE SET NULL
);

CREATE INDEX idx_activity_timestamp ON activity_event(timestamp DESC);
CREATE INDEX idx_activity_type ON activity_event(event_type);
CREATE INDEX idx_activity_content ON activity_event(content_uid);
```

---

## Group 10: Ads (DEFERRED — Phase 6)

### 10.1 `ad_impression`
Log of ads shown to the user.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT | |
| `placement` | TEXT | NOT NULL | e.g. `anime_details_open`, `episode_start` |
| `format` | TEXT | NOT NULL | `interstitial` \| `redirect` \| `video` \| `banner` |
| `content_uid` | TEXT | | Associated content (nullable) |
| `shown_at` | INTEGER | NOT NULL | Epoch millis |
| `completed` | INTEGER | NOT NULL DEFAULT 0 | 1 if watched to completion, 0 if skipped |

```sql
CREATE TABLE ad_impression (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    placement TEXT NOT NULL,
    format TEXT NOT NULL,
    content_uid TEXT,
    shown_at INTEGER NOT NULL,
    completed INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (content_uid) REFERENCES content_uid(uid) ON DELETE SET NULL
);

CREATE INDEX idx_ad_shown_at ON ad_impression(shown_at DESC);
```

---

## Migration Strategy

- **Schema version**: stored in `app_metadata` table (`key="schema_version"`).
- **Migrations**: SQLDelight `.sqm` files (one per version bump). Each file is a set of SQL statements.
- **Data-transforming migrations**: supported (e.g., dedup before unique index) — SQLDelight handles these as raw SQL.
- **Phased DB migration**: the old project's pattern (one-shot preference flags, idempotent, try/catch) will be reused for complex data migrations.

### Initial schema (Phase 3)
- Tables 1-8 (identity + library + watch + app_metadata).
- Migration `1.sqm` → `2.sqm` sets `schema_version=2`.

### Phase 4 additions
- Tables 4-7 (downloads + trackers + extensions + metadata cache).

### Phase 6 additions (deferred)
- Tables 9-10 (activity + ads).

---

## Backup/Restore Integration

The backup system (D-041) serializes these tables to JSON:
- **Full backup**: all tables except `tracker_sync_state` (tokens) + `app_metadata` (app-specific).
- **Library-only backup**: `content_uid` + `external_reference` + `library_entry` + `category` + `library_entry_category`.
- **Import from Aniyomi/Mangayomi**: map external entries → `IdentityResolver.resolveOrCreate()` → write to `content_uid` + `external_reference` + `library_entry`.

See `15-backup-research.md` for the import flow + merge semantics (§7.5 of the architecture plan).

---

## Key Design Decisions

1. **ContentUID is a String UUID** (not auto-increment). Stable forever, survives source switches.
2. **ExternalReference is generic** — `ecosystem` is a string, not an enum. Adding a new ecosystem = no schema change.
3. **EpisodeUID separate from ContentUID** — episodes have their own stable IDs. Watch progress + downloads key off episode_uid.
4. **Denormalized `content_uid` in `history`** — for fast "recently watched" queries without joining through episode_uid.
5. **ON DELETE CASCADE** on all foreign keys — deleting a content_uid cleans up all related data.
6. **No boolean type** — SQLite doesn't have one. Use INTEGER (0/1).
7. **Timestamps are epoch millis** (INTEGER) — consistent, sortable, timezone-independent.
8. **Partial unique indexes NOT needed** — the `UNIQUE(ecosystem, source_id, external_id)` constraint allows duplicate nulls in SQLite by default, which is fine (null source_id = tracker, each tracker is unique per ecosystem).

---

## What's NOT in the Schema (Intentionally)

- **User credentials** — stored in Android Keystore / EncryptedSharedPreferences, not the DB.
- **Extension APK files** — on disk, not in DB. DB only tracks `installed_source` metadata.
- **Downloaded video files** — on disk. DB only tracks `downloaded_episode` metadata (path, size).
- **Cover images** — cached on disk by Coil. DB stores the URL (`content_uid.cover_url`).
- **Preferences** — in SharedPreferences (`PreferenceStore`), not the DB. Exception: `app_metadata` for app-level flags.
