# 68 — ROUND 86: THE v1.1.42 DEVICE ROUND — the honest verdicts, the live timers, the timeline detail page, the normal source list, and the per-system health ring (D-587..D-592)

> Round: 86 · Date: 2026-09-24 · Base: v1.1.42 (round 85) · Ship: v1.1.43/10143
> Input: the user's v1.1.42 device report — "fifty-fifty-ish": the uninstall choreography and the pills held, but the Link Source sheet's search bar could not be TAPPED, a dead source cascaded as "skipped", runs that were never stopped said "Stopped by user", and the dedicated detail page's layout was "very bad… really bad".

---

## 0. THE USER'S REPORT, ITEMIZED (and where each item landed)

| # | The complaint | The fix | Where |
|---|---|---|---|
| 1 | Link Sources: "rather than making them like alarm clock selector, let's make them just normal and better" | the two drums are DELETED — one normal alphabetical list (D-587) | D-587, ManualSearchSheet |
| 2 | "the heading is shown in a bubble format… show it in a rounded rectangle format and make the text bold and a bit bigger" | `SourceSectionHeading` — full-width rounded rect, Bold 14sp (up from the 11sp pill), count on the right | D-587 |
| 3 | "the results will be shown in proper alphabetical order and in a proper list kind of format" | both buckets arrive pre-sorted (round 85); the list rows render them in order | D-587 |
| 4 | "the currently selected one is properly highlighted and managed better" | tint + 1.5dp border + ExtraBold + a primary check bubble on the selected row; the linked ✓ stays | D-587 |
| 5 | "move the text to the very bottom… and update this text" | the hint now sits BELOW the search bar, reworded "Tap a source to select it, then search below." | D-587 |
| 6 | "clicking on the search bar… nothing was happening… the selection would not appear" (THE critical bug) | ROOT CAUSE: the round-85 keyboard-back poll cleared focus on its FIRST check — the async IME had not opened yet, so "not yet visible" read as "just closed" and the field lost focus the same frame it won it (paste worked because it edits the buffer programmatically). The watcher now acts only AFTER the keyboard was OBSERVED visible once (`imeSeenVisible`) | D-587 |
| 7 | Suite health: "split those three statuses into six, separating the two systems… shown together as one and then split" | the ring is SIX segments — the Aniyomi half (primary/error/dim), a 4° gap, the CloudStream half (tertiary/error/dim); two-caption legend | D-588, TestingHomeScreen + DonutSegment.gapAfterDegrees |
| 8 | Recent runs: "show the icons of the extensions and the name… and that's it" | the strip now renders the last tested TARGETS (store-ordered) as icon + name chips — nothing else | D-588 |
| 9 | System cards: the count "should be shown on the right side and in a highlighted view", "no need to show the arrow", "a better improved blend of colors" | the subtitle count is GONE — a right-side accent chip carries it; the chevron is deleted; each card has an accent edge (primary / tertiary) | D-588 |
| 10 | "the button of the stats was most definitely not good" | the footer Stats pill restyled: icon (primary) + label + a failed-count bubble | D-588 |
| 11 | Stats: "no need to show any of those at all" (Recent runs + Needs attention) | BOTH sections deleted; the Clear-all affordance relocated below the charts | D-588, TestingStatsScreen |
| 12 | "Run all, run failed, run passed" | THREE run-scope pills in the list controls row (eco-scoped ids from the same verdict data the sort reads; count baked into the label; empty scopes disabled) | D-589 |
| 13 | Sorting: "passed at the very top in alphabetical order… failed at the bottom… if it failed in a lot of things, then it will be much further down" | the verdict sort: PASSED (alphabetical) → untested (alphabetical) → FAILED by failedCount ASCENDING then name (fewest failures highest, most failures lowest); re-sorts LIVE mid-run via the testedTick reload + animateItem | D-589 |
| 14 | Expanded details: "in the section of itself… separate from each other… buttons at the very bottom right corner" | the expansion is TWO blocks: a separated sub-container for the kind rows, then a bottom-right-aligned actions row (Full details · Run tests) | D-589 |
| 15 | "proper separation between each individual tests" + keep the one-at-a-time reveal | every row is its own rounded card; each row fades/expands in on first appearance (AppearingKindRow) | D-589 |
| 16 | "the duration… should be shown in live… while those 10 seconds are passing" | `runningKindStartedAtMs` recorded on every RUNNING emission; the compact rows TICK (200ms producer) while their kind runs | D-589/D-590 |
| 17 | "an animation of dots moving from that test name to the test duration" | the leader-dot trail (1→3 dots, infinite transition) between name and live timer — RUNNING only | D-589 |
| 18 | Test order: "first the ping test. Then the homepage test. Then the search test" | the chain + the enum order swapped: Ping → Home page → Search → Details → … (persistence keys by NAME — no migration) | D-590 |
| 19 | "it failed and it said it was skipped… rather than saying it was failed" + "if the search page and the home page fail… all of those tests will just directly be marked as failed" | THE HONEST GATE: the engine's prerequisite gate emits FAILED "Not run — <labels> failed" (SKIPPED was a lie for dead sources); the four test-level defensive skips aligned to FAILED | D-590 |
| 20 | "if the details page does not load, still it is considered as a pass [when the episode list loads]; if the details page loads but the episode list does not, it is considered as a fail" | THE DETAILS FORGIVENESS: DETAILS FAILED + EPISODE_LIST PASSED → DETAILS rewritten SKIPPED("Forgiven — the episode list loaded") at the end of the chain, so isHealthy/store/stats/re-run scopes all see the honest chain outcome | D-590 |
| 21 | "it said stopped by user, even though I never stopped it" | ROOT CAUSE: a stray CancellationException escaping a plugin killed the run job silently, and the round-85 settle stamped every completion-while-RUNNING as "Stopped by user". The settle is now CAUSE-AWARE: `stopRequested` (set only by stop()) + the completion cause + a session-generation latch — a user stop keeps the honest SKIPPED settle; an unexpected death rewrites dangling RUNNING kinds to FAILED "Run aborted: <Class: msg>", leaves the target NOT user-aborted, and logs the cause at ERROR with the stack | D-590 |
| 22 | "logs apparently don't seem to be that helpful" | STRUCTURED DEBUG LOG: `TEST TARGET START/FINISH` + `TEST KIND START/FINISH` (verdict, durationMs, http, url, reason, detail) + `TEST RUN STOPPED/ABORTED` + per-phrase search DEBUG lines | D-590 |
| 23 | "the details page which gets shown should be the one dedicated for the single extension… if there is only one extension" | the list run-strip's "Details" (and the hub banner/footer) route to `TestingTargetDetailScreen` when `session.queue.size == 1` | D-591 |
| 24 | The detail page: "the layout is very bad… at the very top, the extension details… well-formatted" | the dossier header: icon, name, verdict chip, and meta rows (System / Version / Package / Plugin / NSFW / Site / Last tested — resolved from the live managers) | D-591 |
| 25 | "the progress bar needs to be improved… a multi-stage progress bar… seven stages… happening in live view" | the 7-segment bar: each stage fills LIVE (its own wall clock against its budget, animated) and snaps to its verdict color | D-591 |
| 26 | "the bottom button… needs to be given a different color… it should not be given full width" | the run action is a bespoke TERTIARY pill (wrap-content, never full-width, dimmed while running) | D-591 |
| 27 | "a timeline kind of user interface… on the left side the timeline… bubbles connected… on the right side the cards" | the results section is a real timeline: a left rail of connected status bubbles (IntrinsicSize rows) + the per-test cards on the right | D-591 |
| 28 | "it showed me the description… that is not good… the tags… HTTP 200 / 543 ms… was not good" | the static descriptions are GONE from the cards; the PING/STREAM metric chips are GONE (the message + detail lines already say it) | D-591/D-592 |
| 29 | "the details should be shown in a coding kind of window kind of vibe" | `CodeWindowBlock` — a dark monospace terminal card with the three window dots; the cards' message/detail lines (and the search stats) live inside it | D-591 |
| 30 | Search: "what number of searches it did… did the first search give responses, or the second one" + "while the search is performing, it should say… which name it searched with" | the payload carries `searchAttempts` + the winning phrase/category (advanced stats in the code window); the LIVE per-phrase status ("Trying \"Link Click\" (Donghua)…") pipes through the context → the controller → the rows/cards while SEARCH runs | D-592 |
| 31 | Search/home results: "the top six results… grid format, three at the top and three at the bottom… the title one line" | `PayloadEntriesGrid` — the 3×2 grid, one-line titles | D-592 |
| 32 | Details: "it will prefer the search page's results, and it will randomly pick… every single time a different details page… from the top ten" | `detailsWalkOrder()` — a shuffled top-10 window of the search pool (home fallback) heads the walk; the pool tail remains the fallback | D-592 |
| 33 | Details dossier: "the title… the details, the URL… properly shown in a proper formatted way" | the dossier gains `detailsUrl` as its own formatted line; capture rides the store round-trip | D-592 |
| 34 | Video resolve "can be formatted better" | the resolved links render as numbered cards with quality chips (+N more) | D-592 |
| 35 | Stream play: "if the actual live preview could be shown… I would most definitely prefer it" | THE LIVE PREVIEW: the pass payload now carries streamUrl + referer/UA/headers (store round-trips them); the pages render a muted looping ExoPlayer card (DefaultHttpDataSource with the stream's own headers, released on disposal) — media3-ui added to the impl module | D-592 |
| 36 | "at the very bottom, it would show a finish… a complete block, just like similar to the other cards… if it fails, then it will properly show the failed one too" | THE FINISH BLOCK: the run page's closing card (verdict headline, passed/failed/skipped counts, wall time from the new `RunSession.finishedAtMs`; primary variant when all-healthy, error variant otherwise) | D-592 |
| 37 | Multi-run page top: "way too much generic, bad, ugly… our own custom design language" | the progress card is bespoke: a breathing pulse dot, a phase stadium chip, a hand-drawn rounded progress bar, "n of m done"; Stop/Skip and the aftermath buttons are bespoke stadium pills — zero stock Material left on the page | D-592 |
| 38 | "if the user tries to leave the extension testing page… prompted that leaving would cancel the tests, and it would actually cancel all the tests" | THE LEAVE GUARD: BackHandler + the header's back on the hub AND the run page — while a run is live, leaving opens "Leave and cancel / Stay"; leaving calls `controller.stop()` first | D-592 |

---

## 1. D-587 — THE SHEET'S FIFTH PASS: the focus-killer fix + the normal list

### 1.1 The un-clickable search bar (the round's critical bug)
The round-85 keyboard-back watcher polled the dialog window's IME insets every 100ms and cleared focus the moment `ime()` was invisible — but Compose's IME show is ASYNC, so on EVERY tap the first poll iteration ran before the keyboard opened and `clearFocus()` fired the same frame the field won focus. No caret, no selection handles, no keyboard; the auto-pasted title flashed and the blur-reset wiped it. PASTE still worked (programmatic buffer edit + the Search button's visibility only needs text). THE FIX: the watcher waits until the IME has been OBSERVED visible once (`imeSeenVisible`), then treats a later invisible as the back dismissal — the round-85 blur contract is fully preserved.

### 1.2 The drums are gone
`SourceSide`, `SourceWheelPair`, `SourceWheelSection`, the 236dp/48dp constants and `SourceWheelColumn` are DELETED (~400 lines). The selection collapsed to one `selectedSource` (linked-seeded). `SourceListPanel` renders one bounded LazyColumn: a `SourceSectionHeading` per system (the rounded-RECT, bold 14sp, count right), the alphabetized rows, `SourceListRow` with the full selected treatment. The hint moved BELOW the bar and reads "Tap a source to select it, then search below." The icon helpers (`SourceIcon`/`WheelSourceIcon`/`WheelIconFallback`) survive unchanged — the rows reuse them.

---

## 2. D-588 — THE HUB + THE STATS: six statuses, icon runs, the cuts

- THE SIX-SEGMENT RING: `DonutSegment` gained `gapAfterDegrees`; the chart's usable sweep shrinks by the declared gaps so segments + gaps complete exactly one revolution (the entry animation scales both consistently). The hub feeds Aniyomi passed/error/dim → 4° gap → CloudStream tertiary/error/dim; the legend is two caption rows ("Aniyomi"/"CloudStream") with pass/fail/new counts. The center keeps the overall healthy %.
- RECENTLY TESTED: the strip reads `storedRuns.values.sortedByDescending(testedAtMs).take(8)` — each chip is `TargetIconView` + name in a stadium, nothing else (uninstalled targets fall back to the letter tile via the stored name/ecosystem).
- SYSTEM CARDS: accent edge (primary/tertiary) + title + proportion bar + the right-side count chip; chevron deleted with its import.
- FOOTER: the Stats pill is `surfaceVariant@0.6` with a primary icon and a failed-count bubble.
- STATS PAGE: "Needs attention" and "Recent runs" are GONE; Clear-all survives as the page's closing action; `RUN_HISTORY_FORMAT` + its imports removed. The charts (trend sparkline, by-system donuts, stage bars) are untouched.

---

## 3. D-589 — THE LIST: run scopes, the verdict sort, the live rows

- THREE RUN SCOPES: the controls row split into two items — count + select-all, then the pill row (Run all / Run failed · n / Run passed · n) in primary/error/tertiary blends. Scopes are ecosystem-scoped id lists computed from the same `stateFor` verdicts the sort reads (the controller's rerunFailed/rerunPassed remain app-global and stay unused here).
- THE VERDICT SORT: bucket 0 passed (isHealthy), 1 untested (no finished state), 2 failed (incl. aborted) — inside bucket 2 `failedCount ASCENDING` then name; alphabetical inside the other buckets. `stateFor` now PREFERS THE STORE for a fresh live state (the queue's empty states no longer mask previous verdicts mid-run); the testedTick reload + `animateItem()` make the order evolve live.
- THE EXPANSION: block 1 = the kind rows in their own sub-surface (each row a card via the KindCompactRow rewrite); block 2 = the actions row aligned END (Full details, then Run tests at the very bottom-right).
- LIVE ROWS: the compact row ticks while its kind runs (200ms producer against `runningKindStartedAtMs`), shows the leader dots, surfaces the live per-phrase search line, and prints a SKIPPED verdict's reason in a small dim line.

---

## 4. D-590 — THE ENGINE: honest gates, the forgiveness, the settle that tells the truth

- THE ORDER: chain + enum = Ping → Home page → Search → Details → Episode list → Video resolve → Stream play (both display sites read `.entries`).
- THE HONEST GATE: missing prerequisites emit FAILED "Not run — <failed labels failed>"; the Details/Episodes/VideoResolve/StreamPlay defensive skips aligned. SKIPPED now belongs ONLY to: the user's skip, the Stop settle, and the forgiveness.
- THE DETAILS FORGIVENESS: DETAILS FAILED + EPISODE_LIST PASSED → DETAILS → SKIPPED("Forgiven — the episode list loaded") (original failure kept in detail via the rewrite; logged). DETAILS pass + episodes fail stays a FAIL by construction.
- THE SETTLE: `stopRequested` (only `stop()` sets it) + `invokeOnCompletion { cause }` + the session GENERATION latch (identity never matched — the loop copies the session on every emission). User stop → the round-85 behavior (SKIPPED "Stopped by user" + aborted). Unexpected death → RUNNING kinds → FAILED "Run aborted: <Class: msg>", the live target finished but NOT aborted (Re-run failed offers it), `Logger.e` with the stack. `TEST RUN STOPPED/ABORTED` lines make both visible in logcat for the first time.
- THE LOGGING: TEST TARGET START/FINISH (verdict, pass/fail/skip, totalMs) + TEST KIND START/FINISH (durationMs, http, url, reason, detail; FAILED at WARN) + per-phrase search DEBUG lines (phrase → WIN/empty/error).

---

## 5. D-591 — THE DETAIL PAGE: the dossier, the stages, the timeline

- THE ROUTE: run-strip "Details" + hub banner/footer → `onOpenTarget(queue.first())` when the session has exactly one target (MainActivity passes `onOpenTarget` to the hub).
- THE HEADER: icon 48dp + name + verdict chip + the meta rows (System/Version/Package/Plugin/NSFW/Site/Last tested) from `controller.targetMeta()` (Aniyomi: installed extension pkg/version/NSFW; CS: parent plugin + mainUrl).
- THE 7-STAGE BAR: one segment per test; done stages snap to their verdict color; the RUNNING stage fills live (150ms producer, animated fraction, capped 0.9 so the bar never lies).
- THE RUN PILL: tertiary, wrap-content, play icon, dimmed while running.
- THE TIMELINE: per-kind rows of [connector, bubble, connector | card] with `IntrinsicSize.Min` so the rail connects through every card; the whole section is one LazyColumn item.
- THE CARDS: description gone; the message/detail lines live in `CodeWindowBlock` (dark mono, three window dots, the live line accented); the search card adds the attempts + winning-phrase stats; the payload renders below (the 3×2 grid, the dossier with URL, EP chips, the numbered server cards, the live preview).

---

## 6. D-592 — THE RUN PAGE + THE PAYLOADS + THE GUARD

- THE BESPOKE TOP: pulse dot (breathes while running) + label + phase chip; the hand-drawn progress bar; "n of m done"; the stat chips. Stop/Skip = stadium pills; the aftermath = full-width pills (error/primary). No stock Material remains.
- THE FINISH BLOCK: the last item under the terminal phase — headline (Run finished — all healthy / Run finished / Run stopped), counts, wall time (`RunSession.finishedAtMs`, set on COMPLETED and both settle branches), primary/error accent.
- THE PAYLOADS: the 3×2 result grid (top 6, one-line titles); the dossier's URL line; the numbered video cards; the LIVE PREVIEW (media3-ui on the impl classpath; ExoPlayer + PlayerView in an AndroidView, muted, loop-one, the stream's own headers via DefaultHttpDataSource, released on disposal; shown only when the payload carries a URL).
- THE LEAVE GUARD: hub + run page — BackHandler and the header back route through the confirm dialog while a run is live; "Leave and cancel" stops the controller then pops.
- THE LIVE SEARCH: `ExtensionTestContext.onSearchPhrase` fires per attempt; the controller pipes it into `TargetRunState.runningDetail`; the list rows, the run hero and the search card all show which phrase is being tried.

---

## 7. The round-86 ledger of artifacts

- Code: 20 files across the testing package, the result store, the charts, the anime-details sheet, MainActivity and the impl build file (`implementation(libs.media3.ui)`).
- The round-86 compile review: a dedicated agent audited every changed file for syntax/unresolved-reference/scope errors — ZERO confirmed errors (braces balanced after the sed deletions; the Triple-destructuring comparator, the generation latch, the media3 surface and all call sites verified).
- Persistence: the store's payload projection round-trips every new field; old blobs degrade gracefully (opt-read).
- The UI-design-agent prompt from the round-85 special order remains at the upload folder (`ANI-KUTA-UI-DESIGN-AGENT-PROMPT.md`).

## 8. Open items for the next device round

- The stream preview's real-device behavior on DRM/hls links (the test only Range-GETs; ExoPlayer may surface formats the preview cannot play — the card degrades to black, never to a failure).
- The verdict sort's bucket for aborted-only targets sits in "failed" (failedCount 0) — between untested and real failures only by name; watch the device impression.
- If the user wants the six-status ring's halves swapped or the gap widened, it is one `gapAfterDegrees` value.
