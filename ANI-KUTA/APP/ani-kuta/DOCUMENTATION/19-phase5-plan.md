# Phase 5 — Functional App: Extensions → Details → Watch → Identity

> **Revised plan** (session web-3a43f99b, second pass). Supersedes the first
> Phase 5 plan which put identity first — that was wrong. The user's directive:
> *"first make the app functional so we can test things, then move to the deeper
> parts."* Extensions and the watch flow come FIRST; the identity graph is a
> later refinement.
>
> **Why the re-order:** You can't test the watch page without a source
> installed, and you can't fetch episodes without the Details page wired to a
> source. Identity (the full ContentUID graph) is not needed to *watch* anime —
> a minimal "this anime links to source X's SAnime" row is enough for 5a–5c.
> The proper graph (5d) is a *refinement* that enables backup portability and
> auto-matching, not a prerequisite for playback.

---

## 1. Goal

**Make the app watchable end-to-end, then layer on the deeper systems.**

The user must be able to: install an extension from a repo → browse a source's
catalog → open an anime's details → see its episode list → tap an episode →
pick a video server/quality from the resolver sheet → watch in the MPV player →
resume from last position. Everything after that (identity, history, updates,
backup) is persistence and polish.

**Exit criteria (what "Phase 5 done" means):**
- [ ] 5a: User can add an extension repo, browse its index, install an extension, trust it.
- [ ] 5a: Installed sources appear in a source browser (popular/latest grid).
- [ ] 5b: Tapping an anime (from Browse or a Source) opens a Details page with banner, info, **episode list**.
- [ ] 5b: Tapping an episode opens the **VideoResolver bottom sheet** (servers/quality). Selecting one navigates to Watch.
- [ ] 5b: User can manually link an AniList anime to a source's anime (search → link).
- [ ] 5c: Watch screen plays the video via MPV, with controls, seek, resume.
- [ ] 5c: Resume from last saved position (D-049 video caching for instant resume).
- [ ] 5d: Minimal linking from 5b migrated to the proper ContentUID + ExternalReference graph.
- [ ] 5e: Watch history records + displays; new-episode detection runs.
- [ ] 5f: Backup/restore imports an Aniyomi `.tachibk`; custom color picker works.

---

## 2. Sub-Phases (revised logical order)

```
5a Extensions ─► 5b Details ─► 5c Watch ─► 5d Identity ─► 5e History/Updates ─► 5f Backup/Polish
   (enabler)      (hub)        (player)    (refinement)    (persistence)        (portability)
```

| Sub-phase | Name | Depends on | Why this position | Key risk |
|-----------|------|------------|-------------------|----------|
| **5a** | Extension Management | Phase 3b (data:extension) | **First.** No sources = no episodes = nothing to test. This is the gate to everything else. | Repo index API format varies per repo |
| **5b** | Details Page Overhaul | 5a (sources available) | Second. The Details page is the hub: shows episodes, links sources, launches resolver→watch. Current Details screen is bare (no episodes). | Source linking UX (manual search flow) |
| **5c** | Watch Screen | 5b (episode + video resolved) | Third. Once an episode + video URL are resolved, the player plays it. This is the "testable" milestone. | MPV lifecycle in Compose (D-050 fix) |
| **5d** | Identity System | 5a–5c (linking data exists to migrate) | Fourth. Upgrades 5b's minimal `source_link` rows to the proper ContentUID + ExternalReference graph. Refinement, not gate. | Migration correctness (don't lose links) |
| **5e** | History + Updates | 5c (watch events feed history) | Fifth. Derived from watch events + new-episode detection over installed sources. | New-episode detection has no old-project reference |
| **5f** | Backup/Restore + Color Picker | 5d (identity graph for export) | Last. Backup needs all data tables + identity to be portable. Color picker is a small standalone. | Multi-format import compat |

**Key insight:** 5a–5c deliver a **watchable app**. 5d–5f deliver a **complete,
portable, refined** app. The user can test 5a–5c on a device; 5d–5f are
invisible refinements (except the color picker UI).

---

## 3. Sub-Phase 5a — Extension Management (the enabler)

**Goal:** The user can install extensions, add repos, trust extensions, and
browse installed sources' catalogs. After 5a, the app "has content."

**Reference:** Old project `feature/extensions-settings/` —
`ExtensionsSettingsScreen.kt` (446 LOC), `ExtensionRepoSettingsScreen.kt`
(296 LOC). Rebuild clean, don't copy-paste (D-038).

### 3.1 What Phase 3b already built (reuse)
- `:data:extension` — `ExtensionLoader` (child-first classloader),
  `ExtensionManager` (load/trust, reactive `StateFlow`), `TrustService`,
  `Extension`/`LoadResult` models. ✅
- `:core:provider-api` — `ExtensionProvider` abstraction (D-031 — multi-
  ecosystem foundation). ✅
- `:core:source-api` — `eu.kanade.tachiyomi.animesource.*` (Aniyomi binary-
  compat contract, 36 files). ✅

### 3.2 What 5a builds (the gaps)
| Module / file | Purpose | Old-project ref |
|---------------|---------|-----------------|
| `:data:extension` → `installer/` | `ExtensionInstaller` (APK install via PackageInstaller), `ExtensionInstallService`, `ExtensionInstallReceiver`, `PackageInstallerBackend` | old `data/extension/installer/` (5 files) |
| `:data:extension` → `repo/` | `ExtensionRepo` (model), `ExtensionRepoApi` (fetch index JSON), `ExtensionRepoRepository` (CRUD + verify), `RepoVerificationResult` | old `data/extension/repo/` (3 files) |
| `:data:extension` → `updatechecker/` | `EpisodeFetchGateway` (per-source new-episode check — used by 5e, but the interface lives here) | old `updatechecker/` |
| `:feature:extensions-settings` (api/impl) | Two screens: `ExtensionsSettingsScreen` (installed/untrusted/available lists, install/uninstall, trust prompt, enable/disable per source) + `ExtensionRepoSettingsScreen` (add/remove repos, verify URL, browse index) | old `feature/extensions-settings/` |

### 3.3 Trust flow (D-043 — NO default repos)
- User adds a repo URL → `ExtensionRepoApi` fetches `index.json` → verify
  signature/fingerprint → store in `extensions.sq` (`extension_repo` table,
  Phase 3a already has the schema).
- Browse repo's extension list → tap Install → APK downloads →
  `PackageInstaller` installs → on install, extension is UNTRUSTED.
- Trust prompt: "This extension is from `<repo>`. Trust it?" → user confirms →
  `TrustService` stores the fingerprint → `ExtensionManager.loadAll()` picks it
  up → source becomes available.
- **No default repos** (D-043). User supplies their own.

### 3.4 Source browser (new — old project had it inside Browse)
- Once sources are installed + trusted, a "Sources" view (either a tab in
  Browse or a section) lists them. Tapping a source opens its catalog:
  `source.fetchPopularAnime(page)` → grid of `SAnime`.
- Tapping an `SAnime` → Details screen (5b) in "extension entry" mode.

### 3.5 Modularization for future ecosystems (D-031)
- The `ExtensionProvider` abstraction (Phase 3b) is the seam. Aniyomi is the
  first impl. Future: Mangayomi, Cloudstream, Kotatsu — each a new
  `:core:provider-<ecosystem>` module + its own installer/repo adapter if the
  format differs. The `:feature:extensions-settings` UI stays shared; it talks
  to the `ExtensionProvider` interface, not Aniyomi directly.
- **5a builds the Aniyomi path only.** The abstraction is there; we don't
  pre-build the others (no premature abstraction — CORE_RULES §10).

### 3.6 Decisions to confirm (5a)
- **Q-052** Source browser placement: new 5th bottom-nav tab "Sources", or a
  section/mode in Browse? *Recommend: section in Browse (keep 4 tabs).* → renumbered Q-056 (see §9).

---

## 4. Sub-Phase 5b — Details Page Overhaul (the hub)

**Goal:** The Details page shows full anime info + an episode list, lets the
user link sources, and launches the resolver → watch. After 5b, the user can
reach the Watch screen (which 5c makes playable).

**Reference:** Old project `feature/anime-details/` — `AnimeDetailScreen.kt`
(327 LOC, unified AniList+extension), `EpisodesSection.kt`, `DetailBanner.kt`,
`DetailInfo.kt`, `DetailContent.kt`, `SourceSwitcherMenu.kt`,
`ManualSearchSheet.kt`, `AniListSearchSheet.kt`, `EpisodeDownloadControl.kt`.

### 4.1 Current state (what's wrong)
- Current `DetailsScreen.kt` (240 LOC) is bare: banner, cover, title, score,
  description. **No episodes. No source linking. No resolver.** The user said:
  *"The look is very bad. We need to focus on its look."*

### 4.2 What 5b builds
| File | Purpose |
|------|---------|
| `DetailsScreen.kt` (rewrite) | Unified entry (AniList ID OR extension SAnime). Banner + collapsing header + info + description + episodes section. Adaptive theming from cover color (D-048). |
| `DetailsViewModel.kt` (rewrite) | Loads via `AnimeDetailsProviderRegistry` (AniList OR extension). Manages episode list state, source linking, download states. |
| `EpisodesSection.kt` | Episode list (LazyColumn): number, title, thumbnail, filler tag, download state, tap → resolver. Sort (asc/desc). |
| `SourceSwitcherMenu.kt` | 3-dot menu: switch between linked sources for the same anime, "Search for source" (opens ManualSearchSheet), "Link to AniList" (opens AniListSearchSheet). |
| `ManualSearchSheet.kt` | Bottom sheet: search installed sources by title → list candidates → tap to link. Creates a `source_link` row (5b minimal identity, upgraded in 5d). |
| `AniListSearchSheet.kt` | Bottom sheet: search AniList by title → link an extension anime to an AniList entry (for metadata enrichment). |

### 4.3 Minimal source linking (the 5b ponytail → 5d upgrade)
- 5b uses a SIMPLE `source_link` row: `(anime_key, source_pkg, source_anime_url, source_anime_title, linked_at)`.
  - `anime_key` = the temporary `content_key` string (`"<ecosystem>:<source_id|->:<external_id>"`) already used in Phase 4.
  - This is enough to: show episodes for a linked source, switch sources, play episodes.
- **5d upgrades this** to `ContentUID` + `ExternalReference` (proper graph).
  The migration is mechanical: each `source_link` row → one `ContentUID` + one
  `ExternalReference`. No data loss, no UX change.

### 4.4 Episode → resolver → watch flow
1. User taps an episode row → `DetailsViewModel` calls `VideoResolver` (Phase
   3c, already built) with the `SEpisode` + `AnimeSource`.
2. `VideoResolver` fetches `source.fetchVideoList(episode)` → `List<Video>`
   (each Video has URL + quality + server label).
3. **VideoResolver bottom sheet** appears (reuse Phase 3c `VideoResolverSheet`
   UI from old project `feature/video-resolver/VideoResolverSheet.kt`):
   lists servers/qualities, user picks one.
4. On pick → `onNavigate(WatchKey(animeKey, episodeUrl, episodeTitle, ...))`
   → Watch screen (5c).

### 4.5 Decisions to confirm (5b)
- **Q-053** Episode list default sort: descending (newest first)? *Recommend: yes (matches old project + most anime apps).*
- **Q-054** Default video quality: auto / highest / ask-each-time? *Recommend: ask-each-time (user picks in resolver sheet).*

---

## 5. Sub-Phase 5c — Watch Screen (the player)

**Goal:** Play the resolved video via MPV with controls, seek, resume. After
5c, the app is **watchable** — the core testable milestone.

**Reference:** Old project `feature/watch/` — `WatchScreen.kt` (2386 LOC,
**must split** per D-038), `WatchRequest.kt`, `sheets/SpeedSheet.kt`,
`sheets/PlayerSheets.kt`, `WatchEpisodeDisplayPrefs.kt`.

### 5.1 What Phase 3c already built (reuse)
- `:core:player` — `AnikutaMPVView` (wraps aniyomi-mpv-lib, D-050 KoinComponent
  fix applied), `PlayerStateHolder`, `PlaybackStateStore`, `PlayerObserver`,
  `PlayerInitializer`. ✅
- `:core:player-mpv-lib` — AAR wrapper module (D-044, swappable). ✅
- `:core:video-resolver` — `VideoResolver`, `ResolverState`. ✅
- `:core:watch-progress` — `WatchProgress`, `WatchProgressStore` (saved
  positions). ✅
- `:core:download` — `DownloadManager` (for offline episodes, later). ✅

### 5.2 What 5c builds (the split)
| File | LOC est. | Purpose |
|------|----------|---------|
| `WatchScreen.kt` | ~400 | Scaffold: AndroidView(MPV), state hoisting, lifecycle, back gesture. |
| `WatchViewModel.kt` | ~300 | Episode list, current video, resume position, player commands (seek, speed, track). |
| `sheets/SpeedSheet.kt` | ~80 | Playback speed picker (0.25x–4x). |
| `sheets/PlayerSheets.kt` | ~250 | Settings sheet (HW decoder, skip-op, subtitle delay), audio track picker, subtitle track picker. |
| `sheets/ResolverSheet.kt` | ~150 | Re-opens the video resolver mid-playback (switch server/quality). Reuses Phase 3c VideoResolver. |
| `WatchControlsOverlay.kt` | ~350 | Play/pause, seek bar, time, next/prev episode, open sheets, lock, fullscreen exit. Auto-hide. |
| `WatchEpisodeDisplayPrefs.kt` | ~100 | Episode list display settings (port from old `feature/episode-settings`). |

### 5.3 Player lifecycle (D-050 fix — verify it holds)
- `AnikutaMPVView` uses `KoinComponent` (not companion `lateinit var`). ✅
- 5c verifies: MPV view created via `AndroidView` in Compose, survives config
  changes, releases on dispose. **Single instance** (old project pattern —
  don't create multiple MPV instances).
- `DisposableEffect` to release MPV resources on screen exit.

### 5.4 Video caching for instant resume (D-049)
- MPV config (set in `PlayerInitializer`, Phase 3c): `cache-secs=120`,
  `demuxer-max-bytes=150MiB`, `stream-cache-dir` = app cache dir (persistent).
- On resume: `WatchProgressStore` provides saved position → seek to it. The
  cached segment around the position plays instantly while the stream loads.
- 5c verifies the cache persists between sessions (kill app, reopen, resume).

### 5.5 Episode navigation
- Next/Prev episode buttons in the controls overlay. Uses the episode list
  from `DetailsViewModel` (passed via `WatchKey` or re-fetched).
- When the current episode ends, auto-advance to next (toggleable pref).

---

## 6. Sub-Phase 5d — Identity System (refinement)

**Goal:** Upgrade 5b's minimal `source_link` rows to the proper `ContentUID` +
`ExternalReference` graph (D-032). Enables backup portability (5f) and
auto-matching (additive).

**Why now, not first:** The app is already watchable (5a–5c). Identity is
needed for *portability* (backup/restore across apps) and *convenience* (auto-
matching), not for playback. Doing it after the watch flow means we migrate
real data (not theoretical), and the user has seen the app work.

### 6.1 Data model (SQLDelight)
- `content_uid`: `id` (app UUID), `ecosystem` (VIDEO/IMAGE/TEXT), `title`, `created_at`.
- `external_reference`: `id`, `content_uid_id` (FK), `provider` (ANILIST /
  source-package), `external_id`, `confidence` (HIGH/MEDIUM/LOW), `is_primary`,
  `created_at`. Unique partial index on `(provider, external_id)` WHERE
  `is_primary = 1` (SQLDelight partial indexes — why we stayed on SQLDelight, D-035).
- **Migration from 5b's `source_link`:** each row → one `ContentUID` + one
  `ExternalReference` (HIGH confidence, manual link). Drop `source_link`.

### 6.2 Modules
- New `:core:identity` — `ContentUid`, `ExternalReference`, `IdentityRepository`
  (create/merge/split/find), `MatchingEngine` (auto-link by title similarity).
- Update `:core:metadata` — `MetadataMerger` keys off `ContentUid`.
- Update `:feature:anime-details` — `SourceSwitcherMenu` + `ManualSearchSheet`
  now create `ExternalReference` rows instead of `source_link` rows.

### 6.3 Auto-matching (additive, optional in 5d)
- When the user opens an AniList anime's details AND sources are installed, the
  `MatchingEngine` searches sources for a matching `SAnime` by normalized
  title + year. Confidence HIGH (exact + year), MEDIUM (fuzzy), LOW (single
  candidate). User confirms or overrides.
- **Ship 5d with manual linking working first** (from 5b). Auto-matching is an
  enhancement added once manual is stable.

### 6.4 Merge/split
- User can merge two ContentUids (same anime, different sources linked
  separately) or split (wrong auto-match). UI in Details screen.

---

## 7. Sub-Phase 5e — History + Updates (persistence)

**Goal:** Watch history records + displays; new-episode detection runs.

**Depends on:** 5c (watch events), 5d (identity for stable content keys in
history).

### 7.1 History
- `:feature:history` — list of watched episodes (anime, episode, timestamp,
  progress %). Port `HistoryScreen` + `HistoryViewModel` from old project.
- Data source: `:core:activity-tracker` events (Phase 3d) + `:core:watch-progress`.
  History is a VIEW over watch events, not a separate table.

### 7.2 Updates (new-episode detection — D-029, no old-project reference)
- **New work.** `:core:updates` — `UpdateChecker` (per source, calls
  `source.fetchEpisodeList` + diffs against last-seen), `UpdateStore`
  (last-checked timestamp per content), `UpdatesRepository`.
- `:feature:updates` — Updates screen (list of new episodes, "mark read",
  "open"). Schedule via WorkManager (default every 6h, user-configurable).
- **Notifications (D-029)** come AFTER this — can't notify on undetected
  episodes. Defer notifications to Phase 6.

### 7.3 Library integration
- New episodes badge the Library card with a count.
- "Updates" — section in Library (keeps nav at 4 tabs) OR a 5th tab. Confirm
  with user (Q-055).

---

## 8. Sub-Phase 5f — Backup/Restore + Custom Color Picker (portability)

**Goal:** Backup/restore (multi-app import), custom accent color picker.

**Depends on:** 5d (identity graph for portable export).

### 8.1 Backup/restore (D-041, D-047)
- `:feature:backup` — rebuild properly (D-047), don't copy-paste.
- **Import:** Aniyomi/Animiru/Anikku `.tachibk` (protobuf — reuse old
  `AniyomiBackupTranslator`); Mangayomi `.backup` (JSON-in-zip — new translator).
- **Export:** `.anikuta` v2 (JSON-in-zip, includes identity graph + all tables).
- **Merge semantics:** import matches existing ContentUids by external
  references; duplicates skipped; conflicts prompt user.
- UI: `BackupSettingsScreen` (frequency, max backups, categories), restore
  flow with category selection + summary dialog.

### 8.2 Custom color picker (D-053 CUSTOM editor — the palette Phase 5 item)
- The accent palette system (D-053) ships in Phase 4 with 10 presets + CUSTOM
  (defaults to Lime). 5f adds the **color picker UI** for CUSTOM:
  - `CustomColorSheet` — HSV picker + hex input + live preview.
  - Wires to `ThemePreferences.setCustomAccent(color)` (already implemented).
- Port old project's `CustomColorSheet.kt` (reference, rebuild clean).

---

## 9. Decisions to confirm with user (before starting 5a)

- **Q-056** (was Q-052) Source browser placement: 5th bottom-nav tab "Sources",
  or a section/mode in Browse? *Recommend: section in Browse (keeps 4 tabs).*
- **Q-057** (was Q-053) Episode list default sort: descending (newest first)?
  *Recommend: yes.*
- **Q-058** (was Q-054) Default video quality: auto / highest / ask-each-time?
  *Recommend: ask-each-time (resolver sheet).*
- **Q-059** (was Q-055) Updates placement: Library section, or 5th bottom-nav
  tab? *Recommend: Library section.*
- **Q-060** Auto-matching scope for 5d: search ALL installed sources, or only
  sources the user explicitly enabled for that anime? *Recommend: the latter
  (control + performance).*
- **Q-061** Backup auto-frequency default: daily / weekly / manual-only?
  *Recommend: weekly.*

---

## 10. Sequencing rationale (why this order, not the old one)

1. **5a before everything:** No sources → no episodes → nothing to test. The
   user's #1 priority. The old plan buried extension UI in "5e" (parallel,
   late) — wrong.
2. **5b before 5c:** The Watch screen needs an episode + a resolved video URL.
   The Details page is what produces those. Building Watch first would mean
   hardcoding test videos — useless.
3. **5c is the testable milestone:** After 5c, the user can install an
   extension, browse, open details, play an episode. **The app is functional.**
4. **5d after 5c:** Identity is a refinement. The minimal `source_link` from
   5b is enough to watch. Migrating to the graph after the watch flow works
   means we migrate *real usage data*, not theory. And the user sees the app
   work before we ask them to care about identity.
5. **5e after 5c:** History/updates are derived from watch events. Can't
   detect new episodes for content that isn't watchable.
6. **5f last:** Backup needs identity (5d) + all data tables (5a–5e). The
   color picker is small and standalone — bundled here for efficiency.

**The old plan's mistake:** It treated identity as a "foundation" that
everything else depends on. In reality, the watch flow only needs a *minimal*
link (source_pkg + source_anime_url), which a single DB row provides. The full
graph is a *quality* improvement (portability, auto-matching), not a *functional*
prerequisite. The user correctly sensed this: *"first make it functional, then
the deeper parts."*

---

## 11. Module additions summary

| Module | Sub-phase | Purpose |
|--------|-----------|---------|
| `:data:extension` (add installer/ + repo/ subpackages) | 5a | APK installer + repo API + repo repository |
| `:feature:extensions-settings` (api/impl) | 5a | Extensions + repo management screens |
| `:feature:anime-details` (rewrite impl) | 5b | Episodes, source linking, resolver launch |
| `:feature:watch` (api/impl) | 5c | Watch screen + player UI + sheets (split from old 2386 LOC) |
| `:core:identity` | 5d | ContentUID + ExternalReference + matching engine |
| `:feature:history` | 5e | Watch history list |
| `:core:updates` | 5e | New-episode detection |
| `:feature:updates` | 5e | Updates screen |
| `:feature:backup` | 5f | Backup/restore + import translators |

Estimated module count: 31 (current) → ~40 after Phase 5.

---

## 12. Future phases (adjusted)

The full phase roadmap, now that Phase 5 is "functional app + refinements":

- Phase 0–3: ✅ done (env, plan, scaffold, core modules).
- Phase 4: ✅ mostly done (feature-screen UIs + accent palette system).
- **Phase 5:** Functional app (extensions → details → watch → identity → history/updates → backup). *This plan.*
- Phase 6: Ad system + activity-tracker UI (D-033) + notifications (D-029, needs 5e).
- Phase 7: Manga reader (D-030 — image content type).
- Phase 8: Novels (D-030 — text content type).
- Phase 9: Polish, testing, release.

---

## 13. Documentation to update as we go (CORE_RULES §6, §24, §25, §26)

- `APP/ani-kuta/DOCUMENTATION/database/` — add `identity.md` when 5d tables
  land; update `er-diagram.md` + `changelog.md` (§24).
- `AGENT-CONTEXT/memory/decisions.md` — record Q-056..Q-061 answers as D-056+.
- `AGENT-CONTEXT/memory/progress.md` — move sub-phases to "done" as they ship.
- `AGENT-CONTEXT/memory/changelog.md` — one-line per sub-phase completion.
- Dashboard (`DASHBOARD/webpage/`) — update module count + Phase 5 progress
  page per sub-phase (delegate to full-stack-dev sub-agent, §19). **Verify the
  build passes + sidebar/nav reflect current state** (CORE_RULES §26 — the
  gap that caused the stale "Phase 3" sidebar item).

---

*This plan is the corrected Phase 5. The sequencing rationale (§10) is load-
bearing — don't reorder sub-phases without re-evaluating the dependencies.
The user explicitly rejected the prior order; respect their directive:
functional first, refinements second.*
