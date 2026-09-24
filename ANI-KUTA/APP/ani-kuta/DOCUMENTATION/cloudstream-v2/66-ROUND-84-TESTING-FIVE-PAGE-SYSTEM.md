# 66 — ROUND 84: THE FIVE-PAGE TESTING SYSTEM + THE HARD-ISOLATION HANG FIX, the delete exit choreography, the true pills, the button-feel menus, and the taller/3D/user-driven sheet

> Round 84 (D-580..D-583). The v1.1.40 device round. The user's headline
> verdict: the testing UI was "cluttered", the batch experience "got stuck on
> some areas" (with a dead Stop button), and the whole system needed a
> multi-page redesign — plus a second pass on the round-83 UI surfaces
> (delete animation, header pills, filter menus + search field, the link
> source sheet's height/highlight/3D/scroll/search-bar).
>
> Code: `testing/` (the system reworked — 9 new files, 4 deleted, 4 reworked),
> `ExtensionsSettingsKey.kt` (4 new keys), `MainActivity.kt` (the pushTesting
> guard + 5 branches), `ExtensionListChrome.kt` (the shared delete
> choreography), `ExtensionsSettingsScreen.kt` (pills + menus + search),
> `CloudstreamExtensionsSection.kt` (the CS delete choreography),
> `ManualSearchSheet.kt` (the sheet's round-84 pass).

---

## 1. D-583 — THE HANG: why the app went unresponsive on SOME Aniyomi extensions and Stop did nothing

### 1.1 The anatomy

The round-84 report: "it gets unresponsive on some of the extensions when
they reach the search page testing, and after that if I click the stop
button, it does not work" — Aniyomi only; CloudStream never hung.

Every safety layer the round-82/83 engine had is **cooperative**:

| Layer | Mechanism | Why it fails on a blocking extension |
|---|---|---|
| Inner attempt timeout | `withTimeout(12s)` | `withTimeout` can only fire at a suspension point — a body that never suspends never reaches one |
| Kind timeout | `withTimeout(kind.timeoutMs)` at the engine | same |
| Stop | `batchJob?.cancel()` | `Job.cancel()` is a flag; a thread wedged in non-suspending code never observes it |
| Batch loop | sequential `runTestsFor` awaits | no engine-level recovery — one wedged target stalls the whole queue |

Real extensions DO contain fully-blocking overrides (synchronous
`client.newCall(...).execute()` + parse inside a `suspend fun`, JS engines,
`readTimeout(0)` clients). When one wedged, `isBatchRunning` stayed `true`
forever — and the old screen gated EVERY control on that flag, Stop
included. The "unresponsive screen" was not an ANR; it was a screen whose
every affordance was disabled by a never-clearing flag.

### 1.2 The fix — `TestIsolation.runKind` (the hard isolation)

The invariant: **the awaiter's return must never depend on the tested code's
cooperation.**

1. Each kind's body runs on a DEDICATED single-thread daemon dispatcher
   (`Anikuta-ExtTest-<KIND>`), created per kind-run (never a shared pool — a
   wedged thread must not queue up the next kind).
2. The caller awaits the body's `Deferred.await()` in 250 ms poll slices
   under its OWN wall-clock deadline. `await()` is a cancellable suspension
   — the caller's deadline ALWAYS fires, no matter what the body does.
3. On timeout / skip / stop: the body is cancelled (it unwinds at its next
   suspension) and the executor is `shutdownNow()`-ed — the interrupt makes
   synchronous OkHttp calls abort.
4. The engine's own `withTimeout`/TCE handling is DELETED — one clock, one
   owner. A timeout is now a VERDICT: `FAILED "Timed out after 12 s"` with
   the detail "the extension's code never returned — it was abandoned
   mid-call".
5. `context.ioDispatcher` — the engine publishes each kind's dedicated
   dispatcher into the test context, and all seven test files route their
   blocking work through it (`withContext(context.ioDispatcher)`), so the
   interrupt lands on the ACTUAL blocked call instead of abandoning a zombie
   on the shared IO pool.
6. The test client gained `callTimeout(30s)` — a drip-feed server could
   otherwise reset the read timeout per read forever (STREAM_PLAY).
7. `SearchTest` hygiene: `CancellationException` is rethrown (a Stop can no
   longer be eaten into "every phrase errored"), the attempt-timeout TCE is
   disambiguated from an outer cancellation by `currentCoroutineContext()
   .isActive`, and the SEARCH budget rose 60s → **80s** (60s was exactly
   5 × 12s with zero headroom).

Thread hygiene: one executor per kind-run + `deferred.cancel()` +
`shutdownNow()` in `finally` on EVERY exit path → steady-state extra threads
= 0. The only residue is a daemon thread stuck in an interrupt-immune call
(classic JVM DNS), which self-releases at the OS resolver timeout.

### 1.3 Skip — the stuck-test escape hatch

The report asked for it explicitly: "options like skipping a test for a
specific one if I feel like it is stuck". The run page carries **Skip
target** while a run is live; `controller.skipCurrent()` sets an atomic the
isolation loop polls at slice granularity — the engine marks the current AND
every remaining kind of that target `SKIPPED "Skipped by user"`, the run
advances to the next target, and `TargetRunState.abortedByUser = true`
prevents the cut-short verdict from ever counting as healthy.

---

## 2. D-583 — THE FIVE-PAGE SYSTEM (the multi-page redesign)

The report: "the whole extension testing system in a multi-page format…
maybe five pages… proper modular fashion… learn from the extensions page
itself". The system:

| # | Page | Key | What it carries |
|---|---|---|---|
| 1 | **Home** | `ExtensionTestingKey` (existing) | hero proportion bar + tested/total, the live-run banner (View / Stop), the TWO ecosystem cards (the per-system entry the report asked for), Run all, Statistics |
| 2 | **List** | `ExtensionTestingListKey(ecosystem)` | one system's targets: NO checkbox column — `[icon][name+lang][ONE status chip][chevron]`; tap = expand (emphasized-easing `animateContentSize` + animated visibility), long-press = select (haptic tick; the check badge rides the icon, never a dedicated column); search field, select-all, pinned selection bar ("Test selected (n)") |
| 3 | **Run** | `ExtensionTestingRunKey(targetIdsCsv)` | the dedicated full-screen run experience (the round-83 overlay is dead): big animated progress, the current target's live per-kind rows (ping → search → home → details → episodes → video → play), the finished verdicts (tap → detail), the upcoming queue, Stop + Skip always live, completion re-runs |
| 4 | **Target detail** | `ExtensionTestingTargetKey(targetId)` | the full seven verdict cards — status, duration, message, diagnostic detail — plus per-target re-run |
| 5 | **Stats** | `ExtensionTestingStatsKey` | totals, per-system pass bars, the per-kind AVG-DURATION + FAILURE-RATE table, the "needs attention" failing list, the persisted run-history feed, Clear all results |

### 2.1 The run controller (`ExtensionTestRunController`)

The run lifecycle moved out of screen `remember`-state into an APP-SCOPED
singleton (`GlobalContext.get()` — the PluginImportActivity precedent; zero
DI wiring):

- its session lives in a `CoroutineScope(SupervisorJob() + Dispatchers.Default)`
  — navigating between the five pages never kills a run (the round-83
  overlay died with the screen);
- Stop and Skip are always wired to live methods (no `isBatchRunning`
  gating anywhere — the wedge class is structurally gone);
- verdicts persist per target the moment each finishes (the D-579 contract);
- every run appends a `StoredRunSummary` (scope/label/counts/per-kind
  aggregates, capped at 40) to the store's `history` key — the stats page's
  feed;
- the run page AUTO-STARTS its csv payload exactly once per armed payload
  and never while a run is live — the Home banner's "View" push (empty csv)
  only ever OBSERVES.

### 2.2 The navigation guard

Five page classes + several paths that can push the same class twice (a
detail's re-run pushing the Run page that already sits below it; rapid
double-taps) + the `SaveableStateProvider(class.simpleName)` nav scheme = the
Task-62 "Key … used multiple times" crash. Every testing push routes through
`pushTesting`: a new key is added; an existing one is REVEALED (everything
above popped) and, when the payload differs, re-payloaded in place.

### 2.3 The verdict models

`TargetRunState` gains `abortedByUser`; `isHealthy` now requires it false.
The store round-trips it (`optBoolean`, migration-free). `SEARCH` timeout
80s (§1.2). The store's `history` key is deliberately NOT `run-` prefixed —
`loadAll`/`prune` filter by the prefix and stay untouched.

Deleted: `ExtensionTestingScreen.kt`, `TestingTargetCard.kt`,
`TestingBatchOverlay.kt`, `TestingSummaryCard.kt` (the ProportionBar and the
status visuals moved to `TestingSharedUi.kt` — one shared file so all five
pages render identical semantics).

---

## 3. D-580 — THE DELETE EXIT CHOREOGRAPHY (both ecosystems)

The report: "the animation of the extension being deleted is not that
proper… the same kind of animation which is currently implemented in the
downloaded page". The Downloads reference (D-384) is a two-phase exit:

1. **The settle beat** — the row dips to 0.94 scale over 110 ms
   (`FastOutSlowInEasing`);
2. **The exit** — the row slides horizontally out of view (translationX →
   row width, `LinearOutSlowInEasing`) while fading (240 ms), both driven
   from draw-phase-only `graphicsLayer` transforms (zero recomposition per
   frame), and only THEN the real delete fires.

The shared implementation lives in `ExtensionListChrome.kt`
(`DeleteExitState` + `rememberDeleteExitState` + `runExitChoreography` +
`restoreFromExit` + `deleteExitLayer`) so BOTH tabs stay pixel-identical:

- **Aniyomi** installed/untrusted/errored rows: the trash tap plays the
  choreography optimistically, then fires the system uninstall intent (the
  system dialog IS the confirmation and can be dismissed — if the row is
  still composed ~3 s later the uninstall was cancelled and the row fades
  back in);
- **CloudStream** installed/errored/untrusted rows: the in-app AlertDialog
  stays the confirmation; the choreography runs AFTER it, and the plugin
  delete fires when the exit finishes (the data removal closes the gap);
- **Every list row of both tabs** (all four sections) now carries
  `Modifier.animateItem()` — after the choreography the rows below GLIDE up
  exactly like the Downloads page, and installs/uninstills animate too.

---

## 4. D-580 — THE TRUE HEADER PILLS

The report: the icon-only squares "read as circles, not pills… each one of
them will be having the appropriate amount of width as needed". The three
header buttons are now TRUE pills — icon + short ExtraBold label
(**Tests · Filters · Settings**) in a rounded-full surface whose width
adapts to the content.

---

## 5. D-581 — THE BUTTON-FEEL FILTER MENUS + THE SEARCH FIELD

- **Language menu**: the `DropdownMenuItem`s became full-width BORDERED
  option cards (`MenuOptionButton`) separated by 4 dp gaps — the report's
  "separation between each individual language options… a button kind of
  feel". Active option: primary wash + ring + check badge; inactive: quiet
  raised cards.
- **Sort menu**: the same card anatomy; the active row keeps its direction
  arrow; tapping the active mode still flips ↑/↓.
- **The dedicated filter search field**: a steady leading magnifier (the
  classic silhouette), a FOCUS RING (the border warms to primary while
  typing), a taller touch field, a boxed clear button — the report called
  the old bare field "bad".

---

## 6. D-582 — THE LINK SOURCE SHEET (the third device pass)

1. **Taller** — the wheel grew 186 → 236 dp and the wheels mode carries a
   60 %-of-screen minimum height (the report: the wrap-content sheet was
   "a little bit way too smaller").
2. **THE WRONG-INITIAL-HIGHLIGHT BUG, root-caused** — the report: it
   highlighted CloudStream even though an ANIYOMI extension was linked (the
   Aniyomi row carried the ✓). Anatomy: each wheel's `snapshotFlow {
   centerIndex }` emits its initial value (0) on first collection; the CS
   wheel composes LAST, so its initial emission set `activeSide =
   CLOUDSTREAM` after Aniyomi's — and when the linked Aniyomi source sat at
   index 0, NO scroll ever re-fired Aniyomi's emission. Fix: center
   emissions now only move the SELECTION; the ACTIVE side changes only on
   USER-driven interaction (a row tap, or a drag/fling that settles on the
   wheel — detected via `isScrollInProgress` + a guarded true→false settle
   transition). The initial emission and the programmatic pre-selection
   snap can never steal the highlight again.
3. **The 3D cards** — each ecosystem card gains an animated drop shadow
   (lifts when active), a hairline rim that warms to primary when active,
   and a top-light/bottom-shade gradient sheen — the report: the flat tints
   "were not getting a 3D kind of effect".
4. **Scroll + haptics** — the per-row falloff math moved INTO the
   `graphicsLayer` block (draw phase): scrolling no longer recomposes every
   visible row per frame (the report: "make the scrolling a bit more
   proper, a bit more smoother"); `HapticHelper.lightTick` fires on
   drag-settle and taps (the report: "add some haptic feedback to the
   scrolling").
5. **The search bar** — a focus ring + an elevated primary search button;
   and the content name is NO LONGER prewritten: the bar opens EMPTY
   showing "Search <source>…", and the FIRST focus pastes the content name
   (the report's exact spec) — which also dissolves the truncated-name
   complaint (the long prewritten title was what got cut).

---

## 7. Verification

- Nesting-aware tokenizer balance on EVERY touched file (package lines
  byte-verified against their folders — the round-83 `.tests` lesson).
- CI run 1 (the UI batch, 839c7c8): **GREEN first-try**.
- CI run 2 (the testing system, 713ba6e): pending at doc time — see the
  progress ledger.
- The engine/controller seam re-read in full; the isolation design
  reviewed against the three failure classes (runBlocking's interrupt
  swallowing, single-thread self-deadlock, shared-pool queueing) — all
  excluded by construction.
