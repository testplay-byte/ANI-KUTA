# 67 — ROUND 85: THE v1.1.41 DEVICE ROUND — honest stop/settle semantics, in-place runs, rich payloads, the stats dashboard, the hub rework, the sheet's fourth pass, and the confirmed-removal choreography (D-584..D-586)

> Round: 85 · Date: 2026-09-24 · Base: v1.1.41 (round 84) · Ship: v1.1.42/10142
> Input: the user's v1.1.41 device report — the longest feedback round of the testing system so far, spanning the Link Source sheet, the extensions screen, all five testing pages, the statistics, and the animation choreography.

---

## 0. THE USER'S REPORT, ITEMIZED (and where each item landed)

| # | The complaint | The fix | Where |
|---|---|---|---|
| 1 | Link Source sheet: "the bottom-up menu is way too much, and there is a lot of empty space" | the D-582 60% min-height slab is GONE (wrap-content); results mode is a ceiling not a fixed height | D-586, ManualSearchSheet |
| 2 | "when I click the search bar… the keyboard covers the whole search bar" | the sheet's bottom padding = `navigationBars ∪ ime` insets + `adjustResize` on the activity | D-586 |
| 3 | "removing my focus from the search bar… should return to its normal state, like without the text showing" | the BLUR CONTRACT: on focus loss (outside tap OR keyboard-back dismissal) the query resets and the paste re-arms — unless results are showing | D-586 |
| 4 | "the extensions should be sorted by name in alphabetical order" | both wheel buckets `sortedWith(CASE_INSENSITIVE_ORDER)` + `remember`-keyed (no per-keystroke refilter) | D-586 |
| 5 | Extensions header pills: "they should not be showing text. There should only be the icons" | icon-only stadium pills, 44dp min width (still pills, not circles), a11y name on the Surface | D-586 |
| 6 | "the two buttons at the bottom [of Testing Home] feel a little bit off… get some creativeness" | the HEALTH-RING hero + the recent-run strip + the COMMAND FOOTER (morphing Run-all pill + badged Stats pill) | D-585 |
| 7 | "if I stopped something, it would not get marked as stopped and would not be stopped most probably either" / "it will still show now testing" | the STOP SETTLE: `currentTargetId` cleared, dangling RUNNING results → SKIPPED("Stopped by user"), the interrupted target finished+aborted | D-584 |
| 8 | "when I went back and tried clicking run test on it again, the test did not rerun properly" | the armed-set was `rememberSaveable` (restored by the nav shell per CLASS) — now instance-local + armed only after a successful start | D-584 |
| 9 | "the options for the test and the full details should be shown at the top… only the simple tests and their time duration should be shown" | the list expansion leads with the actions and renders ONLY compact status+duration rows (no messages, no PENDING wall) | D-584 |
| 10 | "when I clicked on Run Tests, it ran the tests on a completely new screen, which was not good. It should run on that same screen" | IN-PLACE RUNS: list Run-all/per-row/selected and the detail page's run button start the app-scoped controller directly; the run page is a deep view | D-584 |
| 11 | "video resolve… only tried for five seconds or so, and it failed there. Like they were working once" | the CS resolver no longer clamps a missing/degenerate `loadLinksTimeoutMs` UP into a hard 5s wall; the Aniyomi test mirrors the PRODUCTION hoster ladder; the kind budget 45s→90s; up to 3 episodes | D-584 |
| 12 | "not be using one piece for the anime… about normally 12 or 24 episodes… also add one Chinese anime to the list" | phrases: Jujutsu Kaisen (24 ep) → Link Click (donghua, 11 ep) → Breaking Bad → Interstellar; One Piece + Naruto out | D-584 |
| 13 | "if one search fails, then it will not just mark it as failed, but it will test the next search phrase in line" | the ladder already continued; the round-85 CANDIDATE WALK extends it past search: details/episodes walk the FULL search + home pools | D-584 |
| 14 | "show me the actual search results on the details there. Same goes for the homepage… details page… episode list… the video result too and for the stream play too" | the TestPayload chain + the payload renderers (poster cards, dossier, EP chips, server rows, metric chips) | D-585 |
| 15 | "the test run screen was not showing the details in a clean, beautiful, well-managed way… the details should be shown below the tests" | the run page's rows are tap-expandable with message + detail + PAYLOAD below the row | D-585 |
| 16 | "you were only using a single bar… You did not show any circular donuts, graphs, bars" | the TestingCharts kit (animated donut, grow-in bars, sparkline, count-ups) + the stats dashboard rework | D-585 |
| 17 | "if the user has long-pressed and has opened up the selections, then single clicking will not open up the details" | while a selection is open, tap TOGGLES selection (never expands); with none open, tap expands | D-584 |
| 18 | "on the very right side of each one of the extensions, there is no need to show the arrow" | the trailing chevron is GONE — the status chip is the single trailing indicator | D-584 |
| 19 | "when the user actually clicks OK and the extension gets deleted, then the animation should play… add this kind of animation for trusting and untrusting too" | the CONFIRMED-REMOVAL GHOST ROWS (system-broadcast-driven) + tap-driven trust/untrust exits on BOTH ecosystems | D-586 |
| 20 | deploy 4-5 research sub-agents on the testing screens | DONE — 5 Explore agents (list/run+detail+tests/stats+history/sheet/pills+animations+hub) with file:line root-causes; their findings drive D-584..D-586 | this round |

Plus: the user-ordered FIRST task — a paste-ready **agentic UI-designer prompt** for the testing subsystem, delivered to the upload folder (`ANI-KUTA-UI-DESIGN-AGENT-PROMPT.md`) with the full domain brief (two ecosystems, the 7-stage pipeline, the payload inventory, the pain points, the deliverable spec).

---

## 1. D-584 — THE RUN-STATE MACHINE + IN-PLACE RUNS + THE PATIENT RESOLVE

### 1.1 The stop residue (the "still shows now testing" bug)
Three stacked defects in the controller's stop path:
1. `RunSession.currentTargetId` was never cleared — the Run page's hero rendered whenever it was non-null with no phase gate, printing "Now testing" forever after a stop.
2. The engine's in-flight `TestResult(status = RUNNING)` stayed in the states map on cancel (no terminal emission ever came) — every renderer that draws a RUNNING result as a spinner spun forever.
3. `start()` created the whole queue with `isRunning = true` — during a batch, every queued row claimed "Testing…".

THE FIX (one place, the settle in `invokeOnCompletion`): clear `currentTargetId`; rewrite RUNNING results to `SKIPPED("Stopped by user")`; mark the interrupted target `finished + abortedByUser` (a verdict the user cut short is not healthy — D-583 — and "Re-run failed" naturally offers it again). COMPLETED also clears `currentTargetId`, so the last target joins the Finished section instead of posing as "now testing" forever. Queue states start `isRunning = false` and only the loop's current target flips true — a "Queued" chip (tertiary) now covers the waiting rows.

### 1.2 The rerun blocker (the "won't re-run" bug)
The Run page's `armedCsvs` was `rememberSaveable` — and the nav shell keys every screen's saveable state BY CLASS NAME (`SaveableStateProvider(key::class.simpleName)`) without ever calling `removeState`. Pushing the Run page again with the SAME csv restored the armed set → the auto-start guard was false → `controller.start()` never fired → the page rendered the stale session. THE FIX: instance-local `remember` + arm ONLY after `start()` returns true, keyed on `(csv, phase)` so a refusal (another run live) retries when the phase changes.

### 1.3 In-place runs (the "same screen" demand)
Every run entry now calls the app-scoped controller DIRECTLY: the list's "Run all", per-row "Run tests", "Test selected", the detail page's "Run all tests for this source", and the hub's Run-all. The rows animate queued → testing → verdict right where they are (the session StateFlow every page already observes). The dedicated Run page remains a DEEP VIEW: the hub banner's "View", the list's pinned RUN STRIP (progress + Details + Stop, shown while running), and the update-safe full experience.

### 1.4 The list screen rework
Expansion: ACTIONS FIRST, then only `KindCompactRow`s (status icon + label + duration — NO message column, NO PENDING wall; a hint line when nothing started). Selection contract: `selectionMode = selectedIds.isNotEmpty()` gates the row's tap — tap toggles selection while selecting, expands otherwise; long-press always selects. The trailing chevron is REMOVED. `animateItem()` on the rows; the double height-animator (animateContentSize + AnimatedVisibility driving the same height) resolved to AnimatedVisibility alone.

### 1.5 The patient resolve (the "~5 seconds" bug)
Two root causes found by the research agents:
- **The CS clamp**: `CloudstreamLinkResolver.totalTimeoutMs` coerced the provider's declared budget into `[5s, 480s]` — a MISSING or degenerate (0/sub-5s) declaration became a HARD 5-SECOND WALL around `loadLinks`. Fix: null or < 5s ⇒ the 120s default; a real declaration is honored up to 480s. The 30s first-link watchdog still bounds the UX, so the default costs nothing in production.
- **The Aniyomi ladder bypass**: the test called only the legacy `getVideoList(episode)` while production (`VideoResolver`) tries `getHosterList` FIRST (ext-lib 16+), then lazy `getVideoList(hoster)` per hoster, then the legacy call. A hoster-based extension whose legacy parse throws failed the test in one page-fetch while playback worked. The test now mirrors the production ladder exactly (per-rung 20s bound, max 4 hosters, up to 3 episodes), each rung individually caught.

The `VIDEO_RESOLVE` kind budget rose 45s→90s — patience is the point; the hard isolation (D-583) still guarantees the run advances.

### 1.6 The smart phrases + the candidate walk
Phrases: `Jujutsu Kaisen` (Anime, 24 ep) → `Link Click` (Donghua, 11 ep — the user's Chinese-anime ask) → `Breaking Bad` (Series) → `Interstellar` (Movie). The round-84 complaint about One Piece was that the CHAIN TARGET became a 1100-episode marathon; the first winning phrase now yields a ~12-24-episode show.

The CANDIDATE WALK: `ExtensionTestContext` gains `searchCandidates` + `homeCandidates` + `candidatePool()` (foundAnime first, then search, then home — URL-deduped). DETAILS walks the pool forward on an exception (the winner becomes foundAnime for the rest of the chain); EPISODE_LIST walks forward on empty episode lists (counting dead ends honestly in the message). The user's "use any of the contents from the home page to check the details page" — now real, and stronger than asked: one dead entry never sinks the chain.

---

## 2. D-585 — THE RICH PAYLOADS + THE STATS DASHBOARD + THE HUB

### 2.1 The payload chain
`TestPayload` (all-plain-data: entries, details dossier, episode chips, video rows, metric fields) rides `TestOutcome → TestResult → the controller → the UI`; the store persists a JSON projection per kind (opt-read both ways — old blobs without payloads degrade gracefully; Drawables/Headers never enter it). Capture caps: 12 entries, 8 genres, 600-char synopsis, 48 episode chips, 12 videos.

Per test: PING (HTTP code + RTT chips), SEARCH/HOME (the ACTUAL results as poster cards — SubcomposeAsyncImage with letter-tile fallbacks), DETAILS (the parsed dossier: poster + title + status + genres + 4-line synopsis), EPISODE_LIST (EP 1…EP n chips + "+N more"), VIDEO_RESOLVE (Aniyomi `videoTitle`/resolution rows; CS link name + quality via the ABI Qualities scale — 0=Auto, 400=null, ≥2000=4K), STREAM_PLAY (HTTP + bytes-delivered chips).

### 2.2 Where payloads render
- RUN page: every terminal kind row is TAP-EXPANDABLE (a chevron appears only when there's something to show) — the message, the detail line, then the payload BELOW the row. "The details should be shown below the tests and their details should be proper."
- DETAIL page: each `KindDetailCard` renders its payload inline — the full dossier by default (detail text no longer truncated at 3 lines).

### 2.3 The stats dashboard (the "single bar" complaint)
New `TestingCharts.kt` — bespoke Canvas, zero library chrome:
- `DonutChart` — animated-sweep segments (the ProfileSections D-248 drawing technique upgraded with an entry sweep + a center slot), Butt caps, an idle rest-ring;
- `AnimatedStatBarRow` — grow-in horizontal bars (the debug-bubble NetworkTab pattern);
- `Sparkline` — line + soft-fill trend (NetworkTab's path technique);
- `CountUpText` — numbers move on entry.

The STATS page now reads: HEALTH-RING hero (passed/error/interrupted segments + % center + legend + count-up StatsBig) → the PASS-RATE TREND sparkline over the history (≥2 runs) → BY SYSTEM as two mini donuts → STAGE RELIABILITY as seven grow-in bars (fail fraction + avg time; error-tinted at ≥50%) → Needs attention → Recent runs (now with wall time) → Clear.

### 2.4 The hub rework (the "two off buttons" complaint)
The hero became a HEALTH RING (donut + % center + legend). A RECENT-RUNS chip strip (the last 6 runs, tap → Stats). The bottom: a COMMAND FOOTER — one designed primary pill ("Run all tests · N"; while running it morphs to live n-of-m + "tap to view" + an inline Stop) + a tonal Stats pill with a failed-count badge. No stock Material buttons anywhere on the page.

---

## 3. D-586 — THE CONFIRMED-REMOVAL CHOREOGRAPHY + THE SHEET'S FOURTH PASS + THE ICON PILLS

### 3.1 The ghost rows (the delete-animation timing)
The round-84 design played the exit choreography optimistically on the trash tap and restored the row if the system uninstall was cancelled after ~3s — the round-85 report reverses it: "when the user actually clicks OK and the extension gets deleted, then the animation should play."

For an Aniyomi uninstall the "OK" IS the system dialog, and the only honest signal that it landed is the system's `ACTION_PACKAGE_REMOVED` broadcast. So:
- the trash fires `ExtensionInstaller.uninstallApk` DIRECTLY — no local animation, the row stays fully visible under the prompt, a cancel costs NOTHING (the blind `delay(3000)` restore heuristic is deleted);
- a screen-local receiver (ContextCompat-registered, NOT_EXPORTED, the ExtensionInstallReceiver pattern) freezes the removed extension as a GHOST — keyed by pkgName at its captured index (read from the last COMPOSED lists via `rememberUpdatedState`, so the manager's own refresh racing the receiver is harmless);
- `mergeGhosts()` re-inserts the ghost into the section list at exactly its old position; the row renders with `forcedExit = true`, plays the exit choreography ONCE, calls `onExitDone`, the ghost drops, and `animateItem()` glides the rows below closed;
- uninstalling from system Settings also animates (harmless, arguably correct); non-extension removals never match.

TRUST/UNTRUST gets the same choreography tap-driven on BOTH ecosystems (Aniyomi installed-untrust / untrusted-trust / errored-untrust; CS installed-untrust / untrusted-trust): the row visibly leaves its section, THEN the data change fires, and it re-enters the destination via animateItem. The CS delete flow already animated on the dialog's OK — documented as already per-spec.

### 3.2 The sheet's fourth pass
- DEAD SPACE: the `heightIn(min = 60% of screen)` is gone (it was a ~180-250dp void between the hint and the search bar); the results mode's fixed height became a ceiling.
- THE KEYBOARD: the sheet's bottom padding is `WindowInsets.navigationBars.union(WindowInsets.ime)` — nav-bar height when closed, keyboard height when open — plus `android:windowSoftInputMode="adjustResize"` on the activity. The search bar always rides above both.
- SORT: both wheel buckets are alphabetically sorted (case-insensitive) and `remember(availableSources)`-keyed — which also fixes the whole-sheet per-keystroke refilter. The linked-source pre-selection runs on the sorted lists, so centering stays correct automatically.
- THE BLUR CONTRACT: on focus loss while no results are showing (`manualSearchState is Idle && !showResults`), the query resets and the first-focus paste re-arms. Two paths reach it: an outside tap (onFocusChanged) and keyboard-back dismissal (which does NOT blur — an IME-closed `snapshotFlow` observer clears focus explicitly). Results are never nuked.
- `query`/`showResults`/`autoPasted` are `rememberSaveable` — rotation no longer blanks the sheet.

### 3.3 The icon-only header pills
`HeaderPillButton` lost its label (the device report: "they should not be showing text. There should only be the icons") but NOT its pill shape: `defaultMinSize(44dp × 40dp)` + wider padding keeps a wide stadium (the exact round-84 "circles, not pills" regression avoided), and the accessible name moved onto the Surface via `semantics { contentDescription }` (one clean TalkBack node; the icon's own description is null).

---

## 4. THE UI-DESIGN-AGENT PROMPT (the user's special order — done FIRST)

Delivered to the upload folder: `ANI-KUTA-UI-DESIGN-AGENT-PROMPT.md`. A self-contained brief for an external agentic UI designer: the role, the host design language, the full domain (two ecosystems, the 7-stage pipeline, the per-test payload inventory, the operations, the persisted/history data), the KNOWN PAIN POINTS verbatim (so the designer beats them), the five screens with expected outcomes (layouts as targets, not cages), the micro-moments, and the deliverable format (vanilla HTML/CSS/JS mockups + rationale + state matrix + handoff tokens). The user can paste it into any agentic design tool and evaluate a from-scratch layout against ours.

---

## 5. VERIFICATION

- Nesting-aware tokenizer on every touched Kotlin file: ALL BALANCED; manifest minidom-validated (the XML "errors" from the Kotlin tokenizer are quote-literals false positives).
- Self re-reads caught pre-push: the nullable `result.message`/`result.detail` in the run page's expanded row, the `hasDetail` payload gate, the lambda-param shadowing in the store's genre projection, the sheet's lost newline.
- CI (disclosed ledger): batch 1 (77f8185) GREEN first-try; batch 2 (6360bae) FAILED on ONE error (the missing `TestPayload` import in PingTest) — diagnosed from the logs, fixed in the batch-3 push (541a3ec); batch 3 GREEN. Release run: see the progress ledger.

## 6. THE DEVICE-ROUND CHECKLIST (v1.1.42)

- [ ] Testing Home: health ring animates; Run-all starts IN PLACE (the footer morphs to live progress + Stop; the banner shows too); recent-run chips open Stats.
- [ ] List screen: Run all / row Run / Test selected all run WITHOUT navigating; rows go Queued → Testing… → verdict live; the pinned run strip shows progress + Stop + Details.
- [ ] Tap a row → expands with ACTIONS ON TOP + only durations; long-press → selection; with a selection open, tap toggles selection; no trailing arrow.
- [ ] Run → STOP mid-target: the hero vanishes, the interrupted target reads "Skipped"/Stopped, no spinner survives, "Re-run failed (n)" includes it; re-run works from the list AND a fresh Run page push.
- [ ] Detail page: Run here animates the cards in place; tap a completed row on the Run page → message + detail + ACTUAL results below.
- [ ] Search/Home payloads: real poster cards; Details: the dossier; Episodes: EP chips; Video: server/quality rows; Ping/Stream: HTTP/RTT/bytes chips — on BOTH the run page (expanded) and the detail page.
- [ ] Video resolve on previously-"5s-failed" CS plugins now resolves (patient budget); hoster-based Aniyomi extensions pass.
- [ ] Stats: health donut + count-ups + trend sparkline + per-system mini donuts + stage-reliability bars.
- [ ] Phrases: the search message should show Jujutsu Kaisen or Link Click as the winning phrase on most anime sources; a dead first entry no longer fails Details/Episodes.
- [ ] Sheet: compact (no dead slab), alphabetical wheels, the keyboard never covers the search bar, blur resets the field (results survive once a search ran).
- [ ] Pills: Tests/Filters/Settings are icon-only stadiums.
- [ ] Delete: tap trash → the SYSTEM dialog appears with the row still visible; Cancel → nothing; OK → the row animates out and the gap closes. Trust/untrust animates the same way on both tabs.
