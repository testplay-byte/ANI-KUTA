# Progress Log

> Live status of the ANI-KUTA project. **Update after every work session.**

## Current Phase
**ALL MAJOR PHASES COMPLETE + DB OPTIMIZATION + RATINGS UI + CONTINUE WATCHING UI + PROFILE UI v6 — MERGED TO `main` (feature branch deleted).**

Phases 0-4, 5a/5b/5c, Phase B (auto-link), Phase C (content identity), Phase D (data-management), Phase DL (download system DL.0-DL.8), Phase WP (watch progress + watched status), Phase HI (history page), Phase UP (updates + WorkManager smart engine), Phase SC (schedule list + calendar view), Phase TR (ratings store), Phase NOTIF (notification system), Phase CW (continue watching logic), the Debug Bubble, and the Profile page (genre radar + watch flow + time DNA + heatmap + timeline + crop editor) are ALL DONE and on `main`.

**Merge completed (this session):**
- `feature/db-optimization-ratings-cw` (55 commits) fast-forward merged into `main` — no conflicts (`main` hadn't diverged). `main` now at `c15b1b8`.
- CI on `main` verified GREEN (run 31432557415, commit c15b1b8a) — the final gate before branch deletion.
- Feature branch deleted (local + remote). Only `main` remains.

**Profile UI v6 (D-183..D-186, commit 6945df6 — now on main):**
- **Magnetic snap guard:** Snap now only fires when `firstVisibleItemIndex == 0` (near top). Fixes the "scroll to bottom → auto-scrolls to top" bug.
- **Watch flow sidebar:** Taller (260dp). Card-level transparent scrim for reliable tap-outside close.
- **Time DNA + Recently Watched:** Side-by-side in ONE card — donut left (own bg), recently watched list right (own bg).
- **Heatmap labels:** Column bottom padding 24dp, month-label Box 18dp, day-markers bottom padding 20dp — fixes bottom-half cut-off.

**Profile UI v5 (D-177..D-182, commit 47196ad — now on main):**
- Magnetic snap + gradient blur + equal-width mini tabs. Watch flow: complementary today color, sidebar from LEFT, solid bg. Time DNA donut tint. Genre radar in-web highlight. Avatar crop editor (new AvatarCropScreen.kt). Settings URL/upload state separation. CI fix: Coil3 `result.image` API.

**This session (DB optimization + ratings + continue-watching + watch-progress fixes):**
- **Phase 1 (DB-OPT):** Deleted 2 dead `.sq` files (`extensions.sq`, `metadata.sq` — zero call sites). Enabled `PRAGMA foreign_keys = ON`. Dropped 6 redundant indexes. Added 8 missing indexes (continue-watching partial, retention purges, AniList JOIN, content extension lookup, library dedup). Fixed WP-B1 (`setAutoMarkSuppressed` now clears `completed_at` + INSERT-when-missing guard). Fixed audio-variants bug (added `source_name` + `scanlator` columns to `data_cache_episode`; preserved through enriched cache write; fixed offline-fallback URL bug). Fixed extension trust bug (per-package `isEnabled` flag; only enabled extensions' sources appear in pickers; backward-compat seeding). CI green (run 31348314200).
- **Phase 2 (watch-progress fixes):** WP-B2 (resetAutoMarkSuppressed on FILE_LOADED — re-arms 85% auto-mark). WP-B3 (resume-seek — click same episode → plays from where you left). WP-B4 (save on episode switch). Progress bar in Details episodes list (thumbnail bottom edge, like YouTube). CI green (run 31348683710).
- **Phase 3 (continue-watching UI):** Single-row carousel at top of Browse. Cover thumbnails, EP badges, progress bars, placeholder images. Tap → Details (resume kicks in on play via WP-B3). CI green (run 31348903899).
- **Phase 4 (ratings UI):** Per-anime 10-star rating on Details (right of synopsis title). Per-episode 10-star rating on Watch (below currently playing episode text). Each star = 10 points (0-100 backend). Temporary testing implementation. CI green (run 31349493109, after fixing a composable-scope compile error).

**Branch:** `feature/db-optimization-ratings-cw` (all 4 phases on this branch). Awaiting user device verification before merge to `main`.

⚠️ **Known gaps (deferred per user):**
- Proxy-churn re-resolve NOT wired (D-149) — deferred to future phase.
- Outer retry loop not implemented — deferred.
- Subtitle loading for downloaded episodes still not working on device (D-152 fixes are in but unverified; user deferred to a later session).
- ✅ ~~Rating UI not built~~ — DONE (Phase 4, temporary 10-star implementation).
- ✅ ~~Continue Watching UI not placed~~ — DONE (Phase 3, Browse carousel).
- SQLite UPSERT migration NOT done (SQLite 3.24+ required; API 24-28 ships 3.9-3.22 — can't use `ON CONFLICT DO UPDATE` on minSdk 24). INSERT OR REPLACE kept; callers already read-then-write.
- CHECK constraints for magic strings NOT added (can't ALTER TABLE to add CHECK on existing installs; would need table rebuild). Deferred.
- Extension settings (extension's own preferences UI) — future task per user.

**Next:** User device verification of all 4 phases. Then: merge to `main` + clean up the CI trigger (`feature/**` → `main` only). Then: subtitle loading investigation + Phase 6+ (ads, backup/restore, identity system).

**Previous session (swipe / calendar / notifications):**
- **Swipe background fixed (D-153):** the reveal background in `DetailsScreen.EpisodeRow` + `HistoryScreen.HistoryRow` was invisible because it used `fillMaxSize()` inside a wrap-content-height Box (resolves to 0 height). Switched to `matchParentSize()` (BoxScope) + always-compose with `graphicsLayer` alpha fade. The previous session's `fillMaxSize` "fix" was the regression.
- **Calendar toggle fixed (D-154):** the List/Calendar toggle was hidden because `ScheduleListContent` emitted the toggle + content as bare siblings into a parent Box (later siblings draw on top → list covered the toggle). Wrapped in a `Column`. Also: auto-fetch schedule on first open if empty, calendar `verticalScroll`, empty-state hint, gate the Updates-driven `ScrollBlurOverlay` to the Updates tab.
- **Notification settings UI built (D-155):** `NotificationPreferences` (master toggle + defaults) in `:core:preferences`; `NotificationManager` now respects the global kill switch; `NotificationsSettingsScreen` + ViewModel in `:app` (master toggle, defaults, per-anime library list + detail bottom sheet); Notifications nav row in Settings + `NotificationsKey` wired.
- **CI false-green fixed (D-156):** previous commits `db26c47`/`fd1a9a5` actually FAILED CI (`:app:compileDebugKotlin` — DocumentFile unresolved from the subtitle disk-scan code) but progress.md claimed green. Added `implementation(libs.androidx.documentfile)` to `:app`. CI now genuinely green (run 31275021179, artifact 53 MB).
- **Branch cleanup:** `feature/watch-progress-history-updates` deleted (was fully merged into main; main verified green). Only `main` remains.
- Subtitles intentionally deferred per user (separate session).

⚠️ **Known gaps (deferred per user):**
- Proxy-churn re-resolve NOT wired (D-149) — deferred to future phase.
- Outer retry loop not implemented — deferred.
- Subtitle loading for downloaded episodes still not working on device (D-152 fixes are in but unverified; user deferred to a later session).
- Rating UI not built — store + schema ready, UI pending.
- Continue Watching UI not placed — logic ready, UI deferred.

**Next:** Debug bubble — MERGED to main (D-163..D-165, DB-1..DB-9). `feature/debug-bubble` branch deleted. Next: Database optimization (new AI agent) → Rating UI → Continue Watching UI → Subtitle loading investigation.

## What's Done
- [x] Phase 0 (environment, rules, dashboard, old project documented).
- [x] Phase 1 (architecture plan, design language, all decisions confirmed D-001..D-051).
- [x] Phase 2 (12-module scaffold, CI green).
- [x] **Phase 3 — Core Modules COMPLETE** ✅ (15 modules across 4 sub-phases):
  - 3a Foundation: provider-api, source-api (Aniyomi binary-compat, 36 files), database (8 SQLDelight .sq files).
  - 3b Extensions: data:extension (loader, manager, trust).
  - 3c Playback: player (AnikutaMPVView), player-mpv-lib (AAR wrapper), video-resolver, download.
  - 3d Supporting: metadata (merger + providers), tracker-api, tracker-anilist (TrackSyncManager), activity-tracker (365-day), watch-progress.
- [x] **Phase 4 — Feature Screens (mostly done)**:
  - 4a: App shell (AnikutaRoot, bottom nav, Nav3 backstack), Browse, Details.
  - 4b: Library (grid/list + sort + customize sheet), Search (filter sheet), More, Settings, Appearance (General).
  - Theming: light/dark/AMOLED, accent palette system (D-053 — 10 functional presets + CUSTOM), header blur, adaptive colors.
  - UX: smooth animations (CollapsingHeader, ScrollBlurOverlay, scale-on-press), back gesture (BackHandler), bottom-nav hidden on sub-screens.
- [x] **Phase 5a — Extension Management (done)**: AnimeExtension sealed class, repo system, installer system, AnimeExtensionApi, `:feature:extensions-settings` module, Nav wiring, manifest permissions + service.
- [x] **Phase 5b — Details Page Overhaul (done)**: DetailsViewModel (source linking, fetchEpisodes, searchSource, resolveEpisode), DetailsScreen wired, ManualSearchSheet, ResolverSheet, WatchKey.
- [x] **Phase 5c — Watch Screen (mostly complete)** ✅ — player overhaul this session:
  - **Animiru repo cloned** to `REFERENCES/animiru/ANIMIRU/` + 11 documentation files (8,101 lines) in `REFERENCES/animiru/documentation/` (read-only reference, no code copied — D-065).
  - **Video playback fixed** (audio but no video). ROOT CAUSE: `AnikutaMPVView.initOptions()` was EMPTY — `setVo("gpu")` was never called → MPV had no video output. Ported full `initOptions()` from old project: `setVo`, `profile=fast`, `hwdec=auto` (NOT `auto-copy`), `demuxer-max-bytes=256MB`, `vd-lavc-film-grain=cpu`, all 12 subtitle prefs via `setOptionString`, `tls-ca-file`, etc. (D-061).
  - **Top padding bug fixed**. ROOT CAUSE: `WatchScreen`'s `DisposableEffect(playerMode)` called `setDecorFitsSystemWindows(true)` in minimized mode, conflicting with `enableEdgeToEdge()`. Empty `onDispose` left window corrupted → double top padding on Browse/Library after exiting player. Fixed: removed the `true` call + added cleanup in `onDispose` (D-062).
  - **Loading failed overlay fixed**. ROOT CAUSE: `PlayerObserver` didn't clear error state on `FILE_LOADED`. Fixed: `onEvent(FILE_LOADED)` now calls `updateError(null)` + `updateLoadingState(READY)` + loads tracks.
  - **QualitySheet ported** — replaced placeholder with full accordion server list + quality chips. Created `ResolverServer`/`ResolverAudioVersion`/`ResolverVideo` data classes + `ResolvedVideosRegistry` (in-memory singleton — D-063). `DetailsViewModel.resolveEpisode` now also calls `resolveStructured`. Quality switching = re-loadfile.
  - **SubtitleSettingsSheet ported** — sticky header + 3 sections (Typography / Colors / Position & Misc) + `NumericEntrySheet` (custom keypad) + `ColorPickerSheet` (preset swatches + RGBA sliders). All 12 subtitle prefs added to `PlayerPreferences`. `applySubtitlePreferences()` uses `setPropertyInt`/`setPropertyDouble` for numerics. Uses non-reactive `PlayerPreferences` with local `mutableStateOf` (D-064 — simpler than porting old reactive `Preference<T>` API).
  - **SubtitleTracksSheet** — wired `onOpenSettings` callback to swap to `SubtitleSettingsSheet`.
  - **PlayerInitializer** — simplified mpv.conf (removed `cache=yes`, `hwdec=auto-copy`, `hwdec-codecs`, `sub-ass-force-margins` from conf — now set via `setOptionString` in `initOptions`).
  - **configChanges** — added `uiMode` (theme toggle no longer recreates Activity).
- [x] **Phase DL — Download System (substantially complete, `download-system-plan` branch)** ✅ — implemented across 41 commits (see "Session — Download System" entry below + `download-research/13-implementation-plan.md` status table):
  - **DL.0 Foundations** (5849e13, 379f3a6): download data models, preferences, DB schema (`downloaded_episode` table re-keyed by mainId + episodeKey, `.data.json` as source of truth).
  - **DL.1 Engine + Storage** (9b4c5d7, b8b5d7b, +4 fixes): DownloadManager interface, DefaultDownloadManager, HttpDownloader (Range-resume + validation), HlsDownloader (pure Kotlin), DownloadStorageProvider (SAF + `.data.json` + same-title collision handling), DownloadScanner (scan-on-startup), TempDownloadCache, DownloadLogger.
  - **DL.2 Orchestrator + AutoDownload + proxy-churn** (6382dbe, +5 fixes): DownloadOrchestrator, AutoDownloadEngine (5-step: flatten → rank → applyFallbacks → pick → globalFallback), `ReResolver` types. ⚠️ Proxy-churn re-resolve is BUILT but NOT WIRED (D-149, D003).
  - **DL.3-DL.8** (4298cb3, e29d616, +3 fixes): queue management + dynamic progress, foreground DownloadService (NetworkCallback auto-pause/resume, onTimeout API 35+, onTaskRemoved restart), DownloadNotificationManager (2 channels), 7-section settings UI (drag-reorderable priority/quality/audio/server), downloads page (live queue + bulk actions + downloaded files page), episode download controls on details, player offline integration.
  - **Offline playback** (d83915d, de7c0bc, 1f85339): content:// → fd:// ParcelFileDescriptor conversion + 500ms surface-readiness delay; MPV SIGABRT fixed.
  - **Stability fixes** (DL-CRASH-FIX 1-3, DL-CRITICAL-FIX 1-3, DL-IMPROVE 1-3, DL-REMAINING, METADATA-FIX-v2): DB schema migration via `onOpen` + first-run setup dialog, stale data cache, episodeUrl caching, metadata disappearing, local subtitles.

## What's Next
1. **Download system device testing** (verify on real device): enqueue a download, pause/resume, offline playback of a downloaded episode, auto-download trigger, notification channels, foreground service survival across screen-off/task-removal.
2. **Wire proxy-churn re-resolve + outer retry loop + 2 re-resolve bugs** (D-149, D-151) — DEFERRED per user, grouped into a future phase. Full plan in `download-research/FUTURE-PHASE-DL-GAPS.md`: (a) ~50-line adapter in `:app` implementing `HttpDownloader.ReResolver` + Koin binding + `DownloadModule.kt:92` null→getOrNull(); (b) fix `127.0.0.1` guard (`HttpDownloader.kt:261`) + `video_uri`/`video_url` column bug (`:271`); (c) `RetryPolicy` class + outer retry loop in `launchDownload` catch block + backoff + notification UX; (d) delete or wire `DownloadVideoPickerSheet`. Estimated ~6-8 hours. Manual retry (`retryDownload()`) works as today's fallback.
3. **Nav3 vs hand-rolled nav** — ✅ DECIDED (D-150): keep hand-rolled `mutableStateListOf<NavKey>` + `when(currentKey)` nav as-is. Do NOT migrate to Nav3. R7 (process-death backstack recreation) accepted as a known limitation. Nav3 1.1.5 dep stays on classpath (unused; future cleanup option). Docs updated: `12-nav-research.md` (Resolution note). If R7 becomes important later: hybrid `rememberSaveable` fix (~1-2h, Option C.1 in sandbox `03-nav3-comparison.md`).
4. **Doc-debt sweep** (discrepancy D005): master.md [DONE this session], navigation.md [DONE this session], knowledge/* + decisions.md numbering [DEFERRED until Nav3 + proxy-churn decisions settle, so docs reflect final state].
5. **Watch-progress bug fixes + resume feature** — watch progress IS persisted to SQLDelight (Phase WP shipped `SqlDelightWatchProgressStore`; D-072's in-memory store is superseded). But there are real bugs: (1) `setAutoMarkSuppressed` SQL doesn't clear `completed_at` → stale data on un-mark; (2) `resetAutoMarkSuppressed` never called on FILE_LOADED → CF1 re-arm broken; (3) no resume-seek (users always start at position 0 — `AnikutaMPVView.loadVideo(resumePosition)` exists but WatchScreen bypasses it); (4) no save on episode switch. Plus: Ratings UI + Continue Watching UI (both have backend ready, zero UI).
6. **Episode switching inside WatchScreen** (needs PlayerStateHolder fields: `episodeList`, `currentEpisodeIndex`, `isSwitchingEpisode`) + resume position.
7. **Phase 6+**: ads (D-033), activity-tracker UI, manga reader (D-030), novels, backup/restore (`15-backup-research.md`), identity system (Phase 5d).

## Blockers / Open Questions
- Download system implemented but NOT device-tested yet. Proxy-churn re-resolve + outer retry loop + 2 re-resolve bugs deferred to a future phase (D-149, D-151) — plan in `download-research/FUTURE-PHASE-DL-GAPS.md`.
- Nav3: ✅ DECIDED (D-150) — keep hand-rolled nav; R7 accepted as known limitation. Nav3 1.1.5 dep unused (future cleanup option).
- AGENT-CONTEXT knowledge/* + decisions.md numbering stale — doc-debt sweep deferred (D005) until proxy-churn/retry future phase settles.
- Watch progress IS persisted to SQLDelight (Phase WP — `SqlDelightWatchProgressStore`; D-072 in-memory store superseded). Known bugs: setAutoMarkSuppressed doesn't clear completed_at; resetAutoMarkSuppressed never called on FILE_LOADED; no resume-seek; no save on episode switch.
- Custom color picker (palette editor) deferred to Phase 5f.

## Known doc debt
- **master.md** — ✅ UPDATED this session (was: "Phase 1 blocked" false, nonexistent `knowledge/app-design-language.md` ref, "16 sections" wrong, `ANIKUTA-PROJECT/` path wrong).
- **navigation.md** — ✅ UPDATED this session (was: nonexistent `knowledge/app-design-language.md` ref, "1882 lines" wrong, "21 sections" wrong, "10-16" doc range wrong).
- **progress.md top header** — ✅ UPDATED this session (was stale "Phase 5c"; now reflects Phase DL + the download system).
- **download-research/13-implementation-plan.md** — ✅ UPDATED this session (added status table marking DL.0-DL.8 as implemented + Phase-D disambiguation note).
- **changelog.md** — ✅ UPDATED this session (added Phase DL download-system entries).
- **decisions.md** — ✅ ADDED D-148 (download system) + D-149 (proxy-churn gap) this session. STILL DEFERRED: D-121 missing, D-037/D-038 out of order, D-008 compileSdk 35 (actual 36), D-009 should be superseded by D-034/D-035.
- **knowledge/module-map.md + architecture.md** — DEFERRED. Describe "proposed" 8 core modules (actual 38 Gradle modules). Marked "Draft (finalized in Phase 1)" — never finalized. Big rewrite; do after Nav3 decision.
- **knowledge/tech-stack.md** — DEFERRED. Says compileSdk/targetSdk = 35 (actual 36); OkHttp "TBD" (actual 5.0.0-alpha.14); missing Koin 4.2.2, SQLDelight 2.0.2, Nav3 1.1.5, aniyomi-mpv-lib 1.18.n, Coil 3.0.4.
- **knowledge/old-vs-new.md** — DEFERRED. Says old project location "unknown" (actual: `REFERENCES/old-kuta/ANIKUTA/`, 36 modules, 631 files, 10-file docs).
- **knowledge/dashboard.md** — DEFERRED. Says "5 pages" (recount from actual dashboard).
- **Two "Phase D" tracks collide** — ✅ ADDRESSED this session: data-management Phase D (D-141..D-147) vs download-system Phase D.0-D.8. Going forward, download-system phases are written **"Phase DL.0-DL.8"** to disambiguate. The `download-research/` docs retain "Phase D.0-D.8" historically (with a disambiguation note at the top of 13-implementation-plan.md). Historical REVIEW files unchanged.
- **Repo root pollution** (discrepancy D001): `skills/` (69 generic Z.ai skills) + 234KB `worklog.md` committed on both branches — violates CORE_RULES §4. DEFERRED per user (not a concern right now).

## Last Updated
- Session: feature/debug-bubble → main merge + branch cleanup (Z.ai Code sandbox).
- By: main agent (merge + CI verification + branch deletion).
- Branch: `main` (feature/debug-bubble merged + deleted). `main` is at 0fcc850 (CI cleanup after merge).
- CI: ✅ green on feature/debug-bubble before merge (run 31341636467, commit bd7f31d, artifact 54.1 MB). Main CI verification in progress.
- Note: D-001..D-165 decisions. Debug bubble fully implemented (DB-1..DB-9) and MERGED to main. The `feature/debug-bubble` branch has been deleted (both local + remote). The `build-apk.yml` workflow triggers have been cleaned up to only build on `main` + tags (the `feature/debug-bubble` entry was removed). Next: database optimization (new AI agent will take over).
- **Merge summary:** 53 commits fast-forward-merged from `feature/debug-bubble` into `main`. 51 files changed, 8,706 insertions. The merge added: (1) the full debug bubble feature (DB-1..DB-8), (2) DB Activity tracker with read+write tracking (DB-9), (3) chart sliding, (4) viewMode hoist, (5) export logs, (6) filter toggle, (7) dual-line chart, (8) dashboard DB Viewer page, (9) dashboard debug-bubble page. No changes to any repository, ViewModel, .sq file, or release code path.

## Session web-3a43f99b (twelfth pass) — Double-Resolve Bug Fix

### What was done
- ROOT CAUSE of "loading failed" identified via 2 parallel subagent analyses (COMPARE-OLD-TO-NEW + COMPARE-NEW-TO-OLD): VideoResolver called getHosterList TWICE (once for flat resolve(), once for structured resolveStructured()). AniKotoS extension creates a local proxy server on each call — second call killed first call's proxy URLs.
- FIX: Merged into single resolve() call. ResolverState.Success now includes rawVideos: List<Video>. DetailsViewModel calls videoResolver.buildServers(rawVideos) to derive structured servers from the SAME video list — NO second getHosterList.
- Also matched old project's filter: `videos.filter { it.videoUrl.isNotBlank() }` — rejects videos with empty URLs.
- PlayerErrorOverlay redesigned: inline on player surface (not popup), Close (X) button + Retry button.
- Added === VIDEO PICKED === log at DetailsScreen.onPickVideo showing quality, URL, headers, registry key.
- CI GREEN (8100d91, run 30900950702).

### Key decisions
- D-066: Double-resolve is forbidden. Single resolve() + buildServers() derivation.
- D-067: Error overlay is inline on player surface with Close button (not popup, not force-opening QualitySheet).

## Session web-f53f0459 — Phase 5c Player: Stuck-Loading Fix + Episode State + External Subtitles + Capture-Only Progress

### What was done
- **Stuck-loading regression FIXED (D-068)**: `setSwitchingError()` + 30s watchdog. All explicit failure paths now show errors immediately.
- **Episode-switch state hoisted (D-069)**: `currentEpisodeUrl/Number/Title/resolvedVideosKey` on `PlayerStateHolder`. Episode list highlight + "now playing" card + QualitySheet now reactive to switches.
- **External subtitle/audio loading re-added (D-070)**: `pendingSubtitleTracks/AudioTracks/trackHeaders` on `PlayerObserver`. `sub-add`/`audio-add` on FILE_LOADED with 300ms delay. Wired in initMpv + onQualitySelected + onEpisodeSwitch.
- **SubtitleTrackFormatter ported (D-071)**: ISO 639 → English names. "English" instead of "eng".
- **EpisodeSwitchingOverlay ported (D-073)**: Loading shield over player during switches. Both minimized + fullscreen.
- **Speed setter bug fixed (D-073)**: `setPropertyDouble` instead of `setPropertyInt` (was truncating 1.5f→1).
- **Capture-only WatchProgressStore (D-072)**: InMemoryWatchProgressStore + periodic save (10s) + save-on-dispose. No restore yet.
- **Dead singleOf(::PlayerStateHolder) removed (D-074)**.
- **CORE_RULES updated**: §5 (player scaffolding is not boilerplate + interface exception), §7 (player carve-out), §17 (import rewrite rule).

### Status
- CI pending push (will push after this commit).
- Awaiting device verification.

### What's next
- Phase D: Wire 7 dead fullscreen buttons (skip-next, audio, server, speed, more, PiP, rotate).
- Phase E: 15s fatal-error watchdog, auto-play-next, skip OP/ED, app-exit pause/resume.
- Phase F: Full doc-drift sweep + D-050 re-decide (companion hack).

## Session web-f53f0459 (continued) — Player Playback Fixes + Remaining Phases

### Critical playback fixes (from user log analysis)
- TLS CA cert fix (D-075): deleted empty cacert.pem, guarded tls-ca-file → HTTPS streams work now
- Observer cleanup (D-076): remove MPVLib observers on dispose → no more 4x event duplication
- Error handling rework (D-077): non-intrusive banner + auto-retry (no more dialog box)
- Spinner fix (D-078): pause no longer shows loading spinner
- Episode switch title (D-078): overlay shows correct episode name during switching
- Better error messages: TLS/SSL/HTTP/stream errors captured + appended

### Remaining phases completed
- Episode sanitization (D-079): EpisodeTitleParser — clean titles, no more hashes/code as names
- Speed control (D-080): SpeedSheet wired in fullscreen — presets + slider, live apply
- Skip-next (D-080): wired → switches to next episode
- 15s fatal-error watchdog (D-081): catches stuck HLS streams
- App-exit pause/resume (D-081): ON_STOP pauses playback

### CI status
- All commits green (last: 061c17b)

### What's next
- User device testing of all fixes
- Remaining dead fullscreen buttons (audio, server, more, PiP, rotate)
- Auto-play-next, skip OP/ED
- Full doc-drift sweep

## Session web-f53f0459 (continued) — Phase B: Auto-Link System

### What was done
- **Download button fix (D-122)**: Created `DownloadEpisodeButton` composable (24dp icon in 40dp clickable Box) used consistently in BOTH places (with/without synopsis). Shows toast "Download functionality not yet implemented" on tap.
- **`:core:smart-matcher` module created (D-123)**: New module with:
  - `TitleNormalizer` — normalizes titles (lowercase, strip punctuation, remove season/year suffixes like S2, Season 2, (TV), 2nd Season, II/III/IV).
  - `LevenshteinDistance` — character-level edit distance + similarity ratio (two-row DP, O(n) space).
  - `MatchResult` — sealed: Match/NoMatch/Skipped/Error.
  - `SmartMatcherConfig` — threshold (0.80 default), strategy (FUZZY/STRICT/MANUAL), yearBonus (0.10), containsBonus (0.05).
  - `SmartMatcher` — main matcher: normalize → Levenshtein → contains bonus → year bonus → cap 1.0 → threshold check.
  - `AutoLinkResult` — sealed: Cached/Matched/NoMatch/Skipped/Error.
  - `AutoLinkService` — orchestrator: cache check → per-source setting → AniList search → SmartMatcher → cache result.
  - `SmartMatcherModule` — Koin DI.
- **`AutoLinkPreferences` (D-124)**: Added to `:core:preferences`. Stores:
  - Global toggle (auto_link_enabled, default true)
  - Strategy (auto_link_strategy: fuzzy/strict/manual, default fuzzy)
  - Threshold (auto_link_threshold, default 0.80)
  - Per-source overrides (auto_link_source:$sourceId: default/on/off)
  - Link cache (auto_link_cache:$sourceId:$hash(animeUrl): anilistId)
- **DetailsViewModel rewrite (D-125)**: Added 9th + 10th + 11th constructor params (anilistProvider, autoLinkService, autoLinkPreferences). New state: `autoLinkState` (Idle/Searching/Matched/NoMatch/Skipped/Error), `anilistSearchState` (Idle/Searching/Empty/Results/Error), `showManualLinkSheet`. New methods: `performAutoLink()`, `searchAniListForLink()`, `linkAniListEntry()`, `skipAniListLink()`, `unlinkAniList()`, `openManualLinkSheet()`, `dismissManualLinkSheet()`, `mergeAniListIntoUnified()`. Auto-link kicks off after `loadFromExtension()` succeeds. On match, merges AniList data via `AniListDetailsProvider.mergeInto()` + triggers episode metadata fetch.
- **ManualLinkSheet (D-126)**: Bottom sheet for manual AniList linking. Header "Link to AniList" + search field (pre-filled with extension title) + search button + results list (cover + title + score + year + Link button) + "Skip AniList link" button. Auto-searches on open. Full states (Idle/Searching/Empty/Results/Error).
- **AutoLinkSettingsScreen (D-127)**: New settings screen accessible from SettingsScreen hub → "Metadata" → "Auto-Link". Global section: master toggle + strategy selector (Fuzzy/Strict/Manual segmented toggle) + threshold slider (0.50–1.00). Per-extension section: 3-way override (Default/Always link/Never link) per installed extension.
- **DetailsScreen updates (D-128)**: Added auto-link badge ("Linked to AniList" with check icon) + searching spinner ("Auto-linking...") in the banner. Added "Link to AniList" / "Unlink AniList" to the three-dot menu (extension entries only, with divider). Wired ManualLinkSheet.
- **`AniListDetailsProvider` registered as concrete type (D-129)**: anilistModule now registers it both as `AnimeDetailsProvider` (named "anilist") AND as concrete `AniListDetailsProvider` for direct injection into DetailsViewModel.
- **Subagent review**: All 24 files reviewed for compile errors — clean. No issues found.

### CI status
- Awaiting push + CI build.

### What's next
- User device testing of Phase B (auto-link ON match, auto-link OFF, manual link, skip, per-extension override).
- Phase C: contentId system (migrate identity, watch progress, library).
- Phase D: Multi-source metadata (MAL, TMDB, Kitsu providers).

## Session web-f53f0459 (continued) — Phase B Fixes + Repo Reorganization

### User testing feedback (Phase B)
User tested Phase B and reported:
- ✅ Download button size now consistent (both with/without synopsis) + toast works.
- ✅ Auto-link ON: Opens extension anime → "Linked to AniList" badge → metadata loads.
- ✅ Auto-link cache: Re-opening loads instantly (user noted they didn't ask for cache — will revisit).
- ✅ Auto-link NoMatch: Opens manual link sheet correctly with search + skip.
- ✅ Manual link/unlink: Three-dot menu shows correct options.
- ⚠️ Manual link: Tapping a result does NOT update the details page UI (stale extension data shown).
- ❌ AutoLinkSettingsScreen UI: Header/first option overlapping, "texture overlapping", ugly.
- ❌ Per-extension override: Not reactive — must leave + return to see changes.
- ⚠️ Stale metadata when switching details pages (unlinked content).
- 📋 User wants a data-source selector (AniList vs Extension priority).

### Fixes implemented (D-130 through D-133)

**D-130: Data-source priority + selector**
- Added `DataSourcePriority` enum (ANILIST/EXTENSION) to `:core:common`.
- `AnimeDetailsProvider.mergeInto()` now takes a `priority` parameter.
  - ANILIST: AniList values overwrite extension values. Used for manual link.
  - EXTENSION: Extension values kept; AniList fills nulls. Used for auto-link.
- Added `dataSourcePriority` field to `UnifiedAnime`.
- Added `switchDataSource(priority)` method to DetailsViewModel.
- Added `DataSourceSelector` composable (segmented toggle) to DetailsScreen — shown only when both anilistId + sourceId are present.

**D-131: Stale metadata fix**
- `loadFromAniList()` + `loadFromExtension()` now reset ALL state flows before loading (was only resetting _state + autoLinkState).

**D-132: Per-extension override reactivity**
- Each `PerExtensionCard` now holds a local `mutableStateOf` snapshot keyed by `ext.pkgName`. Tapping updates the local state immediately (UI flips live) AND persists.
- Redesigned AutoLinkSettingsScreen UI: split into 4 separate cards (SwitchCard, StrategyCard, ThresholdCard, PerExtensionCard) with shorter subtitles + animated color transitions.

**D-133: Repo reorganization + new core rules**
- Moved AGENT-CONTEXT/, APP/, DASHBOARD/, REFERENCES/ into single wrapper folder `ANI-KUTA/`.
- Updated CI workflows (build-apk.yml + deploy-dashboard.yml) with new paths.
- Updated .gitignore + README.md.
- CORE_RULES §4: single-wrapper-folder rule (non-negotiable).
- CORE_RULES §15: sandbox recovery rule (re-clone if environment feels off).

### Subagent review
All 7 modified files reviewed for compile errors — clean. No issues found.

### CI status
- Awaiting push + CI build.

### What's next
- User device testing of all fixes.
- Phase C: contentId system (migrate identity, watch progress, library).

## Session web-f53f0459 (continued) — Data Source Selector Fix + Phase C Plan v2

### User testing feedback (round 2)
User tested the Phase B fixes and reported:
- ✅ Download button: still good + toast works.
- ✅ Data source selector: AniList toggle works (shows AniList data).
- ❌ Data source selector: Extension toggle does NOT update (stale AniList data shown). Had to reopen the page.
- 📋 Selector placement: should be in the three-dot menu, not below the banner.
- 📋 Selector should be available for AniList entries too (when they have a linked source).
- 📋 Selector will eventually support more sources (TMDB, Kitsu) — not just AniList vs Extension.
- ✅ Auto-link settings UI: redesigned, looks much better now. Per-extension override works live.
- ✅ Repo structure: looks good.
- 📋 Phase C: user reviewed the plan, gave detailed feedback on contentId design.

### Fixes implemented (D-134)

**D-134: Data source selector — fix reactivity + move to three-dot menu**
- Root cause: `mergeAniListIntoUnified` overwrote the base UnifiedAnime's fields with AniList data. Switching back to EXTENSION priority couldn't recover the original extension data.
- Fix: Added `extensionBase` + `anilistBase` to DetailsViewModel. The displayed UnifiedAnime is always computed by `remergeBases(priority)` which merges the two original bases. Switching priority never loses data.
- Moved selector from LazyColumn body to three-dot DropdownMenu.
- Made selector available for AniList entries with linked sources (`linkSource()` now creates `extensionBase` from the picked SAnime).
- Updated `linkSource()`, `unlinkSource()`, `unlinkAniList()` to manage the bases.

### Phase C plan v2 created
- Replaced the old Phase C plan with a new v2 that incorporates user feedback.
- Key design: stable UUID contentId + content_source_link table (one-to-many).
- Database tables with full SQL (content, content_source_link, watch_progress, library, watch_history).
- Architecture: new `:core:content` module with ContentRepository + ContentResolver.
- 6 open questions for the user (Q-001 through Q-005 + Q-006 confirmed).
- Honest analysis of the user's "changing contentId" proposal — explained why a stable ID is safer.
- No migration needed (watch progress, library, history aren't set up yet).

### CI status
- Awaiting push + CI build.

### What's next
- User reviews Phase C plan v2 + answers open questions.
- After confirmation, use full-stack-dev agent to convert plan into a web page.
- Then implement Phase C (C.1 → C.5).

## Session web-f53f0459 (continued) — Phase C plan v4 (final)

### User feedback on plan v3
User reviewed the Phase C plan v3 + dashboard page and gave detailed feedback:
- ✅ Two-ID system (Main ID + Content ID) is good.
- ❌ Content ID missing extension ID + source ID — needs 6 sections, not 5.
- 📋 Repo URL format: ends with `index.min.json` (e.g. `https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json`).
- ❌ Web page: tables should be one-per-row, not two-per-row.
- 📋 Session scope: focus ONLY on content ID system (main + detail + lookup tables). Defer watch progress/library/history/tracking.
- 📋 Use separate detail tables per source type (anilist_details, extension_details, other_source_details).

### Plan v4 created (D-135)
- Content ID format v2: 6 sections with `sourceId` added. Uses repo DB ID (integer) instead of full URL.
- 8 tables: 4 lookup (data_sources, systems, extension_repos, extensions) + 1 main (content) + 3 detail (anilist_details, extension_details, other_source_details).
- Removed deferred tables (watch_progress, library, watch_history, content_source_link) from this session's scope.
- 10 confirmed decisions (Q-001 through Q-010).
- Dashboard web page updated: one table per row, new Content ID format, new detail tables, removed deferred tables, added "Deferred" section.

### CI status
- Awaiting push + CI build.

### What's next
- User reviews plan v4 + dashboard page.
- If approved, implement Phase C (C.1 database schema → C.2 content module → C.3 DetailsViewModel integration → C.4 console logging).

## Session web-f53f0459 (continued) — Phase C implementation (content identity + library)

### What was done
- **Content ID format fix**: Changed from repo DB ID to full repo URL per user request. The URL is essential for backup/restore + retrieving more extension IDs.
- **New module `:core:content`**: ContentIdGenerator, ContentRepository, ContentResolver, ContentSeeder.
- **8 content tables + 2 library tables** created in SQLDelight.
- **Lookup tables seeded** on first launch (data_sources, systems, Default library category).
- **DetailsViewModel**: wired ContentResolver + ContentRepository. Calls resolveContentForAniList/resolveContentForExtension on load. Added toggleLibrary() + isInLibrary state.
- **DetailsScreen**: bookmark button now works (saves/un-saves to Default category).
- **LibraryViewModel**: rewritten to use ContentRepository instead of PreferenceStore. Fetches content records + AniList data for grid display.
- **Subagent review**: all 18 files pass compile check.

### CI status
- Awaiting push + CI build.

### What's next
- User device testing of the library system.
- If issues, fix them.
- Then continue with watch progress, history, tracking (deferred).

## Session web-f53f0459 (continued) — Cross-source dedup + library categories

### User testing feedback (Phase C round 1)
User tested Phase C and reported:
- ✅ Library save works (bookmark button saves, remembers across restarts).
- ✅ Library page shows saved anime.
- ❌ **Duplicate library entries**: Saved anime from AniList, then opened same anime from extension → 2 separate library entries (should be 1 — same content).
- ❌ Extension anime not showing as saved after auto-link (the mainId was different).
- ❌ Extension library entry gives 404 error when opened (anilistId=0 for extension-only content).
- ❌ Data-source selector + unlink not working smoothly after linkSource.

### Fixes implemented (D-137, D-138)

**D-137: Cross-source content deduplication**
- Root cause: `resolveContentForExtension` didn't check auto-link cache → always created new content record. `mergeAniListIntoUnified` didn't persist the link in the database.
- Fix: `resolveContentForExtension` now checks `autoLinkPreferences.getCachedAniListId()` first. If cached + content exists → links to existing mainId. `mergeAniListIntoUnified` now calls `contentResolver.linkAniList()` to persist. `unlinkAniList` calls `contentResolver.unlinkAniList()`.

**D-138: Library categories system**
- CategoryPickerSheet (long-press bookmark) — shows categories with checkboxes + create new category.
- ContentRepository: full category CRUD.
- DetailsViewModel: category state + methods.
- LibraryViewModel: category filtering + management.
- library.sq: renameCategory + getCategoriesForContent queries.

### CI status
- CI #197 GREEN. APK built successfully.

### What's next (pending)
- Library page category tabs UI (showing categories as tabs at the top) — ViewModel logic is ready, LibraryScreen UI needs updating.
- Long-press category tab → delete/rename dialog.
- Extension library entry 404 error (navigation issue — library passes anilistId=0 for extension-only content).
- Document the data structures in DOCUMENTATION/database/.

## Session web-f53f0459 (continued) — D-139: Cross-source dedup root cause + library crash + category tabs

### User testing feedback (Phase C round 2)
- ❌ Cross-source dedup still not working — extension anime not showing as saved after auto-link.
- ❌ Library crash: `IllegalArgumentException: Key "194829" was already used` — duplicate anilistId keys in LazyVerticalGrid.
- 📋 Category picker should be a popup, not a bottom sheet.
- ❌ Data source selector: cover switches but nothing else changes.
- ❌ Unlink AniList: extension-only state doesn't load info.
- 📋 Library page category tabs UI not implemented.

### Root cause analysis
The cross-source dedup failure was because `linkSource()` (called when linking a source from the AniList side) didn't cache the reverse mapping. When the same anime was later opened from the extension, `resolveContentForExtension` couldn't find the existing content record → created a new one with a different mainId → bookmark didn't show as saved → library had duplicate entries.

### Fixes implemented (D-139)
1. **linkSource()** — now caches reverse mapping + persists extension link in DB + fetches full extension details.
2. **Library dedup** — deduplicates by anilistId to prevent LazyGrid crash.
3. **CategoryPickerSheet** — changed to AlertDialog popup.
4. **remergeBases()** — STRICT switching (primary values only, no fallback).
5. **Library category tabs** — CategoryTabsRow + long-press delete/rename + create new category.

### CI status
- CI #199 GREEN.

### What's next
- User device testing.
- Extension library entry 404 error (navigation issue — still pending).
- Document data structures in DOCUMENTATION/database/.

## Session web-f53f0459 (continued) — D-140: Library crash + 404 + live reload + category tabs

### User testing feedback (Phase C round 3)
- ✅ Cross-source dedup working (bookmark shows as saved from either entry point).
- ❌ Library crash: `Key "0" was already used` — multiple extension-only entries with anilistId=0.
- ❌ 404 error when opening extension-only content from library.
- ❌ Library not updating live (needs app restart).
- ❌ Data source selector disappears on reopen (only Refresh/Share shown).
- ❌ Category tabs UI bad (bubbles, lock icon, "+" button).
- ❌ Long-press category tab not working.
- 📋 Category tabs smart features (Default hides when empty, All hides when 1 cat).
- 📋 Library header should show total entries.
- 📋 Delete category with move-to-default option.

### Fixes implemented (D-140)
1. **LibraryEntry** data class — uses mainId (stable UUID) as key. Fixes crash + 404.
2. **LibraryViewModel** rewritten — builds LibraryEntry from content records.
3. **LibraryScreen** rewritten — uses mainId as key, LibraryEntry for navigation, live reload via LaunchedEffect.
4. **MainActivity** — navigation checks hasAniListId → AniList, else → Extension.
5. **loadLinkedSource** — restores extensionBase from DB on reopen.
6. **Category tabs** — smart features, text+underline style, no "+" button.
7. **Long-press** — Rename / Delete (with Move to Default option).
8. **Library header** — total entries subtitle.
9. **CategoryPickerSheet** — removed lock icon.
10. **New setting** — showCategoryCounts.

### CI status
- CI #201 GREEN.

### What's next
- User device testing.
- Extension library entry source link restoration (when opening from library, episodes should load from the linked source).
- Document data structures in DOCUMENTATION/database/.

## Session web-f53f0459 (continued) — D-141: Library UI fixes + multi-select + Phase D plan

### User testing feedback (Phase C round 4)
- ✅ Cross-source dedup working.
- ✅ Library crash fixed.
- ✅ 404 error fixed.
- ✅ Library live reload working.
- ✅ Data source selector on reopen working.
- ✅ Category tabs smart features working.
- ❌ Extension-only cover images not showing in library.
- ❌ Library heading should be "X in Library" (not separate heading + subtitle).
- ❌ Category count format should be "[3] Default" (not "Default (3)").
- ❌ Delete dialog formatting (Move to Default should only show if category has entries).
- ❌ No white spacer line below category tabs.
- ❌ Library performance (re-fetches from AniList on every tab switch).
- 📋 Multi-select mode for library entries.
- ❌ Refresh button in details page doesn't work.

### Fixes implemented (D-141)
1. Extension-only cover images — fixed coverUrl assignment.
2. Library heading — "X in Library" as main title.
3. Category count — "[3] Default" format.
4. Delete dialog — Move to Default only if entries exist.
5. White spacer line below tabs.
6. In-memory cache — anilistCache prevents re-fetching on tab switch.
7. Multi-select mode — long-press → selection mode with bottom bar.
8. Refresh button — DetailsViewModel.refresh() wired to menu item.

### Phase D plan written
- `DOCUMENTATION/planning/data-management/PHASE-D-PLAN.md`
- Covers: local metadata cache, browse page cache + refresh, details page multi-stage refresh, image caching, backup/restore, library performance.
- 6 implementation phases (D.1-D.6).
- 5 open questions for the user.

### CI status
- CI #204 GREEN.

### What's next
- User device testing of D-141.
- Answer Phase D open questions (Q-001 through Q-005).
- Begin Phase D implementation.

## Session web-f53f0459 (continued) — D-142, D-143: UI fixes + bottom nav replacement

### D-142: Extension cover images + multi-select UI
- Fixed extension-only cover images: `resolveContentForExtension` now accepts UnifiedAnime + stores `extension_detail` (with `thumbnailUrl`) in DB.
- Category count format: changed to rounded brackets "Default (3)".
- Multi-select top buttons: styled with icons (DoneAll/Clear/SyncAlt).
- Multi-select bottom bar: replaced nav bar with opaque surface + icons (Close/Category/Delete).
- Phase D plan v2: removed backup/restore, metadata never expires, 6hr homepage only, vibration, solid caching, two source types.

### D-143: Bottom nav bar replacement + library total count
- Added `selectionModeContent` parameter to `AnikutaBottomNavBar`.
- Created `LibrarySelectionMode` + `LocalLibrarySelectionMode` CompositionLocal.
- LibraryScreen syncs selection state → AppRoot reads it → passes SelectionActionBar to nav bar.
- SelectionActionBar replaces the nav pills INSIDE the floating pill (not overlay).
- Library header: totalEntries shows TOTAL across ALL categories.

### CI status
- CI #210 GREEN.

### What's next
- Start Phase D implementation (D.1-D.5).

## Session web-f53f0459 (continued) — D-143 + Phase D.1

### D-143: Bottom nav bar replacement + library total count
- Added `selectionModeContent` parameter to AnikutaBottomNavBar.
- Created LibrarySelectionMode + LocalLibrarySelectionMode CompositionLocal.
- LibraryScreen syncs selection state → AppRoot reads it → passes SelectionActionBar.
- SelectionActionBar replaces nav pills INSIDE the floating pill (Cancel/Category/Delete with icons).
- Library header: totalEntries shows TOTAL across ALL categories.

### Phase D.1: Local metadata cache
- New module :core:data-cache with DataCacheRepository.
- 3 new SQLDelight tables: anime_metadata_cache, data_cache_episode, browse_cache.
- DetailsViewModel checks cache first → instant display → then fetches + caches.
- LibraryViewModel checks cache first → no network on tab switch.
- Metadata never expires. All data persists across restarts.

### CI status
- CI #216 GREEN.

### What's next (Phase D.2-D.5)
- D.2: Browse page cache + pull-to-refresh + 6-hour auto-update.
- D.3: Details page multi-stage refresh (vibration + visual indicators).
- D.4: Coil disk cache (500MB, persistent).
- D.5: Library pull-to-refresh with vibration + lazy loading.

## Session web-f53f0459 (continued) — Phase D.2-D.5 COMPLETE

### D.2: Browse page cache + pull-to-refresh + 6-hour auto-update
- BrowseViewModel reads from browse_cache first → instant display.
- If cache expired (6h) → fetches from network in background → updates cache.
- Pull-to-refresh with vibration (drag down at top).
- Background refresh indicator (subtle spinner when auto-updating).

### D.3: Details page multi-stage refresh
- refreshEpisodesList() — only refreshes episodes from extension source.
- refreshMetadata() — only refreshes metadata + updates anime_metadata_cache.
- refreshAll() — full refresh (both).
- Three-dot menu "Refresh" calls refreshAll().
- RefreshStage enum + RefreshState sealed interface for future scroll-based triggers.

### D.4: Coil disk cache (500MB, persistent)
- ImageLoaderFactory with 500MB disk cache + 25% memory cache.
- Registered as Coil singleton via SingletonImageLoader.setSafe.
- All AsyncImage composables use the persistent disk cache.
- Images survive app restarts.

### D.5: Library pull-to-refresh with vibration
- refreshLibrary() clears cache + re-fetches from network.
- Pull-to-refresh with vibration (drag down at top).
- Visual indicators: pull progress spinner + background refresh spinner.

### CI status
- CI #220 GREEN. Phase D complete.

### Phase D summary
- D.1: Local metadata cache ✅ (CI #216)
- D.2: Browse page cache + pull-to-refresh ✅ (CI #220)
- D.3: Details page multi-stage refresh ✅ (CI #220)
- D.4: Coil disk cache 500MB ✅ (CI #220)
- D.5: Library pull-to-refresh ✅ (CI #220)

## Session web-f53f0459 (continued) — D-146, D-147: Episode caching + offline fixes

### D-146: Multi-select category picker + cache-first details + offline + refresh feedback
- Multi-select category picker: no longer auto-closes. Multiple selections + Done button.
- Cache-first details: loadFromAniList skips network if cache exists.
- Offline mode: shows cached data instead of error when network fails.
- Refresh feedback: "Refreshing..." overlay with spinner.

### D-147: Episode caching + offline extension fallback
- Episode list caching: fetchEpisodes checks data_cache_episode first → instant from cache.
- After network fetch → caches episodes + enriched metadata locally.
- Offline extension: tryCachedExtensionData() loads from DB when network fails.
- Extension-only anime shows full details + episodes offline (if previously opened).

### CI status
- CI #224 GREEN.

### Still pending (will fix in next iteration)
- Browse pull-to-refresh (pointerInput gesture detection issue).
- Library 3-stage pull-to-refresh.
- Search page AniList caching (12-hour refresh).
- Library selection mode UI (fade unselected covers).

## Session — Download System (Phase DL.0-DL.8) — `download-system-plan` branch

> **Consolidated entry** for the 41 download-system commits on the
> `download-system-plan` branch (41 ahead of `main`, 0 behind). These commits
> were made across multiple working sessions but were never logged in
> `progress.md` — this entry closes that doc-drift gap (discrepancy D002,
> discovered during the analysis-and-doc-update session).

### What was done (by phase — commit SHAs from `git log`)

**Research + planning**
- `ba2141f` (DL-RESEARCH): 14 download-system research docs (`download-research/00-16`) + dashboard webpage.
- `8cb8177` (DL-PLAN-FIX): plan v2 — 5 review rounds (REVIEW-1..5) + 72 MUST-FIX items (M1-M72) applied.

**Phase DL.0 — Foundations**
- `5849e13` (DL-D0): download data models, preferences, `downloaded_episode` DB schema (re-keyed by `main_id` + `episode_key`, 5-digit padded; `.data.json` as durable source of truth; content FORMAT folders `video`/`images`/`text`).
- `379f3a6` (DL-D0-FIX): REVIEW-D0 fixes.

**Phase DL.1 — Engine + Storage**
- `9b4c5d7` (DL-D1-1): download data models + preferences.
- `b8b5d7b` (DL-D1-2): progress tracker + cache + logger + `DownloadManager` interface.
- `65fe7a4`, `baa7628`, `cebafb0`, `c558beb` (DL-D1-FIX1-4): interface alignment, 30+ compile errors, TempDownloadCache API, FileOutputStream param.
- Delivered: `DefaultDownloadManager`, `HttpDownloader` (Range-resume + validation + HLS re-detection), `HlsDownloader` (pure Kotlin, no encrypted HLS), `DownloadStorageProvider` (SAF + `.data.json` reinstall recognition + same-title collision handling), `DownloadScanner` (scan-on-startup), `TempDownloadCache`, `DownloadLogger`.

**Phase DL.2 — Orchestrator + AutoDownload + proxy-churn**
- `6382dbe` (DL-D2-1): `DownloadOrchestrator`, `AutoDownloadEngine` (5-step pure-function pipeline: flatten → rank → applyFallbacks → pick → globalFallback), `ReResolver` types.
- `8ad6899`, `add3932`, `5bbb5be`, `e633d81`, `30ed37a` (DL-D2-FIX1-5): video-resolver dep, List<Int> Comparable, ResolverState serialization, ReResolver return-in-collect, missing import.
- ⚠️ **Proxy-churn re-resolve is BUILT but NOT WIRED** (D-149, discrepancy D003): `HttpDownloader.reResolver = null` (`DownloadModule.kt:92`); the promised `:app` `downloadAppModule` was never created; the two `ReResolver` interfaces are signature-incompatible. Wiring deferred per user — see `ani-kuta-analysis/04-proxy-churn-explanation.md` for the full plan.

**Phase DL.3-DL.8 — Queue + Service + Notifications + Settings UI + Downloads UI + Player + QoL**
- `4298cb3` (DL-D3-D8-1): settings UI + downloads page + episode download control + player integration + QoL (one batch commit).
- `e29d616` (DL-D3-D8): wired download states into `DetailsViewModel` + verified all UI files.
- `a926b08`, `d5a8a00`, `e9d5592` (DL-D3-D8-FIX1-3): duplicate `downloadStates`, `DownloadNavKeys` package + MainActivity imports, duplicate download imports.
- Delivered: `DownloadQueue` (Mutex + Semaphore, all REVIEW-5 fixes M6/M11/M15/M31/M34/M36/M37/M38/M41/M42/M43 wired), foreground `DownloadService` (NetworkCallback auto-pause/resume, `onTimeout` API 35+, `onTaskRemoved` restart), `DownloadNotificationManager` (2 channels), 7-section settings UI (drag-reorderable priority/quality/audio/server lists), downloads page (live queue + bulk actions + 10s auto-clear of COMPLETED + downloaded files page grouped by anime), episode download controls on details page, player offline integration.

**Offline playback**
- `d83915d` (DL-OFFLINE): offline playback + downloaded episode UI + Play/Delete menu.
- `de7c0bc` (DL-PLAYBACK-FIX): MPV offline playback — `content://` → `fd://` ParcelFileDescriptor conversion.
- `1f85339` (DL-CRITICAL-FIX3): MPV SIGABRT — 500ms surface-readiness delay for `fd://` + episode metadata disappearing.
- `66947ea`, `be4d1ea` (DL-REMAINING + FIX): subtitle naming, quality switcher, compile fix.

**Stability / migration / flow fixes**
- `616a57f`, `1e34c33`, `5949521` (DL-CRASH-FIX 1-3): DB schema migration crash — drop+recreate download tables on upgrade; migration via `onOpen`; first-run setup dialog.
- `f30b290`, `336f264` (DL-UI-FIX 1-2): download button shows resolver sheet in download mode + 360p/HSUB defaults; `ResolvedVideo` type for download.
- `6717e02` (DL-FLOW-FIX): extensive download flow logging + 360p/HSUB preference migration.
- `d60bd83`, `2c4c81f` (DL-DOWNLOAD-FIX 1-2): `effectiveLinkedSource` null in resolver sheet; moved to top-level scope.
- `ab86b26` (DL-CRASH-FIX3): Toast on main thread + localhost proxy connection failure handling.
- `8b9d1ab`, `cf01023` (DL-IMPROVE 1-2): downloaded episodes show as downloaded + `data.json` populated + hidden files; `downloaded_episode` DB insert + `data.json` FK fields + always-show Downloaded button.
- `9812814`, `d6f0d21` (DL-IMPROVE 3 + FIX): stale data cache + `DownloadedFilesScreen` navigation + `WatchKey` metadata; `upsertEpisodeMetadataBatch` method name.
- `454fe86`, `c359aff` (DL-CRITICAL-FIX 1-2): offline playback crash + stale data + episodeUrl caching; `SQLiteException` — `data_cache_episode.episode_url` column missing.
- `234ea15` (METADATA-FIX-v2, branch HEAD): metadata disappearing + episode list + local subtitles.

### Status
- Download system substantially complete. Branch is 41 commits ahead of `main`.
- CI status: green on recent commits (per prior session logs; verify on next push).
- ⚠️ **Two known code bugs found during analysis** (beyond the unwired proxy-churn):
  1. `HttpDownloader.kt:261` only checks `url.startsWith("http://localhost")` — but AniKotoS uses `127.0.0.1` (per `lessons-learned.md` D-092). The re-resolve guard misses `127.0.0.1` URLs.
  2. `HttpDownloader.kt:271` writes the fresh URL to the `video_uri` column, but the download read path uses `video_url` — a `DownloadStore.updateDownloadVideoUrl` query is missing. (Found by proxy-churn research subagent.)
- ⚠️ `DownloadVideoPickerSheet` (235 LOC) exists but is NOT wired — `MainActivity.handleDownloadEpisode()` handles `EnqueueResult.ShowPicker` with a `// TODO: show the DownloadVideoPickerSheet (Phase D.6 follow-up). For now, log only`.

### What's next (download system)
1. Device testing (enqueue / pause-resume / offline playback / auto-download / notifications / foreground-service survival). **Checklist:** `APP/ani-kuta/DOCUMENTATION/download-device-testing-checklist.md`.
2. Wire proxy-churn re-resolve (D-149, deferred per user) + fix the two bugs above in the same change.
3. Wire `DownloadVideoPickerSheet` (the multi-quality picker for downloads).
4. Implement outer retry loop (`RETRYING` state + `RetryPolicy`) — currently max attempts = 2, spec says 6.
5. **D-FIX-SUB device verification (section C of the checklist)** — confirm the 5 subtitle fixes work on a real device (especially C5: subtitles survive reinstall).

### D-FIX-SUB — Downloaded-episode subtitle fixes (this session)
- **5 issues fixed** (see `decisions.md` D-152 + `changelog.md` D-FIX-SUB section):
  1. `subtitleUris` was never populated on task completion → offline playback had NO subtitles (CRITICAL). Fixed via `PublishResult` return type.
  2. Subtitle fetch sent no headers → 403 on protected CDNs. Fixed via `applyTrackHeaders` (MPV comma-format) + UA fallback.
  3. `DownloadTrack` had no `headers` field. Fixed: added field; `DownloadOrchestrator` passes video headers as fallback.
  4. Subtitle naming was index-based → picker showed "Subtitle 1". Fixed: lang-based naming + `extractSubtitleLangFromUri` → "English" / "Japanese".
  5. `DownloadScanner` set `subtitleUris = emptyList()` on reinstall → subtitles lost. Fixed: `findSubtitleUrisForEpisode` re-discovers them.
- **Sub-agent reviewed (SUB-REVIEW):** COMPILES. Reviewer caught a header-format logic bug (JSON vs MPV-comma) — fixed.
- **Awaiting device verification** (checklist section C, esp. C5 reinstall test).
