# Rounds 121–129 — the reliability program (H2, H3, M1…M7)

> **HTML companion (the report + the test guide + the checklists):** `ROUNDS-120-129.html` at the workspace root.
> **Status:** implemented and **CI-verified** (the build is the compiler of record — this app is never built locally). Decisions: **D-748** (the nav route identity), **D-749** (the evidence-driven stall watchdog), **D-758** (the progress writer's lifecycle), **D-759** (the OAuth event + the pending-request IDs), **D-760** (the progress-completion contract), **D-761** (the end-of-episode + resume contract, with D-751/D-752), **D-762** (the tracking contract), **D-763/D-764** (the CloudStream + playback-cache round), **D-765** (the versioned schema). No version bump, no tag, no release (D-738).
> **CI ledger:** 39310982783 (H2) · 39311763928 (H3) · 39312406823 (M1) · 39313160643 **RED** (a build-script placement mistake) · 39313678330 (the fix + M3) · 39315913364 (M4) · 39317837718 (M5) · 39319190527 **RED** (a duplicated file fragment) · 39320193878 **RED** (the wrong SQLDelight migration API) · 39321004727 (M6 + M7) · 39321740520 **RED** (the final unit-test gate caught my own cache-fingerprint bug) · **39322176393 GREEN — the final verification: the opt-in unit-test gate dispatched on the finished tree (every test source set compiles, every test task runs — 94 new test methods included — and the same run built, ABI-gated and uploaded the debug APK).** Every red run is recorded, not hidden.

---

## 1 · H2 (D-748) — the nav route identity

**The two defects.** The nav shell keyed its `SaveableStateProvider` by the key's **class simple name** and compared routes by **full data-class equality**.

- *NAV-1 — the provider-key collision.* Replacing one `AnimeDetailsKey.AniList` with another composes BOTH during the 450 ms transition, and both were keyed `"AniList"` → Compose throws `Key AniList was used multiple times`.
- *NAV-10 — the deep-link equality.* The "already viewing this content" guard compared whole data classes; a deep link builds its key **without** the presentation fields, so the same content compared **unequal** → the shell replaced the page the user was already on.

**The fix.** A key that implements the new `NavKeyIdentity` states which THING it routes to — the content identity, never the presentation fields: `anilist:<animeId>` and `extension:<sourceId>:<animeUrl>`. `NavKeys.stateKeyOf(key)` = the identity **+ the key's own value hash** (the per-instance suffix); `NavKeys.isSameRoute(a, b)` compares the identity only for identity-aware keys and keeps full equality for the rest.

**Tests.** `NavKeysTest` + `AnimeDetailsKeyIdentityTest` (pure JVM).

## 2 · H3 (D-749) — the stall watchdog fires only on evidence

The old watchdog armed on "duration > 0 && !isPlaying && (position == 0 || position >= duration - 2)" and fired 15 s later with *"This server is not responding"* — producing two false errors the user could hit at will (pausing at the start; the end of **every** episode) while being unable to see the real stall (playing with a frozen position).

**The rule now:** playback **has started**, the **user** has not paused, EOF is not reached, nothing else claimed the failure, and the **position did not advance** across the whole 15 s window. `PlaybackWatchdogPolicy` (`:core:player`) is the pure predicate; `PlayerStateHolder` carries `hasEverPlayed` + `isUserPaused`; the screen SAMPLES the evidence (the old effect was keyed on the position, so a 15 s window could never complete while playing).

**Tests.** 13 locks.

## 3 · M1 (D-758) — the progress writer is closed on every path

The channel + writer were closed **only on the success path**, so every failed attempt leaked a suspended `consumeEach` coroutine and an open channel (three failures = three leaks). `ProgressWriter` owns both; its `close()` is idempotent and lives in a `finally` (on `NonCancellable`); the success path still closes it explicitly so the last progress is flushed **before** the completion write.

**Tests.** `ProgressWriterTest` + `RetryPolicyTest` (the classification: 5xx/429/IO retry; 4xx/validation/unknown/cancellation do not; three attempts; capped backoff).

## 4 · M2 (D-759) — the OAuth login is an event; pending requests carry IDs

**NAV-2.** The login result travelled through two `@Volatile` flags that the composition polled every 200 ms **forever**; the guard read `currentKey` — a plain `val` captured at the FIRST composition — so "am I already on the Trackers page?" was answered against the screen the user was on when the app **started** (a later login pushed a duplicate More + Trackers entry). Now: an event on a buffered channel, a **live** back-stack guard, and the token exchange on the activity's `lifecycleScope` (the old `MainScope()` was never cancelled).

**NAV-5.** A `StateFlow` only emits on a CHANGE → two identical deliveries compared equal and the second was **swallowed**; a delivery that arrived while the previous one was handled was **cleared** by the previous handler. Now every offer gets a fresh ID (`PendingRequestMailbox`) and `consume(id)` only clears the request the handler actually handled.

**Tests.** `PendingRequestMailboxTest` — the app module gets its first test source set.

## 5 · M3 (D-760) — the progress-completion contract

1. **The final save was dropped** — it was launched on the screen's `rememberCoroutineScope()` inside `onDispose`, and that scope is cancelled the moment the screen leaves the composition (so the 85 % auto-mark never fired and the episode stayed "not watched" in the library **and** in AniList). `WatchProgressStore.saveFinal(...)` hands the write to a scope that **outlives the screen**.
2. **The EOF save was missing** — the 10 s timer can be ten seconds behind at the end, so the completion was judged against a stale position.
3. **The completion write was not atomic** — a read plus a write with no transaction; the timer, the EOF save and the final save can fire together. Now one transaction.
4. **The sync bridge swallowed cancellations** — `runCatching` catches `CancellationException`, so a cancelled pass kept the loop running. Both sites now rethrow it.

## 6 · M4 (D-761, D-751/D-752) — the end-of-episode + resume contract

- **PB-4:** a completed (or effectively watched) episode starts at **0** and offers its old position for five seconds.
- **PB-2:** at 95 %/outro the next-episode action appears; the auto-advance follows the `autoPlayNext` preference.
- **PB-8:** both nav keys carry `startPositionMs` (milliseconds); the conversion to the player's unit happens in one place.

**Tests.** `PlaybackResumePolicyTest` (13 locks).

## 7 · M5 (D-762) — one `firstWatchedAt` contract + a paced startup sweep

The CS stack wrote `firstWatchedAt` on the first save, the store on the completion — and the store's value always won, so the two writers silently disagreed. ONE contract: the first time the episode was **watched** (`WatchProgressContract`, pure + tested). The startup sweep is paced (400 ms between relays) and uses `collect` instead of `collectLatest` so the tail of the sweep is never starved.

## 8 · M6 (D-763/D-764) — the CloudStream + playback-cache round

- **PB-5:** the engine's event collector is declared **before** the play trigger, so a fast pre-READY failure is no longer emitted into the void.
- **PB-9:** a stall is evidence (buffering past 30 s **and** no progress) and is reported like any other error (`CsStallPolicy`).
- **PB-7:** the URL's identity fingerprint tells a fresh signature from a different file; a changed file drops the cached ranges.
- **PB-6:** the CS online path still bypasses the cache — a decision, not an omission (see D-763).

## 9 · M7 (D-765) — the versioned schema

`2.sqm` (the data-preserving 1 → 2 migration: the dead tables only) + the driver's `onUpgrade` → the generated `Schema.migrate(...)`; the episode-cache writes moved to `Dispatchers.IO`.

## 10 · The device checklist

The full, item-by-item checklists live in `ROUNDS-120-129.html` (tap-to-tick, with a progress bar). The one to run first is the **database migration**: install over the old build, open the app, and confirm the library, the watch progress, the downloads and the settings are all still there.
