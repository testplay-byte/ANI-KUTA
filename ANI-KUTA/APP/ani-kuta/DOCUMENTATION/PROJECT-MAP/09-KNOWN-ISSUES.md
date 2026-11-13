# 09 — KNOWN ISSUES (the live register — audited 2026-11-13, round 145)

> Every finding is evidence-backed (file:line verified). Severity: CRITICAL = breaks a
> primary flow for real users; HIGH = crashes/data-loss class on realistic paths; MEDIUM =
> quality/consistency/jank; LOW = polish/hygiene. **FIXED** entries carry the round.

## CRITICAL

- **[FIXED r145] C-1 — The wizard's five stale step indices looped forever.** The 12-step
  enum (D-864) left STORAGE/RESTORE/NOTIFICATIONS/BATTERY/OVERLAY navigating to the
  PRE-rework magic ints (STORAGE → 3 = DETAILS_UI): a fresh install cycled the personalize
  block forever, FINISH was unreachable, `onboardingCompleted` never set. SHIPPED in
  v1.1.86. Fixed in round 145 (ce5b3023): all navigation via `steps.indexOf` + the FINISH
  position + the OnboardingStepTest chain-lock test. **A v1.1.87 hotfix release is REQUIRED
  for fresh installs.**

## HIGH

- **[FIXED r145] H-1 — History taps navigated TWICE (and the second push carried the
  poisonous 0L).** The D-863 wiring prepended `openDetailsByMainId` but left the legacy
  lookup body (which used `details.sourceId ?: 0L` — the value that mints duplicate
  mainIds). Fixed: the legacy body removed; the unified path is the only path.
- **[FIXED r145] H-2 — The torrent outlived its screen.** `forgetActiveTorrent` launched
  into `viewModelScope` from `onCleared()` — the scope is already cancelled there, so the
  forget was a no-op and the embedded server kept the torrent running after the watch
  screen closed. Fixed: the forget runs on the app-wide IO scope.
- **[FIXED r145] F2 — `hasColumn` never inspected the table's FIRST column** (the
  moveToFirst+moveToNext skip). Consequences: the `anime_update_state.main_id` guard could
  never match → the `learned_offset_ms` ALTER never ran (a legacy-shape write crash class);
  five table-missing probes were always-true → onCreate ran ~6× per launch. Fixed
  (do/while). ⚠ Devices whose anime_update_state predates the column AND that then hit the
  F1-class upgrade still need the migration to add it — the pre-flight covers the ALTER
  order.
- **[FIXED r145] F1 — The migration chain v1→4 crashed on genuinely-old v1 installs.**
  3.sqm SELECTs columns the legacy onOpen ALTERs used to add — but onUpgrade runs BEFORE
  onOpen, so a v1-era install failed the chain with "no such column" = a launch crash loop.
  Fixed: the onUpgrade PRE-FLIGHT runs migrateSchemaIfNeeded before the .sqm chain. The
  remaining documented cohort: a device with NO watch_progress table at all (v0.2.16-and-
  earlier) still needs a one-time uninstall.
- **[FIXED r145] F3 — The startup download scan could crash the app at launch.** The scan
  was the one unguarded body in a handler-less startup scope (a revoked SAF tree or a
  SQLiteException escaped to a process crash). Fixed: the scan is runCatching-wrapped + the
  scope gained a CoroutineExceptionHandler backstop.
- **OPEN — H-3 — The torrent engine binds hardcoded port 8090 and `echoProbe` accepts ANY
  non-empty answer** — a foreign TorrServer on 8090 would be silently adopted (its cache,
  its lifecycle). Also the AnikutaApp `cleanupStaleCache()` runs recursive delete on the
  MAIN thread (ANR risk on a large cache). Options: an identity check on the echo response
  (a magic header/endpoint), a port probe refinement, the cleanup moved to IO. File:
  `core/torrent/TorrServerLoader.kt:39,119,143-152`; `AnikutaApp.kt:125`.
- **OPEN — H-4 — The backup archive materializes in RAM twice** (the JSON dump + the zip
  bytes + the covers + up to 128MB of offline images). Large libraries can OOM on low-RAM
  devices. A streaming-zip rewrite is the fix. File: `core/backup/BackupManager.kt:475-502,
  277,1097-1160`.

## MEDIUM

- **M-1 (open)** — The backstack + the last tab are NOT persisted across process death
  (`remember`, not rememberSaveable) — deep navigation is lost. MainActivity.kt:691-702.
- **M-2 (open)** — Synchronous SQLDelight reads on the MAIN thread in the navigation
  lambdas (`getMainEntryByMainId`/`getContentDetails` — non-suspend, executeAsOneOrNull)
  — jank/ANR risk as the DB grows. ContentRepository.kt:81-100.
- **M-3 (FIXED, round 146)** — Episode notifications: SUB and DUB of the same episode share ONE id —
  the second post replaces the first (the exact swallowed-banner class D-503 claimed
  fixed). NotificationManager.kt:216-218.
- **M-4 (FIXED, round 146)** — Fractional episode numbers (12.5) re-detect as NEW every check run (the
  `.toLong()` truncation in the max-episode tracking) — the "new" badge never ages out.
  UpdateEngine.kt:497-607.
- **M-5 (open)** — Restore is merge-only with NO rollback: a mid-restore failure after the
  DB COMMIT reports "Restore failed" while the DB half applied. BackupManager.kt:897-924.
- **M-6 (FIXED, round 147)** — The backup carried the ad-system prefs (a donor's `alwaysSponsor=true`
  re-enables the debug sponsor popup on the recipient; the grace timestamps cross over
  too). BackupModels.kt:110-117 (the exclusion list).
- **M-7 (FIXED, round 146)** — The wizard state was `remember` — a rotation reset to WELCOME (stepIndex + personalizeEntered are saveable).
  OnboardingScreen.kt:161-174.
- **M-8 (open)** — The AniList OAuth poller captures a STALE `currentKey` once and polls
  forever (200ms), pushing TrackersKey regardless of the actual location.
  MainActivity.kt:778-796.
- **M-9 (open)** — The check-engine `onFailed` cards are not trigger-gated: a failing
  periodic run can buzz repeatedly (D-426's spirit covers only empty runs).
  UpdateEngine.kt:161 + UpdateProgressNotifierImpl.kt:448-466.
- **M-10 (open)** — F4: the FK-CASCADE web to main_entry is dormant but half-inconsistent
  with D-862 (the tracking-intent/track/notification/schedule/rating tables still cascade);
  a future deleteMainEntry caller would wipe them while watch history survives. Either the
  v4 treatment or an explicit decision.
- **M-11 (open)** — F5: the resolver's create path is two non-transactional writes +
  check-then-insert (a process death or a race mints duplicate main_entry rows). The
  scanner got INSERT OR IGNORE (D-791); the resolver did not. ContentResolver.kt:146-165,
  253-277.
- **M-12 (open)** — F6: `replaceDataSourceAxis`/`replaceExtensionAxis` are DEAD — a source
  switch uses the COALESCE merge, which can mix two sources' metadata (the content.sq
  comment says replace is the ONLY correct tool for a switch). D-792 is half-wired.
- **M-13 (open)** — F8: `WatchProgressStore.save` is a non-transactional read-modify-write
  shared by 4+ concurrent writers — the flag fields (watch_count/completed_at/
  first_watched_at) are last-write-wins (played_time_ms is safe — monotonic).
  SqlDelightWatchProgressStore.kt:47-120.

## LOW (the polish/hygiene list)

- L-1: NotificationPosterKey is the only NavKey without @Serializable. L-2: the conditional
  registerForActivityResult in onCreate. L-3: PendingNavigation last-wins on double taps.
  L-4: the Downloads-page degenerate-key fallback (?: 0L + the silent null details) — the
  same class H-1 fixed. L-5: fixed with C-1 (the FINISH position). L-6: the OAuth exchange
  on MainScope (unmanaged). L-7: cleartext-permitted + user-CA trust (deliberate for
  extensions — document the MITM trade-off). L-8: the torrent add's 5s timeout misreads
  slow swarms as dead. L-9: a dead try/catch in UpdateCheckWorker. L-10: echoProbe leaks
  the connection on the exception path. L-11: the interop restore counts rows that failed
  internally. L-12: F9 — toggleLibrary does main-thread unguarded DB writes. L-13: F10 —
  setUserMarkedWatched leaves completed_at/first_watched_at NULL (the AniList startedAt
  seeding misses them). L-14: F11 — the unmark doesn't clear the session ledger rows. L-15:
  F12 — markAllWatched is an N×2 loop without a transaction. L-16: F13 — the backup
  category id-collision mis-attaches items. L-17: F14 — updateCoverAccent unguarded.
  L-18: the dead-code family (clearByMainId/deleteMainEntry/deleteContentDetails/
  markCompleted/replaceAxis writers) — unwired, with stale stories attached; either wire
  or delete. L-19: the OnboardingStepTest + the 6 SchemaMigrationTests are pinned but
  UNRUN since round 144 (the no-local-Gradle boundary).

## HIGH (the playback/downloads pipeline — audited round 145, wave 3)

- **OPEN — P-H1 — CS offline DASH failures are silently dropped: dead player, no error UI.**
  The engine emits PlaybackError with a NON-null url for the offline sidecar/manifest
  failures, but onEngineError only handles offline errors when url == null (currentLink is
  null on offline loads → the mismatch check drops the event). A corrupt .dashmeta = a black
  screen with live-looking dead controls. CsPlayerEngine.kt:552-560,600-610 +
  CsWatchViewModel.kt:909-925.
- **OPEN — P-H2 — The initial fd:// loadfile rides an untracked postDelayed(500)** — leaving
  a downloaded episode within ~500ms of open posts a loadfile at a DESTROYED mpv handle (a
  native-crash class; SIGSEGV is not catchable). The switch path fixed this exact hazard
  (the delay moved inside the cancellable job); the initial-open path never got the same
  migration. WatchScreen.kt:1396-1409 vs 1441-1517 + the 1976-1982 precedent.

## MEDIUM (the playback pipeline)

- **P-M1 — The MPV resume latch survives episode re-visits**: switch EP1→EP5→EP1 replays
  EP1 from 0:00 AND zeroes persistedPlayedBaseMs — the cumulative 60s watch-mark undercounts
  on re-visits (the D-789 contract fails for this case). WatchScreen.kt:718,882-888,1922.
- **P-M2 — CS Up Next auto-advance cuts the ending**: the countdown starts at the 95% latch
  with no near-end hold — auto-play switches ~10s later at ≈95.7%. The MPV stack received
  the last-10s hold (round 123); the CS stack never got the parity fix.
  CsWatchScreen.kt:465-480 vs WatchScreen.kt:2211-2226.
- **P-M3 — Dead Audio + Rotate buttons in the MPV fullscreen controls** (the params default
  to {} and the call site never passes them). FullscreenControls.kt:349,480 + WatchScreenUi.
  kt:192-217.
- **P-M4 — AniSkip AUTO latch never resets per session**: EP1 auto-skips once; switching
  back to EP1 in the same session never auto-skips again (BUTTON mode unaffected).
  WatchPlaybackController.kt:181,231-245.
- **P-M5 — No CS stuck-playback watchdog**: BUFFERING forever on a stalling stream (no
  error raised, no retry) — the MPV stack has the 30s/15s watchdogs; the CS stack has
  neither. CsPlayerControls.kt:307.

## LOW (the playback pipeline — the quick list)

- P-L1: torrent-only resolution leaves the CS screen on the RESOLVING overlay forever (no
  prompt; the user must open the sheet manually). CsWatchViewModel.kt:726-753,678-688.
- P-L2: dead download-badge taps when the queue row is absent (the badge shows, every tap
  is a silent no-op). WatchScreen.kt:3496-3515.
- P-L3: fractional episode numbers break the downloaded-subtitle/rating/meta lookups
  (12.5 → 12). WatchScreen.kt:1540,3152,2680.
- P-L4: the CS subtitles sheet stays open after picking Off (every other pick closes it).
  CsWatchScreen.kt:779-784.
- P-L5: the seekbar pointerInput(maxRange) restarts mid-scrub when the duration arrives.
  FullscreenControls.kt:545,553 + CsPlayerControls.kt:913,921.
- P-L6: the download-concurrency swap transiently over-concurrent (old permits stay held).
  DownloadQueue.kt:323-330.
- P-L7: duplicate-key LazyColumn crash possible in the siblings that never got the F5
  index-suffix hardening (QualityServerAccordion by name; the episode rows by url —
  conditional on a source emitting duplicates). PlayerSheets.kt:882, WatchScreen.kt:3646.
- P-L8: PlayerStateHolder leaks an unused scope + reset() misses _isSwitchingEpisode;
  a dead isLocalhost branch.

## The docs-staleness finding (this audit's meta-finding)

- The AGENT-CONTEXT knowledge files carried THREE eras of stale facts (master.md said
  "v1.1.72 debug / v1.1.5 professional — round 117"; the counts said 57 modules / 25
  tables / 634 files — reality: 59 / 30 / 678). REFRESHED this round (see the git history)
  + the new PROJECT-MAP folder is the maintained entry point. The lesson stands: re-verify
  every count at every phase boundary; never copy a number forward.
