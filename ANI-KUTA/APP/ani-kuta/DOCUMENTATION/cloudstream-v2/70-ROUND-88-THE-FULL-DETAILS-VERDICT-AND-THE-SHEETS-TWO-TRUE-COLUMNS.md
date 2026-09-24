# 70 — ROUND 88: THE FULL-DETAILS VERDICT AND THE SHEET'S TWO TRUE COLUMNS (D-614..D-624)

> Round: 88 · Date: 2026-09-24 · Base: v1.1.44 (round 87) · Ship: v1.1.45/10145
> Input: the user's v1.1.44 device report — the testing hub's first look is finally GOOD ("much better… much more proper"), but the Link Sources sheet is "still not handled properly" (the wanted layout is explicit: Aniyomi LEFT, CloudStream RIGHT, side by side), and the Full Details page's "overall experience was most definitely not good… quite a lot of failures". The user also set the process bar: plan everything, sub-agent verify RE-verify at least 2-3 times, no rushing.

---

## 0. THE FEEDBACK → FIX MAP

| # | The feedback | The fix | Where |
|---|---|---|---|
| 1 | Link Sources: "on the top, on the left side, the Aniyomi extensions, on the right side, the CloudStream extensions" | TWO SIDE-BY-SIDE COLUMNS (the round-87 stacked section cards read as one tall column): a Row of two weighted cards — Aniyomi LEFT, CloudStream RIGHT — each with a FIXED heading (accent dot + bold label + count) above its OWN independently-scrolling LazyColumn | D-614, ManualSearchSheet |
| 2 | The sheet's search bar must keep its room (the round-87 contract) | the ime-aware cap reserve grown 214→244dp (the two fixed column headings sit above their capped lists); the cap stays at exactly one computation site | D-614 |
| 3 | Full details: "the overall experience was most definitely not good" | the eight-item rework below (D-615..D-622) + the D-624 color catch | TestingTargetDetailScreen + TestingSharedUi |
| 4 | (audit) opening a tested extension replayed a stale verdict banner EVERY time | the banner's seen-set is SEEDED at first composition — only a verdict that ARRIVES while the screen watches earns the banner; re-runs un-see their kinds | D-615 |
| 5 | (audit) the inline banner shoved the whole timeline ~34dp down and back on every completion (14 jolts per 7-test run) | the banner is an OVERLAY pinned under the header, floating ABOVE the list — appearance and exit shift NOTHING | D-616 |
| 6 | (audit) the hero's seven stacked meta rows ate half the screen; "System" shown twice | the dossier is a TWO-COLUMN meta grid (wide Package/Site cells take full rows, wrap to 2 lines); the duplicated System row is GONE | D-617 |
| 7 | "quite a lot of failures" — no summary, reasons truncated | a VERDICT SUMMARY (Passed n / Failed n / Not run n chips, non-zero only) at the top of the hero; the failure REASON renders IN FULL (the 2-line ellipsis on the full-details page was backwards) | D-618 |
| 8 | (audit) a queued target read "Untested" here but "Queued" on the list; the run pill was a dead end during any live run | the chip is queued-aware; the pill has three honest states: "View live run" (navigates to the live session — MainActivity wires onOpenRun), "A run is in progress…" (disabled, truthful), "Run all tests" | D-619 |
| 9 | (audit) the stage bar lied pre-run (budget lengths, one gray strip, "each part a different color" unmet) and the 2% clamp stole width | pending segments wear their KIND's color at faint alpha; the clamp is a RENORMALIZED 3% floor (widths always sum to one bar); the per-segment animation waves are gone (the running segment grows on its live ticker) | D-620 |
| 10 | (audit) a RUNNING test's rail bubble was IDENTICAL to a passed one | the running bubble PULSES (ring + dot, infinite transition); passed is solid; pending cards fade ONCE (the double-dim was unreadable) | D-621 |
| 11 | (audit) a STORED page started streaming video on open; the ended preview left a permanent black 16:9 box | TAP-TO-PLAY gate (a compact play strip; auto-play only while watching a live run); the ended preview collapses to a one-row strip ("Stream played successfully" / "Preview unavailable — the verdict came from the stream's data"); the ExoPlayer exists ONLY inside the playing branch and is released on exit | D-622 |
| 12 | (audit) the payloads hid what the tests found: 6 of 12 results, 24 of 48 episodes, 3 of 8 genres; an empty labeled box on every PING block | the grid shows all 12, the chips all 48, the genres all 8; results labels carry counts ("TOP RESULTS · 12"); kinds whose payload view renders nothing (PING) skip the box entirely; 9sp type bumped to 10sp | D-623 |
| 13 | (caught by re-verification) the "View live run" pill reached for the M3 baseline `tertiary` — the never-themed pale pink the round-87 report had already rejected | the live-run accent is the PALETTE's sky (TestingPalette.SystemB); black icon on the light sky bubble | D-624 |

---

## 1. D-614 — THE SHEET'S TWO TRUE COLUMNS

`SourceListPanel` is now a Row of two `SourceColumnCard`s (weight 1f each): Aniyomi first (LEFT), CloudStream second (RIGHT). Each card: a fixed heading row (8dp accent dot in the system's palette hue, 13sp ExtraBold label, count) and its own LazyColumn (`items(sources, key = id)`) capped by `heightIn(max = listMaxHeight)` — independent scroll, ragged bottoms allowed (the sheet still wraps when the lists are short). `SourceColumnRow` is the half-width budget row: 20dp icon (WheelSourceIcon/WheelIconFallback gained a size param), 11sp one-line name, the full selected treatment (tint + 1.5dp border + ExtraBold + 16dp check bubble), the linked ✓ kept. An empty ecosystem shows an honest note instead of vanishing. The cap's reserve grew by the headings' ~30dp so the search bar's room is unchanged.

## 2. D-615/D-616 — THE BANNER THAT EARNS ITS APPEARANCE

The stale-banner bug: `seenTerminalKinds` started EMPTY at every composition, so a stored page's whole history read as "fresh" and the last verdict (usually Stream play) bannered on open, every time, on every re-entry. The seed: the first composition with a results snapshot marks every already-terminal kind seen SILENTLY; only verdicts arriving later banner; a kind returning to RUNNING (a re-run) un-sees itself. The layout-jump bug: the banner was a LazyColumn ITEM whose AnimatedVisibility height snapped in/out, shoving the timeline. It is now composed in the Box that wraps the list, `align(TopCenter)` under the header — it floats; nothing below it ever moves.

## 3. D-617/D-618 — THE HERO THAT ANSWERS FIRST

The dossier: `MetaCell(label, value, wide)` + `buildMetaRows` (narrow cells pair two-per-row; wide cells take a row) + `MetaGrid` (hairline dividers between rows). Version‖Plugin, Package (wide), NSFW‖Last tested, Site (wide) — four rows instead of seven; the duplicated System row deleted. Above the grid: the verdict summary chips — counts computed from the results (decided = not PENDING/RUNNING; notRun = 7 − decided), only non-zero chips render. The failure reason (`result.detail`) lost its maxLines=2 — the full why, wrapped, inside its block.

## 4. D-619 — THE PILL THAT KNOWS ITS PLACE

`inLiveRun = runActive && queue.contains(targetId) && !finished`. The status chip gets `queued = inLiveRun` (the shared chip's own logic picks "Queued" vs "Testing…"). The pill: watching → sky "View live run" → `onOpenRun()` (MainActivity pushes `ExtensionTestingRunKey()` — the empty-csv push is OBSERVE-ONLY by the run screen's own contract); another run live → disabled "A run is in progress…"; else → "Run all tests" → `controller.start`.

## 5. D-620/D-621 — THE HONEST BAR AND THE BREATHING RAIL

The stage bar: pending = kind color @ 0.16; skipped 0.30; failed 0.55; passed full. Widths: each fraction floored at 0.03 then RENORMALIZED (`floored[i] / flooredTotal`) — visibility without theft; the segments' `animateFloatAsState` waves deleted (the running segment's `liveMs` ticker IS the animation, inherently smooth, no neighbor reflow). The rail: RUNNING = pulsing ring (infinite 0.25→0.9 alpha) + live dot; PASSED = solid; the pending card's whole-card `.alpha(0.55)` is gone — one fade (faint fill + hairline border).

## 6. D-622 — THE PREVIEW THAT ASKS BEFORE IT PLAYS

`StreamPreviewPlayer(autoPlay)`: false → a compact tap-to-play strip ("Tap to preview the stream — muted, capped at 30s"). The player object moved INSIDE `if (phase == PLAYING)` — when the cap, the stream's end, or an error flips the phase, the branch leaves composition and the DisposableEffect releases player+surface. The end state is a one-row strip on the theme surface — the black 16:9 box is gone. The detail page passes `autoPlayPreview = inLiveRun` (live context auto-plays; stored pages ask). `kindPayloadHasContent(kind, payload)` gates the results section so PING (payload view empty by design since D-592) never renders an empty labeled box.

## 7. D-623/D-624 — THE WHOLE PAYLOAD AND THE COLOR DISCIPLINE

Caps now match the capture caps exactly: entries 12, episodes 48, genres 8 (videos were already uncapped); the labels count what they show. 9sp → 10sp across the touched views. D-624 exists because the FINAL re-verification pass caught the new pill reaching for `colorScheme.tertiary` — the theme never defines it (grep: zero tertiary assignments repo-wide), so it resolved to the M3 baseline pale pink, the exact near-twin-of-error the round-87 palette doctrine bans. The live-run accent is now `TestingPalette.SystemB` (sky), black icon on the light bubble.

## 8. THE VERIFICATION LEDGER (the user's explicit order — three passes)

- PASS 1 — COMPILE REVIEW (general-purpose agent): every changed file against real stdlib (2.0.20 constant-pool checks) / media3 / Compose signatures; brace/paren balance machine-checked; **0 confirmed errors**; 2 dead imports flagged and removed.
- PASS 2 — REQUIREMENTS AUDIT (general-purpose agent): 21 checkpoints (A1-A6 sheet, B1-B12 detail, C1-C3 shared) — **21/21 LANDED** with file:line evidence; the regression sweep confirmed the four GOOD pages untouched and every shared-signature change backward-compatible; an 8-item ranked honest-gap list (the one real item — the headings outside the cap — was fixed immediately).
- PASS 3 — ADVERSARIAL RE-VERIFICATION (general-purpose agent): fresh eyes on compose runtime hazards (overlay touch pass-through, the player's listener→recompose→dispose chain, tap-to-play reset semantics, division-by-zero, nested-scroll context, state keys, rotation), every user-facing string, the tertiary theme grep (→ D-624), and the empty-csv push contract. Verdict: FIX FIRST (item 1) → fixed → clean.

## 9. Open items for the next device round

- The run page's expand-triggered stream previews still auto-play when reviewing a FINISHED run there (deliberate: the tap-to-expand IS consent) — if the user notices the inconsistency, gate them the same way.
- A "Queue next" affordance while another run is live would close the last dead-end on the detail pill.
- The in-flight test counts as "Not run" in the summary chips until it decides — cosmetic, self-corrects.
