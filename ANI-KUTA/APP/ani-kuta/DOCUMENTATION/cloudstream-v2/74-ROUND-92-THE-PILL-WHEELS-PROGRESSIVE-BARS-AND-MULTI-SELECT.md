# ROUND 92 — THE PILL WHEELS + THE PROGRESSIVE STAGE BARS + THE MULTI-SELECT BATCH ACTIONS

**Date:** 2026-09-27 · **Phase:** DEBUG-FIRST (D-565) · **Trigger:** the user's v1.1.48 device report
**Commits:** 1a8d9957 (implementation) + e7b0c34a (the CI compile fixes) · decisions D-636..D-639 · shipped as **v1.1.49/10149**

---

## 0. The round in one paragraph

The v1.1.48 device report carried three verdicts. The **Link Sources sheet** was close but carried a real bug ("if I tap on any of the extensions… the extension above it will be selected… every single time") plus a restyle list (theme colors only on the active column, no count bubble, a pill-shaped heading, an all-rounded list container, a taller wheel with ≥4 visible rows, distance-based blur, a tighter hint, a two-step search bar whose first tap does NOT open the keyboard, the card hiding once a search runs, and a card layout rework). The **testing system** was "definitely good… exactly like how I wanted it to be" except one thing: the live run's stage timing "shows all the stages times… altogether in the single go" instead of revealing progressively with relative bar lengths. And the **extensions page** earned the round's big feature: **multi-select batch actions** (long-press → selection → the bottom bar's Install/Trust/Untrust/Delete, each applying only to the rows it fits; the aniyomi delete chains the system prompts one after another; the CloudStream delete takes a single confirmation). This round implements all three.

---

## 1. The Link Sources sheet (D-636)

### 1.1 THE TAP FIX (the round's one real bug)

The user: "if I tap on any of the extensions, then that extension will not be selected, but instead the extension above it will be selected… every single time. Even if the current extension is selected and I click on the selected extension, then it will automatically select the extension above it."

- **Root cause:** the round-91 tap handler called `animateScrollToItem(idx, -centerPaddingPx)`. That offset semantics is WRONG under the half-viewport contentPadding model: with `contentPadding = (wheel − row)/2`, the LazyList item state `(idx, 0)` — **offset zero** — IS the centered position (this is the project's own working drum idiom, commit 01106f07: `listState.scrollToItem(initialIndex)` with symmetric padding). The negative offset scrolled the tapped row to the **bottom edge** of the wheel, leaving a row ABOVE it nearest the center — the centered-index machine then selected that row. On the device's ~3-row wheel the nearest-center row was exactly one above the tap (on a full 5-row wheel it would be two above) — matching the report verbatim.
- **The fix:** `animateScrollToItem(index)` — no offset. The tapped row lands exactly centered.
- **The suppression guard:** while a tap's centering animation runs, its own centered-index events are the animation's passing traffic (the tap already set the selection) — they are ignored until the animation lands. A **counter** (`tapCenteringCount`), not a flag, so overlapping taps each release only their own hold; the decrement rides a `finally` so a cancelled animation (a competing scroll) still releases.

### 1.2 The restyle — pill heading + all-rounded container + theme colors

The user: "they should not be given the current type of colors… they should be given the actual theme color, and the color should only be given to the one which is currently selected and which is currently in active… the total number of extensions does not need to be shown in a proper bubble… the top section… should be in a rounded way, like all four sides… like a pill shape. And the bottom section which has all the extensions showing, it should be in an all-rounded view too."

- **THE PILL HEADING:** each column opens with its own stadium pill (`RoundedCornerShape(50)`) — the system's name + the count as **plain quiet text** (the accent count chip is gone). Active: `primary @0.13` fill + `primary @0.45` ring + the label in primary. Inactive: neutral `surfaceVariant @0.45` + a faint outline. **No hardcoded colors anymore** — the old `0xFF34D399` (Aniyomi green) and `0xFF38BDF8` (CloudStream sky) are deleted; the accent is `MaterialTheme.colorScheme.primary`, worn ONLY by the column holding the selection.
- **THE LIST CONTAINER:** the wheel lives in its own **all-rounded card** (`RoundedCornerShape(18dp)`) below the pill — tinted (`primary @0.055`) + ringed (`primary @0.40`, 1.5dp) only while active, neutral otherwise. The old single-card-with-gradient-band anatomy is gone.
- **Column gap:** 10dp.

### 1.3 The taller wheel

The user: "in the current height only at a time three extensions can be shown, but what I wanted was for at least four… one center… one top… one bottom… and the other two top and bottom… shown slightly."

- **The viewport is 168dp:** `centerPadding = (168 − 36)/2 = 66` — the center row + ONE full row above (top at 27dp) + ONE full row below + a **24dp peek** beyond each of those. Exactly the described geometry.
- **The chrome slimmed to a 288dp reserve** (the compact pill headings, the tighter hint, the leaner card) — `min(168, 60%·screen − 288, ime budget)`, floored at 120dp. On a 780dp screen the whole sheet lands ≈58% — the 60% cap holds WITH the taller wheel (round 91's 330dp reserve starved the wheel to ~150dp on the same screen; that shortfall is why only three rows showed).

### 1.4 The distance-driven blur

The user: "the currently selected one will never be blurred out that much, but the ones which are further away, like the closest to it, will be slightly blurred, but the ones which are further away will be more blurred."

- The row's **distance from the centered row** (|index − centeredIndex|) buckets the treatment: **0 → no blur; 1 → 0.8dp; 2 → 1.8dp; 3+ → 3dp** — plus a text-alpha falloff (0.78 / 0.64 / 0.52). The selected/centered row is never blurred. Before the first layout (centeredIndex null) everything reads distance 0 — no blur flash on composition. Pre-Android-12: the blur is a no-op and the alpha falloff carries the effect.

### 1.5 The two-step search bar

The user: "for the first time when the user clicks on the search bar, it will not open up the keyboard. It will only switch its state to the name of the content typed in and the search button showing."

- The bar starts **UNARMED**: a transparent `matchParentSize` overlay swallows the first tap and **arms** the bar — the content name pastes (TextFieldValue, caret at the end), the search button reveals, and **no focus, no IME**. A haptic tick is the feedback (the overlay is ripple-less).
- The **second tap** lands on the real field — focus + keyboard.
- The round-85 **blur reset now also disarms**, so the cycle repeats after every idle reset.
- The button's visibility condition gains `|| searchArmed`.

### 1.6 The card + the hint

- **The card HIDES once a search runs** ("when the user actually searches for any content… the very bottom section should disappear. By that I mean the currently connected details") — `if (!showResults)` around the `LinkedContentCard`.
- **The card layout rework** ("improve the UI of the bottom card… manage it better"): a hairline-bordered surface (16dp), the cover in its own **ringed 52×72 frame** (radius 10), the title → details → **iconed linked-via row** hierarchy (a `Link` glyph + "Linked via X" in primary, or "No source linked yet" quiet).
- **The hint sits tighter** — `top 0 / bottom 6` (was 2/10) ("the distance between the top and the bottom of it should be reduced"); the search row's own padding trimmed 16/12 → 14/10.

---

## 2. The live run's progressive stage bars (D-637)

The user: "it shows all the stages times there, rather than showing only one stage time and then the second one, then the third one… all of them altogether in the single go… when a test is being performed for a specific part, then only that bar will show and the ones before it will show. And the length of the bar will still represent the total number of time it has taken, and it will be adjusted properly according to the previous ones, and also the size of the previous ones will be adjusted appropriately."

- **PROGRESSIVE:** the hero renders a stage's row ONLY once that stage has STARTED (`results[kind] != null && status != PENDING` — the controller adds result entries per RUNNING emission). Each row reveals with an **expand+fade AnimatedVisibility** as its turn arrives; future stages render nothing at all. The old all-seven-rows-with-dashes wall is gone.
- **THE TIME BAR:** every shown stage carries a 5dp bar on a full-width track — its length is the stage's duration **relative to the LONGEST stage shown** (`ms / maxStageMs`, floored at 4%). The RUNNING stage's bar grows live (a 150ms ticker against `runningKindStartedAtMs`); finished bars hold their actual durations.
- **THE RENORMALIZATION:** when a new maximum lands (a long search overtaking a fast ping), every earlier bar's fraction re-targets and **animates** (`animateFloatAsState`, 350ms FastOutSlowIn) — "the size of the previous ones will be adjusted appropriately."
- Colors follow the stage-bar language: the kind's full color; FAILED @0.55; SKIPPED @0.30. `LiveKindRow` gains a `bar` slot (rendered under the row, above the live-phrase footnote and the expandable details — both preserved). A private `STAGE_MIN_MS = 250` floors the weights (twin of the detail page's D-632 constant).

---

## 3. Multi-select batch actions (D-638 aniyomi + D-639 CloudStream)

The user: "if I long press on any of the extensions, then it should show me the selection menu where I can select the extensions and click the delete button or select the other options which might be available, like untrust or trust, or maybe the install action, depending on what I have selected. And the actions will be performed in a batch afterwards… one after the other, but with proper care and with proper planning… for the CloudStream side, it will just do a single confirmation to delete it all because it does not need the system prompt… the options will be shown at the very bottom… depending on the available actions… if he clicks delete, then only the installed ones will be deleted, and the other ones will be left as it is."

### 3.1 The shared chrome (ExtensionListChrome.kt)

- **`SelectionCheckBubble`** — the 22dp leading ring → filled primary disc + check.
- **`SelectionBarAction`** — the icon+label pill (primary-tinted; destructive = error-tinted).
- **`ExtensionSelectionBar`** — the bottom bar scaffold: the X (exit + cancel), the label line (count or batch progress), and the actions slot. Slide-up + fade in; slide-down out.

### 3.2 The contract (both tabs)

- **Long-press any row** (every section — trusted, failed, untrusted, available) → selection mode with that row pre-selected + a haptic tick. Taps toggle rows; deselecting the last row exits.
- **Rows in selection mode:** the check bubble leads, the row's own action icons hide, the surface wears the selected tint + ring, and the tap toggles selection.
- **The bottom bar shows ONLY the actions the selection supports** — Install (any selected AVAILABLE), Trust (any selected UNTRUSTED), Untrust (any selected INSTALLED/ERRORED), Delete (any selected row actually on the device) — and **each action applies only to its own subset**: a mixed installed+available selection offers BOTH Install and Delete; Delete uninstalls just the installed ones and leaves the rest untouched.
- **Batches run one after the other**: installs await each flow's completion (aniyomi) or stagger 350ms (CS); trust/untrust run sequentially; the list's bottom padding grows so the bar never covers rows. Switching tabs drops the selection.

### 3.3 The aniyomi delete — the chained SYSTEM prompts (D-638)

- Android uninstalls APKs one system prompt at a time, so the batch is a **queue**: the head's uninstaller fires; the moment the package leaves the manager's flows (the system OK — the same signal the ghost machinery rides), the next head fires immediately ("afterwards it will give me the second pop-up immediately after that getting deleted"). A **cancelled** prompt stalls the queue (nothing was deleted); the bar's **X stops the batch**.
- While the batch runs, the bar's label reads **"Uninstalling n/N…"**; already-gone heads are skipped so the chain never stalls on a ghost.
- **Reorder moved to the header**: long-press used to enter reorder mode — that door is now a **SwapVert pill** in the header (aniyomi tab only), because long-press belongs to selection per this round's spec.

### 3.4 The CloudStream delete — ONE confirmation (D-639)

- Plugins are files, not APKs — no per-package system dialogs. The bar's Delete opens a **single AlertDialog** ("Uninstall N plugins?"), then the uninstalls run sequentially (300ms stagger), rows gliding out via `animateItem`.

---

## 4. What this round did NOT touch

- The testing detail page's "Stage timings" card (the D-632 equal-by-default stacked bar) — the user said the full-details screen "looks quite good" and the stage-timing complaint was about the LIVE RUN hero; the detail page keeps its completed-run behavior.
- The suite health ring, the home screen, the list screen, the run screen's back behavior — all confirmed good this round; byte-identical.
- The Run-all target picker — still future, still ordered, still not implemented.

---

## 5. Verification

- Programmatic brace/paren balance on all five files (sheet, run screen, chrome, both extension screens) — clean.
- Symbol greps: `accentDot`/`WHEEL_ROW_COUNT`/`fiveRowWheel` = zero matches; the new symbols (`searchArmed`, `tapCenteringCount`, `WHEEL_VIEWPORT_HEIGHT`, `WHEEL_BLUR_BY_DISTANCE`, `SelectionCheckBubble`, `SelectionBarAction`, `ExtensionSelectionBar`) defined once, used from both tabs.
- Import hygiene: no duplicates; the CS file's now-unused `clickable` import removed; the sheet's now-unused `Brush` import removed.
- **CI: run 36267763500 FAILED** on three compile errors, all in the new multi-select code — (1) `ExtensionSelectionBar`'s `actions` slot sat BEFORE the defaulted `modifier`, so the call sites' trailing lambda could not bind (Kotlin binds a trailing lambda only to the FINAL parameter) → "No value passed for parameter 'actions'" + "Too many arguments" + a cascade of "@Composable invocations" errors inside the orphaned lambda; (2) `animateColorAsState` imported from `androidx.compose.animation.core` (it lives in `androidx.compose.animation`); (3) a bare `else {}` branch inside the rows' `onClick = if (selectionMode) … else {}` argument infers as `Any`, not `() -> Unit` — hoisted to explicitly-typed `val rowClick` locals. **Run 36268029978 on the fix commit e7b0c34a: GREEN** (second run — within the D-472 ≤2 budget). All three became round-92 lessons.

---

## 6. The device-round checklist (v1.1.49)

1. **Link Sources — the tap:** tap any extension → THAT row centers and selects (also tap the already-selected row → nothing moves). Tap rows above and below the current selection — each lands on itself.
2. **The columns:** pill headings; the count as plain text; the accent follows the ACTIVE column in the theme's own color; the list container all-rounded.
3. **The wheel:** ≥4 rows visible — center + one full above + one full below + two slight peeks.
4. **The blur:** the centered row crisp; neighbors slightly soft; the rims softest (still readable).
5. **The search bar:** first tap → the name pastes + the search button appears + NO keyboard; second tap → the keyboard. Search → the bottom card disappears; "Change" → it returns.
6. **The card:** the ringed cover, the iconed "Linked via" row.
7. **The live run:** open View Live Run during a run — only the started stages show, each with its time bar; the running bar grows; earlier bars resize when a long stage lands.
8. **Multi-select (both tabs):** long-press → selection; toggle rows; the bottom bar shows only the applicable actions; mixed installed+available selections offer Install AND Delete, each acting only on its subset.
9. **Aniyomi delete batch:** select several → Delete → the system prompts chain one per OK; the bar counts n/N; X stops.
10. **CS delete batch:** select several → Delete → ONE confirm → all go.
11. **Reorder:** the header's SwapVert pill (aniyomi tab) still opens reorder mode.
