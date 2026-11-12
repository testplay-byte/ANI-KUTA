# 04 — PLAYBACK PIPELINE (the two players + the watch tracking)

## 1. The two stacks (and why there are two)

- **The MPV stack** (`feature/watch` + `core/player` + `core/player-mpv-lib`): plays MPV-able
  URIs (http/https/file/content). Full gesture/zoom/cinema OSD. State: PlayerStateHolder.
- **The CS stack** (`feature/cs-watch` + `core/cs-player`): CloudStream links resolve to
  plain progressive/HLS URLs played by a Media3-host engine (CS links NEVER touch MPV
  directly — the isolation contract in CsLinkModels.kt). The SAME watch-progress/session
  stores feed the Profile from both.
- **The torrent seam** (round 143): a TORRENT/MAGNET link never reaches either engine raw —
  `CsWatchViewModel.prepareTorrentAndPlay` transforms it through `core/torrent` into a
  localhost stream URL played as a plain VIDEO link.

## 2. The episode → play chain (the guards that matter)

Details page episode click → the resolver sheet (DetailsViewModel.resolveEpisode →
ResolvedVideosRegistry) or the CS resolve sheet (CsResolveSheet → CloudstreamLinkResolver).
**The generation locks** (D-232-class): resolveGeneration/playGeneration — a play request
only lands while the generations match (the stale-episode replay race). The CS side adds
the torrent branch: TORRENT pick → "Preparing torrent…" → gateway.prepare → deliverPlay.

**The C-1 lesson (round 145)**: the wizard's steps navigate via `steps.indexOf` — the
hardcoded-index pattern is banned after five stale ints looped STORAGE→DETAILS_UI forever.
The details page's episode arrival uses an `item`-wrapped LaunchedEffect (composable calls
cannot sit directly in the LazyListScope).

## 3. The save + tracking gates

- Every save point (the 10s tick, EOF, pre-switch, dispose — NonCancellable) is
  runCatching-wrapped and gates on: duration > 0, genuinely-reached playback, not-ERROR,
  `PlaybackResumePolicy.shouldPersist`.
- The watch-mark = the user's threshold AND ≥ 60s cumulative (`played_time_ms` monotonic).
- The CS player accumulates only forward deltas ≤ 60s (seeks excluded).
- Resume: 15s before the saved position (the verified move).
- Up Next: arms only mid-file (the strict arming: dur > 0 && 0 < pos < dur-1), offers in the
  last 10s, survives dismissal per episode.
- AniSkip: the three-way Found/NotFound/Error contract (only definitive answers latch).

## 4. The file map (the biggest, most-edited files)

| File | Size | Role |
|---|---|---|
| feature/watch/impl/.../WatchScreen.kt | ~4.5k lines | the MPV screen + the episode browser + the controller seam (WatchPlaybackController, split from WatchScreenUi.kt) |
| feature/cs-watch/impl/.../CsWatchPage.kt + CsWatchViewModel.kt | ~1.2k each | the CS screen + the VM (the resolve flow, the torrent seam, the session recorder) |
| feature/anime-details/impl/.../DetailsScreen.kt | ~4k lines | the details page + the flattened episode LazyColumn + the target-episode arrival |
| core/download/.../DefaultDownloadManager.kt | ~1k | the queue/fetch/orchestration |
| app/.../MainActivity.kt | ~3.2k | the nav + the key dispatch + the deep links |

## 5. The downloads

`core/download`: the queue (DownloadQueue) + DownloadService (the FGS, DATA_SYNC) + the
fetchers (HTTP chunked resume, HLS, DASH) + DownloadScanner (the .data.json reconciliation
at startup) + DownloadStorageProvider (SAF). The downloaded keys: exact
`"<mainId>|<episodeKey>"` + the number alias `"<mainId>|#<num>"` (the switch-follower).
The startup scan is runCatching-wrapped + the scope has an exception handler (round 145,
F3 — a revoked SAF tree can no longer crash the app at launch).
