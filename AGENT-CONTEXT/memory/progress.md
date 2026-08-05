# Progress Log

> Live status of the ANI-KUTA project. **Update after every work session.**

## Current Phase
**Phase 5c — WATCH SCREEN (mostly complete).** Phase 4 feature screens + Phase 5a (extensions) + Phase 5b (details) done. Phase 5c player overhaul done this session: video playback fixed (initOptions ported), top-padding bug fixed, loading overlay fixed, QualitySheet ported (3-tier server→audio→video), SubtitleSettingsSheet ported (typography/colors/position + NumericEntrySheet + ColorPickerSheet), SubtitleTracksSheet wired, PlayerInitializer simplified, configChanges uiMode added. Next: device testing, episode switching, resume position, top-nav polish.

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

## What's Next
1. **Phase 5c device testing + loose ends** (verify in next CI build):
   - On-device test: video actually renders (initOptions fix), no top padding after exiting player, loading overlay clears on FILE_LOADED, quality switching works, subtitle settings apply live.
   - Episode switching inside WatchScreen (next/previous episode from minimized list — needs `PlayerStateHolder` fields: `episodeList`, `currentEpisodeIndex`, `isSwitchingEpisode`).
   - Resume position (wire `WatchProgressStore` — save on pause/exit, restore on loadfile).
   - Top-nav bar polish (minimized mode pill header — verify collapse-on-scroll).
2. **Phase 5 — Functional App** (plan in D-054, `APP/ani-kuta/DOCUMENTATION/19-phase5-plan.md`):
   - **5a Extension Management** — ✅ DONE (data + UI + installer + repos + nav).
   - **5b Details Page Overhaul** — ✅ DONE (DetailsViewModel + ManualSearchSheet + ResolverSheet + WatchKey).
   - **5c Watch Screen** — ✅ MOSTLY DONE (player overhaul done this session; episode switching + resume pending).
   - **5d** Identity System → **5e** History/Updates → **5f** Backup/Color-picker.
   - Decisions D-055..D-065 confirmed.
3. **Phase 6+**: Ad system + activity-tracker UI (D-033), notifications (D-029, needs 5e), manga reader (D-030), novels.

## Blockers / Open Questions
- Nothing blocking. Phase 5c player overhaul needs device verification (CI builds APK; user tests on real device — verify video renders, no top-padding bug, quality switch, subtitle settings apply live).
- Episode switching inside WatchScreen pending (needs PlayerStateHolder fields).
- Resume position pending (WatchProgressStore wiring).
- Custom color picker (palette editor) deferred to Phase 5f.
- Q-056..Q-061 (Phase 5 plan §9) all answered (D-055..D-060).

## Known doc debt
- None currently (caught up this session). CORE_RULES §26 now enforces continuous verification.

## Last Updated
- Session: web-3a43f99b (eleventh pass) — Phase 5c player overhaul
- By: main agent (player overhaul) + documentation subagent (DOCS-UPDATE)
- Note: Phase 5c player overhaul complete — initOptions ported (D-061), top-padding bug fixed (D-062), ResolvedVideosRegistry (D-063), SubtitleSettingsSheet with non-reactive prefs (D-064), Animiru repo cloned as read-only reference (D-065). Next: device testing + episode switching + resume position.

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
