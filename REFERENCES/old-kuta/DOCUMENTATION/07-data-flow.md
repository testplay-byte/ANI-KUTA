# 07 — Data Flow

> How data moves through the old ANIKUTA app, end-to-end.
> From user tapping an anime, to watching a video, to tracking progress.

---

## The Big Picture

```
User taps anime
      │
      ▼
┌─────────────────┐     ┌──────────────────────┐
│ :feature:browse │ ──▶ │ :feature:anime-details│
│  or :search     │     │  (AnimeDetailsVM)     │
└─────────────────┘     └──────────┬───────────┘
                                   │
                    ┌──────────────┼──────────────┐
                    ▼              ▼              ▼
            ┌────────────┐ ┌────────────┐ ┌────────────┐
            │ AniList API│ │ Extension  │ │ EpisodeMeta│
            │ (:core:    │ │ Source     │ │ data Cache │
            │  anilist)  │ │ (:data:ext)│ │ (:core:ep) │
            └────────────┘ └────────────┘ └────────────┘
                    │              │
                    ▼              ▼
            ┌─────────────────────────────┐
            │  AnimeDetailsProvider       │
            │  Registry (ADR-041)         │
            │  3-stage: AniList→match→ep  │
            └──────────────┬──────────────┘
                           │
                           ▼
            ┌─────────────────────────────┐
            │  User taps an episode       │
            └──────────────┬──────────────┘
                           │
                           ▼
            ┌─────────────────────────────┐
            │  :feature:video-resolver    │
            │  (modal sheet — picks video)│
            └──────────────┬──────────────┘
                           │
                           ▼
            ┌─────────────────────────────┐
            │  :core:video-resolver       │
            │  extracts playable URL      │
            └──────────────┬──────────────┘
                           │
                           ▼
            ┌─────────────────────────────┐
            │  :feature:watch             │
            │  embeds :core:player (MPV)  │
            └──────────────┬──────────────┘
                           │
                           ▼
            ┌─────────────────────────────┐
            │  WatchProgressStore         │
            │  (JSON in prefs, keyed by   │
            │   contentId|episodeNumber)  │
            └──────────────┬──────────────┘
                           │
                    ┌──────┴──────┐
                    ▼             ▼
            ┌────────────┐ ┌────────────┐
            │ History    │ │ Trackers   │
            │ (WatchProg)│ │ (AniList/MAL│
            │            │ │  sync)     │
            └────────────┘ └────────────┘
```

---

## Stage 1: Discovery (Browse / Search)

1. **Browse tab** (`:feature:browse`) fetches trending/seasonal anime from **AniList GraphQL API** (`:core:anilist`).
2. **Search tab** (`:feature:search`) queries both AniList AND installed extension sources (`:data:extension`).
3. Results are displayed as cards. User taps one → navigates to anime details.

**Data shape**: `Anime` model (from `:core:common`) with `LocalId`, `ContentId`, title, cover image, score, etc.

---

## Stage 2: Anime Details

The `AnimeDetailsViewModel` (1013 LOC — the most complex VM) is **source-agnostic**. It uses `AnimeDetailsProviderRegistry` (ADR-041) which tries providers in order:

1. **AniList provider** (`:data:anime`) — fetches metadata from AniList.
2. **Extension provider** (`:data:extension`) — if the anime has a source link, fetches from the extension source.
3. **3-stage pipeline**:
   - Stage 1: AniList metadata (title, cover, synopsis, score).
   - Stage 2: Match to an extension source (title similarity).
   - Stage 3: Fetch episode list from the matched source.

The user can **switch sources in-place** (AniList ↔ Extension) without leaving the details page. This works because of the **two-tier identity** (ADR-050): `ContentId` stays stable across source switches.

---

## Stage 3: Episode List + Metadata

- Episode list comes from the extension source (or AniList if no source).
- **EpisodeMetadataCache** (`:core:episode-metadata`) enriches episodes with:
  - Thumbnail URLs (from AniList, Jikan, or Anikage sources).
  - Air dates.
  - Episode titles.
- Cached by `contentId|episodeNumber` key.

---

## Stage 4: Video Resolution

User taps an episode → `:feature:video-resolver` shows a modal sheet:

1. **`:core:video-resolver`** calls the extension source's `fetchEpisodeList` → `fetchVideoList`.
2. The resolver extracts a **playable video URL** from the source's response.
3. Some sources use anti-scraping (PNG headers, obfuscated HLS) — `HlsDownloader` in `:core:download` handles PNG header stripping for megaplay.buzz/kotocdn.site CDNs.
4. User picks a video quality/source from the sheet → navigates to watch screen.

---

## Stage 5: Playback (MPV)

`:feature:watch` embeds `:core:player`'s `AnikutaMPVView`:

- **Single MPV instance** (ADR-025) — fullscreen is an overlay swap, not navigation. The player keeps playing when you "minimize".
- `AnikutaMPVView` is XML-inflated (not Compose) because `obtainStyledAttributes` requires `XmlBlock$Parser`.
- Player preferences are companion-`lateinit` initialized.
- **ScrollBlurOverlay** — gradient scrim over the video (no `RenderEffect`, GPU-cheap).

---

## Stage 6: Progress Tracking

As the user watches:

1. **`WatchProgressStore`** (`:core:player`) saves position every few seconds.
   - Stored as JSON in SharedPreferences.
   - Keyed by `"$contentId|$episodeNumber"` (ADR-050 two-tier identity).
2. **`PlaybackStateStore`** saves the last playback state (speed, subtitle track, audio track).

---

## Stage 7: History + Tracker Sync

- **History** (`:feature:history`) reads from `WatchProgressStore` (NOT `:data:history` — that's implemented but unused by the UI). Shows recently watched episodes.
- **Trackers** (`:core:tracker`) sync watch status to AniList/MAL:
  - `TrackSyncManager` uses the same `contentId|episodeNumber` key.
  - OAuth for AniList + MAL login (`:feature:trackers`).
  - Updates episode "watched" status on the tracker.

---

## Stage 8: Downloads (Offline)

- `:core:download` handles HTTP + HLS downloads.
- `DownloadTask` keyed by `contentId|episodeNumber`.
- Advanced resume support (byte-range for HTTP, segment-level for HLS).
- `:feature:download` shows the queue + downloaded files browser.
- Downloaded files are played through the same `:core:player` MPV instance.

---

## Stage 9: Backup/Restore

- `:core:backup` uses `List<BackupProvider>` (Koin multi-binding with `named("backupProviders")` qualifier).
- Each provider serializes its data to JSON.
- Aniyomi format translator allows importing/exporting Aniyomi backup files.
- `:feature:backup` provides the UI.

---

## Key Patterns in the Data Flow

1. **ContentId is the universal key** — everything (progress, downloads, metadata, tracking) is keyed by `contentId|episodeNumber`. This lets sources switch without losing data.
2. **Providers are pluggable** — `AnimeDetailsProvider`, `MetadataProvider`, `BackupProvider`, `UpdateSource` are all `List<T>` in Koin. Add one = one class + one Koin line.
3. **Gateway interfaces** — `:core:*` declares interfaces (e.g. `EpisodeFetchGateway`), `:data:*` implements them. Core stays free of data deps.
4. **Reactive everywhere** — `Preference.changes(): Flow<T>`, repository `observeX()`, manager `StateFlow`s. UI always reflects current state.
5. **Dispatchers injected** (`DispatcherProvider`) — all network/DB on `Dispatchers.IO`, testable.
