# Progress Log

> Live status of the ANI-KUTA project. **Update after every work session.**

## Current Phase
**Phase 4 — FEATURE SCREENS (in progress).** Core modules (Phase 3) complete. Library, Search, More, Settings, Appearance built. UI polish + accent palette system done this session. Next: finish Phase 4 loose ends → plan Phase 5 (identity system, watch screen, backup/restore).

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
- [x] **This session (web-3a43f99b)**:
  - Library CustomizeSheet height: cap WHOLE column at 70% screen height (was capping inner list only → exceeded limit). Same for search FilterSheet.
  - Browse: added "Browse" CollapsingHeader heading (was missing).
  - Accent palette system: AccentPreset + AccentColors in :core:designsystem, AnikutaTheme accent override, ThemePreferences storage, functional PalettesCarousel (live apply + selection ring + improved card UI).
  - Docs catch-up: progress.md, changelog (Phase 3+4), decisions D-052/D-053, lessons-learned, SESSION.md.

## What's Next
1. **Phase 4 loose ends** (verify in next CI build): library sheet 70% cap, browse heading, accent palettes applying live, light-mode theming across all screens.
2. **Phase 5 — Identity + Watch + Data wiring** (plan in `APP/ani-kuta/DOCUMENTATION/19-phase5-plan.md`):
   - 5a: Identity system (ContentUID + ExternalReference + matching engine — D-032/D-042).
   - 5b: Watch screen (player UI, sheets, episode list, resume — D-049 video caching).
   - 5c: History + Updates + new-episode detection (D-029).
   - 5d: Backup/restore (multi-app import — D-041/D-047) + custom color picker (palette Phase 5 item).
   - 5e: Extension repo management UI (add/remove repos — D-043 no defaults).
3. **Phase 6+**: Ad system + activity tracker UI (D-033), notifications (D-029), manga reader (D-030), novels.

## Blockers / Open Questions
- None blocking. Phase 4 UI work needs device verification (CI builds APK; user tests).
- Custom color picker (palette editor) deferred to Phase 5d — selection + storage work now.

## Last Updated
- Session: web-3a43f99b-57b3-4d89-a26a-63737d005c8f
- By: main agent
- Note: UI fixes (sheet 70% cap, browse heading) + functional accent palette system + documentation catch-up (progress/changelog/decisions/lessons were stale at Phase 2). Phase 5 plan written.
