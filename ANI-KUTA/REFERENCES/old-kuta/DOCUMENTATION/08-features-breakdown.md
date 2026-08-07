# 08 — Features Breakdown

> Per-feature deep dive: what each feature does, how it's built, and key implementation notes.
> Source: `REFERENCES/old-kuta/ANIKUTA/feature/`.

---

## Feature Module Index

| Module | Tab/Screen | LOC (key files) | Status |
|--------|-----------|-----------------|--------|
| `:feature:browse` | Home tab | ~moderate | ✅ active |
| `:feature:library` | Library tab | ~moderate | ✅ active |
| `:feature:search` | Search tab | ~moderate | ✅ active |
| `:feature:my` | Profile tab | ~moderate | ✅ active |
| `:feature:history` | History tab | small | ✅ active |
| `:feature:updates` | Updates tab | ~moderate | ✅ active |
| `:feature:anime-details` | Detail page | VM: 1013 LOC | ✅ active |
| `:feature:watch` | Player host | WatchScreen: 2386 LOC | ✅ active |
| `:feature:video-resolver` | Modal sheet | small | ✅ active |
| `:feature:episode-settings` | Settings hub | small | ✅ active |
| `:feature:extensions-settings` | Extensions mgmt | ~moderate | ✅ active |
| `:feature:settings` | 8 settings screens | ~moderate | ✅ active (README stale) |
| `:feature:trackers` | Tracker login | ~moderate | ✅ active |
| `:feature:backup` | Backup/restore UI | small | ✅ active |
| `:feature:download` | Download queue | ~moderate | ✅ active |
| `:feature:setup-wizard` | 14-step onboarding | 1840 LOC (1 file) | ✅ active |

---

## 1. Browse (Home Tab)

**What**: The landing screen. Shows AniList trending + seasonal anime in horizontal rails.

**How**:
- Fetches from `:core:anilist` GraphQL API.
- Compose `LazyRow` for horizontal rails.
- Cards use `:core:designsystem` components.
- Tapping a card navigates to `:feature:anime-details`.

**Notes**: Originally had a separate `:feature:home` module (removed Phase 9 — Home tab = BrowseScreen).

---

## 2. Library

**What**: User's saved anime, organized by category.

**How**:
- Grid + list view toggle.
- Categories (user-defined, stored in SQLDelight via `:data:anime` → `CategoryRepository`).
- Sort: title, score, last watched, etc.
- Filter: by category, status, tracker.
- Long-press → multi-select → bulk actions.

**Notes**: Category data in `:core:database` SQLDelight schema.

---

## 3. Search

**What**: Search across AniList AND extension sources simultaneously.

**How**:
- Dual search: AniList GraphQL + each installed extension source.
- Results merged, deduplicated by title similarity.
- Filters: genre, status, year, sort.
- Extension sources come from `:data:extension` → `AnimeExtensionManager`.

**Notes**: Title similarity matching is in `:data:extension` (source matcher).

---

## 4. Anime Details (most complex feature)

**What**: The anime detail page — banner, synopsis, episodes, source switcher.

**How**:
- `AnimeDetailsViewModel` (1013 LOC) is **source-agnostic**.
- Uses `AnimeDetailsProviderRegistry` (ADR-041) — tries AniList provider, then Extension provider.
- **3-stage pipeline**: AniList metadata → match to extension source → fetch episode list.
- **In-place source switching**: user can switch AniList ↔ Extension without leaving the page.
- Works because of two-tier identity (ADR-050): `ContentId` stays stable across source switches.
- `EpisodesSection.kt` (inline — no separate `:feature:episode-list` module) shows the episode list.
- Episode thumbnails from `:core:episode-metadata` → `EpisodeMetadataCache`.

**Key files**:
- `AnimeDetailsViewModel.kt` — 1013 LOC, the core VM.
- `EpisodesSection.kt` — episode list UI.
- Source switcher UI component.

**Notes**: This is the hardest feature to rebuild. The source-agnostic design with in-place switching is powerful but complex.

---

## 5. Watch (Player Host — largest file in project)

**What**: The video player screen. Embeds MPV, handles fullscreen, controls.

**How**:
- `WatchScreen.kt` is **2386 LOC** — the single largest file in the project.
- Embeds `:core:player`'s `AnikutaMPVView` (XML-inflated, not Compose).
- **Single MPV instance** (ADR-025) — fullscreen is an overlay swap, not navigation. Player keeps playing when "minimized".
- Player controls: play/pause, seek, speed, subtitle track, audio track.
- `ScrollBlurOverlay` — gradient scrim over video (GPU-cheap, no `RenderEffect`).
- `PlaybackStateStore` saves: speed, subtitle track, audio track, resume position.

**Key files**:
- `WatchScreen.kt` — 2386 LOC.
- Player controls composables.

**Notes**: The 2386-LOC file is a code-smell — should be split in the rebuild. The single-instance MPV pattern is good and should be preserved.

---

## 6. Video Resolver (Modal Sheet)

**What**: A bottom sheet that picks which video to watch (when multiple sources/qualities exist).

**How**:
- `:core:video-resolver` calls the extension source's `fetchVideoList`.
- Extracts playable video URLs.
- Some sources use anti-scraping (PNG headers) — handled by `:core:download`'s `HlsDownloader`.
- User picks a video → navigates to `:feature:watch`.

**Notes**: Phase 8 moved video-resolver logic from `:feature:*` to `:core:video-resolver` to remove feature→feature dependencies.

---

## 7. History

**What**: Recently watched episodes list.

**How**:
- Reads from `WatchProgressStore` (`:core:player`) — JSON in SharedPreferences.
- Keyed by `contentId|episodeNumber`.
- Shows: anime title, episode number, progress bar, last watched time.
- Tapping resumes from last position.

**Notes**: `:data:history` (SQLDelight impl) exists but is UNUSED by the UI — `:feature:history` reads `WatchProgressStore` directly. This is a discrepancy to resolve in the rebuild.

---

## 8. Updates

**What**: Checks for new episodes across the user's library.

**How**:
- `:core:update-checker` polls extension sources for new episodes since last check.
- Schedule + calendar view.
- Live-check option (manual refresh).
- `EpisodeFetchGateway` interface (declared in `:core:update-checker`, impl in `:data:extension`).

**Notes**: Gateway interface pattern lets `:core:*` stay free of `:data:*` deps.

---

## 9. Profile (My)

**What**: User stats + charts + recently watched.

**How**:
- Stats from `WatchProgressStore` + tracker data.
- Charts: episodes watched over time, top genres, watch time.
- Recently watched grid.
- Tracker status (AniList/MAL connected or not).

---

## 10. Trackers

**What**: AniList + MAL tracker sync (OAuth login + status sync).

**How**:
- `:core:tracker` — tracker implementations (AniList + MAL).
- `:feature:trackers` — login UI (OAuth flow).
- `TrackSyncManager` — syncs episode "watched" status.
- Uses `contentId|episodeNumber` key.

**Notes**: Only AniList + MAL implemented. Adding more (Shikimori, etc.) = one class + one Koin line.

---

## 11. Backup/Restore

**What**: Export/import app data.

**How**:
- `:core:backup` uses `List<BackupProvider>` (Koin multi-binding with `named("backupProviders")` qualifier).
- Each provider serializes its data to JSON.
- **Aniyomi format translator** — can import/export Aniyomi backup files.
- `:feature:backup` — UI for selecting what to back up/restore.

**Notes**: The `named("backupProviders")` qualifier is critical — without it, multiple `single<T>` with no qualifier silently overwrite (caused the "only 1 category saved" bug in the old project).

---

## 12. Downloads

**What**: Offline episode downloads.

**How**:
- `:core:download` — download manager (HTTP + HLS + advanced resume).
- `DownloadTask` keyed by `contentId|episodeNumber`.
- HTTP: byte-range resume.
- HLS: segment-level resume.
- `HlsDownloader` — PNG anti-scraping header stripping (megaplay.buzz/kotocdn.site CDNs).
- `:feature:download` — queue UI + downloaded files browser.

---

## 13. Extensions Settings

**What**: Manage installed extension sources.

**How**:
- `:data:extension` — full extension system:
  - **Loader**: DEX classloader (loads APKs).
  - **Installer**: PackageInstaller foreground service.
  - **Manager**: 3 StateFlows (installed, updating, available).
  - **Repo API**: SharedPreferences-backed CRUD for extension repos.
  - **Trust**: SHA-256 set of trusted extensions.
  - **Source matcher**: title similarity matching.
- `:feature:extensions-settings` — list + repo management UI.
- Extensions are Aniyomi-compatible (ADR-029) — load as APKs via `ChildFirstPathClassLoader`.

**Notes**: This is the most complex subsystem. The Aniyomi binary compat constraint shapes the whole `:core:source-api` module.

---

## 14. Settings (8 screens — README is stale)

**What**: App settings.

**How**:
- 8 fully-implemented settings screens: General, Appearance, Player, About, Ads, etc.
- Uses `:core:preferences` — `PreferenceStore`, `ThemePreferences`, `SetupWizardPreferences`.
- `:feature:settings` README says "Empty stub — NOT YET IMPLEMENTED" but it's fully implemented — **README is stale**.

**Notes**: Fix the stale README in the rebuild. Settings should use a consistent pattern (list + detail).

---

## 15. Setup Wizard (14-step onboarding)

**What**: First-launch onboarding flow.

**How**:
- Single 1840-LOC file with a 14-step wizard.
- Gated by `SetupWizardPreferences.isCompleted`.
- Re-runnable from Settings → General → "Run setup wizard again".
- Steps: theme selection, tracker login, extension install, etc.
- Animated transitions between steps.

**Notes**: 1840 LOC in one file is too much — split in the rebuild. The wizard concept is good.

---

## Cross-Cutting Feature Patterns

1. **Bottom sheets**: `dragHandle = null` consistently — custom drag handles.
2. **No feature→feature deps**: Phase 8 moved all shared types/logic to `:core:*`.
3. **Hand-rolled nav**: state-machine in `MainActivity.kt` (Voyager migration planned but not done).
4. **No unit tests**: no feature module ships tests — major gap for the rebuild.
5. **Reactive UI**: all screens observe `StateFlow`/`Flow` from repositories/managers.
