# Phase 5 — Identity, Watch & Data Wiring Plan

> Plan for Phase 5 of the ANI-KUTA rebuild. Written session web-3a43f99b per user
> request ("plan out phase 5 too so we have a smoother way moving forward").
>
> **Why this plan exists:** Phase 3 built the core modules (player, resolver,
> extensions, metadata, trackers) and Phase 4 built the feature-screen UIs
> (Library, Search, Browse, Settings). But nothing is WIRED end-to-end yet —
> you can't tap an anime and watch it, because there's no identity link between
> an AniList entry and a source's episodes, and the Watch screen isn't built.
> Phase 5 closes that gap so the app becomes testable on a real device.

---

## 1. Goal

**Make the app watchable end-to-end:** browse AniList → open details → link to a
source → pick an episode → play with MPV → resume from last position → record
history + sync to AniList. Plus the supporting systems (identity, history,
updates, backup/restore) that make the data persistent and portable.

**Exit criteria (what "Phase 5 done" means):**
- [ ] User can install an extension (APK) and add an extension repo.
- [ ] User can link an AniList anime to a source's anime (manual search).
- [ ] User can open the Watch screen, pick an episode, and play it via MPV.
- [ ] Playback resumes from the last saved position (D-049 video caching).
- [ ] Watch history records + displays; AniList tracker syncs (one-way, D-045).
- [ ] Backup/restore can import an Aniyomi `.tachibk` file (D-041).
- [ ] New-episode detection runs (D-029 prerequisite for future notifications).

---

## 2. Sub-Phases (dependency-ordered)

```
5a Identity ──────► 5b Watch ──────► 5c History/Updates
   (foundation)        (testing)        (persistence)
                          │
                          └─► 5d Backup/Restore + color picker
                          └─► 5e Extension repo UI (can parallelize with 5b+)
```

| Sub-phase | Name | Depends on | Key risk |
|-----------|------|------------|----------|
| 5a | Identity system | Phase 3 DB + metadata | Matching-engine correctness (false merges) |
| 5b | Watch screen + extension wiring | 5a, Phase 3 player/resolver | MPV lifecycle in Compose (D-050 fix) |
| 5c | History + Updates + new-episode detection | 5b | New-episode detection has no old-project reference |
| 5d | Backup/restore + custom color picker | 5a (identity), 5b (data tables) | Multi-format import compat |
| 5e | Extension repo management UI | Phase 3b data:extension | Repo index API format varies |

---

## 3. Sub-Phase 5a — Identity System (foundation)

**Why first:** Every other Phase 5 feature needs stable content identity. The
watch screen links an episode to a content entry; history records a content
entry; backup exports content entries. Without identity, we'd hardcode
`content_key` strings (the current temporary ponytail) and have to migrate later.

**Decisions:** D-032 (flexible + switchable + backup compat), D-042 (deferred
to here).

### 3.1 Data model (SQLDelight)
- `content_uid` table: `id` (app UUID), `ecosystem` (VIDEO/IMAGE/TEXT),
  `title`, `created_at`. The app's canonical content identity.
- `external_reference` table: `id`, `content_uid_id` (FK), `provider`
  (ANILIST / source-package-name), `external_id` (the provider's ID),
  `confidence` (HIGH/MEDIUM/LOW), `is_primary`, `created_at`.
  - Unique partial index on `(provider, external_id)` WHERE `is_primary = 1`
    (only one primary ref per provider — SQLDelight partial indexes, why we
    stayed on SQLDelight per D-035).
- **Migration:** populate `content_uid` from existing `metadata` table rows
  (each cached AniList anime becomes a ContentUID with one HIGH-confidence
  ANILIST external reference). Replace the temporary `content_key` string
  column used in Phase 4 Library/History with `content_uid_id` FK.

### 3.2 Modules
- New module `:core:identity` — `ContentUid`, `ExternalReference` models,
  `IdentityRepository` (create/merge/split/find), `MatchingEngine`
  (auto-link AniList ↔ source by title similarity).
- `:core:database` — add the 2 tables + `.sq` queries.
- Update `:core:metadata` — `MetadataMerger` keys off `ContentUid` not
  `content_key`.

### 3.3 Matching engine (the hard part)
- When the user opens an AniList anime's details and a source is installed, the
  engine searches the source for a matching `SAnime` by normalized title +
  year. Confidence HIGH if exact title + year match, MEDIUM if fuzzy, LOW if
  only one candidate.
- User can override (manual search → link). Manual links are HIGH confidence.
- **Merge/split:** user can merge two ContentUids (same anime, different
  sources) or split (wrong match). UI in Details screen.

### 3.4 Risk mitigation
- Start with MANUAL linking only (user picks the source match). Auto-matching
  is additive — ship 5a with manual, add the engine in 5b. This de-risks 5a
  (no false merges) and lets the watch screen work sooner.

---

## 4. Sub-Phase 5b — Watch Screen + Extension Wiring (testing priority)

**Why this is the user's priority:** "Without moving on to the next phases we
cannot test out the watch page." This is where the app becomes usable.

**Depends on:** 5a (identity for episode→content link), Phase 3 player +
video-resolver + watch-progress.

### 4.1 Extension wiring (the missing link)
Currently Browse/Details use AniList directly. To watch, we need:
1. **Extension install UI** — list installed extensions (from
   `ExtensionManager`), install from APK, enable/disable. Screen in Settings →
   Extensions (old project: `ExtensionsSettingsScreen`).
2. **Source listing** — once an extension is enabled, browse its catalog
   (popular/latest) via `AnimeCatalogueSource.fetchPopularAnime`.
3. **Manual search / linking** — from an AniList anime's Details screen, search
   installed sources for a match → create an `ExternalReference` (5a) linking
   the source's `SAnime` to the AniList `ContentUid`. Old project:
   `ManualSearchSheet`, `SourceSwitcherMenu`.
4. **Episode fetch** — once linked, `source.fetchEpisodeList(sAnime)` →
   episodes. Cache in `metadata` table.

### 4.2 Watch screen (port from old project, split per D-038)
Old project's `WatchScreen.kt` is 2386 LOC — **split** into:
- `WatchScreen.kt` — scaffold + state hoisting.
- `WatchViewModel.kt` — episode list, resume position, player commands.
- `sheets/` — `SpeedSheet`, `PlayerSheets` (settings, audio, subtitle tracks),
  `ResolverSheet` (video quality/server picker — reuses Phase 3 VideoResolver).
- `WatchEpisodeDisplayPrefs.kt` — episode list display settings (port from old
  `feature/episode-settings`).

### 4.3 Player lifecycle (D-050 fix)
- The old project used a companion `lateinit var playerPreferences` hack. We
  fixed this (D-050) using `KoinComponent` in Phase 3c's `AnikutaMPVView`.
- Verify: MPV view is created via `AndroidView` in Compose, survives
  configuration changes, releases on dispose. Single instance (old project
  pattern — don't create multiple MPV instances).

### 4.4 Video caching for instant resume (D-049)
- MPV config: `cache-secs=120`, `demuxer-max-bytes=150MiB`,
  `stream-cache-dir` = app cache dir (persistent on disk).
- On resume: seek to saved position. The cached segment around the position
  plays instantly while the stream loads in the background.
- Watch-progress store (Phase 3d) provides the saved position.

### 4.5 Decisions to confirm with user
- Q: Episode list default sort (descending = newest first, like most anime
  apps)? Old project used descending.
- Q: Default video quality preference (auto / highest / ask)?

---

## 5. Sub-Phase 5c — History, Updates, New-Episode Detection

**Depends on:** 5b (watch events feed history + updates).

### 5.1 History
- `:feature:history` — list of watched episodes (time, anime, episode, progress
  %). Port `HistoryScreen` + `HistoryViewModel` from old project.
- Data source: `activity_tracker` events (Phase 3d) + `watch_progress` table.
  History is a VIEW over watch events, not a separate table.

### 5.2 Updates (new-episode detection — D-029, no old-project reference)
- **This is new work** (old project didn't have it). Build a
  `NewEpisodeDetector` that periodically (WorkManager) checks each library
  entry's linked source for new episodes since last check.
- `:core:updates` — `UpdateChecker` (per source), `UpdateStore` (last-checked
  timestamp per content), `UpdatesRepository`.
- `:feature:updates` — Updates screen (list of new episodes, "mark read",
  "open"). Old project `UpdatesScreen` as UI reference but new logic.
- Schedule: WorkManager periodic (default every 6 hours, user-configurable).
- **Notifications** (D-029) come AFTER this — can't notify on episodes we
  haven't detected. Defer notifications to Phase 6.

### 5.3 Library updates integration
- When new episodes are detected, badge the Library card with a count.
- "Updates" tab or section — decide: separate tab (4→5 tabs?) or a section in
  Library. Old project had it in the bottom nav. Confirm with user.

---

## 6. Sub-Phase 5d — Backup/Restore + Custom Color Picker

**Depends on:** 5a (identity), 5b (all watch/history data tables exist).

### 6.1 Backup/restore (D-041, D-047)
- `:feature:backup` — port from old project but REBUILD properly (D-047).
- **Import formats:**
  - Aniyomi/Animiru/Anikku `.tachibk` (protobuf) — reuse old
    `AniyomiBackupTranslator`. Covers most users.
  - Mangayomi `.backup` (JSON-in-zip) — new translator.
- **Export:** ANI-KUTA's own `.anikuta` format v2 (JSON-in-zip, includes
  identity graph + all data tables).
- **Merge semantics (§7.5 of architecture plan):** import matches existing
  ContentUids by external references; duplicates skipped; conflicts prompt user.
- UI: `BackupSettingsScreen` (frequency, max backups, categories), restore flow
  with category selection + summary dialog.

### 6.2 Custom color picker (palette Phase 5 item — D-053)
- The accent palette system (D-053) ships in Phase 4 with 10 presets + CUSTOM
  (defaults to Lime). Phase 5d adds the **color picker UI** for CUSTOM:
  - `CustomColorSheet` — HSV picker + hex input + live preview.
  - Wires to `ThemePreferences.setCustomAccent(color)` (already implemented).
- Port old project's `CustomColorSheet.kt` (reference, rebuild clean).

---

## 7. Sub-Phase 5e — Extension Repo Management UI

**Depends on:** Phase 3b `:data:extension`. Can parallelize with 5b–5d.

### 7.1 Extension repos (D-043 — NO default repos)
- `:feature:extensions-settings` — list installed extensions (from
  `ExtensionManager`), install/update/uninstall, enable/disable per source.
- Repo management: add/remove extension repo URLs (user-supplied, no defaults
  per D-043). Fetch repo index (`ExtensionRepoApi`), list available extensions.
- Port old project's `ExtensionsSettingsScreen` +
  `ExtensionRepoSettingsScreen` (reference).

### 7.2 Trust flow
- When an extension is installed, it's UNTRUSTED until the user approves
  (TrustService from Phase 3b). UI prompt: "This extension is from an unknown
  source. Trust it?" — old project pattern.

---

## 8. Sequencing Rationale

1. **5a before 5b:** Watch needs to link episodes to content. Without identity,
   we'd use `content_key` strings and migrate later (costly, error-prone). The
   user explicitly deferred identity to here (D-042) to "understand the data
   first" — that understanding now exists from Phase 3.
2. **5a ships manual-linking first:** Auto-matching is additive and risky
   (false merges). Manual linking lets 5b proceed immediately. The matching
   engine lands inside 5b as an enhancement.
3. **5b before 5c:** History/updates are derived from watch events. Can't
   detect new episodes for content that isn't watchable yet.
4. **5d after 5b:** Backup needs all data tables (watch, history, library,
   identity) to exist. Backing up a half-built schema means migration pain.
5. **5e parallel:** Extension repo UI is independent of the watch flow. Can be
   done any time after 5a (needs identity to link sources to content).

---

## 9. Decisions to Confirm With User (before starting 5a)

- **Q-052** Episode list default sort: descending (newest first) or ascending?
  Recommend descending (matches most anime apps + old project).
- **Q-053** Updates placement: 5th bottom-nav tab, or a section in Library?
  Recommend Library section (keeps nav at 4 tabs, less crowded).
- **Q-054** Auto-matching scope for 5b: search ALL installed sources, or only
  sources the user explicitly enabled for that anime? Recommend the latter
  (control + performance).
- **Q-055** Backup auto-frequency default: daily / weekly / manual-only?
  Recommend weekly (low overhead, covers most users).

---

## 10. Module additions summary

| Module | Sub-phase | Purpose |
|--------|-----------|---------|
| `:core:identity` | 5a | ContentUID + ExternalReference + matching engine |
| `:feature:watch` (api/impl) | 5b | Watch screen + player UI + sheets |
| `:feature:history` | 5c | Watch history list |
| `:core:updates` | 5c | New-episode detection |
| `:feature:updates` | 5c | Updates screen |
| `:feature:backup` | 5d | Backup/restore + import translators |
| `:feature:extensions-settings` | 5e | Extension + repo management UI |

Estimated module count: 31 (current) → ~38 after Phase 5.

---

## 11. Documentation to update as we go (CORE_RULES §6, §24, §25)

- `APP/ani-kuta/DOCUMENTATION/database/` — add `identity.md` when 5a tables
  land; update `er-diagram.md` + `changelog.md` (§24).
- `AGENT-CONTEXT/memory/decisions.md` — record Q-052..Q-055 answers as D-056+.
- `AGENT-CONTEXT/memory/progress.md` — move sub-phases to "done" as they ship.
- Dashboard (`DASHBOARD/webpage/`) — update module count + Phase 5 progress
  page (delegate to full-stack-dev sub-agent, §19).

---

*This is a plan, not a contract. Adjust per-phase as we learn. The sequencing
rationale (§8) is the load-bearing part — don't reorder sub-phases without
re-evaluating the dependencies.*
