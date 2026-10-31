# 112 · ROUND 130 — THE SKIP CONTRACT, THE DURABLE DISMISSAL, THE DOWNLOADS OPEN-START, AND THE v1.1.74 DEBUG RELEASE

> 2026-10-31. The user's order: research the skip providers and make the
> intro/outro bands actually work again; make the Up Next dismissal durable;
> handle the deferred downloads re-test properly; fix the `.data.json`
> timestamp rolling; verify with sub-agents; then cut the **v1.1.74 debug
> release** so the in-app updater carries everything. (The WatchScreen size
> question was answered as a PLAN — see §6; no code.) Decisions
> D-803..D-806.

## 1. The AniSkip contract (D-803) — why the bands went silent, and the fix

The research (a live API probe + the provider sweep) confirmed: **AniSkip v2
is live, keyless, and unchanged** (120 req/s, CORS open); **MALSync is NOT a
skip provider** (it maps ids, it has no timing DB); the alternatives
(Anime-Skip, TheIntroDB, IntroDb) are account-walled or TMDb/IMDb-keyed —
**AniSkip stays the primary, and no second integration is worth it**; the
fallback that matters is manual stamps + heuristics (already in the app).
The real bug was OURS, three layers deep:

1. **The poisoned negative latch (the big one).** The client collapsed
   network errors, parse failures, and genuine "no data" into the same
   `null`, and the caller latched ALL of them
   (`pref_episode_skip_asked_v2_*`) — one flaky fetch permanently silenced
   an episode's skip data across restarts. THE FIX: a three-way contract —
   `Found` / `NotFound` (the service answered "no data": definitive, latches)
   / `Error` (network, parse, the AniList→MAL hop failing transiently,
   no-ids-yet: never latches — the next episode open retries). The latch key
   re-keyed to **v3**: every poisoned latch expires on update.
2. **The dropped types.** The query asked only `op&ed`; AniSkip also carries
   `mixed-op`/`mixed-ed` (late-series overlapped endings are common). Both
   are now requested and parsed (`mixed-ed` → outro).
3. **The length-mismatch 404.** AniSkip answers the SAME 404 for "no
   submission" AND "wrong episodeLength" (verified live). A stream that cuts
   differently from the listing poisoned the episode. A definitive miss with
   a length sent now retries ONCE without the length (the parameter is
   optional).

Also fixed: `episodeSkip` clears at effect entry (a switched-to episode no
longer shows the previous episode's bands); the "transfers from the details
page" model — the transfer is implicit and correct (the player re-reads the
DB by `mainId`); what was missing was ids on extension-only rows (the noted
AniList title-search fallback — see §7). The details-side id plumbing is
intact; the `mal=` URL fallback still works.

## 2. The durable Up Next dismissal (D-804)

The dismissal was a composition-local map — process death lost it. Now:
`PlayerPreferences` persists an ordered, capped (256) list of dismissed
episode keys; the offer guard consults BOTH stores; **dismiss** persists;
**seek back out of the end zone** clears both (the re-arm is durable too);
and the fresh-replay semantics are preserved exactly: a genuine replay from
zero (the resume decision lands on StartAtBeginning) clears the durable
dismissal — a new playback attempt is a fresh chance — while a process-death
RESUME (ResumeAtPosition) keeps it: dismiss, kill the app, reopen at the
saved position → the card stays dismissed.

## 3. The downloads open-start + the queue hygiene (D-805's companions)

The re-test audit (a very thorough sub-agent pass) found the round-122 core
loop fully implemented and consistent — with ONE high-value gap: **nothing
at app open started the download service**, and the self-heal sweep (the
NetworkCallback + the one-shot reconnect evaluation) lives inside it. After
a force-stop or reboot while offline, WAITING tasks waited forever even
after reconnecting. Fix: `DefaultDownloadManager.ensureServiceForPendingWork()`
called once at app open — if the persisted queue holds queued / stale-
downloading / waiting / network-class-error tasks (PAUSED deliberately
excluded — the user's pause intent wins), the service starts and its sweep
resumes exactly what the network allows. Plus: the per-attempt progress
writer no longer leaks (tracked + disposed on every exit — retry, pause,
cancel), and the DOWNLOADING transition writes the fresh snapshot instead of
the launch-time capture.

## 4. The honest `.data.json` stamps (D-806)

`ContentDataJson` gains `lastScanAt` (nullable, `ignoreUnknownKeys`-safe both
directions). Scan writes (the reconcile + the episodes rewrite) stamp
`lastScanAt` and **never** `updatedAt` — the duplicate-folder arbitration
reads `updatedAt` as "download recency" and that signal is now honest (a
scanned folder no longer automatically wins future comparisons).
Download-flow writes keep rolling `updatedAt`. An identical scan pass writes
no file at all.

## 5. Verification + the release

- Adversarial sub-agent verification found **5 defects** (2 in the AniSkip
  path — a transient AniList→MAL hop still latched; the mismatch-retry's own
  failures mislabeled as definitive) — **all fixed pre-push**; the full
  suite (219 tests) + the full app compile green.
- Mainline CI: Build APK run **41196747242 GREEN** (head `d00c6726`).
- **The v1.1.74 debug release** (Routine A): `release/1.1.74` cut from the
  green head, ONE bump commit (`46424a67`-style: 10120/1.1.20 →
  **10174/1.1.74**; 10174 > 10173 — the in-app updater installs in place),
  the annotated tag `v1.1.74` → `release-apk.yml` (run
  **41197352617 GREEN first-try**) builds the debug arm64-v8a APK and creates the
  testplay-byte release (`ani-kuta-v1.1.74-debug-arm64-v8a.apk` +
  SHA256SUMS.txt, stable + latest). CI this cycle: 2 runs, inside D-472.
- **The updater effect**: v1.1.73 installs get the v1.1.74 offer carrying
  rounds 126–130: the cumulative watch-mark gate, the database hardening
  (B1–B7), the About Community section, both Profile fixes, this round's
  four workstreams.

## 6. The WatchScreen size — the requested overview (NO code)

**What it is:** `WatchScreen.kt` (~2,100 lines) is five screens in one
composable: (1) the MPV init/observer wiring, (2) the episode-switch +
resolve pipeline, (3) the watch-progress/played-time saves, (4) the Up Next
+ AniSkip state machines, (5) the entire Compose UI tree (fullscreen +
minimized, gestures, drawers, dialogs). It is large because it is the
player.

**The approach I would take** (when you order it):
- **Phase 1 — stateless extraction (low risk):** move the pure composables
  (dialogs, drawers, the Up Next card, the subtitle panels) into
  `feature/watch/impl/ui/` files. Pure moves, zero behavior change,
  mechanically verifiable.
- **Phase 2 — state-holder extraction (medium):** move the Up Next
  bookkeeping (offered/dismissed/countdown) and the skip state machines into
  a `WatchPlaybackController` class owned by the screen — they are already
  coherent units with clear inputs.
- **Phase 3 — the switch pipeline (highest risk):** the episode-switch +
  resolve flow (generation latches, superseded-switch aborts) becomes a
  controller with its own tests. This is the part where regressions would
  bite.
- **Cons:** big-diff risk on the app's most device-tested screen; merge
  conflicts with any in-flight work; the behavior locks are logcat/manual —
  no UI tests exist, so every phase needs a device round; it delays features
  for zero user-visible gain.
- **My recommendation:** do Phase 1 opportunistically whenever we touch the
  player anyway; order Phases 2–3 only if the file starts resisting edits.

## 7. Open items

- **The AniList title-search fallback** (what I meant): extension-only
  content with neither a MAL id in the DB, nor a `mal=` in the URL, nor an
  AniList link cannot reach AniSkip at all — the fallback would search
  AniList by title to mint the id. Now documented; not built (the latch fix
  covers the far more common cases).
- The manual skip-stamp editor was removed in round 124 (stored stamps still
  work as the fallback; no UI to set them). If you want per-series manual
  overrides back, say so.
- The downloads device re-test: run the checklist on v1.1.74 (in the release
  notes/your read-out) — the audit predicts everything passes EXCEPT the
  force-stop variant, which this round's fix addresses.
