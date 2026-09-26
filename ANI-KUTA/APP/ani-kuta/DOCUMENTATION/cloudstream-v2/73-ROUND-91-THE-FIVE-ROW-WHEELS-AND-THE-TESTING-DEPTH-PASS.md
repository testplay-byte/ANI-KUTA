# ROUND 91 — THE FIVE-ROW WHEELS + THE TESTING DEPTH PASS

**Date:** 2026-09-27 · **Phase:** DEBUG-FIRST (D-565) · **Trigger:** the user's v1.1.47 device report
**Commits:** a41e4b4e (implementation) · decisions D-628..D-633 · shipped as **v1.1.48/10148**

---

## 0. The round in one paragraph

The v1.1.47 device report arrived with two verdicts: the Link Sources sheet was **"not handled properly — a lot of issues"** (height, five-at-a-time, blur-gray, centering, heading depth, section highlight, spacing, and the card showing AniList's details instead of the extension's), while the extension testing section was **"very good now… good work"** and earned a polish list (suite-health text/depth, rectangular icon-tinted chips, reworked buttons, confirmation, the split live banner/footer, the list screen's heading/subtitle/manifest, the full-details page's collapse/bar/timeline/banner/settings, and the run screen's free back). This round implements every item verbatim — the sheet's columns became **five-row wheels** and the card went **extension-side**, and the testing system got its **depth pass** across all five pages.

---

## 1. The Link Sources sheet (D-628)

### 1.1 The geometry — 60% cap + the five-row wheel

- **THE 60% CAP:** the sheet's total height is bounded to `screenHeight × 0.60` — a fixed chrome reserve of **330dp** (header ~58 + the two column headings ~40 + search row ~66 + hint ~28 + the linked-content card ~104 + spacing ~34) is subtracted, and the wheels take what remains.
- **THE FIVE-ROW WHEEL:** each column's list is a FIXED-height viewport of exactly **five 36dp rows (192dp)** — not a fill-everything list ("it should only show at a time five, and the others will be shown when the user scrolls"). Rows are deterministic 36dp (`Modifier.height(WHEEL_ROW_HEIGHT)`), so the wheel geometry is computable up front.
- **THE IME GUARD KEEPS** its D-612/D-626 lineage: `imeBudget = screenHeight − chromeReserve − insetsDp` — with the keyboard open the wheels shrink so the search bar and the card keep their room. Final wheel height = `min(fiveRowWheel, sheet60Budget, imeBudget)` floored at 120dp.

### 1.2 The wheel contract — selected is always centered

- **HALF-VIEWPORT contentPadding** (`(wheelHeight − rowHeight)/2` top and bottom): the FIRST and LAST rows can sit in the center, the area above/below staying empty — "even if it is at the very bottom or at the very top, then it will be centered and the area above it or below it will be left empty."
- **SNAP FLING** (`rememberSnapFlingBehavior`): every fling settles with a row centered.
- **TAP-TO-CENTER:** tapping a row selects it AND `animateScrollToItem(index, −centerPaddingPx)` centers it — the canonical LazyList centering recipe.
- **The scroll-selection + interaction guard from D-626 are unchanged** (the centered row is the selection; only user-driven scrolls move it; the seed-centering effect on open still works under the padding model).

### 1.3 The highlight language

- **ROWS:** the selected row keeps its tint + border + bold + ✓ bubble; every other row is **grayed (onSurfaceVariant @0.80) with a slight blur (1.2dp)** — "not fully blurred. It should be clearly readable." The blur rides after background/border in the modifier chain so it softens the CONTENT; it is a no-op below Android 12 (API 31+ RenderEffect) where the gray carries the effect alone. Icon bumped to 22dp, name to 12sp (readability inside the five-row budget).
- **THE COLUMN HIGHLIGHT:** the card whose list holds the current selection lights up — accent border (1.5dp @0.45) + accent tint (@0.07); the other stays neutral with a faint hairline ("that section should be highlighted when any of the systems is selected").
- **THE HEADING, WITH DEPTH:** an accent **gradient band** (0.18→0.04) behind the heading row, the accent **dot**, the label, and the count in an **accent chip** — closed by a 1dp accent hairline above the wheel.
- **SPACING:** 16dp between the columns block and the search bar ("there should be some space between the top two systems and the bottom search bar").

### 1.4 The card goes EXTENSION-SIDE

The user: "it is showing the details from any list [AniList]. It should not show the details from any list, but it should be showing the details from the extension side."

- **DetailsViewModel:** `extensionBase` (the D-134 original extension data) is now backed by a `MutableStateFlow` — every existing read/write keeps the plain property syntax; the UI observes `extensionBaseState`.
- **DetailsScreen** builds the `LinkedContentInfo` from the EXTENSION side: the extension's own **title/cover/status/score/year** + the **episode count from the linked source's own episode list** (`EpisodeState.Loaded.size`, `Empty`→0) — the one real "total episodes" the extension reports. Never the priority-merged display anime (which for AniList-opened entries carried the tracker's stats).
- **AniList-only entries** (no extension link yet): the sparse honest card — title + cover + **"No source linked yet"** — no tracker stats pretending to be extension details.
- The "Linked via <source>" line stays; `LinkedContentCard` renders the unlinked state in place of a missing line.

---

## 2. Suite Health (D-629)

- The legend labels read **"pass" / "fail" / "new"** (the ring geometry from D-627 is untouched — the user confirmed it was "handled exactly like how I wanted").
- Each legend row is now its own **DEPTH SECTION**: a hairline-bordered, subtly-filled card (`surfaceVariant @0.45`, outline @0.30, 10dp corners), the three sections separated by **spacers** (6dp) — "separate each one of those sections with some spacers, and give some depth to these three sections."
- The D-627 aligned fixed-column table inside each row is unchanged (`[swatches][total][label centered][A][+][B]`).

---

## 3. The testing home (D-630)

- **Recently tested chips:** RECTANGULAR with rounded corners (12dp — no more stadium pills), and each background carries the extension's **own icon tint** — a new `rememberIconTint(drawable, iconUrl)` in `:core:designsystem` runs the CoverColorExtractor swatch pipeline (vibrant→darkVibrant→muted→dominant, saturation ≥0.40, lightness 0.40–0.65) over a bounded icon bitmap (BitmapDrawable passthrough, or a ≤96px canvas rasterization for vector/adaptive icons; Palette resized to 48). The tint draws at **0.12 alpha** — "not way too much vibrant. It should be slightly applied." The letter-tile hue (now the extracted `letterTileColor(name)` helper) is the fallback, so a chip and its never-blank icon always agree.
- **The system cards:** the depth pass — a hairline border, and each system's GLYPH (Tv / Cloud) in a 42dp accent-tinted well replaces the old 4dp edge bar.
- **The command footer:** both buttons are **rounded RECTANGLES (14dp)** now. Run-all is a **SOLID MUTED ACCENT** — `lerp(primary, background, 0.20f)` — "a solid color, but not a way too bright color from the accent color", with shadow and centered content; the label is just **"Run all tests"** (the "· N" count dropped — the hero and the system cards already count). The stats pill keeps its tonal fill + failed badge, in the rectangle shape.
- **CONFIRMATION:** tapping Run-all opens an AlertDialog ("Run all tests?" / "All N installed sources will be tested, one after another. You can stop anytime." / Start · Not now) — "it should first of all ask the user for the confirmation."
- **THE SPLIT LIVE EXPERIENCE:** the old duplicate "Testing n of m" at both ends is gone.
  - **TOP banner:** WHICH extension is being tested (its name) + **AT WHAT STAGE** — the stage line. A finished stage's **verdict lingers ≥1s** before yielding: a small poll-loop machine (`rememberStageLine`) enqueues fresh verdicts, shows each for a minimum of `STAGE_VERDICT_LINGER_MS = 1000`, then hands off to whatever stage is live (waiting between stages/targets if it must, re-arming on a run-generation change) — "even though it was completed way too quickly and the next one has completed too. There will be some padding there going on so that the overall experience is dealt with smoothly." The line swaps through an AnimatedContent crossfade (never an instant cut). The View/Stop actions left the banner — the banner is information now.
  - **BOTTOM footer (running state):** the run's **LIVE ELAPSED TIME** (LiveElapsedText against `startedAtMs`) + "n of m · tap to view" + the **Stop** action — "it could show the current time at the bottom, and it could also show the option to stop it at the bottom."
- **Header blur:** ScrollBlurOverlay (§2.2) wraps the list — the hub joins the app-wide language.
- **Future, ordered but NOT implemented:** the Run-all **target picker** ("when the user clicks that button, the user will be given an option to select which ones to test. But for now it is perfect, and let's keep it as Run All Test button").

---

## 4. The list screen (D-631)

- **The heading is just the system** — "Aniyomi" / "CloudStream" ("the heading should not say Anyomi targets or Cloud Stream targets").
- **The lang · system row subtitle is GONE** — the heading owns the system ("the user can see the headings at the top"); the language was parade noise on every row.
- **THE MANIFEST:** a target with no results expands to just the tests' **names + theme-color dots** (full-strength kind colors) — "the details of it appear only when the user clicks the Run All Tests… then the processing will be shown exactly like how it is being handled currently." Once results exist, the live KindCompactRow grid takes over unchanged.
- **Header blur** (§2.2).

---

## 5. The full-details page (D-632)

- **THE DOSSIER COLLAPSES:** the hero opens as the summary — icon, name, status chip, and an expand chevron; the verdict chips + the metadata grid live behind the tap ("they should not be shown in the expanded version every time. When the user clicks on it, only then will it expand and the full details will show"). `rememberSaveable`, so rotation keeps the choice. The **Language** row moved into the meta grid (its one honest home); the ecosystem·lang subtitle is gone.
- **THE STAGE BAR is its own section** below the dossier — a "Stage timings" card with the bar and the honest caption ("the bar segments… should be in a separate section below it").
- **THE EQUAL-BY-DEFAULT MODEL:** untested segments all hold the **NOMINAL share (10s)** — equal lengths by construction (the old timeoutMs budgets made them differ pre-run); when a test starts, its segment drops to the smallest and **grows with its live time** (150ms ticks); finished segments hold their **actual durations**; the neighbors renormalize. **Every weight animates** (`animateFloatAsState`, 350ms FastOutSlowIn chase) so the shrink, the growth, and the reflow read as one smooth motion — "the animation needs to be handled smoothly." The D-620 visibility floor + renormalization is kept; pending fills brightened 0.16→0.28.
- **THE RUN PILL:** a SOLID muted-accent rectangle that says **"Run Tests"** — nothing else ("it does not need to show seven tests or other things like that"). The live-run states (View live run / A run is in progress) keep their D-624 sky treatment.
- **THE SETTINGS GEAR:** the header's actions slot carries the extension's settings — MainActivity wires it to `SourcePreferencesKey(targetId)` ("he will be led to the settings page of that specific extension"). A non-configurable source gets the honest "This source has no settings." page.
- **THE TIMELINE:** the rail's bubbles are **CENTERED on each section** (connector halves above/below through the midpoints — first row draws no line above, last none below); never-run kinds render **compact name+color sections** (full-strength dots — "the theme colors need to be somewhat on a brighter side, like contrasty. They should not be dulled"; no "Not run yet" text — the KindDetailCard's pending branch moved out and its signature tightened to a non-null result); and each section wrapper **animateContentSize**s so a completed test expands its section smoothly ("after the test has been completed, then the section should expand smoothly, but it does not expand smoothly").
- **THE VERDICT BANNER:** SOLID — `surfaceContainerHigh` + hairline + shadow + a 3dp accent leading edge, with **darkening gradient scrims** fading in above and below ("its background should not be transparent… it should have some darkening effect around it so that it is clearly visible"). The D-615/D-616 seeding + overlay behavior is unchanged.
- **Header blur** (§2.2).

---

## 6. The run screen's free back (D-633) + the stats blur

- **The leave guard is GONE from the run page** — the BackHandler + "Leave the test run?" dialog deleted; back pops freely and the run continues in the app-scoped controller ("It should allow me to go back without any problems from that specific screen, without stopping the test"). The prompt lives on the HOME screen only (D-592, unchanged there).
- **Header blur** on the run page AND the stats page — all five testing pages now carry the §2.2 language.

---

## 7. Verification

- Brace/paren balance checked programmatically across all 12 touched files (all OK).
- Symbol greps: `KindDetailCard` (1 caller, tightened), `SystemCard` (2 callers, glyph param), `wheelHeight`/`WHEEL_*` (sheet-only), `extensionBase` (flow-backed property), `rememberIconTint`/`letterTileColor` (designsystem + shared UI + home), zero dangling references to removed symbols (the run screen's `showLeaveDialog`, the detail pill's "· 7 tests", the list rows' subtitle builder).
- CI: Build APK run **36257993430** on the implementation commit a41e4b4e (polled via the API; the run's verdict recorded in the ledger commit that follows).
- The v1.1.48 release follows the standing D-565 loop (release branch + bump + tag + Release APK run + LIVE mirror).

## 8. The device-round checklist (for the user)

1. **Link Sources:** the sheet stays within ~60% of the screen; each column shows five rows; the centered row is the selected one (scroll, fling, tap — all center); the rest are gray + slightly blurred but readable; the two headings carry their accent bands + count chips; the column holding the selection is lit; the gap between the systems and the search bar is real; with the keyboard open everything still fits.
2. **The card:** on a LINKED entry the details are the EXTENSION's (its title, its cover, its status/year/score, and the episode count from its own episode list); on an unlinked AniList entry it says "No source linked yet" — no AniList stats anywhere.
3. **Suite health:** the legend reads pass/fail/new; three separated depth cards; the alignment intact.
4. **Home:** rectangular icon-tinted recent chips; the system cards' glyphs; the solid Run-all rectangle (asks for confirmation); during a run the top says which extension + which stage (verdicts hold ~1s each) while the bottom shows the elapsed time + n-of-m + Stop.
5. **List:** headings "Aniyomi"/"CloudStream"; no lang·system subtitles; a never-run expansion shows the test manifest only.
6. **Full details:** the dossier is collapsed until tapped (Language lives inside); "Stage timings" is its own card (equal segments at rest — watch one start smallest and grow while the others shrink); "Run Tests" solid pill; the header gear opens the extension's settings; the timeline dots sit centered on their sections; never-run kinds are compact name+color rows; a completing test expands its section smoothly; the verdict banner is solid with the darkened surround.
7. **Run screen:** back from the Test Run screen pops immediately — no prompt, the run keeps going; the prompt appears only when leaving the whole extension-testing section from the home screen.
