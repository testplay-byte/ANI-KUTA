# Phase C — contentId System (Planning)

> **Status:** PLANNING (not yet implemented)
> **Date:** Post-Phase B, session web-f53f0459
> **Depends on:** Phase A (UnifiedAnime ✅) + Phase B (auto-link ✅)

## Problem

The app currently uses two different identity systems depending on where an
anime came from:

- **AniList entries** are identified by `anilistId: Int` (e.g. `154587`).
- **Extension entries** are identified by `sourceId: Long` + `animeUrl: String`.

When an extension entry is auto-linked to AniList (Phase B), it has BOTH.
But the two systems don't know they're the same anime. This causes:

1. **Watch progress fragmentation**: Watching episode 5 of "Frieren" from an
   extension source doesn't sync with watching episode 5 from AniList. The
   user has two separate progress entries for the same anime.
2. **Library duplication**: Adding "Frieren" to the library from an extension
   search + from AniList creates two library entries.
3. **No cross-source resume**: If the user watches 3 episodes from source A,
   then source A goes down, they can't resume from episode 4 on source B
   because the app doesn't know the two sources have the same anime.
4. **History fragmentation**: Watch history shows the same anime twice (once
   per source).

## Proposed Architecture

### 1. contentId — a stable cross-source identity

A `contentId: String` that uniquely identifies an anime REGARDLESS of which
source it came from. Format:

- **AniList-linked**: `"al:154587"` (AniList ID is the canonical identity).
- **Extension-only (not linked)**: `"aniyomi:$sourceId:$hash(animeUrl)"`
  (fallback — unique per source, but not cross-source).

When an extension entry is auto-linked or manually linked to AniList, its
`contentId` becomes `"al:<anilistId>"` — the SAME as the AniList entry's
`contentId`. Now they're the same anime.

### 2. Migration path (non-breaking)

- Add `contentId: String?` to `UnifiedAnime` (nullable during migration).
- Compute it lazily: `anilistId?.let { "al:$it" } ?: sourceId?.let { "aniyomi:$it:${animeUrl.hashCode()}" }`
- This is what `temporaryContentId` already does in Phase A — Phase C
  promotes it to a first-class field + uses it everywhere.

### 3. Watch progress migration

- `WatchProgressStore` currently keys on a string (probably the episode URL
  or a temporary ID).
- Phase C: migrate the key to `contentId + episodeNumber`.
- Old entries: auto-migrate using the existing `temporaryContentId` logic.
- After migration: watching episode 5 from any source updates the same
  progress entry.

### 4. Library migration

- Library entries currently key on `anilistId`.
- Phase C: key on `contentId`.
- Extension-only anime get a `contentId` of `"aniyomi:$sourceId:$hash"`.
- When an extension-only anime is later linked to AniList, its `contentId`
  changes from `aniyomi:...` to `al:...`. The library entry needs to be
  re-keyed (a one-time migration when the link happens).

### 5. History migration

- Watch history currently logs per-source.
- Phase C: log per-`contentId`. The history entry records which source was
  used, but the anime identity is `contentId`.

### 6. contentId resolution service

A `ContentIdResolver` that:
- Given a `contentId`, returns the `UnifiedAnime` (from cache or by
  re-fetching AniList + extension data).
- Given an `anilistId`, returns the `contentId`.
- Given a `sourceId + animeUrl`, returns the `contentId` (checks the
  auto-link cache first).

## Phases

### C.1 — contentId field + resolver
- Add `contentId` to `UnifiedAnime` (computed from anilistId/sourceId).
- Create `ContentIdResolver` in `:core:common`.
- Update `WatchKey` to carry `contentId` (alongside the existing fields,
  for backward compat).

### C.2 — Watch progress migration
- Change `WatchProgressStore` key from temp ID to `contentId + episodeNumber`.
- Auto-migrate existing entries on first launch.
- Test: watch episode 5 from source A → open from source B → resume from 5.

### C.3 — Library migration
- Change library key from `anilistId` to `contentId`.
- When an extension entry is linked to AniList, re-key its library entry.
- Test: add to library from extension → link to AniList → library entry
  doesn't duplicate.

### C.4 — History migration
- Change history log key to `contentId`.
- Show "watched from source X" in history (source is metadata, not identity).
- Test: watch from source A → history shows one entry.

### C.5 — Cross-source resume
- When the user opens an anime from source B that has the same `contentId`
  as a source A entry they were watching, offer "Resume from episode N".
- Test: watch 3 eps from source A → open from source B → "Resume from ep 4".

## Risks + Mitigations

1. **Migration data loss**: If the migration script has a bug, existing
   watch progress could be lost.
   - Mitigation: Backup the old store before migration. Test migration on
   a copy first. Log every migration step.

2. **Hash collisions**: `animeUrl.hashCode()` could collide for two
   different anime on the same source.
   - Mitigation: Use the full `animeUrl` string in the fallback contentId
   (not the hash). The hash was a simplification — Phase C uses the full URL.

3. **Re-keying library entries**: When an extension entry is linked to
   AniList, the library entry's key changes. If the app crashes mid-re-key,
   the entry could be lost.
   - Mitigation: Write the new entry BEFORE deleting the old one. Use a
   transaction if the store supports it.

4. **Backward compatibility**: Old `WatchKey`s in the nav backstack won't
   have `contentId`.
   - Mitigation: `contentId` is nullable during migration. Code that reads
   it falls back to the old identity logic.

## Estimated effort

- C.1 (contentId field + resolver): Small — 1-2 files.
- C.2 (watch progress): Medium — migrate store + test.
- C.3 (library): Medium — migrate store + re-key on link.
- C.4 (history): Small — change the key.
- C.5 (cross-source resume): Medium — UI + logic.

Total: ~1 full session if done carefully.

## Open questions for user

1. Should the library deduplicate existing entries on migration (merge
   duplicates) or keep them separate?
2. Should cross-source resume be automatic (open from source B → auto-resume
   from ep 4) or manual (show a "Resume from ep 4?" prompt)?
3. Should the contentId be shown in the UI (e.g. in the details page info
   section) or hidden?
4. Should we add a "Linked sources" list in the details page (show all
   sources that have the same `contentId`)?
