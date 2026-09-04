# DEVICE ROUND 30 — PLAN (v0.4.17 device report → v0.4.18)

The v0.4.17 device report, decomposed. Every item below was investigated from the
shipped code before a line was written; root causes first, fixes second.

---

## The report's CONFIRMATIONS (do not regress)

- **The data.json deletion system is FULLY RESOLVED** — the round's central
  verification, four rounds in the making: delete 1-of-5 removed the file, the
  `episodes/` entry, AND the data.json section; two more deletes each clean
  ("the ones which I deleted were properly removed, their sections were removed
  properly"); deleting the remaining two removed the WHOLE FOLDER
  ("everything is working properly and everything is fully satisfactory").
  D-404's `"wt"` truncating write + salvage + rebuild + verified ladder is
  CLOSED. No deletion code was touched this round.
- The wizard's overall first-launch flow, the first screen, the folder pick +
  verification, the Continue wiring, and the finish summary ("the very last
  screen… is perfect").
- The Browse preload: "the Browse page looks proper, like it was pre-loaded"
  (covers were the network's, not the preloader's, fault).
- The delete button's tap-outside disarm (round 28, re-confirmed).

---

## Item A (D-406-a) — THE WELCOME BACKGROUND'S STUTTER

### The report
"The first screen was proper, and the background was proper too, but there was
some stuttering there. Occasionally, it would skip some frames or jump into
some frames afterwards. It still keeps resetting and some stuff like that…
smooth flowing shapes which would change into different shapes and would split
sometimes and other stuff like that."

### Root causes (two concrete defects, read straight out of the shipped art)
1. **THE PHASE-WRAP RESET** — the round-29 engine drove everything from two
   `rememberInfiniteTransition` phases that each ran 0 → 2π and then WRAPPED
   back to 0. The blobs multiplied those phases by NON-INTEGER speeds
   (wobble 1.7/2.1/1.9…, drift 1.4/0.8/1.2/1.6), so at the wrap
   `sin(2π × 1.7) ≠ sin(0)` — every blob's silhouette SNAPPED (the "jump into
   some frames") and its center TELEPORTED (the "keeps resetting") on a fixed
   11s/24s schedule. The animation was never continuous at the cycle boundary.
2. **PER-FRAME ALLOCATIONS** — the draw pass built a fresh `Path`, an
   `Array(8)`, and 8 `Offset`s per blob per frame (~50 heap objects/frame →
   GC churn → the "skip some frames"), and TWO `animateFloat` states drove two
   invalidation streams.

### The fix (a rebuilt engine, one file: `OnboardingWelcomeArt.kt`)
- **ONE MONOTONIC CLOCK** — a single `mutableFloatStateOf` written by a
  `withFrameNanos` loop (delta-accumulated, clamped to 64ms so a backgrounded
  app PAUSES the art instead of leaping it). Every motion is
  `sin/cos(t · f + φ)` of ever-growing `t`: nothing wraps, nothing resets, ever.
- **DRAW-PHASE ONLY** — the clock is read ONLY inside `drawBehind` (a
  draw-phase state read), so the per-frame write invalidates the DRAW alone:
  zero recomposition, zero remeasure, one invalidation stream.
- **ZERO STEADY-STATE ALLOCATION** — every `Path` is pre-allocated and
  `reset()`+refilled; the sample points live in reused `FloatArray`s; the
  radial-gradient brushes are cached per (width, accent) and rebuilt only on a
  change. The GC never sees a frame.
- **SHAPES THAT CHANGE INTO DIFFERENT SHAPES** — each blob's silhouette blends
  an ORGANIC wobble (two spatial harmonics drifting in time) with a ROUNDED
  REGULAR POLYGON (its own side count: triangle/square/pentagon/hexagon)
  along a staged morph cycle — hold-organic → smooth morph → hold-shaped →
  smooth return — with `f(0) == f(1) == 0` so the cycle wrap is seamless.
- **SPLITTING** — each blob carries a staged split cycle: two halves born at
  the SAME center with the SAME shape (indistinguishable from one blob) that
  drift apart along a slowly precessing axis, easing slightly smaller as they
  separate, then merge back. The halves' wobble phases diverge ONLY in
  proportion to the split (0.55 × split), so both the birth and the merge are
  perfectly continuous — no pop, ever. Staggered per-blob periods/phases mean
  splits happen "sometimes", never in unison.
- The centers ride continuous Lissajous orbits; the hairline outline geometry
  rotates on the same clock (`(t · deg/s) mod 360` — visually periodic, so the
  mod boundary is seamless).
- The wordmark, the rotating tagline, and the palette (the report approved
  them) are untouched.

---

## Item B (D-406-b) — THE BUTTON-AT-THE-TOP BUG (structural, three screens)

### The report
"the Skip for Now option in the download folder selector was shown at the very
top… It should be shown at the very bottom but it was being shown at the very
top" — the same on the notifications page — "the Start Watching button is shown
at the very top, which is not good."

### Root cause
`OnboardingPermissionStep` and `OnboardingFinishStep` each emitted TWO
root-level layouts: a `fillMaxSize` content Column, then a SEPARATE
bottom-CTA Column emitted AFTER it as a sibling. `AnimatedContent` places
every root child at TopStart (a Box-like layout), so the CTA column OVERLAPPED
the top of the screen instead of sitting under the content. The "pin to the
bottom by the weighted column" comment in the round-29 code described an
arrangement that structurally could not happen.

### The fix
Every step is now ONE root `Column`: TopBar → content `weight(1f)` → the CTA
INSIDE it, last child, pinned to the bottom by the weighted content. This one
structural change moves "Skip for now"/"Continue" and "Start watching" to the
bottom of the folder, notifications, battery, and finish screens at once, and
removes the overlap that made the granted states "not look good" (the CTA was
covering the content).

---

## Item C (D-406-c) — THE THEME STEP REBUILT (mode toggle + appearance UI)

### The report
"the carousels are apparently big and they do not show the appropriate options.
At the very top the user should be given the option to select which theme he
wants, light mode or dark mode… There are only two options: light mode or dark
mode [no System]. Depending on the user's selection, below the appropriate
options will be shown… the main entry will be bigger and the other ones will
be smaller. For the carousel I would like you to utilize the exact same UI
which is being used in the appearance page."

### The fix
- **THE TOP**: a Light/Dark segmented toggle — an exact replica of the
  Appearance → General page's `SegmentedToggle` (surfaceVariant track, 12dp
  corners, 4dp inner padding, animated primary pill, 13sp ExtraBold/Medium)
  with EXACTLY two options. SYSTEM never appears; at startup a SYSTEM pref
  resolves to the system's ACTUAL mode (`isSystemInDarkTheme`), so the toggle
  initializes to what the user is effectively looking at, and the first tap
  makes the choice explicit.
- **THE APPROPRIATE OPTIONS BELOW**: every `OnboardingThemeChoice` now carries
  a `mode` bucket ("light" | "dark"); the carousel filters to the selected
  mode's themes. Dark: Midnight, AMOLED, Dusk, Night. Light: Daylight, Teal
  Mist, Coral Day.
- **THE CAROUSEL UI**: an exact replica of the appearance page's
  `PalettePreviewCard` — the theme's own preview background, the accent dot,
  the selected badge (accent circle + white check), the surface card swatch
  with the accent bar at the bottom, the bold label — scaled to 128×198dp for
  the carousel. The CENTER CARD is big; the side cards shrink to 76% with
  faded alpha — a draw-phase `graphicsLayer` scale driven by the LIVE scroll
  distance (re-executed on scroll, never recomposing: buttery at 166Hz).
  The label ink derives from each card's OWN preview background luminance
  (a light card must stay readable while the app is dark — the appearance
  page reads the live theme because its previews always match it; the
  wizard's deliberately don't).
- **LIVE APPLICATION**: kept the round-29 behaviors the report approved — the
  centered card's theme applies on SETTLE (a 220ms debounced snapshotFlow;
  flinging past cards never re-themes per frame), tapping an off-center card
  animates the carousel to it, and selection recomposes the whole app through
  ThemePreferences.
- **NO MISMATCHED-ACCENT FLASH**: switching the mode when the applied theme
  belongs to the other bucket applies that mode's DEFAULT CARD in one shot
  (mode + preset together, in MainActivity's mode callback — the id semantics
  stay app-side), so the app never renders dark-mode-with-light-accent for
  the 220ms the debounce takes. The carousel re-scrolls to the same card and
  the settle is a no-op: the highlight always equals the truth.

---

## Item D (D-406-d) — THE PERMISSION STEPS STRIPPED

### The report (the named offenders, verbatim)
"it was looking way too text-filled and cramped… 'Pick the folder where
episodes are saved for offline playback' [unnecessary] … 'Your download folder
is verified and ready. Episodes will be saved there' [unnecessary] … you can
pick a different folder anytime later in settings. Downloads is also
irrelevant… make the Folder Verified option simpler and cleaner. It should not
repeat itself at the top… it shouldn't also show the folder structure and the
folder part… it should show a full button to change the folder rather than
just showing a simple text."

### The fix (all three steps: folder / notifications / battery)
- EVERY named description is gone — the step is now: the big icon, the title,
  the action. No subtitle, no skip-explanation, no granted description, no
  "later in Settings" hints, no status chip stacked above a panel repeating
  it.
- **THE GRANTED MOMENT**: the big step icon MORPHS into a check
  (`AnimatedContent`, emphasized scale+fade) the instant the state verifies,
  with ONE clean line under it — "Folder verified" / "Notifications enabled"
  / "Background usage allowed". No repetition, no folder tree.
- **THE FULL CHANGE BUTTON**: the folder step's re-pick is a full-width
  `OnboardingSecondaryCta` (a translucent pill with the accent hairline
  border and the accent label) — a real button, "rather than just showing a
  simple text".
- **THE COMBINED BOTTOM BUTTON**: the round-29 Skip-for-now → Continue
  behavior the report approved, now structurally at the BOTTOM (Item B).
- Vertical rhythm: weighted spacers center the icon block between the TopBar
  and the bottom CTA — breathing room instead of cramped text.

---

## Item E (D-406-e) — small corrections in the same pass

- **The finish CTA** at the bottom (Item B's structural fix applied to the
  "You're all set" screen the report called perfect).
- **The step progress numbering** — the storage step was passing
  `stepNumber = 1`, a DUPLICATE of the theme step's 1/5 (visible as a
  stuttering progress bar: 1,1,2,3,5). Now 1..5 in order.
- **The back button's touch target**: 36dp → 48dp (the Material accessibility
  minimum) — the glyph stays 20dp, the padding grows.
- **AutoMirrored arrow** — the project's own convention (MainActivity already
  uses it) applied to the wizard's back button.
- A static-review pass (sub-agent) verified every import, signature, and
  Compose API against the actual artifacts and the repo's CI-compiled
  precedents: zero compile errors; the findings above were its warnings.

---

## The verification ladder

1. Static review (done — clean; artifact-level API verification).
2. GitHub Actions: full build + the module unit tests (incl.
   `:feature:onboarding`'s step-machine tests — the enum is unchanged).
3. Tag v0.4.18 + the Release APK workflow → the device round checklist:
   the welcome's 60s+ soak (no reset/jump — the wraps are gone), the theme
   step's two-option toggle + filtered carousel + big-center cards, the
   folder/notifications/battery steps' text-less layout with the bottom
   buttons, the granted-moment icon morph, the finish CTA at the bottom.
