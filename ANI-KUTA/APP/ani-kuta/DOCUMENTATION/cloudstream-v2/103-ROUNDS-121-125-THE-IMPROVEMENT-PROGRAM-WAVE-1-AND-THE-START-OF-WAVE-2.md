# Rounds 121–125 — The improvement program, Wave 1 and the start of Wave 2

> **Status:** IMPLEMENTED + CI GREEN. Decisions: **D-748** (the nav route identity, H2), **D-749** (the evidence-driven stall watchdog, H3), **D-758** (the progress writer's lifecycle, M1), **D-759** (the OAuth event + the pending-request IDs, M2), **D-760** (the progress-completion contract, M3). Plan: `PLAN.md` / `PLAN.html` (workspace root). Backlog: `IMPROVEMENTS.md` / `IMPROVEMENTS.html`.
> **What these rounds are:** the approved 65-item improvement backlog, executed in the plan's order. Each round fixes one defect class, states its contract in a unit-tested policy where a pure seam exists, and is verified by a CI run. No user-visible policy changed without a decision; no version bump, tag, or release (D-738).
> **CI ledger (every run, in order):** 39310982783 GREEN (H2) · 39311763928 GREEN (H3) · 39312406823 GREEN (M1) · **39313160643 RED** (M2 — a build-script placement mistake, see §5) · 39313678330 GREEN (M2's fix + M3) · …see `progress.md` for the runs after this record.

---

## 1. H2 (D-748) — the nav route identity

**The two defects.** The nav shell keyed its `SaveableStateProvider` by the key's **class simple name** and compared routes by **full data-class equality**.

- *NAV-1 — the provider-key collision.* Replacing one `AnimeDetailsKey.AniList` with another composes BOTH during the 450 ms transition, and both were keyed `"AniList"` → Compose throws `Key AniList was used multiple times`.
- *NAV-10 — the deep-link equality.* The "already viewing this content" guard compared whole data classes. A deep link builds its key **without** the presentation fields (cover URL, title, transition key), so the same content compared **unequal** → the shell replaced the page the user was already on, reloading the content and putting two same-class providers on screen at once.

**The fix.** A key that implements the new `NavKeyIdentity` states which THING it routes to — the content identity, never the presentation fields:

| Key | Identity |
|-----|----------|
| `AnimeDetailsKey.AniList` | `anilist:<animeId>` |
| `AnimeDetailsKey.Extension` | `extension:<sourceId>:<animeUrl>` |

- `NavKeys.stateKeyOf(key)` = the identity **+ the key's own value hash** — the per-instance suffix the plan asks for. Two entries of the same class alive at once get different provider keys (the crash cannot happen); the same entry keeps its key, so its `rememberSaveable` state survives.
- `NavKeys.isSameRoute(a, b)` compares the **identity only** for identity-aware keys; every other key keeps full equality, so nothing else changes.
- The contract is opt-in: a key joins by implementing one interface; the fallback is today's behavior.

**Tests.** `NavKeysTest` (the helper contract) + `AnimeDetailsKeyIdentityTest` (the details identity per key type) — both pure JVM.

## 2. H3 (D-749) — the stall watchdog fires only on evidence

**The defect (PB-1).** The old fatal-error watchdog armed on `duration > 0 && !isPlaying && (position == 0 || position >= duration - 2)` and fired 15 s later with *"This server is not responding"*. It did not know **why** playback had stopped, so it produced two false errors the user could hit at will:

- **pausing at the start** (position 0) → the error on a perfectly healthy video;
- **reaching the end of an episode** (keep-open parks MPV at the end) → the same error at the end of **every** episode.

It also could not see the classic stall at all: it required `!isPlaying`, while the real server-death signature is playback reporting *"playing"* with a **frozen position**.

**The rule now.** The watchdog fires only on evidence: playback **has started**, the **user** has not paused, EOF is not reached, no error or switch claimed the failure, and the **position did not advance** across the whole 15 s window.

- `PlaybackWatchdogPolicy` (`:core:player`) — a pure, unit-tested predicate over an `Evidence` snapshot.
- `PlayerStateHolder` carries the two facts it needs: `hasEverPlayed` and `isUserPaused` (the play/pause button and the app-backgrounding pause both mark the pause as the user's).
- The screen **samples** the evidence instead of keying the effect on the position (the old `LaunchedEffect(isPlaying, duration, position)` restarted on every position tick — a 15 s window could never complete while the video was playing).

**Tests.** 13 locks: the two false positives never fire; the real stall does; a pause/EOF/error/switch *during* the window cancels it.

## 3. M1 (D-758) — the progress writer is closed on every path

**The defect (DL-2).** The queue created the progress channel + its single writer coroutine inline and closed them **only on the success path**. Every **failed** attempt therefore leaked a suspended `consumeEach` coroutine and an open channel — and the retry loop created a new pair per attempt (three failures = three writers suspended forever on channels nobody would send to again).

**The fix.** `ProgressWriter` owns the channel and the writer coroutine; its `close()` is idempotent and safe inside a `finally` (it closes the channel — the consumer drains what is pending and ends — and joins the coroutine). The queue creates one per attempt and closes it in a `finally` on `NonCancellable` (so the final flush completes even when the attempt was cancelled); the success path still closes it explicitly, so the last progress is flushed **before** the completion write. CONFLATED + throttled as before — only the lifecycle changed.

**Tests.** `ProgressWriterTest` (the no-leak locks) + `RetryPolicyTest` (the classification: 5xx/429/IOException retry, 4xx/validation/unknown/cancellation do not, three attempts, capped exponential backoff).

## 4. M2 (D-759) — the OAuth login is an event; pending requests carry IDs

**NAV-2 — the polling watcher.** The login result travelled through two `@Volatile` flags that the composition polled **every 200 ms, forever**. The poll's guard (`currentKey !is TrackersKey`) read `currentKey` — a plain `val` captured at the **first** composition — so "am I already on the Trackers page?" was answered against the screen the user was on when the app **started**: a login that happened minutes later pushed a **duplicate** More + Trackers entry. Now: the result is an **event** on a buffered channel (delivered exactly once, survives until the composition collects it, collection bound to the composition), the guard reads the **live** back stack, and the token exchange moved from a never-cancelled `MainScope()` to the Activity's `lifecycleScope`.

**NAV-5 — two races in the one-slot intake.** The intake was a bare `MutableStateFlow<Request?>`:

- a StateFlow only emits on a **change** → two identical deliveries (the same notification tapped twice) compared equal and the second one **never arrived** (the tap was swallowed);
- a delivery that arrived **while the previous one was being handled** was cleared by the previous handler's `consume()`.

Now every offer gets a fresh, monotonically increasing ID (`PendingRequestMailbox`): identical payloads are still two requests, and `consume(id)` only clears the request the handler actually handled.

**Tests.** `PendingRequestMailboxTest` (the identity locks) — the app module gets its first test source set.

## 5. M3 (D-760) — the progress-completion contract

**The theme:** the final write of a viewing session was neither atomic nor guaranteed to happen.

1. **The final save was dropped.** The player saved the last progress by launching on the **screen's** `rememberCoroutineScope()` inside `onDispose` — and that scope is cancelled the moment the screen leaves the composition. The 85 % auto-mark then never fired and the episode stayed "not watched" in the library **and** in AniList ("sometimes if I completely watched an episode it would not update"). **Fix:** `WatchProgressStore.saveFinal(...)` hands the write to a scope that **outlives the screen** (the store is an application-scoped singleton).
2. **The EOF save was missing.** The 10-second timer can be ten seconds behind when an episode ends, so the completion threshold was judged against a stale position. The screen now saves the **final** position the moment the video reaches the end.
3. **The completion write was not atomic.** `save()` read the existing row and then upserted — two statements, no transaction. The timer, the EOF save and the final save can fire at the same instant; interleaved, a completion could be overwritten by a read that had not yet seen it (the auto-mark never fires) or `watch_count` could be incremented twice. The read-modify-write is now **one transaction** — atomic and idempotent.
4. **The sync bridge swallowed cancellations.** `TrackingWatchSyncBridge` wrapped its work in `runCatching`, which catches `CancellationException` — so a cancelled pass kept the loop running and the bridge could not be stopped cleanly. Both sites now rethrow the cancellation and swallow only real failures.

## 6. The one red run (and what it taught)

Run **39313160643** (M2) failed in the **configuration phase**, not in the code: the app module's `testImplementation(libs.junit)` had been placed by an edit that anchored on the wrong closing brace, so it landed **inside** the `checkDependencyAlignment` task block — Gradle could not resolve the accessor ("receiver type mismatch"). One line, moved to the `dependencies` block (commit `152b6bd9`), and the next run was green. It is recorded here because the ledger is meant to be honest: the failure was a build-script placement mistake, found by the CI log, and it cost one run.

## 7. The device checklist (the user's device — CI cannot prove behavior)

**H2 (navigation identity)**
- [ ] Open a details page, then open a **different** details page from it — the transition is smooth, no crash, and the second page shows its own content.
- [ ] While on a details page, tap the same notification/link for that content again — the page does **not** reload and no duplicate screen appears.
- [ ] Scroll a long list, leave it, come back — the scroll position is preserved.

**H3 (stall watchdog)**
- [ ] Pause the video at the start and wait 30 s — **no** "server is not responding" error.
- [ ] Let an episode finish — **no** error at the end.
- [ ] Force a real stall (throttle the network to a crawl) — the error appears **once**, and retry works.

**M1/M3 (downloads + progress)**
- [ ] Download a file, let it fail (e.g. kill the network mid-download) and retry — no runaway resource use, and the progress bar is sane.
- [ ] Watch an episode to the end — the episode shows as **watched** in the library and in AniList, without leaving the app.
- [ ] Back out mid-playback and reopen the episode — the position is where you left it.

## 8. Next

**M4** (PB-2, PB-4, PB-8): the end-of-episode next-episode action at 95 %/outro, the auto-advance preference, the completed-episode resume contract (a completed episode starts at 0 with a short "Resume from mm:ss" offer), and the millisecond unit contract.
