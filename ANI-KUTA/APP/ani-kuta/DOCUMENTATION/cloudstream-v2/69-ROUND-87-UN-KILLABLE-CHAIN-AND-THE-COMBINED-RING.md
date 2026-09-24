# 69 — ROUND 87: THE v1.1.43 DEVICE ROUND — the un-killable chain, the honest palette, the combined ring, and the blocks that carry their own results (D-593..D-613)

> Round: 87 · Date: 2026-09-24 · Base: v1.1.43 (round 86) · Ship: v1.1.44/10144
> Input: the user's v1.1.43 device report — "most definitely not satisfied": the Link Sources sheet had lost its two groups and squeezed its search bar, the suite-health ring's two system colors were near-twins, the code-window blocks had to go entirely, a single test's timeout was killing every test after it, and the run pages jumped and froze where they should linger and tick.

---

## 0. THE USER'S REPORT, ITEMIZED (and where each item landed)

| # | The complaint | The fix | Where |
|---|---|---|---|
| 1 | Link Sources: the two ecosystems must be two SEPARATE groups again — not one merged list | one rounded SECTION CARD per ecosystem (accent dot + bold heading + count), the Aniyomi list and the CloudStream list clearly apart | D-613, ManualSearchSheet |
| 2 | Link Sources: "the search bar is squished — there's no room for it" | ROOT CAUSE: the fixed 430dp list cap could exceed the space left once the IME opened, so the bar clipped. The cap is now ADAPTIVE: screen − nav − IME − 214dp reserved — the bar can never be squeezed out again | D-612 |
| 3 | Suite health: "if Aniyomi has 10 passed and CloudStream 5, show 15 — with the split inside" | THE COMBINED RING: pass / fail / untested groups, each ring subdivided by system, group gaps REPLACED (not added) so 10+5 reads as one honest 15; the legend shows the total and the "· 10 + 5" split | D-599, TestingHomeScreen + TestingCharts |
| 4 | "the colors of Aniyomi and CloudStream are way too close together. They look ugly" | ROOT CAUSE: primary (the accent preset) vs the never-themed M3 baseline tertiary (pale pink ~20° from error red). TestingPalette.kt — FIXED hues that never follow the accent: ANIYOMI = emerald #34D399, CLOUDSTREAM = sky #38BDF8 | D-594, TestingPalette.kt (NEW) |
| 5 | System cards: the bar must fill the full width; the count badge belongs right beside the text, not marooned at the far edge | the proportion bar spans the card; the count chip sits NEXT TO the title; the idle content centered | D-600/D-601 |
| 6 | "the Run All Test button on the main screen is not good" | the run-all pill redrawn — bordered, tinted, centered beneath the hero | D-602 |
| 7 | "the untested color blends into the background — a huge problem" | untested stopped being an afterthought: two distinct grays (light/dark per system) + a VISIBLE ring idle track (onSurfaceVariant@0.25, resolved in composition — Canvas lambdas are not composable) + visible untested track/fill in the cards | D-594/D-602 |
| 8 | "remove the double-check (select all) button" (the round-86 order that did not land) | `DoneAll` DELETED — import, button, and all | D-603, TestingTargetListScreen |
| 9 | "the three run buttons go to the RIGHT of the source count — not below it" | ONE controls row: the count and the three compact run pills (all/failed/passed) share the line | D-603 |
| 10 | the detail page's empty state said "…each test's timing here" | there IS no empty state anymore — every one of the seven blocks always renders; un-run ones simply sit faded, so the wording question dissolved | D-604/D-605 |
| 11 | "tests that haven't run should still show, but greyed/faded" | pending blocks render on the faint surfaceVariant track at reduced alpha (0.14 fill / 0.42 rows on the list), their stage-bar share held on the track | D-605/D-606 |
| 12 | "when a test completes, do NOT auto-scroll to the top or bottom — let the result stay a while, then collapse smoothly" | the verdict BANNER: slides in under the actions, LINGERS ~2.6s, slides away; the run page's auto-scroll effect DELETED — the user owns the viewport | D-609/D-611 |
| 13 | "the animations need more polish" | animateFloatAsState on every ring/bar fraction, the banner's slide+fade spring, the entrance choreography kept, the breathing pulse dot retained | D-609/D-610 |
| 14 | Full Details: "the top details layout is not clean" | the DOSSIER HEADER: small uppercase muted labels in a fixed column, values that wrap, hairline dividers between rows — every fact readable at a glance | D-605 |
| 15 | "the progress bar: the LENGTH should be by each task's time, each part a different color" | THE TIME-PROPORTIONAL STAGE BAR: finished segments sized by ACTUAL ms, the running segment grows on a live ticker, pending holds its budget's share; every segment wears its TEST KIND's color (failures dim their hue) | D-606 |
| 16 | "the Run All Tests for this Source button needs a redesign" | bordered tinted pill + icon badge, wrap-content, never full-width, dimmed while running | D-608 |
| 17 | "remove the code window COMPLETELY — show the results inside the colored test blocks (e.g. the ping block carries its results; homepage shows results in the block)" | `CodeWindowBlock` DELETED entirely; each result block is tinted + bordered with its kind color and carries a labeled RESULTS section INSIDE (message/detail as clean text rows, search ladder as stat pills, payload inside the block) | D-607 |
| 18 | video resolve: "show ALL the resolved videos as a list" | the payload rows UNcapped (payload 24) and the CS resolver now ACCUMULATES links across snapshots (dedup) so early finds survive later snapshots | D-595/D-596 |
| 19 | stream play: "put a 30-second limit — then stop, say it played successfully, and close everything" | THE 30-SECOND CAP: a one-second countdown ticks the last moments, auto-stop at the ceiling, "Stream played successfully", REPEAT_MODE_OFF, and the PlayerView detached in onRelease — surface, session, player: all closed cleanly | D-598 |
| 20 | multi-run page: "the currently running test doesn't show its live time" | the live elapsed timer finally reaches the run page's rows (LiveElapsedText → KindResultRow → LiveKindRow) — a running test ticks everywhere | D-597 |
| 21 | THE ENGINE: "when one test stops midway due to timeout, the other tests should NOT stop" | ROOT CAUSES: (a) SearchTest caught CancellationException BEFORE TimeoutCancellationException — TCE IS-A CE — so its own 12s phrase timeout escaped the ladder and cancelled the whole run job; (b) TestIsolation's awaiter rethrew every body-origin CE; (c) the queue loop had no blast-radius containment. THE FIX: TCE caught FIRST + isActive guards; body-origin CEs convert to FAILED verdicts inside the isolation async (isActive distinguishes "the body died" from "the user pressed Stop"); each target wrapped in containment — one target settles FAILED "Run aborted: …" and the queue CONTINUES; a CoroutineExceptionHandler nets the rest; stop() still unwinds everything | D-593 |
| 22 | "the whole testing system should be modular, with the logic verified, the workflow verified, and proper error handling" | the palette extracted to ONE source of truth (TestingPalette), the engine's isolation/containment/ladder separation, cause-aware settles at every level, and the two sub-agent audits (compile + the 22-point requirements check) as the verification | D-593/D-594 + §7 |

---

## 1. D-593 — THE UN-KILLABLE CHAIN

The round-86 engine told the truth about WHY a test died — and in doing so revealed that one death took the whole run with it. Three coordinated fixes:

- THE CATCH-LADDER ORDER (SearchTest): `TimeoutCancellationException` is a SUBCLASS of `CancellationException`; the old `catch (ce: CancellationException)` sat before the TCE catch, so the test's own `withTimeoutOrNull`-style phrase timeout was consumed as "cancelled" and RETHROWN — killing the run job. TCE is now caught FIRST and converted to a phrase error; both catches guard with `currentCoroutineContext().isActive` so a genuine user-stop still propagates. DetailsTest / EpisodeListTest / VideoResolveTest got the same guard on their CE paths; HomePageTest had no guard at all and needed none after the isolation fix.
- THE ISOLATION AWAITER (TestIsolation): the body runs inside an async block that CATCHES Throwable and converts body-origin CancellationExceptions into FAILED verdicts — the awaiter can now never see a body CE and mistake it for scope cancellation. The `isActive` check at the catch site is the discriminator: the context dead ⇒ a real cancellation (rethrow); the context alive ⇒ the PLUGIN cancelled itself ⇒ FAILED with the cause in the detail line.
- THE CONTAINMENT (ExtensionTestRunController): each target's queue step is wrapped in try/catch + `containTargetFailure(target, cause)` — the target settles FAILED "Run aborted: <Class: msg>" and the LOOP MOVES TO THE NEXT TARGET. A `CoroutineExceptionHandler` on the run scope nets anything that still escapes. `stop()` remains the only thing that unwinds the whole run — a user stop is a stop; a test death is one verdict.

## 2. D-594 — TESTINGPALETTE: one source of truth for every hue

The new `TestingPalette.kt` owns every color the testing system uses. Why the old pair failed: the ring fed the theme's `primary` (user-selectable accent) against Material3's BASELINE `tertiary` #EFB8C8 — a pale pink nothing else in the app ever used, sitting ~20° from error red. Fixed assignments that never follow the accent preset: `SystemA` emerald #34D399 (Aniyomi), `SystemB` sky #38BDF8 (CloudStream, ~50° away); failures red/orange per system; untested two distinct grays (light/dark); seven per-kind accents (ping sky, homepage amber, search pink, details coral, episodes teal, video-resolve lime, stream violet) so a block is the same color everywhere it appears — ring, cards, stage bar, result blocks.

## 3. D-595..D-598 — THE PAYLOADS AND THE 30-SECOND STREAM

- D-595: `PayloadVideoRows` renders EVERY resolved link — the cap is gone (payload 24).
- D-596: the CloudStream resolver ACCUMULATES links across its snapshots with dedup — a link found in an early snapshot is not lost when a later snapshot re-lists the page.
- D-597: the run page's rows tick — `LiveElapsedText` wired through `KindResultRow` → `LiveKindRow` against `runningKindStartedAtMs`; the "frozen 0 ms" report is dead.
- D-598: the stream preview's 30-SECOND CAP — a countdown state machine (Playing → the last seconds tick down → auto-stop), "Stream played successfully" on the natural ceiling stop, `REPEAT_MODE_OFF` so no looping, and the `PlayerView` detached in `onRelease` — the player, surface and session close cleanly.

## 4. D-599..D-602 — THE HUB: the combined ring and the visible untested

- D-599: the suite-health ring is ONE COMBINED number. Groups (passed / failed / untested), each group's sweep subdivided by system with the group gap REPLACED out of the sweep (never added) — 10+5 renders as a single 15 with the emerald/sky split inside. `HealthLegendRow` prints the total and the "· 10 + 5" per-system split.
- D-600: the system cards' count chip sits RIGHT NEXT TO the title (the far-edge marooning is gone).
- D-601: the cards' proportion bar fills the card's width; idle content centered.
- D-602: the ring's idle track is `onSurfaceVariant@0.25` RESOLVED IN COMPOSITION (Canvas lambdas cannot call composable color resolvers — the round's own compile-review catch) so untested is a visible track, not white-on-white; the Run-all pill centered + redrawn.

## 5. D-603/D-604 — THE LIST: one row, no select-all, no hiding

The `DoneAll` button is DELETED (the round-86 order that had not landed). The controls row is ONE line: the source count, then the three compact run pills beside it. Every one of the seven tests always renders in the expansion — pending rows ride at 0.42 alpha instead of hiding behind a placeholder.

## 6. D-605..D-610 — THE DETAIL PAGE, REWRITTEN

- D-605 THE DOSSIER: uppercase muted labels in a fixed column, wrapping values, hairline dividers — version / package / plugin / NSFW / site readable at a glance.
- D-606 THE TIME-PROPORTIONAL STAGE BAR: segment length = the test's actual duration (finished = actual ms, running = live ticker, pending = its budget's share on the faint track); each segment in its kind's color, failures dim their hue.
- D-607 THE RESULT BLOCKS: tinted + bordered per kind, a labeled RESULTS section INSIDE the block (message/detail text rows, search ladder as stat pills, the payload — grid / dossier / episodes / links / preview — rendered within the block). `CodeWindowBlock` is DELETED, not restyled.
- D-608 THE RUN PILL: bordered tinted pill with an icon badge, wrap-content.
- D-609 THE TRANSIENT VERDICT BANNER: a fresh terminal verdict slides in under the actions, lingers ~2.6s, slides away; re-runs un-see their kinds; NOTHING on the page auto-scrolls.
- D-610: the entrance/choreography polish — animated fractions on every ring and bar, spring'd banner, the breathing pulse dot retained.

## 7. D-611..D-613 — THE RUN PAGE'S SCROLL, THE SHEET'S TWO GROUPS

- D-611: the run page's auto-scroll effect ("follow the live target") is DELETED — the device report: after a test completes "it should not automatically move to the very bottom or to the very top". The user drives the scroll.
- D-612: the sheet's search bar can never clip again — the list cap is ADAPTIVE: screen height − nav bar − IME insets − 214dp reserved for everything else.
- D-613: the two ecosystems render as two SEPARATE section cards (accent dot + bold heading + count) — Aniyomi's list and CloudStream's list, visibly apart.

## 8. The round-87 ledger of artifacts

- Code: 14 files modified + `TestingPalette.kt` NEW (+1268/−600): the testing package (controller, isolation, charts, home/list/detail/run/stats screens, shared UI, 4 test classes), the anime-details sheet, and the palette.
- THE TWO SUB-AGENT AUDITS (the user's explicit order): (a) the COMPILE review verified every changed file against real Kotlin 2.2.0 + material3 1.3.1 + compose-ui sources — exactly 2 confirmed errors (missing `import kotlinx.coroutines.isActive` in DetailsTest + EpisodeListTest), both FIXED before the push; (b) the REQUIREMENTS audit checked all 22 device-report points against the code — 22/22 landed, 4 stale comments corrected.
- Persistence: payload growth (24) and the CS accumulation ride the existing store round-trip; old blobs degrade gracefully (opt-read).
- CI note: per D-281 the compiler of record is GitHub Actions — the implementation was verified by the audits + the push's Build APK run, never a local Gradle.

## 9. Open items for the next device round

- The containment's "Run aborted: …" verdict shows on the ONE dead target while the queue continues — watch the device impression: "one failed, rest ran" must read as resilience, not as a hidden crash.
- The palette's per-kind accents are fixed constants — if the user themes the app hard (e.g. a red accent), the search block's pink must still read as "search", not as "error"; move per-kind hues into the theme if it ever collides.
- The 30s stream cap is a constant in TestingSharedUi — if the user wants a per-stream or user-settable ceiling, it is one value.
