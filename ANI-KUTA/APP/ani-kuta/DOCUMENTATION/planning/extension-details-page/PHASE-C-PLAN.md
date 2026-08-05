# Phase C — contentId System (Plan v2)

> **Status:** DRAFT — awaiting user review + answers to open questions
> **Date:** Post-Phase B, session web-f53f0459
> **Depends on:** Phase A (UnifiedAnime ✅) + Phase B (auto-link ✅ + data-source selector ✅)

---

## 1. Problem Statement

The app currently uses two different identity systems depending on where an
anime came from:

- **AniList entries** are identified by `anilistId: Int`.
- **Extension entries** are identified by `sourceId: Long` + `animeUrl: String`.

When an extension entry is auto-linked to AniList (Phase B), it has BOTH
identities. But the two systems don't know they're the same anime. This causes:

1. **Watch progress fragmentation** — watching ep 5 from source A ≠ ep 5 from source B.
2. **Library duplication** — same anime added from different sources = 2 entries.
3. **No cross-source resume** — if source A goes down, can't resume from source B.
4. **History fragmentation** — same anime appears once per source.

**User's decision:** Keep both identity systems (anilistId, sourceId/animeUrl).
They're essential — they tell us which content from which source the user
interacted with. Add a NEW internal identity layer on top.

---

## 2. User's Proposal + My Analysis

### User's proposal
- A `contentId` generated from "a bunch of things" (somewhat random).
- The `contentId` **changes** when the user switches sources.
- Database stores: `contentId`, `anilistId`, `sourceId`, `animeUrl`, `contentType`, etc.
- When sources change, the `contentId` is updated + all related data is re-keyed.

### My concern (honest feedback per CORE_RULES §2)

A **changing** contentId defeats the purpose of a cross-source identity. Here's why:

| Scenario | Changing contentId | Stable contentId |
|----------|-------------------|-----------------|
| User watches ep 5 from source A | Progress keyed by `contentId_A` | Progress keyed by `contentId` |
| User switches to source B | `contentId` changes to `contentId_B` → **progress orphaned** | `contentId` stays → **progress preserved** |
| User adds to library from source A | Library keyed by `contentId_A` | Library keyed by `contentId` |
| User opens from source B | `contentId` changes → **library entry orphaned** | Library entry found → **no duplicate** |

If the contentId changes, every data store keyed by it (watch progress, library,
history) would need to be **migrated** to the new ID. This is:
- **Fragile** — a crash mid-migration = data loss.
- **Complex** — every source switch triggers a cascade of re-keying.
- **Unnecessary** — a stable ID avoids all of this.

### What I think the user actually wants

I believe the user is describing TWO separate concepts:

1. **Identity** (should be STABLE) — "this is the same anime regardless of source."
   This is the contentId. It should NEVER change once assigned.

2. **Display source** (DOES change) — "show me data from AniList vs Extension."
   This is already handled by `dataSourcePriority` (D-130). It's a FIELD on the
   content record, not the contentId itself.

The user said "If the user changes the source from one source to another source
then the whole content ID itself will change and all the relevant things about
it will change too." I think "all the relevant things" = the FIELDS on the
content record (anilistId, sourceId, displaySource), NOT the contentId itself.

### My recommended design

**Stable contentId (UUID) + source link table.**

- `contentId` = a UUID assigned once when a content is first seen. NEVER changes.
- The content record has FIELDS that change (anilistId, sourceId, displaySource).
- Multiple sources can be linked to one content (one-to-many relationship).
- Switching the display source only updates a FIELD — the contentId is untouched.
- All data stores (watch progress, library, history) key on the stable contentId.

This gives the user what they want (a unified identity that's independent of
anilistId) without the data-loss risk of a changing ID.

**Open question Q-001:** Does the user agree that the contentId should be stable
(assigned once, never changes), with only the FIELDS (anilistId, sourceId,
displaySource) changing? Or does the user specifically want the contentId itself
to change — and if so, how should data migration be handled?

---

## 3. Recommended Architecture

### 3.1 Two-table design

```
┌─────────────────────────────────────────────────────────────┐
│  content (1 row per anime)                                  │
│  ────────────────────────────────────                       │
│  contentId        TEXT PRIMARY KEY  ← stable UUID           │
│  contentType      TEXT             ← 'anime' (future: manga)│
│  displaySource    TEXT             ← 'anilist'/'extension'  │
│  createdAt        INTEGER          ← epoch millis            │
│  updatedAt        INTEGER          ← epoch millis            │
└─────────────────────────────────────────────────────────────┘
                            │
                            │ 1
                            │
                            │ N
┌─────────────────────────────────────────────────────────────┐
│  content_source_link (1 row per linked source)              │
│  ─────────────────────────────────────────                   │
│  id               INTEGER PRIMARY KEY AUTOINCREMENT          │
│  contentId        TEXT NOT NULL  ← FK → content.contentId    │
│  sourceType       TEXT NOT NULL  ← 'anilist'/'extension'/... │
│  sourceAnimeId    TEXT           ← anilistId or animeUrl     │
│  sourceId         INTEGER        ← extension source ID       │
│  isPrimary        INTEGER DEFAULT 0  ← 1 = display source    │
│  linkedAt         INTEGER        ← epoch millis              │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 How it works

**When a new anime is first seen** (e.g. user opens an extension search result):
1. Generate a UUID: `contentId = UUID.randomUUID().toString()`.
2. Insert into `content`: `(contentId, 'anime', 'extension', now, now)`.
3. Insert into `content_source_link`: `(contentId, 'extension', animeUrl, sourceId, 1, now)`.

**When auto-link matches an extension entry to AniList:**
1. The `contentId` stays the same (NO change).
2. Insert a new row into `content_source_link`: `(contentId, 'anilist', anilistId.toString(), null, 0, now)`.
3. Optionally update `content.displaySource` to `'anilist'` (if the user wants AniList data).

**When the user switches the display source (data-source selector):**
1. Update `content.displaySource` to the new source type.
2. Update `content_source_link.isPrimary` (set old primary to 0, new primary to 1).
3. The `contentId` stays the same. All watch progress / library / history is preserved.

**When the user manually links an AniList entry to a source:**
1. The `contentId` stays the same (it was assigned when the AniList entry was first opened).
2. Insert a new row into `content_source_link` for the extension.
3. The data-source selector becomes available (both sources linked).

**When the user unlinks a source:**
1. Delete the `content_source_link` row for that source.
2. If it was the primary, switch primary to the remaining source.
3. The `contentId` stays the same.

### 3.3 Why this design is better than a changing contentId

| Operation | Changing contentId | Stable contentId (recommended) |
|-----------|-------------------|-------------------------------|
| Switch display source | Re-key all data → risky | Update 1 field → safe |
| Link new source | Re-key all data → risky | Insert 1 row → safe |
| Unlink source | Re-key all data → risky | Delete 1 row → safe |
| App crash mid-operation | Data loss | No data loss (contentId unchanged) |
| Watch progress | Needs migration on every switch | Never needs migration |
| Library | Needs migration on every switch | Never needs migration |

---

## 4. Database Tables (SQLDelight)

### 4.1 `content.sq`

```sql
CREATE TABLE content (
    contentId     TEXT NOT NULL PRIMARY KEY,
    contentType   TEXT NOT NULL DEFAULT 'anime',
    displaySource TEXT NOT NULL DEFAULT 'extension',
    createdAt     INTEGER NOT NULL,
    updatedAt     INTEGER NOT NULL
);

-- Get a content record by contentId
getContentById:
SELECT * FROM content WHERE contentId = :contentId;

-- Get a content record by anilistId (via source link)
getContentByAniListId:
SELECT c.* FROM content c
JOIN content_source_link l ON c.contentId = l.contentId
WHERE l.sourceType = 'anilist' AND l.sourceAnimeId = :anilistId
LIMIT 1;

-- Get a content record by extension sourceId + animeUrl (via source link)
getContentByExtension:
SELECT c.* FROM content c
JOIN content_source_link l ON c.contentId = l.contentId
WHERE l.sourceType = 'extension' AND l.sourceId = :sourceId AND l.sourceAnimeId = :animeUrl
LIMIT 1;

-- Insert a new content record
insertContent:
INSERT OR IGNORE INTO content (contentId, contentType, displaySource, createdAt, updatedAt)
VALUES (:contentId, :contentType, :displaySource, :createdAt, :updatedAt);

-- Update the display source
updateDisplaySource:
UPDATE content SET displaySource = :displaySource, updatedAt = :updatedAt
WHERE contentId = :contentId;
```

### 4.2 `content_source_link.sq`

```sql
CREATE TABLE content_source_link (
    id            INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    contentId     TEXT NOT NULL,
    sourceType    TEXT NOT NULL,
    sourceAnimeId TEXT,
    sourceId      INTEGER,
    isPrimary     INTEGER NOT NULL DEFAULT 0,
    linkedAt      INTEGER NOT NULL,
    FOREIGN KEY (contentId) REFERENCES content(contentId) ON DELETE CASCADE
);

CREATE INDEX idx_link_contentId ON content_source_link(contentId);
CREATE INDEX idx_link_source ON content_source_link(sourceType, sourceAnimeId);

-- Get all source links for a content
getLinksByContentId:
SELECT * FROM content_source_link WHERE contentId = :contentId;

-- Get the primary source link for a content
getPrimaryLink:
SELECT * FROM content_source_link WHERE contentId = :contentId AND isPrimary = 1
LIMIT 1;

-- Get a specific source link by anilistId
getLinkByAniListId:
SELECT * FROM content_source_link
WHERE sourceType = 'anilist' AND sourceAnimeId = :anilistId
LIMIT 1;

-- Get a specific source link by extension sourceId + animeUrl
getLinkByExtension:
SELECT * FROM content_source_link
WHERE sourceType = 'extension' AND sourceId = :sourceId AND sourceAnimeId = :animeUrl
LIMIT 1;

-- Insert a new source link
insertLink:
INSERT OR IGNORE INTO content_source_link
(contentId, sourceType, sourceAnimeId, sourceId, isPrimary, linkedAt)
VALUES (:contentId, :sourceType, :sourceAnimeId, :sourceId, :isPrimary, :linkedAt);

-- Set primary source (unset old primary, set new primary)
setPrimaryLink:
UPDATE content_source_link SET isPrimary = 0 WHERE contentId = :contentId;
UPDATE content_source_link SET isPrimary = 1 WHERE id = :linkId;

-- Delete a source link
deleteLink:
DELETE FROM content_source_link WHERE id = :linkId;
```

### 4.3 `watch_progress.sq` (NEW — not yet implemented, uses contentId)

```sql
CREATE TABLE watch_progress (
    contentId     TEXT NOT NULL,
    episodeNumber REAL NOT NULL,
    position      REAL NOT NULL,
    duration      REAL NOT NULL,
    completed     INTEGER NOT NULL DEFAULT 0,
    updatedAt     INTEGER NOT NULL,
    PRIMARY KEY (contentId, episodeNumber),
    FOREIGN KEY (contentId) REFERENCES content(contentId) ON DELETE CASCADE
);

getProgress:
SELECT * FROM watch_progress WHERE contentId = :contentId AND episodeNumber = :episodeNumber;

getAllProgress:
SELECT * FROM watch_progress WHERE contentId = :contentId ORDER BY episodeNumber;

upsertProgress:
INSERT INTO watch_progress (contentId, episodeNumber, position, duration, completed, updatedAt)
VALUES (:contentId, :episodeNumber, :position, :duration, :completed, :updatedAt)
ON CONFLICT(contentId, episodeNumber) DO UPDATE SET
    position = :position,
    duration = :duration,
    completed = :completed,
    updatedAt = :updatedAt;
```

### 4.4 `library.sq` (NEW — not yet implemented, uses contentId)

```sql
CREATE TABLE library (
    contentId     TEXT NOT NULL PRIMARY KEY,
    addedAt       INTEGER NOT NULL,
    FOREIGN KEY (contentId) REFERENCES content(contentId) ON DELETE CASCADE
);

isInLibrary:
SELECT EXISTS(SELECT 1 FROM library WHERE contentId = :contentId);

addToLibrary:
INSERT OR IGNORE INTO library (contentId, addedAt) VALUES (:contentId, :addedAt);

removeFromLibrary:
DELETE FROM library WHERE contentId = :contentId;
```

### 4.5 `watch_history.sq` (NEW — not yet implemented, uses contentId)

```sql
CREATE TABLE watch_history (
    id            INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    contentId     TEXT NOT NULL,
    episodeNumber REAL NOT NULL,
    watchedAt     INTEGER NOT NULL,
    FOREIGN KEY (contentId) REFERENCES content(contentId) ON DELETE CASCADE
);

addHistory:
INSERT INTO watch_history (contentId, episodeNumber, watchedAt)
VALUES (:contentId, :episodeNumber, :watchedAt);

getHistory:
SELECT * FROM watch_history WHERE contentId = :contentId ORDER BY watchedAt DESC;
```

---

## 5. Architecture (Kotlin modules)

### 5.1 New module: `:core:content`

```
core/content/
├── build.gradle.kts
└── src/main/java/com/confused/anikuta/core/content/
    ├── ContentId.kt              ← value class / type alias for String
    ├── ContentRecord.kt          ← data class: contentId, contentType, displaySource, ...
    ├── ContentSourceLink.kt      ← data class: id, contentId, sourceType, sourceAnimeId, ...
    ├── ContentRepository.kt      ← interface: get/create/link/unlink/switchSource
    ├── ContentResolver.kt        ← resolves anilistId/sourceId/animeUrl → contentId
    └── ContentModule.kt          ← Koin DI
```

### 5.2 `ContentResolver` — the key class

```kotlin
class ContentResolver(private val repo: ContentRepository) {

    /**
     * Resolve an AniList entry to a contentId.
     * - If a content record exists for this anilistId → return it.
     * - If not → create a new content record + source link → return new contentId.
     */
    suspend fun resolveOrCreateForAniList(anilistId: Int): String

    /**
     * Resolve an extension entry to a contentId.
     * - If a content record exists for this (sourceId, animeUrl) → return it.
     * - If not → create a new content record + source link → return new contentId.
     */
    suspend fun resolveOrCreateForExtension(sourceId: Long, animeUrl: String): String

    /**
     * Link an AniList ID to an existing content (when auto-link matches).
     * - Adds a source_link row.
     * - contentId stays the same.
     */
    suspend fun linkAniList(contentId: String, anilistId: Int)

    /**
     * Link an extension source to an existing content (when user links a source).
     */
    suspend fun linkExtension(contentId: String, sourceId: Long, animeUrl: String)

    /**
     * Switch the primary display source.
     * - Updates content.displaySource.
     * - Updates content_source_link.isPrimary.
     * - contentId stays the same.
     */
    suspend fun switchDisplaySource(contentId: String, sourceType: String)
}
```

### 5.3 Integration with existing code

**`UnifiedAnime`** — add `contentId: String?` field:
```kotlin
data class UnifiedAnime(
    // ... existing fields ...
    val contentId: String? = null,  // Phase C: stable cross-source identity
)
```

**`DetailsViewModel`** — use `ContentResolver` on load:
```kotlin
fun loadFromAniList(animeId: Int) {
    viewModelScope.launch {
        val anime = anilistApi.fetchAnimeDetails(animeId)
        val contentId = contentResolver.resolveOrCreateForAniList(animeId)
        anilistBase = anime.toUnifiedAnime().copy(contentId = contentId)
        remergeBases(DataSourcePriority.ANILIST)
        // ...
    }
}
```

**`WatchKey`** — carry `contentId` (for watch progress):
```kotlin
@Serializable
data class WatchKey(
    // ... existing fields ...
    val contentId: String? = null,  // Phase C: for watch progress
)
```

---

## 6. Implementation Phases

### C.1 — Database schema + repository + resolver
- Add `content.sq` + `content_source_link.sq` to `:core:database`.
- Create `:core:content` module with `ContentRepository` + `ContentResolver`.
- Register in Koin.
- No UI changes yet.

### C.2 — Integrate with DetailsViewModel
- Add `contentId` field to `UnifiedAnime`.
- `DetailsViewModel.loadFromAniList()` + `loadFromExtension()` call `ContentResolver`.
- `linkSource()` + `unlinkSource()` + `linkAniListEntry()` + `unlinkAniList()` update source links.
- `switchDataSource()` updates `displaySource` in the database.

### C.3 — Watch progress (uses contentId from the start — no migration)
- Add `watch_progress.sq`.
- `WatchProgressStore` keys on `contentId + episodeNumber`.
- `WatchKey` carries `contentId`.

### C.4 — Library (uses contentId from the start — no migration)
- Add `library.sq`.
- Library screen shows entries by `contentId`.
- Adding from any source links to the same `contentId`.

### C.5 — History (uses contentId from the start — no migration)
- Add `watch_history.sq`.
- History logs by `contentId`.

---

## 7. Open Questions for User

### Q-001: Stable vs changing contentId
**My recommendation:** Stable UUID (assigned once, never changes).
**User's original idea:** contentId changes when sources change.
**Question:** Does the user agree with the stable approach after reading the
analysis in §2? Or does the user specifically want a changing ID — and if so,
how should data migration be handled to prevent data loss?

### Q-002: contentId generation
**Options:**
- (a) `UUID.randomUUID()` — truly random, assigned on first sighting.
- (b) Hash of `anilistId` (for linked entries) + `sourceId:animeUrl` (for unlinked) — deterministic.
- (c) Hash of `title + year` — deterministic but collision-prone.
**My recommendation:** (a) UUID — simplest, no collision risk.
**Question:** Which does the user prefer?

### Q-003: contentType field
**Question:** What content types should we support? Just `'anime'` for now, with
`'manga'` + `'novel'` as future placeholders? Or something else?

### Q-004: Multiple extension sources per content
**Scenario:** The user links "Frieren" from Source A, then later finds "Frieren"
on Source B and links it too.
**Question:** Should the content record support MULTIPLE extension source links
(one per source)? My design says yes (the `content_source_link` table supports
this). Does the user agree?

### Q-005: Default display source when multiple are linked
**Scenario:** An extension entry is auto-linked to AniList. Which is the default
display source — AniList or Extension?
**Current behavior:** Extension priority (non-intrusive — D-130).
**Question:** Should the default change to AniList (since AniList data is richer)?
Or stay as Extension (current behavior)?

### Q-006: Should the contentId be shown in the UI?
**User's answer (from feedback):** No — "I don't think that there is any need
for it to be shown in the UI at all."
**Confirmed:** contentId is internal only. Not shown in any screen.

---

## 8. Next Steps

1. **User reviews this plan** + answers Q-001 through Q-005.
2. **I finalize the plan** based on answers.
3. **I use the full-stack-dev agent** to convert the plan into a visual web page
   (per user's request: "I would like you to use a full stack dev agent to convert
   our plan into a web page").
4. **Implementation begins** (Phase C.1 → C.5).

---

## 9. What NOT to expect (user's clarifications)

- **No migration needed** — watch progress, library, and history aren't set up
  yet. We're building the contentId system from scratch, alongside these features.
- **contentId is NOT anilistId** — it's independent. Some content won't have an
  anilistId at all.
- **contentId is NOT shown in the UI** — it's an internal tracking ID only.
- **Both identity systems stay** — anilistId + sourceId/animeUrl are kept.
  contentId sits ON TOP, unifying them.
