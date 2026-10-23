# Round 120 — The Player Overhaul and the Downloads Reliability Set

> THE ORDER (the user's round-120 mandate, both halves): the player gets resume ("exactly from the place where it was left… if the user had watched 50%, then it will automatically start to play from 50% mark"), watched-episodes-start-fresh ("this is a huge issue"), end-of-episode behavior (a next-episode card with thumbnail/number/title + play, auto-play-next as a setting), intro/outro skipping with saving and handling ("the system knows where the intros are and outros are"), a real player settings hub ("currently we only have one thing"), the false "server not responding" when paused at start, the MPV switching flaws ("no in-flight resolve cancellation"), the identity hazard, and the debt. The downloads side gets robust auto-resume, smart expired-URL handling ("it should properly resolve the episode again… select the appropriate server again… the appropriate resolution again… resume from the place where it was left"), modular + commented, no main-thread runBlocking in service intents, the ASK picker dead end, storage-full handling, the 24h temp purge eating paused progress, notification improvement, and queue reorder/bulk select with a clean UI. OUT OF SCOPE BY ORDER: the details/library pages, the database, the auto-download engine (deferred with the notification system's own improvement). ALL WORK ON `main` (the backup branch is ignored and will be deleted).

## 1. The two shared seams (everything else builds on them)

- **`PlaybackResumePolicy`** (`core/player/…/PlaybackResumePolicy.kt`, pure Kotlin): `plan(stored: WatchProgress?, resumeEnabled)` → `StartAtBeginning | ResumeAtPosition(seconds)`. Watched (incl. the un-marked-flag semantics) → fresh; <5s → fresh; else resume exactly. Owns the shared constants: `MIN_RESUME_SECONDS=5`, `NEXT_EPISODE_TRIGGER_FRACTION=0.95`, `UP_NEXT_COUNTDOWN_SECONDS=10`, `RESUME_PILL_DURATION_MS=5000`. **8 JVM tests** lock it (`PlaybackResumePolicyTest` — the module's first test lock; JUnit wired via `testImplementation(libs.junit)`).
- **`EpisodeKeys`** (`core/watch-progress/…/EpisodeKeys.kt`): the ONE `"$mainId|%05d"` builder. `WatchScreen.buildEpisodeKey` and `CsWatchViewModel.episodeKey()` delegate to it — the format now has exactly one author.

## 2. The player (MPV stack) — D-746/D-747/D-749

- **Resume**: per-episode-key latch map (in-session A→B→A never re-resumes); the policy decides; a dismissible "Resumed · mm:ss [Restart]" pill shows 5s.
- **Up Next**: the shared designsystem **`UpNextCard`** inside the player surface — thumbnail (cover fallback), EP label, title, dismiss, Play with countdown ring. Trigger: 95% of duration OR the outro start, once per key. Auto-play ON → 10s countdown then advance; OFF → button-only; card pref OFF → nothing (the 95% poll AND the EOF hook both gate — see §6, the audit catch).
- **EOF**: `PlayerObserver.onEofReached` (the rising edge of mpv's `eof-reached`) writes pos=duration → the 85% auto-mark fires; a suppressed episode stays un-marked; then the Up Next offer.
- **Skip system**: per-anime timestamps (`pref_skip_timestamps_<mainId>` = "introStart|introEnd|outroStart"), pills in-window (intro `[start−3, end)`, outro `[start, duration−2)`), Off/Button/Auto per side; outro-Auto jumps to the next episode; the **Skip editor sheet** opens from the FF button in both control bars ("Set here" stamps the current position, per-row clear).
- **Settings hub**: Player settings' "Playback behavior" card (item 0) — auto-play next, next-episode card, resume, skip intro/outro segmented rows + four search-index entries (`player.autoplay_next`, `player.next_up_card`, `player.resume`, `player.skip`).
- **Switching hygiene**: `switchJob?.cancel()` + monotonic `switchGeneration` guards at pre-resolve / post-resolve-pre-loadfile / post-surface-wait; `resetPlaybackMetrics()` on every episode change (no stale-position bleed); the exit save runs under `NonCancellable` on a SupervisorJob scope, with the cancelling effect declared first so it disposes last.
- **The honest watchdog**: fires only stuck-at-start (`position==0 && !userPaused && !isSwitching && no error`, 15s). `userPaused` sets on pause/ON_STOP and clears on every fresh loadfile — pausing at 0:00 no longer trips "server not responding" (the round's explicit report), and a genuinely dead server still does.
- **Speed persists** (both stacks): the MPV view read the pref at init but nothing ever wrote it; the speed sheet writes now.

## 3. The player (CS stack) — D-750

`onEpisodeEnded()` honors `autoPlayNext` (OFF parks on the card instead of force-advancing); the same `UpNextCard` renders in both CS display modes (95% trigger off the engine's 100ms ticker + the ENDED event; countdown when auto-play ON); offline engine errors route to `Phase.FAILED` with a reason (the old `url == null → return` dropped them silently); `rearmAutoMark()` re-arms the 85% auto-mark on play acceptance; persisted speed applied after every `engine.start/startOffline*` (the engine resets it to 1x per start) and saved from the speed sheet; `episodeKey()` delegates to `EpisodeKeys`.

## 4. The downloads — D-748/D-749

1. **Auto-resume root cause**: DefaultDownloadManager's init collector unconditionally `DownloadService.stop()`-ed the moment the queue showed "all paused" — which is exactly the network-loss instant, killing the NetworkCallback that fires the resume. Removed; the service owns its lifecycle (keep-alive while `hasNetworkPausedTasks()`, self-stop when truly empty).
2. **No main-thread runBlocking**: intent actions + `onTimeout` run `launchDetached` (`NonCancellable + Dispatchers.IO`) — the writes survive the service's own scope teardown.
3. **Expired URLs**: resolve context persisted for EVERY task; non-localhost 403/410 in either fetcher re-resolves the episode (pinned server + quality, agnostic fallback), truncates, retries once; same-URL re-resolve rethrows.
4. **Storage honesty**: pre-flight free-space check (known total +10% / 200MB floor, `usableSpace<=0` skips) + ENOSPC cause-walk on both catch branches → one honest non-retryable error.
5. **Temp purge**: `cleanupStale` exempts tasks still in the queue — a >24h-paused download keeps its bytes.
6. **Notifications**: error notifications carry a per-task **Retry** action (`ACTION_RETRY`); the tapped notification cancels on retry.
7. **Queue management**: persisted `queueOrder` pref; the SCHEDULER sorts by it (the arranged list IS the download order — not just a visual re-sort); the flat reorder card (up/down buttons, VM-computed moves, prune-on-write); long-press multi-select with bulk pause/resume/remove; both modes self-exit on empty queue.
8. **The ASK picker dead end**: `ShowPicker` parks a request on `DownloadPickerBus` (the AppToast pattern); the app root renders the shared `DownloadVideoPickerSheet`; the pick calls `enqueueSpecific` with the already-resolved identity.

## 5. Deliberately out of scope (the order)

Details/library pages (source linking, tracking), the database (versioning), the auto-download engine + the notification-system rework it depends on, the details-page raw-vs-ordinal episode-key mismatch. The backup branch (`backup/main-pre-revert-2026-10-21`) is IGNORED per the standing order (to be deleted by the user); round 120 is implemented on `main` from its own baseline.

## 6. The verification (two read-only audit agents) + the catches

- **Player audit: FAIL → fixed.** B1: `exitSaveScope.cancel()` — `cancel` is a kotlinx EXTENSION (missing import — would have failed `:feature:watch` in CI); fixed. M1: the settings anchor auto-expand used LazyColumn ITEM indices where SECTION ids were needed (search-landing expanded the wrong section); fixed (ids stay 1..4). M2: the MPV EOF hook offered the card unconditionally, bypassing the card pref; fixed (gated like the 95% poll). Minors: `userPaused` not cleared on the three un-pause paths (watchdog stayed disarmed after pause→switch; fixed ×3), the CS countdown's delegated-property smart-cast (rewritten with a local ticker).
- **Downloads audit: PASS + 5 minors, all fixed**: ENOSPC arriving pre-wrapped as DownloadException bypassed the friendly message (cause-walk added to that branch too); the error notification lingered after a successful Retry (cancelled on tap); the reorder double-tap race (moves now compute in the VM against the live queue); stale ids accumulating in `queueOrder` (prune-on-write); reorder mode left armed when the queue emptied (auto-exit).

## 7. The CI ledger (D-472 — one over budget, disclosed)

Commits `2e6dceaf` (feat(player), 16 files) + `8160d6da` (feat(downloads), 14 files) pushed together → run **39996422543** RED on exactly ONE error (the Skip editor sheet's missing `ExperimentalMaterial3Api` OptIn — a fully-qualified `ModalBottomSheet` call that slipped past the read-only audit) → fix `b484be7b` → run **39998619941** RED on exactly ONE error (`CsWatchViewModel.onEpisodeEnded`'s trailing if/else missing its `return` — expression position is not a block-body return) → fix `2a99ca40` → run **40000778322 GREEN** (full build + ABI check; the `anikuta-apk` artifact, versionCode 10120). THREE Build APK runs — one over the D-472 budget; the overage is two single-error compile misses, both classes now banked in lessons-learned (the doctrine: fix the CLASS, sweep the still-uncompiled modules, before the next push). The docs push is paths-ignored — no run. NOTE: the "unit tests gate every push" comments in build-apk.yml are STALE on main — the job runs `assembleDebug` only; the new PlaybackResumePolicyTest compiles under `test`/`check`, not on this path (disclosed; the policy is audit-verified in the meantime).

## 8. The device checklist

Lives OUTSIDE the repo per the workspace convention: `planning/2026-10-23-round-120-device-test.html` (+ the .md twin) — the grouped, step-by-step test guide for this round's behaviors.
